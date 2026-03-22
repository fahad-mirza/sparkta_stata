<div align="center">

# sparkta

**Interactive, self-contained HTML charts/graphs and dashboards from Stata.**  
One command. Zero dependencies. No Python. No R. No server.

[![Stata 17+](https://img.shields.io/badge/Stata-17%2B-1a6fa0?style=flat-square)](https://www.stata.com)
[![Version](https://img.shields.io/badge/version-3.5.111-4a9eff?style=flat-square)](#)
[![License](https://img.shields.io/badge/license-MIT-22c55e?style=flat-square)](#)
[![Chart.js](https://img.shields.io/badge/Chart.js-4.4-f97316?style=flat-square)](https://www.chartjs.org)
[![SSC](https://img.shields.io/badge/SSC-ssc%20install%20sparkta,%20replace-8b5cf6?style=flat-square)](#installation)

```stata
sysuse auto, clear
sparkta price, type(cibar) over(rep78) title("Mean Price by Repair Record")
```

*Your browser opens. An interactive, shareable `.html` file is written to disk.*

**[Live chart/graph gallery (GitHub Pages)](https://fahad-mirza.github.io/sparkta_stata/)**

</div>

---

## What sparkta produces

Every `sparkta` command produces a single `.html` file that:

- Opens **instantly** in any browser -- no install, no plugins, no internet needed by the viewer
- Has **interactive tooltips**, hover highlights, and animated rendering
- Includes **live dropdown filters** that slice the data without any server
- Carries a **collapsible statistics panel** (N, Mean, Median, SD, Min, Max, CV) with distribution sparklines matching `summarize, detail` exactly
- Can be **emailed, shared on a USB drive, or archived** -- it is entirely self-contained

---

## Why not just use Python or R?

This is the honest comparison.

| | Stata `graph` | Python / R | **sparkta** |
|:---|:---:|:---:|:---:|
| Stata-native syntax | yes | no | **yes** |
| Interactive tooltips | no | yes | **yes** |
| Live filter dropdowns | no | needs server | **yes, self-contained** |
| Output is a single shareable file | no | no | **yes** |
| Viewer needs zero software | yes | no | **yes** |
| Offline / air-gapped use | yes | no | **yes** |
| Data stays on your machine | yes | no | **yes** |
| CI whiskers matching `ci means` | no | no | **yes** |
| Stats panel matching `summarize` | no | no | **yes** |
| Runs inside your existing do-file | yes | no | **yes** |

Python and R produce interactive charts/graphs -- but only if your viewer installs the right
libraries, or if you run a Shiny or Dash server. The output is never truly self-contained.
sparkta produces a single `.html` file that anyone can open, anywhere, forever.

### Performance

Chart/graphs generation runs entirely inside Stata via the Java Plugin Interface.
There is no export step, no subprocess, no round-trip.

| Dataset size | Generation time | HTML open time |
|:---|:---:|:---:|
| ~500 obs | < 0.2 s | instant |
| ~10,000 obs | ~0.4 s | instant |
| ~100,000 obs | ~0.8 s | instant |
| ~500,000 obs | 1.0 -- 1.5 s | instant |

The HTML file renders immediately. Even 500-group graphs stay under 3 MB thanks
to lazy sparkline rendering -- group statistics panels load as you scroll, not all at once.

---

## Data privacy and institutional use

For researchers working with sensitive data, sparkta provides something no
cloud or browser-based tool can match: **your data never leaves Stata**.

- All processing runs on your local machine via the Stata Java Plugin Interface
- The output HTML has no data-fetching code, no callbacks, no external requests
- No telemetry of any kind

The `offline` option goes further: it embeds all JavaScript inside the HTML so
the file makes **zero external network requests** when opened in a browser.

```stata
* Fully air-gapped -- all JS bundled inside the HTML (~280 KB)
sparkta price, type(cibar) over(rep78) offline export("secure/chart.html")
```

This is appropriate for:
- IRB-restricted and clinical trial data
- Financial and proprietary records
- Institutional networks that block external CDN requests
- Permanent archival -- the file renders identically in any browser, years from now

sparkta also checks for all required JS libraries at startup and **fails immediately**
with a clear, actionable error if any are missing. There is no silent CDN fallback.

---

## Installation

**Option 1 -- from SSC**:

```stata
ssc install sparkta, replace
```

**Option 2 -- from GitHub** (available now):

```stata
net install sparkta, from("https://raw.githubusercontent.com/fahad-mirza/sparkta_stata/main/ado/")
```

**Verify the installation:**

```stata
sysuse auto, clear
sparkta price, over(rep78)
```

A graph should open in your browser. If you see `[sparkta v3.5.111]` in the Stata output, the installation is working.

**Requirements:** Stata 17+ with Java 8+ (bundled with most Stata installations since version 16).

---

## Quick start

```stata
sysuse auto, clear

* The simplest call -- one variable, default interactive bar chart/graph
sparkta price

* Group by a categorical variable
sparkta price, over(rep78)

* CI bars -- t-distribution intervals matching ci means exactly
sparkta price, type(cibar) over(rep78)

* Violin plot with animated KDE density
sparkta price, type(violin) over(rep78)

* Scatter with fit line and live CI band
sparkta price mpg, type(scatter) fit(lfit) fitci sliders(mpg)

* Unlimited live filter dropdowns -- filters data live, no server needed
sparkta price, over(rep78) filters(foreign headroom)

* Dual-handle range sliders -- stats panel updates live
sparkta price, over(rep78) sliders(mpg price)

* Save to a file instead of auto-opening browser
sparkta price, type(cibar) over(rep78) export("~/Desktop/chart.html")

* Fully offline -- embeds all JS, zero network requests when opened
sparkta price, over(rep78) offline export("~/secure/chart.html")
```

---

## Chart/graph types (20+ total)

### Core charts/graphs

```stata
sysuse auto, clear

sparkta price, type(bar) over(rep78)               // grouped bar
sparkta price, type(hbar) over(rep78)              // horizontal bar
sparkta price, type(line) over(rep78)              // line
sparkta price, type(area) over(rep78)              // filled area
sparkta price mpg, type(scatter) over(foreign)     // scatter coloured by group
sparkta price mpg weight, type(bubble)             // bubble  (y x size)
sparkta price, type(pie)   over(foreign)           // pie
sparkta price, type(donut) over(foreign)           // donut
```

### Scatter fit lines and CI bands

```stata
sysuse auto, clear

* 7 fit types -- computed in Stata, rendered in Chart.js
sparkta price mpg, type(scatter) fit(lfit)         // linear OLS
sparkta price mpg, type(scatter) fit(qfit)         // quadratic OLS
sparkta price mpg, type(scatter) fit(lowess)       // locally weighted smoother
sparkta price mpg, type(scatter) fit(exp)          // exponential
sparkta price mpg, type(scatter) fit(log)          // logarithmic
sparkta price mpg, type(scatter) fit(power)        // power
sparkta price mpg, type(scatter) fit(ma)           // 5-point moving average

* Add a CI band that recomputes live when sliders change
sparkta price mpg, type(scatter) fit(lfit) fitci

* Separate fit + CI band per over() group
sparkta price mpg, type(scatter) over(foreign) fit(lowess) fitci

* Combine with sliders -- CI band recomputes as you drag
sparkta price mpg, type(scatter) fit(lfit) fitci sliders(mpg)
```

### Statistical charts/graphs

These are unique to sparkta -- no other Stata visualization package produces them.

```stata
* CI bars -- standard error from t-distribution, matches Stata ci means
sparkta price, type(cibar) over(rep78)
sparkta price, type(cibar) over(rep78) cilevel(90)
sparkta price, type(cibar) over(rep78) stat(median)

* CI line with shaded confidence band
sparkta price, type(ciline) over(rep78)

* Histogram -- auto Sturges bins or user-specified, three display modes
sparkta price, type(histogram)
sparkta price, type(histogram) bins(20) histtype(density)
sparkta price, type(histogram) histtype(fraction)
```

### Distribution charts/graphs

```stata
* Box and whisker -- Tukey fences, outlier dots, IQR box
sparkta price, type(boxplot) over(rep78)
sparkta price, type(hbox)    over(rep78)           // horizontal
sparkta price, type(boxplot) over(rep78) whiskerfence(3)  // 3x IQR fence

* Violin -- animated KDE density + IQR box + whiskers
sparkta price, type(violin)  over(rep78)
sparkta price, type(hviolin) over(rep78)           // horizontal
sparkta price, type(violin)  over(rep78) bandwidth(1500)
```

### Stacked charts/graphs

```stata
sysuse nlsw88, clear

sparkta wage, over(race) type(stackedbar)
sparkta wage, over(race) type(stackedhbar)
sparkta wage, over(race) type(stackedbar100)       // composition view -- bars sum to 100%
sparkta wage, over(race) type(stackedhbar100)
sparkta price weight length, over(rep78) type(stackedline)
sparkta price weight length, over(rep78) type(stackedarea)
```

---

## Grouping, panels, and filters

```stata
sysuse auto, clear

* over() -- one coloured series per group, all on one chart/graph
sparkta price, over(rep78)

* by() -- separate panel per group value, rendered side by side
sparkta price, over(rep78) by(foreign)

* filters() -- unlimited interactive dropdowns, filters data live
sparkta price, over(rep78) filters(foreign)
sparkta price, over(rep78) filters(foreign headroom rep78)  // any number of vars

* sliders() -- dual-handle numeric range controls
sparkta price, over(rep78) sliders(mpg)
sparkta price, over(rep78) sliders(mpg price weight)       // any numeric vars

* Combine filters and sliders freely
sparkta price, over(rep78) filters(foreign) sliders(mpg price)

* Stats panel updates live on every filter and slider interaction
* String variables work exactly the same way
sysuse nlsw88, clear
sparkta wage, over(industry) filters(occupation)
```

---

## Reference annotations

Draw lines, bands, labelled points, and ellipses on any chart/graph.

```stata
sysuse auto, clear

* Horizontal reference line with label and custom color
sparkta price, over(rep78) ///
    yline(6000) ylinelabel("Avg list price") ylinecolor(#e74c3c)

* Multiple reference lines at different thresholds
sparkta price, over(rep78) yline(4000 6000 9000)

* Shaded reference band
sparkta price, over(rep78) ///
    yband(4500 7500) ybandcolor(rgba(52,152,219,0.15))

* Lines and bands together
sparkta price, over(rep78) ///
    yline(6000) ylinelabel("Target")          ///
    yband(4500 7500) ybandcolor(rgba(52,152,219,0.12))

* Vertical line on scatter (x-axis)
sparkta price mpg, type(scatter) ///
    xline(25) xlinelabel("Fuel threshold") xlinecolor(#e74c3c)

* Annotated point -- Stata scattieri-style  y|x  syntax
sparkta price mpg, type(scatter) ///
    apoint(12000|5) apointcolor(#e74c3c) apointsize(10) ///
    alabeltext("Outlier") alabelpos(15)

* Ellipse highlight over a cluster
sparkta price mpg, type(scatter) ///
    aellipse(10000 14000|15 25) aellipsecolor(rgba(231,76,60,0.15))
```

---

## Theming

```stata
sysuse auto, clear

sparkta price, over(rep78) theme(dark)             // dark background
sparkta price, over(rep78) theme(light)            // light background
sparkta price, over(rep78) theme(cblind1)          // Okabe-Ito colorblind-safe
sparkta price, over(rep78) theme(tab1)             // Tableau 10 palette
sparkta price, over(rep78) theme(tab2)             // ColorBrewer Set1
sparkta price, over(rep78) theme(viridis)          // perceptually uniform
sparkta price, over(rep78) theme(neon)             // bright saturated, best on dark

* Compound: background + palette in one option
sparkta price, over(rep78) theme(dark_viridis)
sparkta price, over(rep78) theme(dark_neon)
sparkta price, over(rep78) theme(light_tab2)

* Manual color list always overrides any theme
sparkta price, over(rep78) colors(#e74c3c #3498db #2ecc71 #f39c12 #9b59b6)
```

---

## Styling

All color options accept hex, `rgb()`, `rgba()`, or CSS named colors.
All size options accept a number in pt or a Stata size keyword (`small`, `medium`, `large`).

```stata
sysuse auto, clear

* Title and axis typography
sparkta price, over(rep78)                         ///
    titlesize(26) titlecolor(#2c3e50)              ///
    xtitlesize(13) xtitlecolor(#7f8c8d)            ///
    ylabsize(11)   ylabcolor(#95a5a6)

* Tooltip appearance
sparkta price, over(rep78)                         ///
    tooltipbg(rgba(0,0,0,0.9))                     ///
    tooltipborder(#3498db)                         ///
    tooltipfontsize(13) tooltippadding(10)

* Line dash patterns
sparkta price weight, type(line) over(foreign)     ///
    lpattern(dash) linewidth(2) nopoints

* Gradient fill on area charts/graphs
sparkta price, type(area) over(rep78) gradient

* PNG download button embedded in the chart/graph header
sparkta price, type(cibar) over(rep78) download

* Note and subtitle
sparkta price, over(rep78)                         ///
    subtitle("Stata auto dataset, 74 automobiles") ///
    note("Source: Stata built-in dataset, 1978")
```

---

## Secondary y-axis

```stata
sysuse auto, clear

* price on left y-axis, mpg on right y-axis, same chart/graph
sparkta price mpg, type(line) over(foreign) ///
    y2(mpg) y2title("Fuel economy (MPG)") ytitle("Price (USD)")
```

---

## Summary statistics panel

Every chart/graph includes a collapsible statistics panel computed entirely inside Stata.

- **Matches `summarize, detail` exactly** -- same N, Mean, Median, SD, Min, Max, CV
- Per-group breakdown with an IQR sparkline distribution for each group
- Updates live when filter dropdowns change
- `nostats` suppresses the panel entirely

The panel is computed before the chart/graph renders. The numbers are always consistent
with your Stata results window, regardless of filter or display settings.

---

## Full option reference

```stata
help sparkta
```

| Group | Options |
|:---|:---|
| Chart/graph type | `type()` |
| Grouping | `over()` `by()` `filters()` `sliders()` |
| Statistics | `stat()` `cilevel()` `histtype()` `bins()` |
| Fit lines | `fit()` `fitci` |
| Axes | `yrange()` `xrange()` `ytitle()` `xtitle()` `xlabels()` `ylabels()` `yreverse` `xreverse` `ygrace()` `noticks` `xticks()` `yticks()` |
| Annotations | `yline()` `xline()` `yband()` `xband()` `ylinelabel()` `xlinelabel()` `apoint()` `alabeltext()` `aellipse()` |
| Line / point | `lpattern()` `linewidth()` `nopoints` `smooth` `pointsize()` `pointhoversize()` |
| Typography | `titlesize()` `titlecolor()` `subtitlesize()` `xtitlesize()` `xlabsize()` `xlabcolor()` `tooltipbg()` `tooltipfontsize()` `notesize()` |
| Legend | `legend()` `legtitle()` `leglabels()` `legsize()` `nolegend` `relabel()` |
| Theme | `theme()` `colors()` `bgcolor()` `plotcolor()` `gradient` |
| Export | `export()` `offline` `download` |
| Box / violin | `whiskerfence()` `bandwidth()` `mediancolor()` `meancolor()` |
| Secondary axis | `y2()` `y2title()` `y2range()` |
| Labelling | `title()` `subtitle()` `note()` `caption()` `xlabels()` `ylabels()` |

---

## Repository structure

```
ado/
  sparkta.ado            Stata command
  sparkta.sthlp          Help file  (help sparkta)
  sparkta.jar            Pre-compiled Java plugin
examples/
  basic_charts.do        Bar, line, scatter, area, pie, donut
  stat_charts.do         CI bar, CI line, histogram
  boxviolin.do           Boxplot, violin, whiskerfence, bandwidth
  offline_mode.do        Air-gapped workflow
docs/
  index.html             Live interactive chart/graph gallery (GitHub Pages)
  INSTALL.md
  CHANGELOG.md
java/
  src/                   Java source (developers only)
  build.bat / build.sh
  fetch_js_libs.bat / fetch_js_libs.sh
```

---

## Build from source (developers only)

The pre-compiled `ado/sparkta.jar` is ready to use. To rebuild from source:

```
cd java/

# Step 1 -- download bundled JS libraries (once, before first build)
fetch_js_libs.bat        (Windows)
fetch_js_libs.sh         (Mac / Linux)

# Step 2 -- compile
build.bat                (Windows)
build.sh                 (Mac / Linux)
```

**Order matters.** JS libraries must be downloaded before compilation so they
are bundled inside the jar for offline use. Skipping Step 1 causes `offline`
graphs to fail at render time with a clear pre-flight error message.

Requirements: Java 8+ JDK, Stata 17+

---

## Platform support

| Platform | Status |
|:---|:---|
| Windows (Stata 17-19, Java 8-21) | Fully tested |
| Mac (Intel / Apple Silicon) | Jar is platform-independent; browser auto-open uses `open` |
| Linux | Jar is platform-independent; browser auto-open uses `xdg-open` |

Chart/graph generation and HTML export work on all platforms.
If you verify Mac or Linux, please open a GitHub issue with your Stata version and OS.

---

## Authors

**Fahad Mirza** -- Author and developer  
[LinkedIn](https://www.linkedin.com/in/fahad-mirza/) | [GitHub](https://github.com/fahad-mirza/sparkta_stata)

**Claude** (Anthropic) -- Co-developer  
Algorithm design, Java rendering engine, Chart.js integration, statistical methods, debugging.

---

<div align="center">

*Powered by [Chart.js 4.4](https://www.chartjs.org/) &nbsp;|&nbsp; Stata Java Plugin Interface &nbsp;|&nbsp; Statistics match* `summarize, detail` *exactly*

</div>
