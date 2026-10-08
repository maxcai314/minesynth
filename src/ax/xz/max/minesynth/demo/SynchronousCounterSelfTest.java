package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.netlist.Netlist;
import ax.xz.max.minesynth.rtlil.BitVector;
import ax.xz.max.minesynth.rtlil.RtlilParser;
import ax.xz.max.minesynth.sim.Simulator;

import java.nio.file.Path;

/**
 * Self-contained behavioral checks for the synthesized four-bit synchronous
 * counter. The reset is active-low and synchronous: asserting it changes the
 * count only on the next rising clock edge.
 *
 * <p>Usage: {@code SynchronousCounterSelfTest [file.rtlil]}. The default is
 * {@code synthesis/tests/rtlil/test_synchronous_counter.rtlil}.
 */
public final class SynchronousCounterSelfTest {
	private static int checks;
	private static int failures;

	public static void main(String[] args) {
		try {
			String file = args.length > 0 ? args[0]
				: "synthesis/tests/rtlil/test_synchronous_counter.rtlil";
			run(file);
		} catch (Exception e) {
			failures++;
			System.out.println("FAIL unexpected exception: " + e);
			e.printStackTrace(System.out);
		}

		System.out.println();
		System.out.println(checks + " checks, " + failures + " failures"
			+ (failures == 0 ? " - ALL TESTS PASSED" : ""));
		if (failures != 0)
			System.exit(1);
	}

	private static void run(String file) throws Exception {
		Netlist netlist = Netlist.of(RtlilParser.parseFile(Path.of(file)));
		check(netlist.topName().equals("\\counter_4bit"),
			"counter netlist has top \\counter_4bit");
		check(netlist.port("\\clk").orElseThrow().width() == 1
			&& netlist.port("\\rst_n").orElseThrow().width() == 1
			&& netlist.port("\\count").orElseThrow().width() == 4,
			"counter ports have the expected widths");

		Simulator simulator = new Simulator(netlist);
		check(count(simulator) == 0, "counter powers up at zero");

		// Active-low synchronous reset holds zero on a rising edge.
		simulator.setInput("\\rst_n", BitVector.of(false));
		simulator.propagate();
		check(count(simulator) == 0, "reset starts low at zero");
		simulator.stepClock("\\clk");
		check(count(simulator) == 0, "low reset keeps count at zero on a clock");

		// Releasing reset increments once per rising edge.
		simulator.setInput("\\rst_n", BitVector.of(true));
		for (int expected = 1; expected <= 5; expected++) {
			simulator.stepClock("\\clk");
			check(count(simulator) == expected,
				"released counter reaches " + expected);
		}

		// Synchronous reset must not change the state without a clock edge.
		simulator.setInput("\\rst_n", BitVector.of(false));
		simulator.propagate();
		check(count(simulator) == 5, "asserting reset without a clock preserves count");
		simulator.stepClock("\\clk");
		check(count(simulator) == 0, "reset clears count on the next clock");

		// Four-bit arithmetic wraps from 15 back to zero.
		simulator.setInput("\\rst_n", BitVector.of(true));
		for (int expected = 1; expected <= 16; expected++) {
			simulator.stepClock("\\clk");
			check(count(simulator) == (expected & 0xF),
				"counter wraps at four bits, step " + expected);
		}

		printTrace(netlist);
	}

	private static void printTrace(Netlist netlist) throws Exception {
		Simulator simulator = new Simulator(netlist);
		System.out.println();
		System.out.println("rst_n | count");
		System.out.println("------+------");

		simulator.setInput("\\rst_n", BitVector.of(false));
		simulator.propagate();
		System.out.printf("%5d | %5d%n", 0, count(simulator));
		for (int i = 0; i < 3; i++) {
			simulator.stepClock("\\clk");
			System.out.printf("%5d | %5d%n", 0, count(simulator));
		}

		simulator.setInput("\\rst_n", BitVector.of(true));
		for (int i = 0; i < 20; i++) {
			simulator.stepClock("\\clk");
			System.out.printf("%5d | %5d%n", 1, count(simulator));
		}
	}

	private static int count(Simulator simulator) {
		return (int) simulator.output("\\count").toLong();
	}

	private static void check(boolean condition, String label) {
		checks++;
		if (condition)
			System.out.println("PASS " + label);
		else {
			failures++;
			System.out.println("FAIL " + label);
		}
	}
}
