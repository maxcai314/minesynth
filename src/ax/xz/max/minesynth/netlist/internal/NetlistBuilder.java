package ax.xz.max.minesynth.netlist.internal;

import ax.xz.max.minesynth.netlist.CellKind;
import ax.xz.max.minesynth.netlist.Net;
import ax.xz.max.minesynth.netlist.Netlist;
import ax.xz.max.minesynth.netlist.NetlistException;
import ax.xz.max.minesynth.netlist.Pin;
import ax.xz.max.minesynth.netlist.PortSpec;
import ax.xz.max.minesynth.rtlil.Attributes;
import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.rtlil.Connection;
import ax.xz.max.minesynth.rtlil.RtlilDesign;
import ax.xz.max.minesynth.rtlil.RtlilModule;
import ax.xz.max.minesynth.rtlil.SigBit;
import ax.xz.max.minesynth.rtlil.SigSpec;
import ax.xz.max.minesynth.rtlil.State;
import ax.xz.max.minesynth.rtlil.Wire;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the top module of a parsed design into a {@link Netlist}: union-find
 * over wire bits (seeded by the module's connect aliases and the two constant
 * states), pin attachment from cell connections and top ports, then driver
 * and topology validation.
 */
public final class NetlistBuilder {
	private final RtlilDesign design;

	private RtlilModule top;
	private final Map<String, Wire> wires = new HashMap<>();
	private final Map<String, Integer> wireBase = new HashMap<>();
	private int[] parent;
	private int const0Node;
	private int const1Node;

	private final Map<Integer, List<Pin>> driverPins = new LinkedHashMap<>();
	private final Map<Integer, List<Pin>> sinkPins = new LinkedHashMap<>();
	private final Map<Integer, String> netNames = new HashMap<>();
	private final Map<String, CellKind> kinds = new LinkedHashMap<>();
	private final Map<String, Map<String, PortSpec>> cellPorts = new LinkedHashMap<>();

	public NetlistBuilder(RtlilDesign design) {
		this.design = design;
	}

	public Netlist build() throws NetlistException {
		selectTop();
		indexWires();
		applyAliases();
		resolveCells();
		attachCellPins();
		attachPortPins();
		List<Net> nets = new ArrayList<>();
		Map<Pin, Net> pinToNet = new HashMap<>();
		buildNets(nets, pinToNet);
		List<Cell> combOrder = orderCombinational(nets);
		return new Netlist(top.name(), top.ports(), top.cells(), kinds, nets, pinToNet, combOrder);
	}

	// ---- stages ----

	private void selectTop() throws NetlistException {
		top = design.topModule().orElseThrow(() ->
			new NetlistException("no top module: nothing carries the \\top attribute"
				+ " and there is no single non-blackbox module"));
		if (!top.processes().isEmpty())
			throw new NetlistException(top.name() + " still contains processes;"
				+ " this is a pre-proc dump, not a synthesized netlist");
		if (!top.memories().isEmpty())
			throw new NetlistException(top.name() + " still contains memories;"
				+ " run the full synthesis flow first");
	}

	private void indexWires() throws NetlistException {
		int next = 0;
		for (Wire wire : top.wires()) {
			if (wire.attributes().get(Attributes.INIT).isPresent())
				throw new NetlistException("wire " + wire.name() + " carries an \\init value;"
					+ " FF initial values are not supported (registers power up at 0)");
			wires.put(wire.name(), wire);
			wireBase.put(wire.name(), next);
			next += wire.width();
		}
		const0Node = next;
		const1Node = next + 1;
		parent = new int[next + 2];
		for (int i = 0; i < parent.length; i++)
			parent[i] = i;
	}

	private void applyAliases() throws NetlistException {
		for (Connection connection : top.connections()) {
			List<SigBit> left = connection.left().bits();
			List<SigBit> right = connection.right().bits();
			for (int i = 0; i < left.size(); i++)
				aliasBits(left.get(i), right.get(i));
		}
		if (find(const0Node) == find(const1Node))
			throw new NetlistException("connect statements tie constant 0 to constant 1");
	}

	private void aliasBits(SigBit a, SigBit b) throws NetlistException {
		union(nodeOf(a), nodeOf(b));
	}

	private void resolveCells() throws NetlistException {
		for (Cell cell : top.cells()) {
			Optional<CellKind> kind = CellKind.byType(cell.type());
			if (kind.isEmpty()) {
				String hint = design.module(cell.type()).isPresent()
					? " (a module instance; the synthesis flow should have flattened this)"
					: "";
				throw new NetlistException("cell " + cell.name() + " has type " + cell.type()
					+ " which is not in the Minecraft cell contract" + hint);
			}
			kinds.put(cell.name(), kind.get());
			cellPorts.put(cell.name(), kind.get().ports(cell));
		}
	}

	private void attachCellPins() throws NetlistException {
		for (Cell cell : top.cells()) {
			Map<String, PortSpec> ports = cellPorts.get(cell.name());
			for (String connected : cell.connections().keySet())
				if (!ports.containsKey(connected))
					throw new NetlistException(cell.name() + ": connection to unknown port " + connected);

			for (var entry : ports.entrySet()) {
				String port = entry.getKey();
				PortSpec spec = entry.getValue();
				SigSpec signal = cell.connection(port).orElseThrow(() ->
					new NetlistException(cell.name() + ": port " + port + " is unconnected"));
				if (signal.width() != spec.width())
					throw new NetlistException(cell.name() + ": port " + port + " is " + spec.width()
						+ " bits but is connected to " + signal.width() + " bits");

				List<SigBit> bits = signal.bits();
				for (int i = 0; i < bits.size(); i++) {
					Pin pin = new Pin.CellPin(cell.name(), port, i);
					switch (bits.get(i)) {
						case SigBit.WireBit bit -> attach(spec.direction(), nodeOf(bit), pin);
						case SigBit.ConstBit(State state) -> {
							if (spec.direction() == PortSpec.Direction.INPUT) {
								int node = constNode(state, cell.name() + "." + port);
								sinks(node).add(pin);
							} else if (state != State.SX) {
								// x means discarded output; anything else is a short
								throw new NetlistException(cell.name() + ": output port " + port
									+ " is tied to constant " + state.text());
							}
						}
					}
				}
			}
		}
	}

	private void attachPortPins() throws NetlistException {
		for (Wire wire : top.ports()) {
			Wire.Direction direction = wire.port().orElseThrow().direction();
			if (direction == Wire.Direction.INOUT)
				throw new NetlistException("port " + wire.name() + " is inout, which has no Minecraft mapping");
			for (int i = 0; i < wire.width(); i++) {
				int node = wireBase.get(wire.name()) + i;
				Pin pin = new Pin.PortPin(wire.name(), i);
				if (direction == Wire.Direction.INPUT)
					drivers(node).add(pin);
				else
					sinks(node).add(pin);
			}
		}
	}

	private void buildNets(List<Net> nets, Map<Pin, Net> pinToNet) throws NetlistException {
		chooseNetNames();
		int rootOfConst0 = find(const0Node);
		int rootOfConst1 = find(const1Node);

		Map<Integer, List<Pin>> driversByRoot = byRoot(driverPins);
		Map<Integer, List<Pin>> sinksByRoot = byRoot(sinkPins);

		var roots = new java.util.LinkedHashSet<Integer>();
		roots.addAll(driversByRoot.keySet());
		roots.addAll(sinksByRoot.keySet());

		for (int root : roots) {
			List<Pin> drivers = new ArrayList<>(driversByRoot.getOrDefault(root, List.of()));
			if (root == rootOfConst0)
				drivers.add(new Pin.ConstantPin(State.S0));
			else if (root == rootOfConst1)
				drivers.add(new Pin.ConstantPin(State.S1));
			List<Pin> sinks = sinksByRoot.getOrDefault(root, List.of());

			String name = netNames.get(root);
			if (drivers.isEmpty()) {
				if (sinks.isEmpty())
					continue;
				throw new NetlistException("net " + (name != null ? name : "#" + root)
					+ " has sinks " + sinks + " but no driver");
			}
			if (drivers.size() > 1)
				throw new NetlistException("net " + (name != null ? name : "#" + root)
					+ " has multiple drivers: " + drivers);

			Net net = new Net(nets.size(), drivers.get(0), sinks, Optional.ofNullable(name));
			nets.add(net);
			pinToNet.put(net.driver(), net);
			for (Pin sink : sinks)
				pinToNet.put(sink, net);
		}
	}

	private List<Cell> orderCombinational(List<Net> nets) throws NetlistException {
		// edges run driver cell -> sink cell across nets; FFs and ports do not order
		Map<String, Cell> combCells = new LinkedHashMap<>();
		for (Cell cell : top.cells())
			if (!kinds.get(cell.name()).isFlipFlop())
				combCells.put(cell.name(), cell);

		Map<String, List<String>> successors = new HashMap<>();
		Map<String, Integer> indegree = new HashMap<>();
		for (String name : combCells.keySet())
			indegree.put(name, 0);

		for (Net net : nets) {
			if (!(net.driver() instanceof Pin.CellPin(String driverCell, String p, int b))
					|| !combCells.containsKey(driverCell))
				continue;
			for (Pin sink : net.sinks()) {
				if (sink instanceof Pin.CellPin(String sinkCell, String sp, int sb)
						&& combCells.containsKey(sinkCell)) {
					successors.computeIfAbsent(driverCell, k -> new ArrayList<>()).add(sinkCell);
					indegree.merge(sinkCell, 1, Integer::sum);
				}
			}
		}

		ArrayDeque<String> ready = new ArrayDeque<>();
		indegree.forEach((name, degree) -> {
			if (degree == 0)
				ready.add(name);
		});
		List<Cell> order = new ArrayList<>(combCells.size());
		while (!ready.isEmpty()) {
			String name = ready.poll();
			order.add(combCells.get(name));
			for (String successor : successors.getOrDefault(name, List.of()))
				if (indegree.merge(successor, -1, Integer::sum) == 0)
					ready.add(successor);
		}
		if (order.size() != combCells.size()) {
			List<String> looped = indegree.entrySet().stream()
				.filter(e -> e.getValue() > 0)
				.map(Map.Entry::getKey)
				.limit(8)
				.toList();
			throw new NetlistException("combinational loop through cells " + looped);
		}
		return order;
	}

	// ---- helpers ----

	private void attach(PortSpec.Direction direction, int node, Pin pin) {
		if (direction == PortSpec.Direction.INPUT)
			sinks(node).add(pin);
		else
			drivers(node).add(pin);
	}

	private int constNode(State state, String where) throws NetlistException {
		return switch (state) {
			case S0 -> const0Node;
			case S1 -> const1Node;
			default -> throw new NetlistException(where + " is tied to constant " + state.text()
				+ "; x/z constants are not allowed in a final netlist");
		};
	}

	private int nodeOf(SigBit bit) throws NetlistException {
		return switch (bit) {
			case SigBit.WireBit(String wire, int index) -> wireBase.get(wire) + index;
			case SigBit.ConstBit(State state) -> constNode(state, "a connect statement");
		};
	}

	private List<Pin> drivers(int node) {
		return driverPins.computeIfAbsent(find(node), k -> new ArrayList<>());
	}

	private List<Pin> sinks(int node) {
		return sinkPins.computeIfAbsent(find(node), k -> new ArrayList<>());
	}

	/** Re-keys pin groups by final union-find root (unions may happen after attachment). */
	private Map<Integer, List<Pin>> byRoot(Map<Integer, List<Pin>> pins) {
		Map<Integer, List<Pin>> result = new LinkedHashMap<>();
		pins.forEach((node, list) -> result.computeIfAbsent(find(node), k -> new ArrayList<>()).addAll(list));
		return result;
	}

	private void chooseNetNames() {
		for (Wire wire : top.wires()) {
			for (int i = 0; i < wire.width(); i++) {
				int root = find(wireBase.get(wire.name()) + i);
				String candidate = wire.width() == 1 ? wire.name() : wire.name() + " [" + (i + wire.offset()) + "]";
				String current = netNames.get(root);
				if (current == null || preferName(wire, candidate, current))
					netNames.put(root, candidate);
			}
		}
	}

	private boolean preferName(Wire wire, String candidate, String current) {
		boolean candidatePublic = candidate.startsWith("\\");
		boolean currentPublic = current.startsWith("\\");
		if (wire.isPort() && candidatePublic)
			return true;
		return candidatePublic && !currentPublic;
	}

	private int find(int node) {
		int root = node;
		while (parent[root] != root)
			root = parent[root];
		while (parent[node] != root) {
			int next = parent[node];
			parent[node] = root;
			node = next;
		}
		return root;
	}

	private void union(int a, int b) {
		int rootA = find(a);
		int rootB = find(b);
		if (rootA != rootB)
			parent[rootA] = rootB;
	}
}
