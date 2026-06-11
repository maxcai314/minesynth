package ax.xz.max.minesynth.structure;

/**
 * The sixteen Minecraft dye colors plus {@link #UNASSIGNED}. Components should
 * build their wool and glass as UNASSIGNED; the color is assigned when the
 * component is placed into a larger build, which makes the final circuit easy
 * to read in game.
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
