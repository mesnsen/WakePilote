# Sources consultées

Vérification documentaire réalisée le **10 octobre 2026**. Documentation Android/Google et rapport public de recherche primaire. Les politiques de distribution et les versions devront être revérifiées lors de l'implémentation et de la publication.

Les descriptions d'API ci-dessous sont des faits documentaires. Les seuils, scores, choix d'architecture, délais et critères d'essai du dossier sont des propositions propres à WakePilote ; ils ne sont pas validés par ces sources.

| ID | Source primaire | Usage dans l'étude |
| --- | --- | --- |
| S1 | [Google Play — Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en) | Exigence API 36 pour nouvelles applications et mises à jour mobiles depuis le 31 août 2026 |
| S2 | [Android — Restrictions on starting a foreground service from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) | Restrictions de démarrage et permissions pendant l'utilisation |
| S3 | [Android — Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) | Type camera, permission dédiée et usage en arrière-plan |
| S4 | [Android — Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission) | Permission à partir d'API 33 et différence entre FGS et notification visible |
| S5 | [Google AI Edge — Face Landmarker Android](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker/android) | Flux vidéo, observations, blendshapes et intégration Android |
| S6 | [Google AI Edge — Face Landmarker overview](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker) | Nature des sorties et du modèle ; aucun diagnostic de fatigue fourni |
| S7 | [Google AI Edge — Setup guide for Android](https://developers.google.com/edge/mediapipe/solutions/setup_android) | Prérequis Android, modèle local et CPU/GPU |
| S8 | [ML Kit — Detect faces on Android](https://developers.google.com/ml-kit/vision/face-detection/android) | Variante embarquée contre téléchargement Play Services, possibilités d'intégration |
| S9 | [ML Kit — Face detection concepts](https://developers.google.com/ml-kit/vision/face-detection/face-detection-concepts) | Limites de classification des yeux selon la pose et types de sorties |
| S10 | [Android — Manage audio focus](https://developer.android.com/media/optimize/audio-focus) | Focus, restrictions API 35+, appels et pertes de focus |
| S11 | [Android — AudioRouting](https://developer.android.com/reference/android/media/AudioRouting) | Préférence distincte de route effective ; observation valide pendant lecture |
| S12 | [Android — Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes) | Validation des dépendances natives et des APK sur environnement 16 Ko |
| S13 | [Android — Handle user-initiated stopping of foreground services](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping) | Arrêt complet du processus et absence de callback lors de l'arrêt utilisateur décrit |
| S14 | [Android — Thermal API](https://developer.android.com/games/optimize/adpf/thermal) | Adaptation à l'état thermique et limites des informations matérielles |
| S15 | [Google Play — User Data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en) | Information sur les données, permissions sensibles et politique de confidentialité |
| S16 | [Google Play — Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en-GB) | Déclarations à établir selon le comportement final de l'application et de ses SDK |
| S17 | [NHTSA — Drowsy Driver Warning System for Heavy-Vehicle Drivers](https://www.nhtsa.gov/document/drowsy-driver-warning-system-heavy-vehicle-drivers) | Définition de PERCLOS ; aucune transposition de performance à la caméra de WakePilote |
| S18 | [Android — CameraX architecture](https://developer.android.com/media/camera/camerax/architecture) | Gestion explicite d'un cycle de vie personnalisé |
| S19 | [Android — CameraX image analysis](https://developer.android.com/media/camera/camerax/analyze) | Stratégie d'analyse KEEP_ONLY_LATEST |
| S20 | [Google Play — Foreground service and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en-en) | Déclarations de service à préparer avant diffusion Play |

## Ce que les sources ne prouvent pas

Elles ne prouvent ni qu'un téléphone donné garde la caméra active écran verrouillé, ni qu'un autoradio diffuse le flux reçu, ni que les seuils choisis détectent correctement la somnolence. Ces points relèvent des essais définis dans le [plan de validation](06_VALIDATION.md).

Les éléments Google Play sont des exigences de distribution consultées à cette date ; ils ne sont pas nécessaires pour un simple build APK local et ne représentent pas une approbation anticipée de l'application.
