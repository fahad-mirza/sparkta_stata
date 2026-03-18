package com.dashboard.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single Stata variable with its metadata and values.
 *
 * valueLabels stores the Stata value label mapping for this variable,
 * e.g. {1.0 -> "Poor", 2.0 -> "Fair", 3.0 -> "Average"} for rep78.
 * When present, group labels in charts use the text label instead of
 * the raw numeric value.
 *
 * v3.5.8: groupIndex() builds a Map<key, List<rowIdx>> lazily on first call.
 * Key normalisation matches DatasetBuilder/StatsRenderer: sdz(String.valueOf(v)).
 * DataSet.MISSING_SENTINEL is used as the key when v is null (numeric missing)
 * or empty string (string missing -- see StataDataReader v3.5.8 fix).
 * Callers replace O(N) per-group scans with a single O(1) map.get(groupKey).
 */
public class Variable {

    private String              name;
    private String              label;
    private boolean             numeric;
    private List<Object>        values;
    private Map<Double, String> valueLabels;   // numeric value -> label text
    private Map<String, List<Integer>> _groupIndex; // lazy; null until first call

    public Variable() {
        this.values      = new ArrayList<>();
        this.valueLabels = new LinkedHashMap<>();
        this._groupIndex = null;
    }

    // -------------------------
    // Getters and Setters
    // -------------------------

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = (label == null || label.trim().isEmpty()) ? name : label.trim();
    }

    public boolean isNumeric() {
        return numeric;
    }

    public void setNumeric(boolean numeric) {
        this.numeric = numeric;
    }

    public List<Object> getValues() {
        return values;
    }

    public void setValues(List<Object> values) {
        this.values = values;
    }

    public void addValue(Object value) {
        this.values.add(value);
    }

    public int size() {
        return values.size();
    }

    // -------------------------
    // Value label support
    // -------------------------

    /**
     * Store a value label mapping: numeric code -> display text.
     * e.g. putValueLabel(1.0, "Poor") for rep78.
     */
    public void putValueLabel(double code, String text) {
        if (text != null && !text.trim().isEmpty())
            valueLabels.put(code, text.trim());
    }

    /**
     * Returns the text label for a numeric code, or null if no label exists.
     */
    public String getValueLabel(double code) {
        return valueLabels.get(code);
    }

    /**
     * Returns true if this variable has any value labels attached.
     */
    public boolean hasValueLabels() {
        return !valueLabels.isEmpty();
    }

    /**
     * Returns the full value label map (read-only view).
     */
    public Map<Double, String> getValueLabels() {
        return valueLabels;
    }

    /**
     * Returns display name: variable label if available, otherwise variable name.
     */
    public String getDisplayName() {
        return (label != null && !label.isEmpty()) ? label : name;
    }

    // -------------------------
    // Group index (v3.5.8)
    // -------------------------

    /**
     * Returns a lazily-built Map<normalizedKey, List<rowIndex>> for this variable.
     * Scanning all N observations once and caching the result converts every
     * per-group O(N) scan in DatasetBuilder and StatsRenderer to O(1).
     *
     * Key normalisation rules (must match all callers):
     *   null value (numeric missing)  -> DataSet.MISSING_SENTINEL
     *   ""   value (string missing)   -> DataSet.MISSING_SENTINEL
     *   "1.0" / Double 1.0            -> "1"   (sdz strips trailing .0)
     *   "London"                      -> "London" (no-op for real strings)
     *
     * The map is built once per Variable instance and cached. If values are
     * mutated after first call, call invalidateGroupIndex() to force rebuild.
     */
    public synchronized Map<String, List<Integer>> groupIndex() {
        if (_groupIndex != null) return _groupIndex;
        Map<String, List<Integer>> idx = new LinkedHashMap<>();
        int n = values.size();
        for (int i = 0; i < n; i++) {
            Object v = values.get(i);
            String key;
            if (v == null) {
                key = DataSet.MISSING_SENTINEL;
            } else {
                String s = String.valueOf(v);
                // sdz: strip trailing ".0" so "1.0" and "1" map to same key
                if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
                // empty string (string-var missing) -> sentinel
                key = s.isEmpty() ? DataSet.MISSING_SENTINEL : s;
            }
            idx.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        _groupIndex = idx;
        return _groupIndex;
    }

    /** Clears the cached group index so groupIndex() rebuilds on next call. */
    public synchronized void invalidateGroupIndex() {
        _groupIndex = null;
    }

    @Override
    public String toString() {
        return "Variable{name='" + name + "', label='" + label +
               "', numeric=" + numeric + ", obs=" + values.size() + "}";
    }
}
