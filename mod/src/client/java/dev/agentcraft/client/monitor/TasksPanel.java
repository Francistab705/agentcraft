package dev.agentcraft.client.monitor;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.agentcraft.client.foreman.ForemanState;
import dev.agentcraft.client.foreman.Protocol.Agent;
import dev.agentcraft.client.foreman.Protocol.Task;
import dev.agentcraft.client.foreman.Protocol.TaskStatus;
import dev.agentcraft.client.ui.UiStyle;
import dev.agentcraft.client.ui.WorldUi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The HQ task board in the same dark style as the usage and summary boards: a monitor panel bound to
 * "tasks" with four columns (Todo, Doing, Review, Done). Each card shows the task id, who has it and
 * its title in large text, with a coloured edge for its state (Review is the "needs you" colour). The
 * title band says how many tasks are done and how many are waiting for you. Right-clicking a card
 * opens the task's details, as on the original board (see {@link #taskAt}).
 */
final class TasksPanel {
	private static final float TITLE = 2.6f;
	private static final float LABEL = 1.7f;
	private static final float SMALL = 1.4f;
	private static final float BODY = 1.5f;
	private static final int MAX_TITLE_LINES = 2;

	/** where the cards were drawn last frame, per panel origin, so a click can find its card */
	private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();

	record Card(float x, float y, float w, float h, String taskId) {
	}

	record Entry(int ppb, int panelH, List<Card> cards) {
	}

	private TasksPanel() {
	}

	/** The id of the task whose card contains panel pixel (px, py) of the panel at {@code origin}, or null. */
	static @Nullable String taskAt(BlockPos origin, float px, float py) {
		Entry e = ENTRIES.get(origin);
		if (e == null) {
			return null;
		}
		for (Card c : e.cards) {
			if (px >= c.x && px <= c.x + c.w && py >= c.y && py <= c.y + c.h) {
				return c.taskId;
			}
		}
		return null;
	}

	static @Nullable Entry entry(BlockPos origin) {
		return ENTRIES.get(origin);
	}

	private record Placed(Task task, List<FormattedCharSequence> lines, float x, float y, float w, float h, int col) {
	}

	static void draw(BlockPos origin, PoseStack ps, SubmitNodeCollector c, ScreenStyle st, int ppb, int panelW, int panelH, @Nullable ForemanState fs,
		int light) {
		float w = panelW * ppb;
		float h = panelH * ppb;
		float b = ppb * 2f / 16f;
		float pad = 14;
		float x0 = b + pad;
		float x1 = w - b - pad;
		Font font = Minecraft.getInstance().font;
		DisplayDraw.Rects rects = new DisplayDraw.Rects();
		rects.add(b - 0.5f, b - 0.5f, w - b + 0.5f, h - b + 0.5f, 0, st.bgTop(), st.bgBottom(), light);
		float titleBottom = b + 32;
		rects.add(b - 0.5f, b - 0.5f, w - b + 0.5f, titleBottom, 3 * DisplayDraw.Z_STEP, st.headerBg(), light);
		rects.add(x0, titleBottom, x1, titleBottom + 1, 4 * DisplayDraw.Z_STEP, st.rule(), light);

		List<List<Task>> cols = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		int total = 0;
		if (fs != null) {
			for (Task t : fs.tasks().values()) {
				switch (t.status()) {
					case TODO -> cols.get(0).add(t);
					case DOING, BLOCKED -> cols.get(1).add(t);
					case REVIEW -> cols.get(2).add(t);
					case DONE -> cols.get(3).add(t);
					default -> {
						continue;
					}
				}
				total++;
			}
			Comparator<Task> byPriority = Comparator.comparingInt(Task::priority).reversed().thenComparingLong(Task::createdAt);
			for (int i = 0; i < 3; i++) {
				cols.get(i).sort(byPriority);
			}
			cols.get(3).sort(Comparator.comparingLong(Task::updatedAt).reversed());
		}

		float gap = 10;
		float colW = (x1 - x0 - 3 * gap) / 4f;
		float headTop = titleBottom + 10;
		float cardsTop = headTop + 30;
		float bottom = h - b - 12;
		float moreRoom = 18;
		String[] labels = {"TODO", "DOING", "REVIEW", "DONE"};
		int[] colors = {st.muted(), st.tool(), st.attention(), st.result()};
		List<Placed> placed = new ArrayList<>();
		int[] hidden = new int[4];
		for (int ci = 0; ci < 4; ci++) {
			float cx = x0 + ci * (colW + gap);
			rects.add(cx, headTop + 20, cx + colW, headTop + 23, 4 * DisplayDraw.Z_STEP, colors[ci], light);
			float y = cardsTop;
			List<Task> list = cols.get(ci);
			int shown = 0;
			for (Task t : list) {
				List<FormattedCharSequence> lines = font.split(FormattedText.of(t.title()), (int) ((colW - 16) / BODY));
				if (lines.size() > MAX_TITLE_LINES) {
					lines = new ArrayList<>(lines.subList(0, MAX_TITLE_LINES));
				}
				float ch = 6 + 8 * SMALL + 4 + lines.size() * (8 * BODY + 2) + 6;
				if (y + ch > bottom - (shown + 1 < list.size() ? moreRoom : 0) && shown > 0) {
					break;
				}
				placed.add(new Placed(t, lines, cx, y, colW, ch, ci));
				int edge = t.status() == TaskStatus.BLOCKED ? st.error() : colors[ci];
				rects.add(cx, y, cx + colW, y + ch, 2 * DisplayDraw.Z_STEP, DisplayDraw.mix(st.bgBottom(), st.text(), 0.09f), light);
				rects.add(cx, y, cx + 4, y + ch, 3 * DisplayDraw.Z_STEP, edge, light);
				y += ch + 6;
				shown++;
			}
			hidden[ci] = list.size() - shown;
		}
		rects.submit(ps, c);

		ps.pushPose();
		ps.translate(0, 0, MonitorRenderer.Z_TEXT);
		text(ps, c, "TASKS", x0, b + 8, TITLE, st.text(), light);
		if (fs == null) {
			text(ps, c, "Waiting for the Foreman...", x0, titleBottom + 24, 1.7f, st.muted(), light);
			ps.popPose();
			return;
		}
		int done = cols.get(3).size();
		int review = cols.get(2).size();
		String right = done + " of " + total + " done";
		text(ps, c, right, x1 - font.width(right) * LABEL, b + 11, LABEL, st.muted(), light);
		if (review > 0) {
			String need = review + (review == 1 ? " waiting for you" : " waiting for you");
			text(ps, c, need, x1 - font.width(right) * LABEL - 20 - font.width(need) * LABEL, b + 11, LABEL, st.attention(), light);
		}
		for (int ci = 0; ci < 4; ci++) {
			float cx = x0 + ci * (colW + gap);
			text(ps, c, labels[ci], cx, headTop, LABEL, colors[ci], light);
			String n = String.valueOf(cols.get(ci).size());
			text(ps, c, n, cx + colW - font.width(n) * LABEL, headTop, LABEL, st.muted(), light);
			if (cols.get(ci).isEmpty()) {
				text(ps, c, "nothing here", cx + 4, cardsTop + 4, SMALL, st.muted(), light);
			}
			if (hidden[ci] > 0) {
				text(ps, c, "+" + hidden[ci] + " more", cx + 4, bottom - 12, SMALL, st.muted(), light);
			}
		}
		List<Card> cards = new ArrayList<>();
		for (Placed p : placed) {
			Task t = p.task;
			cards.add(new Card(p.x, p.y, p.w, p.h, t.id()));
			float tx = p.x + 10;
			text(ps, c, t.id(), tx, p.y + 6, SMALL, st.muted(), light);
			Agent a = t.assignee() == null ? null : fs.agent(t.assignee());
			if (a != null) {
				String nm = a.name();
				text(ps, c, nm, p.x + p.w - 6 - font.width(nm) * SMALL, p.y + 6, SMALL, st.name(a.id()), light);
			}
			float ly = p.y + 6 + 8 * SMALL + 4;
			for (FormattedCharSequence line : p.lines) {
				ps.pushPose();
				ps.translate(tx, ly, 0);
				ps.scale(BODY, BODY, 1f);
				WorldUi.submitText(ps, c, line, 0, 0, UiStyle.withAlpha(st.text(), 255), light);
				ps.popPose();
				ly += 8 * BODY + 2;
			}
		}
		ps.popPose();
		ENTRIES.put(origin.immutable(), new Entry(ppb, panelH, cards));
	}

	private static void text(PoseStack ps, SubmitNodeCollector c, String s, float x, float y, float scale, int color, int light) {
		ps.pushPose();
		ps.translate(x, y, 0);
		ps.scale(scale, scale, 1f);
		WorldUi.submitText(ps, c, s, 0, 0, UiStyle.withAlpha(color, 255), light);
		ps.popPose();
	}
}
