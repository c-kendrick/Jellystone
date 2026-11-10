import java.util.Comparator;

public class Arson extends IgnitionSource {

    private final int row;
    private final int col;

    public Arson(int row, int col) {
        super(0.55, 0.5);
        this.row = row;
        this.col = col;
    }

    @Override
    public void ignite(Forest forest) {
        Cell[][] grid = forest.getGrid();

        if (row < 0 || row >= grid.length
                || col < 0 || col >= grid[row].length) {
            throw new IllegalArgumentException(
                    "Arson coordinates must be inside the forest");
        }

        // Cell currently stores its row in x and its column in y.
        // Prefer the requested cell, otherwise the nearest suitable tree.
        Cell target = availableTrees(forest)
                .filter(cell -> cell.getTree().getSpreadability() >= spreadability)
                .min(Comparator.comparingInt(cell ->
                        Math.abs(cell.getX() - row)
                                + Math.abs(cell.getY() - col)))
                .orElseThrow(() -> new IllegalStateException(
                        "Arson found no unburnt living tree with spreadability >= "
                                + spreadability));

        target.getTree().ignite(severity);
    }
}
