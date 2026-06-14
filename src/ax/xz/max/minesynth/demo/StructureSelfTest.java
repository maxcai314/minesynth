package ax.xz.max.minesynth.demo;

import ax.xz.max.minesynth.schematic.SchematicWriter;
import ax.xz.max.minesynth.structure.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

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
			placementRuleChecks();
			factoryChecks();
			signalStatsChecks();
			junctionChecks();
			junctionViaChecks();
			buildGuideChecks();
			schematicChecks();
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
		check(Direction.fromDelta(1, 0) == EAST && Direction.fromDelta(0, -1) == NORTH,
			"fromDelta maps a unit step back to its direction");
		check(Direction.between(new Cell(2, 0, 1), new Cell(2, 0, 2)) == SOUTH
			&& Direction.between(new Cell(2, 0, 1), new Cell(1, 0, 1)) == WEST,
			"between names the step from one cell to an adjacent one");
		expectThrow(() -> Direction.between(new Cell(0, 0, 0), new Cell(0, 1, 0)),
			"not horizontally adjacent", "between rejects a vertical pair");
	}

	private static void coordinateChecks() {
		check(new Cell(1, 2, 3).blockOrigin().equals(new BlockPos(3, 6, 9)), "cell to block origin");
		check(new BlockPos(5, 7, 2).cell().equals(new Cell(1, 2, 0)), "block to cell");
		check(new Cell(1, 0, 1).plus(NORTH, 2).equals(new Cell(1, 0, -1)), "cell direction offset");
		check(new Cell(4, 0, 7).atHeight(3).equals(new Cell(4, 3, 7)), "atHeight keeps the column");

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
			"touches a side shell", "contained structure with shell dust off-port rejected");
		Structure notContained = new Structure.Builder(new Cell(1, 1, 1))
			.horizontallyContained(false)
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
			.horizontallyContained(false)
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
			"incompatible", "two non-contained placements may not touch");
		// the assembly itself uses its shell (the vias do), so it cannot claim containment
		Structure.Builder spaced = new Structure.Builder(new Cell(3, 2, 3))
			.horizontallyContained(false)
			.place(via, new Cell(0, 0, 0), NORTH, BlockColor.UNASSIGNED)
			.place(via, new Cell(2, 0, 0), NORTH, BlockColor.UNASSIGNED)
			.place(Wires.wire(NORTH, SOUTH), new Cell(1, 0, 0), NORTH, BlockColor.UNASSIGNED);
		check(!spaced.build().blocks().isEmpty(), "gap or contained neighbor between vias is fine");

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

		// the pin re-export idiom: a placed component's pins become the assembly's own
		PlacedStructure placedWire = new PlacedStructure(
			Wires.wire(NORTH, SOUTH), new Cell(1, 0, 0), NORTH, BlockColor.UNASSIGNED);
		Structure reexport = new Structure.Builder(new Cell(2, 1, 1))
			.place(placedWire)
			.addInputs(placedWire.inputPins())
			.addOutputs(placedWire.outputPins())
			.build();
		check(reexport.inputs().equals(placedWire.inputPins())
			&& reexport.outputs().equals(placedWire.outputPins()),
			"addInputs/addOutputs re-export placed pins");
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
					wire.blockAt(wire.inputs().getFirst().connectionBlock()).orElseThrow()
						.equals(new StructureBlock.RedstoneDust())
					&& wire.blockAt(wire.outputs().getFirst().connectionBlock()).orElseThrow()
						.equals(new StructureBlock.RedstoneDust());
				if (!wire.contained() || !portsAreDust) {
					check(false, "wire " + in + " to " + out + " is contained with dust ports");
					return;
				}
			}
		}
		check(combos == 12, "all 12 wire combinations build");
		expectThrow(() -> Wires.wire(NORTH, NORTH), "different faces", "degenerate wire rejected");

		Structure repeaterWire = Wires.repeaterWire(SOUTH, NORTH);
		check(repeaterWire.contained()
			&& repeaterWire.blockAt(repeaterWire.inputs().getFirst().connectionBlock()).orElseThrow()
				.equals(new StructureBlock.Repeater(NORTH, 1)),
			"repeater wire has the repeater on its input port block, facing in");
		check(repeaterWire.blockAt(new BlockPos(1, 1, 1)).orElseThrow()
				.equals(new StructureBlock.RedstoneDust())
			&& repeaterWire.blockAt(repeaterWire.outputs().getFirst().connectionBlock()).orElseThrow()
				.equals(new StructureBlock.RedstoneDust()),
			"repeater wire carries dust from center to output");
		check(repeaterWire.rotatedTo(EAST).equals(Wires.repeaterWire(WEST, EAST)),
			"repeater wire rotates like its plain counterpart");

		Structure bentRepeater = Wires.repeaterWire(NORTH, EAST);
		check(bentRepeater.contained()
			&& bentRepeater.blockAt(new BlockPos(1, 1, 0)).orElseThrow()
				.equals(new StructureBlock.Repeater(SOUTH, 1))
			&& bentRepeater.blockAt(new BlockPos(2, 1, 1)).orElseThrow()
				.equals(new StructureBlock.RedstoneDust()),
			"bent repeater wire: entrance repeater, dust turns the corner");
		expectThrow(() -> Wires.repeaterWire(NORTH, NORTH), "different faces", "degenerate repeater wire rejected");

		Structure via = Vias.upward(3, NORTH, EAST);
		check(via.size().equals(new Cell(1, 3, 1)) && !via.contained(), "upward via shape");
		check(via.inputs().equals(List.of(new StructurePin(new Cell(0, 0, 0), NORTH)))
			&& via.outputs().equals(List.of(new StructurePin(new Cell(0, 2, 0), EAST))),
			"upward via pins at bottom and top");
		check(via.blockAt(via.inputs().getFirst().connectionBlock()).orElseThrow()
				.equals(new StructureBlock.RedstoneDust())
			&& via.blockAt(via.outputs().getFirst().connectionBlock()).orElseThrow()
				.equals(new StructureBlock.RedstoneDust()),
			"via ports are dust");
		Structure down = Vias.downward(3, EAST, SOUTH);
		check(down.inputs().equals(List.of(new StructurePin(new Cell(0, 2, 0), EAST)))
			&& down.outputs().equals(List.of(new StructurePin(new Cell(0, 0, 0), SOUTH))),
			"downward via swaps the pin roles");
		expectThrow(() -> Vias.upward(1, NORTH, SOUTH), "at least 2 cells", "flat via rejected");

		check(repeaterCount(Vias.upward(2, Direction.NORTH, SOUTH)) == 0,
			"short via needs no internal refresh");
		Structure tallUp = Vias.upward(5, NORTH, SOUTH);
		check(repeaterCount(tallUp) > 0, "tall via refreshes itself with repeaters");

		Structure tallDown = Vias.downward(5, SOUTH, NORTH); // same staircase, signal flowing down
		boolean facingsFlip = true;
		for (var entry : tallUp.blocks().entrySet()) {
			StructureBlock downBlock = tallDown.blockAt(entry.getKey()).orElse(null);
			if (entry.getValue() instanceof StructureBlock.Repeater(Direction facing, int delay)) {
				if (!new StructureBlock.Repeater(facing.opposite(), delay).equals(downBlock))
					facingsFlip = false;
			} else if (!entry.getValue().equals(downBlock)) {
				facingsFlip = false;
			}
		}
		check(facingsFlip && tallUp.blocks().size() == tallDown.blocks().size(),
			"downward via is the same staircase with repeaters facing down-signal");

		int built = 0;
		for (int height = 2; height <= 6; height++) {
			for (Direction in : Direction.values()) {
				for (Direction out : Direction.values()) {
					Structure upVia = Vias.upward(height, in, out);
					Structure downVia = Vias.downward(height, in, out);
					if (!viaPortsAreDust(upVia) || !signalConnects(upVia)) {
						check(false, "via sweep: upward " + height + " " + in + " to " + out);
						return;
					}
					if (!viaPortsAreDust(downVia) || !signalConnects(downVia)) {
						check(false, "via sweep: downward " + height + " " + in + " to " + out);
						return;
					}
					built += 2;
				}
			}
		}
		check(built == 160, "via sweep: heights 2..6, all face pairs, both ways, all conduct");

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

	private static void signalStatsChecks() {
		check(Wires.wire(NORTH, SOUTH).signal().equals(new ax.xz.max.minesynth.structure.SignalStats(3, -3, 0)),
			"plain wire stats are exact (3, -3, 0)");
		check(Wires.repeaterWire(SOUTH, NORTH).signal()
				.equals(new ax.xz.max.minesynth.structure.SignalStats(1, 12, 1)),
			"repeater wire stats (1, 12, 1)");
		check(Gates.notGate().inputSignal() == 2 && Gates.notGate().outputSignal() == 14
			&& Gates.notGate().delayTicks() == 1, "NOT gate stats (2, 14, 1)");
		check(Gates.andGate().outputSignal() == 13 && Gates.andGate().delayTicks() == 2,
			"AND gate stats (13 out, 2 ticks)");
		check(Gates.orGate().outputSignal() == 11 && Gates.orGate().delayTicks() == 1,
			"OR gate stats (11 out, 1 tick)");

		Structure wire = Wires.wire(NORTH, EAST);
		check(wire.rotatedTo(EAST).signal().equals(wire.signal())
			&& wire.recolored(BlockColor.LIME).signal().equals(wire.signal()),
			"rotation and recoloring preserve stats");
		check(new Structure.Builder(new Cell(1, 1, 1)).placeBlock(1, 0, 1, WOOL).build().signal()
				.equals(ax.xz.max.minesynth.structure.SignalStats.WIRE_LIKE),
			"builder default stats are wire-like");
		expectThrow(() -> new ax.xz.max.minesynth.structure.SignalStats(1, 0, 0),
			"outputSignal", "zero output signal rejected");
		expectThrow(() -> new ax.xz.max.minesynth.structure.SignalStats(16, -3, 0),
			"inputSignal", "out-of-range input signal rejected");

		Structure pure = Vias.upward(2, NORTH, SOUTH);
		check(pure.outputSignal() < 0 && pure.inputSignal() == -pure.outputSignal() + 1
			&& pure.delayTicks() == 0, "pure via stats: input covers the dust run plus outward delivery");
		Structure tall = Vias.upward(5, NORTH, SOUTH);
		check(tall.delayTicks() == repeaterCount(tall) && tall.outputSignal() > 0,
			"tall via tallies repeater delay and emits an absolute output");
	}

	private static void junctionChecks() {
		Structure fork = Wires.simpleJunction(NORTH, EAST, SOUTH);
		check(fork.contained() && fork.inputs().size() == 1 && fork.outputs().size() == 2,
			"3-way junction shape");
		check(fork.blockAt(new StructurePin(new Cell(0, 0, 0), EAST).connectionBlock()).orElseThrow()
				.equals(new StructureBlock.RedstoneDust())
			&& fork.blockAt(new StructurePin(new Cell(0, 0, 0), SOUTH).connectionBlock()).orElseThrow()
				.equals(new StructureBlock.RedstoneDust()),
			"junction outputs carry dust");
		check(Wires.simpleJunction(NORTH, EAST, SOUTH, WEST).outputs().size() == 3, "4-way cross builds");
		check(Wires.wire(NORTH, EAST).equals(Wires.simpleJunction(NORTH, EAST)),
			"wire is the one-output junction");

		Structure repeaterFork = Wires.repeaterSimpleJunction(NORTH, EAST, WEST);
		check(repeaterFork.contained()
			&& repeaterFork.blockAt(repeaterFork.inputs().getFirst().connectionBlock()).orElseThrow()
				.equals(new StructureBlock.Repeater(SOUTH, 1)),
			"repeater junction has the entrance repeater");
		check(repeaterFork.signal().equals(new ax.xz.max.minesynth.structure.SignalStats(1, 12, 1)),
			"repeater junction stats");
		expectThrow(() -> Wires.simpleJunction(NORTH, EAST, EAST), "duplicate", "duplicate junction output rejected");
		expectThrow(() -> Wires.simpleJunction(NORTH), "1 to 3", "junction without outputs rejected");
	}

	private static void junctionViaChecks() {
		Structure up = Vias.upwardJunction(NORTH,
			List.of(new ax.xz.max.minesynth.structure.ViaTap(1, EAST),
				new ax.xz.max.minesynth.structure.ViaTap(3, WEST)));
		check(up.size().equals(new Cell(1, 4, 1)) && !up.contained(), "upward junction via shape");
		check(up.inputs().equals(List.of(new StructurePin(new Cell(0, 0, 0), NORTH)))
			&& up.outputs().equals(List.of(
				new StructurePin(new Cell(0, 1, 0), EAST),
				new StructurePin(new Cell(0, 3, 0), WEST))),
			"upward junction via pins");
		check(signalConnects(up, up.inputs().getFirst(), up.outputs().get(0))
			&& signalConnects(up, up.inputs().getFirst(), up.outputs().get(1)),
			"upward junction via conducts to every tap");

		Structure down = Vias.downwardJunction(3, SOUTH,
			List.of(new ax.xz.max.minesynth.structure.ViaTap(0, NORTH),
				new ax.xz.max.minesynth.structure.ViaTap(2, EAST)));
		check(down.inputs().equals(List.of(new StructurePin(new Cell(0, 3, 0), SOUTH)))
			&& down.outputs().size() == 2, "downward junction via pins");
		check(signalConnects(down, down.inputs().getFirst(), down.outputs().get(0))
			&& signalConnects(down, down.inputs().getFirst(), down.outputs().get(1)),
			"downward junction via conducts to every tap");

		int swept = 0;
		for (Direction in : Direction.values()) {
			for (Direction t1 : Direction.values()) {
				for (Direction t2 : Direction.values()) {
					Structure u = Vias.upwardJunction(in,
						List.of(new ax.xz.max.minesynth.structure.ViaTap(1, t1),
							new ax.xz.max.minesynth.structure.ViaTap(3, t2)));
					Structure d = Vias.downwardJunction(3, in,
						List.of(new ax.xz.max.minesynth.structure.ViaTap(0, t1),
							new ax.xz.max.minesynth.structure.ViaTap(2, t2)));
					for (Structure via : List.of(u, d)) {
						for (StructurePin output : via.outputs()) {
							if (!signalConnects(via, via.inputs().getFirst(), output)) {
								check(false, "junction via sweep " + in + " " + t1 + " " + t2);
								return;
							}
						}
					}
					swept += 2;
				}
			}
		}
		check(swept == 128, "junction via sweep: all face combinations conduct to all taps");
	}

	private static void placementRuleChecks() {
		var contained = PlacementRule.CONTAINED;
		var exposed = PlacementRule.EXPOSED;
		var noAbove = exposed.withAllowsAbove(false);

		check(Gates.andGate().placement().equals(noAbove), "AND gate forbids anything above it");
		check(Gates.orGate().placement().equals(exposed), "OR gate is exposed but open above");
		check(Gates.notGate().placement().equals(contained), "NOT gate is fully contained");

		var ABOVE = Adjacency.ABOVE;
		var NORTH_SIDE = Adjacency.NORTH;
		check(!noAbove.canNeighbor(contained, ABOVE),
			"nothing may sit directly above a no-above gate");
		check(exposed.canNeighbor(contained, ABOVE),
			"a contained wire may sit above an open-topped gate");
		check(noAbove.canNeighbor(contained, NORTH_SIDE),
			"a no-above gate still accepts a contained side neighbor");
		check(!exposed.canNeighbor(exposed, NORTH_SIDE),
			"two exposed structures may not be side-adjacent");

		// the live placement path: a wire directly above an AND gate is rejected
		expectThrow(() -> new Structure.Builder(new Cell(2, 2, 1))
				.place(Gates.andGate(), new Cell(0, 0, 0), NORTH, BlockColor.UNASSIGNED)
				.place(Wires.wire(NORTH, SOUTH), new Cell(0, 1, 0), NORTH, BlockColor.UNASSIGNED),
			"incompatible", "wire directly above an AND gate is rejected");
		Structure overNot = new Structure.Builder(new Cell(1, 2, 1))
			.horizontallyContained(false) // a parent re-validates sub-structure port dust against its own shell
			.place(Gates.notGate(), new Cell(0, 0, 0), NORTH, BlockColor.UNASSIGNED)
			.place(Wires.wire(NORTH, SOUTH), new Cell(0, 1, 0), NORTH, BlockColor.UNASSIGNED)
			.build();
		check(overNot.blocks().size() > Gates.notGate().blocks().size(),
			"a wire above an open-topped NOT gate is accepted");

		// validation catches a structure that pokes redstone through a shell it claims is clear
		expectThrow(() -> new Structure.Builder(new Cell(1, 1, 1))
				.placeBlock(1, 1, 1, StructureBlock.WOOL)
				.placeBlock(1, 2, 1, StructureBlock.REDSTONE_DUST).build(),
			"top shell", "dust on the top shell of an allows-above structure is rejected");
	}

	private static void schematicChecks() throws Exception {
		Map<BlockColor, Integer> expectedMappings = Map.of(
				BlockColor.WHITE, 0,
				BlockColor.LIME, 5,
				BlockColor.RED, 14,
				BlockColor.BLACK, 15);
		for (var entry : expectedMappings.entrySet()) {
			check(entry.getKey().ordinal() == entry.getValue(),
					"BlockColor order matches the legacy dye palette");
		}

		Path file = Path.of("out", "selftest.schematic");
		SchematicWriter.write(Wires.repeaterWire(SOUTH, NORTH).recolored(BlockColor.LIME), file);
		byte[] raw = Files.readAllBytes(file);
		check(raw.length > 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B,
			"schematic file is gzip compressed");

		byte[] nbt;
		try (var in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
			nbt = in.readAllBytes();
		}
		String payload = new String(nbt, StandardCharsets.ISO_8859_1);
		check(nbt[0] == 0x0A && payload.startsWith("Schematic", 3),
			"root compound is named Schematic");
		check(payload.contains("Width") && payload.contains("Height") && payload.contains("Length")
			&& payload.contains("Materials") && payload.contains("Alpha")
			&& payload.contains("Blocks") && payload.contains("Data"),
			"schematic carries the six required tags");
	}

	// ---- helpers ----

	private static int repeaterCount(Structure structure) {
		return (int) structure.blocks().values().stream()
			.filter(block -> block instanceof StructureBlock.Repeater)
			.count();
	}

	private static boolean viaPortsAreDust(Structure via) {
		return !via.contained()
			&& via.blockAt(via.inputs().getFirst().connectionBlock()).orElseThrow()
				.equals(new StructureBlock.RedstoneDust())
			&& via.blockAt(via.outputs().getFirst().connectionBlock()).orElseThrow()
				.equals(new StructureBlock.RedstoneDust());
	}

	/**
	 * Follows the signal from input port to output port through dust and
	 * repeaters using the in-game rules: dust connects at its own level and
	 * diagonally one block up or down, an opaque block directly above the
	 * lower dust cuts a diagonal, dust on glass does not transmit downward,
	 * and repeaters pass only from their back to their front.
	 */
	private static boolean signalConnects(Structure via) {
		return signalConnects(via, via.inputs().getFirst(), via.outputs().getFirst());
	}

	private static boolean signalConnects(Structure via, StructurePin from, StructurePin to) {
		var blocks = via.blocks();
		BlockPos start = from.connectionBlock();
		BlockPos goal = to.connectionBlock();
		var visited = new HashSet<BlockPos>();
		var queue = new ArrayDeque<BlockPos>();
		visited.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			if (at.equals(goal))
				return true;
			if (blocks.get(at) instanceof StructureBlock.Repeater(Direction facing, int delay)) {
				BlockPos front = at.offset(facing);
				if (blocks.get(front) instanceof StructureBlock.RedstoneDust && visited.add(front))
					queue.add(front);
				continue;
			}
			for (Direction direction : Direction.values()) {
				BlockPos level = at.offset(direction);
				if (blocks.get(level) instanceof StructureBlock.RedstoneDust && visited.add(level))
					queue.add(level);
				if (blocks.get(level) instanceof StructureBlock.Repeater(Direction facing, int delay)
						&& facing == direction && visited.add(level))
					queue.add(level);

				BlockPos up = level.above();
				boolean upCut = blocks.get(at.above()) instanceof StructureBlock.Wool;
				if (!upCut && blocks.get(up) instanceof StructureBlock.RedstoneDust && visited.add(up))
					queue.add(up);

				BlockPos down = level.below();
				boolean downCut = blocks.get(level) instanceof StructureBlock.Wool
					|| blocks.get(at.below()) instanceof StructureBlock.Glass;
				if (!downCut && blocks.get(down) instanceof StructureBlock.RedstoneDust && visited.add(down))
					queue.add(down);
			}
		}
		return false;
	}

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
