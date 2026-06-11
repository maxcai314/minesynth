package ax.xz.max.minesynth.rtlil;

import java.util.List;
import java.util.Optional;

/**
 * A {@code sync} rule of a process: when {@code type} fires (with respect to
 * {@code signal}, absent for always/global/init), the {@code updates} take
 * effect and the {@code memWrites} are performed.
 */
public record SyncRule(
	Type type,
	Optional<SigSpec> signal,
	List<Connection> updates,
	List<MemWrite> memWrites
) {
	public enum Type { ALWAYS, GLOBAL, INIT, LOW, HIGH, POSEDGE, NEGEDGE, EDGE;
		/** The lowercase keyword used in the text format. */
		public String keyword() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		/** True for the types that take a signal operand. */
		public boolean hasSignal() {
			return switch (this) {
				case LOW, HIGH, POSEDGE, NEGEDGE, EDGE -> true;
				case ALWAYS, GLOBAL, INIT -> false;
			};
		}
	}

	public SyncRule {
		updates = List.copyOf(updates);
		memWrites = List.copyOf(memWrites);
	}
}
