package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.pnr.Floorplan;
import ax.xz.max.minesynth.pnr.NaivePlacer;
import ax.xz.max.minesynth.pnr.NaiveRouter;
import ax.xz.max.minesynth.pnr.NetEnd;
import ax.xz.max.minesynth.pnr.Placement;
import ax.xz.max.minesynth.pnr.PnrDesign;
import ax.xz.max.minesynth.schematic.SchematicWriter;
import ax.xz.max.minesynth.structure.BuildGuide;
import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.Direction;
import ax.xz.max.minesynth.structure.Gates;
import ax.xz.max.minesynth.structure.Structure;
import ax.xz.max.minesynth.structure.StructurePin;

import java.nio.file.Path;

/**
 * The full pipeline on the NAND circuit: a hand-written {@link PnrDesign}
 * (the same logic the hand-assembled GateBoardDemo builds) is machine-placed
 * by {@link NaivePlacer} and machine-routed by {@link NaiveRouter}, then
 * printed as a build tutorial and exported as a schematic. out = NOT(A AND B).
 */
public final class PnrDemo {
	public static void main(String[] args) throws Exception {
		Floorplan floorplan = new Floorplan.Builder(new Cell(10, 3, 5))
			.inputPort("A", new StructurePin(new Cell(2, 0, 4), Direction.SOUTH))
			.inputPort("B", new StructurePin(new Cell(4, 0, 4), Direction.SOUTH))
			.outputPort("OUT", new StructurePin(new Cell(6, 0, 4), Direction.SOUTH))
			.build();

		PnrDesign design = new PnrDesign.Builder(floorplan)
			.component("and1", Gates.andGate())
			.component("not1", Gates.notGate())
			.connect("a_in", new NetEnd.Port("A"), new NetEnd.Pin("and1", 0))
			.connect("b_in", new NetEnd.Port("B"), new NetEnd.Pin("and1", 1))
			.connect("and_to_not", new NetEnd.Pin("and1", 0), new NetEnd.Pin("not1", 0))
			.connect("out", new NetEnd.Pin("not1", 0), new NetEnd.Port("OUT"))
			.build();

		Placement placement = new NaivePlacer().place(design);
		System.out.println("placement:");
		placement.placements().forEach((name, placed) ->
			System.out.println("  " + name + " at " + placed.position() + " (" + placed.color() + ")"));
		System.out.println();

		Structure board = new NaiveRouter().route(placement);

		System.out.println("machine-placed and machine-routed NAND: out = NOT(A AND B)");
		System.out.println();
		System.out.println(BuildGuide.compassDiagram());
		System.out.println(BuildGuide.render(board));
		System.out.println("board ports (in floorplan declaration order):");
		floorplan.inputPorts().forEach((name, pin) ->
			System.out.println("  input " + name + ": dust at block " + block(pin)));
		floorplan.outputPorts().forEach((name, pin) ->
			System.out.println("  output " + name + ": dust at block " + block(pin)));
		System.out.println();
		System.out.println("expected truth table:");
		System.out.println("  A B | OUT");
		System.out.println("  0 0 |  1");
		System.out.println("  0 1 |  1");
		System.out.println("  1 0 |  1");
		System.out.println("  1 1 |  0");

		Path schematicFile = Path.of("out", "pnr-nand.schematic");
		SchematicWriter.write(board, schematicFile);
		System.out.println();
		System.out.println("wrote " + schematicFile + " (worldedit: //schem load pnr-nand, then //paste)");
	}

	private static String block(StructurePin pin) {
		var b = pin.connectionBlock();
		return "(" + b.x() + ", " + b.y() + ", " + b.z() + ")";
	}
}
