// =============================================================================
// test_release_v360.do -- Sparkta v3.6.0-t2g release regression suite
// =============================================================================
// Runs every chart type and every post-estimation pathway, writes one HTML per
// case into ./test_out/, and never stops on an error: each case is wrapped in
// capture noisily and its return code is logged to test_out/_manifest.csv.
//
// AFTER RUNNING:  zip the test_out/ folder and share it. Then on the review side:
//     node verify/js_check.js test_out verify/expectations.json
// which executes every chart script, every plugin hook and every tooltip
// callback under a mock Chart.js, and asserts the per-file expectations.
//
// Writes test_out/_run.log (full session log) for review.
// Requires: Stata 17+, sparkta v3.6.0-t2d installed (build.bat), internet for
//           CDN charts (r_offline_* cases need fetch_js_libs to have been run).
// Datasets: sysuse auto, sysuse nlsw88, webuse-free synthetic panel for the
//           event study (generated inline, seeded).
// Naming:   r_<area>_<case>.html   area = base | opt | pe (post-est) | mp (margins)
// ASCII only.
// =============================================================================
version 17
clear all
set more off
set seed 20260905

// ---- output folder + manifest ------------------------------------------------
capture mkdir test_out
local OUT "`c(pwd)'/test_out"
local MAN "`OUT'/_manifest.csv"
discard   // s9q: drop any previously loaded sparkta so the freshly built ado (and its subroutines) load
capture log close _sparkta_suite
log using "`OUT'/_run.log", replace text name(_sparkta_suite)
tempname mf
file open `mf' using "`MAN'", write replace
file write `mf' "case,rc,file_exists,note" _n

capture program drop _chk
program _chk
    // usage: _chk casename rc [note]
    args name rc note
    local f "$SPK_OUT/`name'.html"
    local ex = fileexists("`f'")
    file write $SPK_MF `"`name',`rc',`ex',`note'"' _n
    local expfail = (substr("`name'", 1, 6) == "r_err_")
    if `expfail' & `rc' != 0     display as result "  PASS  `name'  (expected failure, rc=`rc')"
    else if `expfail'             display as error  "  FAIL  `name'  (should have failed but rc=0)"
    else if `rc' == 0 & `ex'     display as result "  PASS  `name'"
    else if `rc' == 0            display as error  "  FAIL  `name'  (rc=0 but no file)"
    else                          display as error  "  FAIL  `name'  rc=`rc'"
end

// t2f fix 6 -- NUMERIC FIDELITY: r(table) does NOT survive the sparkta call (run 46: sparkta runs
// r-class commands internally), so Stata's truth is written BEFORE each post-estimation call:
//   _truth_pre <case>   -> _truth/<case>.csv   (verify/fidelity_check.py compares the page to it)
capture program drop _truth_pre
program _truth_pre
    // usage: _truth_pre <case> [stored estimate names]  -- with names, the truth is the union of
    // each stored model's r(table) (estimates restore + ereturn display re-posts it)
    gettoken name ests : 0
    tempname _T
    if "`ests'" != "" {
        tempname _U
        local _first 1
        foreach _e of local ests {
            capture quietly estimates restore `_e'
            if _rc != 0 continue
            capture quietly ereturn display
            capture confirm matrix r(table)
            if _rc != 0 continue
            matrix `_U' = r(table)
            if `_first' matrix `_T' = `_U'
            else matrix `_T' = `_T', `_U'
            local _first 0
        }
        if !`_first' {
            capture mkdir "$SPK_OUT/_truth"
            capture sparkta_truth_csv `_T' "$SPK_OUT/_truth/`name'.csv"
        }
        exit
    }
    // pwcompare/contrast pages plot r(table_vs) (the contrasts), not the margins in r(table)
    capture confirm matrix r(table_vs)
    if _rc == 0 matrix `_T' = r(table_vs)
    else {
        capture confirm matrix r(table)
        if _rc != 0 exit
        matrix `_T' = r(table)
    }
    capture mkdir "$SPK_OUT/_truth"
    capture sparkta_truth_csv `_T' "$SPK_OUT/_truth/`name'.csv"
end

// t2g -- SIDE-BY-SIDE: Stata's own graph for a case, exported to _stata/<case>.png, so the review
// can paste it next to sparkta's render (verify/sidebyside.py). marginsplot is built in;
// coefplot only when Jann's package is installed.
capture program drop _stata_graph
program _stata_graph
    args name kind
    capture mkdir "$SPK_OUT/_stata"
    if "`kind'" == "marginsplot" capture quietly marginsplot, name(_spk_g, replace)
    else if "`kind'" == "coefplot" {
        capture which coefplot
        if _rc != 0 exit
        capture quietly coefplot, drop(_cons) xline(0) name(_spk_g, replace)
    }
    else if "`kind'" == "coefplot_vertical" {
        capture which coefplot
        if _rc != 0 exit
        capture quietly coefplot, drop(_cons) vertical yline(0) name(_spk_g, replace)
    }
    else exit
    if _rc == 0 capture quietly graph export "$SPK_OUT/_stata/`name'.png", name(_spk_g) width(800) replace
    capture graph drop _spk_g
end

// writes a Stata matrix with its row/column names as CSV (Mata; ASCII only)
capture program drop sparkta_truth_csv
program sparkta_truth_csv
    args M fn
    mata: _spk_truth_csv("`M'", "`fn'")
end
mata:
void _spk_truth_csv(string scalar M, string scalar fn)
{
    real matrix X
    string matrix rn, cn
    string scalar line
    real scalar fh, i, j
    X  = st_matrix(M)
    rn = st_matrixrowstripe(M)
    cn = st_matrixcolstripe(M)
    unlink(fn)
    fh = fopen(fn, "w")
    fput(fh, "row," + invtokens(cn[., 2]', ","))
    for (i = 1; i <= rows(X); i++) {
        line = rn[i, 2]
        for (j = 1; j <= cols(X); j++) line = line + "," + strofreal(X[i, j], "%18.0g")
        fput(fh, line)
    }
    fclose(fh)
}
end


// --- Section H helper (v3.6.0-t2d): DiD event-study matrices from csdid2 / jwdid / lwdid ----
// Runs only when csdid is installed. Uses the mpdta example data shipped with csdid.
capture program drop sparkta_matrix_did_cases
program sparkta_matrix_did_cases
    args OUT
    preserve
    // t2b: no download -- simulate a staggered-adoption panel (500 counties, 2003-2007,
    // cohorts 2004/2006/2007 + never treated, dynamic effect -0.03 per year since treatment)
    clear
    set seed 20260907
    set obs 500
    generate long countyreal = _n
    generate int first_treat = cond(_n <= 125, 2004, cond(_n <= 250, 2006, cond(_n <= 375, 2007, 0)))
    generate double ui = rnormal(0, 0.3)
    expand 5
    bysort countyreal: generate int year = 2002 + _n
    generate double rel = cond(first_treat > 0, year - first_treat, .)
    generate double lemp = 5.8 + ui + 0.02 * (year - 2003) + cond(rel < . & rel >= 0, -0.03 * (rel + 1), 0) + rnormal(0, 0.1)
    generate double lpop = 4 + rnormal(0, 0.5)
    drop ui rel
    // csdid2 / csdid: estat event -> r(table)
    capture csdid lemp, ivar(countyreal) time(year) gvar(first_treat) method(dripw)
    if _rc == 0 {
        capture estat event
        if _rc == 0 {
            _truth_pre r_mat_csdid_event
            capture noisily sparkta, type(eventstudy) matrix(r(table)) export("`OUT'/r_mat_csdid_event.html")
            _chk r_mat_csdid_event `=_rc' "csdid estat event -> r(table) -> eventstudy (names pinned from the log)"
        }
    }
    else display as text "  (csdid estimation failed on mpdta: rc=`=_rc')"
    // jwdid: estat event -> r(table)
    capture which jwdid
    if _rc == 0 {
        capture which hdfe
        if _rc != 0 display as text "  (jwdid needs the hdfe package -- ssc install hdfe -- jwdid case skipped)"
    }
    if _rc == 0 {
        capture noisily jwdid lemp, ivar(countyreal) tvar(year) gvar(first_treat) never
        if _rc == 0 {
            capture estat event
            if _rc == 0 {
                _truth_pre r_mat_jwdid_event
                capture noisily sparkta, type(eventstudy) matrix(r(table)) export("`OUT'/r_mat_jwdid_event.html")
                _chk r_mat_jwdid_event `=_rc' "jwdid estat event -> r(table) -> eventstudy"
            }
        }
        else display as text "  (jwdid estimation failed on mpdta: rc=`=_rc')"
    }
    // lwdid: same family
    capture which lwdid
    if _rc == 0 {
        // lwdid leaves no results matrix: its event table goes to a DATASET via save() (ryear, watt,
        // se, t_stat, p_value, low_ci, up_ci, ...). Build a matrix from it: rows Tm#/Tp# (our parser),
        // columns watt se low_ci up_ci p_value -- autodetected since t2f fix 2 (watt, low_ci, up_ci).
        // The -1 row is missing in the file, so the reference gap appears at -1 automatically.
        tempfile _lw
        capture noisily lwdid lemp, ivar(countyreal) tvar(year) gvar(first_treat) rolling(demean) method(ra) save(`_lw')
        if _rc == 0 {
            // (the helper already holds a preserve; lwdid is its last case, so the data may be replaced here)
            capture noisily use `"`_lw'"', clear
            if _rc == 0 & _N > 0 {
                // t2f fix 4: results() does the mkmat internally (autodetect: ryear watt se low_ci up_ci p_value)
                capture noisily sparkta, type(eventstudy) results(`_lw') export("`OUT'/r_mat_lwdid_event.html")
                _chk r_mat_lwdid_event `=_rc' "lwdid save() dataset -> results() -> eventstudy (explicit zero row = reference)"
            }
            else display as text "  (lwdid save() file could not be read -- case skipped)"
        }
        else display as text "  (lwdid estimation failed on mpdta: rc=`=_rc')"
    }
    restore
end
global SPK_OUT "`OUT'"
global SPK_MF  `mf'
display as text _newline "sparkta release suite -> `OUT'" _newline

// =============================================================================
// SECTION A: base chart types (auto / nlsw88)
// =============================================================================
sysuse auto, clear

capture noisily sparkta price mpg, type(bar) over(foreign) title("bar over") export("`OUT'/r_base_bar_over.html")
_chk r_base_bar_over `=_rc'

capture noisily sparkta price, type(hbar) over(rep78) relabel(One|Two|Three|Four|Five) export("`OUT'/r_base_hbar_relabel.html")
_chk r_base_hbar_relabel `=_rc'

capture noisily sparkta price mpg weight, type(stackedbar) over(foreign) export("`OUT'/r_base_stackedbar.html")
_chk r_base_stackedbar `=_rc'

capture noisily sparkta price mpg, type(stackedbar100) over(rep78) export("`OUT'/r_base_stackedbar100.html")
_chk r_base_stackedbar100 `=_rc'

capture noisily sparkta price, type(line) over(rep78) by(foreign) export("`OUT'/r_base_line_over_by.html")
_chk r_base_line_over_by `=_rc'

capture noisily sparkta price mpg, type(area) over(rep78) export("`OUT'/r_base_area.html")
_chk r_base_area `=_rc'

capture noisily sparkta weight price, type(scatter) over(foreign) fit(lfit) fitci export("`OUT'/r_base_scatter_fit.html")
_chk r_base_scatter_fit `=_rc'

capture noisily sparkta weight price mpg, type(bubble) export("`OUT'/r_base_bubble.html")
_chk r_base_bubble `=_rc'

capture noisily sparkta, type(pie) over(rep78) export("`OUT'/r_base_pie.html")
_chk r_base_pie `=_rc'

capture noisily sparkta price, type(donut) over(foreign) export("`OUT'/r_base_donut.html")
_chk r_base_donut `=_rc'

capture noisily sparkta price, type(cibar) over(rep78) cilevel(90) export("`OUT'/r_base_cibar.html")
_chk r_base_cibar `=_rc'

capture noisily sparkta mpg, type(ciline) over(rep78) export("`OUT'/r_base_ciline.html")
_chk r_base_ciline `=_rc'

capture noisily sparkta price, type(histogram) bins(12) export("`OUT'/r_base_histogram.html")
_chk r_base_histogram `=_rc'

capture noisily sparkta price, type(boxplot) over(rep78) whiskerfence(1.5) export("`OUT'/r_base_boxplot.html")
_chk r_base_boxplot `=_rc'

capture noisily sparkta mpg, type(hbox) over(foreign) export("`OUT'/r_base_hbox.html")
_chk r_base_hbox `=_rc'

capture noisily sparkta price, type(violin) over(foreign) export("`OUT'/r_base_violin.html")
_chk r_base_violin `=_rc'

// filters, themes, annotations, styling
capture noisily sparkta price mpg, type(bar) over(foreign) filter(rep78) export("`OUT'/r_opt_filter.html")
_chk r_opt_filter `=_rc'

capture noisily sparkta price, type(bar) over(rep78) theme(dark_viridis) export("`OUT'/r_opt_theme_dark.html")
_chk r_opt_theme_dark `=_rc'

capture noisily sparkta weight price, type(scatter) yline(6000) xline(3000) yband(4000 8000) ///
    ylinelabel("Mean price") apoint(6000 3000) alabeltext("Ref") export("`OUT'/r_opt_annotations.html")
_chk r_opt_annotations `=_rc'

capture noisily sparkta price mpg, type(line) over(rep78) lpatterns(solid|dash) nopoints ///
    leglabels(Price|MPG) yreverse noticks download export("`OUT'/r_opt_lines_legend.html")
_chk r_opt_lines_legend `=_rc'

capture noisily sparkta price, type(bar) over(rep78) gradient titlesize(20) titlecolor(navy) ///
    tooltipbg(#222222) xtickangle(45) export("`OUT'/r_opt_styling.html")
_chk r_opt_styling `=_rc'

capture noisily sparkta price, type(bar) over(foreign) nostats notimestamp export("`OUT'/r_opt_nostats.html")
_chk r_opt_nostats `=_rc'

// palette() via Ben Jann's colorpalette (optional dependency; skipped if absent)
capture which colorpalette
if _rc == 0 {
    capture noisily sparkta price, type(bar) over(rep78) palette(okabe) export("`OUT'/r_opt_palette_okabe.html")
    _chk r_opt_palette_okabe `=_rc' "colorpalette okabe -> 5 Okabe-Ito bars"
    capture noisily sparkta price mpg, type(line) over(rep78) palette(viridis, n(2) reverse) export("`OUT'/r_opt_palette_viridis.html")
    _chk r_opt_palette_viridis `=_rc' "colorpalette viridis n(2) reverse"
    quietly regress price mpg weight foreign
    capture noisily sparkta, type(coefplot) palette(tableau) cistyle(band) export("`OUT'/r_opt_palette_coefplot.html")
    _chk r_opt_palette_coefplot `=_rc' "palette() on post-estimation chart"
    capture noisily sparkta price, type(bar) over(rep78) palette(okabe) colors(navy maroon) export("`OUT'/r_err_palette_and_colors.html")
    _chk r_err_palette_and_colors `=_rc' "EXPECT rc!=0: palette() with colors()"
}
else display as text "  (colorpalette not installed -- palette() cases skipped; ssc install colorpalette)"

// string over() var + large N
sysuse nlsw88, clear
capture noisily sparkta wage, type(bar) over(race) by(married) collapsestats export("`OUT'/r_base_nlsw_over_by.html")
_chk r_base_nlsw_over_by `=_rc'

capture noisily sparkta wage hours, type(scatter) over(union) export("`OUT'/r_base_nlsw_scatter.html")
_chk r_base_nlsw_scatter `=_rc'

// offline (only meaningful if fetch_js_libs was run; failure is informative)
sysuse auto, clear
capture noisily sparkta price, type(bar) over(foreign) offline export("`OUT'/r_opt_offline.html")
_chk r_opt_offline `=_rc' "needs bundled JS libs"

// =============================================================================
// SECTION B: coefplot / eventstudy
// =============================================================================
sysuse auto, clear
quietly regress price mpg weight foreign
_truth_pre r_pe_cp_whisker
_stata_graph r_pe_cp_whisker coefplot
capture noisily sparkta, type(coefplot) title("coefplot whisker") export("`OUT'/r_pe_cp_whisker.html")
_chk r_pe_cp_whisker `=_rc'

_truth_pre r_pe_cp_band
capture noisily sparkta, type(coefplot) cistyle(band) connected export("`OUT'/r_pe_cp_band.html")
_chk r_pe_cp_band `=_rc'

_truth_pre r_pe_cp_area_bar
capture noisily sparkta, type(coefplot) cistyle(area) coefstyle(bar) export("`OUT'/r_pe_cp_area_bar.html")
_chk r_pe_cp_area_bar `=_rc'

_truth_pre r_pe_cp_levels
capture noisily sparkta, type(coefplot) levels(90 95) pexline(0) export("`OUT'/r_pe_cp_levels.html")
_chk r_pe_cp_levels `=_rc'

_truth_pre r_pe_cp_sort
capture noisily sparkta, type(coefplot) coefsort(desc) refval(none) ciwidth(2.5) cicolors(maroon) export("`OUT'/r_pe_cp_sort.html")
_chk r_pe_cp_sort `=_rc'

_truth_pre r_pe_cp_labels_omit
capture noisily sparkta, type(coefplot) coeflabels("mpg/Miles per gallon" "weight/Weight in lbs") omit(_cons) export("`OUT'/r_pe_cp_labels_omit.html")
_chk r_pe_cp_labels_omit `=_rc'

_truth_pre r_pe_cp_show_coeflbl
capture noisily sparkta, type(coefplot) show(mpg weight) coeflbl(mpg "MPG override") export("`OUT'/r_pe_cp_show_coeflbl.html")
_chk r_pe_cp_show_coeflbl `=_rc'

_truth_pre r_pe_cp_dark_levels
capture noisily sparkta, type(coefplot) theme(dark) levels(90 95) cistyle(band) export("`OUT'/r_pe_cp_dark_levels.html")
_chk r_pe_cp_dark_levels `=_rc'

// v3.6.0-t2d: Stata-convention aliases must produce the SAME output as the sparkta names
quietly regress price mpg weight foreign
_truth_pre r_pe_cp_stata_aliases
capture noisily sparkta, type(coefplot) level(90) keep(mpg weight) sort(asc) vertical recastci(rcap) msymbol(D) msize(large) lwidth(thick) ///
    coeflabels(mpg = "Miles per gallon") export("`OUT'/r_pe_cp_stata_aliases.html")
_chk r_pe_cp_stata_aliases `=_rc' "level/keep/sort/vertical/recastci/msymbol/msize/lwidth/coeflabels(=)"

quietly regress price i.rep78 foreign weight
quietly margins rep78
_truth_pre r_mp_t25_stata_aliases
capture noisily sparkta, type(marginsplot) recastci(rarea) recast(connected) level(90) export("`OUT'/r_mp_t25_stata_aliases.html")
_chk r_mp_t25_stata_aliases `=_rc' "recastci(rarea) recast(connected) level(90) == cistyle(area) connected cilevel(90)"

capture noisily sparkta price, type(bar) over(foreign) nolabel mcolor(navy maroon) export("`OUT'/r_opt_stata_aliases_bar.html")
_chk r_opt_stata_aliases_bar `=_rc' "nolabel mcolor() on a data chart"

capture noisily sparkta, type(coefplot) level(90) cilevel(95) export("`OUT'/r_err_alias_conflict.html")
_chk r_err_alias_conflict `=_rc' "EXPECT rc!=0: alias and primary together"

// factor variables + base level handling
quietly regress price i.rep78 foreign weight
_truth_pre r_pe_cp_factor_base
capture noisily sparkta, type(coefplot) export("`OUT'/r_pe_cp_factor_base.html")
_chk r_pe_cp_factor_base `=_rc'

_truth_pre r_pe_cp_factor_nobase
capture noisily sparkta, type(coefplot) nobase export("`OUT'/r_pe_cp_factor_nobase.html")
_chk r_pe_cp_factor_nobase `=_rc'

// eform (logit) -- OR scale, ref line at 1
quietly logit foreign mpg weight price
_truth_pre r_pe_cp_eform
capture noisily sparkta, type(coefplot) eform export("`OUT'/r_pe_cp_eform.html")
_chk r_pe_cp_eform `=_rc'

_truth_pre r_pe_cp_eform_levels
capture noisily sparkta, type(coefplot) eform cistyle(band) levels(90 95) export("`OUT'/r_pe_cp_eform_levels.html")
_chk r_pe_cp_eform_levels `=_rc'

// multi-model
quietly regress price mpg
estimates store m1
quietly regress price mpg weight
estimates store m2
quietly regress price mpg weight foreign
estimates store m3
_truth_pre r_pe_cp_multi_whisker m1 m2 m3
capture noisily sparkta, type(coefplot) estnames(m1 m2 m3) estlabels(Simple~Controls~Full) export("`OUT'/r_pe_cp_multi_whisker.html")
_chk r_pe_cp_multi_whisker `=_rc'

_truth_pre r_pe_cp_multi_band_levels
capture noisily sparkta, type(coefplot) estnames(m1 m2 m3) cistyle(band) levels(90 95) estlabels(M1~M2~M3) export("`OUT'/r_pe_cp_multi_band_levels.html")
_chk r_pe_cp_multi_band_levels `=_rc' "regression: cpMDot color scope bug"

_truth_pre r_pe_cp_multi_area_sort
capture noisily sparkta, type(coefplot) estnames(m1 m2 m3) cistyle(area) coefsort(value) export("`OUT'/r_pe_cp_multi_area_sort.html")
_chk r_pe_cp_multi_area_sort `=_rc'

_truth_pre r_pe_cp_multi_noci
capture noisily sparkta, type(coefplot) estnames(m1 m2 m3) noci pointstyles(circle rect triangle) export("`OUT'/r_pe_cp_multi_noci.html")
_chk r_pe_cp_multi_noci `=_rc'

// multi-model eform
quietly logit foreign mpg
estimates store l1
quietly logit foreign mpg weight
estimates store l2
_truth_pre r_pe_cp_multi_eform l1 l2
capture noisily sparkta, type(coefplot) estnames(l1 l2) eform cistyle(band) export("`OUT'/r_pe_cp_multi_eform.html")
_chk r_pe_cp_multi_eform `=_rc'

// eventstudy on a synthetic staggered panel (leads/lags)
clear
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
_truth_pre r_pe_es_band
capture noisily sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    cistyle(band) connected pexline(0) title("event study") export("`OUT'/r_pe_es_band.html")
_chk r_pe_es_band `=_rc'

_truth_pre r_pe_es_area_levels
capture noisily sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    cistyle(area) connected levels(90 95) coeflabels("lead5/t-5" "lead4/t-4" "lead3/t-3" "lead2/t-2" "lag0/t0" "lag1/t+1" "lag2/t+2" "lag3/t+3" "lag4/t+4" "lag5/t+5") ///
    export("`OUT'/r_pe_es_area_levels.html")
_chk r_pe_es_area_levels `=_rc'

_truth_pre r_pe_es_dark
capture noisily sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    cistyle(whisker) theme(dark) export("`OUT'/r_pe_es_dark.html")
_chk r_pe_es_dark `=_rc'

// =============================================================================
// SECTION C: marginsplot -- every pattern, incl. the ones added in s8b
// =============================================================================
sysuse auto, clear
quietly regress price i.rep78 foreign weight

quietly margins rep78
_truth_pre r_mp_t1_factor
_stata_graph r_mp_t1_factor marginsplot
capture noisily sparkta, type(marginsplot) export("`OUT'/r_mp_t1_factor.html")
_chk r_mp_t1_factor `=_rc'

quietly margins rep78
_truth_pre r_mp_t2_bar
capture noisily sparkta, type(marginsplot) cistyle(bar) export("`OUT'/r_mp_t2_bar.html")
_chk r_mp_t2_bar `=_rc' "bars (zero-based) + CI whiskers"

quietly margins rep78
_truth_pre r_mp_t3_area
capture noisily sparkta, type(marginsplot) cistyle(area) connected export("`OUT'/r_mp_t3_area.html")
_chk r_mp_t3_area `=_rc'

quietly regress price i.rep78##i.foreign weight
quietly margins rep78#foreign
_truth_pre r_mp_t4_interaction
_stata_graph r_mp_t4_interaction marginsplot
capture noisily sparkta, type(marginsplot) over(foreign) export("`OUT'/r_mp_t4_interaction.html")
_chk r_mp_t4_interaction `=_rc'

quietly margins rep78#foreign
_truth_pre r_mp_t5_interaction_area
capture noisily sparkta, type(marginsplot) over(foreign) cistyle(area) connected export("`OUT'/r_mp_t5_interaction_area.html")
_chk r_mp_t5_interaction_area `=_rc'

quietly regress price i.foreign c.weight c.mpg
quietly margins, at(weight=(1760(250)4840))
_truth_pre r_mp_t6_numericx
_stata_graph r_mp_t6_numericx marginsplot
capture noisily sparkta, type(marginsplot) cistyle(area) connected export("`OUT'/r_mp_t6_numericx.html")
_chk r_mp_t6_numericx `=_rc' "x labels 1760..4760; tooltip title weight = 1760"

quietly margins foreign, at(weight=(1760(250)4840))
_truth_pre r_mp_t7_numericx_2series
capture noisily sparkta, type(marginsplot) over(foreign) cistyle(area) connected export("`OUT'/r_mp_t7_numericx_2series.html")
_chk r_mp_t7_numericx_2series `=_rc'

quietly regress price i.rep78##i.foreign weight
quietly margins r.foreign@rep78, contrast
_truth_pre r_mp_t8_contrast
capture noisily sparkta, type(marginsplot) pexline(0) export("`OUT'/r_mp_t8_contrast.html")
_chk r_mp_t8_contrast `=_rc'

quietly regress price i.rep78 c.weight##c.mpg
quietly margins rep78, dydx(weight)
_truth_pre r_mp_t9_ame
capture noisily sparkta, type(marginsplot) pexline(0) export("`OUT'/r_mp_t9_ame.html")
_chk r_mp_t9_ame `=_rc'

quietly margins rep78
_truth_pre r_mp_t10_nested
capture noisily sparkta, type(marginsplot) levels(90 95) export("`OUT'/r_mp_t10_nested.html")
_chk r_mp_t10_nested `=_rc' "mpLegend + mpW_L2_0; tooltip shows both CIs"

quietly regress price i.rep78 foreign weight
quietly margins rep78, post
_truth_pre r_mp_t11_post_coefplot
capture noisily sparkta, type(coefplot) export("`OUT'/r_mp_t11_post_coefplot.html")
_chk r_mp_t11_post_coefplot `=_rc'

// --- new in s8b ---------------------------------------------------------------
quietly regress price i.rep78 foreign weight
quietly margins rep78, pwcompare
// diagnostic: which r() matrices does pwcompare leave? (review reads _run.log)
return list
capture noisily matrix list r(table_vs)
_truth_pre r_mp_t12_pwcompare
capture noisily sparkta, type(marginsplot) pexline(0) export("`OUT'/r_mp_t12_pwcompare.html")
_chk r_mp_t12_pwcompare `=_rc' "labels should read 2 vs 1 etc."

quietly regress price i.foreign c.weight c.mpg
quietly margins, at(weight=(1760(250)4840)) level(95)
_truth_pre r_mp_t13_nested_numericx
capture noisily sparkta, type(marginsplot) levels(90 95) export("`OUT'/r_mp_t13_nested_numericx.html")
_chk r_mp_t13_nested_numericx `=_rc' "regression: inner CI must align on numeric x"

quietly regress price i.rep78##i.foreign weight
quietly margins rep78#foreign
_truth_pre r_mp_t14_nested_2series
capture noisily sparkta, type(marginsplot) over(foreign) levels(90 95) export("`OUT'/r_mp_t14_nested_2series.html")
_chk r_mp_t14_nested_2series `=_rc' "regression: inner CI for BOTH series (mpW_L2_0, mpW_L2_1)"

quietly regress price i.foreign c.weight
quietly margins, at(foreign=(0 1))
_truth_pre r_mp_t15_factor_at
capture noisily sparkta, type(marginsplot) export("`OUT'/r_mp_t15_factor_at.html")
_chk r_mp_t15_factor_at `=_rc' "factor at() -- untested pattern"

quietly regress price c.weight c.mpg
quietly margins, at(weight=(2000 3000 4000) mpg=(15 25))
capture noisily sparkta, type(marginsplot) export("`OUT'/r_err_two_at_vars.html")
_chk r_err_two_at_vars `=_rc' "EXPECT rc!=0: two varying at() variables"

quietly logit foreign c.weight c.mpg
quietly margins, at(weight=(1760(500)4760))
_truth_pre r_mp_t17_logit_pr
capture noisily sparkta, type(marginsplot) cistyle(area) connected export("`OUT'/r_mp_t17_logit_pr.html")
_chk r_mp_t17_logit_pr `=_rc' "predicted probabilities on numeric x"

quietly regress price i.rep78 foreign weight
quietly margins, over(foreign)
_truth_pre r_mp_t18_margins_over
capture noisily sparkta, type(marginsplot) export("`OUT'/r_mp_t18_margins_over.html")
_chk r_mp_t18_margins_over `=_rc' "margins, over() (not sparkta over())"

quietly margins rep78
_truth_pre r_mp_t19_dark
capture noisily sparkta, type(marginsplot) theme(dark_tab1) levels(90 95) cistyle(area) pexline(6000) ///
    title("dark nested area") export("`OUT'/r_mp_t19_dark.html")
_chk r_mp_t19_dark `=_rc'

quietly margins rep78
_truth_pre r_mp_t21_nested_ribbon
capture noisily sparkta, type(marginsplot) cistyle(area) connected levels(90 95) title("nested ribbons") export("`OUT'/r_mp_t21_nested_ribbon.html")
_chk r_mp_t21_nested_ribbon `=_rc' "two nested ribbons, no whiskers"

quietly margins rep78
_truth_pre r_mp_t22_nested_ribbon_whisk
capture noisily sparkta, type(marginsplot) cistyle(area whisker) connected levels(90 95) title("nested ribbons + whiskers") export("`OUT'/r_mp_t22_nested_ribbon_whisk.html")
_chk r_mp_t22_nested_ribbon_whisk `=_rc' "ribbons with whiskers overlaid: cistyle(area whisker)"

quietly regress price mpg
capture noisily sparkta, type(coefplot) cistyle(area whisker) export("`OUT'/r_err_cistyle_combo_coefplot.html")
_chk r_err_cistyle_combo_coefplot `=_rc' "EXPECT rc!=0: combined cistyle is marginsplot-only for now"

quietly regress price i.rep78 foreign weight
quietly margins rep78
_truth_pre r_mp_t23_plotmargin
capture noisily sparkta, type(marginsplot) plotmargin(10 10 25 25) title("plotmargin 10 10 25 25") export("`OUT'/r_mp_t23_plotmargin.html")
_chk r_mp_t23_plotmargin `=_rc' "25% y cushion, 10% x"

capture noisily sparkta price, type(bar) over(rep78) plotmargin(20) export("`OUT'/r_opt_plotmargin_bar.html")
_chk r_opt_plotmargin_bar `=_rc' "plotmargin(20) -> ygrace 20% on a data chart"

quietly margins rep78
_truth_pre r_mp_t24_plotmargin_neg
capture noisily sparkta, type(marginsplot) plotmargin(5 5 -20 -20) title("negative plotmargin crops the CI tails") export("`OUT'/r_mp_t24_plotmargin_neg.html")
_chk r_mp_t24_plotmargin_neg `=_rc' "y bounds 20% INSIDE the data range (cropped on purpose)"

capture noisily sparkta price, type(bar) over(rep78) plotmargin(-10) export("`OUT'/r_err_plotmargin_neg_bar.html")
_chk r_err_plotmargin_neg_bar `=_rc' "EXPECT rc!=0: negative plotmargin on a data chart"

capture noisily sparkta price, type(bar) over(rep78) plotmargin(5 500) export("`OUT'/r_err_plotmargin_range.html")
_chk r_err_plotmargin_range `=_rc' "EXPECT rc!=0: plotmargin out of range"

quietly margins rep78
_truth_pre r_mp_t20_titles_width
capture noisily sparkta, type(marginsplot) xtitle("Repair record") ytitle("Predicted price (USD)") ///
    ciwidth(2.5) export("`OUT'/r_mp_t20_titles_width.html")
_chk r_mp_t20_titles_width `=_rc'

// =============================================================================
// =====================================================================
// SECTION E (v3.6.0-t2d): OPTION COVERAGE -- data-chart options that the
// post-estimation sessions never exercised. Each case packs several
// compatible options; intent_check derives rules for the ones it knows.
// =====================================================================
sysuse auto, clear
display as text _newline "--- E: fit types ---"
capture noisily sparkta price weight, type(scatter) fit(lfit) fitci export("`OUT'/r_fit_lfit_ci.html")
_chk r_fit_lfit_ci `=_rc' "lfit + CI band"
capture noisily sparkta price weight, type(scatter) fit(qfit) fitci over(foreign) export("`OUT'/r_fit_qfit_ci_over.html")
_chk r_fit_qfit_ci_over `=_rc' "qfit + CI per group"
capture noisily sparkta price weight, type(scatter) fit(lowess) export("`OUT'/r_fit_lowess.html")
_chk r_fit_lowess `=_rc' "lowess"
capture noisily sparkta price weight, type(scatter) fit(lowess) over(foreign) mlabel(make) export("`OUT'/r_fit_lowess_over_mlabel.html")
_chk r_fit_lowess_over_mlabel `=_rc' "lowess per group + marker labels"
capture noisily sparkta price weight, type(scatter) fit(exp) export("`OUT'/r_fit_exp.html")
_chk r_fit_exp `=_rc' "exponential fit"
capture noisily sparkta price weight, type(scatter) fit(log) export("`OUT'/r_fit_log.html")
_chk r_fit_log `=_rc' "log fit"
capture noisily sparkta price weight, type(scatter) fit(power) export("`OUT'/r_fit_power.html")
_chk r_fit_power `=_rc' "power fit"
capture noisily sparkta price weight, type(scatter) fit(ma) export("`OUT'/r_fit_ma.html")
_chk r_fit_ma `=_rc' "moving average"
capture noisily sparkta price weight, type(scatter) fit(lowess) fitci export("`OUT'/r_err_fitci_lowess.html")
_chk r_err_fitci_lowess `=_rc' "EXPECT rc!=0: fitci only with lfit/qfit"

display as text _newline "--- E: axes ---"
capture noisily sparkta price, type(bar) over(rep78) yrange(0 8000) ystepsize(2000) ytickangle(0) xlabels(One|Two|Three|Four|Five) ///
    subtitle("custom axes") note("Source: auto.dta") caption("N = 69 with rep78") export("`OUT'/r_axes_range_step_labels.html")
_chk r_axes_range_step_labels `=_rc' "yrange ystepsize xlabels subtitle note caption"
capture noisily sparkta price weight, type(scatter) xrange(1500 5000) xstepsize(500) xtype(log) ytype(log) export("`OUT'/r_axes_log_log.html")
_chk r_axes_log_log `=_rc' "log-log scatter with explicit range/step"
capture noisily sparkta price, type(line) over(rep78) xreverse yreverse xgridlines(off) ygridlines(off) xborder(off) yborder(off) export("`OUT'/r_axes_reverse_grid.html")
_chk r_axes_reverse_grid `=_rc' "reversed axes, grid and border toggles"
capture noisily sparkta price mpg, type(line) over(rep78) y2(mpg) y2title("MPG (right)") y2range(10 45) export("`OUT'/r_axes_y2.html")
_chk r_axes_y2 `=_rc' "secondary y axis"
capture noisily sparkta price, type(bar) over(rep78) xtickcount(3) ytickcount(4) ygrace(20) aspect(2.2) padding(30) export("`OUT'/r_axes_tickcount_aspect.html")
_chk r_axes_tickcount_aspect `=_rc' "tick counts, ygrace, aspect, padding"

display as text _newline "--- E: line / area styling ---"
capture noisily sparkta price, type(line) over(rep78) stepped(after) lpattern(dashdot) pointstyle(diamond) pointsize(6) pointborderwidth(2) export("`OUT'/r_line_stepped_pattern.html")
_chk r_line_stepped_pattern `=_rc' "stepped line, pattern, diamond points"
capture noisily sparkta price mpg, type(line) over(rep78) smooth(0.6) spanmissing fill areaopacity(0.3) linewidth(3) export("`OUT'/r_line_smooth_fill.html")
_chk r_line_smooth_fill `=_rc' "smooth, spanmissing, fill, areaopacity"
capture noisily sparkta price, type(bar) over(rep78) barwidth(0.5) bargroupwidth(0.7) borderradius(6) opacity(0.6) stat(median) export("`OUT'/r_bar_widths_median.html")
_chk r_bar_widths_median `=_rc' "bar widths, radius, opacity, stat(median)"
capture noisily sparkta price, type(bar) over(rep78) stat(count) datalabels sortgroups(desc) export("`OUT'/r_bar_count_datalabels_desc.html")
_chk r_bar_count_datalabels_desc `=_rc' "stat(count) with data labels, groups sorted desc"
capture noisily sparkta price, type(bar) over(foreign) gradient gradcolors(navy|white) animate(slow) easing(bounce) animduration(1200) animdelay(200) export("`OUT'/r_bar_gradient_anim.html")
_chk r_bar_gradient_anim `=_rc' "gradient + animation controls"

display as text _newline "--- E: pie / histogram / distribution ---"
capture noisily sparkta, type(donut) over(rep78) cutout(65) rotation(90) circumference(270) sliceborder(2) hoveroffset(12) pielabels export("`OUT'/r_pie_donut_styling.html")
_chk r_pie_donut_styling `=_rc' "donut cutout/rotation/circumference/labels"
capture noisily sparkta price, type(pie) over(foreign) stat(sum) export("`OUT'/r_pie_sum.html")
_chk r_pie_sum `=_rc' "pie stat(sum)"
capture noisily sparkta price, type(histogram) histtype(frequency) bins(12) export("`OUT'/r_hist_frequency.html")
_chk r_hist_frequency `=_rc' "histogram frequency"
capture noisily sparkta price, type(histogram) histtype(fraction) by(foreign) export("`OUT'/r_hist_fraction_by.html")
_chk r_hist_fraction_by `=_rc' "histogram fraction, by() panels"
capture noisily sparkta price, type(histogram) over(foreign) export("`OUT'/r_err_hist_over.html")
_chk r_err_hist_over `=_rc' "EXPECT rc!=0: histogram does not take over()"
capture noisily sparkta price, type(violin) over(foreign) bandwidth(400) mediancolor(red) meancolor(black) export("`OUT'/r_violin_bandwidth_colors.html")
_chk r_violin_bandwidth_colors `=_rc' "violin bandwidth + marker colours"
capture noisily sparkta price mpg, type(boxplot) over(foreign) by(rep78) layout(horizontal) export("`OUT'/r_box_two_vars_layout.html")
_chk r_box_two_vars_layout `=_rc' "boxplot two vars, by() panels: wrapped grid, shared axis, one key"
capture noisily sparkta price, type(bar) over(foreign) by(rep78) layout(grid) export("`OUT'/r_by_bar_grid_shared.html")
_chk r_by_bar_grid_shared `=_rc' "by() grid, shared axis from the panel MEANS"
capture noisily sparkta price, type(bar) over(foreign) by(rep78) yfree export("`OUT'/r_by_bar_yfree.html")
_chk r_by_bar_yfree `=_rc' "yfree: each panel keeps its own axis"
capture noisily sparkta price, type(violin) over(foreign) by(rep78) export("`OUT'/r_by_violin_key.html")
_chk r_by_violin_key `=_rc' "violin by(): one key above the grid"
capture noisily sparkta price, type(boxplot) over(foreign) by(rep78) mediancolor(red) meancolor(gold) export("`OUT'/r_by_box_custom_marker_colors.html")
_chk r_by_box_custom_marker_colors `=_rc' "custom median/mean colours must appear in the key glyphs"
capture noisily sparkta price mpg, type(line) over(rep78) by(foreign) lpatterns(solid|dash) pointstyle(diamond) yline(6000) ylinelabel("Mean price") ylinecolor(red) export("`OUT'/r_by_line_styled_key.html")
_chk r_by_line_styled_key `=_rc' "by() key: dash pattern, diamond marker and the reference line, in the user colours"

display as text _newline "--- E: legend / text / colours ---"
capture noisily sparkta price mpg, type(line) over(rep78) legend(bottom) legtitle("Series") legsize(14) legcolor(navy) legbgcolor(white) legboxheight(14) export("`OUT'/r_legend_styling.html")
_chk r_legend_styling `=_rc' "legend position/title/size/colours"
capture noisily sparkta price, type(bar) over(foreign) nolegend bgcolor(#f4f4f4) plotcolor(white) gridcolor(gray) gridopacity(0.4) ///
    subtitle("styled text") subtitlesize(18) subtitlecolor(maroon) xtitlesize(16) xtitlecolor(navy) ytitlesize(16) ytitlecolor(navy) xlabsize(13) ylabsize(13) xlabcolor(black) ylabcolor(black) notesize(11) export("`OUT'/r_text_sizes_colors.html")
_chk r_text_sizes_colors `=_rc' "text sizes/colours, background, grid"
capture noisily sparkta price, type(bar) over(rep78) tooltipformat(%12.1fc) tooltipmode(index) tooltipposition(average) tooltipborder(2) tooltipfontsize(14) tooltippadding(10) export("`OUT'/r_tooltip_options.html")
_chk r_tooltip_options `=_rc' "tooltip format/mode/position/style"
capture noisily sparkta price, type(bar) over(rep78, showmissing) export("`OUT'/r_missing_showmissing.html")
_chk r_missing_showmissing `=_rc' "over(var, showmissing): missing rep78 as its own bar"
capture noisily sparkta price, type(bar) over(rep78) nomissing export("`OUT'/r_missing_nomissing.html")
_chk r_missing_nomissing `=_rc' "nomissing"

display as text _newline "--- E: annotations ---"
// t2j fix8x: apoint() takes space-separated y x pairs (the pipe form was a do-file typo; rc=198 in the fix8w run)
capture noisily sparkta price weight, type(scatter) xline(3000|4000) xlinecolor(red|blue) xlinelabel(A|B) yline(6000) ylinecolor(green) ///
    xband(2000 2500) xbandcolor(rgba(0,0,255,0.1)) yband(8000 10000|12000 14000) ybandcolor(rgba(255,0,0,0.08)|rgba(0,255,0,0.08)) ///
    apoint(6000 3000 12000 4000) apointcolor(red|blue) apointsize(9) alabeltext(Cheap|Heavy) alabelpos(6000 3000 15|12000 4000 45) alabelfs(12) alabelgap(14) ///
    aellipse(4000 2000 8000 3500) aellipsecolor(rgba(255,165,0,0.15)) aellipseborder(orange) export("`OUT'/r_annot_full.html")
_chk r_annot_full `=_rc' "lines, bands, points, labels, ellipse together"
capture noisily sparkta price, type(bar) over(rep78) xline(3) xband(1 2) export("`OUT'/r_annot_categorical_ignored.html")
_chk r_annot_categorical_ignored `=_rc' "xline/xband on categorical x are ignored, not errors"

display as text _newline "--- E: filters and sliders ---"
capture noisily sparkta price, type(bar) over(foreign) online export("`OUT'/r_opt_online_cdn.html")
_chk r_opt_online_cdn `=_rc' "online: CDN scripts, small file"
capture noisily sparkta price, type(bar) over(foreign) online offline export("`OUT'/r_err_online_offline.html")
_chk r_err_online_offline `=_rc' "EXPECT rc!=0: online and offline together"
capture noisily sparkta price, type(bar) over(foreign) filters(rep78) sliders(weight) export("`OUT'/r_filter_sliders.html")
_chk r_filter_sliders `=_rc' "filter + range slider"
capture noisily sparkta price weight, type(scatter) filters(rep78 foreign) sliders(mpg) fit(lfit) fitci export("`OUT'/r_filter_scatter_fit.html")
_chk r_filter_scatter_fit `=_rc' "two filters + slider on a scatter with CI fit"
capture noisily sparkta price, type(line) over(rep78) filters(foreign) noallfilter export("`OUT'/r_filter_noall.html")
_chk r_filter_noall `=_rc' "noallfilter"

display as text _newline "--- E: post-estimation table extras ---"
quietly regress price mpg weight foreign
capture noisily sparkta, type(coefplot) headings(1 "Vehicle"|3 "Origin") nocons rescale(0.001) order(foreign mpg weight) addstats(rsq fstat "Sample/Full Full Full") indicators("Controls/Yes Yes Yes") export("`OUT'/r_cp_table_extras.html")
_chk r_cp_table_extras `=_rc' "headings nocons rescale order addstats indicators"
quietly regress price mpg
capture noisily sparkta, type(coefplot) cons export("`OUT'/r_cp_cons.html")
_chk r_cp_cons `=_rc' "cons shown explicitly"

// =====================================================================
// SECTION F (v3.6.0-t2d): EXPORT toolbar (PNG / SVG / PDF) on one page per family
// =====================================================================
sysuse auto, clear
capture noisily sparkta price, type(bar) over(rep78) download title("Export check: bar") export("`OUT'/r_export_bar.html")
_chk r_export_bar `=_rc' "PNG/SVG/PDF on a bar chart"
capture noisily sparkta price mpg, type(boxplot) over(foreign) by(rep78) download title("Export check: box panels") export("`OUT'/r_export_box_by.html")
_chk r_export_box_by `=_rc' "export on by() panels: key above grid must be in PNG/SVG"
quietly regress price mpg weight foreign
capture noisily sparkta, type(coefplot) levels(90 95) download title("Export check: coefplot") export("`OUT'/r_export_coefplot.html")
_chk r_export_coefplot `=_rc' "export with coefficient table (PDF: vector chart + text table)"
quietly regress price i.rep78 foreign weight
quietly margins rep78
capture noisily sparkta, type(marginsplot) cistyle(area whisker) levels(90 95) download title("Export check: marginsplot") export("`OUT'/r_export_marginsplot.html")
_chk r_export_marginsplot `=_rc' "export with nested band + whisker key"
capture noisily sparkta price weight, type(scatter) fit(lfit) fitci over(foreign) download title("Export check: scatter fit") export("`OUT'/r_export_scatter_fit.html")
_chk r_export_scatter_fit `=_rc' "export with fit line + CI band in key"
// t2j fix8w (U21): the toolbar is the DEFAULT now (download above is a no-op alias); notoolbar hides it
capture noisily sparkta price, type(bar) over(rep78) notoolbar title("Export check: notoolbar") export("`OUT'/r_export_notoolbar.html")
_chk r_export_notoolbar `=_rc' "notoolbar: page has no PNG/SVG/PDF buttons"

// =====================================================================
// SECTION G (v3.6.0-t2d): saveas() -- PDF / SVG / PNG straight from Stata (headless Edge/Chrome)
// Skipped when no Chromium-based browser is found (message printed).
// =====================================================================
sysuse auto, clear
sparkta, findbrowser
local _bfound `"$SPARKTA_BROWSER_FOUND"'
if `"`_bfound'"' != "" {
    display as text "  saveas: using `_bfound'"
    capture noisily sparkta price, type(bar) over(rep78) download export("`OUT'/r_saveas_bar.html") saveas(`OUT'/r_saveas_bar.pdf `OUT'/r_saveas_bar.svg `OUT'/r_saveas_bar.png)
    _chk r_saveas_bar `=_rc' "pdf + svg + png written without clicking"
    foreach ext in pdf svg png {
        capture confirm file "`OUT'/r_saveas_bar.`ext'"
        if _rc display as error "  MISSING r_saveas_bar.`ext'"
        else display as result "  ok   r_saveas_bar.`ext'"
    }
    capture noisily sparkta price, type(bar) over(rep78) export("`OUT'/r_saveas_nodownload.html") saveas(`OUT'/r_saveas_nodownload.pdf `OUT'/r_saveas_nodownload.svg)
    _chk r_saveas_nodownload `=_rc' "saveas WITHOUT download: exporter must still be on the page"
    foreach ext in pdf svg {
        capture confirm file "`OUT'/r_saveas_nodownload.`ext'"
        if _rc display as error "  MISSING r_saveas_nodownload.`ext'"
        else display as result "  ok   r_saveas_nodownload.`ext'"
    }
    quietly regress price mpg weight foreign
    capture noisily sparkta, type(coefplot) levels(90 95) export("`OUT'/r_saveas_table.html") saveas(`OUT'/r_saveas_table.pdf, table)
    _chk r_saveas_table `=_rc' "table-only PDF from Stata"
    capture noisily sparkta price, type(bar) over(rep78) export("`OUT'/r_saveas_png3x.html") saveas(`OUT'/r_saveas_png3x.png, scale(3))
    _chk r_saveas_png3x `=_rc' "PNG at 3x device scale (3300x2100)"
    capture noisily sparkta price mpg, type(boxplot) over(foreign) by(rep78) export("`OUT'/r_saveas_by.html") saveas(`OUT'/r_saveas_by.pdf `OUT'/r_saveas_by.png)
    _chk r_saveas_by `=_rc' "by() panels composed into one figure (title, key, captioned grid)"
    capture noisily sparkta price, type(bar) over(rep78) export("`OUT'/r_saveas_page.html") saveas(`OUT'/r_saveas_page.pdf, page)
    _chk r_saveas_page `=_rc' "whole-page PDF (chart + stats panel)"
    capture noisily sparkta price, type(bar) over(rep78) export("`OUT'/r_err_saveas_ext.html") saveas(`OUT'/x.docx)
    _chk r_err_saveas_ext `=_rc' "EXPECT rc!=0: unsupported saveas extension"
    local _bp `"`_bfound'"'
    capture noisily sparkta price, type(bar) over(rep78) export("`OUT'/r_saveas_browser_opt.html") saveas(`OUT'/r_saveas_browser_opt.png) browser("`_bp'")
    _chk r_saveas_browser_opt `=_rc' "browser() option with an explicit path"
    capture noisily sparkta price, type(bar) over(rep78) export("`OUT'/r_err_browser_missing.html") saveas(`OUT'/x.png) browser("C:\nope\chrome.exe")
    _chk r_err_browser_missing `=_rc' "EXPECT rc!=0: browser() path does not exist"
    capture noisily sparkta price, type(bar) over(rep78) export("`OUT'/r_saveas_close.html") saveas(`OUT'/r_saveas_close.png, close)
    _chk r_saveas_close `=_rc' "saveas(..., close): browser quits right after the export"
    capture noisily sparkta price, type(bar) over(rep78) export("`OUT'/r_saveas_idle.html") saveas(`OUT'/r_saveas_idle.png, idle(30))
    _chk r_saveas_idle `=_rc' "saveas(..., idle(30)): 30 s idle timeout for this session"
    sparkta, closebrowser
    display as text "  (session browser closed at the end of Section G)"
}
else display as text "  (no Edge/Chrome found -- saveas() cases skipped; set global SPARKTA_BROWSER to test)"

// =====================================================================
// SECTION H (v3.6.0-t2d): MATRIX MODE -- coefplot/eventstudy from matrices
// =====================================================================
sysuse auto, clear
display as text _newline "--- H: round trip: the same regression through e(b) and through r(table) must match ---"
quietly regress price mpg weight foreign
capture noisily sparkta, type(coefplot) levels(90 95) export("`OUT'/r_mat_rt_normal.html")
_chk r_mat_rt_normal `=_rc' "reference: normal coefplot from e(b)/e(V)"
quietly regress price mpg weight foreign
capture noisily sparkta, type(coefplot) levels(90 95) matrix(r(table)) export("`OUT'/r_mat_rt_table.html")
_chk r_mat_rt_table `=_rc' "matrix(r(table)): autodetected b se ll ul pvalue df"
display as text _newline "--- H: Statalist case: b and se in two rows ---"
matrix input A = (13.1304, 3.6182, 1.3543, -1.2372 \ 4.766, 1.388, 0.936, 1.410)
matrix colnames A = Base Pre Post1 Post2
capture noisily sparkta, type(coefplot) matrix(A) se(2) export("`OUT'/r_mat_b_se.html")
_chk r_mat_b_se `=_rc' "matrix(A) se(2): CI computed with z"
capture noisily sparkta, type(coefplot) matrix(A) se(2) df(30) export("`OUT'/r_mat_b_se_df.html")
_chk r_mat_b_se_df `=_rc' "matrix(A) se(2) df(30): CI computed with t(30)"
capture noisily sparkta, type(coefplot) matrix(A) export("`OUT'/r_mat_points_only.html")
_chk r_mat_points_only `=_rc' "estimates only: points, no CI, one note"
display as text _newline "--- H: Jann forms: ci((2 3)), name-based, column-oriented, another matrix ---"
matrix C = J(3, 3, .)
matrix rownames C = median ll95 ul95
matrix colnames C = mpg trunk turn
local i 0
foreach v of var mpg trunk turn {
    local ++i
    quietly centile `v'
    matrix C[1, `i'] = r(c_1) \ r(lb_1) \ r(ub_1)
}
capture noisily sparkta, type(coefplot) matrix(C) ci((2 3)) export("`OUT'/r_mat_jann_ci.html")
_chk r_mat_jann_ci `=_rc' "Jann: matrix(C), ci((2 3)) -- the help-file example"
capture noisily sparkta, type(coefplot) matrix(C[1]) ci(ll95 ul95) export("`OUT'/r_mat_ci_names.html")
_chk r_mat_ci_names `=_rc' "ci(ll95 ul95) by name; matrix(C[1]) selects the row"
matrix T = C'
capture noisily sparkta, type(coefplot) matrix(T[,1]) ci(2 3) export("`OUT'/r_mat_cols.html")
_chk r_mat_cols `=_rc' "column-oriented: matrix(T[,1]) with ci(2 3) read as columns"
matrix B = A[1, 1...]
matrix S = A[2, 1...]
capture noisily sparkta, type(coefplot) matrix(B) se(S) export("`OUT'/r_mat_se_matrix.html")
_chk r_mat_se_matrix `=_rc' "se(S): standard errors from another matrix"
display as text _newline "--- H: margins and bootstrap ---"
quietly regress price i.rep78 foreign weight
quietly margins rep78
capture noisily sparkta, type(coefplot) matrix(r(table)) export("`OUT'/r_mat_margins.html")
_chk r_mat_margins `=_rc' "margins r(table) as a coefplot (not marginsplot)"
capture noisily bootstrap, reps(50) seed(1) nowarn: regress price mpg weight
capture noisily sparkta, type(coefplot) matrix(e(b)) ci(e(ci_normal)) export("`OUT'/r_mat_bootstrap.html")
_chk r_mat_bootstrap `=_rc' "bootstrap: matrix(e(b)) with ci(e(ci_normal)) two-row CI matrix"
display as text _newline "--- H: several matrices, labels ---"
quietly regress price mpg weight
matrix M1 = r(table)
quietly regress price mpg weight foreign
matrix M2 = r(table)
capture noisily sparkta, type(coefplot) matrix(M1 M2) plotlabels("Short" "Full") export("`OUT'/r_mat_multi.html")
_chk r_mat_multi `=_rc' "matrix(M1 M2) + plotlabels(): multi-model layout from matrices"
display as text _newline "--- H: event study from a matrix with relative-time names ---"
matrix E = J(3, 7, .)
matrix rownames E = b se pvalue
matrix colnames E = Tm3 Tm2 Tm1 Tp0 Tp1 Tp2 Post_avg
matrix E[1, 1] = 0.03,  -0.004, -0.024, -0.020, -0.051, -0.137, -0.077
matrix E[2, 1] = 0.014,  0.013,  0.014,  0.011,  0.016,  0.035,  0.020
matrix E[3, 1] = 0.057,  0.780,  0.109,  0.067,  0.001,  0.000,  0.000
capture noisily sparkta, type(eventstudy) matrix(E) export("`OUT'/r_mat_event_names.html")
_chk r_mat_event_names `=_rc' "eventstudy from a matrix: Tm3..Tp2 ordered by time, Post_avg left off the axis"
capture noisily sparkta, type(eventstudy) matrix(E) show(*) export("`OUT'/r_mat_event_summ.html")
_chk r_mat_event_summ `=_rc' "show(*): Post_avg included, after the series"
matrix E2 = E
matrix colnames E2 = T-3 T-2 T-1 T T+1 T+2 Pre_avg
capture noisily sparkta, type(eventstudy) matrix(E2) show(T* Pre_avg) export("`OUT'/r_mat_event_csdid1.html")
_chk r_mat_event_csdid1 `=_rc' "csdid 1.x names T-3 .. T+2 parsed; show() with a wildcard"
display as text _newline "--- H: csdid2 / jwdid / lwdid when installed ---"
capture which csdid
if _rc == 0 {
    capture noisily sparkta_matrix_did_cases "`OUT'"
}
else display as text "  (csdid not installed -- DiD matrix cases skipped)"
display as text _newline "--- H: errors ---"
capture noisily sparkta, type(coefplot) matrix(NOPE) export("`OUT'/r_err_mat_missing.html")
_chk r_err_mat_missing `=_rc' "EXPECT rc!=0: matrix not found"
capture noisily sparkta, type(coefplot) matrix(A) ci(2) export("`OUT'/r_err_mat_ci_one.html")
_chk r_err_mat_ci_one `=_rc' "EXPECT rc!=0: ci() needs two positions"
capture noisily sparkta, type(coefplot) matrix(A) se(nothere) export("`OUT'/r_err_mat_se_name.html")
_chk r_err_mat_se_name `=_rc' "EXPECT rc!=0: se(name) not in the matrix"
capture noisily sparkta, type(marginsplot) matrix(A) export("`OUT'/r_err_mat_marginsplot.html")
_chk r_err_mat_marginsplot `=_rc' "EXPECT rc!=0: matrix() with marginsplot"
quietly regress price mpg weight
estimates store em1
capture noisily sparkta, type(coefplot) matrix(A) estnames(em1) export("`OUT'/r_err_mat_estnames.html")
_chk r_err_mat_estnames `=_rc' "EXPECT rc!=0: matrix() with estnames()"
capture noisily sparkta price, type(bar) over(foreign) ci(2 3) export("`OUT'/r_err_ci_without_matrix.html")
_chk r_err_ci_without_matrix `=_rc' "EXPECT rc!=0: ci() without matrix()"

// =============================================================================
// SECTION I (v3.6.0-t2e): COLOUR NORMALISATION -- every colour option accepts
// colorpalette triplets, Stata colour names, hex without #, and the %opacity /
// *intensity suffixes. Also the t2e eventstudy band fixes (ribbon per phase,
// inner level, cicolors(pre|post)).
// =============================================================================
display as text _newline "--- I: colour normalisation ---"
sysuse auto, clear
// Stata names + gs + a CSS pass-through name; expect navy #1a476f, cranberry #c10534, gs10 #a0a0a0
capture noisily sparkta price mpg, type(bar) over(foreign) colors(navy cranberry gs10 tomato) export("`OUT'/r_col_stata_names.html")
_chk r_col_stata_names `=_rc' "Stata colour names -> hex; tomato passes through"
// colorpalette-style triplets, quoted, mixed with a bare hex (no #)
capture noisily sparkta price mpg, type(bar) over(foreign) colors("230 159 0" "86 180 233" e69f00) export("`OUT'/r_col_triplets.html")
_chk r_col_triplets `=_rc' "RGB triplets and bare hex -> #e69f00 #56b4e9"
// opacity and intensity suffixes: navy%50 -> rgba(26,71,111,.5); maroon*0.6 lighter; navy*1.5 darker
capture noisily sparkta price mpg, type(line) over(foreign) colors(navy%50 maroon*0.6) ylinecolor(navy*1.5) yline(6000) export("`OUT'/r_col_suffixes.html")
_chk r_col_suffixes `=_rc' "%opacity and *intensity suffixes"
// single-colour options and pipe lists take the same grammar
capture noisily sparkta price, type(bar) over(rep78) bgcolor(gs15) plotcolor("255 255 224") gridcolor(ltblue) titlecolor(dknavy) export("`OUT'/r_col_single_options.html")
_chk r_col_single_options `=_rc' "bgcolor/plotcolor/gridcolor/titlecolor normalised"
// the real thing: locals straight from colorpalette (skipped when colorpalette is absent)
capture which colorpalette
if _rc == 0 {
    colorpalette okabe, nograph
    capture noisily sparkta price mpg, type(bar) over(foreign) colors("`r(p1)'" "`r(p2)'" "`r(p3)'") export("`OUT'/r_col_colorpalette_locals.html")
    _chk r_col_colorpalette_locals `=_rc' "colors() from colorpalette r(p#) locals"
}
else display as text "  (colorpalette not installed -- r_col_colorpalette_locals skipped)"
// eventstudy: band split by phase, inner level, cicolors(pre|post) with Stata names, base period -1 omitted
clear
set seed 2026
set obs 4000
gen id   = ceil(_n / 20)                  // same panel as Section B: 20 periods, so lead5..lag5 all exist
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
_truth_pre r_col_es_band_cicolors
capture noisily sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    cistyle(band) levels(90 95) colors(navy maroon) cicolors(emidblue|erose) export("`OUT'/r_col_es_band_cicolors.html")
_chk r_col_es_band_cicolors `=_rc' "eventstudy band per phase + inner + cicolors(pre|post)"
_truth_pre r_col_es_band_triplets
capture noisily sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    cistyle(band) connected colors("230 159 0" "0 114 178") export("`OUT'/r_col_es_band_triplets.html")
_chk r_col_es_band_triplets `=_rc' "eventstudy pre/post from colors() slots 1-2 (triplets)"
// t2f: reference period marker (hollow, at 0), leads/lags as separate lines, together, refperiod()
_truth_pre r_es_ref_together
capture noisily sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    cistyle(band) connected together export("`OUT'/r_es_ref_together.html")
_chk r_es_ref_together `=_rc' "reference marker at -1; one line through it (together)"
_truth_pre r_es_ref_none
capture noisily sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    cistyle(whisker) connected refperiod(none) export("`OUT'/r_es_ref_none.html")
_chk r_es_ref_none `=_rc' "refperiod(none): no reference marker"
_truth_pre r_es_ref_explicit
capture noisily sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    refperiod(-1) export("`OUT'/r_es_ref_explicit.html")
_chk r_es_ref_explicit `=_rc' "refperiod(-1) explicit"
_truth_pre r_es_separate
capture noisily sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    cistyle(band) connected separate export("`OUT'/r_es_separate.html")
_chk r_es_separate `=_rc' "separate: leads and lags as two series (estimation mode default is together)"
capture noisily sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    together separate export("`OUT'/r_err_together_separate.html")
_chk r_err_together_separate `=_rc' "EXPECT rc!=0: together and separate together"
sysuse auto, clear
capture noisily sparkta price, type(bar) over(foreign) together export("`OUT'/r_err_together_bar.html")
_chk r_err_together_bar `=_rc' "EXPECT rc!=0: together only with eventstudy"
// t2f fix 4: results() -- a dataset/frame of estimates (parmest/regsave/lwdid layouts) -> matrix mode
quietly regress price mpg weight foreign
tempfile _pe
preserve
matrix _T = r(table)'
local _rn : rownames _T
clear
quietly svmat double _T, names(col)
quietly gen str32 parm = word("`_rn'", _n)
rename (b ll ul pvalue) (estimate min95 max95 p)           // parmest's names -> autodetect
quietly save `_pe'
capture frame drop spk_res
frame create spk_res
frame spk_res: quietly use `_pe'
restore
quietly regress price mpg weight foreign                     // r(table) truth for the fidelity stage
_truth_pre r_res_coef_file
capture noisily sparkta, type(coefplot) results(`_pe') omit(_cons) export("`OUT'/r_res_coef_file.html")
_chk r_res_coef_file `=_rc' "results(file): parmest layout (parm estimate min95 max95 p) autodetected"
quietly regress price mpg weight foreign                     // fresh r(table): sparkta does not preserve r()
_truth_pre r_res_coef_frame
capture noisily sparkta, type(coefplot) results(frame:spk_res, name(parm) b(estimate) ci(min95 max95)) omit(_cons) export("`OUT'/r_res_coef_frame.html")
_chk r_res_coef_frame `=_rc' "results(frame:...) with explicit name()/b()/ci()"
capture noisily sparkta, type(coefplot) results(`_pe') matrix(r(table)) export("`OUT'/r_err_results_matrix.html")
_chk r_err_results_matrix `=_rc' "EXPECT rc!=0: results() with matrix()"
capture noisily sparkta, type(marginsplot) results(`_pe') export("`OUT'/r_err_results_margins.html")
_chk r_err_results_margins `=_rc' "EXPECT rc!=0: results() for marginsplot needs a margins, saving() file (no _margin column)"
capture noisily sparkta, type(coefplot) results(no_such_file_xyz) export("`OUT'/r_err_results_missing.html")
_chk r_err_results_missing `=_rc' "EXPECT rc!=0: results() file missing"
capture frame drop spk_res
sysuse auto, clear

// =============================================================================
// SECTION J (v3.6.0-t2g): results() for marginsplot (margins, saving() layout) and
// margins results surviving colour options (the snapshot/repost pair)
// =============================================================================
display as text _newline "--- J: marginsplot results() + snapshot ---"
sysuse auto, clear
quietly regress price i.rep78 mpg
tempfile _ms1 _ms2 _ms3
quietly margins rep78, saving(`_ms1', replace)
preserve
quietly use `_ms1', clear
describe                                  // t2g fix 3 diagnostic: the real layout/labels of a margins, saving() file
char list                                 // t2g fix 4 diagnostic: what margins stores in the characteristics
restore
_truth_pre r_res_mp_factor
capture noisily sparkta, type(marginsplot) results(`_ms1') export("`OUT'/r_res_mp_factor.html")
_chk r_res_mp_factor `=_rc' "results(): margins rep78 saving() -> 5 factor margins"
quietly margins, at(mpg=(15 20 25 30)) saving(`_ms2', replace)
_truth_pre r_res_mp_at
capture noisily sparkta, type(marginsplot) results(`_ms2') export("`OUT'/r_res_mp_at.html")
_chk r_res_mp_at `=_rc' "results(): continuous at() -> numeric x from the rebuilt r(at)"
quietly margins rep78, at(mpg=(15 25)) saving(`_ms3', replace)
_truth_pre r_res_mp_at_factor
capture noisily sparkta, type(marginsplot) results(`_ms3') export("`OUT'/r_res_mp_at_factor.html")
_chk r_res_mp_at_factor `=_rc' "results(): at() x factor -> series per rep78 level"
quietly margins rep78
_truth_pre r_mp_colors_snapshot
capture noisily sparkta, type(marginsplot) colors(navy) cicolors(ltblue) export("`OUT'/r_mp_colors_snapshot.html")
_chk r_mp_colors_snapshot `=_rc' "margins in memory + colour options (normaliser is rclass): snapshot keeps r()"
quietly regress price i.rep78##i.foreign mpg
quietly margins rep78#foreign
_truth_pre r_mp_cicolors_series
capture noisily sparkta, type(marginsplot) cistyle(area) cicolors(emidblue|erose) export("`OUT'/r_mp_cicolors_series.html")
_chk r_mp_cicolors_series `=_rc' "cicolors(pre|post) per series on marginsplot bands (t2g fix 3)"
quietly margins rep78
_truth_pre r_mp_palette_snapshot
capture noisily sparkta, type(marginsplot) palette(okabe) export("`OUT'/r_mp_palette_snapshot.html")
_chk r_mp_palette_snapshot `=_rc' "margins in memory + palette() (colorpalette is rclass): snapshot keeps r()"
capture noisily sparkta, type(marginsplot) results(`_ms1' `_ms2') export("`OUT'/r_err_results_mp_two.html")
_chk r_err_results_mp_two `=_rc' "EXPECT rc!=0: marginsplot results() takes one source"

// --- t2j fix8x: results() COLUMN MAPPING -- any tidy table, not only margins, saving() files -----
// (a) explicit map: a collapsed table with the user's own column names -> b() se() ci() factors()
tempfile _tidy1 _tidy2 _tidy3
preserve
quietly {
    collapse (mean) mn=price (semean) sdm=price (count) n=price, by(rep78)   // names that are NOT keywords
    gen double lo = mn - invttail(n - 1, 0.025) * sdm
    gen double hi = mn + invttail(n - 1, 0.025) * sdm
    gen double pr = 2 * ttail(n - 1, abs(mn / sdm))
    save `_tidy1', replace
    // (b) autodetect: the keyword names (estimate / stderr / lower / upper / pvalue) with NO map at all
    rename (mn sdm lo hi pr) (estimate stderr lower upper pvalue)
    save `_tidy2', replace
}
restore
capture noisily sparkta, type(marginsplot) results(`_tidy1', b(mn) se(sdm) ci(lo hi) pvalue(pr) factors(rep78)) ///
    title("Mean price by repair record (mapped columns)") export("`OUT'/r_res_mp_map_factor.html")
_chk r_res_mp_map_factor `=_rc' "results() map: b() se() ci() pvalue() factors() on a collapse table"
capture noisily sparkta, type(marginsplot) results(`_tidy2', factors(rep78)) export("`OUT'/r_res_mp_map_autodetect.html")
_chk r_res_mp_map_autodetect `=_rc' "results() map: statistic columns autodetected (estimate/stderr/lower/upper/pvalue)"
// (c) at(): a continuous x column -> numeric x axis; two groups -> factors() series
preserve
quietly {
    gen double mpg5 = 5 * floor(mpg / 5)
    collapse (mean) est=price (semean) se=price (count) n=price, by(mpg5 foreign)
    drop if n < 2
    gen double lo = est - 1.96 * se
    gen double hi = est + 1.96 * se
    save `_tidy3', replace
}
restore
capture noisily sparkta, type(marginsplot) results(`_tidy3', b(est) se(se) ci(lo hi) at(mpg5) factors(foreign)) ///
    connected export("`OUT'/r_res_mp_map_at.html")
_chk r_res_mp_map_at `=_rc' "results() map: at(mpg5) numeric x + factors(foreign) series"
// (d) frame source: the map must run on a COPY (the user's frame keeps its columns)
capture frame drop _spk_tidy
frame create _spk_tidy
frame _spk_tidy: use `_tidy1', clear
capture noisily sparkta, type(marginsplot) results(frame:_spk_tidy, b(mn) se(sdm) ci(lo hi) factors(rep78)) export("`OUT'/r_res_mp_map_frame.html")
_chk r_res_mp_map_frame `=_rc' "results() map from frame:name"
frame _spk_tidy: capture confirm variable _margin
if _rc == 0 display as error "  FAIL  r_res_mp_map_frame  (the user's frame was modified: _margin added)"
else        display as result "  PASS  r_res_mp_map_frame  (user frame untouched)"
frame drop _spk_tidy
// (e) errors: no statistic column at all; no condition column
capture noisily sparkta, type(marginsplot) results(`_tidy1', factors(rep78)) export("`OUT'/r_err_results_mp_nob.html")
_chk r_err_results_mp_nob `=_rc' "EXPECT rc!=0: no _margin, no b(), nothing to autodetect (mn/sdm are not keywords)"
capture noisily sparkta, type(marginsplot) results(`_tidy1', b(mn) se(sdm)) export("`OUT'/r_err_results_mp_nofactor.html")
_chk r_err_results_mp_nofactor `=_rc' "EXPECT rc!=0: no _m1/_at1 and no factors()/at()"

// =============================================================================
// SECTION K (v3.6.0-t2h, batch 2a): PUBLICATION TABLE
//   Exercises sparkta_table_opts.ado (arg 209) + PubTable.java. Each page must
//   carry the estout/esttab publication table, the [Publication|Detailed] toggle
//   (coef) and the Copy/Download dropdowns. verify/latex_check.js (stage 2e)
//   compiles the booktabs LaTeX and structurally checks the Markdown/CSV.
// =============================================================================
sysuse auto, clear
quietly regress price mpg weight foreign
_truth_pre r_tbl_single
capture noisily sparkta, type(coefplot) tstat interval title("Publication table -- single model") export("`OUT'/r_tbl_single.html")
_chk r_tbl_single `=_rc' "pub table: single model, t and CI shown beneath the estimate"

// multi-model with group headings() + indicator (indicate-style) rows
quietly regress price weight
estimates store b1
quietly regress price weight foreign
estimates store b2
quietly regress price weight foreign mpg
estimates store b3
_truth_pre r_tbl_multi_headings_indicate b1 b2 b3
capture noisily sparkta, type(coefplot) estnames(b1 b2 b3) estlabels(Base~Origin~Full) headings(1 "Vehicle size"|2 "Origin") indicators("Controls/No No Yes") export("`OUT'/r_tbl_multi_headings_indicate.html")
_chk r_tbl_multi_headings_indicate `=_rc' "pub table: multi-model with headings() group rows + indicator block"

// eform (odds ratios): pub table exponentiated exactly as the chart
quietly logit foreign mpg weight
_truth_pre r_tbl_eform
capture noisily sparkta, type(coefplot) eform title("Odds ratios") export("`OUT'/r_tbl_eform.html")
_chk r_tbl_eform `=_rc' "pub table: eform (odds ratios)"

// marginsplot pub table (rows = levels/at-values, columns = series)
quietly regress price i.rep78##i.foreign mpg
quietly margins rep78#foreign
_truth_pre r_tbl_margins
capture noisily sparkta, type(marginsplot) title("Predictive margins") export("`OUT'/r_tbl_margins.html")
_chk r_tbl_margins `=_rc' "pub table: marginsplot margins-as-rows, series-as-columns"

// nostars: significance stars suppressed in the table and the legend
quietly regress price mpg weight foreign
_truth_pre r_tbl_nostars
capture noisily sparkta, type(coefplot) nostars export("`OUT'/r_tbl_nostars.html")
_chk r_tbl_nostars `=_rc' "pub table: nostars suppresses the star column and legend"

// notable: publication table suppressed entirely (the chart still renders)
_truth_pre r_tbl_notable
capture noisily sparkta, type(coefplot) notable export("`OUT'/r_tbl_notable.html")
_chk r_tbl_notable `=_rc' "pub table: notable suppresses the whole table block"

// =============================================================================
// SECTION L (v3.6.0-t2i, hardening): correctness regressions from external review
//   VERIFY BY EYE / against r(table): these guard the Astra findings that the
//   green pipeline could not see (they need real Stata data or non-default CI).
// =============================================================================
// levels() OUTER band must be the SECOND value. Was ignored -> defaulted to 95;
// masked because every prior test used levels(90 95). Page must read "90% CI".
sysuse auto, clear
quietly regress price mpg weight foreign
_truth_pre r_hl_levels_80_90
capture noisily sparkta, type(coefplot) levels(80 90) cistyle(band) export("`OUT'/r_hl_levels_80_90.html")
_chk r_hl_levels_80_90 `=_rc' "levels(80 90): OUTER band = 90% (not the 95% default); inner = 80%"

// Missing outcome must NOT listwise-delete across independent series. With price
// missing in a few obs, mpg's points at those x's must remain (old markout
// dropped the whole row). Confirm both series keep all in-range points by eye.
sysuse auto, clear
replace price = . in 5/9
capture noisily sparkta price mpg, type(line) export("`OUT'/r_hl_missing_nolistwise.html")
_chk r_hl_missing_noListwise `=_rc' "missing price rows kept in sample; mpg series not thinned by listwise deletion"

// cilevel edge: 99 valid; 99.9 must ERROR (it rounds to an invalid 100).
sysuse auto, clear
quietly regress price mpg
capture noisily sparkta, type(coefplot) cilevel(99) export("`OUT'/r_hl_cilevel_99.html")
_chk r_hl_cilevel_99 `=_rc' "cilevel(99) valid"
capture noisily sparkta, type(coefplot) cilevel(99.9) export("`OUT'/r_err_cilevel_999.html")
_chk r_err_cilevel_999 `=_rc' "EXPECT rc!=0: cilevel(99.9) rounds to invalid 100"

// SECTION D: error handling -- these SHOULD fail with a clear message (rc != 0)
// =============================================================================
sysuse auto, clear                        // t2e fix 3: Section I leaves a synthetic panel in memory
capture noisily sparkta price, type(marginsplot) export("`OUT'/r_err_marginsplot_with_varlist.html")
_chk r_err_marginsplot_with_varlist `=_rc' "EXPECT rc!=0: varlist not allowed"

quietly regress price mpg
capture noisily sparkta, type(coefplot) cistyle(bogus) export("`OUT'/r_err_bad_cistyle.html")
_chk r_err_bad_cistyle `=_rc' "EXPECT rc!=0: invalid cistyle"

capture noisily sparkta, type(marginsplot) eform export("`OUT'/r_err_mp_eform.html")
_chk r_err_mp_eform `=_rc' "EXPECT rc!=0: eform invalid for marginsplot"

// =============================================================================
file close `mf'
// ---- summary: count outcomes from the manifest (expected failures count as pass) ----
preserve
quietly import delimited using "`MAN'", clear varnames(1) stringcols(_all)
quietly gen byte ok = (substr(case,1,6)=="r_err_" & rc!="0") | (substr(case,1,6)!="r_err_" & rc=="0" & file_exists=="1")
quietly count
local _n = r(N)
quietly count if ok
local _ok = r(N)
display as text _newline "=== SUITE SUMMARY: `_ok' / `_n' cases as intended" _c
if `_ok' < `_n' {
    display as error " -- UNEXPECTED:"
    list case rc file_exists if !ok, noobs clean
}
else display as result " -- CLEAN RUN"
restore
log close _sparkta_suite
display as text _newline "=== manifest: `MAN' ==="
type "`MAN'"
display as text _newline "Now zip the test_out folder and share it for js_check review."
