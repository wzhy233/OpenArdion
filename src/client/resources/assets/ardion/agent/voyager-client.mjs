import * as mc from './minecraft.mjs';

export const prepareBot = () => mc.call('prepare_bot');

export async function createClient(events = []) {
  let cancelled = false;
  let signal;
  const id = name => typeof name === 'string' && /^[a-z0-9_]+(:[a-z0-9_/.-]+)?$/.test(name) ? (name.includes(':') ? name : `minecraft:${name}`) : (() => { throw new Error('Invalid registry name'); })();
  const count = value => { if (!Number.isSafeInteger(value) || value < 1) throw new Error('count must be a positive integer'); return value; };
  const call = async (name, args) => { if (cancelled || signal?.aborted) throw new Error('Voyager program cancelled'); return mc.call(name, args, signal); };
  const run = async (name, args) => { const result = await call(name, args); return result.actionId ? mc.waitFor(result.actionId, signal) : result; };
  const inventory = () => call('inventory');
  const dismissChat = async () => { if ((await call('state')).screen.type === 'ChatScreen') await call('inventory_action', { action: 'close' }); };
  const itemCount = (view, name) => view.items.filter(item => item.id === id(name)).reduce((sum, item) => sum + item.count, 0);
  const nearby = async name => {
    const result = await call('find_blocks', { block: id(name), radius: 32, limit: 128 });
    if (!result.blocks?.length) throw new Error(`No ${name} nearby; explore first`);
    return result.blocks;
  };
  const open = async position => {
    await api.goto({ ...position, range: 3 });
    await call('interact_block', position);
    await api.waitTicks(5);
    return inventory();
  };
  const click = (view, slot, button = 0) => call('inventory_action', { action: 'click', syncId: view.handler.syncId, slot, button, type: 'PICKUP' });
  const transfer = async (name, amount, toContainer, targetSlot) => {
    amount = count(amount); name = id(name);
    let remaining = amount;
    while (remaining) {
      const view = await inventory();
      if (view.handler.cursor.count) throw new Error('Container cursor is occupied; recover it before transferring items');
      const source = view.handler.slots.find(slot => slot.playerInventory === toContainer && slot.id === name && slot.count > 0);
      const target = view.handler.slots.find(slot => targetSlot === undefined
        ? slot.playerInventory !== toContainer && (slot.empty || slot.count === 0 || slot.id === name && slot.count < 64)
        : slot.slot === targetSlot);
      if (!source || !target) throw new Error(`Insufficient ${name} or no destination slot`);
      const moving = Math.min(remaining, source.count, 64 - (target.count ?? 0));
      if (moving <= 0) throw new Error('Destination is full');
      await click(view, source.slot);
      try { for (let i = 0; i < moving; i++) await click(view, target.slot, 1); }
      finally { if (!cancelled && (await inventory()).handler.cursor.count) await click(view, source.slot); }
      await api.waitTicks(3);
      const after = await inventory();
      const delta = itemCount(view, name) - itemCount(after, name);
      if (toContainer ? delta !== moving : delta !== -moving) throw new Error('Server did not confirm the requested item transfer');
      remaining -= moving;
    }
    return { name, transferred: amount };
  };
  const api = {
    target: 'client',
    begin: () => { cancelled = false; },
    stop: () => { cancelled = true; },
    bindSignal: value => { signal = value; },
    observe: () => mc.observe(),
    findBlocks: async (name, radius = 32, limit = 128) => (await call('find_blocks', { block: id(name), radius, limit })).blocks,
    cancel: async () => { cancelled = true; return mc.cancel(); },
    disconnect: async () => { throw new Error('client is the real player; disconnect is only available for target bot'); },
    goto: async position => {
      if (!position || !['x', 'y', 'z'].every(axis => Number.isFinite(position[axis]))) throw new Error('goto requires finite x/y/z');
      await dismissChat();
      return run('navigate', position);
    },
    waitTicks: ticks => run('wait_ticks', { ticks: count(ticks) }),
    equip: async name => {
      const item = (await inventory()).items.find(item => item.id === id(name));
      if (!item) throw new Error(`Not carried: ${name}`);
      return call('inventory_action', { action: 'equip', slot: item.slot });
    },
    consume: async () => { await dismissChat(); return run('use_item', { ticks: 40 }); },
    mineBlock: async (name, amount = 1) => {
      count(amount);
      const before = await inventory();
      let mined = 0;
      while (mined < amount) {
        const target = (await nearby(name))[0];
        await api.goto({ ...target, range: 3 });
        await run('mine_block', target);
        mined++;
        await api.goto({ ...target, range: 0.5 });
        await api.waitTicks(10);
      }
      events.push({ type: 'onSave', name: `${name}_mined` });
      return { mined, before, after: await inventory() };
    },
    craftItem: async (name, amount = 1) => {
      count(amount);
      const before = await inventory();
      const tables = await call('find_blocks', { block: 'minecraft:crafting_table', radius: 32, limit: 1 });
      if (tables.blocks.length) await open(tables.blocks[0]);
      else await call('inventory_action', { action: 'open' });
      try {
        const recipes = await call('recipes', { query: id(name) });
        const recipe = recipes.recipes.find(recipe => recipe.hasIngredients && recipe.outputs.some(item => item.id === id(name)));
        if (!recipe) throw new Error(`No usable server recipe for ${name}; check ingredients and crafting station`);
        for (let i = 0; i < amount; i++) await run('craft_recipe', { recipeId: recipe.recipeId, syncId: (await inventory()).handler.syncId });
        const after = await inventory();
        if (itemCount(after, name) <= itemCount(before, name)) throw new Error('Crafting output was not received');
        return { crafted: itemCount(after, name) - itemCount(before, name) };
      } finally { await mc.call('inventory_action', { action: 'close' }); }
    },
    placeItem: async (name, position) => {
      await api.goto({ ...position, range: 3 }); await api.equip(name);
      for (const [dx, dy, dz, side] of [[0,-1,0,'up'],[0,1,0,'down'],[-1,0,0,'east'],[1,0,0,'west'],[0,0,-1,'south'],[0,0,1,'north']]) {
        const reference = { x: position.x + dx, y: position.y + dy, z: position.z + dz };
        const block = await call('block', reference);
        if (block.id === 'minecraft:air' || block.id === 'minecraft:cave_air') continue;
        await call('interact_block', { ...reference, face: side }); await api.waitTicks(5);
        const placed = await call('block', position);
        if (placed.id !== id(name)) throw new Error(`Placement was not confirmed: ${placed.id}`);
        events.push({ type: 'onSave', name: `${name}_placed` }); return placed;
      }
      throw new Error('No supporting block; floating placement is unavailable');
    },
    smeltItem: async (name, fuel, amount = 1) => {
      count(amount); const before = await inventory(); await open((await nearby('furnace'))[0]);
      try {
        for (let i = 0; i < amount; i++) {
          const view = await inventory();
          if (!view.handler.slots[1].count) await transfer(fuel, 1, true, 1);
          await transfer(name, 1, true, 0);
          let ready = false;
          for (let tick = 0; tick < 300; tick += 10) {
            await api.waitTicks(10); const view = await inventory();
            if (view.handler.slots[2].count) { await call('inventory_action', { action: 'click', syncId: view.handler.syncId, slot: 2, type: 'QUICK_MOVE' }); ready = true; break; }
          }
          if (!ready) throw new Error('Furnace did not produce output; inspect fuel/input and recipe');
        }
        return { smelted: amount, before, after: await inventory() };
      } finally { await mc.call('inventory_action', { action: 'close' }); }
    },
    killMob: async (name, timeout = 300) => {
      if (!Number.isFinite(timeout) || timeout <= 0) throw new Error('timeout must be positive seconds');
      const entities = (await call('entities', { radius: 48, limit: 80 })).entities;
      const target = entities.find(entity => entity.typeId === id(name));
      if (!target) throw new Error(`No ${name} nearby; explore first`);
      const deadline = Date.now() + timeout * 1000;
      while (Date.now() < deadline) {
        const current = (await call('entities', { radius: 96, limit: 80 })).entities.find(entity => entity.id === target.id);
        if (!current) throw new Error('Target left observation range; death is not confirmed');
        if (current.alive === false || current.health <= 0) { events.push({ type: 'onSave', name: `${name}_killed` }); return { killed: target.id }; }
        await api.goto({ ...current.position, range: 2 });
        await call('interact_entity', { entityId: target.id, action: 'attack' }); await api.waitTicks(12);
      }
      throw new Error('Combat timed out');
    },
    shoot: async (weapon, name) => {
      if (!['bow', 'crossbow', 'snowball', 'ender_pearl', 'egg', 'splash_potion', 'trident'].includes(weapon.replace(/^minecraft:/, ''))) throw new Error('Unsupported ranged item');
      const target = (await call('entities', { radius: 48, limit: 80 })).entities.find(entity => entity.typeId === id(name));
      if (!target) throw new Error(`No ${name} nearby`);
      await api.equip(weapon);
      await call('aim_entity', { entityId: target.id });
      const result = await run('use_item', { ticks: 25 });
      if (weapon.replace(/^minecraft:/, '') === 'crossbow') await run('use_item', { ticks: 1 });
      return result;
    },
    exploreUntil: async (direction, seconds = 60, callback = () => false) => {
      if (!direction || !['x','y','z'].every(axis => [-1,0,1].includes(direction[axis])) || !Object.values(direction).some(value => value !== 0)) throw new Error('direction must contain -1/0/1 and be nonzero');
      if (!Number.isFinite(seconds) || seconds <= 0 || typeof callback !== 'function') throw new Error('Invalid exploration timeout/callback');
      const deadline = Date.now() + seconds * 1000;
      while (Date.now() < deadline) {
        const result = await callback(await api.observe()); if (result) return result;
        const { position } = (await api.observe()).state;
        await api.goto({ x: Math.floor(position.x + direction.x * 12), y: Math.floor(position.y + direction.y * 12), z: Math.floor(position.z + direction.z * 12), timeoutTicks: Math.max(1, Math.min(1200, Math.floor((deadline - Date.now()) / 50))) });
      }
      return null;
    },
    depositItemIntoChest: async (position, items) => chest(position, items, true),
    getItemFromChest: async (position, items) => chest(position, items, false),
    checkItemInsideChest: async position => { const view = await open(position); await call('inventory_action', { action: 'close' }); return view.handler.slots.filter(slot => !slot.playerInventory && slot.count); },
  };
  async function chest(position, items, deposit) {
    if (!items || typeof items !== 'object' || Array.isArray(items)) throw new Error('items must be an item/count map');
    await open(position);
    try { const results = []; for (const [name, amount] of Object.entries(items)) results.push(await transfer(name, amount, deposit)); return results; }
    finally { await mc.call('inventory_action', { action: 'close' }); }
  }
  return api;
}
