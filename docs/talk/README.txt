sparkta_talk -- Stata Conference 2026 deck for sparkta v3.6.0
Version 1.0 | 2026-09-30 | ASCII only

PRESENT
  Double-click index.html (Edge or Chrome). No internet needed.
  Right / Space / PageDown = next, Left / PageUp = back, Home / End
  F = full screen, N = speaker notes + timer (starts on first N), O = slide overview
  Tiles on the wall slides open the live page; Esc closes it.
  After you click INSIDE a chart, the chart owns the keyboard: click the slide
  background (outside the chart) once, then the arrow keys work again.

BACKUP
  sparkta_talk_backup.pdf = every slide with PNG stills instead of live charts.
  index.html?static=1 shows the same thing in the browser.

PUBLISH (optional, for the QR code on the last slide)
  Copy this whole folder to docs/talk/ in the sparkta_stata repo, commit and push
  in GitHub Desktop. It then opens at https://fahad-mirza.github.io/sparkta_stata/talk/

REFRESH CHARTS after re-running a demo do-file
  python make_assets.py <path to talk_out>   (needs Pillow: pip install pillow)

CONTENTS
  index.html   the deck (slide text, speaker notes and timings are in the SLIDES list)
  charts/      live sparkta pages (self-contained html)
  img/         thumb/ and still/ (from the saveas PNGs), stata/ (graph export), qr.svg
  files/       E01 export results: PNG, PDF, SVG, table PDF
