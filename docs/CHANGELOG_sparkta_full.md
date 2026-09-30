*! sparkta version 3.6.0 2026-09-13
*! ===========================================================================
*! FULL CHANGELOG (archive). The live sparkta.ado header keeps only a short
*! recent list; this file is the complete history. Newest first.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9v (2026-09-16, JAVA ONLY, HeadlessBrowser; jar rebuilt; ado header line 4 only):
*!   THE ALT+TAB WINDOW, ROOT CAUSE FOUND. [stated] Fahad restarted the laptop (clean Alt+Tab),
*!   ran `procs` (82 processes, none ours), then ONE export with saveas(png pdf svg) -- on his
*!   machine sparkta-export.jar exists, so only the SVG used the session browser (PNG/PDF from
*!   the SVG) -- and `procs` again 50 s later: "no session browser", yet a NEW msedge.exe tree
*!   of 7 processes, root pid 3180 started at the export time with parent "?" (a dead parent),
*!   was alive. So: the msedge.exe Java launches is a LAUNCHER; it exits once the real browser
*!   is up (or on Browser.close); proc.isAlive() then reads false ("no session browser") and
*!   proc.toHandle().descendants() is EMPTY, so fix9r's killTree(proc) had nothing to kill and
*!   the real browser (3180 + children) lived on -- the headless=new window Fahad sees in
*!   Alt+Tab. fix9s's sweep could not catch it either (no command lines on Windows, fix9u).
*!   FIX: record the tree while the launcher is alive -- sessionTree (pid -> the ORIGINAL
*!   ProcessHandle, whose start time makes isAlive() false once a pid is reused, so an unrelated
*!   process can never be killed) filled by trackTree() right after the DevTools port file
*!   appears and at every export (touchIdle), plus trackByLaunch(): any process with the same
*!   exe name started after the launch that does not hang off a browser that existed before the
*!   launch (the user's own Edge/Chrome) is ours too. close(why) builds the victim list FIRST,
*!   sends Browser.close, kills the launcher, waits up to 2 s, then destroys every tracked
*!   process still alive (forcibly on a second pass). The idle timer calls close("idle");
*!   lastClose records why/when/tracked/force-killed/still-alive. `procs` gained a "session:"
*!   line (devtools reachable?, tracked pids and how many alive, started N s ago, idle timer
*!   pending?, last close ...) and a SESSION marker on tracked pids. Shutdown hook added once.
*!   Verified in the cloud with the SFI stub (Drive.java): explicit close -> tracked 8/9,
*!   still alive after 0; idle close -> "last close: idle ..., 0 alive" (a `procs` issued while
*!   close() is still running shows the old state -- not a bug); a launcher script that spawns
*!   the browser detached and exits -> tracked 10, all gone. Gate ALL CHECKS PASSED. NOT RUN ON
*!   REAL STATA: tests/test_fix9v_v360.do PART E is the check (procs 15 s after a saveas()).
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9u (2026-09-16, Java + ado; jar rebuilt; pre-push worklist):
*!   ALT+TAB WINDOW CLOSED (no code change). Fahad's `procs` output on fix9t: 67 browser-like
*!   processes, NONE headless, none under Stata's JVM, "no session browser"; all are WebView2
*!   runtime processes of other apps (started at login) or his own Chrome tree. The window is
*!   titled "Price by Repair record" = a chart PAGE: when export() is absent sparkta writes a
*!   temp html and opens it in the default browser (DashboardBuilder.exportOrOpen ->
*!   BrowserLauncher.open, rundll32 on Windows) -- by design, one tab per non-exported chart.
*!   Also learned: the JDK does not implement ProcessHandle.Info.commandLine()/arguments() on
*!   Windows (JDK-8176725), so `procs` shows no command lines there and the fix9s sweepStrays()
*!   can never match on Windows; close() = DevTools Browser.close + process tree (fix9r) is what
*!   cleans up, and the evidence says it does. `procs` now prints one line saying so.
*!   DECISION 5 (Fahad: "transparent fill"): hollow markers (coefplot p > 0.1, marginsplot,
*!   event study, plugin-drawn dots) are TRANSPARENT like Stata msymbol(Oh) -- grid lines and CI
*!   bars show through the ring. Was: filled with the plot background. ChartRenderer.HOLLOW_FILL =
*!   rgba(0,0,0,0) (canvas2svg emits fill-opacity="0"; 'transparent' is not an SVG keyword);
*!   _spkSigDot strokes the ring only. Probe: h_t18_es export SVG has 3 fill-opacity="0" markers.
*!   DECISION 6 (Fahad: "fix now"): export clones were drawn TWICE. new Chart() with
*!   responsive:false + animation:false already renders synchronously (Chart.js 4: bindEvents sets
*!   attached, the constructor calls update(), render() draws when the animator holds nothing --
*!   verified in the bundled chart.umd source) and the following tmp.draw() drew everything again
*!   into a C2S context whose clearRect is a no-op, so every path was emitted twice and
*!   translucent fills (CI bands, areas, bubbles) came out darker in SVG/PDF than on screen.
*!   New _spkExportChart(c2s,c2) constructs and draws only if the constructor emitted nothing;
*!   used by _spkChartToSvg and the by() grid in _spkPageToSvg. Probe on 6 harness pages: path
*!   count exactly halves (31 vs 62, 46 vs 92, 52 vs 102, 99 vs 197, 93 vs 184, 66 vs 129);
*!   gate svg 63/63, latex 44/44, pixel 73/73 unchanged.
*!   DECISION 7 (Fahad: "drop it"): headless ?print= runs (full-page PNG included) add body class
*!   spk-print, and body.spk-print .subtitle{display:none} hides the "Made with sparkta" footer
*!   (@media print already hid it; the on-screen PNG run did not). Page footer unchanged on screen.
*!   ITEM 8: DROPPED -- [stated] Fahad: SSC copies .jar files over as regular package files, so
*!   no `net get sparkta` hint is needed (added and removed the same day); the SSC e-mail no
*!   longer asks the jar question.
*!   ITEM 10: the "Stata 15-16 (default 384 MB)" heap lines dropped (sparkta needs Stata 17).
*!   ITEM 11: docs/index.html install line = net install ... main/ado/ (ssc install once Kit
*!   lists it); code.install wraps. ITEM 12: README "Upgrading from 3.5.111" paragraph (replace +
*!   restart Stata; relative export paths now resolve against c(pwd), 3.5.111 used the JVM folder;
*!   v3.5.111 tag URL). ITEM 13: pkg Distribution-Date 20260916 (re-bump if the push slips).
*!   Gate ALL CHECKS PASSED (harness 73/73, pixel 73/73, svg 63/63, latex 44/44). NOT RUN ON
*!   REAL STATA: needs test_release_v360.do 210/210 + the fix9u smoke list in the handover.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9t (2026-09-15, JAVA ONLY, HeadlessBrowser; jar rebuilt; ado header line 4 only):
*!   diagnostic only. fix9s suite 210/210, session browser started/closed twice, yet Fahad still
*!   sees "a new tab" in Alt+Tab, Task Manager shows no command line, and PowerShell is
*!   restricted on his corporate laptop. New command "procs" lists every browser-like process
*!   (msedge / chrome / chromium / sparkta in the command line) from inside Stata's own JVM via
*!   ProcessHandle: pid, parent pid, start time, HEADLESS and SPARKTA markers, the command line
*!   (first 400 chars). Run in Stata:
*!     javacall com.dashboard_test.tools.HeadlessBrowser execute, classpath("c:\ado\personal\sparkta.jar") args("procs")
*!   Verified in the cloud (lists a live headless chromium with its --user-data-dir). Gate ALL
*!   CHECKS PASSED. Owed: the procs output while the window is present, plus the window title.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9s (2026-09-15, JAVA ONLY, HeadlessBrowser; jar rebuilt; ado header line 4 only):
*!   [stated] Fahad: "background windows still open" after a fix9r suite run whose log shows the
*!   session browser started twice, reused 8 times and CLOSED twice ("session browser closed").
*!   So the windows he sees are not the live session browser: they are ORPHANS from earlier
*!   sessions -- a one-off run that timed out and had only its launcher force-killed (fix9o era),
*!   a Stata that was ended without its JVM shutdown hook running, etc. Every sparkta browser is
*!   launched with a profile directory named sparkta-browser-* (session) or sparkta_run_*
*!   (one-off), so they can be told apart from the user's own Edge/Chrome. New sweepStrays():
*!   at the start of every HeadlessBrowser call and inside close(), every process whose command
*!   line has --headless AND --user-data-dir=<one of those names>, that is not the live session
*!   browser and not this JVM or its ancestors, is destroyed (forcibly if needed). The user's
*!   own browser windows are never matched. Verified in the cloud: a hand-launched orphan with a
*!   sparkta_run_ profile (8 processes) is gone after one HeadlessBrowser call; an unrelated
*!   headless browser next to it (9 processes) is untouched. Gate ALL CHECKS PASSED. NOT RUN ON
*!   STATA. Owed: run any saveas() once on fix9s (the sweep runs before the export), then check
*!   Alt+Tab and Task Manager -- the old windows should be gone; if any msedge.exe remains, its
*!   command line (Task Manager > Details > Command line) says whether it is ours.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9r (2026-09-15, JAVA ONLY, HeadlessBrowser; jar rebuilt; ado header line 4 only):
*!   [stated] Fahad: on the 120 s build the Alt+Tab window was STILL open after more than 120 s,
*!   so the idle timer's close() was not ending the browser on his Windows. close() used to GET
*!   /json/close (a no-op without a target id) and Process.destroy() the launched msedge.exe --
*!   and on Windows that can be a launcher whose children are the real browser, which destroy()
*!   never reaches. close() now (1) sends DevTools Browser.close over the browser websocket (an
*!   orderly quit of every window and child), (2) waits 2 s, (3) killTree(): destroy + destroy
*!   forcibly + ProcessHandle.descendants() destroyed one by one. The same killTree() ends a
*!   timed-out one-off run (was destroyForcibly on the launcher only). Verified in the cloud
*!   through the real svg command with SPARKTA_BROWSER_IDLE_MS=3000: 10 chromium processes 0.8 s
*!   after the export, 0 after 4.8 s, profile directory removed. Gate ALL CHECKS PASSED.
*!   NOT RUN ON STATA. Owed: on Windows, one saveas(), wait ~15 s, Alt+Tab shows no sparkta
*!   window and Task Manager shows no msedge.exe with --headless; if one remains, report its
*!   window title and command line (Task Manager > Details > Command line column).
*! ---------------------------------------------------------------------------
*! fix9p CONFIRMED by Fahad (2026-09-15): test_release_v360.do 210/210 CLEAN RUN on the fix9p
*!   build with an Edge window open; r_saveas_page.pdf written (2 pages). fix9p is the BASELINE
*!   OF RECORD. Two things seen on that PDF and on his desktop -> fix9q.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9q (2026-09-15, JAVA ONLY + help; jar rebuilt; ado header line 4 only):
*!   (1) [stated] Fahad: the whole-page PDF "is cut" -- the statistics table is wider than a
*!       portrait page and scrolls sideways on screen (.tbl-wrap overflow-x:auto); print cannot
*!       scroll, so the STD DEV / DISTRIBUTION columns were clipped at the card edge. @media
*!       print now sets .tbl-wrap overflow:visible, table.st at .68rem with .26/.38rem cell
*!       padding and wrapping headers, smaller spark-cell padding, and .grp break-inside:avoid.
*!       Verified through the ado's page-scope route (HeadlessBrowser run + chromium, 820 px
*!       window): every column including the full sparkline fits on the page.
*!   (2) [stated] Fahad: "there is some window open when I do Alt+Tab; after the exporting is
*!       complete, that window should close." On his Windows the headless=new session browser
*!       (kept alive between saveas() calls since t1b/t1f) is listed in Alt+Tab for the whole
*!       idle period. Default idle 120 s -> 10 s (HeadlessBrowser.idleMs): exports inside one
*!       do-file are seconds apart so the ~0.3 s reuse stays, a longer pause costs one ~2 s
*!       relaunch, and the window is gone 10 s after the last export. saveas(..., idle(#))
*!       and close still override; help text updated (default 10).
*!   Gate ALL CHECKS PASSED. NOT RUN ON STATA. Owed: saveas(..., page) PDF shows every stats
*!   column; the Alt+Tab window disappears ~10 s after the last export; test_release 210/210.
*! ---------------------------------------------------------------------------
*! fix9o CONFIRMED by Fahad (2026-09-15): fit-line legend entries "appear as intended";
*!   test_fix9d_v360.do 14/14 on the fix9o build (session-browser svg/png/pdf routes, table PDF,
*!   3x page PNG, box by() PDF). test_release_v360.do 209/210: the one unexpected failure is
*!   r_saveas_page (whole-page PDF) -- "HeadlessBrowser run: timed out", rc 198.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9p (2026-09-15, JAVA ONLY, HeadlessBrowser + print CSS; jar rebuilt; ado header
*!   line 4 only): r_saveas_page is the ONE saveas() case that goes through the one-off shell
*!   route (msedge --headless=new --print-to-pdf ... ?print=page via HeadlessBrowser "run"), and
*!   that command line had no --user-data-dir, so the one-off browser opened the DEFAULT Edge
*!   profile. With a normal Edge window already using that profile (Fahad had Edge open for the
*!   PDF previews), the new process waits on the profile lock -- or hands the command to the
*!   running window, which never prints -- until the 90 s cap; the session browser was never
*!   affected because it has always had its own directory (t1b). The same page-scope print
*!   runs in 1.8 s in the cloud, which is why the review side never saw it. Fix: "run" and
*!   "runcapture" add --user-data-dir=<temp dir> when the command has none and delete it after.
*!   Also: the "Export:" toolbar label leaked into PAGE-scope prints (fix9c hid it for chart and
*!   table scopes only) -- .spk-toolbar joins the general @media print hide list. Verified in the
*!   cloud through HeadlessBrowser.execute("run", chromium, <ado flags>, ?print=page): rc 0,
*!   1.8 s, 2-page PDF with the chart, temp profile removed. Gate ALL CHECKS PASSED. NOT RUN ON
*!   STATA. Owed: with an Edge window OPEN, rerun test_release_v360.do (expect 210/210; the
*!   r_saveas_page PDF must have the chart on page 1 and no "Export:" text).
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9o (2026-09-15, JAVA ONLY, ChartRenderer; jar rebuilt; ado header line 4 only):
*!   [stated] Fahad: the fit line's legend entry should be a dashed line in the fit colour, not a
*!   hollow circle. Cause: the scatter legend uses usePointStyle, and Chart.js builds each entry
*!   from the dataset's POINT style (controller.getStyle(0)), which carries no borderDash -- a
*!   pointRadius:0 line dataset therefore showed an empty ring (and, with pointStyle:'line'
*!   alone, a solid line). Fix: fit datasets carry pointStyle:'line' (both emission sites; the
*!   plot is unchanged because pointRadius stays 0), and scatterLegendCfg() wraps the default
*!   generateLabels to copy borderDash / borderWidth / borderColor onto the entry of every
*!   point-less line dataset -- skipped when leglabels()/relabel() installed their own
*!   generateLabels. Filter rebuilds only replace .data, so they keep the style. Verified
*!   headless on H-D9 (scatter over(foreign) lfit): the "Price (lfit)" entry is a dashed line
*!   in the dark fit accent; 0 JS errors. Gate ALL CHECKS PASSED. NOT RUN ON STATA. Owed: any
*!   scatter with fit() (legend entry = dashed line in the fit colour, on screen and in the
*!   PNG/SVG/PDF exports), test_release_v360.do 210/210.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9n (2026-09-14, JAVA ONLY + help; jar rebuilt; ado header line 4 only): [stated]
*!   Fahad: "can the PDF, PNG and SVG show which filter is applied at the time of exporting?"
*!   New page helper _spkActiveFilters() (printJs, on every page) reads the live filter bar:
*!   every filters() dropdown not on All ("<label> = <option>"), every sliders() range moved off
*!   its full span ("<label> = lo - hi", from the on-screen value spans), plus the current row
*!   count from the obs counter ("N = 11"); empty when nothing is filtered. Drawn as a third
*!   header line (12 px, context-line colour) by _spkChartToSvg and _spkPageToSvg (so SVG,
*!   page PNG and the saveas() routes carry it), by the single-chart PNG button, and inserted
*!   as a <p class='subtitle2 spk-print-filters'> under the context line by the print swap
*!   (removed on afterprint) for the PDF button, Ctrl+P and --print-to-pdf. The exporter
*!   references the helper through typeof so a page without printJs (or the svg_check sandbox)
*!   still exports. Help: notoolbar paragraph mentions the line. Verified headless on a bar page
*!   with filters(foreign) + sliders(mpg): "Filters: Car origin = Domestic; Mileage (mpg) = 20 -
*!   35; N = 11" in the PNG and in the chart-scope PDF; nothing shown when unfiltered; 0 JS
*!   errors. Gate ALL CHECKS PASSED (harness 73/73, pixel 73/73, svg 63/63, latex 44/44).
*!   NOT RUN ON STATA. Owed: set a dropdown / slider on a page, press PNG, SVG, PDF -- the
*!   Filters line under the context line; unfiltered exports unchanged; test_fix9d_v360.do +
*!   test_release_v360.do 210/210.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9m (2026-09-14, JAVA ONLY, HtmlGenerator print CSS; jar rebuilt; ado header line 4
*!   only): Fahad's printed by() page showed the shared key as bare "1 2 3 4 5" -- the swatches
*!   are CSS background colours (.sw/.dot) or border colours (.ln/.box), and browsers drop
*!   background colours when printing unless the page opts in (the "Background graphics"
*!   checkbox). The @media print block now sets print-color-adjust:exact on the shared key,
*!   the elements key and the chips row, so every key glyph prints whatever the dialog says.
*!   Verified headless: chart-scope print of H-D3 (bar over foreign, by rep78) WITHOUT
*!   background graphics shows the Domestic / Foreign swatches. Gate ALL CHECKS PASSED.
*!   NOT RUN ON STATA (same smoke list as fix9k/fix9l; the by() PDF must show the key colours).
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9l (2026-09-14, JAVA ONLY + help text; jar rebuilt; ado header line 4 only):
*!   [stated] Fahad: "one button for PNG SVG and PDF for by() plots". A by() page used to carry a
*!   toolbar on EVERY panel: PNG/SVG exported that panel alone while PDF (chart scope since
*!   fix9k) printed the whole grid whichever panel was pressed. Now ONE toolbar sits above the
*!   panel grid (before the shared key) and all three buttons export the whole figure the way
*!   saveas() does: _spkDownload('page', fmt) -- SVG = _spkPageToSvg() (title, context line,
*!   shared key, panels two across, panel captions); PNG = that SVG rasterised at 2x through an
*!   <img> onto an offscreen canvas (fonts, keys and the large-data raster travel inside the
*!   SVG); PDF = the chart-scope print. Per-panel toolbars removed (buildByPanels). Help:
*!   notoolbar paragraph rewritten (figure exports; one toolbar per by() page; whole page via
*!   the browser's Print; table PDF button) through build_sthlp.py; smcl_check OK.
*!   Verified headless on H-D2 (6 panels): 1 toolbar on the page, PNG 1936x2478 and SVG show
*!   the same figure (title, key, 6 captioned panels two across), 0 JS errors. Gate ALL CHECKS
*!   PASSED (harness 73/73, pixel 73/73, svg 63/63, latex 44/44). NOT RUN ON STATA. Owed: on a
*!   by() page press PNG, SVG, PDF (one toolbar above the grid; every panel in each file), plus
*!   the fix9k list (single chart PNG/PDF/SVG, Ctrl+P, table PDF), test_fix9d_v360.do and
*!   test_release_v360.do 210/210.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9k (2026-09-14, JAVA ONLY, jar rebuilt; ado header line 4 only): the three export
*!   buttons on the chart toolbar, after Fahad's report ("PDF shows an empty chart and runs to
*!   several pages; PNG has no title/subtitle; SVG fine"). HtmlGenerator only.
*!   (1) PDF printed a BLANK chart card: _spkSwapForPrint replaced each canvas with an <img>
*!       whose src was a data: SVG; the browser decodes that asynchronously, and Chrome lays out
*!       the print pages the moment the beforeprint handlers return -- the image still measured
*!       0 x 0 (probe: naturalWidth 0 right after the swap, 1022 x 588 a frame later). The swap
*!       now inserts the SVG INLINE (box.innerHTML, viewBox added, width 100 per cent, height
*!       auto), which is laid out synchronously. Same fix serves the saveas() shell route
*!       (?print=chart + --print-to-pdf) and the browser's own Ctrl+P.
*!   (2) The chart toolbar's PDF button printed the WHOLE page (title, chart, statistics panel,
*!       table) so it ran to 2-3 pages; it now prints the CHART scope (_spkPrintOnly('chart'):
*!       title + context line + chart + keys), matching its PNG/SVG neighbours. Whole page = the
*!       browser's Print command; the publication table keeps its own table-scope PDF button.
*!   (3) _spkChartToSvg(chartInst,keyItems,noHead): the print swap passes noHead=true so the
*!       page title is not repeated inside the SVG (and inside EVERY panel on a by() page); the
*!       SVG file export keeps its header. verify_before_zip's s9l signature check updated.
*!   (4) PNG button now draws the page title and context line above the chart (bold 18 px /
*!       12 px, 18 px figure margin, all scaled by the canvas device ratio), then the chart,
*!       then the keys -- the same figure the SVG export produces.
*!   Verified headless (Playwright + the container Chromium): PNG button on H-T18 shows title,
*!   context, chart, key; chart-scope print of H-T18 = 1 page with the chart, H-D2 by() page =
*!   2 pages, panels two across, page title once; inline <svg> measured at full size the instant
*!   the swap returned; the ado's shell --print-to-pdf route on the offline page H-T19 = 1 page
*!   with the chart; 0 JS errors. Gate ALL CHECKS PASSED (harness 73/73, pixel 73/73, svg 63/63,
*!   latex 44/44). test_release_v360.do 210/210 was confirmed by Fahad on fix9j the same day.
*!   NOT RUN ON STATA. Owed: build.bat + restart; on any page press PNG (title present), PDF
*!   (chart, one page, no blank box), SVG; a by() page PDF; Ctrl+P whole page shows the chart;
*!   test_fix9d_v360.do (saveas routes) and test_release_v360.do 210/210 (jar changed).
*!   Fahad's "cannot read file" when uploading the PNG to the chat is not reproduced here (the
*!   file is a plain image/png blob); retry with the fix9k PNG.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9j (2026-09-14, JAVA ONLY, jar rebuilt; ado header line 4 only): event-study markers
*!   drawn as "white spots, not hollow circles" (Fahad, examples/postest.do "Event study,
*!   relabelled periods"). Root cause, traced with a canvas-context probe (setLineDash / save /
*!   restore / arc hooks): the esZero plugin (the dashed "Null (0)" line, afterDatasetsDraw)
*!   called c.save() and c.setLineDash([5,5]) BEFORE touching ch.scales.y. The crisp plugin's
*!   afterInit chart.resize() renders the chart once before Chart.js has built its scales, so
*!   ys.getPixelForValue threw a TypeError inside the try/catch around resize(); the exception was
*!   swallowed and the save() never undone, leaving [5,5] as the canvas context's BASE dash for the
*!   life of the page. Chart.js strokes point rings without setting a dash, so every marker ring on
*!   the page was dashed: solid markers hid it, hollow markers (white fill, 2 px ring) became white
*!   dots with a broken rim, and the reference-period ring looked clipped. Fix in ChartRenderer:
*!   esZero returns before save() when xs/ys/chartArea are missing (as esCI already did) and
*!   restores in a finally. Reproduced and verified headless on a page shaped like Fahad's
*!   (numeric event time, cistyle(area), levels(90 95), connected): 253 dashed arcs before, 0 after;
*!   rings intact at 6x zoom. Coefplot/marginsplot pages were never affected (their cpZero reads the
*!   scale before saving, so the early throw leaks nothing) -- Fahad's coef.png/pdf hollow markers
*!   are clean. Scan of every save()+setLineDash plugin: no other one saves before its scale check.
*!   RULE: a plugin that changes context state must check its inputs BEFORE save() and restore in a
*!   finally; the afterInit resize() draw runs with no scales.
*!   Also this round: docs/charts/*.html regenerated by Fahad with showcase.do on 3.6.0 (were
*!   v3.5.38), js_check 8/8; postest.do exports reviewed (coef.pdf 1 page vector, table-only PDF,
*!   full-page PNG 3x with the table); test_netinstall_out pages all v3.6.0, js_check 3/3.
*!   Gate ALL CHECKS PASSED, harness 73/73, pixel_check 73/73, svg_check 63/63, latex 44/44.
*!   NOT RUN ON STATA. Owed: build.bat + restart, examples/postest.do (event-study pages: hollow
*!   rings, not white spots), test_release_v360.do 210/210 (jar changed).
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9i (2026-09-14, DOCS AND EXAMPLES ONLY -- sparkta.ado and sparkta.jar are the
*!   fix9h-e files, byte for byte; header line 4 still says fix9h-e on purpose, so the real-Stata
*!   confirmation of that build stands). Roadmap 4a + docs item 5:
*!   - ado/sparkta.sthlp rebuilt by verify/build_sthlp.py rev 2: "Large data (scatter and
*!     bubble)" paragraph in "Memory and large datasets" (the 20,000-point rule, what the mode
*!     changes and what it does not, the Stata note, exports as one embedded image, SVG size,
*!     the fit() thinning), Contents line updated; smcl_check OK. Only the builder changed;
*!     the pre-docs source is untouched.
*!   - ado/sparkta.pkg Distribution-Date 20260913 -> 20260914 (the help changed).
*!   - README.md: three new sections -- "Post-estimation charts (new in 3.6.0)", "Export to
*!     PNG, PDF and SVG (saveas)", "Large data" -- plus Post-estimation / Publication table rows
*!     in the option table and the new files in "Repository structure".
*!   - examples/postest.do NEW (coefplot, marginsplot, eventstudy, table options, saveas();
*!     every command is one the release suite or the help file already runs).
*!   - examples/basic_charts.do, stat_charts.do, offline_mode.do: "%%" -> "%" in 15 title /
*!     subtitle / note strings (Stata does not collapse %% inside an option string, so the
*!     page showed "95%% CI"; showcase.do and the gallery chart C1_cibar.html use a single %
*!     and render correctly). offline_mode.do also had `if price > 4000` AFTER the comma,
*!     which is a hard syntax error -- moved before the comma. Static check: every option
*!     used in examples/*.do exists in the 3.6.0 syntax block.
*!   - tests/test_netinstall_v360.do NEW (rev 2): the roadmap 4a script; Fahad has no second
*!     machine, so with CLEAN = 1 it redirects PLUS and PERSONAL to an empty temp folder for the
*!     session (sysdir set; a restart undoes it), proves `which sparkta` fails, then net installs
*!     from GitHub and checks 24 files on the adopath, header 3.6.0, jar found, handshake,
*!     coefplot, saveas PNG (session-browser route, as an SSC user gets), help -- 9/9. Offline readiness proved here: pkg f lines == ado/ files
*!     (24, no duplicates), all 23 text files ASCII with LF, all 22 ados carry version 17,
*!     ado/ dist/ and SSC jars identical, ado and sthlp in the SSC zip identical to ado/.
*!   - docs/SSC_SUBMISSION.md, claude GITHUB_UPDATE doc: refreshed to this tree; e-mail draft.
*!   NOT RUN ON STATA. Owed: examples/postest.do and the three fixed examples once on 3.6.0,
*!   test_netinstall_v360.do on the second machine after the push.
*! ---------------------------------------------------------------------------
*! fix9h-e CONFIRMED on real Stata (2026-09-14, Fahad's 5th run; review-side QA/QC): test_fix9g_v360.do
*!   13/13 on the fix9h-e build -- header line 4 fix9h-e; large-data note on every > 20,000
*!   page (100,000 / 50,250 in a panel / 20,001) and NOT on the 20,000 page; _spkBig flags
*!   1 / 3 (over) / 2 (by panels) / 0 as expected; animation off only on big pages; fit +
*!   CI series 82 points spanning -4.52 .. 4.36 (the full x range), order:-1, dark accent
*!   rgba(43,67,92) with the legend in dataset order; exports 6.8 s / 5.8 s; SVG one page-
*!   aligned <image>; PDF vs SVG vs PNG cloud centroids within 1.5 pt on both the single
*!   page (507,306 / 506,305 / 506,305) and the by() page (993,457 / 992,456) -- the PDF now
*!   matches the HTML; bigscatter_probe 13/13 (hover 8-24 ms on the 100k pages, tooltip 1.0,
*!   bubble 208 ms), byfilter_probe --embedded 1/1, js_check 13/13, 0 JS errors.
*!   test_release_v360.do on the same build: 210/210 CLEAN RUN (29 expected failures as
*!   intended); review side js_check 183/183, intent 209/209, fidelity 46/46, LaTeX 93/93,
*!   viz 0 errors; r_base_scatter_fit shows per-group dark fit lines on top. fix9h-e is the
*!   BASELINE OF RECORD. Two checker gaps fixed on the way (verify/ only): js_check's vm
*!   sandbox lacked atob(), so every filter page with more than 200 rows (base64 Float64
*!   columns) reported "filter refresh threw" -- atob/btoa added; intent_check and
*!   fidelity_check looked for test_release_v360.do in the package root, the repo keeps it in
*!   tests/ -- both now try tests/ first. The pre-existing "trailing comma in chart config"
*!   js_check warning is buildPointConfig's trailing comma (legal JS), not a fix9g change.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9h-e (2026-09-14, JAVA ONLY, jar rebuilt): PDF raster placement, final form. After
*!   fix9h-c Fahad's PDFs had the cloud at the right SIZE but ~46 pt too low (and the second
*!   by() panel subtly left): the PDF pattern PDFBox graphics2d builds for an SVG <image> has
*!   Matrix [1 0 0 -1 0 imageHeight] in page space, so the image's bottom-left always sits on
*!   the page origin, ignoring translation as well as scale, and it TILES with XStep = the
*!   image width (which is why the second panel looked almost right). FIX: the export raster
*!   is now the size of the whole export page and page-aligned -- _spkChartToSvg and
*!   _spkPageToSvg stash the chart's page offset and the page size on the canvas2svg ctx
*!   (ctx.__spkPage), the plugin draws the cloud into a page-sized bitmap at that offset and
*!   blits it at (-x,-y); under correct SVG semantics it lands at page (0,0) (nested panel
*!   svgs clip it to their viewport), and under the pattern's page-origin anchoring it lands
*!   in the same place with no tiling inside the page. Verified: rsvg renders of the single
*!   and by() SVGs (clouds on their axes, both panels), the session-browser PNG route
*!   (2200x1400, both panels). The PDFBox path itself: Fahad's rerun. SVG ~1 MB.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9h-d (2026-09-14, JAVA ONLY, jar rebuilt): export tabs left open. Fahad sees several
*!   windows titled SPARKTA_SVG_READY in Alt+Tab: the session browser (one headless Edge kept
*!   alive 2 min between saveas() calls) lists every open target as a window under
*!   --headless=new, and the export tabs were NOT closing -- HeadlessBrowser closed them with
*!   an HTTP GET /json/close/<id> after the DevTools socket was already gone, which the cloud
*!   reproduced (one tab left after three exports). FIX (HeadlessBrowser.closeTab, used by
*!   the svg and png routes): Page.close over the tab's own socket, then the socket close,
*!   then /json/close as GET and PUT, then a /json/list check with Target.closeTarget over
*!   the browser socket for any survivor. Verified: 3 svg + 2 png exports leave only the
*!   browser's own about:blank anchor tab, which goes when the session browser idles out
*!   (2 min), on `sparkta, closebrowser`, or when Stata exits.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9h-c (2026-09-14, JAVA ONLY, jar rebuilt): export raster at 1x. Fahad's PDFs after
*!   fix9h-b were unchanged. Inside his PDF the cloud is a tiling PATTERN (BBox 1912x954 = the
*!   2x bitmap, XStep/YStep the same, Matrix = the y-flip only) filling the plot: PDFBox
*!   graphics2d draws the image through a pattern whose matrix ignores the current transform,
*!   so neither <image width/height> (fix9g-b) nor an enclosing scale() (fix9h-b) can shrink
*!   it -- the only placement it honours is an unscaled image. FIX: the export clone builds
*!   its raster at the clone's own size (1x; _spkBigStart st.export r=1) and blits it with a
*!   plain drawImage(off,0,0), so intrinsic == display and every renderer agrees. Cost: the
*!   cloud in PDF/PNG/SVG exports is 1x (screen resolution) rather than 2x; SVG size drops
*!   (big05 1.3 MB -> 441 KB). The comments in HtmlGenerator carry the trail; a 2x raster is
*!   possible again only with a PDF renderer that composes the CTM into the image placement.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9h-b (2026-09-14, JAVA ONLY, jar rebuilt): the PDF of a large page drew the raster
*!   cloud at its intrinsic 2x size, spilling past the plot (Fahad's screenshots of the fit
*!   and by() PDFs from the fix9g-c run); the PNG of the same SVG (jsvg -> raster) and rsvg
*!   were right. The fast PDF path is jsvg -> PDFBox graphics2d, which does not honour the
*!   <image width height> downscale canvas2svg emits for a 5-argument drawImage. FIX in the
*!   export clone blit: the downscale is now a ctx.scale() before a 3-argument drawImage, so
*!   canvas2svg writes <g transform="scale(0.5,0.5)"><image width=1968 height=980 ...> -- the
*!   image at its intrinsic size inside a transform, which every renderer honours (the whole
*!   SVG is built on transforms). Verified: rsvg render of the SVG, the session-browser PNG
*!   route (2200x1620, cloud in place); the jsvg/PDFBox PDF itself cannot run in the cloud
*!   (Maven blocked) -- Fahad's rerun checks it. Fallback if PDFBox still misplaces it: emit
*!   the raster at 1x (intrinsic == display size), one line in _spkBigStart (st.export r=1).
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9h (2026-09-14, JAVA ONLY, jar rebuilt; ado header line only): fit lines above the
*!   points, the way Stata draws lfit over a scatter (Fahad's decision after seeing the 100k
*!   fit page, where the line vanished inside the cloud). Since v3.5.57 the fit line was the
*!   point hue at alpha 0.75, dashed, with order:2 -- i.e. UNDER the points (Chart.js draws
*!   the lowest order last). Now: order:-1 (above every point dataset; the CI band keeps
*!   order:3 under everything), colour = an opaque dark accent of the series hue (RGB x 0.55
*!   via HtmlGenerator.darken; with over() each group's line is the dark accent of its own
*!   hue, so it stays attributable), dash [6,3] kept, width 2. The elements-key "Fit:" entry
*!   uses the same accent. Chart.js sorts legend entries by dataset order, which would have
*!   put "Fit" before the data series, so fit() pages get labels.sort by datasetIndex (points
*!   first, fit after) -- scatterLegendCfg(). Applies to the single page, over() groups and
*!   by() panels alike (one builder, buildFitDatasets). Verified on the offline harness page
*!   H-D9 (auto, over(foreign), lfit fitci) and the 100k oracle page: line on top, key and
*!   legend consistent. Gate ALL CHECKS PASSED (viz 0 errors).
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9g-c (2026-09-14, ADO + JAVA, jar rebuilt): javacall argument overflow, found by
*!   Fahad's second run of test_fix9g_v360.do (12/13: fix9g-b's raster exports WORK -- the by()
*!   page PNG and PDF via the fast jsvg/PDFBox path show both clouds, 1.8 MB each). The one
*!   failure was the fit(lfit) fitci 100k page: its saveas SVG took 32.8 s and the PNG failed
*!   rc=2. Diagnosis on his HTML in the cloud: the export SVG had 154,674 paths, 77,284 of them
*!   CI-band fills with "L undefined undefined" -- and the three fit datasets on the page were
*!   each 140 points and CONSECUTIVE CHUNKS of one sequence (ranks 1-3500, 3525-7000, 7025-
*!   10500 of the sorted x). ROOT CAUSE, not fix9g: the ado computes fit() in Stata and passes
*!   up to 4,000 "x,y|x,y" points per series as ONE javacall argument; with full-precision
*!   doubles that is ~150 KB, and Stata splits an argument longer than roughly 5,000
*!   characters into several arguments (~5,280 chars each here), so args 158-185 received the
*!   chunks of the fit line and every later slot shifted (the page still rendered because the
*!   shifted slots are unused on a scatter). Never seen before because auto-sized fits are
*!   short. FIX (ado, 2 places): _maxpts is now a CHARACTER budget -- _fit_budget 3000 divided
*!   by the measured characters per point (x and y of obs 1 / _N at macro precision), min 40,
*!   max 4000; over() divides the budget by the group count (min 20); the thinning step uses
*!   ceil() so the point count never exceeds the cap. A 100k-row double fit now carries ~78
*!   points per series (a straight lfit line and a 78-point CI band look the same; lowess is
*!   coarser but smooth). FIX (Java, DashboardBuilder): args.length > 210 is now an ERROR
*!   naming the longest argument slot, so any future over-long option value fails loudly
*!   instead of rendering shifted garbage. Main program 3,065 code lines (tag 3,060). Gate
*!   ALL CHECKS PASSED. The ado changed, so test_release_v360.do 210/210 is owed again.
*!   Fahad's third run (fix9g-c): 13/13 -- exports 5.1 s, SVG 1.1 MB (115 paths + 1 image),
*!   PDF one page, PNG 1984x1202, all with the cloud; probes green. One thing the review
*!   side caught in his fit page: the thinned line stopped at x = 2.27 while the data run to
*!   4.4 -- every step-th valid row is kept, so with a coarse step the LAST row (the maximum
*!   x) was never sampled (the old step of 25 hid this). Fix (ado, the 4 thinning loops):
*!   the last valid row is always kept, so every fit / CI series spans the full x range.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9g-b (2026-09-14, JAVA ONLY, jar rebuilt): exports of large pages, from Fahad's
*!   real-Stata run of test_fix9g_v360.do (11/13; every on-screen case passed and every note
*!   printed as expected). The two failures were the saveas() cases: the export clone drew the
*!   100k points as vector paths -- 200,098 paths, 52 MB, 11-20 s in the browser -- so the
*!   session-browser SVG hit the 20 s DevTools call cap ("svg via session browser (20029 ms)",
*!   TimeoutException on the first case) and the PNG print swap never signalled; and a 50 MB
*!   SVG / 100k-path PDF is unusable for a paper anyway. FIX: for datasets flagged _spkBig the
*!   export clone (canvas2svg: _spkChartToSvg, _spkPageToSvg, the print swap behind saveas PNG/
*!   PDF) draws the cloud as ONE raster <image> at 2x the export size, built synchronously from
*!   the clone's own elements so it sits exactly on the clone's axes; axes, ticks, text, fit
*!   line, CI band and key stay vector. Measured on the oracle pages: big05 fit+ci SVG 52 MB /
*!   11.4 s -> 1.3 MB / 2.6 s (110 paths + 1 image); big06 by() page SVG 3.6 MB with one image
*!   per panel; bubble 0.8 MB. The real session-browser routes run in the cloud with the
*!   container Chromium (HeadlessBrowser svg + png chart scope): rc 0, 8-11 s each, PNG shows
*!   the cloud in both panels. DevTools budgets raised as headroom (WebSocket call 20 -> 60 s,
*!   page-ready polls 15 -> 45 s) -- a fast page returns at once, only a slow one waits longer.
*!   This supersedes the fix9g line "export clones draw every real point": above 20,000
*!   points the cloud is raster in exports (like matplotlib's rasterized=True), by design.
*!   Found on the way, NOT changed (decision for Fahad): every export clone is drawn TWICE --
*!   new Chart() renders once and _spkChartToSvg/_spkPageToSvg call tmp.draw() again -- so an
*!   all-vector export carries every mark twice (the 200,098 paths above = 2 x 100k) and
*!   translucent fills (alpha 0.85) come out darker than on screen. The big-page raster path
*!   blits once (its flag is not reset on the second draw); the generic double draw touches
*!   every chart's exports and needs its own svg_check sweep before removing the tmp.draw().
*!   NOT testable in the cloud: the fast jsvg/PDFBox path (sparkta-export.jar; Maven is not
*!   reachable from the container) -- jsvg must render the embedded data-URI <image>; the
*!   smoke test asks Fahad to check the fast-path PNG/PDF show the cloud.
*! ---------------------------------------------------------------------------
*! v3.6.0 fix9g (2026-09-14, JAVA ONLY, jar rebuilt; ado header line only): large-data mode for
*!   scatter/bubble. Fahad wants 100,000-row scatters to open easily in v3.6. Automatic above
*!   ChartRenderer.BIG_SCATTER_MIN = 20,000 points per chart (summed over over() groups; a by()
*!   panel is its own chart); no new ado option. What it does: (1) the renderer flags every point
*!   dataset _spkBig:true and emits animation:false for that chart; (2) new global page plugin
*!   spkBigScatter (HtmlGenerator pluginReg, unconditional like spkAspectLock/spkCrisp/spkSnap/
*!   spkAlign) draws the flagged datasets ONCE onto an offscreen canvas -- device-pixel size,
*!   Chart.js' own transform, Chart.js' own per-dataset clip rectangle, every element drawn by its
*!   own PointElement.draw in DATA order -- and blits it 1:1 in beforeDatasetDraw at the place
*!   Chart.js would have drawn the first big dataset (Chart.js draws the last dataset first, so
*!   fit lines / CI bands keep their z-order under the points); the flagged datasets' own draw is
*!   cancelled (return false). Elements keep their data, pointRadius and hitRadius, so tooltips,
*!   mlabel(), fit(), fitci, statistics and filters are untouched; the active (hovered) elements
*!   are drawn live on top in afterDatasetsDraw so the hover highlight still shows. The bitmap is
*!   built in chunks of 20,000 elements (first chunk synchronously, then one per animation frame:
*!   the page stays responsive and the cloud fills in over a few frames) and rebuilt only when its
*!   signature changes (bitmap size, device ratio, chart area, axis ranges, visible big datasets,
*!   their data array identity/length, colour, radius, style) -- i.e. after a filter/slider change,
*!   a resize, a zoom or a legend toggle, never on a hover. Export clones (canvas2svg SVG, saveas
*!   PDF/SVG: no .spk-chart-box parent -- the crisp-stack guard) are untouched and draw every real
*!   point; the toolbar PNG (drawImage of the page canvas) and the browser full-page PNG contain
*!   the blitted cloud. (3) Stata note via SFIToolkit.displayln: "large-data mode: N points [in a
*!   panel] drawn once, no animation (hover and export unchanged)".
*!   DEVIATION from the handover, with evidence: the handover asked for pointRadius:0 on the big
*!   datasets and for sorting the points by x with normalized:true. pointRadius:0 is not needed
*!   (beforeDatasetDraw returning false already makes the Chart.js render pass a no-op) and would
*!   have leaked into the export clones, which share the dataset objects. Sorting by x CHANGES
*!   THE PICTURE: the default marker fill is translucent (rgba alpha 0.85, 0.1-alpha border), so
*!   in a dense cloud the topmost points decide the look, and x-sorted drawing put every column's
*!   largest-x points on top -- vertical streaks and a darker cloud (197,943 of 1,084,860 canvas
*!   pixels differed from the pre-fix render, max channel delta 141). Data order kept; normalized
*!   dropped (it only skips Chart.js' sortedness scan, milliseconds). "Order is irrelevant for
*!   scatter" is false for translucent overlapping markers -- recorded in project memory.
*!   OFFLINE ORACLE (built first, per the handover): verify/harness/HarnessBig.java renders nine
*!   pages on a seeded synthetic fixture (verify/sfi-stub Data.setBig(n, seed): mpg = x ~ N(0,1),
*!   price = x + noise, weight 1..10, foreign 0/1, rep78 1..3) -- 100k plain, over(rep78), filters,
*!   bubble, fit(lfit) fitci, by(foreign), 5k control, 20,001 edge, by()+filter;
*!   verify/bigscatter_probe.js (Playwright, container Chromium, ratio 1.5) hooks Chart.prototype
*!   draw/update before Chart.js loads, times page load, the draws in the settle window, the draws
*!   ONE dispatched mousemove over a point triggers (Chart.js throttles DOM events to rAF, so the
*!   cost lands in the following frames -- the first probe cut measured 0.3 ms and 0 draws, wrong),
*!   the tooltip opacity at 450 ms, a filter change, and screenshots the canvas at rest for a
*!   pixel comparison against the pre-fix render. SPK_STUB_ECHO=1 makes the stub echo Stata notes.
*!   MEASURED (headless Chromium, no GPU; 100,000 points; ms):
*!     page                 load before/after   hover(1 move) before/after   tooltip@450ms before/after
*!     big01 scatter        8645 / 2713         5044 (4 draws) / 7               0.60 / 1
*!     big02 over(3)        8756 / 2675         4947 / 20                        0.61 / 1
*!     big03 filters        8594 / 2642         4892 / 8   (filter change 726 / 628)   0.98 / 1
*!     big04 bubble        14464 / 7009         7723 / 71                        0.00 / 1
*!     big05 fit+ci         8551 / 2699         4887 / 10                        0.61 / 1
*!     big06 by(2 panels)   7859 / 2697         2153 / 6                         0.95 / 1
*!     big08 20,001 edge    2065 / 770          1648 / 5                         0.94 / 1
*!     big07 5,000 control   852 / 864           97 / 108  (below threshold: unchanged)
*!   One pre-fix chart draw stroked 100k arcs through the page ctx in ~1.2 s (bubble ~2.5 s); the
*!   load sequence ran 6 such draws and every hover-state change 4 more -- that was the lag. After:
*!   one bitmap build of ~2.1 s (bubble 6 s) in 20k chunks, then every draw is one drawImage.
*!   Fidelity: canvas at rest after vs before -- 1.6-1.9 per cent of pixels differ by 1-2/255 (a
*!   handful 3-15/255), max 15: the 8-bit rounding of compositing translucent markers through a
*!   transparent bitmap; the pass rule in the probe is max delta <= 16 and <= 5 per cent of pixels
*!   (the x-sorted regression above showed as 141 over 18 per cent). Also checked on the after
*!   pages: _spkChartToSvg clone emits 200,098 paths (every point, 52 MB, 11 s -- the SVG export
*!   cost is unchanged from before); hover highlight drawn (active elements r=6 on top); legend
*!   toggle and viewport resize rebuild once and complete; filter change rebuilds (49,923 points);
*!   by()+filter page passes byfilter_probe --embedded; 0 JS errors on every page.
*!   GATE: verify_before_zip.py ALL CHECKS PASSED (harness 73/73, js_check/pixels/svg/latex green,
*!   viz 0 errors, dist/ado jars byte-identical, class 55); HarnessByFilter 54/54 + byfilter_probe
*!   54/54. NOT RUN: Stata (the pipeline has none) -- real-Stata smoke test requested.
*!   Known: hit-testing still walks all points (~4 ms per move, left alone); the SVG/PDF export of
*!   a 100k page is slow by construction (vector, every point) -- v3.7 7a binning is the answer.
*! ---------------------------------------------------------------------------
*! v3.6.0 RELEASE TAG (2026-09-13): the fix9f build tagged as v3.6.0 for GitHub and SSC. No
*!   chart/Java logic change. Version string "3.6.0-t2j" -> "3.6.0" in the ado local
*!   sparkta_version and HtmlGenerator.VERSION (the two must agree: VersionCheck handshake), so
*!   the jar was rebuilt (both dist/ and ado/ copies, byte-identical). Every component ado
*!   header now reads "*! sparkta version 3.6.0 2026-09-13"; the sparkta.ado *! header is
*!   collapsed to six lines (this file holds the history). `version 17` added to the six
*!   helper ados that lacked it (SSC requires a version statement in every ado file):
*!   sparkta_browser_run, sparkta_event_times, sparkta_find_browser, sparkta_matrix_sort_time,
*!   sparkta_mg_varlabel, sparkta_mg_vlabel. sparkta.pkg Distribution-Date 20260913; sthlp
*!   header date; README version badge/lines (3.5.111 -> 3.6.0, Java 8+ -> Java 11+ because the
*!   jar is compiled with --release 11 for Stata 17). SSC submission notes in docs/SSC_SUBMISSION.md.
*! ---------------------------------------------------------------------------
*! v3.6.0 docs pass 1 (2026-09-13, sparkta.sthlp only; no ado/Java change): help file rebuilt by
*!   verify/build_sthlp.py. Fixes from Fahad's review: six places still said online was the
*!   default and offline an option (self-contained has been the default since t1k); a {cmd:...}
*!   wrapped over a line break rendered as a stray brace; two garbled synopt lines; filter()/
*!   filter2() leftovers; "all 149 options" (215). Post-estimation types were absent from the
*!   chart-type list -- new catalogue with data AND post-estimation types (what each needs and
*!   draws), a Post-estimation section (coefplot / marginsplot / eventstudy inputs, matrix()
*!   grammar, results() with the marginsplot column map, event-time name patterns, refperiod/
*!   together/separate), colour grammar + palette(), saveas() scopes and the export layout rule,
*!   online/offline rewritten, stale methods (Sturges bins, Scott bandwidth, Cornish-Fisher t)
*!   replaced by what the code does. The option table is generated from the ado syntax block
*!   and the build asserts every option is listed.
*!   docs pass 1b (same day): the first cut printed every {synopt:} raw in Stata -- the generator
*!   joined sections with ONE newline, so the Options paragraph was still open when {synoptset}
*!   began (SMCL prints block directives inside an open paragraph verbatim). Fixed (sections joined
*!   by a blank line) and made impossible to ship again: new verify/smcl_check.py models paragraph
*!   state, synopt/p2col context, per-line brace balance, directive names and helpb anchors; it
*!   runs inside build_sthlp.py and as verify_before_zip stage 4b. It reproduces both of Fahad's
*!   reports on the old files (the raw synopts on cut 1, the wrapped {cmd:} on the pre-docs help).
*!   docs pass 1c: Fahad's line-by-line review -- long first-column entries pushed the second column
*!   to the next line (ci((2 3)) ci(2 3) ci(ll ul); pointstyle(circle|...|triangle)), option groups
*!   ran into each other, and {opt} with nested parentheses/quotes is unreliable. New
*!   verify/smcl_render.py lays the file out like the Viewer (paragraphs, p2col, synopt tables) so
*!   the result can be READ as text and audited: first-column overflow, missing blank line before a
*!   {syntab}, any brace left in the rendered text. Fixes: synopt table {cmd:}{it:} only, width 42,
*!   long specs shortened with the detail moved to the description, blank line before each group
*!   (as Stata's own help files do), p2col widths raised where an entry overflowed, utilities as a
*!   p2col table. Both audits run inside build_sthlp.py; stage 4b of verify_before_zip runs the check.
*!   docs pass 1d: one brace was still on screen -- "{cmd:lwdid}) via {cmd:" vanished from a 304-
*!   character {p2col:} source line: the Viewer corrupts a source line past roughly 250 characters
*!   (the old help never exceeded 170). build_sthlp.py now folds every line over 150 characters at
*!   a space outside any directive; smcl_check.py errors on any line over 240.
*! ---------------------------------------------------------------------------
*! v3.6.0-t2j fix9f (2026-09-13, Java + ado; jar rebuilt; header -fix8r narrative): Fahad asked
*!   whether the fix9e two-across export is optional and whether the stacked look can still be
*!   exported. Now: no layout() -> stacked on screen, two across in every export (PNG browser
*!   route, PDF, SVG/fast route); layout(vertical) written out -> stacked everywhere;
*!   layout(grid|horizontal) unchanged. Mechanics: the ado no longer substitutes "vertical" for
*!   an empty layout() (validation adjusted; DashboardBuilder still defaults the empty value to
*!   vertical, and records layoutExplicit); HtmlGenerator adds class panels-keep to an explicit
*!   vertical container, the two fix9e CSS rules use .panels-vertical:not(.panels-keep), and
*!   _spkPageToSvg uses one column when .panels-keep is present. Harness: H-D11 is now the
*!   default-layout page (exports 1100x1715 grid), new H-D12 layout(vertical) (exports
*!   1100x5770 stacked); SVG route: H-D11 two columns, H-D12 one column.
*! ---------------------------------------------------------------------------
*! v3.6.0-t2j fix9e (2026-09-13, Java only; jar rebuilt; ado header only, -fix8t/8s narrative):
*!   Fahad's browser-route run on Windows Edge (sparkta-export.jar renamed away) confirmed the
*!   fix9d full-page PNG (coefplot with key, 5-panel box grid, "png via session browser, full
*!   page at 2.0x"), and showed the one difference between the two PNG routes: the browser
*!   route captured the by() page as laid out on screen -- layout() defaults to vertical, one
*!   panel per row -- while the SVG/Java route and the PDF print CSS put panels two across.
*!   Fahad: the grid reads better. HtmlGenerator CSS: the @media print two-column rule now
*!   includes .panels-vertical, and the on-screen chart-only mode (what the PNG capture uses)
*!   forces every panel container to a two-column grid. New harness page H-D11 (box by rep78,
*!   layout(vertical)); Linux check: chart PNG 2200x3436 two across, PDF two across.
*! ---------------------------------------------------------------------------
*! v3.6.0-t2j fix9d (2026-09-13, Java + ado; jar rebuilt; header -fix8v narrative): Fahad's
*!   decisions on the six pre-tag follow-ups (1b, 2a, 3 keep, 4a, 5a, 6 yes+yes).
*!   (5a) saveas() PNG: new HeadlessBrowser command `png <exe> <page> <out> [scale] [scope]`
*!   -- opens the page at 1100 CSS px in the session browser, waits for its own print swap
*!   (title SPARKTA_PRINT_READY: canvases replaced by their SVG, nothing mid-animation), reads
*!   Page.getLayoutMetrics and captures beyond the viewport at scale(#). The ado's png branch
*!   calls it first and keeps the one-off `--screenshot` run as the fallback. Before, without
*!   sparkta-export.jar (never in the .pkg; Mac/Linux builds had no way to make it) the PNG
*!   was the 1100x700 window: post-estimation pages lost the elements key, by() grids the
*!   lower panels. Linux check: eventstudy 2200x1462 with the key, 6-panel box grid
*!   2200x3436 complete, page scope at 3x, table scope at 1x.
*!   (2a) Violin filtering: every _violinData entry now carries gi (GLOBAL over-group index,
*!   -1 without over()) and vi (plot-variable index); _vFilter accepts a (gi, vi) -> values
*!   function and walks _vOrigData, so the single page (FilterRenderer box/violin branch) and
*!   the by() panels (_updatePanelChart) recompute EVERY violin. Before, one value list per
*!   over group of the FIRST variable was passed and `groupVals.map` produced that many
*!   violins -- a two-variable violin lost its second violin on any filter change (BF40).
*!   Multi-var over() panels render LOCAL groups (DatasetBuilder), so their gi is looked up
*!   in the global list. Verification: `window._vTarget[_N]()` exposes the entries the chart
*!   is animating to; byfilter_probe.js checks n / median / mean of every entry against its
*!   own rows (a first-variable-only page now FAILS: 7 entries wrong); new sweep case BF54
*!   (violin 2 vars over rep78 by foreign); new harness pages H-F15/H-F16 (single page, 2
*!   vars, without/with over()) checked by verify/violin_single_check.js (6 + 24 checks).
*!   (1b) ado note when stackedbar100/stackedhbar100 is used without over() (plain mean bars,
*!   unchanged); (4a) ado note listing by()/filters()/sliders() when given with coefplot/
*!   marginsplot/eventstudy (they read estimation results; the options were silently ignored
*!   -- the chart is still drawn, no error). (6) sparkta_shell_quiet.ado removed from ado/ and
*!   sparkta.pkg (nothing has called it since t1c's ProcessBuilder route: 22 ado files now);
*!   build.sh Step 5b + fetch_js_libs.sh J1-J5 mirror build.bat's optional sparkta-export.jar.
*!   (3) panel group layout left as is by decision.
*! ---------------------------------------------------------------------------
*! v3.6.0-t2j fix9c (2026-09-13, Java only; jar rebuilt; ado header only, -fix8u narrative):
*!   release-gate item 4, the Mac/Linux saveas() leg exercised for the first time (cloud Linux,
*!   shipped dist jar + Chromium 1194, no Stata): HeadlessBrowser svg (DevTools session), run
*!   --print-to-pdf (chart + page + table scopes), --screenshot at scale 2, runcapture --dump-dom
*!   + SvgExtract, FileCopy with a space in the name -- all rc 0, vector PDFs. Two cross-platform
*!   defects seen in the output: (1) `saveas(f.pdf)` (chart scope) on a post-estimation page
*!   printed the publication table as page 2 -- the U1 PubTable block is `.spk-pub-wrap` and the
*!   chart-only CSS only hid `#spkTableBlock`; (2) every chart-scope PNG and PDF carried a stray
*!   "Export:" label -- the toolbar rows hid their buttons but not the label (toolbar default on
*!   since fix8w made this visible on every export). Fix (HtmlGenerator only): both toolbar
*!   rows get class `spk-toolbar`; chart-only (print AND on-screen) hides `.spk-pub-wrap` and
*!   `.spk-toolbar`; table-only hides `.spk-toolbar`. Re-run: eventstudy chart PDF 1 page, no
*!   label in any PDF/PNG, table scope unchanged (1 page, table only). NOTED, not changed:
*!   without sparkta-export.jar (never in the .pkg; java/build.sh has no Step 5b) PNG is a
*!   1100x700 viewport screenshot, so a tall page (post-estimation elements key, by() grids)
*!   is cropped; sparkta_shell_quiet.ado is shipped but no longer called.
*! ---------------------------------------------------------------------------
*! v3.6.0-t2j fix9b (2026-09-13, Java only; jar rebuilt; ado header only, -fix8w narrative):
*!   from Fahad's real-Stata run of test_byfilter_v360.do (66/66: 61 pass + 5 expected failures)
*!   and test_release_v360.do (210/210) reviewed with `byfilter_probe.js --embedded` (59/62 pages
*!   clean on first pass). ONE product defect: r_bf_n02 (scatter wage tenure by(union)
*!   filters(industry)) drew 1,407 points after ANY filter change against 1,398 at load -- the
*!   ungrouped branch of sparkta_engine.js buildScatterPoints tested x/y for null/undefined only,
*!   but base64 float64 columns decode a Stata missing as NaN, so the 9 nonunion rows with tenure
*!   missing became {x:NaN} points (single-page scatter + filter had the same leak). It now reads
*!   through _sdVal like the grouped branch, bubble and fit builders already did. Proven on a
*!   patched copy of Fahad's page (probe clean). The other two were probe artifacts, fixed in the
*!   probe: histogram truth binned edge-by-edge (a wage value sitting on an edge went to the other
*!   bin; the render and the filter both use floor over an equal width) and the coefplot
*!   observation page has no by() panels. OBSERVATION: by()/filters() on a coefplot are silently
*!   ignored (rc 0, plain coefplot) -- an ado note or error is a follow-up for Fahad's decision.
*! ---------------------------------------------------------------------------
*! v3.6.0-t2j fix9a (2026-09-13, Java only; jar rebuilt; ado body unchanged, header +fix9a
*!   -fix8n narrative): roadmap item 3 -- by()+filter over ALL chart types.
*!   METHOD: new offline sweep verify/harness/HarnessByFilter.java (53 by() pages: every data
*!   chart type x filters()/sliders(), with/without over(), 1 and 2 vars, stat(sum|median|count),
*!   showmissing, noallfilter, filter on the by() var, two filters) rendered through the real
*!   DashboardBuilder on the SFI fixture (the stub now emulates the ado markout: Data.markoutRep78),
*!   then verify/byfilter_probe.js drives EVERY dropdown value (and a slider range) in headless
*!   Chromium and compares each panel's datasets with an independent recomputation from the
*!   fixture (means/sums/medians per var x group, box medians, cibar/ciline means, histogram bins
*!   from the panel's own edges, pie shares, 100% shares, scatter point counts, n badges, JS
*!   errors). Baseline fix8z: 20 of the first 39 pages FAILED.
*!   ROOT CAUSE (FilterRenderer.buildFilterScriptByPanels, _updatePanelChart): the by()-panel
*!   updater knew three layouts -- scatter/bubble, boxplot/violin single var, and a generic
*!   "one value per dataset" bar/line branch -- and every other type fell into the generic
*!   branch, which wrote the wrong shape: multi-var bars (one dataset, one value per var) became a
*!   single bar; multi-var over() bars/lines/areas (one dataset per var, one value per group)
*!   collapsed to one value each; stacked and 100%-stacked bars the same (no renormalisation);
*!   pie/donut (one slice per var) lost a slice; cibar received plain numbers where
*!   chartjs-chart-error-bars expects {y,yMin,yMax} and THREW inside Chart.js, so the whole filter
*!   change died (no panel, badge or obs count updated); ciline (3 datasets per var) and
*!   histogram (bin counts) were overwritten with a single mean; boxplot over() wrote group 0 only
*!   (the fix8f single-page bug, never ported to panels); scatter/bubble over() mapped GLOBAL group
*!   index to a panel's PRESENT-groups datasets, so a panel missing a group (Foreign has no rep78
*!   1-2) drew the wrong group's points; fit lines / CI bands in panels were never rebuilt.
*!   FIX: _updatePanelChart now mirrors DatasetBuilder's panel layouts per type (over() groups
*!   are globally aligned for bar/line/area/box; PRESENT-only for scatter, stack100, cibar
*!   (n >= 2) and ciline, so those are slotted by the panel's own labels), recomputes cibar/ciline
*!   CIs with the engine's exact t critical, recounts histograms into the panel's own bins (new
*!   _binEdges_<id> array emitted by ChartRenderer.histogram, per-bin tooltip counts refreshed),
*!   renormalises 100% stacks over the groups the panel shows, rebuilds fit lines and fitci bands
*!   per panel (per group with over()), and keeps violin on its own _vFilter_N path.
*!   ALSO: pie/donut raw slice values now live on the dataset (_spkRaw; tooltip reads it first) --
*!   `var _spkPieRaw` was one page global, so every by() panel's tooltip showed the LAST panel's
*!   raw values. And stackedbar100 WITHOUT over() is rendered as plain aggregates (ChartRenderer
*!   picks numDatasets); the single-page filter's CASE B branch rewrote those bars into shares on
*!   the first filter change -- it now stays on the generic branch (consistent with the render).
*!   RESULT: 53/53 sweep pages pass every dropdown value; standard gate rerun.
*!   FOLLOW-UPS (not blockers): stackedbar100 without over() has nothing to stack -- decide
*!   whether it should draw ONE 100% column (renderer change) or warn like a single-var stacked
*!   type; violin filtering (single page and panels) recomputes the FIRST plot variable only;
*!   the by()-panel cibar/ciline/scatter/stack100 "present groups only" layout differs from the
*!   bar/box "globally aligned" layout (cosmetic inconsistency, pre-existing).
*! ---------------------------------------------------------------------------
*! v3.6.0-t2j fix8z (2026-09-13, ado only; jar unchanged): sparkta_read_results_margins drops the
*!   rows of a MAPPED table whose condition value (_m#, _at) is missing, with a note -- collapse
*!   keeps the rep78==. group and Fahad's t_map.html drew it as a sixth point labelled "r6".
*!   margins, saving() files (nothing mapped) are untouched. r_res_mp_map_at ("invalid syntax",
*!   rc=198) ROOT CAUSE from Fahad's trace: `capture drop _at` ran before _at existed; Stata's
*!   drop treats a non-existent name as an abbreviation and dropped the unique match _at1, so the
*!   at() grid was empty (J(6, 0, .)). New helper sparkta_results_mp_dropx (confirm variable, exact
*!   -> drop) replaces every drop in the reader; the _at#/_m# existence checks are exact too.
*!   LESSON (engineering rule): never `capture drop <name>` a name that may not exist when a
*!   longer name shares its prefix -- confirm exact first.
*!   Second finding from t_map_at.html: the Foreign series was shifted one condition LEFT (its
*!   mpg5=35 mean drawn at 30, and an empty 35 column in the table). The downstream reader maps
*!   cells by position in at-major order, as margins always lays them out; a user table can be
*!   sparse (Foreign had no n>=2 row at mpg5=10) and unsorted. Mapped tables are now completed
*!   with fillin over the condition columns (empty cells = non-estimable gaps, a note says how
*!   many) and sorted by them.
*!   Third finding (same page, ad hoc vs suite): series read "foreign=0/1" or "Domestic/Foreign"
*!   depending on the dataset in memory. The source's value labels (mapped factor levels) and
*!   variable labels (at()/factor names) now travel in $SPK_MG_VLABS / $SPK_MG_VARLABS (set by the
*!   results reader, cleared by sparkta_margins_keep); sparkta_read_margins' four label lookups and
*!   the main program's x-title use helpers sparkta_mg_vlabel / sparkta_mg_varlabel (source first,
*!   memory second). Harness H-T41..T45 sweep every bar-coefplot CI variant for the hollow-bar key.
*!   The two helpers live in their OWN files (sparkta_mg_vlabel.ado, sparkta_mg_varlabel.ado, in
*!   sparkta.pkg): a program defined inside another ado is only known after that file loads, and
*!   Fahad's run hit "command sparkta_mg_varlabel is unrecognized". LESSON: one public program per
*!   file, always. Also: Stata loads ado files from the CURRENT directory before PERSONAL -- stale
*!   copies in Downloads shadowed the installed ones (`which` shows ".\name.ado").
*! ---------------------------------------------------------------------------
*! v3.6.0-t2j fix8y (2026-09-13, ado only; jar unchanged from fix8x):
*!   Fahad's fix8x smoke test: `results(t_tidy, factors(rep78))` failed as intended (rc=198 with
*!   the grammar) but printed "columns autodetected: se=sd" -- the keyword picker used
*!   `confirm variable sd`, which accepts ABBREVIATIONS, so it matched the column "sdm". Both
*!   pickers (sparkta_read_results_margins: sparkta_results_mp_pick; sparkta_read_results:
*!   the coefplot/eventstudy role loop) now use `confirm ... variable, exact`. The margins
*!   diagnostic names the column actually taken. Header: +fix8y, -fix8k narrative.
*! ---------------------------------------------------------------------------
*! v3.6.0-t2j fix8x (2026-09-13, results() column mapping for marginsplot; ado + Java; jar rebuilt):
*!   Ado: sparkta_read_results_margins.ado takes b() se() ci(lo hi) pvalue() at(varlist)
*!   factors(varlist) and clones those columns onto _margin/_se_margin/_ci_lb/_ci_ub/_pvalue/
*!   _at1../_m1.. (the variable names become the at()/factor names; a factors() word that is
*!   not a variable keeps its old meaning, the name of an existing _m#). When _margin is absent
*!   and b() is not given, the statistic columns are autodetected from the matrix()/results()
*!   keyword lists (b coef estimate ... / se stderr ... / ll lb lower ... / ul ub upper ... /
*!   pvalue p pval ...) with a diagnostic line; a table with neither _m1/_at1 nor factors()/at()
*!   errors with the mapping grammar. sparkta_read_results.ado forwards the suboptions (AT()
*!   added; FACtors() is a string now) and reads frame:name sources from a COPY of the frame
*!   (frame copy + frame drop) because the map adds variables. sparkta.ado: header only.
*!   Java: coefstyle(bar) -- fix8w put a "Hollow marker: p > 0.1" key on the bar coefplot, but
*!   with the default cistyle(whisker) (and band/area/noci) the bar chart draws no marker at
*!   all (t_coef_bar smoke test). Bars now carry the significance: p > 0.1 bars are outline-only
*!   (per-bar backgroundColor from _cpSig) and the key entry is "Hollow bar: p > 0.1" with a
*!   new "hollowbox" glyph (HTML key + PNG/SVG/PDF export key).
*!   Suite/tooling: test_release_v360.do r_annot_full used apoint(y x|y x) -- apoint() is
*!   space-separated pairs (rc=198 in the fix8w run; the only non-clean case); fixed the
*!   do-file. intent_check.py: single-model horizontal coefplot is a scatter since fix8n
*!   (38 stale "expected bar" fails), violins list no Outliers key (fix8w). expectations.json:
*!   coefplot pages type scatter, violin plugin list without vInlineLegend, contrast labels "3"
*!   (fix8w bare levels), palette(okabe) snapshot expects Okabe-Ito colour 1 = black.
*! ---------------------------------------------------------------------------
*! v3.6.0-t2j fix8w (2026-09-12, batch 2 UX pass; ado + Java; jar rebuilt):
*!   Ado: sparkta.ado +NOTOOLbar option; is_download defaults to 1 (toolbar on every chart),
*!   notoolbar sets 0, download is a no-op alias, saveas() still forces 1.
*!   sparkta_read_margins.ado: unlabelled factor levels on the x axis are the bare value
*!   ("1", "2"; was "rep78=1") in the r(at) categorical path, the contrast path and the
*!   plain N.varname path; the x title already carries the variable label (s8x). Series
*!   (legend) fallbacks keep "varname=level".
*!   Java ChartRenderer: SIG_ALPHA = 0.10 -- coefficient markers filled when p <= 0.1,
*!   hollow (plot-background fill, 2 px ring in the series colour) otherwise, p missing =
*!   hollow (base levels). One JS helper _spkSigDot() feeds every plugin-drawn dot (cpDot
*!   whisker/levels2/band/bar variants, cpMDot multi-model); real Chart.js points (vertical
*!   single coefplot line, multi-model scatter, event-study pre/post scatter) get per-point
*!   pointBackgroundColor lists + _legendFill, and _spkSigLegend restores the series colour
*!   on legend swatches (usePointStyle copies point 0's fill). Key entry "Hollow marker:
*!   p > 0.1" (outlier glyph) registered only when a hollow marker exists.
*!   marginsPlot: value axis now Stata-like -- bounds hug the data (cushioned by
*!   plotmargin), ticks are the nice-step multiples inside the bounds (was: axis extended
*!   to the nice floor/ceiling, so predicted prices with lowest CI 1,352 started at 0; the
*!   side-by-side r_mp_t4 showed Stata at 2,000). Bars keep the zero floor. Tooltip title
*!   for categorical x is "<x title>: <level>" ("Repair record 1978: 1").
*!   legbgcolor(): labels.backgroundColor is a Chart.js no-op; the value is now emitted as
*!   labels.spkBg and HtmlGenerator's global spkLegendBg plugin paints a rounded box hugging
*!   the union of the legend hit boxes (beforeDraw, then chart.legend.draw() on top).
*!   buildSharedKey: box key has ONE "IQR box" entry (Fahad: never one per colour).
*!   Verified in place, no change needed: post-est pages have no stats panel (the
*!   publication table is the panel); show()/omit()/nobase filter in sparkta_read_eresults
*!   before chart and table split; marginsplot reads cicolors() (t2g fix 3); predictive
*!   margins never forced to zero (s8b).
*!   Verify: Harness a[115]="1" default (H-D1 = notoolbar page), new H-D10 legbgcolor and
*!   H-T39 marginsplot cicolors scenarios, mp harness labels "1..5" + a[5] x title;
*!   intent_check toolbar rules (default on, notoolbar), rep78 label rule; expectations
*!   r_mp_t1_factor labels/tooltip; svg_check registers chartjs-chart-error-bars and skips
*!   by() grids (single-config extraction cannot resolve panel slices; by() exports checked
*!   in a real browser: every series colour present); viz_audit AX-ANCHOR accepts
*!   data-spanning axis BOUNDS (marginsplot) as well as anchored ticks; test suite
*!   +r_export_notoolbar. Gate ALL CHECKS PASSED (63 harness, svg 55/55, viz 0 errors).
*! v3.6.0-t2j fix8v (Java only; ado body byte-identical to fix8u, header +1 entry):
*!   Fahad's real-Stata check of fix8u: bar/scatter/pie/by-panels/box crisp; the violin at
*!   browser zoom 250% still looked soft AND its on-canvas key (Median/Mean/IQR Box/
*!   Whiskers/KDE Shape, drawn inside the plot area at the top right) covered violins 4-5.
*!   KEY, universal rule: no legend is drawn on the canvas inside the plot area any more.
*!   Post-estimation charts already used the HTML elements key (CANVAS_CI_KEY=false, s9j);
*!   box/violin single pages now emit the same HTML shared key the by() grid uses, above
*!   the chart (HtmlGenerator.build -> buildSharedKey when isBoxOrViolinType). The
*!   _vLegendPlugin / _bpLegendPlugin definitions stay in the page but are not registered.
*!   Violin key corrected while there: its coloured swatches are the DENSITY shapes, the
*!   IQR box is one neutral outlined box (plugin colours), and violins draw no outliers --
*!   the shared key used to list "IQR box: <group>" per colour plus "Outliers" for violins
*!   (by() pages too). Box key unchanged (per-group IQR box colours, Whiskers, Outliers).
*!   HTML text reflows at any zoom and is always sharp; wraps to two rows when narrow.
*!   CRISP STACK hardening (no headless repro of the 250% softness -- emulated Ctrl-+ steps
*!   1.5 -> 1.65 -> 1.875 -> 2.25 -> 3 -> 3.75 were all aligned -- so the stack now
*!   guarantees the state instead of trusting the events):
*!   - 110% on a 150% display = ratio 1.649999976: q was 0 (cap 10, tolerance 1e-6) so the
*!     canvas was not aligned at that step. _spkQ now tolerance 1e-3, cap 20 (q=20 there).
*!   - Chart.js bitmap = floor(css * ratio): 1120 * 1.649999976 = 1847.99997 -> 1847 while
*!     the display rect is 1848 device px (resampled again). _spkCrispRatio returns the
*!     native ratio + 1e-6 so floor lands on the integer; transform error ~1/1000 px.
*!   - Self-healing guard (spkAlign beforeDraw): each draw checks currentDevicePixelRatio
*!     == wanted ratio and that the canvas' rounded CSS rect is on the device grid with
*!     bitmap == display * ratio; if not, ONE _spkAlignAll (ratio re-apply + re-align) is
*!     scheduled via setTimeout, at most 4 per device-ratio value (no redraw loop). Proven
*!     by sabotage: ratio forced to 1 + style broken through Chart.js' own resize -> one
*!     heal restored 1.5 / 1722x858 / max-width 1148px. _spkAlignAll always re-applies
*!     the ratio (it used to only when devicePixelRatio had changed since the last apply).
*!   PROOF: 80/80 on-page canvases of 61 harness pages aligned at 1.5, 1.65, 1.25, 1.0,
*!   2.5; grid rows single-pixel at 1.65 and 1.5; tooltips fire (scatter/box/box-by/bar,
*!   all violins); alignment holds through viewport resize, reflow and 1.5->2.5->1.5;
*!   narrow-width (250%-equivalent) violin and box render with the key above the plot.
*!   Gate: verify_before_zip ALL CHECKS PASSED.
*! v3.6.0-t2j fix8u (Java only; ado body byte-identical to fix8t, header +1 entry):
*!   "Elements still look blurred" after fix8t (violin box, scatter markers, grid) on the
*!   150% display. MEASURED (headless Chrome at device pixel ratio 1.5, column profile of
*!   the fix8t crisp_violin.html): grid rows at the top of the plot single-pixel (220), from
*!   mid-plot down 2-row pairs (225/248, 232/241) -- the signature of a bitmap being
*!   STRETCHED, not of soft drawing. Chart.js sized the canvas to 1150 x 575 css px (bitmap
*!   floor(575 * 1.5) = 862 rows) inside a box at css top 115.58; the display rect was 863
*!   device rows at a fractional offset, so the browser resampled the whole bitmap. spkSnap
*!   could not help: it snaps inside a bitmap that is then scaled.
*!   ISOLATED the browser rule with a bare <canvas> (no Chart.js): Chrome rounds a replaced
*!   element's rect to whole CSS px FIRST and multiplies by the ratio after. At 1.5 a canvas
*!   at css top 135 lands on device row 202.5 (every line a 127/127 pair); at css top 134
*!   (even) with an even css height it is single-row sharp. So 1:1 blitting needs css
*!   top/left/width/height all multiples of q = smallest integer with q * ratio integer
*!   (2 at 150%, 4 at 125% and 175%, 1 at 100%/200%) and bitmap == css * ratio exactly.
*!   FIX spkAlign (HtmlGenerator pluginReg, registered after spkSnap): afterInit wraps the
*!   chart instance's resize() so that BEFORE every Chart.js resize (ResizeObserver, zoom
*!   re-apply, our own) the canvas gets style.left/top = the sub-pixel nudge that puts its
*!   rounded css position on a multiple of q (document coordinates) and max-width/height =
*!   the largest multiples of q inside the box; Chart.js honours those caps in
*!   getMaximumSize, so chart.width/height, the forced css style and the bitmap agree. A
*!   <body> ResizeObserver re-aligns every chart when a reflow moves the box. No q <= 10
*!   (odd zooms): styles cleared, old behaviour. Hit-testing unchanged (display size ==
*!   chart.width/height; violin custom tooltip uses the same css space).
*!   Ratio rule (spkCrisp): native whenever q exists and ratio != 1; 2 at 100% (exact 2:1
*!   downscale) and when no q. spkSnap now snaps in SCREEN pixels: unit = ratio / dpr when
*!   that is an integer (2 at 100%), so a 1 css px line is 2 bitmap px on even edges = one
*!   sharp screen pixel after the downscale. Exports (saveas/download clones) untouched.
*!   PROOF (headless Chrome, harness pages): grid lines exactly 1 device row (217) at 1.5,
*!   1.0 and 1.25, exactly 2 at 2.0 and 1.75; violin box outline 3 solid rows at 1.5; all
*!   80 on-page canvases of the 61 Harness pages + 39 of the 36 HarnessSweep pages aligned
*!   (rounded device origin, bitmap == display) at 1.5 / 1.0 / 1.25, 0 page errors;
*!   alignment holds through a viewport resize, a 13.3 px reflow above the chart, and a
*!   live ratio change 1.5 -> 2.5 -> 1.5; Chart.js tooltips fire (scatter/bar/box/pie) and
*!   all violin custom tooltips fire (one by() panel violin non-hoverable = pre-existing,
*!   identical without spkAlign). Gate: verify_before_zip ALL CHECKS PASSED.
*!   Real-Stata check (150%): rebuild crisp_violin / crisp_scatter with the fix8u jar; the
*!   violin box, grid and markers must be single sharp lines at 100% and after Ctrl-+.
*! v3.6.0-t2j fix8t (Java only; ado body byte-identical to fix8s, header +1 entry):
*!   Tier A of "crisp at every scale" (Fahad's display is 150% = device pixel ratio 1.5).
*!   Finding: fix8s' max(dpr,2) rule made Chrome DOWNSAMPLE 2 -> 1.5 (non-integer), which
*!   smeared every hairline; measured a grid line at 2 device px of 220/235 grey. A 1 css px
*!   line is 1.5 device px and can never be crisp unless drawn at a whole number of device
*!   pixels ON the device grid -- which nothing in the pipeline did (Chart.js aligns its grid
*!   to the css grid with a 0.5 css min half-width; sparkta painters not at all).
*!   D1 ratio rule (spkCrisp): native dpr when dpr >= 1.5 (no resampling, snapping lands
*!      exactly); 2 when below (100%: integer downscale; 90/125%: near-integer, text
*!      supersampled).
*!   D2 spkSnap global plugin (HtmlGenerator pluginReg): afterInit installs own-property
*!      wrappers on the chart's 2D context (only canvases inside .spk-chart-box; export
*!      clones untouched). beginPath/moveTo/lineTo/rect/closePath are BUFFERED and replayed
*!      at stroke/fill/clip: lineWidth -> whole device px (css<=1: floor(d+0.25) min 1, else
*!      round); each horizontal/vertical segment moved onto pixel centres (odd width) or
*!      edges (even width / fills). Any curve op (arc/arcTo/ellipse/bezier/quadratic/
*!      roundRect) marks the path curved and disables snapping for that path; Path2D
*!      arguments, text and anything under a rotation/skew transform pass through; fillRect/
*!      strokeRect snap directly without touching the path in progress (canvas spec).
*!      Covers Chart.js grid/border/bars, chartjs-chart-boxplot, and all sparkta painters
*!      (CI bars, whiskers, reflines, bands, keys) with NO per-painter edits.
*!   D3 violin inner box: fill 0.22 / stroke 0.75 black at 2 px (dark: 0.25 / 0.85 white)
*!      -- was a 1.5 px 40% hairline the user could not read. Whisker stays dashed.
*!   Verified offline at device ratio 1.5: grid lines now exactly 1 device px (were 2 px
*!      smeared) on violin/scatter/coefplot; violin box edges solid; contact sheet of line,
*!      stacked, cp bar+area, marginsplot area, histogram, donut clean; 97 pages 0 errors;
*!      full gate green. Text remains canvas-rasterised (Tier C = vector renderer).
*! v3.6.0-t2j fix8s (Java only; ado body byte-identical to fix8r, header +1 entry):
*!   crisp charts at every browser zoom. User-reported: axis text/markers looked blurred next
*!   to the (DOM) summary table at 100%, and stayed blurred after Ctrl-+ to 250% in Chrome.
*!   Root cause (reproduced in Chromium): Chart.js draws on a bitmap of css px x
*!   devicePixelRatio and re-reads the ratio only inside resize(); on a zoom the layout resize
*!   fires first (ratio still 1, e.g. 326 css px -> 326 px buffer) and the separate DPR-change
*!   listener did not reliably trigger the second resize, so the 1x bitmap was stretched 2.5x.
*!   At 100% on a 1x screen the bitmap is 1 px per css px with no font hinting, hence softer
*!   than DOM text. FIX (HtmlGenerator pluginReg, next to spkAspectLock): global plugin
*!   spkCrisp -- beforeInit sets options.devicePixelRatio = max(window.devicePixelRatio, 2)
*!   for canvases inside .spk-chart-box (supersampled on 1x / fractional-zoom screens);
*!   _spkCrispArm() listens on matchMedia('(resolution: Ndppx)') and re-arms after each change,
*!   and a window resize fallback checks for a ratio change; both re-apply the ratio to every
*!   Chart.instances entry on the page and call chart.resize() directly (bypasses resizeDelay).
*!   The saveas/download export clones set o2.devicePixelRatio=1 on a fake canvas with no
*!   .spk-chart-box parent, so they are untouched (SVG/PDF stay vector, PNG scale unchanged).
*!   Verified offline: buffer/css ratio 2.0 at 100%, 2.5 at emulated 250%, 2.0 at 90% (was
*!   0.9) on scatter, bar by() panels, violin, multi-model coefplot; 0 page errors; tooltip
*!   probes unchanged (hit-testing is in css px). Optional follow-up not done: set
*!   Chart.defaults.font.family to the page font stack so chart text matches the table.
*! v3.6.0-t2j fix8r (Java only; ado body byte-identical to fix8q; header: +1 entry, the
*!   superseded fix8m block dropped -- it lives here): the four UX decisions logged after
*!   the fix8q QC sweep, all Java, one jar rebuild.
*!   D1 pie/donut default aspect. ChartRenderer.pie() never emitted aspectRatio, so Chart.js
*!      used its pie default of 1 and a full-width page drew a 986 px disc. Now emits
*!      aspectRatio:2 unless aspect() is given (Chart.js centres the disc in the 2:1 box);
*!      by() panels keep their 1.35 (set before pie() runs). spkAspectLock reads the same
*!      option, so the resize lock follows.
*!   D2 subtitle wording (HtmlGenerator.contextLine): scatter/bubble -> "<y> vs <x>[, sized
*!      by <r>]" (Stata order: first var = y); histogram/boxplot/violin (hbox/hviolin are
*!      normalised to those in DashboardBuilder) -> "Distribution of <vars>". Bars/lines/ci
*!      keep "<Stat> of <vars>". Pie/donut and frequency wording unchanged.
*!   D3 shared by() panel y-range (HtmlGenerator.buildByScripts): niceRange target 6 -> 10,
*!      matching the fix8q C8 axis lock, so price panels get 0-16,000 (bars) or 2,000-16,000
*!      (points) instead of 0-20,000.
*!   D4 violin tooltip hit band (ChartRenderer.violinChart mousemove): the value test was
*!      cursor within [min-1, max+1] DATA units, so an n=1 violin (min==max) had a 2-unit
*!      band -- sub-pixel on a price axis. Now tested in pixels: [px(min), px(max)] widened
*!      6 px each side, so every violin has a >= 12 px target on either orientation.
*!   Verified offline (no Stata): 19 Java files compile; Harness + HarnessSweep pages
*!      re-rendered; Playwright checks pie box aspect, subtitle strings, panel axis ranges
*!      and n=1 violin hover. Real-Stata smoke test still required.
*! v3.6.0-t2j fix8q (Java only; ado body byte-identical to fix8p, header +1 entry):
*!   post-fix8p QC sweep, all chart types. Method: verify/harness/HarnessSweep.java adds 36
*!   offline scenarios (bubble +filter/+over/+by, hbar, stacked 2-series bar/hbar/line/area,
*!   stack100, cibar/ciline, histogram on a var with missing, hbox/hviolin, pie/donut, y2,
*!   dark) to the 61 in Harness.java (SPK_OFFLINE=1 renders self-contained pages);
*!   verify/sweep_probe.js drives a real Playwright mouse over up to 3 marks per dataset on
*!   EVERY chart (fires + correct title/body), an empty in-plot spot and off-canvas (hides),
*!   checks a viewport resize round-trip and JS errors; violin/box probed via their own
*!   geometry. RESULT: 0 JS errors, 97/97 js_check, viz_audit 0 errors, every reachable mark
*!   fires and every tooltip dismisses (incl. stacked segments, violin HTML tooltip,
*!   box median), resize returns to the identical box. Non-firing marks were all
*!   zero/null/1-px marks (empty over-group, non-estimable margin, mpg next to price).
*!   FIXES from the sweep:
*!   S1 box/violin value axis (buildBoxValueAxisConfig) emits an explicit padded min/max,
*!      and Chart.js labels the bounds of an explicit range (ticks.includeBounds default),
*!      so every box/violin axis ended in "2,290.4" / "16,914.6". Now includeBounds:false
*!      (the option the coefplot axes already use) -- interior nice ticks only.
*!   S2 by(, showmissing): the "(missing)" panel rendered FIRST because the group sort
*!      compared "" as a string before numbers. DataSet.uniqueValues/uniqueGroupKeys now
*!      share one TOTAL comparator (groupOrder): missing last (Stata order), numbers
*!      numeric and before strings, strings by text -- also removes the latent
*!      "comparison method violates its general contract" risk of the old mixed compare.
*!      (Without showmissing the ado marks missing by() rows out, so the harness-only
*!      "(missing)" panel never appears in real Stata output.)
*!   S3 C8 axis lock extended (ChartRenderer.lockAxisToFullData now also in scatter();
*!      new lockAxisToPanelExtent + DashboardOptions._panelXExtent/_panelYExtent set in
*!      HtmlGenerator.buildByScripts for scatter/bubble): a filtered scatter keeps the full
*!      -data frame; scatter/bubble by() panels all share one x axis (they were free per
*!      panel: 2,000-4,500 vs 2,000-2,900). The shared panel y-range for scatter/bubble now
*!      comes from the y variable's own extent (the regex over data:[{x:,y:,r:}] had swept x
*!      and r values in, widening price to 0-20,000). Lock ranges target ~10 ticks instead
*!      of 6 (3,299-15,906 -> 2,000-16,000, not 0-20,000). Verified with a real filter
*!      change: axes identical before/after/back, marks update, no JS errors.
*!   Harness.java: SPK_OFFLINE env var renders every scenario self-contained.
*!   NOT changed (logged for decision): pie/donut default aspect 1 -> a 986x986 pie at
*!   full width (pre-existing, same in fix8o); subtitle says "Mean of <var>" on histogram
*!   and scatter; n=1 violin tooltip is a 2-unit-wide hit range; shared by() panel y-range
*!   still uses the 6-tick nice range (0-20,000 for price).
*! v3.6.0-t2j fix8p (Java+ado): tooltip dismiss on every chart type (nearest,intersect:true
*!   in BOTH the interaction block and every tooltip builder -- bar/line, cibar, ciline,
*!   stack100, scatter, bubble, box/violin; the interaction block alone was not enough);
*!   stacked bars show the segment under the cursor. B-SUBN: subtitle N = non-missing count
*!   of the plotted variable (HtmlGenerator contextLine), matching the stats table (rep78
*!   histogram: 69, not 74). C8: filtered bubble locks x/y to the full-data extent
*!   (lockAxisToFullData + varExtent; only when filters exist and no user xrange/yrange).
*!   C2: ado note when a stacked type gets a single variable (draws as a regular bar/line,
*!   as Stata does). C3: marginsplot over() warning already existed (t2j B3). B-RESIZE:
*!   global Chart.js plugin spkAspectLock (afterInit) gives the chart box a CSS aspect-ratio,
*!   positions the canvas absolutely and sets maintainAspectRatio:false, so page layout never
*!   depends on the canvas -> the Chrome fractional-zoom (90%) shrink loop has no feedback
*!   path; fix7's reserved scrollbar stays. Verified: static pipeline green, real-Stata
*!   smoke test, and the 90% zoom loop confirmed gone on real Chrome.
*! v3.6.0-t2j fix8o (build scripts only): keep dist/sparkta.jar and ado/sparkta.jar
*!   byte-identical. build.bat copied each freshly built jar to dist/ but never to ado/
*!   (the copy sparkta.pkg installs to end users), and build.sh wrote neither repo copy
*!   (only dist_test/ and ~/ado/personal). So the two shipped jars were refreshed out-of-
*!   band and drifted; a real-Stata run then smoke-tested the dist-flavour jar installed
*!   to the personal ado dir, NOT the ado/ jar that ships. verify_before_zip check 16
*!   caught it as 'dist and ado jars differ' (ChartRenderer.class was byte-identical in
*!   both, so the fix8n coefplot fix shipped regardless; DashboardBuilder/VersionCheck/
*!   StataDataReader differed as same-source/different-JDK compiler noise, plus a 274-byte
*!   jsDelivr banner on the dist Chart.js). FIX: build.bat and build.sh now copy the ONE
*!   built jar to BOTH dist/ and ado/. No Java, no ado-body, no jar-content change.
*! v3.6.0-t2j fix8n (Java+jar): horizontal coefplot tooltip works for every coefficient
*!   (user-reported: after fix8m only the largest coefficient, e.g. car origin, showed a
*!   tooltip). ROOT CAUSE: fix8m rendered the horizontal coefplot as a transparent type:'bar'
*!   whose LENGTH was the coefficient (baseline 0 -> value). A small coefficient (weight=3.5
*!   on a -1700..6700 axis) is then a ~1px-wide hit target, so mode:'nearest',intersect:true
*!   could not land on it; only a large coefficient had a wide enough bar. FIX: draw the
*!   horizontal coefplot markers as REAL Chart.js scatter points at (value, index) -- the same
*!   construction as the multi-model coefplot, which works. The points are invisible
*!   (pointRadius:0; the CI/dot plugin still draws the styled dot) but carry a fixed-pixel
*!   hitRadius, so hovering near ANY coefficient fires its tooltip regardless of magnitude and
*!   it hides off the point -- point-based, matching the multi-model plot. The y-axis becomes
*!   linear+reversed (min -0.5, max k-0.5, integer ticks labelled with the coefficient names)
*!   so the scatter point y=i and the plugins' getPixelForValue(i) stay in lock-step; whisker/
*!   band/area/stars/refline plugins are unchanged (they already keyed off getPixelForValue).
*!   indexAxis:'y' is kept so interaction and the viz auditor still read x as the value axis.
*!   Verified offline (Playwright): every coefficient hoverable for horizontal/vertical/bar/
*!   multi-model, dismisses off-point, band+whisker render correctly, viz audit 0 errors.
*!   Java only (jar rebuilt); ado program body byte-identical to fix8e; header +1 entry.
*! v3.6.0-t2j fix8m (Java+jar): coefplot tooltip matches the event study (user-reported).
*!   Was interaction mode:index,intersect:false: the tooltip fired anywhere in a
*!   coefficient's row/column and never dismissed. The horizontal coefplot draws its
*!   point estimates with a canvas plugin over a transparent bar that was 1px thick,
*!   so intersect:true could never land on it. fix8m widens that (still invisible) bar
*!   to the full row (barPercentage/categoryPercentage:1.0) for a real hit target and
*!   pins the tooltip to mode:nearest,intersect:true on every coefplot (single and
*!   multi-model, horizontal/vertical/bar style). The tooltip now lands on the
*!   coefficient under the cursor and HIDES when the pointer leaves it. The pin also
*!   neutralises the ado tooltipmode(index) default, which would otherwise re-enable
*!   index mode here. Java only (jar rebuilt); ado program body unchanged (byte-
*!   identical to fix8e); ado header gained a short changelog entry only.
*! v3.6.0-t2j fix8l (Java+jar): numeric event-study tooltip fix (user-reported). The
*!   event study draws reference / pre / post as separate scatter datasets whose points
*!   sit at DIFFERENT x per dataIndex; the ado defaults tooltipmode to index, and index
*!   mode then grouped those unrelated points and pinned the tooltip to the chart centre
*!   showing the wrong period's stats. eventStudy() now forces mode:nearest,intersect:true
*!   so the tooltip shows the single point under the cursor, anchored at that point, and
*!   hides when the pointer leaves a point. Java only (jar rebuilt); ado body unchanged.
*! v3.6.0-t2j fix8k (ado): shrink the *! changelog to fix a load failure.
*!   ROOT CAUSE of "command sparkta not defined by sparkta.ado" in fix8f-fix8j: the
*!   *! changelog header in this file had grown to ~1,000+ lines. fix8e (966 *! lines)
*!   loaded; every build after it (989-1014 *! lines), with a BYTE-IDENTICAL program
*!   body, failed to define the program -- Stata reads the whole file (which sparkta
*!   still prints it) but the oversized leading comment block prevented program
*!   define sparkta from completing on auto-load. FIX: the full version history moved
*!   here; only a short recent list is kept in the ado header. NOTE: the fix8j entry
*!   below records the SUPERSEDED (program-body-size) diagnosis as it stood at the
*!   time; fix8k's header-size cause is the correct one.
*! ---------------------------------------------------------------------------
*! v3.6.0-t2j fix8j (ado): TRUE FIX for the fix8f-fix8i load failure (Stata log confirmed it:
*!         "command sparkta not defined by sparkta.ado" even though which sparkta printed the
*!         file). ROOT CAUSE, now identified: the single-variable stackedbar note (issue 5) I
*!         added in fix8f put 9 more lines INTO the main `sparkta` program. That program is
*!         already ~192 KB / 3,700+ lines and sits at Stata's ceiling for one program (the
*!         project memory warned that the main program is near Stata's per-program limit and
*!         that new logic must go into a subprogram, not the main program). Those 9 lines tip it
*!         past the ceiling, so `program define sparkta` no longer completed on auto-load and
*!         the command was left undefined. It was NOT nested quotes (fix8h's guess) and NOT
*!         environmental (fix8i's guess) -- it was program SIZE, and I broke the memory's own
*!         rule by adding to the main program. The QA pipeline runs no Stata, so it could not
*!         catch a define-time failure. FIX: removed the note entirely and reverted the
*!         cosmetic marginsplot display-as-error->as-text swap, so the main program body is now
*!         BYTE-IDENTICAL to the loading fix8e (verified: 192,627 bytes, identical). All the
*!         fix8f-fix8g FEATURE fixes live in the Java jar (issue 1 tooltip, 4 box mean, 9 box
*!         filter, 10 CI colour, 11 histogram density) and are unaffected. Issue 5 (a note for
*!         single-variable stackedbar) is dropped for now; if wanted it must go in a subprogram.
*! v3.6.0-t2j fix8g (Java + ado + jar REBUILD): user output-review round (outputs were fix8e).
*!         #1 REAL, now FIXED: bar/line/area tooltips only fired when the pointer landed
*!            exactly on the thin line/point (Chart.js default intersect:true), so a price+mpg
*!            line -- mpg flat on the shared-axis floor -- was nearly impossible to hover and
*!            the tooltip felt disconnected. Added interaction:{mode:'nearest',intersect:false}
*!            so it fires anywhere in the plot and anchors on the nearest datapoint, matching
*!            the responsive event-study feel the user liked. (ChartRenderer bar/line options.)
*!         #4 boxplot tooltip now lists the Mean value (the box already drew the mean marker;
*!            the violin tooltip already showed it). ChartRenderer box tooltip callback.
*!         #9 single-variable over() boxplot + filter no longer drops every group past the
*!            first (Foreign vanished on any filter): FilterRenderer now rebuilds the box
*!            data[] driven by the GROUP set for the colorByGroup layout (1 dataset, one box
*!            per group), so box count stays aligned to the labels; empty groups still null.
*!         #10 CI error bars (cibar) now drawn with a DARKENED stroke (HtmlGenerator.darken,
*!            factor 0.62) so the interval reads more strongly than the lighter bar fill.
*!         #11 histogram density no longer displays as 0: the emitted value uses significant-
*!            figure precision (fmt, was "%.6f" which truncated small densities to 0.000000),
*!            and the tooltip formats density with 3 significant figures (was toFixed(4),
*!            which read 0 below 5e-5 and rounded 0.000264 to "0.0003").
*!         #5 single-y-variable stackedbar/stackedarea/stackedline now emits a NOTE that
*!            stacking is across y-variables (with one variable it renders as a plain bar).
*!         #6 marginsplot over()-ignored note changed from "as error" (red) to "as text".
*!         Intended / correct, no change: #7 count = non-missing over() group total (missing
*!            dropped, no phantom; showmissing appends "(Missing)").
*!         COULD NOT REPRODUCE in a fix8e render (need the user's actual output file): #2/#3
*!            single-model coefplot tooltip fires across the whole plot here (index mode,
*!            intersect:false) with styling + tooltipmode(index) honored; #8 coefplot stars
*!            draw on the canvas. If these fail for the user, the likely cause is a stale
*!            installed jar or a real-data path -- pending their fix8e HTML to confirm; the
*!            durable fix for #2 is to render the single-model coefplot as real scatter points
*!            like the multi-model path (their suggestion), tracked as a follow-up.
*! v3.6.0-t2j fix8 (Java + ado + jar REBUILD): decision-probe follow-through -- every option
*!         that the probe showed was parsed-but-not-consumed is now threaded through the
*!         config systemically (routed through the existing shared source-of-truth helper,
*!         not patched at the call site). A6: pie/donut legend labels now flow through
*!         legendLabelsCfg() so legcolor()/legbgcolor()/legendsize()/legendboxheight() are
*!         honored exactly as on every other chart. A2: inner nested-CI band/area fill+edge
*!         under levels() now re-derive from the cicolors()-aware colour (colAtAlpha), not a
*!         hardcoded blue; the whisker path already did. A4/A5/A7/A8/A9 (from earlier fix8
*!         work): box/violin axis fonts, box tooltip styling, bar outline linewidth,
*!         tooltipmode(index), and post-est legend(position) all honored. A10: post-est
*!         connected line honors linewidth()/smooth() (via lineWidthOr/smoothOr, default
*!         width 2 + straight tension 0 preserved), and multi-model coefplot now draws the
*!         connecting line at all (was hardcoded showLine:false). A12: histogram default bins
*!         share one static Sturges rule across build and browser-filter recompute (was two
*!         disagreeing formulas -> filtering silently re-binned). A11: coefsort()/order() on a
*!         multi-model coefplot now prints an explicit "ignored for multi-model" note instead
*!         of silently no-op-ing. A13 (NEW OPTION): reflinewidth(#) sets the null/reference-
*!         line width on coefplot/eventstudy/marginsplot (reuses the reserved arg slot 77; no
*!         positional-protocol shift). smooth()/linewidth() are un-defaulted at the arg
*!         boundary so each call site supplies its own historical default -- identical output
*!         when the option is unset. ASCII-only; no positional args added or moved.
*!         DEEP-DIVE follow-ups (multi-agent code + visual review): (i) cibar bar-outline
*!         width now honors linewidth() (was a hardcoded borderWidth:1, same class as the
*!         A7 bar fix). (ii) Two build-vs-browser-filter DIVERGENCES aligned (same class as
*!         the A12 histogram-bins bug): the qfit CI band recompute in sparkta_engine.js now
*!         uses the EXACT quadratic leverage v'(X'X)^-1 v (matching FitComputer.computeQfit)
*!         instead of the linear-model approximation 1/n+(x-xbar)^2/Sxx that made a filtered
*!         band up to ~35% too narrow near the centroid; and lowess (both the JS filter path
*!         and the FitComputer fallback) now runs Cleveland's 3 bisquare robustness iterations
*!         to match Stata's `lowess`, so a filter that keeps an outlier no longer bends the
*!         curve away from the initial render. Verified: qfit leverages sum to 3 (=#params);
*!         robust lowess resists outliers; full pipeline green; qfit/lowess ASCII-clean.
*!         DEEP-DIVE round 2 (the three remaining open items): (1) mixed-magnitude note --
*!         when 2+ variables share one y-axis (line/area/bar/hbar) and their typical
*!         magnitudes differ by >50x and y2() is unset, print a note suggesting
*!         y2(varname); no data is rescaled and y2() stays opt-in (Stata yaxis(2)
*!         philosophy). (2) connected line under levels() -- the nested-CI dot plugin
*!         dropped the connecting line, so `connected` drew no line when levels() was
*!         set; it now draws it (honoring linewidth()), and every coefplot connecting
*!         line now honors linewidth() via lineWidthOr (was hardcoded 1.5). (3) event
*!         study CI whiskers now honor ciwidth() (outer + inner were hardcoded 1.5/3.2
*!         and ignored the option), so faint whiskers on small-CI data can be thickened.
*!         DEEP-DIVE round 2b (full-codebase option + statistics audit follow-ups):
*!         (a) post-est charts (coefplot/eventstudy/coefplotmulti/marginsplot) now honor
*!         tooltip styling (tooltipbg/tooltipborder/tooltipfontsize/tooltippadding) and
*!         tooltipmode() -- their hand-built tooltips previously skipped the shared prefix.
*!         (b) post-est legends (eventstudy/coefplotmulti/marginsplot) now honor legcolor/
*!         legbgcolor/legendsize/legendboxheight (were hand-built without them). (c) the
*!         inner nested-CI whisker on coefplot/coefplotmulti now scales with ciwidth()
*!         (was hardcoded 3.5). (d) event-study markers honor pointsize() (was pointRadius:5).
*!         (e) lowess now runs 1 initial + 3 bisquare robustness iterations = Stata's
*!         lowess default iterate(3) (both the JS filter path and the FitComputer fallback;
*!         was 3 total fits). (f) filtered fit CI bands (lfit/qfit/exp/log/power) now honor
*!         cilevel() via the exact two-tailed tCrit(df,level) instead of a hardcoded 95%.
*!         Statistics audit confirmed EXACT match to Stata for: percentiles (Stata type-2
*!         averaging), SD/CV (n-1), KDE bandwidth (0.9*min(sd,IQR/1.349)*n^-0.2), box Tukey
*!         fences, cibar/ciline CI (exact invttail), qfit exact leverage, histogram default
*!         bins (min(sqrt N, 10*lnN/ln10)).
*!         DEEP-DIVE round 2c (closing verification fixes): (g) the ado fit-CI bands
*!         (lfit/qfit/exp/log/power, over + no-over paths, 10 sites) now use
*!         invttail(df,(100-cilevel)/200) instead of a hardcoded 0.025, so the INITIAL
*!         render honors cilevel() -- this both completes (f) [the filtered band was made
*!         cilevel-aware] and fixes a pre-existing mislabel where a cilevel(90) band drew at
*!         95% while the legend said 90%. (h) fitci now renders offline/without Stata: the
*!         FitComputer fallback CI band (lfit/qfit/exp/log/power) is drawn instead of being
*!         discarded, so the legend swatch no longer advertises a band that never appears.
*!         (i) the fitci legend key is emitted only for CI-supporting fit types (not
*!         lowess/ma). Full pipeline green; all changes ASCII-clean and compile-verified.
*!         DEEP-DIVE round 3 (multi-agent visual analysis of a full test run, 86 offline
*!         pages): one real defect found and fixed -- ciline + filter recompute wrote the
*!         three per-variable datasets in the order [upper,lower,mean] while ciLineDatasets()
*!         builds them [mean,upper,lower], so ANY filter change (even selecting "All")
*!         rotated the series: the mean line jumped to the upper-CI value and the band
*!         broke. FilterRenderer now writes each recomputed array into its matching slot
*!         (mean->base, upper->base+1, lower->base+2). Verified: after a filter recompute the
*!         mean stays within [lower,upper] at every point. Everything else in the run
*!         checked clean -- box quartiles exact vs summarize,detail; whiskerfence, histogram
*!         bins/scaling, KDE bandwidth, cibar/ciline CI level, stat() aggregates, pie/donut
*!         shares, stacked100 totals, and all round-2 styling fixes confirmed in the outputs.
*!         DEEP-DIVE round 3b (user-reported coefplot UX + one carried-over item):
*!         (j) coefplot TOOLTIPS now fire -- coefplot draws its points via a canvas plugin
*!         over a transparent 1px bar (horizontal) / borderless line (vertical), so Chart.js
*!         default hit-testing never landed on the element; added interaction{mode:index,
*!         intersect:false} so hovering a coefficient row/column shows its Coef/SE/p/CI
*!         tooltip. (k) coefplot ANIMATION -- the plugin-drawn marks meant Chart.js's dataset
*!         animation was invisible, so the chart popped in; added a lightweight canvas
*!         fade-in (Web Animations API) gated on the animation duration. (l) bubble by()
*!         panels now scale radii on the FULL-dataset size range (matching the JS filter
*!         recompute) instead of per-panel, so bubbles no longer resize the first time a
*!         filter is touched. All verified in a live browser; full pipeline green.
*!         DEEP-DIVE round 3c (user requests): (m) significance STARS ON THE CANVAS for
*!         coefplot (single- and multi-model): a cpStars/cpMStars plugin draws */**/***
*!         next to each coefficient marker using the SAME thresholds as the table (stars(),
*!         default 0.10/0.05/0.01), suppressed by nostars, skipped for base/omitted terms,
*!         dodge-aware and legend-hide-aware for multi-model. (n) grouped-bar tooltip COUNT
*!         investigated: it is already per-(variable,group) and correct -- it equals across
*!         the bars in a category only when those variables share the same non-missing
*!         observations. Proven: "price rep78, over(foreign)" shows Price n=20 but rep78
*!         n=18 in the Domestic group (rep78 has 2 missing there), i.e. counts DO differ per
*!         bar when missingness differs. No count change made (would be fabricating).
*!         DEEP-DIVE round 3d (regression pass found + fixed 2 real issues): (o) PHANTOM
*!         (Missing) over-group -- DataSet.uniqueValues/uniqueGroupKeys mapped a missing
*!         over-value to an empty-string group and, by default (showmissing off), never
*!         dropped it; this drew an empty "(Missing)" bar at index 0, a blank stacked100
*!         legend swatch, a blank leading ciline category, and made Overall-N != sum of
*!         group-N. Now the empty group is dropped by default (Stata drops missing over()
*!         unless asked); showmissing still shows a labelled (Missing) group AT THE END.
*!         One fix at the single source of truth corrects bar/line/area/stacked/stacked100/
*!         pie/ciline/box/violin/bubble and the by()-panel global groups together. (p)
*!         cistyle default hardening: coefPlot now defaults an empty cistyle to whisker so
*!         the CI can never silently vanish if the style is unset. Verified: grouped bar and
*!         ciline over rep78 now label [1..5] with no phantom; showmissing appends (Missing);
*!         coefplot whiskers + on-canvas stars render together. Full pipeline green (61/61).
*! v3.6.0-t2j fix7 (Java + jar REBUILD): browser-zoom infinite resize loop. On desktop
*!         browsers with classic (width-taking) scrollbars, zooming below 100% could send a
*!         chart into an endless shrink-then-jump-back loop: the responsive chart grew past the
*!         viewport -> a vertical scrollbar appeared -> that narrowed the container -> the
*!         maintainAspectRatio chart shrank -> the scrollbar vanished -> the container widened
*!         -> repeat. Fix: html{overflow-y:scroll} always reserves the scrollbar so layout
*!         width never toggles, breaking the loop; and every responsive chart now sets
*!         resizeDelay:150 to debounce the ResizeObserver. Appearance unchanged (pixel checks
*!         identical). NOTE: headless browsers use zero-width overlay scrollbars so the loop
*!         cannot be reproduced in the cloud harness -- confirm the zoom test in a desktop browser.
*! v3.6.0-t2j fix6 (Java + jar REBUILD): deep-dive render fixes + hardcoded-option audit.
*!         THREE render bugs: (D1) a single-series stackedarea collapsed into the first
*!         category -- the line/area family now stacks on the VALUE axis only (bars keep
*!         both), so a lone filled series spans full width. (D2) whiskerfence(k) affected
*!         outlier classification but NOT the drawn whisker (the boxplot plugin recomputed
*!         with its default coef:1.5); the box object now emits explicit whiskerMin/whiskerMax
*!         so the whisker draws to the k fence. (D4) content-visibility:auto was dropped from
*!         the stats group cards so they no longer export blank in print/PDF or a non-scrolled
*!         capture. HARDCODE AUDIT (options honored in build but shadowed by a literal, or
*!         parsed-but-dead) -- fixed: bandwidth() now kept on the violin filter recompute
*!         (was computeKde(s,0,50)); pointhoversize() honored on scatter/bubble (was a
*!         hardcoded pointsize+2); bubble now honors pointstyle()/pointborderwidth()/
*!         pointrotation() (were dead for bubble); pointstyles()/msymbol() now drive
*!         multi-model coefplot/eventstudy marker shapes (arg was parsed but never read);
*!         box/violin now honor xgridlines/ygridlines/xborder/yborder (were hardcoded on);
*!         the stats-panel sparkline outlier fence now uses whiskerfence(k) (build + filter,
*!         was 1.5). Documented but NOT changed (lower severity / needs a design call, see
*!         the deep-dive report): pointhoversize/pointsize hardcodes on coefplot/eventstudy/
*!         marginsplot; inner nested-CI band colour/width literals under levels(); box/violin
*!         per-axis font styling and tooltip styling; pie legend colour/bg/boxheight; bar
*!         outline linewidth; connected line width/smooth on post-est; coefsort/order silent
*!         no-op on multi-model; histogram default-bin formula build-vs-filter mismatch.
*!         Verified in the cloud: whisker reaches the k fence; single-series area full-width;
*!         box nogrid, bubble pointstyle, violin-filter bandwidth all confirmed in output;
*!         full pipeline green (pctile/filtergroup/pixel unchanged).
*! v3.6.0-t2j fix5 (Java + jar REBUILD): numerical hardening ported from the rc1 comparison.
*!         (A) stats() now admits only FINITE values (a stray NaN/Inf no longer poisons
*!         mean/SD/CV), and sum/mean use Neumaier-compensated accumulation with a scaled
*!         overflow fallback (safeSum/safeMean/safeSampleSd) -- steady for large-magnitude
*!         columns (years, income, ids) that previously suffered catastrophic cancellation.
*!         (B) scatter fit lines (FitComputer) rewritten to fit in normalized coordinates
*!         with compensated sums, and the qfit CI band now uses the EXACT quadratic leverage
*!         instead of a linear-term approximation. Verified in the cloud: lfit recovers
*!         y=2x+1 exactly and stays exact at x~1e9; qfit recovers y=x^2; all 61 render
*!         scenarios still pass pixel/stat/percentile checks. NOT ported (assessed, deferred):
*!         type-tagged group keys and the union-index shared filter+over identity -- both are
*!         cross-cutting identity/filter-engine rewrites (78+ call sites) whose real-world
*!         benefit is negligible for single-typed Stata variables and which cannot be fully
*!         validated without a licensed Stata run; deferred to a Stata-tested session.
*! v3.6.0-t2j fix4 (Java + ado + jar REBUILD): six hardening items ported from a parallel
*!         rc1 build, for the international public release. (1) LOCALE: every numeric
*!         String.format and every keyword toLowerCase in Java now uses Locale.ROOT, so
*!         charts no longer emit invalid JS numbers (e.g. 1234,50) on comma-decimal
*!         machines (de/fr/es/...). (2) ESCAPING: DataEmbedder.jsStr now escapes less-than,
*!         greater-than and ampersand, closing a closing-script-tag breakout via value or
*!         marker labels in the data block. (3) NOSTATS: the filter handler calls the stats
*!         updater unconditionally, so under nostats it is now emitted as a no-op instead of
*!         absent -- no more ReferenceError on every filter change. (4) SAFE SAVEAS: exports
*!         now render to a tempfile, are verified non-empty, and are atomically moved onto
*!         the destination (FileCopy); sparkta_browser_run propagates the browser rc; HTML
*!         export writes atomically (FileUtil); CSV and TSV both get a formula-injection
*!         guard that still preserves negative numbers. A failed export no longer destroys
*!         the previous file. (5) LOSSLESS STATS: box/violin/CI/KDE values embed via
*!         Double.toString instead of %.6f (no more underflow to 0.000000). (6) VERSION
*!         HANDSHAKE: VersionCheck compares the loaded jar's version to this ado before
*!         rendering, catching a stale JVM-cached backend. NOTE: the Java items are verified
*!         in the cloud pipeline + a comma-locale render test; the ado-only paths (safe
*!         saveas, version handshake) need a real Stata run to confirm.
*! v3.6.0-t2j fix3 (Java + jar REBUILD): three visual/interaction fixes from screenshot
*!         review. (A) by()-panel + filter: the per-panel header count (n = ...) kept its
*!         build-time value while the chart and the global obs count updated, so the badge
*!         looked stuck; the filter now refreshes each panel n to the filtered row count.
*!         (B) bubble shared key: a bubble (or scatter) with no over() is a single-colour
*!         series whose variables are x / y / size ENCODINGS, but the key drew one coloured
*!         dot per numeric variable (e.g. Price / Weight / Mileage in three colours) while
*!         all plotted points were one colour; it now shows ONE entry naming the encoding.
*!         (C) table PDF export: choosing PDF (table only) captured the open export menu as
*!         an artifact; the print stylesheet now hides .spk-pub-toolbar (and its menus), so
*!         the exported PDF holds only the table. Verified in a real browser: panel n
*!         updates on filter, one-colour bubble key, and no toolbar text in the table PDF.
*!         (D) publication table alignment: the coefficient grid and the goodness-of-fit
*!         block (Observations / R-squared / ...) are two separate HTML tables; with auto
*!         layout each sized its columns from its own content, so the wider stat labels
*!         pushed the footer number columns out of line with the coefficient columns above.
*!         Both tables now sit in ONE horizontal-scroll wrapper, are each width:100% of a
*!         shared inner box, and use table-layout:fixed with an identical fixed-width
*!         colgroup (label 220px, each model column 130px). So every column lines up
*!         vertically for ANY number of models, and when many models make the columns
*!         exceed the page the two blocks scroll together (staying aligned) instead of
*!         cramming. Verified with 1/2/3/5/8/12 models: identical column x-positions in
*!         both blocks every time, no value crowding.
*! v3.6.0-t2j fix2 (Java + jar REBUILD): the export (Copy / Download) menu on the
*!         publication table did not retract after you chose an item (Markdown/CSV/LaTeX/
*!         PDF...): the item runs INSIDE .spk-pub-toolbar, so the outside-click close never
*!         fired for it and the menu stayed open (the export still ran). Each item now
*!         calls _spkPubHideMenus() to close the menu after acting. Verified in a real
*!         browser (open -> item click -> menu hidden, export runs, no JS errors).
*! v3.6.0-t2j fix1 (post-test-run, ado only -- jar unchanged): the X3 xrange()/yrange()
*!         confirm-number guards were written as one-line if-brace statements. Stata requires
*!         the opening brace to be the LAST token on the line, so this corrupted brace matching
*!         and every chart using xrange()/yrange() ended with a matching-close-brace error (the
*!         chart still exported first, so only a real Stata run caught it). Rewrote the four
*!         guards as multi-line if-blocks. Added ado_lint rule 6c to reject one-line block
*!         braces so this class cannot recur.
*! v3.6.0-t2j (deep-dive audit fixes, Java + ado + jars, REBUILD): STATISTICAL PARITY:
*!         percentiles = Stata default summarize,detail (was interpolating altdef) across
*!         Q1/median/Q3/IQR/whiskers/outliers/violin box + engine + filter; t-critical =
*!         exact invttail for any cilevel (was 90/95/99 table + Cornish-Fisher); histogram
*!         auto-bins = Stata min(sqrt(N),10*log10 N); violin KDE bandwidth = Stata kdensity
*!         0.9*min(sd,IQR/1.349)*n^-0.2; whiskerfence carried into filtered boxplot;
*!         filtered histogram density uses non-missing N. FILTER PARITY: over-group match
*!         by _si category index (labeled/relabeled over() no longer blanks cibar/ciline/
*!         box/violin); filtered violin recomputes its KDE shape (was frozen); bubble keeps
*!         its radius on filter; pie sum/count per mode; 100%-stacked renormalizes; by()-panel
*!         box/violin/bubble refresh. FIXES: order() 10+ coefficient prefix bug; estnames()
*!         and fit() restore the caller's active e(); marginsplot warns over() is ignored;
*!         xrange/yrange ignored on a category axis (was blanking); xtype(time) downgrades to
*!         a category axis with a note (no date adapter bundled); confirm-number guards on
*!         xrange/yrange/tick/step/aspect/barwidth and yband/xband/apoint; TSV copy gets the
*!         CSV formula-injection guard; publication-table stars honour stars()/nostars in the
*!         detailed table and LaTeX/CSV/TSV exports; saveas erases stale targets before export
*!         and accepts filenames containing spaces; noallfilter fires the filter on load.
*! v3.6.0-t2i (hardening after external review, Java + ado + jars, REBUILD): locale-neutral numbers
*!         (Locale.US around execute()); </script>/apostrophe escaping + UTF-8-safe LaTeX; Float64 filter
*!         buffer (was Float32, lossy); levels(a b) now uses b as the OUTER CI (was cilevel default);
*!         markout no longer listwise-deletes the plotted varlist for bar/line/area/scatter/hist/box/
*!         violin (only cibar/ciline or nomissing) so line gaps/spanmissing survive; cilevel(99.9)->100
*!         rejected; filter fixes (getValues export, ciline _ciValidIdx, violin _mainChart via
*!         Chart.getChart); both jars rebuilt (class 55, 210-arg, resources, identical). Section L suite
*!         cases need a Stata run. Known-remaining: bubble-filter radius, pie/stack100 filter percents.
*! v3.6.0-t2h (batch 2a, Java + ado, REBUILD): PUBLICATION TABLE. New component sparkta_table_opts.ado
*!         parses stars()/nostars/tstat/interval/nofooter/notable -> packed arg 209. New Java class
*!         PubTable.java renders an estout/esttab-style, sortable on-page table from the SAME __tblData
*!         the chart uses (coefplot/eventstudy) or __tblMargins (marginsplot), with LaTeX (booktabs +
*!         threeparttable, \caption/\label, standalone-compilable), Markdown (Copy default) and tidy-long
*!         CSV exports via Copy/Download dropdowns. The existing regress-style table is kept behind a
*!         [Publication | Detailed] toggle. headings()/indicators()/base levels/fit stats carried into the
*!         table. stars() also recolours the detailed table + legend. interval (not ci) because ci() is the
*!         matrix-mode option. NOT RUN IN STATA HERE; harness+js_check+pixel+pdflatex verified. REBUILD (arg 209).
*! v3.6.0-t2g (fix 5, ado only): run 53 trace -> `global _spk_labmap ""` is invalid syntax. The label
*!         map is gone; results(marginsplot) reads the name from the file characteristic _m#[varname] /
*!         _at#[varname] (what margins, saving() stores), else the label if it is a name, else a fallback.
*! v3.6.0-t2g (fix 4, ado only): run 52 193/193, fidelity 40/40, cicolors on marginsplot confirmed.
*!         margins, saving() labels _m#/_at# with the variable LABEL ("Repair record 1978"), so
*!         results() now maps that label to a variable in the caller's data ($_spk_labmap) -> rep78,
*!         value labels and axis title follow; the "name inside the label" guess (-> "Repair") is gone.
*! v3.6.0-t2g (fix 3, Java + ado, REBUILD): marginsplot now honours cicolors(c1|c2..) on the CI layer
*!         (band, whisker, inner, key); results(marginsplot) finds the at()/factor variable names
*!         from the saving() file labels/characteristics and says so when it cannot.
*! v3.6.0-t2g (fix 2, ado only): run 50 192/192, fidelity 39/40 -- the marginsplot y title carried
*!         literal compound quotes: the repost call wrapped labels into string-asis options (fix-5
*!         lesson, second occurrence). Values now passed bare.
*! v3.6.0-t2g (fix 1, ado only): run 49 -- every marginsplot page was built from a 1x1 missing
*!         r(table_vs): "matrix X = r(name)" silently creates a 1x1 missing when r(name) is absent.
*!         sparkta_margins_keep now confirms each matrix first. results() for marginsplot: the
*!         summary display used "..." + cond() (known trap) -- built in a local.
*! v3.6.0-t2f (fix 5, ado): run 45 -- every marginsplot case failed and no _truth/ was written: the
*!         sparkta_color_opts call wrapped values in compound quotes into string-asis options, so an
*!         empty option arrived as "" and the rclass normaliser ran for every case, wiping r().
*!         Call now passes opt(`opt'). (Whether r(table) survives the call is what run 46 tells us.)
*!         results(): suboptions parsed via spec(); autodetect takes numeric columns only (lwdid
*!         has a STRING column named effect).
*! v3.6.0-t2f (fix 4, ado): results(source [, name() time() b() ci() se() pvalue()]) -- a dataset
*!         or frame:name of estimates (lwdid save(), parmest, regsave, hand-built) becomes a matrix
*!         and runs through matrix mode unchanged (coefplot, eventstudy). sparkta_read_results.ado.
*! v3.6.0-t2f (fix 3, Java, REBUILD): an explicit normalised row (estimate = lo = hi = 0 at t<0,
*!         as lwdid writes for -1) is the reference period -- hollow marker, not a data point.
*! v3.6.0-t2f (fix 2, ado only): eventstudy default is now TOGETHER (post series joined to the
*!         reference) in estimation mode, where every lead/lag is relative to the omitted period,
*!         and SEPARATE with matrix() from a DiD estimator; new `separate` keyword. (user, run 43)
*! v3.6.0-t2f (fix 1, ado only): run 42 178/178, but refperiod(none)/together had no effect --
*!         _es_opts was re-declared empty in the post-estimation block after sparkta_es_opts set
*!         it. Declared once before the call. lwdid helper reads its r(table) (no estat event).
*! v3.6.0-t2f: EVENT STUDY REFERENCE PERIOD: hollow marker at the omitted (normalised-to-0)
*!         period, pre ribbon/line pinch to it, leads and lags as separate series (BJS 2021);
*!         refperiod(auto|none|#) and together (event_plot). New arg 208. FILE SPLIT stage 1:
*!         13 subprograms moved to their own sparkta_*.ado files (one public program per file,
*!         same names, same code); sparkta.ado now holds the main program only. Recompile required.
*! v3.6.0-t2e (fix 2, ado only, no rebuild): run 40 failed 3 colour cases with "olive not found":
*!         syntax strips quotes from string options, so colors("230 159 0") arrived as bare
*!         numbers and " 230 " matched inside the Stata-name table. Fix: COLORS(string asis),
*!         numeric tokens never looked up, three consecutive integers re-grouped as a triplet.
*! v3.6.0-t2e: run 39 (t2d): 167/167 CLEAN, legends confirmed in Edge. Pixel review found
*!         cistyle(band) on eventstudy drew one pre-coloured ribbon and no inner level:
*!         now one ribbon per model x phase (pre|post colour, stops at the base period),
*!         inner level darker on top, single-point phase -> rectangle. pre/post colours
*!         are slots 1-2 of colors(); cicolors(pre|post) recolours the CI layer.
*!         COLOUR NORMALISATION for EVERY colour option: colorpalette triplets
*!         ("230 159 0"), Stata names (navy, gs10, cranberry), hex without #, and the
*!         suffixes name%50 (opacity) / name*0.6 (intensity) all become #rrggbb or
*!         rgba() before Java (sparkta_color_norm / sparkta_color_list). Recompile required.
*! v3.6.0-t2d: EVENT STUDY REDESIGN: numeric relative-time axis ("Periods to treatment"),
*!         pre-treatment / post-treatment colours, CI rectangles by default (whisker/band
*!         available), dashed null line; several models = one marker shape per model,
*!         dodged, legend "<model> (pre|post)". Applies to estimation-mode eventstudy
*!         (lead#/lag#, T-3/T+2, tm3/tp0, -3.__event__ names parsed) and matrix mode
*!         (csdid/jwdid/lwdid estat event). Unparsed names keep the category axis.
*!         New arg 207 (relative times). Recompile required.
*! v3.6.0-t2c: run 38: 166/166 CLEAN; matrix mode verified in pixels (csdid2 works end
*!         to end). Eventstudy from a matrix now leaves Pre_avg/Post_avg off the time
*!         axis by default (show(*) includes them). Verification stack caught up with
*!         the product (key retirement, self-contained default, multi-matrix); a broken
*!         regex had silenced the visual audit since s9j -- fixed, and a crash of that
*!         stage now fails the pipeline. Ado + verify.
*! v3.6.0-t2b: run 37 of matrix mode: 12 cases failed on one display line (string +
*!         cond() is not a display expression); ci(e(name))/se(e(name)) now recognised
*!         before the parenthesis split; post-estimation types now reject a varlist
*!         (marginsplot with varlist was never guarded). DiD cases use a simulated
*!         panel (no download). Ado only.
*! v3.6.0-t2a: MATRIX MODE (S13). matrix(M | M[#] | M[,#] | r(table) | e(name) | A B C)
*!         with ci() se() df() pvalue() in Ben Jann's grammar, statistics autodetected
*!         by row/column name, plotlabels() alias, and type(eventstudy) parsing of
*!         relative-time names (csdid/jwdid/lwdid estat event). New reader
*!         sparkta_read_matrix feeds the unchanged post-estimation pipeline. Ado only.
*! v3.6.0-t1n: fetch_js_libs.bat flagged pdfbox-io (47,716 bytes, genuine) as too
*!         small -- threshold lowered to 20,000; JS numbering fixed to [n/6].
*!         No product change vs t1m.
*! v3.6.0-t1m: EXPORT BUG (user: fit lines missing): browsers have Path2D, so Chart.js
*!         strokes every line dataset via ctx.stroke(path), which canvas2svg ignores --
*!         all lines (fits, type(line) series) vanished from exports. The exporter now
*!         forces the direct-drawing branch (an empty segment option). svg_check runs
*!         browser-like (Path2D defined) and asserts every line dataset leaves a
*!         multi-segment stroked path. Java only.
*! v3.6.0-t1l: saveas() now covers by() pages: _spkPageToSvg composes every panel
*!         (captioned, in the screen grid) with the title, context line and shared key
*!         into ONE figure; single-chart pages unchanged. Every chart type exports.
*!         Java only.
*! v3.6.0-t1k: SELF-CONTAINED PAGES ARE THE DEFAULT (JS embedded from the jar, ~300 KB);
*!         new option online = CDN scripts (~50 KB, needs internet to view). offline is
*!         accepted for compatibility. saveas() always writes self-contained. Ado only.
*! v3.6.0-t1j: exported figures were clipped at the page edge (the plot border WAS the
*!         edge; SVG had no background). The exporter now adds an 18 px margin all round,
*!         a page background (theme-aware), and inner layout padding so no plot element
*!         touches the edge. Applies to SVG, PDF and PNG alike. Java only.
*! v3.6.0-t1i: run 35 (t1h) verified in log and pixels: close, idle(20), offline pages,
*!         vector PDFs incl. coefplot key, 4x PNG. One race: reading DevToolsActivePort
*!         while Edge still held it (1 of 4 launches) -> retry loop. Java only.
*! v3.6.0-t1h: saveas() suboptions close (quit the session browser right after this
*!         export) and idle(#) (seconds of inactivity before it quits by itself; default
*!         120). Default behaviour is fully automatic: warm between exports, closes on
*!         idle and on Stata exit, the user never waits. Recompile required.
*! v3.6.0-t1g: saveas() implies offline (self-contained) libraries so the headless
*!         render never depends on the network. Run 34 (t1f): 3.1 / 0.48 / 3.2 / 6.6 s,
*!         idle relaunch confirmed, scale(4) PNG 3824x2264. Ado only.
*! v3.6.0-t1f: SESSION BROWSER CONFIRMED on the user machine (2164 ms first call incl.
*!         launch, 262 ms second). It now closes itself after 2 minutes idle (env or
*!         -D SPARKTA_BROWSER_IDLE_MS; 0 = never) and on Stata exit; closebrowser stays
*!         as a manual option. Java only.
*! v3.6.0-t1e: session browser: Edge launched fine; the driver's JSON reader missed
*!         Chrome's pretty-printed "key": "value" (space after the colon) -> null URL ->
*!         NPE. Reader now accepts whitespace. Java only; restart Stata after build.
*! v3.6.0-t1d: run 31 (t1c): the session driver returned rc=2 with NO message even
*!         though it prints before anything can fail -> Stata drops System.out when a
*!         javacall returns non-zero. Driver now writes via SFIToolkit.displayln (by
*!         reflection) and THROWS on failure, so the reason always reaches the Results
*!         window. Restart Stata after build.bat: loaded Java classes are not reloaded.
*! v3.6.0-t1c: run 30 (t1b): Java PDF/PNG confirmed (vector PDF 636x378pt, 2x PNG);
*!         the session browser fell back silently and the fallback opened cmd windows.
*!         Now: driver reports every Throwable with cause + location; ALL browser runs
*!         go through Java ProcessBuilder (no console window, no cmd quoting);
*!         fallback message shows the rc. Recompile required.
*! v3.6.0-t1b: SESSION BROWSER: saveas() keeps one headless Edge/Chrome alive across
*!         calls and drives it over the DevTools protocol (JDK WebSocket, no new jars):
*!         first call ~2 s, later calls ~0.3 s; the SVG comes back over the socket (no
*!         dump-dom / temp files). Falls back to the one-off run if anything fails.
*!         sparkta, closebrowser quits it; a JVM shutdown hook quits it on exit.
*!         Recompile required.
*! v3.6.0-t1a: fast path ran on the user machine (sparkta-export.jar built, browser
*!         once, Java took over) and hit ClassNotFoundException org.apache.pdfbox.io
*!         .IOUtils: PDFBox 3.0 moved that package into pdfbox-io. fetch_js_libs.bat
*!         now fetches 5 Java jars; build.bat bundles all five. Rebuild after fetch.
*! v3.6.0-s9z: SvgConvert fixed for jsvg 2.x (load(URL); no ViewBox/FloatSize imports;
*!         render(component, g)) -- the four libraries downloaded and Step 5b now
*!         reached the compiler on the user machine. Java only (rebuild).
*! v3.6.0-s9y: FAST saveas() path: fetch_js_libs.bat also downloads jsvg + PDFBox +
*!         pdfbox-graphics2d; build.bat compiles src/export/SvgConvert into
*!         sparkta-export.jar when they are present; saveas() then launches the
*!         browser ONCE for the SVG and Java makes the PNG (scale()) and vector PDF.
*!         Without the libs, the per-format browser fallback stays. Recompile.
*! v3.6.0-s9x: export figures, not screenshots. SVG now carries the title and
*!         context line (drawn into the vector). saveas() default scope is chart
*!         (page | table available); PNG is the chart at a device scale -- scale(#),
*!         default 2x; PDF viewport sized to the page so nothing is shrunk.
*!         Recompile required.
*! v3.6.0-s9w: saveas() now loads the page exporter itself (it was only present with
*!         download, so headless SVG waited forever and the PDF swap silently skipped
*!         -> bitmap). Extractor messages go to Stata output; ?export=svg reports a
*!         reason after 5 s instead of hanging. Recompile required.
*! v3.6.0-s9v: saveas() fixes from the first real run: (1) SVG: extractor javacall
*!         now passes classpath(); (2) PDF was a pixel screenshot -- headless Chrome
*!         never fires beforeprint, so pages opened with ?print=... now swap every
*!         canvas for its SVG on load (vector PDF); desktop viewport 1400x1000; @page
*!         12mm; Edge stderr silenced. Recompile required.
*! v3.6.0-s9u: sparkta, findbrowser also sets global SPARKTA_BROWSER_FOUND so do-files
*!         can branch on it (sparkta is deliberately not rclass). Suite Section G gates
*!         on that global. Ado only.
*! v3.6.0-s9t: browser(path) option for saveas() when auto-detection fails (user
*!         request); the not-found message now shows the three ways to point at a
*!         browser. Detection confirmed on the user machine (Edge + Chrome found).
*! v3.6.0-s9s: "sparkta, findbrowser" -- user-facing diagnostic for saveas() (lists
*!         every candidate path and the result). Subroutines exist only once sparkta
*!         has loaded, so the diagnostic goes through the main command. Ado only.
*! v3.6.0-s9r: run 26 (s9q) 132/132 CLEAN but Section G found no browser on a
*!         Windows machine that surely has Edge. sparkta_find_browser now uses the
*!         ProgramFiles / ProgramFiles(x86) / LOCALAPPDATA env vars, per-user Edge and
*!         Chrome installs, a "where" fallback, and a verbose option that lists each
*!         candidate. Ado only.
*! v3.6.0-s9q: suite starts with discard (a session keeps the OLD ado in memory after
*!         build.bat; subroutines like sparkta_find_browser then do not exist).
*!         No product change vs s9p.
*! v3.6.0-s9p: NEW saveas(): write .pdf / .svg / .png straight from Stata, no
*!         clicking -- drives the exported page with a headless Edge/Chrome
*!         (--print-to-pdf, --screenshot, --dump-dom + ?export=svg through the page's
*!         own exporter; com.dashboard_test.tools.SvgExtract). saveas(f.pdf, table)
*!         prints only the table. Global SPARKTA_BROWSER overrides detection.
*!         Recompile required (new Java class).
*! v3.6.0-s9o: table-only PDF: a PDF button in the coefficient-table toolbar prints
*!         just the table (title + context kept) via a body class + print CSS. Print
*!         handlers now on every page (not only with download). Java only.
*! v3.6.0-s9n: suite Section F -- export toolbar on one page per family (bar, box
*!         by(), coefplot with table, marginsplot nested, scatter fit). 132 cases.
*!         No product change vs s9m.
*! v3.6.0-s9m: PDF button (download option): whole page via the browser print
*!         dialog. beforeprint swaps every chart canvas for its SVG (vector in the
*!         PDF), afterprint restores; print stylesheet hides toolbars/chips/filters,
*!         white page, tables never split. Tables and stats print as real text. Java only.
*! v3.6.0-s9l: VECTOR EXPORT is back: an SVG button next to PNG (download option).
*!         A fresh Chart is drawn into a canvas2svg context with the three canvas
*!         methods Chart.js 4 needs shimmed (resetTransform, setLineDash, roundRect);
*!         every sparkta plugin draws through the same ctx, and the key band is
*!         included. canvas2svg is inlined from the jar whenever download is on.
*!         Proven headlessly (page code -> SVG -> librsvg). Java only.
*! v3.6.0-s9k: PNG export now composes the page keys (shared key + elements key)
*!         into a band under the chart -- same roles, colours, dash arrays, marker
*!         shapes and whisker weights as on screen. Inner-CI whisker glyph is thicker.
*!         Java only.
*! v3.6.0-s9j: ELEMENTS KEY on every page: renderers register each non-dataset element
*!         they draw (CI outer/inner, null line, pexline, fit line, CI band, xline/yline,
*!         bands) with the resolved colour/dash; one HTML key renders them under the
*!         chart (or above the by() grid). The three canvas-drawn nested-CI keys are
*!         retired (CANVAS_CI_KEY=false). Chart.js legend keeps dataset toggling.
*!         NOTE: PNG/SVG export does not include the HTML key yet. Java only.
*! v3.6.0-s9i: keys list EVERY drawn element with the user's styling: by() shared
*!         key now has Median, Mean, IQR box (per group), Whiskers, Outliers (+ Density
*!         for violin); series glyphs carry the real dash array and marker shape;
*!         xline/yline and fit/CI entries added. Chart.js legends use usePointStyle so
*!         line/scatter entries show the series marker and dash. Java only.
*! v3.6.0-s9h: by() shared key now takes median/mean/box colours from the SAME
*!         resolver the chart draws with (user override -> dark theme -> palette
*!         luminance); key glyphs carry data-role/data-color so intent_check verifies
*!         each against the chart property it stands for. Java only.
*! v3.6.0-s9g: by() PANELS PASS (user: r_box_two_vars_layout was unreadable):
*!         panels wrap into a grid (min 380px, no scroll strip); ONE key above the
*!         grid (per-panel Chart.js and box/violin keys off); panels share one
*!         value axis computed from what they PLOT (two-pass), round ticks --
*!         yfree keeps per-panel axes (arg 206); panel titles are captions with n;
*!         missing by-group titled "(missing)"; taller panels (aspect 1.35).
*!         verify: SFI stub now serves a 30-obs auto-like FIXTURE so the harness
*!         renders data charts (H-D1..D6). Recompile required.
*! v3.6.0-s9f: run 21 (s9e) CLEAN on all five stages; log-axis minimum confirmed.
*!         Log tick labels: 2 and 5 (and 3) are labelled when the axis spans less
*!         than ~1.6 decades (a 1,500-5,000 log axis had NO labels). Audit
*!         AX-LOGTICKS. Recompile required.
*! v3.6.0-s9e: run 20 (s9d, 122 cases) CLEAN on all stages. Log-axis minimum fix
*!         now actually applied (the s9c branch never landed; the fixed "min:1"
*!         is now the decade below the data). Pixel stage: boxplot/violin/error-bar
*!         plugins registered, series-colour matching, marker tolerance. Recompile.
*! v3.6.0-s9d: user screenshot (r_bar_count_datalabels_desc): data labels collided
*!         with the legend -> 12% headroom on the value axis when datalabels is on;
*!         labels use the shared formatter (counts are integers); value-axis title
*!         follows the statistic ("Count", "Sum of Price"). Audit AX-HEADROOM.
*!         Recompile required.
*! v3.6.0-s9c: run 19 (s9b, 121 cases) pixel review. Fixes: fill/area charts kept
*!         series colours bound to draw order (Price turned orange) -> colour follows
*!         variable identity; log axes now start at the decade below the data
*!         minimum (were squeezed at 1). Test fixes: histogram uses by(), layout()
*!         needs by(), addstats keywords (rsq fstat). 122 cases. Recompile required.
*! v3.6.0-s9b: suite Section E -- option coverage: all 7 fit types (lfit qfit lowess
*!         exp log power ma) + fitci, axes/ranges/steps/log/y2, line/bar styling,
*!         pie/histogram/violin/box options, legend/text/tooltip styling, full
*!         annotation set, filters+sliders, coefplot table extras. 120 cases (was 84).
*!         No product change vs s9a.
*! v3.6.0-s9a: Pixel deep dive of all 75 pages. Fixes: (1) hbar axis titles were
*!         swapped (over label on the value axis); (2) filter stats panel: dot cloud
*!         and range labels now rebuilt from the filtered rows, SD/CV cleared at n<2,
*!         empty state at n=0 (user-found in r_opt_filter). Java only.
*! v3.6.0-s8z: (1) every post-estimation plugin now clips to the plot area, so a
*!         cropped CI (negative plotmargin) stops at the axis instead of drawing over
*!         the labels; (2) browser-zoom resize loop fixed: canvas sits in its own
*!         unpadded position:relative box (Chart.js responsive contract). Java only.
*! v3.6.0-s8y: I5/I6 tooltips. ONE formatter (_spkFmt: magnitude decimals, %, int,
*!         p, coef) on every page; post-est hovers use it. Data-chart hover now:
*!         "Car origin: Foreign" / "Mean of Price: 6,385" / "n = 22 (29.7% of 74)",
*!         counts from the SAME aggregation the chart draws (engine publishes
*!         _spkTipN under filters; static _spkTipN0 otherwise). Pie: "3: 30 obs (43.5%)".
*!         Recompile required.
*! v3.6.0-s8x: run 17 (s8w) CLEAN on all 4 stages, 74 A / 1 B. Closed the last gap:
*!         coefplot coefstyle(bar) + cistyle(area|band) now draws a per-coefficient
*!         CI rectangle over the bar (plugin cpBarArea), matching marginsplot.
*!         Recompile required.
*! v3.6.0-s8w: run 16 (s8v) CLEAN 84/84; per-file review docs/RUN16. Java: multi-model
*!         noci axis range from point estimates (was hidden CIs); HTML header stamps
*!         noci. Audit: fit-line colour pairs exempt from COL-DIST. Recompile required.
*! v3.6.0-s8v: Java: pointsize()/linewidth() accept decimals (Integer.parseInt on
*!         pointHoverRadius crashed with msize(large)=7.5: "For input string"). Harness
*!         H-T28/29 reproduce it. Run 15 (s8u): 83/84 -- coeflabels(=) confirmed.
*!         Recompile required.
*! v3.6.0-s8u: coeflabels(var = "Label") now normalised BEFORE the quoted-entry walk
*!         (the walk only sees text inside quotes, so the s8s version never fired).
*!         Ado only. Run 14 (s8t): 83/84 -- all other aliases confirmed on real output.
*! v3.6.0-s8t: FIX s8s: a comment line inside the syntax /// block ended the syntax
*!         command (all later options "not allowed"). Removed; ado_lint rule 4b.
*!         msymbol() now maps to sparkta shapes only (diamond/plus added to
*!         pointstyle()); msymbol(i) -> nopoints; alias block runs before validation.
*! v3.6.0-s8s: Stata-convention aliases (docs/OPTIONS_VS_STATA.md): level() keep()
*!         drop() sort vertical recastci() recast() msymbol() msize() mcolor()
*!         lwidth() nolabel, coeflabels(var = "Label"). Alias + primary together is
*!         an error. Ado only (no Java change vs s8r).
*! v3.6.0-s8r: data-anchored ticks on post-estimation axes (user request): nice
*!         step (1/2/5 x 10^k), first tick = floor(data min), last = ceil(data max),
*!         plotmargin() cushion OUTSIDE the ticks. marginsplot x and y, coefplot and
*!         multi-model value axes (refval/pexline still forced in). Java only.
*! v3.6.0-s8q: plotmargin() accepts negative values (-50..100) for coefplot /
*!         eventstudy / marginsplot = crop into the data, as Stata does; data charts
*!         reject negatives (Chart.js grace). Header comment records plotmargin so
*!         the audit treats requested cropping as WARN not ERROR. Recompile required.
*! v3.6.0-s8p: NEW plotmargin(): cushion between data and axes in percent of the
*!         data range, Stata plotregion(margin()) grammar: plotmargin(5) | (x y) |
*!         (l r b t). Feeds post-estimation ranges directly and ygrace() for data
*!         charts. Arg 205. Interaction margins x-title (first part of #; @-part
*!         for contrasts). Recompile required.
*! v3.6.0-s8o: base row label: Stata regexr() has no backreferences -> regexm/regexs;
*!         contrast stripes (r1vs0.foreign@1.rep78) now yield the x-axis title;
*!         marginsplot x-axis end padding (offset:true; 5% grace on numeric x) so
*!         end-point CI whiskers no longer sit on the axis. Recompile required.
*! v3.6.0-s8n: Alignment pass. NEW verify/intent_check.py derives expectations from
*!         each sparkta command (+ preceding regress/margins) -- stage 2b. Fixes it
*!         found: marginsplot x-axis title from the factor / at() variable LABEL
*!         ("Repair record 1978", "Weight (lbs.)"); coefplot base row label was
*!         "1rep78" (subinstr ate the dot) -> "rep78=1"; coefplot tooltip titles now
*!         use the same resolved label as the axis. Recompile required.
*! v3.6.0-s8m: cistyle() accepts a LIST: cistyle(area whisker) / cistyle(band+bar)
*!         composes a band for each CI level with a whisker at the point (both
*!         levels with levels()); key shows band + whisker glyphs. marginsplot only
*!         for combined styles; no new arg. Suite: r_mp_t21 nested ribbons,
*!         r_mp_t22 area whisker, r_err_cistyle_combo_coefplot. Recompile required.
*! v3.6.0-s8l: marginsplot levels() + cistyle(area|band): inner CI is now a darker
*!         BAND (rectangles, or ribbon when connected/numeric x) -- no whiskers; key
*!         shows two band swatches. Matches coefplot cpInner. viz_audit ENC-MIXED.
*!         Java only.
*! v3.6.0-s8k: marginsplot encoding fixes (user-found, r_mp_t19): CI key now shows a
*!         band swatch when the outer CI is drawn as a band; cistyle(area) draws per-
*!         category rectangles unless points are connected (ribbon only with
*!         connected or numeric x); padded axis bounds no longer appear as ticks
*!         (includeBounds:false, also coefplot). viz_audit: ENC-LEGEND, ENC-RIBBON,
*!         ENC-LEVELS, AX-BOUNDS rules. Java only.
*! v3.6.0-s8j: context line fixes from run 8: pie/donut "Share of ..." (no garbage
*!         name in frequency mode), series count ignores empty segments, N with
*!         thousands separator, aria-label on by() panel canvases. Java only.
*! v3.6.0-s8i: UX pass (Java): context line under the title (statistic, vars, by,
*!         N, CI; or cmd/depvar/N/CI; or margins conditions/series/CI); build stamp
*!         demoted to a footer ("Made with sparkta"); canvas role=img + aria-label;
*!         nested-CI subtitle retired (legend on chart). Recompile required.
*! v3.6.0-s8h: cosmetic: trim variable list in the two-at() error. First fully clean
*!         suite run (s8g): 72 PASS / 0 FAIL, 67/67 structural, viz 95.1. Ado only.
*! v3.6.0-s8g: FIX 73 "invalid number, outside of allowed range" lines per suite
*!         (4 per marginsplot): `=regexs(1)' on an if-line is expanded before the
*!         condition is evaluated. dy/dx label now built inside a block. ado_lint
*!         rule 6b guards the pattern. Suite marks r_err_* as expected PASS. Ado only.
*! v3.6.0-s8f: Presentation pass 1 from run-4 review. Default title from context
*!         (not "Sparkta"); marginsplot y-axis from r(predict1_label) + dy/dx /
*!         contrast / pairwise qualifiers; eform label on the value axis for
*!         multi-model; pwcompare base marker (2vs1bn); legend hides CI helper
*!         datasets (Java). Recompile required.
*! v3.6.0-s8e: pwcompare: r(table_vs) read re-applied (the s8c edit was lost to a
*!         later whole-file rewrite); verify check 9 now guards 14 fix markers.
*!         Suite run 3: 66/67 js_check, manifest clean, viz 95.1. No Java change.
*! v3.6.0-s8d: FIX rc=5101 on multi-model coefplot without levels(): the ado
*!         accumulated "~~" for _pe_lower2/_pe_upper2 and Java treated that as a
*!         real inner CI (ArrayIndexOutOfBounds). Now: ado skips empty groups;
*!         Java requires numeric tokens and sizes estLabels to nModels.
*!         pwcompare diagnostic added to test suite. marginsplot cistyle(bar) now
*!         draws bars (zero-based) + CI whiskers; was identical to whisker.
*!         Recompile required.
*! v3.6.0-s8c: Review of first release-suite run (59 HTML). Fixes: eform+levels()
*!         inner CI now exponentiated; pwcompare reads r(table_vs) ("2 vs 1");
*!         factor at() -> categorical with value labels; two varying at() vars
*!         -> clear error; marginsplot refval defaults to none for predictions
*!         (0 kept for dydx/contrast/pwcompare); eform sets axis title (Odds ratio
*!         /IRR/Hazard ratio); eform rejected for marginsplot; lpatterns() accepts
*!         space-separated. Java: coefplot axis titles, _cpTip null-safe (base
*!         levels no longer blank the chart), dark_viridis reversed, solid marks.
*!         verify/: viz_audit.js + VIZ_STANDARD.md (stage 4). NEW: palette() --
*!         Ben Jann colorpalette integration (optional dep). Recompile required.
*! v3.6.0-s8b: Code review + verification system. marginsplot: pwcompare labels
*!         ("2 vs 1"), x-title from at() var, xlab from r(at) (tooltip title fix),
*!         r(at) x-column chooser scans all rows. Option renames (abbrev clash):
*!         COEFLbl->COEFLBl, COEFLabels->COEFLABels, XTICKangle->XTICKANgle
*!         (full names unchanged). Debug displays removed. Java recompile required.
*! v3.6.0-s7d: Post-estimation charts: type(coefplot), type(eventstudy), type(marginsplot).
*!         New options: omit(), show(), estnames(), coeflbl(), headings(),
*!         nocons, nobase, coefstyle(), cistyle(), pointstyles().
*!         Ado reads e(b)/e(V) for coefplot/eventstudy, r(table)/r(at) for marginsplot.
*!         Java args 161-179 added. args.length bumped to 180. Java recompile required.
*! v3.5.58: mlabpos(#) clock position for scatter labels; marker name in tooltip.
*!          Args renumbered: mlabpos=154, fit=155, fitci=156. Java recompile required.
*!          fitci CI band for lfit/qfit. Args 152-155 added. Java recompile required.
*!          nomissing is a no-op for bar/line/area/scatter/histogram/boxplot/violin
*!          because missing outcome values are already silently excluded from all
*!          aggregation. Its only meaningful role is cibar/ciline where the
*!          error-bars plugin cannot handle null data points. Updated synopt
*!          one-liner and Options block accordingly. No code change, no recompile.
*! F-1h: Fix autoSkip:false not applied to y category axis on hbar/hbox/hviolin.
*!        Root cause: both buildAxisConfig() and buildBoxCategoryAxisConfig()
*!        had "if (isX)" / "if (isCatX)" guards on autoSkip:false.
*!        For hbar the category axis is y (not x), so all category labels
*!        were still being dropped by Chart.js autoSkip default.
*!        Fix: remove the x-axis guard -- autoSkip:false applies to ALL
*!        category axes regardless of orientation.
*!        Rotation is still x-only (y-axis labels are always horizontal).
*!        Java recompile required (ChartRenderer.java).
*! F-1g: Extend rotation to all chart types.
*! F-1g: Extend smart x-axis rotation + autoSkip:false to all chart types.
*!        ciBar:   nCat from over() uniqueValues, passed to buildAxisConfig
*!        ciLine:  nCat from groups list, passed to buildAxisConfig
*!        histogram: nBins+1 (bin edge labels), passed to buildHistXScaleCat
*!        boxPlot/hbox: nCat to buildBoxCategoryAxisConfig (new nCategories param)
*!        violin/hviolin: same as boxPlot
*!        buildBoxCategoryAxisConfig: rewritten with rotation + autoSkip:false
*!          (x-axis only; y-axis for hbox/hviolin never needs rotation)
*!        buildHistXScaleCat: overload with nBins param
*!        scatter/bubble: numeric x-axis, no change (not categorical)
*!        Java recompile required (ChartRenderer.java).
*! F-1f: Smart x-axis rotation for bar charts.
*! F-1f: Smart x-axis label rotation + autoSkip:false for bar charts.
*!        Previously: maxRotation:0 forced labels horizontal always AND
*!        Chart.js autoSkip:true silently dropped labels when crowded.
*!        Fix: buildAxisConfig() gains nCategories parameter.
*!        Auto-rotation: <=8 cats=0deg, 9-20=45deg, >20=90deg.
*!        autoSkip:false on category x-axis ensures ALL labels shown.
*!        User xtickangle() still overrides auto-rotation.
*!        hbar: nCat=0 (category axis is y, not x -- no x rotation).
*!        Java recompile required (ChartRenderer.java changed).
*! F-1e: Fix pie/donut showmissing slice order + gray color.
*! F-1e: Fix pie/donut showmissing: missing slice now last + gray.
*!        Mode 2+3: uniqueValues/uniqueGroupKeys now pass o.showmissingOver
*!        so (Missing) appends at END matching Stata bar chart behaviour.
*!        Null key lookup fixed: null maps to MISSING_SENTINEL when
*!        showmissingOver=true (was always mapping to empty string).
*!        Missing slice gets gray rgba(160,160,160,0.65) like bar charts.
*!        Also fixed: test_pie_modes.do P10 used bare showmissing flag
*!        instead of over(rep78, showmissing) suboption.
*!        Java recompile required (ChartRenderer.java changed).
*! F-1d: Fix pie Mode 3 varlist(default=none).
*! F-1d: Fix pie Mode 3 -- syntax [varlist] -> [varlist(default=none)].
*!        Root cause: Stata syntax [varlist] with no vars typed expands to
*!        ALL dataset variables (not empty). With auto dataset that is 12 vars,
*!        so nvar=12 fired the "cannot combine multi-var with over()" guard.
*!        Fix: varlist(default=none) makes varlist truly empty when unspecified.
*!        Also fixed: sparkta_version display local was hardcoded 3.5.42.
*!        Ado-only fix, no Java recompile needed.
*! F-1c: pie/donut three-mode support.
*! F-1c: pie/donut three-mode support matching Stata graph pie.
*!        Mode 1: sparkta price mpg, type(pie)            multi-var slices
*!        Mode 2: sparkta price, type(pie) over(rep78)    one var + over()
*!        Mode 3: sparkta, type(pie) over(rep78)          frequency counts
*!        ado: syntax varlist -> [varlist] (optional). Pie validation rewritten.
*!        markout guarded for empty varlist. ChartRenderer.pie() rewritten.
*!        Also: all slider tests S1-S8 confirmed working (v3.5.48-F1a).
*!        Java recompile required (ChartRenderer.java changed).
*! F-1b: showmissing over() bar order fix in engine (DataEmbedder).
*! F-1b: Fix showmissing over() bar order and color.
*!        embedCatVar() was using buildCategoryList() which put (Missing) at
*!        index 0 in _sc/_si. DatasetBuilder renders missing at the END.
*!        Fix: use DataSet.uniqueValues(showMissing=true) in embedCatVar() so
*!        _si indices and _sc label order match the chart's x-axis exactly.
*!        overLabels in _smeta fixed to use showmissingOver flag.
*! F-1a: sliders(varlist) -- dual-handle numeric range filter UI.
*!        Stata syntax: sliders(price mpg) -- any numeric variable.
*!        Engine: filterRows() extended to apply {lo,hi} range per slider var.
*!        No new CDN libs -- slider UI is pure HTML/CSS/JS (offline-safe).
*!        Arg 151: pipe-sep slider varlist. hasAnyFilter() activates engine.
*! F-0e: Fix blank chart after filter change when over() is a numeric variable.
*!        DataEmbedder was only embedding over()/by() into _si/_sc for string
*!        vars; numeric over() was in _sd only. Engine aggregate() reads _si
*!        for row->group mapping -- if _si[groupVar] is undefined every bucket
*!        is empty -> null data -> blank chart.  Fix: always embed over()/by()
*!        into _si/_sc regardless of numeric/string type.  Java-only fix.
*! F-0d: Remove stray closing brace left over from F-0b/F-0c edit
*!        of the filters() block. Ado-only fix, no Java change.
*! F-0c: Remove filter()/filter2() from syntax block to eliminate
*!        Stata abbreviation conflict with filters().
*!        filters() is now the sole filter option.
*!        Back-compat: filter()/filter2() accepted via manual parse
*!        with migration hint in error message. Ado-only change.
*! F-0b: filters() option -- unlimited filter variables.
*!        filters(v1 v2 v3 ...) replaces filter()/filter2().
*!        Old filter()/filter2() still accepted for back-compat.
*!        Java wiring: arg 76 now carries pipe-sep varlist.
*!        Ado-only change, no Java recompile needed.
*! F-0: Row-level filter engine. Replaces combinatorial _dashData
*!       pre-computation with DataEmbedder (_sd/_si/_sc/_smeta) +
*!       sparkta_engine.js browser aggregation. Unlimited filter vars.
*!       Cardinality check added (max 500 unique values per filter var).
*!       Java recompile required.
*! v3.5.42: Bulletproof jar search: findfile sparkta.jar directly (Path 1),
*!           store sysdir_PLUS/personal in locals before confirm file checks.
*!           No Java recompile needed.
*! v3.5.42: Reverted c(source) (not a real Stata constant -- was a hallucination).
*!           findfile is the correct and well-documented primary lookup. Added missing
*!           sysdir_personal forward-slash variant (Mac/Linux) and sysdir_PLUS/s/sparkta/
*!           explicit fallback. HtmlGenerator.VERSION bumped to 3.5.42 for consistency.
*!           Java recompile required (VERSION constant change).
*! v3.5.40: Removed hardcoded C:\ado\personal path. Added sysdir_PLUS subfolder and
*!           Mac/Linux separator variants. Ado-only change.
*! v3.5.39: robust jar search. findfile(sparkta.ado) now primary lookup -- finds jar
*!           wherever Stata installed the ado (SSC PLUS/s/sparkta/, net install personal/,
*!           or any adopath location). Added sysdir_PLUS/s/sparkta/ fallback and Mac/Linux
*!           forward-slash sysdir_personal variants. Removed hardcoded C:\ado\personal path.
*!           sparkta_check.ado updated to match. Ado-only, no Java recompile needed.
*! v3.5.36 Fix: leglabels() generateLabels callback now emitted in colorByCategory mode
*!         (bar + over() + single var). Root cause: _leglabelsOnXAxis=true was
*!         suppressing generateLabels. New _leglabelsColorByCategory flag allows it
*!         through when needed. Java recompile required.
*!          Cross-chart behavior table, worked examples, version history entries.
*!          No code changes. No recompile needed.
*! v3.5.34 Fix relabel() legend in colorByCategory mode: now shows N colored entries
*!          (one per bar) instead of one. Add nolegend flag (alias for legend(none)).
*! v3.5.28: fix ALL option abbreviation conflicts across all 142 options.
*!          FILTEr/FILTER2, LPATTErn/LPATTERns, ANIMDElay/ANIMDUration,
*!          TOOLTIPBOrder. Zero conflicts confirmed by audit. Ado-only.
*! v3.5.11: fix tooltip option abbreviation conflicts (tooltipformat vs tooltipfontsize,
*!          tooltipposition vs tooltippadding). Raise min abbrevs: TOOLTIPFORmat,
*!          TOOLTIPFONtsize, TOOLTIPPOSition, TOOLTIPPADding. Ado-only fix.
*! v3.5.10: auto axis titles from variable label/name (bar/line/area/boxplot/violin).
*! v3.5.8: O(N*G)->O(N) group index (Variable.groupIndex()), string over()/by() missing fix.
*! v3.5.4: stackedhbar added (stacked horizontal bar, non-100%%).
*! v3.5.3: alabelpos() minute-clock direction (3rd token per entry); alabelgap() offset px (arg 149).
*! v3.5.0: Phase 2-B: reference annotations (args 127-136, 140-148).
*!   19 new options: yline, xline, ylinecolor, xlinecolor, ylinelabel, xlinelabel,
*!   yband, xband, ybandcolor, xbandcolor (lines/bands, args 127-136);
*!   apoint, apointcolor, apointsize (points, args 140-142);
*!   alabelpos, alabeltext, alabelfs (labels, args 143-145);
*!   aellipse, aellipsecolor, aellipseborder (ellipses, args 146-148).
*!   CDN lib 5: chartjs-plugin-annotation@3.0.1 (auto-registers).
*!   buildAnnotationConfig(allowX) single chokepoint in ChartRenderer.
*!   Offline: annotation lib added to pre-flight only when hasAnnotations()=true.
*! v3.4.1: leglabels() fix -- auto-suppress legend on single-dataset by() panels.
*!   _singleDatasetByPanel transient flag set in buildByScripts(); read by
*!   buildLegendConfig() and legendLabelsCfg() in ChartRenderer.
*!   leglabels() now correctly skipped when panel has no over() (nothing to rename).
*! v3.4.0: Phase 2-C: leglabels(), xticks(), yticks() (args 137-139).
*!   leglabels(): pipe-sep legend entry renames via generateLabels callback.
*!   xticks()/yticks(): pipe-sep numeric tick values via afterBuildTicks callback.
*!   No conflict with noticks: afterBuildTicks runs at scale-build, beforeDraw later.
*!   Args 127-136 reserved as placeholders for Phase 2-B (reference lines/bands).
*! v3.3.1: Compound themes: dark_<palette> and light_<palette> now accepted.
*!   isDark() helper in HtmlGenerator propagated to all 4 renderers (18 sites).
*!   ado theme validation expanded to all bare + compound combinations.
*! v3.3.0: Phase 2-D: 7 named color palettes via theme() option.
*!   tab1 (Tableau10), tab2 (Set1), tab3 (Dark2), cblind1 (Okabe-Ito),
*!   neon, swift_red, viridis. namedScheme() in HtmlGenerator.java.
*!   dark/light theme keywords preserved; colors() still overrides palette.
*! v3.2.4: gradcolors() per-series support -- colon-delimited pairs.
*! v3.2.3: gradient flash fix -- init _grad vars to transparent not null.
*! v3.2.2: gradient fix -- null init + re-entry guard for onComplete.
*! v3.2.1: gradient fill fix -- onComplete pattern for correct canvas dimensions.
*! v3.2.0: Phase 1-E: notesize(), gradient (args 125-126).
*!   notesize(): applies user CSS font-size value to both .note and .caption classes.
*!   gradient: vertical canvas gradient fill for area and bar charts.
*!     Area: full-opacity color at top -> transparent at bottom.
*!     Bar: full-opacity at top -> 60% opacity at bottom (subtle depth).
*!     buildGradientPreamble() in HtmlGenerator emits JS preamble creating
*!     window._grad{i} gradient objects; DatasetBuilder references them as
*!     backgroundColor values. Gradient skipped for line-only, scatter,
*!     pie/donut, histogram, boxplot/violin.
*!   args.length check bumped 125->127. All files updated.
*! v3.1.0: Phase 1-D: lpattern(), lpatterns(), nopoints, pointhoversize() (args 121-124).
*!   lpatternToBorderDash() helper in DatasetBuilder is the single source of truth
*!   for pattern->borderDash translation; resolvedBorderDash() handles per-series
*!   resolution (lpatterns takes precedence over lpattern, cycles if fewer entries).
*!   linePointRadius() and linePointHoverRadius() helpers centralise nopoints and
*!   pointhoversize logic; all three line/area emission sites (buildDatasetStyle,
*!   numDatasets, ciLineDatasets) delegate to these helpers.
*!   ciline: borderDash and nopoints applied to mean line only; CI band boundary
*!   lines keep their intentional pointRadius:0 and fixed borderDash:[4,3].
*!   args.length check bumped 121->125. All five files updated.
*! v2.7.5: SVG blank-output fix: pre-scale c2sCtx by devicePixelRatio; chart-goes-blank
*!   fix: call chartInst.update("none") after ctx restore. v2.7.4 stubs retained.
*!   canvas2svg 1.0.16 is missing methods Chart.js 4.4 calls during draw().
*!   Missing: resetTransform, roundRect, getTransform, ellipse,
*!   createConicGradient, isPointInPath, isPointInStroke.
*!   Fix: after new C2S(), iterate _c2sMethods and bind each stub only if
*!   the method is absent (typeof !== function). Existing real implementations
*!   in future canvas2svg versions are preserved. v2.7.4
*! v2.7.3: SVG draw() fix: chart.attached patch.
*!   Chart.js 4 draw() checks this.attached (getter: canvas.isConnected).
*!   canvas2svg SVG element not in DOM -> isConnected=false -> draw() returns
*!   immediately -> empty SVG string. Fix: defineProperty on chartInst to shadow
*!   prototype getter with true; delete in finally to restore. Also patches
*!   c2sCtx.canvas.ownerDocument = document for secondary checks. v2.7.3
*! v2.7.2: SVG canvas dimension patch + toolbar placement fix.
*!   (1) Object.defineProperty patches c2sCtx.canvas.width/height to plain
*!       integers. SVG element returns SVGAnimatedLength objects -- Chart.js
*!       reads these as numbers, gets object -> NaN -> empty SVG. v2.7.2
*!   (2) try/catch/finally added: errors surfaced via console.error + alert.
*!       Empty SVG guard (length<50) prevents silent blank downloads. v2.7.2
*!   (3) Download buttons moved from absolute overlay to flow-positioned
*!       toolbar row above canvas. No more overlap with chart content. v2.7.2
*! v2.7.1: Three fixes from download testing:
*!   (1) SVG fix: removed chartInst.canvas=ctx.canvas (canvas2svg has no
*!       .canvas property -- set it to undefined, draw() failed silently).
*!       Now swaps only chartInst.ctx; try/finally guarantees restore.
*!       Uses offsetWidth/Height for CSS-pixel SVG viewport. v2.7.1
*!   (2) over()+by() unblocked: replaced blanket guard with narrow check
*!       that only blocks pie/donut (where the combo is meaningless).
*!       All other types (bar, line, scatter, cibar, boxplot etc) now
*!       support over() within by() panels. Java already handled this.
*!   (3) Test 3 (bar+over+by) now works as a consequence of fix (2).
*! v2.7.0: Phase 2-A: download option -- PNG + SVG export buttons.
*! v2.6.2: by()+filter() now works: all panels update on dropdown change.
*! v2.6.1: Three rendering fixes (see changelog).
*! v2.6.0: Architecture refactor (DashboardOptions inner classes) + Phase 1-A+B styling.
*!          StyleOptions, AxisOptions, ChartOptions, StatOptions inner classes.
*!          18 new styling opts: titlesize/color, subtitlesize/color, xtitlesize/color,
*!          ytitlesize/color, xlabsize/color, ylabsize/color, legcolor, legbgcolor,
*!          tooltipbg, tooltipborder, tooltipfontsize, tooltippadding (args 97-114).
*!          Shared builder methods: tooltipStylePrefix(), axisTitleCfg(),
*!          axisTickStyleCfg(), legendLabelsCfg() in ChartRenderer.
*! v2.4.10: Revert boxplot+over() to single dataset (per-element bg[] array, fixes alignment).
*!           mediancolor()/meancolor() options. Auto white/black from avg palette luminance.
*! v2.4.9: F1 global median/mean color (avg palette luminance), F2 meanRadius 5, F3 null-pad no-over() boxplot
*! v2.4.8: Fix E1: violin median/mean colors computed per-dataset from each
*!          violin's own fill -- medianColorFor(col(dsIdx)) per violin dataset.
*!          Fix E2: boxplot+over() single-var now one-dataset-per-group so each
*!          group box gets medianColorFor(col(gi)) -- correct contrast always.
*!          Fix E3: boxplot no-over() multi-var now one-dataset-per-variable
*!          so each variable box gets medianColorFor(col(vi)) correctly.
*!          Root cause: @sgratzl plugin applies medianColor as dataset-level
*!          scalar only; per-element arrays do not exist for this property.
*! v2.4.8: Fix A: outlier hollow circle (transparent fill, colored border).
*!          Fix B: suppress Chart.js default legend for box/violin.
*!          Fix C: hbox and hviolin type support + horizontal rendering.
*!          Fix D: boxplot/violin _initChart wrapping for filter interaction.
*! v2.4.6: Adaptive median/mean colors (luminance rule), violin 3-item legend, whisker swatch #cccccc
*! v2.4.5: violin/boxplot: median=#ffffff (white), mean=#f59e0b (amber);
*!         inline canvas legend plugin (bpInlineLegend) in chart area
*! Stata sparkta -- interactive visualization builder using Java Plugin Interface
*! v1.6.0: Replace obslist macro with touse variable name to remove ~13K obs limit
*! v1.6.1: Fix aggregate() median to use Stata-compatible percentile formula
*! v1.7.0: Add cibar and ciline chart types with cilevel() option
*! v1.7.2: Add cibandopacity() option
*! v1.7.3: Fix filter destroy+reinit; _initChart() approach replaces JSON.parse
*! v1.8.0: Add histogram chart type with bins() and histtype() options
*! v1.8.1: Fix double-comma JS syntax error in buildHistXScale
*! v1.8.2: Fix histogram filter -- move _ttRanges_ preamble out of histogram() so var _mainChart= inserts correctly
*! v2.0.0: Modular Java refactor -- ChartRenderer, DatasetBuilder, FilterRenderer, StatsRenderer
*! v2.0.1: areaopacity() option; area charts auto-set beginAtZero; sort area series by fill size
*! v2.0.2: offline option -- embed Chart.js inline for fully self-contained air-gapped HTML
*! v2.0.3: offline graceful CDN fallback when libs missing; online/offline mode in status msg
*! v2.0.4: strict offline (no CDN fallback ever); pre-flight check fails fast with fix instructions;
*!          build.bat bundles resources into jar; fetch_js_libs.bat adds pause and size verification
*! v2.0.5: fetch_js_libs.bat adds --ssl-no-revoke for institutional/corporate networks
*! v2.0.6: export() path fix -- expand ~ on Windows, normalise separators,
*!          show resolved path before writing, clear error if write fails
*! v2.4.4: Five boxplot/violin visual fixes:
*!  (1) Color by group: single var + over() now colors each box by group
*!      using per-element backgroundColor/borderColor arrays.
*!  (2) Color by variable: no-over() now colors each box by variable
*!      using per-element color arrays via buildBoxColorArrays().
*!  (3) Violin data format: violin type now passes raw number arrays
*!      via buildViolinPoint() instead of pre-computed stat objects.
*!      Plugin computes its own KDE from raw values.
*!  (4) Outliers clipped: added minStats/maxStats/grace to y-axis scale
*!      config (buildBoxYAxisConfig) so axis bounds include all outlier
*!      dots. grace:5pct prevents edge clipping.
*!  (5) Median/mean contrast: medianColor/meanColor now white so lines
*!      are visible against the colored box fill. borderWidth raised to 2.
*! v2.4.4: Box plot visual fixes (requires recompile).
*!          1. Colors per over() group: single-var boxplot/violin now uses
*!             per-element backgroundColor[]/borderColor[] arrays so each
*!             group box gets its own palette color.
*!          2. Median/mean line visibility: medianColor and meanBackground
*!             Color now #ffffff (white) so lines are visible against the
*!             colored fill (was same color as fill = invisible).
*!          3. Outlier clipping: y-axis now uses minStats/maxStats/grace
*!             so scale auto-extends to include all outlier dots with 5pct
*!             padding at edges.
*!          4. Violin tooltip: ctx.raw is a raw number array for violin;
*!             tooltip now branches on Array.isArray(ctx.raw) and computes
*!             five-number summary in JS for violin; boxplot path unchanged.
*!          5. Multi-var no-over y-scale: does not force beginAtZero so
*!             price/weight/mpg each fill the available scale space.
*! v2.4.4: Four boxplot/violin rendering fixes (compile fix: o.dark->
*!          o.theme.equals("dark") -- DashboardOptions has no .dark field):
*!  (1) COLORS: boxplot+over() single-var now emits per-element color arrays
*!      (one palette color per group box). Multi-var: one color per dataset.
*!  (2) MEDIAN/MEAN LINES: medianColor/meanBackgroundColor set to white (#ffffff)
*!      so lines are visible against the colored box fill.
*!  (3) VIOLIN: violin+over() now emits one dataset per group (null-padded
*!      for other positions). Violin type does not support per-element color
*!      arrays. Each group violin gets its own palette color. Tooltip mode
*!      changed to nearest to avoid showing null-padded groups.
*!  (4) OUTLIER CLIPPING: minStats/maxStats moved from scale config to
*!      dataset level (options.boxplot.datasets.*). Scale-level placement
*!      has no effect -- dataset-level is the correct API per plugin docs.
*!      Recompile required.
*! v2.4.2: fetch_js_libs.bat fix. The @ symbol in the scoped npm URL
*!          (@sgratzl/chartjs-chart-boxplot) caused Windows batch to
*!          misinterpret @ as a command modifier, closing the window.
*!          Fix: store URL in variable BP_URL before echo and curl.
*!          Also added set CURL_RC immediately after curl for block 4.
*! v2.4.1: Fix boxplot blank chart. chartjs-chart-boxplot UMD does NOT
*!          auto-register. Must call Chart.register(BoxPlotController,
*!          ViolinController, BoxAndWiskers, Violin) before new Chart().
*!          UMD global is "ChartBoxPlot". Also fixed CDN URL: correct
*!          scoped package is @sgratzl/chartjs-chart-boxplot@4.4.5
*!          (not the unscoped chartjs-chart-boxplot@4.4.0 which 404s).
*! v2.4.0: Box plot and violin chart types. type(boxplot) and type(violin).
*!          Whiskers: Tukey k*IQR fences (default k=1.5), configurable via
*!          whiskerfence(). Outliers shown as dots. Five-number summary in
*!          tooltip. Works with or without over(). Requires
*!          chartjs-chart-boxplot@4.4.0 CDN plugin. Args length 94.
*! v2.3.0: Secondary y-axis. New options: y2(), y2title(), y2range().
*!          Variables listed in y2() are plotted on the right axis.
*!          Works for bar and line charts with over(). Args 90-92 added.
*! v2.2.1: cibar color fix. Single-variable cibar colors each bar by
*!          its group (matching type(bar)). Multi-variable keeps one color
*!          per series. errorBar whiskers match bar color.
*! v2.2.0: 100% stacked bar/hbar. type(stackedbar100) and type(stackedhbar100).
*!          Each column normalised to 100%%; values shown as percentage share.
*!          Tooltip: variable name, Share %%, raw stat value, column total.
*!          Useful for composition comparisons across groups.
*! v2.1.2: Scatter/bubble axes swapped to match Stata convention (first variable = y-axis).
*!          scatter label now shows "y vs x". Compile errors fixed (illegal escape
*!          sequences in Java regex and JS regex strings).
*! v2.1.1: Tooltip redesign (revised). Clean consistent layout:
*!          title=group, variable name, stat label, CI [lo-hi], N each on own line.
*!          Scatter/bubble: y variable shown before x (Stata convention).
*!          CI bar/line: N now shown correctly from embedded data point (was "--").
*!          No stat label duplication. tooltipformat() respected everywhere.
*! v2.1.0: Tooltip redesign -- multi-line structured tooltips for all chart types.
*!          Bar/line: title=group, stat label (Mean/Median/Sum etc), N shown.
*!          CI bar/line: Mean + "95% CI [lo - hi]" + N each on own line.
*!          Scatter/bubble: axis-labelled x/y values. Hist: bin range in title.
*!          Pie/donut: slice % with raw sum. tooltipformat() respected everywhere.
*! v3.5.38: sthlp fix -- xticks() Options detail block was missing its opening phang/opt line;
*!           block started mid-sentence. Sthlp-only change, no recompile needed.
*! v2.0.9: sparkta_check moved to sparkta_check.ado; "Dashboard ready" -> "Sparkta ready"
*! v2.0.8: renamed from dashboard to sparkta across all files
*! v2.0.7: fetch_js_libs.bat rewritten -- all 3 files attempt regardless of
*!          individual failures, no goto, per-file result, URL shown, pause always

