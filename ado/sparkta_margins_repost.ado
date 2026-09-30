*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: re-posts kept margins results as r() for sparkta_read_margins -- internal
*! t2g: new (rclass on purpose: this is the one place that is allowed to set r())

// =============================================================================
// sparkta_margins_repost  (v3.6.0-t2g)
//   sparkta_margins_repost, [hasvs hasat plab() expr() cmdl()]
// Posts _spk_mg_table (-> r(table)), _spk_mg_vs (-> r(table_vs)), _spk_mg_at
// (-> r(at)) and the three labels, so sparkta_read_margins runs unchanged
// whether the results came from margins in memory, from a snapshot taken
// before r() was disturbed, or from results() (a margins, saving() file).
// return matrix MOVES the source matrix, so the fixed names are consumed here.
// =============================================================================
program sparkta_margins_repost, rclass
    version 17
    syntax , [HASVS HASAT PLAB(string asis) EXPR(string asis) CMDL(string asis)]
    return matrix table = _spk_mg_table
    if "`hasvs'" != "" return matrix table_vs = _spk_mg_vs
    if "`hasat'" != "" return matrix at = _spk_mg_at
    // defensive: strip one layer of compound quotes if a caller wrapped the value
    foreach _v in plab expr cmdl {
        if substr(`"``_v''"', 1, 2) == `"`"'"' & substr(`"``_v''"', -2, .) == `"'"'"' {
            local `_v' = substr(`"``_v''"', 3, strlen(`"``_v''"') - 4)
        }
    }
    return local predict1_label `"`plab'"'
    return local expression `"`expr'"'
    return local cmdline `"`cmdl'"'
end
