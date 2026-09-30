*! sparkta version 3.6.0 2026-09-13
*! t2j fix8z: clears the results() label maps ($SPK_MG_VLABS, $SPK_MG_VARLABS) at the start of every call
*! component of sparkta: snapshots margins results (r(table), r(table_vs), r(at), labels) -- internal
*! t2g fix 1: confirm matrix before copying r(table_vs)/r(at) (a missing r() matrix copies as a 1x1 missing)
*! t2g: new. r() does not survive sparkta's own r-class calls (palette(), colour normaliser, results()),
*!      so the margins results are kept here right after syntax and re-posted by sparkta_margins_repost.

// =============================================================================
// sparkta_margins_keep  (v3.6.0-t2g)
//   sparkta_margins_keep
// Copies the margins results in memory into fixed-name matrices and returns
// their presence + the r() labels the reader uses via c_local:
//   _mg_have (0/1)  _mg_hasvs  _mg_hasat  _mg_plab  _mg_expr  _mg_cmdl
// Matrices: _spk_mg_table  _spk_mg_vs  _spk_mg_at   (moved by the repost)
// =============================================================================
program sparkta_margins_keep
    version 17
    // fix8z: the results() source label maps are per call
    global SPK_MG_VLABS ""
    global SPK_MG_VARLABS ""
    foreach _m in _spk_mg_table _spk_mg_vs _spk_mg_at {
        capture matrix drop `_m'
    }
    c_local _mg_have 0
    c_local _mg_hasvs 0
    c_local _mg_hasat 0
    capture confirm matrix r(table)
    if _rc != 0 exit
    matrix _spk_mg_table = r(table)
    c_local _mg_have 1
    // fix (run 49): "matrix X = r(name)" does NOT error when r(name) is absent -- the reference
    // evaluates as a missing SCALAR and a 1x1 matrix of . is created. confirm first, always.
    capture confirm matrix r(table_vs)
    if _rc == 0 {
        matrix _spk_mg_vs = r(table_vs)
        c_local _mg_hasvs 1
    }
    capture confirm matrix r(at)
    if _rc == 0 {
        matrix _spk_mg_at = r(at)
        c_local _mg_hasat 1
    }
    c_local _mg_plab `"`r(predict1_label)'"'
    c_local _mg_expr `"`r(expression)'"'
    c_local _mg_cmdl `"`r(cmdline)'"'
end
