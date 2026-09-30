*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: parses coefficient names to relative event times -- internal, installed with sparkta; not a user command
*! split out of sparkta.ado in v3.6.0-t2f (Stage 1 of the file split) -- logic unchanged

// =============================================================================
// SUBROUTINE: sparkta_event_times (v3.6.0-t2d)
//   Parses coefficient names into relative times for the numeric event-study
//   axis. Input: the pipe/tilde-separated display names. sreturns s(times) in the
//   same layout, s(ok)=1 when every name parsed (summaries Pre_avg/Post_avg count
//   as parsed: 9998/9999), s(bad) = the first name that did not.
// =============================================================================
program sparkta_event_times, sclass
    version 17          // v3.6.0: SSC requires an explicit version statement in every ado
    args names
    local out ""
    local ok 1
    local bad ""
    local rest "`names'"
    local first 1
    while "`rest'" != "" {
        gettoken tok rest : rest, parse("|~")
        if "`tok'" == "|" | "`tok'" == "~" {
            local out "`out'`tok'"
            continue
        }
        local lc = lower(trim("`tok'"))
        local tm .
        if      regexm("`lc'", "^t$")                       local tm 0
        else if regexm("`lc'", "^t[-]([0-9]+)$")            local tm = -real(regexs(1))
        else if regexm("`lc'", "^t\+([0-9]+)$")             local tm =  real(regexs(1))
        else if regexm("`lc'", "^t?m([0-9]+)$")             local tm = -real(regexs(1))
        else if regexm("`lc'", "^t?p([0-9]+)$")             local tm =  real(regexs(1))
        else if regexm("`lc'", "^lead_?([0-9]+)$")          local tm = -real(regexs(1))
        else if regexm("`lc'", "^lag_?([0-9]+)$")           local tm =  real(regexs(1))
        else if regexm("`lc'", "^(-?[0-9]+)b?n?o?\.")       local tm =  real(regexs(1))
        else if regexm("`lc'", "^(pre|post)_?avg")          local tm = cond(regexs(1) == "pre", 9998, 9999)
        else if regexm("`lc'", "^(-?[0-9]+)$")              local tm =  real(regexs(1))
        else if regexm("`lc'", "=(-?[0-9]+)$")              local tm =  real(regexs(1))
        if `tm' >= . & `tm' != 9998 & `tm' != 9999 {
            local ok 0
            if "`bad'" == "" local bad "`tok'"
        }
        local out "`out'`tm'"
    }
    sreturn local times "`out'"
    sreturn local ok "`ok'"
    sreturn local bad "`bad'"
end
