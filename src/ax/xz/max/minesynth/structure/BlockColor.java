package ax.xz.max.minesynth.structure;

/**
 * The sixteen Minecraft dye colors plus {@link #UNASSIGNED}. Components should
 * build their wool and glass as UNASSIGNED; the color is assigned when the
 * component is placed into a larger build, which makes the final circuit easy
 * to read in game.
 *
 * <p>The declaration order of the sixteen colors deliberately matches the
 * legacy dye data values 0..15; the schematic exporter relies on the ordinal.
 * Do not reorder.
 */
public enum BlockColor {
	WHITE,
	ORANGE,
	MAGENTA,
	LIGHT_BLUE,
	YELLOW,
	LIME,
	PINK,
	GRAY,
	LIGHT_GRAY,
	CYAN,
	PURPLE,
	BLUE,
	BROWN,
	GREEN,
	RED,
	BLACK,
	/** Placeholder recolored at placement time; legal to leave in place. */
	UNASSIGNED
}
