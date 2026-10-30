package me.foesio.foDrops.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Context for evaluating configured profiles from another plugin. */
public record FoDropTriggerRequest(String eventType, Player player, Location location,
                                   ItemStack tool, String targetTypeKey,
                                   Map<String, String> targetPersistentData) {
    public FoDropTriggerRequest {
        location = location == null ? null : location.clone();
        tool = tool == null ? null : tool.clone();
        targetTypeKey = targetTypeKey == null ? "" : targetTypeKey;
        targetPersistentData = Map.copyOf(targetPersistentData == null ? Map.of() : targetPersistentData);
    }

    @Override public Location location() { return location == null ? null : location.clone(); }
    @Override public ItemStack tool() { return tool == null ? null : tool.clone(); }
}
