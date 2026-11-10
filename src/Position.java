public record Position(int row, int col) {
    public double distanceTo(Position other) {
        return Math.hypot(row - other.row, col - other.col);
    }
}
