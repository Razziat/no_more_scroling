"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { createExtension } = require("../test-support/extension-harness.js");
const { LOCKS_KEY, PUNITIVE_DURATION_MS } = require("../src/core/locks.js");
const { SETTINGS_KEY } = require("../src/core/settings.js");

const shorts = "https://www.youtube.com/shorts/abc";
const reels = "https://www.instagram.com/reel/def";

test("deux onglets déclenchent leurs sanctions sans écraser l'autre plateforme", async () => {
  const extension = createExtension();
  const youtube = await extension.createTab("https://www.youtube.com/");
  const instagram = await extension.createTab("https://www.instagram.com/");
  youtube.click(shorts);
  instagram.click(reels);
  await extension.settle();
  const locks = extension.stored[LOCKS_KEY].sites;
  assert.ok(locks.youtube.expiresAt > 0);
  assert.ok(locks.instagram.expiresAt > 0);
  assert.ok(youtube.blocker);
  assert.ok(instagram.blocker);
});

test("le déblocage survit aux changements d'un autre onglet après cinq secondes", async () => {
  const extension = createExtension();
  const tab = await extension.createTab(shorts);
  assert.ok(extension.stored[LOCKS_KEY].sites.youtube.expiresAt > 0);
  await extension.unlock("youtube");
  extension.advance(6_000);
  await extension.lock("instagram", reels);
  await extension.settle();
  assert.equal(extension.stored[LOCKS_KEY].sites.youtube.expiresAt, 0);
  assert.equal(tab.sent.length, 1, "aucune nouvelle demande sans navigation");
  assert.ok(tab.blocker, "la route reste bloquée sans sanction punitive");

  tab.navigate("https://www.youtube.com/");
  tab.navigate(shorts);
  await extension.settle();
  assert.ok(extension.stored[LOCKS_KEY].sites.youtube.expiresAt > 0,
    "une véritable nouvelle navigation est sanctionnée");
});

test("un déblocage concurrent conserve la sanction de l'autre plateforme", async () => {
  const extension = createExtension();
  await extension.lock("youtube", shorts);
  await Promise.all([extension.unlock("youtube"), extension.lock("instagram", reels)]);
  assert.equal(extension.stored[LOCKS_KEY].sites.youtube.expiresAt, 0);
  assert.ok(extension.stored[LOCKS_KEY].sites.instagram.expiresAt > 0);
});

test("un ancien déblocage ne protège pas une nouvelle page après expiration de la grâce", async () => {
  const extension = createExtension();
  await extension.lock("youtube", shorts);
  await extension.unlock("youtube");
  extension.advance(6_000);
  const newTab = await extension.createTab(shorts);
  assert.equal(newTab.sent.length, 1);
  assert.ok(extension.stored[LOCKS_KEY].sites.youtube.expiresAt > 0);
});

test("les demandes répétées et le démarrage du worker ne prolongent pas une sanction", async () => {
  const extension = createExtension();
  await extension.lock("youtube", shorts);
  const expiresAt = extension.stored[LOCKS_KEY].sites.youtube.expiresAt;
  extension.advance(10_000);
  extension.startup();
  await Promise.all([extension.lock("youtube", shorts), extension.lock("instagram", reels)]);
  await extension.settle();
  assert.equal(extension.stored[LOCKS_KEY].sites.youtube.expiresAt, expiresAt);
  assert.ok(extension.stored[LOCKS_KEY].sites.instagram.expiresAt > 0);
});

test("un échec d'écriture ne bloque pas les mutations suivantes", async () => {
  const extension = createExtension();
  extension.failNextWrite();
  assert.equal((await extension.lock("youtube", shorts)).ok, false);
  assert.equal((await extension.lock("instagram", reels)).ok, true);
  assert.ok(extension.stored[LOCKS_KEY].sites.instagram.expiresAt > 0);
  assert.equal(extension.warnings.length, 1);
});

test("un échec de sanction conserve le blocage normal et les médias en pause", async () => {
  const extension = createExtension();
  extension.failNextWrite();
  const tab = await extension.createTab(shorts);
  assert.equal(extension.stored[LOCKS_KEY].sites.youtube.expiresAt, 0);
  assert.ok(tab.blocker);
  assert.equal(tab.media.paused, true);
  tab.play();
  assert.equal(tab.media.paused, true);
});

test("une notification de route tardive ne bloque pas la page désormais autorisée", async () => {
  const extension = createExtension();
  const tab = await extension.createTab("https://www.youtube.com/");
  tab.delayedRouteMessage(shorts);
  await extension.settle();
  assert.equal(tab.blocker, undefined);
  assert.equal(tab.sent.length, 0);
  tab.play();
  assert.equal(tab.media.paused, false);
});

test("l'expiration renvoie vers l'accueil sans recréer une sanction", async () => {
  const extension = createExtension();
  const tab = await extension.createTab(shorts);
  extension.advance(PUNITIVE_DURATION_MS + 1);
  tab.tick();
  tab.tick();
  await extension.settle();
  assert.equal(tab.href, "https://www.youtube.com/");
  assert.equal(tab.blocker, undefined);
  assert.equal(tab.sent.length, 1);
});

test("le worker refuse les déblocages provenant des pages et les routes autorisées", async () => {
  const extension = createExtension();
  await extension.lock("youtube", shorts);
  const reply = await extension.request({ type: "ANTI_SCROLL_UNLOCK", siteId: "youtube" }, {
    id: "anti-scroll-test", url: shorts, frameId: 0
  });
  assert.equal(reply.ok, false);
  assert.ok(extension.stored[LOCKS_KEY].sites.youtube.expiresAt > 0);
  await extension.lock("instagram", "https://www.instagram.com/");
  assert.equal(extension.stored[LOCKS_KEY].sites.instagram.expiresAt, 0);
});

test("les réglages désactivés sont vérifiés par le worker avant une sanction", async () => {
  const extension = createExtension({ punitiveMode: false });
  await extension.lock("youtube", shorts);
  assert.equal(extension.stored[LOCKS_KEY].sites.youtube.expiresAt, 0);
  await extension.storage.local.set({ [SETTINGS_KEY]: {
    punitiveMode: true, sites: { youtube: false, instagram: true }
  } });
  await extension.lock("youtube", shorts);
  assert.equal(extension.stored[LOCKS_KEY].sites.youtube.expiresAt, 0);
});

test("une lecture initiale tardive ne restaure pas une sanction déjà débloquée", async () => {
  const extension = createExtension();
  await extension.lock("youtube", shorts);
  const releaseRead = extension.delayNextRead();
  const tab = await extension.createTab(shorts);
  await extension.unlock("youtube");
  extension.advance(6_000);
  releaseRead();
  await extension.settle();
  assert.equal(extension.stored[LOCKS_KEY].sites.youtube.expiresAt, 0);
  assert.equal(tab.sent.length, 0);
  // A punitive blocker starts an animation; a normal route blocker has an
  // ordinary card and no canvas. Inspect the real constructed DOM structure.
  const tags = element => [element.tagName, ...element.children.flatMap(tags)];
  assert.equal(tags(tab.blocker.shadow).includes("canvas"), false);
});
