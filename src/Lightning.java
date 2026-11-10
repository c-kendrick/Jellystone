import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class Lightning extends IgnitionSource {

    private final Random random;
    private final Set<Cell> struckCells = new HashSet<>();
    private Cell lastStruckCell;

    public Lightning() {
        this(new Random());
    }

    public Lightning(Random random) {
        super(0.8, 0.7);
        this.random = java.util.Objects.requireNonNull(random, "Random must not be null");
    }

    public Cell getLastStruckCell() {
        return lastStruckCell;
    }

    @Override
    public void ignite(Forest forest) {
        // Choose randomly from valid targets instead of trying random
        // coordinates, which can repeatedly miss trees in a sparse forest.
        List<Cell> candidates = availableTrees(forest)
                .filter(cell -> !struckCells.contains(cell))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "Lightning found no unburnt living tree it has not struck before");
        }

        Cell target = candidates.get(random.nextInt(candidates.size()));
        igniteTarget(target);
    }

    public void igniteAt(Forest forest, Position position) {
        Cell[][] grid = forest.getGrid();
        if (position.row() < 0 || position.row() >= grid.length
                || position.col() < 0 || position.col() >= grid[position.row()].length) {
            throw new IllegalArgumentException("Lightning target must be inside the forest");
        }
        Cell target = grid[position.row()][position.col()];
        if (!canIgnite(target)) throw new IllegalStateException("Lightning needs an unburnt living tree");
        igniteTarget(target);
    }

    private void igniteTarget(Cell target) {
        target.getTree().ignite(severity);
        target.setHeat(0.0);
        target.setRainExposure(0.0);
        target.protectIgnition(SimulationRules.LIGHTNING_VISIBLE_SECONDS);
        lastStruckCell = target;
        struckCells.add(target);
    }
}
