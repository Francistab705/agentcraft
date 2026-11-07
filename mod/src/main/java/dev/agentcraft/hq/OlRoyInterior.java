package dev.agentcraft.hq;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * SBC HQ ground floor inside Ol'Roy: "Studio + Garden Court" (ol-roy/../sbc-hq/mockups.html, option C)
 * with a game corner next to the café. West wing: lobby, meeting room, desks, workshop, library and
 * mission hall. East wing: garden court, lounge, café and kitchen, and the game corner.
 *
 * <p>Vanilla blocks only and no agent anchors: the stations here are stand-ins for AgentCraft's own
 * station blocks, which come when this floor becomes the working HQ. Coordinates are footprint cells
 * (cx east, cy north from the south-west corner); dy is the height above the floor plate.
 */
final class OlRoyInterior {
	private final Plan p;
	private final boolean[][] in;

	OlRoyInterior(Plan p, boolean[][] in) {
		this.p = p;
		this.in = in;
	}

	private static final int G = OlRoyBuilder.GROUND;

	private static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState wool(DyeColor c) {
		return b(Blocks.WOOL.pick(c));
	}

	private static BlockState concrete(DyeColor c) {
		return b(Blocks.CONCRETE.pick(c));
	}

	/** A stair-like block whose open side faces {@code dir}: the seat looks that way. */
	private static BlockState seat(Block stairs, Direction dir) {
		return stairs.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, dir);
	}

	/** True on a footprint cell that is inside and not on the outer wall ring. */
	private boolean ok(int cx, int cy) {
		return OlRoyBuilder.deep(in, cx, cy, 1);
	}

	private void put(int cx, int dy, int cy, BlockState s) {
		if (ok(cx, cy)) {
			p.set(OlRoyBuilder.OX + cx, G + dy, OlRoyBuilder.rowZ(cy), s);
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

	void build() {
		lobby();
		meeting();
		deskPod();
		workshop();
		missionHall();
		library();
		gardenCourt();
		lounge();
		cafe();
		gameCorner();
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
		for (int cx = 41; cx <= 45; cx++) {
			put(cx, 1, 8, seat(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
			put(cx, 1, 5, seat(Blocks.SPRUCE_STAIRS, Direction.NORTH));
		}
		for (int cy = 6; cy <= 7; cy++) {
			put(40, 1, cy, seat(Blocks.SPRUCE_STAIRS, Direction.EAST));
			put(46, 1, cy, seat(Blocks.SPRUCE_STAIRS, Direction.WEST));
		}
		put(43, 2, 6, b(Blocks.LANTERN));
	}

	/** Six agent desks in two rows plus a larger L-shaped desk for the user. */
	private void deskPod() {
		floor(3, 15, 26, 26, b(Blocks.SPRUCE_PLANKS));
		BlockState top = b(Blocks.DARK_OAK_SLAB);
		for (int row = 0; row < 2; row++) {
			int cy = row == 0 ? 17 : 22;
			for (int d = 0; d < 3; d++) {
				int cx = 5 + d * 5;
				box(cx, cy, cx + 3, cy, 1, 1, top);
				put(cx + 1, 1, cy + 1, seat(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
				put(cx + 3, 2, cy, b(Blocks.POTTED_FERN));
			}
		}
		// the user's desk: an L, with the chair tucked into the corner
		box(21, 22, 24, 22, 1, 1, top);
		box(24, 19, 24, 21, 1, 1, top);
		put(22, 1, 23, seat(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
		put(21, 2, 22, b(Blocks.LANTERN));
		// low shelf between the desks and the workshop, with a gap to walk through
		for (int cy = 16; cy <= 25; cy++) {
			if (cy < 20 || cy > 21) {
				put(27, 1, cy, b(Blocks.BOOKSHELF));
				put(27, 2, cy, b(Blocks.BOOKSHELF));
			}
		}
	}

	/** Terminal, testbench and merge station as three counters with a stand-in block on each. */
	private void workshop() {
		floor(29, 16, 47, 25, b(Blocks.POLISHED_ANDESITE));
		BlockState counter = b(Blocks.DARK_OAK_PLANKS);
		int[] starts = {30, 36, 42};
		Block[] props = {Blocks.LECTERN, Blocks.BREWING_STAND, Blocks.SMITHING_TABLE};
		for (int i = 0; i < 3; i++) {
			box(starts[i], 24, starts[i] + 4, 24, 1, 1, counter);
			put(starts[i] + 2, 2, 24, b(props[i]));
			put(starts[i] + 2, 1, 22, b(Blocks.SPRUCE_SLAB));
		}
	}

	/** Goal atrium floor medallion, decision podium and a task wall of coloured cards. */
	private void missionHall() {
		floor(29, 30, 47, 51, OlRoyBuilder.PARQUET());
		int mx = 38;
		int my = 40;
		for (int cy = my - 5; cy <= my + 5; cy++) {
			for (int cx = mx - 5; cx <= mx + 5; cx++) {
				double r = Math.hypot(cx - mx, cy - my);
				if (r <= 5.2) {
					put(cx, 0, cy, wool(r <= 2.2 ? DyeColor.YELLOW : r <= 4.2 ? DyeColor.WHITE : DyeColor.BLUE));
				}
			}
		}
		// decision podium: a small dais with a lectern, facing the entrance
		floor(37, 31, 39, 33, b(Blocks.POLISHED_ANDESITE));
		put(38, 1, 32, b(Blocks.LECTERN));
		// task wall along the north wall
		DyeColor[] cards = {DyeColor.ORANGE, DyeColor.LIME, DyeColor.CYAN, DyeColor.PINK, DyeColor.YELLOW};
		for (int cx = 31; cx <= 45; cx++) {
			box(cx, 50, cx, 50, 1, 3, b(Blocks.DEEPSLATE_TILES));
			int h = HqLandscape.hash(cx, 50, 5);
			if (h % 3 != 0) {
				put(cx, 2, 49, wool(cards[h % cards.length]));
			}
		}
	}

	private void library() {
		floor(6, 30, 25, 49, OlRoyBuilder.PARQUET());
		box(5, 31, 5, 47, 1, 3, b(Blocks.BOOKSHELF));
		box(6, 49, 25, 49, 1, 3, b(Blocks.BOOKSHELF));
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
		// armchairs by the open side
		put(8, 1, 36, seat(Blocks.WOOL_STAIRS.pick(DyeColor.GREEN), Direction.EAST));
		put(8, 1, 42, seat(Blocks.WOOL_STAIRS.pick(DyeColor.GREEN), Direction.EAST));
	}

	// ------------------------------------------------------------------ east wing

	private void gardenCourt() {
		int x0 = 52;
		int x1 = 70;
		int y0 = 7;
		int y1 = 30;
		floor(x0, y0, x1, y1, b(Blocks.GRASS_BLOCK));
		// paths: one across, one along
		floor(x0, 18, x1, 19, b(Blocks.DIRT_PATH));
		floor(61, y0, 62, y1, b(Blocks.DIRT_PATH));
		// pond with lily pads
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
		// three small trees: a short trunk and a flat crown, to fit under the ceiling
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
		// ferns, grass and a few flowers on the lawn
		Block[] flowers = {Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.ALLIUM};
		for (int cy = y0; cy <= y1; cy++) {
			for (int cx = x0; cx <= x1; cx++) {
				if (p.get(OlRoyBuilder.OX + cx, G, OlRoyBuilder.rowZ(cy)).is(Blocks.GRASS_BLOCK) && p.isAir(OlRoyBuilder.OX + cx, G + 1, OlRoyBuilder.rowZ(cy))) {
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
		// a bench on the path
		box(57, 16, 59, 16, 1, 1, b(Blocks.SPRUCE_SLAB));
	}

	private void lounge() {
		floor(73, 9, 86, 29, wool(DyeColor.LIGHT_GRAY));
		floor(76, 14, 83, 24, wool(DyeColor.ORANGE));
		BlockState sofa = seat(Blocks.WOOL_STAIRS.pick(DyeColor.CYAN), Direction.SOUTH);
		for (int cx = 76; cx <= 83; cx++) {
			put(cx, 1, 25, sofa);
		}
		for (int cy = 15; cy <= 23; cy++) {
			put(75, 1, cy, seat(Blocks.WOOL_STAIRS.pick(DyeColor.CYAN), Direction.EAST));
		}
		box(78, 19, 80, 20, 1, 1, b(Blocks.SPRUCE_SLAB));
		put(79, 2, 19, b(Blocks.LANTERN));
		put(82, 1, 17, seat(Blocks.WOOL_STAIRS.pick(DyeColor.GREEN), Direction.WEST));
		put(82, 1, 22, seat(Blocks.WOOL_STAIRS.pick(DyeColor.GREEN), Direction.WEST));
		// window bench along the east wall
		for (int cy = 11; cy <= 27; cy++) {
			put(86, 1, cy, seat(Blocks.WOOL_STAIRS.pick(DyeColor.BROWN), Direction.WEST));
		}
		put(85, 1, 10, b(Blocks.POTTED_FERN));
		put(85, 1, 28, b(Blocks.POTTED_FERN));
	}

	private void cafe() {
		floor(51, 35, 71, 51, b(Blocks.POLISHED_ANDESITE));
		// bar counter with a front, and a kitchen line behind it
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
		// four small tables, each with four seats
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
		// Pong court: black field, white border and centre line, two paddles and the ball
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
