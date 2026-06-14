package ax.xz.max.minesynth.structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates vertical signal transport: a 1x1-cell column containing a
 * clockwise dust-on-wool spiral around the 3x3 perimeter. Plain vias carry a
 * signal between two levels; junction vias additionally tap the signal out at
 * several levels on the way. The spiral uses the full cell cross-section, so
 * vias are never contained; the placement rules keep two of them from sitting
 * in adjacent cells.
 *
 * <p>Supports are wool, not glass: glass passes redstone upward but not
 * downward, which would break descending signals. Opaque wool carries both
 * directions safely here because the spiral never puts a block directly above
 * a dust.
 *
 * <p>Long spirals refresh themselves: whenever the dust run since the last
 * refresh reaches {@link #REFRESH_RUN_LIMIT}, the spiral pauses climbing for a
 * two-step flat segment holding a repeater (repeaters cannot sit on the
 * diagonal, but ring positions at face centers run straight, so a repeater
 * fits there). Repeaters face along the signal, which makes vias directional.
 * Refresh rows keep clear of the last two climbs before every waypoint so the
 * waypoint walk's wool can never sit directly above (and cut) spiral dust.
 *
 * <p>Signal statistics are computed from the planned path per the
 * {@link SignalStats} contract and set on the built structure; delay tallies
 * the internal repeaters.
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

	/** A waypoint the spiral must pass through: a port block position. */
	private record Waypoint(int ringIndex, int dustY) {}

	/**
	 * A via carrying a signal from {@code inFace} at the bottom cell up to
	 * {@code outFace} at the top cell. {@code heightCells} is at least 2.
	 */
	public static Structure upward(int heightCells, Direction inFace, Direction outFace) {
		requireHeight(heightCells);
		return upwardJunction(inFace, List.of(new ViaTap(heightCells - 1, outFace)));
	}

	/**
	 * A via carrying a signal from {@code inFace} at the top cell down to
	 * {@code outFace} at the bottom cell: the same staircase with every
	 * repeater facing down-spiral instead.
	 */
	public static Structure downward(int heightCells, Direction inFace, Direction outFace) {
		requireHeight(heightCells);
		return downwardJunction(heightCells - 1, inFace, List.of(new ViaTap(0, outFace)));
	}

	/**
	 * A junction via fed at the bottom cell through {@code inFace}, tapping
	 * the signal out at every listed level on the way up. Tap levels are
	 * strictly ascending and at least 1; the structure is as tall as the
	 * highest tap plus one.
	 */
	public static Structure upwardJunction(Direction inFace, List<ViaTap> taps) {
		validateTaps(taps, 1, Integer.MAX_VALUE);
		List<Waypoint> waypoints = new ArrayList<>();
		waypoints.add(waypoint(0, inFace));
		List<StructurePin> outputs = new ArrayList<>();
		for (ViaTap tap : taps) {
			waypoints.add(waypoint(tap.level(), tap.face()));
			outputs.add(new StructurePin(new Cell(0, tap.level(), 0), tap.face()));
		}
		StructurePin input = new StructurePin(new Cell(0, 0, 0), inFace);
		return build(waypoints, taps.getLast().level() + 1, input, outputs, true);
	}

	/**
	 * A junction via fed at cell level {@code inputLevel} through
	 * {@code inFace}, tapping the signal out at every listed level on the way
	 * down. Tap levels are strictly ascending and below the input level; the
	 * structure is {@code inputLevel + 1} cells tall.
	 */
	public static Structure downwardJunction(int inputLevel, Direction inFace, List<ViaTap> taps) {
		if (inputLevel < 1)
			throw new IllegalArgumentException("a downward junction needs its input at level 1 or higher");
		validateTaps(taps, 0, inputLevel - 1);
		List<Waypoint> waypoints = new ArrayList<>();
		List<StructurePin> outputs = new ArrayList<>();
		for (ViaTap tap : taps) {
			waypoints.add(waypoint(tap.level(), tap.face()));
			outputs.add(new StructurePin(new Cell(0, tap.level(), 0), tap.face()));
		}
		waypoints.add(waypoint(inputLevel, inFace));
		StructurePin input = new StructurePin(new Cell(0, inputLevel, 0), inFace);
		return build(waypoints, inputLevel + 1, input, outputs, false);
	}

	// ---- planning ----

	/**
	 * Plans and assembles the spiral through the ascending waypoints. The
	 * first segment's starting walk candidate shifts on each retry attempt;
	 * a candidate whose spiral would overlap itself fails to build and the
	 * next attempt is tried.
	 */
	private static Structure build(List<Waypoint> waypoints, int heightCells,
			StructurePin input, List<StructurePin> outputs, boolean signalUpward) {
		for (int attempt = 0; attempt <= 5; attempt++) {
			List<PathStep> path = plan(waypoints, attempt);
			if (path == null)
				continue;
			try {
				return assemble(path, waypoints, heightCells, input, outputs, signalUpward);
			} catch (IllegalArgumentException selfOverlap) {
				// rare wrap collision for this phase; the next attempt avoids it
			}
		}
		throw new IllegalStateException("via ring phase did not resolve for waypoints " + waypoints
			+ "; this is a bug in the spiral planner");
	}

	/** Plans the full path, or returns null if some segment's phase cannot resolve. */
	private static List<PathStep> plan(List<Waypoint> waypoints, int firstSegmentBwStart) {
		List<PathStep> path = new ArrayList<>();
		Waypoint start = waypoints.getFirst();
		path.add(new PathStep(start.ringIndex(), start.dustY(), false));
		int index = start.ringIndex();
		int y = start.dustY();
		int run = 1;
		int previousTopWalk = 0;

		for (int i = 1; i < waypoints.size(); i++) {
			Waypoint target = waypoints.get(i);
			int climbSteps = target.dustY() - y;
			int bwStart = i == 1 ? firstSegmentBwStart : 0;
			// a waypoint shelf (previous walk plus this segment's starting walk)
			// may span at most 5 flats, so spiral revisits always climb enough
			// to keep their support wool off the dust below
			int bwLimit = Math.min(5, 5 - previousTopWalk);

			SegmentResult chosen = null;
			int chosenTopWalk = Integer.MAX_VALUE;
			for (int bw = bwStart; bw <= bwLimit; bw++) {
				SegmentResult candidate = runSegment(index, y, run, bw, climbSteps);
				int topWalk = Math.floorMod(target.ringIndex() - candidate.endIndex(), RING.size());
				if (topWalk < chosenTopWalk) {
					chosen = candidate;
					chosenTopWalk = topWalk;
				}
			}
			if (chosen == null || chosenTopWalk > 5)
				return null;
			index = chosen.endIndex();
			y = chosen.endY();
			run = chosen.endRun();

			path.addAll(chosen.steps());
			for (int w = 0; w < chosenTopWalk; w++) {
				path.add(new PathStep(++index, y, false));
				run++;
			}
			previousTopWalk = chosenTopWalk;
		}
		return path;
	}

	private record SegmentResult(List<PathStep> steps, int endIndex, int endY, int endRun) {}

	/**
	 * One segment: a starting flat walk, then the climb with refresh
	 * insertion. Refreshes keep out of the first two and last two climbs of
	 * the segment (so the waypoint shelves on either side can never put their
	 * wool directly above a refresh row's dust) and fire early in the last
	 * permitted slots when the run is already moderate.
	 */
	private static SegmentResult runSegment(int startIndex, int startY, int startRun, int bottomWalk, int climbSteps) {
		List<PathStep> steps = new ArrayList<>();
		int index = startIndex;
		int y = startY;
		int run = startRun;
		for (int i = 0; i < bottomWalk; i++) {
			steps.add(new PathStep(++index, y, false));
			run++;
		}
		for (int climbsLeft = climbSteps; climbsLeft > 0; climbsLeft--) {
			int climbsDone = climbSteps - climbsLeft;
			boolean lastChance = climbsLeft <= 4;
			boolean wanted = run >= REFRESH_RUN_LIMIT || (lastChance && run >= 3);
			if (climbsDone >= 2 && climbsLeft > 2 && wanted && isFaceCenter(index + 1)) {
				steps.add(new PathStep(++index, y, true));
				steps.add(new PathStep(++index, y, false));
				run = 1;
			}
			steps.add(new PathStep(++index, ++y, false));
			run++;
		}
		return new SegmentResult(steps, index, y, run);
	}

	// ---- assembly ----

	private static Structure assemble(List<PathStep> path, List<Waypoint> waypoints, int heightCells,
			StructurePin input, List<StructurePin> outputs, boolean signalUpward) {
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

		SignalStats stats = computeStats(path, waypoints, signalUpward);
		builder.horizontallyContained(false)
			.inputSignal(stats.inputSignal())
			.outputSignal(stats.outputSignal())
			.delayTicks(stats.delayTicks())
			.input(input);
		for (StructurePin output : outputs)
			builder.output(output);
		return builder.build();
	}

	/**
	 * Computes the conservative stats by walking the path in signal order.
	 * Output is the worst guarantee across taps: 15 minus the tail behind the
	 * last repeater for post-repeater taps, or relative dust loss when the
	 * spiral has no repeaters at all.
	 */
	private static SignalStats computeStats(List<PathStep> path, List<Waypoint> waypoints, boolean signalUpward) {
		List<PathStep> signalPath = new ArrayList<>(path);
		List<Waypoint> outputWaypoints = new ArrayList<>(
			signalUpward ? waypoints.subList(1, waypoints.size()) : waypoints.subList(0, waypoints.size() - 1));
		if (!signalUpward)
			java.util.Collections.reverse(signalPath);

		int repeaters = 0;
		int dustsSinceStart = 0;
		int dustsSinceRepeater = 0;
		int prefix = -1; // dusts before the first repeater
		int worstAbsolute = Integer.MAX_VALUE;
		int worstRelative = 0;
		int preTapNeed = 0;

		for (PathStep step : signalPath) {
			if (step.repeater()) {
				if (repeaters == 0)
					prefix = dustsSinceStart;
				repeaters++;
				dustsSinceRepeater = 0;
				continue;
			}
			dustsSinceStart++;
			dustsSinceRepeater++;
			Waypoint here = new Waypoint(Math.floorMod(step.ringIndex(), RING.size()), step.dustY());
			if (outputWaypoints.contains(here)) {
				if (repeaters > 0)
					worstAbsolute = Math.min(worstAbsolute, 15 - dustsSinceRepeater);
				else {
					worstRelative = Math.max(worstRelative, dustsSinceStart);
					preTapNeed = Math.max(preTapNeed, dustsSinceStart + 1);
				}
				outputWaypoints.remove(here);
			}
		}

		if (repeaters == 0) {
			int in = Math.min(15, Math.max(dustsSinceStart, preTapNeed));
			return new SignalStats(in, -worstRelative, 0);
		}
		int in = Math.min(15, Math.max(prefix, preTapNeed));
		int out = worstAbsolute == Integer.MAX_VALUE
			? 15 - dustsSinceRepeater // no post-repeater tap; conservative end-of-path value
			: Math.min(worstAbsolute, preTapNeed > 0 ? in - (preTapNeed - 1) : worstAbsolute);
		return new SignalStats(in, out, repeaters);
	}

	// ---- helpers ----

	private static Waypoint waypoint(int level, Direction face) {
		return new Waypoint(ringIndex(face), level * Cell.BLOCKS + 1);
	}

	private static void validateTaps(List<ViaTap> taps, int minLevel, int maxLevel) {
		if (taps.isEmpty())
			throw new IllegalArgumentException("a junction via needs at least one tap");
		int previous = Integer.MIN_VALUE;
		for (ViaTap tap : taps) {
			if (tap.level() < minLevel || tap.level() > maxLevel)
				throw new IllegalArgumentException(tap + " is outside levels " + minLevel + ".." + maxLevel);
			if (tap.level() <= previous)
				throw new IllegalArgumentException("tap levels must be strictly ascending, got " + taps);
			previous = tap.level();
		}
	}

	private static void requireHeight(int heightCells) {
		if (heightCells < 2)
			throw new IllegalArgumentException("a via needs at least 2 cells of height, got " + heightCells
				+ " (use Wires for same-level transport)");
	}

	/** The direction of clockwise ring travel arriving at {@code index}. */
	private static Direction travelDirection(int index) {
		int[] from = RING.get(Math.floorMod(index - 1, RING.size()));
		int[] to = RING.get(Math.floorMod(index, RING.size()));
		return Direction.fromDelta(to[0] - from[0], to[1] - from[1]);
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
