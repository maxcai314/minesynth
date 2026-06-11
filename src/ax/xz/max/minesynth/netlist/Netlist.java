package ax.xz.max.minesynth.netlist;

import ax.xz.max.minesynth.netlist.internal.NetlistBuilder;
import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.rtlil.RtlilDesign;
import ax.xz.max.minesynth.rtlil.Wire;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A validated bit-level netlist: the top module's cells plus the {@link Net}s
 * connecting them, with every alias resolved and every check from the package
 * contract already passed. Immutable.
 *
 * <p>Build one with {@link #of}. The constructor is for the internal builder;
 * it does not re-validate.
 */
public final class Netlist {
	private final String topName;
	private final List<Wire> ports;
	private final List<Cell> cells;
	private final Map<String, Cell> cellsByName;
	private final Map<String, CellKind> kinds;
	private final List<Net> nets;
	private final Map<Pin, Net> pinToNet;
	private final List<Cell> combOrder;

	public Netlist(String topName, List<Wire> ports, List<Cell> cells, Map<String, CellKind> kinds,
			List<Net> nets, Map<Pin, Net> pinToNet, List<Cell> combOrder) {
		this.topName = topName;
		this.ports = List.copyOf(ports);
		this.cells = List.copyOf(cells);
		this.cellsByName = cells.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Cell::name, c -> c));
		this.kinds = Map.copyOf(kinds);
		this.nets = List.copyOf(nets);
		this.pinToNet = Map.copyOf(pinToNet);
		this.combOrder = List.copyOf(combOrder);
	}

	/**
	 * Builds and validates the netlist of {@code design}'s top module.
	 *
	 * @throws NetlistException if the design is not a flat, fully mapped
	 *         minesynth netlist; the message names the offending object
	 */
	public static Netlist of(RtlilDesign design) throws NetlistException {
		return new NetlistBuilder(design).build();
	}

	public String topName() {
		return topName;
	}

	/** Top-level port wires, sorted by port index. */
	public List<Wire> ports() {
		return ports;
	}

	public Optional<Wire> port(String name) {
		return ports.stream().filter(w -> w.name().equals(name)).findFirst();
	}

	public List<Cell> cells() {
		return cells;
	}

	public Optional<Cell> cell(String name) {
		return Optional.ofNullable(cellsByName.get(name));
	}

	/** The library kind of {@code cell}. */
	public CellKind kindOf(Cell cell) {
		return kinds.get(cell.name());
	}

	public List<Cell> flipFlops() {
		return cells.stream().filter(c -> kindOf(c).isFlipFlop()).toList();
	}

	public List<Net> nets() {
		return nets;
	}

	/** The net this pin is attached to, if any. */
	public Optional<Net> netAt(Pin pin) {
		return Optional.ofNullable(pinToNet.get(pin));
	}

	/**
	 * The combinational cells in evaluation order: every cell appears after
	 * all cells whose outputs feed its inputs (flip-flop outputs and ports
	 * break the ordering, as they should).
	 */
	public List<Cell> combOrder() {
		return combOrder;
	}

	@Override
	public String toString() {
		return "Netlist[" + topName + ", " + cells.size() + " cells, " + nets.size() + " nets]";
	}
}
