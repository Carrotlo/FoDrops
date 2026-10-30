package me.foesio.foDrops.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Reports one accepted item chunk after FoDrops delivery or an external handoff. */
public final class FoDropItemRewardProcessedEvent extends Event {
    public enum Outcome { DELIVERED_BY_FODROPS, HANDLED_EXTERNALLY }

    private static final HandlerList HANDLERS = new HandlerList();

    private final String eventType;
    private final String profileId;
    private final String entryId;
    private final String targetTypeKey;
    private final String deliveryMode;
    private final Player player;
    private final Location location;
    private final ItemStack itemStack;
    private final Outcome outcome;

    public FoDropItemRewardProcessedEvent(String eventType, String profileId, String entryId,
                                          String targetTypeKey, String deliveryMode, Player player,
                                          Location location, ItemStack itemStack, Outcome outcome) {
        this.eventType = eventType;
        this.profileId = profileId;
        this.entryId = entryId;
        this.targetTypeKey = targetTypeKey;
        this.deliveryMode = deliveryMode;
        this.player = player;
        this.location = location == null ? null : location.clone();
        this.itemStack = itemStack.clone();
        this.outcome = outcome;
    }

    public String getEventType() { return eventType; }
    public String getProfileId() { return profileId; }
    public String getEntryId() { return entryId; }
    public String getTargetTypeKey() { return targetTypeKey; }
    public String getDeliveryMode() { return deliveryMode; }
    public Player getPlayer() { return player; }
    public Location getLocation() { return location == null ? null : location.clone(); }
    public ItemStack getItemStack() { return itemStack.clone(); }
    public Outcome getOutcome() { return outcome; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
