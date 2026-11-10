import java.util.Random;

// The original uniform forest, retained as an alternative generation strategy.
public class RandomForestGenerator implements ForestGenerator {
    @Override
    public Cell[][] generate(int rows, int columns, Random random) {
        Cell[][] grid = new Cell[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                Cell cell = new Cell(row, col);
                cell.setBiome(Biome.WOODLAND);
                cell.setMoisture(Biome.WOODLAND.getInitialMoisture());
                if (random.nextDouble() < 0.8) {
                    cell.plantTree(TreeSpecies.OAK.createTree(random));
                }
                grid[row][col] = cell;
            }
        }
        return grid;
    }
}
