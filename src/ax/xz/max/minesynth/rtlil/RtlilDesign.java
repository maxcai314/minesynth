package ax.xz.max.minesynth.rtlil;

import java.util.List;
import java.util.Optional;

/**
 * A complete parsed RTLIL file: the {@code autoidx} counter and the modules in
 * file order. Files written by the minesynth flow contain the blackbox MC_*
 * declarations followed by the (flattened) user design, which carries the
 * {@code \top} attribute.
 */
public record RtlilDesign(long autoidx, List<RtlilModule> modules) {
	public RtlilDesign {
		modules = List.copyOf(modules);
	}

	public Optional<RtlilModule> module(String name) {
		return modules.stream().filter(m -> m.name().equals(name)).findFirst();
	}

	/**
	 * The module marked with the {@code \top} attribute, or, failing that, the
	 * only non-blackbox module of the design.
	 */
	public Optional<RtlilModule> topModule() {
		Optional<RtlilModule> marked = modules.stream().filter(RtlilModule::isTop).findFirst();
		if (marked.isPresent())
			return marked;
		List<RtlilModule> candidates = modules.stream().filter(m -> !m.isBlackbox()).toList();
		return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
	}

	@Override
	public String toString() {
		return "RtlilDesign[" + modules.size() + " modules]";
	}
}
