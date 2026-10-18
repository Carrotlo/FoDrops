package me.foesio.foDrops.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoDropHookEventsTest {
    @Test
    void additionalRollsAreBoundedAndInputsAreImmutable() {
        FoDropEvaluationRollEvent event = new FoDropEvaluationRollEvent(
            null, "MOB_KILL", "ZOMBIE", List.of("one"), Map.of("owner:key", "value"));
        event.setAdditionalRolls(99);
        assertEquals(8, event.getAdditionalRolls());
        assertThrows(UnsupportedOperationException.class, () -> event.getMatchedProfileIds().add("two"));
        assertThrows(UnsupportedOperationException.class, () -> event.getTargetPersistentData().put("x", "y"));
    }

    @Test
    void entryChanceIsClampedAndCarriesTagsAndContext() {
        FoDropEntryChanceEvent event = new FoDropEntryChanceEvent(
            null, "MOB_KILL", "profile", "entry", Set.of("profile-tag"), Set.of("reforge-token"),
            1.0D, false, Map.of("fodungeons:mob-profile", "crypt_guard"));
        event.setChance(150.0D);
        assertEquals(100.0D, event.getChance());
        assertEquals(Set.of("reforge-token"), event.getEntryTags());
        assertEquals("crypt_guard", event.getTargetPersistentData().get("fodungeons:mob-profile"));
    }
}
