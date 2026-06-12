package ax.xz.max.minesynth.pnr;

import ax.xz.max.minesynth.structure.Structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The full input to placement and routing: the floorplan, the named component
 * instances to place, and the nets connecting component pins and board ports.
 * The gate-level netlist of chip design, with geometry constraints attached.
 */
public record PnrDesign(Floorplan floorplan, Map<String, Structure> components, List<Net> nets) {
	public PnrDesign {
		components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
		nets = List.copyOf(nets);
		validate(floorplan, components, nets);
	}

	/**
	 * Builds a design from a validated {@link ax.xz.max.minesynth.netlist.Netlist}:
	 * every netlist cell becomes a component using the structure mapped for
	 * its {@link ax.xz.max.minesynth.netlist.CellKind}, and every netlist net
	 * becomes a {@link Net}.
	 *
	 * <p>Only single-bit gate kinds are supported for now; mapped structures
	 * must declare their input pins in the gate's port order (A, B, then S)
	 * and exactly one output. Floorplan port names must follow the netlist's
	 * top-level ports: the wire name without its prefix, with {@code [bit]}
	 * appended for multi-bit wires (so wire {@code \a} of width 2 needs ports
	 * {@code a[0]} and {@code a[1]}).
	 */
	public static PnrDesign fromNetlist(ax.xz.max.minesynth.netlist.Netlist netlist, Floorplan floorplan,
			Map<ax.xz.max.minesynth.netlist.CellKind, Structure> gateLibrary) {
		Builder builder = new Builder(floorplan);
		Map<String, String> componentNames = new LinkedHashMap<>();
		Map<String, ax.xz.max.minesynth.netlist.CellKind> kinds = new LinkedHashMap<>();

		int index = 0;
		for (ax.xz.max.minesynth.rtlil.Cell cell : netlist.cells()) {
			ax.xz.max.minesynth.netlist.CellKind kind = netlist.kindOf(cell);
			Structure structure = gateLibrary.get(kind);
			if (structure == null)
				throw new IllegalArgumentException("no structure mapped for " + kind.rtlilType()
					+ " (cell " + cell.name() + ")");
			String name = kind.name().replace("GATE_", "").toLowerCase(java.util.Locale.ROOT) + index++;
			componentNames.put(cell.name(), name);
			kinds.put(cell.name(), kind);
			builder.component(name, structure);
		}

		for (ax.xz.max.minesynth.netlist.Net net : netlist.nets()) {
			String netName = net.name().orElse("n" + net.id());
			NetEnd source = switch (net.driver()) {
				case ax.xz.max.minesynth.netlist.Pin.CellPin(String cell, String port, int bit) -> {
					requireGateOutput(kinds.get(cell), port, netName);
					yield new NetEnd.Pin(componentNames.get(cell), 0);
				}
				case ax.xz.max.minesynth.netlist.Pin.PortPin(String wire, int bit) ->
					new NetEnd.Port(portName(netlist, wire, bit));
				case ax.xz.max.minesynth.netlist.Pin.ConstantPin c ->
					throw new IllegalArgumentException("net " + netName
						+ " is driven by a constant, which is not supported yet");
			};
			List<NetEnd> sinks = new java.util.ArrayList<>();
			for (ax.xz.max.minesynth.netlist.Pin sink : net.sinks()) {
				switch (sink) {
					case ax.xz.max.minesynth.netlist.Pin.CellPin(String cell, String port, int bit) ->
						sinks.add(new NetEnd.Pin(componentNames.get(cell),
							gateInputIndex(kinds.get(cell), port, netName)));
					case ax.xz.max.minesynth.netlist.Pin.PortPin(String wire, int bit) ->
						sinks.add(new NetEnd.Port(portName(netlist, wire, bit)));
					case ax.xz.max.minesynth.netlist.Pin.ConstantPin c ->
						throw new IllegalArgumentException("net " + netName + " sinks into a constant");
				}
			}
			if (sinks.isEmpty())
				continue; // dangling driver, nothing to route
			builder.connect(netName, source, sinks.toArray(NetEnd[]::new));
		}
		return builder.build();
	}

	private static String portName(ax.xz.max.minesynth.netlist.Netlist netlist, String wireName, int bit) {
		var wire = netlist.port(wireName).orElseThrow(() ->
			new IllegalArgumentException("netlist references unknown port wire " + wireName));
		String base = wireName.substring(1);
		return wire.width() > 1 ? base + "[" + bit + "]" : base;
	}

	private static void requireGateOutput(ax.xz.max.minesynth.netlist.CellKind kind, String port, String netName) {
		if (gateInputOrder(kind, netName) == null || !port.equals("\\Y"))
			throw new IllegalArgumentException("net " + netName + " is driven by unsupported pin "
				+ port + " of a " + kind.rtlilType());
	}

	private static int gateInputIndex(ax.xz.max.minesynth.netlist.CellKind kind, String port, String netName) {
		List<String> order = gateInputOrder(kind, netName);
		int index = order.indexOf(port);
		if (index < 0)
			throw new IllegalArgumentException("net " + netName + " sinks into unknown port "
				+ port + " of a " + kind.rtlilType());
		return index;
	}

	private static List<String> gateInputOrder(ax.xz.max.minesynth.netlist.CellKind kind, String netName) {
		return switch (kind) {
			case GATE_NOT -> List.of("\\A");
			case GATE_AND, GATE_OR, GATE_XOR -> List.of("\\A", "\\B");
			case GATE_MUX -> List.of("\\A", "\\B", "\\S");
			default -> throw new IllegalArgumentException("net " + netName + " touches a "
				+ kind.rtlilType() + " cell, which fromNetlist cannot map yet");
		};
	}

	private static void validate(Floorplan floorplan, Map<String, Structure> components, List<Net> nets) {
		Set<NetEnd> drivenSinks = new HashSet<>();
		Set<String> netNames = new HashSet<>();
		for (Net net : nets) {
			if (!netNames.add(net.name()))
				throw new IllegalArgumentException("duplicate net name " + net.name());
			switch (net.source()) {
				case NetEnd.Port(String name) -> {
					if (!floorplan.inputPorts().containsKey(name))
						throw new IllegalArgumentException("net " + net.name()
							+ " is driven by unknown input port " + name);
				}
				case NetEnd.Pin(String component, int index) ->
					requirePin(components, component, index, true, net.name());
			}
			for (NetEnd sink : net.sinks()) {
				switch (sink) {
					case NetEnd.Port(String name) -> {
						if (!floorplan.outputPorts().containsKey(name))
							throw new IllegalArgumentException("net " + net.name()
								+ " drives unknown output port " + name);
					}
					case NetEnd.Pin(String component, int index) ->
						requirePin(components, component, index, false, net.name());
				}
				if (!drivenSinks.add(sink))
					throw new IllegalArgumentException(sink + " is driven by more than one net");
			}
		}
	}

	private static void requirePin(Map<String, Structure> components, String component, int index,
			boolean output, String netName) {
		Structure structure = components.get(component);
		if (structure == null)
			throw new IllegalArgumentException("net " + netName + " references unknown component " + component);
		int pinCount = output ? structure.outputs().size() : structure.inputs().size();
		if (index < 0 || index >= pinCount)
			throw new IllegalArgumentException("net " + netName + " references "
				+ (output ? "output" : "input") + " pin " + index + " of " + component
				+ ", which has only " + pinCount);
	}

	/** Fluent builder; components and nets keep declaration order. */
	public static final class Builder {
		private final Floorplan floorplan;
		private final Map<String, Structure> components = new LinkedHashMap<>();
		private final List<Net> nets = new ArrayList<>();

		public Builder(Floorplan floorplan) {
			this.floorplan = floorplan;
		}

		public Builder component(String name, Structure structure) {
			if (components.putIfAbsent(name, structure) != null)
				throw new IllegalArgumentException("duplicate component " + name);
			return this;
		}

		public Builder connect(String netName, NetEnd source, NetEnd... sinks) {
			nets.add(new Net(netName, source, List.of(sinks)));
			return this;
		}

		/** Connect with an automatic net name. */
		public Builder connect(NetEnd source, NetEnd... sinks) {
			return connect("net" + nets.size(), source, sinks);
		}

		public PnrDesign build() {
			return new PnrDesign(floorplan, components, nets);
		}
	}
}
