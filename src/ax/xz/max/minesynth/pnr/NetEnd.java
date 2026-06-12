package ax.xz.max.minesynth.pnr;

/**
 * One endpoint of a {@link Net}: either a named floorplan port or a pin of a
 * named component. Direction follows from position in the net: a component
 * pin used as a net source is that component's output pin {@code index}; used
 * as a sink it is input pin {@code index}. A port used as a source is a board
 * input; as a sink, a board output.
 */
public sealed interface NetEnd permits NetEnd.Port, NetEnd.Pin {

	/** A floorplan port, by name. */
	record Port(String name) implements NetEnd {
		@Override
		public String toString() {
			return "port " + name;
		}
	}

	/** A component pin, by component name and pin index. */
	record Pin(String component, int index) implements NetEnd {
		@Override
		public String toString() {
			return component + "[" + index + "]";
		}
	}
}
