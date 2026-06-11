package ax.xz.max.minesynth.rtlil;

/**
 * One cell parameter value plus its flavor flag ({@code parameter signed} /
 * {@code parameter real} in the text format).
 */
public record CellParameter(Constant value, Flavor flavor) {
	public enum Flavor { NONE, SIGNED, REAL }

	public static CellParameter plain(Constant value) {
		return new CellParameter(value, Flavor.NONE);
	}

	@Override
	public String toString() {
		return flavor == Flavor.NONE ? value.toString() : flavor + " " + value;
	}
}
