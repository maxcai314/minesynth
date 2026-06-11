package ax.xz.max.minesynth.rtlil;

import java.util.ArrayList;
import java.util.List;

/**
 * A signal specification: an ordered list of {@link SigChunk}s, LSB-first.
 * This is what cell ports and connections refer to; it may mix wire slices
 * and constants, e.g. the text form <code>{ \hi 2'01 \lo }</code>.
 *
 * <p>Note the text format lists concatenation elements MSB-first; the parser
 * reverses them so chunk 0 here always carries the LSBs.
 */
public record SigSpec(List<SigChunk> chunks) {
	public SigSpec {
		chunks = List.copyOf(chunks);
	}

	public static SigSpec of(SigChunk... chunks) {
		return new SigSpec(List.of(chunks));
	}

	/** A constant sigspec from a bit vector. */
	public static SigSpec constant(BitVector value) {
		return of(new SigChunk.Bits(value));
	}

	/** The full width of {@code wire}. */
	public static SigSpec wire(Wire wire) {
		return of(new SigChunk.WireSlice(wire.name(), 0, wire.width()));
	}

	/** Total width in bits. */
	public int width() {
		int w = 0;
		for (SigChunk c : chunks)
			w += c.width();
		return w;
	}

	/** True if every chunk is a constant. */
	public boolean isFullyConstant() {
		return chunks.stream().allMatch(c -> c instanceof SigChunk.Bits);
	}

	/** This sigspec expanded to individual bits, LSB-first. */
	public List<SigBit> bits() {
		List<SigBit> bits = new ArrayList<>(width());
		for (SigChunk c : chunks) {
			switch (c) {
				case SigChunk.Bits(BitVector value) -> {
					for (State s : value.bits())
						bits.add(new SigBit.ConstBit(s));
				}
				case SigChunk.WireSlice(String wire, int offset, int width) -> {
					for (int i = 0; i < width; i++)
						bits.add(new SigBit.WireBit(wire, offset + i));
				}
			}
		}
		return bits;
	}

	/** The constant value of this sigspec. Requires {@link #isFullyConstant()}. */
	public BitVector constantValue() {
		List<State> bits = new ArrayList<>(width());
		for (SigChunk c : chunks) {
			if (!(c instanceof SigChunk.Bits(BitVector value)))
				throw new IllegalStateException("sigspec is not constant: " + this);
			bits.addAll(value.bits());
		}
		return new BitVector(bits);
	}

	@Override
	public String toString() {
		if (chunks.size() == 1)
			return chunks.get(0).toString();
		StringBuilder sb = new StringBuilder("{");
		// display MSB-first like the text format
		for (int i = chunks.size() - 1; i >= 0; i--)
			sb.append(' ').append(chunks.get(i));
		return sb.append(" }").toString();
	}
}
