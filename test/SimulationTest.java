import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class SimulationTest {
    public static void main(String[] args) {
        testRegionsAndBrokenBorders();
        testSlowHeatAndNoJumpAcrossGaps();
        testEmbersCrossGapsButRespectRain();
        testRainAndLightningCadence();
        testSpiritEnergyAndImmunity();
        testMovementAndSnapshotIsolation();
        testMortalityAndNoRegrowth();
        testArsonCadence();
        testParallelMatchesSerial();
        measureDryFireDuration();
        System.out.println("All world simulation tests passed.");
    }

    private static Forest empty(int rows, int columns) {
        Forest forest = new Forest(rows, columns, new RandomForestGenerator(), new Random(1));
        for (Cell[] row : forest.getGrid()) for (Cell cell : row) {
            cell.removeTree(); cell.setMoisture(0.0);
        }
        return forest;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void testRegionsAndBrokenBorders() {
        Forest forest = new Forest(120, 180, new BiomeForestGenerator(), new Random(42));
        Set<Integer> regions = new HashSet<>();
        Set<Biome> biomes = new HashSet<>();
        int firebreaks = 0, touchingBorders = 0;
        for (Cell[] row : forest.getGrid()) for (Cell cell : row) {
            regions.add(cell.getRegionId()); biomes.add(cell.getBiome());
            if (cell.isFirebreak()) {
                firebreaks++;
                check(!cell.hasTree(), "Boundary clearings must not contain trees");
            }
            if (cell.getBiome() == Biome.CLEARING) check(!cell.hasTree(), "Clearing must be empty");
            int r = cell.getX(), c = cell.getY();
            if (c + 1 < 180) {
                Cell neighbour = forest.getGrid()[r][c + 1];
                if (cell.getBiome() != neighbour.getBiome() && !cell.isFirebreak()
                        && !neighbour.isFirebreak()) touchingBorders++;
            }
        }
        check(regions.size() == 72 && biomes.size() == 6, "Expected many small regions of all six biomes");
        check(firebreaks > 0 && touchingBorders > 0, "Need both cleared and touching biome borders");
        System.out.println("PASS: 72 regions, " + firebreaks + " clearing cells, " + touchingBorders + " touching borders");
    }
    private static void testSlowHeatAndNoJumpAcrossGaps() {
        Forest forest = empty(1, 2);
        Tree source = new Tree(0.9), target = new Tree(0.9);
        forest.getGrid()[0][0].plantTree(source); forest.getGrid()[0][1].plantTree(target);
        source.ignite();
        for (int i = 0; i < 4; i++) forest.update();
        check(!target.isBurning() && forest.getGrid()[0][1].getHeat() > 0,
                "Spread must accumulate heat instead of igniting in one step");
        for (int i = 0; i < 40 && !target.isBurning(); i++) forest.update();
        check(target.isBurning(), "Sustained exposure must eventually ignite a dry neighbour");
        check(source.getHealth() > 70, "Fuel should last far longer than a few updates");
        Forest gap = empty(1, 3);
        Tree left = new Tree(0.9), right = new Tree(0.9);
        gap.getGrid()[0][0].plantTree(left); gap.getGrid()[0][2].plantTree(right); left.ignite();
        for (int i = 0; i < 240; i++) gap.update();
        check(!right.isBurning() && gap.getGrid()[0][2].getHeat() == 0,
                "Neighbour spread must not jump across an empty cell without embers");
        System.out.println("PASS: slow heat accumulation, slow fuel loss, gaps block ordinary spread");
    }
    private static void testEmbersCrossGapsButRespectRain() {
        Forest forest = empty(1, 4);
        Tree target = new Tree(0.9); forest.getGrid()[0][2].plantTree(target);
        Ember ember = new Ember(0.5, 0.5, 0.0, 2.0, 1.0);
        check(ember.advance(forest, 0.5), "Ember should remain visibly airborne");
        check(!target.isBurning(), "Ember must not ignite its destination before landing");
        check(!ember.advance(forest, 0.5) && target.isBurning(), "Ember should ignite across the gap on landing");
        target.extinguish(); forest.getGrid()[0][2].setMoisture(0.9);
        new Ember(0.5, 0.5, 0, 2, 1).advance(forest, 1);
        check(!target.isBurning(), "A wet landing area should prevent ember ignition");
        Forest clearing = empty(1, 4);
        new Ember(0.5, 0.5, 0, 2, 1).advance(clearing, 1);
        check(!clearing.getGrid()[0][2].hasTree(), "Embers cannot create fuel in a clearing");
        System.out.println("PASS: visible ember travel, dry landing ignition, wet/empty landing protection");
    }
    private static void testRainAndLightningCadence() {
        Forest forest = empty(1, 1); forest.getGrid()[0][0].plantTree(new Tree(0.9));
        WeatherSystem weather = new WeatherSystem(new Random(42));
        check(weather.strike(forest), "Lightning should ignite a tree immediately");
        check(weather.storms().size() == 1 && weather.flashes().size() == 1,
                "Fire-starting lightning needs a storm and visible flash");
        for (int step = 1; step <= 48; step++) {
            double time = step * 0.25;
            weather.advance(forest, time, 0.25);
            forest.advanceEnvironment(weather, time, 0.25);
            if (time < SimulationRules.LIGHTNING_VISIBLE_SECONDS) {
                check(forest.getGrid()[0][0].getTree().isBurning(),
                        "A lightning fire must remain visible before heavy rain suppresses it");
            }
        }
        check(!forest.getGrid()[0][0].getTree().isBurning(), "Heavy central rain should eventually suppress fire");
        Forest empty = empty(1, 1);
        WeatherSystem ongoing = new WeatherSystem(new Random(2));
        double previous = 0;
        int count = 0;
        for (int step = 1; step <= 800; step++) {
            double time = step * 0.25;
            ongoing.advance(empty, time, 0.25);
            if (ongoing.getStormCount() != count) {
                double interval = time - previous;
                check(interval >= 20 && interval <= 40.00001, "Global weather interval outside 20–40 seconds");
                previous = time; count = ongoing.getStormCount();
            }
        }
        check(count >= 5 && ongoing.getGustCount() > 0, "Weather must continue after the forest is empty");
        check(ongoing.getLightningFireCount() == 0, "An empty forest cannot manufacture new fire fuel");
        System.out.println("PASS: immediate visible ignition, rain suppression and recurring global 20–40s weather");
    }
    private static void testSpiritEnergyAndImmunity() {
        Forest forest = empty(3, 3);
        for (Cell[] row : forest.getGrid()) for (Cell cell : row) {
            Tree tree = new Tree(0.9); tree.ignite(); cell.plantTree(tree);
        }
        ForestSpirit spirit = new ForestSpirit(1, new Position(1, 1));
        for (int i = 0; i < 4; i++) spirit.act(forest, 1.5);
        check(spirit.getEnergy() == 0.0, "Extinguishing should consume finite energy");
        spirit.act(forest, 1.0);
        check(spirit.view().recovering(), "An exhausted spirit must enter recovery");
        spirit.act(forest, 20.0);
        check(spirit.getEnergy() > 90 && !spirit.view().recovering(), "Recovery should restore energy");
        spirit.kill(); check(spirit.isAlive(), "Fire must not kill spirits");
        System.out.println("PASS: spirit extinguishing, energy exhaustion, recovery and immunity");
    }
    private static void testMortalityAndNoRegrowth() {
        Forest forest = empty(20, 20);
        // Provide some fuel for startup, then deliberately expose one animal and the arsonist.
        for (Cell[] row : forest.getGrid()) for (Cell cell : row) cell.plantTree(new Tree(0.9));
        try (WorldSimulation simulation = new WorldSimulation(forest, 9, false)) {
            simulation.start(WorldSimulation.Startup.ARSONIST);
            Actor animal = simulation.getActors().stream().filter(actor -> actor.kind() == Actor.Kind.ANIMAL).findFirst().orElseThrow();
            Actor human = simulation.getActors().stream().filter(actor -> actor.kind() == Actor.Kind.ARSONIST).findFirst().orElseThrow();
            for (Actor actor : new Actor[]{animal, human}) {
                Position p = actor.getPosition(); Tree tree = new Tree(0.9); tree.ignite();
                forest.getGrid()[p.row()][p.col()].plantTree(tree);
            }
            int population = simulation.getActors().size();
            simulation.step(0.25);
            check(!animal.isAlive() && !human.isAlive(), "Animals and arsonist should die on contact with fire");
            for (int i = 0; i < 40; i++) simulation.step(0.25);
            check(!animal.isAlive() && simulation.getActors().size() == population, "Dead actors must not respawn");
        }
        Forest barren = empty(2, 2);
        Tree doomed = new Tree(0.9); doomed.kill(); barren.getGrid()[0][0].plantTree(doomed);
        try (WorldSimulation simulation = new WorldSimulation(barren, 3, false)) {
            for (int i = 0; i < 500; i++) simulation.step(0.25);
            check(barren.livingTrees().count() == 0, "An exhausted forest must not regrow");
            check(barren.getGrid()[0][0].isScorched(), "Burnt ground should persist");
        }
        System.out.println("PASS: contact deaths, no respawn, no regrowth and persistent burnt ground");
    }
    private static void testMovementAndSnapshotIsolation() {
        Forest forest = empty(7, 7);
        Tree fire = new Tree(0.9); fire.ignite(); forest.getGrid()[2][3].plantTree(fire);
        WorldSnapshot snapshot = new WorldSnapshot(forest);
        Animal animal = new Animal(1, new Position(3, 3), AnimalSpecies.DEER);
        animal.setGoal(new Position(2, 3)); animal.prepareMovement(1);
        Position escape = animal.view().movement().choosePosition(animal.view(), snapshot);
        check(snapshot.fireDistance(escape) > snapshot.fireDistance(animal.getPosition()),
                "Animals should flee fire even when their roaming goal points at it");
        Arsonist person = new Arsonist(2, new Position(3, 3));
        person.setGoal(new Position(2, 3)); person.prepareMovement(1);
        Position retreat = person.view().movement().choosePosition(person.view(), snapshot);
        check(snapshot.fireDistance(retreat) > snapshot.fireDistance(person.getPosition()),
                "Arsonist should prioritise fire avoidance over reaching his target");
        ForestSpirit spirit = new ForestSpirit(3, new Position(5, 3)); spirit.prepareMovement(1);
        Position approach = spirit.view().movement().choosePosition(spirit.view(), snapshot);
        check(snapshot.fireDistance(approach) < snapshot.fireDistance(spirit.getPosition()),
                "Spirits should flow towards a nearby fire");
        double health = snapshot.health(snapshot.index(2, 3));
        fire.damage(20); fire.extinguish();
        check(snapshot.burning(snapshot.index(2, 3)) && snapshot.health(snapshot.index(2, 3)) == health,
                "Live mutations must not change a previously published snapshot");
        Animal rabbit = new Animal(4, new Position(0, 0), AnimalSpecies.RABBIT);
        int moves = 0;
        for (int step = 0; step < 4; step++) {
            rabbit.prepareMovement(0.25);
            if (rabbit.view().readyToMove()) {
                rabbit.applyMove(new Position(0, ++moves));
            }
        }
        check(moves == 3, "Fractional movement credit must preserve three rabbit moves per second");
        System.out.println("PASS: fleeing, cautious arson movement, fire seeking, snapshot isolation and movement rates");
    }
    private static void testArsonCadence() {
        Forest forest = empty(80, 100);
        for (Cell[] row : forest.getGrid()) for (Cell cell : row) cell.plantTree(new Tree(0.9));
        try (WorldSimulation simulation = new WorldSimulation(forest, 42, false)) {
            simulation.start(WorldSimulation.Startup.ARSONIST);
            Arsonist human = (Arsonist) simulation.getActors().stream()
                    .filter(actor -> actor instanceof Arsonist).findFirst().orElseThrow();
            check(human.getAttacks() == 1, "First arson attack should be immediate");
            for (int attack = 1; attack <= 3; attack++) {
                double deadline = human.getNextAttack();
                double interval = deadline - simulation.getTime();
                check(interval >= 4.75 && interval <= 10.0, "Arson deadline must be drawn from 5–10 seconds");
                while (simulation.getTime() + 0.25 < deadline) simulation.step(0.25);
                check(human.getAttacks() == attack, "Arsonist must not attack before his deadline");
                simulation.step(0.25);
                check(human.isAlive() && human.getAttacks() == attack + 1,
                        "Arsonist should safely repeat an attack when the global interval elapses");
            }
        }
        System.out.println("PASS: three immediate arson attacks followed by random 5–10 second intervals per person");
    }
    private static void testParallelMatchesSerial() {
        try (WorldSimulation serial = Main.createSimulation(77, false);
             WorldSimulation parallel = Main.createSimulation(77, true)) {
            serial.start(WorldSimulation.Startup.ARSONIST);
            parallel.start(WorldSimulation.Startup.ARSONIST);
            for (int step = 0; step < 360; step++) {
                serial.step(0.25); parallel.step(0.25);
                if (step % 60 != 0 && step != 359) continue;
                WorldView a = serial.view(), b = parallel.view();
                check(a.arsonAttacks() == b.arsonAttacks() && a.actors().equals(b.actors()),
                        "Parallel movement/state differs from serial execution");
                check(a.embers().equals(b.embers()) && a.storms().equals(b.storms()),
                        "Parallel execution changed weather or ember randomness");
                for (int index = 0; index < a.world().size(); index++) {
                    check(a.world().burning(index) == b.world().burning(index)
                            && a.world().health(index) == b.world().health(index)
                            && a.world().heat(index) == b.world().heat(index)
                            && a.world().moisture(index) == b.world().moisture(index),
                            "Parallel fire result differs from serial execution");
                }
            }
        }
        System.out.println("PASS: serial and parallel state match exactly over 90 simulated seconds");
    }
    private static void measureDryFireDuration() {
        Forest forest = empty(12, 18);
        Random random = new Random(8);
        for (Cell[] row : forest.getGrid()) for (Cell cell : row) {
            cell.setMoisture(0.18);
            if (random.nextDouble() < 0.78) cell.plantTree(new Oak(0.6 + random.nextDouble() * 0.4));
        }
        forest.getGrid()[6][9].plantTree(new Oak(0.9));
        new Arson(6, 9).ignite(forest);
        double time = 0;
        while (time < 600 && forest.livingTrees().anyMatch(cell -> cell.getTree().isBurning())) {
            forest.update(); time += 0.25;
        }
        check(time >= 180 && time <= 240, "Reference dry fire should last 3–4 minutes, observed " + time);
        System.out.printf("PASS: reference dry fire lasted %.1f seconds (%.2f minutes)%n", time, time / 60);
    }
}
