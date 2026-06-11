package ax.xz.max.minesynth.structure;

import java.util.Optional;

/**
 * A block that can appear inside a {@link Structure}. Deliberately small and
 * independent of any Minecraft API; translating to game blockstates is the
 * future schematic emitter's concern. Air is the absence of an entry, not a
 * block type.
 *
 * <p>Directional semantics are minesynth's own: a {@link Repeater}'s facing is
 * the signal flow direction (input behind, output ahead), and a
 * {@link RedstoneTorch} stores where its supporting block is.
 */
public sealed interface StructureBlock permits
	StructureBlock.Wool, StructureBlock.Glass, StructureBlock.RedstoneDust,
	StructureBlock.Repeater, StructureBlock.RedstoneTorch {

	/** Uncolored wool, recolored at placement time. */
	StructureBlock WOOL = new Wool(BlockColor.UNASSIGNED);
	/** Uncolored glass, recolored at placement time. */
	StructureBlock GLASS = new Glass(BlockColor.UNASSIGNED);
	/** Redstone dust; must rest on a {@link #canSupportDust()} block. */
	StructureBlock REDSTONE_DUST = new RedstoneDust();

	/** The opaque solid building block of the ecosystem. */
	record Wool(BlockColor color) implements StructureBlock {}

	/**
	 * Transparent support. Caution: dust on glass passes redstone upward but
	 * NOT downward (a Minecraft mechanic), so glass is unsuitable for paths
	 * that descend; vias use wool. The asymmetry could serve deliberate
	 * one-way designs later.
	 */
	record Glass(BlockColor color) implements StructureBlock {}

	/** Redstone dust. */
	record RedstoneDust() implements StructureBlock {}

	/**
	 * A repeater pointing in {@code facing} (the direction the signal flows
	 * through it), with a delay of 1 to 4 redstone ticks.
	 */
	record Repeater(Direction facing, int delay) implements StructureBlock {
		public Repeater {
			if (delay < 1 || delay > 4)
				throw new IllegalArgumentException("repeater delay must be 1..4 ticks, got " + delay);
		}
	}

	/**
	 * A redstone torch. {@code wallAttachment} is the direction of the block
	 * it hangs on (so {@code onWall(NORTH)} sticks to the south face of the
	 * block to its north and points south); empty means it stands on the
	 * block below.
	 */
	record RedstoneTorch(Optional<Direction> wallAttachment) implements StructureBlock {
		public static RedstoneTorch onFloor() {
			return new RedstoneTorch(Optional.empty());
		}

		public static RedstoneTorch onWall(Direction attachedTo) {
			return new RedstoneTorch(Optional.of(attachedTo));
		}

		/** The position of the block this torch is attached to. */
		public BlockPos supportingBlock(BlockPos position) {
			return wallAttachment.map(position::offset).orElse(position.below());
		}
	}

	/** True for the signal-carrying blocks: dust, repeaters, torches. */
	default boolean isRedstoneComponent() {
		return switch (this) {
			case RedstoneDust d -> true;
			case Repeater r -> true;
			case RedstoneTorch t -> true;
			case Wool w -> false;
			case Glass g -> false;
		};
	}

	/** True if dust, repeaters, or floor torches may rest on this block. */
	default boolean canSupportDust() {
		return switch (this) {
			case Wool w -> true;
			case Glass g -> true;
			default -> false;
		};
	}

	/**
	 * This block with {@link BlockColor#UNASSIGNED} wool or glass recolored to
	 * {@code color}; explicitly colored and non-colorable blocks are returned
	 * unchanged. Recoloring to UNASSIGNED is a no-op.
	 */
	default StructureBlock withColor(BlockColor color) {
		if (color == BlockColor.UNASSIGNED)
			return this;
		return switch (this) {
			case Wool(BlockColor c) when c == BlockColor.UNASSIGNED -> new Wool(color);
			case Glass(BlockColor c) when c == BlockColor.UNASSIGNED -> new Glass(color);
			default -> this;
		};
	}

	/** This block as it appears when its structure is rotated to {@code orientation}. */
	default StructureBlock rotatedTo(Direction orientation) {
		return switch (this) {
			case Repeater(Direction facing, int delay) -> new Repeater(facing.rotatedTo(orientation), delay);
			case RedstoneTorch(Optional<Direction> wall) ->
				new RedstoneTorch(wall.map(d -> d.rotatedTo(orientation)));
			default -> this;
		};
	}
}
