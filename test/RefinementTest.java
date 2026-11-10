import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.SwingUtilities;

public class RefinementTest {
    public static void main(String[] args) throws Exception {
        testCombinedStartup();
        testRainGradientAndDryEdgeFire();
        testOneGlobalFireClock();
        testSpiritEffectAndProtection();
        testEmbersScaleWithFire();
        SwingUtilities.invokeAndWait(() -> {
            testCameraBounds();
            testSpiritRenderingWithoutBars();
        });
        System.out.println("All refinement tests passed.");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static Forest empty(int rows, int columns) {
        Forest forest = new Forest(rows, columns, new RandomForestGenerator(), new Random(1));
        for (Cell[] row : forest.getGrid()) for (Cell cell : row) {
            cell.removeTree(); cell.setMoisture(0.0);
        }
        return forest;
    }
    private static void testCombinedStartup() {
        try (WorldSimulation simulation = Main.createSimulation(42, false)) {
            simulation.start();
            WorldView view = simulation.view();
            check(view.arsonAttacks() == SimulationRules.ARSONIST_COUNT && view.lightningFires() == 1,
                    "Both arson and lightning must start immediately without a chooser");
            check(view.world().fires().size() >= SimulationRules.ARSONIST_COUNT + 1, "Both startup fires must already be visible");
            check(view.actors().stream().filter(a -> a.kind() == Actor.Kind.ARSONIST).count() == SimulationRules.ARSONIST_COUNT,
                    "The combined world needs three arsonists");
        }
        System.out.println("PASS: immediate combined arson/lightning startup");
    }
    private static void testRainGradientAndDryEdgeFire() {
        check(WeatherSystem.moistureProfile(0, 10) == 1.0, "Storm centre must be 100% wet");
        double edge = WeatherSystem.moistureProfile(9.6, 10);
        check(edge >= 0.10 && edge <= 0.20, "Storm outer edge must be 10–20% wet");
        check(WeatherSystem.moistureProfile(11, 10) == 0, "Rain cannot exist outside its cloud radius");
        Forest forest = empty(80, 100);
        for (Cell[] row : forest.getGrid()) for (Cell cell : row) cell.plantTree(new Tree(0.9));
        WeatherSystem weather = new WeatherSystem(new Random(5));
        check(weather.strike(forest), "A global fire strike must ignite suitable fuel");
        Position fire = weather.flashes().stream().filter(WeatherSystem.FlashView::startedFire)
                .findFirst().orElseThrow().position();
        check(weather.rainAt(fire.row(), fire.col()) <= 0.20,
                "The new fire must be on the dry edge rather than the wet centre");
        WeatherSystem.StormView storm = weather.storms().get(0);
        Position centre = new Position((int) Math.round(storm.row()), (int) Math.round(storm.col()));
        Cell centralCell = forest.getGrid()[centre.row()][centre.col()];
        centralCell.getTree().ignite();
        for (int step = 1; step <= 48; step++) {
            weather.advance(forest, step * 0.25, 0.25);
            forest.advanceEnvironment(weather, step * 0.25, 0.25);
        }
        check(centralCell.getMoisture() == 1.0 && !centralCell.getTree().isBurning(),
                "Wet centre should saturate and extinguish ordinary fire");
        Cell edgeCell = forest.getGrid()[fire.row()][fire.col()];
        check(edgeCell.getMoisture() <= 0.20 && edgeCell.getTree().isBurning(),
                "Light rain at the edge must not automatically saturate or extinguish a lightning fire");
        System.out.println("PASS: saturated centre, dry edge, central suppression and sustained edge ignition");
    }
    private static void testOneGlobalFireClock() {
        Forest forest = empty(70, 90);
        for (Cell[] row : forest.getGrid()) for (Cell cell : row) cell.plantTree(new Tree(0.9));
        WeatherSystem weather = new WeatherSystem(new Random(7));
        weather.strike(forest);
        int previousCount = 1, maximumClouds = 0;
        double previousTime = 0;
        for (int step = 1; step <= 960; step++) {
            double time = step * 0.25;
            weather.advance(forest, time, 0.25);
            forest.advanceEnvironment(weather, time, 0.25);
            maximumClouds = Math.max(maximumClouds, weather.storms().size());
            for (WeatherSystem.StormView storm : weather.storms()) {
                check(storm.row() >= 0 && storm.row() <= 69 && storm.col() >= 0 && storm.col() <= 89,
                        "Drifting clouds must stay inside the world");
            }
            for (WeatherSystem.FlashView flash : weather.flashes()) {
                check(flash.position().row() >= 0 && flash.position().row() < 70
                        && flash.position().col() >= 0 && flash.position().col() < 90,
                        "Lightning must strike inside the world");
            }
            if (weather.getLightningFireCount() != previousCount) {
                double interval = time - previousTime;
                check(interval >= 20 && interval <= 40.00001,
                        "Fire-producing lightning must use one global 20–40s timer");
                WeatherSystem.FlashView flash = weather.flashes().stream()
                        .filter(f -> f.startedFire() && f.age() == 0).findFirst().orElseThrow();
                check(forest.getGrid()[flash.position().row()][flash.position().col()].getTree().isBurning(),
                        "A fire-producing strike must actually ignite its tree on that update");
                previousCount = weather.getLightningFireCount(); previousTime = time;
            }
        }
        check(maximumClouds > 1, "The global timer test needs overlapping clouds");
        check(weather.getLightningCount() > weather.getLightningFireCount() * 3,
                "Most visible lightning occurrences should not start fires");
        System.out.printf("PASS: one global clock with %d overlapping clouds; %d strikes, %d fires%n",
                maximumClouds, weather.getLightningCount(), weather.getLightningFireCount());
    }
    private static void testSpiritEffectAndProtection() {
        Forest forest = empty(3, 3);
        Tree tree = new Tree(0.9); tree.ignite(); forest.getGrid()[1][2].plantTree(tree);
        ForestSpirit spirit = new ForestSpirit(1, new Position(1, 1));
        spirit.act(forest, 0.25);
        check(!tree.isBurning() && new Position(1, 2).equals(spirit.view().effectTarget()),
                "Spirit action must publish the actual affected tree for its tendril");
        spirit.act(forest, 1.0);
        check(spirit.view().effectTarget() == null, "An idle spirit must stop showing its tendril");
        new Lightning().igniteAt(forest, new Position(1, 2));
        ForestSpirit another = new ForestSpirit(2, new Position(1, 1));
        another.act(forest, 0.25);
        check(tree.isBurning() && another.getEnergy() == 100.0,
                "Spirits must not erase a lightning fire before it can be seen");
        System.out.println("PASS: short-lived targeted spirit effects and visible lightning ignition");
    }
    private static void testEmbersScaleWithFire() {
        Forest small = empty(40, 60), large = empty(40, 60);
        Tree only = new Tree(0.9); only.ignite(); small.getGrid()[20][30].plantTree(only);
        for (int row = 18; row <= 22; row++) for (int col = 28; col <= 32; col++) {
            Tree tree = new Tree(0.9); tree.ignite(); large.getGrid()[row][col].plantTree(tree);
        }
        check(Ember.emissionRate(new WorldSnapshot(large), 1) > Ember.emissionRate(new WorldSnapshot(small), 1),
                "Larger fires should produce more embers");
        check(Ember.emissionRate(new WorldSnapshot(large), 0) == 0,
                "No wind gust should mean no windborne ember emission");
        try (WorldSimulation a = new WorldSimulation(small, 12, false);
             WorldSimulation b = new WorldSimulation(large, 12, false)) {
            a.getWeather().startGust(); b.getWeather().startGust();
            for (int step = 0; step < 20; step++) { a.step(0.25); b.step(0.25); }
            check(b.view().embers().size() > a.view().embers().size(),
                    "Larger fires should visibly emit more particles in the seeded integration run");
            System.out.printf("PASS: small fire %d airborne embers; large fire %d%n",
                    a.view().embers().size(), b.view().embers().size());
        }
    }
    private static WorldView fixture(Forest forest, List<Actor.View> actors) {
        return new WorldView(new WorldSnapshot(forest), actors, List.of(), List.of(),
                new WeatherSystem.GustView(1, 0, 0, 0), List.of(), 1.0,
                0, 0, 0, 0, 0, 0, 0, false, "", Map.of());
    }
    private static BufferedImage paint(ForestPanel panel) {
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); panel.paint(graphics); graphics.dispose();
        return image;
    }
    private static void wheel(ForestPanel panel, int amount) {
        panel.dispatchEvent(new MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL, 0, 0,
                panel.getWidth() / 2, panel.getHeight() / 2, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, amount));
    }
    private static void noOutside(ForestPanel panel) {
        BufferedImage image = paint(panel);
        int background = panel.getBackground().getRGB();
        for (int row = 0; row < image.getHeight(); row++) for (int col = 0; col < image.getWidth(); col++) {
            check(image.getRGB(col, row) != background, "Camera exposed outside-world background");
        }
        for (int x : new int[]{0, panel.getWidth() - 1}) for (int y : new int[]{0, panel.getHeight() - 1}) {
            check(panel.getToolTipText(new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false)) != null,
                    "Visible corners must always belong to world cells");
        }
    }
    private static void testCameraBounds() {
        ForestPanel panel = new ForestPanel(fixture(empty(20, 30), List.of()));
        for (int[] size : new int[][]{{1200, 300}, {300, 1200}, {1199, 743}, {320, 200}}) {
            panel.setSize(size[0], size[1]); wheel(panel, 1000); noOutside(panel);
            String first = panel.getToolTipText(new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, 0, 0, 0, false));
            String last = panel.getToolTipText(new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0,
                    size[0] - 1, size[1] - 1, 0, false));
            check(first.startsWith("[0, 0]") && last.startsWith("[19, 29]"),
                    "Maximum zoom-out must show the complete world, without a minimap overlay");
            wheel(panel, -1000); noOutside(panel);
            for (int destination : new int[]{100000, -100000}) {
                panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, 0,
                        100, 100, 1, false, MouseEvent.BUTTON1));
                panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 0,
                        MouseEvent.BUTTON1_DOWN_MASK, destination, destination, 0, false));
                panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0,
                        destination, destination, 1, false, MouseEvent.BUTTON1));
                noOutside(panel);
            }
        }
        System.out.println("PASS: full-world zoom limit, extreme panning and four viewport shapes without outside space");
    }
    private static Actor.View cloneSpirit(Actor.View source, double energy, Position target) {
        return new Actor.View(source.id(), source.kind(), source.position(), source.goal(), null,
                true, false, false, energy, source.state(), target, source.movement());
    }
    private static int differences(BufferedImage first, BufferedImage second) {
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++) {
            if (first.getRGB(x, y) != second.getRGB(x, y)) count++;
        }
        return count;
    }
    private static void testSpiritRenderingWithoutBars() {
        Forest forest = empty(20, 30);
        Tree tree = new Tree(0.9); tree.ignite(); forest.getGrid()[10][11].plantTree(tree);
        ForestSpirit spirit = new ForestSpirit(1, new Position(10, 10)); spirit.act(forest, 0.25);
        ForestPanel panel = new ForestPanel(fixture(forest, List.of(cloneSpirit(spirit.view(), 100, null))));
        panel.setSize(600, 400);
        BufferedImage fullEnergy = paint(panel);
        panel.setView(fixture(forest, List.of(cloneSpirit(spirit.view(), 0, null))));
        check(differences(fullEnergy, paint(panel)) == 0, "Energy levels must not render as progress bars");
        panel.setView(fixture(forest, List.of(spirit.view())));
        check(differences(fullEnergy, paint(panel)) > 0, "An active spirit must render its physical connection");
        System.out.println("PASS: spirit tendril is visible; no energy/progress bars are drawn");
    }
}
