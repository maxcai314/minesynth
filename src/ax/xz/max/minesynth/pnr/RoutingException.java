package ax.xz.max.minesynth.pnr;

/**
 * Thrown when a router cannot realize a placement's nets: no path exists, a
 * via cannot be placed, or a signal-strength requirement cannot be met.
 */
public class RoutingException extends Exception {
	public RoutingException(String message) {
		super(message);
	}
}
