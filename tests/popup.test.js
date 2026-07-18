"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const popupHtml = fs.readFileSync(
  path.resolve(__dirname, "../src/popup/popup.html"),
  "utf8"
);
const popupCss = fs.readFileSync(
  path.resolve(__dirname, "../src/popup/popup.css"),
  "utf8"
);

test("les trois interrupteurs visuels sont des labels cliquables", () => {
  assert.equal((popupHtml.match(/<label class="switch/g) || []).length, 3);
  assert.equal(popupHtml.includes('<span class="switch">'), false);
});

test("le champ natif couvre toute la surface de chaque interrupteur", () => {
  const inputRule = popupCss.match(/\.switch input\s*\{([^}]+)\}/);
  assert.ok(inputRule);
  assert.match(inputRule[1], /inset:\s*0/);
  assert.match(inputRule[1], /height:\s*100%/);
  assert.match(inputRule[1], /width:\s*100%/);
  assert.match(inputRule[1], /z-index:\s*2/);
});

test("un bouton de déblocage existe pour chaque plateforme", () => {
  assert.match(popupHtml, /data-unlock="youtube"/);
  assert.match(popupHtml, /data-unlock="instagram"/);
});

test("la popup charge les traductions avant son contrôleur", () => {
  const i18nPosition = popupHtml.indexOf("../core/i18n.js");
  const popupPosition = popupHtml.indexOf('src="popup.js"');

  assert.ok(i18nPosition > -1);
  assert.ok(i18nPosition < popupPosition);
  assert.match(popupHtml, /data-i18n="punitiveMode"/);
  assert.match(popupHtml, /data-i18n="unlock"/);
});
