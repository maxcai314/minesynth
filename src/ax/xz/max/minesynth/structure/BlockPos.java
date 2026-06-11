package ax.xz.max.minesynth.structure;

/**
 * A position in block units within a structure (or a block-unit extent).
 * Same axes as {@link Cell}: +x east, +y up, +z south.
 */
public record BlockPos(int x, int y, int z) {
	public BlockPos plus(BlockPos other) {
		return new BlockPos(x + other.x, y + other.y, z + other.z);
	}

	/** One block over in {@code direction} (same height). */
	public BlockPos offset(Direction direction) {
		return new BlockPos(x + direction.dx(), y, z + direction.dz());
	}

	public BlockPos below() {
		return new BlockPos(x, y - 1, z);
	}

	public BlockPos above() {
		return new BlockPos(x, y + 1, z);
	}

	/** The cell containing this block (floor division by {@link Cell#BLOCKS}). */
	public Cell cell() {
		return new Cell(Math.floorDiv(x, Cell.BLOCKS), Math.floorDiv(y, Cell.BLOCKS), Math.floorDiv(z, Cell.BLOCKS));
	}

	@Override
	public String toString() {
		return "BlockPos[" + x + ", " + y + ", " + z + "]";
	}
}
