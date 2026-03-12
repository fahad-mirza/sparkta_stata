// test_release.do -- Sparkta comprehensive release regression test
// Version: v3.5.3  |  Date: 2026-03-09
// Purpose: Full regression test covering all chart types and key options.
//          Must pass completely before any GitHub or SSC submission.
// Usage:   sysuse auto, clear
//          then run this file section by section
//          Check: no errors, no JS console errors in browser, correct rendering
// =============================================================================

version 17
sysuse auto, clear

di ""
di "============================================================"
di " SPARKTA RELEASE REGRESSION TEST  v3.5.3"
di " Dataset: auto (74 obs, price weight mpg rep78 foreign etc)"
di "============================================================"
di ""

// =============================================================================
// SECTION 1: BAR CHARTS
// =============================================================================
di "--- SECTION 1: BAR CHARTS ---"

// 1-A: Basic bar, single var
sparkta price, type(bar) title("1-A: Basic bar single var")

// 1-B: Bar with over()
sparkta price, type(bar) over(rep78) nomissing title("1-B: Bar over(rep78)")

// 1-C: Bar multi-var
sparkta price weight, type(bar) title("1-C: Bar multi-var (no over)")

// 1-D: Bar multi-var + over()
sparkta price weight, type(bar) over(foreign) title("1-D: Bar multi-var over(foreign)")

// 1-E: Horizontal bar
sparkta price, type(hbar) over(rep78) nomissing title("1-E: Horizontal bar")

// 1-F: Stacked bar
sparkta price weight, type(stackedbar) over(rep78) nomissing title("1-F: Stacked bar")

// 1-G: 100% stacked bar
sparkta price weight, type(stackedbar100) over(rep78) nomissing title("1-G: 100pct stacked bar")

// 1-H: 100% stacked horizontal
sparkta price weight, type(stackedhbar100) over(foreign) title("1-H: 100pct stacked hbar")

// 1-I: Bar + by() panels
sparkta price weight, type(bar) by(foreign) layout(grid) title("1-I: Bar by(foreign) grid layout")

// 1-J: Bar styling options
sparkta price, type(bar) over(rep78) nomissing ///
    barwidth(0.6) bargroupwidth(0.8) borderradius(4) opacity(0.9) ///
    title("1-J: Bar styling") ///
    titlesize(22) titlecolor(#2c3e50) ///
    ytitle("Mean Price (USD)") ytitlesize(13) ///
    datalabels tooltipformat(",.0f")

di "--- Section 1 done ---"

// =============================================================================
// SECTION 2: LINE AND AREA CHARTS
// =============================================================================
di "--- SECTION 2: LINE AND AREA CHARTS ---"

// 2-A: Basic line
sparkta price, type(line) over(rep78) nomissing title("2-A: Line over(rep78)")

// 2-B: Area chart
sparkta price, type(area) over(rep78) nomissing smooth(0.4) title("2-B: Area smooth")

// 2-C: Stacked area
sparkta price weight, type(stackedarea) over(foreign) title("2-C: Stacked area")

// 2-D: Line with lpatterns + nopoints
sparkta price weight, type(line) over(foreign) ///
    lpatterns(solid|dash) nopoints linewidth(2.5) ///
    title("2-D: Line lpatterns nopoints")

// 2-E: Stepped line
sparkta price, type(line) over(foreign) stepped(after) title("2-E: Stepped line")

// 2-F: Line + gradient
sparkta price, type(area) over(rep78) nomissing ///
    gradient title("2-F: Area gradient")

// 2-G: Gradcolors per-series (colon-delimited pairs)
sparkta price weight, type(area) over(foreign) ///
    gradcolors(#1e40af|transparent:#f97316|transparent) ///
    title("2-G: gradcolors per-series")

di "--- Section 2 done ---"

// =============================================================================
// SECTION 3: SCATTER AND BUBBLE
// =============================================================================
di "--- SECTION 3: SCATTER AND BUBBLE ---"

// 3-A: Scatter
sparkta price mpg, type(scatter) title("3-A: Scatter")

// 3-B: Scatter with over()
sparkta price mpg, type(scatter) over(foreign) title("3-B: Scatter over(foreign)")

// 3-C: Bubble
sparkta price mpg weight, type(bubble) over(foreign) title("3-C: Bubble")

// 3-D: Scatter + point styling
sparkta price mpg, type(scatter) over(foreign) ///
    pointsize(6) pointstyle(triangle) pointborderwidth(2) ///
    pointhoversize(10) title("3-D: Scatter point styling")

di "--- Section 3 done ---"

// =============================================================================
// SECTION 4: PIE AND DONUT
// =============================================================================
di "--- SECTION 4: PIE AND DONUT ---"

// 4-A: Pie
sparkta price, type(pie) over(foreign) pielabels title("4-A: Pie")

// 4-B: Donut
sparkta price, type(donut) over(rep78) nomissing cutout(65) title("4-B: Donut cutout(65)")

// 4-C: Pie + sum stat
sparkta price, type(pie) over(foreign) stat(sum) pielabels ///
    title("4-C: Pie sum stat")

// 4-D: Donut with rotation and partial arc
sparkta price, type(donut) over(foreign) rotation(-90) circumference(180) ///
    title("4-D: Donut semicircle")

di "--- Section 4 done ---"

// =============================================================================
// SECTION 5: CI CHARTS
// =============================================================================
di "--- SECTION 5: CI CHARTS ---"

// 5-A: CI bar 95%
sparkta price, type(cibar) over(rep78) nomissing title("5-A: CIbar 95pct")

// 5-B: CI bar 90%
sparkta price weight, type(cibar) over(foreign) cilevel(90) title("5-B: CIbar 90pct")

// 5-C: CI bar 99%
sparkta price, type(cibar) over(rep78) nomissing cilevel(99) title("5-C: CIbar 99pct")

// 5-D: CI line
sparkta price, type(ciline) over(rep78) nomissing cibandopacity(0.2) ///
    title("5-D: CIline")

// 5-E: CI line + yline annotation
sparkta price, type(ciline) over(rep78) nomissing ///
    yline(5000) ylinelabel(Reference) ylinecolor(red) ///
    title("5-E: CIline + yline annotation")

di "--- Section 5 done ---"

// =============================================================================
// SECTION 6: HISTOGRAM
// =============================================================================
di "--- SECTION 6: HISTOGRAM ---"

// 6-A: Default histogram (density, Sturges bins)
sparkta price, type(histogram) title("6-A: Histogram density default")

// 6-B: Frequency histogram
sparkta price, type(histogram) histtype(frequency) bins(15) ///
    title("6-B: Histogram frequency bins(15)")

// 6-C: Fraction histogram
sparkta price, type(histogram) histtype(fraction) title("6-C: Histogram fraction")

// 6-D: Histogram by() panels
sparkta price, type(histogram) by(foreign) layout(grid) ///
    title("6-D: Histogram by(foreign)")

// 6-E: Histogram + xline + xband (fractional index placement)
sparkta price, type(histogram) ///
    xline(5000) xband(3000 8000) xbandcolor(rgba(255,165,0,0.15)) ///
    xlinelabel(Median) title("6-E: Histogram xline + xband")

di "--- Section 6 done ---"

// =============================================================================
// SECTION 7: BOX AND VIOLIN
// =============================================================================
di "--- SECTION 7: BOX AND VIOLIN ---"

// 7-A: Boxplot with over()
sparkta price, type(boxplot) over(rep78) nomissing title("7-A: Boxplot over(rep78)")

// 7-B: Horizontal boxplot
sparkta price, type(hbox) over(foreign) title("7-B: Horizontal boxplot")

// 7-C: Boxplot multi-var (no over)
sparkta price weight mpg, type(boxplot) title("7-C: Boxplot multi-var no over")

// 7-D: Boxplot + whiskerfence + colors
sparkta price, type(boxplot) over(rep78) nomissing ///
    whiskerfence(3) mediancolor(#e74c3c) meancolor(#2980b9) ///
    title("7-D: Boxplot fence(3) custom marker colors")

// 7-E: Violin
sparkta price, type(violin) over(rep78) nomissing title("7-E: Violin over(rep78)")

// 7-F: Horizontal violin
sparkta price, type(hviolin) over(foreign) title("7-F: Horizontal violin")

// 7-G: Violin + bandwidth
sparkta price, type(violin) over(rep78) nomissing bandwidth(2000) ///
    title("7-G: Violin bandwidth(2000)")

// 7-H: Violin + filter
sparkta price, type(violin) over(rep78) filter(foreign) nomissing ///
    title("7-H: Violin with filter")

di "--- Section 7 done ---"

// =============================================================================
// SECTION 8: FILTERS AND PANELS
// =============================================================================
di "--- SECTION 8: FILTERS AND PANELS ---"

// 8-A: Single filter
sparkta price weight, type(bar) over(foreign) filter(rep78) ///
    title("8-A: Bar + single filter")

// 8-B: Two filters
sparkta price, type(bar) over(rep78) nomissing ///
    filter(foreign) filter2(rep78) ///
    title("8-B: Bar + two filters")

// 8-C: By panels grid
sparkta price weight, type(bar) by(foreign) layout(grid) ///
    title("8-C: By panels grid")

// 8-D: Sortgroups desc
sparkta price, type(bar) over(rep78) nomissing sortgroups(desc) ///
    title("8-D: Sortgroups desc")

// 8-E: nostats
sparkta price weight, type(bar) over(foreign) nostats ///
    title("8-E: nostats suppressed")

di "--- Section 8 done ---"

// =============================================================================
// SECTION 9: AXIS OPTIONS
// =============================================================================
di "--- SECTION 9: AXIS OPTIONS ---"

// 9-A: yrange + xrange
sparkta price mpg, type(scatter) ///
    yrange(0 20000) xrange(10 40) ///
    title("9-A: yrange + xrange")

// 9-B: ystart(zero)
sparkta price, type(bar) over(foreign) ystart(zero) title("9-B: ystart zero")

// 9-C: Tick control
sparkta price mpg, type(scatter) ///
    ytickcount(5) xtickcount(4) xtickangle(45) ///
    title("9-C: tickcount + tickangle")

// 9-D: yreverse + xreverse
sparkta price, type(bar) over(rep78) nomissing ///
    yreverse title("9-D: yreverse")

// 9-E: noticks
sparkta price weight, type(bar) over(foreign) ///
    noticks title("9-E: noticks")

// 9-F: ygrace
sparkta price, type(bar) over(rep78) nomissing ///
    ygrace(0.15) title("9-F: ygrace(0.15)")

// 9-G: animduration
sparkta price, type(bar) over(foreign) ///
    animduration(3000) title("9-G: animduration(3000)")

// 9-H: xticks + yticks
sparkta price mpg, type(scatter) ///
    xticks(10|20|30|40) yticks(3000|6000|9000|12000|15000) ///
    title("9-H: xticks + yticks")

// 9-I: Custom axis labels
sparkta price, type(bar) over(rep78) nomissing ///
    xlabels(1 rep|2 rep|3 rep|4 rep|5 rep) ///
    title("9-I: xlabels custom")

di "--- Section 9 done ---"

// =============================================================================
// SECTION 10: SECONDARY Y-AXIS
// =============================================================================
di "--- SECTION 10: SECONDARY Y-AXIS ---"

// 10-A: Line chart dual axis
sparkta price mpg, type(line) over(foreign) ///
    y2(mpg) y2title("Miles per Gallon") ///
    title("10-A: Dual axis line")

// 10-B: Bar chart dual axis
sparkta price weight, type(bar) over(rep78) nomissing ///
    y2(weight) y2title("Weight (lbs)") ///
    title("10-B: Dual axis bar")

di "--- Section 10 done ---"

// =============================================================================
// SECTION 11: REFERENCE ANNOTATIONS
// =============================================================================
di "--- SECTION 11: REFERENCE ANNOTATIONS ---"

// 11-A: yline single
sparkta price, type(bar) over(rep78) nomissing ///
    yline(6000) title("11-A: yline single")

// 11-B: yline multi + colors + labels
sparkta price, type(bar) over(rep78) nomissing ///
    yline(4000|8000) ylinecolor(red|blue) ylinelabel(Low|High) ///
    title("11-B: yline multi color label")

// 11-C: yband
sparkta price, type(bar) over(rep78) nomissing ///
    yband(4000 8000) ybandcolor(rgba(0,200,0,0.12)) ///
    title("11-C: yband")

// 11-D: xline on scatter + yline combined
sparkta price mpg, type(scatter) ///
    xline(20) yline(6000) ///
    xlinelabel(Avg MPG) ylinelabel(Avg Price) ///
    title("11-D: Scatter xline + yline")

// 11-E: xline ignored silently on bar (no error)
sparkta price, type(bar) over(rep78) nomissing ///
    xline(2) yline(5000) title("11-E: xline silently ignored on bar")

// 11-F: Histogram xline + xband
sparkta price, type(histogram) ///
    xline(6000) xband(4000 9000) ///
    title("11-F: Histogram xline xband")

// 11-G: apoint
sparkta price mpg, type(scatter) ///
    apoint(15000 25 20000 30) apointcolor(red|blue) apointsize(10) ///
    title("11-G: apoint")

// 11-H: alabelpos minute-clock + alabelgap
sparkta price mpg, type(scatter) ///
    alabelpos(15000 25 15|20000 30 30|10000 35 0) ///
    alabeltext(Right|Below|Center) alabelgap(18) ///
    title("11-H: alabelpos minute-clock positions")

// 11-I: aellipse
sparkta price mpg, type(scatter) ///
    aellipse(10000 20 20000 30) ///
    aellipsecolor(rgba(0,100,255,0.1)) aellipseborder(rgba(0,100,255,0.5)) ///
    title("11-I: aellipse")

// 11-J: All annotation types combined
sparkta price mpg, type(scatter) ///
    yline(10000) xline(20) ///
    yband(8000 12000) ybandcolor(rgba(200,200,0,0.1)) ///
    apoint(15000 25) apointcolor(red) apointsize(8) ///
    alabelpos(15000 25 30) alabeltext(Key point) alabelgap(15) ///
    aellipse(10000 18 20000 32) aellipsecolor(rgba(0,0,200,0.08)) ///
    title("11-J: All annotations combined")

// 11-K: Annotations + dark theme
sparkta price, type(line) over(rep78) nomissing ///
    yline(6000) ylinelabel(Benchmark) theme(dark) ///
    title("11-K: Annotations dark theme")

// 11-L: Annotations + by() panels
sparkta price, type(bar) over(rep78) by(foreign) ///
    yline(6000) yband(4000 8000) nomissing ///
    title("11-L: Annotations by() panels")

di "--- Section 11 done ---"

// =============================================================================
// SECTION 12: COLOR THEMES AND PALETTES
// =============================================================================
di "--- SECTION 12: THEMES AND PALETTES ---"

// 12-A: Dark theme
sparkta price weight, type(bar) over(foreign) theme(dark) ///
    title("12-A: Dark theme")

// 12-B: Named palette tab1
sparkta price, type(bar) over(rep78) nomissing theme(tab1) ///
    title("12-B: theme tab1")

// 12-C: Named palette cblind1 (colorblind safe)
sparkta price, type(bar) over(rep78) nomissing theme(cblind1) ///
    title("12-C: theme cblind1")

// 12-D: Compound theme dark_viridis
sparkta price, type(line) over(rep78) nomissing theme(dark_viridis) ///
    title("12-D: theme dark_viridis")

// 12-E: Compound theme light_neon
sparkta price weight, type(bar) over(foreign) theme(light_neon) ///
    title("12-E: theme light_neon")

// 12-F: colors() override
sparkta price weight, type(bar) over(foreign) ///
    colors("#e74c3c #3498db") title("12-F: colors() override")

di "--- Section 12 done ---"

// =============================================================================
// SECTION 13: FONT AND TOOLTIP STYLING
// =============================================================================
di "--- SECTION 13: FONT AND TOOLTIP STYLING ---"

// 13-A: Title/subtitle/note fonts
sparkta price weight, type(bar) over(foreign) ///
    title("13-A: Font styling") subtitle("1978 Auto Data") ///
    note("Source: Stata built-in auto dataset") notesize(12px) ///
    titlesize(26) titlecolor(#2c3e50) ///
    subtitlesize(14) subtitlecolor(#7f8c8d)

// 13-B: Axis title + label styling
sparkta price, type(bar) over(rep78) nomissing ///
    xtitle("Repair Record") ytitle("Mean Price") ///
    xtitlesize(13) xtitlecolor(#555) ///
    xlabsize(11) xlabcolor(#888) ///
    ylabsize(11) ylabcolor(#888) ///
    title("13-B: Axis font styling")

// 13-C: Tooltip styling
sparkta price, type(cibar) over(rep78) nomissing ///
    tooltipbg(rgba(0,0,0,0.9)) tooltipborder(#4e79a7) ///
    tooltipfontsize(13) tooltippadding(12) ///
    title("13-C: Tooltip styling")

// 13-D: Legend styling
sparkta price weight, type(bar) over(foreign) ///
    legend(bottom) legtitle("Car Origin") ///
    legsize(12) legcolor(#333) legbgcolor(rgba(240,240,240,0.9)) ///
    title("13-D: Legend styling")

// 13-E: leglabels rename
sparkta price, type(bar) over(foreign) ///
    leglabels(Domestic|Foreign) title("13-E: leglabels rename")

di "--- Section 13 done ---"

// =============================================================================
// SECTION 14: OFFLINE MODE
// =============================================================================
di "--- SECTION 14: OFFLINE MODE ---"

// 14-A: Basic offline
sparkta price weight, type(bar) over(foreign) offline ///
    export("sparkta_test_offline.html") ///
    title("14-A: Offline bar")

// 14-B: Offline with annotation (lib 5 bundled)
sparkta price, type(bar) over(rep78) nomissing offline ///
    yline(6000) ylinelabel(Benchmark) ///
    export("sparkta_test_offline_annot.html") ///
    title("14-B: Offline with annotation")

di "--- Section 14 done ---"

// =============================================================================
// SECTION 15: EXPORT
// =============================================================================
di "--- SECTION 15: EXPORT ---"

// 15-A: Export to file
sparkta price weight, type(bar) over(foreign) ///
    title("15-A: Export to file") ///
    export("sparkta_test_export.html")

di "--- Section 15 done ---"

// =============================================================================
di ""
di "============================================================"
di " SPARKTA RELEASE REGRESSION TEST COMPLETE"
di " If you see this line with no errors above: PASS"
di " Check all exported HTML files open correctly in browser"
di " Check browser console for any JS errors"
di "============================================================"
