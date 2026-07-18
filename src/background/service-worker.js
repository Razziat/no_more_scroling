"use strict";

importScripts(
  "../core/rules.js",
  "../core/settings.js",
  "../core/locks.js"
);

const { isSupportedUrl } = globalThis.AntiScrollRules;
const {
  SETTINGS_KEY,
  cloneDefaultSettings,
  normalizeSettings
} = globalThis.AntiScrollSettings;
const {
  LOCKS_KEY,
  cloneDefaultLocks,
  normalizeLocks
} = globalThis.AntiScrollLocks;

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
  ensureStoredStateExists().catch(() => {
    // L'extension continuera avec les réglages par défaut si le stockage échoue.
  });
});

chrome.runtime.onStartup.addListener(() => {
  ensureStoredStateExists().catch(() => {});
});

chrome.webNavigation.onCommitted.addListener(notifyContentScript);
chrome.webNavigation.onHistoryStateUpdated.addListener(notifyContentScript);
