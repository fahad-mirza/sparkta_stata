// =============================================================================
// test_byfilter_v360.do -- Sparkta v3.6.0-t2j fix9a: by() + filters()/sliders() on
//                          EVERY chart type (roadmap item 3, 2026-09-13)
// =============================================================================
// Version: 1.0 (fix9a). ASCII only.
//
// WHAT IT DOES
//   Writes one by()-panel page per case into ./test_out_byfilter/ (each page has at least
//   one filters() dropdown, some a sliders() range) and never stops on an error: every
//   case is wrapped in capture noisily and its return code goes to _manifest.csv.
//   The cases mirror the offline sweep verify/harness/HarnessByFilter.java (BF01-BF53)
//   on the REAL auto.dta (74 obs, 5 with rep78 missing -> markout) plus nlsw88 cases with
//   value-labelled and many-level filter variables, plus the expected-error cases.
//
// AFTER RUNNING -- two review prongs:
//   1. Static/automatic (review side, no Stata): zip test_out_byfilter/ and share it. I run
//        node verify/byfilter_probe.js test_out_byfilter --embedded
//      which opens every page headless, selects EVERY dropdown value (and a slider range),
//      and compares each panel's datasets with an independent recomputation from the page's
//      own embedded columns (means/sums/medians per var x group, box medians, cibar/ciline
//      means, histogram bins, pie shares, 100% shares, scatter point counts, n badges,
//      JS errors).
//   2. By eye (your side, 5 minutes): open the pages marked "LOOK" below in Chrome and pick
//      a dropdown value; every panel must redraw (bars/lines/points/boxes/slices/bins), the
//      panel "n = ..." badge and the obs count next to the dropdown must change, and nothing
//      may go blank or throw (F12 console). The expected picture is written next to each case.
//
// Requires: Stata 17+, sparkta v3.6.0-t2j fix9a installed (build.bat or the shipped jars,
//           RESTART Stata after a jar change), sysuse auto / nlsw88.
// Naming:   r_bf_<nn>_<desc>.html ; r_bf_err_* are EXPECTED failures (rc != 0 = PASS).
// =============================================================================
version 17
clear all
set more off

// ---- output folder + manifest ------------------------------------------------
capture mkdir test_out_byfilter
local OUT "`c(pwd)'/test_out_byfilter"
local MAN "`OUT'/_manifest.csv"
discard   // drop any previously loaded sparkta so the installed ado (and its subroutines) load
capture log close _sparkta_bf
log using "`OUT'/_run.log", replace text name(_sparkta_bf)
tempname mf
file open `mf' using "`MAN'", write replace
file write `mf' "case,rc,file_exists,note" _n
global SPK_OUT "`OUT'"
global SPK_MF  "`mf'"

capture program drop _chk
program _chk
    // usage: _chk casename rc [note]   -- r_bf_err_* cases are expected to FAIL (rc != 0)
    args name rc note
    local f "$SPK_OUT/`name'.html"
    local ex = fileexists("`f'")
    file write $SPK_MF `"`name',`rc',`ex',`note'"' _n
    local expfail = (substr("`name'", 1, 9) == "r_bf_err_")
    if `expfail' & `rc' != 0     display as result "  PASS  `name'  (expected failure, rc=`rc')"
    else if `expfail'             display as error  "  FAIL  `name'  (should have failed but rc=0)"
    else if `rc' == 0 & `ex'     display as result "  PASS  `name'"
    else if `rc' == 0            display as error  "  FAIL  `name'  (rc=0 but no file)"
    else                          display as error  "  FAIL  `name'  rc=`rc'"
end

which sparkta      // header must start "v3.6.0-t2j fix9a"

// =============================================================================
// PART A -- auto.dta, by(foreign) or by(rep78); filters(rep78) or filters(mpg)
// (mpg has 21 levels: the dropdown is long on purpose; several values leave a panel EMPTY,
//  which must draw as an empty panel with "n = 0", never an error)
// =============================================================================
sysuse auto, clear

// --- bar family -----------------------------------------------------------------------
capture noisily sparkta price, type(bar) by(foreign) filters(rep78) title("BF01 bar 1var") export("`OUT'/r_bf_01_bar_by.html")
_chk r_bf_01_bar_by `=_rc' "LOOK: one bar per panel; rep78=5 -> Domestic panel empty (n = 0), Foreign bar = mean of 9 cars"
capture noisily sparkta price, type(bar) over(rep78) by(foreign) filters(mpg) title("BF02 bar over") export("`OUT'/r_bf_02_bar_over_by.html")
_chk r_bf_02_bar_over_by `=_rc' "5 rep78 positions in EVERY panel (Foreign has no 1-2: gaps stay); mpg=22 -> few bars"
capture noisily sparkta price mpg, type(bar) by(foreign) filters(rep78) title("BF03 bar 2var") export("`OUT'/r_bf_03_bar_2var_by.html")
_chk r_bf_03_bar_2var_by `=_rc' "LOOK: TWO bars (Price, Mileage) per panel after any filter change (fix8z drew one)"
capture noisily sparkta price mpg, type(bar) over(rep78) by(foreign) filters(mpg) title("BF04 bar 2var over") export("`OUT'/r_bf_04_bar_2var_over_by.html")
_chk r_bf_04_bar_2var_over_by `=_rc' "LOOK: 2 series x 5 groups per panel keep their shape after filtering (fix8z collapsed to 1 group)"
capture noisily sparkta price, type(hbar) over(rep78) by(foreign) filters(mpg) title("BF05 hbar over") export("`OUT'/r_bf_05_hbar_over_by.html")
_chk r_bf_05_hbar_over_by `=_rc' "horizontal bars, same as BF02"
capture noisily sparkta price, type(bar) stat(sum) over(rep78) by(foreign) filters(mpg) title("BF06 stat(sum)") export("`OUT'/r_bf_06_bar_sum_over_by.html")
_chk r_bf_06_bar_sum_over_by `=_rc' "sums shrink as the filter narrows"
capture noisily sparkta price mpg, type(bar) stat(median) by(foreign) filters(rep78) title("BF07 stat(median) 2var") export("`OUT'/r_bf_07_bar_median_2var_by.html")
_chk r_bf_07_bar_median_2var_by `=_rc' "two median bars per panel"

// --- line / area ----------------------------------------------------------------------
capture noisily sparkta price mpg, type(line) over(rep78) by(foreign) filters(mpg) title("BF08 line 2var over") export("`OUT'/r_bf_08_line_2var_over_by.html")
_chk r_bf_08_line_2var_over_by `=_rc' "LOOK: two lines over 5 x positions per panel survive a filter change"
capture noisily sparkta price, type(line) by(foreign) filters(rep78) title("BF09 line 1var") export("`OUT'/r_bf_09_line_by.html")
_chk r_bf_09_line_by `=_rc' "single point per panel"
capture noisily sparkta price mpg, type(area) over(rep78) by(foreign) filters(mpg) title("BF10 area 2var over") export("`OUT'/r_bf_10_area_2var_over_by.html")
_chk r_bf_10_area_2var_over_by `=_rc' "two filled areas per panel"

// --- stacked family -------------------------------------------------------------------
capture noisily sparkta price mpg, type(stackedbar) over(rep78) by(foreign) filters(mpg) title("BF11 stackedbar") export("`OUT'/r_bf_11_stackedbar_by.html")
_chk r_bf_11_stackedbar_by `=_rc' "LOOK: stacked segments per rep78 position keep stacking after a filter change"
capture noisily sparkta price mpg, type(stackedhbar) over(rep78) by(foreign) filters(mpg) title("BF12 stackedhbar") export("`OUT'/r_bf_12_stackedhbar_by.html")
_chk r_bf_12_stackedhbar_by `=_rc' "horizontal stacks"
capture noisily sparkta price mpg, type(stackedarea) over(rep78) by(foreign) filters(mpg) title("BF13 stackedarea") export("`OUT'/r_bf_13_stackedarea_by.html")
_chk r_bf_13_stackedarea_by `=_rc' "stacked areas"
capture noisily sparkta price mpg, type(stackedline) over(rep78) by(foreign) filters(mpg) title("BF14 stackedline") export("`OUT'/r_bf_14_stackedline_by.html")
_chk r_bf_14_stackedline_by `=_rc' "stacked lines"
capture noisily sparkta price mpg, type(stackedbar100) over(rep78) by(foreign) filters(mpg) title("BF15 stackedbar100") export("`OUT'/r_bf_15_stack100_by.html")
_chk r_bf_15_stack100_by `=_rc' "LOOK: every column (Price, Mileage) still totals 100% after filtering; Foreign shows only its groups"
capture noisily sparkta price mpg, type(stackedhbar100) over(rep78) by(foreign) filters(mpg) title("BF16 stackedhbar100") export("`OUT'/r_bf_16_stackh100_by.html")
_chk r_bf_16_stackh100_by `=_rc' "horizontal 100% stacks"

// --- scatter / bubble -----------------------------------------------------------------
capture noisily sparkta price weight, type(scatter) by(foreign) filters(rep78) title("BF17 scatter") export("`OUT'/r_bf_17_scatter_by.html")
_chk r_bf_17_scatter_by `=_rc' "points thin out per panel; rep78=5 -> Domestic panel empty"
capture noisily sparkta price weight, type(scatter) over(rep78) by(foreign) filters(mpg) title("BF18 scatter over") export("`OUT'/r_bf_18_scatter_over_by.html")
_chk r_bf_18_scatter_over_by `=_rc' "LOOK: Foreign panel has series 3,4,5 only -- after mpg=All the points stay with THEIR series colour (fix8z shifted them by two groups)"
capture noisily sparkta price weight, type(scatter) fit(lfit) by(foreign) filters(rep78) title("BF19 scatter lfit") export("`OUT'/r_bf_19_scatter_fit_by.html")
_chk r_bf_19_scatter_fit_by `=_rc' "LOOK: the fit line MOVES with the filtered points in each panel (fix8z left it frozen)"
capture noisily sparkta price weight mpg, type(bubble) by(foreign) filters(rep78) title("BF20 bubble") export("`OUT'/r_bf_20_bubble_by.html")
_chk r_bf_20_bubble_by `=_rc' "bubbles keep their radius scale on filter"

// --- pie / donut (over()+by() is rejected for pie -> multi-var mode only) -------------
capture noisily sparkta price mpg, type(pie) by(foreign) filters(rep78) title("BF21 pie 2var") export("`OUT'/r_bf_21_pie_2var_by.html")
_chk r_bf_21_pie_2var_by `=_rc' "LOOK: two slices per panel after filtering (fix8z left one); hover: each panel shows ITS OWN raw sums (fix8z showed the last panel's)"
capture noisily sparkta price mpg, type(donut) by(foreign) filters(rep78) title("BF22 donut 2var") export("`OUT'/r_bf_22_donut_2var_by.html")
_chk r_bf_22_donut_2var_by `=_rc' "same as BF21 with a hole"

// --- CI charts (over() is mandatory) --------------------------------------------------
capture noisily sparkta price, type(cibar) over(rep78) by(foreign) filters(mpg) title("BF23 cibar") export("`OUT'/r_bf_23_cibar_over_by.html")
_chk r_bf_23_cibar_over_by `=_rc' "LOOK: bars + whiskers redraw on filter, groups with n<2 vanish, NO console error (fix8z threw inside Chart.js and nothing updated)"
capture noisily sparkta price, type(ciline) over(rep78) by(foreign) filters(mpg) title("BF24 ciline") export("`OUT'/r_bf_24_ciline_over_by.html")
_chk r_bf_24_ciline_over_by `=_rc' "mean line + band redraw per panel"

// --- histogram ------------------------------------------------------------------------
capture noisily sparkta price, type(histogram) by(foreign) filters(rep78) title("BF25 histogram") export("`OUT'/r_bf_25_hist_by.html")
_chk r_bf_25_hist_by `=_rc' "LOOK: bins recount inside each panel's own edges; hover shows the filtered per-bin n (fix8z drew one bar)"

// --- box / violin ---------------------------------------------------------------------
capture noisily sparkta price, type(boxplot) by(foreign) filters(rep78) title("BF26 box") export("`OUT'/r_bf_26_box_by.html")
_chk r_bf_26_box_by `=_rc' "one box per panel recomputed"
capture noisily sparkta price, type(boxplot) over(rep78) by(foreign) filters(mpg) title("BF27 box over") export("`OUT'/r_bf_27_box_over_by.html")
_chk r_bf_27_box_over_by `=_rc' "LOOK: all 5 group boxes per panel stay after a filter change (fix8z kept group 1 only)"
capture noisily sparkta price, type(hbox) over(rep78) by(foreign) filters(mpg) title("BF28 hbox over") export("`OUT'/r_bf_28_hbox_over_by.html")
_chk r_bf_28_hbox_over_by `=_rc' "horizontal boxes"
capture noisily sparkta price, type(violin) by(foreign) filters(rep78) title("BF29 violin") export("`OUT'/r_bf_29_violin_by.html")
_chk r_bf_29_violin_by `=_rc' "violin reshapes (KDE) per panel"
capture noisily sparkta price, type(violin) over(rep78) by(foreign) filters(mpg) title("BF30 violin over") export("`OUT'/r_bf_30_violin_over_by.html")
_chk r_bf_30_violin_over_by `=_rc' "one violin per group per panel"
capture noisily sparkta price, type(hviolin) over(rep78) by(foreign) filters(mpg) title("BF31 hviolin over") export("`OUT'/r_bf_31_hviolin_over_by.html")
_chk r_bf_31_hviolin_over_by `=_rc' "horizontal violins"
capture noisily sparkta price mpg, type(boxplot) by(foreign) filters(rep78) title("BF32 box 2var") export("`OUT'/r_bf_32_box_2var_by.html")
_chk r_bf_32_box_2var_by `=_rc' "LOOK: two boxes (Price, Mileage) per panel after filtering"

// --- filter mechanics: sliders, showmissing, noallfilter, filter on the by() var --------
capture noisily sparkta price, type(bar) by(foreign) sliders(weight) title("BF33 sliders") export("`OUT'/r_bf_33_bar_by_slider.html")
_chk r_bf_33_bar_by_slider `=_rc' "LOOK: drag the weight range: both panels and badges follow"
capture noisily sparkta price, type(bar) over(rep78) by(foreign) filters(mpg) sliders(weight) title("BF34 filter + slider") export("`OUT'/r_bf_34_bar_over_by_fs.html")
_chk r_bf_34_bar_over_by_fs `=_rc' "dropdown AND slider combine (AND)"
capture noisily sparkta price, type(bar) by(rep78) filters(foreign) title("BF35 by(rep78)") export("`OUT'/r_bf_35_bar_byrep78.html")
_chk r_bf_35_bar_byrep78 `=_rc' "5 panels (rep78 missing dropped); filter Foreign -> panels 1-2 empty"
capture noisily sparkta price, type(bar) by(rep78, showmissing) filters(foreign) title("BF36 showmissing") export("`OUT'/r_bf_36_bar_byrep78_sm.html")
_chk r_bf_36_bar_byrep78_sm `=_rc' "6 panels, the (missing) panel LAST, it filters too"
capture noisily sparkta price, type(bar) by(foreign) filters(rep78) noallfilter title("BF37 noallfilter") export("`OUT'/r_bf_37_bar_by_noall.html")
_chk r_bf_37_bar_by_noall `=_rc' "no All entry; the page opens already filtered on rep78=1"
capture noisily sparkta price, type(bar) over(foreign) by(rep78) filters(rep78) title("BF38 filter = by var") export("`OUT'/r_bf_38_bar_filter_is_by.html")
_chk r_bf_38_bar_filter_is_by `=_rc' "filtering on the by() variable empties every other panel (n = 0), no error"
capture noisily sparkta price weight, type(scatter) by(foreign) filters(rep78 mpg) title("BF39 two filters") export("`OUT'/r_bf_39_scatter_by_2filters.html")
_chk r_bf_39_scatter_by_2filters `=_rc' "two dropdowns combine (AND)"

// --- second sweep: remaining layout variants ------------------------------------------
capture noisily sparkta price mpg, type(violin) by(foreign) filters(rep78) title("BF40 violin 2var") export("`OUT'/r_bf_40_violin_2var_by.html")
_chk r_bf_40_violin_2var_by `=_rc' "KNOWN GAP (pre-existing, single page too): only the FIRST variable's violin refreshes on filter"
capture noisily sparkta price mpg, type(bar) stat(sum) over(rep78) by(foreign) filters(mpg) title("BF41 2var sum") export("`OUT'/r_bf_41_bar_2var_over_sum.html")
_chk r_bf_41_bar_2var_over_sum `=_rc' "two sum series per panel"
capture noisily sparkta price mpg, type(cibar) over(rep78) by(foreign) filters(mpg) title("BF42 cibar 2var") export("`OUT'/r_bf_42_cibar_2var_over_by.html")
_chk r_bf_42_cibar_2var_over_by `=_rc' "two CI bar series per group"
capture noisily sparkta price mpg, type(ciline) over(rep78) by(foreign) filters(mpg) title("BF43 ciline 2var") export("`OUT'/r_bf_43_ciline_2var_over_by.html")
_chk r_bf_43_ciline_2var_over_by `=_rc' "two mean lines with bands"
capture noisily sparkta price weight, type(scatter) fit(lfit) over(rep78) by(foreign) filters(mpg) title("BF44 over + lfit") export("`OUT'/r_bf_44_scatter_over_fit_by.html")
_chk r_bf_44_scatter_over_fit_by `=_rc' "one fit line per group per panel, each moves with its points"
capture noisily sparkta price weight, type(scatter) fit(qfit) fitci by(foreign) filters(rep78) title("BF45 qfit fitci") export("`OUT'/r_bf_45_scatter_fitci_by.html")
_chk r_bf_45_scatter_fitci_by `=_rc' "LOOK: the CI band moves with the fit after a filter change"
capture noisily sparkta price, type(histogram) histtype(frequency) by(foreign) filters(rep78) title("BF46 hist frequency") export("`OUT'/r_bf_46_hist_freq_by.html")
_chk r_bf_46_hist_freq_by `=_rc' "bin counts are integers"
capture noisily sparkta price mpg, type(pie) stat(sum) by(foreign) filters(rep78) title("BF47 pie stat(sum)") export("`OUT'/r_bf_47_pie_sum_by.html")
_chk r_bf_47_pie_sum_by `=_rc' "slices are raw sums"
capture noisily sparkta price weight mpg, type(bubble) over(rep78) by(foreign) filters(mpg) title("BF48 bubble over") export("`OUT'/r_bf_48_bubble_over_by.html")
_chk r_bf_48_bubble_over_by `=_rc' "bubbles per group per panel"
capture noisily sparkta price mpg, type(stackedbar100) by(foreign) filters(rep78) title("BF49 stack100 no over") export("`OUT'/r_bf_49_stack100_noover_by.html")
_chk r_bf_49_stack100_noover_by `=_rc' "FOLLOW-UP: without over() there is nothing to stack -- draws plain mean bars (decision pending)"
capture noisily sparkta price mpg, type(line) by(foreign) filters(rep78) title("BF50 line 2var") export("`OUT'/r_bf_50_line_2var_by.html")
_chk r_bf_50_line_2var_by `=_rc' "two points per panel"
capture noisily sparkta price mpg, type(hbar) by(foreign) filters(rep78) title("BF51 hbar 2var") export("`OUT'/r_bf_51_hbar_2var_by.html")
_chk r_bf_51_hbar_2var_by `=_rc' "two horizontal bars per panel"
capture noisily sparkta price, type(bar) stat(count) over(rep78) by(foreign) filters(mpg) title("BF52 stat(count)") export("`OUT'/r_bf_52_bar_count_over_by.html")
_chk r_bf_52_bar_count_over_by `=_rc' "counts per group; badges equal the sum of the bars"
capture noisily sparkta price mpg, type(stackedbar) by(foreign) filters(rep78) title("BF53 stackedbar no over") export("`OUT'/r_bf_53_stackedbar_noover_by.html")
_chk r_bf_53_stackedbar_noover_by `=_rc' "two bars per panel (nothing to stack across without over())"

// =============================================================================
// PART B -- nlsw88: value-labelled filter/by variables, many-level dropdown, 2,246 obs
// =============================================================================
sysuse nlsw88, clear
capture noisily sparkta wage, type(bar) over(race) by(married) filters(collgrad) sliders(age) title("NB01 wage by married") export("`OUT'/r_bf_n01_bar_over_by_fs.html")
_chk r_bf_n01_bar_over_by_fs `=_rc' "value-labelled groups (white/black/other) + dropdown (college grad) + age slider"
capture noisily sparkta wage tenure, type(scatter) by(union) filters(industry) title("NB02 scatter by union") export("`OUT'/r_bf_n02_scatter_by_industry.html")
_chk r_bf_n02_scatter_by_industry `=_rc' "12-level dropdown; union missing dropped (markout)"
capture noisily sparkta wage, type(boxplot) over(collgrad) by(race) filters(south) title("NB03 box by race") export("`OUT'/r_bf_n03_box_over_by.html")
_chk r_bf_n03_box_over_by `=_rc' "3 panels x 2 boxes; south filter"
capture noisily sparkta wage, type(histogram) by(collgrad) filters(race) title("NB04 hist by collgrad") export("`OUT'/r_bf_n04_hist_by.html")
_chk r_bf_n04_hist_by `=_rc' "bins recount per panel"
capture noisily sparkta wage, type(cibar) over(race) by(collgrad) filters(married) title("NB05 cibar by collgrad") export("`OUT'/r_bf_n05_cibar_over_by.html")
_chk r_bf_n05_cibar_over_by `=_rc' "3 CI bars per panel"
capture noisily sparkta wage hours, type(pie) by(race) filters(married) title("NB06 pie by race") export("`OUT'/r_bf_n06_pie_by.html")
_chk r_bf_n06_pie_by `=_rc' "3 panels x 2 slices; hover each panel: raw sums differ by panel"
capture noisily sparkta wage, type(violin) over(collgrad) by(race) filters(union) title("NB07 violin by race") export("`OUT'/r_bf_n07_violin_over_by.html")
_chk r_bf_n07_violin_over_by `=_rc' "violins reshape per panel"
capture noisily sparkta wage ttl_exp, type(stackedbar100) over(race) by(married) filters(collgrad) title("NB08 stack100 by married") export("`OUT'/r_bf_n08_stack100_by.html")
_chk r_bf_n08_stack100_by `=_rc' "columns total 100% per panel"

// =============================================================================
// PART C -- expected failures (rc != 0 = PASS) and one observation
// =============================================================================
sysuse auto, clear
capture noisily sparkta price, type(pie) over(rep78) by(foreign) filters(mpg) export("`OUT'/r_bf_err_pie_over_by.html")
_chk r_bf_err_pie_over_by `=_rc' "pie rejects over()+by() (rc 198)"
capture noisily sparkta price, type(cibar) by(foreign) filters(rep78) export("`OUT'/r_bf_err_cibar_no_over.html")
_chk r_bf_err_cibar_no_over `=_rc' "cibar needs over() (rc 198)"
capture noisily sparkta price, type(bar) by(foreign) filters(nosuchvar) export("`OUT'/r_bf_err_filter_novar.html")
_chk r_bf_err_filter_novar `=_rc' "filters(): variable not found"
capture noisily sparkta price, type(bar) by(foreign) sliders(make) export("`OUT'/r_bf_err_slider_string.html")
_chk r_bf_err_slider_string `=_rc' "sliders(): string variable rejected"
sysuse nlsw88, clear
capture noisily sparkta wage, type(bar) by(race) filters(idcode) export("`OUT'/r_bf_err_filter_500.html")
_chk r_bf_err_filter_500 `=_rc' "filters(): idcode has 2,246 levels (max 500)"
// OBSERVATION (not a pass/fail): post-estimation types read matrices, not observations --
// what does by()/filters() do there? Expect either a clean error or a silent ignore; report which.
sysuse auto, clear
regress price mpg weight foreign
capture noisily sparkta, type(coefplot) by(foreign) filters(rep78) export("`OUT'/r_bf_obs_coefplot_by.html")
display as text "  OBSERVE r_bf_obs_coefplot_by: rc=`=_rc' (report the message if any; a coefplot with by()/filters() has no defined meaning yet)"

// ---- wrap up -----------------------------------------------------------------
file close `mf'
display as text _n "Manifest: `MAN'"
display as text "Zip test_out_byfilter/ and share it; review side runs: node verify/byfilter_probe.js test_out_byfilter --embedded"
log close _sparkta_bf
