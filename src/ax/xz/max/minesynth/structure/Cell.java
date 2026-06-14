package ax.xz.max.minesynth.structure;

/**
 * A position or extent measured in cell units (one cell = {@link #BLOCKS}
 * cubed blocks). As a size, x is the east-west width, y the height, and z the
 * north-south length, all strictly positive by convention.
 */
public record Cell(int x, int y, int z) {
	/** Edge length of one cell in blocks. */
	public static final int BLOCKS = 3;

	public Cell plus(Cell other) {
		return new Cell(x + other.x, y + other.y, z + other.z);
	}

	/** This cell shifted {@code cells} steps in {@code direction}. */
	public Cell plus(Direction direction, int cells) {
		return new Cell(x + direction.dx() * cells, y, z + direction.dz() * cells);
	}

	/** This cell's column at a different height (same x and z). */
	public Cell atHeight(int height) {
		return new Cell(x, height, z);
	}

	/** The block position of this cell's lowest, northwesternmost corner. */
	public BlockPos blockOrigin() {
		return new BlockPos(x * BLOCKS, y * BLOCKS, z * BLOCKS);
	}

	@Override
	public String toString() {
		return "Cell[" + x + ", " + y + ", " + z + "]";
	}
}
