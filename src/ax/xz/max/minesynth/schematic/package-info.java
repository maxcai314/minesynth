/**
 * Write-only export of {@link ax.xz.max.minesynth.structure.Structure}s to
 * MCEdit-format {@code .schematic} files, the format WorldEdit's
 * {@code //schem load} understands.
 *
 * <p>The format is gzipped NBT with legacy numeric block IDs. Only the six
 * tags WorldEdit's reader actually requires are written (root compound named
 * {@code Schematic}; {@code Width}/{@code Height}/{@code Length} shorts;
 * {@code Materials} = {@code "Alpha"}; {@code Blocks} and {@code Data} byte
 * arrays); everything optional is omitted.
 *
 * <p>This package never reads or parses NBT, and the jNBT library it uses
 * internally does not appear in any public signature.
 */
package ax.xz.max.minesynth.schematic;
