<div align="center">

# sparkta

**Interactive, self-contained HTML charts and dashboards from Stata.**  
One command. Zero dependencies. No Python. No R. No server.

[![Stata 17+](https://img.shields.io/badge/Stata-17%2B-1a6fa0?style=flat-square)](https://www.stata.com)
[![Version](https://img.shields.io/badge/version-3.5.96-4a9eff?style=flat-square)](#)
[![License](https://img.shields.io/badge/license-MIT-22c55e?style=flat-square)](#)
[![Chart.js](https://img.shields.io/badge/Chart.js-4.4-f97316?style=flat-square)](https://www.chartjs.org)
[![SSC](https://img.shields.io/badge/SSC-ssc%20install%20sparkta-8b5cf6?style=flat-square)](#installation)

```stata
sysuse auto, clear
sparkta price, type(cibar) over(rep78) title("Mean Price by Repair Record")
```

*Your browser opens. An interactive, shareable `.html` file is written to disk.*

**[Live chart gallery (GitHub Pages)](https://fahad-mirza.github.io/sparkta_stata/)**

</div>

---

## What is new in v3.5.96

> **Major update.** If you installed sparkta previously, this version brings
> substantial new features. Update with:
> ```stata
> net install sparkta, from("https://raw.githubusercontent.com/fahad-mirza/sparkta_stata/main/ado/") replace
> ```

### New features since last GitHub release

| Feature | Description |
|:---|:---|
| **Live filter dropdowns** | `filters(varlist)` adds interactive dropdowns. Viewers filter the chart live -- no server, no page reload. Any number of variables. |
| **Live stats panel update** | The N, Mean, Median, SD, Min, Max, CV table now updates instantly when a filter changes, showing statistics for only the filtered rows. |
| **Dual-handle range sliders** | `sliders(varlist)` adds min/max range sliders for any numeric variable. Drag to filter a continuous range interactively. |
| **by() panels with filters** | Filter changes update all `by()` panels simultaneously and independently. |
| **Scatter fit lines** | `fit(lfit\|qfit\|lowess\|exp\|log\|power\|ma)` overlays a fitted line. `fitci` adds a shaded confidence band. |
| **Per-group fit lines** | `fit()` with `over()` draws a separate fitted line per group in each group colour. |
| **Marker labels on scatter** | `mlabel(varname)` labels scatter points with any variable. `mlabpos()` sets clock-position. |
| **Reference annotations** | `yline()` `xline()` `yband()` `xband()` for reference lines and shaded bands. `apoint()` `alabeltext()` `aellipse()` for annotated points and ellipses. All support colours and text. |
| **Named colour palettes** | `theme(cblind1\|tab1\|tab2\|viridis\|neon\|...)` and compound themes `dark_viridis`, `light_tab2` etc. |
| **Legend and label overrides** | `leglabels()` renames legend entries. `relabel()` renames over-group labels on both axis and legend. `xticks()` `yticks()` for custom tick positions. |
| **Gradient fills** | `gradient` on bar and area charts. `gradcolors()` for per-series control. |
| **Secondary y-axis** | `y2(varlist)` plots variables on a right-hand axis with independent scale. |
| **Stacked horizontal bar** | `type(stackedhbar)` and `type(stackedhbar100)` complete the stacked chart family. |
| **Performance** | 500k obs x 100 groups: generation time 0.5s (was 5.5s). File size for 500-group charts: ~2-3 MB (was ~40 MB). O(N) group indexing, lazy sparkline rendering, bulk JNI reads. |

### Filter syntax change

The old `filter()` and `filter2()` options still work. The new `filters(varlist)` replaces them with a single option that accepts any number of variables:

```stata
* Old (still accepted)
sparkta price, over(rep78) filter(foreign)

* New -- unlimited filters in one option
sparkta price, over(rep78) filters(foreign rep78)

* With by() panels
sparkta price, over(rep78) by(foreign) filters(rep78)

* With range sliders
sparkta price mpg, type(scatter) sliders(mpg)
```

---

## What sparkta produces

Every `sparkta` command writes a single `.html` file that:

- Opens **instantly** in any browser -- no install, no plugins, no internet needed
- Has **interactive tooltips**, hover highlights, and animated rendering
- Includes **live dropdown filters** and **range sliders** that slice data without a server
- Carries a **collapsible statistics panel** (N, Mean, Median, SD, Min, Max, CV)
  with distribution sparklines that **update live when filters change**
- Can be **emailed, shared on USB, or archived** -- entirely self-contained

---

## Why not just use Python or R?

| | Stata `graph` | Python / R | **sparkta** |
|:---|:---:|:---:|:---:|
| Stata-native syntax | yes | no | **yes** |
| Interactive tooltips | no | yes | **yes** |
| Live filter dropdowns | no | needs server | **yes, self-contained** |
| Live stats panel update | no | no | **yes** |
| Range sliders | no | needs server | **yes, self-contained** |
| Single shareable file | no | no | **yes** |
| Viewer needs zero software | yes | no | **yes** |
| Offline / air-gapped | yes | no | **yes** |
| Data stays on your machine | yes | no | **yes** |
| CI whiskers matching `ci means` | no | no | **yes** |
| Stats panel matching `summarize` | no | no | **yes** |
| Runs inside your existing do-file | yes | no | **yes** |

---

## Performance

Generation runs entirely inside Stata via the Java Plugin Interface.

| Dataset size | Generation time | HTML open time |
|:---|:---:|:---:|
| ~500 obs | < 0.2 s | instant |
| ~10,000 obs | ~0.4 s | instant |
| ~100,000 obs | ~0.8 s | instant |
| ~500,000 obs | ~1.0 s | instant |

Even 500-group charts stay under 3 MB thanks to lazy sparkline rendering.

---

## Data privacy

All processing runs locally via the Stata Java Plugin Interface. The output HTML
has no data-fetching code, no callbacks, no external requests. The `offline` option
bundles all JavaScript inside the file (~280 KB) for zero network requests when opened.

```stata
sparkta price, type(cibar) over(rep78) offline export("secure/chart.html")
```

Suitable for IRB-restricted data, financial records, institutional networks that
block CDN requests, and permanent archival.

---

## Installation

**From GitHub** (available now):

```stata
net install sparkta, from("https://raw.githubusercontent.com/fahad-mirza/sparkta_stata/main/ado/")
```

**From SSC** (once listed):

```stata
ssc install sparkta
```

**Update an existing installation:**

```stata
net install sparkta, from("https://raw.githubusercontent.com/fahad-mirza/sparkta_stata/main/ado/") replace
```

**Verify:**

```stata
sysuse auto, clear
sparkta price, over(foreign)
```

**Requirements:** Stata 17+ with Java 8+ (bundled with most Stata installations since version 16).

---

## Quick start

```stata
sysuse auto, clear

* Simplest possible call
sparkta price

* Group by a variable
sparkta price, over(rep78)

* CI bars with live filter
sparkta price, type(cibar) over(rep78) filters(foreign)

* Scatter with lowess fit line
sparkta price mpg, type(scatter) fit(lowess) over(foreign)

* Violin plot
sparkta price, type(violin) over(rep78)

* Save to a file
sparkta price, type(cibar) over(rep78) export("~/Desktop/chart.html")

* Fully offline -- zero network requests when opened
sparkta price, over(rep78) offline export("~/secure/chart.html")
```

---

## Chart types (20+)

### Bar family

```stata
sysuse auto, clear
sparkta price, type(bar)          over(rep78)   // grouped bar
sparkta price, type(hbar)         over(rep78)   // horizontal bar
sparkta price, type(stackedbar)   over(rep78)   // stacked
sparkta price, type(stackedhbar)  over(rep78)   // stacked horizontal
sparkta price, type(stackedbar100)  over(rep78) // 100 pct stacked
sparkta price, type(stackedhbar100) over(rep78) // 100 pct stacked horizontal
```

### Line and area

```stata
sparkta price, type(line)        over(rep78)
sparkta price, type(stackedline) over(rep78)
sparkta price, type(area)        over(rep78)
sparkta price, type(stackedarea) over(rep78)
```

### Scatter and bubble

```stata
sparkta price mpg,        type(scatter) over(foreign)
sparkta price mpg weight, type(bubble)              // y x size
```

### Pie and donut

```stata
sparkta price, type(pie)   over(foreign)
sparkta price, type(donut) over(foreign) cutout(65)
```

### Statistical (unique to sparkta)

```stata
sparkta price, type(cibar)  over(rep78)             // CI bar, t-distribution
sparkta price, type(ciline) over(rep78)             // CI line with shaded band
sparkta price, type(histogram)                      // auto Sturges bins
sparkta price, type(histogram) bins(20) histtype(density)
```

### Distribution

```stata
sparkta price, type(boxplot)  over(rep78)           // Tukey fences, outlier dots
sparkta price, type(hbox)     over(rep78)           // horizontal
sparkta price, type(violin)   over(rep78)           // KDE density + IQR box
sparkta price, type(hviolin)  over(rep78)           // horizontal
```

---

## Grouping, panels, and filters

```stata
sysuse auto, clear

* over() -- one coloured series per group, one chart
sparkta price, over(rep78)

* by() -- separate panel per group value
sparkta price, over(rep78) by(foreign)

* filters() -- live interactive dropdowns
sparkta price, over(rep78) filters(foreign)
sparkta price, over(rep78) filters(foreign rep78)

* sliders() -- dual-handle range sliders
sparkta price mpg, type(scatter) sliders(mpg)

* Combined
sparkta price, over(rep78) by(foreign) filters(rep78) sliders(price)
```

---

## Scatter fit lines

```stata
sysuse auto, clear

sparkta price mpg, type(scatter) fit(lfit)    // linear regression
sparkta price mpg, type(scatter) fit(qfit)    // quadratic
sparkta price mpg, type(scatter) fit(lowess)  // LOWESS smooth
sparkta price mpg, type(scatter) fit(exp)     // exponential
sparkta price mpg, type(scatter) fit(log)     // logarithmic
sparkta price mpg, type(scatter) fit(power)   // power
sparkta price mpg, type(scatter) fit(ma)      // moving average

* With shaded confidence band (lfit and qfit only)
sparkta price mpg, type(scatter) fit(lfit) fitci

* Per-group fit lines
sparkta price mpg, type(scatter) over(foreign) fit(lowess)
```

---

## Reference annotations

```stata
sysuse auto, clear

* Horizontal reference line with label and colour
sparkta price, over(rep78) ///
    yline(6000) ylinelabel(Average) ylinecolor(#e74c3c)

* Multiple reference lines
sparkta price, over(rep78) yline(4000|6000|9000)

* Shaded horizontal band
sparkta price, over(rep78) ///
    yband(4500 7500) ybandcolor(rgba(52,152,219,0.15))

* Vertical line on scatter
sparkta price mpg, type(scatter) ///
    xline(25) xlinelabel(Fuel threshold) xlinecolor(#e74c3c)

* Annotated point with label
sparkta price mpg, type(scatter) ///
    apoint(12000 5) apointcolor(#e74c3c) apointsize(10) ///
    alabeltext(Outlier) alabelpos(12000 5 15) alabelfs(12)

* Ellipse over a cluster
sparkta price mpg, type(scatter) ///
    aellipse(10000 14000 15 25) aellipsecolor(rgba(231,76,60,0.15))
```

---

## Theming

```stata
sysuse auto, clear

sparkta price, over(rep78) theme(dark)             // dark background
sparkta price, over(rep78) theme(cblind1)          // Okabe-Ito colorblind-safe
sparkta price, over(rep78) theme(tab1)             // Tableau 10
sparkta price, over(rep78) theme(tab2)             // ColorBrewer Set1
sparkta price, over(rep78) theme(viridis)          // perceptually uniform
sparkta price, over(rep78) theme(neon)             // bright, best on dark bg
sparkta price, over(rep78) theme(swift_red)        // warm earth tones

* Compound: background + palette in one option
sparkta price, over(rep78) theme(dark_viridis)
sparkta price, over(rep78) theme(dark_neon)
sparkta price, over(rep78) theme(light_tab2)

* Manual colour list always overrides any theme
sparkta price, over(rep78) colors("#e74c3c #3498db #2ecc71 #f39c12 #9b59b6")
```

---

## Styling

All colour options accept hex `#rrggbb`, `rgb()`, `rgba()`, or CSS named colours.

```stata
sysuse auto, clear

* Title typography
sparkta price, over(rep78) ///
    titlesize(24) titlecolor(#2c3e50) ///
    subtitle("1978 Automobile Data") subtitlecolor(#7f8c8d) ///
    note("Source: Stata built-in dataset")

* Axis styling
sparkta price, over(rep78) ///
    xtitlesize(13) xtitlecolor(#555) ///
    xlabsize(11)   xlabcolor(#777)

* Tooltip appearance
sparkta price, over(rep78) ///
    tooltipbg(rgba(0,0,0,0.9)) tooltipborder(#3498db) tooltipfontsize(13)

* Bar appearance
sparkta price, over(rep78) barwidth(0.6) borderradius(4) opacity(0.85)

* Line dash patterns
sparkta price mpg, type(line) over(foreign) lpattern(dash) linewidth(2)

* Gradient fill
sparkta price, type(area) over(rep78) gradient

* PNG download button embedded in chart header
sparkta price, type(cibar) over(rep78) download
```

---

## Summary statistics panel

Every chart includes a collapsible statistics panel computed inside Stata:

- Matches `summarize, detail` exactly (N, Mean, Median, SD, Min, Max, CV)
- Per-group rows with IQR sparkline distributions
- **Updates live when filter dropdowns or sliders change**
- `nostats` suppresses it entirely

---

## Full option reference

```stata
help sparkta
```

| Group | Options |
|:---|:---|
| Chart type | `type()` |
| Grouping | `over()` `by()` `filters()` `sliders()` |
| Statistics | `stat()` `cilevel()` `histtype()` `bins()` |
| Axes | `yrange()` `xrange()` `ytitle()` `xtitle()` `ytype()` `xtype()` `yreverse` `xreverse` `ygrace()` `noticks` `xticks()` `yticks()` `ystepsize()` `ytickcount()` |
| Fit lines | `fit()` `fitci` |
| Marker labels | `mlabel()` `mlabpos()` `mlabvposition()` |
| Annotations | `yline()` `xline()` `yband()` `xband()` `ylinelabel()` `xlinelabel()` `apoint()` `alabeltext()` `alabelpos()` `alabelfs()` `alabelgap()` `aellipse()` |
| Line / point | `lpattern()` `lpatterns()` `linewidth()` `nopoints` `smooth()` `stepped()` `spanmissing` `pointsize()` `pointstyle()` `pointhoversize()` |
| Typography | `titlesize()` `titlecolor()` `subtitlesize()` `subtitlecolor()` `xtitlesize()` `xtitlecolor()` `xlabsize()` `xlabcolor()` `tooltipbg()` `tooltipborder()` `tooltipfontsize()` `tooltippadding()` `notesize()` |
| Legend | `legend()` `legtitle()` `legsize()` `legbgcolor()` `leglabels()` `nolegend` `relabel()` |
| Theme | `theme()` `colors()` `bgcolor()` `plotcolor()` `gridcolor()` `gridopacity()` `gradient` `gradcolors()` |
| Export | `export()` `offline` `download` |
| Bar | `barwidth()` `bargroupwidth()` `borderradius()` `opacity()` `datalabels` |
| Pie / donut | `cutout()` `rotation()` `circumference()` `sliceborder()` `pielabels` |
| Box / violin | `whiskerfence()` `bandwidth()` `mediancolor()` `meancolor()` |
| Secondary axis | `y2()` `y2title()` `y2range()` |
| Labels | `title()` `subtitle()` `note()` `caption()` `xlabels()` `ylabels()` |
| Data | `nomissing` `sortgroups()` `novaluelabels` `nostats` |
| Animation | `animate()` `animduration()` `animdelay()` `easing()` |
| Layout | `aspect()` `padding()` `layout()` |

---

## Repository structure

```
ado/
  sparkta.ado            Stata command
  sparkta.sthlp          Help file  (help sparkta)
  sparkta.jar            Pre-compiled Java plugin
  sparkta.pkg            SSC package descriptor
  stata.toc              SSC table of contents
dist/
  sparkta.jar            Pre-compiled jar (also copied to ado/)
examples/
  basic_charts.do        Bar, line, scatter, area, pie, donut
  stat_charts.do         CI bar, CI line, histogram
  boxviolin.do           Boxplot, violin, whiskerfence, bandwidth
  offline_mode.do        Air-gapped workflow
docs/
  INSTALL.md
  CHANGELOG.md
java/
  src/                   Java source (developers only)
  build.bat / build.sh
  fetch_js_libs.bat / fetch_js_libs.sh
  check_build.py
```

---

## Build from source (developers only)

The pre-compiled `ado/sparkta.jar` is ready to use. To rebuild from source:

```
cd java/

# Step 1: download bundled JS libraries (once, before first build)
fetch_js_libs.bat        (Windows)
fetch_js_libs.sh         (Mac / Linux)

# Step 2: compile
build.bat                (Windows)
build.sh                 (Mac / Linux)
```

**Order matters.** JS libraries must be fetched before compiling so they
are bundled inside the jar for offline use. Build target: Java 11 (`--release 11`).

---

## Platform support

| Platform | Status |
|:---|:---|
| Windows (Stata 17+, Java 11-21) | Fully tested |
| Mac (Intel / Apple Silicon) | Jar is platform-independent; browser auto-open via `open` |
| Linux | Jar is platform-independent; browser auto-open via `xdg-open` |

---

## Authors

**Fahad Mirza** -- Author and developer  
[LinkedIn](https://www.linkedin.com/in/fahad-mirza/) | [GitHub](https://github.com/fahad-mirza/sparkta_stata)

**Claude** (Anthropic) -- Co-developer  
Algorithm design, Java rendering engine, Chart.js integration, statistical methods, filter engine, debugging.

---

<div align="center">

*Powered by [Chart.js 4.4](https://www.chartjs.org/) &nbsp;|&nbsp; Stata Java Plugin Interface &nbsp;|&nbsp; Statistics match* `summarize, detail` *exactly*

</div>
