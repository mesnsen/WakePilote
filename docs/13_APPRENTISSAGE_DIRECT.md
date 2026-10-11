# Version 0.1.3 — apprentissage direct

## Utilisation

1. À l'arrêt, ouvrir **Apprentissage** et choisir la **Détection à apprendre** dans la liste déroulante : yeux ouverts, yeux fermés, clignement, bâillement, basculement de la tête vers le bas ou parler / sourire / autre.
2. Activer la caméra. L'aperçu affiche les points du visage et conserve le zoom de cadrage. Attendre l'indication « Prêt ».
3. Maintenir la position ou effectuer le mouvement, puis toucher immédiatement l'un des deux boutons :
   - **Exemple correct** : le mouvement correspond à la détection choisie.
   - **Contre-exemple** : ce mouvement ne doit pas être reconnu comme la détection choisie.
4. L'exemple est sauvegardé directement, avec une vibration si elle est activée. Refaire un mouvement pour ajouter un nouvel exemple. Il n'y a plus de compte à rebours, de proposition à confirmer ni d'écran de correction.
5. **Exporter mes exemples (JSON)** permet de choisir une destination Android. Joindre ensuite ce fichier dans la conversation pour analyser les erreurs et préparer une amélioration de l'algorithme.

Exemple : sélectionner « Bâillement », simuler un bâillement et toucher « Exemple correct ». Puis parler ou sourire et toucher « Contre-exemple ». Ce dernier exemple signifie seulement « ce n'est pas un bâillement » ; il ne reçoit pas automatiquement une autre classe.

Pour la tête : baisser puis relever la tête dans les trois secondes précédant l'appui. Garder la tête stable ou regarder sur le côté donne des contre-exemples. Pour les yeux fermés, garder la position jusqu'à l'appui pour limiter les transitions dans la séquence.

## Mesures sauvegardées

Seules les mesures numériques sont gardées temporairement en mémoire pendant ce mode : jusqu'à trois secondes, une mesure toutes les 50 ms au plus, 61 mesures maximum. **Aucune photo, vidéo ni piste audio n'est enregistrée.** L'appui sauvegarde la fenêtre précédente avec la détection sélectionnée et le choix humain. Les mesures continuent à porter leur qualité réelle, même en cas de pose difficile ; une mauvaise mesure n'est jamais transformée en preuve de surveillance.

Les boutons demandent au moins 500 ms de mesures et des points faciaux récents (500 ms maximum). Ils deviennent indisponibles si le flux se périme. Après un appui, un changement de détection, une sortie du mode ou un arrêt caméra, la fenêtre temporaire est vidée pour éviter de réutiliser le mouvement précédent. Un appui pendant la sauvegarde est ignoré. Les exemples déjà sauvegardés restent disponibles après fermeture.

La limite reste de 50 exemples et de 12 Mio. Les cinq derniers peuvent être supprimés individuellement ; la suppression de l'ensemble demande confirmation. Les fichiers déjà exportés sont indépendants. Ouvrir le sélecteur d'export arrête la caméra de préparation : la réactiver pour continuer.

## Export JSON et compatibilité

Le format passe à `schemaVersion=2`. Les anciens fichiers internes v1 sont lus à leur emplacement d'origine et réécrits atomiquement en v2 lors de la prochaine modification. Les annotations historiques restent distinctes ; aucune ancienne correction n'est convertie en contre-exemple.

| Champ | Sens |
| --- | --- |
| `annotationMode` | `TARGET_EXAMPLE` pour les nouveaux appuis, `LEGACY_REVIEW` pour les anciens essais |
| `example.target` | Détection choisie, dont `HEAD_NOD` pour le mouvement vers le bas |
| `example.matchesTarget` | `true` pour « Exemple correct », `false` pour « Contre-exemple » |
| `example.scope` | `WINDOW_BEFORE_TAP` : annotation globale de la fenêtre précédant l'appui |
| `actual`, `decision` | `null` pour un nouvel exemple ; sens historique conservé pour les exemples v1 |
| `expected` | Cible choisie, conservée pour la compatibilité avec les anciens outils |
| `predicted` | Résumé indicatif des yeux/bouche par les règles existantes ; ne classe pas la tête et n'est pas une vérité terrain |
| `frames[].atMs` | Temps relatif au début de la fenêtre, de 0 à 3 000 ms au plus |
| `frames[].pitchDegrees`, `yawDegrees` | Orientation mesurée pour examiner les mouvements de tête |
| `frames[].features` | Repères normalisés indexés x/y/z et coefficients d'expression ; aucun pixel |

Les autres mesures, la calibration, les qualités, les interruptions et les informations de version/appareil décrites en [0.1.2](12_FLUIDITE_APPRENTISSAGE.md) sont conservées. Les seuils du moteur restent ceux de `wake-rules-0.1.2` ; la version de l'application exportatrice est indiquée séparément. Les exemples positifs/négatifs annotent une **fenêtre**, pas chaque mesure individuellement : les transitions doivent être examinées avant d'ajuster des seuils.

Ce mode collecte les exemples choisis par l'utilisateur. Il ne réentraîne pas automatiquement MediaPipe et ne change pas les seuils d'alerte sur le téléphone. Aucun envoi réseau n'est ajouté. Le fichier doit être partagé explicitement pour permettre une analyse ultérieure.

## Vérification

Les tests couvrent la sélection dans la liste, les deux appuis directs, les contre-exemples associés à leur cible, le maintien de l'historique de tête, la péremption du flux, la limite mémoire, les sorties/changements de cible, les doubles appuis, la persistance/export et la migration v1. Les résultats exécutés et l'APK sont référencés dans [VALIDATION_RESULTS.md](VALIDATION_RESULTS.md).

La précision, la fluidité et l'ergonomie des gestes restent à essayer sur le téléphone réel, à l'arrêt.
