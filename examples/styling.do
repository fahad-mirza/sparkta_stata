*! sparkta example: styling.do
*! Demonstrates: v2.6.0 typography and tooltip styling options
*! Dataset: auto (built-in)
*! Version: 3.5.96 | 2026-03-17
* Run with: do styling.do

sysuse auto, clear

* ============================================================
* TITLE AND SUBTITLE TYPOGRAPHY
* ============================================================

* Custom title size and color
sparkta price weight, type(bar) over(foreign) ///
    titlesize(28) titlecolor(#2c3e50) ///
    subtitlesize(14) subtitlecolor(#7f8c8d) ///
    title("Custom Title Size and Color") ///
    subtitle("Subtitle styled independently")
    * export("~/Desktop/st_title.html")

* ============================================================
* AXIS TITLE AND TICK LABEL STYLING
* ============================================================

* Axis title and tick label sizes and colors
sparkta price, type(cibar) over(rep78) ///
    xtitlesize(13) xtitlecolor(#16a085) ///
    ytitlesize(13) ytitlecolor(#8e44ad) ///
    xlabsize(11)   xlabcolor(#555) ///
    ylabsize(11)   ylabcolor(#555) ///
    xtitle("Repair Record (1=Poor, 5=Excellent)") ///
    ytitle("Mean Price (USD)") ///
    title("Axis Typography -- Styled Titles and Tick Labels")
    * export("~/Desktop/st_axis.html")

* ============================================================
* LEGEND STYLING
* ============================================================

* Legend text color and background
sparkta price weight, type(bar) over(foreign) ///
    legend(bottom) legtitle("Variable") ///
    legcolor(#2c3e50) legbgcolor(rgba(245,245,245,0.9)) ///
    title("Legend Styling -- Color and Background")
    * export("~/Desktop/st_legend.html")

* ============================================================
* TOOLTIP STYLING
* ============================================================

* Dark tooltip with accent border
sparkta price, type(cibar) over(rep78) ///
    tooltipbg(rgba(0,0,0,0.9)) ///
    tooltipborder(#4e79a7) ///
    tooltipfontsize(13) ///
    tooltippadding(12) ///
    title("Tooltip Styling -- Dark Background with Blue Border")
    * export("~/Desktop/st_tooltip.html")

* ============================================================
* DARK THEME -- FULL CUSTOM TYPOGRAPHY
* ============================================================

sparkta price weight, type(bar) over(foreign) ///
    theme(dark) ///
    titlesize(24) titlecolor(#ecf0f1) ///
    subtitlesize(13) subtitlecolor(#bdc3c7) ///
    xtitlecolor(#bdc3c7) ytitlecolor(#bdc3c7) ///
    xlabcolor(#95a5a6) ylabcolor(#95a5a6) ///
    legcolor(#ecf0f1) legbgcolor(rgba(0,0,0,0.35)) ///
    tooltipbg(rgba(15,15,25,0.95)) tooltipborder(#3498db) ///
    tooltipfontsize(13) tooltippadding(12) ///
    legend(bottom) legtitle("Variable") ///
    title("Dark Theme -- Full Custom Typography") ///
    subtitle("All v2.6.0 styling options applied")
    * export("~/Desktop/st_dark_full.html")

di "styling.do complete"
