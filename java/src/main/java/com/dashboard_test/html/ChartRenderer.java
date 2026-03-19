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
 */
class ChartRenderer {

    private final DashboardOptions o;
    private final HtmlGenerator    gen;  // shared utilities
    private final DatasetBuilder   dsb;  // dataset/label builders

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
        if (stacked) sb.append("stacked:true,");
        // v2.2.0: 100% stacked -- clamp value axis to [0, 100]
        boolean isValueAxis = (!isHbar && !isX) || (isHbar && isX);
        if (o.chart.stack100 && isValueAxis) {
            sb.append("min:0,max:100,beginAtZero:true,");
        }
        if (beginZero) sb.append("beginAtZero:true,");
        // Log scale: Chart.js cannot draw from 0; set a sensible min if none given
        if (isLog && rMin.isEmpty()) {
            sb.append("min:1,");
        } else if (!rMin.isEmpty()) {
            sb.append("min:").append(rMin).append(",max:").append(rMax).append(",");
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
        if (!isX && !o.axes.ygrace.isEmpty() && o.axes.yrangeMin.isEmpty()) {
            String gv = o.axes.ygrace.trim();
            sb.append("grace:");
            if (gv.endsWith("%")) sb.append("'").append(gv).append("',");
            else                  sb.append(gv).append(",");
        }
        sb.append("ticks:{").append(axisTickStyleCfg(isX));
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
            sb.append(",callback:function(v,i,t){var n=Number(v.toString());return n===Math.pow(10,Math.round(Math.log10(n)))?n.toLocaleString():'';}");
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
        String xTitleStr = !o.axes.xtitle.isEmpty() ? o.axes.xtitle
            : (_ov != null ? _ov.getDisplayName() : "");
        String yTitleStr = !o.axes.ytitle.isEmpty() ? o.axes.ytitle
            : (!_nv.isEmpty() && (_ov != null || _nv.size() == 1)
                ? _nv.get(0).getDisplayName() : "");

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
        String xScaleCfg = buildAxisConfig("x", xTitleStr, o.chart.horizontal, isBar, isLine, nCat);
        String yScaleCfg = buildAxisConfig("y", yTitleStr, o.chart.horizontal, isBar);
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
            + "formatter:function(v){return v==null?'':parseFloat(v).toFixed(1);}}"
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
            + "    responsive:true,maintainAspectRatio:true,"+aspectCfg+"\n"
            + (o.chart.horizontal ? "    indexAxis:'y',\n" : "")
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

        String ds = data.hasOver()
            ? scatterOverDs(data, xv, yv, labelVar, posVar)
            : scatterSingleDs(xv, yv, labelVar, posVar);

        // v3.5.57: fit line datasets appended after scatter points
        // v3.5.63: strip trailing comma from ds before appending fitDs.
        // scatterOverDs() ends each dataset with "," including the last one.
        // buildFitDatasets() starts with "," so combining gives ",," = invalid JS.
        String dsCleaned = ds.endsWith(",") ? ds.substring(0, ds.length()-1) : ds;
        String fitDs = buildFitDatasets(xv, yv, col(0));

        // mlabel datalabels plugin config
        String mlabelCfg = buildMlabelCfg(labelVar, posVar, data);

        // Use buildAxisConfig so scatter gets all tick/grid/range options consistently
        String xScaleCfg = buildAxisConfig("x", xl, false, false);
        String yScaleCfg = buildAxisConfig("y", yl, false, false);

        String easingCfg = o.chart.easing.isEmpty() ? "" : ",easing:'"+o.chart.easing+"'";
        String delayCfg  = o.chart.animdelay.isEmpty() ? "" : ",delay:"+o.chart.animdelay;

        String annotCfg = buildAnnotationConfig(true);
        return "new Chart(document.getElementById('"+id+"'), {\n"
            + "  type:'scatter',\n"
            + "  data:{datasets:["+dsCleaned+fitDs+"]},\n"
            + "  options:{\n"
            + "    responsive:true,maintainAspectRatio:true,\n"
            + (o.chart.aspect.isEmpty()?"":"    aspectRatio:"+o.chart.aspect+",\n")
            + "    animation:{duration:"+animDuration()+easingCfg+delayCfg+"},\n"
            + buildPadding()
            + "    plugins:{legend:"+buildLegendConfig()+","+buildScatterTooltipCfg(xl,yl)
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

            String fitColor = baseColor.replace("0.85","0.75").replace(",1)",",0.9)");
            String ciColor  = baseColor.replace("0.85","0.15").replace(",1)",",0.15)");
            // Use FitResult label suffix when available (e.g. " (MA-7)"), else fallback
            String suffix = (!javaLabelHolder[0].isEmpty()) ? javaLabelHolder[0] : " (" + o.chart.fit + ")";

            // CI bands (only when no over())
            if (o.chart.fitci && !o.chart.fitCiUpper.isEmpty()
                              && !o.chart.fitCiLower.isEmpty()) {
                String upData = parseXYString(o.chart.fitCiUpper);
                String loData = parseXYString(o.chart.fitCiLower);
                sb.append(",{label:'95% CI (upper)',type:'line',data:").append(upData)
                  .append(",borderColor:'").append(fitColor).append("'")
                  .append(",backgroundColor:'").append(ciColor).append("'")
                  .append(",borderWidth:0,pointRadius:0,fill:'+1'")
                  .append(",datalabels:{display:false},order:3}");
                sb.append(",{label:'95% CI (lower)',type:'line',data:").append(loData)
                  .append(",borderColor:'").append(fitColor).append("'")
                  .append(",backgroundColor:'").append(ciColor).append("'")
                  .append(",borderWidth:0,pointRadius:0,fill:'-1'")
                  .append(",datalabels:{display:false},order:3}");
            }

            sb.append(",{label:'").append(escJs(yv.getDisplayName())).append(escJs(suffix))
              .append("',type:'line',data:").append(lineData)
              .append(",borderColor:'").append(fitColor).append("'")
              .append(",backgroundColor:'transparent'")
              .append(",borderWidth:2,borderDash:[6,3],pointRadius:0,fill:false")
              .append(",datalabels:{display:false},order:2}");

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
                String fitColor = gColor.replace("0.85","0.75").replace(",1)",",0.9)");
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
                  .append(",borderWidth:2,borderDash:[6,3],pointRadius:0,fill:false")
                  .append(",datalabels:{display:false},order:2}");
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
        for (int i = 0; i < n; i++) {
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
            + buildPointConfig(0)+"}";
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
            for (int i = 0; i < n; i++) {
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
              .append(buildPointConfig(palIdx)).append("},");
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

        String datasets = data.hasOver()
            ? bubbleOverDs(data, xv, yv, rv, rmin, rrange)
            : bubbleSingleDs(xv, yv, rv, rmin, rrange);

        String xScaleCfg = buildAxisConfig("x", xl, false, false);
        String yScaleCfg = buildAxisConfig("y", yl, false, false);
        String easingCfg = o.chart.easing.isEmpty() ? "" : ",easing:'"+o.chart.easing+"'";
        String delayCfg  = o.chart.animdelay.isEmpty()     ? "" : ",delay:"+o.chart.animdelay;

        String annotCfg = buildAnnotationConfig(true);
        return "new Chart(document.getElementById('"+id+"'), {\n"
            + "  type:'bubble',\n"
            + "  data:{datasets:["+datasets+"]},\n"
            + "  options:{\n"
            + "    responsive:true,maintainAspectRatio:true,\n"
            + (o.chart.aspect.isEmpty()?"":"    aspectRatio:"+o.chart.aspect+",\n")
            + "    animation:{duration:"+animDuration()+easingCfg+delayCfg+"},\n"
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
        for (int i=0;i<n;i++) {
            Object x=xv.getValues().get(i), y=yv.getValues().get(i), r=rv.getValues().get(i);
            if (x!=null&&y!=null&&r!=null) {
                double rN = 5+35*(((Number)r).doubleValue()-rmin)/rrange;
                pts.append("{x:").append(x).append(",y:").append(y).append(",r:").append(String.format("%.1f",rN)).append("},");
            }
        }
        return "{label:'"+escJs(xv.getDisplayName()+" vs "+yv.getDisplayName()+" (size="+rv.getDisplayName()+")")+"',"
            + "data:["+pts+"],backgroundColor:'"+col(0)+"'}";
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
            for (int i=0;i<n;i++) {
                Object gval=ov.getValues().get(i);
                if (!gc.equals(sdz(gval==null?"":String.valueOf(gval)))) continue;
                Object x=xv.getValues().get(i), y=yv.getValues().get(i), r=rv.getValues().get(i);
                if (x!=null&&y!=null&&r!=null) {
                    double rN=5+35*(((Number)r).doubleValue()-rmin)/rrange;
                    pts.append("{x:").append(x).append(",y:").append(y).append(",r:").append(String.format("%.1f",rN)).append("},");
                }
            }
            sb.append("{label:'").append(escJs(ov.getDisplayName()+" = "+g)).append("',")
              .append("data:[").append(pts).append("],")
              .append("backgroundColor:'").append(col(palIdx)).append("',")
              .append("hoverBackgroundColor:'").append(colS(palIdx)).append("'},");
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
            lblJs.append("'").append(String.format("%.0f", lo)).append("',");
            datJs.append(String.format("%.6f", yVal)).append(",");
            ttRng.append("'[").append(String.format("%.2f", lo))
                 .append(", ").append(String.format("%.2f", hi)).append(")',");
            // v2.6.1: per-bin count so tooltip shows obs in this bin, not total n
            ttCnt.append((int) cnt).append(",");
        }
        // Append the final right edge as the last tick label (n+1 total labels)
        double lastHi = bins[bins.length - 1][2];
        lblJs.append("'").append(String.format("%.0f", lastHi)).append("'");

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
        String ttRangesVar = "var _ttRanges_" + safeId + "=["
            + ttRng.toString() + "];\n"
            + "var _ttCounts_" + safeId + "=["
            + ttCnt.toString() + "];\n";
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
            + "    responsive:true,maintainAspectRatio:true,\n"
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
            lblJs.append("'").append(String.format("%.0f", lo)).append("',");
            datJs.append(String.format("%.6f", yVal)).append(",");
            rngJs.append("'[").append(String.format("%.2f", lo))
                 .append(", ").append(String.format("%.2f", hi)).append(")',");
            cntJs.append((int) cnt).append(","); // v2.6.1
        }
        // Append the final right-edge tick label
        double lastHi = bins[bins.length - 1][2];
        lblJs.append("'").append(String.format("%.0f", lastHi)).append("'");

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
            String.format("%.4f", xMin), // [3] xMin
            String.format("%.4f", xMax), // [4] xMax
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
            + "    responsive:true,maintainAspectRatio:true,\n"
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
            + "    responsive:true,maintainAspectRatio:true,\n"
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

    // -- CI dataset builders (reusable by filter data builder) -----------------

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
                valJs.append(String.format("%.4f", v)).append(",");
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
                valJs.append(String.format("%.4f", v)).append(",");
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
                valJs.append(String.format("%.4f", v)).append(",");
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

        return "new Chart(document.getElementById('"+id+"'), {\n"
            + "  type:'"+(donut?"doughnut":"pie")+"',\n"
            + piePluginsArr
            + "  data:{\n"
            + "    labels:["+lblJs+"],\n"
            + "    datasets:[{label:'"+escJs(datasetLabel)+"',"
            + "      data:["+valJs+"],backgroundColor:["+bgJs+"],borderColor:["+brdJs+"],"
            + "      borderWidth:"+borderW+",hoverOffset:"+hoverOff+"}]\n"
            + "  },\n"
            + "  options:{\n"
            + "    responsive:true,\n"
            + "    cutout:"+cutoutVal+",\n"
            + "    rotation:"+rotVal+",\n"
            + "    circumference:"+circumVal+",\n"
            + "    animation:{duration:"+animDuration()+easingCfg+"},\n"
            + buildPadding()
            + "    plugins:{\n"
            + "      legend:{display:"+(legendPos.equals("none")?"false":"true")+","
            + "position:'"+legendPos+"',labels:{color:'"+labelColor()+"',padding:16,boxWidth:14"
            + (o.chart.legendsize.isEmpty() ? "" : ",font:{size:"+o.chart.legendsize+"}")
            + "}"
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
        switch (o.stats.stat.toLowerCase()) {
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
     * Tooltip for bar/line/area charts.
     * Title = x-axis label (group name or variable name).
     * Per dataset: variable name, stat label + value.
     * AfterBody: N from _dashData.
     */
    private String buildBarLineTooltipCfg() {
        // For 100% stacked bar: show pct + raw value on separate lines
        if (o.chart.stack100) return buildStack100TooltipCfg();

        String ttMode  = o.chart.tooltipmode.equals("index") ? "nearest" : o.chart.tooltipmode;
        String ttPos   = o.chart.tooltippos.equals("average") ? "nearest" : o.chart.tooltippos;
        String statLbl = escJs(tooltipStatLabel());
        String fmtV    = tooltipNumFmt("v");
        return "tooltip:{" + tooltipStylePrefix() + "mode:'" + ttMode + "',position:'" + ttPos + "',"
            + "callbacks:{"
            // Title: the x-axis group label
            + "title:function(items){"
            +   "return items.length?items[0].label:'';"
            + "},"
            // Label: variable name on its own line, then stat + value
            + "label:function(ctx){"
            +   "var v=ctx.parsed.y;"
            +   "if(v===null||v===undefined)return '';"
            +   "var lbl=ctx.dataset.label||'';"
            +   "var val=" + fmtV + ";"
            +   "return ['  '+lbl,'  " + statLbl + ":  '+val];"
            + "},"
            // AfterBody: N shown once after all dataset labels
            + "afterBody:function(items){"
            +   "if(!items.length)return [];"
            +   "var k1=document.getElementById('_f1')?document.getElementById('_f1').value:'ALL';"
            +   "var k2=document.getElementById('_f2')?document.getElementById('_f2').value:'ALL';"
            +   "var nd=typeof _dashData!=='undefined'?_dashData[k1+'||'+k2]:null;"
            +   "if(nd&&nd.n!==undefined)return ['  N:  '+nd.n];"
            +   "return [];"
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
        return "tooltip:{" + tooltipStylePrefix() + "mode:'point',position:'" + ttPos + "',"
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
        return "tooltip:{" + tooltipStylePrefix() + "mode:'point',position:'" + ttPos + "',"
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
                +   "return ["
                +     "'  '+ctx.label+':  '+(" + fmtPct + ")"
                +   "];"
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
        String fmtV  = tooltipNumFmt("v");
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
        String ttMode = o.chart.tooltipmode.equals("index") ? "nearest" : o.chart.tooltipmode;
        String ttPos  = o.chart.tooltippos.equals("average") ? "nearest" : o.chart.tooltippos;
        String ciLvl  = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel.trim();
        String fmtMean = tooltipNumFmt("mean");
        String fmtLo   = tooltipNumFmt("lo");
        String fmtHi   = tooltipNumFmt("hi");
        return "tooltip:{" + tooltipStylePrefix() + "mode:'" + ttMode + "',position:'" + ttPos + "',"
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
        String ttMode = o.chart.tooltipmode.equals("index") ? "nearest" : o.chart.tooltipmode;
        String ttPos  = o.chart.tooltippos.equals("average") ? "nearest" : o.chart.tooltippos;
        String ciLvl  = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel.trim();
        String fmtMean = tooltipNumFmt("mean");
        String fmtLo   = tooltipNumFmt("lo");
        String fmtHi   = tooltipNumFmt("hi");
        return "tooltip:{" + tooltipStylePrefix() + "mode:'" + ttMode + "',position:'" + ttPos + "',"
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
        String ttMode  = o.chart.tooltipmode.equals("index") ? "index" : o.chart.tooltipmode;
        String ttPos   = o.chart.tooltippos.equals("average") ? "average" : o.chart.tooltippos;
        String fmtRaw  = tooltipNumFmt("raw");
        String fmtTot  = tooltipNumFmt("total");
        String statLbl = escJs(tooltipStatLabel());
        // v3.5.14: with indexAxis:'y' (horizontal bar), Chart.js puts the numeric
        // value in ctx.parsed.x (not ctx.parsed.y). Use the correct axis.
        String parsedPct = o.chart.horizontal ? "ctx.parsed.x" : "ctx.parsed.y";
        return "tooltip:{" + tooltipStylePrefix() + "mode:'" + ttMode + "',position:'" + ttPos + "',"
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
        if (!o.chart.legendsize.isEmpty())
            sb.append(",font:{size:").append(o.chart.legendsize).append("}");
        if (!o.chart.legendboxheight.isEmpty())
            sb.append(",boxHeight:").append(o.chart.legendboxheight);
        if (!o.style.legBgColor.isEmpty())
            sb.append(",backgroundColor:'").append(o.style.legBgColor).append("'");
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
                    try { v = String.format("%.6f", binEdgeToIndex(Double.parseDouble(v), histBinEdges)); }
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
                    try { lo = String.format("%.6f", binEdgeToIndex(Double.parseDouble(parts[0]), histBinEdges)); }
                    catch (NumberFormatException e) { lo = parts[0]; }
                    try { hi = String.format("%.6f", binEdgeToIndex(Double.parseDouble(parts[1]), histBinEdges)); }
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
          + "      ctx.save();\n"
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
          + "        ctx.save();\n"
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
          + "        ctx.save();\n"
          + "        var q1px=valScale.getPixelForValue(vd.q1);\n"
          + "        var q3px=valScale.getPixelForValue(vd.q3);\n"
          + "        ctx.fillStyle=_vIsDark?'rgba(255,255,255,0.18)':'rgba(0,0,0,0.15)';\n"
          + "        ctx.strokeStyle=_vIsDark?'rgba(255,255,255,0.5)':'rgba(0,0,0,0.4)';\n"
          + "        ctx.lineWidth=1.5;\n"
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
          + "        ctx.save();\n"
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
          + "        ctx.save();\n"
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
          + "    var cursor=_vIsHoriz?valScale.getValueForPixel(mx):valScale.getValueForPixel(my);\n"
          + "    if(cursor<vd.min-1||cursor>vd.max+1)return;\n"
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
          + "    ctx.save();\n"
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
          + "      ctx.save();\n"
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
            + "      responsive:true,maintainAspectRatio:true," + aspectCfg + "\n"
            // duration:0 -- we drive all animation ourselves via RAF
            + "      animation:{duration:0},\n"
            + paddingCfg
            + "      plugins:{\n"
            + "        legend:{display:false},\n"
            + "        tooltip:{enabled:false}\n"
            + "      },\n"
            + "      scales:{x:" + xScaleCfg + ",y:" + yScaleCfg + "}\n"
            + "    },\n"
            + "    plugins:[_vPlugin,_vLegendPlugin]\n"
            + "  });\n"
            + "  " + tooltipJs
            + "}\n"
            // Expose _vAnimateTo on window so _applyFilter (outside IIFE) can call it.
            // FilterRenderer.buildFilterScript calls _vAnimateTo (single chart).
            // FilterRenderer.buildFilterScriptByPanels calls _vAnimateTo_0, _vAnimateTo_1 etc.
            // id=mainChart -> expose as window._vAnimateTo
            // id=chart_by_N -> expose as window._vAnimateTo_N (extract trailing digit(s))
            + (id.equals("mainChart")
               ? "window._vAnimateTo=_vAnimateTo;\n"
               : "window['_vAnimateTo_"+safeId.replaceAll("^.*_(\\d+)$","$1")+"']=_vAnimateTo;\n")
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
                      : (o.chart.tooltipmode.equals("index") ? "index" : "nearest");
        String ttPos  = o.chart.tooltippos.equals("average") ? "average" : "nearest";
        String ttCfg  = "tooltip:{mode:'" + ttMode + "',position:'" + ttPos + "',"
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
            +   "return ["
            +     "'  '+lbl,"
            +     "'  Median:  '+fmt(d.median),"
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
            + "    ctx.save();\n"
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
            + "      ctx.save();\n"
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
            + "    responsive:true,maintainAspectRatio:true," + aspectCfg + "\n"
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
            + "  plugins:[_bpLegendPlugin]\n"
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
            sb.append(String.format("min:%.4f,max:%.4f,", yLo, yHi));
        } else {
            sb.append("grace:'5%',");
        }
        sb.append("ticks:{color:'").append(lc).append("'},");
        sb.append("grid:{color:'").append(gc).append("',display:true},");
        sb.append("border:{color:'").append(gc).append("',display:true},");
        if (!title.isEmpty()) {
            sb.append("title:{display:true,text:'").append(escJs(title))
              .append("',color:'").append(lc).append("'},");
        }
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
        sb.append("ticks:{color:'").append(lc).append("'").append(tickRotation);
        // autoSkip:false always -- show all category labels on both x and y category axes.
        // For hbox/hviolin the category axis is y; rotation is skipped but labels must all show.
        sb.append(",autoSkip:false");
        sb.append("},");
        sb.append("grid:{color:'").append(gc).append("',display:true},");
        sb.append("border:{color:'").append(gc).append("',display:true},");
        if (!title.isEmpty()) {
            sb.append("title:{display:true,text:'").append(escJs(title))
              .append("',color:'").append(lc).append("'},");
        }
        sb.append("}");
        return sb.toString();
    }


}
