package me.foesio.foDrops.drop;

public enum DropEventType {
    BLOCK_BREAK("Block Break"),
    MOB_KILL("Mob Kill"),
    BLOCK_DROP_ITEM("Block Drop Item"),
    PLAYER_FISH("Player Fish"),
    BLOCK_EXPLOSION("Block Exploded"),
    ENTITY_EXPLOSION("Entity Exploded"),
    ENTITY_DROP_ITEM("Entity Drop Item"),
    PLAYER_SHEAR_ENTITY("Player Shear Entity"),
    PLAYER_CRAFT("Player Craft");

    private final String displayName;

    DropEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DropEventType next() {
        DropEventType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static DropEventType fromConfig(String raw) {
        if (raw == null) {
            return BLOCK_BREAK;
        }

        for (DropEventType value : values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return BLOCK_BREAK;
    }
}
