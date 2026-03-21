package dev.shared.do_gamer.module.simple_galaxy_gate.config;

public final class Defaults {
    private Defaults() {
        // Prevent instantiation
    }

    public static final double MAP_CENTER_X = 10_000.0;
    public static final double MAP_CENTER_Y = 6_000.0;
    public static final double TOLERANCE_DISTANCE = 4_000.0; // Distance from center to kill NPCs safely
    public static final double KAMIKAZE_SHIFT_X = -1_500.0;
    public static final double KAMIKAZE_SHIFT_Y = -1_000.0;
    public static final double REPAIR_RADIUS = 2_000.0;
    public static final double FAR_TARGET_DISTANCE = 2_000.0;
    public static final double PREFER_TARGET_DISTANCE_OFFSET = 100.0;
}
