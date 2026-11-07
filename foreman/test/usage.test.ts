import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { FIVE_HOURS_MS, UsageLedger, WEEK_MS } from '../src/usage.js';

const H = 3600_000;
function ledger(): UsageLedger {
  return new UsageLedger(path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'usage-')), 'usage.jsonl'));
}

describe('usage ledger', () => {
  it('opens a 5-hour window with the first use and resets it after 5 hours', () => {
    const l = ledger();
    const t0 = Date.UTC(2026, 10, 7, 12, 0, 0);
    l.record({ ts: t0, agent: 'marlow', key: 'marlow:g1', costUsd: 1, turns: 2 });
    l.record({ ts: t0 + 4 * H, agent: 'kit', key: 'kit:t1', costUsd: 2, turns: 3 });
    let s = l.summary(t0 + 4.5 * H);
    expect(s.fiveHour.active).toBe(true);
    expect(s.fiveHour.costUsd).toBe(3);
    expect(s.fiveHour.resetsAt).toBe(t0 + FIVE_HOURS_MS);
    // after the window ran out, the next use starts a fresh one and the old cost no longer counts
    l.record({ ts: t0 + 6 * H, agent: 'kit', key: 'kit:t2', costUsd: 4, turns: 1 });
    s = l.summary(t0 + 6.5 * H);
    expect(s.fiveHour.costUsd).toBe(4);
    expect(s.fiveHour.startedAt).toBe(t0 + 6 * H);
    // and with no new use the window is simply over
    expect(l.summary(t0 + 12 * H).fiveHour.active).toBe(false);
  });

  it('sums the last 7 days per agent, most used first, and ignores older work', () => {
    const l = ledger();
    const now = Date.UTC(2026, 10, 7, 12, 0, 0);
    l.record({ ts: now - WEEK_MS - H, agent: 'old', key: 'old:t0', costUsd: 9, turns: 1 });
    l.record({ ts: now - 2 * H, agent: 'wren', key: 'wren:t1', costUsd: 1, turns: 1 });
    l.record({ ts: now - H, agent: 'marlow', key: 'marlow:g1', costUsd: 3, turns: 1 });
    const s = l.summary(now);
    expect(s.week.costUsd).toBe(4);
    expect(s.agents.map((a) => a.id)).toEqual(['marlow', 'wren']);
    expect(s.tasks[0]?.key).toBe('marlow:g1');
  });

  it('survives a restart and a half-written last line', () => {
    const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'usage-')), 'usage.jsonl');
    const a = new UsageLedger(file);
    a.record({ ts: Date.now(), agent: 'kit', key: 'kit:t1', costUsd: 0.5, turns: 1 });
    fs.appendFileSync(file, '{"k":"use","ts":');
    expect(new UsageLedger(file).entries).toHaveLength(1);
  });
});
