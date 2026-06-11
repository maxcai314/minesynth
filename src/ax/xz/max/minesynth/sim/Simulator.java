package ax.xz.max.minesynth.sim;

import ax.xz.max.minesynth.netlist.CellKind;
import ax.xz.max.minesynth.netlist.Net;
import ax.xz.max.minesynth.netlist.Netlist;
import ax.xz.max.minesynth.netlist.NetlistException;
import ax.xz.max.minesynth.netlist.Pin;
import ax.xz.max.minesynth.rtlil.BitVector;
import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.rtlil.State;
import ax.xz.max.minesynth.rtlil.Wire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cycle-accurate two-state simulator for a minesynth {@link Netlist}.
 *
 * <p>Typical use: {@code setInput} the design's inputs, then {@code stepClock}
 * once per cycle and read {@code output} values. {@code propagate()} settles
 * combinational logic, applies async resets, and fires any clock edges caused
 * by the latest input changes; {@code stepClock} is just a 0-then-1 input
 * sequence on the clock port with propagation after each.
 */
public final class Simulator {
	private static final int MAX_SETTLE_ITERATIONS = 1024;

	private final Netlist netlist;
	private final boolean[] netValues;
	private final Map<String, boolean[]> inputs = new HashMap<>();

	private final List<int[]> constantSources = new ArrayList<>(); // {netId, value}
	private final List<PortSource> portSources = new ArrayList<>();
	private final List<CombOp> combOps = new ArrayList<>();
	private final List<FfSlot> flipFlops = new ArrayList<>();

	private boolean dirty = true;

	private record PortSource(int netId, String port, int bit) {}

	private record CombOp(CellKind kind, int[] inputNets, int outputNet) {}

	/** Mutable per-register slot; nets are -1 where unconnected (x-tied Q bits). */
	private static final class FfSlot {
		final String name;
		final int clkNet;
		final int arstNet; // -1 for MC_DFF31
		final boolean[] arstValue;
		final int[] dNets;
		final int[] qNets;
		final boolean[] state;
		Boolean previousClk;

		FfSlot(String name, int clkNet, int arstNet, boolean[] arstValue, int[] dNets, int[] qNets) {
			this.name = name;
			this.clkNet = clkNet;
			this.arstNet = arstNet;
			this.arstValue = arstValue;
			this.dNets = dNets;
			this.qNets = qNets;
			this.state = new boolean[dNets.length];
		}
	}

	/**
	 * Prepares the simulator and settles the design with all inputs at 0.
	 *
	 * @throws NetlistException if a register's reset value contains x/z bits
	 */
	public Simulator(Netlist netlist) throws NetlistException {
		this.netlist = netlist;
		this.netValues = new boolean[netlist.nets().size()];

		for (Wire port : netlist.ports())
			if (port.isInput())
				inputs.put(port.name(), new boolean[port.width()]);

		for (Net net : netlist.nets()) {
			switch (net.driver()) {
				case Pin.ConstantPin(State state) ->
					constantSources.add(new int[] {net.id(), state == State.S1 ? 1 : 0});
				case Pin.PortPin(String portWire, int bit) ->
					portSources.add(new PortSource(net.id(), portWire, bit));
				case Pin.CellPin ignored -> {}
			}
		}

		for (Cell cell : netlist.combOrder())
			combOps.add(combOp(cell));
		for (Cell cell : netlist.flipFlops())
			flipFlops.add(ffSlot(cell));

		propagate();
	}

	/**
	 * Sets an input port. The value must be exactly as wide as the port and
	 * fully defined (no x/z bits). Takes effect on the next propagation.
	 */
	public void setInput(String portName, BitVector value) {
		boolean[] bits = inputs.get(portName);
		if (bits == null)
			throw new IllegalArgumentException("no input port named " + portName);
		if (value.width() != bits.length)
			throw new IllegalArgumentException(portName + " is " + bits.length
				+ " bits wide, but the supplied value " + value + " has " + value.width());
		if (!value.isFullyDefined())
			throw new IllegalArgumentException(portName + ": value " + value
				+ " contains x/z bits; simulation is two-state");
		for (int i = 0; i < bits.length; i++)
			bits[i] = value.bit(i).toBoolean();
		dirty = true;
	}

	/** The value an input port is currently set to. */
	public BitVector input(String portName) {
		boolean[] bits = inputs.get(portName);
		if (bits == null)
			throw new IllegalArgumentException("no input port named " + portName);
		return BitVector.fromBooleans(bits);
	}

	/** Drives {@code clockPort} low then high, propagating after each step. Convenience method for testing. */
	public void stepClock(String clockPort) {
		setInput(clockPort, BitVector.of(false));
		propagate();
		setInput(clockPort, BitVector.of(true));
		propagate();
	}

	/** The current value of an output port, exactly as wide as the port. */
	public BitVector output(String portName) {
		if (dirty)
			propagate();
		Wire port = netlist.port(portName)
			.orElseThrow(() -> new IllegalArgumentException("no port named " + portName));
		boolean[] bits = new boolean[port.width()];
		for (int i = 0; i < port.width(); i++) {
			var net = netlist.netAt(new Pin.PortPin(portName, i));
			bits[i] = net.isPresent() && netValues[net.get().id()];
		}
		return BitVector.fromBooleans(bits);
	}

	/**
	 * Settles combinational logic, then repeatedly applies async resets and
	 * clock edges until the design is stable.
	 */
	public void propagate() {
		for (int iteration = 0; iteration < MAX_SETTLE_ITERATIONS; iteration++) {
			settle();
			boolean changed = false;

			// async resets win over everything
			for (FfSlot ff : flipFlops)
				if (ff.arstNet >= 0 && netValues[ff.arstNet])
					changed |= load(ff, ff.arstValue);

			// sample all D inputs first, then commit, so FFs clock simultaneously
			List<FfSlot> firing = new ArrayList<>();
			List<boolean[]> sampled = new ArrayList<>();
			for (FfSlot ff : flipFlops) {
				boolean clk = netValues[ff.clkNet];
				boolean rising = ff.previousClk != null && !ff.previousClk && clk;
				ff.previousClk = clk;
				if (rising && !(ff.arstNet >= 0 && netValues[ff.arstNet])) {
					boolean[] next = new boolean[ff.dNets.length];
					for (int i = 0; i < next.length; i++)
						next[i] = ff.dNets[i] >= 0 && netValues[ff.dNets[i]];
					firing.add(ff);
					sampled.add(next);
				}
			}
			for (int i = 0; i < firing.size(); i++)
				changed |= load(firing.get(i), sampled.get(i));

			if (!changed) {
				dirty = false;
				return;
			}
		}
		throw new IllegalStateException("design did not stabilize after "
			+ MAX_SETTLE_ITERATIONS + " iterations (oscillating async logic?)");
	}

	// ---- internals ----

	private boolean load(FfSlot ff, boolean[] value) {
		boolean changed = false;
		for (int i = 0; i < ff.state.length; i++) {
			if (ff.state[i] != value[i]) {
				ff.state[i] = value[i];
				changed = true;
			}
		}
		return changed;
	}

	private void settle() {
		for (int[] constant : constantSources)
			netValues[constant[0]] = constant[1] != 0;
		for (PortSource source : portSources)
			netValues[source.netId()] = inputs.get(source.port())[source.bit()];
		for (FfSlot ff : flipFlops)
			for (int i = 0; i < ff.qNets.length; i++)
				if (ff.qNets[i] >= 0)
					netValues[ff.qNets[i]] = ff.state[i];
		for (CombOp op : combOps)
			netValues[op.outputNet()] = evaluate(op);
	}

	private boolean evaluate(CombOp op) {
		int[] in = op.inputNets();
		return switch (op.kind()) {
			case GATE_NOT -> !netValues[in[0]];
			case GATE_AND -> netValues[in[0]] && netValues[in[1]];
			case GATE_OR -> netValues[in[0]] || netValues[in[1]];
			case GATE_XOR -> netValues[in[0]] ^ netValues[in[1]];
			case GATE_MUX -> netValues[in[2]] ? netValues[in[1]] : netValues[in[0]];
			case MC_UAND16 -> {
				for (int net : in)
					if (!netValues[net])
						yield false;
				yield true;
			}
			case MC_UOR16 -> anySet(in);
			case MC_UNOR16 -> !anySet(in);
			case MC_UXOR2, MC_UXOR4, MC_UXOR8, MC_UXOR16 -> {
				boolean parity = false;
				for (int net : in)
					parity ^= netValues[net];
				yield parity;
			}
			case MC_DFF31, MC_ADFF31 -> throw new IllegalStateException("FF in comb order");
		};
	}

	private boolean anySet(int[] nets) {
		for (int net : nets)
			if (netValues[net])
				return true;
		return false;
	}

	private CombOp combOp(Cell cell) {
		CellKind kind = netlist.kindOf(cell);
		int[] inputNets = switch (kind) {
			case GATE_NOT -> new int[] {pinNet(cell, "\\A", 0)};
			case GATE_AND, GATE_OR, GATE_XOR -> new int[] {pinNet(cell, "\\A", 0), pinNet(cell, "\\B", 0)};
			case GATE_MUX -> new int[] {pinNet(cell, "\\A", 0), pinNet(cell, "\\B", 0), pinNet(cell, "\\S", 0)};
			default -> {
				int width = cell.connections().get("\\A").width();
				int[] nets = new int[width];
				for (int i = 0; i < width; i++)
					nets[i] = pinNet(cell, "\\A", i);
				yield nets;
			}
		};
		return new CombOp(kind, inputNets, pinNet(cell, "\\Y", 0));
	}

	private FfSlot ffSlot(Cell cell) throws NetlistException {
		CellKind kind = netlist.kindOf(cell);
		int width = cell.connections().get("\\D").width();
		int[] dNets = new int[width];
		int[] qNets = new int[width];
		for (int i = 0; i < width; i++) {
			dNets[i] = pinNetOrMissing(cell, "\\D", i);
			qNets[i] = pinNetOrMissing(cell, "\\Q", i);
		}
		int clkNet = pinNet(cell, "\\CLK", 0);

		int arstNet = -1;
		boolean[] arstValue = new boolean[width];
		if (kind == CellKind.MC_ADFF31) {
			arstNet = pinNet(cell, "\\ARST", 0);
			BitVector value = CellKind.arstValue(cell);
			if (!value.isFullyDefined())
				throw new NetlistException(cell.name() + ": ARST_VALUE contains x/z bits");
			for (int i = 0; i < width; i++)
				arstValue[i] = value.bit(i) == State.S1;
		}
		return new FfSlot(cell.name(), clkNet, arstNet, arstValue, dNets, qNets);
	}

	private int pinNet(Cell cell, String port, int bit) {
		return netlist.netAt(new Pin.CellPin(cell.name(), port, bit))
			.orElseThrow(() -> new IllegalStateException(cell.name() + "." + port + "[" + bit + "] has no net"))
			.id();
	}

	private int pinNetOrMissing(Cell cell, String port, int bit) {
		return netlist.netAt(new Pin.CellPin(cell.name(), port, bit)).map(Net::id).orElse(-1);
	}
}
