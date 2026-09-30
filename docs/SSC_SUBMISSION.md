# sparkta v3.6.0 -- SSC submission notes (2026-09-13, revised 2026-09-16 fix9u)

What the SSC archive rules say (http://fmwww.bc.edu/repec/bocode/s/sscsubmit.html,
read 2026-09-13) and how the release folder maps onto them.

## Rules that apply

1. Files go in ONE zip, e-mailed to the archive maintainer (Kit Baum), saying whether
   the material is new or a revision. sparkta is NEW.
2. Do NOT send a .pkg or stata.toc: "It is not necessary nor desirable to generate a
   Stata-format package (.pkg) file, since the SSC archive software generates the package
   file automatically." (Our ado/sparkta.pkg and ado/stata.toc stay in the GitHub
   `net install` folder only.)
3. Every ado must carry a `version n.n` statement (done: all 22 files say `version 17`;
   six helpers gained it at the tag).
4. Help must be .sthlp (ado/sparkta.sthlp).
5. A NEW package needs: suggested package name, a title line, and the abstract that
   appears in the package listing. Suggested text is at the bottom of this file.
6. Ancillary files (do-files, data) may be included. The rules do not mention binary
   files. [stated] Fahad (2026-09-16): SSC does copy .jar files over as regular package
   files, so sparkta.jar (394 KB, platform-independent Java, Stata 17+) is listed like any
   other file and `ssc install sparkta` puts it next to the ado files. No question to ask;
   the e-mail just says what the jar is.

## What to send

`sparkta_v3.6.0_ssc.zip` (built by `python3 make_ssc_zip.py`; the 2026-09-16 build carries the
fix9u ado + jar and the rev 2 help with the "Large data" paragraph) -- FLAT, no folders:

    sparkta.ado + the 21 sparkta_*.ado helpers    (22 ado files, ASCII, version 17)
    sparkta.sthlp
    sparkta.jar                                   (subject to the OK above)

Not included: sparkta.pkg, stata.toc (SSC makes them), sparkta-export.jar (optional
developer speed-up, never shipped), README/CHANGELOG (GitHub only).

## Suggested package name / title / abstract

Package name: sparkta

Title line: 'SPARKTA': module to produce interactive self-contained HTML charts and
dashboards from Stata

Abstract: sparkta generates interactive, self-contained HTML charts from Stata using a
Java backend and Chart.js 4. One command produces a single .html file that opens in any
browser with no server, no Python and no R. It supports 20+ chart types (bar, line,
area, scatter, bubble, pie, donut, stacked and 100% stacked bars, CI bars and lines,
histogram, box and violin plots) and post-estimation charts (coefplot, marginsplot,
event study) drawn from e(), matrices or saved results, with a publication table
(LaTeX, Markdown, CSV). Pages offer live filter dropdowns, range sliders, by() panel
grids, summary statistics panels, reference lines and annotations, fit lines, colour
grammar compatible with colorpalette, and saveas() export to PNG, PDF and SVG through a
headless browser. Requires Stata 17 or later (uses Stata's bundled Java 11 runtime).

Author: Fahad Mirza, World Bank. E-mail: fm.109fahad.m@gmail.com
Keywords: interactive graphics, HTML, Chart.js, dashboard, coefplot, marginsplot,
event study, visualisation, Java

## If the jar ever had to be ancillary (not expected)

The ado finds the jar via `findfile` and also searches the current directory, so a jar
fetched with `net get sparkta` into the working folder would be found. No message change
is needed (the fix9u `net get` hint was added and then removed the same day, once Fahad
confirmed SSC copies jars over).

## Pre-send checklist

- [x] `python3 verify_before_zip.py` green (no --dev) on the release folder (2026-09-14)
- [x] real-Stata smoke test on the release build: test_tag_v360.do 6/6, test_release_v360.do
      210/210, test_fix9g_v360.do 13/13 on fix9h-e (2026-09-14); fix9s 210/210 (2026-09-15)
- [ ] fix9u on real Stata: tests/test_fix9u_v360.do, then test_release_v360.do 210/210
- [ ] clean `net install` on a 2nd Windows machine (roadmap 4a): run
      tests/test_netinstall_v360.do there after the push, expect 9/9
- [ ] git tag v3.5.111 first, then v3.6.0 on the commit that contains this folder
- [ ] e-mail to Kit: zip attached, NEW package, the name/title/abstract above (draft below)

## E-mail draft (to Kit Baum, SSC archive maintainer)

Subject: New package for SSC: sparkta (interactive HTML charts from Stata)

Dear Kit,

Please find attached sparkta_v3.6.0_ssc.zip, a NEW package for the SSC archive.

Suggested package name: sparkta
Title: 'SPARKTA': module to produce interactive self-contained HTML charts and dashboards
from Stata

Abstract: sparkta generates interactive, self-contained HTML charts from Stata using a
Java backend and Chart.js 4. One command produces a single .html file that opens in any
browser with no server, no Python and no R. It supports 20+ chart types (bar, line, area,
scatter, bubble, pie, donut, stacked and 100% stacked bars, CI bars and lines, histogram,
box and violin plots) and post-estimation charts (coefplot, marginsplot, event study)
drawn from e(), matrices or saved results, with a publication table (LaTeX, Markdown,
CSV). Pages offer live filter dropdowns, range sliders, by() panel grids, summary
statistics panels, reference lines and annotations, fit lines, colour grammar compatible
with colorpalette, and saveas() export to PNG, PDF and SVG through a headless browser.
Requires Stata 17 or later (uses Stata's bundled Java 11 runtime).

Author: Fahad Mirza, World Bank. E-mail: fm.109fahad.m@gmail.com
Keywords: interactive graphics, HTML, Chart.js, dashboard, coefplot, marginsplot,
event study, visualisation, Java

The zip is flat and contains 22 ado files (sparkta.ado plus 21 sparkta_*.ado helpers, all
with a version 17 statement), sparkta.sthlp, and sparkta.jar (394 KB). The jar is the
platform-independent Java backend that the ado loads through Stata's javacall; it is
compiled for Java 11, which every Stata 17+ installation bundles, so it needs no
installation step of its own -- it only has to sit next to the ado files, where the
command looks for it (findfile sparkta.jar).

No .pkg or stata.toc is included, per the archive rules. The source, tests and a chart
gallery are at https://github.com/fahad-mirza/sparkta_stata (MIT licence).

Many thanks for maintaining the archive.

Best regards,
Fahad Mirza
