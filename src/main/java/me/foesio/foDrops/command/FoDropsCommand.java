package me.foesio.foDrops.command;

import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foDrops.FoDrops;
import me.foesio.foDrops.drop.DropStore;
import me.foesio.foDrops.editor.EditorManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class FoDropsCommand implements CommandExecutor, TabCompleter {
    private final FoDrops plugin;
    private final DropStore dropStore;
    private final EditorManager editorManager;
    private final UpdateNoticeService updateNotices;

    public FoDropsCommand(FoDrops plugin, DropStore dropStore, EditorManager editorManager, UpdateNoticeService updateNotices) {
        this.plugin = plugin;
        this.dropStore = dropStore;
        this.editorManager = editorManager;
        this.updateNotices = updateNotices;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fodrops.admin")) {
            plugin.messages().sendConfigured(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            plugin.messages().sendConfigured(sender, "help-header");
            plugin.messages().sendConfigured(sender, "help-version");
            plugin.messages().sendConfigured(sender, "help-editor");
            plugin.messages().sendConfigured(sender, "help-reload");
            plugin.messages().sendConfigured(sender, "help-count", "{count}", String.valueOf(dropStore.getAllDrops().size()));
            return true;
        }

        String subcommand = args[0].toLowerCase();
        if (subcommand.equals("version")) {
            plugin.messages().sendConfigured(sender, "version", "{version}", plugin.getDescription().getVersion());
            updateNotices.checkAndSendVersion(sender);
            return true;
        }

        if (subcommand.equals("editor")) {
            if (!(sender instanceof Player player)) {
                plugin.messages().sendConfigured(sender, "players-only");
                return true;
            }

            editorManager.openEditorMenu(player);
            return true;
        }

        if (subcommand.equals("reload")) {
            FoReloadResult result = plugin.reloadPluginData(dropStore);
            if (!result.successful()) {
                plugin.getLogger().warning("Reload failed at " + result.failedStep() + ": " + result.errorMessage());
                plugin.messages().sendConfigured(sender, "reload-failed", "{step}", result.failedStep(), "{error}", result.errorMessage());
                return true;
            }
            plugin.messages().sendConfigured(sender, "reloaded", "{count}", String.valueOf(dropStore.getAllDrops().size()));
            return true;
        }

        plugin.messages().sendConfigured(sender, "unknown-subcommand", "{arg}", args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            if ("version".startsWith(args[0].toLowerCase())) {
                suggestions.add("version");
            }
            if ("editor".startsWith(args[0].toLowerCase())) {
                suggestions.add("editor");
            }
            if ("reload".startsWith(args[0].toLowerCase())) {
                suggestions.add("reload");
            }
        }
        return suggestions;
    }
}
