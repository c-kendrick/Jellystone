import java.awt.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import javax.swing.*;

public class ForestPanel extends JPanel {

    private Forest forest;

    private Image treeImage;
    private Image fireImage;

    private Lightning lightning;
    private Image lightningImage;

    private Cell lastLightningCell;
    private long lightningStartTime;

    public ForestPanel(Forest forest, Lightning lightning) {
        this.forest = forest;
        this.lightning = lightning;

        treeImage = loadImage("tree.png");
        fireImage = loadImage("fire.png");
        lightningImage = loadImage("lightning.png");
    }

    private static Image loadImage(String fileName) {
        try {
            // Supports images included on the classpath or inside a JAR.
            URL resource = ForestPanel.class.getResource("/images/" + fileName);
            if (resource != null) {
                return readImage(resource);
            }

            // Find the project images folder even when an IDE uses a nested
            // output directory and Java starts outside the project folder.
            Path location = Paths.get(ForestPanel.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path directory = Files.isDirectory(location)
                    ? location : location.getParent();

            while (directory != null) {
                Path imagePath = directory.resolve("images").resolve(fileName);
                if (Files.isRegularFile(imagePath)) {
                    return readImage(imagePath.toUri().toURL());
                }
                directory = directory.getParent();
            }

            // Also supports IDEs that compile outside the project entirely.
            Path imagePath = Paths.get("images", fileName);
            if (Files.isRegularFile(imagePath)) {
                return readImage(imagePath.toUri().toURL());
            }
        } catch (IOException | URISyntaxException exception) {
            throw new IllegalStateException(
                    "Could not load images/" + fileName, exception);
        }

        throw new IllegalStateException("Cannot find images/" + fileName
                + ". Keep the images folder in the project, or include it"
                + " on the runtime classpath. Working directory: "
                + System.getProperty("user.dir"));
    }

    private static Image readImage(URL resource) throws IOException {
        Image image = ImageIO.read(resource);
        if (image == null) {
            throw new IOException("Unsupported or corrupt image: " + resource);
        }
        return image;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Cell[][] grid = forest.getGrid();

        int cellWidth = getWidth() / grid[0].length;
        int cellHeight = getHeight() / grid.length;

        Cell currentLightningCell = null;

        if (lightning != null) {
            currentLightningCell = lightning.getLastStruckCell();

            if (currentLightningCell != lastLightningCell) {
                lastLightningCell = currentLightningCell;
                lightningStartTime = System.currentTimeMillis();
            }
        }

        for (int row = 0; row < grid.length; row++) {

            for (int col = 0; col < grid[row].length; col++) {

                Cell cell = grid[row][col];

                g.setColor(Color.BLACK);

                g.fillRect(
                    col * cellWidth,
                    row * cellHeight,
                    cellWidth,
                    cellHeight
                );

                if (lightning != null &&
                    cell == currentLightningCell &&
                    System.currentTimeMillis() - lightningStartTime < 3000) {

                    g.drawImage(
                        lightningImage,
                        col * cellWidth + 2,
                        row * cellHeight + 2,
                        cellWidth - 4,
                        cellHeight - 4,
                        this
                    );
                }
                else {
                    if (!cell.hasTree()) {

                        // Empty cell stays black

                    }
                    else if (cell.getTree().isBurning()) {

                        g.drawImage(
                            fireImage,
                            col * cellWidth + 2,
                            row * cellHeight + 2,
                            cellWidth - 4,
                            cellHeight - 4,
                            this
                        );
                    }
                    else {

                        g.drawImage(
                            treeImage,
                            col * cellWidth + 2,
                            row * cellHeight + 2,
                            cellWidth - 4,
                            cellHeight - 4,
                            this
                        );
                    }
                }
            }
        }
    }
}
