(function initializeI18n(root, factory) {
  const api = factory();

  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }

  root.AntiScrollI18n = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function createI18nApi() {
  "use strict";

  const DEFAULT_LOCALE = "en";
  const runtimeRoot = typeof globalThis !== "undefined" ? globalThis : {};

  const TRANSLATIONS = Object.freeze({
    en: Object.freeze({
      blockReels: "Block Reels",
      blockShorts: "Block Shorts",
      classicContentAvailable:
        "Regular videos and other {site} pages are not blocked.",
      continueOn: "Continue on {site}",
      forThirtyMinutes: "for 30 minutes",
      introDescription:
        "Regular videos remain available. Only short-form infinite-scroll content is blocked.",
      introTitle: "Choose what deserves your attention.",
      lockedRemaining: "Blocked for",
      openHome: "Open {site} home",
      previousPage: "Previous page",
      platformsProtected: "Protected platforms",
      privacy: "No browsing history is sent or collected.",
      productIsBlocked: "{product} are blocked",
      protection: "PROTECTION",
      punitiveDescription: "One attempt blocks the entire site for 30 min.",
      punitiveMode: "Punitive mode",
      siteHome: "{site} home",
      siteIsBlocked: "{site} is blocked",
      siteRemainsAccessible: "{site} remains available",
      statusActiveCount: "{count}/2 active",
      statusActive: "Active",
      statusLockedOne: "1 blocked",
      statusLockedMany: "{count} blocked",
      statusPaused: "Paused",
      stayFocused: "Stay focused!",
      tagline: "The content, without the spiral.",
      timeRemaining: "TIME REMAINING",
      unlock: "Unlock",
      unlockFromExtension: "You can unlock the site from the extension."
    }),
    fr: Object.freeze({
      blockReels: "Bloquer les Reels",
      blockShorts: "Bloquer les Shorts",
      classicContentAvailable:
        "Les vidéos classiques et les autres pages de {site} ne sont pas bloquées.",
      continueOn: "Continuer sur {site}",
      forThirtyMinutes: "pendant 30 minutes",
      introDescription:
        "Les vidéos normales restent disponibles. Seuls les formats courts à défilement infini sont bloqués.",
      introTitle: "Choisis ce qui mérite ton attention.",
      lockedRemaining: "Bloqué encore",
      openHome: "Ouvrir l’accueil {site}",
      previousPage: "Page précédente",
      platformsProtected: "Plateformes protégées",
      privacy: "Aucun historique de navigation n’est envoyé ou collecté.",
      productIsBlocked: "Les {product} sont bloqués",
      protection: "PROTECTION",
      punitiveDescription: "Une tentative bloque tout le site pendant 30 min.",
      punitiveMode: "Mode punitif",
      siteHome: "Accueil {site}",
      siteIsBlocked: "{site} est bloqué",
      siteRemainsAccessible: "{site} reste accessible",
      statusActiveCount: "{count}/2 actifs",
      statusActive: "Actif",
      statusLockedOne: "1 bloqué",
      statusLockedMany: "{count} bloqués",
      statusPaused: "En pause",
      stayFocused: "Reste concentré !",
      tagline: "Le contenu, sans la spirale.",
      timeRemaining: "TEMPS RESTANT",
      unlock: "Débloquer",
      unlockFromExtension:
        "Tu peux déverrouiller le site depuis l’extension."
    })
  });

  function resolveLocale(language) {
    const normalized = String(language || "")
      .trim()
      .replace(/_/g, "-")
      .toLowerCase();

    if (normalized === "fr" || normalized.startsWith("fr-")) {
      return "fr";
    }

    return DEFAULT_LOCALE;
  }

  function getBrowserLanguage() {
    try {
      if (typeof runtimeRoot.chrome?.i18n?.getUILanguage === "function") {
        return runtimeRoot.chrome.i18n.getUILanguage();
      }
    } catch (_error) {
      // navigator.language reste disponible dans les pages de test ordinaires.
    }

    return runtimeRoot.navigator?.language || DEFAULT_LOCALE;
  }

  function interpolate(message, values) {
    return message.replace(/\{([a-zA-Z0-9_]+)\}/g, (match, key) =>
      Object.prototype.hasOwnProperty.call(values, key)
        ? String(values[key])
        : match
    );
  }

  function createI18n(language = getBrowserLanguage()) {
    const locale = resolveLocale(language);
    const messages = TRANSLATIONS[locale];

    return Object.freeze({
      language,
      locale,
      t(key, values = {}) {
        const message = messages[key] || TRANSLATIONS[DEFAULT_LOCALE][key] || key;
        return interpolate(message, values);
      }
    });
  }

  return Object.freeze({
    DEFAULT_LOCALE,
    TRANSLATIONS,
    createI18n,
    getBrowserLanguage,
    resolveLocale
  });
});
