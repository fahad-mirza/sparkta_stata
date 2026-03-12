*! sparkta version 3.5.41
*! v3.5.41: Reverted c(source) (not a real Stata constant -- was a hallucination).
*!           findfile is the correct and well-documented primary lookup. Added missing
*!           sysdir_personal forward-slash variant (Mac/Linux) and sysdir_PLUS/s/sparkta/
*!           explicit fallback. HtmlGenerator.VERSION bumped to 3.5.41 for consistency.
*!           Java recompile required (VERSION constant change).
*! v3.5.40: Removed hardcoded C:\ado\personal path. Added sysdir_PLUS subfolder and
*!           Mac/Linux separator variants. Ado-only change.
*! v3.5.39: robust jar search. findfile(sparkta.ado) now primary lookup -- finds jar
*!           wherever Stata installed the ado (SSC PLUS/s/sparkta/, net install personal/,
*!           or any adopath location). Added sysdir_PLUS/s/sparkta/ fallback and Mac/Linux
*!           forward-slash sysdir_personal variants. Removed hardcoded C:\ado\personal path.
*!           sparkta_check.ado updated to match. Ado-only, no Java recompile needed.
*! v3.5.36 Fix: leglabels() generateLabels callback now emitted in colorByCategory mode
*!         (bar + over() + single var). Root cause: _leglabelsOnXAxis=true was
*!         suppressing generateLabels. New _leglabelsColorByCategory flag allows it
*!         through when needed. Java recompile required.
*!          Cross-chart behavior table, worked examples, version history entries.
*!          No code changes. No recompile needed.
*! v3.5.34 Fix relabel() legend in colorByCategory mode: now shows N colored entries
*!          (one per bar) instead of one. Add nolegend flag (alias for legend(none)).
*! v3.5.28: fix ALL option abbreviation conflicts across all 142 options.
*!          FILTEr/FILTER2, LPATTErn/LPATTERns, ANIMDElay/ANIMDUration,
*!          TOOLTIPBOrder. Zero conflicts confirmed by audit. Ado-only.
*! v3.5.11: fix tooltip option abbreviation conflicts (tooltipformat vs tooltipfontsize,
*!          tooltipposition vs tooltippadding). Raise min abbrevs: TOOLTIPFORmat,
*!          TOOLTIPFONtsize, TOOLTIPPOSition, TOOLTIPPADding. Ado-only fix.
*! v3.5.10: auto axis titles from variable label/name (bar/line/area/boxplot/violin).
*! v3.5.8: O(N*G)->O(N) group index (Variable.groupIndex()), string over()/by() missing fix.
*! v3.5.4: stackedhbar added (stacked horizontal bar, non-100%%).
*! v3.5.3: alabelpos() minute-clock direction (3rd token per entry); alabelgap() offset px (arg 149).
*! v3.5.0: Phase 2-B: reference annotations (args 127-136, 140-148).
*!   19 new options: yline, xline, ylinecolor, xlinecolor, ylinelabel, xlinelabel,
*!   yband, xband, ybandcolor, xbandcolor (lines/bands, args 127-136);
*!   apoint, apointcolor, apointsize (points, args 140-142);
*!   alabelpos, alabeltext, alabelfontsize (labels, args 143-145);
*!   aellipse, aellipsecolor, aellipseborder (ellipses, args 146-148).
*!   CDN lib 5: chartjs-plugin-annotation@3.0.1 (auto-registers).
*!   buildAnnotationConfig(allowX) single chokepoint in ChartRenderer.
*!   Offline: annotation lib added to pre-flight only when hasAnnotations()=true.
*! v3.4.1: leglabels() fix -- auto-suppress legend on single-dataset by() panels.
*!   _singleDatasetByPanel transient flag set in buildByScripts(); read by
*!   buildLegendConfig() and legendLabelsCfg() in ChartRenderer.
*!   leglabels() now correctly skipped when panel has no over() (nothing to rename).
*! v3.4.0: Phase 2-C: leglabels(), xticks(), yticks() (args 137-139).
*!   leglabels(): pipe-sep legend entry renames via generateLabels callback.
*!   xticks()/yticks(): pipe-sep numeric tick values via afterBuildTicks callback.
*!   No conflict with noticks: afterBuildTicks runs at scale-build, beforeDraw later.
*!   Args 127-136 reserved as placeholders for Phase 2-B (reference lines/bands).
*! v3.3.1: Compound themes: dark_<palette> and light_<palette> now accepted.
*!   isDark() helper in HtmlGenerator propagated to all 4 renderers (18 sites).
*!   ado theme validation expanded to all bare + compound combinations.
*! v3.3.0: Phase 2-D: 7 named color palettes via theme() option.
*!   tab1 (Tableau10), tab2 (Set1), tab3 (Dark2), cblind1 (Okabe-Ito),
*!   neon, swift_red, viridis. namedScheme() in HtmlGenerator.java.
*!   dark/light theme keywords preserved; colors() still overrides palette.
*! v3.2.4: gradcolors() per-series support -- colon-delimited pairs.
*! v3.2.3: gradient flash fix -- init _grad vars to transparent not null.
*! v3.2.2: gradient fix -- null init + re-entry guard for onComplete.
*! v3.2.1: gradient fill fix -- onComplete pattern for correct canvas dimensions.
*! v3.2.0: Phase 1-E: notesize(), gradient (args 125-126).
*!   notesize(): applies user CSS font-size value to both .note and .caption classes.
*!   gradient: vertical canvas gradient fill for area and bar charts.
*!     Area: full-opacity color at top -> transparent at bottom.
*!     Bar: full-opacity at top -> 60% opacity at bottom (subtle depth).
*!     buildGradientPreamble() in HtmlGenerator emits JS preamble creating
*!     window._grad{i} gradient objects; DatasetBuilder references them as
*!     backgroundColor values. Gradient skipped for line-only, scatter,
*!     pie/donut, histogram, boxplot/violin.
*!   args.length check bumped 125->127. All files updated.
*! v3.1.0: Phase 1-D: lpattern(), lpatterns(), nopoints, pointhoversize() (args 121-124).
*!   lpatternToBorderDash() helper in DatasetBuilder is the single source of truth
*!   for pattern->borderDash translation; resolvedBorderDash() handles per-series
*!   resolution (lpatterns takes precedence over lpattern, cycles if fewer entries).
*!   linePointRadius() and linePointHoverRadius() helpers centralise nopoints and
*!   pointhoversize logic; all three line/area emission sites (buildDatasetStyle,
*!   numDatasets, ciLineDatasets) delegate to these helpers.
*!   ciline: borderDash and nopoints applied to mean line only; CI band boundary
*!   lines keep their intentional pointRadius:0 and fixed borderDash:[4,3].
*!   args.length check bumped 121->125. All five files updated.
*! v2.7.5: SVG blank-output fix: pre-scale c2sCtx by devicePixelRatio; chart-goes-blank
*!   fix: call chartInst.update("none") after ctx restore. v2.7.4 stubs retained.
*!   canvas2svg 1.0.16 is missing methods Chart.js 4.4 calls during draw().
*!   Missing: resetTransform, roundRect, getTransform, ellipse,
*!   createConicGradient, isPointInPath, isPointInStroke.
*!   Fix: after new C2S(), iterate _c2sMethods and bind each stub only if
*!   the method is absent (typeof !== function). Existing real implementations
*!   in future canvas2svg versions are preserved. v2.7.4
*! v2.7.3: SVG draw() fix: chart.attached patch.
*!   Chart.js 4 draw() checks this.attached (getter: canvas.isConnected).
*!   canvas2svg SVG element not in DOM -> isConnected=false -> draw() returns
*!   immediately -> empty SVG string. Fix: defineProperty on chartInst to shadow
*!   prototype getter with true; delete in finally to restore. Also patches
*!   c2sCtx.canvas.ownerDocument = document for secondary checks. v2.7.3
*! v2.7.2: SVG canvas dimension patch + toolbar placement fix.
*!   (1) Object.defineProperty patches c2sCtx.canvas.width/height to plain
*!       integers. SVG element returns SVGAnimatedLength objects -- Chart.js
*!       reads these as numbers, gets object -> NaN -> empty SVG. v2.7.2
*!   (2) try/catch/finally added: errors surfaced via console.error + alert.
*!       Empty SVG guard (length<50) prevents silent blank downloads. v2.7.2
*!   (3) Download buttons moved from absolute overlay to flow-positioned
*!       toolbar row above canvas. No more overlap with chart content. v2.7.2
*! v2.7.1: Three fixes from download testing:
*!   (1) SVG fix: removed chartInst.canvas=ctx.canvas (canvas2svg has no
*!       .canvas property -- set it to undefined, draw() failed silently).
*!       Now swaps only chartInst.ctx; try/finally guarantees restore.
*!       Uses offsetWidth/Height for CSS-pixel SVG viewport. v2.7.1
*!   (2) over()+by() unblocked: replaced blanket guard with narrow check
*!       that only blocks pie/donut (where the combo is meaningless).
*!       All other types (bar, line, scatter, cibar, boxplot etc) now
*!       support over() within by() panels. Java already handled this.
*!   (3) Test 3 (bar+over+by) now works as a consequence of fix (2).
*! v2.7.0: Phase 2-A: download option -- PNG + SVG export buttons.
*! v2.6.2: by()+filter() now works: all panels update on dropdown change.
*! v2.6.1: Three rendering fixes (see changelog).
*! v2.6.0: Architecture refactor (DashboardOptions inner classes) + Phase 1-A+B styling.
*!          StyleOptions, AxisOptions, ChartOptions, StatOptions inner classes.
*!          18 new styling opts: titlesize/color, subtitlesize/color, xtitlesize/color,
*!          ytitlesize/color, xlabsize/color, ylabsize/color, legcolor, legbgcolor,
*!          tooltipbg, tooltipborder, tooltipfontsize, tooltippadding (args 97-114).
*!          Shared builder methods: tooltipStylePrefix(), axisTitleCfg(),
*!          axisTickStyleCfg(), legendLabelsCfg() in ChartRenderer.
*! v2.4.10: Revert boxplot+over() to single dataset (per-element bg[] array, fixes alignment).
*!           mediancolor()/meancolor() options. Auto white/black from avg palette luminance.
*! v2.4.9: F1 global median/mean color (avg palette luminance), F2 meanRadius 5, F3 null-pad no-over() boxplot
*! v2.4.8: Fix E1: violin median/mean colors computed per-dataset from each
*!          violin's own fill -- medianColorFor(col(dsIdx)) per violin dataset.
*!          Fix E2: boxplot+over() single-var now one-dataset-per-group so each
*!          group box gets medianColorFor(col(gi)) -- correct contrast always.
*!          Fix E3: boxplot no-over() multi-var now one-dataset-per-variable
*!          so each variable box gets medianColorFor(col(vi)) correctly.
*!          Root cause: @sgratzl plugin applies medianColor as dataset-level
*!          scalar only; per-element arrays do not exist for this property.
*! v2.4.8: Fix A: outlier hollow circle (transparent fill, colored border).
*!          Fix B: suppress Chart.js default legend for box/violin.
*!          Fix C: hbox and hviolin type support + horizontal rendering.
*!          Fix D: boxplot/violin _initChart wrapping for filter interaction.
*! v2.4.6: Adaptive median/mean colors (luminance rule), violin 3-item legend, whisker swatch #cccccc
*! v2.4.5: violin/boxplot: median=#ffffff (white), mean=#f59e0b (amber);
*!         inline canvas legend plugin (bpInlineLegend) in chart area
*! Stata sparkta -- interactive visualization builder using Java Plugin Interface
*! v1.6.0: Replace obslist macro with touse variable name to remove ~13K obs limit
*! v1.6.1: Fix aggregate() median to use Stata-compatible percentile formula
*! v1.7.0: Add cibar and ciline chart types with cilevel() option
*! v1.7.2: Add cibandopacity() option
*! v1.7.3: Fix filter destroy+reinit; _initChart() approach replaces JSON.parse
*! v1.8.0: Add histogram chart type with bins() and histtype() options
*! v1.8.1: Fix double-comma JS syntax error in buildHistXScale
*! v1.8.2: Fix histogram filter -- move _ttRanges_ preamble out of histogram() so var _mainChart= inserts correctly
*! v2.0.0: Modular Java refactor -- ChartRenderer, DatasetBuilder, FilterRenderer, StatsRenderer
*! v2.0.1: areaopacity() option; area charts auto-set beginAtZero; sort area series by fill size
*! v2.0.2: offline option -- embed Chart.js inline for fully self-contained air-gapped HTML
*! v2.0.3: offline graceful CDN fallback when libs missing; online/offline mode in status msg
*! v2.0.4: strict offline (no CDN fallback ever); pre-flight check fails fast with fix instructions;
*!          build.bat bundles resources into jar; fetch_js_libs.bat adds pause and size verification
*! v2.0.5: fetch_js_libs.bat adds --ssl-no-revoke for institutional/corporate networks
*! v2.0.6: export() path fix -- expand ~ on Windows, normalise separators,
*!          show resolved path before writing, clear error if write fails
*! v2.4.4: Five boxplot/violin visual fixes:
*!  (1) Color by group: single var + over() now colors each box by group
*!      using per-element backgroundColor/borderColor arrays.
*!  (2) Color by variable: no-over() now colors each box by variable
*!      using per-element color arrays via buildBoxColorArrays().
*!  (3) Violin data format: violin type now passes raw number arrays
*!      via buildViolinPoint() instead of pre-computed stat objects.
*!      Plugin computes its own KDE from raw values.
*!  (4) Outliers clipped: added minStats/maxStats/grace to y-axis scale
*!      config (buildBoxYAxisConfig) so axis bounds include all outlier
*!      dots. grace:5pct prevents edge clipping.
*!  (5) Median/mean contrast: medianColor/meanColor now white so lines
*!      are visible against the colored box fill. borderWidth raised to 2.
*! v2.4.4: Box plot visual fixes (requires recompile).
*!          1. Colors per over() group: single-var boxplot/violin now uses
*!             per-element backgroundColor[]/borderColor[] arrays so each
*!             group box gets its own palette color.
*!          2. Median/mean line visibility: medianColor and meanBackground
*!             Color now #ffffff (white) so lines are visible against the
*!             colored fill (was same color as fill = invisible).
*!          3. Outlier clipping: y-axis now uses minStats/maxStats/grace
*!             so scale auto-extends to include all outlier dots with 5pct
*!             padding at edges.
*!          4. Violin tooltip: ctx.raw is a raw number array for violin;
*!             tooltip now branches on Array.isArray(ctx.raw) and computes
*!             five-number summary in JS for violin; boxplot path unchanged.
*!          5. Multi-var no-over y-scale: does not force beginAtZero so
*!             price/weight/mpg each fill the available scale space.
*! v2.4.4: Four boxplot/violin rendering fixes (compile fix: o.dark->
*!          o.theme.equals("dark") -- DashboardOptions has no .dark field):
*!  (1) COLORS: boxplot+over() single-var now emits per-element color arrays
*!      (one palette color per group box). Multi-var: one color per dataset.
*!  (2) MEDIAN/MEAN LINES: medianColor/meanBackgroundColor set to white (#ffffff)
*!      so lines are visible against the colored box fill.
*!  (3) VIOLIN: violin+over() now emits one dataset per group (null-padded
*!      for other positions). Violin type does not support per-element color
*!      arrays. Each group violin gets its own palette color. Tooltip mode
*!      changed to nearest to avoid showing null-padded groups.
*!  (4) OUTLIER CLIPPING: minStats/maxStats moved from scale config to
*!      dataset level (options.boxplot.datasets.*). Scale-level placement
*!      has no effect -- dataset-level is the correct API per plugin docs.
*!      Recompile required.
*! v2.4.2: fetch_js_libs.bat fix. The @ symbol in the scoped npm URL
*!          (@sgratzl/chartjs-chart-boxplot) caused Windows batch to
*!          misinterpret @ as a command modifier, closing the window.
*!          Fix: store URL in variable BP_URL before echo and curl.
*!          Also added set CURL_RC immediately after curl for block 4.
*! v2.4.1: Fix boxplot blank chart. chartjs-chart-boxplot UMD does NOT
*!          auto-register. Must call Chart.register(BoxPlotController,
*!          ViolinController, BoxAndWiskers, Violin) before new Chart().
*!          UMD global is "ChartBoxPlot". Also fixed CDN URL: correct
*!          scoped package is @sgratzl/chartjs-chart-boxplot@4.4.5
*!          (not the unscoped chartjs-chart-boxplot@4.4.0 which 404s).
*! v2.4.0: Box plot and violin chart types. type(boxplot) and type(violin).
*!          Whiskers: Tukey k*IQR fences (default k=1.5), configurable via
*!          whiskerfence(). Outliers shown as dots. Five-number summary in
*!          tooltip. Works with or without over(). Requires
*!          chartjs-chart-boxplot@4.4.0 CDN plugin. Args length 94.
*! v2.3.0: Secondary y-axis. New options: y2(), y2title(), y2range().
*!          Variables listed in y2() are plotted on the right axis.
*!          Works for bar and line charts with over(). Args 90-92 added.
*! v2.2.1: cibar color fix. Single-variable cibar colors each bar by
*!          its group (matching type(bar)). Multi-variable keeps one color
*!          per series. errorBar whiskers match bar color.
*! v2.2.0: 100% stacked bar/hbar. type(stackedbar100) and type(stackedhbar100).
*!          Each column normalised to 100%%; values shown as percentage share.
*!          Tooltip: variable name, Share %%, raw stat value, column total.
*!          Useful for composition comparisons across groups.
*! v2.1.2: Scatter/bubble axes swapped to match Stata convention (first variable = y-axis).
*!          scatter label now shows "y vs x". Compile errors fixed (illegal escape
*!          sequences in Java regex and JS regex strings).
*! v2.1.1: Tooltip redesign (revised). Clean consistent layout:
*!          title=group, variable name, stat label, CI [lo-hi], N each on own line.
*!          Scatter/bubble: y variable shown before x (Stata convention).
*!          CI bar/line: N now shown correctly from embedded data point (was "--").
*!          No stat label duplication. tooltipformat() respected everywhere.
*! v2.1.0: Tooltip redesign -- multi-line structured tooltips for all chart types.
*!          Bar/line: title=group, stat label (Mean/Median/Sum etc), N shown.
*!          CI bar/line: Mean + "95% CI [lo - hi]" + N each on own line.
*!          Scatter/bubble: axis-labelled x/y values. Hist: bin range in title.
*!          Pie/donut: slice % with raw sum. tooltipformat() respected everywhere.
*! v3.5.38: sthlp fix -- xticks() Options detail block was missing its opening phang/opt line;
*!           block started mid-sentence. Sthlp-only change, no recompile needed.
*! v2.0.9: sparkta_check moved to sparkta_check.ado; "Dashboard ready" -> "Sparkta ready"
*! v2.0.8: renamed from dashboard to sparkta across all files
*! v2.0.7: fetch_js_libs.bat rewritten -- all 3 files attempt regardless of
*!          individual failures, no goto, per-file result, URL shown, pause always

program define sparkta
    version 17

    // Print version so user can confirm which ado is loaded
    display as text "  [sparkta v3.5.41 | `c(sysdir_personal)']"

    syntax varlist [if] [in],           ///
        [TYPE(string)]                  ///  bar line scatter pie hbar stackedbar stackedarea area bubble donut cibar ciline stackedbar100 stackedhbar100
        [TITLE(string)]                 ///  main chart title
        [SUBTitle(string)]              ///  subtitle below title
        [NOTE(string)]                  ///  note below chart
        [CAPTION(string)]               ///  caption below note
        [EXPORT(string)]                ///  export path for HTML file
        [THEME(string)]                 ///  default | dark
        /// - Axes -
        [XTITLE(string)]                ///  x-axis label
        [YTITLE(string)]                ///  y-axis label
        [XRANGE(string)]                ///  x-axis bounds: two numbers e.g. xrange(0 100)
        [YRANGE(string)]                ///  y-axis bounds: two numbers e.g. yrange(0 50)
        [YSTARt(string)]                ///  ystart(zero) forces y-axis to start at 0
        [XTYpe(string)]                 ///  x-axis scale: linear (default) | log | category | time
        [YTYpe(string)]                 ///  y-axis scale: linear (default) | log
        [XTICKCount(string)]            ///  number of x-axis ticks
        [YTICKCount(string)]            ///  number of y-axis ticks
        [XTICKangle(string)]            ///  x-axis label rotation degrees e.g. xtickangle(45)
        [YTICKangle(string)]            ///  y-axis label rotation degrees
        [XLABels(string)]               ///  custom x-axis tick labels, pipe-separated: xlabels(Low|Med|High)
        [YLABels(string)]               ///  custom y-axis tick labels, pipe-separated: ylabels(Bad|OK|Good)
        [XSTEPsize(string)]             ///  x-axis tick interval e.g. xstepsize(10)
        [YSTEPsize(string)]             ///  y-axis tick interval
        [XGRIDlines(string)]            ///  on (default) | off
        [YGRIDlines(string)]            ///  on (default) | off
        [XBORder(string)]               ///  on (default) | off - axis border line
        [YBORder(string)]               ///  on (default) | off
        /// - Grouping / layout -
        [OVER(string)]                  ///  group variable (same chart) [, showmissing]
        [BY(string)]                    ///  panel variable (separate charts) [, showmissing]
        [FILTEr(string)]                ///  interactive filter dropdown 1 [, showmissing]
        [FILTER2(string)]               ///  interactive filter dropdown 2 [, showmissing]
        [LAYOUT(string)]                ///  vertical | horizontal | grid
        /// - Chart behaviour -
        [HORizontal]                    ///  horizontal bar chart
        [STACKed]                         ///  stacked bars/areas
        [FILL]                          ///  fill area under line
        [SMOOTH(string)]                ///  line tension 0-1 (default 0.3)
        [SPANmissing]                   ///  connect line across missing data
        [STEPped(string)]               ///  step line: before | after | middle
        /// - Point / line appearance -
        [POINTSIze(string)]             ///  point radius px (default 4)
        [POINTStyle(string)]            ///  circle cross dash line rect rectRounded star triangle
        [POINTBorderwidth(string)]      ///  point border width px (default 1)
        [POINTRotation(string)]         ///  point rotation degrees (default 0)
        [LINEWidth(string)]             ///  line/border width px (default 2)
        /// - Bar appearance -
        [BARWidth(string)]              ///  bar thickness 0-1 fraction (default 0.8)
        [BARGroupwidth(string)]         ///  gap between bar groups 0-1 (default 0.8)
        [BORDERRadius(string)]          ///  rounded bar corners px (default 0)
        [OPacity(string)]               ///  fill opacity 0-1 (default from colors)
        /// - Layout & padding -
        [ASPect(string)]                ///  aspect ratio width/height
        [PADding(string)]               ///  chart inner padding px (single value or "t r b l")
        /// - Animation -
        [ANIMAte(string)]               ///  none | fast | slow
        [EASing(string)]                ///  linear easeIn easeOut easeInOut bounce elastic etc.
        [ANIMDElay(string)]             ///  animation delay ms
        /// - Tooltip -
        [TOOLTIPFORmat(string)]         ///  number format string
        [TOOLTIPMode(string)]           ///  index (default) | point | nearest | x | y
        [TOOLTIPPOSition(string)]       ///  average (default) | nearest
        /// - Legend -
        [LEGEND(string)]                ///  top (default) | bottom | left | right | none
        [LEGTitle(string)]           ///  text heading above legend entries
        [LEGSize(string)]            ///  legend label font size px
        [LEGBoxheight(string)]       ///  legend color box height px
        /// - Colors & styling -
        [COLORS(string)]                ///  space-separated series colors
        [BGCOLOR(string)]               ///  page background color
        [PLOTColor(string)]             ///  chart card background color
        [GRIDColor(string)]             ///  grid line color
        [GRIDOpacity(string)]           ///  grid opacity 0-1
        [DATAlabels]                    ///  show values on chart elements
        /// - Pie / Donut specific -
        [STAT(string)]                  ///  mean (default)|sum|count|median|min|max  (pie: pct|sum)
        [CUTout(string)]                ///  donut hole % e.g. cutout(65)
        [ROTation(string)]              ///  start angle degrees e.g. rotation(-90)
        [CIRCumference(string)]         ///  arc degrees e.g. circumference(180)
        [SLICEBorder(string)]           ///  gap between slices px
        [HOVEROffset(string)]           ///  slice pop-out on hover px
        [PIElabels]                     ///  show value/pct labels on slices
        /// - Data handling -
        [NOMISsing]                     ///  exclude missing values
        [SORTgroups(string)]                ///  sort over()/by() group labels: asc (default) | desc
        [NOVAluelabels]                 ///  show raw numeric codes not value labels
        [NOSTATs]                       ///  suppress summary statistics panel
        [CILevel(string)]               ///  CI level for cibar/ciline: 90|95|99 (default 95)
        [CIBandopacity(string)]         ///  ciline fill band opacity 0-1 (default 0.18)
        [AREAopacity(string)]           ///  area fill opacity 0-1 (default 0.75) (v2.0.1)
        [OFFLINE]                       ///  embed JS inline -- fully offline HTML (v2.0.2)
        /// - Secondary y-axis (v2.3.0) -
        [Y2(string)]                    ///  variables plotted on right y-axis (space-separated)
        [Y2Title(string)]               ///  right y-axis label
        [Y2Range(string)]               ///  right y-axis range: "min max" e.g. y2range(0 100)
        /// - Histogram -
        [BINS(string)]                  ///  number of histogram bins (default: auto Sturges rule)
        [HISTType(string)]              ///  density (default) | frequency | fraction
        /// - Box plot / Violin (v2.4.0) -
        [WHISKERFence(string)]          ///  whisker fence multiplier k: Q1-k*IQR / Q3+k*IQR (default 1.5)
        [MEDIANColor(string)]           ///  median line color (default: auto white/black) (v2.4.10)
        [MEANColor(string)]             ///  mean dot color (default: same as mediancolor) (v2.4.10)
        [BANDWidth(string)]             ///  KDE bandwidth for violin (default: Silverman auto) (v2.5.0)
        /// - Phase 1-A: font / color styling (v2.6.0) -
        [TITLESize(string)]             ///  title font size in pt (e.g. titlesize(24))
        [TITLEColor(string)]            ///  title color (hex, rgb, or CSS name)
        [SUBTITLESize(string)]          ///  subtitle font size in pt
        [SUBTITLEColor(string)]         ///  subtitle color
        [XTITLESize(string)]            ///  x-axis title font size in pt
        [XTITLEColor(string)]           ///  x-axis title color
        [YTITLESize(string)]            ///  y-axis title font size in pt
        [YTITLEColor(string)]           ///  y-axis title color
        [XLABSize(string)]              ///  x tick label font size in pt
        [XLABColor(string)]             ///  x tick label color
        [YLABSize(string)]              ///  y tick label font size in pt
        [YLABColor(string)]             ///  y tick label color
        [LEGColor(string)]              ///  legend text color
        [LEGBGColor(string)]            ///  legend background color
        /// - Phase 1-B: tooltip styling (v2.6.0) -
        [TOOLTIPBg(string)]             ///  tooltip background color (e.g. rgba(0,0,0,0.9))
        [TOOLTIPBOrder(string)]         ///  tooltip border color
        [TOOLTIPFONtsize(string)]       ///  tooltip font size in pt
        [TOOLTIPPADding(string)]        ///  tooltip padding in px
        /// - Phase 2-A: export buttons (v2.7.0) -
        [DOWNload]                          ///  show PNG download button on chart
        /// - Phase 1-C: axis utilities (v3.0.3) -
        [YREVerse]                          ///  reverse y-axis direction (top to bottom)
        [XREVerse]                          ///  reverse x-axis direction (right to left)
        [NOTicks]                           ///  hide axis tick marks (labels remain visible)
        [YGRace(string)]                    ///  y-axis grace padding above/below data: "5%%" or "10"
        [ANIMDUration(string)]              ///  animation duration in ms e.g. animduration(800)
        /// - Phase 1-D: line/point style (v3.1.0) -
        [LPATTErn(string)]                  ///  line dash pattern for all series: solid | dash | dot | dashdot
        [LPATTERns(string)]                 ///  per-series dash patterns pipe-separated: e.g. solid|dash|dot
        [NOPoints]                          ///  suppress point markers on line/area charts
        [NOLEGend]                          ///  suppress legend (alias for legend(none)) (v3.5.34)
        [POINTHoversize(string)]            ///  point radius on hover px (default: pointsize + 2)
        /// - Phase 1-E: note size + gradient (v3.2.0) -
        [NOTESize(string)]                  ///  font size for note/caption text e.g. notesize(1rem) or notesize(14px)
        [GRADient]                          ///  gradient fill (auto palette colors)
        [GRADColors(string)]                ///  gradient colors: "s|e" all series, or "s0|e0:s1|e1:..." per-series
        /// - Phase 2-C: legend/tick overrides (v3.4.0) -
        [LEGLabels(string)]                 ///  rename legend entries: leglabels(Label1|Label2|...) pipe-separated, in dataset order
        [RELabel(string)]                   ///  rename over() group labels on x-axis AND legend: relabel(A|B|C) -- Stata over(var,relabel()) equivalent (v3.5.34)
        [XTicks(string)]                    ///  custom x-axis tick values: xticks(0|25|50|75|100) pipe-separated numeric
        [YTicks(string)]                    ///  custom y-axis tick values: yticks(0|10|20|30) pipe-separated numeric
        /// - Phase 2-B: reference annotations (v3.5.0) -
        [YLine(string)]                     ///  y reference lines: yline(50) or yline(50|75) pipe-sep values
        [XLine(string)]                     ///  x reference lines: xline(3) pipe-sep values (ignored on categorical x)
        [YLINEColor(string)]                ///  colors per yline, pipe-sep: ylinecolor(red|blue)
        [XLINEColor(string)]                ///  colors per xline, pipe-sep: xlinecolor(navy)
        [YLINELabel(string)]                ///  labels per yline, pipe-sep: ylinelabel(Average|Max)
        [XLINELabel(string)]                ///  labels per xline, pipe-sep: xlinelabel(Cutoff)
        [YBand(string)]                     ///  horizontal bands: yband(20 40) or yband(20 40|60 80) "lo hi" per entry
        [XBand(string)]                     ///  vertical bands: xband(1 3) "lo hi" per entry (ignored on categorical x)
        [YBANDColor(string)]                ///  fill colors per yband, pipe-sep: ybandcolor(rgba(0,200,0,0.1))
        [XBANDColor(string)]                ///  fill colors per xband, pipe-sep: xbandcolor(rgba(0,0,200,0.1))
        [APoint(string)]                    ///  annotation points: apoint(y1 x1 y2 x2 ...) space-sep y x pairs
        [APOINTColor(string)]               ///  colors per apoint, pipe-sep: apointcolor(red|blue)
        [APOINTSize(string)]                ///  radius px for all apoints: apointsize(10)
        [ALABELPos(string)]                 ///  label positions: alabelpos(y1 x1 pos|y2 x2 pos) pipe-sep; pos=minute clock (0=center,15=right,30=below,45=left)
        [ALABELText(string)]                ///  label texts: alabeltext(Cluster A|Cluster B) pipe-sep
        [ALABELFontSize(string)]            ///  font size px for annotation labels: alabelfontsize(12)
        [ALABELGap(string)]                 ///  label offset distance px from point: alabelgap(15)
        [AEllipse(string)]                  ///  ellipses: aellipse(ymin xmin ymax xmax|...) 4 values per ellipse
        [AELLIPSEColor(string)]             ///  fill colors per ellipse, pipe-sep: aellipsecolor(rgba(0,0,200,0.1))
        [AELLIPSEBorder(string)]            //   border colors per ellipse, pipe-sep: aellipseborder(navy)

    local _rlbl `"`relabel'"'  // v3.5.34: copy to safe local (avoids Stata built-in name conflict)
    // - Version check -
    if `c(stata_version)' < 17 {
        display as error "sparkta requires Stata 17 or later"
        exit 198
    }

    // - Type -
    if "`type'" == "" local type "bar"
    local type = lower("`type'")
    // Aliases
    if "`type'" == "horizontalbar" local type "hbar"
    // v2.4.7: horizontal boxplot and violin aliases
    if "`type'" == "hbox"    local type "hboxplot"
    if "`type'" == "hviolin" local type "hviolinplot"
    // v3.5.3: stacked horizontal (non-100%) alias -- passes through to Java
    // Java aliases stackedhbar -> bar + stack=true + horizontal=true
    local valid_types "bar line scatter pie hbar stackedbar stackedhbar stackedarea area bubble donut stackedline cibar ciline histogram stackedbar100 stackedhbar100 boxplot violin hboxplot hviolinplot"
    if !`:list type in valid_types' {
        display as error "Invalid type: `type'"
        display as error "Valid: bar line scatter pie hbar stackedbar stackedhbar area bubble"
        display as error "       donut stackedarea stackedline stackedbar100 stackedhbar100"
        display as error "       cibar ciline histogram boxplot hbox violin hviolin"
        exit 198
    }

    // - over/by: parse varname and optional showmissing suboption (v1.9.1) -
    // Syntax allows over(varname [, showmissing]) so we received a string.
    // Parse each with local syntax to extract varname and flag separately.
    local sm_over    "0"
    local sm_by      "0"
    local sm_filter  "0"
    local sm_filter2 "0"
    // v1.9.1: parse over/by/filter/filter2 strings for optional , showmissing
    // Uses tokenize so we never need compound quotes inside function calls.
    foreach slot in over by filter filter2 {
        local _raw : copy local `slot'
        if "`_raw'" != "" {
            // Strip optional comma so "rep78, showmissing" and "rep78 showmissing" both work
            local _raw2 = subinstr("`_raw'", ",", " ", 1)
            // tokenize splits on spaces; token 1 = varname, token 2 (if any) = suboption
            tokenize `_raw2'
            local _varpart "`1'"
            local _sub     = lower("`2'")
            if "`_sub'" == "showmissing" | "`_sub'" == "sm" {
                local sm_`slot' "1"
            }
            else if "`_sub'" != "" {
                display as error "`slot'() suboption not recognised: `2'"
                display as error "  Valid suboption: showmissing"
                exit 198
            }
            // Confirm varname exists
            capture confirm variable `_varpart'
            if _rc != 0 {
                display as error "`slot'() variable not found: `_varpart'"
                exit 198
            }
            local `slot' "`_varpart'"  // replace string with clean varname
        }
    }
    // over()+by() are allowed together for all chart types except pie/donut.
    // For pie/donut, over() defines slices so by() panels would just repeat
    // the same slices -- no useful meaning. All other types: over() groups
    // within each by() panel (e.g. grouped bars per panel). v2.7.1
    if "`over'" != "" & "`by'" != "" {
        if "`type'" == "pie" | "`type'" == "donut" {
            display as error "pie and donut do not support over() with by() together"
            display as error "  over() defines slices; by() would repeat identical panels"
            exit 198
        }
    }

    // - cibar / ciline: require over() -
    if ("`type'" == "cibar" | "`type'" == "ciline") & "`over'" == "" {
        display as error "cibar and ciline require over() to define comparison groups"
        display as error "  e.g. sparkta price, type(cibar) over(rep78) nomissing"
        exit 198
    }

    // - cilevel() validation (v1.7.0) -
    // Default 95%. Accepts any integer 1-99.
    // Only meaningful for cibar/ciline but silently accepted for other types.
    if "`cilevel'" == "" local cilevel "95"
    local cilevel_num = real("`cilevel'")
    if missing(`cilevel_num') | `cilevel_num' <= 0 | `cilevel_num' >= 100 {
        display as error "cilevel() must be a number between 1 and 99 (e.g. 90, 95, 99)"
        exit 198
    }
    local cilevel = string(round(`cilevel_num', 1))

    // - cibandopacity() validation (v1.7.2) -
    // Controls ciline fill band opacity. Default 0.18.
    // Accepts any value 0-1. Ignored for non-ciline charts.
    if "`cibandopacity'" == "" local cibandopacity "0.18"
    local cbop_num = real("`cibandopacity'")
    if missing(`cbop_num') | `cbop_num' < 0 | `cbop_num' > 1 {
        display as error "cibandopacity() must be a number between 0 and 1 (e.g. 0.1, 0.2, 0.3)"
        exit 198
    }

    // - areaopacity() validation (v2.0.1) -
    // Controls area fill opacity for non-stacked area charts. Default 0.75.
    if "`areaopacity'" == "" local areaopacity "0.75"
    local flop_num = real("`areaopacity'")
    if missing(`flop_num') | `flop_num' < 0 | `flop_num' > 1 {
        display as error "areaopacity() must be a number between 0 and 1 (e.g. 0.1, 0.5, 1.0)"
        exit 198
    }

    // - offline flag (v2.0.2) -
    local is_offline "0"
    if "`offline'" != "" local is_offline "1"
    local is_download "0"
    if "`download'" != "" local is_download "1"
    // - Phase 1-C axis utilities (v3.0.3) -
    local is_yreverse "0"
    if "`yreverse'" != "" local is_yreverse "1"
    local is_xreverse "0"
    if "`xreverse'" != "" local is_xreverse "1"
    local is_noticks "0"
    if "`noticks'" != "" local is_noticks "1"
    // ygrace: accept "5%%" or plain number; pass through as-is
    if "`ygrace'" == "" local ygrace ""
    // animduration: must be a positive integer
    if "`animduration'" != "" {
        capture confirm integer number `animduration'
        if _rc {
            display as error "sparkta: animduration() must be a positive integer (milliseconds)"
            exit 198
        }
        if `animduration' < 0 {
            display as error "sparkta: animduration() must be >= 0"
            exit 198
        }
    }
    // - Phase 1-D: line/point style (v3.1.0) -
    // lpattern: validate accepted values (only for line/area types)
    local valid_lpatterns "solid dash dot dashdot"
    if "`lpattern'" != "" {
        if !`:list lpattern in valid_lpatterns' {
            display as error "sparkta: lpattern() must be solid, dash, dot, or dashdot"
            exit 198
        }
    }
    // lpatterns: validate each pipe-separated token
    if "`lpatterns'" != "" {
        local _lp_check = subinstr("`lpatterns'", "|", " ", .)
        foreach _tok of local _lp_check {
            if !`:list _tok in valid_lpatterns' {
                display as error "sparkta: lpatterns() token '`_tok'' must be solid, dash, dot, or dashdot"
                exit 198
            }
        }
    }
    // nopoints flag
    local is_nopoints "0"
    if "`nopoints'" != "" local is_nopoints "1"
    // pointhoversize: must be a positive number
    // Use real() -- confirm number can fail on string-typed options (v3.5.2)
    if "`pointhoversize'" != "" {
        local _phsz = real("`pointhoversize'")
        if missing(`_phsz') | `_phsz' < 1 {
            display as error "sparkta: pointhoversize() must be a positive number >= 1 (pixels)"
            exit 198
        }
    }

    // - Phase 1-E: notesize validation (v3.2.0) -
    // notesize accepts any CSS font-size value (e.g. "1rem", "14px", "0.9em")
    // We accept any non-empty string -- CSS validity checked by the browser.
    // gradient: flag for auto palette. gradcolors(): custom colors.
    // Single pair:      gradcolors(start|end)          -- all series same
    // Per-series:       gradcolors(s0start|s0end : s1start|s1end : ...)
    // Java receives: "" | "1" | "start|end" | "s0s|s0e:s1s|s1e:..."
    local gradient_val ""
    if `"`gradcolors'"' != "" {
        // Split on colon to get per-series segments; validate each has a pipe
        local _gcsrc `"`gradcolors'"'
        local _gcsrc = subinstr(`"`_gcsrc'"', " ", "", .)
        local _gcok  1
        local _gcn   = 1 + (length(`"`_gcsrc'"') - length(subinstr(`"`_gcsrc'"', ":", "", .)))
        forvalues _gci = 1/`_gcn' {
            if `_gcn' == 1 {
                local _gcseg `"`_gcsrc'"'
            }
            else {
                // extract segment _gci (colon-delimited)
                local _gcpos = strpos(`"`_gcsrc'"', ":")
                if `_gci' < `_gcn' {
                    local _gcseg = substr(`"`_gcsrc'"', 1, `_gcpos'-1)
                    local _gcsrc = substr(`"`_gcsrc'"', `_gcpos'+1, .)
                }
                else {
                    local _gcseg `"`_gcsrc'"'
                }
            }
            local _gppos = strpos(`"`_gcseg'"', "|")
            if `_gppos' == 0 {
                di as err "gradcolors(): segment `_gci' missing | separator  (e.g. #1e40af|transparent)"
                exit 198
            }
            local _gc1 = strtrim(substr(`"`_gcseg'"', 1, `_gppos'-1))
            local _gc2 = strtrim(substr(`"`_gcseg'"', `_gppos'+1, .))
            if `"`_gc1'"' == "" | `"`_gc2'"' == "" {
                di as err "gradcolors(): segment `_gci' has empty start or end color"
                exit 198
            }
        }
        local gradient_val `"`gradcolors'"'
    }
    else if "`gradient'" != "" {
        local gradient_val "1"
    }

    // - Phase 2-C: leglabels / xticks / yticks validation (v3.4.0) -
    // leglabels: pipe-separated legend entry names. No further validation needed --
    //   arbitrary text is valid. Java handles index-out-of-range gracefully (ignores).
    // xticks / yticks: pipe-separated numeric values.
    //   Validate that every token is a real number; reject if any is not.
    if `"`xticks'"' != "" {
        local _xtoks = subinstr(`"`xticks'"', "|", " ", .)
        foreach _tok of local _xtoks {
            capture confirm number `_tok'
            if _rc {
                di as err "xticks(): `_tok' is not a valid number. Use pipe-separated numerics, e.g. xticks(0|25|50|75|100)"
                exit 198
            }
        }
    }
    if `"`yticks'"' != "" {
        local _ytoks = subinstr(`"`yticks'"', "|", " ", .)
        foreach _tok of local _ytoks {
            capture confirm number `_tok'
            if _rc {
                di as err "yticks(): `_tok' is not a valid number. Use pipe-separated numerics, e.g. yticks(0|10|20|30)"
                exit 198
            }
        }
    }

    // - Phase 2-B: annotation validation (v3.5.0) -
    // yline/xline: pipe-separated numeric values.
    if `"`yline'"' != "" {
        local _yltoks = subinstr(`"`yline'"', "|", " ", .)
        foreach _tok of local _yltoks {
            capture confirm number `_tok'
            if _rc {
                di as err "yline(): '`_tok'' is not a valid number. Use numeric values, e.g. yline(15000) or yline(10000|20000)"
                exit 198
            }
        }
    }
    if `"`xline'"' != "" {
        local _xltoks = subinstr(`"`xline'"', "|", " ", .)
        foreach _tok of local _xltoks {
            capture confirm number `_tok'
            if _rc {
                di as err "xline(): '`_tok'' is not a valid number. Use numeric values, e.g. xline(20) or xline(10|30)"
                exit 198
            }
        }
    }
    // apointsize/alabelfontsize: must be positive numbers if supplied
    // Use real() for validation -- confirm number can fail on string-typed options (v3.5.2)
    if `"`apointsize'"' != "" {
        local _apsz = real(`"`apointsize'"')
        if missing(`_apsz') | `_apsz' <= 0 {
            di as err "apointsize(): must be a positive number (px), e.g. apointsize(10)"
            exit 198
        }
    }
    if `"`alabelfontsize'"' != "" {
        local _alfs = real(`"`alabelfontsize'"')
        if missing(`_alfs') | `_alfs' <= 0 {
            di as err "alabelfontsize(): must be a positive number (px), e.g. alabelfontsize(12)"
            exit 198
        }
    }
    if `"`alabelgap'"' != "" {
        local _algap = real(`"`alabelgap'"')
        if missing(`_algap') | `_algap' < 0 {
            di as err "alabelgap(): must be a non-negative number (px), e.g. alabelgap(15)"
            exit 198
        }
    }

    // - stack100 flag: 100% stacked bar (v2.2.0) -
    local is_stack100 "0"
    if "`type'" == "stackedbar100" | "`type'" == "stackedhbar100" local is_stack100 "1"

    // - boxplot/violin validation (v2.4.0) -
    if "`type'" == "boxplot" | "`type'" == "violin" {
        // stat() is not applicable -- full distribution always shown
        if "`stat'" != "" & "`stat'" != "mean" {
            display as error "stat() is not applicable to `type' -- the full distribution is always shown"
            display as error "Remove the stat() option and rerun"
            exit 198
        }
        // whiskerfence() validation: must be a positive number
        if "`whiskerfence'" != "" {
            local wf_num = real("`whiskerfence'")
            if missing(`wf_num') | `wf_num' <= 0 {
                display as error "whiskerfence() must be a positive number (e.g. whiskerfence(1.5))"
                exit 198
            }
        }
        // bandwidth() validation: must be a positive number when supplied (v2.5.0)
        if "`bandwidth'" != "" {
            local bw_num = real("`bandwidth'")
            if missing(`bw_num') | `bw_num' <= 0 {
                display as error "bandwidth() must be a positive number (e.g. bandwidth(200))"
                exit 198
            }
        }
    }

    // - y2() validation (v2.3.0) -
    // Each variable named in y2() must also appear in varlist.
    // y2() is only meaningful with over() -- warn but do not error.
    if "`y2'" != "" {
        foreach v2var of local y2 {
            if !`:list v2var in varlist' {
                display as error "y2(): variable `v2var' not in varlist"
                exit 198
            }
        }
        if "`over'" == "" {
            display as text "Note: y2() has most effect when over() is also specified"
        }
    }

    // - y2range() parsing (v2.3.0) -
    // Accepts "min max" -- pass as single string to Java which splits on space.
    if "`y2range'" != "" {
        local y2r_parts : word count `y2range'
        if `y2r_parts' != 2 {
            display as error "y2range() requires exactly two values: y2range(min max)"
            exit 198
        }
    }

    // - bins() validation (v1.8.0) -
    // Number of histogram bins. Default "" = auto (Sturges rule in Java).
    // Accepts any positive integer.
    if "`bins'" != "" {
        local bins_num = real("`bins'")
        if missing(`bins_num') | `bins_num' < 2 | `bins_num' != int(`bins_num') {
            display as error "bins() must be a positive integer >= 2 (e.g. bins(10))"
            exit 198
        }
        local bins = string(int(`bins_num'))
    }

    // - histtype() validation (v1.8.0) -
    // Y-axis metric for histogram. Default "density" matches Stata's histogram default.
    if "`histtype'" == "" local histtype "density"
    local histtype = lower("`histtype'")
    local valid_histtypes "density frequency fraction"
    if !`:list histtype in valid_histtypes' {
        display as error "histtype() must be: density (default) | frequency | fraction"
        exit 198
    }

    // - filter / filter2 -
    // filter() and filter2() varnames and showmissing already parsed above.
    // filter2() requires filter() to also be specified.
    if "`filter2'" != "" & "`filter'" == "" {
        display as error "filter2() requires filter() to also be specified"
        exit 198
    }

    // - layout -
    if "`layout'" == "" local layout "vertical"
    local layout = lower("`layout'")
    if "`layout'" != "vertical" & "`layout'" != "horizontal" & "`layout'" != "grid" {
        display as error "layout() accepts: vertical horizontal grid"
        exit 198
    }
    if "`layout'" != "vertical" & "`by'" == "" {
        display as error "layout() only applies when by() is specified"
        exit 198
    }

    // - Axis type validation -
    if "`xtype'" == "" local xtype ""
    if "`ytype'" == "" local ytype ""
    if "`xtype'" != "" {
        local xtype = lower("`xtype'")
        if "`xtype'" != "linear" & "`xtype'" != "log" & "`xtype'" != "logarithmic" & "`xtype'" != "category" & "`xtype'" != "time" {
            display as error "xtype() accepts: linear log category time"
            exit 198
        }
        if "`xtype'" == "log" local xtype "logarithmic"
    }
    if "`ytype'" != "" {
        local ytype = lower("`ytype'")
        if "`ytype'" != "linear" & "`ytype'" != "log" & "`ytype'" != "logarithmic" {
            display as error "ytype() accepts: linear log"
            exit 198
        }
        if "`ytype'" == "log" local ytype "logarithmic"
    }

    // - xrange / yrange -
    local xrangemin ""
    local xrangemax ""
    if "`xrange'" != "" {
        local xrangemin : word 1 of `xrange'
        local xrangemax : word 2 of `xrange'
        if "`xrangemax'" == "" {
            display as error "xrange() requires two numbers e.g. xrange(0 100)"
            exit 198
        }
    }
    local yrangemin ""
    local yrangemax ""
    if "`yrange'" != "" {
        local yrangemin : word 1 of `yrange'
        local yrangemax : word 2 of `yrange'
        if "`yrangemax'" == "" {
            display as error "yrange() requires two numbers e.g. yrange(0 50)"
            exit 198
        }
    }

    // - ystart -
    local ystartzero "0"
    if "`ystart'" != "" {
        if lower("`ystart'") == "zero" local ystartzero "1"
        else {
            display as error "ystart() only accepts 'zero'"
            exit 198
        }
    }

    // - gridlines booleans -
    if "`xgridlines'" == "" local xgridlines "on"
    if "`ygridlines'" == "" local ygridlines "on"
    if "`xborder'"    == "" local xborder    "on"
    if "`yborder'"    == "" local yborder    "on"
    local xgridlines = lower("`xgridlines'")
    local ygridlines = lower("`ygridlines'")

    // - animate -
    if "`animate'" == "" local animate ""
    else {
        local animate = lower("`animate'")
        if "`animate'" != "none" & "`animate'" != "fast" & "`animate'" != "slow" {
            display as error "animate() accepts: none fast slow"
            exit 198
        }
    }

    // - tooltip -
    if "`tooltipmode'" == "" local tooltipmode "index"
    local tooltipmode = lower("`tooltipmode'")
    if "`tooltipposition'" == "" local tooltipposition "average"
    local tooltipposition = lower("`tooltipposition'")

    // - legend -
    if "`legend'" == "" local legend "top"
    local legend = lower("`legend'")
    // v3.5.34: nolegend flag is an alias for legend(none) (Stata convention)
    if "`nolegend'" != "" local legend "none"
    local valid_legend "top bottom left right none"
    if !`:list legend in valid_legend' {
        display as error "legend() accepts: top bottom left right none"
        exit 198
    }

    // - stepped -
    if "`stepped'" != "" {
        local stepped = lower("`stepped'")
        if "`stepped'" != "before" & "`stepped'" != "after" & "`stepped'" != "middle" {
            display as error "stepped() accepts: before after middle"
            exit 198
        }
    }

    // - pointstyle -
    if "`pointstyle'" != "" {
        local pointstyle = lower("`pointstyle'")
        local valid_ps "circle cross dash line rect rectrounded star triangle"
        if !`:list pointstyle in valid_ps' {
            display as error "pointstyle() accepts: circle cross dash line rect rectRounded star triangle"
            exit 198
        }
        if "`pointstyle'" == "rectrounded" local pointstyle "rectRounded"
    }

    // - stat -
    // For pie/donut: pct (default) | sum
    // For bar/line/area: mean (default) | sum | count | median | min | max
    // Default depends on chart type
    if "`stat'" == "" {
        if "`type'" == "pie" | "`type'" == "donut" {
            local stat "pct"
        }
        else {
            local stat "mean"
        }
    }
    local stat = lower("`stat'")
    if "`stat'" != "mean"   & "`stat'" != "pct"    & "`stat'" != "sum"  & ///
       "`stat'" != "count"  & "`stat'" != "median"  & ///
       "`stat'" != "min"    & "`stat'" != "max" {
        display as error "stat() accepts: mean (default) | sum | count | median | min | max"
        display as error "  (for pie/donut charts: pct | sum)"
        exit 198
    }

    // - pie/donut validation -
    local nvar : word count `varlist'
    if ("`type'" == "pie" | "`type'" == "donut") {
        if `nvar' != 1 {
            display as error "Pie/donut requires exactly 1 variable e.g. sparkta price, type(donut) over(rep78)"
            exit 198
        }
        if "`over'" == "" {
            display as error "Pie/donut requires over() e.g. sparkta price, type(donut) over(rep78)"
            exit 198
        }
    }
    if ("`type'" == "scatter" | "`type'" == "bubble") & `nvar' < 2 {
        display as error "Scatter/bubble requires at least 2 numeric variables"
        exit 198
    }
    if "`type'" == "bubble" & `nvar' < 3 {
        display as error "Bubble requires exactly 3 variables: x y size"
        exit 198
    }

    // - histogram validation (v1.8.0) -
    if "`type'" == "histogram" {
        if `nvar' != 1 {
            display as error "histogram requires exactly 1 numeric variable"
            exit 198
        }
        if "`over'" != "" {
            display as error "histogram does not support over() -- use by() for panel histograms"
            exit 198
        }
    }

    // - Boolean flags -
    local is_horizontal "0"
    if "`horizontal'" != "" local is_horizontal "1"
    local is_stack "0"
    if "`stacked'" != "" local is_stack "1"
    local is_fill "0"
    if "`fill'" != "" local is_fill "1"
    local is_datalabels "0"
    if "`datalabels'" != "" local is_datalabels "1"
    local is_pielabels "0"
    if "`pielabels'" != "" local is_pielabels "1"
    local is_nomissing "0"
    if "`nomissing'" != "" local is_nomissing "1"
    local is_spanmissing "0"
    if "`spanmissing'" != "" local is_spanmissing "1"
    // - sortgroups: accepts asc or desc (v1.5 extended from binary toggle) -
    if "`sortgroups'" == "" local is_sortgroups ""
    else {
        local sg_low = lower("`sortgroups'")
        if "`sg_low'" == "asc" | "`sg_low'" == "desc" {
            local is_sortgroups "`sg_low'"
        }
        else {
            display as error "sortgroups() accepts: asc | desc"
            exit 198
        }
    }
    local is_novaluelabels "0"
    if "`novaluelabels'" != "" local is_novaluelabels "1"
    local is_nostats "0"
    if "`nostats'" != "" local is_nostats "1"

    // - Defaults for optional strings -
    foreach opt in title subtitle theme note caption export xtitle ytitle ///
        over by bgcolor plotcolor gridcolor colors tooltipformat aspect    ///
        smooth pointsize pointstyle pointborderwidth pointrotation         ///
        linewidth barwidth bargroupwidth borderradius opacity padding      ///
        easing animdelay legtitle legsize legboxheight                     ///
        xtickcount ytickcount xtickangle ytickangle xstepsize ystepsize   ///
        xlabels ylabels                                                    ///
        cutout rotation circumference sliceborder hoveroffset stepped {
        if "``opt''" == "" local `opt' ""
    }
    if "`gridopacity'" == "" local gridopacity "0.15"
    if "`title'"       == "" local title "Sparkta"
    if "`theme'"       == "" local theme "default"
    // v3.3.0: accept named palettes, dark/light_palette compounds, and background themes
    // Split on underscore: prefix = dark|light|"", suffix = palette name|""
    local theme_ok = 0
    // bare background keywords
    foreach t in default dark light {
        if "`theme'" == "`t'" local theme_ok = 1
    }
    // bare palette names
    foreach t in tab1 tab2 tab3 cblind1 neon swift_red viridis {
        if "`theme'" == "`t'" local theme_ok = 1
    }
    // dark_<palette> and light_<palette> compound themes
    foreach bg in dark light {
        foreach t in tab1 tab2 tab3 cblind1 neon swift_red viridis {
            if "`theme'" == "`bg'_`t'" local theme_ok = 1
        }
    }
    if `theme_ok' == 0 {
        display as error "theme() accepts: default | dark | light | <palette>"
        display as error "  palettes: tab1 tab2 tab3 cblind1 neon swift_red viridis"
        display as error "  compounds: dark_<palette> or light_<palette>"
        exit 198
    }

    // - stackedarea / stackedline aliasing (no braces - Stata 19 safe) -
    // stackedarea -> type=area, is_fill=1 (fill flag passed explicitly to Java)
    // stackedline -> type=line, is_fill stays 0 (no fill)
    // v3.5.34: stackedline was aliased to "area" causing fill on all series.
    // v3.5.34: stackedarea now explicitly sets is_fill=1 in ado (belt-and-braces:
    //          Java area alias also sets fill=true, but ado flag makes it jar-independent).
    local origtype "`type'"
    if "`type'" == "stackedarea" local type "area"
    if "`origtype'" == "stackedarea" local is_fill "1"
    if "`type'" == "stackedline" local type "line"
    if "`origtype'" == "stackedarea" local is_stack "1"
    if "`origtype'" == "stackedline" local is_stack "1"
    // - Resolve sample (v1.6.0) -
    // mark all obs satisfying if/in. Missing value handling is done in Java.
    // We pass the TOUSE VARIABLE NAME to Java so it can iterate obs directly,
    // avoiding the ~67K char Stata macro limit that previously capped N at ~13,000.
    tempvar touse
    mark `touse' `if' `in'
    // v1.8.8: markout excludes obs with missing values in grouping/filter vars.
    // v1.9.1: skip markout for variables with showmissing set -- those obs
    //         are intentionally kept and labelled as "(Missing)" in the chart.
    markout `touse' `varlist'
    // v3.5.9: use strok for string grouping/filter vars so markout only drops
    // obs with empty-string values, not ALL obs (Stata default for string vars).
    if "`over'"    != "" & "`sm_over'"    == "0" {
        local _otyp : type `over'
        if substr("`_otyp'", 1, 3) == "str" markout `touse' `over', strok
        else                                 markout `touse' `over'
    }
    if "`by'"      != "" & "`sm_by'"      == "0" {
        local _btyp : type `by'
        if substr("`_btyp'", 1, 3) == "str" markout `touse' `by', strok
        else                                 markout `touse' `by'
    }
    if "`filter'"  != "" & "`sm_filter'"  == "0" {
        local _ftyp : type `filter'
        if substr("`_ftyp'", 1, 3) == "str" markout `touse' `filter', strok
        else                                 markout `touse' `filter'
    }
    if "`filter2'" != "" & "`sm_filter2'" == "0" {
        local _f2typ : type `filter2'
        if substr("`_f2typ'", 1, 3) == "str" markout `touse' `filter2', strok
        else                                  markout `touse' `filter2'
    }
    quietly count if `touse'
    if r(N) == 0 {
        display as error "No observations in sample"
        exit 2000
    }
    local nobs = r(N)
    // Pass the tempvar name so Java reads it via Data.getVarIndex()
    local tousename "`touse'"
    display as text "Building sparkta v3.5.36 with `nobs' observations..."
    display as text "  (Stata personal dir: `c(sysdir_personal)')"

    // - Large dataset memory warning (v1.6.0) -
    // Java heap defaults vary by Stata version. If sparkta crashes with a
    // "Java heap space" error on large datasets, increase the heap using
    // set java_heapmax, then RESTART Stata for the change to take effect.
    //
    // Default Java heap sizes by Stata version:
    //   Stata 15-16:  384 MB  (default)
    //   Stata 17-18:  512 MB  (default)
    //   Stata 19+  : 4096 MB  (default - unlikely to need adjustment)
    //
    // Thresholds below assume 2-5 variables. More variables = more memory.
    // Run:  query java   to check your current heap allocation and usage.
    if `nobs' > 500000 {
        display as text ""
        display as text "  {hline 58}"
        display as text "  WARNING: Very large dataset (`nobs' observations)."
        display as text "  Java may run out of memory. If sparkta fails:"
        display as text ""
        display as text "    Stata 15-16 (default 384 MB):"
        display as text "      set java_heapmax 2048m"
        display as text "    Stata 17-18 (default 512 MB):"
        display as text "      set java_heapmax 2048m"
        display as text "    Stata 19+   (default 4096 MB):"
        display as text "      set java_heapmax 8192m"
        display as text ""
        display as text "  Restart Stata after changing."
        display as text "  Check current heap with:  query java"
        display as text "  {hline 58}"
        display as text ""
    }
    else if `nobs' > 100000 {
        display as text ""
        display as text "  Note: large dataset (`nobs' obs)."
        display as text "  If sparkta fails with a memory error:"
        display as text "    Stata 15-16 (default 384 MB): set java_heapmax 1024m"
        display as text "    Stata 17-18 (default 512 MB): set java_heapmax 1024m"
        display as text "    Stata 19+   (default 4096 MB): unlikely to need change"
        display as text "  Restart Stata after changing. Check with: query java"
        display as text ""
    }

    // - Locate sparkta.jar (v3.5.41) -
    // Search order:
    //   1. findfile "sparkta.ado" -- searches the adopath and returns the first
    //      match; jar must be co-located with the ado (guaranteed by our pkg file).
    //      This correctly handles ssc install (PLUS/s/sparkta/) and net install
    //      (personal/) because the jar is listed in sparkta.pkg alongside the ado.
    //   2. sysdir_personal variants -- net install personal, all separator styles
    //   3. sysdir_PLUS/s/sparkta/ -- SSC explicit fallback
    //   4. c(pwd) -- last resort: working directory / dev copy
    local jarpath ""

    // --- Path 1: findfile -- primary (works for SSC, net install, and adopath) ---
    // findfile searches the full adopath for sparkta.ado and returns its directory.
    // Because sparkta.jar is listed in sparkta.pkg alongside sparkta.ado, it lands
    // in the same folder. Stripping the filename gives us the correct jar directory.
    capture findfile "sparkta.ado"
    if !_rc {
        local _ffdir = subinstr(r(fn), "sparkta.ado", "", .)
        local _p1 `"`_ffdir'sparkta.jar"'
        capture confirm file `"`_p1'"'
        if !_rc local jarpath `"`_p1'"'
    }

    // --- Paths 2a-2c: sysdir_personal (net install, all separator variants) ---
    if "`jarpath'" == "" {
        foreach trypath in                                  ///
            `"`c(sysdir_personal)'sparkta.jar"'           ///
            `"`c(sysdir_personal)'\sparkta.jar"'         ///
            `"`c(sysdir_personal)'/sparkta.jar"' {
            capture confirm file `"`trypath'"'
            if !_rc {
                local jarpath `"`trypath'"'
                continue, break
            }
        }
    }

    // --- Paths 3a-3b: sysdir_PLUS/s/sparkta/ (SSC explicit fallback) ---
    if "`jarpath'" == "" {
        foreach trypath in                                              ///
            `"`c(sysdir_PLUS)'s\sparkta\sparkta.jar"'              ///
            `"`c(sysdir_PLUS)'s/sparkta/sparkta.jar"' {
            capture confirm file `"`trypath'"'
            if !_rc {
                local jarpath `"`trypath'"'
                continue, break
            }
        }
    }

    // --- Paths 4a-4b: c(pwd) -- working directory (dev / manual copy) ---
    if "`jarpath'" == "" {
        foreach trypath in                          ///
            `"`c(pwd)'\sparkta.jar"'             ///
            `"`c(pwd)'/sparkta.jar"' {
            capture confirm file `"`trypath'"'
            if !_rc {
                local jarpath `"`trypath'"'
                continue, break
            }
        }
    }

    if "`jarpath'" == "" {
        display as error "sparkta.jar not found. Searched:"
        display as error "  Same folder as sparkta.ado (via findfile)"
        display as error "  `c(sysdir_personal)'sparkta.jar"
        display as error "  `c(sysdir_PLUS)'s\sparkta\sparkta.jar"
        display as error "  `c(pwd)'\sparkta.jar"
        display as error ""
        display as error "Fix: sparkta.jar must be in the same folder as sparkta.ado."
        display as error "Your Stata personal folder: `c(sysdir_personal)'"
        display as error "Reinstall: ssc install sparkta"
        exit 198
    }

    // - Call Java -
    javacall com.dashboard.DashboardBuilder execute,            ///
        classpath("`jarpath'")                                  ///
        args(`"`varlist'"'           ///  0  varlist
             `"`type'"'              ///  1  chart type
             `"`title'"'             ///  2  title
             `"`theme'"'             ///  3  theme
             `"`export'"'            ///  4  export path
             `"`xtitle'"'            ///  5  x-axis label
             `"`ytitle'"'            ///  6  y-axis label
             `"`over'"'              ///  7  over variable
             `"`by'"'                ///  8  by variable
             `"`tousename'"'         ///  9  touse variable name (v1.6.0)
             `"`layout'"'            ///  10 layout
             `"`bgcolor'"'           ///  11 page bg color
             `"`plotcolor'"'         ///  12 plot region color
             `"`gridcolor'"'         ///  13 grid color
             `"`gridopacity'"'       ///  14 grid opacity
             `"`colors'"'            ///  15 series colors
             `"`note'"'              ///  16 note
             `"`caption'"'           ///  17 caption
             `"`subtitle'"'          ///  18 subtitle
             `"`xrangemin'"'         ///  19 x min
             `"`xrangemax'"'         ///  20 x max
             `"`yrangemin'"'         ///  21 y min
             `"`yrangemax'"'         ///  22 y max
             `"`ystartzero'"'        ///  23 y start at zero flag
             `"`is_horizontal'"'     ///  24 horizontal flag
             `"`is_stack'"'          ///  25 stack flag
             `"`is_fill'"'           ///  26 fill flag
             `"`smooth'"'            ///  27 line tension
             `"`pointsize'"'         ///  28 point radius
             `"`linewidth'"'         ///  29 line width
             `"`aspect'"'            ///  30 aspect ratio
             `"`animate'"'           ///  31 animation speed
             `"`legend'"'            ///  32 legend position
             `"`is_datalabels'"'     ///  33 data labels flag
             `"`tooltipformat'"'     ///  34 tooltip format
             `"`stat'"'              ///  35 pie stat
             `"`cutout'"'            ///  36 donut cutout
             `"`rotation'"'          ///  37 pie rotation
             `"`circumference'"'     ///  38 pie circumference
             `"`sliceborder'"'       ///  39 slice border
             `"`hoveroffset'"'       ///  40 hover offset
             `"`is_pielabels'"'      ///  41 pie labels flag
             `"`is_nomissing'"'      ///  42 no missing flag
             `"`xtype'"'             ///  43 x-axis type
             `"`ytype'"'             ///  44 y-axis type
             `"`xtickcount'"'        ///  45 x tick count
             `"`ytickcount'"'        ///  46 y tick count
             `"`xtickangle'"'        ///  47 x tick angle
             `"`ytickangle'"'        ///  48 y tick angle
             `"`xstepsize'"'         ///  49 x step size
             `"`ystepsize'"'         ///  50 y step size
             `"`xgridlines'"'        ///  51 x gridlines on/off
             `"`ygridlines'"'        ///  52 y gridlines on/off
             `"`xborder'"'           ///  53 x axis border
             `"`yborder'"'           ///  54 y axis border
             `"`barwidth'"'          ///  55 bar thickness
             `"`bargroupwidth'"'     ///  56 bar group gap
             `"`borderradius'"'      ///  57 rounded bar corners
             `"`opacity'"'           ///  58 fill opacity
             `"`padding'"'           ///  59 chart padding
             `"`easing'"'            ///  60 easing function
             `"`animdelay'"'         ///  61 animation delay
             `"`tooltipmode'"'       ///  62 tooltip mode
             `"`tooltipposition'"'   ///  63 tooltip position
             `"`legtitle'"'       ///  64 legend title (legtitle)
             `"`legsize'"'        ///  65 legend font size (legsize)
             `"`legboxheight'"'   ///  66 legend box height (legboxheight)
             `"`pointstyle'"'        ///  67 point style
             `"`pointborderwidth'"'  ///  68 point border width
             `"`pointrotation'"'     ///  69 point rotation
             `"`is_spanmissing'"'    ///  70 span missing flag
             `"`stepped'"'           ///  71 stepped line mode
             `"`is_sortgroups'"'           ///  72 sort groups
             `"`is_novaluelabels'"'        ///  73 suppress value labels
             `"`xlabels'"'                 ///  74 custom x-axis tick labels
             `"`ylabels'"'                 ///  75 custom y-axis tick labels
             `"`filter'"'                  ///  76 filter variable 1
             `"`filter2'"'                 ///  77 filter variable 2
             `"`is_nostats'"'              ///  78 suppress stats panel (v1.5)
             `"`cilevel'"'                 ///  79 CI level for cibar/ciline (v1.7)
             `"`cibandopacity'"'           ///  80 ciline band opacity 0-1 (v1.7.2)
             `"`bins'"'                    ///  81 histogram bin count (v1.8.0)
             `"`histtype'"'               ///  82 histogram y-axis: density|frequency|fraction (v1.8.0)
             `"`sm_over'"'                ///  83 showmissing for over() (v1.9.1)
             `"`sm_by'"'                  ///  84 showmissing for by() (v1.9.1)
             `"`sm_filter'"'              ///  85 showmissing for filter() (v1.9.1)
             `"`sm_filter2'"' ///  86 showmissing for filter2() (v1.9.1)
             `"`areaopacity'"'   ///  87 area fill opacity 0-1 (v2.0.1)
             `"`is_offline'"'   ///  88 offline: embed JS inline (v2.0.2)
             `"`is_stack100'"' ///  89 100% stacked bar (v2.2.0)
             `"`y2'"' ///  90 secondary y-axis variables (v2.3.0)
             `"`y2title'"' ///  91 secondary y-axis title (v2.3.0)
             `"`y2range'"' ///  92 secondary y-axis range "min max" (v2.3.0)
             `"`whiskerfence'"'   ///  93 box/violin whisker fence multiplier (v2.4.0)
             `"`mediancolor'"'    ///  94 median marker color override (v2.4.10)
             `"`meancolor'"'      ///  95 mean marker color override (v2.4.10)
             `"`bandwidth'"'      ///  96 KDE bandwidth for violin (v2.5.0; empty=Silverman auto)
             `"`titlesize'"'      ///  97 title font size (v2.6.0 Phase 1-A)
             `"`titlecolor'"'     ///  98 title color (v2.6.0 Phase 1-A)
             `"`subtitlesize'"'   ///  99 subtitle font size (v2.6.0 Phase 1-A)
             `"`subtitlecolor'"'  ///  100 subtitle color (v2.6.0 Phase 1-A)
             `"`xtitlesize'"'     ///  101 x-axis title font size (v2.6.0 Phase 1-A)
             `"`xtitlecolor'"'    ///  102 x-axis title color (v2.6.0 Phase 1-A)
             `"`ytitlesize'"'     ///  103 y-axis title font size (v2.6.0 Phase 1-A)
             `"`ytitlecolor'"'    ///  104 y-axis title color (v2.6.0 Phase 1-A)
             `"`xlabsize'"'       ///  105 x tick label font size (v2.6.0 Phase 1-A)
             `"`xlabcolor'"'      ///  106 x tick label color (v2.6.0 Phase 1-A)
             `"`ylabsize'"'       ///  107 y tick label font size (v2.6.0 Phase 1-A)
             `"`ylabcolor'"'      ///  108 y tick label color (v2.6.0 Phase 1-A)
             `"`legcolor'"'       ///  109 legend text color (v2.6.0 Phase 1-A)
             `"`legbgcolor'"'     ///  110 legend background color (v2.6.0 Phase 1-A)
             `"`tooltipbg'"'      ///  111 tooltip background color (v2.6.0 Phase 1-B)
             `"`tooltipborder'"'  ///  112 tooltip border color (v2.6.0 Phase 1-B)
             `"`tooltipfontsize'"' ///  113 tooltip font size (v2.6.0 Phase 1-B)
             `"`tooltippadding'"' ///  114 tooltip padding px (v2.6.0 Phase 1-B)
             `"`is_download'"'   ///  115 download PNG button (v2.7.0)
             `"`is_yreverse'"'    ///  116 reverse y-axis (v3.0.3)
             `"`is_xreverse'"'    ///  117 reverse x-axis (v3.0.3)
             `"`is_noticks'"'     ///  118 hide tick marks (v3.0.3)
             `"`ygrace'"'         ///  119 y-axis grace padding (v3.0.3)
             `"`animduration'"'   ///  120 exact animation ms (v3.0.3)
             `"`lpattern'"'       ///  121 line dash pattern all series (v3.1.0)
             `"`lpatterns'"'      ///  122 per-series dash patterns pipe-sep (v3.1.0)
             `"`is_nopoints'"'    ///  123 suppress point markers (v3.1.0)
             `"`pointhoversize'"' ///  124 point hover radius px (v3.1.0)
             `"`notesize'"'       ///  125 note/caption font size (v3.2.0)
             `"`gradient_val'"'   ///  126 gradient fill on area/bar (v3.2.0)
             `"`yline'"'          ///  127 y reference lines pipe-sep (v3.5.0)
             `"`xline'"'          ///  128 x reference lines pipe-sep (v3.5.0)
             `"`ylinecolor'"'     ///  129 colors per yline pipe-sep (v3.5.0)
             `"`xlinecolor'"'     ///  130 colors per xline pipe-sep (v3.5.0)
             `"`ylinelabel'"'     ///  131 labels per yline pipe-sep (v3.5.0)
             `"`xlinelabel'"'     ///  132 labels per xline pipe-sep (v3.5.0)
             `"`yband'"'          ///  133 horizontal bands "lo hi" pipe-sep (v3.5.0)
             `"`xband'"'          ///  134 vertical bands "lo hi" pipe-sep (v3.5.0)
             `"`ybandcolor'"'     ///  135 colors per yband pipe-sep (v3.5.0)
             `"`xbandcolor'"'     ///  136 colors per xband pipe-sep (v3.5.0)
             `"`leglabels'"'      ///  137 legend label overrides pipe-sep (v3.4.0)
             `"`xticks'"'         ///  138 custom x tick values pipe-sep (v3.4.0)
             `"`yticks'"'         ///  139 custom y tick values pipe-sep (v3.4.0)
             `"`apoint'"'         ///  140 annotation points y x pairs (v3.5.0)
             `"`apointcolor'"'    ///  141 colors per apoint pipe-sep (v3.5.0)
             `"`apointsize'"'     ///  142 apoint radius px (v3.5.0)
             `"`alabelpos'"'      ///  143 label positions "y x pos" pipe-sep pos=minute clock (v3.5.0/v3.5.2)
             `"`alabeltext'"'     ///  144 label texts pipe-sep (v3.5.0)
             `"`alabelfontsize'"' ///  145 label font size px (v3.5.0)
             `"`aellipse'"'       ///  146 ellipses "ymin xmin ymax xmax" pipe-sep (v3.5.0)
             `"`aellipsecolor'"'  ///  147 ellipse fill colors pipe-sep (v3.5.0)
             `"`aellipseborder'"' ///  148 ellipse border colors pipe-sep (v3.5.0)
             `"`alabelgap'"'      ///  149 label offset distance px (v3.5.2)
             `"`_rlbl'"')        //  150 relabel over() groups on x-axis AND legend pipe-sep (v3.5.34)

end

// sparkta_check is in sparkta_check.ado
// Stata finds it automatically when that file is in the ado path.
