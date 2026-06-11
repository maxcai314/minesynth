package ax.xz.max.minesynth.rtlil;

/**
 * A {@code memwr} action inside a sync rule: writes {@code data} to
 * {@code memory} at {@code address} for the bit lanes enabled by
 * {@code enable}. {@code priorityMask} orders simultaneous writes.
 */
public record MemWrite(
	Attributes attributes,
	String memory,
	SigSpec address,
	SigSpec data,
	SigSpec enable,
	Constant priorityMask
) {}
