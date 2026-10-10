package ax.xz.max.minesynth.structure;

import static ax.xz.max.minesynth.structure.StructureBlock.*;

/**
 * Reference logic and sequential cell structures used by the standard cell
 * library. These are demo-grade designs for proving out the modeling layer;
 * treat them as drafts until verified in game (see {@link BuildGuide}).
 */
public final class Gates {
	private Gates() {}

	/**
	 * Positive-edge-triggered D flip-flop bank with {@code width} independent
	 * data bits and one shared clock. On each low-to-high transition of CLK, it
	 * samples every D bit simultaneously and stores those values on the matching
	 * Q outputs. Q does not change otherwise. This cell has no
	 * reset input; the simulator assumes it powers up at zero.
	 *
	 * <p>The structure is {@code width + 2 x 1 x 3} cells.
	 * D inputs enter from the south, Q outputs leave from the north,
	 * and CLK enters from the west side of the center row.
	 *
	 * <p>Pin ordering follows the cell-library contract. Inputs are CLK first,
	 * then D[0] through D[width - 1]. Outputs are Q[0] through Q[width - 1].
	 * Physical pin locations are implementation-defined,
	 * but this list ordering must remain stable.
	 */
	public static Structure dff(int width) {
		if (width < 1 || width > 31)
			throw new IllegalArgumentException("DFF width must be between 1 and 31, got " + width);

		// our implementation uses width + 2 cells in the X direction, and 2 cells of height.
		Structure.Builder builder = new Structure.Builder(new Cell(width + 2, 2, 1))
				.inputSignal(2)
				.outputSignal(14)
				.delayTicks(10 * width / 4) // conservative estimate
				.horizontallyContained(true);

		// input CLK from the west (cell x=0), with pulse generator to drive memory cells
		builder.input(new StructurePin(new Cell(0, 0, 0), Direction.WEST))
				.placeBlock(0, 0, 1, WOOL)
				.placeBlock(0, 1, 1, REDSTONE_DUST)
				.placeBlock(1, 1, 1, WOOL)
				.placeBlock(1, 2, 1, RedstoneTorch.onFloor())
				.placeBlock(1, 3, 1, WOOL)
				.placeBlock(2, 0, 1, WOOL)
				.placeBlock(2, 1, 1, new Repeater(Direction.EAST, 4)) // delay for pulse gen
				.placeBlock(2, 2, 1, WOOL)
				.placeBlock(2, 3, 1, REDSTONE_DUST)
				.placeBlock(3, 1, 1, WOOL)
				.placeBlock(3, 2, 1, REDSTONE_DUST);

		// now, generate each memory cell, with input D from south and output Q to north.
		for (int i = 0; i < width; i++) {
			int originX = 3 * i + 4;
			Cell originCell = new Cell(i + 1, 0, 0);

			// build the memory cell, wiring the input to the output, south to north
			builder.input(new StructurePin(originCell, Direction.SOUTH))
					.placeBlock(originX, 0, 2, WOOL)
					.placeBlock(originX, 1, 2, REDSTONE_DUST)
					.placeBlock(originX, 0, 1, WOOL)
					.placeBlock(originX, 1, 1, new Repeater(Direction.NORTH, 1))
					.placeBlock(originX, 0, 0, WOOL)
					.placeBlock(originX, 1, 0, REDSTONE_DUST)
					.output(new StructurePin(originCell, Direction.NORTH));

			// build the driver for the cell, locking the repeater
			builder.placeBlock(originX + 1, 0, 1, WOOL)
					.placeBlock(originX + 1, 1, 1, new Repeater(Direction.WEST, 1))
					.placeBlock(originX + 2, 1, 1, WOOL)
					.placeBlock(originX + 2, 2, 1, REDSTONE_DUST);

			// connect the clock to the driver, with a repeater every 4 units.
			if ((i + 1) % 4 == 0) {
				builder.placeBlock(originX, 2, 1, WOOL)
						.placeBlock(originX, 3, 1, REDSTONE_DUST)
						.placeBlock(originX + 1, 2, 1, WOOL)
						.placeBlock(originX + 1, 3, 1, new Repeater(Direction.EAST, 1))
						.placeBlock(originX + 2, 3, 1, WOOL);
			} else {
				builder.placeBlock(originX, 2, 1, WOOL)
						.placeBlock(originX, 3, 1, REDSTONE_DUST)
						.placeBlock(originX + 1, 2, 1, WOOL)
						.placeBlock(originX + 1, 3, 1, REDSTONE_DUST);
			}
		}

		return builder.build();
	}

	/**
	 * A one-cell constant source with no inputs and one north-facing output.
	 * Both values retain a glass block at the center for a visible physical
	 * marker. A true source adds a wall torch on the wool's north face; a false
	 * source leaves that output position as air.
	 */
	public static Structure constant(boolean value) {
		StructureBlock block = new Glass(value ? BlockColor.WHITE : BlockColor.BLACK);
		Structure.Builder builder = new Structure.Builder(new Cell(1, 1, 1))
				.horizontallyContained(true)
				.inputSignal(1).outputSignal(14).delayTicks(0)
				.output(new StructurePin(new Cell(0, 0, 0), Direction.NORTH));
		builder.placeBlock(1, 1, 1, block); // center block
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				builder.placeBlock(i, 0, j, block); // platform
			}
		}
		if (value)
			builder.placeBlock(1, 1, 0, RedstoneTorch.onWall(Direction.SOUTH));
		return builder.build();
	}

	/**
	 * Inverter, 1x1x1, contained: input dust powers the center wool, which
	 * shuts off a torch hanging on its south face. Input NORTH, output SOUTH.
	 */
	public static Structure notGate() {
		return new Structure.Builder(new Cell(1, 1, 1))
			.inputSignal(2).outputSignal(14).delayTicks(1)
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
	 * output north of cell (0,0,0). The inverter torches and the elevated NOR
	 * line sit on the top shell, so nothing may be placed directly above it.
	 */
	public static Structure andGate() {
		return new Structure.Builder(new Cell(2, 1, 1))
			.horizontallyContained(false)
			.allowsAbove(false)
			.inputSignal(2).outputSignal(13).delayTicks(2)
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
			.horizontallyContained(false)
			.inputSignal(2).outputSignal(11).delayTicks(1)
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

	/**
	 * XOR gate, 2x1x2, contained.
	 * This design uses 3 layers of torches in different combinations to
	 * drive the output. No torches are placed on the 3rd layer, so
	 * structures may be placed directly above it.
	 *
	 * <p>Pins follow the canonical example: inputs south of both cells,
	 * output north of cell (0,0,0).
	 */
	public static Structure xorGate() {
		return new Structure.Builder(new Cell(2, 1, 2))
				.horizontallyContained(true)
				.allowsAbove(true)
				.inputSignal(2).outputSignal(14).delayTicks(3)
				// input A (west column)
				.placeBlock(1, 0, 5, WOOL)
				.placeBlock(1, 1, 5, REDSTONE_DUST)
				.placeBlock(1, 1, 4, WOOL)
				.placeBlock(1, 1, 3, StructureBlock.RedstoneTorch.onWall(Direction.SOUTH))
				.placeBlock(1, 2, 3, WOOL)
				// input B (east column)
				.placeBlock(4, 0, 5, WOOL)
				.placeBlock(4, 1, 5, REDSTONE_DUST)
				.placeBlock(4, 1, 4, WOOL)
				.placeBlock(4, 1, 3, StructureBlock.RedstoneTorch.onWall(Direction.SOUTH))
				.placeBlock(4, 2, 3, WOOL)
				// elevated AND line circuitry
				.placeBlock(2, 1, 3, WOOL)
				.placeBlock(3, 1, 3, WOOL)
				.placeBlock(2, 2, 3, REDSTONE_DUST)
				.placeBlock(3, 2, 3, REDSTONE_DUST)
				.placeBlock(3, 1, 2, StructureBlock.RedstoneTorch.onWall(Direction.SOUTH))
				// final combiner: NAND against each input
				.placeBlock(1, 1, 2, WOOL)
				.placeBlock(1, 2, 2, REDSTONE_DUST)
				.placeBlock(2, 0, 2, WOOL)
				.placeBlock(2, 1, 2, REDSTONE_DUST)
				.placeBlock(2, 2, 2, WOOL)
				.placeBlock(1, 1, 1, StructureBlock.RedstoneTorch.onWall(Direction.SOUTH))
				.placeBlock(4, 0, 2, WOOL)
				.placeBlock(4, 1, 2, REDSTONE_DUST)
				.placeBlock(4, 1, 1, WOOL)
				.placeBlock(4, 2, 1, REDSTONE_DUST)
				.placeBlock(3, 1, 1, WOOL)
				.placeBlock(3, 2, 1, REDSTONE_DUST)
				.placeBlock(2, 1, 1, WOOL)
				.placeBlock(2, 2, 1, REDSTONE_DUST)
				.placeBlock(2, 1, 0, StructureBlock.RedstoneTorch.onWall(Direction.SOUTH))
				.placeBlock(1, 0, 0, WOOL)
				.placeBlock(1, 1, 0, REDSTONE_DUST)
				.input(new StructurePin(new Cell(0, 0, 1), Direction.SOUTH))
				.input(new StructurePin(new Cell(1, 0, 1), Direction.SOUTH))
				.output(new StructurePin(new Cell(0, 0, 0), Direction.NORTH))
				.build();
	}

	/**
	 * Skeleton for a single-bit 2-to-1 multiplexer. A and B are data inputs,
	 * S is the select input, and Y is the selected output:
	 * {@code Y = S ? B : A}. Thus S low passes A through to Y, while S high
	 * passes B through to Y. An equivalent Boolean expression is
	 * {@code Y = (A AND NOT S) OR (B AND S)}.
	 *
	 * <p>Input pin ordering follows the cell-library contract: A is input 0,
	 * B is input 1, and S is input 2. Y is output 0. The 2x2x2-cell skeleton
	 * puts A and B on the south faces of cells (0,0,1) and (1,0,1), S on the
	 * west face of cell (0,0,0), and Y on the north face of cell (0,0,0).
	 */
	public static Structure muxGate() {
		return new Structure.Builder(new Cell(2, 2, 2))
				.horizontallyContained(true)
				.allowsAbove(true)
				.inputSignal(7).outputSignal(13).delayTicks(3)
				.input(new StructurePin(new Cell(0, 0, 1), Direction.SOUTH)) // A
				.input(new StructurePin(new Cell(1, 0, 1), Direction.SOUTH)) // B
				.input(new StructurePin(new Cell(0, 0, 0), Direction.WEST)) // S
				.output(new StructurePin(new Cell(1, 0, 0), Direction.NORTH)) // Y
				// input A
				.placeBlock(1, 0, 5, WOOL)
				.placeBlock(1, 1, 5, REDSTONE_DUST)
				.placeBlock(1, 1, 4, WOOL)
				.placeBlock(1, 1, 3, RedstoneTorch.onWall(Direction.SOUTH))
				// input B
				.placeBlock(4, 0, 5, WOOL)
				.placeBlock(4, 1, 5, REDSTONE_DUST)
				.placeBlock(4, 1, 4, WOOL)
				.placeBlock(4, 1, 3, RedstoneTorch.onWall(Direction.SOUTH))
				// multiplexor selector A
				.placeBlock(0, 0, 1, WOOL)
				.placeBlock(0, 1, 1, REDSTONE_DUST)
				.placeBlock(1, 1, 1, WOOL)
				.placeBlock(1, 2, 1, REDSTONE_DUST)
				.placeBlock(2, 0, 1, WOOL)
				.placeBlock(2, 1, 1, REDSTONE_DUST)
				.placeBlock(2, 0, 2, WOOL)
				.placeBlock(2, 1, 2, new Repeater(Direction.SOUTH, 1))
				.placeBlock(2, 0, 3, WOOL)
				.placeBlock(2, 1, 3, REDSTONE_DUST)
				.placeBlock(2, 1, 4, WOOL)
				.placeBlock(2, 2, 4, REDSTONE_DUST)
				.placeBlock(3, 1, 4, WOOL)
				.placeBlock(3, 2, 4, REDSTONE_DUST)
				.placeBlock(3, 1, 3, WOOL)
				.placeBlock(3, 2, 3, new Repeater(Direction.NORTH, 1))
				.placeBlock(3, 2, 2, WOOL)
				.placeBlock(3, 2, 1, RedstoneTorch.onWall(Direction.SOUTH))
				// multiplexor selector B
				.placeBlock(1, 2, 2, WOOL)
				.placeBlock(1, 3, 2, REDSTONE_DUST)
				.placeBlock(2, 2, 2, WOOL)
				.placeBlock(2, 3, 2, REDSTONE_DUST)
				.placeBlock(2, 3, 3, WOOL)
				.placeBlock(2, 4, 3, REDSTONE_DUST)
				.placeBlock(3, 3, 3, RedstoneTorch.onWall(Direction.WEST))
				.placeBlock(4, 2, 3, WOOL)
				.placeBlock(4, 3, 3, REDSTONE_DUST)
				.placeBlock(4, 2, 2, RedstoneTorch.onWall(Direction.SOUTH))
				// output wire
				.placeBlock(4, 1, 1, WOOL)
				.placeBlock(4, 2, 1, REDSTONE_DUST)
				.placeBlock(4, 0, 0, WOOL)
				.placeBlock(4, 1, 0, REDSTONE_DUST)
				.build();
	}
}
