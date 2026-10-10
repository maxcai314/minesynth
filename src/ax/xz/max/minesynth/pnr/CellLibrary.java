package ax.xz.max.minesynth.pnr;

import ax.xz.max.minesynth.netlist.CellKind;
import ax.xz.max.minesynth.rtlil.Cell;
import ax.xz.max.minesynth.structure.Structure;

/**
 * Supplies the redstone structure used to implement a netlist cell kind.
 *
 * <p>Returned structures must order their input and output pin lists according
 * to {@link CellKind#ports(Cell)}: ports in declaration order, with every
 * multi-bit port expanded from bit 0 upward. Pin coordinates and faces are an
 * implementation detail of the physical structure.
 */
@FunctionalInterface
public interface CellLibrary {
	/** Returns the structure implementing {@code cell}, or {@code null} if unmapped. */
	Structure structureFor(Cell cell, CellKind kind);

	/**
	 * The standard cell library that provides default implementations for logic gates.
	 */
	static CellLibrary standardLibrary() {
		return new StandardCellLibrary();
	}
}
