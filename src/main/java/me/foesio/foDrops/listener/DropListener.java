package me.foesio.foDrops.listener;

import me.foesio.core.FoCoreContext;
import me.foesio.core.inventory.InventoryDepositResultMode;
import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.item.FoItemStacks;
import me.foesio.core.sound.FoSoundService;
import me.foesio.foDrops.FoDrops;
import me.foesio.foDrops.drop.DropContext;
import me.foesio.foDrops.drop.DropEngine;
import me.foesio.foDrops.drop.DropEvaluation;
import me.foesio.foDrops.drop.DropEventType;
import me.foesio.foDrops.drop.DropStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DropListener implements Listener {
    private static final long TNT_SOURCE_TTL_MS = 120_000L;

    private final DropEngine dropEngine;
    private final InventoryDepositService inventoryDeposits;
    private final Map<BlockKey, PendingTntSource> pendingTntSources = new HashMap<>();
    private final Map<UUID, PendingTntSource> tntSourcesByEntity = new HashMap<>();

    public DropListener(FoDrops plugin, DropStore dropStore, FoCoreContext core, FoSoundService sounds) {
        this.inventoryDeposits = core.inventoryDeposits();
        this.dropEngine = new DropEngine(plugin, dropStore, inventoryDeposits, sounds);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        DropContext context = createContext(
            DropEventType.BLOCK_BREAK,
            event.getPlayer(),
            centered(event.getBlock().getLocation()),
            tool,
            event.getBlock().getType().name()
        );
        DropEvaluation evaluation = dropEngine.evaluate(context);
        if (evaluation.getMatchedDefinitions().isEmpty()) {
            return;
        }

        if (evaluation.isCancelVanillaDrops()) {
            event.setDropItems(false);
        }
        dropEngine.applyRewards(evaluation, context, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        if (handleEntityExplosionDeath(event)) {
            return;
        }

        Player player = event.getEntity().getKiller();
        if (player == null || isNaturalTropicalFishDryOut(event)) {
            return;
        }

        DropContext context = createContext(
            DropEventType.MOB_KILL,
            player,
            event.getEntity().getLocation(),
            player.getInventory().getItemInMainHand(),
            event.getEntityType().name()
        );
        DropEvaluation evaluation = dropEngine.evaluate(context);
        if (evaluation.getMatchedDefinitions().isEmpty()) {
            return;
        }

        if (evaluation.isCancelVanillaDrops()) {
            event.getDrops().clear();
        }
        dropEngine.applyRewards(evaluation, context, event.getDrops());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player != null ? player.getInventory().getItemInMainHand() : null;
        DropContext context = createContext(
            DropEventType.BLOCK_DROP_ITEM,
            player,
            centered(event.getBlockState().getLocation()),
            tool,
            event.getBlockState().getType().name()
        );
        DropEvaluation evaluation = dropEngine.evaluate(context);
        if (evaluation.getMatchedDefinitions().isEmpty()) {
            return;
        }

        if (evaluation.isCancelVanillaDrops()) {
            event.getItems().clear();
        }
        dropEngine.applyRewards(evaluation, context, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        Location location = event.getHook() != null ? event.getHook().getLocation() : player.getLocation();
        String caughtKey = null;
        if (event.getCaught() instanceof Item caughtItem && caughtItem.getItemStack() != null) {
            caughtKey = caughtItem.getItemStack().getType().name();
        }

        DropContext context = createContext(
            DropEventType.PLAYER_FISH,
            player,
            location,
            player.getInventory().getItemInMainHand(),
            caughtKey
        );
        DropEvaluation evaluation = dropEngine.evaluate(context);
        if (evaluation.getMatchedDefinitions().isEmpty()) {
            return;
        }

        if (evaluation.isCancelVanillaDrops() && event.getCaught() instanceof Item caughtItem) {
            caughtItem.remove();
        }
        dropEngine.applyRewards(evaluation, context, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        ExplosionSource source = findPendingSource(BlockKey.from(event.getBlock()));
        Player player = source != null ? source.player() : null;
        ItemStack tool = source != null ? source.tool() : null;
        if (handleExplosionDrops(DropEventType.BLOCK_EXPLOSION, player, tool, event.blockList())) {
            event.setYield(0.0F);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        ExplosionSource source = resolveExplosionSource(event);
        Player player = source != null ? source.player() : null;
        ItemStack tool = source != null ? source.tool() : null;
        if (handleExplosionDrops(DropEventType.BLOCK_EXPLOSION, player, tool, event.blockList())) {
            event.setYield(0.0F);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        ExplosionSource source = resolveExplosionSource(event.getPrimingEntity());
        if (source == null && event.getPrimingBlock() != null) {
            source = findPendingSourceNear(centered(event.getPrimingBlock().getLocation()), 8);
        }
        if (source == null || source.player() == null) {
            return;
        }

        cleanupTntSources();
        pendingTntSources.put(
            BlockKey.from(event.getBlock()),
            new PendingTntSource(source.player().getUniqueId(), cloneTool(source.tool()), System.currentTimeMillis())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        cleanupTntSources();
        pendingTntSources.put(
            BlockKey.from(event.getBlock()),
            new PendingTntSource(player.getUniqueId(), cloneTool(player.getInventory().getItemInMainHand()), System.currentTimeMillis())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosiveInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
            || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || event.getClickedBlock() == null
            || !isPlayerTriggeredExplosive(event.getClickedBlock().getType())) {
            return;
        }

        cleanupTntSources();
        pendingTntSources.put(
            BlockKey.from(event.getClickedBlock()),
            new PendingTntSource(event.getPlayer().getUniqueId(), cloneTool(event.getItem()), System.currentTimeMillis())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }

        PendingTntSource source = pendingTntSources.remove(BlockKey.from(event.getLocation().getBlock()));
        if (source == null) {
            return;
        }

        Player player = Bukkit.getPlayer(source.playerId());
        if (player != null && tnt.getSource() == null) {
            tnt.setSource(player);
        }
        tntSourcesByEntity.put(tnt.getUniqueId(), source);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDropItem(EntityDropItemEvent event) {
        Player player = event.getEntity() instanceof Player entityPlayer ? entityPlayer : null;
        ItemStack tool = player != null ? player.getInventory().getItemInMainHand() : null;
        DropContext context = createContext(
            DropEventType.ENTITY_DROP_ITEM,
            player,
            event.getEntity().getLocation(),
            tool,
            event.getEntityType().name()
        );
        DropEvaluation evaluation = dropEngine.evaluate(context);
        if (evaluation.getMatchedDefinitions().isEmpty()) {
            return;
        }

        if (evaluation.isCancelVanillaDrops()) {
            event.setCancelled(true);
        }
        dropEngine.applyRewards(evaluation, context, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerShear(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        DropContext context = createContext(
            DropEventType.PLAYER_SHEAR_ENTITY,
            player,
            event.getEntity().getLocation(),
            player.getInventory().getItemInMainHand(),
            event.getEntity().getType().name()
        );
        DropEvaluation evaluation = dropEngine.evaluate(context);
        if (evaluation.getMatchedDefinitions().isEmpty()) {
            return;
        }

        if (evaluation.isCancelVanillaDrops()) {
            event.setCancelled(true);
        }
        dropEngine.applyRewards(evaluation, context, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String craftedType = null;
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem != null && currentItem.getType() != Material.AIR) {
            craftedType = currentItem.getType().name();
        } else if (event.getRecipe() != null && event.getRecipe().getResult() != null) {
            craftedType = event.getRecipe().getResult().getType().name();
        }

        DropContext context = createContext(
            DropEventType.PLAYER_CRAFT,
            player,
            player.getLocation(),
            player.getInventory().getItemInMainHand(),
            craftedType
        );
        DropEvaluation evaluation = dropEngine.evaluate(context);
        if (evaluation.getMatchedDefinitions().isEmpty()) {
            return;
        }

        int craftExecutions = countCraftExecutions(event, player, evaluation.isCancelVanillaDrops());
        if (craftExecutions <= 0) {
            return;
        }

        if (evaluation.isCancelVanillaDrops()) {
            event.setCancelled(true);
            event.getInventory().setResult(new ItemStack(Material.AIR));
            event.setCurrentItem(new ItemStack(Material.AIR));
            consumeCraftingIngredients(event, player, craftExecutions);
        }

        for (int i = 0; i < craftExecutions; i++) {
            dropEngine.applyRewards(evaluation, context, null);
        }
    }

    private DropContext createContext(DropEventType eventType, Player player, Location location, ItemStack tool, String targetTypeKey) {
        World world = null;
        Biome biome = null;
        if (location != null) {
            world = location.getWorld();
            if (world != null) {
                biome = location.getBlock().getBiome();
            }
        } else if (player != null) {
            world = player.getWorld();
            location = player.getLocation();
            biome = location.getBlock().getBiome();
        }

        int silk = DropEngine.getSilkTouchLevel(tool);
        int fortune = DropEngine.getFortuneLevel(tool);
        return new DropContext(eventType, player, location, world, biome, tool, silk, fortune, targetTypeKey);
    }

    private Location centered(Location location) {
        return location.clone().add(0.5D, 0.5D, 0.5D);
    }

    private boolean handleExplosionDrops(DropEventType eventType, Player player, ItemStack tool, List<Block> blocks) {
        boolean cancelVanillaDrops = false;

        for (Block block : blocks) {
            DropContext context = createContext(
                eventType,
                player,
                centered(block.getLocation()),
                tool,
                block.getType().name()
            );
            DropEvaluation evaluation = dropEngine.evaluate(context);
            if (evaluation.getMatchedDefinitions().isEmpty()) {
                continue;
            }

            if (evaluation.isCancelVanillaDrops()) {
                cancelVanillaDrops = true;
            }
            dropEngine.applyRewards(evaluation, context, null);
        }

        return cancelVanillaDrops;
    }

    private boolean handleEntityExplosionDeath(EntityDeathEvent event) {
        EntityDamageEvent damage = event.getEntity().getLastDamageCause();
        if (!isExplosionDamage(damage)) {
            return false;
        }

        ExplosionSource source = resolveExplosionSource(damage, event.getEntity().getKiller());
        Player player = source != null ? source.player() : null;
        ItemStack tool = source != null ? source.tool() : null;
        DropContext context = createContext(
            DropEventType.ENTITY_EXPLOSION,
            player,
            event.getEntity().getLocation(),
            tool,
            event.getEntityType().name()
        );
        DropEvaluation evaluation = dropEngine.evaluate(context);
        if (evaluation.isCancelVanillaDrops()) {
            event.getDrops().clear();
        }
        dropEngine.applyRewards(evaluation, context, event.getDrops());
        return true;
    }

    private boolean isExplosionDamage(EntityDamageEvent damage) {
        if (damage == null) {
            return false;
        }

        EntityDamageEvent.DamageCause cause = damage.getCause();
        return cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
            || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
    }

    private boolean isNaturalTropicalFishDryOut(EntityDeathEvent event) {
        EntityDamageEvent damage = event.getEntity().getLastDamageCause();
        return event.getEntityType() == EntityType.TROPICAL_FISH
            && damage != null
            && damage.getCause() == EntityDamageEvent.DamageCause.DRYOUT;
    }

    private int countCraftExecutions(CraftItemEvent event, Player player, boolean replacingVanillaResult) {
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) {
            result = event.getRecipe() != null ? event.getRecipe().getResult() : null;
        }
        if (result == null || result.getType() == Material.AIR) {
            return 0;
        }

        if (!event.isShiftClick()) {
            return 1;
        }

        int ingredientLimit = countCraftableIngredientSets(event.getInventory().getMatrix());
        if (ingredientLimit <= 0) {
            return 0;
        }
        if (replacingVanillaResult) {
            return ingredientLimit;
        }

        return Math.min(ingredientLimit, countResultInventoryFit(player, result));
    }

    private int countCraftableIngredientSets(ItemStack[] matrix) {
        int limit = Integer.MAX_VALUE;
        boolean hasIngredient = false;

        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            hasIngredient = true;
            limit = Math.min(limit, item.getAmount());
        }

        return hasIngredient ? limit : 0;
    }

    private ExplosionSource resolveExplosionSource(EntityExplodeEvent event) {
        ExplosionSource source = resolveExplosionSource(event.getEntity());
        return source != null ? source : findPendingSourceNear(event.getLocation(), 12);
    }

    private ExplosionSource resolveExplosionSource(EntityDamageEvent damage, Player fallbackPlayer) {
        if (damage instanceof EntityDamageByEntityEvent entityDamage) {
            ExplosionSource source = resolveExplosionSource(entityDamage.getDamager());
            if (source != null) {
                return source;
            }
        }

        if (fallbackPlayer != null) {
            return new ExplosionSource(fallbackPlayer, cloneTool(fallbackPlayer.getInventory().getItemInMainHand()));
        }

        return null;
    }

    private ExplosionSource resolveExplosionSource(Entity entity) {
        if (entity instanceof Player player) {
            return new ExplosionSource(player, cloneTool(player.getInventory().getItemInMainHand()));
        }
        if (entity instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            if (source instanceof Player player) {
                PendingTntSource pendingSource = tntSourcesByEntity.get(tnt.getUniqueId());
                ItemStack tool = pendingSource != null ? pendingSource.tool() : player.getInventory().getItemInMainHand();
                return new ExplosionSource(player, cloneTool(tool));
            }

            if (source != null) {
                ExplosionSource explosionSource = resolveExplosionSource(source);
                if (explosionSource != null) {
                    return explosionSource;
                }
            }

            PendingTntSource pendingSource = tntSourcesByEntity.get(tnt.getUniqueId());
            if (pendingSource != null) {
                Player player = Bukkit.getPlayer(pendingSource.playerId());
                return new ExplosionSource(player, pendingSource.tool());
            }
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return new ExplosionSource(player, cloneTool(player.getInventory().getItemInMainHand()));
            }
            if (shooter instanceof Entity shooterEntity) {
                return resolveExplosionSource(shooterEntity);
            }
        }
        return null;
    }

    private ExplosionSource findPendingSource(BlockKey key) {
        cleanupTntSources();
        PendingTntSource source = pendingTntSources.get(key);
        if (source == null) {
            return null;
        }

        Player player = Bukkit.getPlayer(source.playerId());
        return player == null ? null : new ExplosionSource(player, cloneTool(source.tool()));
    }

    private ExplosionSource findPendingSourceNear(Location location, int radius) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        cleanupTntSources();
        UUID worldId = location.getWorld().getUID();
        int radiusSquared = radius * radius;
        PendingTntSource nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (Map.Entry<BlockKey, PendingTntSource> entry : pendingTntSources.entrySet()) {
            BlockKey key = entry.getKey();
            if (!key.worldId().equals(worldId)) {
                continue;
            }

            int dx = key.x() - location.getBlockX();
            int dy = key.y() - location.getBlockY();
            int dz = key.z() - location.getBlockZ();
            int distance = dx * dx + dy * dy + dz * dz;
            if (distance <= radiusSquared && distance < nearestDistance) {
                nearest = entry.getValue();
                nearestDistance = distance;
            }
        }

        if (nearest == null) {
            return null;
        }

        Player player = Bukkit.getPlayer(nearest.playerId());
        return player == null ? null : new ExplosionSource(player, cloneTool(nearest.tool()));
    }

    private boolean isPlayerTriggeredExplosive(Material material) {
        String name = material.name();
        return name.endsWith("_BED") || name.equals("RESPAWN_ANCHOR");
    }

    private ItemStack cloneTool(ItemStack tool) {
        return tool == null ? null : FoItemStacks.cloneItem(tool);
    }

    private void cleanupTntSources() {
        long now = System.currentTimeMillis();
        pendingTntSources.values().removeIf(source -> now - source.createdAt() > TNT_SOURCE_TTL_MS);
        tntSourcesByEntity.values().removeIf(source -> now - source.createdAt() > TNT_SOURCE_TTL_MS);
    }

    private int countResultInventoryFit(Player player, ItemStack result) {
        int resultAmount = Math.max(1, result.getAmount());
        int maxStackSize = Math.max(1, result.getMaxStackSize());
        int fitAmount = 0;

        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                fitAmount += maxStackSize;
                continue;
            }

            if (item.isSimilar(result)) {
                fitAmount += Math.max(0, item.getMaxStackSize() - item.getAmount());
            }
        }

        return fitAmount / resultAmount;
    }

    private void consumeCraftingIngredients(CraftItemEvent event, Player player, int craftExecutions) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack ingredient = matrix[i];
            if (ingredient == null || ingredient.getType() == Material.AIR) {
                continue;
            }

            Material remainder = ingredient.getType().getCraftingRemainingItem();
            int remainingAmount = ingredient.getAmount() - craftExecutions;
            if (remainingAmount > 0) {
                ItemStack remaining = FoItemStacks.cloneItem(ingredient);
                remaining.setAmount(remainingAmount);
                matrix[i] = remaining;
            } else {
                matrix[i] = null;
            }

            if (remainder != null && remainder != Material.AIR) {
                giveCraftingRemainder(player, remainder, craftExecutions);
            }
        }
        event.getInventory().setMatrix(matrix);
    }

    private void giveCraftingRemainder(Player player, Material material, int amount) {
        int remaining = amount;
        int maxStackSize = Math.max(1, material.getMaxStackSize());
        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            ItemStack stack = new ItemStack(material, stackAmount);
            inventoryDeposits.deposit(
                player,
                stack,
                player.getLocation(),
                OverflowPolicy.DROP_OVERFLOW,
                InventoryDepositResultMode.BASIC
            );
            remaining -= stackAmount;
        }
    }

    private record ExplosionSource(Player player, ItemStack tool) {
    }

    private record PendingTntSource(UUID playerId, ItemStack tool, long createdAt) {
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
