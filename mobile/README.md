# Anti Scroll pour Android

[English](README.en.md)

Cette première version Android bloque les lecteurs **YouTube Shorts** et
**Instagram Reels** tout en conservant l’accès aux autres parties des deux
applications.

## État du MVP

- Application Android native en Kotlin et Jetpack Compose.
- Compatibilité Android 8.0 ou version ultérieure.
- Activation indépendante du blocage YouTube et Instagram.
- Détection locale avec un service d’accessibilité Android.
- Retour automatique à l’écran précédent lorsqu’un Short ou Reel est détecté.
- Message temporaire « Reste concentré ! ».
- Mode punitif facultatif : une tentative bloque toute la plateforme pendant
  30 minutes, avec minuteur et déblocage manuel.
- Limite facultative des sessions Instagram : 5 minutes sur le fil d’accueil
  ou Explorer, avec pause du minuteur dans les messages et les profils.
- Après un premier dépassement, Instagram est bloqué pendant 30 minutes. Un
  second dépassement dans la même journée le bloque jusqu’à minuit.
- Interface française ou anglaise selon la langue Android.
- Aucune permission Internet et aucune donnée transmise.

Une nouvelle tentative pendant une sanction ne prolonge pas les 30 minutes :
elle affiche uniquement le temps restant.

Un message discret reste visible pendant trois secondes après la fermeture de
l’application bloquée. Il indique la durée au début de la sanction, puis le temps
restant lors des tentatives suivantes. Le minuteur détaillé et le déblocage manuel
restent disponibles dans Anti Scroll.

En mode punitif, Anti Scroll quitte l’application sanctionnée avec l’action
Android **Retour**. Il ne déclenche pas l’action **Accueil** : la page du bureau
ou l’application visible avant l’ouverture est donc conservée.

## Ouvrir et compiler le projet

1. Installer la version stable récente d’Android Studio.
2. Ouvrir le dossier `mobile/android`.
3. Laisser Android Studio installer le SDK Android demandé et synchroniser
   Gradle.
4. Brancher un téléphone Android avec le débogage USB activé.
5. Lancer la configuration `app`.

La version de débogage peut aussi être générée en ligne de commande :

```powershell
cd mobile/android
.\gradlew.bat testDebugUnitTest assembleDebug
```

L’APK généré se trouve dans
`mobile/android/app/build/outputs/apk/debug/app-debug.apk`.

La procédure de création d’un APK release signé et de publication automatisée
sur GitHub est décrite dans [RELEASING.md](RELEASING.md). Ne jamais publier
l’APK de debug.

## Première activation

1. Ouvrir Anti Scroll.
2. Choisir les plateformes à protéger.
3. Activer facultativement les **Sessions de fil limitées à 5 minutes**.
4. Activer facultativement le **Mode punitif** pour les Shorts et Reels.
5. Appuyer sur **Activer la protection**.
6. Lire et accepter la déclaration d’utilisation.
7. Dans les réglages Android, sélectionner **Protection Anti Scroll** et
   activer le service.

### APK installé manuellement sur Android 13 ou supérieur

Android peut empêcher l’activation du service d’accessibilité lorsqu’Anti
Scroll a été installé depuis un fichier APK plutôt que depuis une boutique
d’applications. Dans ce cas :

1. Ouvrir **Paramètres > Applications > Anti Scroll**.
2. Ouvrir le menu **⋮** en haut à droite.
3. Choisir **Autoriser les paramètres restreints** et confirmer son identité.
4. Revenir dans **Paramètres > Accessibilité > Applications installées**.
5. Sélectionner **Protection Anti Scroll**, puis activer le service.

Le nom exact des menus peut varier selon le fabricant. Cette protection est un
mécanisme de sécurité Android pour les applications installées manuellement ;
la procédure est décrite dans
[l’aide officielle Android](https://support.google.com/android/answer/12623953?hl=fr).

L’accès d’accessibilité est nécessaire pour identifier l’écran actuellement
affiché et déclencher l’action Android **Retour**. Il ne sert pas à lire,
enregistrer ou transmettre les messages, recherches ou vidéos de
l’utilisateur.

## Architecture

```text
mobile/android/
├── app/src/main/java/com/antiscroll/mobile/
│   ├── MainActivity.kt
│   ├── accessibility/
│   │   ├── ShortFormBlockerService.kt
│   │   └── UiTreeReader.kt
│   ├── blocking/
│   │   ├── BlockCoordinator.kt
│   │   └── BlockOverlayController.kt
│   ├── data/
│   │   ├── SettingsRepository.kt
│   │   ├── PunitiveLockManager.kt
│   │   └── InstagramSessionLimitManager.kt
│   ├── detection/
│   │   ├── YouTubeShortsDetector.kt
│   │   ├── InstagramReelsDetector.kt
│   │   ├── InstagramSessionSurfaceClassifier.kt
│   │   └── ShortFormDetectionEngine.kt
│   └── ui/theme/
└── app/src/test/
```

Le service ne reçoit que les événements des paquets
`com.google.android.youtube` et `com.instagram.android`. Il transforme une
copie limitée de l’arbre d’accessibilité en modèle mémoire, puis exécute les
détecteurs propres à chaque plateforme. Cette copie est immédiatement
abandonnée après l’analyse et n’est jamais écrite sur le disque.

Lorsque la limite de session Instagram est active, l’application conserve
uniquement des compteurs locaux : durée de la session en cours, nombre de
dépassements du jour et fin d’un éventuel blocage. L’accueil et Explorer sont
comptés. Les messages, les profils et les pauses hors d’Instagram suspendent
le minuteur sans effacer le temps consommé, même après une longue absence.
Après la première pénalité, une nouvelle tranche de cinq minutes est disponible.
Le temps consommé et les dépassements sont remis à zéro au changement de jour.

La classification dépend de l’interface d’accessibilité exposée par Instagram.
Par sécurité contre les contournements, un écran Instagram inconnu est compté
comme du fil jusqu’à ce qu’il soit reconnu comme un écran de messages ou de
profil.

## Détection et faux positifs

Les détecteurs utilisent en priorité :

- les identifiants de vues propres aux lecteurs Shorts ou Reels ;
- la classe de la racine de l’écran actuel lorsqu’elle est suffisamment explicite ;
- l’état sélectionné de l’onglet Shorts ou Reels ;
- des descriptions explicites du lecteur.

La simple présence d’un bouton « Shorts » ou « Reels » non sélectionné n’est
pas suffisante. Cela évite de bloquer l’accueil YouTube ou le fil Instagram.

YouTube et Instagram peuvent modifier leur interface sans préavis. Les règles
de détection sont donc isolées et testées, mais elles devront parfois être
adaptées après une mise à jour de ces applications. Les tests finaux doivent
être réalisés sur un véritable téléphone avec les versions réellement
installées.

## Confidentialité

- aucune permission `INTERNET` ;
- aucune télémétrie ;
- aucun compte ;
- aucun contenu d’interface conservé ;
- réglages et compteurs de session enregistrés uniquement dans les préférences
  privées Android.

Avant une éventuelle publication sur Google Play, l’utilisation du service
d’accessibilité devra être déclarée dans Play Console et documentée dans la
fiche de l’application.
