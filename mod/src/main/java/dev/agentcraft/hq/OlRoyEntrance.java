package dev.agentcraft.hq;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The SBC OS front of Ol'Roy: a paved forecourt on the entrance's centre line with three glowing lines
 * (orange, green, purple: Claude, Codex, Hermes, as in the Mission Control floor stripes) running from the
 * door out over the pond to a viewing platform in front of the waterfall, a pier carrying them there, light posts, and
 * a lit "SBC OS" sign standing on the canopy. Everything here is plain blocks on the lawn side of the building;
 * it does not touch the inside.
 *
 * <p>World coordinates: the building's front wall is z = OZ + D - 1, the door is x = OX + 24..26 (centre
 * OX + 25), and the lawn runs south (+z) to the pond, whose water starts about 11 blocks out on the centre line.
 */
final class OlRoyEntrance {
	private OlRoyEntrance() {
	}

	private static final int G = OlRoyBuilder.GROUND;
	private static final int CX = OlRoyBuilder.OX + 25;
	private static final int FRONT = OlRoyBuilder.OZ + 53;
	/** The canopy: z FRONT+1..FRONT+3, roof surface at G+5 (its deck of grey concrete). */
	private static final int CANOPY_TOP = G + 5;

	private static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	private static final String[][] FONT = {
		// S
		{".###.", "#...#", "#....", ".###.", "....#", "#...#", ".###."},
		// B
		{"####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."},
		// C
		{".####", "#....", "#....", "#....", "#....", "#....", ".####"},
		// O
		{".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."},
	};

	static void build(Plan p) {
		forecourt(p);
		pools(p);
		spine(p);
		doorFrame(p);
		pier(p);
		posts(p);
		sign(p);
	}

	/** Dark paving, a checker of polished deepslate and tiles, from under the canopy out to the lawn edge. */
	private static void forecourt(Plan p) {
		BlockState polished = b(Blocks.POLISHED_DEEPSLATE);
		BlockState tiles = b(Blocks.DEEPSLATE_TILES);
		BlockState edge = b(Blocks.DEEPSLATE_BRICKS);
		int z0 = FRONT + 1;
		int z1 = FRONT + 14;
		for (int x = CX - 11; x <= CX + 11; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean border = (x == CX - 11 || x == CX + 11 || z == z1) && Math.abs(x - CX) > 3;
				p.set(x, G, z, border ? edge : (x + z) % 2 == 0 ? polished : tiles);
			}
		}
		// clear the canopy column that stood in the doorway (x = CX + 1, canopy z FRONT + 3) and flank the door instead
		for (int dy = 1; dy <= 3; dy++) {
			p.set(CX + 1, G + dy, FRONT + 3, Blocks.AIR.defaultBlockState());
		}
		for (int side : new int[] {-1, 1}) {
			for (int dy = 1; dy <= 3; dy++) {
				p.set(CX + side * 4, G + dy, FRONT + 3, b(Blocks.POLISHED_DEEPSLATE));
			}
			p.set(CX + side * 4, G + 4, FRONT + 3, b(Blocks.SEA_LANTERN));
		}
	}

	/**
	 * Two reflecting pools, one each side of the centre path (7 wide: x CX-10..CX-4 and CX+4..CX+10, z FRONT+4..+13),
	 * cut 3 deep into the paving: water two deep over a floor of polished deepslate with a sea lantern in every
	 * third cell, so they glow from below at night. The pavement stays as the rim.
	 */
	private static void pools(Plan p) {
		BlockState water = b(Blocks.WATER);
		BlockState floor = b(Blocks.POLISHED_DEEPSLATE);
		BlockState lit = b(Blocks.SEA_LANTERN);
		for (int side : new int[] {-1, 1}) {
			int xa = side < 0 ? CX - 10 : CX + 4;
			for (int x = xa; x <= xa + 6; x++) {
				for (int z = FRONT + 4; z <= FRONT + 13; z++) {
					p.set(x, G, z, Blocks.AIR.defaultBlockState());
					p.set(x, G - 1, z, water);
					p.set(x, G - 2, z, water);
					p.set(x, G - 3, z, (x + z) % 3 == 0 ? lit : floor);
				}
			}
		}
	}

	/** A dark polished-deepslate frame round the 3-wide door, so it stands out from the glass and brick. */
	private static void doorFrame(Plan p) {
		BlockState frame = b(Blocks.POLISHED_DEEPSLATE);
		for (int side : new int[] {-2, 2}) {
			for (int dy = 1; dy <= 4; dy++) {
				p.set(CX + side, G + dy, FRONT, frame);
			}
		}
		for (int x = CX - 2; x <= CX + 2; x++) {
			p.set(x, G + 4, FRONT, frame);
		}
	}

	/** The pier's last deck row, and the viewing platform in front of the waterfall (the bluff's face is at OZ + D + 32 + 14). */
	private static final int PIER_END = FRONT + 45;

	/** Three coloured lines down the middle: orange, lime, purple (one block each), from the door to the viewing platform. */
	private static void spine(Plan p) {
		DyeColor[] colors = {DyeColor.ORANGE, DyeColor.LIME, DyeColor.PURPLE};
		for (int k = 0; k < 3; k++) {
			BlockState line = b(Blocks.CONCRETE.pick(colors[k]));
			for (int z = FRONT + 1; z <= PIER_END - 6; z++) {
				p.set(CX - 1 + k, G, z, line);
			}
		}
	}

	/**
	 * One straight dark-oak pier on the centre line, from the forecourt over the pond to a wide viewing platform in
	 * front of the waterfall: glass rails, lantern posts, support piles in the water, benches facing the fall, and sea
	 * lanterns under the water beside it so it glows at night.
	 */
	private static void pier(Plan p) {
		BlockState deck = b(Blocks.DARK_OAK_PLANKS);
		BlockState log = b(Blocks.DARK_OAK_LOG);
		BlockState glass = b(Blocks.GLASS_PANE);
		BlockState fence = b(Blocks.DARK_OAK_FENCE);
		int zStart = FRONT + 15;
		int zPlat = PIER_END - 5;
		for (int z = zStart; z <= PIER_END; z++) {
			for (int x = CX - 2; x <= CX + 2; x++) {
				if (z <= zPlat - 1 && x >= CX - 1 && x <= CX + 1) {
					continue; // the inlay lines run on over the deck
				}
				p.set(x, G, z, deck);
			}
		}
		// the viewing platform, wider
		for (int z = zPlat; z <= PIER_END; z++) {
			for (int x = CX - 5; x <= CX + 5; x++) {
				p.set(x, G, z, deck);
			}
		}
		// piles under the deck (dark oak logs down into the pond bed)
		for (int z = zStart + 1; z < zPlat; z += 4) {
			for (int x : new int[] {CX - 2, CX + 2}) {
				for (int y = G - 6; y < G; y++) {
					p.set(x, y, z, log);
				}
			}
		}
		for (int x : new int[] {CX - 5, CX - 2, CX + 2, CX + 5}) {
			for (int z : new int[] {zPlat, PIER_END}) {
				for (int y = G - 6; y < G; y++) {
					p.set(x, y, z, log);
				}
			}
		}
		// glass rails along both sides of the pier, broken by the lantern posts
		for (int z = zStart; z < zPlat; z++) {
			for (int x : new int[] {CX - 2, CX + 2}) {
				p.set(x, G + 1, z, glass);
			}
		}
		// platform rails: the front (toward the fall) and both sides
		for (int x = CX - 5; x <= CX + 5; x++) {
			p.set(x, G + 1, PIER_END, glass);
		}
		for (int z = zPlat; z <= PIER_END; z++) {
			p.set(CX - 5, G + 1, z, glass);
			p.set(CX + 5, G + 1, z, glass);
		}
		// lantern posts along the pier and at the platform's four corners
		for (int z = zStart + 1; z < zPlat; z += 4) {
			for (int x : new int[] {CX - 2, CX + 2}) {
				p.set(x, G + 1, z, fence);
				p.set(x, G + 2, z, fence);
				p.set(x, G + 3, z, b(Blocks.LANTERN));
			}
		}
		for (int x : new int[] {CX - 5, CX + 5}) {
			for (int z : new int[] {zPlat, PIER_END}) {
				p.set(x, G + 1, z, fence);
				p.set(x, G + 2, z, fence);
				p.set(x, G + 3, z, b(Blocks.LANTERN));
			}
		}
		// benches facing the waterfall (south)
		BlockState bench = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
		for (int x : new int[] {CX - 4, CX - 3, CX + 3, CX + 4}) {
			p.set(x, G + 1, zPlat + 2, bench);
		}
		// glow under the water beside the fall
		for (int x : new int[] {CX - 4, CX - 2, CX + 2, CX + 4}) {
			p.set(x, G - 2, PIER_END + 1, b(Blocks.SEA_LANTERN));
		}
	}

	/** Light posts along both edges of the centre path, between the pools: a deepslate-brick wall post with a lantern on top. */
	private static void posts(Plan p) {
		for (int side : new int[] {-1, 1}) {
			for (int z : new int[] {FRONT + 5, FRONT + 9, FRONT + 13}) {
				int x = CX + side * 3;
				for (int dy = 1; dy <= 3; dy++) {
					p.set(x, G + dy, z, b(Blocks.DEEPSLATE_BRICK_WALL));
				}
				p.set(x, G + 4, z, b(Blocks.LANTERN));
			}
		}
	}

	/** "SBC OS": 5 x 7 block letters in sea lantern on a dark panel, standing on the canopy roof, readable from the lawn. */
	private static void sign(Plan p) {
		int[] order = {0, 1, 2, -1, 3, 0};
		int width = 0;
		for (int c : order) {
			width += (c < 0 ? 3 : 5) + 1;
		}
		width -= 1;
		int x0 = CX - width / 2;
		int top = CANOPY_TOP + 7;
		int z = FRONT + 2;
		// the panel behind the letters
		for (int x = x0 - 2; x <= x0 + width + 1; x++) {
			for (int y = CANOPY_TOP + 1; y <= top + 1; y++) {
				p.set(x, y, FRONT + 1, b(Blocks.DEEPSLATE_TILES));
			}
		}
		int x = x0;
		for (int c : order) {
			if (c < 0) {
				x += 4;
				continue;
			}
			String[] glyph = FONT[c];
			for (int row = 0; row < 7; row++) {
				for (int col = 0; col < 5; col++) {
					if (glyph[row].charAt(col) == '#') {
						p.set(x + col, top - row, z, b(Blocks.SEA_LANTERN));
					}
				}
			}
			x += 6;
		}
	}
}
