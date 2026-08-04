package me.foesio.foDrops.drop;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class DropContext {
    private final DropEventType eventType;
    private final Player player;
    private final Location location;
    private final World world;
    private final Biome biome;
    private final ItemStack tool;
    private final int silkTouchLevel;
    private final int fortuneLevel;
    private final String targetTypeKey;

    public DropContext(
        DropEventType eventType,
        Player player,
        Location location,
        World world,
        Biome biome,
        ItemStack tool,
        int silkTouchLevel,
        int fortuneLevel,
        String targetTypeKey
    ) {
        this.eventType = eventType;
        this.player = player;
        this.location = location;
        this.world = world;
        this.biome = biome;
        this.tool = tool;
        this.silkTouchLevel = silkTouchLevel;
        this.fortuneLevel = fortuneLevel;
        this.targetTypeKey = targetTypeKey;
    }

    public DropEventType getEventType() {
        return eventType;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getLocation() {
        return location;
    }

    public World getWorld() {
        return world;
    }

    public Biome getBiome() {
        return biome;
    }

    public ItemStack getTool() {
        return tool;
    }

    public int getSilkTouchLevel() {
        return silkTouchLevel;
    }

    public int getFortuneLevel() {
        return fortuneLevel;
    }

    public String getTargetTypeKey() {
        return targetTypeKey;
    }
}
