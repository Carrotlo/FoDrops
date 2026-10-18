package me.foesio.foDrops.drop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DropStorePdcTest {

    @Test
    void restoresNamespacedKeysContainingDots() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("entity-pdc.my.plugin:mob.profile", List.of("boss", "elite"));
        Map<String, List<String>> loaded = new LinkedHashMap<>();

        DropStore.loadEntityPersistentData(yaml.getConfigurationSection("entity-pdc"), loaded);

        assertEquals(Map.of("my.plugin:mob.profile", List.of("boss", "elite")), loaded);
    }
}
