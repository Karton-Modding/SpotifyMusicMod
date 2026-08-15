package dev.karton.spotifywidget.config;

/** How long the HUD stays on screen. */
public enum DisplayMode {
    /** Always visible while something is playing. */
    ALWAYS("always", -1),
    /** Visible for 15 seconds around the configured triggers. */
    SECONDS_15("15s", 15),
    /** Visible for 5 seconds around the configured triggers. */
    SECONDS_5("5s", 5);

    private final String id;
    private final int seconds;

    DisplayMode(String id, int seconds) {
        this.id = id;
        this.seconds = seconds;
    }

    public String id() {
        return id;
    }

    /** Visible duration in seconds, or -1 when permanent. */
    public int seconds() {
        return seconds;
    }

    public boolean timed() {
        return seconds > 0;
    }

    public String label() {
        return switch (this) {
            case ALWAYS -> "Always on";
            case SECONDS_15 -> "15 seconds";
            case SECONDS_5 -> "5 seconds";
        };
    }

    public DisplayMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static DisplayMode byId(String id, DisplayMode fallback) {
        for (DisplayMode mode : values()) {
            if (mode.id.equals(id)) return mode;
        }
        return fallback;
    }
}
