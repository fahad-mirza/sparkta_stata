# Sparkta verification system (v3.6.0-s8b)

Stata has no compiler and Chart.js swallows plugin exceptions, so bugs in
sparkta historically surfaced only after a build.bat + Stata + browser cycle.
This folder checks every layer it CAN without Stata (ASCII, ado-lint, Java
compile, headless HTML render + pixels).

IMPORTANT -- this does NOT close the loop. There is no Stata here, so a green
run does NOT prove the .ado loads or runs: it cannot catch a load-time
"command <x> not defined by <x>.ado", a runtime rc, or a Stata-version parser
quirk. ado_lint's quote check is parity-based and does not model nesting.
After ANY .ado change, a real-Stata smoke-test is mandatory before calling it
done (discard; which sparkta; sparkta price mpg, type(bar); + one per changed
path). See project_memory.md Section 2 rules 17-19 and the fix8f post-mortem in
Section 6.

## One command

    python3 verify/verify_all.py --dev            # before packaging a handover zip
    python3 verify/verify_all.py                  # before a release (jar must be fresh)
    python3 verify/verify_all.py test_out         # + validate Stata-generated HTML

Needs: python3, javac (JDK 17+), node (18+).

## What each piece does

| File                     | Layer  | Catches                                                     |
|--------------------------|--------|-------------------------------------------------------------|
| `ado_lint.py`            | Stata  | unterminated strings / compound quotes, brace balance per   |
|                          |        | program, version drift, bare `%g`, leftover `[DBG]`, real   |
|                          |        | Stata min-abbreviation ambiguities, unused options,         |
|                          |        | javacall arg count vs `args.length`                         |
| `sfi-stub/`              | Java   | minimal `com.stata.sfi` so `javac` can compile the whole    |
|                          |        | tree here: scope, symbol, type errors (never ship this jar) |
| `harness/Harness.java`   | Java   | drives the REAL `DashboardBuilder.execute()` for 16 post-   |
|                          |        | estimation scenarios (coefplot, eventstudy, marginsplot     |
|                          |        | T1-T16 incl. nested-CI-on-numeric-x, two-series nested CI,  |
|                          |        | single point, all-null, dark) -- no Stata needed            |
| `js_check.js`            | JS     | runs every chart script under a mock Chart.js, then invokes |
|                          |        | EVERY plugin hook and tooltip callback with a mock chart -> |
|                          |        | ReferenceError / TypeError inside our plugins, `{x:,y:}`,   |
|                          |        | empty array elements; applies `expectations.json`           |
| `expectations.json`      | JS     | per-file assertions for `test_release_v360.do` output:      |
|                          |        | chart type, numeric/category x, dataset count and names,    |
|                          |        | required/forbidden plugins, labels, tooltip text, HTML text |
| `intent_check.py`        | intent | derives what each page MUST contain from its sparkta command  |
|                          |        | and the estimation/margins command before it (type, cistyle,  |
|                          |        | levels, connected, at(), over(), titles, show/omit/sort ...)  |
|                          |        | -- replaces hand-written expectations for alignment           |
| `viz_audit.js`           | design | scores every chart against `VIZ_STANDARD.md`: zero baseline, |
|                          |        | axis use, banking, label density, series/slice counts, legend, |
|                          |        | colour separability + deuteranopia, WCAG contrast, CI/ref lines|
| `VIZ_STANDARD.md`        | design | the rubric with sources (Cleveland-McGill, Tufte, Wilke, Few, |
|                          |        | WCAG 2.1, Okabe-Ito)                                          |
| `../verify_before_zip.py`| all    | 16 gates incl. all of the above + `dist/sparkta.jar`        |
|                          |        | freshness (`--dev` downgrades jar staleness to a warning)   |

## Release-suite workflow (Stata side)

    build.bat                       (also refreshes ..\dist\sparkta.jar)
    do test_release_v360.do         -> test_out\*.html + test_out\_manifest.csv
    zip test_out and share

Review side:

    python3 verify/verify_all.py test_out

Stage 3 reads `_manifest.csv`: every `r_*` case must have rc=0 and a file;
every `r_err_*` case must have rc!=0.

## Adding a scenario

1. Stata: add a `capture noisily sparkta ... export("`OUT'/r_<area>_<name>.html")`
   line followed by `_chk r_<area>_<name> `=_rc'` in `test_release_v360.do`.
2. Expectations: add `{"file": "^r_<area>_<name>", ...}` to `expectations.json`.
3. Headless (optional, for post-estimation types): add a builder in
   `Harness.java` using the arg positions from project_memory.md Section 4.

## Why the SFI stub is safe

Classes compiled against the stub are NEVER shipped. `build.bat` compiles
against Stata's real `sfi-api.jar`; the stub only proves the Java is
well-formed. Method signatures in the stub mirror the calls the code makes
(`Data.getNum(int,long)`, `ValueLabel.getLabel(String,double)` etc.).

## Mutation probe (added s8d)

`harness/Probe.java` takes a passing multi-model scenario and mutates one arg at
a time to the shapes the ado can send ("", "~~", "|", a single group). Run with
`java -Dsparkta.trace=1 -cp <classes>:<resources> Probe` to get a Java stack
trace for each failing mutation. This found the rc=5101 bug in one pass.

## Pixel stage (added s8z2) -- the blind spot the user found

`pixel_check.js` renders every harness chart with the REAL Chart.js on a real
canvas (node-canvas) and counts coloured ink outside the plot area (legend box
excluded). It caught the negative-plotmargin whisker drawing through the axis
(219 px on the s8x build, 0 after the clip fix). Runs inside verify_before_zip
check 15b when `canvas` + `chart.js` are installed:

    apt-get install libcairo2-dev libpango1.0-dev libjpeg-dev libgif-dev librsvg2-dev libnode-dev
    npm install canvas@2.11.2 chart.js@4.4.0 @sgratzl/chartjs-chart-boxplot@4.4.5 chartjs-chart-error-bars@4.4.0 --build-from-source --nodedir=/usr

(then symlink or NODE_PATH the node_modules into the handover folder). Without
them the stage prints "skipped". What it still cannot see: browser layout
(the zoom resize loop) -- that needs a real browser; the DOM contract is
enforced statically instead (canvas inside an unpadded .spk-chart-box).

## SVG stage (added s9l)

`svg_check.js` executes each download-enabled page's OWN `_spkChartToSvg()` headlessly
(canvas2svg inlined from the page, Chart.js on node-canvas, a minimal DOM from
@xmldom/xmldom) and checks the result is a real vector (paths + text); with
`rsvg-convert` installed it also rasterises it. `npm i @xmldom/xmldom`; `apt install librsvg2-bin`.

## by()+filter sweep (fix9a)

    javac -cp <classes> -d <classes> verify/harness/HarnessByFilter.java
    SPK_OFFLINE=1 java -cp <classes>:<sfi-stub> HarnessByFilter <outdir>     # 53 by() pages
    SPK_CHROME=/path/to/chrome node verify/byfilter_probe.js <outdir>       # fixture truth
    node verify/byfilter_probe.js test_out_byfilter --embedded              # Stata-made pages

`HarnessByFilter.java` renders every data chart type as a by() page with filters()
(and sliders()) on the SFI fixture; the stub's `Data.markoutRep78` emulates the ado
markout. `byfilter_probe.js` drives EVERY dropdown value (and a slider range) in
headless Chromium and compares each panel's datasets with an independent recomputation
(means/sums/medians per var x group, box medians, cibar/ciline means, histogram bins
from the panel's own `_binEdges_<id>`, pie shares, 100% shares, scatter point counts,
n badges, JS errors). `--embedded` takes the truth from the page's own columns instead
of the fixture, for the pages `test_byfilter_v360.do` makes on real Stata.

## Large-scatter oracle (fix9g)

    javac -cp <classes> -d <classes> verify/harness/HarnessBig.java
    SPK_STUB_ECHO=1 SPK_OFFLINE=1 java -Xmx2g -cp <classes>:<sfi-stub> HarnessBig <outdir>   # 9 pages
    node verify/bigscatter_probe.js <outdir> --out <outdir>/_probe [--ref <pre-fix probe dir>]
    node verify/byfilter_probe.js <outdir> --embedded --glob big09    # the by()+filter page

`HarnessBig.java` renders 100,000-point scatter/bubble pages (plain, over(), filters(),
bubble, fit(lfit) fitci, by(), by()+filter) plus a 5,000-point control and a 20,001-point
edge page on the stub's seeded synthetic fixture (`Data.setBig(n, seed)`); `SPK_STUB_ECHO=1`
echoes the Stata-side notes (the large-data note). `bigscatter_probe.js` hooks
`Chart.prototype.draw/update` before Chart.js loads and reports load time, the draws in
the settle window, the draws ONE dispatched mousemove triggers (Chart.js throttles DOM
events to requestAnimationFrame, so the cost lands in the following frames), the tooltip
opacity at 450 ms, a filter change, the spkBigScatter bitmap state, and screenshots the
canvas at rest; `--ref` compares it pixel-for-pixel with an earlier run (pass: max channel
delta <= 16 on <= 5 per cent of the pixels -- the 8-bit compositing rounding of translucent
markers through a transparent bitmap; a real regression is 100+ over 15-25 per cent).
Numbers before/after fix9g are in docs/CHANGELOG_sparkta_full.md. Not part of the gate
(renders take ~10 s, the probe ~4 minutes): run it after any change to scatter/bubble
datasets, the crisp stack or the export clones.
