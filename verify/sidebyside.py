#!/usr/bin/env python3
"""sidebyside.py -- sparkta v3.6.0-t2g
For every _stata/<case>.png the suite exported (Stata's own marginsplot / coefplot),
paste it next to sparkta's pixel render of <case>.html and write
_sidebyside/<case>.png. This does not judge; it makes "does it look like Stata"
a one-glance check for the reviewer. Requires node-canvas (pixel_check.js
renders the PNG with PIXEL_PNG=1).
Usage: python3 sidebyside.py <test_out>
"""
import os, sys, glob, subprocess, shutil
OUT = sys.argv[1] if len(sys.argv) > 1 else 'test_out'
HERE = os.path.dirname(os.path.abspath(__file__))
st = sorted(glob.glob(os.path.join(OUT, '_stata', '*.png')))
if not st:
    print('sidebyside: no _stata/*.png in this run (suite older than t2g, or graph export failed)'); sys.exit(0)
try:
    from PIL import Image
except ImportError:
    print('sidebyside: Pillow not installed -- skipped'); sys.exit(0)
tmp = os.path.join(OUT, '_sbs_tmp'); os.makedirs(tmp, exist_ok=True)
for p in st:
    name = os.path.basename(p)[:-4]
    h = os.path.join(OUT, name + '.html')
    if os.path.exists(h): shutil.copy(h, tmp)
env = dict(os.environ, PIXEL_PNG='1')
r = subprocess.run(['node', os.path.join(HERE, 'pixel_check.js'), tmp], capture_output=True, text=True, env=env)
dest = os.path.join(OUT, '_sidebyside'); os.makedirs(dest, exist_ok=True)
made = 0
for p in st:
    name = os.path.basename(p)[:-4]
    ours = os.path.join(tmp, name + '.png')
    if not os.path.exists(ours): print(f'  {name}: sparkta render missing'); continue
    a = Image.open(p).convert('RGB')
    b = Image.open(ours).convert('RGBA'); bg = Image.new('RGBA', b.size, 'white'); bg.alpha_composite(b); b = bg.convert('RGB')
    hgt = max(a.height, b.height)
    sheet = Image.new('RGB', (a.width + b.width + 30, hgt + 30), 'white')
    sheet.paste(a, (10, 20)); sheet.paste(b, (a.width + 20, 20))
    try:
        from PIL import ImageDraw
        d = ImageDraw.Draw(sheet); d.text((10, 4), 'Stata: ' + name, fill='black'); d.text((a.width + 20, 4), 'sparkta', fill='black')
    except Exception: pass
    sheet.save(os.path.join(dest, name + '.png')); made += 1
shutil.rmtree(tmp, ignore_errors=True)
print(f'sidebyside: {made} sheet(s) in {dest}')
