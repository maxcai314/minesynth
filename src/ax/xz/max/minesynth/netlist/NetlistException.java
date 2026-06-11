package ax.xz.max.minesynth.netlist;

/**
 * Thrown when an RTLIL design cannot be turned into a valid minesynth netlist:
 * it uses cells outside the Minecraft contract, has connectivity errors
 * (multiple drivers, undriven sinks), or is not a flat post-synthesis netlist.
 */
public class NetlistException extends Exception {
	public NetlistException(String message) {
		super(message);
	}
}
