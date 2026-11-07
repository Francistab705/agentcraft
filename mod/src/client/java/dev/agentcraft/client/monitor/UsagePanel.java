package dev.agentcraft.client.monitor;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.agentcraft.client.foreman.Protocol.UsageAgent;
import dev.agentcraft.client.foreman.Protocol.UsageSummary;
import dev.agentcraft.client.foreman.Protocol.UsageTask;
import dev.agentcraft.client.ui.UiStyle;
import dev.agentcraft.client.ui.WorldUi;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.jspecify.annotations.Nullable;

/**
 * The HQ usage board: a monitor panel bound to "usage", the same size and style as the task and
 * summary boards (8 x 4 blocks). Left column: the current 5-hour window (cost, time left, a bar of
 * how much of the window has passed), today, and the last 7 days. Right column: cost per agent over
 * the last 7 days with a bar each, then the costliest jobs. A footer says the figures are estimates,
 * not plan limits, and shows Claude's last limit message if any. Pure drawing: everything comes from
 * the {@link UsageSummary} the Foreman pushes with its status.
 */
final class UsagePanel {
	private static final float FIVE_HOURS_MS = 5 * 3600_000f;

	private UsagePanel() {
	}

	static void draw(PoseStack ps, SubmitNodeCollector c, ScreenStyle st, int ppb, int panelW, int panelH, @Nullable UsageSummary u, int light) {
		float w = panelW * ppb;
		float h = panelH * ppb;
		float b = ppb * 2f / 16f;
		float pad = 14;
		float x0 = b + pad;
		float x1 = w - b - pad;
		DisplayDraw.Rects rects = new DisplayDraw.Rects();
		rects.add(b - 0.5f, b - 0.5f, w - b + 0.5f, h - b + 0.5f, 0, st.bgTop(), st.bgBottom(), light);
		float titleBottom = b + 32;
		rects.add(b - 0.5f, b - 0.5f, w - b + 0.5f, titleBottom, 3 * DisplayDraw.Z_STEP, st.headerBg(), light);
		rects.add(x0, titleBottom, x1, titleBottom + 1, 4 * DisplayDraw.Z_STEP, st.rule(), light);

		float top = titleBottom + 10;
		float gap = 26;
		float colW = x1 - x0 - gap;
		float aW = colW * 0.36f;
		float bW = colW * 0.64f;
		float ax = x0;
		float bx = ax + aW + gap;
		float footerY = h - b - 20;

		if (u == null) {
			rects.submit(ps, c);
			ps.pushPose();
			ps.translate(0, 0, MonitorRenderer.Z_TEXT);
			text(ps, c, "SBC HQ USAGE", x0, b + 8, 2.6f, st.text(), light);
			text(ps, c, "Waiting for usage data.", x0, top + 30, 1.9f, st.muted(), light);
			text(ps, c, "It appears when the real Claude team is running.", x0, top + 56, 1.5f, st.muted(), light);
			ps.popPose();
			return;
		}

		var win = u.fiveHour();
		long now = u.generatedAt();
		float barY = top + 94;
		rects.add(ax, barY, ax + aW, barY + 10, DisplayDraw.Z_STEP, st.rule(), light);
		if (win.active() && win.startedAt() != null && win.resetsAt() != null) {
			float used = Math.max(0f, Math.min(1f, (now - win.startedAt()) / FIVE_HOURS_MS));
			rects.add(ax, barY, ax + aW * used, barY + 10, 2 * DisplayDraw.Z_STEP, used > 0.85f ? st.error() : st.tool(), light);
		}
		List<UsageAgent> agents = u.agents();
		int rows = Math.min(6, agents.size());
		double max = 0.0001;
		for (UsageAgent a : agents) {
			max = Math.max(max, a.weekUsd());
		}
		float rowH = 29;
		float bar0 = bx + 112;
		float bar1 = bx + bW - 100;
		for (int i = 0; i < rows; i++) {
			UsageAgent a = agents.get(i);
			float ry = top + 24 + i * rowH;
			rects.add(bar0, ry + 4, bar1, ry + 17, DisplayDraw.Z_STEP, st.rule(), light);
			rects.add(bar0, ry + 4, bar0 + (float) ((bar1 - bar0) * (a.weekUsd() / max)), ry + 17, 2 * DisplayDraw.Z_STEP, st.name(a.id()), light);
		}
		float jobsY = top + 24 + 6 * rowH + 6;
		rects.add(bx, jobsY - 4, bx + bW, jobsY - 3, 2 * DisplayDraw.Z_STEP, st.rule(), light);
		rects.submit(ps, c);

		ps.pushPose();
		ps.translate(0, 0, MonitorRenderer.Z_TEXT);
		text(ps, c, "SBC HQ USAGE", x0, b + 8, 2.6f, st.text(), light);
		String right = "estimates, not plan limits";
		text(ps, c, right, x1 - width(right) * 1.7f, b + 11, 1.7f, st.muted(), light);

		// left column: current window, today, last 7 days
		text(ps, c, "CURRENT 5-HOUR WINDOW", ax, top, 1.5f, st.muted(), light);
		text(ps, c, usd(win.costUsd()), ax, top + 22, 4.4f, win.active() ? st.text() : st.muted(), light);
		String when = win.active() && win.resetsAt() != null ? "resets in " + left(win.resetsAt() - now) : "no activity in the last 5 hours";
		text(ps, c, when, ax, top + 64, 2.0f, st.muted(), light);
		text(ps, c, "TODAY", ax, barY + 22, 1.7f, st.muted(), light);
		text(ps, c, usd(u.today().costUsd()), ax + 92, barY + 18, 2.6f, st.text(), light);
		text(ps, c, "LAST 7 DAYS", ax, barY + 54, 1.7f, st.muted(), light);
		text(ps, c, usd(u.week().costUsd()), ax, barY + 72, 3.4f, st.text(), light);
		text(ps, c, u.week().turns() + " steps", ax, barY + 104, 1.8f, st.muted(), light);

		// right column: per agent, then the costliest jobs
		text(ps, c, "PER AGENT, LAST 7 DAYS", bx, top, 1.5f, st.muted(), light);
		for (int i = 0; i < rows; i++) {
			UsageAgent a = agents.get(i);
			float ry = top + 24 + i * rowH;
			text(ps, c, cap(a.id()), bx, ry + 2, 2.0f, st.name(a.id()), light);
			String v = usd(a.weekUsd());
			text(ps, c, v, bx + bW - width(v) * 2.0f, ry + 2, 2.0f, st.text(), light);
		}
		if (rows == 0) {
			text(ps, c, "No usage recorded yet.", bx, top + 28, 2.0f, st.muted(), light);
		}
		text(ps, c, "COSTLIEST JOBS", bx, jobsY, 1.5f, st.muted(), light);
		List<UsageTask> tasks = u.tasks();
		for (int i = 0; i < Math.min(3, tasks.size()); i++) {
			UsageTask t = tasks.get(i);
			float ty = jobsY + 20 + i * 22;
			text(ps, c, ellipsize(t.key(), 24), bx, ty, 1.7f, st.text(), light);
			String v = usd(t.costUsd());
			text(ps, c, v, bx + bW - width(v) * 1.7f, ty, 1.7f, st.muted(), light);
		}

		String foot = "Estimated cost: what this work would cost on the API. The 5-hour window is estimated from when work started.";
		if (u.lastLimit() != null) {
			var l = u.lastLimit();
			foot = "Claude limit message: " + l.type() + " " + l.status()
				+ (l.utilization() != null ? " (" + Math.round(l.utilization() * 100) + "% used)" : "") + ".  " + foot;
		}
		text(ps, c, ellipsize(foot, 100), x0, footerY, 1.3f, st.muted(), light);
		ps.popPose();
	}

	private static void text(PoseStack ps, SubmitNodeCollector c, String s, float x, float y, float scale, int color, int light) {
		ps.pushPose();
		ps.translate(x, y, 0);
		ps.scale(scale, scale, 1f);
		WorldUi.submitText(ps, c, s, 0, 0, UiStyle.withAlpha(color, 255), light);
		ps.popPose();
	}

	private static float width(String s) {
		return Minecraft.getInstance().font.width(s);
	}

	private static String usd(double v) {
		return String.format(Locale.ROOT, "$%.2f", v);
	}

	private static String cap(String id) {
		return id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1);
	}

	private static String left(long ms) {
		long m = Math.max(0, ms / 60000);
		return (m / 60) + "h " + (m % 60) + "m";
	}

	private static String ellipsize(String s, int max) {
		return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "...";
	}
}
