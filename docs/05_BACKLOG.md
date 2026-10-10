# Backlog priorisé et phases

**État initial : conception rédigée. Toutes les tâches d'implémentation et tous les essais ci-dessous restent à réaliser.** L'autorisation demandée dans le cahier des charges est nécessaire avant la phase 2.

P0 : indispensable au fonctionnement maîtrisé ; P1 : nécessaire à une V1 stabilisée ; P2 : amélioration après stabilisation. Les priorités ne déplacent pas automatiquement une phase : un essai réduit d'un risque bloquant peut être réalisé plus tôt que sa finition.

## Backlog

| ID | Priorité | Travail | Phase | Dépendance / preuve attendue |
| --- | --- | --- | --- | --- |
| WP-01 | P0 | Décisions d'architecture et domaine d'usage | 1 | Dossier relu, périmètre validé |
| WP-02 | P0 | Projet multi-module, versions fixes, wrapper et build reproductible | 2 | WP-01 ; build et tests du squelette sur CI |
| WP-03 | P0 | Permissions, caméra frontale et aperçu | 2 | WP-02 ; refus/accord/recréation vérifiés |
| WP-04 | P0 | Modèle embarqué, provenance/hash, adaptateur MediaPipe | 2 | WP-03 ; première installation en mode avion |
| WP-05 | P0 | Observabilité des yeux, timestamps et contrôle de fraîcheur | 2–3 | WP-04 ; gel caméra, visage absent et données tardives testés |
| WP-06 | P0 | Service caméra et essai de continuité avec GPS | Essai 2, finition 6 | WP-03 ; téléphone réel, notification, retour dans l'app |
| WP-07 | P0 | Yeux bilatéraux, hystérésis, fermetures, sept états | 3 | WP-05 ; traces synthétiques et horloge simulée |
| WP-08 | P1 | PERCLOS estimé, couverture, calibration et score combiné | 3 | WP-07 ; fenêtres irrégulières et cas inconnus |
| WP-09 | P1 | Bâillements et hochements comme signaux secondaires | 3 | WP-08 ; parole, sourire, rétroviseurs et bosses |
| WP-10 | P0 | Lecteur local, vibration et priorités d'alertes | 4 | WP-07 ; pas de double lecteur, arrêt et libération |
| WP-11 | P0 | Arrêt manuel, automatique, acquittement et réarmement | 4 | WP-10 ; concurrence, récidive et données périmées |
| WP-12 | P0 | Test audio et route observée | Essai 2, finition 5 | WP-02 ; route pendant lecture et test entendu à l'arrêt |
| WP-13 | P0 | Bluetooth multimédia, repli, focus et interruptions | 5 | WP-12, WP-10 ; matrice téléphone/autoradio/casques |
| WP-14 | P1 | Onboarding, préparation, écrans simples et réglages | 6 | WP-03, WP-11 ; parcours complet sans compte |
| WP-15 | P0 | Actions de notification et refus/désactivation du canal | 6 | WP-06, WP-11 ; mode visible seulement si nécessaire |
| WP-16 | P1 | Assombrissement, aperçu optionnel et adaptation thermique | 6 | WP-05 ; mesure prolongée et indisponibilité explicite |
| WP-17 | P0 | Tests instrumentés, APK de test, traçabilité et bilan | 7 | Phases précédentes ; preuves de la validation ci-dessous |
| WP-18 | P1 | Audit vie privée, licences, manifestes et éléments Play | 7 | Versions finales, formulaire conforme au comportement réel |
| WP-19 | P2 | Traductions, profils matériels et réglages affinés | Après V1 | Mesures disponibles ; aucune promesse non vérifiée |

## Critères d'acceptation des sept phases

| Phase | Livrable | Conditions pour passer à la suite |
| --- | --- | --- |
| 1 — Étude et architecture | Les huit documents de conception et le cahier des charges | Risques prioritaires identifiés ; MediaPipe/ML Kit comparés ; états, UX, validation et décisions proposés ; autorisation de coder obtenue |
| 2 — Prototype caméra | Première application de test avec observations locales | Build propre ; modèle chargeable hors ligne dès installation ; caméra frontale et arrêt fiables ; images libérées ; mesures horodatées ; essai réel GPS/service et essai audio de faisabilité consignés |
| 3 — Moteur de somnolence | Moteur Kotlin indépendant et tests | Scénarios U01–U18 de validation réussis ; aucun temps manquant assimilé à une fermeture/réouverture ; règle urgente disponible sans attendre PERCLOS ; bouche/tête seules sans urgence |
| 4 — Alertes | Deux niveaux, arrêt manuel/automatique et récidive | Arrêt logiciel cible ≤150 ms ; bouton et notification idempotents ; aucune reprise tardive d'ancienne session ; réouverture stable et cooldown vérifiés ; interruption audio clairement signalée |
| 5 — Audio voiture | Trois préférences de sortie et test pré-départ | Matrice audio renseignée avec routes observées et audibilité confirmée à l'arrêt ; déconnexion et focus gérés ; HFP seul non présenté comme multimédia ; aucune garantie déduite d'une simple connexion |
| 6 — UX et surveillance | Parcours complet, service et performance | GPS, verrouillage et permissions testés par appareil ; pas de statut faussement actif après panne détectable ; aperçu indépendant ; paramètres simples ; essai prolongé et chauffe documentés |
| 7 — Stabilisation et livraison | APK installable de test, documentation et rapports | CI verte pour le commit livré ; installation physique hors ligne ; artefact/hash/version traçables ; limites publiées ; contrôle natif 16 Ko, confidentialité et licences ; aucun défaut P0 ouvert pour le périmètre annoncé |

Les objectifs de temps sont des portes d'essai, pas des performances déjà obtenues. Un résultat rouge exige une correction ou un périmètre explicitement réduit avec accord avant de le présenter comme supporté.

## Cadence de développement après accord

1. Une branche de travail et des commits lisibles par phase. Le nom du dépôt et du produit reste WakePilote.
2. À chaque phase : code, compilation, tests pertinents, puis compte rendu des résultats réels et des limites restantes.
3. CI dès la phase 2 : wrapper vérifié, compilation du module Kotlin, tests unitaires et build debug Android. Ajouter lint et tests instrumentés au fur et à mesure ; ne pas attendre la fin pour découvrir un build impossible.
4. Fournir à la livraison de phase 7 un APK de test installable et le rapport associé. La publication Play et la signature de production constituent des travaux séparés, pas une conséquence automatique de la création de l'APK.

## Contenu attendu de GitHub Actions

La configuration n'est pas écrite à ce stade. Elle devra effectuer : vérification du wrapper et des dépendances ; tests JVM ; lint ; assemblage APK ; tests instrumentés ciblés sur émulateur ; publication des rapports et de l'APK en artefacts avec conservation bornée. Les workflows utilisent des permissions minimales, des actions épinglées et n'exposent pas de secrets aux contributions externes.

Le modèle est embarqué avec origine et hash : si son téléchargement n'est réalisé qu'au build, le build vérifie le hash et l'APK final contient bien le fichier ; aucune récupération réseau à la première surveillance. L'APK de test doit conserver une signature permettant les mises à jour du même canal si cet usage est retenu. Une clé de signature n'entre jamais dans le dépôt ; son stockage CI reste à définir avant la livraison.

Les tests physiques ne sont pas remplacés par un badge CI. Les rapports doivent distinguer tests automatiques, vérifications matérielles et tâches non exécutées.

## Critère d'arrêt du projet ou de réduction de périmètre

Si la continuité caméra ou la sortie audio ne peut pas être démontrée pour un couple téléphone/OS, ne pas l'annoncer compatible. Si les mesures visuelles échouent systématiquement la nuit ou avec certaines lunettes, exclure ces conditions et le dire dans l'application. Si les faux négatifs ou fausses alertes empêchent un usage compréhensible, conserver le statut de prototype et retravailler la détection avant toute diffusion élargie.
