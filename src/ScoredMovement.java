import java.util.Comparator;

/** Reuses movement selection while accepting different scoring behaviour. */
public final class ScoredMovement implements MovementStrategy {
    @FunctionalInterface
    public interface CellScore {
        double score(Actor.View actor, Position candidate, WorldSnapshot world);
    }

    private final CellScore scorer;
    public ScoredMovement(CellScore scorer) { this.scorer = scorer; }

    @Override
    public Position choosePosition(Actor.View actor, WorldSnapshot world) {
        if (!actor.readyToMove() || actor.recovering()) return actor.position();
        return world.neighbours(actor.position()).stream()
                .filter(p -> actor.kind() == Actor.Kind.SPIRIT
                        || !world.burning(world.index(p.row(), p.col())))
                .max(Comparator.comparingDouble(p -> scorer.score(actor, p, world)))
                .orElse(actor.position());
    }

    public static final MovementStrategy FLEE = new ScoredMovement((actor, p, world) -> {
        int index = world.index(p.row(), p.col());
        double safety = Math.min(12, world.fireDistance(p));
        double preference = actor.animalSpecies().prefers(world.biome(index)) ? 2.0 : 0.0;
        if (world.firebreak(index) && actor.animalSpecies() == AnimalSpecies.COW) preference = 2.0;
        return safety * 5.0 + preference - world.heat(index) * 12.0
                - p.distanceTo(actor.goal()) * 0.15;
    });

    public static final MovementStrategy SEEK_FIRE = new ScoredMovement((actor, p, world) -> {
        if (world.fires().isEmpty()
                || world.fireDistance(actor.position()) > SimulationRules.SPIRIT_AWARENESS_RADIUS) {
            return -p.distanceTo(actor.goal());
        }
        return -world.fireDistance(p) * 10.0 - p.distanceTo(actor.goal()) * 0.01;
    });

    public static final MovementStrategy AVOID_FIRE = new ScoredMovement((actor, p, world) -> {
        int distance = world.fireDistance(p);
        double danger = distance < 5 ? (5 - distance) * 1000.0 : 0.0;
        int index = world.index(p.row(), p.col());
        return -danger - world.heat(index) * 500.0 - p.distanceTo(actor.goal());
    });
}
