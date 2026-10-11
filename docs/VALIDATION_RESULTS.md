# Résultats de validation du prototype 0.1.3

Validation exécutée le 11 octobre 2026 pour le code du commit `90a2eea8ff049be357aa73f9102900d60715295a`.

La [CI Android nº 5](https://github.com/mesnsen/WakePilote/actions/runs/38132909075) a terminé avec succès ses deux jobs, `build` et `instrumented`. Les compléments documentaires ultérieurs ne modifient pas ce code. La version 0.1.3 remplace le parcours de capture/correction par l'[apprentissage direct](13_APPRENTISSAGE_DIRECT.md) : sélection dans une liste, exemple correct ou contre-exemple, aperçu avec les points, sauvegarde immédiate des mesures précédentes et export JSON sans vidéo.

| Vérification | Résultat |
| --- | --- |
| Moteur Kotlin | 35 tests, zéro échec : 24 de surveillance, 6 de classification/compatibilité, 5 de fenêtre temporelle et annotations par cible ; `:core:test` réussi en CI et vérification JVM locale réussie |
| Compilation Android | APK application et APK de tests construits avec JDK 17 / Gradle 8.11.1 / SDK 36 |
| Lint | Réussi pour `app`, `audio`, `vision` et `data` ; zéro erreur |
| Tests instrumentés | 19 tests réussis sur Android 15 Google APIs ; Wi-Fi et données mobiles désactivés avant les tests |
| Apprentissage direct | Deux appuis associés à la cible ; aucune seconde validation ; double appui protégé ; export Android vérifié |
| Mémoire temporaire | Fenêtre limitée à 3 secondes/61 mesures ; mesures périmées refusées ; vidage après appui, changement de cible, sortie ou arrêt caméra |
| Mouvements de tête | Historique d'inclinaison préservé, y compris les mesures rejetées pour pose ; annotation `HEAD_NOD` positive/négative conservée |
| Contre-exemples | Une annotation négative exclut uniquement sa cible ; aucune autre classe réelle n'est inventée |
| Persistance et migration | Réouverture et export v2 réussis ; migration v1 conserve les anciennes corrections ; fichier corrompu préservé et signalé ; suppressions vérifiées |
| Interface | Liste déroulante, choix tête/bâillement, deux boutons directs et désactivation sans mesures récentes vérifiés |
| Régressions caméra | Durée de vie du bitmap, libération sur erreur, mesures brutes et effacement après perte du visage vérifiés |
| Points du visage | Rotations 0°, 90°, 180°, 270°, cadrage, décalage de l'aperçu et miroir frontal vérifiés |
| Audio | Lecture statique, arrêt, alerte urgente et redémarrage réussis ; l'émulateur ne valide pas l'audibilité réelle |
| Modèle embarqué | Hash vérifié ; chargement natif VIDEO avec un visage maximum et trois images grises traitées |
| Confidentialité | Permissions Internet, état réseau, microphone et localisation absentes ; télémétrie/DataTransport absents ; `TasksStatsDummyLogger` vérifié sur Android |
| Architecture et alignement | ARM64 uniquement, cinq bibliothèques ; ELF 16 Ko et ZIP avec `zipalign -c -P 16 4` vérifiés en CI |
| Signature livrée | Signature v2 vérifiée après téléchargement avec `ApkVerifier` ; signature debug |
| Téléphone réel / véhicule | Non exécuté |

## APK de test livré

- Fichier : `WakePilote-0.1.3-debug.apk`, version `0.1.3`, code `4`.
- Taille : 32 905 042 octets.
- SHA-256 : `e1aa304155cea2734dc3c9c8014d2bbf1a2d646ea1b6d79a1e87185705cd3fb9`.
- Provenance : [artefact de la CI](https://github.com/mesnsen/WakePilote/actions/runs/38132909075/artifacts/11699563640), conservé jusqu'au 10 novembre 2026 par le workflow.
- SHA-256 de l'archive : `2edca2d47e9b3ae5e015fc7ea67ca3e7b31a66ab8a0c612ca660b820cc8af223`. Archive et APK contrôlés après téléchargement ; l'APK correspond au fichier validé en CI.
- Minimum : Android 8, processeur ARM64, caméra frontale. Essais à l'arrêt.
- Les clés debug peuvent différer entre exécutions. Si Android refuse la mise à jour, exporter les exemples avant toute désinstallation : désinstaller efface les réglages et exemples internes.

## Portée des tests

Les 19 tests Android comprennent quatre contrôles d'accueil/confidentialité/modèle, cinq régressions caméra, un cycle audio, cinq tests de stockage, trois tests d'interface et un parcours complet de sauvegarde/export. Les observations faciales sont synthétiques ; le bitmap Android, `MPImage`, le chargement natif, le stockage, Compose et le résolveur de fichiers sont exercés réellement. Les tests ne mesurent pas la qualité de détection d'un visage réel. Aucune image ni donnée d'apprentissage de l'utilisateur n'est ajoutée au dépôt.

L'émulateur x86_64 utilise la traduction ARM64. Les mesures de performance ne sont donc pas celles d'un téléphone ARM réel. La compilation Android, le lint et les contrôles SDK ont été exécutés en CI ; les tests JVM et la signature de l'APK téléchargé ont également été vérifiés localement.

Les seuils de surveillance restent inchangés. Le nouveau mode collecte des annotations sur des fenêtres temporelles ; il ne réentraîne pas automatiquement le modèle. Les mouvements transitoires ne deviennent pas des labels individuels de chaque mesure. L'analyse ultérieure doit distinguer transitions et positions stables et réserver des exemples indépendants à la validation.

Restent à essayer sur matériel : confort des boutons avec les yeux fermés, fluidité, précision des points, netteté/zoom, seuils, GPS/verrouillage, audibilité réelle, Bluetooth, interruptions audio, consommation et chauffe. Voir le [plan de validation](06_VALIDATION.md). Aucun taux de sécurité ou de détection réelle n'est annoncé. Les dépendances restent épinglées ; leurs suggestions de mise à jour et les avertissements ABI ChromeOS ne sont pas une qualification pour ces appareils.

## Historique 0.1.2

La [CI Android nº 4](https://github.com/mesnsen/WakePilote/actions/runs/38083154667) validait le commit `bbc747922a2736a5109d59599b055d007eea0288` avec 30 tests JVM et 16 tests Android. Elle ajoutait le suivi lissé, le zoom et le parcours de capture/correction désormais remplacé.

Ancien APK : `WakePilote-0.1.2-debug.apk`, 32 905 038 octets, SHA-256 `cb27593b8ca6f1f7f28dd7f3ab06c2e14671e579cdce10a2c3f2c16ef7a127c8`.

## Historique 0.1.1

La [CI Android nº 3](https://github.com/mesnsen/WakePilote/actions/runs/38079434128) validait le commit `211881c91467ee0aac25b5272bc91e2973465d8c` avec 24 tests du moteur et 9 tests Android. Cette version corrigeait les exceptions d'analyse et d'audio et ajoutait les points du visage.

Ancien APK : `WakePilote-0.1.1-debug.apk`, 32 823 118 octets, SHA-256 `19e0f5380ed62619488caf2e5503f54d55b632ac8581e4ea6e1fa6cb25f75cbd`.

## Historique 0.1.0

La [CI Android nº 2](https://github.com/mesnsen/WakePilote/actions/runs/38077423176) validait le commit `b6efeecb1fa48552563fbfb1bf67bcf37b687994` avec 24 tests du moteur et 4 tests Android. Le test d'inférence sans visage n'atteignait pas la mesure de luminosité et aucun test n'exerçait encore le lecteur sonore ; les défauts observés sur téléphone n'étaient donc pas couverts.

Ancien APK : `WakePilote-0.1.0-debug.apk`, 32 823 122 octets, SHA-256 `71fc8cb319a5a9ca026bc73357e98719f27a70c66eac3099b45677b7025e5c26`. Il est remplacé par 0.1.1 pour les nouveaux essais.
