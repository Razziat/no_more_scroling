(function initializeLocks(root, factory) {
  const api = factory();

  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }

  root.AntiScrollLocks = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function createLocks() {
  "use strict";

  const LOCKS_KEY = "antiScrollLocks";
  const PUNITIVE_DURATION_MS = 30 * 60 * 1000;
  const MANUAL_UNLOCK_GRACE_MS = 5 * 1000;
  const SITE_IDS = Object.freeze(["youtube", "instagram"]);

  function normalizeTimestamp(value) {
    return Number.isFinite(value) && value > 0 ? value : 0;
  }

  function normalizeSiteLock(value) {
    const lock = value && typeof value === "object" ? value : {};
    return {
      expiresAt: normalizeTimestamp(lock.expiresAt),
      punitiveBypassUntil: normalizeTimestamp(lock.punitiveBypassUntil)
    };
  }

  function normalizeLocks(value) {
    const sites = value && typeof value.sites === "object" ? value.sites : {};
    const normalizedSites = {};

    for (const siteId of SITE_IDS) {
      normalizedSites[siteId] = normalizeSiteLock(sites[siteId]);
    }

    return { sites: normalizedSites };
  }

  function cloneDefaultLocks() {
    return normalizeLocks(null);
  }

  function getSiteLock(value, siteId) {
    const locks = normalizeLocks(value);
    return locks.sites[siteId] || normalizeSiteLock(null);
  }

  function getActiveLock(value, siteId, now = Date.now()) {
    const lock = getSiteLock(value, siteId);
    return lock.expiresAt > now ? lock : null;
  }

  function hasPunitiveBypass(value, siteId, now = Date.now()) {
    return getSiteLock(value, siteId).punitiveBypassUntil > now;
  }

  function lockSite(value, siteId, now = Date.now()) {
    const locks = normalizeLocks(value);

    if (!SITE_IDS.includes(siteId)) {
      return locks;
    }

    locks.sites[siteId] = {
      expiresAt: now + PUNITIVE_DURATION_MS,
      punitiveBypassUntil: 0
    };
    return locks;
  }

  function unlockSite(value, siteId, now = Date.now()) {
    const locks = normalizeLocks(value);

    if (!SITE_IDS.includes(siteId)) {
      return locks;
    }

    locks.sites[siteId] = {
      expiresAt: 0,
      punitiveBypassUntil: now + MANUAL_UNLOCK_GRACE_MS
    };
    return locks;
  }

  function formatRemainingTime(expiresAt, now = Date.now()) {
    const totalSeconds = Math.max(0, Math.ceil((expiresAt - now) / 1000));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  }

  return Object.freeze({
    LOCKS_KEY,
    PUNITIVE_DURATION_MS,
    MANUAL_UNLOCK_GRACE_MS,
    SITE_IDS,
    normalizeLocks,
    cloneDefaultLocks,
    getActiveLock,
    hasPunitiveBypass,
    lockSite,
    unlockSite,
    formatRemainingTime
  });
});
