*! sparkta example: stat_charts.do
*! Demonstrates: cibar, ciline, histogram
*! Dataset: auto (built-in)
*! Version: 3.5.96 | 2026-03-17
* Run with: do stat_charts.do
* Charts open in browser unless export() is specified.

sysuse auto, clear

* ============================================================
* CI BAR CHARTS -- mean bars with t-distribution CI whiskers
* over() is required for cibar and ciline
* ============================================================

* Basic CI bar: 95% CI (default)
sparkta price, type(cibar) over(rep78) ///
    title("Mean Price by Repair Record (95%% CI)") ///
    ytitle("Mean Price (USD)") ///
    note("Whiskers show t-distribution 95%% confidence intervals")
    * export("~/Desktop/ex_cibar_basic.html")

* Multiple variables: each gets its own color
sparkta price weight, type(cibar) over(foreign) ///
    title("Price and Weight by Origin (95%% CI)") ///
    legend(bottom) legtitle("Variable")
    * export("~/Desktop/ex_cibar_multi.html")

* 90% CI -- narrower whiskers
sparkta price, type(cibar) over(rep78) cilevel(90) ///
    title("Mean Price (90%% CI)") ///
    subtitle("Narrower interval compared to 95%%")
    * export("~/Desktop/ex_cibar_90.html")

* 99% CI -- wider whiskers
sparkta price, type(cibar) over(rep78) cilevel(99) ///
    title("Mean Price (99%% CI)") ///
    subtitle("Wider interval compared to 95%%")
    * export("~/Desktop/ex_cibar_99.html")

* Horizontal CI bar
sparkta price weight, type(cibar) over(rep78) ///
    horizontal cilevel(95) ///
    title("Price and Weight (95%% CI) -- Horizontal") ///
    legend(bottom)
    * export("~/Desktop/ex_cibar_horiz.html")

* Dark theme CI bar
sparkta price, type(cibar) over(rep78) ///
    theme(dark) cilevel(95) ///
    title("Mean Price by Repair Record -- Dark Theme") ///
    ytitle("Mean Price (USD)")
    * export("~/Desktop/ex_cibar_dark.html")

* ============================================================
* CI LINE CHARTS -- mean line with shaded CI band
* ============================================================

* Basic CI line: 95% band
sparkta price, type(ciline) over(rep78) ///
    title("Mean Price by Repair Record (95%% CI Band)") ///
    ytitle("Mean Price (USD)") ///
    note("Shaded band shows t-distribution confidence interval")
    * export("~/Desktop/ex_ciline_basic.html")

* Multiple variables: one line per variable
sparkta price weight, type(ciline) over(foreign) ///
    title("Price and Weight (95%% CI) -- Line") ///
    legend(bottom)
    * export("~/Desktop/ex_ciline_multi.html")

* Narrower CI band opacity
sparkta price, type(ciline) over(rep78) ///
    cibandopacity(0.10) cilevel(95) ///
    title("Mean Price -- Faint CI Band") ///
    note("cibandopacity(0.10): subtle shading")
    * export("~/Desktop/ex_ciline_faint.html")

* Wider CI band opacity
sparkta price, type(ciline) over(rep78) ///
    cibandopacity(0.35) cilevel(95) ///
    title("Mean Price -- Prominent CI Band") ///
    note("cibandopacity(0.35): more visible shading")
    * export("~/Desktop/ex_ciline_wide.html")

* CI line with filter
sparkta price, type(ciline) over(rep78) ///
    filters(foreign) cibandopacity(0.20) ///
    title("Mean Price by Repair Record (95%% CI)") ///
    subtitle("Filter by Car Origin using the dropdown")
    * export("~/Desktop/ex_ciline_filter.html")

* ============================================================
* HISTOGRAMS
* ============================================================

* Default: density with Sturges rule bins
sparkta price, type(histogram) ///
    title("Price Distribution") ///
    ytitle("Density") xtitle("Price (USD)")
    * export("~/Desktop/ex_hist_density.html")

* Frequency count
sparkta price, type(histogram) histtype(frequency) ///
    title("Price Distribution -- Frequency") ///
    ytitle("Count") xtitle("Price (USD)")
    * export("~/Desktop/ex_hist_freq.html")

* Fraction (proportion)
sparkta price, type(histogram) histtype(fraction) ///
    title("Price Distribution -- Fraction") ///
    ytitle("Proportion") xtitle("Price (USD)")
    * export("~/Desktop/ex_hist_frac.html")

* Manual bin count
sparkta price, type(histogram) bins(20) ///
    title("Price Distribution -- 20 Bins") ///
    ytitle("Density")
    * export("~/Desktop/ex_hist_bins.html")

* Histogram with filter
sparkta price, type(histogram) histtype(density) ///
    filters(foreign) ///
    title("Price Distribution") ///
    subtitle("Filter by Car Origin using the dropdown")
    * export("~/Desktop/ex_hist_filter.html")

* Side-by-side histograms using by()
sparkta price, type(histogram) ///
    by(foreign) layout(horizontal) ///
    title("Price Distribution by Car Origin") ///
    note("by() creates separate panels; histogram does not support over()")
    * export("~/Desktop/ex_hist_by.html")

* MPG histogram: fewer bins, frequency
sparkta mpg, type(histogram) bins(10) histtype(frequency) ///
    title("Mileage Distribution -- 10 Bins") ///
    ytitle("Count") xtitle("MPG")
    * export("~/Desktop/ex_hist_mpg.html")

di "stat_charts.do complete"
