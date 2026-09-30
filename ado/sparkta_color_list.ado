*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: applies sparkta_color_norm to a colour list -- internal, installed with sparkta; not a user command
*! split out of sparkta.ado in v3.6.0-t2f (Stage 1 of the file split) -- logic unchanged

// =============================================================================
// sparkta_color_list  (v3.6.0-t2e)
//   sparkta_color_list `"list"' space|pipe|grad|single   -> r(list)
// Applies sparkta_color_norm to every token of a colour option and joins the
// result with the option's own delimiter:
//   space  colors(): quoted triplets stay one token -- colors("230 159 0" navy)
//   pipe   cicolors() ylinecolor() ... : navy|"230 159 0"|#e69f00
//   grad   gradcolors(): "s|e" or "s0|e0:s1|e1" (pipe inside colon groups)
//   single one colour (bgcolor(), titlecolor(), ...)
// =============================================================================
program sparkta_color_list, rclass
    version 17
    args list mode
    local out ""
    if "`mode'" == "single" {
        sparkta_color_norm `"`list'"'
        return local list `"`r(color)'"'
        exit
    }
    if "`mode'" == "space" {
        local _rest `"`list'"'
        while `"`_rest'"' != "" {
            gettoken _tok _rest : _rest
            if regexm(`"`_tok'"', "^[0-9]+$") {
                // "230 159 0" whose quotes were stripped: three integers in a row = one triplet
                gettoken _t2 _r2 : _rest
                gettoken _t3 _r3 : _r2
                if regexm(`"`_t2'"', "^[0-9]+$") & regexm(`"`_t3'"', "^[0-9]+$") {
                    local _tok "`_tok' `_t2' `_t3'"
                    local _rest `"`_r3'"'
                }
            }
            sparkta_color_norm `"`_tok'"'
            if `"`out'"' == "" local out `"`r(color)'"'
            else               local out `"`out' `r(color)'"'
        }
        return local list `"`out'"'
        exit
    }
    if "`mode'" == "grad" {
        local _rest `"`list'"'
        while `"`_rest'"' != "" {
            local _p = strpos(`"`_rest'"', ":")
            if `_p' > 0 {
                local _grp = substr(`"`_rest'"', 1, `_p' - 1)
                local _rest = substr(`"`_rest'"', `_p' + 1, .)
            }
            else {
                local _grp `"`_rest'"'
                local _rest ""
            }
            sparkta_color_list `"`_grp'"' pipe
            if `"`out'"' == "" local out `"`r(list)'"'
            else               local out `"`out':`r(list)'"'
        }
        return local list `"`out'"'
        exit
    }
    // pipe
    local _rest `"`list'"'
    local _first 1
    while `"`_rest'"' != "" | `_first' {
        local _first 0
        local _p = strpos(`"`_rest'"', "|")
        if `_p' > 0 {
            local _tok = substr(`"`_rest'"', 1, `_p' - 1)
            local _rest = substr(`"`_rest'"', `_p' + 1, .)
            local _more 1
        }
        else {
            local _tok `"`_rest'"'
            local _rest ""
            local _more 0
        }
        sparkta_color_norm `"`_tok'"'
        local out `"`out'`r(color)'"'
        if `_more' local out `"`out'|"'
    }
    return local list `"`out'"'
end
