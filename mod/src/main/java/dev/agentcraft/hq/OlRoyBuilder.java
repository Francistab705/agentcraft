package dev.agentcraft.hq;

import dev.agentcraft.AgentCraft;
import dev.agentcraft.command.AgentCraftCommands;
import dev.agentcraft.world.HqWorld;
import java.util.Locale;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
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

	/**
	 * World position of the footprint's south-west corner: on the flat plain south of the lake (see the
	 * terrain survey in ol-roy/notes.md), well east of the studio's site box (x up to 46).
	 */
	private static final int OX = 200;
	private static final int OZ = 84;
	/** Flat pad: the building plus 12 blocks around it and a 62-block lawn/pond/bluff strip to the south. */
	private static final int CORE_X0 = OX - 12;
	private static final int CORE_X1 = OX + W - 1 + 12;
	private static final int CORE_Z0 = OZ - 12;
	private static final int CORE_Z1 = OZ + D - 1 + 62;
	/** Width of the blend from the flat pad into the natural terrain. */
	private static final int SKIRT = 16;
	/** Highest water surface block in the pond (sea level 63 means water fills up to y=62; the pond sits 1 below the lawn). */
	private static final int POND_TOP = 63;
	private static final int PX = OX + 47;
	private static final int PZ = OZ + D + 22;
	private static final int PRX = 26;
	private static final int PRZ = 15;
	/** Bluff centre: its north face is the waterfall wall, facing the pond. */
	private static final int BX = PX;
	private static final int BZ = PZ + 18;
	private static final int BLUFF_TOP = StudioHqBuilder.GROUND + 13;

	private static final int GROUND = StudioHqBuilder.GROUND;
	/** Floor to floor in blocks: about 4 clear above a 1-block slab (the real building is about 4.3 m floor to floor). */
	private static final int FLOOR_H = 5;
	private static final int FLOORS = 4;
	/** Bay spacing of the brick piers, in blocks. */
	private static final int BAY = 4;
	/** The roof terrace: the top floor's protruding west section (cx 0..TERRACE, cy 0..TERRACE_BACK) is left open to the west and south. */
	private static final int TERRACE = 11;
	/** Last footprint row of the terrace: the section that sticks out west ends at the step in the wall after row 26. */
	private static final int TERRACE_BACK = 26;
	private static final BlockState BRACE = Blocks.IRON_BARS.defaultBlockState();
	private static final BlockState SPRUCE = Blocks.SPRUCE_PLANKS.defaultBlockState();

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState PLATE = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
	private static final BlockState GREY = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
	private static final BlockState BRICK = Blocks.BRICKS.defaultBlockState();
	private static final BlockState GLASS = Blocks.GLASS_PANE.defaultBlockState();

	static void init() {
		AgentCraftCommands.sub(root -> root.then(Commands.literal("olroy").then(Commands.literal("survey").executes(ctx -> {
			survey(ctx.getSource().getLevel());
			ctx.getSource().sendSuccess(() -> Component.literal("Terrain survey written to the game log"), false);
			return 1;
		})).executes(ctx -> {
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

	/**
	 * Logs a coarse height map of the natural terrain (no chunks are generated: it asks the noise
	 * generator). One char per sample: '~' water, otherwise a letter per 4 blocks of floor height from
	 * y=56 (a = 56-59, b = 60-63, c = 64-67 ...).
	 */
	private static void survey(ServerLevel level) {
		var gen = level.getChunkSource().getGenerator();
		var rs = level.getChunkSource().randomState();
		int step = 8;
		for (int z = -140; z <= 200; z += step) {
			StringBuilder row = new StringBuilder();
			for (int x = -60; x <= 300; x += step) {
				int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs) - 1;
				int surf = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs) - 1;
				row.append(surf > floor ? '~' : (char) ('a' + Math.max(0, Math.min(25, (floor - 56) / 4))));
			}
			AgentCraft.LOGGER.info("SURVEY z={} {}", String.format(Locale.ROOT, "%4d", z), row);
		}
		AgentCraft.LOGGER.info("SURVEY x from -60 to 300 step {}", step);
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

	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();

	static String build(ServerLevel level) {
		long t0 = System.nanoTime();
		boolean[][] in = new boolean[D][W];
		for (int y = 0; y < D; y++) {
			for (int x = 0; x < W; x++) {
				in[y][x] = inside(x + 0.5, y + 0.5);
			}
		}
		int minX = CORE_X0 - SKIRT;
		int maxX = CORE_X1 + SKIRT;
		int minZ = CORE_Z0 - SKIRT;
		int maxZ = CORE_Z1 + SKIRT;
		int minY = GROUND - 14;
		int roofY = GROUND + FLOORS * FLOOR_H;
		int maxY = roofY + 10;
		Plan p = new Plan(minX, minY, minZ, maxX, maxY, maxZ, GROUND, y -> AIR);
		var gen = level.getChunkSource().getGenerator();
		var rs = level.getChunkSource().randomState();
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				double w = padWeight(x, z);
				int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs) - 1;
				boolean water = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs) - 1 > floor;
				if (w <= 0.001 || (water && w < 1)) {
					// natural terrain (and natural water) is left exactly as the world has it
					for (int y = minY; y <= maxY; y++) {
						p.set(x, y, z, level.getBlockState(new BlockPos(x, y, z)));
					}
					continue;
				}
				int top = (int) Math.round(floor + (GROUND - floor) * w);
				column(p, x, z, minY, maxY, top);
			}
		}
		pond(p, minY);
		bluff(p);
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
				boolean westFace = out(in, cx - 1, cy);
				boolean southFace = out(in, cx, cy - 1);
				boolean pier = wall && ((nsFace && ewFace) || (nsFace && cx % BAY == 0) || (ewFace && cy % BAY == 0));
				for (int f = 0; f < FLOORS; f++) {
					int base = GROUND + f * FLOOR_H;
					p.set(wx, base, wz, PLATE);
					boolean top = f == FLOORS - 1;
					boolean innerV = top && cx == TERRACE + 1 && cy <= TERRACE_BACK + 1;
					boolean innerH = top && cy == TERRACE_BACK + 1 && cx <= TERRACE + 1;
					if (innerV || innerH) {
						// the glass wall closing the terrace on its east and north sides
						boolean innerPier = (innerV && cy % BAY == 0) || (innerH && cx % BAY == 0);
						for (int dy = 1; dy < FLOOR_H; dy++) {
							p.set(wx, base + dy, wz, innerPier ? BRICK : GLASS);
						}
					}
					boolean terraceCell = top && cx <= TERRACE && cy <= TERRACE_BACK;
					if (!wall && !innerV && !innerH) {
						if (terraceCell) {
							p.set(wx, base + FLOOR_H - 1, wz, SPRUCE);
						} else if (cx % 5 == 2 && cy % 5 == 2) {
							p.set(wx, base + FLOOR_H - 1, wz, St.light(13));
						}
					}
					if (!wall) {
						continue;
					}
					boolean cut = terraceCell && (westFace || southFace);
					boolean backCorner = cut && out(in, cx, cy + 1);
					for (int dy = 1; dy < FLOOR_H; dy++) {
						p.set(wx, base + dy, wz, cut ? (backCorner ? GREY : terraceEdge(southFace ? cx : cy, dy)) : pier ? BRICK : facade(f, dy));
					}
				}
				p.set(wx, roofY, wz, PLATE);
				if (wall) {
					p.set(wx, roofY + 1, wz, GREY);
				}
			}
		}
		details(p, in, roofY);
		p.settleGrass();
		Plan.Stats st = p.apply(level, null, true);
		// the plan sets water without waking the fluid engine: tick the basin so it spills over the lip
		for (int x = BX - 1; x <= BX + 1; x++) {
			for (int z = BZ - 4; z <= BZ + 2; z++) {
				level.scheduleTick(new BlockPos(x, BLUFF_TOP, z), Fluids.WATER, 5);
			}
		}
		AgentCraft.LOGGER.info("Ol'Roy: planned and applied {} cells ({} changed, {} connections) in {} ms", st.cells(), st.changed(), st.connected(),
			(System.nanoTime() - t0) / 1_000_000);
		return String.format(Locale.ROOT, "%d blocks updated", st.changed() + st.connected());
	}

	/**
	 * The open outer edge of the roof terrace: grey columns every 4 blocks, X-bracing of thin bars
	 * between them, and a glass guard at floor level.
	 */
	private static BlockState terraceEdge(int along, int dy) {
		if (along % BAY == 0) {
			return GREY;
		}
		if (dy == FLOOR_H - 1) {
			return SPRUCE;
		}
		int j = along % BAY;
		if (dy == j || dy == BAY - j) {
			return BRACE;
		}
		return dy == 1 ? GLASS : AIR;
	}

	/** Entrance door, canopy over the lawn side, and the rooftop mechanical boxes. */
	private static void details(Plan p, boolean[][] in, int roofY) {
		// entrance: a 3-wide, 3-high opening in the ground-floor glass at the middle of the west wing's front
		for (int cx = 24; cx <= 26; cx++) {
			for (int dy = 1; dy <= 3; dy++) {
				p.set(OX + cx, GROUND + dy, OZ + (D - 1), AIR);
			}
		}
		// canopy: a flat dark roof 3 blocks deep along the front of the west wing, on columns at its edge
		for (int cx = 6; cx <= 46; cx++) {
			for (int k = 1; k <= 3; k++) {
				p.set(OX + cx, GROUND + 4, OZ + (D - 1) + k, SPRUCE);
				p.set(OX + cx, GROUND + 5, OZ + (D - 1) + k, GREY);
			}
		}
		for (int cx = 6; cx <= 46; cx += 10) {
			for (int dy = 1; dy <= 3; dy++) {
				p.set(OX + cx, GROUND + dy, OZ + (D - 1) + 3, GREY);
			}
		}
		// rooftop mechanical boxes, set back from the edge: x0, y0, x1, y1, height
		int[][] boxes = {{22, 18, 44, 36, 3}, {52, 20, 66, 34, 3}, {72, 10, 84, 24, 2}};
		for (int[] b : boxes) {
			for (int cy = b[1]; cy <= b[3]; cy++) {
				for (int cx = b[0]; cx <= b[2]; cx++) {
					if (in[cy][cx]) {
						for (int h = 1; h <= b[4]; h++) {
							p.set(OX + cx, roofY + h, OZ + (D - 1 - cy), GREY);
						}
					}
				}
			}
		}
	}

	/** 1 on the flat pad, easing to 0 over {@link #SKIRT} blocks outside it. */
	private static double padWeight(int x, int z) {
		double dx = Math.max(Math.max(CORE_X0 - x, x - CORE_X1), 0);
		double dz = Math.max(Math.max(CORE_Z0 - z, z - CORE_Z1), 0);
		double d = Math.hypot(dx, dz);
		if (d >= SKIRT) {
			return 0;
		}
		double t = d / SKIRT;
		return 1 - t * t * (3 - 2 * t);
	}

	/** Stone, then dirt, then a grass top at {@code top}; air above. */
	private static void column(Plan p, int x, int z, int minY, int maxY, int top) {
		for (int y = minY; y <= maxY; y++) {
			p.set(x, y, z, y > top ? AIR : y == top ? GRASS : y >= top - 3 ? DIRT : STONE);
		}
	}

	/** Elliptical pond dug into the lawn, deepest at the middle; water to {@link #POND_TOP}. */
	private static void pond(Plan p, int minY) {
		for (int x = PX - PRX; x <= PX + PRX; x++) {
			for (int z = PZ - PRZ; z <= PZ + PRZ; z++) {
				double r2 = sq((x - PX) / (double) PRX) + sq((z - PZ) / (double) PRZ);
				if (r2 >= 1) {
					continue;
				}
				int bed = POND_TOP - 1 - (int) Math.round(4 * (1 - r2));
				for (int y = bed; y <= POND_TOP + 1; y++) {
					p.set(x, y, z, y == bed ? DIRT : y <= POND_TOP ? WATER : AIR);
				}
			}
		}
	}

	/**
	 * Rock bluff behind the pond. A 3-wide basin of source blocks on top spills over the north face
	 * into the pond; the game's own fluid flow makes the waterfall.
	 */
	private static void bluff(Plan p) {
		for (int x = BX - 10; x <= BX + 10; x++) {
			for (int z = BZ - 4; z <= BZ + 6; z++) {
				boolean core = Math.abs(x - BX) <= 3 && z <= BZ + 4;
				int dx = Math.abs(x - BX);
				int top = core ? BLUFF_TOP : BLUFF_TOP - dx / 4 - Math.max(0, z - (BZ + 2)) - HqLandscape.hash(x, z, 77) % 3;
				for (int y = GROUND; y <= top; y++) {
					int h = HqLandscape.hash(x, y * 31 + z, 78);
					p.set(x, y, z, h % 5 == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
						: h % 3 == 0 ? Blocks.ANDESITE.defaultBlockState() : STONE);
				}
			}
		}
		// basin: floor one below the rim, sources 3 wide from the front lip back to z = BZ + 2
		for (int x = BX - 1; x <= BX + 1; x++) {
			for (int z = BZ - 4; z <= BZ + 2; z++) {
				p.set(x, BLUFF_TOP, z, WATER);
			}
		}
		for (int x = BX - 1; x <= BX + 1; x++) {
			p.set(x, BLUFF_TOP - 1, BZ - 4, STONE);
		}
	}

	private static double sq(double v) {
		return v * v;
	}

	/** Between piers: all glass on the ground floor, a grey spandrel under glass above. */
	private static BlockState facade(int floor, int dy) {
		return floor > 0 && dy == 1 ? GREY : GLASS;
	}

	private static boolean out(boolean[][] in, int x, int y) {
		return x < 0 || y < 0 || x >= W || y >= D || !in[y][x];
	}
}
