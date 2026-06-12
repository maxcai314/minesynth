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
import ax.xz.max.minesynth.structure.SignalStats;
import ax.xz.max.minesynth.structure.Structure;
import ax.xz.max.minesynth.structure.StructureBlock;
import ax.xz.max.minesynth.structure.StructurePin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The synthesized two-bit adder through the whole stack: the RTLIL netlist
 * from the synthesis flow is parsed, lifted into a {@link PnrDesign} via
 * {@link PnrDesign#fromNetlist}, machine-placed and machine-routed, and
 * exported as a schematic. XOR has no hand-built gate yet, so it is itself a
 * composite: (A OR B) AND NOT(A AND B), placed and routed by the same
 * pipeline into a sub-board that becomes a reusable component.
 *
 * <p>Expected behavior in game: cout:sum = a + b + cin.
 */
public final class AdderPnrDemo {
	public static void main(String[] args) throws Exception {
		String file = args.length > 0 ? args[0] : "synthesis/tests/rtlil/test_two_bit_adder.rtlil";
		Netlist netlist = Netlist.of(RtlilParser.parseFile(Path.of(file)));
		System.out.println("netlist " + netlist.topName() + ": " + netlist.cells().size()
			+ " cells, " + netlist.nets().size() + " nets");

		Structure xor = xorGate();
		System.out.println("composite XOR gate: " + xor.size() + " cells, " + xor.signal());
		System.out.println();

		Map<CellKind, Structure> gateLibrary = Map.of(
			CellKind.GATE_AND, Gates.andGate(),
			CellKind.GATE_OR, Gates.orGate(),
			CellKind.GATE_NOT, Gates.notGate(),
			CellKind.GATE_XOR, xor);

		Floorplan floorplan = new Floorplan.Builder(new Cell(42, 8, 76))
			.inputPort("a[0]", south(4, 75))
			.inputPort("a[1]", south(9, 75))
			.inputPort("b[0]", south(14, 75))
			.inputPort("b[1]", south(19, 75))
			.inputPort("cin", south(24, 75))
			.outputPort("sum[0]", north(4))
			.outputPort("sum[1]", north(9))
			.outputPort("cout", north(14))
			.build();

		PnrDesign design = PnrDesign.fromNetlist(netlist, floorplan, gateLibrary);
		System.out.println("design: " + design.components().size() + " components, "
			+ design.nets().size() + " nets");

		Placement placement = new NaivePlacer(5).place(design);
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

	/**
	 * XOR as a routed sub-board: (A OR B) AND NOT(A AND B). Routed with the
	 * nested-board strength contract (inputs assumed at 12, outputs guaranteed
	 * at 8) and re-declared with those stats so the outer router can chain it.
	 * 12 is always deliverable to a composite's pins: composites are not
	 * contained, so their vias sit at least one cell away and the final
	 * pigtail piece can always become a repeater.
	 */
	static Structure xorGate() throws Exception {
		Floorplan plan = new Floorplan.Builder(new Cell(16, 4, 12))
			.inputPort("A", south(3, 11))
			.inputPort("B", south(9, 11))
			.outputPort("OUT", north(13))
			.build();
		PnrDesign design = new PnrDesign.Builder(plan)
			.component("and1", Gates.andGate())
			.component("or1", Gates.orGate())
			.component("not1", Gates.notGate())
			.component("and2", Gates.andGate())
			.connect("a", new NetEnd.Port("A"), new NetEnd.Pin("and1", 0), new NetEnd.Pin("or1", 0))
			.connect("b", new NetEnd.Port("B"), new NetEnd.Pin("and1", 1), new NetEnd.Pin("or1", 1))
			.connect("nand_leg", new NetEnd.Pin("and1", 0), new NetEnd.Pin("not1", 0))
			.connect("or_leg", new NetEnd.Pin("or1", 0), new NetEnd.Pin("and2", 0))
			.connect("not_leg", new NetEnd.Pin("not1", 0), new NetEnd.Pin("and2", 1))
			.connect("out", new NetEnd.Pin("and2", 0), new NetEnd.Port("OUT"))
			.build();
		Structure routed = new NaiveRouter(12, 8).route(new NaivePlacer().place(design));

		long delay = routed.blocks().values().stream()
			.filter(b -> b instanceof StructureBlock.Repeater || b instanceof StructureBlock.RedstoneTorch)
			.count();
		return new Structure(routed.size(), routed.blocks(), routed.inputs(), routed.outputs(),
			routed.contained(), new SignalStats(12, 8, (int) delay));
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
