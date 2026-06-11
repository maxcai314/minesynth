/**
 * Spatial model for redstone builds: immutable, cell-aligned {@link ax.xz.max.minesynth.structure.Structure}s
 * that placement and routing assemble into a full circuit. This does not depend on any Minecraft APIs.
 *
 * <p>Conventions (every type in this package assumes them):
 * <ul>
 * <li><b>Axes match Minecraft</b>: +x = east, +y = up, +z = south. NORTH is
 *     the -z direction, so a structure's north face is its z=0 side.</li>
 * <li><b>Cells are 3x3x3 blocks</b> ({@link ax.xz.max.minesynth.structure.Cell#BLOCKS}).
 *     Structures are sized in whole cells and aligned to the cell grid; one
 *     component owns each cell, which keeps neighbors from interfering.</li>
 * <li><b>Ports</b>: a {@link ax.xz.max.minesynth.structure.StructurePin} names a cell
 *     and a cardinal face; signals cross cell boundaries through the center
 *     block of that face at middle height (see
 *     {@link ax.xz.max.minesynth.structure.StructurePin#connectionBlock()}).
 *     Components put redstone dust there; facing ports in adjacent cells then
 *     connect automatically.</li>
 * <li><b>Support</b>: a structure may not assume anything exists below it.
 *     Dust, repeaters and floor torches must rest on a block inside the same
 *     structure; the builder rejects violations.</li>
 * <li><b>Containment</b>: a contained structure keeps redstone components off
 *     its outer block shell except at port blocks, so it can sit next to
 *     anything. Non-contained structures (vias and most gates) must never be
 *     placed in cells adjacent to another non-contained structure.</li>
 * <li><b>Orientation</b>: structures are designed facing NORTH and may be
 *     placed rotated; the orientation names the direction local north ends up
 *     pointing. Repeater facings and torch attachments rotate along.</li>
 * </ul>
 *
 * <p>The package models blocks; it does not simulate redstone physics.
 * For debugging, {@link ax.xz.max.minesynth.structure.BuildGuide} helps visualize structures.
 */
package ax.xz.max.minesynth.structure;
