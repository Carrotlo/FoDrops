package me.foesio.foDrops.drop;

public enum DropDeliveryMode {
    GROUND("Ground"),
    INVENTORY("Inventory");

    private final String displayName;

    DropDeliveryMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DropDeliveryMode next() {
        DropDeliveryMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static DropDeliveryMode fromConfig(String raw) {
        if (raw == null) {
            return GROUND;
        }

        for (DropDeliveryMode value : values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return GROUND;
    }
}
