import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;

public class Forest {
    private final Cell[][] grid;
    private final Random random;
    private Wind wind;
    private final FireModel fireModel = new FireModel();

    public Forest(int rows, int columns) {
        this(rows, columns, new BiomeForestGenerator(), new Random());
    }

    public Forest(int rows, int columns, ForestGenerator generator, Random random) {
        if (rows <= 0 || columns <= 0) {
            throw new IllegalArgumentException("Forest dimensions must be positive");
        }
        this.random = Objects.requireNonNull(random, "Random must not be null");
        grid = Objects.requireNonNull(generator, "Generator must not be null")
                .generate(rows, columns, random);
    }

    public Cell[][] getGrid() { return grid; }
    public Stream<Cell> livingTrees() {
        return Arrays.stream(grid).flatMap(Arrays::stream)
                .filter(cell -> cell.hasTree() && cell.getTree().isAlive());
    }
    public void setWind(Wind wind) {
        this.wind = wind;
        wind.applyWind(this);
    }
    public Wind getWind() { return wind; }

    // Retained for simple standalone uses; WorldSimulation coordinates the
    // complete weather / fire / animal update using the same methods below.
    public void update() {
        WorldSnapshot snapshot = new WorldSnapshot(this);
        double x = wind == null ? 0.0 : switch (wind.getDirection()) {
            case "EAST" -> 1.0; case "WEST" -> -1.0; default -> 0.0;
        };
        double y = wind == null ? 0.0 : switch (wind.getDirection()) {
            case "SOUTH" -> 1.0; case "NORTH" -> -1.0; default -> 0.0;
        };
        applyHeat(fireModel.plan(snapshot, SimulationRules.STEP_SECONDS, x, y,
                wind == null ? 0.0 : wind.getSpeed() / 100.0, false));
        consumeFuel(snapshot, SimulationRules.STEP_SECONDS);
    }

    public void advanceEnvironment(WeatherSystem weather, double time, double seconds) {
        for (Cell[] row : grid) {
            for (Cell cell : row) {
                cell.advanceIgnitionProtection(seconds);
                double rain = weather.rainAt(cell.getX(), cell.getY());
                double evaporation = weather.isDrySpell(time) ? 0.014 : 0.003;
                double baseline = cell.getBiome().getInitialMoisture();
                double moisture = cell.getMoisture();
                // Rain has a spatial moisture profile, rather than gradually
                // saturating even its dry outer edge to 100%.
                if (rain > 0.0) moisture = Math.max(moisture, rain);
                else moisture = Math.max(baseline * 0.4, moisture - evaporation * seconds);
                cell.setMoisture(moisture);
                cell.setRainExposure(rain >= 0.55
                        ? cell.getRainExposure() + rain * seconds
                        : cell.getRainExposure() - seconds);
                if (cell.hasTree() && cell.getTree().isBurning()
                        && !cell.isIgnitionProtected()
                        && cell.getRainExposure() >= 3.0) {
                    cell.getTree().extinguish();
                    cell.setHeat(0.0);
                    cell.setRainExposure(0.0);
                }
            }
        }
    }

    public void applyHeat(List<FireModel.HeatChange> changes) {
        int columns = grid[0].length;
        for (FireModel.HeatChange change : changes) {
            Cell cell = grid[change.index() / columns][change.index() % columns];
            if (!cell.hasTree() || cell.getTree().isBurning()) continue;
            cell.setHeat(change.heat());
            double threshold = SimulationRules.IGNITION_HEAT + cell.getMoisture() * 0.5;
            if (cell.getHeat() >= threshold && cell.getMoisture() < 0.85) {
                cell.getTree().ignite();
                cell.setHeat(0.0);
            }
        }
    }

    public void consumeFuel(WorldSnapshot before, double seconds) {
        for (int index = 0; index < before.size(); index++) {
            Cell cell = grid[index / before.columns()][index % before.columns()];
            if (!cell.hasTree()) continue;
            Tree tree = cell.getTree();
            // A newly ignited tree begins consuming fuel on the next step.
            if (before.burning(index) && tree.isBurning() && !cell.isIgnitionProtected()) {
                tree.advanceBurning(seconds);
            }
            if (!tree.isAlive()) {
                cell.removeTree();
                cell.setScorched(true);
                cell.setHeat(0.0);
            }
        }
    }

    public void display() {
        for (Cell[] row : grid) {
            for (Cell cell : row) {
                System.out.print(!cell.hasTree() ? ". " : cell.getTree().isBurning() ? "X " : "T ");
            }
            System.out.println();
        }
    }
}
