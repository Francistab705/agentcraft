package dev.agentcraft.hq;

import dev.agentcraft.block.ModBlocks;
import dev.agentcraft.block.MonitorBlock;
import dev.agentcraft.block.PanelBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * SBC HQ Mission Control: the second floor of Ol'Roy (sbc-hq/mission-control-mockup.html). A 40 x 40 hall
 * two floors high (the floor above is cut away over it) with the screens wrapped around three walls: one
 * giant main screen between two flank boards on the north wall, and a board on each side wall. They
 * reuse the existing boards ("tasks", "summary", "usage"), so a screen anywhere shows the same live
 * data. The hall is entered by a stair from the ground-floor lobby.
 *
 * <p>Coordinates are footprint cells like {@link OlRoyInterior}; dy is the height above this floor's
 * plate (dy 1 is where feet stand). Nothing here is an agent station yet: consoles, project rooms and
 * the briefing room come in later steps.
 */
final class OlRoyMissionControl {
	private final Plan p;
	private final boolean[][] in;

	OlRoyMissionControl(Plan p, boolean[][] in) {
		this.p = p;
		this.in = in;
	}

	private static final int G = OlRoyBuilder.GROUND;
	/** The second floor's plate. */
	private static final int S = G + 5;

	/** The hall's outer wall ring, in footprint cells. */
	private static final int X0 = 6;
	private static final int X1 = 45;
	private static final int Y0 = 11;
	private static final int Y1 = 50;
	/** Highest wall block: the floor-three plate (S + 10) is the ceiling. */
	private static final int WALL_TOP = 9;

	private static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState concrete(DyeColor c) {
		return b(Blocks.CONCRETE.pick(c));
	}

	private static int rz(int cy) {
		return OlRoyBuilder.rowZ(cy);
	}

	private boolean ok(int cx, int cy) {
		return OlRoyBuilder.deep(in, cx, cy, 1);
	}

	/** A block on this floor (dy above the plate), only inside the building. */
	private void put(int cx, int dy, int cy, BlockState s) {
		if (ok(cx, cy)) {
			p.set(OlRoyBuilder.OX + cx, S + dy, rz(cy), s);
		}
	}

	private void bind(int cx, int dy, int cy, String binding) {
		if (ok(cx, cy)) {
			p.bind(OlRoyBuilder.OX + cx, S + dy, rz(cy), binding);
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
		hall();
		screens();
		stair();
	}

	/** Floor, the cut-away ceiling, the wall ring, lights and the coloured floor stripes for the future console rows. */
	private void hall() {
		BlockState wall = b(Blocks.DEEPSLATE_TILES);
		// double height: the floor-two plate and its sparse ceiling lights go, over the whole hall
		for (int cy = Y0; cy <= Y1; cy++) {
			for (int cx = X0; cx <= X1; cx++) {
				put(cx, 4, cy, Blocks.AIR.defaultBlockState());
				put(cx, 5, cy, Blocks.AIR.defaultBlockState());
				put(cx, 9, cy, Blocks.AIR.defaultBlockState());
				put(cx, 0, cy, b(Blocks.POLISHED_DEEPSLATE));
			}
		}
		// walls, with a 5-wide door in the south wall straight above the stair
		box(X0, Y0, X1, Y0, 1, WALL_TOP, wall);
		box(X0, Y1, X1, Y1, 1, WALL_TOP, wall);
		box(X0, Y0, X0, Y1, 1, WALL_TOP, wall);
		box(X1, Y0, X1, Y1, 1, WALL_TOP, wall);
		box(23, Y0, 27, Y0, 1, 4, Blocks.AIR.defaultBlockState());
		// ceiling lights: a sea lantern grid under the ceiling plus invisible fill light
		for (int cy = Y0 + 4; cy < Y1; cy += 7) {
			for (int cx = X0 + 4; cx < X1; cx += 7) {
				put(cx, WALL_TOP, cy, b(Blocks.SEA_LANTERN));
				put(cx, WALL_TOP - 4, cy, St.light(13));
			}
		}
		// floor stripes where the three console rows will stand: Claude, Codex, Hermes
		int[] rowsY = {22, 29, 36};
		DyeColor[] colors = {DyeColor.ORANGE, DyeColor.LIME, DyeColor.PURPLE};
		for (int r = 0; r < 3; r++) {
			for (int cx = 12; cx <= 39; cx++) {
				put(cx, 0, rowsY[r], concrete(colors[r]));
			}
		}
	}

	/**
	 * The wrapped screens. Adjacent monitor blocks join into one panel whatever they are bound to, so a
	 * pillar of wall always separates two screens. North wall (faces south): summary | MAIN (tasks) | usage;
	 * west wall (faces east): tasks; east wall (faces west): usage.
	 */
	private void screens() {
		screen(Direction.SOUTH, 8, 16, Y1, 3, 7, "summary");
		screen(Direction.SOUTH, 18, 33, Y1, 2, 8, "tasks");
		screen(Direction.SOUTH, 35, 43, Y1, 3, 7, "usage");
		for (int dy = 1; dy <= 9; dy++) {
			put(17, dy, Y1, b(Blocks.POLISHED_DEEPSLATE));
			put(34, dy, Y1, b(Blocks.POLISHED_DEEPSLATE));
		}
		// side walls: the screens run along z, so they are placed by cy
		sideScreen(Direction.EAST, X0, 24, 37, 3, 7, "tasks");
		sideScreen(Direction.WEST, X1, 24, 37, 3, 7, "usage");
	}

	private BlockState monitor(Direction facing) {
		return ModBlocks.MONITOR.defaultBlockState().setValue(PanelBlock.FACING, facing).setValue(MonitorBlock.LIT, true);
	}

	private void screen(Direction facing, int cx0, int cx1, int cy, int dy0, int dy1, String binding) {
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int dy = dy0; dy <= dy1; dy++) {
				put(cx, dy, cy, monitor(facing));
				bind(cx, dy, cy, binding);
			}
		}
	}

	private void sideScreen(Direction facing, int cx, int cy0, int cy1, int dy0, int dy1, String binding) {
		for (int cy = cy0; cy <= cy1; cy++) {
			for (int dy = dy0; dy <= dy1; dy++) {
				put(cx, dy, cy, monitor(facing));
				bind(cx, dy, cy, binding);
			}
		}
	}

	/**
	 * A straight stair, 5 wide, on the entrance's centre line (the ground-floor door is cx 24..26): five steps
	 * rise north at cx 23..27, cy 6..10, and the top step is flush with this floor, so you walk straight on
	 * through the hall's south door (cy 11). The foot (cy 5) has open lobby floor to the entrance. The ground-floor
	 * ceiling plate is cut clear of the climb (cx 22..28, cy 7..9), with a glass rail round the opening, and the
	 * reception desk segment that stood under it is removed.
	 */
	private void stair() {
		// the reception desk (cy 9, cx 19..31 with a gap at 24..26) would sit under the stair: clear its middle part
		for (int cx = 22; cx <= 28; cx++) {
			for (int dy = 1; dy <= 3; dy++) {
				if (ok(cx, 9)) {
					p.set(OlRoyBuilder.OX + cx, G + dy, rz(9), Blocks.AIR.defaultBlockState());
				}
			}
		}
		BlockState step = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
		for (int n = 1; n <= 5; n++) {
			int cy = 5 + n;
			for (int cx = 23; cx <= 27; cx++) {
				if (ok(cx, cy)) {
					p.set(OlRoyBuilder.OX + cx, G + n, rz(cy), step);
				}
			}
		}
		// the opening: the plate above the climb (the top step, cy 10, replaces it)
		for (int cy = 7; cy <= 9; cy++) {
			for (int cx = 22; cx <= 28; cx++) {
				if (ok(cx, cy)) {
					p.set(OlRoyBuilder.OX + cx, S, rz(cy), Blocks.AIR.defaultBlockState());
				}
			}
		}
		// rails round the opening on this floor (not across the top, where you step off)
		BlockState glass = b(Blocks.GLASS_PANE);
		for (int cy = 7; cy <= 10; cy++) {
			put(21, 1, cy, glass);
			put(29, 1, cy, glass);
		}
		for (int cx = 22; cx <= 28; cx++) {
			put(cx, 1, 6, glass);
		}
		// two lit posts mark the foot of the stair on the ground floor
		for (int cx : new int[] {22, 28}) {
			p.set(OlRoyBuilder.OX + cx, G + 1, rz(4), b(Blocks.DARK_OAK_FENCE));
			p.set(OlRoyBuilder.OX + cx, G + 2, rz(4), b(Blocks.LANTERN));
		}
		// lanterns either side of the hall door
		put(21, 2, 10, b(Blocks.LANTERN));
		put(29, 2, 10, b(Blocks.LANTERN));
	}
}
