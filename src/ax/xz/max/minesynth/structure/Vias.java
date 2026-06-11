package ax.xz.max.minesynth.structure;

import java.util.List;

/**
 * Generates vertical signal transport: a 1x1-cell column of the requested
 * height containing a clockwise dust-on-glass spiral around the 3x3 perimeter.
 * The spiral uses the full cell cross-section, so vias are never contained;
 * the placement rules keep two of them from sitting in adjacent cells.
 *
 * <p>Note on signal strength: dust loses one power per block, so tall vias
 * eat budget quickly; strength budgeting is a future analysis concern.
 */
public final class Vias {
	/** Perimeter ring of a 3x3 footprint, clockwise from the north face center. */
	private static final List<int[]> RING = List.of(
		new int[] {1, 0}, new int[] {2, 0}, new int[] {2, 1}, new int[] {2, 2},
		new int[] {1, 2}, new int[] {0, 2}, new int[] {0, 1}, new int[] {0, 0});

	private Vias() {}

	/**
	 * A via carrying a signal from {@code inFace} at the bottom cell up to
	 * {@code outFace} at the top cell. {@code heightCells} is at least 2.
	 */
	public static Structure upward(int heightCells, Direction inFace, Direction outFace) {
		Structure spiral = spiral(heightCells, inFace, outFace);
		return spiral; // spiral() already orders pins bottom-in, top-out
	}

	/**
	 * A via carrying a signal from {@code inFace} at the top cell down to
	 * {@code outFace} at the bottom cell. Dust is undirected, so this is the
	 * same staircase with the pin roles swapped.
	 */
	public static Structure downward(int heightCells, Direction inFace, Direction outFace) {
		Structure spiral = spiral(heightCells, outFace, inFace);
		return new Structure(spiral.size(), spiral.blocks(),
			spiral.outputs(), spiral.inputs(), spiral.contained());
	}

	/**
	 * Builds the staircase with input at the bottom {@code bottomFace} and
	 * output at the top {@code topFace}. The ring phase is adjusted with short
	 * constant-height walks at the bottom and top (each at most 4 steps) so
	 * the climb lands exactly on the top port without the spiral colliding
	 * with itself.
	 */
	private static Structure spiral(int heightCells, Direction bottomFace, Direction topFace) {
		if (heightCells < 2)
			throw new IllegalArgumentException("a via needs at least 2 cells of height, got " + heightCells
				+ " (use Wires for same-level transport)");

		int bottomIndex = ringIndex(bottomFace);
		int topIndex = ringIndex(topFace);
		int climbSteps = Cell.BLOCKS * (heightCells - 1); // dust y goes 1 .. 1+climbSteps
		int phase = Math.floorMod(topIndex - bottomIndex - climbSteps, RING.size());
		int bottomWalk = Math.min(phase, 3);
		int topWalk = phase - bottomWalk;

		Structure.Builder builder = new Structure.Builder(new Cell(1, heightCells, 1));
		int index = bottomIndex;
		int y = 1;
		step(builder, index, y); // bottom port dust
		for (int i = 0; i < bottomWalk; i++)
			step(builder, ++index, y);
		for (int i = 0; i < climbSteps; i++)
			step(builder, ++index, ++y);
		for (int i = 0; i < topWalk; i++)
			step(builder, ++index, y);

		return builder
			.contained(false)
			.input(new StructurePin(new Cell(0, 0, 0), bottomFace))
			.output(new StructurePin(new Cell(0, heightCells - 1, 0), topFace))
			.build();
	}

	/** Glass support plus dust at the given ring position and dust height. */
	private static void step(Structure.Builder builder, int ringIndex, int dustY) {
		int[] xz = RING.get(Math.floorMod(ringIndex, RING.size()));
		builder.placeBlock(new BlockPos(xz[0], dustY - 1, xz[1]), StructureBlock.GLASS);
		builder.placeBlock(new BlockPos(xz[0], dustY, xz[1]), StructureBlock.REDSTONE_DUST);
	}

	private static int ringIndex(Direction face) {
		return switch (face) {
			case NORTH -> 0;
			case EAST -> 2;
			case SOUTH -> 4;
			case WEST -> 6;
		};
	}
}
