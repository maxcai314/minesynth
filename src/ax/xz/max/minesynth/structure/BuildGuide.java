package ax.xz.max.minesynth.structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Renders a structure as a layer-by-layer ASCII build tutorial.
 *
 * <p>This is a testing and debugging aid for following a design in game by
 * hand; it is not part of the core modeling API and nothing else depends on
 * it. Layers print bottom-up; within a layer, rows run north to south and
 * columns west to east.
 *
 * <p>Legend: {@code .} air, {@code W} wool, {@code G} glass, {@code d} dust,
 * {@code ^ > v <} repeater by facing, {@code t} floor torch,
 * {@code n e s w} wall torch by the side its supporting block is on.
 */
public final class BuildGuide {
	private BuildGuide() {}

	/**
	 * A compass showing how the cardinal directions map onto the grids this
	 * class renders: north is the top row (-z), east is the right column (+x),
	 * and layers print bottom-up (y increases toward the sky).
	 */
	public static String compassDiagram() {
		return """
			           N (-z)
			           |
			(-x) W ----+---- E (+x)
			           |
			           S (+z)

			grid orientation: north = top row, west = left column,
			layers print bottom (y=0) first
			""";
	}

	/** The full tutorial text for a structure. */
	public static String render(Structure structure) {
		StringBuilder out = new StringBuilder();
		BlockPos extent = structure.blockSize();
		out.append("structure: ").append(structure.size().x()).append("x")
			.append(structure.size().y()).append("x").append(structure.size().z())
			.append(" cells (").append(extent.x()).append("x").append(extent.y())
			.append("x").append(extent.z()).append(" blocks), ")
			.append(structure.contained() ? "contained" : "NOT contained").append('\n');

		for (int y = 0; y < extent.y(); y++) {
			out.append('\n').append("-- layer y=").append(y);
			if (y == 0)
				out.append(" (bottom)");
			if (y == extent.y() - 1)
				out.append(" (top)");
			out.append(" --\n");
			renderLayer(out, structure, y, extent);
		}

		renderPins(out, "INPUT", structure.inputs());
		renderPins(out, "OUTPUT", structure.outputs());
		renderColors(out, structure);
		out.append('\n').append("legend: . air, W wool, G glass, d dust, ^>v< repeater (facing),")
			.append(" t floor torch, n/e/s/w wall torch (side of its support block)").append('\n');
		return out.toString();
	}

	/**
	 * Tutorial for a placement instruction: the structure as it would be
	 * stamped (rotated and recolored), with a header giving the offset to add
	 * to every coordinate.
	 */
	public static String render(PlacedStructure placed) {
		Structure resolved = placed.structure()
			.rotatedTo(placed.orientation())
			.recolored(placed.color());
		return "placed at cell " + placed.position() + " = block " + placed.position().blockOrigin()
			+ " (add to all coordinates below), facing " + placed.orientation()
			+ ", color " + placed.color() + "\n"
			+ render(resolved);
	}

	private static void renderLayer(StringBuilder out, Structure structure, int y, BlockPos extent) {
		out.append("        x:");
		for (int x = 0; x < extent.x(); x++)
			out.append(' ').append(x % 10);
		out.append('\n');

		for (int z = 0; z < extent.z(); z++) {
			String label = z == 0 ? "z=" + z + " (N)" : "z=" + z;
			out.append(String.format("%-9s ", label));
			for (int x = 0; x < extent.x(); x++) {
				out.append(glyph(structure.blockAt(new BlockPos(x, y, z))));
				if (x < extent.x() - 1)
					out.append(' ');
			}
			out.append('\n');
		}

		// run-length row descriptions, skipping empty rows
		for (int z = 0; z < extent.z(); z++) {
			String runs = describeRow(structure, y, z, extent.x());
			if (!runs.isEmpty())
				out.append("  z=").append(z).append(": ").append(runs).append('\n');
		}
	}

	private static String describeRow(Structure structure, int y, int z, int width) {
		List<String> parts = new ArrayList<>();
		int x = 0;
		while (x < width) {
			Optional<StructureBlock> block = structure.blockAt(new BlockPos(x, y, z));
			if (block.isEmpty()) {
				x++;
				continue;
			}
			int start = x;
			while (x + 1 < width && structure.blockAt(new BlockPos(x + 1, y, z)).equals(block))
				x++;
			String range = start == x ? "x" + start : "x" + start + "-" + x;
			parts.add(name(block.get()) + " at " + range);
			x++;
		}
		return String.join(", ", parts);
	}

	private static void renderPins(StringBuilder out, String kind, List<StructurePin> pins) {
		if (pins.isEmpty())
			return;
		out.append('\n');
		for (int i = 0; i < pins.size(); i++) {
			StructurePin pin = pins.get(i);
			BlockPos block = pin.connectionBlock();
			out.append(kind).append('[').append(i).append("]: ").append(pin.cell())
				.append(' ').append(pin.face())
				.append(", connects through block (").append(block.x()).append(", ")
				.append(block.y()).append(", ").append(block.z()).append(")\n");
		}
	}

	private static void renderColors(StringBuilder out, Structure structure) {
		Map<BlockColor, Integer> counts = new LinkedHashMap<>();
		structure.blocks().values().forEach(block -> {
			switch (block) {
				case StructureBlock.Wool(BlockColor color) -> counts.merge(color, 1, Integer::sum);
				case StructureBlock.Glass(BlockColor color) -> counts.merge(color, 1, Integer::sum);
				default -> {}
			}
		});
		if (counts.isEmpty())
			return;
		out.append('\n').append("colors:");
		counts.forEach((color, count) -> out.append(' ').append(color).append("=").append(count));
		out.append('\n');
	}

	private static char glyph(Optional<StructureBlock> block) {
		if (block.isEmpty())
			return '.';
		return switch (block.get()) {
			case StructureBlock.Wool w -> 'W';
			case StructureBlock.Glass g -> 'G';
			case StructureBlock.RedstoneDust d -> 'd';
			case StructureBlock.Repeater(Direction facing, int delay) -> switch (facing) {
				case NORTH -> '^';
				case EAST -> '>';
				case SOUTH -> 'v';
				case WEST -> '<';
			};
			case StructureBlock.RedstoneTorch torch -> torch.wallAttachment()
				.map(direction -> switch (direction) {
					case NORTH -> 'n';
					case EAST -> 'e';
					case SOUTH -> 's';
					case WEST -> 'w';
				})
				.orElse('t');
		};
	}

	private static String name(StructureBlock block) {
		return switch (block) {
			case StructureBlock.Wool(BlockColor color) -> color == BlockColor.UNASSIGNED
				? "wool" : color.toString().toLowerCase(java.util.Locale.ROOT) + " wool";
			case StructureBlock.Glass(BlockColor color) -> color == BlockColor.UNASSIGNED
				? "glass" : color.toString().toLowerCase(java.util.Locale.ROOT) + " glass";
			case StructureBlock.RedstoneDust d -> "dust";
			case StructureBlock.Repeater(Direction facing, int delay) ->
				"repeater(" + facing + ", " + delay + "t)";
			case StructureBlock.RedstoneTorch torch -> torch.wallAttachment()
				.map(direction -> "torch(on wall " + direction + ")")
				.orElse("torch(on floor)");
		};
	}
}
