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


def texture(name, palette, seed, moss=False, carved=False, plaque=False):
    # Warm sandstone, bronze-brown plaque and restrained verdigris weathering.
    rng = random.Random(seed)
    image = Canvas(32, 32, (*palette[0], 255))
    for y in range(32):
        for x in range(32):
            image.rect(x, y, 1, 1, rng.choice(palette))
    if plaque:
        image.bevel(1,1,30,30,(67,49,37),(181,145,87),(36,28,24))
        image.bevel(3,3,26,26,(77,58,43),(45,33,26),(133,105,68))
        # Chiseled abstract ornaments stay outside the central player-head area.
        for x,y,w,h in [(6,6,7,1),(6,6,1,5),(19,6,7,1),(25,6,1,5),
                        (7,25,5,1),(14,24,4,1),(21,25,4,1)]:
            image.rect(x,y,w,h,(38,28,23));image.rect(x,y+1,w,1,(150,118,76))
        image.rect(14,5,4,2,(190,151,90))
    else:
        # Fine, stepped fractures with one-pixel chipped highlights.
        for points in [[(8,0),(8,3),(9,4),(10,5),(10,8),(11,9),(12,10)],
                       [(27,19),(26,20),(26,23),(25,24),(23,25),(23,28),(22,29),(22,31)]]:
            for x,y in points:
                image.rect(x,y,1,2,(136,114,82));image.rect(x+1,y,1,1,(239,222,184))
        for _ in range(9):
            x,y=rng.randrange(3,29),rng.randrange(3,29)
            image.rect(x,y,2,1,(230,211,172));image.rect(x+1,y+1,1,1,(174,151,113))
        if carved:
            # A shallow geometric border unlike a normal stone/stone-brick block.
            image.rect(1,2,30,1,(242,226,191));image.rect(1,3,30,1,(146,120,83))
            image.rect(1,28,30,1,(146,120,83));image.rect(1,29,30,1,(226,207,164))
            for x in range(4,29,8):
                image.rect(x,8,4,1,(146,120,83));image.rect(x,9,1,5,(146,120,83))
                image.rect(x+1,13,3,1,(146,120,83));image.rect(x+2,10,1,3,(237,218,177))
    if moss:
        for x,y,radius in [(2,3,4),(28,29,4)]:
            for yy in range(max(0,y-radius),min(32,y+radius+1)):
                for xx in range(max(0,x-radius),min(32,x+radius+1)):
                    if abs(xx-x)+abs(yy-y)<radius+rng.randrange(-2,3) and rng.random()<.65:
                        image.rect(xx,yy,1,1,rng.choice([(62,107,91),(80,127,108),(117,153,128)]))
    image.save(ASSETS/'textures'/'block'/f'{name}.png')


def box(start, end, texture_name):
    return {'from': start, 'to': end,
            'faces': {side:{'uv':[0,0,16,16], 'texture':'#'+texture_name}
                      for side in ['north','south','east','west','up','down']}}


def build_model():
    texture('stone',[(207,190,155),(214,198,164),(220,205,174),(210,194,160)],3,carved=True)
    texture('mossy_stone',[(205,187,150),(213,196,161),(218,202,169)],4,True)
    texture('plaque',[(66,47,34),(74,55,40),(82,62,46)],5,plaque=True)
    elements=[box([1,0,1],[15,2,15],'moss'),box([2,2,3],[14,4,13],'stone'),
              box([3,4,6],[13,19,10],'moss'),box([4,19,6],[12,22,10],'stone'),
              box([5,22,6],[11,23,10],'stone'),
              box([4.5,8,5.5],[11.5,18,6],'plaque'),
              box([5,8,10],[11,16,10.25],'plaque')]
    write_json(ASSETS/'models/tombstone.json',{
        'textures':{'stone':'tracesdeath:block/stone','moss':'tracesdeath:block/mossy_stone',
                    'plaque':'tracesdeath:block/plaque',
                    'particle':'tracesdeath:block/stone'},
        'gui_light':'side','elements':elements,
        'display':{'gui':{'rotation':[30,225,0],'translation':[0,-2,0],'scale':[.6,.6,.6]}}})
    write_json(ASSETS/'items/tombstone.json',{'model':{'type':'minecraft:model','model':'tracesdeath:tombstone'}})


def build_panel():
    # Artwork is exported from the approved source via export-gui-texture.ps1.
    texture=ASSETS/'textures/font/corpse_panel.png'
    assert texture.is_file(), 'Run scripts/export-gui-texture.ps1 first'
    assert struct.unpack('!II',texture.read_bytes()[16:24])==(704,552)
    write_json(PACK/'assets/minecraft/font/default.json',{'providers':[
        {'type':'space','advances':{'‹':-8,'›':-169}},
        {'type':'bitmap','file':'tracesdeath:font/corpse_panel.png','ascent':13,'height':138,'chars':['◆']}
    ]})


def legacy_models():
    minecraft=PACK/'assets/minecraft'
    for carrier,entries,base in [
        ('stone',[(7310000,'tracesdeath:tombstone'),(7310002,'tracesdeath:blank')],{'parent':'minecraft:block/stone'}),
        ('stone_pressure_plate',[(7310000,'tracesdeath:tombstone')],{'parent':'minecraft:block/stone_pressure_plate'}),
        ('polished_blackstone',[(7310002,'tracesdeath:blank')],{'parent':'minecraft:block/polished_blackstone'}),
        ('mossy_stone_bricks',[(7310002,'tracesdeath:blank')],{'parent':'minecraft:block/mossy_stone_bricks'})
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


def build_separator():
    # Vanilla panes remain visible without the pack; the reserved model is transparent with it.
    Canvas(16,16).save(ASSETS/'textures/item/blank.png')
    write_json(ASSETS/'models/blank.json',{'parent':'minecraft:item/generated','textures':{'layer0':'tracesdeath:item/blank'}})
    carrier='black_stained_glass_pane'
    base={'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:block/black_stained_glass'}}
    root=PACK/'assets/minecraft'
    write_json(root/f'models/item/{carrier}_tracesdeath_base.json',base)
    fallback='minecraft:item/'+carrier+'_tracesdeath_base'
    write_json(root/f'models/item/{carrier}.json',{**base,'overrides':[
        {'predicate':{'custom_model_data':7310200},'model':'tracesdeath:blank'},
        {'predicate':{'custom_model_data':7310201},'model':fallback}]})
    write_json(root/f'items/{carrier}.json',{'model':{'type':'minecraft:range_dispatch','property':'minecraft:custom_model_data','index':0,
        'entries':[{'threshold':7310200,'model':{'type':'minecraft:empty'}},{'threshold':7310201,'model':{'type':'minecraft:model','model':fallback}}],
        'fallback':{'type':'minecraft:model','model':fallback}}})


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
    allowed={'models/item/stone.json','models/item/stone_tracesdeath_base.json','items/stone.json','font/default.json','models/item/black_stained_glass_pane.json','models/item/black_stained_glass_pane_tracesdeath_base.json','items/black_stained_glass_pane.json','models/item/stone_pressure_plate.json','models/item/stone_pressure_plate_tracesdeath_base.json','items/stone_pressure_plate.json','models/item/polished_blackstone.json','models/item/polished_blackstone_tracesdeath_base.json','items/polished_blackstone.json','models/item/mossy_stone_bricks.json','models/item/mossy_stone_bricks_tracesdeath_base.json','items/mossy_stone_bricks.json'}
    assert {str(p.relative_to(PACK/'assets/minecraft')).replace('\\','/') for p in (PACK/'assets/minecraft').rglob('*.json')}==allowed


def main():
    version=next(line.split('=',1)[1].strip() for line in (ROOT/'gradle.properties').read_text(encoding='utf-8').splitlines() if line.startswith('version='))
    write_json(PACK/'pack.mcmeta',{'pack':{'description':f'作者：Polang | 版本：{version}\n墓碑专用材质与遗体界面装饰',
        'pack_format':15,'supported_formats':{'min_inclusive':15,'max_inclusive':88},'min_format':15,'max_format':88}})
    build_model();build_panel();legacy_models();build_separator()
    shutil.copyfile(ROOT/'assets/logo-memorial-128.png', PACK/'pack.png')
    verify()
    output=ROOT/'build/resource-pack/TracesDeath.zip'
    output.parent.mkdir(parents=True,exist_ok=True)
    paths=[PACK/'pack.mcmeta',PACK/'pack.png',*sorted((PACK/'assets').rglob('*'))]
    with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for file in paths:
            if file.is_file():
                info=zipfile.ZipInfo(file.relative_to(PACK).as_posix(),(2020,1,1,0,0,0))
                info.compress_type=zipfile.ZIP_DEFLATED
                archive.writestr(info,file.read_bytes())
    sha1=hashlib.sha1(output.read_bytes()).hexdigest()
    output.with_suffix('.sha1').write_text(sha1+'\n',encoding='utf-8')
    print(f'{output}\nSHA-1: {sha1}\nModel and texture references verified')

if __name__=='__main__':main()
