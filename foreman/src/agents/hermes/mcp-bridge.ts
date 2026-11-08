// The AgentCraft team tools as a tiny MCP server over HTTP (streamable HTTP, JSON replies only) on
// 127.0.0.1, for one Hermes turn. Hermes runs inside the user's gateway process, so it cannot be
// handed in-process tools like Claude or dynamic tools like Codex: it gets this URL in session/new
// and calls the tools like any MCP server. A random bearer token keeps other local programs out.
import crypto from 'node:crypto';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import { z } from 'zod';
import type { AgentTool } from '../tools.js';

export const MCP_NAME = 'agentcraft';

export interface McpBridge {
  /** plain HTTP route for a client without MCP: POST <callUrl>/<tool> with the JSON arguments, bearer token */
  callUrl: string;
  token: string;
  /** the ACP `mcpServers` entry for this bridge */
  server: { type: 'http'; name: string; url: string; headers: Array<{ name: string; value: string }> };
  close(): void;
}

/* eslint-disable @typescript-eslint/no-explicit-any */
export async function startMcpBridge(tools: AgentTool[]): Promise<McpBridge> {
  const token = crypto.randomBytes(24).toString('hex');
  const byName = new Map(tools.map((t) => [t.name, t]));

  const handle = async (msg: any): Promise<any | undefined> => {
    const { id, method, params } = msg ?? {};
    const ok = (result: unknown) => ({ jsonrpc: '2.0', id, result });
    const err = (code: number, message: string) => ({ jsonrpc: '2.0', id, error: { code, message } });
    if (id === undefined) return undefined; // a notification (notifications/initialized...)
    switch (method) {
      case 'initialize':
        return ok({ protocolVersion: params?.protocolVersion ?? '2025-03-26', capabilities: { tools: {} }, serverInfo: { name: MCP_NAME, version: '0.1.0' } });
      case 'ping':
        return ok({});
      case 'tools/list':
        return ok({ tools: tools.map((t) => ({ name: t.name, description: t.description, inputSchema: z.toJSONSchema(z.object(t.shape)) })) });
      case 'tools/call': {
        const t = byName.get(String(params?.name));
        if (!t) return err(-32602, `Unknown tool ${params?.name}. Available: ${[...byName.keys()].join(', ')}`);
        const parsed = z.object(t.shape).safeParse(params?.arguments ?? {});
        if (!parsed.success) {
          return ok({ content: [{ type: 'text', text: `Invalid arguments for ${t.name}: ${parsed.error.issues.map((i) => `${i.path.join('.') || 'input'}: ${i.message}`).join('; ')}` }], isError: true });
        }
        try {
          return ok(await t.handler(parsed.data));
        } catch (e) {
          return ok({ content: [{ type: 'text', text: e instanceof Error ? e.message : String(e) }], isError: true });
        }
      }
      default:
        return err(-32601, `unsupported: ${method}`);
    }
  };

  const server = http.createServer((req, res) => {
    if (req.headers.authorization !== `Bearer ${token}`) {
      res.writeHead(401).end();
      return;
    }
    if (req.method !== 'POST') {
      res.writeHead(405, { allow: 'POST' }).end();
      return;
    }
    const chunks: Buffer[] = [];
    req.on('data', (c: Buffer) => chunks.push(c));
    req.on('end', () => {
      void (async () => {
        const direct = /^\/call\/([A-Za-z0-9_]+)$/.exec(req.url ?? '');
        if (direct) {
          let args: unknown = {};
          try {
            const raw = Buffer.concat(chunks).toString('utf8').trim();
            if (raw) args = JSON.parse(raw);
          } catch {
            res.writeHead(400, { 'content-type': 'text/plain' }).end('ERROR: the body must be a JSON object with the tool arguments');
            return;
          }
          const r: any = (await handle({ jsonrpc: '2.0', id: 1, method: 'tools/call', params: { name: direct[1], arguments: args } })) ?? {};
          const out = r.result ?? { content: [{ type: 'text', text: r.error?.message ?? 'error' }], isError: true };
          res.writeHead(200, { 'content-type': 'text/plain' }).end(`${out.isError ? 'ERROR: ' : ''}${(out.content ?? []).map((c: any) => c.text).join('\n')}`);
          return;
        }
        let body: any;
        try {
          body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        } catch {
          res.writeHead(400).end();
          return;
        }
        const replies = (await Promise.all((Array.isArray(body) ? body : [body]).map(handle))).filter((r) => r !== undefined);
        if (!replies.length) {
          res.writeHead(202).end();
          return;
        }
        res.writeHead(200, { 'content-type': 'application/json' }).end(JSON.stringify(Array.isArray(body) ? replies : replies[0]));
      })();
    });
  });
  await new Promise<void>((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  const port = (server.address() as AddressInfo).port;
  return {
    callUrl: `http://127.0.0.1:${port}/call`,
    token,
    server: { type: 'http', name: MCP_NAME, url: `http://127.0.0.1:${port}/mcp`, headers: [{ name: 'Authorization', value: `Bearer ${token}` }] },
    close: () => {
      server.close();
      server.closeAllConnections?.();
    },
  };
}
