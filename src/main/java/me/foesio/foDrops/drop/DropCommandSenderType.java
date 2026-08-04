package me.foesio.foDrops.drop;

public enum DropCommandSenderType {
    CONSOLE("Console"),
    PLAYER("Player");

    private final String displayName;

    DropCommandSenderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DropCommandSenderType next() {
        DropCommandSenderType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static DropCommandSenderType fromConfig(String raw) {
        if (raw == null) {
            return CONSOLE;
        }

        for (DropCommandSenderType value : values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return CONSOLE;
    }
}
