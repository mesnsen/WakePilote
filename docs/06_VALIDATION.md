# Stratégie de validation

**Aucun des tests ci-dessous n'a encore été exécuté.** Cette page définit les preuves à obtenir avant de présenter une fonctionnalité comme opérationnelle. Le dépôt ne contient pour l'instant que la conception.

## Ordre de validation

Commencer par les règles sur traces numériques synthétiques, puis vérifier l'extraction visuelle sur séquences consenties, puis la chaîne complète sur téléphone à l'arrêt. Les essais avec GPS portent sur la coexistence des applications ; ils ne nécessitent pas qu'un conducteur somnole ni même que le véhicule roule.

Une application compilée ou un son entendu une fois ne suffit pas à qualifier le système. Les tests doivent distinguer la détection d'une fermeture observable, l'interprétation expérimentale du risque et l'émission/perception d'une alerte.

## Tests unitaires du moteur Kotlin

Chaque test décrit les observations, leurs timestamps, les ticks éventuels, les commandes et l'état attendu. Utiliser une horloge simulée, sans attendre des secondes réelles. Tester les limites juste avant, exactement au seuil et juste après. Générer plusieurs cadences et séquences de trous.

| ID | Scénario | Résultat attendu |
| --- | --- | --- |
| U01 | Yeux ouverts pendant plusieurs fenêtres | Aucun épisode urgent ; fenêtres bornées |
| U02 | Clignements bilatéraux ordinaires de 100–250 ms | Pas d'alerte urgente ni accumulation de temps fermé entre clignements |
| U03 | Fermeture bilatérale prolongée, statistiques encore froides | Urgence à la durée spécifiée, sans dépendance au PERCLOS |
| U04 | Un œil fermé, autre ouvert | Pas de fermeture bilatérale prioritaire |
| U05 | Deux yeux ouverts de nouveau après urgence | Arrêt automatique après 2 s valides continues, pas avant |
| U06 | Arrêt manuel au milieu d'une urgence | Arrêt demandé immédiatement ; session et observations conservées |
| U07 | Fermeture critique maintenue après arrêt manuel | Reprise possible après le délai de 5 s, jamais sur des données périmées |
| U08 | Aucun visage ou yeux masqués | État inconnu puis indisponible ; aucune fermeture inventée |
| U09 | Trou de suivi ≤250 ms puis même état | Mémoire d'épisode éventuellement conservée ; temps du trou exclu |
| U10 | Trou >250 ms ou caméra figée sur un dernier résultat fermé | Chronomètre de fermeture réinitialisé ; aucune croissance par tick seul |
| U11 | Timestamps irréguliers, doublons et retours arrière | Rejets déterministes ; durées non négatives ; pas de dépendance aux FPS |
| U12 | Un bâillement, sourire ou événement de tête isolé | Pas d'urgence ; pas de double comptage d'un événement continu |
| U13 | Fenêtre PERCLOS tronquée, données inconnues, dénominateur nul | Rapport et couverture corrects ; résultat indisponible si couverture insuffisante |
| U14 | Signaux proches des seuils, conflit EAR/blendshape | Hystérésis bornée ; pas de fermeture prolongée fabriquée |
| U15 | Ancien callback après Terminer puis nouveau Démarrer | Ignoré ; aucun redémarrage sonore depuis l'ancienne session |
| U16 | Urgence puis perte du visage ; réouverture interrompue | Pas d'arrêt automatique pour récupération ; action manuelle reste valable |
| U17 | Score élevé résiduel après arrêt automatique | Pas de boucle immédiate du même épisode ; nouvelle fermeture prioritaire toujours possible |
| U18 | Trois épisodes, changement de profil, nouvelle session | Compteurs sans doublons ; pause recommandée ; paramètres cohérents pour toute la session |

Compléter par des invariants : PERCLOS dans [0,1] lorsqu'il est défini ; temps valide ≤ durée de fenêtre ; état INACTIVE sans demande sonore ; caméra perdue ne devient jamais « yeux ouverts » ; arrêt manuel et fin de session idempotents.

## Extraction visuelle et données consenties

Utiliser d'abord des vidéos jouées ou capturées dans un cadre consenti et immobile. Inclure morphologies variées, lunettes transparentes, reflets, paupières naturellement étroites, éclairages, poses et distances. Des vidéos de fermeture volontaire à l'arrêt servent à vérifier la géométrie et le délai ; elles ne prouvent pas une détection de somnolence réelle.

Annoter séparément : visibilité des yeux, début/fin des fermetures, clignements, bâillements potentiels, mouvements de tête, qualité de lumière et événements hors domaine. Faire relire un sous-ensemble par une deuxième personne et traiter les désaccords. Ne pas utiliser les prédictions du modèle comme vérité terrain.

Séparer les personnes et les sessions entre réglage des seuils et évaluation finale. Une calibration propre au participant reste autorisée si elle reproduit exactement le parcours produit ; ses images ne doivent pas être réutilisées pour ajuster les seuils globaux. Fixer les seuils avant d'exécuter le lot de validation réservé.

Aucune vidéo ou donnée faciale individuelle n'entre dans le dépôt public. Les données externes nécessitent licence et consentement adaptés ; aucune base ne sera téléchargée ou redistribuée par défaut. Les premières traces numériques peuvent être purement synthétiques et ne contenir aucune identité.

## Matrice de tests fonctionnels

| Domaine | Scénarios obligatoires | Preuve attendue |
| --- | --- | --- |
| Hors ligne | Installation fraîche puis premier lancement en mode avion | Modèle chargé, suivi et sons utilisables ; aucun besoin de téléchargement |
| Session | 20 démarrages/arrêts ; rotation ; recréation d'activité | Une seule caméra/session ; ressources libérées ; aucun lecteur résiduel |
| GPS | Google Maps et Waze au premier plan, retour à WakePilote | Mesures réellement reçues et fraîches, actions de notification utilisables |
| Verrouillage | Écran verrouillé, déverrouillage, écran assombri | Résultat consigné par modèle/OS ; aucune continuité simplement supposée |
| Permissions | Caméra refusée/révoquée ; notifications refusées/canal coupé | Message explicite ; pas de crash ; politique de mode visible respectée |
| Caméra occupée | Autre application caméra, erreur capteur, image figée | Indisponibilité détectée ; aucun faux éveil ou faux sommeil |
| Fin système | Arrêt FGS via Android, arrêt forcé, processus tué | Reprise inactive ; pas de relance caméra intempestive |
| Énergie | Batterie faible, économie batterie, charge secteur | Continuité ou limite mesurée ; état exact affiché |
| Chauffe | Session 30 min puis 2 h sur chaque famille ciblée | Cadence, latence, mémoire, CPU, température/état thermique et batterie enregistrés |
| Visage | Lunettes claires/opaques, masque partiel, biais d'angle, passager | Qualité et cas manqués rapportés ; ambiguïtés sans changement silencieux de conducteur |
| Éclairage | Faible lumière, nuit, contre-jour, soleil et transitions | Domaine réel documenté ; absence de promesse de vision nocturne |
| Mouvements | Parole, sourire, chant, rétroviseurs, tableau de bord, secousses simulées | Faux positifs comptabilisés et causes conservées |
| UI | Texte agrandi, TalkBack, petit écran, orientations | Boutons non masqués et arrêt sonore accessible |

La matrice minimale proposée inclut API 26, 33, 34, 35 et 36 dans les vérifications système, avec émulateurs lorsque pertinent. Les mesures matérielles doivent couvrir au moins trois téléphones physiques de deux constructeurs, dont un appareil intermédiaire et un récent. Ajouter les versions Android ultérieures réellement disponibles lors de la phase 2 sans déclarer une compatibilité avant essai.

## Matrice audio

Pour chaque ligne, consigner téléphone/OS, lecteur, périphérique, préférence, route observée pendant lecture, focus, volume, confirmation auditive à l'arrêt et délai de coupure. Aucun accès microphone n'est nécessaire dans l'application ; des instruments externes peuvent servir aux mesures de test.

| Cas | Vérification |
| --- | --- |
| Téléphone seul | Son local, arrêt manuel, vibration activée/désactivée |
| Voiture en Bluetooth multimédia | Test entendu et route observée ; ne pas se limiter à l'état connecté |
| Bluetooth appels seulement | Pas de validation automatique comme sortie voiture multimédia |
| Autoradio sur radio FM ou autre source | Route éventuellement Bluetooth mais absence d'audibilité identifiée par le test humain |
| Casque Bluetooth ou filaire | Ne pas l'étiqueter automatiquement « voiture » ; préférences et replis explicites |
| Déconnexion / reconnexion | Changement détecté, repli tenté et vérifié, statut mis à jour |
| Musique et instructions GPS | Demande de focus, interruptions et reprise conformes, pas de lecteur concurrent |
| Appel entrant/en cours | Son interrompu ou refusé documenté ; pas de contournement de l'appel |
| Silencieux, Ne pas déranger, volume faible ou nul | Comportement réellement constaté, sans changement forcé des réglages |
| Action de notification répétée | Arrêt idempotent, sans arrêt accidentel de la caméra |
| Urgence lors d'un test sonore | Test remplacé par l'urgence ; aucune superposition |

## Mesures et portes d'acceptation

| Mesure | Définition et cible de prototype |
| --- | --- |
| Règles déterministes | 100 % des scénarios U01–U18 et invariants attendus réussis ; cela valide le moteur, pas le modèle visuel |
| Latence visuelle | P50/P95 acquisition→observation ; cible P95 ≤250 ms sur appareils annoncés compatibles |
| Fraîcheur | Aucune conclusion étendue sur résultat périmé ; panne de flux signalée au délai spécifié + un tick lorsque le processus s'exécute |
| Déclenchement | Délai depuis début annoté et depuis seuil observé, rapportés séparément ; cible ajoutée au seuil ≤500 ms pour la fermeture prioritaire sur appareil qualifié |
| Arrêt sonore | Cible commande→arrêt logiciel ≤150 ms ; délai acoustique Bluetooth mesuré séparément, non garanti par cette valeur |
| Mémoire et charge | Pas de file croissante ni crash sur 2 h ; mesurer mémoire/CPU/batterie par appareil, aperçu et chargeur, avant de fixer un budget de distribution |
| Détection visuelle | Rappel des fermetures prolongées annotées, précision, faux urgents par heure observable, cas manqués et couverture ; ventiler par personne, appareil et condition |
| Audio | Taux de lectures démarrées, de routes observées et de tests réellement entendus ; chaque interruption distinguée d'une panne de détection |

Pour toute statistique, fournir effectifs, durées d'essai et incertitudes ; zéro échec sur quelques essais n'est pas « 100 % fiable ». Rapporter les résultats sur tout le corpus et sur la partie observable : une caméra souvent indisponible ne doit pas améliorer artificiellement les chiffres en excluant tous les cas difficiles.

Les cibles terrain de rappel, de faux positifs et d'audibilité doivent être fixées après le pilote exploratoire et **avant** l'évaluation sur le lot réservé. Tant qu'elles ne sont pas fixées et atteintes, la livraison reste un prototype de test à l'arrêt. Aucun taux de détection de somnolence réelle ne découle d'un simple test de fermeture volontaire des yeux.

## Rapport de livraison attendu

Le rapport d'une version contiendra commit, version APK, hash, versions dépendances/modèle, profil de seuils, tests automatiques avec résultats, appareils/OS réellement testés, heures d'essai, limites connues et défauts ouverts. CI ne peut pas valider à elle seule le Bluetooth d'un véhicule, les restrictions OEM ou la perception d'un son.

Avant une diffusion élargie : fermer les défauts P0, documenter les exclusions, vérifier le comportement hors ligne et l'absence d'images sauvegardées, contrôler licences et éléments Play, puis faire relire le périmètre et les allégations du produit. Aucune publication en magasin n'est incluse dans cette phase de conception.
