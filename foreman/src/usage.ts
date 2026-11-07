// Usage ledger: every agent turn's estimated cost, with the time it happened, so the Foreman can answer
// "how much in the current 5-hour window / this week, and who used it".
//
// The cost is Claude's own estimate of what the work would cost on the API (total_cost_usd). On a
// subscription login it is NOT the share of your plan's limits: it is good for comparing agents and tasks.
// The only real quota signal is the SDK's rate_limit_event, which we record too (usually only near a limit).
import fs from 'node:fs';
import path from 'node:path';

export const FIVE_HOURS_MS = 5 * 3600_000;
export const WEEK_MS = 7 * 24 * 3600_000;
const DAY_MS = 24 * 3600_000;

export interface UsageEntry {
  ts: number;
  /** agent id, e.g. "juniper" */
  agent: string;
  /** session key "<agent>:<task or goal>" */
  key: string;
  model?: string;
  /** estimated cost of this slice, in USD */
  costUsd: number;
  turns: number;
  /** imported from the per-session totals the first time the ledger was created (time is approximate) */
  backfill?: boolean;
}

export interface LimitEvent {
  ts: number;
  agent: string;
  /** e.g. five_hour, seven_day */
  type: string;
  /** allowed | allowed_warning | rejected */
  status: string;
  /** 0-1 when Claude reports it */
  utilization?: number;
}

export interface UsageAgent {
  id: string;
  model?: string;
  fiveHourUsd: number;
  todayUsd: number;
  weekUsd: number;
  weekTurns: number;
}

export interface UsageSummary {
  generatedAt: number;
  fiveHour: { active: boolean; startedAt: number | null; resetsAt: number | null; costUsd: number; turns: number };
  today: { costUsd: number };
  week: { since: number; costUsd: number; turns: number };
  agents: UsageAgent[];
  /** most expensive sessions in the last 7 days */
  tasks: { key: string; agent: string; costUsd: number }[];
  lastLimit: LimitEvent | null;
  /** plain-English caveat shown next to the numbers */
  note: string;
}

const NOTE =
  'Dollar figures are estimates of what this work would cost on the API. They are for comparing agents and tasks, not your exact share of the plan limits.';

const round = (n: number) => Math.round(n * 1000) / 1000;

export class UsageLedger {
  readonly entries: UsageEntry[] = [];
  readonly limits: LimitEvent[] = [];

  constructor(private readonly file: string) {
    this.load();
  }

  get exists(): boolean {
    return fs.existsSync(this.file);
  }

  private load(): void {
    let text = '';
    try {
      text = fs.readFileSync(this.file, 'utf8');
    } catch {
      return;
    }
    for (const line of text.split('\n')) {
      if (!line.trim()) continue;
      try {
        const o = JSON.parse(line) as { k: string } & Record<string, unknown>;
        if (o.k === 'use') this.entries.push(o as unknown as UsageEntry);
        else if (o.k === 'limit') this.limits.push(o as unknown as LimitEvent);
      } catch {
        // a half-written last line: ignore it
      }
    }
  }

  private append(kind: 'use' | 'limit', rec: object): void {
    try {
      fs.mkdirSync(path.dirname(this.file), { recursive: true });
      fs.appendFileSync(this.file, JSON.stringify({ k: kind, ...rec }) + '\n');
    } catch {
      // the ledger must never break an agent turn
    }
  }

  record(e: UsageEntry): void {
    if (!(e.costUsd > 0) && !(e.turns > 0)) return;
    const entry = { ...e, costUsd: round(e.costUsd) };
    this.entries.push(entry);
    this.append('use', entry);
  }

  recordLimit(l: LimitEvent): void {
    this.limits.push(l);
    this.append('limit', l);
  }

  /** One-off import of the per-session totals the Foreman kept before the ledger existed. */
  backfill(sessions: Record<string, { turns?: number; costUsd?: number; updatedAt?: number; model?: string }>): number {
    let n = 0;
    for (const [key, s] of Object.entries(sessions)) {
      if (!(s.costUsd && s.costUsd > 0)) continue;
      this.record({ ts: s.updatedAt ?? Date.now(), agent: key.split(':')[0] ?? key, key, model: s.model, costUsd: s.costUsd, turns: s.turns ?? 0, backfill: true });
      n++;
    }
    return n;
  }

  summary(now = Date.now()): UsageSummary {
    const sorted = [...this.entries].sort((a, b) => a.ts - b.ts);
    // the 5-hour window opens with the first use after the previous window ran out
    let start: number | null = null;
    const block: UsageEntry[] = [];
    for (const e of sorted) {
      if (start === null || e.ts >= start + FIVE_HOURS_MS) {
        start = e.ts;
        block.length = 0;
      }
      block.push(e);
    }
    const active = start !== null && now < start + FIVE_HOURS_MS;
    const fiveHourEntries = active ? block : [];
    const weekSince = now - WEEK_MS;
    const dayStart = new Date(now);
    dayStart.setHours(0, 0, 0, 0);
    const today = sorted.filter((e) => e.ts >= dayStart.getTime());
    const week = sorted.filter((e) => e.ts >= weekSince);
    const sum = (xs: UsageEntry[]) => xs.reduce((t, e) => t + e.costUsd, 0);

    const ids = [...new Set(week.map((e) => e.agent))];
    const agents: UsageAgent[] = ids.map((id) => {
      const mine = (xs: UsageEntry[]) => xs.filter((e) => e.agent === id);
      const w = mine(week);
      return {
        id,
        model: [...w].reverse().find((e) => e.model)?.model,
        fiveHourUsd: round(sum(mine(fiveHourEntries))),
        todayUsd: round(sum(mine(today))),
        weekUsd: round(sum(w)),
        weekTurns: w.reduce((t, e) => t + e.turns, 0),
      };
    }).sort((a, b) => b.weekUsd - a.weekUsd);

    const byKey = new Map<string, { agent: string; costUsd: number }>();
    for (const e of week) {
      const cur = byKey.get(e.key) ?? { agent: e.agent, costUsd: 0 };
      cur.costUsd += e.costUsd;
      byKey.set(e.key, cur);
    }
    const tasks = [...byKey.entries()].map(([key, v]) => ({ key, agent: v.agent, costUsd: round(v.costUsd) })).sort((a, b) => b.costUsd - a.costUsd).slice(0, 8);

    return {
      generatedAt: now,
      fiveHour: { active, startedAt: active ? start : null, resetsAt: active && start !== null ? start + FIVE_HOURS_MS : null, costUsd: round(sum(fiveHourEntries)), turns: fiveHourEntries.reduce((t, e) => t + e.turns, 0) },
      today: { costUsd: round(sum(today)) },
      week: { since: weekSince, costUsd: round(sum(week)), turns: week.reduce((t, e) => t + e.turns, 0) },
      agents,
      tasks,
      lastLimit: this.limits.length ? this.limits[this.limits.length - 1]! : null,
      note: NOTE,
    };
  }
}

export { DAY_MS };
