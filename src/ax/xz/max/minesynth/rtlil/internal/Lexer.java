package ax.xz.max.minesynth.rtlil.internal;

import ax.xz.max.minesynth.rtlil.RtlilParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizer for the RTLIL text format. The format is line-oriented: every
 * statement ends at the newline, so EOL is a real token here. Identifiers are
 * greedy ({@code \}/{@code $} followed by any run of non-whitespace), which is
 * what lets auto-generated names contain dots, colons and brackets; slice
 * brackets only count when they stand alone after whitespace.
 */
public final class Lexer {
	private final String text;
	private final String source;
	private final List<Token> tokens = new ArrayList<>();

	private Lexer(String text, String source) {
		this.text = text;
		this.source = source;
	}

	public static List<Token> tokenize(String text, String source) throws RtlilParseException {
		Lexer lexer = new Lexer(text, source);
		lexer.run();
		return lexer.tokens;
	}

	private void run() throws RtlilParseException {
		int lineNo = 0;
		for (String rawLine : text.split("\n", -1)) {
			lineNo++;
			String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
			lexLine(line, lineNo);
			tokens.add(new Token(Token.Kind.EOL, "", lineNo));
		}
		tokens.add(new Token(Token.Kind.EOF, "", lineNo));
	}

	private void lexLine(String line, int lineNo) throws RtlilParseException {
		int i = 0;
		int n = line.length();
		while (i < n) {
			char c = line.charAt(i);
			if (c == ' ' || c == '\t') {
				i++;
			} else if (c == '#') {
				return; // comment to end of line
			} else if (c == '"') {
				i = lexString(line, i, lineNo);
			} else if (c == '\\' || c == '$') {
				int start = i;
				while (i < n && line.charAt(i) != ' ' && line.charAt(i) != '\t')
					i++;
				if (i - start < 2)
					throw error(lineNo, "empty identifier");
				add(Token.Kind.ID, line.substring(start, i), lineNo);
			} else if (c == '-' || isDigit(c)) {
				i = lexNumber(line, i, lineNo);
			} else if (isWordChar(c)) {
				int start = i;
				while (i < n && isWordChar(line.charAt(i)))
					i++;
				add(Token.Kind.WORD, line.substring(start, i), lineNo);
			} else {
				Token.Kind kind = switch (c) {
					case '{' -> Token.Kind.LBRACE;
					case '}' -> Token.Kind.RBRACE;
					case '[' -> Token.Kind.LBRACKET;
					case ']' -> Token.Kind.RBRACKET;
					case ':' -> Token.Kind.COLON;
					case ',' -> Token.Kind.COMMA;
					default -> throw error(lineNo, "unexpected character '" + c + "'");
				};
				add(kind, String.valueOf(c), lineNo);
				i++;
			}
		}
	}

	private int lexNumber(String line, int i, int lineNo) throws RtlilParseException {
		int n = line.length();
		int start = i;
		if (line.charAt(i) == '-')
			i++;
		if (i >= n || !isDigit(line.charAt(i)))
			throw error(lineNo, "'-' must start a number");
		while (i < n && isDigit(line.charAt(i)))
			i++;
		if (i < n && line.charAt(i) == '\'') {
			if (line.charAt(start) == '-')
				throw error(lineNo, "negative width on sized constant");
			i++;
			while (i < n && isBitChar(line.charAt(i)))
				i++;
			add(Token.Kind.VALUE, line.substring(start, i), lineNo);
		} else {
			add(Token.Kind.INT, line.substring(start, i), lineNo);
		}
		return i;
	}

	/** Unescapes the same way the Yosys lexer does: \n, \t, octal, else literal. */
	private int lexString(String line, int i, int lineNo) throws RtlilParseException {
		int n = line.length();
		StringBuilder sb = new StringBuilder();
		i++; // opening quote
		while (true) {
			if (i >= n)
				throw error(lineNo, "unterminated string");
			char c = line.charAt(i);
			if (c == '"') {
				i++;
				break;
			}
			if (c == '\\' && i + 1 < n) {
				char e = line.charAt(++i);
				if (e == 'n') {
					sb.append('\n');
					i++;
				} else if (e == 't') {
					sb.append('\t');
					i++;
				} else if (e >= '0' && e <= '7') {
					int value = 0;
					while (i < n && line.charAt(i) >= '0' && line.charAt(i) <= '7') {
						value = value * 8 + (line.charAt(i) - '0');
						i++;
					}
					sb.append((char) value);
				} else {
					sb.append(e); // covers \\ and \" and anything else
					i++;
				}
			} else {
				sb.append(c);
				i++;
			}
		}
		add(Token.Kind.STRING, sb.toString(), lineNo);
		return i;
	}

	private void add(Token.Kind kind, String text, int line) {
		tokens.add(new Token(kind, text, line));
	}

	private RtlilParseException error(int line, String message) {
		return new RtlilParseException(source, line, message);
	}

	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private static boolean isBitChar(char c) {
		return c == '0' || c == '1' || c == 'x' || c == 'z' || c == 'm' || c == '-';
	}

	private static boolean isWordChar(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
	}
}
