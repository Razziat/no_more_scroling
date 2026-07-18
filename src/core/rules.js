(function initializeRules(root, factory) {
  const api = factory();

  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }

  root.AntiScrollRules = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function createRules() {
  "use strict";

  const SITE_DEFINITIONS = Object.freeze({
    youtube: Object.freeze({
      id: "youtube",
      name: "YouTube",
      productName: "Shorts",
      safeUrl: "https://www.youtube.com/",
      hostnames: Object.freeze(["youtube.com"]),
      blockedPath: /^\/shorts(?:\/|$)/i
    }),
    instagram: Object.freeze({
      id: "instagram",
      name: "Instagram",
      productName: "Reels",
      safeUrl: "https://www.instagram.com/",
      hostnames: Object.freeze(["instagram.com"]),
      blockedPath: /^\/(?:reel|reels)(?:\/|$)/i
    })
  });

  function toUrl(value) {
    if (value instanceof URL) {
      return value;
    }

    try {
      return new URL(String(value));
    } catch (_error) {
      return null;
    }
  }

  function hostnameMatches(hostname, baseHostname) {
    const normalizedHostname = hostname.toLowerCase();
    return (
      normalizedHostname === baseHostname ||
      normalizedHostname.endsWith(`.${baseHostname}`)
    );
  }

  function getSiteForUrl(value) {
    const url = toUrl(value);

    if (!url || url.protocol !== "https:") {
      return null;
    }

    return (
      Object.values(SITE_DEFINITIONS).find((site) =>
        site.hostnames.some((hostname) =>
          hostnameMatches(url.hostname, hostname)
        )
      ) || null
    );
  }

  function isSiteEnabled(siteId, settings) {
    if (!settings || !settings.sites) {
      return true;
    }

    return settings.sites[siteId] !== false;
  }

  function getBlockDecision(value, settings) {
    const url = toUrl(value);
    const site = getSiteForUrl(url);

    if (!url || !site) {
      return Object.freeze({ blocked: false, reason: "unsupported-site" });
    }

    if (!isSiteEnabled(site.id, settings)) {
      return Object.freeze({
        blocked: false,
        reason: "site-disabled",
        siteId: site.id
      });
    }

    if (!site.blockedPath.test(url.pathname)) {
      return Object.freeze({
        blocked: false,
        reason: "allowed-route",
        siteId: site.id
      });
    }

    return Object.freeze({
      blocked: true,
      category: "short-form-video",
      siteId: site.id,
      siteName: site.name,
      productName: site.productName,
      safeUrl: site.safeUrl,
      blockedUrl: url.href
    });
  }

  function isSupportedUrl(value) {
    return Boolean(getSiteForUrl(value));
  }

  return Object.freeze({
    SITE_DEFINITIONS,
    getBlockDecision,
    getSiteForUrl,
    isSupportedUrl
  });
});
