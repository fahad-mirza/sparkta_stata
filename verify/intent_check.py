#!/usr/bin/env python3
"""
intent_check.py -- does each HTML match the Stata command that produced it? (s8n)

Unlike expectations.json (hand-written), this DERIVES the expectations from the
command line in test_release_v360.do and the estimation / margins command that
precedes it, then compares them with what js_check.js extracted from the HTML
(_js_check_report.json). One rule per option; every rule prints what it expected
and what it saw, so a mismatch is explainable.

Usage: python3 verify/intent_check.py <test_out_dir> [<path/to/test_release_v360.do>]
Exit 1 if any case is misaligned. ASCII only.
"""
import re, sys, os, json

OUT = sys.argv[1] if len(sys.argv) > 1 else 'test_out'
_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# fix9h-e: the repo layout keeps the suite under tests/ (the dev tree had it in the root)
DO  = sys.argv[2] if len(sys.argv) > 2 else next((c for c in [os.path.join(_ROOT, 'tests', 'test_release_v360.do'), os.path.join(_ROOT, 'test_release_v360.do')] if os.path.exists(c)), os.path.join(_ROOT, 'tests', 'test_release_v360.do'))

# ----------------------------------------------------------------- parse do-file
src = open(DO, 'rb').read().decode('ascii', 'replace').replace('\r\n', '\n')
src = re.sub(r'\s*///\s*\n\s*', ' ', src)            # join continuations
lines = [l for l in src.split('\n')]

# ---------------- t2e: mirror of ado sparkta_color_norm (returns the rgb prefix expected in the HTML, or None to skip)
_STATA_COLS = dict(black=(0,0,0), white=(255,255,255), gray=(128,128,128), dimgray=(105,105,105), blue=(0,0,255), red=(255,0,0),
    green=(0,255,0), yellow=(255,255,0), cyan=(0,255,255), magenta=(255,0,255), navy=(26,71,111), maroon=(144,53,59),
    forest_green=(85,117,47), dkorange=(227,126,0), teal=(110,142,132), cranberry=(193,5,52), lavender=(165,165,223),
    khaki=(202,194,126), sienna=(160,82,45), emidblue=(123,146,168), emerald=(45,109,102), brown=(156,136,79), erose=(191,161,156),
    gold=(255,210,0), bluishgray=(205,210,217), lime=(187,205,47), ltblue=(173,216,230), olive=(107,142,35), orange=(255,165,0),
    orange_red=(255,69,0), pink=(255,192,203), purple=(128,0,128), chocolate=(210,105,30), dkgreen=(0,100,0), dknavy=(0,0,128),
    eggshell=(255,255,224), eltblue=(130,178,220), eltgreen=(179,226,175), ebg=(218,217,200), ebblue=(0,139,188), edkblue=(0,76,105),
    emidgreen=(89,168,89), midblue=(0,128,255), midgreen=(0,192,0), mint=(160,255,200), ltkhaki=(250,250,200), ltbluishgray=(234,242,243),
    olive_teal=(200,210,180), sand=(240,215,155), sandb=(255,210,0), stone=(215,200,175), dkred=(139,0,0), ltred=(255,128,128))
def color_norm(tok):
    s = tok.replace('"', '').strip()
    if not s or s.lower().startswith(('rgb(', 'rgba(')): return None
    alpha = inten = None
    m = re.search(r'%(\d+)$', s)
    if m: alpha = int(m.group(1)); s = s[:m.start()].strip()
    m = re.search(r'\*([0-9.]+)$', s)
    if m: inten = float(m.group(1)); s = s[:m.start()].strip()
    rgb = None
    m = re.match(r'^(\d+) (\d+) (\d+)$', s)
    if m: rgb = tuple(int(x) for x in m.groups())
    elif re.match(r'^#?[0-9A-Fa-f]{6}$', s): h = s.lstrip('#'); rgb = (int(h[:2], 16), int(h[2:4], 16), int(h[4:], 16))
    elif re.match(r'^gs(\d+)$', s.lower()): v = min(255, 16 * int(s[2:])); rgb = (v, v, v)
    elif s.lower() in _STATA_COLS: rgb = _STATA_COLS[s.lower()]
    if rgb is None: return s   # CSS pass-through: the name itself must appear
    if inten and 0 < inten < 1: rgb = tuple(255 - (255 - c) * inten for c in rgb)
    elif inten and inten > 1: rgb = tuple(c / inten for c in rgb)
    rgb = tuple(int(min(255, max(0, round(c)))) for c in rgb)
    return 'rgba(%d,%d,%d' % rgb   # alpha is applied by Java per role; only the rgb prefix is asserted

def parse_opts(cmd):
    """sparkta <varlist>, opt1(args) opt2 ... -> (varlist, {opt: args|True})"""
    body = cmd[len('sparkta'):].strip()
    if ',' in body: varlist, opts = body.split(',', 1)
    else: varlist, opts = body, ''
    out = {}; i = 0; s = opts
    while i < len(s):
        m = re.match(r'\s*([A-Za-z_][A-Za-z0-9_]*)', s[i:])
        if not m: i += 1; continue
        name = m.group(1).lower(); i += m.end()
        if i < len(s) and s[i] == '(':
            depth = 0; j = i
            while j < len(s):
                if s[j] == '(': depth += 1
                elif s[j] == ')':
                    depth -= 1
                    if depth == 0: break
                j += 1
            out[name] = s[i+1:j].strip(); i = j + 1
        else: out[name] = True
    return varlist.split(), out

cases = []
prev = []   # rolling context of non-sparkta commands
for ln in lines:
    st = ln.strip()
    if not st or st.startswith('//') or st.startswith('*') or st.startswith('_chk') or st.startswith('display') or st.startswith('file '): continue
    if st.startswith('capture noisily sparkta') or st.startswith('sparkta'):
        cmd = st.replace('capture noisily ', '')
        m = re.search(r'export\("`OUT\'/(r_[a-z0-9_]+)\.html"\)', cmd)
        if not m: continue
        varlist, opts = parse_opts(cmd)
        for k in ('over', 'by'):
            if k in opts and isinstance(opts[k], str) and ',' in opts[k]: opts[k] = opts[k].split(',')[0].strip()   # over(var, showmissing)
        # s8s: Stata-convention aliases -> sparkta names, so the rules below apply unchanged
        for al, pr in (('level','cilevel'),('keep','show'),('drop','omit'),('mcolor','colors'),('msymbol','pointstyle'),('msize','pointsize'),('lwidth','linewidth')):
            if al in opts and pr not in opts: opts[pr] = opts.pop(al)
        if 'sort' in opts and 'coefsort' not in opts and opts.get('type') in ('coefplot','eventstudy','marginsplot'): opts['coefsort'] = {'asc':'value','b':'value','v':'value','value':'value','desc':'desc','p':'pval','pval':'pval','se':'se','t':'se','abs':'abs'}.get(str(opts['sort']).lower(),'value'); opts.pop('sort')
        if 'nolabel' in opts: opts['novaluelabels'] = True
        if 'recastci' in opts and 'cistyle' not in opts:
            opts['cistyle'] = ' '.join({'rarea':'area','rband':'area','rcap':'whisker','rbar':'bar','rline':'band','rspike':'band'}.get(t, t) for t in opts.pop('recastci').split())
        if 'recast' in opts and opts['recast'] in ('connected','line'): opts['connected'] = True
        if 'coeflabels' in opts: opts['coeflabels'] = re.sub(r'(\w+)\s*=\s*"([^"]+)"', r'"\1/\2"', opts['coeflabels'])
        cases.append({'name': m.group(1), 'cmd': cmd, 'varlist': varlist, 'opts': opts,
                      'est': next((p for p in reversed(prev) if re.match(r'(quietly )?(regress|logit|logistic|poisson|reghdfe|probit)\b', p)), ''),
                      'margins': next((p for p in reversed(prev) if re.match(r'(quietly )?margins\b', p)), ''),
                      'stores': [p for p in prev if p.startswith('estimates store')]})
    else:
        prev.append(st)
        if len(prev) > 40: prev = prev[-40:]

# ----------------------------------------------------------------- observed
rep = {r['file'][:-5]: r for r in json.load(open(os.path.join(OUT, '_js_check_report.json')))}
man = {}
mp = os.path.join(OUT, '_manifest.csv')
if os.path.exists(mp):
    for row in open(mp).read().splitlines()[1:]:
        p = row.split(','); man[p[0]] = (int(p[1]), p[2] == '1')

VALUE_LABELS = {'foreign': ['Domestic', 'Foreign'], 'race': ['White', 'Black', 'Other'], 'married': ['Single', 'Married'], 'union': ['Nonunion', 'Union']}
VAR_LABELS = {'price': 'Price', 'mpg': 'Mileage (mpg)', 'weight': 'Weight (lbs.)', 'foreign': 'Car origin', 'rep78': 'Repair record 1978', 'wage': 'Hourly wage', 'race': 'Race', 'hours': 'Usual hours worked', 'married': 'Married', 'union': 'Union member'}

def html_key_roles(n):
    try: h = open(os.path.join(OUT, n + '.html')).read()
    except Exception: return []
    return re.findall(r"data-role='(\w+)'", h)

def type_map(t):
    return {'bar': 'bar', 'hbar': 'bar', 'stackedbar': 'bar', 'stackedbar100': 'bar', 'line': 'line', 'area': 'line', 'scatter': 'scatter',
            'bubble': 'bubble', 'pie': 'pie', 'donut': 'doughnut', 'cibar': 'barWithErrorBars', 'ciline': 'line', 'histogram': 'bar',
            'boxplot': 'boxplot', 'hbox': 'boxplot', 'violin': 'bar', 'coefplot': 'bar', 'eventstudy': 'scatter', 'marginsplot': 'line'}.get(t)   # t2d: event studies are numeric scatter charts

# ----------------------------------------------------------------- rules
def check(case):
    n = case['name']; o = case['opts']; t = o.get('type', 'bar'); info = rep.get(n, {}).get('info', {}) if n in rep else None
    R = []   # (rule, expected, observed, ok)
    def rule(name, exp, obs, ok): R.append((name, exp, obs, bool(ok)))

    # existence / error cases
    if n.startswith('r_err_'):
        if n not in man: return None
        rc, ex = man.get(n, (0, True))
        late = n.startswith(('r_err_saveas', 'r_err_browser'))   # the page is written before saveas() can fail
        rule('error case rejected', 'rc!=0' + ('' if late else ', no file'), f'rc={rc}, file={ex}', rc != 0 and (late or not ex))
        return R
    rc, ex = man.get(n, (None, None))
    if n not in man: return None   # t2c: case did not run in this session (conditional section) -> skipped, not failed
    rule('rendered', 'rc=0 and file', f'rc={rc} file={ex}', rc == 0 and ex and info is not None)
    if info is None: return R
    plugins = info.get('plugins', []); ax = info.get('axisTitles', {}); labels = [str(x) for x in info.get('labels', [])]
    nds = info.get('nDatasets', 0); dsl = info.get('datasetLabels', [])

    # chart type
    et = type_map(t)
    if t == 'marginsplot' and re.search(r'\bbar\b', o.get('cistyle', '')): et = 'bar'
    if 'matrix' in o and len(o['matrix'].split()) > 1: o['estnames'] = ' '.join(m.split('[')[0] for m in o['matrix'].split())   # t2c: matrix(A B) = multi-model
    if t == 'coefplot' and o.get('estnames'): et = 'scatter'
    if t == 'coefplot' and 'vertical' in o and not o.get('estnames'): et = 'line'   # vertical single-model coefplot = line chart, category x
    if t == 'coefplot' and 'vertical' not in o and not o.get('estnames') and o.get('coefstyle') != 'bar': et = 'scatter'   # fix8x: since fix8n the horizontal single-model coefplot is a scatter (real points, uniform hit targets); coefstyle(bar) stays a bar
    rule('chart type', et, info.get('type'), info.get('type') == et)
    if t == 'hbar' or (t == 'coefplot' and not o.get('estnames') and 'vertical' not in o): rule('horizontal', "indexAxis y", info.get('indexAxis'), info.get('indexAxis') == 'y')
    if t == 'hbar' and o.get('over') and 'xtitle' not in o and 'ytitle' not in o:
        rule('hbar axis titles follow the axes', {'x': VAR_LABELS.get(case['varlist'][0], case['varlist'][0]), 'y': VAR_LABELS.get(o['over'], o['over'])}, ax, ax.get('y') == VAR_LABELS.get(o['over'], o['over']) and ax.get('x') == VAR_LABELS.get(case['varlist'][0], case['varlist'][0]))
    if t == 'coefplot' and 'vertical' in o: rule('vertical', 'no indexAxis y', info.get('indexAxis'), info.get('indexAxis') != 'y')
    if t in ('stackedbar', 'stackedbar100'): rule('stacked', True, info.get('stacked'), info.get('stacked'))
    if t == 'violin': rule('violin plugin', 'customViolin', plugins, 'customViolin' in plugins)

    # title / context
    if 'title' in o: rule('title', o['title'].strip('"'), info.get('h1'), info.get('h1') == o['title'].strip('"'))
    else: rule('generated title', 'non-empty, not "Sparkta"', info.get('h1'), info.get('h1') not in ('', 'Sparkta'))
    rule('context line', 'non-empty', info.get('subtitle'), bool(info.get('subtitle')))
    if 'xtitle' in o: rule('xtitle', o['xtitle'].strip('"'), ax, o['xtitle'].strip('"') in ax.values())
    if 'ytitle' in o: rule('ytitle', o['ytitle'].strip('"'), ax, o['ytitle'].strip('"') in ax.values())

    # theme
    if o.get('theme', '').startswith('dark'):
        bg = info.get('bodyBg', '')
        lum = sum(int(bg[i:i+2], 16) for i in (1, 3, 5)) / 3 if len(bg) == 7 else 255
        rule('dark background', 'dark body bg', bg, lum < 80)
    if 'notimestamp' in o: rule('no footer stamp', 'absent', 'present' if 'Made with sparkta' in open(os.path.join(OUT, n + '.html')).read() else 'absent', 'Made with sparkta' not in open(os.path.join(OUT, n + '.html')).read())
    if 'nostats' in o: rule('no stats panel', False, info.get('hasStatsPanel'), not info.get('hasStatsPanel'))
    if 'filter' in o: rule('filter UI', True, info.get('hasFilterUi'), info.get('hasFilterUi'))
    if info is not None and not n.startswith('r_err_'):
        html = open(os.path.join(OUT, n + '.html')).read()
        if 'online' in o: rule('online: CDN scripts', 'cdn.jsdelivr.net present', 'CDN' if 'cdn.jsdelivr.net' in html else 'inline', 'cdn.jsdelivr.net' in html)
        else: rule('default page is self-contained', 'no CDN script tags', 'CDN' if 'cdn.jsdelivr.net' in html else 'inline', 'cdn.jsdelivr.net' not in html)
    # t2j fix8w (U21): the export toolbar is the default; notoolbar removes it (download is a no-op alias)
    if 'notoolbar' in o:
        html = open(os.path.join(OUT, n + '.html')).read()
        rule('notoolbar: no export buttons', 'absent', 'present' if '>PNG</button>' in html else 'absent', '>PNG</button>' not in html)
    elif info is not None and not n.startswith('r_err_'):
        html = open(os.path.join(OUT, n + '.html')).read()
        rule('export toolbar: PNG SVG PDF', ['PNG','SVG','PDF'], [b for b in ('PNG','SVG','PDF') if ('>' + b + '</button>') in html], all(('>' + b + '</button>') in html for b in ('PNG','SVG','PDF')))
        rule('SVG exporter + canvas2svg inlined', 'both present', 'yes' if ('function _spkChartToSvg' in html and 'C2S' in html) else 'no', 'function _spkChartToSvg' in html and 'C2S' in html)
        rule('print swap + stylesheet', 'beforeprint + @media print', 'yes' if ("addEventListener('beforeprint'" in html and '@media print' in html) else 'no', "addEventListener('beforeprint'" in html and '@media print' in html)
    if 'plotmargin' in o:
        pm = o['plotmargin'].split(); l, r, b_, t_ = (pm * 4)[:4] if len(pm) == 1 else ((pm[0], pm[0], pm[1], pm[1]) if len(pm) == 2 else pm)
        html = open(os.path.join(OUT, n + '.html')).read()
        if t in ('coefplot', 'eventstudy', 'marginsplot'):
            sc = rep[n]['info']; y = None
            # value-axis range must exceed data+CI range by the requested cushion (checked via viz_audit axisUse instead)
            neg = float(b_) < 0 or float(t_) < 0
            if neg: rule('plotmargin crops', 'axisUse > 1 (data beyond axis)', f"axisUse={sc.get('axisUse')}", sc.get('axisUse') is not None and sc.get('axisUse') > 1.0)
            else: rule('plotmargin applied', f'y cushion {b_}/{t_}% of range', f"axisUse={sc.get('axisUse')}", sc.get('axisUse') is not None and sc.get('axisUse') <= 1.0 / (1 + (float(b_) + float(t_)) / 100) + 0.02)
        else:
            rule('plotmargin -> ygrace', f"grace:'{b_}%'", 'present' if f"grace:'{b_}%'" in html else 'absent', f"grace:'{b_}%'" in html)
    if 'lpatterns' in o:
        pats = o['lpatterns'].replace('+', ' ').replace('|', ' ').split()
        want = [p != 'solid' for p in pats]; got = [bool(x) for x in info.get('borderDash', [])][:len(pats)]
        rule('lpatterns', want, got, want == got)
    if 'relabel' in o:
        want = o['relabel'].split('|'); rule('relabel', want, labels, all(w in labels for w in want))
    if 'leglabels' in o:
        html = open(os.path.join(OUT, n + '.html')).read(); want = o['leglabels'].split('|')
        rule('leglabels', want, 'in generateLabels' if all(w in html for w in want) else 'missing', all(w in html for w in want))
    if 'palette' in o and 'okabe' in o['palette']:
        cols = str(info.get('colors', '')); rule('palette okabe', 'Okabe-Ito rgba(0,0,0 / 230,159,0 / 86,180,233', cols[:80], 'rgba(0,0,0' in cols or 'rgba(230,159,0' in cols or 'rgba(86,180,233' in cols)
    # ---------------- t2e: colour normalisation -- every colour token the user typed must reach the page
    # as the rgb the ado's sparkta_color_norm produces (mirror of the ado table; keep the two in sync)
    if info is not None and not n.startswith('r_err_'):
        html = open(os.path.join(OUT, n + '.html')).read()
        for opt, mode in (('colors', 'space'), ('cicolors', 'pipe'), ('ylinecolor', 'pipe'), ('bgcolor', 'single'), ('plotcolor', 'single'), ('gridcolor', 'single'), ('titlecolor', 'single')):
            if opt not in o or 'palette' in o: continue
            toks = [o[opt]] if mode == 'single' else (o[opt].split('|') if mode == 'pipe' else re.findall(r'"[^"]*"|\S+', o[opt]))
            for idx, tk in enumerate(toks):
                if '`' in tk: continue                     # unresolved macro in the command text (colorpalette locals) -- cannot be mirrored
                if opt == 'colors' and idx >= max(nds, 1): continue   # colours beyond the number of series are never drawn
                exp = color_norm(tk)
                if exp is None: continue
                hx = ('#%02x%02x%02x' % tuple(int(x) for x in exp[5:].split(','))) if exp.startswith('rgba(') else exp
                found = exp in html or exp.replace('rgba(', 'rgb(') in html or hx.lower() in html.lower()
                rule(f'{opt}() token {tk}', exp, 'present' if found else 'absent', found)

    # ---------------- t2a: MATRIX MODE
    if 'matrix' in o and info is not None and not n.startswith('r_err_'):
        html = open(os.path.join(OUT, n + '.html')).read()
        def table_numbers(h):
            tb = re.search(r"<table[\s\S]*?</table>", h)
            return re.findall(r">\s*(-?\d[\d,]*\.\d{4})\s*<", tb.group(0)) if tb else []
        def table_rows(h):
            tb = re.search(r"<table[\s\S]*?</table>", h)
            return re.findall(r"<tr[^>]*>\s*<td[^>]*>([^<]*)<", tb.group(0)) if tb else []
        if n == 'r_mat_rt_table':
            ref = os.path.join(OUT, 'r_mat_rt_normal.html')
            if os.path.exists(ref):
                a_, b_ = table_numbers(open(ref).read()), table_numbers(html)
                same = len(a_) == len(b_) and all(abs(float(x.replace(',', '')) - float(y.replace(',', ''))) < 1e-4 for x, y in zip(a_, b_))
                rule('ROUND TRIP: matrix(r(table)) == e(b)/e(V) coefplot', f'{len(a_)} table numbers identical', f'{len(b_)} numbers, match={same}', same)
                ra, rb = table_rows(open(ref).read()), table_rows(html)
                rule('round trip: same coefficient rows', ra, rb, ra == rb)
        if n == 'r_mat_points_only':
            plugins_ = info.get('plugins', []) or []
            noci_ok = not any(str(pn).startswith(('cpWhisker', 'cpBand', 'cpArea', 'cpMWhisker', 'cpMBand', 'cpMArea')) for pn in plugins_) and "data-role='ci'" not in html
            rule('points only: no CI drawn (auto noci)', 'no CI plugin, no CI key entry', plugins_, noci_ok)
        # the elements-key rule below expects a CI entry unless noci was given: auto-noci pages carry none
        if n == 'r_mat_points_only': o['noci'] = True
        if n in ('r_mat_event_names', 'r_mat_event_csdid1', 'r_mat_event_summ', 'r_mat_csdid_event'):
            rows = table_rows(html)
            def tkey(x):
                m = re.match(r'^(?:t|T)m(\d+)$', x) or re.match(r'^(?:t|T)-(\d+)$', x)
                if m: return -int(m.group(1))
                m = re.match(r'^(?:t|T)p(\d+)$', x) or re.match(r'^(?:t|T)\+(\d+)$', x)
                if m: return int(m.group(1))
                if x in ('T', 'Tp0', 'tp0'): return 0
                return 9999
            times = [tkey(r) for r in rows if tkey(r) != 9999]
            rule('eventstudy from matrix: rows ordered by relative time', 'ascending', times, times == sorted(times) and len(times) >= 5)
            if 'show' in o: rule('summary row kept last (show(*))', 'Pre_avg/Post_avg after the series', rows[-1] if rows else None, bool(rows) and rows[-1].lower().replace('_', '') in ('preavg', 'postavg'))
            else: rule('summary rows off the time axis by default', 'no Pre_avg/Post_avg row', [r for r in rows if r.lower().replace('_','') in ('preavg','postavg')], not any(r.lower().replace('_','') in ('preavg','postavg') for r in rows))
        if n == 'r_mat_multi':
            rule('multi-matrix: two plot groups labelled from plotlabels()', ['Short', 'Full'], [x for x in ('Short', 'Full') if x in html], 'Short' in html and 'Full' in html)
        if n == 'r_mat_b_se_df':
            # t2i hardening: was hardcoded True (Astra). Really compare the df/t-based
            # CI width to its z-based sibling -- the t CI must be WIDER. Skips (no rule)
            # if a sibling page is absent, rather than asserting a false pass.
            def _ciw(fn):
                p = os.path.join(OUT, fn)
                if not os.path.exists(p): return None
                h = open(p).read()
                lo = re.search(r"lo:\s*'([-\d.]+)'", h); hi = re.search(r"hi:\s*'([-\d.]+)'", h)
                if not lo or not hi: return None
                try: return abs(float(hi.group(1)) - float(lo.group(1)))
                except ValueError: return None
            _wdf, _wz = _ciw('r_mat_b_se_df.html'), _ciw('r_mat_b_se.html')
            if _wdf is not None and _wz is not None:
                rule('df(30): t-based CI wider than z-based (r_mat_b_se)', 'df width > z width',
                     '%.4f vs %.4f' % (_wdf, _wz), _wdf > _wz)
    # ---------------- s9j: the elements key must list every non-dataset element the chart draws
    if info is not None and not n.startswith('r_err_'):
        html = open(os.path.join(OUT, n + '.html')).read()
        km = re.search(r"<div class='spk-shared-key spk-elements-key'.*?</div>", html, re.S)
        have = set(re.findall(r"data-role='(\w+)'", km.group(0))) if km else set()
        want = set()
        if t in ('coefplot', 'eventstudy', 'marginsplot'):
            if 'noci' not in o: want.add('ci')
            if 'levels' in o and 'noci' not in o: want.add('ci_inner')
            m = case['margins']
            if t == 'marginsplot':
                if re.search(r'dydx|contrast|pwcompare|margins +[a-z]{1,2}\.', m) and o.get('refval') != 'none': want.add('refline')
            elif o.get('refval') != 'none': want.add('refline')
            if 'pexline' in o: want.add('pexline')
        if t in ('cibar', 'ciline'): want.add('ci')
        if 'fit' in o: want.add('fit')
        if 'fitci' in o: want.add('fitci')
        if 'yline' in o or ('xline' in o and t in ('scatter', 'bubble', 'line', 'area')): want.add('refline')
        if 'yband' in o or ('xband' in o and t in ('scatter', 'bubble')): want.add('band')
        missing = sorted(want - have)
        if want: rule('elements key lists every drawn element', sorted(want), sorted(have), not missing)
    # ---------------- Section E (s9b): fit types and styling options
    if 'fit' in o:
        html = open(os.path.join(OUT, n + '.html')).read()
        ft = o['fit'].strip()
        tag_ = {'ma': 'MA-'}.get(ft, ft)
        rule(f'fit({ft}) dataset', f'"({tag_}...)" series label', [d for d in dsl if '(' in d][:3], any(f'({tag_}' in d for d in dsl))
        if 'fitci' in o: rule('fitci band', 'CI (upper)/(lower) datasets', [d for d in dsl if 'CI' in d][:2], any('CI' in d for d in dsl))
        if 'over' in o and o['over'] in VALUE_LABELS: rule('fit per group', VALUE_LABELS[o['over']], dsl[:6], all(any(v in d for d in dsl) for v in VALUE_LABELS[o['over']]))
    if o.get('ytype') == 'log' or o.get('xtype') == 'log':
        html = open(os.path.join(OUT, n + '.html')).read()
        # price min 3,291 -> decade 1000; weight min 1,760 -> 1000 (x has an explicit xrange in the suite case)
        got = re.findall(r"y:\{type:'logarithmic',min:([\d.]+)", html)
        rule('log y-axis anchored to the data decade', 'min:1000', got, bool(got) and abs(float(got[0]) - 1000) < 1e-6)
        rule('log ticks label sub-decades', 'span<=1.6 rule in callback', 'yes' if 'span<=1.6' in html else 'no', 'span<=1.6' in html)
    if 'fill' in o and len(case['varlist']) >= 2:
        first = VAR_LABELS.get(case['varlist'][0], case['varlist'][0]); cols = info.get('colors', []); dsl2 = info.get('datasetLabels', [])
        idx0 = next((i for i, d in enumerate(dsl2) if d.startswith(first)), None)
        rule('fill keeps colour identity', f'{first} uses series colour 0 (78,121,167)', str(cols[idx0])[:40] if idx0 is not None else 'not found', idx0 is not None and '78,121,167' in str(cols[idx0]))
    if 'datalabels' in o and t in ('bar','hbar','line'):
        html = open(os.path.join(OUT, n + '.html')).read()
        rule('datalabels headroom', "grace:'12%'", 'present' if "grace:'12%'" in html or 'ygrace' in o else 'absent', "grace:'12%'" in html or 'ygrace' in o)
        rule('datalabels via formatter', '_spkFmt in formatter', 'yes' if 'formatter:function(v){return v==null?\'\':_spkFmt(' in html else 'no', 'formatter:function(v){return v==null?\'\':_spkFmt(' in html)
        if o.get('stat') == 'count': rule('count axis title', 'Count', ax, 'Count' in ax.values())
    if o.get('by'):
        html = open(os.path.join(OUT, n + '.html')).read()
        rule('by(): one shared key above the grid', "spk-shared-key", 'present' if "class='spk-shared-key'" in html else 'absent', "class='spk-shared-key'" in html)
        rule('by(): panels wrap (no scroll strip)', 'auto-fit grid', 'yes' if 'repeat(auto-fit,minmax(380px,1fr))' in html else 'no', 'repeat(auto-fit,minmax(380px,1fr))' in html)
        rule('by(): panel n shown', 'panel-n', 'yes' if "class='panel-n'" in html else 'no', "class='panel-n'" in html)
        # s9h (user rule): each key glyph must carry the colour the chart draws FOR THAT ROLE
        roles = re.findall(r"data-role='(\w+)' data-color='([^']*)'", html)
        body = html.split("class='spk-shared-key'")[-1]
        bad = []
        for role, colr in roles:
            c = colr.lower()
            if role == 'median':   ok = ("mediancolor:'" + c) in body.lower()
            elif role == 'mean':   ok = ("meanbackgroundcolor:'" + c) in body.lower() or ("meancolor:'" + c) in body.lower()   # boxplot plugin vs custom violin
            elif role == 'box':    ok = ("backgroundcolor:'" + c) in body.lower() or ("backgroundcolor:['" + c) in body.lower() or c in body.lower()
            elif role == 'outlier': ok = ("outlierbordercolor:'" + c) in body.lower() or ("outliercolor:'" + c) in body.lower() or c in body.lower()
            elif role == 'whisker': ok = ("bordercolor:'" + c) in body.lower() or ("bordercolor:['" + c) in body.lower() or c in body.lower()
            elif role == 'refline': ok = c in body.lower()     # xline/yline colour is passed straight to the annotation config
            else:                  ok = c in body.lower()
            if not ok: bad.append(role + '=' + colr)
        rule('key glyph colours match the drawing (by role)', 'median/mean/box/series colours found in chart config', bad or 'all match', not bad)
        if t in ('boxplot','hbox','violin','hviolin'):
            want = ['median','mean','box','whisker'] + (['density'] if t.startswith('v') or t == 'hviolin' else ['outlier'])   # fix8x: violins draw no outliers (fix8w key change)
            have = [r for r, _ in roles]
            rule('key lists every drawn element', want, sorted(set(have)), all(w in have for w in want))
        if t in ('line','area') and ('lpattern' in o or 'lpatterns' in o):
            rule('key carries the dash pattern', 'stroke-dasharray in a series glyph', 'yes' if 'stroke-dasharray' in html.split("class='spk-shared-key'")[1].split('</div>')[0] else 'no', 'stroke-dasharray' in html.split("class='spk-shared-key'")[1].split('</div>')[0])
        if ('yline' in o or 'xline' in o):
            rule('key lists reference lines', "data-role='refline'", 'yes' if "data-role='refline'" in html else 'no', "data-role='refline'" in html)
        ys = re.findall(r"y:\{[^}]*?min:([-\d.e]+),max:([-\d.e]+)", html)
        if 'yfree' in o: rule('yfree: panels not forced to one range', 'no shared min/max', ys[:2], len(set(ys)) != 1 or not ys)
        elif t not in ('pie','donut','histogram','stackedbar100'): rule('by(): shared value axis', 'same min/max on every panel', ys[:3], bool(ys) and len(set(ys)) == 1)
    if 'stepped' in o: rule('stepped', o['stepped'], 'stepped' if "stepped:'" + o['stepped'] + "'" in open(os.path.join(OUT, n + '.html')).read() else 'absent', "stepped:'" + o['stepped'] + "'" in open(os.path.join(OUT, n + '.html')).read())
    if 'legend' in o and o['legend'] in ('bottom','left','right'): rule('legend position', o['legend'], info.get('legendPos'), True)
    if 'y2' in o: rule('secondary axis', 'yAxisID y2', 'present' if "yAxisID:'y2'" in open(os.path.join(OUT, n + '.html')).read() else 'absent', "yAxisID:'y2'" in open(os.path.join(OUT, n + '.html')).read())
    if 'histtype' in o: rule('histtype axis title', o['histtype'].capitalize(), ax, any(o['histtype'][:4].lower() in v.lower() for v in ax.values()))
    if 'cutout' in o: rule('donut cutout', o['cutout'] + '%', 'present' if "cutout:'" + o['cutout'] + "%'" in open(os.path.join(OUT, n + '.html')).read() or 'cutout:' + o['cutout'] in open(os.path.join(OUT, n + '.html')).read() else 'absent', "cutout" in open(os.path.join(OUT, n + '.html')).read())
    # ---------------- I5/I6 (s8y): hover content on data charts
    if t in ('bar', 'hbar', 'line', 'area', 'stackedbar') and o.get('over') and not o.get('by'):
        html = open(os.path.join(OUT, n + '.html')).read()
        rule('shared formatter present', '_spkFmt defined', 'yes' if 'window._spkFmt=function' in html else 'no', 'window._spkFmt=function' in html)
        rule('hover shows n and share', "'n = ' in tooltip", 'yes' if "n = '+_spkFmt(n,'int')" in html else 'no', "n = '+_spkFmt(n,'int')" in html)
        rule('hover title has over label', f"{VAR_LABELS.get(o['over'], o['over'])}", 'yes' if f"var _spkOverLabel='{VAR_LABELS.get(o['over'], o['over'])}'" in html else 'no', f"var _spkOverLabel='{VAR_LABELS.get(o['over'], o['over'])}'" in html)
    if t in ('pie', 'donut'):
        html = open(os.path.join(OUT, n + '.html')).read()
        rule('pie hover has raw values', '_spkPieRaw', 'yes' if 'var _spkPieRaw=[' in html else 'no', 'var _spkPieRaw=[' in html)
    # ---------------- base charts: over()/by()/varlist
    if t not in ('coefplot', 'eventstudy', 'marginsplot'):
        over = o.get('over'); by = o.get('by')
        if by: rule('by() panels', '>1 chart', info.get('nCharts'), info.get('nCharts', 0) > 1)
        if over and 'novaluelabels' in o and over == 'foreign': rule('nolabel -> raw codes', ['0','1'], labels[:2], all(c in labels for c in ('0','1')))
        if over and over in VALUE_LABELS and 'novaluelabels' not in o and t not in ('pie', 'doughnut', 'donut', 'boxplot', 'hbox', 'violin', 'histogram', 'scatter', 'bubble'):
            vl = VALUE_LABELS[over]
            # either groups on the x-axis (labels) or groups as series (dataset labels) -- both are valid layouts
            ok = all(v in labels for v in vl) or all(v in dsl for v in vl) or all(any(v in d for d in dsl) for v in vl)
            rule('over() value labels', vl, {'labels': labels[:6], 'series': dsl[:6]}, ok)
        if over == 'rep78' and t in ('bar', 'hbar', 'line', 'area', 'cibar', 'ciline') and 'relabel' not in o and 'xlabels' not in o:
            rule('over(rep78) levels', '1..5 on x', labels[:6], all(str(k) in labels for k in range(1, 6)))
        if len(case['varlist']) >= 2 and over and t in ('bar', 'line', 'area', 'stackedbar'):
            rule('multi-var series', len(case['varlist']), nds, nds == len(case['varlist']))
        if t == 'cibar' or t == 'ciline':
            lev = o.get('cilevel', '95'); rule('cilevel in context', f'{lev}% CI', info.get('subtitle'), f'{lev}% CI' in (info.get('subtitle') or ''))

    # ---------------- eventstudy (t2d): numeric relative-time axis, pre/post colours, esCI/esZero plugins
    if t == 'eventstudy' and info.get('type') == 'scatter':
        html = open(os.path.join(OUT, n + '.html')).read()
        rule('numeric time axis', 'x linear', info.get('numericX'), info.get('numericX') is True)
        if 'noci' in o: rule('noci', 'no esCI plugin', plugins, 'esCI' not in plugins)
        else: rule('CI plugin', 'esCI', plugins, 'esCI' in plugins)
        rule('null line plugin', 'esZero', plugins, 'esZero' in plugins)
        multi_es = bool(o.get('estnames')) or ('matrix' in o and len(o['matrix'].split()) > 1)
        k_es = 1 if not multi_es else len((o.get('estnames') or o['matrix']).split())
        # t2f: per model = pre + post [+ reference marker] [+ 1 line if together else 2 lines (pre, post)]
        has_ref = 'esRef:true' in html
        together = ('together' in o) or ('separate' not in o and 'matrix' not in o)   # t2f fix 2 default: estimation mode joins, matrix() separates
        want_ds = k_es * (2 + (1 if has_ref else 0) + ((1 if together else 2) if 'connected' in o else 0))
        rule('pre/post[/reference/line] datasets', want_ds, nds, nds == want_ds)
        if o.get('refperiod', '').lower() == 'none': rule('refperiod(none)', 'no reference marker', has_ref, not has_ref)
        if 'connected' in o and not together and has_ref: rule('leads/lags as separate lines', '(pre line) and (post line)', 'both' if '(pre line)' in html and '(post line)' in html else 'missing', '(pre line)' in html and '(post line)' in html)
        if not multi_es: rule('legend: Pre-treatment / Post-treatment', ['Pre-treatment', 'Post-treatment'], [d for d in dsl if d in ('Pre-treatment', 'Post-treatment')], all(x in dsl for x in ('Pre-treatment', 'Post-treatment')))
        if 'pexline' in o: rule('pexline', 'pexline key entry', 'pexline' in html_key_roles(n), 'pexline' in html_key_roles(n))
    # ---------------- coefplot / eventstudy (category axis)
    if t == 'coefplot' or (t == 'eventstudy' and info.get('type') != 'scatter'):
        multi = bool(o.get('estnames')); cs = o.get('cistyle', 'whisker' if t == 'coefplot' else 'bar')
        if multi:
            k = len(o['estnames'].split()); rule('models', k, nds, nds == k)
            base = {'whisker': 'cpMWhisker', 'bar': 'cpMWhisker', 'band': 'cpMBand', 'area': 'cpMArea'}[cs]
            if 'noci' in o: rule('noci', 'no cpM CI plugin', plugins, not any(p.startswith(('cpMWhisker', 'cpMBand', 'cpMArea')) for p in plugins))
            else: rule('cistyle', base, plugins, base in plugins)
            if 'levels' in o: rule('levels inner', 'cpMInner (key lists ci_inner)', plugins, 'cpMInner' in plugins)
            if 'estlabels' in o:
                want = o['estlabels'].split('~'); rule('estlabels', want, dsl, dsl == want)
        else:
            exp = {'whisker': 'cpWhisker', 'bar': 'cpRangeBar' if t == 'eventstudy' else 'cpWhisker', 'band': 'cpBand', 'area': 'cpArea'}[cs]
            if o.get('coefstyle') == 'bar' and cs in ('area','band'): exp = 'cpBarArea'   # s8x: CI rectangles over bars
            if 'noci' not in o and n != 'r_mat_points_only': rule('cistyle', exp, plugins, exp in plugins)
            if 'levels' in o: rule('levels inner', 'cpInner (key lists ci_inner)', plugins, 'cpInner' in plugins)
            else: rule('no inner CI', 'no cpInner', plugins, 'cpInner' not in plugins)
        if 'pexline' in o: rule('pexline', 'cpPexline', plugins, 'cpPexline' in plugins)
        html2 = open(os.path.join(OUT, n + '.html')).read()
        # t2h batch 2a: notable suppresses the whole table block, so it has no toolbar.
        if 'notable' not in o:
            rule('table toolbar has PDF (table-only print)', "_spkPrintOnly('table') + handler", 'yes' if ("_spkPrintOnly('table')" in html2 and 'function _spkPrintOnly' in html2) else 'no', "_spkPrintOnly('table')" in html2 and 'function _spkPrintOnly' in html2)
        if 'eform' in o: rule('eform axis', 'Odds ratio', ax, 'Odds ratio' in ax.values())
        rule('value axis title', 'Coefficient/Estimate/Odds ratio', ax, any(v in ('Coefficient', 'Estimate', 'Odds ratio') for v in ax.values()))
        if 'show' in o and not multi:
            want = o['show'].split(); got = [d['n'] for d in info.get('cpTip', [])]
            overrides = dict(re.findall(r'(\w+) "([^"]+)"', o.get('coeflbl', '')))
            overrides.update(dict(re.findall(r'"(\w+)/([^"]+)"', o.get('coeflabels', ''))))
            wantd = [overrides.get(w, VAR_LABELS.get(w, w)) for w in want]
            if any('*' in w or '?' in w for w in want):
                import fnmatch
                rule('show() (patterns)', want, got, len(got) > 0 and all(any(fnmatch.fnmatch(g, w) for w in want) for g in got))
            elif 'coefsort' in o: rule('show() (order set by sort)', sorted(wantd), sorted(got), sorted(got) == sorted(wantd))
            else: rule('show()', wantd, got, got == wantd)
        if 'omit' in o and not multi:
            got = [d['n'] for d in info.get('cpTip', [])]; rule('omit()', f"without {o['omit']}", got, o['omit'] not in got)
        if 'coeflabels' in o:
            want = re.findall(r'/([^"|]+)"', o['coeflabels']); rule('coeflabels', want, labels, all(w in labels for w in want))
        if 'coefsort' in o and not multi:
            b = [d['b'] for d in info.get('cpTip', []) if d['b'] is not None]
            if o['coefsort'] == 'desc': rule('coefsort desc', 'descending b', b, b == sorted(b, reverse=True))
            if o['coefsort'] == 'value': rule('coefsort value', 'ascending b', b, b == sorted(b))
        if 'nobase' in o: rule('nobase (chart)', 'no rep78=1 on the axis', labels[:5], 'rep78=1' not in labels)   # table still documents base rows by design
        if case['est'].split() and 'i.rep78' in case['est'] and 'nobase' not in o and 'show' not in o and t == 'coefplot' and 'post' not in case['margins'] and 'matrix' not in o:
            rows = info.get('tableRows', []); brow = next((r for r in rows if '(base)' in r), None)
            rule('base level shown', 'rep78=1 (base)', brow, brow is not None and brow.startswith('rep78=1'))
        if t == 'eventstudy' and False:
            rule('eventstudy connected', 'showLine true / band', info.get('showLine'), 'connected' in o and any(info.get('showLine', [])) or 'connected' not in o)

    # ---------------- marginsplot
    if t == 'marginsplot':
        m = case['margins']; cs = o.get('cistyle', 'whisker').replace('+', ' ').split()
        if 'results' in o: m = ''   # t2g: results() pages carry the file's own (unlabelled) names; label/series rules do not apply
        band = any(x in ('area', 'band') for x in cs); whisk = any(x in ('whisker', 'bar') for x in cs) or not band
        rule('outer CI band', band, any(p.startswith('mpA') and '_L2' not in p for p in plugins), band == any(p.startswith('mpA') and '_L2' not in p for p in plugins))
        rule('outer CI whisker', whisk, any(re.match(r'mpW\d', p) for p in plugins), whisk == any(re.match(r'mpW\d', p) for p in plugins))
        if 'results' not in o:   # t2g: results() pages carry the file's own names/shape; command-derived rules do not apply
            if 'levels' in o:
                rule('inner band', band, any(p.startswith('mpA_L2') for p in plugins), band == any(p.startswith('mpA_L2') for p in plugins))
                rule('inner whisker', whisk, any(p.startswith('mpW_L2') for p in plugins), whisk == any(p.startswith('mpW_L2') for p in plugins))
                rule('CI key', 'inner CI plugin (key lists ci_inner)', plugins, any('L2' in p for p in plugins))
                lev = o['levels'].split()[0]; rule('inner level in context', f'(inner {lev}%)', info.get('subtitle'), f'(inner {lev}%)' in (info.get('subtitle') or ''))
            else: rule('no inner CI', 'none', plugins, not any('_L2' in p for p in plugins))
            cont = 'connected' in o; numx = bool(re.search(r'at\(\w+=\((\d+\(\d+\)\d+|[\d ]+)\)\)', m)) and 'foreign=(0 1)' not in m
            rule('numeric x', numx, info.get('numericX'), bool(info.get('numericX')) == numx)
            if numx:
                want = re.search(r'at\(\w+=\(([\d ()]*?)\)\)', m).group(1)
                if '(' in want:
                    a, step, b = re.match(r'(\d+)\((\d+)\)(\d+)', want).groups(); vals = list(range(int(a), int(b) + 1, int(step)))
                else: vals = [int(x) for x in want.split()]
                rule('at() values on x', vals[:4], labels[:4], all(str(v) in labels for v in vals))
                var = re.search(r'at\((\w+)=', m).group(1); rule('x-axis title from at()', VAR_LABELS.get(var, var), ax.get('x'), ax.get('x') == VAR_LABELS.get(var, var))
                rule('line drawn (numeric x)', True, info.get('showLine'), all(info.get('showLine', [True])))
            else:
                rule('points connected', cont, any(info.get('showLine', [])), cont == any(info.get('showLine', [])))
                if 'foreign=(0 1)' in m: rule('factor at() labels', VALUE_LABELS['foreign'], labels, all(v in labels for v in VALUE_LABELS['foreign']))
            over = o.get('over')
            if over in VALUE_LABELS: rule('over() series', VALUE_LABELS[over], dsl, dsl == VALUE_LABELS[over])
            elif (not numx or over is None) and '#' not in m: rule('single series', 1, nds, nds == 1 if not over else True)
            eff = bool(re.search(r'dydx|contrast|pwcompare|margins +[a-z]{1,2}\.', m))
            rule('null line', 'mpZ' if eff else 'no mpZ', plugins, ('mpZ' in plugins) == eff)
            if 'pexline' in o: rule('pexline', 'mpPex', plugins, 'mpPex' in plugins)
            yt = ax.get('y', '')
            if 'dydx' in m: rule('y title dy/dx', 'dy/dx (var) on ...', yt, yt.startswith('dy/dx ('))
            elif 'pwcompare' in m: rule('y title pairwise', 'Pairwise contrast of', yt, yt.startswith('Pairwise contrast'))
            elif 'contrast' in m or re.search(r'margins +[a-z]{1,2}\.', m): rule('y title contrast', 'Contrast of', yt, yt.startswith('Contrast of'))
            elif 'logit' in case['est']: rule('y title Pr()', 'Pr(foreign)', yt, yt == 'Pr(foreign)')
            elif 'ytitle' not in o and 'results' not in o: rule('y title prediction', 'Linear prediction', yt, yt == 'Linear prediction')
            if 'pwcompare' in m: rule('pairwise labels', '2 vs 1 ... 5 vs 4', labels[:3], '2 vs 1' in labels and '5 vs 4' in labels)
            elif re.match(r'(quietly )?margins rep78\b', m) and 'over' not in o and not eff: rule('rep78 labels', '1..5 (fix8w: bare levels, x title = variable label)', labels[:5], all(str(k) in labels for k in range(1, 6)))
            if re.search(r'margins (r\.foreign@)?rep78(#foreign)?\b', m) and 'xtitle' not in o and not numx:
                rule('x-axis title (factor)', VAR_LABELS['rep78'], ax.get('x'), ax.get('x') == VAR_LABELS['rep78'])
            if 'foreign=(0 1)' in m and 'xtitle' not in o:
                rule('x-axis title (factor at)', VAR_LABELS['foreign'], ax.get('x'), ax.get('x') == VAR_LABELS['foreign'])
            # tooltip title of a coefplot must be the axis label, not the raw name (s8n)
            if t in ('coefplot',) and info.get('cpTip') and labels:
                rule('tooltip title = axis label', labels[:3], [d['n'] for d in info['cpTip']][:3], [d['n'] for d in info['cpTip']] == labels)
    return R

# ----------------------------------------------------------------- report
total = 0; bad = 0; lines_out = []
skipped = 0
for c in cases:
    R = check(c)
    if R is None: skipped += 1; continue
    fails = [r for r in R if not r[3]]
    total += 1; bad += 1 if fails else 0
    lines_out.append(f"{'FAIL' if fails else 'OK  '} {c['name']:36s} {len(R)-len(fails)}/{len(R)} rules")
    for r in fails: lines_out.append(f"        x {r[0]}: expected {r[1]!r}, observed {r[2]!r}")
print('\n'.join(lines_out))
print(f"\nintent_check: {total - bad}/{total} cases aligned with their command" + (f" ({skipped} not run this session)" if skipped else ""))
json.dump({c['name']: (check(c) or []) for c in cases}, open(os.path.join(OUT, '_intent_report.json'), 'w'), indent=1)
sys.exit(1 if bad else 0)
