# Anti Scroll Android v0.3.3

Version bêta pour Android 8.0 ou supérieur.

## Corrections

- La remise à zéro des compteurs Instagram suit le fuseau horaire actuel du téléphone, même si celui-ci change pendant que le service d’accessibilité est actif.
- Un blocage jusqu’à minuit s’adapte au prochain minuit local après un changement de fuseau.
- La première pénalité de trente minutes n’est pas prolongée par un changement de fuseau.
- Le calcul de minuit prend en compte les passages à l’heure d’été et à l’heure d’hiver.

## Documentation

Les README français et anglais présentent désormais des captures de l’application : réglages, activation de la protection, temps Instagram restant et première pénalité. Les captures utilisent des compteurs de démonstration.

## Installation

Télécharger `anti-scroll-android-0.3.3.apk` et l’installer. L’APK utilise la même clé de signature que la version 0.3.2 pour permettre une mise à jour sans désinstallation.

Activer le service d’accessibilité Anti Scroll. Si Android bloque cette activation après une installation manuelle, ouvrir les informations de l’application et autoriser les paramètres restreints.

## Vérification

- Tests unitaires Android et Android Lint dans le workflow de publication.
- Vérification de la signature de l’APK et de sa compatibilité avec la version précédente.
- Empreinte SHA-256 disponible dans `SHA256SUMS.txt`.

[Changelog complet](https://github.com/Razziat/no_more_scroling/compare/android-v0.3.2...android-v0.3.3)
