*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: runs the headless browser without a console window -- internal, installed with sparkta; not a user command
*! split out of sparkta.ado in v3.6.0-t2f (Stage 1 of the file split) -- logic unchanged

// =============================================================================
// SUBROUTINE: sparkta_browser_run (v3.6.0-t1c)
//   sparkta_browser_run "jar" "outfile-or-empty" "exe" arg arg ...
// Runs the browser through Java's ProcessBuilder: no console window, no cmd
// quoting -- every token is passed as its own argument. Output to a file when
// the second argument is non-empty (used for --dump-dom).
// =============================================================================
program sparkta_browser_run
    version 17          // v3.6.0: SSC requires an explicit version statement in every ado
    gettoken jar 0 : 0
    gettoken outf 0 : 0
    if `"`outf'"' != "" {
        capture noisily javacall com.dashboard_test.tools.HeadlessBrowser execute, classpath(`"`jar'"') args("runcapture" `"`outf'"' `0')
    }
    else {
        capture noisily javacall com.dashboard_test.tools.HeadlessBrowser execute, classpath(`"`jar'"') args("run" `0')
    }
    // t2j fix4: propagate the browser's return code so callers (saveas) can detect a
    // failed export instead of silently treating it as success. Was swallowed before.
    exit _rc
end
