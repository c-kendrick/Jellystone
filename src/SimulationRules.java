/** Tunable simulation values. All durations are simulation seconds. */
public final class SimulationRules {
    private SimulationRules() { }
    public static final int ROWS = 120;
    public static final int COLUMNS = 180;
    public static final double STEP_SECONDS = 0.25;
    public static final int ARSONIST_COUNT = 3;
    public static final double ARSON_MIN_INTERVAL = 5.0;
    public static final double ARSON_MAX_INTERVAL = 10.0;
    public static final double LIGHTNING_MIN_INTERVAL = 20.0;
    public static final double LIGHTNING_MAX_INTERVAL = 40.0;
    public static final double AMBIENT_LIGHTNING_MIN_INTERVAL = 2.0;
    public static final double AMBIENT_LIGHTNING_MAX_INTERVAL = 5.0;
    public static final double LIGHTNING_VISIBLE_SECONDS = 4.0;
    public static final double FUEL_DAMAGE_PER_SECOND = 1.0;
    public static final double NEIGHBOUR_HEAT_PER_SECOND = 0.24;
    public static final double HEAT_COOLING_PER_SECOND = 0.022;
    public static final double IGNITION_HEAT = 1.0;
    public static final int ANIMAL_COUNT = 110;
    public static final int SPIRIT_COUNT = 5;
    public static final int SPIRIT_AWARENESS_RADIUS = 28;
}
