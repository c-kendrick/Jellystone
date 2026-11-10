import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Reads the original source PNGs; sprite-sheet frames are selected at runtime. */
public final class SpriteAssets {
    private final Map<String, BufferedImage> originals = new HashMap<>();
    private final Map<String, BufferedImage> scaled = new HashMap<>();

    public SpriteAssets() {
        for (String name : new String[]{"tree", "fire", "lightning"}) {
            originals.put(name, load("images/" + name + ".png"));
        }
        for (String name : new String[]{"animals", "people", "spirit", "cloud", "wind", "rain"}) {
            originals.put(name, load("images/sprites/" + name + ".png"));
        }
        originals.put("storm-lightning", load("images/sprites/lightning.png"));
    }

    public BufferedImage tile(String name, int size) { return tile(name, size, 0); }
    public BufferedImage tile(String name, int size, int frame) {
        size = Math.max(1, size);
        String key = name + ":" + size + ":" + frame;
        BufferedImage cached = scaled.get(key);
        if (cached != null) return cached;
        BufferedImage source;
        if (name.startsWith("animal-")) {
            AnimalSpecies species = AnimalSpecies.valueOf(name.substring(7));
            source = originals.get("animals").getSubimage(
                    species.spriteColumn * 16, species.spriteRow * 16, 16, 16);
        } else if (name.equals("arsonist")) {
            source = originals.get("people").getSubimage(17, 7 * 17, 16, 16);
        } else if (name.equals("spirit") || name.equals("cloud") || name.equals("wind")
                || name.equals("rain") || name.equals("storm-lightning")) {
            BufferedImage strip = originals.get(name);
            int frameSize = strip.getHeight();
            int count = strip.getWidth() / frameSize;
            source = strip.getSubimage(Math.floorMod(frame, count) * frameSize, 0,
                    frameSize, frameSize);
        } else source = originals.get(name);
        if (source == null) throw new IllegalArgumentException("Unknown sprite: " + name);
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(source, 0, 0, size, size, null);
        graphics.dispose();
        // Zoom can produce many requested sizes over a long session.
        if (scaled.size() > 800) scaled.clear();
        scaled.put(key, result);
        return result;
    }

    private static BufferedImage load(String relativePath) {
        try {
            URL resource = SpriteAssets.class.getResource("/" + relativePath);
            if (resource != null) return read(resource);
            Path location = Paths.get(SpriteAssets.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path directory = Files.isDirectory(location) ? location : location.getParent();
            while (directory != null) {
                Path candidate = directory.resolve(relativePath);
                if (Files.isRegularFile(candidate)) return read(candidate.toUri().toURL());
                directory = directory.getParent();
            }
            Path candidate = Paths.get(relativePath);
            if (Files.isRegularFile(candidate)) return read(candidate.toUri().toURL());
        } catch (IOException | URISyntaxException exception) {
            throw new IllegalStateException("Could not load " + relativePath, exception);
        }
        throw new IllegalStateException("Cannot find " + relativePath
                + ". Copy the complete images folder beside src, or include it on the classpath.");
    }
    private static BufferedImage read(URL resource) throws IOException {
        BufferedImage result = ImageIO.read(resource);
        if (result == null) throw new IOException("Unsupported or corrupt image: " + resource);
        return result;
    }
}
