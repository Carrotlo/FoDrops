package me.foesio.foDrops.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;
import java.util.Map;

/** Fired immediately before one custom-drop chance is rolled. */
public final class FoDropEntryChanceEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String eventType;
    private final String profileId;
    private final String entryId;
    private final Set<String> profileTags;
    private final Set<String> entryTags;
    private final double originalChance;
    private final boolean bonusRoll;
    private final Map<String, String> targetPersistentData;
    private double chance;

    public FoDropEntryChanceEvent(Player player, String eventType, String profileId, String entryId,
                                  Set<String> profileTags, Set<String> entryTags, double originalChance,
                                  boolean bonusRoll, Map<String, String> targetPersistentData) {
        this.player = player;
        this.eventType = eventType;
        this.profileId = profileId;
        this.entryId = entryId;
        this.profileTags = Set.copyOf(profileTags);
        this.entryTags = Set.copyOf(entryTags);
        this.originalChance = originalChance;
        this.chance = originalChance;
        this.bonusRoll = bonusRoll;
        this.targetPersistentData = Map.copyOf(targetPersistentData);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public Player getPlayer() { return player; }
    public String getEventType() { return eventType; }
    public String getProfileId() { return profileId; }
    public String getEntryId() { return entryId; }
    public Set<String> getProfileTags() { return profileTags; }
    public Set<String> getEntryTags() { return entryTags; }
    public double getOriginalChance() { return originalChance; }
    public boolean isBonusRoll() { return bonusRoll; }
    public Map<String, String> getTargetPersistentData() { return targetPersistentData; }
    public double getChance() { return chance; }
    public void setChance(double chance) { this.chance = Math.max(0.0D, Math.min(100.0D, chance)); }
}
