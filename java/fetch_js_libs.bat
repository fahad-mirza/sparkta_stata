@echo off
REM fetch_js_libs.bat -- Step 1 of the Sparkta build process (Windows)
REM
REM PURPOSE: Downloads the five pinned JavaScript libraries that Sparkta
REM bundles into the jar for offline chart viewing. These files must be
REM present before you run build.bat (Step 2).
REM
REM ORDER:  1. fetch_js_libs.bat   <- YOU ARE HERE
REM         2. build.bat
REM
REM Run this once. Re-run only if you need to refresh the libraries or
REM if a new library (e.g. chartjs-boxplot, canvas2svg) has been added to the package.
REM
REM v3.6.0-s9y: ALSO fetches four Java libraries (Maven Central) into java\lib\ for the
REM fast export path (saveas(): SVG -> PNG at any DPI and vector PDF in pure Java).
REM They are optional: without them build.bat still builds sparkta.jar and saveas()
REM falls back to one headless-browser run per format.
REM
REM --ssl-no-revoke: bypasses certificate revocation checks on
REM institutional/corporate networks (schannel error 0x80092012).
REM --retry 2: retries twice on transient failures before giving up.
REM Window stays open after all attempts regardless of success or failure.
REM v2.7.0

setlocal EnableDelayedExpansion

set DEST=src\main\resources\com\dashboard_test\js
if not exist "%DEST%" mkdir "%DEST%"
set FAILURES=0

echo.
echo  =========================================
echo   Fetching JS libraries for offline mode
echo  =========================================
echo.

REM == [1/5] chart.js ==========================================================
echo  [1/6] Fetching chart.js@4.4.0...
echo        https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js
curl -L --show-error --ssl-no-revoke --retry 2 "https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js" -o "%DEST%\chartjs-4.4.0.min.js"
set CURL_RC=%ERRORLEVEL%
if %CURL_RC% neq 0 (
    echo  [FAIL] chart.js -- curl exit code %CURL_RC%
    set /a FAILURES+=1
) else (
    for %%F in ("%DEST%\chartjs-4.4.0.min.js") do set SZ=%%~zF
    if !SZ! LSS 10000 (
        echo  [FAIL] chart.js -- file too small (!SZ! bytes^), likely an error page
        set /a FAILURES+=1
    ) else (
        echo  [OK]   chart.js -- !SZ! bytes
    )
)
echo.

REM == [2/5] chartjs-plugin-datalabels =========================================
echo  [2/6] Fetching chartjs-plugin-datalabels@2.2.0...
echo        https://cdn.jsdelivr.net/npm/chartjs-plugin-datalabels@2.2.0/dist/chartjs-plugin-datalabels.min.js
curl -L --show-error --ssl-no-revoke --retry 2 "https://cdn.jsdelivr.net/npm/chartjs-plugin-datalabels@2.2.0/dist/chartjs-plugin-datalabels.min.js" -o "%DEST%\chartjs-datalabels-2.2.0.min.js"
set CURL_RC=%ERRORLEVEL%
if %CURL_RC% neq 0 (
    echo  [FAIL] chartjs-plugin-datalabels -- curl exit code %CURL_RC%
    set /a FAILURES+=1
) else (
    for %%F in ("%DEST%\chartjs-datalabels-2.2.0.min.js") do set SZ=%%~zF
    if !SZ! LSS 1000 (
        echo  [FAIL] chartjs-plugin-datalabels -- file too small (!SZ! bytes^), likely an error page
        set /a FAILURES+=1
    ) else (
        echo  [OK]   chartjs-plugin-datalabels -- !SZ! bytes
    )
)
echo.

REM == [3/5] chartjs-chart-error-bars ==========================================
echo  [3/6] Fetching chartjs-chart-error-bars@4.4.0...
echo        https://cdn.jsdelivr.net/npm/chartjs-chart-error-bars@4.4.0/build/index.umd.min.js
curl -L --show-error --ssl-no-revoke --retry 2 "https://cdn.jsdelivr.net/npm/chartjs-chart-error-bars@4.4.0/build/index.umd.min.js" -o "%DEST%\chartjs-errorbars-4.4.0.min.js"
set CURL_RC=%ERRORLEVEL%
if %CURL_RC% neq 0 (
    echo  [FAIL] chartjs-chart-error-bars -- curl exit code %CURL_RC%
    set /a FAILURES+=1
) else (
    for %%F in ("%DEST%\chartjs-errorbars-4.4.0.min.js") do set SZ=%%~zF
    if !SZ! LSS 1000 (
        echo  [FAIL] chartjs-chart-error-bars -- file too small (!SZ! bytes^), likely an error page
        set /a FAILURES+=1
    ) else (
        echo  [OK]   chartjs-chart-error-bars -- !SZ! bytes
    )
)
echo.

REM == [4/5] chartjs-chart-boxplot ==========================================
REM NOTE: URL contains @ (scoped npm package). Store in variable to prevent
REM the Windows shell from misinterpreting @ as a command modifier.
set BP_URL=https://cdn.jsdelivr.net/npm/@sgratzl/chartjs-chart-boxplot@4.4.5/build/index.umd.min.js
echo  [4/6] Fetching sgratzl/chartjs-chart-boxplot@4.4.5...
echo        %BP_URL%
curl -L --show-error --ssl-no-revoke --retry 2 "%BP_URL%" -o "%DEST%\chartjs-boxplot-4.4.5.min.js"
set CURL_RC=%ERRORLEVEL%
if %CURL_RC% neq 0 (
    echo  [FAIL] chartjs-chart-boxplot -- curl exit code %CURL_RC%
    set /a FAILURES+=1
) else (
    for %%F in ("%DEST%\chartjs-boxplot-4.4.5.min.js") do set SZ=%%~zF
    if !SZ! LSS 10000 (
        echo  [FAIL] chartjs-chart-boxplot -- file too small (!SZ! bytes^), likely error page
        set /a FAILURES+=1
    ) else (
        echo  [OK]   chartjs-chart-boxplot -- !SZ! bytes
    )
)
echo.

REM == [5/6] chartjs-plugin-annotation =========================================
echo  [5/6] Fetching chartjs-plugin-annotation@3.0.1...
echo        https://cdn.jsdelivr.net/npm/chartjs-plugin-annotation@3.0.1/dist/chartjs-plugin-annotation.min.js
set ANNOT_URL=https://cdn.jsdelivr.net/npm/chartjs-plugin-annotation@3.0.1/dist/chartjs-plugin-annotation.min.js
curl -L --show-error --ssl-no-revoke --retry 2 "%ANNOT_URL%" -o "%DEST%\chartjs-annotation-3.0.1.min.js"
set CURL_RC=%ERRORLEVEL%
if %CURL_RC% neq 0 (
    echo  [FAIL] chartjs-plugin-annotation -- curl exit code %CURL_RC%
    set /a FAILURES+=1
) else (
    for %%F in ("%DEST%\chartjs-annotation-3.0.1.min.js") do set SZ=%%~zF
    if !SZ! LSS 1000 (
        echo  [FAIL] chartjs-plugin-annotation -- file too small (!SZ! bytes^), likely error page
        set /a FAILURES+=1
    ) else (
        echo  [OK]   chartjs-plugin-annotation -- !SZ! bytes
    )
)
echo.

REM == [6/6] canvas2svg =====================================================
echo  [6/6] Fetching canvas2svg@1.0.19 (from GitHub -- npm only has 1.0.16)...
echo        https://raw.githubusercontent.com/gliffy/canvas2svg/master/canvas2svg.js
curl -L --show-error --ssl-no-revoke --retry 2 "https://raw.githubusercontent.com/gliffy/canvas2svg/master/canvas2svg.js" -o "%DEST%\canvas2svg-1.0.19.js"
set CURL_RC=%ERRORLEVEL%
if %CURL_RC% neq 0 (
    echo  [FAIL] canvas2svg -- curl exit code %CURL_RC%
    set /a FAILURES+=1
) else (
    for %%F in ("%DEST%\canvas2svg-1.0.19.js") do set SZ=%%~zF
    if !SZ! LSS 1000 (
        echo  [FAIL] canvas2svg -- file too small (!SZ! bytes^), likely error page
        set /a FAILURES+=1
    ) else (
        echo  [OK]   canvas2svg -- !SZ! bytes
    )
)

REM == Java libraries for the fast export path (optional) =====================
set JLIB=lib
if not exist "%JLIB%" mkdir "%JLIB%"
echo.
echo  =========================================
echo   Fetching Java libraries (saveas fast path)
echo  =========================================
echo.
REM == [J1/5] jsvg-2.1.0.jar 
echo  [J1/5] Fetching jsvg-2.1.0.jar...
echo        https://repo1.maven.org/maven2/com/github/weisj/jsvg/2.1.0/jsvg-2.1.0.jar
curl -L --show-error --ssl-no-revoke --retry 2 "https://repo1.maven.org/maven2/com/github/weisj/jsvg/2.1.0/jsvg-2.1.0.jar" -o "%JLIB%\jsvg-2.1.0.jar"
set CURL_RC=%ERRORLEVEL%
if %CURL_RC% neq 0 (
    echo  [FAIL] jsvg-2.1.0.jar -- curl exit code %CURL_RC%
    set /a FAILURES+=1
) else (
    for %%F in ("%JLIB%\jsvg-2.1.0.jar") do set SZ=%%~zF
    if !SZ! LSS 400000 (
        echo  [FAIL] jsvg-2.1.0.jar -- file too small (!SZ! bytes^), likely an error page
        set /a FAILURES+=1
    ) else (
        echo  [OK]   jsvg-2.1.0.jar -- !SZ! bytes
    )
)
echo.
REM == [J2/5] graphics2d-3.0.5.jar 
echo  [J2/5] Fetching graphics2d-3.0.5.jar...
echo        https://repo1.maven.org/maven2/de/rototor/pdfbox/graphics2d/3.0.5/graphics2d-3.0.5.jar
curl -L --show-error --ssl-no-revoke --retry 2 "https://repo1.maven.org/maven2/de/rototor/pdfbox/graphics2d/3.0.5/graphics2d-3.0.5.jar" -o "%JLIB%\graphics2d-3.0.5.jar"
set CURL_RC=%ERRORLEVEL%
if %CURL_RC% neq 0 (
    echo  [FAIL] graphics2d-3.0.5.jar -- curl exit code %CURL_RC%
    set /a FAILURES+=1
) else (
    for %%F in ("%JLIB%\graphics2d-3.0.5.jar") do set SZ=%%~zF
    if !SZ! LSS 20000 (
        echo  [FAIL] graphics2d-3.0.5.jar -- file too small (!SZ! bytes^), likely an error page
        set /a FAILURES+=1
    ) else (
        echo  [OK]   graphics2d-3.0.5.jar -- !SZ! bytes
    )
)
echo.
REM == [J3/5] pdfbox-3.0.5.jar 
echo  [J3/5] Fetching pdfbox-3.0.5.jar...
echo        https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/3.0.5/pdfbox-3.0.5.jar
curl -L --show-error --ssl-no-revoke --retry 2 "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/3.0.5/pdfbox-3.0.5.jar" -o "%JLIB%\pdfbox-3.0.5.jar"
set CURL_RC=%ERRORLEVEL%
if %CURL_RC% neq 0 (
    echo  [FAIL] pdfbox-3.0.5.jar -- curl exit code %CURL_RC%
    set /a FAILURES+=1
) else (
    for %%F in ("%JLIB%\pdfbox-3.0.5.jar") do set SZ=%%~zF
    if !SZ! LSS 2000000 (
        echo  [FAIL] pdfbox-3.0.5.jar -- file too small (!SZ! bytes^), likely an error page
        set /a FAILURES+=1
    ) else (
        echo  [OK]   pdfbox-3.0.5.jar -- !SZ! bytes
    )
)
echo.
REM == [J4/5] fontbox-3.0.5.jar 
echo  [J4/5] Fetching fontbox-3.0.5.jar...
echo        https://repo1.maven.org/maven2/org/apache/pdfbox/fontbox/3.0.5/fontbox-3.0.5.jar
curl -L --show-error --ssl-no-revoke --retry 2 "https://repo1.maven.org/maven2/org/apache/pdfbox/fontbox/3.0.5/fontbox-3.0.5.jar" -o "%JLIB%\fontbox-3.0.5.jar"
set CURL_RC=%ERRORLEVEL%
if %CURL_RC% neq 0 (
    echo  [FAIL] fontbox-3.0.5.jar -- curl exit code %CURL_RC%
    set /a FAILURES+=1
) else (
    for %%F in ("%JLIB%\fontbox-3.0.5.jar") do set SZ=%%~zF
    if !SZ! LSS 1000000 (
        echo  [FAIL] fontbox-3.0.5.jar -- file too small (!SZ! bytes^), likely an error page
        set /a FAILURES+=1
    ) else (
        echo  [OK]   fontbox-3.0.5.jar -- !SZ! bytes
    )
)
echo.
REM == [J5/5] pdfbox-io-3.0.5.jar (PDFBox 3.0 split its io package into this artifact)
echo  [J5/5] Fetching pdfbox-io-3.0.5.jar...
echo        https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-io/3.0.5/pdfbox-io-3.0.5.jar
curl -L --show-error --ssl-no-revoke --retry 2 "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-io/3.0.5/pdfbox-io-3.0.5.jar" -o "%JLIB%\pdfbox-io-3.0.5.jar"
set CURL_RC=%ERRORLEVEL%
if %CURL_RC% neq 0 (
    echo  [FAIL] pdfbox-io-3.0.5.jar -- curl exit code %CURL_RC%
    set /a FAILURES+=1
) else (
    for %%F in ("%JLIB%\pdfbox-io-3.0.5.jar") do set SZ=%%~zF
    if !SZ! LSS 20000 (
        echo  [FAIL] pdfbox-io-3.0.5.jar -- file too small (!SZ! bytes^), likely an error page
        set /a FAILURES+=1
    ) else (
        echo  [OK]   pdfbox-io-3.0.5.jar -- !SZ! bytes
    )
)
echo.
echo  NOTE: if pdfbox/fontbox 3.0.5 are not the newest 3.0.x, edit the three URLs above
echo        to the same 3.0.x number (graphics2d 3.0.5 works with any PDFBox 3.0.x).
echo.
pause
endlocal
REM == Summary ==================================================================
if %FAILURES%==0 (
    echo  =========================================
    echo   All libraries downloaded successfully (6 JS + 5 Java).
    echo   Next step: run build.bat
    echo  =========================================
) else (
    echo  =========================================
    echo   %FAILURES% file(s^) failed to download.
    echo   Successfully downloaded files are kept.
    echo   For any failed file, download it manually
    echo   from the URL shown above and place it in:
    echo   %DEST%
    echo   Then run build.bat directly.
    echo  =========================================
)
echo.
