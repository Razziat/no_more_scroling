# Captures Android du README

Captures prises le 7 octobre 2026 depuis l’application Android compilée
localement, sur un émulateur dédié Pixel 4 / Android 11 (API 30), en thème clair.
Les variantes `-fr` et `-en` utilisent les traductions réelles de l’application.
Les PNG sont des captures de l’interface, sans retouche.

| Fichier | État représenté |
| --- | --- |
| `home-*.png` | Première ouverture : service inactif, Shorts et Reels activés, options de session et de pénalité désactivées. |
| `activation-*.png` | Déclaration affichée par le bouton d’activation. |
| `session-*.png` | Limite Instagram et mode punitif activés ; deux minutes consommées, trois restantes, aucun dépassement. |
| `penalty-*.png` | Premier dépassement de session ; Instagram bloqué pendant trente minutes, compteur à 1/2. |

Les compteurs des deux derniers exemples ont été préparés dans les
préférences de l’émulateur pour illustrer des états reproductibles. Ils ne
proviennent pas d’une utilisation personnelle. Les captures du compteur sont
prises plus bas dans la page, après défilement.

Pour renouveler une capture, ouvrir le même écran sur un émulateur, vérifier
sa langue et attendre que l’interface soit entièrement affichée. Utiliser le
bouton de capture d’Android Studio ou `adb shell screencap -p`, puis récupérer
le PNG avec `adb pull`. Conserver les noms des fichiers pour préserver les
liens relatifs des quatre README.
