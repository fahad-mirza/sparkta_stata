*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: value label for <var> <value> on marginsplot pages -- internal; not a user command
*! t2j fix8z: new. Results() source labels ($SPK_MG_VLABS / $SPK_MG_VARLABS, set by sparkta_read_results_margins) first,
*!   then the variable in memory. Own file so Stata auto-loads it wherever it is called from.

// t2j fix8z: value label for <var> <value>: the results() source's labels first (global
// SPK_MG_VLABS = "var|value|text|var|value|text..."), then the variable in memory. c_local _mgvl
// ("" when none). Every label lookup in this file goes through here so results() pages read the
// same whatever dataset is loaded.
program sparkta_mg_vlabel
    version 17          // v3.6.0: SSC requires an explicit version statement in every ado
    args var val
    local _out ""
    if `"$SPK_MG_VLABS"' != "" {
        tokenize `"$SPK_MG_VLABS"', parse("|")
        local _i 1
        while "``_i''" != "" {
            local _v "``_i''"
            local _i = `_i' + 2
            local _l "``_i''"
            local _i = `_i' + 2
            local _t "``_i''"
            local _i = `_i' + 2
            if "`_v'" == "`var'" & "`_l'" == "`val'" {
                local _out "`_t'"
                continue, break
            }
        }
    }
    if "`_out'" == "" {
        capture local _out : label (`var') `val'
        if _rc != 0 local _out ""
    }
    c_local _mgvl `"`_out'"'
end
