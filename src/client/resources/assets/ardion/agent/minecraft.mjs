let session;
let sequence = 0;

function withTimeout(signal, ms) {
  const timeout = AbortSignal.timeout(ms);
  if (!signal) return timeout;
  if (typeof AbortSignal.any === "function") return AbortSignal.any([timeout, signal]);
  const controller = new AbortController();
  const abort = () => controller.abort();
  timeout.addEventListener("abort", abort, { once: true });
  if (signal.aborted) abort();
  else signal.addEventListener("abort", abort, { once: true });
  return controller.signal;
}

async function request(method, params, signal) {
  const endpoint = new URL(process.env.ARDION_MCP_URL ?? "");
  if (endpoint.protocol !== "http:" || endpoint.hostname !== "127.0.0.1" || endpoint.pathname !== "/mcp") {
    throw new Error("ARDION_MCP_URL must name Ardion's local Minecraft MCP");
  }
  const token = process.env.ARDION_MCP_TOKEN;
  if (!token) throw new Error("Run Minecraft programs through the Ardion CLI environment");
  const headers = { "Content-Type": "application/json", Authorization: `Bearer ${token}` };
  if (session) headers["Mcp-Session-Id"] = session;
  const response = await fetch(endpoint, {
    method: "POST", headers,
    body: JSON.stringify({ jsonrpc: "2.0", id: ++sequence, method, params }),
    signal: withTimeout(signal, 20000),
  });
  if (!response.ok) throw new Error(`Minecraft MCP HTTP ${response.status}`);
  const message = await response.json();
  if (message.error) throw new Error(`Minecraft MCP: ${JSON.stringify(message.error)}`);
  if (method === "initialize") {
    session = response.headers.get("Mcp-Session-Id");
    if (!session) throw new Error("Minecraft MCP did not issue a session");
  }
  return message.result;
}

export async function call(name, args = {}, signal) {
  if (signal?.aborted) throw new Error("Voyager program cancelled");
  if (!session) await request("initialize", { protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "ardion-program", version: "1" } }, signal);
  const result = await request("tools/call", { name, arguments: args }, signal);
  const text = result.content.filter(part => part.type === "text").map(part => part.text).join("\n");
  if (result.isError) throw new Error(`Minecraft ${name}: ${text}`);
  let value;
  try { value = JSON.parse(text); } catch { value = text; }
  const images = result.content.filter(part => part.type === "image");
  return images.length ? { value, images } : value;
}

export async function waitFor(actionId, signal) {
  let lastTicks = -1, stuck = 0;
  for (;;) {
    if (signal?.aborted) throw new Error("Voyager program cancelled");
    const result = await call("action_status", { actionId, waitMs: 10000 }, signal);
    if (result.status === "running") {
      if (result.ticks === lastTicks) stuck++; else stuck = 0;
      lastTicks = result.ticks;
      if (stuck >= 3) {
        try { await call("cancel_action", {}, signal); } catch {  }
        throw new Error("Minecraft action stopped advancing; it was cancelled instead of leaving Voyager running");
      }
      continue;
    }
    if (result.status !== "succeeded") throw new Error(`Minecraft action: ${JSON.stringify(result)}`);
    return result;
  }
}

export async function run(tool, args = {}) {
  const result = await call(tool, args);
  return result.actionId ? waitFor(result.actionId) : result;
}

export async function observe() {
  return { state: await call("state"), inventory: await call("inventory") };
}

export async function cancel() { return call("cancel_action"); }
