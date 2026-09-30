package com.dashboard_test;

import com.dashboard_test.html.HtmlGenerator;
import com.stata.sfi.SFIToolkit;

/**
 * VersionCheck -- verifies the loaded ado and the loaded Java backend belong to the
 * same sparkta release. t2j fix4 (ported from Astra rc1).
 *
 * Why this exists: Stata keeps a single long-lived JVM per session. If the package
 * files are replaced on disk but the JVM has already loaded an older HtmlGenerator,
 * rendering silently uses the stale backend. Reading the version reflectively (not as
 * an inlined compile-time constant) reads it from the actually-loaded class, so a
 * cached/mismatched jar is caught before any chart is built.
 *
 * javacall entry point:
 *   javacall com.dashboard_test.VersionCheck execute, classpath(jar) args("&lt;ado version&gt;")
 * Returns 0 when the versions match, RC_GENERAL_ERROR (198) otherwise.
 */
public final class VersionCheck {
    private VersionCheck() {}

    public static int execute(String[] args) {
        if (args == null || args.length != 1 || args[0] == null || args[0].trim().isEmpty()) {
            SFIToolkit.error("sparkta: internal version check failed; reinstall the complete package.\n");
            return SFIToolkit.RC_GENERAL_ERROR;
        }
        String expected = args[0].trim();
        String actual = loadedBackendVersion();
        if (actual == null || actual.trim().isEmpty()) {
            SFIToolkit.error("sparkta: cannot read the loaded JAR version; reinstall all "
                + "sparkta files together, then restart Stata.\n");
            return SFIToolkit.RC_GENERAL_ERROR;
        }
        if (!actual.equals(expected)) {
            SFIToolkit.error("sparkta: ado/JAR version mismatch (ado " + expected
                + ", JAR " + actual + "). Reinstall all sparkta files together, "
                + "then restart Stata.\n");
            return SFIToolkit.RC_GENERAL_ERROR;
        }
        return 0;
    }

    /**
     * Read the field reflectively so javac cannot inline the String constant. That
     * matters when Stata's long-lived JVM still holds an older HtmlGenerator class
     * after the package files were replaced on disk.
     */
    static String loadedBackendVersion() {
        try {
            Object value = HtmlGenerator.class.getField("VERSION").get(null);
            return value instanceof String ? (String) value : null;
        } catch (ReflectiveOperationException | LinkageError | SecurityException failure) {
            return null;
        }
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }
}
