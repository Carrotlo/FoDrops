package me.foesio.foDrops.drop;

import me.foesio.core.config.ConfigValueLists;
import me.foesio.core.config.ResourceFiles;
import me.foesio.foDrops.FoDrops;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DropStore {
    private static final String REFORGE_INSTALL_MARKER = "data-migrations.reforge-acquisition-v1";
    private static final String REFORGE_REMOVED_DEFAULTS = "data-migrations.reforge-removed-defaults";

    private final FoDrops plugin;
    private final File dataFile;
    private final Map<String, DropDefinition> drops = new LinkedHashMap<>();
    private final Map<DropEventType, List<DropDefinition>> dropsByEvent = new EnumMap<>(DropEventType.class);

    public DropStore(FoDrops plugin) {
        this.plugin = plugin;
        this.dataFile = ResourceFiles.dataFile(plugin, "drops.yml");
    }

    public void load() {
        ResourceFiles.saveDefault(plugin, "drops.yml");
        if (plugin.getServer().getPluginManager().getPlugin("FoReforge") != null) {
            installReforgeDropDefaults();
        } else {
            removeAbsentReforgeDefaults();
        }
        installReforgeGameplayTags();

        drops.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = yaml.getConfigurationSection("drops");
        if (root != null) {
            int maxProfiles = plugin.getMaxProfiles();
            int maxCustomDrops = plugin.getMaxCustomDropsPerProfile();
            int maxCommands = plugin.getMaxCommandsPerEntry();
            int maxDropAmount = plugin.getMaxDropAmount();

            for (String id : root.getKeys(false)) {
                if (drops.size() >= maxProfiles) {
                    plugin.getLogger().warning("Ignoring extra drop profiles in drops.yml because limits.max-profiles is " + maxProfiles + ".");
                    break;
                }

                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }

                String name = section.getString("name", id);
                DropEventType eventType = DropEventType.fromConfig(section.getString("event"));
                boolean cancelVanillaDrops = section.getBoolean("cancel-vanilla-drops", false);
                int priority = section.getInt("priority", 0);
                boolean stopProcessing = section.getBoolean("stop-processing", false);
                Set<String> profileTags = normalizedTags(section.getStringList("tags"));
                DropConditions conditions = loadConditions(yaml, "drops." + id + ".conditions");

                List<CustomDropEntry> customDrops = new ArrayList<>();
                ConfigurationSection dropSection = section.getConfigurationSection("custom-drops");
                if (dropSection != null) {
                    for (String key : dropSection.getKeys(false)) {
                        if (customDrops.size() >= maxCustomDrops) {
                            plugin.getLogger().warning("Ignoring extra custom drops for profile '" + id + "' because limits.max-custom-drops-per-profile is " + maxCustomDrops + ".");
                            break;
                        }

                        ConfigurationSection entrySection = dropSection.getConfigurationSection(key);
                        if (entrySection == null) {
                            continue;
                        }

                        double chance = entrySection.getDouble("chance", 100.0D);
                        boolean itemReward = entrySection.getBoolean("item-reward", true);
                        boolean sendMessage = entrySection.getBoolean("send-message", false);
                        boolean respectFortune = entrySection.getBoolean("respect-fortune", false);
                        Set<String> entryTags = normalizedTags(entrySection.getStringList("tags"));
                        ItemStack item = entrySection.getItemStack("item");
                        List<DropCommandAction> commands = loadCommands(entrySection, maxCommands);
                        if (item == null || item.getType() == Material.AIR) {
                            if (itemReward) {
                                continue;
                            }
                            item = new ItemStack(Material.COMMAND_BLOCK);
                        }
                        if (!itemReward && commands.isEmpty()) {
                            continue;
                        }

                        int minAmount = entrySection.getInt("min-amount", item.getAmount());
                        int maxAmount = entrySection.getInt("max-amount", minAmount);
                        DropDeliveryMode deliveryMode = DropDeliveryMode.fromConfig(entrySection.getString("delivery-mode"));
                        customDrops.add(new CustomDropEntry(key, entryTags, item, chance, minAmount, maxAmount,
                            deliveryMode, commands, maxDropAmount, itemReward, sendMessage, respectFortune));
                    }
                }

                drops.put(id, new DropDefinition(id, name, eventType, cancelVanillaDrops, priority,
                    stopProcessing, conditions, customDrops, profileTags));
            }
        }

        rebuildEventIndex();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().copyDefaults(false);

        for (DropDefinition drop : drops.values()) {
            String basePath = "drops." + drop.getId();
            yaml.set(basePath + ".name", drop.getName());
            yaml.set(basePath + ".event", drop.getEventType().name());
            yaml.set(basePath + ".cancel-vanilla-drops", drop.isCancelVanillaDrops());
            yaml.set(basePath + ".priority", drop.getPriority());
            yaml.set(basePath + ".stop-processing", drop.isStopProcessing());
            yaml.set(basePath + ".tags", new ArrayList<>(drop.getTags()));
            saveConditions(yaml, basePath + ".conditions", drop.getConditions());

            List<CustomDropEntry> entries = drop.getCustomDrops();
            Set<String> savedEntryKeys = new LinkedHashSet<>();
            for (int i = 0; i < entries.size(); i++) {
                CustomDropEntry entry = entries.get(i);
                String entryKey = entry.getId().matches("[a-zA-Z0-9_-]+") ? entry.getId() : String.valueOf(i);
                int suffix = i;
                while (!savedEntryKeys.add(entryKey)) entryKey = String.valueOf(++suffix);
                String entryPath = basePath + ".custom-drops." + entryKey;
                yaml.set(entryPath + ".item-reward", entry.isItemReward());
                yaml.set(entryPath + ".send-message", entry.isSendMessage());
                yaml.set(entryPath + ".respect-fortune", entry.isRespectFortune());
                yaml.set(entryPath + ".tags", new ArrayList<>(entry.getTags()));
                yaml.set(entryPath + ".item", entry.getItem());
                yaml.set(entryPath + ".chance", entry.getChance());
                yaml.set(entryPath + ".min-amount", entry.getMinAmount());
                yaml.set(entryPath + ".max-amount", entry.getMaxAmount());
                yaml.set(entryPath + ".delivery-mode", entry.getDeliveryMode().name());
                saveCommands(yaml, entryPath + ".commands", entry.getCommands());
            }
        }

        try {
            yaml.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save drops.yml: " + exception.getMessage());
        }

        rebuildEventIndex();
    }

    public List<DropDefinition> getAllDrops() {
        return new ArrayList<>(drops.values());
    }

    public DropDefinition getDrop(String id) {
        return drops.get(id);
    }

    public DropDefinition createDrop() {
        return createDrop("New Drop");
    }

    public DropDefinition createDrop(String name) {
        String id = generateId();
        String displayName = name == null || name.isBlank() ? "New Drop" : name.trim();
        DropDefinition definition = new DropDefinition(id, displayName, DropEventType.BLOCK_BREAK, false, 0, false, new DropConditions(), List.of());
        drops.put(id, definition);
        save();
        return definition;
    }

    public boolean deleteDrop(String id) {
        DropDefinition removed = drops.remove(id);
        if (removed == null) {
            return false;
        }
        save();
        return true;
    }

    public List<DropDefinition> getDropsForEvent(DropEventType eventType) {
        return dropsByEvent.getOrDefault(eventType, List.of());
    }

    private String generateId() {
        int next = drops.size() + 1;
        String id = "drop_" + next;
        while (drops.containsKey(id)) {
            next++;
            id = "drop_" + next;
        }
        return id;
    }

    private void rebuildEventIndex() {
        dropsByEvent.clear();
        for (DropEventType eventType : DropEventType.values()) {
            dropsByEvent.put(eventType, new ArrayList<>());
        }

        for (DropDefinition definition : drops.values()) {
            dropsByEvent.computeIfAbsent(definition.getEventType(), key -> new ArrayList<>()).add(definition);
        }

        for (Map.Entry<DropEventType, List<DropDefinition>> entry : dropsByEvent.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(DropDefinition::getPriority).reversed().thenComparing(DropDefinition::getId));
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
    }

    private DropConditions loadConditions(YamlConfiguration yaml, String basePath) {
        DropConditions conditions = new DropConditions();
        conditions.getWorlds().addAll(ConfigValueLists.lowerStrings(yaml, basePath + ".worlds"));
        conditions.getDisabledWorlds().addAll(ConfigValueLists.lowerStrings(yaml, basePath + ".disabled-worlds"));
        conditions.getBiomes().addAll(ConfigValueLists.upperStrings(yaml, basePath + ".biomes"));
        conditions.getTargets().addAll(ConfigValueLists.upperStrings(yaml, basePath + ".targets"));

        ConfigurationSection section = yaml.getConfigurationSection(basePath);
        if (section == null) {
            return conditions;
        }

        conditions.setPermission(section.getString("permission", ""));

        for (String tool : section.getStringList("tools")) {
            if (!tool.isBlank()) {
                conditions.getTools().add(tool);
            }
        }

        conditions.setSilkTouchMode(SilkTouchMode.fromConfig(section.getString("silk-touch")));
        conditions.setFortuneMin(section.getInt("fortune-min", 0));
        conditions.setCooldownMs(section.getLong("cooldown-ms", 0L));
        for (String requiredPlugin : section.getStringList("required-plugins")) {
            if (requiredPlugin != null && !requiredPlugin.isBlank()) {
                conditions.getRequiredPlugins().add(requiredPlugin.trim());
            }
        }

        ConfigurationSection persistentData = section.getConfigurationSection("entity-pdc");
        loadEntityPersistentData(persistentData, conditions.getEntityPersistentData());

        if (section.contains("time.min") && section.contains("time.max")) {
            conditions.setTimeRange(section.getInt("time.min"), section.getInt("time.max"));
        }

        for (String weather : section.getStringList("weather")) {
            DropWeather value = DropWeather.fromConfig(weather);
            if (value != null) {
                conditions.getWeather().add(value);
            }
        }

        return conditions;
    }

    static void loadEntityPersistentData(ConfigurationSection persistentData, Map<String, List<String>> target) {
        if (persistentData == null || target == null) {
            return;
        }
        // Bukkit treats dots as path separators. NamespacedKey permits dots in both
        // namespace and key, so walk leaf paths recursively to reconstruct the exact key.
        for (String key : persistentData.getKeys(true)) {
            Object raw = persistentData.get(key);
            if (raw instanceof ConfigurationSection) {
                continue;
            }
            List<String> values = new ArrayList<>();
            if (raw instanceof List<?> list) {
                for (Object value : list) {
                    if (value != null && !value.toString().isBlank()) {
                        values.add(value.toString().trim());
                    }
                }
            } else if (raw != null && !raw.toString().isBlank()) {
                values.add(raw.toString().trim());
            }
            if (!values.isEmpty()) {
                target.put(key.toLowerCase(java.util.Locale.ROOT), values);
            }
        }
    }

    private void saveConditions(YamlConfiguration yaml, String basePath, DropConditions conditions) {
        yaml.set(basePath + ".worlds", conditions.getWorlds());
        yaml.set(basePath + ".disabled-worlds", conditions.getDisabledWorlds());
        yaml.set(basePath + ".biomes", conditions.getBiomes());
        yaml.set(basePath + ".targets", conditions.getTargets());
        yaml.set(basePath + ".permission", conditions.getPermission());
        yaml.set(basePath + ".tools", conditions.getTools());
        yaml.set(basePath + ".silk-touch", conditions.getSilkTouchMode().name());
        yaml.set(basePath + ".fortune-min", conditions.getFortuneMin());
        yaml.set(basePath + ".cooldown-ms", conditions.getCooldownMs());
        yaml.set(basePath + ".required-plugins", conditions.getRequiredPlugins());
        yaml.set(basePath + ".entity-pdc", null);
        for (Map.Entry<String, List<String>> entry : conditions.getEntityPersistentData().entrySet()) {
            yaml.set(basePath + ".entity-pdc." + entry.getKey(), entry.getValue());
        }

        if (conditions.getTimeMin() != null && conditions.getTimeMax() != null) {
            yaml.set(basePath + ".time.min", conditions.getTimeMin());
            yaml.set(basePath + ".time.max", conditions.getTimeMax());
        } else {
            yaml.set(basePath + ".time", null);
        }

        List<String> weather = new ArrayList<>();
        for (DropWeather dropWeather : conditions.getWeather()) {
            weather.add(dropWeather.name());
        }
        yaml.set(basePath + ".weather", weather);
    }

    private void installReforgeDropDefaults() {
        boolean firstInstall = !plugin.getConfig().getBoolean(REFORGE_INSTALL_MARKER, false);
        Set<String> removedDefaults = new LinkedHashSet<>(plugin.getConfig().getStringList(REFORGE_REMOVED_DEFAULTS));
        if (!firstInstall && removedDefaults.isEmpty()) {
            return;
        }

        try (InputStream stream = plugin.getResource("reforge-drops.yml")) {
            if (stream == null) {
                plugin.getLogger().warning("Missing bundled reforge-drops.yml; reforge mob rewards were not installed.");
                return;
            }
            YamlConfiguration additions;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                additions = YamlConfiguration.loadConfiguration(reader);
            }
            YamlConfiguration live = YamlConfiguration.loadConfiguration(dataFile);
            ConfigurationSection defaults = additions.getConfigurationSection("drops");
            boolean changed = false;
            if (defaults != null) {
                for (String id : defaults.getKeys(false)) {
                    if (!firstInstall && !removedDefaults.contains(id)) {
                        continue;
                    }
                    String destination = "drops." + id;
                    if (live.contains(destination)) {
                        continue;
                    }
                    ConfigurationSection source = defaults.getConfigurationSection(id);
                    if (source == null) {
                        continue;
                    }
                    copyReforgeDefault(source, live, destination);
                    changed = true;
                }
            }
            if (changed) {
                File backup = new File(dataFile.getParentFile(), firstInstall
                    ? "drops.yml.pre-reforge-acquisition-v1.bak"
                    : "drops.yml.pre-reforge-return-v1.bak");
                if (!backup.exists()) {
                    Files.copy(dataFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
                live.save(dataFile);
            }
            plugin.getConfig().set(REFORGE_INSTALL_MARKER, true);
            plugin.getConfig().set(REFORGE_REMOVED_DEFAULTS, null);
            plugin.saveConfig();
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not install default reforge mob rewards: " + exception.getMessage());
        }
    }

    private void removeAbsentReforgeDefaults() {
        if (!plugin.getConfig().getBoolean(REFORGE_INSTALL_MARKER, false)) {
            return;
        }

        try (InputStream stream = plugin.getResource("reforge-drops.yml")) {
            if (stream == null) {
                plugin.getLogger().warning("Missing bundled reforge-drops.yml; existing profiles were preserved.");
                return;
            }
            YamlConfiguration bundled;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                bundled = YamlConfiguration.loadConfiguration(reader);
            }
            YamlConfiguration live = YamlConfiguration.loadConfiguration(dataFile);
            List<String> removed = removeUntouchedReforgeDefaults(live, bundled.getConfigurationSection("drops"));
            if (removed.isEmpty()) {
                return;
            }

            File backup = new File(dataFile.getParentFile(), "drops.yml.pre-reforge-absent-v1.bak");
            if (!backup.exists()) {
                Files.copy(dataFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
            live.save(dataFile);

            Set<String> restoreOnReturn = new LinkedHashSet<>(plugin.getConfig().getStringList(REFORGE_REMOVED_DEFAULTS));
            restoreOnReturn.addAll(removed);
            plugin.getConfig().set(REFORGE_REMOVED_DEFAULTS, new ArrayList<>(restoreOnReturn));
            plugin.saveConfig();
            plugin.getLogger().info("Removed " + removed.size() + " untouched FoReforge drop defaults while FoReforge is absent.");
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not remove unused FoReforge drop defaults: " + exception.getMessage());
        }
    }

    static List<String> removeUntouchedReforgeDefaults(YamlConfiguration live, ConfigurationSection defaults) {
        List<String> removed = new ArrayList<>();
        if (defaults == null) {
            return removed;
        }
        for (String id : defaults.getKeys(false)) {
            ConfigurationSection current = live.getConfigurationSection("drops." + id);
            ConfigurationSection bundled = defaults.getConfigurationSection(id);
            if (current == null || bundled == null || !isUntouchedReforgeDefault(current, bundled)) {
                continue;
            }
            live.set("drops." + id, null);
            removed.add(id);
        }
        return removed;
    }

    static void copyReforgeDefault(ConfigurationSection source, YamlConfiguration destination, String path) {
        copySection(source, destination, path);
        ConfigurationSection entries = source.getConfigurationSection("custom-drops");
        if (entries != null) {
            for (String id : entries.getKeys(false)) {
                destination.set(path + ".custom-drops." + id + ".tags", List.of("reforge-token"));
            }
        }
    }

    private static boolean isUntouchedReforgeDefault(ConfigurationSection current, ConfigurationSection bundled) {
        YamlConfiguration normalized = new YamlConfiguration();
        copySection(current, normalized, "profile");
        Object profileTags = normalized.get("profile.tags");
        if (profileTags instanceof List<?> tags && tags.isEmpty()) {
            normalized.set("profile.tags", null);
        }

        ConfigurationSection entries = bundled.getConfigurationSection("custom-drops");
        if (entries != null) {
            for (String id : entries.getKeys(false)) {
                String path = "profile.custom-drops." + id;
                if (List.of("reforge-token").equals(normalized.get(path + ".tags"))) {
                    normalized.set(path + ".tags", null);
                }
                Object item = normalized.get(path + ".item");
                if (item instanceof ItemStack stack && stack.getType() == Material.COMMAND_BLOCK
                        && stack.getAmount() == 1 && !stack.hasItemMeta()) {
                    normalized.set(path + ".item", null);
                }
                ConfigurationSection commands = normalized.getConfigurationSection(path + ".commands");
                if (commands != null && commands.getKeys(false).equals(Set.of("0"))) {
                    ConfigurationSection numbered = commands.getConfigurationSection("0");
                    if (numbered != null) {
                        copySection(numbered, normalized, path + ".commands.give");
                        normalized.set(path + ".commands.0", null);
                    }
                }
            }
        }

        ConfigurationSection comparable = normalized.getConfigurationSection("profile");
        return comparable != null && leafValues(comparable).equals(leafValues(bundled));
    }

    private static Map<String, Object> leafValues(ConfigurationSection section) {
        Map<String, Object> leaves = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : section.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) {
                leaves.put(entry.getKey(), entry.getValue());
            }
        }
        return leaves;
    }

    private void installReforgeGameplayTags() {
        String marker = "data-migrations.reforge-gameplay-tags-v1";
        if (plugin.getConfig().getBoolean(marker, false)) {
            return;
        }

        YamlConfiguration live = YamlConfiguration.loadConfiguration(dataFile);
        boolean changed = false;
        Set<String> eligible = Set.of(
            "drop_1", "drop_2", "drop_3", "drop_4", "drop_5", "drop_6",
            "drop_7", "drop_8", "drop_9", "drop_10", "drop_11", "drop_12"
        );
        Set<String> ores = Set.of("drop_4", "drop_6", "drop_7", "drop_9", "drop_12");

        for (String id : eligible) {
            changed |= addTag(live, "drops." + id + ".tags", "reforge-eligible");
        }
        for (String id : ores) {
            changed |= addTag(live, "drops." + id + ".tags", "reforge-ore");
        }
        changed |= addTag(live, "drops.dungeon_crystal_shard.tags", "dungeon-crystal");

        ConfigurationSection profiles = live.getConfigurationSection("drops");
        if (profiles != null) {
            for (String profileId : profiles.getKeys(false)) {
                if (!profileId.toLowerCase(Locale.ROOT).startsWith("foreforge_")) {
                    continue;
                }
                ConfigurationSection entries = profiles.getConfigurationSection(profileId + ".custom-drops");
                if (entries == null) {
                    continue;
                }
                for (String entryId : entries.getKeys(false)) {
                    changed |= addTag(live,
                        "drops." + profileId + ".custom-drops." + entryId + ".tags", "reforge-token");
                }
            }
        }

        try {
            if (changed) {
                File backup = new File(dataFile.getParentFile(), "drops.yml.pre-reforge-gameplay-tags-v1.bak");
                if (!backup.exists()) {
                    Files.copy(dataFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
                live.save(dataFile);
            }
            plugin.getConfig().set(marker, true);
            plugin.saveConfig();
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not install FoReforge gameplay tags: " + exception.getMessage());
        }
    }

    private static boolean addTag(YamlConfiguration yaml, String path, String tag) {
        String ownerPath = path.substring(0, path.length() - ".tags".length());
        if (!yaml.contains(ownerPath)) {
            return false;
        }
        List<String> tags = new ArrayList<>(yaml.getStringList(path));
        boolean present = tags.stream().anyMatch(value -> value.equalsIgnoreCase(tag));
        if (present) {
            return false;
        }
        tags.add(tag);
        yaml.set(path, tags);
        return true;
    }

    private static Set<String> normalizedTags(List<String> rawTags) {
        Set<String> tags = new LinkedHashSet<>();
        if (rawTags != null) {
            rawTags.stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .forEach(tags::add);
        }
        return tags;
    }

    static void copySection(ConfigurationSection source, YamlConfiguration destination, String destinationPath) {
        for (Map.Entry<String, Object> entry : source.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) {
                destination.set(destinationPath + "." + entry.getKey(), entry.getValue());
            }
        }
    }

    private List<DropCommandAction> loadCommands(ConfigurationSection entrySection, int maxCommands) {
        List<DropCommandAction> commands = new ArrayList<>();
        if (maxCommands <= 0) {
            return commands;
        }

        ConfigurationSection commandSection = entrySection.getConfigurationSection("commands");
        if (commandSection != null) {
            List<String> keys = new ArrayList<>(commandSection.getKeys(false));
            keys.sort(String::compareTo);
            for (String key : keys) {
                if (commands.size() >= maxCommands) {
                    break;
                }

                ConfigurationSection section = commandSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }

                String commandLine = section.getString("command", "").trim();
                if (commandLine.isEmpty()) {
                    continue;
                }

                DropCommandSenderType senderType = DropCommandSenderType.fromConfig(section.getString("sender"));
                commands.add(new DropCommandAction(senderType, commandLine));
            }
            return commands;
        }

        for (String commandLine : entrySection.getStringList("commands")) {
            if (commands.size() >= maxCommands) {
                break;
            }

            if (!commandLine.isBlank()) {
                commands.add(new DropCommandAction(DropCommandSenderType.CONSOLE, commandLine.trim()));
            }
        }

        return commands;
    }

    private void saveCommands(YamlConfiguration yaml, String basePath, List<DropCommandAction> commands) {
        yaml.set(basePath, null);
        for (int i = 0; i < commands.size(); i++) {
            DropCommandAction commandAction = commands.get(i);
            String path = basePath + "." + i;
            yaml.set(path + ".sender", commandAction.getSenderType().name());
            yaml.set(path + ".command", commandAction.getCommand());
        }
    }
}
