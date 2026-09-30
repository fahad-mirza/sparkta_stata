package com.dashboard_test.tools;

import com.dashboard_test.util.FileUtil;

/**
 * FileCopy -- atomic replacement helper for completed browser export files.
 * t2j fix4 (ported from Astra rc1). javacall entry point:
 *   javacall com.dashboard_test.tools.FileCopy execute, classpath(jar) args(source dest)
 * Copies a finished export (PDF/PNG/SVG the headless browser wrote to a Stata tempfile)
 * onto the user's destination via a sibling temp file plus an atomic move, refusing a
 * missing or empty source. This lets saveas() keep the prior file intact when an export
 * fails, instead of erasing the destination up front. Returns 0 ok, 1 usage, 2 failure.
 */
public final class FileCopy {
    private FileCopy() {}

    public static int execute(String[] args) {
        if (args == null || args.length != 2) {
            HeadlessBrowser.say("FileCopy: need <source> <destination>");
            return 1;
        }
        try {
            FileUtil.copyFileAtomically(args[0], args[1]);
            return 0;
        } catch (Exception ex) {
            HeadlessBrowser.say("FileCopy: " + ex.getClass().getSimpleName()
                + ": " + ex.getMessage());
            return 2;
        }
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }
}
