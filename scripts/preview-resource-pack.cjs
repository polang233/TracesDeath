// Isolated resource-pack preview server. Console input is forwarded to the child server.
// node scripts/preview-resource-pack.cjs <java> <paper.jar> <plugin.jar> <new-directory> <accepted-eula.txt>
const fs = require('node:fs');
const path = require('node:path');
const {spawn} = require('node:child_process');
const readline = require('node:readline');
const [java, paperPath, pluginPath, directoryPath, eulaPath] = process.argv.slice(2);
if (!eulaPath) throw new Error('Missing arguments');
const directory = path.resolve(directoryPath);
if (fs.existsSync(directory)) throw new Error('Preview directory already exists');
if (!/^eula=true\s*$/m.test(fs.readFileSync(eulaPath, 'utf8'))) throw new Error('Accepted EULA file required');
fs.mkdirSync(directory, {recursive:true});
if (process.env.RESTART_RUNTIME_CACHE) for (const name of ['cache','libraries','versions']) {
 const source=path.join(process.env.RESTART_RUNTIME_CACHE,name);
 if(fs.existsSync(source)) fs.cpSync(source,path.join(directory,name),{recursive:true});
}
fs.copyFileSync(eulaPath,path.join(directory,'eula.txt'));
if(process.env.PREVIEW_WORLD_SOURCE) fs.cpSync(process.env.PREVIEW_WORLD_SOURCE,path.join(directory,'pack-test'),{recursive:true});
if(process.env.PREVIEW_RECORDS_SOURCE) fs.cpSync(process.env.PREVIEW_RECORDS_SOURCE,path.join(directory,'plugins/TracesDeath/corpses'),{recursive:true});
fs.mkdirSync(path.join(directory,'plugins/TracesDeath'),{recursive:true});
fs.writeFileSync(path.join(directory,'plugins/TracesDeath/config.yml'),
 'loot:\n  owner-only: false\n  claim-all-mode: restore_slots\ncorpse:\n  type: tombstone\n');
fs.writeFileSync(path.join(directory,'plugins/TracesDeath/tombstone.yml'),
 'gui:\n  enabled: true\ntest-server:\n  bind-address: 127.0.0.1\n  port: 8163\n  public-url: http://127.0.0.1:8163\n');
fs.writeFileSync(path.join(directory,'server.properties'),[
 'server-ip=127.0.0.1','server-port=25580','online-mode=false','level-name=pack-test','difficulty=peaceful',
 'level-type=minecraft:flat','generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}',
 'generate-structures=false','spawn-protection=0','view-distance=3','simulation-distance=3','max-players=4'
].join('\n'));
{
 const log=fs.createWriteStream(path.join(directory,'preview.log'));
 const child=spawn(java,['-Xms512m','-Xmx1536m','-Dterminal.jline=false','-Dterminal.ansi=false','-Dfile.encoding=UTF-8',
  '-jar',path.resolve(paperPath),'--nogui','--add-plugin',path.resolve(pluginPath)],{cwd:directory,windowsHide:true,stdio:['pipe','pipe','pipe']});
 child.stdout.on('data',data=>{process.stdout.write(data);log.write(data);});
 child.stderr.on('data',data=>{process.stderr.write(data);log.write(data);});
 child.on('exit',code=>{log.end(()=>process.exit(code||0));});
 readline.createInterface({input:process.stdin}).on('line',line=>child.stdin.write(line+'\n'));
 process.on('SIGINT',()=>child.stdin.write('stop\n'));
 console.log('PREVIEW SERVER PID',child.pid,'ADDRESS 127.0.0.1:25580');
 console.log('Use console: td testpack <player>');
}
