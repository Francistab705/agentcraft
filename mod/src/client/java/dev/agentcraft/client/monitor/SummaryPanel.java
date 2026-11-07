package dev.agentcraft.client.monitor;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.agentcraft.client.foreman.ForemanState;
import dev.agentcraft.client.foreman.Protocol.Agent;
import dev.agentcraft.client.foreman.Protocol.Goal;
import dev.agentcraft.client.foreman.Protocol.Task;
import dev.agentcraft.client.foreman.Protocol.TaskStatus;
import dev.agentcraft.client.ui.UiStyle;
import dev.agentcraft.client.ui.WorldUi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The HQ summary board: a wide monitor panel bound to "summary", meant to sit beside the task board.
 * It lists the finished tasks, newest first, each with who did it, when, and the summary the agent
 * wrote (wrapped, large enough to read from a few blocks away), so you can read what was done
 * instead of scanning a long Done column. The current goal and its progress sit under the title.
 */
final class SummaryPanel {
	private static final float TITLE = 2.6f;
	private static final float HEAD = 2.2f;
	private static final float BODY = 2.0f;
	private static final int MAX_LINES = 4;

	private SummaryPanel() {
	}

	static void draw(PoseStack ps, SubmitNodeCollector c, ScreenStyle st, int ppb, int panelW, int panelH, @Nullable ForemanState fs, int light) {
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

		List<Task> done = new ArrayList<>();
		if (fs != null) {
			for (Task t : fs.tasks().values()) {
				if (t.status() == TaskStatus.DONE) {
					done.add(t);
				}
			}
			done.sort(Comparator.comparingLong(Task::updatedAt).reversed());
		}
		float footerY = h - b - 18;
		float y = titleBottom + 26;
		float textW = x1 - x0;

		// lay out the entries first (so the separators can be drawn with the other rectangles)
		List<Entry> entries = new ArrayList<>();
		int shown = 0;
		for (Task t : done) {
			String text = t.summary() == null || t.summary().isBlank() ? "(no summary written)" : t.summary().strip().replaceAll("\\s+", " ");
			List<FormattedCharSequence> lines = font.split(FormattedText.of(text), (int) (textW / BODY));
			boolean cut = lines.size() > MAX_LINES;
			if (cut) {
				lines = new ArrayList<>(lines.subList(0, MAX_LINES));
			}
			float eh = 8 * HEAD + 6 + lines.size() * (8 * BODY + 3) + 10;
			if (y + eh > footerY && shown > 0) {
				break;
			}
			entries.add(new Entry(t, lines, cut, y));
			y += eh;
			rects.add(x0, y - 6, x1, y - 5, 4 * DisplayDraw.Z_STEP, st.rule(), light);
			shown++;
		}
		rects.submit(ps, c);

		ps.pushPose();
		ps.translate(0, 0, MonitorRenderer.Z_TEXT);
		text(ps, c, "WHAT WAS DONE", x0, b + 8, TITLE, st.text(), light);
		Goal goal = fs == null ? null : fs.goal();
		if (goal != null) {
			String g = "Goal: " + goal.text();
			text(ps, c, ellipsize(font, g, textW / 1.5f), x0, titleBottom + 8, 1.5f, st.muted(), light);
		}
		if (fs == null || done.isEmpty()) {
			text(ps, c, fs == null ? "Waiting for the Foreman..." : "Nothing finished yet. Finished work appears here with a plain-English summary.",
				x0, titleBottom + 44, 1.7f, st.muted(), light);
		}
		long now = System.currentTimeMillis();
		for (Entry e : entries) {
			Task t = e.task;
			Agent a = t.assignee() == null || fs == null ? null : fs.agent(t.assignee());
			String who = a != null ? a.name() : (t.assignee() == null ? "" : t.assignee());
			float ey = e.y;
			int whoColor = a != null ? st.name(a.id()) : st.text();
			text(ps, c, t.id(), x0, ey, HEAD, st.muted(), light);
			float tx = x0 + font.width(t.id()) * HEAD + 10;
			String ago = ago(now - t.updatedAt());
			float agoW = font.width(ago) * 1.5f;
			float whoW = who.isEmpty() ? 0 : font.width(who) * HEAD;
			text(ps, c, ellipsize(font, t.title(), (x1 - tx - whoW - agoW - 36) / HEAD), tx, ey, HEAD, st.text(), light);
			text(ps, c, ago, x1 - agoW, ey + 2, 1.5f, st.muted(), light);
			if (!who.isEmpty()) {
				text(ps, c, who, x1 - agoW - 14 - whoW, ey, HEAD, whoColor, light);
			}
			float ly = ey + 8 * HEAD + 6;
			for (FormattedCharSequence line : e.lines) {
				ps.pushPose();
				ps.translate(x0, ly, 0);
				ps.scale(BODY, BODY, 1f);
				WorldUi.submitText(ps, c, line, 0, 0, UiStyle.withAlpha(st.result(), 255), light);
				ps.popPose();
				ly += 8 * BODY + 3;
			}
			if (e.cut) {
				text(ps, c, "...", x0, ly - 4, BODY, st.muted(), light);
			}
		}
		if (done.size() > entries.size()) {
			text(ps, c, "+" + (done.size() - entries.size()) + " earlier", x0, footerY, 1.4f, st.muted(), light);
		}
		ps.popPose();
	}

	private record Entry(Task task, List<FormattedCharSequence> lines, boolean cut, float y) {
	}

	private static void text(PoseStack ps, SubmitNodeCollector c, String s, float x, float y, float scale, int color, int light) {
		ps.pushPose();
		ps.translate(x, y, 0);
		ps.scale(scale, scale, 1f);
		WorldUi.submitText(ps, c, s, 0, 0, UiStyle.withAlpha(color, 255), light);
		ps.popPose();
	}

	private static String ellipsize(Font font, String s, float maxUnscaled) {
		if (font.width(s) <= maxUnscaled) {
			return s;
		}
		String t = s;
		while (t.length() > 1 && font.width(t + "...") > maxUnscaled) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "...";
	}

	private static String ago(long ms) {
		long m = Math.max(0, ms / 60000);
		if (m < 1) {
			return "just now";
		}
		if (m < 60) {
			return m + "m ago";
		}
		long hrs = m / 60;
		return hrs < 48 ? hrs + "h ago" : (hrs / 24) + "d ago";
	}
}
