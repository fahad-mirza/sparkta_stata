@echo off
REM ============================================================
REM  build.bat - Sparkta build script (Windows)
REM  Configured for Stata 19 Now (StataNow19) with Zulu JDK 21
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
set JAVA_BIN=%STATA_DIR%\utilities\java\windows-x64\zulu-jdk21.0.10\bin
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
echo  Running pre-build checks...
python check_build.py
if errorlevel 1 (
    echo  [ERROR] Pre-build check failed. Fix errors above before compiling.
    pause
    exit /b 1
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
    echo  [WARN] One or more offline JS libraries not found.
    echo         The offline option will fail at runtime.
    echo         To fix: run fetch_js_libs.bat then rebuild.
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

REM --- Step 6: Copy ado files to dist -----------------------------------------
copy /Y "%SCRIPT_DIR%..\ado\sparkta.ado"        "%DIST_DIR%\sparkta.ado"        >nul
copy /Y "%SCRIPT_DIR%..\ado\sparkta_check.ado"  "%DIST_DIR%\sparkta_check.ado"  >nul
copy /Y "%SCRIPT_DIR%..\ado\sparkta.sthlp"      "%DIST_DIR%\sparkta.sthlp"      >nul

echo  [OK]  Copied sparkta.ado and sparkta.sthlp to dist\
echo.

REM --- Step 7: Install into Stata personal ado directory ----------------------
echo  Installing into Stata personal ado directory...

REM Install to C:\ado\personal (traditional location)
set ADO_DIR1=C:\ado\personal
if not exist "%ADO_DIR1%" mkdir "%ADO_DIR1%"
copy /Y "%DIST_DIR%\sparkta.jar"    "%ADO_DIR1%\sparkta.jar"    >nul
copy /Y "%DIST_DIR%\sparkta.ado"        "%ADO_DIR1%\sparkta.ado"        >nul
copy /Y "%DIST_DIR%\sparkta_check.ado"  "%ADO_DIR1%\sparkta_check.ado"  >nul
copy /Y "%DIST_DIR%\sparkta.sthlp"      "%ADO_DIR1%\sparkta.sthlp"      >nul
echo  [OK]  Installed to: %ADO_DIR1%

REM Also install to user-profile ado\personal (Stata's default on many Windows installs)
set ADO_DIR2=%USERPROFILE%\ado\personal
if not exist "%ADO_DIR2%" mkdir "%ADO_DIR2%"
copy /Y "%DIST_DIR%\sparkta.jar"    "%ADO_DIR2%\sparkta.jar"    >nul
copy /Y "%DIST_DIR%\sparkta.ado"        "%ADO_DIR2%\sparkta.ado"        >nul
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
