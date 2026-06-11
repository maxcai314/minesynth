package ax.xz.max.minesynth.rtlil;

import java.util.Optional;

/**
 * A wire declaration. {@code offset} and {@code upto} only affect how slice
 * indexes are written in the text format (source-level numbering); the rest of
 * the model always uses physical bit indexes 0..width-1.
 */
public record Wire(
	String name,
	int width,
	int offset,
	boolean upto,
	boolean signed,
	Optional<Port> port,
	Attributes attributes
) {
	/** Port direction and 1-based port index, present if this wire is a module port. */
	public record Port(Direction direction, int index) {}

	public enum Direction { INPUT, OUTPUT, INOUT }

	public boolean isPort() {
		return port.isPresent();
	}

	public boolean isInput() {
		return port.map(p -> p.direction() != Direction.OUTPUT).orElse(false);
	}

	public boolean isOutput() {
		return port.map(p -> p.direction() != Direction.INPUT).orElse(false);
	}

	@Override
	public String toString() {
		return "Wire[" + name + ", width=" + width + "]";
	}
}
