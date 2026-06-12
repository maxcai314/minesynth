package ax.xz.max.minesynth.pnr;

import ax.xz.max.minesynth.structure.BlockColor;
import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.Direction;
import ax.xz.max.minesynth.structure.PlacedStructure;
import ax.xz.max.minesynth.structure.Structure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Baseline placer: components in declaration order, laid out in west-to-east
 * rows at ground level with no search. Every component keeps {@link #SPACING}
 * empty cells to its neighbors and the layout keeps a {@link #MARGIN}-cell
 * ring to the floorplan boundary, leaving generous room for the router's via
 * columns and satisfying the non-contained adjacency rule by construction.
 * Components are colored round-robin for in-game readability.
 */
public final class NaivePlacer implements Placer {
	/**
	 * Default empty cells kept between any two components. Three, not two: a
	 * channel between two non-contained components must be wide enough for its
	 * center row to host via columns, which may not neighbor non-contained
	 * cells. Congested designs benefit from even wider channels.
	 */
	public static final int DEFAULT_SPACING = 3;
	/** Empty-cell ring kept inside the floorplan boundary. */
	public static final int MARGIN = 2;

	private final int spacing;

	public NaivePlacer() {
		this(DEFAULT_SPACING);
	}

	/** A placer with custom component spacing (at least {@link #DEFAULT_SPACING} recommended). */
	public NaivePlacer(int spacing) {
		this.spacing = spacing;
	}

	private static final List<BlockColor> PALETTE = List.of(
		BlockColor.LIME, BlockColor.CYAN, BlockColor.ORANGE, BlockColor.MAGENTA,
		BlockColor.YELLOW, BlockColor.LIGHT_BLUE, BlockColor.PINK, BlockColor.PURPLE);

	@Override
	public Placement place(PnrDesign design) throws PlacementException {
		Cell size = design.floorplan().size();
		Map<String, PlacedStructure> placements = new LinkedHashMap<>();

		int cursorX = MARGIN;
		int rowZ = MARGIN;
		int rowLength = 0;
		int colorIndex = 0;

		for (var entry : design.components().entrySet()) {
			String name = entry.getKey();
			Structure structure = entry.getValue();
			Cell footprint = structure.size();

			if (cursorX + footprint.x() > size.x() - MARGIN && cursorX > MARGIN) {
				// wrap to the next row
				cursorX = MARGIN;
				rowZ += rowLength + spacing;
				rowLength = 0;
			}
			if (cursorX + footprint.x() > size.x() - MARGIN
					|| rowZ + footprint.z() > size.z() - MARGIN
					|| footprint.y() > size.y())
				throw new PlacementException("component " + name + " (" + footprint
					+ ") does not fit the floorplan " + size + " at row z=" + rowZ);

			placements.put(name, new PlacedStructure(structure,
				new Cell(cursorX, 0, rowZ), Direction.NORTH, PALETTE.get(colorIndex % PALETTE.size())));
			cursorX += footprint.x() + spacing;
			rowLength = Math.max(rowLength, footprint.z());
			colorIndex++;
		}

		return new Placement(design, placements);
	}
}
