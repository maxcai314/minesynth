package ax.xz.max.minesynth.rtlil;

import java.util.Optional;

/**
 * A module-level {@code parameter} declaration (the available parameters of a
 * blackbox or parametric module), with its default value if one was written.
 */
public record ModuleParameter(String name, Optional<Constant> defaultValue) {
	@Override
	public String toString() {
		return "ModuleParameter[" + name + defaultValue.map(v -> " = " + v).orElse("") + "]";
	}
}
