package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.pnr.Floorplan;
import ax.xz.max.minesynth.pnr.NaivePlacer;
import ax.xz.max.minesynth.pnr.NaiveRouter;
import ax.xz.max.minesynth.pnr.NetEnd;
import ax.xz.max.minesynth.pnr.Placement;
import ax.xz.max.minesynth.pnr.PlacementException;
import ax.xz.max.minesynth.pnr.PnrDesign;
import ax.xz.max.minesynth.pnr.RoutingException;
import ax.xz.max.minesynth.structure.BlockColor;
import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.Direction;
import ax.xz.max.minesynth.structure.Gates;
import ax.xz.max.minesynth.structure.PlacedStructure;
import ax.xz.max.minesynth.structure.Structure;
import ax.xz.max.minesynth.structure.StructureBlock;
import ax.xz.max.minesynth.structure.StructurePin;
import ax.xz.max.minesynth.structure.Wires;

import java.util.Map;

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
		var netlist = ax.xz.max.minesynth.netlist.Netlist.of(ax.xz.max.minesynth.rtlil.RtlilParser
			.parseFile(java.nio.file.Path.of("synthesis/tests/rtlil/test_two_bit_adder.rtlil")));
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
		var library = Map.of(
			ax.xz.max.minesynth.netlist.CellKind.GATE_AND, Gates.andGate(),
			ax.xz.max.minesynth.netlist.CellKind.GATE_OR, Gates.orGate(),
			ax.xz.max.minesynth.netlist.CellKind.GATE_XOR, Gates.andGate()); // pin-compatible stand-in
		PnrDesign design = PnrDesign.fromNetlist(netlist, plan, library);
		check(design.components().size() == 12 && design.nets().size() == 17,
			"fromNetlist lifts the adder netlist (12 components, 17 nets)");
		expectThrow(() -> PnrDesign.fromNetlist(netlist, plan, Map.of()),
			"no structure mapped", "fromNetlist rejects unmapped cell kinds");
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
