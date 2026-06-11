package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.structure.BlockColor;
import ax.xz.max.minesynth.structure.BlockPos;
import ax.xz.max.minesynth.structure.BuildGuide;
import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.Direction;
import ax.xz.max.minesynth.structure.PlacedStructure;
import ax.xz.max.minesynth.structure.Structure;
import ax.xz.max.minesynth.structure.StructurePin;
import ax.xz.max.minesynth.structure.Vias;
import ax.xz.max.minesynth.structure.Wires;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Routes three independent lines diagonally across a 3x3x3-cell board, each
 * crossing over the others through vias, and prints the build tutorial:
 * <ul>
 * <li>A (LIME): west edge to east edge, straight along the ground.</li>
 * <li>B (CYAN): in at the south edge's west side, up a height-2 via, east
 *     across level 1, down a via to the north edge's east side.</li>
 * <li>C (ORANGE): in at the north edge's west side, up a height-3 via, east
 *     across level 2, down a via to the south edge's east side.</li>
 * </ul>
 * Both elevated runs are corner wire, repeater wire, corner wire; without the
 * mid-run refresh line B would be 19 dust end to end, past the budget.
 * Toggling any one lever must change only that line's output.
 */
public final class ViaBoardDemo {
	public static void main(String[] args) {
		List<PlacedStructure> lineA = new ArrayList<>();
		for (int x = 0; x < 3; x++)
			lineA.add(new PlacedStructure(Wires.wire(Direction.WEST, Direction.EAST),
				new Cell(x, 0, 1), Direction.NORTH, BlockColor.LIME));

		PlacedStructure upB = new PlacedStructure(Vias.upward(2, Direction.SOUTH, Direction.NORTH),
			new Cell(0, 0, 2), Direction.NORTH, BlockColor.CYAN);
		PlacedStructure westB = new PlacedStructure(Wires.wire(Direction.SOUTH, Direction.EAST),
			new Cell(0, 1, 1), Direction.NORTH, BlockColor.CYAN);
		PlacedStructure midB = new PlacedStructure(Wires.repeaterWire(Direction.WEST, Direction.EAST),
			new Cell(1, 1, 1), Direction.NORTH, BlockColor.CYAN);
		PlacedStructure eastB = new PlacedStructure(Wires.wire(Direction.WEST, Direction.NORTH),
			new Cell(2, 1, 1), Direction.NORTH, BlockColor.CYAN);
		PlacedStructure downB = new PlacedStructure(Vias.downward(2, Direction.SOUTH, Direction.NORTH),
			new Cell(2, 0, 0), Direction.NORTH, BlockColor.CYAN);

		PlacedStructure upC = new PlacedStructure(Vias.upward(3, Direction.NORTH, Direction.SOUTH),
			new Cell(0, 0, 0), Direction.NORTH, BlockColor.ORANGE);
		PlacedStructure westC = new PlacedStructure(Wires.wire(Direction.NORTH, Direction.EAST),
			new Cell(0, 2, 1), Direction.NORTH, BlockColor.ORANGE);
		PlacedStructure midC = new PlacedStructure(Wires.repeaterWire(Direction.WEST, Direction.EAST),
			new Cell(1, 2, 1), Direction.NORTH, BlockColor.ORANGE);
		PlacedStructure eastC = new PlacedStructure(Wires.wire(Direction.WEST, Direction.SOUTH),
			new Cell(2, 2, 1), Direction.NORTH, BlockColor.ORANGE);
		PlacedStructure downC = new PlacedStructure(Vias.downward(3, Direction.NORTH, Direction.SOUTH),
			new Cell(2, 0, 2), Direction.NORTH, BlockColor.ORANGE);

		Structure.Builder builder = new Structure.Builder(new Cell(3, 3, 3)).contained(false);
		lineA.forEach(builder::place);
		builder.place(upB).place(westB).place(midB).place(eastB).place(downB)
			.place(upC).place(westC).place(midC).place(eastC).place(downC)
			.addInputs(lineA.getFirst().inputPins())
			.addInputs(upB.inputPins())
			.addInputs(upC.inputPins())
			.addOutputs(lineA.getLast().outputPins())
			.addOutputs(downB.outputPins())
			.addOutputs(downC.outputPins());
		Structure board = builder.build();

		System.out.println("via demo board: three lines crossing diagonally");
		System.out.println();
		System.out.println(BuildGuide.compassDiagram());
		System.out.println(BuildGuide.render(board));
		System.out.println("component colors:");
		System.out.println("  LIME   = line A, west to east on the ground");
		System.out.println("  CYAN   = line B, south-west corner over to north-east (level 1, height-2 vias)");
		System.out.println("  ORANGE = line C, north-west corner over to south-east (level 2, height-3 vias)");
		System.out.println();
		System.out.println("in-game hookup (lamps next to the outputs):");
		System.out.println("  line A in:  " + hookup(lineA.getFirst().inputPins().getFirst()));
		System.out.println("  line B in:  " + hookup(upB.inputPins().getFirst()));
		System.out.println("  line C in:  " + hookup(upC.inputPins().getFirst()));
		System.out.println("  line A out: dust at block " + block(lineA.getLast().outputPins().getFirst().connectionBlock()));
		System.out.println("  line B out: dust at block " + block(downB.outputPins().getFirst().connectionBlock()));
		System.out.println("  line C out: dust at block " + block(downC.outputPins().getFirst().connectionBlock()));
		System.out.println();
		System.out.println("expected behavior:");
		System.out.println("  each lever drives only its own lamp, through two corners and");
		System.out.println("  (for B and C) a climb and a descent; no line may affect another");
	}

	private static String hookup(StructurePin pin) {
		return "power the dust at block " + block(pin.connectionBlock()) + ", e.g. a lever just "
			+ pin.face().toString().toLowerCase(Locale.ROOT) + " of it";
	}

	private static String block(BlockPos position) {
		return "(" + position.x() + ", " + position.y() + ", " + position.z() + ")";
	}
}
