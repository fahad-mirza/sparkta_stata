*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: normalises one colour token to #rrggbb/rgba() -- internal, installed with sparkta; not a user command
*! split out of sparkta.ado in v3.6.0-t2f (Stage 1 of the file split) -- logic unchanged

// =============================================================================
// sparkta_color_norm  (v3.6.0-t2e)
//   sparkta_color_norm `"spec"'   -> r(color)
// Normalises ONE colour token the way a Stata user writes it, so Java only ever
// receives #rrggbb, rgba(r,g,b,a) or an untouched CSS name:
//   "230 159 0"  (colorpalette r(p#) triplet)              -> #e69f00
//   navy | maroon | gs10 | cranberry | ... (help colorstyle)  -> hex from the table
//   e69f00 | #e69f00 | #E69F00                              -> #e69f00
//   rgb(...) | rgba(...)                                    -> passed through
//   suffixes: name%50 = 50 percent opacity -> rgba(r,g,b,0.5)
//             name*0.6 = intensity (<1 lighter towards white, >1 darker) -> hex
//   anything else (a CSS name such as tomato) is passed through unchanged;
//   suffixes cannot be applied to a pass-through name and are dropped.
// Stata has no hex format, so digits are looked up in "0123456789abcdef".
// =============================================================================
program sparkta_color_norm, rclass
    version 17
    args spec
    local s = trim(itrim(subinstr(`"`spec'"', `"""', "", .)))
    if `"`s'"' == "" {
        return local color ""
        exit
    }
    if regexm(lower(`"`s'"'), "^rgba?[(]") {
        return local color `"`s'"'
        exit
    }
    local _alpha ""
    local _inten ""
    if regexm(`"`s'"', "%([0-9]+)$") {
        local _alpha = regexs(1)
        local s = trim(regexr(`"`s'"', "%[0-9]+$", ""))
    }
    if regexm(`"`s'"', "[*]([0-9.]+)$") {
        local _inten = regexs(1)
        local s = trim(regexr(`"`s'"', "[*][0-9.]+$", ""))
    }
    local hx "0123456789abcdef"
    local r ""
    if regexm(`"`s'"', "^([0-9]+) ([0-9]+) ([0-9]+)$") {
        local r = regexs(1)
        local g = regexs(2)
        local b = regexs(3)
    }
    else if regexm(`"`s'"', "^#?([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])$") {
        local _h = lower(regexs(1) + regexs(2) + regexs(3))
        local r = 16 * (strpos("`hx'", substr("`_h'", 1, 1)) - 1) + strpos("`hx'", substr("`_h'", 2, 1)) - 1
        local g = 16 * (strpos("`hx'", substr("`_h'", 3, 1)) - 1) + strpos("`hx'", substr("`_h'", 4, 1)) - 1
        local b = 16 * (strpos("`hx'", substr("`_h'", 5, 1)) - 1) + strpos("`hx'", substr("`_h'", 6, 1)) - 1
    }
    else {
        local _n = lower(`"`s'"')
        if regexm("`_n'", "^gs([0-9]+)$") {
            local r = min(255, 16 * real(regexs(1)))
            local g = `r'
            local b = `r'
        }
        else {
            // Stata colour names (help colorstyle): name r g b ...
            local _tab "black 0 0 0 white 255 255 255 gray 128 128 128 dimgray 105 105 105"
            local _tab "`_tab' blue 0 0 255 red 255 0 0 green 0 255 0 yellow 255 255 0 cyan 0 255 255 magenta 255 0 255"
            local _tab "`_tab' navy 26 71 111 maroon 144 53 59 forest_green 85 117 47 dkorange 227 126 0 teal 110 142 132"
            local _tab "`_tab' cranberry 193 5 52 lavender 165 165 223 khaki 202 194 126 sienna 160 82 45 emidblue 123 146 168"
            local _tab "`_tab' emerald 45 109 102 brown 156 136 79 erose 191 161 156 gold 255 210 0 bluishgray 205 210 217"
            local _tab "`_tab' lime 187 205 47 ltblue 173 216 230 olive 107 142 35 orange 255 165 0 orange_red 255 69 0"
            local _tab "`_tab' pink 255 192 203 purple 128 0 128 chocolate 210 105 30 dkgreen 0 100 0 dknavy 0 0 128"
            local _tab "`_tab' eggshell 255 255 224 eltblue 130 178 220 eltgreen 179 226 175 ebg 218 217 200 ebblue 0 139 188"
            local _tab "`_tab' edkblue 0 76 105 emidgreen 89 168 89 midblue 0 128 255 midgreen 0 192 0 mint 160 255 200"
            local _tab "`_tab' ltkhaki 250 250 200 ltbluishgray 234 242 243 olive_teal 200 210 180 sand 240 215 155"
            local _tab "`_tab' sandb 255 210 0 stone 215 200 175 dkred 139 0 0 ltred 255 128 128"
            // a bare number must never be looked up (" 230 " occurs inside the table -> olive)
            local _pos = 0
            if !regexm("`_n'", "^[0-9.]+$") local _pos = strpos(" `_tab' ", " `_n' ")
            if `_pos' > 0 {
                local _rest = substr(" `_tab' ", `_pos' + strlen(" `_n' "), .)
                local r : word 1 of `_rest'
                local g : word 2 of `_rest'
                local b : word 3 of `_rest'
            }
        }
    }
    if "`r'" == "" {
        // unknown name: pass through (CSS names such as tomato, steelblue work in the browser)
        return local color `"`s'"'
        exit
    }
    if "`_inten'" != "" {
        local _f = real("`_inten'")
        if `_f' > 0 & `_f' < 1 {
            foreach _c in r g b {
                local `_c' = 255 - (255 - ``_c'') * `_f'
            }
        }
        else if `_f' > 1 {
            foreach _c in r g b {
                local `_c' = ``_c'' / `_f'
            }
        }
    }
    local out "#"
    foreach _c in r g b {
        local _cv = min(255, max(0, round(``_c'')))
        local out "`out'`=substr("`hx'", floor(`_cv'/16)+1, 1)'`=substr("`hx'", mod(`_cv',16)+1, 1)'"
        local `_c' = `_cv'
    }
    if "`_alpha'" != "" {
        local _a = min(100, max(0, real("`_alpha'"))) / 100
        local out "rgba(`r',`g',`b',`_a')"
    }
    return local color "`out'"
end
