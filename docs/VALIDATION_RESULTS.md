# Résultats de validation du prototype

Validation exécutée le 10 octobre 2026 pour le code du commit `b6efeecb1fa48552563fbfb1bf67bcf37b687994`.

La [CI Android nº 2](https://github.com/mesnsen/WakePilote/actions/runs/38077423176) a terminé avec succès ses deux jobs, `build` et `instrumented`. Les compléments documentaires ultérieurs ne modifient pas ce code.

| Vérification | Résultat |
| --- | --- |
| Moteur Kotlin | 24 tests, zéro échec et zéro erreur ; tâche `:core:test` réussie en CI |
| Compilation Android | APK de l'application et APK de tests construits avec JDK 17 / Gradle 8.11.1 / SDK 36 |
| Lint | Réussi pour `app`, `audio`, `vision` et `data` ; zéro erreur |
| Tests instrumentés | 4 tests réussis sur Android 15 Google APIs ; Wi-Fi et données mobiles désactivés avant les tests |
| Modèle embarqué | SHA-256 vérifié, puis chargement natif et traitement d'une image grise sans visage réussis |
| Confidentialité du paquet | Permissions Internet, état réseau, microphone et localisation absentes ; classes d'envoi de télémétrie et DataTransport absentes |
| Journalisation MediaPipe | La fabrique retourne effectivement `TasksStatsDummyLogger` dans le test Android |
| Architecture native | ARM64 uniquement, cinq bibliothèques natives présentes ; aucune architecture partiellement empaquetée |
| Alignement 16 Ko | Segments ELF contrôlés par script et alignement ZIP vérifié avec `zipalign -c -P 16 4` |
| Signature de l'APK livré | Signature debug v2 vérifiée avec `apksigner` ; aucune clé de production |
| Wrapper Gradle | Hash identique au hash officiel de Gradle 8.11.1 |
| Essais sur téléphone / véhicule | Non exécutés |

## APK de test livré

- Fichier : `WakePilote-0.1.0-debug.apk`.
- Taille : 32 823 122 octets.
- SHA-256 : `71fc8cb319a5a9ca026bc73357e98719f27a70c66eac3099b45677b7025e5c26`.
- Provenance : [artefact de la CI](https://github.com/mesnsen/WakePilote/actions/runs/38077423176/artifacts/11679392792), conservé 30 jours par ce workflow.
- Minimum : Android 8, processeur ARM64 et caméra frontale. Installer et essayer uniquement à l'arrêt.

## Ce que les tests couvrent

Les tests du moteur couvrent les durées de fermeture, les clignements courts, la fermeture d'un seul œil, les horodatages irréguliers, les images périmées ou figées, la perte du visage, le PERCLOS pondéré par le temps et sa couverture minimale, le score combiné, l'arrêt manuel, le réarmement, la réouverture stable, les sessions précédentes et la calibration.

Les quatre tests Android vérifient le premier parcours d'accueil, les permissions finales, la suppression effective de la télémétrie et le chargement du modèle. L'émulateur x86_64 annonce la prise en charge ARM64 par traduction ; ce résultat ne mesure pas les performances d'un téléphone ARM réel.

## Limites et avertissements résiduels

Le lint signale encore des versions plus récentes de dépendances, une suggestion de style Kotlin et la couverture ABI de ChromeOS. Les versions restent figées pour cette livraison et l'application cible les téléphones ARM64. Les actions CI épinglées fonctionnent, mais leur migration vers les versions actuelles reste une tâche de maintenance.

Un précédent échec d'édition de liens de ressources locales après changement de la transformation Gradle a été résolu par une reconstruction propre. En cas de modification de cette transformation, commencer par `./gradlew clean` ; le build CI part également d'un checkout propre.

Restent à exécuter sur matériel : image d'un visage consenti, précision des seuils, latence, caméra pendant GPS et verrouillage, arrêt sonore réel, autoradio Bluetooth, appels, déconnexion, volume nul, mode Ne pas déranger, consommation et chauffe prolongée. Les scénarios figurent dans [06_VALIDATION.md](06_VALIDATION.md). Aucun taux de sécurité, de détection réelle ou de continuité en conduite n'est annoncé.
