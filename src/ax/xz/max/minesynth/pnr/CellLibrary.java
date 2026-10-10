package ax.xz.max.minesynth.pnr;

import ax.xz.max.minesynth.netlist.CellKind;
import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.structure.Structure;

import java.util.Objects;

/**
 * Supplies the redstone structure used to implement a physical cell request.
 *
 * <p>A request is either a {@link NetlistCell}, which carries the complete
 * RTLIL cell and its validated {@link CellKind}, or a {@link Constant}. This
 * keeps synthetic physical components such as constant sources configurable
 * without pretending that they are RTLIL cells.
 *
 * <p>Structures returned for netlist cells must order their input and output
 * pin lists according to {@link CellKind#ports(Cell)}: ports in declaration
 * order, with every multi-bit port expanded from bit 0 upward. A constant
 * structure must have no inputs and exactly one output. Pin coordinates and
 * faces are implementation details. {@link PnrDesign#fromNetlist} rejects
 * structures whose pin counts do not match these contracts.
 */
@FunctionalInterface
public interface CellLibrary {
	/** A physical component that the PNR bridge needs the library to provide. */
	sealed interface CellRequest permits NetlistCell, Constant {}

	/** A real RTLIL cell together with its validated logical kind. */
	record NetlistCell(Cell cell, CellKind kind) implements CellRequest {
		public NetlistCell {
			Objects.requireNonNull(cell, "cell");
			Objects.requireNonNull(kind, "kind");
		}
	}

	/** A synthetic zero-input physical source for a constant bit. */
	record Constant(boolean value) implements CellRequest {}

	/** Returns the structure implementing {@code request}, or {@code null} if unmapped. */
	Structure structureFor(CellRequest request);

	/**
	 * The standard library of physical logic cells and synthetic sources.
	 */
	static CellLibrary standardLibrary() {
		return new StandardCellLibrary();
	}
}
