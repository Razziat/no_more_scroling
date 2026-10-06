# Publier l'application Android sur GitHub Releases

Le dépôt contient un workflow GitHub Actions qui construit, teste et signe
l'APK Android lorsqu'un tag `android-vX.Y.Z` est poussé. Ce préfixe distingue
les versions mobiles des tags de l'extension navigateur. La Release est créée en
**brouillon** : sa publication reste toujours une action manuelle.

## À faire une seule fois

### 1. Créer la clé de signature

Créer la clé dans un dossier privé, en dehors du dépôt :

```powershell
keytool -genkeypair -v `
  -keystore C:\chemin-prive\anti-scroll-release.jks `
  -alias anti-scroll `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000
```

Conserver durablement et séparément :

- le fichier `anti-scroll-release.jks` ;
- le mot de passe du keystore ;
- l'alias de la clé, ici `anti-scroll` ;
- le mot de passe de la clé.

La même clé doit signer toutes les futures mises à jour. Ne jamais placer la
clé ou ses mots de passe dans Git, un ticket, un message ou les notes d'une
Release. Prévoir au moins deux sauvegardes chiffrées dans des emplacements
distincts.

### 2. Convertir la clé en Base64

Dans PowerShell, sans déplacer la clé dans le dépôt :

```powershell
$keyStorePath = 'C:\chemin-prive\anti-scroll-release.jks'
$keyStoreBytes = [System.IO.File]::ReadAllBytes($keyStorePath)
[Convert]::ToBase64String($keyStoreBytes) | Set-Clipboard
```

Base64 est un encodage, pas un chiffrement. Coller immédiatement la valeur dans
le secret GitHub chiffré décrit ci-dessous, puis effacer le presse-papiers :

```powershell
Set-Clipboard -Value 'effacé'
```

### 3. Ajouter les secrets GitHub Actions

Dans le dépôt GitHub : **Settings > Secrets and variables > Actions > New
repository secret**. Créer exactement ces quatre secrets :

| Secret | Valeur |
| --- | --- |
| `ANDROID_RELEASE_KEYSTORE_BASE64` | contenu Base64 copié précédemment |
| `ANDROID_RELEASE_STORE_PASSWORD` | mot de passe du keystore |
| `ANDROID_RELEASE_KEY_ALIAS` | alias, par exemple `anti-scroll` |
| `ANDROID_RELEASE_KEY_PASSWORD` | mot de passe de la clé |

## Publier une nouvelle version

### 1. Préparer et tester la version

Dans `mobile/android/app/build.gradle.kts` :

- définir un `versionName`, par exemple `0.3.1` ;
- augmenter `versionCode` à une valeur strictement supérieure à la précédente ;
- vérifier que le tag prévu est exactement `android-v` suivi du `versionName`.

Faire les tests finaux sur un téléphone réel, notamment :

- ouverture de Shorts depuis l'onglet, l'accueil et un lien direct ;
- ouverture de Reels depuis l'onglet, le fil et un lien direct ;
- mode normal et mode punitif ;
- limite de session Instagram : pause dans les messages et profils, première
  sanction de 30 minutes, seconde sanction jusqu’à minuit ;
- mise à jour par-dessus la précédente version signée, sans désinstallation.

### 2. Créer et pousser le tag

Après avoir fusionné ou validé le commit à publier :

```powershell
git tag -a android-v0.3.1 -m "Anti Scroll Android v0.3.1"
git push origin android-v0.3.1
```

Le workflow `.github/workflows/android-release.yml` effectue alors :

1. la vérification du tag et du `versionName` ;
2. les tests unitaires et Android Lint ;
3. la compilation de l'APK release signé ;
4. la vérification cryptographique de l'APK ;
5. la génération de `SHA256SUMS.txt` ;
6. la création d'une Release GitHub en brouillon.

### 3. Publier le brouillon

Pour relancer uniquement la construction d’une version déjà taguée, ouvrir
**Actions > Android release > Run workflow** et sélectionner son tag
`android-vX.Y.Z`. Le lancement manuel conserve les mêmes vérifications de
version, de tests et de signature. Choisir le tag, pas la branche `main`.

Dans **GitHub > Releases**, ouvrir le brouillon créé automatiquement :

- relire les notes générées ;
- préciser qu'Android 8.0 ou supérieur est requis ;
- expliquer l'activation du service d'accessibilité ;
- marquer la première version comme **pre-release** tant qu'elle est en bêta ;
- vérifier la présence de l'APK et de `SHA256SUMS.txt` ;
- publier la Release.

## Construction locale facultative

La variante release lit uniquement les variables d'environnement suivantes :

```text
ANDROID_RELEASE_STORE_FILE
ANDROID_RELEASE_STORE_PASSWORD
ANDROID_RELEASE_KEY_ALIAS
ANDROID_RELEASE_KEY_PASSWORD
```

Après les avoir définies dans un terminal temporaire, lancer depuis
`mobile/android` :

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache `
  testDebugUnitTest lintRelease assembleRelease
```

Ne pas enregistrer les mots de passe dans un script ou dans
`gradle.properties`. L'APK est généré dans
`app/build/outputs/apk/release/app-release.apk`.

## Vérification développeur Android

Pour une diffusion durable hors Google Play, créer également un compte dans
l'Android Developer Console et enregistrer le package
`com.antiscroll.mobile` avec cette même clé de signature avant le déploiement
mondial de la vérification développeur Android.
