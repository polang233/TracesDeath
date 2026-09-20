// Reproduces an interrupted claim session followed by a normal restart.
// Usage: node scripts/restart-corpse.cjs <java> <paper.jar> <plugin.jar> <version> <work-dir> <accepted-eula.txt>
// Creates a fresh isolated server directory. Kills only the Java child started here.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {spawn} = require('node:child_process');
const {once} = require('node:events');
const mineflayer = require('mineflayer');
const [java, paperArg, pluginArg, version, dirArg, eulaArg] = process.argv.slice(2);
if (!eulaArg) throw new Error('Expected java, paper.jar, plugin.jar, version, new work-dir, accepted-eula.txt');
const port=Number(process.env.RESTART_PORT || 25577);
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid test port');
const directory = path.resolve(dirArg), paper = path.resolve(paperArg), plugin = path.resolve(pluginArg);
if (fs.existsSync(directory)) throw new Error('Use a new directory: ' + directory);
if (!/^eula=true\s*$/m.test(fs.readFileSync(eulaArg, 'utf8'))) throw new Error('An accepted local EULA file is required');
fs.mkdirSync(directory, {recursive:true});
if (process.env.RESTART_RUNTIME_CACHE) for (const name of ['cache','libraries','versions']) {
 const source=path.join(process.env.RESTART_RUNTIME_CACHE,name);
 if(fs.existsSync(source)) fs.cpSync(source,path.join(directory,name),{recursive:true});
}
fs.copyFileSync(eulaArg, path.join(directory, 'eula.txt'));
const claimToInventory = process.env.CLAIM_ALL_MODE === 'inventory';
fs.mkdirSync(path.join(directory,'plugins/TracesDeath'),{recursive:true});
fs.writeFileSync(path.join(directory,'plugins/TracesDeath/config.yml'),'loot:\n  claim-all-mode: '+(claimToInventory?'fill_inventory':'restore_slots')+'\n');
fs.writeFileSync(path.join(directory, 'server.properties'), [
 'server-ip=127.0.0.1','server-port='+port,'online-mode=false','level-name=regression',
 'level-type=minecraft:flat','generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}',
 'generate-structures=false','spawn-protection=0','view-distance=3','simulation-distance=3','difficulty=peaceful','max-players=2'
].join('\n'));
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
let server, bot, boot = 0, output = '', logs;
const messages=[];
async function until(predicate, label, timeout=20000) {
 const deadline=Date.now()+timeout;
 while(Date.now()<deadline) {if(predicate()) return; await sleep(50);}
 throw new Error('Timeout: '+label);
}
async function startServer() {
 boot++; output='';
 logs=fs.createWriteStream(path.join(directory,`boot-${boot}.log`));
 server=spawn(java,['-Xms512m','-Xmx1536m','-Dterminal.jline=false','-Dterminal.ansi=false','-Dfile.encoding=UTF-8','-jar',paper,'--nogui','--add-plugin',plugin],{cwd:directory,windowsHide:true,stdio:['pipe','pipe','pipe']});
 server.stdout.on('data', b=>{output+=b.toString();logs.write(b);});
 server.stderr.on('data', b=>{output+=b.toString();logs.write(b);});
 server.on('error', e=>console.error(e));
 await until(()=>output.includes('Done (') || server.exitCode!==null,'server boot',180000);
 if(server.exitCode!==null) throw new Error(output.slice(-8000));
 console.log('BOOT',boot,'pid',server.pid);
 if(/\[TracesDeath\].*(?:启动失败|Error|Exception)/.test(output)) console.log('PLUGIN ERROR',output.slice(-6000));
}
async function command(text, wait=300) {server.stdin.write(text+'\n');await sleep(wait);}
async function connect() {
 bot=mineflayer.createBot({host:'127.0.0.1',port,username:'CrashBot',auth:'offline',version});
 bot.on('error',e=>console.error('BOT',e.message));
 bot.on('messagestr',m=>{messages.push(m);if(m.includes('遗体') || m.includes('恢复')) console.log('CHAT',m);});
 await once(bot,'spawn');await sleep(1500);
 await command('gamemode creative CrashBot');
 await command('tp CrashBot 0.5 80 2.5');
 if (boot > 1) await command('gamemode survival CrashBot');
}
function playerItems(){return bot.inventory.slots.slice(5,46).filter(Boolean);}
function all(name){return Object.values(bot.entities).filter(e=>e.name===name);}
async function open() {
 await until(()=>all('interaction').length>0,'interaction entity');
 const target=all('interaction')[0];
 console.log('INTERACT', bot.entity.position, target.position, target.id);
 if (version.startsWith('26.')) bot._client.write('use_entity',{target:target.id,hand:'main_hand',location:{x:0,y:0.3,z:0},sneaking:false});
 else await bot.activateEntity(target);
 await until(()=>bot.currentWindow?.slots[7]?.name==='clock','open GUI contents');
}
function snapshot(label) {
 const data={label,position:bot.entity.position,inventory:playerItems().map(i=>({slot:i.slot,name:i.name,count:i.count})),
  mannequin:all('mannequin').length,interaction:all('interaction').length,
  drops:all('item').map(e=>({id:e.id,item:e.getDroppedItem?.(),metadata:e.metadata})),
  gui:bot.currentWindow?.slots.slice(0,54).map(i=>i&&({name:i.name,count:i.count})),
  records:fs.readdirSync(path.join(directory,'plugins/TracesDeath/corpses')).filter(n=>n.endsWith('.yml')).map(n=>({name:n,data:fs.readFileSync(path.join(directory,'plugins/TracesDeath/corpses',n),'utf8')}))};
 fs.writeFileSync(path.join(directory,label+'.json'),JSON.stringify(data,null,2));
 console.log('STATE',label,JSON.stringify({inventory:data.inventory,mannequin:data.mannequin,interaction:data.interaction,drops:data.drops.length}));
 return data;
}
async function shutdown(force) {
 const ended=once(server,'exit');
 if(force) server.kill('SIGKILL'); else server.stdin.write('stop\n');
 await ended; logs.end(); await sleep(1500);
 if(bot) {bot.removeAllListeners();bot.end();bot=null;}
 console.log('STOP',force?'forced':'normal');
}

function totals(items) {
 const result={};
 for(const item of items) if(item) result[item.name]=(result[item.name]||0)+item.count;
 return Object.fromEntries(Object.entries(result).sort());
}
async function checkMenuAndClaimAll() {
 assert.equal(bot.currentWindow.slots[6]?.name,'chest');
 assert.equal(bot.currentWindow.slots[7]?.name,'clock');
 for(let i=9;i<18;i++) assert.equal(bot.currentWindow.slots[i]?.name,'black_stained_glass_pane');
 assert.equal(bot.currentWindow.slots[53]?.name,'stone','last hotbar slot');
 const records=fs.readdirSync(path.join(directory,'plugins/TracesDeath/corpses')).filter(n=>n.endsWith('.yml'));
 const record=fs.readFileSync(path.join(directory,'plugins/TracesDeath/corpses',records[0]),'utf8');
 assert.match(record,/death-time: [1-9][0-9]+/);
 await bot.clickWindow(7,0,0);await sleep(200);
 assert(messages.some(m=>m.includes('死亡者 ID：CrashBot')));
 assert(messages.some(m=>m.includes('死亡时间：')&&!m.includes('未记录')));
 bot.closeWindow(bot.currentWindow);await sleep(150);
 await command('item replace entity CrashBot armor.chest with golden_chestplate');
 if(claimToInventory) {
  for(let i=0;i<9;i++) await command('item replace entity CrashBot hotbar.'+i+' with nether_star 64',60);
  for(let i=0;i<27;i++) await command('item replace entity CrashBot inventory.'+i+' with nether_star 64',60);
  await command('item replace entity CrashBot hotbar.0 with gold_ingot 61');
 } else await command('item replace entity CrashBot hotbar.1 with nether_star 7');
 await open();
 const source=bot.currentWindow.slots.slice(0,54).filter((i,slot)=>i&&(slot<5||slot>=18)&&i.name!=='gray_stained_glass_pane');
 const expected=totals([...playerItems(),...source]);
 await bot.clickWindow(6,0,0);
 await until(()=>!bot.currentWindow,'claim-all closes GUI');
 await sleep(120);
 const dropped=all('item').map(e=>e.getDroppedItem()).filter(Boolean);
 assert.deepEqual(totals([...playerItems(),...dropped]),expected,'inventory plus world drops conserve all items');
 if(claimToInventory) {
  assert.equal(bot.inventory.slots[36]?.name,'gold_ingot');
  assert.equal(bot.inventory.slots[36]?.count,64);
  assert.equal(bot.inventory.slots[6]?.name,'golden_chestplate');
 } else {
  assert.equal(bot.inventory.slots[37]?.name,'stone');
  assert.equal(bot.inventory.slots[6]?.name,'iron_chestplate');
  assert(dropped.some(i=>i.name==='nether_star'&&i.count===7));
  assert(dropped.some(i=>i.name==='golden_chestplate'));
 }
 assert.equal(all('mannequin').length,0);
 assert.equal(all('interaction').length,0);
 snapshot('claim-all-'+(claimToInventory?'inventory':'original'));
 console.log('PASS new menu, death information, claim-all '+(claimToInventory?'inventory overflow':'original slots and displaced items'));
}

(async()=>{
 await startServer();await connect();
 await command('gamerule ' + (version.startsWith('26.') ? 'keep_inventory' : 'keepInventory') + ' false');
 await command('gamerule ' + (version.startsWith('26.') ? 'spawn_mobs' : 'doMobSpawning') + ' false');
 await command('fill -3 79 -3 3 79 3 stone');
 await command('spawnpoint CrashBot 0 80 0');
 await command('clear CrashBot');
 for(let i=0;i<9;i++) await command('item replace entity CrashBot hotbar.'+i+' with stone 64',60);
 for(let i=0;i<27;i++) await command('item replace entity CrashBot inventory.'+i+' with gold_ingot 32',60);
 await command('item replace entity CrashBot armor.chest with iron_chestplate');
 await command('item replace entity CrashBot armor.legs with iron_leggings');
 await command('item replace entity CrashBot armor.feet with iron_boots');
 await command('item replace entity CrashBot hotbar.0 with diamond 12');
 await command('item replace entity CrashBot inventory.3 with emerald 5');
 await command('item replace entity CrashBot armor.head with diamond_helmet');
 await command('item replace entity CrashBot weapon.offhand with shield');
 await command('tp CrashBot 0.5 80 0.5');
 await command('gamemode survival CrashBot');
 const dead=once(bot,'death');await command('kill CrashBot');await dead;await sleep(2500);
 await command('tp CrashBot 0.5 80 2.5');
 await command('save-all flush',1000);
 await open();
 assert.equal(bot.currentWindow.slots[45]?.count,12);
 for(const slot of [45,0,4,18]) {await bot.clickWindow(slot,0,0);await sleep(180);}
 const expected=snapshot('before-crash');
 await shutdown(true);
 await startServer();await connect();await sleep(1500);
 const crash=snapshot('after-crash');
 assert.equal(crash.drops.length,0,'loose items after forced stop');
 assert.deepEqual(crash.inventory,expected.inventory,'inventory after forced stop');
 assert.deepEqual(crash.records,expected.records,'corpse records after forced stop');
 assert.equal(playerItems().filter(i=>i.name==='diamond').reduce((s,i)=>s+i.count,0),12);
 await open();assert.equal(bot.currentWindow.slots[45],null);assert.equal(bot.currentWindow.slots[21]?.count,5);
 snapshot('after-crash-open');
 await shutdown(false);
 await startServer();await connect();await sleep(1500);
 const normal=snapshot('after-normal-restart');
 assert.equal(normal.drops.length,0,'loose items after normal stop');
 assert.deepEqual(normal.inventory,expected.inventory,'inventory after normal stop');
 assert.deepEqual(normal.records,expected.records,'corpse records after normal stop');
 await open();assert.equal(bot.currentWindow.slots[21]?.count,5);
 snapshot('after-normal-open');
 console.log('PASS forced stop with GUI open, exact inventory, normal restart interaction');
 await checkMenuAndClaimAll();
 await shutdown(false);
})().then(()=>process.exit(0)).catch(async e=>{
 console.error(e.stack);
 try{if(bot?.entity) snapshot('failure');}catch{}
 if(server && server.exitCode===null) await shutdown(false);
 process.exit(1);
});
