/** State objects control when the person travels and when he attempts ignition. */
public final class Arsonist extends Actor {
    public interface State {
        void update(Arsonist actor, WorldSimulation simulation);
        String name();
    }
    private static final class Travelling implements State {
        @Override public void update(Arsonist actor, WorldSimulation simulation) {
            if (simulation.getTime() >= actor.nextAttack) {
                actor.state = new Lighting();
                actor.state.update(actor, simulation);
            }
        }
        @Override public String name() { return "Travelling / avoiding fire"; }
    }
    private static final class Lighting implements State {
        @Override public void update(Arsonist actor, WorldSimulation simulation) {
            if (simulation.tryArson(actor)) {
                actor.attacks++;
                actor.nextAttack = simulation.getTime() + simulation.nextArsonInterval();
                actor.goal = simulation.chooseArsonGoal(actor.position);
                actor.state = new Travelling();
            }
        }
        @Override public String name() { return "Seeking a safe ignition position"; }
    }

    private State state = new Lighting();
    private double nextAttack;
    private int attacks;
    public Arsonist(int id, Position position) { super(id, position); }
    public void act(WorldSimulation simulation) {
        if (alive) state.update(this, simulation);
    }
    public int getAttacks() { return attacks; }
    public double getNextAttack() { return nextAttack; }
    @Override public Kind kind() { return Kind.ARSONIST; }
    @Override protected double speed() { return 1.5; }
    @Override public View view() {
        return new View(id, kind(), position, goal, null, alive, readyToMove(),
                false, 0.0, state.name(), null, ScoredMovement.AVOID_FIRE);
    }
}
