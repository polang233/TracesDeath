// Run only against the isolated run-core server. Requires mineflayer 4.39+.
// NODE_PATH may point at an existing node_modules directory.
// node scripts/smoke-corpse.cjs [exercise|seed|resume]
const assert = require('node:assert/strict');
const { once } = require('node:events');
const mineflayer = require('mineflayer');
const mode = process.argv[2] || 'exercise';
const bot = mineflayer.createBot({host:'127.0.0.1', port:25576, username:'CoreBot', auth:'offline', version:'1.21.9'});
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const messages = [];
bot.on('messagestr', text => { messages.push(text); console.log('CHAT', text); });
bot.on('error', error => console.error(error));
bot.on('kicked', reason => console.error('KICKED', reason));
async function command(text) { bot.chat('/' + text); await sleep(350); }
async function until(predicate, description, timeout = 12000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) { if (predicate()) return; await sleep(100); }
  throw new Error('Timed out: ' + description);
}
const entities = name => Object.values(bot.entities).filter(entity => entity.name === name);
async function open() {
  await until(() => entities('interaction').length === 1, 'one interaction entity');
  bot.activateEntity(entities('interaction')[0]);
  await until(() => bot.currentWindow?.slots[6]?.name === 'clock', 'corpse GUI contents');
}
async function close() { if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(300); }
async function click(slot) { await bot.clickWindow(slot, 0, 0); await sleep(400); }
function expectSlot(slot, name, count) {
  const item = bot.currentWindow.slots[slot];
  assert.equal(item?.name, name, 'GUI slot ' + slot);
  assert.equal(item?.count, count, 'GUI amount ' + slot);
}
(async () => {
  await once(bot, 'spawn');
  await sleep(1200);
  await command('gamemode creative');
  if (mode !== 'resume') {
    await command('gamerule keepInventory false');
    await command('gamerule doMobSpawning false');
    await command('fill -3 79 -3 3 79 3 stone');
    await command('tp @s 0.5 80 0.5');
    await command('spawnpoint @s 0 80 0');
    await command('clear @s');
    await command('item replace entity @s hotbar.0 with diamond 12');
    await command('item replace entity @s inventory.3 with emerald 5');
    await command('item replace entity @s armor.head with diamond_helmet');
    await command('item replace entity @s weapon.offhand with shield');
    await command('gamemode survival');
    const dead = once(bot, 'death');
    bot.chat('/kill @s');
    await dead;
    await sleep(1000);
    if (!bot.isAlive) bot.respawn();
    await sleep(2000);
  }
  await command('tp @s 0.5 80 2.5');
  await command('gamemode survival');
  await until(() => entities('mannequin').length === 1, 'one mannequin');
  await open();
  expectSlot(0, 'diamond_helmet', 1);
  expectSlot(4, 'shield', 1);
  expectSlot(21, 'emerald', 5);
  expectSlot(45, 'diamond', 12);
  assert.equal(entities('item').length, 0, 'no vanilla item drops');
  await close();
  await command('data get entity @e[type=mannequin,limit=1] pose');
  if (mode === 'seed') {
    console.log('PASS seed: original slots, 1 visual + 1 hitbox; left for restart');
    await command('stop');
    return;
  }
  // A full inventory with one partial diamond stack permits exactly three items.
  for (let i = 0; i < 9; i++) await command(`item replace entity @s hotbar.${i} with stone 64`);
  for (let i = 0; i < 27; i++) await command(`item replace entity @s inventory.${i} with stone 64`);
  await command('item replace entity @s hotbar.0 with diamond 61');
  await open();
  await click(45);
  expectSlot(45, 'diamond', 9);
  expectSlot(21, 'emerald', 5);
  await click(45);
  expectSlot(45, 'diamond', 9);
  await close();
  assert.equal(bot.inventory.items().filter(i => i.name === 'diamond').reduce((n, i) => n + i.count, 0), 64);
  await command('clear @s');
  // Move away far enough to unload both chunks, then return and verify reconstruction.
  await command('gamemode creative');
  await command('tp @s 1000 100 1000');
  await sleep(12000);
  await command('tp @s 0.5 80 2.5');
  await command('gamemode survival');
  await open();
  expectSlot(45, 'diamond', 9);
  for (const slot of [0, 4, 21, 45]) await click(slot);
  await until(() => entities('mannequin').length === 0 && entities('interaction').length === 0, 'empty corpse disappears');
  assert.equal(bot.currentWindow, null, 'GUI closes when empty');
  for (const [name, count] of [['diamond_helmet',1], ['shield',1], ['emerald',5], ['diamond',9]]) {
    assert.equal(bot.inventory.items().filter(i => i.name === name).reduce((n, i) => n + i.count, 0), count, name);
  }
  // keepInventory must leave no corpse.
  await command('gamerule keepInventory true');
  const death = once(bot, 'death'); bot.chat('/kill @s'); await death;
  await sleep(2500);
  assert.equal(entities('mannequin').length, 0);
  assert.equal(bot.inventory.items().filter(i => i.name === 'diamond').reduce((n, i) => n + i.count, 0), 9);
  await command('gamerule keepInventory false');
  console.log('PASS ' + mode + ': fixed slots, partial/full inventory, close/reopen, chunk return, empty cleanup, keepInventory');
  await command('stop');
})().catch(async error => { console.error(error); await close(); bot.quit(); process.exitCode = 1; });
setTimeout(() => { console.error('Smoke test deadline exceeded'); bot.quit(); process.exit(1); }, 120000).unref();
