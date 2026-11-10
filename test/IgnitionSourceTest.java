public class IgnitionSourceTest {

    public static void main(String[] args) {
        testArsonDefaults();
        testArsonIgnitesTargetCell();
        testArsonDoesNotIgniteLowSpreadabilityTree();
        testLightningDefaults();
        testLightningIgnitesRandomCell();
        testBackburningDefaults();
        testBackburningIgnitesWaveColumns();
        testRejectsInvalidSeverity();
        testRejectsInvalidSpreadability();
        testIgnitionSourcePolymorphism();
        testArsonSearchesNearestSuitableTree();
        testArsonReportsNoSuitableTree();
        testLightningFindsSparseTargets();
        testBackburningUsesNearestPopulatedRow();
        testEmptyForestsReportFailure();
        testInvalidCoordinates();
        
        System.out.println("All IgnitionSource tests passed.");
    }

    private static void testArsonDefaults() {
        Arson arson = new Arson(0, 0);

        check(
                arson.severity == 0.55,
                "Arson should use severity 0.55"
        );
        check(
                arson.spreadability == 0.5,
                "Arson should use spreadability 0.5"
        );
    }

    private static void testArsonIgnitesTargetCell() {
        Forest forest = new Forest(2, 2);
        forest.getGrid()[0][0].plantTree(new Tree(0.9));

        Arson arson = new Arson(0, 0);
        arson.ignite(forest);

        check(
                forest.getGrid()[0][0].getTree().isBurning(),
                "Arson should ignite the tree in its target cell"
        );
    }

    private static void testArsonDoesNotIgniteLowSpreadabilityTree() {
        Forest forest = new Forest(2, 2);
        forest.getGrid()[0][0].plantTree(new Tree(0.1));
        forest.getGrid()[1][1].plantTree(new Tree(0.9));

        Arson arson = new Arson(0, 0);
        arson.ignite(forest);

        check(
                !forest.getGrid()[0][0].getTree().isBurning(),
                "Arson should not ignite a tree below its flammability threshold"
        );
    }

    private static void testLightningDefaults() {
        Lightning lightning = new Lightning();

        check(
                lightning.severity == 0.8,
                "Lightning should use severity 0.8"
        );
        check(
                lightning.spreadability == 0.7,
                "Lightning should use spreadability 0.7"
        );
    }

    private static void testLightningIgnitesRandomCell() {
        Forest forest = new Forest(1, 1);
        forest.getGrid()[0][0].plantTree(new Tree(0.9));

        Lightning lightning = new Lightning();
        lightning.ignite(forest);

        check(
                forest.getGrid()[0][0].getTree().isBurning(),
                "Lightning should ignite a tree in the forest"
        );
    }

    private static void testBackburningDefaults() {
        Backburning backburning = new Backburning(0);

        check(
                backburning.severity == 0.7,
                "Backburning should use severity 0.7"
        );
        check(
                backburning.spreadability == 0.4,
                "Backburning should use spreadability 0.4"
        );
    }

    private static void testBackburningIgnitesWaveColumns() {
        Forest forest = new Forest(1, 6);

        for (int col = 0; col < 6; col++) {
            forest.getGrid()[0][col].plantTree(new Tree(0.9));
        }

        Backburning backburning = new Backburning(0);
        backburning.ignite(forest);

        // With a 3-column wavelength, only the wave troughs (columns 2
        // and 5) fall below the 0.4 threshold and ignite immediately.
        int[] troughColumns = {2, 5};
        int[] crestColumns = {0, 1, 3, 4};

        for (int col : troughColumns) {
            check(
                    forest.getGrid()[0][col].getTree().isBurning(),
                    "Backburning should ignite every tree in the selected row"
            );
        }
        for (int col : crestColumns) {
            check(!forest.getGrid()[0][col].getTree().isBurning(),
                    "Backburning should preserve its initial wave pattern");
        }
    }

    private static Forest emptyForest(int rows, int columns) {
        Forest forest = new Forest(rows, columns);
        for (Cell[] cells : forest.getGrid()) {
            for (Cell cell : cells) {
                cell.removeTree();
            }
        }
        return forest;
    }

    private static void testArsonSearchesNearestSuitableTree() {
        Forest forest = emptyForest(4, 4);
        Tree unsuitable = new Tree(0.1);
        Tree nearest = new Tree(0.9);
        Tree farther = new Tree(0.9);
        forest.getGrid()[0][0].plantTree(unsuitable);
        forest.getGrid()[0][1].plantTree(nearest);
        forest.getGrid()[3][3].plantTree(farther);

        new Arson(0, 0).ignite(forest);
        check(nearest.isBurning(), "Arson should find the nearest suitable tree");
        check(!unsuitable.isBurning(), "Arson should respect its threshold");
        check(!farther.isBurning(), "Arson should start only one fire");

        Forest trailingEmptyCells = emptyForest(3, 1);
        trailingEmptyCells.getGrid()[0][0].plantTree(new Tree(0.9));
        new Arson(2, 0).ignite(trailingEmptyCells);
        check(trailingEmptyCells.getGrid()[0][0].getTree().isBurning(),
                "Arson should search safely from the final empty row");
    }

    private static void testArsonReportsNoSuitableTree() {
        Forest forest = emptyForest(1, 1);
        forest.getGrid()[0][0].plantTree(new Tree(0.1));
        expectFailure(IllegalStateException.class,
                () -> new Arson(0, 0).ignite(forest));
    }

    private static void testLightningFindsSparseTargets() {
        Forest forest = emptyForest(50, 50);
        Tree onlyTree = new Tree(0.1);
        Cell target = forest.getGrid()[49][49];
        target.plantTree(onlyTree);
        Lightning lightning = new Lightning();

        lightning.ignite(forest);
        check(onlyTree.isBurning(), "Lightning should find a single tree in a sparse forest");
        check(lightning.getLastStruckCell() == target,
                "Lightning should retain its target for the display");

        onlyTree.extinguish();
        expectFailure(IllegalStateException.class, () -> lightning.ignite(forest));
        Tree anotherTree = new Tree(0.9);
        forest.getGrid()[0][0].plantTree(anotherTree);
        lightning.ignite(forest);
        check(anotherTree.isBurning(), "Lightning should choose a new target on reuse");
    }

    private static void testBackburningUsesNearestPopulatedRow() {
        Forest forest = emptyForest(5, 1);
        Tree nearest = new Tree(0.1);
        Tree farther = new Tree(0.9);
        forest.getGrid()[1][0].plantTree(nearest);
        forest.getGrid()[4][0].plantTree(farther);

        new Backburning(2).ignite(forest);
        check(nearest.isBurning(), "Backburning should handle an empty selected row");
        check(!farther.isBurning(), "Backburning should stay within one row");
    }

    private static void testEmptyForestsReportFailure() {
        IgnitionSource[] sources = {
                new Arson(0, 0), new Lightning(), new Backburning(0)
        };
        for (IgnitionSource source : sources) {
            Forest forest = emptyForest(2, 2);
            expectFailure(IllegalStateException.class, () -> source.ignite(forest));

            Tree dead = new Tree(0.9);
            dead.kill();
            forest.getGrid()[0][0].plantTree(dead);
            Tree alreadyBurning = new Tree(0.9);
            alreadyBurning.ignite();
            forest.getGrid()[1][1].plantTree(alreadyBurning);
            expectFailure(IllegalStateException.class, () -> source.ignite(forest));
        }
    }

    private static void testInvalidCoordinates() {
        Forest forest = emptyForest(2, 2);
        expectFailure(IllegalArgumentException.class,
                () -> new Arson(-1, 0).ignite(forest));
        expectFailure(IllegalArgumentException.class,
                () -> new Arson(0, 2).ignite(forest));
        expectFailure(IllegalArgumentException.class,
                () -> new Backburning(2).ignite(forest));
    }

    private static void expectFailure(Class<? extends RuntimeException> type,
                                      Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            check(type.isInstance(exception), "Unexpected failure: " + exception);
            check(exception.getMessage() != null && !exception.getMessage().isEmpty(),
                    "Failure should explain what happened");
            return;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

private static void testIgnitionSourcePolymorphism() {
    IgnitionSource[] sources = {
            new Arson(0, 0),
            new Lightning(),
            new Backburning(0)
    };

    for (IgnitionSource source : sources) {
        Forest forest = new Forest(1, 1);
        forest.getGrid()[0][0].plantTree(new Tree(0.9));

        source.ignite(forest);

        check(
                forest.getGrid()[0][0].getTree().isBurning(),
                source.getClass().getSimpleName()
                        + " should ignite through an IgnitionSource reference"
        );
    }
}

private static void testRejectsInvalidSeverity() {
    boolean exceptionThrown = false;

    try {
        new TestIgnitionSource(1.1, 0.5);
    } catch (IllegalArgumentException exception) {
        exceptionThrown = true;
    }

    check(
            exceptionThrown,
            "Severity above 1.0 should throw an exception"
    );
}

private static void testRejectsInvalidSpreadability() {
    boolean exceptionThrown = false;

    try {
        new TestIgnitionSource(0.5, -0.1);
    } catch (IllegalArgumentException exception) {
        exceptionThrown = true;
    }

    check(
            exceptionThrown,
            "Spreadability below 0.0 should throw an exception"
    );
}

private static class TestIgnitionSource extends IgnitionSource {
    TestIgnitionSource(double severity, double spreadability) {
        super(severity, spreadability);
    }

    @Override
    public void ignite(Forest forest) {
        // No ignition behaviour is needed for constructor validation.
    }
}
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
