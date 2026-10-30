package me.foesio.foDrops.api;

import me.foesio.foDrops.FoDrops;
import me.foesio.foDrops.drop.DropContext;
import me.foesio.foDrops.drop.DropEngine;
import me.foesio.foDrops.drop.DropEventType;
import me.foesio.foDrops.drop.DropStore;
import me.foesio.foDrops.drop.DropDefinition;
import me.foesio.foDrops.drop.CustomDropEntry;
import me.foesio.foDrops.drop.DropEvaluation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

public final class FoDropsApiImpl implements FoDropsApi {
    private final FoDrops plugin;
    private final DropEngine engine;
    private final DropStore store;
    private boolean triggering;

    public FoDropsApiImpl(FoDrops plugin, DropEngine engine, DropStore store) {
        this.plugin = plugin;
        this.engine = engine;
        this.store = store;
    }

    @Override
    public boolean matchesBlockProfileTag(Player player, Block block, ItemStack tool, String tag) {
        requirePrimaryThread();
        if (!plugin.isEnabled()) {
            return false;
        }
        if (block == null || tag == null || tag.isBlank()) {
            return false;
        }
        Location location = block.getLocation().add(0.5D, 0.5D, 0.5D);
        DropContext context = new DropContext(
            DropEventType.BLOCK_BREAK,
            player,
            location,
            block.getWorld(),
            block.getBiome(),
            tool,
            DropEngine.getSilkTouchLevel(tool),
            DropEngine.getFortuneLevel(tool),
            block.getType().name()
        );
        return engine.matchesProfileTag(context, tag);
    }

    @Override
    public List<FoDropProfileSnapshot> getProfiles() {
        requirePrimaryThread();
        if (!plugin.isEnabled()) {
            return List.of();
        }
        return store.getAllDrops().stream().map(FoDropsApiImpl::snapshot).toList();
    }

    @Override
    public Optional<FoDropProfileSnapshot> getProfile(String id) {
        requirePrimaryThread();
        if (!plugin.isEnabled() || id == null || id.isBlank()) {
            return Optional.empty();
        }
        DropDefinition definition = store.getDrop(id);
        return definition == null ? Optional.empty() : Optional.of(snapshot(definition));
    }

    @Override
    public FoDropTriggerResult trigger(FoDropTriggerRequest request) {
        requirePrimaryThread();
        if (request == null || request.eventType() == null || request.location() == null
            || request.location().getWorld() == null) {
            throw new IllegalArgumentException("eventType and a world-backed location are required");
        }
        DropEventType eventType;
        try {
            eventType = DropEventType.valueOf(request.eventType());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown FoDrops event type: " + request.eventType(), exception);
        }
        if (!plugin.isEnabled()) {
            return new FoDropTriggerResult(FoDropTriggerResult.Status.UNAVAILABLE, List.of(), false);
        }
        if (triggering) {
            return new FoDropTriggerResult(FoDropTriggerResult.Status.REENTRANT, List.of(), false);
        }
        triggering = true;
        try {
            Location location = request.location();
            World world = location.getWorld();
            ItemStack tool = request.tool();
            DropContext context = new DropContext(eventType, request.player(), location, world,
                location.getBlock().getBiome(), tool, DropEngine.getSilkTouchLevel(tool),
                DropEngine.getFortuneLevel(tool), request.targetTypeKey(), request.targetPersistentData());
            DropEvaluation evaluation = engine.evaluate(context);
            List<String> matched = evaluation.getMatchedDefinitions().stream().map(DropDefinition::getId).toList();
            if (matched.isEmpty()) {
                return new FoDropTriggerResult(FoDropTriggerResult.Status.NO_MATCH, List.of(), false);
            }
            engine.applyRewards(evaluation, context, null);
            return new FoDropTriggerResult(FoDropTriggerResult.Status.PROCESSED, matched,
                evaluation.isCancelVanillaDrops());
        } finally {
            triggering = false;
        }
    }

    private static FoDropProfileSnapshot snapshot(DropDefinition definition) {
        List<FoDropEntrySnapshot> entries = definition.getCustomDrops().stream().map(FoDropsApiImpl::snapshot).toList();
        return new FoDropProfileSnapshot(definition.getId(), definition.getName(),
            definition.getEventType().name(), definition.getTags(), definition.getPriority(),
            definition.isStopProcessing(), definition.isCancelVanillaDrops(), entries);
    }

    private static FoDropEntrySnapshot snapshot(CustomDropEntry entry) {
        return new FoDropEntrySnapshot(entry.getId(), entry.getTags(), entry.getChance(),
            entry.getMinAmount(), entry.getMaxAmount(), entry.getDeliveryMode().name(),
            entry.isItemReward(), entry.getItem());
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("FoDrops API must be called on the primary server thread");
        }
    }
}
