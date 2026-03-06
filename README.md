# sparkta

**Interactive self-contained HTML charts and dashboards from Stata -- one command, zero dependencies.**

```stata
sysuse auto, clear
sparkta price, type(cibar) over(rep78) title("Mean Price with 95% CI")
```

Opens a fully interactive chart in your browser. Share the `.html` file with anyone -- no R, no Python, no server, no internet connection required by the viewer.

---

## Why sparkta?

| Feature | `graph` | Plotly / Shiny | **sparkta** |
|---|---|---|---|
| Interactive tooltips | No | Yes | **Yes** |
| Filter dropdowns | No | Yes (server) | **Yes (self-contained)** |
| Self-contained HTML file | No | No | **Yes** |
| Stata-native syntax | Yes | No | **Yes** |
| Offline / air-gapped use | Yes | No | **Yes** |
| CI charts matching `ci means` | No | No | **Yes** |
| Summary statistics panel | No | No | **Yes** |
| Box and violin plots | Limited | Yes | **Yes** |
| Zero setup for viewers | Yes | No | **Yes** |

---

## Quick start

```stata
ssc install sparkta
sysuse auto, clear

* Bar chart grouped by repair record
sparkta price, type(bar) over(rep78)

* CI bar with 95% confidence intervals
sparkta price, type(cibar) over(rep78) cilevel(95)

* Violin plot -- animated KDE + IQR box
sparkta price, type(violin) over(rep78)

* Interactive filter dropdown
sparkta price weight, type(bar) over(rep78) filter(foreign)

* Save to file instead of opening browser
sparkta price, type(cibar) over(rep78) export("~/Desktop/chart.html")

* Fully offline -- no CDN, no internet needed when opened
sparkta price, type(bar) over(foreign) offline export("~/Desktop/offline.html")
```

---

## Chart types

| Type | Description |
|---|---|
| `bar` | Vertical grouped bar chart |
| `hbar` | Horizontal bar chart |
| `stackedbar` | Stacked vertical bars |
| `stackedbar100` | 100% stacked (composition view) |
| `stackedhbar100` | 100% stacked horizontal |
| `line` | Line chart |
| `area` | Filled area chart |
| `stackedarea` | Stacked filled area chart |
| `scatter` | Scatter plot (`y x` variable order, Stata convention) |
| `bubble` | Bubble chart (`y x size`) |
| `pie` | Pie chart |
| `donut` | Donut chart |
| `cibar` | Mean bars with t-distribution CI whiskers |
| `ciline` | Mean line with shaded CI band |
| `histogram` | Histogram -- density, frequency, or fraction |
| `boxplot` | Box-and-whisker with Tukey fences |
| `hbox` | Horizontal box-and-whisker |
| `violin` | Violin plot (KDE + IQR box + whiskers, animated) |
| `hviolin` | Horizontal violin plot |

---

## Key options

```stata
* --- Grouping ---
sparkta price, over(rep78)                        // one series per group
sparkta price, by(foreign)                        // separate panel per group
sparkta price, filter(foreign)                    // interactive dropdown filter
sparkta price, filter(foreign) filter2(rep78)     // two independent filters

* --- Statistics ---
sparkta price, stat(median)                       // median instead of mean
sparkta price, type(cibar) cilevel(90)            // 90% CI whiskers
sparkta price, type(histogram) histtype(frequency)

* --- Box and violin ---
sparkta price, type(boxplot) whiskerfence(3)      // 3x IQR fence (fewer outliers)
sparkta price, type(violin) bandwidth(2000)       // custom KDE bandwidth
sparkta price, type(boxplot) mediancolor(#e74c3c) // custom marker color

* --- Axes ---
sparkta price, ytitle("USD") yrange(0 15000)
sparkta price, xlabels(Poor|Fair|Average|Good|Excellent)

* --- Secondary y-axis (dual scale) ---
sparkta price mpg, type(line) over(foreign) y2(mpg) y2title("MPG")

* --- Typography and tooltip styling (v2.6.0) ---
sparkta price weight, type(bar) over(foreign)     ///
    titlesize(28) titlecolor(#2c3e50)             ///
    xtitlesize(13) xlabsize(11) xlabcolor(#888)   ///
    tooltipbg(rgba(0,0,0,0.9)) tooltipborder(#4e79a7)

* --- Export and offline ---
sparkta price, export("~/Downloads/chart.html")
sparkta price, offline export("~/Downloads/chart_offline.html")
```

---

## Styling options (v2.6.0)

Version 2.6.0 adds fine-grained control over typography and tooltips. All
color options accept standard CSS values: named colors, hex, RGB, or RGBA.

| Option group | Options |
|---|---|
| Title | `titlesize()` `titlecolor()` |
| Subtitle | `subtitlesize()` `subtitlecolor()` |
| Axis titles | `xtitlesize()` `xtitlecolor()` `ytitlesize()` `ytitlecolor()` |
| Axis tick labels | `xlabsize()` `xlabcolor()` `ylabsize()` `ylabcolor()` |
| Legend | `legcolor()` `legbgcolor()` |
| Tooltip | `tooltipbg()` `tooltipborder()` `tooltipfontsize()` `tooltippadding()` |

```stata
* Dark themed chart with fully custom typography
sparkta price weight, type(bar) over(foreign) theme(dark) ///
    titlesize(26) titlecolor(#ecf0f1)             ///
    xtitlecolor(#bdc3c7) ytitlecolor(#bdc3c7)     ///
    xlabcolor(#95a5a6)  ylabcolor(#95a5a6)        ///
    legcolor(#ecf0f1) legbgcolor(rgba(0,0,0,0.4)) ///
    tooltipbg(rgba(20,20,20,0.95)) tooltipborder(#3498db) ///
    tooltipfontsize(13) tooltippadding(12)
```

---

## Summary statistics panel

Every chart includes a collapsible statistics panel showing N, Mean, Median,
Min, Max, SD, and a distribution sparkline with IQR box and outlier dots.
Statistics match `summarize, detail` output exactly.
Use `nostats` to suppress the panel.

---

## Installation

### From SSC (recommended)
```stata
ssc install sparkta
```

### From GitHub
```stata
net install sparkta, from("https://raw.githubusercontent.com/fahad-mirza/sparkta/main/ado/")
```

Copy `dist/sparkta.jar` to your Stata personal directory (`c(sysdir_personal)`).

### Verify installation
```stata
sparkta_check
```

---

## Build from source (developers only)

The pre-compiled `dist/sparkta.jar` is ready to use. To build from source:

**Step 1 -- Download JS libraries** (run once before first build, and when adding new chart type plugins):
```
cd java/
fetch_js_libs.bat      (Windows)
fetch_js_libs.sh       (Mac/Linux)
```

**Step 2 -- Compile:**
```
build.bat              (Windows)
build.sh               (Mac/Linux)
```

> **Order matters.** JS libraries must be downloaded before compilation
> so they are bundled inside the jar for offline use. Skipping Step 1
> will cause `offline` charts to fail silently at render time.

**Requirements:** Java 8+ JDK, Stata 17+

---

## Repository structure

```
ado/
  sparkta.ado            Stata command
  sparkta.sthlp          Help file  (help sparkta)
  sparkta_check.ado      Installation verifier
dist/
  sparkta.jar            Pre-compiled jar with all JS libs bundled
examples/
  basic_charts.do        Bar, line, scatter, area, pie, donut
  stat_charts.do         CI bar, CI line, histogram
  boxviolin.do           Boxplot, violin, dual y-axis, filter edge cases
  styling.do             v2.6.0 typography and tooltip styling
  edge_cases.do          Missing data, single obs groups, extreme values
  offline_mode.do        Offline / air-gapped workflow
java/
  src/                   Java source
  build.bat / build.sh
  fetch_js_libs.bat / fetch_js_libs.sh
docs/
  INSTALL.md
  CHANGELOG.md
```

---

## Offline and air-gapped use

The `offline` option embeds all JavaScript inside the HTML file at generation time:

- Opens in any browser with no internet connection
- Makes zero external network requests when opened
- Recommended for clinical, financial, and institutional data
- Permanent reproducible snapshot -- renders identically years later

File size: ~250-320 KB vs ~50 KB for CDN-linked files. No other differences.

---

## Requirements

- Stata 17 or later (uses the Java Plugin Interface, JPI)
- Java 8+ (bundled with most Stata installations)
- `sparkta.jar` in `c(sysdir_personal)` or current working directory

---

## Authors

**Fahad Mirza** -- Author and Developer
[LinkedIn](https://www.linkedin.com/in/fahad-mirza/) | [GitHub](https://github.com/fahad-mirza/)

**Claude** (Anthropic) -- Co-developer
Algorithm implementation, Java rendering engine, debugging, and feature development.

---

*Chart rendering: Chart.js 4.4 | Java bridge: Stata JPI | Statistics match `summarize, detail` exactly*
