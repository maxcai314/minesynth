package ax.xz.max.minesynth.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An immutable redstone build: a cell-aligned volume of blocks with declared
 * input and output pins. Structures are origin-anchored and designed facing
 * NORTH; placing them elsewhere (offset, rotated, colored) is expressed with
 * {@link PlacedStructure} and realized by {@link Builder#place}.
 *
 * <p>The canonical constructor validates every package convention (bounds,
 * pin boundary rule, dust support, torch attachment, containment), so a
 * Structure that exists is a valid design. Build one fluently with
 * {@link Builder}.
 *
 * <p>{@code placement} (see {@link PlacementRule}) captures how the structure
 * may sit against its neighbors. The constructor checks that the blocks match
 * the claimed rule: redstone may only reach a shell the rule says is open.
 */
public record Structure(
	Cell size,
	Map<BlockPos, StructureBlock> blocks,
	List<StructurePin> inputs,
	List<StructurePin> outputs,
	PlacementRule placement,
	SignalStats signal
) {
	public Structure {
		blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
		inputs = List.copyOf(inputs);
		outputs = List.copyOf(outputs);
		validate(size, blocks, inputs, outputs, placement);
	}

	/** Whether this structure keeps its redstone clear of its side shells; see {@link PlacementRule}. */
	public boolean contained() {
		return placement.horizontallyContained();
	}

	/** Required strength as if a dust were placed on this cell's input pin block; see {@link SignalStats}. */
	public int inputSignal() {
		return signal.inputSignal();
	}

	/**
	 * Strength as if a dust were placed on the output pin's adjacent cell's
	 * port block; negative = relative to the input, positive = absolute. See
	 * {@link SignalStats}.
	 */
	public int outputSignal() {
		return signal.outputSignal();
	}

	/** Redstone ticks until outputs settle after an input change; see {@link SignalStats}. */
	public int delayTicks() {
		return signal.delayTicks();
	}

	/** The block at {@code position}, empty meaning air. */
	public Optional<StructureBlock> blockAt(BlockPos position) {
		return Optional.ofNullable(blocks.get(position));
	}

	/** The extent in blocks: size times {@link Cell#BLOCKS} per axis. */
	public BlockPos blockSize() {
		return size.blockOrigin();
	}

	/**
	 * This structure rotated so its local north points at {@code orientation}
	 * (about the +y-axis; NORTH returns this structure). Blocks, pins, and
	 * directional block states all rotate together; the result is re-validated
	 * by construction.
	 */
	public Structure rotatedTo(Direction orientation) {
		if (orientation == Direction.NORTH)
			return this;

		Cell rotatedSize = rotateSize(size, orientation);
		Map<BlockPos, StructureBlock> rotatedBlocks = new LinkedHashMap<>();
		blocks.forEach((position, block) ->
			rotatedBlocks.put(rotateBlock(position, orientation, blockSize()), block.rotatedTo(orientation)));

		List<StructurePin> rotatedInputs = rotatePins(inputs, orientation);
		List<StructurePin> rotatedOutputs = rotatePins(outputs, orientation);
		return new Structure(rotatedSize, rotatedBlocks, rotatedInputs, rotatedOutputs, placement, signal);
	}

	/** This structure with all UNASSIGNED wool and glass recolored to {@code color}. */
	public Structure recolored(BlockColor color) {
		if (color == BlockColor.UNASSIGNED)
			return this;
		Map<BlockPos, StructureBlock> recolored = new LinkedHashMap<>();
		blocks.forEach((position, block) -> recolored.put(position, block.withColor(color)));
		return new Structure(size, recolored, inputs, outputs, placement, signal);
	}

	private List<StructurePin> rotatePins(List<StructurePin> pins, Direction orientation) {
		return pins.stream()
			.map(pin -> new StructurePin(
				rotateCell(pin.cell(), orientation, size),
				pin.face().rotatedTo(orientation)))
			.toList();
	}

	/** Rotates an extent: quarter turns swap width and length. */
	static Cell rotateSize(Cell size, Direction orientation) {
		return switch (orientation) {
			case NORTH, SOUTH -> size;
			case EAST, WEST -> new Cell(size.z(), size.y(), size.x());
		};
	}

	private static BlockPos rotateBlock(BlockPos p, Direction orientation, BlockPos extent) {
		return switch (orientation) {
			case NORTH -> p;
			case EAST -> new BlockPos(extent.z() - 1 - p.z(), p.y(), p.x());
			case SOUTH -> new BlockPos(extent.x() - 1 - p.x(), p.y(), extent.z() - 1 - p.z());
			case WEST -> new BlockPos(p.z(), p.y(), extent.x() - 1 - p.x());
		};
	}

	static Cell rotateCell(Cell c, Direction orientation, Cell extent) {
		return switch (orientation) {
			case NORTH -> c;
			case EAST -> new Cell(extent.z() - 1 - c.z(), c.y(), c.x());
			case SOUTH -> new Cell(extent.x() - 1 - c.x(), c.y(), extent.z() - 1 - c.z());
			case WEST -> new Cell(c.z(), c.y(), extent.x() - 1 - c.x());
		};
	}

	// ---- validation ----

	private static void validate(Cell size, Map<BlockPos, StructureBlock> blocks,
			List<StructurePin> inputs, List<StructurePin> outputs, PlacementRule placement) {
		if (size.x() < 1 || size.y() < 1 || size.z() < 1)
			throw new IllegalArgumentException("structure size must be strictly positive, got " + size);
		BlockPos extent = size.blockOrigin();

		Set<StructurePin> seenPins = new HashSet<>();
		List<StructurePin> allPins = new ArrayList<>(inputs);
		allPins.addAll(outputs);
		Set<BlockPos> portBlocks = new HashSet<>();
		for (StructurePin pin : allPins) {
			if (!seenPins.add(pin))
				throw new IllegalArgumentException("duplicate pin " + pin);
			validatePin(pin, size);
			portBlocks.add(pin.connectionBlock());
		}

		blocks.forEach((position, block) -> {
			if (!inBounds(position, extent))
				throw new IllegalArgumentException("block at " + position + " is outside the "
					+ extent.x() + "x" + extent.y() + "x" + extent.z() + " block volume");
			validateSupport(position, block, blocks);
			if (!block.isRedstoneComponent() || portBlocks.contains(position))
				return;
			// redstone may only reach a shell the placement rule says is open
			if (placement.horizontallyContained() && onSideShell(position, extent))
				throw new IllegalArgumentException("structure is horizontally contained, but " + describe(block)
					+ " at " + position + " touches a side shell away from any port;"
					+ " move it inward or call horizontallyContained(false)");
			if (placement.allowsAbove() && position.y() == extent.y() - 1)
				throw new IllegalArgumentException("structure allows neighbors above, but " + describe(block)
					+ " at " + position + " touches its top shell; call allowsAbove(false)");
			if (placement.allowsBelow() && position.y() == 0)
				throw new IllegalArgumentException("structure allows neighbors below, but " + describe(block)
					+ " at " + position + " touches its bottom shell; call allowsBelow(false)");
		});
	}

	private static void validatePin(StructurePin pin, Cell size) {
		Cell c = pin.cell();
		if (c.x() < 0 || c.x() >= size.x() || c.y() < 0 || c.y() >= size.y() || c.z() < 0 || c.z() >= size.z())
			throw new IllegalArgumentException(pin + " is outside the structure (size " + size + ")");
		boolean onBoundary = switch (pin.face()) {
			case NORTH -> c.z() == 0;
			case SOUTH -> c.z() == size.z() - 1;
			case EAST -> c.x() == size.x() - 1;
			case WEST -> c.x() == 0;
		};
		if (!onBoundary)
			throw new IllegalArgumentException(pin + " does not lie on the structure boundary"
				+ " in its face direction (size " + size + ")");
	}

	private static void validateSupport(BlockPos position, StructureBlock block,
			Map<BlockPos, StructureBlock> blocks) {
		switch (block) {
			case StructureBlock.RedstoneDust d -> requireSupportBelow(position, blocks, "redstone dust");
			case StructureBlock.Repeater r -> requireSupportBelow(position, blocks, "repeater");
			case StructureBlock.RedstoneTorch torch -> {
				BlockPos support = torch.supportingBlock(position);
				StructureBlock supporting = blocks.get(support);
				if (torch.wallAttachment().isPresent()) {
					if (!(supporting instanceof StructureBlock.Wool))
						throw new IllegalArgumentException("wall torch at " + position
							+ " needs wool at " + support + " to hang on");
				} else if (supporting == null || !supporting.canSupportDust()) {
					throw new IllegalArgumentException("floor torch at " + position
						+ " needs a supporting block at " + support + " inside the structure");
				}
			}
			default -> {}
		}
	}

	private static void requireSupportBelow(BlockPos position, Map<BlockPos, StructureBlock> blocks, String what) {
		StructureBlock below = blocks.get(position.below());
		if (below == null || !below.canSupportDust())
			throw new IllegalArgumentException(what + " at " + position + " has no supporting block below it;"
				+ " structures may not assume anything exists outside themselves");
	}

	private static boolean inBounds(BlockPos p, BlockPos extent) {
		return p.x() >= 0 && p.x() < extent.x()
			&& p.y() >= 0 && p.y() < extent.y()
			&& p.z() >= 0 && p.z() < extent.z();
	}

	private static boolean onSideShell(BlockPos p, BlockPos extent) {
		return p.x() == 0 || p.x() == extent.x() - 1
			|| p.z() == 0 || p.z() == extent.z() - 1;
	}

	private static String describe(StructureBlock block) {
		return switch (block) {
			case StructureBlock.RedstoneDust d -> "redstone dust";
			case StructureBlock.Repeater r -> "a repeater";
			case StructureBlock.RedstoneTorch t -> "a redstone torch";
			case StructureBlock.Wool w -> "wool";
			case StructureBlock.Glass g -> "glass";
		};
	}

	@Override
	public String toString() {
		return "Structure[size=" + size + ", " + blocks.size() + " blocks, "
			+ inputs.size() + " in, " + outputs.size() + " out, " + placement + "]";
	}

	// ---- builder ----

	/**
	 * Fluent builder for structures. Coordinates are in blocks for
	 * {@link #placeBlock} and in cells for {@link #place}; all methods throw
	 * immediately on out-of-bounds or overlapping placement so design errors
	 * surface at the call site.
	 */
	public static final class Builder {
		private record Claim(int placement, PlacementRule rule) {}

		private final Cell size;
		private final BlockPos extent;
		private final Map<BlockPos, StructureBlock> blocks = new LinkedHashMap<>();
		private final List<StructurePin> inputs = new ArrayList<>();
		private final List<StructurePin> outputs = new ArrayList<>();
		private final Map<Cell, Claim> cellClaims = new HashMap<>();
		private PlacementRule placement = PlacementRule.CONTAINED;
		private SignalStats signal = SignalStats.WIRE_LIKE;
		private int placements = 0;

		public Builder(Cell sizeInCells) {
			if (sizeInCells.x() < 1 || sizeInCells.y() < 1 || sizeInCells.z() < 1)
				throw new IllegalArgumentException("builder size must be strictly positive, got " + sizeInCells);
			this.size = sizeInCells;
			this.extent = sizeInCells.blockOrigin();
		}

		/** Places one block; throws if out of bounds or the position is taken. */
		public Builder placeBlock(BlockPos position, StructureBlock block) {
			if (position.x() < 0 || position.x() >= extent.x()
					|| position.y() < 0 || position.y() >= extent.y()
					|| position.z() < 0 || position.z() >= extent.z())
				throw new IllegalArgumentException("block at " + position + " is outside the builder volume "
					+ extent.x() + "x" + extent.y() + "x" + extent.z());
			if (blocks.putIfAbsent(position, block) != null)
				throw new IllegalArgumentException("position " + position + " already holds "
					+ blocks.get(position));
			return this;
		}

		public Builder placeBlock(int x, int y, int z, StructureBlock block) {
			return placeBlock(new BlockPos(x, y, z), block);
		}

		/** Fills the inclusive box from {@code from} to {@code to} with {@code block}. */
		public Builder fill(BlockPos from, BlockPos to, StructureBlock block) {
			for (int x = Math.min(from.x(), to.x()); x <= Math.max(from.x(), to.x()); x++)
				for (int y = Math.min(from.y(), to.y()); y <= Math.max(from.y(), to.y()); y++)
					for (int z = Math.min(from.z(), to.z()); z <= Math.max(from.z(), to.z()); z++)
						placeBlock(new BlockPos(x, y, z), block);
			return this;
		}

		public Builder fill(int x1, int y1, int z1, int x2, int y2, int z2, StructureBlock block) {
			return fill(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), block);
		}

		public Builder input(StructurePin pin) {
			inputs.add(pin);
			return this;
		}

		public Builder output(StructurePin pin) {
			outputs.add(pin);
			return this;
		}

		/**
		 * Declares several inputs at once, in order. Pairs with
		 * {@link PlacedStructure#inputPins()} to re-export a placed
		 * component's ports as this structure's own.
		 */
		public Builder addInputs(List<StructurePin> pins) {
			inputs.addAll(pins);
			return this;
		}

		/** Declares several outputs at once, in order; see {@link #addInputs}. */
		public Builder addOutputs(List<StructurePin> pins) {
			outputs.addAll(pins);
			return this;
		}

		/** Sets the full placement rule; see {@link PlacementRule}. */
		public Builder placement(PlacementRule placement) {
			this.placement = placement;
			return this;
		}

		/** Whether redstone stays clear of the side shells (safe to sit beside). */
		public Builder horizontallyContained(boolean value) {
			this.placement = placement.withHorizontallyContained(value);
			return this;
		}

		/** Whether a structure may sit in the cell directly above. */
		public Builder allowsAbove(boolean value) {
			this.placement = placement.withAllowsAbove(value);
			return this;
		}

		/** Whether a structure may sit in the cell directly below. */
		public Builder allowsBelow(boolean value) {
			this.placement = placement.withAllowsBelow(value);
			return this;
		}

		/** Required arriving strength at the input pin block; see {@link SignalStats}. */
		public Builder inputSignal(int inputSignal) {
			this.signal = new SignalStats(inputSignal, signal.outputSignal(), signal.delayTicks());
			return this;
		}

		/** Strength delivered to the adjacent cell's port block; see {@link SignalStats}. */
		public Builder outputSignal(int outputSignal) {
			this.signal = new SignalStats(signal.inputSignal(), outputSignal, signal.delayTicks());
			return this;
		}

		/** Redstone ticks until outputs settle; see {@link SignalStats}. */
		public Builder delayTicks(int delayTicks) {
			this.signal = new SignalStats(signal.inputSignal(), signal.outputSignal(), delayTicks);
			return this;
		}

		/** Places a sub-structure; see {@link #place(PlacedStructure)}. */
		public Builder place(Structure structure, Cell position, Direction orientation, BlockColor color) {
			return place(new PlacedStructure(structure, position, orientation, color));
		}

		/**
		 * Stamps a placement instruction into this builder: the sub-structure
		 * is rotated to its orientation, recolored (unless the placement color
		 * is UNASSIGNED), and copied in at the cell offset. The placement
		 * claims its full cell volume: placements may not share cells, and each
		 * face-adjacency to an existing placement must satisfy both structures'
		 * {@link PlacementRule}s (so, for example, nothing may be placed
		 * directly above a gate whose torch pokes through its ceiling).
		 */
		public Builder place(PlacedStructure placed) {
			Structure resolved = placed.structure()
				.rotatedTo(placed.orientation())
				.recolored(placed.color());
			Cell at = placed.position();
			Cell resolvedSize = resolved.size();

			if (at.x() < 0 || at.y() < 0 || at.z() < 0
					|| at.x() + resolvedSize.x() > size.x()
					|| at.y() + resolvedSize.y() > size.y()
					|| at.z() + resolvedSize.z() > size.z())
				throw new IllegalArgumentException("placement at " + at + " of size " + resolvedSize
					+ " does not fit in the builder (size " + size + ")");

			int placementId = placements++;
			PlacementRule rule = resolved.placement();
			List<Cell> claimed = new ArrayList<>();
			for (int x = 0; x < resolvedSize.x(); x++)
				for (int y = 0; y < resolvedSize.y(); y++)
					for (int z = 0; z < resolvedSize.z(); z++)
						claimed.add(at.plus(new Cell(x, y, z)));

			for (Cell cell : claimed) {
				Claim existing = cellClaims.get(cell);
				if (existing != null)
					throw new IllegalArgumentException("cell " + cell + " is already occupied by placement #"
						+ existing.placement());
				for (Adjacency side : Adjacency.values()) {
					Cell neighbor = side.neighbor(cell);
					Claim other = cellClaims.get(neighbor);
					if (other != null && other.placement() != placementId
							&& !rule.canNeighbor(other.rule(), side))
						throw new IllegalArgumentException("placement at " + cell
							+ " is incompatible with placement #" + other.placement()
							+ " at " + neighbor + " (" + rule + " vs " + other.rule() + ")");
				}
			}
			// pre-check block collisions so a failed place() leaves the builder untouched
			BlockPos offset = at.blockOrigin();
			for (BlockPos position : resolved.blocks().keySet()) {
				BlockPos target = position.plus(offset);
				if (blocks.containsKey(target))
					throw new IllegalArgumentException("placement at " + at + " collides with existing "
						+ blocks.get(target) + " at " + target);
			}

			for (Cell cell : claimed)
				cellClaims.put(cell, new Claim(placementId, rule));
			resolved.blocks().forEach((position, block) -> placeBlock(position.plus(offset), block));
			return this;
		}

		/** Builds the immutable structure; all package conventions are validated here. */
		public Structure build() {
			return new Structure(size, blocks, inputs, outputs, placement, signal);
		}
	}
}
