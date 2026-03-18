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
REM --ssl-no-revoke: bypasses certificate revocation checks on
REM institutional/corporate networks (schannel error 0x80092012).
REM --retry 2: retries twice on transient failures before giving up.
REM Window stays open after all attempts regardless of success or failure.
REM v3.5.96

setlocal EnableDelayedExpansion

set DEST=src\main\resources\com\dashboard\js
if not exist "%DEST%" mkdir "%DEST%"
set FAILURES=0

echo.
echo  =========================================
echo   Fetching JS libraries for offline mode
echo  =========================================
echo.

REM == [1/5] chart.js ==========================================================
echo  [1/5] Fetching chart.js@4.4.0...
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
echo  [2/5] Fetching chartjs-plugin-datalabels@2.2.0...
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
echo  [3/5] Fetching chartjs-chart-error-bars@4.4.0...
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
echo  [4/5] Fetching sgratzl/chartjs-chart-boxplot@4.4.5...
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

REM == Summary ==================================================================
if %FAILURES%==0 (
    echo  =========================================
    echo   All 6 libraries downloaded successfully.
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
pause
endlocal
