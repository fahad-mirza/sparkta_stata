*! sparkta version 3.6.0 2026-09-13
*! sparkta -- interactive self-contained HTML charts and dashboards from Stata
*! Author: Fahad Mirza. Requires Stata 17 or later (Java 11 backend).
*! Release v3.6.0 (2026-09-13) = build v3.6.0-t2j fix9z (fix9f, confirmed on real Stata 210/210, +
*!   fix9g large-data mode, fix9g-c fit() string cap, fix9h fit line above the points). Full history
*!   (t-series and fix tags) lives in docs/CHANGELOG_sparkta_full.md; keep THIS header short.

program define sparkta
    version 17

    // Single version constant -- update this one line on every version bump
    local sparkta_version "3.6.0"

    // Print version so user can confirm which ado is loaded
    display as text "  [sparkta v`sparkta_version']"

    // Save raw argument string BEFORE syntax strips quotes.
    // Used by indicators() and addstats() parsers to extract quoted content.
    local _raw_0 `"`0'"'

    syntax [varlist(default=none)] [if] [in],          ///
        [TYPE(string)]                  ///  bar line scatter pie hbar stackedbar stackedarea area bubble donut cibar ciline stackedbar100 stackedhbar100
        [TITLE(string)]                 ///  main chart title
        [SUBTitle(string)]              ///  subtitle below title
        [NOTE(string)]                  ///  note below chart
        [CAPTION(string)]               ///  caption below note
        [EXPORT(string)]                ///  export path for HTML file
        [SAVEas(string)]                ///  saveas(f.pdf f.svg f.png[, chart|page|table scale(#) close idle(#)]) -- chart scope default; scale(3)=3x PNG; close = quit the session browser now; idle(#) = seconds before it quits by itself (default 120)
        [FINDBrowser]                   ///
        [CLOSEBrowser]                  ///  quit the session's headless browser now (it also closes itself after 2 min idle and on exit)  v3.6.0-s9s: diagnostic -- list the headless-browser candidates saveas() would use, then exit
        [BROWser(string)]               ///  v3.6.0-s9t: path to Edge/Chrome for saveas() when auto-detection fails (overrides $SPARKTA_BROWSER)
        [THEME(string)]                 ///  default | dark
        /// - Axes -
        [XTITLE(string)]                ///  x-axis label
        [YTITLE(string)]                ///  y-axis label
        [XRANGE(string)]                ///  x-axis bounds: two numbers e.g. xrange(0 100)
        [YRANGE(string)]                ///  y-axis bounds: two numbers e.g. yrange(0 50)
        [YSTARt(string)]                ///  ystart(zero) forces y-axis to start at 0
        [XTYpe(string)]                 ///  x-axis scale: linear (default) | log | category | time
        [YTYpe(string)]                 ///  y-axis scale: linear (default) | log
        [XTICKCOunt(string)]            ///  number of x-axis ticks
        [YTICKCount(string)]            ///  number of y-axis ticks
        [XTICKANgle(string)]            ///  x-axis label rotation degrees e.g. xtickangle(45)
        [YTICKANgle(string)]            ///  y-axis label rotation degrees
        [XLABELs(string)]               ///  custom x-axis tick labels, pipe-separated: xlabels(Low|Med|High)
        [YLABELs(string)]               ///  custom y-axis tick labels, pipe-separated: ylabels(Bad|OK|Good)
        [XSTEPsize(string)]             ///  x-axis tick interval e.g. xstepsize(10)
        [YSTEPsize(string)]             ///  y-axis tick interval
        [XGRIDlines(string)]            ///  on (default) | off
        [YGRIDlines(string)]            ///  on (default) | off
        [XBORder(string)]               ///  on (default) | off - axis border line
        [YBORder(string)]               ///  on (default) | off
        /// - Grouping / layout -
        [OVER(string)]                  ///  group variable (same chart) [, showmissing]
        [BY(string)]                    ///  panel variable (separate charts) [, showmissing]
        [FILTERs(string)]               ///  unlimited filters: space-sep varlist e.g. filters(rep78 foreign)
        [SLIDers(string)]               ///  dual-handle range sliders: space-sep numeric varlist e.g. sliders(price mpg) (F-1)
        [MLABEl(string)]                ///  scatter marker labels: mlabel(varname) or mlabel(varname, all) to force all labels
        [MLABPOs(string)]               ///  label minute-clock position 0-59 (0=center,15=right,30=below,45=left); same convention as alabelpos()
        [MLABVPOsition(string)]         ///  per-obs minute-clock position variable (0-59 per row); mirrors Stata mlabvposition()
        [FIT(string)]                   ///  scatter fit line: lfit qfit lowess exp log power ma
        [FITCi]                         ///  add CI band to fit line (lfit and qfit only)
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
        [POINTSTyle(string)]            ///  circle cross dash line rect rectRounded star triangle
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
        [YFRee]                             ///  v3.6.0-s9g: by() panels keep their own value axis (default: one shared, comparable axis)
        [PLOTMargin(string)]            ///  v3.6.0-s8p: cushion between data and axes, percent of data range, like Stata plotregion(margin()): plotmargin(5) | plotmargin(x y) | plotmargin(l r b t)
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
        [LEGBOXheight(string)]       ///  legend color box height px
        /// - Colors & styling -
        [COLORS(string asis)]           ///  series colors: names, hex, "r g b" triplets, name%50, name*0.6 (t2e; asis keeps the quotes)
        [PALette(string)]               ///  v3.6.0-s8c: colour scheme via Ben Jann's colorpalette (SSC), e.g. palette(okabe) palette(viridis, n(6) reverse)
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
        [OFFLINE]                       ///  (default since v3.6.0-t1k) self-contained page, JS embedded; accepted for compatibility
        [ONline]                        ///  v3.6.0-t1k: load Chart.js and plugins from a CDN instead (~250 KB smaller; needs internet to view)
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
        [TITLESIze(string)]             ///  title font size in pt (e.g. titlesize(24))
        [TITLECOlor(string)]            ///  title color (hex, rgb, or CSS name)
        [SUBTITLESIze(string)]          ///  subtitle font size in pt
        [SUBTITLECOlor(string)]         ///  subtitle color
        [XTITLESIze(string)]            ///  x-axis title font size in pt
        [XTITLECOlor(string)]           ///  x-axis title color
        [YTITLESIze(string)]            ///  y-axis title font size in pt
        [YTITLECOlor(string)]           ///  y-axis title color
        [XLABSize(string)]              ///  x tick label font size in pt
        [XLABColor(string)]             ///  x tick label color
        [YLABSize(string)]              ///  y tick label font size in pt
        [YLABColor(string)]             ///  y tick label color
        [LEGColor(string)]              ///  legend text color
        [LEGBGColor(string)]            ///  legend background color
        /// - Phase 1-B: tooltip styling (v2.6.0) -
        [TOOLTIPBG(string)]             ///  tooltip background color (e.g. rgba(0,0,0,0.9))
        [TOOLTIPBOrder(string)]         ///  tooltip border color
        [TOOLTIPFONtsize(string)]       ///  tooltip font size in pt
        [TOOLTIPPADding(string)]        ///  tooltip padding in px
        /// - Phase 2-A: export buttons (v2.7.0) -
        [DOWNload]                          ///  show PNG download button on chart
        [NOTOOLbar]                         ///  t2j fix8w (U21): hide the export toolbar (shown by default since fix8w)
        /// - Phase 1-C: axis utilities (v3.0.3) -
        [YREVerse]                          ///  reverse y-axis direction (top to bottom)
        [XREVerse]                          ///  reverse x-axis direction (right to left)
        [NOTICKs]                           ///  hide axis tick marks (labels remain visible)
        [YGRAce(string)]                    ///  y-axis grace padding above/below data: "5%%" or "10"
        [ANIMDUration(string)]              ///  animation duration in ms e.g. animduration(800)
        /// - Phase 1-D: line/point style (v3.1.0) -
        [LPATTErn(string)]                  ///  line dash pattern for all series: solid | dash | dot | dashdot
        [LPATTERns(string)]                 ///  per-series dash patterns pipe-separated: e.g. solid|dash|dot
        [NOPoints]                          ///  suppress point markers on line/area charts
        [NOLEGend]                          ///  suppress legend (alias for legend(none)) (v3.5.34)
        [POINTHoversize(string)]            ///  point radius on hover px (default: pointsize + 2)
        /// - Phase 1-E: note size + gradient (v3.2.0) -
        [NOTESIze(string)]                  ///  font size for note/caption text e.g. notesize(1rem) or notesize(14px)
        [GRADient]                          ///  gradient fill (auto palette colors)
        [GRADCOlors(string)]                ///  gradient colors: "s|e" all series, or "s0|e0:s1|e1:..." per-series
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
        [YBANDCOlor(string)]                ///  fill colors per yband, pipe-sep: ybandcolor(rgba(0,200,0,0.1))
        [XBANDCOlor(string)]                ///  fill colors per xband, pipe-sep: xbandcolor(rgba(0,0,200,0.1))
        [APoint(string)]                    ///  annotation points: apoint(y1 x1 y2 x2 ...) space-sep y x pairs
        [APOINTCOlor(string)]               ///  colors per apoint, pipe-sep: apointcolor(red|blue)
        [APOINTSIze(string)]                ///  radius px for all apoints: apointsize(10)
        [ALABELPos(string)]                 ///  label positions: alabelpos(y1 x1 pos|y2 x2 pos) pipe-sep; pos=minute clock (0=center,15=right,30=below,45=left)
        [ALABELText(string)]                ///  label texts: alabeltext(Cluster A|Cluster B) pipe-sep
        [ALABELFs(string)]            ///  font size px for annotation labels: alabelfs(12)
        [ALABELGap(string)]                 ///  label offset distance px from point: alabelgap(15)
        [AELlipse(string)]                  ///  ellipses: aellipse(ymin xmin ymax xmax|...) 4 values per ellipse
        [AELLIPSEColor(string)]             ///  fill colors per ellipse, pipe-sep: aellipsecolor(rgba(0,0,200,0.1))
        [AELLIPSEBorder(string)]            ///  border colors per ellipse, pipe-sep: aellipseborder(navy)
        /// - Post-estimation charts (v3.6.0) -
        [OMIT(string)]                      ///  coefplot: drop coefficients by name, e.g. omit(_cons 1.female)
        [SHOW(string)]                      ///  coefplot: keep only listed coefficients, e.g. show(mpg weight)
        [ESTNames(string)]                  ///
        [RESUlts(string asis)]              ///  t2f fix 4: results(file|frame:name [, name() time() b() ci() se() pvalue()]) -> matrix mode
        [MATrix(string)]                    ///  v3.6.0-t2a: MATRIX MODE -- plot from a matrix: matrix(M) | matrix(M[2]) | matrix(M[,3]) | matrix(r(table)) | matrix(A B C)
        [CI(string)]                        ///  matrix mode: lower/upper by position or name: ci((2 3)) ci(2 3) ci(ll ul), or another matrix ci(C)
        [SE(string)]                        ///  matrix mode: standard errors by position/name/matrix -> CI at cilevel (z, or t with df())
        [DF(string)]                        ///  matrix mode: degrees of freedom (a number, or a row/column name or position)
        [PVALue(string)]                    ///  matrix mode: p-values by position/name (autodetected from r(table))
        [PLOTLabels(string)]                ///  Jann alias of estlabels(): labels for the plots when several models/matrices are combined  coefplot: stored estimate names for multi-model, e.g. estnames(m1 m2 m3)
        [COEFLBl(string)]                   ///  coefplot: pipe-sep coefficient label overrides, e.g. coeflbl(mpg "MPG"|weight "Wt")
        [HEADings(string)]                  ///  coefplot: pipe-sep section headings, e.g. headings(2 "Demographics"|5 "Controls")
        [NOCOns]                            ///  coefplot: drop _cons (shorthand for omit(_cons))
        [NOBASe]                            ///  coefplot: drop base/omitted factor levels (b. and o. prefixes)
        [CONS]                              ///  coefplot: force show _cons even when nocons is default (v3.6.0)
        [EForm]                             ///  coefplot: exponentiate coefficients (odds ratios, hazard ratios) (v3.6.0)
        [NOCI]                              ///  coefplot/eventstudy: suppress confidence intervals entirely (v3.6.0)
        [REScale(real 1)]                   ///  coefplot: multiply all coefficients/CIs by factor, e.g. rescale(1000) (v3.6.0)
        [COEFSort(string)]                  ///  coefplot: sort coefficients: coefsort(value|abs|pval|se|desc) (v3.6.0)
        [ORDer(string)]                     ///  coefplot: custom coefficient display order, e.g. order(weight mpg foreign) (v3.6.0)
        [COEFSTyle(string)]                 ///  coefplot/eventstudy: scatter (default) or bar
        [CISTyle(string)]                   ///  coefplot/eventstudy/marginsplot: whisker | band | bar
        [POINTSTYLes(string)]               ///  per-model/group marker shapes space-sep: pointstyles(circle triangle)
        /// - Session 6 visual control (v3.6.0-s6) -
        [REFVal(string)]                    ///  coefplot: reference line value or "none": refval(1) or refval(none)
        [REFLINEWidth(string)]              ///  coefplot/eventstudy/marginsplot: reference-line width px (default 1): reflinewidth(2) (t2j fix8 A13)
        [ESTLabels(string)]                 ///  coefplot: custom model labels tilde-sep: estlabels(OLS~2SLS~GMM)
        [CICOLors(string)]                  ///  coefplot: CI colors pipe-sep per model; eventstudy: cicolors(pre|post) (t2e)
        [CIWidth(string)]                   ///  coefplot: CI line/band thickness px (default 1.5): ciwidth(2)
        [CONNECTed]                         ///  coefplot/eventstudy: draw connecting line through point estimates
        [REFPeriod(string)]                 ///  eventstudy: reference (omitted) period: auto (default) | none | # (t2f)
        [TOGether]                          ///  eventstudy: join the post series to the reference period (default in estimation mode) (t2f)
        [SEParate]                          ///  eventstudy: leads and lags as separate series (default with matrix()) (t2f fix 2)
        [PEXLine(string)]                   ///  coefplot/eventstudy: vertical reference line at x-value e.g. pexline(0) (v3.6.0-s7d)
        [LEVEL(string)]                     ///  = cilevel()  (Stata universal CI level; full name to avoid a levels() clash)
        [KEEP(string)]                      ///  = show()     (coefplot, Jann)
        [DROP(string)]                      ///  = omit()     (coefplot, Jann)
        [VERTical]                          ///  coefplot: vertical layout (Jann); eventstudy default
        [RECASTCI(string)]                  ///  = cistyle(): rarea|rband->area, rcap->whisker, rbar->bar, rline->band (marginsplot)
        [RECAST(string)]                    ///  connected|line -> connected (marginsplot)
        [MSYMbol(string)]                   ///  = pointstyle(): O circle, D diamond, T triangle, S square, X cross, + plus (twoway)
        [MSIZE(string)]                     ///  = pointsize(): vtiny..vhuge or px (twoway)
        [MCOLor(string asis)]               ///  = colors() (twoway)
        [LWidth(string)]                    ///  = linewidth(): vthin..vthick or px (twoway)
        [NOLABel]                           ///  = novaluelabels (graph bar)
        [LEVels(string)]                    ///  coefplot/eventstudy: two nested CI levels e.g. levels(90 95), inner first (v3.6.0-s7d)
        [NOTIMEStamp]                       ///  suppress timestamp subtitle line (v3.6.0-s7d)
        [COLLAPSEstats]                     ///  stats panel starts collapsed on load (v3.6.0-s7d)
        [NOALLFilter]                       ///  suppress All option from filter() dropdowns (v3.6.0-s7d)
        /// - Session 6c: reproducible table row options -
        [INDicators(string)]                ///  table export: indicator rows "label/v1 v2..." entries
        [ADDStats(string)]                  ///  table export: additional stat rows, keywords or "label/v1 v2..."
        /// - t2h batch 2a: publication-table controls (parsed by sparkta_table_opts -> arg 209) -
        [STARS(numlist max=3 >0 <1)]        ///  table: significance thresholds high->low, e.g. stars(0.10 0.05 0.01) (v3.6.0-t2h)
        [NOSTARS]                           ///  table: suppress significance stars (v3.6.0-t2h)
        [TStat]                             ///  table: show t/z beneath the estimate (v3.6.0-t2h)
        [INTerval]                          ///  table: show the confidence interval beneath the estimate (v3.6.0-t2h; ci() is the matrix-mode option)
        [NOFOoter]                          ///  table: drop the N / R-squared / star-legend footer (v3.6.0-t2h)
        [NOTABle]                           ///  table: suppress the publication table entirely (v3.6.0-t2h)
        /// - Coefficient label overrides -
        [COEFLABels(string)]                //   coefplot: custom coef labels "varname/Display Label" entries

    // v3.6.0-t1b: sparkta, closebrowser -- quit the headless browser kept alive by saveas().
    // Handled before any varlist/type checks; the jar is located the same way as later.
    if "`closebrowser'" != "" {
        capture findfile sparkta.jar
        if _rc {
            display as error "sparkta.jar not found on the adopath"
            exit 601
        }
        capture noisily javacall com.dashboard_test.tools.HeadlessBrowser execute, classpath("`r(fn)'") args("close")
        display as text "  session browser closed"
        exit
    }
    // v3.6.0-s9s: sparkta, findbrowser -- diagnostic for saveas(); no chart is built
    if "`findbrowser'" != "" {
        sparkta_find_browser, verbose
        global SPARKTA_BROWSER_FOUND `"`r(browser)'"'   // handed to the caller (sparkta is not rclass, by design: it must not clear r(table))
        if `"`r(browser)'"' != "" display as result `"  saveas() will use: `r(browser)'"'
        else display as error "  no Chromium-based browser found -- set: global SPARKTA_BROWSER \"C:\\path\\to\\msedge.exe\""
        exit
    }

    // ---- v3.6.0-s8s: resolve Stata-convention aliases into the sparkta names ----
    // Rule: alias and primary both given -> error; alias only -> copy; values mapped.
    foreach _pair in "level cilevel" "keep show" "drop omit" "mcolor colors" {
        local _al : word 1 of `_pair'
        local _pr : word 2 of `_pair'
        if `"``_al''"' != "" {
            if `"``_pr''"' != "" {
                display as error "`_al'() and `_pr'() are the same option -- specify only one"
                exit 198
            }
            local `_pr' `"``_al''"'
        }
    }
    // sort(): existing sparkta option (sortgroups, abbreviates to sort) on data charts;
    // on post-estimation charts it is Jann's sort(): asc|b|v|value, desc, p|pval, se|t, abs
    if "`sortgroups'" != "" & inlist("`type'", "coefplot", "eventstudy", "marginsplot") {
        if "`coefsort'" != "" {
            display as error "sort() and coefsort() are the same option on post-estimation charts -- specify only one"
            exit 198
        }
        local _sg = lower("`sortgroups'")
        if      inlist("`_sg'", "asc", "b", "v", "value") local coefsort "value"
        else if inlist("`_sg'", "desc")                   local coefsort "desc"
        else if inlist("`_sg'", "p", "pval")              local coefsort "pval"
        else if inlist("`_sg'", "se", "t")                local coefsort "se"
        else if inlist("`_sg'", "abs")                    local coefsort "abs"
        else {
            display as error "sort(): on post-estimation charts expected asc|desc|b|p|se|abs -- got `sortgroups'"
            exit 198
        }
        local sortgroups ""
    }
    if "`vertical'" != "" {
        if "`horizontal'" != "" {
            display as error "vertical and horizontal cannot both be specified"
            exit 198
        }
        local _vertical 1
    }
    else local _vertical 0
    if "`nolabel'" != "" local novaluelabels "novaluelabels"
    if "`recastci'" != "" {
        if "`cistyle'" != "" {
            display as error "recastci() and cistyle() are the same option -- specify only one"
            exit 198
        }
        local _rc_map ""
        foreach _rct of local recastci {
            local _rct = lower("`_rct'")
            if inlist("`_rct'", "rarea", "rband", "area")   local _rc_map "`_rc_map' area"
            else if inlist("`_rct'", "rcap", "rcapsym", "whisker") local _rc_map "`_rc_map' whisker"
            else if inlist("`_rct'", "rbar", "bar")         local _rc_map "`_rc_map' bar"
            else if inlist("`_rct'", "rline", "rspike", "band")  local _rc_map "`_rc_map' band"
            else {
                display as error "recastci(): expected rarea | rcap | rbar | rline (or area | whisker | bar | band) -- got `_rct'"
                exit 198
            }
        }
        local cistyle = trim("`_rc_map'")
    }
    if "`recast'" != "" {
        local _rcs = lower("`recast'")
        if inlist("`_rcs'", "connected", "line", "scatter") {
            if "`_rcs'" != "scatter" local connected "connected"
        }
        else {
            display as error "recast(): expected connected | line | scatter -- got `recast'"
            exit 198
        }
    }
    if "`msymbol'" != "" {
        if "`pointstyle'" != "" {
            display as error "msymbol() and pointstyle() are the same option -- specify only one"
            exit 198
        }
        local _ms = lower("`msymbol'")
        if      inlist("`_ms'", "o", "oh", "circle", "smcircle")   local pointstyle "circle"
        else if inlist("`_ms'", "d", "dh", "diamond", "smdiamond") local pointstyle "diamond"
        else if inlist("`_ms'", "t", "th", "triangle", "smtriangle") local pointstyle "triangle"
        else if inlist("`_ms'", "s", "sh", "square", "smsquare")   local pointstyle "rect"
        else if inlist("`_ms'", "x", "smx", "cross")                local pointstyle "cross"
        else if inlist("`_ms'", "+", "smplus", "plus")              local pointstyle "plus"
        else if inlist("`_ms'", "p", "point", "dot")                local pointstyle "circle"
        else if inlist("`_ms'", "i", "none", "invisible")           local nopoints "nopoints"
        else {
            display as error "msymbol(): expected O D T S X + p i (Stata symbols; hollow/sm variants accepted) -- got `msymbol'"
            exit 198
        }
    }
    if "`msize'" != "" {
        if "`pointsize'" != "" {
            display as error "msize() and pointsize() are the same option -- specify only one"
            exit 198
        }
        local _msz = lower("`msize'")
        if      "`_msz'" == "vtiny"   local pointsize "1.5"
        else if "`_msz'" == "tiny"    local pointsize "2.5"
        else if "`_msz'" == "vsmall"  local pointsize "3"
        else if "`_msz'" == "small"   local pointsize "3.5"
        else if "`_msz'" == "medsmall" local pointsize "4"
        else if "`_msz'" == "medium"  local pointsize "5"
        else if "`_msz'" == "medlarge" local pointsize "6"
        else if "`_msz'" == "large"   local pointsize "7.5"
        else if "`_msz'" == "vlarge"  local pointsize "9"
        else if "`_msz'" == "huge"    local pointsize "11"
        else if "`_msz'" == "vhuge"   local pointsize "14"
        else local pointsize "`msize'"     // a number = px
    }
    if "`lwidth'" != "" {
        if "`linewidth'" != "" {
            display as error "lwidth() and linewidth() are the same option -- specify only one"
            exit 198
        }
        local _lw = lower("`lwidth'")
        if      inlist("`_lw'", "vvthin", "vthin") local linewidth "0.5"
        else if "`_lw'" == "thin"    local linewidth "1"
        else if "`_lw'" == "medthin" local linewidth "1.5"
        else if "`_lw'" == "medium"  local linewidth "2"
        else if "`_lw'" == "medthick" local linewidth "2.5"
        else if "`_lw'" == "thick"   local linewidth "3.5"
        else if inlist("`_lw'", "vthick", "vvthick") local linewidth "5"
        else local linewidth "`lwidth'"      // a number = px
    }

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
    local valid_types "bar line scatter pie hbar stackedbar stackedhbar stackedarea area bubble donut stackedline cibar ciline histogram stackedbar100 stackedhbar100 boxplot violin hboxplot hviolinplot coefplot eventstudy marginsplot"
    if !`:list type in valid_types' {
        display as error "Invalid type: `type'"
        display as error "Valid: bar line scatter pie hbar stackedbar stackedhbar area bubble"
        display as error "       donut stackedarea stackedline stackedbar100 stackedhbar100"
        display as error "       cibar ciline histogram boxplot hbox violin hviolin"
        display as error "       coefplot eventstudy marginsplot"
        exit 198
    }
    // t2j fix8p (C2): a stacked type with a single variable has nothing to stack -- it draws
    // as a regular bar/line (this matches Stata's graph bar). Warn so the user is not surprised.
    if inlist("`type'","stackedbar","stackedhbar","stackedarea","stackedline","stackedbar100","stackedhbar100") {
        local _spk_nstk : word count `varlist'
        if `_spk_nstk' == 1 {
            display as text "  note: type(`type') stacks 2 or more variables; a single variable has nothing to stack, so it draws as a regular bar/line. Pass 2+ variables to stack."
        }
        // t2j fix9d (decision 1b, 2026-09-13): a 100% stack needs over() groups to share out --
        // without over() there is nothing to normalise, so it draws plain mean bars of the
        // variables (unchanged behaviour; the note explains what the user sees).
        else if inlist("`type'","stackedbar100","stackedhbar100") & `"`over'"' == "" {
            display as text "  note: type(`type') without over() has no groups to share out -- it draws plain bars of the variables' means. Add over(groupvar) for a 100% stack."
        }
    }
    // (C3 marginsplot over() is already warned in the marginsplot read path -- t2j "B3".)
    // v3.6.0: classify post-estimation types (no data scan, read matrices only)
    local _is_postest 0
    // t2g: keep the margins results NOW -- palette(), the colour normaliser and results() all run
    //      r-class code; sparkta_margins_repost re-posts them right before the reader.
    if "`type'" == "marginsplot" sparkta_margins_keep
    // t2f fix 4: results() = a dataset/frame of estimates -> matrices -> the matrix() path below
    local _results_src ""
    if `"`results'"' != "" {
        if `"`matrix'"' != "" {
            display as error "results() and matrix() cannot be combined"
            exit 198
        }
        sparkta_read_results, spec(`results') type(`type')
    }
    // v3.6.0-t2a matrix-mode guards + multi-matrix layout: moved to sparkta_matrix_guards.ado (t2g,
    //   main-program headroom); values in, c_local back: matrix estlabels estnames _mat_list _mat_multi
    sparkta_matrix_guards, type(`type') matrix(`matrix') plotlabels(`plotlabels') estlabels(`estlabels') ///
        estnames(`estnames') ci(`ci') se(`se') df(`df') pvalue(`pvalue') results(`_results_src')
    if inlist("`type'", "coefplot", "eventstudy", "marginsplot") {
        local _is_postest 1
    }
    // t2j fix9d (decision 4a, 2026-09-13): by() / filters() / sliders() act on the data in
    // memory, but a post-estimation chart reads estimation results and never touches the
    // data -- they were silently ignored (rc 0, a plain chart). Say so instead of surprising
    // the user; the chart is still drawn (a template reused across chart types must not stop).
    if `_is_postest' {
        local _spk_ign ""
        if `"`by'"' != ""      local _spk_ign "`_spk_ign' by()"
        if `"`filters'"' != "" local _spk_ign "`_spk_ign' filters()"
        if `"`sliders'"' != "" local _spk_ign "`_spk_ign' sliders()"
        if "`_spk_ign'" != "" {
            display as text "  note: `=trim("`_spk_ign'")' ignored -- type(`type') draws estimation results, not the data in memory, so there is nothing to panel or filter"
        }
    }
    // v3.6.0-t2b: post-estimation charts take no varlist (r_err_marginsplot_with_varlist only failed
    // by accident before: no r(table) happened to be in memory)
    if `_is_postest' & "`varlist'" != "" {
        display as error "type(`type') does not take a varlist -- it reads estimation results (e(b)/e(V), r(table)) or matrix()"
        exit 198
    }

    // - over/by: parse varname and optional showmissing suboption (v1.9.1) -
    // Syntax allows over(varname [, showmissing]) so we received a string.
    // Parse each with local syntax to extract varname and flag separately.
    local sm_over    "0"
    local sm_by      "0"
    local sm_filters "0"  // single showmissing flag for filters()
    // v1.9.1: parse over/by/filter/filter2 strings for optional , showmissing
    // Uses tokenize so we never need compound quotes inside function calls.
    foreach slot in over by {
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
    // - filters() -- unlimited filter variables (F-0b) -
    // Accepts a space-separated varlist optionally ending in , showmissing.
    // Each varname is validated and stored pipe-separated in `_filters_list'.
    local _filters_list ""
    if "`filters'" != "" {
        // Strip optional trailing , showmissing
        local _fraw = subinstr("`filters'", ",", " ", .)
        local _fraw = itrim("`_fraw'")
        // Check for showmissing keyword at end
        local _lastw = word("`_fraw'", wordcount("`_fraw'"))
        if lower("`_lastw'") == "showmissing" | lower("`_lastw'") == "sm" {
            local sm_filters "1"
            local _fraw = substr("`_fraw'", 1, length("`_fraw'") - length("`_lastw'") - 1)
            local _fraw = itrim("`_fraw'")
        }
        // Validate each varname and build pipe list
        local _nfv = wordcount("`_fraw'")
        if `_nfv' == 0 {
            display as error "filters(): no variable names found"
            exit 198
        }
        if `_nfv' > 10 {
            display as error "filters(): maximum 10 filter variables"
            exit 198
        }
        forvalues _fi = 1/`_nfv' {
            local _fv = word("`_fraw'", `_fi')
            capture confirm variable `_fv'
            if _rc != 0 {
                display as error "filters(): variable not found: `_fv'"
                exit 198
            }
            // Cardinality check (same 500-cat ceiling as filter()/filter2())
            quietly levelsof `_fv', local(_flevels)
            local _nlevels : word count `_flevels'
            if `_nlevels' > 500 {
                display as error "filters(): variable `_fv' has `_nlevels' unique values (max 500)"
                exit 198
            }
            if "`_filters_list'" == "" local _filters_list "`_fv'"
            else                        local _filters_list "`_filters_list'|`_fv'"
        }
    }

    // - sliders() -- dual-handle numeric range sliders (F-1) -
    // Accepts a space-separated varlist of NUMERIC variables only.
    // Stored pipe-separated in `_sliders_list' for Java arg 151.
    local _sliders_list ""
    if "`sliders'" != "" {
        local _sraw = itrim("`sliders'")
        local _nsv = wordcount("`_sraw'")
        if `_nsv' == 0 {
            display as error "sliders(): no variable names found"
            exit 198
        }
        if `_nsv' > 10 {
            display as error "sliders(): maximum 10 slider variables"
            exit 198
        }
        forvalues _si = 1/`_nsv' {
            local _sv = word("`_sraw'", `_si')
            capture confirm numeric variable `_sv'
            if _rc != 0 {
                display as error "sliders(): variable not found or not numeric: `_sv'"
                display as error "  sliders() only accepts numeric variables"
                exit 198
            }
            if "`_sliders_list'" == "" local _sliders_list "`_sv'"
            else                       local _sliders_list "`_sliders_list'|`_sv'"
        }
    }

    // -- mlabel(): scatter marker labels ----------------------------------------
    // Syntax: mlabel(varname) or mlabel(varname, all)
    // Parses varname and optional ", all" suboption (same pattern as over(,showmissing)).
    // _mlabel_var: the label variable name (empty if not specified)
    // _mlabel_all: 1 if ", all" suboption given (show all labels regardless of N)
    local _mlabel_var ""
    local _mlabel_all "0"
    if "`mlabel'" != "" {
        // Strip optional comma so "varname, all" and "varname all" both work
        local _mltmp = itrim(subinstr("`mlabel'", ",", " ", .))
        local _mlabel_var = word("`_mltmp'", 1)
        local _mlword2    = lower(word("`_mltmp'", 2))
        if "`_mlword2'" == "all" local _mlabel_all "1"
        else if "`_mlword2'" != "" {
            display as error "mlabel(): unrecognised suboption: `_mlword2'"
            display as error "  Valid suboption: all"
            exit 198
        }
        // Validate that the label variable exists
        capture confirm variable `_mlabel_var'
        if _rc != 0 {
            display as error "mlabel(): variable not found: `_mlabel_var'"
            exit 198
        }
        // Only meaningful for scatter/bubble
        if "`type'" != "scatter" & "`type'" != "bubble" {
            display as error "mlabel() is only supported for type(scatter) and type(bubble)"
            exit 198
        }
    }

    // -- mlabpos(): label minute-clock position for all scatter marker labels -----
    // Accepts integer 0-59 matching internal minute-clock convention (same as alabelpos).
    // 0=center, 15=right, 30=below, 45=left, approaching 60=above.
    // Default: 45 (above, matching alabelpos default of 15 rotated to top).
    // Mirrors Stata mlabposition() but uses 0-59 for consistency with alabelpos().
    local _mlabpos ""
    if "`mlabpos'" != "" {
        capture confirm integer number `mlabpos'
        if _rc != 0 {
            display as error "mlabpos(): must be an integer between 0 and 59"
            exit 198
        }
        if `mlabpos' < 0 | `mlabpos' > 59 {
            display as error "mlabpos(): must be between 0 and 59 (minute-clock position)"
            display as error "  0=center, 15=right, 30=below, 45=left, 60/0=above"
            exit 198
        }
        local _mlabpos "`mlabpos'"
    }

    // -- mlabvposition(): per-observation minute-clock position variable ---------
    // Syntax: mlabvposition(varname) -- numeric variable with values 0-59 per row.
    // Each point gets its label at the minute-clock position from this variable.
    // 0=center, 15=right, 30=below, 45=left. Requires mlabel() to be set.
    local _mlabvpos ""
    if "`mlabvposition'" != "" {
        if "`mlabel'" == "" {
            display as error "mlabvposition() requires mlabel() to also be specified"
            exit 198
        }
        if "`type'" != "scatter" & "`type'" != "bubble" {
            display as error "mlabvposition() is only supported for type(scatter) and type(bubble)"
            exit 198
        }
        capture confirm numeric variable `mlabvposition'
        if _rc != 0 {
            display as error "mlabvposition(): variable not found or not numeric: `mlabvposition'"
            exit 198
        }
        local _mlabvpos "`mlabvposition'"
    }

    // -- palette(): resolve a colorpalette scheme into colors() -------------------
    // v3.6.0-s8c. Requires colorpalette by Ben Jann (ssc install colorpalette).
    // The full option string is handed to colorpalette, so any scheme/option
    // colorpalette understands works here: palette(okabe), palette(viridis, n(6)),
    // palette(tableau, reverse), palette(HCL blues, n(5) ipolate(8)) ...
    // colorpalette returns r(p1)..r(pN) as "r g b" strings -> converted to #rrggbb
    // and stored in `colors' (arg 15), which overrides theme() palettes in Java.
    if `"`palette'"' != "" {
        if `"`colors'"' != "" {
            display as error "palette() and colors() cannot be combined"
            exit 198
        }
        capture which colorpalette
        if _rc != 0 {
            display as error "palette() requires the colorpalette package by Ben Jann"
            display as error "  install with:  ssc install colorpalette, replace"
            exit 198
        }
        local _palspec `"`palette'"'
        if strpos(`"`_palspec'"', ",") == 0 local _palspec `"`_palspec', nograph"'
        else                                local _palspec `"`_palspec' nograph"'
        capture noisily colorpalette `_palspec'
        if _rc != 0 {
            display as error `"palette(): colorpalette could not resolve "`palette'""'
            exit 198
        }
        local _paln = r(n)
        local _palhex ""
        forvalues _pi = 1/`_paln' {
            local _pc `"`r(p`_pi')'"'
            // strip any opacity suffix ("%50") or intensity ("*0.8") colorpalette may attach
            local _pc = regexr(`"`_pc'"', "[%*].*$", "")
            local _pc = trim(itrim(`"`_pc'"'))
            if regexm(`"`_pc'"', "^([0-9]+) ([0-9]+) ([0-9]+)$") {
                local _hx "#"
                forvalues _ch = 1/3 {
                    local _cv = min(255, max(0, round(real(regexs(`_ch')))))
                    local _hx "`_hx'`=substr("0123456789abcdef", floor(`_cv'/16)+1, 1)'`=substr("0123456789abcdef", mod(`_cv',16)+1, 1)'"
                }
            }
            else if regexm(`"`_pc'"', "^#?[0-9A-Fa-f]{6}$") {
                local _hx = "#" + subinstr(`"`_pc'"', "#", "", .)
            }
            else local _hx `"`_pc'"'   // named colour -- Java toRgba() handles CSS names
            local _palhex "`_palhex' `_hx'"
        }
        local colors = trim("`_palhex'")
        display as text "  palette(`palette'): `_paln' colours from colorpalette"
    }

    // -- v3.6.0-t2e: colour normalisation -- every colour option accepts what a Stata
    //    user would write: colorpalette triplets ("230 159 0"), Stata names (navy, gs10,
    //    cranberry -- help colorstyle), hex with/without #, rgb()/rgba(), and the
    //    suffixes name%50 (opacity) and name*0.6 (intensity). Java only ever sees
    //    #rrggbb, rgba(r,g,b,a) or an untouched CSS name. See sparkta_color_list.
    //    (t2f fix 4: the loops live in sparkta_color_opts.ado -- values in, c_local back. fix 5: pass
    //     opt(`opt') -- NOT opt(`"`opt'"') -- the component uses string asis, which keeps quotes, so a
    //     wrapped empty value arrived as "" and every case ran the rclass normaliser, wiping r())
    sparkta_color_opts, colors(`colors') cicolors(`cicolors') ylinecolor(`ylinecolor') ///
        xlinecolor(`xlinecolor') ybandcolor(`ybandcolor') xbandcolor(`xbandcolor') ///
        apointcolor(`apointcolor') aellipsecolor(`aellipsecolor') aellipseborder(`aellipseborder') ///
        gradcolors(`gradcolors') bgcolor(`bgcolor') plotcolor(`plotcolor') ///
        gridcolor(`gridcolor') mediancolor(`mediancolor') meancolor(`meancolor') ///
        titlecolor(`titlecolor') subtitlecolor(`subtitlecolor') xtitlecolor(`xtitlecolor') ///
        ytitlecolor(`ytitlecolor') xlabcolor(`xlabcolor') ylabcolor(`ylabcolor') ///
        legcolor(`legcolor') legbgcolor(`legbgcolor') tooltipbg(`tooltipbg') ///
        tooltipborder(`tooltipborder')

    // -- t2f: event-study reference-period options -> arg 208 (validated for every type).
    //    fix 1 (run 42): the local was re-declared empty later in the post-estimation block,
    //    so Java always saw the defaults -- declared ONCE, here, and never again.
    local _es_opts ""
    sparkta_es_opts, type(`type') refperiod(`refperiod') `together' `separate' matrix(`matrix')

    // -- t2h (batch 2a): publication-table options -> arg 209 (validated for every type).
    //    Declared ONCE here, never re-initialised downstream (the c_local memory rule).
    local _tbl_opts ""
    sparkta_table_opts, stars(`stars') `nostars' `tstat' `interval' `nofooter' `notable'

    // -- fit(): scatter fit line -------------------------------------------------
    // fitci adds CI band (lfit and qfit only).
    local _fit ""
    if "`fit'" != "" {
        local _fit = lower(itrim("`fit'"))
        local _valid_fits "lfit qfit lowess exp log power ma"
        local _fit_ok 0
        foreach _vf of local _valid_fits {
            if "`_fit'" == "`_vf'" local _fit_ok 1
        }
        if `_fit_ok' == 0 {
            display as error "fit(): unrecognised fit type: `_fit'"
            display as error "  Valid types: lfit qfit lowess exp log power ma"
            exit 198
        }
        if "`type'" != "scatter" & "`type'" != "bubble" {
            display as error "fit() is only supported for type(scatter) and type(bubble)"
            exit 198
        }
        // fitci valid with lfit, qfit, exp, log, power (all linearised OLS models)
        local _fitci_ok = inlist("`_fit'", "lfit", "qfit", "exp", "log", "power")
        if "`fitci'" != "" & !`_fitci_ok' {
            display as error "fitci is only supported with fit(lfit), fit(qfit), fit(exp), fit(log), fit(power)"
            exit 198
        }

    }
    else if "`fitci'" != "" {
        display as error "fitci requires fit(lfit), fit(qfit), fit(exp), fit(log), or fit(power) to also be specified"
        exit 198
    }
    local _fitci "0"
    if "`fitci'" != "" local _fitci "1"

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
    // v3.6.0-t2i (hardening): rounding can push a value in (99,100) up to 100,
    // which is an invalid CI level (alpha 0 -> infinite critical value). Re-check
    // the ROUNDED value and reject (Astra: cilevel(99.9) passed then became 100).
    if real("`cilevel'") >= 100 | real("`cilevel'") <= 0 {
        display as error "cilevel() must round to an integer between 1 and 99 (e.g. 90, 95, 99)"
        exit 198
    }

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
    // v3.6.0-t1k: SELF-CONTAINED IS THE DEFAULT. A sparkta page is shared and opened
    // later, anywhere; it must not go blank without a network. online = CDN scripts
    // (smaller file). saveas() always needs the embedded libraries for the headless render.
    if "`online'" != "" & "`offline'" != "" {
        display as error "online and offline cannot both be specified"
        exit 198
    }
    local is_offline "1"
    if "`online'" != "" local is_offline "0"
    if `"`saveas'"' != "" & "`online'" != "" {
        local is_offline "1"
        local _saveas_forced_offline 1
    }
    // t2j fix8w (U21): the export toolbar (PNG / SVG / PDF) is ON by default; notoolbar hides it.
    // download is kept as a no-op alias for old scripts; saveas() always needs the page exporter.
    local is_download "1"
    if "`notoolbar'" != "" local is_download "0"
    if `"`saveas'"' != "" local is_download "1"   // v3.6.0-s9w: headless PDF/SVG need the page's exporter (canvas2svg + _spkChartToSvg)
    // - Phase 1-C axis utilities (v3.0.3) -
    local is_yreverse "0"
    if "`yreverse'" != "" local is_yreverse "1"
    local is_xreverse "0"
    if "`xreverse'" != "" local is_xreverse "1"
    local is_noticks "0"
    if "`noticks'" != "" local is_noticks "1"
    // ygrace: accept "5%%" or plain number; pass through as-is
    if "`ygrace'" == "" local ygrace ""
    // v3.6.0-s8p: plotmargin() -- Stata plotregion(margin()) grammar in PERCENT of the
    // data range: 1 value = all sides; 2 = x y; 4 = l r b t. Stored as "l r b t".
    // The y part also feeds ygrace() for data charts when ygrace() is not given.
    local _plotmargin ""
    if "`plotmargin'" != "" {
        local _pm = itrim(trim(subinstr("`plotmargin'", "%", "", .)))
        local _pmn : word count `_pm'
        foreach _pv of local _pm {
            capture confirm number `_pv'
            if _rc != 0 | `_pv' < -50 | `_pv' > 100 {
                display as error "plotmargin(): values must be percentages between -50 and 100 -- got '`plotmargin''"
                exit 198
            }
        }
        if `_pmn' == 1      local _plotmargin "`_pm' `_pm' `_pm' `_pm'"
        else if `_pmn' == 2 {
            local _pm1 : word 1 of `_pm'
            local _pm2 : word 2 of `_pm'
            local _plotmargin "`_pm1' `_pm1' `_pm2' `_pm2'"
        }
        else if `_pmn' == 4 local _plotmargin "`_pm'"
        else {
            display as error "plotmargin(): give 1 value (all), 2 (x y) or 4 (l r b t)"
            exit 198
        }
        // negative = crop into the data (Stata allows it). Post-estimation ranges
        // are computed here so we can honour it; Chart.js grace on data charts cannot.
        local _pm_neg 0
        foreach _pv of local _plotmargin {
            if `_pv' < 0 local _pm_neg 1
        }
        if `_pm_neg' & !inlist("`type'", "coefplot", "eventstudy", "marginsplot") {
            display as error "plotmargin(): negative values (cropping) are supported for coefplot, eventstudy and marginsplot only"
            exit 198
        }
        if `_pm_neg' display as text "  plotmargin(): negative margin -- data beyond the axis limits will be cropped"
        if "`ygrace'" == "" {
            local _pmb : word 3 of `_plotmargin'
            local ygrace "`_pmb'%"
        }
    }
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
    // v3.6.0-s8b: be forgiving -- lpatterns(solid dash) is a common slip; normalise to pipes
    if "`lpatterns'" != "" & strpos("`lpatterns'", "|") == 0 local lpatterns = subinstr(trim(itrim("`lpatterns'")), " ", "|", .)
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
    // A2 (t2j): yband/xband/apoint coordinates flow straight into the chart's
    // annotation config as JS numbers. Validate every coordinate token so a
    // non-numeric value is rejected here instead of emitting malformed JS.
    // yband/xband: "lo hi" per entry, pipe-separated (e.g. yband(20 40|60 80)).
    if `"`yband'"' != "" {
        local _ybtoks = subinstr(`"`yband'"', "|", " ", .)
        foreach _tok of local _ybtoks {
            capture confirm number `_tok'
            if _rc {
                di as err "yband(): '`_tok'' is not a valid number. Use 'lo hi' numeric pairs, e.g. yband(20 40) or yband(20 40|60 80)"
                exit 198
            }
        }
    }
    if `"`xband'"' != "" {
        local _xbtoks = subinstr(`"`xband'"', "|", " ", .)
        foreach _tok of local _xbtoks {
            capture confirm number `_tok'
            if _rc {
                di as err "xband(): '`_tok'' is not a valid number. Use 'lo hi' numeric pairs, e.g. xband(1 3) or xband(1 3|5 7)"
                exit 198
            }
        }
    }
    // apoint: space-separated "y x" coordinate pairs (e.g. apoint(15000 3 20000 4)).
    if `"`apoint'"' != "" {
        foreach _tok of local apoint {
            capture confirm number `_tok'
            if _rc {
                di as err "apoint(): '`_tok'' is not a valid number. Use space-separated 'y x' pairs, e.g. apoint(15000 3 20000 4)"
                exit 198
            }
        }
    }
    // apointsize/alabelfs: must be positive numbers if supplied
    // Use real() for validation -- confirm number can fail on string-typed options (v3.5.2)
    if `"`apointsize'"' != "" {
        local _apsz = real(`"`apointsize'"')
        if missing(`_apsz') | `_apsz' <= 0 {
            di as err "apointsize(): must be a positive number (px), e.g. apointsize(10)"
            exit 198
        }
    }
    if `"`alabelfs'"' != "" {
        local _alfs = real(`"`alabelfs'"')
        if missing(`_alfs') | `_alfs' <= 0 {
            di as err "alabelfs(): must be a positive number (px), e.g. alabelfs(12)"
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

    // - mixed-magnitude note (t2j fix8) -
    // When two or more variables are plotted on one shared y-axis and their typical
    // magnitudes differ by a large factor, the smaller series renders nearly flat.
    // A secondary right axis (y2()) already exists; we keep it opt-in (matching
    // Stata's explicit yaxis(2) philosophy) but point it out here rather than let
    // the user puzzle over a flat line. No data is rescaled. Only fires for the
    // shared-axis chart families, when y2() is not already used.
    if "`y2'" == "" & inlist("`type'","line","area","bar","hbar") {
        local _mm_nv : word count `varlist'
        if `_mm_nv' >= 2 {
            local _mm_min = .
            local _mm_max = 0
            foreach _mm_yv of local varlist {
                capture confirm numeric variable `_mm_yv'
                if !_rc {
                    quietly summarize `_mm_yv', detail
                    local _mm_md = abs(r(p50))
                    if `_mm_md' > 0 & `_mm_md' < . {
                        if `_mm_md' < `_mm_min' local _mm_min = `_mm_md'
                        if `_mm_md' > `_mm_max' local _mm_max = `_mm_md'
                    }
                }
            }
            if `_mm_min' < . & `_mm_min' > 0 & `_mm_max'/`_mm_min' > 50 {
                local _mm_ratio = round(`_mm_max'/`_mm_min')
                display as text "Note: the plotted variables differ widely in typical magnitude (about `_mm_ratio'x). The smaller series may render nearly flat on a shared y-axis. To put a variable on a secondary right axis, add y2(varname)."
            }
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

    // - filter / filter2 / filters -
    // Validation and cardinality checks are handled above in the filters() block.
    // _filters_list now holds the canonical pipe-separated list of filter varnames.
    // (filter()/filter2() back-compat merges into _filters_list above.)

    // - layout -
    // t2j fix9f: an EMPTY layout() is passed through (Java still draws it vertical on screen)
    // so exports can tell "default" from an explicit layout(vertical): the default exports as
    // a two-across grid (fix9e), an explicit layout(vertical) exports stacked, as on screen.
    local layout = lower("`layout'")
    if "`layout'" != "" & "`layout'" != "vertical" & "`layout'" != "horizontal" & "`layout'" != "grid" {
        display as error "layout() accepts: vertical horizontal grid"
        exit 198
    }
    if "`layout'" != "" & "`layout'" != "vertical" & "`by'" == "" {
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
        // X2 (t2j): a true time scale needs a Chart.js date adapter, which is not
        // bundled (the page is strictly self-contained). Warn and fall back to a
        // category axis: pre-format your dates and pass them as labels, or plot the
        // numeric date (e.g. daily/monthly) on a linear axis with xlabels().
        if "`xtype'" == "time" {
            display as error "  note: xtype(time) is not supported (no date adapter is bundled); using a category axis. Format dates first and pass them via the x labels, or use a linear axis."
        }
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
        // X3 (t2j): the range values flow straight into the chart's scale min/max as
        // JS numbers, so a non-numeric value would emit malformed JS. Validate here.
        // NB Stata requires the opening brace to be the LAST token on its line -- a
        // one-line "if _rc { ... }" corrupts brace matching (t2j-fix1).
        capture confirm number `xrangemin'
        if _rc {
            display as error "xrange(): '`xrangemin'' is not a number (e.g. xrange(0 100))"
            exit 198
        }
        capture confirm number `xrangemax'
        if _rc {
            display as error "xrange(): '`xrangemax'' is not a number (e.g. xrange(0 100))"
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
        capture confirm number `yrangemin'
        if _rc {
            display as error "yrange(): '`yrangemin'' is not a number (e.g. yrange(0 50))"
            exit 198
        }
        capture confirm number `yrangemax'
        if _rc {
            display as error "yrange(): '`yrangemax'' is not a number (e.g. yrange(0 50))"
            exit 198
        }
    }

    // X3 (t2j): single-number axis/appearance options flow straight into the chart's
    // JS config. Reject non-numeric values here with a clear message rather than
    // letting them emit malformed JS. (Multi-value or unit-bearing options such as
    // padding, plotmargin and the grace options are validated in their own parsers.)
    foreach _no in xtickcount ytickcount xtickangle ytickangle xstepsize ystepsize aspect barwidth {
        if "``_no''" != "" {
            capture confirm number ``_no''
            if _rc {
                display as error "`_no'(): '``_no''' is not a number"
                exit 198
            }
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
        local valid_ps "circle cross dash line rect rectrounded star triangle diamond plus"
        if !`:list pointstyle in valid_ps' {
            display as error "pointstyle() accepts: circle cross dash line rect rectRounded star triangle diamond plus"
            exit 198
        }
        if "`pointstyle'" == "rectrounded" local pointstyle "rectRounded"
        if "`pointstyle'" == "diamond"     local pointstyle "rectRot"    // v3.6.0-s8s: Chart.js name for a diamond
        if "`pointstyle'" == "plus"        local pointstyle "crossRot"   // v3.6.0-s8s: Chart.js name for a plus
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

    // - pie/donut validation -- three modes matching Stata graph pie -
    // Mode 1: sparkta v1 v2 v3, type(pie)            multi-var, no over()
    // Mode 2: sparkta price, type(pie) over(rep78)    one var + over()
    // Mode 3: sparkta, type(pie) over(rep78)          no var, freq counts
    local nvar : word count `varlist'
    if ("`type'" == "pie" | "`type'" == "donut") {
        if `nvar' == 0 & "`over'" == "" {
            display as error "pie/donut requires variables or over(). Three modes:"
            display as error "  Mode 1 (var totals):  sparkta price mpg, type(pie)"
            display as error "  Mode 2 (group sums):  sparkta price, type(pie) over(rep78)"
            display as error "  Mode 3 (frequencies): sparkta, type(pie) over(rep78)"
            exit 198
        }
        if `nvar' > 1 & "`over'" != "" {
            display as error "pie/donut: cannot combine multiple variables with over()."
            display as error "  Multi-var (no over): sparkta price mpg, type(pie)"
            display as error "  One var + over:      sparkta price, type(pie) over(rep78)"
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
    // v3.6.0-s8f: informative default title instead of the literal "Sparkta"
    //   post-estimation: from the estimation / margins command
    //   data charts:     "<stat> of <var(s)> by <over>" from variable labels
    if "`title'" == "" {
        if "`type'" == "marginsplot" {
            capture local title `"`r(title)'"'
            if `"`title'"' == "" local title "Predictive margins"
        }
        else if inlist("`type'", "coefplot", "eventstudy") {
            local _t_cmd `"`e(cmd)'"'
            local _t_dv  `"`e(depvar)'"'
            if "`matrix'" != "" & `_mat_multi' local title "Coefficient comparison: `estnames'"
            else if "`_results_src'" != "" & "`type'" == "eventstudy" local title "Event study: `_results_src'"
            else if "`_results_src'" != "" local title "Estimates from `_results_src'"
            else if "`matrix'" != "" & "`type'" == "eventstudy" local title "Event study: `matrix'"
            else if "`matrix'" != "" local title "Estimates from `matrix'"
            else if "`estnames'" != "" local title "Coefficient comparison: `estnames'"
            else if "`type'" == "eventstudy" local title "Event study: `_t_dv'"
            else if `"`_t_dv'"' != "" local title "`_t_cmd': `_t_dv'"
            else local title "Coefficient plot"
        }
        else {
            local _t_vars ""
            foreach _tv of local varlist {
                local _tvl : variable label `_tv'
                if "`_tvl'" == "" local _tvl "`_tv'"
                local _t_vars "`_t_vars', `_tvl'"
            }
            local _t_vars = substr("`_t_vars'", 3, .)
            local _t_by ""
            if "`over'" != "" {
                local _t_by : variable label `over'
                if "`_t_by'" == "" local _t_by "`over'"
                local _t_by " by `_t_by'"
            }
            if "`_t_vars'" != "" local title "`_t_vars'`_t_by'"
            else if "`over'" != "" local title "Frequency`_t_by'"
            else local title "Sparkta"
        }
    }
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

    // - Post-estimation option validation (v3.6.0) ----------------------------
    // coefstyle(): scatter (default) or bar
    local _coefstyle ""
    if "`coefstyle'" != "" {
        local _coefstyle = lower(itrim("`coefstyle'"))
        if !inlist("`_coefstyle'", "scatter", "bar") {
            display as error "coefstyle(): invalid value '`coefstyle''"
            display as error "  Valid: scatter (default) | bar"
            exit 198
        }
    }
    // cistyle(): whisker (default for coefplot), band (default for marginsplot), bar (default for eventstudy)
    // cistyle(): one or two encodings. v3.6.0-s8m: a LIST composes encodings --
    //   cistyle(area whisker)  band for the level + whisker at the point
    //   cistyle(band bar)      (bar = capped whisker)
    // "+" is accepted as a separator (area+whisker). At most one band-type
    // (area|band) and one whisker-type (whisker|bar). Combined styles are
    // implemented for marginsplot; coefplot/eventstudy accept a single style.
    local _cistyle ""
    if "`cistyle'" != "" {
        local _cistyle = lower(itrim(trim(subinstr("`cistyle'", "+", " ", .))))
        local _cs_nband 0
        local _cs_nwhisk 0
        foreach _cst of local _cistyle {
            if !inlist("`_cst'", "whisker", "band", "bar", "area") {
                display as error "cistyle(): must be whisker | band | bar | area, or a pair such as cistyle(area whisker) -- got '`cistyle''"
                exit 198
            }
            if inlist("`_cst'", "area", "band")   local _cs_nband  = `_cs_nband'  + 1
            if inlist("`_cst'", "whisker", "bar") local _cs_nwhisk = `_cs_nwhisk' + 1
        }
        if `_cs_nband' > 1 | `_cs_nwhisk' > 1 {
            display as error "cistyle(): combine at most one of area|band with one of whisker|bar -- got '`cistyle''"
            exit 198
        }
        local _cs_n : word count `_cistyle'
        if `_cs_n' > 1 & "`type'" != "marginsplot" {
            display as error "cistyle(`cistyle'): combined styles are currently supported for type(marginsplot) only"
            exit 198
        }
    }
    // Set cistyle defaults per type if user did not specify
    if "`_cistyle'" == "" & "`type'" == "coefplot"    local _cistyle "whisker"
    if "`_cistyle'" == "" & "`type'" == "eventstudy"  local _cistyle "bar"
    if "`_cistyle'" == "" & "`type'" == "marginsplot" local _cistyle "whisker"
    // Set coefstyle default
    if "`_coefstyle'" == "" & `_is_postest' local _coefstyle "scatter"

    // - Session 6 option validation (v3.6.0-s6) -------------------------
    // refval(): value or "none". ado auto-sets 0 raw, 1 eform.
    local _pe_refval ""
    if "`refval'" != "" {
        local _rv = lower(itrim("`refval'"))
        if "`_rv'" == "none" {
            local _pe_refval "none"
        }
        else {
            capture local _rvtest = real("`_rv'")
            if _rc != 0 | "`_rvtest'" == "." {
                display as error "refval(): must be a number or 'none' -- got '`refval''"
                exit 198
            }
            local _pe_refval "`_rv'"
        }
    }
    local _yfree = ("`yfree'" != "")   // v3.6.0-s9g
    // v3.6.0-s8b: eform is meaningful only for e(b)-based charts
    if "`eform'" != "" & "`type'" == "marginsplot" {
        display as error "eform is not valid with type(marginsplot); margins already reports predictions."
        exit 198
    }
    if "`_pe_refval'" == "" & `_is_postest' {
        if "`eform'" != "" {
            local _pe_refval "1"
        }
        else if "`type'" == "marginsplot" {
            // v3.6.0-s8b: predictive margins (e.g. predicted price ~6000) must NOT be
            // forced to include zero -- it flattens the plot (Wilke ch.17: only bars
            // need a zero baseline). Keep the null line for effects: dydx/contrasts/
            // pwcompare. pexline(0) / refval(0) still override.
            local _mp_cmdl `"`_mg_cmdl'"'   // t2g: from the snapshot (r() may be gone by now)
            local _mp_iseff 0
            if regexm(`"`_mp_cmdl'"', "dydx|dyex|eydx|eyex|contrast|pwcompare") local _mp_iseff 1
            if regexm(`"`_mp_cmdl'"', "margins +([a-z]{1,2})\.[A-Za-z_]") local _mp_iseff 1   // contrast operators r. a. g. h. j. p. q.
            if `_mp_iseff' local _pe_refval "0"
            else local _pe_refval "none"
        }
        else {
            local _pe_refval "0"
        }
    }
    // reflinewidth(): reference-line width in px (t2j fix8 A13). Empty -> Java default 1.
    local _pe_reflinewidth ""
    if "`reflinewidth'" != "" {
        capture confirm number `reflinewidth'
        if _rc {
            display as error "reflinewidth(): must be a number -- got '`reflinewidth''"
            exit 198
        }
        if `reflinewidth' <= 0 {
            display as error "reflinewidth(): must be greater than 0"
            exit 198
        }
        local _pe_reflinewidth "`reflinewidth'"
    }
    // estlabels(): tilde-sep custom legend labels
    local _pe_estlabels ""
    if "`estlabels'" != "" {
        if !`_is_postest' {
            display as error "estlabels() is only valid with post-estimation types"
            exit 198
        }
        local _pe_estlabels `"`estlabels'"'
    }
    // cicolors(): pipe-sep per-model CI colors
    local _pe_cicolors ""
    if "`cicolors'" != "" {
        if !`_is_postest' {
            display as error "cicolors() is only valid with post-estimation types"
            exit 198
        }
        local _pe_cicolors `"`cicolors'"'
    }
    // ciwidth(): CI thickness in px (positive number)
    local _pe_ciwidth ""
    if "`ciwidth'" != "" {
        if !`_is_postest' {
            display as error "ciwidth() is only valid with post-estimation types"
            exit 198
        }
        capture local _cwtest = real("`ciwidth'")
        if _rc != 0 | `_cwtest' <= 0 {
            display as error "ciwidth(): must be a positive number -- got '`ciwidth''"
            exit 198
        }
        local _pe_ciwidth "`ciwidth'"
    }

    // indicators() and addstats(): extract from _raw_0 (saved before syntax).
    // syntax strips outer quotes making multi-word labels unparseable via gettoken.
    // _raw_0 contains the original call string with all quotes intact.
    // Strategy: scan _raw_0 character-by-character to find quoted entries in
    //   indicators(...) and addstats(...), preserving spaces inside quotes.
    // Format per entry: "label/v1 v2 v3" -- split on first / for label vs values.
    local _pe_indicators ""
    local _pe_addstats ""
    if `"`indicators'"' != "" | `"`addstats'"' != "" {
        if !`_is_postest' {
            if `"`indicators'"' != "" {
                display as error "indicators() is only valid with post-estimation types"
                exit 198
            }
            display as error "addstats() is only valid with post-estimation types"
            exit 198
        }
        sparkta_parse_tblrows `"`_raw_0'"'
        local _pe_indicators "`r(_pe_indicators)'"
        local _pe_addstats   "`r(_pe_addstats)'"
    }

    // Coefficient display label locals -- populated after _pe_names is set (see below)
    local _pe_varlabels ""
    local _pe_customlabels ""

    // s7d option flags -- init before set-from-options
    local _notimestamp   0
    local _collapsestats 0
    local _noallfilter   0

    // levels() -- two nested CI levels for coefplot/eventstudy (v3.6.0-s7d)
    if "`notimestamp'" != "" local _notimestamp 1
    if "`collapsestats'" != "" local _collapsestats 1
    if "`noallfilter'" != "" local _noallfilter 1

    if "`levels'" != "" {
        local _lev1 : word 1 of `levels'
        local _lev2 : word 2 of `levels'
        if "`_lev2'" == "" {
            display as error "levels() requires two values e.g. levels(90 95)"
            exit 198
        }
        local _lev1n = real("`_lev1'")
        local _lev2n = real("`_lev2'")
        foreach _lv in `_lev1' `_lev2' {
            local _lvn = real("`_lv'")
            if missing(`_lvn') | `_lvn' <= 0 | `_lvn' >= 100 {
                display as error "levels() values must be between 1 and 99"
                exit 198
            }
        }
        // Ensure inner (narrower) < outer (wider) -- silently sort
        if `_lev1n' > `_lev2n' {
            local _tmp = `_lev1n'
            local _lev1n = `_lev2n'
            local _lev2n = `_tmp'
        }
        local _lev1 = string(round(`_lev1n', 1))
        local _lev2 = string(round(`_lev2n', 1))
        // v3.6.0-t2i (hardening): levels(inner outer) defines BOTH bands, so the
        // OUTER band is the CI level -- override cilevel to _lev2. Previously the
        // coefplot/eventstudy reader kept cilevel (default 95) as the outer and
        // ignored _lev2 entirely, so levels(80 90) drew 80 & 95, not 80 & 90
        // (masked in tests because every case used levels(90 95); Astra). This
        // one assignment fixes the reader computation, the axis/label, arg 79 and
        // the publication table; the marginsplot path already passed _lev2.
        local cilevel "`_lev2'"
    }

    // Post-estimation type guards
    if `_is_postest' & "`type'" != "marginsplot" & "`matrix'" == "" {
        // coefplot and eventstudy require e(b) to exist
        capture confirm matrix e(b)
        if _rc != 0 {
            display as error "type(`type') requires estimation results in memory."
            display as error "  Run an estimation command first (regress, logit, etc.),"
            display as error "  then run sparkta, type(`type')."
            exit 198
        }
    }
    if "`type'" == "marginsplot" {
        // t2g: re-post the kept results (margins in memory or results() file) so the reader sees r()
        if "`_mg_have'" == "1" {
            local _mg_flags ""
            if "`_mg_hasvs'" == "1" local _mg_flags "hasvs"
            if "`_mg_hasat'" == "1" local _mg_flags "`_mg_flags' hasat"
            // fix 2: values passed bare -- string asis keeps compound quotes (the fix-5 lesson again)
            sparkta_margins_repost, `_mg_flags' plab(`_mg_plab') expr(`_mg_expr') cmdl(`_mg_cmdl')
        }
        // marginsplot requires r(table) from a prior margins command
        capture confirm matrix r(table)
        if _rc != 0 {
            display as error "type(marginsplot) requires margins results in memory."
            display as error "  Run margins first, then sparkta, type(marginsplot)."
            exit 198
        }
    }
    // Post-estimation options are only valid for post-estimation types
    if !`_is_postest' {
        foreach _peopt in omit show estnames coeflbl headings coefstyle cistyle pointstyles coefsort order refval reflinewidth estlabels cicolors ciwidth connected indicators addstats coeflabels pexline levels {
            if "``_peopt''" != "" {
                display as error "`_peopt'() is only valid with type(coefplot), type(eventstudy), or type(marginsplot)"
                exit 198
            }
        }
        if "`nocons'" != "" {
            display as error "nocons is only valid with type(coefplot) or type(eventstudy)"
            exit 198
        }
        if "`nobase'" != "" {
            display as error "nobase is only valid with type(coefplot) or type(eventstudy)"
            exit 198
        }
        if "`eform'" != "" {
            display as error "eform is only valid with type(coefplot) or type(eventstudy)"
            exit 198
        }
        if "`noci'" != "" {
            display as error "noci is only valid with type(coefplot), type(eventstudy), or type(marginsplot)"
            exit 198
        }
        if `rescale' != 1 {
            display as error "rescale() is only valid with type(coefplot) or type(eventstudy)"
            exit 198
        }
    }
    // v3.6.0: coefplot/eventstudy default to dropping _cons unless user specifies cons
    // This matches common coefplot usage where drop(_cons) is almost always wanted.
    if inlist("`type'", "coefplot", "eventstudy") & "`cons'" == "" & "`nocons'" == "" {
        // Default: drop _cons for coefplot/eventstudy (user can override with cons option)
        local nocons "nocons"
    }
    // nocons: set omit to include _cons if not already in omit list
    if "`nocons'" != "" & "`omit'" == "" local omit "_cons"
    if "`nocons'" != "" & "`omit'" != "" & !strpos("`omit'", "_cons") {
        local omit "`omit' _cons"
    }
    // v3.6.0: noci flag -- pass to Java as arg 183
    local _noci_flag ""
    if "`noci'" != "" local _noci_flag "1"
    // -------------------------------------------------------------------------

    // - Post-estimation data readers (v3.6.0) ---------------------------------
    // Initialize locals that will hold pipe-separated data for Java
    local _pe_names    ""
    local _pe_coefs    ""
    local _pe_lower    ""
    local _pe_upper    ""
    local _pe_lower2   ""   // v3.6.0-s7d: inner CI lower bounds
    local _pe_upper2   ""   // v3.6.0-s7d: inner CI upper bounds
    local _pe_ses      ""
    local _pe_pvals    ""
    local _pe_tzvals   ""
    local _pe_estnames ""
    local _pe_headings ""
    local _pe_nobs     ""
    local _pe_depvar   ""
    local _pe_orient   ""
    local _pe_coefst   ""
    local _pe_cist     ""
    local _pe_pstyles  ""
    local _pe_bases    ""
    local _pe_tstat    ""
    local _pe_cmd      ""
    local _mp_data     ""
    local _mp_upper    ""
    local _mp_lower    ""
    local _mp_xlab     ""
    local _mp_ylab     ""
    local _mp_series   ""   // v3.6.0-s8a: pipe-sep series labels
    local _mp_xpos     ""   // v3.6.0-s8a: numeric x positions from r(at)
    // Session 6 visual control locals (v3.6.0-s6)
    // Note: _pe_refval already initialized in validation block above
    local _pe_estlabels  ""
    local _pe_cicolors   ""
    local _pe_ciwidth    ""
    local _pe_connected  0
    local _pe_xpos       ""   // v3.6.0-t2d: eventstudy relative times (numeric axis) -- empty = category axis

    if inlist("`type'", "coefplot", "eventstudy") {
        // Orientation: coefplot defaults horizontal, eventstudy defaults vertical
        if "`type'" == "coefplot"    local _pe_orient "h"  // coefplot default: horizontal (Jann convention)
        if "`type'" == "eventstudy"  local _pe_orient "v"  // eventstudy default: vertical (time on x-axis)
        // If user specified horizontal flag, override eventstudy default
        if "`horizontal'" != "" local _pe_orient "h"
        if `_vertical' local _pe_orient "v"      // v3.6.0-s8s: Jann-style vertical coefplot
        // v3.6.0-s8b: label the value axis when eform is used (Wilke: axes must
        // state the transformed quantity; a bare number axis at ref 1 is ambiguous)
        if "`eform'" != "" {
            local _eform_lbl "exp(coefficient)"
            if inlist("`e(cmd)'", "logit", "logistic", "clogit", "mlogit", "ologit", "xtlogit", "melogit") local _eform_lbl "Odds ratio"
            if inlist("`e(cmd)'", "poisson", "nbreg", "xtpoisson", "mepoisson", "zip", "zinb") local _eform_lbl "Incidence rate ratio"
            if inlist("`e(cmd)'", "cox", "stcox", "streg", "stcrreg") local _eform_lbl "Hazard ratio"
            if inlist("`e(cmd)'", "probit", "oprobit") local _eform_lbl "exp(coefficient)"
            local _ef_vert = ("`_pe_orient'" == "v") | ("`estnames'" != "")   // multi-model is always vertical
            if !`_ef_vert' & "`xtitle'" == "" local xtitle "`_eform_lbl'"
            if  `_ef_vert' & "`ytitle'" == "" local ytitle "`_eform_lbl'"
        }
        local _pe_coefst "`_coefstyle'"
        local _pe_cist   "`_cistyle'"
        if "`pointstyles'" != "" local _pe_pstyles "`pointstyles'"
        // Session 6 visual control locals
        local _pe_estlabels `"`estlabels'"'
        local _pe_cicolors  `"`cicolors'"'
        local _pe_ciwidth   "`ciwidth'"
        if "`connected'" != "" local _pe_connected 1
        // Pass through options that Java needs
        local _pe_headings "`headings'"
        local _pe_estnames "`estnames'"
        // Read e(b)/e(V) and compute SE, CI, p-values
        display as text "  Post-estimation mode: type(`type')"
        local _nobase_flag ""
        if "`nobase'" != "" local _nobase_flag "nobase"

        if "`matrix'" != "" {
            // -- MATRIX MODE (v3.6.0-t2a): one reader per matrix; several -> tilde groups like estnames() --
            local _es_flag ""
            if "`type'" == "eventstudy" local _es_flag "eventstudy"
            local _mm_count 0
            foreach _mspec of local _mat_list {
                local _mm_count = `_mm_count' + 1
                sparkta_read_matrix, matrix(`_mspec') ci(`ci') se(`se') df(`df') pvalue(`pvalue') cilevel(`cilevel') level2(`_lev1') omit(`omit') show(`show') coeflbl(`coeflbl') `_es_flag'
                if !`_pe_matrix_hasci' & "`noci'" == "" {
                    local noci "noci"
                    local _noci_flag "1"
                }
                local _mlabel : word `_mm_count' of `estnames'
                if "`_mlabel'" == "" local _mlabel "`_pe_matname'"
                if `_mm_count' == 1 {
                    local _mm_names  "`_pe_names'"
                    local _mm_coefs  "`_pe_coefs'"
                    local _mm_lower  "`_pe_lower'"
                    local _mm_upper  "`_pe_upper'"
                    local _mm_lower2 "`_pe_lower2'"
                    local _mm_upper2 "`_pe_upper2'"
                    local _mm_ses    "`_pe_ses'"
                    local _mm_pvals  "`_pe_pvals'"
                    local _mm_tzvals "`_pe_tzvals'"
                    local _mm_bases  ""
                    local _mm_nobs   ""
                    local _mm_tstat  "`_pe_tstat'"
                    local _mm_cmd    "`_pe_cmd'"
                    local _mm_depvar "`_pe_depvar'"
                    local _mm_estlabels "`_mlabel'"
                    local _mm_fitstats ""
                    local _mm_xpos   "`_pe_xpos'"
                }
                else {
                    local _mm_names  "`_mm_names'~`_pe_names'"
                    local _mm_coefs  "`_mm_coefs'~`_pe_coefs'"
                    local _mm_lower  "`_mm_lower'~`_pe_lower'"
                    local _mm_upper  "`_mm_upper'~`_pe_upper'"
                    if "`_pe_lower2'" != "" {
                        local _mm_lower2 "`_mm_lower2'~`_pe_lower2'"
                        local _mm_upper2 "`_mm_upper2'~`_pe_upper2'"
                    }
                    local _mm_ses    "`_mm_ses'~`_pe_ses'"
                    local _mm_pvals  "`_mm_pvals'~`_pe_pvals'"
                    local _mm_tzvals "`_mm_tzvals'~`_pe_tzvals'"
                    local _mm_nobs   "`_mm_nobs'~"
                    local _mm_cmd    "matrix"
                    local _mm_estlabels "`_mm_estlabels'~`_mlabel'"
                    local _mm_fitstats "`_mm_fitstats'~"
                    local _mm_xpos   "`_mm_xpos'~`_pe_xpos'"
                }
            }
            if `_mat_multi' {
                local _pe_names    "`_mm_names'"
                local _pe_coefs    "`_mm_coefs'"
                local _pe_lower    "`_mm_lower'"
                local _pe_upper    "`_mm_upper'"
                local _pe_lower2   "`_mm_lower2'"
                local _pe_upper2   "`_mm_upper2'"
                local _pe_ses      "`_mm_ses'"
                local _pe_pvals    "`_mm_pvals'"
                local _pe_tzvals   "`_mm_tzvals'"
                local _pe_bases    ""
                local _pe_nobs     "`_mm_nobs'"
                local _pe_tstat    "`_mm_tstat'"
                local _pe_cmd      "`_mm_cmd'"
                local _pe_depvar   "`_mm_depvar'"
                local _pe_estnames "`_mm_estlabels'"
                local _pe_fitstats "`_mm_fitstats'"
                local _pe_xpos     "`_mm_xpos'"
                display as text "  Matrix mode: `_mm_count' matrices combined"
            }
        }
        else if "`estnames'" != "" {
            // -- Multi-model mode: iterate through stored estimates --
            // Each model's data is concatenated with tilde (~) between models.
            // Java parses tilde groups to create separate datasets per model.
            local _mm_names  ""
            local _mm_coefs  ""
            local _mm_lower  ""
            local _mm_upper  ""
            local _mm_ses    ""
            local _mm_pvals  ""
            local _mm_tzvals ""
            local _mm_bases  ""
            local _mm_nobs   ""
            local _mm_tstat  ""
            local _mm_cmd    ""
            local _mm_depvar ""
            local _mm_count 0
            local _mm_estlabels ""
            local _mm_fitstats ""   // v3.6.0-s6c: tilde-sep fit stats per model
            // B4 (t2j): the loop below `estimates restore`s each named model, which
            // leaves the LAST one active and clobbers whatever estimates the caller had
            // active. Snapshot the caller's active estimates and restore them after the
            // loop so sparkta leaves e() as it found it (caller-state hygiene).
            tempname _spk_estsave
            local _spk_have_est = 0
            capture estimates store `_spk_estsave'
            if _rc == 0 local _spk_have_est = 1
            foreach _ename of local estnames {
                capture estimates restore `_ename'
                if _rc != 0 {
                    display as error "sparkta: estimates `_ename' not found."
                    display as error "  Use {bf:estimates store `_ename'} after your regression."
                    exit 198
                }
                local _mm_count = `_mm_count' + 1
                display as text "  Model `_mm_count': `_ename' (`e(cmd)')"
                sparkta_read_eresults, cilevel(`cilevel') omit(`omit') show(`show') coeflbl(`coeflbl') `_nobase_flag' level2(`_lev1')
                // Concatenate with tilde separator
                if `_mm_count' == 1 {
                    local _mm_names  "`_pe_names'"
                    local _mm_coefs  "`_pe_coefs'"
                    local _mm_lower  "`_pe_lower'"
                    local _mm_upper  "`_pe_upper'"
                    local _mm_lower2 "`_pe_lower2'"
                    local _mm_upper2 "`_pe_upper2'"
                    local _mm_ses    "`_pe_ses'"
                    local _mm_pvals  "`_pe_pvals'"
                    local _mm_tzvals "`_pe_tzvals'"
                    local _mm_bases  "`_pe_bases'"
                    local _mm_nobs   "`_pe_nobs'"
                    local _mm_tstat  "`_pe_tstat'"
                    local _mm_cmd    "`_pe_cmd'"
                    local _mm_depvar "`_pe_depvar'"
                    local _mm_estlabels "`_ename'"
                    local _mm_fitstats "`_pe_fitstats'"   // v3.6.0-s6c
                }
                else {
                    local _mm_names  "`_mm_names'~`_pe_names'"
                    local _mm_coefs  "`_mm_coefs'~`_pe_coefs'"
                    local _mm_lower  "`_mm_lower'~`_pe_lower'"
                    local _mm_upper  "`_mm_upper'~`_pe_upper'"
                    // v3.6.0-s8c: only accumulate inner CI when levels() produced values;
                    // "~~" (empty groups) crashed the Java levels2 path (rc=5101)
                    if "`_pe_lower2'" != "" {
                        local _mm_lower2 "`_mm_lower2'~`_pe_lower2'"
                        local _mm_upper2 "`_mm_upper2'~`_pe_upper2'"
                    }
                    local _mm_ses    "`_mm_ses'~`_pe_ses'"
                    local _mm_pvals  "`_mm_pvals'~`_pe_pvals'"
                    local _mm_tzvals "`_mm_tzvals'~`_pe_tzvals'"
                    if "`_pe_bases'" != "" {
                        if "`_mm_bases'" == "" local _mm_bases "`_pe_bases'"
                        else local _mm_bases "`_mm_bases'|`_pe_bases'"
                    }
                    local _mm_nobs   "`_mm_nobs'~`_pe_nobs'"
                    local _mm_tstat  "`_pe_tstat'"
                    local _mm_cmd    "`_pe_cmd'"
                    local _mm_depvar "`_pe_depvar'"
                    local _mm_estlabels "`_mm_estlabels'~`_ename'"
                    local _mm_fitstats "`_mm_fitstats'~`_pe_fitstats'"   // v3.6.0-s6c
                }
            }
            // B4 (t2j): restore the caller's active estimates (and drop our snapshot).
            if `_spk_have_est' {
                capture estimates restore `_spk_estsave'
                capture estimates drop `_spk_estsave'
            }
            // Override the single-model locals with multi-model concatenated data
            local _pe_names    "`_mm_names'"
            local _pe_coefs    "`_mm_coefs'"
            local _pe_lower    "`_mm_lower'"
            local _pe_upper    "`_mm_upper'"
            local _pe_lower2   "`_mm_lower2'"
            local _pe_upper2   "`_mm_upper2'"
            local _pe_ses      "`_mm_ses'"
            local _pe_pvals    "`_mm_pvals'"
            local _pe_tzvals   "`_mm_tzvals'"
            local _pe_bases    "`_mm_bases'"
            local _pe_nobs     "`_mm_nobs'"
            local _pe_tstat    "`_mm_tstat'"
            local _pe_cmd      "`_mm_cmd'"
            local _pe_depvar   "`_mm_depvar'"
            local _pe_estnames "`_mm_estlabels'"
            local _pe_fitstats "`_mm_fitstats'"   // v3.6.0-s6c
            display as text "  Multi-model: `_mm_count' models combined"
        }
        else {
            // -- Single-model mode: read current e() results --
            sparkta_read_eresults, cilevel(`cilevel') omit(`omit') show(`show') coeflbl(`coeflbl') `_nobase_flag' level2(`_lev1')
            // _pe_fitstats already set by c_local from sparkta_read_eresults
        }

        // -- v3.6.0-t2d: eventstudy on a NUMERIC time axis: every coefficient name must parse to a
        // relative time (lead#/lag#, T-3/T/T+2, tm3/tp0, Tm3/Tp1, -3.__event__, plain integers).
        // Matrix mode already parsed them; estimation mode parses here. Any unparsed name ->
        // the category-axis chart as before, with a note.
        if "`type'" == "eventstudy" & "`matrix'" == "" {
            sparkta_event_times "`_pe_names'"
            if "`s(ok)'" == "1" local _pe_xpos "`s(times)'"
            else {
                local _pe_xpos ""
                display as text "  eventstudy: could not read a relative time from `s(bad)' -- drawing a category axis"
            }
            sreturn clear
        }
        if "`type'" == "eventstudy" & "`matrix'" != "" {
            // all periods parsed? (summaries are 9998/9999; "." means unparsed)
            if strpos("`_pe_xpos'", ".") > 0 {
                local _pe_xpos ""
                display as text "  eventstudy: some coefficient names are not relative times -- drawing a category axis"
            }
        }
        // -- Collect variable labels now that _pe_names is populated --
        // _pe_names holds pipe-sep coef names per model, tilde-sep across models.
        // e.g. "mpg|weight|foreign~mpg|weight|rep78"
        // NOTE: _cons is renamed to Constant by sparkta_read_eresults before storage.
        // We skip Constant (Java handles the display) and collect labels for real vars.
        local _vl_seen ""
        local _vl_result ""
        local _vl_rest "`_pe_names'"
        while "`_vl_rest'" != "" {
            gettoken _vn _vl_rest : _vl_rest, parse("|~")
            if "`_vn'" == "|" | "`_vn'" == "~" continue
            if "`_vn'" == "" | "`_vn'" == "Constant" | "`_vn'" == "_cons" continue
            // Skip if already collected
            if strpos(" `_vl_seen' ", " `_vn' ") > 0 continue
            local _vl_seen "`_vl_seen' `_vn'"
            // Strip factor variable prefix (e.g. 1.rep78 -> rep78) for label lookup.
            // Skip display names that are not valid Stata variable names:
            //   contains '=' (our varname=level format e.g. 'rep78=1')
            //   contains ' ' (interaction display e.g. 'rep78 x foreign')
            //   starts with digit (unreformed colname)
            if strpos("`_vn'", "=") > 0 | strpos("`_vn'", " ") > 0 continue
            if regexm("`_vn'", "^[0-9]") continue
            local _vn_base "`_vn'"
            if regexm("`_vn'", "^[0-9]+[bon]*\\.") local _vn_base = regexr("`_vn'", "^[0-9]+[bon]*\\.", "")
            capture local _vlbl : variable label `_vn_base'   // t2a: matrix-mode names need not be variables
            if _rc continue
            if "`_vlbl'" == "" continue
            // Safety: strip reserved separators
            local _vlbl = subinstr("`_vlbl'", "|", " ", .)
            local _vlbl = subinstr("`_vlbl'", "~", " ", .)
            if "`_vl_result'" == "" local _vl_result "`_vn'|||`_vlbl'"
            else local _vl_result "`_vl_result'~~~`_vn'|||`_vlbl'"
        }
        local _pe_varlabels "`_vl_result'"

        // -- Parse coeflabels() custom labels from _raw_0 --
        if `"`coeflabels'"' != "" {
            sparkta_parse_coeflabels `"`_raw_0'"'
            local _pe_customlabels "`r(_pe_customlabels)'"
        }
        // v3.6.0: eform -- exponentiate coefficients, CIs, and SEs (odds ratios, hazard ratios)
        // SE transform: delta method SE(exp(b)) = exp(b) * SE(b)
        // CI: exp(lower), exp(upper) -- matches Stata's eform convention
        if "`eform'" != "" {
            display as text "  eform: exponentiating coefficients (odds/hazard ratios)"
            // Parse pipes AND tildes into numbered locals, transform, rebuild
            // Tilde (~) separates models in multi-model mode; pipe (|) separates
            // coefficients within a model. Both must be parsed as delimiters.
            // We record the delimiter sequence from the coefs parse so the
            // rebuilt strings preserve the original | vs ~ structure.
            local _ef_k 0
            local _ef_delims ""
            local _tmpc "`_pe_coefs'"
            while "`_tmpc'" != "" {
                local _ef_k = `_ef_k' + 1
                gettoken _tok _tmpc : _tmpc, parse("|~")
                if "`_tok'" == "|" | "`_tok'" == "~" {
                    local _ef_delims "`_ef_delims'`_tok'"
                    local _ef_k = `_ef_k' - 1
                    continue
                }
                local _efc_`_ef_k' "`_tok'"
            }
            local _tmpx "`_pe_lower'"
            local _ti 0
            while "`_tmpx'" != "" {
                local _ti = `_ti' + 1
                gettoken _tok _tmpx : _tmpx, parse("|~")
                if "`_tok'" == "|" | "`_tok'" == "~" {
                    local _ti = `_ti' - 1
                    continue
                }
                local _efl_`_ti' "`_tok'"
            }
            local _tmpx "`_pe_upper'"
            local _ti 0
            while "`_tmpx'" != "" {
                local _ti = `_ti' + 1
                gettoken _tok _tmpx : _tmpx, parse("|~")
                if "`_tok'" == "|" | "`_tok'" == "~" {
                    local _ti = `_ti' - 1
                    continue
                }
                local _efu_`_ti' "`_tok'"
            }
            local _tmpx "`_pe_ses'"
            local _ti 0
            while "`_tmpx'" != "" {
                local _ti = `_ti' + 1
                gettoken _tok _tmpx : _tmpx, parse("|~")
                if "`_tok'" == "|" | "`_tok'" == "~" {
                    local _ti = `_ti' - 1
                    continue
                }
                local _efs_`_ti' "`_tok'"
            }
            // Transform using scalars (robust against macro expansion issues)
            local _ef_rc ""
            local _ef_rl ""
            local _ef_ru ""
            local _ef_rs ""
            forvalues _ti = 1/`_ef_k' {
                scalar _spk_ec = exp(`_efc_`_ti'')
                scalar _spk_el = exp(`_efl_`_ti'')
                scalar _spk_eu = exp(`_efu_`_ti'')
                scalar _spk_es = scalar(_spk_ec) * `_efs_`_ti''
                local _fec : display %12.7g scalar(_spk_ec)
                local _fec = itrim("`_fec'")
                local _fel : display %12.7g scalar(_spk_el)
                local _fel = itrim("`_fel'")
                local _feu : display %12.7g scalar(_spk_eu)
                local _feu = itrim("`_feu'")
                local _fes : display %12.7g scalar(_spk_es)
                local _fes = itrim("`_fes'")
                if `_ti' > 1 {
                    // Use the recorded delimiter (| or ~) at position _ti-1
                    local _dsep = substr("`_ef_delims'", `_ti' - 1, 1)
                    local _ef_rc "`_ef_rc'`_dsep'"
                    local _ef_rl "`_ef_rl'`_dsep'"
                    local _ef_ru "`_ef_ru'`_dsep'"
                    local _ef_rs "`_ef_rs'`_dsep'"
                }
                local _ef_rc "`_ef_rc'`_fec'"
                local _ef_rl "`_ef_rl'`_fel'"
                local _ef_ru "`_ef_ru'`_feu'"
                local _ef_rs "`_ef_rs'`_fes'"
                scalar drop _spk_ec _spk_el _spk_eu _spk_es
            }
            local _pe_coefs "`_ef_rc'"
            local _pe_lower "`_ef_rl'"
            local _pe_upper "`_ef_ru'"
            local _pe_ses   "`_ef_rs'"
            // v3.6.0-s8b: levels() inner CI bounds (_pe_lower2/_pe_upper2) live on the
            // log scale too -- exponentiate them with the same |/~ structure, else the
            // inner whiskers are drawn at log-scale positions on an eform axis.
            foreach _l2 in _pe_lower2 _pe_upper2 {
                if "``_l2''" != "" {
                    local _l2_out ""
                    local _l2_src "``_l2''"
                    while "`_l2_src'" != "" {
                        gettoken _tok _l2_src : _l2_src, parse("|~")
                        if "`_tok'" == "|" | "`_tok'" == "~" {
                            local _l2_out "`_l2_out'`_tok'"
                            continue
                        }
                        if "`_tok'" == "." | "`_tok'" == "" local _l2_out "`_l2_out'."
                        else {
                            scalar _spk_e2 = exp(`_tok')
                            local _fe2 : display %12.7g scalar(_spk_e2)
                            local _l2_out "`_l2_out'`=itrim("`_fe2'")'"
                            scalar drop _spk_e2
                        }
                    }
                    local `_l2' "`_l2_out'"
                }
            }
        }
        // v3.6.0: rescale -- multiply coefficients, SEs, and CI bounds by factor
        // Applied AFTER eform so the two compose correctly if both specified.
        if `rescale' != 1 {
            display as text "  rescale: multiplying values by `rescale'"
            // Parse into numbered locals with both | and ~ as delimiters.
            // Record delimiter sequence to preserve multi-model ~ structure.
            local _rs_k 0
            local _rs_delims ""
            local _tmpc "`_pe_coefs'"
            while "`_tmpc'" != "" {
                local _rs_k = `_rs_k' + 1
                gettoken _tok _tmpc : _tmpc, parse("|~")
                if "`_tok'" == "|" | "`_tok'" == "~" {
                    local _rs_delims "`_rs_delims'`_tok'"
                    local _rs_k = `_rs_k' - 1
                    continue
                }
                local _rsc_`_rs_k' "`_tok'"
            }
            local _tmpx "`_pe_lower'"
            local _ti 0
            while "`_tmpx'" != "" {
                local _ti = `_ti' + 1
                gettoken _tok _tmpx : _tmpx, parse("|~")
                if "`_tok'" == "|" | "`_tok'" == "~" {
                    local _ti = `_ti' - 1
                    continue
                }
                local _rsl_`_ti' "`_tok'"
            }
            local _tmpx "`_pe_upper'"
            local _ti 0
            while "`_tmpx'" != "" {
                local _ti = `_ti' + 1
                gettoken _tok _tmpx : _tmpx, parse("|~")
                if "`_tok'" == "|" | "`_tok'" == "~" {
                    local _ti = `_ti' - 1
                    continue
                }
                local _rsu_`_ti' "`_tok'"
            }
            local _tmpx "`_pe_ses'"
            local _ti 0
            while "`_tmpx'" != "" {
                local _ti = `_ti' + 1
                gettoken _tok _tmpx : _tmpx, parse("|~")
                if "`_tok'" == "|" | "`_tok'" == "~" {
                    local _ti = `_ti' - 1
                    continue
                }
                local _rss_`_ti' "`_tok'"
            }
            local _rs_rc ""
            local _rs_rl ""
            local _rs_ru ""
            local _rs_rs ""
            forvalues _ti = 1/`_rs_k' {
                scalar _spk_rc = `_rsc_`_ti'' * `rescale'
                scalar _spk_rl = `_rsl_`_ti'' * `rescale'
                scalar _spk_ru = `_rsu_`_ti'' * `rescale'
                scalar _spk_rs = `_rss_`_ti'' * abs(`rescale')
                local _frc : display %12.7g scalar(_spk_rc)
                local _frc = itrim("`_frc'")
                local _frl : display %12.7g scalar(_spk_rl)
                local _frl = itrim("`_frl'")
                local _fru : display %12.7g scalar(_spk_ru)
                local _fru = itrim("`_fru'")
                local _frs : display %12.7g scalar(_spk_rs)
                local _frs = itrim("`_frs'")
                if `_ti' > 1 {
                    local _dsep = substr("`_rs_delims'", `_ti' - 1, 1)
                    local _rs_rc "`_rs_rc'`_dsep'"
                    local _rs_rl "`_rs_rl'`_dsep'"
                    local _rs_ru "`_rs_ru'`_dsep'"
                    local _rs_rs "`_rs_rs'`_dsep'"
                }
                local _rs_rc "`_rs_rc'`_frc'"
                local _rs_rl "`_rs_rl'`_frl'"
                local _rs_ru "`_rs_ru'`_fru'"
                local _rs_rs "`_rs_rs'`_frs'"
                scalar drop _spk_rc _spk_rl _spk_ru _spk_rs
            }
            local _pe_coefs "`_rs_rc'"
            local _pe_lower "`_rs_rl'"
            local _pe_upper "`_rs_ru'"
            local _pe_ses   "`_rs_rs'"
        }
        // t2j fix8 (A11): coefsort()/order() reorder the single pipe-sep coefficient
        // arrays; with multiple models the coefficients are tilde-joined per model and
        // that reordering is not defined, so it is silently skipped below. Warn the user
        // explicitly rather than ignoring the option without a word.
        if ("`coefsort'" != "" | "`order'" != "") & strpos("`_pe_coefs'", "~") {
            display as text "  note: coefsort()/order() apply to single-model coefplots only; ignored for multi-model estimates"
        }
        // v3.6.0: coefsort -- sort coefficients by value, abs value, pval, or se
        // Applied AFTER eform/rescale. Reorders all pipe-sep arrays in sync.
        // Only for single-model (tilde in _pe_coefs means multi-model, skip sort).
        if "`coefsort'" != "" & !strpos("`_pe_coefs'", "~") {
            // Validate sort keyword
            if !inlist("`coefsort'", "value", "abs", "pval", "se", "desc") {
                display as error "coefsort() must be one of: value, abs, pval, se, desc"
                exit 198
            }
            display as text "  coefsort(`coefsort'): reordering coefficients"
            // Parse pipe-sep arrays into numbered locals for sorting
            local _sn = wordcount(subinstr("`_pe_names'", "|", " ", .)) + 1
            // Actually count by splitting
            local _sk 0
            local _tmp_names "`_pe_names'"
            while "`_tmp_names'" != "" {
                local _sk = `_sk' + 1
                gettoken _sn_`_sk' _tmp_names : _tmp_names, parse("|")
                if "`_sn_`_sk''" == "|" {
                    local _sk = `_sk' - 1
                    continue
                }
            }
            // Parse coefs, lower, upper, ses, pvals the same way
            local _tmp "`_pe_coefs'"
            local _si 0
            while "`_tmp'" != "" {
                local _si = `_si' + 1
                gettoken _sc_`_si' _tmp : _tmp, parse("|")
                if "`_sc_`_si''" == "|" {
                    local _si = `_si' - 1
                    continue
                }
            }
            local _tmp "`_pe_lower'"
            local _si 0
            while "`_tmp'" != "" {
                local _si = `_si' + 1
                gettoken _sl_`_si' _tmp : _tmp, parse("|")
                if "`_sl_`_si''" == "|" {
                    local _si = `_si' - 1
                    continue
                }
            }
            local _tmp "`_pe_upper'"
            local _si 0
            while "`_tmp'" != "" {
                local _si = `_si' + 1
                gettoken _su_`_si' _tmp : _tmp, parse("|")
                if "`_su_`_si''" == "|" {
                    local _si = `_si' - 1
                    continue
                }
            }
            local _tmp "`_pe_ses'"
            local _si 0
            while "`_tmp'" != "" {
                local _si = `_si' + 1
                gettoken _ss_`_si' _tmp : _tmp, parse("|")
                if "`_ss_`_si''" == "|" {
                    local _si = `_si' - 1
                    continue
                }
            }
            local _tmp "`_pe_pvals'"
            local _si 0
            while "`_tmp'" != "" {
                local _si = `_si' + 1
                gettoken _sp_`_si' _tmp : _tmp, parse("|")
                if "`_sp_`_si''" == "|" {
                    local _si = `_si' - 1
                    continue
                }
            }
            local _tmp "`_pe_tzvals'"
            local _si 0
            while "`_tmp'" != "" {
                local _si = `_si' + 1
                gettoken _st_`_si' _tmp : _tmp, parse("|")
                if "`_st_`_si''" == "|" {
                    local _si = `_si' - 1
                    continue
                }
            }
            // fix9x: levels() inner CI bounds (_pe_lower2/_pe_upper2) must be sorted
            //   with the rest -- they were left in estimation order, so after coefsort()
            //   the inner (e.g. 90%) band was drawn on another coefficient's row.
            local _s2 = ("`_pe_lower2'" != "")
            foreach _q in l2 u2 {
                if "`_q'" == "l2" local _tmp "`_pe_lower2'"
                else                local _tmp "`_pe_upper2'"
                local _si 0
                while "`_tmp'" != "" {
                    gettoken _tok _tmp : _tmp, parse("|")
                    if "`_tok'" == "|" continue
                    local _si = `_si' + 1
                    local _s`_q'_`_si' "`_tok'"
                }
            }
            // Build sort key per coefficient
            forvalues _si = 1/`_sk' {
                if "`coefsort'" == "value" local _skey_`_si' = `_sc_`_si''
                else if "`coefsort'" == "abs" local _skey_`_si' = abs(`_sc_`_si'')
                else if "`coefsort'" == "desc" local _skey_`_si' = -abs(`_sc_`_si'')
                else if "`coefsort'" == "pval" local _skey_`_si' = `_sp_`_si''
                else if "`coefsort'" == "se" local _skey_`_si' = `_ss_`_si''
            }
            // Bubble sort (fine for <50 coefficients)
            local _swapped 1
            while `_swapped' {
                local _swapped 0
                forvalues _si = 1/`= `_sk' - 1' {
                    local _sj = `_si' + 1
                    if `_skey_`_si'' > `_skey_`_sj'' {
                        // Swap all arrays
                        foreach _arr in sn sc sl su ss sp st skey sl2 su2 {   // fix9x: + inner CI
                            local _stmp "`_`_arr'_`_si''"
                            local _`_arr'_`_si' "`_`_arr'_`_sj''"
                            local _`_arr'_`_sj' "`_stmp'"
                        }
                        local _swapped 1
                    }
                }
            }
            // Rebuild pipe-sep strings from sorted arrays
            local _pe_names  "`_sn_1'"
            local _pe_coefs  "`_sc_1'"
            local _pe_lower  "`_sl_1'"
            local _pe_upper  "`_su_1'"
            local _pe_ses    "`_ss_1'"
            local _pe_pvals  "`_sp_1'"
            local _pe_tzvals "`_st_1'"
            forvalues _si = 2/`_sk' {
                local _pe_names  "`_pe_names'|`_sn_`_si''"
                local _pe_coefs  "`_pe_coefs'|`_sc_`_si''"
                local _pe_lower  "`_pe_lower'|`_sl_`_si''"
                local _pe_upper  "`_pe_upper'|`_su_`_si''"
                local _pe_ses    "`_pe_ses'|`_ss_`_si''"
                local _pe_pvals  "`_pe_pvals'|`_sp_`_si''"
                local _pe_tzvals "`_pe_tzvals'|`_st_`_si''"
            }
            if `_s2' {   // fix9x: rebuild the inner CI strings in the sorted order
                local _pe_lower2 "`_sl2_1'"
                local _pe_upper2 "`_su2_1'"
                forvalues _si = 2/`_sk' {
                    local _pe_lower2 "`_pe_lower2'|`_sl2_`_si''"
                    local _pe_upper2 "`_pe_upper2'|`_su2_`_si''"
                }
            }
        }
        // v3.6.0: order() -- custom coefficient display order
        // Reorder pipe-sep arrays to match user-specified name list.
        // Names not in the list are appended at the end in original order.
        if "`order'" != "" & !strpos("`_pe_coefs'", "~") {
            display as text "  order(): reordering to user-specified sequence"
            // Parse current arrays into indexed locals
            local _ok 0
            local _tmp "`_pe_names'"
            while "`_tmp'" != "" {
                local _ok = `_ok' + 1
                gettoken _on_`_ok' _tmp : _tmp, parse("|")
                if "`_on_`_ok''" == "|" {
                    local _ok = `_ok' - 1
                    continue
                }
            }
            local _tmp "`_pe_coefs'"
            local _oi 0
            while "`_tmp'" != "" {
                local _oi = `_oi' + 1
                gettoken _oc_`_oi' _tmp : _tmp, parse("|")
                if "`_oc_`_oi''" == "|" {
                    local _oi = `_oi' - 1
                    continue
                }
            }
            local _tmp "`_pe_lower'"
            local _oi 0
            while "`_tmp'" != "" {
                local _oi = `_oi' + 1
                gettoken _ol_`_oi' _tmp : _tmp, parse("|")
                if "`_ol_`_oi''" == "|" {
                    local _oi = `_oi' - 1
                    continue
                }
            }
            local _tmp "`_pe_upper'"
            local _oi 0
            while "`_tmp'" != "" {
                local _oi = `_oi' + 1
                gettoken _ou_`_oi' _tmp : _tmp, parse("|")
                if "`_ou_`_oi''" == "|" {
                    local _oi = `_oi' - 1
                    continue
                }
            }
            local _tmp "`_pe_ses'"
            local _oi 0
            while "`_tmp'" != "" {
                local _oi = `_oi' + 1
                gettoken _os_`_oi' _tmp : _tmp, parse("|")
                if "`_os_`_oi''" == "|" {
                    local _oi = `_oi' - 1
                    continue
                }
            }
            local _tmp "`_pe_pvals'"
            local _oi 0
            while "`_tmp'" != "" {
                local _oi = `_oi' + 1
                gettoken _op_`_oi' _tmp : _tmp, parse("|")
                if "`_op_`_oi''" == "|" {
                    local _oi = `_oi' - 1
                    continue
                }
            }
            local _tmp "`_pe_tzvals'"
            local _oi 0
            while "`_tmp'" != "" {
                local _oi = `_oi' + 1
                gettoken _ot_`_oi' _tmp : _tmp, parse("|")
                if "`_ot_`_oi''" == "|" {
                    local _oi = `_oi' - 1
                    continue
                }
            }
            // fix9x: parse the levels() inner CI bounds too, so they follow order()
            local _o2 = ("`_pe_lower2'" != "")
            foreach _q in l2 u2 {
                if "`_q'" == "l2" local _tmp "`_pe_lower2'"
                else                local _tmp "`_pe_upper2'"
                local _oi 0
                while "`_tmp'" != "" {
                    gettoken _tok _tmp : _tmp, parse("|")
                    if "`_tok'" == "|" continue
                    local _oi = `_oi' + 1
                    local _o`_q'_`_oi' "`_tok'"
                }
            }
            local _rl2 ""
            local _ru2 ""
            // Build reordered arrays: first the user-specified names, then remainder
            local _oused ""
            local _rn ""
            local _rc ""
            local _rl ""
            local _ru ""
            local _rs ""
            local _rp ""
            local _rt ""
            local _rfirst 1
            // Pass 1: user-specified order
            foreach _oname of local order {
                forvalues _oi = 1/`_ok' {
                    local _on_trimmed = itrim("`_on_`_oi''")
                    if "`_on_trimmed'" == "`_oname'" {
                        if !`_rfirst' {
                            local _rn "`_rn'|"
                            local _rc "`_rc'|"
                            local _rl "`_rl'|"
                            local _ru "`_ru'|"
                            local _rs "`_rs'|"
                            local _rp "`_rp'|"
                            local _rt "`_rt'|"
                            local _rl2 "`_rl2'|"
                            local _ru2 "`_ru2'|"
                        }
                        local _rn "`_rn'`_on_`_oi''"
                        local _rc "`_rc'`_oc_`_oi''"
                        local _rl "`_rl'`_ol_`_oi''"
                        local _ru "`_ru'`_ou_`_oi''"
                        local _rs "`_rs'`_os_`_oi''"
                        local _rp "`_rp'`_op_`_oi''"
                        local _rt "`_rt'`_ot_`_oi''"
                        local _rl2 "`_rl2'`_ol2_`_oi''"
                        local _ru2 "`_ru2'`_ou2_`_oi''"
                        local _oused "`_oused' `_oi'"
                        local _rfirst 0
                    }
                }
            }
            // Pass 2: append remaining (not in user list) in original order
            // B1 (t2j): match the index as a WHOLE space-delimited token. The old
            // strpos("`_oused'", " `_oi'") matched " 1" inside " 10"/" 11"/... so with
            // 10+ coefficients an un-ordered single-digit coef was wrongly treated as
            // used and DROPPED. Pad both the haystack and needle with spaces so " 1 "
            // cannot match " 10 ".
            forvalues _oi = 1/`_ok' {
                if strpos(" `_oused' ", " `_oi' ") > 0 continue
                if !`_rfirst' {
                    local _rn "`_rn'|"
                    local _rc "`_rc'|"
                    local _rl "`_rl'|"
                    local _ru "`_ru'|"
                    local _rs "`_rs'|"
                    local _rp "`_rp'|"
                    local _rt "`_rt'|"
                    local _rl2 "`_rl2'|"
                    local _ru2 "`_ru2'|"
                }
                local _rn "`_rn'`_on_`_oi''"
                local _rc "`_rc'`_oc_`_oi''"
                local _rl "`_rl'`_ol_`_oi''"
                local _ru "`_ru'`_ou_`_oi''"
                local _rs "`_rs'`_os_`_oi''"
                local _rp "`_rp'`_op_`_oi''"
                local _rt "`_rt'`_ot_`_oi''"
                local _rl2 "`_rl2'`_ol2_`_oi''"
                local _ru2 "`_ru2'`_ou2_`_oi''"
                local _rfirst 0
            }
            local _pe_names  "`_rn'"
            local _pe_coefs  "`_rc'"
            local _pe_lower  "`_rl'"
            local _pe_upper  "`_ru'"
            local _pe_ses    "`_rs'"
            local _pe_pvals  "`_rp'"
            local _pe_tzvals "`_rt'"
            if `_o2' {   // fix9x
                local _pe_lower2 "`_rl2'"
                local _pe_upper2 "`_ru2'"
            }
        }
    }
    else if "`type'" == "marginsplot" {
        local _pe_coefst "`_coefstyle'"
        local _pe_cist   "`_cistyle'"
        if "`pointstyles'" != "" local _pe_pstyles "`pointstyles'"
        local _pe_estlabels `"`estlabels'"'
        local _pe_cicolors  `"`cicolors'"'
        local _pe_ciwidth   "`ciwidth'"
        if "`connected'" != "" local _pe_connected 1
        // v3.6.0-s8a: Call sparkta_read_margins to read r(table)/r(at)
        display as text "  Post-estimation mode: type(marginsplot)"
        display as text "  Reading stored margins results from r(table)..."
        // B3 (t2j): over() is a no-op for marginsplot -- the series/grouping is taken
        // from the margins result's own factor/interaction (#) structure, not over().
        // Warn instead of silently ignoring it, so the user is not misled.
        if "`over'" != "" {
            display as error "  note: over() is ignored for type(marginsplot); series come from the margins specification itself (e.g. margins i.a#i.b or margins a, over(b))."
        }
        sparkta_read_margins , cilevel("`_lev2'") ///
            cilevel2("`_lev1'") ///
            cistyle("`_cistyle'")
        // v3.6.0-s8b: continuous at() -> x-axis title defaults to the at() variable
        if "`xtitle'" == "" & "`_mp_xvar'" != "" {
            local xtitle "`_mp_xvar'"
            sparkta_mg_varlabel `_mp_xvar'   // fix8z: results() source label first, then the data in memory
            if `"`_mgvarl'"' != "" local xtitle `"`_mgvarl'"'
        }
    }
    // -------------------------------------------------------------------------

    // - Resolve sample (v1.6.0) -
    // mark all obs satisfying if/in. Missing value handling is done in Java.
    // We pass the TOUSE VARIABLE NAME to Java so it can iterate obs directly,
    // avoiding the ~67K char Stata macro limit that previously capped N at ~13,000.
    // v3.6.0: post-estimation types skip this entirely -- they read matrices, not data.
    local tousename ""
    local nobs 0
    if !`_is_postest' {
    tempvar touse
    mark `touse' `if' `in'
    // v1.8.8: markout excludes obs with missing values in grouping/filter vars.
    // v1.9.1: skip markout for variables with showmissing set -- those obs
    //         are intentionally kept and labelled as "(Missing)" in the chart.
    // F-1c: varlist may be empty (pie Mode 3: no vars, over() = freq count)
    // v3.6.0-t2i (hardening): do NOT listwise-delete the plotted varlist for the
    // families whose missing outcomes are already excluded per-variable in Java
    // aggregation (see the header note: nomissing is a no-op for bar/line/area/
    // scatter/histogram/box/violin). An unconditional markout dropped every row
    // missing in ANY plotted var -> cross-series listwise bias, and it removed
    // line gaps / defeated spanmissing (Astra). markout is still applied for
    // cibar/ciline (their error-bars plugin cannot take null points) and whenever
    // the user explicitly asks for nomissing. Other types keep the safe default.
    local _mo_javamiss = inlist("`type'","bar","hbar","line","area","scatter") ///
        | inlist("`type'","histogram","boxplot","hbox","violin","hviolin") ///
        | inlist("`type'","stackedbar","stackedhbar","stackedbar100","stackedhbar100") ///
        | inlist("`type'","stackedline","stackedarea")
    if "`varlist'" != "" & ("`nomissing'" != "" | !`_mo_javamiss') markout `touse' `varlist'
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
    // F-0b: markout for all filter vars in _filters_list (pipe-separated)
    if "`_filters_list'" != "" & "`sm_filters'" == "0" {
        local _nfl = wordcount(subinstr("`_filters_list'", "|", " ", .))
        forvalues _mfi = 1/`_nfl' {
            local _mfv = word(subinstr("`_filters_list'", "|", " ", .), `_mfi')
            local _mftyp : type `_mfv'
            if substr("`_mftyp'", 1, 3) == "str" markout `touse' `_mfv', strok
            else                                   markout `touse' `_mfv'
        }
    }
    // F-1: markout for slider vars (always numeric, plain markout)
    if "`_sliders_list'" != "" {
        local _nsl = wordcount(subinstr("`_sliders_list'", "|", " ", .))
        forvalues _msi = 1/`_nsl' {
            local _msv = word(subinstr("`_sliders_list'", "|", " ", .), `_msi')
            markout `touse' `_msv'
        }
    }
    quietly count if `touse'
    if r(N) == 0 {
        display as error "No observations in sample"
        exit 2000
    }
    local nobs = r(N)
    // Pass the tempvar name so Java reads it via Data.getVarIndex()
    local tousename "`touse'"
    display as text "Building sparkta v`sparkta_version' with `nobs' observations..."

    // - Large dataset memory warning (v1.6.0) -
    // Java heap defaults vary by Stata version. If sparkta crashes with a
    // "Java heap space" error on large datasets, increase the heap using
    // set java_heapmax, then RESTART Stata for the change to take effect.
    //
    // Default Java heap sizes by Stata version (fix9u: Stata 15-16 lines dropped, sparkta needs 17+):
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
        display as text "    Stata 17-18 (default 512 MB): set java_heapmax 1024m"
        display as text "    Stata 19+   (default 4096 MB): unlikely to need change"
        display as text "  Restart Stata after changing. Check with: query java"
        display as text ""
    }
    } // end if !_is_postest (sample resolution + memory warning)
    else {
        // v3.6.0: post-estimation types -- no data scan needed
        display as text "Building sparkta v`sparkta_version' (post-estimation mode)..."
    }

    // - Stata-computed fit lines (v3.5.108) -----------------------------------
    // Computes fitted values using native Stata commands before calling Java.
    // Supports fit() alone (no over) and fit() with over() (per-group lines).
    //
    // Architecture:
    //   lfit/qfit : regress + predict xb + predict stdp for CI
    //   lowess    : lowess command directly (includes 3 bisquare robust iters)
    //   exp       : regress ln(y) on x, back-transform
    //   log       : regress y on ln(x)
    //   power     : regress ln(y) on ln(x), back-transform
    //   ma        : Java FitComputer (deterministic, no Stata command needed)
    //
    // Output encoding:
    //   No over():  "x1,y1|x2,y2|..."  (plain pipe-sep, backward compat)
    //   With over(): "Domestic=x,y|..~Foreign=x,y|.."
    //                Group key = value label (or raw value if no label).
    //                Tilde (~) separates groups; equals (=) separates key from pts.
    //                Java parseFitGroups() detects tilde to select path.
    //
    // Adaptive thinning: with G groups, _maxpts per group = max(200, 4000/G)
    //   so total arg string stays under Stata's ~64KB local macro limit.
    //
    // fitci with over() is blocked at validation (deferred feature).
    // -------------------------------------------------------------------------
    local _fitLineData  ""
    local _fitCiUpper   ""
    local _fitCiLower   ""

    if "`_fit'" != "" & "`_fit'" != "ma" {

        // B5 (t2j): the fit() paths below run internal regress/lowess commands that
        // overwrite the caller's active e(). Snapshot the caller's active estimates
        // now and restore them after the fit block so sparkta leaves e() as it found
        // it (caller-state hygiene). No active estimates -> capture fails, treated no-op.
        tempname _spk_fitest
        local _spk_fit_hasest = 0
        capture estimates store `_spk_fitest'
        if _rc == 0 local _spk_fit_hasest = 1

        local _yvar : word 1 of `varlist'
        local _xvar : word 2 of `varlist'

        // Determine if over() is active for per-group fits
        local _fit_has_over 0
        if "`over'" != "" local _fit_has_over 1

        // Adaptive thinning: cap the points so each fit string stays SHORT.
        // fix9g-c (2026-09-14, from a real 100,000-row run): a javacall argument longer
        // than roughly 5,000 characters is split by Stata into several arguments, so a
        // 4,000-point fit line of full-precision doubles (~150 KB) arrived in Java as
        // 140-point chunks spread over args 158-185 and every later argument shifted.
        // The cap is now a CHARACTER budget (_fit_budget) divided by the measured
        // characters per point (x and y at full macro precision), never the old 4000.
        local _fit_budget 3000
        local _cpp = strlen("`=`_xvar'[1]'") + strlen("`=`_yvar'[1]'") + 3
        local _cpp2 = strlen("`=`_xvar'[_N]'") + strlen("`=`_yvar'[_N]'") + 3
        if `_cpp2' > `_cpp' local _cpp = `_cpp2'
        if `_cpp' < 8 local _cpp 8
        local _maxpts = max(40, min(4000, floor(`_fit_budget' / `_cpp')))
        if `_fit_has_over' {
            quietly levelsof `over' if `touse', local(_over_vals)
            local _ngroups = wordcount(`"`_over_vals'"')
            if `_ngroups' > 1 {
                local _maxpts = max(20, floor(`_fit_budget' / (`_ngroups' * `_cpp')))
            }
        }

        // Single helper macro: builds "x,y|x,y|..." string from _fv
        // into local `_out_pts'. Used in both the over() and no-over() paths.
        // Reads: `_fv' (fitted values tempvar), `_xvar', `_maxpts', current data.
        // Writes: `_out_pts' (pipe-separated string), may be empty on failure.

        preserve

        quietly keep if `touse'

        if `_fit_has_over' {
            // -------------------------------------------------------------------
            // Per-group path: iterate over unique values of over() variable.
            // tempfile resets data between groups (no nested preserve/restore).
            // -------------------------------------------------------------------
            local _otyp : type `over'
            local _over_is_str = (substr("`_otyp'", 1, 3) == "str")

            // Save full touse subset once -- reload for each group
            tempfile _fit_base
            quietly save `_fit_base'

            foreach _oval in `_over_vals' {

                // Reload full touse subset for this group
                quietly use `_fit_base', clear

                // Subset to this group and get its display label for the key
                if `_over_is_str' {
                    quietly keep if `over' == "`_oval'"
                    local _okey "`_oval'"
                }
                else {
                    quietly keep if `over' == `_oval'
                    // Value label becomes the key -- matches DataSet.uniqueValues() in Java
                    local _okey : label (`over') `_oval'
                    // If no value label defined, key is the raw numeric string
                    if "`_okey'" == "" local _okey "`_oval'"
                }

                quietly count
                if r(N) < 3 continue        // skip groups too small for fit

                tempvar _fv _se _ci_u _ci_l _lny _lnx _lnfv

                // -- lfit ------------------------------------------------------
                if "`_fit'" == "lfit" {
                    quietly regress `_yvar' `_xvar'
                    quietly predict double `_fv', xb
                    if "`_fitci'" == "1" {
                        quietly predict double `_se', stdp
                        local _tcrit = invttail(e(df_r), (100-`cilevel')/200)
                        quietly gen double `_ci_u' = `_fv' + `_tcrit' * `_se'
                        quietly gen double `_ci_l' = `_fv' - `_tcrit' * `_se'
                    }
                }
                // -- qfit ------------------------------------------------------
                else if "`_fit'" == "qfit" {
                    quietly regress `_yvar' c.`_xvar'##c.`_xvar'
                    quietly predict double `_fv', xb
                    if "`_fitci'" == "1" {
                        quietly predict double `_se', stdp
                        local _tcrit = invttail(e(df_r), (100-`cilevel')/200)
                        quietly gen double `_ci_u' = `_fv' + `_tcrit' * `_se'
                        quietly gen double `_ci_l' = `_fv' - `_tcrit' * `_se'
                    }
                }
                // -- lowess ----------------------------------------------------
                else if "`_fit'" == "lowess" {
                    quietly lowess `_yvar' `_xvar', generate(`_fv') nograph
                    quietly recast double `_fv'
                }
                // -- exp -------------------------------------------------------
                else if "`_fit'" == "exp" {
                    quietly gen double `_lny' = ln(`_yvar') if `_yvar' > 0
                    quietly count if `_lny' != .
                    if r(N) >= 3 {
                        quietly regress `_lny' `_xvar'
                        quietly gen double `_fv' = exp(_b[_cons] + _b[`_xvar'] * `_xvar') ///
                            if `_yvar' > 0
                        if "`_fitci'" == "1" {
                            // CI on log scale: exp(lny_hat +/- t*se_lny)
                            quietly predict double `_lnfv', xb
                            quietly predict double `_se', stdp
                            local _tcrit = invttail(e(df_r), (100-`cilevel')/200)
                            quietly gen double `_ci_u' = exp(`_lnfv' + `_tcrit' * `_se') ///
                                if `_yvar' > 0
                            quietly gen double `_ci_l' = exp(`_lnfv' - `_tcrit' * `_se') ///
                                if `_yvar' > 0
                        }
                    }
                }
                // -- log -------------------------------------------------------
                else if "`_fit'" == "log" {
                    quietly gen double `_lnx' = ln(`_xvar') if `_xvar' > 0
                    quietly count if `_lnx' != .
                    if r(N) >= 3 {
                        quietly regress `_yvar' `_lnx'
                        quietly predict double `_fv' if `_xvar' > 0, xb
                        if "`_fitci'" == "1" {
                            // CI on y scale (linear model in ln(x)), symmetric
                            quietly predict double `_se', stdp
                            local _tcrit = invttail(e(df_r), (100-`cilevel')/200)
                            quietly gen double `_ci_u' = `_fv' + `_tcrit' * `_se' ///
                                if `_xvar' > 0
                            quietly gen double `_ci_l' = `_fv' - `_tcrit' * `_se' ///
                                if `_xvar' > 0
                        }
                    }
                }
                // -- power -----------------------------------------------------
                else if "`_fit'" == "power" {
                    quietly gen double `_lny' = ln(`_yvar') if `_yvar' > 0 & `_xvar' > 0
                    quietly gen double `_lnx' = ln(`_xvar') if `_yvar' > 0 & `_xvar' > 0
                    quietly count if `_lny' != .
                    if r(N) >= 3 {
                        quietly regress `_lny' `_lnx'
                        quietly gen double `_fv' = exp(_b[_cons]) * ///
                            (`_xvar'^_b[`_lnx']) if `_yvar' > 0 & `_xvar' > 0
                        if "`_fitci'" == "1" {
                            // CI on log-log scale: exp(lny_hat +/- t*se)
                            quietly predict double `_lnfv', xb
                            quietly predict double `_se', stdp
                            local _tcrit = invttail(e(df_r), (100-`cilevel')/200)
                            quietly gen double `_ci_u' = exp(`_lnfv' + `_tcrit' * `_se') ///
                                if `_yvar' > 0 & `_xvar' > 0
                            quietly gen double `_ci_l' = exp(`_lnfv' - `_tcrit' * `_se') ///
                                if `_yvar' > 0 & `_xvar' > 0
                        }
                    }
                }

                // Build this group's "x,y|x,y|..." string into _out_pts
                local _out_pts ""
                capture confirm variable `_fv'
                if !_rc {
                    quietly sort `_xvar', stable
                    quietly count if `_fv' != .
                    local _nvalid = r(N)
                    local _step 1
                    if `_nvalid' > `_maxpts' {
                        local _step = ceil(`_nvalid' / `_maxpts')   // fix9g-c: ceil keeps points <= _maxpts
                        if `_step' < 1 local _step 1
                    }
                    local _cnt 0
                    forvalues _i = 1/`=_N' {
                        if `_fv'[`_i'] == . continue
                        local _cnt = `_cnt' + 1
                        if mod(`_cnt' - 1, `_step') != 0 & `_cnt' != `_nvalid' continue   // fix9g-c: the last point is always kept
                        local _xi = `_xvar'[`_i']
                        local _yi = `_fv'[`_i']
                        if "`_out_pts'" == "" local _out_pts "`_xi',`_yi'"
                        else                  local _out_pts "`_out_pts'|`_xi',`_yi'"
                    }
                }

                // Append fit line to master string with group key
                if "`_out_pts'" != "" {
                    if "`_fitLineData'" == "" local _fitLineData "`_okey'=`_out_pts'"
                    else                      local _fitLineData "`_fitLineData'~`_okey'=`_out_pts'"
                }

                // CI bands per group (lfit/qfit + fitci only)
                // Same "GroupLabel=x,y|..~GroupLabel2=..." encoding as fitLineData.
                // Dataset layout with over()+fitci: stride=3 per group.
                //   dsets[G + gi*3 + 0] = ciUpper_gi
                //   dsets[G + gi*3 + 1] = ciLower_gi
                //   dsets[G + gi*3 + 2] = fitLine_gi
                if "`_fitci'" == "1" {
                    capture confirm variable `_ci_u'
                    if !_rc {
                        local _out_ci_u ""
                        local _out_ci_l ""
                        local _cnt 0
                        forvalues _i = 1/`=_N' {
                            if `_ci_u'[`_i'] == . | `_ci_l'[`_i'] == . continue
                            local _cnt = `_cnt' + 1
                            if mod(`_cnt' - 1, `_step') != 0 & `_cnt' != `_nvalid' continue   // fix9g-c: the last point is always kept
                            local _xi = `_xvar'[`_i']
                            local _ui = `_ci_u'[`_i']
                            local _li = `_ci_l'[`_i']
                            if "`_out_ci_u'" == "" {
                                local _out_ci_u "`_xi',`_ui'"
                                local _out_ci_l "`_xi',`_li'"
                            }
                            else {
                                local _out_ci_u "`_out_ci_u'|`_xi',`_ui'"
                                local _out_ci_l "`_out_ci_l'|`_xi',`_li'"
                            }
                        }
                        if "`_out_ci_u'" != "" {
                            if "`_fitCiUpper'" == "" {
                                local _fitCiUpper "`_okey'=`_out_ci_u'"
                                local _fitCiLower "`_okey'=`_out_ci_l'"
                            }
                            else {
                                local _fitCiUpper "`_fitCiUpper'~`_okey'=`_out_ci_u'"
                                local _fitCiLower "`_fitCiLower'~`_okey'=`_out_ci_l'"
                            }
                        }
                    }
                }
            }
            // end foreach _oval

        }
        else {
            // -------------------------------------------------------------------
            // No over(): single fit on full touse subset (unchanged from v3.5.108)
            // -------------------------------------------------------------------
            quietly count
            local _nfit = r(N)

            if `_nfit' >= 3 {

                tempvar _fv _se _ci_u _ci_l _lny _lnx _lnfv

                // -- lfit ------------------------------------------------------
                if "`_fit'" == "lfit" {
                    quietly regress `_yvar' `_xvar'
                    quietly predict double `_fv', xb
                    if "`_fitci'" == "1" {
                        quietly predict double `_se', stdp
                        local _tcrit = invttail(e(df_r), (100-`cilevel')/200)
                        quietly gen double `_ci_u' = `_fv' + `_tcrit' * `_se'
                        quietly gen double `_ci_l' = `_fv' - `_tcrit' * `_se'
                    }
                }
                // -- qfit ------------------------------------------------------
                else if "`_fit'" == "qfit" {
                    quietly regress `_yvar' c.`_xvar'##c.`_xvar'
                    quietly predict double `_fv', xb
                    if "`_fitci'" == "1" {
                        quietly predict double `_se', stdp
                        local _tcrit = invttail(e(df_r), (100-`cilevel')/200)
                        quietly gen double `_ci_u' = `_fv' + `_tcrit' * `_se'
                        quietly gen double `_ci_l' = `_fv' - `_tcrit' * `_se'
                    }
                }
                // -- lowess ----------------------------------------------------
                // recast double: lowess generate() stores float by default;
                // recast promotes in-place so local reads full precision. (v3.5.108)
                else if "`_fit'" == "lowess" {
                    quietly lowess `_yvar' `_xvar', generate(`_fv') nograph
                    quietly recast double `_fv'
                }
                // -- exp -------------------------------------------------------
                else if "`_fit'" == "exp" {
                    quietly gen double `_lny' = ln(`_yvar') if `_yvar' > 0
                    quietly count if `_lny' != .
                    if r(N) >= 3 {
                        quietly regress `_lny' `_xvar'
                        quietly gen double `_fv' = exp(_b[_cons] + _b[`_xvar'] * `_xvar') ///
                            if `_yvar' > 0
                        if "`_fitci'" == "1" {
                            quietly predict double `_lnfv', xb
                            quietly predict double `_se', stdp
                            local _tcrit = invttail(e(df_r), (100-`cilevel')/200)
                            quietly gen double `_ci_u' = exp(`_lnfv' + `_tcrit' * `_se') ///
                                if `_yvar' > 0
                            quietly gen double `_ci_l' = exp(`_lnfv' - `_tcrit' * `_se') ///
                                if `_yvar' > 0
                        }
                    }
                }
                // -- log -------------------------------------------------------
                else if "`_fit'" == "log" {
                    quietly gen double `_lnx' = ln(`_xvar') if `_xvar' > 0
                    quietly count if `_lnx' != .
                    if r(N) >= 3 {
                        quietly regress `_yvar' `_lnx'
                        quietly predict double `_fv' if `_xvar' > 0, xb
                        if "`_fitci'" == "1" {
                            quietly predict double `_se', stdp
                            local _tcrit = invttail(e(df_r), (100-`cilevel')/200)
                            quietly gen double `_ci_u' = `_fv' + `_tcrit' * `_se' ///
                                if `_xvar' > 0
                            quietly gen double `_ci_l' = `_fv' - `_tcrit' * `_se' ///
                                if `_xvar' > 0
                        }
                    }
                }
                // -- power -----------------------------------------------------
                else if "`_fit'" == "power" {
                    quietly gen double `_lny' = ln(`_yvar') if `_yvar' > 0 & `_xvar' > 0
                    quietly gen double `_lnx' = ln(`_xvar') if `_yvar' > 0 & `_xvar' > 0
                    quietly count if `_lny' != .
                    if r(N) >= 3 {
                        quietly regress `_lny' `_lnx'
                        quietly gen double `_fv' = exp(_b[_cons]) * ///
                            (`_xvar'^_b[`_lnx']) if `_yvar' > 0 & `_xvar' > 0
                        if "`_fitci'" == "1" {
                            quietly predict double `_lnfv', xb
                            quietly predict double `_se', stdp
                            local _tcrit = invttail(e(df_r), (100-`cilevel')/200)
                            quietly gen double `_ci_u' = exp(`_lnfv' + `_tcrit' * `_se') ///
                                if `_yvar' > 0 & `_xvar' > 0
                            quietly gen double `_ci_l' = exp(`_lnfv' - `_tcrit' * `_se') ///
                                if `_yvar' > 0 & `_xvar' > 0
                        }
                    }
                }

                // Build output string (plain format, no group key)
                capture confirm variable `_fv'
                if !_rc {
                    quietly sort `_xvar', stable
                    quietly count if `_fv' != .
                    local _nvalid = r(N)
                    local _step 1
                    if `_nvalid' > `_maxpts' {
                        local _step = ceil(`_nvalid' / `_maxpts')   // fix9g-c: ceil keeps points <= _maxpts
                        if `_step' < 1 local _step 1
                    }
                    local _fitLineData ""
                    local _cnt 0
                    forvalues _i = 1/`=_N' {
                        if `_fv'[`_i'] == . continue
                        local _cnt = `_cnt' + 1
                        if mod(`_cnt' - 1, `_step') != 0 & `_cnt' != `_nvalid' continue   // fix9g-c: the last point is always kept
                        local _xi = `_xvar'[`_i']
                        local _yi = `_fv'[`_i']
                        if "`_fitLineData'" == "" local _fitLineData "`_xi',`_yi'"
                        else                      local _fitLineData "`_fitLineData'|`_xi',`_yi'"
                    }

                    // CI bands (lfit/qfit only, no over())
                    if "`_fitci'" == "1" {
                        capture confirm variable `_ci_u'
                        if !_rc {
                            local _fitCiUpper ""
                            local _fitCiLower ""
                            local _cnt 0
                            forvalues _i = 1/`=_N' {
                                if `_ci_u'[`_i'] == . | `_ci_l'[`_i'] == . continue
                                local _cnt = `_cnt' + 1
                                if mod(`_cnt' - 1, `_step') != 0 & `_cnt' != `_nvalid' continue   // fix9g-c: the last point is always kept
                                local _xi = `_xvar'[`_i']
                                local _ui = `_ci_u'[`_i']
                                local _li = `_ci_l'[`_i']
                                if "`_fitCiUpper'" == "" {
                                    local _fitCiUpper "`_xi',`_ui'"
                                    local _fitCiLower "`_xi',`_li'"
                                }
                                else {
                                    local _fitCiUpper "`_fitCiUpper'|`_xi',`_ui'"
                                    local _fitCiLower "`_fitCiLower'|`_xi',`_li'"
                                }
                            }
                        }
                    }
                }
            }
        }
        // end if/else _fit_has_over

        restore
        // B5 (t2j): restore the caller's active estimates clobbered by the fit regress.
        if `_spk_fit_hasest' {
            capture estimates restore `_spk_fitest'
            capture estimates drop `_spk_fitest'
        }
    }

    // - Locate sparkta.jar (v3.5.42) -
    // Search order:
    //   1. findfile "sparkta.jar" -- direct jar search on adopath (most reliable)
    //   2. findfile "sparkta.ado" -- find ado dir, look for jar alongside it
    //   3. sysdir_PLUS/s/sparkta/ -- net install PLUS subfolder (explicit)
    //   4. sysdir_personal -- manual copy to personal dir
    //   5. c(pwd) -- dev / working directory fallback
    local jarpath ""

    // Store sysdir values in locals first -- avoids backtick/quote issues in foreach
    local _plus "`c(sysdir_PLUS)'"
    local _pers "`c(sysdir_personal)'"
    local _pwd  "`c(pwd)'"

    // --- Path 1: findfile sparkta.jar directly ---
    // Most reliable: findfile searches the full adopath for the jar itself.
    // net install places sparkta.jar on the adopath so this finds it directly.
    capture findfile "sparkta.jar"
    if !_rc {
        capture confirm file "`r(fn)'"
        if !_rc local jarpath "`r(fn)'"
    }

    // --- Path 2: findfile sparkta.ado, look for jar in same directory ---
    if "`jarpath'" == "" {
        capture findfile "sparkta.ado"
        if !_rc {
            local _ffdir = subinstr("`r(fn)'", "sparkta.ado", "", .)
            capture confirm file "`_ffdir'sparkta.jar"
            if !_rc local jarpath "`_ffdir'sparkta.jar"
        }
    }

    // --- Path 3: sysdir_PLUS/s/sparkta/ (net install PLUS subfolder) ---
    if "`jarpath'" == "" {
        capture confirm file "`_plus's\sparkta\sparkta.jar"
        if !_rc local jarpath "`_plus's\sparkta\sparkta.jar"
    }
    if "`jarpath'" == "" {
        capture confirm file "`_plus's/sparkta/sparkta.jar"
        if !_rc local jarpath "`_plus's/sparkta/sparkta.jar"
    }

    // --- Path 4: sysdir_personal (manual copy) ---
    if "`jarpath'" == "" {
        capture confirm file "`_pers'sparkta.jar"
        if !_rc local jarpath "`_pers'sparkta.jar"
    }

    // --- Path 5: c(pwd) (dev / working directory) ---
    if "`jarpath'" == "" {
        capture confirm file "`_pwd'\sparkta.jar"
        if !_rc local jarpath "`_pwd'\sparkta.jar"
    }
    if "`jarpath'" == "" {
        capture confirm file "`_pwd'/sparkta.jar"
        if !_rc local jarpath "`_pwd'/sparkta.jar"
    }

    if "`jarpath'" == "" {
        display as error "sparkta.jar not found. Searched:"
        display as error "  findfile sparkta.jar (adopath)"
        display as error "  findfile sparkta.ado -> same directory"
        display as error "  `_plus's\sparkta\sparkta.jar"
        display as error "  `_pers'sparkta.jar"
        display as error "  `_pwd'\sparkta.jar"
        display as error ""
        display as error "Fix: reinstall sparkta so sparkta.jar is on the adopath."
        display as error "  net install sparkta, from(https://raw.githubusercontent.com/fahad-mirza/sparkta_stata/main/ado/) replace"
        exit 198
    }

    // - Call Java -
    // Show which jar was found -- critical for diagnosing version mismatches
    display as text "  Using jar: `jarpath'"

    // t2j fix4: jar<->ado version handshake (ported from Astra rc1). Stata keeps ONE
    // long-lived JVM per session, so if the package is updated on disk but the JVM has
    // already loaded an older HtmlGenerator, rendering silently uses the stale backend.
    // VersionCheck reads the loaded HtmlGenerator.VERSION reflectively (not an inlined
    // constant) and compares it to this ado's version. A genuine mismatch returns 198;
    // we then re-run it noisily to show the specific ado/JAR versions and stop. Any other
    // rc (e.g. a jar so old it predates VersionCheck) is non-fatal here -- the main render
    // call below surfaces a real problem if one exists.
    capture javacall com.dashboard_test.VersionCheck execute, classpath("`jarpath'") args("`sparkta_version'")
    if _rc == 198 {
        capture noisily javacall com.dashboard_test.VersionCheck execute, classpath("`jarpath'") args("`sparkta_version'")
        exit 198
    }

    // v3.6.0: Post-estimation types -- show extracted data summary
    if `_is_postest' {
        display as text ""
        display as text "  Coefficients: `_pe_names'"
        // Debug: show pipe-sep data lengths (remove after debugging)
        local _dbg_nc : word count `=subinstr("`_pe_coefs'", "|", " ", .)'
        local _dbg_nl : word count `=subinstr("`_pe_lower'", "|", " ", .)'
        local _dbg_nu : word count `=subinstr("`_pe_upper'", "|", " ", .)'
        local _dbg_ns : word count `=subinstr("`_pe_ses'", "|", " ", .)'
        local _dbg_np : word count `=subinstr("`_pe_pvals'", "|", " ", .)'
        display as text "  Data check: coefs=`_dbg_nc' lower=`_dbg_nl' upper=`_dbg_nu' ses=`_dbg_ns' pvals=`_dbg_np'"
        if "`eform'" != "" | `rescale' != 1 {
            display as text "  Coefs: `_pe_coefs'"
            display as text "  Lower: `_pe_lower'"
            display as text "  Upper: `_pe_upper'"
            display as text "  SEs:   `_pe_ses'"
        }
    }

    capture noisily javacall com.dashboard_test.DashboardBuilder execute, ///
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
             `"`_filters_list'"'           ///  76 pipe-sep filter varlist (F-0b)
             `"`_pe_reflinewidth'"'         ///  77 reference-line width px (t2j fix8 A13; was reserved/empty)
             `"`is_nostats'"'              ///  78 suppress stats panel (v1.5)
             `"`cilevel'"'                 ///  79 CI level for cibar/ciline (v1.7)
             `"`cibandopacity'"'           ///  80 ciline band opacity 0-1 (v1.7.2)
             `"`bins'"'                    ///  81 histogram bin count (v1.8.0)
             `"`histtype'"'               ///  82 histogram y-axis: density|frequency|fraction (v1.8.0)
             `"`sm_over'"'                ///  83 showmissing for over() (v1.9.1)
             `"`sm_by'"'                  ///  84 showmissing for by() (v1.9.1)
             `"`sm_filters'"'             ///  85 showmissing for filters (F-0b)
             ""                             ///  86 reserved (was sm_filter2)
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
             `"`alabelfs'"' ///  145 label font size px (v3.5.0)
             `"`aellipse'"'       ///  146 ellipses "ymin xmin ymax xmax" pipe-sep (v3.5.0)
             `"`aellipsecolor'"'  ///  147 ellipse fill colors pipe-sep (v3.5.0)
             `"`aellipseborder'"' ///  148 ellipse border colors pipe-sep (v3.5.0)
             `"`alabelgap'"'      ///  149 label offset distance px (v3.5.2)
             `"`_rlbl'"'          ///  150 relabel over() groups on x-axis AND legend pipe-sep (v3.5.34)
             `"`_sliders_list'"'           ///  151 pipe-sep slider varlist: dual-handle numeric range (F-1)
             `"`_mlabel_var'"'             ///  152 mlabel varname: scatter marker label variable
             `"`_mlabel_all'"'             ///  153 mlabelall: 1=show all labels regardless of N
             `"`_mlabpos'"'               ///  154 mlabpos: minute-clock 0-59 for all labels
             `"`_mlabvpos'"'              ///  155 mlabvpos: per-obs minute-clock position variable
             `"`_fit'"'                    ///  156 fit type: lfit|qfit|lowess|exp|log|power|ma
             `"`_fitci'"'                  ///  157 fitci: 1=add CI band to lfit/qfit
             `"`_fitLineData'"'            ///  158 Stata-computed fit line: "x,y|x,y|..." sorted by x
             `"`_fitCiUpper'"'             ///  159 Stata-computed CI upper: "x,y|x,y|..."
             `"`_fitCiLower'"'             ///  160 Stata-computed CI lower: "x,y|x,y|..."
             /// - Post-estimation args (v3.6.0, indices 161-179) -
             `"`_pe_names'"'              ///  161 coefplot: pipe-sep coefficient display names
             `"`_pe_coefs'"'              ///  162 coefplot: pipe-sep coefficient values
             `"`_pe_lower'"'              ///  163 coefplot: pipe-sep CI lower bounds
             `"`_pe_upper'"'              ///  164 coefplot: pipe-sep CI upper bounds
             `"`_pe_ses'"'                ///  165 coefplot: pipe-sep standard errors
             `"`_pe_pvals'"'              ///  166 coefplot: pipe-sep p-values
             `"`_pe_estnames'"'           ///  167 coefplot: tilde-sep model names for multi-model
             `"`_pe_headings'"'           ///  168 coefplot: pipe-sep heading positions and texts
             `"`_pe_nobs'"'               ///  169 coefplot: sample size(s) tilde-sep for multi-model
             `"`_pe_depvar'"'             ///  170 coefplot: dependent variable name
             `"`_pe_orient'"'             ///  171 coefplot: v=vertical (default) h=horizontal
             `"`_pe_coefst'"'             ///  172 shared: scatter or bar
             `"`_pe_cist'"'               ///  173 shared: whisker | band | bar
             `"`_pe_pstyles'"'            ///  174 shared: space-sep per-model point styles
             `"`_mp_data'"'               ///  175 marginsplot: group-aware pipe-sep margins data
             `"`_mp_upper'"'              ///  176 marginsplot: group-aware pipe-sep CI upper
             `"`_mp_lower'"'              ///  177 marginsplot: group-aware pipe-sep CI lower
             `"`_mp_xlab'"'               ///  178 marginsplot: x-axis label
             `"`_mp_ylab'"'              ///  179 marginsplot: y-axis label
             `"`_pe_bases'"'             ///  180 coefplot: pipe-sep base/omitted variable names
             `"`_pe_tstat'"'             ///  181 coefplot: 1=t-distribution 0=z-distribution
             `"`_pe_cmd'"'              ///  182 coefplot: estimation command name
             `"`_noci_flag'"'           ///  183 post-estimation: 1=suppress CIs (v3.6.0)
             `"`_pe_tzvals'"'              ///  184 coefplot: pipe-sep pre-computed t/z statistics (v3.6.0)
             `"`_pe_refval'"'              ///  185 coefplot: reference line value or "none" (v3.6.0-s6)
             `"`_pe_estlabels'"'           ///  186 coefplot: tilde-sep custom legend labels (v3.6.0-s6)
             `"`_pe_cicolors'"'            ///  187 coefplot: pipe-sep per-model CI colors (v3.6.0-s6)
             `"`_pe_ciwidth'"'              ///  188 coefplot: CI line/band width px (v3.6.0-s6)
             `"`c(pwd)'"'                 ///  189 Stata working directory for relative export paths
             `_pe_connected'              ///  190 coefplot/eventstudy: connected line flag
             `"`_pe_fitstats'"'           ///  191 coefplot: tilde-sep pipe-sep fit stats per model (v3.6.0-s6c)
             `"`_pe_indicators'"'         ///  192 table export: tilde-sep indicator rows "label|v1|v2" (v3.6.0-s6c)
             `"`_pe_addstats'"'           ///  193 table export: tilde-sep addstats rows (v3.6.0-s6c)
             `"`_pe_varlabels'"'          ///  194 coefplot: auto variable labels varname|||label~~~... (v3.6.0)
             `"`_pe_customlabels'"'        ///  195 coefplot: custom labels from coeflabels() varname|||label~~~... (v3.6.0)
             `"`pexline'"'        ///  196 pexline: vertical reference line for coefplot/eventstudy (v3.6.0-s7d)
             `"`_pe_lower2'"'     ///  197 levels(): inner CI lower bounds pipe-sep (v3.6.0-s7d)
             `"`_pe_upper2'"'     ///  198 levels(): inner CI upper bounds pipe-sep (v3.6.0-s7d)
             `"`_lev1'"'        ///  199 levels(): inner CI level value e.g. "90" (v3.6.0-s7d)
             `_notimestamp'              ///  200 notimestamp (v3.6.0-s7d)
             `_collapsestats'            ///  201 collapsestats (v3.6.0-s7d)
             `_noallfilter'              ///  202 noallfilter (v3.6.0-s7d)
             `"`_mp_series'"'            ///  203 marginsplot: series labels (v3.6.0-s8a)
             `"`_mp_xpos'"'            ///  204 last marginsplot arg
             `"`_plotmargin'"'               ///  205 plotmargin "l r b t" percent (v3.6.0-s8p)
             `"`_yfree'"'                   ///   206 yfree flag (v3.6.0-s9g)
             `"`_pe_xpos'"'                 ///   207 eventstudy relative times, tilde/pipe (v3.6.0-t2d; empty = category axis)
             `"`_es_opts'"'                 ///   208 eventstudy reference period / together (v3.6.0-t2f)
             `"`_tbl_opts'"')               //   209 publication-table options (v3.6.0-t2h, batch 2a)
    if _rc == 5100 {
        display as error ""
        display as error "  SPARKTA ERROR: Java class not found."
        display as error "  Your installed sparkta.jar is outdated or from a different version."
        display as error ""
        display as error "  Fix: copy the new sparkta.jar to your Stata personal ado folder."
        display as error "    Windows: %APPDATA%\Stata\ado\personal\"
        display as error "    Mac/Linux: ~/ado/personal/"
        display as error ""
        display as error "  Or reinstall from GitHub:"
        display as error `"    net install sparkta, from("https://raw.githubusercontent.com/fahad-mirza/sparkta_stata/main/ado/") replace"'
        exit 5100
    }
    else if _rc {
        exit _rc
    }

    // ---- v3.6.0-s9p: saveas(): PDF / SVG / PNG straight from Stata -----------------
    // Drives the exported page with a headless Chromium (Edge ships with Windows; Chrome
    // or Edge on Mac/Linux). PDF: --print-to-pdf (charts become vector through the page's
    // own print swap; ", table" prints only the coefficient table). PNG: --screenshot.
    // SVG: the page runs its own exporter under ?export=svg and hands the SVG back via
    // --dump-dom; com.dashboard_test.tools.SvgExtract writes the .svg file.
    if `"`saveas'"' != "" {
        if `"`export'"' == "" {
            display as error "saveas() requires export(): the headless browser renders the exported HTML"
            exit 198
        }
        local _sa_scope "chart"     // s9x: figures by default; ", page" for the whole page, ", table" for the table
        local _sa_close 0
        local _sa_idle ""
        local _sa_scale "2"         // s9x: PNG device scale (2 = 2x; use scale(#) for 3 or 4)
        local _sa_list `"`saveas'"'
        if strpos(`"`_sa_list'"', ",") > 0 {
            local _sa_opts = lower(trim(substr(`"`_sa_list'"', strpos(`"`_sa_list'"', ",") + 1, .)))
            local _sa_list  = trim(substr(`"`_sa_list'"', 1, strpos(`"`_sa_list'"', ",") - 1))
            // suboptions: page | table | chart,  scale(#),  close,  idle(#seconds)
            if regexm("`_sa_opts'", "scale\(([0-9.]+)\)") {
                local _sa_scale = regexs(1)
                local _sa_opts = trim(subinstr("`_sa_opts'", regexs(0), "", .))
            }
            local _sa_close 0
            if regexm("`_sa_opts'", "(^| )close($| )") {
                local _sa_close 1
                local _sa_opts = trim(subinstr("`_sa_opts'", "close", "", .))
            }
            local _sa_idle ""
            if regexm("`_sa_opts'", "idle\(([0-9.]+)\)") {
                local _sa_idle = regexs(1)
                local _sa_opts = trim(subinstr("`_sa_opts'", regexs(0), "", .))
            }
            local _sa_opts = trim(itrim("`_sa_opts'"))
            if "`_sa_opts'" != "" {
                local _sa_scope "`_sa_opts'"
                if !inlist("`_sa_scope'", "page", "table", "chart") {
                    display as error "saveas(): scope must be chart (default), page or table -- got `_sa_scope'"
                    exit 198
                }
            }
        }
        local _br ""
        if `"`browser'"' != "" {
            capture confirm file `"`browser'"'
            if _rc {
                display as error `"browser(): file not found: `browser'"'
                exit 601
            }
            local _br `"`browser'"'
        }
        else {
            sparkta_find_browser
            local _br `"`r(browser)'"'
        }
        if `"`_br'"' == "" {
            display as error "saveas(): no Chromium-based browser found (Microsoft Edge or Google Chrome)."
            display as error `"  Tell sparkta where it is:  browser("C:\Program Files\Google\Chrome\Application\chrome.exe")"'
            display as error `"  or once per session:       global SPARKTA_BROWSER "...\chrome.exe""'
            display as error "  Run  sparkta, findbrowser  to see which paths were checked."
            exit 198
        }
        // absolute path -> file:// URL
        local _html `"`export'"'
        if substr(`"`_html'"', 2, 1) != ":" & substr(`"`_html'"', 1, 1) != "/" local _html `"`c(pwd)'/`_html'"'
        local _html = subinstr(`"`_html'"', "\", "/", .)
        // s9y: FAST PATH -- if sparkta-export.jar (built by build.bat from fetch_js_libs.bat's
        // Java libraries) sits next to sparkta.jar, get the SVG once from the browser and let
        // Java make the PNG (any scale) and the vector PDF from it: one browser launch total.
        local _expjar = subinstr(`"`jarpath'"', "sparkta.jar", "sparkta-export.jar", 1)
        capture confirm file `"`_expjar'"'
        local _fast = (_rc == 0) & ("`_sa_scope'" == "chart")
        local _fast_svg ""
        local _sl "/"   // "file:" + three slashes -- never write /// inside a string (Stata continuation)
        local _url `"file:`_sl'`_sl'`_sl'`_html'"'
        local _base "--headless=new --disable-gpu --no-sandbox --hide-scrollbars --log-level=3 --virtual-time-budget=8000"
        if "`_saveas_forced_offline'" == "1" display as text "  saveas: online ignored -- the page is written self-contained so export works without a network"
        if `_fast' {
            // the SVG, obtained ONCE and reused for every format in this call.
            // t1b: first try the session browser (one headless Edge kept alive across
            // sparkta calls, driven over DevTools: ~0.3 s); fall back to a fresh
            // headless run with --dump-dom if that fails for any reason.
            tempfile _dumpf0 _svgf0
            local _dumpx = subinstr(`"`_dumpf0'"', "\", "/", .) + ".htm"
            local _fast_svg = subinstr(`"`_svgf0'"', "\", "/", .) + ".svg"
            capture noisily javacall com.dashboard_test.tools.HeadlessBrowser execute, classpath("`jarpath'") args("svg" `"`_br'"' `"`_html'"' `"`_fast_svg'"')
            if _rc {
                display as text "  saveas: session browser unavailable (rc=`=_rc') -- using a one-off headless run"
                sparkta_browser_run `"`jarpath'"' `"`_dumpx'"' `"`_br'"' `_base' --dump-dom `"`_url'?export=svg"'
                capture noisily javacall com.dashboard_test.tools.SvgExtract execute, classpath("`jarpath'") args(`"`_dumpx'"' `"`_fast_svg'"')
                if _rc {
                    display as error "saveas(): could not obtain the SVG for the fast path"
                    exit 198
                }
            }
        }
        // IO3 (t2j): saveas() may name several files separated by spaces
        // (saveas("fig.pdf fig.png")) AND each name may itself contain spaces
        // (saveas("my chart.pdf")). A plain `foreach of local' splits on every space,
        // corrupting names with spaces. Rebuild the list by accumulating words until
        // one ends in a known extension (.pdf/.png/.svg); that accumulated string is
        // one filename. A trailing remainder with no known extension becomes one file
        // too (it hits the unsupported-extension error below, as before).
        local _sa_nf 0
        local _sa_acc ""
        foreach _tok of local _sa_list {
            local _sa_acc = trim(`"`_sa_acc' `_tok'"')
            local _sa_e = lower(substr(`"`_sa_acc'"', -4, 4))
            if inlist("`_sa_e'", ".pdf", ".png", ".svg") {
                local ++_sa_nf
                local _saf`_sa_nf' `"`_sa_acc'"'
                local _sa_acc ""
            }
        }
        if `"`_sa_acc'"' != "" {
            local ++_sa_nf
            local _saf`_sa_nf' `"`_sa_acc'"'
        }
        forvalues _sfi = 1/`_sa_nf' {
            local _sf `"`_saf`_sfi''"'
            local _ext = lower(substr(`"`_sf'"', -3, 3))
            local _out `"`_sf'"'
            if substr(`"`_out'"', 2, 1) != ":" & substr(`"`_out'"', 1, 1) != "/" local _out `"`c(pwd)'/`_out'"'
            // IO1 (t2j fix4): export to a TEMPFILE first, verify it, then atomically move it
            // onto the destination (FileCopy) -- so a failed headless export can never destroy
            // a previously-good file. This replaces the old "erase the target first, write
            // straight to it" flow (which lost the prior file on any browser crash). Paired
            // with sparkta_browser_run now propagating the browser's rc.
            tempfile _sa_tmp
            local _sa_tmpf = subinstr(`"`_sa_tmp'"', "\", "/", .) + ".`_ext'"
            local _sa_rc = 0
            if `_fast' & inlist("`_ext'", "pdf", "png") {
                capture noisily javacall com.dashboard_test.export.SvgConvert execute, classpath("`_expjar'") args(`"`_fast_svg'"' `"`_sa_tmpf'"' "`_sa_scale'" "white")
                local _sa_rc = _rc
            }
            else if "`_ext'" == "pdf" {
                // viewport close to the printable width so nothing is scaled down; landscape figure by default
                local _ws = cond("`_sa_scope'" == "chart", "--window-size=1100,700", "--window-size=820,1100")
                capture noisily sparkta_browser_run `"`jarpath'"' "" `"`_br'"' `_base' `_ws' --no-pdf-header-footer `"--print-to-pdf=`_sa_tmpf'"' `"`_url'?print=`_sa_scope'"'
                local _sa_rc = _rc
            }
            else if "`_ext'" == "png" {
                // t2j fix9d: FULL-PAGE PNG through the session browser (DevTools capture of the
                // laid-out page at scale(#), any scope) -- the old --screenshot run captured the
                // 1100x700 window only, so a post-estimation page lost its elements key and a
                // by() grid its lower panels whenever sparkta-export.jar was absent (Mac/Linux,
                // every SSC install). The one-off screenshot run stays as the fallback.
                capture noisily javacall com.dashboard_test.tools.HeadlessBrowser execute, classpath("`jarpath'") args("png" `"`_br'"' `"`_html'"' `"`_sa_tmpf'"' "`_sa_scale'" "`_sa_scope'")
                local _sa_rc = _rc
                if `_sa_rc' {
                    display as text "  saveas: session browser unavailable for png (rc=`_sa_rc') -- using a one-off headless screenshot (viewport only)"
                    capture noisily sparkta_browser_run `"`jarpath'"' "" `"`_br'"' `_base' --window-size=1100,700 --force-device-scale-factor=`_sa_scale' `"--screenshot=`_sa_tmpf'"' `"`_url'?print=`_sa_scope'"'
                    local _sa_rc = _rc
                }
            }
            else if "`_ext'" == "svg" & `"`_fast_svg'"' != "" {
                capture copy `"`_fast_svg'"' `"`_sa_tmpf'"', replace
                local _sa_rc = _rc
            }
            else if "`_ext'" == "svg" {
                tempfile _dump
                local _dumpf = subinstr(`"`_dump'"', "\", "/", .) + ".htm"
                capture noisily sparkta_browser_run `"`jarpath'"' `"`_dumpf'"' `"`_br'"' `_base' --dump-dom `"`_url'?export=svg"'
                if !_rc {
                    capture noisily javacall com.dashboard_test.tools.SvgExtract execute, classpath("`jarpath'") args(`"`_dumpf'"' `"`_sa_tmpf'"')
                }
                local _sa_rc = _rc
            }
            else {
                display as error "saveas(): unsupported extension .`_ext' in `_sf' (use .pdf .svg .png)"
                exit 198
            }
            if `_sa_rc' {
                display as error "saveas(): `_sf' was not written (export failed, rc=`_sa_rc'); the existing file, if any, was left unchanged"
                exit 198
            }
            // the export must have produced a real, non-empty tempfile before we replace _out
            capture confirm file `"`_sa_tmpf'"'
            if _rc {
                display as error "saveas(): `_sf' was not written (no output produced); the existing file, if any, was left unchanged"
                exit 198
            }
            capture noisily javacall com.dashboard_test.tools.FileCopy execute, classpath("`jarpath'") args(`"`_sa_tmpf'"' `"`_out'"')
            if _rc {
                display as error "saveas(): could not place `_sf' (rc=`=_rc'); the existing file, if any, was left unchanged"
                exit 198
            }
            capture erase `"`_sa_tmpf'"'
            display as text "  saveas: `_sf'"
        }
        // t1h: session-browser lifetime overrides. Default: it stays warm and closes itself
        // after 2 minutes idle (and on exit) -- the user never waits. close = quit right
        // now; idle(#) = seconds of inactivity before it quits, for this session.
        if "`_sa_idle'" != "" capture javacall com.dashboard_test.tools.HeadlessBrowser execute, classpath("`jarpath'") args("idle" "`_sa_idle'")
        if `_sa_close' {
            capture javacall com.dashboard_test.tools.HeadlessBrowser execute, classpath("`jarpath'") args("close")
            display as text "  saveas: session browser closed"
        }
    }

end
