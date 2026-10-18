package me.foesio.foDrops.drop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DropEnginePdcTest {

    @Test
    void requiresEveryKeyAndSupportsAnyPresentValue() {
        Map<String, List<String>> requirements = Map.of(
            "fodungeons:dungeon-type", List.of("egyptian"),
            "fodungeons:mob-profile", List.of("*"));

        assertTrue(DropEngine.matchesPersistentData(requirements, Map.of(
            "FoDungeons:Dungeon-Type", "EGYPTIAN",
            "fodungeons:mob-profile", "future_profile")));
        assertFalse(DropEngine.matchesPersistentData(requirements, Map.of(
            "fodungeons:dungeon-type", "egyptian")));
        assertFalse(DropEngine.matchesPersistentData(requirements, Map.of(
            "fodungeons:dungeon-type", "crypt",
            "fodungeons:mob-profile", "future_profile")));
    }
}
