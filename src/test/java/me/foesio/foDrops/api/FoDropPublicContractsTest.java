package me.foesio.foDrops.api;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoDropPublicContractsTest {
    @Test
    void snapshotDoesNotExposeMutableCollections() {
        FoDropEntrySnapshot entry = new FoDropEntrySnapshot("entry", Set.of("token"), 25,
            1, 2, "GROUND", true, null);
        FoDropProfileSnapshot profile = new FoDropProfileSnapshot("profile", "Profile", "BLOCK_BREAK",
            Set.of("mine"), 0, false, false, new ArrayList<>(List.of(entry)));

        assertThrows(UnsupportedOperationException.class, () -> profile.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> profile.tags().add("extra"));
    }

    @Test
    void triggerRequestCopiesCallerInputs() {
        Location originalLocation = new Location(null, 1, 2, 3);
        Map<String, String> originalData = new HashMap<>(Map.of("plugin:key", "value"));
        FoDropTriggerRequest request = new FoDropTriggerRequest("BLOCK_BREAK", null,
            originalLocation, null, "STONE", originalData);

        originalLocation.setX(90);
        originalData.clear();
        request.location().setX(80);
        assertEquals(1, request.location().getX());
        assertEquals("value", request.targetPersistentData().get("plugin:key"));
    }

    @Test
    void oldRewardConstructorRemainsUsableAndNewOneCarriesEntryId() {
        FoDropRewardEvent old = new FoDropRewardEvent("BLOCK_BREAK", "profile", "Profile",
            "GROUND", "STONE", null, null, null);
        FoDropRewardEvent current = new FoDropRewardEvent("BLOCK_BREAK", "profile", "entry", "Profile",
            "GROUND", "STONE", null, null, null);

        assertEquals("", old.getEntryId());
        assertEquals("entry", current.getEntryId());
    }
}
