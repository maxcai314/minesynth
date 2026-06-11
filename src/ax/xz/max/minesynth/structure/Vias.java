package ax.xz.max.minesynth.structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates vertical signal transport: a 1x1-cell column of the requested
 * height containing a clockwise dust-on-wool spiral around the 3x3 perimeter.
 * The spiral uses the full cell cross-section, so vias are never contained;
 * the placement rules keep two of them from sitting in adjacent cells.
 *
 * <p>Supports are wool, not glass: glass passes redstone upward but not
 * downward, which would break descending signals. Opaque wool carries both
 * directions safely here because the spiral never puts a block directly above
 * a dust (the same column is only revisited a full ring later, at least four
 * blocks higher), so no diagonal connection is ever cut.
 *
 * <p>Long spirals refresh themselves: whenever the dust run since the last
 * refresh reaches {@link #REFRESH_RUN_LIMIT}, the spiral pauses climbing for a
 * two-step flat segment holding a repeater (repeaters cannot sit on the
 * diagonal, but ring positions at face centers run straight, so a repeater
 * fits there). Repeaters face along the signal, which makes vias directional;
 * {@link #upward} and {@link #downward} bake the correct facings in.
 */
public final class Vias {
	/** Perimeter ring of a 3x3 footprint, clockwise from the north face center. */
	private static final List<int[]> RING = List.of(
		new int[] {1, 0}, new int[] {2, 0}, new int[] {2, 1}, new int[] {2, 2},
		new int[] {1, 2}, new int[] {0, 2}, new int[] {0, 1}, new int[] {0, 0});

	/**
	 * Maximum dust run inside a via before a repeater is inserted. Worst-case
	 * segments stay around a dozen blocks, well inside redstone's 15.
	 */
	private static final int REFRESH_RUN_LIMIT = 6;

	private Vias() {}

	/** One position of the planned spiral: where, how high, and what carries the signal. */
	private record PathStep(int ringIndex, int dustY, boolean repeater) {}

	/**
	 * A via carrying a signal from {@code inFace} at the bottom cell up to
	 * {@code outFace} at the top cell. {@code heightCells} is at least 2.
	 */
	public static Structure upward(int heightCells, Direction inFace, Direction outFace) {
		return spiral(heightCells, inFace, outFace, true);
	}

	/**
	 * A via carrying a signal from {@code inFace} at the top cell down to
	 * {@code outFace} at the bottom cell: the same staircase with the pin
	 * roles swapped and every repeater facing down-spiral instead.
	 */
	public static Structure downward(int heightCells, Direction inFace, Direction outFace) {
		Structure spiral = spiral(heightCells, outFace, inFace, false);
		return new Structure(spiral.size(), spiral.blocks(),
			spiral.outputs(), spiral.inputs(), spiral.contained());
	}

	/**
	 * Builds the staircase with its bottom port on {@code bottomFace} and top
	 * port on {@code topFace}. Constant-height walks at the bottom and top
	 * adjust the ring phase so the climb (whose length the refresh segments
	 * extend) lands exactly on the top port without the spiral colliding with
	 * itself; the walk bounds keep every self-collision case out of reach.
	 */
	private static Structure spiral(int heightCells, Direction bottomFace, Direction topFace, boolean signalUpward) {
		if (heightCells < 2)
			throw new IllegalArgumentException("a via needs at least 2 cells of height, got " + heightCells
				+ " (use Wires for same-level transport)");

		int bottomIndex = ringIndex(bottomFace);
		int topIndex = ringIndex(topFace);
		int climbSteps = Cell.BLOCKS * (heightCells - 1); // dust y goes 1 .. 1+climbSteps
		int topY = climbSteps + 1;

		// refresh segments shift the ring phase, so candidate bottom walks are
		// simulated until the top walk is short enough; a candidate whose
		// spiral would overlap itself fails to build and the next one is tried
		for (int bottomWalkCandidate = 0; bottomWalkCandidate <= 5; bottomWalkCandidate++) {
			List<PathStep> path = simulatePath(bottomIndex, bottomWalkCandidate, climbSteps);
			int topWalk = Math.floorMod(topIndex - path.getLast().ringIndex(), RING.size());
			if (topWalk > 5)
				continue;
			int index = path.getLast().ringIndex();
			for (int i = 0; i < topWalk; i++)
				path.add(new PathStep(++index, topY, false));
			try {
				return assemble(path, heightCells, bottomFace, topFace, signalUpward);
			} catch (IllegalArgumentException selfOverlap) {
				// rare wrap collision for this phase; the next candidate avoids it
			}
		}
		throw new IllegalStateException("via ring phase did not resolve for height " + heightCells
			+ " " + bottomFace + " to " + topFace + "; this is a bug in the spiral planner");
	}

	private static Structure assemble(List<PathStep> path, int heightCells,
			Direction bottomFace, Direction topFace, boolean signalUpward) {
		Structure.Builder builder = new Structure.Builder(new Cell(1, heightCells, 1));
		for (PathStep step : path) {
			int[] xz = RING.get(Math.floorMod(step.ringIndex(), RING.size()));
			BlockPos support = new BlockPos(xz[0], step.dustY() - 1, xz[1]);
			builder.placeBlock(support, StructureBlock.WOOL);
			if (step.repeater()) {
				Direction travel = travelDirection(step.ringIndex());
				builder.placeBlock(support.above(),
					new StructureBlock.Repeater(signalUpward ? travel : travel.opposite(), 1));
			} else {
				builder.placeBlock(support.above(), StructureBlock.REDSTONE_DUST);
			}
		}

		return builder
			.contained(false)
			.input(new StructurePin(new Cell(0, 0, 0), bottomFace))
			.output(new StructurePin(new Cell(0, heightCells - 1, 0), topFace))
			.build();
	}

	/**
	 * Plans port, bottom walk, and climb, inserting a flat repeater-plus-dust
	 * refresh whenever the dust run gets long and the next ring position is a
	 * face center (where travel runs straight, so a repeater fits).
	 *
	 * <p>Refreshes stay out of the last two climbs, keeping every refresh row
	 * at least three blocks below the top; otherwise the top walk's wool
	 * supports could sit directly above the row's dust and cut the spiral.
	 * Because of that keep-clear zone, the need is anticipated: in the last
	 * two permitted slots a refresh is inserted even for a moderate run, so
	 * the final stretch (tail climbs plus top walk) never grows too long.
	 */
	private static List<PathStep> simulatePath(int bottomIndex, int bottomWalk, int climbSteps) {
		List<PathStep> path = new ArrayList<>();
		int index = bottomIndex;
		int y = 1;
		int run = 1;
		path.add(new PathStep(index, y, false)); // bottom port dust
		for (int i = 0; i < bottomWalk; i++) {
			path.add(new PathStep(++index, y, false));
			run++;
		}
		for (int climbsLeft = climbSteps; climbsLeft > 0; climbsLeft--) {
			boolean lastChance = climbsLeft <= 4;
			boolean wanted = run >= REFRESH_RUN_LIMIT || (lastChance && run >= 3);
			if (climbsLeft > 2 && wanted && isFaceCenter(index + 1)) {
				path.add(new PathStep(++index, y, true));
				path.add(new PathStep(++index, y, false));
				run = 1;
			}
			path.add(new PathStep(++index, ++y, false));
			run++;
		}
		return path;
	}

	/** The direction of clockwise ring travel arriving at {@code index}. */
	private static Direction travelDirection(int index) {
		int[] from = RING.get(Math.floorMod(index - 1, RING.size()));
		int[] to = RING.get(Math.floorMod(index, RING.size()));
		int dx = to[0] - from[0];
		int dz = to[1] - from[1];
		for (Direction direction : Direction.values())
			if (direction.dx() == dx && direction.dz() == dz)
				return direction;
		throw new IllegalStateException("ring positions are not adjacent");
	}

	/** Face-center ring positions (indexes 0, 2, 4, 6) have straight-through travel. */
	private static boolean isFaceCenter(int index) {
		return Math.floorMod(index, 2) == 0;
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
