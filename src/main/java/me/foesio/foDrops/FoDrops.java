package me.foesio.foDrops;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.dialog.NativeDialogSettings;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.message.FoStyle;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foDrops.command.FoDropsCommand;
import me.foesio.foDrops.drop.DropStore;
import me.foesio.foDrops.editor.EditorManager;
import me.foesio.foDrops.listener.DropListener;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class FoDrops extends JavaPlugin {
    private static final String MODRINTH_PROJECT_ID = "F4bHtd5M";
    private static final int BSTATS_PLUGIN_ID = 33115;
    private static final String DEFAULT_PREFIX_TOKEN = "{theme}FoDrops &8» {muted}";
    private static final String HELP_RELOAD_MESSAGE = "{prefix} {theme}/fodrops reload {muted}- reload config.yml, messages.yml, and drops.yml";
    private static final String OLD_HELP_RELOAD_MESSAGE = "{prefix} {theme}/fodrops reload {muted}- reload config.yml, messages.yml, drops.yml, and dialogs/";
    private static final String RELOADED_MESSAGE = "{prefix} {good}Reloaded config.yml, messages.yml, and drops.yml. Loaded {theme}{count} {good}drop profiles.";
    private static final String OLD_RELOADED_MESSAGE = "{prefix} {good}Reloaded config.yml, messages.yml, drops.yml, and dialogs/. Loaded {theme}{count} {good}drop profiles.";
    private static final int HARD_MAX_PROFILES = 500;
    private static final int HARD_MAX_CUSTOM_DROPS_PER_PROFILE = 256;
    private static final int HARD_MAX_COMMANDS_PER_ENTRY = 64;
    private static final int HARD_MAX_DROP_AMOUNT = 4096;

    private FoCoreContext core;
    private UpdateNoticeService updateNotices;
    private DropStore dropStore;
    private FoMessageService messages;
    private EditorManager editorManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        boolean messagesFileExisted = new File(getDataFolder(), "messages.yml").exists();
        messages = FoMessageService.load(this, messageMigrations(messagesFileExisted));
        ensureConfigDefaults();

        core = FoPluginCore.create(this, currentNativeDialogSettings());
        core.warnIfNativeDialogsUnavailable();
        core.metrics(BSTATS_PLUGIN_ID);
        updateNotices = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID).start();

        dropStore = new DropStore(this);
        dropStore.load();

        editorManager = new EditorManager(this, dropStore, core);
        getServer().getPluginManager().registerEvents(editorManager, this);
        getServer().getPluginManager().registerEvents(new DropListener(this, dropStore, core), this);

        FoDropsCommand command = new FoDropsCommand(this, dropStore, editorManager, updateNotices);
        if (getCommand("fodrops") != null) {
            getCommand("fodrops").setExecutor(command);
            getCommand("fodrops").setTabCompleter(command);
        } else {
            getLogger().severe("Command 'fodrops' is missing from plugin.yml.");
        }
    }

    @Override
    public void onDisable() {
        if (dropStore != null) {
            dropStore.save();
        }
        if (editorManager != null) {
            editorManager.close();
            editorManager = null;
        }
        if (core != null) {
            core.close();
            core = null;
        }
        updateNotices = null;
    }

    public FoMessageService messages() {
        return messages;
    }

    public void reloadMessages() {
        if (messages == null) {
            boolean messagesFileExisted = new File(getDataFolder(), "messages.yml").exists();
            messages = FoMessageService.load(this, messageMigrations(messagesFileExisted));
            return;
        }
        messages.reload();
    }

    public FoReloadResult reloadPluginData(DropStore dropStore) {
        return FoReloadRegistry.create()
            .addConfig(this)
            .addMessages(messages)
            .add("config-defaults", this::ensureConfigDefaults)
            .add("dialog-inputs", this::refreshDialogInputState)
            .add("drops", dropStore::load)
            .reload();
    }

    public void reloadDialogInputs() {
        ensureConfigDefaults();
        refreshDialogInputState();
    }

    private void refreshDialogInputState() {
        if (editorManager != null) {
            editorManager.reloadDialogInputs();
        }
    }

    private NativeDialogSettings currentNativeDialogSettings() {
        return NativeDialogSettings.fromConfig(getConfig().getConfigurationSection("native-dialogs"));
    }

    public NativeDialogSupport detectNativeDialogs() {
        return NativeDialogSupport.detect(this, currentNativeDialogSettings());
    }

    public int getMaxProfiles() {
        return getLimit("limits.max-profiles", 100, 1, HARD_MAX_PROFILES);
    }

    public int getMaxCustomDropsPerProfile() {
        return getLimit("limits.max-custom-drops-per-profile", 64, 1, HARD_MAX_CUSTOM_DROPS_PER_PROFILE);
    }

    public int getMaxCommandsPerEntry() {
        return getLimit("limits.max-commands-per-entry", 8, 0, HARD_MAX_COMMANDS_PER_ENTRY);
    }

    public int getMaxDropAmount() {
        return getLimit("limits.max-drop-amount", 64, 1, HARD_MAX_DROP_AMOUNT);
    }

    public boolean shouldBlockOperatorRewardCommands() {
        return getConfig().getBoolean("safety.block-operator-commands", true);
    }

    public boolean shouldBlockGamemodeRewardCommands() {
        return getConfig().getBoolean("safety.block-gamemode-commands", true);
    }

    private int getLimit(String path, int fallback, int min, int max) {
        int value = getConfig().getInt(path, fallback);
        return Math.max(min, Math.min(max, value));
    }

    private void ensureConfigDefaults() {
        getConfig().options().parseComments(true);
        boolean changed = needsNativeDialogDefaults();
        NativeDialogConfigDefaults.addDefaults(getConfig());

        if (getConfig().contains("prefix")) {
            getConfig().set("prefix", null);
            changed = true;
        }
        if (getConfig().contains("messages")) {
            getConfig().set("messages", null);
            changed = true;
        }
        if (getConfig().contains("gui-titles")) {
            getConfig().set("gui-titles", null);
            changed = true;
        }

        boolean legacySafetyValue = getConfig().getBoolean("safety.block-operator-and-gamemode-commands", true);

        if (!getConfig().contains("safety.block-operator-commands")) {
            getConfig().set("safety.block-operator-commands", legacySafetyValue);
            changed = true;
        }
        if (!getConfig().contains("safety.block-gamemode-commands")) {
            getConfig().set("safety.block-gamemode-commands", legacySafetyValue);
            changed = true;
        }
        if (getConfig().contains("safety.block-operator-and-gamemode-commands")) {
            getConfig().set("safety.block-operator-and-gamemode-commands", null);
            changed = true;
        }
        if (changed) {
            saveConfig();
        }
    }

    private boolean needsNativeDialogDefaults() {
        return !getConfig().contains(NativeDialogConfigDefaults.ENABLED_PATH)
            || !getConfig().contains(NativeDialogConfigDefaults.WARN_ON_FALLBACK_PATH)
            || getConfig().getComments("native-dialogs").isEmpty()
            || getConfig().getComments(NativeDialogConfigDefaults.ENABLED_PATH).isEmpty()
            || getConfig().getComments(NativeDialogConfigDefaults.WARN_ON_FALLBACK_PATH).isEmpty();
    }

    private FoMessageMigrations messageMigrations(boolean messagesFileExisted) {
        String legacyPrefix = getConfig().getString("prefix", DEFAULT_PREFIX_TOKEN);
        boolean overrideWithLegacyMessages = !messagesFileExisted && getConfig().isConfigurationSection("messages");
        return FoMessageMigrations.create()
            .add(config -> migrateLegacyConfigMessages(config, overrideWithLegacyMessages))
            .add(config -> migrateLegacyPrefix(config, legacyPrefix))
            .replaceExact("help-reload", OLD_HELP_RELOAD_MESSAGE, HELP_RELOAD_MESSAGE)
            .replaceExact("reloaded", OLD_RELOADED_MESSAGE, RELOADED_MESSAGE)
            .removeExact("version-checking", "{prefix} {muted}Checking Modrinth for updates...")
            .removeExact("version-current", "{prefix} {good}You are running the latest version.")
            .removeExact("version-update-available", "{prefix} {bad}Update available: {theme}{latest} {muted}(current: {current}) {theme}{url}")
            .removeExact("version-check-failed", "{prefix} {bad}Could not check Modrinth for updates right now.")
            .build();
    }

    private boolean migrateLegacyConfigMessages(FileConfiguration messageConfig, boolean overrideWithLegacyMessages) {
        ConfigurationSection oldMessages = getConfig().getConfigurationSection("messages");
        if (oldMessages == null) {
            return false;
        }

        boolean changed = false;
        for (String key : oldMessages.getKeys(true)) {
            if (oldMessages.isConfigurationSection(key)) {
                continue;
            }
            if (!overrideWithLegacyMessages && messageConfig.contains(key)) {
                continue;
            }
            messageConfig.set(key, oldMessages.get(key));
            changed = true;
        }
        return changed;
    }

    private boolean migrateLegacyPrefix(FileConfiguration messageConfig, String legacyPrefix) {
        if (legacyPrefix == null || legacyPrefix.isBlank()) {
            return false;
        }

        String currentPrefix = messageConfig.getString("tokens.prefix");
        if (currentPrefix != null && !isDefaultPrefix(currentPrefix)) {
            return false;
        }
        if (legacyPrefix.equals(currentPrefix)) {
            return false;
        }

        messageConfig.set("tokens.prefix", legacyPrefix);
        return true;
    }

    private boolean isDefaultPrefix(String prefix) {
        return DEFAULT_PREFIX_TOKEN.equals(prefix)
            || FoStyle.defaultPrefixTemplate().equals(prefix)
            || FoStyle.defaultPrefix(getName()).equals(prefix);
    }

}
