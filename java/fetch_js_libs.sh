#!/bin/bash
# fetch_js_libs.sh -- Step 1 of the Sparkta build process (macOS/Linux)
#
# PURPOSE: Downloads the five pinned JavaScript libraries that Sparkta
# bundles into the jar for offline chart viewing. These files must be
# present before you run build.sh (Step 2).
#
# ORDER:  1. ./fetch_js_libs.sh   <- YOU ARE HERE
#         2. ./build.sh
#
# Run once. Re-run only if you need to refresh the libraries or if a
# new library has been added to the package.
# v2.7.0

DEST="src/main/resources/com/dashboard_test/js"
mkdir -p "$DEST"

echo "Fetching chart.js@4.4.0..."
curl -L "https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js" \
     -o "$DEST/chartjs-4.4.0.min.js" --fail --silent --show-error
echo "  -> $(wc -c < $DEST/chartjs-4.4.0.min.js) bytes"

echo "Fetching chartjs-plugin-datalabels@2.2.0..."
curl -L "https://cdn.jsdelivr.net/npm/chartjs-plugin-datalabels@2.2.0/dist/chartjs-plugin-datalabels.min.js" \
     -o "$DEST/chartjs-datalabels-2.2.0.min.js" --fail --silent --show-error
echo "  -> $(wc -c < $DEST/chartjs-datalabels-2.2.0.min.js) bytes"

echo "Fetching chartjs-chart-error-bars@4.4.0..."
curl -L "https://cdn.jsdelivr.net/npm/chartjs-chart-error-bars@4.4.0/build/index.umd.min.js" \
     -o "$DEST/chartjs-errorbars-4.4.0.min.js" --fail --silent --show-error
echo "  -> $(wc -c < $DEST/chartjs-errorbars-4.4.0.min.js) bytes"

echo "Fetching @sgratzl/chartjs-chart-boxplot@4.4.5..."
curl -L "https://cdn.jsdelivr.net/npm/@sgratzl/chartjs-chart-boxplot@4.4.5/build/index.umd.min.js" \
     -o "$DEST/chartjs-boxplot-4.4.5.min.js" --fail --silent --show-error
echo "  -> $(wc -c < $DEST/chartjs-boxplot-4.4.5.min.js) bytes"

echo "Fetching canvas2svg@1.0.19 (from GitHub -- npm only has 1.0.16)..."
curl -L "https://raw.githubusercontent.com/gliffy/canvas2svg/master/canvas2svg.js" \
     -o "$DEST/canvas2svg-1.0.19.js" --fail --silent --show-error
echo "  -> $(wc -c < $DEST/canvas2svg-1.0.19.js) bytes"

echo "Fetching chartjs-plugin-annotation@3.0.1..."
curl -L "https://cdn.jsdelivr.net/npm/chartjs-plugin-annotation@3.0.1/dist/chartjs-plugin-annotation.min.js" \
     -o "$DEST/chartjs-annotation-3.0.1.min.js" --fail --silent --show-error
echo "  -> $(wc -c < $DEST/chartjs-annotation-3.0.1.min.js) bytes"

# --- v3.6.0-t2j fix9d: the five Java libraries for the OPTIONAL fast saveas() path -------
# (jsvg + pdfbox: SVG -> PNG/PDF in pure Java, no browser round-trip per format). build.sh
# Step 5b packs them into sparkta-export.jar when all five are present; without them the
# browser route is used for every format (fix9d made that route full-page too). Same pins
# and URLs as fetch_js_libs.bat.
JLIB="$(dirname "$0")/lib"
mkdir -p "$JLIB"
echo ""
echo "Fetching the Java export libraries into $JLIB (optional fast saveas path)..."
for JURL in \
  "https://repo1.maven.org/maven2/com/github/weisj/jsvg/2.1.0/jsvg-2.1.0.jar" \
  "https://repo1.maven.org/maven2/de/rototor/pdfbox/graphics2d/3.0.5/graphics2d-3.0.5.jar" \
  "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/3.0.5/pdfbox-3.0.5.jar" \
  "https://repo1.maven.org/maven2/org/apache/pdfbox/fontbox/3.0.5/fontbox-3.0.5.jar" \
  "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-io/3.0.5/pdfbox-io-3.0.5.jar"; do
    JNAME="$(basename "$JURL")"
    echo "Fetching $JNAME..."
    if curl -L "$JURL" -o "$JLIB/$JNAME" --fail --silent --show-error --retry 2; then
        echo "  -> $(wc -c < "$JLIB/$JNAME") bytes"
    else
        echo "  [WARN] $JNAME not fetched -- the fast saveas path stays off (browser route still works)"
    fi
done

echo ""
echo "Done. 6 JavaScript libraries (+ 5 optional Java libraries) downloaded. Now recompile the jar with: ./build.sh"
