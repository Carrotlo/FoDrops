package me.foesio.foDrops.drop;

public enum SilkTouchMode {
    IGNORE("Ignore"),
    REQUIRE("Require"),
    FORBID("Forbid");

    private final String displayName;

    SilkTouchMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SilkTouchMode next() {
        SilkTouchMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static SilkTouchMode fromConfig(String raw) {
        if (raw == null) {
            return IGNORE;
        }

        for (SilkTouchMode value : values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return IGNORE;
    }
}
