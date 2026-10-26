import mineflayer from 'mineflayer';
import pathfinder from 'mineflayer-pathfinder';
import { plugin as collectblock } from './collectblock/index.mjs';
import tool from 'mineflayer-tool';
import pvp from 'mineflayer-pvp';
import hawkeye from 'minecrafthawkeye';
import { Vec3 } from 'vec3';
import { readFile } from 'node:fs/promises';
import { once } from 'node:events';
import path from 'node:path';

export function connectionOptions(input) {
  const options = { ...input, username: input?.auth === 'microsoft' ? input.username : 'Alex' };
  if (!options || typeof options.host !== 'string' || !options.host.trim() ||
      !Number.isInteger(options.port) || options.port < 1 || options.port > 65535 ||
      typeof options.username !== 'string' || !options.username.trim()) throw new Error('host, port and username are required');
  if (options.auth && !['offline', 'microsoft'].includes(options.auth)) throw new Error('auth must be offline or microsoft');
  if ((!options.auth || options.auth === 'offline') && !/^[A-Za-z0-9_]{3,16}$/.test(options.username)) throw new Error('Offline Minecraft usernames must contain 3-16 letters, digits or underscores');
  return options;
}

export async function connect(input, events, signal) {
  const options = connectionOptions(input);
  const bot = mineflayer.createBot({ host: options.host, port: options.port, username: options.username,
    auth: options.auth ?? 'offline', version: options.version,
    profilesFolder: path.join(process.env.ARDION_HOME, 'agent', 'voyager-auth'),
    onMsaCode: code => events.push({ type: 'auth', verificationUri: code.verification_uri, userCode: code.user_code }),
  });
  bot.loadPlugin(pathfinder.pathfinder); bot.loadPlugin(tool.plugin);
  bot.loadPlugin(collectblock); bot.loadPlugin(pvp.plugin); bot.loadPlugin(hawkeye);
  const spawned = once(bot, 'spawn');
  const ended = once(bot, 'end').then(([reason]) => { throw new Error(`Bot disconnected: ${reason}`); });
  bot.on('error', error => events.push({ type: 'onError', message: error.message }));
  bot.on('kicked', reason => events.push({ type: 'onError', message: JSON.stringify(reason) }));
  try { await Promise.race([spawned, ended,
    new Promise((_, reject) => signal?.addEventListener('abort', () => { bot.quit(); reject(new Error('Bot connection cancelled')); }, { once: true })),
    new Promise((_, reject) => { const timer = setTimeout(() => reject(new Error('Mineflayer connection timed out')), 30000); timer.unref(); })]); }
  catch (error) { bot.quit(); throw error; }
  bot.pathfinder.setMovements(new pathfinder.Movements(bot));
  bot.pathfinder.thinkTimeout = 5000;
  // Voyager reports feedback to the agent; recovery must never issue implicit cheat commands.
  bot.chat = message => {
    if (message.startsWith('/')) throw new Error('Voyager recovery cannot execute cheat commands');
    events.push({ type: 'onChat', message });
  };
  bot.save = name => events.push({ type: 'onSave', name });
  const files = ['craftHelper', 'craftItem', 'mineBlock', 'placeItem', 'smeltItem', 'useChest', 'exploreUntil', 'waitForMobRemoved', 'killMob', 'shoot'];
  const sources = await Promise.all(files.map(name => readFile(new URL(`./primitives/${name}.js`, import.meta.url), 'utf8')));
  const names = ['mineBlock', 'craftItem', 'smeltItem', 'placeItem', 'killMob', 'exploreUntil', 'depositItemIntoChest', 'getItemFromChest', 'checkItemInsideChest', 'shoot'];
  const load = new Function('mcData', 'Vec3', ...Object.keys(pathfinder.goals),
    'let _mineBlockFailCount=0,_craftItemFailCount=0,_smeltItemFailCount=0,_placeItemFailCount=0,_killMobFailCount=0;\n' + sources.join('\n') + `\nreturn {${names.join(',')}};`);
  const primitives = load(bot.registry, Vec3, ...Object.values(pathfinder.goals));
  const api = {
    target: 'bot',
    observe: async () => ({ target: 'bot', version: bot.version, username: bot.username,
      health: bot.health, food: bot.food, position: bot.entity.position,
      dimension: bot.game.dimension, time: bot.time.timeOfDay,
      entities: Object.values(bot.entities).filter(entity => entity.position.distanceTo(bot.entity.position) <= 48).map(entity => ({ id: entity.id, name: entity.name, position: entity.position })),
      inventory: bot.inventory.items().map(item => ({ name: item.name, count: item.count, slot: item.slot })) }),
    findBlocks: async (name, radius = 32, count = 128) => {
      const block = bot.registry.blocksByName[name.replace(/^minecraft:/, '')];
      if (!block) throw new Error(`Unknown block: ${name}`);
      return bot.findBlocks({ matching: block.id, maxDistance: radius, count }).map(position => ({ name: bot.blockAt(position)?.name, ...position }));
    },
    goto: async position => {
      const ticks = Number.isFinite(position?.timeoutTicks) ? Math.max(1, Math.min(12000, position.timeoutTicks)) : 1200;
      const move = bot.pathfinder.goto(new pathfinder.goals.GoalNear(position.x, position.y, position.z, position.range ?? 1));
      void move.catch(error => console.error('[voyager] path ended:', error.message));
      let timer;
      try {
        return await Promise.race([move, new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('Bot path timed out; movement stopped')), ticks * 50 + 1000); })]);
      } catch (error) {
        bot.pathfinder.setGoal(null);
        bot.clearControlStates();
        throw error;
      } finally { clearTimeout(timer); }
    },
    waitTicks: ticks => bot.waitForTicks(ticks),
    equip: async name => { const item = bot.inventory.items().find(item => item.name === name.replace(/^minecraft:/, '')); if (!item) throw new Error(`Not carried: ${name}`); await bot.equip(item, 'hand'); },
    consume: async () => bot.consume(),
    cancel: async () => { bot.pathfinder.setGoal(null); bot.collectBlock.cancelTask(); bot.pvp.stop(); bot.hawkEye?.stop(); bot.stopDigging(); bot.deactivateItem(); bot.clearControlStates(); },
    disconnect: async () => { await api.cancel(); bot.quit(); },
  };
  for (const name of names) api[name] = async (...args) => {
    const countIndex = { mineBlock: 1, craftItem: 1, smeltItem: 2 }[name];
    if (countIndex !== undefined && args[countIndex] !== undefined && (!Number.isSafeInteger(args[countIndex]) || args[countIndex] < 1)) throw new Error('count must be a positive integer');
    const normalized = args.map(arg => {
      if (typeof arg === 'string') return arg.replace(/^minecraft:/, '');
      if (arg && typeof arg === 'object' && 'x' in arg && 'y' in arg && 'z' in arg) {
        if (!['x','y','z'].every(axis => Number.isFinite(arg[axis]) && Math.abs(arg[axis]) <= 30000000)) throw new Error('Invalid world position');
        return new Vec3(arg.x, arg.y, arg.z);
      }
      return arg;
    });
    return primitives[name](bot, ...normalized);
  };
  return api;
}
