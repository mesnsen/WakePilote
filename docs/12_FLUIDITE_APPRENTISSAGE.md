# Version 0.1.2 — fluidité, zoom et exemples corrigés

## Cadrer et observer

L'aperçu est plus haut et remplit son cadre. Le curseur va de 1× à 3×, avec un bouton 1× et un bouton « Ajuster au visage ». Ce dernier effectue un ajustement ponctuel : centrer d'abord le visage, puis garder le front et le menton visibles. Le zoom demandé est mémorisé sur le téléphone.

CameraX applique le zoom à la caméra dans la plage prise en charge. Si la caméra frontale atteint sa limite, l'aperçu et ses points sont agrandis ensemble par le complément restant. L'interface indique ce cas : l'agrandissement visuel n'ajoute aucun détail à l'image analysée. Le cadrage n'est pas un mécanisme de reconnaissance d'identité.

## Fluidité

- MediaPipe suit un visage en mode vidéo, avec `numFaces=1`. Cela active son lissage et permet de réutiliser le suivi au lieu de rechercher constamment un deuxième visage.
- Le plafond logiciel passe d'une analyse toutes les 66 ms à une toutes les 33 ms. Le flux conserve seulement l'image la plus récente ; le débit réel dépend du téléphone. Le ralentissement thermique à 100 ms et la suspension en cas de surchauffe sont conservés.
- Activer ou masquer l'aperçu change sa surface sans détacher puis rattacher l'analyse caméra.
- Les points sont publiés séparément des états de l'écran. Les informations générales sont rafraîchies au plus toutes les 100 ms, sauf changement d'état important ; chaque observation continue d'alimenter le moteur.
- La fraîcheur des mesures, les seuils d'alerte et les contrôles de qualité ne sont pas assouplis pour donner une impression de continuité.

Le suivi vise désormais un seul visage. Il ne permet pas de certifier qu'aucun second visage n'entre dans la scène. Les contrôles de cadrage et de changement brusque restent actifs ; une seule personne doit être visible. Aucun débit mesuré sur le téléphone du signalement n'est annoncé avant un nouvel essai.

## Recueillir des exemples

1. À l'arrêt, ouvrir **Apprentissage**, puis **Activer la caméra d'apprentissage**.
2. Régler le zoom et choisir **Yeux ouverts**, **Yeux fermés**, **Clignement**, **Bâillement** ou **Parler / sourire / autre**.
3. Toucher **Enregistrer 3 secondes**. Un compte à rebours de 2 secondes laisse le temps de se placer. Une vibration simple marque le début et une double vibration la fin, si les vibrations sont activées et disponibles.
4. Lire le mouvement proposé. **OK · garder** confirme cette proposition. **Pas OK · corriger** permet de choisir ce qui s'est réellement produit, puis **Garder la correction** sauvegarde l'exemple. Une proposition indéterminée doit être corrigée ou l'essai jeté.
5. Répéter plusieurs fois, y compris avec des erreurs de détection et des mouvements de bouche ordinaires. Il est inutile de provoquer de fatigue : les gestes peuvent être simulés à l'arrêt.
6. Toucher **Exporter mes exemples (JSON)**, choisir une destination Android, puis joindre le fichier à la conversation pour l'analyse.

Les clips confirmés sont conservés après fermeture de l'application. La limite est de 50 clips et 12 Mio de données internes. L'interface permet de supprimer individuellement les cinq derniers exemples ou de tout effacer après confirmation. Les copies déjà exportées restent indépendantes. Quitter pendant le compte à rebours ou l'enregistrement annule uniquement la séquence inachevée. L'ouverture du sélecteur de destination arrête la caméra de préparation ; il faut la réactiver pour continuer les captures.

Ce mode recueille des exemples annotés. Il ne réentraîne pas MediaPipe et ne modifie pas automatiquement les seuils de surveillance. Une analyse ultérieure du fichier permettra de comparer les erreurs, proposer des réglages et vérifier ces changements sur des séquences distinctes.

## Contenu de l'export

Le JSON utilise `schemaVersion=1`, `algorithmVersion=wake-rules-0.1.2` et le hash du modèle embarqué. Il n'inclut aucune photo, vidéo, identité reconnue, localisation, numéro de série ou identifiant de compte. Il contient des mesures personnelles du visage, des horodatages de création, ainsi que le modèle du téléphone et sa version Android pour examiner les performances. Le partage est déclenché par l'utilisateur.

| Champ | Interprétation |
| --- | --- |
| `expected` | Mouvement choisi comme consigne avant l'essai |
| `predicted` | Résumé de la séquence par les règles du mode apprentissage |
| `actual` | Mouvement finalement confirmé ou corrigé par l'utilisateur |
| `decision` | `CONFIRMED` ou `CORRECTED`, conservé séparément de la prédiction |
| `settings` | Références de calibration des yeux, sensibilité et zoom demandé |
| `frames[].atMs` | Temps relatif au début des 3 secondes ; aucune durée n'est inventée dans les trous |
| `quality`, `processingMs`, `cameraZoom` | Qualité de la mesure, durée du traitement et zoom réellement appliqué à la caméra |
| EAR / blink / mouthRatio / jawOpen | Géométrie des yeux et de la bouche et coefficients MediaPipe |
| `pitchDegrees`, `yawDegrees` | Estimations expérimentales de l'orientation |
| `brightness`, `faceWidth`, `faceHeight` | Luminosité et taille normalisée du visage dans l'image analysée |
| `features` | 38 repères indexés x/y/z utiles aux yeux, lèvres, contours, sourcils et iris ; scores d'expression disponibles |
| `goodFrames`, `largestGapMs` | Nombre de mesures de qualité exploitable et plus longue interruption, bords de séquence inclus |

Les coordonnées sont normalisées dans l'image d'analyse redressée, avant miroir d'affichage. Les valeurs absentes ou non finies sont exportées en `null`. Les mauvaises mesures restent utiles et sont conservées avec leur qualité ; une mesure vieille de plus de 500 ms est marquée `STALE`, sans devenir une preuve pour la surveillance. L'enregistrement est limité à une mesure toutes les 50 ms et à 90 mesures maximum par clip. Aucun pixel n'est retenu.

## Vérifications

Les régressions couvrent les séquences ouvertes/fermées, les clignements, les bâillements, les trous temporels, la conservation des mesures rejetées, la différence entre prédiction et annotation, la persistance, le JSON, le parcours OK / Pas OK, le compte à rebours, l'export via le résolveur Android et l'annulation lors d'une sortie. Les résultats exécutés sont consignés dans [VALIDATION_RESULTS.md](VALIDATION_RESULTS.md).

Restent à qualifier sur le téléphone : vitesse réelle, netteté, zoom frontal, maintien des points pendant les mouvements et audibilité/vibration. Les exemples futurs doivent être analysés séparément des séquences utilisées pour choisir les seuils.

Références : [Face Landmarker Android](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker/android), [Preview / SurfaceProvider](https://developer.android.com/reference/androidx/camera/core/Preview), [CameraControl / zoom](https://developer.android.com/reference/androidx/camera/core/CameraControl).
