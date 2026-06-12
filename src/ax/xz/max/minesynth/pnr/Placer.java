package ax.xz.max.minesynth.pnr;

/**
 * The placement stage: chooses where every component of a design goes.
 * Implementations range from {@link NaivePlacer} to future optimizing
 * placers; all return a validated {@link Placement}.
 */
public interface Placer {
	/**
	 * Places every component of the design inside its floorplan.
	 *
	 * @throws PlacementException if the design cannot be placed; the message
	 *         names the component that did not fit
	 */
	Placement place(PnrDesign design) throws PlacementException;
}
