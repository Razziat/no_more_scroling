(function initializeContentScript() {
  "use strict";

  const { getBlockDecision, getSiteForUrl } = globalThis.AntiScrollRules;
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
    hasPunitiveBypass,
    lockSite,
    normalizeLocks
  } = globalThis.AntiScrollLocks;
  const { createBrainAnimation } = globalThis.AntiScrollBrain;
  const i18n = globalThis.AntiScrollI18n.createI18n();
  const { locale, t } = i18n;

  const state = {
    settings: cloneDefaultSettings(),
    locks: cloneDefaultLocks(),
    blockerHost: null,
    blockerShadow: null,
    brainController: null,
    countdownTimer: null,
    lockExpiresAt: 0,
    mode: null,
    lastObservedUrl: window.location.href,
    pendingMount: null
  };

  function setPageBlocked(blocked) {
    if (document.documentElement) {
      document.documentElement.toggleAttribute("data-anti-scroll-blocked", blocked);
      if (blocked) {
        document.documentElement.setAttribute("data-anti-scroll-blocked", "true");
      }
    }
  }

  function removeBlocker() {
    setPageBlocked(false);
    window.clearInterval(state.countdownTimer);
    state.countdownTimer = null;
    state.brainController?.destroy();
    state.brainController = null;
    state.blockerHost?.remove();
    state.blockerHost = null;
    state.blockerShadow = null;
    state.mode = null;
    state.lockExpiresAt = 0;

    if (state.pendingMount) {
      state.pendingMount.disconnect();
      state.pendingMount = null;
    }
  }

  function createElement(tagName, className, text) {
    const element = document.createElement(tagName);
    if (className) {
      element.className = className;
    }
    if (typeof text === "string") {
      element.textContent = text;
    }
    return element;
  }

  function createBlockerContent(decision, mode) {
    const isPunitiveLock = mode === "lock";
    const wrapper = createElement(
      "div",
      isPunitiveLock ? "backdrop lock-backdrop" : "backdrop"
    );
    const brand = createElement("div", "brand");
    const mark = createElement("span", "mark", "↕");
    const brandName = createElement("span", "brand-name", "ANTI-SCROLL");
    mark.setAttribute("aria-hidden", "true");
    brand.append(mark, brandName);

    if (isPunitiveLock) {
      const screen = createElement("section", "lock-screen");
      const grid = createElement("div", "lock-grid");
      const copy = createElement("div", "lock-copy");
      const brainStage = createElement("div", "brain-stage");
      const brainCanvas = createElement("canvas", "brain-canvas");
      const title = createElement("h1", "title lock-title", t("stayFocused"));
      const summary = createElement("p", "lock-summary");
      const summaryMain = createElement(
        "span",
        "lock-summary-main",
        t("siteIsBlocked", { site: decision.siteName })
      );
      const summaryAccent = createElement(
        "span",
        "lock-summary-accent",
        t("forThirtyMinutes")
      );
      const divider = createElement("div", "divider lock-divider");
      const timerLabel = createElement("p", "timer-label", t("timeRemaining"));
      const timer = createElement(
        "p",
        "timer",
        formatRemainingTime(decision.expiresAt)
      );
      const reassurance = createElement(
        "p",
        "reassurance lock-reassurance",
        t("unlockFromExtension")
      );

      screen.setAttribute("role", "dialog");
      screen.lang = locale;
      screen.setAttribute("aria-modal", "true");
      screen.setAttribute("aria-labelledby", "anti-scroll-title");
      title.id = "anti-scroll-title";
      divider.setAttribute("aria-hidden", "true");
      timer.setAttribute("role", "timer");
      timer.setAttribute("aria-live", "polite");
      brainCanvas.setAttribute("aria-hidden", "true");
      brainCanvas.width = 960;
      brainCanvas.height = 720;

      summary.append(summaryMain, summaryAccent);
      copy.append(
        title,
        summary,
        divider,
        timerLabel,
        timer,
        reassurance
      );
      brainStage.append(brainCanvas);
      grid.append(copy, brainStage);
      screen.append(brand, grid);
      wrapper.append(screen);

      return {
        wrapper,
        primary: null,
        timer,
        brainCanvas
      };
    }

    const card = createElement("section", "card");
    const eyebrow = createElement(
      "p",
      "eyebrow",
      `${decision.siteName} · ${decision.productName}`
    );
    const title = createElement("h1", "title", t("stayFocused"));
    const summary = createElement("p", "normal-summary");
    const summaryMain = createElement(
      "span",
      "normal-summary-main",
      t("productIsBlocked", { product: decision.productName })
    );
    const summaryAccent = createElement(
      "span",
      "normal-summary-accent",
      t("siteRemainsAccessible", { site: decision.siteName })
    );
    const divider = createElement("div", "divider");
    const reassurance = createElement(
      "p",
      "reassurance",
      t("classicContentAvailable", { site: decision.siteName })
    );
    const actions = createElement("div", "actions");
    const primary = createElement(
      "button",
      "button button-primary",
      mode === "attempt"
        ? t("continueOn", { site: decision.siteName })
        : t("siteHome", { site: decision.siteName })
    );
    const secondary = createElement(
      "button",
      "button button-secondary",
      mode === "attempt"
        ? t("openHome", { site: decision.siteName })
        : t("previousPage")
    );

    card.setAttribute("role", "dialog");
    card.lang = locale;
    card.setAttribute("aria-modal", "true");
    card.setAttribute("aria-labelledby", "anti-scroll-title");
    title.id = "anti-scroll-title";
    divider.setAttribute("aria-hidden", "true");
    primary.type = "button";
    secondary.type = "button";

    primary.addEventListener("click", () => {
      if (mode === "attempt") {
        removeBlocker();
        return;
      }

      window.location.replace(decision.safeUrl);
    });

    secondary.addEventListener("click", () => {
      if (mode === "attempt") {
        window.location.assign(decision.safeUrl);
        return;
      }

      if (window.history.length > 1) {
        window.history.back();
      } else {
        window.location.replace(decision.safeUrl);
      }
    });

    summary.append(summaryMain, summaryAccent);
    actions.append(primary, secondary);
    card.append(brand, eyebrow, title, summary, divider, reassurance, actions);
    wrapper.append(card);

    return { wrapper, primary, timer: null, brainCanvas: null };
  }

  function injectBlockerStyles(shadow) {
    const style = document.createElement("style");
    style.textContent = `
      :host {
        color-scheme: dark;
        font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont,
          "Segoe UI", sans-serif;
      }

      * { box-sizing: border-box; }

      .backdrop {
        align-items: center;
        background:
          radial-gradient(circle at 12% 18%, rgba(105, 255, 181, 0.13), transparent 34%),
          radial-gradient(circle at 88% 82%, rgba(80, 116, 255, 0.12), transparent 34%),
          #090b0b;
        color: #f4f7f5;
        display: flex;
        height: 100%;
        justify-content: center;
        padding: 24px;
        width: 100%;
      }

      .card {
        background: rgba(19, 23, 22, 0.96);
        border: 1px solid rgba(255, 255, 255, 0.1);
        border-radius: 24px;
        box-shadow: 0 28px 90px rgba(0, 0, 0, 0.46);
        max-width: 560px;
        padding: clamp(28px, 5vw, 52px);
        width: 100%;
      }

      .lock-backdrop {
        align-items: stretch;
        background:
          radial-gradient(circle at 76% 44%, rgba(42, 203, 163, 0.055), transparent 34%),
          #070b0d;
        min-height: 100%;
        overflow: auto;
        padding: 0;
      }

      .lock-screen {
        display: flex;
        flex-direction: column;
        margin: 0 auto;
        max-width: 1680px;
        min-height: 100%;
        padding: clamp(28px, 3.4vw, 58px) clamp(26px, 5vw, 88px);
        position: relative;
        width: 100%;
      }

      .lock-screen > .brand {
        flex: 0 0 auto;
        margin-bottom: 0;
        min-height: 30px;
        position: relative;
        z-index: 4;
      }

      .lock-grid {
        align-items: center;
        display: grid;
        flex: 1;
        gap: clamp(28px, 4vw, 76px);
        grid-template-columns: minmax(470px, 0.95fr) minmax(480px, 1.05fr);
        min-height: 0;
      }

      .lock-copy {
        min-width: 0;
        padding: 40px 0 52px;
        position: relative;
        z-index: 2;
      }

      .lock-title {
        font-size: clamp(44px, 3.7vw, 64px);
        line-height: 1.02;
        max-width: 650px;
      }

      .lock-summary {
        font-size: clamp(27px, 2.4vw, 40px);
        font-weight: 720;
        letter-spacing: -0.035em;
        line-height: 1.12;
        margin: 24px 0 0;
        max-width: 620px;
      }

      .lock-summary-main,
      .lock-summary-accent {
        display: block;
      }

      .lock-summary-accent {
        color: #74efaa;
        margin-top: 5px;
        white-space: nowrap;
      }

      .lock-divider {
        background: rgba(255, 255, 255, 0.085);
        margin: clamp(30px, 4vh, 48px) 0 30px;
        max-width: 540px;
      }

      .lock-reassurance {
        font-size: 12px;
        margin-top: 22px;
      }

      .brain-stage {
        height: min(76vh, 820px);
        min-height: 430px;
        min-width: 0;
        position: relative;
        width: 100%;
      }

      .brain-canvas {
        display: block;
        height: 100%;
        width: 100%;
      }

      .brand {
        align-items: center;
        display: flex;
        gap: 10px;
        margin-bottom: 48px;
      }

      .mark {
        align-items: center;
        background: #68f5ad;
        border-radius: 9px;
        color: #07110c;
        display: inline-flex;
        font-size: 17px;
        font-weight: 900;
        height: 30px;
        justify-content: center;
        transform: rotate(45deg);
        width: 30px;
      }

      .mark::first-letter { transform: rotate(-45deg); }

      .brand-name {
        color: #dce5e0;
        font-size: 12px;
        font-weight: 800;
        letter-spacing: 0.16em;
      }

      .eyebrow {
        color: #68f5ad;
        font-size: 12px;
        font-weight: 800;
        letter-spacing: 0.12em;
        margin: 0 0 14px;
        text-transform: uppercase;
      }

      .title {
        font-size: clamp(34px, 7vw, 56px);
        font-weight: 730;
        letter-spacing: -0.045em;
        line-height: 0.98;
        margin: 0;
        max-width: 480px;
      }

      .normal-summary {
        font-size: clamp(21px, 4vw, 28px);
        font-weight: 720;
        letter-spacing: -0.025em;
        line-height: 1.2;
        margin: 24px 0 0;
      }

      .normal-summary-main,
      .normal-summary-accent {
        display: block;
      }

      .normal-summary-accent {
        color: #68f5ad;
        margin-top: 4px;
      }

      .timer-label {
        color: #68f5ad;
        font-size: 11px;
        font-weight: 850;
        letter-spacing: 0.14em;
        margin: 34px 0 8px;
      }

      .timer {
        color: #78efab;
        font-size: clamp(76px, 9vw, 148px);
        font-variant-numeric: tabular-nums;
        font-weight: 760;
        letter-spacing: -0.055em;
        line-height: 0.95;
        margin: 0;
      }

      .divider {
        background: rgba(255, 255, 255, 0.1);
        height: 1px;
        margin: 32px 0 22px;
      }

      .reassurance {
        color: #7f8b85;
        font-size: 13px;
        line-height: 1.55;
        margin: 0;
      }

      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        margin-top: 32px;
      }

      .button {
        border: 0;
        border-radius: 12px;
        cursor: pointer;
        font: inherit;
        font-size: 14px;
        font-weight: 750;
        min-height: 48px;
        padding: 0 20px;
        transition: transform 120ms ease, background 120ms ease;
      }

      .button:hover { transform: translateY(-1px); }
      .button:active { transform: translateY(0); }
      .button:focus-visible { outline: 3px solid rgba(104, 245, 173, 0.38); outline-offset: 3px; }

      .button-primary {
        background: #68f5ad;
        color: #07110c;
      }

      .button-primary:hover { background: #7affb9; }

      .button-secondary {
        background: rgba(255, 255, 255, 0.075);
        color: #e9efec;
      }

      .button-secondary:hover { background: rgba(255, 255, 255, 0.12); }

      @media (max-width: 980px) {
        .lock-screen {
          min-height: auto;
          padding-bottom: 54px;
        }

        .lock-grid {
          gap: 0;
          grid-template-columns: minmax(0, 1fr);
          width: 100%;
        }

        .brain-stage {
          height: min(48vh, 430px);
          min-height: 290px;
          order: -1;
        }

        .lock-copy {
          margin: 0 auto;
          max-width: 680px;
          padding: 12px 0 28px;
          width: 100%;
        }

        .lock-title {
          font-size: clamp(40px, 7vw, 66px);
          max-width: 100%;
        }

        .lock-summary-accent {
          white-space: normal;
        }
      }

      @media (max-width: 520px) {
        .card { border-radius: 20px; padding: 28px; }
        .brand { margin-bottom: 36px; }
        .actions { flex-direction: column; }
        .button { width: 100%; }

        .lock-screen {
          padding: 24px 22px 40px;
        }

        .lock-screen > .brand {
          margin-bottom: 0;
        }

        .brain-stage {
          height: 280px;
          min-height: 280px;
        }

        .lock-copy {
          padding-top: 4px;
        }

        .lock-title {
          font-size: clamp(36px, 10vw, 48px);
        }

        .lock-summary {
          font-size: clamp(25px, 7vw, 34px);
          margin-top: 20px;
        }

        .lock-divider {
          margin: 28px 0 24px;
        }

        .timer {
          font-size: clamp(72px, 23vw, 108px);
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .button { transition: none; }
      }
    `;
    shadow.append(style);
  }

  function mountBlockerNow(decision, mode) {
    removeBlocker();

    const host = document.createElement("anti-scroll-blocker");
    host.style.setProperty("display", "block", "important");
    host.style.setProperty("position", "fixed", "important");
    host.style.setProperty("inset", "0", "important");
    host.style.setProperty("z-index", "2147483647", "important");
    host.style.setProperty("visibility", "visible", "important");
    host.style.setProperty("pointer-events", "auto", "important");
    host.setAttribute("data-anti-scroll-locale", locale);
    host.setAttribute(
      "aria-label",
      mode === "lock"
        ? `${t("stayFocused")} ${t("siteIsBlocked", {
            site: decision.siteName
          })} ${t("forThirtyMinutes")}`
        : `${t("stayFocused")} ${t("productIsBlocked", {
            product: decision.productName
          })} ${t("siteRemainsAccessible", { site: decision.siteName })}`
    );

    const shadow = host.attachShadow({ mode: "closed" });
    injectBlockerStyles(shadow);
    const { wrapper, primary, timer, brainCanvas } = createBlockerContent(
      decision,
      mode
    );
    shadow.append(wrapper);

    state.blockerHost = host;
    state.blockerShadow = shadow;
    state.mode = mode;
    state.lockExpiresAt = mode === "lock" ? decision.expiresAt : 0;

    if (mode === "route" || mode === "lock") {
      setPageBlocked(true);
    }

    document.documentElement.append(host);

    if (brainCanvas) {
      state.brainController = createBrainAnimation(brainCanvas, {
        seed: decision.siteId === "instagram" ? 91273 : 87421
      });
    }

    if (mode === "lock" && timer) {
      const updateCountdown = () => {
        const remaining = decision.expiresAt - Date.now();
        timer.textContent = formatRemainingTime(decision.expiresAt);

        if (remaining <= 0) {
          window.clearInterval(state.countdownTimer);
          state.countdownTimer = null;
          window.location.replace(decision.safeUrl);
        }
      };

      updateCountdown();
      state.countdownTimer = window.setInterval(updateCountdown, 250);
    } else if (primary) {
      window.requestAnimationFrame(() => primary.focus());
    }
  }

  function mountBlocker(decision, mode) {
    if (document.documentElement) {
      mountBlockerNow(decision, mode);
      return;
    }

    state.pendingMount?.disconnect();
    state.pendingMount = new MutationObserver(() => {
      if (!document.documentElement) {
        return;
      }

      state.pendingMount.disconnect();
      state.pendingMount = null;
      mountBlockerNow(decision, mode);
    });
    state.pendingMount.observe(document, { childList: true });
  }

  function createLockDecision(site, lock) {
    return {
      blocked: true,
      category: "punitive-site-lock",
      siteId: site.id,
      siteName: site.name,
      productName: site.productName,
      safeUrl: site.safeUrl,
      expiresAt: lock.expiresAt
    };
  }

  function activatePunitiveLock(shortDecision) {
    const now = Date.now();
    state.locks = lockSite(state.locks, shortDecision.siteId, now);
    const lock = getActiveLock(state.locks, shortDecision.siteId, now);
    const site = getSiteForUrl(shortDecision.blockedUrl);

    chrome.storage.local.set({ [LOCKS_KEY]: state.locks }).catch(() => {});
    return createLockDecision(site, lock);
  }

  function evaluateUrl(value, source) {
    const site = getSiteForUrl(value);
    const activeLock = site
      ? getActiveLock(state.locks, site.id, Date.now())
      : null;

    if (site && activeLock) {
      const lockDecision = createLockDecision(site, activeLock);
      const alreadyShowingSameLock =
        state.mode === "lock" &&
        state.lockExpiresAt === activeLock.expiresAt;

      if (!alreadyShowingSameLock) {
        mountBlocker(lockDecision, "lock");
      }
      return lockDecision;
    }

    const decision = getBlockDecision(value, state.settings);

    if (decision.blocked) {
      if (
        state.settings.punitiveMode &&
        !hasPunitiveBypass(state.locks, decision.siteId, Date.now())
      ) {
        const lockDecision = activatePunitiveLock(decision);
        mountBlocker(lockDecision, "lock");
        return lockDecision;
      }

      const alreadyShowingSameRoute =
        state.mode === "route" && decision.blockedUrl === window.location.href;

      if (!alreadyShowingSameRoute) {
        mountBlocker(decision, source === "click" ? "attempt" : "route");
      }
      return decision;
    }

    if (state.mode === "route" || state.mode === "lock") {
      removeBlocker();
    }

    return decision;
  }

  function evaluateCurrentLocation(source) {
    state.lastObservedUrl = window.location.href;
    return evaluateUrl(window.location.href, source);
  }

  function findLinkInEvent(event) {
    for (const target of event.composedPath()) {
      if (target instanceof HTMLAnchorElement && target.href) {
        return target;
      }
    }

    return null;
  }

  function interceptNavigation(event) {
    const link = findLinkInEvent(event);
    if (!link) {
      return;
    }

    const decision = getBlockDecision(link.href, state.settings);
    if (!decision.blocked) {
      return;
    }

    event.preventDefault();
    event.stopImmediatePropagation();
    evaluateUrl(link.href, "click");
  }

  async function loadStoredState() {
    try {
      const stored = await chrome.storage.local.get([SETTINGS_KEY, LOCKS_KEY]);
      state.settings = normalizeSettings(stored[SETTINGS_KEY]);
      state.locks = normalizeLocks(stored[LOCKS_KEY]);
    } catch (_error) {
      state.settings = cloneDefaultSettings();
      state.locks = cloneDefaultLocks();
    }

    evaluateCurrentLocation("settings-loaded");
  }

  document.addEventListener("click", interceptNavigation, true);
  document.addEventListener("auxclick", interceptNavigation, true);

  window.addEventListener("popstate", () => evaluateCurrentLocation("popstate"));
  window.addEventListener("hashchange", () => evaluateCurrentLocation("hashchange"));

  chrome.runtime.onMessage.addListener((message) => {
    if (message?.type === "ANTI_SCROLL_ROUTE_CHANGED" && message.url) {
      evaluateUrl(message.url, "web-navigation");
    }
  });

  chrome.storage.onChanged.addListener((changes, areaName) => {
    if (areaName !== "local") {
      return;
    }

    let shouldEvaluate = false;

    if (changes[SETTINGS_KEY]) {
      state.settings = normalizeSettings(changes[SETTINGS_KEY].newValue);
      shouldEvaluate = true;
    }

    if (changes[LOCKS_KEY]) {
      state.locks = normalizeLocks(changes[LOCKS_KEY].newValue);
      shouldEvaluate = true;
    }

    if (shouldEvaluate) {
      evaluateCurrentLocation("stored-state-changed");
    }
  });

  // Filet de sécurité pour les applications monopages qui modifient l'URL sans
  // produire un événement DOM exploitable par le content script.
  window.setInterval(() => {
    if (window.location.href !== state.lastObservedUrl) {
      evaluateCurrentLocation("url-watch");
    }
  }, 750);

  evaluateCurrentLocation("initial");
  loadStoredState();
})();
