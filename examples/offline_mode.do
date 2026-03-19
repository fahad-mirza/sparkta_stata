*! sparkta example: offline_mode.do
*! Demonstrates: offline option for air-gapped and institutional environments
*! Dataset: auto (built-in)
*! Version: 1.0 | 2026-03-06
*
* The offline option embeds all JavaScript libraries (Chart.js + plugins)
* directly inside the HTML file. The result opens correctly in any browser
* with zero network requests -- no CDN, no internet required.
*
* REQUIREMENT: sparkta.jar must have the JS libraries bundled at compile time.
* If you built from source, run fetch_js_libs.bat (Windows) or
* fetch_js_libs.sh (Mac/Linux) BEFORE running build.bat / build.sh.
* The pre-compiled dist/sparkta.jar already includes all libraries.
*
* File size note: offline HTML is ~250-320 KB vs ~50 KB for online files.
* This is normal and expected.

sysuse auto, clear

* ============================================================
* BASIC OFFLINE EXPORTS
* ============================================================
* These write .html files to disk (offline mode always saves to file).
* Adjust the export paths for your system.

* Bar chart -- offline, single variable
sparkta price, type(bar) over(rep78) offline ///
    title("Mean Price by Repair Record -- OFFLINE") ///
    export("~/Desktop/offline_bar.html")
di "Created: ~/Desktop/offline_bar.html"

* CI bar -- offline: whiskers included, fully self-contained
sparkta price weight, type(cibar) over(foreign) ///
    cilevel(95) offline ///
    title("Mean Price and Weight (95%% CI) -- OFFLINE") ///
    export("~/Desktop/offline_cibar.html")
di "Created: ~/Desktop/offline_cibar.html"

* Boxplot -- offline: boxplot plugin bundled in jar
sparkta price, type(boxplot) over(rep78) offline ///
    title("Price Distribution -- Boxplot OFFLINE") ///
    export("~/Desktop/offline_boxplot.html")
di "Created: ~/Desktop/offline_boxplot.html"

* Violin -- offline: custom violin (no extra plugin needed)
sparkta price, type(violin) over(rep78) offline ///
    title("Price Distribution -- Violin OFFLINE") ///
    export("~/Desktop/offline_violin.html")
di "Created: ~/Desktop/offline_violin.html"

* ============================================================
* OFFLINE FOR CONFIDENTIAL DATA
* ============================================================
* Use offline + export() to create a self-contained snapshot
* suitable for clinical, financial, or restricted datasets.

* Simulate confidential data with a restricted subsample
sparkta price, type(cibar) over(rep78) if price > 4000 ///
    cilevel(99) offline ///
    title("Restricted Sample -- 99%% CI -- OFFLINE") ///
    subtitle("No network request made when this file opens") ///
    note("Safe for institutional networks and confidential archives") ///
    export("~/Desktop/offline_confidential.html")
di "Created: ~/Desktop/offline_confidential.html"

* ============================================================
* OFFLINE WITH FILTERS
* ============================================================
* Filters work fully offline -- all data pre-computed in the HTML

sparkta price weight, type(bar) over(rep78) ///
    filter(foreign) offline ///
    title("Price and Weight -- Interactive Filter -- OFFLINE") ///
    subtitle("Dropdown works with no internet connection") ///
    export("~/Desktop/offline_filter.html")
di "Created: ~/Desktop/offline_filter.html"

* ============================================================
* OFFLINE WITH DARK THEME
* ============================================================

sparkta price weight, type(bar) over(foreign) ///
    theme(dark) offline ///
    colors("#4e79a7 #f28e2b") ///
    title("Dark Theme -- OFFLINE") ///
    export("~/Desktop/offline_dark.html")
di "Created: ~/Desktop/offline_dark.html"

* ============================================================
* VERIFY OFFLINE SETUP
* ============================================================
* If sparkta errors with "JS library missing from jar", run:
*   fetch_js_libs.bat   (Windows, from the java/ folder)
*   fetch_js_libs.sh    (Mac/Linux, from the java/ folder)
* Then recompile with build.bat / build.sh.
* The pre-compiled dist/sparkta.jar already has all libs bundled.

di ""
di "offline_mode.do complete"
di "Open any of the exported .html files in a browser."
di "All files render correctly with no internet connection."
