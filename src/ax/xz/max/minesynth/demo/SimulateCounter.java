package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.netlist.Netlist;
import ax.xz.max.minesynth.rtlil.BitVector;
import ax.xz.max.minesynth.rtlil.RtlilParser;
import ax.xz.max.minesynth.sim.Simulator;

import java.nio.file.Path;

/**
 * The end-to-end sequential-logic proof: loads a synthesized counter netlist,
 * pulses the clock, and prints the counters counting. Knows the two counter
 * test designs:
 * <ul>
 * <li>{@code \counters_en} from {@code tests/rtl/test_techmap_dffe.sv}
 *     (build with {@code ./build.sh counters_en tests/rtl/test_techmap_dffe.sv})</li>
 * <li>{@code \counters} from {@code tests/rtl/test_techmap_dff.sv}, which also
 *     demonstrates async vs sync reset
 *     (build with {@code ./build.sh counters tests/rtl/test_techmap_dff.sv})</li>
 * </ul>
 *
 * <p>Usage: {@code SimulateCounter [file.rtlil]}. With no argument, runs both
 * reference netlists from {@code synthesis/tests/rtlil/}.
 */
public final class SimulateCounter {
	public static void main(String[] args) throws Exception {
		if (args.length > 0) {
			simulate(args[0]);
			return;
		}
		simulate("synthesis/tests/rtlil/test_techmap_dffe.rtlil");
		System.out.println();
		simulate("synthesis/tests/rtlil/test_techmap_dff.rtlil");
	}

	private static void simulate(String file) throws Exception {
		Netlist netlist = Netlist.of(RtlilParser.parseFile(Path.of(file)));
		Simulator simulator = new Simulator(netlist);

		switch (netlist.topName()) {
			case "\\counters_en" -> countersEn(simulator);
			case "\\counters" -> counters(simulator);
			default -> {
				System.out.println("unknown top module " + netlist.topName() + " in " + file);
				System.out.println("this demo knows the counter tests, e.g."
					+ " synthesis/tests/rtlil/test_techmap_dffe.rtlil");
				System.exit(1);
			}
		}
	}

	private static void countersEn(Simulator sim) {
		System.out.println("simulating \\counters_en (plain/en/wide/negedge/toggle counters)");
		System.out.println();
		System.out.println("cycle | en | plain | en_cnt | en_wide | neg | toggle");

		sim.setInput("\\en", BitVector.of(true));
		for (int cycle = 0; cycle < 12; cycle++) {
			printCountersEnRow(sim, cycle);
			sim.stepClock("\\clk");
		}
		System.out.println("      (enable off; en_cnt and en_wide must freeze)");
		sim.setInput("\\en", BitVector.of(false));
		for (int cycle = 12; cycle < 16; cycle++) {
			printCountersEnRow(sim, cycle);
			sim.stepClock("\\clk");
		}
		printCountersEnRow(sim, 16);
	}

	private static void printCountersEnRow(Simulator sim, int cycle) {
		System.out.printf("%5d | %2d | %5d | %6d | %7d | %3d | %6d%n",
			cycle,
			sim.input("\\en").toLong(),
			sim.output("\\plain_count").toLong(),
			sim.output("\\en_count").toLong(),
			sim.output("\\en_wide").toLong(),
			sim.output("\\neg_count").toLong(),
			sim.output("\\toggle").toLong());
	}

	private static void counters(Simulator sim) {
		System.out.println("simulating \\counters (48-bit async/sync reset counters, en=1)");
		System.out.println("w");
		System.out.println();
		System.out.println("step                    | acount (adff)  | en_acount      | scount (sdff)  | en_scount");

		sim.setInput("\\en", BitVector.of(true));

		// async reset takes effect with NO clock edge
		sim.setInput("\\reset", BitVector.of(true));
		sim.propagate();
		printCountersRow(sim, "reset=1, no clock yet");

		// sync counters need an edge to load the reset value
		sim.stepClock("\\clk");
		printCountersRow(sim, "reset=1, one clock");

		sim.setInput("\\reset", BitVector.of(false));
		sim.propagate();
		for (int cycle = 0; cycle < 5; cycle++) {
			sim.stepClock("\\clk");
			printCountersRow(sim, "count cycle " + cycle);
		}

		// async reset again, mid-run, still without an edge
		sim.setInput("\\reset", BitVector.of(true));
		sim.propagate();
		printCountersRow(sim, "reset=1 again, no clock");
	}

	private static void printCountersRow(Simulator sim, String label) {
		System.out.printf("%-23s | %014x | %014x | %014x | %014x%n",
			label,
			sim.output("\\acount").toLong(),
			sim.output("\\en_acount").toLong(),
			sim.output("\\scount").toLong(),
			sim.output("\\en_scount").toLong());
	}
}
