package com.dashboard.data;

import com.stata.sfi.Data;
import com.stata.sfi.SFIToolkit;
import com.stata.sfi.ValueLabel;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Reads variables and observations from Stata's in-memory dataset.
 *
 * v1.6.0: Replaced obslist string approach (limited to ~13K obs by Stata macro
 * size) with touse variable approach. Java now receives the NAME of a Stata
 * tempvar (e.g. "__000001") that is 1 for in-sample observations and 0/missing
 * otherwise. Java iterates 1..Data.getObsTotal() and includes obs where
 * touse==1, using the SFI Data API directly.
 *
 * v3.5.7: Bulk column read via Data.getNumVarVector() (Stata 17+).
 * Previously, every observation required one JNI call to Data.getNum().
 * At 500k obs x 4 variables = 2M JNI crossings, each costing ~300ns of
 * JNI overhead, the data-read phase took 0.5-2 seconds before any chart
 * computation started.
 *
 * Data.getNumVarVector(varIdx, buffer, obs1, obs2) copies an entire column
 * into a pre-allocated double[] in a SINGLE native call. Iterating the buffer
 * in Java costs ~1ns per element (pure Java) vs ~300ns per JNI call.
 * Result: data-read time drops from ~1s to ~50ms for 500k observations.
 *
 * The touse scan is also batched: one getNumVarVector() call reads the entire
 * touse column, then we filter in a pure Java loop.
 *
 * Fallback: if getNumVarVector() returns a non-zero error code (e.g. on a
 * Stata version that pre-dates the method), readVariable() falls back to the
 * original per-obs Data.getNum() loop automatically. String variables always
 * use per-obs Data.getStr() (no bulk read available for strings in SFI).
 *
 * if/in is handled by Stata's mark/markout on the .ado side, which sets the
 * touse variable before Java is called. Java only reads, never modifies data.
 *
 * over() splits a numeric variable into one series per category value.
 * by()   reads all variables separately per category -- used by HtmlGenerator
 *        to produce one chart panel per category.
 */
public class StataDataReader {

    /**
     * @param varlist      Space-separated variable names to plot
     * @param over         Grouping variable name (empty string if none)
     * @param by           Panel variable name (empty string if none)
     * @param touseName    Name of the Stata touse tempvar (1=in sample, 0=excluded)
     */
    public DataSet read(String varlist, String over, String by,
                        String touseName) throws Exception {
        return read(varlist, over, by, touseName, false);
    }

    public DataSet read(String varlist, String over, String by,
                        String touseName, boolean nomissing) throws Exception {
        return read(varlist, over, by, touseName, nomissing, false);
    }

    public DataSet read(String varlist, String over, String by,
                        String touseName, boolean nomissing,
                        boolean novaluelabels) throws Exception {
        return read(varlist, over, by, touseName, nomissing, novaluelabels, "", "");
    }

    public DataSet read(String varlist, String over, String by,
                        String touseName, boolean nomissing,
                        boolean novaluelabels,
                        String filter1, String filter2) throws Exception {

        // -- Resolve touse variable -------------------------------------------
        int touseIdx = Data.getVarIndex(touseName);
        if (touseIdx < 0) {
            throw new Exception("touse variable not found: '" + touseName
                + "'. This is an internal error -- please report it.");
        }

        // v3.5.7: Build obs index with a single bulk read of the touse column.
        // One getNumVarVector() call replaces N individual Data.getNum() calls.
        long totalObs = Data.getObsTotal();
        long[] obs = buildObsArray(touseIdx, totalObs);

        if (obs.length == 0) {
            throw new Exception("Observation list is empty after applying touse filter");
        }

        String[] varNames = varlist.trim().split("\\s+");

        // -- Read all raw variables -------------------------------------------
        Variable rawOver    = over.isEmpty()    ? null : readVariable(over,    obs, totalObs, novaluelabels);
        Variable rawBy      = by.isEmpty()      ? null : readVariable(by,      obs, totalObs, novaluelabels);
        Variable rawFilter1 = filter1.isEmpty() ? null : readVariable(filter1, obs, totalObs, novaluelabels);
        Variable rawFilter2 = filter2.isEmpty() ? null : readVariable(filter2, obs, totalObs, novaluelabels);

        if (!over.isEmpty()    && rawOver    == null) throw new Exception("over() variable not found: "    + over);
        if (!by.isEmpty()      && rawBy      == null) throw new Exception("by() variable not found: "      + by);
        if (!filter1.isEmpty() && rawFilter1 == null) throw new Exception("filter() variable not found: "  + filter1);
        if (!filter2.isEmpty() && rawFilter2 == null) throw new Exception("filter2() variable not found: " + filter2);

        List<Variable> rawVars = new ArrayList<>();
        for (String varName : varNames) {
            Variable var = readVariable(varName, obs, totalObs, novaluelabels);
            if (var == null) { SFIToolkit.errorln("Variable not found, skipping: " + varName); continue; }
            rawVars.add(var);
        }

        // -- Build keep-mask: which rows survive nomissing filter -------------
        int nRows = obs.length;
        boolean[] keep = new boolean[nRows];
        Arrays.fill(keep, true);

        if (nomissing) {
            // Drop row if over/by/filter value is null or empty string
            for (Variable raw : new Variable[]{rawOver, rawBy, rawFilter1, rawFilter2}) {
                if (raw == null) continue;
                for (int i = 0; i < nRows; i++) {
                    Object v = raw.getValues().get(i);
                    if (v == null || String.valueOf(v).trim().isEmpty()) keep[i] = false;
                }
            }
            // Drop row if any plot variable value is null (numeric missing)
            for (Variable var : rawVars) {
                for (int i = 0; i < nRows; i++) {
                    if (var.getValues().get(i) == null) keep[i] = false;
                }
            }
        }

        // -- Build filtered DataSet -------------------------------------------
        DataSet dataset = new DataSet();
        if (rawOver    != null) dataset.setOverVariable(filterVar(rawOver,    keep));
        if (rawBy      != null) dataset.setByVariable(filterVar(rawBy,        keep));
        if (rawFilter1 != null) dataset.setFilter1Variable(filterVar(rawFilter1, keep));
        if (rawFilter2 != null) dataset.setFilter2Variable(filterVar(rawFilter2, keep));
        for (Variable var : rawVars) dataset.addVariable(filterVar(var, keep));

        return dataset;
    }

    // =========================================================================
    // v3.5.7: Bulk column read via reflection.
    //
    // Stata's SFI Data class has bulk read methods but the exact method name
    // and signature vary across Stata versions and are not stable in the public
    // SFI API. Hard-coding the name causes compile errors on mismatched versions
    // (as seen in v3.5.7 first attempt: getNumVarVector compiled in CI but
    // failed on the user's Stata 19 sfi-api.jar with a different signature).
    //
    // Solution: probe via reflection at runtime (one-time, ~microseconds).
    // We try candidate method names/signatures in priority order and cache
    // the first one that exists. All subsequent calls use the cached result.
    //
    // Candidates probed in priority order:
    //   1. getNumVarVector(int, double[], long, long)  -- long obs range variant
    //   2. getNumVarVector(int, double[], int,  int)   -- int obs range variant
    //   3. getNumericColumnDouble(int)                 -- returns full double[]
    //
    // If none found: _bulkStrategy = PER_OBS, fall back to Data.getNum() loop.
    // This is guaranteed to work on all Stata 17+ versions.
    // =========================================================================

    private enum BulkStrategy { VECTOR_LONG, VECTOR_INT, FULL_COLUMN, PER_OBS }

    // Static cache: probed once per JVM session, shared across all invocations
    private static volatile BulkStrategy _bulkStrategy = null;
    private static volatile Method        _bulkMethod   = null;

    /** One-time probe: find best available bulk-read method. Thread-safe. */
    private static synchronized void probeBulkMethod() {
        if (_bulkStrategy != null) return;
        Class<?> dc = Data.class;

        // Candidate 1: getNumVarVector(int, double[], long, long)
        try {
            Method m = dc.getMethod("getNumVarVector",
                int.class, double[].class, long.class, long.class);
            _bulkMethod = m; _bulkStrategy = BulkStrategy.VECTOR_LONG; return;
        } catch (NoSuchMethodException e) { /* try next */ }

        // Candidate 2: getNumVarVector(int, double[], int, int)
        try {
            Method m = dc.getMethod("getNumVarVector",
                int.class, double[].class, int.class, int.class);
            _bulkMethod = m; _bulkStrategy = BulkStrategy.VECTOR_INT; return;
        } catch (NoSuchMethodException e) { /* try next */ }

        // Candidate 3: getNumericColumnDouble(int) -- returns full double[]
        try {
            Method m = dc.getMethod("getNumericColumnDouble", int.class);
            _bulkMethod = m; _bulkStrategy = BulkStrategy.FULL_COLUMN; return;
        } catch (NoSuchMethodException e) { /* try next */ }

        // No bulk method found -- use per-obs fallback
        _bulkMethod = null; _bulkStrategy = BulkStrategy.PER_OBS;
    }

    /**
     * Read an entire numeric column into a double[] using the best available
     * bulk method. Returns null if unavailable or on any error, signalling
     * the caller to fall back to per-obs Data.getNum().
     */
    private static double[] bulkReadColumn(int varIdx, int n) {
        probeBulkMethod();
        if (_bulkStrategy == BulkStrategy.PER_OBS) return null;
        try {
            if (_bulkStrategy == BulkStrategy.VECTOR_LONG) {
                double[] buf = new double[n];
                int rc = (Integer) _bulkMethod.invoke(null, varIdx, buf, 1L, (long) n);
                return rc == 0 ? buf : null;
            }
            if (_bulkStrategy == BulkStrategy.VECTOR_INT) {
                double[] buf = new double[n];
                int rc = (Integer) _bulkMethod.invoke(null, varIdx, buf, 1, n);
                return rc == 0 ? buf : null;
            }
            if (_bulkStrategy == BulkStrategy.FULL_COLUMN) {
                // Returns the full column array; length may exceed n, we index by obs-1
                return (double[]) _bulkMethod.invoke(null, varIdx);
            }
        } catch (Exception e) {
            // Any runtime failure: disable bulk reads for rest of session
            _bulkStrategy = BulkStrategy.PER_OBS;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Build the in-sample obs array from the touse column.
    // -------------------------------------------------------------------------
    private long[] buildObsArray(int touseIdx, long totalObs) {
        int n = (int) Math.min(totalObs, (long) Integer.MAX_VALUE);
        double[] buf = bulkReadColumn(touseIdx, n);

        if (buf != null) {
            int limit = Math.min(n, buf.length);
            int inSample = 0;
            for (int i = 0; i < limit; i++)
                if (!Data.isValueMissing(buf[i]) && buf[i] == 1.0) inSample++;
            long[] obs = new long[inSample];
            int j = 0;
            for (int i = 0; i < limit; i++)
                if (!Data.isValueMissing(buf[i]) && buf[i] == 1.0) obs[j++] = (long)(i + 1);
            return obs;
        }

        // Fallback: per-obs loop
        List<Long> obsList = new ArrayList<>();
        for (long ob = 1; ob <= totalObs; ob++) {
            double tv = Data.getNum(touseIdx, ob);
            if (!Data.isValueMissing(tv) && tv == 1.0) obsList.add(ob);
        }
        long[] obs = new long[obsList.size()];
        for (int i = 0; i < obs.length; i++) obs[i] = obsList.get(i);
        return obs;
    }

    // -------------------------------------------------------------------------
    // Return a copy of var containing only rows where keep[i] == true
    // -------------------------------------------------------------------------
    private Variable filterVar(Variable src, boolean[] keep) {
        Variable dst = new Variable();
        dst.setName(src.getName());
        dst.setLabel(src.getLabel());
        dst.setNumeric(src.isNumeric());
        for (Map.Entry<Double, String> e : src.getValueLabels().entrySet()) {
            dst.putValueLabel(e.getKey(), e.getValue());
        }
        for (int i = 0; i < keep.length && i < src.getValues().size(); i++) {
            if (keep[i]) dst.addValue(src.getValues().get(i));
        }
        return dst;
    }

    // -------------------------------------------------------------------------
    // Read a single variable for the given observation array.
    // Numeric: one bulkReadColumn() call, then extract in-sample values in Java.
    //          Falls back to per-obs Data.getNum() if bulk unavailable.
    // String:  always per-obs Data.getStr() (no SFI bulk method for strings).
    // -------------------------------------------------------------------------
    private Variable readVariable(String varName, long[] obs, long totalObs,
                                   boolean novaluelabels) {
        int varIdx = Data.getVarIndex(varName);
        if (varIdx < 0) return null;

        Variable var = new Variable();
        var.setName(varName);
        var.setLabel(Data.getVarLabel(varIdx));

        int varType = Data.getType(varIdx);
        boolean isNumeric = (varType != Data.TYPE_STR && varType != Data.TYPE_STRL);
        var.setNumeric(isNumeric);

        if (isNumeric) {
            int n = (int) Math.min(totalObs, (long) Integer.MAX_VALUE);
            double[] buf = bulkReadColumn(varIdx, n);
            if (buf != null) {
                for (long ob : obs) {
                    int idx = (int)(ob - 1);
                    double val = idx < buf.length ? buf[idx] : Double.MAX_VALUE;
                    var.addValue(Data.isValueMissing(val) ? null : val);
                }
            } else {
                for (long ob : obs) {
                    double val = Data.getNum(varIdx, ob);
                    var.addValue(Data.isValueMissing(val) ? null : val);
                }
            }
        } else {
            for (long ob : obs) {
                String val = Data.getStr(varIdx, ob);
                // v3.5.8: store null for empty/missing string values so the
                // MISSING_SENTINEL path in groupIndex() and groupIdx() fires
                // consistently for both numeric and string grouping variables.
                // Empty string is Stata's convention for a missing string value.
                var.addValue((val == null || val.isEmpty()) ? null : val);
            }
        }

        // -- Read value labels if attached --
        if (isNumeric && !novaluelabels) {
            try {
                String lblName = ValueLabel.getVarValueLabel(varIdx);
                if (lblName != null && !lblName.trim().isEmpty()) {
                    Set<Double> seen = new LinkedHashSet<>();
                    for (Object v : var.getValues())
                        if (v instanceof Number) seen.add(((Number) v).doubleValue());
                    for (double code : seen) {
                        try {
                            String txt = ValueLabel.getLabel(lblName, code);
                            if (txt != null && !txt.trim().isEmpty())
                                var.putValueLabel(code, txt.trim());
                        } catch (Exception e2) { /* no label for this value */ }
                    }
                }
            } catch (Exception e) { /* no label set attached */ }
        }

        return var;
    }

}

