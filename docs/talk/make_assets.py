"""make_assets.py -- sparkta talk deck asset builder
Version: 1.0 | 2026-09-30
Copies sparkta pages and PNG stills from a talk_out folder into this deck folder
and regenerates the thumbnails (img/thumb) and stills (img/still).
Usage:  python make_assets.py <path to talk_out>      (needs Pillow)
ASCII only."""
import glob, os, shutil, sys
from PIL import Image
src = sys.argv[1]; here = os.path.dirname(os.path.abspath(__file__))
skip = {"B06_nlsw_panels", "E01_table"}  # B06 dropped in demo v1.3
for f in glob.glob(os.path.join(src, "*.html")):
    n = os.path.basename(f)[:-5]
    if n not in skip: shutil.copy(f, os.path.join(here, "charts", n + ".html"))
for f in ("E01_export.pdf", "E01_export.svg", "E01_table.pdf"):
    if os.path.exists(os.path.join(src, f)): shutil.copy(os.path.join(src, f), os.path.join(here, "files", f))
if os.path.exists(os.path.join(src, "png", "E01_export.png")):
    shutil.copy(os.path.join(src, "png", "E01_export.png"), os.path.join(here, "files", "E01_export.png"))
for f in glob.glob(os.path.join(src, "png", "*.png")):
    n = os.path.basename(f)[:-4]
    if n in skip: continue
    im = Image.open(f).convert("RGB")
    c = im.crop((0, 0, im.width, min(im.height, int(im.width * 0.66))))
    c.resize((720, int(c.height * 720 / c.width)), Image.LANCZOS).save(os.path.join(here, "img", "thumb", n + ".jpg"), quality=85)
    s = im.crop((0, 0, im.width, min(im.height, 1760)))
    s.resize((1100, int(s.height * 1100 / s.width)), Image.LANCZOS).save(os.path.join(here, "img", "still", n + ".jpg"), quality=88)
print("assets refreshed from", src)
