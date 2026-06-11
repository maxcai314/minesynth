package ax.xz.max.minesynth.netlist;

import java.util.List;
import java.util.Optional;

/**
 * One single-bit signal of the design: exactly one driver and any number of
 * sinks. {@code name} is a representative RTLIL wire bit (public names
 * preferred) for human-readable output.
 */
public record Net(int id, Pin driver, List<Pin> sinks, Optional<String> name) {
	public Net {
		sinks = List.copyOf(sinks);
	}

	/** Sink count; the placement cost driver. */
	public int fanout() {
		return sinks.size();
	}

	@Override
	public String toString() {
		return "Net[" + id + name.map(n -> " " + n).orElse("") + ", driver=" + driver
			+ ", fanout=" + sinks.size() + "]";
	}
}
