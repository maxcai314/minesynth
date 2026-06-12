package ax.xz.max.minesynth.schematic;

import ax.xz.max.minesynth.structure.BlockColor;
import ax.xz.max.minesynth.structure.BlockPos;
import ax.xz.max.minesynth.structure.Direction;
import ax.xz.max.minesynth.structure.Structure;
import ax.xz.max.minesynth.structure.StructureBlock;
import de.pauleff.jnbt.api.ICompoundTag;
import de.pauleff.jnbt.api.NBTFactory;
import de.pauleff.jnbt.api.NBTFileFactory;
import de.pauleff.jnbt.formats.binary.Compression_Types;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves a {@link Structure} as an MCEdit {@code .schematic} file that
 * WorldEdit can load and paste. Block types map onto legacy numeric IDs
 * exactly as WorldEdit's own legacy table translates them back; UNASSIGNED
 * wool exports as white wool and UNASSIGNED glass as plain glass.
 */
public final class SchematicWriter {
	private SchematicWriter() {}

	/**
	 * Writes the structure to {@code file}, creating parent directories as
	 * needed. The file is a gzipped NBT compound named {@code Schematic}
	 * carrying Width/Height/Length, Materials, Blocks and Data.
	 */
	public static void write(Structure structure, Path file) throws SchematicException {
		BlockPos size = structure.blockSize();
		if (size.x() > Short.MAX_VALUE || size.y() > Short.MAX_VALUE || size.z() > Short.MAX_VALUE)
			throw new SchematicException("structure is too large for the schematic format: "
				+ size.x() + "x" + size.y() + "x" + size.z() + " blocks");

		byte[] blocks = new byte[size.x() * size.y() * size.z()];
		byte[] data = new byte[blocks.length];
		structure.blocks().forEach((position, block) -> {
			int index = (position.y() * size.z() + position.z()) * size.x() + position.x();
			blocks[index] = blockId(block);
			data[index] = blockData(block);
		});

		ICompoundTag root = NBTFactory.createCompound("Schematic");
		root.addShort("Width", (short) size.x())
			.addShort("Height", (short) size.y())
			.addShort("Length", (short) size.z())
			.addString("Materials", "Alpha")
			.addByteArray("Blocks", blocks)
			.addByteArray("Data", data);

		try {
			if (file.getParent() != null)
				Files.createDirectories(file.getParent());
			NBTFileFactory.writeNBTFile(file.toFile(), root, Compression_Types.GZIP);
		} catch (IOException e) {
			throw new SchematicException("cannot write schematic to " + file, e);
		}
	}

	private static byte blockId(StructureBlock block) {
		return (byte) switch (block) {
			case StructureBlock.Wool w -> 35;
			case StructureBlock.Glass(BlockColor color) -> color == BlockColor.UNASSIGNED ? 20 : 95;
			case StructureBlock.RedstoneDust d -> 55;
			case StructureBlock.Repeater r -> 93;  // unpowered repeater
			case StructureBlock.RedstoneTorch t -> 76; // lit redstone torch
		};
	}

	private static byte blockData(StructureBlock block) {
		return (byte) switch (block) {
			case StructureBlock.Wool(BlockColor color) -> colorData(color);
			case StructureBlock.Glass(BlockColor color) ->
				color == BlockColor.UNASSIGNED ? 0 : colorData(color);
			case StructureBlock.RedstoneDust d -> 0;
			case StructureBlock.Repeater(Direction facing, int delay) ->
				repeaterDirection(facing) | ((delay - 1) << 2);
			case StructureBlock.RedstoneTorch torch -> torch.wallAttachment()
				.map(SchematicWriter::torchDirection)
				.orElse(5); // standing on the floor
		};
	}

	/** Legacy dye palette order matches the BlockColor declaration order. */
	private static int colorData(BlockColor color) {
		return color == BlockColor.UNASSIGNED ? 0 : color.ordinal();
	}

	/** Legacy repeater direction bits name the output side (our facing). */
	private static int repeaterDirection(Direction facing) {
		return switch (facing) {
			case NORTH -> 0;
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
		};
	}

	/** Legacy torch data names the direction the torch points, away from its support. */
	private static int torchDirection(Direction attachedTo) {
		return switch (attachedTo) {
			case WEST -> 1;  // attached west, pointing east
			case EAST -> 2;
			case NORTH -> 3; // attached north, pointing south
			case SOUTH -> 4;
		};
	}
}
