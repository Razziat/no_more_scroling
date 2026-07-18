(function initializePopup() {
  "use strict";

  const {
    SETTINGS_KEY,
    cloneDefaultSettings,
    normalizeSettings
  } = globalThis.AntiScrollSettings;
  const {
    LOCKS_KEY,
    cloneDefaultLocks,
    formatRemainingTime,
    getActiveLock,
    normalizeLocks,
    unlockSite
  } = globalThis.AntiScrollLocks;
  const { locale, t } = globalThis.AntiScrollI18n.createI18n();

  const siteToggles = Array.from(document.querySelectorAll("[data-site]"));
  const platformCards = Array.from(
    document.querySelectorAll("[data-platform-card]")
  );
  const unlockButtons = Array.from(document.querySelectorAll("[data-unlock]"));
  const punitiveToggle = document.querySelector("#punitive-toggle");
  const status = document.querySelector("#status");
  let currentSettings = cloneDefaultSettings();
  let currentLocks = cloneDefaultLocks();

  document.documentElement.lang = locale;

  for (const element of document.querySelectorAll("[data-i18n]")) {
    element.textContent = t(element.dataset.i18n);
  }

  for (const element of document.querySelectorAll("[data-i18n-aria-label]")) {
    element.setAttribute("aria-label", t(element.dataset.i18nAriaLabel));
  }

  function render() {
    const now = Date.now();
    let lockedCount = 0;

    punitiveToggle.checked = currentSettings.punitiveMode;

    for (const toggle of siteToggles) {
      toggle.checked = currentSettings.sites[toggle.dataset.site] !== false;
    }

    for (const card of platformCards) {
      const siteId = card.dataset.platformCard;
      const activeLock = getActiveLock(currentLocks, siteId, now);
      const toggle = card.querySelector(`[data-site="${siteId}"]`);
      const panel = card.querySelector("[data-lock-panel]");
      const timer = card.querySelector("[data-lock-timer]");

      card.classList.toggle("locked", Boolean(activeLock));
      panel.hidden = !activeLock;
      toggle.disabled = Boolean(activeLock);

      if (activeLock) {
        lockedCount += 1;
        timer.textContent = formatRemainingTime(activeLock.expiresAt, now);
      }
    }

    const activeCount = Object.values(currentSettings.sites).filter(Boolean).length;
    status.classList.toggle("locked", lockedCount > 0);
    status.classList.toggle("off", lockedCount === 0 && activeCount === 0);

    if (lockedCount > 0) {
      status.textContent = t(
        lockedCount === 1 ? "statusLockedOne" : "statusLockedMany",
        { count: lockedCount }
      );
    } else {
      status.textContent = activeCount > 0
        ? t("statusActiveCount", { count: activeCount })
        : t("statusPaused");
    }
  }

  async function saveSettings(nextSettings) {
    currentSettings = normalizeSettings(nextSettings);
    render();
    await chrome.storage.local.set({ [SETTINGS_KEY]: currentSettings });
  }

  async function saveSite(siteId, enabled) {
    await saveSettings({
      ...currentSettings,
      sites: {
        ...currentSettings.sites,
        [siteId]: enabled
      }
    });
  }

  async function setPunitiveMode(enabled) {
    await saveSettings({ ...currentSettings, punitiveMode: enabled });
  }

  async function manuallyUnlock(siteId) {
    currentLocks = unlockSite(currentLocks, siteId, Date.now());
    render();
    await chrome.storage.local.set({ [LOCKS_KEY]: currentLocks });
  }

  async function load() {
    try {
      const stored = await chrome.storage.local.get([SETTINGS_KEY, LOCKS_KEY]);
      currentSettings = normalizeSettings(stored[SETTINGS_KEY]);
      currentLocks = normalizeLocks(stored[LOCKS_KEY]);
    } catch (_error) {
      currentSettings = cloneDefaultSettings();
      currentLocks = cloneDefaultLocks();
    }

    render();
  }

  for (const toggle of siteToggles) {
    toggle.addEventListener("change", () => {
      saveSite(toggle.dataset.site, toggle.checked).catch(load);
    });
  }

  punitiveToggle.addEventListener("change", () => {
    setPunitiveMode(punitiveToggle.checked).catch(load);
  });

  for (const button of unlockButtons) {
    button.addEventListener("click", () => {
      manuallyUnlock(button.dataset.unlock).catch(load);
    });
  }

  chrome.storage.onChanged.addListener((changes, areaName) => {
    if (areaName !== "local") {
      return;
    }

    if (changes[SETTINGS_KEY]) {
      currentSettings = normalizeSettings(changes[SETTINGS_KEY].newValue);
    }

    if (changes[LOCKS_KEY]) {
      currentLocks = normalizeLocks(changes[LOCKS_KEY].newValue);
    }

    render();
  });

  window.setInterval(render, 500);
  load();
})();
