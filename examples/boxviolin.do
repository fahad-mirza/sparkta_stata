*! sparkta example: boxviolin.do
*! Demonstrates: boxplot, hbox, violin, hviolin, whiskerfence, filter
*! Dataset: auto (built-in)
*! Version: 1.0 | 2026-03-06
* Run with: do boxviolin.do
* Charts open in browser unless export() is specified.

sysuse auto, clear

* ============================================================
* BOXPLOT -- Tukey box-and-whisker
* Whiskers: Tukey 1.5*IQR fence by default
* Points outside fences are shown as outlier dots
* ============================================================

* Basic boxplot: price distribution by repair record
sparkta price, type(boxplot) over(rep78) ///
    title("Price Distribution by Repair Record") ///
    ytitle("Price (USD)") ///
    note("Whiskers: 1.5 x IQR (Tukey default)")
    * export("~/Desktop/ex_box_basic.html")

* Multiple variables: one box per variable, no over()
sparkta price weight mpg, type(boxplot) ///
    title("Distribution of Three Variables") ///
    note("No over(): one box per variable")
    * export("~/Desktop/ex_box_multivar.html")

* Horizontal boxplot: hbox
sparkta price, type(hbox) over(rep78) ///
    title("Price Distribution -- Horizontal Boxplot") ///
    xtitle("Price (USD)")
    * export("~/Desktop/ex_hbox.html")

* Custom whisker fence: 3*IQR (fewer outliers shown)
sparkta price, type(boxplot) over(rep78) ///
    whiskerfence(3) ///
    title("Price Distribution -- 3x IQR Fence") ///
    note("whiskerfence(3): tighter fence shows fewer outliers")
    * export("~/Desktop/ex_box_fence3.html")

* Tight fence: 1*IQR (more outliers shown)
sparkta price, type(boxplot) over(rep78) ///
    whiskerfence(1) ///
    title("Price Distribution -- 1x IQR Fence") ///
    note("whiskerfence(1): wider fence reveals more outliers")
    * export("~/Desktop/ex_box_fence1.html")

* Dark theme boxplot
sparkta price, type(boxplot) over(rep78) ///
    theme(dark) ///
    title("Price Distribution by Repair Record -- Dark") ///
    ytitle("Price (USD)")
    * export("~/Desktop/ex_box_dark.html")

* Custom marker colors: red median line, blue mean dot
sparkta price, type(boxplot) over(rep78) ///
    mediancolor(#e74c3c) meancolor(#2980b9) ///
    title("Price Distribution -- Custom Marker Colors") ///
    note("Red = median line, Blue = mean dot")
    * export("~/Desktop/ex_box_markercolors.html")

* Custom palette: light fills (auto-detected -> dark markers)
sparkta price, type(boxplot) over(rep78) ///
    colors(#f7c59f #f4e1d2 #e8d5c4 #dde8c9 #c5dde8) ///
    title("Price Distribution -- Light Custom Palette") ///
    note("Light fills: median and mean markers auto-set to dark")
    * export("~/Desktop/ex_box_lightpalette.html")

* Boxplot with interactive filter
sparkta price, type(boxplot) over(rep78) ///
    filter(foreign) ///
    title("Price Distribution by Repair Record") ///
    subtitle("Filter by Car Origin using the dropdown")
    * export("~/Desktop/ex_box_filter.html")

* ============================================================
* VIOLIN CHART
* Custom KDE (Gaussian kernel, Silverman bandwidth)
* Animates on load (grow-in) and on filter change (tween)
* ============================================================

* Basic violin: price distribution by repair record
sparkta price, type(violin) over(rep78) ///
    title("Price Distribution by Repair Record -- Violin") ///
    ytitle("Price (USD)") ///
    note("KDE shape shows full distribution; diamond=median, dot=mean")
    * export("~/Desktop/ex_violin_basic.html")

* Violin, no over(): one violin per variable
sparkta price weight, type(violin) ///
    title("Price and Weight Distributions") ///
    note("No over(): one violin per variable")
    * export("~/Desktop/ex_violin_noover.html")

* Horizontal violin: hviolin
sparkta price, type(hviolin) over(rep78) ///
    title("Price Distribution -- Horizontal Violin") ///
    xtitle("Price (USD)")
    * export("~/Desktop/ex_hviolin.html")

* Custom KDE bandwidth (wider bandwidth = smoother shape)
sparkta price, type(violin) over(rep78) ///
    bandwidth(2000) ///
    title("Price Violin -- Custom Bandwidth 2000") ///
    note("bandwidth(): controls KDE smoothness (Silverman default if omitted)")
    * export("~/Desktop/ex_violin_bandwidth.html")

* Custom marker colors: red median, blue mean
sparkta price, type(violin) over(rep78) ///
    mediancolor(#e74c3c) meancolor(#2980b9) ///
    title("Price Violin -- Custom Marker Colors") ///
    note("Red diamond = median, Blue dot = mean")
    * export("~/Desktop/ex_violin_markercolors.html")

* Dark theme violin
sparkta price, type(violin) over(rep78) ///
    theme(dark) ///
    title("Price Distribution -- Violin Dark Theme") ///
    ytitle("Price (USD)")
    * export("~/Desktop/ex_violin_dark.html")

* Violin with interactive filter (animates on change)
sparkta price, type(violin) over(rep78) ///
    filter(foreign) ///
    title("Price Violin by Repair Record") ///
    subtitle("Filter by Car Origin: violin animates on change")
    * export("~/Desktop/ex_violin_filter.html")

* ============================================================
* SECONDARY Y-AXIS -- different scales on same chart
* ============================================================

* Price on primary y-axis, mpg on secondary
sparkta price mpg, type(line) over(foreign) ///
    y2(mpg) y2title("MPG") ///
    ytitle("Price (USD)") ///
    title("Price and MPG by Origin -- Dual Axis") ///
    note("y2(): variables on separate y-axis scales") ///
    legend(bottom)
    * export("~/Desktop/ex_y2.html")

di "boxviolin.do complete"
