*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: normalises every colour option (values in, c_local back) -- internal; not a user command
*! t2f fix 4: new -- the loops previously in sparkta.ado, moved for main-program headroom; logic unchanged

// =============================================================================
// sparkta_color_opts  (v3.6.0-t2f fix 4)
//   sparkta_color_opts, colors(`"..."') cicolors(`"..."') ... tooltipborder(`"..."')
// Applies sparkta_color_list to each colour option with the mode it needs and
// returns the normalised value to the CALLER with c_local under the same name
// (the caller must not re-declare those locals afterwards -- memory rule).
//   space   colors
//   pipe    cicolors ylinecolor xlinecolor ybandcolor xbandcolor apointcolor aellipsecolor aellipseborder
//   grad    gradcolors
//   single  bgcolor plotcolor gridcolor mediancolor meancolor titlecolor subtitlecolor xtitlecolor
//           ytitlecolor xlabcolor ylabcolor legcolor legbgcolor tooltipbg tooltipborder
// =============================================================================
program sparkta_color_opts
    version 17
    syntax , [colors(string asis) cicolors(string asis) ylinecolor(string asis) xlinecolor(string asis) ybandcolor(string asis) xbandcolor(string asis) apointcolor(string asis) aellipsecolor(string asis) aellipseborder(string asis) gradcolors(string asis) bgcolor(string asis) plotcolor(string asis) gridcolor(string asis) mediancolor(string asis) meancolor(string asis) titlecolor(string asis) subtitlecolor(string asis) xtitlecolor(string asis) ytitlecolor(string asis) xlabcolor(string asis) ylabcolor(string asis) legcolor(string asis) legbgcolor(string asis) tooltipbg(string asis) tooltipborder(string asis)]
    foreach _co in colors {
        if `"``_co''"' != "" {
            sparkta_color_list `"``_co''"' space
            c_local `_co' `"`r(list)'"'
        }
    }
    foreach _co in cicolors ylinecolor xlinecolor ybandcolor xbandcolor apointcolor aellipsecolor aellipseborder {
        if `"``_co''"' != "" {
            sparkta_color_list `"``_co''"' pipe
            c_local `_co' `"`r(list)'"'
        }
    }
    if `"`gradcolors'"' != "" {
        sparkta_color_list `"`gradcolors'"' grad
        c_local gradcolors `"`r(list)'"'
    }
    foreach _co in bgcolor plotcolor gridcolor mediancolor meancolor titlecolor subtitlecolor xtitlecolor ytitlecolor xlabcolor ylabcolor legcolor legbgcolor tooltipbg tooltipborder {
        if `"``_co''"' != "" {
            sparkta_color_list `"``_co''"' single
            c_local `_co' `"`r(list)'"'
        }
    }
end
