import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Owns live mutable state. Only the model thread calls step(). */
public final class WorldSimulation implements AutoCloseable {
    public enum Startup { COMBINED, ARSONIST, LIGHTNING, BACKBURNING }
    private final Forest forest;
    private final Random random;
    private final WeatherSystem weather;
    private final FireModel fire = new FireModel();
    private final List<Actor> actors = new ArrayList<>();
    private final List<Ember> embers = new ArrayList<>();
    private final ExecutorService calculations;
    private final int initialTrees;
    private double time, nextWander;
    private int nextActorId;
    private String latestEvent = "The forest is ready";
    private boolean started;

    public WorldSimulation(Forest forest, long seed, boolean parallel) {
        this.forest = forest;
        random = new Random(seed);
        weather = new WeatherSystem(random);
        calculations = parallel ? Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "forest-calculation");
            thread.setDaemon(true);
            return thread;
        }) : null;
        initialTrees = (int) forest.livingTrees().count();
    }
    public Forest getForest() { return forest; }
    public WeatherSystem getWeather() { return weather; }
    public double getTime() { return time; }
    public List<Actor> getActors() { return List.copyOf(actors); }

    public void start() { start(Startup.COMBINED); }

    // Isolated modes remain available to regression tests. The app uses start().
    public void start(Startup startup) {
        if (started) throw new IllegalStateException("This world has already started");
        started = true;
        for (int i = 0; i < SimulationRules.ANIMAL_COUNT; i++) {
            AnimalSpecies species = AnimalSpecies.values()[i % AnimalSpecies.values().length];
            Position position = randomPosition();
            // Prefer a matching habitat, without making one mandatory on a small map.
            for (int attempt = 0; attempt < 30; attempt++) {
                Position candidate = randomPosition();
                if (species.prefers(cell(candidate).getBiome())) { position = candidate; break; }
            }
            actors.add(new Animal(nextActorId++, position, species));
        }
        for (int i = 0; i < SimulationRules.SPIRIT_COUNT; i++) {
            actors.add(new ForestSpirit(nextActorId++, randomPosition()));
        }
        switch (startup) {
            case COMBINED -> {
                spawnArsonists();
                weather.strike(forest);
                latestEvent = "An arsonist and a lightning storm arrive";
            }
            case ARSONIST -> spawnArsonists();
            case LIGHTNING -> weather.strike(forest);
            case BACKBURNING -> {
                try {
                    new Backburning(forest.getGrid().length / 2).ignite(forest);
                    latestEvent = "A controlled backburn starts along a row";
                } catch (IllegalStateException exception) { latestEvent = exception.getMessage(); }
            }
        }
        if (startup == Startup.LIGHTNING) latestEvent = weather.getLatestEvent();
        checkDeaths();
    }

    private void spawnArsonists() {
        for (int index = 0; index < SimulationRules.ARSONIST_COUNT; index++) {
            spawnArsonist(index);
        }
    }

    private void spawnArsonist(int index) {
        Position preferred = index == 0
                ? new Position(forest.getGrid().length / 2, forest.getGrid()[0].length / 2)
                : randomPosition();
        WorldSnapshot safety = new WorldSnapshot(forest);
        Cell target = forest.livingTrees()
                .filter(candidate -> !candidate.getTree().isBurning()
                        && candidate.getTree().getSpreadability() >= 0.5)
                .filter(candidate -> safety.fireDistance(
                        new Position(candidate.getX(), candidate.getY())) >= 4)
                .filter(candidate -> actors.stream().filter(actor -> actor.kind() == Actor.Kind.ARSONIST)
                        .noneMatch(actor -> actor.getPosition().distanceTo(
                                new Position(candidate.getX(), candidate.getY())) < 12.0))
                .min(Comparator.comparingDouble(candidate ->
                        new Position(candidate.getX(), candidate.getY()).distanceTo(preferred)))
                .orElse(null);
        if (target == null) { latestEvent = "No tree is suitable for an arson attack"; return; }
        Position treePosition = new Position(target.getX(), target.getY());
        Position standing = neighbours(treePosition).stream()
                .filter(p -> !p.equals(treePosition))
                .filter(p -> !cell(p).hasTree() || !cell(p).getTree().isBurning())
                .sorted(Comparator.comparingInt(p -> cell(p).hasTree() ? 1 : 0))
                .findFirst().orElse(null);
        if (standing == null) { latestEvent = "No safe position for the arsonist"; return; }
        Arsonist arsonist = new Arsonist(nextActorId++, standing);
        arsonist.setGoal(treePosition);
        actors.add(arsonist);
        arsonist.act(this);
    }

    public boolean tryArson(Arsonist actor) {
        Position position = actor.getPosition();
        if (neighbours(position).stream().anyMatch(p ->
                cell(p).hasTree() && cell(p).getTree().isBurning())) return false;
        Cell target = neighbours(position).stream()
                .filter(p -> !p.equals(position)).map(this::cell)
                .filter(candidate -> candidate.hasTree() && candidate.getTree().isAlive()
                        && !candidate.getTree().isBurning()
                        && candidate.getTree().getSpreadability() >= 0.5)
                .min(Comparator.comparingDouble(candidate ->
                        new Position(candidate.getX(), candidate.getY()).distanceTo(actor.view().goal())))
                .orElse(null);
        if (target == null) return false;
        new Arson(target.getX(), target.getY()).ignite(forest);
        latestEvent = "Arsonist starts another fire";
        return true;
    }

    public double nextArsonInterval() {
        return SimulationRules.ARSON_MIN_INTERVAL + random.nextDouble()
                * (SimulationRules.ARSON_MAX_INTERVAL - SimulationRules.ARSON_MIN_INTERVAL);
    }

    public Position chooseArsonGoal(Position from) {
        List<Position> candidates = forest.livingTrees()
                .filter(candidate -> !candidate.getTree().isBurning()
                        && candidate.getTree().getSpreadability() >= 0.5)
                .map(candidate -> new Position(candidate.getX(), candidate.getY()))
                .filter(p -> p.distanceTo(from) >= 8.0 && p.distanceTo(from) <= 18.0)
                .filter(p -> neighbours(p).stream().noneMatch(n ->
                        cell(n).hasTree() && cell(n).getTree().isBurning()))
                .collect(Collectors.toList());
        return candidates.isEmpty() ? randomPosition()
                : candidates.get(random.nextInt(candidates.size()));
    }

    public void step(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0.0 || seconds > 1.0) {
            throw new IllegalArgumentException("Step must be greater than zero and at most one second");
        }
        time += seconds;
        String previousWeather = weather.getLatestEvent();
        weather.advance(forest, time, seconds);
        if (!previousWeather.equals(weather.getLatestEvent())) latestEvent = weather.getLatestEvent();
        forest.advanceEnvironment(weather, time, seconds);
        checkDeaths();
        if (time >= nextWander) {
            for (Actor actor : actors) {
                if (actor.isAlive() && actor.kind() != Actor.Kind.ARSONIST) {
                    actor.setGoal(wanderGoal(actor));
                }
            }
            nextWander = time + 10.0;
        }
        for (Actor actor : actors) {
            if (!actor.isAlive()) continue;
            actor.prepareMovement(seconds);
            if (actor instanceof Arsonist arsonist) {
                Position goal = arsonist.view().goal();
                if (!cell(goal).hasTree() || cell(goal).getTree().isBurning()) {
                    arsonist.setGoal(chooseArsonGoal(arsonist.getPosition()));
                }
                arsonist.act(this);
            }
        }
        WorldSnapshot snapshot = new WorldSnapshot(forest);
        List<Actor.View> actorViews = actors.stream().filter(Actor::isAlive)
                .map(Actor::view).collect(Collectors.toUnmodifiableList());
        List<FireModel.HeatChange> heat;
        List<Actor.Move> moves;
        double windX = weather.windX(), windY = weather.windY();
        double windStrength = weather.windStrength();
        boolean drySpell = weather.isDrySpell(time);
        if (calculations == null) {
            heat = fire.plan(snapshot, seconds, windX, windY, windStrength, drySpell);
            moves = planMoves(actorViews, snapshot);
        } else {
            // Both jobs read exactly the same immutable snapshot. Neither can
            // mutate cells, actors, Random, Swing components, or shared lists.
            CompletableFuture<List<FireModel.HeatChange>> fireJob = CompletableFuture.supplyAsync(
                    () -> fire.plan(snapshot, seconds, windX, windY, windStrength, drySpell), calculations);
            CompletableFuture<List<Actor.Move>> movementJob = CompletableFuture.supplyAsync(
                    () -> planMoves(actorViews, snapshot), calculations);
            heat = fireJob.join();
            moves = movementJob.join();
        }
        // The model thread applies all proposals after both jobs finish.
        forest.applyHeat(heat);
        Map<Integer, Actor> byId = actors.stream()
                .collect(Collectors.toMap(Actor::getId, actor -> actor));
        for (Actor.Move move : moves) byId.get(move.actorId()).applyMove(move.destination());
        forest.consumeFuel(snapshot, seconds);
        checkDeaths();
        for (Actor actor : actors) {
            if (actor instanceof ForestSpirit spirit) spirit.act(forest, seconds);
        }
        advanceEmbers(snapshot, seconds);
        checkDeaths();
    }

    private List<Actor.Move> planMoves(List<Actor.View> views, WorldSnapshot snapshot) {
        return views.stream().map(view -> new Actor.Move(view.id(),
                view.movement().choosePosition(view, snapshot))).collect(Collectors.toList());
    }

    private void advanceEmbers(WorldSnapshot snapshot, double seconds) {
        if (weather.hasGust() && !snapshot.fires().isEmpty()) {
            double expected = Ember.emissionRate(snapshot, weather.windStrength()) * seconds;
            int count = (int) expected;
            if (random.nextDouble() < expected - count) count++;
            for (int i = 0; i < count; i++) {
                Position source = snapshot.fires().get(random.nextInt(snapshot.fires().size()));
                if (!cell(source).hasTree() || !cell(source).getTree().isBurning()) continue;
                double sideways = (random.nextDouble() - 0.5) * 0.8;
                double speed = 2.8 + random.nextDouble() * 1.5;
                embers.add(new Ember(source.row() + 0.5, source.col() + 0.5,
                        weather.windY() * speed + weather.windX() * sideways,
                        weather.windX() * speed - weather.windY() * sideways,
                        2.0 + random.nextDouble() * 2.5));
            }
        }
        embers.removeIf(ember -> !ember.advance(forest, seconds));
    }
    private void checkDeaths() {
        for (Actor actor : actors) {
            if (!actor.isAlive() || actor.kind() == Actor.Kind.SPIRIT) continue;
            Cell standing = cell(actor.getPosition());
            if (standing.hasTree() && standing.getTree().isBurning()) {
                actor.kill();
                if (actor.kind() == Actor.Kind.ARSONIST) latestEvent = "The arsonist was caught by fire";
            }
        }
    }
    private Position randomPosition() {
        return new Position(random.nextInt(forest.getGrid().length),
                random.nextInt(forest.getGrid()[0].length));
    }
    private Position wanderGoal(Actor actor) {
        Position current = actor.getPosition();
        Position result = current;
        for (int attempt = 0; attempt < 12; attempt++) {
            int row = Math.max(0, Math.min(forest.getGrid().length - 1,
                    current.row() + random.nextInt(21) - 10));
            int col = Math.max(0, Math.min(forest.getGrid()[0].length - 1,
                    current.col() + random.nextInt(21) - 10));
            result = new Position(row, col);
            if (actor.kind() != Actor.Kind.ANIMAL
                    || actor.view().animalSpecies().prefers(cell(result).getBiome())) break;
        }
        return result;
    }
    private Cell cell(Position p) { return forest.getGrid()[p.row()][p.col()]; }
    private List<Position> neighbours(Position p) {
        List<Position> result = new ArrayList<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int row = p.row() + dr, col = p.col() + dc;
                if (row >= 0 && col >= 0 && row < forest.getGrid().length
                        && col < forest.getGrid()[0].length) result.add(new Position(row, col));
            }
        }
        return result;
    }

    public WorldView view() {
        List<Actor.View> views = actors.stream().map(Actor::view)
                .collect(Collectors.toUnmodifiableList());
        long livingAnimals = views.stream().filter(actor -> actor.kind() == Actor.Kind.ANIMAL
                && actor.alive()).count();
        long deadAnimals = views.stream().filter(actor -> actor.kind() == Actor.Kind.ANIMAL
                && !actor.alive()).count();
        int arsonAttacks = actors.stream().filter(actor -> actor instanceof Arsonist)
                .mapToInt(actor -> ((Arsonist) actor).getAttacks()).sum();
        Map<Biome, Long> totals = forest.livingTrees().collect(Collectors.groupingBy(Cell::getBiome,
                () -> new EnumMap<>(Biome.class), Collectors.counting()));
        return new WorldView(new WorldSnapshot(forest), views, weather.storms(), weather.flashes(),
                weather.gust(), embers.stream().map(Ember::view).collect(Collectors.toUnmodifiableList()),
                time, initialTrees, totals.values().stream().mapToLong(Long::longValue).sum(),
                livingAnimals, deadAnimals, arsonAttacks, weather.getLightningCount(),
                weather.getLightningFireCount(), weather.isDrySpell(time), latestEvent,
                Map.copyOf(totals));
    }
    @Override public void close() {
        if (calculations != null) calculations.shutdownNow();
    }
}
