import { afterEach, describe, expect, it } from 'vitest';
import { z } from 'zod';
import { startMcpBridge, type McpBridge } from '../src/agents/hermes/mcp-bridge.js';
import type { AgentTool } from '../src/agents/tools.js';

const echo: AgentTool = {
  name: 'echo',
  description: 'echo back',
  shape: { text: z.string() },
  handler: async (a: { text: string }) => ({ content: [{ type: 'text', text: `you said ${a.text}` }] }),
};

let bridge: McpBridge | undefined;
afterEach(() => {
  bridge?.close();
  bridge = undefined;
});

const post = (url: string, token: string | undefined, body: unknown) =>
  fetch(url, { method: 'POST', headers: { ...(token ? { authorization: `Bearer ${token}` } : {}), 'content-type': 'application/json' }, body: JSON.stringify(body) });

describe('hermes team-tool bridge', () => {
  it('calls a tool through the plain /call route', async () => {
    bridge = await startMcpBridge([echo]);
    const r = await post(`${bridge.callUrl}/echo`, bridge.token, { text: 'hi' });
    expect(r.status).toBe(200);
    expect(await r.text()).toBe('you said hi');
  });

  it('reports bad arguments and unknown tools as ERROR text', async () => {
    bridge = await startMcpBridge([echo]);
    expect(await (await post(`${bridge.callUrl}/echo`, bridge.token, {})).text()).toMatch(/^ERROR: Invalid arguments for echo/);
    expect(await (await post(`${bridge.callUrl}/nope`, bridge.token, {})).text()).toMatch(/^ERROR: Unknown tool nope/);
  });

  it('refuses a missing or wrong token', async () => {
    bridge = await startMcpBridge([echo]);
    expect((await post(`${bridge.callUrl}/echo`, undefined, { text: 'x' })).status).toBe(401);
    expect((await post(`${bridge.callUrl}/echo`, 'wrong', { text: 'x' })).status).toBe(401);
  });

  it('speaks MCP: initialize, tools/list, tools/call', async () => {
    bridge = await startMcpBridge([echo]);
    const url = bridge.server.url;
    const token = bridge.token;
    const init = (await (await post(url, token, { jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: '2025-03-26' } })).json()) as any;
    expect(init.result.serverInfo.name).toBe('agentcraft');
    const list = (await (await post(url, token, { jsonrpc: '2.0', id: 2, method: 'tools/list' })).json()) as any;
    expect(list.result.tools.map((t: { name: string }) => t.name)).toEqual(['echo']);
    const call = (await (await post(url, token, { jsonrpc: '2.0', id: 3, method: 'tools/call', params: { name: 'echo', arguments: { text: 'mcp' } } })).json()) as any;
    expect(call.result.content[0].text).toBe('you said mcp');
    expect((await post(url, token, { jsonrpc: '2.0', method: 'notifications/initialized' })).status).toBe(202);
  });
});
