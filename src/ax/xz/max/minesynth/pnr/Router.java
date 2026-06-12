package ax.xz.max.minesynth.pnr;

import ax.xz.max.minesynth.structure.Structure;

/**
 * The routing stage: realizes every net of a placement with wires and vias
 * and assembles the finished board.
 */
public interface Router {
	/**
	 * Routes all nets and returns the complete board structure, with the
	 * floorplan's ports re-exported as the structure's pins.
	 *
	 * @throws RoutingException if some net cannot be realized; the message
	 *         names the net
	 */
	Structure route(Placement placement) throws RoutingException;
}
