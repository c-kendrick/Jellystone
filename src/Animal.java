public final class Animal extends Actor {
    private final AnimalSpecies species;
    public Animal(int id, Position position, AnimalSpecies species) {
        super(id, position); this.species = species;
    }
    @Override public Kind kind() { return Kind.ANIMAL; }
    @Override protected double speed() { return species.speed; }
    @Override public View view() {
        return new View(id, kind(), position, goal, species, alive,
                readyToMove(), false, 0.0, "Roaming / fleeing", null, ScoredMovement.FLEE);
    }
}
