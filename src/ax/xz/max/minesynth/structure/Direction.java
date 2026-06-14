package ax.xz.max.minesynth.structure;

/**
 * A cardinal direction in the horizontal plane, matching Minecraft's compass:
 * NORTH is -z, EAST is +x, SOUTH is +z, WEST is -x. Also used as a structure
 * orientation (the direction local north points after rotation).
 */
public enum Direction {
	NORTH(0, -1),
	EAST(1, 0),
	SOUTH(0, 1),
	WEST(-1, 0);

	private final int dx;
	private final int dz;

	Direction(int dx, int dz) {
		this.dx = dx;
		this.dz = dz;
	}

	/** Unit step along x for this direction. */
	public int dx() {
		return dx;
	}

	/** Unit step along z for this direction. */
	public int dz() {
		return dz;
	}

	public Direction opposite() {
		return values()[(ordinal() + 2) % 4];
	}

	/** 90 degrees clockwise viewed from above: N to E to S to W. */
	public Direction clockwise() {
		return values()[(ordinal() + 1) % 4];
	}

	/**
	 * This direction after rotating a structure so its local north points at
	 * {@code orientation}. NORTH is the identity.
	 */
	public Direction rotatedTo(Direction orientation) {
		return values()[(ordinal() + orientation.ordinal()) % 4];
	}

	/**
	 * The direction whose unit step is {@code (dx, dz)} (exactly one of them
	 * +/-1, the other 0).
	 *
	 * @throws IllegalArgumentException if no direction has that step
	 */
	public static Direction fromDelta(int dx, int dz) {
		for (Direction direction : values())
			if (direction.dx == dx && direction.dz == dz)
				return direction;
		throw new IllegalArgumentException("no direction has step (" + dx + ", " + dz + ")");
	}

	/**
	 * The horizontal direction from {@code from} to the face-adjacent cell
	 * {@code to} (the {@code d} satisfying {@code from.plus(d, 1).equals(to)}).
	 *
	 * @throws IllegalArgumentException if the cells are not horizontally adjacent
	 */
	public static Direction between(Cell from, Cell to) {
		if (from.y() != to.y() || Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z()) != 1)
			throw new IllegalArgumentException(from + " and " + to + " are not horizontally adjacent");
		return fromDelta(to.x() - from.x(), to.z() - from.z());
	}
}
