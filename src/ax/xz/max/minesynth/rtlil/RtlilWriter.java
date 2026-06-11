package ax.xz.max.minesynth.rtlil;

import java.util.HashMap;
import java.util.Map;

/**
 * Serializes an {@link RtlilDesign} back to RTLIL text in the same shape Yosys
 * writes. Mainly a test oracle: a parsed design written out and re-read (by
 * this parser or by {@code yosys read_rtlil}) must describe the same netlist.
 */
public final class RtlilWriter {
	private final StringBuilder out = new StringBuilder();
	private Map<String, Wire> moduleWires;

	private RtlilWriter() {}

	/** The design as RTLIL text. */
	public static String write(RtlilDesign design) {
		RtlilWriter writer = new RtlilWriter();
		writer.dumpDesign(design);
		return writer.out.toString();
	}

	private void dumpDesign(RtlilDesign design) {
		if (design.autoidx() != 0)
			out.append("autoidx ").append(design.autoidx()).append('\n');
		for (RtlilModule module : design.modules())
			dumpModule(module);
	}

	private void dumpModule(RtlilModule module) {
		moduleWires = new HashMap<>();
		for (Wire wire : module.wires())
			moduleWires.put(wire.name(), wire);

		dumpAttributes("", module.attributes());
		out.append("module ").append(module.name()).append('\n');
		for (ModuleParameter parameter : module.parameters()) {
			out.append("  parameter ").append(parameter.name());
			parameter.defaultValue().ifPresent(v -> {
				out.append(' ');
				dumpConstant(v);
			});
			out.append('\n');
		}
		for (Wire wire : module.wires())
			dumpWire(wire);
		for (Memory memory : module.memories())
			dumpMemory(memory);
		for (Cell cell : module.cells())
			dumpCell(cell);
		for (RtlilProcess process : module.processes())
			dumpProcess(process);
		for (Connection connection : module.connections()) {
			out.append("  connect ");
			dumpSigSpec(connection.left());
			out.append(' ');
			dumpSigSpec(connection.right());
			out.append('\n');
		}
		out.append("end\n");
		moduleWires = null;
	}

	private void dumpWire(Wire wire) {
		dumpAttributes("  ", wire.attributes());
		out.append("  wire ");
		if (wire.width() != 1)
			out.append("width ").append(wire.width()).append(' ');
		if (wire.upto())
			out.append("upto ");
		if (wire.offset() != 0)
			out.append("offset ").append(wire.offset()).append(' ');
		wire.port().ifPresent(port -> {
			String direction = switch (port.direction()) {
				case INPUT -> "input";
				case OUTPUT -> "output";
				case INOUT -> "inout";
			};
			out.append(direction).append(' ').append(port.index()).append(' ');
		});
		if (wire.signed())
			out.append("signed ");
		out.append(wire.name()).append('\n');
	}

	private void dumpMemory(Memory memory) {
		dumpAttributes("  ", memory.attributes());
		out.append("  memory ");
		if (memory.width() != 1)
			out.append("width ").append(memory.width()).append(' ');
		if (memory.size() != 0)
			out.append("size ").append(memory.size()).append(' ');
		if (memory.offset() != 0)
			out.append("offset ").append(memory.offset()).append(' ');
		out.append(memory.name()).append('\n');
	}

	private void dumpCell(Cell cell) {
		dumpAttributes("  ", cell.attributes());
		out.append("  cell ").append(cell.type()).append(' ').append(cell.name()).append('\n');
		cell.parameters().forEach((name, parameter) -> {
			out.append("    parameter ");
			switch (parameter.flavor()) {
				case SIGNED -> out.append("signed ");
				case REAL -> out.append("real ");
				case NONE -> {}
			}
			out.append(name).append(' ');
			dumpConstant(parameter.value());
			out.append('\n');
		});
		cell.connections().forEach((port, signal) -> {
			out.append("    connect ").append(port).append(' ');
			dumpSigSpec(signal);
			out.append('\n');
		});
		out.append("  end\n");
	}

	private void dumpProcess(RtlilProcess process) {
		dumpAttributes("  ", process.attributes());
		out.append("  process ").append(process.name()).append('\n');
		dumpCaseBody("    ", process.rootCase());
		for (SyncRule sync : process.syncs())
			dumpSync(sync);
		out.append("  end\n");
	}

	private void dumpCaseBody(String indent, CaseRule caseRule) {
		for (Connection assign : caseRule.assigns()) {
			out.append(indent).append("assign ");
			dumpSigSpec(assign.left());
			out.append(' ');
			dumpSigSpec(assign.right());
			out.append('\n');
		}
		for (SwitchRule switchRule : caseRule.switches())
			dumpSwitch(indent, switchRule);
	}

	private void dumpSwitch(String indent, SwitchRule switchRule) {
		dumpAttributes(indent, switchRule.attributes());
		out.append(indent).append("switch ");
		dumpSigSpec(switchRule.signal());
		out.append('\n');
		for (CaseRule caseRule : switchRule.cases()) {
			dumpAttributes(indent + "  ", caseRule.attributes());
			out.append(indent).append("  case ");
			for (int i = 0; i < caseRule.compare().size(); i++) {
				if (i > 0)
					out.append(" , ");
				dumpSigSpec(caseRule.compare().get(i));
			}
			out.append('\n');
			dumpCaseBody(indent + "    ", caseRule);
		}
		out.append(indent).append("end\n");
	}

	private void dumpSync(SyncRule sync) {
		out.append("    sync ").append(sync.type().keyword());
		sync.signal().ifPresent(signal -> {
			out.append(' ');
			dumpSigSpec(signal);
		});
		out.append('\n');
		for (Connection update : sync.updates()) {
			out.append("      update ");
			dumpSigSpec(update.left());
			out.append(' ');
			dumpSigSpec(update.right());
			out.append('\n');
		}
		for (MemWrite memWrite : sync.memWrites()) {
			dumpAttributes("      ", memWrite.attributes());
			out.append("      memwr ").append(memWrite.memory()).append(' ');
			dumpSigSpec(memWrite.address());
			out.append(' ');
			dumpSigSpec(memWrite.data());
			out.append(' ');
			dumpSigSpec(memWrite.enable());
			out.append(' ');
			dumpConstant(memWrite.priorityMask());
			out.append('\n');
		}
	}

	private void dumpAttributes(String indent, Attributes attributes) {
		attributes.all().forEach((name, value) -> {
			out.append(indent).append("attribute ").append(name).append(' ');
			dumpConstant(value);
			out.append('\n');
		});
	}

	private void dumpSigSpec(SigSpec signal) {
		if (signal.chunks().size() == 1) {
			dumpChunk(signal.chunks().get(0));
			return;
		}
		out.append('{');
		// the text format lists chunks MSB-first
		for (int i = signal.chunks().size() - 1; i >= 0; i--) {
			out.append(' ');
			dumpChunk(signal.chunks().get(i));
		}
		out.append(" }");
	}

	private void dumpChunk(SigChunk chunk) {
		switch (chunk) {
			case SigChunk.Bits(BitVector value) -> out.append(value);
			case SigChunk.WireSlice(String wireName, int offset, int width) -> {
				Wire wire = moduleWires.get(wireName);
				if (wire == null)
					throw new IllegalArgumentException("sigspec references unknown wire " + wireName);
				out.append(wireName);
				if (offset == 0 && width == wire.width())
					return;
				// physical offsets back to source-level select indexes
				if (width == 1) {
					int index = wire.upto()
						? wire.offset() + wire.width() - offset - 1
						: wire.offset() + offset;
					out.append(" [").append(index).append(']');
				} else if (wire.upto()) {
					int first = wire.offset() + wire.width() - (offset + width - 1) - 1;
					int second = wire.offset() + wire.width() - offset - 1;
					out.append(" [").append(first).append(':').append(second).append(']');
				} else {
					int first = wire.offset() + offset + width - 1;
					int second = wire.offset() + offset;
					out.append(" [").append(first).append(':').append(second).append(']');
				}
			}
		}
	}

	private void dumpConstant(Constant constant) {
		switch (constant) {
			case Constant.Bits(BitVector value) -> out.append(value);
			case Constant.Int(int value) -> out.append(value);
			case Constant.Str(String value) -> {
				out.append('"');
				for (int i = 0; i < value.length(); i++) {
					char c = value.charAt(i);
					switch (c) {
						case '\n' -> out.append("\\n");
						case '\t' -> out.append("\\t");
						case '"' -> out.append("\\\"");
						case '\\' -> out.append("\\\\");
						default -> {
							if (c < 32 || c > 126)
								out.append('\\').append(String.format("%03o", (int) c));
							else
								out.append(c);
						}
					}
				}
				out.append('"');
			}
		}
	}
}
