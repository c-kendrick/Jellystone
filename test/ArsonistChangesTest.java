import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.swing.JLabel;

public class ArsonistChangesTest {
    public static void main(String[] args) throws Exception {
        try (WorldSimulation world = Main.createSimulation(42, false)) {
            world.start(WorldSimulation.Startup.ARSONIST);
            List<Actor> people = world.getActors().stream()
                    .filter(actor -> actor.kind() == Actor.Kind.ARSONIST).toList();
            check(people.size() == 3, "The world must contain three arsonists");
            Set<Position> positions = new HashSet<>();
            for (Actor actor : people) {
                check(actor.isAlive(), "An arsonist must not spawn in an existing fire");
                check(positions.add(actor.getPosition()), "Arsonists must start in different locations");
                Arsonist person = (Arsonist) actor;
                check(person.getAttacks() == 1, "Each arsonist should make one immediate attack");
                check(person.getNextAttack() >= 5 && person.getNextAttack() <= 10,
                        "Each arsonist needs an independent 5–10 second deadline");
            }
            check(world.view().world().fires().size() == 3,
                    "Three initial attacks should ignite exactly three trees, not twelve");
            for (int sample = 0; sample < 100; sample++) {
                double interval = world.nextArsonInterval();
                check(interval >= 5 && interval <= 10, "Attack interval must remain within 5–10 seconds");
            }
            JLabel status = new JLabel(), events = new JLabel();
            Method labels = Main.class.getDeclaredMethod("updateLabels", JLabel.class, JLabel.class, WorldView.class);
            labels.setAccessible(true); labels.invoke(null, status, events, world.view());
            check(status.getText().startsWith("Fire:") && status.getText().contains("Trees:")
                    && status.getText().contains("Animals:"), "World statistics should remain visible");
            check(!status.getText().matches(".*\\d{2}:\\d{2}.*"), "The displayed clock must be removed");
        }
        Arsonist walker = new Arsonist(999, new Position(0, 0));
        int moves = 0;
        for (int step = 0; step < 16; step++) {
            walker.prepareMovement(0.25);
            if (walker.view().readyToMove()) walker.applyMove(new Position(0, ++moves));
        }
        check(moves == 6, "Movement must remain 1.5 cells per simulation second");
        Forest forest = new Forest(12, 12, new RandomForestGenerator(), new Random(1));
        for (Cell[] row : forest.getGrid()) for (Cell cell : row) cell.plantTree(new Tree(0.9));
        try (WorldSimulation world = new WorldSimulation(forest, 42, false)) {
            check(world.tryArson(new Arsonist(1, new Position(5, 5))), "The first attack should succeed");
            check(world.view().world().fires().size() == 1, "An attack must light exactly one tree");
            check(world.tryArson(new Arsonist(2, new Position(1, 1))), "The second attack should succeed");
            check(world.view().world().fires().size() == 2, "Two separate attacks should light two trees");
        }
        System.out.println("PASS: three separated arsonists, one tree per attack, 5–10s deadlines, unchanged movement, clock removed");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
