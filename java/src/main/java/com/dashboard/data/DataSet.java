package com.dashboard.data;

import java.util.*;
import java.util.LinkedHashMap;

/**
 * Holds all variables read from Stata for dashboard generation.
 */
public class DataSet {

    private List<Variable> variables;
    private Variable       overVariable;   // over() grouping variable
    private Variable       byVariable;     // by() panel variable
    private Variable       filter1Var;     // filter() interactive dropdown 1
    private Variable       filter2Var;     // filter2() interactive dropdown 2
    private int            observationCount;

    public DataSet() {
        this.variables = new ArrayList<>();
        this.observationCount = 0;
    }

    public void addVariable(Variable var) {
        this.variables.add(var);
        if (var.size() > observationCount) observationCount = var.size();
    }

    public List<Variable> getVariables()     { return variables; }
    public Variable getVariable(int index)   { return variables.get(index); }
    public int getVariableCount()            { return variables.size(); }
    public int getObservationCount()         { return observationCount; }

    public Variable getOverVariable()        { return overVariable; }
    public void setOverVariable(Variable v)  { this.overVariable = v; }

    public Variable getByVariable()          { return byVariable; }
    public void setByVariable(Variable v)    { this.byVariable = v; }

    public Variable getFilter1Variable()     { return filter1Var; }
    public void setFilter1Variable(Variable v) { this.filter1Var = v; }

    public Variable getFilter2Variable()     { return filter2Var; }
    public void setFilter2Variable(Variable v) { this.filter2Var = v; }

    public boolean hasOver()                 { return overVariable != null; }
    public boolean hasBy()                   { return byVariable != null; }
    public boolean hasFilter1()              { return filter1Var != null; }
    public boolean hasFilter2()              { return filter2Var != null; }

    public boolean isEmpty() {
        return variables.isEmpty() || observationCount == 0;
    }

    public List<Variable> getNumericVariables() {
        List<Variable> numeric = new ArrayList<>();
        for (Variable v : variables) {
            if (v.isNumeric()) numeric.add(v);
        }
        return numeric;
    }

    public List<Variable> getStringVariables() {
        List<Variable> strings = new ArrayList<>();
        for (Variable v : variables) {
            if (!v.isNumeric()) strings.add(v);
        }
        return strings;
    }

    public Variable getFirstStringVariable() {
        for (Variable v : variables) {
            if (!v.isNumeric()) return v;
        }
        return null;
    }

    /** v1.9.1: Sentinel label for observations where a grouping variable is missing. */
    public static final String MISSING_SENTINEL = "(Missing)";

    /**
     * Returns the unique values of a variable as an ordered list.
     * Numeric values like "1.0" are normalised to "1" so group
     * lookups are consistent across the whole generator.
     */
    /**
     * showmissing overload: like uniqueValues but includes null/missing values
     * as the MISSING_SENTINEL "(Missing)" label, sorted last. v1.9.1
     */
    public static List<String> uniqueValues(Variable var, String sortgroups, boolean showMissing) {
        List<String> vals = new ArrayList<>(uniqueValues(var, sortgroups));
        if (showMissing) {
            // Remove the raw "" entry that the base method emits for null values,
            // then append MISSING_SENTINEL as the canonical label. v1.9.1
            vals.remove("");
            boolean hasMissing = false;
            for (Object v : var.getValues()) if (v == null) { hasMissing = true; break; }
            if (hasMissing) vals.add(MISSING_SENTINEL); // always last
        }
        return vals;
    }

    /**
     * showmissing overload for group keys. v1.9.1
     */
    public static List<String> uniqueGroupKeys(Variable var, String sortgroups, boolean showMissing) {
        List<String> keys = new ArrayList<>(uniqueGroupKeys(var, sortgroups));
        if (showMissing) {
            // Remove the raw "" key emitted for null values; replace with sentinel. v1.9.1
            keys.remove("");
            boolean hasMissing = false;
            for (Object v : var.getValues()) if (v == null) { hasMissing = true; break; }
            if (hasMissing) keys.add(MISSING_SENTINEL); // always last
        }
        return keys;
    }

    public static List<String> uniqueValues(Variable var) {
        return uniqueValues(var, false);
    }

    // v1.5: String overload supporting "" (no sort), "asc" (ascending), "desc" (descending)
    // v1.8.7: empty sortgroups now defaults to ascending (not encounter order).
    // Encounter order is almost never useful -- ascending is the sensible default.
    // Only "desc" produces descending; anything else (incl. "") gives ascending.
    public static List<String> uniqueValues(Variable var, String sortgroups) {
        if (sortgroups != null && sortgroups.equals("desc")) return uniqueValuesDesc(var);
        return uniqueValues(var, true); // "" or "asc" or any other value -> ascending
    }

    public static List<String> uniqueValues(Variable var, boolean sort) {
        // v3.5.37: LinkedHashMap replaces ArrayList+contains() for O(1) dedup.
        // Maps raw -> display label, preserving first-encounter insertion order.
        LinkedHashMap<String,String> seen = new LinkedHashMap<>();  // raw -> display
        List<Double> numericKeys = new ArrayList<>();               // parallel numeric keys for sort

        for (Object val : var.getValues()) {
            String raw = val == null ? "" : String.valueOf(val);
            if (raw.endsWith(".0")) raw = raw.substring(0, raw.length() - 2);
            if (seen.containsKey(raw)) continue;  // O(1) dedup

            // Resolve display text: value label if available, else raw
            String display = raw;
            if (var.hasValueLabels() && val instanceof Number) {
                String lbl = var.getValueLabel(((Number) val).doubleValue());
                if (lbl != null) display = lbl;
            }
            seen.put(raw, display);

            // Track numeric key for sorting (parallel to seen insertion order)
            try { numericKeys.add(Double.parseDouble(raw)); }
            catch (NumberFormatException e) { numericKeys.add(null); }
        }

        List<String> labelSeen = new ArrayList<>(seen.values());

        if (sort) {
            int n = labelSeen.size();
            List<int[]> idx = new ArrayList<>();
            for (int i = 0; i < n; i++) idx.add(new int[]{i});
            idx.sort((a, b) -> {
                Double da = numericKeys.get(a[0]);
                Double db = numericKeys.get(b[0]);
                if (da != null && db != null) return Double.compare(da, db);
                return labelSeen.get(a[0]).compareTo(labelSeen.get(b[0]));
            });
            List<String> sorted = new ArrayList<>();
            for (int[] i : idx) sorted.add(labelSeen.get(i[0]));
            return sorted;
        }

        return labelSeen;
    }

    /**
     * Returns unique RAW value strings for a variable, in the same order as
     * uniqueValues(). Used internally for data row matching where we need the
     * original numeric code (e.g. "1"), not the value label (e.g. "Poor").
     * This ensures overDatasets() can match rows correctly even when value
     * labels are active.
     */
    // v1.5: String overload supporting "" (no sort), "asc" (ascending), "desc" (descending)
    // v1.8.7: empty sortgroups defaults to ascending (not encounter order).
    public static List<String> uniqueGroupKeys(Variable var, String sortgroups) {
        if (sortgroups != null && sortgroups.equals("desc")) return uniqueGroupKeysDesc(var);
        return uniqueGroupKeys(var, true); // "" or "asc" -> ascending
    }

    public static List<String> uniqueGroupKeys(Variable var, boolean sort) {
        // v3.5.37: LinkedHashSet replaces ArrayList+contains() for O(1) dedup.
        // Preserves first-encounter insertion order (same contract as before).
        java.util.LinkedHashSet<String> seenSet = new java.util.LinkedHashSet<>();
        List<String>  rawSeen    = new ArrayList<>();
        List<Double> numericKeys = new ArrayList<>();

        for (Object val : var.getValues()) {
            String raw = val == null ? "" : String.valueOf(val);
            if (raw.endsWith(".0")) raw = raw.substring(0, raw.length() - 2);
            if (!seenSet.add(raw)) continue;  // O(1) dedup; add returns false if already present
            rawSeen.add(raw);
            try { numericKeys.add(Double.parseDouble(raw)); }
            catch (NumberFormatException e) { numericKeys.add(null); }
        }

        if (sort) {
            int n = rawSeen.size();
            List<int[]> idx = new ArrayList<>();
            for (int i = 0; i < n; i++) idx.add(new int[]{i});
            idx.sort((a, b) -> {
                Double da = numericKeys.get(a[0]);
                Double db = numericKeys.get(b[0]);
                if (da != null && db != null) return Double.compare(da, db);
                return rawSeen.get(a[0]).compareTo(rawSeen.get(b[0]));
            });
            List<String> sorted = new ArrayList<>();
            for (int[] i : idx) sorted.add(rawSeen.get(i[0]));
            return sorted;
        }

        return rawSeen;
    }

    // v1.5: descending sort helper for uniqueValues
    private static List<String> uniqueValuesDesc(Variable var) {
        List<String> asc = uniqueValues(var, true);
        List<String> desc = new ArrayList<>(asc);
        java.util.Collections.reverse(desc);
        return desc;
    }

    // v1.5: descending sort helper for uniqueGroupKeys
    private static List<String> uniqueGroupKeysDesc(Variable var) {
        List<String> asc = uniqueGroupKeys(var, true);
        List<String> desc = new ArrayList<>(asc);
        java.util.Collections.reverse(desc);
        return desc;
    }
}
