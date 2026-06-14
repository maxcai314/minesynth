package ax.xz.max.minesynth.pnr;

import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.PlacedStructure;
import ax.xz.max.minesynth.structure.StructurePin;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The output of placement: every component of the design bound to a
 * {@link PlacedStructure} (position, orientation, color). Validates the
 * spatial ground rules so routers can trust their input; the final
 * structure build re-validates everything as defense in depth.
 */
public record Placement(PnrDesign design, Map<String, PlacedStructure> placements) {
	public Placement {
		placements = Collections.unmodifiableMap(new LinkedHashMap<>(placements));
		validate(design, placements);
	}

	private static void validate(PnrDesign design, Map<String, PlacedStructure> placements) {
		if (!placements.keySet().equals(design.components().keySet()))
			throw new IllegalArgumentException("placement does not cover exactly the design's components: "
				+ placements.keySet() + " vs " + design.components().keySet());

		Cell size = design.floorplan().size();
		Map<Cell, String> claims = new HashMap<>();
		for (var entry : placements.entrySet()) {
			String name = entry.getKey();
			PlacedStructure placed = entry.getValue();
			if (!placed.structure().equals(design.components().get(name)))
				throw new IllegalArgumentException("placement of " + name + " uses a different structure");
			Cell at = placed.position();
			Cell rotated = placed.rotatedSize();
			if (at.x() < 0 || at.y() < 0 || at.z() < 0
					|| at.x() + rotated.x() > size.x()
					|| at.y() + rotated.y() > size.y()
					|| at.z() + rotated.z() > size.z())
				throw new IllegalArgumentException("component " + name + " at " + at
					+ " does not fit the floorplan " + size);
			for (Cell cell : placed.occupiedCells()) {
				String other = claims.putIfAbsent(cell, name);
				if (other != null)
					throw new IllegalArgumentException("components " + other + " and " + name
						+ " both occupy cell " + cell);
			}
		}

		// every face-adjacency between two components must satisfy both rules
		for (var entry : placements.entrySet()) {
			String name = entry.getKey();
			ax.xz.max.minesynth.structure.PlacementRule rule = entry.getValue().structure().placement();
			for (Cell cell : entry.getValue().occupiedCells()) {
				for (ax.xz.max.minesynth.structure.Adjacency side
						: ax.xz.max.minesynth.structure.Adjacency.values()) {
					Cell neighbor = side.neighbor(cell);
					String other = claims.get(neighbor);
					if (other == null || other.equals(name))
						continue;
					var otherRule = placements.get(other).structure().placement();
					if (!rule.canNeighbor(otherRule, side))
						throw new IllegalArgumentException("components " + name + " and " + other
							+ " sit in incompatible adjacent cells " + cell + " / " + neighbor
							+ " (" + rule + " vs " + otherRule + ")");
				}
			}
		}
	}

	/** The board-frame pin a net source drives from (output pin or board input port). */
	public StructurePin sourceLocation(NetEnd end) {
		return switch (end) {
			case NetEnd.Port(String name) -> design.floorplan().inputPorts().get(name);
			case NetEnd.Pin(String component, int index) -> placements.get(component).outputPins().get(index);
		};
	}

	/** The board-frame pin a net sink receives at (input pin or board output port). */
	public StructurePin sinkLocation(NetEnd end) {
		return switch (end) {
			case NetEnd.Port(String name) -> design.floorplan().outputPorts().get(name);
			case NetEnd.Pin(String component, int index) -> placements.get(component).inputPins().get(index);
		};
	}

	/** The signal stats governing a net end (the component's, or null for board ports). */
	public ax.xz.max.minesynth.structure.SignalStats statsOf(NetEnd end) {
		return switch (end) {
			case NetEnd.Port p -> null;
			case NetEnd.Pin(String component, int index) -> design.components().get(component).signal();
		};
	}
}
