package ax.xz.max.minesynth.rtlil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A cell instance: an instantiation of a Yosys internal cell type (like
 * {@code $_AND_}), a blackbox (like {@code \MC_DFF31}), or another module.
 * Connections map port names to the signals tied to them.
 */
public record Cell(
	String type,
	String name,
	Attributes attributes,
	Map<String, CellParameter> parameters,
	Map<String, SigSpec> connections
) {
	public Cell {
		parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
		connections = Collections.unmodifiableMap(new LinkedHashMap<>(connections));
	}

	/** The signal connected to {@code port} (port name includes its {@code \} prefix). */
	public Optional<SigSpec> connection(String port) {
		return Optional.ofNullable(connections.get(port));
	}

	/** Convenience accessor for an integer-valued parameter such as {@code \WIDTH}. */
	public Optional<Integer> intParameter(String name) {
		CellParameter p = parameters.get(name);
		return p == null ? Optional.empty() : Optional.of(p.value().asInt());
	}

	@Override
	public String toString() {
		return "Cell[" + type + " " + name + "]";
	}
}
