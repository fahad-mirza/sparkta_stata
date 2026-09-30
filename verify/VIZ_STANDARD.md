# Sparkta visualization standard (VIZ_STANDARD, v1 -- 2026-09-05)

Every chart sparkta emits is audited against this rubric by verify/viz_audit.js.
Each rule has an id, a severity, and a source. Severity: ERROR = misleading or
broken; WARN = measurably worse than the standard; INFO = advisory.

Score: 100 - 25*ERROR - 8*WARN - 2*INFO (floor 0).  A>=90  B>=75  C>=60  D<60.

Sources
  [CM84]  Cleveland & McGill (1984) Graphical Perception, JASA 79:531-554.
          Accuracy of visual judgement: position on common scale > length >
          angle/area > colour saturation. => prefer bars/dots to pies; align
          series on a common axis.
  [CLV93] Cleveland (1993) Visualizing Data. Banking to 45 degrees: choose the
          aspect ratio so the median absolute slope of line segments is ~45deg.
  [TUF01] Tufte (2001) The Visual Display of Quantitative Information.
          Data-ink ratio: erase non-data ink (heavy grids, redundant legends).
  [WIL19] Wilke (2019) Fundamentals of Data Visualization. ch.17 bars must start
          at zero; ch.6 sort nominal categories by value; ch.19 colour for
          <= ~8 categories; axes must be labelled with quantity and unit.
  [FEW12] Few (2012) Show Me the Numbers. Legend only when it disambiguates;
          <= 8 series per panel; direct labelling over legends where possible.
  [WCAG]  WCAG 2.1: 1.4.3 text contrast >= 4.5:1; 1.4.11 non-text (graphical
          objects) contrast >= 3:1; 1.4.1 do not rely on colour alone.
  [OKI08] Okabe & Ito (2008) colour-universal palette; Brettel/Vienot (1997/1999)
          deuteranopia simulation used to test distinguishability.
  [SCH21] Schwabish (2021) Better Data Visualizations. Declutter; annotate.
  [SJ]    Stata Journal / Stata Graphics Reference: label axes with variable
          labels; CI shown for estimates; reference line at null value.

Rules
  id        sev    rule                                                   source
  AX-ZERO   ERROR  bar/column value axis must include zero                [WIL19 17]
  AX-CLIP   ERROR  data (incl. CI) extends beyond an explicit axis range  [WIL19]
  AX-WASTE  WARN   data occupies < 50% of an explicit axis range          [TUF01]
  AX-TITLE  WARN   value axis of an estimate/CI chart has no title/unit   [WIL19,SJ]
  AX-LOGBAR INFO   log scale on a bar chart (lengths lose meaning)        [CM84]
  AX-ANCHOR WARN   post-est value axis must span the data: anchored ticks    [SJ]
                   (coefplot/eventstudy) OR data-hugging bounds with the
                   nice-step ticks inside them (marginsplot, Stata's look; fix8w)
  ASP-BANK  INFO   line chart far from 45-degree banking; suggest aspect  [CLV93]
  ASP-RANGE INFO   aspect ratio outside 1.2-2.2                           [CLV93]
  CAT-DENSE WARN   category labels likely to overlap (no rotation)        [SCH21]
  CAT-SORT  INFO   nominal bar categories not sorted by value             [WIL19 6]
  SER-MANY  WARN   > 8 data series on one panel                           [FEW12,WIL19 19]
  PIE-MANY  WARN   pie/donut with > 6 slices; prefer bars                 [CM84]
  PIE-SMALL INFO   pie slice < 3% is unreadable                           [CM84]
  LEG-MISS  WARN   > 1 series but legend hidden and no direct labels      [FEW12]
  LEG-REDUN INFO   1 series but legend shown (non-data ink)               [TUF01,FEW12]
  COL-DIST  WARN   two series colours within dE76 < 20 (not separable)    [WIL19 19]
  COL-CVD   WARN   series colours not separable under deuteranopia        [OKI08,WCAG 1.4.1]
  COL-BG    WARN   series colour vs background contrast < 3:1             [WCAG 1.4.11]
  TXT-CONTR WARN   axis/label text vs background contrast < 4.5:1         [WCAG 1.4.3]
  TXT-SMALL WARN   any font size < 11px                                   [WCAG,SCH21]
  GRID-HEAVY INFO  gridline alpha > 0.25 (non-data ink)                   [TUF01]
  CI-MISS   WARN   estimate chart (coefplot/marginsplot) shows no CI      [SJ]
  REF-MISS  INFO   effect chart without a reference line at the null      [SJ]
  NULL-TIP  INFO   null points but tooltip does not explain them          [SCH21]
  CLUTTER   INFO   data labels on every point with > 20 points            [TUF01,SCH21]
  SIZE-HTML INFO   HTML > 2 MB (stats panel dot clouds / embedded libs)   --

What the audit cannot see (browser needed): actual pixel overlap, legend box
collisions, text truncation, colour rendering. Treat CAT-DENSE and ASP-* as
estimates; confirm visually on the flagged files.
