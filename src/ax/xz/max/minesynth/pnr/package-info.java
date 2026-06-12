/**
 * Placement and routing: turning a set of components plus desired
 * connectivity into one finished {@link ax.xz.max.minesynth.structure.Structure}.
 * The stage vocabulary loosely mirrors real chip design:
 *
 * <ol>
 * <li>A {@link ax.xz.max.minesynth.pnr.Floorplan} fixes the board's total cell
 *     dimensions and the exact boundary locations of its named input and
 *     output ports.</li>
 * <li>A {@link ax.xz.max.minesynth.pnr.PnrDesign} adds the named component
 *     instances and the {@link ax.xz.max.minesynth.pnr.Net}s connecting
 *     component pins and board ports.</li>
 * <li>A {@link ax.xz.max.minesynth.pnr.Placer} chooses a position,
 *     orientation, and color for every component, producing a
 *     {@link ax.xz.max.minesynth.pnr.Placement}.</li>
 * <li>A {@link ax.xz.max.minesynth.pnr.Router} generates the wires and vias
 *     realizing every net and returns the finished board structure with the
 *     floorplan ports re-exported as its pins.</li>
 * </ol>
 *
 * <p>Routing uses the {@link ax.xz.max.minesynth.structure.SignalStats}
 * contract for static signal-strength analysis: a connection is feasible iff
 * the driver's resolved output strength is at least the receiver's required
 * input strength.
 *
 * <p>{@link ax.xz.max.minesynth.pnr.NaivePlacer} and
 * {@link ax.xz.max.minesynth.pnr.NaiveRouter} are intentionally simple
 * baseline implementations; smarter algorithms implement the same interfaces.
 */
package ax.xz.max.minesynth.pnr;
