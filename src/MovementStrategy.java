@FunctionalInterface
public interface MovementStrategy {
    Position choosePosition(Actor.View actor, WorldSnapshot world);
}
