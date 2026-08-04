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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DropStore {
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
                        customDrops.add(new CustomDropEntry(item, chance, minAmount, maxAmount, deliveryMode, commands, maxDropAmount, itemReward, sendMessage, respectFortune));
                    }
                }

                drops.put(id, new DropDefinition(id, name, eventType, cancelVanillaDrops, priority, stopProcessing, conditions, customDrops));
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
            saveConditions(yaml, basePath + ".conditions", drop.getConditions());

            List<CustomDropEntry> entries = drop.getCustomDrops();
            for (int i = 0; i < entries.size(); i++) {
                CustomDropEntry entry = entries.get(i);
                String entryPath = basePath + ".custom-drops." + i;
                yaml.set(entryPath + ".item-reward", entry.isItemReward());
                yaml.set(entryPath + ".send-message", entry.isSendMessage());
                yaml.set(entryPath + ".respect-fortune", entry.isRespectFortune());
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
