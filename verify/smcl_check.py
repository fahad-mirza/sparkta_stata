#!/usr/bin/env python3
"""smcl_check.py -- structural check of a .sthlp file (sparkta v3.6.0, 2026-09-13)

No Stata in the cloud, so this models the SMCL rules that made `help sparkta` print raw
directives: a paragraph opened by {p ...} {phang} {pstd} {pmore} {pin} {p2col:} {synopt:}
stays open until {p_end} or a BLANK line; a block directive ({synoptset} {synopthdr}
{synoptline} {syntab} {p2colset} {p2line} {p2colreset} {title} {dlgtab} {marker} {hline})
that starts while a paragraph is still open is printed verbatim, and everything after
it inside the paragraph too. Also: {synopt:} only between {synoptset} and {synoptline},
{p2col:} only between {p2colset} and {p2colreset}; balanced braces per line; ASCII;
every directive name known; every helpb anchor defined.
Usage: python3 verify/smcl_check.py ado/sparkta.sthlp   (exit 1 on any finding)
"""
import re, sys

KNOWN = set("""smcl p p_end p2col p2colset p2colreset p2line title marker cmd it bf helpb help browse
stata hline break opt opth varlist ifin dlgtab txt err res ul newvar var col c space tab bind synopt
synoptset synoptline synopthdr pstd phang phang2 phang3 pmore pin p2coldent hi input search net ado
dtsel view sf syntab ...""".split())
BLOCK = ("{synoptset", "{synopthdr", "{synoptline", "{syntab", "{p2colset", "{p2line", "{p2colreset",
         "{title", "{dlgtab", "{marker", "{hline")
PARA = ("{p ", "{p}", "{phang", "{pstd", "{pmore", "{pin", "{p2col", "{synopt:")

def main(path):
    text = open(path, "rb").read()
    errs = []
    if any(b > 127 for b in text): errs.append("non-ASCII byte in file")
    lines = text.decode("ascii", "replace").split("\n")
    in_para = False; in_syn = False; in_p2 = False
    markers = set(re.findall(r"\{marker (\w+)\}", "\n".join(lines)))
    for i, l in enumerate(lines, 1):
        s = l.strip()
        if len(l) > 240:
            errs.append("%d: line is %d characters (the Viewer corrupts text past ~250; fold it at a space outside any directive)" % (i, len(l)))
        if l.count("{") != l.count("}"):
            errs.append("%d: unbalanced braces: %s" % (i, s[:80]))
        for m in re.finditer(r"\{([A-Za-z_0-9.]+)", l):
            if m.group(1) not in KNOWN and not m.group(1).startswith("*"):
                errs.append("%d: unknown directive {%s" % (i, m.group(1)))
        for m in re.finditer(r"sparkta##(\w+)", l):
            if m.group(1) not in markers: errs.append("%d: helpb anchor not defined: %s" % (i, m.group(1)))
        if s == "":
            in_para = False; continue
        if s.startswith(BLOCK) and in_para:
            errs.append("%d: block directive inside an open paragraph (add {p_end} or a blank line before it): %s" % (i, s[:70]))
            in_para = False
        if s.startswith("{synoptset"): in_syn = True
        if s.startswith("{synoptline") and in_syn and not any(x.strip().startswith("{synopt:") for x in lines[i:i+3]): in_syn = False
        if s.startswith("{p2colset"): in_p2 = True
        if s.startswith("{p2colreset"): in_p2 = False
        if s.startswith("{synopt:") and not in_syn: errs.append("%d: {synopt:} outside synoptset..synoptline" % i)
        if s.startswith("{p2col:") and not in_p2: errs.append("%d: {p2col:} outside p2colset..p2colreset" % i)
        if s.startswith(PARA):
            in_para = not s.endswith("{p_end}")
        elif "{p_end}" in s:
            in_para = False
    if errs:
        print("\n".join(errs[:60])); print("smcl_check: %d finding(s) in %s" % (len(errs), path)); return 1
    print("smcl_check: OK -- %s (%d lines)" % (path, len(lines))); return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "ado/sparkta.sthlp"))
