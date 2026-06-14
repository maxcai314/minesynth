package ax.xz.max.minesynth.structure;

/**
 * How a structure may be placed against its neighbors. Generalizes the old
 * single "contained" flag, which could not express that an AND gate whose
 * output torch pokes through its ceiling is a fine horizontal neighbor yet
 * forbids anything directly above it.
 *
 * <p>The three booleans:
 * <ul>
 * <li>{@code horizontallyContained}: no redstone reaches the four side shells
 *     (off-port). A contained structure keeps to itself sideways, so it is safe
 *     to sit beside anything; two structures that are both exposed may not be
 *     side-adjacent (their redstone would intermingle).</li>
 * <li>{@code allowsAbove}: tolerates a structure in the cell directly above.</li>
 * <li>{@code allowsBelow}: tolerates a structure in the cell directly below.</li>
 * </ul>
 *
 * <p>The two adjacency predicates are the whole rule; the placement validators
 * ask nothing else.
 */
public record PlacementRule(
	boolean horizontallyContained,
	boolean allowsAbove,
	boolean allowsBelow
) {
	/** Fully self-contained and permissive: safe anywhere next to anything. */
	public static final PlacementRule CONTAINED = new PlacementRule(true, true, true);

	/**
	 * Horizontally exposed (redstone reaches the side shells) but open above and
	 * below. The usual rule for vias and shell-using gates whose tops and
	 * bottoms are clear.
	 */
	public static final PlacementRule EXPOSED = new PlacementRule(false, true, true);

	/**
	 * Whether {@code neighbor} may occupy the {@code where} cell relative to a
	 * structure with this rule (e.g. {@code andRule.canNeighbor(wireRule, ABOVE)}).
	 */
	public boolean canNeighbor(PlacementRule neighbor, Adjacency where) {
		return switch (where) {
			case ABOVE -> verticallyCompatible(this, neighbor);  // neighbor above this
			case BELOW -> verticallyCompatible(neighbor, this);  // neighbor below this
			case NORTH, EAST, SOUTH, WEST -> horizontallyCompatible(this, neighbor);
		};
	}

	/** Two side-adjacent structures coexist iff at least one keeps off the shared shell. */
	private static boolean horizontallyCompatible(PlacementRule a, PlacementRule b) {
		return a.horizontallyContained || b.horizontallyContained;
	}

	/** {@code upper} may sit directly above {@code lower} iff each opens the shared shell. */
	private static boolean verticallyCompatible(PlacementRule lower, PlacementRule upper) {
		return lower.allowsAbove && upper.allowsBelow;
	}

	public PlacementRule withHorizontallyContained(boolean value) {
		return new PlacementRule(value, allowsAbove, allowsBelow);
	}

	public PlacementRule withAllowsAbove(boolean value) {
		return new PlacementRule(horizontallyContained, value, allowsBelow);
	}

	public PlacementRule withAllowsBelow(boolean value) {
		return new PlacementRule(horizontallyContained, allowsAbove, value);
	}

	@Override
	public String toString() {
		return "PlacementRule[" + (horizontallyContained ? "contained" : "exposed")
			+ (allowsAbove ? "" : ", noAbove")
			+ (allowsBelow ? "" : ", noBelow") + "]";
	}
}
