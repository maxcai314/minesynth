package ax.xz.max.minesynth.rtlil;

import java.util.List;

/**
 * A {@code switch} inside a process case: compares {@code signal} against each
 * case's compare values in order; the first match wins.
 */
public record SwitchRule(Attributes attributes, SigSpec signal, List<CaseRule> cases) {
	public SwitchRule {
		cases = List.copyOf(cases);
	}
}
