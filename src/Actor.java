public abstract class Actor {
    public enum Kind { ANIMAL, SPIRIT, ARSONIST }
    public record View(int id, Kind kind, Position position, Position goal,
                       AnimalSpecies animalSpecies, boolean alive,
                       boolean readyToMove, boolean recovering, double energy,
                       String state, Position effectTarget, MovementStrategy movement) { }
    public record Move(int actorId, Position destination) { }

    protected final int id;
    protected Position position;
    protected Position goal;
    protected boolean alive = true;
    private double movementCredit;

    protected Actor(int id, Position position) {
        this.id = id; this.position = position; this.goal = position;
    }
    public int getId() { return id; }
    public Position getPosition() { return position; }
    public boolean isAlive() { return alive; }
    public void kill() { alive = false; }
    public void setGoal(Position goal) { this.goal = goal; }
    public void prepareMovement(double seconds) {
        movementCredit = Math.min(2.0, movementCredit + speed() * seconds);
    }
    public void applyMove(Position destination) {
        if (alive && movementCredit >= 1.0) {
            position = destination;
            movementCredit -= 1.0;
        }
    }
    protected boolean readyToMove() { return movementCredit >= 1.0; }
    public abstract Kind kind();
    protected abstract double speed();
    public abstract View view();
}
