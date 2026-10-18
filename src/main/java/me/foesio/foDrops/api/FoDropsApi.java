package me.foesio.foDrops.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Read-only matching API for owner-tagged FoDrops profiles. */
public interface FoDropsApi {
    boolean matchesBlockProfileTag(Player player, Block block, ItemStack tool, String tag);
}
