package me.foesio.foDrops.drop;

import me.foesio.core.item.FoItemStacks;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class CustomDropEntry {
    private static final int FALLBACK_MAX_AMOUNT = 64;

    private final String id;
    private final Set<String> tags;
    private ItemStack item;
    private double chance;
    private int minAmount;
    private int maxAmount;
    private DropDeliveryMode deliveryMode;
    private boolean itemReward;
    private boolean sendMessage;
    private boolean respectFortune;
    private final List<DropCommandAction> commands;

    public CustomDropEntry(ItemStack item, double chance, int minAmount, int maxAmount, DropDeliveryMode deliveryMode, List<DropCommandAction> commands) {
        this(item, chance, minAmount, maxAmount, deliveryMode, commands, FALLBACK_MAX_AMOUNT);
    }

    public CustomDropEntry(
        ItemStack item,
        double chance,
        int minAmount,
        int maxAmount,
        DropDeliveryMode deliveryMode,
        List<DropCommandAction> commands,
        int maxAmountLimit
    ) {
        this(item, chance, minAmount, maxAmount, deliveryMode, commands, maxAmountLimit, true);
    }

    public CustomDropEntry(
        ItemStack item,
        double chance,
        int minAmount,
        int maxAmount,
        DropDeliveryMode deliveryMode,
        List<DropCommandAction> commands,
        int maxAmountLimit,
        boolean itemReward
    ) {
        this(item, chance, minAmount, maxAmount, deliveryMode, commands, maxAmountLimit, itemReward, false);
    }

    public CustomDropEntry(
        ItemStack item,
        double chance,
        int minAmount,
        int maxAmount,
        DropDeliveryMode deliveryMode,
        List<DropCommandAction> commands,
        int maxAmountLimit,
        boolean itemReward,
        boolean sendMessage
    ) {
        this(item, chance, minAmount, maxAmount, deliveryMode, commands, maxAmountLimit, itemReward, sendMessage, false);
    }

    public CustomDropEntry(
        ItemStack item,
        double chance,
        int minAmount,
        int maxAmount,
        DropDeliveryMode deliveryMode,
        List<DropCommandAction> commands,
        int maxAmountLimit,
        boolean itemReward,
        boolean sendMessage,
        boolean respectFortune
    ) {
        this("", Set.of(), item, chance, minAmount, maxAmount, deliveryMode, commands, maxAmountLimit,
            itemReward, sendMessage, respectFortune);
    }

    public CustomDropEntry(
        String id,
        Set<String> tags,
        ItemStack item,
        double chance,
        int minAmount,
        int maxAmount,
        DropDeliveryMode deliveryMode,
        List<DropCommandAction> commands,
        int maxAmountLimit,
        boolean itemReward,
        boolean sendMessage,
        boolean respectFortune
    ) {
        this.id = id == null ? "" : id.trim();
        this.tags = new LinkedHashSet<>();
        if (tags != null) {
            tags.stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .forEach(this.tags::add);
        }
        setItem(item);
        setChance(chance);
        setAmountRange(minAmount, maxAmount, maxAmountLimit);
        setDeliveryMode(deliveryMode);
        setItemReward(itemReward);
        setSendMessage(sendMessage);
        setRespectFortune(respectFortune);
        this.commands = new ArrayList<>(commands);
    }

    public String getId() {
        return id;
    }

    public Set<String> getTags() {
        return tags;
    }

    public boolean hasTag(String tag) {
        return tag != null && tags.contains(tag.trim().toLowerCase(Locale.ROOT));
    }

    public CustomDropEntry(ItemStack item, double chance, int minAmount, int maxAmount, DropDeliveryMode deliveryMode) {
        this(item, chance, minAmount, maxAmount, deliveryMode, List.of());
    }

    public CustomDropEntry(ItemStack item, double chance, int minAmount, int maxAmount, DropDeliveryMode deliveryMode, int maxAmountLimit) {
        this(item, chance, minAmount, maxAmount, deliveryMode, List.of(), maxAmountLimit);
    }

    public ItemStack getItem() {
        return FoItemStacks.cloneItem(item);
    }

    public void setItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            this.item = new ItemStack(Material.STONE);
            return;
        }
        this.item = FoItemStacks.cloneItem(item);
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = Math.max(0.0D, Math.min(100.0D, chance));
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public void setAmountRange(int minAmount, int maxAmount) {
        setAmountRange(minAmount, maxAmount, FALLBACK_MAX_AMOUNT);
    }

    public void setAmountRange(int minAmount, int maxAmount, int maxAmountLimit) {
        int limit = Math.max(1, maxAmountLimit);
        int min = Math.max(1, minAmount);
        int max = Math.max(min, maxAmount);
        min = Math.min(min, limit);
        max = Math.min(max, limit);
        this.minAmount = min;
        this.maxAmount = max;
    }

    public DropDeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DropDeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode == null ? DropDeliveryMode.GROUND : deliveryMode;
    }

    public boolean isItemReward() {
        return itemReward;
    }

    public void setItemReward(boolean itemReward) {
        this.itemReward = itemReward;
        if (!itemReward) {
            this.respectFortune = false;
        }
    }

    public boolean isSendMessage() {
        return sendMessage;
    }

    public void setSendMessage(boolean sendMessage) {
        this.sendMessage = sendMessage;
    }

    public boolean isRespectFortune() {
        return respectFortune;
    }

    public void setRespectFortune(boolean respectFortune) {
        this.respectFortune = itemReward && respectFortune;
    }

    public List<DropCommandAction> getCommands() {
        return commands;
    }

    public boolean hasCommands() {
        return !commands.isEmpty();
    }

    public boolean shouldDrop(Random random) {
        return shouldDrop(random, chance);
    }

    public boolean shouldDrop(Random random, double effectiveChance) {
        double checkedChance = Math.max(0.0D, Math.min(100.0D, effectiveChance));
        if (checkedChance <= 0.0D) {
            return false;
        }
        if (checkedChance >= 100.0D) {
            return true;
        }
        return random.nextDouble() * 100.0D <= checkedChance;
    }

    public ItemStack createDropItem(Random random) {
        return createDropItem(random, 0, FALLBACK_MAX_AMOUNT);
    }

    public ItemStack createDropItem(Random random, int fortuneLevel, int maxAmountLimit) {
        ItemStack cloned = FoItemStacks.cloneItem(item);
        int amount = minAmount;
        if (maxAmount > minAmount) {
            amount = minAmount + random.nextInt((maxAmount - minAmount) + 1);
        }
        if (respectFortune && fortuneLevel > 0) {
            int bound = fortuneLevel >= Integer.MAX_VALUE - 2 ? Integer.MAX_VALUE : fortuneLevel + 2;
            int multiplier = Math.max(1, random.nextInt(bound));
            amount = (int) Math.min(Math.max(1, maxAmountLimit), (long) amount * multiplier);
        }
        cloned.setAmount(Math.max(1, amount));
        return cloned;
    }
}
