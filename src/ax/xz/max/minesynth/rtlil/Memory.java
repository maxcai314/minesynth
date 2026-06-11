package ax.xz.max.minesynth.rtlil;

/**
 * A memory declaration ({@code size} words of {@code width} bits). Only seen
 * in pre-{@code memory_map} designs; the minesynth flow lowers all memories,
 * so post-techmap netlists never contain these.
 */
public record Memory(String name, int width, int size, int offset, Attributes attributes) {
	@Override
	public String toString() {
		return "Memory[" + name + ", width=" + width + ", size=" + size + "]";
	}
}
