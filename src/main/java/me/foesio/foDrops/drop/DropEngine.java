package me.foesio.foDrops.drop;

import me.foesio.core.command.CommandPlaceholders;
import me.foesio.core.command.CommandSafety;
import me.foesio.core.command.CommandSafety.UnsafeCommandType;
import me.foesio.core.inventory.InventoryDepositResultMode;
import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.item.FoItemStacks;
import me.foesio.foDrops.FoDrops;
import me.foesio.foDrops.api.FoDropRewardEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class DropEngine {
    private static final long COOLDOWN_CLEANUP_INTERVAL_MS = 300_000L;
    private static final long COOLDOWN_STALE_TTL_MS = 86_400_000L;

    private final FoDrops plugin;
    private final DropStore dropStore;
    private final InventoryDepositService inventoryDeposits;
    private final Random random = new Random();
    private final Map<UUID, Map<String, Long>> cooldownByPlayerAndProfile = new HashMap<>();
    private long nextCooldownCleanupAt = 0L;

    public DropEngine(FoDrops plugin, DropStore dropStore, InventoryDepositService inventoryDeposits) {
        this.plugin = plugin;
        this.dropStore = dropStore;
        this.inventoryDeposits = inventoryDeposits == null ? InventoryDepositService.create() : inventoryDeposits;
    }

    public DropEvaluation evaluate(DropContext context) {
        DropEvaluation evaluation = new DropEvaluation();
        long now = System.currentTimeMillis();
        cleanupCooldownCache(now);

        for (DropDefinition definition : dropStore.getDropsForEvent(context.getEventType())) {
            if (!matchesConditions(definition.getConditions(), context, definition.getId(), now)) {
                continue;
            }

            evaluation.getMatchedDefinitions().add(definition);
            markCooldown(definition.getConditions(), context.getPlayer(), definition.getId(), now);
            if (definition.isCancelVanillaDrops()) {
                evaluation.setCancelVanillaDrops(true);
            }
            if (definition.isStopProcessing()) {
                break;
            }
        }

        return evaluation;
    }

    public void applyRewards(DropEvaluation evaluation, DropContext context, Collection<ItemStack> groundTarget) {
        Location location = context.getLocation();
        World world = context.getWorld();
        if (location == null || world == null) {
            return;
        }

        for (DropDefinition definition : evaluation.getMatchedDefinitions()) {
            for (CustomDropEntry entry : definition.getCustomDrops()) {
                if (!entry.shouldDrop(random)) {
                    continue;
                }

                if (!entry.isItemReward()) {
                    ItemStack marker = entry.getItem();
                    runCommands(entry, context, marker);
                    sendRewardMessage(entry, context, definition, marker);
                    continue;
                }

                ItemStack built = entry.createDropItem(random, context.getFortuneLevel(), plugin.getMaxDropAmount());
                if (deliverItem(entry.getDeliveryMode(), built, context, definition, groundTarget, world, location)) {
                    runCommands(entry, context, built);
                    sendRewardMessage(entry, context, definition, built);
                }
            }
        }
    }

    private boolean matchesConditions(DropConditions conditions, DropContext context, String profileId, long now) {
        if (conditions == null || !conditions.hasAnyRestrictions()) {
            return true;
        }

        if (!conditions.getWorlds().isEmpty() || !conditions.getDisabledWorlds().isEmpty()) {
            World world = context.getWorld();
            if (world == null) {
                return false;
            }

            String worldName = world.getName().toLowerCase(Locale.ROOT);
            boolean disabled = conditions.getDisabledWorlds().stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(worldName::equals);
            if (disabled) {
                return false;
            }

            boolean enabled = conditions.getWorlds().stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(worldName::equals);
            if (!conditions.getWorlds().isEmpty() && !enabled) {
                return false;
            }
        }

        if (!conditions.getBiomes().isEmpty()) {
            if (context.getBiome() == null) {
                return false;
            }
            String biomeName = context.getBiome().name();
            boolean matched = conditions.getBiomes().stream().map(String::toUpperCase).anyMatch(biomeName::equals);
            if (!matched) {
                return false;
            }
        }

        if (!conditions.getTargets().isEmpty()) {
            String currentTarget = context.getTargetTypeKey();
            if (currentTarget == null || currentTarget.isBlank()) {
                return false;
            }
            boolean matched = conditions.getTargets().stream().map(String::toUpperCase).anyMatch(currentTarget::equals);
            if (!matched) {
                return false;
            }
        }

        if (!conditions.getPermission().isBlank()) {
            Player player = context.getPlayer();
            if (player == null || !player.hasPermission(conditions.getPermission())) {
                return false;
            }
        }

        if (!conditions.getTools().isEmpty()) {
            if (!matchesTool(context.getTool(), conditions.getTools())) {
                return false;
            }
        }

        if (conditions.getSilkTouchMode() == SilkTouchMode.REQUIRE && context.getSilkTouchLevel() <= 0) {
            return false;
        }
        if (conditions.getSilkTouchMode() == SilkTouchMode.FORBID && context.getSilkTouchLevel() > 0) {
            return false;
        }

        if (context.getFortuneLevel() < conditions.getFortuneMin()) {
            return false;
        }

        if (conditions.getCooldownMs() > 0L && context.getPlayer() != null) {
            Long lastTriggeredAt = getLastTriggeredAt(context.getPlayer().getUniqueId(), profileId);
            if (lastTriggeredAt != null && now - lastTriggeredAt < conditions.getCooldownMs()) {
                return false;
            }
        }

        if (conditions.getTimeMin() != null && conditions.getTimeMax() != null) {
            World world = context.getWorld();
            if (world == null) {
                return false;
            }

            int current = (int) (world.getTime() % 24000L);
            int min = conditions.getTimeMin();
            int max = conditions.getTimeMax();
            boolean inRange = min <= max ? current >= min && current <= max : current >= min || current <= max;
            if (!inRange) {
                return false;
            }
        }

        if (!conditions.getWeather().isEmpty()) {
            World world = context.getWorld();
            if (world == null) {
                return false;
            }

            DropWeather currentWeather = currentWeather(world);
            if (!conditions.getWeather().contains(currentWeather)) {
                return false;
            }
        }

        return true;
    }

    private Long getLastTriggeredAt(UUID playerId, String profileId) {
        Map<String, Long> profileMap = cooldownByPlayerAndProfile.get(playerId);
        if (profileMap == null) {
            return null;
        }
        return profileMap.get(profileId);
    }

    private void markCooldown(DropConditions conditions, Player player, String profileId, long now) {
        if (conditions == null || conditions.getCooldownMs() <= 0L || player == null) {
            return;
        }

        cooldownByPlayerAndProfile
            .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
            .put(profileId, now);
    }

    private void cleanupCooldownCache(long now) {
        if (now < nextCooldownCleanupAt) {
            return;
        }
        nextCooldownCleanupAt = now + COOLDOWN_CLEANUP_INTERVAL_MS;

        Iterator<Map.Entry<UUID, Map<String, Long>>> playerIterator = cooldownByPlayerAndProfile.entrySet().iterator();
        while (playerIterator.hasNext()) {
            Map<String, Long> profileMap = playerIterator.next().getValue();
            profileMap.values().removeIf(last -> now - last > COOLDOWN_STALE_TTL_MS);
            if (profileMap.isEmpty()) {
                playerIterator.remove();
            }
        }
    }

    private DropWeather currentWeather(World world) {
        if (world.isThundering()) {
            return DropWeather.THUNDER;
        }
        if (world.hasStorm()) {
            return DropWeather.RAIN;
        }
        return DropWeather.CLEAR;
    }

    private boolean matchesTool(ItemStack tool, Collection<String> requirements) {
        if (tool == null || tool.getType() == Material.AIR) {
            return false;
        }

        Material material = tool.getType();
        String materialName = material.name();

        for (String rawRequirement : requirements) {
            String requirement = rawRequirement.trim();
            if (requirement.isEmpty()) {
                continue;
            }

            String upper = requirement.toUpperCase(Locale.ROOT);
            if (upper.startsWith("TAG:")) {
                String tag = upper.substring(4);
                if (matchesToolTag(materialName, tag)) {
                    return true;
                }
                continue;
            }

            if (materialName.equals(upper)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesToolTag(String materialName, String tag) {
        return switch (tag) {
            case "PICKAXE", "PICKAXES" -> materialName.endsWith("_PICKAXE");
            case "AXE", "AXES" -> materialName.endsWith("_AXE");
            case "SHOVEL", "SHOVELS", "SPADE", "SPADES" -> materialName.endsWith("_SHOVEL");
            case "HOE", "HOES" -> materialName.endsWith("_HOE");
            case "SWORD", "SWORDS" -> materialName.endsWith("_SWORD");
            case "SHEARS" -> materialName.equals("SHEARS");
            case "FISHING_ROD", "ROD" -> materialName.equals("FISHING_ROD");
            default -> false;
        };
    }

    private boolean deliverItem(
        DropDeliveryMode mode,
        ItemStack itemStack,
        DropContext context,
        DropDefinition definition,
        Collection<ItemStack> groundTarget,
        World world,
        Location location
    ) {
        Player player = context.getPlayer();
        int remaining = Math.max(1, itemStack.getAmount());
        int maxStackSize = Math.max(1, itemStack.getMaxStackSize());
        boolean deliveredAny = false;
        while (remaining > 0) {
            ItemStack chunk = FoItemStacks.cloneItem(itemStack);
            chunk.setAmount(Math.min(maxStackSize, remaining));
            remaining -= chunk.getAmount();

            FoDropRewardEvent event = new FoDropRewardEvent(
                context.getEventType().name(),
                definition.getId(),
                definition.getName(),
                mode.name(),
                context.getTargetTypeKey(),
                player,
                location,
                chunk
            );
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                if (event.isHandled()) {
                    deliveredAny = true;
                }
                continue;
            }

            ItemStack finalChunk = event.getItemStack();
            if (finalChunk == null || finalChunk.getType() == Material.AIR || finalChunk.getAmount() <= 0) {
                continue;
            }

            if (deliverStack(mode, finalChunk, player, groundTarget, world, location)) {
                deliveredAny = true;
            }
        }
        return deliveredAny;
    }

    private boolean deliverStack(
        DropDeliveryMode mode,
        ItemStack itemStack,
        Player player,
        Collection<ItemStack> groundTarget,
        World world,
        Location location
    ) {
        int remaining = Math.max(1, itemStack.getAmount());
        int maxStackSize = Math.max(1, itemStack.getMaxStackSize());
        boolean deliveredAny = false;
        while (remaining > 0) {
            ItemStack chunk = FoItemStacks.cloneItem(itemStack);
            chunk.setAmount(Math.min(maxStackSize, remaining));
            remaining -= chunk.getAmount();

            if (mode == DropDeliveryMode.INVENTORY && player != null) {
                if (groundTarget == null) {
                    inventoryDeposits.deposit(
                        player,
                        chunk,
                        location,
                        OverflowPolicy.DROP_OVERFLOW,
                        InventoryDepositResultMode.BASIC
                    );
                } else {
                    Map<Integer, ItemStack> overflow = player.getInventory().addItem(chunk);
                    for (ItemStack leftover : overflow.values()) {
                        spawnOrCollect(leftover, groundTarget, world, location);
                    }
                }
                deliveredAny = true;
                continue;
            }

            spawnOrCollect(chunk, groundTarget, world, location);
            deliveredAny = true;
        }
        return deliveredAny;
    }

    private void spawnOrCollect(ItemStack itemStack, Collection<ItemStack> groundTarget, World world, Location location) {
        if (groundTarget != null) {
            groundTarget.add(itemStack);
            return;
        }
        world.dropItemNaturally(location, itemStack);
    }

    private void runCommands(CustomDropEntry entry, DropContext context, ItemStack droppedItem) {
        if (!entry.hasCommands()) {
            return;
        }

        Map<String, String> placeholders = null;
        for (DropCommandAction action : entry.getCommands()) {
            String rendered = action.getNormalizedCommand();
            if (rendered.isBlank()) {
                continue;
            }

            if (action.hasPlaceholders()) {
                if (placeholders == null) {
                    placeholders = createCommandPlaceholders(context, droppedItem);
                }
                rendered = renderCommand(rendered, placeholders);
                if (rendered.isBlank()) {
                    continue;
                }
            }

            if (action.getSenderType() == DropCommandSenderType.PLAYER) {
                if (context.getPlayer() == null) {
                    continue;
                }
                dispatch(context.getPlayer(), rendered);
                continue;
            }

            dispatch(Bukkit.getConsoleSender(), rendered);
        }
    }

    private Map<String, String> createCommandPlaceholders(DropContext context, ItemStack itemStack) {
        Player player = context.getPlayer();
        String worldName = context.getWorld() != null ? context.getWorld().getName() : "";
        String playerName = player != null ? player.getName() : "";
        String playerUuid = player != null ? player.getUniqueId().toString() : "";
        String itemName = itemStack != null ? itemStack.getType().name().toLowerCase(Locale.ROOT) : "";
        String amount = itemStack != null ? String.valueOf(itemStack.getAmount()) : "0";
        return Map.of(
            "player", playerName,
            "uuid", playerUuid,
            "world", worldName,
            "event", context.getEventType().name().toLowerCase(Locale.ROOT),
            "item", itemName,
            "amount", amount
        );
    }

    private String renderCommand(String template, Map<String, String> placeholders) {
        return CommandPlaceholders.apply(template, placeholders);
    }

    private void dispatch(CommandSender sender, String commandLine) {
        String normalized = CommandSafety.normalizeCommandLine(commandLine);
        if (normalized.isBlank()) {
            return;
        }

        UnsafeCommandType unsafeType = CommandSafety.unsafeType(normalized);
        if (isUnsafeRewardCommandBlocked(unsafeType)) {
            plugin.getLogger().warning("Blocked unsafe " + unsafeType.displayName() + " drop reward command: " + normalized);
            return;
        }

        Bukkit.dispatchCommand(sender, normalized);
    }

    private void sendRewardMessage(CustomDropEntry entry, DropContext context, DropDefinition definition, ItemStack itemStack) {
        if (!entry.isSendMessage() || context.getPlayer() == null) {
            return;
        }

        plugin.messages().sendConfigured(
            context.getPlayer(),
            "custom-drop-message",
            "{profile}", definition.getName(),
            "{profile_id}", definition.getId(),
            "{item}", formatRewardName(entry, itemStack),
            "{amount}", String.valueOf(Math.max(1, itemStack.getAmount())),
            "{chance}", trimChance(entry.getChance())
        );
    }

    private String formatRewardName(CustomDropEntry entry, ItemStack itemStack) {
        if (!entry.isItemReward()) {
            return "command reward";
        }
        return itemStack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String trimChance(double chance) {
        if (Math.floor(chance) == chance) {
            return Integer.toString((int) chance);
        }
        return String.format(Locale.US, "%.2f", chance);
    }

    private boolean isUnsafeRewardCommandBlocked(UnsafeCommandType unsafeType) {
        return switch (unsafeType) {
            case OPERATOR -> plugin.shouldBlockOperatorRewardCommands();
            case GAMEMODE -> plugin.shouldBlockGamemodeRewardCommands();
            case NONE -> false;
        };
    }

    public static int getSilkTouchLevel(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) {
            return 0;
        }
        return tool.getEnchantmentLevel(Enchantment.SILK_TOUCH);
    }

    public static int getFortuneLevel(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) {
            return 0;
        }
        return tool.getEnchantmentLevel(Enchantment.FORTUNE);
    }
}
