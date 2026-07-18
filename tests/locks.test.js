"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const {
  MANUAL_UNLOCK_GRACE_MS,
  PUNITIVE_DURATION_MS,
  cloneDefaultLocks,
  formatRemainingTime,
  getActiveLock,
  hasPunitiveBypass,
  lockSite,
  normalizeLocks,
  unlockSite
} = require("../src/core/locks.js");

test("crée un verrou de 30 minutes pour un seul site", () => {
  const now = 1_800_000_000_000;
  const locks = lockSite(cloneDefaultLocks(), "youtube", now);

  assert.equal(
    getActiveLock(locks, "youtube", now).expiresAt,
    now + PUNITIVE_DURATION_MS
  );
  assert.equal(getActiveLock(locks, "instagram", now), null);
});

test("un verrou expiré n’est plus actif", () => {
  const now = 1_800_000_000_000;
  const locks = lockSite(cloneDefaultLocks(), "instagram", now);

  assert.equal(
    getActiveLock(locks, "instagram", now + PUNITIVE_DURATION_MS),
    null
  );
});

test("le déblocage manuel retire le verrou et ajoute une courte grâce", () => {
  const now = 1_800_000_000_000;
  const locked = lockSite(cloneDefaultLocks(), "youtube", now);
  const unlocked = unlockSite(locked, "youtube", now + 1000);

  assert.equal(getActiveLock(unlocked, "youtube", now + 1000), null);
  assert.equal(hasPunitiveBypass(unlocked, "youtube", now + 1000), true);
  assert.equal(
    hasPunitiveBypass(
      unlocked,
      "youtube",
      now + 1000 + MANUAL_UNLOCK_GRACE_MS
    ),
    false
  );
});

test("formate le temps restant sans valeur négative", () => {
  const now = 1_800_000_000_000;
  assert.equal(formatRemainingTime(now + PUNITIVE_DURATION_MS, now), "30:00");
  assert.equal(formatRemainingTime(now + 61_000, now), "01:01");
  assert.equal(formatRemainingTime(now - 1000, now), "00:00");
});

test("normalise les données persistées invalides", () => {
  assert.deepEqual(
    normalizeLocks({
      sites: {
        youtube: { expiresAt: "tomorrow", punitiveBypassUntil: -1 }
      }
    }),
    {
      sites: {
        youtube: { expiresAt: 0, punitiveBypassUntil: 0 },
        instagram: { expiresAt: 0, punitiveBypassUntil: 0 }
      }
    }
  );
});
