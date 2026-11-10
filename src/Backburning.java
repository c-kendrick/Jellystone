import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Backburning extends IgnitionSource {

    private static final double WAVELENGTH = 3.0;
    private final int row;

    public Backburning(int row) {
        super(0.7, 0.4);
        this.row = row;
    }

    @Override
    public void ignite(Forest forest) {
        Cell[][] grid = forest.getGrid();

        if (row < 0 || row >= grid.length) {
            throw new IllegalArgumentException(
                    "Backburning row must be inside the forest");
        }

        // If the requested row has no suitable trees, use the nearest
        // row that does. Keep the controlled burn within a single row.
        int selectedRow = IntStream.range(0, grid.length)
                .boxed()
                .filter(candidate -> Arrays.stream(grid[candidate])
                        .anyMatch(this::canIgnite))
                .min(Comparator.comparingInt(candidate -> Math.abs(candidate - row)))
                .orElseThrow(() -> new IllegalStateException(
                        "Backburning found no unburnt living trees"));

        List<Cell> candidates = Arrays.stream(grid[selectedRow])
                .filter(this::canIgnite)
                .collect(Collectors.toList());

        double frequency = 2 * Math.PI / WAVELENGTH;
        boolean startedFire = false;

        for (int col = 0; col < grid[selectedRow].length; col++) {
            Cell cell = grid[selectedRow][col];
            double wave = (Math.sin(col * frequency) + 1) / 2;

            if (canIgnite(cell) && wave < spreadability) {
                cell.getTree().ignite(severity);
                startedFire = true;
            }
        }

        // Sparse or very short rows may contain no trees at the wave
        // troughs. Start one fire so the controlled burn still begins.
        if (!startedFire) {
            candidates.get(0).getTree().ignite(severity);
        }
    }
}

