# Anti-scroll

[Français](README.md) | **English**

A Brave/Chromium extension that blocks **YouTube Shorts** and **Instagram
Reels** while keeping regular videos, posts, profiles, and search available.

Anti-scroll runs entirely in your browser: no account, no server, and no
telemetry.

## Features

- Targeted blocking of YouTube Shorts and Instagram Reels routes.
- Normal access to the rest of YouTube and Instagram.
- Optional punitive mode: one attempt blocks the entire platform for 30
  minutes.
- Visible countdown and manual unlock from the extension popup.
- Punitive screen with an animated neural network rendered in Canvas 2D.
- French or English interface based on the Brave UI language.
- Settings stored locally with `chrome.storage.local`.

## Install in Brave

### Direct download — recommended

1. Download the latest version:

   [**Download anti-scroll.zip**](https://github.com/Razziat/no_more_scroling/releases/latest/download/anti-scroll.zip)

2. Extract the ZIP file to a permanent directory.
3. Open `brave://extensions`.
4. Enable **Developer mode** in the top-right corner.
5. Click **Load unpacked**.
6. Select the extracted directory containing `manifest.json`.
7. Pin Anti-scroll to the toolbar to access its controls.

> Brave cannot load the ZIP archive directly. Extract it before selecting the
> extension directory.

### Install with Git

1. Clone the repository:

   ```bash
   git clone https://github.com/Razziat/no_more_scroling.git
   ```

2. Open `brave://extensions`.
3. Enable **Developer mode** in the top-right corner.
4. Click **Load unpacked**.
5. Select the `no_more_scroling` directory containing `manifest.json`.
6. Pin Anti-scroll to the toolbar to access its controls.

After changing the code, reload the extension from `brave://extensions`, then
refresh any YouTube and Instagram tabs that were already open.

## How it works

- Clicking a Shorts or Reels link is intercepted before navigation.
- Opening a Shorts or Reels URL directly displays the blocking screen.
- Internal route changes on YouTube and Instagram are monitored even when the
  page is not fully reloaded.
- Each platform can be enabled or disabled from the popup.
- **Punitive mode** is disabled by default. When enabled, an attempt to access
  a Short or Reel blocks the entire platform for 30 minutes.
- The remaining time is displayed on both the blocking screen and the popup.
- The interface automatically follows the Brave UI language: French for
  `fr-*` locales and English for every other locale.
- The punitive screen displays an animated neural network with pulsing
  neurons, curved connections, and synaptic impulses.
- The popup's **Unlock** button immediately removes a platform penalty.
- Settings remain in `chrome.storage.local`; no browsing history is sent to a
  server.

After a manual unlock, the platform becomes available immediately. If the
current tab still points to a Shorts or Reels URL, that page remains blocked
normally without instantly creating another 30-minute penalty.

## Architecture

```text
manifest.json
src/
├── background/service-worker.js  # Dynamic navigation monitoring (Manifest V3)
├── content/
│   ├── brain-animation.js         # Canvas 2D neural network
│   ├── content-script.js          # Navigation interception and blocking UI
│   └── content.css                # Locks the blocked page
├── core/
│   ├── rules.js                   # URL adapters and blocking rules
│   ├── settings.js                # Defaults and settings normalization
│   ├── locks.js                   # Penalties, expiration, and manual unlock
│   └── i18n.js                    # French/English browser-based translations
└── popup/
    ├── popup.html
    ├── popup.css
    └── popup.js                   # Per-platform settings
```

The blocking engine primarily relies on URL paths:

- YouTube: `/shorts` and `/shorts/*`
- Instagram: `/reel/*` and `/reels/*`

This approach is less fragile than CSS selectors tied to each platform's user
interface.

## Tests

The project has no runtime dependencies. With Node.js 18 or newer:

```bash
npm test
```

Tests cover blocked and allowed routes, subdomains, look-alike domains, per-site
settings, punitive locks, the popup, browser language selection, and the neural
animation model.

## Current limitations

- The MVP supports YouTube and Instagram only.
- Platforms may change their routes; the corresponding rules are located in
  `src/core/rules.js`.
- The extension targets desktop Brave/Chromium. Blocking content inside native
  mobile apps requires a separate Android/iOS architecture.
