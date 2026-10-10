package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.netlist.Netlist;
import ax.xz.max.minesynth.pnr.CellLibrary;
import ax.xz.max.minesynth.pnr.Floorplan;
import ax.xz.max.minesynth.pnr.NaivePlacer;
import ax.xz.max.minesynth.pnr.NaiveRouter;
import ax.xz.max.minesynth.pnr.Placement;
import ax.xz.max.minesynth.pnr.PnrDesign;
import ax.xz.max.minesynth.rtlil.RtlilParser;
import ax.xz.max.minesynth.schematic.SchematicWriter;
import ax.xz.max.minesynth.structure.BuildGuide;
import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.Direction;
import ax.xz.max.minesynth.structure.Gates;
import ax.xz.max.minesynth.structure.Structure;
import ax.xz.max.minesynth.structure.StructurePin;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The synthesized four-bit synchronous counter through the complete physical
 * pipeline: parse RTLIL, instantiate standard cells, place, route, and export
 * the resulting Minecraft structure.
 *
 * <p>The reset is active-low and synchronous. While {@code rst_n} is high,
 * {@code count} increments on each rising edge of {@code clk}; while
 * {@code rst_n} is low, the next rising edge clears {@code count} to zero.
 *
 * <p>Usage: {@code SynchronousCounterPnrDemo [file.rtlil]}. The default is
 * {@code synthesis/tests/rtlil/test_synchronous_counter.rtlil}.
 */
public final class SynchronousCounterPnrDemo {
	public static void main(String[] args) throws Exception {
		String file = args.length > 0 ? args[0]
			: "synthesis/tests/rtlil/test_synchronous_counter.rtlil";
		Netlist netlist = Netlist.of(RtlilParser.parseFile(Path.of(file)));
		System.out.println("netlist " + netlist.topName() + ": " + netlist.cells().size()
			+ " cells, " + netlist.nets().size() + " nets");

		System.out.println("using 4-bit DFF: " + Gates.dff(4).size() + " cells");
		System.out.println();

		Cell dimensions = new Cell(24, 10, 26);
		Floorplan floorplan = new Floorplan.Builder(dimensions)
			.inputPort("clk", south(8, dimensions.z() - 1))
			.inputPort("rst_n", south(16, dimensions.z() - 1))
			.outputPort("count[0]", north(8))
			.outputPort("count[1]", north(12))
			.outputPort("count[2]", north(16))
			.outputPort("count[3]", north(20))
			.build();

		PnrDesign design = PnrDesign.fromNetlist(netlist, floorplan, CellLibrary.standardLibrary());
		System.out.println("design: " + design.components().size() + " components, "
			+ design.nets().size() + " nets");
		System.out.println("floorplan size: " + floorplan.size());

		final int spacingBetweenComponents = 5; // cells

		Placement placement = new NaivePlacer(spacingBetweenComponents).place(design);
		System.out.println("placements:");
		placement.placements().forEach((name, placed) ->
			System.out.println("  " + name + " at " + placed.position()));
		System.out.println();

		Structure board = new NaiveRouter().route(placement);
		System.out.println("routed board: " + board.size() + " cells, "
			+ board.blocks().size() + " blocks");
		System.out.println();
		System.out.println("board ports:");
		floorplan.inputPorts().forEach((name, pin) ->
			System.out.println("  input  " + name + ": dust at block " + block(pin)));
		floorplan.outputPorts().forEach((name, pin) ->
			System.out.println("  output " + name + ": dust at block " + block(pin)));
		System.out.println();
		System.out.println("expected: count increments on rising clk edges; low rst_n clears on the next edge");

		Path schematicFile = Path.of("out", "pnr-synchronous-counter.schematic");
		SchematicWriter.write(board, schematicFile);
		Path guideFile = Path.of("out", "pnr-synchronous-counter-guide.txt");
		Files.writeString(guideFile, BuildGuide.compassDiagram() + "\n" + BuildGuide.render(board));
		System.out.println();
		System.out.println("wrote " + schematicFile
			+ " (worldedit: //schem load pnr-synchronous-counter, then //paste)");
		System.out.println("wrote " + guideFile
			+ " (full layer-by-layer tutorial, too big for the console)");
	}

	private static StructurePin south(int x, int z) {
		return new StructurePin(new Cell(x, 0, z), Direction.SOUTH);
	}

	private static StructurePin north(int x) {
		return new StructurePin(new Cell(x, 0, 0), Direction.NORTH);
	}

	private static String block(StructurePin pin) {
		var b = pin.connectionBlock();
		return "(" + b.x() + ", " + b.y() + ", " + b.z() + ")";
	}
}
