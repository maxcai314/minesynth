package ax.xz.max.minesynth.pnr;

import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.StructurePin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The user-specified shape of the finished board: its total cell dimensions
 * and the exact boundary locations of every named input and output port.
 * Mirrors the floorplanning step of chip design.
 */
public record Floorplan(Cell size, Map<String, StructurePin> inputPorts, Map<String, StructurePin> outputPorts) {
	public Floorplan {
		inputPorts = Collections.unmodifiableMap(new LinkedHashMap<>(inputPorts));
		outputPorts = Collections.unmodifiableMap(new LinkedHashMap<>(outputPorts));
		if (size.x() < 1 || size.y() < 1 || size.z() < 1)
			throw new IllegalArgumentException("floorplan size must be strictly positive, got " + size);
		var pinCells = new java.util.HashSet<Cell>();
		for (var entry : inputPorts.entrySet())
			validatePort(entry.getKey(), entry.getValue(), size, pinCells, outputPorts);
		for (var entry : outputPorts.entrySet())
			validatePort(entry.getKey(), entry.getValue(), size, pinCells, inputPorts);
	}

	private static void validatePort(String name, StructurePin pin, Cell size,
			java.util.Set<Cell> pinCells, Map<String, StructurePin> otherSide) {
		Cell c = pin.cell();
		if (c.x() < 0 || c.x() >= size.x() || c.y() < 0 || c.y() >= size.y() || c.z() < 0 || c.z() >= size.z())
			throw new IllegalArgumentException("port " + name + " at " + pin + " is outside the floorplan " + size);
		boolean onBoundary = switch (pin.face()) {
			case NORTH -> c.z() == 0;
			case SOUTH -> c.z() == size.z() - 1;
			case EAST -> c.x() == size.x() - 1;
			case WEST -> c.x() == 0;
		};
		if (!onBoundary)
			throw new IllegalArgumentException("port " + name + " at " + pin
				+ " does not lie on the floorplan boundary in its face direction");
		if (otherSide.containsKey(name))
			throw new IllegalArgumentException("port name " + name + " is used for both an input and an output");
		if (!pinCells.add(c))
			throw new IllegalArgumentException("port " + name + " shares cell " + c + " with another port");
	}

	/** Fluent builder; ports keep declaration order. */
	public static final class Builder {
		private final Cell size;
		private final Map<String, StructurePin> inputs = new LinkedHashMap<>();
		private final Map<String, StructurePin> outputs = new LinkedHashMap<>();

		public Builder(Cell size) {
			this.size = size;
		}

		public Builder inputPort(String name, StructurePin pin) {
			if (inputs.putIfAbsent(name, pin) != null)
				throw new IllegalArgumentException("duplicate input port " + name);
			return this;
		}

		public Builder outputPort(String name, StructurePin pin) {
			if (outputs.putIfAbsent(name, pin) != null)
				throw new IllegalArgumentException("duplicate output port " + name);
			return this;
		}

		public Floorplan build() {
			return new Floorplan(size, inputs, outputs);
		}
	}
}
