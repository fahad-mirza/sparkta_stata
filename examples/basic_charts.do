*! sparkta example: basic_charts.do
*! Demonstrates: bar, hbar, stackedbar, line, area, scatter, bubble, pie, donut
*! Dataset: auto (built-in)
*! Version: 3.5.96 | 2026-03-17
* Run with: do basic_charts.do
* Charts open in browser unless export() is specified.
* Uncomment export() lines to save files to disk.

sysuse auto, clear

* ============================================================
* BAR CHARTS
* ============================================================

* Default: mean of price by repair record
sparkta price, type(bar) over(rep78) ///
    title("Mean Price by Repair Record") ///
    ytitle("Mean Price (USD)")
    * export("~/Desktop/ex_bar_basic.html")

* Multiple variables, grouped bars, custom colors
sparkta price weight, type(bar) over(foreign) ///
    title("Price and Weight by Car Origin") ///
    subtitle("Mean values -- 1978 auto data") ///
    colors("#4e79a7 #f28e2b") legend(bottom) ///
    legtitle("Variable")
    * export("~/Desktop/ex_bar_grouped.html")

* Aggregation: count
sparkta price, type(bar) over(rep78) stat(count) ///
    title("Number of Cars per Repair Rating") ///
    ytitle("Count") datalabels
    * export("~/Desktop/ex_bar_count.html")

* Horizontal bars with rounded corners
sparkta price weight mpg, type(hbar) over(foreign) ///
    title("Three Variables by Origin (Horizontal)") ///
    borderradius(4) bargroupwidth(0.7)
    * export("~/Desktop/ex_hbar.html")

* Stacked bars -- composition view
sparkta price weight, type(stackedbar) over(rep78) ///
    title("Price + Weight Stacked by Repair Record") ///
    opacity(0.9)
    * export("~/Desktop/ex_stackedbar.html")

* 100% stacked -- share of group total
sparkta price weight mpg, type(stackedbar100) over(foreign) ///
    title("Variable Share by Car Origin (100%% Stacked)") ///
    subtitle("Each bar sums to 100%%") ///
    legend(bottom)
    * export("~/Desktop/ex_stackedbar100.html")

* ============================================================
* LINE AND AREA CHARTS
* ============================================================

* Line chart with smoothing
sparkta price, type(line) over(rep78) ///
    title("Mean Price by Repair Record -- Line") ///
    smooth(0.4) ytitle("Mean Price (USD)")
    * export("~/Desktop/ex_line.html")

* Area chart -- fill below line
sparkta price, type(area) over(rep78) ///
    title("Mean Price by Repair Record -- Area") ///
    areaopacity(0.4) ytitle("Mean Price (USD)")
    * export("~/Desktop/ex_area.html")

* Stacked area -- overlapping fills
sparkta price weight, type(stackedarea) over(foreign) ///
    title("Price and Weight -- Stacked Area") ///
    areaopacity(0.5)
    * export("~/Desktop/ex_stackedarea.html")

* Step function -- useful for categorical changes
sparkta price, type(line) over(rep78) stepped(after) ///
    title("Mean Price -- Step Function") ///
    note("stepped(after): value holds until next group")
    * export("~/Desktop/ex_stepped.html")

* ============================================================
* SCATTER AND BUBBLE CHARTS
* ============================================================
* NOTE: for scatter, list y first, then x (Stata convention)

* Basic scatter: price (y) vs mpg (x)
sparkta price mpg, type(scatter) ///
    title("Price vs MPG") ///
    ytitle("Price (USD)") xtitle("Mileage (mpg)")
    * export("~/Desktop/ex_scatter.html")

* Scatter by group: color by origin
sparkta price mpg, type(scatter) over(foreign) ///
    title("Price vs MPG by Car Origin") ///
    ytitle("Price (USD)") xtitle("Mileage (mpg)") ///
    legend(bottom)
    * export("~/Desktop/ex_scatter_over.html")

* Bubble: price (y) vs mpg (x), size = weight
sparkta price mpg weight, type(bubble) over(foreign) ///
    title("Price vs MPG -- Bubble Size = Weight") ///
    ytitle("Price") xtitle("MPG") ///
    opacity(0.7)
    * export("~/Desktop/ex_bubble.html")

* ============================================================
* PIE AND DONUT CHARTS
* ============================================================

* Basic pie: mean price by repair record
sparkta price, type(pie) over(rep78) ///
    title("Price Share by Repair Record") ///
    pielabels legend(bottom)
    * export("~/Desktop/ex_pie.html")

* Donut: deeper hole, rotated start, sum aggregation
sparkta price, type(donut) over(foreign) stat(sum) ///
    title("Total Price by Car Origin") ///
    cutout(65) rotation(-90) pielabels ///
    note("stat(sum): shows total not mean")
    * export("~/Desktop/ex_donut.html")

* ============================================================
* FILTERS AND PANELS
* ============================================================

* Interactive filter dropdown
sparkta price weight, type(bar) over(rep78) ///
    filters(foreign) ///
    title("Price and Weight by Repair Record") ///
    subtitle("Use dropdown to filter by Car Origin")
    * export("~/Desktop/ex_filter.html")

* by() panels in a grid layout
sparkta price, type(bar) over(rep78) ///
    by(foreign) layout(grid) ///
    title("Price by Repair Record") ///
    subtitle("Separate panels: Domestic vs Foreign")
    * export("~/Desktop/ex_by_grid.html")

* ============================================================
* THEME AND APPEARANCE
* ============================================================

* Dark theme with custom colors
sparkta price weight, type(bar) over(foreign) ///
    theme(dark) colors("#e74c3c #3498db") ///
    title("Price and Weight by Origin -- Dark Theme") ///
    borderradius(4) legend(bottom)
    * export("~/Desktop/ex_dark.html")

* Custom axis labels, yrange, annotations
sparkta price, type(bar) over(rep78) ///
    xlabels(Poor|Fair|Average|Good|Excellent) ///
    yrange(0 12000) ytitle("Mean Price (USD)") ///
    title("Price by Repair Rating -- Custom Labels") ///
    borderradius(4) datalabels
    * export("~/Desktop/ex_custom_labels.html")

di "basic_charts.do complete"
