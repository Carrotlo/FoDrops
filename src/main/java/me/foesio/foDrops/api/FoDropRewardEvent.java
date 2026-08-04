package me.foesio.foDrops.api;

import me.foesio.core.item.FoItemStacks;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

public class FoDropRewardEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String eventType;
    private final String profileId;
    private final String profileName;
    private final String deliveryMode;
    private final String targetTypeKey;
    private final Player player;
    private final Location location;
    private ItemStack itemStack;
    private boolean cancelled;
    private boolean handled;

    public FoDropRewardEvent(
        String eventType,
        String profileId,
        String profileName,
        String deliveryMode,
        String targetTypeKey,
        Player player,
        Location location,
        ItemStack itemStack
    ) {
        this.eventType = eventType;
        this.profileId = profileId;
        this.profileName = profileName;
        this.deliveryMode = deliveryMode;
        this.targetTypeKey = targetTypeKey;
        this.player = player;
        this.location = location == null ? null : location.clone();
        this.itemStack = itemStack == null ? null : FoItemStacks.cloneItem(itemStack);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public String getEventType() {
        return eventType;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getDeliveryMode() {
        return deliveryMode;
    }

    public String getTargetTypeKey() {
        return targetTypeKey;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    public ItemStack getItemStack() {
        return itemStack == null ? null : FoItemStacks.cloneItem(itemStack);
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack == null ? null : FoItemStacks.cloneItem(itemStack);
    }

    public boolean isHandled() {
        return handled;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }
}
