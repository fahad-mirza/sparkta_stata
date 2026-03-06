# sparkta

**Interactive self-contained HTML charts and dashboards from Stata -- one command, zero dependencies.**

```stata
sysuse auto, clear
sparkta price, type(cibar) over(rep78) title("Mean Price with 95% CI")
```

Opens a fully interactive chart in your browser. Share the `.html` file with anyone -- no R, no Python, no server, no internet connection required by the viewer.

---

## Why sparkta?

| Feature | `graph` | Plotly/Shiny | **sparkta** |
|---|---|---|---|
| Interactive tooltips | No | Yes | **Yes** |
| Filter dropdowns | No | Yes (server) | **Yes (self-contained)** |
| Self-contained file | No | No | **Yes** |
| Stata syntax | Yes | No | **Yes** |
| Offline / air-gapped | Yes | No | **Yes** |
| CI charts matching `ci means` | No | No | **Yes** |
| Summary stats panel | No | No | **Yes** |
| Zero setup for viewers | Yes | No | **Yes** |

---

## Quick start

```stata
ssc install sparkta
sysuse auto, clear

* Bar chart
sparkta price, type(bar) over(rep78)

* CI bar with 95% confidence intervals
sparkta price, type(cibar) over(rep78) cilevel(95)

* Violin plot
sparkta price, type(violin) over(rep78)

* Interactive filter dropdown
sparkta price weight, type(bar) over(rep78) filter(foreign)

* Save to file
sparkta price, type(cibar) over(rep78) export("~/Desktop/chart.html")

* Fully offline -- no CDN, no internet required when opened
sparkta price, type(bar) over(foreign) offline export("~/Desktop/chart_offline.html")
```

---

## Chart types

| Type | Description |
|---|---|
| `bar` | Vertical grouped bar chart |
| `hbar` | Horizontal bar chart |
| `stackedbar` | Stacked vertical bars |
| `stackedbar100` | 100% stacked (composition view) |
| `line` | Line chart |
| `area` | Filled area chart |
| `stackedarea` | Stacked filled area chart |
| `scatter` | Scatter plot (`y x` variable order) |
| `bubble` | Bubble chart (`y x size`) |
| `pie` | Pie chart |
| `donut` | Donut chart |
| `cibar` | Mean bars with t-distribution CI whiskers |
| `ciline` | Mean line with shaded CI band |
| `histogram` | Histogram (density / frequency / fraction) |
| `boxplot` | Box-and-whisker with Tukey fences |
| `hbox` | Horizontal box-and-whisker |
| `violin` | Violin plot (KDE + IQR box + whiskers, animated) |
| `hviolin` | Horizontal violin plot |

---

## Key options

```stata
* Grouping and panels
sparkta price, over(rep78)              // one series per group on same chart
sparkta price, by(foreign)             // separate panel per group
sparkta price, filter(foreign)         // interactive dropdown filter
sparkta price, filter(foreign) filter2(rep78)  // two independent filters

* Statistics
sparkta price, stat(median)             // median instead of mean
sparkta price, type(cibar) cilevel(90)  // 90% CI whiskers

* Appearance
sparkta price, theme(dark)              // dark background
sparkta price, colors("#e74c3c #3498db") // custom colors
sparkta price, datalabels               // show values on bars

* Box and violin
sparkta price, type(boxplot) whiskerfence(3)       // 3x IQR fence
sparkta price, type(violin) bandwidth(2000)        // custom KDE bandwidth
sparkta price, type(boxplot) mediancolor(#e74c3c)  // custom marker color

* Axes
sparkta price, ytitle("USD") yrange(0 15000)
sparkta price, xlabels(Poor|Fair|Average|Good|Excellent)

* Secondary y-axis
sparkta price mpg, type(line) over(foreign) y2(mpg) y2title("MPG")

* Export and offline
sparkta price, export("~/Downloads/chart.html")
sparkta price, offline export("~/Downloads/chart_offline.html")
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

The pre-compiled `dist/sparkta.jar` is ready to use.
To build from source:

**Step 1 -- Download JS libraries** (run once before first build, and whenever
a new chart type plugin is added):
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

> **Order matters.** JS libraries must be downloaded before jar compilation
> so they are bundled inside for offline use.

Requirements: Java 8+ (JDK), Stata 17+

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
  basic_charts.do        Bar, line, scatter, area, pie
  stat_charts.do         CI bar, CI line, histogram
  boxviolin.do           Boxplot, violin, dual y-axis
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

The `offline` option embeds all JavaScript inside the HTML file:

- Opens in any browser with no internet connection
- Makes zero external network requests when opened
- Recommended for clinical, financial, and institutional data
- Permanent reproducible snapshot -- renders identically years later

File size: ~250-320 KB vs ~50 KB for CDN-linked files.

---

## Requirements

- Stata 17 or later (uses the Java Plugin Interface, JPI)
- Java 8+ (bundled with most Stata installations)
- `sparkta.jar` in `c(sysdir_personal)` or working directory

---

## Authors

**Fahad Mirza** -- Author and Developer

**Claude** (Anthropic) -- Co-developer
Algorithm implementation, Java rendering engine, debugging, and feature development.

---

*Chart rendering: Chart.js 4.4 | Java bridge: Stata JPI | Statistics match Stata summarize,detail exactly*
