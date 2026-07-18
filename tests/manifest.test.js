"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const projectRoot = path.resolve(__dirname, "..");
const manifest = JSON.parse(
  fs.readFileSync(path.join(projectRoot, "manifest.json"), "utf8")
);

test("le manifeste est en version 3 et ne demande que les permissions prévues", () => {
  assert.equal(manifest.manifest_version, 3);
  assert.deepEqual(manifest.permissions.sort(), ["storage", "webNavigation"]);
});

test("tous les fichiers déclarés dans le manifeste existent", () => {
  const declaredFiles = [
    manifest.background.service_worker,
    manifest.action.default_popup,
    ...manifest.content_scripts.flatMap((entry) => [
      ...(entry.js || []),
      ...(entry.css || [])
    ])
  ];

  for (const relativePath of declaredFiles) {
    assert.equal(
      fs.existsSync(path.join(projectRoot, relativePath)),
      true,
      `${relativePath} est introuvable`
    );
  }
});

test("les traductions sont chargées avant le script de contenu", () => {
  const scripts = manifest.content_scripts[0].js;

  assert.ok(scripts.indexOf("src/core/i18n.js") > -1);
  assert.ok(
    scripts.indexOf("src/core/i18n.js") <
      scripts.indexOf("src/content/content-script.js")
  );
});

test("les imports du service worker existent relativement à son fichier", () => {
  const workerPath = path.join(projectRoot, manifest.background.service_worker);
  const workerSource = fs.readFileSync(workerPath, "utf8");
  const importedPaths = Array.from(
    workerSource.matchAll(/importScripts\(([^)]+)\)/g),
    (match) =>
      Array.from(match[1].matchAll(/"([^"]+)"/g), (pathMatch) => pathMatch[1])
  ).flat();

  assert.ok(importedPaths.length > 0);
  for (const relativePath of importedPaths) {
    assert.equal(
      fs.existsSync(path.resolve(path.dirname(workerPath), relativePath)),
      true,
      `${relativePath} est introuvable depuis le service worker`
    );
  }
});
