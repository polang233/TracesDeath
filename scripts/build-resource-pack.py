"""Build the TracesDeath prototype resource pack using only Python's standard library."""
from pathlib import Path
import hashlib
import json
import random
import shutil
import struct
import zlib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'resource-pack'
ASSETS = PACK / 'assets' / 'tracesdeath'

class Canvas:
    def __init__(self, width, height, color=(0, 0, 0, 0)):
        self.width, self.height = width, height
        self.pixels = [color] * (width * height)

    def rect(self, x, y, width, height, color):
        if len(color) == 3:
            color = (*color, 255)
        for yy in range(max(0, y), min(self.height, y + height)):
            for xx in range(max(0, x), min(self.width, x + width)):
                self.pixels[yy * self.width + xx] = color

    def bevel(self, x, y, width, height, fill, light, dark):
        self.rect(x, y, width, height, dark)
        self.rect(x, y, width - 1, height - 1, light)
        self.rect(x + 1, y + 1, width - 2, height - 2, fill)

    def save(self, path, scale=1):
        path.parent.mkdir(parents=True, exist_ok=True)
        rows = []
        for y in range(self.height):
            row = b'\0' + b''.join(bytes(p) * scale for p in self.pixels[y*self.width:(y+1)*self.width])
            rows.extend([row] * scale)
        def chunk(kind, data):
            return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data) & 0xffffffff)
        data = b'\x89PNG\r\n\x1a\n'
        data += chunk(b'IHDR', struct.pack('!2I5B', self.width*scale, self.height*scale, 8, 6, 0, 0, 0))
        data += chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b'')
        path.write_bytes(data)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def texture(name, palette, seed, moss=False):
    rng = random.Random(seed)
    image = Canvas(16, 16, (*palette[0], 255))
    for y in range(16):
        for x in range(16):
            image.rect(x, y, 1, 1, rng.choice(palette))
    if moss:
        for x, y, w, h in [(0,0,7,2),(0,2,3,5),(2,5,3,3),(12,12,4,4),(10,14,3,2)]:
            image.rect(x,y,w,h,(91,113,49))
        image.rect(1,1,4,1,(130,150,68))
        image.rect(12,13,2,1,(123,145,63))
    image.save(ASSETS/'textures'/'block'/f'{name}.png')


def box(start, end, texture_name):
    return {'from': start, 'to': end,
            'faces': {side:{'uv':[0,0,16,16], 'texture':'#'+texture_name}
                      for side in ['north','south','east','west','up','down']}}


def build_model():
    texture('stone',[(111,116,113),(120,125,121),(128,132,126),(115,121,116)],3)
    texture('mossy_stone',[(112,117,112),(126,130,120),(118,123,115)],4,True)
    texture('plaque',[(52,62,62),(58,69,68),(64,75,72)],5)
    emblem=Canvas(16,16,(18,104,115,255))
    emblem.bevel(1,1,14,14,(42,182,186),(130,239,220),(18,111,132))
    emblem.rect(4,3,4,3,(212,255,241))
    emblem.rect(9,10,4,3,(24,146,159))
    emblem.save(ASSETS/'textures/block/emblem.png')
    elements=[box([1,0,1],[15,2,15],'moss'),box([2,2,3],[14,4,13],'stone'),
              box([3,4,6],[13,19,10],'moss'),box([4,19,6],[12,22,10],'stone'),
              box([5,22,6],[11,23,10],'stone'),
              box([5,8,5.75],[11,16,6],'plaque'),box([7,11,5.5],[9,13,5.75],'emblem'),
              box([5,8,10],[11,16,10.25],'plaque'),box([7,11,10.25],[9,13,10.5],'emblem')]
    write_json(ASSETS/'models/tombstone.json',{
        'textures':{'stone':'tracesdeath:block/stone','moss':'tracesdeath:block/mossy_stone',
                    'plaque':'tracesdeath:block/plaque','emblem':'tracesdeath:block/emblem',
                    'particle':'tracesdeath:block/stone'},
        'gui_light':'side','elements':elements,
        'display':{'gui':{'rotation':[30,225,0],'translation':[0,-2,0],'scale':[.6,.6,.6]}}})
    write_json(ASSETS/'items/tombstone.json',{'model':{'type':'minecraft:model','model':'tracesdeath:tombstone'}})


def build_panel():
    # Only the corpse panel and the inventory-label band are drawn. Player slots begin at y=140.
    image=Canvas(176,138,(78,81,76,255))
    image.bevel(1,1,174,136,(143,147,139),(206,210,198),(40,45,39))
    image.bevel(3,3,170,132,(161,164,154),(184,191,177),(70,77,65))
    image.rect(6,5,164,10,(73,80,71))
    rng=random.Random(29)
    for _ in range(200):
        x=rng.choice([rng.randrange(0,6),rng.randrange(170,176)])
        y=rng.randrange(0,125)
        image.rect(x,y,rng.randrange(1,3),rng.randrange(1,4),rng.choice([(90,101,79),(102,116,75),(143,153,119),(58,70,52)]))
    for x,y in [(22,1),(46,2),(97,0),(131,3),(169,16),(2,64),(172,89)]:
        image.rect(x,y,2,4,(44,51,43));image.rect(x+2,y+3,3,1,(44,51,43));image.rect(x+4,y+4,1,3,(44,51,43))
    for slot in range(9):
        image.bevel(7+slot*18,17,18,18,(139,139,139),(58,64,55),(222,228,213))
    # Worn stone separator with shallow carved marks, moss at the rim.
    image.bevel(6,36,164,17,(100,109,91),(172,181,155),(48,62,43))
    image.rect(9,39,158,1,(64,77,54));image.rect(9,49,158,1,(169,178,149))
    for x in range(15,160,23):
        image.rect(x,42,7,2,(71,82,61));image.rect(x+3,41,1,5,(71,82,61));image.rect(x+1,45,5,1,(129,139,113))
    for x,y,w,h in [(8,37,13,2),(20,38,5,3),(153,48,12,3),(146,49,8,2),(3,119,4,10),(168,122,5,9)]:
        image.rect(x,y,w,h,(89,108,58))
    for y in [54,72,90,108]:
        for col in range(9):
            image.bevel(7+col*18,y-1,18,18,(139,139,139),(62,67,59),(232,234,220))
    # A thicker lower rim under the corpse hotbar; the native inventory label remains readable.
    image.bevel(6,125,164,12,(193,196,182),(223,227,209),(75,86,64))
    image.rect(9,126,155,1,(139,153,114))
    image.rect(162,130,5,3,(106,125,75));image.rect(166,132,3,3,(79,99,52))
    image.rect(2,135,172,2,(86,100,70))
    image.save(ASSETS/'textures/font/corpse_panel.png')
    write_json(PACK/'assets/minecraft/font/default.json',{'providers':[
        {'type':'space','advances':{'‹':-8,'›':-169}},
        {'type':'bitmap','file':'tracesdeath:font/corpse_panel.png','ascent':13,'height':138,'chars':['◆']}
    ]})
    return image


def legacy_models():
    minecraft=PACK/'assets/minecraft'
    for carrier,entries,base in [
        ('stone',[(7310000,'tracesdeath:tombstone')],{'parent':'minecraft:block/stone'})
    ]:
        overrides=[];modern=[]
        for value,model in entries:
            overrides.extend([{'predicate':{'custom_model_data':value},'model':model},
                              {'predicate':{'custom_model_data':value+1},'model':'minecraft:item/'+carrier+'_tracesdeath_base'}])
            modern.extend([{'threshold':value,'model':{'type':'minecraft:model','model':model}},
                           {'threshold':value+1,'model':{'type':'minecraft:model','model':'minecraft:item/'+carrier+'_tracesdeath_base'}}])
        write_json(minecraft/f'models/item/{carrier}_tracesdeath_base.json',base)
        write_json(minecraft/f'models/item/{carrier}.json',{**base,'overrides':overrides})
        write_json(minecraft/f'items/{carrier}.json',{'model':{'type':'minecraft:range_dispatch','property':'minecraft:custom_model_data','index':0,
            'entries':modern,'fallback':{'type':'minecraft:model','model':'minecraft:item/'+carrier+'_tracesdeath_base'}}})


def preview_panel(panel):
    panel.save(PACK/'previews/upper-panel.png',4)


def verify():
    for file in ASSETS.rglob('*.json'):
        data=json.loads(file.read_text(encoding='utf-8'))
        if 'models' in file.parts:
            for value in data.get('textures',{}).values():
                if value.startswith('tracesdeath:'):
                    assert (ASSETS/'textures'/(value.split(':',1)[1]+'.png')).is_file(), value
        elif 'items' in file.parts and data['model']['type']=='minecraft:model':
            assert (ASSETS/'models'/(data['model']['model'].split(':',1)[1]+'.json')).is_file(), file
    for file in ASSETS.rglob('*.png'):
        assert file.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), file
    allowed={'models/item/stone.json','models/item/stone_tracesdeath_base.json','items/stone.json','font/default.json'}
    assert {str(p.relative_to(PACK/'assets/minecraft')).replace('\\','/') for p in (PACK/'assets/minecraft').rglob('*.json')}==allowed


def main():
    write_json(PACK/'pack.mcmeta',{'pack':{'description':'TracesDeath · 墓碑与原版风格遗体界面',
        'pack_format':15,'supported_formats':{'min_inclusive':15,'max_inclusive':88},'min_format':15,'max_format':88}})
    build_model();panel=build_panel();legacy_models();preview_panel(panel)
    shutil.copyfile(ROOT/'assets/logo-memorial-128.png', PACK/'pack.png')
    verify()
    output=ROOT/'build/resource-pack/TracesDeath-Prototype.zip'
    output.parent.mkdir(parents=True,exist_ok=True)
    paths=[PACK/'pack.mcmeta',PACK/'pack.png',*sorted((PACK/'assets').rglob('*'))]
    with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for file in paths:
            if file.is_file():
                info=zipfile.ZipInfo(file.relative_to(PACK).as_posix(),(2020,1,1,0,0,0))
                info.compress_type=zipfile.ZIP_DEFLATED
                archive.writestr(info,file.read_bytes())
    legacy=output.with_name('TracesDeath-Prototype-1.19.4.zip')
    with zipfile.ZipFile(output) as source, zipfile.ZipFile(legacy,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for info in source.infolist():
            content=source.read(info.filename)
            if info.filename=='pack.mcmeta':
                content=json.dumps({'pack':{'description':'TracesDeath · 墓碑与遗体界面','pack_format':13}},ensure_ascii=False).encode('utf-8')
            archive.writestr(info,content)
    legacy.with_suffix('.sha1').write_text(hashlib.sha1(legacy.read_bytes()).hexdigest()+'\n',encoding='utf-8')
    sha1=hashlib.sha1(output.read_bytes()).hexdigest()
    output.with_suffix('.sha1').write_text(sha1+'\n',encoding='utf-8')
    print(f'{output}\nSHA-1: {sha1}\nModel and texture references verified')

if __name__=='__main__':main()
