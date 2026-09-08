package me.foesio.foDrops.editor;

import me.foesio.core.FoCoreContext;
import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.dialog.DialogServiceFactory;
import me.foesio.core.dialog.FallbackDialogService;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.ConfigEditorButton;
import me.foesio.core.editor.ConfigEditorMenu;
import me.foesio.core.editor.ConfigEditorValueType;
import me.foesio.core.editor.CursorItemEditor;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.editor.EditorMenuHolder;
import me.foesio.core.editor.EditorSaveResult;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.gui.GuiTitles;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.mob.MobChooserActionType;
import me.foesio.core.mob.MobChooserClick;
import me.foesio.core.mob.MobChooserHolder;
import me.foesio.core.mob.MobChooserMenus;
import me.foesio.core.mob.MobChooserMode;
import me.foesio.core.mob.MobChooserRequest;
import me.foesio.core.mob.MobSelections;
import me.foesio.core.selector.TriStateSelectionActionType;
import me.foesio.core.selector.TriStateSelectionClick;
import me.foesio.core.selector.TriStateSelectionHolder;
import me.foesio.core.selector.TriStateSelectionMenus;
import me.foesio.core.selector.TriStateSelectionRequest;
import me.foesio.core.selector.TriStateSelectionState;
import me.foesio.core.selector.TriStateSelections;
import me.foesio.core.selector.WorldSelectionEntries;
import me.foesio.core.message.FoStyle;
import me.foesio.core.text.FoText;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.foDrops.FoDrops;
import me.foesio.foDrops.drop.CustomDropEntry;
import me.foesio.foDrops.drop.DropCommandAction;
import me.foesio.foDrops.drop.DropCommandSenderType;
import me.foesio.foDrops.drop.DropConditions;
import me.foesio.foDrops.drop.DropDefinition;
import me.foesio.foDrops.drop.DropDeliveryMode;
import me.foesio.foDrops.drop.DropEventType;
import me.foesio.foDrops.drop.DropStore;
import me.foesio.foDrops.drop.DropWeather;
import me.foesio.foDrops.drop.SilkTouchMode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

public class EditorManager implements Listener {
    private static final int[] GRID_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final String EDITOR_ROOT_ID = "fodrops-editor-root";
    private static final int MANAGE_DROPS_SLOT = 10;
    private static final int MAX_PROFILES_SLOT = 11;
    private static final int MAX_CUSTOM_DROPS_SLOT = 12;
    private static final int MAX_COMMANDS_SLOT = 13;
    private static final int MAX_DROP_AMOUNT_SLOT = 14;
    private static final int BLOCK_OPERATOR_SLOT = 15;
    private static final int BLOCK_GAMEMODE_SLOT = 16;
    private static final int MAX_PROFILES_LIMIT = 500;
    private static final int MAX_CUSTOM_DROPS_LIMIT = 256;
    private static final int MAX_COMMANDS_LIMIT = 64;
    private static final int MAX_DROP_AMOUNT_LIMIT = 4096;
    private static final String TITLE_EDITOR = "FoDrops Editor";
    private static final String TITLE_DROPS = "Drop Profiles";
    private static final String TITLE_DROP = "Edit • {name}";
    private static final String TITLE_CUSTOM = "Custom Drops";
    private static final String TITLE_CUSTOM_ENTRY = "Edit Custom Drop";
    private static final String TITLE_CONFIRM_DELETE = "Confirm Delete";
    private static final String TITLE_CONDITIONS = "Conditions";
    private static final String TITLE_COMMANDS = "Commands";
    private static final String TITLE_MOB_TARGETS = "Mob Targets";
    private static final String TITLE_ADD_CUSTOM_DROP = "Add Custom Drop";

    private final FoDrops plugin;
    private final DropStore dropStore;
    private final FoCoreContext core;
    private final FoEditorSounds editorSounds;
    private final GuiButtonConfig buttons = GuiButtonConfig.defaults();
    private final Map<UUID, ChatPrompt> chatPrompts = new ConcurrentHashMap<>();
    private final Map<UUID, String> mobTargetDropIds = new ConcurrentHashMap<>();
    private final Map<UUID, String> worldSelectionDropIds = new ConcurrentHashMap<>();
    private final Set<UUID> warnedFallbackPlayers = ConcurrentHashMap.newKeySet();

    public EditorManager(FoDrops plugin, DropStore dropStore, FoCoreContext core, FoEditorSounds editorSounds) {
        this.plugin = plugin;
        this.dropStore = dropStore;
        this.core = core;
        this.editorSounds = editorSounds;
    }

    public void reloadDialogInputs() {
        warnedFallbackPlayers.clear();
    }

    public void close() {
        chatPrompts.clear();
        mobTargetDropIds.clear();
        worldSelectionDropIds.clear();
        warnedFallbackPlayers.clear();
    }

    public void openEditorMenu(Player player) {
        player.openInventory(settingsEditor().open(player));
    }

    public void openEditorFromCommand(Player player) {
        editorSounds.open(player);
        openEditorMenu(player);
    }

    private ConfigEditorMenu settingsEditor() {
        return ConfigEditorMenu.builder(plugin, plugin.messages(), settingsEditorDefaults())
            .id(EDITOR_ROOT_ID)
            .title("title", TITLE_EDITOR)
            .size("size", 27)
            .filler("filler", true, Material.GRAY_STAINED_GLASS_PANE)
            .booleanLabels("{good}Enabled", "{bad}Disabled")
            .reloadSettings(this::reloadPluginSettings)
            .button(ConfigEditorButton.action("manage-drops", "buttons.manage-drops", MANAGE_DROPS_SLOT)
                .fallbackMaterial(Material.CHEST)
                .build())
            .button(maxProfilesButton())
            .button(maxCustomDropsButton())
            .button(maxCommandsButton())
            .button(maxDropAmountButton())
            .button(booleanSetting(
                "block-operator",
                "safety.block-operator-commands",
                "Block Operator Commands",
                "buttons.block-operator",
                BLOCK_OPERATOR_SLOT,
                plugin::shouldBlockOperatorRewardCommands
            ))
            .button(booleanSetting(
                "block-gamemode",
                "safety.block-gamemode-commands",
                "Block Gamemode Commands",
                "buttons.block-gamemode",
                BLOCK_GAMEMODE_SLOT,
                plugin::shouldBlockGamemodeRewardCommands
            ))
            .build();
    }

    private ConfigEditorButton booleanSetting(String id, String configPath, String label, String guiPath, int slot, java.util.function.BooleanSupplier currentValue) {
        return ConfigEditorButton.booleanSetting(id, configPath, label, guiPath, slot, currentValue)
            .toggleMaterials(Material.LIME_DYE, Material.RED_DYE)
            .build();
    }

    private ConfigEditorButton integerSetting(String id, String configPath, String label, String guiPath, int slot, IntSupplier currentValue, Material material) {
        return ConfigEditorButton.integerSetting(id, configPath, label, guiPath, slot, currentValue)
            .fallbackMaterial(material)
            .build();
    }

    private ConfigEditorButton maxProfilesButton() {
        return integerSetting("max-profiles", "limits.max-profiles", "Max Profiles", "buttons.max-profiles", MAX_PROFILES_SLOT, plugin::getMaxProfiles, Material.WRITABLE_BOOK);
    }

    private ConfigEditorButton maxCustomDropsButton() {
        return integerSetting("max-custom-drops", "limits.max-custom-drops-per-profile", "Max Custom Drops Per Profile", "buttons.max-custom-drops", MAX_CUSTOM_DROPS_SLOT, plugin::getMaxCustomDropsPerProfile, Material.CHEST);
    }

    private ConfigEditorButton maxCommandsButton() {
        return integerSetting("max-commands", "limits.max-commands-per-entry", "Max Commands", "buttons.max-commands", MAX_COMMANDS_SLOT, plugin::getMaxCommandsPerEntry, Material.COMMAND_BLOCK);
    }

    private ConfigEditorButton maxDropAmountButton() {
        return integerSetting("max-drop-amount", "limits.max-drop-amount", "Max Drop Amount", "buttons.max-drop-amount", MAX_DROP_AMOUNT_SLOT, plugin::getMaxDropAmount, Material.HOPPER);
    }

    private ConfigEditorButton configButton(PromptType type) {
        return switch (type) {
            case CONFIG_MAX_PROFILES -> maxProfilesButton();
            case CONFIG_MAX_CUSTOM_DROPS -> maxCustomDropsButton();
            case CONFIG_MAX_COMMANDS -> maxCommandsButton();
            case CONFIG_MAX_DROP_AMOUNT -> maxDropAmountButton();
            default -> null;
        };
    }

    private int configMin(PromptType type) {
        return switch (type) {
            case CONFIG_MAX_COMMANDS -> 0;
            case CONFIG_MAX_PROFILES, CONFIG_MAX_CUSTOM_DROPS, CONFIG_MAX_DROP_AMOUNT -> 1;
            default -> 0;
        };
    }

    private int configMax(PromptType type) {
        return switch (type) {
            case CONFIG_MAX_PROFILES -> MAX_PROFILES_LIMIT;
            case CONFIG_MAX_CUSTOM_DROPS -> MAX_CUSTOM_DROPS_LIMIT;
            case CONFIG_MAX_COMMANDS -> MAX_COMMANDS_LIMIT;
            case CONFIG_MAX_DROP_AMOUNT -> MAX_DROP_AMOUNT_LIMIT;
            default -> 0;
        };
    }

    private YamlConfiguration settingsEditorDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("title", TITLE_EDITOR);
        defaults.set("size", 27);
        defaults.set("filler.enabled", true);
        defaults.set("filler.material", Material.GRAY_STAINED_GLASS_PANE.name());

        actionButton(defaults, "buttons.manage-drops", MANAGE_DROPS_SLOT, Material.CHEST,
            "{theme}Manage Drop Profiles",
            List.of(
                "{white}Open the profile browser",
                "{white}and reward editor.",
                "{white}Profiles: {theme}" + dropStore.getAllDrops().size() + "/" + plugin.getMaxProfiles()
            )
        );
        toggleButton(defaults, "buttons.block-operator", BLOCK_OPERATOR_SLOT, "Block Operator Commands",
            "Stop rewards from running op and deop commands.");
        toggleButton(defaults, "buttons.block-gamemode", BLOCK_GAMEMODE_SLOT, "Block Gamemode Commands",
            "Stop rewards from running gamemode commands.");
        integerButton(defaults, "buttons.max-profiles", MAX_PROFILES_SLOT, Material.WRITABLE_BOOK, "Max Profiles", 1, MAX_PROFILES_LIMIT);
        integerButton(defaults, "buttons.max-custom-drops", MAX_CUSTOM_DROPS_SLOT, Material.CHEST, "Max Custom Drops Per Profile", 1, MAX_CUSTOM_DROPS_LIMIT);
        integerButton(defaults, "buttons.max-commands", MAX_COMMANDS_SLOT, Material.COMMAND_BLOCK, "Max Commands", 0, MAX_COMMANDS_LIMIT);
        integerButton(defaults, "buttons.max-drop-amount", MAX_DROP_AMOUNT_SLOT, Material.HOPPER, "Max Drop Amount", 1, MAX_DROP_AMOUNT_LIMIT);
        return defaults;
    }

    private void actionButton(YamlConfiguration defaults, String path, int slot, Material material, String name, List<String> lore) {
        defaults.set(path + ".slot", slot);
        defaults.set(path + ".material", material.name());
        defaults.set(path + ".name", name);
        defaults.set(path + ".lore", lore);
    }

    private void toggleButton(YamlConfiguration defaults, String path, int slot, String label, String detail) {
        defaults.set(path + ".slot", slot);
        defaults.set(path + ".enabled-material", Material.LIME_DYE.name());
        defaults.set(path + ".disabled-material", Material.RED_DYE.name());
        defaults.set(path + ".name", "{theme}" + label);
        defaults.set(path + ".lore", List.of(
            "{white}Current: {value}",
            "",
            "{white}" + detail,
            "{white}Click to toggle."
        ));
    }

    private void integerButton(YamlConfiguration defaults, String path, int slot, Material material, String label, int min, int max) {
        defaults.set(path + ".slot", slot);
        defaults.set(path + ".material", material.name());
        defaults.set(path + ".name", "{theme}" + label);
        defaults.set(path + ".lore", List.of(
            "{white}Current: {theme}{value}",
            "{white}Range: {theme}" + min + "-" + max,
            "",
            "{white}Click to enter a new value."
        ));
    }

    private void reloadPluginSettings() {
        var result = plugin.reloadPluginData(dropStore);
        if (!result.successful()) {
            throw new IllegalStateException(result.failedStep() + ": " + result.errorMessage());
        }
    }

    public void openMainMenu(Player player, int page) {
        List<DropDefinition> drops = dropStore.getAllDrops();
        openMainMenu(player, "", page, drops);
    }

    private void openMainMenu(Player player, String filter, int page, List<DropDefinition> drops) {
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<EntryBrowserRequest.Entry> entries = drops.stream()
            .filter(definition -> matchesFilter(definition.getId() + " " + definition.getName() + " " + definition.getEventType().getDisplayName(), normalizedFilter))
            .map(definition -> EntryBrowserRequest.Entry.of(definition.getId(), button(player,
                Material.CHEST,
                "{theme}" + definition.getName(),
                "{white}ID: {theme}" + definition.getId(),
                "{white}Event: {theme}" + definition.getEventType().getDisplayName(),
                "{white}Priority: {theme}" + definition.getPriority(),
                "{white}Stop Processing: " + (definition.isStopProcessing() ? "{good}Yes" : "{bad}No"),
                "{white}Cancel Vanilla: " + (definition.isCancelVanillaDrops() ? "{good}Yes" : "{bad}No"),
                "{white}Custom Drops: {theme}" + definition.getCustomDrops().size(),
                "",
                "{white}Click to edit this profile."
            )))
            .toList();

        EntryBrowserMenus.open(player, EntryBrowserRequest.builder()
            .title(TITLE_DROPS)
            .entries(entries)
            .page(page)
            .filter(normalizedFilter)
            .buttons(buttons)
            .showBack(true)
            .context(new MainBrowserContext())
            .addButton(button(player, Material.ANVIL, "{theme}Add Drop", "{white}Create a new drop profile", "{white}and open its editor.", "{white}Limit: {theme}" + drops.size() + "/" + plugin.getMaxProfiles()))
            .build());
    }

    private boolean matchesFilter(String value, String filter) {
        return filter == null || filter.isBlank() || value.toLowerCase(Locale.ROOT).contains(filter);
    }

    public void openDropMenu(Player player, String dropId) {
        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        EditorMenu menu = new EditorMenu(MenuType.DROP, dropId, 0, -1, 0);
        Inventory inventory = Bukkit.createInventory(menu, 36, getGuiTitle(TITLE_DROP, "{name}", definition.getName()));
        menu.setInventory(inventory);

        inventory.setItem(10, button(player,
            Material.NAME_TAG,
            "{theme}Rename Drop",
            "{white}Current: {theme}" + definition.getName(),
            "",
            "{white}Click and type in chat."
        ));
        inventory.setItem(11, button(player, Material.COMPASS, "{theme}Event Type", buildEventTypeLore(definition)));
        inventory.setItem(12, button(player,
            Material.BOOK,
            "{theme}Event Targets",
            "{white}Current: " + formatList(definition.getConditions().getTargets(), 4),
            "",
            "{white}Set what this event acts on",
            "{white}(block/entity/item type keys).",
            "{white}Click to set comma-separated",
            "{white}targets or {theme}none {white}to clear.",
            "{white}Example: {theme}stone"
        ));
        inventory.setItem(13, button(player,
            Material.HOPPER,
            "{theme}Conditions",
            "{white}Configured: " + (definition.getConditions().hasAnyRestrictions() ? "{good}Yes" : "{bad}No"),
            "",
            "{white}Click to edit condition rules."
        ));
        inventory.setItem(14, button(player,
            Material.CLOCK,
            "{theme}Priority",
            "{white}Current: {theme}" + definition.getPriority(),
            "",
            "{white}Click to set priority in chat."
        ));
        inventory.setItem(15, button(player,
            definition.isCancelVanillaDrops() ? Material.LIME_DYE : Material.RED_DYE,
            definition.isCancelVanillaDrops() ? "{good}Cancel Vanilla Drops: ON" : "{bad}Cancel Vanilla Drops: OFF",
            "{white}If ON, matching profile removes",
            "{white}vanilla drops when possible."
        ));
        inventory.setItem(16, button(player,
            definition.isStopProcessing() ? Material.LIME_DYE : Material.RED_DYE,
            definition.isStopProcessing() ? "{good}Stop Processing: ON" : "{bad}Stop Processing: OFF",
            "{white}If ON, lower-priority profiles",
            "{white}won't run after this one matches."
        ));
        inventory.setItem(19, button(player,
            Material.CHEST,
            "{theme}Custom Drops",
            "{white}Entries: {theme}" + definition.getCustomDrops().size(),
            "",
            "{white}Click to edit entry list."
        ));
        inventory.setItem(20, button(player, Material.LAVA_BUCKET, "{bad}Delete Drop", "{white}Remove this drop profile."));
        inventory.setItem(GuiSlots.bottomMiddleSlot(4), buttons.back(player));

        fillBackground(inventory);
        player.openInventory(inventory);
    }

    public void openDeleteConfirmMenu(Player player, String dropId) {
        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        EditorMenu menu = new EditorMenu(MenuType.DELETE_CONFIRM, dropId, 0, -1, 0);
        Inventory inventory = Bukkit.createInventory(menu, 27, getGuiTitle(TITLE_CONFIRM_DELETE));
        menu.setInventory(inventory);

        inventory.setItem(11, button(player,
            Material.LAVA_BUCKET,
            "{bad}Delete Forever",
            "{white}Profile: {theme}" + definition.getName(),
            "{white}ID: {theme}" + definition.getId(),
            "",
            "{bad}This cannot be undone."
        ));
        inventory.setItem(GuiSlots.bottomMiddleSlot(3), buttons.back(player));

        fillBackground(inventory);
        player.openInventory(inventory);
    }

    public void openAddCustomDropMenu(Player player, String dropId, int returnPage) {
        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        EditorMenu menu = new EditorMenu(MenuType.ADD_CUSTOM_DROP, dropId, returnPage, -1, returnPage);
        Inventory inventory = Bukkit.createInventory(menu, 27, getGuiTitle(TITLE_ADD_CUSTOM_DROP));
        menu.setInventory(inventory);

        inventory.setItem(11, button(player,
            Material.CHEST,
            "{theme}Item Reward",
            "{white}Adds the item held",
            "{white}on your cursor."
        ));
        inventory.setItem(15, button(player,
            Material.COMMAND_BLOCK,
            "{theme}Command Reward",
            "{white}Runs a command without",
            "{white}dropping an item."
        ));
        inventory.setItem(GuiSlots.bottomMiddleSlot(3), buttons.back(player));

        fillBackground(inventory);
        player.openInventory(inventory);
    }

    public void openMobTargetSelector(Player player, String dropId, int page) {
        openMobTargetSelector(player, dropId, page, "");
    }

    public void openMobTargetSelector(Player player, String dropId, int page, String filter) {
        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        mobTargetDropIds.put(player.getUniqueId(), definition.getId());
        MobChooserRequest request = MobChooserRequest.builder()
            .title(getGuiTitle(TITLE_MOB_TARGETS))
            .mode(MobChooserMode.MULTI_TOGGLE)
            .page(page)
            .filter(filter)
            .selectedTypes(MobSelections.fromKeys(definition.getConditions().getTargets()))
            .availableTypes(MobChooserMenus.allLivingMobs())
            .showBack(true)
            .showSearch(true)
            .buttons(buttons)
            .build();
        MobChooserMenus.open(player, request);
    }

    public void openWorldSelector(Player player, String dropId, int page) {
        openWorldSelector(player, dropId, page, "");
    }

    public void openWorldSelector(Player player, String dropId, int page, String filter) {
        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            worldSelectionDropIds.remove(player.getUniqueId());
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        DropConditions conditions = definition.getConditions();
        worldSelectionDropIds.put(player.getUniqueId(), definition.getId());
        TriStateSelectionRequest request = TriStateSelectionRequest.builder()
            .worldSelection()
            .entries(WorldSelectionEntries.loadedAndConfigured(plugin, configuredWorldNames(conditions)))
            .states(TriStateSelections.fromEnabledDisabled(conditions.getWorlds(), conditions.getDisabledWorlds()))
            .cycleOrder(List.of(
                TriStateSelectionState.NEUTRAL,
                TriStateSelectionState.ENABLED,
                TriStateSelectionState.DISABLED
            ))
            .enabledLabel("Enabled")
            .disabledLabel("Disabled")
            .neutralLabel("Default")
            .clickHint("Click to cycle world state.")
            .page(page)
            .filter(filter)
            .showBack(true)
            .showSearch(true)
            .buttons(buttons)
            .build();
        TriStateSelectionMenus.open(player, request);
    }

    public void openConditionsMenu(Player player, String dropId) {
        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        DropConditions conditions = definition.getConditions();
        EditorMenu menu = new EditorMenu(MenuType.CONDITIONS, dropId, 0, -1, 0);
        Inventory inventory = Bukkit.createInventory(menu, 36, getGuiTitle(TITLE_CONDITIONS));
        menu.setInventory(inventory);

        inventory.setItem(10, EditorItemFactory.worlds(player,
            conditions.getWorlds().size(),
            conditions.getDisabledWorlds().size(),
            "No override"
        ));
        inventory.setItem(11, button(player,
            Material.OAK_SAPLING,
            "{theme}Biomes",
            "{white}" + formatList(conditions.getBiomes(), 4),
            "",
            "{white}Click to set biome names",
            "{white}or {theme}none {white}to clear."
        ));
        inventory.setItem(12, button(player,
            Material.WATER_BUCKET,
            "{theme}Weather",
            "{white}" + formatWeather(conditions.getWeather()),
            "",
            "{white}Values: clear, rain, thunder",
            "{white}or {theme}none {white}to clear."
        ));
        inventory.setItem(13, button(player,
            Material.IRON_PICKAXE,
            "{theme}Tool Type",
            "{white}" + formatList(conditions.getTools(), 4),
            "",
            "{white}Use material names or tags",
            "{white}like {theme}DIAMOND_PICKAXE{white}, {theme}tag:pickaxe"
        ));
        inventory.setItem(14, button(player,
            Material.ENCHANTED_BOOK,
            "{theme}Silk Touch",
            buildSilkTouchLore(conditions.getSilkTouchMode())
        ));
        inventory.setItem(15, button(player,
            Material.EXPERIENCE_BOTTLE,
            "{theme}Fortune Min",
            "{white}Current: {theme}" + conditions.getFortuneMin(),
            "",
            "{white}Click to set minimum level."
        ));
        inventory.setItem(16, button(player,
            Material.PAPER,
            "{theme}Permission",
            "{white}" + (conditions.getPermission().isBlank() ? "{muted}None" : "{theme}" + conditions.getPermission()),
            "",
            "{white}Click to set required permission",
            "{white}or {theme}none {white}to clear."
        ));
        inventory.setItem(19, button(player,
            Material.CLOCK,
            "{theme}Time Range",
            "{white}Current: {theme}" + formatTimeRange(conditions),
            "",
            "{white}Format: {theme}min-max {white}(0-23999)",
            "{white}or {theme}none {white}to clear."
        ));
        inventory.setItem(20, button(player,
            Material.CLOCK,
            "{theme}Cooldown",
            "{white}Current: {theme}" + conditions.getCooldownMs() + "ms",
            "",
            "{white}Per-player per-profile cooldown.",
            "{white}{theme}0 {white}= disabled.",
            "{white}Click to set value in chat."
        ));
        inventory.setItem(GuiSlots.bottomMiddleSlot(4), buttons.back(player));

        fillBackground(inventory);
        player.openInventory(inventory);
    }

    public void openCustomDropsMenu(Player player, String dropId, int page) {
        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }
        openCustomDropsMenu(player, dropId, "", page);
    }

    private void openCustomDropsMenu(Player player, String dropId, String filter, int page) {
        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<EntryBrowserRequest.Entry> entries = new ArrayList<>();
        List<CustomDropEntry> customDrops = definition.getCustomDrops();
        for (int index = 0; index < customDrops.size(); index++) {
            CustomDropEntry entry = customDrops.get(index);
            if (!matchesFilter(customDropSearchText(entry, index), normalizedFilter)) {
                continue;
            }
            entries.add(EntryBrowserRequest.Entry.of(String.valueOf(index), customDropIcon(player, definition, entry, index)));
        }
        EntryBrowserMenus.open(player, EntryBrowserRequest.builder()
            .title(TITLE_CUSTOM)
            .entries(entries)
            .page(page)
            .filter(normalizedFilter)
            .buttons(buttons)
            .showBack(true)
            .context(new CustomBrowserContext(dropId))
            .addButton(button(player, Material.ANVIL, "{theme}Add Custom Drop", "{white}Choose item reward or", "{white}command reward.", "{white}Limit: {theme}" + customDrops.size() + "/" + plugin.getMaxCustomDropsPerProfile()))
            .build());
    }

    private String customDropSearchText(CustomDropEntry entry, int index) {
        return (entry.isItemReward() ? "item " + entry.getItem().getType().name() : "command") + " " + (index + 1);
    }

    private ItemStack customDropIcon(Player player, DropDefinition definition, CustomDropEntry entry, int index) {
        if (entry.isItemReward()) {
            return DialogIcons.forViewer(player, entry.getItem().clone());
        }
        double expectedValue = (entry.getChance() / 100.0D) * ((entry.getMinAmount() + entry.getMaxAmount()) / 2.0D);
        String gateLine = definition.getConditions().hasAnyRestrictions() ? "{muted}Profile gate: context-dependent" : "{good}Profile gate: always active";
        List<String> lore = new ArrayList<>();
        lore.add("{white}Type: " + (entry.isItemReward() ? "{theme}Item" : "{theme}Command"));
        lore.add("{white}Chance: {theme}" + trimChance(entry.getChance()) + "%");
        if (entry.isItemReward()) {
            lore.add("{white}Amount: {theme}" + entry.getMinAmount() + " - " + entry.getMaxAmount());
            lore.add("{white}Fortune: " + (entry.isRespectFortune() ? "{good}Enabled" : "{bad}Disabled"));
            lore.add("{white}Delivery: " + formatDeliveryMode(entry.getDeliveryMode()));
            lore.add("{white}Expected/Trigger: {theme}" + formatDecimal(expectedValue));
        }
        lore.add("{white}Message: " + (entry.isSendMessage() ? "{good}Enabled" : "{bad}Disabled"));
        addCommandRewardLore(lore, entry.getCommands());
        lore.add(gateLine);
        return button(player, Material.COMMAND_BLOCK,
                entry.isItemReward() ? "{theme}Drop #" + (index + 1) : "{theme}Command Reward #" + (index + 1),
                lore.toArray(String[]::new));
    }

    public void openCustomDropEntryMenu(Player player, String dropId, int entryIndex, int returnPage) {
        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }
        if (entryIndex < 0 || entryIndex >= definition.getCustomDrops().size()) {
            plugin.messages().sendConfigured(player, "custom-drop-missing");
            openCustomDropsMenu(player, dropId, returnPage);
            return;
        }

        CustomDropEntry entry = definition.getCustomDrops().get(entryIndex);
        EditorMenu menu = new EditorMenu(MenuType.CUSTOM_ENTRY, dropId, 0, entryIndex, returnPage);
        Inventory inventory = Bukkit.createInventory(menu, 36, getGuiTitle(TITLE_CUSTOM_ENTRY));
        menu.setInventory(inventory);

        inventory.setItem(10, button(player,
            Material.COMMAND_BLOCK,
            "{theme}Commands",
            "{white}Actions: {theme}" + entry.getCommands().size(),
            "",
            "{white}Click to edit commands."
        ));
        inventory.setItem(11, button(player,
            Material.GOLD_NUGGET,
            "{theme}Chance",
            "{white}Current: {theme}" + trimChance(entry.getChance()) + "%",
            "",
            "{white}Click to set chance in chat."
        ));
        inventory.setItem(12, button(player,
            entry.isItemReward() ? Material.IRON_INGOT : Material.GRAY_DYE,
            "{theme}Amount Range",
            entry.isItemReward()
                ? "{white}Current: {theme}" + entry.getMinAmount() + " - " + entry.getMaxAmount()
                : "{muted}Command-only entries do not drop items.",
            "",
            entry.isItemReward()
                ? "{white}Click to set amount range."
                : "{muted}Enable item reward first."
        ));
        inventory.setItem(13, button(player,
            entry.isItemReward() ? Material.HOPPER : Material.GRAY_DYE,
            "{theme}Delivery Mode",
            entry.isItemReward()
                ? "{white}Current: " + formatDeliveryMode(entry.getDeliveryMode())
                : "{muted}Command-only entries do not drop items.",
            "",
            entry.isItemReward()
                ? "{white}Click to cycle mode."
                : "{muted}Enable item reward first."
        ));
        inventory.setItem(14, button(player,
            entry.isSendMessage() ? Material.LIME_DYE : Material.RED_DYE,
            entry.isSendMessage() ? "{good}Send Message: ON" : "{bad}Send Message: OFF",
            "{white}Sends the reward message",
            "{white}when this entry triggers.",
            "",
            "{white}Click to toggle."
        ));
        inventory.setItem(16, button(player,
            entry.isItemReward() ? Material.LIME_DYE : Material.RED_DYE,
            entry.isItemReward() ? "{good}Item Reward: ON" : "{bad}Item Reward: OFF",
            "{white}When OFF, this entry only",
            "{white}runs configured commands.",
            "",
            "{white}Click to toggle."
        ));
        inventory.setItem(15, button(player,
            !entry.isItemReward() ? Material.GRAY_DYE : entry.isRespectFortune() ? Material.LAPIS_LAZULI : Material.REDSTONE,
            !entry.isItemReward()
                ? "{muted}Respect Fortune"
                : entry.isRespectFortune() ? "{good}Respect Fortune: ON" : "{bad}Respect Fortune: OFF",
            !entry.isItemReward()
                ? "{white}State: {muted}Unavailable"
                : "{white}State: " + (entry.isRespectFortune() ? "{good}Enabled" : "{bad}Disabled"),
            !entry.isItemReward()
                ? "{muted}Item rewards only."
                : "{white}Uses the tool Fortune level",
            !entry.isItemReward()
                ? "{muted}Enable item reward first."
                : "{white}to multiply item amount.",
            "",
            !entry.isItemReward()
                ? "{muted}Unavailable for command-only entries."
                : "{white}Click to toggle."
        ));
        inventory.setItem(22, button(player, Material.LAVA_BUCKET, "{bad}Delete Entry", "{white}Remove this custom drop entry."));
        inventory.setItem(GuiSlots.bottomMiddleSlot(4), buttons.back(player));

        fillBackground(inventory);
        player.openInventory(inventory);
    }

    public void openCustomDropDeleteConfirmMenu(Player player, String dropId, int entryIndex, int returnPage) {
        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }
        if (entryIndex < 0 || entryIndex >= definition.getCustomDrops().size()) {
            plugin.messages().sendConfigured(player, "custom-drop-missing");
            openCustomDropsMenu(player, dropId, returnPage);
            return;
        }

        EditorMenu menu = new EditorMenu(MenuType.CUSTOM_DELETE_CONFIRM, dropId, 0, entryIndex, returnPage);
        Inventory inventory = Bukkit.createInventory(menu, 27, getGuiTitle(TITLE_CONFIRM_DELETE));
        menu.setInventory(inventory);

        inventory.setItem(11, button(player,
            Material.LAVA_BUCKET,
            "{bad}Delete Forever",
            "{white}Entry: {theme}#" + (entryIndex + 1),
            "",
            "{bad}This cannot be undone."
        ));
        inventory.setItem(GuiSlots.bottomMiddleSlot(3), buttons.back(player));

        fillBackground(inventory);
        player.openInventory(inventory);
    }

    public void openCommandMenu(Player player, String dropId, int entryIndex, int page, int returnPage) {
        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }
        if (entryIndex < 0 || entryIndex >= definition.getCustomDrops().size()) {
            plugin.messages().sendConfigured(player, "custom-drop-missing");
            openCustomDropsMenu(player, dropId, returnPage);
            return;
        }

        openCommandMenu(player, dropId, entryIndex, "", page, returnPage);
    }

    private void openCommandMenu(Player player, String dropId, int entryIndex, String filter, int page, int returnPage) {
        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null || entryIndex < 0 || entryIndex >= definition.getCustomDrops().size()) {
            plugin.messages().sendConfigured(player, "custom-drop-missing");
            openCustomDropsMenu(player, dropId, returnPage);
            return;
        }
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<DropCommandAction> commands = definition.getCustomDrops().get(entryIndex).getCommands();
        List<EntryBrowserRequest.Entry> entries = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            DropCommandAction action = commands.get(index);
            if (!matchesFilter(action.getCommand() + " " + action.getSenderType().getDisplayName(), normalizedFilter)) {
                continue;
            }
            entries.add(EntryBrowserRequest.Entry.of(String.valueOf(index), button(player,
                action.getSenderType() == DropCommandSenderType.CONSOLE ? Material.COMMAND_BLOCK : Material.PAPER,
                "{theme}Command #" + (index + 1),
                "{white}Sender: {theme}" + action.getSenderType().getDisplayName(),
                "{white}Command: {theme}" + action.getCommand(),
                "",
                "{white}Left-click: edit command text",
                "{white}Right-click: toggle sender",
                "{white}Shift-right: remove"
            )));
        }
        EntryBrowserMenus.open(player, EntryBrowserRequest.builder()
            .title(TITLE_COMMANDS)
            .entries(entries)
            .page(page)
            .filter(normalizedFilter)
            .buttons(buttons)
            .showBack(true)
            .context(new CommandBrowserContext(dropId, entryIndex, returnPage))
            .addButton(button(player, Material.ANVIL, "{theme}Add Command", "{white}Format in chat:", "{theme}<console|player> <command>", "{white}Limit: {theme}" + commands.size() + "/" + plugin.getMaxCommandsPerEntry()))
            .build());
    }

    @EventHandler
    public void onEditorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MobChooserHolder mobChooserHolder) {
            handleMobChooserInventoryClick(event, player, mobChooserHolder);
            return;
        }
        if (holder instanceof TriStateSelectionHolder selectionHolder) {
            handleWorldSelectorInventoryClick(event, player, selectionHolder);
            return;
        }
        if (holder instanceof EntryBrowserHolder entryBrowserHolder) {
            event.setCancelled(true);
            if (!player.hasPermission("fodrops.admin")) {
                player.closeInventory();
                plugin.messages().sendConfigured(player, "no-permission");
                return;
            }
            handleEntryBrowserClick(event, player, entryBrowserHolder);
            return;
        }
        if (holder instanceof EditorMenuHolder editorMenuHolder) {
            handleSettingsEditorClick(event, player, editorMenuHolder);
            return;
        }
        if (!(holder instanceof EditorMenu menu)) {
            return;
        }

        if (event.getClickedInventory() == null) {
            return;
        }

        boolean clickedTop = event.getClickedInventory().equals(event.getView().getTopInventory());
        if (!clickedTop) {
            if (menu.type == MenuType.CUSTOM || menu.type == MenuType.CUSTOM_ENTRY || menu.type == MenuType.ADD_CUSTOM_DROP) {
                ClickType clickType = event.getClick();
                if (event.isShiftClick()
                    || clickType == ClickType.NUMBER_KEY
                    || clickType == ClickType.SWAP_OFFHAND
                    || clickType == ClickType.DOUBLE_CLICK) {
                    event.setCancelled(true);
                }
                return;
            }

            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        if (!player.hasPermission("fodrops.admin")) {
            player.closeInventory();
            plugin.messages().sendConfigured(player, "no-permission");
            return;
        }

        int slot = event.getSlot();
        if (menu.type == MenuType.MAIN) {
            handleMainClick(player, menu, slot);
            return;
        }
        if (menu.type == MenuType.DROP) {
            handleDropClick(player, menu, slot);
            return;
        }
        if (menu.type == MenuType.DELETE_CONFIRM) {
            handleDeleteConfirmClick(player, menu, slot);
            return;
        }
        if (menu.type == MenuType.CONDITIONS) {
            handleConditionsClick(player, menu, slot);
            return;
        }
        if (menu.type == MenuType.CUSTOM) {
            handleCustomClick(player, menu, slot, event.getClick(), event.getCursor());
            return;
        }
        if (menu.type == MenuType.CUSTOM_ENTRY) {
            handleCustomEntryClick(player, menu, slot, event.getClick(), event.getCursor());
            return;
        }
        if (menu.type == MenuType.CUSTOM_DELETE_CONFIRM) {
            handleCustomDeleteConfirmClick(player, menu, slot);
            return;
        }
        if (menu.type == MenuType.ADD_CUSTOM_DROP) {
            handleAddCustomDropClick(player, menu, slot, event.getCursor());
            return;
        }
        handleCommandsClick(player, menu, slot, event.getClick());
    }

    @EventHandler
    public void onEditorDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof EditorMenu)
            && !(holder instanceof EntryBrowserHolder)
            && !(holder instanceof MobChooserHolder)
            && !(holder instanceof TriStateSelectionHolder)
            && !(holder instanceof EditorMenuHolder)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void handleEntryBrowserClick(InventoryClickEvent event, Player player, EntryBrowserHolder holder) {
        Object context = holder.request().context();
        EntryBrowserClick click = EntryBrowserMenus.handleClick(event.getRawSlot(), holder, event.getClick());
        playBrowserSound(player, click);
        if (context == null) {
            return;
        }
        switch (click.action()) {
            case ENTRY -> {
                if (context instanceof MainBrowserContext) {
                    editorSounds.open(player);
                    openDropMenu(player, click.entryId());
                } else if (context instanceof CustomBrowserContext custom) {
                    editorSounds.open(player);
                    openCustomDropEntryMenu(player, custom.dropId(), parseBrowserIndex(click.entryId()), holder.request().page());
                } else if (context instanceof CommandBrowserContext commands) {
                    handleCommandBrowserEntry(player, commands, holder, click);
                }
            }
            case ADD -> {
                if (context instanceof MainBrowserContext) {
                    startPrompt(player, PromptType.ADD_DROP, "", -1, holder.request().page(), -1, holder.request().page());
                } else if (context instanceof CustomBrowserContext custom) {
                    editorSounds.open(player);
                    openAddCustomDropMenu(player, custom.dropId(), holder.request().page());
                } else if (context instanceof CommandBrowserContext commands) {
                    startPrompt(player, PromptType.COMMAND_ADD, commands.dropId(), commands.entryIndex(), holder.request().page(), -1, commands.returnPage());
                }
            }
            case BACK -> {
                if (context instanceof MainBrowserContext) {
                    openEditorMenu(player);
                } else if (context instanceof CustomBrowserContext custom) {
                    openDropMenu(player, custom.dropId());
                } else if (context instanceof CommandBrowserContext commands) {
                    openCustomDropEntryMenu(player, commands.dropId(), commands.entryIndex(), commands.returnPage());
                }
            }
            case SEARCH -> startEntryBrowserSearch(player, holder);
            case CLEAR_SEARCH -> reopenEntryBrowser(player, context, "", 0, holder.request().page());
            case PREVIOUS_PAGE -> reopenEntryBrowser(player, context, holder.request().filter(), holder.request().page() - 1, holder.request().page());
            case NEXT_PAGE -> reopenEntryBrowser(player, context, holder.request().filter(), holder.request().page() + 1, holder.request().page());
            case EXTRA, NONE -> {
            }
        }
    }

    private void playBrowserSound(Player player, EntryBrowserClick click) {
        switch (click.action()) {
            case BACK -> editorSounds.back(player);
            case SEARCH -> editorSounds.search(player);
            case CLEAR_SEARCH -> editorSounds.clearSearch(player);
            case PREVIOUS_PAGE -> editorSounds.previousPage(player);
            case NEXT_PAGE -> editorSounds.nextPage(player);
            case ADD, EXTRA, NONE -> {
            }
        }
    }

    private void handleCommandBrowserEntry(Player player, CommandBrowserContext context, EntryBrowserHolder holder, EntryBrowserClick click) {
        int commandIndex = parseBrowserIndex(click.entryId());
        DropDefinition definition = dropStore.getDrop(context.dropId());
        if (definition == null || context.entryIndex() < 0 || context.entryIndex() >= definition.getCustomDrops().size()) {
            openCustomDropsMenu(player, context.dropId(), context.returnPage());
            return;
        }
        List<DropCommandAction> commands = definition.getCustomDrops().get(context.entryIndex()).getCommands();
        if (commandIndex < 0 || commandIndex >= commands.size()) {
            openCommandMenu(player, context.dropId(), context.entryIndex(), holder.request().page(), context.returnPage());
            return;
        }
        DropCommandAction action = commands.get(commandIndex);
        if (click.clickType() == ClickType.SHIFT_RIGHT) {
            if (!definition.getCustomDrops().get(context.entryIndex()).isItemReward() && commands.size() <= 1) {
                editorSounds.error(player);
                plugin.messages().sendConfigured(player, "custom-drop-command-required");
                return;
            }
            commands.remove(commandIndex);
            editorSounds.delete(player);
            dropStore.save();
            plugin.messages().sendConfigured(player, "command-removed");
            reopenEntryBrowser(player, context, holder.request().filter(), holder.request().page(), context.returnPage());
            return;
        }
        if (click.clickType() == ClickType.RIGHT) {
            action.setSenderType(action.getSenderType().next());
            editorSounds.cycle(player);
            dropStore.save();
            plugin.messages().sendConfigured(player, "command-sender-updated", "{sender}", action.getSenderType().getDisplayName());
            reopenEntryBrowser(player, context, holder.request().filter(), holder.request().page(), context.returnPage());
            return;
        }
        startPrompt(player, PromptType.COMMAND_EDIT, context.dropId(), context.entryIndex(), holder.request().page(), commandIndex, context.returnPage());
    }

    private int parseBrowserIndex(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void reopenEntryBrowser(Player player, Object context, String filter, int page, int fallbackPage) {
        if (context instanceof MainBrowserContext) {
            openMainMenu(player, filter, page, dropStore.getAllDrops());
        } else if (context instanceof CustomBrowserContext custom) {
            openCustomDropsMenu(player, custom.dropId(), filter, page);
        } else if (context instanceof CommandBrowserContext commands) {
            openCommandMenu(player, commands.dropId(), commands.entryIndex(), filter, page, commands.returnPage());
        } else {
            openMainMenu(player, fallbackPage);
        }
    }

    private void startEntryBrowserSearch(Player player, EntryBrowserHolder holder) {
        Object context = holder.request().context();
        PromptType type = context instanceof MainBrowserContext ? PromptType.MAIN_SEARCH
            : context instanceof CustomBrowserContext ? PromptType.CUSTOM_SEARCH : PromptType.COMMAND_SEARCH;
        if (context instanceof MainBrowserContext) {
            openInputPrompt(player, new ChatPrompt(type, "", -1, holder.request().page(), -1, holder.request().page(), holder.request().filter(), false));
        } else if (context instanceof CustomBrowserContext custom) {
            openInputPrompt(player, new ChatPrompt(type, custom.dropId(), -1, holder.request().page(), -1, holder.request().page(), holder.request().filter(), false));
        } else if (context instanceof CommandBrowserContext commands) {
            openInputPrompt(player, new ChatPrompt(type, commands.dropId(), commands.entryIndex(), holder.request().page(), -1, commands.returnPage(), holder.request().filter(), false));
        }
    }

    @EventHandler
    public void onPromptChat(AsyncPlayerChatEvent event) {
        ChatPrompt prompt = chatPrompts.get(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();
        core.scheduler().runForPlayer(event.getPlayer(), () -> handlePromptInput(event.getPlayer(), prompt, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chatPrompts.remove(event.getPlayer().getUniqueId());
        mobTargetDropIds.remove(event.getPlayer().getUniqueId());
        worldSelectionDropIds.remove(event.getPlayer().getUniqueId());
        warnedFallbackPlayers.remove(event.getPlayer().getUniqueId());
    }

    private void handleSettingsEditorClick(InventoryClickEvent event, Player player, EditorMenuHolder holder) {
        if (event.getClickedInventory() == null) {
            return;
        }

        event.setCancelled(true);
        boolean clickedTop = event.getClickedInventory().equals(event.getView().getTopInventory());
        if (!clickedTop) {
            return;
        }

        if (!player.hasPermission("fodrops.admin")) {
            player.closeInventory();
            plugin.messages().sendConfigured(player, "no-permission");
            return;
        }

        Optional<ConfigEditorButton> clicked = settingsEditor().buttonAt(holder, event.getSlot());
        if (clicked.isEmpty()) {
            return;
        }

        ConfigEditorButton button = clicked.get();
        if (button.type() == ConfigEditorValueType.BOOLEAN) {
            saveBooleanSetting(player, button);
            return;
        }
        if (button.type() == ConfigEditorValueType.INTEGER) {
            startConfigPrompt(player, promptTypeForSetting(button.id()));
            return;
        }

        if (button.id().equals("manage-drops")) {
            editorSounds.open(player);
            openMainMenu(player, 0);
        }
    }

    private void saveBooleanSetting(Player player, ConfigEditorButton button) {
        EditorSaveResult result = settingsEditor().toggle(button);
        if (!sendSettingSaveResult(player, button.label(), result)) {
            return;
        }
        openEditorMenu(player);
    }

    private boolean sendSettingSaveResult(Player player, String setting, EditorSaveResult result) {
        if (!result.successful()) {
            editorSounds.error(player);
            plugin.messages().sendConfigured(player, "editor-save-failed",
                "{setting}", setting,
                "{error}", result.errorMessage());
            return false;
        }

        plugin.messages().sendConfigured(player, "editor-saved", "{setting}", setting);
        editorSounds.save(player);
        return true;
    }

    private void handleMainClick(Player player, EditorMenu menu, int slot) {
        String dropId = menu.dropBySlot.get(slot);
        if (dropId != null) {
            editorSounds.open(player);
            openDropMenu(player, dropId);
            return;
        }

        if (slot == 49) {
            editorSounds.back(player);
            openEditorMenu(player);
            return;
        }
        if (slot == 47) {
            if (dropStore.getAllDrops().size() >= plugin.getMaxProfiles()) {
                editorSounds.error(player);
                plugin.messages().sendConfigured(player, "drop-limit-reached", "{max}", String.valueOf(plugin.getMaxProfiles()));
                return;
            }

            startPrompt(player, PromptType.ADD_DROP, "", -1, menu.page, -1, menu.page);
            return;
        }
        if (slot == 45) {
            editorSounds.previousPage(player);
            openMainMenu(player, menu.page - 1);
            return;
        }
        if (slot == 53) {
            editorSounds.nextPage(player);
            openMainMenu(player, menu.page + 1);
        }
    }

    private void handleDropClick(Player player, EditorMenu menu, int slot) {
        DropDefinition definition = dropStore.getDrop(menu.dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        if (slot == 10) {
            startPrompt(player, PromptType.RENAME, definition.getId(), -1, 0, -1, 0);
            return;
        }
        if (slot == 11) {
            definition.setEventType(definition.getEventType().next());
            editorSounds.cycle(player);
            dropStore.save();
            plugin.messages().sendConfigured(player, "drop-event-updated", "{event}", definition.getEventType().getDisplayName());
            openDropMenu(player, definition.getId());
            return;
        }
        if (slot == 12) {
            if (definition.getEventType() == DropEventType.MOB_KILL) {
                editorSounds.open(player);
                openMobTargetSelector(player, definition.getId(), 0);
                return;
            }

            startPrompt(player, PromptType.CONDITION_TARGETS, definition.getId(), -1, 0, -1, 0);
            return;
        }
        if (slot == 13) {
            editorSounds.open(player);
            openConditionsMenu(player, definition.getId());
            return;
        }
        if (slot == 14) {
            startPrompt(player, PromptType.PRIORITY, definition.getId(), -1, 0, -1, 0);
            return;
        }
        if (slot == 15) {
            definition.setCancelVanillaDrops(!definition.isCancelVanillaDrops());
            editorSounds.toggle(player, definition.isCancelVanillaDrops());
            dropStore.save();
            plugin.messages().sendConfigured(player, "drop-cancel-updated", "{state}", definition.isCancelVanillaDrops() ? "{good}Enabled" : "{bad}Disabled");
            openDropMenu(player, definition.getId());
            return;
        }
        if (slot == 16) {
            definition.setStopProcessing(!definition.isStopProcessing());
            editorSounds.toggle(player, definition.isStopProcessing());
            dropStore.save();
            plugin.messages().sendConfigured(player, "drop-stop-updated", "{state}", definition.isStopProcessing() ? "{good}Enabled" : "{bad}Disabled");
            openDropMenu(player, definition.getId());
            return;
        }
        if (slot == 19) {
            editorSounds.open(player);
            openCustomDropsMenu(player, definition.getId(), 0);
            return;
        }
        if (slot == 20) {
            editorSounds.open(player);
            openDeleteConfirmMenu(player, definition.getId());
            return;
        }
        if (slot == GuiSlots.bottomMiddleSlot(4)) {
            editorSounds.back(player);
            openMainMenu(player, 0);
        }
    }

    private void handleDeleteConfirmClick(Player player, EditorMenu menu, int slot) {
        DropDefinition definition = dropStore.getDrop(menu.dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        if (slot == 11) {
            dropStore.deleteDrop(definition.getId());
            editorSounds.delete(player);
            plugin.messages().sendConfigured(player, "drop-deleted", "{id}", definition.getId());
            openMainMenu(player, 0);
            return;
        }

        if (slot == GuiSlots.bottomMiddleSlot(3)) {
            editorSounds.back(player);
            openDropMenu(player, definition.getId());
        }
    }

    private void handleMobChooserInventoryClick(InventoryClickEvent event, Player player, MobChooserHolder holder) {
        if (event.getClickedInventory() == null) {
            return;
        }

        event.setCancelled(true);
        boolean clickedTop = event.getClickedInventory().equals(event.getView().getTopInventory());
        if (!clickedTop) {
            return;
        }

        if (!player.hasPermission("fodrops.admin")) {
            player.closeInventory();
            plugin.messages().sendConfigured(player, "no-permission");
            return;
        }

        String dropId = mobTargetDropIds.get(player.getUniqueId());
        if (dropId == null) {
            player.closeInventory();
            return;
        }

        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            mobTargetDropIds.remove(player.getUniqueId());
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        MobChooserClick click = MobChooserMenus.handleClick(event.getRawSlot(), holder);
        if (click.action() == MobChooserActionType.PREVIOUS_PAGE
            || click.action() == MobChooserActionType.NEXT_PAGE
            || click.action() == MobChooserActionType.CLEAR_SEARCH) {
            if (click.action() == MobChooserActionType.PREVIOUS_PAGE) {
                editorSounds.previousPage(player);
            } else if (click.action() == MobChooserActionType.NEXT_PAGE) {
                editorSounds.nextPage(player);
            } else {
                editorSounds.clearSearch(player);
            }
            openMobTargetSelector(player, definition.getId(), click.nextRequest().page(), click.nextRequest().filter());
            return;
        }
        if (click.action() == MobChooserActionType.SEARCH) {
            editorSounds.search(player);
            startMobTargetSearchPrompt(player, definition.getId(), holder.request().page(), holder.request().filter());
            return;
        }
        if (click.action() == MobChooserActionType.BACK) {
            editorSounds.back(player);
            mobTargetDropIds.remove(player.getUniqueId());
            openDropMenu(player, definition.getId());
            return;
        }
        if (click.action() != MobChooserActionType.TOGGLE && click.action() != MobChooserActionType.SELECT) {
            return;
        }

        toggleMobTarget(definition, click.entityType());
        editorSounds.cycle(player);
        plugin.messages().sendConfigured(player, "condition-updated", "{condition}", "mob targets");
        dropStore.save();
        openMobTargetSelector(player, definition.getId(), click.page(), click.filter());
    }

    private void handleWorldSelectorInventoryClick(InventoryClickEvent event, Player player, TriStateSelectionHolder holder) {
        if (event.getClickedInventory() == null) {
            return;
        }

        event.setCancelled(true);
        boolean clickedTop = event.getClickedInventory().equals(event.getView().getTopInventory());
        if (!clickedTop) {
            return;
        }

        if (!player.hasPermission("fodrops.admin")) {
            player.closeInventory();
            plugin.messages().sendConfigured(player, "no-permission");
            return;
        }

        String dropId = worldSelectionDropIds.get(player.getUniqueId());
        if (dropId == null) {
            player.closeInventory();
            return;
        }

        DropDefinition definition = dropStore.getDrop(dropId);
        if (definition == null) {
            worldSelectionDropIds.remove(player.getUniqueId());
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        TriStateSelectionClick click = TriStateSelectionMenus.handleClick(event.getRawSlot(), holder);
        if (click.action() == TriStateSelectionActionType.PREVIOUS_PAGE
            || click.action() == TriStateSelectionActionType.NEXT_PAGE
            || click.action() == TriStateSelectionActionType.CLEAR_SEARCH) {
            if (click.action() == TriStateSelectionActionType.PREVIOUS_PAGE) {
                editorSounds.previousPage(player);
            } else if (click.action() == TriStateSelectionActionType.NEXT_PAGE) {
                editorSounds.nextPage(player);
            } else {
                editorSounds.clearSearch(player);
            }
            openWorldSelector(player, definition.getId(), click.nextRequest().page(), click.nextRequest().filter());
            return;
        }
        if (click.action() == TriStateSelectionActionType.SEARCH) {
            editorSounds.search(player);
            startWorldSearchPrompt(player, definition.getId(), holder.request().page(), holder.request().filter());
            return;
        }
        if (click.action() == TriStateSelectionActionType.BACK) {
            editorSounds.back(player);
            worldSelectionDropIds.remove(player.getUniqueId());
            openConditionsMenu(player, definition.getId());
            return;
        }
        if (click.action() != TriStateSelectionActionType.TOGGLE) {
            return;
        }

        saveWorldSelection(definition.getConditions(), click.nextRequest());
        editorSounds.cycle(player);
        plugin.messages().sendConfigured(player, "condition-updated", "{condition}", "worlds");
        dropStore.save();
        openWorldSelector(player, definition.getId(), click.page(), click.filter());
    }

    private List<String> configuredWorldNames(DropConditions conditions) {
        List<String> configured = new ArrayList<>();
        configured.addAll(conditions.getWorlds());
        configured.addAll(conditions.getDisabledWorlds());
        return configured;
    }

    private void saveWorldSelection(DropConditions conditions, TriStateSelectionRequest request) {
        conditions.getWorlds().clear();
        conditions.getWorlds().addAll(normalizedWorldKeys(TriStateSelections.enabledKeys(request)));
        conditions.getDisabledWorlds().clear();
        conditions.getDisabledWorlds().addAll(normalizedWorldKeys(TriStateSelections.disabledKeys(request)));
    }

    private List<String> normalizedWorldKeys(List<String> values) {
        return values.stream()
            .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
            .filter(value -> !value.isBlank())
            .distinct()
            .sorted()
            .toList();
    }

    private void toggleMobTarget(DropDefinition definition, EntityType entityType) {
        if (entityType == null) {
            return;
        }

        String target = entityType.name();
        List<String> targets = definition.getConditions().getTargets();
        if (!targets.removeIf(existing -> existing.equalsIgnoreCase(target))) {
            targets.add(target);
            targets.sort(String::compareTo);
        }
    }

    private void handleConditionsClick(Player player, EditorMenu menu, int slot) {
        DropDefinition definition = dropStore.getDrop(menu.dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        DropConditions conditions = definition.getConditions();
        if (slot == 10) {
            editorSounds.open(player);
            openWorldSelector(player, definition.getId(), 0);
            return;
        }
        if (slot == 11) {
            startPrompt(player, PromptType.CONDITION_BIOMES, definition.getId(), -1, 0, -1, 0);
            return;
        }
        if (slot == 12) {
            startPrompt(player, PromptType.CONDITION_WEATHER, definition.getId(), -1, 0, -1, 0);
            return;
        }
        if (slot == 13) {
            startPrompt(player, PromptType.CONDITION_TOOLS, definition.getId(), -1, 0, -1, 0);
            return;
        }
        if (slot == 14) {
            conditions.setSilkTouchMode(conditions.getSilkTouchMode().next());
            editorSounds.cycle(player);
            dropStore.save();
            plugin.messages().sendConfigured(player, "condition-updated", "{condition}", "silk-touch");
            openConditionsMenu(player, definition.getId());
            return;
        }
        if (slot == 15) {
            startPrompt(player, PromptType.CONDITION_FORTUNE, definition.getId(), -1, 0, -1, 0);
            return;
        }
        if (slot == 16) {
            startPrompt(player, PromptType.CONDITION_PERMISSION, definition.getId(), -1, 0, -1, 0);
            return;
        }
        if (slot == 19) {
            startPrompt(player, PromptType.CONDITION_TIME, definition.getId(), -1, 0, -1, 0);
            return;
        }
        if (slot == 20) {
            startPrompt(player, PromptType.CONDITION_COOLDOWN, definition.getId(), -1, 0, -1, 0);
            return;
        }
        if (slot == GuiSlots.bottomMiddleSlot(4)) {
            editorSounds.back(player);
            openDropMenu(player, definition.getId());
        }
    }

    private void handleCustomClick(Player player, EditorMenu menu, int slot, ClickType clickType, ItemStack cursor) {
        DropDefinition definition = dropStore.getDrop(menu.dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        Integer entryIndex = menu.entryBySlot.get(slot);
        if (entryIndex != null) {
            if (entryIndex < 0 || entryIndex >= definition.getCustomDrops().size()) {
                openCustomDropsMenu(player, definition.getId(), menu.page);
                return;
            }

            editorSounds.open(player);
            openCustomDropEntryMenu(player, definition.getId(), entryIndex, menu.page);
            return;
        }

        if (slot == 47) {
            editorSounds.open(player);
            openAddCustomDropMenu(player, definition.getId(), menu.page);
            return;
        }
        if (slot == GuiSlots.bottomMiddleSlot(6)) {
            editorSounds.back(player);
            openDropMenu(player, definition.getId());
            return;
        }
        int totalPages = Math.max(1, (int) Math.ceil(definition.getCustomDrops().size() / (double) GRID_SLOTS.length));
        if (slot == 45 && menu.page > 0) {
            editorSounds.previousPage(player);
            openCustomDropsMenu(player, definition.getId(), menu.page - 1);
            return;
        }
        if (slot == 53 && menu.page < totalPages - 1) {
            editorSounds.nextPage(player);
            openCustomDropsMenu(player, definition.getId(), menu.page + 1);
        }
    }

    private void handleCustomEntryClick(Player player, EditorMenu menu, int slot, ClickType clickType, ItemStack cursor) {
        DropDefinition definition = dropStore.getDrop(menu.dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }
        if (menu.selectedIndex < 0 || menu.selectedIndex >= definition.getCustomDrops().size()) {
            plugin.messages().sendConfigured(player, "custom-drop-missing");
            openCustomDropsMenu(player, definition.getId(), menu.returnPage);
            return;
        }

        CustomDropEntry entry = definition.getCustomDrops().get(menu.selectedIndex);
        if (slot == 10) {
            editorSounds.open(player);
            openCommandMenu(player, definition.getId(), menu.selectedIndex, 0, menu.returnPage);
            return;
        }
        if (slot == 11) {
            startPrompt(player, PromptType.CHANCE, definition.getId(), menu.selectedIndex, menu.returnPage, -1, menu.returnPage);
            return;
        }
        if (slot == 12) {
            if (!entry.isItemReward()) {
                return;
            }
            startPrompt(player, PromptType.AMOUNT, definition.getId(), menu.selectedIndex, menu.returnPage, -1, menu.returnPage);
            return;
        }
        if (slot == 13) {
            if (!entry.isItemReward()) {
                return;
            }
            entry.setDeliveryMode(entry.getDeliveryMode().next());
            editorSounds.cycle(player);
            dropStore.save();
            plugin.messages().sendConfigured(player, "custom-drop-delivery-updated",
                "{mode}", entry.getDeliveryMode() == DropDeliveryMode.INVENTORY ? "{good}Inventory" : "{theme}Ground");
            openCustomDropEntryMenu(player, definition.getId(), menu.selectedIndex, menu.returnPage);
            return;
        }
        if (slot == 14) {
            entry.setSendMessage(!entry.isSendMessage());
            editorSounds.toggle(player, entry.isSendMessage());
            dropStore.save();
            plugin.messages().sendConfigured(player, "custom-drop-message-updated", "{state}", entry.isSendMessage() ? "{good}Enabled" : "{bad}Disabled");
            openCustomDropEntryMenu(player, definition.getId(), menu.selectedIndex, menu.returnPage);
            return;
        }
        if (slot == 15) {
            if (!entry.isItemReward()) {
                plugin.messages().sendConfigured(player, "custom-drop-fortune-updated", "{state}", "{bad}Item rewards only");
                return;
            }
            entry.setRespectFortune(!entry.isRespectFortune());
            editorSounds.toggle(player, entry.isRespectFortune());
            dropStore.save();
            plugin.messages().sendConfigured(player, "custom-drop-fortune-updated", "{state}", entry.isRespectFortune() ? "{good}Enabled" : "{bad}Disabled");
            openCustomDropEntryMenu(player, definition.getId(), menu.selectedIndex, menu.returnPage);
            return;
        }
        if (slot == 16) {
            if (entry.isItemReward() && entry.getCommands().isEmpty()) {
                editorSounds.error(player);
                plugin.messages().sendConfigured(player, "custom-drop-command-required");
                return;
            }
            entry.setItemReward(!entry.isItemReward());
            editorSounds.toggle(player, entry.isItemReward());
            dropStore.save();
            plugin.messages().sendConfigured(player, "custom-drop-type-updated", "{state}", entry.isItemReward() ? "{good}Enabled" : "{bad}Disabled");
            openCustomDropEntryMenu(player, definition.getId(), menu.selectedIndex, menu.returnPage);
            return;
        }
        if (slot == 22) {
            editorSounds.open(player);
            openCustomDropDeleteConfirmMenu(player, definition.getId(), menu.selectedIndex, menu.returnPage);
            return;
        }
        if (slot == GuiSlots.bottomMiddleSlot(4)) {
            editorSounds.back(player);
            openCustomDropsMenu(player, definition.getId(), menu.returnPage);
        }
    }

    private void handleCustomDeleteConfirmClick(Player player, EditorMenu menu, int slot) {
        DropDefinition definition = dropStore.getDrop(menu.dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }
        if (menu.selectedIndex < 0 || menu.selectedIndex >= definition.getCustomDrops().size()) {
            plugin.messages().sendConfigured(player, "custom-drop-missing");
            openCustomDropsMenu(player, definition.getId(), menu.returnPage);
            return;
        }

        if (slot == 11) {
            definition.getCustomDrops().remove(menu.selectedIndex);
            editorSounds.delete(player);
            dropStore.save();
            plugin.messages().sendConfigured(player, "custom-drop-removed");
            openCustomDropsMenu(player, definition.getId(), menu.returnPage);
            return;
        }
        if (slot == GuiSlots.bottomMiddleSlot(3)) {
            editorSounds.back(player);
            openCustomDropEntryMenu(player, definition.getId(), menu.selectedIndex, menu.returnPage);
        }
    }

    private void handleAddCustomDropClick(Player player, EditorMenu menu, int slot, ItemStack cursor) {
        DropDefinition definition = dropStore.getDrop(menu.dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        if (slot == 22) {
            editorSounds.back(player);
            openCustomDropsMenu(player, definition.getId(), menu.returnPage);
            return;
        }

        if (slot != 11 && slot != 15) {
            return;
        }

        if (definition.getCustomDrops().size() >= plugin.getMaxCustomDropsPerProfile()) {
            editorSounds.error(player);
            plugin.messages().sendConfigured(player, "custom-drop-limit-reached", "{max}", String.valueOf(plugin.getMaxCustomDropsPerProfile()));
            return;
        }

        if (slot == 11) {
            ItemStack inputItem = CursorItemEditor.cloneItem(cursor).orElse(null);
            if (inputItem == null) {
                editorSounds.addItemError(player);
                plugin.messages().sendConfigured(player, "cursor-required");
                return;
            }

            int amount = Math.min(Math.max(1, inputItem.getAmount()), plugin.getMaxDropAmount());
            definition.getCustomDrops().add(new CustomDropEntry(inputItem, 100.0D, amount, amount, DropDeliveryMode.GROUND, plugin.getMaxDropAmount()));
            editorSounds.addItem(player);
            dropStore.save();
            plugin.messages().sendConfigured(player, "custom-drop-added");
            openCustomDropsMenu(player, definition.getId(), menu.returnPage);
            return;
        }

        if (plugin.getMaxCommandsPerEntry() <= 0) {
            editorSounds.error(player);
            plugin.messages().sendConfigured(player, "command-limit-reached", "{max}", String.valueOf(plugin.getMaxCommandsPerEntry()));
            return;
        }

        startPrompt(player, PromptType.COMMAND_DROP_ADD, definition.getId(), -1, menu.returnPage, -1, menu.returnPage);
    }

    private void handleCommandsClick(Player player, EditorMenu menu, int slot, ClickType clickType) {
        DropDefinition definition = dropStore.getDrop(menu.dropId);
        if (definition == null) {
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }
        if (menu.selectedIndex < 0 || menu.selectedIndex >= definition.getCustomDrops().size()) {
            plugin.messages().sendConfigured(player, "custom-drop-missing");
            openCustomDropsMenu(player, definition.getId(), menu.returnPage);
            return;
        }

        CustomDropEntry entry = definition.getCustomDrops().get(menu.selectedIndex);
        Integer commandIndex = menu.commandBySlot.get(slot);
        if (commandIndex != null) {
            if (commandIndex < 0 || commandIndex >= entry.getCommands().size()) {
                openCommandMenu(player, definition.getId(), menu.selectedIndex, menu.page, menu.returnPage);
                return;
            }

            DropCommandAction action = entry.getCommands().get(commandIndex);
            if (clickType == ClickType.SHIFT_RIGHT) {
                if (!entry.isItemReward() && entry.getCommands().size() <= 1) {
                    editorSounds.error(player);
                    plugin.messages().sendConfigured(player, "custom-drop-command-required");
                    return;
                }
                entry.getCommands().remove((int) commandIndex);
                dropStore.save();
                plugin.messages().sendConfigured(player, "command-removed");
                openCommandMenu(player, definition.getId(), menu.selectedIndex, menu.page, menu.returnPage);
                return;
            }
            if (clickType == ClickType.RIGHT) {
                action.setSenderType(action.getSenderType().next());
                dropStore.save();
                plugin.messages().sendConfigured(player, "command-sender-updated", "{sender}", action.getSenderType().getDisplayName());
                openCommandMenu(player, definition.getId(), menu.selectedIndex, menu.page, menu.returnPage);
                return;
            }
            if (clickType == ClickType.LEFT) {
                startPrompt(player, PromptType.COMMAND_EDIT, definition.getId(), menu.selectedIndex, menu.page, commandIndex, menu.returnPage);
            }
            return;
        }

        if (slot == 47) {
            if (entry.getCommands().size() >= plugin.getMaxCommandsPerEntry()) {
                editorSounds.error(player);
                plugin.messages().sendConfigured(player, "command-limit-reached", "{max}", String.valueOf(plugin.getMaxCommandsPerEntry()));
                return;
            }

            startPrompt(player, PromptType.COMMAND_ADD, definition.getId(), menu.selectedIndex, menu.page, -1, menu.returnPage);
            return;
        }
        if (slot == GuiSlots.bottomMiddleSlot(6)) {
            editorSounds.back(player);
            openCustomDropEntryMenu(player, definition.getId(), menu.selectedIndex, menu.returnPage);
            return;
        }
        int totalPages = Math.max(1, (int) Math.ceil(entry.getCommands().size() / (double) GRID_SLOTS.length));
        if (slot == 45 && menu.page > 0) {
            editorSounds.previousPage(player);
            openCommandMenu(player, definition.getId(), menu.selectedIndex, menu.page - 1, menu.returnPage);
            return;
        }
        if (slot == 53 && menu.page < totalPages - 1) {
            editorSounds.nextPage(player);
            openCommandMenu(player, definition.getId(), menu.selectedIndex, menu.page + 1, menu.returnPage);
        }
    }

    private void handlePromptInput(Player player, ChatPrompt prompt, String input) {
        if (!player.isOnline()) {
            chatPrompts.remove(player.getUniqueId());
            return;
        }

        if (!player.hasPermission("fodrops.admin")) {
            chatPrompts.remove(player.getUniqueId());
            plugin.messages().sendConfigured(player, "no-permission");
            return;
        }

        if (input.equalsIgnoreCase("cancel")) {
            cancelPrompt(player, prompt);
            return;
        }

        if (prompt.type.isConfigSetting()) {
            handleConfigPromptInput(player, prompt, input);
            return;
        }
        if (prompt.type == PromptType.ADD_DROP) {
            handleAddDropPromptInput(player, prompt, input);
            return;
        }
        if (prompt.type == PromptType.MAIN_SEARCH || prompt.type == PromptType.CUSTOM_SEARCH || prompt.type == PromptType.COMMAND_SEARCH) {
            chatPrompts.remove(player.getUniqueId());
            String filter = input.equalsIgnoreCase("none") ? "" : input.trim();
            if (prompt.type == PromptType.MAIN_SEARCH) {
                reopenEntryBrowser(player, new MainBrowserContext(), filter, 0, prompt.page);
            } else if (prompt.type == PromptType.CUSTOM_SEARCH) {
                reopenEntryBrowser(player, new CustomBrowserContext(prompt.dropId), filter, 0, prompt.returnPage);
            } else {
                reopenEntryBrowser(player, new CommandBrowserContext(prompt.dropId, prompt.entryIndex, prompt.returnPage), filter, 0, prompt.returnPage);
            }
            return;
        }

        DropDefinition definition = dropStore.getDrop(prompt.dropId);
        if (definition == null) {
            chatPrompts.remove(player.getUniqueId());
            editorSounds.error(player);
            plugin.messages().sendConfigured(player, "drop-missing");
            openMainMenu(player, 0);
            return;
        }

        switch (prompt.type) {
            case MOB_TARGET_SEARCH -> {
                chatPrompts.remove(player.getUniqueId());
                String filter = input.equalsIgnoreCase("none") ? "" : input;
                openMobTargetSelector(player, definition.getId(), 0, filter);
            }
            case WORLD_SEARCH -> {
                chatPrompts.remove(player.getUniqueId());
                String filter = input.equalsIgnoreCase("none") ? "" : input;
                openWorldSelector(player, definition.getId(), 0, filter);
            }
            case MAIN_SEARCH, CUSTOM_SEARCH, COMMAND_SEARCH -> {
            }
            case RENAME -> {
                if (input.isBlank()) {
                    rejectPromptInput(player, prompt, "prompt-invalid-text");
                    return;
                }
                definition.setName(input.trim());
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "drop-renamed", "{name}", input.trim());
                openDropMenu(player, definition.getId());
            }
            case PRIORITY -> {
                Integer value = parseInteger(input);
                if (value == null) {
                    rejectPromptInput(player, prompt, "prompt-invalid-number");
                    return;
                }
                definition.setPriority(value);
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "drop-priority-updated", "{priority}", String.valueOf(value));
                openDropMenu(player, definition.getId());
            }
            case CHANCE -> {
                CustomDropEntry entry = getPromptEntry(player, definition, prompt);
                if (entry == null) {
                    return;
                }

                Double chance = parseDouble(input);
                if (chance == null) {
                    rejectPromptInput(player, prompt, "prompt-invalid-number");
                    return;
                }
                if (chance < 0.0D || chance > 100.0D) {
                    rejectPromptInput(player, prompt, "prompt-chance-range");
                    return;
                }

                entry.setChance(chance);
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "custom-drop-chance-updated", "{chance}", trimChance(chance));
                openCustomDropEntryMenu(player, definition.getId(), prompt.entryIndex, prompt.returnPage);
            }
            case AMOUNT -> {
                CustomDropEntry entry = getPromptEntry(player, definition, prompt);
                if (entry == null) {
                    return;
                }

                int[] amountRange = parseAmountRange(input, plugin.getMaxDropAmount());
                if (amountRange == null) {
                    rejectPromptInput(player, prompt, "prompt-amount-format", "{max}", String.valueOf(plugin.getMaxDropAmount()));
                    return;
                }

                entry.setAmountRange(amountRange[0], amountRange[1], plugin.getMaxDropAmount());
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "custom-drop-amount-updated",
                    "{min}", String.valueOf(amountRange[0]),
                    "{max}", String.valueOf(amountRange[1]));
                openCustomDropEntryMenu(player, definition.getId(), prompt.entryIndex, prompt.returnPage);
            }
            case CONDITION_WORLDS -> {
                List<String> worlds = parseSimpleList(input, true, false);
                definition.getConditions().getWorlds().clear();
                definition.getConditions().getWorlds().addAll(worlds);
                definition.getConditions().getDisabledWorlds().clear();
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "condition-updated", "{condition}", "worlds");
                openConditionsMenu(player, definition.getId());
            }
            case CONDITION_BIOMES -> {
                List<String> biomes = parseSimpleList(input, false, true);
                definition.getConditions().getBiomes().clear();
                definition.getConditions().getBiomes().addAll(biomes);
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "condition-updated", "{condition}", "biomes");
                openConditionsMenu(player, definition.getId());
            }
            case CONDITION_TARGETS -> {
                List<String> targets = parseTargetList(input);
                definition.getConditions().getTargets().clear();
                definition.getConditions().getTargets().addAll(targets);
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "condition-updated", "{condition}", "targets");
                openDropMenu(player, definition.getId());
            }
            case CONDITION_PERMISSION -> {
                definition.getConditions().setPermission(input.equalsIgnoreCase("none") ? "" : input);
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "condition-updated", "{condition}", "permission");
                openConditionsMenu(player, definition.getId());
            }
            case CONDITION_TOOLS -> {
                List<String> tools = parseToolList(input);
                definition.getConditions().getTools().clear();
                definition.getConditions().getTools().addAll(tools);
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "condition-updated", "{condition}", "tools");
                openConditionsMenu(player, definition.getId());
            }
            case CONDITION_FORTUNE -> {
                Integer value = parseInteger(input);
                if (value == null || value < 0) {
                    rejectPromptInput(player, prompt, "prompt-invalid-number");
                    return;
                }
                definition.getConditions().setFortuneMin(value);
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "condition-updated", "{condition}", "fortune-min");
                openConditionsMenu(player, definition.getId());
            }
            case CONDITION_TIME -> {
                if (input.equalsIgnoreCase("none")) {
                    definition.getConditions().setTimeRange(null, null);
                } else {
                    int[] range = parseTimeRange(input);
                    if (range == null) {
                        rejectPromptInput(player, prompt, "prompt-time-format");
                        return;
                    }
                    definition.getConditions().setTimeRange(range[0], range[1]);
                }
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "condition-updated", "{condition}", "time");
                openConditionsMenu(player, definition.getId());
            }
            case CONDITION_WEATHER -> {
                List<DropWeather> weather = parseWeather(input);
                if (weather == null) {
                    rejectPromptInput(player, prompt, "prompt-weather-format");
                    return;
                }
                definition.getConditions().getWeather().clear();
                definition.getConditions().getWeather().addAll(weather);
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "condition-updated", "{condition}", "weather");
                openConditionsMenu(player, definition.getId());
            }
            case CONDITION_COOLDOWN -> {
                Long value = parseLong(input);
                if (value == null || value < 0L) {
                    rejectPromptInput(player, prompt, "prompt-invalid-number");
                    return;
                }
                definition.getConditions().setCooldownMs(value);
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "condition-updated", "{condition}", "cooldown-ms");
                openConditionsMenu(player, definition.getId());
            }
            case COMMAND_DROP_ADD -> {
                if (plugin.getMaxCommandsPerEntry() <= 0) {
                    chatPrompts.remove(player.getUniqueId());
                    editorSounds.error(player);
                    plugin.messages().sendConfigured(player, "command-limit-reached", "{max}", String.valueOf(plugin.getMaxCommandsPerEntry()));
                    openCustomDropsMenu(player, definition.getId(), prompt.returnPage);
                    return;
                }

                if (definition.getCustomDrops().size() >= plugin.getMaxCustomDropsPerProfile()) {
                    chatPrompts.remove(player.getUniqueId());
                    editorSounds.error(player);
                    plugin.messages().sendConfigured(player, "custom-drop-limit-reached", "{max}", String.valueOf(plugin.getMaxCustomDropsPerProfile()));
                    openCustomDropsMenu(player, definition.getId(), prompt.returnPage);
                    return;
                }

                DropCommandAction command = parseCommandWithSender(input);
                if (command == null) {
                    rejectPromptInput(player, prompt, "prompt-command-format");
                    return;
                }

                CustomDropEntry entry = new CustomDropEntry(
                    new ItemStack(Material.COMMAND_BLOCK),
                    100.0D,
                    1,
                    1,
                    DropDeliveryMode.GROUND,
                    List.of(command),
                    plugin.getMaxDropAmount(),
                    false
                );
                definition.getCustomDrops().add(entry);
                dropStore.save();
                editorSounds.add(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "custom-command-added");
                openCustomDropsMenu(player, definition.getId(), prompt.returnPage);
            }
            case COMMAND_ADD -> {
                CustomDropEntry entry = getPromptEntry(player, definition, prompt);
                if (entry == null) {
                    return;
                }
                if (entry.getCommands().size() >= plugin.getMaxCommandsPerEntry()) {
                    chatPrompts.remove(player.getUniqueId());
                    editorSounds.error(player);
                    plugin.messages().sendConfigured(player, "command-limit-reached", "{max}", String.valueOf(plugin.getMaxCommandsPerEntry()));
                    openCommandMenu(player, definition.getId(), prompt.entryIndex, prompt.page, prompt.returnPage);
                    return;
                }

                DropCommandAction command = parseCommandWithSender(input);
                if (command == null) {
                    rejectPromptInput(player, prompt, "prompt-command-format");
                    return;
                }

                entry.getCommands().add(command);
                dropStore.save();
                editorSounds.add(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "command-added");
                openCommandMenu(player, definition.getId(), prompt.entryIndex, prompt.page, prompt.returnPage);
            }
            case COMMAND_EDIT -> {
                CustomDropEntry entry = getPromptEntry(player, definition, prompt);
                if (entry == null) {
                    return;
                }
                if (prompt.commandIndex < 0 || prompt.commandIndex >= entry.getCommands().size()) {
                    plugin.messages().sendConfigured(player, "command-missing");
                    chatPrompts.remove(player.getUniqueId());
                    openCommandMenu(player, definition.getId(), prompt.entryIndex, prompt.page, prompt.returnPage);
                    return;
                }
                if (input.isBlank()) {
                    rejectPromptInput(player, prompt, "prompt-invalid-text");
                    return;
                }

                entry.getCommands().get(prompt.commandIndex).setCommand(input.trim());
                dropStore.save();
                editorSounds.save(player);
                chatPrompts.remove(player.getUniqueId());
                plugin.messages().sendConfigured(player, "command-updated");
                openCommandMenu(player, definition.getId(), prompt.entryIndex, prompt.page, prompt.returnPage);
            }
        }
    }

    private void handleAddDropPromptInput(Player player, ChatPrompt prompt, String input) {
        String name = input.trim();
        if (name.isBlank()) {
            rejectPromptInput(player, prompt, "prompt-invalid-text");
            return;
        }

        if (dropStore.getAllDrops().size() >= plugin.getMaxProfiles()) {
            chatPrompts.remove(player.getUniqueId());
            editorSounds.error(player);
            plugin.messages().sendConfigured(player, "drop-limit-reached", "{max}", String.valueOf(plugin.getMaxProfiles()));
            openMainMenu(player, prompt.page);
            return;
        }

        DropDefinition created = dropStore.createDrop(name);
        chatPrompts.remove(player.getUniqueId());
        editorSounds.add(player);
        plugin.messages().sendConfigured(player, "drop-created", "{id}", created.getId());
        openDropMenu(player, created.getId());
    }

    private void handleConfigPromptInput(Player player, ChatPrompt prompt, String input) {
        ConfigEditorButton button = configButton(prompt.type);
        if (button == null) {
            chatPrompts.remove(player.getUniqueId());
            openEditorMenu(player);
            return;
        }

        OptionalInt parsed = settingsEditor().parseInteger(input, configMin(prompt.type), configMax(prompt.type));
        if (parsed.isEmpty()) {
            rejectPromptInput(player, prompt, "prompt-limit-format",
                "{min}", String.valueOf(configMin(prompt.type)),
                "{max}", String.valueOf(configMax(prompt.type)));
            return;
        }

        EditorSaveResult result = settingsEditor().save(button, parsed.getAsInt());
        chatPrompts.remove(player.getUniqueId());
        if (!sendSettingSaveResult(player, button.label(), result)) {
            openEditorMenu(player);
            return;
        }
        openEditorMenu(player);
    }

    private CustomDropEntry getPromptEntry(Player player, DropDefinition definition, ChatPrompt prompt) {
        if (prompt.entryIndex < 0 || prompt.entryIndex >= definition.getCustomDrops().size()) {
            chatPrompts.remove(player.getUniqueId());
            editorSounds.error(player);
            plugin.messages().sendConfigured(player, "custom-drop-missing");
            openCustomDropsMenu(player, definition.getId(), prompt.returnPage);
            return null;
        }
        return definition.getCustomDrops().get(prompt.entryIndex);
    }

    private void reopenAfterPrompt(Player player, ChatPrompt prompt) {
        switch (prompt.type) {
            case ADD_DROP -> openMainMenu(player, prompt.page);
            case MAIN_SEARCH -> reopenEntryBrowser(player, new MainBrowserContext(), prompt.filter, prompt.page, prompt.page);
            case CUSTOM_SEARCH -> reopenEntryBrowser(player, new CustomBrowserContext(prompt.dropId), prompt.filter, prompt.page, prompt.returnPage);
            case COMMAND_SEARCH -> reopenEntryBrowser(player, new CommandBrowserContext(prompt.dropId, prompt.entryIndex, prompt.returnPage), prompt.filter, prompt.page, prompt.returnPage);
            case RENAME, PRIORITY -> openDropMenu(player, prompt.dropId);
            case CHANCE, AMOUNT -> openCustomDropEntryMenu(player, prompt.dropId, prompt.entryIndex, prompt.returnPage);
            case CONDITION_TARGETS -> openDropMenu(player, prompt.dropId);
            case MOB_TARGET_SEARCH -> openMobTargetSelector(player, prompt.dropId, prompt.page, prompt.filter);
            case WORLD_SEARCH -> openWorldSelector(player, prompt.dropId, prompt.page, prompt.filter);
            case CONDITION_WORLDS, CONDITION_BIOMES, CONDITION_PERMISSION, CONDITION_TOOLS, CONDITION_FORTUNE, CONDITION_TIME, CONDITION_WEATHER, CONDITION_COOLDOWN ->
                openConditionsMenu(player, prompt.dropId);
            case COMMAND_DROP_ADD -> openAddCustomDropMenu(player, prompt.dropId, prompt.returnPage);
            case COMMAND_ADD, COMMAND_EDIT -> openCommandMenu(player, prompt.dropId, prompt.entryIndex, prompt.page, prompt.returnPage);
            case CONFIG_MAX_PROFILES, CONFIG_MAX_CUSTOM_DROPS, CONFIG_MAX_COMMANDS, CONFIG_MAX_DROP_AMOUNT -> openEditorMenu(player);
        }
    }

    private void startPrompt(Player player, PromptType promptType, String dropId, int entryIndex, int page, int commandIndex, int returnPage) {
        openInputPrompt(player, new ChatPrompt(promptType, dropId, entryIndex, page, commandIndex, returnPage, "", false));
    }

    private void startMobTargetSearchPrompt(Player player, String dropId, int page, String filter) {
        openInputPrompt(player, new ChatPrompt(PromptType.MOB_TARGET_SEARCH, dropId, -1, page, -1, 0, filter, false));
    }

    private void startWorldSearchPrompt(Player player, String dropId, int page, String filter) {
        openInputPrompt(player, new ChatPrompt(PromptType.WORLD_SEARCH, dropId, -1, page, -1, 0, filter, false));
    }

    private void startConfigPrompt(Player player, PromptType promptType) {
        if (promptType == null || !promptType.isConfigSetting()) {
            return;
        }
        openInputPrompt(player, new ChatPrompt(promptType, "", -1, 0, -1, 0, "", false));
    }

    private PromptType promptTypeForSetting(String id) {
        return switch (id) {
            case "max-profiles" -> PromptType.CONFIG_MAX_PROFILES;
            case "max-custom-drops" -> PromptType.CONFIG_MAX_CUSTOM_DROPS;
            case "max-commands" -> PromptType.CONFIG_MAX_COMMANDS;
            case "max-drop-amount" -> PromptType.CONFIG_MAX_DROP_AMOUNT;
            default -> null;
        };
    }

    private void openInputPrompt(Player player, ChatPrompt prompt) {
        ChatPrompt nativePrompt = prompt.withNativeDialog(true);
        NativeDialogSupport nativeDialogs = plugin.detectNativeDialogs();
        DialogService dialogs = DialogServiceFactory.create(
            plugin,
            nativeDialogs,
            fallbackDialogService(nativeDialogs, prompt),
            core.scheduler()
        );

        boolean openedNative = EditorDialogInputs.openTextFromInventory(
            plugin,
            core.inventoryCloseSuppressor(),
            dialogs,
            player,
            buildTextDialogRequest(prompt),
            input -> handlePromptInput(player, nativePrompt, input == null ? "" : input.trim()),
            () -> cancelPrompt(player, nativePrompt)
        );
        if (openedNative) {
            editorSounds.open(player);
        }
    }

    private DialogService fallbackDialogService(NativeDialogSupport nativeDialogs, ChatPrompt prompt) {
        return new FallbackDialogService(
            nativeDialogs,
            (player, request, onClose) -> runIfPresent(onClose),
            (player, request, onConfirm, onCancel) -> runIfPresent(onCancel),
            (player, request, onSubmit, onCancel) -> {
                warnNativeDialogFallback(player, nativeDialogs);
                startChatPrompt(player, prompt);
            }
        );
    }

    private void warnNativeDialogFallback(Player player, NativeDialogSupport nativeDialogs) {
        if (nativeDialogs.configEnabled() && nativeDialogs.warnOnFallback() && warnedFallbackPlayers.add(player.getUniqueId())) {
            plugin.messages().sendConfigured(player, "native-dialogs-fallback");
        }
    }

    private void runIfPresent(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    private void startChatPrompt(Player player, ChatPrompt prompt) {
        chatPrompts.put(player.getUniqueId(), prompt);
        player.closeInventory();
        sendPromptMessage(player, prompt);
    }

    private void cancelPrompt(Player player, ChatPrompt prompt) {
        chatPrompts.remove(player.getUniqueId());
        editorSounds.back(player);
        plugin.messages().sendConfigured(player, "prompt-cancelled");
        reopenAfterPrompt(player, prompt);
    }

    private void rejectPromptInput(Player player, ChatPrompt prompt, String messageKey, String... placeholders) {
        editorSounds.error(player);
        plugin.messages().sendConfigured(player, messageKey, placeholders);
        if (prompt.nativeDialog) {
            chatPrompts.remove(player.getUniqueId());
            reopenAfterPrompt(player, prompt);
        }
    }

    private void sendPromptMessage(Player player, ChatPrompt prompt) {
        if (prompt.type == PromptType.AMOUNT) {
            plugin.messages().sendConfigured(player, "prompt-amount", "{max}", String.valueOf(plugin.getMaxDropAmount()));
            return;
        }
        if (prompt.type.isConfigSetting()) {
            plugin.messages().sendConfigured(player, promptMessageKey(prompt.type),
                "{min}", String.valueOf(configMin(prompt.type)),
                "{max}", String.valueOf(configMax(prompt.type)));
            return;
        }
        plugin.messages().sendConfigured(player, promptMessageKey(prompt.type));
    }

    private String promptMessageKey(PromptType type) {
        return switch (type) {
            case MOB_TARGET_SEARCH -> "prompt-mob-search";
            case WORLD_SEARCH -> "prompt-world-search";
            case MAIN_SEARCH, CUSTOM_SEARCH, COMMAND_SEARCH -> "prompt-entry-search";
            case ADD_DROP -> "prompt-add-drop";
            case RENAME -> "prompt-rename";
            case PRIORITY -> "prompt-priority";
            case CHANCE -> "prompt-chance";
            case AMOUNT -> "prompt-amount";
            case CONDITION_TARGETS -> "prompt-targets";
            case CONDITION_WORLDS -> "prompt-worlds";
            case CONDITION_BIOMES -> "prompt-biomes";
            case CONDITION_PERMISSION -> "prompt-permission";
            case CONDITION_TOOLS -> "prompt-tools";
            case CONDITION_FORTUNE -> "prompt-fortune";
            case CONDITION_TIME -> "prompt-time";
            case CONDITION_WEATHER -> "prompt-weather";
            case CONDITION_COOLDOWN -> "prompt-cooldown";
            case COMMAND_DROP_ADD -> "prompt-command-drop-add";
            case COMMAND_ADD -> "prompt-command-add";
            case COMMAND_EDIT -> "prompt-command-edit";
            case CONFIG_MAX_PROFILES -> "prompt-max-profiles";
            case CONFIG_MAX_CUSTOM_DROPS -> "prompt-max-custom-drops";
            case CONFIG_MAX_COMMANDS -> "prompt-max-commands";
            case CONFIG_MAX_DROP_AMOUNT -> "prompt-max-drop-amount";
        };
    }

    private TextDialogRequest buildTextDialogRequest(ChatPrompt prompt) {
        DropDefinition definition = dropStore.getDrop(prompt.dropId);
        CustomDropEntry entry = promptEntryOrNull(definition, prompt);

        return new TextDialogRequest(
            prompt.type == PromptType.MOB_TARGET_SEARCH || prompt.type == PromptType.WORLD_SEARCH
                || prompt.type == PromptType.MAIN_SEARCH || prompt.type == PromptType.CUSTOM_SEARCH || prompt.type == PromptType.COMMAND_SEARCH
                ? "Search" : promptTitle(prompt.type),
            List.of(renderPromptText(promptBody(prompt.type), prompt)),
            promptFieldLabel(prompt.type),
            currentPromptValue(definition, entry, prompt),
            promptPlaceholder(prompt.type),
            submitButton(prompt.type),
            cancelButton(prompt.type),
            bodyWidth(prompt.type),
            inputWidth(prompt.type),
            maxLength(prompt.type),
            true,
            false,
            false
        );
    }

    private String renderPromptText(String text, ChatPrompt prompt) {
        if (prompt.type.isConfigSetting()) {
            return text
                .replace("{min}", String.valueOf(configMin(prompt.type)))
                .replace("{max}", String.valueOf(configMax(prompt.type)));
        }
        return text.replace("{max}", String.valueOf(plugin.getMaxDropAmount()));
    }

    private DialogButton submitButton(PromptType type) {
        return switch (type) {
            case MOB_TARGET_SEARCH, WORLD_SEARCH, MAIN_SEARCH, CUSTOM_SEARCH, COMMAND_SEARCH -> DialogButton.search("Search", "", 100);
            default -> DialogButton.save("Save", "", 100);
        };
    }

    private DialogButton cancelButton(PromptType type) {
        return type == PromptType.MOB_TARGET_SEARCH || type == PromptType.WORLD_SEARCH
            || type == PromptType.MAIN_SEARCH || type == PromptType.CUSTOM_SEARCH || type == PromptType.COMMAND_SEARCH
            ? DialogButton.cancel("Back", "", 100)
            : DialogButton.cancel("Cancel", "", 100);
    }

    private String promptPlaceholder(PromptType type) {
        return switch (type) {
            case MOB_TARGET_SEARCH -> "zombie";
            case WORLD_SEARCH -> "world";
            case MAIN_SEARCH, CUSTOM_SEARCH, COMMAND_SEARCH -> "diamond";
            case ADD_DROP -> "Bonus Diamonds";
            case RENAME -> "Bonus Diamonds";
            case PRIORITY -> "0";
            case CHANCE -> "50";
            case AMOUNT -> "1-3";
            case CONDITION_TARGETS -> "stone, deepslate";
            case CONDITION_WORLDS -> "world, world_nether";
            case CONDITION_BIOMES -> "plains, forest";
            case CONDITION_PERMISSION -> "fodrops.vip";
            case CONDITION_TOOLS -> "diamond_pickaxe, tag:pickaxe";
            case CONDITION_FORTUNE -> "3";
            case CONDITION_TIME -> "1000-12000";
            case CONDITION_WEATHER -> "clear, rain";
            case CONDITION_COOLDOWN -> "5000";
            case COMMAND_DROP_ADD, COMMAND_ADD -> "console give {player} diamond 1";
            case COMMAND_EDIT -> "give {player} diamond 1";
            case CONFIG_MAX_PROFILES -> "100";
            case CONFIG_MAX_CUSTOM_DROPS -> "64";
            case CONFIG_MAX_COMMANDS -> "8";
            case CONFIG_MAX_DROP_AMOUNT -> "64";
        };
    }

    private int inputWidth(PromptType type) {
        return switch (type) {
            case MOB_TARGET_SEARCH, WORLD_SEARCH, MAIN_SEARCH, CUSTOM_SEARCH, COMMAND_SEARCH -> 280;
            case PRIORITY, CHANCE, AMOUNT, CONDITION_FORTUNE, CONDITION_TIME, CONDITION_COOLDOWN,
                CONFIG_MAX_PROFILES, CONFIG_MAX_CUSTOM_DROPS, CONFIG_MAX_COMMANDS, CONFIG_MAX_DROP_AMOUNT -> 220;
            case COMMAND_DROP_ADD, COMMAND_ADD, COMMAND_EDIT -> 360;
            default -> 300;
        };
    }

    private int bodyWidth(PromptType type) {
        return switch (type) {
            case COMMAND_DROP_ADD, COMMAND_ADD, COMMAND_EDIT -> 360;
            default -> 300;
        };
    }

    private int maxLength(PromptType type) {
        return switch (type) {
            case MOB_TARGET_SEARCH, WORLD_SEARCH, MAIN_SEARCH, CUSTOM_SEARCH, COMMAND_SEARCH -> 80;
            case PRIORITY, CHANCE, AMOUNT, CONDITION_FORTUNE, CONDITION_TIME, CONDITION_COOLDOWN,
                CONFIG_MAX_PROFILES, CONFIG_MAX_CUSTOM_DROPS, CONFIG_MAX_COMMANDS, CONFIG_MAX_DROP_AMOUNT -> 64;
            default -> 256;
        };
    }

    private String promptTitle(PromptType type) {
        return switch (type) {
            case MOB_TARGET_SEARCH -> "Search Mob Targets";
            case WORLD_SEARCH -> "Search Worlds";
            case MAIN_SEARCH, CUSTOM_SEARCH, COMMAND_SEARCH -> "Search Entries";
            case ADD_DROP -> "Name Drop";
            case RENAME -> "Rename Drop";
            case PRIORITY -> "Set Priority";
            case CHANCE -> "Set Drop Chance";
            case AMOUNT -> "Set Amount Range";
            case CONDITION_TARGETS -> "Set Targets";
            case CONDITION_WORLDS -> "Set Worlds";
            case CONDITION_BIOMES -> "Set Biomes";
            case CONDITION_PERMISSION -> "Set Permission";
            case CONDITION_TOOLS -> "Set Tools";
            case CONDITION_FORTUNE -> "Set Fortune Minimum";
            case CONDITION_TIME -> "Set Time Range";
            case CONDITION_WEATHER -> "Set Weather";
            case CONDITION_COOLDOWN -> "Set Cooldown";
            case COMMAND_DROP_ADD -> "Add Command Reward";
            case COMMAND_ADD -> "Add Command";
            case COMMAND_EDIT -> "Edit Command";
            case CONFIG_MAX_PROFILES -> "Set Max Profiles";
            case CONFIG_MAX_CUSTOM_DROPS -> "Set Max Custom Drops Per Profile";
            case CONFIG_MAX_COMMANDS -> "Set Max Commands";
            case CONFIG_MAX_DROP_AMOUNT -> "Set Max Drop Amount";
        };
    }

    private String promptBody(PromptType type) {
        return switch (type) {
            case MOB_TARGET_SEARCH -> "Enter a mob search term. Leave empty to show all mobs.";
            case WORLD_SEARCH -> "Enter a world search term. Leave empty to show all worlds.";
            case MAIN_SEARCH, CUSTOM_SEARCH, COMMAND_SEARCH -> "Enter an entry search term. Leave empty to show all entries.";
            case ADD_DROP -> "Enter the initial drop profile name.";
            case RENAME -> "Enter the profile display name.";
            case PRIORITY -> "Enter priority as an integer.";
            case CHANCE -> "Enter chance from 0 to 100.";
            case AMOUNT -> "Enter a single amount or min-max. Max: {max}.";
            case CONDITION_TARGETS -> "Enter target keys separated by commas, or none.";
            case CONDITION_WORLDS -> "Enter worlds separated by commas, or none.";
            case CONDITION_BIOMES -> "Enter biome names separated by commas, or none.";
            case CONDITION_PERMISSION -> "Enter a permission node, or none.";
            case CONDITION_TOOLS -> "Enter tools or tag:pickaxe values separated by commas, or none.";
            case CONDITION_FORTUNE -> "Enter minimum Fortune level, 0 or higher.";
            case CONDITION_TIME -> "Enter min-max from 0 to 23999, or none.";
            case CONDITION_WEATHER -> "Enter clear, rain, thunder separated by commas, or none.";
            case CONDITION_COOLDOWN -> "Enter cooldown in milliseconds. Use 0 to disable.";
            case COMMAND_DROP_ADD, COMMAND_ADD -> "Use format: <console|player> <command>.";
            case COMMAND_EDIT -> "Enter the command text.";
            case CONFIG_MAX_PROFILES, CONFIG_MAX_CUSTOM_DROPS, CONFIG_MAX_COMMANDS, CONFIG_MAX_DROP_AMOUNT ->
                "Enter a whole number from {min} to {max}.";
        };
    }

    private String promptFieldLabel(PromptType type) {
        return switch (type) {
            case MOB_TARGET_SEARCH, WORLD_SEARCH, MAIN_SEARCH, CUSTOM_SEARCH, COMMAND_SEARCH -> "Search";
            case ADD_DROP, RENAME -> "Name";
            case PRIORITY -> "Priority";
            case CHANCE -> "Chance";
            case AMOUNT -> "Amount";
            case CONDITION_TARGETS -> "Targets";
            case CONDITION_WORLDS -> "Worlds";
            case CONDITION_BIOMES -> "Biomes";
            case CONDITION_PERMISSION -> "Permission";
            case CONDITION_TOOLS -> "Tools";
            case CONDITION_FORTUNE -> "Fortune";
            case CONDITION_TIME -> "Time";
            case CONDITION_WEATHER -> "Weather";
            case CONDITION_COOLDOWN -> "Cooldown";
            case COMMAND_DROP_ADD, COMMAND_ADD, COMMAND_EDIT -> "Command";
            case CONFIG_MAX_PROFILES, CONFIG_MAX_CUSTOM_DROPS, CONFIG_MAX_COMMANDS, CONFIG_MAX_DROP_AMOUNT -> "Value";
        };
    }

    private String currentPromptValue(DropDefinition definition, CustomDropEntry entry, ChatPrompt prompt) {
        if (prompt.type.isConfigSetting()) {
            ConfigEditorButton button = configButton(prompt.type);
            return button == null ? "" : String.valueOf(button.integerValue());
        }
        if (prompt.type == PromptType.ADD_DROP) {
            return "";
        }
        if (definition == null) {
            return "";
        }

        DropConditions conditions = definition.getConditions();
        return switch (prompt.type) {
            case MOB_TARGET_SEARCH -> prompt.filter;
            case WORLD_SEARCH -> prompt.filter;
            case MAIN_SEARCH, CUSTOM_SEARCH, COMMAND_SEARCH -> prompt.filter;
            case ADD_DROP -> "";
            case RENAME -> definition.getName();
            case PRIORITY -> String.valueOf(definition.getPriority());
            case CHANCE -> entry == null ? "" : trimChance(entry.getChance());
            case AMOUNT -> entry == null ? "" : entry.getMinAmount() + "-" + entry.getMaxAmount();
            case CONDITION_TARGETS -> joinOrNone(conditions.getTargets());
            case CONDITION_WORLDS -> joinOrNone(conditions.getWorlds());
            case CONDITION_BIOMES -> joinOrNone(conditions.getBiomes());
            case CONDITION_PERMISSION -> conditions.getPermission().isBlank() ? "none" : conditions.getPermission();
            case CONDITION_TOOLS -> joinOrNone(conditions.getTools());
            case CONDITION_FORTUNE -> String.valueOf(conditions.getFortuneMin());
            case CONDITION_TIME -> formatTimeRange(conditions);
            case CONDITION_WEATHER -> conditions.getWeather().isEmpty()
                ? "none"
                : conditions.getWeather().stream().map(weather -> weather.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
            case CONDITION_COOLDOWN -> String.valueOf(conditions.getCooldownMs());
            case COMMAND_DROP_ADD, COMMAND_ADD -> "";
            case COMMAND_EDIT -> currentCommandValue(entry, prompt.commandIndex);
            case CONFIG_MAX_PROFILES, CONFIG_MAX_CUSTOM_DROPS, CONFIG_MAX_COMMANDS, CONFIG_MAX_DROP_AMOUNT -> "";
        };
    }

    private CustomDropEntry promptEntryOrNull(DropDefinition definition, ChatPrompt prompt) {
        if (definition == null || prompt.entryIndex < 0 || prompt.entryIndex >= definition.getCustomDrops().size()) {
            return null;
        }
        return definition.getCustomDrops().get(prompt.entryIndex);
    }

    private String currentCommandValue(CustomDropEntry entry, int commandIndex) {
        if (entry == null || commandIndex < 0 || commandIndex >= entry.getCommands().size()) {
            return "";
        }
        return entry.getCommands().get(commandIndex).getCommand();
    }

    private String joinOrNone(List<String> values) {
        if (values.isEmpty()) {
            return "none";
        }
        return String.join(", ", values);
    }

    private int[] parseAmountRange(String raw, int maxAllowed) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }

        int limit = Math.max(1, maxAllowed);
        try {
            if (value.contains("-")) {
                String[] split = value.split("-", 2);
                if (split.length != 2) {
                    return null;
                }
                int min = Integer.parseInt(split[0].trim());
                int max = Integer.parseInt(split[1].trim());
                if (min <= 0 || max <= 0 || max < min || min > limit || max > limit) {
                    return null;
                }
                return new int[]{min, max};
            }

            int single = Integer.parseInt(value);
            if (single <= 0 || single > limit) {
                return null;
            }
            return new int[]{single, single};
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int[] parseTimeRange(String raw) {
        String value = raw.trim();
        String[] split = value.split("-", 2);
        if (split.length != 2) {
            return null;
        }

        try {
            int min = Integer.parseInt(split[0].trim());
            int max = Integer.parseInt(split[1].trim());
            if (min < 0 || max < 0 || min > 23999 || max > 23999) {
                return null;
            }
            return new int[]{min, max};
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<String> parseSimpleList(String input, boolean lowercase, boolean uppercase) {
        if (input.equalsIgnoreCase("none")) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (String part : input.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (lowercase) {
                values.add(trimmed.toLowerCase(Locale.ROOT));
            } else if (uppercase) {
                values.add(trimmed.toUpperCase(Locale.ROOT));
            } else {
                values.add(trimmed);
            }
        }
        return values;
    }

    private List<String> parseToolList(String input) {
        if (input.equalsIgnoreCase("none")) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (String part : input.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("tag:")) {
                values.add(trimmed.toLowerCase(Locale.ROOT));
            } else {
                values.add(trimmed.toUpperCase(Locale.ROOT));
            }
        }
        return values;
    }

    private List<String> parseTargetList(String input) {
        if (input.equalsIgnoreCase("none")) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (String part : input.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            values.add(trimmed.toUpperCase(Locale.ROOT));
        }
        return values;
    }

    private List<DropWeather> parseWeather(String input) {
        if (input.equalsIgnoreCase("none")) {
            return List.of();
        }

        List<DropWeather> weather = new ArrayList<>();
        for (String part : input.split(",")) {
            DropWeather value = DropWeather.fromConfig(part.trim());
            if (value == null) {
                return null;
            }
            if (!weather.contains(value)) {
                weather.add(value);
            }
        }
        return weather;
    }

    private DropCommandAction parseCommandWithSender(String input) {
        String raw = input.trim();
        int separator = raw.indexOf(' ');
        if (separator <= 0 || separator >= raw.length() - 1) {
            return null;
        }

        String senderRaw = raw.substring(0, separator).trim();
        String commandLine = raw.substring(separator + 1).trim();
        if (commandLine.isEmpty()) {
            return null;
        }

        DropCommandSenderType senderType = DropCommandSenderType.fromConfig(senderRaw);
        if (!senderRaw.equalsIgnoreCase("console") && !senderRaw.equalsIgnoreCase("player")) {
            return null;
        }

        return new DropCommandAction(senderType, commandLine);
    }

    private Integer parseInteger(String input) {
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parseLong(String input) {
        try {
            return Long.parseLong(input.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double parseDouble(String input) {
        try {
            return Double.parseDouble(input.trim().replace(",", "."));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String formatTimeRange(DropConditions conditions) {
        if (conditions.getTimeMin() == null || conditions.getTimeMax() == null) {
            return "none";
        }
        return conditions.getTimeMin() + "-" + conditions.getTimeMax();
    }

    private String formatWeather(List<DropWeather> weather) {
        if (weather.isEmpty()) {
            return "{muted}None";
        }
        return "{theme}" + weather.stream().map(DropWeather::getDisplayName).collect(Collectors.joining(", "));
    }

    private String formatList(List<String> values, int max) {
        if (values.isEmpty()) {
            return "{muted}None";
        }
        List<String> preview = values.stream().limit(max).collect(Collectors.toList());
        String text = String.join(", ", preview);
        if (values.size() > max) {
            text += " +" + (values.size() - max);
        }
        return "{theme}" + text;
    }

    private void addCommandRewardLore(List<String> lore, List<DropCommandAction> commands) {
        if (commands.isEmpty()) {
            lore.add(plugin.messages().renderTemplate("{white}Command Reward: {muted}None"));
            return;
        }

        lore.add(plugin.messages().renderTemplate("{white}Command Reward:"));
        int shown = Math.min(commands.size(), 3);
        for (int i = 0; i < shown; i++) {
            DropCommandAction action = commands.get(i);
            lore.add(plugin.messages().renderTemplate(
                "{muted}- {theme}" + action.getSenderType().getDisplayName() + "{muted}: {white}" + action.getCommand()
            ));
        }
        if (commands.size() > shown) {
            lore.add(plugin.messages().renderTemplate("{muted}+ " + (commands.size() - shown) + " more"));
        }
    }

    private void fillBackground(Inventory inventory) {
        ItemStack filler = EditorItemFactory.filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private void fillEmptyDropSlots(Inventory inventory, int startIndex) {
        ItemStack filler = EditorItemFactory.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = startIndex; i < GRID_SLOTS.length; i++) {
            inventory.setItem(GRID_SLOTS[i], filler);
        }
    }

    private ItemStack button(Player player, Material material, String name, String... lore) {
        String renderedName = plugin.messages().renderTemplate(name);
        String label = FoText.plain(renderedName).trim();
        String normalizedLabel = label.toLowerCase(Locale.ROOT);
        boolean enabled = normalizedLabel.endsWith(": on") || normalizedLabel.endsWith(" on")
                || normalizedLabel.endsWith(": enabled") || normalizedLabel.endsWith(" enabled");
        boolean disabled = normalizedLabel.endsWith(": off") || normalizedLabel.endsWith(" off")
                || normalizedLabel.endsWith(": disabled") || normalizedLabel.endsWith(" disabled");
        if (enabled || disabled) {
            int separator = label.lastIndexOf(':');
            label = label.substring(0, separator >= 0 ? separator : label.lastIndexOf(' ')).trim();
        }

        String color = renderedName.contains(FoStyle.BAD) ? FoStyle.BAD
                : renderedName.contains(FoStyle.GOOD) ? FoStyle.GOOD : FoStyle.THEME;
        List<String> information = new ArrayList<>();
        if (lore != null) {
            for (String line : lore) {
                information.add(plugin.messages().renderTemplate(line));
            }
        }
        if ((enabled || disabled) && information.stream().noneMatch(line ->
                FoText.plain(line).trim().toLowerCase(Locale.ROOT).startsWith("state:"))) {
            information.add(0, "State: " + (enabled ? FoStyle.GOOD + "ON" : FoStyle.BAD + "OFF"));
        }
        return EditorItemFactory.button(player, material, color, label, information, "interact");
    }

    private String getGuiTitle(String title, String... placeholders) {
        return GuiTitles.format(plugin.messages().renderTemplate(title, placeholders));
    }

    private String trimChance(double chance) {
        if (Math.floor(chance) == chance) {
            return Integer.toString((int) chance);
        }
        return String.format(Locale.US, "%.2f", chance);
    }

    private String formatDecimal(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private String formatDeliveryMode(DropDeliveryMode mode) {
        if (mode == DropDeliveryMode.INVENTORY) {
            return "{good}Inventory";
        }
        return "{theme}Ground";
    }

    private String[] buildSilkTouchLore(SilkTouchMode selectedMode) {
        List<String> lines = new ArrayList<>();
        lines.add("{white}Click to cycle mode.");
        lines.add("");
        lines.add("{white}Modes:");

        for (SilkTouchMode mode : SilkTouchMode.values()) {
            if (mode == selectedMode) {
                lines.add("{theme}» " + mode.getDisplayName());
            } else {
                lines.add("{white}• " + mode.getDisplayName());
            }
        }

        return lines.toArray(new String[0]);
    }

    private String[] buildEventTypeLore(DropDefinition definition) {
        List<String> lines = new ArrayList<>();
        lines.add("{white}Click to cycle event type.");
        lines.add("");
        lines.add("{white}Events:");

        for (me.foesio.foDrops.drop.DropEventType type : me.foesio.foDrops.drop.DropEventType.values()) {
            if (type == definition.getEventType()) {
                lines.add("{theme}» " + type.getDisplayName());
            } else {
                lines.add("{white}• " + type.getDisplayName());
            }
        }

        return lines.toArray(new String[0]);
    }

    private enum MenuType {
        MAIN,
        DROP,
        DELETE_CONFIRM,
        CONDITIONS,
        CUSTOM,
        CUSTOM_ENTRY,
        CUSTOM_DELETE_CONFIRM,
        ADD_CUSTOM_DROP,
        COMMANDS
    }

    private enum PromptType {
        MOB_TARGET_SEARCH,
        WORLD_SEARCH,
        MAIN_SEARCH,
        CUSTOM_SEARCH,
        COMMAND_SEARCH,
        ADD_DROP,
        RENAME,
        PRIORITY,
        CHANCE,
        AMOUNT,
        CONDITION_TARGETS,
        CONDITION_WORLDS,
        CONDITION_BIOMES,
        CONDITION_PERMISSION,
        CONDITION_TOOLS,
        CONDITION_FORTUNE,
        CONDITION_TIME,
        CONDITION_WEATHER,
        CONDITION_COOLDOWN,
        COMMAND_DROP_ADD,
        COMMAND_ADD,
        COMMAND_EDIT,
        CONFIG_MAX_PROFILES,
        CONFIG_MAX_CUSTOM_DROPS,
        CONFIG_MAX_COMMANDS,
        CONFIG_MAX_DROP_AMOUNT;

        private boolean isConfigSetting() {
            return switch (this) {
                case CONFIG_MAX_PROFILES, CONFIG_MAX_CUSTOM_DROPS, CONFIG_MAX_COMMANDS, CONFIG_MAX_DROP_AMOUNT -> true;
                default -> false;
            };
        }
    }

    private static final class ChatPrompt {
        private final PromptType type;
        private final String dropId;
        private final int entryIndex;
        private final int page;
        private final int commandIndex;
        private final int returnPage;
        private final String filter;
        private final boolean nativeDialog;

        private ChatPrompt(PromptType type, String dropId, int entryIndex, int page, int commandIndex, int returnPage, String filter, boolean nativeDialog) {
            this.type = type;
            this.dropId = dropId;
            this.entryIndex = entryIndex;
            this.page = page;
            this.commandIndex = commandIndex;
            this.returnPage = returnPage;
            this.filter = filter == null ? "" : filter;
            this.nativeDialog = nativeDialog;
        }

        private ChatPrompt withNativeDialog(boolean nativeDialog) {
            return new ChatPrompt(type, dropId, entryIndex, page, commandIndex, returnPage, filter, nativeDialog);
        }
    }

    private record MainBrowserContext() {
    }

    private record CustomBrowserContext(String dropId) {
    }

    private record CommandBrowserContext(String dropId, int entryIndex, int returnPage) {
    }

    private static final class EditorMenu implements InventoryHolder {
        private final MenuType type;
        private final String dropId;
        private final int page;
        private final int selectedIndex;
        private final int returnPage;
        private final Map<Integer, String> dropBySlot = new HashMap<>();
        private final Map<Integer, Integer> entryBySlot = new HashMap<>();
        private final Map<Integer, Integer> commandBySlot = new HashMap<>();
        private Inventory inventory;

        private EditorMenu(MenuType type, String dropId, int page, int selectedIndex, int returnPage) {
            this.type = type;
            this.dropId = dropId;
            this.page = page;
            this.selectedIndex = selectedIndex;
            this.returnPage = returnPage;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
