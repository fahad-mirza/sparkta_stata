package com.dashboard.html;

import com.dashboard.data.*;
import java.util.*;

/**
 * FilterRenderer -- builds filter dropdown UI, pre-computed data slices,
 * and the _applyFilter() JavaScript function.
 * v2.0.0: Extracted from HtmlGenerator.java as part of modular refactor.
 * Responsibility: everything related to filter() and filter2() interactivity:
 *   - Dropdown HTML bar (buildFilterUi)
 *   - _dashData JS object with pre-computed slices per filter combination (buildFilterData)
 *   - _applyFilter() JS function (buildFilterScript)
 *   - DataSet subsetting per filter key (sliceData)
 */
class FilterRenderer {

    private final DashboardOptions o;
    private final HtmlGenerator    gen;   // shared utilities + dataset builders
    private final DatasetBuilder   dsb;   // builds dataset strings per slice
    private final ChartRenderer    cr;    // for isCiBar/isCiLine/isHistogram type checks

    FilterRenderer(DashboardOptions o, HtmlGenerator gen, DatasetBuilder dsb, ChartRenderer cr) {
        this.o   = o;
        this.gen = gen;
        this.dsb = dsb;
        this.cr  = cr;
    }

    // Delegate shared utilities
    private String escHtml(String s)  { return gen.escHtml(s); }
    private String escJs(String s)    { return gen.escJs(s); }
    private String sdz(String s)      { return gen.sdz(s); }
    private String labelColor()       { return gen.labelColor(); }

    // Delegate subsetVar (lives in HtmlGenerator with byVar subsetting logic)
    private Variable subsetVar(Variable v, List<Integer> idx) { return gen.subsetVar(v, idx); }

    // Delegate dataset builders (used to build slice datasets)
    private String overLabels(DataSet d)                               { return dsb.overLabels(d); }
    private String overDatasets(DataSet d, boolean l, boolean log)     { return dsb.overDatasets(d, l, log); }
    private String catLabels(DataSet d)                                { return dsb.catLabels(d); }
    private String aggLabels(DataSet d)                                { return dsb.aggLabels(d); }
    private String numDatasets(DataSet d, boolean l, boolean log)      { return dsb.numDatasets(d, l, log); }
    private String ciBarLabels(DataSet d)                              { return dsb.ciBarLabels(d); }
    private String ciBarDatasets(DataSet d)                            { return dsb.ciBarDatasets(d); }
    private String ciLineDatasets(DataSet d)                           { return dsb.ciLineDatasets(d); }
    private String[] histogramFilterData(DataSet d)                    { return cr.histogramFilterData(d); }
    private String scatterDatasets(DataSet d)                          { return cr.scatterDatasets(d); }  // v2.6.2
    private String bubbleDatasets(DataSet d)                           { return cr.bubbleDatasets(d); }   // v2.6.2

    String buildFilterUi(DataSet data) {
        if (!data.hasFilter1()) return "";
        String lc = labelColor();
        String bg = gen.isDark() ? "#1e2a3a" : "#f0f0f0";
        String brd = gen.isDark() ? "#3a4a5a" : "#cccccc";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='filter-bar' style='")
          .append("background:").append(bg).append(";")
          .append("border:1px solid ").append(brd).append(";")
          .append("border-radius:6px;padding:10px 16px;margin-bottom:12px;")
          .append("display:flex;align-items:center;gap:16px;flex-wrap:wrap;'>\n")
          .append("  <span style='font-weight:600;color:").append(lc).append(";font-size:13px;'>Filter:</span>\n");

        // Filter 1 dropdown
        // v1.8.6: dropdowns always sort ascending -- sortgroups controls chart axis only
        Variable f1 = data.getFilter1Variable();
        // v1.9.1: showmissing adds "(Missing)" option to dropdown
        List<String> f1labels = DataSet.uniqueValues(f1, "asc", o.showmissingFilter);
        List<String> f1keys   = DataSet.uniqueGroupKeys(f1, "asc", o.showmissingFilter);
        sb.append("  <label style='color:").append(lc).append(";font-size:13px;'>")
          .append(escHtml(f1.getDisplayName())).append(":</label>\n")
          .append("  <select id='_f1' onchange='_applyFilter()' style='")
          .append("padding:4px 8px;border-radius:4px;border:1px solid ").append(brd).append(";")
          .append("background:").append(gen.isDark() ? "#16213e" : "#fff").append(";")
          .append("color:").append(lc).append(";font-size:13px;'>\n")
          .append("    <option value='ALL'>All ").append(escHtml(f1.getDisplayName())).append("</option>\n");
        for (int i = 0; i < f1labels.size(); i++) {
            String key = f1keys.get(i);
            if (key == null || key.trim().isEmpty()) continue;  // skip missing values
            sb.append("    <option value='").append(escHtml(key)).append("'>")
              .append(escHtml(f1labels.get(i))).append("</option>\n");
        }
        sb.append("  </select>\n");

        // Filter 2 dropdown (if present)
        if (data.hasFilter2()) {
            Variable f2 = data.getFilter2Variable();
            // v1.8.6: dropdowns always sort ascending
            // v1.9.1: showmissing adds "(Missing)" option
            List<String> f2labels = DataSet.uniqueValues(f2, "asc", o.showmissingFilter2);
            List<String> f2keys   = DataSet.uniqueGroupKeys(f2, "asc", o.showmissingFilter2);
            sb.append("  <label style='color:").append(lc).append(";font-size:13px;'>")
              .append(escHtml(f2.getDisplayName())).append(":</label>\n")
              .append("  <select id='_f2' onchange='_applyFilter()' style='")
              .append("padding:4px 8px;border-radius:4px;border:1px solid ").append(brd).append(";")
              .append("background:").append(gen.isDark() ? "#16213e" : "#fff").append(";")
              .append("color:").append(lc).append(";font-size:13px;'>\n")
              .append("    <option value='ALL'>All ").append(escHtml(f2.getDisplayName())).append("</option>\n");
            for (int i = 0; i < f2labels.size(); i++) {
                String key = f2keys.get(i);
                if (key == null || key.trim().isEmpty()) continue;  // skip missing values
                sb.append("    <option value='").append(escHtml(key)).append("'>")
                  .append(escHtml(f2labels.get(i))).append("</option>\n");
            }
            sb.append("  </select>\n");
        }

        sb.append("</div>\n");
        return sb.toString();
    }

    /**
     * Pre-compute all filter-slice combinations and embed as a JS object.
     * Key format: "f1key||f2key" where "ALL" means no filter on that dimension.
     * Each value is {labels:[...], datasets:[...]} matching Chart.js data format.
     * Returns empty string when no filters are specified.
     */
    String buildFilterData(DataSet data) {
        if (!data.hasFilter1()) return "";

        Variable f1 = data.getFilter1Variable();
        List<String> f1keys = DataSet.uniqueGroupKeys(f1, o.chart.sortgroups, o.showmissingFilter);

        // f2 may be absent -- treat as single "ALL" value
        List<String> f2keys = new ArrayList<>();
        f2keys.add("ALL");
        if (data.hasFilter2()) {
            for (String k : DataSet.uniqueGroupKeys(data.getFilter2Variable(), o.chart.sortgroups)) {
                if (k != null && !k.trim().isEmpty()) f2keys.add(k);  // skip blank/missing
            }
        }

        StringBuilder sb = new StringBuilder("var _dashData={\n");

        // v3.5.21: Set global over-groups from FULL unfiltered data so every filter
        // slice uses the same x-axis positions and color indices. Without this, a
        // slice with only rep78=3,4,5 maps values to positions 1,2,3 and resets
        // colors to col(0) instead of col(2,3,4). Same pattern as buildByScripts().
        if (data.hasOver()) {
            o._globalOverGroups = DataSet.uniqueValues(
                data.getOverVariable(), o.chart.sortgroups, o.showmissingOver);
        }

        // Build all combinations including ALL||ALL, f1val||ALL, ALL||f2val, f1val||f2val
        List<String> f1all = new ArrayList<>();
        f1all.add("ALL");
        for (String k : f1keys) {
            // v1.9.1: MISSING_SENTINEL is a valid key -- only skip truly empty strings
            if (k != null && (DataSet.MISSING_SENTINEL.equals(k) || !k.trim().isEmpty())) f1all.add(k);
        }

        boolean isCiBar     = o.type.equals("cibar");
        boolean isCiLine    = o.type.equals("ciline");
        boolean isHistogram = o.type.equals("histogram");
        // v2.5.1: boxplot and violin need their own stat-object dataset format.
        // Using plain overDatasets() produces mean numbers which the boxplot
        // plugin cannot render -- chart goes blank on filter change.
        boolean isBoxplot   = o.type.equals("boxplot") || o.type.equals("hbox");
        boolean isViolin    = o.type.equals("violin")  || o.type.equals("hviolin");
        // v2.6.2: scatter/bubble use {x,y}/{x,y,r} point objects, not scalar arrays.
        // Must use scatterDatasets()/bubbleDatasets() -- overDatasets() produces
        // bar-style scalars which Chart.js scatter ignores -> blank chart on filter.
        boolean isScatter   = o.type.equals("scatter");
        boolean isBubble    = o.type.equals("bubble");

        for (String k1 : f1all) {
            for (String k2 : f2keys) {
                String key = k1 + "||" + k2;
                DataSet slice = sliceData(data, k1, k2);
                String labels;
                String datasets;
                if (isCiBar || isCiLine) {
                    // CI charts need their own dataset format (CI stats, not raw means)
                    // ciBar also needs ciBarLabels() not overLabels() -- skips n<2 and missing groups
                    labels   = isCiBar ? ciBarLabels(slice) : (slice.hasOver() ? overLabels(slice) : catLabels(slice));
                    datasets = isCiBar ? ciBarDatasets(slice) : ciLineDatasets(slice);
                } else if (isHistogram) {
                    // Histogram: rebin from scratch for each filter slice.
                    // Store labels, ranges, n, xMin, xMax so _applyFilter can
                    // swap labels, update axis bounds, tooltip ranges, and n count.
                    // v1.8.5: added labels swap and n field (Bug 1+3 fix)
                    String[] hd = histogramFilterData(slice);
                    labels   = hd[0];
                    datasets = hd[1];
                    sb.append("\"").append(escJs(key)).append("\":")
                      .append("{labels:[").append(labels).append("],")
                      .append("datasets:[").append(datasets).append("],")
                      .append("ranges:[").append(hd[2]).append("],");
                    // v2.6.1: store per-bin counts so tooltip shows obs-in-bin not total N
                    if (hd.length > 6) sb.append("counts:[").append(hd[6]).append("],");
                    sb.append("n:").append(hd[5]).append(",");
                    sb.append("xMin:").append(hd[3]).append(",");
                    sb.append("xMax:").append(hd[4]).append("},\n");
                    continue; // skip the generic append below
                } else if (isBoxplot) {
                    // v2.5.1: boxplot filter -- must use boxplotLabels()/boxplotDatasets()
                    // so each slice gets proper stat objects {min,q1,median,mean,q3,max,outliers}.
                    // Plain overDatasets() would produce mean numbers -> plugin renders nothing.
                    labels   = dsb.boxplotLabels(slice);
                    datasets = dsb.boxplotDatasets(slice, false);
                } else if (isViolin) {
                    // v2.5.1: violin filter -- store the raw array (not the var declaration)
                    // so _applyFilter can assign it: _violinData = d.violinData
                    labels   = dsb.violinLabels(slice);
                    datasets = dsb.violinDataArray(slice);
                    sb.append("\"").append(escJs(key)).append("\":")
                      .append("{labels:[").append(labels).append("],")
                      .append("violinData:").append(datasets).append("},\n");
                    continue; // skip generic append below
                } else if (isScatter || isBubble) {
                    // v2.6.2: scatter/bubble use {x,y}/{x,y,r} point objects.
                    // No labels[] -- scatter data:{datasets:[...]} has no label axis.
                    // _applyFilter will update datasets only (no labels swap).
                    datasets = isBubble ? bubbleDatasets(slice) : scatterDatasets(slice);
                    sb.append("\"").append(escJs(key)).append("\":{")
                      .append("datasets:[").append(datasets).append("]},\n");
                    continue; // skip generic append below
                } else {
                    // v1.8.9: use aggLabels() for no-over aggregated slices
                    labels   = slice.hasOver() ? overLabels(slice)
                             : (slice.getFirstStringVariable() != null) ? catLabels(slice)
                             : aggLabels(slice);
                    datasets = slice.hasOver()
                        ? overDatasets(slice, isLineType(), false)
                        : numDatasets(slice, isLineType(), false);
                }
                sb.append("\"").append(escJs(key)).append("\":")
                  .append("{labels:[").append(labels).append("],")
                  .append("datasets:[").append(datasets).append("]},\n");
            }
        }
        o._globalOverGroups = null; // reset after filter slices (v3.5.21)
        sb.append("};\n");
        return sb.toString();
    }

    /**
     * Returns true if the current chart type is a line/area variant.
     * Used by buildFilterData to pass correct isLine flag to dataset builders.
     */
    boolean isLineType() {
        String t = o.type;
        return t.equals("line") || t.equals("area") || t.equals("stackedarea")
            || t.equals("stackedline") || t.equals("ciline");
    }

    /**
     * Subset the full DataSet to rows where the by() variable equals groupKey.
     * Preserves filter1, filter2, and over variables on the resulting DataSet
     * so that sliceData() can further filter by dropdown value afterwards.
     * Used only when data.hasBy() && data.hasFilter1(). (v2.6.2)
     */
    private DataSet sliceByGroup(DataSet data, String groupKey) {
        Variable byVar = data.getByVariable();
        String gc = sdz(groupKey);
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < byVar.getValues().size(); i++) {
            Object v = byVar.getValues().get(i);
            String vs = v == null ? "" : sdz(String.valueOf(v));
            if (gc.equals(vs)) keep.add(i);
        }
        DataSet sub = new DataSet();
        for (Variable var : data.getVariables())
            sub.addVariable(subsetVar(var, keep));
        if (data.hasOver())     sub.setOverVariable(subsetVar(data.getOverVariable(), keep));
        if (data.hasFilter1())  sub.setFilter1Variable(subsetVar(data.getFilter1Variable(), keep));
        if (data.hasFilter2())  sub.setFilter2Variable(subsetVar(data.getFilter2Variable(), keep));
        // do NOT set byVariable on the subset -- each panel is already the by-group
        return sub;
    }

        /**
     * Subset the DataSet to rows matching the given filter key values.
     * "ALL" means include all rows for that filter dimension.
     */
    DataSet sliceData(DataSet data, String f1key, String f2key) {
        int n = data.getVariables().isEmpty() ? 0
              : data.getVariables().get(0).getValues().size();

        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            boolean ok = true;
            // Check filter1
            if (!f1key.equals("ALL") && data.hasFilter1()) {
                Object v = data.getFilter1Variable().getValues().get(i);
                // v1.9.1: MISSING_SENTINEL key matches null observations
                boolean match = (v == null && f1key.equals(DataSet.MISSING_SENTINEL))
                             || (v != null && sdz(String.valueOf(v)).equals(sdz(f1key)));
                if (!match) ok = false;
            }
            // Check filter2
            if (!f2key.equals("ALL") && data.hasFilter2()) {
                Object v = data.getFilter2Variable().getValues().get(i);
                boolean match = (v == null && f2key.equals(DataSet.MISSING_SENTINEL))
                             || (v != null && sdz(String.valueOf(v)).equals(sdz(f2key)));
                if (!match) ok = false;
            }
            if (ok) keep.add(i);
        }

        DataSet slice = new DataSet();
        for (Variable var : data.getVariables())
            slice.addVariable(subsetVar(var, keep));
        if (data.hasOver())
            slice.setOverVariable(subsetVar(data.getOverVariable(), keep));
        // Filter vars not needed inside the slice
        return slice;
    }

    /**
     * Pre-compute filter-slice data for each by()-panel independently.
     * Called instead of buildFilterData() when data.hasBy() && data.hasFilter1().
     *
     * Emits N separate JS objects: _dashData_0, _dashData_1, ... one per panel.
     * Each object is keyed by "f1key||f2key" exactly like the single-panel _dashData,
     * but sliced to that panel's by-group FIRST, then by filter combination.
     *
     * All inner-loop logic (histogram, cibar, violin, boxplot, default) is
     * identical to buildFilterData() -- we reuse it unchanged. (v2.6.2)
     */
    String buildFilterDataByPanels(DataSet data, List<String> groupKeys) {
        if (!data.hasFilter1()) return "";

        Variable f1 = data.getFilter1Variable();
        List<String> f1keys = DataSet.uniqueGroupKeys(f1, o.chart.sortgroups, o.showmissingFilter);

        List<String> f2keys = new ArrayList<>();
        f2keys.add("ALL");
        if (data.hasFilter2()) {
            for (String k : DataSet.uniqueGroupKeys(data.getFilter2Variable(), o.chart.sortgroups)) {
                if (k != null && !k.trim().isEmpty()) f2keys.add(k);
            }
        }

        List<String> f1all = new ArrayList<>();
        f1all.add("ALL");
        for (String k : f1keys) {
            if (k != null && (DataSet.MISSING_SENTINEL.equals(k) || !k.trim().isEmpty())) f1all.add(k);
        }

        boolean isCiBar     = o.type.equals("cibar");
        boolean isCiLine    = o.type.equals("ciline");
        boolean isHistogram = o.type.equals("histogram");
        boolean isBoxplot   = o.type.equals("boxplot") || o.type.equals("hbox");
        boolean isViolin    = o.type.equals("violin")  || o.type.equals("hviolin");
        boolean isScatter   = o.type.equals("scatter");  // v2.6.2
        boolean isBubble    = o.type.equals("bubble");   // v2.6.2

        StringBuilder sb = new StringBuilder();

        // v3.5.21: Set global over-groups from FULL data before panel+filter slicing.
        // Same fix as buildFilterData() -- ensures x-axis alignment and color
        // consistency across all filter slices within each panel.
        if (data.hasOver()) {
            o._globalOverGroups = DataSet.uniqueValues(
                data.getOverVariable(), o.chart.sortgroups, o.showmissingOver);
        }

        for (int pi = 0; pi < groupKeys.size(); pi++) {
            // Slice full dataset to this by-group (preserves filter vars)
            DataSet panelData = sliceByGroup(data, groupKeys.get(pi));
            sb.append("var _dashData_").append(pi).append("={\n");

            for (String k1 : f1all) {
                for (String k2 : f2keys) {
                    String key   = k1 + "||" + k2;
                    DataSet slice = sliceData(panelData, k1, k2);
                    String labels;
                    String datasets;

                    if (isCiBar || isCiLine) {
                        labels   = isCiBar ? ciBarLabels(slice) : (slice.hasOver() ? overLabels(slice) : catLabels(slice));
                        datasets = isCiBar ? ciBarDatasets(slice) : ciLineDatasets(slice);
                    } else if (isHistogram) {
                        String[] hd = histogramFilterData(slice);
                        labels   = hd[0];
                        datasets = hd[1];
                        sb.append("\"").append(escJs(key)).append("\":")
                          .append("{labels:[").append(labels).append("],")
                          .append("datasets:[").append(datasets).append("],")
                          .append("ranges:[").append(hd[2]).append("],");
                        if (hd.length > 6) sb.append("counts:[").append(hd[6]).append("],");
                        sb.append("n:").append(hd[5]).append(",");
                        sb.append("xMin:").append(hd[3]).append(",");
                        sb.append("xMax:").append(hd[4]).append("},\n");
                        continue;
                    } else if (isBoxplot) {
                        labels   = dsb.boxplotLabels(slice);
                        datasets = dsb.boxplotDatasets(slice, false);
                    } else if (isViolin) {
                        labels   = dsb.violinLabels(slice);
                        datasets = dsb.violinDataArray(slice);
                        sb.append("\"").append(escJs(key)).append("\":")
                          .append("{labels:[").append(labels).append("],")
                          .append("violinData:").append(datasets).append("},\n");
                        continue;
                    } else if (isScatter || isBubble) {
                        // v2.6.2: scatter/bubble use {x,y}/{x,y,r} point objects.
                        // No labels[] -- _applyFilter updates datasets only.
                        datasets = isBubble ? bubbleDatasets(slice) : scatterDatasets(slice);
                        sb.append("\"").append(escJs(key)).append("\":")
                          .append("{datasets:[").append(datasets).append("]},\n");
                        continue;
                    } else {
                        labels   = slice.hasOver() ? overLabels(slice)
                                 : (slice.getFirstStringVariable() != null) ? catLabels(slice)
                                 : aggLabels(slice);
                        datasets = slice.hasOver()
                            ? overDatasets(slice, isLineType(), false)
                            : numDatasets(slice, isLineType(), false);
                    }
                    sb.append("\"").append(escJs(key)).append("\":")
                      .append("{labels:[").append(labels).append("],")
                      .append("datasets:[").append(datasets).append("]},\n");
                }
            }
            sb.append("};\n");
        }
        return sb.toString();
    }

    /**
     * Build _applyFilter() for by()+filter() charts. (v2.6.2)
     * Updates each panel chart_by_N independently from its own _dashData_N.
     * Histogram panels also swap _ttRanges_chart_by_N and _ttCounts_chart_by_N.
     */
    String buildFilterScriptByPanels(DataSet data, List<String> groupKeys) {
        if (!data.hasFilter1()) return "";
        boolean has2        = data.hasFilter2();
        boolean isHistogram = o.type.equals("histogram");
        boolean isCiType    = o.type.equals("cibar") || o.type.equals("ciline");
        boolean isBoxplot   = o.type.equals("boxplot") || o.type.equals("hbox");
        boolean isViolin    = o.type.equals("violin")  || o.type.equals("hviolin");
        boolean isScatter   = o.type.equals("scatter"); // v2.6.3
        boolean isBubble    = o.type.equals("bubble");  // v2.6.3

        StringBuilder sb = new StringBuilder();
        sb.append("function _applyFilter(){\n")
          .append("  var k1=document.getElementById('_f1').value;\n");
        if (has2) {
            sb.append("  var k2=document.getElementById('_f2').value;\n")
              .append("  var key=k1+'||'+k2;\n");
        } else {
            sb.append("  var key=k1+'||ALL';\n");
        }

        for (int pi = 0; pi < groupKeys.size(); pi++) {
            String chartId  = "chart_by_" + pi;
            String safeId   = chartId.replace("-", "_");
            String dashVar  = "_dashData_" + pi;
            sb.append("  var d").append(pi).append("=").append(dashVar).append("[key];\n")
              .append("  if(!d").append(pi).append("){console.warn('No data for panel ")
              .append(pi).append(" key: '+key);}\n")
              .append("  else{\n");

            if (isHistogram) {
                // Histogram: swap labels, datasets, ranges, counts then update
                sb.append("    var c").append(pi).append("=Chart.getChart('").append(chartId).append("');\n")
                  .append("    if(c").append(pi).append("){\n")
                  .append("      c").append(pi).append(".data.labels=d").append(pi).append(".labels;\n")
                  .append("      c").append(pi).append(".data.datasets=d").append(pi).append(".datasets;\n")
                  .append("      if(d").append(pi).append(".ranges)_ttRanges_").append(safeId)
                  .append("=d").append(pi).append(".ranges;\n")
                  .append("      if(d").append(pi).append(".counts)_ttCounts_").append(safeId)
                  .append("=d").append(pi).append(".counts;\n")
                  .append("      c").append(pi).append(".update();\n")
                  .append("    }\n");
            } else if (isCiType || isBoxplot) {
                // These require destroy+reinit; each panel has its own _initChart_N()
                sb.append("    if(typeof _initChart_").append(pi).append("==='function')")
                  .append("_initChart_").append(pi).append("(d").append(pi).append(".labels,d")
                  .append(pi).append(".datasets);\n");
            } else if (isViolin) {
                // v3.5.28: _vAnimateTo_N is now on window (IIFE scope fix).
                sb.append("    var _vAt").append(pi).append("=window['_vAnimateTo_").append(pi).append("'];\n")
                  .append("    if(typeof _vAt").append(pi).append("==='function')")
                  .append("_vAt").append(pi).append("(d").append(pi).append(".violinData,d")
                  .append(pi).append(".labels);\n");
            } else if (isScatter || isBubble) {
                // v2.6.2: scatter/bubble -- no labels axis, datasets only
                sb.append("    var c").append(pi).append("=Chart.getChart('").append(chartId).append("');\n")
                  .append("    if(c").append(pi).append("){\n")
                  .append("      c").append(pi).append(".data.datasets=d").append(pi).append(".datasets;\n")
                  .append("      c").append(pi).append(".update();\n")
                  .append("    }\n");
            } else {
                // Standard charts: get by chart id and update
                sb.append("    var c").append(pi).append("=Chart.getChart('").append(chartId).append("');\n")
                  .append("    if(c").append(pi).append("){\n")
                  .append("      c").append(pi).append(".data.labels=d").append(pi).append(".labels;\n")
                  .append("      c").append(pi).append(".data.datasets=d").append(pi).append(".datasets;\n")
                  .append("      c").append(pi).append(".update();\n")
                  .append("    }\n");
            }
            sb.append("  }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

        /**
     * Build the JS applyFilter() function that reads the dropdowns,
     * looks up the pre-computed slice, and updates the chart.
     *
     * For standard charts: calls _mainChart.data update + update() which is
     * efficient and preserves animation state.
     *
     * For cibar and ciline: the chartjs-chart-error-bars plugin (cibar) and
     * Chart.js fill-band rendering (ciline) do not reliably redraw on a plain
     * data.update() call -- error whiskers and fill bands may freeze or vanish.
     * Fix: destroy the chart and recreate it from the stored config, replacing
     * only the data object. This is slightly slower but fully reliable.
     * We store _mainChartConfig at init time to support this.
     *
     * Returns empty string when no filters are specified.
     */
    String buildFilterScript(DataSet data) {
        if (!data.hasFilter1()) return "";
        boolean has2        = data.hasFilter2();
        boolean isCiType    = o.type.equals("cibar") || o.type.equals("ciline");
        boolean isHistogram = o.type.equals("histogram");
        // v2.5.1: boxplot requires destroy+reinit (same as cibar/ciline).
        boolean isBoxplot   = o.type.equals("boxplot") || o.type.equals("hbox");
        // v2.5.1: violin swaps _violinData global then calls update() on base chart.
        boolean isViolin    = o.type.equals("violin")  || o.type.equals("hviolin");
        // v2.6.2: scatter/bubble use {x,y} point objects -- no labels axis.
        boolean isScatter   = o.type.equals("scatter");
        boolean isBubble    = o.type.equals("bubble");
        StringBuilder sb = new StringBuilder();
        sb.append("function _applyFilter(){\n")
          .append("  var k1=document.getElementById('_f1').value;\n");
        if (has2) {
            sb.append("  var k2=document.getElementById('_f2').value;\n")
              .append("  var key=k1+'||'+k2;\n");
        } else {
            sb.append("  var key=k1+'||ALL';\n");
        }
        sb.append("  var d=_dashData[key];\n")
          .append("  if(!d){console.warn('No data for filter key: '+key);return;}\n");

        if (isCiType || isBoxplot) {
            // Use _initChart() -- destroy()+new Chart() required for plugin types.
            sb.append("  _initChart(d.labels,d.datasets);\n");
        } else if (isViolin) {
            // v2.5.1: call _vAnimateTo() which tweens from current shapes to
            // new ones using RAF loop + easeOutCubic. No direct data swap.
            // v3.5.28: _vAnimateTo is exposed on window (IIFE scope fix).
            sb.append("  if(window._vAnimateTo)window._vAnimateTo(d.violinData,d.labels);\n");
        } else if (isHistogram) {
            // v1.8.5 Fix: swap labels (category axis requires label update on filter change).
            // Also swap ranges (tooltip bin boundaries) and n (obs count for tooltip).
            // xMin/xMax stored in _dashData for future linear axis use but not applied
            // here since category axis ignores them.
            sb.append("  _mainChart.data.labels=d.labels;\n")
              .append("  _mainChart.data.datasets=d.datasets;\n")
              .append("  if(d.ranges)_ttRanges_mainChart=d.ranges;\n")
              // v2.6.1: also swap per-bin counts so tooltip shows correct obs-in-bin
              .append("  if(d.counts)_ttCounts_mainChart=d.counts;\n")
              .append("  _mainChart.update();\n");
                        } else if (isScatter || isBubble) {
            // v2.6.2: scatter/bubble have no label axis.
            // Only swap datasets -- no labels update needed.
            sb.append("  _mainChart.data.datasets=d.datasets;\n")
              .append("  _mainChart.update();\n");
        } else {
            sb.append("  _mainChart.data.labels=d.labels;\n")
              .append("  _mainChart.data.datasets=d.datasets;\n")
              .append("  _mainChart.update();\n");
        }
        sb.append("}\n");
        o._globalOverGroups = null; // reset after by()+filter slices (v3.5.21)
        return sb.toString();
    }


}
