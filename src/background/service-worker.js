"use strict";

importScripts(
  "../core/rules.js",
  "../core/settings.js",
  "../core/locks.js"
);

const { isSupportedUrl, getBlockDecision } = globalThis.AntiScrollRules;
const {
  SETTINGS_KEY,
  cloneDefaultSettings,
  normalizeSettings
} = globalThis.AntiScrollSettings;
const {
  LOCKS_KEY,
  cloneDefaultLocks,
  normalizeLocks,
  SITE_IDS,
  getActiveLock,
  hasPunitiveBypass,
  lockSite,
  unlockSite
} = globalThis.AntiScrollLocks;

// Every lock mutation (including initialization) goes through one writer.
// Re-read storage inside the queue: content scripts may hold stale snapshots.
let mutationQueue = Promise.resolve();

function enqueueMutation(operation) {
  const result = mutationQueue.then(operation);
  mutationQueue = result.catch(() => {});
  return result;
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!["ANTI_SCROLL_LOCK", "ANTI_SCROLL_UNLOCK"].includes(message?.type)) {
    return;
  }

  const isUnlock = message.type === "ANTI_SCROLL_UNLOCK";
  const trustedSender = sender.id === chrome.runtime.id && (
    isUnlock
      ? sender.url === chrome.runtime.getURL("src/popup/popup.html")
      : sender.frameId === 0 && isSupportedUrl(sender.url)
  );
  if (!trustedSender || !SITE_IDS.includes(message.siteId)) {
    sendResponse({ ok: false });
    return;
  }

  enqueueMutation(async () => {
    const stored = await chrome.storage.local.get([SETTINGS_KEY, LOCKS_KEY]);
    const settings = normalizeSettings(stored[SETTINGS_KEY]);
    const locks = normalizeLocks(stored[LOCKS_KEY]);
    const now = Date.now();
    if (!isUnlock) {
      const decision = getBlockDecision(message.url, settings);
      if (!settings.punitiveMode || !decision.blocked ||
          decision.siteId !== message.siteId ||
          getActiveLock(locks, message.siteId, now) ||
          hasPunitiveBypass(locks, message.siteId, now)) {
        return;
      }
    }
    const nextLocks = isUnlock
      ? unlockSite(locks, message.siteId, now)
      : lockSite(locks, message.siteId, now);
    await chrome.storage.local.set({ [LOCKS_KEY]: nextLocks });
  }).then(
    () => sendResponse({ ok: true }),
    (error) => {
      console.warn("Anti-scroll: unable to persist lock change", error);
      sendResponse({ ok: false });
    }
  );
  // Keep the message channel open until the queued write has completed.
  return true;
});

async function ensureStoredStateExists() {
  const stored = await chrome.storage.local.get([SETTINGS_KEY, LOCKS_KEY]);
  const changes = {};

  if (!stored[SETTINGS_KEY]) {
    changes[SETTINGS_KEY] = cloneDefaultSettings();
  } else {
    changes[SETTINGS_KEY] = normalizeSettings(stored[SETTINGS_KEY]);
  }

  changes[LOCKS_KEY] = stored[LOCKS_KEY]
    ? normalizeLocks(stored[LOCKS_KEY])
    : cloneDefaultLocks();

  await chrome.storage.local.set(changes);
}

function notifyContentScript(details) {
  if (
    details.frameId !== 0 ||
    typeof details.tabId !== "number" ||
    !isSupportedUrl(details.url)
  ) {
    return;
  }

  chrome.tabs
    .sendMessage(details.tabId, {
      type: "ANTI_SCROLL_ROUTE_CHANGED",
      url: details.url
    })
    .catch(() => {
      // Le content script peut ne pas encore être prêt lors d'une navigation complète.
    });
}

chrome.runtime.onInstalled.addListener(() => {
  enqueueMutation(ensureStoredStateExists).catch(() => {
    // L'extension continuera avec les réglages par défaut si le stockage échoue.
  });
});

chrome.runtime.onStartup.addListener(() => {
  enqueueMutation(ensureStoredStateExists).catch(() => {});
});

chrome.webNavigation.onCommitted.addListener(notifyContentScript);
chrome.webNavigation.onHistoryStateUpdated.addListener(notifyContentScript);
