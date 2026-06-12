package ax.xz.max.minesynth.pnr;

import java.util.List;

/**
 * One logical connection: a single driving source and one or more sinks
 * (more than one sink = fanout).
 */
public record Net(String name, NetEnd source, List<NetEnd> sinks) {
	public Net {
		sinks = List.copyOf(sinks);
		if (sinks.isEmpty())
			throw new IllegalArgumentException("net " + name + " has no sinks");
	}

	@Override
	public String toString() {
		return "Net[" + name + ": " + source + " -> " + sinks + "]";
	}
}
