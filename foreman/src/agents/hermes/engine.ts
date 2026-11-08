// The Hermes engine: the user's own Hermes, reached through the Agent Client Protocol (`hermes acp`),
// one acp process per turn. Hermes runs inside the user's Hermes gateway (the same one the desktop
// app uses); an agent session is created in Hermes' safe mode, so none of the user's Hermes config,
// memory, plugins or MCP servers (Gmail, Firefly...) are loaded.
//
// What this engine can and cannot promise, unlike Claude and Codex:
// - Hermes asks (through session/request_permission) before file edits and risky commands; each ask goes
//   through AgentCraft's policy and, if needed, an in-game prompt.
// - Hermes' terminal is NOT checked by AgentCraft's policy (Hermes runs most commands without asking, and
//   it may do an edit it was denied through the terminal instead). Treat a Hermes agent like Hermes itself.
// - Hermes does not run in the Foreman's process, so the git safety environment cannot be handed to it;
//   it is given in the instructions instead.
// - the team tools (send_message, ask_user, update_task...) are an MCP server on 127.0.0.1 (mcp-bridge.ts)
import os from 'node:os';
import path from 'node:path';
import type { HermesConfig } from '../../config.js';
import type { Foreman } from '../../foreman.js';
import { firstLine, headLines, truncate } from '../../util/text.js';
import { userName } from '../../user.js';
import { toolActivity } from '../activity.js';
import type { AuthCheck, Engine, Role, TurnSpec, TurnStats } from '../engine.js';
import { modelLabel } from '../models.js';
import type { AgentTool } from '../tools.js';
import { AcpClient } from './acp.js';
import { HERMES_NOT_FOUND, findHermes } from './find.js';
import { MCP_NAME, startMcpBridge } from './mcp-bridge.js';

/* eslint-disable @typescript-eslint/no-explicit-any */

const DEFAULT_MODEL = 'gpt-5.6-sol';
const DEFAULT_PROVIDER = 'openai-codex';
const CLIENT_INFO = { name: 'agentcraft', title: 'AgentCraft', version: '0.1.0' };

function errorText(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/** MCP tools need Hermes to hand the editor's servers to its background worker, which this Hermes build does not do (launch_credentials_unavailable); until it does, the tools are plain HTTP calls from the terminal. AGENTCRAFT_HERMES_MCP=1 switches back. */
const USE_MCP = process.env.AGENTCRAFT_HERMES_MCP === '1';

function notes(role: Role, tools: AgentTool[], env: Record<string, string | undefined>, bridge: { callUrl: string; token: string }): string {
  const git = Object.entries(env)
    .filter(([k, v]) => v !== undefined && k.startsWith('GIT_'))
    .map(([k, v]) => `${k}=${JSON.stringify(v)}`)
    .join(' ');
  return [
    '',
    '# Working in AgentCraft (Hermes)',
    USE_MCP
      ? `- The AgentCraft team tools (${tools.map((t) => t.name).join(', ')}) come from the "${MCP_NAME}" MCP server: call them directly. They are how the team and ${userName()} see your work; plain text only shows on your monitor.`
      : [
          `- The AgentCraft team tools (${tools.map((t) => t.name).join(', ')}) are how the team and ${userName()} see your work; plain text only shows on your monitor. You have no tool by those names: call one from the terminal, with the arguments as JSON on stdin, and read its reply:`,
          `  curl -s -X POST -H "Authorization: Bearer ${bridge.token}" --data-binary @- ${bridge.callUrl}/<tool_name> <<'JSON'`,
          '  {"argument": "value"}',
          '  JSON',
          '  Example: tool_name update_task with {"task_id": "t2", "status": "review"}. A reply starting with ERROR: failed; fix the arguments and retry. Always finish a task by calling update_task (status review) and send_message to your lead.',
        ].join('\n'),
    '- Work only inside your current directory. Do not use memory, skills, cron jobs, delegation or other agents; ask questions only with the ask_user tool.',
    git ? `- Prefix every git command with these variables: ${git}` : '',
    `- Never push, never touch another branch or another directory. ${userName()} sees your file edits and may deny them; never work around a denial (for example by writing the file from the terminal): find another way or use ask_user.`,
    role === 'lead' ? '- You are read-only: never modify files or run commands that change anything.' : '',
  ]
    .filter((l) => l !== '')
    .join('\n');
}

/** What an ACP tool call is, in the Claude tool vocabulary the policy and the activity labels know. */
function claudeTool(tc: any): { tool: string; input: Record<string, unknown> } {
  const raw = tc?.rawInput?.arguments ?? {};
  const loc = tc?.locations?.[0]?.path ?? tc?.content?.find?.((c: any) => c?.type === 'diff')?.path ?? raw.path ?? raw.file_path;
  const text = (tc?.content ?? []).map((c: any) => c?.content?.text ?? c?.text ?? '').find((t: string) => typeof t === 'string' && t.startsWith('$ '));
  const command = raw.command ?? (text ? text.replace(/^\$ /, '') : String(tc?.title ?? '').replace(/^terminal:\s*/, ''));
  switch (tc?.kind) {
    case 'read':
      return { tool: 'Read', input: { file_path: loc } };
    case 'search':
      return { tool: 'Grep', input: { pattern: String(tc?.title ?? '').replace(/^[^:]*:\s*/, ''), path: loc } };
    case 'edit':
      return { tool: raw.old_string === undefined && tc?.rawInput?.tool === 'write_file' ? 'Write' : 'Edit', input: { file_path: loc } };
    case 'delete':
    case 'move':
      return { tool: 'Edit', input: { file_path: loc } };
    case 'fetch':
      return { tool: 'WebFetch', input: { url: String(tc?.title ?? '').replace(/^[^:]*:\s*/, '') } };
    case 'execute': {
      // a team tool called through the HTTP bridge: show it as the tool, like Claude's and Codex's
      const viaBridge = /\/call\/([A-Za-z0-9_]+)/.exec(String(command));
      if (viaBridge) {
        let input: Record<string, unknown> = {};
        const json = /<<'?JSON'?\s*([\s\S]*?)\s*\nJSON/.exec(String(command))?.[1];
        try {
          if (json) input = JSON.parse(json);
        } catch {
          /* keep the empty input */
        }
        return { tool: viaBridge[1]!, input };
      }
      return { tool: 'Bash', input: { command } };
    }
    default: {
      // our own team tools show up as e.g. "agentcraft: send_message" / "mcp_agentcraft_send_message"
      const name = String(tc?.title ?? '').replace(/^.*?(agentcraft)[_:.\s-]+/i, '').trim();
      return { tool: name || 'tool', input: raw };
    }
  }
}

class HermesMapper {
  readonly stats: TurnStats = { isError: false, errors: [] };
  steps = 0;
  private messageId: string | undefined;
  private message = '';
  private finalText: string | undefined;
  private calls = new Map<string, any>();

  constructor(
    private fm: Foreman,
    private agentId: string,
    private cwd: string,
    private role: Role,
  ) {}

  private set(patch: { state?: any; station?: any; activity?: string }): void {
    const a = this.fm.agent(this.agentId);
    if (a?.state === 'waiting_user' && patch.state !== 'waiting_user') return; // a pending question keeps the agent at the user
    this.fm.setAgent(this.agentId, patch);
  }

  private refreshSoon(): void {
    if (this.role !== 'worker') return;
    const repoId = this.fm.agent(this.agentId)?.repoId;
    if (repoId) this.fm.repos.scheduleRefresh(repoId, 1500);
  }

  /** The agent's message so far is complete: show it. */
  private flushMessage(): void {
    const text = this.message.trim();
    this.message = '';
    if (!text) return;
    this.fm.agentLog(this.agentId, 'text', truncate(text, 1200));
    this.set({ state: 'thinking', activity: firstLine(text, 48) });
    this.finalText = text;
  }

  finish(stopReason: string): void {
    this.flushMessage();
    this.stats.subtype = stopReason === 'end_turn' ? 'success' : stopReason === 'cancelled' ? 'interrupted' : 'error_during_execution';
    this.stats.isError = stopReason !== 'end_turn';
    this.stats.numTurns = this.steps;
    if (this.finalText) this.stats.resultText = this.finalText;
    this.fm.agentLog(this.agentId, this.stats.isError ? 'error' : 'result', `turn ${stopReason === 'end_turn' ? 'complete' : `ended: ${stopReason}`} (${this.steps} steps)`);
  }

  handle(update: any): void {
    const fm = this.fm;
    const id = this.agentId;
    switch (update?.sessionUpdate) {
      case 'agent_message_chunk': {
        const mid = String(update.messageId ?? '');
        if (mid !== this.messageId) {
          this.flushMessage();
          this.messageId = mid;
        }
        this.message += String(update.content?.text ?? '');
        break;
      }
      case 'agent_thought_chunk':
        this.set({ state: 'thinking', activity: 'thinking' });
        break;
      case 'tool_call': {
        this.flushMessage();
        this.calls.set(String(update.toolCallId), update);
        const t = claudeTool(update);
        const act = toolActivity(t.tool, t.input, this.cwd);
        fm.agentLog(id, 'tool', act.label);
        this.set({ state: act.state, station: act.station, activity: act.activity });
        if (t.tool === 'Edit' || t.tool === 'Write' || t.tool === 'Bash') this.refreshSoon();
        break;
      }
      case 'tool_call_update': {
        const status = String(update.status ?? '');
        if (status !== 'completed' && status !== 'failed') break;
        this.steps++;
        const text = (update.content ?? []).map((c: any) => c?.content?.text ?? c?.text ?? '').join('\n').trim();
        if (status === 'failed') fm.agentLog(id, 'error', truncate(text || 'failed', 600));
        else fm.agentLog(id, 'result', headLines(text, 8, 900) || 'ok');
        this.refreshSoon();
        break;
      }
    }
  }
}

export class HermesEngine implements Engine {
  readonly id = 'hermes' as const;
  readonly label = 'Hermes';

  constructor(
    private fm: Foreman,
    private cfg: HermesConfig,
    private opts: { bin?: string; args?: string[] } = {},
  ) {}

  private bin(): string | undefined {
    return this.opts.bin ?? findHermes(this.cfg.path);
  }

  private args(): string[] {
    return ['--safe-mode', '-m', this.cfg.model ?? DEFAULT_MODEL, '--provider', this.cfg.provider ?? DEFAULT_PROVIDER, ...(this.opts.args ?? [])];
  }

  model(_role: Role): string {
    return this.cfg.model ?? DEFAULT_MODEL;
  }

  authFailedMessage(detail: string): string {
    return `Hermes authentication failed (${detail}). Check that Hermes works on its own (\`hermes -z "hi"\`) and that its gateway is running, then restart the Foreman.`;
  }

  private async open(cwd: string, onStderr?: (s: string) => void): Promise<{ client: AcpClient; init: any }> {
    const bin = this.bin();
    if (!bin) throw new Error(HERMES_NOT_FOUND);
    const client = new AcpClient(bin, { cwd, env: process.env, args: this.args(), ...(onStderr ? { onStderr } : {}) });
    try {
      const init = await client.request<any>('initialize', { protocolVersion: 1, clientCapabilities: {}, clientInfo: CLIENT_INFO }, 60_000);
      const method = (init?.authMethods ?? []).find((m: any) => !m.args) ?? init?.authMethods?.[0];
      if (method?.id) await client.request('authenticate', { methodId: method.id }, 60_000);
      return { client, init };
    } catch (e) {
      client.close();
      throw e;
    }
  }

  async checkAuth(): Promise<AuthCheck> {
    if (!this.bin()) return { ok: false, message: HERMES_NOT_FOUND };
    let client: AcpClient | undefined;
    try {
      ({ client } = await this.open(os.homedir()));
      return { ok: true, account: `Hermes (${this.cfg.provider ?? DEFAULT_PROVIDER})` };
    } catch (e) {
      return { ok: false, message: `Hermes check failed: ${errorText(e)}. Check that \`hermes acp --check\` passes and the Hermes gateway is running, then restart the Foreman.` };
    } finally {
      client?.close();
    }
  }

  async runTurn(spec: TurnSpec): Promise<TurnStats> {
    const { agentId, role, cwd, abort } = spec;
    const mapper = new HermesMapper(this.fm, agentId, cwd, role);
    const bridge = await startMcpBridge(spec.tools);
    let client: AcpClient | undefined;
    let sessionId: string | undefined;
    const onAbort = () => {
      if (client && sessionId) client.notify('session/cancel', { sessionId });
      const t = setTimeout(() => client?.close(), 2_000);
      t.unref?.();
    };
    try {
      ({ client } = await this.open(cwd, (s) => this.fm.log.debug(`[${agentId} hermes] ${s.trim().slice(0, 300)}`)));
      spec.onProcess(client.child);
      const c = client;
      c.onNotification((method, params: any) => {
        if (method !== 'session/update' || abort.signal.aborted) return;
        if (params?.sessionId && sessionId && params.sessionId !== sessionId) return;
        mapper.handle(params?.update);
      });
      c.onRequest((method, params) => this.answer(method, params, spec));
      if (abort.signal.aborted) onAbort();
      else abort.signal.addEventListener('abort', onAbort, { once: true });

      const session = { cwd, mcpServers: USE_MCP ? [bridge.server] : [] };
      let resumed = false;
      if (spec.resume) {
        try {
          await c.request('session/resume', { sessionId: spec.resume, ...session }, 60_000);
          sessionId = spec.resume;
          resumed = true;
        } catch (e) {
          this.fm.log.warn(`${agentId}: could not resume Hermes session ${spec.resume} (${errorText(e)}); starting a new one`);
        }
      }
      if (!sessionId) sessionId = (await c.request<any>('session/new', session, 120_000)).sessionId as string;
      mapper.stats.sessionId = sessionId;
      spec.onSession(sessionId);
      spec.onModel?.(modelLabel(this.model(role)) ?? this.model(role));
      if (abort.signal.aborted) return mapper.stats;

      const text = resumed ? spec.prompt : `${spec.instructions}${notes(role, spec.tools, spec.env, bridge)}\n\n---\n\n${spec.prompt}`;
      const died = c.exited.then(() => {
        if (!abort.signal.aborted) throw new Error(`hermes acp exited during the turn${c.stderr().length ? `: ${truncate(c.stderr().slice(-3).join(' | '), 300)}` : ''}`);
        return undefined as never;
      });
      const res = await Promise.race([c.request<any>('session/prompt', { sessionId, prompt: [{ type: 'text', text }] }, 0), died]);
      mapper.finish(String(res?.stopReason ?? 'end_turn'));
      return mapper.stats;
    } catch (e) {
      const msg = errorText(e);
      mapper.stats.errors.push(msg);
      if (/auth|login|credential|401/i.test(msg)) mapper.stats.authFailed = firstLine(msg, 200);
      if (!abort.signal.aborted) throw e;
      mapper.finish('cancelled');
      return mapper.stats;
    } finally {
      abort.signal.removeEventListener('abort', onAbort);
      bridge.close();
      client?.close();
    }
  }

  /** Hermes' own requests: permission to edit or run something (-> policy / the user). */
  private async answer(method: string, params: any, spec: TurnSpec): Promise<unknown> {
    if (method !== 'session/request_permission') throw new Error(`AgentCraft does not handle ${method}`);
    const options: Array<{ optionId: string; kind: string }> = params?.options ?? [];
    const allow = options.find((o) => o.kind === 'allow_once') ?? options.find((o) => o.kind.startsWith('allow'));
    const deny = options.find((o) => o.kind === 'reject_once') ?? options.find((o) => o.kind.startsWith('reject'));
    const t = claudeTool(params?.toolCall);
    if (t.tool === 'Edit' || t.tool === 'Write') t.input.file_path = path.resolve(spec.cwd, String(t.input.file_path ?? ''));
    const r = await spec.permission(t.tool, t.input, spec.abort.signal, params?.toolCall?.title ?? undefined);
    if (r.allow && allow) return { outcome: { outcome: 'selected', optionId: allow.optionId } };
    if (!r.allow && !r.interrupt && deny) return { outcome: { outcome: 'selected', optionId: deny.optionId } };
    return { outcome: { outcome: 'cancelled' } };
  }
}
