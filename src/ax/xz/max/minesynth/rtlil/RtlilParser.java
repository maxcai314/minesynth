package ax.xz.max.minesynth.rtlil;

import ax.xz.max.minesynth.rtlil.internal.Lexer;
import ax.xz.max.minesynth.rtlil.internal.ParserEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parses the Yosys RTLIL text format (as written by {@code write_rtlil}) into
 * an {@link RtlilDesign}. Stateless; both methods are pure functions.
 */
public final class RtlilParser {
	private RtlilParser() {}

	/**
	 * Parses an {@code .rtlil} file.
	 *
	 * @throws RtlilParseException if the file cannot be read or does not parse;
	 *         the message carries the file name and line number
	 */
	public static RtlilDesign parseFile(Path path) throws RtlilParseException {
		String text;
		try {
			text = Files.readString(path);
		} catch (IOException e) {
			throw new RtlilParseException(path.toString(), "cannot read file: " + e.getMessage(), e);
		}
		return parse(text, path.toString());
	}

	/** Parses RTLIL text; {@code sourceName} is only used in error messages. */
	public static RtlilDesign parse(String text, String sourceName) throws RtlilParseException {
		return new ParserEngine(Lexer.tokenize(text, sourceName), sourceName).parseDesign();
	}
}
