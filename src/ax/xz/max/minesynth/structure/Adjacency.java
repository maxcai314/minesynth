package ax.xz.max.minesynth.structure;

/**
 * One of the six face-adjacent cells relative to a cell: the four horizontal
 * sides plus {@link #ABOVE} and {@link #BELOW}. Unlike {@link Direction} (the
 * horizontal block facings), this includes the vertical neighbors, so it is
 * the right vocabulary for "which neighboring cell" questions such as
 * {@link PlacementRule#canNeighbor}.
 */
public enum Adjacency {
	NORTH(0, 0, -1),
	EAST(1, 0, 0),
	SOUTH(0, 0, 1),
	WEST(-1, 0, 0),
	ABOVE(0, 1, 0),
	BELOW(0, -1, 0);

	private final Cell delta;

	Adjacency(int dx, int dy, int dz) {
		this.delta = new Cell(dx, dy, dz);
	}

	/** The neighbor cell on this side of {@code cell}. */
	public Cell neighbor(Cell cell) {
		return cell.plus(delta);
	}

	/** True for {@link #ABOVE} and {@link #BELOW}. */
	public boolean isVertical() {
		return this == ABOVE || this == BELOW;
	}
}
