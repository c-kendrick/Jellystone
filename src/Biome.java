import java.awt.Color;
import java.util.Random;

public enum Biome {
    WOODLAND("Woodland", 0.75, new Color(68, 88, 43),
            TreeSpecies.OAK, TreeSpecies.OAK, TreeSpecies.OAK,
            TreeSpecies.BIRCH, TreeSpecies.BIRCH),
    BOREAL_FOREST("Boreal forest", 0.85, new Color(39, 64, 86),
            TreeSpecies.SPRUCE, TreeSpecies.SPRUCE, TreeSpecies.SPRUCE,
            TreeSpecies.SPRUCE, TreeSpecies.BIRCH),
    EUCALYPTUS_GROVE("Eucalyptus grove", 0.60, new Color(110, 91, 49),
            TreeSpecies.EUCALYPTUS, TreeSpecies.EUCALYPTUS,
            TreeSpecies.EUCALYPTUS, TreeSpecies.OAK),
    RAINFOREST("Rainforest", 0.95, new Color(28, 91, 62),
            TreeSpecies.JUNGLE, TreeSpecies.JUNGLE, TreeSpecies.JUNGLE,
            TreeSpecies.JUNGLE, TreeSpecies.BIRCH, TreeSpecies.OAK),
    SCRUBLAND("Scrubland", 0.20, new Color(137, 118, 71),
            TreeSpecies.EUCALYPTUS, TreeSpecies.OAK, TreeSpecies.BIRCH),
    CLEARING("Clearing", 0.0, new Color(150, 140, 98));

    private final String displayName;
    private final double treeDensity;
    private final Color groundColor;
    private final TreeSpecies[] speciesPool;

    Biome(String displayName, double treeDensity, Color groundColor,
          TreeSpecies... speciesPool) {
        this.displayName = displayName;
        this.treeDensity = treeDensity;
        this.groundColor = groundColor;
        this.speciesPool = speciesPool;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getTreeDensity() {
        return treeDensity;
    }

    public Color getGroundColor() {
        return groundColor;
    }

    public double getInitialMoisture() {
        return switch (this) {
            case WOODLAND -> 0.18;
            case BOREAL_FOREST -> 0.28;
            case EUCALYPTUS_GROVE -> 0.08;
            case RAINFOREST -> 0.58;
            case SCRUBLAND -> 0.10;
            case CLEARING -> 0.12;
        };
    }

    public Tree createTree(Random random) {
        if (speciesPool.length == 0) {
            throw new IllegalStateException(displayName + " does not grow trees");
        }
        // Repeated entries give a species a greater share of the mixture.
        return speciesPool[random.nextInt(speciesPool.length)].createTree(random);
    }
}
