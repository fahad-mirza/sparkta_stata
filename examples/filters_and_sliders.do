*! sparkta example: filters_and_sliders.do
*! Demonstrates: filters(), sliders(), by() + filters(), fit lines, mlabel()
*! Dataset: auto (built-in), nlsw88 (built-in)
*! Version: 3.5.96 | 2026-03-17
* Run with: do filters_and_sliders.do
* Charts open in browser unless export() is specified.

sysuse auto, clear

* ============================================================
* FILTER DROPDOWNS
* ============================================================

* Single filter dropdown
sparkta price, over(rep78) ///
    filters(foreign) ///
    title("Mean Price by Repair Record") ///
    subtitle("Use dropdown to filter by Car Origin")
    * export("~/Desktop/ex_filter_single.html")

* Two filter dropdowns (any number of variables)
sparkta price, over(rep78) ///
    filters(foreign rep78) ///
    title("Mean Price by Repair Record") ///
    subtitle("Filter by Origin and Repair Record")
    * export("~/Desktop/ex_filter_two.html")

* Filter + by() panels: all panels update together
sparkta price, over(rep78) by(foreign) ///
    filters(rep78) ///
    title("Price by Repair Record") ///
    subtitle("Dropdown updates both panels simultaneously")
    * export("~/Desktop/ex_filter_by.html")

* Filter on scatter
sparkta price mpg, type(scatter) over(foreign) ///
    filters(rep78) ///
    title("Price vs MPG by Origin") ///
    subtitle("Filter by repair record")
    * export("~/Desktop/ex_filter_scatter.html")

* ============================================================
* RANGE SLIDERS
* ============================================================

* Single range slider
sparkta price, over(foreign) ///
    sliders(mpg) ///
    title("Mean Price by Origin") ///
    subtitle("Drag MPG slider to filter observations")
    * export("~/Desktop/ex_slider_single.html")

* Two sliders on scatter
sparkta price mpg, type(scatter) ///
    sliders(price mpg) ///
    title("Price vs MPG") ///
    subtitle("Drag either slider to zoom into a range")
    * export("~/Desktop/ex_slider_scatter.html")

* Filters and sliders combined
sparkta price, over(foreign) ///
    filters(rep78) sliders(mpg) ///
    title("Mean Price by Origin") ///
    subtitle("Dropdown + range slider combined")
    * export("~/Desktop/ex_filter_slider.html")

* ============================================================
* SCATTER FIT LINES (7 types)
* ============================================================

* Linear fit
sparkta price mpg, type(scatter) ///
    fit(lfit) ///
    title("Price vs MPG -- Linear Fit")
    * export("~/Desktop/ex_fit_lfit.html")

* Quadratic fit with 95% confidence band
sparkta price mpg, type(scatter) ///
    fit(qfit) fitci ///
    title("Price vs MPG -- Quadratic Fit with CI Band")
    * export("~/Desktop/ex_fit_qfit_ci.html")

* LOWESS smooth
sparkta price mpg, type(scatter) ///
    fit(lowess) ///
    title("Price vs MPG -- LOWESS Smooth")
    * export("~/Desktop/ex_fit_lowess.html")

* Per-group fit lines (one per over-group)
sparkta price mpg, type(scatter) over(foreign) ///
    fit(lfit) ///
    title("Price vs MPG -- Fit Line per Group") ///
    subtitle("Separate lfit line for Domestic and Foreign")
    * export("~/Desktop/ex_fit_over.html")

* Fit line with filter
sparkta price mpg, type(scatter) over(foreign) ///
    fit(lowess) filters(rep78) ///
    title("Price vs MPG with LOWESS + Filter")
    * export("~/Desktop/ex_fit_filter.html")

* ============================================================
* MARKER LABELS ON SCATTER
* ============================================================

* Label points with make (car name)
sparkta price mpg if price > 10000, type(scatter) ///
    mlabel(make) ///
    title("Most Expensive Cars: Price vs MPG") ///
    subtitle("Labels show car make")
    * export("~/Desktop/ex_mlabel.html")

* ============================================================
* NLSW88 LARGER DATASET
* ============================================================

sysuse nlsw88, clear

* Filter on a categorical variable
sparkta wage, over(race) ///
    filters(collgrad) ///
    title("Mean Wage by Race") ///
    subtitle("Filter by College Graduate status")
    * export("~/Desktop/ex_nlsw88_filter.html")

* Slider on scatter with fit line
sparkta wage hours, type(scatter) over(collgrad) ///
    fit(lfit) sliders(ttl_exp) ///
    title("Wages vs Hours by Education") ///
    subtitle("Drag experience slider to filter observations")
    * export("~/Desktop/ex_nlsw88_fit_slider.html")

di "filters_and_sliders.do complete"
