# WakePilote

Projet d'application Android de détection de signes de somnolence, avec analyse du visage sur le téléphone et alertes sonores locales.

**Version 0.1.1 — prototype Android expérimental.** Correctifs de l'analyse et du son, avec points du visage visibles dans l'aperçu : [détail](docs/11_CORRECTIFS_0.1.1.md).

Le dépôt contient maintenant une application native Kotlin/Compose qui embarque le modèle MediaPipe lors de la compilation, un moteur de règles indépendant d'Android, les tests et GitHub Actions. Les documents de conception ci-dessous restent le plan de référence historique. Le [bilan d'implémentation](docs/09_IMPLEMENTATION.md) décrit la version réelle et ses limites ; ne pas confondre une fonctionnalité codée avec une validation sur téléphone ou véhicule.

## Fonctions du prototype

- Caméra frontale avec aperçu optionnel, points du visage, contrôle du cadrage, de la lumière et de la fraîcheur.
- Fermeture prolongée des deux yeux, clignements, PERCLOS **estimé**, indices secondaires de bouche et de tête.
- Avertissement précoce et alerte urgente, arrêt manuel immédiat demandé au lecteur, arrêt automatique après réouverture stable, reprise possible après acquittement.
- Trois préférences audio, observation du routage pendant lecture, test sonore avant départ et vibration.
- Service caméra et notifications avec commandes distinctes pour arrêter le son ou terminer la surveillance.
- Calibration facultative, réglages locaux, interface française, aucun compte ni permission réseau.

L'APK cible Android 8 ou ultérieur, sur architecture **arm64-v8a** uniquement. La CI utilise la traduction ARM de l'image Google APIs Android 15 sur émulateur x86_64 ; MediaPipe 0.10.26.1 ne fournit pas de bibliothèque native x86_64. Une version Android compatible ne garantit pas la disponibilité de la caméra ou de l'audio en arrière-plan sur tous les constructeurs.

## Compiler et vérifier

Prérequis : JDK 17, Android SDK 36 et Build Tools 35.0.0. Définir `ANDROID_HOME` ou un `local.properties` local. Le modèle de 3,8 Mo est téléchargé une fois par le script de préparation, vérifié par hash puis inclus dans l'APK. Il n'est jamais téléchargé par l'application.

```bash
python3 scripts/fetch_model.py
./gradlew :core:test :app:assembleDebug :app:lintDebug :audio:lintDebug :vision:lintDebug :data:lintDebug
python3 scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:connectedDebugAndroidTest
```

La dernière commande nécessite un appareil ou un émulateur. Elle vérifie le chargement natif du modèle, l'analyse d'un résultat facial synthétique, la lecture sonore, les coordonnées des points, les permissions, la suppression de la télémétrie et le premier parcours ; elle ne mesure pas les performances sur un visage réel. Une [modification documentée de MediaPipe](third_party/NOTICES.md) retire la collecte de métriques du SDK amont ; son hash et sa structure sont contrôlés à chaque compilation.

L'APK se trouve dans `app/build/outputs/apk/debug/app-debug.apk`. Le workflow [Android](.github/workflows/android.yml) publie l'artefact `WakePilote-0.1.1-debug` et les rapports. Un APK debug est signé pour les essais ; les signatures locales et CI peuvent différer. Si Android refuse une mise à jour pour incompatibilité de signature, il faut désinstaller la précédente version de test, ce qui efface ses réglages. Aucune signature de production n'est créée ou conservée dans ce dépôt.

## Premier essai

À l'arrêt uniquement : accepter l'explication initiale, autoriser la caméra, vérifier le cadrage, jouer le test sonore, confirmer qu'il est entendu, puis démarrer. Tester le bouton d'arrêt sonore et la fin de surveillance. Une autorisation de notifications refusée impose le mode visible : quitter ou verrouiller l'application termine alors la session.

Les essais GPS, Bluetooth véhicule, appels, verrouillage et chauffe sont à effectuer selon le [plan de validation](docs/06_VALIDATION.md), avant de les annoncer pris en charge sur un modèle de téléphone.

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

## Validation restante

La validation sur téléphone réel, la continuité avec une application GPS et l'audibilité dans un véhicule restent indispensables. Les seuils ne sont pas validés sur un corpus de conducteurs. Voir le [bilan](docs/09_IMPLEMENTATION.md) et les portes de validation dans le [backlog](docs/05_BACKLOG.md). Cette version ne doit pas servir à décider qu'une personne peut continuer à conduire.
