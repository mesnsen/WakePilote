# Dépendances et modèle embarqué

Le modèle MediaPipe Face Landmarker est téléchargé depuis Google, sans modification :

- URL : https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
- Version du bundle : float16 / 1.
- Taille : 3 758 596 octets.
- SHA-256 : `64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff`.
- Licence : Apache License 2.0, voir [texte](Apache-2.0.txt) et cartes officielles ci-dessous.
- Auteurs : Google / The MediaPipe Authors. Les droits des composants tiers sont conservés.

Cartes de modèle : [Face Mesh V2](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MediaPipe%20Face%20Mesh%20V2.pdf), [Blendshape V2](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20Blendshape%20V2.pdf). Le bundle inclut également le détecteur de visage et les métadonnées géométriques associés, distribués par MediaPipe.

La carte Face Mesh indique que le modèle n'est pas destiné aux décisions critiques pour la vie humaine. WakePilote 0.1.0 reste un prototype expérimental à tester à l'arrêt ; il ne constitue pas un dispositif de sécurité validé et ne doit pas servir à décider qu'une personne peut continuer à conduire.

Bibliothèques : Kotlin, coroutines, AndroidX, MediaPipe et Gradle sont utilisées selon leurs licences respectives, notamment Apache 2.0. Les distributions conservent leurs notices embarquées. Le texte Apache fourni ici ne définit pas une licence générale pour le code original du projet.

Les sons sont synthétisés localement par le code de WakePilote ; aucun extrait sonore tiers n'est redistribué. Aucun service audio ou modèle ne nécessite un téléchargement pendant la session.

## Modification de MediaPipe Tasks Core — 10 octobre 2026

WakePilote modifie l'AAR `com.google.mediapipe:tasks-core:0.10.26.1` (Apache 2.0 ; The MediaPipe Authors), dont le SHA-256 original est `f4bcadaab6ae07e2ac2b4710da9f0b193603990f06832a8aefd81215b791981b`.

La transformation reproductible `buildSrc/src/main/java/wakepilote/gradlelogic/OfflineMediaPipe.java` remplace les deux références de `TasksStatsProtoLogger` par `TasksStatsDummyLogger` dans la fabrique de journalisation. Elle supprime `TasksStatsProtoLogger` et `RemoteLoggingClient`. Les bibliothèques natives et le modèle ne sont pas modifiés. Les dépendances Google DataTransport sont exclues. Tout changement de l'AAR ou de sa structure interrompt la compilation et impose une revue de cette modification.

Ce changement empêche la collecte et la mise en file de métriques d'usage par cette partie de MediaPipe ; la fabrique retourne son implémentation officielle sans effet. La suppression des permissions réseau constitue une protection supplémentaire. Le test instrumenté contrôle la fabrique et l'absence des classes de télémétrie.

Références : [avis de confidentialité MediaPipe](https://github.com/google-ai-edge/mediapipe#privacy-notice), [TasksStatsDummyLogger](https://developers.google.com/edge/api/mediapipe/java/com/google/mediapipe/tasks/core/logging/TasksStatsDummyLogger).
