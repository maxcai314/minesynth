package ax.xz.max.minesynth.structure;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates single-cell wire and junction structures: dust paths over wool
 * connecting cell faces through the center. All pieces here are contained and
 * use only the port blocks and the cell center, so they can sit next to
 * anything.
 */
public final class Wires {
	private Wires() {}

	/**
	 * A 1x1x1-cell wire carrying a signal from the {@code in} face to the
	 * {@code out} face, straight or L-shaped. Equivalent to a one-output
	 * {@link #simpleJunction}.
	 */
	public static Structure wire(Direction in, Direction out) {
		return simpleJunction(in, out);
	}

	/**
	 * A single-cell fork: dust enters at {@code in} and leaves through every
	 * listed output face (1 to 3 of them). Dust is undirected; the
	 * input/output split is bookkeeping for routing.
	 */
	public static Structure simpleJunction(Direction in, Direction... outputs) {
		Set<Direction> outs = validateOutputs(in, outputs);

		BlockPos center = new BlockPos(1, 1, 1);
		Structure.Builder builder = new Structure.Builder(new Cell(1, 1, 1))
			.placeBlock(center.below(), StructureBlock.WOOL)
			.placeBlock(center, StructureBlock.REDSTONE_DUST)
			.placeBlock(center.offset(in).below(), StructureBlock.WOOL)
			.placeBlock(center.offset(in), StructureBlock.REDSTONE_DUST)
			.input(new StructurePin(new Cell(0, 0, 0), in))
			.inputSignal(3).outputSignal(-3).delayTicks(0);
		for (Direction out : outs) {
			builder.placeBlock(center.offset(out).below(), StructureBlock.WOOL)
				.placeBlock(center.offset(out), StructureBlock.REDSTONE_DUST)
				.output(new StructurePin(new Cell(0, 0, 0), out));
		}
		return builder.build();
	}

	/**
	 * A 1x1x1-cell wire with a repeater at its entrance, refreshing the signal
	 * to full strength (and adding one tick of delay). The repeater sits on
	 * the input port block facing into the cell, reading the neighbor's port
	 * dust directly behind it; dust then carries the signal through the center
	 * to the output face, so bends work just like {@link #wire}. Unlike a
	 * plain wire, this one is directional. Equivalent to a one-output
	 * {@link #repeaterSimpleJunction}.
	 */
	public static Structure repeaterWire(Direction in, Direction out) {
		return repeaterSimpleJunction(in, out);
	}

	/**
	 * A {@link #simpleJunction} with the entrance repeater of
	 * {@link #repeaterWire}: refreshes the signal, then forks it through every
	 * listed output face. Directional.
	 */
	public static Structure repeaterSimpleJunction(Direction in, Direction... outputs) {
		Set<Direction> outs = validateOutputs(in, outputs);

		BlockPos center = new BlockPos(1, 1, 1);
		Structure.Builder builder = new Structure.Builder(new Cell(1, 1, 1))
			.placeBlock(center.below(), StructureBlock.WOOL)
			.placeBlock(center, StructureBlock.REDSTONE_DUST)
			.placeBlock(center.offset(in).below(), StructureBlock.WOOL)
			.placeBlock(center.offset(in), new StructureBlock.Repeater(in.opposite(), 1))
			.input(new StructurePin(new Cell(0, 0, 0), in))
			.inputSignal(1).outputSignal(12).delayTicks(1);
		for (Direction out : outs) {
			builder.placeBlock(center.offset(out).below(), StructureBlock.WOOL)
				.placeBlock(center.offset(out), StructureBlock.REDSTONE_DUST)
				.output(new StructurePin(new Cell(0, 0, 0), out));
		}
		return builder.build();
	}

	private static Set<Direction> validateOutputs(Direction in, Direction... outputs) {
		if (outputs.length < 1 || outputs.length > 3)
			throw new IllegalArgumentException("a junction needs 1 to 3 outputs, got " + outputs.length);
		Set<Direction> outs = new LinkedHashSet<>();
		for (Direction out : outputs) {
			if (out == in)
				throw new IllegalArgumentException("junction needs two different faces, got " + in + " twice");
			if (!outs.add(out))
				throw new IllegalArgumentException("duplicate junction output " + out);
		}
		return outs;
	}
}
