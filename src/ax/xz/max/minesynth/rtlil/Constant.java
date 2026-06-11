package ax.xz.max.minesynth.rtlil;

/**
 * An RTLIL constant: a bit vector, a 32-bit signed integer, or a string.
 * Used for attribute values, cell parameters, and inside signal specs.
 *
 * <p>Yosys treats these forms loosely (an attribute written as {@code 1} or as
 * {@code 1'1} means the same thing), so consumers should prefer the {@code as*}
 * coercions over matching the exact variant.
 */
public sealed interface Constant permits Constant.Bits, Constant.Int, Constant.Str {

	/** A sized bit-vector constant such as {@code 4'0101}. */
	record Bits(BitVector value) implements Constant {
		@Override
		public String toString() {
			return value.toString();
		}
	}

	/** A plain decimal integer constant (32-bit signed in RTLIL). */
	record Int(int value) implements Constant {
		@Override
		public String toString() {
			return java.lang.Integer.toString(value);
		}
	}

	/** A quoted string constant, unescaped. */
	record Str(String value) implements Constant {
		@Override
		public String toString() {
			return value;
		}
	}

	/**
	 * This constant as an int.
	 *
	 * @throws IllegalStateException for strings and for bit vectors with x/z bits
	 */
	default int asInt() {
		return switch (this) {
			case Int(int value) -> value;
			case Bits(BitVector value) -> value.toInt();
			case Str s -> throw new IllegalStateException("string constant used as int: " + s.value());
		};
	}

	/** True if this is a nonzero integer or a bit vector containing a 1. */
	default boolean isTruthy() {
		return switch (this) {
			case Int(int value) -> value != 0;
			case Bits(BitVector value) -> value.bits().contains(State.S1);
			case Str(String value) -> !value.isEmpty();
		};
	}
}
