# WakePilote

Projet d'application Android de détection de signes de somnolence, avec analyse du visage sur le téléphone et alertes sonores locales.

**État au 10 octobre 2026 : phase 1 — conception rédigée, soumise à validation.**

Le cahier des charges demande une autorisation avant toute écriture du code de l'application. Ce dépôt contient donc la conception et le plan de développement. Il ne contient encore ni application exécutable, ni modèle intégré, ni APK, ni tests exécutés. Les performances et la compatibilité matérielle restent à mesurer.

## Dossier de conception

| Document | Contenu |
| --- | --- |
| [Étude technique et risques](docs/01_ETUDE_ET_RISQUES.md) | Contraintes Android, comparaison MediaPipe / ML Kit, audio voiture, limites et risques prioritaires |
| [Architecture](docs/02_ARCHITECTURE.md) | Modules, responsabilités, cycle de vie, données et gestion des défaillances |
| [Algorithme](docs/03_ALGORITHME.md) | Observabilité, yeux, PERCLOS estimé, bouche, tête, états, règles et alertes |
| [Écrans et parcours](docs/04_UX.md) | Premier lancement, préparation, surveillance, alertes et paramètres |
| [Backlog et phases](docs/05_BACKLOG.md) | Priorités, dépendances et critères d'acceptation des sept phases |
| [Validation](docs/06_VALIDATION.md) | Scénarios, métriques, essais Android/audio et conditions de livraison |
| [Décisions techniques](docs/07_DECISIONS.md) | Choix proposés, incertitudes et éléments à figer avant le code |
| [Sources](docs/08_SOURCES.md) | Documentation officielle consultée le 10 octobre 2026 |
| [Cahier des charges original](docs/CAHIER_DES_CHARGES_ORIGINAL.txt) | Texte fourni, conservé à l'identique |

## Direction proposée

- Kotlin et Jetpack Compose ; MVVM pour les écrans.
- CameraX et MediaPipe Face Landmarker, modèle inclus dans l'application.
- Moteur de décision Kotlin indépendant d'Android et testable avec une horloge simulée.
- Service de premier plan de type caméra, démarré par l'utilisateur depuis l'application visible.
- Moteur audio distinct, test sonore avant le départ et observation de la sortie réellement utilisée pendant la lecture.
- Aucun compte, cloud, publicité en session, enregistrement d'image, reconnaissance d'identité ou accès aux commandes du véhicule.

## Limite du produit

WakePilote doit signaler des signes compatibles avec la somnolence et inviter à s'arrêter en sécurité. Il ne doit jamais certifier la vigilance du conducteur. Une réouverture des yeux peut arrêter le son ; elle ne prouve pas qu'il est possible de continuer à conduire.

Les seuils proposés sont expérimentaux. Les essais se font à l'arrêt, sur simulateur ou sur données consenties ; aucune situation de somnolence ne doit être provoquée au volant.

## Prochaine étape

Après validation de cette conception : créer le projet Android, le moteur testable et le prototype caméra. Vérifier très tôt les deux risques bloquants : continuité de l'analyse avec une application GPS au premier plan et fiabilité observable du chemin audio. Voir les portes de validation dans le [backlog](docs/05_BACKLOG.md).
