# Résultats de validation du prototype 0.1.1

Validation exécutée le 10 octobre 2026 pour le code du commit `211881c91467ee0aac25b5272bc91e2973465d8c`.

La [CI Android nº 3](https://github.com/mesnsen/WakePilote/actions/runs/38079434128) a terminé avec succès ses deux jobs, `build` et `instrumented`. Les compléments documentaires ultérieurs ne modifient pas ce code. Les [correctifs 0.1.1](11_CORRECTIFS_0.1.1.md) concernent la durée de vie des pixels analysés, l'initialisation du lecteur sonore et les repères du visage.

| Vérification | Résultat |
| --- | --- |
| Moteur Kotlin | 24 tests, zéro échec et zéro erreur ; tâche `:core:test` réussie en CI |
| Compilation Android | APK de l'application et APK de tests construits avec JDK 17 / Gradle 8.11.1 / SDK 36 |
| Lint | Réussi pour `app`, `audio`, `vision` et `data` ; zéro erreur |
| Tests instrumentés | 9 tests réussis sur Android 15 Google APIs ; Wi-Fi et données mobiles désactivés avant les tests |
| Régression de l'analyse | Mesures sur résultat facial synthétique avant libération du bitmap ; libération aussi en cas d'erreur ; effacement après perte du visage |
| Alignement des points | Rotations 0°, 90°, 180° et 270°, cadrage, décalage de l'aperçu et miroir frontal vérifiés |
| Régression audio | Lecture statique, arrêt, alerte urgente et redémarrage réussis ; l'émulateur ne valide pas l'audibilité réelle |
| Modèle embarqué | SHA-256 vérifié, puis chargement natif et traitement d'une image grise sans visage réussis |
| Confidentialité du paquet | Permissions Internet, état réseau, microphone et localisation absentes ; classes d'envoi de télémétrie et DataTransport absentes |
| Journalisation MediaPipe | La fabrique retourne effectivement `TasksStatsDummyLogger` dans le test Android |
| Architecture native | ARM64 uniquement, cinq bibliothèques natives présentes ; aucune architecture partiellement empaquetée |
| Alignement 16 Ko | Segments ELF contrôlés par script et alignement ZIP vérifié avec `zipalign -c -P 16 4` |
| Signature de l'APK livré | Signature debug v2 vérifiée avec `apksigner` ; aucune clé de production |
| Wrapper Gradle | Hash identique au hash officiel de Gradle 8.11.1 |
| Essais sur téléphone / véhicule | Non exécutés |

## APK de test livré

- Fichier : `WakePilote-0.1.1-debug.apk`, version Android `0.1.1`, code `2`.
- Taille : 32 823 118 octets.
- SHA-256 : `19e0f5380ed62619488caf2e5503f54d55b632ac8581e4ea6e1fa6cb25f75cbd`.
- Provenance : [artefact de la CI](https://github.com/mesnsen/WakePilote/actions/runs/38079434128/artifacts/11679712103), conservé jusqu'au 9 novembre 2026 par ce workflow. Empreinte de l'archive et empreinte de l'APK contrôlées après téléchargement.
- Minimum : Android 8, processeur ARM64 et caméra frontale. Installer et essayer uniquement à l'arrêt.
- La clé de test de cette exécution diffère de celle de 0.1.0. Si Android refuse la mise à jour, désinstaller l'ancienne version puis installer 0.1.1 ; cette opération efface les réglages.

## Ce que les tests couvrent

Les tests du moteur couvrent les durées de fermeture, les clignements courts, la fermeture d'un seul œil, les horodatages irréguliers, les images périmées ou figées, la perte du visage, le PERCLOS pondéré par le temps et sa couverture minimale, le score combiné, l'arrêt manuel, le réarmement, la réouverture stable, les sessions précédentes et la calibration.

Les neuf tests Android comprennent les quatre contrôles précédents (accueil, permissions, absence de télémétrie et chargement du modèle), quatre régressions de la chaîne caméra et un cycle de lecture/arrêt/redémarrage audio. Le résultat facial injecté est synthétique : le chemin de lecture des pixels utilise un vrai bitmap Android et un vrai `MPImage`, mais ce test ne mesure pas la qualité de détection d'un visage réel. La capture fournie lors du signalement n'est pas stockée dans le dépôt.

L'émulateur x86_64 annonce la prise en charge ARM64 par traduction ; ce résultat ne mesure pas les performances d'un téléphone ARM réel. La compilation locale propre, le lint des quatre modules, les tests du moteur et les contrôles du paquet ont également réussi. L'APK livré provient de la CI, et sa signature v2 a été vérifiée après téléchargement.

## Limites et avertissements résiduels

Le lint signale encore des versions plus récentes de dépendances, une suggestion de style Kotlin et la couverture ABI de ChromeOS. Les versions restent figées pour cette livraison et l'application cible les téléphones ARM64. Les actions CI épinglées fonctionnent, mais leur migration vers les versions actuelles reste une tâche de maintenance.

Un précédent échec d'édition de liens de ressources locales après changement de la transformation Gradle a été résolu par une reconstruction propre. En cas de modification de cette transformation, commencer par `./gradlew clean` ; le build CI part également d'un checkout propre.

Restent à exécuter sur matériel : image d'un visage consenti, précision des seuils, latence, caméra pendant GPS et verrouillage, arrêt sonore réel, autoradio Bluetooth, appels, déconnexion, volume nul, mode Ne pas déranger, consommation et chauffe prolongée. Les scénarios figurent dans [06_VALIDATION.md](06_VALIDATION.md). Aucun taux de sécurité, de détection réelle ou de continuité en conduite n'est annoncé.

## Historique 0.1.0

La [CI Android nº 2](https://github.com/mesnsen/WakePilote/actions/runs/38077423176) validait le commit `b6efeecb1fa48552563fbfb1bf67bcf37b687994` avec 24 tests du moteur et 4 tests Android. Le test d'inférence sans visage n'atteignait pas la mesure de luminosité et aucun test n'exerçait encore le lecteur sonore ; les défauts observés sur téléphone n'étaient donc pas couverts.

Ancien APK : `WakePilote-0.1.0-debug.apk`, 32 823 122 octets, SHA-256 `71fc8cb319a5a9ca026bc73357e98719f27a70c66eac3099b45677b7025e5c26`. Il est remplacé par 0.1.1 pour les nouveaux essais.
