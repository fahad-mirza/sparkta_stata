*! test_netinstall_v360.do -- sparkta v3.6.0 clean net install check (roadmap 4a), 2026-09-14, rev 3
*! Purpose: prove that `net install` from the GitHub ado/ folder delivers all 24 files, that the
*!          jar is found on the adopath, that the ado/JAR version handshake passes, and that one
*!          data chart, one post-estimation chart and one saveas() export run -- on a machine
*!          that has never seen sparkta.
*! rev 2: NO second machine needed. With CLEAN = 1 (default) the do-file makes THIS Stata
*!        session look like a fresh one: PLUS and PERSONAL are redirected to an empty temp folder
*!        for the session only (sysdir set is not saved; a restart puts them back), so the dev
*!        copies in PERSONAL (sparkta.ado, the jar, sparkta-export.jar) are invisible and the
*!        exports take the same session-browser route an SSC user gets. Case 0 proves the
*!        isolation: `which sparkta` must FAIL before the install.
*! Run AFTER the v3.6.0 push (git tag v3.5.111 first, then main), from a FRESHLY STARTED Stata
*! (the JVM keeps a loaded jar for the session) started in a folder with no sparkta files.
*! Expected: 9/9 pass. Output folder: test_netinstall_out\ next to this do-file.
*! Set FROM to the v3.5.111 tag URL to prove the frozen old release still installs.
*! rev 3: Fahad's first run (2026-09-14, BEFORE the push) proved the isolation and the install
*!        mechanics but installed v3.5.111 from main (4/9: 21 helper ados missing, header 3.5.111,
*!        levels()/saveas() unknown) -- exactly what "not pushed yet" looks like. Case 3 now says so
*!        in words; the temp path no longer carries a doubled separator (c(tmpdir) ends in one).
*!        Note: 3.5.111 wrote its export next to Stata's Java runtime (relative paths were resolved
*!        against the JVM's working directory) -- 3.6.0 passes c(pwd) to Java (arg 189) instead.

version 17
clear all
set more off

local FROM "https://raw.githubusercontent.com/fahad-mirza/sparkta_stata/main/ado/"
local CLEAN = 1                       // 1 = simulate a machine without sparkta (rev 2)
capture mkdir test_netinstall_out
local out "test_netinstall_out"
local pass = 0
local fail = 0

// 0. isolate this session: empty PLUS and PERSONAL, so nothing installed before is visible
if `CLEAN' {
    local stamp = subinstr("`c(current_time)'", ":", "", .)
    local tmp = subinstr("`c(tmpdir)'", "\", "/", .)
    if substr("`tmp'", -1, 1) == "/" local tmp = substr("`tmp'", 1, length("`tmp'") - 1)
    local base "`tmp'/sparkta_clean_`stamp'"
    capture mkdir "`base'"
    capture mkdir "`base'/plus"
    capture mkdir "`base'/personal"
    sysdir set PLUS "`base'/plus/"
    sysdir set PERSONAL "`base'/personal/"
    di as txt "PLUS and PERSONAL redirected for this session to `base'"
    sysdir
    capture which sparkta
    if _rc {
        local ++pass
        di as txt "isolation OK: sparkta is not visible before the install"
    }
    else {
        local ++fail
        di as error "sparkta is still visible (`r(fn)') -- start Stata in a folder without sparkta files"
    }
}

// 1. install (replace: harmless on a clean machine)
capture noisily net install sparkta, from("`FROM'") replace
if _rc == 0 local ++pass
else {
    local ++fail
    di as error "net install failed (rc = " _rc ")"
}

// 2. every file the .pkg lists is on the adopath: 22 ado, the help, the jar
local nmiss = 0
foreach f in sparkta.ado sparkta.sthlp sparkta.jar sparkta_browser_run.ado ///
    sparkta_color_list.ado sparkta_color_norm.ado sparkta_color_opts.ado sparkta_es_opts.ado ///
    sparkta_event_times.ado sparkta_find_browser.ado sparkta_margins_keep.ado ///
    sparkta_margins_repost.ado sparkta_matrix_guards.ado sparkta_matrix_sort_time.ado ///
    sparkta_mg_varlabel.ado sparkta_mg_vlabel.ado sparkta_parse_coeflabels.ado ///
    sparkta_parse_tblrows.ado sparkta_read_eresults.ado sparkta_read_margins.ado ///
    sparkta_read_matrix.ado sparkta_read_results.ado sparkta_read_results_margins.ado ///
    sparkta_table_opts.ado {
    capture findfile `f'
    if _rc {
        local ++nmiss
        di as error "  missing on the adopath: `f'"
    }
}
if `nmiss' == 0 local ++pass
else local ++fail
di as txt "files missing: `nmiss' of 24"

// 3. the installed main file is 3.6.0 (header line 1) -- read it by eye too
which sparkta
capture findfile sparkta.ado
tempname fh
file open `fh' using "`r(fn)'", read text
file read `fh' line1
file close `fh'
if strpos(`"`line1'"', "sparkta version 3.6.0") local ++pass
else {
    local ++fail
    di as error `"header line 1 is: `line1'"'
    if strpos(`"`line1'"', "3.5.111") di as error "  -> GitHub main still serves v3.5.111: the v3.6.0 tree has not been pushed yet (see GITHUB_UPDATE_v3.6.0.md)"
}

// 4. the jar lives next to the ado (net install put it in PLUS/s/)
capture findfile sparkta.jar
if _rc == 0 {
    local ++pass
    di as txt "jar: `r(fn)'"
}
else local ++fail

// 5. data chart = VersionCheck handshake (ado 3.6.0 vs JAR 3.6.0); look for "[sparkta v3.6.0]"
//    and NO "version mismatch" line in the output
sysuse auto, clear
capture noisily sparkta price, type(cibar) over(rep78) export("`out'/n_cibar.html")
if _rc == 0 & fileexists("`out'/n_cibar.html") local ++pass
else local ++fail

// 6. a post-estimation chart (exercises the helper ados: readers, table options)
regress price mpg weight foreign
capture noisily sparkta, type(coefplot) levels(90 95) export("`out'/n_coef.html")
if _rc == 0 & fileexists("`out'/n_coef.html") local ++pass
else local ++fail

// 7. saveas() PNG through the session browser (needs Edge or Chrome)
capture noisily sparkta, type(coefplot) export("`out'/n_coef2.html") saveas("`out'/n_coef.png", close)
if _rc == 0 & fileexists("`out'/n_coef.png") local ++pass
else local ++fail

// 8. help file opens (SMCL parses)
capture noisily help sparkta
if _rc == 0 local ++pass
else local ++fail

di as txt _n "test_netinstall_v360: `pass' passed, `fail' failed (expect 9/9 with CLEAN = 1, 8/8 otherwise)"
di as txt "Also check by eye: the Stata output shows [sparkta v3.6.0] once and no version-mismatch message."
if `CLEAN' di as txt "PLUS/PERSONAL return to normal when you restart Stata (sysdir set is per session)."
