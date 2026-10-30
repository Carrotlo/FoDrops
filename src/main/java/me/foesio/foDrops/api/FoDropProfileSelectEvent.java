package me.foesio.foDrops.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Map;
import java.util.Set;

/** Fired for an eligible profile before selection, cooldown marking, or vanilla-drop changes. */
public final class FoDropProfileSelectEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String eventType;
    private final String profileId;
    private final Set<String> profileTags;
    private final String targetTypeKey;
    private final Location location;
    private final Map<String, String> targetPersistentData;
    private boolean cancelled;

    public FoDropProfileSelectEvent(Player player, String eventType, String profileId,
                                    Set<String> profileTags, String targetTypeKey, Location location,
                                    Map<String, String> targetPersistentData) {
        this.player = player;
        this.eventType = eventType;
        this.profileId = profileId;
        this.profileTags = Set.copyOf(profileTags);
        this.targetTypeKey = targetTypeKey;
        this.location = location == null ? null : location.clone();
        this.targetPersistentData = Map.copyOf(targetPersistentData);
    }

    public Player getPlayer() { return player; }
    public String getEventType() { return eventType; }
    public String getProfileId() { return profileId; }
    public Set<String> getProfileTags() { return profileTags; }
    public String getTargetTypeKey() { return targetTypeKey; }
    public Location getLocation() { return location == null ? null : location.clone(); }
    public Map<String, String> getTargetPersistentData() { return targetPersistentData; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
