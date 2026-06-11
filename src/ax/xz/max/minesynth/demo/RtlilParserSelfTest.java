package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.rtlil.BitVector;
import ax.xz.max.minesynth.rtlil.CaseRule;
import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.rtlil.CellParameter;
import ax.xz.max.minesynth.rtlil.Constant;
import ax.xz.max.minesynth.rtlil.RtlilDesign;
import ax.xz.max.minesynth.rtlil.RtlilModule;
import ax.xz.max.minesynth.rtlil.RtlilParseException;
import ax.xz.max.minesynth.rtlil.RtlilParser;
import ax.xz.max.minesynth.rtlil.RtlilProcess;
import ax.xz.max.minesynth.rtlil.RtlilWriter;
import ax.xz.max.minesynth.rtlil.SigBit;
import ax.xz.max.minesynth.rtlil.SigChunk;
import ax.xz.max.minesynth.rtlil.SigSpec;
import ax.xz.max.minesynth.rtlil.SwitchRule;
import ax.xz.max.minesynth.rtlil.SyncRule;
import ax.xz.max.minesynth.rtlil.Wire;

import java.util.List;

/**
 * Self-contained checks for the RTLIL parser: no files, no JUnit, prints PASS
 * or FAIL per check and exits nonzero on any failure.
 */
public final class RtlilParserSelfTest {
	private static int checks = 0;
	private static int failures = 0;

	public static void main(String[] args) {
		try {
			bitVectorChecks();
			wireAndSliceChecks();
			concatChecks();
			cellAndAttributeChecks();
			processChecks();
			errorChecks();
			roundTripChecks();
		} catch (Exception e) {
			failures++;
			System.out.println("FAIL unexpected exception: " + e);
			e.printStackTrace(System.out);
		}
		System.out.println();
		System.out.println(checks + " checks, " + failures + " failures"
			+ (failures == 0 ? " - ALL TESTS PASSED" : ""));
		if (failures != 0)
			System.exit(1);
	}

	// ---- checks ----

	private static void bitVectorChecks() {
		BitVector v = BitVector.fromTextDigits("0101", 4);
		check(v.toLong() == 5, "4'0101 reads as 5");
		check(v.toString().equals("4'0101"), "bit vector text round trip");
		check(BitVector.fromTextDigits("1111", 4).toInt() == -1, "toInt sign-extends");
		check(BitVector.fromTextDigits("11", 4).toLong() == 3, "short digits zero-fill the high bits");
		check(BitVector.of(5, 4).equals(v), "of(long) matches text parse");
		check(!BitVector.fromTextDigits("01xz", 4).isFullyDefined(), "x/z bits are not defined");
	}

	private static void wireAndSliceChecks() throws RtlilParseException {
		RtlilModule m = parseModule("""
			module \\m
			  wire width 8 offset 4 \\w
			  wire width 8 upto \\u
			  wire width 2 \\pair
			  wire input 1 \\narrow
			  connect \\narrow \\w [5]
			  connect \\narrow \\u [0]
			  connect \\pair \\w [9:8]
			  connect \\pair \\u [1:2]
			end
			""");
		Wire w = m.wire("\\w").orElseThrow();
		check(w.width() == 8 && w.offset() == 4 && !w.upto(), "wire options parse");
		Wire narrow = m.wire("\\narrow").orElseThrow();
		check(narrow.isInput() && narrow.port().orElseThrow().index() == 1, "port wire parses");

		check(m.connections().get(0).right().bits().equals(List.of(new SigBit.WireBit("\\w", 1))),
			"offset wire select normalizes to physical bit");
		check(m.connections().get(1).right().bits().equals(List.of(new SigBit.WireBit("\\u", 7))),
			"upto wire select counts from the top");
		check(m.connections().get(2).right().chunks().equals(List.of(new SigChunk.WireSlice("\\w", 4, 2))),
			"offset wire range select");
		check(m.connections().get(3).right().chunks().equals(List.of(new SigChunk.WireSlice("\\u", 5, 2))),
			"upto wire range select");
	}

	private static void concatChecks() throws RtlilParseException {
		RtlilModule m = parseModule("""
			module \\m
			  wire width 4 \\hi
			  wire width 4 \\lo
			  wire width 8 \\out
			  connect \\out { \\hi \\lo }
			  connect \\out { 4'1001 \\lo }
			end
			""");
		List<SigBit> bits = m.connections().get(0).right().bits();
		check(bits.get(0).equals(new SigBit.WireBit("\\lo", 0)), "concat is MSB-first in text, LSB-first in model");
		check(bits.get(7).equals(new SigBit.WireBit("\\hi", 3)), "concat MSB lands at the top");

		SigSpec mixed = m.connections().get(1).right();
		check(mixed.chunks().get(1).equals(new SigChunk.Bits(BitVector.fromTextDigits("1001", 4))),
			"constant chunk keeps its bit order");

		SigSpec constant = parseModule("""
			module \\m
			  wire width 4 \\x
			  connect \\x { 2'10 2'01 }
			end
			""").connections().get(0).right();
		check(constant.isFullyConstant() && constant.constantValue().toString().equals("4'1001"),
			"constant concat reassembles MSB-first text");
	}

	private static void cellAndAttributeChecks() throws RtlilParseException {
		RtlilDesign design = RtlilParser.parse("""
			autoidx 7
			attribute \\top 1
			attribute \\src "a.sv:1.2-3.4"
			module \\m
			  parameter \\P
			  parameter \\Q 42
			  wire width 16 \\a
			  wire \\y
			  wire $weird$name.x[2]
			  memory width 8 size 16 \\ram
			  attribute \\keep 1
			  attribute \\note "tab\\there\\nand \\101"
			  cell \\MC_UAND16 $cell1
			    parameter signed \\WIDTH 16
			    parameter \\V 4'01xz
			    connect \\A \\a
			    connect \\Y \\y
			  end
			  connect $weird$name.x[2] \\a [4]
			end
			""", "selftest");
		check(design.autoidx() == 7, "autoidx parses");
		RtlilModule m = design.modules().get(0);
		check(m.isTop() && m.attributes().src().orElse("").equals("a.sv:1.2-3.4"), "module attributes attach");
		check(m.parameters().get(0).defaultValue().isEmpty(), "parameter without default");
		check(m.parameters().get(1).defaultValue().orElseThrow().asInt() == 42, "parameter with default");
		check(m.memories().get(0).size() == 16, "memory parses");

		Cell cell = m.cells().get(0);
		check(cell.type().equals("\\MC_UAND16") && cell.name().equals("$cell1"), "cell type and name");
		check(cell.attributes().isSet("\\keep"), "cell attributes attach");
		check(cell.parameters().get("\\WIDTH").flavor() == CellParameter.Flavor.SIGNED, "signed parameter flag");
		check(cell.parameters().get("\\V").value().toString().equals("4'01xz"), "x/z parameter value");
		check(cell.attributes().get("\\note").orElseThrow() instanceof Constant.Str(String s)
			&& s.equals("tab\there\nand A"), "string escapes including octal");

		check(m.wire("$weird$name.x[2]").isPresent(), "brackets allowed inside auto-generated names");
		check(m.connections().get(0).right().bits().equals(List.of(new SigBit.WireBit("\\a", 4))),
			"slice after weird name still parses");
	}

	private static void processChecks() throws RtlilParseException {
		RtlilModule m = parseModule("""
			module \\m
			  wire width 2 \\s
			  wire \\d
			  wire \\q
			  wire \\clk
			  wire width 4 \\addr
			  process $proc1
			    assign \\q \\d
			    attribute \\full_case 1
			    switch \\s
			      case 2'00 , 2'11
			        assign \\q 1'0
			      case
			    end
			    sync posedge \\clk
			      update \\q \\d
			      memwr \\ram \\addr 4'0000 4'1111 2
			    sync init
			      update \\q 1'1
			  end
			end
			""");
		RtlilProcess process = m.processes().get(0);
		CaseRule root = process.rootCase();
		check(root.assigns().size() == 1 && root.switches().size() == 1, "root case body splits assigns/switches");

		SwitchRule sw = root.switches().get(0);
		check(sw.attributes().isSet("\\full_case"), "attributes attach to switch");
		check(sw.cases().size() == 2, "switch case count");
		check(sw.cases().get(0).compare().size() == 2, "comma-separated compare list");
		check(sw.cases().get(1).compare().isEmpty(), "default case has no compares");

		check(process.syncs().size() == 2, "sync count");
		SyncRule posedge = process.syncs().get(0);
		check(posedge.type() == SyncRule.Type.POSEDGE && posedge.signal().isPresent(), "posedge sync has signal");
		check(posedge.memWrites().size() == 1
			&& posedge.memWrites().get(0).priorityMask().asInt() == 2, "memwr parses");
		check(process.syncs().get(1).type() == SyncRule.Type.INIT
			&& process.syncs().get(1).signal().isEmpty(), "init sync has no signal");
	}

	private static void errorChecks() {
		expectError("""
			module \\m
			  connect \\x \\x
			end
			""", "undeclared wire");
		expectError("""
			module \\m
			  wire width 2 \\a
			  wire \\b
			  connect \\a \\b
			end
			""", "width mismatch");
		expectError("""
			module \\m
			  wire \\a
			  process $p
			    switch \\a
			    end
			    assign \\a 1'0
			  end
			end
			""", "assign statement after switch");
		expectError("attribute \\a 1\n", "dangling");
		expectError("""
			module \\m
			  wire \\a
			  connect \\a 4'0000 [1]
			end
			""", "select on something other than a plain wire");
	}

	private static void roundTripChecks() throws RtlilParseException {
		String sample = """
			autoidx 99
			attribute \\top 1
			module \\m
			  parameter \\P
			  wire width 8 offset 4 \\w
			  wire width 8 upto \\u
			  wire width 4 input 1 \\a
			  wire output 2 \\y
			  memory width 8 size 16 \\ram
			  cell $_AND_ $g1
			    parameter signed \\X -3
			    connect \\A \\w [5]
			    connect \\B { \\u [0] 1'1 \\a [3:2] }
			    connect \\Y \\y
			  end
			  process $p
			    assign \\y \\a [0]
			    switch \\a [1:0]
			      case 2'01
			        assign \\y 1'1
			      case
			    end
			    sync negedge \\w [4]
			      update \\y 1'0
			  end
			  connect \\w [11:8] \\a
			end
			""";
		RtlilDesign first = RtlilParser.parse(sample, "roundtrip-1");
		String written = RtlilWriter.write(first);
		RtlilDesign second;
		try {
			second = RtlilParser.parse(written, "roundtrip-2");
		} catch (RtlilParseException e) {
			failures++;
			checks++;
			System.out.println("FAIL written output does not reparse: " + e.getMessage());
			System.out.println(written);
			return;
		}
		check(first.equals(second), "write-then-parse reproduces the design exactly");
		check(RtlilWriter.write(second).equals(written), "writer output is stable");
	}

	// ---- helpers ----

	private static RtlilModule parseModule(String text) throws RtlilParseException {
		return RtlilParser.parse(text, "selftest").modules().get(0);
	}

	private static void expectError(String text, String messagePart) {
		checks++;
		try {
			RtlilParser.parse(text, "selftest");
			failures++;
			System.out.println("FAIL expected error containing \"" + messagePart + "\" but parse succeeded");
		} catch (RtlilParseException e) {
			if (e.getMessage().contains(messagePart)) {
				System.out.println("PASS rejects: " + messagePart);
			} else {
				failures++;
				System.out.println("FAIL wrong error, wanted \"" + messagePart + "\": " + e.getMessage());
			}
		}
	}

	private static void check(boolean condition, String label) {
		checks++;
		if (condition) {
			System.out.println("PASS " + label);
		} else {
			failures++;
			System.out.println("FAIL " + label);
		}
	}
}
