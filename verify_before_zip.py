#!/usr/bin/env python3
"""
verify_before_zip.py -- Sparkta pre-packaging verification (v1.0)
Run this BEFORE every zip to catch known failure modes.
Exit 0 = all clear, Exit 1 = problems found.

Checks:
  1. ASCII-only in all source files
  2. Brace balance in Java files
  3. program/end balance in ado
  4. args.length matches javacall arg count
  5. No *! lines inside program body in ado
  6. Tilde-safety: any gettoken parse() on pipe-sep post-est data
     must also parse "~" for multi-model support
  7. New pipe-sep field sync: every pipe-sep array that exists for
     coefs/lower/upper/ses/pvals must also exist for tzvals
  8. DashboardOptions fields match DashboardBuilder wiring count
  9. check_build.py passes (if present)
"""
import re, os, sys, glob

errors = []
warnings = []

BASE = os.path.dirname(os.path.abspath(__file__))
ADO_PATH = os.path.join(BASE, "ado", "sparkta.ado")
# v3.6.0-t2f: the package is sparkta.ado + sparkta_*.ado components (one public program each)
ADO_FILES = [ADO_PATH] + sorted(glob.glob(os.path.join(BASE, "ado", "sparkta_*.ado")))
JAVA_BASE = os.path.join(BASE, "java", "src", "main", "java", "com", "dashboard_test")

def check_ascii(path):
    """Check file is pure ASCII."""
    raw = open(path, 'rb').read()
    bad = [(i+1, hex(b)) for i, b in enumerate(raw) if b > 127]
    if bad:
        errors.append(f"Non-ASCII in {os.path.basename(path)}: {len(bad)} bytes (first: byte {bad[0][0]}={bad[0][1]})")

def check_java_braces(path):
    """Check brace balance in Java, skipping strings and comments."""
    src = open(path).read()
    o = c = 0
    i = 0
    n = len(src)
    while i < n:
        if src[i:i+2] == '/*':
            i += 2
            while i < n and src[i:i+2] != '*/': i += 1
            i += 2; continue
        if src[i:i+2] == '//':
            while i < n and src[i] != '\n': i += 1
            continue
        if src[i] == '"':
            i += 1
            while i < n:
                if src[i] == '\\': i += 2; continue
                if src[i] == '"': i += 1; break
                i += 1
            continue
        if src[i] == "'":
            i += 1
            while i < n:
                if src[i] == '\\': i += 2; continue
                if src[i] == "'": i += 1; break
                i += 1
            continue
        if src[i] == '{': o += 1
        elif src[i] == '}': c += 1
        i += 1
    if o != c:
        errors.append(f"{os.path.basename(path)}: brace mismatch (open={o}, close={c})")

# =========================================================================
# 1. ASCII check on all source files
# =========================================================================
print("1. ASCII check...")
for _af in ADO_FILES: check_ascii(_af)
for jf in glob.glob(os.path.join(JAVA_BASE, "**", "*.java"), recursive=True):
    check_ascii(jf)
# t2i hardening: also scan the verification code itself (harness/js/py) -- a
# non-ASCII byte here slipped past the old scan and corrupted a LaTeX test.
for vf in (glob.glob(os.path.join(BASE, "verify", "**", "*.java"), recursive=True)
           + glob.glob(os.path.join(BASE, "verify", "*.js"))
           + glob.glob(os.path.join(BASE, "verify", "*.py"))):
    check_ascii(vf)

# =========================================================================
# 2. Java brace balance
# =========================================================================
print("2. Java brace balance...")
for jf in glob.glob(os.path.join(JAVA_BASE, "**", "*.java"), recursive=True):
    check_java_braces(jf)

# =========================================================================
# 3. Ado program/end balance
# =========================================================================
print("3. Ado program/end balance...")
ado = "\n".join(open(_af).read() for _af in ADO_FILES)   # t2f: every component, for the marker checks
progs = re.findall(r'^program\s+(?:define\s+)?(\w+)', ado, re.MULTILINE)
ends = re.findall(r'^end\s*$', ado, re.MULTILINE)
if len(progs) != len(ends):
    errors.append(f"Ado program/end mismatch: {len(progs)} programs vs {len(ends)} ends")

# =========================================================================
# 4. Args.length matches javacall arg count
# =========================================================================
print("4. Args.length vs javacall count...")
db_path = os.path.join(JAVA_BASE, "DashboardBuilder.java")
db = open(db_path).read()
m = re.search(r'args\.length\s*<\s*(\d+)', db)
if m:
    expected_args = int(m.group(1))
else:
    errors.append("Cannot find args.length check in DashboardBuilder.java")
    expected_args = None

# Count javacall args in ado: find the closing '"')  // NNN line
jc_match = re.search(r"//\s*(\d+)\s+.*$(?=\n\s*if\s+_rc\s*==)", ado, re.MULTILINE)
if jc_match:
    last_arg_idx = int(jc_match.group(1))
    javacall_count = last_arg_idx + 1  # 0-indexed
    if expected_args and javacall_count != expected_args:
        errors.append(f"args.length={expected_args} but last javacall arg index={last_arg_idx} (need {last_arg_idx+1})")
    else:
        print(f"   args.length={expected_args}, last arg index={last_arg_idx} -- OK")

# =========================================================================
# 4b. Help file structure (2026-09-13): smcl_check.py models the SMCL rules that made
#     `help sparkta` print every {synopt:} raw (a block directive inside an open paragraph)
#     and the stray-brace report (a directive wrapped over a line break).
print("4b. sparkta.sthlp SMCL structure (verify/smcl_check.py)...")
import subprocess as _sp
_rc = _sp.call([sys.executable, os.path.join(BASE, "verify", "smcl_check.py"), os.path.join(BASE, "ado", "sparkta.sthlp")])
if _rc: errors.append("  sparkta.sthlp failed verify/smcl_check.py (see lines above)")
_rc2 = _sp.call([sys.executable, os.path.join(BASE, "verify", "smcl_render.py"), os.path.join(BASE, "ado", "sparkta.sthlp"), "--audit"])
if _rc2: errors.append("  sparkta.sthlp failed the verify/smcl_render.py layout audit (see lines above)")
else: print("  [OK] sparkta.sthlp structure valid")

# 5. No *! inside program body
# =========================================================================
print("5. No *! inside program body...")
in_program = False
for i, line in enumerate(ado.splitlines(), 1):
    stripped = line.strip()
    if re.match(r'^program\s', stripped):
        in_program = True
    elif stripped == 'end' and in_program:
        in_program = False
    elif in_program and stripped.startswith('*!'):
        errors.append(f"Ado line {i}: *! inside program body: {stripped[:60]}")

# =========================================================================
# 6. Tilde-safety: gettoken on post-est pipe-sep data must parse "|~"
# =========================================================================
print("6. Tilde-safety in eform/rescale...")
# Find all gettoken calls within the eform/rescale blocks
# They should use parse("|~") not just parse("|")
eform_block = re.search(r'if\s+"`eform\'"\s*!=\s*""\s*\{(.*?)local\s+_pe_coefs\s+"`_ef_rc\'"',
                         ado, re.DOTALL)
if eform_block:
    block = eform_block.group(1)
    pipe_only = re.findall(r'parse\("\|"\)', block)
    pipe_tilde = re.findall(r'parse\("\|~"\)', block)
    if pipe_only:
        errors.append(f"Eform block has {len(pipe_only)} gettoken with parse(\"|\") -- must be parse(\"|~\") for multi-model")
    if not pipe_tilde:
        warnings.append("Eform block: no parse(\"|~\") found -- may not handle multi-model")

rescale_block = re.search(r'if\s+`rescale\'\s*!=\s*1\s*\{(.*?)local\s+_pe_coefs\s+"`_rs_rc\'"',
                           ado, re.DOTALL)
if rescale_block:
    block = rescale_block.group(1)
    pipe_only = re.findall(r'parse\("\|"\)', block)
    pipe_tilde = re.findall(r'parse\("\|~"\)', block)
    if pipe_only:
        errors.append(f"Rescale block has {len(pipe_only)} gettoken with parse(\"|\") -- must be parse(\"|~\") for multi-model")
    if not pipe_tilde:
        warnings.append("Rescale block: no parse(\"|~\") found -- may not handle multi-model")

# =========================================================================
# 7. Pipe-sep field sync: tzvals must appear alongside pvals everywhere
# =========================================================================
print("7. Pipe-sep field sync (tzvals parallel to pvals)...")
# Check that _pe_tzvals appears in: init, c_local return, multi-model accum, javacall
for pattern, desc in [
    (r'local\s+_pe_tzvals\s+""', "init block"),
    (r'c_local\s+_pe_tzvals', "c_local return"),
    (r'_mm_tzvals', "multi-model accumulation"),
    (r'_pe_tzvals.*//.*184', "javacall arg 184"),
]:
    if not re.search(pattern, ado):
        errors.append(f"_pe_tzvals missing from {desc}")

# Check coefsort includes tzvals
if '_st_' not in ado and 'coefsort' in ado:
    warnings.append("coefsort block may not reorder _pe_tzvals (no _st_ prefix found)")

# Check order() includes tzvals
if '_ot_' not in ado and "order'" in ado:
    warnings.append("order() block may not reorder _pe_tzvals (no _ot_ prefix found)")

# =========================================================================
# 8. check_build.py
# =========================================================================
print("8. check_build.py...")
cb_path = os.path.join(BASE, "java", "check_build.py")
if os.path.exists(cb_path):
    rc = os.system(f"cd {os.path.join(BASE, 'java')} && python3 check_build.py > /dev/null 2>&1")
    if rc != 0:
        errors.append("check_build.py failed")
else:
    warnings.append("check_build.py not found")

# =========================================================================
# 9. Key features present
# =========================================================================
print("9. Key features present...")
cr_path = os.path.join(JAVA_BASE, "html", "ChartRenderer.java")
hg_path = os.path.join(JAVA_BASE, "html", "HtmlGenerator.java")
cr = open(cr_path).read()
hg = open(hg_path).read()

for feature, file_content, desc in [
    ('coefPlotMulti', cr, 'multi-model coefplot in ChartRenderer'),
    ('isMultiModel', hg, 'multi-model table in HtmlGenerator'),
    ('fmtTz', hg, 'pre-computed t/z formatter in HtmlGenerator'),
    ('peTzvals', hg, 'peTzvals field usage in HtmlGenerator'),
    ('estimates restore', ado, 'estimates restore in ado'),
    ('_mm_tzvals', ado, 'multi-model tzvals accumulation in ado'),
    # v3.6.0-s8d: every s8b-s8d fix has a marker here so a lost edit fails the build
    ('confirm matrix r(table_vs)', ado, 'pwcompare r(table_vs) read (s8d)'),
    ('vs([0-9]+)', ado, 'pwcompare 2vs1 label regex (s8c)'),
    ('_rat_is_factor', ado, 'factor at() categorical handling (s8c)'),
    ('_rat_varying', ado, 'two-at()-vars guard (s8c)'),
    ('if "`_pe_lower2\'" != "" {', ado, 'no empty levels2 groups in multi-model accumulation (s8d)'),
    ('foreach _l2 in _pe_lower2 _pe_upper2', ado, 'eform exponentiates inner CI (s8c)'),
    ('PALette(string)', ado, 'palette() option (s8c)'),
    ('numOrNull(', cr, 'numOrNull helper (s8b)'),
    ('_mpTip', cr, 'marginsplot rich tooltip (s8b)'),
    ('mpW_L2_', cr, 'per-series inner CI (s8b)'),
    ('hasLevels2M = !lo2rawM.replace', cr, 'multi-model levels2 numeric check (s8d)'),
    ('barMode', cr, 'marginsplot bar mode (s8d)'),
    ('valTitleJs', cr, 'coefplot axis titles (s8c)'),
    ('r(predict1_label)', ado, 'marginsplot y-label from predict label (s8f)'),
    ('informative default title', ado, 'default title from context (s8f)'),
    ('vs([0-9]+)[bon]*', ado, 'pwcompare base marker (s8f)'),
    ('filter:function(it)', cr, 'legend CI-helper filter (s8f)'),
    ('String contextLine(DataSet data)', hg, 'context subtitle (s8i)'),
    ("role='img'", hg, 'accessible canvas (s8i)'),
    ('outerIsBand', cr, 'CI key matches encoding (s8k)'),
    ('fillRect(xPx-hw', cr, 'area CI rectangles when unconnected (s8k)'),
    ('includeBounds:false', cr, 'no padded bounds as ticks (s8k)'),
    ("mpA_L2_", cr, 'marginsplot nested inner BAND (s8l)'),
    ('ciTok.contains', cr, 'cistyle list parsing (s8m)'),
    ('cistyle(area whisker)', ado, 'cistyle list validation (s8m)'),
    ('local _bname = regexs(1) + "." + regexs(2)', ado, 'base row label keeps the dot (s8o, regexm/regexs)'),
    ('sparkta_mg_varlabel `_mp_xvar\'', ado, 'marginsplot x-title from variable label (s8n; via the results()-aware helper since fix8z)'),
    ('gen.resolveCoefLabel(names[i].trim())', cr, 'coefplot tooltip = axis label (s8n)'),
    ('x:{offset:true,grid', cr, 'marginsplot category x end padding (s8o)'),
    ('plotMarginFrac()', cr, 'plotmargin() applied (s8p)'),
    ('PLOTMargin(string)', ado, 'plotmargin option (s8p)'),
    ('negative margin -- data beyond the axis limits will be cropped', ado, 'negative plotmargin (s8q)'),
    ('" plotmargin=" + o.chart.plotMargin', hg, 'plotmargin recorded in HTML header (s8q)'),
    ('static double[] niceRange(', cr, 'data-anchored ticks (s8r)'),
    ('resolve Stata-convention aliases', ado, 'Stata aliases (s8s)'),
    ('(parseDouble(pSize, 4) + 2)', cr, 'decimal pointsize (s8v)'),
    ("cpBarArea", cr, 'bar coefplot CI rectangles (s8x)'),
    ('static String fmtJs()', cr, 'shared formatter (s8y)'),
    ("spk-chart-box", hg, 'canvas sizing box (s8z)'),
    ('c.rect(ch.chartArea.left,ch.chartArea.top', cr, 'plugins clipped to plot area (s8z)'),
    ('o.chart.horizontal ? valTitle : catTitle', cr, 'hbar axis titles (s9a)'),
    ('static Double minPositive(Variable v)', cr, 'log axis anchored (s9c)'),
    ("grace:'12%',", cr, 'datalabel headroom (s9d)'),
    ('double dec = (logDataMin != null && logDataMin > 0)', cr, 'log axis min at the source (s9e)'),
    ('span<=1.6&&(m===2||m===5)', cr, 'log sub-decade tick labels (s9f)'),
    ('private String buildSharedKey(DataSet data)', hg, 'by() shared key (s9g)'),
    ('pass 1: build every panel with a free axis', hg, 'by() shared axis two-pass (s9g)'),
    ('repeat(auto-fit,minmax(380px,1fr))', hg, 'by() wrapping grid (s9g)'),
    ('[YFRee]', ado, 'yfree option (s9g)'),
    ('dsb.boxMarkerColor(nGroups, false)', hg, 'key uses the drawing resolver (s9h)'),
    ('private String keyGlyph(String kind', hg, 'styled key glyphs (s9i)'),
    (',usePointStyle:true', cr, 'legend glyphs carry marker + dash (s9i)'),
    ('String buildElementsKey()', hg, 'elements key (s9j)'),
    ('static final boolean CANVAS_CI_KEY = false', cr, 'canvas CI keys retired (s9j)'),
    ('function _spkDrawKey(ctx,items,x,y,dpr,textColor)', hg, 'key composed into PNG export (s9k)'),
    ('function _spkChartToSvg(chartInst,keyItems,noHead)', hg, 'vector SVG export (s9l; fix9k noHead)'),
    ('loadResource("canvas2svg-1.0.19.js")', hg, 'canvas2svg inlined with download (s9l)'),
    ("window.addEventListener('beforeprint'", hg, 'print swaps canvases for SVG (s9m)'),
    ('@media print{body{background:#fff !important', hg, 'print stylesheet (s9m)'),
    ("_spkPrintOnly('table')", hg, 'table-only PDF button (s9o)'),
    ('static String printJs()', hg, 'print handlers on every page (s9o)'),
    ('program sparkta_find_browser', ado, 'saveas browser detection (s9p)'),
    ("q.export==='svg'", hg, 'page ?export=svg mode (s9p)'),
    ('function _spkSwapForPrint()', hg, 'print swap callable on load (s9v)'),
    ('if `"`saveas\'"\' != "" local is_download "1"', ado, 'saveas loads the exporter (s9w)'),
    ('var tTitle=h1?(h1.textContent', hg, 'svg carries title + context (s9x)'),
    ('var M=18;', hg, 'export figure margins (t1j)'),
    ('function _spkPageToSvg()', hg, 'by() pages export as one figure (t1l)'),
    ('o2.elements.line.segment={}', hg, 'export forces direct line drawing / Path2D (t1m)'),
    ('program sparkta_read_matrix', ado, 'matrix mode reader (t2a)'),
    ('program sparkta_event_times', ado, 'event-time parser (t2d)'),
    ('String eventStudy(String id)', cr, 'numeric event-study renderer (t2d)'),
    ('207 eventstudy relative times', ado, 'arg 207 passed (t2d)'),
    ('program sparkta_color_norm', ado, 'colour normaliser (t2e)'),
    ('program sparkta_color_list', ado, 'colour list wrapper (t2e)'),
    ('sparkta_color_list `"``_co\'\'"\' space', ado, 'colors() normalised before Java (t2e)'),
    ('ribbon(arr,3,4,0.45)', cr, 'eventstudy band: inner-level ribbon per phase (t2e)'),
    ('byModelCol', cr, 'eventstudy cicolors(pre|post) (t2e)'),
    ('userAlpha', hg, 'colors() keeps user alpha (t2e)'),
    ('program sparkta_read_results', ado, 'results() dataset/frame reader (t2f fix 4)'),
    ('program sparkta_color_opts', ado, 'colour options normalised in a component (t2f fix 4)'),
    ('zeroRow', cr, 'explicit zero row = reference period (t2f fix 3)'),
    ('program sparkta_margins_keep', ado, 'margins snapshot before r-class components (t2g)'),
    ('program sparkta_margins_repost', ado, 'margins re-post for the reader (t2g)'),
    ('program sparkta_read_results_margins', ado, 'results() for marginsplot (t2g)'),
    ('program sparkta_matrix_guards', ado, 'matrix guards moved out of main (t2g)'),
    ('ciCol.apply(si)', cr, 'marginsplot honours cicolors() (t2g fix 3)'),
    ('program sparkta_results_mp_name', ado, 'results(marginsplot) name discovery (t2g fix 3)'),
    ('[MATrix(string)]', ado, 'matrix() option (t2a)'),
    ('--force-device-scale-factor=`_sa_scale', ado, 'png scale (s9x)'),
    ('com.dashboard_test.export.SvgConvert execute', ado, 'fast export path (s9y)'),
    ('repo1.maven.org/maven2/com/github/weisj/jsvg', open(os.path.join(BASE,'java','fetch_js_libs.bat')).read(), 'fetch_js_libs fetches Java libs (s9y)'),
    ('Step 5b (v3.6.0-s9y)', open(os.path.join(BASE,'java','build.bat')).read(), 'build.bat builds sparkta-export.jar (s9y)'),
    ('classpath("`jarpath\'") args(`"`_dumpf', ado, 'SvgExtract classpath (s9v)'),
    ('origIdx.getOrDefault(var, ci)', open(os.path.join(BASE,'java','src','main','java','com','dashboard_test','html','DatasetBuilder.java')).read(), 'fill colour identity (s9c)'),
    ('_updateSpk(gi, vi, s, dmn, dmx, vals)', open(os.path.join(BASE,'java','src','main','java','com','dashboard_test','html','FilterRenderer.java')).read(), 'filter stats refresh rebuilds dots (s9a)'),
    ('w._spkTipN = tipN', open(os.path.join(BASE, 'java', 'src', 'main', 'resources', 'com', 'dashboard_test', 'js', 'sparkta_engine.js')).read(), 'engine tooltip counts (s8y)'),
]:
    if feature not in file_content:
        errors.append(f"Missing: {desc}")

# =========================================================================
# 9b. sparkta_version local matches *! header (display version consistency)
# =========================================================================
print("9b. sparkta_version local vs *! header...")
import re as _re
# 9a-guard (fix8k): the *! CHANGELOG HEADER must stay SHORT. An oversized leading
# comment block was the CONFIRMED cause of the fix8f-fix8j "command sparkta not defined
# by sparkta.ado": fix8e loaded at 966 *! lines, every larger build failed to define the
# program (Stata reads the file -- `which` prints it -- but cannot define it). The full
# history lives in docs/CHANGELOG_sparkta_full.md; sparkta.ado keeps only a short header.
# Static, no-Stata guard: cap the *! count and the total file length well under fix8e.
_main_ado = open(ADO_PATH).read()
_bang = sum(1 for _l in _main_ado.splitlines() if _l.startswith("*!"))
_total = _main_ado.count("\n") + 1
if _bang > 300:
    errors.append(f"  sparkta.ado has {_bang} *! header lines (max 300) -- move history to docs/CHANGELOG_sparkta_full.md (oversized header broke loading in fix8f-fix8j)")
elif _bang > 150:
    warnings.append(f"  sparkta.ado *! header is {_bang} lines -- trim toward docs/CHANGELOG_sparkta_full.md before it approaches the fix8f load-failure zone")
if _total > 4200:
    errors.append(f"  sparkta.ado is {_total} lines (max 4200) -- fix8e loaded at 4679 but fix8f+ failed; keep the file well under that")
print(f"  [OK] sparkta.ado header {_bang} *! lines, {_total} total lines (load-safe)")
m_hdr = _re.search(r"\*! sparkta version (\S+)", ado)
m_loc = _re.search(r'local sparkta_version \"([^\"]+)\"', ado)
if m_hdr and m_loc:
    v_hdr = m_hdr.group(1)
    v_loc = m_loc.group(1)
    if v_hdr != v_loc:
        errors.append(f"  VERSION LOCAL MISMATCH: *! header={v_hdr} vs local sparkta_version={v_loc}")
    else:
        print(f"  [OK] sparkta_version local matches header: {v_loc}")
    # t2f: every component file must carry the same *! sparkta version line (a stale copy fails here, not in Stata)
    for _af in ADO_FILES:
        _mh = _re.search(r"\*! sparkta version (\S+)", open(_af).read())
        if not _mh: errors.append(f"  {os.path.basename(_af)}: no *! sparkta version line")
        elif _mh.group(1) != v_hdr: errors.append(f"  {os.path.basename(_af)}: version {_mh.group(1)} != {v_hdr}")
    print(f"  [OK] {len(ADO_FILES)} ado files carry version {v_hdr}")
    # t2f fix 4: every ado file must be listed in sparkta.pkg (the earlier pkg edit silently missed -- LF file)
    _pkg = open(os.path.join(BASE, "ado", "sparkta.pkg")).read()
    for _af in ADO_FILES:
        if ("f " + os.path.basename(_af)) not in _pkg: errors.append(f"  sparkta.pkg does not list {os.path.basename(_af)}")
    print("  [OK] sparkta.pkg lists every ado file")
else:
    if not m_hdr: errors.append("  Could not find *! sparkta version line in ado")
    if not m_loc: errors.append("  Could not find local sparkta_version in ado")

# =========================================================================
# 10. Stata ado parenthesis balance check
# Catches extra ) that cause "option ) not allowed" at runtime.
# Approach: track running depth across continuation lines (///).
# A line group ending with net depth < 0 has unmatched closing brackets.
# Single-line check: if a line has no opening ( but has ) and contains
# keywords like local/gettoken/if/regexr that expect balanced parens,
# it's suspicious. We check for lines where depth < -1 (extra extra )).
# =========================================================================
print("10. Stata ado parenthesis balance check...")
ado_lines = ado.splitlines()
bad_paren_lines = []
for i, line in enumerate(ado_lines, 1):
    stripped = line.strip()
    if not stripped or stripped.startswith('*') or stripped.startswith('//'):
        continue
    # Count ( and ) outside string literals on this line
    in_str = False
    depth = 0
    open_count = 0
    close_count = 0
    for ch in stripped:
        if ch == '"':
            in_str = not in_str
        if not in_str:
            if ch == '(':
                depth += 1
                open_count += 1
            elif ch == ')':
                depth -= 1
                close_count += 1
    # Flag lines that:
    # - Are executable statements (start with local/gettoken/if/while/capture/display)
    # - Have depth < -1 (at least 2 more ) than ()
    # This avoids false positives on javacall closing lines which have exactly depth=-1
    is_executable = any(stripped.startswith(kw) for kw in
        ['local ', 'gettoken ', 'if ', 'while ', 'capture ', 'display ',
         'foreach ', 'forvalues ', 'assert ', 'scalar '])
    if depth < -1 and is_executable:
        bad_paren_lines.append((i, depth, stripped[:80]))
    # Also catch depth == -1 on lines that have both ( and ) -- these shouldn't be negative
    elif depth == -1 and open_count > 0 and is_executable:
        bad_paren_lines.append((i, depth, stripped[:80]))

if bad_paren_lines:
    for ln, d, s in bad_paren_lines[:10]:
        errors.append(f"Ado L{ln}: unmatched ')' (depth={d}): {s}")
else:
    print("  [OK] Ado parenthesis balance: clean")

# =========================================================================
# 11. JS plugin brace balance -- catch unclosed { in plugin JS strings
# =========================================================================
print("11. JS plugin brace balance in ChartRenderer...")
import re as _re11

_plugin_issues = []
_cur_plugin = None
_cur_opens = 0
_cur_closes = 0

def _count_js_braces(line):
    o = c = 0; in_str = False
    for i, ch in enumerate(line):
        if ch == '"' and (i == 0 or line[i-1] != "\\"):
            in_str = not in_str
        elif in_str:
            if ch == "{": o += 1
            elif ch == "}": c += 1
    return o, c

for _ln in cr.splitlines():
    _m = _re11.search(r"id:\\'(cp[A-Za-z]+)\\'", _ln)
    if _m:
        if _cur_plugin and _cur_opens != _cur_closes:
            _plugin_issues.append(f"Plugin {_cur_plugin}: JS opens={_cur_opens} closes={_cur_closes}")
        _cur_plugin = _m.group(1); _cur_opens = 0; _cur_closes = 0
    if _cur_plugin:
        _o, _c = _count_js_braces(_ln); _cur_opens += _o; _cur_closes += _c
        if (_ln.strip().endswith('"};') or _ln.strip().endswith('";"')) and (
                'levels2Suffix' in _ln or 'innerPlugin' in _ln or 'legendPlugin' in _ln):
            if _cur_opens != _cur_closes:
                _plugin_issues.append(f"Plugin {_cur_plugin}: opens={_cur_opens} closes={_cur_closes}")
            _cur_plugin = None; _cur_opens = 0; _cur_closes = 0

if _plugin_issues:
    for _iss in _plugin_issues:
        errors.append(f"  JS brace imbalance: {_iss}")
    print(f"  [FAIL] {len(_plugin_issues)} JS plugin brace issue(s)")
else:
    print("  [OK] JS plugin brace balance clean")


# =========================================================================
# Check 12: No single-backslash \| in Java split() calls (illegal escape).
# (v3.6.0-s8b: this check previously sat AFTER sys.exit() and never ran.)
# =========================================================================
print("12. Java split() pipe escaping...")
import re as _re, subprocess as _sp, shutil as _sh, glob as _glob
_cr_src = open(os.path.join(JAVA_BASE, "html", "ChartRenderer.java")).read()
_bad_splits = _re.findall(r'\.split\("\\\|"', _cr_src)   # literal: split("\|")
if _bad_splits:
    errors.append(f"Illegal escape in Java split(): {len(_bad_splits)} single-backslash pipe(s). Use split(\"\\\\|\").")
else:
    print("  [OK] no single-backslash split(\"\\|\")")

# =========================================================================
# Check 13: Stata static lint (verify/ado_lint.py)
# =========================================================================
print("13. Stata ado lint (verify/ado_lint.py)...")
_lint = os.path.join(BASE, "verify", "ado_lint.py")
if os.path.exists(_lint):
    for _af in ADO_FILES:
        _r = _sp.run([sys.executable, _lint, _af, os.path.join(JAVA_BASE, "html", "HtmlGenerator.java"),
                      os.path.join(JAVA_BASE, "DashboardBuilder.java")], capture_output=True, text=True)
        for _l in _r.stdout.splitlines():
            if _l.strip().startswith(("ERROR", "WARN", "ado_lint")): print("  " + os.path.basename(_af) + ": " + _l.strip())
        if _r.returncode != 0: errors.append(f"ado_lint reported errors in {os.path.basename(_af)} (see above)")
else:
    warnings.append("verify/ado_lint.py not found -- skipped")

# =========================================================================
# Check 14: Java compiles (javac against verify/sfi-stub). Catches scope /
# symbol / type errors BEFORE the user runs build.bat.
# =========================================================================
print("14. Java compile check (javac + SFI stub)...")
_javac = _sh.which("javac")
_stub_src = os.path.join(BASE, "verify", "sfi-stub")
if _javac and os.path.isdir(_stub_src):
    _tmp = os.path.join(BASE, "verify", "_build")
    _sh.rmtree(_tmp, ignore_errors=True); os.makedirs(_tmp)
    _stub_files = _glob.glob(os.path.join(_stub_src, "com", "stata", "sfi", "*.java"))
    _src_files = _glob.glob(os.path.join(JAVA_BASE, "**", "*.java"), recursive=True)
    _r = _sp.run([_javac, "-Xlint:-this-escape", "-d", _tmp] + _stub_files + _src_files, capture_output=True, text=True)
    _errs = [l for l in _r.stderr.splitlines() if ": error:" in l]
    if _r.returncode != 0 or _errs:
        for l in _r.stderr.splitlines()[:30]: print("  " + l)
        errors.append(f"javac failed ({len(_errs)} error line(s))")
    else:
        print(f"  [OK] {len(_src_files)} Java files compile")
        # ---- Check 15: headless harness + JS validation -------------------
        print("15. Headless render harness + js_check.js...")
        _harn = os.path.join(BASE, "verify", "harness", "Harness.java")
        _node = _sh.which("node")
        if os.path.exists(_harn) and _node:
            _r2 = _sp.run([_javac, "-cp", _tmp, "-d", _tmp, _harn], capture_output=True, text=True)
            if _r2.returncode != 0:
                errors.append("Harness.java failed to compile"); print(_r2.stderr[:800])
            else:
                _out = os.path.join(BASE, "verify", "_harness_out")
                _sh.rmtree(_out, ignore_errors=True)
                _res = os.path.join(BASE, "java", "src", "main", "resources")   # embedded JS libs for offline scenarios
                _r3 = _sp.run(["java", "-cp", os.pathsep.join([_tmp, _res]), "Harness", _out], capture_output=True, text=True)
                print("  " + (_r3.stdout.strip().splitlines() or ["(no output)"])[-1])
                if _r3.returncode != 0: errors.append("harness scenarios failed to render"); print(_r3.stdout[-1500:])
                # ---- Check 15d (t2i hardening): LOCALE NEUTRALITY. Re-render every
                #      scenario under de_DE (comma decimal, dot grouping) and diff
                #      against the default render, ignoring only the timestamp line.
                #      Any difference = a machine number leaked the JVM locale into the
                #      generated JS/JSON (Astra: 41/44 js pages broke under de_DE). The
                #      product fix is Locale.US around DashboardBuilder.execute().
                _out_de = os.path.join(BASE, "verify", "_harness_out_de")
                _sh.rmtree(_out_de, ignore_errors=True)
                _r3de = _sp.run(["java", "-Duser.language=de", "-Duser.country=DE",
                                 "-cp", os.pathsep.join([_tmp, _res]), "Harness", _out_de],
                                capture_output=True, text=True)
                if _r3de.returncode != 0:
                    errors.append("locale check: harness failed to render under de_DE")
                else:
                    def _strip_ts(p):
                        return [l for l in open(p, encoding="utf-8").read().splitlines() if "Made with sparkta" not in l]
                    _locale_bad = []
                    for _f in sorted(_glob.glob(os.path.join(_out, "*.html"))):
                        _b = os.path.basename(_f)
                        _g = os.path.join(_out_de, _b)
                        if os.path.exists(_g) and _strip_ts(_f) != _strip_ts(_g):
                            _locale_bad.append(_b)
                    print("  locale: %d files, %d locale-neutral, %d leaked"
                          % (len(_glob.glob(os.path.join(_out, "*.html"))),
                             len(_glob.glob(os.path.join(_out, "*.html"))) - len(_locale_bad), len(_locale_bad)))
                    if _locale_bad:
                        errors.append("locale check: %d page(s) differ under de_DE (locale leaked into numbers): %s"
                                      % (len(_locale_bad), ", ".join(_locale_bad[:6])))
                    _sh.rmtree(_out_de, ignore_errors=True)
                # ---- Check 15e (t2i hardening): SCRIPT-TAG BALANCE. A hostile label
                #      containing </script> that is not escaped breaks out of the inline
                #      script; the parser then sees more </script> than <script. Every
                #      page must balance. (H-T38 renders hostile titles/coef names.)
                _esc_bad = []
                for _f in sorted(_glob.glob(os.path.join(_out, "*.html"))):
                    _c = open(_f, encoding="utf-8").read()
                    if _c.count("<script") != _c.count("</script>"):
                        _esc_bad.append(os.path.basename(_f))
                print("  escaping: %d files, %d balanced, %d broken"
                      % (len(_glob.glob(os.path.join(_out, "*.html"))),
                         len(_glob.glob(os.path.join(_out, "*.html"))) - len(_esc_bad), len(_esc_bad)))
                if _esc_bad:
                    errors.append("escaping check: unescaped </script> broke out on: " + ", ".join(_esc_bad[:6]))
                # ---- Check 15f (t2i hardening): FILTER-BUFFER PRECISION. The large-N
                #      filter buffer must be lossless Float64, not Float32 (Astra: f32
                #      collapsed 100000000/100000001 and drifted currency/weights).
                _de_src = open(os.path.join(JAVA_BASE, "html", "DataEmbedder.java")).read()
                _eng = os.path.join(BASE, "java", "src", "main", "resources", "com", "dashboard_test", "js", "sparkta_engine.js")
                _eng_src = open(_eng).read() if os.path.exists(_eng) else ""
                if "putFloat(" in _de_src or "Float32Array" in _de_src:
                    errors.append("precision: DataEmbedder still uses Float32 for the filter buffer")
                if "Float32Array" in _eng_src:
                    errors.append("precision: sparkta_engine.js still decodes Float32Array")
                _r9 = _sp.run([_node, os.path.join(BASE, "verify", "precision_check.js")], capture_output=True, text=True)
                _pc = [l for l in _r9.stdout.strip().splitlines() if l.startswith("precision_check")]
                print("  precision: " + (_pc[-1] if _pc else "(no output)"))
                if _r9.returncode != 0:
                    errors.append("precision_check: Float64 round-trip not lossless")
                    for l in _r9.stdout.splitlines():
                        if "FAIL" in l: print("  " + l)
                # ---- Check 15g (t2j): PERCENTILE PARITY WITH STATA. The percentile
                #      method must be Stata's default (summarize,detail), not altdef.
                #      Checks the algorithm against known Stata results AND the rendered
                #      overall boxplot's Q1/median/Q3 against the fixture's Stata values.
                _r10 = _sp.run([_node, os.path.join(BASE, "verify", "pctile_check.js"), _out], capture_output=True, text=True)
                _qc = [l for l in _r10.stdout.strip().splitlines() if l.startswith("pctile_check")]
                print("  pctile: " + (_qc[-1] if _qc else "(no output)"))
                if _r10.returncode != 0:
                    errors.append("pctile_check: percentiles do not match Stata's default summarize,detail")
                    for l in _r10.stdout.splitlines():
                        if "FAIL" in l: print("  " + l)
                # ---- Check 15h (t2j): F1 -- filtered over-group matching must use the
                #      _si category index (not raw _sd codes vs value labels). Proves the
                #      value-labeled over var + filter pages populate >= 2 groups where the
                #      old raw-_sd compare populated 0.
                _r11 = _sp.run([_node, os.path.join(BASE, "verify", "filtergroup_check.js"), _out], capture_output=True, text=True)
                _fg = [l for l in _r11.stdout.strip().splitlines() if l.startswith("filtergroup_check")]
                print("  filtergroup: " + (_fg[-1] if _fg else "(no output)"))
                if _r11.returncode != 0:
                    errors.append("filtergroup_check: filtered over-group matching is wrong (F1 -- value-labeled over var)")
                    for l in _r11.stdout.splitlines():
                        if "FAIL" in l: print("  " + l)
                # ---- Check 15b (s8z2): REAL PIXELS -- render every harness chart with Chart.js on
                #      node-canvas; coloured ink outside the plot area = a plugin leaked (the
                #      negative-plotmargin whisker through the axis, user-found). Needs npm canvas.
                _r6 = _sp.run([_node, os.path.join(BASE, "verify", "pixel_check.js"), _out, "--strict"], capture_output=True, text=True,
                              env=dict(os.environ, NODE_PATH=os.environ.get("NODE_PATH", "") + os.pathsep + os.path.join(BASE, "node_modules")))
                _pl = [l for l in _r6.stdout.strip().splitlines() if l.startswith(("pixel_check", "PX-")) or " files, " in l]
                print("  pixels: " + (_pl[-1] if _pl else "(no output)"))
                if _r6.returncode != 0:
                    errors.append("pixel_check: coloured ink outside the plot area (a plugin is not clipped)")
                    for l in _r6.stdout.splitlines():
                        if "FAIL" in l: print("  " + l)
                _r4 = _sp.run([_node, os.path.join(BASE, "verify", "js_check.js"), _out, os.path.join(BASE, "verify", "expectations_harness.json")], capture_output=True, text=True)
                print("  " + (_r4.stdout.strip().splitlines() or ["(no output)"])[-1])
                _r7 = _sp.run([_node, os.path.join(BASE, "verify", "svg_check.js"), _out], capture_output=True, text=True,
                              env=dict(os.environ, NODE_PATH=os.environ.get("NODE_PATH", "") + os.pathsep + os.path.join(BASE, "node_modules")))
                _sl = [l for l in _r7.stdout.strip().splitlines() if l.startswith("svg_check")]
                print("  svg: " + (_sl[-1] if _sl else "(no output)"))
                if _r7.returncode != 0: errors.append("svg_check: vector export failed on a download-enabled page")
                # ---- Check 15c (t2h batch 2a): PUBLICATION TABLE -- run each post-est
                #      page's own PubTable exporters and compile the booktabs LaTeX with
                #      pdflatex; structural checks on Markdown/CSV. Skips if pdflatex is
                #      absent (a user machine only needs Stata's JDK; texlive is review-side).
                _pdftex = _sh.which("pdflatex")
                if _pdftex:
                    _r8 = _sp.run([_node, os.path.join(BASE, "verify", "latex_check.js"), _out], capture_output=True, text=True)
                    _ll = [l for l in _r8.stdout.strip().splitlines() if l.startswith("latex_check")]
                    print("  latex: " + (_ll[-1] if _ll else "(no output)"))
                    if _r8.returncode != 0:
                        errors.append("latex_check: publication-table LaTeX failed to compile (or MD/CSV malformed)")
                        for l in _r8.stdout.splitlines():
                            if "FAIL" in l: print("  " + l)
                else:
                    warnings.append("pdflatex not available -- publication-table LaTeX compile check skipped")
                _r5 = _sp.run([_node, os.path.join(BASE, "verify", "viz_audit.js"), _out], capture_output=True, text=True)
                if _r5.returncode != 0 and 'SyntaxError' in (_r5.stderr or '') + (_r5.stdout or ''): errors.append('viz_audit.js does not load (syntax error) -- the advisory stage is silently absent')
                _tail = [l for l in _r5.stdout.strip().splitlines() if l.startswith(("HARNESS", "17 files", "16 files")) or "mean score" in l]
                if _tail: print("  viz: " + _tail[-1])
                if _r4.returncode != 0:
                    errors.append("js_check found errors in harness output")
                    for l in _r4.stdout.splitlines():
                        if "ERROR" in l or "FAIL" in l: print("  " + l)
        else:
            # t2i hardening: fail closed. The render/js/pixel/locale/escaping/latex
            # stages are the review side's job (a green run must not "pass" with them
            # silently skipped -- Astra). node is required here; if it is genuinely
            # unavailable, run with SPARKTA_ALLOW_SKIP=1 to downgrade to a warning.
            _msg = "harness or node not available -- render/js/pixel checks could not run"
            if os.environ.get("SPARKTA_ALLOW_SKIP") == "1":
                warnings.append(_msg + " [SPARKTA_ALLOW_SKIP=1: warning only]")
            else:
                errors.append(_msg + " (set SPARKTA_ALLOW_SKIP=1 to allow)")
    _sh.rmtree(_tmp, ignore_errors=True)
else:
    # t2i hardening: the compile stage must not silently skip either.
    _msg = "javac or verify/sfi-stub not available -- compile + render checks could not run"
    if os.environ.get("SPARKTA_ALLOW_SKIP") == "1":
        warnings.append(_msg + " [SPARKTA_ALLOW_SKIP=1: warning only]")
    else:
        errors.append(_msg + " (set SPARKTA_ALLOW_SKIP=1 to allow)")

# =========================================================================
# Check 16: dist/sparkta.jar freshness -- must be newer than every .java file
# and must contain the current marginsPlot markers. A stale jar in the zip is
# what SSC/GitHub users would install.
# =========================================================================
print("16. jar freshness + integrity (dist/ AND ado/)...")
# t2i hardening: validate BOTH jars, not just dist/. The package's ado/sparkta.jar
# is the one Stata actually loads; the review release-gate ignored it and the two
# had drifted to an ancient 150-arg build (Astra). Each jar must: be fresh, carry
# the current classes (PubTable + ChartRenderer markers), bundle the offline JS,
# expect 210 args, and NOT contain com/stata (which would shadow Stata's SFI).
# The two jars must be byte-identical (same build).
import zipfile as _zf, hashlib as _hl
_dev = "--dev" in sys.argv
_newest = max(os.path.getmtime(f) for f in _glob.glob(os.path.join(JAVA_BASE, "**", "*.java"), recursive=True))
def _sev(msg):
    (warnings if _dev else errors).append(msg + (" [--dev: warning only]" if _dev else ""))
def _check_jar(_jar, _lbl):
    if not os.path.exists(_jar):
        errors.append(f"{_lbl} missing"); return None
    _md5 = _hl.md5(open(_jar, "rb").read()).hexdigest()
    if os.path.getmtime(_jar) < _newest:
        _sev(f"{_lbl} is OLDER than the Java sources -- rebuild (build.bat/.sh)")
    try:
        with _zf.ZipFile(_jar) as _z:
            _names = _z.namelist()
            if any(n.startswith("com/stata/") for n in _names):
                errors.append(f"{_lbl} contains com/stata classes -- would shadow Stata's SFI at runtime")
            if not any(n.endswith("PubTable.class") for n in _names):
                _sev(f"{_lbl} lacks PubTable.class -- stale/wrong build")
            if not any(n.endswith("sparkta_engine.js") for n in _names):
                _sev(f"{_lbl} lacks bundled JS resources -- offline/self-contained default broken")
            _db = [n for n in _names if n.endswith("DashboardBuilder.class")]
            if _db and b"210 expected" not in _z.read(_db[0]):
                _sev(f"{_lbl} is not the 210-argument build -- stale/wrong jar")
            _cr = [n for n in _names if n.endswith("ChartRenderer.class")]
            if not _cr:
                errors.append(f"{_lbl} has no ChartRenderer.class")
            else:
                _b = _z.read(_cr[0])
                # markers that are real standalone string constants in the class
                # (mpLegend/_mpTip live only in comments/concatenations -> unreliable).
                for _mk in (b"mpW_L2_", b"_cpTip", b"mpA_L2_", b"cpInner"):
                    if _mk not in _b: _sev(f"{_lbl} ChartRenderer.class lacks marker {_mk.decode()} -- stale build")
    except Exception as _e:
        errors.append(f"cannot read {_lbl}: {_e}")
    return _md5
_m_dist = _check_jar(os.path.join(BASE, "dist", "sparkta.jar"), "dist/sparkta.jar")
_m_ado  = _check_jar(os.path.join(BASE, "ado",  "sparkta.jar"), "ado/sparkta.jar")
if _m_dist and _m_ado and _m_dist != _m_ado:
    _sev("dist/sparkta.jar and ado/sparkta.jar DIFFER -- they must be the same build")
elif _m_dist and _m_ado:
    print("  [OK] dist and ado jars present, current, 210-arg, and byte-identical")

print("\n" + "=" * 60)
if errors:
    print(f"ERRORS: {len(errors)}")
    for e in errors:
        print(f"  [FAIL] {e}")
else:
    print("ALL CHECKS PASSED")

if warnings:
    print(f"\nWARNINGS: {len(warnings)}")
    for w in warnings:
        print(f"  [WARN] {w}")

line_counts = {
    "ado": len(ado.splitlines()),
    "ChartRenderer": len(cr.splitlines()),
    "HtmlGenerator": len(hg.splitlines()),
}
print(f"\nLine counts: {line_counts}")
print("=" * 60)

sys.exit(1 if errors else 0)

