package com.dashboard.data;

import java.util.*;
import java.util.LinkedHashMap;

/**
 * Holds all variables read from Stata for dashboard generation.
 *
 * F-0 (sparkta): filter1Var and filter2Var replaced by
 * List<Variable> filterVars so the engine supports unlimited filter
 * dropdowns. Backward-compat accessors (getFilter1Variable etc.) are
 * preserved so callers only need minimal edits.
 */
public class DataSet {

    private List<Variable> variables;
    private Variable       overVariable;
    private Variable       byVariable;
    private List<Variable> filterVars;     // F-0: replaces filter1Var/filter2Var
    private List<Variable> sliderVars;     // F-1: numeric range slider variables
    private int            observationCount;

    public DataSet() {
        this.variables   = new ArrayList<>();
        this.filterVars  = new ArrayList<>();
        this.sliderVars  = new ArrayList<>();
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

    // ---- F-0: filter list API -----------------------------------------------

    /** Returns all filter variables (may be empty, never null). */
    public List<Variable> getFilterVariables() { return filterVars; }

    /** Appends a filter variable. */
    public void addFilterVariable(Variable v) { if (v != null) filterVars.add(v); }

    /** Number of filter variables registered. */
    public int getFilterCount() { return filterVars.size(); }

    /** Returns filter variable at slot i, or null if out of range. */
    public Variable getFilterVariable(int i) {
        return (i >= 0 && i < filterVars.size()) ? filterVars.get(i) : null;
    }

    /** True if at least one filter variable exists. */
    public boolean hasFilter()  { return !filterVars.isEmpty(); }

    // ---- F-1: slider list API -----------------------------------------------

    /** Returns all slider variables (may be empty, never null). */
    public List<Variable> getSliderVariables() { return sliderVars; }

    /** Appends a slider variable. */
    public void addSliderVariable(Variable v) { if (v != null) sliderVars.add(v); }

    /** True if at least one slider variable exists. */
    public boolean hasSliders() { return !sliderVars.isEmpty(); }

    /** True if chart has any interactive filter (dropdown or slider). */
    public boolean hasAnyFilter() { return !filterVars.isEmpty() || !sliderVars.isEmpty(); }

    public boolean hasOver()    { return overVariable != null; }
    public boolean hasBy()      { return byVariable   != null; }

    // ---- backward-compat accessors ------------------------------------------

    public Variable getFilter1Variable()       { return getFilterVariable(0); }
    public void setFilter1Variable(Variable v) {
        if (filterVars.isEmpty()) filterVars.add(v); else filterVars.set(0, v);
    }
    public Variable getFilter2Variable()       { return getFilterVariable(1); }
    public void setFilter2Variable(Variable v) {
        while (filterVars.size() < 2) filterVars.add(null);
        filterVars.set(1, v);
    }
    public boolean hasFilter1() { return filterVars.size() >= 1 && filterVars.get(0) != null; }
    public boolean hasFilter2() { return filterVars.size() >= 2 && filterVars.get(1) != null; }

    public boolean isEmpty() {
        return variables.isEmpty() || observationCount == 0;
    }

    /** Returns all variables (numeric and string) in the dataset. */
    public List<Variable> getAllVariables() {
        return new ArrayList<>(variables);
    }

    public List<Variable> getNumericVariables() {
        List<Variable> out = new ArrayList<>();
        for (Variable v : variables) { if (v.isNumeric()) out.add(v); }
        return out;
    }

    public List<Variable> getStringVariables() {
        List<Variable> out = new ArrayList<>();
        for (Variable v : variables) { if (!v.isNumeric()) out.add(v); }
        return out;
    }

    public Variable getFirstStringVariable() {
        for (Variable v : variables) { if (!v.isNumeric()) return v; }
        return null;
    }

    /** Sentinel label for observations where a grouping variable is missing. */
    public static final String MISSING_SENTINEL = "(Missing)";

    // ---- uniqueValues / uniqueGroupKeys (unchanged logic) -------------------

    public static List<String> uniqueValues(Variable var, String sortgroups, boolean showMissing) {
        List<String> vals = new ArrayList<>(uniqueValues(var, sortgroups));
        if (showMissing) {
            vals.remove("");
            boolean hasMissing = false;
            for (Object v : var.getValues()) if (v == null) { hasMissing = true; break; }
            if (hasMissing) vals.add(MISSING_SENTINEL);
        }
        return vals;
    }

    public static List<String> uniqueGroupKeys(Variable var, String sortgroups, boolean showMissing) {
        List<String> keys = new ArrayList<>(uniqueGroupKeys(var, sortgroups));
        if (showMissing) {
            keys.remove("");
            boolean hasMissing = false;
            for (Object v : var.getValues()) if (v == null) { hasMissing = true; break; }
            if (hasMissing) keys.add(MISSING_SENTINEL);
        }
        return keys;
    }

    public static List<String> uniqueValues(Variable var) { return uniqueValues(var, false); }

    public static List<String> uniqueValues(Variable var, String sortgroups) {
        if (sortgroups != null && sortgroups.equals("desc")) return uniqueValuesDesc(var);
        return uniqueValues(var, true);
    }

    public static List<String> uniqueValues(Variable var, boolean sort) {
        LinkedHashMap<String,String> seen = new LinkedHashMap<>();
        List<Double> numericKeys = new ArrayList<>();
        for (Object val : var.getValues()) {
            String raw = val == null ? "" : String.valueOf(val);
            if (raw.endsWith(".0")) raw = raw.substring(0, raw.length() - 2);
            if (seen.containsKey(raw)) continue;
            String display = raw;
            if (var.hasValueLabels() && val instanceof Number) {
                String lbl = var.getValueLabel(((Number) val).doubleValue());
                if (lbl != null) display = lbl;
            }
            seen.put(raw, display);
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

    public static List<String> uniqueGroupKeys(Variable var, String sortgroups) {
        if (sortgroups != null && sortgroups.equals("desc")) return uniqueGroupKeysDesc(var);
        return uniqueGroupKeys(var, true);
    }

    public static List<String> uniqueGroupKeys(Variable var, boolean sort) {
        java.util.LinkedHashSet<String> seenSet = new java.util.LinkedHashSet<>();
        List<String>  rawSeen    = new ArrayList<>();
        List<Double>  numericKeys = new ArrayList<>();
        for (Object val : var.getValues()) {
            String raw = val == null ? "" : String.valueOf(val);
            if (raw.endsWith(".0")) raw = raw.substring(0, raw.length() - 2);
            if (!seenSet.add(raw)) continue;
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

    private static List<String> uniqueValuesDesc(Variable var) {
        List<String> d = new ArrayList<>(uniqueValues(var, true));
        java.util.Collections.reverse(d); return d;
    }
    private static List<String> uniqueGroupKeysDesc(Variable var) {
        List<String> d = new ArrayList<>(uniqueGroupKeys(var, true));
        java.util.Collections.reverse(d); return d;
    }
}
