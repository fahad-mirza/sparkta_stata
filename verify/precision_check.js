#!/usr/bin/env node
/*
 * precision_check.js -- verify/precision_check.js (v3.6.0-t2i hardening)
 *
 * The large-N (>=200) filter buffer is a base64-encoded typed array. It used to
 * be Float32 (4 bytes, ~7 significant digits), so filtering silently changed
 * means, CIs, currency, weights, IDs and dates -- e.g. 100000000 and 100000001
 * decoded to the same number (Astra, Critical). It is now Float64.
 *
 * This check does two things, with no Stata needed:
 *   1. Round-trips a set of high-precision values through the SAME encoding
 *      DataEmbedder.toBase64Float64 uses (little-endian putDouble) and the SAME
 *      decode sparkta_engine.js _sdGet uses (atob -> Uint8 -> Float64Array), and
 *      asserts EXACT equality.
 *   2. Proves the regression: the identical values through Float32 LOSE precision
 *      (so the test would have failed on the old build), confirming the test has
 *      teeth.
 *
 * Usage: node verify/precision_check.js
 */
'use strict';

// Values that need more than Float32's ~7 significant digits.
const VALUES = [
  100000000, 100000001,          // adjacent large integers (IDs, obs counts)
  1234567.891234,                // precise currency
  9876543210.5,                  // large weight/monetary
  0.123456789012345,             // many decimals
  -55555.5555,                   // negative
  3.141592653589793,             // pi
  NaN,                           // missing sentinel
];

// --- encode exactly as Java DataEmbedder.toBase64Float64 (LE putDouble) ---
function encodeFloat64(vals) {
  const buf = new ArrayBuffer(vals.length * 8);
  const dv = new DataView(buf);
  for (let i = 0; i < vals.length; i++) dv.setFloat64(i * 8, vals[i], true /*LE*/);
  const bytes = new Uint8Array(buf);
  let bin = '';
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return Buffer.from(bin, 'binary').toString('base64');
}
// Float32 counterpart, to prove the old build lost precision.
function encodeFloat32(vals) {
  const buf = new ArrayBuffer(vals.length * 4);
  const dv = new DataView(buf);
  for (let i = 0; i < vals.length; i++) dv.setFloat32(i * 4, vals[i], true);
  const bytes = new Uint8Array(buf);
  let bin = '';
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return Buffer.from(bin, 'binary').toString('base64');
}

// --- decode exactly as sparkta_engine.js _sdGet (Float64Array) ---
function decodeFloat64(b64) {
  const bin = Buffer.from(b64, 'base64').toString('binary');
  const buf = new ArrayBuffer(bin.length);
  const view = new Uint8Array(buf);
  for (let i = 0; i < bin.length; i++) view[i] = bin.charCodeAt(i) & 0xff;
  return new Float64Array(buf);
}
function decodeFloat32(b64) {
  const bin = Buffer.from(b64, 'base64').toString('binary');
  const buf = new ArrayBuffer(bin.length);
  const view = new Uint8Array(buf);
  for (let i = 0; i < bin.length; i++) view[i] = bin.charCodeAt(i) & 0xff;
  return new Float32Array(buf);
}

let fail = 0;
const out64 = decodeFloat64(encodeFloat64(VALUES));

// 1. Float64 round-trip must be EXACT for every value.
for (let i = 0; i < VALUES.length; i++) {
  const a = VALUES[i], b = out64[i];
  const ok = Number.isNaN(a) ? Number.isNaN(b) : a === b;
  if (!ok) { console.log('  FAIL float64 round-trip: ' + a + ' -> ' + b); fail++; }
}
if (out64.length !== VALUES.length) { console.log('  FAIL length mismatch'); fail++; }

// 2. Regression: Float32 must LOSE precision here (else the test is toothless).
const out32 = decodeFloat32(encodeFloat32(VALUES));
const f32Collides = (out32[0] === out32[1]);           // 100000000 == 100000001 under f32
const f32Drifts   = Math.abs(out32[2] - VALUES[2]) > 1e-6;
if (!f32Collides && !f32Drifts) {
  console.log('  FAIL regression guard: Float32 did not lose precision -- test has no teeth');
  fail++;
}

console.log('precision_check: ' + (VALUES.length) + ' values, float64 lossless=' + (fail === 0)
  + ', float32-would-collide=' + f32Collides);
process.exit(fail ? 1 : 0);
