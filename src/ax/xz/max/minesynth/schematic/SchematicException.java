package ax.xz.max.minesynth.schematic;

/**
 * Thrown when a structure cannot be exported as a schematic file, typically
 * wrapping the underlying I/O failure.
 */
public class SchematicException extends Exception {
	public SchematicException(String message) {
		super(message);
	}

	public SchematicException(String message, Throwable cause) {
		super(message, cause);
	}
}
