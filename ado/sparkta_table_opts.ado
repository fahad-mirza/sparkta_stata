*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: publication-table options (values in, c_local back) -- internal; not a user command
*! t2h batch 2a: NEW -- parses/validates stars()/nostars/tstat/ci/nofooter/notable, packs arg 209

// =============================================================================
// sparkta_table_opts  (v3.6.0-t2h, batch 2a)
//   sparkta_table_opts [, stars(numlist) nostars tstat ci nofooter notable]
// Validates the publication-table options and returns a single packed string to
// the CALLER via c_local _tbl_opts, in the exact shape DashboardBuilder.
// parseTableOpts() reads (pipe-sep key=value, order-independent):
//   stars=0.10 0.05 0.01|nostars=0|tstat=0|ci=0|nofooter=0|notable=0
// The caller must declare _tbl_opts ONCE before this call and never re-init it
// afterwards (c_local memory rule). ASCII only; no compound quotes on the pack.
// =============================================================================
program sparkta_table_opts
    version 17
    // stars(): up to three descending thresholds in (0,1), estout's default is
    // 0.10 0.05 0.01 (also sparkta's existing star legend). tstat/interval pick
    // what sits beneath the estimate (default is the SE). nostars, nofooter,
    // notable are plain toggles. The "show CI beneath" toggle is spelled
    // INTerval, not ci: ci() is already the matrix-mode option in sparkta's main
    // syntax, so a bare ci flag would collide. The packed key stays "ci" for
    // DashboardBuilder.parseTableOpts().
    syntax [, STARs(numlist max=3 >0 <1) NOSTARs TStat INTerval NOFOoter NOTABle]

    // -- stars: keep the user's numbers if given, else the estout default; then
    //    sort DESCENDING so slot 1 = * (most lenient), slot 3 = *** (strictest).
    local _starstr "0.10 0.05 0.01"
    if "`stars'" != "" {
        // numlist already validated >0 <1 and max=3 by syntax; sort descending
        numlist "`stars'", sort
        local _sorted "`r(numlist)'"
        local _rev ""
        foreach _v of local _sorted {
            local _rev "`_v' `_rev'"
        }
        local _starstr = strtrim("`_rev'")
    }

    // -- flags: present -> 1, absent -> 0
    local _ns = cond("`nostars'"  != "", "1", "0")
    local _ts = cond("`tstat'"    != "", "1", "0")
    local _ci = cond("`interval'" != "", "1", "0")
    local _nf = cond("`nofooter'" != "", "1", "0")
    local _nt = cond("`notable'"  != "", "1", "0")

    // -- pack: bare values (no compound quotes -- the receiving javacall arg is
    //    a plain string; wrapping would arrive with literal quotes, the fix-5
    //    lesson recorded twice in project_memory.md).
    c_local _tbl_opts "stars=`_starstr'|nostars=`_ns'|tstat=`_ts'|ci=`_ci'|nofooter=`_nf'|notable=`_nt'"
end
