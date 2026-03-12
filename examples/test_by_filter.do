*! sparkta v2.6.2 -- by() + filter() interaction test
*! Purpose: Verify that filter dropdowns update all by()-panels correctly
*! Dataset: auto (built-in)
*! Version: 1.1 | 2026-03-07
*
* NOTE: over() and by() are mutually exclusive in sparkta.
*   over() = groups within a single chart (one chart, multiple series)
*   by()   = separate panels (one chart per group)
*   These tests use by() for panels and let each panel show one variable.
*   over() within each panel is not needed for the filter test.
*
* HOW TO USE:
*   Run each block and verify using the VERIFY instructions.
*   Every chart exports to ~/Desktop with prefix byf_ for easy identification.
*   Open in browser. Change the filter dropdown. Watch all panels update.
*
* PASS criterion: ALL panels update simultaneously when dropdown changes.
* FAIL criterion: panels do not change, or only some panels change.
*
* SECTIONS:
*   A. Core: bar + by() + filter()
*   B. Histogram + by() + filter() -- the originally failing combo
*   C. Line/scatter + by() + filter()
*   D. Two filters + by()
*   E. Non-regression: by() alone (no filter) must still work
*   F. Non-regression: filter() alone (no by()) must still work
* =============================================================

sysuse auto, clear

di ""
di "============================================================"
di " SECTION A: bar + by() + filter()"
di "============================================================"

* A1. Two panels split by foreign, filtered by rep78
* VERIFY: select rep78==1 from dropdown -> both panels shrink to rep78==1 obs only
*         select rep78==3             -> both panels show rep78==3 obs
*         select "All rep78"          -> both panels restore full group data
sparkta price, type(bar) by(foreign) ///
    layout(grid) filter(rep78) ///
    title("A1: bar by(foreign) filter(rep78)") ///
    note("PASS: BOTH panels update on dropdown change. FAIL: panels frozen.") ///
    export("~/Desktop/byf_A1_bar_by_filter.html")

* A2. Five panels split by rep78, filtered by foreign
* VERIFY: select "Domestic" -> all five panels show domestic cars only
*         select "Foreign"  -> all five panels show foreign cars only
sparkta price, type(bar) by(rep78) ///
    layout(grid) filter(foreign) ///
    title("A2: bar by(rep78) filter(foreign) -- 5 panels all update") ///
    note("PASS: all 5 panels update. FAIL: some panels frozen.") ///
    export("~/Desktop/byf_A2_bar_5panels_filter.html")

* A3. Multiple variables per panel, filtered
* VERIFY: filter change updates bar heights for all variables in all panels
sparkta price weight mpg, type(bar) stat(mean) by(foreign) ///
    layout(grid) filter(rep78) ///
    title("A3: bar 3vars by(foreign) filter(rep78)") ///
    note("PASS: all bars update in both panels.") ///
    export("~/Desktop/byf_A3_bar_multvar_by_filter.html")

di ""
di "============================================================"
di " SECTION B: histogram + by() + filter() -- the reported bug"
di "============================================================"

* B1. The exact failing case
* VERIFY: (1) both panels show tooltip on hover
*         (2) change filter -> BOTH panels re-bin and update simultaneously
*         (3) tooltip in each updated panel still shows per-bin count
sparkta price, type(histogram) by(foreign) filter(rep78) ///
    title("B1: histogram by(foreign) filter(rep78)") ///
    note("PASS: both panels re-bin on filter change. FAIL: panels do not update.") ///
    export("~/Desktop/byf_B1_hist_by_filter.html")

* B2. Five histogram panels
sparkta price, type(histogram) by(rep78) filter(foreign) ///
    layout(grid) ///
    title("B2: histogram by(rep78) filter(foreign) -- 5 histogram panels") ///
    note("PASS: all 5 histograms re-bin. FAIL: frozen after filter change.") ///
    export("~/Desktop/byf_B2_hist_5panels_filter.html")

* B3. Per-bin count correct after filter change
* VERIFY: hover a bar with "All" selected -> note "Obs in bin: X"
*         change filter to Domestic        -> same bar shows smaller X
*         change filter to Foreign         -> X smallest (only 22 foreign cars)
sparkta price, type(histogram) bins(5) ///
    by(foreign) filter(rep78) ///
    title("B3: histogram bins(5) -- per-bin counts correct after filter change") ///
    note("PASS: obs-in-bin count changes correctly. FAIL: count does not update.") ///
    export("~/Desktop/byf_B3_hist_bincounts_filter.html")

* B4. Histogram frequency mode
sparkta price, type(histogram) histtype(frequency) ///
    by(foreign) filter(rep78) ///
    title("B4: histogram frequency by(foreign) filter(rep78)") ///
    note("PASS: frequency bars re-bin on filter change.") ///
    export("~/Desktop/byf_B4_hist_freq_by_filter.html")

di ""
di "============================================================"
di " SECTION C: line and scatter + by() + filter()"
di "============================================================"

* C1. Line chart -- mean by value of by-variable, filtered
sparkta price weight, type(line) by(foreign) ///
    layout(grid) filter(rep78) ///
    title("C1: line 2vars by(foreign) filter(rep78)") ///
    note("PASS: both line panels update on filter change.") ///
    export("~/Desktop/byf_C1_line_by_filter.html")

* C2. Scatter -- each panel is one foreign group, filtered by rep78
sparkta price weight, type(scatter) by(foreign) ///
    layout(grid) filter(rep78) ///
    title("C2: scatter by(foreign) filter(rep78)") ///
    note("PASS: both scatter panels update.") ///
    export("~/Desktop/byf_C2_scatter_by_filter.html")

di ""
di "============================================================"
di " SECTION D: two filters + by()"
di "============================================================"

* D1. Two filters + by()
* VERIFY: change filter1 (rep78) alone -> all panels respond
*         change filter2 (foreign) alone -> all panels respond
*         change both -> panels show intersection
sparkta price weight, type(bar) by(rep78) ///
    layout(grid) filter(foreign) filter2(rep78) ///
    title("D1: bar by(rep78) filter(foreign) filter2(rep78)") ///
    note("PASS: all panels update for any dropdown combination.") ///
    export("~/Desktop/byf_D1_bar_by_2filters.html")

di ""
di "============================================================"
di " SECTION E: non-regression -- by() alone (no filter)"
di "============================================================"

* E1. by() only: no filter bar, panels static -- must still render correctly
sparkta price, type(bar) by(foreign) layout(grid) ///
    title("E1: by() no filter -- non-regression") ///
    note("PASS: panels render, no filter bar shown.") ///
    export("~/Desktop/byf_E1_by_nofilter.html")

* E2. Histogram by() only
sparkta price, type(histogram) by(foreign) layout(grid) ///
    title("E2: histogram by() no filter -- non-regression") ///
    note("PASS: both histogram panels render with tooltips. No filter bar.") ///
    export("~/Desktop/byf_E2_hist_by_nofilter.html")

di ""
di "============================================================"
di " SECTION F: non-regression -- filter() alone (no by())"
di "============================================================"

* F1. filter() only: single-chart filter path must be unchanged
sparkta price weight, type(bar) filter(foreign) ///
    title("F1: filter() no by() -- non-regression") ///
    note("PASS: single chart updates on filter change as before.") ///
    export("~/Desktop/byf_F1_filter_noby.html")

* F2. Histogram filter() only
sparkta price, type(histogram) filter(foreign) ///
    title("F2: histogram filter() no by() -- non-regression") ///
    note("PASS: single histogram re-bins, per-bin count correct.") ///
    export("~/Desktop/byf_F2_hist_filter_noby.html")

di ""
di "============================================================"
di " All test charts exported to ~/Desktop with prefix byf_"
di ""
di " CHECKLIST:"
di "   A1-A3: bar panels ALL update on filter change"
di "   B1-B4: histogram panels re-bin + per-bin count correct after change"
di "   C1-C2: line/scatter panels update"
di "   D1:    two dropdowns both drive all panels"
di "   E1-E2: by() alone unchanged (no filter bar shown)"
di "   F1-F2: filter() alone unchanged (single chart updates)"
di "============================================================"
