package me.foesio.foDrops.api;

import me.foesio.foDrops.drop.DropContext;
import me.foesio.foDrops.drop.DropEngine;
import me.foesio.foDrops.drop.DropEventType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class FoDropsApiImpl implements FoDropsApi {
    private final DropEngine engine;

    public FoDropsApiImpl(DropEngine engine) {
        this.engine = engine;
    }

    @Override
    public boolean matchesBlockProfileTag(Player player, Block block, ItemStack tool, String tag) {
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
}
