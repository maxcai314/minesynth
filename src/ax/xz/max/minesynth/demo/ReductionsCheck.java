package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.netlist.Netlist;
import ax.xz.max.minesynth.rtlil.BitVector;
import ax.xz.max.minesynth.rtlil.RtlilParser;
import ax.xz.max.minesynth.sim.Simulator;

import java.nio.file.Path;
import java.util.Random;

/**
 * Golden-model check for combinational logic: drives the synthesized
 * {@code \reductions} netlist with random vectors and compares every output
 * against the same operation computed directly in Java.
 *
 * <p>Usage: {@code ReductionsCheck [file.rtlil]}, default
 * {@code synthesis/tests/rtlil/test_techmap_ureduce.rtlil} (regenerate it with
 * {@code ./build.sh reductions tests/rtl/test_techmap_ureduce.sv}).
 */
public final class ReductionsCheck {
	private static final int W = 20;
	private static final long MASK = (1L << W) - 1;
	private static final int TRIALS = 500;

	public static void main(String[] args) throws Exception {
		String file = args.length > 0 ? args[0] : "synthesis/tests/rtlil/test_techmap_ureduce.rtlil";
		Netlist netlist = Netlist.of(RtlilParser.parseFile(Path.of(file)));
		if (!netlist.topName().equals("\\reductions")) {
			System.out.println("expected top \\reductions, got " + netlist.topName());
			System.out.println("pass synthesis/tests/rtlil/test_techmap_ureduce.rtlil or rebuild it with:");
			System.out.println("  cd synthesis && ./build.sh reductions tests/rtl/test_techmap_ureduce.sv");
			System.exit(1);
		}
		Simulator simulator = new Simulator(netlist);

		Random random = new Random(20260610);
		int failures = 0;
		for (int trial = 0; trial < TRIALS; trial++) {
			// edge vectors first, then random
			long input = switch (trial) {
				case 0 -> 0;
				case 1 -> MASK;
				case 2 -> 1;
				case 3 -> 1L << (W - 1);
				default -> random.nextLong() & MASK;
			};
			simulator.setInput("\\i_in", BitVector.of(input, W));
			simulator.propagate();
			failures += verify(simulator, input);
		}

		System.out.println(TRIALS + " vectors, " + failures + " mismatches"
			+ (failures == 0 ? " - GOLDEN MODEL MATCHES" : ""));
		if (failures != 0)
			System.exit(1);
	}

	private static int verify(Simulator simulator, long input) {
		int failures = 0;
		failures += compare(simulator, input, "\\o_and", input == MASK);
		failures += compare(simulator, input, "\\o_or", input != 0);
		failures += compare(simulator, input, "\\o_xor2", parity(input & 0x3));
		failures += compare(simulator, input, "\\o_xor3", parity(input & 0x7));
		failures += compare(simulator, input, "\\o_xor7", parity(input & 0x7F));
		failures += compare(simulator, input, "\\o_xor15", parity(input & 0x7FFF));
		failures += compare(simulator, input, "\\o_xor", parity(input));
		failures += compare(simulator, input, "\\o_xnor", !parity(input));
		failures += compare(simulator, input, "\\o_bool", input != 0);
		failures += compare(simulator, input, "\\o_not15", (input & 0x7FFF) == 0);
		failures += compare(simulator, input, "\\o_not", input == 0);
		return failures;
	}

	private static int compare(Simulator simulator, long input, String port, boolean expected) {
		boolean actual = simulator.output(port).toBoolean();
		if (actual == expected)
			return 0;
		System.out.printf("MISMATCH %s for input %05x: netlist says %d, golden model says %d%n",
			port, input, actual ? 1 : 0, expected ? 1 : 0);
		return 1;
	}

	private static boolean parity(long value) {
		return Long.bitCount(value) % 2 == 1;
	}
}
