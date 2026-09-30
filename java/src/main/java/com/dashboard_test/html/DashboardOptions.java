package com.dashboard_test.html;

/**
 * DashboardOptions -- all user-specified options passed from Stata to HtmlGenerator.
 *
 * v2.6.0: Restructured into grouped inner classes for maintainability.
 *   Flat fields: identity, grouping/data, transient (computed, not from user).
 *   Inner classes: StyleOptions, AxisOptions, ChartOptions, StatOptions.
 *
 * MIGRATION NOTE: all call sites updated from o.fieldName to o.group.fieldName
 * for grouped fields. Flat fields (varlist, type, theme, over, by, etc.) are
 * unchanged. The compiler catches every missed rename -- no silent breakage.
 *
 * Arg index map (v3.5.37 -- complete):
 *   0-93:    core options (varlist, type, title, axes, behaviour, stats)
 *   94-96:   mediancolor, meancolor, bandwidth (box/violin)
 *   97-110:  Phase 1-A font/color styling     -> o.style.*
 *   111-114: Phase 1-B tooltip styling         -> o.style.*
 *   115:     Phase 2-A download button         -> o.chart.download
 *   116-120: Phase 1-C axis utilities          -> o.axes.* / o.chart.*
 *   121-124: Phase 1-D line/point style        -> o.chart.*
 *   125-126: Phase 1-E notesize, gradient      -> o.style.*
 *   127-136: Phase 2-B annotation lines/bands  -> o.axes.*
 *   137-139: Phase 2-C legend/tick overrides   -> o.chart.* / o.axes.*
 *   140-149: Phase 2-B annotation pts/labels/ellipses -> o.axes.*
 *   150:     relabel() over-group rename       -> o.chart.relabel
 *   Next free: 151
 * hasAnnotations(): single source of truth for annotation-lib dependency check.
 *   Called by DashboardBuilder (offline preflight) and HtmlGenerator (CDN/script tags).
 */
public class DashboardOptions {

    // -------------------------------------------------------------------------
    // FLAT: Identity -- used everywhere, grouping adds no value
    // -------------------------------------------------------------------------
    public String varlist   = "";
    public String type      = "bar";
    public String title     = "Dashboard";
    public String subtitle  = "";
    public String theme     = "default";
    public String export    = "";
    public String stataPwd  = "";  // arg 189: Stata c(pwd) for relative export path resolution
    public String note      = "";
    public String caption   = "";

    // -------------------------------------------------------------------------
    // FLAT: Grouping / data -- shared across many renderers
    // -------------------------------------------------------------------------
    public String  over               = "";
    public String  by                 = "";
    public String  tousename          = "";
    public boolean nomissing          = false;
    public boolean showmissingOver    = false;
    public boolean showmissingBy      = false;
    public boolean showmissingFilter  = false;
    public boolean showmissingFilter2 = false;

    // -------------------------------------------------------------------------
    // FLAT: Transient -- computed during processing, not from user args
    // -------------------------------------------------------------------------
    // _histPreamble: set by histogram() to pass _ttRanges_ and _ttCounts_ JS var
    // declarations back to build() for emission before the chart script. (v2.6.1)
    public String _histPreamble = "";
    // _byHistPreambles: one preamble per by()-panel histogram, in panel order.
    // Populated by HtmlGenerator.buildByScripts() after each panel histogram()
    // call so each panel's tooltip can reference its own _ttRanges_/_ttCounts_. (v2.6.1)
    /** s9g: true while by() panels are being built: renderers omit per-panel keys, axes use the shared range. */
    public boolean _panelMode = false;
    /** t2j fix8 (deep-dive r3): global bubble size range (min + span) over the FULL dataset,
     *  set by buildByScripts() before the panel loop so every by() panel scales bubble radii
     *  on the same basis the JS filter recompute uses (buildBubblePoints, full-data scale).
     *  Without it the server scaled radii per panel, so bubbles jumped size the first time a
     *  filter was touched. NaN = not set (non-by bubble uses its own full-data range). */
    public double _bubbleRminGlobal  = Double.NaN;
    public double _bubbleRspanGlobal = Double.NaN;
    /** t2j fix8q (C8 for by() panels): full-data {min,max} of the x / y variables of a
     *  scatter or bubble, set by buildByScripts() before the panel loop so every panel locks
     *  its continuous axes to one shared frame (a panel subset only knows its own extent).
     *  null = not in panel mode. */
    public double[] _panelXExtent = null;
    public double[] _panelYExtent = null;
    /** s9j: elements the renderer drew that are NOT Chart.js datasets -- {role,label,kind,color,dash,pointStyle}.
     *  Renderers register as they emit; HtmlGenerator renders one "elements key" from the list. */
    public java.util.List<String[]> _keyItems = new java.util.ArrayList<>();
    public boolean _sharedAxis = false;
    public java.util.List<String> _byHistPreambles = new java.util.ArrayList<>();
    // _boxYMin/_boxYMax: computed by DatasetBuilder.boxplotDatasets() to hold
    // the overall data range INCLUDING outlier values. The @sgratzl plugin does
    // NOT report outlier values to Chart.js scale auto-range so without these
    // explicit bounds outliers are drawn outside the plot area. (v2.4.4)
    public double _boxYMin = Double.MAX_VALUE;
    public double _boxYMax = Double.MIN_VALUE;
    // leglabels() renames legend items by intra-panel dataset index (v3.4.1).
    // No transient fields needed -- legendLabelsCfg() uses o.chart.legendlabels directly.
    // _leglabelsOnXAxis: true when leglabels() was applied to x-axis variable labels
    // (aggLabels or single-var overLabels mode). legendLabelsCfg() skips generateLabels. (v3.4.1)
    public boolean _leglabelsOnXAxis = false;
    // _leglabelsColorByCategory: true when leglabels() is active AND chart is in
    // colorByCategory mode (bar + over() + single var). In this mode leglabels() renames
    // BOTH the x-axis group labels AND the per-bar colored legend items via generateLabels.
    // Without this flag, _leglabelsOnXAxis=true would suppress generateLabels. (v3.5.36)
    public boolean _leglabelsColorByCategory = false;
    // _globalOverGroups: the full over-group display label list from the COMPLETE dataset,
    // set by HtmlGenerator.buildByScripts() before panel subsetting so overDatasets() can
    // look up each local group's global color index. Null when not in by() mode. (v3.4.1)
    public java.util.List<String> _globalOverGroups = null;

    // -------------------------------------------------------------------------
    // GROUPED: inner class instances
    // -------------------------------------------------------------------------
    public final StyleOptions style = new StyleOptions();
    public final AxisOptions  axes  = new AxisOptions();
    public final ChartOptions chart = new ChartOptions();
    public final StatOptions  stats = new StatOptions();
    // v3.6.0-t2h (batch 2a): publication-table options (arg 209, parsed by
    // sparkta_table_opts.ado). Drives the estout/esttab-style table and its
    // LaTeX / Markdown / tidy-CSV exports. See PubTable.java.
    public final TableOptions table = new TableOptions();

    // =========================================================================
    // StyleOptions -- font sizes, colors, tooltip styling (Phase 1-A+B)
    // =========================================================================
    public static class StyleOptions {
        // -- Title / subtitle text (Phase 1-A, args 97-100) --
        public String titleSize      = "";   // arg 97
        public String titleColor     = "";   // arg 98
        public String subtitleSize   = "";   // arg 99
        public String subtitleColor  = "";   // arg 100

        // -- Axis titles (Phase 1-A, args 101-104) --
        public String xtitleSize     = "";   // arg 101
        public String xtitleColor    = "";   // arg 102
        public String ytitleSize     = "";   // arg 103
        public String ytitleColor    = "";   // arg 104

        // -- Axis tick labels (Phase 1-A, args 105-108) --
        public String xlabSize       = "";   // arg 105
        public String xlabColor      = "";   // arg 106
        public String ylabSize       = "";   // arg 107
        public String ylabColor      = "";   // arg 108

        // -- Legend (Phase 1-A, args 109-110) --
        public String legColor       = "";   // arg 109: legend text color
        public String legBgColor     = "";   // arg 110: legend background color

        // -- Tooltip (Phase 1-B, args 111-114) --
        public String tooltipBg      = "";   // arg 111: tooltip background color
        public String tooltipBorder  = "";   // arg 112: tooltip border color
        public String tooltipFontSize = "";  // arg 113: tooltip font size (pt)
        public String tooltipPadding = "";   // arg 114: tooltip padding (px)
        public boolean noTimestamp   = false; // arg 200: suppress timestamp subtitle (v3.6.0-s7d)
        public boolean collapseStats = false; // arg 201: stats panel starts collapsed (v3.6.0-s7d)
        public boolean noAllFilter   = false; // arg 202: suppress All in filter dropdowns (v3.6.0-s7d)

        // -- Note/caption size (Phase 1-E, arg 125) --
        // Applied to both .note and .caption CSS classes. Accepts any CSS font-size
        // value (e.g. "1rem", "14px", "0.9em"). Empty = use theme defaults.
        public String noteSize       = "";   // arg 125: note/caption font size

        // -- Gradient fill (Phase 1-E, arg 126) --
        // When true, area and bar charts use a vertical canvas gradient for backgroundColor
        // instead of a flat color. Gradient: full-opacity color at top -> transparent at bottom
        // for area charts; full-opacity -> 60% opacity at bottom for bar charts.
        // Skipped for: line (no fill), scatter, pie/donut, histogram, boxplot/violin.
        public String  gradient      = "";    // arg 126: "" = off, "1" = auto palette, "color1|color2" = custom
    }

    // =========================================================================
    // AxisOptions -- all axis configuration
    // =========================================================================
    public static class AxisOptions {
        // -- Axis titles --
        public String  xtitle        = "";
        public String  ytitle        = "";

        // -- Custom tick labels --
        public String  xlabels       = "";   // custom x-axis tick labels
        public String  ylabels       = "";   // custom y-axis tick labels

        // -- Filter variables --
        // F-0b: pipe-separated filter varlist, e.g. "rep78|foreign|make"
        // Populated by DashboardBuilder from arg 76. Split into DataSet.filterVars
        // by StataDataReader.read(). Old filter1/filter2 kept for back-compat.
        public String  filterList    = "";   // pipe-sep, from arg 76 (F-0b)
        public String  filter1       = "";   // back-compat (derived from filterList)
        public String  filter2       = "";   // back-compat (derived from filterList)

        // -- Axis ranges --
        public String  xrangeMin     = "";
        public String  xrangeMax     = "";
        public String  yrangeMin     = "";
        public String  yrangeMax     = "";
        public boolean yStartZero    = false;

        // -- Axis scale types --
        public String  xtype         = "";   // linear | logarithmic | category | time
        public String  ytype         = "";   // linear | logarithmic

        // -- Tick control --
        public String  xtickcount    = "";
        public String  ytickcount    = "";
        public String  xtickangle    = "";
        public String  ytickangle    = "";
        public String  xstepsize     = "";
        public String  ystepsize     = "";

        // -- Grid / border display --
        public boolean xgridlines    = true;
        public boolean ygridlines    = true;
        public boolean xborder       = true;
        public boolean yborder       = true;

        // -- Secondary y-axis (v2.3.0) --
        public String  y2vars        = "";
        public String  y2title       = "";
        public String  y2rangeMin    = "";
        public String  y2rangeMax    = "";

        // -- Phase 1-C axis utilities (args 116-119, v3.0.3) --
        public boolean yreverse      = false; // arg 116 reverse y-axis direction
        public boolean xreverse      = false; // arg 117 reverse x-axis direction
        public boolean noticks       = false; // arg 118 hide tick marks (keep labels)
        public String  ygrace        = "";    // arg 119 y-axis grace padding e.g. "5%" or "10"

        // -- Phase 2-C tick value overrides (args 138-139, v3.4.0) --
        // Pipe-separated numeric tick values for the axis.
        // e.g. xticks(0|25|50|75|100) pins exactly those 5 ticks on the x-axis.
        // Implemented as afterBuildTicks callback in buildAxisConfig().
        // Only meaningful on numeric/linear axes; ignored on category axes.
        public String  xticks        = "";    // arg 138: custom x tick values
        public String  yticks        = "";    // arg 139: custom y tick values

        // -- Phase 2-B: reference annotations (args 127-136, 140-148, v3.5.0) --
        // Lines and bands:
        public String  yline         = "";   // arg 127: pipe-sep y reference line values
        public String  xline         = "";   // arg 128: pipe-sep x reference line values
        public String  ylinecolor    = "";   // arg 129: pipe-sep colors per yline
        public String  xlinecolor    = "";   // arg 130: pipe-sep colors per xline
        public String  ylinelabel    = "";   // arg 131: pipe-sep labels per yline
        public String  xlinelabel    = "";   // arg 132: pipe-sep labels per xline
        public String  yband         = "";   // arg 133: pipe-sep "lo hi" pairs for horizontal bands
        public String  xband         = "";   // arg 134: pipe-sep "lo hi" pairs for vertical bands
        public String  ybandcolor    = "";   // arg 135: pipe-sep colors per yband
        public String  xbandcolor    = "";   // arg 136: pipe-sep colors per xband
        // Points:
        public String  apoint        = "";   // arg 140: space-sep y x pairs (scattteri convention)
        public String  apointcolor   = "";   // arg 141: pipe-sep colors per point
        public String  apointsize    = "";   // arg 142: radius px for all points
        // Labels:
        public String  alabelpos     = "";   // arg 143: pipe-sep "y x pos" coordinate pairs; pos=minute clock
        public String  alabeltext    = "";   // arg 144: pipe-sep label texts
        public String  alabelfontsize = "";  // arg 145: font size px for all labels
        public String  alabelgap     = "";   // arg 149: label offset distance px (v3.5.2)
        // Ellipses:
        public String  aellipse      = "";   // arg 146: pipe-sep "ymin xmin ymax xmax" quads
        public String  aellipsecolor = "";   // arg 147: pipe-sep fill colors per ellipse
        public String  aellipseborder = "";  // arg 148: pipe-sep border colors per ellipse
    }

    // =========================================================================
    // ChartOptions -- chart behaviour, appearance, animation, legend
    // =========================================================================
    public static class ChartOptions {
        // -- Layout --
        public String  layout        = "vertical";
        public boolean layoutExplicit = false;   // fix9f: true when the user wrote layout(); the default vertical stack exports two across
        public boolean horizontal    = false;
        public boolean stack         = false;
        public boolean fill          = false;
        public boolean stack100      = false;  // 100% stacked bar (v2.2.0)
        public boolean offline       = false;  // embed JS inline (v2.0.2)

        // -- Line / area behaviour --
        public String  smooth        = "0.3";
        public boolean spanmissing   = false;
        public String  stepped       = "";     // before | after | middle

        // -- Data behaviour --
        public String  sortgroups    = "";     // "" | "asc" | "desc"
        public boolean novaluelabels = false;
        public boolean nostats       = false;

        // -- Point / line styling --
        public String  pointsize          = "4";
        public String  pointstyle         = "";
        public String  pointborderwidth   = "1";
        public String  pointrotation      = "0";
        public String  linewidth          = "2";

        // -- Bar appearance --
        public String  barwidth      = "";     // barPercentage 0-1
        public String  bargroupwidth = "";     // categoryPercentage 0-1
        public String  borderradius  = "";     // px
        public String  opacity       = "";     // 0-1

        // -- Layout & padding --
        public String  aspect        = "";
        public String  padding       = "";
        public String  plotMargin    = "";    // arg 205: "l r b t" percent of data range (v3.6.0-s8p)
        public boolean yfree         = false; // arg 206: by() panels keep their own value axis (default: shared) (v3.6.0-s9g)
        public String  peXpos        = "";    // arg 207: eventstudy relative times, tilde-grouped per model, pipe-sep ("." = not a period) (v3.6.0-t2d)
        public String  esOpts        = "";    // arg 208: eventstudy reference-period options "ref=auto|none|<#>;together=0|1" (v3.6.0-t2f)

        // -- Animation --
        public String  animate       = "";    // coarse: none|fast|slow
        public String  easing        = "";
        public String  animdelay     = "";
        public String  animduration  = "";    // arg 120 exact ms; overrides animate() (v3.0.3)

        // -- Tooltip --
        // Defaults: nearest/nearest so tooltip snaps to specific bar/point.
        // DashboardBuilder always overwrites from args; these are safety fallbacks.
        public String  tooltipfmt    = "";
        public String  tooltipmode   = "nearest";
        public String  tooltippos    = "nearest";

        // -- Legend --
        public String  legend        = "top";
        public String  legendtitle   = "";
        public String  legendsize    = "";
        public String  legendboxheight = "";

        // -- Colors & background --
        public String  colors        = "";
        public String  bgcolor       = "";
        public String  plotcolor     = "";
        public String  gridcolor     = "";
        public String  gridopacity   = "0.15";

        // -- Datalabels --
        public boolean datalabels    = false;
        public boolean pielabels     = false;

        // -- Area fill opacity (v2.0.1) --
        public String  areaopacity   = "0.35";  // effective default; DashboardBuilder always wires from arg 87 (v3.5.37 aligned)

        // -- Phase 1-D: line/point style (args 121-124, v3.1.0) --
        // lpattern: single dash pattern applied to ALL line/area datasets.
        //   Accepted values: solid | dash | dot | dashdot
        //   Translated to Chart.js borderDash arrays in DatasetBuilder.lpatternToBorderDash().
        public String  lpattern      = "";    // arg 121: line dash pattern (all series)

        // lpatterns: pipe-separated per-series dash patterns.
        //   e.g. "solid|dash|dot" -- cycles if fewer entries than datasets.
        //   Takes precedence over lpattern() when both are specified.
        public String  lpatterns     = "";    // arg 122: per-series dash patterns

        // nopoints: when true, sets pointRadius:0 and pointHoverRadius:0 on all
        //   line/area datasets, suppressing point markers entirely.
        //   Takes precedence over pointsize() and pointhoversize() when set.
        public boolean nopoints      = false; // arg 123: suppress point markers

        // pointhoversize: hover-only point radius override (px).
        //   Overrides the default (pointsize + 2) heuristic.
        //   Ignored when nopoints is true.
        public String  pointhoversize = "";   // arg 124: point radius on hover

        // -- Phase 2-C: legend label overrides (arg 137, v3.4.0) --
        // Pipe-separated list of replacement legend entry names, in dataset order.
        // e.g. "Domestic|Foreign" renames the first two legend entries.
        // Implemented as a generateLabels callback in legendLabelsCfg().
        // Skipped for pie/donut (labels come from data keys, not dataset labels).
        public String  legendlabels  = "";    // arg 137: pipe-sep legend label overrides
        public String  relabel       = "";    // arg 150: pipe-sep relabel -- renames x-axis groups AND legend (Stata relabel() equivalent)
        public String  sliders       = "";    // arg 151: pipe-sep slider varlist for dual-handle numeric range filters (F-1)
        public String  mlabelVar     = "";    // arg 152: scatter marker label variable name
        public boolean mlabelAll     = false; // arg 153: show all labels regardless of N (from mlabel(var, all))
        public String  mlabpos       = "";    // arg 154: minute-clock 0-59 uniform position for all labels
        public String  mlabvpos      = "";    // arg 155: per-obs minute-clock position variable name
        public String  fit           = "";    // arg 156: fit line type: lfit|qfit|lowess|exp|log|power|ma
        public boolean fitci         = false; // arg 157: add CI band to lfit/qfit fit line
        // v3.5.69: Stata-computed fit data. Non-empty when ado pre-computed the fit.
        // Format: "x1,y1|x2,y2|..." sorted by x. Java parses and embeds directly.
        // When empty, ChartRenderer falls back to FitComputer (Java approximation).
        public String  fitLineData   = "";    // arg 158: pre-computed fit line points
        public String  fitCiUpper    = "";    // arg 159: pre-computed CI upper bound
        public String  fitCiLower    = "";    // arg 160: pre-computed CI lower bound

        // -- Phase 2-A: PNG download button (arg 115, v2.7.0) --
        public boolean download      = false; // arg 115: show PNG download button (SVG removed v2.9.2)

        // -- Post-estimation charts (v3.6.0, args 161-179) --
        public String  peNames      = "";    // arg 161: pipe-sep coefficient display names
        public String  peCoefs      = "";    // arg 162: pipe-sep coefficient values
        public String  peLower      = "";    // arg 163: pipe-sep CI lower bounds
        public String  peUpper      = "";    // arg 164: pipe-sep CI upper bounds
        public String  peSes        = "";    // arg 165: pipe-sep standard errors
        public String  pePvals      = "";    // arg 166: pipe-sep p-values
        public String  peEstNames   = "";    // arg 167: tilde-sep model names (multi-model)
        public String  peHeadings   = "";    // arg 168: pipe-sep heading positions and texts
        public String  peNobs       = "";    // arg 169: sample size(s)
        public String  peDepvar     = "";    // arg 170: dependent variable name
        public String  peOrient     = "";    // arg 171: h=horizontal, v=vertical
        public String  peCoefStyle  = "";    // arg 172: scatter or bar
        public String  peCiStyle    = "";    // arg 173: whisker | band | bar
        public String  pePStyles    = "";    // arg 174: space-sep per-model point styles
        public String  mpData       = "";    // arg 175: marginsplot margins data
        public String  mpUpper      = "";    // arg 176: marginsplot CI upper
        public String  mpLower      = "";    // arg 177: marginsplot CI lower
        public String  mpXlab       = "";    // arg 178: marginsplot x-axis label
        public String  mpYlab       = "";    // arg 179: marginsplot y-axis label
        public String  mpSeries     = "";    // arg 203: marginsplot pipe-sep series labels (tilde=sep models)
        public String  mpXpos       = "";    // arg 204: marginsplot numeric x positions from r(at)
        public String  peBases      = "";    // arg 180: pipe-sep base/omitted variable display names
        public String  peTstat      = "";    // arg 181: "1"=t-distribution, "0"=z-distribution
        public String  peCmd        = "";    // arg 182: estimation command name (regress, logit, etc.)
        public boolean peNoci       = false; // arg 183: suppress confidence intervals (v3.6.0)
        public String  peTzvals     = "";    // arg 184: pipe-sep pre-computed t/z statistics (v3.6.0)
        // -- Session 6 visual control (v3.6.0-s6, args 185-188) --
        public String  peRefval     = "0";   // arg 185: reference line value or "none" (ado sets 1 for eform)
        public String  refLineWidth = "";    // arg 77: reference-line width px (t2j fix8 A13; empty -> default 1)
        public String  peEstlabels  = "";    // arg 186: tilde-sep custom legend labels (overrides estnames display)
        public String  peCicolors   = "";    // arg 187: pipe-sep per-model CI colors (default: marker at 60% opacity)
        public String  peCiwidth    = "";    // arg 188: CI line/band-border width px (default 1.5 whisker, 0.5 band)
        // -- Session 6 connected option (v3.6.0-s6) --
        public boolean peConnected  = false; // arg 190: draw connecting line through point estimates
        public String  peModelStats = "";    // arg 191: tilde-sep pipe-sep fit stats "r2=0.42|F=18.4~r2=0.58" (v3.6.0-s6c)
        public String  peIndicators = "";    // arg 192: tilde-sep indicator rows "label|v1|v2|..." (v3.6.0-s6c)
        public String  peAddstats   = "";    // arg 193: tilde-sep addstats rows "label|v1|v2|..." (v3.6.0-s6c)
        public String  peVarLabels  = "";    // arg 194: auto variable labels "varname|||label~~~..." (v3.6.0)
        public String  peCustomLabels = "";  // arg 195: custom labels from coeflabels() "varname|||label~~~..." (v3.6.0)
        public String  pePexline     = "";    // arg 196: vertical reference line x-value for coefplot/eventstudy (v3.6.0-s7a)
        public String  peLevels2Lo   = "";    // arg 197: levels() inner CI lower bounds pipe-sep (v3.6.0-s7b)
        public String  peLevels2Hi   = "";    // arg 198: levels() inner CI upper bounds pipe-sep (v3.6.0-s7b)
        public String  peLevels2Val  = "";    // arg 199: levels() inner CI level value e.g. "90" (v3.6.0-s7b)
    }

    // =========================================================================
    // StatOptions -- statistical computation parameters
    // =========================================================================
    public static class StatOptions {
        // -- Aggregation --
        public String  stat          = "mean"; // mean | sum | count | median | min | max | pct -- effective default; "pct" only active for pie/donut (v3.5.37 aligned)

        // -- Pie / Donut --
        public String  cutout        = "";
        public String  rotation      = "";
        public String  circumference = "";
        public String  sliceborder   = "";
        public String  hoveroffset   = "";

        // -- CI charts (v1.7.0) --
        public String  cilevel       = "95";    // confidence level: 90 | 95 | 99
        public String  cibandopacity = "0.18";  // CI band fill opacity for ciline

        // -- Histogram (v1.8.0) --
        public String  bins          = "";        // "" = Sturges rule auto
        public String  histtype      = "density"; // density | frequency | fraction

        // -- Box / violin (v2.4.0) --
        public String  whiskerfence  = "1.5";   // Tukey IQR multiplier
        public String  mediancolor   = "";      // arg 94: "" = auto white/black
        public String  meancolor     = "";      // arg 95: "" = auto white/black

        // -- Violin KDE (v2.5.0) --
        public String  bandwidth     = "";      // arg 96: "" = Silverman rule auto
    }

    // =========================================================================
    // TableOptions -- publication-table controls (v3.6.0-t2h, batch 2a).
    // Parsed in DashboardBuilder from arg 209 (packed by sparkta_table_opts.ado
    // as "stars=0.10 0.05 0.01|nostars=0|tstat=0|ci=0|nofooter=0|notable=0").
    // Consumed by PubTable.java only. The chart is unaffected by any of these.
    // =========================================================================
    public static class TableOptions {
        // Significance thresholds, HIGH -> LOW, mapped to * ** *** (estout default
        // is 0.10 0.05 0.01, which also matches sparkta's existing star legend).
        public String[] stars   = {"0.10", "0.05", "0.01"};
        public boolean  noStars = false;   // nostars: suppress the star column entirely
        public boolean  tstat   = false;   // tstat: show [t]/[z] beneath the estimate
        public boolean  ci      = false;   // ci: show [lo, hi] beneath the estimate
        public boolean  noFooter= false;   // nofooter: drop the N / R2 / legend footer block
        public boolean  noTable = false;   // notable: suppress the whole table block
    }

    // =========================================================================
    // POST-ESTIMATION TYPE CHECK (v3.6.0)
    // Returns true for coefplot, eventstudy, marginsplot.
    // These types skip data reading and use pre-computed pipe-sep args instead.
    // =========================================================================
    public boolean isPostEstimation() {
        return type.equals("coefplot") || type.equals("eventstudy")
            || type.equals("marginsplot");
    }

    // =========================================================================
    // ANNOTATION DEPENDENCY CHECK (v3.5.37)
    // Single source of truth: called by DashboardBuilder (offline preflight)
    // and HtmlGenerator (CDN script tags + needsAN flag).
    // Add any new annotation-trigger field here when new annotation types are added.
    // =========================================================================
    public boolean hasAnnotations() {
        return !axes.yline.isEmpty() || !axes.xline.isEmpty()
            || !axes.yband.isEmpty() || !axes.xband.isEmpty()
            || !axes.apoint.isEmpty() || !axes.alabelpos.isEmpty()
            || !axes.aellipse.isEmpty()
            || !chart.pePexline.isEmpty();  // arg 196: pexline for coefplot/eventstudy (v3.6.0-s7a)
    }
}
