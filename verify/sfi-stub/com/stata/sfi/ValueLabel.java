package com.stata.sfi;
/* verify/sfi-stub: value labels for the Data fixture (foreign -> origin). Never shipped. */
public final class ValueLabel {
    public static String getVarValueLabel(int var) { return var == 4 ? "origin" : ""; }
    public static String getLabel(String lblname, int value) {
        if ("origin".equals(lblname)) return value == 0 ? "Domestic" : (value == 1 ? "Foreign" : "");
        return "";
    }
    public static String getLabel(String lblname, double value) { return getLabel(lblname, (int) value); }
    public static int[] getValues(String lblname) { return "origin".equals(lblname) ? new int[]{0, 1} : new int[0]; }
}
