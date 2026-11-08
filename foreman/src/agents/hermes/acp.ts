// A minimal Agent Client Protocol client for `hermes acp`: JSON-RPC 2.0, one JSON object per line on
// stdio. The agent sends notifications (session/update) and requests of its own (session/request_permission),
// which `onRequest` answers.
import { spawn, type ChildProcess } from 'node:child_process';
import readline from 'node:readline';

type Json = unknown;
type Pending = { resolve(v: Json): void; reject(e: Error): void; method: string };

export class AcpError extends Error {
  constructor(
    readonly method: string,
    readonly code: number | undefined,
    message: string,
    readonly data?: unknown,
  ) {
    super(`${method}: ${message}`);
  }
}

export interface AcpOptions {
  cwd: string;
  env: NodeJS.ProcessEnv;
  /** arguments before `acp` (Hermes' global flags, e.g. --safe-mode -m model --provider p) */
  args: string[];
  onStderr?(text: string): void;
}

export class AcpClient {
  readonly child: ChildProcess;
  private nextId = 1;
  private pending = new Map<number, Pending>();
  private notificationHandlers: Array<(method: string, params: Json) => void> = [];
  private requestHandler: ((method: string, params: Json) => Promise<Json>) | undefined;
  private exitError: Error | undefined;
  private stderrTail: string[] = [];
  /** settles when the process has exited */
  readonly exited: Promise<{ code: number | null; signal: NodeJS.Signals | null }>;

  constructor(bin: string, opts: AcpOptions) {
    this.child = spawn(bin, [...opts.args, 'acp'], { cwd: opts.cwd, env: opts.env, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
    this.child.stdin?.on('error', () => undefined); // EPIPE after exit: the exit handler reports it
    this.child.stderr?.setEncoding('utf8');
    this.child.stderr?.on('data', (s: string) => {
      for (const line of s.split(/\r?\n/).filter((l) => l.trim())) {
        this.stderrTail.push(line);
        if (this.stderrTail.length > 20) this.stderrTail.shift();
      }
      opts.onStderr?.(s);
    });
    this.exited = new Promise((resolve) => {
      this.child.once('error', (e) => {
        this.fail(new Error(`could not start hermes: ${e.message}`));
        resolve({ code: null, signal: null });
      });
      this.child.once('exit', (code, signal) => {
        this.fail(new Error(`hermes acp exited (${signal ?? code})${this.stderrTail.length ? `: ${this.stderrTail.slice(-3).join(' | ')}` : ''}`));
        resolve({ code, signal });
      });
    });
    readline.createInterface({ input: this.child.stdout! }).on('line', (line) => this.onLine(line));
  }

  /** The last lines the process wrote to stderr (for error messages). */
  stderr(): string[] {
    return [...this.stderrTail];
  }

  onNotification(handler: (method: string, params: Json) => void): void {
    this.notificationHandlers.push(handler);
  }

  onRequest(handler: (method: string, params: Json) => Promise<Json>): void {
    this.requestHandler = handler;
  }

  request<T = Json>(method: string, params: Json, timeoutMs = 60_000): Promise<T> {
    if (this.exitError) return Promise.reject(this.exitError);
    const id = this.nextId++;
    return new Promise<T>((resolve, reject) => {
      const timer = timeoutMs > 0 ? setTimeout(() => {
        this.pending.delete(id);
        reject(new AcpError(method, undefined, `no answer after ${Math.round(timeoutMs / 1000)}s`));
      }, timeoutMs) : undefined;
      timer?.unref?.();
      this.pending.set(id, {
        method,
        resolve: (v) => {
          if (timer) clearTimeout(timer);
          resolve(v as T);
        },
        reject: (e) => {
          if (timer) clearTimeout(timer);
          reject(e);
        },
      });
      this.write({ jsonrpc: '2.0', id, method, params });
    });
  }

  notify(method: string, params?: Json): void {
    this.write({ jsonrpc: '2.0', method, ...(params === undefined ? {} : { params }) });
  }

  /** End the process (the team also ends its whole process tree when a turn is stopped). */
  close(): void {
    try {
      this.child.stdin?.end();
    } catch {
      /* ignore */
    }
    if (this.child.exitCode === null && this.child.signalCode === null) this.child.kill();
  }

  private write(msg: Json): void {
    if (this.exitError || !this.child.stdin?.writable) return;
    this.child.stdin.write(`${JSON.stringify(msg)}\n`);
  }

  private fail(e: Error): void {
    this.exitError ??= e;
    for (const p of this.pending.values()) p.reject(e);
    this.pending.clear();
  }

  private onLine(line: string): void {
    let m: { id?: number | string; method?: string; params?: Json; result?: Json; error?: { code?: number; message?: string; data?: { details?: string } } };
    try {
      m = JSON.parse(line);
    } catch {
      return; // not a protocol line
    }
    if (m.method === undefined && m.id !== undefined) {
      const p = this.pending.get(Number(m.id));
      if (!p) return;
      this.pending.delete(Number(m.id));
      if (m.error) p.reject(new AcpError(p.method, m.error.code, m.error.data?.details ?? m.error.message ?? 'error', m.error.data));
      else p.resolve(m.result);
      return;
    }
    if (m.method !== undefined && m.id !== undefined) {
      const id = m.id;
      const handler = this.requestHandler;
      if (!handler) {
        this.write({ jsonrpc: '2.0', id, error: { code: -32601, message: `unsupported: ${m.method}` } });
        return;
      }
      handler(m.method, m.params)
        .then((result) => this.write({ jsonrpc: '2.0', id, result }))
        .catch((e: Error) => this.write({ jsonrpc: '2.0', id, error: { code: -32000, message: e.message } }));
      return;
    }
    if (m.method !== undefined) for (const h of this.notificationHandlers) h(m.method, m.params);
  }
}
