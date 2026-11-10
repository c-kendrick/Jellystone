import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class Lightning extends IgnitionSource {

    private final Random random = new Random();
    private final Set<Cell> struckCells = new HashSet<>();
    private Cell lastStruckCell;

    public Lightning() {
        super(0.8, 0.7);
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
        target.getTree().ignite(severity);
        lastStruckCell = target;
        struckCells.add(target);
    }
}
