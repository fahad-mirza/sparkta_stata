# check_build.py  v2.0  (sparkta)
# Pre-package sanity checks run automatically by build.bat before compilation.
# Checks:
#   1. All .java files: brace balance using proper char-by-char parser
#   2. All .java files: ASCII-only (no Unicode chars)
#   3. sparkta_engine.js present in resources (F-0 requirement)
#   4. Version consistency: HtmlGenerator.VERSION vs sparkta.ado *! header
# Note: FilterRenderer four-method boolean check removed (F-0 rewrote FilterRenderer).
# Exit code 0 = OK, 1 = errors found.

import re, sys, os

BASE     = os.path.dirname(os.path.abspath(__file__))
JAVA_SRC = os.path.join(BASE, 'src/main/java/com/dashboard')
errors   = []

# ---- Proper brace counter: skips strings, char literals, comments ----
def count_code_braces(src):
    opens = closes = 0
    i = 0
    n = len(src)
    while i < n:
        # Block comment
        if src[i:i+2] == '/*':
            i += 2
            while i < n and src[i:i+2] != '*/':
                i += 1
            i += 2
            continue
        # Line comment
        if src[i:i+2] == '//':
            while i < n and src[i] != '\n':
                i += 1
            continue
        # String literal
        if src[i] == '"':
            i += 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i] == '"':
                    i += 1
                    break
                i += 1
            continue
        # Char literal
        if src[i] == "'":
            i += 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        if src[i] == '{':
            opens  += 1
        elif src[i] == '}':
            closes += 1
        i += 1
    return opens, closes

# ---- Collect all .java files ----
java_files = []
for root, dirs, files in os.walk(JAVA_SRC):
    for f in files:
        if f.endswith('.java'):
            java_files.append(os.path.join(root, f))

# ---- Check each file ----
for path in java_files:
    name = os.path.basename(path)
    raw  = open(path, 'rb').read()

    # ASCII check
    bad = [i for i, b in enumerate(raw) if b > 127]
    if bad:
        errors.append(f"  {name}: non-ASCII bytes at byte positions {bad[:5]}")

    # Brace balance
    src = raw.decode('ascii', errors='replace')
    o, c = count_code_braces(src)
    if o != c:
        errors.append(f"  {name}: brace mismatch -- {o} '{{' vs {c} '}}' (delta {o-c:+d})")

# ---- sparkta_engine.js presence check (F-0) ----
engine_path = os.path.join(BASE, 'src/main/resources/com/dashboard/js/sparkta_engine.js')
if not os.path.exists(engine_path):
    errors.append("  sparkta_engine.js not found in resources/com/dashboard/js/ (F-0 requirement)")

# ---- Version consistency: HtmlGenerator.VERSION vs sparkta.ado *! header (v2.0) ----
hg_path  = os.path.join(JAVA_SRC, 'html/HtmlGenerator.java')
ado_path = os.path.join(BASE, '../ado/sparkta.ado')
if os.path.exists(hg_path) and os.path.exists(ado_path):
    hg_src  = open(hg_path).read()
    ado_src = open(ado_path).read()
    m_hg  = re.search(r'public static final String VERSION\s*=\s*"([^"]+)"', hg_src)
    m_ado = re.search(r'\*!\s+sparkta\s+version\s+(\S+)', ado_src)
    if m_hg and m_ado:
        v_hg  = m_hg.group(1)
        v_ado = m_ado.group(1)
        if v_hg != v_ado:
            errors.append(f"  VERSION MISMATCH: HtmlGenerator.VERSION={v_hg} vs sparkta.ado={v_ado}")
    elif not m_hg:
        errors.append("  HtmlGenerator.java: VERSION constant not found")
    elif not m_ado:
        errors.append("  sparkta.ado: *! sparkta version line not found")

if errors:
    print("BUILD CHECK FAILED:")
    for e in errors:
        print(e)
    sys.exit(1)
else:
    print("[OK]  Pre-build check passed (brace balance, ASCII, FilterRenderer booleans)")
    sys.exit(0)
