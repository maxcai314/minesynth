package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.netlist.CellKind;
import ax.xz.max.minesynth.netlist.Net;
import ax.xz.max.minesynth.netlist.Netlist;
import ax.xz.max.minesynth.netlist.NetlistException;
import ax.xz.max.minesynth.netlist.Pin;
import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.rtlil.RtlilParser;
import ax.xz.max.minesynth.rtlil.Wire;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the validated netlist of an RTLIL file and prints connectivity
 * statistics: cells by kind, nets, fanout, and combinational depth.
 *
 * <p>Usage: {@code DumpNetlist [file.rtlil]}, default
 * {@code synthesis/tests/rtlil/test_techmap_dffe.rtlil}. Files that are not
 * valid minesynth netlists (pre-proc dumps, leaked cell types) are rejected
 * with the reason.
 */
public final class DumpNetlist {
	public static void main(String[] args) throws Exception {
		String file = args.length > 0 ? args[0] : "synthesis/tests/rtlil/test_techmap_dffe.rtlil";
		var design = RtlilParser.parseFile(Path.of(file));

		Netlist netlist;
		try {
			netlist = Netlist.of(design);
		} catch (NetlistException e) {
			System.out.println("REJECTED " + file);
			System.out.println("  " + e.getMessage());
			System.exit(1);
			return;
		}

		System.out.println("netlist of " + netlist.topName() + " (" + file + ")");
		System.out.println();
		for (Wire port : netlist.ports()) {
			var direction = port.port().orElseThrow().direction();
			System.out.printf("  port %-6s %-20s width %d%n", direction, port.name(), port.width());
		}

		System.out.println();
		Map<CellKind, Integer> histogram = new LinkedHashMap<>();
		for (Cell cell : netlist.cells())
			histogram.merge(netlist.kindOf(cell), 1, Integer::sum);
		System.out.println("  " + netlist.cells().size() + " cells ("
			+ netlist.flipFlops().size() + " flip-flops)");
		histogram.forEach((kind, count) -> System.out.printf("  %5d   %s%n", count, kind.rtlilType()));

		System.out.println();
		System.out.println("  " + netlist.nets().size() + " nets");
		int maxFanout = 0;
		Net widest = null;
		long totalFanout = 0;
		int constantNets = 0;
		for (Net net : netlist.nets()) {
			totalFanout += net.fanout();
			if (net.driver() instanceof Pin.ConstantPin)
				constantNets++;
			if (net.fanout() > maxFanout) {
				maxFanout = net.fanout();
				widest = net;
			}
		}
		System.out.printf("  avg fanout %.2f, max fanout %d%s%n",
			netlist.nets().isEmpty() ? 0.0 : (double) totalFanout / netlist.nets().size(),
			maxFanout,
			widest != null ? " (" + widest.name().orElse("anonymous") + ")" : "");
		if (constantNets > 0)
			System.out.println("  " + constantNets + " constant-driven nets");

		System.out.println("  combinational depth " + combDepth(netlist) + " levels");
	}

	/** Longest path in cell levels through the combinational cells. */
	private static int combDepth(Netlist netlist) {
		Map<String, Integer> level = new HashMap<>();
		int depth = 0;
		for (Cell cell : netlist.combOrder()) {
			int inputLevel = 0;
			for (var entry : cell.connections().entrySet()) {
				for (int bit = 0; bit < entry.getValue().width(); bit++) {
					var net = netlist.netAt(new Pin.CellPin(cell.name(), entry.getKey(), bit));
					if (net.isPresent() && net.get().driver() instanceof Pin.CellPin(String driver, String p, int b))
						inputLevel = Math.max(inputLevel, level.getOrDefault(driver, 0));
				}
			}
			level.put(cell.name(), inputLevel + 1);
			depth = Math.max(depth, inputLevel + 1);
		}
		return depth;
	}
}
