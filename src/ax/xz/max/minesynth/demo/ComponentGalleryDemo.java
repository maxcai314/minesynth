package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.schematic.SchematicWriter;
import ax.xz.max.minesynth.structure.BlockColor;
import ax.xz.max.minesynth.structure.BuildGuide;
import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.Direction;
import ax.xz.max.minesynth.structure.Gates;
import ax.xz.max.minesynth.structure.Structure;
import ax.xz.max.minesynth.structure.Vias;
import ax.xz.max.minesynth.structure.ViaTap;
import ax.xz.max.minesynth.structure.Wires;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ax.xz.max.minesynth.structure.Direction.EAST;
import static ax.xz.max.minesynth.structure.Direction.NORTH;
import static ax.xz.max.minesynth.structure.Direction.SOUTH;
import static ax.xz.max.minesynth.structure.Direction.WEST;

/**
 * Places one specimen of every component type on a single board for visual
 * inspection in game: wires, repeater wires (straight and bent), junctions,
 * gates, vias both ways, and a two-tap junction via. Prints the tutorial and
 * writes the schematic. Nothing is wired to anything; this is a showroom.
 */
public final class ComponentGalleryDemo {
	public static void main(String[] args) throws Exception {
		Map<String, Structure> row1 = new LinkedHashMap<>();
		row1.put("wire straight", Wires.wire(NORTH, SOUTH));
		row1.put("wire L", Wires.wire(NORTH, EAST));
		row1.put("repeater wire", Wires.repeaterWire(SOUTH, NORTH));
		row1.put("repeater wire bent", Wires.repeaterWire(NORTH, EAST));
		row1.put("junction 2-way", Wires.simpleJunction(NORTH, EAST, SOUTH));
		row1.put("junction 3-way", Wires.simpleJunction(NORTH, EAST, SOUTH, WEST));
		row1.put("repeater junction", Wires.repeaterSimpleJunction(SOUTH, NORTH, EAST));

		Map<String, Structure> row2 = new LinkedHashMap<>();
		row2.put("NOT gate", Gates.notGate());
		row2.put("AND gate", Gates.andGate());
		row2.put("OR gate", Gates.orGate());
		row2.put("via up h2", Vias.upward(2, SOUTH, NORTH));
		row2.put("via down h3", Vias.downward(3, SOUTH, NORTH));
		row2.put("junction via 2 taps", Vias.upwardJunction(NORTH,
			List.of(new ViaTap(1, EAST), new ViaTap(3, WEST))));

		Structure.Builder builder = new Structure.Builder(new Cell(22, 4, 6)).contained(false);
		BlockColor[] palette = {BlockColor.LIME, BlockColor.CYAN, BlockColor.ORANGE, BlockColor.MAGENTA,
			BlockColor.YELLOW, BlockColor.LIGHT_BLUE, BlockColor.PINK};
		StringBuilder key = new StringBuilder();

		int color = 0;
		int x = 1;
		for (var entry : row1.entrySet()) {
			builder.place(entry.getValue(), new Cell(x, 0, 1), Direction.NORTH, palette[color % palette.length]);
			key.append(String.format("  %-20s cell (%d, 0, 1)  %s%n",
				entry.getKey(), x, palette[color % palette.length]));
			x += entry.getValue().size().x() + 2;
			color++;
		}
		x = 1;
		for (var entry : row2.entrySet()) {
			builder.place(entry.getValue(), new Cell(x, 0, 4), Direction.NORTH, palette[color % palette.length]);
			key.append(String.format("  %-20s cell (%d, 0, 4)  %s%n",
				entry.getKey(), x, palette[color % palette.length]));
			x += entry.getValue().size().x() + 2;
			color++;
		}
		Structure gallery = builder.build();

		System.out.println("component gallery (nothing is connected; inspect each specimen)");
		System.out.println();
		System.out.println(BuildGuide.compassDiagram());
		System.out.println(BuildGuide.render(gallery));
		System.out.println("specimens:");
		System.out.print(key);

		Path schematicFile = Path.of("out", "component-gallery.schematic");
		SchematicWriter.write(gallery, schematicFile);
		System.out.println();
		System.out.println("wrote " + schematicFile + " (worldedit: //schem load component-gallery, then //paste)");
	}
}
