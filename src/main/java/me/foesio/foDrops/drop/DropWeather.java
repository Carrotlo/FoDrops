package me.foesio.foDrops.drop;

public enum DropWeather {
    CLEAR("Clear"),
    RAIN("Rain"),
    THUNDER("Thunder");

    private final String displayName;

    DropWeather(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static DropWeather fromConfig(String raw) {
        if (raw == null) {
            return null;
        }

        for (DropWeather value : values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return null;
    }
}
