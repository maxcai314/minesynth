package ax.xz.max.minesynth.pnr;

/**
 * Thrown when a placer cannot realize a design: components do not fit the
 * floorplan, or a placement constraint cannot be satisfied.
 */
public class PlacementException extends Exception {
	public PlacementException(String message) {
		super(message);
	}
}
