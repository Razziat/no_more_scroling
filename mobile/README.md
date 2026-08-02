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
- Interface française ou anglaise selon la langue Android.
- Aucune permission Internet et aucune donnée transmise.

Une nouvelle tentative pendant une sanction ne prolonge pas les 30 minutes :
elle affiche uniquement le temps restant.

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
3. Activer facultativement le **Mode punitif**.
4. Appuyer sur **Activer la protection**.
5. Lire et accepter la déclaration d’utilisation.
6. Dans les réglages Android, sélectionner **Protection Anti Scroll** et
   activer le service.

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
│   │   └── PunitiveLockManager.kt
│   ├── detection/
│   │   ├── YouTubeShortsDetector.kt
│   │   ├── InstagramReelsDetector.kt
│   │   └── ShortFormDetectionEngine.kt
│   └── ui/theme/
└── app/src/test/
```

Le service ne reçoit que les événements des paquets
`com.google.android.youtube` et `com.instagram.android`. Il transforme une
copie limitée de l’arbre d’accessibilité en modèle mémoire, puis exécute les
détecteurs propres à chaque plateforme. Cette copie est immédiatement
abandonnée après l’analyse et n’est jamais écrite sur le disque.

## Détection et faux positifs

Les détecteurs utilisent en priorité :

- les identifiants de vues propres aux lecteurs Shorts ou Reels ;
- le nom de l’écran Android lorsqu’il est suffisamment explicite ;
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
- réglages enregistrés uniquement dans les préférences privées Android.

Avant une éventuelle publication sur Google Play, l’utilisation du service
d’accessibilité devra être déclarée dans Play Console et documentée dans la
fiche de l’application.
