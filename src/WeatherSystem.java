import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public final class WeatherSystem {
    public record StormView(double row, double col, double radius, double intensity) { }
    public record FlashView(Position position, double age, boolean startedFire) { }
    public record GustView(double x, double y, double strength, double remaining) { }

    private static final class Storm {
        double row, col, remaining, driftRow, driftCol;
        final double radius;
        Storm(Position edge, int rows, int columns, Random random) {
            radius = Math.min(9.0 + random.nextDouble() * 8.0,
                    Math.max(0.5, Math.min(rows, columns) * 0.45));
            double angle = random.nextDouble() * Math.PI * 2.0;
            row = edge.row(); col = edge.col();
            // Put the ignited tree at the dry outer edge, not underneath the
            // saturated centre. Search directions keep the cloud in the world.
            for (int attempt = 0; attempt < 32; attempt++) {
                double direction = angle + attempt * Math.PI / 16.0;
                double candidateRow = edge.row() - Math.sin(direction) * radius * 0.96;
                double candidateCol = edge.col() - Math.cos(direction) * radius * 0.96;
                if (candidateRow >= 0 && candidateRow <= rows - 1
                        && candidateCol >= 0 && candidateCol <= columns - 1) {
                    row = candidateRow; col = candidateCol; break;
                }
            }
            double awayRow = row - edge.row(), awayCol = col - edge.col();
            double length = Math.hypot(awayRow, awayCol);
            double speed = 0.25 + random.nextDouble() * 0.3;
            driftRow = length == 0 ? 0.0 : awayRow / length * speed;
            driftCol = length == 0 ? 0.0 : awayCol / length * speed;
            remaining = 38.0 + random.nextDouble() * 16.0;
        }
        StormView view() { return new StormView(row, col, radius, 1.0); }
    }
    private static final class Flash {
        final Position position;
        final boolean startedFire;
        double age;
        Flash(Position position, boolean startedFire) {
            this.position = position; this.startedFire = startedFire;
        }
    }

    private final Random random;
    private final List<Storm> storms = new ArrayList<>();
    private final List<Flash> flashes = new ArrayList<>();
    // These deadlines belong to the whole simulation, not to individual clouds.
    private double nextLightning, nextAmbientLightning, nextGust, nextDrySpell, dryUntil;
    private double gustRemaining, windX = 1.0, windY, gustStrength;
    private int lightningCount, lightningFireCount, stormCount, gustCount;
    private Position lastFirePosition;
    private String latestEvent = "A new forest";

    public WeatherSystem(Random random) {
        this.random = random;
        nextLightning = lightningInterval();
        nextAmbientLightning = ambientLightningInterval();
        nextGust = 7.0 + random.nextDouble() * 8.0;
        nextDrySpell = 70.0 + random.nextDouble() * 40.0;
    }
    private double lightningInterval() {
        return SimulationRules.LIGHTNING_MIN_INTERVAL + random.nextDouble()
                * (SimulationRules.LIGHTNING_MAX_INTERVAL - SimulationRules.LIGHTNING_MIN_INTERVAL);
    }
    private double ambientLightningInterval() {
        return SimulationRules.AMBIENT_LIGHTNING_MIN_INTERVAL + random.nextDouble()
                * (SimulationRules.AMBIENT_LIGHTNING_MAX_INTERVAL - SimulationRules.AMBIENT_LIGHTNING_MIN_INTERVAL);
    }

    public void advance(Forest forest, double time, double seconds) {
        int rows = forest.getGrid().length, columns = forest.getGrid()[0].length;
        for (Storm storm : storms) {
            storm.row += storm.driftRow * seconds; storm.col += storm.driftCol * seconds;
            if (storm.row < 0.0 || storm.row > rows - 1.0) {
                storm.row = Math.max(0.0, Math.min(rows - 1.0, storm.row));
                storm.driftRow = -storm.driftRow;
            }
            if (storm.col < 0.0 || storm.col > columns - 1.0) {
                storm.col = Math.max(0.0, Math.min(columns - 1.0, storm.col));
                storm.driftCol = -storm.driftCol;
            }
            storm.remaining -= seconds;
        }
        storms.removeIf(storm -> storm.remaining <= 0.0);
        flashes.forEach(flash -> flash.age += seconds);
        flashes.removeIf(flash -> flash.age > 1.2);
        gustRemaining = Math.max(0.0, gustRemaining - seconds);
        if (time >= nextLightning) {
            strike(forest);
            nextLightning = time + lightningInterval();
        }
        if (time >= nextAmbientLightning) {
            wetStrike(rows, columns);
            nextAmbientLightning = time + ambientLightningInterval();
        }
        if (time >= nextGust) {
            startGust();
            nextGust = time + 22.0 + random.nextDouble() * 24.0;
        }
        if (time >= nextDrySpell) {
            dryUntil = time + 22.0 + random.nextDouble() * 15.0;
            nextDrySpell = dryUntil + 65.0 + random.nextDouble() * 40.0;
            latestEvent = "A dry spell increases evaporation";
        }
    }

    /** The one globally scheduled fire-starting strike. */
    public boolean strike(Forest forest) {
        List<Cell> candidates = forest.livingTrees()
                .filter(cell -> !cell.getTree().isBurning()).collect(Collectors.toList());
        List<Cell> dry = candidates.stream().filter(cell -> cell.getMoisture() <= 0.25)
                .collect(Collectors.toList());
        List<Cell> pool = dry.isEmpty() ? candidates : dry;
        if (lastFirePosition != null) {
            List<Cell> elsewhere = pool.stream().filter(cell ->
                    new Position(cell.getX(), cell.getY()).distanceTo(lastFirePosition) >= 15.0)
                    .collect(Collectors.toList());
            if (!elsewhere.isEmpty()) pool = elsewhere;
        }
        int rows = forest.getGrid().length, columns = forest.getGrid()[0].length;
        if (pool.isEmpty()) {
            // The weather continues, but no trees are fabricated or resurrected.
            storms.add(new Storm(new Position(random.nextInt(rows), random.nextInt(columns)),
                    rows, columns, random));
            stormCount++;
            latestEvent = "Rain moves over exhausted or already burning ground";
            return false;
        }
        Cell target = pool.get(random.nextInt(pool.size()));
        Position position = new Position(target.getX(), target.getY());
        new Lightning(random).igniteAt(forest, position);
        storms.add(new Storm(position, rows, columns, random));
        stormCount++;
        flashes.add(new Flash(position, true));
        lightningCount++; lightningFireCount++;
        lastFirePosition = position;
        latestEvent = "Lightning ignites a tree on a storm's dry edge";
        return true;
    }

    /** Frequent lightning in the saturated middle; this does not start fires. */
    private void wetStrike(int rows, int columns) {
        if (storms.isEmpty()) return;
        Storm storm = storms.get(random.nextInt(storms.size()));
        double angle = random.nextDouble() * Math.PI * 2.0;
        double reach = random.nextDouble() * storm.radius * 0.18;
        Position position = new Position(
                Math.max(0, Math.min(rows - 1, (int) Math.round(storm.row + Math.sin(angle) * reach))),
                Math.max(0, Math.min(columns - 1, (int) Math.round(storm.col + Math.cos(angle) * reach))));
        flashes.add(new Flash(position, false));
        lightningCount++;
    }

    public static double moistureProfile(double distance, double radius) {
        if (distance > radius) return 0.0;
        double fraction = distance / radius;
        if (fraction <= 0.25) return 1.0;
        if (fraction >= 0.9) return 0.15 + 0.05 * (1.0 - fraction) / 0.1;
        return 1.0 - 0.8 * (fraction - 0.25) / 0.65;
    }
    public double rainAt(int row, int col) {
        double rain = 0.0;
        for (Storm storm : storms) {
            rain = Math.max(rain, moistureProfile(Math.hypot(row - storm.row, col - storm.col), storm.radius));
        }
        return rain;
    }
    public void startGust() {
        double angle = random.nextDouble() * Math.PI * 2.0;
        windX = Math.cos(angle); windY = Math.sin(angle);
        gustStrength = 0.8 + random.nextDouble() * 0.7;
        gustRemaining = 7.0 + random.nextDouble() * 5.0;
        gustCount++;
        latestEvent = "A gust carries embers downwind";
    }
    public boolean isDrySpell(double time) { return time < dryUntil; }
    public boolean hasGust() { return gustRemaining > 0.0; }
    public double windX() { return windX; }
    public double windY() { return windY; }
    public double windStrength() { return hasGust() ? gustStrength : 0.0; }
    public int getLightningCount() { return lightningCount; }
    public int getLightningFireCount() { return lightningFireCount; }
    public int getStormCount() { return stormCount; }
    public int getGustCount() { return gustCount; }
    public String getLatestEvent() { return latestEvent; }
    public List<StormView> storms() {
        return storms.stream().map(Storm::view).collect(Collectors.toUnmodifiableList());
    }
    public List<FlashView> flashes() {
        return flashes.stream().map(flash -> new FlashView(flash.position, flash.age, flash.startedFire))
                .collect(Collectors.toUnmodifiableList());
    }
    public GustView gust() { return new GustView(windX, windY, windStrength(), gustRemaining); }
}
