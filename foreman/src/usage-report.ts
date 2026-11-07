// Usage report: reads <home>/<profile>/usage.jsonl and prints a summary, and writes usage.html (a
// self-contained dashboard) next to it.  npm run usage -- --home <dir> --profile claude [--open]
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { FIVE_HOURS_MS, UsageLedger, type UsageSummary } from './usage.js';

function arg(name: string, fallback: string): string {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1]! : fallback;
}

const home = path.resolve(arg('home', process.env.AGENTCRAFT_HOME ?? path.join(os.homedir(), '.agentcraft')));
const profile = arg('profile', process.env.AGENTCRAFT_PROFILE ?? 'claude');
const dir = path.join(home, profile);
const ledger = new UsageLedger(path.join(dir, 'usage.jsonl'));
if (!ledger.exists) {
  // first run: import the per-session totals the Foreman kept before the ledger existed (same as the Foreman does)
  try {
    const state = JSON.parse(fs.readFileSync(path.join(dir, 'state.json'), 'utf8')) as { sessions?: Record<string, never> };
    ledger.backfill(state.sessions ?? {});
  } catch {
    // no state yet: an empty report
  }
}
const sum = ledger.summary();

const usd = (n: number) => `$${n.toFixed(2)}`;
const when = (ms: number | null) => (ms ? new Date(ms).toLocaleString() : 'none');
const left = (ms: number | null) => {
  if (!ms) return '';
  const m = Math.max(0, Math.round((ms - Date.now()) / 60000));
  return `${Math.floor(m / 60)}h ${m % 60}m left`;
};

console.log(`Usage for ${dir}`);
console.log(`  Current 5-hour window: ${sum.fiveHour.active ? `${usd(sum.fiveHour.costUsd)} (started ${when(sum.fiveHour.startedAt)}, resets ${when(sum.fiveHour.resetsAt)}, ${left(sum.fiveHour.resetsAt)})` : 'no activity in the last 5 hours'}`);
console.log(`  Today: ${usd(sum.today.costUsd)}   Last 7 days: ${usd(sum.week.costUsd)} over ${sum.week.turns} steps`);
for (const a of sum.agents) console.log(`    ${a.id.padEnd(10)} week ${usd(a.weekUsd).padStart(7)}  today ${usd(a.todayUsd).padStart(7)}  5h ${usd(a.fiveHourUsd).padStart(7)}  ${a.model ?? ''}`);
if (sum.lastLimit) console.log(`  Last limit message: ${sum.lastLimit.type} ${sum.lastLimit.status}${sum.lastLimit.utilization !== undefined ? ` ${Math.round(sum.lastLimit.utilization * 100)}%` : ''} at ${when(sum.lastLimit.ts)}`);
console.log(`  ${sum.note}`);

const out = path.join(dir, 'usage.html');
fs.writeFileSync(out, html(sum));
console.log(`Dashboard: ${out}`);
if (process.argv.includes('--open')) spawnSync(process.platform === 'darwin' ? 'open' : 'xdg-open', [out], { stdio: 'ignore' });

function html(s: UsageSummary): string {
  const data = JSON.stringify(s).replace(/</g, '\\u003c');
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>SBC HQ Usage</title>
<style>
:root{--bg:#f6f3ee;--card:#fff;--ink:#2a2622;--muted:#6b645c;--line:#ddd6cb;--bar:#9a4a2f;--bar2:#d6a560;--ok:#3d7a4a}
@media (prefers-color-scheme:dark){:root{--bg:#1d1a17;--card:#26221e;--ink:#efe9e1;--muted:#a79f95;--line:#3a342e;--bar:#e08a62;--bar2:#c29a5b;--ok:#7fb589}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif}
.wrap{max-width:960px;margin:0 auto;padding:28px 16px 56px}h1{font-size:1.6rem;margin:0 0 4px}h2{font-size:1.05rem;margin:28px 0 10px}
.muted{color:var(--muted)}.small{font-size:.85rem}.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}
@media(max-width:700px){.grid{grid-template-columns:1fr}}
.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:16px}.big{font-size:2rem;font-weight:700;line-height:1.1}
.row{display:grid;grid-template-columns:90px 1fr 70px;gap:10px;align-items:center;margin:8px 0}.track{background:var(--line);border-radius:6px;height:14px;overflow:hidden}
.fill{height:100%;background:var(--bar);border-radius:6px}.fill.b{background:var(--bar2)}.num{text-align:right;font-variant-numeric:tabular-nums}
table{width:100%;border-collapse:collapse}td,th{padding:6px 4px;text-align:left;border-bottom:1px solid var(--line)}td:last-child,th:last-child{text-align:right}
.note{border-left:4px solid var(--bar);padding:8px 12px;background:var(--card);border-radius:0 8px 8px 0}
</style></head><body><div class="wrap">
<h1>SBC HQ usage</h1><div class="muted" id="gen"></div>
<h2>Right now</h2><div class="grid">
<div class="card"><div class="muted small">Current 5-hour window</div><div class="big" id="w5"></div><div class="small muted" id="w5s"></div><div class="track" style="margin-top:10px"><div class="fill b" id="w5bar" style="width:0%"></div></div><div class="small muted" id="w5t"></div></div>
<div class="card"><div class="muted small">Today</div><div class="big" id="today"></div></div>
<div class="card"><div class="muted small">Last 7 days</div><div class="big" id="week"></div><div class="small muted" id="weeks"></div></div></div>
<h2>Who used it (last 7 days)</h2><div class="card" id="agents"></div>
<h2>Most expensive jobs (last 7 days)</h2><div class="card"><table><thead><tr><th>Job</th><th>Agent</th><th>Estimated cost</th></tr></thead><tbody id="tasks"></tbody></table></div>
<h2>Limit messages from Claude</h2><div class="card small" id="limit"></div>
<p class="note small" id="note" style="margin-top:20px"></p>
<p class="muted small">Refresh by running <code>node tools/usage.mjs --open</code> again. The 5-hour window is estimated from when work started; the week is the last 7 days.</p>
</div><script>
const S=${data};const $=id=>document.getElementById(id);const usd=n=>'$'+n.toFixed(2);
const t=ms=>ms?new Date(ms).toLocaleString():'none';
$('gen').textContent='As of '+t(S.generatedAt);
const f=S.fiveHour;$('w5').textContent=usd(f.costUsd);
if(f.active){const m=Math.max(0,Math.round((f.resetsAt-S.generatedAt)/60000));$('w5s').textContent='Started '+t(f.startedAt);$('w5t').textContent='Resets '+t(f.resetsAt)+' ('+Math.floor(m/60)+'h '+m%60+'m left)';$('w5bar').style.width=Math.min(100,100-m/3)+'%'}else{$('w5s').textContent='No activity in the last 5 hours'}
$('today').textContent=usd(S.today.costUsd);$('week').textContent=usd(S.week.costUsd);$('weeks').textContent=S.week.turns+' steps';
const max=Math.max(0.0001,...S.agents.map(a=>a.weekUsd));
$('agents').innerHTML=S.agents.length?S.agents.map(a=>'<div class="row"><div><b>'+a.id+'</b><div class="muted small">'+(a.model||'')+'</div></div><div class="track"><div class="fill" style="width:'+(a.weekUsd/max*100)+'%"></div></div><div class="num">'+usd(a.weekUsd)+'</div></div>').join(''):'<span class="muted">No usage recorded yet.</span>';
$('tasks').innerHTML=S.tasks.map(x=>'<tr><td>'+x.key+'</td><td>'+x.agent+'</td><td>'+usd(x.costUsd)+'</td></tr>').join('')||'<tr><td colspan="3" class="muted">Nothing yet.</td></tr>';
$('limit').textContent=S.lastLimit?(S.lastLimit.type+' - '+S.lastLimit.status+(S.lastLimit.utilization!==undefined?' ('+Math.round(S.lastLimit.utilization*100)+'% used)':'')+' at '+t(S.lastLimit.ts)):'None yet. Claude usually only sends these when you get close to a limit.';
$('note').textContent=S.note;
</script></body></html>`;
}

void FIVE_HOURS_MS;
