package ax.xz.max.minesynth.structure;

/**
 * Conservative signal and timing statistics of a structure, used for static
 * analysis during routing.
 *
 * <p>The measurement contract, chosen so chained analysis needs no further
 * arithmetic:
 * <ul>
 * <li>{@code inputSignal} is the required strength measured as if a dust were
 *     placed on THIS structure's input pin block.</li>
 * <li>{@code outputSignal} is the strength measured as if a dust were placed
 *     on the output pin's ADJACENT cell's port block, i.e. after crossing the
 *     cell boundary. Negative means relative (out = in + value, e.g. a plain
 *     wire is -3); positive means absolute (a repeater wire emits 12
 *     regardless of input); zero is illegal.</li>
 * <li>A connection is feasible iff the driver's resolved outputSignal is at
 *     least the receiver's inputSignal.</li>
 * <li>{@code delayTicks} is the number of redstone ticks (0.1 s) until the
 *     outputs settle after an input change.</li>
 * </ul>
 *
 * <p>For structures with several pins, values are the worst case across pins.
 * All values should be conservative.
 */
public record SignalStats(int inputSignal, int outputSignal, int delayTicks) {
	/** Plain-wire-like default: requires 3 arriving, loses 3, settles instantly. */
	public static final SignalStats WIRE_LIKE = new SignalStats(3, -3, 0);

	public SignalStats {
		if (inputSignal < 1 || inputSignal > 15)
			throw new IllegalArgumentException("inputSignal must be 1..15, got " + inputSignal);
		if (outputSignal == 0 || outputSignal < -14 || outputSignal > 15)
			throw new IllegalArgumentException(
				"outputSignal must be -14..-1 (relative) or 1..15 (absolute), got " + outputSignal);
		if (delayTicks < 0)
			throw new IllegalArgumentException("delayTicks must not be negative, got " + delayTicks);
	}

	/** The strength at the next structure's input pin block given {@code arriving} at ours. */
	public int resolveOutput(int arriving) {
		return outputSignal < 0 ? arriving + outputSignal : outputSignal;
	}

	@Override
	public String toString() {
		return "SignalStats[in=" + inputSignal + ", out=" + outputSignal + ", delay=" + delayTicks + "t]";
	}
}
