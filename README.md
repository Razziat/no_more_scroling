# Anti-scroll

**Français** | [English](README.en.md)

Extension Brave/Chromium qui bloque les **Shorts YouTube** et les **Reels
Instagram** tout en laissant accessibles les vidéos classiques, les
publications, les profils et la recherche.

Anti-scroll fonctionne entièrement dans le navigateur : aucun compte, aucun
serveur et aucune télémétrie.

## Fonctionnalités

- Blocage ciblé des routes YouTube Shorts et Instagram Reels.
- Navigation normale conservée sur le reste de YouTube et Instagram.
- Mode punitif facultatif : une tentative bloque la plateforme pendant 30
  minutes.
- Minuteur visible et déblocage manuel depuis la popup.
- Écran punitif avec animation neuronale en Canvas 2D.
- Interface française ou anglaise selon la langue de Brave.
- Réglages conservés localement avec `chrome.storage.local`.

## Installation dans Brave

1. Cloner le dépôt :

   ```bash
   git clone https://github.com/Razziat/no_more_scroling.git
   ```

2. Ouvrir `brave://extensions`.
3. Activer **Mode développeur** en haut à droite.
4. Cliquer sur **Charger l’extension non empaquetée**.
5. Sélectionner le dossier `no_more_scroling` qui contient `manifest.json`.
6. Épingler Anti-scroll dans la barre d’outils pour accéder aux interrupteurs.

Après une modification du code, cliquer sur le bouton de rechargement de l’extension dans `brave://extensions`, puis actualiser les onglets YouTube et Instagram déjà ouverts.

## Fonctionnement

- Un clic sur un lien Shorts ou Reels est intercepté avant la navigation.
- Une URL Shorts/Reels ouverte directement affiche l’écran de blocage.
- Les changements de route internes à YouTube et Instagram sont surveillés, même sans rechargement complet de la page.
- Chaque plateforme peut être activée ou désactivée depuis la popup.
- Le **mode punitif**, désactivé par défaut, bloque toute la plateforme pendant 30 minutes après une tentative d’accès à un Short ou un Reel.
- Le temps restant est affiché sur la page bloquée et dans la popup.
- L’interface suit automatiquement la langue de Brave : français pour une
  interface `fr-*`, anglais pour les autres langues.
- La page punitive affiche un cerveau neuronal animé en Canvas 2D : neurones pulsants, connexions courbes et impulsions synaptiques.
- Le bouton **Débloquer** de la popup permet de lever immédiatement une sanction pour une plateforme.
- Les réglages restent dans `chrome.storage.local` et aucun historique n’est envoyé sur un serveur.

Après un déblocage manuel, la plateforme redevient immédiatement accessible. Si l’onglet courant est encore sur une URL Short/Reel, cette page reste bloquée normalement, sans recréer instantanément une sanction de 30 minutes.

## Architecture

```text
manifest.json
src/
├── background/service-worker.js  # Navigations dynamiques Manifest V3
├── content/
│   ├── brain-animation.js         # Réseau neuronal Canvas 2D
│   ├── content-script.js          # Interception et écran de blocage
│   └── content.css                # Verrouillage de la page bloquée
├── core/
│   ├── rules.js                   # Adaptateurs et règles d’URL
│   ├── settings.js                # Valeurs par défaut et normalisation
│   ├── locks.js                   # Sanctions, expiration et déblocage
│   └── i18n.js                    # Textes français/anglais selon Brave
└── popup/
    ├── popup.html
    ├── popup.css
    └── popup.js                   # Réglages par plateforme
```

Le moteur utilise les chemins d’URL comme source principale :

- YouTube : `/shorts` et `/shorts/*`
- Instagram : `/reel/*` et `/reels/*`

Cette approche est moins fragile que des sélecteurs CSS dépendant de l’interface des plateformes.

## Tests

Le projet ne nécessite aucune dépendance. Avec Node.js 18 ou plus récent :

```bash
npm test
```

Les tests couvrent les routes bloquées, les routes autorisées, les sous-domaines, les faux domaines et la désactivation par plateforme.

## Limites actuelles

- Le MVP prend en charge YouTube et Instagram uniquement.
- Les plateformes peuvent modifier leurs routes ; les règles correspondantes se trouvent dans `src/core/rules.js`.
- L’extension cible Brave/Chromium sur ordinateur. Le blocage à l’intérieur des applications mobiles natives demandera une architecture propre à Android/iOS.
