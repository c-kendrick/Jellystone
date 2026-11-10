import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A read-only copy used by worker calculations and the renderer. */
public final class WorldSnapshot {
    private final int rows;
    private final int columns;
    private final Biome[] biomes;
    private final TreeSpecies[] species;
    private final double[] health, maximumHealth, intensity, spreadability, heat, moisture;
    private final boolean[] burning, scorched, firebreak;
    private final int[] fireDistance, nearestFire;
    private final List<Position> fires;

    public WorldSnapshot(Forest forest) {
        Cell[][] grid = forest.getGrid();
        rows = grid.length;
        columns = grid[0].length;
        int size = rows * columns;
        biomes = new Biome[size]; species = new TreeSpecies[size];
        health = new double[size]; maximumHealth = new double[size];
        intensity = new double[size]; spreadability = new double[size];
        heat = new double[size]; moisture = new double[size];
        burning = new boolean[size]; scorched = new boolean[size];
        firebreak = new boolean[size]; fireDistance = new int[size]; nearestFire = new int[size];
        Arrays.fill(fireDistance, Integer.MAX_VALUE);
        Arrays.fill(nearestFire, -1);
        int[] queue = new int[size];
        int head = 0, tail = 0;
        List<Position> fireCells = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int index = index(row, col);
                Cell cell = grid[row][col];
                biomes[index] = cell.getBiome();
                heat[index] = cell.getHeat(); moisture[index] = cell.getMoisture();
                scorched[index] = cell.isScorched(); firebreak[index] = cell.isFirebreak();
                if (cell.hasTree() && cell.getTree().isAlive()) {
                    Tree tree = cell.getTree();
                    species[index] = TreeSpecies.fromTree(tree);
                    health[index] = tree.getHealth(); maximumHealth[index] = tree.getMaxHealth();
                    intensity[index] = tree.getBurnIntensity();
                    spreadability[index] = tree.getSpreadability();
                    burning[index] = tree.isBurning();
                    if (burning[index]) {
                        fireCells.add(new Position(row, col));
                        fireDistance[index] = 0; nearestFire[index] = index;
                        queue[tail++] = index;
                    }
                }
            }
        }
        // Multi-source breadth-first search gives every actor a cheap danger
        // lookup and a direction towards its nearest fire. Workers only read it.
        while (head < tail) {
            int current = queue[head++];
            int row = current / columns, col = current % columns;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int nextRow = row + dr, nextCol = col + dc;
                    if (!contains(nextRow, nextCol)) continue;
                    int next = index(nextRow, nextCol);
                    if (fireDistance[next] != Integer.MAX_VALUE) continue;
                    fireDistance[next] = fireDistance[current] + 1;
                    nearestFire[next] = nearestFire[current];
                    queue[tail++] = next;
                }
            }
        }
        fires = List.copyOf(fireCells);
    }

    public int rows() { return rows; }
    public int columns() { return columns; }
    public int size() { return rows * columns; }
    public int index(int row, int col) { return row * columns + col; }
    public Position position(int index) { return new Position(index / columns, index % columns); }
    public boolean contains(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < columns;
    }
    public boolean contains(Position p) { return contains(p.row(), p.col()); }
    public Biome biome(int index) { return biomes[index]; }
    public TreeSpecies species(int index) { return species[index]; }
    public boolean hasTree(int index) { return species[index] != null; }
    public double health(int index) { return health[index]; }
    public double maximumHealth(int index) { return maximumHealth[index]; }
    public double intensity(int index) { return intensity[index]; }
    public double spreadability(int index) { return spreadability[index]; }
    public double heat(int index) { return heat[index]; }
    public double moisture(int index) { return moisture[index]; }
    public boolean burning(int index) { return burning[index]; }
    public boolean scorched(int index) { return scorched[index]; }
    public boolean firebreak(int index) { return firebreak[index]; }
    public int fireDistance(Position p) { return fireDistance[index(p.row(), p.col())]; }
    public Position nearestFire(Position p) {
        int fire = nearestFire[index(p.row(), p.col())];
        return fire < 0 ? null : position(fire);
    }
    public List<Position> fires() { return fires; }
    public List<Position> neighbours(Position p) {
        List<Position> result = new ArrayList<>(9);
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                Position next = new Position(p.row() + dr, p.col() + dc);
                if (contains(next)) result.add(next);
            }
        }
        return result;
    }
}
