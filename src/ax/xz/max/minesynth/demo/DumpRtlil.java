package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.rtlil.RtlilDesign;
import ax.xz.max.minesynth.rtlil.RtlilModule;
import ax.xz.max.minesynth.rtlil.RtlilParser;
import ax.xz.max.minesynth.rtlil.RtlilWriter;
import ax.xz.max.minesynth.rtlil.Wire;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses RTLIL files and prints a yosys-stat-like summary per module.
 *
 * <p>Usage: {@code DumpRtlil [--write <out.rtlil>] [files...]}. With no files,
 * reads the reference netlists in {@code synthesis/tests/rtlil/}. With
 * {@code --write}, also serializes the first parsed design back out (useful
 * for verifying fidelity with {@code yosys -p 'read_rtlil out.rtlil; stat'}).
 */
public final class DumpRtlil {
	public static void main(String[] args) throws Exception {
		Path writeBack = null;
		var files = new java.util.ArrayList<String>();
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--write")) {
				writeBack = Path.of(args[++i]);
			} else {
				files.add(args[i]);
			}
		}
		if (files.isEmpty()) {
			files.add("synthesis/tests/rtlil/test_techmap_ureduce.rtlil");
			files.add("synthesis/tests/rtlil/test_techmap_dff.rtlil");
			files.add("synthesis/tests/rtlil/test_techmap_dffe.rtlil");
		}

		boolean first = true;
		for (String file : files) {
			RtlilDesign design = RtlilParser.parseFile(Path.of(file));
			System.out.println("=== " + file + " ===");
			System.out.println("autoidx " + design.autoidx() + ", " + design.modules().size() + " modules");
			for (RtlilModule module : design.modules())
				dumpModule(module);
			if (first && writeBack != null) {
				Files.writeString(writeBack, RtlilWriter.write(design));
				System.out.println("wrote model back to " + writeBack);
			}
			first = false;
		}
	}

	private static void dumpModule(RtlilModule module) {
		StringBuilder header = new StringBuilder("\nmodule " + module.name());
		if (module.isTop())
			header.append("  (top)");
		if (module.isBlackbox())
			header.append("  (blackbox)");
		System.out.println(header);

		int wireBits = module.wires().stream().mapToInt(Wire::width).sum();
		int portBits = module.ports().stream().mapToInt(Wire::width).sum();
		System.out.printf("  %5d wires (%d bits), %d ports (%d bits)%n",
			module.wires().size(), wireBits, module.ports().size(), portBits);
		if (!module.memories().isEmpty())
			System.out.printf("  %5d memories%n", module.memories().size());
		if (!module.processes().isEmpty())
			System.out.printf("  %5d processes%n", module.processes().size());
		if (!module.connections().isEmpty())
			System.out.printf("  %5d connections%n", module.connections().size());

		System.out.printf("  %5d cells%n", module.cells().size());
		Map<String, Integer> histogram = new LinkedHashMap<>();
		for (Cell cell : module.cells())
			histogram.merge(cell.type(), 1, Integer::sum);
		histogram.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(e -> System.out.printf("  %5d   %s%n", e.getValue(), e.getKey()));
	}
}
