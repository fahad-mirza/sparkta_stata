package com.dashboard_test.html;

import com.dashboard_test.data.*;
import java.util.*;

/**
 * DatasetBuilder -- converts DataSet objects into Chart.js dataset/label JS strings.
 * v2.0.0: Extracted from HtmlGenerator.java as part of modular refactor.
 * Responsibility: pure data-to-JS-string translation:
 *   - Dataset arrays for over(), no-over, aggregated, CI, and pie charts
 *   - X-axis label arrays (overLabels, catLabels, aggLabels)
 *   - Dataset style (colors, line width, fill, point config)
 *   - Statistical computation: aggregate(), ciStats(), tCritical(), computeBins()
 *   - Custom label parsing
 * No HTML generation. No filter logic. No stats panel.
 */
class DatasetBuilder {

    private final DashboardOptions o;
    private final HtmlGenerator    gen;  // shared utilities (col, colS, escJs, sdz, etc.)

    DatasetBuilder(DashboardOptions o, HtmlGenerator gen) {
        this.o   = o;
        this.gen = gen;
    }

    // Delegate shared utilities
    private String animDuration()                    { return gen.animDuration(); }
    private double parseDouble(String s, double def) { return gen.parseDouble(s, def); }
    private String escJs(String s)                   { return gen.escJs(s); }
    private String sdz(String s)                     { return gen.sdz(s); }
    private String col(int i)                        { return gen.col(i); }
    private String colS(int i)                       { return gen.colS(i); }
    private String toRgba(String c, double a)        { return gen.toRgba(c, a); }

    double aggregate(List<Double> vals, String stat) {
        if (vals == null || vals.isEmpty()) return 0;
        switch (stat.toLowerCase()) {
            case "sum":
                double s = 0; for (double d : vals) s += d; return s;
            case "count":
                return vals.size();
            case "min":
                double mn = vals.get(0);
                for (double d : vals) if (d < mn) mn = d;
                return mn;
            case "max":
                double mx = vals.get(0);
                for (double d : vals) if (d > mx) mx = d;
                return mx;
            case "median":
                // Route through percentile() for Stata-compatible (n+1)*p/100 formula.
                // Matches the stats panel median and Stata summ,detail output.
                List<Double> sortedMed = new ArrayList<>(vals);
                java.util.Collections.sort(sortedMed);
                return percentile(sortedMed, 50);
            default: // mean (also handles pct fallthrough for non-pie charts)
                double t = 0; for (double d : vals) t += d; return t / vals.size();
        }
    }

    /**
     * Returns a parenthetical stat label suffix for use in dataset legends.
     * Empty string for mean (default -- no clutter), otherwise e.g. " (Median)".
     */
    String statSuffix(String stat) {
        if (stat == null) return "";
        switch (stat.toLowerCase()) {
            case "sum":    return " (Sum)";
            case "count":  return " (Count)";
            case "median": return " (Median)";
            case "min":    return " (Min)";
            case "max":    return " (Max)";
            default:       return "";  // mean and pct: no suffix needed
        }
    }


    // -------------------------------------------------------------------------
    // Filter support: interactive dropdowns that re-slice the chart data
    // -------------------------------------------------------------------------

    /**
     * Build the HTML dropdown bar for filter() and filter2() variables.
     * Returns empty string when no filters are specified.
     */
    String overLabels(DataSet data) {
        // v1.9.1: pass showmissingOver so (Missing) appears in x-axis label list
        List<String> localGroups = DataSet.uniqueValues(data.getOverVariable(), o.chart.sortgroups, o.showmissingOver);
        // v3.4.1: when _globalOverGroups is set (by() panel mode, single-var colorByCategory),
        // use the global list so x-axis labels align with the null-padded data slots.
        boolean useGlobal = (o._globalOverGroups != null)
            && data.getNumericVariables().size() == 1;
        List<String> groups = useGlobal ? o._globalOverGroups : localGroups;

        // Phase 2-C: leglabels() on a SINGLE-var bar with over() renames x-axis group
        // labels (Stata scenario 1 & 5: one colored bar per over-group, x-axis = groups).
        // With MULTIPLE vars + over() (scenario 2 & 3), each var is its own dataset and
        // leglabels() renames the LEGEND (dataset labels), NOT the x-axis. In that case
        // we do NOT apply leglabels here -- legendLabelsCfg() handles it. (v3.4.1)
        // v3.5.33: relabel() always renames x-axis group labels regardless of single/multi-var,
        // AND also renames the legend (Stata over(var, relabel(...)) asyvars showyvars behavior).
        // relabel() takes priority over leglabels() for x-axis label substitution.
        boolean singleVar = data.getNumericVariables().size() == 1;
        List<String> rl = !o.chart.relabel.isEmpty()
            ? parseCustomLabels(o.chart.relabel) : new ArrayList<>();
        List<String> ll = (rl.isEmpty() && singleVar && !o.chart.legendlabels.isEmpty())
            ? parseCustomLabels(o.chart.legendlabels) : new ArrayList<>();
        List<String> xLabels = !rl.isEmpty() ? rl : ll;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String s : groups) {
            String lbl = (!xLabels.isEmpty() && i < xLabels.size())
                ? xLabels.get(i)
                : (s == null || s.trim().isEmpty()) ? DataSet.MISSING_SENTINEL : sdz(s);
            sb.append("'").append(escJs(lbl)).append("',");
            i++;
        }
        return sb.toString();
    }

    // =========================================================================
    // SECONDARY Y-AXIS HELPER (v2.3.0)
    // =========================================================================
    // Returns true when the variable name appears in o.axes.y2vars (space-separated).
    // Used by overDatasets(), numDatasets(), and buildDatasetStyle() to inject
    // yAxisID:'y2' into any dataset whose variable is assigned to the right axis.
    // =========================================================================
    private boolean isY2Var(Variable var) {
        if (o.axes.y2vars == null || o.axes.y2vars.trim().isEmpty()) return false;
        String name = var.getName().trim();
        String disp = var.getDisplayName().trim();
        for (String v : o.axes.y2vars.trim().split("\\s+")) {
            if (v.equals(name) || v.equals(disp)) return true;
        }
        return false;
    }


    String overDatasets(DataSet data, boolean isLine, boolean logTransform) {
        Variable ov = data.getOverVariable();
        // groups = display labels (value labels if available, else raw)
        // groupKeys = raw values used for row matching (always numeric codes)
        // v1.9.1: pass showmissingOver so (Missing) group appears when set
        List<String> localGroups    = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
        List<String> localGroupKeys = DataSet.uniqueGroupKeys(ov, o.chart.sortgroups, o.showmissingOver);

        // v3.5.28: When rendering a by() panel, BOTH x-axis labels and data must use
        // the GLOBAL group list so every panel has identical x-axis positions and
        // absent groups emit null rather than shifting remaining values left.
        // Example: foreign cars have rep78=3,4,5 only. Without global alignment
        // data [4829,6261,6293] maps to x-positions 1,2,3 instead of 3,4,5.
        //
        // useGlobalAlignment: ALL chart types when _globalOverGroups is set.
        //   Iterates global list; absent groups -> null; x-axis stays aligned.
        // useGlobalColors: bar-only (single-var colorByCategory). Selects palette
        //   index from global position so rep78=3 always gets col(2). (v3.4.1)
        boolean useGlobalAlignment = (o._globalOverGroups != null);
        boolean useGlobalColors    = useGlobalAlignment && !isLine
            && data.getNumericVariables().size() == 1;

        // Build global color index map: displayLabel -> globalPosition (bar only)
        java.util.Map<String,Integer> globalColorIdx = new java.util.LinkedHashMap<>();
        if (useGlobalColors) {
            for (int i = 0; i < o._globalOverGroups.size(); i++) {
                globalColorIdx.put(o._globalOverGroups.get(i), i);
            }
        }

        // Build local key lookup: displayLabel -> rawKey (for data row matching)
        java.util.Map<String,String> labelToKey = new java.util.LinkedHashMap<>();
        for (int i = 0; i < localGroups.size(); i++) {
            labelToKey.put(localGroups.get(i), localGroupKeys.get(i));
        }

        // Use global groups for iteration when in by() mode so data indices align
        // with the global x-axis label array from overLabels().
        List<String> groups    = useGlobalAlignment ? o._globalOverGroups : localGroups;
        List<String> groupKeys = localGroupKeys; // only used in non-global path

        List<Variable> nv = new ArrayList<>(data.getNumericVariables());
        // v2.0.1: For non-stacked area charts, sort series by descending mean so the
        // largest filled area is at the back and the smallest is painted on top.
        // Combined with reduced fill opacity (0.35), both series remain visible AND
        // the smaller series is never obscured by a larger one in front of it.
        // The sort uses the per-group aggregate mean averaged across all groups as
        // a proxy for visual area -- robust regardless of variable units.
        // v2.0.1: For non-stacked area charts, sort series DESCENDING by integrated area.
        // With beginAtZero=true (auto-set for area charts), both fills go from y=0 upward.
        // Chart.js paints datasets in forward order: dataset[0] first (bottom), dataset[last]
        // last (top). So the LARGEST fill must be dataset[0] (bottom layer) and the SMALLEST
        // must be dataset[last] (top layer, painted on top of larger fill so it stays visible).
        // Integration proxy: sum of raw values across all groups, proportional to mean*n_groups.
        // This correctly ranks series by visual fill area on the shared y-axis from zero.
        if (isLine && o.chart.fill && !o.chart.stack && nv.size() > 1) {
            // v2.0.1 revised: Sort ASCENDING by integrated area (trapezoid rule proxy).
            // Strategy: dataset[0] = smallest series -> fill:'origin' (fills 0 to its line).
            //           dataset[k] = each larger series -> fill:'-1' (fills the BAND
            //           between itself and the series below it, i.e. fills from
            //           previous-series-line UP to current-series-line).\
            // Result: non-overlapping stacked bands, each series' color occupies exactly
            // the zone between its line and the next-smaller series. Fully visible at
            // any opacity including 1.0. The integration proxy: sum of per-group means,
            // proportional to the trapezoid area on the shared y-axis from zero.
            // Only the aggregate values (means/medians/etc.) matter for area, not raw obs.
            // We approximate by summing all raw values -- proportional when group sizes equal.
            nv.sort((a, b) -> {
                double sumA = 0, sumB = 0;
                for (Object v : a.getValues()) if (v instanceof Number) sumA += ((Number)v).doubleValue();
                for (Object v : b.getValues()) if (v instanceof Number) sumB += ((Number)v).doubleValue();
                return Double.compare(sumA, sumB); // ASCENDING: smallest area first = dataset[0] = bottom
            });
        }
        // When there is only one numeric variable on a bar chart, color each bar
        // by its category (group) rather than giving all bars the same series color.
        boolean colorByCategory = !isLine && nv.size() == 1;
        StringBuilder sb = new StringBuilder();
        int ci = 0;
        for (Variable var : nv) {
            // v2.3.0: inject secondary y-axis ID when variable is in y2vars
            boolean varIsY2 = isY2Var(var);
            sb.append("{label:'").append(escJs(var.getDisplayName() + statSuffix(o.stats.stat))).append("',data:[");
            for (int gi = 0; gi < groups.size(); gi++) {
                String gc;
                if (useGlobalAlignment) {
                    // Global path: look up local key by display label.
                    // If absent in this panel -> emit null to keep x-axis aligned.
                    // Applies to ALL chart types (line, bar, area, etc.) in by() mode.
                    String localKey = labelToKey.get(groups.get(gi));
                    if (localKey == null) { sb.append("null,"); continue; }
                    gc = sdz(localKey);
                } else {
                    gc = sdz(groupKeys.get(gi));
                }
                // v3.5.8: O(1) index lookup replaces O(N) scan.
                // groupIndex() key for MISSING_SENTINEL rows: null -> MISSING_SENTINEL,
                // "" -> MISSING_SENTINEL. gc already uses the same normalisation.
                List<Integer> _rowIdx = ov.groupIndex().get(gc);
                if (_rowIdx == null && o.showmissingOver)
                    _rowIdx = ov.groupIndex().get(DataSet.MISSING_SENTINEL);
                List<Double> vals = new ArrayList<>();
                if (_rowIdx != null) {
                    for (int i : _rowIdx) {
                        Object v = var.getValues().get(i);
                        if (v instanceof Number) vals.add(((Number)v).doubleValue());
                    }
                }
                if (vals.isEmpty()) sb.append("null,");
                else {
                    double agg = aggregate(vals, o.stats.stat);
                    double out = logTransform ? (agg > 0 ? Math.log10(agg) : 0) : agg;
                    sb.append(String.format("%.6f",out)).append(",");
                }
            }
            sb.append("],");
            if (varIsY2) sb.append("yAxisID:'y2',");
            if (colorByCategory) {
                // Build per-bar color arrays -- one color per group.
                // v3.4.1: when useGlobalColors=true (by() panels), look up each local
                // group's global index via globalColorIdx so rep78=3 always gets col(2)
                // regardless of which panel we're in. X-axis only shows local groups
                // (no null padding) but colors match globally. (v3.4.1)
                // v1.9.1: (Missing) bar always grey regardless of palette position.
                // v3.2.0: gradient -- use _grad{colorIdx} references instead of static strings.
                String missingBg  = gen.isDark() ? "rgba(120,120,120,0.55)" : "rgba(160,160,160,0.65)";
                String missingBrd = gen.isDark() ? "rgba(120,120,120,1)"    : "rgba(120,120,120,1)";
                StringBuilder bgArr  = new StringBuilder("[");
                StringBuilder brdArr = new StringBuilder("[");
                int localColorIdx = 0;
                for (int gi = 0; gi < groups.size(); gi++) {
                    boolean isMissGrp = DataSet.MISSING_SENTINEL.equals(groups.get(gi));
                    // Global color index: if we have a global map, look up by group label;
                    // fall back to local counter if not found (shouldn't happen).
                    int cIdx;
                    if (useGlobalColors && !isMissGrp) {
                        Integer gIdx = globalColorIdx.get(groups.get(gi));
                        cIdx = (gIdx != null) ? gIdx : localColorIdx;
                    } else {
                        cIdx = localColorIdx;
                    }
                    if (!o.style.gradient.isEmpty() && !isMissGrp) {
                        bgArr.append("_grad").append(cIdx).append(",");
                    } else {
                        bgArr.append("'").append(isMissGrp ? missingBg : col(cIdx)).append("',");
                    }
                    brdArr.append("'").append(isMissGrp ? missingBrd : colS(cIdx)).append("',");
                    if (!isMissGrp) localColorIdx++;
                }
                bgArr.append("]"); brdArr.append("]");
                sb.append("backgroundColor:").append(bgArr).append(",");
                sb.append("borderColor:").append(brdArr).append(",");
                sb.append("borderWidth:1,");
                if (!o.chart.borderradius.isEmpty()) sb.append("borderRadius:").append(o.chart.borderradius).append(",");
                if (!o.chart.barwidth.isEmpty())     sb.append("barPercentage:").append(o.chart.barwidth).append(",");
                if (!o.chart.bargroupwidth.isEmpty())sb.append("categoryPercentage:").append(o.chart.bargroupwidth).append(",");
            } else {
                sb.append(buildDatasetStyle(ci, isLine, var, ci));
            }
            sb.append("},\n");
            ci++;
        }
        return sb.toString();
    }

    String catLabels(DataSet data) {
        Variable sv = data.getFirstStringVariable();
        if (sv != null) {
            StringBuilder sb = new StringBuilder();
            for (Object v : sv.getValues()) sb.append("'").append(escJs(String.valueOf(v))).append("',");
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        for (int i=1; i<=data.getObservationCount(); i++) sb.append(i).append(",");
        return sb.toString();
    }

    /**
     * Build datasets when no over() variable is present.
     * v1.8.9: applies o.stats.stat (mean/sum/count/median/min/max).
     * v1.9.0: Fixed Chart.js label/data alignment bug.
     *
     * Previous approach emitted one dataset per variable, each with 1 data point
     * but ALL N variable names as x-axis labels. Chart.js maps data[0] -> label[0]
     * for every dataset, so all series stacked on the first label ("Price") and
     * subsequent variables were invisible or displaced.
     *
     * Fix: emit ONE dataset with N data points (one per variable) and per-bar
     * color arrays. The x-axis has N labels (variable names) matching N data
     * points 1-to-1. For line charts, a single series with N points also
     * renders correctly (connecting them in order).
     *
     * Label for legend: stat suffix only (e.g. "(Mean)", "(Sum)") since the
     * x-axis labels already identify each variable.
     */
    String numDatasets(DataSet data, boolean isLine, boolean logTransform) {
        List<Variable> nv = data.getNumericVariables();
        // Aggregate one value per variable
        List<Double> aggVals = new ArrayList<>();
        for (Variable var : nv) {
            List<Double> vals = new ArrayList<>();
            for (Object v : var.getValues()) {
                if (v instanceof Number) vals.add(((Number) v).doubleValue());
            }
            double agg = vals.isEmpty() ? 0 : aggregate(vals, o.stats.stat);
            double out = logTransform ? (agg > 0 ? Math.log10(agg) : 0) : agg;
            aggVals.add(out);
        }
        // Legend label: stat suffix if non-default, else generic label
        String legendLabel = statSuffix(o.stats.stat).isEmpty() ? "Mean" : statSuffix(o.stats.stat).trim()
            .replace("(","").replace(")","");
        StringBuilder sb = new StringBuilder();
        sb.append("{label:'").append(escJs(legendLabel)).append("',data:[");
        for (double v : aggVals) sb.append(String.format("%.6f", v)).append(",");
        sb.append("],");
        if (!isLine) {
            // Per-bar color arrays so each variable gets a distinct color
            // v3.2.0: gradient -- use _grad{i} references instead of static strings
            sb.append("backgroundColor:[");
            for (int i = 0; i < nv.size(); i++) {
                if (!o.style.gradient.isEmpty()) sb.append("_grad").append(i).append(",");
                else sb.append("'").append(col(i)).append("',");
            }
            sb.append("],borderColor:[");
            for (int i = 0; i < nv.size(); i++) sb.append("'").append(colS(i)).append("',");
            sb.append("],borderWidth:1,");
            if (!o.chart.borderradius.isEmpty()) sb.append("borderRadius:").append(o.chart.borderradius).append(",");
            if (!o.chart.barwidth.isEmpty())     sb.append("barPercentage:").append(o.chart.barwidth).append(",");
            if (!o.chart.bargroupwidth.isEmpty())sb.append("categoryPercentage:").append(o.chart.bargroupwidth).append(",");
        } else {
            // Line: use first series color (gradient not applied to plain line, only area)
            String bgVal = (!o.style.gradient.isEmpty() && o.chart.fill) ? "_grad0" : ("'" + col(0) + "'");
            sb.append("backgroundColor:").append(bgVal).append(",");
            sb.append("borderColor:'").append(colS(0)).append("',");
            sb.append("borderWidth:").append(o.chart.linewidth).append(",");
            // v3.1.0: Phase 1-D -- borderDash for series 0, nopoints, pointhoversize
            sb.append(borderDashProp(0));
            sb.append("fill:").append(o.chart.fill).append(",");
            sb.append("tension:").append(o.chart.smooth).append(",");
            sb.append("pointRadius:").append(linePointRadius()).append(",");
            sb.append("pointHoverRadius:").append(linePointHoverRadius()).append(",");
            sb.append("pointBorderWidth:").append(o.chart.pointborderwidth).append(",");
            sb.append("pointRotation:").append(o.chart.pointrotation).append(",");
            if (!o.chart.pointstyle.isEmpty()) sb.append("pointStyle:'").append(o.chart.pointstyle).append("',");
            sb.append("spanGaps:true,");
        }
        sb.append("},\n");
        return sb.toString();
    }

    /**
     * Build x-axis labels when no over() variable is present and stat aggregation
     * is active. Returns one label per numeric variable (the variable display name).
     * v1.8.9: replaces catLabels() in the no-over path for bar/line chart types.
     */
    String aggLabels(DataSet data) {
        List<Variable> nv = data.getNumericVariables();
        // Phase 2-C: leglabels() in multi-var no-over() mode renames the x-axis
        // variable labels (matching Stata: graph bar price weight with renamed bars).
        // leglabels("Car Price|Car Weight") overrides "Price" and "Weight (lbs.)"
        // by position. Extra entries are ignored; missing entries keep the default. (v3.4.1)
        List<String> ll = o.chart.legendlabels.isEmpty()
            ? new ArrayList<>() : parseCustomLabels(o.chart.legendlabels);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Variable var : nv) {
            String lbl = (i < ll.size()) ? ll.get(i) : var.getDisplayName();
            sb.append("'").append(escJs(lbl)).append("',");
            i++;
        }
        return sb.toString();
    }

    /** Build the style properties for a bar or line dataset */
    String buildDatasetStyle(int ci, boolean isLine, Variable var) {
        return buildDatasetStyle(ci, isLine, var, -1);
    }

    String buildDatasetStyle(int ci, boolean isLine, Variable var, int seriesIndex) {
        StringBuilder sb = new StringBuilder();
        // v2.0.1: For non-stacked area charts use user-controlled areaopacity (default 0.35)
        // so overlapping filled regions remain visible. At 0.85 the back series is nearly
        // invisible. Stacked and plain line charts keep the standard col() opacity.
        double fillOp = 0.75; // v2.0.1: default raised from 0.35; fill:'-1' banding eliminates overlap so higher opacity is safe
        try { fillOp = Double.parseDouble(o.chart.areaopacity.trim()); } catch (Exception ignore) {}
        // v3.2.0: Phase 1-E -- gradient: use JS _grad{ci} variable reference instead of
        // static color string. Applies to area (fill=true line) and bar charts only.
        // The gradient preamble (buildGradientPreamble) must be emitted before new Chart().
        boolean useGrad = !o.style.gradient.isEmpty() && (isLine ? o.chart.fill : true);
        String bgValue = useGrad ? "_grad" + ci
            : "'" + ((isLine && o.chart.fill && !o.chart.stack)
                ? toRgba(colS(ci), fillOp)
                : col(ci)) + "'";
        sb.append("backgroundColor:").append(bgValue).append(",");
        sb.append("borderColor:'").append(colS(ci)).append("',");
        if (isLine) {
            sb.append("borderWidth:").append(o.chart.linewidth).append(",");
            // v3.1.0: Phase 1-D -- emit borderDash for this series index (solid emits nothing)
            sb.append(borderDashProp(seriesIndex < 0 ? ci : seriesIndex));
            // Stacked area: first series fills to origin, subsequent fill to series below
            if (o.chart.fill && o.chart.stack) {
                String fillVal = (seriesIndex <= 0) ? "'origin'" : "'-1'";
                sb.append("fill:").append(fillVal).append(",");
            } else if (o.chart.fill && !o.chart.stack && seriesIndex > 0) {
                // v2.0.1: Multi-series non-stacked area: upper series fills the band
                // between its line and the series below (fill:'-1'). Non-overlapping bands.
                // v3.2.0: Exception -- when gradient is active, each series fills to origin
                // independently so the gradient wash is visible on all series.
                if (!o.style.gradient.isEmpty()) {
                    sb.append("fill:'origin',");
                } else {
                    sb.append("fill:'-1',");
                }
            } else if (o.chart.fill && !o.chart.stack && seriesIndex == 0) {
                // Bottom series (smallest): fill from origin to its line
                sb.append("fill:'origin',");
            } else {
                sb.append("fill:").append(o.chart.fill).append(",");
            }
            sb.append("tension:").append(o.chart.smooth).append(",");
            // v3.1.0: Phase 1-D -- nopoints and pointhoversize via helpers
            sb.append("pointRadius:").append(linePointRadius()).append(",");
            sb.append("pointHoverRadius:").append(linePointHoverRadius()).append(",");
            sb.append("pointBorderWidth:").append(o.chart.pointborderwidth).append(",");
            sb.append("pointRotation:").append(o.chart.pointrotation).append(",");
            if (!o.chart.pointstyle.isEmpty()) sb.append("pointStyle:'").append(o.chart.pointstyle).append("',");
            if (!o.chart.stepped.isEmpty())    sb.append("stepped:'").append(o.chart.stepped).append("',");
            sb.append("spanGaps:").append(o.chart.spanmissing).append(",");
        } else {
            sb.append("borderWidth:1,");
            if (!o.chart.borderradius.isEmpty()) sb.append("borderRadius:").append(o.chart.borderradius).append(",");
            if (!o.chart.barwidth.isEmpty())     sb.append("barPercentage:").append(o.chart.barwidth).append(",");
            if (!o.chart.bargroupwidth.isEmpty())sb.append("categoryPercentage:").append(o.chart.bargroupwidth).append(",");
        }
        return sb.toString();
    }

    /** Build point config for scatter/bubble datasets */
    String buildPointConfig(int ci) {
        StringBuilder sb = new StringBuilder();
        sb.append("pointRadius:").append(o.chart.pointsize).append(",");
        sb.append("pointHoverRadius:").append(parseDouble(o.chart.pointsize,4)+2).append(",");
        sb.append("pointBorderWidth:").append(o.chart.pointborderwidth).append(",");
        sb.append("pointRotation:").append(o.chart.pointrotation).append(",");
        if (!o.chart.pointstyle.isEmpty()) sb.append("pointStyle:'").append(o.chart.pointstyle).append("',");
        return sb.toString();
    }

    // =========================================================================
    // PHASE 1-D: LINE / POINT STYLE HELPERS (v3.1.0)
    // =========================================================================
    // These helpers are the single source of truth for dash-pattern and
    // point-radius logic. All line/area dataset emission sites (buildDatasetStyle,
    // numDatasets, ciLineDatasets) delegate here so future pattern additions
    // only require changes in one place.
    // =========================================================================

    /**
     * Translates a Stata-style pattern name to a Chart.js borderDash array string.
     * Returns "" (empty) for solid or unrecognised values -- Chart.js treats an
     * absent borderDash as a solid line so no property is emitted for solid.
     *
     * Supported patterns:
     *   solid   -> ""           (no borderDash property needed)
     *   dash    -> [6,3]        (6px dash, 3px gap)
     *   dot     -> [2,3]        (2px dot, 3px gap)
     *   dashdot -> [8,3,2,3]    (8px dash, 3px gap, 2px dot, 3px gap)
     *
     * If new patterns are added in future, extend this method only.
     * Call sites do: if (!bd.isEmpty()) sb.append("borderDash:"+bd+",");
     */
    private String lpatternToBorderDash(String pattern) {
        if (pattern == null) return "";
        switch (pattern.trim().toLowerCase()) {
            case "dash":    return "[6,3]";
            case "dot":     return "[2,3]";
            case "dashdot": return "[8,3,2,3]";
            default:        return "";  // solid or empty: no borderDash property
        }
    }

    /**
     * Returns the resolved borderDash array string for a given series index.
     * Resolution order (first non-empty wins):
     *   1. lpatterns() per-series list at position seriesIdx (pipe-separated, cycles)
     *   2. lpattern()  global pattern applied to all series
     *   3. ""          (solid -- no property emitted)
     *
     * seriesIdx is 0-based. If lpatterns has fewer entries than datasets,
     * the list cycles (modulo) so every series always gets a pattern.
     *
     * This method is called from every line/area dataset builder, ensuring
     * consistent resolution logic at all sites.
     */
    String resolvedBorderDash(int seriesIdx) {
        // Try per-series list first
        if (!o.chart.lpatterns.isEmpty()) {
            String[] parts = o.chart.lpatterns.split("[|]", -1);
            if (parts.length > 0) {
                String pat = parts[seriesIdx % parts.length].trim();
                String bd = lpatternToBorderDash(pat);
                if (!bd.isEmpty()) return bd;
                // "solid" in lpatterns explicitly overrides a global lpattern for this series
                if (pat.equalsIgnoreCase("solid")) return "";
            }
        }
        // Fall back to global lpattern
        return lpatternToBorderDash(o.chart.lpattern);
    }

    /**
     * Emits borderDash property string for a line/area dataset at seriesIdx.
     * Returns "" when the pattern resolves to solid (no property needed).
     * Usage: sb.append(borderDashProp(si));
     */
    private String borderDashProp(int seriesIdx) {
        String bd = resolvedBorderDash(seriesIdx);
        return bd.isEmpty() ? "" : "borderDash:" + bd + ",";
    }

    /**
     * Returns the point radius value string to emit for a line/area dataset.
     * When nopoints is set, returns "0" regardless of other settings.
     * Otherwise returns o.chart.pointsize (which may itself be user-overridden).
     * Used at all three line/area emission sites for consistency.
     */
    private String linePointRadius() {
        return o.chart.nopoints ? "0" : o.chart.pointsize;
    }

    /**
     * Returns the pointHoverRadius value string for a line/area dataset.
     * Resolution order:
     *   1. nopoints -> 0 (no hover highlight if markers are suppressed)
     *   2. pointhoversize() user override -> that value
     *   3. Default -> pointsize + 2 (Chart.js convention for hover emphasis)
     */
    private String linePointHoverRadius() {
        if (o.chart.nopoints) return "0";
        if (!o.chart.pointhoversize.isEmpty()) {
            try {
                double ph = Double.parseDouble(o.chart.pointhoversize.trim());
                return String.valueOf((int) ph);
            } catch (Exception ignore) {}
        }
        return String.valueOf((int)(parseDouble(o.chart.pointsize, 4) + 2));
    }

    // -- Legend config ---------------------------------------------------------

    /** Returns datalabels:{display:false} suppressor string when the datalabels plugin is
     *  globally registered (pielabels=true) but this chart type doesn't use it.
     *  Without this, Chart.js renders a blank chart for all non-pie charts. */
    double tCritical(int df, int level) {
        // z-scores for infinite df (normal approximation)
        double zCrit = (level == 90) ? 1.6449 : (level == 99) ? 2.5758 : 1.9600;
        if (df < 1)   return zCrit;
        if (df > 120) return zCrit;

        // t-table: rows = df 1..30, 40, 60, 80, 100, 120
        // columns: 90%, 95%, 99%
        // Source: standard two-tailed t-distribution critical values
        double[][] tbl = {
            // df   90%      95%      99%
            /* 1  */ {6.3138, 12.7062, 63.6567},
            /* 2  */ {2.9200,  4.3027,  9.9248},
            /* 3  */ {2.3534,  3.1824,  5.8409},
            /* 4  */ {2.1318,  2.7764,  4.6041},
            /* 5  */ {2.0150,  2.5706,  4.0321},
            /* 6  */ {1.9432,  2.4469,  3.7074},
            /* 7  */ {1.8946,  2.3646,  3.4995},
            /* 8  */ {1.8595,  2.3060,  3.3554},
            /* 9  */ {1.8331,  2.2622,  3.2498},
            /* 10 */ {1.8125,  2.2281,  3.1693},
            /* 11 */ {1.7959,  2.2010,  3.1058},
            /* 12 */ {1.7823,  2.1788,  3.0545},
            /* 13 */ {1.7709,  2.1604,  3.0123},
            /* 14 */ {1.7613,  2.1448,  2.9768},
            /* 15 */ {1.7531,  2.1315,  2.9467},
            /* 16 */ {1.7459,  2.1199,  2.9208},
            /* 17 */ {1.7396,  2.1098,  2.8982},
            /* 18 */ {1.7341,  2.1009,  2.8784},
            /* 19 */ {1.7291,  2.0930,  2.8609},
            /* 20 */ {1.7247,  2.0860,  2.8453},
            /* 21 */ {1.7207,  2.0796,  2.8314},
            /* 22 */ {1.7171,  2.0739,  2.8188},
            /* 23 */ {1.7139,  2.0687,  2.8073},
            /* 24 */ {1.7109,  2.0639,  2.7969},
            /* 25 */ {1.7081,  2.0595,  2.7874},
            /* 26 */ {1.7056,  2.0555,  2.7787},
            /* 27 */ {1.7033,  2.0518,  2.7707},
            /* 28 */ {1.7011,  2.0484,  2.7633},
            /* 29 */ {1.6991,  2.0452,  2.7564},
            /* 30 */ {1.6973,  2.0423,  2.7500},
            /* 40 */ {1.6839,  2.0211,  2.7045},
            /* 60 */ {1.6707,  2.0003,  2.6603},
            /* 80 */ {1.6641,  1.9905,  2.6387},
            /* 100*/ {1.6602,  1.9840,  2.6259},
            /* 120*/ {1.6577,  1.9799,  2.6174}
        };
        int col = (level == 90) ? 0 : (level == 99) ? 2 : 1;

        // df 1-30: direct lookup
        if (df <= 30) return tbl[df - 1][col];

        // df 31-120: linear interpolation between bracketing table rows
        double lo, hi, frac;
        if (df <= 40)  { lo=tbl[29][col]; hi=tbl[30][col]; frac=(df-30.0)/10.0; }
        else if (df <= 60)  { lo=tbl[30][col]; hi=tbl[31][col]; frac=(df-40.0)/20.0; }
        else if (df <= 80)  { lo=tbl[31][col]; hi=tbl[32][col]; frac=(df-60.0)/20.0; }
        else if (df <= 100) { lo=tbl[32][col]; hi=tbl[33][col]; frac=(df-80.0)/20.0; }
        else                { lo=tbl[33][col]; hi=tbl[34][col]; frac=(df-100.0)/20.0; }
        return lo + frac * (hi - lo);
    }

    /**
     * Computes CI statistics for each group of a variable.
     * Returns double[groups][5]: {n, mean, lo, hi, se} per group.
     * n=0 rows indicate empty groups (should be skipped in rendering).
     *
     * @param var     the numeric variable
     * @param ov      the over() grouping variable
     * @param groups  ordered display labels
     * @param keys    ordered raw keys matching ov values
     * @param level   CI level as integer (90, 95, or 99)
     */
    double[][] ciStats(Variable var, Variable ov,
                                List<String> groups, List<String> keys, int level) {
        double[][] result = new double[groups.size()][5];
        for (int gi = 0; gi < groups.size(); gi++) {
            String gc = sdz(keys.get(gi));
            // Collect values for this group
            // v3.5.8: O(1) index lookup replaces O(N) scan.
            List<Integer> _riCI = ov.groupIndex().get(gc);
            List<Double> vals = new ArrayList<>();
            if (_riCI != null) {
                for (int i : _riCI) {
                    Object v = var.getValues().get(i);
                    if (v instanceof Number) vals.add(((Number) v).doubleValue());
                }
            }
            int n = vals.size();
            if (n < 2) {
                // Cannot compute CI with fewer than 2 observations -- leave as zeros
                result[gi] = new double[]{n, n == 1 ? vals.get(0) : 0, 0, 0, 0};
                continue;
            }
            // Mean
            double sum = 0; for (double d : vals) sum += d;
            double mean = sum / n;
            // Sample SD (Bessel correction, matches Stata summ)
            double ss = 0; for (double d : vals) ss += (d - mean) * (d - mean);
            double sd = Math.sqrt(ss / (n - 1));
            // SE and half-width
            double se     = sd / Math.sqrt(n);
            double tCrit  = tCritical(n - 1, level);
            double hw     = tCrit * se;
            result[gi] = new double[]{n, mean, mean - hw, mean + hw, se};
        }
        return result;
    }

    // =========================================================================
    // HISTOGRAM (v1.8.0)
    // =========================================================================
    // Design:
    //   - Single numeric variable, no over() (validated in ado)
    //   - Bins computed in Java: Sturges rule by default, user bins() override
    //   - Y-axis: density (default, matches Stata), frequency, or fraction
    //   - Rendered as Chart.js bar with barPercentage:1, categoryPercentage:1
    //     (bars touch each other = histogram look, not grouped bar look)
    //   - Labels: bin midpoints formatted to 2 dp, shown as x-axis ticks
    //   - Tooltip: shows bin range [lo, hi) and count/density/fraction
    //   - No over() = no legend needed (single dataset)
    //   - by() panels each render their own histogram via buildChartScript
    //   - filter() not blocked -- each filter slice gets its own binning
    //     (bins recomputed per slice so they fit the slice's data range)
    // =========================================================================

    /**
     * Compute histogram bins for a list of values.
     * Returns double[nBins][4]: {midpoint, lo, hi, count} per bin.
     * Bin boundaries are half-open [lo, hi) except last bin is [lo, hi].
     *
     * @param vals   sorted list of non-missing values
     * @param nBins  number of bins (Sturges rule applied upstream if <= 0)
     */
    double[][] computeBins(List<Double> vals, int nBins) {
        if (vals.isEmpty()) return new double[0][4];
        double minV = vals.get(0);
        double maxV = vals.get(vals.size() - 1);
        // Edge case: all values identical -- use 1 bin
        if (maxV == minV) {
            double[][] result = new double[1][4];
            result[0] = new double[]{minV, minV - 0.5, minV + 0.5, vals.size()};
            return result;
        }
        double width  = (maxV - minV) / nBins;
        double[][] bins = new double[nBins][4];
        for (int b = 0; b < nBins; b++) {
            double lo  = minV + b * width;
            double hi  = lo + width;
            double mid = (lo + hi) / 2.0;
            bins[b] = new double[]{mid, lo, hi, 0};
        }
        // Count values into bins
        for (double v : vals) {
            int b = (int) Math.floor((v - minV) / width);
            if (b >= nBins) b = nBins - 1; // last value falls on upper edge
            bins[b][3]++;
        }
        return bins;
    }

    /**
     * Sturges rule for bin count: ceil(log2(n) + 1).
     * Clamps to [5, 50] for visual sanity.
     */
    int sturgesBins(int n) {
        int b = (int) Math.ceil(Math.log(n) / Math.log(2) + 1);
        return Math.max(5, Math.min(50, b));
    }

    /**
     * Histogram chart (v1.8.0).
     * Single variable, bins computed in Java, rendered as Chart.js bar.
     */
    List<Object[]> ciBarValidGroups(DataSet slice) {
        if (!slice.hasOver()) return new ArrayList<>();
        Variable ov = slice.getOverVariable();
        // v1.9.1: pass showmissingOver so (Missing) group appears when set
        List<String> groups    = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
        List<String> groupKeys = DataSet.uniqueGroupKeys(ov, o.chart.sortgroups, o.showmissingOver);
        int level = 95;
        try { level = Integer.parseInt(o.stats.cilevel.trim()); } catch (Exception ignore) {}
        // Use first numeric variable to determine group validity (n>=2 and non-empty key)
        List<Variable> nv = slice.getNumericVariables();
        if (nv.isEmpty()) return new ArrayList<>();
        double[][] cs = ciStats(nv.get(0), ov, groups, groupKeys, level);
        List<Object[]> valid = new ArrayList<>();
        for (int gi = 0; gi < groups.size(); gi++) {
            String key = groupKeys.get(gi);
            // Skip missing-value groups (empty key) -- barWithErrorBars crashes on null bars
            if (key == null || key.trim().isEmpty()) continue;
            // Skip groups with n<2 -- no CI is possible
            if (cs[gi][0] < 2) continue;
            valid.add(new Object[]{groups.get(gi), groupKeys.get(gi), cs[gi]});
        }
        return valid;
    }

    /**
     * Builds the x-axis labels string for a cibar chart -- only valid groups.
     * Matches exactly the groups used by ciBarDatasets().
     */
    String ciBarLabels(DataSet slice) {
        List<Object[]> valid = ciBarValidGroups(slice);
        StringBuilder sb = new StringBuilder();
        for (Object[] row : valid) sb.append("'").append(escJs(sdz((String)row[0]))).append("',");
        return sb.toString();
    }

    /**
     * Builds the barWithErrorBars datasets string for a given DataSet slice.
     * Only emits valid groups (n>=2, non-empty key) -- null bars cause the
     * barWithErrorBars plugin to crash silently and leave the canvas blank.
     */
    String ciBarDatasets(DataSet slice) {
        if (!slice.hasOver()) return "";
        Variable ov = slice.getOverVariable();
        // v1.9.1: pass showmissingOver so (Missing) group appears when set
        List<String> groups    = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
        List<String> groupKeys = DataSet.uniqueGroupKeys(ov, o.chart.sortgroups, o.showmissingOver);
        List<Variable> nv = slice.getNumericVariables();
        int level = 95;
        try { level = Integer.parseInt(o.stats.cilevel.trim()); } catch (Exception ignore) {}
        // Pre-compute which group indices are valid (n>=2, non-empty key)
        // Use first variable to determine validity -- same groups used for all vars
        List<Integer> validIdx = new ArrayList<>();
        if (!nv.isEmpty()) {
            double[][] cs0 = ciStats(nv.get(0), ov, groups, groupKeys, level);
            for (int gi = 0; gi < groups.size(); gi++) {
                String key = groupKeys.get(gi);
                if (key == null || key.trim().isEmpty()) continue;
                if (cs0[gi][0] < 2) continue;
                validIdx.add(gi);
            }
        }
        // v2.2.1: when only one numeric variable is present, color each bar by its
        // group (matching type(bar) single-variable behaviour). When multiple variables
        // are present keep one color per series (each variable = one color).
        boolean colorByGroup = (nv.size() == 1);
        // Pre-build per-group color arrays for the colorByGroup path.
        // validIdx holds the group indices that survived the n>=2 filter, in order.
        // We need one color per valid group, cycling through the palette.
        String missingBg  = gen.isDark() ? "rgba(120,120,120,0.55)" : "rgba(160,160,160,0.65)";
        String missingBrd = gen.isDark() ? "rgba(120,120,120,1)"    : "rgba(120,120,120,1)";

        StringBuilder dsJs = new StringBuilder();
        int ci = 0;
        for (Variable var : nv) {
            double[][] cs = ciStats(var, ov, groups, groupKeys, level);
            StringBuilder dataJs = new StringBuilder();
            for (int gi : validIdx) {
                double[] row = cs[gi];
                // v2.1.0: embed n in data point so tooltip can show group sample size
                dataJs.append(String.format("{y:%.6f,yMin:%.6f,yMax:%.6f,n:%.0f},", row[1], row[2], row[3], row[0]));
            }
            String lbl = escJs(var.getDisplayName() + " (" + level + "% CI)");
            dsJs.append("{label:'").append(lbl).append("',")
                .append("data:[").append(dataJs).append("],");

            if (colorByGroup) {
                // Build per-bar color arrays -- one palette color per valid group
                StringBuilder bgArr    = new StringBuilder("[");
                StringBuilder brdArr   = new StringBuilder("[");
                StringBuilder errArr   = new StringBuilder("[");
                // v3.5.28: use global palette index when _globalOverGroups is set
                // so each over-group always maps to the same palette color.
                java.util.Map<String,Integer> globalCiIdx = new java.util.LinkedHashMap<>();
                if (o._globalOverGroups != null) {
                    for (int gi2 = 0; gi2 < o._globalOverGroups.size(); gi2++)
                        globalCiIdx.put(o._globalOverGroups.get(gi2), gi2);
                }
                int colorIdx = 0;
                for (int gi : validIdx) {
                    boolean isMissGrp = DataSet.MISSING_SENTINEL.equals(groups.get(gi));
                    int palIdx = (!isMissGrp && globalCiIdx.containsKey(groups.get(gi)))
                        ? globalCiIdx.get(groups.get(gi)) : colorIdx;
                    String bg  = isMissGrp ? missingBg  : col(palIdx);
                    String brd = isMissGrp ? missingBrd : colS(palIdx);
                    bgArr.append("'").append(bg).append("',");
                    brdArr.append("'").append(brd).append("',");
                    errArr.append("'").append(brd).append("',");
                    if (!isMissGrp) colorIdx++;
                }
                bgArr.append("]"); brdArr.append("]"); errArr.append("]");
                dsJs.append("backgroundColor:").append(bgArr).append(",")
                    .append("borderColor:").append(brdArr).append(",")
                    .append("borderWidth:1,")
                    .append("errorBarColor:").append(errArr).append(",")
                    .append("errorBarWhiskerColor:").append(errArr).append(",");
            } else {
                // Multi-variable: one solid color per series
                dsJs.append("backgroundColor:'").append(col(ci)).append("',")
                    .append("borderColor:'").append(colS(ci)).append("',")
                    .append("borderWidth:1,")
                    .append("errorBarColor:'").append(colS(ci)).append("',")
                    .append("errorBarWhiskerColor:'").append(colS(ci)).append("',");
            }
            dsJs.append("errorBarLineWidth:2,errorBarWhiskerLineWidth:2,errorBarWhiskerSize:8,");
            if (!o.chart.borderradius.isEmpty()) dsJs.append("borderRadius:").append(o.chart.borderradius).append(",");
            if (!o.chart.barwidth.isEmpty())     dsJs.append("barPercentage:").append(o.chart.barwidth).append(",");
            if (!o.chart.bargroupwidth.isEmpty())dsJs.append("categoryPercentage:").append(o.chart.bargroupwidth).append(",");
            dsJs.append("},\n");
            ci++;
        }
        return dsJs.toString();
    }

    /**
     * Builds the ciLine datasets string (mean + upper + lower) for a given DataSet slice.
     * Extracted from ciLine() so buildFilterData can call it for each filter slice.
     */
    String ciLineDatasets(DataSet slice) {
        if (!slice.hasOver()) return "";
        Variable ov = slice.getOverVariable();
        // v1.9.1: pass showmissingOver so (Missing) group appears when set
        List<String> groups    = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
        List<String> groupKeys = DataSet.uniqueGroupKeys(ov, o.chart.sortgroups, o.showmissingOver);
        List<Variable> nv = slice.getNumericVariables();
        int level = 95;
        try { level = Integer.parseInt(o.stats.cilevel.trim()); } catch (Exception ignore) {}
        StringBuilder dsJs = new StringBuilder();
        int ci = 0;
        for (Variable var : nv) {
            double[][] cs = ciStats(var, ov, groups, groupKeys, level);
            StringBuilder meanJs = new StringBuilder(), upperJs = new StringBuilder(),
                         lowerJs = new StringBuilder(), nJs = new StringBuilder();
            for (double[] row : cs) {
                if (row[0] < 2) {
                    meanJs.append("null,"); upperJs.append("null,");
                    lowerJs.append("null,"); nJs.append("null,");
                } else {
                    meanJs.append( String.format("%.6f,", row[1]));
                    upperJs.append(String.format("%.6f,", row[3]));
                    lowerJs.append(String.format("%.6f,", row[2]));
                    nJs.append(String.format("%.0f,", row[0]));
                }
            }
            String varLbl  = escJs(var.getDisplayName());
            String ciLabel = escJs(var.getDisplayName() + " (" + level + "% CI)");
            // Band opacity: user-controlled via cibandopacity() option, default 0.18
            double bandAlpha = 0.18;
            try { bandAlpha = Double.parseDouble(o.stats.cibandopacity.trim()); } catch (Exception ignore) {}
            String bandFill = toRgba(colS(ci), bandAlpha);
            String lineCol  = colS(ci);
            // Mean line: use linewidth + 1 so it stands clearly above the band
            // The +1 ensures the line is always thicker than the band borders (borderWidth:1)
            int lw = 2;
            try { lw = Integer.parseInt(o.chart.linewidth.trim()); } catch (Exception ignore) {}
            int meanLw = lw + 1;  // mean line is 1px thicker than user setting for visibility
            // Mean line first (order:0 = on top)
            // v3.1.0: Phase 1-D -- borderDash per series index, nopoints, pointhoversize on mean line only.
            // CI band boundary lines keep pointRadius:0 and their fixed borderDash:[4,3] intentionally.
            dsJs.append("{label:'").append(varLbl).append("',")
                .append("data:[").append(meanJs).append("],")
                // v2.1.0: embed n array so tooltip can show group sample size
                .append("_n:[").append(nJs).append("],")
                .append("borderColor:'").append(lineCol).append("',")
                .append("backgroundColor:'transparent',")
                .append("borderWidth:").append(meanLw).append(",")
                .append(borderDashProp(ci))
                .append("tension:").append(o.chart.smooth).append(",")
                .append("pointRadius:").append(linePointRadius()).append(",")
                .append("pointHoverRadius:").append(linePointHoverRadius()).append(",")
                .append("pointBackgroundColor:'").append(lineCol).append("',")
                .append("pointBorderColor:'").append(lineCol).append("',")
                .append("pointBorderWidth:").append(o.chart.pointborderwidth).append(",")
                .append("order:0,spanGaps:true,fill:false},\n");
            // Upper boundary (fills to lower)
            dsJs.append("{label:'").append(ciLabel).append(" upper',")
                .append("data:[").append(upperJs).append("],")
                .append("borderColor:'").append(toRgba(colS(ci), 0.4)).append("',")
                .append("borderWidth:1,borderDash:[4,3],")
                .append("backgroundColor:'").append(bandFill).append("',")
                .append("fill:'+1',tension:").append(o.chart.smooth).append(",")
                .append("pointRadius:0,order:1,spanGaps:true},\n");
            // Lower boundary (no fill -- upper already covers band)
            dsJs.append("{label:'").append(ciLabel).append(" lower',")
                .append("data:[").append(lowerJs).append("],")
                .append("borderColor:'").append(toRgba(colS(ci), 0.4)).append("',")
                .append("borderWidth:1,borderDash:[4,3],")
                .append("backgroundColor:'transparent',")
                .append("fill:false,tension:").append(o.chart.smooth).append(",")
                .append("pointRadius:0,order:1,spanGaps:true},\n");
            ci++;
        }
        return dsJs.toString();
    }

    // -- Pie / Donut -----------------------------------------------------------

    List<String> parseCustomLabels(String raw) {
        List<String> tokens = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return tokens;
        raw = raw.trim();
        // Use pipe as primary separator (avoids all Stata quote-nesting issues).
        // Fall back to space splitting only if no pipe is present (single-word labels).
        String[] parts = raw.contains("|") ? raw.split("[|]", -1) : raw.split("\\s+");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) tokens.add(t);
        }
        return tokens;
    }

    /**
     * Build a Chart.js labels array string from a list of label strings.
     * Returns e.g. "'Low','Med','High',"
     */
    String customLabelsJs(List<String> labels) {
        StringBuilder sb = new StringBuilder();
        for (String lbl : labels) sb.append("'").append(escJs(lbl)).append("',");
        return sb.toString();
    }


    /**
     * Aggregate a list of values using the requested statistic.
     * Supported: mean, sum, count, median, min, max.
     * For pie/donut charts pct is handled separately; treated as mean here.
     *
     * v1.6.1: median now routes through percentile(sorted, 50) so that
     * stat(median) bar/line charts use the Stata-compatible (n+1)*p/100
     * formula and match the stats panel and summ,detail exactly.
     * Previously used a type-6 size/2 formula which diverged from Stata.
     */

    // -- Descriptive stats (Stata-compatible) ----------------------------

    double[] stats(Variable var) {
        List<Double> vals = new ArrayList<>();
        for (Object o : var.getValues()) if (o instanceof Number) vals.add(((Number)o).doubleValue());
        int n = vals.size();
        if (n == 0) return new double[]{0,0,0,0,0,0,0,0,0};
        double sum=0, min=Double.MAX_VALUE, max=-Double.MAX_VALUE;
        for (double v : vals) { sum+=v; if(v<min)min=v; if(v>max)max=v; }
        double mean = sum / n;
        double var2 = 0;
        for (double v : vals) var2 += Math.pow(v - mean, 2);
        double sd = (n > 1) ? Math.sqrt(var2 / (n - 1)) : 0; // sample SD (N-1), matches Stata summ
        // Sort for percentiles
        List<Double> sorted = new ArrayList<>(vals);
        java.util.Collections.sort(sorted);
        double median = percentile(sorted, 50);
        double q1     = percentile(sorted, 25);
        double q3     = percentile(sorted, 75);
        double cv     = (mean != 0) ? Math.abs(sd / mean) : 0;
        return new double[]{n, mean, min, max, sd, median, q1, q3, cv};
    }

    double percentile(List<Double> sorted, double p) {
        int n = sorted.size();
        if (n == 1) return sorted.get(0);
        double h  = (n + 1) * p / 100.0;
        int    lo = (int) Math.floor(h);
        int    hi = (int) Math.ceil(h);
        // Clamp to valid 1-indexed range
        lo = Math.max(1, Math.min(n, lo));
        hi = Math.max(1, Math.min(n, hi));
        if (lo == hi) return sorted.get(lo - 1); // exact order statistic
        double frac = h - Math.floor(h);
        return sorted.get(lo - 1) + frac * (sorted.get(hi - 1) - sorted.get(lo - 1));
    }

    // =========================================================================
    // 100% STACKED BAR (v2.2.0)
    // =========================================================================
    // For each over() group (x-axis category), compute the column total by
    // summing the aggregated value of every numeric variable in that group.
    // Then express each variable's value as a percentage of that total.
    // Result: every column sums to exactly 100%, making it easy to compare
    // composition across groups regardless of absolute scale differences.
    //
    // Raw values are also stored in a parallel rawData[][] array so the tooltip
    // can show both the percentage share and the underlying raw number.
    //
    // Design:
    //   rawData[varIdx][groupIdx] = raw aggregated value (mean/sum/etc.)
    //   pctData[varIdx][groupIdx] = rawData / colTotal * 100
    //   colTotal[groupIdx]        = sum of rawData[*][groupIdx] across all vars
    //
    // Null groups (no obs): both raw and pct are emitted as null so Chart.js
    // leaves a gap rather than contributing a zero to neighbour percentages.
    // =========================================================================

    /**
     * Build 100% stacked bar datasets.
     * Each variable becomes a dataset; values are column-percentage shares.
     * Requires over() -- validated in ado.
     */
    String overDatasets100(DataSet data) {
        // v3.5.13: Two cases for stackedbar100/stackedhbar100:
        //
        // CASE A -- over() present:
        //   One dataset per OVER-GROUP, one data value per numeric variable.
        //   Column total = sum of all group aggregates for that variable.
        //   Each value = group_agg / column_total * 100.
        //   Previously this method produced one dataset per VARIABLE -> each
        //   group was trivially 100% of itself when single var. (Bug fixed.)
        //
        // CASE B -- no over(), multi-var only:
        //   One dataset per VARIABLE, single x-axis category.
        //   Each value = var_agg / sum_of_all_var_aggs * 100.

        Variable ov = data.getOverVariable();
        List<Variable> nv = data.getNumericVariables();

        if (ov != null) {
            // CASE A: datasets = over-groups, x-axis positions = variables
            List<String> groups    = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
            List<String> groupKeys = DataSet.uniqueGroupKeys(ov, o.chart.sortgroups, o.showmissingOver);
            int nVars   = nv.size();
            int nGroups = groups.size();

            // Step 1: aggregate value[vi][gi]
            double[][] raw = new double[nVars][nGroups];
            for (int vi = 0; vi < nVars; vi++) {
                Variable var = nv.get(vi);
                for (int gi = 0; gi < nGroups; gi++) {
                    String gc = sdz(groupKeys.get(gi));
                    List<Integer> rowIdx = ov.groupIndex().get(gc);
                    if (rowIdx == null && o.showmissingOver)
                        rowIdx = ov.groupIndex().get(DataSet.MISSING_SENTINEL);
                    List<Double> vals = new ArrayList<>();
                    if (rowIdx != null)
                        for (int i : rowIdx) {
                            Object v = var.getValues().get(i);
                            if (v instanceof Number) vals.add(((Number)v).doubleValue());
                        }
                    raw[vi][gi] = vals.isEmpty() ? Double.NaN : aggregate(vals, o.stats.stat);
                }
            }

            // Step 2: column total per variable (sum of absolute group aggs)
            double[] colTotal = new double[nVars];
            for (int vi = 0; vi < nVars; vi++)
                for (int gi = 0; gi < nGroups; gi++)
                    if (!Double.isNaN(raw[vi][gi])) colTotal[vi] += Math.abs(raw[vi][gi]);

            // Step 3: one dataset per group
            // v3.5.28: global palette index for color consistency across by() panels
            java.util.Map<String,Integer> globalIdx100 = new java.util.LinkedHashMap<>();
            if (o._globalOverGroups != null) {
                for (int gi2 = 0; gi2 < o._globalOverGroups.size(); gi2++)
                    globalIdx100.put(o._globalOverGroups.get(gi2), gi2);
            }
            StringBuilder sb = new StringBuilder();
            for (int gi = 0; gi < nGroups; gi++) {
                String groupLabel = groups.get(gi);
                boolean isMissing = DataSet.MISSING_SENTINEL.equals(groupLabel);
                StringBuilder dataJs = new StringBuilder();
                StringBuilder rawJs  = new StringBuilder();
                for (int vi = 0; vi < nVars; vi++) {
                    if (Double.isNaN(raw[vi][gi])) {
                        dataJs.append("null,"); rawJs.append("null,");
                    } else {
                        double pct = (colTotal[vi] > 0) ? (raw[vi][gi] / colTotal[vi] * 100.0) : 0.0;
                        dataJs.append(String.format("%.4f,", pct));
                        rawJs.append(String.format("%.6f,", raw[vi][gi]));
                    }
                }
                String displayLabel = isMissing ? "(Missing)" : groupLabel;
                // Use global palette index when available for cross-panel color consistency
                int palIdx100 = (globalIdx100.containsKey(groupLabel)) ? globalIdx100.get(groupLabel) : gi;
                sb.append("{label:'").append(escJs(displayLabel)).append("',")
                  .append("data:[").append(dataJs).append("],")
                  .append("_raw:[").append(rawJs).append("],")
                  .append(buildDatasetStyle(palIdx100, false, null, palIdx100))
                  .append("},\n");
            }
            return sb.toString();
        }

        // CASE B: no over(), one dataset per variable
        int nVars = nv.size();
        double[] raw = new double[nVars];
        for (int vi = 0; vi < nVars; vi++) {
            List<Double> vals = new ArrayList<>();
            for (Object v : nv.get(vi).getValues())
                if (v instanceof Number) vals.add(((Number)v).doubleValue());
            raw[vi] = vals.isEmpty() ? Double.NaN : aggregate(vals, o.stats.stat);
        }
        double total = 0;
        for (double r : raw) if (!Double.isNaN(r)) total += Math.abs(r);
        StringBuilder sb = new StringBuilder();
        for (int vi = 0; vi < nVars; vi++) {
            Variable var = nv.get(vi);
            String pctStr = Double.isNaN(raw[vi]) ? "null"
                : String.format("%.4f", (total > 0 ? raw[vi] / total * 100.0 : 0.0));
            String rawStr = Double.isNaN(raw[vi]) ? "null"
                : String.format("%.6f", raw[vi]);
            sb.append("{label:'").append(escJs(var.getDisplayName() + statSuffix(o.stats.stat))).append("',")
              .append("data:[").append(pctStr).append(",],")
              .append("_raw:[").append(rawStr).append(",],")
              .append(buildDatasetStyle(vi, false, var, vi))
              .append("},\n");
        }
        return sb.toString();
    }


    // =========================================================================
    // BOX PLOT / VIOLIN DATASETS (v2.4.0)
    // =========================================================================
    // Builds datasets for boxplot and violin chart types using the
    // chartjs-chart-boxplot plugin.
    //
    // The plugin accepts pre-computed stat objects so we use our own
    // Stata-compatible percentile() method (same as stats panel) rather than
    // letting the plugin compute its own. Results therefore match
    // "summarize varname, detail" exactly.
    //
    // Data point format (pre-computed):
    //   { min, q1, median, mean, q3, max, outliers:[] }
    //
    // Whisker fences: lower = Q1 - k*IQR, upper = Q3 + k*IQR
    //   where k = o.stats.whiskerfence (default 1.5, Tukey standard).
    //   Points outside fences are collected in outliers[].
    //   Whiskers are clamped to actual data min/max.
    //
    // With over(): one data point per group, one dataset per variable.
    // Without over(): one dataset, one data point per variable.
    // =========================================================================

    /**
     * Builds boxplot/violin dataset JS for the given DataSet slice.
     * Called by ChartRenderer.boxPlot().
     */
    /**
     * Builds boxplot/violin dataset JS.
     *
     * v2.4.4 coloring logic:
     *   over() present, 1 variable:
     *     One dataset, one point per group. Each box colored by GROUP (palette index gi).
     *     Uses per-element backgroundColor[] / borderColor[] arrays.
     *   over() present, multiple variables:
     *     One dataset per variable. Each DATASET gets its own palette color (uniform
     *     across all its groups). Per-element arrays not needed here.
     *   over() absent:
     *     One dataset, one point per variable. Each box colored by VARIABLE (palette index vi).
     *     Uses per-element backgroundColor[] / borderColor[] arrays.
     *
     * Violin note: the violin type expects raw number arrays, not pre-computed stat objects.
     *   isViolin flag routes data through buildViolinPoint() instead of buildBoxPoint().
     *   For violin with over() we collect raw values per group; without over() all raw values.
     */
    String boxplotDatasets(DataSet data) {
        return boxplotDatasets(data, false);
    }

    String boxplotDatasets(DataSet data, boolean isViolin) {
        // v2.4.4: parse whiskerfence, default 1.5 (Tukey)
        double k = 1.5;
        try { k = Double.parseDouble(o.stats.whiskerfence.trim()); } catch (Exception ignore) {}
        final double fence = k;

        List<Variable> nv = data.getNumericVariables();
        StringBuilder sb = new StringBuilder();

        if (data.hasOver()) {
            Variable ov = data.getOverVariable();
            List<String> groups    = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
            List<String> groupKeys = DataSet.uniqueGroupKeys(ov, o.chart.sortgroups, o.showmissingOver);

            // Helper: collect values for one variable in one group
            // (used for both violin and boxplot paths below)

            if (isViolin) {
                // v2.4.4 VIOLIN + over() FIX:
                // Violin type does NOT support per-element backgroundColor arrays.
                // It renders a KDE density shape per data point; Chart.js reads
                // dataset-level color properties only for this chart type.
                // Strategy: emit ONE DATASET PER GROUP (each with one data point).
                // Each dataset has a uniform palette color. This gives each violin
                // its own distinct color and the legend shows one entry per group.
                // With multiple numeric variables we still iterate by variable first,
                // then create sub-datasets by group inside the variable loop.
                // For the common case (single variable) this produces:
                //   dataset[0] = {label:'1', data:[rawVals_group0], bgColor: palette[0]}
                //   dataset[1] = {label:'2', data:[rawVals_group1], bgColor: palette[1]}
                //   ...
                // For multi-variable we prefix the label: "Price: 1", "Price: 2", ...
                boolean multiVar = (nv.size() > 1);
                // v2.4.10: per-dataset marker colors computed from each violin's own fill.
                // Same 3-band luminance rule as boxplot: dark fill->white, light->black, mid->grey.
                // User override (mediancolor/meancolor) still takes priority via boxMarkerColor().
                int dsIdx = 0;
                for (Variable var : nv) {
                    // v3.5.28: build local vals map for global alignment
                    java.util.Map<String,List<Double>> localVlnVals = new java.util.LinkedHashMap<>();
                    java.util.Map<String,Integer> localVlnIdx = new java.util.LinkedHashMap<>();
                    for (int gi = 0; gi < groups.size(); gi++) {
                        String gc = sdz(groupKeys.get(gi));
                        List<Integer> _riVln = ov.groupIndex().get(gc);
                        if (_riVln == null && o.showmissingOver)
                            _riVln = ov.groupIndex().get(DataSet.MISSING_SENTINEL);
                        List<Double> vals = new ArrayList<>();
                        if (_riVln != null) {
                            for (int i : _riVln) {
                                Object v = var.getValues().get(i);
                                if (v instanceof Number) vals.add(((Number)v).doubleValue());
                            }
                        }
                        localVlnVals.put(groups.get(gi), vals);
                        localVlnIdx.put(groups.get(gi), gi);
                    }
                    boolean useGlobalVln = (o._globalOverGroups != null);
                    List<String> renderGroupsVln = useGlobalVln ? o._globalOverGroups : groups;

                    for (int gi = 0; gi < renderGroupsVln.size(); gi++) {
                        String grpLabel = renderGroupsVln.get(gi);
                        List<Double> vals = localVlnVals.containsKey(grpLabel)
                            ? localVlnVals.get(grpLabel) : new ArrayList<>();
                        // Dataset label: just the group label (or "VarName: group" if multi-var)
                        String dsLabel = multiVar
                            ? escJs(var.getDisplayName() + ": " + grpLabel)
                            : escJs(grpLabel);
                        sb.append("{label:'").append(dsLabel).append("',");
                        // Violin null-padding: data spans ALL positions; this group's position = gi
                        sb.append("data:[");
                        for (int gj = 0; gj < renderGroupsVln.size(); gj++) {
                            if (gj == gi) {
                                if (vals.isEmpty()) sb.append("null,");
                                else sb.append(buildViolinPoint(vals)).append(",");
                            } else {
                                sb.append("null,");
                            }
                        }
                        sb.append("],");
                        // Use global index gi for palette color (correct across panels)
                        String c   = col(gi);
                        String cs  = colS(gi);
                        String violinMedC  = boxMarkerColorFromFill(c, false);
                        String violinMeanC = boxMarkerColorFromFill(c, true);
                        sb.append("backgroundColor:'").append(c).append("',");
                        sb.append("borderColor:'").append(cs).append("',");
                        sb.append("borderWidth:2,");
                        sb.append("medianColor:'").append(violinMedC).append("',");
                        sb.append("meanBackgroundColor:'").append(violinMeanC).append("',");
                        sb.append("meanBorderColor:'").append(markerBorderColor(violinMeanC)).append("',");
                        sb.append("medianRadius:5,");
                        sb.append("meanRadius:5,");
                        sb.append("medianBorderWidth:2,");
                        sb.append("medianBorderColor:'").append(markerBorderColor(violinMedC)).append("',");
                        sb.append("meanBorderWidth:2,");
                        sb.append("minStats:'min',maxStats:'max',");
                        if (!o.chart.barwidth.isEmpty())     sb.append("barPercentage:").append(o.chart.barwidth).append(",");
                        if (!o.chart.bargroupwidth.isEmpty())sb.append("categoryPercentage:").append(o.chart.bargroupwidth).append(",");
                        sb.append("},\n");
                        dsIdx++;
                    }
                }

            } else {
                // BOXPLOT + over():
                // One dataset per variable. Per-element color arrays give each group-box
                // its own palette color (colorByGroup path) when there is only one variable.
                // With multiple variables each dataset gets a uniform color (ci path).
                boolean colorByGroup = (nv.size() == 1);
                int ci = 0;
                for (Variable var : nv) {
                    // Collect values per group
                    // v3.5.28: build groupVals keyed by local index, then build
                    // a label->localIndex map for global alignment lookup.
                    List<List<Double>> groupVals = new ArrayList<>();
                    java.util.Map<String,Integer> labelToLocal = new java.util.LinkedHashMap<>();
                    for (int gi = 0; gi < groups.size(); gi++) {
                        String gc = sdz(groupKeys.get(gi));
                        List<Integer> _riBx = ov.groupIndex().get(gc);
                        if (_riBx == null && o.showmissingOver)
                            _riBx = ov.groupIndex().get(DataSet.MISSING_SENTINEL);
                        List<Double> vals = new ArrayList<>();
                        if (_riBx != null) {
                            for (int i : _riBx) {
                                Object v = var.getValues().get(i);
                                if (v instanceof Number) vals.add(((Number)v).doubleValue());
                            }
                        }
                        groupVals.add(vals);
                        labelToLocal.put(groups.get(gi), gi);
                    }

                    // Global alignment: when _globalOverGroups is set, iterate the full
                    // group list and null-pad absent groups (same pattern as overDatasets).
                    boolean useGlobalBx = (o._globalOverGroups != null && colorByGroup);
                    List<String> renderGroups = useGlobalBx ? o._globalOverGroups : groups;

                    if (colorByGroup) {
                        // v2.4.10: SINGLE DATASET with per-element backgroundColor[] array.
                        // One dataset -> one box per label -> correct x-axis alignment.
                        // medianColor/meanBackgroundColor are dataset-level scalars:
                        // auto white/black from avg palette luminance, user-overridable.
                        String outDotColor = gen.isDark() ? "#ff6b6b" : "#e74c3c";
                        String medC = boxMarkerColor(renderGroups.size(), false);
                        String meanC = boxMarkerColor(renderGroups.size(), true);
                        sb.append("{label:'").append(escJs(var.getDisplayName())).append("',data:[");
                        for (int gi = 0; gi < renderGroups.size(); gi++) {
                            String grpLabel = renderGroups.get(gi);
                            Integer localIdx = labelToLocal.get(grpLabel);
                            List<Double> vals = (localIdx != null) ? groupVals.get(localIdx) : new ArrayList<>();
                            if (vals.isEmpty()) sb.append("null,");
                            else sb.append(buildBoxPoint(vals, fence)).append(",");
                        }
                        sb.append("],");
                        // Per-element color arrays: use global index gi for palette
                        StringBuilder bgArr = new StringBuilder("[");
                        StringBuilder bdrArr = new StringBuilder("[");
                        for (int gi = 0; gi < renderGroups.size(); gi++) {
                            bgArr.append("'").append(col(gi)).append("',");
                            bdrArr.append("'").append(colS(gi)).append("',");
                        }
                        bgArr.append("]"); bdrArr.append("]");
                        sb.append("backgroundColor:").append(bgArr).append(",");
                        sb.append("borderColor:").append(bdrArr).append(",");
                        sb.append("borderWidth:2,");
                        sb.append("outlierBackgroundColor:'transparent',");
                        sb.append("outlierBorderColor:'").append(outDotColor).append("',");
                        sb.append("outlierBorderWidth:1.5,");
                        sb.append("outlierRadius:4,");
                        sb.append("medianColor:'").append(medC).append("',");
                        sb.append("meanBackgroundColor:'").append(meanC).append("',");
                        sb.append("meanBorderColor:'").append(markerBorderColor(meanC)).append("',");
                        sb.append("medianRadius:5,");
                        sb.append("meanRadius:5,");
                        sb.append("medianBorderWidth:2,");
                        sb.append("medianBorderColor:'").append(markerBorderColor(medC)).append("',");
                        sb.append("meanBorderWidth:2,");
                        sb.append("minStats:'min',maxStats:'max',");
                        if (!o.chart.barwidth.isEmpty())      sb.append("barPercentage:").append(o.chart.barwidth).append(",");
                        if (!o.chart.bargroupwidth.isEmpty()) sb.append("categoryPercentage:").append(o.chart.bargroupwidth).append(",");
                        sb.append("},\n");
                    } else {
                        // Multi-variable: one dataset per variable, uniform color per ci.
                        sb.append("{label:'").append(escJs(var.getDisplayName())).append("',data:[");
                        for (int gi = 0; gi < groups.size(); gi++) {
                            List<Double> vals = groupVals.get(gi);
                            if (vals.isEmpty()) sb.append("null,");
                            else sb.append(buildBoxPoint(vals, fence)).append(",");
                        }
                        sb.append("],");
                        sb.append(buildBoxDatasetStyle(ci, nv.size()));
                        // v2.4.4: minStats/maxStats at DATASET level for outlier y-range
                        sb.append("minStats:'min',maxStats:'max',");
                        if (!o.chart.barwidth.isEmpty())      sb.append("barPercentage:").append(o.chart.barwidth).append(",");
                        if (!o.chart.bargroupwidth.isEmpty()) sb.append("categoryPercentage:").append(o.chart.bargroupwidth).append(",");
                        sb.append("},\n");
                    }
                    ci++;
                }
            }

        } else {
            // v2.4.8 FIX E3: No over() -- ONE DATASET PER VARIABLE.
            // Each variable gets its own uniform palette color col(vi), so
            // medianColorFor(col(vi)) computes correct contrast per box.
            // Violin no-over: single raw-array dataset (unchanged path).
            if (isViolin) {
                // Violin with no over(): single dataset, all boxes raw arrays
                sb.append("{label:'Distribution',data:[");
                int vi = 0;
                for (Variable var : data.getNumericVariables()) {
                    List<Double> vals = new ArrayList<>();
                    for (Object v : var.getValues())
                        if (v instanceof Number) vals.add(((Number)v).doubleValue());
                    if (vals.isEmpty()) sb.append("null,");
                    else sb.append(buildViolinPoint(vals)).append(",");
                    vi++;
                }
                sb.append("],");
                // v2.4.9 F1: global median/mean color based on avg luminance of all vars used
                int nVarsViolin = data.getNumericVariables().size();
                String c0 = col(0); String cs0 = colS(0);
                String medC0  = boxMarkerColor(nVarsViolin, false);
                String meanC0 = boxMarkerColor(nVarsViolin, true);
                sb.append("backgroundColor:'").append(c0).append("',");
                sb.append("borderColor:'").append(cs0).append("',");
                sb.append("borderWidth:2,");
                sb.append("medianColor:'").append(medC0).append("',");
                sb.append("meanBackgroundColor:'").append(meanC0).append("',");
                sb.append("meanBorderColor:'").append(markerBorderColor(meanC0)).append("',");
                // v2.4.9 F2: meanRadius 5 + meanBorderWidth 2 for visibility on violin
                sb.append("medianRadius:5,");
                        sb.append("meanRadius:5,");
                sb.append("medianBorderWidth:2,");
                sb.append("medianBorderColor:'").append(markerBorderColor(medC0)).append("',");
                        sb.append("meanBorderWidth:2,");
                sb.append("minStats:'min',maxStats:'max',");
                if (!o.chart.barwidth.isEmpty())      sb.append("barPercentage:").append(o.chart.barwidth).append(",");
                if (!o.chart.bargroupwidth.isEmpty()) sb.append("categoryPercentage:").append(o.chart.bargroupwidth).append(",");
                sb.append("},\n");
            } else {
                // Boxplot no over(): v2.6.1 FIX -- use ONE dataset with all boxes in sequence.
                // Previously used one-dataset-per-variable with null-padding, which caused the
                // plugin to render them as grouped bars at the same x-positions (only one box
                // would land centered; others were offset). Mirroring the over()+colorByGroup
                // pattern (single dataset, per-element color arrays) fixes alignment.
                String outDotColor = gen.isDark() ? "#ff6b6b" : "#e74c3c";
                List<Variable> noOverVars = data.getNumericVariables();
                int nVarsBP = noOverVars.size();
                String gNoOverMedC  = boxMarkerColor(nVarsBP, false);
                String gNoOverMeanC = boxMarkerColor(nVarsBP, true);
                // Single dataset: label = first variable name (legend shows variable name not groups)
                // Per-element color arrays: vi-th box gets col(vi)
                sb.append("{label:'").append(escJs(noOverVars.get(0).getDisplayName())).append("',data:[");
                int vi2 = 0;
                for (Variable var : noOverVars) {
                    List<Double> vals = new ArrayList<>();
                    for (Object v : var.getValues())
                        if (v instanceof Number) vals.add(((Number)v).doubleValue());
                    if (vals.isEmpty()) sb.append("null,");
                    else sb.append(buildBoxPoint(vals, fence)).append(",");
                    vi2++;
                }
                sb.append("],");
                // Per-element color arrays so each box gets its palette color
                StringBuilder bgArr  = new StringBuilder("[");
                StringBuilder bdrArr = new StringBuilder("[");
                for (int vi3 = 0; vi3 < nVarsBP; vi3++) {
                    bgArr.append("'").append(col(vi3)).append("',");
                    bdrArr.append("'").append(colS(vi3)).append("',");
                }
                bgArr.append("]");  bdrArr.append("]");
                sb.append("backgroundColor:").append(bgArr).append(",");
                sb.append("borderColor:").append(bdrArr).append(",");
                sb.append("borderWidth:2,");
                sb.append("outlierBackgroundColor:'transparent',");
                sb.append("outlierBorderColor:'").append(outDotColor).append("',");
                sb.append("outlierBorderWidth:1.5,");
                sb.append("outlierRadius:4,");
                sb.append("medianColor:'").append(gNoOverMedC).append("',");
                sb.append("meanBackgroundColor:'").append(gNoOverMeanC).append("',");
                sb.append("meanBorderColor:'").append(markerBorderColor(gNoOverMeanC)).append("',");
                sb.append("medianRadius:5,");
                sb.append("meanRadius:5,");
                sb.append("medianBorderWidth:2,");
                sb.append("medianBorderColor:'").append(markerBorderColor(gNoOverMedC)).append("',");
                sb.append("meanBorderWidth:2,");
                sb.append("minStats:'min',maxStats:'max',");
                if (!o.chart.barwidth.isEmpty())      sb.append("barPercentage:").append(o.chart.barwidth).append(",");
                if (!o.chart.bargroupwidth.isEmpty()) sb.append("categoryPercentage:").append(o.chart.bargroupwidth).append(",");
                sb.append("},\n");
            }   // end if (isViolin) / else
        }       // end } else { // no over()
        return sb.toString();
    }

    /**
     * Builds the x-axis labels for a boxplot/violin chart.
     * With over(): group labels (same as overLabels).
     * Without over(): variable display names.
     */
    String boxplotLabels(DataSet data) {
        if (data.hasOver()) return overLabels(data);
        // No over: one label per numeric variable
        StringBuilder sb = new StringBuilder();
        for (Variable var : data.getNumericVariables())
            sb.append("'").append(escJs(var.getDisplayName())).append("',");
        return sb.toString();
    }

    // =========================================================================
    // CUSTOM VIOLIN -- v2.5.0
    // No external plugin. KDE computed in Java (Gaussian kernel, Silverman
    // bandwidth). Rendered entirely via afterDraw canvas plugin in ChartRenderer.
    // =========================================================================

    /**
     * Builds the _violinData JS variable declaration used by the custom violin
     * afterDraw plugin. Returns a complete JS statement:
     *   var _violinData=[{label,xIdx,color,borderColor,kde,
     *                     median,mean,q1,q3,whiskerLo,whiskerHi,
     *                     min,max,n,medianColor,meanColor}, ...];
     *
     * One entry per group (over() groups) or per variable (no over()).
     * xIdx is the 0-based position on the category x-axis.
     * kde is an array of [yValue, normalizedEstimate] pairs (50 points),
     * where normalizedEstimate is in [0,1] -- the plugin scales to pixels.
     *
     * Bandwidth: o.stats.bandwidth if non-empty positive number, else Silverman rule.
     * v2.5.0
     */
    String violinData(DataSet data) {
        return "var _violinData=" + violinDataArray(data) + ";\n";
    }

    /**
     * Returns just the JS array literal [...] for violin data, without the
     * var _violinData= declaration. Used by FilterRenderer to embed the array
     * directly as a property value in _dashData: {labels:[...], violinData:[...]}.
     * The top-level declaration form (violinData) is used for the initial chart setup.
     * v2.5.1
     */
    String violinDataArray(DataSet data) {
        double fence = 1.5;
        try { fence = Double.parseDouble(o.stats.whiskerfence); } catch (Exception e) {}
        double bwOverride = 0;
        try {
            double bw = Double.parseDouble(o.stats.bandwidth);
            if (bw > 0) bwOverride = bw;
        } catch (Exception e) {}

        StringBuilder sb = new StringBuilder("[\n");

        if (data.hasOver()) {
            Variable ov = data.getOverVariable();
            List<Variable> nv = data.getNumericVariables();
            List<String> groups = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
            List<String> groupKeys = DataSet.uniqueGroupKeys(ov, o.chart.sortgroups, o.showmissingOver);
            boolean multiVar = nv.size() > 1;
            // Single-var: use _globalOverGroups for consistent xIdx across by() panels.
            // Multi-var: cannot use global expansion for xIdx/labels (label count mismatch),
            // but build globalColorMap so each group always gets same palette color in every panel.
            boolean useGlobalVDA = (o._globalOverGroups != null) && !multiVar;
            List<String> renderGroupsVDA = useGlobalVDA ? o._globalOverGroups : groups;
            java.util.Map<String,Integer> localVDAIdx = new java.util.LinkedHashMap<>();
            for (int gi = 0; gi < groups.size(); gi++)
                localVDAIdx.put(groups.get(gi), gi);
            // v3.5.29: global color map for multi-var by() panels so same group
            // always uses the same palette index across panels regardless of local gi.
            java.util.Map<String,Integer> globalColorMap = new java.util.LinkedHashMap<>();
            if (o._globalOverGroups != null) {
                for (int gi2 = 0; gi2 < o._globalOverGroups.size(); gi2++)
                    globalColorMap.put(o._globalOverGroups.get(gi2), gi2);
            }
            // v3.5.31: Group-major order: for each group, emit one entry per variable.
            // Labels: "Price: 1, Weight: 1, Price: 2, Weight: 2, ..."
            // xIdx sequential; color: per-variable index (vi) so Price=col(0),
            // Weight=col(1) consistently regardless of group.
            // Single-var: group outer loop only, gi used for xIdx and color.
            int xIdxCounter = 0;
            if (!multiVar) {
                // Single-var: group-major is same as var-major (one var)
                for (int gi = 0; gi < renderGroupsVDA.size(); gi++) {
                    String g  = renderGroupsVDA.get(gi);
                    Integer localGi = localVDAIdx.get(g);
                    String gk = localGi != null ? sdz(groupKeys.get(localGi)) : null;
                    List<Integer> _riVDA = (gk != null) ? ov.groupIndex().get(gk) : null;
                    if (_riVDA == null && o.showmissingOver)
                        _riVDA = ov.groupIndex().get(DataSet.MISSING_SENTINEL);
                    Variable var = nv.get(0);
                    List<Double> vals = new ArrayList<>();
                    if (_riVDA != null) {
                        for (int i : _riVDA) {
                            Object v = var.getValues().get(i);
                            if (v instanceof Number) vals.add(((Number)v).doubleValue());
                        }
                    }
                    String fillColor = col(gi);
                    String borderColor = colS(gi);
                    String medC  = boxMarkerColorFromFill(fillColor, false);
                    String meanC = boxMarkerColorFromFill(fillColor, true);
                    sb.append(buildViolinEntry(escJs(g), gi, fillColor, borderColor,
                                               medC, meanC, vals, fence, bwOverride));
                    sb.append(",\n");
                }
            } else {
                // Multi-var: group-major -- "Price: 1, Weight: 1, Price: 2, Weight: 2, ..."
                // Color by variable index (vi) so Price is always col(0), Weight col(1), etc.
                // This matches Stata graph box color convention (color by variable, not group).
                for (int gi = 0; gi < renderGroupsVDA.size(); gi++) {
                    String g  = renderGroupsVDA.get(gi);
                    Integer localGi = localVDAIdx.get(g);
                    String gk = localGi != null ? sdz(groupKeys.get(localGi)) : null;
                    List<Integer> _riVDA = (gk != null) ? ov.groupIndex().get(gk) : null;
                    if (_riVDA == null && o.showmissingOver)
                        _riVDA = ov.groupIndex().get(DataSet.MISSING_SENTINEL);
                    for (int vi = 0; vi < nv.size(); vi++) {
                        Variable var = nv.get(vi);
                        List<Double> vals = new ArrayList<>();
                        if (_riVDA != null) {
                            for (int i : _riVDA) {
                                Object v = var.getValues().get(i);
                                if (v instanceof Number) vals.add(((Number)v).doubleValue());
                            }
                        }
                        String label = escJs(var.getDisplayName() + ": " + g);
                        // vi = variable index -> consistent color per variable across all groups
                        String fillColor = col(vi);
                        String borderColor = colS(vi);
                        String medC  = boxMarkerColorFromFill(fillColor, false);
                        String meanC = boxMarkerColorFromFill(fillColor, true);
                        sb.append(buildViolinEntry(label, xIdxCounter, fillColor, borderColor,
                                                   medC, meanC, vals, fence, bwOverride));
                        sb.append(",\n");
                        xIdxCounter++;
                    }
                }
            }
        } else {
            List<Variable> nv = data.getNumericVariables();
            for (int vi = 0; vi < nv.size(); vi++) {
                Variable var = nv.get(vi);
                List<Double> vals = new ArrayList<>();
                for (Object v : var.getValues())
                    if (v instanceof Number) vals.add(((Number)v).doubleValue());
                String fillColor   = col(vi);
                String borderColor = colS(vi);
                String medC  = boxMarkerColorFromFill(fillColor, false);
                String meanC = boxMarkerColorFromFill(fillColor, true);
                sb.append(buildViolinEntry(escJs(var.getDisplayName()), vi,
                                           fillColor, borderColor,
                                           medC, meanC, vals, fence, bwOverride));
                sb.append(",\n");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Builds one _violinData entry JS object literal for a single group/variable.
     * vals may be empty -- returns a null-data entry so the x-axis slot exists.
     * v2.5.0
     */
    private String buildViolinEntry(String label, int xIdx,
                                    String fillColor, String borderColor,
                                    String medC, String meanC,
                                    List<Double> vals, double fence, double bwOverride) {
        if (vals.isEmpty()) {
            return "{label:'" + label + "',xIdx:" + xIdx
                 + ",color:'" + fillColor + "',borderColor:'" + borderColor + "'"
                 + ",kde:[],median:null,mean:null,q1:null,q3:null"
                 + ",whiskerLo:null,whiskerHi:null,min:null,max:null,n:0"
                 + ",medianColor:'" + medC + "',meanColor:'" + meanC + "'}";
        }
        Collections.sort(vals);
        int n = vals.size();
        double q1     = percentile(vals, 25);
        double median = percentile(vals, 50);
        double q3     = percentile(vals, 75);
        double iqr    = q3 - q1;
        double lo     = q1 - fence * iqr;
        double hi     = q3 + fence * iqr;
        double wLo    = vals.get(0);
        double wHi    = vals.get(n-1);
        for (double v : vals) { if (v >= lo) { wLo = v; break; } }
        for (int i = n-1; i >= 0; i--) { if (vals.get(i) <= hi) { wHi = vals.get(i); break; } }
        double sum = 0; for (double v : vals) sum += v;
        double mean = sum / n;
        // Track axis bounds (reuse _boxYMin/_boxYMax for scale computation)
        if (vals.get(0)   < o._boxYMin) o._boxYMin = vals.get(0);
        if (vals.get(n-1) > o._boxYMax) o._boxYMax = vals.get(n-1);

        // KDE
        double[][] kde = computeKde(vals, bwOverride, 50);
        // Normalize estimates to [0,1] -- renderer scales to pixels
        double maxEst = 0;
        for (double[] pt : kde) if (pt[1] > maxEst) maxEst = pt[1];
        StringBuilder kdeSb = new StringBuilder("[");
        for (double[] pt : kde) {
            double norm = (maxEst > 0) ? pt[1] / maxEst : 0;
            kdeSb.append(String.format("[%.6f,%.6f],", pt[0], norm));
        }
        kdeSb.append("]");

        return String.format(
            "{label:'%s',xIdx:%d,color:'%s',borderColor:'%s',"
            + "kde:%s,"
            + "median:%.6f,mean:%.6f,q1:%.6f,q3:%.6f,"
            + "whiskerLo:%.6f,whiskerHi:%.6f,min:%.6f,max:%.6f,n:%d,"
            + "medianColor:'%s',meanColor:'%s'}",
            label, xIdx, fillColor, borderColor,
            kdeSb.toString(),
            median, mean, q1, q3,
            wLo, wHi, vals.get(0), vals.get(n-1), n,
            medC, meanC);
    }

    /**
     * Computes a Gaussian KDE over nPoints evenly spaced across [vals.get(0), vals.get(n-1)].
     * Eval range is clamped to observed data [min, max] -- the kernel uses all data points
     * for density estimation but we do not evaluate tails beyond the data boundary.
     * This prevents the violin shape from extending outside the y-axis range.
     * Bandwidth: bwOverride if > 0, else Silverman rule h = 1.06 * sd * n^(-0.2).
     * Returns double[nPoints][2]: {yValue, estimate}.
     * v2.5.1: clamped to [min, max] (was min-h to max+h which caused axis overflow)
     */
    private double[][] computeKde(List<Double> vals, double bwOverride, int nPoints) {
        int n = vals.size();
        // Mean and SD for Silverman bandwidth
        double sum = 0;
        for (double v : vals) sum += v;
        double mn = sum / n;
        double variance = 0;
        for (double v : vals) variance += (v - mn) * (v - mn);
        double sd = n > 1 ? Math.sqrt(variance / (n - 1)) : 0;
        double h = (bwOverride > 0) ? bwOverride
                 : (sd > 0 ? 1.06 * sd * Math.pow(n, -0.2) : (vals.get(n-1) - vals.get(0)) * 0.1);
        if (h <= 0) h = 1.0; // safety floor

        // v2.5.1: clamp eval range to actual data bounds -- no tails beyond observed data.
        // The kernel still uses all data points for estimation; only eval points are clamped.
        double dMin = vals.get(0);
        double dMax = vals.get(n-1);
        // If all values are identical, spread slightly so step > 0
        if (dMax <= dMin) { dMin -= h; dMax += h; }
        double step = (dMax - dMin) / (nPoints - 1);
        double invNH = 1.0 / (n * h);
        double sqrt2pi = Math.sqrt(2 * Math.PI);

        double[][] kde = new double[nPoints][2];
        for (int i = 0; i < nPoints; i++) {
            double x = dMin + i * step;
            double est = 0;
            for (double v : vals) {
                double u = (x - v) / h;
                est += Math.exp(-0.5 * u * u) / sqrt2pi;
            }
            kde[i][0] = x;
            kde[i][1] = est * invNH;
        }
        return kde;
    }

    /**
     * Returns category labels for the custom violin x-axis.
     * Single-var: delegates to boxplotLabels (over-group labels or var names).
     * Multi-var + over(): returns compound "VarName: group" labels in var-major
     * order, matching the sequential xIdx assigned in violinDataArray.
     * v3.5.29
     */
    String violinLabels(DataSet data) {
        if (data.hasOver() && data.getNumericVariables().size() > 1) {
            // Group-major order: "Price: 1, Weight: 1, Price: 2, Weight: 2, ..."
            // Matches Stata graph box behavior: groups on x-axis, vars side-by-side.
            Variable ov = data.getOverVariable();
            List<String> groups = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
            List<Variable> nv = data.getNumericVariables();
            StringBuilder sb = new StringBuilder();
            for (String g : groups) {
                for (Variable var : nv) {
                    sb.append("'").append(escJs(var.getDisplayName() + ": " + g)).append("',");
                }
            }
            return sb.toString();
        }
        return boxplotLabels(data);
    }

    /**
     * v2.4.10: Returns JS array literal of medianColor strings, one per violin dataset.
     * Used to inject per-dataset medianColor via options.datasets.violin scriptable function,
     * which is the only reliable way to override medianColor in Chart.js 4.x.
     * Dataset order must match boxplotDatasets(data, true) exactly.
     * Example output: ['#000000','#000000','#ffffff','#000000']
     */
    String violinMedColorArray(DataSet data) {
        StringBuilder sb = new StringBuilder("[");
        if (data.hasOver()) {
            List<Variable> nv = data.getNumericVariables();
            Variable ov = data.getOverVariable();
            List<String> groups = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
            // v3.5.28: use global index so marker colors match boxplotDatasets violin path
            boolean useGlobalVlnMed = (o._globalOverGroups != null);
            List<String> renderGrpsMed = useGlobalVlnMed ? o._globalOverGroups : groups;
            int dsIdx = 0;
            for (int vi = 0; vi < nv.size(); vi++) {
                for (int gi = 0; gi < renderGrpsMed.size(); gi++) {
                    sb.append("'").append(boxMarkerColorFromFill(col(gi), false)).append("',");
                    dsIdx++;
                }
            }
        } else {
            List<Variable> nv = data.getNumericVariables();
            int nVarsViolin = nv.size();
            for (int vi = 0; vi < nVarsViolin; vi++) {
                sb.append("'").append(boxMarkerColor(nVarsViolin, false)).append("',");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * v2.4.10: Returns JS array literal of meanColor strings, one per violin dataset.
     * Same structure as violinMedColorArray -- used for meanBackgroundColor scriptable option.
     */
    String violinMeanColorArray(DataSet data) {
        StringBuilder sb = new StringBuilder("[");
        if (data.hasOver()) {
            List<Variable> nv = data.getNumericVariables();
            Variable ov = data.getOverVariable();
            List<String> groups = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
            // v3.5.28: use global index so marker colors match boxplotDatasets violin path
            boolean useGlobalVlnMean = (o._globalOverGroups != null);
            List<String> renderGrpsMean = useGlobalVlnMean ? o._globalOverGroups : groups;
            int dsIdx = 0;
            for (int vi = 0; vi < nv.size(); vi++) {
                for (int gi = 0; gi < renderGrpsMean.size(); gi++) {
                    sb.append("'").append(boxMarkerColorFromFill(col(gi), true)).append("',");
                    dsIdx++;
                }
            }
        } else {
            List<Variable> nv = data.getNumericVariables();
            int nVarsViolin = nv.size();
            for (int vi = 0; vi < nVarsViolin; vi++) {
                sb.append("'").append(boxMarkerColor(nVarsViolin, true)).append("',");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Builds a raw number array JS string for violin data points.
     * The chartjs-chart-boxplot violin type expects number[] not pre-computed stats.
     * Returns "[v1,v2,v3,...]" -- the plugin computes its own KDE from this.
     */
    private String buildViolinPoint(List<Double> vals) {
        StringBuilder sb = new StringBuilder("[");
        for (double v : vals) sb.append(String.format("%.6f,", v));
        sb.append("]");
        return sb.toString();
    }

    /**
     * Builds a single pre-computed box point JS object.
     * { min:x, q1:x, median:x, mean:x, q3:x, max:x, outliers:[...] }
     * Whiskers are clamped to actual data min/max.
     */
    private String buildBoxPoint(List<Double> vals, double k) {
        Collections.sort(vals);
        int n = vals.size();
        double q1     = percentile(vals, 25);
        double median = percentile(vals, 50);
        double q3     = percentile(vals, 75);
        double iqr    = q3 - q1;
        double lo     = q1 - k * iqr;
        double hi     = q3 + k * iqr;
        // Whisker tips: closest actual data point inside fence
        double wLo = vals.get(0);   // default: actual min
        double wHi = vals.get(n-1); // default: actual max
        for (double v : vals) { if (v >= lo) { wLo = v; break; } }
        for (int i = n-1; i >= 0; i--) { if (vals.get(i) <= hi) { wHi = vals.get(i); break; } }
        // Mean
        double sum = 0; for (double v : vals) sum += v;
        double mean = sum / n;
        // Outliers: points strictly outside fences
        StringBuilder out = new StringBuilder("[");
        for (double v : vals) if (v < lo || v > hi) out.append(String.format("%.6f,", v));
        out.append("]");
        // v2.4.4: track overall data range including outliers for explicit axis bounds
        if (vals.get(0)   < o._boxYMin) o._boxYMin = vals.get(0);
        if (vals.get(n-1) > o._boxYMax) o._boxYMax = vals.get(n-1);
        return String.format(
            "{min:%.6f,q1:%.6f,median:%.6f,mean:%.6f,q3:%.6f,max:%.6f,outliers:%s}",
            wLo, q1, median, mean, q3, wHi, out.toString());
    }

    /**
     * Style properties for a box/violin dataset.
     *
     * v2.4.4 changes:
     *   - medianColor/meanColor: contrasting white (light theme) or dark (dark theme)
     *     so the lines are clearly visible against the filled box.
     *   - borderWidth: 2 (was 1) -- box outline is easier to read.
     *   - outlierBackgroundColor: accent red so outliers pop visually.
     *   - Per-element color arrays handled by callers (overColorArray / noOverColorArray).
     *     This method only provides the non-color style properties; callers append colors.
     */
    private String buildBoxDatasetStyle(int ci, int nColors) {
        // Contrasting color for median/mean lines -- white on light theme reads well
        // against the palette fill. Dark theme uses near-white too since box is colored.
        // v2.4.5: median=#ffffff white, mean=#f59e0b amber -- distinct from each other
        String outDotColor  = gen.isDark() ? "#ff6b6b" : "#e74c3c";  // red outlier dots
        StringBuilder sb = new StringBuilder();
        sb.append("backgroundColor:'").append(col(ci)).append("',");
        sb.append("borderColor:'").append(colS(ci)).append("',");
        sb.append("borderWidth:2,");
        // v2.4.7: hollow outlier dots (transparent fill, colored border only)
        sb.append("outlierBackgroundColor:'transparent',");
        sb.append("outlierBorderColor:'").append(outDotColor).append("',");
        sb.append("outlierBorderWidth:1.5,");
        sb.append("outlierRadius:4,");
        // v2.4.9 F1: global median/mean colors; nColors passed by caller (nv.size())
        String medC2  = boxMarkerColor(nColors, false);
        String meanC2 = boxMarkerColor(nColors, true);
        sb.append("medianColor:'").append(medC2).append("',");
        sb.append("meanBackgroundColor:'").append(meanC2).append("',");
        sb.append("meanBorderColor:'").append(markerBorderColor(meanC2)).append("',");
        // v2.4.9 F2: meanRadius 5 + meanBorderWidth 2 for visibility
        sb.append("medianRadius:5,");
                        sb.append("meanRadius:5,");
        sb.append("medianBorderWidth:2,");
        sb.append("medianBorderColor:'").append(markerBorderColor(medC2)).append("',");
                        sb.append("meanBorderWidth:2,");
        if (!o.chart.barwidth.isEmpty())     sb.append("barPercentage:").append(o.chart.barwidth).append(",");
        if (!o.chart.bargroupwidth.isEmpty())sb.append("categoryPercentage:").append(o.chart.bargroupwidth).append(",");
        return sb.toString();
    }

    /** Returns sRGB relative luminance of a fill color string. (v2.4.6)
     *  Light fill (lum>0.35): median dark, mean deep-orange.
     *  Dark fill: median white, mean amber. */
    private double boxFillLuminance(String color) {
        int r=78,g=121,b=167;
        try {
            color = color.trim();
            if (color.startsWith("#")) {
                String hx = color.substring(1);
                if (hx.length()==3) hx=""+hx.charAt(0)+hx.charAt(0)+hx.charAt(1)+hx.charAt(1)+hx.charAt(2)+hx.charAt(2);
                r=Integer.parseInt(hx.substring(0,2),16);
                g=Integer.parseInt(hx.substring(2,4),16);
                b=Integer.parseInt(hx.substring(4,6),16);
            } else if (color.startsWith("rgba")||color.startsWith("rgb")) {
                java.util.regex.Matcher m=java.util.regex.Pattern.compile(
                    "rgba?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)").matcher(color);
                if (m.find()) { r=Integer.parseInt(m.group(1)); g=Integer.parseInt(m.group(2)); b=Integer.parseInt(m.group(3)); }
            }
        } catch (Exception e) {}
        double rs=r/255.0, gs=g/255.0, bs=b/255.0;
        rs=rs<=0.03928?rs/12.92:Math.pow((rs+0.055)/1.055,2.4);
        gs=gs<=0.03928?gs/12.92:Math.pow((gs+0.055)/1.055,2.4);
        bs=bs<=0.03928?bs/12.92:Math.pow((bs+0.055)/1.055,2.4);
        return 0.2126*rs + 0.7152*gs + 0.0722*bs;
    }
    /** Median marker color: dark on light fill, white on dark fill. (v2.4.6) */
    private String medianColorFor(String fill) {
        return boxFillLuminance(fill) > 0.35 ? "#1a1a1a" : "#ffffff";
    }

    /**
     * v2.4.10: Per-fill marker color for violin datasets.
     * Same 3-band luminance rule as boxMarkerColor but applied to a single
     * known fill color rather than averaging across N palette entries.
     * User override (mediancolor/meancolor) still takes priority.
     *   fill lum > 0.60  (near-white) -> #555555 grey
     *   fill lum > 0.15  (normal)     -> #000000 black
     *   fill lum <= 0.15 (dark)       -> #ffffff white
     * dark theme shortcut still applies.
     */
    private String boxMarkerColorFromFill(String fillColor, boolean isMean) {
        String override = isMean ? o.stats.meancolor.trim() : o.stats.mediancolor.trim();
        if (!override.isEmpty()) return override;
        if (gen.isDark()) return "#ffffff";
        double lum = boxFillLuminance(fillColor);
        if (lum > 0.60) return "#555555";
        if (lum > 0.15) return "#000000";
        return "#ffffff";
    }
    /**
     * v2.4.10: Contrasting border color for a marker (median diamond or mean dot).
     * Computes luminance of the marker color itself and returns the inverse:
     *   light marker -> black border (#000000)
     *   dark marker  -> white border (#ffffff)
     * This ensures the marker is always visible against any fill color,
     * including when the user supplies a mediancolor()/meancolor() that
     * happens to match a palette fill. Applied to both medianBorderColor
     * and meanBorderColor consistently. (v2.4.10)
     */
    private String markerBorderColor(String markerColor) {
        return boxFillLuminance(markerColor) > 0.35 ? "#000000" : "#ffffff";
    }
    /**
     * v2.4.10: Simple box/violin marker color helper.
     * Decision tree (first match wins):
     *   1. User override (mediancolor/meancolor option)  -> use it
     *   2. theme == dark                                 -> #ffffff (dark canvas = white markers)
     *   3. avg palette lum > 0.60 (near-white fills)    -> #555555 grey (black too harsh)
     *   4. avg palette lum > 0.15 (normal light fills)  -> #000000 black
     *   5. avg palette lum <= 0.15 (dark fills)         -> #ffffff white
     * Cases 2 and 5 are symmetric: both dark contexts -> white markers.
     */
    private String boxMarkerColor(int nColors, boolean isMean) {
        String override = isMean ? o.stats.meancolor.trim() : o.stats.mediancolor.trim();
        if (!override.isEmpty()) return override;
        if (gen.isDark()) return "#ffffff";  // dark theme always gets white
        if (nColors <= 0) return "#000000";
        double sum = 0;
        for (int i = 0; i < nColors; i++) sum += boxFillLuminance(col(i));
        double lum = sum / nColors;
        if (lum > 0.60) return "#555555";   // near-white palette: grey
        if (lum > 0.15) return "#000000";   // normal light palette: black
        return "#ffffff";                    // dark custom palette on light theme: white
    }

    /**
     * Builds a JS array string of per-element colors for a single-dataset boxplot.
     * Used when over()=absent (one box per variable) so each box gets its own palette color.
     * Also used for the violin chart.
     * nBoxes = number of data points (one per variable or one per group).
     */
    private String buildBoxColorArrays(int nBoxes) {
        StringBuilder bg   = new StringBuilder("[");
        StringBuilder bdr  = new StringBuilder("[");
        StringBuilder out  = new StringBuilder("[");
        String outDotColor = gen.isDark() ? "#ff6b6b" : "#e74c3c";
        for (int i = 0; i < nBoxes; i++) {
            bg.append("'").append(col(i)).append("',");
            bdr.append("'").append(colS(i)).append("',");
            out.append("'").append(outDotColor).append("',");
        }
        bg.append("]"); bdr.append("]"); out.append("]");
        // v2.4.9 F1: global median/mean colors based on avg luminance of nBoxes palette entries.
        String medC3  = boxMarkerColor(nBoxes, false);
        String meanC3 = boxMarkerColor(nBoxes, true);
        return "backgroundColor:" + bg + ","
             + "borderColor:" + bdr + ","
             + "borderWidth:2,"
             // v2.4.7: hollow outlier dots -- transparent fill, colored border ring
             + "outlierBackgroundColor:'transparent',"
             + "outlierBorderColor:'" + outDotColor + "',"
             + "outlierBorderWidth:1.5,"
             + "outlierRadius:4,"
             + "medianColor:'" + medC3 + "',"
             + "meanBackgroundColor:'" + meanC3 + "',"
             + "meanBorderColor:'" + markerBorderColor(meanC3) + "',"
             // v2.4.9 F2: meanRadius 5 + meanBorderWidth 2 for visibility
             + "medianRadius:5,"
             + "meanRadius:5,"
             + "medianBorderWidth:2,"
             + "medianBorderColor:'" + markerBorderColor(medC3) + "',"
             + "meanBorderWidth:2,";
    }


}
