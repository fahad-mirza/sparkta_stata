package com.stata.sfi;
/** Minimal stub of Stata SFI for offline compile-checking only. NOT the real API. */
public final class SFIToolkit {
    public static final int RC_GENERAL_ERROR = 198;
    /** fix9g: SPK_STUB_ECHO=1 echoes Stata-side notes so a harness run can show them. */
    public static void displayln(String s) { if (System.getenv("SPK_STUB_ECHO") != null) System.out.println("[SFI] " + s); }
    public static void display(String s) {}
    public static void error(String s) { System.out.println("[SFI.error] " + s.trim()); }
    public static void errorln(String s) {}
    public static void pollnow() {}
}
