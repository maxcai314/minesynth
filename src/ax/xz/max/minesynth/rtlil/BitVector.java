package ax.xz.max.minesynth.rtlil;

import java.util.ArrayList;
import java.util.List;

/**
 * An RTLIL bit-vector constant, stored LSB-first.
 *
 * <p>The text format writes these MSB-first as {@code <width>'<digits>}, for
 * example {@code 4'0101} (= decimal 5). Conversion happens at the parse and
 * write boundaries only; inside the model, index 0 is always the LSB.
 */
public record BitVector(List<State> bits) {
	public BitVector {
		bits = List.copyOf(bits);
	}

	/** Number of bits. */
	public int width() {
		return bits.size();
	}

	/** The state of bit {@code index} (0 = LSB). */
	public State bit(int index) {
		return bits.get(index);
	}

	/** True if every bit is 0 or 1. */
	public boolean isFullyDefined() {
		return bits.stream().allMatch(State::isDefined);
	}

	/**
	 * This vector as an unsigned long. Requires every bit defined and width
	 * at most 64.
	 *
	 * @throws IllegalStateException if a bit is x/z/m/- or the vector is too wide
	 */
	public long toLong() {
		if (width() > Long.SIZE)
			throw new IllegalStateException("vector too wide for long: " + width() + " bits");
		long value = 0;
		for (int i = width() - 1; i >= 0; i--) {
			State s = bits.get(i);
			if (!s.isDefined())
				throw new IllegalStateException("bit " + i + " is " + s.text() + ", not 0/1");
			value = (value << 1) | (s == State.S1 ? 1 : 0);
		}
		return value;
	}

	/** This vector as a signed 32-bit integer (two's complement, sign-extended). */
	public int toInt() {
		long raw = toLong();
		if (width() >= 32)
			return (int) raw;
		// sign-extend from the top bit
		if (width() > 0 && bit(width() - 1) == State.S1)
			raw |= -1L << width();
		return (int) raw;
	}

	/** Builds a vector of {@code width} bits from the low bits of {@code value}. */
	public static BitVector of(long value, int width) {
		List<State> bits = new ArrayList<>(width);
		for (int i = 0; i < width; i++)
			bits.add(State.of(((value >>> i) & 1) != 0));
		return new BitVector(bits);
	}

	/**
	 * Parses the digit part of a {@code <width>'<digits>} token. Digits are
	 * MSB-first in the text; missing high digits are zero-filled (matching the
	 * Yosys parser).
	 */
	public static BitVector fromTextDigits(String digits, int width) {
		if (digits.length() > width)
			throw new IllegalArgumentException(
				"constant has " + digits.length() + " digits but declared width " + width);
		List<State> bits = new ArrayList<>(width);
		for (int i = 0; i < width; i++) {
			int pos = digits.length() - 1 - i; // LSB is the last character
			bits.add(pos >= 0 ? State.fromText(digits.charAt(pos)) : State.S0);
		}
		return new BitVector(bits);
	}

	/** Text-format rendering, MSB-first: {@code 4'0101}. */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(width()).append('\'');
		for (int i = width() - 1; i >= 0; i--)
			sb.append(bits.get(i).text());
		return sb.toString();
	}
}
