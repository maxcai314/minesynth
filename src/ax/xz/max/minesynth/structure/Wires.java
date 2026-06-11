package ax.xz.max.minesynth.structure;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates single-cell wire structures: a dust path over wool from one cell
 * face to another, straight or L-shaped. Wires are contained and use only the
 * port blocks and the cell center, so they can sit next to anything.
 */
public final class Wires {
	private Wires() {}

	/**
	 * A 1x1x1-cell wire carrying a signal from the {@code in} face to the
	 * {@code out} face. The two faces must differ; dust is undirected, the
	 * input/output distinction is bookkeeping for routing.
	 */
	public static Structure wire(Direction in, Direction out) {
		if (in == out)
			throw new IllegalArgumentException("wire needs two different faces, got " + in + " twice");

		BlockPos center = new BlockPos(1, 1, 1);
		Set<BlockPos> path = new LinkedHashSet<>();
		path.add(center.offset(in));
		path.add(center);
		path.add(center.offset(out));

		Structure.Builder builder = new Structure.Builder(new Cell(1, 1, 1));
		for (BlockPos dust : path) {
			builder.placeBlock(dust.below(), StructureBlock.WOOL);
			builder.placeBlock(dust, StructureBlock.REDSTONE_DUST);
		}
		return builder
			.input(new StructurePin(new Cell(0, 0, 0), in))
			.output(new StructurePin(new Cell(0, 0, 0), out))
			.build();
	}

	/**
	 * A 1x1x1-cell wire with a repeater at its entrance, refreshing the signal
	 * to full strength (and adding one tick of delay). The repeater sits on
	 * the input port block facing into the cell, reading the neighbor's port
	 * dust directly behind it; dust then carries the signal through the center
	 * to the output face, so bends work just like {@link #wire}. Unlike a
	 * plain wire, this one is directional.
	 */
	public static Structure repeaterWire(Direction in, Direction out) {
		if (in == out)
			throw new IllegalArgumentException("repeater wire needs two different faces, got " + in + " twice");

		BlockPos center = new BlockPos(1, 1, 1);
		return new Structure.Builder(new Cell(1, 1, 1))
			.placeBlock(center.offset(in).below(), StructureBlock.WOOL)
			.placeBlock(center.offset(in), new StructureBlock.Repeater(in.opposite(), 1))
			.placeBlock(center.below(), StructureBlock.WOOL)
			.placeBlock(center, StructureBlock.REDSTONE_DUST)
			.placeBlock(center.offset(out).below(), StructureBlock.WOOL)
			.placeBlock(center.offset(out), StructureBlock.REDSTONE_DUST)
			.input(new StructurePin(new Cell(0, 0, 0), in))
			.output(new StructurePin(new Cell(0, 0, 0), out))
			.build();
	}
}
