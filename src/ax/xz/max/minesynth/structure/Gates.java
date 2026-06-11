package ax.xz.max.minesynth.structure;

import static ax.xz.max.minesynth.structure.StructureBlock.REDSTONE_DUST;
import static ax.xz.max.minesynth.structure.StructureBlock.WOOL;

/**
 * Reference logic gate structures. These are demo-grade designs for proving
 * out the modeling layer; treat them as drafts until verified in game (see
 * {@link BuildGuide}). The real synthesis cell library (MC_* cells) comes in a
 * later phase and will live elsewhere.
 */
public final class Gates {
	private Gates() {}

	/**
	 * Inverter, 1x1x1, contained: input dust powers the center wool, which
	 * shuts off a torch hanging on its south face. Input NORTH, output SOUTH.
	 */
	public static Structure notGate() {
		return new Structure.Builder(new Cell(1, 1, 1))
			.placeBlock(1, 0, 0, WOOL)
			.placeBlock(1, 1, 0, REDSTONE_DUST)
			.placeBlock(1, 1, 1, WOOL)
			.placeBlock(1, 1, 2, StructureBlock.RedstoneTorch.onWall(Direction.NORTH))
			.input(new StructurePin(new Cell(0, 0, 0), Direction.NORTH))
			.output(new StructurePin(new Cell(0, 0, 0), Direction.SOUTH))
			.build();
	}

	/**
	 * AND gate, 2x1x1, not contained (it uses the top of its cells). The
	 * classic two-torch design: each input shuts off its inverter torch; the
	 * torches feed an elevated line that is high when either input is low;
	 * the line powers the wool holding the output torch. Out = A and B.
	 *
	 * <p>Pins follow the canonical example: inputs south of both cells,
	 * output north of cell (0,0,0).
	 */
	public static Structure andGate() {
		return new Structure.Builder(new Cell(2, 1, 1))
			.contained(false)
			// input A (west column)
			.placeBlock(1, 0, 2, WOOL)
			.placeBlock(1, 1, 2, REDSTONE_DUST)
			.placeBlock(1, 1, 1, WOOL)
			.placeBlock(1, 2, 1, StructureBlock.RedstoneTorch.onFloor())
			// input B (east column)
			.placeBlock(4, 0, 2, WOOL)
			.placeBlock(4, 1, 2, REDSTONE_DUST)
			.placeBlock(4, 1, 1, WOOL)
			.placeBlock(4, 2, 1, StructureBlock.RedstoneTorch.onFloor())
			// elevated NOR line between the inverter torches
			.placeBlock(2, 1, 1, WOOL)
			.placeBlock(3, 1, 1, WOOL)
			.placeBlock(2, 2, 1, REDSTONE_DUST)
			.placeBlock(3, 2, 1, REDSTONE_DUST)
			// output inverter: line dust powers the wool below it
			.placeBlock(2, 1, 0, StructureBlock.RedstoneTorch.onWall(Direction.SOUTH))
			.placeBlock(1, 0, 0, WOOL)
			.placeBlock(1, 1, 0, REDSTONE_DUST)
			.input(new StructurePin(new Cell(0, 0, 0), Direction.SOUTH))
			.input(new StructurePin(new Cell(1, 0, 0), Direction.SOUTH))
			.output(new StructurePin(new Cell(0, 0, 0), Direction.NORTH))
			.build();
	}

	/**
	 * OR gate, 2x1x1, not contained (the merge row runs along its north
	 * shell). Each input goes through a repeater (preventing backfeed into
	 * the other input) onto a shared dust row. Out = A or B.
	 */
	public static Structure orGate() {
		return new Structure.Builder(new Cell(2, 1, 1))
			.contained(false)
			// input A straight through a repeater to the output port
			.placeBlock(1, 0, 2, WOOL)
			.placeBlock(1, 1, 2, REDSTONE_DUST)
			.placeBlock(1, 0, 1, WOOL)
			.placeBlock(1, 1, 1, new StructureBlock.Repeater(Direction.NORTH, 1))
			.placeBlock(1, 0, 0, WOOL)
			.placeBlock(1, 1, 0, REDSTONE_DUST)
			// input B through a repeater, then west along the north row
			.placeBlock(4, 0, 2, WOOL)
			.placeBlock(4, 1, 2, REDSTONE_DUST)
			.placeBlock(4, 0, 1, WOOL)
			.placeBlock(4, 1, 1, new StructureBlock.Repeater(Direction.NORTH, 1))
			.placeBlock(4, 0, 0, WOOL)
			.placeBlock(4, 1, 0, REDSTONE_DUST)
			.placeBlock(3, 0, 0, WOOL)
			.placeBlock(3, 1, 0, REDSTONE_DUST)
			.placeBlock(2, 0, 0, WOOL)
			.placeBlock(2, 1, 0, REDSTONE_DUST)
			.input(new StructurePin(new Cell(0, 0, 0), Direction.SOUTH))
			.input(new StructurePin(new Cell(1, 0, 0), Direction.SOUTH))
			.output(new StructurePin(new Cell(0, 0, 0), Direction.NORTH))
			.build();
	}
}
