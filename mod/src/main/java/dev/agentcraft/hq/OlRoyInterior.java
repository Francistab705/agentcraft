package dev.agentcraft.hq;

import dev.agentcraft.block.DecisionPodiumBlock;
import dev.agentcraft.block.FacingEntityBlock;
import dev.agentcraft.block.LampStatus;
import dev.agentcraft.block.MergeStationBlock;
import dev.agentcraft.block.ModBlocks;
import dev.agentcraft.block.MonitorBlock;
import dev.agentcraft.block.PanelBlock;
import dev.agentcraft.block.StatusLampBlock;
import dev.agentcraft.layout.AnchorNames;
import dev.agentcraft.layout.Anchors;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * SBC HQ ground floor inside Ol'Roy: "Studio + Garden Court" (sbc-hq/mockups.html, option C) with
 * a game corner next to the café. West wing: lobby, meeting room, desks, workshop, library and
 * mission hall. East wing: garden court, lounge, café and kitchen, and the game corner.
 *
 * <p>The west wing is the working studio: it places AgentCraft's own station blocks and writes the
 * anchors that {@link AnchorNames} requires. Coordinates are footprint cells (cx east, cy north from
 * the south-west corner); dy is the height above the floor plate, so dy 1 is where feet stand.
 * "South" is +z in the world, which is toward smaller cy.
 */
final class OlRoyInterior {
	private final Plan p;
	private final boolean[][] in;
	private final Anchors.Builder a;

	OlRoyInterior(Plan p, boolean[][] in, Anchors.Builder a) {
		this.p = p;
		this.in = in;
		this.a = a;
	}

	private static final int G = OlRoyBuilder.GROUND;
	/** Feet height of anyone standing on the floor plate. */
	private static final int FEET = G + 1;

	private static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState wool(DyeColor c) {
		return b(Blocks.WOOL.pick(c));
	}

	private static BlockState concrete(DyeColor c) {
		return b(Blocks.CONCRETE.pick(c));
	}

	/** A stair whose sitter looks toward {@code dir} (the back of the stair is on the opposite side). */
	private static BlockState seat(Block stairs, Direction dir) {
		return stairs.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, dir.getOpposite());
	}

	private static double wx(double cx) {
		return OlRoyBuilder.OX + cx;
	}

	/** World z of the south edge... the minimum z of footprint row cy (rows grow northward, so z shrinks). */
	private static int rz(int cy) {
		return OlRoyBuilder.rowZ(cy);
	}

	private boolean ok(int cx, int cy) {
		return OlRoyBuilder.deep(in, cx, cy, 1);
	}

	private void put(int cx, int dy, int cy, BlockState s) {
		if (ok(cx, cy)) {
			p.set(OlRoyBuilder.OX + cx, G + dy, rz(cy), s);
		}
	}

	private void floor(int cx0, int cy0, int cx1, int cy1, BlockState s) {
		for (int cy = cy0; cy <= cy1; cy++) {
			for (int cx = cx0; cx <= cx1; cx++) {
				put(cx, 0, cy, s);
			}
		}
	}

	private void box(int cx0, int cy0, int cx1, int cy1, int dy0, int dy1, BlockState s) {
		for (int dy = dy0; dy <= dy1; dy++) {
			for (int cy = cy0; cy <= cy1; cy++) {
				for (int cx = cx0; cx <= cx1; cx++) {
					put(cx, dy, cy, s);
				}
			}
		}
	}

	private static BlockState lamp(LampStatus status) {
		return ModBlocks.STATUS_LAMP.defaultBlockState().setValue(StatusLampBlock.STATUS, status);
	}

	private void bind(int cx, int dy, int cy, String binding) {
		if (ok(cx, cy)) {
			p.bind(OlRoyBuilder.OX + cx, G + dy, rz(cy), binding);
		}
	}

	void build() {
		lobby();
		meeting();
		deskPod();
		workshop();
		missionHall();
		usageBoard();
		library();
		gardenCourt();
		lounge();
		cafe();
		gameCorner();
		a.bounds(OlRoyBuilder.OX + 1, G, OlRoyBuilder.OZ + 1, OlRoyBuilder.OX + 92, G + 10, OlRoyBuilder.OZ + 52);
		a.spot(AnchorNames.ENTRANCE, OlRoyBuilder.OX + 25, FEET, rz(1), 180);
		a.put(AnchorNames.SPAWN, wx(25.5), FEET, rz(2) + 0.5, 180, 0);
	}

	// ------------------------------------------------------------------ west wing

	private void lobby() {
		floor(14, 1, 36, 16, OlRoyBuilder.PARQUET());
		BlockState desk = b(Blocks.DARK_OAK_PLANKS);
		BlockState top = b(Blocks.DARK_OAK_SLAB);
		for (int cx = 19; cx <= 31; cx++) {
			if (cx >= 24 && cx <= 26) {
				continue; // the straight path from the door
			}
			put(cx, 1, 9, desk);
			put(cx, 2, 9, top);
		}
		// planters either side of the door: moss, ferns and a few flowers
		for (int side = 0; side < 2; side++) {
			int x0 = side == 0 ? 17 : 29;
			box(x0, 2, x0 + 3, 3, 1, 1, b(Blocks.MOSS_BLOCK));
			for (int cx = x0; cx <= x0 + 3; cx++) {
				put(cx, 2, 2, b(Blocks.FERN));
				put(cx, 2, 3, (cx + side) % 2 == 0 ? b(Blocks.CORNFLOWER) : b(Blocks.SHORT_GRASS));
			}
		}
	}

	private void meeting() {
		floor(39, 3, 47, 11, OlRoyBuilder.PARQUET());
		BlockState glass = b(Blocks.GLASS_PANE);
		box(38, 2, 38, 12, 1, 3, glass);
		for (int cx = 38; cx <= 48; cx++) {
			if (cx < 42 || cx > 43) {
				box(cx, 12, cx, 12, 1, 3, glass); // door gap at 42-43
			}
		}
		box(41, 6, 45, 7, 1, 1, b(Blocks.DARK_OAK_SLAB));
		int n = 1;
		for (int cx = 41; cx <= 45; cx += 2) {
			put(cx, 1, 8, seat(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
			a.put(AnchorNames.slot(AnchorNames.MEETING, n++), wx(cx + 0.5), FEET, rz(8) + 0.5, 0, 0);
		}
		for (int cx = 41; cx <= 45; cx += 2) {
			put(cx, 1, 5, seat(Blocks.SPRUCE_STAIRS, Direction.NORTH));
			a.put(AnchorNames.slot(AnchorNames.MEETING, n++), wx(cx + 0.5), FEET, rz(5) + 0.5, 180, 0);
		}
		put(43, 2, 6, b(Blocks.LANTERN));
	}

	/** Six agent desks in two rows with monitors facing north, plus a larger L-shaped desk for the user. */
	private void deskPod() {
		floor(3, 15, 26, 26, b(Blocks.SPRUCE_PLANKS));
		BlockState top = St.slab(Blocks.DARK_OAK_SLAB, true);
		List<String> ids = StudioHqBuilder.deskIds();
		int i = 0;
		for (int row = 0; row < 2; row++) {
			int cy = row == 0 ? 22 : 17;
			for (int d = 0; d < 3 && i < ids.size(); d++, i++) {
				int cx = 5 + d * 5;
				String id = ids.get(i);
				for (int x = cx; x <= cx + 2; x++) {
					put(x, 1, cy, top);
					for (int dy = 2; dy <= 3; dy++) {
						put(x, dy, cy, ModBlocks.MONITOR.defaultBlockState().setValue(PanelBlock.FACING, Direction.NORTH).setValue(MonitorBlock.LIT, true));
						bind(x, dy, cy, id);
					}
				}
				put(cx + 1, 4, cy, lamp(LampStatus.IDLE));
				bind(cx + 1, 4, cy, "agent:" + id);
				// Screens face north, toward the task board and the hall, so the player walking up from
				// there reads each screen over the agent's shoulder. The agent sits on the chair north of
				// the desk (desk_<id>), looking south at the monitor.
				put(cx + 1, 1, cy + 1, seat(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
				double sx = wx(cx + 1.5);
				double sz = rz(cy) - 0.5;
				a.put(AnchorNames.desk(id), sx, FEET, sz, 0, 0);
				a.put("seat_" + id, sx, FEET, sz, 0, 0);
				a.put(AnchorNames.monitor(id), sx, FEET + 2.0, rz(cy) + 0.75 - 0.002, 180, 0);
				a.cameraLookAt("desk_" + id, sx - 2.0, FEET + 2.2, rz(cy) - 3.8, sx + 0.3, FEET + 1.8, rz(cy) + 0.75);
				put(cx + 3, 2, cy, b(Blocks.POTTED_FERN));
			}
		}
		// the user's desk: an L, with the chair tucked into the corner
		box(21, 22, 24, 22, 1, 1, top);
		box(24, 19, 24, 21, 1, 1, top);
		put(22, 1, 21, seat(Blocks.SPRUCE_STAIRS, Direction.NORTH));
		put(21, 2, 22, b(Blocks.LANTERN));
		// low shelf between the desks and the workshop, with a gap to walk through
		for (int cy = 16; cy <= 25; cy++) {
			if (cy < 20 || cy > 21) {
				put(27, 1, cy, b(Blocks.BOOKSHELF));
				put(27, 2, cy, b(Blocks.BOOKSHELF));
			}
		}
	}

	/** Terminals, the test bench and the merge station as three counters along the workshop's north side. */
	private void workshop() {
		floor(29, 16, 47, 25, b(Blocks.POLISHED_ANDESITE));
		BlockState bench = St.slab(Blocks.DARK_OAK_SLAB, true);
		BlockState terminal = ModBlocks.CONSOLE_TERMINAL.defaultBlockState().setValue(FacingEntityBlock.FACING, Direction.SOUTH);
		int cy = 24;
		double sz = rz(cy) + 1.4;
		// terminals: two, with benches between them
		for (int cx = 30; cx <= 34; cx++) {
			put(cx, 1, cy, cx % 2 == 1 ? terminal : bench);
		}
		put(32, 2, cy, b(Blocks.POTTED_FERN));
		a.put(AnchorNames.slot(AnchorNames.TERMINAL, 1), wx(31.5), FEET, sz, 180, 0);
		a.put(AnchorNames.slot(AnchorNames.TERMINAL, 2), wx(33.5), FEET, sz, 180, 0);
		// test bench: a terminal with three CI lamps and lab glassware
		for (int cx = 36; cx <= 40; cx++) {
			put(cx, 1, cy, bench);
		}
		put(38, 2, cy, terminal);
		put(36, 2, cy, b(Blocks.BREWING_STAND));
		put(40, 2, cy, b(Blocks.LANTERN));
		int[] lampX = {37, 38, 39};
		int[] lampDy = {2, 3, 2};
		for (int k = 0; k < 3; k++) {
			put(lampX[k], lampDy[k], cy, lamp(LampStatus.IDLE));
			bind(lampX[k], lampDy[k], cy, "ci:#" + (k + 1));
		}
		a.put(AnchorNames.slot(AnchorNames.TESTBENCH, 1), wx(37.5), FEET, sz, 180, 0);
		a.put(AnchorNames.slot(AnchorNames.TESTBENCH, 2), wx(39.5), FEET, sz, 180, 0);
		a.put(AnchorNames.slot(AnchorNames.TESTBENCH, 3), wx(38.5), FEET, sz + 1.0, 180, 0);
		// merge station: three worktop blocks with a `merge` lamp above
		for (int cx = 42; cx <= 46; cx++) {
			put(cx, 1, cy, bench);
		}
		for (int cx = 43; cx <= 45; cx++) {
			put(cx, 1, cy, ModBlocks.MERGE_STATION.defaultBlockState().setValue(FacingEntityBlock.FACING, Direction.SOUTH)
				.setValue(MergeStationBlock.ACTIVE, false));
		}
		put(44, 2, cy, lamp(LampStatus.OFF));
		bind(44, 2, cy, "merge");
		a.put(AnchorNames.slot(AnchorNames.MERGESTATION, 1), wx(43.5), FEET, sz, 180, 0);
		a.put(AnchorNames.slot(AnchorNames.MERGESTATION, 2), wx(45.5), FEET, sz, 180, 0);
	}

	/** Goal atrium medallion with its lamp, the decision podium on its dais, and the task wall. */
	private void missionHall() {
		floor(29, 30, 47, 51, OlRoyBuilder.PARQUET());
		int mx = 38;
		int my = 41;
		for (int cy = my - 5; cy <= my + 5; cy++) {
			for (int cx = mx - 5; cx <= mx + 5; cx++) {
				double r = Math.hypot(cx - mx, cy - my);
				if (r <= 5.2) {
					put(cx, 0, cy, wool(r <= 2.2 ? DyeColor.YELLOW : r <= 4.2 ? DyeColor.WHITE : DyeColor.BLUE));
				}
			}
		}
		put(mx, 1, my, lamp(LampStatus.IDLE));
		bind(mx, 1, my, "goal:atrium");
		a.put(AnchorNames.GOAL_ATRIUM, wx(mx + 0.5), FEET, rz(my) + 0.5, 180, 0);

		// decision podium on a small dais at the hall's south edge, facing the entrance side
		floor(36, 31, 40, 33, b(Blocks.POLISHED_ANDESITE));
		put(38, 1, 33, ModBlocks.DECISION_PODIUM.defaultBlockState().setValue(FacingEntityBlock.FACING, Direction.SOUTH)
			.setValue(DecisionPodiumBlock.OPEN, false));
		a.put(AnchorNames.DECISION_PODIUM, wx(38.5), FEET + 0.95, rz(33) + 0.5, 0, 0);
		// a walnut niche behind it with the `decisions` lamp
		box(37, 34, 39, 34, 1, 4, ModBlocks.WALNUT_PANEL.defaultBlockState());
		put(38, 3, 34, lamp(LampStatus.OFF));
		bind(38, 3, 34, "decisions");
		double ux = wx(38.5);
		double uz = rz(33) + 1.5;
		a.put("podium_user", ux, FEET, uz, 180, 0);
		double[][] userSlots = {{ux - 2.2, uz + 0.6}, {ux + 2.2, uz + 0.6}, {ux, uz + 2.2}};
		for (int k = 0; k < userSlots.length; k++) {
			double dx = ux - userSlots[k][0];
			double dz = uz - userSlots[k][1];
			a.put(AnchorNames.slot(AnchorNames.USER, k + 1), userSlots[k][0], FEET, userSlots[k][1],
				(float) Math.toDegrees(Math.atan2(-dx, dz)), 0);
		}

		// the north wall holds two boards side by side, 8 blocks each: the task board (Todo / Doing / Review /
		// Done) and, to its right, the summary board that reads out what was done in plain English
		box(29, 50, 47, 50, 1, 4, b(Blocks.DEEPSLATE_TILES));
		BlockState summary = ModBlocks.MONITOR.defaultBlockState().setValue(PanelBlock.FACING, Direction.SOUTH).setValue(MonitorBlock.LIT, true);
		for (int cx = 31; cx <= 46; cx++) {
			put(cx, 1, 50, ModBlocks.WALNUT_TRIM.defaultBlockState());
			for (int dy = 2; dy <= 4; dy++) {
				if (cx <= 38) {
					put(cx, dy, 50, ModBlocks.TASK_BOARD.defaultBlockState().setValue(PanelBlock.FACING, Direction.SOUTH));
				} else {
					put(cx, dy, 50, summary);
					bind(cx, dy, 50, "summary");
				}
			}
			put(cx, 4, 49, St.light(15));
		}
		a.put(AnchorNames.TASK_WALL, wx(35.0), G + 3.5, rz(50) + 0.125 + 0.002, 0, 0);
	}

	/**
	 * The usage board: a 16 x 3 screen on the east wall of the mission hall, facing west toward the task
	 * board's side. The monitors are bound to "usage", which makes them draw the Foreman's usage summary
	 * (current 5-hour window, per agent, last 7 days) instead of an agent's log.
	 */
	private void usageBoard() {
		BlockState wall = b(Blocks.DEEPSLATE_TILES);
		box(48, 31, 49, 50, 1, 4, wall);
		BlockState monitor = ModBlocks.MONITOR.defaultBlockState().setValue(PanelBlock.FACING, Direction.WEST).setValue(MonitorBlock.LIT, true);
		for (int cy = 33; cy <= 48; cy++) {
			put(48, 1, cy, ModBlocks.WALNUT_TRIM.defaultBlockState());
			for (int dy = 2; dy <= 4; dy++) {
				put(48, dy, cy, monitor);
				bind(48, dy, cy, "usage");
			}
		}
	}

	private void library() {
		floor(6, 30, 25, 49, OlRoyBuilder.PARQUET());
		box(5, 31, 5, 47, 1, 3, b(Blocks.BOOKSHELF));
		box(6, 49, 25, 49, 1, 3, b(Blocks.BOOKSHELF));
		// memory archives and catalogues replace part of the west shelf wall (agents read from them)
		BlockState archive = ModBlocks.MEMORY_ARCHIVE.defaultBlockState().setValue(FacingEntityBlock.FACING, Direction.EAST);
		for (int cy = 32; cy <= 46; cy++) {
			if (cy % 5 == 4) {
				for (int dy = 1; dy <= 3; dy++) {
					put(5, dy, cy, b(Blocks.BOOKSHELF)); // pilasters
				}
				continue;
			}
			for (int dy = 1; dy <= 3; dy++) {
				if (dy == 2 && cy % 5 == 2) {
					put(5, dy, cy, ModBlocks.MEMORY_CATALOG.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
				} else {
					put(5, dy, cy, archive);
					bind(5, dy, cy, "shared");
				}
			}
		}
		floor(10, 34, 21, 44, wool(DyeColor.RED));
		// two reading tables with chairs and a lantern each
		for (int t = 0; t < 2; t++) {
			int cx = t == 0 ? 11 : 17;
			box(cx, 38, cx + 2, 39, 1, 1, b(Blocks.SPRUCE_SLAB));
			put(cx + 1, 2, 38, b(Blocks.LANTERN));
			for (int k = 0; k < 3; k++) {
				put(cx + k, 1, 40, seat(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
				put(cx + k, 1, 37, seat(Blocks.SPRUCE_STAIRS, Direction.NORTH));
			}
		}
		// readers stand in front of the archive wall, looking at it
		int[] slots = {34, 38, 41, 45};
		for (int k = 0; k < slots.length; k++) {
			a.put(AnchorNames.slot(AnchorNames.LIBRARY, k + 1), wx(7.2), FEET, rz(slots[k]) + 0.5, 90, 0);
		}
		// armchairs by the open side
		put(8, 1, 36, seat(Blocks.WOOL_STAIRS.pick(DyeColor.GREEN), Direction.EAST));
		put(8, 1, 47, seat(Blocks.WOOL_STAIRS.pick(DyeColor.GREEN), Direction.EAST));
	}

	// ------------------------------------------------------------------ east wing

	private void gardenCourt() {
		int x0 = 52;
		int x1 = 70;
		int y0 = 7;
		int y1 = 30;
		floor(x0, y0, x1, y1, b(Blocks.GRASS_BLOCK));
		floor(x0, 18, x1, 19, b(Blocks.DIRT_PATH));
		floor(61, y0, 62, y1, b(Blocks.DIRT_PATH));
		for (int cy = 22; cy <= 27; cy++) {
			for (int cx = 54; cx <= 59; cx++) {
				boolean edge = cx == 54 || cx == 59 || cy == 22 || cy == 27;
				if (!(edge && (cx + cy) % 3 == 0)) {
					put(cx, 0, cy, b(Blocks.WATER));
					if ((cx * 7 + cy * 3) % 5 == 0) {
						put(cx, 1, cy, b(Blocks.LILY_PAD));
					}
				}
			}
		}
		int[][] trees = {{55, 11}, {67, 13}, {66, 26}};
		for (int i = 0; i < trees.length; i++) {
			int tx = trees[i][0];
			int ty = trees[i][1];
			put(tx, 1, ty, b(Blocks.OAK_LOG));
			put(tx, 2, ty, b(Blocks.OAK_LOG));
			Block leaves = i == 1 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.OAK_LEAVES;
			for (int dy = 3; dy <= 4; dy++) {
				int r = dy == 3 ? 2 : 1;
				for (int dx = -r; dx <= r; dx++) {
					for (int dyy = -r; dyy <= r; dyy++) {
						put(tx + dx, dy, ty + dyy, b(leaves));
					}
				}
			}
		}
		Block[] flowers = {Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.ALLIUM};
		for (int cy = y0; cy <= y1; cy++) {
			for (int cx = x0; cx <= x1; cx++) {
				if (p.get(OlRoyBuilder.OX + cx, G, rz(cy)).is(Blocks.GRASS_BLOCK) && p.isAir(OlRoyBuilder.OX + cx, G + 1, rz(cy))) {
					int h = HqLandscape.hash(cx, cy, 11) % 100;
					if (h < 14) {
						put(cx, 1, cy, b(Blocks.SHORT_GRASS));
					} else if (h < 19) {
						put(cx, 1, cy, b(Blocks.FERN));
					} else if (h < 25) {
						put(cx, 1, cy, b(flowers[h % flowers.length]));
					}
				}
			}
		}
		box(57, 16, 59, 16, 1, 1, b(Blocks.SPRUCE_SLAB));
	}

	private void lounge() {
		floor(73, 9, 86, 29, wool(DyeColor.LIGHT_GRAY));
		floor(76, 14, 83, 24, wool(DyeColor.ORANGE));
		BlockState sofaN = seat(Blocks.WOOL_STAIRS.pick(DyeColor.CYAN), Direction.SOUTH);
		BlockState sofaW = seat(Blocks.WOOL_STAIRS.pick(DyeColor.CYAN), Direction.EAST);
		int n = 1;
		for (int cx = 76; cx <= 83; cx++) {
			put(cx, 1, 25, sofaN);
			if (cx == 77 || cx == 80 || cx == 83) {
				a.put(AnchorNames.slot(AnchorNames.LOUNGE, n++), wx(cx + 0.5), FEET, rz(25) + 0.5, 0, 0);
			}
		}
		for (int cy = 15; cy <= 23; cy++) {
			put(75, 1, cy, sofaW);
			if (cy == 16 || cy == 19 || cy == 22) {
				a.put(AnchorNames.slot(AnchorNames.LOUNGE, n++), wx(75.5), FEET, rz(cy) + 0.5, -90, 0);
			}
		}
		box(78, 19, 80, 20, 1, 1, b(Blocks.SPRUCE_SLAB));
		put(79, 2, 19, b(Blocks.LANTERN));
		put(82, 1, 17, seat(Blocks.WOOL_STAIRS.pick(DyeColor.GREEN), Direction.WEST));
		put(82, 1, 22, seat(Blocks.WOOL_STAIRS.pick(DyeColor.GREEN), Direction.WEST));
		for (int cy = 11; cy <= 27; cy++) {
			put(86, 1, cy, seat(Blocks.WOOL_STAIRS.pick(DyeColor.BROWN), Direction.WEST));
		}
		put(85, 1, 10, b(Blocks.POTTED_FERN));
		put(85, 1, 28, b(Blocks.POTTED_FERN));
	}

	private void cafe() {
		floor(51, 35, 71, 51, b(Blocks.POLISHED_ANDESITE));
		box(52, 44, 66, 44, 1, 1, b(Blocks.DARK_OAK_PLANKS));
		box(52, 44, 66, 44, 2, 2, b(Blocks.DARK_OAK_SLAB));
		Block[] line = {Blocks.FURNACE, Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.BARREL, Blocks.CAULDRON, Blocks.HOPPER};
		for (int cx = 53; cx <= 65; cx++) {
			put(cx, 1, 50, b(line[(cx - 53) % line.length]));
		}
		box(53, 49, 65, 49, 1, 1, b(Blocks.DARK_OAK_PLANKS));
		for (int cx = 54; cx <= 64; cx += 2) {
			put(cx, 1, 46, b(Blocks.BARREL));
		}
		int[][] tables = {{55, 38}, {61, 38}, {67, 38}, {58, 41}};
		for (int[] t : tables) {
			put(t[0], 1, t[1], b(Blocks.SPRUCE_SLAB));
			put(t[0], 2, t[1], b(Blocks.LANTERN));
			put(t[0], 1, t[1] + 1, seat(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
			put(t[0], 1, t[1] - 1, seat(Blocks.SPRUCE_STAIRS, Direction.NORTH));
			put(t[0] - 1, 1, t[1], seat(Blocks.SPRUCE_STAIRS, Direction.EAST));
			put(t[0] + 1, 1, t[1], seat(Blocks.SPRUCE_STAIRS, Direction.WEST));
		}
	}

	/** Arcade cabinets along two walls and a Pong court inlaid in the floor. */
	private void gameCorner() {
		floor(74, 35, 92, 52, b(Blocks.DEEPSLATE_TILES));
		BlockState black = concrete(DyeColor.BLACK);
		Block[] screens = {Blocks.OCHRE_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.SEA_LANTERN};
		for (int i = 0, cx = 76; cx <= 90; cx += 2, i++) {
			put(cx, 1, 50, black);
			put(cx, 2, 50, b(screens[i % screens.length]));
			put(cx, 3, 50, black);
			put(cx, 1, 48, b(Blocks.SPRUCE_SLAB));
		}
		for (int i = 0, cy = 38; cy <= 48; cy += 2, i++) {
			put(91, 1, cy, black);
			put(91, 2, cy, b(screens[(i + 2) % screens.length]));
			put(91, 3, cy, black);
			put(89, 1, cy, b(Blocks.SPRUCE_SLAB));
		}
		BlockState white = concrete(DyeColor.WHITE);
		floor(77, 37, 87, 45, black);
		for (int cx = 77; cx <= 87; cx++) {
			put(cx, 0, 37, white);
			put(cx, 0, 45, white);
		}
		for (int cy = 37; cy <= 45; cy++) {
			put(77, 0, cy, white);
			put(87, 0, cy, white);
			if (cy % 2 == 0) {
				put(82, 0, cy, white);
			}
		}
		for (int cy = 40; cy <= 42; cy++) {
			put(79, 0, cy, white);
			put(85, 0, cy, white);
		}
		put(83, 0, 43, b(Blocks.SEA_LANTERN));
	}
}
