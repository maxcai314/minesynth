package ax.xz.max.minesynth.rtlil;

import java.util.List;

/**
 * An RTLIL process: the not-yet-synthesized representation of an always block,
 * with a decision tree (the root case) and sync rules. Only seen before the
 * Yosys {@code proc} pass runs; the minesynth flow never feeds these downstream,
 * but the parser supports them so earlier dumps can be inspected.
 *
 * <p>Named {@code RtlilProcess} rather than {@code Process} to avoid the
 * {@code java.lang.Process} clash.
 */
public record RtlilProcess(String name, Attributes attributes, CaseRule rootCase, List<SyncRule> syncs) {
	public RtlilProcess {
		syncs = List.copyOf(syncs);
	}

	@Override
	public String toString() {
		return "RtlilProcess[" + name + "]";
	}
}
