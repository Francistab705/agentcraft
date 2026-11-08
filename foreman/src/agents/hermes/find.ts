// Where is the Hermes CLI? An explicit path (--hermes-path / AGENTCRAFT_HERMES_PATH), then `hermes` on PATH.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';

function onPath(name: string): string | undefined {
  try {
    const out = execFileSync(process.platform === 'win32' ? 'where' : 'which', [name], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], windowsHide: true, timeout: 5000 });
    return out.split(/\r?\n/).map((s) => s.trim()).filter(Boolean)[0];
  } catch {
    return undefined;
  }
}

let cached: { key: string; bin: string | undefined } | undefined;

export function findHermes(explicit?: string): string | undefined {
  const key = explicit ?? '';
  if (cached?.key === key) return cached.bin;
  const bin = explicit ? (fs.existsSync(explicit) ? explicit : onPath(explicit)) : onPath('hermes');
  cached = { key, bin };
  return bin;
}

export const HERMES_NOT_FOUND = 'Hermes CLI not found. Put `hermes` on your PATH or pass --hermes-path <path to hermes>.';
