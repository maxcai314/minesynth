package ax.xz.max.minesynth.structure;

/**
 * A port of a structure: a cell and the cardinal face signals cross there.
 * Pins are declared relative to the structure's origin in its default (NORTH)
 * orientation and must lie on the structure's boundary in that face's
 * direction.
 *
 * <p>Named StructurePin to avoid confusion with the netlist's Pin type.
 */
public record StructurePin(Cell cell, Direction face) {
	/**
	 * The block this pin connects through: the center block of the cell face
	 * at middle height. By convention the structure places redstone dust here
	 * (supported from below inside the structure); the neighboring cell's
	 * facing port block is directly adjacent, so dust connects across.
	 */
	public BlockPos connectionBlock() {
		BlockPos cellCenter = cell.blockOrigin().plus(new BlockPos(1, 1, 1));
		return cellCenter.offset(face);
	}

	@Override
	public String toString() {
		return "StructurePin[" + cell + " " + face + "]";
	}
}
