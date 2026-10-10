package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.netlist.CellKind;
import ax.xz.max.minesynth.netlist.Netlist;
import ax.xz.max.minesynth.pnr.Floorplan;
import ax.xz.max.minesynth.pnr.NaivePlacer;
import ax.xz.max.minesynth.pnr.NaiveRouter;
import ax.xz.max.minesynth.pnr.NetEnd;
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
import java.util.Map;

/**
 * The synthesized two-bit adder through the whole stack: the RTLIL netlist
 * from the synthesis flow is parsed, lifted into a {@link PnrDesign} via
 * {@link PnrDesign#fromNetlist}, machine-placed and machine-routed, and
 * exported as a schematic. The hand-built XOR structure is used directly as
 * a reusable component in the synthesized design.
 *
 * <p>Expected behavior in game: cout:sum = a + b + cin.
 */
public final class AdderPnrDemo {
	public static void main(String[] args) throws Exception {
		String file = args.length > 0 ? args[0] : "synthesis/tests/rtlil/test_two_bit_adder.rtlil";
		Netlist netlist = Netlist.of(RtlilParser.parseFile(Path.of(file)));
		System.out.println("netlist " + netlist.topName() + ": " + netlist.cells().size()
			+ " cells, " + netlist.nets().size() + " nets");

		System.out.println("using XOR gate: " + Gates.xorGate().size() + " cells");
		System.out.println();

		Map<CellKind, Structure> gateLibrary = Map.of(
			CellKind.GATE_AND, Gates.andGate(),
			CellKind.GATE_OR, Gates.orGate(),
			CellKind.GATE_NOT, Gates.notGate(),
			CellKind.GATE_XOR, Gates.xorGate());

		Cell dimensions = new Cell(32, 8, 24);
		Floorplan floorplan = new Floorplan.Builder(dimensions)
			.inputPort("a[0]", south(4, dimensions.z() - 1))
			.inputPort("a[1]", south(9, dimensions.z() - 1))
			.inputPort("b[0]", south(14, dimensions.z() - 1))
			.inputPort("b[1]", south(19, dimensions.z() - 1))
			.inputPort("cin", south(24, dimensions.z() - 1))
			.outputPort("sum[0]", north(4))
			.outputPort("sum[1]", north(9))
			.outputPort("cout", north(14))
			.build();

		PnrDesign design = PnrDesign.fromNetlist(netlist, floorplan, /*gateLibrary*/ null);
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
		System.out.println("expected: cout:sum = a + b + cin");

		Path schematicFile = Path.of("out", "pnr-adder.schematic");
		SchematicWriter.write(board, schematicFile);
		Path guideFile = Path.of("out", "pnr-adder-guide.txt");
		Files.writeString(guideFile, BuildGuide.compassDiagram() + "\n" + BuildGuide.render(board));
		System.out.println();
		System.out.println("wrote " + schematicFile + " (worldedit: //schem load pnr-adder, then //paste)");
		System.out.println("wrote " + guideFile + " (full layer-by-layer tutorial, too big for the console)");
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
