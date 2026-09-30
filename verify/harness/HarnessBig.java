/*
 * HarnessBig.java -- v3.6.0 fix9g: large-scatter oracle pages (100,000 synthetic points).
 *
 * Renders the scatter/bubble pages that fix9g's large-data mode targets, through the real
 * DashboardBuilder on the SFI stub's synthetic BIG fixture (Data.setBig): mpg = x ~ N(0,1),
 * price = y = x + noise, weight = size, foreign 0/1, rep78 1..3. verify/bigscatter_probe.js
 * then measures load time, hover latency and point counts in headless Chromium and compares
 * the render pixel-for-pixel with a reference screenshot.
 *
 * Pages (all SPK_OFFLINE=1 self-contained):
 *   big01_scatter.html          plain 100k scatter
 *   big02_scatter_over.html     100k scatter over(rep78) (3 groups)
 *   big03_scatter_filter.html   100k scatter with filters(foreign)
 *   big04_bubble.html           100k bubble (price mpg weight)
 *   big05_scatter_fit.html      100k scatter fit(lfit) fitci
 *   big06_scatter_by.html       100k scatter by(foreign) -- two 50k panels
 *   big07_scatter_small.html    5,000-point control page (below the threshold: normal mode)
 *   big08_scatter_edge.html     20,001-point page (just above the threshold)
 *   big09_scatter_by_filter.html 100k scatter by(foreign) filters(rep78) -- the by()-panel
 *                               filter updater path (verify/byfilter_probe.js --embedded --glob big09)
 *
 * Usage:  SPK_OFFLINE=1 java -cp <classes>:<sfi-stub> HarnessBig <outdir>
 * ASCII only. verify/ only -- never shipped in the jar.
 */
import java.util.ArrayList;
import java.util.List;

public class HarnessBig {
    static final int BIG_N = 100000;
    static final long SEED = 20260913L;

    // arg slots (see the javacall block in sparkta.ado): 76 filters, 156 fit, 157 fitci
    static String[] big(String title, String file, String vars, String type, String over, String by, String filters) {
        String[] a = Harness.dataCase(title, file, vars, type, over, by, "");
        a[76] = filters;
        return a;
    }

    public static void main(String[] argv) throws Exception {
        Harness.outDir = argv[0];
        new java.io.File(Harness.outDir).mkdirs();
        List<Object[]> cases = new ArrayList<>();   // {n, args}
        String[] a;

        cases.add(new Object[]{BIG_N, big("BIG01 scatter 100k",              "big01_scatter.html",        "price mpg",        "scatter", "",      "",        "")});
        cases.add(new Object[]{BIG_N, big("BIG02 scatter 100k over(rep78)",  "big02_scatter_over.html",   "price mpg",        "scatter", "rep78", "",        "")});
        cases.add(new Object[]{BIG_N, big("BIG03 scatter 100k filters",      "big03_scatter_filter.html", "price mpg",        "scatter", "",      "",        "foreign")});
        cases.add(new Object[]{BIG_N, big("BIG04 bubble 100k",               "big04_bubble.html",         "price mpg weight", "bubble",  "",      "",        "")});
        a = big("BIG05 scatter 100k fit(lfit) fitci",                        "big05_scatter_fit.html",    "price mpg",        "scatter", "",      "",        "");
        a[156] = "lfit"; a[157] = "1"; cases.add(new Object[]{BIG_N, a});
        cases.add(new Object[]{BIG_N, big("BIG06 scatter 100k by(foreign)",  "big06_scatter_by.html",     "price mpg",        "scatter", "",      "foreign", "")});
        cases.add(new Object[]{5000,  big("BIG07 scatter 5k (control)",      "big07_scatter_small.html",  "price mpg",        "scatter", "",      "",        "")});
        cases.add(new Object[]{20001, big("BIG08 scatter 20001 (edge)",      "big08_scatter_edge.html",   "price mpg",        "scatter", "",      "",        "")});
        cases.add(new Object[]{BIG_N, big("BIG09 scatter 100k by+filter",    "big09_scatter_by_filter.html", "price mpg",     "scatter", "",      "foreign", "rep78")});

        int rc = 0, n = 0;
        for (Object[] c : cases) {
            String[] args = (String[]) c[1];
            com.stata.sfi.Data.setBig((Integer) c[0], SEED);
            com.stata.sfi.Data.markoutRep78 = false;   // the big fixture has no missing rep78
            int r;
            long t0 = System.currentTimeMillis();
            try { r = com.dashboard_test.DashboardBuilder.execute(args); }
            catch (Throwable t) { System.out.println("  EXC " + args[2] + ": " + t); r = 1; }
            long ms = System.currentTimeMillis() - t0;
            java.io.File f = new java.io.File(args[4]);
            System.out.println("  " + (r == 0 ? "ok  " : "FAIL") + " " + args[2] + "  (" + ms + " ms, " + (f.length() / 1024) + " KB)");
            if (r != 0) rc = 1;
            n++;
        }
        com.stata.sfi.Data.setBig(0, 0);
        System.out.println("HarnessBig: " + n + " scenarios, rc=" + rc);
        System.exit(rc);
    }
}
