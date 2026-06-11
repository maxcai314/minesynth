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
}
