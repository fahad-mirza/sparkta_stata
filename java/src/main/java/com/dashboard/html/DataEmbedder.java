package com.dashboard.html;

import com.dashboard.data.DataSet;
import com.dashboard.data.Variable;

import java.util.*;

/**
 * DataEmbedder  (F-0, sparkta)  v1.2 -- Float32Array base64 encoding for _sd
 *
 * Performance upgrade: numeric _sd arrays are now emitted as base64-encoded
 * Float32Array buffers instead of plain JSON arrays. This reduces payload size
 * by ~60% for numeric columns (4 bytes/value vs ~7 bytes average in JSON) and
 * cuts browser parse time significantly at N>10k rows.
 *
 * Encoding:
 *   Each numeric column is packed into a little-endian Float32Array (4 bytes
 *   per value). Missing values (null) are encoded as Float.NaN (0x7FC00000).
 *   The raw bytes are base64-encoded and emitted as a string literal.
 *   The engine decodes on first access via _sdDecode() in sparkta_engine.js.
 *
 *   Emitted form:  _sdb['varname'] = 'base64string';
 *   At runtime:    _sd['varname']  = _sdDecode(_sdb['varname']);
 *                  decoded as Float32Array, NaN positions treated as null.
 *
 * Fallback: integer-valued whole columns retain compact integer JSON for
 *   human readability in small datasets (N < PLAIN_JSON_THRESHOLD = 200).
 *
 * Embeds row-level data into the HTML as compact JavaScript arrays so the
 * browser-side sparkta_engine.js can aggregate on demand for any filter
 * combination -- no combinatorial pre-computation required.
 *
 * Emitted JS globals
 * ------------------
 *   _sd        : object keyed by variable name -> flat numeric array (all rows)
 *   _si        : object keyed by filter variable name -> categorical index array
 *   _sc        : object keyed by filter variable name -> category label array
 *   _smeta     : metadata: nObs, over var name, by var name, filter var names,
 *                plot var names, stat type
 *
 * Encoding
 * --------
 *   Numeric plot/over/by values are stored as JSON arrays.
 *   Categorical filter values are index-encoded:
 *     - category label list stored in _sc[varname]
 *     - per-row index stored in _si[varname] as a JSON array
 *   Missing numeric value is encoded as null in the JSON array.
 *   Missing categorical value: -1 in _si (engine skips g2 < 0).
 *     When showMissing=true, (Missing) appears in _sc at index 0 and
 *     _si uses 0 for those rows -- matches FilterRenderer dropdown.
 *
 * v1.1 FIX (category index alignment):
 *   buildCategoryList() now accepts a showMissing flag, matching
 *   FilterRenderer.buildCategoryList(). Previously DataEmbedder always
 *   inserted (Missing) at index 0 when any nulls existed, but
 *   FilterRenderer only inserted it when showMissing=true. This caused
 *   the dropdown option indices to be off-by-one from _si indices for
 *   any variable with missing values when showMissing was false.
 *   Missing rows now get _si value -1 (not 0) when (Missing) is excluded.
 *
 * Design notes
 * ------------
 *   We keep plain JSON (not base64 typed arrays) for the initial F-0
 *   implementation -- simpler, universally readable in all browsers,
 *   and adequate for datasets up to ~100k rows.  The base64/Float32Array
 *   upgrade path is documented but deferred to F-0b.
 *
 *   The engine reads _sd for numeric columns and _si/_sc for categoricals.
 *   sparkta_engine.js does all aggregation client-side.
 */
public class DataEmbedder {

    private final DataSet data;
    private final DashboardOptions o;
    private final HtmlGenerator gen;

    public DataEmbedder(DataSet data, DashboardOptions o, HtmlGenerator gen) {
        this.data = data;
        this.o    = o;
        this.gen  = gen;
    }

    /**
     * Returns a JS block (no surrounding script tag) that defines
     * _sd, _si, _sc, and _smeta for the sparkta_engine.js filter system.
     */
    public String buildDataBlock() {
        int nObs = data.getObservationCount();
        StringBuilder sb = new StringBuilder();
        sb.append("// sparkta_engine data (F-0)\n");

        // -- _sd / _sdb: numeric values for each variable ---------------------
        // Small datasets (N < PLAIN_JSON_THRESHOLD): plain JSON into _sd directly.
        // Large datasets: base64 Float32Array into _sdb; _sd decoded lazily by engine.
        // _sdDecode() in sparkta_engine.js handles the base64 -> Float32Array path.
        boolean useb64 = nObs >= PLAIN_JSON_THRESHOLD;
        sb.append("var _sd = {};\n");
        if (useb64) sb.append("var _sdb = {};\n");

        // Helper lambda to emit one variable into _sd or _sdb
        // (Java 8+: effectively-final captures only)
        java.util.function.BiConsumer<String,Variable> emitVar = (name, v) -> {
            String encoded = numericArray(v, nObs);
            if (useb64 && !encoded.startsWith("[")) {
                // base64 string -- goes into _sdb for deferred decode
                sb.append("_sdb[").append(jsStr(name)).append("] = '")
                  .append(encoded).append("';\n");
            } else {
                sb.append("_sd[").append(jsStr(name)).append("] = ")
                  .append(encoded).append(";\n");
            }
        };

        for (Variable v : data.getVariables()) {
            if (v.isNumeric()) emitVar.accept(v.getName(), v);
        }
        if (data.hasOver() && data.getOverVariable().isNumeric()) {
            emitVar.accept(data.getOverVariable().getName(), data.getOverVariable());
        }
        if (data.hasBy() && data.getByVariable().isNumeric()) {
            emitVar.accept(data.getByVariable().getName(), data.getByVariable());
        }
        // F-1: embed slider variables
        for (Variable sv : data.getSliderVariables()) {
            String sname = sv.getName();
            String encoded = numericArray(sv, nObs);
            if (useb64 && !encoded.startsWith("[")) {
                sb.append("if (!_sdb[").append(jsStr(sname)).append("]) { ");
                sb.append("_sdb[").append(jsStr(sname)).append("] = '")
                  .append(encoded).append("'; }\n");
            } else {
                sb.append("if (!_sd[").append(jsStr(sname)).append("]) { ");
                sb.append("_sd[").append(jsStr(sname)).append("] = ")
                  .append(encoded).append("; }\n");
            }
        }

        // -- _si, _sc: categorical index arrays for filter variables ----------
        sb.append("var _si = {};\n");
        sb.append("var _sc = {};\n");
        List<Variable> fvars = data.getFilterVariables();
        for (int fi = 0; fi < fvars.size(); fi++) {
            Variable fv = fvars.get(fi);
            String fname = fv.getName();
            // v1.1: use showMissing flag matching FilterRenderer so indices align
            boolean showMissing = getShowMissing(fi);
            List<String> cats = buildCategoryList(fv, showMissing);
            Map<String, Integer> catIdx = new LinkedHashMap<>();
            for (int i = 0; i < cats.size(); i++) catIdx.put(cats.get(i), i);

            // _sc[name] = ["cat0", "cat1", ...]
            sb.append("_sc[").append(jsStr(fname)).append("] = [");
            for (int i = 0; i < cats.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(jsStr(cats.get(i)));
            }
            sb.append("];\n");

            // _si[name] = [0,1,2,0,...] per row
            // v1.1: missing rows get -1 when (Missing) not in category list
            sb.append("_si[").append(jsStr(fname)).append("] = [");
            List<Object> vals = fv.getValues();
            int n = Math.min(vals.size(), nObs);
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(",");
                Object v = vals.get(i);
                String key = categoryKey(v, fv);
                Integer idx = catIdx.get(key);
                sb.append(idx != null ? idx : -1);
            }
            sb.append("];\n");
        }

        // Always embed over() and by() into _si/_sc so the engine can group rows.
        // Numeric over/by vars store their values in _sd above (for other uses),
        // but the engine's aggregate() function reads _si[groupVar] for row->group
        // mapping.  Without _si the groupOf array is undefined and all buckets
        // are empty -> blank chart after any filter change.  (v3.5.47-F0e fix)
        if (data.hasOver()) {
            embedCatVar(data.getOverVariable(), nObs, sb);
        }
        if (data.hasBy()) {
            embedCatVar(data.getByVariable(), nObs, sb);
        }

        // -- _smeta: metadata for engine aggregation -------------------------
        sb.append("var _smeta = {\n");
        sb.append("  nObs: ").append(nObs).append(",\n");
        // plot variable names
        sb.append("  plotVars: [");
        boolean first = true;
        for (Variable v : data.getVariables()) {
            if (v.isNumeric()) {
                if (!first) sb.append(","); first = false;
                sb.append(jsStr(v.getName()));
            }
        }
        sb.append("],\n");
        // over variable name
        sb.append("  overVar: ");
        sb.append(data.hasOver() ? jsStr(data.getOverVariable().getName()) : "null");
        sb.append(",\n");
        // by variable name
        sb.append("  byVar: ");
        sb.append(data.hasBy() ? jsStr(data.getByVariable().getName()) : "null");
        sb.append(",\n");
        // filter variable names in order
        sb.append("  filterVars: [");
        first = true;
        for (Variable fv : data.getFilterVariables()) {
            if (!first) sb.append(","); first = false;
            sb.append(jsStr(fv.getName()));
        }
        sb.append("],\n");
        // stat type (mean/sum/count etc.)
        sb.append("  stat: ").append(jsStr(o.stats.stat)).append(",\n");
        // over category display labels (for x-axis labels in filtered redraws).
        // Must match DatasetBuilder's label order exactly: non-missing values first
        // (sorted), then (Missing) at the end when showmissingOver=true.
        sb.append("  overLabels: ");
        if (data.hasOver()) {
            Variable ov = data.getOverVariable();
            List<String> ovLabels = DataSet.uniqueValues(ov, o.chart.sortgroups, o.showmissingOver);
            ovLabels.remove(""); // remove empty-string sentinel if present
            sb.append("[");
            for (int i = 0; i < ovLabels.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(jsStr(ovLabels.get(i)));
            }
            sb.append("]");
        } else {
            sb.append("null");
        }
        // F-1: slider variable metadata {name, label, min, max, step}
        sb.append(",\n  sliders: [");
        List<Variable> svars = data.getSliderVariables();
        for (int si = 0; si < svars.size(); si++) {
            Variable sv = svars.get(si);
            if (si > 0) sb.append(",");
            double[] minMax = numericMinMax(sv, nObs);
            double lo   = minMax[0];
            double hi   = minMax[1];
            double step = computeStep(lo, hi);
            sb.append("{");
            sb.append("name:").append(jsStr(sv.getName())).append(",");
            sb.append("label:").append(jsStr(sv.getDisplayName())).append(",");
            sb.append("min:").append(formatNum(lo)).append(",");
            sb.append("max:").append(formatNum(hi)).append(",");
            sb.append("step:").append(formatNum(step));
            sb.append("}");
        }
        sb.append("]"); // close sliders array
        // v3.5.65: embed mlabel strings for scatter filter engine rebuild.
        // When mlabel(varname) is set, embed the per-row label strings into
        // _smeta.labels so sparkta_engine can reattach them to {x,y} points
        // after filter changes without losing marker labels.
        sb.append(",\n  labels: ");
        if (!o.chart.mlabelVar.isEmpty()) {
            // Find the label variable in the DataSet
            Variable lv = null;
            for (Variable v : data.getAllVariables()) {
                if (v.getName().equals(o.chart.mlabelVar)) { lv = v; break; }
            }
            if (lv != null) {
                sb.append("[");
                List<Object> lvals = lv.getValues();
                int lsize = Math.min(lvals.size(), nObs);
                for (int i = 0; i < lsize; i++) {
                    if (i > 0) sb.append(",");
                    Object lval = lvals.get(i);
                    sb.append(lval != null ? jsStr(String.valueOf(lval)) : "null");
                }
                sb.append("]");
            } else {
                sb.append("null");
            }
        } else {
            sb.append("null");
        }
        // F-3: by-group display labels in panel order (chart_by_0 = byGroups[0]).
        // Used by buildFilterScriptByPanels() to map panel index to group rows.
        sb.append(",\n  byGroups: ");
        if (data.hasBy()) {
            Variable bv = data.getByVariable();
            List<String> byLbls = DataSet.uniqueValues(bv, o.chart.sortgroups, o.showmissingBy);
            sb.append("[");
            for (int i = 0; i < byLbls.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(jsStr(byLbls.get(i)));
            }
            sb.append("]");
        } else {
            sb.append("null");
        }
        // F-3/F-2: chart type so JS filter knows scatter vs bar/line path.
        sb.append(",\n  chartType: ").append(jsStr(o.type));
        sb.append("\n};\n");

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    // Threshold below which plain JSON is used (small datasets -- human readable)
    private static final int PLAIN_JSON_THRESHOLD = 200;

    /**
     * Encodes a numeric Variable as either:
     *   - Plain JSON array (N < PLAIN_JSON_THRESHOLD): "[1,2,null,4,...]"
     *   - Base64 Float32Array (N >= threshold): base64 string for _sdb
     *
     * Missing values: null in JSON, Float.NaN in Float32Array.
     * Whole-number values in JSON are emitted as integers (saves ~30% space).
     */
    private String numericArray(Variable v, int nObs) {
        List<Object> vals = v.getValues();
        int n = Math.min(vals.size(), nObs);

        if (n < PLAIN_JSON_THRESHOLD) {
            // Small dataset -- plain JSON for readability
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(",");
                Object val = vals.get(i);
                if (val == null) {
                    sb.append("null");
                } else {
                    double d = ((Number) val).doubleValue();
                    if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
                        sb.append((long) d);
                    else
                        sb.append(d);
                }
            }
            sb.append("]");
            return sb.toString();
        }

        // Large dataset -- Float32Array packed as base64
        return toBase64Float32(vals, n);
    }

    /**
     * Packs values into a little-endian Float32Array and returns base64 string.
     * Missing (null) values are encoded as Float.NaN (IEEE 754: 0x7FC00000).
     * Uses Java's built-in Base64 encoder (available since Java 8).
     */
    private static String toBase64Float32(List<Object> vals, int n) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(n * 4);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) {
            Object val = vals.get(i);
            if (val == null) {
                buf.putFloat(Float.NaN);
            } else {
                buf.putFloat((float) ((Number) val).doubleValue());
            }
        }
        return java.util.Base64.getEncoder().encodeToString(buf.array());
    }

    /** Embeds a grouping variable (over/by) as categorical into _sc/_si.
     *  Uses DataSet.uniqueValues(showMissing=true) so the category order
     *  matches the chart's x-axis label order exactly -- (Missing) at the END,
     *  not at index 0.  This is critical: _si indices must align with the
     *  label positions the chart renders, otherwise the engine routes rows to
     *  the wrong group after any filter change. */
    private void embedCatVar(Variable fv, int nObs, StringBuilder sb) {
        String fname = fv.getName();
        // Use uniqueValues with showMissing=true -- appends (Missing) at end,
        // matching DatasetBuilder's label order for the initial chart render.
        List<String> cats = DataSet.uniqueValues(fv, o.chart.sortgroups, true);
        // Remove empty string sentinel -- replace with (Missing) already appended above
        cats.remove("");
        Map<String, Integer> catIdx = new LinkedHashMap<>();
        for (int i = 0; i < cats.size(); i++) catIdx.put(cats.get(i), i);

        sb.append("_sc[").append(jsStr(fname)).append("] = [");
        for (int i = 0; i < cats.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(jsStr(cats.get(i)));
        }
        sb.append("];\n");

        sb.append("_si[").append(jsStr(fname)).append("] = [");
        List<Object> vals = fv.getValues();
        int n = Math.min(vals.size(), nObs);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(",");
            Object v = vals.get(i);
            String key = categoryKey(v, fv);
            Integer idx = catIdx.get(key);
            sb.append(idx != null ? idx : -1);
        }
        sb.append("];\n");
    }

    /**
     * Builds the ordered category label list for a variable.
     * v1.1: showMissing parameter added for consistency with FilterRenderer.
     * Missing values appear as "(Missing)" at index 0 ONLY when showMissing=true
     * AND at least one null value exists. When showMissing=false, missing rows
     * will get _si index -1 (engine skips g2 < 0 in aggregate).
     */
    private List<String> buildCategoryList(Variable fv, boolean showMissing) {
        boolean hasMissing = false;
        for (Object v : fv.getValues()) if (v == null) { hasMissing = true; break; }

        List<String> cats = new ArrayList<>();
        if (hasMissing && showMissing) cats.add(DataSet.MISSING_SENTINEL); // index 0

        // Unique non-missing display labels in sorted order
        List<String> nonMissing = DataSet.uniqueValues(fv, o.chart.sortgroups);
        nonMissing.remove("");
        nonMissing.remove(DataSet.MISSING_SENTINEL);
        cats.addAll(nonMissing);
        return cats;
    }

    /**
     * Returns whether a filter variable at the given index should show missing values.
     * Mirrors FilterRenderer.getShowMissing() logic exactly.
     * Filter index 0 -> o.showmissingFilter, index 1 -> o.showmissingFilter2,
     * index 2+ -> false (no showmissing support beyond first two filters).
     */
    private boolean getShowMissing(int filterIndex) {
        if (filterIndex == 0) return o.showmissingFilter;
        if (filterIndex == 1) return o.showmissingFilter2;
        return false;
    }

    /**
     * Returns the display label key for a raw value, for use in catIdx lookup.
     * Null -> MISSING_SENTINEL.  Numeric codes mapped through value labels.
     */
    private String categoryKey(Object v, Variable fv) {
        if (v == null) return DataSet.MISSING_SENTINEL;
        if (fv.hasValueLabels() && v instanceof Number) {
            String lbl = fv.getValueLabel(((Number) v).doubleValue());
            if (lbl != null) return lbl;
        }
        String raw = String.valueOf(v);
        if (raw.endsWith(".0")) raw = raw.substring(0, raw.length() - 2);
        if (raw.isEmpty()) return DataSet.MISSING_SENTINEL;
        return raw;
    }

    /** Emits a JS string literal with basic escaping. ASCII-only safe. */
    static String jsStr(String s) {
        if (s == null) return "null";
        return "'" + s.replace("\\", "\\\\")
                      .replace("'", "\\'")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r") + "'";
    }

    /**
     * F-1: Returns [min, max] of non-missing numeric values in a variable.
     * Returns [0, 1] as a safe fallback if the variable has no non-missing values.
     */
    private double[] numericMinMax(Variable v, int nObs) {
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        List<Object> vals = v.getValues();
        int n = Math.min(vals.size(), nObs);
        for (int i = 0; i < n; i++) {
            Object o = vals.get(i);
            if (o == null) continue;
            double d = ((Number) o).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) continue;
            if (d < lo) lo = d;
            if (d > hi) hi = d;
        }
        if (lo > hi) { lo = 0; hi = 1; } // all missing fallback
        return new double[]{lo, hi};
    }

    /**
     * F-1: Computes a "nice" step size for a slider given its data range.
     * Rules (mirrors Stata's axis tick logic):
     *   range <= 1        -> 0.01
     *   range <= 10       -> 0.1
     *   range <= 100      -> 1
     *   range <= 1000     -> 5
     *   range <= 10000    -> 10
     *   otherwise         -> round range/200 up to nearest power-of-10 multiple
     */
    private double computeStep(double lo, double hi) {
        double range = hi - lo;
        if (range <= 0)      return 1;
        if (range <= 1)      return 0.01;
        if (range <= 10)     return 0.1;
        if (range <= 100)    return 1;
        if (range <= 1000)   return 5;
        if (range <= 10000)  return 10;
        // General case: ~200 steps across the range, rounded to a nice number
        double raw   = range / 200.0;
        double mag   = Math.pow(10, Math.floor(Math.log10(raw)));
        double ratio = raw / mag;
        double nice  = ratio < 2 ? mag : ratio < 5 ? 2 * mag : 5 * mag;
        return nice;
    }

    /**
     * F-1: Formats a double for JS emission -- integer when whole, else up to 4 dp.
     */
    private String formatNum(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        // Strip trailing zeros after decimal point
        String s = String.format("%.4f", d);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }
}
