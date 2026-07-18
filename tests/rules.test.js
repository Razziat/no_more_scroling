"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const {
  getBlockDecision,
  getSiteForUrl,
  isSupportedUrl
} = require("../src/core/rules.js");

test("bloque les routes Shorts de YouTube", () => {
  for (const url of [
    "https://www.youtube.com/shorts",
    "https://www.youtube.com/shorts/abc123",
    "https://m.youtube.com/shorts/abc123?feature=share"
  ]) {
    const decision = getBlockDecision(url);
    assert.equal(decision.blocked, true, url);
    assert.equal(decision.siteId, "youtube");
  }
});

test("laisse les vidéos YouTube classiques accessibles", () => {
  for (const url of [
    "https://www.youtube.com/",
    "https://www.youtube.com/watch?v=abc123",
    "https://www.youtube.com/results?search_query=music",
    "https://www.youtube.com/feed/subscriptions"
  ]) {
    assert.equal(getBlockDecision(url).blocked, false, url);
  }
});

test("bloque les routes Reel et Reels d’Instagram", () => {
  for (const url of [
    "https://www.instagram.com/reel/ABC123/",
    "https://instagram.com/reels/",
    "https://www.instagram.com/reels/audio/123456/"
  ]) {
    const decision = getBlockDecision(url);
    assert.equal(decision.blocked, true, url);
    assert.equal(decision.siteId, "instagram");
  }
});

test("laisse les publications et profils Instagram accessibles", () => {
  for (const url of [
    "https://www.instagram.com/",
    "https://www.instagram.com/p/ABC123/",
    "https://www.instagram.com/explore/",
    "https://www.instagram.com/example_profile/"
  ]) {
    assert.equal(getBlockDecision(url).blocked, false, url);
  }
});

test("respecte les réglages par plateforme", () => {
  const settings = {
    sites: { youtube: false, instagram: true }
  };

  assert.equal(
    getBlockDecision("https://www.youtube.com/shorts/abc", settings).blocked,
    false
  );
  assert.equal(
    getBlockDecision("https://www.instagram.com/reel/abc/", settings).blocked,
    true
  );
});

test("refuse les faux domaines et les protocoles non sécurisés", () => {
  assert.equal(isSupportedUrl("https://youtube.com.example.org/shorts/abc"), false);
  assert.equal(isSupportedUrl("https://fakeinstagram.com/reel/abc"), false);
  assert.equal(isSupportedUrl("http://www.youtube.com/shorts/abc"), false);
  assert.equal(getSiteForUrl("not-a-url"), null);
});
