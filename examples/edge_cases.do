*! sparkta edge case tests: edge_cases.do
*! Purpose: Systematically exercises boundary conditions and known fragile paths
*! Dataset: auto (built-in), with synthetic modifications for some cases
*! Version: 1.0 | 2026-03-06
*
* HOW TO USE:
*   Run each section independently. Every chart opens in browser unless
*   export() is uncommented. Watch for: blank charts, Stata r() errors,
*   Java exceptions in the Stata output, malformed tooltips, clipped axes.
*
* SECTIONS:
*   A. Missing data in grouping variables
*   B. Single-observation groups (CI charts, boxplots)
*   C. Extreme and near-zero values
*   D. Large number of groups (legend stress)
*   E. Single variable, no over()
*   F. String-valued over() and by() variables
*   G. Filter interactions with missing groups
*   H. Box and violin edge cases
*   I. Histogram edge cases
*   J. v2.6.0 styling options -- overflow and CSS edge cases
*   K. Offline mode integrity
* =============================================================

sysuse auto, clear

* =============================================================
* SECTION A: Missing data in grouping variables
* Expected: missing groups silently excluded by default;
*           showmissing makes them explicit as "(Missing)" group
* =============================================================

* A1. rep78 has 5 missing obs -- default excludes them silently
sparkta price, type(bar) over(rep78) ///
    title("A1: Missing in over() -- default excludes silently") ///
    note("rep78 has 5 missing obs -- should show 5 bars, not 6")
    * export("~/Desktop/ec_A1.html")

* A2. showmissing makes the missing group explicit
sparkta price, type(bar) over(rep78, showmissing) ///
    title("A2: showmissing -- Missing group shown last") ///
    note("Should show 5 repair groups + (Missing) as 6th bar")
    * export("~/Desktop/ec_A2.html")

* A3. Missing in by() -- one panel per non-missing group
sparkta price, type(histogram) by(rep78) layout(grid) ///
    title("A3: Missing in by() -- 5 panels only, no missing panel")
    * export("~/Desktop/ec_A3.html")

* A4. showmissing on by() -- extra panel for missing group
sparkta price, type(histogram) by(rep78, showmissing) layout(grid) ///
    title("A4: showmissing on by() -- 6 panels including Missing")
    * export("~/Desktop/ec_A4.html")

* A5. filter() with a variable that has no missing: should not add Missing option
sparkta price, type(bar) over(rep78) filter(foreign) ///
    title("A5: filter(foreign) -- no missing in filter var, no Missing option") ///
    note("foreign has no missing -- dropdown should show Domestic/Foreign only")
    * export("~/Desktop/ec_A5.html")

* A6. Two filters, one with missing, one without
sparkta price, type(bar) over(foreign) ///
    filter(rep78) filter2(foreign) ///
    title("A6: Two filters -- filter(rep78) has missing, filter2(foreign) does not")
    * export("~/Desktop/ec_A6.html")

* =============================================================
* SECTION B: Single-observation groups
* Expected: cibar/ciline omit groups with n<2; boxplot shows degenerate box
* =============================================================

* Create a dataset where one group has exactly 1 observation
preserve
keep if rep78 != . & price != .
* Drop all but 1 obs of rep78==1 (there's only 1 in auto anyway)
* rep78==1 has n=2 normally -- let's drop one to get n=1
drop if rep78 == 1 & _n > 1

* B1. cibar with a group that has n=1 -- should omit that group silently
sparkta price, type(cibar) over(rep78) ///
    title("B1: cibar -- group with n=1 silently omitted") ///
    note("Group with n=1 cannot have a CI: should be absent from chart")
    * export("~/Desktop/ec_B1.html")

* B2. ciline same check
sparkta price, type(ciline) over(rep78) ///
    title("B2: ciline -- group with n=1 omitted from CI band")
    * export("~/Desktop/ec_B2.html")

* B3. boxplot with n=1 group -- degenerate box (all quartiles equal)
sparkta price, type(boxplot) over(rep78) ///
    title("B3: boxplot -- n=1 group should show as flat line/point") ///
    note("Degenerate box: Q1=median=Q3=min=max, IQR=0")
    * export("~/Desktop/ec_B3.html")

restore

* =============================================================
* SECTION C: Extreme and near-zero values
* Expected: axes auto-range correctly, no clipping, tooltip readable
* =============================================================

* C1. Very large values -- check tooltip formatting and axis range
sparkta price, type(bar) over(rep78) ///
    tooltipformat(",.0f") ///
    title("C1: Large values -- tooltip with comma formatting") ///
    ytitle("Price (USD)")
    * export("~/Desktop/ec_C1.html")

* C2. yrange with a max much larger than data -- bars should look short
sparkta price, type(bar) over(rep78) ///
    yrange(0 50000) ///
    title("C2: yrange max >> data max -- bars should be short")
    * export("~/Desktop/ec_C2.html")

* C3. yrange with min > 0 -- y-axis does not start at zero
sparkta price, type(line) over(rep78) ///
    yrange(3000 12000) ///
    title("C3: yrange(3000 12000) -- y-axis does not start at 0")
    * export("~/Desktop/ec_C3.html")

* C4. Logarithmic y-axis -- all values must be positive
sparkta price, type(bar) over(rep78) ///
    ytype(logarithmic) ///
    title("C4: Log y-axis -- price values all positive, should render fine")
    * export("~/Desktop/ec_C4.html")

* C5. mpg is bounded [12, 41] -- check boxplot whisker clipping at data bounds
sparkta mpg, type(boxplot) over(foreign) ///
    title("C5: mpg boxplot -- check whiskers do not exceed data range") ///
    note("Min mpg=12, Max mpg=41 -- outlier dots should sit within these")
    * export("~/Desktop/ec_C5.html")

* C6. weight has no outliers at 1.5*IQR -- check that no outlier dots appear
sparkta weight, type(boxplot) over(foreign) ///
    whiskerfence(1.5) ///
    title("C6: weight boxplot 1.5*IQR -- may have no outlier dots") ///
    note("If no outliers: outlier dot layer should be invisible, not error")
    * export("~/Desktop/ec_C6.html")

* C7. whiskerfence(0.1) -- nearly every point is an outlier
sparkta price, type(boxplot) over(rep78) ///
    whiskerfence(0.1) ///
    title("C7: whiskerfence(0.1) -- nearly all points are outliers") ///
    note("Stress test: many outlier dots, axis clipping check")
    * export("~/Desktop/ec_C7.html")

* =============================================================
* SECTION D: Many groups (legend and color stress)
* Expected: colors cycle gracefully, legend does not overflow, labels readable
* =============================================================

* D1. over() with 5 groups -- near palette boundary
sparkta price weight mpg, type(bar) over(rep78) ///
    title("D1: 5 groups x 3 variables = 15 bars") ///
    legend(bottom)
    * export("~/Desktop/ec_D1.html")

* D2. stackedbar100 with many variables -- share proportions must sum to 100%
sparkta price weight length turn displacement headroom, ///
    type(stackedbar100) over(foreign) ///
    title("D2: 6 variables stackedbar100 -- each column must sum to 100%%") ///
    legend(bottom) stat(mean)
    * export("~/Desktop/ec_D2.html")

* D3. Many bars from by() -- check grid layout wraps correctly
sparkta price, type(bar) by(rep78) layout(grid) ///
    title("D3: by(rep78) grid layout -- 5 panels in 2-column grid")
    * export("~/Desktop/ec_D3.html")

* D4. sortgroups(desc) -- groups should appear in reverse order
sparkta price, type(bar) over(rep78) sortgroups(desc) ///
    title("D4: sortgroups(desc) -- groups 5,4,3,2,1 left to right")
    * export("~/Desktop/ec_D4.html")

* =============================================================
* SECTION E: Single variable, no over() -- one bar or line per variable
* Expected: each variable is its own group, color-by-variable
* =============================================================

* E1. Single variable, no over() -- one bar
sparkta price, type(bar) ///
    title("E1: single variable no over() -- one bar showing mean price")
    * export("~/Desktop/ec_E1.html")

* E2. Multiple variables, no over() -- one bar per variable
sparkta price weight mpg length, type(bar) stat(mean) datalabels ///
    title("E2: 4 variables no over() -- one colored bar each")
    * export("~/Desktop/ec_E2.html")

* E3. Multiple variables, no over(), median stat
sparkta price weight mpg, type(bar) stat(median) ///
    title("E3: median, no over() -- values should match summarize,detail")
    * export("~/Desktop/ec_E3.html")

* E4. Boxplot, multiple variables, no over() -- one box per variable
sparkta price weight mpg, type(boxplot) ///
    title("E4: boxplot 3 variables no over() -- one box each") ///
    note("price ~ 6000, weight ~ 3000, mpg ~ 21: very different scales")
    * export("~/Desktop/ec_E4.html")

* E5. Violin, multiple variables, no over() -- mixed scale check
sparkta price weight, type(violin) ///
    title("E5: violin 2 variables no over() -- price and weight on same axis") ///
    note("Different scales: distributions may overlap heavily -- check KDE range")
    * export("~/Desktop/ec_E5.html")

* =============================================================
* SECTION F: String-valued grouping variables
* Expected: string groups use their text value, sort correctly
* =============================================================

* F1. Create a string variable and use it in over()
preserve
gen origin = "Domestic"
replace origin = "Foreign" if foreign == 1
label drop _all

sparkta price, type(bar) over(foreign) novaluelabels ///
    title("F1: novaluelabels -- shows 0/1 codes instead of Domestic/Foreign")
    * export("~/Desktop/ec_F1.html")

* F2. Value labels vs raw codes
sparkta price, type(bar) over(foreign) ///
    title("F2: value labels on -- should show Domestic/Foreign not 0/1")
    * export("~/Desktop/ec_F2.html")

restore

* =============================================================
* SECTION G: Filter interactions
* Expected: chart updates live on filter change; no stale state
* =============================================================

* G1. filter on the same variable as over() -- legal, unusual
sparkta price, type(bar) over(rep78) filter(rep78) ///
    title("G1: filter on same variable as over()") ///
    note("Selecting a filter option should show that group as the only bar")
    * export("~/Desktop/ec_G1.html")

* G2. Two filters on the same variable -- legal, unusual
sparkta price, type(bar) over(foreign) ///
    filter(rep78) filter2(rep78) ///
    title("G2: filter and filter2 on same variable") ///
    note("Both dropdowns drive same dimension -- check no JS error on change")
    * export("~/Desktop/ec_G2.html")

* G3. filter with violin -- animation should tween on filter change
sparkta price, type(violin) over(rep78) filter(foreign) ///
    title("G3: violin + filter -- shape should animate smoothly on dropdown change") ///
    note("Select Domestic/Foreign and watch violin tween. Both directions.")
    * export("~/Desktop/ec_G3.html")

* G4. filter with boxplot -- chart should reinitialise (destroy+reinit pattern)
sparkta price, type(boxplot) over(rep78) filter(foreign) ///
    title("G4: boxplot + filter -- reinit on change, no stale whiskers") ///
    note("Outlier dots and whiskers must update cleanly on filter change")
    * export("~/Desktop/ec_G4.html")

* G5. cibar + filter -- N per group must update on filter change
sparkta price, type(cibar) over(rep78) filter(foreign) ///
    title("G5: cibar + filter -- CI widths change with N as filter changes") ///
    note("CI must recompute for filtered subpopulation, not full dataset")
    * export("~/Desktop/ec_G5.html")

* =============================================================
* SECTION H: Box and violin edge cases
* =============================================================

* H1. whiskerfence(1.5) vs whiskerfence(3) -- side by side visual check
* Run these consecutively and compare
sparkta price, type(boxplot) over(rep78) whiskerfence(1.5) ///
    title("H1a: whiskerfence(1.5) -- Tukey default") ///
    note("Baseline comparison for H1b")
    * export("~/Desktop/ec_H1a.html")

sparkta price, type(boxplot) over(rep78) whiskerfence(3) ///
    title("H1b: whiskerfence(3) -- wider fence, fewer outlier dots") ///
    note("Compare with H1a: some outlier dots should disappear")
    * export("~/Desktop/ec_H1b.html")

* H2. hbox with many groups -- horizontal layout stress
sparkta price, type(hbox) over(rep78) ///
    title("H2: hbox 5 groups -- horizontal layout, check y-axis labels readable") ///
    xtitle("Price (USD)")
    * export("~/Desktop/ec_H2.html")

* H3. hviolin -- horizontal orientation, animated
sparkta price, type(hviolin) over(rep78) ///
    title("H3: hviolin -- horizontal violin, check KDE shapes are horizontal") ///
    xtitle("Price (USD)")
    * export("~/Desktop/ec_H3.html")

* H4. Custom marker colors on dark theme -- luminance auto-detection disabled
sparkta price, type(boxplot) over(rep78) ///
    theme(dark) ///
    mediancolor(#ff6b6b) meancolor(#ffd93d) ///
    title("H4: Dark theme + custom marker colors") ///
    note("mediancolor and meancolor override luminance auto-detection")
    * export("~/Desktop/ec_H4.html")

* H5. Light custom palette -- auto-detection should pick dark markers
sparkta price, type(boxplot) over(rep78) ///
    colors("#f7dc6f #82e0aa #85c1e9 #f1948a #c39bd3") ///
    title("H5: Light pastel palette -- markers should auto-detect to dark") ///
    note("Luminance of these colors is high: median/mean should be dark, not white")
    * export("~/Desktop/ec_H5.html")

* H6. Violin bandwidth extremes
sparkta price, type(violin) over(rep78) bandwidth(500) ///
    title("H6a: bandwidth(500) -- tight KDE, spiky shapes") ///
    note("Small bandwidth: shapes should be narrow and data-hugging")
    * export("~/Desktop/ec_H6a.html")

sparkta price, type(violin) over(rep78) bandwidth(10000) ///
    title("H6b: bandwidth(10000) -- very wide KDE, nearly Gaussian") ///
    note("Large bandwidth: shapes should be smooth, wide, and symmetric")
    * export("~/Desktop/ec_H6b.html")

* H7. Violin with very small group (n=2)
preserve
keep if rep78 == 1 | rep78 == 5
sparkta price, type(violin) over(rep78) ///
    title("H7: violin with n=2 group (rep78==1) -- degenerate KDE") ///
    note("Group with n=2: KDE may be near-flat; should not crash")
    * export("~/Desktop/ec_H7.html")
restore

* =============================================================
* SECTION I: Histogram edge cases
* =============================================================

* I1. Very few distinct values -- Sturges rule may give more bins than values
preserve
keep if rep78 != .
sparkta rep78, type(histogram) ///
    title("I1: histogram of rep78 (5 distinct values, integer) -- Sturges bins") ///
    note("Sturges gives ~4 bins; bins may straddle integer values")
    * export("~/Desktop/ec_I1.html")
restore

* I2. User-specified bins exceed distinct values
sparkta price, type(histogram) bins(50) ///
    title("I2: bins(50) -- maximum bins, many may be near-empty") ///
    note("Some bins will have count=0; chart should show gaps not error")
    * export("~/Desktop/ec_I2.html")

* I3. bins(2) -- minimum legal value
sparkta price, type(histogram) bins(2) ///
    title("I3: bins(2) -- minimum legal value") ///
    note("Two bins only; large bars, chart should render cleanly")
    * export("~/Desktop/ec_I3.html")

* I4. histtype comparison -- same data, three metrics
sparkta price, type(histogram) histtype(density) ///
    title("I4a: density -- area under curve sums to 1") ///
    note("Default metric: count / (n * binWidth)")
    * export("~/Desktop/ec_I4a.html")

sparkta price, type(histogram) histtype(frequency) ///
    title("I4b: frequency -- raw count per bin") ///
    note("Y-axis should be integers; total across bars = n")
    * export("~/Desktop/ec_I4b.html")

sparkta price, type(histogram) histtype(fraction) ///
    title("I4c: fraction -- proportion per bin") ///
    note("Y values in (0,1); total across bars = 1")
    * export("~/Desktop/ec_I4c.html")

* I5. histogram with by() -- separate panel per group
sparkta price, type(histogram) by(foreign) layout(grid) ///
    histtype(density) ///
    title("I5: histogram by(foreign) -- two separate density panels") ///
    note("Each panel is independent; densities do not need to match scale")
    * export("~/Desktop/ec_I5.html")

* I6. histogram with filter -- updates on dropdown change
sparkta price, type(histogram) filter(foreign) ///
    title("I6: histogram + filter -- bins must recompute on filter change") ///
    note("Bin edges may shift as n changes; check no axis clipping")
    * export("~/Desktop/ec_I6.html")

* =============================================================
* SECTION J: v2.6.0 styling options -- overflow and CSS edge cases
* =============================================================

* J1. Very large title font -- check layout does not break
sparkta price, type(bar) over(rep78) ///
    titlesize(48) ///
    title("J1: Giant title (48px) -- chart should still render below it")
    * export("~/Desktop/ec_J1.html")

* J2. rgba() colors with spaces -- parser must handle the spaces correctly
sparkta price, type(bar) over(foreign) ///
    tooltipbg("rgba(0, 0, 0, 0.9)") ///
    legbgcolor("rgba(255, 255, 255, 0.8)") ///
    title("J2: rgba() with spaces in color strings")
    * export("~/Desktop/ec_J2.html")

* J3. All styling options combined -- kitchen sink
sparkta price weight, type(bar) over(foreign) ///
    titlesize(22) titlecolor(#2c3e50) ///
    subtitlesize(14) subtitlecolor(#7f8c8d) ///
    xtitlesize(13) xtitlecolor(#34495e) ///
    ytitlesize(13) ytitlecolor(#34495e) ///
    xlabsize(11) xlabcolor(#95a5a6) ///
    ylabsize(11) ylabcolor(#95a5a6) ///
    legcolor(#2c3e50) legbgcolor(rgba(255,255,255,0.85)) ///
    tooltipbg(rgba(44,62,80,0.95)) tooltipborder(#3498db) ///
    tooltipfontsize(13) tooltippadding(12) ///
    title("J3: All v2.6.0 styling options combined") ///
    subtitle("Typography and tooltip stress test") ///
    legend(bottom) legtitle("Variable")
    * export("~/Desktop/ec_J3.html")

* J4. Dark theme + light legend text (expected: white labels on dark bg)
sparkta price, type(cibar) over(rep78) ///
    theme(dark) ///
    legcolor(#ffffff) legbgcolor(rgba(0,0,0,0.5)) ///
    tooltipbg(rgba(255,255,255,0.95)) tooltipborder(#888) ///
    title("J4: Dark theme + white legend text + light tooltip")
    * export("~/Desktop/ec_J4.html")

* J5. Empty strings -- styling options with empty string should use defaults
sparkta price, type(bar) over(foreign) ///
    titlecolor("") xlabcolor("") ///
    title("J5: Empty color strings -- should fall back to theme defaults") ///
    note("No error expected; theme colors should show as usual")
    * export("~/Desktop/ec_J5.html")

* =============================================================
* SECTION K: Offline mode integrity
* Expected: chart renders with no network requests; identical to online version
* Note: requires offline JS libs bundled in sparkta.jar
*       Run fetch_js_libs + build first if offline charts show blank
* =============================================================

* K1. Basic offline bar chart
sparkta price, type(bar) over(rep78) offline ///
    title("K1: Offline bar chart -- must render without internet") ///
    export("~/Desktop/ec_K1_offline.html")
    * Open file while disconnected from internet to verify

* K2. Offline boxplot (requires boxplot plugin bundled)
sparkta price, type(boxplot) over(rep78) offline ///
    title("K2: Offline boxplot -- plugin bundled in jar") ///
    export("~/Desktop/ec_K2_offline.html")

* K3. Offline violin
sparkta price, type(violin) over(rep78) offline ///
    title("K3: Offline violin -- KDE and animation work offline") ///
    export("~/Desktop/ec_K3_offline.html")

* K4. Offline with filter -- JS interaction must work with no CDN
sparkta price, type(bar) over(rep78) filter(foreign) offline ///
    title("K4: Offline + filter -- dropdown interaction with no CDN") ///
    export("~/Desktop/ec_K4_offline.html")

di ""
di "edge_cases.do complete."
di "Review each chart for: blank renders, JS console errors, clipped axes,"
di "wrong tooltip values, broken filter interactions, legend overflow."
