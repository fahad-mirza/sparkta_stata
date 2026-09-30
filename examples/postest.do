*! sparkta example: postest.do
*! Demonstrates: coefplot, marginsplot, eventstudy, the publication table, saveas() exports
*! Datasets: auto (built-in) and a synthetic staggered panel (no download needed)
*! Version: 1.0 | 2026-09-14 | sparkta 3.6.0 (docs item 5)
* Run with: do postest.do
* Every command below is one the release suite (tests/test_release_v360.do) or the help
* file already exercises; the syntax is 3.6.0 as shipped.
* Charts open in the browser unless export() is given; the saveas() cases need
* Microsoft Edge or Google Chrome (sparkta, findbrowser shows what would be used).

version 17
clear all
set more off
capture mkdir postest_out
local out "postest_out"

* ============================================================
* COEFPLOT -- markers with confidence intervals, reference line at 0
* No varlist: the chart reads e(b), e(V) and e(df_r) of the last estimation.
* ============================================================
sysuse auto, clear
regress price mpg weight foreign

sparkta, type(coefplot) title("Price regression")                 // defaults: _cons dropped, 95% whiskers
sparkta, type(coefplot) levels(90 95) cistyle(band)                // two nested intervals
sparkta, type(coefplot) show(mpg weight) ///
    coeflabels("mpg/Miles per gallon" "weight/Weight in lbs") coefsort(abs)
sparkta, type(coefplot) coeflabels(mpg = "Miles per gallon")     // Stata-style alias, one label
sparkta, type(coefplot) cistyle(band) connected coefsort(abs) tstat  // t statistics in the table

* eform: odds ratios after logit (estimate and bounds are exponentiated together)
logit foreign mpg weight
sparkta, type(coefplot) eform title("Odds ratios")

* several models side by side (one colour each, one table column per model)
regress price mpg weight foreign
estimates store full
regress price mpg weight
estimates store reduced
sparkta, type(coefplot) estnames(full reduced) estlabels(Full~Reduced) ///
    title("Two specifications")

* ============================================================
* MARGINSPLOT -- what Stata's marginsplot would draw, after margins
* r(table) is snapshotted and restored, so the command can be repeated.
* ============================================================
regress price i.rep78 mpg
margins rep78
sparkta, type(marginsplot) cistyle(area whisker) connected title("Mean price by repair record")

regress price i.foreign##c.mpg
margins foreign, at(mpg=(15 20 25 30 35))
sparkta, type(marginsplot) over(foreign) cistyle(area) title("Price by mpg, domestic vs foreign")

* ============================================================
* EVENTSTUDY -- leads and lags from a regression on a synthetic staggered panel
* Coefficient names carry the period (lead5 ... lead2, lag0 ... lag5); show() keeps
* only those, leaving the fixed effects out. The omitted period (-1) is found
* automatically and drawn as a hollow marker at 0.
* ============================================================
clear
set seed 20260914
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

sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    cistyle(band) connected pexline(0) title("Event study")
sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5) ///
    cistyle(area) connected levels(90 95) ///
    coeflabels("lead5/t-5" "lead4/t-4" "lead3/t-3" "lead2/t-2" "lag0/t0" ///
               "lag1/t+1" "lag2/t+2" "lag3/t+3" "lag4/t+4" "lag5/t+5") ///
    title("Event study, relabelled periods")

* From a matrix (rows b / se / pvalue, column names = periods), e.g. after
* csdid/jwdid `estat event`: sparkta, type(eventstudy) matrix(r(table))

* ============================================================
* SAVEAS -- PNG / PDF / SVG of a post-estimation page, and the table on its own
* ============================================================
sysuse auto, clear
regress price mpg weight foreign
sparkta, type(coefplot) export("`out'/coef.html") saveas("`out'/coef.png" "`out'/coef.pdf")
sparkta, type(coefplot) export("`out'/coef_table.html") saveas("`out'/coef_table.pdf", table)
sparkta, type(coefplot) export("`out'/coef_page.html") saveas("`out'/coef_page.png", page scale(3) close)

di as txt "postest.do done -- exports in `out'/"
