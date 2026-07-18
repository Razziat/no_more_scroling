"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const {
  createI18n,
  getBrowserLanguage,
  resolveLocale
} = require("../src/core/i18n.js");

test("sélectionne le français pour les variantes régionales françaises", () => {
  assert.equal(resolveLocale("fr"), "fr");
  assert.equal(resolveLocale("fr-FR"), "fr");
  assert.equal(resolveLocale("fr_CA"), "fr");
});

test("utilise l’anglais pour l’anglais et les langues non traduites", () => {
  assert.equal(resolveLocale("en-US"), "en");
  assert.equal(resolveLocale("de-DE"), "en");
  assert.equal(resolveLocale(""), "en");
});

test("traduit et interpole les textes de l’écran punitif", () => {
  const french = createI18n("fr-FR");
  const english = createI18n("en-GB");

  assert.equal(french.t("stayFocused"), "Reste concentré !");
  assert.equal(
    french.t("siteIsBlocked", { site: "YouTube" }),
    "YouTube est bloqué"
  );
  assert.equal(french.t("forThirtyMinutes"), "pendant 30 minutes");
  assert.equal(english.t("stayFocused"), "Stay focused!");
  assert.equal(
    english.t("siteIsBlocked", { site: "YouTube" }),
    "YouTube is blocked"
  );
  assert.equal(english.t("forThirtyMinutes"), "for 30 minutes");
});

test("traduit la nouvelle hiérarchie du mode non punitif", () => {
  const french = createI18n("fr-FR");
  const english = createI18n("en-US");

  assert.equal(
    french.t("productIsBlocked", { product: "Shorts" }),
    "Les Shorts sont bloqués"
  );
  assert.equal(
    french.t("siteRemainsAccessible", { site: "YouTube" }),
    "YouTube reste accessible"
  );
  assert.equal(
    english.t("productIsBlocked", { product: "Shorts" }),
    "Shorts are blocked"
  );
  assert.equal(
    english.t("siteRemainsAccessible", { site: "YouTube" }),
    "YouTube remains available"
  );
});

test("lit en priorité la langue de l’interface du navigateur", () => {
  const previousChrome = globalThis.chrome;
  globalThis.chrome = { i18n: { getUILanguage: () => "fr-FR" } };

  try {
    assert.equal(getBrowserLanguage(), "fr-FR");
  } finally {
    globalThis.chrome = previousChrome;
  }
});
