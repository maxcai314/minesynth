package ax.xz.max.minesynth.pnr;

import ax.xz.max.minesynth.netlist.CellKind;
import ax.xz.max.minesynth.netlist.Netlist;
import ax.xz.max.minesynth.netlist.NetlistException;
import ax.xz.max.minesynth.netlist.Pin;
import ax.xz.max.minesynth.netlist.PortSpec;
import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.structure.Structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
	 * Builds a design from a validated {@link Netlist}: every netlist cell
	 * becomes a component supplied by {@code cellLibrary}, and every netlist
	 * net becomes a {@link Net}.
	 *
	 * <p>Cell ports are matched to structure pins using the canonical order
	 * defined by {@link CellKind#ports(Cell)}. Inputs and outputs are numbered
	 * independently; ports retain declaration order and vector ports expand
	 * from bit 0 upward. Every used constant-driven net gets its own physical
	 * constant-source component from the same library. Floorplan port names
	 * follow the netlist's top-level ports: the wire name without its prefix,
	 * with {@code [bit]} appended for multi-bit wires (so wire {@code \a} of
	 * width 2 needs ports {@code a[0]} and {@code a[1]}).
	 */
	public static PnrDesign fromNetlist(Netlist netlist, Floorplan floorplan,
	                                    CellLibrary cellLibrary) {
		Objects.requireNonNull(cellLibrary, "cellLibrary");
		Builder builder = new Builder(floorplan);
		Map<String, ComponentMapping> componentMappings = new LinkedHashMap<>();

		int index = 0;
		for (Cell cell : netlist.cells()) {
			CellKind kind = netlist.kindOf(cell);
			Structure structure = cellLibrary.structureFor(new CellLibrary.NetlistCell(cell, kind));
			if (structure == null)
				throw new IllegalArgumentException("no structure mapped for " + kind.rtlilType()
					+ " (cell " + cell.name() + ")");

			PinLayout pinLayout = pinLayout(cell, kind);
			requireLibraryPinCounts(cell, kind, structure, pinLayout);

			String name = kind.name().replace("GATE_", "").toLowerCase(Locale.ROOT) + index++;
			componentMappings.put(cell.name(), new ComponentMapping(name, kind, pinLayout));
			builder.component(name, structure);
		}

		Map<Integer, String> constantComponents = new LinkedHashMap<>();
		int constantIndex = 0;
		for (var net : netlist.nets()) {
			if (net.sinks().isEmpty() || !(net.driver() instanceof Pin.ConstantPin constant))
				continue;
			boolean value = constant.state().toBoolean();
			String name = "constant" + (value ? "1" : "0") + "_" + constantIndex++;
			Structure structure = cellLibrary.structureFor(new CellLibrary.Constant(value));
			if (structure == null)
				throw new IllegalArgumentException("no structure mapped for constant " + (value ? 1 : 0));
			requireConstantPinCounts(value, structure);
			constantComponents.put(net.id(), name);
			builder.component(name, structure);
		}

		for (var net : netlist.nets()) {
			if (net.sinks().isEmpty())
				continue; // dangling driver, nothing to route
			String netName = net.name().orElse("n" + net.id());
			NetEnd source = switch (net.driver()) {
				case Pin.CellPin(String cell, String port, int bit) ->
					componentPin(componentMappings, cell, port, bit, true, netName);
				case Pin.PortPin(String wire, int bit) ->
					new NetEnd.Port(portName(netlist, wire, bit));
				case Pin.ConstantPin c ->
					new NetEnd.Pin(constantComponents.get(net.id()), 0);
			};
			List<NetEnd> sinks = new ArrayList<>();
			for (Pin sink : net.sinks()) {
				switch (sink) {
					case Pin.CellPin(String cell, String port, int bit) ->
						sinks.add(componentPin(componentMappings, cell, port, bit, false, netName));
					case Pin.PortPin(String wire, int bit) ->
						sinks.add(new NetEnd.Port(portName(netlist, wire, bit)));
					case Pin.ConstantPin c ->
						throw new IllegalArgumentException("net " + netName + " sinks into a constant");
				}
			}
			builder.connect(netName, source, sinks.toArray(NetEnd[]::new));
		}
		return builder.build();
	}

	private static PinLayout pinLayout(Cell cell, CellKind kind) {
		Map<PortBit, Integer> inputs = new LinkedHashMap<>();
		Map<PortBit, Integer> outputs = new LinkedHashMap<>();
		int inputIndex = 0;
		int outputIndex = 0;
		try {
			for (var entry : kind.ports(cell).entrySet()) {
				PortSpec spec = entry.getValue();
				for (int bit = 0; bit < spec.width(); bit++) {
					PortBit portBit = new PortBit(entry.getKey(), bit);
					if (spec.direction() == PortSpec.Direction.INPUT)
						inputs.put(portBit, inputIndex++);
					else
						outputs.put(portBit, outputIndex++);
				}
			}
		} catch (NetlistException e) {
			throw new IllegalStateException("validated cell " + cell.name()
				+ " no longer satisfies the " + kind.rtlilType() + " port contract", e);
		}
		return new PinLayout(Map.copyOf(inputs), Map.copyOf(outputs));
	}

	private static void requireLibraryPinCounts(Cell cell, CellKind kind, Structure structure,
	                                             PinLayout layout) {
		requireLibraryPinCount(cell, kind, "input", structure.inputs().size(), layout.inputs().size());
		requireLibraryPinCount(cell, kind, "output", structure.outputs().size(), layout.outputs().size());
	}

	private static void requireLibraryPinCount(Cell cell, CellKind kind, String direction,
	                                           int actual, int expected) {
		if (actual != expected)
			throw new IllegalArgumentException("cell " + cell.name() + " (" + kind.rtlilType()
				+ "): library structure has " + actual + " " + direction
				+ " pins, but the port contract requires " + expected);
	}

	private static void requireConstantPinCounts(boolean value, Structure structure) {
		if (!structure.inputs().isEmpty() || structure.outputs().size() != 1)
			throw new IllegalArgumentException("constant " + (value ? 1 : 0)
				+ " library structure must have no inputs and exactly one output, but has "
				+ structure.inputs().size() + " inputs and " + structure.outputs().size() + " outputs");
	}

	private static NetEnd.Pin componentPin(Map<String, ComponentMapping> componentMappings,
	                                       String cellName, String port, int bit,
	                                       boolean output, String netName) {
		ComponentMapping component = componentMappings.get(cellName);
		if (component == null)
			throw new IllegalArgumentException("net " + netName + " references unknown cell " + cellName);

		Map<PortBit, Integer> indices = output
			? component.pinLayout().outputs()
			: component.pinLayout().inputs();
		Integer index = indices.get(new PortBit(port, bit));
		if (index == null)
			throw new IllegalArgumentException("net " + netName + " references unknown "
				+ (output ? "output" : "input") + " pin " + port + "[" + bit + "] of a "
				+ component.kind().rtlilType());
		return new NetEnd.Pin(component.name(), index);
	}

	private static String portName(Netlist netlist, String wireName, int bit) {
		var wire = netlist.port(wireName).orElseThrow(() ->
			new IllegalArgumentException("netlist references unknown port wire " + wireName));
		String base = wireName.substring(1);
		return wire.width() > 1 ? base + "[" + bit + "]" : base;
	}

	private record PortBit(String port, int bit) {}

	private record PinLayout(Map<PortBit, Integer> inputs, Map<PortBit, Integer> outputs) {}

	private record ComponentMapping(String name, CellKind kind, PinLayout pinLayout) {}

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
