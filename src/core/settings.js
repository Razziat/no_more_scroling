(function initializeSettings(root, factory) {
  const api = factory();

  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }

  root.AntiScrollSettings = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function createSettings() {
  "use strict";

  const SETTINGS_KEY = "antiScrollSettings";

  const DEFAULT_SETTINGS = Object.freeze({
    punitiveMode: false,
    sites: Object.freeze({
      youtube: true,
      instagram: true
    })
  });

  function normalizeSettings(value) {
    const sites = value && typeof value.sites === "object" ? value.sites : {};

    return {
      punitiveMode:
        value && typeof value.punitiveMode === "boolean"
          ? value.punitiveMode
          : DEFAULT_SETTINGS.punitiveMode,
      sites: {
        youtube:
          typeof sites.youtube === "boolean"
            ? sites.youtube
            : DEFAULT_SETTINGS.sites.youtube,
        instagram:
          typeof sites.instagram === "boolean"
            ? sites.instagram
            : DEFAULT_SETTINGS.sites.instagram
      }
    };
  }

  function cloneDefaultSettings() {
    return normalizeSettings(DEFAULT_SETTINGS);
  }

  return Object.freeze({
    SETTINGS_KEY,
    DEFAULT_SETTINGS,
    normalizeSettings,
    cloneDefaultSettings
  });
});
