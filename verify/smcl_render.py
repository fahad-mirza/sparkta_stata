#!/usr/bin/env python3
"""smcl_render.py -- approximate SMCL -> plain-text renderer (sparkta v3.6.0, 2026-09-13)

There is no Stata in the cloud, so this lays a .sthlp out the way the Viewer does, close
enough to READ the result and to measure column overflow: paragraphs ({p n1 n2 n3},
{phang}, {pstd}, {pmore}, {pin}), two-column tables ({p2colset}/{p2col}), option tables
({synoptset}/{synopt}/{syntab}), titles, dlgtabs, hlines. Inline directives ({cmd:} {it:}
{bf:} {opt} {helpb} {stata} {browse} ...) are reduced to their visible text.
Usage: python3 verify/smcl_render.py ado/sparkta.sthlp [width] > out.txt
       python3 verify/smcl_render.py ado/sparkta.sthlp --audit   (only the findings)
Findings: a literal brace in the rendered text (a directive SMCL would not have
understood), a first-column entry wider than its column (the Viewer pushes the second
column to the next line), a {syntab} heading with no blank line before it.
"""
import re, sys, textwrap

def inline(s):
    """Reduce inline directives to visible text."""
    prev = None
    while prev != s:
        prev = s
        s = re.sub(r"\{(?:cmd|it|bf|txt|err|res|ul|hi|input|sf|var|newvar|varlist|ifin|c):?([^{}]*)\}", r"\1", s)
        s = re.sub(r"\{opt ([^{}]*)\}", r"\1", s)
        s = re.sub(r"\{opth ([^{}]*)\}", r"\1", s)
        s = re.sub(r"\{(?:helpb|help|browse|stata|search|net|ado|view|dtsel|bind) [^{}:]*:([^{}]*)\}", r"\1", s)
        s = re.sub(r"\{(?:helpb|help|browse|stata) ([^{}]*)\}", r"\1", s)
        s = re.sub(r"\{space (\d+)\}", lambda m: " " * int(m.group(1)), s)
        s = re.sub(r"\{hline (\d+)\}", lambda m: "-" * int(m.group(1)), s)
        s = re.sub(r"\{break\}", "\n", s)
        s = re.sub(r"\{\.\.\.\}", "", s)
        s = re.sub(r"\{c 39\}", "'", s)
    return s

def wrap(text, first, hang, width):
    text = re.sub(r"\s+", " ", text.strip())
    if not text: return [""]
    return textwrap.wrap(text, width=width, initial_indent=" " * first, subsequent_indent=" " * hang,
                         break_long_words=False, break_on_hyphens=False) or [""]

def render(lines, width=100):
    out, findings = [], []
    para = None          # (first, hang) of an open paragraph, text accumulates in buf
    buf = []
    p2 = (8, 36, 36, 2); syn = 32; tabbed = False
    def flush():
        nonlocal buf, para
        if para is not None:
            out.extend(wrap(inline(" ".join(buf)), para[0], para[1], width))
        buf, para = [], None
    def twocol(a, b, c1, c2, cont, lineno, what):
        a, b = inline(a).strip(), inline(b).strip()
        col1 = " " * c1 + a
        if len(col1) + 2 > c2:
            findings.append("%d: %s first column overflows (%d chars, column is %d): %s" % (lineno, what, len(a), c2 - c1 - 2, a))
            out.append(col1)
            out.extend(wrap(b, c2, cont, width))
        else:
            rows = wrap(b, 0, 0, width - c2) if b else [""]
            out.append(col1.ljust(c2) + rows[0])
            for r in rows[1:]: out.append(" " * cont + r)
    for i, raw in enumerate(lines, 1):
        l = raw.rstrip("\n")
        s = l.strip()
        if s == "":
            flush(); out.append(""); continue
        if s.startswith("{smcl}") or s.startswith("{* "): continue
        m = re.match(r"\{p2colset (\d+) (\d+) (\d+) (\d+)\}", s)
        if m: flush(); p2 = tuple(int(x) for x in m.groups()); continue
        if s.startswith("{p2colreset"): flush(); continue
        if s.startswith("{p2line"): flush(); out.append(" " * p2[0] + "-" * (width - p2[0] - 4)); continue
        m = re.match(r"\{synoptset (\d+)( tabbed)?\}", s)
        if m: flush(); syn = int(m.group(1)); tabbed = bool(m.group(2)); continue
        if s.startswith("{synopthdr"): flush(); out.append(" " * 4 + "options".ljust(syn + 4) + "Description"); continue
        if s.startswith("{synoptline"): flush(); out.append(" " * 4 + "-" * (width - 8)); continue
        m = re.match(r"\{syntab:(.*)\}", s)
        if m:
            flush()
            if out and out[-1].strip() != "" and not out[-1].startswith("    ---"):
                findings.append("%d: syntab '%s' has no blank line before it" % (i, m.group(1)))
            out.append(" " * 4 + m.group(1)); continue
        m = re.match(r"\{title:(.*)\}", s)
        if m: flush(); out.append(inline(m.group(1))); continue
        m = re.match(r"\{dlgtab:(.*)\}", s)
        if m: flush(); out.append(""); out.append("    " + inline(m.group(1)) + "  " + "-" * 40); continue
        if s.startswith("{hline}"): flush(); out.append("-" * width); continue
        if s.startswith("{marker"): flush(); continue
        m = re.match(r"\{p2col:(.*)\}(.*?)\{p_end\}\s*$", s) or re.match(r"\{p2col:(.*)\}(.*)$", s)
        if m and s.startswith("{p2col:"):
            flush()
            # the first column ends at the brace that closes {p2col:  -- find it by depth
            depth, j = 0, 0
            for j, ch in enumerate(s):
                if ch == "{": depth += 1
                elif ch == "}":
                    depth -= 1
                    if depth == 0: break
            a = s[len("{p2col:"):j]; b = s[j + 1:].replace("{p_end}", "")
            c1, c2, cont = p2[0], p2[1], p2[2]
            twocol(a, b, c1, c2, cont, i, "p2col"); continue
        if s.startswith("{synopt:"):
            flush()
            depth, j = 0, 0
            for j, ch in enumerate(s):
                if ch == "{": depth += 1
                elif ch == "}":
                    depth -= 1
                    if depth == 0: break
            a = s[len("{synopt:"):j]; b = s[j + 1:].replace("{p_end}", "")
            c1 = 6 if tabbed else 4
            twocol(a, b, c1, syn + 4 + 2, syn + 4 + 2, i, "synopt"); continue
        m = re.match(r"\{p (\d+) (\d+) (\d+)\}(.*)$", s)
        if m: flush(); para = (int(m.group(1)), int(m.group(2))); s = m.group(4)
        elif s.startswith("{pstd}"): flush(); para = (4, 4); s = s[6:]
        elif s.startswith("{phang}"): flush(); para = (4, 8); s = s[7:]
        elif s.startswith("{pmore}"): flush(); para = (8, 8); s = s[7:]
        elif s.startswith("{pin}"): flush(); para = (8, 8); s = s[5:]
        elif s.startswith("{p}"): flush(); para = (0, 0); s = s[3:]
        if para is None: para = (0, 0)
        if "{p_end}" in s:
            buf.append(s.replace("{p_end}", "")); flush()
        else:
            buf.append(s)
    flush()
    for k, l in enumerate(out, 1):
        if "{" in l or "}" in l:
            findings.append("rendered line %d still has a brace: %s" % (k, l.strip()[:90]))
    return out, findings

if __name__ == "__main__":
    path = sys.argv[1]
    audit = "--audit" in sys.argv
    width = 100
    for a in sys.argv[2:]:
        if a.isdigit(): width = int(a)
    out, findings = render(open(path).read().split("\n"), width)
    if audit:
        print("\n".join(findings)); print("smcl_render: %d finding(s)" % len(findings))
        sys.exit(1 if findings else 0)
    print("\n".join(out))
    if findings:
        sys.stderr.write("\n".join(findings) + "\nsmcl_render: %d finding(s)\n" % len(findings))
