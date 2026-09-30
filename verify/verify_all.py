#!/usr/bin/env python3
"""
verify_all.py -- run the complete sparkta verification system (v3.6.0-s8b)

    python3 verify/verify_all.py                 # source checks + headless render
    python3 verify/verify_all.py test_out        # ... plus validate Stata-generated HTML
    python3 verify/verify_all.py --dev           # jar staleness is a warning, not an error

Stages (each prints its own summary; any ERROR fails the run):
  1. verify_before_zip.py     16 checks: ASCII, braces, versions, arg wiring,
                              ado_lint, javac (SFI stub), harness + js_check,
                              dist/sparkta.jar freshness
  2. js_check.js <dir>        if a directory of Stata-generated HTML is given:
                              execute every chart script / plugin hook / tooltip
                              under mock Chart.js and apply verify/expectations.json
  2c. fidelity_check.py       page numbers == Stata's r(table) (_truth/, t2f fix 4)
  2b. intent_check.py         derive expectations from each sparkta command (+ the
                              estimation/margins command before it) and compare
  3. manifest check           if <dir>/_manifest.csv exists (from test_release_v360.do):
                              every case rc==0 except the r_err_* cases (which must fail)
  4. viz_audit.js <dir>       score every chart against verify/VIZ_STANDARD.md
                              (Cleveland-McGill, Tufte, Wilke, Few, WCAG 2.1);
                              advisory unless --strict-viz

Requirements: python3, javac (JDK 17+), node (18+). ASCII only.
"""
import os, sys, subprocess, csv

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
args = [a for a in sys.argv[1:] if not a.startswith('--')]
flags = [a for a in sys.argv[1:] if a.startswith('--')]
html_dir = args[0] if args else None
fails = 0

def stage(title):
    print("\n" + "=" * 70 + "\n" + title + "\n" + "=" * 70)

stage("STAGE 1: source checks (verify_before_zip.py)")
r = subprocess.run([sys.executable, os.path.join(BASE, 'verify_before_zip.py')] + flags, cwd=BASE)
if r.returncode != 0: fails += 1

if html_dir:
    d = html_dir if os.path.isabs(html_dir) else os.path.join(os.getcwd(), html_dir)
    stage(f"STAGE 2: Stata-generated HTML validation ({d})")
    r = subprocess.run(['node', os.path.join(BASE, 'verify', 'js_check.js'), d, os.path.join(BASE, 'verify', 'expectations.json')])
    if r.returncode != 0: fails += 1

    stage("STAGE 2b: intent check -- HTML vs the Stata command that made it (verify/intent_check.py)")
    r = subprocess.run([sys.executable, os.path.join(BASE, 'verify', 'intent_check.py'), d, os.path.join(BASE, 'test_release_v360.do')])
    if r.returncode != 0: fails += 1

    stage("STAGE 2c: numeric fidelity -- page numbers vs Stata's r(table) in _truth/ (verify/fidelity_check.py)")
    r = subprocess.run([sys.executable, os.path.join(BASE, 'verify', 'fidelity_check.py'), d, os.path.join(BASE, 'test_release_v360.do')])
    if r.returncode != 0: fails += 1

    stage("STAGE 2d: side-by-side with Stata's own graphs (_stata/ -> _sidebyside/, verify/sidebyside.py) -- visual, not scored")
    subprocess.run([sys.executable, os.path.join(BASE, 'verify', 'sidebyside.py'), d])

    stage("STAGE 2e: publication table -- compile each page's booktabs LaTeX with pdflatex (verify/latex_check.js)")
    import shutil as _sh2
    if _sh2.which('pdflatex'):
        r = subprocess.run(['node', os.path.join(BASE, 'verify', 'latex_check.js'), d])
        if r.returncode != 0: fails += 1
    else:
        print("  pdflatex not available -- skipped (install texlive-latex-base + texlive-latex-extra)")

    man = os.path.join(d, '_manifest.csv')
    if os.path.exists(man):
        stage("STAGE 3: run manifest (_manifest.csv)")
        bad = []
        with open(man) as f:
            for row in csv.DictReader(f):
                name, rc, ex = row['case'], int(row['rc']), row['file_exists'] == '1'
                expect_fail = name.startswith('r_err_')
                ok = (rc != 0) if expect_fail else (rc == 0 and ex)
                print(f"  {'OK  ' if ok else 'FAIL'} {name:40s} rc={rc:<4d} file={'y' if ex else 'n'}  {row.get('note','')}")
                if not ok: bad.append(name)
        print(f"manifest: {len(bad)} unexpected result(s)" + (": " + ", ".join(bad) if bad else ""))
        if bad: fails += 1

    stage("STAGE 4: visual-quality audit (verify/VIZ_STANDARD.md)")
    r = subprocess.run(['node', os.path.join(BASE, 'verify', 'viz_audit.js'), d] + (['--strict'] if '--strict-viz' in flags else []))
    if r.returncode != 0: fails += 1
    print("(stage 4 is advisory unless --strict-viz; see <dir>/_viz_audit_report.json)")

stage("RESULT: " + ("ALL STAGES PASSED" if fails == 0 else f"{fails} STAGE(S) FAILED"))
sys.exit(1 if fails else 0)
