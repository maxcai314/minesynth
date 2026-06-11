package ax.xz.max.minesynth.rtlil.internal;

import ax.xz.max.minesynth.rtlil.Attributes;
import ax.xz.max.minesynth.rtlil.BitVector;
import ax.xz.max.minesynth.rtlil.CaseRule;
import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.rtlil.CellParameter;
import ax.xz.max.minesynth.rtlil.Connection;
import ax.xz.max.minesynth.rtlil.Constant;
import ax.xz.max.minesynth.rtlil.Memory;
import ax.xz.max.minesynth.rtlil.ModuleParameter;
import ax.xz.max.minesynth.rtlil.RtlilDesign;
import ax.xz.max.minesynth.rtlil.RtlilModule;
import ax.xz.max.minesynth.rtlil.RtlilParseException;
import ax.xz.max.minesynth.rtlil.RtlilProcess;
import ax.xz.max.minesynth.rtlil.SigChunk;
import ax.xz.max.minesynth.rtlil.SigSpec;
import ax.xz.max.minesynth.rtlil.SwitchRule;
import ax.xz.max.minesynth.rtlil.SyncRule;
import ax.xz.max.minesynth.rtlil.Wire;
import ax.xz.max.minesynth.rtlil.MemWrite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recursive-descent parser over the token stream. Statement structure follows
 * the Yosys grammar (frontends/rtlil); in particular, attribute statements
 * buffer up and attach to the next declared object, slice indexes are
 * normalized from source-level to physical bit offsets using the declared
 * wire's offset/upto flags, and concatenations are reversed into LSB-first
 * chunk order.
 */
public final class ParserEngine {
	private static final Set<String> CASE_BODY_END = Set.of("case", "end");
	private static final Set<String> ROOT_CASE_END = Set.of("sync", "end");

	private final List<Token> tokens;
	private final String source;
	private int pos;

	private final LinkedHashMap<String, Constant> pendingAttributes = new LinkedHashMap<>();
	private Map<String, Wire> moduleWires;

	public ParserEngine(List<Token> tokens, String source) {
		this.tokens = tokens;
		this.source = source;
	}

	public RtlilDesign parseDesign() throws RtlilParseException {
		long autoidx = 0;
		List<RtlilModule> modules = new ArrayList<>();

		skipEols();
		while (peek().kind() != Token.Kind.EOF) {
			if (atWord("autoidx")) {
				next();
				autoidx = parseLongToken();
				expectEol();
			} else if (atWord("attribute")) {
				parseAttributeLine();
			} else if (atWord("module")) {
				modules.add(parseModule());
			} else {
				throw error("expected module, attribute, or autoidx, got " + peek());
			}
			skipEols();
		}
		requireNoPendingAttributes("end of file");
		return new RtlilDesign(autoidx, modules);
	}

	// ---- module level ----

	private RtlilModule parseModule() throws RtlilParseException {
		expectWord("module");
		String name = expect(Token.Kind.ID).text();
		expectEol();
		Attributes attributes = takeAttributes();

		moduleWires = new HashMap<>();
		List<ModuleParameter> parameters = new ArrayList<>();
		List<Wire> wires = new ArrayList<>();
		List<Memory> memories = new ArrayList<>();
		List<Cell> cells = new ArrayList<>();
		List<RtlilProcess> processes = new ArrayList<>();
		List<Connection> connections = new ArrayList<>();

		skipEols();
		while (!atWord("end")) {
			if (atWord("attribute")) {
				parseAttributeLine();
			} else if (atWord("parameter")) {
				next();
				String paramName = expect(Token.Kind.ID).text();
				Optional<Constant> defaultValue = atEol() ? Optional.empty() : Optional.of(parseConstant());
				expectEol();
				parameters.add(new ModuleParameter(paramName, defaultValue));
			} else if (atWord("wire")) {
				wires.add(parseWire());
			} else if (atWord("memory")) {
				memories.add(parseMemory());
			} else if (atWord("cell")) {
				cells.add(parseCell());
			} else if (atWord("process")) {
				processes.add(parseProcess());
			} else if (atWord("connect")) {
				next();
				SigSpec left = parseSigSpec();
				SigSpec right = parseSigSpec();
				expectEol();
				requireEqualWidths(left, right, "connect");
				connections.add(new Connection(left, right));
			} else {
				throw error("unexpected " + peek() + " in module body");
			}
			skipEols();
		}
		next(); // end
		expectEol();
		requireNoPendingAttributes("end of module " + name);
		moduleWires = null;

		return new RtlilModule(name, attributes, parameters, wires, memories, cells, processes, connections);
	}

	private Wire parseWire() throws RtlilParseException {
		expectWord("wire");
		int width = 1;
		int offset = 0;
		boolean upto = false;
		boolean signed = false;
		Wire.Direction direction = null;
		int portIndex = 0;

		while (peek().kind() == Token.Kind.WORD) {
			String option = next().text();
			switch (option) {
				case "width" -> width = parseIntToken();
				case "offset" -> offset = parseIntToken();
				case "upto" -> upto = true;
				case "signed" -> signed = true;
				case "input" -> {
					direction = Wire.Direction.INPUT;
					portIndex = parseIntToken();
				}
				case "output" -> {
					direction = Wire.Direction.OUTPUT;
					portIndex = parseIntToken();
				}
				case "inout" -> {
					direction = Wire.Direction.INOUT;
					portIndex = parseIntToken();
				}
				default -> throw error("unknown wire option '" + option + "'");
			}
		}
		String name = expect(Token.Kind.ID).text();
		expectEol();
		if (width < 0)
			throw error("negative wire width on " + name);

		Optional<Wire.Port> port = direction == null
			? Optional.empty()
			: Optional.of(new Wire.Port(direction, portIndex));
		Wire wire = new Wire(name, width, offset, upto, signed, port, takeAttributes());
		if (moduleWires.putIfAbsent(name, wire) != null)
			throw error("duplicate wire " + name);
		return wire;
	}

	private Memory parseMemory() throws RtlilParseException {
		expectWord("memory");
		int width = 1;
		int size = 0;
		int offset = 0;
		while (peek().kind() == Token.Kind.WORD) {
			String option = next().text();
			switch (option) {
				case "width" -> width = parseIntToken();
				case "size" -> size = parseIntToken();
				case "offset" -> offset = parseIntToken();
				default -> throw error("unknown memory option '" + option + "'");
			}
		}
		String name = expect(Token.Kind.ID).text();
		expectEol();
		return new Memory(name, width, size, offset, takeAttributes());
	}

	private Cell parseCell() throws RtlilParseException {
		expectWord("cell");
		String type = expect(Token.Kind.ID).text();
		String name = expect(Token.Kind.ID).text();
		expectEol();
		Attributes attributes = takeAttributes();

		Map<String, CellParameter> parameters = new LinkedHashMap<>();
		Map<String, SigSpec> connections = new LinkedHashMap<>();

		skipEols();
		while (!atWord("end")) {
			if (atWord("parameter")) {
				next();
				CellParameter.Flavor flavor = CellParameter.Flavor.NONE;
				if (atWord("signed")) {
					next();
					flavor = CellParameter.Flavor.SIGNED;
				} else if (atWord("real")) {
					next();
					flavor = CellParameter.Flavor.REAL;
				}
				String paramName = expect(Token.Kind.ID).text();
				Constant value = parseConstant();
				expectEol();
				if (parameters.put(paramName, new CellParameter(value, flavor)) != null)
					throw error("duplicate parameter " + paramName + " on cell " + name);
			} else if (atWord("connect")) {
				next();
				String portName = expect(Token.Kind.ID).text();
				SigSpec signal = parseSigSpec();
				expectEol();
				if (connections.put(portName, signal) != null)
					throw error("duplicate connection to port " + portName + " on cell " + name);
			} else {
				throw error("unexpected " + peek() + " in cell body");
			}
			skipEols();
		}
		next(); // end
		expectEol();
		return new Cell(type, name, attributes, parameters, connections);
	}

	// ---- processes ----

	private RtlilProcess parseProcess() throws RtlilParseException {
		expectWord("process");
		String name = expect(Token.Kind.ID).text();
		expectEol();
		Attributes attributes = takeAttributes();

		CaseRule rootCase = parseCaseBody(Attributes.EMPTY, List.of(), ROOT_CASE_END);
		List<SyncRule> syncs = new ArrayList<>();
		while (atWord("sync"))
			syncs.add(parseSync());

		expectWord("end");
		expectEol();
		requireNoPendingAttributes("end of process " + name);
		return new RtlilProcess(name, attributes, rootCase, syncs);
	}

	/**
	 * Parses assign/switch statements until one of {@code terminators}. The
	 * terminating token is left in the stream. Pending attributes may survive
	 * this method; they belong to the next case of the enclosing switch.
	 */
	private CaseRule parseCaseBody(Attributes attributes, List<SigSpec> compare, Set<String> terminators)
			throws RtlilParseException {
		List<Connection> assigns = new ArrayList<>();
		List<SwitchRule> switches = new ArrayList<>();

		skipEols();
		while (true) {
			Token token = peek();
			if (token.kind() == Token.Kind.WORD && terminators.contains(token.text()))
				break;
			if (atWord("attribute")) {
				parseAttributeLine();
			} else if (atWord("assign")) {
				if (!switches.isEmpty())
					throw error("assign statement after switch statement in the same case body");
				next();
				SigSpec left = parseSigSpec();
				SigSpec right = parseSigSpec();
				expectEol();
				requireEqualWidths(left, right, "assign");
				assigns.add(new Connection(left, right));
			} else if (atWord("switch")) {
				switches.add(parseSwitch());
			} else {
				throw error("unexpected " + token + " in case body");
			}
			skipEols();
		}
		return new CaseRule(attributes, compare, assigns, switches);
	}

	private SwitchRule parseSwitch() throws RtlilParseException {
		Attributes attributes = takeAttributes();
		expectWord("switch");
		SigSpec signal = parseSigSpec();
		expectEol();

		List<CaseRule> cases = new ArrayList<>();
		skipEols();
		while (true) {
			if (atWord("attribute")) {
				parseAttributeLine();
				skipEols();
				continue;
			}
			if (!atWord("case"))
				break;
			Attributes caseAttributes = takeAttributes();
			next(); // case
			List<SigSpec> compare = new ArrayList<>();
			if (!atEol()) {
				compare.add(parseSigSpec());
				while (peek().kind() == Token.Kind.COMMA) {
					next();
					compare.add(parseSigSpec());
				}
			}
			expectEol();
			cases.add(parseCaseBody(caseAttributes, compare, CASE_BODY_END));
		}
		expectWord("end");
		expectEol();
		requireNoPendingAttributes("end of switch");
		return new SwitchRule(attributes, signal, cases);
	}

	private SyncRule parseSync() throws RtlilParseException {
		expectWord("sync");
		Token typeToken = expect(Token.Kind.WORD);
		SyncRule.Type type = switch (typeToken.text()) {
			case "always" -> SyncRule.Type.ALWAYS;
			case "global" -> SyncRule.Type.GLOBAL;
			case "init" -> SyncRule.Type.INIT;
			case "low" -> SyncRule.Type.LOW;
			case "high" -> SyncRule.Type.HIGH;
			case "posedge" -> SyncRule.Type.POSEDGE;
			case "negedge" -> SyncRule.Type.NEGEDGE;
			case "edge" -> SyncRule.Type.EDGE;
			default -> throw error("unknown sync type '" + typeToken.text() + "'");
		};
		Optional<SigSpec> signal = type.hasSignal() ? Optional.of(parseSigSpec()) : Optional.empty();
		expectEol();

		List<Connection> updates = new ArrayList<>();
		List<MemWrite> memWrites = new ArrayList<>();
		skipEols();
		while (true) {
			if (atWord("update")) {
				next();
				SigSpec left = parseSigSpec();
				SigSpec right = parseSigSpec();
				expectEol();
				requireEqualWidths(left, right, "update");
				updates.add(new Connection(left, right));
			} else if (atWord("attribute")) {
				parseAttributeLine();
			} else if (atWord("memwr")) {
				Attributes attributes = takeAttributes();
				next();
				String memory = expect(Token.Kind.ID).text();
				SigSpec address = parseSigSpec();
				SigSpec data = parseSigSpec();
				SigSpec enable = parseSigSpec();
				Constant priorityMask = atEol() ? new Constant.Int(0) : parseConstant();
				expectEol();
				memWrites.add(new MemWrite(attributes, memory, address, data, enable, priorityMask));
			} else {
				break;
			}
			skipEols();
		}
		return new SyncRule(type, signal, updates, memWrites);
	}

	// ---- signals and constants ----

	private SigSpec parseSigSpec() throws RtlilParseException {
		SigSpec spec = parseSigSpecAtom();
		while (peek().kind() == Token.Kind.LBRACKET)
			spec = parseSlice(spec);
		return spec;
	}

	private SigSpec parseSigSpecAtom() throws RtlilParseException {
		Token token = peek();
		switch (token.kind()) {
			case VALUE, INT, STRING -> {
				Constant constant = parseConstant();
				return SigSpec.constant(constantToBits(constant));
			}
			case ID -> {
				next();
				Wire wire = moduleWires.get(token.text());
				if (wire == null)
					throw error("reference to undeclared wire " + token.text());
				return SigSpec.wire(wire);
			}
			case LBRACE -> {
				next();
				List<SigSpec> parts = new ArrayList<>();
				while (peek().kind() != Token.Kind.RBRACE) {
					if (atEol() || peek().kind() == Token.Kind.EOF)
						throw error("unterminated concatenation");
					parts.add(parseSigSpec());
				}
				next(); // }
				// text order is MSB-first; the model is LSB-first
				List<SigChunk> chunks = new ArrayList<>();
				for (int i = parts.size() - 1; i >= 0; i--)
					chunks.addAll(parts.get(i).chunks());
				return new SigSpec(chunks);
			}
			default -> throw error("expected signal, got " + token);
		}
	}

	private SigSpec parseSlice(SigSpec base) throws RtlilParseException {
		// Yosys only allows selects directly on a whole wire reference
		if (!(base.chunks().size() == 1
				&& base.chunks().get(0) instanceof SigChunk.WireSlice(String wireName, int chunkOffset, int chunkWidth)
				&& chunkOffset == 0))
			throw error("bit/range select on something other than a plain wire");
		Wire wire = moduleWires.get(wireName);
		if (wire == null || chunkWidth != wire.width())
			throw error("bit/range select on something other than a plain wire");

		expect(Token.Kind.LBRACKET);
		int first = parseIntToken();
		Integer second = null;
		if (peek().kind() == Token.Kind.COLON) {
			next();
			second = parseIntToken();
		}
		expect(Token.Kind.RBRACKET);

		int a = toPhysicalIndex(wire, first);
		int b = second == null ? a : toPhysicalIndex(wire, second);
		int lsb = Math.min(a, b);
		int width = Math.abs(a - b) + 1;
		if (lsb < 0 || lsb + width > wire.width())
			throw error("select out of bounds on wire " + wire.name()
				+ " [" + first + (second != null ? ":" + second : "") + "]");
		return SigSpec.of(new SigChunk.WireSlice(wire.name(), lsb, width));
	}

	/** Source-level select index to physical bit offset, as the Yosys parser does. */
	private static int toPhysicalIndex(Wire wire, int textIndex) {
		return wire.upto()
			? wire.offset() + wire.width() - 1 - textIndex
			: textIndex - wire.offset();
	}

	private Constant parseConstant() throws RtlilParseException {
		Token token = next();
		return switch (token.kind()) {
			case VALUE -> new Constant.Bits(parseValueToken(token));
			case INT -> {
				long value = parseLong(token);
				if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
					throw error("integer constant out of 32-bit range: " + token.text());
				yield new Constant.Int((int) value);
			}
			case STRING -> new Constant.Str(token.text());
			default -> throw error("expected constant, got " + token);
		};
	}

	private BitVector parseValueToken(Token token) throws RtlilParseException {
		String text = token.text();
		int tick = text.indexOf('\'');
		int width;
		try {
			width = Integer.parseInt(text.substring(0, tick));
		} catch (NumberFormatException e) {
			throw error("constant width out of range: " + text);
		}
		String digits = text.substring(tick + 1);
		if (digits.length() > width)
			throw error("constant " + text + " has more digits than its width");
		return BitVector.fromTextDigits(digits, width);
	}

	/** Constants inside sigspecs become bit vectors (ints as 32-bit, strings as bytes). */
	private static BitVector constantToBits(Constant constant) {
		return switch (constant) {
			case Constant.Bits(BitVector value) -> value;
			case Constant.Int(int value) -> BitVector.of(value & 0xFFFFFFFFL, 32);
			case Constant.Str(String value) -> {
				// 8 bits per character, first character in the most significant byte
				List<ax.xz.max.minesynth.rtlil.State> bits = new ArrayList<>(value.length() * 8);
				for (int i = value.length() - 1; i >= 0; i--) {
					char c = value.charAt(i);
					for (int bit = 0; bit < 8; bit++)
						bits.add(ax.xz.max.minesynth.rtlil.State.of(((c >> bit) & 1) != 0));
				}
				yield new BitVector(bits);
			}
		};
	}

	// ---- attributes ----

	private void parseAttributeLine() throws RtlilParseException {
		expectWord("attribute");
		String name = expect(Token.Kind.ID).text();
		Constant value = parseConstant();
		expectEol();
		pendingAttributes.put(name, value);
	}

	private Attributes takeAttributes() {
		if (pendingAttributes.isEmpty())
			return Attributes.EMPTY;
		Attributes attributes = new Attributes(pendingAttributes);
		pendingAttributes.clear();
		return attributes;
	}

	private void requireNoPendingAttributes(String where) throws RtlilParseException {
		if (!pendingAttributes.isEmpty())
			throw error("dangling attributes " + pendingAttributes.keySet() + " at " + where);
	}

	// ---- token plumbing ----

	private Token peek() {
		return tokens.get(pos);
	}

	private Token next() {
		Token token = tokens.get(pos);
		if (token.kind() != Token.Kind.EOF)
			pos++;
		return token;
	}

	private boolean atWord(String word) {
		Token token = peek();
		return token.kind() == Token.Kind.WORD && token.text().equals(word);
	}

	private boolean atEol() {
		return peek().kind() == Token.Kind.EOL || peek().kind() == Token.Kind.EOF;
	}

	private Token expect(Token.Kind kind) throws RtlilParseException {
		Token token = next();
		if (token.kind() != kind)
			throw new RtlilParseException(source, token.line(), "expected " + describe(kind) + ", got " + token);
		return token;
	}

	private void expectWord(String word) throws RtlilParseException {
		Token token = next();
		if (token.kind() != Token.Kind.WORD || !token.text().equals(word))
			throw new RtlilParseException(source, token.line(), "expected '" + word + "', got " + token);
	}

	private void expectEol() throws RtlilParseException {
		Token token = peek();
		if (token.kind() == Token.Kind.EOF)
			return;
		if (token.kind() != Token.Kind.EOL)
			throw error("expected end of line, got " + token);
		next();
	}

	private void skipEols() {
		while (peek().kind() == Token.Kind.EOL)
			next();
	}

	private int parseIntToken() throws RtlilParseException {
		Token token = expect(Token.Kind.INT);
		long value = parseLong(token);
		if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
			throw error("integer out of range: " + token.text());
		return (int) value;
	}

	private long parseLongToken() throws RtlilParseException {
		return parseLong(expect(Token.Kind.INT));
	}

	private long parseLong(Token token) throws RtlilParseException {
		try {
			return Long.parseLong(token.text());
		} catch (NumberFormatException e) {
			throw error("integer out of range: " + token.text());
		}
	}

	private void requireEqualWidths(SigSpec left, SigSpec right, String statement) throws RtlilParseException {
		if (left.width() != right.width())
			throw error(statement + " width mismatch: " + left.width() + " vs " + right.width());
	}

	private RtlilParseException error(String message) {
		return new RtlilParseException(source, peek().line(), message);
	}

	private static String describe(Token.Kind kind) {
		return switch (kind) {
			case ID -> "an identifier";
			case INT -> "an integer";
			case VALUE -> "a sized constant";
			case STRING -> "a string";
			case WORD -> "a keyword";
			default -> kind.toString();
		};
	}
}
