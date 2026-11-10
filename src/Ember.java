/** A visible moving ember; it can cross empty cells but needs a tree to ignite. */
public final class Ember {
    public record View(double row, double col, double previousRow, double previousCol) { }
    private double row, col, previousRow, previousCol;
    private final double rowSpeed, colSpeed;
    private double remaining;

    public Ember(double row, double col, double rowSpeed, double colSpeed, double lifetime) {
        this.row = row; this.col = col;
        previousRow = row; previousCol = col;
        this.rowSpeed = rowSpeed; this.colSpeed = colSpeed;
        remaining = lifetime;
    }
    /** Returns true while airborne. Landing never creates trees in a clearing. */
    public boolean advance(Forest forest, double seconds) {
        previousRow = row; previousCol = col;
        row += rowSpeed * seconds; col += colSpeed * seconds;
        remaining -= seconds;
        if (row < 0.0 || col < 0.0 || row >= forest.getGrid().length
                || col >= forest.getGrid()[0].length) return false;
        if (remaining > 0.0) return true;
        Cell target = forest.getGrid()[(int) row][(int) col];
        if (target.hasTree() && !target.getTree().isBurning()
                && target.getMoisture() < 0.65) {
            target.setHeat(target.getHeat() + 1.3 * (1.0 - target.getMoisture()));
            if (target.getHeat() >= SimulationRules.IGNITION_HEAT) {
                target.getTree().ignite(0.65);
                target.setHeat(0.0);
            }
        }
        return false;
    }
    public View view() { return new View(row, col, previousRow, previousCol); }

    public static double emissionRate(WorldSnapshot world, double gustStrength) {
        double intensity = world.fires().stream().mapToDouble(position ->
                world.intensity(world.index(position.row(), position.col()))).sum();
        return intensity * Math.max(0.0, gustStrength) * 0.08;
    }
}
