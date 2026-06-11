package ax.xz.max.minesynth.rtlil;

/**
 * A {@code connect} statement: {@code left} and {@code right} carry the same
 * signal (an alias, not a directed assignment). Both sides have equal width.
 * Also used for the lhs/rhs pairs inside process assign and update statements,
 * where it does act as a directed assignment to {@code left}.
 */
public record Connection(SigSpec left, SigSpec right) {
	@Override
	public String toString() {
		return left + " = " + right;
	}
}
