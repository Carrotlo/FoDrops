package me.foesio.foDrops.api;

import java.util.List;
import java.util.Set;

/** Immutable view of active profile metadata and entries, without internal conditions or commands. */
public record FoDropProfileSnapshot(String id, String name, String eventType, Set<String> tags,
                                    int priority, boolean stopProcessing, boolean cancelVanillaDrops,
                                    List<FoDropEntrySnapshot> entries) {
    public FoDropProfileSnapshot {
        tags = Set.copyOf(tags);
        entries = List.copyOf(entries);
    }
}
