#!/usr/bin/env python3
"""
ado_lint.py -- static checks for sparkta.ado (v3.6.0-s8b)

Stata has no compiler; these checks catch the classes of error that have cost
build round-trips on this project:
  1. line endings uniform (CRLF) and ASCII-only
  2. version string agreement: *! header, `sparkta_version` local, HtmlGenerator.VERSION
  3. program/end balance and brace balance PER PROGRAM (strings/comments stripped)
  4. per-logical-line double-quote balance (/// continuations joined)
  5. /* */ comment balance
  6. no bare %g display formats (invalid in Stata -> r(120))
  7. no leftover [DBG] display lines
  8. main syntax block: duplicate options, REAL Stata min-abbreviation ambiguities
  9. options declared in syntax but never referenced as `name'
 10. javacall arg count vs DashboardBuilder args.length threshold
 11. no `local x = "..."` where a string is assigned with = (truncates at 245 chars
     in older Stata; use `local x "..."`) -- reported as warnings

Usage: python3 ado_lint.py <path/to/sparkta.ado> [<path/to/HtmlGenerator.java>] [<DashboardBuilder.java>]
Exit 0 = clean, 1 = errors. ASCII only.
"""
import re, sys, os

def main():
    ado_path = sys.argv[1] if len(sys.argv) > 1 else 'ado/sparkta.ado'
    hg_path  = sys.argv[2] if len(sys.argv) > 2 else 'java/src/main/java/com/dashboard_test/html/HtmlGenerator.java'
    db_path  = sys.argv[3] if len(sys.argv) > 3 else 'java/src/main/java/com/dashboard_test/DashboardBuilder.java'
    raw = open(ado_path, 'rb').read()
    errors, warns = [], []

    # 1. line endings + ASCII
    crlf = raw.count(b'\r\n'); lf_only = raw.count(b'\n') - crlf
    if crlf and lf_only: errors.append(f"mixed line endings: {crlf} CRLF, {lf_only} LF-only")
    bad = [i+1 for i, ln in enumerate(raw.split(b'\n')) if any(c > 127 for c in ln)]
    if bad: errors.append(f"non-ASCII bytes on lines {bad[:8]}")
    text = raw.decode('ascii', 'replace').replace('\r\n', '\n')
    lines = text.split('\n')

    # 2. versions
    m = re.search(r'^\*! sparkta version (\S+)', text, re.M)
    ver_hdr = m.group(1) if m else None
    m2 = re.search(r'local sparkta_version\s+"?([^"\n]+)"?', text)
    ver_loc = m2.group(1).strip() if m2 else None
    ver_java = None
    if os.path.exists(hg_path):
        m3 = re.search(r'VERSION\s*=\s*"([^"]+)"', open(hg_path).read())
        ver_java = m3.group(1) if m3 else None
    vs = {'header': ver_hdr, 'local': ver_loc, 'HtmlGenerator': ver_java}
    if len({v for v in vs.values() if v}) != 1: errors.append(f"version mismatch: {vs}")

    # helper: strip strings and comments from a logical line for brace counting
    def tokenize(s):
        """Return (code_without_strings_or_comments, unbalanced_quote_flag).
        Handles Stata simple strings "...", compound strings `"..."' (nestable),
        and // comments only when outside a string."""
        out = []; i = 0; n = len(s); depth = 0; in_simple = False
        while i < n:
            c = s[i]; nxt = s[i+1] if i + 1 < n else ''
            if in_simple:
                if c == '"': in_simple = False
                i += 1; continue
            if depth:
                if c == '`' and nxt == '"': depth += 1; i += 2; continue
                if c == '"' and nxt == "'": depth -= 1; i += 2; continue
                i += 1; continue
            if c == '`' and nxt == '"': depth += 1; i += 2; continue
            if c == '"': in_simple = True; i += 1; continue
            if c == '/' and nxt == '/': break
            out.append(c); i += 1
        return ''.join(out), (in_simple or depth != 0)

    def strip_code(s):
        return tokenize(s)[0]
    # join /// continuations into logical lines (keep origin line number)
    logical = []; buf = ''; start = None
    for i, ln in enumerate(lines):
        if start is None: start = i + 1
        code = ln
        if '///' in code:
            buf += code.split('///')[0] + ' '; continue
        buf += code; logical.append((start, buf)); buf = ''; start = None
    if buf: logical.append((start, buf))

    # 4b. a comment-only line inside a /// continuation silently ENDS the command
    #     (s8s: it truncated the syntax block -> "option X not allowed")
    cont = False
    for i, ln in enumerate(lines):
        st = ln.strip()
        if cont and st.startswith('//') and '///' not in st:
            errors.append(f"L{i+1}: comment line inside a /// continuation ends the command here: {st[:60]}")
        cont = '///' in st and not st.startswith('//')
    # 5. block comments
    depth = 0
    for ln_no, s in logical:
        depth += s.count('/*') - s.count('*/')
        if depth < 0: errors.append(f"L{ln_no}: unmatched */"); depth = 0
    if depth: errors.append(f"unclosed /* comment ({depth})")

    # 3. program/end + braces per program
    prog = None; bal = 0; nprog = 0; in_block = 0
    for ln_no, s in logical:
        st = s.strip()
        if st.startswith('*') and not st.startswith('*!'): continue
        if '/*' in st: in_block += 1
        if in_block:
            if '*/' in st: in_block -= 1
            continue
        c = strip_code(s)
        pm = re.match(r'\s*program\s+(?:define\s+)?(\w+)', c)
        if pm:
            if prog: errors.append(f"L{ln_no}: program {pm.group(1)} starts inside program {prog}")
            prog = pm.group(1); bal = 0; nprog += 1; continue
        if re.match(r'\s*end\s*$', c):
            if not prog: errors.append(f"L{ln_no}: 'end' outside program")
            elif bal != 0: errors.append(f"program {prog}: brace imbalance {bal:+d} at end (L{ln_no})")
            prog = None; continue
        bal += c.count('{') - c.count('}')
        if bal < 0: errors.append(f"L{ln_no}: '}}' closes more than opened in {prog}"); bal = 0
    if prog: errors.append(f"program {prog} has no 'end'")

    # 4. quote balance per logical line (double quotes only; compound quotes counted as pairs)
    for ln_no, s in logical:
        st = s.strip()
        if st.startswith('*') and not st.startswith('*!'): continue
        if tokenize(s)[1]: errors.append(f"L{ln_no}: unterminated string / compound quote: {st[:80]}")

    # 6. bare %g
    for ln_no, s in logical:
        if re.search(r'%g\b(?![0-9.])', s) and 'display' in s: errors.append(f"L{ln_no}: bare %g format (invalid in Stata, use %12.0g)")
    # 6b. `=expr' on an `if`/`else if` line: Stata expands macros BEFORE evaluating the
    #     condition, so the expression runs even when the condition is false (s8f bug).
    for ln_no, s in logical:
        if re.match(r"\s*(else\s+)?if\b", s) and "`=" in s:
            errors.append(f"L{ln_no}: `=expr' on an if-line runs even when the condition is false -- move it into a block")
    # 6c. one-line block brace (t2j): Stata requires the '{' that opens an if/else/
    #     foreach/forvalues/while/capture/quietly block to be the LAST token on its
    #     line, and the closing '}' to stand alone. A one-line "if x { ...; exit }"
    #     silently corrupts brace matching -- the parser skips looking for a lone '}',
    #     mis-pairs a later brace, and the error surfaces far away as "matching close
    #     brace not found" (it shipped in t2j's xrange/yrange guards; the chart even
    #     exported first, so only a real Stata run caught it -- this rule now does).
    #     NB brace COUNTING (rule 3) cannot see this: the braces are balanced.
    for ln_no, s in logical:
        if s.lstrip().startswith('*'): continue       # Stata comment line (* or *!)
        code = strip_code(s)                          # strings + // comments removed
        code = re.sub(r'/\*.*?\*/', '', code)         # inline /* */ block comments
        code = re.sub(r'\$\{[^}]*\}', '', code)       # ${global} macro refs (literal braces)
        cs = code.rstrip()
        if '{' in cs and not cs.endswith('{'):
            errors.append(f"L{ln_no}: opening brace '{{' must be the LAST token on the line "
                          f"(Stata brace rule); a one-line 'if x {{ ... }}' corrupts brace "
                          f"matching -> 'matching close brace not found': {s.strip()[:70]}")
    # 7. DBG
    for ln_no, s in logical:
        if '[DBG' in s: errors.append(f"L{ln_no}: leftover [DBG] display")
    # 11. local x = "..."
    for ln_no, s in logical:
        if re.match(r'\s*local\s+\w+\s*=\s*"', s) and 'subinstr' not in s and 'regexs' not in s and 'strtrim' not in s and 'trim(' not in s and 'itrim(' not in s and 'upper(' not in s and 'lower(' not in s and 'substr(' not in s and 'strofreal' not in s and 'string(' not in s and 'word(' not in s:
            warns.append(f"L{ln_no}: local = \"literal\" (use local x \"...\" to avoid 245-char truncation): {s.strip()[:70]}")

    # 12. STATA SEMANTICS (t2g fix 5) -- each rule is a run we lost; the help entry it rests on is named.
    #     These are errors, not warnings: the zip is refused.
    prev = ''
    for idx, (ln_no, s) in enumerate(logical):
        st = s.strip()
        # 12a. help matrix define: "matrix X = r(name)" never errors -- an absent r(name) is a missing scalar and
        #      X becomes a 1x1 missing. The copy must be guarded by "confirm matrix r(name)" on a nearby line.
        m = re.match(r'(?:capture\s+)?matrix\s+\S+\s*=\s*(r\(\w+\))\s*$', st)
        if m:
            window = ' '.join(x[1] for x in logical[max(0, idx - 6):idx])
            if f'confirm matrix {m.group(1)}' not in window:
                errors.append(f"L{ln_no}: matrix copy of {m.group(1)} without a preceding 'confirm matrix {m.group(1)}' (help matrix define: an absent r() matrix copies as a 1x1 missing)")
        # 12b. help macro: global macro names -- do not start one with an underscore, and do not define one as "";
        #      "global _x \"\"" was invalid syntax in run 53.
        if re.match(r'global\s+_', st):
            errors.append(f"L{ln_no}: global macro name starting with an underscore (run 53: invalid syntax): {st[:60]}")
        # 12c. help display: display takes a LIST of display expressions; "..." + cond(...) is not one.
        if re.match(r'(?:noisily\s+|quietly\s+)?di(?:splay)?\b', st) and re.search(r'"\s*\+\s*\w+\(', st):
            errors.append(f"L{ln_no}: display with \"...\" + f(...) (help display: not a display expression; build the string in a local): {st[:60]}")
        # 12d. help syntax: a value wrapped in compound quotes passed into an option -- if the receiving option is
        #      string asis the quotes are KEPT (an empty value arrives as ""). Flag opt(`"`x'"') on program calls.
        if re.match(r'sparkta_\w+\s*,', st) and re.search(r"\w+\(`\"`\w+'\"'\)", st):
            errors.append(f"L{ln_no}: option value wrapped in compound quotes on a component call (help syntax: string asis keeps them; pass opt(`x')): {st[:70]}")
        prev = st

    # 8. syntax block
    si = next((i for i, l in enumerate(lines) if l.strip().startswith('syntax') and 'varlist' in l), None)
    if si is not None:
        blk = []; i = si
        while i < len(lines):
            blk.append(lines[i])
            if '///' not in lines[i]: break
            i += 1
        clean = ' '.join(l.split('///')[0] for l in blk)
        opts = re.findall(r'\[([A-Za-z][A-Za-z0-9]*)(?:\([^)]*\))?\]', clean)
        opts = [o for o in opts if o not in ('varlist', 'if', 'in')]
        full = [o.lower() for o in opts]
        def minab(o):
            mm = re.match(r'[A-Z0-9]+', o); return (mm.group(0) if mm else o).lower()
        ab = [minab(o) for o in opts]
        dups = sorted({f for f in full if full.count(f) > 1})
        if dups: errors.append(f"duplicate options in syntax: {dups}")
        conf = set()
        for a in range(len(opts)):
            for b in range(a + 1, len(opts)):
                L = max(len(ab[a]), len(ab[b]))
                if L <= min(len(full[a]), len(full[b])) and full[a][:L] == full[b][:L]:
                    conf.add((opts[a], opts[b]))
        # singular/plural pairs saved by exact-match are warnings, not errors
        for c in sorted(conf):
            fa, fb = c[0].lower(), c[1].lower()
            plural = (fa + 's' == fb) or (fb + 's' == fa)
            (warns if plural else errors).append(f"min-abbrev ambiguity: {c[0]} / {c[1]}" + (" (plural pair; exact match saves full names)" if plural else ""))
        # 9. dead options
        body = text[text.find('syntax [varlist'):]
        dead = sorted(f for f in set(full) if body.lower().count('`' + f + "'") == 0)
        if dead: warns.append(f"options never referenced as `name': {dead}")
        print(f"  syntax block: {len(opts)} options, {len(conf)} abbreviation ambiguities")

    # 10. javacall arg count
    jc = text.find('javacall com.dashboard_test.DashboardBuilder')   # the main call only (s9v: other javacalls exist)
    if jc > 0:
        seg = text[jc:jc + 40000]
        n = 0
        for l in seg.split('\n'):
            st = l.strip()
            if st.startswith('`') or st.startswith('"') or st.startswith('args('): n += 1
            if st.startswith('if _rc'): break
        thr = None
        if os.path.exists(db_path):
            m4 = re.search(r'args\.length\s*<\s*(\d+)', open(db_path).read())
            thr = int(m4.group(1)) if m4 else None
        print(f"  javacall args: {n}; DashboardBuilder threshold: {thr}")
        if thr and n < thr: errors.append(f"javacall passes {n} args but Java requires >= {thr}")

    print(f"  programs: {nprog}, logical lines: {len(logical)}")
    for w in warns: print("  WARN  " + w)
    for e in errors: print("  ERROR " + e)
    print(f"ado_lint: {'CLEAN' if not errors else str(len(errors)) + ' ERROR(S)'}, {len(warns)} warning(s)")
    sys.exit(1 if errors else 0)

if __name__ == '__main__':
    main()
