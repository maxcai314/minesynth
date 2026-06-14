package ax.xz.max.minesynth.pnr;

import ax.xz.max.minesynth.structure.Adjacency;
import ax.xz.max.minesynth.structure.BlockColor;
import ax.xz.max.minesynth.structure.Cell;
import ax.xz.max.minesynth.structure.Direction;
import ax.xz.max.minesynth.structure.PlacementRule;
import ax.xz.max.minesynth.structure.SignalStats;
import ax.xz.max.minesynth.structure.Structure;
import ax.xz.max.minesynth.structure.StructurePin;
import ax.xz.max.minesynth.structure.Vias;
import ax.xz.max.minesynth.structure.Wires;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Baseline router using the layer-per-net strategy: apart from a trivial
 * directly-facing case and short ground pigtails that bring pins to their via
 * columns, nothing routes at ground level. Each net climbs an upward via,
 * runs across one 2D routing layer (tried iteratively from y=1 upward), and
 * descends through downward vias to its sinks. If a net cannot be realized on
 * one layer it is torn off and retried one layer higher; when no layer works
 * the router throws.
 *
 * <p>Wire cells are {@link Wires#simpleJunction} pieces (gaining branch faces
 * where fanout attaches) and become {@link Wires#repeaterSimpleJunction}
 * where the tracked strength runs low (repair works on bends and forks too).
 * Strength bookkeeping follows the
 * {@link SignalStats} contract; board inputs are assumed to arrive at
 * {@link #BOARD_INPUT_STRENGTH}. Every net gets its own wire color.
 */
public final class NaiveRouter implements Router {
	/** Default conservative strength assumed at board input ports. */
	public static final int BOARD_INPUT_STRENGTH = 14;
	/** Maximum ground-level cells between a pin and its via column. */
	public static final int PIGTAIL_LIMIT = 4;

	private static final List<BlockColor> NET_PALETTE = List.of(
		BlockColor.RED, BlockColor.BLUE, BlockColor.GREEN, BlockColor.BROWN,
		BlockColor.WHITE, BlockColor.BLACK, BlockColor.GRAY, BlockColor.LIGHT_GRAY);

	private final int boardInputStrength;
	private final int boardOutputRequirement;

	public NaiveRouter() {
		this(BOARD_INPUT_STRENGTH, 1);
	}

	/**
	 * A router with explicit strength assumptions, for boards that will be
	 * nested as components: {@code boardInputStrength} is what the board's
	 * input port blocks are assumed to receive (the future component's
	 * inputSignal), and {@code boardOutputRequirement} is the strength every
	 * output port must deliver to its neighbor (the future component's
	 * outputSignal).
	 */
	public NaiveRouter(int boardInputStrength, int boardOutputRequirement) {
		this.boardInputStrength = boardInputStrength;
		this.boardOutputRequirement = boardOutputRequirement;
	}

	@Override
	public Structure route(Placement placement) throws RoutingException {
		return new Job(placement, boardInputStrength, boardOutputRequirement).run();
	}

	/** Signals that the current layer attempt cannot work; the net retries higher. */
	private static final class LayerFailure extends Exception {
		LayerFailure(String reason) {
			super(reason, null, false, false);
		}
	}

	private record Claim(PlacementRule rule) {}

	private record PendingPiece(Structure structure, Cell at, BlockColor color) {}

	/** One routed wire cell being assembled: faces, strength, and piece kind. */
	private static final class WireCell {
		final Cell cell;
		final Direction in;
		final LinkedHashSet<Direction> outs = new LinkedHashSet<>();
		int arriving = -1;
		boolean repeater;

		WireCell(Cell cell, Direction in) {
			this.cell = cell;
			this.in = in;
		}

		int output() {
			return repeater ? 12 : arriving - 3;
		}
	}

	/** An up or down via column whose top face is fixed once the layer path is known. */
	private static final class ViaColumn {
		final Cell groundCell;
		final int layer;
		final Direction bottomFace;
		final boolean upward;
		Direction topFace;
		Structure structure;

		ViaColumn(Cell groundCell, int layer, Direction bottomFace, boolean upward) {
			this.groundCell = groundCell;
			this.layer = layer;
			this.bottomFace = bottomFace;
			this.upward = upward;
		}

		Cell topCell() {
			return groundCell.atHeight(layer);
		}

		void buildStructure() {
			structure = upward
				? Vias.upward(layer + 1, bottomFace, topFace)
				: Vias.downward(layer + 1, topFace, bottomFace);
		}

		int resolveOutput(int arriving) {
			return structure.signal().resolveOutput(arriving);
		}
	}

	/** Where a chain meets a pin: the first routable cell and the face toward the pin side. */
	private record Terminal(Cell cell, Direction entryFace) {}

	private static final class Job {
		final Placement placement;
		final Cell size;
		final int inputStrength;
		final int outputRequirement;
		final Map<Cell, Claim> claims = new HashMap<>();
		final Map<Cell, PendingPiece> pieces = new LinkedHashMap<>();

		// per-attempt transaction state
		final List<Cell> txClaims = new ArrayList<>();
		final Map<Cell, WireCell> txWires = new LinkedHashMap<>();
		final List<ViaColumn> txVias = new ArrayList<>();

		Job(Placement placement, int inputStrength, int outputRequirement) {
			this.placement = placement;
			this.size = placement.design().floorplan().size();
			this.inputStrength = inputStrength;
			this.outputRequirement = outputRequirement;
		}

		Structure run() throws RoutingException {
			placement.placements().forEach((name, placed) -> {
				for (Cell cell : placed.occupiedCells())
					claims.put(cell, new Claim(placed.structure().placement()));
			});

			int colorIndex = 0;
			for (Net net : placement.design().nets())
				routeNet(net, NET_PALETTE.get(colorIndex++ % NET_PALETTE.size()));

			return assemble();
		}

		// ---- per-net routing ----

		void routeNet(Net net, BlockColor color) throws RoutingException {
			StructurePin source = placement.sourceLocation(net.source());
			int sourceStrength = sourceStrength(net);

			if (net.sinks().size() == 1) {
				StructurePin sink = placement.sinkLocation(net.sinks().getFirst());
				boolean facing = source.cell().plus(source.face(), 1).equals(sink.cell())
					&& sink.face() == source.face().opposite();
				if (facing) {
					if (sourceStrength < requiredAt(net.sinks().getFirst()))
						throw new RoutingException("net " + net.name()
							+ " cannot satisfy its sink even though the pins face each other");
					return; // directly facing pins connect with zero pieces
				}
			}

			String lastFailure = "no routing layers available";
			for (int layer = 1; layer < size.y(); layer++) {
				txClaims.clear();
				txWires.clear();
				txVias.clear();
				try {
					routeOnLayer(net, sourceStrength, layer);
					commit(color);
					return;
				} catch (LayerFailure failure) {
					lastFailure = "layer " + layer + ": " + failure.getMessage();
					rollback();
				}
			}
			throw new RoutingException("net " + net.name() + " could not be routed on any layer of "
				+ size + "; " + lastFailure);
		}

		void routeOnLayer(Net net, int sourceStrength, int layer) throws LayerFailure {
			// source side: pigtail at ground level, then the upward via
			Terminal sourceTerminal = terminal(placement.sourceLocation(net.source()),
				net.source() instanceof NetEnd.Port);
			List<WireCell> sourcePigtail = new ArrayList<>();
			ViaColumn sourceVia = placeTerminalVia(sourceTerminal, layer, true, sourcePigtail);

			boolean sourceChainWalked = false;
			for (NetEnd sinkEnd : net.sinks()) {
				Terminal sinkTerminal = terminal(placement.sinkLocation(sinkEnd),
					sinkEnd instanceof NetEnd.Port);
				List<WireCell> sinkPigtail = new ArrayList<>();
				ViaColumn sinkVia = placeTerminalVia(sinkTerminal, layer, false, sinkPigtail);

				List<Cell> starts = sourceChainWalked ? branchSeeds(layer) : List.of(sourceVia.topCell());
				if (starts.isEmpty())
					throw new LayerFailure("no branch seeds left for fanout to " + sinkEnd);
				List<Cell> path = bfsOnLayer(starts, sinkVia.topCell(), layer);
				if (path == null)
					throw new LayerFailure("no path to sink " + sinkEnd);

				// lay the new layer wire cells and fix the via faces
				List<WireCell> newCells = new ArrayList<>();
				for (int i = 1; i < path.size() - 1; i++) {
					Direction in = Direction.between(path.get(i), path.get(i - 1));
					WireCell cell = new WireCell(path.get(i), in);
					if (i + 1 < path.size())
						cell.outs.add(Direction.between(path.get(i), path.get(i + 1)));
					claimWire(path.get(i));
					txWires.put(path.get(i), cell);
					newCells.add(cell);
				}
				sinkVia.topFace = Direction.between(sinkVia.topCell(), path.get(path.size() - 2));

				sinkVia.buildStructure();
				List<ChainSegment> chain = new ArrayList<>();
				int entering;
				if (!sourceChainWalked) {
					sourceVia.topFace = Direction.between(sourceVia.topCell(), path.get(1));
					sourceVia.buildStructure();
					chain.add(new ChainSegment(sourcePigtail, sourceVia));
					entering = sourceStrength;
					sourceChainWalked = true;
				} else {
					WireCell seed = txWires.get(path.getFirst());
					seed.outs.add(Direction.between(seed.cell, path.get(1)));
					entering = seed.output();
				}
				chain.add(new ChainSegment(newCells, sinkVia));
				chain.add(new ChainSegment(sinkPigtail, null));
				walkRepaired(entering, chain, requiredAt(sinkEnd));
			}
		}

		/** Wire cells followed by an optional via, in signal order. */
		private record ChainSegment(List<WireCell> wires, ViaColumn via) {}

		/**
		 * Walks the chain assigning strengths; on a shortfall, converts the
		 * latest cell that can actually drive a repeater (arriving >= 1) at or
		 * before the failure point, and walks again, until the sink requirement
		 * is met or no eligible cell remains. The fix should land on the last live
		 * cell upstream (which alone suffices) rather than on the dead cell.
		 */
		void walkRepaired(int entering, List<ChainSegment> chain, int required) throws LayerFailure {
			while (true) {
				WalkOutcome outcome = tryWalk(entering, chain);
				if (outcome.failedAt() < 0 && outcome.delivered() >= required)
					return;
				int limit = outcome.failedAt() < 0 ? Integer.MAX_VALUE : outcome.failedAt();
				if (!convertLatestBefore(chain, limit))
					throw new LayerFailure("strength repair exhausted (delivered " + outcome.delivered()
						+ ", required " + required + ")");
			}
		}

		/** Delivered strength on success (failedAt -1), or the failing wire-cell index. */
		private record WalkOutcome(int delivered, int failedAt) {}

		WalkOutcome tryWalk(int entering, List<ChainSegment> chain) {
			int strength = entering;
			int index = 0;
			for (ChainSegment segment : chain) {
				for (WireCell cell : segment.wires()) {
					cell.arriving = strength;
					if (strength < (cell.repeater ? 1 : 3))
						return new WalkOutcome(strength, index);
					index++;
					strength = cell.output();
				}
				if (segment.via() != null) {
					if (strength < segment.via().structure.inputSignal())
						return new WalkOutcome(strength, index);
					strength = segment.via().resolveOutput(strength);
				}
			}
			return new WalkOutcome(strength, -1);
		}

		/**
		 * Converts the latest non-repeater cell with flat index at most
		 * {@code limit} that has enough input to drive a repeater
		 * ({@code arriving >= 1}); false if none. Picking the latest such cell
		 * is both optimal (covers the most wire before it) and the fix for the
		 * doubled-repeater bug.
		 */
		boolean convertLatestBefore(List<ChainSegment> chain, int limit) {
			WireCell best = null;
			int index = 0;
			for (ChainSegment segment : chain) {
				for (WireCell cell : segment.wires()) {
					if (index > limit)
						break;
					if (!cell.repeater && cell.arriving >= 1)
						best = cell;
					index++;
				}
			}
			if (best == null)
				return false;
			best.repeater = true;
			return true;
		}

		// ---- terminals, pigtails, vias ----

		/** Board ports route from their own cell outward; component pins from the faced cell. */
		Terminal terminal(StructurePin pin, boolean boardPort) {
			return boardPort
				? new Terminal(pin.cell(), pin.face())
				: new Terminal(pin.cell().plus(pin.face(), 1), pin.face().opposite());
		}

		/**
		 * Finds the nearest ground cell within {@link #PIGTAIL_LIMIT} of the
		 * terminal where a via column up to {@code layer} fits, claims the
		 * column and the pigtail wire cells leading to it, and returns the
		 * via. Pigtail cells are ordered pin side first.
		 */
		ViaColumn placeTerminalVia(Terminal terminal, int layer, boolean upward, List<WireCell> pigtail)
				throws LayerFailure {
			Map<Cell, Cell> cameFrom = new HashMap<>();
			ArrayDeque<Cell> queue = new ArrayDeque<>();
			Map<Cell, Integer> depth = new HashMap<>();
			if (!availableForPiece(terminal.cell(), PlacementRule.CONTAINED))
				throw new LayerFailure("terminal cell " + terminal.cell() + " is occupied");
			queue.add(terminal.cell());
			depth.put(terminal.cell(), 0);

			while (!queue.isEmpty()) {
				Cell at = queue.poll();
				if (viaColumnFits(at, layer)) {
					List<Cell> pinToVia = tracePath(cameFrom, terminal.cell(), at);
					layPigtail(terminal, pinToVia, upward, pigtail);
					Direction bottomFace = pinToVia.size() == 1
						? terminal.entryFace()
						: Direction.between(at, pinToVia.get(pinToVia.size() - 2));
					claimViaColumn(at, layer);
					ViaColumn via = new ViaColumn(at, layer, bottomFace, upward);
					txVias.add(via);
					return via;
				}
				int d = depth.get(at);
				if (d >= PIGTAIL_LIMIT)
					continue;
				for (Direction direction : Direction.values()) {
					Cell next = at.plus(direction, 1);
					if (next.y() == 0 && availableForPiece(next, PlacementRule.CONTAINED) && !depth.containsKey(next)) {
						depth.put(next, d + 1);
						cameFrom.put(next, at);
						queue.add(next);
					}
				}
			}
			throw new LayerFailure("no via spot within " + PIGTAIL_LIMIT + " cells of " + terminal.cell());
		}

		/** Lays the pigtail wire cells between pin and via (everything except the via cell). */
		void layPigtail(Terminal terminal, List<Cell> pinToVia, boolean sourceSide, List<WireCell> pigtail)
				throws LayerFailure {
			List<Cell> wireCells = pinToVia.subList(0, pinToVia.size() - 1);
			for (int i = 0; i < wireCells.size(); i++) {
				Cell at = wireCells.get(i);
				Direction pinSide = i == 0
					? terminal.entryFace()
					: Direction.between(at, wireCells.get(i - 1));
				Direction viaSide = Direction.between(at, pinToVia.get(i + 1));
				// signal flows pin->via on the source side and via->pin on the sink side
				WireCell cell = sourceSide ? new WireCell(at, pinSide) : new WireCell(at, viaSide);
				cell.outs.add(sourceSide ? viaSide : pinSide);
				claimWire(at);
				txWires.put(at, cell);
				pigtail.add(cell);
			}
			if (!sourceSide)
				java.util.Collections.reverse(pigtail); // walk order: via side first
		}

		boolean viaColumnFits(Cell ground, int layer) {
			for (int y = 0; y <= layer; y++)
				if (!availableForPiece(ground.atHeight(y), PlacementRule.EXPOSED))
					return false;
			return true;
		}

		/**
		 * Whether a piece with rule {@code rule} can occupy {@code cell}: it
		 * must be free and every claimed face-neighbor must tolerate it (this is
		 * what keeps a wire out of the cell directly above a no-above gate).
		 */
		boolean availableForPiece(Cell cell, PlacementRule rule) {
			if (!isFree(cell))
				return false;
			for (Adjacency side : Adjacency.values()) {
				Claim other = claims.get(side.neighbor(cell));
				if (other != null && !rule.canNeighbor(other.rule(), side))
					return false;
			}
			return true;
		}

		void claimViaColumn(Cell ground, int layer) {
			for (int y = 0; y <= layer; y++) {
				Cell cell = ground.atHeight(y);
				claims.put(cell, new Claim(PlacementRule.EXPOSED));
				txClaims.add(cell);
			}
		}

		void claimWire(Cell cell) {
			claims.put(cell, new Claim(PlacementRule.CONTAINED));
			txClaims.add(cell);
		}

		// ---- layer pathfinding ----

		List<Cell> bfsOnLayer(List<Cell> starts, Cell target, int layer) {
			Map<Cell, Cell> cameFrom = new HashMap<>();
			ArrayDeque<Cell> queue = new ArrayDeque<>();
			for (Cell start : starts) {
				cameFrom.put(start, start);
				queue.add(start);
			}
			while (!queue.isEmpty()) {
				Cell at = queue.poll();
				if (at.equals(target)) {
					Cell root = at;
					while (!cameFrom.get(root).equals(root))
						root = cameFrom.get(root);
					return tracePath(cameFrom, root, target);
				}
				for (Direction direction : Direction.values()) {
					Cell next = at.plus(direction, 1);
					if (cameFrom.containsKey(next) || next.y() != layer)
						continue;
					if (!next.equals(target) && !availableForPiece(next, PlacementRule.CONTAINED))
						continue;
					cameFrom.put(next, at);
					queue.add(next);
				}
			}
			return null;
		}

		/** Layer cells of this net that may grow a fanout branch (face budget allowing). */
		List<Cell> branchSeeds(int layer) {
			List<Cell> seeds = new ArrayList<>();
			txWires.forEach((cell, wire) -> {
				if (cell.y() == layer && wire.outs.size() < 3)
					seeds.add(cell);
			});
			return seeds;
		}

		// ---- commit / rollback / assembly ----

		void commit(BlockColor color) {
			for (var entry : txWires.entrySet()) {
				WireCell wire = entry.getValue();
				Direction[] outs = wire.outs.toArray(new Direction[0]);
				Structure piece = wire.repeater
					? Wires.repeaterSimpleJunction(wire.in, outs)
					: Wires.simpleJunction(wire.in, outs);
				pieces.put(entry.getKey(), new PendingPiece(piece, entry.getKey(), color));
			}
			for (ViaColumn via : txVias)
				pieces.put(via.groundCell, new PendingPiece(via.structure,
					new Cell(via.groundCell.x(), 0, via.groundCell.z()), color));
			txClaims.clear();
			txWires.clear();
			txVias.clear();
		}

		void rollback() {
			for (Cell cell : txClaims)
				claims.remove(cell);
			txClaims.clear();
			txWires.clear();
			txVias.clear();
		}

		Structure assemble() throws RoutingException {
			Floorplan floorplan = placement.design().floorplan();
			Structure.Builder board = new Structure.Builder(size).horizontallyContained(false);
			try {
				placement.placements().values().forEach(board::place);
				for (PendingPiece piece : pieces.values())
					board.place(piece.structure(), piece.at(), Direction.NORTH, piece.color());
				board.addInputs(List.copyOf(floorplan.inputPorts().values()));
				board.addOutputs(List.copyOf(floorplan.outputPorts().values()));
				return board.build();
			} catch (IllegalArgumentException e) {
				throw new RoutingException("router produced an invalid board: " + e.getMessage());
			}
		}

		// ---- small helpers ----

		int sourceStrength(Net net) throws RoutingException {
			SignalStats stats = placement.statsOf(net.source());
			if (stats == null)
				return inputStrength;
			int strength = stats.outputSignal() > 0
				? stats.outputSignal()
				: stats.inputSignal() + stats.outputSignal();
			if (strength < 1)
				throw new RoutingException("net " + net.name() + " source " + net.source()
					+ " cannot guarantee any output strength (" + stats + ")");
			return strength;
		}

		int requiredAt(NetEnd sink) {
			SignalStats stats = placement.statsOf(sink);
			return stats == null ? outputRequirement : stats.inputSignal();
		}

		boolean isFree(Cell cell) {
			return cell.x() >= 0 && cell.x() < size.x()
				&& cell.y() >= 0 && cell.y() < size.y()
				&& cell.z() >= 0 && cell.z() < size.z()
				&& !claims.containsKey(cell);
		}

		static List<Cell> tracePath(Map<Cell, Cell> cameFrom, Cell start, Cell end) {
			List<Cell> path = new ArrayList<>();
			Cell at = end;
			while (!at.equals(start)) {
				path.add(at);
				at = cameFrom.get(at);
			}
			path.add(start);
			java.util.Collections.reverse(path);
			return path;
		}

	}
}
