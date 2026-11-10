import java.util.Comparator;

public final class ForestSpirit extends Actor {
    private double energy = 100.0;
    private boolean recovering;
    private double cooldown;
    private Position effectTarget;
    private double effectRemaining;

    public ForestSpirit(int id, Position position) { super(id, position); }
    @Override public Kind kind() { return Kind.SPIRIT; }
    @Override protected double speed() { return recovering ? 0.0 : 3.0; }
    @Override public void kill() { /* Spirits are immune to fire. */ }
    public double getEnergy() { return energy; }

    public void act(Forest forest, double seconds) {
        effectRemaining = Math.max(0.0, effectRemaining - seconds);
        if (effectRemaining == 0.0) effectTarget = null;
        cooldown = Math.max(0.0, cooldown - seconds);
        if (energy < 25.0) recovering = true;
        if (recovering) {
            energy = Math.min(100.0, energy + seconds * 5.0);
            if (energy >= 95.0) recovering = false;
            return;
        }
        if (cooldown > 0.0) return;
        forest.livingTrees()
                .filter(cell -> cell.getTree().isBurning())
                .filter(cell -> !cell.isIgnitionProtected())
                .filter(cell -> Math.abs(cell.getX() - position.row()) <= 1
                        && Math.abs(cell.getY() - position.col()) <= 1)
                .min(Comparator.comparingDouble(cell ->
                        Math.hypot(cell.getX() - position.row(), cell.getY() - position.col())))
                .ifPresent(cell -> {
                    cell.getTree().extinguish();
                    cell.setHeat(0.0);
                    cell.setMoisture(Math.max(0.65, cell.getMoisture()));
                    energy -= 25.0;
                    cooldown = 1.5;
                    effectTarget = new Position(cell.getX(), cell.getY());
                    effectRemaining = 0.8;
                });
    }

    @Override public View view() {
        return new View(id, kind(), position, goal, null, alive, readyToMove(),
                recovering, energy, recovering ? "Recovering" : "Seeking / extinguishing",
                effectTarget, ScoredMovement.SEEK_FIRE);
    }
}
