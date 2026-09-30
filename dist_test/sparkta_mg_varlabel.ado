*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: variable label for <var> on marginsplot pages -- internal; not a user command
*! t2j fix8z: new. Results() source labels ($SPK_MG_VLABS / $SPK_MG_VARLABS, set by sparkta_read_results_margins) first,
*!   then the variable in memory. Own file so Stata auto-loads it wherever it is called from.

// t2j fix8z: variable label for <var>: the results() source first ($SPK_MG_VARLABS = "var|text|..."),
// then the variable in memory. c_local _mgvarl ("" when none).
program sparkta_mg_varlabel
    version 17          // v3.6.0: SSC requires an explicit version statement in every ado
    args var
    local _out ""
    if `"$SPK_MG_VARLABS"' != "" {
        tokenize `"$SPK_MG_VARLABS"', parse("|")
        local _i 1
        while "``_i''" != "" {
            local _v "``_i''"
            local _i = `_i' + 2
            local _t "``_i''"
            local _i = `_i' + 2
            if "`_v'" == "`var'" {
                local _out "`_t'"
                continue, break
            }
        }
    }
    if "`_out'" == "" {
        capture confirm variable `var', exact
        if _rc == 0 {
            local _out : variable label `var'
        }
    }
    c_local _mgvarl `"`_out'"'
end
