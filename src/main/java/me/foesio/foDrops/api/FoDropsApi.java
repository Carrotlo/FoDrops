package me.foesio.foDrops.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/** Bukkit service for active FoDrops profiles. Call from the primary server thread only. */
public interface FoDropsApi {
    /** Condition-only query; ignores cooldowns and does not predict stop-processing. */
    boolean matchesBlockProfileTag(Player player, Block block, ItemStack tool, String tag);

    /** Copies active configured profile metadata and entries, in configured order. */
    List<FoDropProfileSnapshot> getProfiles();

    /** Copies one active profile; returns empty for an unknown ID or disabled provider. */
    Optional<FoDropProfileSnapshot> getProfile(String id);

    /**
     * Runs normal profile selection and rewards. The caller owns the underlying Bukkit event and
     * any vanilla-drop changes. A processed result means profiles matched, not that a random reward won.
     * Invalid input throws IllegalArgumentException; off-thread calls throw IllegalStateException.
     */
    FoDropTriggerResult trigger(FoDropTriggerRequest request);
}
