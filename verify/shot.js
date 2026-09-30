#!/usr/bin/env node
// shot.js -- screenshot an offline HTML chart (no interaction). ASCII only.
'use strict';
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const file = process.argv[2];
  const out = process.argv[3] || file.replace(/\.html$/, '') + '.png';
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const page = await browser.newPage({ viewport: { width: 1000, height: 720 }, deviceScaleFactor: 2 });
  await page.goto('file://' + path.resolve(file), { waitUntil: 'networkidle' });
  await page.waitForTimeout(900);
  await page.screenshot({ path: out });
  console.log('SHOT ' + out);
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
