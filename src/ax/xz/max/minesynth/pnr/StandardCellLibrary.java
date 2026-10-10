package ax.xz.max.minesynth.pnr;

import ax.xz.max.minesynth.netlist.CellKind;
import ax.xz.max.minesynth.pnr.CellLibrary.CellRequest;
import ax.xz.max.minesynth.pnr.CellLibrary.Constant;
import ax.xz.max.minesynth.pnr.CellLibrary.NetlistCell;
import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.structure.Gates;
import ax.xz.max.minesynth.structure.Structure;

/**
 * Provides the synthesizer with a repertoire of known components.
 * Uses {@link Gates} to provide default implementations for logic cells and
 * constant sources.
 */
public class StandardCellLibrary implements CellLibrary {
	@Override
	public Structure structureFor(CellRequest request) {
		return switch (request) {
			case NetlistCell(Cell cell, CellKind kind) -> structureFor(cell, kind);
			case Constant(boolean value) -> Gates.constant(value);
		};
	}

	private Structure structureFor(Cell cell, CellKind kind) {
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
			case GATE_MUX -> Gates.muxGate();
			default -> throw new UnsupportedOperationException("not yet implemented");
		};
	}
}
