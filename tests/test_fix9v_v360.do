// =============================================================================
// test_fix9v_v360.do -- Sparkta v3.6.0-t2j fix9u + fix9v smoke test (2026-09-16)
// =============================================================================
// Version: 1.1 (fix9v: PART E, the browser-tree close). ASCII only.
//
// WHAT IT COVERS (pre-push worklist decisions, all Java + two ado display changes)
//   decision 5  hollow markers (p > 0.1) are TRANSPARENT (Stata msymbol(Oh)): the SVG export
//               of an event study / coefplot carries fill-opacity="0" on the hollow rings.
//   decision 6  export clones are drawn ONCE: an SVG of an area chart holds every path once
//               (translucent fills are no longer darker in SVG/PDF than on screen).
//   decision 7  the full-page PNG has no "Made with sparkta" footer.
//   item 10     Stata 15-16 heap lines gone from the large-data note (display only).
//   fix9v       the session browser is gone 15 s after the last export: `procs` says
//               "tracked pids N (0 alive)" and "last close: idle ..., still alive after 0"; NO
//               msedge.exe/chrome.exe line carries the SESSION marker; Alt+Tab shows nothing new.
//
// The SVG checks read the exported file back with a Mata byte scan (no plugin needed):
//   fill-opacity="0"  present  -> hollow rings are transparent (decision 5)
//   <path count       counted  -> printed for the review side; fix9t emitted exactly twice as
//                                 many paths as fix9u for the same page
//
// AFTER RUNNING
//   Zip test_out_fix9v/ and share it. By eye (2 minutes):
//     r_9v_es.png        hollow pre-treatment markers are RINGS with the grid visible inside
//     r_9v_area.svg      open in a browser: the area fill is as light as on screen
//     r_9v_page.png      full-page capture, NO "Made with sparkta v3.6.0 ..." line at the bottom
//   Alt+Tab 10 s after the last export: no sparkta browser window (the chart pages this
//   script exports are NOT opened in a browser; only export()-less calls open a tab).
//
// Requires: Stata 17+, sparkta v3.6.0-t2j fix9v installed (build.bat / build.sh, RESTART
//           Stata after a jar change), Edge or Chrome for saveas().
// Then run test_release_v360.do (jar and ado changed): expect 210/210.
// =============================================================================
version 17
clear all
set more off

capture mkdir test_out_fix9v
local OUT "`c(pwd)'/test_out_fix9v"
local MAN "`OUT'/_manifest.csv"
discard
capture log close _sparkta_9v
log using "`OUT'/_run.log", replace text name(_sparkta_9v)
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
    if `rc' == 0 & `ex'     display as result "  PASS  `name'"
    else if `rc' == 0       display as error  "  FAIL  `name'  (rc=0 but no file)"
    else                    display as error  "  FAIL  `name'  rc=`rc'"
end

capture program drop _scan
program _scan, rclass
    // usage: _scan file needle  -> r(n) = number of occurrences of needle in the file.
    // Done in Mata: an SVG is one very long line and a Stata string expression would truncate.
    args f needle
    mata: _spk_scan("`f'", `"`needle'"')
    return scalar n = `_n'
end
mata:
mata clear
void _spk_scan(string scalar f, string scalar needle)
{
    string colvector L
    string scalar   s
    real scalar     i
    L = cat(f)
    s = ""
    for (i = 1; i <= rows(L); i++) s = s + L[i]
    st_local("_n", strofreal((strlen(s) - strlen(subinstr(s, needle, "", .))) / strlen(needle)))
}
end

which sparkta      // header line 4 must say "fix9v"

// -----------------------------------------------------------------------------
// PART A -- decision 5: transparent hollow markers (event study + coefplot)
// -----------------------------------------------------------------------------
clear
set seed 9016
set obs 4000
gen id   = ceil(_n / 20)
bysort id: gen t = _n
gen treated = (id <= 100)
gen ev = t - 11
gen y = 2 + 0.05 * t + rnormal(0, 1)
forvalues k = 2/5 {
    gen lead`k' = treated * (ev == -`k')
}
forvalues k = 0/5 {
    gen lag`k'  = treated * (ev == `k')
    replace y = y + 0.15 * `k' * lag`k'
}
quietly regress y lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5 i.t i.id
capture noisily sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    cistyle(band) pexline(0) title("event study, fix9u") export("`OUT'/r_9v_es.html") ///
    saveas(`OUT'/r_9v_es.svg `OUT'/r_9v_es.png)
_chk r_9v_es `=_rc' "hollow pre-treatment markers = transparent rings"
capture noisily _scan "`OUT'/r_9v_es.svg" `"fill-opacity="0""'
if _rc == 0 & r(n) > 0 display as result "  PASS  r_9v_es.svg has `r(n)' transparent (hollow) elements"
else display as error "  FAIL  r_9v_es.svg has no fill-opacity=0 element (hollow rings not transparent?)"

sysuse auto, clear
quietly regress price mpg weight foreign turn
capture noisily sparkta, type(coefplot) export("`OUT'/r_9v_cp.html") saveas(`OUT'/r_9v_cp.svg)
_chk r_9v_cp `=_rc' "coefplot hollow markers (p > 0.1 coefficients) transparent"
capture noisily _scan "`OUT'/r_9v_cp.svg" `"fill-opacity="0""'
if _rc == 0 display as text "  info  r_9v_cp.svg transparent elements: `r(n)' (0 is fine if every p <= 0.1)"

// -----------------------------------------------------------------------------
// PART B -- decision 6: export drawn once (path count for the review side)
// -----------------------------------------------------------------------------
sysuse auto, clear
capture noisily sparkta price mpg, type(area) over(rep78) export("`OUT'/r_9v_area.html") ///
    saveas(`OUT'/r_9v_area.svg `OUT'/r_9v_area.pdf)
_chk r_9v_area `=_rc' "area chart SVG/PDF: translucent fill as light as on screen"
capture noisily _scan "`OUT'/r_9v_area.svg" "<path"
if _rc == 0 display as text "  info  r_9v_area.svg <path> count: `r(n)' (fix9t wrote twice this number)"

capture noisily sparkta price mpg, type(cibar) over(rep78) export("`OUT'/r_9v_cibar.html") saveas(`OUT'/r_9v_cibar.svg)
_chk r_9v_cibar `=_rc' "cibar SVG drawn once"
capture noisily _scan "`OUT'/r_9v_cibar.svg" "<path"
if _rc == 0 display as text "  info  r_9v_cibar.svg <path> count: `r(n)'"

// -----------------------------------------------------------------------------
// PART C -- decision 7: full-page PNG without the build-stamp footer
// -----------------------------------------------------------------------------
capture noisily sparkta price, type(bar) over(rep78) export("`OUT'/r_9v_page.html") ///
    saveas(`OUT'/r_9v_page.png, page close)
_chk r_9v_page `=_rc' "full-page PNG: no Made-with-sparkta footer (by eye)"

// -----------------------------------------------------------------------------
// PART D -- item 10: the large-data note (display only; scroll the log for it)
// -----------------------------------------------------------------------------
clear
set obs 120000
gen x = rnormal()
gen y = x + rnormal()
capture noisily sparkta y x, type(scatter) export("`OUT'/r_9v_big.html")
_chk r_9v_big `=_rc' "large-data note must not mention Stata 15-16"

// -----------------------------------------------------------------------------
// PART E -- fix9v: the session browser tree is closed 10 s after the last export
// -----------------------------------------------------------------------------
// r_9v_page above ended with saveas(..., close) so the browser is already gone; start a new
// session browser with a plain saveas(), wait 15 s, and list the browser processes.
sysuse auto, clear
capture noisily sparkta price, type(bar) over(foreign) export("`OUT'/r_9v_close.html") saveas(`OUT'/r_9v_close.svg)
_chk r_9v_close `=_rc' "session browser started; must be gone by itself within 10 s"
display as text "  waiting 15 s for the idle close ..."
sleep 15000
capture findfile sparkta.jar
capture noisily javacall com.dashboard_test.tools.HeadlessBrowser execute, classpath("`r(fn)'") args("procs")
display as text "  PASS rule: 'tracked pids N (0 alive)', 'still alive after 0', no SESSION line, Alt+Tab unchanged"

file close `mf'
display as text ""
display as text "Manifest: `MAN'"
display as text "Zip test_out_fix9v/ and share it. Then: test_release_v360.do (expect 210/210)."
log close _sparkta_9v
