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
python3 check_build.py
if [ $? -ne 0 ]; then
    echo "  [ERROR] Pre-build check failed. Fix errors above."
    exit 1
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

# --- Step 5: Package into jar -----------------------------------------------
info "Packaging sparkta.jar..."

"$JAR_EXE" cf "$DIST_DIR/sparkta.jar" -C "$OUT_DIR" .

ok "sparkta.jar created at: $DIST_DIR/sparkta.jar"
echo ""

# --- Step 6: Copy ado files to dist -----------------------------------------
cp "$SCRIPT_DIR/../ado/sparkta.ado"         "$DIST_DIR/sparkta.ado"
cp "$SCRIPT_DIR/../ado/sparkta_check.ado"   "$DIST_DIR/sparkta_check.ado"
cp "$SCRIPT_DIR/../ado/sparkta.sthlp"       "$DIST_DIR/sparkta.sthlp"

ok "Copied sparkta.ado and sparkta.sthlp to dist_test/"
echo ""

# --- Step 7: Install into Stata personal ado directory ----------------------
info "Installing into Stata personal ado directory..."

ADO_DIR="$HOME/ado/personal"

if [ ! -d "$ADO_DIR" ]; then
    info "Creating $ADO_DIR..."
    mkdir -p "$ADO_DIR"
fi

cp "$DIST_DIR/sparkta.jar"    "$ADO_DIR/sparkta.jar"
cp "$DIST_DIR/sparkta.ado"    "$ADO_DIR/sparkta.ado"
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
