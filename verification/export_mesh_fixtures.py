"""Export bundled Bedrock rest-pose cubes using the renderer's documented pixel/pivot conventions.
These are geometry fixtures, not a running Forge renderer or headset capture.
"""
from pathlib import Path
import json, math, re
import numpy as np
P=Path(__file__).resolve().parent.parent
A=P/'src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz'
def transform(pos,rot=(0,0,0)):
 m=np.eye(4);m[:3,3]=np.array(pos)/16
 for axis,deg in [(2,rot[2]),(1,rot[1]),(0,rot[0])]:
  a=math.radians(deg);c,s=math.cos(a),math.sin(a);r=np.eye(4)
  if axis==0:r[1:3,1:3]=[[c,-s],[s,c]]
  if axis==1:r[0,0]=r[2,2]=c;r[0,2]=s;r[2,0]=-s
  if axis==2:r[0:2,0:2]=[[c,-s],[s,c]]
  m=m@r
 return m
def read_json(path):
 text=path.read_text()
 text=re.sub(r'("(?:\\.|[^"\\])*")|//[^\n]*|/\*.*?\*/',lambda m:m.group(1) or '',text,flags=re.S)
 text=re.sub(r',([\s]*[}\]])',r'\1',text)
 return json.loads(text)
lines=[];guns=0
for f in sorted((A/'display/guns').glob('*.json')):
 d=read_json(f);model=d.get('model','').split(':')[-1]
 path=A/'geo_models'/(model+'.json')
 if not path.exists():continue
 model=read_json(path);g=model.get('minecraft:geometry')
 if not g:continue
 bones={b['name']:b for b in g[0]['bones']}
 if 'magazine' not in bones or 'thirdperson_hand' not in bones:continue
 def ancestors(name):
  r=[]
  while name in bones:r.append(name);name=bones[name].get('parent')
  return r
 def matrix(name):
  b=bones[name];pv=np.array(b.get('pivot',[0,0,0]),float);pn=b.get('parent')
  if pn:
   parent=matrix(pn);pos=pv-np.array(bones[pn].get('pivot',[0,0,0]));pos[1]*=-1
  else:parent=np.eye(4);pos=pv.copy();pos[1]=24-pos[1]
  return parent@transform(pos,b.get('rotation',[0,0,0]))
 scale=np.array(d.get('transform',{}).get('scale',{}).get('thirdperson',[.6,.6,.6]))
 base=transform([0,0,0],[0,0,180])@np.diag([*scale,1])@np.linalg.inv(matrix('thirdperson_hand'))
 boxes={'body':[],'mag':[]}
 for name,b in bones.items():
  ancestry=ancestors(name)
  if any(n=='additional_magazine' or n.startswith('bullet') or n in ['lefthand','righthand'] or n.endswith('hand_pos') or n.startswith('mag_extended_') for n in ancestry):continue
  kind='mag' if 'magazine' in ancestry else 'body';bm=base@matrix(name)
  for c in b.get('cubes',[]):
   pv=np.array(b.get('pivot',[0,0,0]),float);cm=bm
   if 'rotation' in c:
    cpv=np.array(c.get('pivot',[0,0,0]),float);delta=cpv-pv;delta[1]*=-1
    cm=cm@transform(delta,c['rotation']);pv=cpv
   origin=np.array(c['origin'],float);size=np.array(c['size'],float);lo=origin-pv;lo[1]=pv[1]-origin[1]-size[1]
   inf=c.get('inflate',0);hi=(lo+size+inf)/16;lo=(lo-inf)/16
   boxes[kind].append([*cm.flatten(order='F'),*lo,*hi])
 if not boxes['mag']:continue
 axis=base[:3,:3]@np.array([0,-1,0]);axis/=np.linalg.norm(axis)
 lines.append('gun '+f.stem+' '+' '.join(map(str,axis))+' '+str(len(boxes['body']))+' '+str(len(boxes['mag'])))
 for kind in ['body','mag']:
  lines.extend(' '.join(f'{x:.9g}' for x in row) for row in boxes[kind])
 guns+=1
(P/'verification/mesh-fixtures.txt').write_text('\n'.join(lines)+'\n')
print('Exported',guns,'bundled gun magazine meshes; no game boot')
