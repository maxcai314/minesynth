package ax.xz.max.minesynth.structure;

/**
 * One output of a junction via: the cell level (0 = bottom) and the face the
 * signal exits through at that level.
 */
public record ViaTap(int level, Direction face) {
	public ViaTap {
		if (level < 0)
			throw new IllegalArgumentException("tap level must not be negative, got " + level);
	}

	@Override
	public String toString() {
		return "ViaTap[level " + level + " " + face + "]";
	}
}
