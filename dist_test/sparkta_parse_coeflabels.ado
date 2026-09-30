*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: coeflabels(var = "..") parser -- internal, installed with sparkta; not a user command
*! split out of sparkta.ado in v3.6.0-t2f (Stage 1 of the file split) -- logic unchanged

// =========================================================================
// SUBROUTINE: sparkta_parse_coeflabels (v3.6.0)
// Parses coeflabels() option from the raw pre-syntax argument string.
// Extracts each "varname/Display Label" quoted entry.
// Splits on first / -- everything after is the label (subsequent / safe).
// Safety: strips | and ~ from labels (reserved separators).
// Returns via r(): _pe_customlabels in varname|||label~~~varname|||label format.
// =========================================================================
program sparkta_parse_coeflabels, rclass
    version 17
    local rawstr `"`1'"'
    local result ""

    // Find coeflabels( in the raw string
    local optstart = strpos(`"`rawstr'"', "coeflabels(")
    if `optstart' == 0 {
        return local _pe_customlabels ""
        exit
    }
    local contentstart = `optstart' + length("coeflabels(")
    local content = substr(`"`rawstr'"', `contentstart', .)
    // v3.6.0-s8u: Jann syntax  varname = "Label"  -> normalise to  "varname/Label"
    // BEFORE the quoted-entry walk (the walk only sees text inside quotes).
    // Loop: find  name<spaces>=<spaces>"  and fold the name into the quote.
    local _guard 0
    while regexm(`"`content'"', `"([A-Za-z_][A-Za-z0-9_.]*)[ ]*=[ ]*""') & `_guard' < 50 {
        local _vn = regexs(1)
        local _hit = regexs(0)
        local content = subinstr(`"`content'"', `"`_hit'"', `""`_vn'/"', 1)
        local _guard = `_guard' + 1
    }

    // Walk character by character to extract quoted entries
    local pos 1
    local slen = length(`"`content'"')
    local going 1
    while `going' & `pos' <= `slen' {
        local ch = substr(`"`content'"', `pos', 1)
        if `"`ch'"' == ")" {
            local going 0
            continue
        }
        if `"`ch'"' == char(34) {
            // Extract quoted entry
            local pos = `pos' + 1
            local entry ""
            local inner 1
            while `inner' & `pos' <= `slen' {
                local c2 = substr(`"`content'"', `pos', 1)
                if `"`c2'"' == char(34) {
                    local inner 0
                }
                else {
                    local entry "`entry'`c2'"
                }
                local pos = `pos' + 1
            }
            // Must have / separator
            if strpos("`entry'", "/") == 0 {
                display as error "coeflabels(): missing / separator in: `entry'"
                display as error "  Format: coeflabels(\"varname/Display Label\" ...)"
                exit 198
            }
            // Split on FIRST / only -- everything after is the label
            local sep = strpos("`entry'", "/")
            local vname = strtrim(substr("`entry'", 1, `sep' - 1))
            local lbl   = strtrim(substr("`entry'", `sep' + 1, .))
            // _cons is renamed to Constant by sparkta_read_eresults before Java sees it.
            // Translate key so Java lookup finds it correctly.
            if "`vname'" == "_cons" local vname "Constant"
            // Safety: strip single-char separators from label
            local lbl = subinstr("`lbl'", "|", " ", .)
            local lbl = subinstr("`lbl'", "~", " ", .)
            // Accumulate using ||| and ~~~ sentinels
            if "`result'" == "" local result "`vname'|||`lbl'"
            else local result "`result'~~~`vname'|||`lbl'"
        }
        else {
            local pos = `pos' + 1
        }
    }

    return local _pe_customlabels "`result'"
end
