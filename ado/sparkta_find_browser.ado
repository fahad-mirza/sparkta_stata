*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: locates a Chromium-based browser for saveas() -- internal, installed with sparkta; not a user command
*! split out of sparkta.ado in v3.6.0-t2f (Stage 1 of the file split) -- logic unchanged

// sparkta_check removed -- not distributed

// =============================================================================
// SUBROUTINE: sparkta_find_browser (v3.6.0-s9p)
// Returns r(browser) = path of a Chromium-based browser, or "" if none.
// Order: global SPARKTA_BROWSER, then Edge / Chrome in their default locations.
// =============================================================================
program sparkta_find_browser, rclass
    version 17          // v3.6.0: SSC requires an explicit version statement in every ado
    syntax [, Verbose]
    if "$SPARKTA_BROWSER" != "" {
        return local browser "$SPARKTA_BROWSER"
        exit
    }
    local cands ""
    if "`c(os)'" == "Windows" {
        capture local pf   : env ProgramFiles
        capture local pf86 : env ProgramFiles(x86)
        capture local lad  : env LOCALAPPDATA
        if "`pf'"   == "" local pf   "C:\Program Files"
        if "`pf86'" == "" local pf86 "C:\Program Files (x86)"
        local cands `"`cands' "`pf86'\Microsoft\Edge\Application\msedge.exe""'
        local cands `"`cands' "`pf'\Microsoft\Edge\Application\msedge.exe""'
        local cands `"`cands' "`lad'\Microsoft\Edge\Application\msedge.exe""'
        local cands `"`cands' "`pf'\Google\Chrome\Application\chrome.exe""'
        local cands `"`cands' "`pf86'\Google\Chrome\Application\chrome.exe""'
        local cands `"`cands' "`lad'\Google\Chrome\Application\chrome.exe""'
    }
    else if "`c(os)'" == "MacOSX" {
        local cands `"`cands' "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome""'
        local cands `"`cands' "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge""'
        local cands `"`cands' "/Applications/Chromium.app/Contents/MacOS/Chromium""'
    }
    else {
        local cands `"`cands' "/usr/bin/google-chrome" "/usr/bin/chromium" "/usr/bin/chromium-browser" "/usr/bin/microsoft-edge" "/snap/bin/chromium""'
    }
    local found ""
    foreach c of local cands {
        capture confirm file `"`c'"'
        if "`verbose'" != "" display as text `"  browser candidate: `c' -> "' cond(_rc == 0, "found", "no")
        if _rc == 0 & "`found'" == "" local found `"`c'"'
    }
    if "`found'" == "" & "`c(os)'" == "Windows" {
        // last resort: ask Windows where msedge / chrome live (works when they are on PATH)
        tempfile wh
        capture shell where msedge.exe chrome.exe > "`wh'" 2>nul
        capture file open _spkwh using `"`wh'"', read text
        if _rc == 0 {
            file read _spkwh line
            if !r(eof) & `"`line'"' != "" local found `"`line'"'
            file close _spkwh
        }
        if "`verbose'" != "" display as text `"  where msedge/chrome -> "' cond("`found'" == "", "nothing", `"`found'"')
    }
    return local browser `"`found'"'
end
