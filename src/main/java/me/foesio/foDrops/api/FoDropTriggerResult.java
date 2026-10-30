package me.foesio.foDrops.api;

import java.util.List;

/** Selection result, not a guarantee that a random reward roll succeeded. */
public record FoDropTriggerResult(Status status, List<String> matchedProfileIds,
                                  boolean wouldCancelVanillaDrops) {
    public enum Status { PROCESSED, NO_MATCH, UNAVAILABLE, REENTRANT }

    public FoDropTriggerResult {
        matchedProfileIds = List.copyOf(matchedProfileIds);
    }
}
