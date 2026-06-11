package ax.xz.max.minesynth.rtlil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The RTLIL attributes attached to an object ({@code attribute \name value}
 * lines preceding it). Keys keep their {@code \}/{@code $} prefix.
 */
public record Attributes(Map<String, Constant> all) {
	/** Attribute marking the top module ({@code hierarchy -top}). */
	public static final String TOP = "\\top";
	/** Attribute marking a module as a blackbox (no contents, just ports). */
	public static final String BLACKBOX = "\\blackbox";
	/** Source location attribute, e.g. {@code "foo.sv:12.3-14.7"}. */
	public static final String SRC = "\\src";
	/** Initial value attribute on FF output wires. */
	public static final String INIT = "\\init";

	/** No attributes. */
	public static final Attributes EMPTY = new Attributes(Map.of());

	public Attributes {
		all = Collections.unmodifiableMap(new LinkedHashMap<>(all));
	}

	public Optional<Constant> get(String name) {
		return Optional.ofNullable(all.get(name));
	}

	/** True if the attribute exists and has a truthy value. */
	public boolean isSet(String name) {
		Constant c = all.get(name);
		return c != null && c.isTruthy();
	}

	public Optional<String> src() {
		return switch (all.get(SRC)) {
			case Constant.Str(String value) -> Optional.of(value);
			case null, default -> Optional.empty();
		};
	}

	@Override
	public String toString() {
		return all.toString();
	}
}
