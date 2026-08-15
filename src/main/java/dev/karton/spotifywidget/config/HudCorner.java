package dev.karton.spotifywidget.config;

/** Screen corner the HUD is anchored to. */
public enum HudCorner {
    TOP_LEFT("top_left"),
    TOP_RIGHT("top_right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_RIGHT("bottom_right");

    private final String id;

    HudCorner(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean left() {
        return this == TOP_LEFT || this == BOTTOM_LEFT;
    }

    public boolean top() {
        return this == TOP_LEFT || this == TOP_RIGHT;
    }

    public String label() {
        return switch (this) {
            case TOP_LEFT -> "Top left";
            case TOP_RIGHT -> "Top right";
            case BOTTOM_LEFT -> "Bottom left";
            case BOTTOM_RIGHT -> "Bottom right";
        };
    }

    public HudCorner next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static HudCorner byId(String id, HudCorner fallback) {
        for (HudCorner corner : values()) {
            if (corner.id.equals(id)) return corner;
        }
        return fallback;
    }
}
