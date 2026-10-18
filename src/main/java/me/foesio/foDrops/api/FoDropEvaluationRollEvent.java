package me.foesio.foDrops.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.Map;

/**
 * Fired once after FoDrops has selected the matching profiles and before rewards are rolled.
 * Integrations may request bounded, independent reruns of the entire selected profile set.
 */
public final class FoDropEvaluationRollEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private static final int MAX_ADDITIONAL_ROLLS = 8;

    private final Player player;
    private final String eventType;
    private final String targetTypeKey;
    private final List<String> matchedProfileIds;
    private final Map<String, String> targetPersistentData;
    private int additionalRolls;

    public FoDropEvaluationRollEvent(Player player, String eventType, String targetTypeKey,
                                     List<String> matchedProfileIds, Map<String, String> targetPersistentData) {
        this.player = player;
        this.eventType = eventType;
        this.targetTypeKey = targetTypeKey;
        this.matchedProfileIds = List.copyOf(matchedProfileIds);
        this.targetPersistentData = Map.copyOf(targetPersistentData);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public Player getPlayer() {
        return player;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTargetTypeKey() {
        return targetTypeKey;
    }

    public List<String> getMatchedProfileIds() {
        return matchedProfileIds;
    }

    public Map<String, String> getTargetPersistentData() {
        return targetPersistentData;
    }

    public int getAdditionalRolls() {
        return additionalRolls;
    }

    public void setAdditionalRolls(int additionalRolls) {
        this.additionalRolls = Math.max(0, Math.min(MAX_ADDITIONAL_ROLLS, additionalRolls));
    }

    public void requestAdditionalRoll() {
        setAdditionalRolls(additionalRolls + 1);
    }
}
