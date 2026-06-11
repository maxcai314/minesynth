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
 * Assembles a board where signals route over and under each other using vias,
 * and prints the in-game build tutorial. Three independent lines that must
 * not interfere:
 * <ul>
 * <li>A (LIME): west to east on the ground along the middle row.</li>
 * <li>B (CYAN): south to north at x=1, hopping over A with height-2 vias
 *     (up, bridge wire at level 1, down). Pure dust, 13 of the 15 strength
 *     budget.</li>
 * <li>C (ORANGE): south to north at x=3, hopping over A with height-3 vias.
 *     Tall spirals refresh themselves with internal repeaters; the level-2
 *     bridge is a {@link Wires#repeaterWire}, demonstrating the
 *     entrance-repeater wire component as well.</li>
 * </ul>
 *
 * <p>Toggling any one lever must change only that line's output. Components
 * are declared once as {@link PlacedStructure}s; board pins and hookup
 * instructions are derived from them.
 */
public final class ViaBoardDemo {
	public static void main(String[] args) {
		// line A: four straight wires west to east along z=1
		List<PlacedStructure> lineA = new ArrayList<>();
		for (int x = 0; x < 4; x++)
			lineA.add(new PlacedStructure(Wires.wire(Direction.WEST, Direction.EAST),
				new Cell(x, 0, 1), Direction.NORTH, BlockColor.LIME));

		// line B: height-2 hop over A at x=1
		PlacedStructure upB = new PlacedStructure(Vias.upward(2, Direction.SOUTH, Direction.NORTH),
			new Cell(1, 0, 2), Direction.NORTH, BlockColor.CYAN);
		PlacedStructure bridgeB = new PlacedStructure(Wires.wire(Direction.SOUTH, Direction.NORTH),
			new Cell(1, 1, 1), Direction.NORTH, BlockColor.CYAN);
		PlacedStructure downB = new PlacedStructure(Vias.downward(2, Direction.SOUTH, Direction.NORTH),
			new Cell(1, 0, 0), Direction.NORTH, BlockColor.CYAN);

		// line C: height-3 hop over A at x=3, refreshed mid-bridge
		PlacedStructure upC = new PlacedStructure(Vias.upward(3, Direction.SOUTH, Direction.NORTH),
			new Cell(3, 0, 2), Direction.NORTH, BlockColor.ORANGE);
		PlacedStructure bridgeC = new PlacedStructure(Wires.repeaterWire(Direction.SOUTH, Direction.NORTH),
			new Cell(3, 2, 1), Direction.NORTH, BlockColor.ORANGE);
		PlacedStructure downC = new PlacedStructure(Vias.downward(3, Direction.SOUTH, Direction.NORTH),
			new Cell(3, 0, 0), Direction.NORTH, BlockColor.ORANGE);

		Structure.Builder builder = new Structure.Builder(new Cell(4, 3, 3)).contained(false);
		lineA.forEach(builder::place);
		builder.place(upB).place(bridgeB).place(downB)
			.place(upC).place(bridgeC).place(downC)
			.addInputs(lineA.getFirst().inputPins())   // A
			.addInputs(upB.inputPins())                // B
			.addInputs(upC.inputPins())                // C
			.addOutputs(lineA.getLast().outputPins())
			.addOutputs(downB.outputPins())
			.addOutputs(downC.outputPins());
		Structure board = builder.build();

		System.out.println("via demo board: three independent crossings");
		System.out.println();
		System.out.println(BuildGuide.compassDiagram());
		System.out.println(BuildGuide.render(board));
		System.out.println("component colors:");
		System.out.println("  LIME   = line A, west to east on the ground");
		System.out.println("  CYAN   = line B, south to north over A (height-2 vias, pure dust)");
		System.out.println("  ORANGE = line C, south to north over A (height-3 vias + repeater bridge)");
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
		System.out.println("  each lever drives only its own lamp; toggling one line");
		System.out.println("  must never change the other two outputs");
		System.out.println();
		System.out.println("signal strength notes:");
		System.out.println("  line B path is 13 dust total, inside the 15 budget with no repeater");
		System.out.println("  line C's height-3 spirals carry an internal repeater each (look for the");
		System.out.println("  arrow glyphs inside the via), and the bridge repeater refreshes again,");
		System.out.println("  so every dust run stays well under budget");
	}

	private static String hookup(StructurePin pin) {
		return "power the dust at block " + block(pin.connectionBlock()) + ", e.g. a lever just "
			+ pin.face().toString().toLowerCase(Locale.ROOT) + " of it";
	}

	private static String block(BlockPos position) {
		return "(" + position.x() + ", " + position.y() + ", " + position.z() + ")";
	}
}
