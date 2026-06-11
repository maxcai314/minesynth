package ax.xz.max.minesynth.netlist;

/**
 * The contract of one cell port: its direction and width (already resolved
 * against the cell's parameters, so a 17-bit {@code MC_DFF31} reports D and Q
 * as width 17).
 */
public record PortSpec(Direction direction, int width) {
	public enum Direction { INPUT, OUTPUT }

	public static PortSpec input(int width) {
		return new PortSpec(Direction.INPUT, width);
	}

	public static PortSpec output(int width) {
		return new PortSpec(Direction.OUTPUT, width);
	}
}
