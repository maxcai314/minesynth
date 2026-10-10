package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.netlist.Netlist;
import ax.xz.max.minesynth.pnr.CellLibrary;
import ax.xz.max.minesynth.pnr.Floorplan;
import ax.xz.max.minesynth.pnr.NaivePlacer;
import ax.xz.max.minesynth.pnr.NaiveRouter;
import ax.xz.max.minesynth.pnr.NetEnd;
import ax.xz.max.minesynth.pnr.Placement;
import ax.xz.max.minesynth.pnr.PlacementException;
import ax.xz.max.minesynth.pnr.PnrDesign;
import ax.xz.max.minesynth.pnr.RoutingException;
import ax.xz.max.minesynth.rtlil.RtlilParser;
import ax.xz.max.minesynth.structure.BlockColor;
import ax.xz.max.minesynth.structure.BlockPos;
import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.Direction;
import ax.xz.max.minesynth.structure.Gates;
import ax.xz.max.minesynth.structure.PlacedStructure;
import ax.xz.max.minesynth.structure.Structure;
import ax.xz.max.minesynth.structure.StructureBlock;
import ax.xz.max.minesynth.structure.StructurePin;
import ax.xz.max.minesynth.structure.Wires;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ax.xz.max.minesynth.structure.Direction.NORTH;
import static ax.xz.max.minesynth.structure.Direction.SOUTH;

/**
 * Self-contained checks for the placement and routing pipeline: model
 * validation, the naive placer's layout rules, and the naive router's
 * behavior including strength repair. Prints PASS or FAIL per check and exits
 * nonzero on any failure.
 */
public final class PnrSelfTest {
	private static int checks = 0;
	private static int failures = 0;

	public static void main(String[] args) {
		try {
			floorplanChecks();
			designChecks();
			placementChecks();
			naivePlacerChecks();
			naiveRouterChecks();
			fromNetlistChecks();
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

	// ---- model validation ----

	private static void floorplanChecks() {
		Floorplan plan = new Floorplan.Builder(new Cell(5, 2, 5))
			.inputPort("A", new StructurePin(new Cell(0, 0, 2), Direction.WEST))
			.outputPort("Y", new StructurePin(new Cell(4, 0, 2), Direction.EAST))
			.build();
		check(plan.inputPorts().size() == 1 && plan.outputPorts().size() == 1, "floorplan builds");

		expectThrow(() -> new Floorplan.Builder(new Cell(5, 2, 5))
				.inputPort("A", new StructurePin(new Cell(2, 0, 2), Direction.WEST)).build(),
			"boundary", "off-boundary port rejected");
		expectThrow(() -> new Floorplan.Builder(new Cell(5, 2, 5))
				.inputPort("A", new StructurePin(new Cell(0, 0, 2), Direction.WEST))
				.outputPort("A", new StructurePin(new Cell(4, 0, 2), Direction.EAST)).build(),
			"both an input and an output", "name shared across directions rejected");
		expectThrow(() -> new Floorplan.Builder(new Cell(5, 2, 5))
				.inputPort("A", new StructurePin(new Cell(0, 0, 2), Direction.WEST))
				.inputPort("B", new StructurePin(new Cell(0, 0, 2), Direction.WEST)).build(),
			"shares cell", "two ports on one cell rejected");
	}

	private static void designChecks() {
		Floorplan plan = new Floorplan.Builder(new Cell(7, 2, 7))
			.inputPort("A", new StructurePin(new Cell(0, 0, 3), Direction.WEST))
			.build();

		expectThrow(() -> new PnrDesign.Builder(plan)
				.connect("n", new NetEnd.Port("A"), new NetEnd.Pin("ghost", 0)).build(),
			"unknown component", "net to unknown component rejected");
		expectThrow(() -> new PnrDesign.Builder(plan)
				.component("g", Gates.notGate())
				.connect("n", new NetEnd.Port("A"), new NetEnd.Pin("g", 5)).build(),
			"which has only", "pin index out of range rejected");
		expectThrow(() -> new PnrDesign.Builder(plan)
				.component("g", Gates.notGate())
				.connect("n1", new NetEnd.Port("A"), new NetEnd.Pin("g", 0))
				.connect("n2", new NetEnd.Pin("g", 0), new NetEnd.Pin("g", 0)).build(),
			"more than one net", "double-driven sink rejected");
	}

	private static void placementChecks() {
		Floorplan plan = new Floorplan.Builder(new Cell(7, 2, 7)).build();
		PnrDesign design = new PnrDesign.Builder(plan)
			.component("a", Gates.notGate())
			.component("b", Gates.notGate())
			.build();

		expectThrow(() -> new Placement(design, Map.of(
				"a", placed(Gates.notGate(), 1, 1),
				"b", placed(Gates.notGate(), 1, 1))),
			"both occupy", "overlapping placements rejected");
		expectThrow(() -> new Placement(design, Map.of(
				"a", placed(Gates.notGate(), 6, 6),
				"b", placed(Gates.notGate(), 8, 1))),
			"does not fit", "out-of-bounds placement rejected");
		expectThrow(() -> new Placement(design, Map.of("a", placed(Gates.notGate(), 1, 1))),
			"exactly the design's components", "missing placement rejected");

		PnrDesign viaDesign = new PnrDesign.Builder(plan)
			.component("a", Gates.andGate())
			.component("b", Gates.andGate())
			.build();
		expectThrow(() -> new Placement(viaDesign, Map.of(
				"a", placed(Gates.andGate(), 1, 1),
				"b", placed(Gates.andGate(), 1, 2))),
			"adjacent cells", "adjacent non-contained placements rejected");

		// pin resolution in board coordinates
		PnrDesign one = new PnrDesign.Builder(plan).component("a", Gates.andGate()).build();
		Placement placement = new Placement(one, Map.of("a", placed(Gates.andGate(), 2, 3)));
		check(placement.sinkLocation(new NetEnd.Pin("a", 1))
				.equals(new StructurePin(new Cell(3, 0, 3), SOUTH)),
			"sink pin resolves to board coordinates");
		check(placement.sourceLocation(new NetEnd.Pin("a", 0))
				.equals(new StructurePin(new Cell(2, 0, 3), NORTH)),
			"source pin resolves to board coordinates");
	}

	private static PlacedStructure placed(Structure s, int x, int z) {
		return new PlacedStructure(s, new Cell(x, 0, z), NORTH, BlockColor.UNASSIGNED);
	}

	// ---- naive placer ----

	private static void naivePlacerChecks() throws Exception {
		Floorplan plan = new Floorplan.Builder(new Cell(10, 3, 5)).build();
		PnrDesign design = new PnrDesign.Builder(plan)
			.component("and1", Gates.andGate())
			.component("not1", Gates.notGate())
			.build();
		Placement placement = new NaivePlacer().place(design);
		check(placement.placements().get("and1").position().equals(new Cell(2, 0, 2))
			&& placement.placements().get("not1").position().equals(new Cell(7, 0, 2)),
			"naive placer: margin 2, spacing 3, declaration order");

		PnrDesign tooBig = new PnrDesign.Builder(new Floorplan.Builder(new Cell(6, 2, 5)).build())
			.component("and1", Gates.andGate())
			.component("and2", Gates.andGate())
			.component("and3", Gates.andGate())
			.build();
		boolean threw = false;
		try {
			new NaivePlacer().place(tooBig);
		} catch (PlacementException e) {
			threw = true;
		}
		check(threw, "naive placer throws when components do not fit");
	}

	// ---- naive router ----

	private static void naiveRouterChecks() throws Exception {
		// the NAND pipeline end to end
		Floorplan floorplan = new Floorplan.Builder(new Cell(10, 3, 5))
			.inputPort("A", new StructurePin(new Cell(2, 0, 4), SOUTH))
			.inputPort("B", new StructurePin(new Cell(4, 0, 4), SOUTH))
			.outputPort("OUT", new StructurePin(new Cell(6, 0, 4), SOUTH))
			.build();
		PnrDesign design = new PnrDesign.Builder(floorplan)
			.component("and1", Gates.andGate())
			.component("not1", Gates.notGate())
			.connect("a_in", new NetEnd.Port("A"), new NetEnd.Pin("and1", 0))
			.connect("b_in", new NetEnd.Port("B"), new NetEnd.Pin("and1", 1))
			.connect("and_to_not", new NetEnd.Pin("and1", 0), new NetEnd.Pin("not1", 0))
			.connect("out", new NetEnd.Pin("not1", 0), new NetEnd.Port("OUT"))
			.build();
		Structure board = new NaiveRouter().route(new NaivePlacer().place(design));
		check(board.size().equals(new Cell(10, 3, 5)), "NAND board has the floorplan size");
		check(board.inputs().size() == 2 && board.outputs().size() == 1
			&& board.inputs().get(0).equals(new StructurePin(new Cell(2, 0, 4), SOUTH)),
			"board re-exports the floorplan ports");
		check(board.blocks().keySet().stream().anyMatch(p -> p.y() >= 3), "routing happens above layer 0");
		long routedRepeaters = board.blocks().values().stream()
			.filter(b -> b instanceof StructureBlock.Repeater).count();
		check(routedRepeaters >= 1, "strength repair inserted repeaters (gates and h2 vias have none)");

		// a long straight net forces several refreshes; none may be doubled up
		Floorplan longPlan = new Floorplan.Builder(new Cell(16, 3, 3))
			.inputPort("IN", new StructurePin(new Cell(0, 0, 1), Direction.WEST))
			.outputPort("OUT", new StructurePin(new Cell(15, 0, 1), Direction.EAST))
			.build();
		PnrDesign longNet = new PnrDesign.Builder(longPlan)
			.connect("run", new NetEnd.Port("IN"), new NetEnd.Port("OUT"))
			.build();
		Structure longBoard = new NaiveRouter().route(new Placement(longNet, Map.of()));
		long runRepeaters = longBoard.blocks().values().stream()
			.filter(b -> b instanceof StructureBlock.Repeater).count();
		check(runRepeaters >= 2, "long run needs multiple refreshes");
		check(consecutiveRepeaterPairs(longBoard) == 0,
			"no two repeaters land in adjacent cells (the doubled-repeater bug)");

		// trivial case: directly facing pins route with zero pieces
		Floorplan empty = new Floorplan.Builder(new Cell(7, 2, 7)).build();
		PnrDesign facing = new PnrDesign.Builder(empty)
			.component("a", Gates.notGate())
			.component("b", Gates.notGate())
			.connect("n", new NetEnd.Pin("a", 0), new NetEnd.Pin("b", 0))
			.build();
		Placement facingPlacement = new Placement(facing, Map.of(
			"a", placed(Gates.notGate(), 2, 2),
			"b", placed(Gates.notGate(), 2, 3)));
		Structure trivial = new NaiveRouter().route(facingPlacement);
		check(trivial.blocks().size() == 2 * Gates.notGate().blocks().size(),
			"directly facing pins connect with zero pieces");

		// fanout: one source, two sinks, routed with a branch
		PnrDesign fan = new PnrDesign.Builder(new Floorplan.Builder(new Cell(13, 3, 7)).build())
			.component("src", Gates.notGate())
			.component("s1", Gates.notGate())
			.component("s2", Gates.notGate())
			.connect("f", new NetEnd.Pin("src", 0), new NetEnd.Pin("s1", 0), new NetEnd.Pin("s2", 0))
			.build();
		Structure fanned = new NaiveRouter().route(new NaivePlacer().place(fan));
		check(fanned.blocks().size() > 3 * Gates.notGate().blocks().size(),
			"fanout net routes through shared wiring");

		// no routing layers at all
		PnrDesign flat = new PnrDesign.Builder(new Floorplan.Builder(new Cell(9, 1, 5)).build())
			.component("a", Gates.notGate())
			.component("b", Gates.notGate())
			.connect("n", new NetEnd.Pin("a", 0), new NetEnd.Pin("b", 0))
			.build();
		expectRoutingFailure(flat, null, "height-1 floorplan cannot route non-adjacent nets");

		// sink pin walled in by another component
		PnrDesign walled = new PnrDesign.Builder(new Floorplan.Builder(new Cell(9, 3, 7)).build())
			.component("a", Gates.notGate())
			.component("wall", Gates.notGate())
			.component("b", Gates.notGate())
			.connect("n", new NetEnd.Pin("a", 0), new NetEnd.Pin("b", 0))
			.build();
		Placement walledPlacement = new Placement(walled, Map.of(
			"a", placed(Gates.notGate(), 1, 1),
			"b", placed(Gates.notGate(), 5, 5),
			"wall", placed(Gates.notGate(), 5, 4))); // sits exactly on b's faced cell
		expectRoutingFailure(null, walledPlacement, "walled-in sink pin throws");

		// a source that cannot guarantee any strength
		Structure weakSource = new Structure.Builder(new Cell(1, 1, 1))
			.placeBlock(1, 0, 1, StructureBlock.WOOL)
			.placeBlock(1, 1, 1, StructureBlock.REDSTONE_DUST)
			.placeBlock(1, 0, 0, StructureBlock.WOOL)
			.placeBlock(1, 1, 0, StructureBlock.REDSTONE_DUST)
			.output(new StructurePin(new Cell(0, 0, 0), NORTH))
			.inputSignal(3).outputSignal(-14).delayTicks(0)
			.build();
		PnrDesign weak = new PnrDesign.Builder(new Floorplan.Builder(new Cell(9, 3, 7)).build())
			.component("weak", weakSource)
			.component("sink", Gates.notGate())
			.connect("n", new NetEnd.Pin("weak", 0), new NetEnd.Pin("sink", 0))
			.build();
		expectRoutingFailure(weak, null, "hopeless source strength throws");
	}

	private static void fromNetlistChecks() throws Exception {
		var netlist = Netlist.of(RtlilParser
			.parseFile(Path.of("synthesis/tests/rtlil/test_two_bit_adder.rtlil")));
		Floorplan plan = new Floorplan.Builder(new Cell(30, 2, 30))
			.inputPort("a[0]", new StructurePin(new Cell(2, 0, 29), SOUTH))
			.inputPort("a[1]", new StructurePin(new Cell(4, 0, 29), SOUTH))
			.inputPort("b[0]", new StructurePin(new Cell(6, 0, 29), SOUTH))
			.inputPort("b[1]", new StructurePin(new Cell(8, 0, 29), SOUTH))
			.inputPort("cin", new StructurePin(new Cell(10, 0, 29), SOUTH))
			.outputPort("sum[0]", new StructurePin(new Cell(2, 0, 0), NORTH))
			.outputPort("sum[1]", new StructurePin(new Cell(4, 0, 0), NORTH))
			.outputPort("cout", new StructurePin(new Cell(6, 0, 0), NORTH))
			.build();
		PnrDesign design = PnrDesign.fromNetlist(netlist, plan, CellLibrary.standardLibrary());
		check(design.components().size() == 12 && design.nets().size() == 17,
			"fromNetlist lifts the adder netlist (12 components, 17 nets)");
		expectThrow(() -> PnrDesign.fromNetlist(netlist, plan, request -> null),
			"no structure mapped", "fromNetlist rejects unmapped cell kinds");

		var dffNetlist = Netlist.of(RtlilParser.parse("""
			attribute \\top 1
			module \\dff_bridge_test
			  wire input 1 \\clk
			  wire width 4 input 2 \\d
			  wire width 4 output 3 \\q
			  cell \\MC_DFF31 \\ff
			    parameter signed \\WIDTH 4
			    connect \\CLK \\clk
			    connect \\D \\d
			    connect \\Q \\q
			  end
			end
			""", "dff_bridge_test.rtlil"));
		Floorplan dffPlan = new Floorplan.Builder(new Cell(20, 3, 20))
			.inputPort("clk", new StructurePin(new Cell(2, 0, 19), SOUTH))
			.inputPort("d[0]", new StructurePin(new Cell(4, 0, 19), SOUTH))
			.inputPort("d[1]", new StructurePin(new Cell(6, 0, 19), SOUTH))
			.inputPort("d[2]", new StructurePin(new Cell(8, 0, 19), SOUTH))
			.inputPort("d[3]", new StructurePin(new Cell(10, 0, 19), SOUTH))
			.outputPort("q[0]", new StructurePin(new Cell(4, 0, 0), NORTH))
			.outputPort("q[1]", new StructurePin(new Cell(6, 0, 0), NORTH))
			.outputPort("q[2]", new StructurePin(new Cell(8, 0, 0), NORTH))
			.outputPort("q[3]", new StructurePin(new Cell(10, 0, 0), NORTH))
			.build();
		PnrDesign dffDesign = PnrDesign.fromNetlist(
			dffNetlist, dffPlan, CellLibrary.standardLibrary());
		check(dffDesign.nets().stream().anyMatch(net ->
				net.source().equals(new NetEnd.Port("clk"))
					&& net.sinks().contains(new NetEnd.Pin("mc_dff310", 0)))
			&& dffDesign.nets().stream().anyMatch(net ->
				net.source().equals(new NetEnd.Port("d[3]"))
					&& net.sinks().contains(new NetEnd.Pin("mc_dff310", 4)))
			&& dffDesign.nets().stream().anyMatch(net ->
				net.source().equals(new NetEnd.Pin("mc_dff310", 3))
					&& net.sinks().contains(new NetEnd.Port("q[3]"))),
			"fromNetlist maps vector DFF pins in canonical CLK, D, Q order");
		expectThrow(() -> PnrDesign.fromNetlist(dffNetlist, dffPlan,
				request -> Gates.notGate()),
			"port contract requires 5", "fromNetlist rejects wrong library pin counts");

		Structure lowConstant = Gates.constant(false);
		Structure highConstant = Gates.constant(true);
		check(lowConstant.blocks().size() == 1
				&& lowConstant.blockAt(new BlockPos(1, 1, 1)).orElse(null) instanceof StructureBlock.Wool
				&& highConstant.blocks().size() == 2
				&& highConstant.blockAt(new BlockPos(1, 1, 0)).orElse(null)
					instanceof StructureBlock.RedstoneTorch torch
				&& torch.wallAttachment().orElse(null) == SOUTH,
			"constant structures retain center wool and high adds a north-face torch");

		var constantNetlist = Netlist.of(RtlilParser.parse("""
			attribute \\top 1
			module \\constant_bridge_test
			  wire output 1 \\low
			  wire output 2 \\high
			  connect \\low 1'0
			  connect \\high 1'1
			end
			""", "constant_bridge_test.rtlil"));
		Floorplan constantPlan = new Floorplan.Builder(new Cell(10, 3, 10))
			.outputPort("low", new StructurePin(new Cell(2, 0, 0), NORTH))
			.outputPort("high", new StructurePin(new Cell(4, 0, 0), NORTH))
			.build();
		CellLibrary standardLibrary = CellLibrary.standardLibrary();
		int[] constantRequests = {0};
		CellLibrary trackingLibrary = request -> {
			if (request instanceof CellLibrary.Constant)
				constantRequests[0]++;
			return standardLibrary.structureFor(request);
		};
		PnrDesign constantDesign = PnrDesign.fromNetlist(
			constantNetlist, constantPlan, trackingLibrary);
		long constantTorches = constantDesign.components().values().stream()
			.flatMap(structure -> structure.blocks().values().stream())
			.filter(block -> block instanceof StructureBlock.RedstoneTorch)
			.count();
		check(constantDesign.components().size() == 2 && constantDesign.nets().size() == 2
				&& constantDesign.components().values().stream().allMatch(structure ->
					structure.inputs().isEmpty() && structure.outputs().size() == 1
						&& structure.blockAt(new BlockPos(1, 1, 1))
							.orElse(null) instanceof StructureBlock.Wool)
				&& constantTorches == 1 && constantRequests[0] == 2,
			"fromNetlist materializes low and high constant sources");

		// Regression: count[3]'s second fanout branch reaches its sink via
		// directly from a weak branch seed. The router must detour to make a
		// wire cell available for strength repair instead of exhausting.
		var counterNetlist = Netlist.of(RtlilParser.parseFile(
			Path.of("synthesis/tests/rtlil/test_synchronous_counter.rtlil")));
		Floorplan counterPlan = new Floorplan.Builder(new Cell(32, 10, 48))
			.inputPort("clk", new StructurePin(new Cell(8, 0, 47), SOUTH))
			.inputPort("rst_n", new StructurePin(new Cell(16, 0, 47), SOUTH))
			.outputPort("count[0]", new StructurePin(new Cell(8, 0, 0), NORTH))
			.outputPort("count[1]", new StructurePin(new Cell(12, 0, 0), NORTH))
			.outputPort("count[2]", new StructurePin(new Cell(16, 0, 0), NORTH))
			.outputPort("count[3]", new StructurePin(new Cell(20, 0, 0), NORTH))
			.build();
		PnrDesign counterDesign = PnrDesign.fromNetlist(
			counterNetlist, counterPlan, CellLibrary.standardLibrary());
		Structure counterBoard = new NaiveRouter().route(
			new NaivePlacer(8).place(counterDesign));
		check(counterBoard.outputs().size() == 4,
			"strength repair detours when an adjacent sink via has no repeater site");
	}

	/** Number of face-adjacent cell pairs that both contain a repeater. */
	private static int consecutiveRepeaterPairs(Structure board) {
		Set<Cell> repeaterCells = new HashSet<>();
		board.blocks().forEach((pos, block) -> {
			if (block instanceof StructureBlock.Repeater)
				repeaterCells.add(pos.cell());
		});
		int pairs = 0;
		for (Cell cell : repeaterCells)
			for (Cell delta : List.of(new Cell(1, 0, 0), new Cell(0, 1, 0), new Cell(0, 0, 1)))
				if (repeaterCells.contains(cell.plus(delta)))
					pairs++;
		return pairs;
	}

	private static void expectRoutingFailure(PnrDesign design, Placement placement, String label) {
		checks++;
		try {
			Placement p = placement != null ? placement : new NaivePlacer().place(design);
			new NaiveRouter().route(p);
			failures++;
			System.out.println("FAIL " + label + " (routed unexpectedly)");
		} catch (RoutingException e) {
			System.out.println("PASS " + label);
		} catch (Exception e) {
			failures++;
			System.out.println("FAIL " + label + " (wrong exception: " + e + ")");
		}
	}

	// ---- helpers ----

	private static void expectThrow(ThrowingRunnable action, String messagePart, String label) {
		checks++;
		try {
			action.run();
			failures++;
			System.out.println("FAIL " + label + " (no exception thrown)");
		} catch (Exception e) {
			if (e.getMessage() != null && e.getMessage().contains(messagePart)) {
				System.out.println("PASS " + label);
			} else {
				failures++;
				System.out.println("FAIL " + label + " (wrong message: " + e.getMessage() + ")");
			}
		}
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static void check(boolean condition, String label) {
		checks++;
		if (condition) {
			System.out.println("PASS " + label);
		} else {
			failures++;
			System.out.println("FAIL " + label);
		}
	}
}
