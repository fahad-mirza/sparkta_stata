# check_build.py  v1.3  (v3.5.37)
# Pre-package sanity checks run automatically by build.bat before compilation.
# Checks:
#   1. All .java files: brace balance using proper char-by-char parser
#      (regex stripping misses edge cases in Java string literals)
#   2. All .java files: ASCII-only (no Unicode chars)
#   3. FilterRenderer.java: boolean declarations match usage
#   4. Version consistency: HtmlGenerator.VERSION vs sparkta.ado *! header
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

# ---- FilterRenderer boolean declaration check ----
fr_path = os.path.join(JAVA_SRC, 'html/FilterRenderer.java')
if os.path.exists(fr_path):
    content = open(fr_path).read()
    method_pattern = re.compile(r'\n    String (build\w+)\(.*?\{', re.DOTALL)
    positions = [(m.group(1), m.start(), m.end()) for m in method_pattern.finditer(content)]
    for i, (mname, start, body_start) in enumerate(positions):
        end  = positions[i+1][1] if i+1 < len(positions) else len(content)
        body = content[body_start:end]
        declared = set(re.findall(r'boolean\s+(\w+)\s*=', body))
        used     = set(re.findall(r'(?:\(|&&|\|\|)\s*(is\w+)', body))
        for var in used:
            if var not in declared:
                errors.append(f"  FilterRenderer.java {mname}(): uses '{var}' but never declares it")

# ---- Version consistency: HtmlGenerator.VERSION vs sparkta.ado *! header (v1.3) ----
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
