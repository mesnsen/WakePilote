# Étude technique et risques

Date : 10 octobre 2026. Statut : proposition de conception ; aucun résultat expérimental disponible.

## Faisabilité et périmètre

Une V1 Android peut analyser le visage localement et émettre un signal sonore. La difficulté principale est de savoir quand les observations et l'alerte sont effectivement disponibles. Les landmarks ne constituent pas un diagnostic ; la caméra visible classique ne résout pas l'obscurité ni les lunettes opaques.

Trois engagements de conception structurent le projet : ne pas assimiler une donnée manquante à des yeux fermés ; ne pas assimiler une sortie audio sélectionnée à un son entendu ; ne pas assimiler un service lancé à une caméra qui fournit encore des données.

Le produit visé est une application pour smartphone, sans application Android Auto ou Android Automotive et sans interface avec les commandes de la voiture.

## Android et versions SDK

| Sujet | Constat documenté | Conséquence proposée |
| --- | --- | --- |
| Publication Play | La page officielle consultée exige API 36 pour les nouvelles applications et mises à jour mobiles depuis le 31 août 2026 [S1] | `compileSdk=36`, `targetSdk=36` comme base ; revérifier à la publication |
| Compatibilité minimale | MediaPipe Tasks annonce Android API 24 minimum [S7] | `minSdk=26` proposé : choix de périmètre pour limiter les anciennes variantes et disposer d'AudioFocusRequest ; ce n'est pas une exigence Play |
| Démarrage caméra | Les permissions « pendant l'utilisation » limitent le démarrage d'un service caméra en arrière-plan [S2, S3] | Démarrer depuis l'activité visible, après accord caméra ; aucun démarrage automatique au boot |
| GPS au premier plan | Le type de service `camera` permet un accès caméra en arrière-plan dans le cadre Android prévu [S3] | Service propriétaire du cycle caméra ; validation sur chaque famille d'appareils |
| Notifications | Sur Android 13+, le refus peut masquer la notification FGS dans le volet, même si le service peut démarrer [S4] | Exiger des notifications utilisables pour le mode arrière-plan ; mode visible seulement sinon |
| Audio focus | À partir de la cible API 35, demande limitée à l'application au premier plan ou exécutant un FGS [S10] | Jouer les alertes depuis la session de surveillance et traiter chaque échec |
| Bibliothèques natives | Les dépendances natives rendent nécessaire la vérification de compatibilité 16 Ko [S12] | Contrôler les binaires MediaPipe et tester l'APK/AAB ; un réglage Gradle seul ne répare pas une bibliothèque incompatible |

La disponibilité d'API 36 ne signifie pas que tous les comportements constructeur sont garantis. L'émulateur servira à l'interface et au cycle de vie ; la caméra, la chauffe et l'audio nécessitent des téléphones réels.

## MediaPipe ou ML Kit ?

| Besoin | MediaPipe Face Landmarker | ML Kit Face Detection |
| --- | --- | --- |
| Géométrie des yeux et de la bouche | Maillage dense, adapté à des mesures géométriques | Landmarks et contours utiles, moins détaillés |
| Expressions | Blendshapes optionnels | Classification yeux ouverts / sourire |
| Orientation | Matrice de transformation optionnelle, à interpréter et valider | Angles d'Euler disponibles selon configuration |
| Hors ligne dès l'installation | Modèle `.task` embarqué à prévoir | Choisir la variante avec modèle embarqué ; éviter la dépendance au téléchargement Play Services |
| Limites documentées | Scores d'expression, pas probabilités de somnolence | Classification yeux ouverts limitée aux visages proches de face, environ −18° à +18° de rotation Y |
| Décision | Choix proposé pour la V1 | Option de comparaison si coût matériel excessif, pas second moteur actif en permanence |

Sources : [S5, S6, S8, S9]. La comparaison décrit les interfaces offertes ; elle ne démontre aucune supériorité mesurée en détection de fatigue. MediaPipe est retenu pour la richesse des observations utiles aux règles demandées. Un test matériel peut remettre en cause ce choix.

Les versions exactes de Kotlin, Compose, AGP, Gradle, CameraX et MediaPipe seront figées au début de la phase 2 après un build minimal. Pas de versions dynamiques. Le modèle devra avoir une origine, une version, un hash SHA-256 et des conditions de redistribution vérifiées ; aucune licence n'est présumée ici.

## Audio voiture : ce que l'application peut vérifier

Android distingue une préférence de sortie et la route effectivement utilisée. La route d'un lecteur s'observe pendant la lecture ; une préférence acceptée n'est pas une garantie de routage [S11].

La conception distingue quatre informations : préférence utilisateur, périphériques multimédias disponibles, route observée pendant le son, confirmation « J'ai entendu le test ». La dernière expire à chaque nouvelle session ou changement de route/volume significatif. Même une route Bluetooth observée ne renseigne pas sur la source sélectionnée ou le volume de l'autoradio.

Le moteur proposera Automatique, Smartphone et Sortie voiture. Une sortie Bluetooth multimédia n'est pas automatiquement une voiture : l'utilisateur choisit le périphérique lors du test. Une liaison réservée aux appels ne sera pas annoncée comme sortie multimédia validée. Aucun forçage SCO, mode téléphonie, volume global ou contournement du mode Ne pas déranger n'est prévu.

Si la route demandée échoue, essayer le haut-parleur local, vérifier le résultat pendant la lecture et afficher le changement. Si aucun chemin n'est vérifiable, signaler « Alerte sonore indisponible » avec vibration/notification si disponibles. Les appels et pertes de focus peuvent interrompre le son ; on ne promet pas de passer au-dessus d'un appel [S10].

## Registre des risques

P0 : bloque le prototype destiné à une évaluation utilisateur. P1 : bloque la stabilisation si non maîtrisé. Probabilités non quantifiées faute de mesures.

| ID | Risque et conséquence | Priorité | Réduction et preuve attendue |
| --- | --- | --- | --- |
| R01 | Caméra suspendue avec GPS, verrouillage ou autre app caméra : absence de surveillance | P0 | Horloge de fraîcheur indépendante ; essais par OS/OEM ; état indisponible sur panne détectable |
| R02 | Bluetooth connecté mais alerte inaudible | P0 | Observer le routage pendant lecture ; test entendu à l'arrêt ; échec explicite et repli vérifié |
| R03 | Faux négatif avec nuit, reflet, lunettes, angle ou morphologie | P0 | Domaine d'observation documenté ; données variées ; inconnues exclues des règles ; cas manqués rapportés |
| R04 | Faux positif avec parole, clignement, rétroviseur ou secousse | P1 | Hystérésis, règles bilatérales, temporalité ; bouche et tête ne suffisent pas seules à l'urgence |
| R05 | Arrêt automatique intempestif ou arrêt manuel inefficace | P0 | Réouverture fiable et continue ; commande audio prioritaire ; tests de course et de notification |
| R06 | Silencieux, volume faible, focus refusé, appel ou route perdue | P0 | Vérification avant session, états audio séparés, signal alternatif ; aucune promesse d'audibilité universelle |
| R07 | Chauffe, batterie, analyse trop lente | P1 | Profilage prolongé ; ajustement de résolution/cadence ; arrêt des conclusions si données trop espacées |
| R08 | Modèle absent, incompatible ou binaire natif non compatible 16 Ko | P0 | Modèle embarqué et hash contrôlé ; installation fraîche hors ligne ; test de chargement et d'ABI |
| R09 | Téléphone tué par le système ou arrêté par l'utilisateur | P0 | Ne pas restaurer une fausse session active ; journal de session non sensible et information au retour ; aucun processus mort ne peut annoncer sa propre panne [S13] |
| R10 | Le passager remplace le conducteur dans le suivi | P0 | Zone conducteur, continuité géométrique, réinitialisation sur changement ; aucune identification biométrique |
| R11 | Interprétation abusive de « récupération » comme sécurité retrouvée | P0 | Message de pause conservé ; aucune jauge « aptitude à conduire », aucun taux de sécurité annoncé |
| R12 | Images ou paramètres faciaux exposés par logs/sauvegardes | P1 | Mémoire seulement pour images/landmarks ; aucun SDK analytique ; calibration locale exclue des sauvegardes ; audit |

## Performance, confidentialité et distribution

Point de départ expérimental : analyse autour de 15 observations par seconde, résolution 640×480 si suffisante, aucune file non bornée. L'aperçu peut être coupé indépendamment de l'analyse. Réduire la charge en cas de chauffe seulement tant que le domaine de détection reste acceptable ; sinon déclarer l'analyse indisponible. Le Thermal API est une information complémentaire, à confronter aux latences réelles [S14].

Aucune permission Internet, microphone ou localisation n'est prévue. Les seuils, préférences et une référence de calibration locale peuvent être conservés ; aucune série de landmarks ni historique de trajet en V1. Les données de test consenties ne seront pas placées dans ce dépôt public.

Avant diffusion, il faudra auditer les SDK, rédiger une politique de confidentialité et remplir les déclarations Play correspondant au comportement réel. Le traitement local ne dispense pas d'expliquer l'usage de la caméra [S15, S16]. Ces documents de conception ne constituent pas une validation réglementaire ou clinique.

Les identifiants [S1] à [S17] renvoient aux [sources](08_SOURCES.md).
