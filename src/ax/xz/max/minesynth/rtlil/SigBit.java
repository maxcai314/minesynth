package ax.xz.max.minesynth.rtlil;

/**
 * One bit of a {@link SigSpec}: either a constant state or one physical bit
 * of a wire. The bit-expanded view that connectivity analysis works on.
 */
public sealed interface SigBit permits SigBit.ConstBit, SigBit.WireBit {

	/** A constant bit. */
	record ConstBit(State state) implements SigBit {
		@Override
		public String toString() {
			return String.valueOf(state.text());
		}
	}

	/** Physical bit {@code index} of wire {@code wire} (0 = wire LSB). */
	record WireBit(String wire, int index) implements SigBit {
		@Override
		public String toString() {
			return wire + "[" + index + "]";
		}
	}
}
