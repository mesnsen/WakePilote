# Décisions avant le code

Statut : décisions proposées au 10 octobre 2026. Elles ne constituent pas une autorisation de commencer l'implémentation.

## Choix proposés pour validation globale

| Sujet | Proposition | Justification |
| --- | --- | --- |
| Produit | WakePilote, application smartphone Android | Correspond au dépôt demandé et au cahier des charges |
| Identifiant applicatif | `io.github.mesnsen.wakepilote` | Reflète le compte GitHub du projet sans supposer la propriété d'un domaine ; à figer avant toute distribution |
| SDK | minimum 26, compilation/cible 36 | Périmètre raisonnable pour la V1 et exigence Play vérifiée ; voir étude [S1, S7] |
| UI | Kotlin, Compose, MVVM, StateFlow | Séparation de l'interface et de la session |
| Vision | CameraX et MediaPipe Face Landmarker embarqué | Mesures riches pour les yeux, la bouche et la tête |
| Calcul | Moteur Kotlin pur, règles temporelles explicables | Tests sans caméra ni appareil, horloge injectable |
| Modules | core, vision, audio, data, app | Frontières testables sans multiplier inutilement les couches |
| Inférence | CPU au prototype, comparaison GPU ensuite | Point de comparaison reproductible ; choisir l'accélération après mesure |
| Alertes | Son local progressif, vibration optionnelle, arrêt manuel immédiat | Signal identifiable, contrôle simple et indépendant de la vision |
| Audio | AudioTrack ; préférence de périphérique et route observée pendant lecture | Testabilité du lecteur et de la sortie effective |
| Attributs audio | Essayer `USAGE_MEDIA` et `CONTENT_TYPE_SONIFICATION` pour la voie multimédia voiture | Hypothèse d'implémentation à valider sur la matrice ; ne pas choisir un usage pour contourner le système |
| Focus | Gain transitoire pour urgence ; tester réduction du média pour le signal précoce | Respecter les décisions Android et les appels, signaler le refus |
| Arrière-plan | FGS caméra démarré depuis activité visible | Propriétaire explicite de la session |
| Notifications refusées | Mode visible seulement, arrêt de session en quittant/verrouillant | Garder une commande d'arrêt sonore accessible |
| Données | Images en mémoire ; réglages/calibration scalaires locaux | Hors ligne, aucune identité ou trajectoire conservée |
| Livraison | APK de test via GitHub Actions, rapport et hash | Progression vérifiable avant toute publication Play |

## Points à figer au démarrage de la phase 2

Ces éléments demandent de la vérification technique ; ils n'exigent pas de demander à l'utilisateur de choisir chaque détail de bibliothèque.

| Élément | Décision attendue | Vérification |
| --- | --- | --- |
| Versions | Matrice exacte AGP / Gradle / JDK / Kotlin / Compose / CameraX / MediaPipe | Compatibilités officielles, build minimal, versions fixes et hash du wrapper |
| Modèle | Fichier `.task`, URL d'origine, version, SHA-256, taille, conditions de redistribution | Chargement hors ligne et conformité des droits ; pas de fichier manquant remplacé par des données fictives |
| Géométrie | Indices de landmarks, repères d'orientation et méthode de calibration | Séquences annotées ; yeux gauche/droit et miroir corrects |
| Conducteur / passager | Zone conducteur et détection d'ambiguïté | Comparer un ou plusieurs visages, coût et stabilité ; ne pas inventer un suivi d'identité |
| Caméra | Cycle de vie service et transfert depuis l'aperçu | Essai app visible → GPS → retour → fin |
| Audio | Attributs, type(s) de service réellement requis, gestion du focus et des routes | Prototype sur téléphone et véhicule à l'arrêt ; déclaration correspondant à l'usage final |
| Stockage de calibration | Schéma et exclusions de sauvegarde | Aucun transfert involontaire via backup ; suppression vérifiable |

## Informations utiles pour les essais matériels

Le modèle du téléphone principal, sa version Android et le système audio du véhicule permettront de choisir le premier parcours matériel. Ces informations ne bloquent pas la création du projet et des tests unitaires après autorisation ; elles conditionnent toute affirmation de compatibilité réelle.

Par défaut, commencer à l'arrêt, de jour, visage dégagé, puis élargir progressivement la matrice. Lunettes opaques et obscurité ne seront pas annoncées comme prises en charge sans preuve.

## Choix avant diffusion

La licence du projet n'est pas définie par sa visibilité publique. Ne pas ajouter automatiquement une licence de code ou de marque au nom du propriétaire. Recenser séparément les licences du modèle, des SDK et des sons avant leur redistribution.

Avant le premier canal de test destiné à être mis à jour, définir la conservation de la clé de signature. Avant une publication Play, figer identifiant applicatif, identité de l'éditeur, signature de production, politique de confidentialité, déclarations et périmètre effectivement validé. La conception ne réserve pas le nom WakePilote comme marque et n'en vérifie pas la disponibilité commerciale.

## Validation attendue

Valider cette architecture et autoriser le début du développement phase par phase. Sans cette autorisation, le travail reste limité aux documents, conformément au cahier des charges original. Après accord, les ajustements techniques ordinaires seront réalisés et documentés ; un changement substantiel de périmètre ou une publication externe en magasin fera l'objet d'une décision spécifique.

Références : [sources](08_SOURCES.md).
