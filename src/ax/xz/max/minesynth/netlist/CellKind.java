package ax.xz.max.minesynth.netlist;

import ax.xz.max.minesynth.rtlil.BitVector;
import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.rtlil.Constant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The Minecraft cell library: every cell type allowed in a final minesynth
 * netlist, with its port contract. This is the Java-side mirror of the
 * whitelist in {@code synthesis/synth_build.ys}; the two must stay in sync
 * (see {@code synthesis/show/docs/synthesis.md}).
 */
public enum CellKind {
	/** Posedge register, WIDTH 1..31. Ports CLK, D, Q. */
	MC_DFF31("\\MC_DFF31"),
	/** Posedge register with async active-high reset to ARST_VALUE. Ports CLK, ARST, D, Q. */
	MC_ADFF31("\\MC_ADFF31"),
	/** AND-reduce, WIDTH 1..16. Ports A, Y. */
	MC_UAND16("\\MC_UAND16"),
	/** OR-reduce, WIDTH 1..16. Ports A, Y. */
	MC_UOR16("\\MC_UOR16"),
	/** NOR-reduce, WIDTH 1..16. Ports A, Y. */
	MC_UNOR16("\\MC_UNOR16"),
	/** XOR-reduce, width exactly 2. Ports A, Y. (Declared but never emitted by the techlib.) */
	MC_UXOR2("\\MC_UXOR2"),
	/** XOR-reduce, WIDTH 1..4. Ports A, Y. */
	MC_UXOR4("\\MC_UXOR4"),
	/** XOR-reduce, WIDTH 1..8. Ports A, Y. */
	MC_UXOR8("\\MC_UXOR8"),
	/** XOR-reduce, WIDTH 1..16. Ports A, Y. */
	MC_UXOR16("\\MC_UXOR16"),
	/** Two-input AND gate. Ports A, B, Y. */
	GATE_AND("$_AND_"),
	/** Two-input OR gate. Ports A, B, Y. */
	GATE_OR("$_OR_"),
	/** Two-input XOR gate. Ports A, B, Y. */
	GATE_XOR("$_XOR_"),
	/** Inverter. Ports A, Y. */
	GATE_NOT("$_NOT_"),
	/** Mux: Y = S ? B : A. Ports A, B, S, Y. */
	GATE_MUX("$_MUX_");

	private final String rtlilType;

	CellKind(String rtlilType) {
		this.rtlilType = rtlilType;
	}

	/** The RTLIL cell type string, prefix included. */
	public String rtlilType() {
		return rtlilType;
	}

	public static Optional<CellKind> byType(String rtlilType) {
		for (CellKind kind : values())
			if (kind.rtlilType.equals(rtlilType))
				return Optional.of(kind);
		return Optional.empty();
	}

	/** True for the stateful cells (registers). */
	public boolean isFlipFlop() {
		return this == MC_DFF31 || this == MC_ADFF31;
	}

	/** True for the wide MC_* reduction cells. */
	public boolean isReduction() {
		return switch (this) {
			case MC_UAND16, MC_UOR16, MC_UNOR16, MC_UXOR2, MC_UXOR4, MC_UXOR8, MC_UXOR16 -> true;
			default -> false;
		};
	}

	/**
	 * The port contract of {@code cell}, with widths resolved from its
	 * parameters. Also validates the parameters themselves (width bounds,
	 * reset value width).
	 *
	 * @throws NetlistException if a parameter is missing, out of range, or
	 *         inconsistent
	 */
	public Map<String, PortSpec> ports(Cell cell) throws NetlistException {
		Map<String, PortSpec> ports = new LinkedHashMap<>();
		switch (this) {
			case MC_DFF31 -> {
				int width = requireWidth(cell, 1, 31);
				ports.put("\\CLK", PortSpec.input(1));
				ports.put("\\D", PortSpec.input(width));
				ports.put("\\Q", PortSpec.output(width));
			}
			case MC_ADFF31 -> {
				int width = requireWidth(cell, 1, 31);
				BitVector resetValue = arstValue(cell);
				if (resetValue.width() != width)
					throw new NetlistException(cell.name() + ": ARST_VALUE is " + resetValue.width()
						+ " bits but WIDTH is " + width);
				ports.put("\\CLK", PortSpec.input(1));
				ports.put("\\ARST", PortSpec.input(1));
				ports.put("\\D", PortSpec.input(width));
				ports.put("\\Q", PortSpec.output(width));
			}
			case MC_UAND16, MC_UOR16, MC_UNOR16, MC_UXOR16 -> reduction(cell, ports, 16);
			case MC_UXOR8 -> reduction(cell, ports, 8);
			case MC_UXOR4 -> reduction(cell, ports, 4);
			case MC_UXOR2 -> {
				ports.put("\\A", PortSpec.input(2));
				ports.put("\\Y", PortSpec.output(1));
			}
			case GATE_AND, GATE_OR, GATE_XOR -> {
				ports.put("\\A", PortSpec.input(1));
				ports.put("\\B", PortSpec.input(1));
				ports.put("\\Y", PortSpec.output(1));
			}
			case GATE_NOT -> {
				ports.put("\\A", PortSpec.input(1));
				ports.put("\\Y", PortSpec.output(1));
			}
			case GATE_MUX -> {
				ports.put("\\A", PortSpec.input(1));
				ports.put("\\B", PortSpec.input(1));
				ports.put("\\S", PortSpec.input(1));
				ports.put("\\Y", PortSpec.output(1));
			}
		}
		return ports;
	}

	/** The async reset value of an {@code MC_ADFF31} cell. */
	public static BitVector arstValue(Cell cell) throws NetlistException {
		var parameter = cell.parameters().get("\\ARST_VALUE");
		if (parameter == null)
			throw new NetlistException(cell.name() + ": missing ARST_VALUE parameter");
		return switch (parameter.value()) {
			case Constant.Bits(BitVector value) -> value;
			case Constant.Int(int value) -> BitVector.of(value & 0xFFFFFFFFL, 32);
			case Constant.Str s -> throw new NetlistException(cell.name() + ": ARST_VALUE is a string");
		};
	}

	private void reduction(Cell cell, Map<String, PortSpec> ports, int maxWidth) throws NetlistException {
		int width = requireWidth(cell, 1, maxWidth);
		ports.put("\\A", PortSpec.input(width));
		ports.put("\\Y", PortSpec.output(1));
	}

	private int requireWidth(Cell cell, int min, int max) throws NetlistException {
		var width = cell.intParameter("\\WIDTH");
		if (width.isEmpty())
			throw new NetlistException(cell.name() + " (" + rtlilType + "): missing WIDTH parameter");
		if (width.get() < min || width.get() > max)
			throw new NetlistException(cell.name() + " (" + rtlilType + "): WIDTH " + width.get()
				+ " outside " + min + ".." + max);
		return width.get();
	}
}
