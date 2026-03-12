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

DEST="src/main/resources/com/dashboard/js"
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

echo ""
echo "Done. 6 libraries downloaded. Now recompile the jar with: ./build.sh"
