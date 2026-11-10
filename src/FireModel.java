import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Pure heat calculations: this class never writes to the live forest. */
public final class FireModel {
    public record HeatChange(int index, double heat) { }

    public List<HeatChange> plan(WorldSnapshot world, double seconds,
                                 double windX, double windY, double windStrength,
                                 boolean drySpell) {
        return IntStream.range(0, world.size())
                .filter(index -> world.hasTree(index) && !world.burning(index))
                .mapToObj(index -> {
                    Position target = world.position(index);
                    double incoming = 0.0;
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            if (dr == 0 && dc == 0) continue;
                            int row = target.row() + dr, col = target.col() + dc;
                            if (!world.contains(row, col)) continue;
                            int source = world.index(row, col);
                            if (!world.burning(source)) continue;
                            double alignment = (-dc * windX - dr * windY)
                                    / Math.hypot(dr, dc);
                            double windFactor = Math.max(0.4,
                                    1.0 + alignment * windStrength * 0.65);
                            incoming += world.intensity(source) * windFactor;
                        }
                    }
                    double dryness = Math.pow(1.0 - world.moisture(index), 2);
                    double warming = incoming * SimulationRules.NEIGHBOUR_HEAT_PER_SECOND
                            * world.spreadability(index) * dryness * (drySpell ? 1.15 : 1.0);
                    double cooling = SimulationRules.HEAT_COOLING_PER_SECOND
                            * (1.0 + world.moisture(index) * 3.0);
                    return new HeatChange(index,
                            Math.max(0.0, world.heat(index) + (warming - cooling) * seconds));
                }).collect(Collectors.toList());
    }
}
