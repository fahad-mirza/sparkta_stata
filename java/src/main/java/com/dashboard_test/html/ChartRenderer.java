package com.dashboard_test.html;

import com.dashboard_test.data.*;
import java.util.*;

/**
 * ChartRenderer -- all chart-type rendering and chart configuration helpers.
 * v2.0.0: Extracted from HtmlGenerator.java as part of modular refactor.
 * v3.5.0: Phase 2-B reference annotation support (buildAnnotationConfig).
 * v3.5.10: Auto axis titles from variable label/name in barLine(), boxPlot(),
 *          violinChart() -- falls back to getDisplayName() when xtitle/ytitle
 *          not supplied, matching Stata graph command convention.
 *
 * Responsibility: given a DataSet and options, produce the Chart.js script
 * string for any supported chart type. Also owns shared chart helpers:
 * axis config, legend config, dataset style, note/caption, padding.
 *
 * TO ADD A NEW CHART TYPE:
 *   1. Add case "typename": to the switch in buildChartScript()
 *   2. Add typename() method in the clearly-labelled section below
 *   3. Add typenameFilterData() if the type supports filter()
 *   4. Add "typename" to valid_types in dashboard.ado
 *   No other files need to change.
 *
 * Supported types: bar, line, scatter, bubble, pie, donut,
 *                  cibar, ciline, histogram, hbar,
 *                  stackedbar, stackedline, area, stackedarea
  *
 * v3.6.0-s8a  marginsPlot(): category + numeric x (from r(at)); CI plugins mpW/mpA;
 *             nested CI mpW_L2; inline legend mpLegend; subtitle set in DashboardBuilder.
 * v3.6.0-s8b  marginsPlot(): rich tooltip (_mpTip, CI in hover, "Not estimable");
 *             inner CI per-series and numericX-aware; legend sized via measureText;
 *             x-extrapolation removed; null guards for missing x/y; numOrNull().
 * v3.6.0-s8c  coefPlot/coefPlotMulti value-axis titles; _cpTip via numOrNull (base
 *             levels); cpMDot colour scope fix; marginsPlot solid marks;
 *             HtmlGenerator: viridis reversed on dark themes.
 * v3.6.0-s8d  coefPlotMulti: hasLevels2M requires numeric tokens ("~~" is not a CI);
 *             estLabels sized to nModels; lo2/hi2 lookups bounds-safe. (rc=5101 fix)
 * v3.6.0-s8f  legendLabelsCfg: filter out CI helper datasets (upper/lower) from legends.
 */
class ChartRenderer {

    private final DashboardOptions o;
    private final HtmlGenerator    gen;  // shared utilities
    private final DatasetBuilder   dsb;  // dataset/label builders

    /** s9j: the HTML elements key now lists CI levels for every chart, so the three canvas-drawn
     *  nested-CI keys are off. (Trade-off, noted: PNG/SVG export does not include the HTML key.) */
    static final boolean CANVAS_CI_KEY = false;
    /** s9j: register a drawn element for the page key (role, label, glyph kind, colour, dash JS, point style). */
    void key(String role, String label, String kind, String color, String dash, String ps) {
        o._keyItems.add(new String[]{ role, label, kind, color == null ? "" : color, dash == null ? "" : dash, ps == null ? "" : ps });
    }

    // -- t2j fix8w (P4 / U10): significance markers ------------------------------------------
    /** Coefficient markers are FILLED when p <= SIG_ALPHA and HOLLOW otherwise (p missing, e.g. a
     *  base level, counts as not significant). Same threshold on coefplot (single, multi-model,
     *  every CI style) and the event study; the elements key explains the hollow marker. */
    static final double SIG_ALPHA = 0.10;
    static final String SIG_KEY_LABEL = "Hollow marker: p > 0.1";
    /** t2j fix8x: coefstyle(bar) encodes significance on the bar fill instead (no marker drawn). */
    static final String SIG_BAR_KEY_LABEL = "Hollow bar: p > 0.1";
    /** JS array "[1,0,...]": 1 = filled, 0 = hollow, one entry per coefficient (null-safe). */
    private static String sigArr(String[] pvals, int k) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < k; i++) { if (i > 0) b.append(","); b.append(sigFlag(pvals, i)); }
        return b.append("]").toString();
    }
    private static int sigFlag(String[] pvals, int i) {
        String v = numOrNull(pvals, i);
        if (v.equals("null")) return 0;
        try { return Double.parseDouble(v) <= SIG_ALPHA ? 1 : 0; } catch (NumberFormatException e) { return 0; }
    }
    /** fix9u (decision 5): hollow markers are TRANSPARENT like Stata's msymbol(Oh) -- grid lines
     *  and CI bars show through the ring. Until fix9t the ring was filled with the plot background.
     *  rgba(0,0,0,0) rather than 'transparent': canvas2svg emits it as fill-opacity="0", which
     *  every SVG/PDF consumer understands. */
    static final String HOLLOW_FILL = "rgba(0,0,0,0)";
    /** Emitted once per page (idempotent): draws one marker dot, filled or hollow. A hollow dot
     *  is a 2 px ring with NO fill (fix9u; _spkPlotBg kept for other callers). */
    static String sigDotJs() {
        return "if(!window._spkSigDot){"
            + "window._spkPlotBg=function(chart){try{var el=chart.canvas.closest('.chart-wrapper')||chart.canvas.parentElement;"
            + "var b=getComputedStyle(el).backgroundColor;if(b&&b!=='rgba(0, 0, 0, 0)'&&b!=='transparent')return b;}catch(e){}return '#ffffff';};"
            + "window._spkSigDot=function(chart,x,y,r,c,solid,ring){var ctx=chart.ctx;ctx.beginPath();ctx.arc(x,y,r,0,2*Math.PI);"
            + "if(solid){ctx.fillStyle=c;ctx.fill();if(ring){ctx.strokeStyle='rgba(255,255,255,0.85)';ctx.lineWidth=1.5;ctx.stroke();}}"
            + "else{ctx.strokeStyle=c;ctx.lineWidth=2;ctx.stroke();}};"
            // legend swatches: with usePointStyle Chart.js copies point 0's fill, which may be the
            // hollow fill -- datasets carry _legendFill (their series colour) and this restores it
            + "window._spkSigLegend=function(chart){var items=Chart.defaults.plugins.legend.labels.generateLabels(chart);"
            + "items.forEach(function(it){var d=chart.data.datasets[it.datasetIndex];if(d&&d._legendFill){it.fillStyle=d._legendFill;it.strokeStyle=d._legendFill;}});return items;};}\n";
    }
    /** JS array of per-point fill colours for real Chart.js points: the series colour when
     *  filled, transparent when hollow (fix9u; same look as the plugin-drawn dots). */
    private String sigFillArr(String[] pvals, int k, String color) {
        String bg = HOLLOW_FILL;
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < k; i++) { if (i > 0) b.append(","); b.append("'").append(sigFlag(pvals, i) == 1 ? color : bg).append("'"); }
        return b.append("]").toString();
    }
    /** s9c: smallest positive value on the axis being configured (set by the renderer before buildAxisConfig). */
    Double logDataMin = null;

    // -- fix9g: large-data mode for scatter/bubble ------------------------------------------
    // Above BIG_SCATTER_MIN points on one chart (summed over over() groups; a by() panel is
    // its own chart) the page switches to large-data mode, automatically and with no ado
    // option: the datasets are flagged _spkBig:true (the spkBigScatter page plugin draws the
    // point cloud ONCE onto an offscreen bitmap and blits it on every render instead of
    // stroking every arc on every hover frame) and the chart's load animation is off. Data,
    // tooltip, mlabel(), fit lines, CI bands, filters and exports are unchanged (export
    // clones draw the real points).
    // Draw order is KEPT (data order). The handover proposed sorting the points by x with
    // normalized:true, but the oracle showed that changes the picture: the default marker
    // fill is translucent (alpha 0.85, 0.1-alpha border), so in a dense cloud the topmost
    // points decide the look, and x-sorted drawing put every column's largest-x points on
    // top -- vertical streaks and a darker cloud (197,943 of 1,084,860 pixels differed,
    // max channel delta 141). With data order the bitmap is pixel-identical to the old
    // render; the normalized flag would only skip Chart.js' sortedness scan (milliseconds).
    static final int BIG_SCATTER_MIN = 20000;
    /** true while scatter()/bubble() build the datasets of a large chart. */
    private boolean bigMode = false;
    /** optional row order for the point loops (null = data order, the shipped choice). */
    private int[] bigOrder = null;
    private boolean bigNoted = false;

    /** Number of rows with both x and y present (the points a scatter/bubble draws). */
    static int countPairs(Variable xv, Variable yv) {
        int n = Math.min(xv.size(), yv.size()), c = 0;
        for (int i = 0; i < n; i++) if (xv.getValues().get(i) != null && yv.getValues().get(i) != null) c++;
        return c;
    }
    /** Turns large-data mode on for the chart being built when it has more than BIG_SCATTER_MIN points. */
    private void bigBegin(Variable xv, Variable yv) {
        int n = countPairs(xv, yv);
        bigMode = n > BIG_SCATTER_MIN;
        bigOrder = null;   // see the note on draw order above: data order is kept
        if (bigMode && !bigNoted) {
            bigNoted = true;
            // by() pages: the note names the first panel that crossed the threshold
            String where = o.by.isEmpty() ? "" : " in a panel";
            try { com.stata.sfi.SFIToolkit.displayln("  large-data mode: " + String.format(Locale.ROOT, "%,d", n)
                + " points" + where + " drawn once, no animation (hover and export unchanged)"); } catch (Throwable ignore) {}
        }
    }
    private void bigEnd() { bigMode = false; bigOrder = null; }
    /** Dataset properties emitted on every point dataset of a large chart (trailing comma). */
    private String bigDsProps() { return bigMode ? "_spkBig:true," : ""; }
    /** The chart-level animation block: off in large-data mode. */
    private String animBlock(String easingCfg, String delayCfg) {
        return bigMode ? "    animation:false,\n" : "    animation:{duration:"+animDuration()+easingCfg+delayCfg+"},\n";
    }
    static Double minPositive(Variable v) {
        if (v == null) return null; Double m = null;
        for (Object x : v.getValues()) if (x instanceof Number) { double d = ((Number) x).doubleValue(); if (d > 0 && (m == null || d < m)) m = d; }
        return m;
    }

    ChartRenderer(DashboardOptions o, HtmlGenerator gen, DatasetBuilder dsb) {
        this.o   = o;
        this.gen = gen;
        this.dsb = dsb;
    }

    // Delegate shared utilities
    private String escJs(String s)              { return gen.escJs(s); }
    private String escHtml(String s)            { return gen.escHtml(s); }
    private String sdz(String s)               { return gen.sdz(s); }
    private String col(int i)                  { return gen.col(i); }
    private String colS(int i)                 { return gen.colS(i); }
    private String toRgba(String c, double a)  { return gen.toRgba(c, a); }
    private String labelColor()                { return gen.labelColor(); }
    private String gridCssColor()              { return gen.gridCssColor(); }
    private String resolve(String v, String d) { return gen.resolve(v, d); }

    // Delegate additional utilities
    private String animDuration()                              { return gen.animDuration(); }
    private double parseDouble(String s, double def)          { return gen.parseDouble(s, def); }
    private double[][] computeBins(List<Double> v, int n)     { return dsb.computeBins(v, n); }
    private int sturgesBins(int n)                            { return dsb.sturgesBins(n); }

    // Delegate dataset/label builders
    private String overLabels(DataSet d)                           { return dsb.overLabels(d); }
    private String overDatasets(DataSet d, boolean l, boolean log) { return dsb.overDatasets(d, l, log); }
    private String catLabels(DataSet d)                            { return dsb.catLabels(d); }
    private String numDatasets(DataSet d, boolean l, boolean log)  { return dsb.numDatasets(d, l, log); }
    private String overDatasets100(DataSet d)                      { return dsb.overDatasets100(d); }
    private String boxplotDatasets(DataSet d)                      { return dsb.boxplotDatasets(d); }
    private String boxplotDatasets(DataSet d, boolean vln)         { return dsb.boxplotDatasets(d, vln); }
    private String boxplotLabels(DataSet d)                        { return dsb.boxplotLabels(d); }
    private String violinData(DataSet d)                           { return dsb.violinData(d); }
    private String violinLabels(DataSet d)                         { return dsb.violinLabels(d); }
    private String aggLabels(DataSet d)                            { return dsb.aggLabels(d); }
    private String buildDatasetStyle(int ci, boolean l, Variable v, int si) { return dsb.buildDatasetStyle(ci, l, v, si); }
    private String buildPointConfig(int ci)                        { return dsb.buildPointConfig(ci); }
    private String statSuffix(String s)                            { return dsb.statSuffix(s); }
    private String ciBarLabels(DataSet d)                          { return dsb.ciBarLabels(d); }
    private String ciBarDatasets(DataSet d)                        { return dsb.ciBarDatasets(d); }
    private String ciLineDatasets(DataSet d)                       { return dsb.ciLineDatasets(d); }
    private List<Object[]> ciBarValidGroups(DataSet d)             { return dsb.ciBarValidGroups(d); }
    private List<String> parseCustomLabels(String r)               { return dsb.parseCustomLabels(r); }
    private String customLabelsJs(List<String> l)                  { return dsb.customLabelsJs(l); }

    String buildChartScript(String id, DataSet data) {
        return buildChartScript(id, data, false);
    }

    String buildChartScript(String id, DataSet data, boolean assignToMain) {
        String chartVar = assignToMain ? "var _mainChart=" : "";
        String script;
        switch (o.type) {
            case "line":      script = barLine(id, data, true);   break;
            case "scatter":   script = scatter(id, data);          break;
            case "bubble":    script = bubble(id, data);           break;
            case "pie":       script = pie(id, data, false);       break;
            case "donut":     script = pie(id, data, true);        break;
            case "cibar":     script = ciBar(id, data);            break;
            case "ciline":    script = ciLine(id, data);           break;
            case "histogram": script = histogram(id, data);        break;
            case "boxplot":   script = boxPlot(id, data, false);   break;
            case "violin":    script = violinChart(id, data);       break;
            case "coefplot":  script = coefPlot(id);               break;
            case "eventstudy": script = o.chart.peXpos.isEmpty() ? coefPlot(id) : eventStudy(id); break;   // t2d: numeric event-study when times parsed
            case "marginsplot": script = marginsPlot(id);           break;  // v3.6.0-s8a
            default:          script = barLine(id, data, false);   break;
        }

        // v3.2.0: Phase 1-E -- prepend gradient variable preamble when gradient=true.
        // Applies to bar and area (line+fill) charts only. Skipped for all others.
        // The preamble creates window._grad{i} canvas gradient objects that dataset
        // backgroundColor references use instead of static color strings.
        if (!o.style.gradient.isEmpty()) {
            boolean gradApplies = o.type.equals("bar")
                || o.type.equals("area") || o.type.equals("stackedarea")
                || (o.type.equals("line") && o.chart.fill);
            if (gradApplies) {
                int nSeries = data.getNumericVariables().size();
                // v3.2.1: colorByCategory path (single var + over()) puts one color
                // per BAR using _grad0.._gradN per group, not per dataset.
                boolean colorByCategory = (nSeries == 1) && data.hasOver();
                if (colorByCategory && data.getOverVariable() != null) {
                    List<String> grps = DataSet.uniqueValues(
                        data.getOverVariable(), o.chart.sortgroups,
                        o.showmissingOver);
                    nSeries = grps.size();
                }
                // v3.2.1: gradient function defined before new Chart().
                // onComplete callback fires after first render with real canvas dimensions.
                // Inject onComplete into animation:{...} config block in the script.
                String gradFn = gen.buildGradientPreamble(Math.max(nSeries, 1), id, colorByCategory);
                String onComplete = "onComplete:function(anim){_buildGrads_"
                    + id + "(anim.chart);},";
                // Insert onComplete after "animation:{" in the script
                script = script.replace("animation:{", "animation:{" + onComplete);
                script = gradFn + script;
            }
        }

        // For CI charts and boxplot with filters: wrap chart init in _initChart()
        // so _applyFilter() can call destroy()+_initChart() with new data slices.
        // cibar/ciline: plugin callbacks drop silently on JSON.parse round-trip.
        // boxplot: same issue -- tooltip callbacks and _bpLegendPlugin config lost.
        // violin: EXCLUDED -- violinChart() already wraps itself in _initChart()
        //   internally as part of its animation engine. Wrapping again here would
        //   produce a double _mainChart= assignment and a JS syntax error (v2.5.1).
        boolean isCiType = o.type.equals("cibar") || o.type.equals("ciline")
                        || o.type.equals("boxplot");
        // v2.5.1: also skip var _mainChart= injection for violin (already inside _initChart)
        boolean skipMainAssign = o.type.equals("violin") || o.type.equals("hviolin");
        if (assignToMain && isCiType && data.hasFilter1()) {
            String el = "document.getElementById('" + id + "')";
            String chartPrefix = "new Chart(" + el + ", ";
            int cfgStart = script.indexOf(chartPrefix);
            if (cfgStart >= 0) {
                // Extract the entire config object passed to new Chart(...)
                String beforeChart = script.substring(0, cfgStart);
                String configBlock = script.substring(cfgStart + chartPrefix.length());
                // Strip trailing ");\n"
                if (configBlock.endsWith(");\n")) {
                    configBlock = configBlock.substring(0, configBlock.length() - 3);
                }
                // Find "data:{labels:[...],datasets:[...]}" inside configBlock and replace
                // with references to the _labels/_datasets parameters passed to _initChart.
                // Strategy: replace the data:{...} block with data:{labels:_labels,datasets:_datasets}
                // We locate "data:{labels:[" and find the matching closing brace.
                String dataKey = "data:{labels:[";
                int dataStart = configBlock.indexOf(dataKey);
                if (dataStart >= 0) {
                    // Find end of data:{...} block by counting braces from dataStart
                    int depth = 0;
                    int dataEnd = dataStart;
                    for (int i = dataStart; i < configBlock.length(); i++) {
                        char c = configBlock.charAt(i);
                        if (c == '{') depth++;
                        else if (c == '}') { depth--; if (depth == 0) { dataEnd = i + 1; break; } }
                    }
                    String before = configBlock.substring(0, dataStart);
                    String after  = configBlock.substring(dataEnd);
                    // Build: wrap in function that accepts labels+datasets, uses options block as-is
                    String wrapped = beforeChart
                        + "function _initChart(_labels,_datasets){\n"
                        + "  if(typeof _mainChart!=='undefined'&&_mainChart)_mainChart.destroy();\n"
                        + "  _mainChart=new Chart(" + el + ", " + before
                        + "data:{labels:_labels,datasets:_datasets}"
                        + after + ");\n"
                        + "}\n"
                        // Extract initial labels+datasets from the original data block to call _initChart now
                        + "var _initLabels=[" + extractLabels(configBlock, dataStart) + "];\n"
                        + "var _initDatasets=[" + extractDatasets(configBlock, dataStart) + "];\n"
                        + "_initChart(_initLabels,_initDatasets);\n";
                    return wrapped;
                }
            }
        }
        // Insert chartVar ("var _mainChart=") immediately before "new Chart(" rather
        // than at the start of the script string. This handles chart types like histogram
        // that emit a preamble (e.g. var _ttRanges_...;) before the new Chart(...) call.
        // For chart types with no preamble, indexOf returns 0 so the result is identical.
        // v2.5.1: skip for violin -- _initChart() inside violinChart() already handles
        // the _mainChart assignment. Injecting here would create a double assignment.
        if (!chartVar.isEmpty() && !skipMainAssign) {
            int chartPos = script.indexOf("new Chart(");
            if (chartPos > 0) {
                return script.substring(0, chartPos) + chartVar + script.substring(chartPos);
            }
        }
        if (skipMainAssign) return script;
        return chartVar + script;
    }

    /** Extract the labels array content from within a data:{labels:[...]} block */
    String extractLabels(String cfg, int dataStart) {
        // Find "labels:[" after dataStart and extract content until matching "]"
        int ls = cfg.indexOf("labels:[", dataStart);
        if (ls < 0) return "";
        ls += "labels:[".length();
        int depth = 1, end = ls;
        for (int i = ls; i < cfg.length(); i++) {
            char c = cfg.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') { depth--; if (depth == 0) { end = i; break; } }
        }
        return cfg.substring(ls, end);
    }

    /** Extract the datasets array content from within a data:{labels:[...],datasets:[...]} block */
    String extractDatasets(String cfg, int dataStart) {
        // Find "datasets:[" after dataStart and extract content until matching "]"
        int ds = cfg.indexOf("datasets:[", dataStart);
        if (ds < 0) return "";
        ds += "datasets:[".length();
        int depth = 1, end = ds;
        for (int i = ds; i < cfg.length(); i++) {
            char c = cfg.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') { depth--; if (depth == 0) { end = i; break; } }
        }
        return cfg.substring(ds, end);
    }

    // -- Shared axis config builder --------------------------------------------

    /**
     * Builds a Chart.js scale config object for one axis.
     * @param axis    "x" or "y"
     * @param title   axis title string (may be empty)
     * @param isHbar  true when horizontal bar -- axes are swapped
     */
    String buildAxisConfig(String axis, String title, boolean isHbar, boolean isBar) {
        return buildAxisConfig(axis, title, isHbar, isBar, false, 0);
    }

    String buildAxisConfig(String axis, String title, boolean isHbar, boolean isBar, boolean isLineChart) {
        return buildAxisConfig(axis, title, isHbar, isBar, isLineChart, 0);
    }

    /**
     * Builds a Chart.js scale config object for one axis.
     *
     * nCategories: number of distinct category labels on the category axis.
     *   Used to auto-rotate x labels when many categories are present.
     *   0 means unknown / not applicable (no auto-rotation logic).
     *
     * Auto-rotation rules (applied when xtickangle() is NOT set):
     *   nCategories <= 8  : horizontal (0 deg) -- labels fit without overlap
     *   nCategories <= 20 : 45 deg -- moderate number, slight tilt is readable
     *   nCategories > 20  : 90 deg -- many categories, vertical saves space
     *
     * autoSkip is always set to false so ALL labels are shown regardless of
     * overlap -- rotation is our answer to crowding, not label suppression.
     */
    String buildAxisConfig(String axis, String title, boolean isHbar, boolean isBar,
                           boolean isLineChart, int nCategories) {
        boolean isX = axis.equals("x");
        // For bar charts, the category axis (x for vertical bars, y for horizontal)
        // should have NO type specified -- Chart.js auto-detects it from string labels.
        // Only emit type when the user explicitly requests it, or for the value axis.
        // Line charts also use categorical x-axis (labels from over() groups or string vars).
        // Omit type so Chart.js auto-detects 'category' from string labels.
        boolean isCategoryAxis = (isBar || (isLineChart && isX)) && ((!isHbar && isX) || (isHbar && !isX));
        String userType = isX ? o.axes.xtype : o.axes.ytype;
        // X2 (t2j): xtype(time)/ytype(time) needs a Chart.js date adapter, which is NOT
        // bundled (offline = strictly self-contained). Emitting type:'time' without an
        // adapter blanks the chart. Downgrade to auto category / linear so the axis
        // still renders with its labels drawn as-is. The ado warns the user and the
        // help documents that a formatted time scale is not supported.
        if (userType.equals("time")) userType = "";
        // Category axis: omit type unless user overrode it.
        // EXCEPTION: when stack100=true, Chart.js 4 may treat the category axis as
        // linear when stacked:true is also present, positioning all bars at 0.
        // Explicitly emit type:'category' in that case to prevent misdetection.
        // Value axis: default to "linear" (or user-specified).
        String scaleType = isCategoryAxis
            ? (!userType.isEmpty() ? userType : (o.chart.stack100 ? "category" : ""))
            : (userType.isEmpty() ? "linear" : userType);
        boolean isLog     = scaleType.equals("logarithmic");
        String tickcount  = isX ? o.axes.xtickcount  : o.axes.ytickcount;
        String tickangle  = isX ? o.axes.xtickangle  : o.axes.ytickangle;
        String stepsize   = isX ? o.axes.xstepsize   : o.axes.ystepsize;
        boolean showGrid  = isX ? o.axes.xgridlines  : o.axes.ygridlines;
        boolean showBord  = isX ? o.axes.xborder     : o.axes.yborder;
        String  rMin      = isX ? o.axes.xrangeMin   : o.axes.yrangeMin;
        String  rMax      = isX ? o.axes.xrangeMax   : o.axes.yrangeMax;
        // beginAtZero is incompatible with log scale (log(0) = -Infinity -> blank chart)
        boolean beginZero = !isX && o.axes.yStartZero && !isLog;
        boolean stacked   = o.chart.stack;

        StringBuilder sb = new StringBuilder("{");
        if (!scaleType.isEmpty()) sb.append("type:'").append(scaleType).append("',");
        // v2.2.0: 100% stacked -- clamp value axis to [0, 100]
        boolean isValueAxis = (!isHbar && !isX) || (isHbar && isX);
        // t2j fix6 (D1): bars stack on BOTH axes (Chart.js bar stacking needs it), but the
        // line/area family must stack on the VALUE axis only. stacked:true on a line/area
        // CATEGORY axis makes Chart.js collapse a single-series filled area into the first
        // category. Value-axis stacking still drives real multi-series area/line stacks.
        if (stacked && (isBar || isValueAxis)) sb.append("stacked:true,");
        if (o.chart.stack100 && isValueAxis) {
            sb.append("min:0,max:100,beginAtZero:true,");
        }
        if (beginZero) sb.append("beginAtZero:true,");
        // Log scale: Chart.js cannot draw from 0; set a sensible min if none given
        if (isLog && rMin.isEmpty()) {
            // s9e: the decade below the smallest positive value (was a fixed 1, which
            // squeezed prices into the top of the plot); 1 only when nothing is known
            double dec = (logDataMin != null && logDataMin > 0) ? Math.pow(10, Math.floor(Math.log10(logDataMin))) : 1;
            sb.append("min:").append(String.format(Locale.ROOT, "%.6g", dec)).append(",");
        } else if (!rMin.isEmpty() && !isCategoryAxis) {
            sb.append("min:").append(rMin).append(",max:").append(rMax).append(",");
        } else if (!rMin.isEmpty() && isCategoryAxis) {
            // X1 (t2j): xrange()/yrange() on a CATEGORY axis is ignored. On a Chart.js
            // category scale, min/max are category INDICES (or label values), not data
            // values, so a numeric range like xrange(1000 20000) over a handful of
            // categories positions the window past the last category and blanks the
            // chart. Range bounds only apply to the value (linear/log) axis; the ado
            // help documents this. Emitting nothing leaves the category axis intact.
            sb.append("/* range ignored on category axis */");
        }
        // ticks -- log scale needs a callback or Chart.js 4 renders blank tick labels
        // Custom labels (xlabels/ylabels): inject a JS callback mapping index -> label.
        // Multi-word labels (containing a space) are returned as a JS array so
        // Chart.js 4 wraps them onto multiple lines natively.
        // If xtickangle/ytickangle is set the user prefers rotation -- skip wrapping.
        String customLabelsStr = isX ? o.axes.xlabels : o.axes.ylabels;
        List<String> customLblList = parseCustomLabels(customLabelsStr);
        // Phase 1-C: axis direction reversal (v3.0.3)
        if (!isX && o.axes.yreverse) sb.append("reverse:true,");
        if ( isX && o.axes.xreverse) sb.append("reverse:true,");
        // Phase 1-C: ygrace -- padding above/below data range on y value axis (v3.0.3)
        // Accepts "5%" (percent) or "10" (absolute). Ignored when yrange() is set.
        // s9d: data labels sit ABOVE the bar/point -- without headroom the top label
        // collides with the legend (user screenshot, r_bar_count_datalabels_desc)
        if (!isX && o.axes.ygrace.isEmpty() && o.axes.yrangeMin.isEmpty() && o.axes.yrangeMax.isEmpty() && o.chart.datalabels && !isLog) {
            sb.append("grace:'12%',");
        }
        if (!isX && !o.axes.ygrace.isEmpty() && o.axes.yrangeMin.isEmpty()) {
            String gv = o.axes.ygrace.trim();
            sb.append("grace:");
            if (gv.endsWith("%")) sb.append("'").append(gv).append("',");
            else                  sb.append(gv).append(",");
        }
        sb.append("ticks:{").append(axisTickStyleCfg(isX));
        if (o._sharedAxis && !rMin.isEmpty()) sb.append(",includeBounds:true");   // shared panel range IS the nice bounds
        if (!customLblList.isEmpty()) {
            // Build JS array where each element is either a plain string (single word)
            // or a JS array of words (multi-word -> Chart.js renders as wrapped lines).
            // Only wrap when no rotation is requested.
            boolean doWrap = tickangle.isEmpty();
            StringBuilder clArr = new StringBuilder("[");
            for (String lbl : customLblList) {
                String[] words = lbl.split("\\s+");
                if (doWrap && words.length > 1) {
                    // Return array of words so Chart.js stacks them vertically
                    clArr.append("[");
                    for (String w : words) clArr.append("'").append(escJs(w)).append("',");
                    clArr.append("],");
                } else {
                    clArr.append("'").append(escJs(lbl)).append("',");
                }
            }
            clArr.append("]");
            sb.append(",callback:function(v,i,t){var cl=").append(clArr)
              .append(";return (i<cl.length)?cl[i]:v;}");
        } else if (isLog) {
            sb.append(",callback:function(v,i,t){var n=Number(v.toString());var e=Math.pow(10,Math.floor(Math.log10(n)+1e-9)),m=Math.round(n/e*1e6)/1e6,span=(t&&t.length?Math.log10(t[t.length-1].value/t[0].value):3);return (m===1||(span<=1.6&&(m===2||m===5))||(span<=1.0&&m===3)||(span<0.6&&(m===1.5||m===4||m===7)))?n.toLocaleString():'';}");
        }
        if (!tickcount.isEmpty()) sb.append(",maxTicksLimit:").append(tickcount);
        // Rotation logic for the category (x) axis:
        //   - xtickangle() set by user: always honour it
        //   - Otherwise: auto-rotate based on number of categories so all labels
        //     are visible without overlap (autoSkip:false ensures none are dropped)
        //     <=8 categories : 0 deg (horizontal)
        //     9-20 categories: 45 deg
        //     >20 categories : 90 deg (vertical, saves maximum horizontal space)
        if (!tickangle.isEmpty()) {
            sb.append(",maxRotation:").append(tickangle).append(",minRotation:").append(tickangle);
        } else if (isX) {
            int autoAngle = 0;
            if (nCategories > 20) {
                autoAngle = 90;
            } else if (nCategories > 8) {
                autoAngle = 45;
            }
            sb.append(",maxRotation:").append(autoAngle).append(",minRotation:").append(autoAngle);
        }
        // autoSkip:false -- always show ALL category labels on the category axis.
        // Applies to both x (vertical bar) and y (horizontal bar hbar).
        // Rotation only makes sense on x-axis; y-axis labels are always horizontal.
        if (isCategoryAxis) {
            sb.append(",autoSkip:false");
        }
        if (!stepsize.isEmpty())  sb.append(",stepSize:").append(stepsize);
        sb.append("},");
        // Phase 2-C: xticks()/yticks() -- pin exact numeric tick values (v3.4.0).
        // afterBuildTicks is a scale-level callback that replaces Chart.js auto-
        // generated ticks with a fixed array of {value, major:false} objects.
        // Only meaningful on numeric/linear axes; no conflict with noticks
        // (noticks runs in beforeDraw phase, afterBuildTicks runs at scale-build time).
        String customTicks = isX ? o.axes.xticks : o.axes.yticks;
        if (!customTicks.isEmpty()) {
            List<String> tickVals = parseCustomLabels(customTicks);
            if (!tickVals.isEmpty()) {
                StringBuilder tv = new StringBuilder("[");
                for (String t : tickVals) {
                    // Only emit numeric values; skip malformed entries
                    try { Double.parseDouble(t.trim()); tv.append("{value:").append(t.trim()).append(",major:false},"); }
                    catch (NumberFormatException e) { /* skip */ }
                }
                tv.append("]");
                sb.append("afterBuildTicks:function(axis){axis.ticks=").append(tv).append(";},");
            }
        }
        // grid (Chart.js 4: use border.display not grid.drawBorder)
        sb.append("grid:{color:'").append(gridCssColor()).append("',");
        sb.append("display:").append(showGrid).append("},");
        // border
        sb.append("border:{color:'").append(gridCssColor()).append("',display:").append(showBord).append("},");
        // title -- axisTitleCfg() applies Phase 1-A size/color from o.style
        boolean isXAxisHere = axis.equals("x");
        sb.append(axisTitleCfg(title, isXAxisHere));
        sb.append("}");
        return sb.toString();
    }

    // =========================================================================
    // SECONDARY Y-AXIS CONFIG (v2.3.0)
    // =========================================================================
    // Builds the right-side y2 scale config. Mirrors buildAxisConfig for the
    // value axis but:
    //   - always position:'right'
    //   - uses o.axes.y2title, o.axes.y2rangeMin, o.axes.y2rangeMax (independent of left axis)
    //   - grid lines disabled by default (avoids double grid overlay)
    //   - inherits theme colors and stacked flag from main options
    // =========================================================================
    String buildY2AxisConfig() {
        String lc  = labelColor();
        String gc  = gridCssColor();
        boolean stk = o.chart.stack;
        StringBuilder sb = new StringBuilder("{");
        sb.append("type:'linear',");
        sb.append("position:'right',");
        if (stk) sb.append("stacked:true,");
        // Range bounds -- independent of left axis yrange()
        if (!o.axes.y2rangeMin.isEmpty() && !o.axes.y2rangeMax.isEmpty()) {
            sb.append("min:").append(o.axes.y2rangeMin).append(",");
            sb.append("max:").append(o.axes.y2rangeMax).append(",");
        }
        sb.append("ticks:{color:'").append(lc).append("',maxRotation:0,minRotation:0},");
        // Grid: turn off right-axis grid lines to avoid double overlay with left axis
        sb.append("grid:{color:'").append(gc).append("',display:false},");
        sb.append("border:{color:'").append(gc).append("',display:true},");
        if (!o.axes.y2title.isEmpty()) {
            sb.append("title:{display:true,text:'").append(escJs(o.axes.y2title))
              .append("',color:'").append(lc).append("'},");
        }
        sb.append("}");
        return sb.toString();
    }


    // -- Bar / Line / Area -----------------------------------------------------

    String barLine(String id, DataSet data, boolean isLine) {
        // Chart.js cannot draw bars on a true logarithmic scale (bars extend from 0,
        // and log(0) = -Infinity). When ytype=log on a bar chart, we fake it:
        // log10-transform the data values and use a linear scale with custom tick
        // labels that display the original (10^tick) values.
        boolean yLogBar = !isLine && o.axes.ytype.equals("logarithmic");

        // Use custom xlabels if provided, otherwise auto-generate from data
        List<String> customXLbls = parseCustomLabels(o.axes.xlabels);
        // v1.8.9: When no over() and stat aggregation active, use aggLabels()
        // (one label per variable) instead of catLabels() (one label per obs row).
        // catLabels() is still used when a string variable provides the x-axis categories.
        // v3.4.1: _leglabelsOnXAxis=true suppresses generateLabels in legendLabelsCfg()
        // because leglabels() was already applied to x-axis tick labels instead.
        // This applies for two scenarios matching Stata convention:
        //   (a) aggLabels: no over(), no string var, no custom xlabels
        //       -> variables are x-axis categories; leglabels renames them
        //   (b) overLabels + single var (colorByCategory): has over(), 1 numeric var
        //       -> over-groups are colored bars on x-axis; leglabels renames groups
        // NOT set for multi-var + over() (scenarios 2 & 3): each var is its own dataset,
        // leglabels() renames the LEGEND datasets via legendLabelsCfg(). (v3.4.1)
        boolean usingAggLabels = !data.hasOver()
            && (data.getFirstStringVariable() == null)
            && customXLbls.isEmpty();
        boolean usingOverColorByCategory = data.hasOver()
            && !isLine
            && data.getNumericVariables().size() == 1
            && customXLbls.isEmpty();
        o._leglabelsOnXAxis = (usingAggLabels || usingOverColorByCategory)
            && !o.chart.legendlabels.isEmpty()
            && o.chart.relabel.isEmpty();  // v3.5.34: relabel() handles legend itself; do not suppress
        // v3.5.36: in colorByCategory mode leglabels() ALSO needs generateLabels for per-bar
        // colored legend items. _leglabelsOnXAxis=true would suppress it, so we set a
        // separate flag that legendLabelsCfg() checks to allow generateLabels through.
        o._leglabelsColorByCategory = usingOverColorByCategory
            && !o.chart.legendlabels.isEmpty()
            && o.chart.relabel.isEmpty();
        String labels   = !customXLbls.isEmpty() ? customLabelsJs(customXLbls)
                        : (data.hasOver() && o.chart.stack100) ? aggLabels(data)
                        : data.hasOver() ? overLabels(data)
                        : (data.getFirstStringVariable() != null) ? catLabels(data)
                        : aggLabels(data);
        // v2.2.0: 100% stacked bar uses normalised percentage datasets
        String datasets = (data.hasOver() && o.chart.stack100)
            ? overDatasets100(data)
            : data.hasOver() ? overDatasets(data, isLine, yLogBar) : numDatasets(data, isLine, yLogBar);

        // Axis titles -- v3.5.9: fall back to variable label (or name) when
        // the user has not supplied xtitle()/ytitle(), matching Stata convention.
        // x-axis: over() variable label/name when over() is present;
        //         no single label applies for multi-category no-over() charts.
        // y-axis: first numeric variable label/name when single var or over() present;
        //         no single label applies for multi-var no-over() charts.
        List<Variable> _nv = data.getNumericVariables();
        Variable _ov = data.getOverVariable();
        // s9a: default titles follow the AXES, not the roles -- on a horizontal bar the
        // category (over) label belongs on y and the value label on x (was swapped;
        // found by the pixel stage on r_base_hbar_relabel). User xtitle()/ytitle()
        // still refer to the literal axis, as in Stata.
        String catTitle = _ov != null ? _ov.getDisplayName() : "";
        String valTitle = (!_nv.isEmpty() && (_ov != null || _nv.size() == 1)) ? _nv.get(0).getDisplayName() : "";
        // s9d: the value axis names the STATISTIC when it is not the mean (Stata: "count of price")
        String _st = o.stats.stat == null ? "" : o.stats.stat.toLowerCase(Locale.ROOT);
        if (!valTitle.isEmpty() && !_st.isEmpty() && !_st.equals("mean")) {
            valTitle = _st.equals("count") ? "Count" : (Character.toUpperCase(_st.charAt(0)) + _st.substring(1) + " of " + valTitle);
        }
        String xTitleStr = !o.axes.xtitle.isEmpty() ? o.axes.xtitle : (o.chart.horizontal ? valTitle : catTitle);
        String yTitleStr = !o.axes.ytitle.isEmpty() ? o.axes.ytitle : (o.chart.horizontal ? catTitle : valTitle);

        boolean isBar = !isLine;
        // Compute category count for smart x-axis rotation.
        // For horizontal bar charts the category axis is y (not x), so pass 0
        // to buildAxisConfig for the x call -- rotation only applies to x.
        int nCat = 0;
        if (!o.chart.horizontal) {
            // Category count = distinct x-axis positions
            if (data.hasOver()) {
                nCat = DataSet.uniqueValues(data.getOverVariable(),
                    o.chart.sortgroups, o.showmissingOver).size();
            } else if (data.getFirstStringVariable() != null) {
                nCat = DataSet.uniqueValues(data.getFirstStringVariable(),
                    o.chart.sortgroups).size();
            } else {
                // aggLabels: one position per numeric variable
                nCat = _nv.size();
            }
        }
        // For log bar: temporarily clear ytype so buildAxisConfig emits linear,
        // then we add a custom tick callback below.
        String savedYtype = o.axes.ytype;
        if (yLogBar) o.axes.ytype = "";
        Double _lmin = null; for (Variable _v : _nv) { Double m = minPositive(_v); if (m != null && (_lmin == null || m < _lmin)) _lmin = m; }
        logDataMin = _lmin; String xScaleCfg = buildAxisConfig("x", xTitleStr, o.chart.horizontal, isBar, isLine, nCat);
        logDataMin = _lmin; String yScaleCfg = buildAxisConfig("y", yTitleStr, o.chart.horizontal, isBar);
        logDataMin = null;
        o.axes.ytype = savedYtype;

        // For log bar: replace y scale with a linear scale that has log-labelled ticks.
        // Data values are log10-transformed; ticks display 10^v = original value.
        if (yLogBar) {
            String lc = labelColor();
            String gc = gridCssColor();
            boolean sb2 = o.chart.stack;
            String yTitle = o.axes.ytitle;
            yScaleCfg = "{"
                + "type:'linear',"
                + (sb2 ? "stacked:true," : "")
                + "ticks:{color:'" + lc + "',"
                +   "callback:function(v){"
                +     "var labels={0:'1',1:'10',2:'100',3:'1k',4:'10k',5:'100k',6:'1M'};"
                +     "var r=Math.round(v*10)/10;"
                +     "if(r!==Math.round(r))return '';"
                +     "var n=Math.round(Math.pow(10,r));"
                +     "return n>=1000?(n/1000).toFixed(n>=1000000?1:0)+'k':String(n);"
                +   "}},"
                + "grid:{color:'" + gc + "',display:true},"
                + "border:{color:'" + gc + "',display:true}"
                + (!yTitle.isEmpty() ? ",title:{display:true,text:'" + escJs(yTitle) + "',color:'" + lc + "'}" : "")
                + "}";
        }

        String animDur   = animDuration();
        String easingCfg = o.chart.easing.isEmpty() ? "" : ",easing:'"+o.chart.easing+"'";
        String delayCfg  = o.chart.animdelay.isEmpty()     ? "" : ",delay:"+o.chart.animdelay;
        String aspectCfg = o.chart.aspect.isEmpty()        ? "" : "aspectRatio:"+o.chart.aspect+",";
        String paddingCfg = buildPadding();

        // Legend config
        String legendCfg = buildLegendConfig();

        // Datalabels
        String dlCfg = o.chart.datalabels
            ? "datalabels:{anchor:'end',align:'end',color:'"+labelColor()+"',font:{size:10},"
            + "formatter:function(v){return v==null?'':_spkFmt(parseFloat(v)" + (o.stats.stat.equals("count") ? ",'int'" : "") + ");}}"
            : "";

        // Tooltip v2.1.0: multi-line structured tooltip with stat label,
        // group title, and N. buildBarLineTooltipCfg() respects tooltipformat().
        String ttCfg = buildBarLineTooltipCfg();

        String annotCfg = buildAnnotationConfig(isLine);
        String pluginsCfg = "plugins:{\n"
            + "      legend:" + legendCfg + ",\n"
            + "      " + ttCfg + "\n"
            + (dlCfg.isEmpty() ? "" : "      ," + dlCfg + "\n")
            + (dlCfg.isEmpty() ? dlSuppress().isEmpty() ? "" : "      " + dlSuppress().substring(1) + "\n" : "")
            + (annotCfg.isEmpty() ? "" : "      ," + annotCfg + "\n")
            + "    }";

        return "new Chart(document.getElementById('"+id+"'), {\n"
            + "  type:'"+(isLine?"line":"bar")+"',\n"
            + "  data:{labels:["+labels+"],datasets:["+datasets+"]},\n"
            + "  options:{\n"
            + "    responsive:true,maintainAspectRatio:true,resizeDelay:150,"+aspectCfg+"\n"
            + (o.chart.horizontal ? "    indexAxis:'y',\n" : "")
            // t2j fix8p (user feedback, supersedes fix8f): intersect:true so the tooltip
            // lands on the mark under the cursor and DISMISSES when the pointer leaves it --
            // the same behavior as the coefplot / event-study charts. fix8f had used
            // intersect:false so the tooltip fired anywhere near the nearest point, but that
            // meant it never dismissed and fired in empty plot space (user-reported across
            // line / bar / area / stat / stacked). To keep thin lines and small points
            // hoverable under intersect:true, line/scatter points carry a widened hitRadius
            // (pointHitRadius, set on the datasets) so hovering near the line still triggers.
            + "    interaction:{mode:'nearest',intersect:true},\n"
            + "    animation:{duration:"+animDur+easingCfg+delayCfg+"},\n"
            + paddingCfg
            + "    "+pluginsCfg+",\n"
            // v2.3.0: append secondary y-axis scale when y2() is specified
            + "    scales:{x:"+xScaleCfg+",y:"+yScaleCfg
            + (o.axes.y2vars.isEmpty() ? "" : ",y2:"+buildY2AxisConfig())
            + "}\n"
            + "  }\n"
            + "});\n";
    }

    // -- Scatter ---------------------------------------------------------------

    String scatter(String id, DataSet data) {
        List<Variable> nv = data.getNumericVariables();
        if (nv.size() < 2) return barLine(id, data, false);
        // v2.1.2: Stata convention is "scatter y x" -- first variable listed is y-axis.
        // nv.get(0) = first variable in varlist = y; nv.get(1) = second variable = x.
        Variable yv = nv.get(0), xv = nv.get(1);
        String xl = o.axes.xtitle.isEmpty() ? escJs(xv.getDisplayName()) : escJs(o.axes.xtitle);
        String yl = o.axes.ytitle.isEmpty() ? escJs(yv.getDisplayName()) : escJs(o.axes.ytitle);

        // v3.5.57: resolve mlabel variable from DataSet (string or numeric)
        Variable labelVar = null;
        if (!o.chart.mlabelVar.isEmpty()) {
            for (Variable v : data.getAllVariables()) {
                if (v.getName().equals(o.chart.mlabelVar)) { labelVar = v; break; }
            }
        }

        // v3.5.60: resolve mlabvpos variable (per-obs minute-clock position)
        Variable posVar = null;
        if (!o.chart.mlabvpos.isEmpty()) {
            for (Variable v : data.getAllVariables()) {
                if (v.getName().equals(o.chart.mlabvpos)) { posVar = v; break; }
            }
        }

        bigBegin(xv, yv);   // fix9g: large-data mode above BIG_SCATTER_MIN points
        String ds = data.hasOver()
            ? scatterOverDs(data, xv, yv, labelVar, posVar)
            : scatterSingleDs(xv, yv, labelVar, posVar);

        // v3.5.57: fit line datasets appended after scatter points
        // v3.5.63: strip trailing comma from ds before appending fitDs.
        // scatterOverDs() ends each dataset with "," including the last one.
        // buildFitDatasets() starts with "," so combining gives ",," = invalid JS.
        String dsCleaned = ds.endsWith(",") ? ds.substring(0, ds.length()-1) : ds;
        String fitDs = buildFitDatasets(xv, yv, col(0));
        if (!o.chart.fit.isEmpty()) {   // s9j
            key("fit", "Fit: " + o.chart.fit, "line", fitLineColor(col(0)), "[6,4]", "");   // fix9h: same accent as the line
            // t2j fix8 (deep-dive r2): only advertise a CI-band key for fit types that
            // actually produce a band (lfit/qfit/exp/log/power); lowess/ma have no CI, so
            // a fitci key there was a phantom swatch with nothing drawn.
            boolean fitHasCi = o.chart.fit.equals("lfit") || o.chart.fit.equals("qfit")
                || o.chart.fit.equals("exp") || o.chart.fit.equals("log") || o.chart.fit.equals("power");
            if (o.chart.fitci && fitHasCi) key("fitci", (o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel) + "% CI band", "swatch", colAtAlpha(col(0), 0.25), "", "");
        }

        // mlabel datalabels plugin config
        String mlabelCfg = buildMlabelCfg(labelVar, posVar, data);

        // Use buildAxisConfig so scatter gets all tick/grid/range options consistently
        logDataMin = minPositive(xv); String xScaleCfg = buildAxisConfig("x", xl, false, false);
        logDataMin = minPositive(yv); String yScaleCfg = buildAxisConfig("y", yl, false, false);
        logDataMin = null;
        // t2j fix8q (C8 for scatter): same axis lock as bubble() -- a filtered scatter keeps the
        // full-data extent so the frame does not jump; by() panels lock x to the full-data
        // extent so every panel shares one x axis (y is already shared via the panel range).
        double[] _sxr = varExtent(xv), _syr = varExtent(yv);
        xScaleCfg = lockAxisToFullData(xScaleCfg, _sxr[0], _sxr[1], data);
        yScaleCfg = lockAxisToFullData(yScaleCfg, _syr[0], _syr[1], data);
        xScaleCfg = lockAxisToPanelExtent(xScaleCfg, o._panelXExtent);
        yScaleCfg = lockAxisToPanelExtent(yScaleCfg, o._panelYExtent);

        String easingCfg = o.chart.easing.isEmpty() ? "" : ",easing:'"+o.chart.easing+"'";
        String delayCfg  = o.chart.animdelay.isEmpty() ? "" : ",delay:"+o.chart.animdelay;

        String annotCfg = buildAnnotationConfig(true);
        String animCfg = animBlock(easingCfg, delayCfg);   // fix9g: animation:false in large-data mode
        bigEnd();
        return "new Chart(document.getElementById('"+id+"'), {\n"
            + "  type:'scatter',\n"
            + "  data:{datasets:["+dsCleaned+fitDs+"]},\n"
            + "  options:{\n"
            + "    responsive:true,maintainAspectRatio:true,resizeDelay:150,\n"
            + (o.chart.aspect.isEmpty()?"":"    aspectRatio:"+o.chart.aspect+",\n")
            + animCfg
            + buildPadding()
            + "    plugins:{legend:"+scatterLegendCfg()+","+buildScatterTooltipCfg(xl,yl)
            + mlabelCfg
            + dlSuppress()
            + (annotCfg.isEmpty() ? "" : "," + annotCfg)
            + "},\n"
            + "    scales:{x:"+xScaleCfg+",y:"+yScaleCfg+"}\n"
            + "  }\n"
            + "});\n";
    }

    /**
     * Build datalabels plugin config for mlabel().
     * v3.5.57: Shows labels from the mlabel variable next to each point.
     * Default: suppress when total points > 30 unless mlabelAll=true.
     * Uses chartjs-plugin-datalabels (already bundled).
     */
    /**
     * Converts a minute-clock position (0-59) to a datalabels align string.
     * 0=center, 15=right, 30=below, 45=left. Same formula as alabelpos().
     * v3.5.60
     */
    private static String minuteClockToAlign(int pos) {
        if (pos == 0) return "center";
        // datalabels align: 0=right, 90=bottom, 180=left, 270=top (CW from right).
        // Minute-clock: 0=top, 15=right, 30=bottom, 45=left (CW from top).
        // Conversion: dl = (pos/60*360 - 90 + 360) % 360
        int a = (int) Math.round((pos / 60.0 * 360.0 - 90.0 + 360.0) % 360.0);
        if (a == 0)   return "right";
        if (a == 90)  return "bottom";
        if (a == 180) return "left";
        if (a == 270) return "top";
        return String.valueOf(a);
    }

    private String buildMlabelCfg(Variable labelVar, Variable posVar, DataSet data) {
        if (labelVar == null) return "";

        // Count non-null points
        int totalPts = 0;
        List<Variable> nv = data.getNumericVariables();
        if (nv.size() >= 2) {
            Variable yv = nv.get(0), xv = nv.get(1);
            int n = Math.min(xv.size(), yv.size());
            for (int i = 0; i < n; i++) {
                if (xv.getValues().get(i) != null && yv.getValues().get(i) != null) totalPts++;
            }
        }

        boolean showAll = o.chart.mlabelAll || totalPts <= 30;
        String display = showAll ? "true" : "false";
        String darkC = gen.isDark() ? "#ffffff" : "#333333";

        // v3.5.58: mlabpos() minute-clock position -> datalabels align angle.
        // Same 0-59 convention as alabelpos(): 0=center, 15=right, 30=below,
        // 45=left, approaching 60=above. Default: 0 (top, no offset = above point).
        // Formula: angle_clockwise = (pos/60)*360 degrees from 12 o'clock.
        // datalabels align uses CCW from 3 o'clock, so: align_deg = 90 - (pos/60)*360.
        // anchor: 'end' keeps label outside the point; 'center' for pos=0.
        String align  = "top";
        String anchor = "end";
        if (!o.chart.mlabpos.isEmpty()) {
            try {
                int pos = Integer.parseInt(o.chart.mlabpos.trim());
                if (pos == 0) {
                    // pos=0: center label on point (same as alabelpos pos=0)
                    align  = "center";
                    anchor = "center";
                } else {
                    // datalabels align: 0=right, 90=bottom, 180=left, 270=top (CW from right).
                    // Convert minute-clock: dl = (pos/60*360 - 90 + 360) % 360
                    int angleInt = (int) Math.round((pos / 60.0 * 360.0 - 90.0 + 360.0) % 360.0);
                    if (angleInt == 0)        { align = "right";  anchor = "end"; }
                    else if (angleInt == 90)  { align = "bottom"; anchor = "start"; }
                    else if (angleInt == 180) { align = "left";   anchor = "end"; }
                    else if (angleInt == 270) { align = "top";    anchor = "end"; }
                    else {
                        align = String.valueOf(angleInt);
                        // anchor='start' in lower half (45-225 deg = right-of-center through bottom to left)
                        anchor = (angleInt > 45 && angleInt <= 225) ? "start" : "end";
                    }
                }
            } catch (NumberFormatException e) {
                align = "top"; anchor = "end";
            }
        }

        // v3.5.60: when mlabvposition variable is set, use JS callback per point.
        // The pos field embedded in each {x,y,label,pos} data object is read at
        // render time. Falls back to mlabpos (uniform) if pos field is absent.
        // minuteClockToAlign() is replicated inline in JS using the same formula.
        String alignCfg;
        String anchorCfg;
        if (posVar != null) {
            // JS callback reads v.pos per point; falls back to uniform align if absent
            String fallbackAlign  = align;
            String fallbackAnchor = anchor;
            // Inline the minute-clock -> datalabels angle conversion in JS
            alignCfg  = "function(ctx){"
                + "var v=ctx.dataset.data[ctx.dataIndex];"
                + "var p=(v&&v.pos!=null)?v.pos:" + (o.chart.mlabpos.isEmpty() ? "0" : o.chart.mlabpos) + ";"
                + "if(p===0)return 'center';"
                + "var a=Math.round((p/60*360-90+360)%360);"
                + "if(a===0)return 'right';"
                + "if(a===90)return 'bottom';"
                + "if(a===180)return 'left';"
                + "if(a===270)return 'top';"
                + "return String(a);"
                + "}";
            anchorCfg = "function(ctx){"
                + "var v=ctx.dataset.data[ctx.dataIndex];"
                + "var p=(v&&v.pos!=null)?v.pos:" + (o.chart.mlabpos.isEmpty() ? "0" : o.chart.mlabpos) + ";"
                + "if(p===0)return 'center';"
                + "var a=Math.round((p/60)*360)%360;"
                + "return(a>45&&a<=225)?'start':'end';"
                + "}";
        } else {
            alignCfg  = "'" + align  + "'";
            anchorCfg = "'" + anchor + "'";
        }

        return ",datalabels:{"
            + "display:" + display + ","
            + "formatter:function(v){return v&&v.label?v.label:'';},"
            + "color:'" + darkC + "',"
            + "font:{size:10},"
            + "align:" + alignCfg + ","
            + "anchor:" + anchorCfg + ","
            + "clamp:true,"
            + "clip:false"
            + "}";
    }

    /**
     * Build fit line datasets for scatter.
     * v3.5.57: Appends one or two extra line datasets after the scatter points.
     * Returns empty string when no fit is requested.
     * CI band: upper + lower as filled line datasets.
     */
    /**
     * Parses a "x1,y1|x2,y2|..." string from the ado fit computation
     * into a Chart.js [{x:...,y:...},...] JSON array string.
     * Skips any pairs that cannot be parsed as doubles.
     * Returns "[]" if the input is empty or yields no valid pairs.
     */
    /**
     * parseFitGroups -- parse fitLineData into an ordered map of group -> points.
     *
     * Two formats:
     *   No over():   "x1,y1|x2,y2|..."
     *                Returns {"": "x1,y1|x2,y2|..."} -- single entry, key is empty.
     *   With over(): "Domestic=x,y|..~Foreign=x,y|.."
     *                Returns {"Domestic": "x,y|..", "Foreign": "x,y|.."} in order.
     *
     * The tilde (~) separates groups. The equals (=) separates group key from points.
     * Neither character appears in numeric x,y values so the split is unambiguous.
     * Insertion order is preserved (LinkedHashMap) to match ado levelsof ordering.
     */
    private static java.util.LinkedHashMap<String,String> parseFitGroups(String raw) {
        java.util.LinkedHashMap<String,String> map = new java.util.LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        if (!raw.contains("~") && !raw.contains("=")) {
            // Plain format (no over): single entry with empty key
            map.put("", raw);
            return map;
        }
        // Group-keyed format: split on tilde first, then on first equals only
        for (String entry : raw.split("~", -1)) {
            int eq = entry.indexOf('=');
            if (eq < 0) continue; // malformed entry, skip
            String key = entry.substring(0, eq).trim();
            String pts = entry.substring(eq + 1);
            if (!pts.isEmpty()) map.put(key, pts);
        }
        return map;
    }

    private static String parseXYString(String raw) {
        if (raw == null || raw.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String pair : raw.split("\\|", -1)) {
            String[] parts = pair.split(",", 2);
            if (parts.length < 2) continue;
            try {
                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                if (!first) sb.append(",");
                sb.append("{x:").append(x).append(",y:").append(y).append("}");
                first = false;
            } catch (NumberFormatException ignore) { /* skip malformed pair */ }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Builds Chart.js fit line dataset string.
     * v3.5.69: Uses Stata-computed data (args 158-160) when available -- exact output.
     * Falls back to FitComputer (Java approximation) when args are empty (ma type,
     * or older callers that do not pass pre-computed data).
     */
    /**
     * buildFitDatasets -- emit Chart.js dataset objects for fit lines.
     *
     * Two paths based on fitLineData format:
     *   No over():   plain "x,y|..." -> one fit line, col(0)
     *   With over(): "Domestic=x,y|..~Foreign=x,y|.." -> one fit line per group,
     *                col(gi) matching scatterOverDs() palette indices.
     *
     * fitci: only supported without over() (blocked in ado). When active, emits
     *        ciUpper and ciLower datasets before the fit line.
     *
     * Java FitComputer fallback: used for ma type (and when ado pre-compute is
     *   absent). FitComputer always computes on the full dataset (no over() split).
     */
    private String buildFitDatasets(Variable xv, Variable yv, String baseColor) {
        if (o.chart.fit.isEmpty()) return "";

        // Parse fitLineData into group map
        // No over(): {"": "x,y|..."} -- single entry, empty key
        // With over(): {"Domestic": "x,y|..", "Foreign": "x,y|.."} -- multiple entries
        java.util.LinkedHashMap<String,String> fitGroups = parseFitGroups(o.chart.fitLineData);

        // Java FitComputer fallback when ado did not pre-compute (ma, or empty fitLineData).
        // FitComputer output is already a JS array "[{x:...,y:...},...]" -- NOT the
        // pipe-separated "x,y|x,y|..." format that parseFitGroups/parseXYString expect.
        // Use sentinel key "__java__" so the rendering path can detect and skip parseXYString.
        final String[] javaLabelHolder = {""}; // receives FitResult.labelSuffix if fallback used
        // t2j fix8 (deep-dive r2): stash the FitComputer fallback's CI band so it can be
        // drawn when the ado did not pre-compute fitCiUpper/Lower (offline/no-Stata). Without
        // this the legend advertised a "CI band" swatch that never rendered in the fallback.
        final String[] javaCiHolder = {"", ""}; // [0]=upperData, [1]=lowerData (JS arrays), "" if none
        if (fitGroups.isEmpty()) {
            java.util.List<Double> xs = new java.util.ArrayList<>();
            java.util.List<Double> ys = new java.util.ArrayList<>();
            int n = Math.min(xv.size(), yv.size());
            for (int i = 0; i < n; i++) {
                Object xo = xv.getValues().get(i), yo = yv.getValues().get(i);
                if (xo != null && yo != null) {
                    try { xs.add(Double.parseDouble(String.valueOf(xo)));
                          ys.add(Double.parseDouble(String.valueOf(yo))); }
                    catch (NumberFormatException e) { /* skip */ }
                }
            }
            if (xs.size() < 3) return "";
            FitComputer.FitResult fit = FitComputer.compute(xs, ys, o.chart.fit, o.chart.fitci);
            if (fit.lineData.equals("[]")) return "";
            // Store with sentinel: value IS the ready JS array, not pipe-sep format
            fitGroups.put("__java__", fit.lineData);
            javaLabelHolder[0] = fit.labelSuffix; // stash suffix outside fitGroups
            if (o.chart.fitci && fit.hasCi && fit.upperData != null && fit.lowerData != null
                    && !fit.upperData.equals("[]") && !fit.lowerData.equals("[]")) {
                javaCiHolder[0] = fit.upperData;
                javaCiHolder[1] = fit.lowerData;
            }
        }

        boolean isMultiGroup = fitGroups.size() > 1;
        StringBuilder sb = new StringBuilder();

        if (!isMultiGroup) {
            // ----------------------------------------------------------------
            // Single fit line path (no over(), backward compatible)
            // ----------------------------------------------------------------
            boolean javaFallback = fitGroups.containsKey("__java__");
            String pts      = javaFallback ? fitGroups.get("__java__") : fitGroups.values().iterator().next();
            // FitComputer output is already a JS array; pipe-sep format needs parseXYString.
            String lineData = javaFallback ? pts : parseXYString(pts);
            if (lineData.equals("[]")) return "";

            // fix9h (Fahad, 2026-09-14): the fit line is a dark accent of the series hue and
            // is drawn ON TOP of the points (order:-1), the way Stata draws lfit over a
            // scatter; it used to be the point hue at alpha 0.75 under the points (order:2),
            // invisible inside a dense cloud. The CI band stays under everything (order:3).
            String fitColor = fitLineColor(baseColor);
            String ciColor  = baseColor.replace("0.85","0.15").replace(",1)",",0.15)");
            // Use FitResult label suffix when available (e.g. " (MA-7)"), else fallback
            String suffix = (!javaLabelHolder[0].isEmpty()) ? javaLabelHolder[0] : " (" + o.chart.fit + ")";

            // CI bands (only when no over()). Prefer the ado's exact pre-computed band
            // (args 159/160); fall back to the FitComputer band (javaCiHolder) so fitci
            // still renders offline/without Stata instead of leaving a legend swatch with
            // no band. Label uses cilevel (kept out of the legend by the upper/lower filter).
            boolean adoCi  = !o.chart.fitCiUpper.isEmpty() && !o.chart.fitCiLower.isEmpty();
            boolean javaCi = !javaCiHolder[0].isEmpty() && !javaCiHolder[1].isEmpty();
            if (o.chart.fitci && (adoCi || javaCi)) {
                String upData = adoCi ? parseXYString(o.chart.fitCiUpper) : javaCiHolder[0];
                String loData = adoCi ? parseXYString(o.chart.fitCiLower) : javaCiHolder[1];
                String ciLvl  = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel;
                sb.append(",{label:'").append(ciLvl).append("% CI (upper)',type:'line',data:").append(upData)
                  .append(",borderColor:'").append(fitColor).append("'")
                  .append(",backgroundColor:'").append(ciColor).append("'")
                  .append(",borderWidth:0,pointRadius:0,fill:'+1'")
                  .append(",datalabels:{display:false},order:3}");
                sb.append(",{label:'").append(ciLvl).append("% CI (lower)',type:'line',data:").append(loData)
                  .append(",borderColor:'").append(fitColor).append("'")
                  .append(",backgroundColor:'").append(ciColor).append("'")
                  .append(",borderWidth:0,pointRadius:0,fill:'-1'")
                  .append(",datalabels:{display:false},order:3}");
            }

            sb.append(",{label:'").append(escJs(yv.getDisplayName())).append(escJs(suffix))
              .append("',type:'line',data:").append(lineData)
              .append(",borderColor:'").append(fitColor).append("'")
              .append(",backgroundColor:'transparent'")
              .append(",borderWidth:2,borderDash:[6,3],pointRadius:0,fill:false,pointStyle:'line'")   // fix9o: legend swatch = dashed line in the fit colour
              .append(",datalabels:{display:false},order:-1}");   // fix9h: above the points

        } else {
            // ----------------------------------------------------------------
            // Per-group path (with over()): one fit line (+ optional CI) per group.
            // Dataset layout (stride S per group):
            //   fitci=false: S=1  -> [fitLine_g0, fitLine_g1, ...]
            //   fitci=true:  S=3  -> [ciUpper_g0, ciLower_g0, fitLine_g0,
            //                         ciUpper_g1, ciLower_g1, fitLine_g1, ...]
            // This stride is mirrored exactly in FilterRenderer for rebuilds.
            // Color: col(gi) per group, matching scatterOverDs() palette indices.
            // ----------------------------------------------------------------
            java.util.LinkedHashMap<String,String> ciUpperGroups =
                parseFitGroups(o.chart.fitCiUpper);
            java.util.LinkedHashMap<String,String> ciLowerGroups =
                parseFitGroups(o.chart.fitCiLower);
            boolean perGroupCi = o.chart.fitci && !ciUpperGroups.isEmpty();

            int gi = 0;
            for (java.util.Map.Entry<String,String> entry : fitGroups.entrySet()) {
                String groupLabel = entry.getKey();
                String pts        = entry.getValue();
                String lineData   = parseXYString(pts);
                if (lineData.equals("[]")) { gi++; continue; }

                String gColor   = col(gi);
                String fitColor = fitLineColor(gColor);   // fix9h: dark accent of the group hue, on top
                String ciColor  = gColor.replace("0.85","0.15").replace(",1)",",0.15)");
                String suffix   = " (" + o.chart.fit + ")";

                if (perGroupCi) {
                    String upPts = ciUpperGroups.getOrDefault(groupLabel, "");
                    String loPts = ciLowerGroups.getOrDefault(groupLabel, "");
                    if (!upPts.isEmpty() && !loPts.isEmpty()) {
                        String upData = parseXYString(upPts);
                        String loData = parseXYString(loPts);
                        // ciUpper -- fills down to ciLower (fill:'+1')
                        sb.append(",{label:'").append(escJs(groupLabel))
                          .append(" 95% CI (upper)',type:'line',data:").append(upData)
                          .append(",borderColor:'").append(fitColor).append("'")
                          .append(",backgroundColor:'").append(ciColor).append("'")
                          .append(",borderWidth:0,pointRadius:0,fill:'+1'")
                          .append(",datalabels:{display:false},order:3}");
                        // ciLower -- fills up to ciUpper (fill:'-1')
                        sb.append(",{label:'").append(escJs(groupLabel))
                          .append(" 95% CI (lower)',type:'line',data:").append(loData)
                          .append(",borderColor:'").append(fitColor).append("'")
                          .append(",backgroundColor:'").append(ciColor).append("'")
                          .append(",borderWidth:0,pointRadius:0,fill:'-1'")
                          .append(",datalabels:{display:false},order:3}");
                    }
                }

                // Fit line (always last within each group's stride)
                sb.append(",{label:'").append(escJs(groupLabel)).append(escJs(suffix))
                  .append("',type:'line',data:").append(lineData)
                  .append(",borderColor:'").append(fitColor).append("'")
                  .append(",backgroundColor:'transparent'")
                  .append(",borderWidth:2,borderDash:[6,3],pointRadius:0,fill:false,pointStyle:'line'")
                  .append(",datalabels:{display:false},order:-1}");   // fix9h: above the points
                gi++;
            }
        }

        return sb.toString();
    }

    String scatterSingleDs(Variable xv, Variable yv, Variable labelVar, Variable posVar) {
        StringBuilder pts = new StringBuilder();
        int n = Math.min(xv.size(), yv.size());
        int nLabel = labelVar != null ? labelVar.size() : 0;
        int nPos   = posVar   != null ? posVar.size()   : 0;
        int nIter = bigOrder != null ? bigOrder.length : n;   // fix9g: optional row order (data order shipped)
        for (int k = 0; k < nIter; k++) {
            int i = bigOrder != null ? bigOrder[k] : k;
            Object x=xv.getValues().get(i), y=yv.getValues().get(i);
            if (x==null||y==null) continue;
            pts.append("{x:").append(x).append(",y:").append(y);
            if (labelVar != null && i < nLabel) {
                Object lv = labelVar.getValues().get(i);
                if (lv != null) pts.append(",label:'").append(escJs(String.valueOf(lv))).append("'");
            }
            if (posVar != null && i < nPos) {
                Object pv = posVar.getValues().get(i);
                if (pv != null) {
                    try { pts.append(",pos:").append((int) Double.parseDouble(String.valueOf(pv))); }
                    catch (NumberFormatException ignore) {}
                }
            }
            pts.append("},");
        }
        String lbl = escJs(yv.getDisplayName()+" vs "+xv.getDisplayName());
        return "{label:'"+lbl+"',data:["+pts+"],backgroundColor:'"+col(0)+"',"
            + bigDsProps() + buildPointConfig(0)+"}";
    }

    // Convenience overloads -- delegate to full form.
    // All new callers should use scatter() which reads labelVar/posVar from o.chart.
    String scatterSingleDs(Variable xv, Variable yv, Variable labelVar) {
        return scatterSingleDs(xv, yv, labelVar, null);
    }
    String scatterSingleDs(Variable xv, Variable yv) {
        return scatterSingleDs(xv, yv, null, null);
    }

    String scatterOverDs(DataSet data, Variable xv, Variable yv, Variable labelVar, Variable posVar) {
        Variable ov = data.getOverVariable();
        List<String> groups    = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
        List<String> groupKeys = DataSet.uniqueGroupKeys(ov, o.chart.sortgroups, o.showmissingOver);
        java.util.Map<String,Integer> globalScIdx = new java.util.LinkedHashMap<>();
        if (o._globalOverGroups != null)
            for (int gi2 = 0; gi2 < o._globalOverGroups.size(); gi2++)
                globalScIdx.put(o._globalOverGroups.get(gi2), gi2);
        StringBuilder sb = new StringBuilder();
        int ci = 0;
        int nLabel = labelVar != null ? labelVar.size() : 0;
        int nPos   = posVar   != null ? posVar.size()   : 0;
        for (int gi = 0; gi < groups.size(); gi++) {
            String g  = groups.get(gi);
            String gc = sdz(groupKeys.get(gi));
            int palIdx = globalScIdx.containsKey(g) ? globalScIdx.get(g) : ci;
            StringBuilder pts = new StringBuilder();
            int n = Math.min(xv.size(), yv.size());
            int nIter = bigOrder != null ? bigOrder.length : n;   // fix9g: optional row order (data order shipped)
            for (int k = 0; k < nIter; k++) {
                int i = bigOrder != null ? bigOrder[k] : k;
                Object gval=ov.getValues().get(i);
                if (!gc.equals(sdz(gval==null?"":String.valueOf(gval)))) continue;
                Object x=xv.getValues().get(i), y=yv.getValues().get(i);
                if (x==null||y==null) continue;
                pts.append("{x:").append(x).append(",y:").append(y);
                if (labelVar != null && i < nLabel) {
                    Object lv = labelVar.getValues().get(i);
                    if (lv != null) pts.append(",label:'").append(escJs(String.valueOf(lv))).append("'");
                }
                if (posVar != null && i < nPos) {
                    Object pv = posVar.getValues().get(i);
                    if (pv != null) {
                        try { pts.append(",pos:").append((int) Double.parseDouble(String.valueOf(pv))); }
                        catch (NumberFormatException ignore) {}
                    }
                }
                pts.append("},");
            }
            sb.append("{label:'").append(escJs(ov.getDisplayName()+" = "+g)).append("',")
              .append("data:[").append(pts).append("],")
              .append("backgroundColor:'").append(col(palIdx)).append("',")
              .append(bigDsProps()).append(buildPointConfig(palIdx)).append("},");
            ci++;
        }
        return sb.toString();
    }

    // Convenience overloads -- all delegate to full form.
    String scatterOverDs(DataSet data, Variable xv, Variable yv, Variable labelVar) {
        return scatterOverDs(data, xv, yv, labelVar, null);
    }
    String scatterOverDs(DataSet data, Variable xv, Variable yv) {
        return scatterOverDs(data, xv, yv, null, null);
    }

    /**
     * Builds datasets[] string for a scatter filter slice. (v2.6.2)
     * Called by FilterRenderer. Now superseded by buildScatterPoints() in
     * sparkta_engine.js for the live filter path -- this method remains for
     * any legacy callers that pre-build static slices.
     */
    String scatterDatasets(DataSet data) {
        List<Variable> nv = data.getNumericVariables();
        if (nv.size() < 2) return "";
        Variable yv = nv.get(0), xv = nv.get(1);
        return data.hasOver() ? scatterOverDs(data, xv, yv) : scatterSingleDs(xv, yv);
    }

    /**
     * Builds the datasets[] string for a bubble filter slice. (v2.6.2)
     * Computes rmin/rrange from the slice data so bubble radii are
     * correctly normalised for each filter subset independently.
     * Returns empty string if slice has fewer than 3 numeric variables.
     */
    String bubbleDatasets(DataSet data) {
        List<Variable> nv = data.getNumericVariables();
        if (nv.size() < 3) return scatterDatasets(data); // fallback like bubble()
        Variable yv=nv.get(0), xv=nv.get(1), rv=nv.get(2);
        double rmin=Double.MAX_VALUE, rmax=-Double.MAX_VALUE;
        for (Object v : rv.getValues()) {
            if (v instanceof Number) {
                double d=((Number)v).doubleValue();
                if (d<rmin) rmin=d;
                if (d>rmax) rmax=d;
            }
        }
        double rrange = (rmax-rmin==0) ? 1 : rmax-rmin;
        // t2j fix8 (deep-dive r3): by() panel mode uses the full-data size range (see bubble()).
        if (!Double.isNaN(o._bubbleRminGlobal)) { rmin = o._bubbleRminGlobal; rrange = o._bubbleRspanGlobal; }
        return data.hasOver()
            ? bubbleOverDs(data, xv, yv, rv, rmin, rrange)
            : bubbleSingleDs(xv, yv, rv, rmin, rrange);
    }

    // -- Bubble ----------------------------------------------------------------

    String bubble(String id, DataSet data) {
        List<Variable> nv = data.getNumericVariables();
        if (nv.size() < 3) return scatter(id, data);
        // v2.1.2: Stata convention -- first variable = y, second = x, third = size.
        Variable yv=nv.get(0), xv=nv.get(1), rv=nv.get(2);
        String xl = o.axes.xtitle.isEmpty() ? escJs(xv.getDisplayName()) : escJs(o.axes.xtitle);
        String yl = o.axes.ytitle.isEmpty() ? escJs(yv.getDisplayName()) : escJs(o.axes.ytitle);

        double rmin=Double.MAX_VALUE, rmax=-Double.MAX_VALUE;
        for (Object v : rv.getValues()) {
            if (v instanceof Number){double d=((Number)v).doubleValue(); if(d<rmin)rmin=d; if(d>rmax)rmax=d;}
        }
        double rrange = (rmax-rmin==0) ? 1 : rmax-rmin;
        // t2j fix8 (deep-dive r3): in by() panel mode, use the full-data size range so every
        // panel scales radii on the same basis as the JS filter recompute (else bubbles
        // resized the first time a filter was touched).
        if (!Double.isNaN(o._bubbleRminGlobal)) { rmin = o._bubbleRminGlobal; rrange = o._bubbleRspanGlobal; }

        bigBegin(xv, yv);   // fix9g: large-data mode above BIG_SCATTER_MIN points
        String datasets = data.hasOver()
            ? bubbleOverDs(data, xv, yv, rv, rmin, rrange)
            : bubbleSingleDs(xv, yv, rv, rmin, rrange);

        String xScaleCfg = buildAxisConfig("x", xl, false, false);
        String yScaleCfg = buildAxisConfig("y", yl, false, false);
        // t2j fix8p (C8): lock the axes to the full-data extent on a filtered bubble so the
        // frame does not jump when the filter changes the visible subset.
        double[] _xr = varExtent(xv), _yr = varExtent(yv);
        xScaleCfg = lockAxisToFullData(xScaleCfg, _xr[0], _xr[1], data);
        yScaleCfg = lockAxisToFullData(yScaleCfg, _yr[0], _yr[1], data);
        // t2j fix8q (C8 for by() panels): a panel subset has no filters and only its own
        // extent, so lock to the FULL-data extent buildByScripts() stored before the loop.
        xScaleCfg = lockAxisToPanelExtent(xScaleCfg, o._panelXExtent);
        yScaleCfg = lockAxisToPanelExtent(yScaleCfg, o._panelYExtent);
        String easingCfg = o.chart.easing.isEmpty() ? "" : ",easing:'"+o.chart.easing+"'";
        String delayCfg  = o.chart.animdelay.isEmpty()     ? "" : ",delay:"+o.chart.animdelay;

        String annotCfg = buildAnnotationConfig(true);
        String animCfg = animBlock(easingCfg, delayCfg);   // fix9g: animation:false in large-data mode
        bigEnd();
        return "new Chart(document.getElementById('"+id+"'), {\n"
            + "  type:'bubble',\n"
            + "  data:{datasets:["+datasets+"]},\n"
            + "  options:{\n"
            + "    responsive:true,maintainAspectRatio:true,resizeDelay:150,\n"
            + (o.chart.aspect.isEmpty()?"":"    aspectRatio:"+o.chart.aspect+",\n")
            + animCfg
            + buildPadding()
            + "    plugins:{legend:"+buildLegendConfig()+","
            + buildBubbleTooltipCfg(xl, yl, escJs(rv.getDisplayName()))
            + dlSuppress()
            + (annotCfg.isEmpty() ? "" : "," + annotCfg)
            + "},\n"
            + "    scales:{x:"+xScaleCfg+",y:"+yScaleCfg+"}\n"
            + "  }\n"
            + "});\n";
    }

    private String bubbleSingleDs(Variable xv, Variable yv, Variable rv, double rmin, double rrange) {
        StringBuilder pts = new StringBuilder();
        int n = Math.min(Math.min(xv.size(),yv.size()),rv.size());
        int nIter = bigOrder != null ? bigOrder.length : n;   // fix9g: optional row order (data order shipped)
        for (int k=0;k<nIter;k++) {
            int i = bigOrder != null ? bigOrder[k] : k;
            if (i >= n) continue;
            Object x=xv.getValues().get(i), y=yv.getValues().get(i), r=rv.getValues().get(i);
            if (x!=null&&y!=null&&r!=null) {
                double rN = 5+35*(((Number)r).doubleValue()-rmin)/rrange;
                pts.append("{x:").append(x).append(",y:").append(y).append(",r:").append(String.format(Locale.ROOT, "%.1f",rN)).append("},");
            }
        }
        return "{label:'"+escJs(xv.getDisplayName()+" vs "+yv.getDisplayName()+" (size="+rv.getDisplayName()+")")+"',"
            + bigDsProps() + "data:["+pts+"],backgroundColor:'"+col(0)+"'"+bubbleStyleProps()+"}";
    }

    /**
     * t2j fix6: honor pointstyle()/pointborderwidth()/pointrotation() on bubble marks.
     * They were parsed but never emitted for bubble (dead). Bubble SIZE stays data-driven
     * (the r value), so pointRadius/hover are intentionally not set here; only the style
     * props are added, and only when the user actually set them (defaults left untouched so
     * bubble's default look is unchanged).
     */
    private String bubbleStyleProps() {
        StringBuilder s = new StringBuilder();
        if (!o.chart.pointstyle.isEmpty())            s.append(",pointStyle:'").append(escJs(o.chart.pointstyle)).append("'");
        if (!o.chart.pointborderwidth.equals("1"))    s.append(",pointBorderWidth:").append(o.chart.pointborderwidth);
        if (!o.chart.pointrotation.equals("0"))       s.append(",pointRotation:").append(o.chart.pointrotation);
        return s.toString();
    }

    /**
     * t2j fix6: per-model marker shapes for multi-model coefplot / eventstudy. Honors
     * pointstyles() / msymbol() (o.chart.pePStyles -- space-separated Chart.js shape names),
     * falling back to the default cycle. Previously pePStyles was parsed but never read, so
     * both paths used their own hardcoded array (and the two arrays even disagreed on order).
     */
    private String[] peShapeCycle() {
        if (o.chart.pePStyles != null && !o.chart.pePStyles.trim().isEmpty()) {
            String[] u = o.chart.pePStyles.trim().split("\\s+");
            if (u.length > 0) return u;
        }
        return new String[]{ "circle", "rectRot", "triangle", "rect", "crossRot", "star" };
    }

    private String bubbleOverDs(DataSet data, Variable xv, Variable yv, Variable rv, double rmin, double rrange) {
        Variable ov = data.getOverVariable();
        // v1.9.1: pass showmissingOver so (Missing) group appears when set
        List<String> groups    = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
        List<String> groupKeys = DataSet.uniqueGroupKeys(ov, o.chart.sortgroups, o.showmissingOver);
        // v3.5.28: build global palette index map for by() panel color consistency
        java.util.Map<String,Integer> globalBbIdx = new java.util.LinkedHashMap<>();
        if (o._globalOverGroups != null)
            for (int gi2 = 0; gi2 < o._globalOverGroups.size(); gi2++)
                globalBbIdx.put(o._globalOverGroups.get(gi2), gi2);
        StringBuilder sb = new StringBuilder();
        int ci = 0;
        for (int gi = 0; gi < groups.size(); gi++) {
            String g  = groups.get(gi);               // display label
            String gc = sdz(groupKeys.get(gi));       // raw key for matching
            int palIdx = globalBbIdx.containsKey(g) ? globalBbIdx.get(g) : ci;
            StringBuilder pts = new StringBuilder();
            int n = Math.min(Math.min(xv.size(),yv.size()),rv.size());
            int nIter = bigOrder != null ? bigOrder.length : n;   // fix9g: optional row order (data order shipped)
            for (int k=0;k<nIter;k++) {
                int i = bigOrder != null ? bigOrder[k] : k;
                if (i >= n) continue;
                Object gval=ov.getValues().get(i);
                if (!gc.equals(sdz(gval==null?"":String.valueOf(gval)))) continue;
                Object x=xv.getValues().get(i), y=yv.getValues().get(i), r=rv.getValues().get(i);
                if (x!=null&&y!=null&&r!=null) {
                    double rN=5+35*(((Number)r).doubleValue()-rmin)/rrange;
                    pts.append("{x:").append(x).append(",y:").append(y).append(",r:").append(String.format(Locale.ROOT, "%.1f",rN)).append("},");
                }
            }
            sb.append("{label:'").append(escJs(ov.getDisplayName()+" = "+g)).append("',")
              .append("data:[").append(pts).append("],")
              .append("backgroundColor:'").append(col(palIdx)).append("',")
              .append("hoverBackgroundColor:'").append(colS(palIdx)).append("'")
              .append(bigMode ? ",_spkBig:true" : "")   // fix9g
              .append(bubbleStyleProps()).append("},");
            ci++;
        }
        return sb.toString();
    }

    // -- CI Bar / CI Line (v1.7.0) ---------------------------------------------
    //
    // Both chart types compute per-group: n, mean, SD, SE, t-critical -> CI bounds.
    // cibar  uses chartjs-chart-error-bars plugin (barWithErrorBars type).
    // ciline uses two transparent boundary datasets with Chart.js fill option
    //        to shade the CI band natively -- no extra plugin needed.
    //
    // t-critical lookup: covers df 1-120 plus infinity (z) at three levels.
    // For df > 120 the normal approximation (z) is used.

    /**
     * Returns the two-tailed t-critical value for a given df and CI level.
     * Supports cilevel 90, 95, 99. Falls back to 95 for unrecognised levels.
     * For df > 120 returns the z-critical (normal approximation).
     *
     * Values taken from standard t-distribution tables, accurate to 4 dp.
     */
    String histogram(String id, DataSet data) {
        List<Variable> nv = data.getNumericVariables();
        if (nv.isEmpty()) return barLine(id, data, false); // safety fallback

        Variable var = nv.get(0); // histogram always one variable (validated in ado)

        // Collect non-missing values and sort
        List<Double> vals = new ArrayList<>();
        for (Object v : var.getValues()) {
            if (v instanceof Number) vals.add(((Number) v).doubleValue());
        }
        java.util.Collections.sort(vals);
        int n = vals.size();
        if (n == 0) return barLine(id, data, false);

        // Bin count: user bins() or Sturges rule
        int nBins = sturgesBins(n);
        if (!o.stats.bins.isEmpty()) {
            try { nBins = Math.max(2, Integer.parseInt(o.stats.bins.trim())); }
            catch (Exception ignore) {}
        }

        double[][] bins = computeBins(vals, nBins);
        double binWidth = bins.length > 0 ? (bins[0][2] - bins[0][1]) : 1.0;

        // CATEGORY AXIS APPROACH (v1.8.4):
        // Use n+1 string labels (all bin lo edges + final hi edge) and n scalar y values.
        // With offset:true (Chart.js default), the axis adds half-bar padding at both
        // edges automatically -- first and last bars render at FULL width, no clipping.
        // This is simpler and more reliable than the linear/{x,y} approach which requires
        // precise min/max tuning and interacts badly with barPercentage/categoryPercentage.
        StringBuilder lblJs  = new StringBuilder();  // n+1 tick labels (bin edges)
        StringBuilder datJs  = new StringBuilder();  // n y-values (one per bar)
        StringBuilder ttRng  = new StringBuilder();  // tooltip range strings (n entries)
        StringBuilder ttCnt  = new StringBuilder();  // v2.6.1: per-bin obs counts

        for (double[] bin : bins) {
            double lo   = bin[1];
            double hi   = bin[2];
            double cnt  = bin[3];
            double yVal;
            switch (o.stats.histtype) {
                case "frequency": yVal = cnt;                   break;
                case "fraction":  yVal = cnt / n;              break;
                default:          yVal = cnt / (n * binWidth); break; // density
            }
            // Tick label: bin lo edge (numeric string, no quotes in value)
            lblJs.append("'").append(String.format(Locale.ROOT, "%.0f", lo)).append("',");
            // t2j fix8f (issue #11): emit the density with significant-figure precision
            // (fmt = %.10g, trailing zeros stripped) instead of "%.6f". Density is 1/range
            // scaled, so a wide-range variable (e.g. price) gives densities <1e-6 that "%.6f"
            // truncated to 0.000000 -> a bar of height 0 and a tooltip reading "0". fmt keeps
            // the real value so both the bar and the tooltip are accurate.
            datJs.append(fmt(yVal)).append(",");
            ttRng.append("'[").append(String.format(Locale.ROOT, "%.2f", lo))
                 .append(", ").append(String.format(Locale.ROOT, "%.2f", hi)).append(")',");
            // v2.6.1: per-bin count so tooltip shows obs in this bin, not total n
            ttCnt.append((int) cnt).append(",");
        }
        // Append the final right edge as the last tick label (n+1 total labels)
        double lastHi = bins[bins.length - 1][2];
        lblJs.append("'").append(String.format(Locale.ROOT, "%.0f", lastHi)).append("'");

        // Y-axis title
        String yTitleDefault;
        switch (o.stats.histtype) {
            case "frequency": yTitleDefault = "Frequency";  break;
            case "fraction":  yTitleDefault = "Fraction";   break;
            default:          yTitleDefault = "Density";    break;
        }
        String xTitle = o.axes.xtitle.isEmpty() ? escJs(var.getDisplayName()) : escJs(o.axes.xtitle);
        String yTitle = o.axes.ytitle.isEmpty() ? yTitleDefault : escJs(o.axes.ytitle);

        String colFill  = col(0);
        String colBord  = colS(0);
        String animDur  = animDuration();
        String easCfg   = o.chart.easing.isEmpty() ? "" : ",easing:'" + o.chart.easing + "'";
        String varLbl   = escJs(var.getDisplayName());
        String nStr     = String.valueOf(n);
        String yScaleCfg = buildAxisConfig("y", yTitle, false, false);
        // x-axis: category axis, NO offset:false, so Chart.js keeps default half-bar
        // padding at both ends -- edge bars render at full width automatically.
        String xScaleCfg = buildHistXScaleCat(xTitle, nBins);

        // v1.8.5 Fix: store _ttRanges_ JS var declaration in _histPreamble so
        // build() can emit it BEFORE the chart script. Without this, the tooltip
        // title callback references _ttRanges_mainChart which is never declared,
        // causing silent undefined errors on hover.
        // // v2.6.1: emit _ttRanges_ (bin ranges) and _ttCounts_ (per-bin obs) together
        // so tooltip shows per-bin observation count, not total dataset N.
        String safeId = id.replace("-","_");
        // t2j fix9a: exact bin edges per chart id (n+1 values) so the by()-panel filter can
        // recount into THIS panel's bins (each panel builds its own bins; _smeta.histBins
        // holds the full-data bins only)
        StringBuilder edgeJs = new StringBuilder();
        for (double[] bin : bins) edgeJs.append(fmt(bin[1])).append(",");
        edgeJs.append(fmt(bins[bins.length - 1][2]));
        String ttRangesVar = "var _ttRanges_" + safeId + "=["
            + ttRng.toString() + "];\n"
            + "var _ttCounts_" + safeId + "=["
            + ttCnt.toString() + "];\n"
            + "var _binEdges_" + safeId + "=[" + edgeJs + "];\n";
        o._histPreamble = ttRangesVar;

        // Build bin-edge numeric array for x-annotation fractional-index mapping.
        // Histogram uses a category axis (string labels), so Chart.js annotation
        // xMin/xMax must be category indices (0-based integers or fractions), NOT
        // raw data values. We interpolate the user's numeric value between bin edges
        // to get a fractional index -- this gives exact proportional placement
        // matching Stata's continuous-axis xline behaviour. (v3.5.1)
        double[] histBinEdges = null;
        if (!o.axes.xline.isEmpty() || !o.axes.xband.isEmpty()) {
            // Collect all bin edge values: lo of each bin + final hi edge
            java.util.List<Double> edgeList2 = new java.util.ArrayList<>();
            for (double[] bin : bins) edgeList2.add(bin[1]);
            edgeList2.add(bins[bins.length - 1][2]);
            histBinEdges = new double[edgeList2.size()];
            for (int i = 0; i < edgeList2.size(); i++) histBinEdges[i] = edgeList2.get(i);
        }

        return "new Chart(document.getElementById('" + id + "'), {\n"
            + "  type:'bar',\n"
            + "  data:{\n"
            + "    labels:[" + lblJs + "],\n"
            + "    datasets:[{\n"
            + "      label:'" + varLbl + "',\n"
            + "      data:[" + datJs + "],\n"
            + "      backgroundColor:'" + colFill + "',\n"
            + "      borderColor:'" + colBord + "',\n"
            + "      borderWidth:1,\n"
            // barPercentage + categoryPercentage = 1.0: bars fill each slot fully (no gap)
            // offset:true (default) gives half-slot padding at each end for full edge bars
            + "      barPercentage:1.0,categoryPercentage:1.0\n"
            + "    }]\n"
            + "  },\n"
            + "  options:{\n"
            + "    responsive:true,maintainAspectRatio:true,resizeDelay:150,\n"
            + (o.chart.aspect.isEmpty() ? "" : "    aspectRatio:" + o.chart.aspect + ",\n")
            + "    animation:{duration:" + animDur + easCfg + "},\n"
            + buildPadding()
            + "    plugins:{\n"
            + "      legend:{display:false},\n"
            + "      " + buildHistTooltipCfg(id, varLbl, yTitleDefault, nStr) + "\n"
            + (buildAnnotationConfig(true, histBinEdges).isEmpty() ? "" : "      ," + buildAnnotationConfig(true, histBinEdges) + "\n")
            + "    },\n"
            + "    scales:{\n"
            + "      x:" + xScaleCfg + ",\n"
            + "      y:" + yScaleCfg + "\n"
            + "    }\n"
            + "  }\n"
            + "});\n";
    }

    /**
     * X-axis scale config for histogram (category axis).
     * Uses string labels (bin lo values) with Chart.js default offset:true.
     * offset:true (the default) automatically adds half-bar padding at both
     * axis edges so the first and last bars render at FULL width -- no clipping.
     * Do NOT add offset:false, type:'linear', or numeric min/max here:
     * - offset:false removes the edge padding, causing half-bar clipping
     * - type:'linear' misinterprets string labels
     * - numeric min/max on category axis are treated as category indices (crashes display)
     */
    String buildHistXScaleCat(String xTitle) {
        return buildHistXScaleCat(xTitle, 0);
    }

    String buildHistXScaleCat(String xTitle, int nBins) {
        // Histogram has nBins+1 tick labels (bin edges including final right edge).
        // Pass nBins+1 as the category count for rotation logic.
        return buildAxisConfig("x", xTitle, false, true, false, nBins > 0 ? nBins + 1 : 0);
    }

    /**
     * Builds histogram labels[], datasets[], and tooltip ranges[] strings for a filter slice.
     * Used by buildFilterData so each filter value gets its own histogram
     * binned from its own data range and count.
     * Returns String[3]: {labelsContent, datasetsContent, rangesContent}
     */
    String[] histogramFilterData(DataSet slice) {
        List<Variable> nv = slice.getNumericVariables();
        if (nv.isEmpty()) return new String[]{"", "", "", "0", "0", "0"}; // [0-5]: labels,ds,ranges,xMin,xMax,n
        Variable var = nv.get(0);

        List<Double> vals = new ArrayList<>();
        for (Object v : var.getValues()) {
            if (v instanceof Number) vals.add(((Number) v).doubleValue());
        }
        java.util.Collections.sort(vals);
        int n = vals.size();
        if (n == 0) return new String[]{"", "", "", "0", "0", "0"}; // [0-5]: labels,ds,ranges,xMin,xMax,n

        int nBins = sturgesBins(n);
        if (!o.stats.bins.isEmpty()) {
            try { nBins = Math.max(2, Integer.parseInt(o.stats.bins.trim())); }
            catch (Exception ignore) {}
        }

        double[][] bins = computeBins(vals, nBins);
        double binWidth = bins.length > 0 ? (bins[0][2] - bins[0][1]) : 1.0;

        // Category axis approach: lo-edge string labels + scalar y values
        // Must match the format used by histogram() for the initial render.
        StringBuilder lblJs = new StringBuilder();
        StringBuilder datJs = new StringBuilder();
        StringBuilder rngJs = new StringBuilder();
        StringBuilder cntJs = new StringBuilder(); // v2.6.1: per-bin obs counts
        for (double[] bin : bins) {
            double lo   = bin[1];
            double hi   = bin[2];
            double cnt  = bin[3];
            double yVal;
            switch (o.stats.histtype) {
                case "frequency": yVal = cnt;              break;
                case "fraction":  yVal = cnt / n;         break;
                default:          yVal = cnt / (n * binWidth); break;
            }
            lblJs.append("'").append(String.format(Locale.ROOT, "%.0f", lo)).append("',");
            datJs.append(fmt(yVal)).append(",");   // t2j fix8f (issue #11): sig-fig density (see initial render)
            rngJs.append("'[").append(String.format(Locale.ROOT, "%.2f", lo))
                 .append(", ").append(String.format(Locale.ROOT, "%.2f", hi)).append(")',");
            cntJs.append((int) cnt).append(","); // v2.6.1
        }
        // Append the final right-edge tick label
        double lastHi = bins[bins.length - 1][2];
        lblJs.append("'").append(String.format(Locale.ROOT, "%.0f", lastHi)).append("'");

        double xMin = bins[0][1];
        double xMax = bins[bins.length - 1][2];

        String colFill = col(0);
        String colBord = colS(0);
        String varLbl  = escJs(var.getDisplayName());
        // Scalar data[] matches the category axis label[] approach in histogram()
        String ds = "{label:'" + varLbl + "',data:[" + datJs
            + "],backgroundColor:'" + colFill + "',borderColor:'" + colBord
            + "',borderWidth:1,barPercentage:1.0,categoryPercentage:1.0}";
        return new String[]{
            lblJs.toString(),           // [0] lo-edge string labels (+ final right edge)
            ds,                         // [1] dataset JS string
            rngJs.toString(),           // [2] tooltip range strings
            String.format(Locale.ROOT, "%.4f", xMin), // [3] xMin
            String.format(Locale.ROOT, "%.4f", xMax), // [4] xMax
            String.valueOf(n),           // [5] n -- total obs for this slice
            cntJs.toString()             // [6] per-bin obs counts (v2.6.1)
        };
    }

    /**
     * CI Bar chart (v1.7.0).
     * Uses chartjs-chart-error-bars plugin which registers the "barWithErrorBars"
     * chart type. Each variable becomes one grouped-bar dataset with per-bar
     * yMin/yMax whiskers. The plugin must be loaded via CDN (see build()).
     */
    String ciBar(String id, DataSet data) {
        key("ci", (o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel) + "% CI", "whisker", "#333333", "", "");   // s9j
        if (!data.hasOver()) return barLine(id, data, false); // safety fallback
        Variable ov = data.getOverVariable();

        // x-axis labels -- MUST use ciBarLabels() to match filtered dataset groups
        // (skips n<2 groups and missing-value groups that crash barWithErrorBars)
        String lblJs = ciBarLabels(data);

        // datasets via extracted method (also used by filter data builder)
        String dsJs = ciBarDatasets(data);

        String xTitle = o.axes.xtitle.isEmpty() ? escJs(ov.getDisplayName()) : escJs(o.axes.xtitle);
        String yTitle = o.axes.ytitle.isEmpty() ? "Mean" : escJs(o.axes.ytitle);
        // ciBar groups: valid groups only (ciBarLabels skips n<2 and missing-val groups)
        int nCatCi = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver).size();
        String xScaleCfg = buildAxisConfig("x", xTitle, false, true, false, nCatCi);
        String yScaleCfg = buildAxisConfig("y", yTitle, false, true);
        String animDur   = animDuration();
        String easingCfg = o.chart.easing.isEmpty() ? "" : ",easing:'"+o.chart.easing+"'";
        String legendCfg = buildLegendConfig();
        String annotCfg  = buildAnnotationConfig(false);
        return "new Chart(document.getElementById('" + id + "'), {\n"
            + "  type:'barWithErrorBars',\n"
            + "  data:{labels:[" + lblJs + "],datasets:[" + dsJs + "]},\n"
            + "  options:{\n"
            + "    responsive:true,maintainAspectRatio:true,resizeDelay:150,\n"
            + (o.chart.aspect.isEmpty() ? "" : "    aspectRatio:" + o.chart.aspect + ",\n")
            + "    animation:{duration:" + animDur + easingCfg + "},\n"
            + buildPadding()
            + "    plugins:{legend:" + legendCfg
            + "," + buildCiBarTooltipCfg()
            + dlSuppress()
            + (annotCfg.isEmpty() ? "" : "," + annotCfg)
            + "},\n"
            + "    scales:{x:" + xScaleCfg + ",y:" + yScaleCfg + "}\n"
            + "  }\n"
            + "});\n";
    }

    /**
     * CI Line chart (v1.7.0).
     * For each variable renders three Chart.js datasets:
     *   1. Upper CI bound (transparent line, fill to dataset below)
     *   2. Lower CI bound (transparent line, fill to dataset above)
     *   3. Mean line (solid, visible)
     * Chart.js fills the band between datasets 1 and 2 natively.
     * No extra CDN plugin needed.
     */
    String ciLine(String id, DataSet data) {
        key("ci", (o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel) + "% CI band", "swatch", colAtAlpha(col(0), 0.25), "", "");   // s9j
        if (!data.hasOver()) return barLine(id, data, true); // safety fallback
        Variable ov = data.getOverVariable();
        List<String> groups = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);

        // x-axis labels
        StringBuilder lblJs = new StringBuilder();
        for (String g : groups) lblJs.append("'").append(escJs(sdz(g))).append("',");

        // datasets via extracted method (also used by filter data builder)
        String dsJs = ciLineDatasets(data);

        String xTitle = o.axes.xtitle.isEmpty() ? escJs(ov.getDisplayName()) : escJs(o.axes.xtitle);
        String yTitle = o.axes.ytitle.isEmpty() ? "Mean" : escJs(o.axes.ytitle);
        int nCatCi = groups.size();
        String xScaleCfg = buildAxisConfig("x", xTitle, false, false, true, nCatCi);
        String yScaleCfg = buildAxisConfig("y", yTitle, false, false);
        String animDur   = animDuration();
        String easingCfg = o.chart.easing.isEmpty() ? "" : ",easing:'"+o.chart.easing+"'";
        String legendCfg = buildLegendConfigCiLine();
        String annotCfg  = buildAnnotationConfig(true);
        return "new Chart(document.getElementById('" + id + "'), {\n"
            + "  type:'line',\n"
            + "  data:{labels:[" + lblJs + "],datasets:[" + dsJs + "]},\n"
            + "  options:{\n"
            + "    responsive:true,maintainAspectRatio:true,resizeDelay:150,\n"
            + (o.chart.aspect.isEmpty() ? "" : "    aspectRatio:" + o.chart.aspect + ",\n")
            + "    animation:{duration:" + animDur + easingCfg + "},\n"
            + buildPadding()
            + "    plugins:{legend:" + legendCfg
            + "," + buildCiLineTooltipCfg()
            + dlSuppress()
            + (annotCfg.isEmpty() ? "" : "," + annotCfg)
            + "},\n"
            + "    scales:{x:" + xScaleCfg + ",y:" + yScaleCfg + "}\n"
            + "  }\n"
            + "});\n";
    }

    /**
     * Legend config for ciLine: filters out the upper/lower band datasets
     * so only the mean line entries appear in the legend.
     */
    String buildLegendConfigCiLine() {
        if (o.chart.legend.equals("none")) return "{display:false}";
        StringBuilder sb = new StringBuilder("{display:true,position:'").append(o.chart.legend).append("',");
        sb.append("labels:{").append(legendLabelsCfg());
        // Filter callback: hide datasets whose label ends with " upper" or " lower"
        sb.append(",filter:function(item,data){")
          .append("return !item.text.endsWith(' upper')&&!item.text.endsWith(' lower');}")
          .append("}");
        if (!o.chart.legendtitle.isEmpty())
            sb.append(",title:{display:true,text:'").append(escJs(o.chart.legendtitle)).append("',color:'").append(labelColor()).append("'}");
        sb.append("}");
        return sb.toString();
    }

    // =========================================================================
    // colAtAlpha -- shared helper: return color string at custom alpha.
    // Works with rgba(r,g,b,a), #rrggbb hex, or named CSS color fallback.
    // Used by coefPlot and coefPlotMulti for CI band/whisker colors. (v3.6.0-s6)
    // =========================================================================
    /* t2j fix8 (A7/A10): linewidth() and smooth() are un-defaulted at the arg
     *  boundary; each post-est call site supplies its own historical default here
     *  (connectors: width 2, tension 0 = straight) while honoring an explicit set. */
    private String lineWidthOr(String def) {
        return (o.chart.linewidth == null || o.chart.linewidth.isEmpty()) ? def : o.chart.linewidth;
    }
    private String smoothOr(String def) {
        return (o.chart.smooth == null || o.chart.smooth.isEmpty()) ? def : o.chart.smooth;
    }
    /* t2j fix8 (A13): reflinewidth() controls the null/reference-line width on post-est
     *  charts (coefplot, eventstudy, marginsplot). Empty -> historical default 1. */
    private String refLineWidthOr(String def) {
        return (o.chart.refLineWidth == null || o.chart.refLineWidth.isEmpty()) ? def : o.chart.refLineWidth;
    }

    /** fix9h: the fit-line colour for a series colour -- an opaque dark accent (RGB x 0.55) of
     *  the same hue, so the line stands out on top of the cloud yet stays attributable to its
     *  group; works on #rrggbb, rgb() and rgba() (alpha dropped: the accent is solid). */
    private String fitLineColor(String seriesColor) {
        String c = seriesColor;
        if (c != null && c.startsWith("rgba(")) {
            String[] p = c.substring(5, c.lastIndexOf(')')).split(",");
            if (p.length >= 3) c = "rgb(" + p[0].trim() + "," + p[1].trim() + "," + p[2].trim() + ")";
        }
        return gen.darken(c, 0.55);
    }

    private String colAtAlpha(String baseColor, double alpha) {
        String a = String.format(Locale.ROOT, "%.2f", alpha);
        if (baseColor.startsWith("rgba(")) {
            String inner = baseColor.substring(5, baseColor.lastIndexOf(')'));
            String[] p = inner.split(",");
            if (p.length >= 3)
                return "rgba(" + p[0].trim() + "," + p[1].trim() + "," + p[2].trim() + "," + a + ")";
        } else if (baseColor.startsWith("#") && baseColor.length() >= 7) {
            int r = Integer.parseInt(baseColor.substring(1,3), 16);
            int g = Integer.parseInt(baseColor.substring(3,5), 16);
            int b = Integer.parseInt(baseColor.substring(5,7), 16);
            return "rgba(" + r + "," + g + "," + b + "," + a + ")";
        }
        return baseColor; // named CSS color fallback
    }

    // -- CI dataset builders (reusable by filter data builder) -----------------

    // =========================================================================
    // coefPlot -- Post-estimation coefficient plot (v3.6.0)
    // Renders coefficients with CI from pre-computed pipe-separated args.
    // Supports: coefstyle(scatter|bar), cistyle(whisker|band|bar),
    //           horizontal (coefplot default) and vertical (eventstudy default).
    // No data scan -- all values come from o.chart.pe* fields.
    // =========================================================================

    // =====================================================================
    // EVENT STUDY (v3.6.0-t2d): numeric relative-time axis, pre/post colours,
    // one marker shape per model, models dodged, CI as rectangles (default) /
    // whiskers / band. Used whenever every coefficient name parsed to a
    // relative time (o.chart.peXpos non-empty); otherwise coefPlot() draws the
    // category-axis version as before.
    //   Inputs: peNames/peCoefs/peLower/peUpper (+ peLevels2Lo/Hi) tilde-grouped
    //   per model, peXpos tilde-grouped relative times ("." = not a period, kept
    //   off the axis), peEstNames labels, peCiStyle bar|whisker|band, peNoci,
    //   peConnected, peRefval, pePexline.
    // =====================================================================
    String eventStudy(String id) {
        String[] gNames = o.chart.peNames.split("~", -1);
        String[] gCoef  = o.chart.peCoefs.split("~", -1);
        String[] gLo    = o.chart.peLower.split("~", -1);
        String[] gHi    = o.chart.peUpper.split("~", -1);
        String[] gX     = o.chart.peXpos.split("~", -1);
        String[] gP     = o.chart.pePvals.split("~", -1);   // t2j fix8w (P4): per-model p-values
        boolean anyHollowES = false;
        String esBg = HOLLOW_FILL;   // fix9u: hollow event-study markers are transparent (was plot background)
        boolean hasInner = !o.chart.peLevels2Lo.isEmpty() && !o.chart.peLevels2Val.isEmpty();
        String[] gLo2 = hasInner ? o.chart.peLevels2Lo.split("~", -1) : new String[0];
        String[] gHi2 = hasInner ? o.chart.peLevels2Hi.split("~", -1) : new String[0];
        int k = gNames.length;
        String[] mLabels = new String[k];
        String[] estl = o.chart.peEstlabels.isEmpty() ? new String[0] : o.chart.peEstlabels.split("~", -1);
        String[] estn = o.chart.peEstNames.isEmpty() ? new String[0] : o.chart.peEstNames.split("~", -1);
        for (int m = 0; m < k; m++) mLabels[m] = m < estl.length && !estl[m].isEmpty() ? estl[m] : (m < estn.length && !estn[m].isEmpty() ? estn[m] : "Model " + (m + 1));
        boolean noci = o.chart.peNoci;
        // t2f: reference (omitted, normalised-to-0) period. "auto" = the single missing integer between the
        // first and last period; "none" = no marker; "<#>" = user's refperiod(). together = post series joined
        // to the reference (event_plot's together); default keeps leads and lags as separate series (BJS 2021).
        String esRefMode = "auto"; boolean esTogether = false;
        for (String kv : (o.chart.esOpts == null ? "" : o.chart.esOpts).split(";")) {
            String[] p2 = kv.trim().split("=", 2);
            if (p2.length < 2) continue;
            if (p2[0].trim().equals("ref")) esRefMode = p2[1].trim();
            if (p2[0].trim().equals("together")) esTogether = p2[1].trim().equals("1");
        }
        String ciStyle = o.chart.peCiStyle == null || o.chart.peCiStyle.isEmpty() ? "bar" : o.chart.peCiStyle.trim().split("\\s+")[0];
        if (ciStyle.equals("area") || ciStyle.equals("rectangle")) ciStyle = ciStyle.equals("area") ? "band" : "bar";

        // colours (t2e): pre / post = slots 1 and 2 of colors()/palette()/theme; marker shape per model.
        // cicolors(pre|post) recolours the CI layer only (one value = both phases); with several
        // models cicolors() keeps its coefplot meaning (one colour per model) -- see esCiCol below.
        String preFill = colS(0), postFill = colS(1);
        String ciPre = preFill, ciPost = postFill;
        String[] ciByModel = null;
        if (!o.chart.peCicolors.isEmpty()) {
            String[] cc = o.chart.peCicolors.split("\\|", -1);
            if (k > 1) { ciByModel = cc; }
            else { ciPre = cc[0].trim().isEmpty() ? preFill : cc[0].trim(); ciPost = cc.length > 1 && !cc[1].trim().isEmpty() ? cc[1].trim() : ciPre; }
        }
        String[] shapes = peShapeCycle();   // t2j fix6: honor pointstyles()/msymbol()
        double dodge = k > 1 ? Math.min(0.28, 0.9 / k) : 0;   // x units between models

        // gather points
        double tmin = Double.MAX_VALUE, tmax = -Double.MAX_VALUE, ymin = Double.MAX_VALUE, ymax = -Double.MAX_VALUE;
        StringBuilder ds = new StringBuilder();
        StringBuilder ci = new StringBuilder("[");      // per point: [x, lo, hi, lo2, hi2, pre(1/0), m]
        int nPts = 0;
        // t2j fix8 (deep-dive r2): event-study markers honor pointsize() (were hardcoded
        // pointRadius:5). Default follows the package-wide marker default like every other
        // chart; hover radius is size+2.
        String esPSize  = o.chart.pointsize.isEmpty() ? "5" : o.chart.pointsize;
        String esPHover = String.format(Locale.ROOT, "%.0f", parseDouble(esPSize, 5) + 2);
        for (int m = 0; m < k; m++) {
            String[] nm = gNames[m].split("\\|", -1), bs = gCoef[m].split("\\|", -1), lo = gLo[m].split("\\|", -1), hi = gHi[m].split("\\|", -1);
            String[] xs = m < gX.length ? gX[m].split("\\|", -1) : new String[0];
            String[] lo2 = hasInner && m < gLo2.length ? gLo2[m].split("\\|", -1) : new String[0];
            String[] hi2 = hasInner && m < gHi2.length ? gHi2[m].split("\\|", -1) : new String[0];
            double off = k > 1 ? (m - (k - 1) / 2.0) * dodge : 0;
            StringBuilder pre = new StringBuilder(), post = new StringBuilder(), linePre = new StringBuilder(), linePost = new StringBuilder();
            StringBuilder preFillA = new StringBuilder("["), postFillA = new StringBuilder("[");   // t2j fix8w (P4): per-point fills
            String[] pv = m < gP.length ? gP[m].split("\\|", -1) : new String[0];
            java.util.TreeSet<Long> seen = new java.util.TreeSet<>();
            double mMin = Double.MAX_VALUE, mMax = -Double.MAX_VALUE;
            Double zeroRow = null;   // t2f fix 3: an explicit normalised row (b = lo = hi = 0, e.g. lwdid's -1) IS the reference
            for (int i = 0; i < nm.length; i++) {
                double t, b, l, h;
                try { t = Double.parseDouble(i < xs.length ? xs[i] : "."); b = Double.parseDouble(bs[i]); } catch (Exception e) { continue; }
                if (t >= 9998) continue;   // summary rows (Pre_avg / Post_avg) are not periods
                try { l = Double.parseDouble(lo[i]); h = Double.parseDouble(hi[i]); } catch (Exception e) { l = b; h = b; }
                if (t < 0 && b == 0 && l == 0 && h == 0 && !esRefMode.equals("none")) { if (zeroRow == null) zeroRow = t; continue; }
                double l2 = l, h2 = h;
                if (hasInner && i < lo2.length) { try { l2 = Double.parseDouble(lo2[i]); h2 = Double.parseDouble(hi2[i]); } catch (Exception e) { /* keep */ } }
                tmin = Math.min(tmin, t); tmax = Math.max(tmax, t); mMin = Math.min(mMin, t); mMax = Math.max(mMax, t);
                if (t == Math.rint(t)) seen.add((long) t);
                ymin = Math.min(ymin, noci ? b : l); ymax = Math.max(ymax, noci ? b : h);
                String pt = "{x:" + fmt(t + off) + ",y:" + fmt(b) + ",t:" + fmt(t) + ",lo:" + fmt(l) + ",hi:" + fmt(h) + ",m:" + m + ",n:'" + escJs(nm[i]) + "'}";
                boolean solid = sigFlag(pv, i) == 1; if (!solid) anyHollowES = true;
                if (t < 0) { pre.append(pre.length() == 0 ? "" : ",").append(pt); preFillA.append(preFillA.length() == 1 ? "" : ",").append("'").append(solid ? preFill : esBg).append("'"); }
                else { post.append(post.length() == 0 ? "" : ",").append(pt); postFillA.append(postFillA.length() == 1 ? "" : ",").append("'").append(solid ? postFill : esBg).append("'"); }
                StringBuilder ln = t < 0 ? linePre : linePost;
                ln.append(ln.length() == 0 ? "" : ",").append("{x:" + fmt(t + off) + ",y:" + fmt(b) + "}");
                if (nPts++ > 0) ci.append(",");
                ci.append("[").append(fmt(t + off)).append(",").append(fmt(l)).append(",").append(fmt(h)).append(",").append(fmt(l2)).append(",").append(fmt(h2)).append(",").append(t < 0 ? 1 : 0).append(",").append(m).append("]");
            }
            String shape = shapes[m % shapes.length];
            // reference period for this model (t2f)
            Double ref = null;
            if (esRefMode.equals("none")) ref = null;
            else if (esRefMode.equals("auto")) {
                if (zeroRow != null) ref = zeroRow;
                else if (mMin < 0 && mMax >= 0) {
                    java.util.List<Long> gaps = new java.util.ArrayList<>();
                    for (long v = (long) Math.ceil(mMin); v <= (long) Math.floor(mMax); v++) if (!seen.contains(v)) gaps.add(v);
                    if (gaps.size() == 1 && gaps.get(0) < 0) ref = (double) gaps.get(0);
                }
            } else { try { ref = Double.parseDouble(esRefMode); } catch (Exception e) { ref = null; } }
            if (ref != null) {
                tmin = Math.min(tmin, ref); tmax = Math.max(tmax, ref); ymin = Math.min(ymin, 0); ymax = Math.max(ymax, 0);
                String rx = fmt(ref + off);
                // CI record with lo = hi = 0 and flag 7 = 1: the pre ribbon pinches to zero width here; rectangles/whiskers skip it
                if (nPts++ > 0) ci.append(",");
                ci.append("[").append(rx).append(",0,0,0,0,1,").append(m).append(",1]");
                if (esTogether) { if (nPts++ > 0) ci.append(","); ci.append("[").append(rx).append(",0,0,0,0,0,").append(m).append(",1]"); }
                linePre.append(linePre.length() == 0 ? "" : ",").append("{x:" + rx + ",y:0}");
                if (esTogether) linePost.insert(0, "{x:" + rx + ",y:0}" + (linePost.length() == 0 ? "" : ","));
                ds.append(ds.length() == 0 ? "" : ",");
                ds.append("{label:'").append(escJs(k > 1 ? mLabels[m] + " (reference)" : "Reference period")).append("',type:'scatter',data:[{x:").append(rx).append(",y:0,t:").append(fmt(ref)).append(",ref:1,m:").append(m).append("}],")
                  .append("backgroundColor:'rgba(0,0,0,0)',borderColor:'").append(preFill).append("',pointStyle:'").append(shape).append("',pointRadius:").append(esPSize).append(",pointHoverRadius:").append(esPHover).append(",borderWidth:1.5,order:0,esModel:").append(m).append(",esRef:true}");
            }
            // one dataset per model x period -- the legend is rebuilt below, so labels here are internal
            ds.append(ds.length() == 0 ? "" : ",");
            String preLab = k > 1 ? mLabels[m] + " (pre)" : "Pre-treatment", postLab = k > 1 ? mLabels[m] + " (post)" : "Post-treatment";
            // t2j fix8w (P4): backgroundColor is a per-point list (series colour = filled, plot
            // background = hollow, p > 0.1); borderWidth 2 makes the hollow ring readable
            ds.append("{label:'").append(escJs(preLab)).append("',type:'scatter',data:[").append(pre).append("],")
              .append("backgroundColor:").append(preFillA).append("],borderColor:'").append(preFill).append("',pointStyle:'").append(shape).append("',pointRadius:").append(esPSize).append(",pointHoverRadius:").append(esPHover).append(",borderWidth:2,order:1,esModel:").append(m).append(",esPre:true,_legendFill:'").append(preFill).append("'}");
            ds.append(",{label:'").append(escJs(postLab)).append("',type:'scatter',data:[").append(post).append("],")
              .append("backgroundColor:").append(postFillA).append("],borderColor:'").append(postFill).append("',pointStyle:'").append(shape).append("',pointRadius:").append(esPSize).append(",pointHoverRadius:").append(esPHover).append(",borderWidth:2,order:1,esModel:").append(m).append(",esPre:false,_legendFill:'").append(postFill).append("'}");
            if (o.chart.peConnected) {
                // leads and lags as separate lines (BJS 2021); together = one line through the reference
                String lineStyle = "borderColor:'rgba(120,120,120,0.55)',borderWidth:1.2,borderDash:[4,3],pointRadius:0,fill:false,order:3,esLine:true,segment:{}}";
                if (esTogether) ds.append(",{label:'").append(escJs(mLabels[m])).append(" (line)',type:'line',data:[").append(linePre).append(linePre.length() > 0 && linePost.length() > 0 ? "," : "").append(linePost).append("],").append(lineStyle);
                else {
                    if (linePre.length() > 0) ds.append(",{label:'").append(escJs(mLabels[m])).append(" (pre line)',type:'line',data:[").append(linePre).append("],").append(lineStyle);
                    if (linePost.length() > 0) ds.append(",{label:'").append(escJs(mLabels[m])).append(" (post line)',type:'line',data:[").append(linePost).append("],").append(lineStyle);
                }
            }
        }
        ci.append("]");
        if (tmin == Double.MAX_VALUE) { tmin = -1; tmax = 1; ymin = -1; ymax = 1; }
        double refVal = 0; try { refVal = Double.parseDouble(o.chart.peRefval); } catch (Exception e) { /* 0 */ }
        boolean showRef = !o.chart.peRefval.equals("none");
        if (showRef) { ymin = Math.min(ymin, refVal); ymax = Math.max(ymax, refVal); }
        double[] pm = plotMarginFrac();
        double[] yr = niceRange(ymin - (ymax - ymin) * pm[2], ymax + (ymax - ymin) * pm[3], 6);
        double xlo = tmin - 0.5 - dodge, xhi = tmax + 0.5 + dodge;

        boolean didSource = o.chart.peCmd != null && o.chart.peCmd.toLowerCase(Locale.ROOT).matches(".*(csdid|jwdid|lwdid|did_|xthdid|eventstudy|event).*");
        String yTitle = !o.axes.ytitle.isEmpty() ? o.axes.ytitle : (didSource ? "ATT" : "Estimate");
        String xTitle = !o.axes.xtitle.isEmpty() ? o.axes.xtitle : "Periods to treatment";
        String lvl = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel;
        // t2j fix8 (deep-dive follow-up): event-study whiskers now honor ciwidth()
        // exactly as coefPlot does (they were hardcoded 1.5 outer / 3.2 inner, so the
        // option was silently ignored here). Default stays 1.5; the inner level stays
        // ~1.7px thicker so nested CIs remain visually distinct. Users can thicken
        // faint whiskers on small-CI data with ciwidth().
        double esCiW = o.chart.peCiwidth.isEmpty() ? 1.5 : parseDouble(o.chart.peCiwidth, 1.5);
        String esCiWStr   = String.format(Locale.ROOT, "%.1f", esCiW);
        String esCiWInner = String.format(Locale.ROOT, "%.1f", esCiW + 1.7);

        // key registrations (elements key)
        if (!noci) {
            key("ci", lvl + "% CI", ciStyle.equals("whisker") ? "whisker" : "swatch", ciStyle.equals("whisker") ? ciPre : colAtAlpha(ciPre, 0.35), "", "");
            if (hasInner) key("ci_inner", o.chart.peLevels2Val + "% CI (inner)", ciStyle.equals("whisker") ? "whisker_thick" : "swatch", ciStyle.equals("whisker") ? ciPre : colAtAlpha(ciPre, 0.6), "", "");
        }
        if (showRef) key("refline", "Null (" + (refVal == Math.rint(refVal) ? String.valueOf((long) refVal) : fmt(refVal)) + ")", "line", "#888888", "[5,5]", "");
        if (ds.indexOf("esRef:true") >= 0) key("esref", "Reference period (= 0)", "outlier", preFill, "", "");
        if (anyHollowES) key("sig", SIG_KEY_LABEL, "outlier", postFill, "", "");   // t2j fix8w (P4)
        if (!o.chart.pePexline.isEmpty()) key("pexline", "Treatment (" + o.chart.pePexline.trim() + ")", "line", "#999999", "[2,3]", "");

        String annotCfgES0 = buildAnnotationConfig(true);
        String annotCfgES = annotCfgES0 == null || annotCfgES0.isEmpty() ? "" : "," + annotCfgES0;
        StringBuilder sb = new StringBuilder();
        sb.append(sigDotJs());   // t2j fix8w (P4): _spkSigLegend for the legend swatches
        sb.append("var _esCI_").append(id).append("=").append(ci).append(";\n");
        sb.append("var _esStyle_").append(id).append("='").append(ciStyle).append("';\n");
        // CI plugin: rectangles / whiskers / band per point, behind the markers
        sb.append("var _esCiPlugin_").append(id).append("={id:'esCI',beforeDatasetsDraw:function(ch){\n")
          .append("  var c=ch.ctx,xs=ch.scales.x,ys=ch.scales.y,a=ch.chartArea;if(!xs||!ys)return;var pts=_esCI_").append(id).append(";var st=_esStyle_").append(id).append(";\n")
          .append("  var preF='").append(ciPre).append("',postF='").append(ciPost).append("';var byModelCol=").append(ciByModel == null ? "null" : "['" + String.join("','", escJsAll(ciByModel)) + "']").append(";\n")
          .append("  function colOf(p){if(byModelCol&&byModelCol[p[6]])return byModelCol[p[6]];return p[5]?preF:postF;}\n")
          .append("  var unit=Math.abs(xs.getPixelForValue(1)-xs.getPixelForValue(0));var w=Math.max(6,Math.min(16,unit*").append(fmt(k > 1 ? Math.max(0.12, dodge * 0.8) : 0.3)).append("));\n")
          .append("  c.save();c.beginPath();c.rect(a.left,a.top,a.right-a.left,a.bottom-a.top);c.clip();\n")
          .append("  function ribbon(arr,lo,hi,alpha){c.beginPath();arr.forEach(function(p,i){var x=xs.getPixelForValue(p[0]),y=ys.getPixelForValue(p[hi]);if(i===0)c.moveTo(x,y);else c.lineTo(x,y);});\n")
          .append("    for(var i=arr.length-1;i>=0;i--){c.lineTo(xs.getPixelForValue(arr[i][0]),ys.getPixelForValue(arr[i][lo]));}c.closePath();c.fillStyle=_spkAlpha(colOf(arr[0]),alpha);c.fill();}\n")
          // t2e: one ribbon per model x phase (pre | post) in that phase's colour -- the ribbon therefore
          // stops at the omitted base period; inner level = darker ribbon on top; a phase with a single
          // point falls back to a rectangle so its CI is never lost.
          .append("  if(st==='band'){var byR={};pts.forEach(function(p){var kk=p[6]+'_'+p[5];(byR[kk]=byR[kk]||[]).push(p);});Object.keys(byR).forEach(function(kk){var arr=byR[kk].slice().sort(function(u,v){return u[0]-v[0];});\n")
          .append("      if(arr.length<2){var p=arr[0];if(p[7])return;var x=xs.getPixelForValue(p[0]),y1=ys.getPixelForValue(p[1]),y2=ys.getPixelForValue(p[2]);c.fillStyle=_spkAlpha(colOf(p),0.22);c.fillRect(x-w/2,Math.min(y1,y2),w,Math.abs(y2-y1));\n")
          .append("        if(p[3]!==p[1]||p[4]!==p[2]){var z1=ys.getPixelForValue(p[3]),z2=ys.getPixelForValue(p[4]);c.fillStyle=_spkAlpha(colOf(p),0.45);c.fillRect(x-w/2,Math.min(z1,z2),w,Math.abs(z2-z1));}return;}\n")
          .append("      ribbon(arr,1,2,0.22);var inner=arr.some(function(p){return p[3]!==p[1]||p[4]!==p[2];});if(inner)ribbon(arr,3,4,0.45);});}\n")
          .append("  pts.forEach(function(p){if(p[7])return;var x=xs.getPixelForValue(p[0]),y1=ys.getPixelForValue(p[1]),y2=ys.getPixelForValue(p[2]);var col=colOf(p);\n")
          .append("    if(st==='bar'){c.fillStyle=_spkAlpha(col,0.32);c.fillRect(x-w/2,Math.min(y1,y2),w,Math.abs(y2-y1));\n")
          .append("      if(p[3]!==p[1]||p[4]!==p[2]){var z1=ys.getPixelForValue(p[3]),z2=ys.getPixelForValue(p[4]);c.fillStyle=_spkAlpha(col,0.55);c.fillRect(x-w/2,Math.min(z1,z2),w,Math.abs(z2-z1));}}\n")
          .append("    else if(st==='whisker'){c.strokeStyle=col;c.lineWidth=").append(esCiWStr).append(";c.beginPath();c.moveTo(x,y1);c.lineTo(x,y2);c.moveTo(x-w/2,y1);c.lineTo(x+w/2,y1);c.moveTo(x-w/2,y2);c.lineTo(x+w/2,y2);c.stroke();\n")
          .append("      if(p[3]!==p[1]||p[4]!==p[2]){var z1=ys.getPixelForValue(p[3]),z2=ys.getPixelForValue(p[4]);c.lineWidth=").append(esCiWInner).append(";c.beginPath();c.moveTo(x,z1);c.lineTo(x,z2);c.stroke();}}\n")
          .append("  });c.restore();}};\n");
        sb.append("function _spkAlpha(cs,a){var m=cs.match(/rgba?\\(([^)]*)\\)/);if(m){var p=m[1].split(',');return 'rgba('+p[0]+','+p[1]+','+p[2]+','+a+')';}if(cs[0]==='#'&&cs.length===7){return 'rgba('+parseInt(cs.slice(1,3),16)+','+parseInt(cs.slice(3,5),16)+','+parseInt(cs.slice(5,7),16)+','+a+')';}return cs;}\n");
        // zero line + treatment line
        // fix9j (user-found, "white spots instead of hollow circles"): the crisp plugin's afterInit
        // resize() draws the chart ONCE before its scales exist; this hook used to save() and set
        // the [5,5] dash first and only then touch ys -> TypeError, swallowed by the resize()
        // try/catch, so the save() was never undone and [5,5] became the context's BASE dash.
        // Chart.js strokes point rings without setting a dash, so every marker ring on the page
        // came out dashed (a hollow marker = a white dot with a broken rim). Guard like esCI does,
        // before anything is saved, and restore in a finally so no later error can leak state.
        sb.append("var _esRef_").append(id).append("={id:'esZero',afterDatasetsDraw:function(ch){var c=ch.ctx,xs=ch.scales.x,ys=ch.scales.y,a=ch.chartArea;if(!xs||!ys||!a)return;c.save();try{c.setLineDash([5,5]);c.lineWidth=1;\n");
        if (showRef) sb.append("  c.strokeStyle='#888888';c.lineWidth=").append(refLineWidthOr("1")).append(";var y=ys.getPixelForValue(").append(fmt(refVal)).append(");if(y>=a.top&&y<=a.bottom){c.beginPath();c.moveTo(a.left,y);c.lineTo(a.right,y);c.stroke();}\n");
        if (!o.chart.pePexline.isEmpty()) sb.append("  c.setLineDash([2,3]);c.strokeStyle='#999999';var x=xs.getPixelForValue(").append(o.chart.pePexline.trim()).append(");if(x>=a.left&&x<=a.right){c.beginPath();c.moveTo(x,a.top);c.lineTo(x,a.bottom);c.stroke();}\n");
        sb.append("  }finally{c.restore();}}};\n");
        // legend: the datasets themselves (Pre-treatment / Post-treatment, or "<model> (pre|post)" with the
        // model's marker shape); connecting-line datasets are filtered out
        // t2j fix8 (A9): honor legend(position)/legend(none); was hardcoded position 'top'.
        String legendCfg = o.chart.legend.equals("none") ? "{display:false}"
            : "{display:true,position:'" + (o.chart.legend.isEmpty() ? "top" : o.chart.legend) + "',labels:{usePointStyle:true,generateLabels:_spkSigLegend,color:'" + labelColor() + "',filter:function(it,data){var d=data.datasets[it.datasetIndex];return !(d&&(d.esLine||d.esRef));}" + postEstLegendExtra() + "}}";
        // ticks: integers over the period range
        StringBuilder xt = new StringBuilder(",includeBounds:false,afterBuildTicks:function(axis){var t=[];for(var v=").append((long) Math.ceil(tmin)).append(";v<=").append((long) Math.floor(tmax)).append(";v++)t.push({value:v,major:false});axis.ticks=t;}");
        String tipTitle = "function(items){return items.length?('t = '+items[0].raw.t):'';}";
        String mArr = "['" + String.join("','", escJsAll(mLabels)) + "']";
        String tipLabel = "function(c){var r=c.raw;if(r.t===undefined)return null;if(r.ref)return 'reference period (normalised to 0)';var s=" + (k > 1 ? mArr + "[r.m]+': '" : "''") + "+_spkFmt(r.y,'coef');if(r.lo!==r.hi)s+='  " + lvl + "% CI ['+_spkFmt(r.lo,'coef')+', '+_spkFmt(r.hi,'coef')+']';return s;}";

        sb.append("new Chart(document.getElementById('").append(id).append("'),{type:'scatter',data:{datasets:[").append(ds).append("]},\n")
          .append("options:{responsive:true,maintainAspectRatio:true,resizeDelay:150,aspectRatio:").append(o.chart.aspect.isEmpty() ? "1.7" : o.chart.aspect).append(",animation:{duration:").append(animDuration()).append("},\n")
          // t2j fix8l (user-reported): the numeric event study draws reference / pre /
          // post as SEPARATE scatter datasets whose points sit at DIFFERENT x per
          // dataIndex (ref@-1, pre[0]@-5, post[0]@0). The ado defaults tooltipmode to
          // 'index' (sparkta.ado ~L1333), and index mode then grouped those unrelated
          // points and averaged the tooltip to the chart centre, showing the wrong
          // period's stats. Force mode:'nearest',intersect:true here (index is never
          // correct for this misaligned-dataset layout): the tooltip shows the single
          // point under the cursor, anchored at that point, and HIDES when the pointer
          // leaves a point (the behaviour the user wants). tooltip STYLE is honored.
          .append("plugins:{legend:").append(legendCfg).append(",tooltip:{").append(tooltipStylePrefix()).append("mode:'nearest',intersect:true,position:'nearest',callbacks:{title:").append(tipTitle).append(",label:").append(tipLabel).append("}},datalabels:{display:false}").append(annotCfgES).append("},\n")
          .append("scales:{x:{type:'linear',min:").append(fmt(xlo)).append(",max:").append(fmt(xhi)).append(",title:{display:true,text:'").append(escJs(xTitle)).append("',color:'").append(labelColor()).append("'},grid:{color:'").append(gridCssColor()).append("'},ticks:{color:'").append(labelColor()).append("'").append(xt).append("}},\n")
          .append("y:{type:'linear',min:").append(fmt(yr[0])).append(",max:").append(fmt(yr[1])).append(",title:{display:true,text:'").append(escJs(yTitle)).append("',color:'").append(labelColor()).append("'},grid:{color:'").append(gridCssColor()).append("'},ticks:{color:'").append(labelColor()).append("'").append(niceTicksJs(yr, showRef ? fmt(refVal) : "")).append("}}}},\n")
          .append("plugins:[").append(noci ? "" : "_esCiPlugin_" + id + ",").append("_esRef_").append(id).append("]});\n");
        return sb.toString();
    }
    private static String fmt(double v) { if (Double.isNaN(v)) return "null"; String s = String.format(java.util.Locale.ROOT, "%.10g", v); return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s; }
    private String[] escJsAll(String[] a) { String[] r = new String[a.length]; for (int i = 0; i < a.length; i++) r[i] = escJs(a[i]); return r; }

    String coefPlot(String id) {
        // Detect multi-model: tilde (~) in peNames means multiple models
        boolean isMultiModel = o.chart.peNames.contains("~");
        if (isMultiModel) return coefPlotMulti(id);

        // Single-model path (original)
        // Parse pipe-separated data
        String[] names = o.chart.peNames.split("\\|", -1);
        String[] coefs = o.chart.peCoefs.split("\\|", -1);
        String[] lower = o.chart.peLower.split("\\|", -1);
        String[] upper = o.chart.peUpper.split("\\|", -1);
        String[] ses   = o.chart.peSes.split("\\|", -1);
        String[] pvals = o.chart.pePvals.split("\\|", -1);
        int k = names.length;

        boolean isHorizontal = o.chart.peOrient.equals("h");
        boolean isBarStyle   = o.chart.peCoefStyle.equals("bar");
        // t2j fix8 (deep-dive r3): default an empty cistyle to "whisker" (the ado already
        // does this for coefplot, but relying on it was fragile -- an empty value fell
        // through every CI branch and drew dots with NO interval, silently dropping the CI).
        String ciStyle       = (o.chart.peCiStyle == null || o.chart.peCiStyle.isEmpty())
                                 ? "whisker" : o.chart.peCiStyle;  // whisker | band | bar

        // Colors -- use existing palette via col() accessor (initialized by HtmlGenerator)
        String mainColor = col(0);

        // cicolors(): per-model CI color override. Single-model: first token only.
        String ciColor;
        if (!o.chart.peCicolors.isEmpty()) {
            String[] ciColorArr = o.chart.peCicolors.split("\\|", -1);
            ciColor = ciColorArr[0].trim();
        } else {
            ciColor = mainColor;
        }
        // Band fill at reduced opacity for readability
        String bandFillColor   = colAtAlpha(ciColor, 0.18);
        String bandBorderColor = colAtAlpha(ciColor, 0.55);
        // Legacy alpha variants (used by cistyle(bar))
        String mainColorAlpha  = colAtAlpha(mainColor, 0.22);
        String mainColorBorder = colAtAlpha(mainColor, 0.35);

        // ciwidth(): CI line thickness. Defaults: 1.5 whisker, 0.5 band border
        double ciWidthVal   = o.chart.peCiwidth.isEmpty() ? 1.5 : parseDouble(o.chart.peCiwidth, 1.5);
        String ciWidthStr   = String.format(Locale.ROOT, "%.1f", ciWidthVal);
        // t2j fix8 (deep-dive r2): inner nested-CI whisker scales with ciwidth() like
        // eventStudy/marginsPlot (was hardcoded 3.5, ignoring the option and able to
        // fall thinner than a user-widened outer whisker). Default 1.5+2.0 = 3.5.
        String innerWhiskW  = String.format(Locale.ROOT, "%.1f", ciWidthVal + 2.0);
        String bandBorderWd = String.format(Locale.ROOT, "%.1f", Math.min(ciWidthVal, 0.8));

        // refval(): reference line value. "none" suppresses. Default set by ado.
        boolean showRefLine = !o.chart.peRefval.equals("none");
        double refLineVal   = 0.0;
        if (showRefLine) {
            try { refLineVal = Double.parseDouble(o.chart.peRefval.trim()); }
            catch (NumberFormatException e) { showRefLine = false; }
        }

        // Build category labels array (coefficient names)
        // v3.6.0: headings() support -- parse heading positions and texts
        // Format: "coefname1 Heading|coefname2 Heading" -- heading appears ABOVE that coef
        java.util.HashMap<Integer, String> headingMap = new java.util.HashMap<>();
        if (!o.chart.peHeadings.isEmpty()) {
            String[] hEntries = o.chart.peHeadings.split("\\|", -1);
            for (String entry : hEntries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;
                // First word is the coefname (raw name), rest is heading text
                int sp = entry.indexOf(' ');
                if (sp < 0) continue;
                String hCoef = entry.substring(0, sp).trim();
                String hText = entry.substring(sp + 1).trim();
                // Find position index in names array
                for (int i = 0; i < k; i++) {
                    if (names[i].trim().equals(hCoef)) {
                        headingMap.put(i, hText);
                        break;
                    }
                }
            }
        }
        boolean hasHeadings = !headingMap.isEmpty();

        // Build labels JS -- apply display label resolution (v3.6.0-s6c25)
        // Use gen.resolveCoefLabel() for Java-time resolution (avoids JS timing issue)
        StringBuilder lblJs = new StringBuilder("[");
        if (hasHeadings) {
            for (int i = 0; i < k; i++) {
                if (i > 0) lblJs.append(",");
                String dispName = gen.resolveCoefLabel(names[i].trim());
                if (headingMap.containsKey(i)) {
                    lblJs.append("['").append(escJs(headingMap.get(i))).append("','")
                         .append(escJs(dispName)).append("']");
                } else {
                    lblJs.append("['','").append(escJs(dispName)).append("']");
                }
            }
        } else {
            for (int i = 0; i < k; i++) {
                if (i > 0) lblJs.append(",");
                lblJs.append("'").append(escJs(gen.resolveCoefLabel(names[i].trim()))).append("'");
            }
        }
        lblJs.append("]");

        // Build data arrays
        StringBuilder coefArr  = new StringBuilder("[");
        StringBuilder lowerArr = new StringBuilder("[");
        StringBuilder upperArr = new StringBuilder("[");
        for (int i = 0; i < k; i++) {
            if (i > 0) { coefArr.append(","); lowerArr.append(","); upperArr.append(","); }
            coefArr.append(coefs[i].trim());
            lowerArr.append(lower[i].trim());
            upperArr.append(upper[i].trim());
        }
        coefArr.append("]"); lowerArr.append("]"); upperArr.append("]");

        // Build inner CI arrays for levels() (v3.6.0-s7b)
        // Strip leading | from pipe-sep strings before splitting (ado accumulates "val1|val2..." or "|val1|val2...")
        String lo2raw = o.chart.peLevels2Lo.startsWith("|") ? o.chart.peLevels2Lo.substring(1) : o.chart.peLevels2Lo;
        String hi2raw = o.chart.peLevels2Hi.startsWith("|") ? o.chart.peLevels2Hi.substring(1) : o.chart.peLevels2Hi;
        boolean hasLevels2 = !lo2raw.isEmpty() && !hi2raw.isEmpty();
        String[] lower2 = hasLevels2 ? lo2raw.split("\\|", -1) : new String[0];
        String[] upper2 = hasLevels2 ? hi2raw.split("\\|", -1) : new String[0];
        // Clamp to k if mismatched
        hasLevels2 = hasLevels2 && lower2.length >= k && upper2.length >= k;
        StringBuilder lower2Arr = new StringBuilder("[");
        StringBuilder upper2Arr = new StringBuilder("[");
        if (hasLevels2) {
            for (int i = 0; i < k; i++) {
                if (i > 0) { lower2Arr.append(","); upper2Arr.append(","); }
                lower2Arr.append(lower2[i].trim());
                upper2Arr.append(upper2[i].trim());
            }
        }
        lower2Arr.append("]"); upper2Arr.append("]");
        String innerLevel = hasLevels2 ? o.chart.peLevels2Val : "";

        // Build custom tooltip data: [{coef, se, pval, lower, upper, name}, ...]
        StringBuilder tipData = new StringBuilder("[");
        for (int i = 0; i < k; i++) {
            if (i > 0) tipData.append(",");
            // v3.6.0-s8b: numOrNull() -- Stata "." for base/omitted levels was emitted
            // raw (p:.) -> JS SyntaxError -> the whole coefplot failed to render.
            tipData.append("{n:'").append(escJs(gen.resolveCoefLabel(names[i].trim()))).append("'")   // s8n: same label as the axis
                   .append(",b:").append(numOrNull(coefs, i))
                   .append(",se:").append(numOrNull(ses, i))
                   .append(",p:").append(numOrNull(pvals, i))
                   .append(",lo:").append(numOrNull(lower, i))
                   .append(",hi:").append(numOrNull(upper, i))
                   .append("}");
        }
        tipData.append("]");

        // Determine chart type and dataset config
        StringBuilder sb = new StringBuilder();
        sb.append("var _cpTip=").append(tipData).append(";\n");
        // t2j fix8w (P4): filled marker p <= 0.1, hollow otherwise -- one flag per coefficient,
        // read by every dot-drawing plugin below and by the vertical line dataset's point fills
        sb.append(sigDotJs()).append("var _cpSig=").append(sigArr(pvals, k)).append(";\n");
        boolean anyHollow = sigArr(pvals, k).contains("0");
        // t2j fix8x: coefstyle(bar) with the default cistyle(whisker) (and band/area, noci) draws
        // NO point marker, so the fix8w "Hollow marker" key advertised something that was never
        // drawn (t_coef_bar smoke test). Bars now carry the significance themselves: p > 0.1 bars
        // are HOLLOW (outline only, no fill) and the key says so with an outline swatch.
        if (anyHollow) {
            if (isBarStyle) key("sig", SIG_BAR_KEY_LABEL, "hollowbox", mainColor, "", "");
            else            key("sig", SIG_KEY_LABEL, "outlier", mainColor, "", "");
        }

        // refval plugin: parameterized reference line (v3.6.0-s6)
        String zeroLineColor = labelColor();
        String refValJs = String.format(Locale.ROOT, "%.6f", refLineVal);
        String zeroPlugin;
        if (!showRefLine) {
            zeroPlugin = "{id:'cpZero',beforeDatasetsDraw:function(){}}";
        } else if (isHorizontal) {
            zeroPlugin = "{id:'cpZero',beforeDatasetsDraw:function(chart){"
                + "var ctx=chart.ctx,xS=chart.scales.x,a=chart.chartArea;"
                + "var px=xS.getPixelForValue(" + refValJs + ");"
                + "if(px>=a.left&&px<=a.right){"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.beginPath();ctx.setLineDash([5,3]);"
                + "ctx.strokeStyle='" + zeroLineColor + "';ctx.lineWidth=" + refLineWidthOr("1") + ";"
                + "ctx.moveTo(px,a.top);ctx.lineTo(px,a.bottom);ctx.stroke();"
                + "ctx.restore();}}}";
        } else {
            zeroPlugin = "{id:'cpZero',beforeDatasetsDraw:function(chart){"
                + "var ctx=chart.ctx,yS=chart.scales.y,a=chart.chartArea;"
                + "var px=yS.getPixelForValue(" + refValJs + ");"
                + "if(px>=a.top&&px<=a.bottom){"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.beginPath();ctx.setLineDash([5,3]);"
                + "ctx.strokeStyle='" + zeroLineColor + "';ctx.lineWidth=" + refLineWidthOr("1") + ";"
                + "ctx.moveTo(a.left,px);ctx.lineTo(a.right,px);ctx.stroke();"
                + "ctx.restore();}}}";
        }

        // pexline: optional vertical reference line for coefplot/eventstudy (v3.6.0-s7a)
        String pexlinePlugin1 = buildPexlinePlugin(isHorizontal, o.chart.pePexline,
            o.chart.pePexline.isEmpty() ? "" : "#999999", gen.isDark());
        String pexSuffix1 = pexlinePlugin1.isEmpty() ? "" : "," + pexlinePlugin1;

        // t2j fix8 (deep-dive r3): significance stars ON THE CANVAS (user request). The
        // table exports already carry stars; this draws them next to each coefficient
        // marker using the SAME thresholds (stars(), default 0.10/0.05/0.01) so the
        // canvas and the table agree. One plugin, appended via pexSuffix1 so every cistyle/
        // orientation branch picks it up (all 9 plugin arrays append pexSuffix1). Suppressed
        // by nostars. p from _cpTip[i].p; skipped for base/omitted coefficients (b==null).
        if (!o.table.noStars && o.table.stars.length > 0) {
            StringBuilder thr = new StringBuilder("[");
            for (int _si = 0; _si < o.table.stars.length; _si++) { if (_si > 0) thr.append(","); thr.append(o.table.stars[_si]); }
            thr.append("]");
            String starCol = labelColor();
            String cpStars = ",{id:'cpStars',afterDatasetsDraw:function(chart){"
                + "var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;var _T=" + thr + ";"
                + "function _st(p){if(p==null||isNaN(p))return '';var s='';for(var j=0;j<_T.length;j++){if(p<_T[j])s+='*';}return s;}"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();"
                + "ctx.fillStyle='" + starCol + "';ctx.font='bold 11px sans-serif';"
                + (isHorizontal
                    ? "ctx.textAlign='left';ctx.textBaseline='middle';for(var i=0;i<_cpTip.length;i++){var d=_cpTip[i];if(d.b==null)continue;var st=_st(d.p);if(!st)continue;var xr=xS.getPixelForValue(d.hi!=null?d.hi:d.b),yp=yS.getPixelForValue(i);ctx.fillText(st,xr+4,yp);}"
                    : "ctx.textAlign='center';ctx.textBaseline='bottom';for(var i=0;i<_cpTip.length;i++){var d=_cpTip[i];if(d.b==null)continue;var st=_st(d.p);if(!st)continue;var xp=xS.getPixelForValue(i),yh=yS.getPixelForValue(d.hi!=null?d.hi:d.b);ctx.fillText(st,xp,yh-4);}")
                + "ctx.restore()}}";
            pexSuffix1 = pexSuffix1 + cpStars;
        }

        // Compute y-axis range with padding (Stata convention: ~10% beyond CI extremes)
        double yMin = Double.MAX_VALUE, yMax = -Double.MAX_VALUE;
        for (int i = 0; i < k; i++) {
            double lo = Double.parseDouble(lower[i].trim());
            double hi = Double.parseDouble(upper[i].trim());
            if (lo < yMin) yMin = lo;
            if (hi > yMax) yMax = hi;
        }
        // s8r: include the reference line in the DATA range, anchor nice ticks to it,
        // then add the plotmargin() cushion outside the ticks
        if (showRefLine) { if (yMin > refLineVal) yMin = refLineVal; if (yMax < refLineVal) yMax = refLineVal; }
        double[] cpNice = niceRange(yMin, yMax, 6);
        double yRange = cpNice[1] - cpNice[0];
        double[] pmfC = plotMarginFrac();
        double yPad = yRange * Math.max(pmfC[2], pmfC[3]);  // plotmargin() (default 10%) each side
        yMin = cpNice[0] - yPad;
        yMax = cpNice[1] + yPad;

        // Aspect ratio: Chart.js aspectRatio = width/height
        // Stata default graph: xsize(5.5)/ysize(4) = 1.375 (wider than tall)
        // For coefplots we want slightly squarer to give vertical CI room
        // Base 1.45 (Stata-like landscape), taller for many coefficients
        double cjAspect = 1.45;
        if (k >= 6) cjAspect = 1.55;   // more coefficients = wider to avoid label crowding
        if (k <= 2) cjAspect = 1.30;   // fewer coefficients = squarer

        // Point style
        String pStyle = o.chart.pointstyle.isEmpty() ? "circle" : o.chart.pointstyle;
        String pSize = o.chart.pointsize.isEmpty() ? "6" : o.chart.pointsize;

        // Build the chart
        if (isBarStyle) {
            // coefstyle(bar): use Chart.js bar chart
            String indexAxis = isHorizontal ? "'y'" : "'x'";
            sb.append("new Chart(document.getElementById('").append(id).append("'),{\n")
              .append("  type:'bar',\n")
              .append("  data:{\n")
              .append("    labels:").append(lblJs).append(",\n")
              .append("    datasets:[{\n")
              .append("      data:").append(coefArr).append(",\n")
              // t2j fix8x: per-bar fill -- filled when p <= 0.1, hollow (transparent) otherwise
              .append("      backgroundColor:_cpSig.map(function(s){return s?'").append(mainColorAlpha).append("':'rgba(0,0,0,0)';}),\n")
              .append("      borderColor:'").append(mainColor).append("',\n")
              .append("      borderWidth:1,borderRadius:2\n")
              .append("    }]\n")
              .append("  },\n");
        } else {
            if (isHorizontal) {
                // Horizontal coefplot (Jann convention): coefficients on y-axis, values on x-axis.
                // t2j fix8n (user-reported): fix8m used a transparent type:'bar' whose LENGTH
                // encoded the coefficient (baseline 0 -> value), so a small coefficient (e.g.
                // weight=3.5 on a -1700..6700 axis) produced a ~1px-wide hit target that could
                // not be hovered -- only a large coefficient (car origin=3673) had a wide enough
                // bar. That is why only one tooltip fired. FIX: render the coefficient markers as
                // REAL Chart.js scatter points at (value, index) -- exactly like the multi-model
                // coefplot, which works. The points are invisible (pointRadius:0) because the
                // plugin below still draws the styled dot, but carry a fixed-pixel hitRadius, so
                // mode:'nearest',intersect:true lands on EVERY coefficient uniformly regardless
                // of its magnitude, and hides when the cursor leaves the point. The y-axis becomes
                // linear+reversed (see scales) so point y=i and the plugins' getPixelForValue(i)
                // stay in lock-step.
                sb.append("new Chart(document.getElementById('").append(id).append("'),{\n")
                  .append("  type:'scatter',\n")
                  .append("  data:{\n")
                  .append("    labels:").append(lblJs).append(",\n")
                  .append("    datasets:[{\n")
                  .append("      data:(").append(coefArr).append(").map(function(v,i){return {x:v,y:i};}),\n")
                  .append("      pointRadius:0,pointHoverRadius:0,hitRadius:12,\n")
                  .append("      borderWidth:0,showLine:false\n")
                  .append("    }]\n")
                  .append("  },\n");
            } else {
                // Vertical coefplot / eventstudy: line chart, category x-axis.
                // connectLine: true for eventstudy by default (not band).
                // connected flag forces line on regardless of CI style.
                // Also allowed on coefplot for sensitivity/ordered coefficient plots.
                boolean connectLine = o.chart.peConnected
                    || (o.type.equals("eventstudy") && !ciStyle.equals("band"));
                sb.append("new Chart(document.getElementById('").append(id).append("'),{\n")
                  .append("  type:'line',\n")
                  .append("  data:{\n")
                  .append("    labels:").append(lblJs).append(",\n")
                  .append("    datasets:[{\n")
                  .append("      data:").append(coefArr).append(",\n")
                  .append("      borderColor:'").append(mainColor).append("',\n")
                  .append("      backgroundColor:'").append(mainColor).append("',\n")
                  // t2j fix8w (P4): hollow point when p > 0.1 (fix9u: transparent fill, 2 px ring)
                  .append("      pointBackgroundColor:").append(sigFillArr(pvals, k, mainColor)).append(",pointBorderColor:'").append(mainColor).append("',pointBorderWidth:2,\n")
                  .append("      pointRadius:").append(pSize).append(",\n")
                  .append("      pointStyle:'").append(pStyle).append("',\n")
                  .append("      pointHoverRadius:").append((parseDouble(pSize, 4) + 2)).append(",\n")
                  .append("      borderWidth:").append(connectLine ? lineWidthOr("2") : "0")
                  .append(",fill:false,tension:").append(smoothOr("0")).append(",showLine:").append(connectLine).append("\n")
                  .append("    }]\n")
                  .append("  },\n");
            }
        }

        // Options block
        String labelCol = labelColor();
        String gridCol  = gridCssColor();
        sb.append("  options:{\n")
          .append("    responsive:true,maintainAspectRatio:true,resizeDelay:150,\n")
          .append("    aspectRatio:").append(String.format(Locale.ROOT, "%.2f", cjAspect)).append(",\n")
          // t2j fix8m/fix8n (user-reported): the coefplot tooltip previously used index mode
          // with intersect:false, so it fired whenever the pointer was anywhere in a
          // coefficient's row/column and never dismissed ("appears on any movement of
          // cursor... doesnt go away"). Every coefplot now uses REAL markers -- the
          // horizontal plot as invisible-but-hittable scatter points (fix8n), the vertical
          // plot as line points, the bar style as bars -- so nearest+intersect:true lands on
          // the coefficient under the cursor and HIDES when it leaves, the same action as the
          // event study. The tooltip block below also pins mode/intersect so the ado
          // tooltipmode(index) default cannot re-enable index mode here.
          .append("    interaction:{mode:'nearest',intersect:true},\n");

        if (isBarStyle || isHorizontal) {
            // indexAxis:'y' for the horizontal bar STYLE (real bars) AND the horizontal
            // scatter coefplot (fix8n). For scatter the points carry explicit {x,y} so
            // indexAxis does not move them, but it declares the value axis as x -- which the
            // interaction logic and the viz auditor both rely on to know this is horizontal.
            sb.append("    indexAxis:").append(isHorizontal ? "'y'" : "'x'").append(",\n");
        }

        // Tooltip
        sb.append("    plugins:{\n")
          .append("      legend:{display:false},\n")
          // t2j fix8m: pin mode:'nearest',intersect:true here (mirrors the event study).
          // tooltip.mode/intersect override the chart interaction, so this also neutralises
          // the ado tooltipmode(index) default (postEstTooltipPrefix would emit mode:'index'
          // and re-break dismissal). tooltip STYLE keys are still honored via the prefix.
          .append("      tooltip:{").append(tooltipStylePrefix()).append("mode:'nearest',intersect:true,\n")
          .append("        callbacks:{\n")
          .append("          title:function(ctx){\n")
          .append("            var i=ctx[0].dataIndex;\n")
          .append("            return _cpTip[i].n;\n")
          .append("          },\n")
          .append("          label:function(ctx){\n")
          .append("            var i=ctx.dataIndex;\n")
          .append("            var d=_cpTip[i];var f=function(v,k){return _spkFmt(v,'coef');};\n")
          .append("            if(d.b==null)return ['(base or omitted)'];\n")
          .append("            return ['Coef: '+f(d.b,4),\n")
          .append("                    'SE: '+f(d.se,4),\n")
          .append("                    'p: '+_spkFmt(d.p,'p'),\n")
          .append("                    'CI: ['+f(d.lo,3)+', '+f(d.hi,3)+']'];\n")
          .append("          }\n")
          .append("        }\n")
          .append("      },\n")
          .append("      datalabels:false\n")
          .append("    },\n");

        // Scales
        // levels2: build inner CI plugin suffix for all cistyle variants (v3.6.0-s7b)
        String levels2Suffix = "";
        if (hasLevels2) {
            String ciCol2 = ciColor;
            // A2 (fix8): inner nested-CI band/area fill+stroke must honor cicolors()
            // exactly as the whisker path (which already uses ciCol2). Re-derive the
            // fill (0.28) and edge (band 0.6 / area 0.55) alphas from the same
            // cicolors-aware source instead of the old hardcoded rgba(78,121,167).
            String cfIn2   = colAtAlpha(ciCol2, 0.28);
            String csIn2b  = colAtAlpha(ciCol2, 0.6);
            String csIn2a  = colAtAlpha(ciCol2, 0.55);
            if (isHorizontal) {
                if (ciStyle.equals("whisker") || ciStyle.equals("bar")) {
                    levels2Suffix = ",{id:'cpInner',afterDatasetsDraw:function(chart){"
                        + "var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;"
                        + "var lo2=" + lower2Arr + ",hi2=" + upper2Arr + ";"
                        + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.strokeStyle='" + ciCol2 + "';ctx.lineWidth=" + innerWhiskW + ";"
                        + "for(var i=0;i<lo2.length;i++){"
                        + "var yPx=yS.getPixelForValue(i);"
                        + "var xLo=xS.getPixelForValue(lo2[i]),xHi=xS.getPixelForValue(hi2[i]);"
                        + "ctx.beginPath();ctx.moveTo(xLo,yPx);ctx.lineTo(xHi,yPx);ctx.stroke();"
                        + "ctx.beginPath();ctx.moveTo(xLo,yPx-6);ctx.lineTo(xLo,yPx+6);ctx.stroke();"
                        + "ctx.beginPath();ctx.moveTo(xHi,yPx-6);ctx.lineTo(xHi,yPx+6);ctx.stroke();}"
                        + "ctx.restore()}}";
                } else if (ciStyle.equals("band")) {
                    levels2Suffix = ",{id:'cpInner',afterDatasetsDraw:function(chart){"
                        + "var ctx=chart.ctx,a=chart.chartArea;"
                        + "var lo2=" + lower2Arr + ",hi2=" + upper2Arr + ";"
                        + "var yS=chart.scales.y,xS=chart.scales.x;"
                        + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();"
                        + "for(var i=0;i<lo2.length;i++){"
                        + "var yPx=yS.getPixelForValue(i);"
                        + "var xLo=xS.getPixelForValue(lo2[i]),xHi=xS.getPixelForValue(hi2[i]);"
                        + "var bh=Math.max(8,(a.bottom-a.top)/lo2.length*0.55);"
                        + "ctx.fillStyle='" + cfIn2 + "';"
                        + "ctx.strokeStyle='" + csIn2b + "';ctx.lineWidth=0.8;"
                        + "ctx.beginPath();"
                        + "if(ctx.roundRect)ctx.roundRect(xLo,yPx-bh/2,xHi-xLo,bh,2);"
                        + "else ctx.rect(xLo,yPx-bh/2,xHi-xLo,bh);"
                        + "ctx.fill();ctx.stroke();}"
                        + "ctx.restore()}}";
                } else if (ciStyle.equals("area")) {
                    levels2Suffix = ",{id:'cpInner',afterDatasetsDraw:function(chart){"
                        + "var ctx=chart.ctx;"
                        + "var lo2=" + lower2Arr + ",hi2=" + upper2Arr + ";"
                        + "var yS=chart.scales.y,xS=chart.scales.x;"
                        + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.fillStyle='" + cfIn2 + "';"
                        + "ctx.beginPath();"
                        + "for(var i=0;i<hi2.length;i++){"
                        + "var xPx=xS.getPixelForValue(i),yHi=yS.getPixelForValue(hi2[i]);"
                        + "if(i===0)ctx.moveTo(xPx,yHi);else ctx.lineTo(xPx,yHi);}"
                        + "for(var i=lo2.length-1;i>=0;i--){"
                        + "var xPx=xS.getPixelForValue(i),yLo=yS.getPixelForValue(lo2[i]);"
                        + "ctx.lineTo(xPx,yLo);}"
                        + "ctx.closePath();ctx.fill();"
                        + "ctx.strokeStyle='" + csIn2a + "';ctx.lineWidth=1.2;"
                        + "ctx.beginPath();"
                        + "for(var i=0;i<hi2.length;i++){"
                        + "var xPx=xS.getPixelForValue(i),yHi=yS.getPixelForValue(hi2[i]);"
                        + "if(i===0)ctx.moveTo(xPx,yHi);else ctx.lineTo(xPx,yHi);}"
                        + "ctx.stroke();ctx.beginPath();"
                        + "for(var i=0;i<lo2.length;i++){"
                        + "var xPx=xS.getPixelForValue(i),yLo=yS.getPixelForValue(lo2[i]);"
                        + "if(i===0)ctx.moveTo(xPx,yLo);else ctx.lineTo(xPx,yLo);}"
                        + "ctx.stroke();ctx.restore()}}";
                }
            } else {
                // eventstudy / coefplot-vertical: y=value axis
                if (!ciStyle.equals("area")) {
                    levels2Suffix = ",{id:'cpInner',afterDatasetsDraw:function(chart){"
                        + "var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;"
                        + "var lo2=" + lower2Arr + ",hi2=" + upper2Arr + ";"
                        + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.strokeStyle='" + ciCol2 + "';ctx.lineWidth=" + innerWhiskW + ";"
                        + "for(var i=0;i<lo2.length;i++){"
                        + "var xPx=xS.getPixelForValue(i);"
                        + "var yLo=yS.getPixelForValue(lo2[i]),yHi=yS.getPixelForValue(hi2[i]);"
                        + "ctx.beginPath();ctx.moveTo(xPx,yLo);ctx.lineTo(xPx,yHi);ctx.stroke();"
                        + "ctx.beginPath();ctx.moveTo(xPx-6,yLo);ctx.lineTo(xPx+6,yLo);ctx.stroke();"
                        + "ctx.beginPath();ctx.moveTo(xPx-6,yHi);ctx.lineTo(xPx+6,yHi);ctx.stroke();}"
                        + "ctx.restore()}}";
                } else {
                    levels2Suffix = ",{id:'cpInner',afterDatasetsDraw:function(chart){"
                        + "var ctx=chart.ctx;"
                        + "var lo2=" + lower2Arr + ",hi2=" + upper2Arr + ";"
                        + "var xS=chart.scales.x,yS=chart.scales.y;"
                        + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.fillStyle='" + cfIn2 + "';"
                        + "ctx.beginPath();"
                        + "for(var i=0;i<hi2.length;i++){"
                        + "var xPx=xS.getPixelForValue(i),yHi=yS.getPixelForValue(hi2[i]);"
                        + "if(i===0)ctx.moveTo(xPx,yHi);else ctx.lineTo(xPx,yHi);}"
                        + "for(var i=lo2.length-1;i>=0;i--){"
                        + "var xPx=xS.getPixelForValue(i),yLo=yS.getPixelForValue(lo2[i]);"
                        + "ctx.lineTo(xPx,yLo);}"
                        + "ctx.closePath();ctx.fill();"
                        + "ctx.strokeStyle='" + csIn2a + "';ctx.lineWidth=1.2;"
                        + "ctx.beginPath();"
                        + "for(var i=0;i<hi2.length;i++){"
                        + "var xPx=xS.getPixelForValue(i),yHi=yS.getPixelForValue(hi2[i]);"
                        + "if(i===0)ctx.moveTo(xPx,yHi);else ctx.lineTo(xPx,yHi);}"
                        + "ctx.stroke();ctx.beginPath();"
                        + "for(var i=0;i<lo2.length;i++){"
                        + "var xPx=xS.getPixelForValue(i),yLo=yS.getPixelForValue(lo2[i]);"
                        + "if(i===0)ctx.moveTo(xPx,yLo);else ctx.lineTo(xPx,yLo);}"
                        + "ctx.stroke();ctx.restore()}}";
                }
            }
        }

        // CI legend plugin: shows which line = which level in top-right corner (v3.6.0-s7b)
        String ciLegendSuffix = "";
        if (hasLevels2) {
            String outerLabel = (o.stats.cilevel != null && !o.stats.cilevel.isEmpty()) ? o.stats.cilevel : "95";
            String innerLabel = innerLevel.isEmpty() ? "90" : innerLevel;
            String lblColor = gen.isDark() ? "rgba(220,220,220,0.85)" : "rgba(60,60,60,0.85)";
            ciLegendSuffix = ",{id:'cpCiLegend',afterDraw:function(chart){"
                + "var ctx=chart.ctx,a=chart.chartArea;"
                + "var x=a.right-10,y=a.top+14;"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.font='11px sans-serif';ctx.fillStyle='" + lblColor + "';ctx.textAlign='right';"
                + "ctx.strokeStyle='" + ciColor + "';"
                + "ctx.lineWidth=3.5;ctx.beginPath();ctx.moveTo(x-54,y-4);ctx.lineTo(x-38,y-4);ctx.stroke();"
                + "ctx.fillText('" + innerLabel + "% CI',x,y);"
                + "ctx.lineWidth=1.5;ctx.beginPath();ctx.moveTo(x-54,y+11);ctx.lineTo(x-38,y+11);ctx.stroke();"
                + "ctx.fillText('" + outerLabel + "% CI',x,y+15);"
                + "ctx.restore()}}";
        }
        // cpDot for levels2: draw point estimates on TOP of all CI layers (v3.6.0-s7b)
        // When hasLevels2=true, dot was suppressed in cpWhisker; re-add here as last plugin
        if (hasLevels2 && (ciStyle.equals("whisker") || ciStyle.equals("bar") || ciStyle.equals("band"))) {
            // t2j fix8 (deep-dive follow-up): when levels() is set the normal dot
            // plugin is suppressed and this one re-adds the dots; it previously
            // dropped the connecting line, so `connected` silently drew no line under
            // levels(). Draw the polyline here too (honoring linewidth()), so
            // connected behaves the same with and without nested CIs.
            String lvlConnLine = o.chart.peConnected
                ? "ctx.strokeStyle='" + mainColor + "';ctx.lineWidth=" + lineWidthOr("1.5") + ";ctx.setLineDash([]);ctx.beginPath();"
                  + "for(var i=0;i<coefs.length;i++){"
                  + (isHorizontal
                      ? "var lxPx=xS.getPixelForValue(coefs[i]),lyPx=yS.getPixelForValue(i);"
                      : "var lxPx=xS.getPixelForValue(i),lyPx=yS.getPixelForValue(coefs[i]);")
                  + "if(i===0)ctx.moveTo(lxPx,lyPx);else ctx.lineTo(lxPx,lyPx);}ctx.stroke();"
                : "";
            String cpDotForLevels = ",{id:'cpDot',afterDatasetsDraw:function(chart){"
                + "var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;"
                + "var coefs=" + coefArr + ";"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();" + lvlConnLine + "ctx.fillStyle='" + mainColor + "';"
                + "for(var i=0;i<coefs.length;i++){"
                + (isHorizontal
                    ? "var xPx=xS.getPixelForValue(coefs[i]);var yPx=yS.getPixelForValue(i);"
                    : "var xPx=xS.getPixelForValue(i);var yPx=yS.getPixelForValue(coefs[i]);")
                + "_spkSigDot(chart,xPx,yPx," + pSize + ",'" + mainColor + "',_cpSig[i],false);"
                + "}ctx.restore()}}";
            levels2Suffix += cpDotForLevels;
        }

        // Value-axis tick injection: always include refval (cpZero line) and pexline value (v3.6.0-s7a)
        // Uses afterBuildTicks at scale level so Chart.js labels these positions.
        String refTickFn = "";
        {
            // Collect values to force as labeled ticks
            java.util.List<String> forcedTicks = new java.util.ArrayList<>();
            // Always inject refval (drawn by cpZero) -- default "0"
            String rv = o.chart.peRefval.trim().replaceAll("[^0-9.\\-]", "");
            if (!rv.isEmpty()) forcedTicks.add(rv);
            // Also inject pexline value if set and different from refval
            if (!o.chart.pePexline.isEmpty()) {
                String pv = o.chart.pePexline.trim().replaceAll("[^0-9.\\-]", "");
                if (!pv.isEmpty() && !pv.equals(rv)) forcedTicks.add(pv);
            }
            if (!forcedTicks.isEmpty()) {
                StringBuilder vlist = new StringBuilder();
                for (int fi = 0; fi < forcedTicks.size(); fi++) {
                    if (fi > 0) vlist.append(",");
                    vlist.append(forcedTicks.get(fi));
                }
                refTickFn = niceTicksJs(cpNice, vlist.toString());   // s8r: anchored ticks + forced values
            }
            else refTickFn = niceTicksJs(cpNice, "");
        }
        // v3.6.0-s8b: value-axis title. User xtitle()/ytitle() were previously ignored
        // here; an estimate axis without a label fails VIZ_STANDARD AX-TITLE. The ado sets
        // xtitle/ytitle to "Odds ratio" etc. under eform.
        {   // s9j: register what this chart draws besides the point estimates
            String ciLbl = (o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel) + "% CI";
            boolean bandCi = ciStyle.equals("band") || ciStyle.equals("area");
            if (!o.chart.peNoci) key("ci", ciLbl, bandCi ? "swatch" : "whisker", bandCi ? colAtAlpha(mainColor, 0.22) : mainColor, "", "");
            if (!o.chart.peLevels2Val.isEmpty() && !o.chart.peNoci) key("ci_inner", o.chart.peLevels2Val + "% CI (inner)", bandCi ? "swatch" : "whisker_thick", bandCi ? colAtAlpha(mainColor, 0.42) : mainColor, "", "");
            if (showRefLine) key("refline", "Null (" + (refLineVal == Math.rint(refLineVal) ? String.valueOf((long) refLineVal) : String.format(Locale.ROOT, "%.4g", refLineVal)) + ")", "line", "#888888", "[5,5]", "");
            if (!o.chart.pePexline.isEmpty()) key("pexline", "Reference (" + o.chart.pePexline.trim() + ")", "line", "#999999", "[2,3]", "");
        }
        String valTitleUser = isHorizontal ? o.axes.xtitle : o.axes.ytitle;
        String valTitle = !valTitleUser.isEmpty() ? valTitleUser
                        : (o.type.equals("eventstudy") ? "Estimate" : "Coefficient");
        String catTitleUser = isHorizontal ? o.axes.ytitle : o.axes.xtitle;
        String valTitleJs = ",title:{display:true,text:'" + escJs(valTitle) + "',color:'" + labelCol + "'}";
        String catTitleJs = catTitleUser.isEmpty() ? "" : ",title:{display:true,text:'" + escJs(catTitleUser) + "',color:'" + labelCol + "'}";
        if (!isBarStyle && isHorizontal) {
            // Horizontal coefplot (fix8n): x=value axis (linear, with CI range); y=coefficient
            // positions as a LINEAR axis (not category) so the scatter points sit at y=i and the
            // plugins' getPixelForValue(i) address the same rows. reverse:true keeps the first
            // coefficient at the top (the category-axis convention this replaces). Integer ticks
            // 0..k-1 are labelled with the coefficient names via the tick callback, exactly like
            // the multi-model coefplot's category-as-linear axis.
            sb.append("    scales:{\n")
              .append("      x:{type:'linear',min:").append(String.format(Locale.ROOT, "%.4f", yMin))
              .append(",max:").append(String.format(Locale.ROOT, "%.4f", yMax))
              .append(",grid:{color:'").append(gridCol).append("'},ticks:{color:'").append(labelCol).append("',includeBounds:false}").append(refTickFn).append(valTitleJs).append("},\n")
              .append("      y:{type:'linear',reverse:true,min:-0.5,max:").append(String.format(Locale.ROOT, "%.1f", k - 0.5))
              .append(",offset:false,\n")
              .append("        afterBuildTicks:function(axis){axis.ticks=[];for(var i=0;i<").append(k).append(";i++)axis.ticks.push({value:i});},\n")
              .append("        grid:{color:'").append(gridCol).append("'},ticks:{color:'").append(labelCol).append("',autoSkip:false,callback:function(v){var l=").append(lblJs).append(";return l[v]||'';}}").append(catTitleJs).append("}\n")
              .append("    }\n");
        } else if (isBarStyle && isHorizontal) {
            // Bar horizontal: indexAxis=y, category on y, value on x -- with computed range
            sb.append("    scales:{\n")
              .append("      x:{min:").append(String.format(Locale.ROOT, "%.4f", yMin))
              .append(",max:").append(String.format(Locale.ROOT, "%.4f", yMax))
              .append(",grid:{color:'").append(gridCol).append("'},ticks:{color:'").append(labelCol).append("',includeBounds:false}").append(refTickFn).append(valTitleJs).append("},\n")
              .append("      y:{grid:{color:'").append(gridCol).append("'},ticks:{color:'").append(labelCol).append("'}").append(catTitleJs).append("}\n")
              .append("    }\n");
        } else {
            // Vertical: category on x (offset:true for padding), value on y
            String headingFont = hasHeadings
                ? ",font:function(ctx){if(Array.isArray(ctx.tick.label)&&ctx.tick.label.length>1)"
                  + "{return ctx.tick.label[0]?{size:11,style:'italic',weight:'bold'}:{size:12};}return{size:12};}"
                : "";
            sb.append("    scales:{\n")
              .append("      x:{offset:true,grid:{color:'").append(gridCol).append("',offset:true},ticks:{color:'").append(labelCol).append("'").append(headingFont).append("}},\n")
              .append("      y:{min:").append(String.format(Locale.ROOT, "%.4f", yMin))
              .append(",max:").append(String.format(Locale.ROOT, "%.4f", yMax))
              .append(",grid:{color:'").append(gridCol).append("'},ticks:{color:'").append(labelCol).append("',includeBounds:false}").append(refTickFn).append(valTitleJs).append("}\n")
              .append("    }\n");
        }

        sb.append("  },\n");

        // ---------------------------------------------------------------
        // CI + reference-line inline plugins (v3.6.0-s6)
        // All CI styles fully implemented for both orientations.
        // ---------------------------------------------------------------
        if (o.chart.peNoci) {
            sb.append("  plugins:[").append(zeroPlugin).append(levels2Suffix).append(CANVAS_CI_KEY ? ciLegendSuffix : "").append(pexSuffix1).append("]\n");

        } else if (ciStyle.equals("whisker") && !isBarStyle) {
            sb.append("  plugins:[{\n")
              .append("    id:'cpWhisker',\n")
              .append("    afterDatasetsDraw:function(chart){\n")
              .append("      var ctx=chart.ctx;\n")
              .append("      var lo=").append(lowerArr).append(",hi=").append(upperArr).append(";\n")
              .append("      var coefs=").append(coefArr).append(";\n");
            if (isHorizontal) {
                sb.append("      var yS=chart.scales.y,xS=chart.scales.x;\n")
                  .append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.strokeStyle='").append(ciColor).append("';ctx.lineWidth=").append(ciWidthStr).append(";\n")
                  .append("      for(var i=0;i<lo.length;i++){\n")
                  .append("        var yPx=yS.getPixelForValue(i);\n")
                  .append("        var xLo=xS.getPixelForValue(lo[i]),xHi=xS.getPixelForValue(hi[i]);\n")
                  .append("        ctx.beginPath();ctx.moveTo(xLo,yPx);ctx.lineTo(xHi,yPx);ctx.stroke();\n")
                  .append("        var cap=4;\n")
                  .append("        ctx.beginPath();ctx.moveTo(xLo,yPx-cap);ctx.lineTo(xLo,yPx+cap);ctx.stroke();\n")
                  .append("        ctx.beginPath();ctx.moveTo(xHi,yPx-cap);ctx.lineTo(xHi,yPx+cap);ctx.stroke();\n")
                  .append("      }\n")
                  // Draw connecting line through dots when peConnected=true
                  .append(o.chart.peConnected
                      ? "      ctx.strokeStyle='" + mainColor + "';ctx.lineWidth=" + lineWidthOr("1.5") + ";ctx.setLineDash([]);ctx.beginPath();\n"
                        + "      for(var i=0;i<coefs.length;i++){\n"
                        + "        var xPx=xS.getPixelForValue(coefs[i]);var yPx=yS.getPixelForValue(i);\n"
                        + "        if(i===0)ctx.moveTo(xPx,yPx);else ctx.lineTo(xPx,yPx);\n"
                        + "      }ctx.stroke();\n"
                      : "")
                  // Draw point dots -- suppressed here when hasLevels2 (dots drawn by cpDot after cpInner)
                  .append(hasLevels2 ? "" :
                      "      ctx.fillStyle='" + mainColor + "';\n"
                    + "      for(var i=0;i<coefs.length;i++){\n"
                    + "        var xPx=xS.getPixelForValue(coefs[i]);\n"
                    + "        var yPx=yS.getPixelForValue(i);\n"
                    + "        _spkSigDot(chart,xPx,yPx," + pSize + ",'" + mainColor + "',_cpSig[i],false);\n"
                    + "      }\n")
                  .append("      ctx.restore();\n");
            } else {
                sb.append("      var xS=chart.scales.x,yS=chart.scales.y;\n")
                  .append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.strokeStyle='").append(ciColor).append("';ctx.lineWidth=").append(ciWidthStr).append(";\n")
                  .append("      for(var i=0;i<lo.length;i++){\n")
                  .append("        var xPx=xS.getPixelForValue(i);\n")
                  .append("        var yLo=yS.getPixelForValue(lo[i]),yHi=yS.getPixelForValue(hi[i]);\n")
                  .append("        ctx.beginPath();ctx.moveTo(xPx,yLo);ctx.lineTo(xPx,yHi);ctx.stroke();\n")
                  .append("        var cap=4;\n")
                  .append("        ctx.beginPath();ctx.moveTo(xPx-cap,yLo);ctx.lineTo(xPx+cap,yLo);ctx.stroke();\n")
                  .append("        ctx.beginPath();ctx.moveTo(xPx-cap,yHi);ctx.lineTo(xPx+cap,yHi);ctx.stroke();\n")
                  .append("      }\n")
                  .append("      ctx.restore();\n");
            }
            sb.append("    }\n")
              .append("  },").append(zeroPlugin).append(levels2Suffix).append(CANVAS_CI_KEY ? ciLegendSuffix : "").append(pexSuffix1).append("]\n");

        } else if (ciStyle.equals("band") && !isBarStyle) {
            // band: per-coefficient CI rectangle for both coefplot and eventstudy.
            // Each period/variable gets one isolated rectangle spanning lo[i]..hi[i].
            // This matches the Callaway-SantAnna / csdid style event study plot.
            // Band is beforeDatasetsDraw; point dots are afterDatasetsDraw (on top).
            sb.append("  plugins:[{\n")
              .append("    id:'cpBand',\n")
              .append("    beforeDatasetsDraw:function(chart){\n")
              .append("      var ctx=chart.ctx,a=chart.chartArea;\n")
              .append("      var lo=").append(lowerArr).append(",hi=").append(upperArr).append(";\n");
            if (isHorizontal) {
                // Horizontal: per-coefficient rectangle (CI as a bar spanning lo to hi)
                sb.append("      var yS=chart.scales.y,xS=chart.scales.x;\n")
                  .append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n")
                  .append("      for(var i=0;i<lo.length;i++){\n")
                  .append("        var yPx=yS.getPixelForValue(i);\n")
                  .append("        var xLo=xS.getPixelForValue(lo[i]),xHi=xS.getPixelForValue(hi[i]);\n")
                  .append("        var bh=Math.max(8,(a.bottom-a.top)/lo.length*0.55);\n")
                  .append("        ctx.fillStyle='").append(bandFillColor).append("';\n")
                  .append("        ctx.strokeStyle='").append(bandBorderColor).append("';ctx.lineWidth=").append(bandBorderWd).append(";\n")
                  .append("        ctx.beginPath();\n")
                  .append("        if(ctx.roundRect)ctx.roundRect(xLo,yPx-bh/2,xHi-xLo,bh,3);\n")
                  .append("        else ctx.rect(xLo,yPx-bh/2,xHi-xLo,bh);\n")
                  .append("        ctx.fill();ctx.stroke();\n")
                  .append("      }\n")
                  .append("      ctx.restore();\n");
            } else {
                // Vertical coefplot: per-coefficient rectangle (same logic as horizontal, axes swapped)
                // Each CI is an independent vertical bar -- variables have no sequential relationship.
                sb.append("      var xS=chart.scales.x,yS=chart.scales.y;\n")
                  .append("      var bw=Math.max(8,(a.right-a.left)/lo.length*0.55);\n")
                  .append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n")
                  .append("      for(var i=0;i<lo.length;i++){\n")
                  .append("        var xPx=xS.getPixelForValue(i);\n")
                  .append("        var yLo=yS.getPixelForValue(lo[i]),yHi=yS.getPixelForValue(hi[i]);\n")
                  .append("        ctx.fillStyle='").append(bandFillColor).append("';\n")
                  .append("        ctx.strokeStyle='").append(bandBorderColor).append("';ctx.lineWidth=").append(bandBorderWd).append(";\n")
                  .append("        ctx.beginPath();\n")
                  .append("        if(ctx.roundRect)ctx.roundRect(xPx-bw/2,yHi,bw,yLo-yHi,3);\n")
                  .append("        else ctx.rect(xPx-bw/2,yHi,bw,yLo-yHi);\n")
                  .append("        ctx.fill();ctx.stroke();\n")
                  .append("      }\n")
                  .append("      ctx.restore();\n");
            }
            sb.append("    }\n");
            // Dot plugin: draws point estimates ON TOP of the band (afterDatasetsDraw)
            // Also draws connecting line when peConnected=true
            String dotConnLine = o.chart.peConnected
                ? (isHorizontal
                    ? "ctx.strokeStyle='" + mainColor + "';ctx.lineWidth=" + lineWidthOr("1.5") + ";ctx.setLineDash([]);ctx.beginPath();"
                      + "for(var i=0;i<coefs.length;i++){var xPx=xS.getPixelForValue(coefs[i]);var yPx=yS.getPixelForValue(i);"
                      + "if(i===0)ctx.moveTo(xPx,yPx);else ctx.lineTo(xPx,yPx);}ctx.stroke();"
                    : "ctx.strokeStyle='" + mainColor + "';ctx.lineWidth=" + lineWidthOr("1.5") + ";ctx.setLineDash([]);ctx.beginPath();"
                      + "for(var i=0;i<coefs.length;i++){var xPx=xS.getPixelForValue(i);var yPx=yS.getPixelForValue(coefs[i]);"
                      + "if(i===0)ctx.moveTo(xPx,yPx);else ctx.lineTo(xPx,yPx);}ctx.stroke();")
                : "";
            String dotPlugin = "{id:'cpDot',afterDatasetsDraw:function(chart){"
                + "var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;"
                + "var coefs=" + coefArr + ";"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();" + dotConnLine
                + "ctx.fillStyle='" + mainColor + "';"
                + "for(var i=0;i<coefs.length;i++){"
                + "var xPx=" + (isHorizontal ? "xS.getPixelForValue(coefs[i]);var yPx=yS.getPixelForValue(i)" : "xS.getPixelForValue(i);var yPx=yS.getPixelForValue(coefs[i])") + ";"
                + "_spkSigDot(chart,xPx,yPx," + pSize + ",'" + mainColor + "',_cpSig[i],false);"
                + "}ctx.restore();}}";
            sb.append("  },").append(hasLevels2 ? "" : dotPlugin + ",").append(zeroPlugin).append(levels2Suffix).append(CANVAS_CI_KEY ? ciLegendSuffix : "").append(pexSuffix1).append("]\n");

        } else if (ciStyle.equals("area") && !isBarStyle) {
            // CI area: filled polygon between upper and lower CI bounds.
            // Semi-transparent fill + thin border. Drawn beforeDatasetsDraw (behind points).
            // Horizontal: polygon across y-axis (coef names on y, values on x).
            // Vertical:   polygon across x-axis (time/coefs on x, values on y).
            // This is the standard shaded confidence ribbon used in event study plots.
            sb.append("  plugins:[{\n")
              .append("    id:'cpArea',\n")
              .append("    beforeDatasetsDraw:function(chart){\n")
              .append("      var ctx=chart.ctx;\n")
              .append("      var lo=").append(lowerArr).append(",hi=").append(upperArr).append(";\n")
              .append("      var n=lo.length;\n")
              .append("      if(n<1)return;\n");
            if (isHorizontal) {
                // Horizontal: polygon on y-axis. Upper edge top->bottom, lower edge bottom->top.
                sb.append("      var yS=chart.scales.y,xS=chart.scales.x;\n")
                  .append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n")
                  .append("      ctx.fillStyle='").append(bandFillColor).append("';\n")
                  .append("      ctx.strokeStyle='").append(bandBorderColor).append("';ctx.lineWidth=").append(bandBorderWd).append(";\n")
                  .append("      ctx.beginPath();\n")
                  // upper edge: go through hi values top to bottom (i=0..n-1)
                  .append("      ctx.moveTo(xS.getPixelForValue(hi[0]),yS.getPixelForValue(0));\n")
                  .append("      for(var i=1;i<n;i++) ctx.lineTo(xS.getPixelForValue(hi[i]),yS.getPixelForValue(i));\n")
                  // lower edge: come back bottom to top (i=n-1..0)
                  .append("      for(var i=n-1;i>=0;i--) ctx.lineTo(xS.getPixelForValue(lo[i]),yS.getPixelForValue(i));\n")
                  .append("      ctx.closePath();ctx.fill();\n")
                  // stroke upper edge
                  .append("      ctx.beginPath();\n")
                  .append("      ctx.moveTo(xS.getPixelForValue(hi[0]),yS.getPixelForValue(0));\n")
                  .append("      for(var i=1;i<n;i++) ctx.lineTo(xS.getPixelForValue(hi[i]),yS.getPixelForValue(i));\n")
                  .append("      ctx.stroke();\n")
                  // stroke lower edge
                  .append("      ctx.beginPath();\n")
                  .append("      ctx.moveTo(xS.getPixelForValue(lo[0]),yS.getPixelForValue(0));\n")
                  .append("      for(var i=1;i<n;i++) ctx.lineTo(xS.getPixelForValue(lo[i]),yS.getPixelForValue(i));\n")
                  .append("      ctx.stroke();\n")
                  .append("      ctx.restore();\n");
            } else {
                // Vertical: polygon on x-axis. Upper edge left->right, lower edge right->left.
                sb.append("      var xS=chart.scales.x,yS=chart.scales.y;\n")
                  .append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n")
                  .append("      ctx.fillStyle='").append(bandFillColor).append("';\n")
                  .append("      ctx.strokeStyle='").append(bandBorderColor).append("';ctx.lineWidth=").append(bandBorderWd).append(";\n")
                  .append("      ctx.beginPath();\n")
                  .append("      ctx.moveTo(xS.getPixelForValue(0),yS.getPixelForValue(hi[0]));\n")
                  .append("      for(var i=1;i<n;i++) ctx.lineTo(xS.getPixelForValue(i),yS.getPixelForValue(hi[i]));\n")
                  .append("      for(var i=n-1;i>=0;i--) ctx.lineTo(xS.getPixelForValue(i),yS.getPixelForValue(lo[i]));\n")
                  .append("      ctx.closePath();ctx.fill();\n")
                  .append("      ctx.beginPath();\n")
                  .append("      ctx.moveTo(xS.getPixelForValue(0),yS.getPixelForValue(hi[0]));\n")
                  .append("      for(var i=1;i<n;i++) ctx.lineTo(xS.getPixelForValue(i),yS.getPixelForValue(hi[i]));\n")
                  .append("      ctx.stroke();\n")
                  .append("      ctx.beginPath();\n")
                  .append("      ctx.moveTo(xS.getPixelForValue(0),yS.getPixelForValue(lo[0]));\n")
                  .append("      for(var i=1;i<n;i++) ctx.lineTo(xS.getPixelForValue(i),yS.getPixelForValue(lo[i]));\n")
                  .append("      ctx.stroke();\n")
                  .append("      ctx.restore();\n");
            }
            sb.append("    }\n");
            // Dot plugin for horizontal coefplot: cpArea is beforeDatasetsDraw
            // so bars (transparent) still show nothing -- need explicit dot drawing
            if (isHorizontal) {
                String dotConnLine2 = o.chart.peConnected
                    ? "ctx.strokeStyle='" + mainColor + "';ctx.lineWidth=" + lineWidthOr("1.5") + ";ctx.setLineDash([]);ctx.beginPath();"
                      + "for(var i=0;i<coefs.length;i++){var xPx=xS.getPixelForValue(coefs[i]);var yPx=yS.getPixelForValue(i);"
                      + "if(i===0)ctx.moveTo(xPx,yPx);else ctx.lineTo(xPx,yPx);}ctx.stroke();"
                    : "";
                String dotPlugin2 = "{id:'cpDot2',afterDatasetsDraw:function(chart){"
                    + "var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;"
                    + "var coefs=" + coefArr + ";"
                    + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();" + dotConnLine2
                    + "ctx.fillStyle='" + mainColor + "';"
                    + "for(var i=0;i<coefs.length;i++){"
                    + "var xPx=xS.getPixelForValue(coefs[i]);"
                    + "var yPx=yS.getPixelForValue(i);"
                    + "_spkSigDot(chart,xPx,yPx," + pSize + ",'" + mainColor + "',_cpSig[i],false);"
                    + "}ctx.restore();}}";
                sb.append("  },").append(dotPlugin2).append(",").append(zeroPlugin).append(levels2Suffix).append(CANVAS_CI_KEY ? ciLegendSuffix : "").append(pexSuffix1).append("]\n");
            } else {
                sb.append("  },").append(zeroPlugin).append(levels2Suffix).append(CANVAS_CI_KEY ? ciLegendSuffix : "").append(pexSuffix1).append("]\n");
            }

        } else if (ciStyle.equals("bar")) {
            sb.append("  plugins:[{\n")
              .append("    id:'cpRangeBar',\n")
              .append("    beforeDatasetsDraw:function(chart){\n")
              .append("      var ctx=chart.ctx,area=chart.chartArea;\n")
              .append("      var lo=").append(lowerArr).append(",hi=").append(upperArr).append(";\n");
            if (isHorizontal && !isBarStyle) {
                sb.append("      var yS=chart.scales.y,xS=chart.scales.x;\n")
                  .append("      var bh=Math.max(6,(area.bottom-area.top)/lo.length*0.5);\n")
                  .append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n")
                  .append("      for(var i=0;i<lo.length;i++){\n")
                  .append("        var yPx=yS.getPixelForValue(i);\n")
                  .append("        var xLo=xS.getPixelForValue(lo[i]),xHi=xS.getPixelForValue(hi[i]);\n")
                  .append("        ctx.fillStyle='").append(mainColorAlpha).append("';\n")
                  .append("        ctx.strokeStyle='").append(mainColorBorder).append("';ctx.lineWidth=0.5;\n")
                  .append("        ctx.beginPath();\n")
                  .append("        if(ctx.roundRect)ctx.roundRect(xLo,yPx-bh/2,xHi-xLo,bh,2);\n")
                  .append("        else ctx.rect(xLo,yPx-bh/2,xHi-xLo,bh);\n")
                  .append("        ctx.fill();ctx.stroke();\n")
                  .append("      }\n")
                  .append("      ctx.restore();\n");
            } else {
                sb.append("      var xS=chart.scales.x,yS=chart.scales.y;\n")
                  .append("      var bw=Math.max(6,(area.right-area.left)/lo.length*0.5);\n")
                  .append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n")
                  .append("      for(var i=0;i<lo.length;i++){\n")
                  .append("        var xPx=xS.getPixelForValue(i);\n")
                  .append("        var yLo=yS.getPixelForValue(lo[i]),yHi=yS.getPixelForValue(hi[i]);\n")
                  .append("        ctx.fillStyle='").append(mainColorAlpha).append("';\n")
                  .append("        ctx.strokeStyle='").append(mainColorBorder).append("';ctx.lineWidth=0.5;\n")
                  .append("        ctx.beginPath();\n")
                  .append("        if(ctx.roundRect)ctx.roundRect(xPx-bw/2,yHi,bw,yLo-yHi,2);\n")
                  .append("        else ctx.rect(xPx-bw/2,yHi,bw,yLo-yHi);\n")
                  .append("        ctx.fill();ctx.stroke();\n")
                  .append("      }\n")
                  .append("      ctx.restore();\n");
            }
            sb.append("    }\n");
            // cpDot: draw point estimate circles on top of bar CI (v3.6.0-s7a fix)
            String barDotPos = isHorizontal
                ? "var xPx=xS.getPixelForValue(coefs[i]);var yPx=yS.getPixelForValue(i);"
                : "var xPx=xS.getPixelForValue(i);var yPx=yS.getPixelForValue(coefs[i]);";
            String barDotPlugin = "{id:'cpDot',afterDatasetsDraw:function(chart){"
                + "var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;"
                + "var coefs=" + coefArr + ";"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.fillStyle='" + mainColor + "';"
                + "ctx.strokeStyle='rgba(255,255,255,0.85)';ctx.lineWidth=1.5;"
                + "for(var i=0;i<coefs.length;i++){" + barDotPos
                + "_spkSigDot(chart,xPx,yPx,4,'" + mainColor + "',_cpSig[i],true);}"
                + "ctx.restore();}}";
            sb.append("  },").append(barDotPlugin).append(",").append(zeroPlugin).append(levels2Suffix).append(CANVAS_CI_KEY ? ciLegendSuffix : "").append(pexSuffix1).append("]\n");

        } else {
            // s8x: coefstyle(bar) with cistyle(band|area) previously drew NO CI (the only
            // non-A page in the suite). Now: per-coefficient CI rectangle over the bar,
            // same encoding as marginsplot's unconnected area (s8k).
            boolean barRectCI = isBarStyle && (ciStyle.equals("band") || ciStyle.equals("area"));
            if (isBarStyle && (ciStyle.equals("whisker") || barRectCI)) {
                String rectFill = colAtAlpha(mainColor, 0.28);
                sb.append("  plugins:[{\n")
                  .append("    id:'").append(barRectCI ? "cpBarArea" : "cpBarWhisker").append("',\n")
                  .append("    afterDatasetsDraw:function(chart){\n")
                  .append("      var ctx=chart.ctx;\n")
                  .append("      var lo=").append(lowerArr).append(",hi=").append(upperArr).append(";\n");
                if (barRectCI && isHorizontal) {
                    sb.append("      var yS=chart.scales.y,xS=chart.scales.x;\n")
                      .append("      var hw=(lo.length>1?Math.abs(yS.getPixelForValue(1)-yS.getPixelForValue(0)):40)*0.22;\n")
                      .append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.fillStyle='").append(rectFill).append("';\n")
                      .append("      for(var i=0;i<lo.length;i++){if(lo[i]===null||hi[i]===null)continue;\n")
                      .append("        var yPx=yS.getPixelForValue(i);var xLo=xS.getPixelForValue(lo[i]),xHi=xS.getPixelForValue(hi[i]);\n")
                      .append("        ctx.fillRect(Math.min(xLo,xHi),yPx-hw,Math.abs(xHi-xLo),2*hw);}\n")
                      .append("      ctx.restore();\n");
                } else if (barRectCI) {
                    sb.append("      var xS=chart.scales.x,yS=chart.scales.y;\n")
                      .append("      var hw=(lo.length>1?Math.abs(xS.getPixelForValue(1)-xS.getPixelForValue(0)):40)*0.22;\n")
                      .append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.fillStyle='").append(rectFill).append("';\n")
                      .append("      for(var i=0;i<lo.length;i++){if(lo[i]===null||hi[i]===null)continue;\n")
                      .append("        var xPx=xS.getPixelForValue(i);var yLo=yS.getPixelForValue(lo[i]),yHi=yS.getPixelForValue(hi[i]);\n")
                      .append("        ctx.fillRect(xPx-hw,Math.min(yLo,yHi),2*hw,Math.abs(yLo-yHi));}\n")
                      .append("      ctx.restore();\n");
                } else if (isHorizontal) {
                    sb.append("      var yS=chart.scales.y,xS=chart.scales.x;\n")
                      .append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.strokeStyle='").append(ciColor).append("';ctx.lineWidth=").append(ciWidthStr).append(";\n")
                      .append("      for(var i=0;i<lo.length;i++){\n")
                      .append("        var yPx=yS.getPixelForValue(i);\n")
                      .append("        var xLo=xS.getPixelForValue(lo[i]),xHi=xS.getPixelForValue(hi[i]);\n")
                      .append("        ctx.beginPath();ctx.moveTo(xLo,yPx);ctx.lineTo(xHi,yPx);ctx.stroke();\n")
                      .append("        var cap=4;\n")
                      .append("        ctx.beginPath();ctx.moveTo(xLo,yPx-cap);ctx.lineTo(xLo,yPx+cap);ctx.stroke();\n")
                      .append("        ctx.beginPath();ctx.moveTo(xHi,yPx-cap);ctx.lineTo(xHi,yPx+cap);ctx.stroke();\n")
                      .append("      }\n")
                      .append("      ctx.restore();\n");
                } else {
                    sb.append("      var xS=chart.scales.x,yS=chart.scales.y;\n")
                      .append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.strokeStyle='").append(ciColor).append("';ctx.lineWidth=").append(ciWidthStr).append(";\n")
                      .append("      for(var i=0;i<lo.length;i++){\n")
                      .append("        var xPx=xS.getPixelForValue(i);\n")
                      .append("        var yLo=yS.getPixelForValue(lo[i]),yHi=yS.getPixelForValue(hi[i]);\n")
                      .append("        ctx.beginPath();ctx.moveTo(xPx,yLo);ctx.lineTo(xPx,yHi);ctx.stroke();\n")
                      .append("        var cap=4;\n")
                      .append("        ctx.beginPath();ctx.moveTo(xPx-cap,yLo);ctx.lineTo(xPx+cap,yLo);ctx.stroke();\n")
                      .append("        ctx.beginPath();ctx.moveTo(xPx-cap,yHi);ctx.lineTo(xPx+cap,yHi);ctx.stroke();\n")
                      .append("      }\n")
                      .append("      ctx.restore();\n");
                }
                sb.append("    }\n")
                  .append("  },").append(zeroPlugin).append(levels2Suffix).append(CANVAS_CI_KEY ? ciLegendSuffix : "").append(pexSuffix1).append("]\n");
            } else {
                // For horizontal coefplot: add point-drawing plugin
                // (bars are transparent/zero-thickness, points must be drawn manually)
                if (isHorizontal) {
                    // Horizontal bar coefplot: draw dots (and optional connecting line) via plugin
                    String connLine = o.chart.peConnected
                        ? "ctx.strokeStyle='" + mainColor + "';ctx.lineWidth=" + lineWidthOr("1.5") + ";"
                          + "ctx.setLineDash([]);ctx.beginPath();"
                          + "for(var i=0;i<coefs.length;i++){"
                          + "var xPx=xS.getPixelForValue(coefs[i]);var yPx=yS.getPixelForValue(i);"
                          + "if(i===0)ctx.moveTo(xPx,yPx);else ctx.lineTo(xPx,yPx);}"
                          + "ctx.stroke();"
                        : "";
                    String pointPlugin = "{id:'cpDot',afterDatasetsDraw:function(chart){"
                        + "var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;"
                        + "var coefs=" + coefArr + ";"
                        + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();" + connLine
                        + "ctx.fillStyle='" + mainColor + "';"
                        + "for(var i=0;i<coefs.length;i++){"
                        + "var xPx=xS.getPixelForValue(coefs[i]);"
                        + "var yPx=yS.getPixelForValue(i);"
                        + "_spkSigDot(chart,xPx,yPx," + pSize + ",'" + mainColor + "',_cpSig[i],false);"
                        + "}ctx.restore();}}";
                    sb.append("  plugins:[").append(pointPlugin).append(",").append(zeroPlugin).append(levels2Suffix).append(CANVAS_CI_KEY ? ciLegendSuffix : "").append(pexSuffix1).append("]\n");
                } else {
                    sb.append("  plugins:[").append(zeroPlugin).append(levels2Suffix).append(CANVAS_CI_KEY ? ciLegendSuffix : "").append(pexSuffix1).append("]\n");
                }
            }
        }

        sb.append("});\n");
        // t2j fix8 (deep-dive r3): coefplot's dots/whiskers/bands are drawn by canvas
        // plugins at their final positions, so Chart.js's dataset animation (which grows
        // the transparent bar/line) is invisible and the chart appeared to "pop" in with
        // no entrance. Add a lightweight canvas fade-in via the Web Animations API, gated
        // on the animation duration (0 = animation off, e.g. noanimation), so coefplot
        // gets a visible entrance. Purely visual; does not touch data, tooltips, or layout.
        sb.append("(function(){var _cpDur=").append(animDuration())
          .append(";var _cpEl=document.getElementById('").append(id).append("');")
          .append("if(_cpDur>0&&_cpEl&&_cpEl.animate){try{_cpEl.animate([{opacity:0},{opacity:1}],{duration:_cpDur,easing:'ease-out'});}catch(_e){}}})();\n");
        return sb.toString();
    }

    // =========================================================================
    // coefPlotMulti -- Multi-model coefficient plot (v3.6.0 Session 4)
    // Renders multiple models side-by-side with jittered x-positions.
    // Data comes as tilde-separated model groups in pe* fields.
    // Each model gets its own color, point style, and whisker set.
    // =========================================================================
    String coefPlotMulti(String id) {
        // Split by tilde to get per-model data
        String[] modelNames  = o.chart.peNames.split("~", -1);
        String[] modelCoefs  = o.chart.peCoefs.split("~", -1);
        String[] modelLower  = o.chart.peLower.split("~", -1);
        String[] modelUpper  = o.chart.peUpper.split("~", -1);
        String[] modelSes    = o.chart.peSes.split("~", -1);
        String[] modelPvals  = o.chart.pePvals.split("~", -1);
        int nModels = modelNames.length;

        // Model display labels: estlabels() overrides estnames, else "Model N"
        // estlabels() tilde-sep, display-only. estnames() still used for estimates restore.
        String[] estLabels;
        if (!o.chart.peEstlabels.isEmpty()) {
            estLabels = o.chart.peEstlabels.split("~", -1);
        } else if (!o.chart.peEstNames.isEmpty()) {
            estLabels = o.chart.peEstNames.split("~", -1);
        } else {
            estLabels = new String[nModels];
        }
        // v3.6.0-s8c: size to nModels first -- assigning estLabels[m] into a shorter
        // user-supplied array threw ArrayIndexOutOfBounds (e.g. estlabels(A~B) for 3 models)
        String[] estLabelsSized = new String[nModels];
        for (int m = 0; m < nModels; m++) {
            estLabelsSized[m] = (m < estLabels.length && estLabels[m] != null && !estLabels[m].trim().isEmpty())
                ? estLabels[m].trim() : "Model " + (m + 1);
        }
        estLabels = estLabelsSized;

        // Build union of all coefficient names across models (preserving order)
        java.util.LinkedHashSet<String> allNamesSet = new java.util.LinkedHashSet<>();
        String[][] perModelNames = new String[nModels][];
        String[][] perModelCoefs = new String[nModels][];
        String[][] perModelLower = new String[nModels][];
        String[][] perModelUpper = new String[nModels][];
        String[][] perModelSes   = new String[nModels][];
        String[][] perModelPvals = new String[nModels][];
        for (int m = 0; m < nModels; m++) {
            perModelNames[m] = modelNames[m].split("\\|", -1);
            perModelCoefs[m] = modelCoefs[m].split("\\|", -1);
            perModelLower[m] = modelLower[m].split("\\|", -1);
            perModelUpper[m] = modelUpper[m].split("\\|", -1);
            perModelSes[m]   = modelSes[m].split("\\|", -1);
            perModelPvals[m] = modelPvals[m].split("\\|", -1);
            for (String n : perModelNames[m]) allNamesSet.add(n.trim());
        }
        String[] allNames = allNamesSet.toArray(new String[0]);
        int k = allNames.length;

        // Build name-to-index map for each model
        // For each model and each unified name, find the coefficient (or null if absent)
        boolean isHorizontal = o.chart.peOrient.equals("h");
        String ciStyle = o.chart.peCiStyle;

        // Jitter offset: spread models symmetrically around each category position
        // offset = [-0.15, 0, +0.15] for 3 models, etc.
        double jitterSpread = 0.25; // total spread per category (0 = no jitter)
        double[] offsets = new double[nModels];
        if (nModels == 1) {
            offsets[0] = 0;
        } else {
            for (int m = 0; m < nModels; m++) {
                offsets[m] = -jitterSpread / 2.0 + (jitterSpread * m / (nModels - 1));
            }
        }

        // Compute y-axis range across ALL models
        double yMin = Double.MAX_VALUE, yMax = -Double.MAX_VALUE;
        for (int m = 0; m < nModels; m++) {
            for (int i = 0; i < perModelLower[m].length; i++) {
                try {
                    // s8w: with noci the axis must fit the point estimates, not the hidden CIs
                    double lo = Double.parseDouble((o.chart.peNoci ? perModelCoefs[m][i] : perModelLower[m][i]).trim());
                    double hi = Double.parseDouble((o.chart.peNoci ? perModelCoefs[m][i] : perModelUpper[m][i]).trim());
                    if (lo < yMin) yMin = lo;
                    if (hi > yMax) yMax = hi;
                } catch (NumberFormatException e) { /* skip */ }
            }
        }
        double yRange = yMax - yMin;
        boolean showRefLine = !o.chart.peRefval.equals("none");
        double refLineVal = 0.0;
        if (showRefLine) {
            try { refLineVal = Double.parseDouble(o.chart.peRefval.trim()); }
            catch (NumberFormatException e) { showRefLine = false; }
        }
        // s8r: reference line inside the data range, nice ticks anchored to it, cushion outside
        if (showRefLine) { if (yMin > refLineVal) yMin = refLineVal; if (yMax < refLineVal) yMax = refLineVal; }
        double[] cpNice = niceRange(yMin, yMax, 6);
        yRange = cpNice[1] - cpNice[0];
        double[] pmfM = plotMarginFrac();
        double yPad = yRange * Math.max(pmfM[2], pmfM[3]);  // plotmargin() (default 10%)
        yMin = cpNice[0] - yPad;
        yMax = cpNice[1] + yPad;

        // ciwidth() and cicolors() for multi-model
        double ciWidthVal = o.chart.peCiwidth.isEmpty() ? 1.5 : parseDouble(o.chart.peCiwidth, 1.5);
        String ciWidthStr = String.format(Locale.ROOT, "%.1f", ciWidthVal);
        String[] ciColorArr = o.chart.peCicolors.isEmpty()
            ? new String[0] : o.chart.peCicolors.split("\\|", -1);

        // Aspect ratio
        double cjAspect = 1.45;
        if (k >= 6) cjAspect = 1.55;
        if (k <= 2) cjAspect = 1.30;

        String pSize = o.chart.pointsize.isEmpty() ? "6" : o.chart.pointsize;
        String labelCol = labelColor();
        String gridCol  = gridCssColor();
        String zeroLineColor = labelColor();

        // Build tooltip data per model
        StringBuilder sb = new StringBuilder();
        // t2j fix8w (P4): per-model significance flags in CATEGORY order (null coef -> 0)
        String[][] sigM = new String[nModels][];
        boolean anyHollowM = false;
        for (int m = 0; m < nModels; m++) {
            java.util.HashMap<String, Integer> ni = new java.util.HashMap<>();
            for (int i = 0; i < perModelNames[m].length; i++) ni.put(perModelNames[m][i].trim(), i);
            sigM[m] = new String[k];
            for (int j = 0; j < k; j++) {
                Integer ix = ni.get(allNames[j]);
                sigM[m][j] = ix != null && sigFlag(perModelPvals[m], ix) == 1 ? "1" : "0";
                if (ix != null && sigM[m][j].equals("0")) anyHollowM = true;
            }
        }
        sb.append(sigDotJs()).append("var _cpSigM=[");
        for (int m = 0; m < nModels; m++) sb.append(m > 0 ? "," : "").append("[").append(String.join(",", sigM[m])).append("]");
        sb.append("];\n");
        if (anyHollowM) key("sig", SIG_KEY_LABEL, "outlier", col(0), "", "");
        sb.append("var _cpTipM=[");
        for (int m = 0; m < nModels; m++) {
            if (m > 0) sb.append(",");
            sb.append("[");
            // Build map from name to index for this model
            java.util.HashMap<String, Integer> nameIdx = new java.util.HashMap<>();
            for (int i = 0; i < perModelNames[m].length; i++) {
                nameIdx.put(perModelNames[m][i].trim(), i);
            }
            for (int j = 0; j < k; j++) {
                if (j > 0) sb.append(",");
                Integer idx = nameIdx.get(allNames[j]);
                if (idx != null) {
                    sb.append("{n:'").append(escJs(gen.resolveCoefLabel(allNames[j]))).append("'")
                      .append(",m:'").append(escJs(estLabels[m])).append("'")
                      .append(",b:").append(numOrNull(perModelCoefs[m], idx))
                      .append(",se:").append(numOrNull(perModelSes[m], idx))
                      .append(",p:").append(numOrNull(perModelPvals[m], idx))
                      .append(",lo:").append(numOrNull(perModelLower[m], idx))
                      .append(",hi:").append(numOrNull(perModelUpper[m], idx))
                      .append("}");
                } else {
                    sb.append("null");
                }
            }
            sb.append("]");
        }
        sb.append("];\n");

        // Build category labels -- apply display label resolution at Java time (v3.6.0-s6c23).
        // gen.resolveCoefLabel() applies three-tier priority: custom > variable label > raw name.
        // Labels are emitted as plain string literals so they are available immediately
        // when new Chart() runs -- before _spkCoefLabel() is defined later in the page.
        StringBuilder lblJs = new StringBuilder("[");
        for (int j = 0; j < k; j++) {
            if (j > 0) lblJs.append(",");
            lblJs.append("'").append(escJs(gen.resolveCoefLabel(allNames[j]))).append("'");
        }
        lblJs.append("]");

        // Build datasets: one scatter dataset per model using numeric x with jitter
        sb.append("new Chart(document.getElementById('").append(id).append("'),{\n");
        sb.append("  type:'scatter',\n");
        sb.append("  data:{\n");
        sb.append("    datasets:[\n");
        String[] pointStyles = peShapeCycle();   // t2j fix6: honor pointstyles()/msymbol()
        for (int m = 0; m < nModels; m++) {
            String mColor = col(m);
            String pStyle = (m < pointStyles.length) ? pointStyles[m] : "circle";
            if (m > 0) sb.append(",\n");
            sb.append("      {label:'").append(escJs(estLabels[m])).append("',data:[");
            // Build map from name to index for this model
            java.util.HashMap<String, Integer> nameIdx = new java.util.HashMap<>();
            for (int i = 0; i < perModelNames[m].length; i++) {
                nameIdx.put(perModelNames[m][i].trim(), i);
            }
            boolean first = true;
            for (int j = 0; j < k; j++) {
                Integer idx = nameIdx.get(allNames[j]);
                if (idx != null) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{x:").append(j + offsets[m])
                      .append(",y:").append(perModelCoefs[m][idx].trim())
                      .append(",_ci:").append(j).append("}"); // _ci = category index for tooltip
                }
            }
            // t2j fix8w (P4): per-point fill -- model colour when p <= 0.1, hollow otherwise
            // (fix9u: transparent, was plot background); the fill list follows the dataset's point order
            StringBuilder fillM = new StringBuilder("[");
            String bgM = HOLLOW_FILL;
            for (int j = 0; j < k; j++) { if (nameIdx.get(allNames[j]) == null) continue; if (fillM.length() > 1) fillM.append(","); fillM.append("'").append(sigM[m][j].equals("1") ? mColor : bgM).append("'"); }
            fillM.append("]");
            sb.append("],_legendFill:'").append(mColor).append("',pointBackgroundColor:").append(fillM)
              .append(",pointBorderColor:'").append(mColor).append("',pointBorderWidth:2")
              .append(",pointRadius:").append(pSize)
              .append(",pointStyle:'").append(pStyle)
              .append("',pointHoverRadius:").append((parseDouble(pSize, 4) + 2));
            // A10 (fix8): multi-model coefplot honors connected() -- was hardcoded
            // showLine:false, so no connecting line ever drew. The line takes the
            // model's own color and honors linewidth()/smooth() like single-model.
            if (o.chart.peConnected) {
                sb.append(",showLine:true,borderColor:'").append(mColor)
                  .append("',borderWidth:").append(lineWidthOr("2"))
                  .append(",tension:").append(smoothOr("0")).append(",fill:false}");
            } else {
                sb.append(",showLine:false}");
            }
        }
        sb.append("\n    ]\n  },\n");

        // Options
        sb.append("  options:{\n");
        sb.append("    responsive:true,maintainAspectRatio:true,resizeDelay:150,\n");
        sb.append("    aspectRatio:").append(String.format(Locale.ROOT, "%.2f", cjAspect)).append(",\n");
        // t2j fix8m: nearest+intersect:true so the tooltip lands on one marker and
        // dismisses off it (single-coefplot / event-study behavior).
        sb.append("    interaction:{mode:'nearest',intersect:true},\n");

        // Tooltip
        sb.append("    plugins:{\n");
        // t2j fix8 (A9): honor legend(position)/legend(none); was hardcoded position 'top'.
        sb.append("      legend:{display:").append(o.chart.legend.equals("none") ? "false" : "true")
          .append(",position:'").append(o.chart.legend.isEmpty() ? "top" : o.chart.legend)
          .append("',labels:{color:'")
          .append(labelCol).append("',usePointStyle:true,pointStyle:'circle',generateLabels:_spkSigLegend,padding:12").append(postEstLegendExtra()).append("}},\n");
        // t2j fix8m: multi-model coefplot uses real scatter markers, so pin
        // mode:'nearest',intersect:true (as for single coefplot / event study): the
        // tooltip shows the single point under the cursor and hides when it leaves,
        // instead of the ado tooltipmode(index) default that grouped points and lingered.
        sb.append("      tooltip:{").append(tooltipStylePrefix()).append("mode:'nearest',intersect:true,\n");
        sb.append("        callbacks:{\n");
        sb.append("          title:function(ctx){var r=ctx[0].raw;var ci=Math.round(r._ci!=null?r._ci:r.x);var l=")
          .append(lblJs).append(";return l[ci]||'';},\n");
        sb.append("          label:function(ctx){var m=ctx.datasetIndex,ci=Math.round(ctx.raw._ci!=null?ctx.raw._ci:ctx.raw.x);\n");
        sb.append("            var d=_cpTipM[m][ci];if(!d)return '';var f=function(v,k){return _spkFmt(v,'coef');};\n");
        sb.append("            if(d.b==null)return [d.m+': (base or omitted)'];\n");
        sb.append("            return [d.m+': '+f(d.b,4),'SE: '+f(d.se,4),\n");
        sb.append("              'p: '+_spkFmt(d.p,'p'),\n");
        sb.append("              'CI: ['+f(d.lo,3)+', '+f(d.hi,3)+']'];}\n");
        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      datalabels:false\n");
        sb.append("    },\n");

        // refTickFn for multi-model: force refval (and pexline) as labeled ticks on y-axis (v3.6.0-s7a)
        String refTickFn = "";
        {
            java.util.List<String> forcedTicks2 = new java.util.ArrayList<>();
            String rv2 = o.chart.peRefval.trim().replaceAll("[^0-9.\\-]", "");
            if (!rv2.isEmpty()) forcedTicks2.add(rv2);
            if (!o.chart.pePexline.isEmpty()) {
                String pv2 = o.chart.pePexline.trim().replaceAll("[^0-9.\\-]", "");
                if (!pv2.isEmpty() && !pv2.equals(rv2)) forcedTicks2.add(pv2);
            }
            if (!forcedTicks2.isEmpty()) {
                StringBuilder vlist2 = new StringBuilder();
                for (int fi = 0; fi < forcedTicks2.size(); fi++) {
                    if (fi > 0) vlist2.append(",");
                    vlist2.append(forcedTicks2.get(fi));
                }
                refTickFn = niceTicksJs(cpNice, vlist2.toString());   // s8r: anchored ticks + forced values
            }
            else refTickFn = niceTicksJs(cpNice, "");
        }

        // Scales: x is linear (numeric positions with jitter), tick labels via callback
        // afterBuildTicks forces ticks at integer positions only (0,1,2,...k-1)
        // so labels are centered under each coefficient group, not at half-integers.
        sb.append("    scales:{\n");
        sb.append("      x:{type:'linear',min:-0.5,max:").append(k - 0.5)
          .append(",grid:{color:'").append(gridCol).append("',offset:false},\n");
        sb.append("        afterBuildTicks:function(axis){axis.ticks=[];for(var i=0;i<").append(k)
          .append(";i++)axis.ticks.push({value:i});},\n");
        sb.append("        ticks:{color:'").append(labelCol).append("',autoSkip:false,\n");
        sb.append("          callback:function(v){var l=").append(lblJs).append(";return l[v]||'';}\n");
        sb.append("        }\n");
        if (!o.axes.xtitle.isEmpty()) sb.append("        ,title:{display:true,text:'").append(escJs(o.axes.xtitle)).append("',color:'").append(labelCol).append("'}\n");
        sb.append("      },\n");
        // v3.6.0-s8b: axis titles (multi-model is always vertical: value on y)
        {   // s9j: key registrations (multi-model)
            String ciLbl = (o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel) + "% CI";
            boolean bandCi = ciStyle.equals("band") || ciStyle.equals("area");
            if (!o.chart.peNoci) key("ci", ciLbl + " (per model colour)", bandCi ? "swatch" : "whisker", bandCi ? colAtAlpha(col(0), 0.22) : col(0), "", "");
            if (!o.chart.peLevels2Val.isEmpty() && !o.chart.peNoci) key("ci_inner", o.chart.peLevels2Val + "% CI (inner)", bandCi ? "swatch" : "whisker_thick", bandCi ? colAtAlpha(col(0), 0.42) : col(0), "", "");
            if (showRefLine) key("refline", "Null (" + (refLineVal == Math.rint(refLineVal) ? String.valueOf((long) refLineVal) : String.format(Locale.ROOT, "%.4g", refLineVal)) + ")", "line", "#888888", "[5,5]", "");
            if (!o.chart.pePexline.isEmpty()) key("pexline", "Reference (" + o.chart.pePexline.trim() + ")", "line", "#999999", "[2,3]", "");
        }
        String mValTitle = !o.axes.ytitle.isEmpty() ? o.axes.ytitle : "Coefficient";
        sb.append("      y:{min:").append(String.format(Locale.ROOT, "%.4f", yMin))
          .append(",max:").append(String.format(Locale.ROOT, "%.4f", yMax))
          .append(",grid:{color:'").append(gridCol).append("'},ticks:{color:'").append(labelCol).append("',includeBounds:false}").append(refTickFn)
          .append(",title:{display:true,text:'").append(escJs(mValTitle)).append("',color:'").append(labelCol).append("'}}\n");
        sb.append("    }\n");
        sb.append("  },\n");

        // ---------------------------------------------------------------
        // Inline plugins: refline + CI (v3.6.0-s6)
        // refval() controls reference line; cicolors()/ciwidth() per-model
        // zeroLineColor declared at top of coefPlotMulti (above)
        // ---------------------------------------------------------------
        String refValJs = String.format(Locale.ROOT, "%.6f", refLineVal);
        String zeroPlugin;
        if (!showRefLine) {
            zeroPlugin = "{id:'cpZero',beforeDatasetsDraw:function(){}}";
        } else {
            zeroPlugin = "{id:'cpZero',beforeDatasetsDraw:function(chart){"
                + "var ctx=chart.ctx,yS=chart.scales.y,a=chart.chartArea;"
                + "var px=yS.getPixelForValue(" + refValJs + ");"
                + "if(px>=a.top&&px<=a.bottom){"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.beginPath();ctx.setLineDash([5,3]);"
                + "ctx.strokeStyle='" + zeroLineColor + "';ctx.lineWidth=" + refLineWidthOr("1") + ";"
                + "ctx.moveTo(a.left,px);ctx.lineTo(a.right,px);ctx.stroke();"
                + "ctx.restore();}}}";
        }

        // ciStyle already declared above

        // pexline for multi-model coefplot (v3.6.0-s7a)
        // Multi-model always uses vertical bar layout (no indexAxis): x=positions, y=values.
        // Value axis is always scales.y in multi-model, so isHorizontal=false for pexline.
        String pexlinePlugin2 = buildPexlinePlugin(false, o.chart.pePexline,
            o.chart.pePexline.isEmpty() ? "" : "#999999", gen.isDark());
        String pexSuffix2 = pexlinePlugin2.isEmpty() ? "" : "," + pexlinePlugin2;

        // t2j fix8 (deep-dive r3): on-canvas significance stars for the MULTI-model coefplot
        // too (same thresholds as the table). Markers are dodged by offsets[m], so a star is
        // drawn above each model's marker at x=j+offset[m]; hidden models (legend toggle) are
        // skipped. Appended via pexSuffix2 (in all 4 multi-model plugin branches).
        if (!o.table.noStars && o.table.stars.length > 0) {
            StringBuilder thrM = new StringBuilder("[");
            for (int _si = 0; _si < o.table.stars.length; _si++) { if (_si > 0) thrM.append(","); thrM.append(o.table.stars[_si]); }
            thrM.append("]");
            StringBuilder offJs = new StringBuilder("[");
            for (int m = 0; m < nModels; m++) { if (m > 0) offJs.append(","); offJs.append(String.format(Locale.ROOT, "%.3f", offsets[m])); }
            offJs.append("]");
            String starColM = labelColor();
            String cpMStars = ",{id:'cpMStars',afterDatasetsDraw:function(chart){"
                + "var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;var _T=" + thrM + ",_OFF=" + offJs + ";"
                + "function _st(p){if(p==null||isNaN(p))return '';var s='';for(var q=0;q<_T.length;q++){if(p<_T[q])s+='*';}return s;}"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();"
                + "ctx.fillStyle='" + starColM + "';ctx.font='bold 10px sans-serif';ctx.textAlign='center';ctx.textBaseline='bottom';"
                + "for(var m=0;m<_cpTipM.length;m++){if(chart.getDatasetMeta(m)&&chart.getDatasetMeta(m).hidden)continue;"
                + "for(var j=0;j<_cpTipM[m].length;j++){var d=_cpTipM[m][j];if(!d||d.b==null)continue;var st=_st(d.p);if(!st)continue;"
                + "var xp=xS.getPixelForValue(j+_OFF[m]),yh=yS.getPixelForValue(d.hi!=null?d.hi:d.b);ctx.fillText(st,xp,yh-3);}}"
                + "ctx.restore()}}";
            pexSuffix2 = pexSuffix2 + cpMStars;
        }

        // levels2 for multi-model: parse inner CI bounds (v3.6.0-s7b)
        // peLevels2Lo/Hi: tilde-sep by model, pipe-sep by coef within model
        String lo2rawM = o.chart.peLevels2Lo.startsWith("|") ? o.chart.peLevels2Lo.substring(1) : o.chart.peLevels2Lo;
        String hi2rawM = o.chart.peLevels2Hi.startsWith("|") ? o.chart.peLevels2Hi.substring(1) : o.chart.peLevels2Hi;
        // v3.6.0-s8c: without levels() the ado accumulates "~~" (one empty group per
        // model). That is not a real inner CI: require at least one numeric token.
        // (Was: rc=5101 "Index 1 out of bounds for length 1" on every multi-model
        //  coefplot without levels().)
        boolean hasLevels2M = !lo2rawM.replace("~","").replace("|","").trim().isEmpty()
                           && !hi2rawM.replace("~","").replace("|","").trim().isEmpty();
        String[][] perModelLower2 = new String[nModels][];
        String[][] perModelUpper2 = new String[nModels][];
        if (hasLevels2M) {
            String[] modelLower2 = lo2rawM.split("~", -1);
            String[] modelUpper2 = hi2rawM.split("~", -1);
            hasLevels2M = modelLower2.length >= nModels && modelUpper2.length >= nModels;
            if (hasLevels2M) {
                for (int m = 0; m < nModels; m++) {
                    String ml2 = modelLower2[m].startsWith("|") ? modelLower2[m].substring(1) : modelLower2[m];
                    String mh2 = modelUpper2[m].startsWith("|") ? modelUpper2[m].substring(1) : modelUpper2[m];
                    perModelLower2[m] = ml2.split("\\|", -1);
                    perModelUpper2[m] = mh2.split("\\|", -1);
                }
            }
        }

        // Build cpMInner plugin string for multi-model inner CI (v3.6.0-s7b)
        String levels2Suffix2 = "";
        if (hasLevels2M) {
            StringBuilder innerPlugin = new StringBuilder();
            innerPlugin.append(",{id:'cpMInner',afterDatasetsDraw:function(chart){\n");
            innerPlugin.append("    var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;\n");
            // Build per-model lo2M/hi2M arrays using same name-index mapping as cpMWhisker
            for (int m = 0; m < nModels; m++) {
                java.util.HashMap<String, Integer> nameIdx2 = new java.util.HashMap<>();
                for (int i = 0; i < perModelNames[m].length; i++)
                    nameIdx2.put(perModelNames[m][i].trim(), i);
                innerPlugin.append("    var lo2m").append(m).append("=[");
                for (int j = 0; j < k; j++) {
                    if (j > 0) innerPlugin.append(",");
                    Integer idx2 = nameIdx2.get(allNames[j]);
                    innerPlugin.append(idx2 != null && idx2 < perModelLower2[m].length ? numOrNull(perModelLower2[m], idx2) : "null");
                }
                innerPlugin.append("],hi2m").append(m).append("=[");
                for (int j = 0; j < k; j++) {
                    if (j > 0) innerPlugin.append(",");
                    Integer idx2 = nameIdx2.get(allNames[j]);
                    innerPlugin.append(idx2 != null && idx2 < perModelUpper2[m].length ? numOrNull(perModelUpper2[m], idx2) : "null");
                }
                innerPlugin.append("],off2m").append(m).append("=").append(String.format(Locale.ROOT, "%.3f", offsets[m]))
                          .append(",clr2m").append(m).append("='").append(col(m)).append("';\n");
            }
            // Draw inner CI: thick whiskers
            innerPlugin.append("    ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.lineWidth=").append(String.format(Locale.ROOT, "%.1f", ciWidthVal + 2.0)).append(";\n");   // t2j fix8 (r2): inner whisker honors ciwidth() (was 3.5)
            for (int m = 0; m < nModels; m++) {
                innerPlugin.append("    if(!chart.getDatasetMeta(").append(m).append(").hidden){\n");
                innerPlugin.append("    ctx.strokeStyle=clr2m").append(m).append(";\n");
                innerPlugin.append("    for(var i=0;i<").append(k).append(";i++){\n");
                innerPlugin.append("      if(lo2m").append(m).append("[i]==null)continue;\n");
                innerPlugin.append("      var xPx=xS.getPixelForValue(i+off2m").append(m).append(");\n");
                innerPlugin.append("      var yLo=yS.getPixelForValue(lo2m").append(m).append("[i]);\n");
                innerPlugin.append("      var yHi=yS.getPixelForValue(hi2m").append(m).append("[i]);\n");
                innerPlugin.append("      ctx.beginPath();ctx.moveTo(xPx,yLo);ctx.lineTo(xPx,yHi);ctx.stroke();\n");
                innerPlugin.append("      var cap=2;\n");
                innerPlugin.append("      ctx.beginPath();ctx.moveTo(xPx-cap,yLo);ctx.lineTo(xPx+cap,yLo);ctx.stroke();\n");
                innerPlugin.append("      ctx.beginPath();ctx.moveTo(xPx-cap,yHi);ctx.lineTo(xPx+cap,yHi);ctx.stroke();\n");
                innerPlugin.append("    }\n");
                innerPlugin.append("    }\n");
            }
            innerPlugin.append("    ctx.restore()}}\n");
            // Also add legend
            String outerLblM = (o.stats.cilevel != null && !o.stats.cilevel.isEmpty()) ? o.stats.cilevel : "95";
            String innerLblM = (!o.chart.peLevels2Val.isEmpty()) ? o.chart.peLevels2Val : "90";
            String lblColM = gen.isDark() ? "rgba(220,220,220,0.85)" : "rgba(60,60,60,0.85)";
            String legendPlugin = ",{id:'cpMCiLegend',afterDraw:function(chart){"
                + "var ctx=chart.ctx,a=chart.chartArea;"
                + "var x=a.right-10,y=a.top+14;"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.font='11px sans-serif';ctx.fillStyle='" + lblColM + "';ctx.textAlign='right';"
                + "ctx.strokeStyle='" + col(0) + "';"
                + "ctx.lineWidth=3.5;ctx.beginPath();ctx.moveTo(x-54,y-4);ctx.lineTo(x-38,y-4);ctx.stroke();"
                + "ctx.fillText('" + innerLblM + "% CI',x,y);"
                + "ctx.lineWidth=1.5;ctx.beginPath();ctx.moveTo(x-54,y+11);ctx.lineTo(x-38,y+11);ctx.stroke();"
                + "ctx.fillText('" + outerLblM + "% CI',x,y+15);"
                + "ctx.restore()}}";
            // cpDot for multi-model: point estimates on top of all CI layers (v3.6.0-s7b)
            // Multi-model cpMWhisker draws dots internally; suppress not needed here
            // since cpMWhisker draws dots AFTER inner CI in plugin execution order.
            // Actually cpMWhisker fires before cpMInner -- so need same fix.
            // Build per-model dot plugin to fire after cpMInner.
            StringBuilder dotMPlugin = new StringBuilder(",{id:'cpMDot',afterDatasetsDraw:function(chart){");
            dotMPlugin.append("var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();");
            for (int m = 0; m < nModels; m++) {
                dotMPlugin.append("if(!chart.getDatasetMeta(").append(m).append(").hidden){");
                // v3.6.0-s8b: clr2m<m> is a var LOCAL to the cpMInner plugin function --
                // referencing it here threw ReferenceError (dots silently not drawn).
                dotMPlugin.append("ctx.fillStyle='").append(col(m)).append("';");
                dotMPlugin.append("var coefM").append(m).append("=[");
                java.util.HashMap<String, Integer> ni = new java.util.HashMap<>();
                for (int i = 0; i < perModelNames[m].length; i++) ni.put(perModelNames[m][i].trim(), i);
                for (int j = 0; j < k; j++) {
                    if (j > 0) dotMPlugin.append(",");
                    Integer ix = ni.get(allNames[j]);
                    dotMPlugin.append(ix != null ? perModelCoefs[m][ix].trim() : "null");
                }
                dotMPlugin.append("],off2d").append(m).append("=").append(String.format(Locale.ROOT, "%.3f", offsets[m])).append(";");
                dotMPlugin.append("for(var i=0;i<").append(k).append(";i++){");
                dotMPlugin.append("if(coefM").append(m).append("[i]==null)continue;");
                dotMPlugin.append("var xPx=xS.getPixelForValue(i+off2d").append(m).append(");");
                dotMPlugin.append("var yPx=yS.getPixelForValue(coefM").append(m).append("[i]);");
                dotMPlugin.append("_spkSigDot(chart,xPx,yPx,5,'").append(col(m)).append("',_cpSigM[").append(m).append("][i],false);}}");
            }
            dotMPlugin.append("ctx.restore()}}");
            levels2Suffix2 = innerPlugin.toString() + dotMPlugin.toString() + (CANVAS_CI_KEY ? legendPlugin : "");
        }

        if (o.chart.peNoci) {
            sb.append("  plugins:[").append(zeroPlugin).append(levels2Suffix2).append(pexSuffix2).append("]\n");

        } else if (ciStyle.equals("band")) {
            // Multi-model CI band: per-coefficient rectangles (NOT polygon).
            // Variables are unordered -- connecting them as a polygon is wrong.
            // Each model draws an isolated rectangle per coefficient spanning lo..hi.
            // Uses beforeDatasetsDraw (behind scatter points which are afterDraw).
            sb.append("  plugins:[{id:'cpMBand',beforeDatasetsDraw:function(chart){\n");
            sb.append("    var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;\n");
            sb.append("    var a=chart.chartArea;\n");
            // Band height per model: scale by nModels to prevent overlap
            sb.append("    var bh=Math.max(4,(a.bottom-a.top)/").append(k)
              .append("*0.35/").append(nModels).append(");\n");
            for (int m = 0; m < nModels; m++) {
                String mColor = (m < ciColorArr.length && !ciColorArr[m].trim().isEmpty())
                    ? ciColorArr[m].trim() : col(m);
                String mFill   = colAtAlpha(mColor, 0.18);
                String mBorder = colAtAlpha(mColor, 0.55);
                double mOffset = offsets[m];
                java.util.HashMap<String, Integer> nameIdx = new java.util.HashMap<>();
                for (int i = 0; i < perModelNames[m].length; i++)
                    nameIdx.put(perModelNames[m][i].trim(), i);
                // v3.6.0-s6c19: skip when dataset hidden (legend click)
                sb.append("    if(!chart.getDatasetMeta(").append(m).append(").hidden){\n");
                sb.append("    (function(){\n");
                sb.append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n");
                sb.append("      ctx.fillStyle='").append(mFill).append("';\n");
                sb.append("      ctx.strokeStyle='").append(mBorder).append("';ctx.lineWidth=")
                  .append(String.format(Locale.ROOT, "%.1f", Math.min(ciWidthVal, 0.8))).append(";\n");
                for (int j = 0; j < k; j++) {
                    Integer idx = nameIdx.get(allNames[j]);
                    if (idx == null) continue; // coef absent from this model
                    double xPos = j + mOffset;
                    String loStr = perModelLower[m][idx].trim();
                    String hiStr = perModelUpper[m][idx].trim();
                    sb.append("      {\n");
                    sb.append("        var xPx=xS.getPixelForValue(").append(String.format(Locale.ROOT, "%.4f", xPos)).append(");\n");
                    sb.append("        var yLo=yS.getPixelForValue(").append(loStr).append(");\n");
                    sb.append("        var yHi=yS.getPixelForValue(").append(hiStr).append(");\n");
                    sb.append("        ctx.beginPath();\n");
                    sb.append("        if(ctx.roundRect)ctx.roundRect(xPx-bh/2,yHi,bh,yLo-yHi,2);\n");
                    sb.append("        else ctx.rect(xPx-bh/2,yHi,bh,yLo-yHi);\n");
                    sb.append("        ctx.fill();ctx.stroke();\n");
                    sb.append("      }\n");
                }
                sb.append("      ctx.restore();\n");
                sb.append("    })();\n");
                sb.append("    }\n"); // end if(!hidden)
            }
            sb.append("  }},").append(zeroPlugin).append(levels2Suffix2).append(pexSuffix2).append("]\n");

        } else if (ciStyle.equals("area")) {
            // Multi-model CI area: per-coefficient rectangle per model.
            // Coefficients are categorical -- connecting them as a ribbon is wrong.
            // Each coefficient gets its own isolated rectangle (like band) but
            // using the area fill style (same semi-transparent colour).
            // Width scaled by nModels to prevent overlap.
            sb.append("  plugins:[{id:'cpMArea',beforeDatasetsDraw:function(chart){\n");
            sb.append("    var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;\n");
            sb.append("    var a=chart.chartArea;\n");
            sb.append("    var bh=Math.max(4,(a.bottom-a.top)/").append(k)
              .append("*0.35/").append(nModels).append(");\n");
            for (int m = 0; m < nModels; m++) {
                String mColor = (m < ciColorArr.length && !ciColorArr[m].trim().isEmpty())
                    ? ciColorArr[m].trim() : col(m);
                String mFill   = colAtAlpha(mColor, 0.18);
                String mBorder = colAtAlpha(mColor, 0.55);
                double mOffset = offsets[m];
                java.util.HashMap<String, Integer> nameIdx = new java.util.HashMap<>();
                for (int i = 0; i < perModelNames[m].length; i++)
                    nameIdx.put(perModelNames[m][i].trim(), i);
                // v3.6.0-s6c19: skip when dataset hidden (legend click)
                sb.append("    if(!chart.getDatasetMeta(").append(m).append(").hidden){\n");
                sb.append("    (function(){\n");
                sb.append("      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n");
                sb.append("      ctx.fillStyle='").append(mFill).append("';\n");
                sb.append("      ctx.strokeStyle='").append(mBorder).append("';")
                  .append("ctx.lineWidth=").append(String.format(Locale.ROOT, "%.1f", Math.min(ciWidthVal, 0.8))).append(";\n");
                for (int j = 0; j < k; j++) {
                    Integer idx = nameIdx.get(allNames[j]);
                    if (idx == null) continue;
                    double xPos = j + mOffset;
                    String loStr = perModelLower[m][idx].trim();
                    String hiStr = perModelUpper[m][idx].trim();
                    sb.append("      {\n")
                      .append("        var xPx=xS.getPixelForValue(").append(String.format(Locale.ROOT, "%.4f", xPos)).append(");\n")
                      .append("        var yLo=yS.getPixelForValue(").append(loStr).append(");\n")
                      .append("        var yHi=yS.getPixelForValue(").append(hiStr).append(");\n")
                      .append("        ctx.beginPath();\n")
                      .append("        if(ctx.roundRect)ctx.roundRect(xPx-bh/2,yHi,bh,yLo-yHi,2);\n")
                      .append("        else ctx.rect(xPx-bh/2,yHi,bh,yLo-yHi);\n")
                      .append("        ctx.fill();ctx.stroke();\n")
                      .append("      }\n");
                }
                sb.append("      ctx.restore();\n");
                sb.append("    })();\n");
                sb.append("    }\n"); // end if(!hidden)
            }
            sb.append("  }},").append(zeroPlugin).append(levels2Suffix2).append(pexSuffix2).append("]\n");

        } else {
            // whisker (default) -- per-model color from cicolors() or palette
            sb.append("  plugins:[{id:'cpMWhisker',afterDatasetsDraw:function(chart){\n");
            sb.append("    var ctx=chart.ctx,xS=chart.scales.x,yS=chart.scales.y;\n");
            for (int m = 0; m < nModels; m++) {
                String mColor = (m < ciColorArr.length && !ciColorArr[m].trim().isEmpty())
                    ? ciColorArr[m].trim() : col(m);
                java.util.HashMap<String, Integer> nameIdx = new java.util.HashMap<>();
                for (int i = 0; i < perModelNames[m].length; i++)
                    nameIdx.put(perModelNames[m][i].trim(), i);
                sb.append("    var lo").append(m).append("=[");
                for (int j = 0; j < k; j++) {
                    if (j > 0) sb.append(",");
                    Integer idx = nameIdx.get(allNames[j]);
                    sb.append(idx != null ? perModelLower[m][idx].trim() : "null");
                }
                sb.append("],hi").append(m).append("=[");
                for (int j = 0; j < k; j++) {
                    if (j > 0) sb.append(",");
                    Integer idx = nameIdx.get(allNames[j]);
                    sb.append(idx != null ? perModelUpper[m][idx].trim() : "null");
                }
                sb.append("],off").append(m).append("=").append(String.format(Locale.ROOT, "%.3f", offsets[m]))
                  .append(",clr").append(m).append("='").append(mColor).append("';\n");
            }
            sb.append("    ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.lineWidth=").append(ciWidthStr).append(";\n");
            for (int m = 0; m < nModels; m++) {
                // v3.6.0-s6c19: skip CI bars when the corresponding dataset is hidden
                // (user clicked the legend item to cross out this model).
                // chart.getDatasetMeta(m).hidden is set true by Chart.js default legend onClick.
                sb.append("    if(!chart.getDatasetMeta(").append(m).append(").hidden){\n");
                sb.append("    ctx.strokeStyle=clr").append(m).append(";\n");
                sb.append("    for(var i=0;i<").append(k).append(";i++){\n");
                sb.append("      if(lo").append(m).append("[i]==null)continue;\n");
                sb.append("      var xPx=xS.getPixelForValue(i+off").append(m).append(");\n");
                sb.append("      var yLo=yS.getPixelForValue(lo").append(m).append("[i]);\n");
                sb.append("      var yHi=yS.getPixelForValue(hi").append(m).append("[i]);\n");
                sb.append("      ctx.beginPath();ctx.moveTo(xPx,yLo);ctx.lineTo(xPx,yHi);ctx.stroke();\n");
                sb.append("      var cap=3;\n");
                sb.append("      ctx.beginPath();ctx.moveTo(xPx-cap,yLo);ctx.lineTo(xPx+cap,yLo);ctx.stroke();\n");
                sb.append("      ctx.beginPath();ctx.moveTo(xPx-cap,yHi);ctx.lineTo(xPx+cap,yHi);ctx.stroke();\n");
                sb.append("    }\n");
                sb.append("    }\n"); // end if(!hidden)
            }
            sb.append("    ctx.restore();\n");
            sb.append("  }},").append(zeroPlugin).append(levels2Suffix2).append(pexSuffix2).append("]\n");
        }

        sb.append("});\n");
        return sb.toString();
    }

    /**
     * Returns the valid (renderable) CI bar groups for a slice.
     * Excludes groups with n<2 (no CI possible) and empty-key groups (missing values).
     * This list is used by both ciBarLabels() and ciBarDatasets() to ensure
     * labels and data arrays always have the same length.
     *
     * Returns List of {displayLabel, rawKey, ciStats[5]} as Object[3].
     */
    String pie(String id, DataSet data, boolean donut) {
        List<Variable> nv      = data.getNumericVariables();
        Variable       overVar = data.getOverVariable();

        // ----------------------------------------------------------------
        // Determine which of three modes Stata graph pie supports:
        //
        //  Mode 1 -- multi-var, no over(): each variable is one slice,
        //            value = sum of that variable across all observations.
        //            sparkta price mpg weight, type(pie)
        //
        //  Mode 2 -- one var + over(): one slice per over() group,
        //            value = sum of variable within that group.
        //            sparkta price, type(pie) over(rep78)
        //
        //  Mode 3 -- no var, over() only: one slice per over() group,
        //            value = count of observations in that group.
        //            sparkta, type(pie) over(rep78)
        // ----------------------------------------------------------------
        boolean mode1 = !nv.isEmpty() && overVar == null; // multi-var, no over
        boolean mode2 = !nv.isEmpty() && overVar != null; // one var + over
        boolean mode3 =  nv.isEmpty() && overVar != null; // freq count by over

        if (!mode1 && !mode2 && !mode3) return ""; // nothing to render

        // For pie/donut: pct and mean both show percentages; sum shows raw values
        boolean usePct = !o.stats.stat.equals("sum");

        StringBuilder lblJs  = new StringBuilder();
        StringBuilder valJs  = new StringBuilder();
        StringBuilder pieRawJs = new StringBuilder();   // I6: raw slice values for the hover
        StringBuilder bgJs   = new StringBuilder();
        StringBuilder brdJs  = new StringBuilder();
        String datasetLabel;

        if (mode1) {
            // ---- Mode 1: each variable is one slice -------------------------
            datasetLabel = "Variables";
            double total = 0;
            double[] sums = new double[nv.size()];
            for (int i = 0; i < nv.size(); i++) {
                for (Object val : nv.get(i).getValues()) {
                    if (val instanceof Number) {
                        double d = ((Number) val).doubleValue();
                        sums[i] += d;
                        total   += d;
                    }
                }
            }
            if (total == 0) total = 1;
            for (int i = 0; i < nv.size(); i++) {
                double v = usePct ? 100.0 * sums[i] / total : sums[i];
                lblJs.append("'").append(escJs(nv.get(i).getDisplayName())).append("',");
                valJs.append(String.format(Locale.ROOT, "%.4f", v)).append(",");
                pieRawJs.append(String.format(Locale.ROOT, "%.4f", sums[i])).append(",");   // I6
                bgJs.append("'").append(col(i)).append("',");
                brdJs.append("'").append(colS(i)).append("',");
            }

        } else if (mode2) {
            // ---- Mode 2: one var per group ----------------------------------
            Variable valVar = nv.get(0);
            datasetLabel    = valVar.getDisplayName() + " by " + overVar.getDisplayName();
            List<String> groups    = DataSet.uniqueValues(overVar, o.chart.sortgroups, o.showmissingOver);
            List<String> groupKeys = DataSet.uniqueGroupKeys(overVar, o.chart.sortgroups, o.showmissingOver);
            double total = 0;
            double[] sums = new double[groups.size()];
            for (int i = 0; i < overVar.getValues().size(); i++) {
                Object gv = overVar.getValues().get(i);
                Object v  = valVar.getValues().get(i);
                // When showmissingOver: null maps to MISSING_SENTINEL key
                String gc = (gv == null)
                    ? (o.showmissingOver ? DataSet.MISSING_SENTINEL : "")
                    : sdz(String.valueOf(gv));
                int ci = groupKeys.indexOf(gc);
                if (ci < 0) continue;
                if (v instanceof Number) {
                    double d = ((Number) v).doubleValue();
                    sums[ci] += d;
                    total    += d;
                }
            }
            if (total == 0) total = 1;
            for (int i = 0; i < groups.size(); i++) {
                double v = usePct ? 100.0 * sums[i] / total : sums[i];
                boolean isMissing = groups.get(i).equals(DataSet.MISSING_SENTINEL);
                String bg  = isMissing ? "'rgba(160,160,160,0.65)'" : "'" + col(i) + "'";
                String brd = isMissing ? "'rgba(120,120,120,1)'"     : "'" + colS(i) + "'";
                lblJs.append("'").append(escJs(groups.get(i))).append("',");
                valJs.append(String.format(Locale.ROOT, "%.4f", v)).append(",");
                pieRawJs.append(String.format(Locale.ROOT, "%.4f", sums[i])).append(",");   // I6
                pieRawJs.append(String.format(Locale.ROOT, "%.4f", sums[i])).append(",");   // I6
                bgJs.append(bg).append(",");
                brdJs.append(brd).append(",");
            }

        } else {
            // ---- Mode 3: frequency count per over() group -------------------
            datasetLabel = "Frequency by " + overVar.getDisplayName();
            List<String> groups    = DataSet.uniqueValues(overVar, o.chart.sortgroups, o.showmissingOver);
            List<String> groupKeys = DataSet.uniqueGroupKeys(overVar, o.chart.sortgroups, o.showmissingOver);
            int total = 0;
            int[] counts = new int[groups.size()];
            for (Object gv : overVar.getValues()) {
                // When showmissingOver: null maps to MISSING_SENTINEL key
                String gc = (gv == null)
                    ? (o.showmissingOver ? DataSet.MISSING_SENTINEL : "")
                    : sdz(String.valueOf(gv));
                int ci = groupKeys.indexOf(gc);
                if (ci < 0) continue;
                counts[ci]++;
                total++;
            }
            if (total == 0) total = 1;
            for (int i = 0; i < groups.size(); i++) {
                double v = usePct ? 100.0 * counts[i] / total : counts[i];
                boolean isMissing = groups.get(i).equals(DataSet.MISSING_SENTINEL);
                String bg  = isMissing ? "'rgba(160,160,160,0.65)'" : "'" + col(i) + "'";
                String brd = isMissing ? "'rgba(120,120,120,1)'"     : "'" + colS(i) + "'";
                lblJs.append("'").append(escJs(groups.get(i))).append("',");
                valJs.append(String.format(Locale.ROOT, "%.4f", v)).append(",");
                bgJs.append(bg).append(",");
                brdJs.append(brd).append(",");
            }
        }

        String cutoutVal  = donut ? (o.stats.cutout.isEmpty() ? "'55%'" : "'"+o.stats.cutout+"%'") : "'0%'";
        String rotVal     = o.stats.rotation.isEmpty()      ? "0"   : o.stats.rotation;
        String circumVal  = o.stats.circumference.isEmpty() ? "360" : o.stats.circumference;
        String borderW    = o.stats.sliceborder.isEmpty()   ? "1"   : o.stats.sliceborder;
        String hoverOff   = o.stats.hoveroffset.isEmpty()   ? "8"   : o.stats.hoveroffset;
        String easingCfg  = o.chart.easing.isEmpty() ? "" : ",easing:'"+o.chart.easing+"'";
        String legendPos  = o.chart.legend.equals("none") ? "none" : o.chart.legend;

        // Per-chart plugin registration for datalabels on pie/doughnut.
        // Global Chart.register() causes blank charts in Chart.js 4.
        String piePluginsArr = o.chart.pielabels
            ? "  plugins:(typeof ChartDataLabels!=='undefined'?[ChartDataLabels]:[]),\n"
            : "";

        String dlSection = "";
        if (o.chart.pielabels) {
            String dlFmt = usePct ? "return parseFloat(v).toFixed(1)+'%';" : "return parseFloat(v).toFixed(2);";
            dlSection = ",\n      datalabels:{color:'#fff',font:{weight:'bold',size:12},"
                + "formatter:function(v){"+dlFmt+"}}";
        }

        String pieRaw = pieRawJs.toString(); if (pieRaw.endsWith(",")) pieRaw = pieRaw.substring(0, pieRaw.length()-1);
        return "var _spkPieRaw=[" + pieRaw + "];\n"   // I6: raw slice values for the hover
            + "new Chart(document.getElementById('"+id+"'), {\n"
            + "  type:'"+(donut?"doughnut":"pie")+"',\n"
            + piePluginsArr
            + "  data:{\n"
            + "    labels:["+lblJs+"],\n"
            + "    datasets:[{label:'"+escJs(datasetLabel)+"',_spkRaw:[" + pieRaw + "],"   // fix9a: per-chart raw values (by() panels each keep their own)
            + "      data:["+valJs+"],backgroundColor:["+bgJs+"],borderColor:["+brdJs+"],"
            + "      borderWidth:"+borderW+",hoverOffset:"+hoverOff+"}]\n"
            + "  },\n"
            + "  options:{\n"
            + "    responsive:true,\n"
            // t2j fix8r (decision 1): pie/donut never emitted aspectRatio, so Chart.js used its
            // pie default of 1 and a full-width page drew a 986 px disc. Default to 2 (the same
            // box every other chart type gets; Chart.js centres the disc in it), honouring a
            // user aspect() when given. by() panels set aspect 1.35 before reaching here.
            + "    aspectRatio:"+(o.chart.aspect.isEmpty() ? "2" : o.chart.aspect)+",\n"
            + "    cutout:"+cutoutVal+",\n"
            + "    rotation:"+rotVal+",\n"
            + "    circumference:"+circumVal+",\n"
            + "    animation:{duration:"+animDuration()+easingCfg+"},\n"
            + buildPadding()
            + "    plugins:{\n"
            + "      legend:{display:"+(legendPos.equals("none")?"false":"true")+","
            // A6 (fix8): route pie/donut legend labels through the shared
            // legendLabelsCfg() source of truth so legcolor/legbgcolor/legendsize/
            // legendboxheight are honored here exactly as on every other chart.
            // Pie keeps its own padding/boxWidth defaults appended after.
            + "position:'"+legendPos+"',labels:{"+legendLabelsCfg()+",padding:16,boxWidth:14}"
            + (o.chart.legendtitle.isEmpty() ? "" : ",title:{display:true,text:'"+escJs(o.chart.legendtitle)+"',color:'"+labelColor()+"'}")
            + "}"
            + dlSection + ",\n"
            + "      " + buildPieTooltipCfg(usePct) + "\n"
            + "    }\n"
            + "  }\n"
            + "});\n";
    }

    // -- Dataset builders ------------------------------------------------------


    /**
     * Parse a pipe-separated label string into a List of tokens.
     * Uses | as separator so multi-word labels work without any quoting.
     * e.g. parseCustomLabels("Low|Med|High")            -> ["Low","Med","High"]
     *      parseCustomLabels("Very Low|Med|Very High")  -> ["Very Low","Med","Very High"]
     * Single-word labels can also be space-separated for convenience:
     *      parseCustomLabels("Low Med High")            -> ["Low","Med","High"]
     * But | takes priority -- if any | is present, split on | only.
     */
    // =========================================================================
    // TOOLTIP CALLBACKS (v2.1.0 revised)
    // =========================================================================
    // Clean consistent layout for all chart types:
    //
    //   Title  = group name / x-axis category (never a number)
    //   Line 1 = variable name (indented)
    //   Line 2 = StatLabel   value    (stat reflects actual stat() option)
    //   Line 3 = CI level CI [lo - hi]  (CI charts only)
    //   Line 4 = N  value               (always last)
    //
    // Rules:
    //   - Stat label always reflects o.stats.stat: Mean/Median/Sum/Count/Min/Max
    //   - tooltipformat() respected via tooltipNumFmt() if set
    //   - N sourced from: embedded data point (CI), _dashData (bar/hist), or omitted
    //   - Scatter/bubble: y variable first (matches Stata convention), then x
    //   - No value ever appears twice
    // =========================================================================

    /** Human-readable stat label matching the current o.stats.stat setting. */
    private String tooltipStatLabel() {
        if (o.stats.stat == null) return "Mean";
        switch (o.stats.stat.toLowerCase(Locale.ROOT)) {
            case "sum":    return "Sum";
            case "count":  return "Count";
            case "median": return "Median";
            case "min":    return "Min";
            case "max":    return "Max";
            default:       return "Mean";
        }
    }

    /**
     * Returns a JS expression that formats numeric variable v.
     * Parses tooltipformat() for decimal places and comma grouping.
     * Falls back to comma-thousands auto-rounding (0-4 dp, no trailing zeros).
     */
    private String tooltipNumFmt(String v) {
        if (!o.chart.tooltipfmt.isEmpty()) {
            String fmt = o.chart.tooltipfmt.trim();
            int dp = 2;
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\.([0-9]+)f").matcher(fmt);
            if (m.find()) { try { dp = Integer.parseInt(m.group(1)); } catch (Exception ignore) {} }
            boolean comma = fmt.contains(",");
            return v + ".toLocaleString(undefined,{minimumFractionDigits:" + dp
                + ",maximumFractionDigits:" + dp
                + (comma ? ",useGrouping:true" : ",useGrouping:false") + "})";
        }
        return "parseFloat(" + v + ".toFixed(4)).toLocaleString(undefined,"
            + "{minimumFractionDigits:0,maximumFractionDigits:4})";
    }

    /**
     * t2j fix8f (issue #11): histogram y-value formatter. Density values are tiny
     * (1/range scale, e.g. 0.0000397), and the generic 4-decimal formatter collapsed
     * them to "0" and rounded 0.000264 to "0.0003" (so a bar read "0.0003" yet sat
     * below the 0.0003 gridline). This keeps counts/percents/fractions >= 1 at up to
     * two decimals with grouping, and shows small magnitudes (density, fractions < 1)
     * with 3 significant figures so they never read as 0. An explicit tooltipfmt()
     * still wins, via tooltipNumFmt.
     */
    private String histNumFmt(String v) {
        if (!o.chart.tooltipfmt.isEmpty()) return tooltipNumFmt(v);
        return "(function(_x){var _a=Math.abs(_x);"
            + "return (_a>=1||_x===0)?_x.toLocaleString(undefined,{maximumFractionDigits:2,useGrouping:true})"
            + ":_x.toLocaleString(undefined,{maximumSignificantDigits:3});})(" + v + ")";
    }

    /**
     * Tooltip for bar/line/area charts.
     * Title = x-axis label (group name or variable name).
     * Per dataset: variable name, stat label + value.
     * AfterBody: N from _dashData.
     */
    private String buildBarLineTooltipCfg() {
        // For 100% stacked bar: show pct + raw value on separate lines
        if (o.chart.stack100) return buildStack100TooltipCfg();

        String ttMode  = o.chart.tooltipmode.isEmpty() ? "nearest" : o.chart.tooltipmode;
        String ttPos   = o.chart.tooltippos.equals("average") ? "nearest" : o.chart.tooltippos;
        String statLbl = escJs(tooltipStatLabel());
        String fmtV    = tooltipNumFmt("v");
        // I6 (v3.6.0-s8y): title = "<over label>: <group>", body = "<Stat> of <var>: value",
        // then "n = k (share of N)". Counts come from the same aggregation the chart draws:
        // window._spkTipN (engine, live under filters) else the static _spkTipN0 (Java).
        String overLbl = "";
        String userFmt = o.chart.tooltipfmt.isEmpty() ? "" : fmtV;   // tooltipformat() wins
        String valExpr = userFmt.isEmpty() ? "_spkFmt(v)" : userFmt;
        String horizVal = o.chart.horizontal ? "ctx.parsed.x" : "ctx.parsed.y";
        return "tooltip:{" + tooltipStylePrefix() + "mode:'" + ttMode + "',intersect:true,position:'" + ttPos + "',"
            + "callbacks:{"
            + "title:function(items){"
            +   "if(!items.length)return '';var ol=(typeof _spkOverLabel==='string'&&_spkOverLabel)?_spkOverLabel+': ':'';"
            +   "return ol+items[0].label;"
            + "},"
            + "label:function(ctx){"
            +   "var v=" + horizVal + ";"
            +   "if(v===null||v===undefined)return '';"
            +   "var lbl=ctx.dataset.label||'';"
            +   "var val=" + valExpr + ";"
            +   "var tn=(window._spkTipN&&window._spkTipN[ctx.datasetIndex])?window._spkTipN[ctx.datasetIndex]:((typeof _spkTipN0!=='undefined'&&_spkTipN0[ctx.datasetIndex])?_spkTipN0[ctx.datasetIndex]:null);"
            +   "var n=tn?tn[ctx.dataIndex]:null;var tot=window._spkTipTotal||(typeof _spkTipTotal0!=='undefined'?_spkTipTotal0:null);"
            +   "var out=['  " + statLbl + " of '+lbl+':  '+val];"
            +   "if(n!==null&&n!==undefined)out.push('  n = '+_spkFmt(n,'int')+(tot?'  ('+_spkFmt(100*n/tot,'pct')+' of '+_spkFmt(tot,'int')+')':''));"
            +   "return out;"
            + "}"
            + "}}";
    }

    /**
     * Tooltip for scatter charts.
     * Title = dataset label (group name when over(), variable pair when not).
     * y variable shown first (matches Stata convention), then x.
     */
    private String buildScatterTooltipCfg(String xLabel, String yLabel) {
        String ttPos = o.chart.tooltippos.equals("average") ? "nearest" : o.chart.tooltippos;
        String fmtX  = tooltipNumFmt("ctx.parsed.x");
        String fmtY  = tooltipNumFmt("ctx.parsed.y");
        // v3.5.58: if mlabel variable is set, show its value as first tooltip line
        String labelLine = o.chart.mlabelVar.isEmpty() ? ""
            : "var _lbl=ctx.dataset.data[ctx.dataIndex];if(_lbl&&_lbl.label)lines.unshift('  '+_lbl.label);";
        return "tooltip:{" + tooltipStylePrefix() + "mode:'point',intersect:true,position:'" + ttPos + "',"
            + "callbacks:{"
            + "title:function(items){"
            +   "return items.length?items[0].dataset.label:'';"
            + "},"
            // y first, then x -- matches Stata scatter y x convention
            // label line prepended when mlabel variable is present
            + "label:function(ctx){"
            +   "var lines=["
            +     "'  " + escJs(yLabel) + ":  '+" + fmtY + ","
            +     "'  " + escJs(xLabel) + ":  '+" + fmtX
            +   "];"
            + labelLine
            + "return lines;"
            + "}"
            + "}}";
    }

    /**
     * Tooltip for bubble charts.
     * Title = group name. y first, then x, then size variable name.
     */
    private String buildBubbleTooltipCfg(String xLabel, String yLabel, String rLabel) {
        String ttPos = o.chart.tooltippos.equals("average") ? "nearest" : o.chart.tooltippos;
        String fmtX  = tooltipNumFmt("ctx.parsed.x");
        String fmtY  = tooltipNumFmt("ctx.parsed.y");
        return "tooltip:{" + tooltipStylePrefix() + "mode:'point',intersect:true,position:'" + ttPos + "',"
            + "callbacks:{"
            + "title:function(items){"
            +   "return items.length?items[0].dataset.label:'';"
            + "},"
            + "label:function(ctx){"
            +   "return ["
            +     "'  " + escJs(yLabel) + ":  '+" + fmtY + ","
            +     "'  " + escJs(xLabel) + ":  '+" + fmtX + ","
            +     "'  Size:  " + escJs(rLabel) + "'"
            +   "];"
            + "}"
            + "}}";
    }

    /**
     * Tooltip for pie/donut charts.
     * Title = dataset label (variable by group).
     * pct mode: slice name, percentage.
     * sum mode: slice name, raw value.
     */
    private String buildPieTooltipCfg(boolean usePct) {
        String fmtPct = "ctx.parsed.toFixed(1)+'%'";
        String fmtRaw = tooltipNumFmt("ctx.parsed");
        if (usePct) {
            return "tooltip:{" + tooltipStylePrefix() + "callbacks:{"
                + "title:function(items){return items.length?items[0].dataset.label:'';},"
                + "label:function(ctx){"
                +   "var _dr=ctx.dataset&&ctx.dataset._spkRaw;"   // fix9a: dataset-level raw first (by() panels), page global second
                +   "var raw=(_dr&&_dr[ctx.dataIndex]!==undefined)?_dr[ctx.dataIndex]:((typeof _spkPieRaw!=='undefined'&&_spkPieRaw[ctx.dataIndex]!==undefined)?_spkPieRaw[ctx.dataIndex]:null);"
                +   "var isCount=" + (o.stats.stat.equals("count") || o.stats.stat.isEmpty() ? "true" : "false") + ";"
                +   "return ['  '+ctx.label+':  '+(raw===null?'':(isCount?_spkFmt(raw,'int')+' obs':_spkFmt(raw))+'  (')+_spkFmt(ctx.parsed,'pct')+(raw===null?'':')')];"
                + "}"
                + "}}";
        } else {
            return "tooltip:{" + tooltipStylePrefix() + "callbacks:{"
                + "title:function(items){return items.length?items[0].dataset.label:'';},"
                + "label:function(ctx){"
                +   "return ["
                +     "'  '+ctx.label+':  '+(" + fmtRaw + ")"
                +   "];"
                + "}"
                + "}}";
        }
    }

    /**
     * Tooltip for histogram.
     * Title = "varName  [lo, hi)".
     * Lines: y-axis metric value, N.
     */
    /**
     * Tooltip for histogram charts. (v2.6.1)
     * Title: variable name + bin range from _ttRanges_.
     * Label: metric value + "Obs in bin: N" from _ttCounts_ (per-bin count).
     * Per-bin counts are emitted in _histPreamble alongside _ttRanges_.
     */
    private String buildHistTooltipCfg(String id, String varLabel,
                                        String yMetric, String nStr) {
        String ttId  = id.replace("-", "_");
        String fmtV  = histNumFmt("v");   // t2j fix8f (issue #11): density-aware formatting
        return "tooltip:{" + tooltipStylePrefix() + ""
            + "callbacks:{"
            + "title:function(items){"
            +   "var i=items[0].dataIndex;"
            +   "var rng=typeof _ttRanges_" + ttId + "!=='undefined'?(_ttRanges_" + ttId + "[i]||''):'bins';"
            +   "return '" + escJs(varLabel) + "  '+rng;"
            + "},"
            + "label:function(item){"
            +   "var v=item.raw;"
            // v2.6.1: read per-bin count from _ttCounts_; fall back to total N if missing
            +   "var i=item.dataIndex;"
            +   "var binN=(typeof _ttCounts_" + ttId + "!=='undefined'&&_ttCounts_" + ttId + "[i]!=null)"
            +       "?_ttCounts_" + ttId + "[i]:" + nStr + ";"
            +   "return ["
            +     "'  " + escJs(yMetric) + ":  '+" + fmtV + ","
            +     "'  Obs in bin: '+binN"
            +   "];"
            + "}"
            + "}}";
    }

    /**
     * Tooltip for CI bar charts (barWithErrorBars plugin).
     * Title = x-axis group label.
     * Lines: variable name, Mean, CI [lo - hi], N.
     * N is read from ctx.raw.n -- embedded in data point by ciBarDatasets().
     */
    private String buildCiBarTooltipCfg() {
        String ttMode = o.chart.tooltipmode.isEmpty() ? "nearest" : o.chart.tooltipmode;
        String ttPos  = o.chart.tooltippos.equals("average") ? "nearest" : o.chart.tooltippos;
        String ciLvl  = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel.trim();
        String fmtMean = tooltipNumFmt("mean");
        String fmtLo   = tooltipNumFmt("lo");
        String fmtHi   = tooltipNumFmt("hi");
        return "tooltip:{" + tooltipStylePrefix() + "mode:'" + ttMode + "',intersect:true,position:'" + ttPos + "',"
            + "callbacks:{"
            + "title:function(items){"
            +   "return items.length?items[0].label:'';"
            + "},"
            + "label:function(ctx){"
            +   "var lbl=ctx.dataset.label||'';"
            // Strip the "(95% CI)" suffix added by ciBarDatasets to dataset label
            +   "lbl=lbl.replace(/ *\\([0-9]+% CI\\)$/,'');"
            +   "var mean=ctx.raw.y;"
            +   "var lo=ctx.raw.yMin;"
            +   "var hi=ctx.raw.yMax;"
            +   "var n=ctx.raw.n;"
            +   "if(mean===null||mean===undefined)return '';"
            +   "var lines=["
            +     "'  '+lbl,"
            +     "'  Mean:  '+" + fmtMean + ","
            +     "'  " + ciLvl + "% CI:  ['+" + fmtLo + "+'  -  '+" + fmtHi + "+']'"
            +   "];"
            +   "if(n!==undefined&&n!==null)lines.push('  N:  '+n);"
            +   "return lines;"
            + "}"
            + "}}";
    }

    /**
     * Tooltip for CI line charts.
     * Filter callback hides upper/lower band datasets.
     * For mean datasets: variable name, Mean, CI bounds from sibling datasets,
     * N from embedded _n array.
     */
    private String buildCiLineTooltipCfg() {
        String ttMode = o.chart.tooltipmode.isEmpty() ? "nearest" : o.chart.tooltipmode;
        String ttPos  = o.chart.tooltippos.equals("average") ? "nearest" : o.chart.tooltippos;
        String ciLvl  = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel.trim();
        String fmtMean = tooltipNumFmt("mean");
        String fmtLo   = tooltipNumFmt("lo");
        String fmtHi   = tooltipNumFmt("hi");
        return "tooltip:{" + tooltipStylePrefix() + "mode:'" + ttMode + "',intersect:true,position:'" + ttPos + "',"
            // Only show tooltip for mean line datasets (not upper/lower band)
            + "filter:function(item){"
            +   "return !item.dataset.label.endsWith(' upper')"
            +     "&&!item.dataset.label.endsWith(' lower');"
            + "},"
            + "callbacks:{"
            + "title:function(items){"
            +   "return items.length?items[0].label:'';"
            + "},"
            + "label:function(ctx){"
            +   "var mean=ctx.parsed.y;"
            +   "if(mean===null||mean===undefined)return '';"
            +   "var lbl=ctx.dataset.label||'';"
            +   "var di=ctx.datasetIndex;"
            +   "var idx=ctx.dataIndex;"
            +   "var chart=ctx.chart;"
            // Upper is di+1, lower is di+2 (layout: mean,upper,lower per variable)
            +   "var upper=chart.data.datasets[di+1]?chart.data.datasets[di+1].data[idx]:null;"
            +   "var lower=chart.data.datasets[di+2]?chart.data.datasets[di+2].data[idx]:null;"
            // N from embedded _n array on the mean dataset
            +   "var n=ctx.dataset._n?ctx.dataset._n[idx]:null;"
            +   "var lines=['  '+lbl,'  Mean:  '+" + fmtMean + "];"
            +   "if(upper!==null&&upper!==undefined&&lower!==null&&lower!==undefined){"
            +     "var lo=lower,hi=upper;"
            +     "lines.push('  " + ciLvl + "% CI:  ['+" + fmtLo + "+'  -  '+" + fmtHi + "+']');"
            +   "}"
            +   "if(n!==null&&n!==undefined)lines.push('  N:  '+n);"
            +   "return lines;"
            + "}"
            + "}}";
    }


    /**
     * Tooltip for 100% stacked bar charts (v2.2.0).
     * Title = x-axis group label.
     * Per dataset: variable name, percentage share, raw value on next line.
     * AfterBody: column total so users can reconstruct absolute values.
     * Raw values stored in dataset._raw[] by overDatasets100().
     */
    private String buildStack100TooltipCfg() {
        String ttMode  = o.chart.tooltipmode.isEmpty() ? "index" : o.chart.tooltipmode;
        String ttPos   = o.chart.tooltippos.equals("average") ? "average" : o.chart.tooltippos;
        String fmtRaw  = tooltipNumFmt("raw");
        String fmtTot  = tooltipNumFmt("total");
        String statLbl = escJs(tooltipStatLabel());
        // v3.5.14: with indexAxis:'y' (horizontal bar), Chart.js puts the numeric
        // value in ctx.parsed.x (not ctx.parsed.y). Use the correct axis.
        String parsedPct = o.chart.horizontal ? "ctx.parsed.x" : "ctx.parsed.y";
        return "tooltip:{" + tooltipStylePrefix() + "mode:'" + ttMode + "',intersect:true,position:'" + ttPos + "',"
            + "callbacks:{"
            + "title:function(items){"
            +   "return items.length?items[0].label:'';"
            + "},"
            + "label:function(ctx){"
            +   "var pct=" + parsedPct + ";"
            +   "if(pct===null||pct===undefined)return '';"
            +   "var lbl=ctx.dataset.label||'';"
            +   "var raw=ctx.dataset._raw?ctx.dataset._raw[ctx.dataIndex]:null;"
            +   "var lines=['  '+lbl,'  Share:  '+pct.toFixed(1)+'%'];"
            +   "if(raw!==null&&raw!==undefined){"
            +     "lines.push('  " + statLbl + ":  '+" + fmtRaw + ");"
            +   "}"
            +   "return lines;"
            + "},"
            + "afterBody:function(items){"
            +   "if(!items.length)return [];"
            +   "var total=0,hasTotal=false;"
            +   "items.forEach(function(item){"
            +     "var raw=item.dataset._raw?item.dataset._raw[item.dataIndex]:null;"
            +     "if(raw!==null&&raw!==undefined){total+=Math.abs(raw);hasTotal=true;}"
            +   "});"
            +   "if(!hasTotal)return [];"
            +   "return ['  Column total:  '+" + fmtTot + "];"
            + "}"
            + "}}";
    }

    String dlSuppress() {
        return (o.chart.pielabels && !o.chart.datalabels) ? ",datalabels:{display:false}" : "";
    }

    /** fix9h: the fit line now carries order:-1 (drawn above the points), and Chart.js sorts
     *  legend entries by dataset order, which would put "Fit" before the data series -- keep
     *  the legend in dataset order (points first, fit after) on fit() pages. */
    String scatterLegendCfg() {
        String cfg = buildLegendConfig();
        if (o.chart.fit.isEmpty()) return cfg;
        // fix9o ([stated] Fahad 2026-09-15: the fit line's key entry should be a dashed line in the
        // fit colour, not a hollow circle): with usePointStyle Chart.js builds each entry from the
        // POINT style (getStyle(0)), which has no borderDash, so a pointRadius:0 line dataset
        // came out as an empty ring and, with pointStyle:'line', as a solid line. Wrap the
        // default generateLabels and copy the dataset's own dash/width/colour onto the entry
        // for point-less line datasets (the fit lines). Skipped when leglabels()/relabel()
        // already installed their own generateLabels.
        String fitLeg = cfg.contains("generateLabels") ? ""
            : "generateLabels:function(c){var d=Chart.defaults.plugins.legend.labels.generateLabels(c);d.forEach(function(it){var ds=c.data.datasets[it.datasetIndex];"
            + "if(ds&&ds.borderDash&&ds.pointRadius===0&&(ds.type==='line'||c.config.type==='line')){it.lineDash=ds.borderDash;it.lineWidth=ds.borderWidth||2;it.strokeStyle=ds.borderColor;it.pointStyle='line';}});return d;},";
        return cfg.replace("labels:{", "labels:{" + fitLeg + "sort:function(a,b){return a.datasetIndex-b.datasetIndex;},");
    }

    String buildLegendConfig() {
        if (o.chart.legend.equals("none")) return "{display:false}";
        StringBuilder sb = new StringBuilder("{display:true,position:'").append(o.chart.legend).append("',");
        sb.append("labels:{").append(legendLabelsCfg()).append("}");
        if (!o.chart.legendtitle.isEmpty())
            sb.append(",title:{display:true,text:'").append(escJs(o.chart.legendtitle)).append("',color:'").append(labelColor()).append("'}");
        sb.append("}");
        return sb.toString();
    }

    // =========================================================================
    // SHARED CONFIG BUILDER METHODS (v2.6.0 -- Phase 1-A+B)
    // Each of these is the single source of truth for its config fragment.
    // Add styling options here once; every chart method picks them up via
    // delegation. No duplication across the 6+ tooltip/axis/legend methods.
    // =========================================================================

    /**
     * Returns the legend labels config key=value pairs (without enclosing braces).
     * Called by buildLegendConfig() and buildLegendConfigCiLine().
     * Phase 1-A: legColor and legBgColor from o.style.
     * Written ONCE -- every legend config calls this.
     */
    private String legendLabelsCfg() {
        String color = o.style.legColor.isEmpty() ? labelColor() : o.style.legColor;
        StringBuilder sb = new StringBuilder("color:'").append(color).append("'");
        // s9i (user rule): legend glyphs must carry the series' own marker and dash pattern.
        // usePointStyle makes Chart.js draw each entry with that dataset's pointStyle and
        // borderDash instead of a plain box.
        if (o.type.equals("line") || o.type.equals("area") || o.type.equals("scatter") || o.type.equals("bubble") || o.type.equals("ciline"))
            sb.append(",usePointStyle:true");
        // v3.6.0-s8f: CI helper datasets (ciline "... (95% CI) upper/lower", fitci
        // "... 95% CI (upper)") are drawing aids, not series -- keep them out of the
        // legend (VIZ_STANDARD LEG-REDUN / Tufte non-data ink). The band itself still
        // draws; toggling the parent series hides it via the dataset's own logic.
        sb.append(",filter:function(it){return !/(\\(upper\\)|\\(lower\\)| upper$| lower$)/.test(String(it.text||''));}");
        if (!o.chart.legendsize.isEmpty())
            sb.append(",font:{size:").append(o.chart.legendsize).append("}");
        if (!o.chart.legendboxheight.isEmpty())
            sb.append(",boxHeight:").append(o.chart.legendboxheight);
        // t2j fix8w: Chart.js has no legend background -- spkBg is read by the spkLegendBg page plugin
        if (!o.style.legBgColor.isEmpty())
            sb.append(",spkBg:'").append(escJs(o.style.legBgColor)).append("'");
        // Phase 2-C: leglabels() -- rename legend entries by intra-panel dataset index.
        // Follows Stata convention: graph bar price weight, by(foreign) asyvars showyvars
        // renames the same dataset[0]/dataset[1] labels in every panel identically.
        // Pie/donut labels come from data keys and are NOT renamed here.
        // SKIP when _leglabelsOnXAxis=true: leglabels() already applied to x-axis
        // variable labels (multi-var no-over() aggLabels mode); legend just shows
        // "Mean" and renaming it would be wrong. (v3.4.1)
        // v3.5.34: relabel() always renames legend entries regardless of _leglabelsOnXAxis.
        // It mirrors Stata's over(var, relabel(...)) asyvars showyvars: both x-axis AND
        // legend show the renamed labels. relabel() takes priority over leglabels() for legend.
        //
        // v3.5.34: colorByCategory mode (single-var + over()) produces ONE dataset with
        // per-bar backgroundColor arrays. Chart.js generateLabels returns only ONE item
        // in that case. relabel() must expand that into N per-bar legend items manually,
        // reading backgroundColor[i] from the dataset for each bar color. (v3.5.34)
        List<String> legendRenames = new ArrayList<>();
        if (!o.chart.relabel.isEmpty()) {
            legendRenames = parseCustomLabels(o.chart.relabel);
        // v3.5.36: colorByCategory mode needs generateLabels for per-bar legend items
        // even though _leglabelsOnXAxis=true (x-axis labels were already renamed).
        } else if (!o.chart.legendlabels.isEmpty()
                   && (!o._leglabelsOnXAxis || o._leglabelsColorByCategory)) {
            legendRenames = parseCustomLabels(o.chart.legendlabels);
        }
        if (!legendRenames.isEmpty()) {
            StringBuilder arr = new StringBuilder("[");
            for (String lbl : legendRenames) arr.append("'").append(escJs(lbl)).append("',");
            arr.append("]");
            // colorByCategory = single-var + over(): ONE dataset, per-bar backgroundColor array.
            // In this mode we must build per-bar legend items manually; dflt has only 1 entry.
            // Detect at JS runtime: if dataset 0 has an Array backgroundColor, expand it.
            // (This is safe to run for all modes -- when not colorByCategory, backgroundColor
            //  is a single string and ds.backgroundColor[i] returns undefined -> fallback to dflt.)
            sb.append(",generateLabels:function(chart){")
              .append("var rl=").append(arr).append(";")
              .append("var ds=chart.data.datasets[0];")
              .append("var bg=ds&&Array.isArray(ds.backgroundColor)?ds.backgroundColor:null;")
              .append("var bc=ds&&Array.isArray(ds.borderColor)?ds.borderColor:null;")
              // colorByCategory path: build one item per relabel entry using bar colors
              .append("if(bg&&bg.length>1){")
              .append("return rl.map(function(lbl,i){")
              .append("return {text:lbl,fillStyle:bg[i]||bg[0],")
              .append("strokeStyle:bc?bc[i]||bc[0]:'rgba(0,0,0,0)',")
              .append("lineWidth:1,hidden:false,index:i};});}")
              // normal multi-dataset path: rename existing dflt entries by index
              .append("var dflt=Chart.defaults.plugins.legend.labels.generateLabels(chart);")
              .append("dflt.forEach(function(item,i){if(i<rl.length)item.text=rl[i];});")
              .append("return dflt;}");
        }
        return sb.toString();
    }

    /**
     * Returns the tooltip base styling config string (may be empty).
     * Prepended inside every tooltip:{...} block.
     * Phase 1-B: tooltipBg, tooltipBorder, tooltipFontSize, tooltipPadding.
     * Written ONCE -- all 6 tooltip config methods call this.
     */
    private String tooltipStylePrefix() {
        StringBuilder sb = new StringBuilder();
        if (!o.style.tooltipBg.isEmpty())
            sb.append("backgroundColor:'").append(o.style.tooltipBg).append("',");
        if (!o.style.tooltipBorder.isEmpty())
            sb.append("borderColor:'").append(o.style.tooltipBorder)
              .append("',borderWidth:1,");
        if (!o.style.tooltipFontSize.isEmpty()) {
            String fs = o.style.tooltipFontSize;
            sb.append("titleFont:{size:").append(fs)
              .append("},bodyFont:{size:").append(fs).append("},");
        }
        if (!o.style.tooltipPadding.isEmpty())
            sb.append("padding:").append(o.style.tooltipPadding).append(",");
        return sb.toString();
    }

    /* t2j fix8 (deep-dive r2): the four post-est charts hand-build their tooltip with
     * custom callbacks and previously skipped tooltipStylePrefix(), so tooltipbg/
     * tooltipborder/tooltipfontsize/tooltippadding and tooltipmode() were silently
     * dropped there. This prefix is prepended right after "tooltip:{"; it is empty
     * when no tooltip option is set (so output is unchanged), additive otherwise, and
     * never touches the custom callbacks. */
    private String postEstTooltipPrefix() {
        String p = tooltipStylePrefix();
        if (!o.chart.tooltipmode.isEmpty()) p += "mode:'" + o.chart.tooltipmode + "',";
        return p;
    }

    /* t2j fix8 (deep-dive r2): the multi-series post-est charts (eventStudy,
     * coefPlotMulti, marginsPlot) hand-build their legend labels and skipped
     * legendLabelsCfg(), so legcolor/legbgcolor/legendsize/legendboxheight were
     * dropped there. These keys are APPENDED into the existing labels:{...} object
     * (a duplicate color key, JS last-wins, cleanly applies legcolor over the default
     * labelColor). Leading comma; empty when no option set, so output is unchanged. */
    private String postEstLegendExtra() {
        StringBuilder s = new StringBuilder();
        if (!o.style.legColor.isEmpty())        s.append(",color:'").append(o.style.legColor).append("'");
        if (!o.style.legBgColor.isEmpty())      s.append(",spkBg:'").append(escJs(o.style.legBgColor)).append("'");   // t2j fix8w: drawn by spkLegendBg
        if (!o.chart.legendsize.isEmpty())      s.append(",font:{size:").append(o.chart.legendsize).append("}");
        if (!o.chart.legendboxheight.isEmpty()) s.append(",boxHeight:").append(o.chart.legendboxheight);
        return s.toString();
    }

    /**
     * Returns the axis title config fragment (title:{...},) or empty string.
     * Called by buildAxisConfig() for x and y axis titles.
     * Phase 1-A: xtitleSize/Color and ytitleSize/Color from o.style.
     * Written ONCE -- buildAxisConfig calls this for both axes.
     *
     * @param title  the title string (may be empty -- returns "" if empty)
     * @param isXAxis  true for x-axis, false for y-axis
     */
    private String axisTitleCfg(String title, boolean isXAxis) {
        if (title == null || title.isEmpty()) return "";
        String userColor = isXAxis ? o.style.xtitleColor : o.style.ytitleColor;
        String userSize  = isXAxis ? o.style.xtitleSize  : o.style.ytitleSize;
        String color = userColor.isEmpty() ? labelColor() : userColor;
        StringBuilder sb = new StringBuilder("title:{display:true,text:'")
            .append(escJs(title)).append("',color:'").append(color).append("'");
        if (!userSize.isEmpty())
            sb.append(",font:{size:").append(userSize).append("}");
        sb.append("},");
        return sb.toString();
    }

    /**
     * Returns the axis ticks font/color config fragment (color:...,font:{...}).
     * Inserted into the ticks:{...} block in buildAxisConfig().
     * Phase 1-A: xlabSize/Color and ylabSize/Color from o.style.
     * Written ONCE -- buildAxisConfig calls this for both axes.
     *
     * @param isXAxis  true for x-axis, false for y-axis
     */
    private String axisTickStyleCfg(boolean isXAxis) {
        String userColor = isXAxis ? o.style.xlabColor : o.style.ylabColor;
        String userSize  = isXAxis ? o.style.xlabSize  : o.style.ylabSize;
        String color = userColor.isEmpty() ? labelColor() : userColor;
        StringBuilder sb = new StringBuilder("color:'").append(color).append("'");
        if (!userSize.isEmpty())
            sb.append(",font:{size:").append(userSize).append("}");
        return sb.toString();
    }

    // -- Note / Caption --------------------------------------------------------

    String buildNoteCaption() {
        if (o.note.isEmpty() && o.caption.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("<div class='note-area'>\n");
        if (!o.note.isEmpty())    sb.append("  <p class='note'>Note: ").append(escHtml(o.note)).append("</p>\n");
        if (!o.caption.isEmpty()) sb.append("  <p class='caption'>").append(escHtml(o.caption)).append("</p>\n");
        return sb.append("</div>\n").toString();
    }

    // -- Padding helper --------------------------------------------------------

    String buildPadding() {
        if (o.chart.padding.isEmpty()) return "";
        String[] parts = o.chart.padding.trim().split("\\s+");
        if (parts.length == 1)
            return "    layout:{padding:"+parts[0]+"},\n";
        if (parts.length == 4)
            return "    layout:{padding:{top:"+parts[0]+",right:"+parts[1]+",bottom:"+parts[2]+",left:"+parts[3]+"}},\n";
        return "";
    }

    // =========================================================================
    // ANNOTATION CONFIG (Phase 2-B, v3.5.0)
    // Single chokepoint: buildAnnotationConfig(allowX).
    // Returns complete "annotation:{annotations:{...}}" JS string or "" if
    // nothing to emit. Callers always check isEmpty() before prepending comma.
    //
    // allowX controls whether xline/xband are emitted:
    //   false: bar, hbar, stackedbar*, cibar (categorical x-axis)
    //   true:  line, area, scatter, bubble, ciline, histogram
    //
    // pie/donut: method returns "" immediately (type check at top).
    // boxplot/violin: method returns "" immediately (annotation deferred).
    //
    // CDN: chartjs-plugin-annotation@3.0.1 auto-registers on load --
    //      no Chart.register() call is needed here.
    // =========================================================================

    /**
     * Returns the annotation:{annotations:{...}} JS config string.
     * allowX: whether x-axis annotations (xline, xband) are emitted.
     *         Pass the local isLine boolean for barLine(); false for bar/cibar;
     *         true for scatter/bubble/ciline/histogram.
     * v3.5.0
     */
    /** Overload for non-histogram callers -- no x-axis interpolation needed. */
    String buildAnnotationConfig(boolean allowX) {
        return buildAnnotationConfig(allowX, null);
    }

    /**
     * Build the annotation:{annotations:{...}} plugin config string.
     * histBinEdges: if non-null, the histogram bin edge values as doubles.
     *   User xline/xband numeric values are converted to fractional category-axis
     *   indices via linear interpolation between bin edges. This gives exact
     *   proportional placement matching Stata's continuous-axis xline behaviour.
     *   Chart.js category axis accepts fractional indices for annotation positioning.
     *   (v3.5.1)
     */
    String buildAnnotationConfig(boolean allowX, double[] histBinEdges) {
        {   // s9j: reference lines and bands go in the key with the user's colour and label
            if (!o.axes.yline.isEmpty()) { String[] v = o.axes.yline.split("\\|"); String[] cs = o.axes.ylinecolor.split("\\|"); String[] ls = o.axes.ylinelabel.split("\\|");
                for (int i = 0; i < v.length; i++) { String c = (i < cs.length && !cs[i].trim().isEmpty()) ? cs[i].trim() : "#666666"; String l = (i < ls.length && !ls[i].trim().isEmpty()) ? ls[i].trim() : "y = " + v[i].trim(); key("refline", l, "line", c, "[6,4]", ""); } }
            if (allowX && !o.axes.xline.isEmpty()) { String[] v = o.axes.xline.split("\\|"); String[] cs = o.axes.xlinecolor.split("\\|"); String[] ls = o.axes.xlinelabel.split("\\|");
                for (int i = 0; i < v.length; i++) { String c = (i < cs.length && !cs[i].trim().isEmpty()) ? cs[i].trim() : "#666666"; String l = (i < ls.length && !ls[i].trim().isEmpty()) ? ls[i].trim() : "x = " + v[i].trim(); key("refline", l, "line", c, "[6,4]", ""); } }
            if (!o.axes.yband.isEmpty()) { String[] b = o.axes.yband.split("\\|"); String[] cs = o.axes.ybandcolor.split("\\|"); for (int i = 0; i < b.length; i++) key("band", "Band y: " + b[i].trim(), "swatch", (i < cs.length && !cs[i].trim().isEmpty()) ? cs[i].trim() : "rgba(0,0,0,0.08)", "", ""); }
            if (allowX && !o.axes.xband.isEmpty()) { String[] b = o.axes.xband.split("\\|"); String[] cs = o.axes.xbandcolor.split("\\|"); for (int i = 0; i < b.length; i++) key("band", "Band x: " + b[i].trim(), "swatch", (i < cs.length && !cs[i].trim().isEmpty()) ? cs[i].trim() : "rgba(0,0,0,0.08)", "", ""); }
        }
        // Early exit for chart types that never support annotations
        String t = o.type;
        if (t.equals("pie") || t.equals("donut")
                || t.equals("boxplot") || t.equals("violin")) return "";

        boolean dark = gen.isDark();

        // Default colors for light and dark themes
        String defLineColor  = dark ? "rgba(200,200,200,0.7)"  : "rgba(150,150,150,0.8)";
        String defBandColor  = dark ? "rgba(200,200,200,0.15)" : "rgba(150,150,150,0.12)";

        StringBuilder sb = new StringBuilder();

        // -- yline(s) ----------------------------------------------------------
        if (!o.axes.yline.isEmpty()) {
            String[] vals   = o.axes.yline.split("\\|", -1);
            String[] colors = o.axes.ylinecolor.isEmpty() ? new String[0] : o.axes.ylinecolor.split("\\|", -1);
            String[] labels = o.axes.ylinelabel.isEmpty() ? new String[0] : o.axes.ylinelabel.split("\\|", -1);
            for (int i = 0; i < vals.length; i++) {
                String v = vals[i].trim();
                if (v.isEmpty()) continue;
                String c = cycleColor(colors, i, defLineColor);
                String lbl = (i < labels.length) ? labels[i].trim() : "";
                sb.append("yl").append(i).append(":").append(annotLine("y", v, c, lbl, dark)).append(",\n");
            }
        }

        // -- xline(s) -- only if allowX ----------------------------------------
        if (allowX && !o.axes.xline.isEmpty()) {
            String[] vals   = o.axes.xline.split("\\|", -1);
            String[] colors = o.axes.xlinecolor.isEmpty() ? new String[0] : o.axes.xlinecolor.split("\\|", -1);
            String[] labels = o.axes.xlinelabel.isEmpty() ? new String[0] : o.axes.xlinelabel.split("\\|", -1);
            for (int i = 0; i < vals.length; i++) {
                String v = vals[i].trim();
                if (v.isEmpty()) continue;
                // Convert to fractional category index for histogram (v3.5.1)
                if (histBinEdges != null) {
                    try { v = String.format(Locale.ROOT, "%.6f", binEdgeToIndex(Double.parseDouble(v), histBinEdges)); }
                    catch (NumberFormatException ignore) {}
                }
                String c = cycleColor(colors, i, defLineColor);
                String lbl = (i < labels.length) ? labels[i].trim() : "";
                sb.append("xl").append(i).append(":").append(annotLine("x", v, c, lbl, dark)).append(",\n");
            }
        }

        // -- yband(s) ----------------------------------------------------------
        if (!o.axes.yband.isEmpty()) {
            String[] bands  = o.axes.yband.split("\\|", -1);
            String[] colors = o.axes.ybandcolor.isEmpty() ? new String[0] : o.axes.ybandcolor.split("\\|", -1);
            for (int i = 0; i < bands.length; i++) {
                String band = bands[i].trim();
                if (band.isEmpty()) continue;
                String[] parts = band.trim().split("\\s+");
                if (parts.length < 2) continue;
                String c = cycleColor(colors, i, defBandColor);
                // yband: yMin=parts[0], yMax=parts[1], xMin/xMax=undefined (full width)
                sb.append("yb").append(i).append(":").append(annotBox("y", parts[0], parts[1], c)).append(",\n");
            }
        }

        // -- xband(s) -- only if allowX ----------------------------------------
        if (allowX && !o.axes.xband.isEmpty()) {
            String[] bands  = o.axes.xband.split("\\|", -1);
            String[] colors = o.axes.xbandcolor.isEmpty() ? new String[0] : o.axes.xbandcolor.split("\\|", -1);
            for (int i = 0; i < bands.length; i++) {
                String band = bands[i].trim();
                if (band.isEmpty()) continue;
                String[] parts = band.trim().split("\\s+");
                if (parts.length < 2) continue;
                String c = cycleColor(colors, i, defBandColor);
                // xband: xMin=parts[0], xMax=parts[1], yMin/yMax=undefined (full height)
                // Convert to fractional category index for histogram (v3.5.1)
                String lo, hi;
                if (histBinEdges != null) {
                    try { lo = String.format(Locale.ROOT, "%.6f", binEdgeToIndex(Double.parseDouble(parts[0]), histBinEdges)); }
                    catch (NumberFormatException e) { lo = parts[0]; }
                    try { hi = String.format(Locale.ROOT, "%.6f", binEdgeToIndex(Double.parseDouble(parts[1]), histBinEdges)); }
                    catch (NumberFormatException e) { hi = parts[1]; }
                } else {
                    lo = parts[0]; hi = parts[1];
                }
                sb.append("xb").append(i).append(":").append(annotBox("x", lo, hi, c)).append(",\n");
            }
        }

        // -- apoint(s) -- y x pairs space-separated (scattteri convention) ------
        // apoint(y1 x1 y2 x2 ...) -- allowX does NOT suppress apoint x-coordinate
        if (!o.axes.apoint.isEmpty()) {
            String[] tokens = o.axes.apoint.trim().split("\\s+");
            String[] colors = o.axes.apointcolor.isEmpty() ? new String[0] : o.axes.apointcolor.split("\\|", -1);
            String  radius  = o.axes.apointsize.isEmpty() ? "8" : o.axes.apointsize.trim();
            int pi = 0;
            for (int i = 0; i + 1 < tokens.length; i += 2, pi++) {
                String yv = tokens[i].trim();
                String xv = tokens[i+1].trim();
                if (yv.isEmpty() || xv.isEmpty()) continue;
                String c = cycleColor(colors, pi, "rgba(255,99,132,0.8)");
                sb.append("ap").append(pi).append(":").append(annotPoint(yv, xv, c, radius)).append(",\n");
            }
        }

        // -- alabel(s) ----------------------------------------------------------
        // alabelpos(y1 x1 pos|y2 x2 pos) + alabeltext(text1|text2)
        // pos is optional minute-clock direction (0=center, 15=right, 30=below, 45=left, 60=above).
        // Default pos=3 (right of point) matching Stata mlabpos default.
        // alabelgap controls offset distance px (default 15). (v3.5.2)
        if (!o.axes.alabelpos.isEmpty() && !o.axes.alabeltext.isEmpty()) {
            String[] posPairs = o.axes.alabelpos.split("\\|", -1);
            String[] texts    = o.axes.alabeltext.split("\\|", -1);
            String   fontSize = o.axes.alabelfontsize.isEmpty() ? "12" : o.axes.alabelfontsize.trim();
            double   gap      = 15.0;
            if (!o.axes.alabelgap.isEmpty()) {
                try { gap = Double.parseDouble(o.axes.alabelgap.trim()); } catch (NumberFormatException ignore) {}
            }
            for (int i = 0; i < posPairs.length && i < texts.length; i++) {
                String pair = posPairs[i].trim();
                String txt  = texts[i].trim();
                if (pair.isEmpty() || txt.isEmpty()) continue;
                String[] coords = pair.trim().split("\\s+");
                if (coords.length < 2) continue;
                // Third token: minute-clock position. Default 15 (right) = Stata mlabpos default.
                int minutePos = 15;
                if (coords.length >= 3) {
                    try { minutePos = Integer.parseInt(coords[2].trim()); } catch (NumberFormatException ignore) {}
                }
                // Compute xAdjust/yAdjust from minute-clock position and gap.
                // pos=0: center on point (Stata mlabpos(0)), gap ignored.
                // pos 1-59: angle = (pos/60)*2*PI clockwise from 12.
                //   xAdjust = gap * sin(angle), yAdjust = -gap * cos(angle)
                int xAdjust = 0, yAdjust = 0;
                if (minutePos != 0) {
                    double angle = (minutePos / 60.0) * 2.0 * Math.PI;
                    xAdjust = (int) Math.round(gap * Math.sin(angle));
                    yAdjust = (int) Math.round(-gap * Math.cos(angle));
                }
                sb.append("lb").append(i).append(":").append(
                    annotLabel(coords[0].trim(), coords[1].trim(), txt, fontSize, xAdjust, yAdjust, dark)
                ).append(",\n");
            }
        }

        // -- aellipse(s) --------------------------------------------------------
        // aellipse(ymin xmin ymax xmax|...) -- 4 values per ellipse
        if (!o.axes.aellipse.isEmpty()) {
            String[] ellipses = o.axes.aellipse.split("\\|", -1);
            String[] fillColors   = o.axes.aellipsecolor.isEmpty() ? new String[0] : o.axes.aellipsecolor.split("\\|", -1);
            String[] borderColors = o.axes.aellipseborder.isEmpty() ? new String[0] : o.axes.aellipseborder.split("\\|", -1);
            for (int i = 0; i < ellipses.length; i++) {
                String quad = ellipses[i].trim();
                if (quad.isEmpty()) continue;
                String[] parts = quad.trim().split("\\s+");
                if (parts.length < 4) continue;
                String fc = cycleColor(fillColors, i, "rgba(99,132,255,0.15)");
                String bc = cycleColor(borderColors, i, "rgba(99,132,255,0.6)");
                sb.append("ae").append(i).append(":").append(annotEllipse(parts[0], parts[1], parts[2], parts[3], fc, bc)).append(",\n");
            }
        }

        // Nothing to emit
        if (sb.length() == 0) return "";
        return "annotation:{annotations:{\n" + sb.toString() + "}}";
    }

    // -------------------------------------------------------------------------
    // Annotation sub-emitters (private static, pure functions, no state)
    // -------------------------------------------------------------------------

    /**
     * Emits a Chart.js annotation line on the given axis.
     * axis: "x" or "y"
     * value: numeric string
     * color: CSS color
     * label: text label (empty = no label)
     * dark: theme for label colors
     * v3.5.0
     */
    /**
     * buildPexlinePlugin -- inline canvas plugin for pexline() on coefplot/eventstudy. (v3.6.0-s7a)
     *
     * AXIS STRUCTURE (determined by examining cpZero, which is always correct):
     *
     *   type(coefplot) single-model  : indexAxis:'y'  -> x = value axis (numeric)
     *                                  cpZero draws on scales.x (vertical line)
     *                                  isHorizontal = true
     *
     *   type(coefplot) multi-model   : no indexAxis   -> x = category positions (0,1,2...)
     *                                  y = value axis (coefficient magnitudes)
     *                                  cpZero draws on scales.y (horizontal line)
     *                                  isHorizontal = false
     *
     *   type(eventstudy)             : no indexAxis   -> x = category (coef names)
     *                                  y = value axis (coefficient magnitudes)
     *                                  cpZero draws on scales.y (horizontal line)
     *                                  isHorizontal = false
     *
     * RULE: pexline always mirrors cpZero:
     *   isHorizontal=true  -> draw VERTICAL line on scales.x at x=pexVal
     *   isHorizontal=false -> draw HORIZONTAL line on scales.y at y=pexVal
     */
    private String buildPexlinePlugin(boolean isHorizontal, String pexVal, String color, boolean dark) {
        if (pexVal == null || pexVal.isEmpty() || pexVal.equalsIgnoreCase("none")) return "";
        String safeVal = pexVal.trim().replaceAll("[^0-9.\\-]", "");
        if (safeVal.isEmpty()) return "";
        if (isHorizontal) {
            // coefplot single-model: indexAxis:'y', value axis = scales.x
            // Draw a vertical line at x = pexVal (same axis as cpZero)
            return "{id:'cpPexline',beforeDatasetsDraw:function(chart){"
                + "var ctx=chart.ctx,xS=chart.scales.x,a=chart.chartArea;"
                + "if(!xS||!a)return;"
                + "var px=xS.getPixelForValue(" + safeVal + ");"
                + "if(px<a.left||px>a.right)return;"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.beginPath();ctx.setLineDash([5,4]);"
                + "ctx.strokeStyle='" + color + "';ctx.lineWidth=1.5;"
                + "ctx.moveTo(px,a.top);ctx.lineTo(px,a.bottom);ctx.stroke();"
                + "ctx.setLineDash([]);ctx.restore();}}";
        } else {
            // coefplot multi-model + eventstudy: value axis = scales.y
            // Draw a horizontal line at y = pexVal (same axis as cpZero)
            return "{id:'cpPexline',beforeDatasetsDraw:function(chart){"
                + "var ctx=chart.ctx,yS=chart.scales.y,a=chart.chartArea;"
                + "if(!yS||!a)return;"
                + "var px=yS.getPixelForValue(" + safeVal + ");"
                + "if(px<a.top||px>a.bottom)return;"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.beginPath();ctx.setLineDash([5,4]);"
                + "ctx.strokeStyle='" + color + "';ctx.lineWidth=1.5;"
                + "ctx.moveTo(a.left,px);ctx.lineTo(a.right,px);ctx.stroke();"
                + "ctx.setLineDash([]);ctx.restore();}}";
        }
    }

    private static String annotLine(String axis, String value, String color, String label, boolean dark) {
        String labelColor = dark ? "rgba(255,255,255,0.85)" : "rgba(0,0,0,0.75)";
        String labelBg    = dark ? "rgba(50,50,50,0.85)"   : "rgba(255,255,255,0.85)";
        StringBuilder sb = new StringBuilder("{type:'line',");
        sb.append(axis).append("Min:").append(value).append(",");
        sb.append(axis).append("Max:").append(value).append(",");
        sb.append("borderColor:'").append(color).append("',");
        sb.append("borderWidth:1.5");
        if (!label.isEmpty()) {
            sb.append(",label:{content:'").append(label.replace("'","\\'")).append("',");
            sb.append("display:true,");
            sb.append("color:'").append(labelColor).append("',");
            sb.append("backgroundColor:'").append(labelBg).append("',");
            sb.append("padding:4,font:{size:11}}");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Emits a Chart.js annotation box as a band.
     * axis: "y" for horizontal band, "x" for vertical band.
     * lo, hi: band extent on the given axis.
     * color: fill color.
     * v3.5.0
     */
    private static String annotBox(String axis, String lo, String hi, String color) {
        StringBuilder sb = new StringBuilder("{type:'box',");
        if (axis.equals("y")) {
            sb.append("yMin:").append(lo).append(",");
            sb.append("yMax:").append(hi).append(",");
        } else {
            sb.append("xMin:").append(lo).append(",");
            sb.append("xMax:").append(hi).append(",");
        }
        sb.append("backgroundColor:'").append(color).append("',");
        sb.append("borderWidth:0}");
        return sb.toString();
    }

    /**
     * Emits a Chart.js annotation point.
     * yVal, xVal: coordinates.
     * color: fill color.
     * radius: circle radius px.
     * v3.5.0
     */
    private static String annotPoint(String yVal, String xVal, String color, String radius) {
        return "{type:'point',"
            + "xValue:" + xVal + ","
            + "yValue:" + yVal + ","
            + "backgroundColor:'" + color + "',"
            + "radius:" + radius + ","
            + "borderWidth:0}";
    }

    /**
     * Emits a Chart.js annotation label at the given coordinates.
     * yVal, xVal: anchor point.
     * text: label text.
     * fontSize: px.
     * dark: for theme-aware colors.
     * v3.5.0
     */
    /**
     * Emits a Chart.js annotation label at (xVal, yVal) with minute-clock positioning.
     * xAdjust/yAdjust: pixel offsets computed from minute-clock direction and gap.
     *   pos=0 -> both 0 (centered on point, Stata mlabpos(0) equivalent).
     *   pos=15 -> xAdjust=+gap, yAdjust=0 (right, Stata mlabpos default).
     * v3.5.2
     */
    private static String annotLabel(String yVal, String xVal, String text,
                                      String fontSize, int xAdjust, int yAdjust, boolean dark) {
        String fc  = dark ? "rgba(255,255,255,0.9)"  : "rgba(0,0,0,0.75)";
        String bgc = dark ? "rgba(50,50,50,0.85)"    : "rgba(255,255,255,0.85)";
        return "{type:'label',"
            + "xValue:" + xVal + ","
            + "yValue:" + yVal + ","
            + "xAdjust:" + xAdjust + ","
            + "yAdjust:" + yAdjust + ","
            + "content:'" + text.replace("'","\\'") + "',"
            + "color:'" + fc + "',"
            + "backgroundColor:'" + bgc + "',"
            + "padding:4,"
            + "font:{size:" + fontSize + "}}";
    }

    /**
     * Emits a Chart.js annotation ellipse.
     * yMin, xMin, yMax, xMax: bounding box corners.
     * fillColor, borderColor: CSS colors.
     * v3.5.0
     */
    private static String annotEllipse(String yMin, String xMin, String yMax, String xMax,
                                        String fillColor, String borderColor) {
        return "{type:'ellipse',"
            + "xMin:" + xMin + ","
            + "xMax:" + xMax + ","
            + "yMin:" + yMin + ","
            + "yMax:" + yMax + ","
            + "backgroundColor:'" + fillColor + "',"
            + "borderColor:'" + borderColor + "',"
            + "borderWidth:1.5}";
    }

    /**
     * Returns the color at index i from a list, cycling if needed.
     * Returns defaultColor if the list is empty or entry at i is blank.
     * v3.5.0
     */
    private static String cycleColor(String[] colors, int idx, String defaultColor) {
        if (colors == null || colors.length == 0) return defaultColor;
        String c = colors[idx % colors.length].trim();
        return c.isEmpty() ? defaultColor : c;
    }

    /**
     * Snap a numeric string value to the nearest label in a histogram category axis.
     * Chart.js annotation on a category axis requires the value to be the exact
     * label string (or a 0-based index). We parse the numeric value and find the
     * closest bin-edge label string to use as the annotation position.
     * (v3.5.2: histogram x-axis fix for xline/xband annotations)
     */
    /**
     * Convert a data-space numeric value to a fractional category-axis index
     * using linear interpolation between histogram bin edges.
     * e.g. xline(15000) on bins [14329, 15906] -> index 7.426
     * Chart.js annotation accepts fractional indices on category axes. (v3.5.1)
     */
    private static double binEdgeToIndex(double val, double[] edges) {
        if (edges == null || edges.length == 0) return 0.0;
        if (val <= edges[0])                   return 0.0;
        if (val >= edges[edges.length - 1])    return edges.length - 1.0;
        for (int i = 0; i < edges.length - 1; i++) {
            if (val >= edges[i] && val <= edges[i + 1]) {
                double frac = (val - edges[i]) / (edges[i + 1] - edges[i]);
                return i + frac;
            }
        }
        return edges.length - 1.0;
    }


    // New design: sortable columns, sparkline with hollow IQR box,
    // median column, CV badge, chip-style column toggles.


    // -- Custom Violin (v2.5.0) -----------------------------------------------
    // No external plugin. KDE computed in Java, rendered entirely via canvas
    // afterDraw plugin. Boxplot elements (IQR box + whiskers + median + mean)
    // drawn on top of each violin shape. Full color control, no plugin surprises.

    /**
     * Renders a custom violin chart using a pure Chart.js scatter base
     * plus a canvas afterDraw plugin that draws all violin shapes.
     *
     * The Chart.js chart itself is type:'scatter' with one invisible dataset
     * per group (single {x:xIdx, y:median} point) so Chart.js auto-scales
     * the axes from the data range. The _violinPlugin afterDraw plugin draws
     * the actual violin shapes, IQR box, whiskers, median diamond, mean dot.
     *
     * Horizontal violin (hviolin): value on X, categories on Y.
     * All canvas drawing is symmetrically swapped for horizontal mode.
     *
     * v2.5.0
     */
    String violinChart(String id, DataSet data) {
        String violinDataJs = violinData(data);
        String labels       = violinLabels(data);
        // t2j fix6: bake the user's bandwidth() into the violin filter-recompute so the KDE
        // keeps the chosen bandwidth after a filter/slider change. The build path already
        // passes it; the client _vFilter used to call computeKde(s,0,50) with a literal 0,
        // so bandwidth() silently reverted to the auto rule on the first interaction.
        String _bwKdeJs = "0";
        try { double _bw = Double.parseDouble(o.stats.bandwidth.trim()); if (_bw > 0) _bwKdeJs = String.valueOf(_bw); }
        catch (Exception ignore) {}

        String aspectCfg  = o.chart.aspect.isEmpty() ? "" : "aspectRatio:" + o.chart.aspect + ",";
        String paddingCfg = buildPadding();
        String animDur    = animDuration();
        String lc         = labelColor();
        String gc         = gridCssColor();

        // Axis config -- value axis is Y (vertical) or X (horizontal)
        // v3.5.9: fall back to variable label/name when xtitle()/ytitle() not supplied.
        List<Variable> _vlNv = data.getNumericVariables();
        Variable _vlOv = data.getOverVariable();
        String _vlValTitle = !_vlNv.isEmpty() ? _vlNv.get(0).getDisplayName() : "";
        String _vlCatTitle = _vlOv != null ? _vlOv.getDisplayName() : "";
        String _vlXTitle = !o.axes.xtitle.isEmpty() ? o.axes.xtitle
            : (o.chart.horizontal ? _vlValTitle : _vlCatTitle);
        String _vlYTitle = !o.axes.ytitle.isEmpty() ? o.axes.ytitle
            : (o.chart.horizontal ? _vlCatTitle : _vlValTitle);
        String xScaleCfg = o.chart.horizontal ? buildBoxValueAxisConfig("x",  _vlXTitle)
                                        : buildBoxCategoryAxisConfig("x", _vlXTitle,
                                            _vlOv != null ? DataSet.uniqueValues(_vlOv, o.chart.sortgroups, o.showmissingOver).size() : _vlNv.size());
        String yScaleCfg = o.chart.horizontal ? buildBoxCategoryAxisConfig("y", _vlYTitle,
                                            _vlOv != null ? DataSet.uniqueValues(_vlOv, o.chart.sortgroups, o.showmissingOver).size() : _vlNv.size())
                                        : buildBoxValueAxisConfig("y",  _vlYTitle);

        // Invisible scatter datasets: one per violin entry, single anchor point
        // so Chart.js knows the y-range for axis scaling. Points are rendered
        // at size 0 -- the violin plugin draws everything itself.
        // For horizontal: x=value, y=category index (Chart.js scatter numeric).
        // We use the pre-existing category axis + a dummy numeric axis trick:
        // actually for simplicity we use type:'bar' with display:false datasets.
        // Cleaner: emit one scatter point per group at (xIdx, median).
        // The scatter x-axis would need to be category -- but scatter only
        // supports numeric x. Solution: use type:'bar' with empty data and
        // override scales, OR use a custom x tick callback with category labels.
        // Best approach: use type:'bar' (horizontal: indexAxis:'y') with one
        // dataset of all-zero bars (display:false) so axis labels appear, then
        // override the y-scale to be linear with explicit min/max from _violinData.
        // The afterDraw plugin gets chartArea + scale pixel mapping from getPixelForValue().
        // v2.5.0: use bar chart base so category axis is automatic.

        // Build anchor datasets for axis: one bar dataset with null data to register labels.
        // The bars are fully transparent -- only used for axis label positioning.
        StringBuilder anchorDs = new StringBuilder();
        anchorDs.append("{label:'_v',data:[");
        // Count labels
        int nCats = 0;
        for (String lbl : labels.split(",")) if (!lbl.trim().isEmpty()) nCats++;
        for (int i = 0; i < nCats; i++) anchorDs.append("0,");
        anchorDs.append("],backgroundColor:'rgba(0,0,0,0)',borderColor:'rgba(0,0,0,0)',borderWidth:0}");

        String isDarkJs = gen.isDark() ? "true" : "false";
        String isHorizJs = o.chart.horizontal ? "true" : "false";
        String indexAxisCfg = o.chart.horizontal ? "indexAxis:'y'," : "";

        // Custom violin afterDraw plugin (pure canvas).
        // _vd = _violinData array. For each entry:
        //   1. Get pixel center from category scale
        //   2. Compute halfWidth from chart area / nGroups * 0.38
        //   3. Draw KDE polygon (mirrored, closed path)
        //   4. Draw whisker line
        //   5. Draw IQR box (30% of halfWidth)
        //   6. Draw median diamond
        //   7. Draw mean dot
        // Horizontal mode: all x/y pixel roles are swapped.
        String violinPlugin =
            "var _vIsHoriz=" + isHorizJs + ";\n"
          + "var _vIsDark=" + isDarkJs + ";\n"
          + "var _vTooltipEl=null;\n"
          + "var _vPlugin={\n"
          + "  id:'customViolin',\n"
          + "  afterDraw:function(chart){\n"
          + "    var ca=chart.chartArea;\n"
          + "    if(!ca||!_violinData||!_violinData.length)return;\n"
          + "    var ctx=chart.ctx;\n"
          + "    var n=_violinData.length;\n"
          // Get the category scale and value scale
          + "    var catScale=_vIsHoriz?chart.scales.y:chart.scales.x;\n"
          + "    var valScale=_vIsHoriz?chart.scales.x:chart.scales.y;\n"
          + "    if(!catScale||!valScale)return;\n"
          // halfWidth in pixels: split available space per category, use 38%
          + "    var catSpan=_vIsHoriz?(ca.bottom-ca.top):(ca.right-ca.left);\n"
          + "    var nCats=catScale.ticks?catScale.ticks.length:n;\n"
          + "    if(nCats<1)nCats=1;\n"
          + "    var halfW=Math.min((catSpan/nCats)*0.38, 60);\n"
          + "    var iqrW=halfW*0.28;\n"
          // Draw each violin
          + "    _violinData.forEach(function(vd,i){\n"
          + "      if(!vd.kde||vd.kde.length===0||vd.n===0)return;\n"
          // Pixel center on category axis
          + "      var cx=_vIsHoriz?null:catScale.getPixelForValue(vd.xIdx);\n"
          + "      var cy=_vIsHoriz?catScale.getPixelForValue(vd.xIdx):null;\n"
          // Draw KDE shape
          + "      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n"
          + "      ctx.beginPath();\n"
          + "      var first=true;\n"
          // Right half (positive x-offset from center)
          + "      vd.kde.forEach(function(pt){\n"
          + "        var pv=valScale.getPixelForValue(pt[0]);\n"
          + "        var off=pt[1]*halfW;\n"
          + "        var px,py;\n"
          + "        if(_vIsHoriz){px=pv;py=cy-off;}else{px=cx+off;py=pv;}\n"
          + "        if(first){ctx.moveTo(px,py);first=false;}else{ctx.lineTo(px,py);}\n"
          + "      });\n"
          // Left half (mirrored -- traverse kde in reverse)
          + "      for(var ki=vd.kde.length-1;ki>=0;ki--){\n"
          + "        var pt2=vd.kde[ki];\n"
          + "        var pv2=valScale.getPixelForValue(pt2[0]);\n"
          + "        var off2=pt2[1]*halfW;\n"
          + "        var px2,py2;\n"
          + "        if(_vIsHoriz){px2=pv2;py2=cy+off2;}else{px2=cx-off2;py2=pv2;}\n"
          + "        ctx.lineTo(px2,py2);\n"
          + "      }\n"
          + "      ctx.closePath();\n"
          + "      ctx.fillStyle=vd.color;\n"
          + "      ctx.fill();\n"
          + "      ctx.strokeStyle=vd.borderColor;\n"
          + "      ctx.lineWidth=1.5;\n"
          + "      ctx.stroke();\n"
          + "      ctx.restore();\n"
          // Whisker line (thin, from whiskerLo to whiskerHi through center)
          + "      if(vd.whiskerLo!=null&&vd.whiskerHi!=null){\n"
          + "        ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n"
          + "        ctx.strokeStyle=_vIsDark?'rgba(255,255,255,0.5)':'rgba(0,0,0,0.35)';\n"
          + "        ctx.lineWidth=1.5;\n"
          + "        ctx.setLineDash([4,3]);\n"
          + "        ctx.beginPath();\n"
          + "        var wLoPx=valScale.getPixelForValue(vd.whiskerLo);\n"
          + "        var wHiPx=valScale.getPixelForValue(vd.whiskerHi);\n"
          + "        if(_vIsHoriz){\n"
          + "          ctx.moveTo(wLoPx,cy);ctx.lineTo(wHiPx,cy);\n"
          + "        }else{\n"
          + "          ctx.moveTo(cx,wLoPx);ctx.lineTo(cx,wHiPx);\n"
          + "        }\n"
          + "        ctx.stroke();\n"
          // Whisker end caps
          + "        ctx.setLineDash([]);\n"
          + "        ctx.lineWidth=2;\n"
          + "        var capOff=iqrW*0.5;\n"
          + "        if(_vIsHoriz){\n"
          + "          ctx.beginPath();ctx.moveTo(wLoPx,cy-capOff);ctx.lineTo(wLoPx,cy+capOff);ctx.stroke();\n"
          + "          ctx.beginPath();ctx.moveTo(wHiPx,cy-capOff);ctx.lineTo(wHiPx,cy+capOff);ctx.stroke();\n"
          + "        }else{\n"
          + "          ctx.beginPath();ctx.moveTo(cx-capOff,wLoPx);ctx.lineTo(cx+capOff,wLoPx);ctx.stroke();\n"
          + "          ctx.beginPath();ctx.moveTo(cx-capOff,wHiPx);ctx.lineTo(cx+capOff,wHiPx);ctx.stroke();\n"
          + "        }\n"
          + "        ctx.restore();\n"
          + "      }\n"
          // IQR box (filled rectangle Q1->Q3, width=iqrW*2)
          + "      if(vd.q1!=null&&vd.q3!=null){\n"
          + "        ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n"
          + "        var q1px=valScale.getPixelForValue(vd.q1);\n"
          + "        var q3px=valScale.getPixelForValue(vd.q3);\n"
          // t2j fix8t (user-reported: the box inside the violin was hard to read -- a 1.5 px
          // 40%-black hairline over the fill). Solid dark outline at 2 px, slightly stronger
          // fill; spkSnap lands the edges on whole device pixels.
          + "        ctx.fillStyle=_vIsDark?'rgba(255,255,255,0.25)':'rgba(0,0,0,0.22)';\n"
          + "        ctx.strokeStyle=_vIsDark?'rgba(255,255,255,0.85)':'rgba(0,0,0,0.75)';\n"
          + "        ctx.lineWidth=2;\n"
          + "        if(_vIsHoriz){\n"
          + "          var bx=Math.min(q1px,q3px),bw=Math.abs(q3px-q1px);\n"
          + "          ctx.fillRect(bx,cy-iqrW,bw,iqrW*2);\n"
          + "          ctx.strokeRect(bx,cy-iqrW,bw,iqrW*2);\n"
          + "        }else{\n"
          + "          var by=Math.min(q1px,q3px),bh=Math.abs(q3px-q1px);\n"
          + "          ctx.fillRect(cx-iqrW,by,iqrW*2,bh);\n"
          + "          ctx.strokeRect(cx-iqrW,by,iqrW*2,bh);\n"
          + "        }\n"
          + "        ctx.restore();\n"
          + "      }\n"
          // Median diamond
          + "      if(vd.median!=null){\n"
          + "        ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n"
          + "        var medPx=valScale.getPixelForValue(vd.median);\n"
          + "        var dm=6;\n"
          + "        ctx.fillStyle=vd.medianColor;\n"
          + "        ctx.beginPath();\n"
          + "        if(_vIsHoriz){\n"
          + "          ctx.moveTo(medPx,cy-dm);ctx.lineTo(medPx+dm,cy);\n"
          + "          ctx.lineTo(medPx,cy+dm);ctx.lineTo(medPx-dm,cy);\n"
          + "        }else{\n"
          + "          ctx.moveTo(cx,medPx-dm);ctx.lineTo(cx+dm,medPx);\n"
          + "          ctx.lineTo(cx,medPx+dm);ctx.lineTo(cx-dm,medPx);\n"
          + "        }\n"
          + "        ctx.closePath();ctx.fill();\n"
          + "        ctx.restore();\n"
          + "      }\n"
          // Mean dot
          + "      if(vd.mean!=null){\n"
          + "        ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n"
          + "        var mnPx=valScale.getPixelForValue(vd.mean);\n"
          + "        ctx.fillStyle=vd.meanColor;\n"
          + "        ctx.strokeStyle=_vIsDark?'rgba(255,255,255,0.7)':'rgba(0,0,0,0.4)';\n"
          + "        ctx.lineWidth=1.5;\n"
          + "        ctx.beginPath();\n"
          + "        if(_vIsHoriz){ctx.arc(mnPx,cy,5,0,Math.PI*2);}\n"
          + "        else{ctx.arc(cx,mnPx,5,0,Math.PI*2);}\n"
          + "        ctx.fill();ctx.stroke();\n"
          + "        ctx.restore();\n"
          + "      }\n"
          + "    });\n"
          + "  }\n"
          + "};\n";

        // Custom tooltip: HTML div shown on mousemove over a violin region.
        // On mousemove: find the closest violin whose x-center is within halfW pixels
        // and whose value range [min,max] covers the cursor y value.
        String tooltipJs =
            "document.getElementById('" + id + "').addEventListener('mousemove',function(e){\n"
          + "  var chart=Chart.getChart('" + id + "');\n"
          + "  if(!chart)return;\n"
          + "  var ca=chart.chartArea;\n"
          + "  if(!ca)return;\n"
          + "  var rect=e.target.getBoundingClientRect();\n"
          + "  var mx=e.clientX-rect.left,my=e.clientY-rect.top;\n"
          + "  var catScale=_vIsHoriz?chart.scales.y:chart.scales.x;\n"
          + "  var valScale=_vIsHoriz?chart.scales.x:chart.scales.y;\n"
          + "  var catSpan=_vIsHoriz?(ca.bottom-ca.top):(ca.right-ca.left);\n"
          + "  var nCats=catScale.ticks?catScale.ticks.length:_violinData.length;\n"
          + "  if(nCats<1)nCats=1;\n"
          + "  var halfW=Math.min((catSpan/nCats)*0.38,60);\n"
          + "  var hit=null;\n"
          + "  _violinData.forEach(function(vd){\n"
          + "    if(vd.n===0)return;\n"
          + "    var ctr=catScale.getPixelForValue(vd.xIdx);\n"
          + "    var dist=_vIsHoriz?Math.abs(my-ctr):Math.abs(mx-ctr);\n"
          + "    if(dist>halfW)return;\n"
          // t2j fix8r (decision 4): the value-range test was +-1 DATA unit around [min,max], so a
          // 1-obs violin (min==max) had a hit band 2 units wide -- sub-pixel on any real axis.
          // Test in PIXELS instead: the band is [min,max] widened by 6 px each side, so every
          // violin, however thin, has a >= 12 px target.
          + "    var pA=valScale.getPixelForValue(vd.min),pB=valScale.getPixelForValue(vd.max);\n"
          + "    var pLo=Math.min(pA,pB)-6,pHi=Math.max(pA,pB)+6;\n"
          + "    var cp=_vIsHoriz?mx:my;\n"
          + "    if(cp<pLo||cp>pHi)return;\n"
          + "    hit=vd;\n"
          + "  });\n"
          + "  if(!_vTooltipEl){\n"
          + "    _vTooltipEl=document.createElement('div');\n"
          + "    _vTooltipEl.style.cssText='position:fixed;background:rgba(0,0,0,0.82);color:#fff;"
          + "font:12px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;"
          + "padding:8px 12px;border-radius:6px;pointer-events:none;z-index:9999;"
          + "line-height:1.6;white-space:nowrap;display:none;';\n"
          + "    document.body.appendChild(_vTooltipEl);\n"
          + "  }\n"
          + "  if(!hit){\n"
          + "    _vTooltipEl.style.display='none';\n"
          + "    return;\n"
          + "  }\n"
          + "  var f=function(v){return v==null?'--':(Math.round(v*100)/100).toLocaleString();};\n"
          + "  _vTooltipEl.innerHTML="
          + "    '<b>'+hit.label+'</b><br>'"
          + "    +'Median: '+f(hit.median)+'<br>'"
          + "    +'Mean: &nbsp;&nbsp;'+f(hit.mean)+'<br>'"
          + "    +'Q1: &nbsp;&nbsp;&nbsp;&nbsp;'+f(hit.q1)+'<br>'"
          + "    +'Q3: &nbsp;&nbsp;&nbsp;&nbsp;'+f(hit.q3)+'<br>'"
          + "    +'Whisker Lo: '+f(hit.whiskerLo)+'<br>'"
          + "    +'Whisker Hi: '+f(hit.whiskerHi)+'<br>'"
          + "    +'N: &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;'+hit.n;\n"
          + "  _vTooltipEl.style.display='block';\n"
          + "  _vTooltipEl.style.left=(e.clientX+14)+'px';\n"
          + "  _vTooltipEl.style.top=(e.clientY-10)+'px';\n"
          + "});\n"
          + "document.getElementById('" + id + "').addEventListener('mouseleave',function(){\n"
          + "  if(_vTooltipEl)_vTooltipEl.style.display='none';\n"
          + "});\n";

        // Legend plugin -- same _bpLegendPlugin pattern, violin items:
        // Median (diamond), Mean (dot), IQR Box (rect), Whiskers (whisker), KDE Shape (rect)
        // Color sourced from _violinData[0] for representative swatches.
        String legendPlugin =
            "var _vLegendPlugin={\n"
          + "  id:'vInlineLegend',\n"
          + "  afterDraw:function(chart){\n"
          + "    var ctx=chart.ctx;\n"
          + "    var ca=chart.chartArea;\n"
          + "    if(!ca||!_violinData||!_violinData.length)return;\n"
          + "    var vd0=_violinData[0];\n"
          + "    if(!vd0)return;\n"
          + "    var shapeColor=vd0.color||'rgba(78,121,167,0.85)';\n"
          + "    var medColor=vd0.medianColor||'#000000';\n"
          + "    var meanColor=vd0.meanColor||'#000000';\n"
          + "    var iqrColor=_vIsDark?'rgba(255,255,255,0.18)':'rgba(0,0,0,0.15)';\n"
          + "    var wColor=_vIsDark?'rgba(255,255,255,0.5)':'rgba(0,0,0,0.35)';\n"
          + "    var items=[\n"
          + "      {label:'Median',type:'diamond',color:medColor},\n"
          + "      {label:'Mean',type:'dot',color:meanColor},\n"
          + "      {label:'IQR Box',type:'rect',color:iqrColor,border:wColor},\n"
          + "      {label:'Whiskers',type:'whisker',color:wColor},\n"
          + "      {label:'KDE Shape',type:'rect',color:shapeColor,border:vd0.borderColor}\n"
          + "    ];\n"
          + "    var fs=11,lh=18,pw=14,gap=6,padX=10,padY=8,bw=0;\n"
          + "    ctx.font=fs+'px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif';\n"
          + "    items.forEach(function(it){var tw=ctx.measureText(it.label).width;if(tw+pw+gap>bw)bw=tw+pw+gap;});\n"
          + "    bw+=padX*2;\n"
          + "    var bh=items.length*lh+padY*2;\n"
          + "    var bx=ca.right-bw-8,by=ca.top+8;\n"
          + "    ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n"
          + "    ctx.fillStyle='rgba(0,0,0,0.55)';\n"
          + "    ctx.strokeStyle='rgba(255,255,255,0.25)';\n"
          + "    ctx.lineWidth=1;\n"
          + "    var r=6;ctx.beginPath();\n"
          + "    ctx.moveTo(bx+r,by);ctx.lineTo(bx+bw-r,by);ctx.arcTo(bx+bw,by,bx+bw,by+r,r);\n"
          + "    ctx.lineTo(bx+bw,by+bh-r);ctx.arcTo(bx+bw,by+bh,bx+bw-r,by+bh,r);\n"
          + "    ctx.lineTo(bx+r,by+bh);ctx.arcTo(bx,by+bh,bx,by+bh-r,r);\n"
          + "    ctx.lineTo(bx,by+r);ctx.arcTo(bx,by,bx+r,by,r);ctx.closePath();\n"
          + "    ctx.fill();ctx.stroke();\n"
          + "    items.forEach(function(it,i){\n"
          + "      var ix=bx+padX,iy=by+padY+i*lh+lh/2;\n"
          + "      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n"
          + "      if(it.type==='diamond'){ctx.fillStyle=it.color;ctx.beginPath();var dm=6;ctx.moveTo(ix+pw/2,iy-dm);ctx.lineTo(ix+pw/2+dm,iy);ctx.lineTo(ix+pw/2,iy+dm);ctx.lineTo(ix+pw/2-dm,iy);ctx.closePath();ctx.fill();}\n"
          + "      else if(it.type==='dot'){ctx.fillStyle=it.color;ctx.strokeStyle=_vIsDark?'rgba(255,255,255,0.7)':'rgba(0,0,0,0.4)';ctx.lineWidth=1.5;ctx.beginPath();ctx.arc(ix+pw/2,iy,4,0,Math.PI*2);ctx.fill();ctx.stroke();}\n"
          + "      else if(it.type==='rect'){ctx.fillStyle=it.color;ctx.strokeStyle=it.border||'rgba(255,255,255,0.6)';ctx.lineWidth=1.2;ctx.fillRect(ix,iy-5,pw,10);ctx.strokeRect(ix,iy-5,pw,10);}\n"
          + "      else if(it.type==='whisker'){ctx.strokeStyle=it.color;ctx.lineWidth=1.8;ctx.setLineDash([3,2]);ctx.beginPath();ctx.moveTo(ix+pw/2,iy-6);ctx.lineTo(ix+pw/2,iy+6);ctx.stroke();ctx.setLineDash([]);ctx.lineWidth=1.5;ctx.beginPath();ctx.moveTo(ix+2,iy-6);ctx.lineTo(ix+pw-2,iy-6);ctx.stroke();ctx.beginPath();ctx.moveTo(ix+2,iy+6);ctx.lineTo(ix+pw-2,iy+6);ctx.stroke();}\n"
          + "      ctx.restore();\n"
          + "      ctx.fillStyle='#ffffff';\n"
          + "      ctx.font=fs+'px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif';\n"
          + "      ctx.textBaseline='middle';\n"
          + "      ctx.fillText(it.label,ix+pw+gap,iy);\n"
          + "    });\n"
          + "    ctx.restore();\n"
          + "  }\n"
          + "};\n";

        // ----------------------------------------------------------------
        // Violin animation engine (v2.5.1)
        // ----------------------------------------------------------------
        // Two animation modes:
        //
        // (A) GROW-IN on initial load:
        //     _vOrigData holds the target _violinData. On first render we
        //     start all KDE estimates at 0 (flat line) and tween the width
        //     multiplier _vAnimT from 0 -> 1 over animDur ms.
        //     Stats (median/mean/q1/q3/whiskers) are held at their real
        //     values throughout -- only the KDE width grows.
        //
        // (B) TWEEN on filter change (_vAnimateTo):
        //     Stores old and new _violinData arrays. Each frame interpolates
        //     every KDE estimate AND every stat value (median/mean/q1/q3/
        //     whiskerLo/whiskerHi) using easeOutCubic.
        //     When old and new have different group counts (e.g. "Domestic"
        //     has only rep78=1,2,3 vs "All" has 1-5) the missing groups
        //     are represented as n=0 entries with all stats = the new value
        //     so they grow in from zero width rather than morphing from garbage.
        //
        // easeOutCubic: t -> 1-(1-t)^3  (matches Chart.js default 'easeOutQuart'
        // closely enough; avoids needing to reference Chart.js internals).
        // ----------------------------------------------------------------
        String animationEngine =
            // Raw (target) violin data -- never mutated
            "var _vOrigData=_violinData.slice();\n"
            // Animation state
            + "var _vAnimT=0;\n"           // 0..1 tween progress
            + "var _vAnimFrom=null;\n"     // source _violinData snapshot for tween
            + "var _vAnimTo=null;\n"       // destination snapshot for tween
            + "var _vAnimStart=null;\n"    // performance.now() at tween start
            + "var _vAnimDur=" + animDur + ";\n"
            + "var _vAnimRaf=null;\n"
            // Easing function: easeOutCubic
            + "function _vEase(t){return 1-(1-t)*(1-t)*(1-t);}\n"
            // Linear interpolation helper
            + "function _vLerp(a,b,t){return a+(b-a)*t;}\n"
            // Build a flat (zero-width) snapshot of a violinData array.
            // Used as the 'from' state for the initial grow-in.
            + "function _vFlatSnap(src){\n"
            + "  return src.map(function(vd){\n"
            + "    var flat={};\n"
            + "    for(var k in vd)flat[k]=vd[k];\n"
            + "    flat.kde=vd.kde.map(function(pt){return[pt[0],0];});\n"
            + "    return flat;\n"
            + "  });\n"
            + "}\n"
            // Build a zero-stat snapshot for a group that doesn't exist in 'from'.
            // KDE is a flat line at the new group's median (so it appears to
            // grow in from a point rather than sliding from position 0).
            + "function _vZeroEntry(newVd){\n"
            + "  var e={};\n"
            + "  for(var k in newVd)e[k]=newVd[k];\n"
            + "  e.kde=newVd.kde.map(function(pt){return[pt[0],0];});\n"
            + "  e.n=0;\n"
            + "  return e;\n"
            + "}\n"
            // Interpolate two violinData snapshots at progress t.
            // Writes result directly into _violinData (in-place mutation).
            + "function _vInterp(from,to,t){\n"
            + "  var et=_vEase(t);\n"
            + "  _violinData=to.map(function(toVd,i){\n"
            + "    var fromVd=(i<from.length)?from[i]:_vZeroEntry(toVd);\n"
            + "    var r={};\n"
            + "    for(var k in toVd)r[k]=toVd[k];\n"
            // Interpolate KDE -- from and to always have same 50 eval points
            // because computeKde always produces exactly 50 points.
            // If counts differ (edge case: n=1 vs n>=2) use toVd.kde as fallback.
            + "    if(fromVd.kde&&toVd.kde&&fromVd.kde.length===toVd.kde.length){\n"
            + "      r.kde=toVd.kde.map(function(pt,j){\n"
            + "        return[pt[0],_vLerp(fromVd.kde[j][1],pt[1],et)];\n"
            + "      });\n"
            + "    }\n"
            // Interpolate all scalar stats
            + "    var sc=['median','mean','q1','q3','whiskerLo','whiskerHi','min','max'];\n"
            + "    sc.forEach(function(k){\n"
            + "      if(fromVd[k]!=null&&toVd[k]!=null)\n"
            + "        r[k]=_vLerp(fromVd[k],toVd[k],et);\n"
            + "    });\n"
            + "    return r;\n"
            + "  });\n"
            + "}\n"
            // Cancel any running animation
            + "function _vCancelAnim(){\n"
            + "  if(_vAnimRaf){cancelAnimationFrame(_vAnimRaf);_vAnimRaf=null;}\n"
            + "}\n"
            // Run one animation: from -> to over _vAnimDur ms.
            // On each frame: interpolate, render chart (triggers afterDraw).
            + "function _vRunAnim(from,to){\n"
            + "  _vCancelAnim();\n"
            + "  _vAnimFrom=from;\n"
            + "  _vAnimTo=to;\n"
            + "  _vAnimStart=null;\n"
            + "  function step(ts){\n"
            + "    if(!_vAnimStart)_vAnimStart=ts;\n"
            + "    var elapsed=ts-_vAnimStart;\n"
            + "    var t=Math.min(elapsed/_vAnimDur,1);\n"
            + "    _vInterp(_vAnimFrom,_vAnimTo,t);\n"
            // _mainChart.render() repaints the canvas triggering afterDraw.
            // We skip Chart.js built-in animation (duration:0 in chart config)
            // and drive every frame ourselves.
            + "    if(_mainChart)_mainChart.render();\n"
            + "    if(t<1)_vAnimRaf=requestAnimationFrame(step);\n"
            + "    else{_violinData=_vAnimTo;_vAnimRaf=null;}\n"
            + "  }\n"
            + "  _vAnimRaf=requestAnimationFrame(step);\n"
            + "}\n"
            // Animate to a new target dataset (called by _applyFilter).
            // Snapshots current _violinData as 'from', maps to new group count.
            + "function _vAnimateTo(newViolins,newLabels){\n"
            + "  var from=_violinData.map(function(vd){\n"
            + "    var c={};\n"
            + "    for(var k in vd)c[k]=vd[k];\n"
            + "    if(vd.kde)c.kde=vd.kde.map(function(pt){return[pt[0],pt[1]];});\n"
            + "    return c;\n"
            + "  });\n"
            // Update the base chart labels so the x-axis redraws correctly.
            // Use duration:0 so this update doesn't fight our RAF loop.
            + "  if(_mainChart){\n"
            + "    _mainChart.data.labels=newLabels;\n"
            + "    _mainChart.data.datasets=[{label:'_v',\n"
            + "      data:newLabels.map(function(){return 0;}),\n"
            + "      backgroundColor:'rgba(0,0,0,0)',\n"
            + "      borderColor:'rgba(0,0,0,0)',borderWidth:0}];\n"
            + "    _mainChart.options.animation={duration:0};\n"
            + "    _mainChart.update();\n"
            // Restore animation duration for future renders
            + "    _mainChart.options.animation={duration:" + animDur + "};\n"
            + "  }\n"
            // Now align 'from' to match newViolins group count.
            // For groups in newViolins that don't exist in 'from', use zeroEntry.
            + "  var alignedFrom=newViolins.map(function(toVd,i){\n"
            + "    if(i<from.length)return from[i];\n"
            + "    return _vZeroEntry(toVd);\n"
            + "  });\n"
            + "  _vRunAnim(alignedFrom,newViolins);\n"
            + "}\n";

        // Build the Chart.js script. Type:'bar' gives us a category x-axis
        // automatically from the labels array. Bars are invisible (alpha=0).
        // The value (y) axis is linear -- controlled by buildBoxValueAxisConfig.
        // v2.5.1: animation is driven by RAF loop (_vRunAnim), not Chart.js
        // built-in animation, so we set duration:0 in the chart config and
        // drive rendering manually. The initial grow-in starts immediately
        // after chart creation via _vRunAnim(_vFlatSnap(_vOrigData),_vOrigData).
        // v3.5.28: wrap entire violin script in an IIFE keyed on canvas id.
        // Without this, every by() panel emits var _violinData, var _vPlugin,
        // var _mainChart etc at top level -- the second panel clobbers the
        // first, so Domestic is blank and Foreign shows only one violin.
        // The IIFE makes all violin state vars local. _mainChart is accessible
        // cross-panel only via Chart.getChart(id) which already works.
        // _vAnimateTo_N (used by _applyFilter for by()+filter() violin) must
        // remain accessible outside the IIFE, so we assign it to window.
        String safeId = id.replace("-", "_");
        return "(function(){\n"
            + "var _mainChart=null;\n"   // IIFE-scoped; shared by _initChart and _vRunAnim
            + violinDataJs
            + legendPlugin
            + violinPlugin
            + animationEngine
            + "function _initChart(_labels,_datasets){\n"
            + "  var existing=Chart.getChart('" + id + "');\n"
            + "  if(existing)existing.destroy();\n"
            + "  _mainChart=new Chart(document.getElementById('" + id + "'), {\n"
            + "    type:'bar',\n"
            + "    data:{labels:_labels,datasets:_datasets},\n"
            + "    options:{\n"
            + "      " + indexAxisCfg + "\n"
            + "      responsive:true,maintainAspectRatio:true,resizeDelay:150," + aspectCfg + "\n"
            // duration:0 -- we drive all animation ourselves via RAF
            + "      animation:{duration:0},\n"
            + paddingCfg
            + "      plugins:{\n"
            + "        legend:{display:false},\n"
            + "        tooltip:{enabled:false}\n"
            + "      },\n"
            + "      scales:{x:" + xScaleCfg + ",y:" + yScaleCfg + "}\n"
            + "    },\n"
            // t2j fix8v: the on-canvas key (_vLegendPlugin) is no longer registered on single
            // pages either -- it sat INSIDE the plot area and covered the last violins once the
            // chart was narrow (browser zoom 250%). The key is now the same HTML shared key the
            // by() grid uses, above the chart (HtmlGenerator.build). Plugin kept for reference.
            + "    plugins:[_vPlugin]\n"
            + "  });\n"
            + "  " + tooltipJs
            + "}\n"
            // t2j: recompute violin entries from FILTERED per-group values and
            // animate to them. Fixes the completeness bug where filtering a violin
            // only swapped invisible bars and left the KDE shape (_violinData) stale
            // -- _vAnimateTo existed but was never called from the filter path.
            // groupVals[i] is the array of non-missing plot values for over-group i
            // (order matches _vOrigData / the original labels). Colors and geometry
            // metadata are copied from _vOrigData[i] (filtering never changes them).
            // KDE + box stats use the SAME Stata-default algorithms as the initial
            // server render (_sAgg.computeKde / _sAgg.pctile).
            // t2j fix9d: groupVals may be a FUNCTION (gi, vi) -> values; it is then evaluated
            // once per _vOrigData entry (every group x every variable, in render order), so
            // multi-variable violins refresh completely. The array form is kept for callers
            // that already pass one list per entry.
            + "function _vFilter(groupVals,fenceK){\n"
            + "  if(!groupVals)return;\n"
            + "  if(typeof groupVals==='function'){var _vf=groupVals;groupVals=_vOrigData.map(function(e){return _vf((e.gi!=null?e.gi:-1),(e.vi!=null?e.vi:0),e);});}\n"
            + "  var _fk=(typeof fenceK==='number'&&fenceK>0)?fenceK:1.5;\n"
            + "  var newViolins=groupVals.map(function(vals,gi){\n"
            + "    var base=_vOrigData[gi]||_vOrigData[_vOrigData.length-1]||{};\n"
            + "    var e={label:base.label,xIdx:(base.xIdx!=null?base.xIdx:gi),gi:base.gi,vi:base.vi,"
            + "color:base.color,borderColor:base.borderColor,"
            + "medianColor:base.medianColor,meanColor:base.meanColor};\n"
            + "    if(!vals||vals.length===0){e.kde=[];e.median=null;e.mean=null;"
            + "e.q1=null;e.q3=null;e.whiskerLo=null;e.whiskerHi=null;e.min=null;e.max=null;e.n=0;return e;}\n"
            + "    var s=vals.slice().sort(function(a,b){return a-b;});\n"
            + "    var n=s.length,sum=0;for(var i=0;i<n;i++)sum+=s[i];\n"
            + "    var q1=_sAgg.pctile(s,25),q3=_sAgg.pctile(s,75),med=_sAgg.pctile(s,50);\n"
            + "    var iqr=q3-q1,lo=q1-_fk*iqr,hi=q3+_fk*iqr,wLo=s[0],wHi=s[n-1];\n"
            + "    for(var a=0;a<n;a++){if(s[a]>=lo){wLo=s[a];break;}}\n"
            + "    for(var b=n-1;b>=0;b--){if(s[b]<=hi){wHi=s[b];break;}}\n"
            + "    e.n=n;e.mean=sum/n;e.median=med;e.q1=q1;e.q3=q3;\n"
            + "    e.whiskerLo=wLo;e.whiskerHi=wHi;e.min=s[0];e.max=s[n-1];\n"
            + "    e.kde=_sAgg.computeKde(s," + _bwKdeJs + ",50);\n"
            + "    return e;\n"
            + "  });\n"
            // keep the current label set (over-groups are unchanged by filtering)
            + "  var lbls=(_mainChart&&_mainChart.data&&_mainChart.data.labels)?_mainChart.data.labels.slice():newViolins.map(function(v){return v.label;});\n"
            + "  _vAnimateTo(newViolins,lbls);\n"
            + "}\n"
            // Expose _vAnimateTo / _vFilter on window so _applyFilter (outside IIFE)
            // can call them. FilterRenderer (single chart) calls window._vFilter;
            // by()-panel charts expose _vFilter_N (trailing panel index).
            // id=mainChart -> expose as window._vAnimateTo / window._vFilter
            // id=chart_by_N -> expose as window._vAnimateTo_N / window._vFilter_N
            + (id.equals("mainChart")
               ? "window._vAnimateTo=_vAnimateTo;window._vFilter=_vFilter;window._vTarget=function(){return _vAnimTo||_violinData;};\n"
               : "window['_vAnimateTo_"+safeId.replaceAll("^.*_(\\d+)$","$1")+"']=_vAnimateTo;"
                 + "window['_vFilter_"+safeId.replaceAll("^.*_(\\d+)$","$1")+"']=_vFilter;"
                 + "window['_vTarget_"+safeId.replaceAll("^.*_(\\d+)$","$1")+"']=function(){return _vAnimTo||_violinData;};\n")   // fix9d: read-only view of the violin entries the chart is animating to (verify/byfilter_probe.js)
            + "var _initLabels=[" + labels + "];\n"
            + "var _initDatasets=[" + anchorDs + "];\n"
            + "_initChart(_initLabels,_initDatasets);\n"
            // Kick off the initial grow-in animation from flat -> full shape
            + "_vRunAnim(_vFlatSnap(_vOrigData),_vOrigData);\n"
            + "})();\n";
    }

    /**
     * Renders a boxplot or violin chart using the chartjs-chart-boxplot plugin.
     * isViolin=true uses Chart.js type "violin"; false uses "boxplot".
     *
     * Dataset format: pre-computed {min,q1,median,mean,q3,max,outliers[]}.
     * The plugin renders the full five-number summary + outlier dots.
     * Whisker fences controlled by o.stats.whiskerfence (default 1.5 = Tukey).
     *
     * X-axis: category (group labels from over(), or variable names if no over).
     * Y-axis: linear value axis (left), honors yrange, ytitle, ystart(zero).
     *
     * Tooltip: shows group label as title, then per-dataset:
     *   variable name, Median, Q1, Q3, Min (whisker lo), Max (whisker hi).
     */
    String boxPlot(String id, DataSet data, boolean isViolin) {
        String chartType = isViolin ? "violin" : "boxplot";
        String labels    = boxplotLabels(data);
        // v2.4.4: pass isViolin so violin gets raw arrays, boxplot gets pre-computed stats
        String datasets  = boxplotDatasets(data, isViolin);

        // v2.4.7: horizontal box/violin (hbox/hviolin) -- swap axes via indexAxis:'y'
        // The plugin honours indexAxis the same way Chart.js bar does.
        // v2.4.10 FIX: for horizontal, y is the CATEGORY axis (group labels) and
        // must be type:'category'. x is the VALUE axis and gets buildBoxYAxisConfig.
        // For vertical, x is category and y is the value axis (original behaviour).
        String indexAxisCfg = o.chart.horizontal ? "indexAxis:'y'," : "";
        // v3.5.9: fall back to variable label/name when xtitle()/ytitle() not supplied.
        // Value axis: first numeric variable label/name.
        // Category axis: over() variable label/name when over() present, else blank.
        List<Variable> _bpNv = data.getNumericVariables();
        Variable _bpOv = data.getOverVariable();
        String _bpValTitle  = !_bpNv.isEmpty() ? _bpNv.get(0).getDisplayName() : "";
        String _bpCatTitle  = _bpOv != null ? _bpOv.getDisplayName() : "";
        String xTitleStr = !o.axes.xtitle.isEmpty() ? o.axes.xtitle
            : (o.chart.horizontal ? _bpValTitle : _bpCatTitle);
        String yTitleStr = !o.axes.ytitle.isEmpty() ? o.axes.ytitle
            : (o.chart.horizontal ? _bpCatTitle : _bpValTitle);
        String xScaleCfg = o.chart.horizontal ? buildBoxValueAxisConfig("x", xTitleStr)
                                        : buildBoxCategoryAxisConfig("x", xTitleStr,
                                            _bpOv != null ? DataSet.uniqueValues(_bpOv, o.chart.sortgroups, o.showmissingOver).size() : _bpNv.size());
        String yScaleCfg = o.chart.horizontal ? buildBoxCategoryAxisConfig("y", yTitleStr,
                                            _bpOv != null ? DataSet.uniqueValues(_bpOv, o.chart.sortgroups, o.showmissingOver).size() : _bpNv.size())
                                        : buildBoxValueAxisConfig("y", yTitleStr);

        String aspectCfg  = o.chart.aspect.isEmpty() ? "" : "aspectRatio:" + o.chart.aspect + ",";
        String paddingCfg = buildPadding();
        String animDur    = animDuration();
        String easingCfg  = o.chart.easing.isEmpty() ? "" : ",easing:'" + o.chart.easing + "'";
        String delayCfg   = o.chart.animdelay.isEmpty() ? "" : ",delay:" + o.chart.animdelay;
        String legendCfg  = buildLegendConfig();
        String lc         = labelColor();

        // Tooltip: show five-number summary per dataset.
        // For boxplot: ctx.raw is a pre-computed stats object {min,q1,median,mean,q3,max,outliers}.
        // For violin:  the plugin receives raw number[] and converts internally to IViolinItem
        //   {min,median,max,coords:[{v,estimate},...]}. ctx.raw is this object, NOT the raw array.
        //   IViolinItem does NOT have q1/q3 -- we use min/max/median only.
        //   isViolin branch detected by absence of d.q1.
        // v2.4.4: violin mode forced to 'nearest' (one-dataset-per-group produces noisy
        //   'index' tooltips that list all null-padded groups).
        String ttMode = isViolin ? "nearest"
                      : (o.chart.tooltipmode.isEmpty() ? "index" : o.chart.tooltipmode);
        String ttPos  = o.chart.tooltippos.equals("average") ? "average" : "nearest";
        // t2j fix8 (A5): box/violin tooltip now honors tooltipbg/border/fontsize/padding via the
        // same shared prefix the standard charts use (it previously emitted no style keys).
        String ttCfg  = "tooltip:{" + tooltipStylePrefix() + "mode:'" + ttMode + "',intersect:true,position:'" + ttPos + "',"
            + "callbacks:{"
            + "title:function(items){return items.length?items[0].label:'';},"
            + "label:function(ctx){"
            +   "var d=ctx.raw;"
            +   "var lbl=ctx.dataset.label||'';"
            +   "var fmt=function(v){return v==null?'--':(Math.round(v*100)/100).toLocaleString();};"
            // null-padded slots (violin one-dataset-per-group) are null -- skip silently
            +   "if(d==null)return '';"
            // v2.4.10: violin passes raw number[] -- ctx.raw is the array itself.
            // Compute stats from the array directly for tooltip display.
            +   "if(Array.isArray(d)){"
            +     "var arr=d.slice().sort(function(a,b){return a-b;});"
            +     "var n=arr.length;"
            +     "if(n===0)return '';"
            +     "var sum=arr.reduce(function(a,b){return a+b;},0);"
            +     "var mn=sum/n;"
            +     "var med=n%2===0?(arr[n/2-1]+arr[n/2])/2:arr[Math.floor(n/2)];"
            +     "return ["
            +       "'  '+lbl,"
            +       "'  Median: '+fmt(med),"
            +       "'  Mean:   '+fmt(mn),"
            +       "'  Min:    '+fmt(arr[0]),"
            +       "'  Max:    '+fmt(arr[n-1]),"
            +       "'  N:      '+n"
            +     "];"
            +   "}"
            // stats object: check it has median property
            +   "if(typeof d!=='object'||d.median===undefined)return '';"
            // v2.4.4: violin IViolinItem has min/median/max/mean (no q1/q3)
            +   "if(d.q1===undefined){"
            +     "return ["
            +       "'  '+lbl,"
            +       "'  Median: '+fmt(d.median),"
            +       "'  Mean:   '+fmt(d.mean),"
            +       "'  Min:    '+fmt(d.min),"
            +       "'  Max:    '+fmt(d.max)"
            +     "];"
            +   "}"
            // boxplot: pre-computed {min,q1,median,mean,q3,max,outliers}
            // t2j fix8f (issue #4): the box draws a mean marker but the tooltip omitted
            // its value -- add a Mean line (the violin branch above already shows it).
            +   "return ["
            +     "'  '+lbl,"
            +     "'  Median:  '+fmt(d.median),"
            +     "'  Mean:    '+fmt(d.mean),"
            +     "'  Q1:      '+fmt(d.q1),"
            +     "'  Q3:      '+fmt(d.q3),"
            +     "'  Lower:   '+fmt(d.min),"
            +     "'  Upper:   '+fmt(d.max),"
            +     "(d.outliers&&d.outliers.length?'  Outliers: '+d.outliers.length:'  Outliers: 0')"
            +   "];"
            + "}"
            + "}}";

        // v2.4.6: inline legend plugin -- three improvements over v2.4.5:
        //   Fix 1: adaptive median/mean colors based on box luminance.
        //          Luminance computed from box fill (standard relative luminance).
        //          Light box (lum>0.35): median=#1a1a1a, mean=#c2410c (deep orange).
        //          Dark box (lum<=0.35): median=#ffffff, mean=#f59e0b (amber).
        //          This keeps both markers visible regardless of palette color.
        //   Fix 2: violin shows 3-item legend (Median, Mean, KDE Shape).
        //          Boxplot shows 5-item legend (adds Whiskers and Outlier).
        //          IQR Box / Whiskers / Outlier do not apply to violin charts.
        //   Fix 3: whisker swatch color #cccccc -- light grey visible on dark bg.
        //          Old #888888 blended with rgba(0,0,0,0.45) legend background.
        //          Also increased legend bg opacity to 0.55 for better contrast.
        String isViolinJs = isViolin ? "true" : "false";
        String isDarkJs   = gen.isDark() ? "true" : "false";
        String bpPlugin = "var _bpIsViolin=" + isViolinJs + ";\n"
            + "var _bpIsDark=" + isDarkJs + ";\n"
            + "var _bpLegendPlugin={\n"
            + "  id:'bpInlineLegend',\n"
            + "  afterDraw:function(chart){\n"
            + "    var ctx=chart.ctx;\n"
            + "    var ca=chart.chartArea;\n"
            + "    if(!ca)return;\n"
            + "    function _lum(c){\n"
            + "      var r=0,g=0,b=0;\n"
            + "      if(!c)return 0;\n"
            + "      var m=c.match(/rgba?[\\s]*\\([\\s]*(\\d+)[\\s]*,[\\s]*(\\d+)[\\s]*,[\\s]*(\\d+)/);\n"
            + "      if(m){r=+m[1];g=+m[2];b=+m[3];}\n"
            + "      else if(c[0]=='#'){\n"
            + "        var hx=c.slice(1);\n"
            + "        if(hx.length===3){hx=hx[0]+hx[0]+hx[1]+hx[1]+hx[2]+hx[2];}\n"
            + "        r=parseInt(hx.slice(0,2),16);g=parseInt(hx.slice(2,4),16);b=parseInt(hx.slice(4,6),16);\n"
            + "      }\n"
            + "      function _s(v){v=v/255;return v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4);}\n"
            + "      return 0.2126*_s(r)+0.7152*_s(g)+0.0722*_s(b);\n"
            + "    }\n"
            + "    var _ds0=chart.data.datasets[0];\n"
            + "    var _bg0=_ds0?(Array.isArray(_ds0.backgroundColor)?_ds0.backgroundColor[0]:_ds0.backgroundColor)||'rgba(78,121,167,0.85)':'rgba(78,121,167,0.85)';\n"
            + "    // v2.4.10: violin -> read medianColor/meanBackgroundColor directly from ds0\n"
            + "    // (each dataset has its own per-fill color; legend shows ds0 as representative).\n"
            + "    // boxplot -> keep avg-luminance approach (single dataset, per-element array).\n"
            + "    var _medColor,_meanColor;\n"
            + "    if(_bpIsViolin&&_ds0&&_ds0.medianColor){\n"
            + "      _medColor=_ds0.medianColor;\n"
            + "      _meanColor=_ds0.meanBackgroundColor||_ds0.medianColor;\n"
            + "    } else {\n"
            + "      var _lvSum=0,_lvCnt=0;\n"
            + "      chart.data.datasets.forEach(function(ds){\n"
            + "        var bg=Array.isArray(ds.backgroundColor)?ds.backgroundColor[0]:ds.backgroundColor;\n"
            + "        if(bg){_lvSum+=_lum(bg);_lvCnt++;}\n"
            + "      });\n"
            + "      var _lv=_lvCnt>0?_lvSum/_lvCnt:_lum(_bg0);\n"
            + "      _medColor=_bpIsDark?'#ffffff':(_lv>0.60?'#555555':(_lv>0.15?'#000000':'#ffffff'));\n"
            + "      _meanColor=_bpIsDark?'#ffffff':(_lv>0.60?'#555555':(_lv>0.15?'#000000':'#ffffff'));\n"
            + "    }\n"
            + "    var items;\n"
            + "    if(_bpIsViolin){\n"
            + "      items=[\n"
            + "        {label:'Median',type:'diamond',color:_medColor},\n"
            + "        {label:'Mean',type:'dot',color:_meanColor},\n"
            + "        {label:'KDE Shape',type:'rect',color:_bg0}\n"
            + "      ];\n"
            + "    } else {\n"
            + "      items=[\n"
            + "        {label:'Median',type:'line',color:_medColor},\n"
            + "        {label:'Mean',type:'dot',color:_meanColor},\n"
            + "        {label:'IQR Box',type:'rect',color:_bg0},\n"
            + "        {label:'Whiskers',type:'whisker',color:'#cccccc'},\n"
            + "        {label:'Outlier',type:'outlier',color:'#e74c3c'}\n"
            + "      ];\n"
            + "    }\n"
            + "    var fs=11,lh=18,pw=14,gap=6,padX=10,padY=8,bw=0;\n"
            + "    ctx.font=fs+'px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif';\n"
            + "    items.forEach(function(it){var tw=ctx.measureText(it.label).width;if(tw+pw+gap>bw)bw=tw+pw+gap;});\n"
            + "    bw+=padX*2;\n"
            + "    var bh=items.length*lh+padY*2;\n"
            + "    var bx=ca.right-bw-8,by=ca.top+8;\n"
            + "    ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n"
            + "    ctx.fillStyle='rgba(0,0,0,0.55)';\n"
            + "    ctx.strokeStyle='rgba(255,255,255,0.25)';\n"
            + "    ctx.lineWidth=1;\n"
            + "    var r=6;ctx.beginPath();\n"
            + "    ctx.moveTo(bx+r,by);ctx.lineTo(bx+bw-r,by);ctx.arcTo(bx+bw,by,bx+bw,by+r,r);\n"
            + "    ctx.lineTo(bx+bw,by+bh-r);ctx.arcTo(bx+bw,by+bh,bx+bw-r,by+bh,r);\n"
            + "    ctx.lineTo(bx+r,by+bh);ctx.arcTo(bx,by+bh,bx,by+bh-r,r);\n"
            + "    ctx.lineTo(bx,by+r);ctx.arcTo(bx,by,bx+r,by,r);ctx.closePath();\n"
            + "    ctx.fill();ctx.stroke();\n"
            + "    items.forEach(function(it,i){\n"
            + "      var ix=bx+padX,iy=by+padY+i*lh+lh/2;\n"
            + "      ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();\n"
            + "      if(it.type==='line'){ctx.strokeStyle=it.color;ctx.lineWidth=2.5;ctx.beginPath();ctx.moveTo(ix,iy);ctx.lineTo(ix+pw,iy);ctx.stroke();}\n"
            + "      else if(it.type==='dot'){ctx.fillStyle=it.color;ctx.beginPath();ctx.arc(ix+pw/2,iy,4,0,Math.PI*2);ctx.fill();}\n"
            + "      else if(it.type==='rect'){ctx.fillStyle=it.color;ctx.strokeStyle='rgba(255,255,255,0.6)';ctx.lineWidth=1.2;ctx.fillRect(ix,iy-5,pw,10);ctx.strokeRect(ix,iy-5,pw,10);}\n"
            + "      else if(it.type==='whisker'){ctx.strokeStyle=it.color;ctx.lineWidth=1.8;ctx.beginPath();ctx.moveTo(ix+pw/2,iy-6);ctx.lineTo(ix+pw/2,iy+6);ctx.stroke();ctx.beginPath();ctx.moveTo(ix+2,iy-6);ctx.lineTo(ix+pw-2,iy-6);ctx.stroke();ctx.beginPath();ctx.moveTo(ix+2,iy+6);ctx.lineTo(ix+pw-2,iy+6);ctx.stroke();}\n"
            + "      else if(it.type==='outlier'){ctx.strokeStyle=it.color;ctx.lineWidth=1.5;ctx.beginPath();ctx.arc(ix+pw/2,iy,4,0,Math.PI*2);ctx.stroke();}\n"
            + "      else if(it.type==='diamond'){ctx.fillStyle=it.color;ctx.beginPath();var dm=6;ctx.moveTo(ix+pw/2,iy-dm);ctx.lineTo(ix+pw/2+dm,iy);ctx.lineTo(ix+pw/2,iy+dm);ctx.lineTo(ix+pw/2-dm,iy);ctx.closePath();ctx.fill();}\n"
            + "      ctx.restore();ctx.fillStyle='#ffffff';\n"
            + "      ctx.font=fs+'px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif';\n"
            + "      ctx.textBaseline='middle';ctx.fillText(it.label,ix+pw+gap,iy);\n"
            + "    });\n"
            + "    ctx.restore();\n"
            + "  }\n"
            + "};\n";
        // v2.4.10: inject medianColor/meanBackgroundColor as scriptable options.
        // Dataset-level medianColor is silently ignored by the plugin in Chart.js 4.x.
        // The reliable path is options.datasets.violin.medianColor as a function.
        // We pre-compute the per-dataset color arrays and read by ctx.datasetIndex.
        String violinDsOpts = "";
        if (isViolin) {
            String medArr  = dsb.violinMedColorArray(data);
            String meanArr = dsb.violinMeanColorArray(data);
            violinDsOpts = "    datasets:{violin:{\n"
                + "      medianColor:function(ctx){var a=" + medArr + ";return a[ctx.datasetIndex]||'#000000';},\n"
                + "      meanBackgroundColor:function(ctx){var a=" + meanArr + ";return a[ctx.datasetIndex]||'#000000';},\n"
                + "      meanBorderColor:function(ctx){var a=" + meanArr + ";var c=a[ctx.datasetIndex]||'#000000';"
                + "var m=c.match(/rgba?\\\\s*\\\\(\\\\s*(\\\\d+)\\\\s*,\\\\s*(\\\\d+)\\\\s*,\\\\s*(\\\\d+)/);var r=0,g=0,b=0;"
                + "if(m){r=+m[1];g=+m[2];b=+m[3];}else if(c[0]=='#'){var hx=c.slice(1);"
                + "if(hx.length==3)hx=hx[0]+hx[0]+hx[1]+hx[1]+hx[2]+hx[2];"
                + "r=parseInt(hx.slice(0,2),16);g=parseInt(hx.slice(2,4),16);b=parseInt(hx.slice(4,6),16);}"
                + "var l=0.2126*(r/255)+0.7152*(g/255)+0.0722*(b/255);"
                + "return l>0.35?'#000000':'#ffffff';},\n"
                + "      medianRadius:5,meanRadius:5,meanBorderWidth:2\n"
                + "    }},\n";
        }
        return bpPlugin
            + "new Chart(document.getElementById('" + id + "'), {\n"
            + "  type:'" + chartType + "',\n"
            + "  data:{labels:[" + labels + "],datasets:[" + datasets + "]},\n"
            + "  options:{\n"
            + "    " + indexAxisCfg + "\n"
            + "    responsive:true,maintainAspectRatio:true,resizeDelay:150," + aspectCfg + "\n"
            + "    animation:{duration:" + animDur + easingCfg + delayCfg + "},\n"
            + paddingCfg
            + violinDsOpts
            // v2.4.7: suppress Chart.js default legend -- _bpLegendPlugin draws
            // the custom inline legend (Median/Mean/IQR/Whiskers/Outlier swatches).
            // Default legend would show series/variable names which adds no information.
            + "    plugins:{\n"
            + "      legend:{display:false},\n"
            + "      " + ttCfg + "\n"
            + "    },\n"
            + "    scales:{x:" + xScaleCfg + ",y:" + yScaleCfg + "}\n"
            + "  },\n"
            // t2j fix8v: _bpLegendPlugin (on-canvas key inside the plot) not registered any
            // more; the HTML shared key above the chart replaces it on single pages too.
            + "  plugins:[]\n"
            + "});\n";
    }


    /**
     * Builds the y-axis scale config for boxplot/violin charts. (v2.4.4)
     *
     * Key differences from the standard buildAxisConfig:
     *   minStats:'min' / maxStats:'max' -- tells the plugin scale to extend
     *     bounds to include ALL outlier dots, not just the whisker tips.
     *     Without this, outliers beyond the whiskers plot outside the chart area.
     *   grace: adds a small padding fraction above and below the scale bounds
     *     so extreme outliers are not clipped at the very edge of the canvas.
     *
     * Honors user yrange() / ytitle() / ystart(zero) options.
     */
    /**
     * Value axis config for boxplot/violin (type:linear, explicit outlier bounds).
     * Used for: y-axis on vertical charts, x-axis on horizontal charts. (v2.4.10)
     */
    private String buildBoxValueAxisConfig(String axisId, String title) {
        String lc = labelColor();
        String gc = gridCssColor();
        StringBuilder sb = new StringBuilder("{");
        sb.append("type:'linear',");
        if (!o.axes.yrangeMin.isEmpty() && !o.axes.yrangeMax.isEmpty()) {
            sb.append("min:").append(o.axes.yrangeMin).append(",");
            sb.append("max:").append(o.axes.yrangeMax).append(",");
        }
        if (o.axes.yStartZero && o.axes.yrangeMin.isEmpty()) sb.append("min:0,");
        // v2.4.4: explicit bounds to include outliers (plugin doesn't report them to scale)
        if (o.axes.yrangeMin.isEmpty() && o._boxYMin < Double.MAX_VALUE) {
            double pad = (o._boxYMax - o._boxYMin) * 0.08;
            double yLo = o._boxYMin - pad;
            double yHi = o._boxYMax + pad;
            if (o.axes.yStartZero && yLo > 0) yLo = 0;
            sb.append(String.format(Locale.ROOT, "min:%.4f,max:%.4f,", yLo, yHi));
        } else {
            sb.append("grace:'5%',");
        }
        // t2j fix8 (A4): reuse the shared axis tick/title style helpers so box/violin honor
        // xlabsize/xlabcolor/ylabsize/ylabcolor and xtitlesize/xtitlecolor/... like every other
        // chart (they previously hardcoded the label colour and emitted no font size).
        // t2j fix8q (QC sweep S1): the explicit padded min/max above are raw data limits, and
        // Chart.js labels the bounds of an explicit range by default (ticks.includeBounds), so
        // every box/violin value axis ended in odd labels like "2,290.4" / "16,914.6". Drop the
        // bound labels; the nice interior ticks remain (same option the coefplot axes use).
        sb.append("ticks:{includeBounds:false,").append(axisTickStyleCfg(axisId.equals("x"))).append("},");
        // t2j fix6: box/violin now honor xgridlines/ygridlines/xborder/yborder (were hardcoded
        // display:true, so nogrid was ignored on these types).
        boolean vGrid = axisId.equals("x") ? o.axes.xgridlines : o.axes.ygridlines;
        boolean vBord = axisId.equals("x") ? o.axes.xborder    : o.axes.yborder;
        sb.append("grid:{color:'").append(gc).append("',display:").append(vGrid).append("},");
        sb.append("border:{color:'").append(gc).append("',display:").append(vBord).append("},");
        sb.append(axisTitleCfg(title, axisId.equals("x")));
        sb.append("}");
        return sb.toString();
    }

    /**
     * Category axis config for boxplot/violin (type:category, no numeric bounds).
     * Used for: x-axis on vertical charts, y-axis on horizontal charts. (v2.4.10)
     */
    private String buildBoxCategoryAxisConfig(String axisId, String title) {
        return buildBoxCategoryAxisConfig(axisId, title, 0);
    }

    private String buildBoxCategoryAxisConfig(String axisId, String title, int nCategories) {
        String lc = labelColor();
        String gc = gridCssColor();
        StringBuilder sb = new StringBuilder("{");
        sb.append("type:'category',");
        // Auto-rotation + autoSkip:false matching barLine logic.
        // For horizontal box/violin the category axis is y -- no rotation needed.
        boolean isCatX = axisId.equals("x");
        String tickRotation = "";
        if (isCatX) {
            String userAngle = o.axes.xtickangle;
            if (!userAngle.isEmpty()) {
                tickRotation = ",maxRotation:" + userAngle + ",minRotation:" + userAngle;
            } else {
                int autoAngle = nCategories > 20 ? 90 : nCategories > 8 ? 45 : 0;
                tickRotation = ",maxRotation:" + autoAngle + ",minRotation:" + autoAngle;
            }
        }
        // t2j fix8 (A4): reuse the shared tick-style helper (honors xlab/ylab size+colour).
        sb.append("ticks:{").append(axisTickStyleCfg(isCatX)).append(tickRotation);
        // autoSkip:false always -- show all category labels on both x and y category axes.
        // For hbox/hviolin the category axis is y; rotation is skipped but labels must all show.
        sb.append(",autoSkip:false");
        sb.append("},");
        // t2j fix6: honor grid/border toggles on the box/violin category axis too.
        boolean cGrid = isCatX ? o.axes.xgridlines : o.axes.ygridlines;
        boolean cBord = isCatX ? o.axes.xborder    : o.axes.yborder;
        sb.append("grid:{color:'").append(gc).append("',display:").append(cGrid).append("},");
        sb.append("border:{color:'").append(gc).append("',display:").append(cBord).append("},");
        // t2j fix8 (A4): reuse the shared title helper (honors xtitle/ytitle size+colour).
        sb.append(axisTitleCfg(title, isCatX));
        sb.append("}");
        return sb.toString();
    }



    // =========================================================================
    // marginsPlot -- Renders margins results from sparkta_read_margins (v3.6.0-s8a)
    // Supports single and multi-series (over()) for categorical and numeric x-axis.
    // Reuses col(), colAtAlpha(), pexline, cistyle plugin patterns from coefPlot().
    // =========================================================================
    // =========================================================================
    // marginsPlot -- v3.6.0-s8a (rewrite: correct categorical/numeric detection,
    //   CI plugins, axis labels, tooltip, series names)
    // =========================================================================
    /**
     * t2j fix8p (C8): on a FILTERED chart, lock a continuous axis to the FULL-data extent
     * so switching a filter (e.g. Domestic <-> Foreign) does not rescale and jump the frame
     * -- only the marks move, not the axes. Applied by default when the chart carries filters;
     * skipped when the user set an explicit range (xrange/yrange already put min:/max: in the
     * config) so the user's range always wins. dmin/dmax are the full-data extent.
     */
    private String lockAxisToFullData(String scaleCfg, double dmin, double dmax, DataSet data) {
        if (data == null || data.getFilterCount() == 0) return scaleCfg;
        if (scaleCfg.contains("min:") || scaleCfg.contains("max:")) return scaleCfg;
        if (Double.isNaN(dmin) || Double.isNaN(dmax) || !(dmax > dmin)) return scaleCfg;
        // fix8q: target ~10 ticks (Chart.js' own default density) -- 6 rounded price 3,299-15,906
        // out to 0-20,000 and wasted a third of the frame; 10 gives 2,000-16,000.
        double[] nr = niceRange(dmin, dmax, 10);
        return "{min:" + fmt(nr[0]) + ",max:" + fmt(nr[1]) + "," + scaleCfg.substring(1);
    }

    /**
     * t2j fix8q (C8 for by() panels): lock a continuous axis to the FULL-data extent that
     * HtmlGenerator.buildByScripts() stored for scatter/bubble panels (null outside panel
     * mode), so every panel shares one frame and a filter never rescales it. A user range
     * (min:/max: already present) or the shared panel y-range always wins.
     */
    private String lockAxisToPanelExtent(String scaleCfg, double[] ext) {
        if (ext == null) return scaleCfg;
        if (scaleCfg.contains("min:") || scaleCfg.contains("max:")) return scaleCfg;
        if (Double.isNaN(ext[0]) || Double.isNaN(ext[1]) || !(ext[1] > ext[0])) return scaleCfg;
        double[] nr = niceRange(ext[0], ext[1], 10);
        return "{min:" + fmt(nr[0]) + ",max:" + fmt(nr[1]) + "," + scaleCfg.substring(1);
    }

    /** Full-data [min,max] extent of a numeric Variable (missing/non-numeric skipped). */
    private static double[] varExtent(Variable v) {
        double mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
        for (Object o : v.getValues()) {
            if (o instanceof Number) { double d = ((Number) o).doubleValue();
                if (d < mn) mn = d; if (d > mx) mx = d; }
        }
        return new double[]{ mn, mx };
    }

    /**
     * v3.6.0-s8r: data-anchored "nice" ticks. Returns {niceMin, niceMax, step} such that
     * niceMin <= dataMin, niceMax >= dataMax and step is 1/2/5 x 10^k with ~target ticks.
     * The plotmargin() cushion is then added OUTSIDE [niceMin, niceMax], so the first and
     * last tick labels sit at (or just before/after) the first and last data points.
     */
    static double[] niceRange(double dataMin, double dataMax, int target) {
        if (!(dataMax > dataMin)) { double c = dataMin; return new double[]{ c - 1, c + 1, 1 }; }
        double raw = (dataMax - dataMin) / Math.max(target, 2);
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / mag, step = (f <= 1) ? 1 : (f <= 2) ? 2 : (f <= 5) ? 5 : 10;
        step *= mag;
        double lo = Math.floor(dataMin / step) * step, hi = Math.ceil(dataMax / step) * step;
        if (Math.abs(lo - dataMin) < 1e-12) lo = dataMin;   // exact hit: keep it
        return new double[]{ lo, hi, step };
    }
    /** JS afterBuildTicks body that sets ticks to niceMin..niceMax by step plus any extra values. */
    static String niceTicksJs(double[] nr, String extraCsv) {
        StringBuilder sb = new StringBuilder(",afterBuildTicks:function(axis){var t=[];");
        sb.append("for(var v=").append(String.format(Locale.ROOT, "%.10g", nr[0])).append(";v<=").append(String.format(Locale.ROOT, "%.10g", nr[1] + nr[2] * 1e-6))
          .append(";v+=").append(String.format(Locale.ROOT, "%.10g", nr[2])).append(")t.push({value:Math.round(v*1e8)/1e8,major:false});");
        if (extraCsv != null && !extraCsv.isEmpty())
            sb.append("[").append(extraCsv).append("].forEach(function(v){if(!t.some(function(q){return Math.abs(q.value-v)<1e-9;}))t.push({value:v,major:false});});");
        sb.append("t.sort(function(a,b){return a.value-b.value;});axis.ticks=t;}");
        return sb.toString();
    }

    /** v3.6.0-s8p: plotmargin() as fractions {left, right, bottom, top}; defaults 5 5 10 10 %. */
    double[] plotMarginFrac() {
        double[] d = { 0.05, 0.05, 0.10, 0.10 };
        String pm = o.chart.plotMargin == null ? "" : o.chart.plotMargin.trim();
        if (pm.isEmpty()) return d;
        String[] t = pm.split("\\s+");
        if (t.length != 4) return d;
        try { for (int i = 0; i < 4; i++) d[i] = Double.parseDouble(t[i]) / 100.0; } catch (NumberFormatException e) { return new double[]{ 0.05, 0.05, 0.10, 0.10 }; }
        return d;
    }

    /**
     * I5 (v3.6.0-s8y): ONE number formatter for every tooltip / label on the page.
     *   _spkFmt(v)        magnitude rule: |v|>=1000 -> 0 dp, >=10 -> 2, >=1 -> 3, else 4; thousands separators
     *   _spkFmt(v,'pct')  percent with 1 dp        _spkFmt(v,'int') integer with separators
     *   _spkFmt(v,'p')    p-value: <0.001 or 3 dp   _spkFmt(v,'coef') 4 significant-ish (3 dp)
     * tooltipformat() (user) still wins where the renderer passes an explicit format.
     */
    static String fmtJs() {
        return "if(typeof _spkFmt!=='function'){window._spkFmt=function(v,k){"
             + "if(v===null||v===undefined||(typeof v==='number'&&isNaN(v)))return '.';"
             + "if(k==='pct')return (v).toLocaleString(undefined,{minimumFractionDigits:1,maximumFractionDigits:1})+'%';"
             + "if(k==='int')return Math.round(v).toLocaleString();"
             + "if(k==='p')return v<0.001?'<0.001':v.toFixed(3);"
             + "if(k==='coef')return Math.abs(v)>=1000?Math.round(v).toLocaleString():v.toLocaleString(undefined,{minimumFractionDigits:3,maximumFractionDigits:3});"
             + "var a=Math.abs(v),d=a>=1000?0:(a>=10?2:(a>=1?3:4));"
             + "return v.toLocaleString(undefined,{minimumFractionDigits:d,maximumFractionDigits:d});};}\n";
    }

    /** v3.6.0-s8b: element i of a pipe-split array as a JS number literal, or "null". */
    private static String numOrNull(String[] a, int i) {
        if (a == null || i >= a.length) return "null";
        String v = a[i].trim();
        if (v.isEmpty() || v.equals(".")) return "null";
        try { double d = Double.parseDouble(v); if (!Double.isFinite(d)) return "null"; } catch (NumberFormatException e) { return "null"; }
        return v;
    }

    String marginsPlot(String id) {
        // Parse tilde-separated series (tilde=models, pipe=items within model)
        String[] seriesData  = o.chart.mpData.isEmpty()  ? new String[]{""} : o.chart.mpData.split("~",  -1);
        String[] seriesUpper = o.chart.mpUpper.isEmpty() ? new String[]{""} : o.chart.mpUpper.split("~", -1);
        String[] seriesLower = o.chart.mpLower.isEmpty() ? new String[]{""} : o.chart.mpLower.split("~", -1);
        String[] seriesXlab  = o.chart.mpXlab.isEmpty()  ? new String[]{""} : o.chart.mpXlab.split("~",  -1);
        String[] seriesXpos  = o.chart.mpXpos.isEmpty()  ? new String[]{""} : o.chart.mpXpos.split("~",  -1);
        // mpSeries: pipe-separated series display names (from interaction second component)
        // Treat "0" as empty (arg shift artifact from noallfilter flag)
        String mpSeriesRaw = o.chart.mpSeries.isEmpty() || o.chart.mpSeries.equals("0") ? "" : o.chart.mpSeries;
        String[] seriesNames = mpSeriesRaw.isEmpty() ? new String[]{""} : mpSeriesRaw.split("\\|", -1);
        int nSeries = seriesData.length;
        // t2g fix 3: cicolors(c1|c2|...) recolours the CI layer (band/whisker/inner) per series; one value = all series
        final String[] ciCc = o.chart.peCicolors.isEmpty() ? null : o.chart.peCicolors.split("\\|", -1);
        java.util.function.IntFunction<String> ciCol = si -> ciCc == null ? col(si) : (si < ciCc.length && !ciCc[si].trim().isEmpty() ? ciCc[si].trim() : ciCc[0].trim());

        // X-axis labels from first series block
        String[] xlabs = seriesXlab.length > 0 && !seriesXlab[0].isEmpty()
            ? seriesXlab[0].split("\\|", -1) : new String[]{};
        int k = Math.max(xlabs.length, 1);

        // Detect numeric x-axis: xpos must contain at least one real finite number.
        // Categorical charts produce xpos like "||||" (all empty segments).
        // Only set numericX=true if any pipe-separated segment parses as a finite double.
        boolean numericX = false;
        if (seriesXpos.length > 0 && !seriesXpos[0].isEmpty()) {
            for (String xseg : seriesXpos[0].split("\\|", -1)) {
                String xs = xseg.trim();
                if (!xs.isEmpty() && !xs.equals(".")) {
                    try { double d = Double.parseDouble(xs); if (Double.isFinite(d)) { numericX = true; break; } }
                    catch (NumberFormatException ignored) {}
                }
            }
        }

        // Rendering options
        // v3.6.0-s8m: cistyle() is a LIST of encodings: "area whisker" = band for the
        // level + whisker at the point. Tokens: area|band (band) and whisker|bar
        // (whisker; bar also switches the point estimates to bars, s8d).
        String ciRaw = o.chart.peCiStyle.isEmpty() ? "whisker" : o.chart.peCiStyle.trim().toLowerCase(Locale.ROOT);
        java.util.List<String> ciTok = java.util.Arrays.asList(ciRaw.replace("+"," ").trim().split("\\s+"));
        boolean bandStyle = ciTok.contains("area") || ciTok.contains("band");
        boolean drawBand  = bandStyle;
        boolean drawWhisk = ciTok.contains("whisker") || ciTok.contains("bar") || !bandStyle;
        boolean barMode   = ciTok.contains("bar") || o.chart.peCoefStyle.equals("bar");
        String ciStyle    = bandStyle ? (ciTok.contains("band") ? "band" : "area") : (barMode ? "bar" : "whisker");
        if (barMode && !numericX) { drawWhisk = true; }   // CI whiskers over the bars
        if (barMode && numericX) { barMode = false; }    // bars need a category axis
        boolean isHoriz  = o.chart.peOrient.equals("h");
        boolean connected = o.chart.peConnected || numericX;
        String pStyle    = o.chart.pointstyle.isEmpty() ? "circle" : o.chart.pointstyle;
        String pSize     = o.chart.pointsize.isEmpty()  ? "5"      : o.chart.pointsize;
        double ciWidthVal = o.chart.peCiwidth.isEmpty() ? 1.5 : parseDouble(o.chart.peCiwidth, 1.5);
        String ciWidthStr = String.format(Locale.ROOT, "%.1f", ciWidthVal);
        boolean showRefLine = !o.chart.peRefval.equals("none");
        double refLineVal = 0.0;
        if (showRefLine) {
            try { refLineVal = Double.parseDouble(o.chart.peRefval.trim()); }
            catch (NumberFormatException e) { showRefLine = false; }
        }
        String labelCol = labelColor();
        String gridCol  = gridCssColor();

        // X-axis labels JS array (category mode: quoted strings; numeric mode: raw numbers)
        StringBuilder lblJs = new StringBuilder("[");
        if (numericX) {
            String[] xpa = seriesXpos[0].split("\\|", -1);
            // v3.6.0-s8b: no extrapolation. The ado now supplies every x from r(at);
            // an empty segment is a data error and must not be silently invented.
            for (int i = 0; i < xpa.length; i++) {
                if (i>0) lblJs.append(",");
                String xv = xpa[i].trim();
                lblJs.append(xv.isEmpty() || xv.equals(".") ? "null" : xv);
            }
        } else {
            for (int i = 0; i < xlabs.length; i++) { if (i>0) lblJs.append(","); lblJs.append("'").append(escJs(xlabs[i])).append("'"); }
        }
        lblJs.append("]");

        // Build JS array of x-labels for tooltip (used by callback)
        StringBuilder xlabArrJs = new StringBuilder("[");
        for (int i = 0; i < xlabs.length; i++) { if (i>0) xlabArrJs.append(","); xlabArrJs.append("'").append(escJs(xlabs[i])).append("'"); }
        xlabArrJs.append("]");

        // Y-axis range across all series (use lower/upper for full CI range)
        double yMin = Double.MAX_VALUE, yMax = -Double.MAX_VALUE;
        for (int si = 0; si < nSeries; si++) {
            String[] lo = (si < seriesLower.length ? seriesLower[si] : "").split("\\|", -1);
            String[] hi = (si < seriesUpper.length ? seriesUpper[si] : "").split("\\|", -1);
            for (String v : lo) { try { double d=Double.parseDouble(v.trim()); if(d<yMin)yMin=d; } catch(NumberFormatException ignored){} }
            for (String v : hi) { try { double d=Double.parseDouble(v.trim()); if(d>yMax)yMax=d; } catch(NumberFormatException ignored){} }
        }
        // Also include point estimates in range
        for (int si = 0; si < nSeries; si++) {
            for (String v : (si < seriesData.length ? seriesData[si] : "").split("\\|", -1)) {
                try { double d=Double.parseDouble(v.trim()); if(d<yMin)yMin=d; if(d>yMax)yMax=d; } catch(NumberFormatException ignored){}
            }
        }
        if (showRefLine) { if(refLineVal<yMin)yMin=refLineVal; if(refLineVal>yMax)yMax=refLineVal; }
        if (barMode) { if(0<yMin)yMin=0; if(0>yMax)yMax=0; }   // bar lengths are only meaningful from zero
        double yRange = (yMax == -Double.MAX_VALUE || yMin == Double.MAX_VALUE) ? 1.0 : yMax - yMin;
        double[] pmf = plotMarginFrac();          // l r b t as fractions
        // s8r: anchor value ticks to the data (nice floor/ceil), cushion outside them
        double[] nyR = niceRange(yMin, yMax, 6);
        String yTickExtra = "";
        if (showRefLine) yTickExtra = String.format(Locale.ROOT, "%.10g", refLineVal);
        if (!o.chart.pePexline.isEmpty()) { try { double pv = Double.parseDouble(o.chart.pePexline.trim()); yTickExtra += (yTickExtra.isEmpty() ? "" : ",") + String.format(Locale.ROOT, "%.10g", pv); } catch (NumberFormatException ignored) {} }
        // t2j fix8w (margins zero-forcing item): Stata's marginsplot value axis hugs the data --
        // the axis runs from just below the lowest CI bound to just above the highest, and the
        // ticks are the nice-step multiples INSIDE that range. The s8r rule extended the axis to
        // the nice floor/ceiling instead, so predicted prices with a lowest CI of 1,352 got an
        // axis from 0 (floor at step 2,000): a third of the plot was empty (side-by-side
        // r_mp_t4: ours 0, Stata 2,000). Bars keep zero because yMin was set to 0 above.
        double step = nyR[2];
        double yPadB = (yRange == 0) ? 0.5 : yRange * pmf[2], yPadT = (yRange == 0) ? 0.5 : yRange * pmf[3];
        double axMin = yMin - yPadB, axMax = yMax + yPadT;
        double tickLo = Math.ceil(axMin / step - 1e-9) * step, tickHi = Math.floor(axMax / step + 1e-9) * step;
        if (barMode && yMin == 0) { axMin = 0; tickLo = 0; }   // a bar baseline sits exactly on the axis floor
        String yTickFn = niceTicksJs(new double[]{ tickLo, tickHi, step }, yTickExtra);
        yMin = axMin; yMax = axMax;
        {   // s9j: key registrations (marginsplot)
            String ciLbl = (o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel) + "% CI";
            if (drawBand)  key("ci", ciLbl + " (band)", "swatch", colAtAlpha(ciCol.apply(0), 0.18), "", "");
            if (drawWhisk) key("ci", ciLbl, "whisker", ciCol.apply(0), "", "");
            if (!o.chart.peLevels2Val.isEmpty()) { if (drawBand) key("ci_inner", o.chart.peLevels2Val + "% CI (inner band)", "swatch", colAtAlpha(col(0), 0.38), "", ""); if (drawWhisk) key("ci_inner", o.chart.peLevels2Val + "% CI (inner)", "whisker_thick", col(0), "", ""); }
            if (showRefLine) key("refline", "Null (" + (refLineVal == Math.rint(refLineVal) ? String.valueOf((long) refLineVal) : String.format(Locale.ROOT, "%.4g", refLineVal)) + ")", "line", "#888888", "[5,5]", "");
            if (!o.chart.pePexline.isEmpty()) key("pexline", "Reference (" + o.chart.pePexline.trim() + ")", "line", "#e0b000", "[6,4]", "");
        }
        double cjAspect = (k >= 8 || numericX) ? 1.7 : (k <= 2 ? 1.3 : 1.5);

        StringBuilder pluginsSuffix = new StringBuilder();
        for (int si = 0; si < nSeries; si++) {
            String sc = ciCol.apply(si);   // t2g fix 3: CI layer colour
            String cf = colAtAlpha(sc, 0.18);
            String cb = colAtAlpha(sc, 0.55);
            String[] loA = (si < seriesLower.length ? seriesLower[si] : "").split("\\|", -1);
            String[] hiA = (si < seriesUpper.length ? seriesUpper[si] : "").split("\\|", -1);
            StringBuilder loJs = new StringBuilder("["), hiJs = new StringBuilder("[");
            for (int i=0;i<loA.length;i++){if(i>0){loJs.append(",");hiJs.append(",");}
                String lv=loA[i].trim(); loJs.append(lv.isEmpty()||lv.equals(".")?"null":lv);
                String hv=hiA[i].trim(); hiJs.append(hv.isEmpty()||hv.equals(".")?"null":hv);}
            loJs.append("]"); hiJs.append("]");

            // xpos array for numeric-x CI plugins
            String xposJsStr = "null";
            if (numericX) {
                String[] xpa2 = (si < seriesXpos.length ? seriesXpos[si] : seriesXpos[0]).split("\\|", -1);
                StringBuilder xpb = new StringBuilder("[");
                for (int i=0;i<xpa2.length;i++){if(i>0)xpb.append(","); String xv=xpa2[i].trim(); xpb.append(xv.isEmpty()||xv.equals(".")?"null":xv);}
                xpb.append("]"); xposJsStr = xpb.toString();
            }

            // For categorical: CI plugin uses getPixelForValue(i) -- category index on x-axis
            // For numeric: CI plugin uses getPixelForValue(xp[i]) -- actual coordinate
            String xExpr = numericX ? "xS.getPixelForValue(xp[i])" : "xS.getPixelForValue(i)";
            // v3.6.0-s8d: grouped bars -- whisker on each bar's own centre (dataset meta)
            if (barMode) xExpr = "((ch.getDatasetMeta(" + si + ").data[i]||{}).x||xS.getPixelForValue(i))";
            String xNull = numericX ? "||xp[i]===null" : "";
            String xpDecl = numericX ? ("var xp="+xposJsStr+";") : "";

            if (drawWhisk) {
                pluginsSuffix.append(",{id:'mpW").append(si)
                    .append("',afterDatasetsDraw:function(ch){var c=ch.ctx,xS=ch.scales.x,yS=ch.scales.y;")
                    .append("var lo=").append(loJs).append(",hi=").append(hiJs).append(";")
                    .append(xpDecl)
                    .append("c.save();c.beginPath();c.rect(ch.chartArea.left,ch.chartArea.top,ch.chartArea.width,ch.chartArea.height);c.clip();c.strokeStyle='").append(sc).append("';c.lineWidth=").append(ciWidthStr).append(";")
                    .append("for(var i=0;i<lo.length;i++){")
                    .append("if(lo[i]===null||hi[i]===null").append(xNull).append(")continue;")
                    .append("var xPx=").append(xExpr).append(";")
                    .append("var yL=yS.getPixelForValue(lo[i]),yH=yS.getPixelForValue(hi[i]);")
                    .append("c.beginPath();c.moveTo(xPx,yL);c.lineTo(xPx,yH);c.stroke();")
                    .append("c.beginPath();c.moveTo(xPx-5,yL);c.lineTo(xPx+5,yL);c.stroke();")
                    .append("c.beginPath();c.moveTo(xPx-5,yH);c.lineTo(xPx+5,yH);c.stroke();}")
                    .append("c.restore()}}");
            }
            if (drawBand && !connected) {
                // v3.6.0-s8k: points are NOT connected (categorical x, no connected option):
                // draw one rectangle per category. A ribbon would assert continuity
                // between categories that the chart itself does not draw (Wilke ch.9).
                pluginsSuffix.append(",{id:'mpA").append(si)
                    .append("',beforeDatasetsDraw:function(ch){var c=ch.ctx,xS=ch.scales.x,yS=ch.scales.y;")
                    .append("var lo=").append(loJs).append(",hi=").append(hiJs).append(";")
                    .append("var hw=(lo.length>1?Math.abs(xS.getPixelForValue(1)-xS.getPixelForValue(0)):60)*0.28;")
                    .append("c.save();c.beginPath();c.rect(ch.chartArea.left,ch.chartArea.top,ch.chartArea.width,ch.chartArea.height);c.clip();c.fillStyle='").append(cf).append("';")
                    .append("for(var i=0;i<lo.length;i++){if(lo[i]===null||hi[i]===null)continue;")
                    .append("var xPx=").append(barMode ? "((ch.getDatasetMeta(" + si + ").data[i]||{}).x||xS.getPixelForValue(i))" : "xS.getPixelForValue(i)").append(";")
                    .append("var yL=yS.getPixelForValue(lo[i]),yH=yS.getPixelForValue(hi[i]);")
                    .append("c.fillRect(xPx-hw,yH,2*hw,yL-yH);}")
                    .append("c.restore()}}");
            }
            if (drawBand && connected) {
                pluginsSuffix.append(",{id:'mpA").append(si)
                    .append("',beforeDatasetsDraw:function(ch){var c=ch.ctx,xS=ch.scales.x,yS=ch.scales.y;")
                    .append("var lo=").append(loJs).append(",hi=").append(hiJs).append(";")
                    .append(xpDecl)
                    .append("c.save();c.beginPath();c.rect(ch.chartArea.left,ch.chartArea.top,ch.chartArea.width,ch.chartArea.height);c.clip();c.fillStyle='").append(cf).append("';c.beginPath();")
                    .append("var st=true;for(var i=0;i<hi.length;i++){if(hi[i]===null").append(xNull).append(")continue;")
                    .append("var xPx=").append(xExpr).append(",yH=yS.getPixelForValue(hi[i]);")
                    .append("if(st){c.moveTo(xPx,yH);st=false;}else c.lineTo(xPx,yH);}")
                    .append("for(var i=lo.length-1;i>=0;i--){if(lo[i]===null").append(xNull).append(")continue;")
                    .append("var xPx=").append(xExpr).append(",yL=yS.getPixelForValue(lo[i]);")
                    .append("c.lineTo(xPx,yL);}c.closePath();c.fill();")
                    .append("c.restore()}}");
            }
        }

        // Check if levels2 has actual values (not just empty pipe-separated strings)
        boolean hasRealLevels2 = !o.chart.peLevels2Lo.isEmpty()
            && !o.chart.peLevels2Hi.isEmpty()
            && !o.chart.peLevels2Lo.replace("|","").replace("~","").trim().isEmpty();
        // (subtitle for nested CI levels is now set in DashboardBuilder before HTML generation)
        // Nested CI (levels2): inner whiskers using peLevels2Lo/Hi.
        // v3.6.0-s8b: one plugin per series (tilde-separated) and numericX-aware
        // x positioning (previously always used the category index -> wrong x on
        // continuous at() charts, and only series 0 was drawn).
        if (hasRealLevels2) {
            String[] lo2Series = o.chart.peLevels2Lo.split("~", -1);
            String[] hi2Series = o.chart.peLevels2Hi.split("~", -1);
            int n2 = Math.min(nSeries, Math.min(lo2Series.length, hi2Series.length));
            for (int si = 0; si < n2; si++) {
                String[] lo2arr = lo2Series[si].split("\\|", -1);
                String[] hi2arr = hi2Series[si].split("\\|", -1);
                if (lo2Series[si].replace("|","").trim().isEmpty()) continue;
                StringBuilder lo2Js = new StringBuilder("[");
                StringBuilder hi2Js = new StringBuilder("[");
                for (int i=0;i<lo2arr.length;i++) {
                    if(i>0){lo2Js.append(",");hi2Js.append(",");}
                    String lv=lo2arr[i].trim(); lo2Js.append(lv.isEmpty()||lv.equals(".")?"null":lv);
                    String hv=(i<hi2arr.length?hi2arr[i]:"").trim(); hi2Js.append(hv.isEmpty()||hv.equals(".")?"null":hv);
                }
                lo2Js.append("]"); hi2Js.append("]");
                String xpDecl2 = "";
                String xExpr2  = "xS.getPixelForValue(i)";
                if (numericX) {
                    String[] xpa2 = (si < seriesXpos.length ? seriesXpos[si] : seriesXpos[0]).split("\\|", -1);
                    StringBuilder xpb = new StringBuilder("[");
                    for (int i=0;i<xpa2.length;i++){if(i>0)xpb.append(","); String xv=xpa2[i].trim(); xpb.append(xv.isEmpty()||xv.equals(".")?"null":xv);}
                    xpb.append("]");
                    xpDecl2 = "var xp=" + xpb + ";";
                    xExpr2  = "xS.getPixelForValue(xp[i])";
                }
                String innerW = String.format(Locale.ROOT, "%.1f", ciWidthVal + 1.0);
                boolean outerBand = bandStyle;
                if (outerBand) {
                    // v3.6.0-s8l: nested BANDS -- inner CI is a darker band, no whiskers.
                    // Same geometry rule as the outer band: ribbon only when the points
                    // are connected or x is numeric, else one rectangle per category.
                    String cfIn = colAtAlpha(ciCol.apply(si), 0.38);
                    StringBuilder ap = new StringBuilder();
                    ap.append(",{id:'mpA_L2_").append(si).append("',beforeDatasetsDraw:function(ch){var c=ch.ctx,xS=ch.scales.x,yS=ch.scales.y;")
                      .append("var lo=").append(lo2Js).append(",hi=").append(hi2Js).append(";").append(xpDecl2)
                      .append("c.save();c.beginPath();c.rect(ch.chartArea.left,ch.chartArea.top,ch.chartArea.width,ch.chartArea.height);c.clip();c.fillStyle='").append(cfIn).append("';");
                    if (connected) {
                        ap.append("c.beginPath();var st=true;for(var i=0;i<hi.length;i++){if(hi[i]===null").append(numericX ? "||xp[i]===null" : "").append(")continue;")
                          .append("var xPx=").append(xExpr2).append(",yH=yS.getPixelForValue(hi[i]);if(st){c.moveTo(xPx,yH);st=false;}else c.lineTo(xPx,yH);}")
                          .append("for(var i=lo.length-1;i>=0;i--){if(lo[i]===null").append(numericX ? "||xp[i]===null" : "").append(")continue;")
                          .append("var xPx=").append(xExpr2).append(",yL=yS.getPixelForValue(lo[i]);c.lineTo(xPx,yL);}c.closePath();c.fill();");
                    } else {
                        ap.append("var hw=(lo.length>1?Math.abs(xS.getPixelForValue(1)-xS.getPixelForValue(0)):60)*0.28;")
                          .append("for(var i=0;i<lo.length;i++){if(lo[i]===null||hi[i]===null)continue;")
                          .append("var xPx=").append(barMode ? "((ch.getDatasetMeta(" + si + ").data[i]||{}).x||xS.getPixelForValue(i))" : xExpr2).append(";")
                          .append("var yL=yS.getPixelForValue(lo[i]),yH=yS.getPixelForValue(hi[i]);c.fillRect(xPx-hw,yH,2*hw,yL-yH);}");
                    }
                    ap.append("c.restore()}}");
                    pluginsSuffix.append(ap);
                    if (!drawWhisk) continue;
                }
                pluginsSuffix.append(",{id:'mpW_L2_").append(si).append("',afterDatasetsDraw:function(ch){")
                    .append("var c=ch.ctx,xS=ch.scales.x,yS=ch.scales.y;")
                    .append("var lo=").append(lo2Js).append(",hi=").append(hi2Js).append(";")
                    .append(xpDecl2)
                    .append("c.save();c.beginPath();c.rect(ch.chartArea.left,ch.chartArea.top,ch.chartArea.width,ch.chartArea.height);c.clip();c.strokeStyle='").append(ciCol.apply(si)).append("';c.lineWidth=").append(innerW).append(";")
                    .append("for(var i=0;i<lo.length;i++){")
                    .append("if(lo[i]===null||hi[i]===null")
                    .append(numericX ? "||xp[i]===null" : "").append(")continue;")
                    .append("var xPx=").append(xExpr2).append(";")
                    .append("var yL=yS.getPixelForValue(lo[i]),yH=yS.getPixelForValue(hi[i]);")
                    .append("c.beginPath();c.moveTo(xPx,yL);c.lineTo(xPx,yH);c.stroke();")
                    .append("c.beginPath();c.moveTo(xPx-8,yL);c.lineTo(xPx+8,yL);c.stroke();")
                    .append("c.beginPath();c.moveTo(xPx-8,yH);c.lineTo(xPx+8,yH);c.stroke();}")
                    .append("c.restore()}}");
            }
        }

        // pexline (vertical reference line on y-axis -- horizontal line at y=val)
        if (!o.chart.pePexline.isEmpty()) {
            try {
                double pv = Double.parseDouble(o.chart.pePexline.trim());
                String pc = gen.isDark() ? "rgba(255,200,100,0.8)" : "rgba(200,100,0,0.8)";
                pluginsSuffix.append(",{id:'mpPex',afterDatasetsDraw:function(ch){")
                    .append("var c=ch.ctx,yS=ch.scales.y,a=ch.chartArea;")
                    .append("var yPx=yS.getPixelForValue(").append(pv).append(");")
                    .append("c.save();c.beginPath();c.rect(ch.chartArea.left,ch.chartArea.top,ch.chartArea.width,ch.chartArea.height);c.clip();c.strokeStyle='").append(pc).append("';c.lineWidth=1.5;c.setLineDash([6,3]);")
                    .append("c.beginPath();c.moveTo(a.left,yPx);c.lineTo(a.right,yPx);c.stroke();c.restore()}}");
            } catch (NumberFormatException ignored) {}
        }
        // Reference line at refval (default 0 for marginsplot)
        if (showRefLine) {
            String rc = gen.isDark() ? "rgba(180,180,180,0.5)" : "rgba(100,100,100,0.3)";
            pluginsSuffix.append(",{id:'mpZ',afterDatasetsDraw:function(ch){")
                .append("var c=ch.ctx,yS=ch.scales.y,a=ch.chartArea;")
                .append("var yPx=yS.getPixelForValue(").append(refLineVal).append(");")
                .append("c.save();c.beginPath();c.rect(ch.chartArea.left,ch.chartArea.top,ch.chartArea.width,ch.chartArea.height);c.clip();c.strokeStyle='").append(rc).append("';c.lineWidth=").append(refLineWidthOr("1")).append(";c.setLineDash([4,2]);")
                .append("c.beginPath();c.moveTo(a.left,yPx);c.lineTo(a.right,yPx);c.stroke();c.restore()}}");
        }

        // Titles
        String yTitle = o.axes.ytitle.isEmpty() ? o.chart.mpYlab : o.axes.ytitle;
        String xTitle = o.axes.xtitle;

        // Build Chart.js config
        StringBuilder sb = new StringBuilder();
        sb.append("var _mainChart=new Chart(document.getElementById('").append(id).append("'),{\r\n");
        sb.append("  type:'").append(barMode ? "bar" : "line").append("',\r\n");
        if (isHoriz && !numericX) sb.append("  indexAxis:'y',\r\n");
        sb.append("  data:{\r\n");
        sb.append("    labels:").append(lblJs).append(",\r\n");
        sb.append("    datasets:[\r\n");
        for (int si = 0; si < nSeries; si++) {
            String sc = col(si);
            String[] ca = (si < seriesData.length ? seriesData[si] : "").split("\\|", -1);
            StringBuilder djs = new StringBuilder("[");
            if (numericX) {
                String[] xpa3 = (si < seriesXpos.length ? seriesXpos[si] : seriesXpos[0]).split("\\|", -1);
                for (int i=0;i<ca.length;i++){if(i>0)djs.append(",");
                    String xv = i<xpa3.length ? xpa3[i].trim() : "";
                    String yv = ca[i].trim();
                    if (xv.isEmpty() || xv.equals(".")) xv = "null";
                    if (yv.isEmpty() || yv.equals(".")) yv = "null";
                    djs.append("{x:").append(xv).append(",y:").append(yv).append("}");}
            } else {
                for (int i=0;i<ca.length;i++){if(i>0)djs.append(","); String dv=ca[i].trim(); djs.append(dv.equals(".")||dv.isEmpty()?"null":dv);}
            }
            djs.append("]");
            // Series label: use seriesNames if meaningful, else "Margin" for single series
            String sl = (si<seriesNames.length && !seriesNames[si].isEmpty() && !seriesNames[si].equals("0"))
                ? escJs(seriesNames[si]) : (nSeries==1 ? "Margin" : "Series "+(si+1));
            // v3.6.0-s8b: solid (alpha 1) line/point colour -- the 0.85 palette alpha
            // pushed the orange series below WCAG 3:1 on white (VIZ_STANDARD COL-BG)
            if (barMode) {
                sb.append("      {label:'").append(sl).append("',data:").append(djs)
                  .append(",borderColor:'").append(colAtAlpha(sc, 1.0)).append("',backgroundColor:'").append(colAtAlpha(sc, 0.75))
                  .append("',borderWidth:1,borderRadius:2,maxBarThickness:60}")
                  .append(si < nSeries-1 ? "," : "").append("\r\n");
            } else
            sb.append("      {label:'").append(sl).append("',data:").append(djs)
                .append(",borderColor:'").append(colAtAlpha(sc, 1.0)).append("',backgroundColor:'").append(colAtAlpha(sc, 1.0))
                .append("',pointRadius:").append(pSize).append(",pointStyle:'").append(pStyle)
                .append("',pointHoverRadius:7,borderWidth:").append(connected?lineWidthOr("2"):"0")
                .append(",tension:").append(smoothOr("0")).append(",showLine:").append(connected).append(",fill:false}")
                .append(si < nSeries-1 ? "," : "").append("\r\n");
        }
        // mpLegend (v3.6.0-s8b): inline legend for nested CI levels, drawn in the
        // plot area. Box width is measured from the longest label (was fixed 152px).
        // Position is data-driven: top-right unless the right 40% of the data sits in
        // the top 45% of the plot, in which case top-left.
        if (CANVAS_CI_KEY && hasRealLevels2) {
            String lblColor = gen.isDark() ? "rgba(220,220,220,0.85)" : "rgba(60,60,60,0.85)";
            String boxFill  = gen.isDark() ? "rgba(30,30,30,0.85)"    : "rgba(255,255,255,0.88)";
            String sc0 = col(0);
            String lev2label = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel;
            String lev1label = o.chart.peLevels2Val.isEmpty() ? "90" : o.chart.peLevels2Val;
            String innerWl = String.format(Locale.ROOT, "%.1f", ciWidthVal + 1.0);
            boolean outerIsBand = bandStyle;
            boolean both = bandStyle && drawWhisk;
            String whiskGlyph1 = "ctx.strokeStyle='" + sc0 + "';ctx.lineWidth=" + ciWidthStr + ";"
                  + "ctx.beginPath();ctx.moveTo(x0,y1);ctx.lineTo(x0+20,y1);ctx.stroke();"
                  + "ctx.beginPath();ctx.moveTo(x0,y1-5);ctx.lineTo(x0,y1+5);ctx.stroke();"
                  + "ctx.beginPath();ctx.moveTo(x0+20,y1-5);ctx.lineTo(x0+20,y1+5);ctx.stroke();";
            String outerSwatch = outerIsBand
                ? "ctx.fillStyle='" + colAtAlpha(sc0, 0.18) + "';ctx.fillRect(x0,y1-6,20,12);ctx.strokeStyle='" + colAtAlpha(sc0, 0.6) + "';ctx.lineWidth=1;ctx.strokeRect(x0,y1-6,20,12);" + (both ? whiskGlyph1 : "")
                : "ctx.strokeStyle='" + sc0 + "';ctx.lineWidth=" + ciWidthStr + ";"
                  + "ctx.beginPath();ctx.moveTo(x0,y1);ctx.lineTo(x0+20,y1);ctx.stroke();"
                  + "ctx.beginPath();ctx.moveTo(x0,y1-5);ctx.lineTo(x0,y1+5);ctx.stroke();"
                  + "ctx.beginPath();ctx.moveTo(x0+20,y1-5);ctx.lineTo(x0+20,y1+5);ctx.stroke();";
            pluginsSuffix.append(",{id:'mpLegend',afterDraw:function(chart){"
                + "var ctx=chart.ctx,a=chart.chartArea;"
                + "var t1='" + lev2label + "% CI" + (outerIsBand ? (both ? " (outer band + whisker)" : " (outer band)") : "") + "',t2='" + lev1label + "% CI (inner" + (outerIsBand ? (both ? " band + whisker" : " band") : "") + ")';"
                + "ctx.save();ctx.beginPath();ctx.rect(chart.chartArea.left,chart.chartArea.top,chart.chartArea.width,chart.chartArea.height);ctx.clip();ctx.font='11px sans-serif';"
                + "var tw=Math.max(ctx.measureText(t1).width,ctx.measureText(t2).width);"
                + "var bw=tw+38,bh=34;"
                + "var _dp=(chart.data.datasets[0]||{data:[]}).data;"
                + "var _rp=_dp.filter(function(v,i){return i>=Math.floor(_dp.length*0.6);});"
                + "var _mY=_rp.reduce(function(m,v){var y=(v&&typeof v==='object')?v.y:v;return (y===null||y===undefined)?m:Math.max(m,y);},chart.scales.y.min);"
                + "var _ul=chart.scales.y.getPixelForValue(_mY)<(a.top+(a.bottom-a.top)*0.45);"
                + "var x0=_ul?(a.left+8):(a.right-bw-4),y1=a.top+18,y2=a.top+34;"
                + "ctx.fillStyle='" + boxFill + "';ctx.fillRect(x0-4,a.top+5,bw,bh);"
                + "ctx.strokeStyle='rgba(200,200,200,0.4)';ctx.lineWidth=0.5;ctx.strokeRect(x0-4,a.top+5,bw,bh);"
                + outerSwatch
                + "ctx.fillStyle='" + lblColor + "';ctx.textAlign='left';ctx.textBaseline='alphabetic';"
                + "ctx.fillText(t1,x0+26,y1+4);"
                + (outerIsBand
                    ? "ctx.fillStyle='" + colAtAlpha(sc0, 0.38) + "';ctx.fillRect(x0,y2-6,20,12);ctx.strokeStyle='" + colAtAlpha(sc0, 0.6) + "';ctx.lineWidth=1;ctx.strokeRect(x0,y2-6,20,12);"
                      + (both ? "ctx.strokeStyle='" + sc0 + "';ctx.lineWidth=" + innerWl + ";ctx.beginPath();ctx.moveTo(x0,y2);ctx.lineTo(x0+20,y2);ctx.stroke();ctx.beginPath();ctx.moveTo(x0,y2-6);ctx.lineTo(x0,y2+6);ctx.stroke();ctx.beginPath();ctx.moveTo(x0+20,y2-6);ctx.lineTo(x0+20,y2+6);ctx.stroke();" : "")
                    : "ctx.strokeStyle='" + sc0 + "';ctx.lineWidth=" + innerWl + ";"
                    + "ctx.beginPath();ctx.moveTo(x0,y2);ctx.lineTo(x0+20,y2);ctx.stroke();"
                    + "ctx.beginPath();ctx.moveTo(x0,y2-6);ctx.lineTo(x0,y2+6);ctx.stroke();"
                    + "ctx.beginPath();ctx.moveTo(x0+20,y2-6);ctx.lineTo(x0+20,y2+6);ctx.stroke();")
                + "ctx.fillStyle='" + lblColor + "';ctx.fillText(t2,x0+26,y2+4);"
                + "ctx.restore()}}");
        }
        sb.append("    ]\r\n  },\r\n");
        sb.append("  options:{\r\n");
        sb.append("    responsive:true,maintainAspectRatio:true,resizeDelay:150,\r\n");
        sb.append("    aspectRatio:").append(String.format(Locale.ROOT, "%.2f", cjAspect)).append(",\r\n");
        sb.append("    plugins:{\r\n");
        // t2j fix8 (A9): honor legend(position)/legend(none); was position-less (defaulted top).
        sb.append("      legend:{display:").append((nSeries > 1 && !o.chart.legend.equals("none")) ? "true" : "false")
          .append(",position:'").append(o.chart.legend.isEmpty() ? "top" : o.chart.legend).append("'")
          // t2j fix8 (deep-dive r2): add a labels:{} object only when legend styling is
          // set (marginsPlot previously had none, so legcolor/legbgcolor/etc. were dropped).
          .append(postEstLegendExtra().isEmpty() ? "" : ",labels:{" + postEstLegendExtra().substring(1) + "}").append("},\r\n");
        // Tooltip (v3.6.0-s8b): mirrors coefPlot's _cpTip pattern.
        //   title : "<xvar> = <value>" (numeric x) or the category label
        //   body  : "<series>: value" + "<lev>% CI: [lo, hi]" (+ inner CI when levels()),
        //           or "Not estimable" for null cells
        // _mpTip[si][i] = {v,lo,hi,lo2,hi2}; built here so the callback needs no parsing.
        StringBuilder tipJs = new StringBuilder("[");
        String[] lo2SeriesT = hasRealLevels2 ? o.chart.peLevels2Lo.split("~", -1) : new String[0];
        String[] hi2SeriesT = hasRealLevels2 ? o.chart.peLevels2Hi.split("~", -1) : new String[0];
        for (int si = 0; si < nSeries; si++) {
            if (si > 0) tipJs.append(",");
            String[] vA  = (si < seriesData.length  ? seriesData[si]  : "").split("\\|", -1);
            String[] lA  = (si < seriesLower.length ? seriesLower[si] : "").split("\\|", -1);
            String[] hA  = (si < seriesUpper.length ? seriesUpper[si] : "").split("\\|", -1);
            String[] l2A = (si < lo2SeriesT.length  ? lo2SeriesT[si]  : "").split("\\|", -1);
            String[] h2A = (si < hi2SeriesT.length  ? hi2SeriesT[si]  : "").split("\\|", -1);
            tipJs.append("[");
            for (int i = 0; i < vA.length; i++) {
                if (i > 0) tipJs.append(",");
                tipJs.append("{v:").append(numOrNull(vA, i))
                     .append(",lo:").append(numOrNull(lA, i))
                     .append(",hi:").append(numOrNull(hA, i))
                     .append(",lo2:").append(numOrNull(l2A, i))
                     .append(",hi2:").append(numOrNull(h2A, i)).append("}");
            }
            tipJs.append("]");
        }
        tipJs.append("]");
        String levOuter = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel;
        String levInner = o.chart.peLevels2Val.isEmpty() ? "90" : o.chart.peLevels2Val;
        // t2j fix8w: categorical x labels are now the bare level ("1"), so the tooltip title
        // names the variable too: "Repair record 1978: 1" (numeric x keeps "weight = 3,010")
        String titlePrefix = xTitle.isEmpty() ? "" : escJs(xTitle) + (numericX ? " = " : ": ");
        sb.append("      tooltip:{").append(postEstTooltipPrefix()).append("callbacks:{\r\n");
        sb.append("        title:function(ctx){\r\n");
        sb.append("          var xlabs=").append(xlabArrJs).append(";\r\n");
        sb.append("          var i=ctx[0].dataIndex;var xl=xlabs[i];\r\n");
        sb.append("          if(xl===undefined||xl===''){var px=ctx[0].parsed.x;xl=(px===undefined||px===null)?('Point '+(i+1)):String(px);}\r\n");
        sb.append("          return '").append(titlePrefix).append("'+xl;\r\n");
        sb.append("        },\r\n");
        sb.append("        label:function(ctx){\r\n");
        sb.append("          var T=").append(tipJs).append(";\r\n");
        sb.append("          var f=function(x){return _spkFmt(x);};\r\n");
        sb.append("          var si=ctx.datasetIndex,i=ctx.dataIndex,s=ctx.dataset.label;\r\n");
        sb.append("          var d=(T[si]&&T[si][i])?T[si][i]:{v:null};\r\n");
        sb.append("          var pre=(s&&s!=='Margin'&&T.length>1)?(s+': '):'';\r\n");
        sb.append("          if(d.v===null)return pre+'Not estimable';\r\n");
        sb.append("          var out=[pre+'").append(nSeries > 1 ? "" : "Margin: ").append("'+f(d.v)];\r\n");
        sb.append("          if(d.lo!==null&&d.hi!==null)out.push('").append(levOuter).append("% CI: ['+f(d.lo)+', '+f(d.hi)+']');\r\n");
        if (hasRealLevels2)
        sb.append("          if(d.lo2!==null&&d.hi2!==null)out.push('").append(levInner).append("% CI: ['+f(d.lo2)+', '+f(d.hi2)+']');\r\n");
        sb.append("          return out;\r\n");
        sb.append("        }\r\n");
        sb.append("      }}\r\n");
        sb.append("    },\r\n");
        sb.append("    scales:{\r\n");
        if (numericX) {
            // s8o: 5% padding beyond the first/last x so end-point whiskers and caps
            // do not sit on (or get clipped by) the axis line (user-reported overlap)
            double xMinV = Double.MAX_VALUE, xMaxV = -Double.MAX_VALUE;
            for (String xseg : seriesXpos[0].split("\\|", -1)) { try { double d = Double.parseDouble(xseg.trim()); if (d < xMinV) xMinV = d; if (d > xMaxV) xMaxV = d; } catch (NumberFormatException ignored) {} }
            String xLim = "";
            String xTickFn = "";
            if (xMinV < xMaxV) {
                // s8r: ticks anchored to the data (nice floor/ceil), cushion OUTSIDE the ticks
                double[] nx = niceRange(xMinV, xMaxV, 7);
                double xr = nx[1] - nx[0];
                xLim = ",min:" + String.format(Locale.ROOT, "%.6g", nx[0] - xr * pmf[0]) + ",max:" + String.format(Locale.ROOT, "%.6g", nx[1] + xr * pmf[1]);
                xTickFn = niceTicksJs(nx, "");
            }
            sb.append("      x:{type:'linear'").append(xLim).append(",grid:{color:'").append(gridCol).append("'},ticks:{color:'").append(labelCol).append("',includeBounds:false}").append(xTickFn);
            if (!xTitle.isEmpty()) sb.append(",title:{display:true,text:'").append(escJs(xTitle)).append("',color:'").append(labelCol).append("'}");
            sb.append("},\r\n");
        } else {
            // s8o: offset:true centres each category in its own slot (half-slot padding at
            // both ends) so the first/last CI whisker is never drawn on the axis line
            sb.append("      x:{offset:true,grid:{color:'").append(gridCol).append("',offset:false},ticks:{color:'").append(labelCol).append("'}");
            if (!xTitle.isEmpty()) sb.append(",title:{display:true,text:'").append(escJs(xTitle)).append("',color:'").append(labelCol).append("'}");
            sb.append("},\r\n");
        }
        sb.append("      y:{min:").append(String.format(Locale.ROOT, "%.4f",yMin)).append(",max:").append(String.format(Locale.ROOT, "%.4f",yMax))
            .append(",grid:{color:'").append(gridCol).append("'},ticks:{color:'").append(labelCol).append("',includeBounds:false}").append(yTickFn);
        if (!yTitle.isEmpty()) sb.append(",title:{display:true,text:'").append(escJs(yTitle)).append("',color:'").append(labelCol).append("'}");
        sb.append("}\r\n");
        sb.append("    }\r\n  },\r\n");
        String plugs = pluginsSuffix.length() > 0 ? pluginsSuffix.substring(1) : "";
        sb.append("  plugins:[").append(plugs).append("]\r\n");
        sb.append("});\r\n");
        return sb.toString();
    }


}
