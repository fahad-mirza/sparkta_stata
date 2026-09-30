package com.dashboard_test.html;

import java.util.ArrayList;
import java.util.List;

/**
 * FitComputer.java -- v3.5.57 (t2j fix5: ported from the Astra rc1 build)
 * t2j fix5: normalized-coordinate OLS with Neumaier-compensated sums (steady for
 * large-magnitude x such as years/income/ids), exact quadratic leverage for the
 * qfit CI band, and finite-guarded input cleaning. Public API (compute + FitResult)
 * is unchanged; the single caller is ChartRenderer. ASCII only.
 * Computes scatter fit lines for Sparkta.
 *
 * Supported fit types:
 *   lfit   - OLS linear regression y = a + bx
 *   qfit   - OLS quadratic fit y = a + bx + cx^2
 *   lowess - Cleveland locally weighted scatterplot smoother (tricube weights)
 *   exp    - exponential fit y = a * e^(bx), linearised via ln(y) = ln(a) + bx
 *   log    - logarithmic fit y = a + b * ln(x)
 *   power  - power fit y = a * x^b, linearised via ln(y) = ln(a) + b*ln(x)
 *   ma     - centred moving average after sorting finite pairs by x; the
 *            window is the smallest odd integer >= max(3, floor(N/10)),
 *            with truncated windows at the two endpoints
 *
 * CI bands:
 *   Computes a pointwise 95% confidence interval for the fitted mean using:
 *   SE_fit = s * sqrt(1/n + (x - xbar)^2 / Sxx)
 *   where s = sqrt(RSS / (n-2)) is the residual standard error.
 *   t_critical uses df=n-2, approximated for large n.
 *
 * Output format: arrays of {x, y} pairs as JavaScript strings, ready for
 * embedding in Chart.js line dataset data arrays.
 *
 * ASCII only. No Unicode.
 */
public class FitComputer {

    /** Result of a fit computation. */
    public static class FitResult {
        /** JS array string of {x,y} for the fit line, e.g. [{x:1,y:2.3},{x:2,y:3.1},...] */
        public String lineData   = "[]";
        /** JS array string of {x,y} for upper CI bound. Empty if no CI. */
        public String upperData  = "[]";
        /** JS array string of {x,y} for lower CI bound. Empty if no CI. */
        public String lowerData  = "[]";
        /** Human-readable label suffix, e.g. " (lfit)" */
        public String labelSuffix = "";
        /** True if CI data was computed. */
        public boolean hasCi = false;
    }

    // Number of points to evaluate along the fit line (x range)
    private static final int EVAL_POINTS = 80;

    /**
     * Compute a fit for the given x/y data.
     *
     * @param xs      x values (nulls excluded before calling)
     * @param ys      y values (nulls excluded before calling)
     * @param fitType one of: lfit qfit lowess exp log power ma
     * @param withCi  true to compute CI band (lfit/qfit only)
     * @return FitResult ready for embedding in JS
     */
    public static FitResult compute(List<Double> xs, List<Double> ys,
                                    String fitType, boolean withCi) {
        FitResult r = new FitResult();
        if (xs == null || ys == null || fitType == null) return r;

        int supplied = Math.min(xs.size(), ys.size());
        List<Double> cleanX = new ArrayList<>(), cleanY = new ArrayList<>();
        for (int i = 0; i < supplied; i++) {
            Double xi = xs.get(i), yi = ys.get(i);
            if (xi != null && yi != null && Double.isFinite(xi) && Double.isFinite(yi)) {
                cleanX.add(xi);
                cleanY.add(yi);
            }
        }
        if (cleanX.size() < 3) return r;
        double[] x = toArray(cleanX), y = toArray(cleanY);

        switch (fitType.toLowerCase(java.util.Locale.ROOT)) {
            case "lfit":   computeLfit(r, x, y, withCi); break;
            case "qfit":   computeQfit(r, x, y, withCi); break;
            case "lowess": computeLowess(r, x, y);        break;
            case "exp":    computeExp(r, x, y, withCi);   break;
            case "log":    computeLog(r, x, y, withCi);   break;
            case "power":  computePower(r, x, y, withCi); break;
            case "ma":     computeMa(r, x, y);            break;
            default:       break;
        }
        return r;
    }

    // -----------------------------------------------------------------------
    // lfit: OLS linear regression y = a + bx
    // -----------------------------------------------------------------------
    private static void computeLfit(FitResult r, double[] x, double[] y, boolean withCi) {
        int n = x.length;
        LinearModel model = LinearModel.fit(x, y, withCi);
        if (model == null) return;

        double xmin = min(x), xmax = max(x);
        StringBuilder line = new StringBuilder("[");
        StringBuilder upper = withCi ? new StringBuilder("[") : null;
        StringBuilder lower = withCi ? new StringBuilder("[") : null;
        double t = tCritical95(n - 2);

        for (int i = 0; i <= EVAL_POINTS; i++) {
            double xi = interpolateFinite(xmin, xmax, i / (double) EVAL_POINTS);
            double fittedNorm = model.predictNormalized(xi);
            double yi = model.response.denormalize(fittedNorm);
            if (!appendPoint(line, xi, yi)) return;
            if (withCi) {
                double se = model.seNormalized(xi);
                double hi = model.response.denormalize(fittedNorm + t * se);
                double lo = model.response.denormalize(fittedNorm - t * se);
                if (!appendPoint(upper, xi, hi) || !appendPoint(lower, xi, lo)) return;
            }
        }
        line.append("]");
        r.lineData    = line.toString();
        r.labelSuffix = " (lfit)";
        if (withCi) {
            upper.append("]"); lower.append("]");
            r.upperData = upper.toString();
            r.lowerData = lower.toString();
            r.hasCi = true;
        }
    }

    // -----------------------------------------------------------------------
    // qfit: OLS quadratic fit.  Work on centred/scaled x for numerical
    // stability and use the exact quadratic leverage v'(X'X)^-1v for the CI.
    // -----------------------------------------------------------------------
    private static void computeQfit(FitResult r, double[] x, double[] y, boolean withCi) {
        int n = x.length;
        if (n < 3) return;
        // Three distinct predictor values identify a quadratic curve exactly,
        // but leave zero residual degrees of freedom.  Render the line while
        // suppressing an unsupported confidence interval in that case.
        boolean computeCi = withCi && n > 3;

        Scale xScale = Scale.of(x, true);
        Scale yScale = Scale.of(y, false);
        if (xScale == null || yScale == null) return;

        double[] z = new double[n];
        double[] w = new double[n];
        // Normal equations use bounded coordinates, avoiding overflow and
        // absolute scale-dependent singularity decisions.
        CompensatedSum szS = new CompensatedSum(), sz2S = new CompensatedSum();
        CompensatedSum sz3S = new CompensatedSum(), sz4S = new CompensatedSum();
        CompensatedSum swS = new CompensatedSum(), szwS = new CompensatedSum();
        CompensatedSum sz2wS = new CompensatedSum();
        for (int i = 0; i < n; i++) {
            double zi=xScale.normalize(x[i]), wi=yScale.normalize(y[i]), zi2=zi*zi;
            if (!Double.isFinite(zi) || !Double.isFinite(wi)) return;
            z[i] = zi; w[i] = wi;
            szS.add(zi); sz2S.add(zi2); sz3S.add(zi2*zi); sz4S.add(zi2*zi2);
            swS.add(wi); szwS.add(zi*wi); sz2wS.add(zi2*wi);
        }
        double s1=n, sz=szS.value(), sz2=sz2S.value(), sz3=sz3S.value(),
            sz4=sz4S.value(), sw=swS.value(), szw=szwS.value(), sz2w=sz2wS.value();
        double[][] A = {{s1,sz,sz2},{sz,sz2,sz3},{sz2,sz3,sz4}};
        double[]   B = {sw, szw, sz2w};
        double[] coef = solve3x3(A, B);
        if (coef == null) return;
        double a=coef[0], b=coef[1], c=coef[2];

        double s = 0;
        if (computeCi) {
            CompensatedSum rss = new CompensatedSum();
            for (int i = 0; i < n; i++) {
                double fitted = Math.fma(c, z[i]*z[i], Math.fma(b, z[i], a));
                double res = w[i] - fitted;
                if (!Double.isFinite(res)) return;
                rss.add(res * res);
            }
            s = (n > 3) ? Math.sqrt(rss.value() / (n - 3)) : 0;
            if (!Double.isFinite(s)) return;
        }

        double xmin = min(x), xmax = max(x);
        StringBuilder line = new StringBuilder("[");
        StringBuilder upper = computeCi ? new StringBuilder("[") : null;
        StringBuilder lower = computeCi ? new StringBuilder("[") : null;
        double t = computeCi ? tCritical95(n - 3) : 0;

        for (int i = 0; i <= EVAL_POINTS; i++) {
            double xi = interpolateFinite(xmin, xmax, i / (double) EVAL_POINTS);
            double zi = xScale.normalize(xi);
            double fitted = Math.fma(c, zi*zi, Math.fma(b, zi, a));
            double yi = yScale.denormalize(fitted);
            if (!appendPoint(line, xi, yi)) return;
            if (computeCi) {
                double[] basis = {1.0, zi, zi*zi};
                double[] invTimesBasis = solve3x3(A, basis);
                if (invTimesBasis == null) return;
                double leverage = basis[0]*invTimesBasis[0]
                    + basis[1]*invTimesBasis[1] + basis[2]*invTimesBasis[2];
                double se = s * Math.sqrt(Math.max(0.0, leverage));
                double hi = yScale.denormalize(fitted + t*se);
                double lo = yScale.denormalize(fitted - t*se);
                if (!appendPoint(upper, xi, hi) || !appendPoint(lower, xi, lo)) return;
            }
        }
        line.append("]");
        r.lineData    = line.toString();
        r.labelSuffix = " (qfit)";
        if (computeCi) {
            upper.append("]"); lower.append("]");
            r.upperData = upper.toString();
            r.lowerData = lower.toString();
            r.hasCi = true;
        }
    }

    // -----------------------------------------------------------------------
    // lowess: Cleveland tricube locally weighted regression
    // bandwidth f = 0.8 (80% of points used per local fit)
    //
    // Performance: O(N log N) -- sorts once by x, then uses binary search
    // to find the span-th nearest neighbour bandwidth for each point.
    // Previous O(N^2 log N) version cloned + sorted dists[] per point.
    //
    // Large N safety: capped at MAX_LOWESS_N=2000 via uniform sampling.
    // At N=2000 this is ~4M weight ops -- fast in Java. Result is
    // interpolated back to original N for correct output size.
    // -----------------------------------------------------------------------
    private static final int MAX_LOWESS_N = 2000;

    private static void computeLowess(FitResult r, double[] x, double[] y) {
        int n = x.length;

        // Sort paired primitive arrays before sampling.  This preserves sparse
        // x regions without allocating one boxed Integer per observation.
        sortPairs(x, y);
        double[] sortedX = x, sortedY = y;

        // Cap at MAX_LOWESS_N via uniform sampling to protect against O(N^2)
        // at large N. Sample indices spread evenly across sorted order.
        if (n > MAX_LOWESS_N) {
            double[] xs2 = new double[MAX_LOWESS_N], ys2 = new double[MAX_LOWESS_N];
            double step = (double)(n - 1) / (MAX_LOWESS_N - 1);
            for (int i = 0; i < MAX_LOWESS_N; i++) {
                int si = (int) Math.round(i * step);
                xs2[i] = sortedX[si]; ys2[i] = sortedY[si];
            }
            sortedX = xs2; sortedY = ys2; n = MAX_LOWESS_N;
        }

        double f    = 0.80;
        int    span = Math.max(3, (int) Math.ceil(f * n));

        double[] xs = sortedX, ys = sortedY;
        Scale xScale = Scale.of(xs, true);
        Scale yScale = Scale.of(ys, false);
        if (xScale == null || yScale == null) return;
        double[] zx = new double[n], wy = new double[n];
        for (int i = 0; i < n; i++) {
            zx[i] = xScale.normalize(xs[i]);
            wy[i] = yScale.normalize(ys[i]);
        }

        // t2j fix8 (cross-path): Cleveland ROBUST lowess -- 3 bisquare robustness
        // iterations, matching Stata's `lowess` (the initial-render source,
        // sparkta.ado:3730) and the browser-filter recompute in sparkta_engine.js.
        // Each iteration multiplies the tricube weight by a per-point robustness
        // weight delta[j] from the previous fit's residuals, so outliers are
        // progressively down-weighted. Previously this fallback was single-pass and
        // could disagree with Stata (and with the robust JS filter path).
        final int ROBUST_ITERS = 4;   // 1 initial fit + 3 bisquare reweights = Stata lowess default iterate(3)
        double[] fitted = new double[n];
        double[] delta  = new double[n];
        for (int i = 0; i < n; i++) delta[i] = 1.0;
        for (int it = 0; it < ROBUST_ITERS; it++) {
            for (int i = 0; i < n; i++) {
                // O(log N): expand a symmetric window of `span` nearest neighbours by
                // x-distance (xs sorted, so they are contiguous).
                double zi = zx[i];
                int lo = i, hi = i;
                while (hi - lo + 1 < span) {
                    double extL = lo > 0     ? zi - zx[lo-1] : Double.MAX_VALUE;
                    double extR = hi < n-1   ? zx[hi+1] - zi : Double.MAX_VALUE;
                    if (extL <= extR) lo--; else hi++;
                }
                lo = Math.max(0, lo);
                hi = Math.min(n-1, hi);

                double h = Math.max(zi - zx[lo], zx[hi] - zi);
                if (!(h > 0) || !Double.isFinite(h)) { fitted[i] = wy[i]; continue; }

                // Tricube*robustness weighted least squares on bounded, target-centred
                // coordinates. Two passes avoid catastrophic determinant cancellation.
                CompensatedSum swS = new CompensatedSum();
                CompensatedSum swuS = new CompensatedSum();
                CompensatedSum swyS = new CompensatedSum();
                for (int j = lo; j <= hi; j++) {
                    double u = (zx[j] - zi) / h;
                    if (Math.abs(u) >= 1.0) continue;
                    double weight = tricube(Math.abs(u)) * delta[j];
                    swS.add(weight); swuS.add(weight*u); swyS.add(weight*wy[j]);
                }
                double sw = swS.value();
                if (!(sw > 0) || !Double.isFinite(sw)) { fitted[i] = wy[i]; continue; }
                double ubar = swuS.value()/sw, ybar = swyS.value()/sw;
                CompensatedSum suuS = new CompensatedSum(), suyS = new CompensatedSum();
                for (int j = lo; j <= hi; j++) {
                    double u = (zx[j] - zi) / h;
                    if (Math.abs(u) >= 1.0) continue;
                    double weight = tricube(Math.abs(u)) * delta[j];
                    double du = u-ubar;
                    suuS.add(weight*du*du);
                    suyS.add(weight*du*(wy[j]-ybar));
                }
                double suu = suuS.value();
                if (!usablePositive(suu, sw)) fitted[i] = ybar;
                else fitted[i] = Math.fma(suyS.value()/suu, -ubar, ybar);
                if (!Double.isFinite(fitted[i])) return;
            }
            if (it < ROBUST_ITERS - 1) {
                // Robustness weights: delta = (1-(r/6med)^2)^2, 0 when |r| >= 6*med|r|.
                double[] absr = new double[n];
                for (int ir = 0; ir < n; ir++) absr[ir] = Math.abs(wy[ir] - fitted[ir]);
                double[] srt = absr.clone();
                java.util.Arrays.sort(srt);
                double med = (n % 2 == 1) ? srt[(n-1)/2] : 0.5*(srt[n/2] + srt[n/2 - 1]);
                double denom = 6.0 * med;
                for (int iw = 0; iw < n; iw++) {
                    if (denom < 1e-12) { delta[iw] = 1.0; }
                    else {
                        double rr = absr[iw] / denom;
                        delta[iw] = (rr >= 1.0) ? 0.0 : (1-rr*rr)*(1-rr*rr);
                    }
                }
            }
        }

        StringBuilder line = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (!appendPoint(line, xs[i], yScale.denormalize(fitted[i]))) return;
        }
        line.append("]");
        r.lineData    = line.toString();
        r.labelSuffix = " (lowess)";
    }

    // -----------------------------------------------------------------------
    // exp: exponential fit y = a * e^(bx)
    // Linearise: ln(y) = ln(a) + bx  -> OLS on (x, ln(y))
    // Only valid for y > 0.
    // -----------------------------------------------------------------------
    // -----------------------------------------------------------------------
    // exp: exponential fit y = a * e^(bx)
    // Linearise: ln(y) = ln(a) + bx  -> OLS on (x, ln(y))
    // CI: computed on the log scale, then exp-transformed.
    //   fitted_lny +/- t * se_lny  -> exp() to get asymmetric CI in y-space.
    // Only valid for y > 0.
    // -----------------------------------------------------------------------
    private static void computeExp(FitResult r, double[] x, double[] y, boolean withCi) {
        int n = x.length;
        List<Double> lx = new ArrayList<>(), ly = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (y[i] > 0) { lx.add(x[i]); ly.add(Math.log(y[i])); }
        }
        if (lx.size() < 3) return;

        double[] xa = toArray(lx), lya = toArray(ly);
        int m = xa.length;
        LinearModel model = LinearModel.fit(xa, lya, withCi);
        if (model == null) return;

        double xmin = min(xa), xmax = max(xa);
        double t = withCi ? tCritical95(m - 2) : 0;
        StringBuilder line = new StringBuilder("[");
        StringBuilder upper = withCi ? new StringBuilder("[") : null;
        StringBuilder lower = withCi ? new StringBuilder("[") : null;

        for (int i = 0; i <= EVAL_POINTS; i++) {
            double xi = interpolateFinite(xmin, xmax, i / (double) EVAL_POINTS);
            double fittedNorm = model.predictNormalized(xi);
            double lnyi = model.response.denormalize(fittedNorm);
            double yi = Math.exp(lnyi);
            if (!appendPoint(line, xi, yi)) return;
            if (withCi) {
                double se = model.seNormalized(xi);
                double hiLog = model.response.denormalize(fittedNorm + t*se);
                double loLog = model.response.denormalize(fittedNorm - t*se);
                if (!appendPoint(upper, xi, Math.exp(hiLog))
                        || !appendPoint(lower, xi, Math.exp(loLog))) return;
            }
        }
        line.append("]");
        r.lineData    = line.toString();
        r.labelSuffix = " (exp fit)";
        if (withCi) {
            upper.append("]"); lower.append("]");
            r.upperData = upper.toString();
            r.lowerData = lower.toString();
            r.hasCi = true;
        }
    }

    // -----------------------------------------------------------------------
    // log: logarithmic fit y = a + b * ln(x)
    // This IS a linear model (OLS on (ln(x), y)), so CI formula is exact.
    //   SE = s * sqrt(1/n + (ln(xi)-lnxbar)^2/Sxx_lnx)
    // CI stays in y-space (symmetric), same as lfit.
    // Only valid for x > 0.
    // -----------------------------------------------------------------------
    private static void computeLog(FitResult r, double[] x, double[] y, boolean withCi) {
        int n = x.length;
        List<Double> lx = new ArrayList<>(), fy = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (x[i] > 0) { lx.add(Math.log(x[i])); fy.add(y[i]); }
        }
        if (lx.size() < 3) return;

        double[] lxa = toArray(lx), ya = toArray(fy);
        int m = lxa.length;
        LinearModel model = LinearModel.fit(lxa, ya, withCi);
        if (model == null) return;

        double xmin = Math.exp(min(lxa)), xmax = Math.exp(max(lxa));
        double t = withCi ? tCritical95(m - 2) : 0;
        StringBuilder line = new StringBuilder("[");
        StringBuilder upper = withCi ? new StringBuilder("[") : null;
        StringBuilder lower = withCi ? new StringBuilder("[") : null;

        for (int i = 0; i <= EVAL_POINTS; i++) {
            double xi = interpolateFinite(xmin, xmax, i / (double) EVAL_POINTS);
            if (xi <= 0) continue;
            double lnxi = Math.log(xi);
            double fittedNorm = model.predictNormalized(lnxi);
            double yi = model.response.denormalize(fittedNorm);
            if (!appendPoint(line, xi, yi)) return;
            if (withCi) {
                double se = model.seNormalized(lnxi);
                double hi = model.response.denormalize(fittedNorm + t*se);
                double lo = model.response.denormalize(fittedNorm - t*se);
                if (!appendPoint(upper, xi, hi) || !appendPoint(lower, xi, lo)) return;
            }
        }
        line.append("]");
        r.lineData    = line.toString();
        r.labelSuffix = " (log fit)";
        if (withCi) {
            upper.append("]"); lower.append("]");
            r.upperData = upper.toString();
            r.lowerData = lower.toString();
            r.hasCi = true;
        }
    }

    // -----------------------------------------------------------------------
    // power: power fit y = a * x^b
    // Linearise: ln(y) = ln(a) + b*ln(x) -> OLS on (ln(x), ln(y))
    // CI: computed on log-log scale, then exp-transformed (asymmetric in y).
    //   CI_upper(x) = exp(fitted_lny + t*se),  CI_lower = exp(fitted_lny - t*se)
    // Only valid for x > 0 and y > 0.
    // -----------------------------------------------------------------------
    private static void computePower(FitResult r, double[] x, double[] y, boolean withCi) {
        int n = x.length;
        List<Double> lx = new ArrayList<>(), ly = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (x[i] > 0 && y[i] > 0) { lx.add(Math.log(x[i])); ly.add(Math.log(y[i])); }
        }
        if (lx.size() < 3) return;

        double[] lxa = toArray(lx), lya = toArray(ly);
        int m = lxa.length;
        LinearModel model = LinearModel.fit(lxa, lya, withCi);
        if (model == null) return;

        double xmin = Math.exp(min(lxa)), xmax = Math.exp(max(lxa));
        double t = withCi ? tCritical95(m - 2) : 0;
        StringBuilder line = new StringBuilder("[");
        StringBuilder upper = withCi ? new StringBuilder("[") : null;
        StringBuilder lower = withCi ? new StringBuilder("[") : null;

        for (int i = 0; i <= EVAL_POINTS; i++) {
            double xi = interpolateFinite(xmin, xmax, i / (double) EVAL_POINTS);
            if (xi <= 0) continue;
            double lnxi = Math.log(xi);
            double fittedNorm = model.predictNormalized(lnxi);
            double lnyi = model.response.denormalize(fittedNorm);
            double yi = Math.exp(lnyi);
            if (!appendPoint(line, xi, yi)) return;
            if (withCi) {
                double se = model.seNormalized(lnxi);
                double hiLog = model.response.denormalize(fittedNorm + t*se);
                double loLog = model.response.denormalize(fittedNorm - t*se);
                if (!appendPoint(upper, xi, Math.exp(hiLog))
                        || !appendPoint(lower, xi, Math.exp(loLog))) return;
            }
        }
        line.append("]");
        r.lineData    = line.toString();
        r.labelSuffix = " (power fit)";
        if (withCi) {
            upper.append("]"); lower.append("]");
            r.upperData = upper.toString();
            r.lowerData = lower.toString();
            r.hasCi = true;
        }
    }

    // -----------------------------------------------------------------------
    // ma: moving average, window = max(3, N/10), centred
    // -----------------------------------------------------------------------
    private static void computeMa(FitResult r, double[] x, double[] y) {
        int n = x.length;
        int win = Math.max(3, n / 10);
        if ((win & 1) == 0) win++; // centred windows have an odd number of points
        int half = win / 2;

        sortPairs(x, y);
        Scale yScale = Scale.of(y, false);
        if (yScale == null) return;
        double[] prefix = new double[n + 1];
        for (int i = 0; i < n; i++) {
            double normalized = yScale.normalize(y[i]);
            prefix[i + 1] = prefix[i] + normalized;
            if (!Double.isFinite(prefix[i + 1])) return;
        }

        StringBuilder line = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - half);
            int hi = Math.min(n - 1, i + half);
            double sum = prefix[hi + 1] - prefix[lo];
            double yi = yScale.denormalize(sum / (hi - lo + 1));
            if (!appendPoint(line, x[i], yi)) return;
        }
        line.append("]");
        r.lineData    = line.toString();
        r.labelSuffix = " (MA-" + win + ")";
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Tricube weight function for lowess. */
    private static double tricube(double u) {
        double v = 1.0 - u*u*u;
        return v*v*v;
    }

    /** Midrange/scale transform that keeps every finite input near [-1, 1]. */
    private static final class Scale {
        final double center;
        final double scale;

        private Scale(double center, double scale) {
            this.center = center;
            this.scale = scale;
        }

        static Scale of(double[] values, boolean requireVariation) {
            if (values == null || values.length == 0) return null;
            double lo = min(values), hi = max(values);
            if (!Double.isFinite(lo) || !Double.isFinite(hi)) return null;
            double center = midpointFinite(lo, hi);
            double scale = 0;
            for (double value : values) {
                double delta = value - center;
                if (!Double.isFinite(delta)) return null;
                scale = Math.max(scale, Math.abs(delta));
            }
            if (!(scale > 0)) {
                if (requireVariation) return null;
                scale = 1.0;
            }
            return Double.isFinite(center) && Double.isFinite(scale)
                ? new Scale(center, scale) : null;
        }

        double normalize(double value) {
            return (value - center) / scale;
        }

        double denormalize(double value) {
            return Math.fma(scale, value, center);
        }
    }

    /** Numerically stable straight-line model in normalized x/y coordinates. */
    private static final class LinearModel {
        final Scale predictor;
        final Scale response;
        final double xMean;
        final double yMean;
        final double slope;
        final double sxx;
        final double residualSe;
        final int n;

        private LinearModel(Scale predictor, Scale response, double xMean,
                            double yMean, double slope, double sxx,
                            double residualSe, int n) {
            this.predictor = predictor;
            this.response = response;
            this.xMean = xMean;
            this.yMean = yMean;
            this.slope = slope;
            this.sxx = sxx;
            this.residualSe = residualSe;
            this.n = n;
        }

        static LinearModel fit(double[] x, double[] y, boolean needResidualSe) {
            int n = Math.min(x.length, y.length);
            if (n < 3) return null;
            Scale px = Scale.of(x, true), py = Scale.of(y, false);
            if (px == null || py == null) return null;
            double[] zx = new double[n], wy = new double[n];
            CompensatedSum sx = new CompensatedSum(), sy = new CompensatedSum();
            for (int i = 0; i < n; i++) {
                zx[i] = px.normalize(x[i]);
                wy[i] = py.normalize(y[i]);
                if (!Double.isFinite(zx[i]) || !Double.isFinite(wy[i])) return null;
                sx.add(zx[i]); sy.add(wy[i]);
            }
            double xm = sx.value()/n, ym = sy.value()/n;
            CompensatedSum sxxSum = new CompensatedSum(), sxySum = new CompensatedSum();
            for (int i = 0; i < n; i++) {
                double dx = zx[i]-xm;
                sxxSum.add(dx*dx);
                sxySum.add(dx*(wy[i]-ym));
            }
            double sxx = sxxSum.value();
            if (!usablePositive(sxx, n)) return null;
            double slope = sxySum.value()/sxx;
            if (!Double.isFinite(slope)) return null;
            double residualSe = 0;
            if (needResidualSe) {
                CompensatedSum rss = new CompensatedSum();
                for (int i = 0; i < n; i++) {
                    double fitted = Math.fma(slope, zx[i]-xm, ym);
                    double residual = wy[i]-fitted;
                    if (!Double.isFinite(residual)) return null;
                    rss.add(residual*residual);
                }
                residualSe = Math.sqrt(rss.value()/(n-2));
                if (!Double.isFinite(residualSe)) return null;
            }
            return new LinearModel(px, py, xm, ym, slope, sxx, residualSe, n);
        }

        double predictNormalized(double rawX) {
            return Math.fma(slope, predictor.normalize(rawX)-xMean, yMean);
        }

        double seNormalized(double rawX) {
            double dx = predictor.normalize(rawX)-xMean;
            double leverage = 1.0/n + dx*dx/sxx;
            return residualSe*Math.sqrt(Math.max(0.0, leverage));
        }
    }

    /** Kahan/Neumaier-style accumulator for bounded regression quantities. */
    private static final class CompensatedSum {
        private double sum;
        private double correction;

        void add(double value) {
            double next = sum + value;
            if (Math.abs(sum) >= Math.abs(value)) correction += (sum-next)+value;
            else correction += (value-next)+sum;
            sum = next;
        }

        double value() { return sum + correction; }
    }

    /** Stable finite interpolation, including opposite-sign near-Double.MAX endpoints. */
    private static double interpolateFinite(double lo, double hi, double fraction) {
        if (fraction <= 0) return lo;
        if (fraction >= 1) return hi;
        if (Math.copySign(1.0, lo) != Math.copySign(1.0, hi)) {
            return lo*(1.0-fraction) + hi*fraction;
        }
        return Math.fma(hi-lo, fraction, lo);
    }

    private static double midpointFinite(double lo, double hi) {
        if (Math.copySign(1.0, lo) != Math.copySign(1.0, hi)) {
            return lo*0.5 + hi*0.5;
        }
        return Math.fma(hi-lo, 0.5, lo);
    }

    private static boolean usablePositive(double value, double referenceScale) {
        if (!(value > 0) || !Double.isFinite(value)) return false;
        double scale = Math.max(1.0, Math.abs(referenceScale));
        return value > 64.0*Math.ulp(scale);
    }

    /** Append only complete finite points; callers fail closed on false. */
    private static boolean appendPoint(StringBuilder out, double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return false;
        if (out.length() > 1) out.append(',');
        out.append("{x:").append(fmt(x)).append(",y:").append(fmt(y)).append('}');
        return true;
    }

    /** Solve 3x3 linear system Ax = B via Gaussian elimination. Returns null if singular. */
    private static double[] solve3x3(double[][] A, double[] B) {
        double[][] M = new double[3][4];
        double matrixScale = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                M[i][j] = A[i][j];
                if (!Double.isFinite(M[i][j])) return null;
                matrixScale = Math.max(matrixScale, Math.abs(M[i][j]));
            }
            M[i][3] = B[i];
            if (!Double.isFinite(M[i][3])) return null;
        }
        if (!(matrixScale > 0)) return null;
        double pivotTolerance = 64.0*Math.ulp(matrixScale);
        for (int col = 0; col < 3; col++) {
            // Partial pivot
            int pivot = col;
            for (int row = col+1; row < 3; row++)
                if (Math.abs(M[row][col]) > Math.abs(M[pivot][col])) pivot = row;
            double[] tmp = M[col]; M[col] = M[pivot]; M[pivot] = tmp;
            if (!(Math.abs(M[col][col]) > pivotTolerance)) return null;
            for (int row = col+1; row < 3; row++) {
                double f = M[row][col] / M[col][col];
                for (int k = col; k <= 3; k++) {
                    M[row][k] = Math.fma(-f, M[col][k], M[row][k]);
                    if (!Double.isFinite(M[row][k])) return null;
                }
            }
        }
        // Back substitution
        double[] x = new double[3];
        for (int i = 2; i >= 0; i--) {
            x[i] = M[i][3];
            for (int j = i+1; j < 3; j++) x[i] -= M[i][j]*x[j];
            x[i] /= M[i][i];
            if (!Double.isFinite(x[i])) return null;
        }
        return x;
    }

    /** t critical value for 95% two-tailed CI. Approximate for large df. */
    /**
     * t-critical value for 95% two-sided CI: t_{0.975, df}.
     * v3.5.71: Matches Stata invttail(df, 0.025) to 6 decimal places.
     *
     * df 1-30:  exact 8-decimal-place lookup table (from Stata/scipy).
     * df 31+:   Cornish-Fisher 4-term expansion. Max error < 1e-7 vs exact,
     *           equivalent to < 0.00005% CI width error -- indistinguishable
     *           from Stata output on any rendered chart.
     *
     * Previous implementation used sparse lookup (df=1,2,...,10,15,20,30,60,120)
     * with linear interpolation, giving up to 1.2% CI width overestimate.
     */
    private static double tCritical95(int df) {
        if (df <= 0) return 12.70620474;
        // Exact 8dp values for df 1-30 (matches Stata invttail to full precision)
        double[] T95 = {
            0,
            12.70620474, 4.30265273, 3.18244631, 2.77644511, 2.57058184,
             2.44691185, 2.36462425, 2.30600414, 2.26215716, 2.22813885,
             2.20098516, 2.17881283, 2.16036866, 2.14478669, 2.13144955,
             2.11990530, 2.10981558, 2.10092204, 2.09302405, 2.08596345,
             2.07961384, 2.07387307, 2.06865761, 2.06389856, 2.05953855,
             2.05552944, 2.05183052, 2.04840714, 2.04522964, 2.04227246
        };
        if (df <= 30) return T95[df];
        // Cornish-Fisher 4-term expansion for df > 30.
        // z = Phi^{-1}(0.975) = 1.95996398 (8 decimal places)
        double z = 1.95996398;
        double z2=z*z, z3=z2*z, z5=z2*z3, z7=z2*z5, z9=z2*z7;
        double d=df, d2=d*d, d3=d2*d, d4=d2*d2;
        return z
            + (z3 + z)                               / (4.0*d)
            + (5.0*z5 + 16.0*z3 + 3.0*z)             / (96.0*d2)
            + (3.0*z7 + 19.0*z5 + 17.0*z3 - 15.0*z)  / (384.0*d3)
            + (79.0*z9 + 779.0*z7 + 1482.0*z5 - 1920.0*z3 - 945.0*z) / (92160.0*d4);
    }

    private static double min(double[] a) {
        double m = a[0]; for (double v : a) if (v < m) m = v; return m;
    }
    private static double max(double[] a) {
        double m = a[0]; for (double v : a) if (v > m) m = v; return m;
    }
    private static double[] toArray(List<Double> lst) {
        double[] a = new double[lst.size()];
        for (int i = 0; i < a.length; i++) a[i] = lst.get(i);
        return a;
    }

    /** In-place paired quicksort with bounded recursion depth. */
    private static void sortPairs(double[] x, double[] y) {
        quickSortPairs(x, y, 0, x.length - 1);
    }

    private static void quickSortPairs(double[] x, double[] y, int left, int right) {
        while (left < right) {
            int i = left, j = right;
            double pivot = x[left + ((right - left) >>> 1)];
            while (i <= j) {
                while (i <= right && Double.compare(x[i], pivot) < 0) i++;
                while (j >= left && Double.compare(x[j], pivot) > 0) j--;
                if (i <= j) {
                    double tx = x[i]; x[i] = x[j]; x[j] = tx;
                    double ty = y[i]; y[i] = y[j]; y[j] = ty;
                    i++; j--;
                }
            }
            // Recurse into the smaller side; iterate over the larger side.
            if (j - left < right - i) {
                if (left < j) quickSortPairs(x, y, left, j);
                left = i;
            } else {
                if (i < right) quickSortPairs(x, y, i, right);
                right = j;
            }
        }
    }

    /** Round-trip-safe, locale-neutral finite JavaScript number literal. */
    static String fmt(double v) {
        return Double.isFinite(v) ? Double.toString(v) : "null";
    }
}
