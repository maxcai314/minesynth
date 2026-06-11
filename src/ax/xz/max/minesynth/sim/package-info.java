/**
 * Two-state functional simulator over a validated {@link ax.xz.max.minesynth.netlist.Netlist}.
 *
 * <p>Semantics, chosen to match redstone rather than 4-state Verilog:
 * <ul>
 * <li>Every net is 0 or 1; there is no x or z. Registers power up at 0.</li>
 * <li>Combinational logic settles instantly (evaluated in topological order;
 *     the netlist layer already guarantees there are no combinational loops).</li>
 * <li>{@code MC_DFF31} samples D on the rising edge of its CLK net. Edges are
 *     detected per FF on the net itself, so inverted (negedge) and gated clocks
 *     behave correctly, including ripple-clocked chains.</li>
 * <li>{@code MC_ADFF31} additionally forces its state to ARST_VALUE whenever
 *     its ARST net is 1, without needing a clock edge.</li>
 * </ul>
 */
package ax.xz.max.minesynth.sim;
