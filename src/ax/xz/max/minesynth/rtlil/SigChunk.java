package ax.xz.max.minesynth.rtlil;

/**
 * One contiguous piece of a {@link SigSpec}: either a constant bit vector or
 * a slice of a wire. Mirrors Yosys's {@code SigChunk}.
 */
public sealed interface SigChunk permits SigChunk.Bits, SigChunk.WireSlice {

	/** Width of this chunk in bits. */
	int width();

	/** A constant chunk. */
	record Bits(BitVector value) implements SigChunk {
		@Override
		public int width() {
			return value.width();
		}

		@Override
		public String toString() {
			return value.toString();
		}
	}

	/**
	 * A slice of a wire: {@code width} bits starting at physical bit
	 * {@code offset} (0-based from the wire's LSB, independent of the wire's
	 * declared {@code offset}/{@code upto} numbering).
	 */
	record WireSlice(String wire, int offset, int width) implements SigChunk {
		public WireSlice {
			if (offset < 0 || width < 0)
				throw new IllegalArgumentException("negative slice: offset=" + offset + " width=" + width);
		}

		@Override
		public String toString() {
			return wire + " [" + (offset + width - 1) + ":" + offset + "]";
		}
	}
}
