class Cell {
    private int x;
    private int y;
    private Tree tree;
    private double windExposure;
    private Biome biome = Biome.WOODLAND;
    private double heat;
    private double moisture;
    private double rainExposure;
    private boolean scorched;
    private boolean firebreak;
    private int regionId;
    private double ignitionProtection;

    Cell(int x, int y) {
        this.x = x;
        this.y = y;
        this.tree = null;
    }
    
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setX(int newX) {
        x = newX;
    }

    public void setY(int newY) {
        y = newY;
    }

    public void plantTree(Tree tree) {
        this.tree = tree;
    }

    public boolean hasTree() {
        if(this.tree == null) {
            return false;
        }
        return true;
    }

    public Tree getTree() {
        return this.tree;
    }

    public Tree removeTree() {
        Tree removedTree = tree;
        tree = null;
        return removedTree;
    }

    public boolean isEmpty() {
        return !hasTree();
    }
    
    public double getWindExposure() {
        return windExposure;
    }

    public void setWindExposure(double windExposure) {
        this.windExposure = windExposure;
    }

    public Biome getBiome() {
        return biome;
    }

    public void setBiome(Biome biome) {
        if (biome == null) {
            throw new IllegalArgumentException("Biome must not be null");
        }
        this.biome = biome;
    }

    public double getHeat() { return heat; }
    public void setHeat(double heat) { this.heat = Math.max(0.0, heat); }
    public double getMoisture() { return moisture; }
    public void setMoisture(double moisture) { this.moisture = Math.max(0.0, Math.min(1.0, moisture)); }
    public double getRainExposure() { return rainExposure; }
    public void setRainExposure(double exposure) { rainExposure = Math.max(0.0, exposure); }
    public boolean isScorched() { return scorched; }
    public void setScorched(boolean scorched) { this.scorched = scorched; }
    public boolean isFirebreak() { return firebreak; }
    public void setFirebreak(boolean firebreak) { this.firebreak = firebreak; }
    public int getRegionId() { return regionId; }
    public void setRegionId(int regionId) { this.regionId = regionId; }
    public void protectIgnition(double seconds) { ignitionProtection = Math.max(0.0, seconds); }
    public void advanceIgnitionProtection(double seconds) {
        ignitionProtection = Math.max(0.0, ignitionProtection - seconds);
    }
    public boolean isIgnitionProtected() { return ignitionProtection > 0.0; }
}
