public enum AnimalSpecies {
    COW("Cow", 1.0, 1, 15),
    DEER("Deer", 2.0, 1, 16),
    RABBIT("Rabbit", 3.0, 2, 17);

    public final String displayName;
    public final double speed;
    public final int spriteColumn, spriteRow;
    AnimalSpecies(String displayName, double speed, int spriteColumn, int spriteRow) {
        this.displayName = displayName; this.speed = speed;
        this.spriteColumn = spriteColumn; this.spriteRow = spriteRow;
    }
    public boolean prefers(Biome biome) {
        return switch (this) {
            case COW -> biome == Biome.CLEARING || biome == Biome.SCRUBLAND;
            case DEER -> biome == Biome.WOODLAND || biome == Biome.BOREAL_FOREST;
            case RABBIT -> biome == Biome.WOODLAND || biome == Biome.SCRUBLAND;
        };
    }
}
