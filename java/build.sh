#!/usr/bin/env bash
# ============================================================
#  build.sh - Stata Dashboard Plugin build script (macOS/Linux)
#  Uses Stata's bundled Java - no Maven or external tools needed
# ============================================================

set -e  # Exit immediately on any error

BOLD="\033[1m"
GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[0;33m"
RESET="\033[0m"

ok()    { echo -e " ${GREEN}[OK]${RESET}  $1"; }
err()   { echo -e " ${RED}[ERROR]${RESET} $1"; exit 1; }
info()  { echo -e " ${YELLOW}[INFO]${RESET} $1"; }
warn()  { echo -e " ${YELLOW}[WARN]${RESET} $1"; }

echo ""
echo -e " ${YELLOW}IMPORTANT:${RESET} Run fetch_js_libs.sh BEFORE this script if you have not"
echo    "  already done so. The JS libraries must be present in"
echo    "  src/main/resources/com/dashboard_test/js/ before the jar is built."
echo    "  Without them the offline option will fail at runtime."
echo ""

echo ""
echo -e " ${BOLD}=========================================${RESET}"
echo -e " ${BOLD} Stata Dashboard Plugin - Build Script  ${RESET}"
echo -e " ${BOLD}=========================================${RESET}"
echo ""

# --- Step 1: Locate Stata's Java --------------------------------------------
JAVAC=""
SFI_JAR=""

# Common Stata installation paths
STATA_PATHS=(
    "/Applications/Stata/Contents/MacOS/utilities/java"       # macOS (any version)
    "/Applications/Stata19/Contents/MacOS/utilities/java"
    "/Applications/Stata18/Contents/MacOS/utilities/java"
    "/Applications/Stata17/Contents/MacOS/utilities/java"
    "/Applications/Stata16/Contents/MacOS/utilities/java"
    "/usr/local/stata18/utilities/java"                        # Linux Stata 18
    "/usr/local/stata17/utilities/java"
    "/usr/local/stata16/utilities/java"
    "/opt/stata18/utilities/java"
    "/opt/stata17/utilities/java"
)

for JAVA_PATH in "${STATA_PATHS[@]}"; do
    FOUND=$(find "$JAVA_PATH" -name "javac" 2>/dev/null | head -1)
    if [ -n "$FOUND" ]; then
        JAVAC="$FOUND"
        JAVA_BIN=$(dirname "$JAVAC")
        break
    fi
done

# If not found, ask the user
if [ -z "$JAVAC" ]; then
    info "Could not locate Stata automatically."
    read -rp "  Enter path to your Stata utilities/java directory: " CUSTOM_PATH
    FOUND=$(find "$CUSTOM_PATH" -name "javac" 2>/dev/null | head -1)
    if [ -z "$FOUND" ]; then
        err "javac not found in $CUSTOM_PATH"
    fi
    JAVAC="$FOUND"
    JAVA_BIN=$(dirname "$JAVAC")
fi

JAR_EXE="$JAVA_BIN/jar"

ok "javac: $JAVAC"
ok "jar:   $JAR_EXE"
echo ""

# --- Step 2: Locate the Stata SFI jar ---------------------------------------
STATA_JAVA_DIR=$(dirname "$JAVA_BIN")  # Go up from bin/ to java/

SFI_JAR=$(find "$(dirname "$STATA_JAVA_DIR")" -name "stata-plugin-interface*.jar" 2>/dev/null | head -1)

if [ -z "$SFI_JAR" ]; then
    # Try broader search from common Stata roots
    for STATA_ROOT in /Applications/Stata* /usr/local/stata* /opt/stata*; do
        SFI_JAR=$(find "$STATA_ROOT" -name "stata-plugin-interface*.jar" 2>/dev/null | head -1)
        [ -n "$SFI_JAR" ] && break
    done
fi

if [ -z "$SFI_JAR" ]; then
    err "stata-plugin-interface jar not found. Check your Stata installation."
fi

ok "SFI jar: $SFI_JAR"
echo ""

# --- Step 3: Set up directories ---------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src/main/java"
OUT_DIR="$SCRIPT_DIR/out"
DIST_DIR="$SCRIPT_DIR/../dist_test"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
mkdir -p "$DIST_DIR"

# --- Step 4: Compile --------------------------------------------------------
# v3.6.0-s8b: Python pre-build check is optional (verify/verify_all.py covers it)
if command -v python3 >/dev/null 2>&1; then
    python3 check_build.py || { echo "  [ERROR] Pre-build check failed. Fix errors above."; exit 1; }
else
    echo "  [SKIP] python3 not available -- pre-build check skipped."
fi
info "Compiling Java sources..."

# Collect all .java files
SOURCES=$(find "$SRC_DIR" -name "*.java")

# --release 11 targets class file version 55, compatible with Stata 17+.
# Stata 17 bundles JDK 11 (max class file 55).
# Stata 18/19 bundle JDK 17/21 and run class file 55 without issue.
# --release is the modern replacement for -source/-target (deprecated).
# Without --release, JDK 21 javac produces class file 65 which Stata 18
# users on Stata 17-18 cannot load (UnsupportedClassVersionError).
# shellcheck disable=SC2086
"$JAVAC" \
    --release 11 \
    -cp "$SFI_JAR" \
    -d "$OUT_DIR" \
    $SOURCES

ok "Compilation successful."
echo ""

# --- Step 4b: copy JS/offline resources into OUT_DIR so they are packed into the
#     jar (t2i hardening: build.sh previously packed classes only via `-C OUT .`
#     -> the bundled offline libraries were missing and offline mode silently
#     could not work).
RES_DIR="$SCRIPT_DIR/src/main/resources"
if [ -d "$RES_DIR" ]; then
    cp -r "$RES_DIR"/. "$OUT_DIR/"
fi
# Fail closed if a mandatory offline JS library is absent (Windows build only warned).
JS_DIR="$OUT_DIR/com/dashboard_test/js"
for _lib in chartjs-4.4.0.min.js sparkta_engine.js chartjs-boxplot-4.4.5.min.js chartjs-errorbars-4.4.0.min.js chartjs-annotation-3.0.1.min.js; do
    if [ ! -f "$JS_DIR/$_lib" ]; then
        echo "  [ERROR] required resource missing from jar: com/dashboard_test/js/$_lib"; exit 1;
    fi
done

# --- Step 5: Package into jar -----------------------------------------------
info "Packaging sparkta.jar..."

# Pack ONLY com/dashboard_test (classes + js resources); never any com/stata SFI
# classes (Stata provides its own SFI at runtime -- shipping them would shadow it).
"$JAR_EXE" cf "$DIST_DIR/sparkta.jar" -C "$OUT_DIR" com/dashboard_test

ok "sparkta.jar created at: $DIST_DIR/sparkta.jar"
echo ""

# --- Step 5b (v3.6.0-t2j fix9d, parity with build.bat s9y): fast export path -- OPTIONAL --
# If fetch_js_libs.sh fetched the five Java libraries into lib/, compile
# src/export/.../SvgConvert.java against them and package a second jar,
# sparkta-export.jar, that bundles those libraries (one classpath entry for the ado).
# Without the libraries this step is skipped and saveas() uses the browser for every
# format (full-page PNG since fix9d).
LIB_DIR="$SCRIPT_DIR/lib"
EXPORT_LIBS="jsvg-2.1.0.jar graphics2d-3.0.5.jar pdfbox-3.0.5.jar fontbox-3.0.5.jar pdfbox-io-3.0.5.jar"
EXPORT_READY=1
EXPORT_CP=""
for J in $EXPORT_LIBS; do
    if [ ! -f "$LIB_DIR/$J" ]; then EXPORT_READY=0; fi
    EXPORT_CP="$EXPORT_CP${EXPORT_CP:+:}$LIB_DIR/$J"
done
if [ "$EXPORT_READY" = "1" ]; then
    info "Building sparkta-export.jar (fast PNG/PDF path)..."
    EXP_OUT="$SCRIPT_DIR/build_export"
    rm -rf "$EXP_OUT"; mkdir -p "$EXP_OUT"
    if "$JAVAC" -cp "$EXPORT_CP" --release 11 -d "$EXP_OUT" "$SCRIPT_DIR/src/export/com/dashboard_test/export/SvgConvert.java"; then
        ( cd "$EXP_OUT" && for J in $EXPORT_LIBS; do "$JAR_EXE" xf "$LIB_DIR/$J"; done && rm -rf META-INF )
        "$JAR_EXE" cf "$DIST_DIR/sparkta-export.jar" -C "$EXP_OUT" .
        cp "$DIST_DIR/sparkta-export.jar" "$SCRIPT_DIR/../dist/sparkta-export.jar"
        cp "$DIST_DIR/sparkta-export.jar" "$HOME/ado/personal/sparkta-export.jar" 2>/dev/null || true
        ok "sparkta-export.jar built and installed (saveas PNG/PDF now pure Java)"
    else
        echo "  [WARN] SvgConvert did not compile -- fast export path disabled (browser fallback still works)."
    fi
else
    info "Java export libraries not in lib/ -- run ./fetch_js_libs.sh to enable the fast saveas path."
fi
echo ""

# --- Step 6: Copy ado files to dist -----------------------------------------
# t2f: main + sparkta_*.ado components (the glob covers every component). There is
# no separate sparkta_check.ado -- the pre-build check is java/check_build.py.
cp "$SCRIPT_DIR"/../ado/sparkta*.ado        "$DIST_DIR/"
cp "$SCRIPT_DIR/../ado/sparkta.sthlp"       "$DIST_DIR/sparkta.sthlp"

ok "Copied sparkta.ado and sparkta.sthlp to dist_test/"
echo ""

# --- Step 6b (v3.6.0-t2j fix8o): refresh BOTH shipped jars from the SAME freshly
#     built jar so they are byte-identical by construction. The repo ships two
#     copies -- dist/sparkta.jar (GitHub/SSC release folder) and ado/sparkta.jar
#     (what sparkta.pkg installs to end users). Previously this script wrote
#     neither (only dist_test/ and ~/ado/personal), so both repo copies were
#     refreshed by hand and drifted -- verify_before_zip check 16 then failed on
#     "dist and ado jars differ". Writing the one built jar to both fixes that.
mkdir -p "$SCRIPT_DIR/../dist"
cp "$DIST_DIR/sparkta.jar" "$SCRIPT_DIR/../dist/sparkta.jar"
cp "$DIST_DIR/sparkta.jar" "$SCRIPT_DIR/../ado/sparkta.jar"
ok "Refreshed dist/sparkta.jar and ado/sparkta.jar (byte-identical to each other)"
echo ""

# --- Step 7: Install into Stata personal ado directory ----------------------
info "Installing into Stata personal ado directory..."

ADO_DIR="$HOME/ado/personal"

if [ ! -d "$ADO_DIR" ]; then
    info "Creating $ADO_DIR..."
    mkdir -p "$ADO_DIR"
fi

cp "$DIST_DIR/sparkta.jar"    "$ADO_DIR/sparkta.jar"
cp "$DIST_DIR"/sparkta*.ado   "$ADO_DIR/"
cp "$DIST_DIR/sparkta.sthlp"  "$ADO_DIR/sparkta.sthlp"

ok "Installed to: $ADO_DIR"
echo ""
echo -e " ${BOLD}=========================================${RESET}"
echo -e " ${BOLD} Build complete! Run this in Stata:    ${RESET}"
echo ""
echo -e "   sysuse auto, clear"
echo -e "   sparkta price mpg, type(bar)"
echo -e " ${BOLD}=========================================${RESET}"
echo ""
