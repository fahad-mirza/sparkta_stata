#!/usr/bin/env python3
"""make_ssc_zip.py -- sparkta v3.6.0 (2026-09-13)

Builds the FLAT zip the SSC archive wants (docs/SSC_SUBMISSION.md): every ado file listed
in ado/sparkta.pkg, the sthlp and the jar, no folders, no .pkg, no stata.toc.
Refuses to run if the pkg lists a file that is missing, if any listed file is non-ASCII,
or if dist/sparkta.jar and ado/sparkta.jar differ (one build must write both).

Usage:  python3 make_ssc_zip.py            -> sparkta_v3.6.0_ssc.zip in the package root
"""
import os, re, sys, zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
ADO = os.path.join(ROOT, "ado")
pkg = open(os.path.join(ADO, "sparkta.pkg")).read()
files = re.findall(r"^f (\S+)", pkg, re.M)
ver = re.search(r'local sparkta_version "([^"]+)"', open(os.path.join(ADO, "sparkta.ado")).read()).group(1)
out = os.path.join(ROOT, "sparkta_v%s_ssc.zip" % ver)

errors = []
for f in files:
    p = os.path.join(ADO, f)
    if not os.path.exists(p):
        errors.append("missing: " + f); continue
    if not f.endswith(".jar"):
        data = open(p, "rb").read()
        if any(b > 127 for b in data):
            errors.append("non-ASCII byte in " + f)
        if f.endswith(".ado") and not re.search(r"^\s*version \d+", data.decode("ascii", "replace"), re.M):
            errors.append("no version statement in " + f)
        if not data.startswith(b"*! sparkta version " + ver.encode()) and f.endswith(".ado"):
            errors.append("header version != %s in %s" % (ver, f))
if open(os.path.join(ROOT, "dist", "sparkta.jar"), "rb").read() != open(os.path.join(ADO, "sparkta.jar"), "rb").read():
    errors.append("dist/sparkta.jar and ado/sparkta.jar differ")
if errors:
    print("\n".join("ERROR " + e for e in errors)); sys.exit(1)

with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for f in files:
        z.write(os.path.join(ADO, f), f)   # flat: arcname is the bare file name
print("wrote %s (%d files, %d bytes)" % (os.path.basename(out), len(files), os.path.getsize(out)))
