package ax.xz.max.minesynth.rtlil;

/**
 * One RTLIL bit state. Beyond plain 0 and 1, RTLIL constants may carry
 * {@code x} (undefined), {@code z} (high impedance), {@code m} (marker, used
 * internally by some passes) and {@code -} (don't care, used in case compares).
 */
public enum State {
	S0('0'),
	S1('1'),
	SX('x'),
	SZ('z'),
	SM('m'),
	SA('-');

	private final char text;

	State(char text) {
		this.text = text;
	}

	/** The single character used for this state in the text format. */
	public char text() {
		return text;
	}

	/** True for {@link #S0} and {@link #S1} only. */
	public boolean isDefined() {
		return this == S0 || this == S1;
	}

	/** Parses one constant digit, e.g. {@code '1'} or {@code 'x'}. */
	public static State fromText(char c) {
		return switch (c) {
			case '0' -> S0;
			case '1' -> S1;
			case 'x' -> SX;
			case 'z' -> SZ;
			case 'm' -> SM;
			case '-' -> SA;
			default -> throw new IllegalArgumentException("not an RTLIL bit state: '" + c + "'");
		};
	}

	/** S1 for true, S0 for false. */
	public static State of(boolean value) {
		return value ? S1 : S0;
	}
}
