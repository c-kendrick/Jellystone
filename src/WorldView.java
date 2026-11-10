import java.util.List;
import java.util.Map;

/** Everything the GUI needs, published only after a complete simulation step. */
public record WorldView(WorldSnapshot world, List<Actor.View> actors,
                        List<WeatherSystem.StormView> storms,
                        List<WeatherSystem.FlashView> flashes,
                        WeatherSystem.GustView gust, List<Ember.View> embers,
                        double time, int initialTrees, long livingTrees,
                        long livingAnimals, long deadAnimals, int arsonAttacks,
                        int lightningStrikes, int lightningFires,
                        boolean drySpell, String latestEvent,
                        Map<Biome, Long> treesByBiome) { }
