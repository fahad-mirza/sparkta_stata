@echo off
REM ============================================================
REM  build.bat - Sparkta build script (Windows)
REM  Configured for Stata 19 Now (StataNow19) with Zulu JDK 21 (auto-detected)
REM  Uses Stata's bundled Java - no Maven or external tools needed
REM  v2.7.0
REM ============================================================

setlocal ENABLEDELAYEDEXPANSION

echo.
echo  =========================================
echo   Sparkta - Build Script
echo  =========================================
echo.
echo  IMPORTANT: Run fetch_js_libs.bat BEFORE this script if you have not
echo  already done so. The JS libraries must be downloaded and placed in
echo  src\main\resources\com\dashboard_test\js\ before the jar is built.
echo  Without them the offline option will fail at runtime.
echo.

REM --- Step 1: Configure paths ------------------------------------------------
REM These paths are set for Stata 19 Now. Edit if your installation differs.

set STATA_DIR=C:\Program Files\StataNow19
set JAVA_ROOT=%STATA_DIR%\utilities\java\windows-x64

REM v3.6.0-s8b: auto-detect the bundled JDK. Stata updates replace the
REM zulu-jdk<version> folder (21.0.10 -> 21.0.12 ...), so pick the newest
REM zulu-jdk* directory that contains bin\javac.exe instead of hard-coding it.
set JAVA_BIN=
for /f "delims=" %%D in ('dir /b /ad /o-n "%JAVA_ROOT%\zulu-jdk*" 2^>nul') do (
    if not defined JAVA_BIN if exist "%JAVA_ROOT%\%%D\bin\javac.exe" set "JAVA_BIN=%JAVA_ROOT%\%%D\bin"
)
REM Fallback: explicit path (edit here if auto-detect fails)
if not defined JAVA_BIN set "JAVA_BIN=%JAVA_ROOT%\zulu-jdk21.0.12\bin"
set JAVAC=%JAVA_BIN%\javac.exe
set JAR_EXE=%JAVA_BIN%\jar.exe

REM Verify the bin folder exists
if not exist "%JAVA_BIN%\" (
    echo  [ERROR] Java bin folder not found at:
    echo          %JAVA_BIN%
    echo.
    echo  Please edit the JAVA_BIN variable in this script to match your path.
    pause
    exit /b 1
)

REM Verify javac.exe exists
if not exist "%JAVAC%" (
    echo  [ERROR] javac.exe not found at: %JAVAC%
    pause
    exit /b 1
)

REM Verify jar.exe exists
if not exist "%JAR_EXE%" (
    echo  [ERROR] jar.exe not found at: %JAR_EXE%
    pause
    exit /b 1
)

echo  [OK]  Stata directory : %STATA_DIR%
echo  [OK]  Java bin folder : %JAVA_BIN%
echo  [OK]  javac           : %JAVAC%
echo  [OK]  jar             : %JAR_EXE%
echo.

REM --- Step 2: Locate the Stata SFI jar ---------------------------------------
set SFI_JAR=
for /r "%STATA_DIR%\utilities\jar" %%F in (sfi-api.jar) do (
    set SFI_JAR=%%F
)

if not defined SFI_JAR (
    echo  [ERROR] sfi-api.jar not found under:
    echo          %STATA_DIR%\utilities\jar
    echo  Please check your Stata installation.
    pause
    exit /b 1
)

echo  [OK]  SFI jar: %SFI_JAR%
echo.

REM --- Step 3: Set up output directories --------------------------------------
set SCRIPT_DIR=%~dp0
set SRC_DIR=%SCRIPT_DIR%src\main\java
set OUT_DIR=%SCRIPT_DIR%out
set DIST_DIR=%SCRIPT_DIR%..\dist_test

if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"

if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"

REM --- Step 4: Compile --------------------------------------------------------
REM v3.6.0-s8b: the Python pre-build check is OPTIONAL. All static checks
REM (ado lint, javac against SFI stub, headless render, js_check) now run on
REM the review side via verify\verify_all.py before every handover. This
REM script must only require the JDK bundled with Stata.
REM Note: on Windows, a bare "python" may resolve to the Microsoft Store alias
REM stub, which prints a message and exits 9009 -- treat that as "not found".
echo  Running pre-build checks (optional, needs Python)...
set PYEXE=
for %%P in (python3.exe python.exe py.exe) do (
    if not defined PYEXE (
        "%%P" -c "import sys" >nul 2>&1 && set "PYEXE=%%P"
    )
)
if not defined PYEXE (
    echo  [SKIP] Python not available -- pre-build check skipped.
    echo         ^(verify\verify_all.py covers this on the review side.^)
) else (
    "%PYEXE%" check_build.py
    if errorlevel 1 (
        echo  [ERROR] Pre-build check failed. Fix errors above before compiling.
        pause
        exit /b 1
    )
)
echo  Compiling Java sources...

REM Collect all .java source files recursively
set SOURCES=
for /r "%SRC_DIR%" %%F in (*.java) do (
    set SOURCES=!SOURCES! "%%F"
)

REM Compile all sources against SFI jar using Stata's bundled JDK 21
"%JAVAC%" ^
    -cp "%SFI_JAR%" ^
    --release 11 ^
    -d "%OUT_DIR%" ^
    %SOURCES%

if %ERRORLEVEL% neq 0 (
    echo.
    echo  [ERROR] Compilation failed. See errors above.
    pause
    exit /b 1
)

echo  [OK]  Compilation successful.
echo.

REM --- Step 4b: Copy resources into OUT_DIR so they are bundled in the jar ----
REM This includes the JS libraries needed for offline mode.
set RES_DIR=%SCRIPT_DIR%src\main\resources
if exist "%RES_DIR%" (
    echo  Copying resources into build output...
    xcopy /E /I /Y /Q "%RES_DIR%" "%OUT_DIR%" >nul
    echo  [OK]  Resources copied.
) else (
    echo  [INFO] No resources directory found -- skipping resource copy.
    echo         (offline mode will not be available)
)
echo.

REM --- Step 4c: Verify JS libs present if resources exist --------------------
set JS_DIR=%OUT_DIR%\com\dashboard_test\js
set OFFLINE_READY=1
if not exist "%JS_DIR%\chartjs-4.4.0.min.js"                  set OFFLINE_READY=0
if not exist "%JS_DIR%\chartjs-datalabels-2.2.0.min.js"       set OFFLINE_READY=0
if not exist "%JS_DIR%\chartjs-errorbars-4.4.0.min.js"        set OFFLINE_READY=0
if not exist "%JS_DIR%\chartjs-boxplot-4.4.5.min.js"          set OFFLINE_READY=0
if not exist "%JS_DIR%\chartjs-annotation-3.0.1.min.js"       set OFFLINE_READY=0
if not exist "%JS_DIR%\canvas2svg-1.0.19.js"                  set OFFLINE_READY=0

if "%OFFLINE_READY%"=="1" (
    echo  [OK]  All 6 offline JS libraries verified in build output.
) else (
    REM t2i hardening: fail closed. Shipping a jar without the bundled libraries
    REM silently breaks the self-contained/offline default -- do not package it.
    echo  [ERROR] One or more offline JS libraries not found in the build output.
    echo          The self-contained/offline default would fail at runtime.
    echo          To fix: run fetch_js_libs.bat then rebuild.
    exit /b 1
)
echo.

REM --- Step 5: Package into jar -----------------------------------------------
echo  Packaging sparkta.jar...

"%JAR_EXE%" cf "%DIST_DIR%\sparkta.jar" -C "%OUT_DIR%" .

if %ERRORLEVEL% neq 0 (
    echo  [ERROR] JAR packaging failed.
    pause
    exit /b 1
)

echo  [OK]  sparkta.jar created at: %DIST_DIR%\sparkta.jar
echo.

REM --- Step 5b (v3.6.0-s9y): fast export path -- OPTIONAL ----------------------
REM If fetch_js_libs.bat fetched the five Java libraries into lib\, compile
REM src\export\...\SvgConvert.java against them and package a second jar,
REM sparkta-export.jar, that bundles those libraries (one classpath entry for the
REM ado). Without the libraries this step is skipped and saveas() uses the browser
REM for every format.
set LIB_DIR=%SCRIPT_DIR%lib
set EXPORT_READY=1
for %%J in (jsvg-2.1.0.jar graphics2d-3.0.5.jar pdfbox-3.0.5.jar fontbox-3.0.5.jar pdfbox-io-3.0.5.jar) do (
    if not exist "%LIB_DIR%\%%J" set EXPORT_READY=0
)
if "%EXPORT_READY%"=="1" (
    echo  Building sparkta-export.jar ^(fast PNG/PDF path^)...
    set EXP_OUT=%SCRIPT_DIR%build_export
    if exist "!EXP_OUT!" rmdir /S /Q "!EXP_OUT!"
    mkdir "!EXP_OUT!"
    "%JAVAC%" -cp "%LIB_DIR%\jsvg-2.1.0.jar;%LIB_DIR%\graphics2d-3.0.5.jar;%LIB_DIR%\pdfbox-3.0.5.jar;%LIB_DIR%\fontbox-3.0.5.jar;%LIB_DIR%\pdfbox-io-3.0.5.jar" --release 11 -d "!EXP_OUT!" "%SCRIPT_DIR%src\export\com\dashboard_test\export\SvgConvert.java"
    if !ERRORLEVEL! neq 0 (
        echo  [WARN] SvgConvert did not compile -- fast export path disabled ^(browser fallback still works^).
    ) else (
        pushd "!EXP_OUT!"
        for %%J in (jsvg-2.1.0.jar graphics2d-3.0.5.jar pdfbox-3.0.5.jar fontbox-3.0.5.jar pdfbox-io-3.0.5.jar) do "%JAR_EXE%" xf "%LIB_DIR%\%%J"
        if exist META-INF rmdir /S /Q META-INF
        popd
        "%JAR_EXE%" cf "%DIST_DIR%\sparkta-export.jar" -C "!EXP_OUT!" .
        copy /Y "%DIST_DIR%\sparkta-export.jar" "%SCRIPT_DIR%..\dist\sparkta-export.jar" >nul
        copy /Y "%DIST_DIR%\sparkta-export.jar" "C:\ado\personal\sparkta-export.jar" >nul
        echo  [OK]  sparkta-export.jar built and installed ^(saveas PNG/PDF now pure Java^).
    )
) else (
    echo  [INFO] Java export libraries not in lib\ -- run fetch_js_libs.bat to enable the fast saveas path.
)
echo.

REM --- Step 6: Copy ado files to dist -----------------------------------------
REM v3.6.0-t2f: sparkta ships one main ado plus the sparkta_*.ado components -- copy them all
copy /Y "%SCRIPT_DIR%..\ado\sparkta*.ado"       "%DIST_DIR%\"                   >nul
copy /Y "%SCRIPT_DIR%..\ado\sparkta.sthlp"      "%DIST_DIR%\sparkta.sthlp"      >nul

REM --- v3.6.0-s8b: refresh the RELEASE jar so verify_before_zip check 16 passes
REM     and the GitHub/SSC dist\ folder always carries the jar built from this source.
if not exist "%SCRIPT_DIR%..\dist" mkdir "%SCRIPT_DIR%..\dist"
copy /Y "%DIST_DIR%\sparkta.jar" "%SCRIPT_DIR%..\dist\sparkta.jar" >nul
echo  [OK]  Release jar refreshed: ..\dist\sparkta.jar

REM --- v3.6.0-t2j fix8o: ALSO refresh the ado\ jar from the SAME freshly built jar.
REM     The ado\sparkta.jar is the copy sparkta.pkg ships to end users; previously the
REM     build wrote dist\ (above) but not ado\, so the two drifted apart whenever ado\
REM     was refreshed out-of-band -- verify_before_zip check 16 then failed on
REM     "dist and ado jars differ". Copying the one built jar to BOTH keeps them
REM     byte-identical by construction.
copy /Y "%DIST_DIR%\sparkta.jar" "%SCRIPT_DIR%..\ado\sparkta.jar" >nul
echo  [OK]  Shipped jar refreshed: ..\ado\sparkta.jar (byte-identical to dist\)

echo  [OK]  Copied sparkta.ado, sparkta_*.ado components and sparkta.sthlp to dist\
echo.

REM --- Step 7: Install into Stata personal ado directory ----------------------
echo  Installing into Stata personal ado directory...

REM Install to C:\ado\personal (traditional location)
set ADO_DIR1=C:\ado\personal
if not exist "%ADO_DIR1%" mkdir "%ADO_DIR1%"
copy /Y "%DIST_DIR%\sparkta.jar"    "%ADO_DIR1%\sparkta.jar"    >nul
copy /Y "%DIST_DIR%\sparkta*.ado"       "%ADO_DIR1%\"                   >nul
copy /Y "%DIST_DIR%\sparkta_check.ado"  "%ADO_DIR1%\sparkta_check.ado"  >nul
copy /Y "%DIST_DIR%\sparkta.sthlp"      "%ADO_DIR1%\sparkta.sthlp"      >nul
echo  [OK]  Installed to: %ADO_DIR1%

REM Also install to user-profile ado\personal (Stata's default on many Windows installs)
set ADO_DIR2=%USERPROFILE%\ado\personal
if not exist "%ADO_DIR2%" mkdir "%ADO_DIR2%"
copy /Y "%DIST_DIR%\sparkta.jar"    "%ADO_DIR2%\sparkta.jar"    >nul
copy /Y "%DIST_DIR%\sparkta*.ado"       "%ADO_DIR2%\"                   >nul
copy /Y "%DIST_DIR%\sparkta_check.ado"  "%ADO_DIR2%\sparkta_check.ado"  >nul
copy /Y "%DIST_DIR%\sparkta.sthlp"      "%ADO_DIR2%\sparkta.sthlp"      >nul
echo  [OK]  Installed to: %ADO_DIR2%

set ADO_DIR=%ADO_DIR1%
echo.
echo  =========================================
echo   Build complete! Run this in Stata:
echo.
echo     sysuse auto, clear
echo     sparkta price mpg, type(bar)
echo  =========================================
echo.

pause
endlocal
