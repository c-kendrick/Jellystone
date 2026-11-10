import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BiomeForestGenerator implements ForestGenerator {
    private record Region(int id, int row, int col, Biome biome) {
        double distanceSquared(int targetRow, int targetCol) {
            double dr = targetRow - row, dc = targetCol - col;
            return dr * dr + dc * dc;
        }
    }

    @Override
    public Cell[][] generate(int rows, int columns, Random random) {
        Cell[][] grid = new Cell[rows][columns];
        List<Region> regions = createRegions(rows, columns, random);
        long boundarySeed = random.nextLong();
        double rowPhase = random.nextDouble() * Math.PI * 2;
        double colPhase = random.nextDouble() * Math.PI * 2;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                final int r = row, c = col;
                Region nearest = regions.stream().min(Comparator.comparingDouble(
                        region -> region.distanceSquared(r, c))).orElseThrow();
                Region second = regions.stream().filter(region -> region != nearest)
                        .min(Comparator.comparingDouble(region -> region.distanceSquared(r, c)))
                        .orElse(nearest);
                double boundaryDistance = (Math.sqrt(second.distanceSquared(row, col))
                        - Math.sqrt(nearest.distanceSquared(row, col))) / 2.0;
                int low = Math.min(nearest.id(), second.id());
                int high = Math.max(nearest.id(), second.id());
                long pairHash = mix(boundarySeed ^ (low * 104729L) ^ (high * 130363L));
                boolean chosenBorder = Math.floorMod(pairHash, 100) < 62;
                // Break up selected boundaries, leaving occasional wooded
                // connections. Other borders have no clearing at all.
                double segment = Math.sin(row / 7.0 + col / 9.0 + (pairHash & 255));
                boolean gap = nearest.biome() != second.biome() && chosenBorder
                        && segment > -0.35 && boundaryDistance < 1.6;
                Cell cell = new Cell(row, col);
                cell.setBiome(nearest.biome());
                cell.setRegionId(nearest.id());
                cell.setFirebreak(gap);
                cell.setMoisture(nearest.biome().getInitialMoisture());
                double variation = 0.18 * Math.sin(row / 5.0 + rowPhase)
                        * Math.cos(col / 6.0 + colPhase);
                double density = nearest.biome() == Biome.CLEARING || gap ? 0.0
                        : Math.max(0.0, Math.min(1.0,
                                nearest.biome().getTreeDensity() + variation));
                if (random.nextDouble() < density) cell.plantTree(nearest.biome().createTree(random));
                grid[row][col] = cell;
            }
        }
        return grid;
    }

    private List<Region> createRegions(int rows, int columns, Random random) {
        List<Integer> positions = IntStream.range(0, rows * columns).boxed()
                .collect(Collectors.toList());
        Collections.shuffle(positions, random);
        List<Biome> biomes = new ArrayList<>(Arrays.asList(Biome.values()));
        Collections.shuffle(biomes, random);
        int count = Math.min(positions.size(), Math.max(6, rows * columns / 300));
        return IntStream.range(0, count).mapToObj(index -> new Region(index,
                positions.get(index) / columns, positions.get(index) % columns,
                biomes.get(index % biomes.size()))).collect(Collectors.toList());
    }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
