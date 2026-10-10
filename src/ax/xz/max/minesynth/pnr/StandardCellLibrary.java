package ax.xz.max.minesynth.pnr;

import ax.xz.max.minesynth.netlist.CellKind;
import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.structure.Gates;
import ax.xz.max.minesynth.structure.Structure;

/**
 * Provides the synthesizer with a repertoire of known components.
 * Uses {@link ax.xz.max.minesynth.structure.Gates} to provide default implementations for logic gates.
 */
public class StandardCellLibrary implements CellLibrary {
	@Override
	public Structure structureFor(Cell cell, CellKind kind) {
		return switch (kind) {
			case MC_DFF31 -> Gates.dff(cell.intParameter("\\WIDTH").orElseThrow(() ->
				new IllegalArgumentException(cell.name() + " is missing WIDTH")));
//			case MC_ADFF31 -> null;
//			case MC_UAND16 -> null;
//			case MC_UOR16 -> null;
//			case MC_UNOR16 -> null;
//			case MC_UXOR2 -> null;
//			case MC_UXOR4 -> null;
//			case MC_UXOR8 -> null;
//			case MC_UXOR16 -> null;
			case GATE_AND -> Gates.andGate();
			case GATE_OR -> Gates.orGate();
			case GATE_XOR -> Gates.xorGate();
			case GATE_NOT -> Gates.notGate();
//			case GATE_MUX -> null;
			default -> throw new UnsupportedOperationException("not yet implemented");
		};
	}
}
