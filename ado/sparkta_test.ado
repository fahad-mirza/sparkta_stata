*! dashboard_test version 1.0
program define dashboard_test
    version 17
    display as text "  [dashboard_test: testing option parsing]"
    syntax , ///
        [BARGroupWidth(string)] ///
        [LEGBoxHeight(string)]
    display as text "  bargroupwidth=`bargroupwidth'"
    display as text "  legboxheight=`legboxheight'"
    display as text "  Both options parsed successfully!"
end
