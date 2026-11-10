import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

public class RendererTest {
    public static void main(String[] args) throws Exception {
        File output = args.length == 0
                ? java.nio.file.Files.createTempDirectory("jellystone-render-check-").toFile()
                : new File(args[0]);
        if (!output.exists() && !output.mkdirs()) throw new IllegalStateException("Cannot create output directory");
        SpriteAssets assets = new SpriteAssets();
        String[] names = {"animal-COW", "animal-DEER", "animal-RABBIT", "arsonist", "spirit", "cloud", "wind"};
        BufferedImage contact = new BufferedImage(630, 110, BufferedImage.TYPE_INT_RGB);
        Graphics2D contactGraphics = contact.createGraphics();
        contactGraphics.setColor(new Color(35, 45, 43)); contactGraphics.fillRect(0, 0, 630, 110);
        for (int index = 0; index < names.length; index++) {
            BufferedImage sprite = assets.tile(names[index], 64, names[index].equals("wind") ? 5 : 0);
            int visible = 0;
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                if ((sprite.getRGB(x, y) >>> 24) != 0) visible++;
            }
            if (visible == 0) throw new AssertionError("Invisible sprite: " + names[index]);
            contactGraphics.drawImage(sprite, index * 90 + 13, 5, null);
            contactGraphics.setColor(Color.WHITE);
            contactGraphics.drawString(names[index].replace("animal-", ""), index * 90 + 8, 95);
        }
        contactGraphics.dispose();
        ImageIO.write(contact, "png", new File(output, "sprite-check.png"));
        try (WorldSimulation simulation = Main.createSimulation(42, false)) {
            simulation.start();
            for (int i = 0; i < 480; i++) simulation.step(0.25);
            simulation.getWeather().startGust();
            for (int i = 0; i < 8; i++) simulation.step(0.25);
            WorldView view = simulation.view();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    ForestPanel panel = new ForestPanel(view);
                    panel.setSize(1180, 740);
                    panel.centreOn(new Position(60, 90));
                    MouseEvent hover = new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, 400, 300, 0, false);
                    String before = panel.getToolTipText(hover);
                    panel.dispatchEvent(new MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL, 0, 0,
                            400, 300, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1));
                    String after = panel.getToolTipText(hover);
                    if (!before.equals(after)) throw new AssertionError("Zoom should preserve the tile under the mouse");
                    panel.dispatchEvent(new MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL, 0, 0,
                            590, 370, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -5));
                    if (!view.world().fires().isEmpty()) panel.centreOn(view.world().fires().get(0));
                    save(panel, new File(output, "world-closeup.png"));
                    panel.dispatchEvent(new MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL, 0, 0,
                            590, 370, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 100));
                    save(panel, new File(output, "world-overview.png"));
                } catch (Exception exception) { throw new RuntimeException(exception); }
            });
        }
        System.out.println("PASS: seven visible source sprites, camera zoom anchor, overview and close-up rendering");
        System.out.println("Inspection images: " + output.getAbsolutePath());
    }
    private static void save(ForestPanel panel, File output) throws Exception {
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); panel.paint(graphics); graphics.dispose();
        int colourful = 0;
        for (int y = 0; y < image.getHeight(); y += 5) for (int x = 0; x < image.getWidth(); x += 5) {
            if (image.getRGB(x, y) != panel.getBackground().getRGB()) colourful++;
        }
        if (colourful < 5000) throw new AssertionError("Forest renderer appears blank");
        ImageIO.write(image, "png", output);
    }
}
