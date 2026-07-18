"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const {
  cloneDefaultSettings,
  normalizeSettings
} = require("../src/core/settings.js");

test("active les deux plateformes par défaut", () => {
  assert.deepEqual(cloneDefaultSettings(), {
    punitiveMode: false,
    sites: { youtube: true, instagram: true }
  });
});

test("normalise les réglages incomplets sans écraser une valeur explicite", () => {
  assert.deepEqual(normalizeSettings({ sites: { youtube: false } }), {
    punitiveMode: false,
    sites: { youtube: false, instagram: true }
  });
});

test("ignore les types de réglages invalides", () => {
  assert.deepEqual(
    normalizeSettings({ sites: { youtube: "false", instagram: 0 } }),
    { punitiveMode: false, sites: { youtube: true, instagram: true } }
  );
});

test("conserve une activation explicite du mode punitif", () => {
  assert.deepEqual(normalizeSettings({ punitiveMode: true }), {
    punitiveMode: true,
    sites: { youtube: true, instagram: true }
  });
});
