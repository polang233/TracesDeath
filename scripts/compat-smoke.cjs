// node scripts/compat-smoke.cjs <java> <paper.jar> <plugin.jar> <version> <visual> <new-directory> <port> <accepted-eula.txt>
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {spawn}=require('node:child_process'),{once}=require('node:events');
const mineflayer=require('mineflayer');
const [java,paperArg,pluginArg,version,visual,dirArg,portArg,eulaArg]=process.argv.slice(2);
if(!eulaArg)throw new Error('Missing arguments');
const directory=path.resolve(dirArg),paper=path.resolve(paperArg),plugin=path.resolve(pluginArg),port=Number(portArg);
if(fs.existsSync(directory))throw new Error('Use a new isolated directory');
if(!/^eula=true\s*$/m.test(fs.readFileSync(eulaArg,'utf8')))throw new Error('Accepted EULA required');
const legacy=version.startsWith('1.12');
fs.mkdirSync(path.join(directory,'plugins/TracesDeath'),{recursive:true});
fs.copyFileSync(eulaArg,path.join(directory,'eula.txt'));
fs.copyFileSync(plugin,path.join(directory,'plugins/TracesDeath.jar'));
fs.writeFileSync(path.join(directory,'plugins/TracesDeath/config.yml'),`corpse:\n  type: ${visual}\n`);
fs.mkdirSync(path.join(directory,'plugins/bStats'),{recursive:true});
fs.writeFileSync(path.join(directory,'plugins/bStats/config.yml'),'enabled: false\n');
fs.writeFileSync(path.join(directory,'server.properties'),[
 'server-ip=127.0.0.1','server-port='+port,'online-mode=false','level-name=compat-test','level-type=FLAT','generate-structures=false',
 'difficulty=0','allow-nether=false','spawn-protection=0','view-distance=3','simulation-distance=3','max-players=2'
].join('\n'));
fs.writeFileSync(path.join(directory,'bukkit.yml'),'settings:\n  allow-end: false\n');
let server,bot,output='',boot=0,log;
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function until(test,label,timeout=25000){let end=Date.now()+timeout;while(Date.now()<end){if(test())return;await sleep(100);}throw new Error('Timeout: '+label);}
async function start(){
 output='';boot++;log=fs.createWriteStream(path.join(directory,`boot-${boot}.log`));
 server=spawn(java,['-Xms256m','-Xmx1024m','-Dterminal.jline=false','-Dterminal.ansi=false','-Dfile.encoding=UTF-8','-jar',paper,'nogui'],{cwd:directory,windowsHide:true,stdio:['pipe','pipe','pipe']});
 for(const stream of [server.stdout,server.stderr])stream.on('data',b=>{output+=b.toString();log.write(b);});
 await until(()=>output.includes('Done (')||server.exitCode!==null,'server boot',180000);
 if(server.exitCode!==null)throw new Error(output.slice(-6000));
 if(/Error occurred while enabling TracesDeath|UnsupportedClassVersionError|NoClassDefFoundError|NoSuchMethodError/.test(output))throw new Error(output.slice(-6000));
 console.log('BOOT',version,visual,boot,'PID',server.pid);
}
async function cmd(text,wait=300){server.stdin.write(text+'\n');await sleep(wait);}
async function connect(){
 bot=mineflayer.createBot({host:'127.0.0.1',port,username:'CompatBot',auth:'offline',version});
 bot.on('error',e=>console.log('BOT',e.message));bot.on('kicked',r=>console.log('KICK',JSON.stringify(r)));
 await once(bot,'spawn');await sleep(700);
}
function visuals(){return Object.values(bot.entities).filter(e=>visual==='chest_minecart'?(e.name||'').includes('minecart'):e.name===(visual==='tombstone'?'item_display':'mannequin'));}
function target(){return visual==='chest_minecart'?visuals()[0]:Object.values(bot.entities).find(e=>e.name==='interaction');}
async function open(){await until(()=>target(),'click target');await bot.activateEntity(target());await until(()=>bot.currentWindow?.slots[7],'corpse inventory');}
async function click(slot,mode=0){try{await bot.clickWindow(slot,0,mode);}catch(e){console.log('CLIENT CLICK',e.message);}await sleep(350);}
function count(name){return bot.inventory.slots.filter(Boolean).filter(i=>i.name===name).reduce((s,i)=>s+i.count,0);}
async function stop(force=false){const done=once(server,'exit');if(force)server.kill('SIGKILL');else server.stdin.write('stop\n');await done;log.end();if(bot){bot.end();bot=null;}await sleep(1200);}
async function item(slot,name,count=1){await cmd(legacy?`replaceitem entity CompatBot slot.${slot} ${name} ${count}`:`item replace entity CompatBot ${slot} with ${name} ${count}`);}
(async()=>{
 await start();await connect();
 await cmd('gamerule keepInventory false');await cmd('gamerule doMobSpawning false');await cmd('gamemode creative CompatBot');
 await cmd('tp CompatBot 8.5 81 8.5',1200);await cmd('fill 0 79 0 15 79 15 stone');await cmd('tp CompatBot 8.5 80 8.5');await cmd('spawnpoint CompatBot 8 80 11');
 await item('hotbar.0','diamond',12);await item('inventory.3','emerald',5);await item('armor.head','iron_helmet');
 await cmd('gamemode survival CompatBot');
 const dead=once(bot,'death');await cmd('kill CompatBot');await dead;await sleep(2200);
 if(bot.health<=0){bot.respawn();await sleep(1000);}
 await cmd('tp CompatBot 8.5 80 11');await open();
 assert.equal(bot.currentWindow.slots[45]?.name,'diamond');assert.equal(bot.currentWindow.slots[21]?.count,5);
 await click(45,1);await until(()=>!bot.currentWindow.slots[45],'Shift withdrawal');
 await click(81,1);assert.equal(bot.currentWindow.slots[45],null,'Shift from player inventory must not deposit');
 await click(6,1);assert.equal(bot.currentWindow.slots[21]?.count,5,'Shift on action button must not claim all');
 bot.closeWindow(bot.currentWindow);await sleep(350);assert.equal(count('diamond'),12);
 await cmd('save-all flush',1200);await stop(true);
 await start();await connect();await cmd('tp CompatBot 8.5 80 11');await open();
 assert.equal(visuals().length,1,'one visual after forced stop');assert.equal(bot.currentWindow.slots[45],null);
 assert.equal(bot.currentWindow.slots[21]?.count,5);await click(6);
 await until(()=>!bot.currentWindow&&visuals().length===0,'empty corpse cleanup');
 assert.equal(count('diamond'),12);assert.equal(count('emerald'),5);assert.equal(count('iron_helmet'),1);
 assert(!/UnsupportedClassVersionError|NoClassDefFoundError|NoSuchMethodError/.test(output));
 console.log('PASS',version,visual,'Shift withdraw, blocked deposit, restart reconciliation, exact inventory and empty cleanup');
 await stop();process.exit(0);
})().catch(async error=>{console.error(error.stack);if(server?.exitCode===null)await stop();process.exit(1);});
