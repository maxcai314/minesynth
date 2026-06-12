package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.schematic.SchematicWriter;
import ax.xz.max.minesynth.structure.BlockColor;
import ax.xz.max.minesynth.structure.BlockPos;
import ax.xz.max.minesynth.structure.BuildGuide;
import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.Direction;
import ax.xz.max.minesynth.structure.Gates;
import ax.xz.max.minesynth.structure.PlacedStructure;
import ax.xz.max.minesynth.structure.Structure;
import ax.xz.max.minesynth.structure.StructurePin;
import ax.xz.max.minesynth.structure.Wires;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Assembles a NAND board out of gate and wire components and prints the
 * in-game build tutorial: out = NOT(A AND B). Build it in a Minecraft world
 * to verify the structure modeling (and the gate designs) for real.
 *
 * <p>Layout, 2x1x3 cells: B enters the south edge through a straight wire,
 * A enters the east edge through an L wire, both feed the AND gate in the
 * middle row; a NOT gate rotated to face SOUTH inverts the result out the
 * north edge.
 */
public final class GateBoardDemo {
	public static void main(String[] args) throws Exception {
		PlacedStructure wireB = new PlacedStructure(Wires.wire(Direction.SOUTH, Direction.NORTH),
			new Cell(0, 0, 2), Direction.NORTH, BlockColor.CYAN);
		PlacedStructure wireA = new PlacedStructure(Wires.wire(Direction.EAST, Direction.NORTH),
			new Cell(1, 0, 2), Direction.NORTH, BlockColor.YELLOW);
		PlacedStructure andGate = new PlacedStructure(Gates.andGate(),
			new Cell(0, 0, 1), Direction.NORTH, BlockColor.LIME);
		PlacedStructure notGate = new PlacedStructure(Gates.notGate(),
			new Cell(0, 0, 0), Direction.SOUTH, BlockColor.RED);

		Structure board = new Structure.Builder(new Cell(2, 1, 3))
			.contained(false) // the AND gate uses the top of the board volume
			.place(wireB)
			.place(wireA)
			.place(andGate)
			.place(notGate)
			.addInputs(wireB.inputPins())     // B
			.addInputs(wireA.inputPins())     // A
			.addOutputs(notGate.outputPins())
			.build();

		System.out.println("NAND demo board: out = NOT(A AND B)");
		System.out.println();
		System.out.println(BuildGuide.compassDiagram());
		System.out.println(BuildGuide.render(board));
		System.out.println("component colors:");
		System.out.println("  CYAN   = input B wire (south edge, straight)");
		System.out.println("  YELLOW = input A wire (east edge, L-shaped)");
		System.out.println("  LIME   = AND gate (middle row)");
		System.out.println("  RED    = NOT gate (north row, rotated 180 degrees)");
		System.out.println();
		System.out.println("in-game hookup:");
		System.out.println("  input B: power the dust at block " + hookup(wireB.inputPins().getFirst()));
		System.out.println("  input A: power the dust at block " + hookup(wireA.inputPins().getFirst()));
		System.out.println("  output:  the torch at block "
			+ block(notGate.outputPins().getFirst().connectionBlock())
			+ "; run dust north from it into a lamp");
		System.out.println();
		System.out.println("expected truth table:");
		System.out.println("  A B | out");
		System.out.println("  0 0 |  1");
		System.out.println("  0 1 |  1");
		System.out.println("  1 0 |  1");
		System.out.println("  1 1 |  0");

		Path schematicFile = Path.of("out", "gate-board.schematic");
		SchematicWriter.write(board, schematicFile);
		System.out.println();
		System.out.println("wrote " + schematicFile + " (worldedit: //schem load gate-board, then //paste)");
	}

	private static String hookup(StructurePin pin) {
		return block(pin.connectionBlock()) + ", e.g. a lever on a block just "
			+ pin.face().toString().toLowerCase(Locale.ROOT) + " of it";
	}

	private static String block(BlockPos position) {
		return "(" + position.x() + ", " + position.y() + ", " + position.z() + ")";
	}
}
