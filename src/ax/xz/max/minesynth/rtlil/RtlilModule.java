package ax.xz.max.minesynth.rtlil;

import java.util.List;
import java.util.Optional;

/**
 * One RTLIL module: wires, memories, cells, processes and connections, plus
 * declared parameters (for blackboxes / parametric modules).
 *
 * <p>Lists preserve file order. Lookups here are linear; layers that need fast
 * access (like the netlist builder) index the lists themselves.
 */
public record RtlilModule(
	String name,
	Attributes attributes,
	List<ModuleParameter> parameters,
	List<Wire> wires,
	List<Memory> memories,
	List<Cell> cells,
	List<RtlilProcess> processes,
	List<Connection> connections
) {
	public RtlilModule {
		parameters = List.copyOf(parameters);
		wires = List.copyOf(wires);
		memories = List.copyOf(memories);
		cells = List.copyOf(cells);
		processes = List.copyOf(processes);
		connections = List.copyOf(connections);
	}

	public Optional<Wire> wire(String name) {
		return wires.stream().filter(w -> w.name().equals(name)).findFirst();
	}

	/** The port wires, sorted by port index. */
	public List<Wire> ports() {
		return wires.stream()
			.filter(Wire::isPort)
			.sorted((a, b) -> Integer.compare(a.port().orElseThrow().index(), b.port().orElseThrow().index()))
			.toList();
	}

	public boolean isTop() {
		return attributes.isSet(Attributes.TOP);
	}

	public boolean isBlackbox() {
		return attributes.isSet(Attributes.BLACKBOX);
	}

	@Override
	public String toString() {
		return "RtlilModule[" + name + ", " + cells.size() + " cells, " + wires.size() + " wires]";
	}
}
