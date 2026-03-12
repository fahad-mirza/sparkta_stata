*! sparkta showcase.do
*! Generates the 8 charts used in the GitHub Pages gallery (docs/charts/)
*! Run this file to regenerate all gallery charts after any sparkta update.
*! Output directory: docs/charts/ relative to this file's location.
*! Requires: ssc install sparkta (or net install from repo)
*! Datasets: auto, nlsw88 (built-in Stata datasets, no download needed)
*
* Usage:
*   cd to the repo root, then:
*   do examples/showcase.do
*
* Version: 3.5.41  2026-03-12

* ---------------------------------------------------------------------------
* Set output directory to docs/charts/ relative to repo root
* ---------------------------------------------------------------------------

* Adjust this path if running from a different working directory
local outdir "docs/charts"

* Create output folder if it does not exist
capture mkdir "`outdir'"

di as txt "sparkta showcase -- generating gallery charts to: `outdir'"
di as txt "------------------------------------------------------"

* ---------------------------------------------------------------------------
* Chart 1: CI Bar -- 95% confidence intervals, t-distribution
* ---------------------------------------------------------------------------
sysuse auto, clear
sparkta price, type(cibar) over(rep78) cilevel(95) nomissing ///
    title("Mean Price by Repair Record (95% CI)") ///
    export("`outdir'/C1_cibar.html")
di as txt "Chart 1/8 OK: CI bar"

* ---------------------------------------------------------------------------
* Chart 2: Violin -- animated KDE density + IQR box
* ---------------------------------------------------------------------------
sysuse auto, clear
sparkta price, type(violin) over(rep78) nomissing ///
    title("Price Distribution by Repair Record") ///
    export("`outdir'/E2_violin.html")
di as txt "Chart 2/8 OK: Violin"

* ---------------------------------------------------------------------------
* Chart 3: Dual interactive filter dropdowns
* ---------------------------------------------------------------------------
sysuse auto, clear
sparkta price, over(rep78) filter(foreign) filter2(headroom) nomissing ///
    title("Price by Repair Record") ///
    export("`outdir'/G2_filter2.html")
di as txt "Chart 3/8 OK: Dual filter"

* ---------------------------------------------------------------------------
* Chart 4: Reference annotations -- yline + yband
* ---------------------------------------------------------------------------
sysuse auto, clear
sparkta price, over(rep78) nomissing ///
    yline(6000) ylinelabel("Target") ylinecolor(#e74c3c) ///
    yband(4000 8000) ybandcolor(rgba(52,152,219,0.12)) ///
    title("Price with Reference Line and Band") ///
    export("`outdir'/K4_yline_yband.html")
di as txt "Chart 4/8 OK: Annotations"

* ---------------------------------------------------------------------------
* Chart 5: Scatter with over() colouring
* ---------------------------------------------------------------------------
sysuse auto, clear
sparkta price mpg, type(scatter) over(foreign) ///
    title("Price vs MPG by Origin") ///
    export("`outdir'/A8_scatter_over.html")
di as txt "Chart 5/8 OK: Scatter"

* ---------------------------------------------------------------------------
* Chart 6: 100% stacked bar -- composition view
* ---------------------------------------------------------------------------
sysuse nlsw88, clear
sparkta wage, over(race) type(stackedbar100) by(collgrad) ///
    title("Wage Composition by Race and Education") ///
    export("`outdir'/B3_stackedbar100.html")
di as txt "Chart 6/8 OK: 100% stacked bar"

* ---------------------------------------------------------------------------
* Chart 7: Panel layout with by()
* ---------------------------------------------------------------------------
sysuse auto, clear
sparkta price, over(rep78) by(foreign) nomissing ///
    title("Price by Repair Record") ///
    export("`outdir'/F1_bar_by.html")
di as txt "Chart 7/8 OK: by() panels"

* ---------------------------------------------------------------------------
* Chart 8: Dark neon theme
* ---------------------------------------------------------------------------
sysuse auto, clear
sparkta price, over(rep78) theme(dark_neon) nomissing ///
    title("Price by Repair Record") ///
    export("`outdir'/I6_theme_dark_neon.html")
di as txt "Chart 8/8 OK: Dark neon theme"

* ---------------------------------------------------------------------------
* Done
* ---------------------------------------------------------------------------
di as txt " "
di as txt "All 8 gallery charts written to: `outdir'"
di as txt "Push docs/ to GitHub and enable Pages to publish the gallery."
di as txt "Gallery URL: https://fahad-mirza.github.io/sparkta_stata/"
