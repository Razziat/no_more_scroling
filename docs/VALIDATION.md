# Validation des blocages

## Vérifications automatisées

À la racine : `node --test` (ou `npm test`). Aucune installation de dépendances.
Les scénarios de `tests/extension-integration.test.js` exécutent le service worker
et les scripts de contenu de production avec des API navigateur simulées et
des écritures asynchrones. Ils couvrent les sanctions simultanées, le déblocage,
les erreurs de stockage, les notifications tardives et la pause des médias.
Ils ne remplacent pas un essai dans Chromium/Brave.

Dans `mobile/android`, avec Java et le SDK Android configurés :

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Le workflow `.github/workflows/ci.yml` lance ces vérifications sur les pull
requests et les pushes sur `main`/`master`, sans clé de signature release.

## Essais navigateur avant publication

Recharger l'extension **et actualiser ses onglets existants** après mise à jour.

1. En mode normal, ouvrir directement un Short et un Reel, puis essayer depuis
   un lien, un clic central et l'historique. Les pages classiques restent accessibles.
2. En mode punitif, déclencher YouTube et Instagram depuis deux fenêtres.
   Les deux sanctions doivent rester actives, sans prolongation au prochain clic.
3. Débloquer YouTube, garder l'URL Shorts ouverte, attendre plus de cinq secondes
   puis modifier la sanction Instagram. YouTube garde son blocage de route, sans
   nouvelle sanction. Une nouvelle navigation vers un Short peut être sanctionnée
   après la courte grâce de déblocage.
4. Vérifier l'absence de son derrière le blocage, y compris lors d'une tentative
   de lecture automatique. Le déblocage ne doit pas relancer les médias tout seul.
5. Tester expiration, rechargement de la page et redémarrage du navigateur.

## Essais Android sur téléphone

Noter le modèle, la version Android, les versions YouTube/Instagram et la langue.
Réaliser ces essais sur au moins un appareil modeste et un appareil récent.

1. Vérifier le message de blocage normal et le message de sanction : ils doivent
   apparaître sans erreur `BlockOverlay` dans logcat.
   Le premier message indique « Instagram bloqué pendant 30 min ». Il reste
   lisible après la sortie et disparaît au bout de trois secondes, même si
   Instagram reste dans les applications récentes. Attendre dix secondes, puis
   rouvrir Instagram : l'application doit être quittée à nouveau et un message
   « Instagram bloqué — encore … » doit apparaître brièvement. Aucun décompte
   animé ne doit rester à l'écran. Le minuteur détaillé reste dans Anti Scroll.
2. Ouvrir puis quitter rapidement un Short/Reel avant le scan. Le retour au fil
   ne doit pas déclencher un deuxième Retour ni une sanction basée sur l'ancien écran.
3. Vérifier que les lecteurs Shorts/Reels restent détectés. Les scans différés
   utilisent désormais l'arbre actuel ; une classe d'événement ancienne ne suffit
   plus à bloquer. Vérifier en particulier les versions aux identifiants de vues
   obfusqués et les ouvertures depuis un lien externe.
4. Alterner fil/Explorer, messages, profils, clavier, panneau système et autre
   application. Consommer deux minutes puis quitter Instagram, attendre plus de
   cinq secondes puis refaire l’essai après une longue pause. Il doit rester
   trois minutes au retour dans la même journée, sans compter le temps d’absence.
5. Désactiver/réactiver la limite de session, éteindre/rallumer l'écran, puis
   relancer le service. Le minuteur doit reprendre uniquement quand nécessaire.
6. Tester le premier dépassement, le second dépassement, l'expiration, le passage
   à minuit et le déblocage manuel du mode punitif.
7. Pendant un blocage jusqu’à minuit, changer le fuseau du téléphone sans relancer
   le service. Le blocage doit suivre le prochain minuit local. À Londres, le
   changement de jour à Paris ne doit pas remettre les compteurs à zéro.

## Mesures de performance

Les temps des tests unitaires ne mesurent ni la batterie ni le délai de blocage.
Pour une comparaison, utiliser le même téléphone et les mêmes versions d'apps,
avec un scénario de défilement identique avant/après modification.

- Capturer une trace système et suivre la section `AntiScroll.captureUiTree` :
  durée médiane, percentile 95 et fréquence des captures.
- Mesurer le délai entre ouverture d'un lecteur court et action Retour.
- Comparer CPU, allocations/mémoire et batterie pendant le fil, les messages,
  puis avec la limite désactivée et l'écran éteint.
- Dans les outils de développement du navigateur, profiler la page bloquée et
  l'animation, en distinguant le coût de l'extension de celui de la plateforme.

Ne jamais enregistrer le texte des messages ou les arbres d'accessibilité dans
les traces partagées. La section de trace ajoutée ne contient aucun contenu utilisateur.
