package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.structure.BlockColor;
import ax.xz.max.minesynth.structure.BlockPos;
import ax.xz.max.minesynth.structure.BuildGuide;
import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.Direction;
import ax.xz.max.minesynth.structure.Gates;
import ax.xz.max.minesynth.structure.PlacedStructure;
import ax.xz.max.minesynth.structure.Structure;
import ax.xz.max.minesynth.structure.StructureBlock;
import ax.xz.max.minesynth.structure.StructurePin;
import ax.xz.max.minesynth.structure.Vias;
import ax.xz.max.minesynth.structure.Wires;

import java.util.List;

import static ax.xz.max.minesynth.structure.Direction.EAST;
import static ax.xz.max.minesynth.structure.Direction.NORTH;
import static ax.xz.max.minesynth.structure.Direction.SOUTH;
import static ax.xz.max.minesynth.structure.Direction.WEST;
import static ax.xz.max.minesynth.structure.StructureBlock.REDSTONE_DUST;
import static ax.xz.max.minesynth.structure.StructureBlock.WOOL;

/**
 * Self-contained checks for the structure modeling package: no files, no
 * JUnit, prints PASS or FAIL per check and exits nonzero on any failure.
 */
public final class StructureSelfTest {
	private static int checks = 0;
	private static int failures = 0;

	public static void main(String[] args) {
		try {
			directionChecks();
			coordinateChecks();
			builderChecks();
			validationChecks();
			rotationChecks();
			recolorChecks();
			placedStructureChecks();
			compositionChecks();
			factoryChecks();
			buildGuideChecks();
		} catch (Exception e) {
			failures++;
			System.out.println("FAIL unexpected exception: " + e);
			e.printStackTrace(System.out);
		}
		System.out.println();
		System.out.println(checks + " checks, " + failures + " failures"
			+ (failures == 0 ? " - ALL TESTS PASSED" : ""));
		if (failures != 0)
			System.exit(1);
	}

	// ---- checks ----

	private static void directionChecks() {
		check(NORTH.opposite() == SOUTH && EAST.opposite() == WEST, "opposites");
		check(NORTH.clockwise() == EAST && WEST.clockwise() == NORTH, "clockwise cycle");
		check(NORTH.rotatedTo(EAST) == EAST && EAST.rotatedTo(EAST) == SOUTH
			&& SOUTH.rotatedTo(SOUTH) == NORTH && WEST.rotatedTo(WEST) == SOUTH,
			"rotatedTo composes ordinal arithmetic");
		check(NORTH.dz() == -1 && SOUTH.dz() == 1 && EAST.dx() == 1 && WEST.dx() == -1,
			"direction deltas match Minecraft axes");
	}

	private static void coordinateChecks() {
		check(new Cell(1, 2, 3).blockOrigin().equals(new BlockPos(3, 6, 9)), "cell to block origin");
		check(new BlockPos(5, 7, 2).cell().equals(new Cell(1, 2, 0)), "block to cell");
		check(new Cell(1, 0, 1).plus(NORTH, 2).equals(new Cell(1, 0, -1)), "cell direction offset");

		check(new StructurePin(new Cell(0, 0, 0), NORTH).connectionBlock().equals(new BlockPos(1, 1, 0)),
			"north port block");
		check(new StructurePin(new Cell(0, 0, 0), EAST).connectionBlock().equals(new BlockPos(2, 1, 1)),
			"east port block");
		check(new StructurePin(new Cell(0, 0, 0), SOUTH).connectionBlock().equals(new BlockPos(1, 1, 2)),
			"south port block");
		check(new StructurePin(new Cell(0, 0, 0), WEST).connectionBlock().equals(new BlockPos(0, 1, 1)),
			"west port block");
		check(new StructurePin(new Cell(2, 1, 0), NORTH).connectionBlock().equals(new BlockPos(7, 4, 0)),
			"port block scales with the cell");
	}

	private static void builderChecks() {
		expectThrow(() -> new Structure.Builder(new Cell(1, 1, 1)).placeBlock(3, 0, 0, WOOL),
			"outside the builder volume", "out-of-bounds placeBlock rejected");
		expectThrow(() -> new Structure.Builder(new Cell(1, 1, 1))
				.placeBlock(1, 0, 1, WOOL).placeBlock(1, 0, 1, WOOL),
			"already holds", "double placement rejected");

		Structure floor = new Structure.Builder(new Cell(1, 1, 1)).fill(0, 0, 0, 2, 0, 2, WOOL).build();
		check(floor.blocks().size() == 9, "fill places the whole box");

		Structure.Builder builder = new Structure.Builder(new Cell(1, 1, 1)).placeBlock(1, 0, 1, WOOL);
		Structure first = builder.build();
		builder.placeBlock(0, 0, 0, WOOL);
		check(first.blocks().size() == 1, "built structure is immune to later builder mutation");
	}

	private static void validationChecks() {
		expectThrow(() -> new Structure.Builder(new Cell(1, 1, 1)).placeBlock(1, 0, 1, REDSTONE_DUST).build(),
			"no supporting block", "dust at y=0 rejected (support would be outside)");
		expectThrow(() -> new Structure.Builder(new Cell(1, 1, 1)).placeBlock(1, 1, 1, REDSTONE_DUST).build(),
			"no supporting block", "floating dust rejected");
		Structure onGlass = new Structure.Builder(new Cell(1, 1, 1))
			.placeBlock(1, 0, 1, StructureBlock.GLASS)
			.placeBlock(1, 1, 1, REDSTONE_DUST)
			.build();
		check(onGlass.blocks().size() == 2, "dust on glass accepted");

		expectThrow(() -> new Structure.Builder(new Cell(1, 1, 1))
				.placeBlock(1, 1, 1, StructureBlock.RedstoneTorch.onWall(NORTH)).build(),
			"needs wool", "wall torch without backing rejected");
		expectThrow(() -> new Structure.Builder(new Cell(1, 1, 1))
				.placeBlock(1, 1, 1, StructureBlock.RedstoneTorch.onFloor()).build(),
			"needs a supporting block", "floating floor torch rejected");

		expectThrow(() -> new Structure.Builder(new Cell(2, 1, 1))
				.input(new StructurePin(new Cell(0, 0, 0), EAST)).build(),
			"boundary", "pin not on its boundary face rejected");
		expectThrow(() -> new Structure.Builder(new Cell(1, 1, 1))
				.input(new StructurePin(new Cell(5, 0, 0), NORTH)).build(),
			"outside the structure", "pin outside the volume rejected");
		expectThrow(() -> new Structure.Builder(new Cell(1, 1, 1))
				.input(new StructurePin(new Cell(0, 0, 0), NORTH))
				.input(new StructurePin(new Cell(0, 0, 0), NORTH)).build(),
			"duplicate pin", "duplicate pin rejected");

		expectThrow(() -> new Structure.Builder(new Cell(1, 1, 1))
				.placeBlock(1, 0, 0, WOOL).placeBlock(1, 1, 0, REDSTONE_DUST).build(),
			"touches the outer shell", "contained structure with shell dust off-port rejected");
		Structure notContained = new Structure.Builder(new Cell(1, 1, 1))
			.contained(false)
			.placeBlock(1, 0, 0, WOOL).placeBlock(1, 1, 0, REDSTONE_DUST)
			.build();
		check(!notContained.contained(), "same design accepted with contained(false)");
		Structure portDust = new Structure.Builder(new Cell(1, 1, 1))
			.placeBlock(1, 0, 0, WOOL).placeBlock(1, 1, 0, REDSTONE_DUST)
			.input(new StructurePin(new Cell(0, 0, 0), NORTH))
			.build();
		check(portDust.contained(), "shell dust at a port block is allowed while contained");

		expectThrow(() -> new StructureBlock.Repeater(NORTH, 5), "1..4", "repeater delay range enforced");
	}

	private static void rotationChecks() {
		Structure wire = Wires.wire(NORTH, EAST);
		check(wire.rotatedTo(NORTH) == wire, "rotating to NORTH is the identity");
		check(wire.rotatedTo(EAST).equals(Wires.wire(EAST, SOUTH)), "L-wire rotated east");
		check(wire.rotatedTo(SOUTH).equals(Wires.wire(SOUTH, WEST)), "L-wire rotated south");
		check(wire.rotatedTo(WEST).equals(Wires.wire(WEST, NORTH)), "L-wire rotated west");

		Structure gate = Gates.andGate();
		check(gate.rotatedTo(EAST).size().equals(new Cell(1, 1, 2)), "rotation swaps width and length");

		Structure repeaterFixture = new Structure.Builder(new Cell(1, 1, 1))
			.placeBlock(1, 0, 1, WOOL)
			.placeBlock(1, 1, 1, new StructureBlock.Repeater(NORTH, 2))
			.build();
		check(repeaterFixture.rotatedTo(EAST).blockAt(new BlockPos(1, 1, 1))
				.orElseThrow().equals(new StructureBlock.Repeater(EAST, 2)),
			"repeater facing rotates");

		Structure torchFixture = new Structure.Builder(new Cell(1, 1, 1))
			.contained(false)
			.placeBlock(1, 1, 1, WOOL)
			.placeBlock(1, 1, 2, StructureBlock.RedstoneTorch.onWall(NORTH))
			.build();
		Structure rotated = torchFixture.rotatedTo(EAST);
		check(rotated.blockAt(new BlockPos(0, 1, 1)).orElseThrow()
				.equals(StructureBlock.RedstoneTorch.onWall(EAST)),
			"torch position and attachment rotate together");
	}

	private static void recolorChecks() {
		Structure wire = Wires.wire(NORTH, SOUTH);
		Structure lime = wire.recolored(BlockColor.LIME);
		check(lime.blockAt(new BlockPos(1, 0, 0)).orElseThrow()
			.equals(new StructureBlock.Wool(BlockColor.LIME)), "UNASSIGNED wool takes the color");
		check(wire.recolored(BlockColor.UNASSIGNED) == wire, "recoloring to UNASSIGNED is a no-op");

		Structure explicit = new Structure.Builder(new Cell(1, 1, 1))
			.placeBlock(1, 0, 1, new StructureBlock.Wool(BlockColor.RED))
			.build();
		check(explicit.recolored(BlockColor.LIME).blockAt(new BlockPos(1, 0, 1)).orElseThrow()
			.equals(new StructureBlock.Wool(BlockColor.RED)), "explicit colors survive recoloring");
	}

	private static void placedStructureChecks() {
		PlacedStructure placed = new PlacedStructure(
			Gates.andGate(), new Cell(3, 0, 2), EAST, BlockColor.UNASSIGNED);
		check(placed.rotatedSize().equals(new Cell(1, 1, 2)), "placed rotated size");
		check(placed.inputPins().equals(List.of(
				new StructurePin(new Cell(3, 0, 2), WEST),
				new StructurePin(new Cell(3, 0, 3), WEST))),
			"input pins rotate then translate to parent coordinates");
		check(placed.outputPins().equals(List.of(new StructurePin(new Cell(3, 0, 2), EAST))),
			"output pin rotates then translates");

		PlacedStructure shifted = placed.addOffset(new Cell(1, 1, 1));
		check(shifted.position().equals(new Cell(4, 1, 3)) && placed.position().equals(new Cell(3, 0, 2)),
			"addOffset(Cell) shifts a copy and leaves the original alone");
		check(placed.addOffset(WEST, 2).position().equals(new Cell(1, 0, 2)),
			"addOffset(Direction, cells)");
		check(placed.withColor(BlockColor.RED).color() == BlockColor.RED
			&& placed.withOrientation(SOUTH).orientation() == SOUTH,
			"withColor and withOrientation withers");

		check(placed.occupiedCells().equals(List.of(new Cell(3, 0, 2), new Cell(3, 0, 3))),
			"occupied cells cover the rotated box");
	}

	private static void compositionChecks() {
		expectThrow(() -> new Structure.Builder(new Cell(2, 1, 1))
				.place(Wires.wire(NORTH, SOUTH), new Cell(0, 0, 0), NORTH, BlockColor.UNASSIGNED)
				.place(Wires.wire(NORTH, SOUTH), new Cell(0, 0, 0), NORTH, BlockColor.UNASSIGNED),
			"already occupied", "two placements may not share a cell");
		expectThrow(() -> new Structure.Builder(new Cell(2, 1, 1))
				.place(Wires.wire(NORTH, SOUTH), new Cell(2, 0, 0), NORTH, BlockColor.UNASSIGNED),
			"does not fit", "placement outside the builder rejected");

		Structure via = Vias.upward(2, NORTH, NORTH);
		expectThrow(() -> new Structure.Builder(new Cell(3, 2, 3))
				.place(via, new Cell(0, 0, 0), NORTH, BlockColor.UNASSIGNED)
				.place(via, new Cell(1, 0, 0), NORTH, BlockColor.UNASSIGNED),
			"intermingle", "two non-contained placements may not touch");
		// the assembly itself uses its shell (the vias do), so it cannot claim containment
		Structure.Builder spaced = new Structure.Builder(new Cell(3, 2, 3))
			.contained(false)
			.place(via, new Cell(0, 0, 0), NORTH, BlockColor.UNASSIGNED)
			.place(via, new Cell(2, 0, 0), NORTH, BlockColor.UNASSIGNED)
			.place(Wires.wire(NORTH, SOUTH), new Cell(1, 0, 0), NORTH, BlockColor.UNASSIGNED);
		check(spaced.build().blocks().size() > 0, "gap or contained neighbor between vias is fine");

		// color cascade: component stays UNASSIGNED in the sub-assembly, gets its
		// final color when the sub-assembly is placed with one. The sub-board
		// re-exports the wire's ports as its own pins, keeping it contained.
		Structure subBoard = new Structure.Builder(new Cell(1, 1, 1))
			.place(Wires.wire(NORTH, SOUTH), new Cell(0, 0, 0), NORTH, BlockColor.UNASSIGNED)
			.input(new StructurePin(new Cell(0, 0, 0), NORTH))
			.output(new StructurePin(new Cell(0, 0, 0), SOUTH))
			.build();
		check(subBoard.blockAt(new BlockPos(1, 0, 0)).orElseThrow()
			.equals(new StructureBlock.Wool(BlockColor.UNASSIGNED)), "UNASSIGNED placement defers coloring");
		Structure parent = new Structure.Builder(new Cell(1, 1, 1))
			.place(subBoard, new Cell(0, 0, 0), NORTH, BlockColor.CYAN)
			.input(new StructurePin(new Cell(0, 0, 0), NORTH))
			.output(new StructurePin(new Cell(0, 0, 0), SOUTH))
			.build();
		check(parent.blockAt(new BlockPos(1, 0, 0)).orElseThrow()
			.equals(new StructureBlock.Wool(BlockColor.CYAN)), "color cascades down the hierarchy");
	}

	private static void factoryChecks() {
		int combos = 0;
		for (Direction in : Direction.values()) {
			for (Direction out : Direction.values()) {
				if (in == out)
					continue;
				Structure wire = Wires.wire(in, out);
				combos++;
				boolean portsAreDust =
					wire.blockAt(wire.inputs().get(0).connectionBlock()).orElseThrow()
						.equals(new StructureBlock.RedstoneDust())
					&& wire.blockAt(wire.outputs().get(0).connectionBlock()).orElseThrow()
						.equals(new StructureBlock.RedstoneDust());
				if (!wire.contained() || !portsAreDust) {
					check(false, "wire " + in + " to " + out + " is contained with dust ports");
					return;
				}
			}
		}
		check(combos == 12, "all 12 wire combinations build");
		expectThrow(() -> Wires.wire(NORTH, NORTH), "different faces", "degenerate wire rejected");

		Structure via = Vias.upward(3, NORTH, EAST);
		check(via.size().equals(new Cell(1, 3, 1)) && !via.contained(), "upward via shape");
		check(via.inputs().equals(List.of(new StructurePin(new Cell(0, 0, 0), NORTH)))
			&& via.outputs().equals(List.of(new StructurePin(new Cell(0, 2, 0), EAST))),
			"upward via pins at bottom and top");
		check(via.blockAt(via.inputs().get(0).connectionBlock()).orElseThrow()
				.equals(new StructureBlock.RedstoneDust())
			&& via.blockAt(via.outputs().get(0).connectionBlock()).orElseThrow()
				.equals(new StructureBlock.RedstoneDust()),
			"via ports are dust");
		Structure down = Vias.downward(3, EAST, SOUTH);
		check(down.inputs().equals(List.of(new StructurePin(new Cell(0, 2, 0), EAST)))
			&& down.outputs().equals(List.of(new StructurePin(new Cell(0, 0, 0), SOUTH))),
			"downward via swaps the pin roles");
		expectThrow(() -> Vias.upward(1, NORTH, SOUTH), "at least 2 cells", "flat via rejected");

		check(Gates.andGate().inputs().equals(List.of(
				new StructurePin(new Cell(0, 0, 0), SOUTH),
				new StructurePin(new Cell(1, 0, 0), SOUTH)))
			&& Gates.andGate().outputs().equals(List.of(new StructurePin(new Cell(0, 0, 0), NORTH))),
			"AND gate pins match the canonical example");
		check(Gates.notGate().contained(), "NOT gate is contained");
		check(!Gates.orGate().contained(), "OR gate is not contained");
	}

	private static void buildGuideChecks() {
		String guide = BuildGuide.render(Gates.notGate());
		check(guide.contains("-- layer y=1 --") && guide.contains("legend:"), "guide renders layers and legend");
		check(guide.contains("INPUT[0]") && guide.contains("(1, 1, 0)"), "guide lists port blocks");

		String placedGuide = BuildGuide.render(new PlacedStructure(
			Gates.notGate(), new Cell(2, 0, 1), SOUTH, BlockColor.RED));
		check(placedGuide.contains("placed at cell Cell[2, 0, 1]") && placedGuide.contains("facing SOUTH"),
			"placed guide reports the offset");

		String compass = BuildGuide.compassDiagram();
		check(compass.contains("N (-z)") && compass.contains("E (+x)")
			&& compass.indexOf("N (-z)") < compass.indexOf("S (+z)"),
			"compass diagram shows the grid orientation");
	}

	// ---- helpers ----

	private static void expectThrow(Runnable action, String messagePart, String label) {
		checks++;
		try {
			action.run();
			failures++;
			System.out.println("FAIL " + label + " (no exception thrown)");
		} catch (IllegalArgumentException | IllegalStateException e) {
			if (e.getMessage() != null && e.getMessage().contains(messagePart)) {
				System.out.println("PASS " + label);
			} else {
				failures++;
				System.out.println("FAIL " + label + " (wrong message: " + e.getMessage() + ")");
			}
		}
	}

	private static void check(boolean condition, String label) {
		checks++;
		if (condition) {
			System.out.println("PASS " + label);
		} else {
			failures++;
			System.out.println("FAIL " + label);
		}
	}
}
