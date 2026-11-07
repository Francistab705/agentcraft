#!/usr/bin/env node
// Usage report: node tools/usage.mjs [--home DIR] [--profile claude] [--open]
// Prints cost per agent for the current 5-hour window, today and the last 7 days, and writes usage.html.
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
if (!args.includes('--home')) {
  const sibling = path.resolve(root, '..', 'agentcraft-home');
  if (fs.existsSync(sibling)) args.push('--home', sibling);
}
const r = spawnSync(process.execPath, ['--import', 'tsx', 'src/usage-report.ts', ...args], { cwd: path.join(root, 'foreman'), stdio: 'inherit' });
process.exit(r.status ?? 1);
