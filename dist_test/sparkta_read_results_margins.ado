*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: results() for marginsplot -- a margins, saving() dataset -> kept margins results -- internal
*! t2j fix8z: mapped tables drop rows whose factor/at value is missing (collapse's rep78==. group drew as "r6"),
*!   are completed to a full at x factor grid (fillin) and sorted at-major -- the downstream reader maps cells by
*!   position and a sparse table shifted a whole series one condition left; the source's value and
*!   variable labels travel with the table ($SPK_MG_VLABS / $SPK_MG_VARLABS);
*!   at() mapping ROOT CAUSE: `capture drop _at` abbreviated to _at1 and dropped it -- all drops exact-name now
*! t2j fix8y: keyword autodetect matches variable names EXACTLY (confirm variable, exact) -- "sd" no longer
*!   picks up "sdm"; the diagnostic line names the column actually taken
*! t2j fix8x: column MAPPING. b() se() ci(lo hi) pvalue() at(varlist) factors(varlist) clone the user's own
*!   columns onto _margin/_se_margin/_ci_lb/_ci_ub/_pvalue/_at#/_m# (so any tidy table plots); when the
*!   _margin column is absent and b() is not given the statistic columns are autodetected from the
*!   matrix()/results() keyword lists (kwb() kwse() kwll() kwul() kwp(), passed by sparkta_read_results)
*! t2g fix 5: names from the column characteristic <col>[varname] (char list, run 53); no label map
*! t2g fix 3: names for _at#/_m# from label / label content / _dta[] characteristic, with a diagnostic line
*! t2g fix 1: summary line built in a local (display "..." + cond() is not a display expression)
*! t2g: new. Layout read: _margin _se_margin _ci_lb _ci_ub _pvalue _at _at1.. _m1.. (variable labels = names)

// =============================================================================
// sparkta_read_results_margins  (v3.6.0-t2g)
//   sparkta_read_results_margins, [atnames(namelist) factors(namelist)]
// Runs with the margins, saving() dataset in memory (or the frame). Builds
//   _spk_mg_table : 9 x N (b se z pvalue ll ul df crit eform), colnames in the
//                   full factor form the reader expects: "3.rep78",
//                   "2._at", "2._at#3.rep78", "3.rep78#1.foreign"
//   _spk_mg_at    : at conditions x at variables (from _at1.., when _at exists)
// and returns _mg_have/_mg_hasvs/_mg_hasat/_mg_plab/_mg_expr/_mg_cmdl via
// c_local, exactly like sparkta_margins_keep.
// Names: the at variables and the margins factor variables are taken from the
// variable labels of _at1.. and _m1.. (margins labels them with the variable
// name); atnames()/factors() override.
// =============================================================================
program sparkta_read_results_margins
    version 17
    syntax , [ATNames(namelist) FACtors(string) SRC(string) ///
              B(varname) SE(varname) CI(varlist) Pvalue(varname) AT(varlist) ///
              KWB(string) KWSE(string) KWLL(string) KWUL(string) KWP(string)]
    foreach _m in _spk_mg_table _spk_mg_vs _spk_mg_at {
        capture matrix drop `_m'
    }
    // -- t2j fix8x: column mapping (runs on a copy of the user's data: preserve or a frame copy)
    // b() se() ci(lo hi) pvalue() -> _margin _se_margin _ci_lb _ci_ub _pvalue
    // at(varlist)                 -> _at1.. (+ the _at condition index); the names ARE the at() names
    // factors(varlist)            -> _m1..  (names ARE the factor names); a word that is not a
    //                                variable keeps the old meaning: the name of an existing _m#
    local _mapped ""
    if "`ci'" != "" {
        local _nci : word count `ci'
        if `_nci' != 2 {
            display as error "results(..., ci()) takes two variables: ci(lower upper)"
            exit 198
        }
    }
    if "`b'"  != "" {
        sparkta_results_mp_clone `b' _margin
        local _mapped "`_mapped' b=`b'"
    }
    if "`se'" != "" {
        sparkta_results_mp_clone `se' _se_margin
        local _mapped "`_mapped' se=`se'"
    }
    if "`ci'" != "" {
        local _cilo : word 1 of `ci'
        local _cihi : word 2 of `ci'
        sparkta_results_mp_clone `_cilo' _ci_lb
        sparkta_results_mp_clone `_cihi' _ci_ub
        local _mapped "`_mapped' ci=`ci'"
    }
    if "`pvalue'" != "" {
        sparkta_results_mp_clone `pvalue' _pvalue
        local _mapped "`_mapped' pvalue=`pvalue'"
    }
    if "`at'" != "" {
        local _j 0
        foreach _v of local at {
            local ++_j
            sparkta_results_mp_clone `_v' _at`_j'
        }
        // drop any _at# beyond the mapped ones so the old layout cannot leak in
        while 1 {
            local ++_j
            capture confirm variable _at`_j', exact
            if _rc != 0 continue, break
            quietly drop _at`_j'
        }
        // fix8z ROOT CAUSE (trace): `capture drop _at` ran before _at existed, and drop treats a
        // non-existent name as an ABBREVIATION -- it silently dropped _at1, so the at() grid was
        // empty (J(6, 0, .) -> "invalid syntax"). Every drop here is exact-name now.
        sparkta_results_mp_dropx _at
        tempvar _atg
        quietly egen long `_atg' = group(`at')
        quietly gen double _at = `_atg'
        if "`atnames'" == "" local atnames "`at'"
        local _mapped "`_mapped' at=`at'"
    }
    if "`factors'" != "" {
        local _j 0
        local _fnames ""
        foreach _w of local factors {
            local ++_j
            capture confirm variable `_w'
            if _rc == 0 & "`_w'" != "_m`_j'" {
                sparkta_results_mp_clone `_w' _m`_j'
                local _mapped "`_mapped' m`_j'=`_w'"
            }
            local _fnames "`_fnames' `_w'"
        }
        local factors = trim("`_fnames'")
    }
    // autodetect the statistic columns when the file is not a margins, saving() one
    capture confirm numeric variable _margin
    if _rc != 0 & "`b'" == "" {
        local _auto ""
        sparkta_results_mp_pick "`kwb'"  _margin
        if "`_pk'" != "" local _auto "`_auto' b=`_pk'"
        sparkta_results_mp_pick "`kwse'" _se_margin
        if "`_pk'" != "" local _auto "`_auto' se=`_pk'"
        sparkta_results_mp_pick "`kwll'" _ci_lb
        if "`_pk'" != "" local _auto "`_auto' lower=`_pk'"
        sparkta_results_mp_pick "`kwul'" _ci_ub
        if "`_pk'" != "" local _auto "`_auto' upper=`_pk'"
        sparkta_results_mp_pick "`kwp'"  _pvalue
        if "`_pk'" != "" local _auto "`_auto' pvalue=`_pk'"
        if "`_auto'" != "" display as text "  results(): columns autodetected:`_auto' (name them with b() se() ci() pvalue() to override)"
    }
    capture confirm numeric variable _margin
    if _rc != 0 {
        display as error "results(): `src' has no _margin column and none could be detected."
        display as error "  A margins, saving() file works as is. For any other table map the columns:"
        display as error "  results(`src', b(est) se(se) ci(lo hi) pvalue(p) factors(group) at(xvar))"
        exit 198
    }
    capture confirm variable _m1
    local _has_m1 = (_rc == 0)
    capture confirm variable _at1
    local _has_at1 = (_rc == 0)
    if !`_has_m1' & !`_has_at1' {
        display as error "results(): `src' has no condition columns -- give factors(groupvar) and/or at(xvar)"
        display as error "  (margins, saving() files carry them as _m1.. and _at1..)"
        exit 198
    }
    // fix8z: a MAPPED table can carry rows whose condition is missing (collapse keeps the
    // rep78==. group; Fahad's t_map.html showed it as a sixth point labelled "r6"). Drop them
    // with a note; margins, saving() files (nothing mapped) are left untouched.
    if "`_mapped'" != "" {
        local _condvars ""
        local _j 1
        while 1 {
            capture confirm variable _m`_j', exact
            if _rc != 0 continue, break
            local _condvars "`_condvars' _m`_j'"
            local ++_j
        }
        if "`at'" != "" local _condvars "`_condvars' _at"
        if "`_condvars'" != "" {
            local _dropif ""
            foreach _cv of local _condvars {
                local _dropif = cond("`_dropif'" == "", "", "`_dropif' | ") + "missing(`_cv')"
            }
            quietly count if `_dropif'
            if r(N) > 0 {
                display as text "  results(): `r(N)' row(s) with a missing condition value dropped (`=trim("`_condvars'")')"
                quietly drop if `_dropif'
            }
            // fix8z (Fahad's t_map_at.html): the reader downstream maps cells by POSITION in
            // at-major order, as margins lays them out (every at x factor cell present). A user
            // table can be sparse (Foreign had no row at mpg5=10) and unsorted, which shifted the
            // whole Foreign series one condition left. Complete the grid (fillin -> empty cells
            // become non-estimable, drawn as gaps) and sort it the way margins does.
            local _cv2 = trim("`_condvars'")
            local _ncv : word count `_cv2'
            if `_ncv' > 1 {
                quietly fillin `_cv2'
                quietly count if _fillin
                if r(N) > 0 display as text "  results(): `r(N)' empty condition x factor cell(s) added (drawn as gaps)"
                quietly drop _fillin
            }
            sort `_cv2'
        }
        // fix8z: carry the SOURCE's value labels (factor levels) and variable labels (at()/factor
        // names) to the reader through $SPK_MG_VLABS / $SPK_MG_VARLABS, so a mapped page reads the
        // same whatever dataset is in memory (Fahad's t_map_at.html: "foreign=0" vs "Domestic").
        local _vl ""
        local _varl ""
        local _j 0
        foreach _w of local factors {
            local ++_j
            capture confirm variable `_w', exact
            if _rc != 0 continue
            local _lab : variable label `_w'
            if `"`_lab'"' != "" & strpos(`"`_lab'"', "|") == 0 local _varl `"`_varl'`_w'|`_lab'|"'
            local _vln : value label `_w'
            if "`_vln'" == "" continue
            quietly levelsof _m`_j', local(_lv)
            foreach _l of local _lv {
                local _t : label `_vln' `_l'
                if `"`_t'"' != "" & `"`_t'"' != "`_l'" & strpos(`"`_t'"', "|") == 0 local _vl `"`_vl'`_w'|`_l'|`_t'|"'
            }
        }
        foreach _w of local at {
            local _lab : variable label `_w'
            if `"`_lab'"' != "" & strpos(`"`_lab'"', "|") == 0 local _varl `"`_varl'`_w'|`_lab'|"'
        }
        global SPK_MG_VLABS `"`_vl'"'
        global SPK_MG_VARLABS `"`_varl'"'
    }
    foreach _v in _ci_lb _ci_ub _se_margin _pvalue {
        capture confirm numeric variable `_v'
        if _rc != 0 quietly gen double `_v' = .
    }
    // -- at() variables --
    local _has_at 0
    local _atlist ""
    capture confirm numeric variable _at
    if _rc == 0 {
        quietly count if _at < .
        if r(N) > 0 local _has_at 1
    }
    if `_has_at' {
        local _k 1
        while 1 {
            capture confirm variable _at`_k', exact
            if _rc != 0 continue, break
            local _nm : word `_k' of `atnames'
            if "`_nm'" == "" sparkta_results_mp_name _at`_k' at`_k'
            if "`_nm'" == "" local _nm "`_rmn'"
            local _atlist "`_atlist' `_nm'"
            local ++_k
        }
        local _atlist = trim("`_atlist'")
        local _nat : word count `_atlist'
        quietly summarize _at
        local _natc = r(max)
        matrix _spk_mg_at = J(`_natc', `_nat', .)
        matrix colnames _spk_mg_at = `_atlist'
        forvalues _a = 1/`_natc' {
            forvalues _j = 1/`_nat' {
                quietly summarize _at`_j' if _at == `_a', meanonly
                if r(N) > 0 matrix _spk_mg_at[`_a', `_j'] = r(mean)
            }
        }
    }
    // -- margins factor variables --
    local _mlist ""
    local _k 1
    while 1 {
        capture confirm variable _m`_k', exact
        if _rc != 0 continue, break
        local _nm : word `_k' of `factors'
        if "`_nm'" == "" sparkta_results_mp_name _m`_k' m`_k'
        if "`_nm'" == "" local _nm "`_rmn'"
        local _mlist "`_mlist' `_nm'"
        local ++_k
    }
    local _mlist = trim("`_mlist'")
    local _nm_f : word count `_mlist'
    // -- one column per row --
    local _N = _N
    matrix _spk_mg_table = J(9, `_N', .)
    matrix rownames _spk_mg_table = b se z pvalue ll ul df crit eform
    local _cols ""
    forvalues _i = 1/`_N' {
        local _cn ""
        if `_has_at' {
            local _av = _at[`_i']
            if `_av' < . {
                local _cn "`=int(`_av')'._at"
            }
        }
        forvalues _j = 1/`_nm_f' {
            local _mv = _m`_j'[`_i']
            if `_mv' < . {
                local _nmj : word `_j' of `_mlist'
                local _cn = cond("`_cn'" == "", "", "`_cn'#") + "`=int(`_mv')'.`_nmj'"
            }
        }
        if "`_cn'" == "" local _cn "r`_i'"
        local _cols "`_cols' `_cn'"
        matrix _spk_mg_table[1, `_i'] = _margin[`_i']
        matrix _spk_mg_table[2, `_i'] = _se_margin[`_i']
        if _se_margin[`_i'] < . & _se_margin[`_i'] != 0 matrix _spk_mg_table[3, `_i'] = _margin[`_i'] / _se_margin[`_i']
        matrix _spk_mg_table[4, `_i'] = _pvalue[`_i']
        matrix _spk_mg_table[5, `_i'] = _ci_lb[`_i']
        matrix _spk_mg_table[6, `_i'] = _ci_ub[`_i']
        matrix _spk_mg_table[9, `_i'] = 0
    }
    matrix colnames _spk_mg_table = `=trim("`_cols'")'
    local _msg "  results(): `src' -> `_N' margins"
    if `_has_at'    local _msg "`_msg', at(`_atlist')"
    if `_nm_f' > 0  local _msg "`_msg', factors `_mlist'"
    if "`_mapped'" != "" local _msg "`_msg', mapped:`_mapped'"
    display as text "`_msg'"
    c_local _mg_have 1
    c_local _mg_hasvs 0
    c_local _mg_hasat `_has_at'
    c_local _mg_plab ""
    c_local _mg_expr ""
    c_local _mg_cmdl "margins (results())"
end

// name of the variable behind a margins, saving() column (_at# or _m#).
// margins stores it in the column characteristic (run 53, char list):
//     _m1[varname]: rep78        _dta[margin_vars]: rep78     _dta[k_at]: 0
// so: 1. the characteristic <column>[varname]     2. the label if it is itself a name
//     3. the fallback, with one diagnostic line so the user can pass factors()/atnames()
program sparkta_results_mp_name
    args var fallback
    local _out : char `var'[varname]
    local _out = trim("`_out'")
    if !regexm("`_out'", "^[A-Za-z_][A-Za-z0-9_]*$") local _out ""
    if "`_out'" == "" {
        local _lab : variable label `var'
        local _lab = trim(`"`_lab'"')
        if regexm(`"`_lab'"', "^[A-Za-z_][A-Za-z0-9_]*$") local _out "`_lab'"
    }
    if "`_out'" == "" {
        local _out "`fallback'"
        display as text `"  results(): no variable name found for `var' -- using `fallback'; give factors()/atnames() to name it"'
    }
    c_local _rmn "`_out'"
end

// t2j fix8x: clone one user column onto a margins, saving() column name (numeric only).
program sparkta_results_mp_clone
    args src dst
    capture confirm numeric variable `src'
    if _rc != 0 {
        capture confirm variable `src'
        if _rc == 0 display as error "results(): `src' is a string variable -- encode it (or use its numeric code) before mapping it to `dst'"
        else        display as error "results(): variable `src' not found in the results() source"
        exit 198
    }
    if "`src'" == "`dst'" exit
    sparkta_results_mp_dropx `dst'
    quietly gen double `dst' = `src'
end

// t2j fix8z: drop a variable only when a variable of EXACTLY that name exists (drop abbreviates).
program sparkta_results_mp_dropx
    args v
    capture confirm variable `v', exact
    if _rc == 0 drop `v'
end

// t2j fix8x: first variable in the keyword list that exists (case-insensitive) -> clone onto dst.
// Returns the picked name in _pk via c_local ("" when none).
program sparkta_results_mp_pick
    args kwlist dst
    local _found ""
    // a column already present (from the file or from an explicit map) is kept as is
    capture confirm variable `dst'
    if _rc == 0 {
        c_local _pk ""
        exit
    }
    foreach _kw of local kwlist {
        // fix8y: exact -- confirm variable accepts ABBREVIATIONS, so keyword "sd" matched a
        // column "sdm" (Fahad's fix8x smoke test, "columns autodetected: se=sd")
        capture confirm variable `_kw', exact
        if _rc == 0 {
            local _found "`_kw'"
            continue, break
        }
        // case-insensitive match on the dataset's variable names
        quietly ds
        foreach _v in `r(varlist)' {
            if lower("`_v'") == lower("`_kw'") {
                local _found "`_v'"
                continue, break
            }
        }
        if "`_found'" != "" continue, break
    }
    if "`_found'" != "" {
        capture confirm numeric variable `_found'
        if _rc == 0 {
            sparkta_results_mp_dropx `dst'
            quietly gen double `dst' = `_found'
        }
        else local _found ""
    }
    c_local _pk "`_found'"
end
