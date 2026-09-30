#!/usr/bin/env python3
"""fidelity_check.py -- sparkta v3.6.0-t2f fix 4
Compares the numbers embedded in each post-estimation page with Stata's own
r(table), which the suite writes to test_out/_truth/<case>.csv (see _chk in
test_release_v360.do). This is the "does the chart show what Stata computed"
check; intent_check proves the page matches the COMMAND, this proves it
matches the NUMBERS.

Per case: the set of (b, lo, hi) triples found in the page must be a subset of
the triples in r(table) (subset, because show()/omit() drop coefficients and
Pre_avg/Post_avg rows stay off the axis). Tolerance 1e-4 relative or 1e-6
absolute (pages print ~7 significant digits). When the command changes the
statistic (eform, rescale, another CI level, levels()) only b is compared,
because r(table) carries the untransformed 95% values.

Usage: python3 fidelity_check.py <test_out> [test_release_v360.do]
"""
import re, sys, os, glob, json

OUT = sys.argv[1] if len(sys.argv) > 1 else 'test_out'
_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# fix9h-e: the repo layout keeps the suite under tests/ (the dev tree had it in the root)
DO  = sys.argv[2] if len(sys.argv) > 2 else next((c for c in [os.path.join(_ROOT, 'tests', 'test_release_v360.do'), os.path.join(_ROOT, 'test_release_v360.do')] if os.path.exists(c)), os.path.join(_ROOT, 'tests', 'test_release_v360.do'))
TRUTH = os.path.join(OUT, '_truth')

# ---------------------------------------------------------------- commands per case
src = open(DO, 'rb').read().decode('ascii', 'replace').replace('\r\n', '\n')
src = re.sub(r'\s*///\s*\n\s*', ' ', src)
cmds = {}
for m in re.finditer(r'sparkta\b([^\n]*?)export\("`OUT\'/([A-Za-z0-9_]+)\.html"\)', src):
    cmds[m.group(2)] = m.group(1)

def num(s):
    try: return float(s)
    except Exception: return None

def close(a, b):
    return abs(a - b) <= max(1e-6, 1e-4 * max(abs(a), abs(b)))

def triples_truth(path):
    rows = {}
    with open(path) as f:
        head = f.readline().rstrip('\n').split(',')[1:]
        for line in f:
            parts = line.rstrip('\n').split(',')
            rows[parts[0]] = [num(x) for x in parts[1:]]
    # r(table): rows b se t pvalue ll ul ... ; lwdid-style matrices: columns named b se ll ul
    if 'b' in rows:
        b, ll, ul = rows['b'], rows.get('ll'), rows.get('ul')
        out = []
        for j in range(len(b)):
            out.append((b[j], ll[j] if ll else None, ul[j] if ul else None))
        return out
    # statistics in columns
    idx = {n: i for i, n in enumerate(head)}
    out = []
    for name, vals in rows.items():
        if 'b' in idx: out.append((vals[idx['b']], vals[idx['ll']] if 'll' in idx else None, vals[idx['ul']] if 'ul' in idx else None))
    return out

def triples_page(html):
    """(b, lo, hi) per plotted coefficient / period / margin; lo/hi None when not drawn"""
    out = []
    # coefplot: var _cpTip=[{n:'..',b:..,se:..,p:..,lo:..,hi:..},...]
    m = re.search(r"var _cpTip=\[(.*?)\];", html, re.S)
    if m:
        for t in re.finditer(r"\{[^}]*?b:([-\d.e+]+)[^}]*?lo:([-\d.e+]+|null|NaN)[^}]*?hi:([-\d.e+]+|null|NaN)", m.group(1)):
            out.append((num(t.group(1)), num(t.group(2)), num(t.group(3))))
        return out, 'coefplot'
    # eventstudy: esCI_<id>=[[t,lo,hi,lo2,hi2,pre,m(,ref)],...] + point data {x:..,y:..,t:..}
    m = re.search(r"esCI_\w+=\[(.*?)\];", html, re.S)
    if m:
        ci = {}
        for t in re.finditer(r"\[([-\d.e+]+),([-\d.e+]+),([-\d.e+]+),[^\]]*?\]", m.group(1)):
            rec = t.group(0)
            if rec.rstrip(']').endswith(',1') and rec.count(',') >= 7: continue   # reference record
            ci[round(num(t.group(1)), 6)] = (num(t.group(2)), num(t.group(3)))
        for t in re.finditer(r"\{x:([-\d.e+]+),y:([-\d.e+]+),t:([-\d.e+]+)(?:,ref:1)?", html):
            if 'ref:1' in t.group(0): continue
            k = round(num(t.group(3)), 6)
            lo, hi = ci.get(k, (None, None))
            out.append((num(t.group(2)), lo, hi))
        return out, 'eventstudy'
    # marginsplot: var lo=[..],hi=[..] (null = not estimable / base level); data:[..] (categorical x)
    # or data:[{x:..,y:..},..] (numeric x); coefplot multi-model: lo0/hi0 .. per model + {x,y,_ci}
    los = re.findall(r"var lo=\[([^\]]*)\],hi=\[([^\]]*)\]", html)
    if los:
        bs = []
        for d in re.findall(r"data:\[((?:null|[-\d.e+]+)(?:, ?(?:null|[-\d.e+]+))*)\]", html):
            vals = [num(x) for x in d.split(',')]
            if vals and any(v is not None for v in vals): bs.extend(vals)
        if not bs:
            bs = [num(y) for y in re.findall(r"\{x:[-\d.e+]+,y:([-\d.e+]+)\}", html)]
        lo = [num(x) for pair in los for x in pair[0].split(',') if x.strip()]
        hi = [num(x) for pair in los for x in pair[1].split(',') if x.strip()]
        n = min(len(bs), len(lo), len(hi))
        for i in range(n):
            if bs[i] is None: continue
            out.append((bs[i], lo[i], hi[i]))
        return out, 'marginsplot'
    multi = re.findall(r"var lo(\d+)=\[([^\]]*)\],hi\d+=\[([^\]]*)\]", html)
    if multi:
        # datasets appear in model order; within a dataset each point's _ci is the ROW index into lo<model>/hi<model>
        arrs = {k: ([num(x) for x in lo_s.split(',')], [num(x) for x in hi_s.split(',')]) for k, lo_s, hi_s in multi}
        dsets = re.findall(r"data:\[((?:\{x:[-\d.e+]+,y:[-\d.e+]+,_ci:\d+\},?)+)\]", html)
        for m, d in enumerate(dsets):
            lo, hi = arrs.get(str(m), ([], []))
            for y, ci in re.findall(r"\{x:[-\d.e+]+,y:([-\d.e+]+),_ci:(\d+)\}", d):
                i = int(ci)
                out.append((num(y), lo[i] if i < len(lo) else None, hi[i] if i < len(hi) else None))
        return out, 'coefplot-multi'
    pts = re.findall(r"\{x:[-\d.e+]+,y:([-\d.e+]+),_ci:\d+\}", html)
    if pts:
        for y in pts: out.append((num(y), None, None))
        return out, 'coefplot-multi'
    return out, None

def match(page, truth, b_only):
    missing = []
    for (b, lo, hi) in page:
        ok = False
        for (tb, tl, tu) in truth:
            if tb is None or b is None: continue
            if not close(b, tb): continue
            if b_only or lo is None or hi is None or tl is None or tu is None: ok = True; break
            if close(lo, tl) and close(hi, tu): ok = True; break
        if not ok: missing.append((b, lo, hi))
    return missing

TRANSFORMS = re.compile(r"\b(eform|rescale|cilevel\(|level\(|levels\(|transform\(|se\(|ci\(|df\()", re.I)

results = []; bad = 0; checked = 0
for path in sorted(glob.glob(os.path.join(TRUTH, '*.csv'))):
    name = os.path.basename(path)[:-4]
    hp = os.path.join(OUT, name + '.html')
    if not os.path.exists(hp): continue
    html = open(hp, encoding='utf-8', errors='replace').read()
    page, kind = triples_page(html)
    if kind is None or not page:
        results.append(f"SKIP {name:36s} no numeric block recognised"); continue
    truth = triples_truth(path)
    cmd = cmds.get(name, '')
    import math
    if re.search(r'\beform\b', cmd):     # page shows exp(b), exp(ll), exp(ul): transform the truth, compare fully
        truth = [tuple(None if v is None else math.exp(v) for v in t) for t in truth]
        cmd = re.sub(r'\beform\b', '', cmd)
    b_only = bool(TRANSFORMS.search(cmd))
    miss = match(page, truth, b_only)
    checked += 1
    if miss:
        bad += 1
        results.append(f"FAIL {name:36s} {kind:11s} {len(page)-len(miss)}/{len(page)} values match r(table)" + (" [b only]" if b_only else ""))
        for mm in miss[:4]: results.append(f"        x page has b={mm[0]} lo={mm[1]} hi={mm[2]} -- not in r(table)")
    else:
        results.append(f"OK   {name:36s} {kind:11s} {len(page)} values match r(table)" + (" [b only]" if b_only else ""))
print('\n'.join(results))
print(f"\nfidelity_check: {checked - bad}/{checked} pages match Stata's r(table)" + ("" if os.path.isdir(TRUTH) else " (no _truth/ folder -- suite older than t2f fix 4)"))
sys.exit(1 if bad else 0)
