package ax.xz.max.minesynth.netlist;

import ax.xz.max.minesynth.rtlil.State;

/**
 * One endpoint of a {@link Net}: a single bit of a cell port, a single bit of
 * a top-level module port, or a constant. Constants only ever appear as
 * drivers. Pins are plain values; resolve them against the netlist's cell and
 * port lookups when needed.
 */
public sealed interface Pin {

	/** Bit {@code bit} of port {@code port} on the cell named {@code cell}. */
	record CellPin(String cell, String port, int bit) implements Pin {
		@Override
		public String toString() {
			return cell + "." + port + "[" + bit + "]";
		}
	}

	/** Bit {@code bit} of the top-level port wire {@code portWire}. */
	record PortPin(String portWire, int bit) implements Pin {
		@Override
		public String toString() {
			return portWire + "[" + bit + "]";
		}
	}

	/** A constant 0 or 1 driver. */
	record ConstantPin(State state) implements Pin {
		@Override
		public String toString() {
			return String.valueOf(state.text());
		}
	}
}
