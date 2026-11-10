import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--headless")) {
            runHeadless(args);
            return;
        }
        SwingUtilities.invokeLater(Main::showWindow);
    }

    private static void showWindow() {
        WorldSimulation simulation = createSimulation(System.nanoTime(), true);
        simulation.start();
        JFrame window = new JFrame("Jellystone");
        ForestPanel panel;
        try {
            panel = new ForestPanel(simulation.view());
        } catch (RuntimeException exception) {
            simulation.close();
            JOptionPane.showMessageDialog(null, exception.getMessage(),
                    "Could not load forest images", JOptionPane.ERROR_MESSAGE);
            return;
        }
        panel.setPreferredSize(new Dimension(1180, 740));
        JLabel status = new JLabel();
        JLabel event = new JLabel();
        JPanel statistics = new JPanel(new GridLayout(2, 1, 0, 3));
        statistics.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        statistics.add(status); statistics.add(event);
        window.add(statistics, BorderLayout.NORTH);
        window.add(panel, BorderLayout.CENTER);
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.pack();
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        window.setSize(Math.min(window.getWidth(), screen.width),
                Math.min(window.getHeight(), screen.height));
        window.setLocationRelativeTo(null); window.setVisible(true);
        panel.requestFocusInWindow();
        updateLabels(status, event, simulation.view());
        Timer repaint = new Timer(50, action -> panel.repaint());
        repaint.start();
        ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "forest-model"); thread.setDaemon(true); return thread;
        });
        ticker.scheduleAtFixedRate(() -> {
            try {
                simulation.step(SimulationRules.STEP_SECONDS);
                WorldView next = simulation.view();
                SwingUtilities.invokeLater(() -> {
                    if (!window.isDisplayable()) return;
                    panel.setView(next); updateLabels(status, event, next);
                });
            } catch (RuntimeException exception) {
                ticker.shutdown();
                SwingUtilities.invokeLater(() -> {
                    if (window.isDisplayable()) JOptionPane.showMessageDialog(window,
                            "The simulation stopped: " + exception.getMessage(),
                            "Simulation error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, 125, 125, TimeUnit.MILLISECONDS); // Fixed 2× speed; physics step stays unchanged.
        window.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                ticker.shutdownNow(); repaint.stop(); simulation.close();
            }
        });
    }

    private static void updateLabels(JLabel status, JLabel event, WorldView view) {
        status.setText(String.format("Fire: %d  |  Trees: %d / %d  |  Animals: %d alive, %d lost",
                view.world().fires().size(),
                view.livingTrees(), view.initialTrees(), view.livingAnimals(), view.deadAnimals()));
        status.setToolTipText("Living trees by biome: " + view.treesByBiome());
        event.setText(view.latestEvent() + (view.drySpell() ? "  •  Dry spell" : "")
                + "  |  Arson: " + view.arsonAttacks()
                + "  |  Lightning: " + view.lightningStrikes() + " strikes, " + view.lightningFires() + " fires");
    }
    public static WorldSimulation createSimulation(long seed, boolean parallel) {
        Forest forest = new Forest(SimulationRules.ROWS, SimulationRules.COLUMNS,
                new BiomeForestGenerator(), new Random(seed));
        return new WorldSimulation(forest, seed ^ 0x5deece66dL, parallel);
    }
    private static void runHeadless(String[] args) {
        double duration = args.length > 1 ? Double.parseDouble(args[1]) : 240.0;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 42L;
        WorldSimulation.Startup startup = args.length > 3
                ? WorldSimulation.Startup.valueOf(args[3].toUpperCase()) : WorldSimulation.Startup.COMBINED;
        try (WorldSimulation simulation = createSimulation(seed, true)) {
            simulation.start(startup);
            long began = System.nanoTime();
            int steps = (int) Math.ceil(duration / SimulationRules.STEP_SECONDS);
            for (int step = 0; step < steps; step++) simulation.step(SimulationRules.STEP_SECONDS);
            WorldView view = simulation.view();
            System.out.printf("%.1f simulation seconds in %.2f real seconds; trees %d/%d; burning %d; animals %d alive/%d dead; arson %d; lightning %d strikes/%d fires; gusts %d%n",
                    view.time(), (System.nanoTime() - began) / 1e9, view.livingTrees(), view.initialTrees(),
                    view.world().fires().size(), view.livingAnimals(), view.deadAnimals(), view.arsonAttacks(),
                    view.lightningStrikes(), view.lightningFires(), simulation.getWeather().getGustCount());
        }
    }
}
