package me.foesio.foDrops.api;

import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** Immutable view of one configured entry; commands are intentionally omitted. */
public record FoDropEntrySnapshot(String id, Set<String> tags, double chance,
                                  int minAmount, int maxAmount, String deliveryMode,
                                  boolean itemReward, ItemStack item) {
    public FoDropEntrySnapshot {
        tags = Set.copyOf(tags);
        item = item == null ? null : item.clone();
    }

    @Override public ItemStack item() { return item == null ? null : item.clone(); }
}
