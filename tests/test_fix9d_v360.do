// =============================================================================
// test_fix9d_v360.do -- Sparkta v3.6.0-t2j fix9c + fix9d smoke test (2026-09-13)
// =============================================================================
// Version: 1.0 (fix9d). ASCII only.
//
// WHAT IT COVERS (the two pre-tag builds since fix9b)
//   fix9c  saveas() chart scope: no publication table as PDF page 2 on post-estimation
//          pages; no stray "Export:" label on any PNG/PDF.
//   fix9d  (5a) saveas() PNG is a FULL-PAGE capture through the session browser;
//          (2a) violin filtering refreshes EVERY violin (group x variable);
//          (1b) note for stackedbar100 without over();
//          (4a) note for by()/filters()/sliders() on a post-estimation type;
//          (6)  sparkta_shell_quiet.ado is gone from the package.
//
// AFTER RUNNING
//   Zip test_out_fix9d/ and share it. Review side (no Stata) runs:
//     node verify/violin_single_check.js test_out_fix9d/r_9d_violin_2var*.html
//     node verify/byfilter_probe.js test_out_fix9d --embedded --glob 'r_9d_bf'
//   and inspects the PNG/PDF files (page count, "Export:" text, PNG height).
//   By eye (2 minutes): open r_9d_es_chart.png -- the whole figure INCLUDING the
//   elements key line under the plot ("95% CI ... Hollow marker") must be there;
//   r_9d_box_by.png must show all rep78 panels; r_9d_es_chart.pdf must be ONE page.
//
// Requires: Stata 17+, sparkta v3.6.0-t2j fix9d installed (build.bat / build.sh, RESTART
//           Stata after a jar change), Edge or Chrome for saveas(), sysuse auto.
// Then run test_release_v360.do (jar and ado changed): expect 210/210.
// =============================================================================
version 17
clear all
set more off

capture mkdir test_out_fix9d
local OUT "`c(pwd)'/test_out_fix9d"
local MAN "`OUT'/_manifest.csv"
discard
capture log close _sparkta_9d
log using "`OUT'/_run.log", replace text name(_sparkta_9d)
tempname mf
file open `mf' using "`MAN'", write replace
file write `mf' "case,rc,file_exists,note" _n
global SPK_OUT "`OUT'"
global SPK_MF  "`mf'"

capture program drop _chk
program _chk
    // usage: _chk casename rc [note]   -- r_9d_err_* cases are expected to FAIL (rc != 0)
    args name rc note
    local f "$SPK_OUT/`name'.html"
    local ex = fileexists("`f'")
    file write $SPK_MF `"`name',`rc',`ex',`note'"' _n
    local expfail = (substr("`name'", 1, 9) == "r_9d_err_")
    if `expfail' & `rc' != 0     display as result "  PASS  `name'  (expected failure, rc=`rc')"
    else if `expfail'             display as error  "  FAIL  `name'  (should have failed but rc=0)"
    else if `rc' == 0 & `ex'     display as result "  PASS  `name'"
    else if `rc' == 0            display as error  "  FAIL  `name'  (rc=0 but no file)"
    else                          display as error  "  FAIL  `name'  rc=`rc'"
end

which sparkta      // header must start "v3.6.0-t2j fix9d"
// (6) the dead component must be gone from a clean install; on an in-place build.bat
//     install an old copy may linger in PERSONAL -- that is fine, nothing calls it
capture which sparkta_shell_quiet
display as text "  sparkta_shell_quiet on the adopath: " cond(_rc == 0, "still present (old copy in PERSONAL? harmless)", "gone (expected on a clean install)")

sysuse auto, clear

// =============================================================================
// PART A -- (2a) violin filtering, every violin must refresh
// =============================================================================
// LOOK: pick rep78 = 3 in the dropdown -- BOTH violins (Price and Mileage) must reshape,
//       not only Price; the obs count changes to 30
capture noisily sparkta price mpg, type(violin) filters(rep78) title("9D violin 2 vars + filter") export("`OUT'/r_9d_violin_2var_filter.html")
_chk r_9d_violin_2var_filter `=_rc' "LOOK: both violins reshape on any rep78 value"
// LOOK: 4 violins (Price/Mileage x Domestic/Foreign); pick rep78 = 5 -> Domestic pair empty, Foreign pair reshapes
capture noisily sparkta price mpg, type(violin) over(foreign) filters(rep78) title("9D violin 2 vars over + filter") export("`OUT'/r_9d_violin_2var_over_filter.html")
_chk r_9d_violin_2var_over_filter `=_rc' "LOOK: all four violins follow the filter"
// by() panels, two variables (the BF40 case) and two variables with over() (BF54)
capture noisily sparkta price mpg, type(violin) by(foreign) filters(rep78) export("`OUT'/r_9d_bf_violin_2var_by.html")
_chk r_9d_bf_violin_2var_by `=_rc' "LOOK: each panel keeps two violins after any filter value"
capture noisily sparkta price mpg, type(violin) over(rep78) by(foreign) filters(mpg) export("`OUT'/r_9d_bf_violin_2var_over_by.html")
_chk r_9d_bf_violin_2var_over_by `=_rc' "LOOK: every group x variable violin follows the mpg filter"
capture noisily sparkta price, type(hviolinplot) over(foreign) filters(rep78) export("`OUT'/r_9d_hviolin_over_filter.html")
_chk r_9d_hviolin_over_filter `=_rc' "regression: single-variable horizontal violin still filters"

// =============================================================================
// PART B -- (1b) and (4a) notes (rc must stay 0; READ the note in the log)
// =============================================================================
capture noisily sparkta price mpg, type(stackedbar100) export("`OUT'/r_9d_note_stack100_noover.html")
_chk r_9d_note_stack100_noover `=_rc' "EXPECT note: stackedbar100 without over() draws plain bars"
capture noisily sparkta price mpg, type(stackedbar100) over(foreign) export("`OUT'/r_9d_stack100_over.html")
_chk r_9d_stack100_over `=_rc' "no note; a real 100% stack"
regress price mpg weight foreign
capture noisily sparkta, type(coefplot) by(foreign) filters(rep78) sliders(mpg) export("`OUT'/r_9d_note_coefplot_by.html")
_chk r_9d_note_coefplot_by `=_rc' "EXPECT note: by() filters() sliders() ignored -- plain coefplot, rc 0"
capture noisily sparkta, type(coefplot) export("`OUT'/r_9d_coefplot_plain.html")
_chk r_9d_coefplot_plain `=_rc' "no note"

// =============================================================================
// PART C -- (5a) + fix9c: saveas() through the session browser
// =============================================================================
// Watch the log: "saveas: png via session browser, full page at 2.0x" is the new route;
// "using a one-off headless screenshot (viewport only)" means the fallback ran -- report it.
regress price mpg weight foreign
capture noisily sparkta, type(coefplot) export("`OUT'/r_9d_es_chart.html") saveas(`OUT'/r_9d_es_chart.pdf `OUT'/r_9d_es_chart.png)
_chk r_9d_es_chart `=_rc' "PDF must be ONE page (no table page); PNG full height incl. the elements key; no 'Export:' text"
capture noisily sparkta, type(coefplot) export("`OUT'/r_9d_es_table.html") saveas(`OUT'/r_9d_es_table.pdf, table)
_chk r_9d_es_table `=_rc' "table scope unchanged: the coefficient table only"
capture noisily sparkta, type(coefplot) export("`OUT'/r_9d_es_page3x.html") saveas(`OUT'/r_9d_es_page3x.png, page scale(3))
_chk r_9d_es_page3x `=_rc' "page scope at 3x: 3300 px wide, chart + table"
capture noisily sparkta price mpg, type(boxplot) over(foreign) by(rep78) export("`OUT'/r_9d_box_by.html") saveas(`OUT'/r_9d_box_by.png `OUT'/r_9d_box_by.pdf)
_chk r_9d_box_by `=_rc' "PNG shows EVERY rep78 panel (was cropped to the first rows)"
capture noisily sparkta price, type(bar) over(rep78) export("`OUT'/r_9d_bar.html") saveas(`OUT'/r_9d_bar.png `OUT'/r_9d_bar.svg `OUT'/r_9d_bar.pdf, close)
_chk r_9d_bar `=_rc' "plain chart, all three formats; close = session browser quits"

// ---- wrap up -----------------------------------------------------------------
file close `mf'
display as text _n "Manifest: `MAN'"
display as text "Zip test_out_fix9d/ and share it (PNG/PDF included)."
log close _sparkta_9d
