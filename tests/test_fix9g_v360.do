// =============================================================================
// test_fix9g_v360.do -- Sparkta v3.6.0 fix9g smoke test: large-data mode (2026-09-14)
// =============================================================================
// Version: 1.2 (fix9g-c: fit() strings capped by length -- the 100k fit+ci export case). ASCII only.
//
// WHAT IT COVERS (Java only; the ado body is unchanged since the v3.6.0 tag build)
//   fix9g  scatter/bubble pages with more than 20,000 points switch to large-data mode
//          automatically: the point cloud is drawn once onto a bitmap (no per-hover redraw),
//          the load animation is off, and Stata prints
//            "large-data mode: N points drawn once, no animation (hover and export unchanged)".
//          Tooltips, mlabel(), fit lines, CI bands, statistics, filters and by() panels are
//          unchanged. fix9g-b: exports (SVG / PDF / PNG) of a large page carry the point
//          cloud as ONE raster image at 2x (axes, text, fit line and band stay vector) --
//          the all-vector export of 100k points was 52 MB and timed out the session browser
//          in the first fix9g run. Pages at or below 20,000 points are untouched.
//   fix9g-c the Stata-computed fit() line is capped by STRING LENGTH (a 100k-row fit was
//          ~150 KB in one javacall argument, which Stata split into ~5 KB chunks, shifting
//          every later argument -- the fit+ci export page showed 3 x 140-point chunks of
//          one line). Expect ~78 points per fit series on the 100k pages; Java now errors
//          if it receives more than 210 arguments.
//
// AFTER RUNNING (by eye, ~5 minutes -- the review side has no Stata)
//   Open r_9g_scatter_100k.html: the cloud should appear within ~2 s and hovering should
//   feel immediate (before fix9g each mouse move redrew 100,000 arcs: seconds of lag).
//   Hover a few points (tooltip shows Price/Mileage), move off (tooltip hides).
//   r_9g_scatter_filter: change the foreign dropdown -- the cloud rebuilds (about 1 s),
//   the obs count updates. r_9g_scatter_by_filter: same per panel.
//   Open the PNG / PDF / SVG exports: every one must contain the point cloud. Your machine
//   has sparkta-export.jar, so PNG/PDF go through the fast jsvg/PDFBox path: if the cloud is
//   MISSING there but present in the SVG, report it (jsvg must render the embedded image).
//   Zip test_out_fix9g/ (HTML + exports + _run.log) and share it; the review side runs
//     node verify/bigscatter_probe.js test_out_fix9g --out test_out_fix9g/_probe
//     node verify/byfilter_probe.js test_out_fix9g --embedded --glob 'r_9g_bf'
//
// Requires: Stata 17+, sparkta v3.6.0 fix9g installed (build.bat / build.sh, RESTART Stata
//           after the jar change), Edge or Chrome for saveas().
// Then run test_release_v360.do (jar changed): expect 210/210.
// =============================================================================
version 17
clear all
set more off

capture mkdir test_out_fix9g
local OUT "`c(pwd)'/test_out_fix9g"
local MAN "`OUT'/_manifest.csv"
discard
capture log close _sparkta_9g
log using "`OUT'/_run.log", replace text name(_sparkta_9g)
tempname mf
file open `mf' using "`MAN'", write replace
file write `mf' "case,rc,file_exists,note" _n
global SPK_OUT "`OUT'"
global SPK_MF  "`mf'"

capture program drop _chk
program _chk
    // usage: _chk casename rc [note]
    args name rc note
    local f "$SPK_OUT/`name'.html"
    local ex = fileexists("`f'")
    file write $SPK_MF `"`name',`rc',`ex',`note'"' _n
    if `rc' == 0 & `ex'      display as result "  PASS  `name'"
    else if `rc' == 0        display as error  "  FAIL  `name'  (rc=0 but no file)"
    else                     display as error  "  FAIL  `name'  rc=`rc'"
end

which sparkta      // header line 4 must say "fix9g"

// =============================================================================
// PART A -- 100,000-row synthetic scatter (seeded, so the review side can reproduce)
// =============================================================================
clear
set seed 20260913
set obs 100000
gen double x = rnormal()
gen double y = x + rnormal()
gen byte grp = 1 + floor(runiform() * 3)
gen byte flag = runiform() < 0.5
gen double size = 1 + floor(runiform() * 10)
label variable x "Mileage-like x"
label variable y "Price-like y"
label variable grp "Group"
label variable flag "Flag"
label variable size "Size"
label define flag 0 "Off" 1 "On"
label values flag flag

// EXPECT in the log for every case below: "large-data mode: 100,000 points drawn once ..."
capture noisily sparkta y x, type(scatter) title("9G scatter 100k") export("`OUT'/r_9g_scatter_100k.html")
_chk r_9g_scatter_100k `=_rc' "LOOK: opens fast, hover immediate, tooltip shows y/x"
capture noisily sparkta y x, type(scatter) over(grp) title("9G scatter 100k over(grp)") export("`OUT'/r_9g_scatter_over.html")
_chk r_9g_scatter_over `=_rc' "LOOK: three colours; legend click hides a group (cloud rebuilds)"
capture noisily sparkta y x, type(scatter) filters(flag) title("9G scatter 100k filters(flag)") export("`OUT'/r_9g_scatter_filter.html")
_chk r_9g_scatter_filter `=_rc' "LOOK: change the flag dropdown -- half the points, obs count updates"
capture noisily sparkta y x, type(scatter) fit(lfit) fitci title("9G scatter 100k fit(lfit) fitci") export("`OUT'/r_9g_scatter_fit.html")
_chk r_9g_scatter_fit `=_rc' "LOOK: fit line and CI band UNDER the points; the line spans the WHOLE x range (fix9g-c)"
capture noisily sparkta y x size, type(bubble) title("9G bubble 100k") export("`OUT'/r_9g_bubble_100k.html")
_chk r_9g_bubble_100k `=_rc' "LOOK: bubbles sized; hover immediate"
capture noisily sparkta y x, type(scatter) by(flag) title("9G scatter 100k by(flag)") export("`OUT'/r_9g_scatter_by.html")
_chk r_9g_scatter_by `=_rc' "EXPECT note says 'points in a panel'; two panels of ~50k"
capture noisily sparkta y x, type(scatter) by(flag) filters(grp) title("9G scatter 100k by+filter") export("`OUT'/r_9g_bf_scatter_by_filter.html")
_chk r_9g_bf_scatter_by_filter `=_rc' "LOOK: each panel follows the grp filter"

// =============================================================================
// PART B -- exports must contain the cloud (fix9g-b: one raster image; the rest vector)
// =============================================================================
capture noisily sparkta y x, type(scatter) fit(lfit) fitci title("9G scatter 100k exports") export("`OUT'/r_9g_scatter_exports.html") saveas(`OUT'/r_9g_scatter_exports.png `OUT'/r_9g_scatter_exports.pdf `OUT'/r_9g_scatter_exports.svg)
_chk r_9g_scatter_exports `=_rc' "LOOK: PNG, PDF and SVG all show the cloud + fit + band; SVG ~1-2 MB with one <image> for the cloud"
capture noisily sparkta y x, type(scatter) by(flag) title("9G scatter 100k by exports") export("`OUT'/r_9g_scatter_by_exports.html") saveas(`OUT'/r_9g_scatter_by_exports.png `OUT'/r_9g_scatter_by_exports.pdf, close)
_chk r_9g_scatter_by_exports `=_rc' "LOOK: both panels in PNG and PDF; close = session browser quits"

// =============================================================================
// PART C -- threshold: at 20,000 points normal mode, at 20,001 large-data mode
// =============================================================================
preserve
keep in 1/20000
capture noisily sparkta y x, type(scatter) title("9G scatter 20000 (normal mode)") export("`OUT'/r_9g_scatter_20000.html")
_chk r_9g_scatter_20000 `=_rc' "EXPECT NO large-data note (animation on)"
restore
preserve
keep in 1/20001
capture noisily sparkta y x, type(scatter) title("9G scatter 20001 (large-data mode)") export("`OUT'/r_9g_scatter_20001.html")
_chk r_9g_scatter_20001 `=_rc' "EXPECT the large-data note: 20,001 points"
restore

// =============================================================================
// PART D -- regression: small data unchanged
// =============================================================================
sysuse auto, clear
capture noisily sparkta price mpg, type(scatter) over(foreign) fit(lfit) fitci export("`OUT'/r_9g_auto_scatter.html")
_chk r_9g_auto_scatter `=_rc' "no note; animated as before"
capture noisily sparkta price mpg weight, type(bubble) filters(rep78) export("`OUT'/r_9g_auto_bubble.html")
_chk r_9g_auto_bubble `=_rc' "no note; filter works as before"

// ---- wrap up -----------------------------------------------------------------
file close `mf'
display as text _n "Manifest: `MAN'"
display as text "Zip test_out_fix9g/ and share it (exports included)."
log close _sparkta_9g
