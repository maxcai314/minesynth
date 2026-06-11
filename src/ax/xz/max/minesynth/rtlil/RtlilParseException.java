package ax.xz.max.minesynth.rtlil;

/**
 * Thrown when an RTLIL file cannot be read or does not parse. Carries the
 * source name and line number where the problem was found ({@code line} is 0
 * for I/O failures, which appear as the cause).
 */
public class RtlilParseException extends Exception {
	private final String source;
	private final int line;

	public RtlilParseException(String source, int line, String message) {
		super(source + (line > 0 ? ":" + line : "") + ": " + message);
		this.source = source;
		this.line = line;
	}

	public RtlilParseException(String source, String message, Throwable cause) {
		super(source + ": " + message, cause);
		this.source = source;
		this.line = 0;
	}

	public String source() {
		return source;
	}

	/** 1-based line number of the error, or 0 if not line-specific. */
	public int line() {
		return line;
	}
}
