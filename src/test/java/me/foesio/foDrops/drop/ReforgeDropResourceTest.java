package me.foesio.foDrops.drop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ReforgeDropResourceTest {

    @Test
    void foDropsAvoidsCircularDependencyWithFoReforge() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream);
        YamlConfiguration plugin = YamlConfiguration.loadConfiguration(
            new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertFalse(plugin.getStringList("softdepend").contains("FoReforge"));
    }

    @Test
    void bundledProfilesContainApprovedTenMobReforges() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("reforge-drops.yml");
        assertNotNull(stream);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
            new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection profiles = yaml.getConfigurationSection("drops");
        assertNotNull(profiles);

        Pattern commandId = Pattern.compile("foreforgeadmin give \\{player} ([a-z0-9-]+)");
        Set<String> ids = new HashSet<>();
        for (String profileId : profiles.getKeys(false)) {
            assertTrue(profiles.getStringList(profileId + ".conditions.targets").isEmpty(), profileId);
            assertEquals(List.of("FoReforge"),
                profiles.getStringList(profileId + ".conditions.required-plugins"), profileId);
            assertNotNull(profiles.getConfigurationSection(profileId + ".conditions.entity-pdc"), profileId);
            ConfigurationSection drops = profiles.getConfigurationSection(profileId + ".custom-drops");
            assertNotNull(drops, profileId);
            for (String dropId : drops.getKeys(false)) {
                String command = drops.getString(dropId + ".commands.give.command", "");
                Matcher matcher = commandId.matcher(command);
                assertTrue(matcher.matches(), profileId + "/" + dropId);
                ids.add(matcher.group(1));
            }
        }
        assertEquals(Set.of("cats-paw", "tomblight", "cat-eye", "illagers-mark", "splitter",
            "far-eye", "iron-bullet", "current-rider", "dust-veil", "egyptian-sifter"), ids);
    }

    @Test
    void oneTimeMigrationCopyPreservesNestedCommandsAndPdcRules() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("reforge-drops.yml");
        assertNotNull(stream);
        YamlConfiguration additions = YamlConfiguration.loadConfiguration(
            new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection profiles = additions.getConfigurationSection("drops");
        assertNotNull(profiles);
        YamlConfiguration live = new YamlConfiguration();

        for (String profileId : profiles.getKeys(false)) {
            DropStore.copyReforgeDefault(profiles.getConfigurationSection(profileId), live, "drops." + profileId);
        }

        assertEquals("foreforgeadmin give {player} cats-paw",
            live.getString("drops.foreforge_egyptian_cat.custom-drops.cats-paw.commands.give.command"));
        assertEquals(List.of("egyptian_cat"),
            live.getStringList("drops.foreforge_egyptian_cat.conditions.entity-pdc.fodungeons:mob-profile"));
        assertEquals(List.of("reforge-token"),
            live.getStringList("drops.foreforge_egyptian_cat.custom-drops.cats-paw.tags"));
        assertEquals(4, live.getConfigurationSection("drops").getKeys(false).size());
    }

    @Test
    void absentPluginRemovesOnlyUntouchedTaggedDefaults() {
        YamlConfiguration bundled = bundledProfiles();
        ConfigurationSection defaults = bundled.getConfigurationSection("drops");
        YamlConfiguration live = new YamlConfiguration();
        for (String id : defaults.getKeys(false)) {
            DropStore.copySection(defaults.getConfigurationSection(id), live, "drops." + id);
            live.set("drops." + id + ".tags", List.of());
            ConfigurationSection entries = defaults.getConfigurationSection(id + ".custom-drops");
            for (String entryId : entries.getKeys(false)) {
                String path = "drops." + id + ".custom-drops." + entryId;
                live.set(path + ".tags", List.of("reforge-token"));
                DropStore.copySection(entries.getConfigurationSection(entryId + ".commands.give"), live,
                    path + ".commands.0");
                live.set(path + ".commands.give", null);
            }
        }
        live.set("drops.owner_profile.name", "Keep me");
        live.set("drops.foreforge_egyptian_cat.custom-drops.cat-eye.chance", 5.0D);

        assertEquals(List.of("foreforge_egyptian_vindicator", "foreforge_egyptian_parched",
            "foreforge_egyptian_all"), DropStore.removeUntouchedReforgeDefaults(live, defaults));
        assertNotNull(live.getConfigurationSection("drops.foreforge_egyptian_cat"));
        assertNotNull(live.getConfigurationSection("drops.owner_profile"));
        assertFalse(live.contains("drops.foreforge_egyptian_vindicator"));
    }

    @Test
    void absentPluginRemovesNewlyInstalledDefaultsBeforeNormalSave() {
        YamlConfiguration bundled = bundledProfiles();
        ConfigurationSection defaults = bundled.getConfigurationSection("drops");
        YamlConfiguration live = new YamlConfiguration();
        for (String id : defaults.getKeys(false)) {
            DropStore.copySection(defaults.getConfigurationSection(id), live, "drops." + id);
        }

        assertEquals(4, DropStore.removeUntouchedReforgeDefaults(live, defaults).size());
        assertTrue(live.getConfigurationSection("drops") == null
            || live.getConfigurationSection("drops").getKeys(false).isEmpty());
    }

    private static YamlConfiguration bundledProfiles() {
        InputStream stream = ReforgeDropResourceTest.class.getClassLoader().getResourceAsStream("reforge-drops.yml");
        assertNotNull(stream);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
