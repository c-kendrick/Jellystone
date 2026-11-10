import java.util.Arrays;
import java.util.stream.Stream;

public abstract class IgnitionSource {

    protected double severity;
    protected double spreadability;

    public IgnitionSource(
            double severity,
            double spreadability) {

        validateValue("Severity", severity);
        validateValue("Spreadability", spreadability);

        this.severity = severity;
        this.spreadability = spreadability;
    }

    private void validateValue(String name, double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be between 0.0 and 1.0"
            );
        }
    }

    public abstract void ignite(Forest forest);

    protected boolean canIgnite(Cell cell) {
        return cell.hasTree()
                && cell.getTree().isAlive()
                && !cell.getTree().isBurning();
    }

    protected Stream<Cell> availableTrees(Forest forest) {
        return Arrays.stream(forest.getGrid())
                .flatMap(Arrays::stream)
                .filter(this::canIgnite);
    }
}
