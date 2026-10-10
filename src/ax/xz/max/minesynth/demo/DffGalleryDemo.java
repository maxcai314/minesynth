package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.schematic.SchematicWriter;
import ax.xz.max.minesynth.structure.BlockColor;
import ax.xz.max.minesynth.structure.BuildGuide;
import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.Direction;
import ax.xz.max.minesynth.structure.Gates;
import ax.xz.max.minesynth.structure.Structure;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Places one-, four-, and sixteen-bit DFF banks on a single board for visual
 * inspection in game. The specimens are not connected to each other.
 */
public final class DffGalleryDemo {
	private static final int MARGIN = 1;
	private static final int GAP = 2;

	public static void main(String[] args) throws Exception {
		Map<String, Structure> specimens = new LinkedHashMap<>();
		specimens.put("DFF(1)", Gates.dff(1));
		specimens.put("DFF(4)", Gates.dff(4));
		specimens.put("DFF(16)", Gates.dff(16));

		int contentWidth = specimens.values().stream().mapToInt(s -> s.size().x()).sum()
			+ GAP * (specimens.size() - 1);
		int height = specimens.values().stream().mapToInt(s -> s.size().y()).max().orElseThrow();
		int depth = specimens.values().stream().mapToInt(s -> s.size().z()).max().orElseThrow();
		Structure.Builder builder = new Structure.Builder(new Cell(
			contentWidth + 2 * MARGIN, height, depth + 2 * MARGIN))
			.horizontallyContained(false);

		BlockColor[] colors = {BlockColor.LIME, BlockColor.CYAN, BlockColor.ORANGE};
		StringBuilder key = new StringBuilder();
		int x = MARGIN;
		int color = 0;
		for (var entry : specimens.entrySet()) {
			Structure structure = entry.getValue();
			Cell position = new Cell(x, 0, MARGIN);
			BlockColor blockColor = colors[color++];
			builder.place(structure, position, Direction.NORTH, blockColor);
			key.append(String.format("  %-8s cell %s  size %s  %s%n",
				entry.getKey(), position, structure.size(), blockColor));
			x += structure.size().x() + GAP;
		}

		Structure gallery = builder.build();
		System.out.println("DFF gallery (nothing is connected; inspect each specimen)");
		System.out.println();
		System.out.println(BuildGuide.compassDiagram());
		System.out.println(BuildGuide.render(gallery));
		System.out.println("specimens:");
		System.out.print(key);

		Path schematicFile = Path.of("out", "dff-gallery.schematic");
		SchematicWriter.write(gallery, schematicFile);
		System.out.println();
		System.out.println("wrote " + schematicFile
			+ " (worldedit: //schem load dff-gallery, then //paste)");
	}
}
