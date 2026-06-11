package ax.xz.max.minesynth.structure;

import java.util.ArrayList;
import java.util.List;

/**
 * A placement instruction: a structure, where it goes (in parent cell
 * coordinates), which way its local north points, and what color its
 * UNASSIGNED wool and glass should take ({@link BlockColor#UNASSIGNED} defers
 * coloring to a higher level).
 *
 * <p>This is deliberately not convertible to a {@link Structure}: with a
 * nonzero offset its pins would no longer lie on a structure boundary and the
 * padding cells would be meaningless. The only way to realize it is
 * {@link Structure.Builder#place(PlacedStructure)}, which checks that it fits.
 * Use PlacedStructure whenever handling anything that is not a base component:
 * assembling boards, shifting candidates around during placement optimization
 * (the {@code addOffset} withers return adjusted copies), or asking where
 * pins land in the parent frame.
 */
public record PlacedStructure(Structure structure, Cell position, Direction orientation, BlockColor color) {

	/** The structure's size once rotated to this orientation. */
	public Cell rotatedSize() {
		return Structure.rotateSize(structure.size(), orientation);
	}

	/** Input pins in parent coordinates (rotated, then offset by the position). */
	public List<StructurePin> inputPins() {
		return translatedPins(structure.rotatedTo(orientation).inputs());
	}

	/** Output pins in parent coordinates (rotated, then offset by the position). */
	public List<StructurePin> outputPins() {
		return translatedPins(structure.rotatedTo(orientation).outputs());
	}

	/** Every parent cell this placement would claim (its full bounding box). */
	public List<Cell> occupiedCells() {
		Cell size = rotatedSize();
		List<Cell> cells = new ArrayList<>();
		for (int x = 0; x < size.x(); x++)
			for (int y = 0; y < size.y(); y++)
				for (int z = 0; z < size.z(); z++)
					cells.add(position.plus(new Cell(x, y, z)));
		return cells;
	}

	/** This placement shifted by {@code delta} cells. */
	public PlacedStructure addOffset(Cell delta) {
		return new PlacedStructure(structure, position.plus(delta), orientation, color);
	}

	/** This placement shifted {@code cells} steps in {@code direction}. */
	public PlacedStructure addOffset(Direction direction, int cells) {
		return new PlacedStructure(structure, position.plus(direction, cells), orientation, color);
	}

	/** This placement with a different color. */
	public PlacedStructure withColor(BlockColor newColor) {
		return new PlacedStructure(structure, position, orientation, newColor);
	}

	/** This placement with a different orientation (replaces; does not compose). */
	public PlacedStructure withOrientation(Direction newOrientation) {
		return new PlacedStructure(structure, position, newOrientation, color);
	}

	private List<StructurePin> translatedPins(List<StructurePin> pins) {
		return pins.stream()
			.map(pin -> new StructurePin(pin.cell().plus(position), pin.face()))
			.toList();
	}

	@Override
	public String toString() {
		return "PlacedStructure[at=" + position + ", facing=" + orientation + ", color=" + color + "]";
	}
}
