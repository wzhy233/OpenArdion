import { createInterface } from 'node:readline';
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import path from 'node:path';

// Redirect before Mineflayer loads. A static import would capture the real stdout logger and corrupt MCP.
console.log = console.info = (...args) => console.error(...args);
const log = (...args) => console.error(new Date().toISOString(), '[voyager]', ...args);

const events = [];
let bot, client, busy = false, active;
const actions = ['connect', 'disconnect', 'observe', 'findBlocks', 'goto', 'mineBlock', 'craftItem', 'smeltItem', 'placeItem', 'killMob', 'shoot', 'exploreUntil', 'depositItemIntoChest', 'getItemFromChest', 'checkItemInsideChest', 'equip', 'consume', 'waitTicks', 'execute', 'cancel'];
const schema = { type: 'object', properties: { target: { type: 'string', enum: ['client', 'bot'] },
  action: { type: 'string', enum: actions }, args: { type: 'array', items: {}, description: 'Positional arguments. To summon Alex in the current singleplayer world: connect with [{currentWorld:true}]; this automatically opens/reuses LAN and logs in Alex. Explicit remote options or saved voyager.json remain supported.' } }, required: ['target', 'action'] };

function deadlineMs(input) {
  const override = Number(process.env.ARDION_VOYAGER_DEADLINE_MS);
  if (Number.isFinite(override) && override > 0) return override;
  const timed = (input.args ?? []).find(value => value && typeof value === 'object' && Number.isFinite(value.timeoutTicks));
  const ticks = timed ? Math.max(1, Math.min(12000, timed.timeoutTicks)) : input.action === 'execute' ? 2400 : 1200;
  return Math.min(660_000, ticks * 50 + 20_000);
}

export async function execute(input, requestId) {
  if (!input || !['client', 'bot'].includes(input.target) || !actions.includes(input.action) || (input.args !== undefined && !Array.isArray(input.args))) throw new Error('Invalid Voyager target, action or args');
  const args = input.args ?? [];
  if (input.action === 'cancel') {
    log('cancel', input.target, requestId);
    if (active?.target === input.target) active.controller.abort();
    if (input.target === 'bot') { await bot?.disconnect(); bot = undefined; return { cancelled: true, disconnected: true }; }
    client?.stop?.();
    return client?.cancel();
  }
  if (busy) throw new Error('A Voyager program is running; cancel or wait for its result first');
  busy = true;
  const controller = new AbortController();
  const timer = AbortSignal.timeout(deadlineMs(input));
  active = { target: input.target, controller, requestId };
  let phase = 'start';
  let finished = false;
  log('start', input.target, input.action, requestId);
  try {
    const operation = async () => {
      if (input.action === 'connect') {
        phase = 'connect';
        if (input.target !== 'bot') throw new Error('client is the existing player; no bot login is needed');
        if (bot) throw new Error('Disconnect the existing bot before changing identity/server');
        let options = args[0];
        if (options === undefined) {
          try { options = JSON.parse(await readFile(path.join(process.env.ARDION_HOME, 'agent', 'voyager.json'), 'utf8')); }
          catch (error) { if (error.code !== 'ENOENT') throw error; options = { currentWorld: true }; }
        }
        if (options?.currentWorld === true) {
          phase = 'prepare-lan';
          const prepared = await (await import(process.env.ARDION_VOYAGER_API)).prepareBot();
          events.push({ type: 'lan', opened: prepared.openedLan, port: prepared.connection.port,
            gameMode: prepared.gameMode, commandsAllowed: prepared.commandsAllowed, onlineMode: prepared.onlineMode });
          options = prepared.connection;
        }
        controller.signal.throwIfAborted();
        phase = 'mineflayer-login';
        const { connect } = await import('./mineflayer.mjs');
        bot = await connect(options, events, controller.signal); return bot.observe();
      }
      phase = 'client-api';
      if (!client && input.target === 'client') client = await (await import(process.env.ARDION_VOYAGER_API)).createClient(events);
      const api = input.target === 'bot' ? bot : client;
      if (!api) throw new Error('Call connect for target bot first; there is no implicit player fallback');
      const signal = typeof AbortSignal.any === 'function' ? AbortSignal.any([controller.signal, timer]) : controller.signal;
      api.bindSignal?.(signal);
      controller.signal.throwIfAborted();
      api.begin?.();
      const guarded = Object.fromEntries(Object.entries(api).map(([name, value]) => [name, typeof value === 'function' ? (...values) => {
        if (controller.signal.aborted || timer.aborted) throw new Error('Voyager program cancelled');
        return value(...values);
      } : value]));
      phase = input.action;
      if (input.action !== 'execute') return guarded[input.action](...args);
      if (typeof args[0] !== 'string') throw new Error('execute args: [absolute program.mjs path]');
      phase = 'program';
      const program = await import(pathToFileURL(path.resolve(args[0])).href + '?run=' + Date.now());
      if (typeof program.default !== 'function') throw new Error('Program must export default async function(api)');
      return await program.default(guarded);
    };
    const op = operation();
    op.then(() => { if (finished) log('late success ignored', phase); }, error => { if (finished) log('late failure', phase, error.message); });
    const timeout = new Promise((_, reject) => timer.addEventListener('abort', () => reject(new Error('Voyager timed out during ' + phase + '; the call was stopped')), { once: true }));
    const cancelled = new Promise((_, reject) => controller.signal.addEventListener('abort', () => reject(new Error('Voyager program cancelled')), { once: true }));
    const result = await Promise.race([op, timeout, cancelled]);
    if (input.action === 'disconnect') bot = undefined;
    log('done', input.target, input.action, requestId);
    return result;
  } finally {
    finished = true;
    const stop = controller.signal.aborted || timer.aborted;
    if (stop && input.action !== 'cancel') {
      try {
        client?.stop?.();
        if (input.target === 'client') await client?.cancel?.();
        if (input.target === 'bot') await bot?.cancel?.();
      } catch (error) { log('cleanup', error.message); }
    }
    if (active?.controller === controller) { busy = false; active = undefined; }
    else if (busy && active == null) busy = false;
  }
}

async function handle(message) {
  if (!('id' in message)) { if (message.method === 'notifications/cancelled' && active?.requestId === message.params?.requestId) await execute({ target: active.target, action: 'cancel' }); return; }
  let result;
  if (message.method === 'initialize') result = { protocolVersion: message.params.protocolVersion, capabilities: { tools: {} }, serverInfo: { name: 'ardion-voyager', version: '1.0.0' } };
  else if (message.method === 'tools/list') result = { tools: [{ name: 'voyager', description: 'Voyager executable behaviors for two explicit targets: the current Ardion client player or a separate Mineflayer bot. args is a positional argument array. mineBlock(name,count), craftItem(name,count), smeltItem(item,fuel,count), placeItem(name,position), killMob(name,timeout), shoot(weapon,mob), chest methods(position,item-count-map), exploreUntil(direction,seconds,callback in programs), goto(position), execute(programPath). Returns feedback/errors/observations; save verified programs as skills. No keyboard or mouse simulation.', inputSchema: schema }] };
  else if (message.method === 'ping') result = {};
  else if (message.method === 'tools/call') {
    const offset = events.length;
    try { if (message.params.name !== 'voyager') throw new Error('Unknown tool'); const value = await execute(message.params.arguments, message.id); const observation = message.params.arguments.target === 'bot' ? await bot?.observe() : await client?.observe(); result = { content: [{ type: 'text', text: JSON.stringify({ target: message.params.arguments.target, result: value ?? null, events: events.slice(offset), observation }) }] }; }
    catch (error) { log('fail', message.id, error.message); result = { isError: true, content: [{ type: 'text', text: JSON.stringify({ error: error.message, events: events.slice(offset) }) }] }; }
    events.splice(0, offset);
  } else { await reply({ jsonrpc: '2.0', id: message.id, error: { code: -32601, message: 'Unknown method' } }); return; }
  await reply({ jsonrpc: '2.0', id: message.id, result });
}

function reply(payload) {
  const line = JSON.stringify(payload) + '\n';
  if (process.stdout.write(line)) return Promise.resolve();
  return new Promise(resolve => process.stdout.once('drain', resolve));
}

const lines = createInterface({ input: process.stdin });
lines.on('line', line => { let message; try { message = JSON.parse(line); } catch { return; } void handle(message).catch(error => log('unhandled', error.message)); });
await new Promise(resolve => lines.on('close', () => {
  active?.controller.abort();
  void Promise.allSettled([bot?.disconnect(), client?.cancel()]).finally(resolve);
}));
