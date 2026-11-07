package dev.agentcraft.hq;

import dev.agentcraft.AgentCraft;
import dev.agentcraft.command.AgentCraftCommands;
import dev.agentcraft.world.HqWorld;
import java.util.Locale;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ol'Roy (Walmart Home Office campus, Bentonville), 1 block = 1 m. Version 0 is massing only:
 * the real footprint (OpenStreetMap way 949466236, snapped to the block grid), four floor plates,
 * a pier-and-glass wall ring and a roof. No interior, door, terrace or roof boxes yet.
 *
 * <p>Not an {@link HqBuilder}: it writes no agent anchors and must not replace the studio's layout,
 * so it has its own command, {@code /agentcraft olroy}, and leaves the studio's saved plan alone.
 * Idempotent: running it twice gives the same world. Source of the numbers: ol-roy/notes.md.
 */
final class OlRoyBuilder {
	private OlRoyBuilder() {
	}

	/** Footprint outline in blocks: x east, y north (from the south-west corner), clockwise from the NW corner. */
	private static final double[][] OUTLINE = {
		{4.09, 53.34}, {4.25, 27.05}, {0.0, 27.02}, {0.16, 0.02}, {1.27, 0.0}, {25.36, 0.2}, {49.15, 0.32}, {49.12, 4.93},
		{51.49, 4.95}, {89.16, 5.16}, {89.0, 32.04}, {93.45, 32.07}, {93.32, 53.89}, {50.34, 53.63}, {47.43, 53.56}, {34.3, 53.53},
	};
	private static final int W = 94;
	private static final int D = 54;

	/** World position of the footprint's south-west corner: east of the studio's site box (x up to 46), same ground. */
	private static final int OX = 70;
	private static final int OZ = -18;
	private static final int MARGIN = 14;

	private static final int GROUND = StudioHqBuilder.GROUND;
	/** Floor to floor in blocks: about 4 clear above a 1-block slab (the real building is about 4.3 m floor to floor). */
	private static final int FLOOR_H = 5;
	private static final int FLOORS = 4;
	/** Bay spacing of the brick piers, in blocks. */
	private static final int BAY = 4;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState PLATE = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
	private static final BlockState GREY = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
	private static final BlockState BRICK = Blocks.BRICKS.defaultBlockState();
	private static final BlockState GLASS = Blocks.GLASS_PANE.defaultBlockState();

	static void init() {
		AgentCraftCommands.sub(root -> root.then(Commands.literal("olroy").executes(ctx -> {
			var source = ctx.getSource();
			if (!HqWorld.isHq(source.getServer())) {
				source.sendFailure(Component.literal("/agentcraft olroy only builds in the \"" + HqWorld.LEVEL_NAME + "\" world"));
				return 0;
			}
			String report;
			try {
				report = build(source.getLevel());
			} catch (RuntimeException e) {
				AgentCraft.LOGGER.error("Ol'Roy build failed", e);
				source.sendFailure(Component.literal("Ol'Roy build failed: " + e));
				return 0;
			}
			source.sendSuccess(() -> Component.literal("Built Ol'Roy at x " + OX + "..." + (OX + W - 1) + ", z " + OZ + "..." + (OZ + D - 1)
				+ ": " + report), true);
			return 1;
		})));
	}

	private static boolean inside(double x, double y) {
		boolean in = false;
		for (int i = 0, n = OUTLINE.length; i < n; i++) {
			double[] a = OUTLINE[i];
			double[] b = OUTLINE[(i + 1) % n];
			if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) {
				in = !in;
			}
		}
		return in;
	}

	static String build(ServerLevel level) {
		long t0 = System.nanoTime();
		boolean[][] in = new boolean[D][W];
		for (int y = 0; y < D; y++) {
			for (int x = 0; x < W; x++) {
				in[y][x] = inside(x + 0.5, y + 0.5);
			}
		}
		int minX = OX - MARGIN;
		int maxX = OX + W - 1 + MARGIN;
		int minZ = OZ - MARGIN;
		int maxZ = OZ + D - 1 + MARGIN;
		int roofY = GROUND + FLOORS * FLOOR_H;
		Plan p = new Plan(minX, GROUND - 4, minZ, maxX, roofY + 6, maxZ, GROUND, y -> y > GROUND ? AIR
			: y == GROUND ? Blocks.GRASS_BLOCK.defaultBlockState() : y >= GROUND - 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
		for (int cy = 0; cy < D; cy++) {
			for (int cx = 0; cx < W; cx++) {
				if (!in[cy][cx]) {
					continue;
				}
				// north is up in the footprint and -z in the world
				int wx = OX + cx;
				int wz = OZ + (D - 1 - cy);
				boolean nsFace = out(in, cx, cy + 1) || out(in, cx, cy - 1);
				boolean ewFace = out(in, cx + 1, cy) || out(in, cx - 1, cy);
				boolean wall = nsFace || ewFace;
				boolean pier = wall && ((nsFace && ewFace) || (nsFace && cx % BAY == 0) || (ewFace && cy % BAY == 0));
				for (int f = 0; f < FLOORS; f++) {
					int base = GROUND + f * FLOOR_H;
					p.set(wx, base, wz, PLATE);
					if (!wall) {
						continue;
					}
					for (int dy = 1; dy < FLOOR_H; dy++) {
						p.set(wx, base + dy, wz, pier ? BRICK : facade(f, dy));
					}
				}
				p.set(wx, roofY, wz, PLATE);
				if (wall) {
					p.set(wx, roofY + 1, wz, GREY);
				}
			}
		}
		p.settleGrass();
		Plan.Stats st = p.apply(level, null, true);
		AgentCraft.LOGGER.info("Ol'Roy: planned and applied {} cells ({} changed, {} connections) in {} ms", st.cells(), st.changed(), st.connected(),
			(System.nanoTime() - t0) / 1_000_000);
		return String.format(Locale.ROOT, "%d blocks updated", st.changed() + st.connected());
	}

	/** Between piers: all glass on the ground floor, a grey spandrel under glass above. */
	private static BlockState facade(int floor, int dy) {
		return floor > 0 && dy == 1 ? GREY : GLASS;
	}

	private static boolean out(boolean[][] in, int x, int y) {
		return x < 0 || y < 0 || x >= W || y >= D || !in[y][x];
	}
}
