import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.QuadCurve2D;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

public class ForestPanel extends JPanel {
    private final SpriteAssets assets = new SpriteAssets();
    private WorldView view;
    private Forest legacyForest;
    // One means the complete world fills the viewport. It cannot zoom out further.
    private double zoomFactor = 1.0;
    private double cameraRow, cameraCol;
    private Point dragStart;
    private double dragRow, dragCol;

    public ForestPanel(WorldView initial) {
        setBackground(new Color(22, 27, 24));
        setToolTipText(""); setFocusable(true); setView(initial);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                dragStart = event.getPoint(); dragRow = cameraRow; dragCol = cameraCol;
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (dragStart == null) return;
                cameraRow = dragRow - (event.getY() - dragStart.y) / scaleY();
                cameraCol = dragCol - (event.getX() - dragStart.x) / scaleX();
                clampCamera(); repaint();
            }
            @Override public void mouseReleased(MouseEvent event) { dragStart = null; }
            @Override public void mouseWheelMoved(MouseWheelEvent event) {
                zoom(Math.pow(1.18, -event.getPreciseWheelRotation()), event.getPoint());
            }
        };
        addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { clampCamera(); repaint(); }
        });
        bind("EQUALS", "zoom-in", () -> zoom(1.2, centre()));
        bind("shift EQUALS", "plus", () -> zoom(1.2, centre()));
        bind("MINUS", "zoom-out", () -> zoom(1.0 / 1.2, centre()));
        bind("UP", "up", () -> pan(-4, 0)); bind("W", "w", () -> pan(-4, 0));
        bind("DOWN", "down", () -> pan(4, 0)); bind("S", "s", () -> pan(4, 0));
        bind("LEFT", "left", () -> pan(0, -4)); bind("A", "a", () -> pan(0, -4));
        bind("RIGHT", "right", () -> pan(0, 4)); bind("D", "d", () -> pan(0, 4));
    }
    public ForestPanel(Forest forest, Lightning lightning) {
        this(new WorldView(new WorldSnapshot(forest), List.of(), List.of(), List.of(),
                new WeatherSystem.GustView(1, 0, 0, 0), List.of(), 0,
                (int) forest.livingTrees().count(), forest.livingTrees().count(),
                0, 0, 0, 0, 0, false, "", Map.of()));
        legacyForest = forest;
    }
    private Point centre() { return new Point(getWidth() / 2, getHeight() / 2); }
    private void bind(String key, String name, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { action.run(); }
        });
    }
    public void setView(WorldView next) { view = next; clampCamera(); repaint(); }
    public void centreOn(Position position) {
        cameraRow = position.row() + 0.5 - getHeight() / scaleY() / 2;
        cameraCol = position.col() + 0.5 - getWidth() / scaleX() / 2;
        clampCamera(); repaint();
    }
    private double scaleX() { return Math.max(1, getWidth()) / (double) view.world().columns() * zoomFactor; }
    private double scaleY() { return Math.max(1, getHeight()) / (double) view.world().rows() * zoomFactor; }
    private double tileSize() { return Math.min(scaleX(), scaleY()); }
    private void zoom(double factor, Point anchor) {
        double row = cameraRow + anchor.y / scaleY(), col = cameraCol + anchor.x / scaleX();
        zoomFactor = Math.max(1.0, Math.min(8.0, zoomFactor * factor));
        cameraRow = row - anchor.y / scaleY(); cameraCol = col - anchor.x / scaleX();
        clampCamera(); repaint();
    }
    private void pan(int row, int col) { cameraRow += row; cameraCol += col; clampCamera(); repaint(); }
    private void clampCamera() {
        if (view == null) return;
        cameraRow = Math.max(0, Math.min(cameraRow,
                Math.max(0, view.world().rows() - getHeight() / scaleY())));
        cameraCol = Math.max(0, Math.min(cameraCol,
                Math.max(0, view.world().columns() - getWidth() / scaleX())));
    }
    private int x(double col) { return (int) Math.round((col - cameraCol) * scaleX()); }
    private int y(double row) { return (int) Math.round((row - cameraRow) * scaleY()); }
    private static Color ground(WorldSnapshot world, int index) {
        if (world.scorched(index)) return new Color(48, 38, 34);
        if (world.firebreak(index)) return new Color(151, 139, 97);
        return world.biome(index).getGroundColor();
    }

    @Override public String getToolTipText(MouseEvent event) {
        if (event.getX() < 0 || event.getY() < 0 || event.getX() >= getWidth() || event.getY() >= getHeight()) return null;
        WorldSnapshot world = legacyForest == null ? view.world() : new WorldSnapshot(legacyForest);
        int row = (int) Math.floor(cameraRow + event.getY() / scaleY());
        int col = (int) Math.floor(cameraCol + event.getX() / scaleX());
        if (!world.contains(row, col)) return null;
        int index = world.index(row, col);
        String text = world.biome(index).getDisplayName()
                + (world.firebreak(index) ? " | Boundary clearing" : "")
                + (world.scorched(index) ? " | Burnt ground" : "");
        if (world.hasTree(index)) text += " | " + world.species(index).getDisplayName()
                + " | Health " + Math.round(world.health(index)) + "/" + Math.round(world.maximumHealth(index))
                + " | Heat " + Math.round(world.heat(index) * 100) + "%";
        text += " | Moisture " + Math.round(world.moisture(index) * 100) + "%";
        Position position = new Position(row, col);
        for (Actor.View actor : view.actors()) {
            if (!actor.position().equals(position)) continue;
            text += " | " + (actor.kind() == Actor.Kind.ANIMAL ? actor.animalSpecies().displayName : actor.kind())
                    + (actor.alive() ? " | " + actor.state() : " | Dead");
        }
        return "[" + row + ", " + col + "] " + text;
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        clampCamera();
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        WorldSnapshot world = legacyForest == null ? view.world() : new WorldSnapshot(legacyForest);
        // Every world-space effect, including clouds and sprite edges, is clipped
        // to the terrain. The camera cannot pan or zoom into an outside area.
        g.clip(new Rectangle(x(0), y(0), x(world.columns()) - x(0), y(world.rows()) - y(0)));
        int startRow = Math.max(0, (int) cameraRow), startCol = Math.max(0, (int) cameraCol);
        int endRow = Math.min(world.rows(), (int) Math.ceil(cameraRow + getHeight() / scaleY()));
        int endCol = Math.min(world.columns(), (int) Math.ceil(cameraCol + getWidth() / scaleX()));
        int spriteSize = Math.max(2, (int) tileSize() - 2);
        for (int row = startRow; row < endRow; row++) {
            for (int col = startCol; col < endCol; col++) {
                int index = world.index(row, col);
                int left = x(col), top = y(row), width = x(col + 1) - left, height = y(row + 1) - top;
                g.setColor(ground(world, index)); g.fillRect(left, top, width, height);
                if (!world.hasTree(index) || world.burning(index)) continue;
                g.drawImage(assets.tile("tree", spriteSize), left + (width - spriteSize) / 2,
                        top + (height - spriteSize) / 2, null);
                if (tileSize() >= 8) {
                    g.setColor(world.species(index).getMarkerColor());
                    int marker = Math.max(2, (int) tileSize() / 5);
                    g.fillRect(left + 1, top + 1, marker, marker);
                }
                if (world.heat(index) > 0.1) {
                    g.setColor(new Color(255, 100, 35, (int) Math.min(130, world.heat(index) * 110)));
                    g.fillRect(left, top, width, height);
                }
            }
        }
        drawWeather(g);
        // Flames stay visible above clouds rather than being hidden underneath them.
        for (Position fire : world.fires()) {
            if (fire.row() < startRow || fire.row() >= endRow || fire.col() < startCol || fire.col() >= endCol) continue;
            int left = x(fire.col()), top = y(fire.row());
            int width = x(fire.col() + 1) - left, height = y(fire.row() + 1) - top;
            g.setColor(new Color(255, 104, 22, 95)); g.fillRect(left, top, width, height);
            g.drawImage(assets.tile("fire", spriteSize), left + (width - spriteSize) / 2,
                    top + (height - spriteSize) / 2, null);
        }
        drawSpiritEffects(g);
        drawActors(g);
        int emberSize = Math.max(1, (int) Math.round(spriteSize * 0.10));
        g.setStroke(new BasicStroke(Math.max(0.5f, emberSize * 0.6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Ember.View ember : view.embers()) {
            double endX = x(ember.col()), endY = y(ember.row());
            double dx = x(ember.previousCol()) - endX, dy = y(ember.previousRow()) - endY;
            double length = Math.hypot(dx, dy);
            double fraction = length == 0 ? 0.0 : Math.min(1.0, tileSize() * 0.3 / length);
            g.setColor(new Color(255, 157, 54, 145));
            g.drawLine((int) (endX + dx * fraction), (int) (endY + dy * fraction), (int) endX, (int) endY);
            g.setColor(new Color(255, 220, 129));
            g.fillRect((int) endX - emberSize / 2, (int) endY - emberSize / 2, emberSize, emberSize);
        }
        g.dispose();
    }
    private void drawSpiritEffects(Graphics2D g) {
        for (Actor.View actor : view.actors()) {
            if (actor.kind() != Actor.Kind.SPIRIT || actor.effectTarget() == null || !actor.alive()) continue;
            double startX = x(actor.position().col() + 0.5), startY = y(actor.position().row() + 0.25);
            double endX = x(actor.effectTarget().col() + 0.5), endY = y(actor.effectTarget().row() + 0.8);
            double middleX = (startX + endX) / 2.0 + tileSize() * 0.15;
            double middleY = (startY + endY) / 2.0 - tileSize() * 0.25;
            g.setColor(new Color(155, 245, 255, 205));
            g.setStroke(new BasicStroke(Math.max(1.0f, (float) tileSize() * 0.06f),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new QuadCurve2D.Double(startX, startY, middleX, middleY, endX, endY));
            int size = Math.max(1, (int) (tileSize() * 0.10));
            for (int dot = 0; dot < 2; dot++) {
                double t = (view.time() * 1.4 + dot * 0.5) % 1.0, opposite = 1.0 - t;
                double px = opposite * opposite * startX + 2 * opposite * t * middleX + t * t * endX;
                double py = opposite * opposite * startY + 2 * opposite * t * middleY + t * t * endY;
                g.fillOval((int) px - size / 2, (int) py - size / 2, size, size);
            }
        }
    }
    private void drawActors(Graphics2D g) {
        int size = Math.max(8, (int) (tileSize() * 1.3));
        for (Actor.View actor : view.actors()) {
            int left = x(actor.position().col() + 0.5) - size / 2;
            int top = y(actor.position().row() + 0.5) - size / 2;
            if (left + size < 0 || top + size < 0 || left > getWidth() || top > getHeight()) continue;
            if (!actor.alive()) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            String sprite = switch (actor.kind()) {
                case ANIMAL -> "animal-" + actor.animalSpecies().name();
                case SPIRIT -> "spirit"; case ARSONIST -> "arsonist";
            };
            if (actor.kind() == Actor.Kind.ARSONIST && actor.alive()) {
                g.setColor(new Color(255, 60, 85, 150)); g.drawOval(left - 2, top - 2, size + 4, size + 4);
            }
            g.drawImage(assets.tile(sprite, size), left, top, null);
            g.setComposite(AlphaComposite.SrcOver);
        }
    }
    private void drawWeather(Graphics2D g) {
        for (WeatherSystem.StormView storm : view.storms()) {
            int centreX = x(storm.col() + 0.5), centreY = y(storm.row() + 0.5);
            for (int layer = 0; layer < 4; layer++) {
                double fraction = 1.0 - layer * 0.23;
                int rx = (int) (storm.radius() * scaleX() * fraction);
                int ry = (int) (storm.radius() * scaleY() * fraction);
                g.setColor(new Color(70, 112, 175, 12 + layer * 5));
                g.fillOval(centreX - rx, centreY - ry, rx * 2, ry * 2);
            }
            g.setColor(new Color(153, 204, 245, 145));
            for (int i = 0; i < 140; i++) {
                double angle = i * 2.39996;
                double reach = Math.sqrt((i + 0.5) / 140.0) * storm.radius();
                double density = WeatherSystem.moistureProfile(reach, storm.radius());
                if ((i * 0.6180339) % 1.0 > density) continue;
                double row = storm.row() + Math.sin(angle) * reach + ((view.time() * 2 + i * 0.37) % 1.0 - 0.5) * 0.6;
                double col = storm.col() + Math.cos(angle) * reach;
                int dropX = x(col), dropY = y(row);
                g.drawLine(dropX, dropY, dropX - 1, dropY + Math.max(2, (int) tileSize() / 3));
            }
            int cloudSize = Math.max(16, (int) (tileSize() * 3));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
            g.drawImage(assets.tile("cloud", cloudSize), centreX - cloudSize / 2,
                    centreY - cloudSize / 2, null);
            g.setComposite(AlphaComposite.SrcOver);
        }
        for (WeatherSystem.FlashView flash : view.flashes()) {
            int size = Math.max(16, (int) tileSize() * 5);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flash.startedFire() ? 1.0f : 0.75f));
            g.drawImage(assets.tile("storm-lightning", size, Math.min(8, (int) (flash.age() * 9))),
                    x(flash.position().col() + 0.5) - size / 2, y(flash.position().row() + 0.5) - size, null);
            g.setComposite(AlphaComposite.SrcOver);
        }
        if (view.gust().strength() > 0.0) {
            int size = Math.max(10, (int) tileSize() * 2);
            int frame = (int) (view.time() * 12) % 12;
            for (int i = 0; i < 18; i++) {
                double row = positiveMod(i * 13.1 + view.time() * 4 * view.gust().y(), view.world().rows());
                double col = positiveMod(i * 19.7 + view.time() * 4 * view.gust().x(), view.world().columns());
                g.drawImage(assets.tile("wind", size, frame), x(col), y(row), null);
            }
        }
    }
    private static double positiveMod(double value, double modulus) { return (value % modulus + modulus) % modulus; }
}
