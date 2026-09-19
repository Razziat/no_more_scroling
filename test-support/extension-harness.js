"use strict";

const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { setImmediate: nextTurn } = require("node:timers/promises");
const { SETTINGS_KEY } = require("../src/core/settings.js");
const { LOCKS_KEY, cloneDefaultLocks } = require("../src/core/locks.js");

class Element {
  constructor(tagName = "div") {
    this.tagName = tagName;
    this.children = [];
    this.attributes = {};
    this.listeners = {};
    this.style = { setProperty() {} };
  }
  setAttribute(key, value) { this.attributes[key] = value; }
  toggleAttribute(key, enabled) {
    if (enabled) this.attributes[key] = "";
    else delete this.attributes[key];
  }
  append(...children) {
    for (const child of children) {
      child.parent = this;
      this.children.push(child);
    }
  }
  remove() {
    if (this.parent) {
      this.parent.children = this.parent.children.filter(child => child !== this);
    }
  }
  addEventListener(type, listener) { this.listeners[type] = listener; }
  attachShadow() { this.shadow = new Element(); return this.shadow; }
  focus() {}
}

class Anchor extends Element {
  constructor(href) { super("a"); this.href = href; }
}

class Media extends Element {
  constructor() { super("video"); this.paused = false; }
  pause() { this.paused = true; }
}

function eventSource() {
  const listeners = [];
  return {
    addListener(listener) { listeners.push(listener); },
    emit(...args) { return listeners.map(listener => listener(...args)); }
  };
}

function runFile(context, relativePath) {
  vm.runInContext(fs.readFileSync(path.resolve(__dirname, "..", relativePath), "utf8"), context, {
    filename: relativePath
  });
}

// Execute the production worker and content scripts. Only browser APIs are
// simulated; storage operations deliberately yield to expose overlapping writes.
function createExtension({ locks = cloneDefaultLocks(), punitiveMode = true } = {}) {
  let now = 1_800_000_000_000;
  let failNextWrite = false;
  let nextTabId = 1;
  let delayedRead = null;
  const id = "anti-scroll-test";
  const popupUrl = `chrome-extension://${id}/src/popup/popup.html`;
  const stored = {
    [SETTINGS_KEY]: { punitiveMode, sites: { youtube: true, instagram: true } },
    [LOCKS_KEY]: structuredClone(locks)
  };
  const changed = eventSource();
  const installed = eventSource();
  const startup = eventSource();
  const messages = eventSource();
  const warnings = [];
  const storage = {
    onChanged: changed,
    local: {
      async get(keys) {
        if (delayedRead) {
          const gate = delayedRead;
          delayedRead = null;
          const snapshot = Object.fromEntries(keys.map(key => [key, structuredClone(stored[key])]));
          await gate.promise;
          return snapshot;
        }
        await nextTurn();
        return Object.fromEntries(keys.map(key => [key, structuredClone(stored[key])]));
      },
      async set(values) {
        await nextTurn();
        if (failNextWrite) {
          failNextWrite = false;
          throw new Error("Simulated storage failure");
        }
        const changes = {};
        for (const [key, value] of Object.entries(values)) {
          const next = structuredClone(value);
          if (JSON.stringify(stored[key]) === JSON.stringify(next)) continue;
          changes[key] = { oldValue: structuredClone(stored[key]), newValue: next };
          stored[key] = next;
        }
        if (Object.keys(changes).length) changed.emit(changes, "local");
      }
    }
  };
  const clock = { now: () => now };
  const workerContext = vm.createContext({
    URL, Date: clock, console: { warn: (...args) => warnings.push(args) },
    chrome: {
      storage,
      runtime: {
        id, getURL: p => `chrome-extension://${id}/${p}`,
        onMessage: messages, onInstalled: installed, onStartup: startup
      },
      tabs: { sendMessage: async () => {} },
      webNavigation: { onCommitted: eventSource(), onHistoryStateUpdated: eventSource() }
    },
    importScripts: (...paths) => paths.forEach(p => runFile(workerContext, `src/background/${p}`))
  });
  runFile(workerContext, "src/background/service-worker.js");

  function request(message, sender = { id, url: popupUrl }) {
    return new Promise(resolve => {
      const results = messages.emit(message, sender, resolve);
      if (!results.includes(true)) resolve({ ok: false });
    });
  }

  async function settle() {
    // Several turns cover queued writes plus onChanged-triggered requests.
    for (let index = 0; index < 20; index++) await nextTurn();
  }

  async function createTab(href) {
    const tabId = nextTabId++;
    const location = { href, replace(url) { this.href = url; }, assign(url) { this.href = url; } };
    const root = new Element("html");
    const media = new Media();
    const documentListeners = {};
    const windowListeners = {};
    const intervals = new Map();
    const routeMessages = eventSource();
    let nextIntervalId = 0;
    const sent = [];
    const context = vm.createContext({
      URL, Date: clock, HTMLAnchorElement: Anchor, HTMLMediaElement: Media,
      console: { warn: (...args) => warnings.push(args) },
      AntiScrollBrain: { createBrainAnimation: () => ({ destroy() {} }) },
      AntiScrollI18n: { createI18n: () => ({ locale: "fr", t: key => key }) },
      document: {
        documentElement: root, createElement: tag => new Element(tag),
        querySelectorAll: () => [media],
        addEventListener: (type, listener) => { documentListeners[type] = listener; }
      },
      window: {
        location, history: { length: 1, back() {} },
        setInterval(fn) { const key = ++nextIntervalId; intervals.set(key, fn); return key; },
        clearInterval(key) { intervals.delete(key); },
        requestAnimationFrame() {},
        addEventListener: (type, listener) => { windowListeners[type] = listener; }
      },
      chrome: {
        storage,
        runtime: {
          onMessage: routeMessages,
          sendMessage(message) {
            sent.push(message);
            return request(message, { id, url: location.href, frameId: 0, tab: { id: tabId } });
          }
        }
      }
    });
    for (const file of ["rules", "settings", "locks"]) runFile(context, `src/core/${file}.js`);
    runFile(context, "src/content/content-script.js");
    await settle();
    return {
      sent, media, root,
      get blocker() { return root.children.find(child => child.tagName === "anti-scroll-blocker"); },
      get href() { return location.href; },
      navigate(url) {
        location.href = url;
        routeMessages.emit({ type: "ANTI_SCROLL_ROUTE_CHANGED", url });
      },
      delayedRouteMessage(url) { routeMessages.emit({ type: "ANTI_SCROLL_ROUTE_CHANGED", url }); },
      click(url) {
        documentListeners.click({
          composedPath: () => [new Anchor(url)], preventDefault() {}, stopImmediatePropagation() {}
        });
      },
      play() { media.paused = false; documentListeners.play({ target: media }); },
      tick() { for (const fn of [...intervals.values()]) fn(); }
    };
  }

  return {
    stored, warnings, createTab, request, settle, storage,
    startup: () => startup.emit(),
    advance: delta => { now += delta; },
    failNextWrite: () => { failNextWrite = true; },
    delayNextRead() {
      let release;
      const promise = new Promise(resolve => { release = resolve; });
      delayedRead = { promise };
      return release;
    },
    unlock: siteId => request({ type: "ANTI_SCROLL_UNLOCK", siteId }),
    lock: (siteId, url) => request({ type: "ANTI_SCROLL_LOCK", siteId, url }, {
      id, url, frameId: 0, tab: { id: 99 }
    })
  };
}

module.exports = { createExtension };
