import java.util.Random;

public interface ForestGenerator {
    Cell[][] generate(int rows, int columns, Random random);
}
