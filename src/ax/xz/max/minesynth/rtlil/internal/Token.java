package ax.xz.max.minesynth.rtlil.internal;

/**
 * One lexed RTLIL token. {@code text} holds the identifier (with prefix), the
 * bare word, the digits of an integer, the full {@code width'digits} value
 * token, or the unescaped string contents.
 */
public record Token(Kind kind, String text, int line) {
	public enum Kind {
		/** A bare keyword-ish word like {@code module} or {@code width}. */
		WORD,
		/** An identifier starting with {@code \} or {@code $}. */
		ID,
		/** A decimal integer, possibly negative. */
		INT,
		/** A sized constant like {@code 4'01xz}. */
		VALUE,
		/** A quoted string (text is already unescaped). */
		STRING,
		LBRACE, RBRACE, LBRACKET, RBRACKET, COLON, COMMA,
		EOL,
		EOF
	}

	@Override
	public String toString() {
		return switch (kind) {
			case EOL -> "end of line";
			case EOF -> "end of file";
			case STRING -> "string \"" + text + "\"";
			default -> "'" + text + "'";
		};
	}
}
