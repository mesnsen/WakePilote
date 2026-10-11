# Bilan de la version 0.1.0

Développement autorisé le 10 octobre 2026. Cette page décrit le code du prototype ; les documents 01 à 08 conservent les propositions de conception initiales.

## Composants implémentés

| Composant | Contenu réel | Limite actuelle |
| --- | --- | --- |
| core | Sept états, horloge monotone fournie en entrée, fermetures bilatérales, couverture temporelle, PERCLOS estimé, score, arrêt/réarmement, calibration | Règles et seuils expérimentaux ; aucun taux de détection réel établi |
| vision | CameraX, Face Landmarker, yeux/blendshapes, géométrie bouche, pose, lumière/cadrage, rejet d'ambiguïté et résultats périmés | Les heuristiques ne prouvent pas la visibilité réelle derrière des lunettes ; pose à valider |
| audio | AudioTrack, sons synthétisés locaux, focus, trois préférences, route observée pendant lecture, repli demandé, vibration | Ne mesure pas l'audibilité de l'autoradio ; dépend des politiques Android et du matériel |
| data | DataStore pour réglages, calibration scalaire et marqueur de session | Aucun historique de trajet ou enregistrement d'image |
| app | Compose, ViewModel, onboarding, aperçu, calibration, alertes, assombrissement, diagnostics, service et notifications | Coexistence GPS/écran verrouillé non qualifiée tant que les essais matériels ne sont pas faits |
| CI | Tests JVM, build APK, lint, contrôle ELF/modèle, tests instrumentés et artefacts | Les émulateurs ne valident pas la caméra et l'audio d'un véhicule |

## Ajustements de conception explicités

- L'inférence est sérialisée sur l'exécuteur d'analyse CameraX avec MediaPipe en mode `VIDEO`, alimenté par les temps monotones des images reçues. Ce choix évite les difficultés de durée de vie de buffers asynchrones du mode LIVE_STREAM. La file CameraX supprime les anciennes images ; aucune série vidéo n'est enregistrée.
- Deux visages sont autorisés dans le détecteur pour repérer l'ambiguïté. Si deux visages sont présents, la mesure est déclarée indisponible. La V1 ne tente pas d'identifier le conducteur parmi plusieurs occupants. Le suivi géométrique n'est pas une reconnaissance d'identité.
- Les limites de pose expérimentales sont ±35° en rotation horizontale et ±30° en inclinaison, à ajuster sur données. Elles ne prouvent pas qu'un œil est observable.
- Les sons sont synthétisés en PCM dans le lecteur, sans dépendance à des fichiers sonores tiers. Une lecture interrompue ne signifie pas que le risque a disparu.
- L'aperçu est masqué au démarrage de la surveillance pour limiter la distraction ; il reste activable. L'assombrissement concerne la fenêtre, pas une garantie de maintien après verrouillage système.
- En cas de chaleur sévère remontée par Android, la caméra est détachée et la surveillance déclarée indisponible. À chaleur modérée, la cadence cible passe de 15 à 10 analyses/s. Aucune performance énergétique n'est revendiquée.
- La version fournit des chaînes françaises ; une localisation complète des messages internes et diagnostics reste une amélioration ultérieure.
- L'APK est limité à ARM64. La dépendance MediaPipe choisie ne fournit pas de binaire x86_64 : inclure des bibliothèques x86_64 partielles provoquait l'échec du chargement natif sur émulateur. La CI utilise l'image Google APIs Android 15 et sa traduction ARM.

## Versions figées

| Outil / dépendance | Version |
| --- | --- |
| JDK | 17 |
| Gradle | 8.11.1, distribution vérifiée par SHA-256 |
| AGP | 8.10.1 |
| Kotlin / plugin Compose | 2.0.21 |
| Compose BOM | 2025.04.01 |
| CameraX | 1.4.2 |
| MediaPipe Tasks Vision | 0.10.26.1 |
| DataStore | 1.1.4 |
| Lifecycle | 2.8.7 |
| Coroutines | 1.9.0 |
| Modèle | Face Landmarker float16/1 ; hash dans le build et les notices |

Versions stables choisies pour un ensemble reproductible, sans prétendre qu'il s'agit des versions les plus récentes. La compatibilité native est contrôlée sur l'APK final.

## Confidentialité du prototype

L'application ne demande ni Internet, ni localisation, ni microphone. Les images et landmarks complets restent dans la chaîne de traitement en mémoire, puis sont libérés. Les données scalaires de calibration sont conservées localement et effaçables depuis les paramètres. Les sauvegardes cloud et transferts automatiques de données applicatives sont désactivés par le manifeste et les règles d'extraction.

Le marqueur de session permet de signaler une interruption au prochain lancement ; un processus tué ne peut pas prévenir en temps réel. La télémétrie du SDK MediaPipe amont est retirée par une transformation documentée et vérifiée par hash : fabrique remplacée par le logger officiel sans effet, classes d'envoi supprimées et DataTransport exclu. Le manifeste interdit les permissions réseau et la CI inspecte l'APK final. Voir les [notices de modification](../third_party/NOTICES.md). La [notice de confidentialité](10_CONFIDENTIALITE.md) décrit cette version et devra être complétée par les coordonnées de l'éditeur avant une publication en magasin.

## Qualification et limites

Les résultats effectivement exécutés sont consignés dans [VALIDATION_RESULTS.md](VALIDATION_RESULTS.md). Tous les autres scénarios du plan initial restent des tâches à exécuter, pas des succès implicites.

À réaliser sur téléphone réel : cadrage/landmarks sur visage consenti, paramètres temporels, GPS au premier plan, verrouillage selon constructeur, casque/voiture A2DP, liaison appels seulement, appel entrant, déconnexion, volume nul et mode Ne pas déranger, essai prolongé et chauffe. Aucun véhicule n'est contrôlé par le code.

La carte de modèle Google précise que Face Mesh n'est pas destiné aux décisions critiques pour la vie humaine. Ce prototype ne constitue pas un dispositif de sécurité automobile validé et ne remplace pas une pause. Voir [notices et licences](../third_party/NOTICES.md).
