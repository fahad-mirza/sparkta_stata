*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: stable sort of matrix rows by event time -- internal, installed with sparkta; not a user command
*! split out of sparkta.ado in v3.6.0-t2f (Stage 1 of the file split) -- logic unchanged

// Orders the pipe-lists of sparkta_read_matrix by parsed event time. Items whose
// time is missing keep their relative order and come first; summaries (9998/9999)
// come last. sreturns the reordered lists.
program sparkta_matrix_sort_time, sclass
    version 17          // v3.6.0: SSC requires an explicit version statement in every ado
    args times names coefs lower upper ses pvals tzvals lower2 upper2
    local n : word count `=subinstr("`times'", "|", " ", .)'
    // build an index order: stable sort on time with missing -> -1e9 keeps first
    local keys ""
    forvalues i = 1/`n' {
        local t : word `i' of `=subinstr("`times'", "|", " ", .)'
        if "`t'" == "." local t -1000000000
        local keys "`keys' `t':`i'"
    }
    // simple selection sort (n is small)
    local order ""
    local remaining "`keys'"
    while "`remaining'" != "" {
        local best ""
        local bestv .
        foreach k of local remaining {
            local v = real(substr("`k'", 1, strpos("`k'", ":") - 1))
            if "`best'" == "" | `v' < `bestv' {
                local best "`k'"
                local bestv `v'
            }
        }
        local order "`order' `=substr("`best'", strpos("`best'", ":") + 1, .)'"
        local remaining : list remaining - best
    }
    foreach f in times names coefs lower upper ses pvals tzvals lower2 upper2 {
        local src "``f''"
        local out ""
        if "`src'" == "" {
            sreturn local `f' ""
            continue
        }
        // preserve EMPTY tokens (a coefficient without SE/p): mark them with char(2)
        local src2 = subinstr("`src'", "||", "|" + char(2) + "|", .)
        local src2 = subinstr("`src2'", "||", "|" + char(2) + "|", .)
        if substr("`src2'", 1, 1) == "|" local src2 = char(2) + "`src2'"
        if substr("`src2'", -1, 1) == "|" local src2 = "`src2'" + char(2)
        local src2 = subinstr("`src2'", " ", char(1), .)
        local src2 = subinstr("`src2'", "|", " ", .)
        local k 0
        foreach i of local order {
            local ++k
            local tok : word `i' of `src2'
            local tok = subinstr("`tok'", char(1), " ", .)
            local tok = subinstr("`tok'", char(2), "", .)
            if `k' == 1 local out "`tok'"
            else        local out "`out'|`tok'"
        }
        sreturn local `f' "`out'"
    }
end
