package ax.xz.max.minesynth.rtlil;

import java.util.List;

/**
 * One case of a process decision tree. The root case of a process has an empty
 * compare list; a {@code case} with an empty compare list inside a switch is
 * the default branch. Assign statements always precede child switches (the
 * text format enforces this and so does the parser).
 */
public record CaseRule(
	Attributes attributes,
	List<SigSpec> compare,
	List<Connection> assigns,
	List<SwitchRule> switches
) {
	public CaseRule {
		compare = List.copyOf(compare);
		assigns = List.copyOf(assigns);
		switches = List.copyOf(switches);
	}
}
