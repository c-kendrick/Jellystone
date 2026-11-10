import java.awt.Color;
import java.util.Random;
import java.util.function.DoubleFunction;

public enum TreeSpecies {
    OAK("Oak", new Color(208, 158, 87), Oak::new),
    BIRCH("Birch", new Color(238, 238, 218), Birch::new),
    EUCALYPTUS("Eucalyptus", new Color(104, 215, 210), Eucalyptus::new),
    SPRUCE("Spruce", new Color(121, 153, 247), Spruce::new),
    JUNGLE("Jungle", new Color(227, 126, 221), Jungle::new);

    private final String displayName;
    private final Color markerColor;
    private final DoubleFunction<Tree> factory;

    TreeSpecies(String displayName, Color markerColor, DoubleFunction<Tree> factory) {
        this.displayName = displayName;
        this.markerColor = markerColor;
        this.factory = factory;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getMarkerColor() {
        return markerColor;
    }

    public Tree createTree(Random random) {
        return factory.apply(0.3 + random.nextDouble() * 0.7);
    }

    public static TreeSpecies fromTree(Tree tree) {
        return valueOf(tree.getTreeType());
    }
}
