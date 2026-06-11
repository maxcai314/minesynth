/**
 * Validated bit-level netlist built from a parsed RTLIL design.
 *
 * <p>{@link ax.xz.max.minesynth.netlist.Netlist#of} takes the output of the
 * minesynth synthesis flow (a flat, post-techmap {@link ax.xz.max.minesynth.rtlil.RtlilDesign}),
 * checks it against the Minecraft cell contract ({@link ax.xz.max.minesynth.netlist.CellKind}),
 * and resolves all wires, slices and connect aliases into {@link ax.xz.max.minesynth.netlist.Net}s:
 * single-driver bit-level signals with an explicit driver pin and sink pins.
 * Placement, routing, timing and simulation all build on this structure.
 *
 * <p>Rejected with a {@link ax.xz.max.minesynth.netlist.NetlistException}: designs
 * with processes or memories (not yet synthesized), unknown cell types (anything
 * outside the contract), parameter or width violations, multiple drivers,
 * undriven sinks, x/z constants in connections, and combinational loops.
 */
package ax.xz.max.minesynth.netlist;
