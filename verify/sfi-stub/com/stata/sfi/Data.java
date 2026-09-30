package com.stata.sfi;
/*
 * verify/sfi-stub: Data with a FIXTURE (v3.6.0-s9g).
 * 30 observations shaped like auto.dta so the harness can render DATA charts
 * (bar/line/box/violin/scatter, over(), by(), filters) through the real
 * StataDataReader without Stata. Variables (1-based like Stata):
 *   1 price  2 mpg  3 weight  4 foreign(0/1)  5 rep78(1-5, some missing)  6 __touse (all 1)
 * fix9g: a synthetic BIG fixture (Data.setBig(n, seed)) for the large-scatter oracle --
 * same five variables, n rows: mpg = x ~ N(0,1), price = y = x + noise, weight = size
 * uniform 1..10, foreign = 0/1, rep78 = 1..3 (no missing). setBig(0, 0) restores auto.
 * Never shipped -- verify/ only.
 */
public final class Data {
    public static final int TYPE_STR = 15;
    public static final int TYPE_STRL = 32768;
    public static final int TYPE_DOUBLE = 4;

    static final String[] NAMES  = { "price", "mpg", "weight", "foreign", "rep78", "__touse" };
    static final String[] LABELS = { "Price", "Mileage (mpg)", "Weight (lbs.)", "Car origin", "Repair record 1978", "" };
    static final double NA = Double.NaN;
    static final double[][] ROWS = {
        {4099,22,2930,0,3},{4749,17,3350,0,3},{3799,22,2640,0,NA},{4816,20,3250,0,3},{7827,15,4080,0,4},
        {5788,18,3670,0,3},{4453,26,2230,0,NA},{5189,20,3280,0,3},{10372,16,3880,0,3},{4082,19,3400,0,3},
        {11385,14,4330,0,3},{14500,14,3900,0,2},{15906,21,4290,0,3},{3299,29,2110,0,3},{5705,16,3690,0,4},
        {4504,22,3180,0,3},{5104,22,3220,0,2},{3667,24,2750,0,1},{3955,19,3430,0,4},{3984,30,2120,0,1},
        {9690,17,2830,1,5},{6295,23,2070,1,4},{9735,25,2200,1,5},{6229,23,2130,1,4},{4589,35,2050,1,4},
        {5079,25,2240,1,4},{8129,21,2750,1,5},{4296,25,2650,1,4},{5799,18,2410,1,3},{4499,28,2350,1,4}
    };
    /** fix9a: the ado markouts obs with a missing over()/by()/filters() var (unless showmissing);
     *  the harness sets this so a touse read returns 0 for rows with rep78 missing. verify/ only. */
    public static boolean markoutRep78 = false;
    /** fix9g: synthetic big fixture (null = the 30-row auto fixture above). */
    static double[][] BIG = null;
    public static void setBig(int n, long seed) {
        if (n <= 0) { BIG = null; return; }
        java.util.Random rnd = new java.util.Random(seed);
        BIG = new double[n][5];
        for (int i = 0; i < n; i++) {
            double x = rnd.nextGaussian();
            double y = x + rnd.nextGaussian();
            BIG[i][0] = Math.round(y * 1000.0) / 1000.0;      // price = y
            BIG[i][1] = Math.round(x * 1000.0) / 1000.0;      // mpg   = x
            BIG[i][2] = 1 + rnd.nextInt(10);                   // weight = size 1..10
            BIG[i][3] = rnd.nextInt(2);                        // foreign 0/1
            BIG[i][4] = 1 + rnd.nextInt(3);                    // rep78 1..3
        }
    }
    static double[][] rows() { return BIG != null ? BIG : ROWS; }
    public static long getObsTotal() { return rows().length; }
    public static int getVarCount() { return NAMES.length; }
    public static int getVarIndex(String name) {
        if (name == null) return 0;
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i + 1;
        if (name.startsWith("__")) return NAMES.length;   // any temp touse var -> all ones
        return 0;
    }
    public static double getNum(int var, long obs) {
        double[][] R = rows();
        if (var < 1 || var > NAMES.length || obs < 1 || obs > R.length) return NA;
        if (var == NAMES.length) return (markoutRep78 && Double.isNaN(R[(int) obs - 1][4])) ? 0 : 1;
        return R[(int) obs - 1][var - 1];
    }
    public static String getStr(int var, long obs) { return ""; }
    public static boolean isValueMissing(double v) { return Double.isNaN(v) || v >= 8.988e307; }
    public static int getType(int var) { return TYPE_DOUBLE; }
    public static boolean isVarTypeStr(int var) { return false; }
    public static String getVarLabel(int var) { return (var >= 1 && var <= LABELS.length) ? LABELS[var - 1] : ""; }
    public static String getVarName(int var) { return (var >= 1 && var <= NAMES.length) ? NAMES[var - 1] : ""; }
    public static int getNumVarVector(int var, double[] buf, long o1, long o2) {
        int n = 0;
        for (long o = o1; o <= o2 && n < buf.length; o++) buf[n++] = getNum(var, o);
        return 0;
    }
}
