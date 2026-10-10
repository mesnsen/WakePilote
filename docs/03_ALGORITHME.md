# Algorithme de détection et d'alerte

**Spécification expérimentale, sans implémentation ni validation.** Les nombres ci-dessous sont des points de départ pour les tests. Ils ne sont ni des seuils médicaux ni une preuve de sécurité. Le moteur doit expliquer chaque décision par ses observations et leur qualité.

## 1. Observations exploitables avant tout calcul

Chaque observation possède un temps monotone et des mesures indépendamment valides ou inconnues. Un visage trouvé ne suffit pas à garantir des yeux observables. Vérifier taille du visage, géométrie des paupières, pose, netteté/luminosité indicative et continuité dans la zone conducteur. Des lunettes opaques ou des reflets peuvent tromper ces heuristiques : ce cas doit rester une limite explicitement testée.

Le conducteur est sélectionné par position, jamais par identité. Une zone est définie lors du placement du téléphone ; un déplacement soudain vers un autre visage invalide le suivi et la calibration. Si plusieurs visages rendent l'association ambiguë, déclarer la mesure inconnue. La détection de cette ambiguïté devra être prototypée ; ne pas prétendre qu'une configuration limitée à un seul visage la résout.

Un cadre de pose initial, par exemple ±25° en rotation horizontale et ±20° en inclinaison verticale relativement au placement, sert seulement de domaine d'essai. Une sortie brève de ce cadre suspend la contribution des yeux ; elle ne signifie pas fermeture. Ajuster ce cadre avec les essais de rétroviseur et de tableau de bord.

## 2. Temps, données manquantes et horloge

Toutes les durées utilisent une horloge monotone en millisecondes. Aucun compteur de frames n'est utilisé comme durée. L'horloge des tests est injectable. Les timestamps identiques, inversés, périmés ou issus d'une ancienne session sont rejetés.

Paramètres initiaux proposés : intervalle maximal entre observations exploitables 250 ms ; résultat périmé au-delà de 500 ms ; surveillance indisponible après 1 500 ms sans yeux observables ; tick de contrôle toutes les 100 ms tant que le service s'exécute. Une panne explicite de caméra passe indisponible immédiatement. Le délai d'initialisation sans résultat est borné à 5 s.

Le moteur découpe le temps en segments. Pour deux observations valides et cohérentes espacées de 250 ms au plus, le segment est attribué à leur état commun. Aux changements d'état, l'intervalle est inconnu, par prudence ; la première observation du nouvel état démarre son chronomètre. Une absence de visage, un signal incertain ou un grand intervalle crée du temps inconnu, exclu des dénominateurs de mesure.

Une interruption de suivi de 250 ms au plus peut conserver la mémoire d'un épisode de fermeture, mais son temps inconnu n'est jamais ajouté à la durée fermée. Une interruption plus longue remet à zéro sa durée continue. Une fermeture observée après le retour repart donc d'un nouvel épisode si le trou dépasse cette tolérance. Cette règle réduit des faux positifs au prix de cas manqués possibles, à mesurer.

Les compteurs de réouverture stable sont plus stricts : toute interruption de validité les remet à zéro. Un tick sans image permet de détecter une panne, mais ne rallonge jamais de lui-même une fermeture ou une réouverture. Ainsi un dernier résultat « fermé » suivi d'un gel caméra ne finit pas par devenir une nouvelle alerte de somnolence.

## 3. Yeux : géométrie, calibration et hystérésis

Pour six points ordonnés sur un œil, calculer :

`EAR = (distance(p2,p6) + distance(p3,p5)) / (2 × distance(p1,p4))`.

Les points sont exprimés dans un repère corrigé du rapport largeur/hauteur ; les correspondances de landmarks seront documentées et vérifiées sur le modèle effectivement embarqué. Un dénominateur trop faible rend la mesure invalide. Les deux yeux sont traités séparément.

La calibration facultative dure environ 3 à 5 s, à l'arrêt, avec les yeux naturellement ouverts. Garder la médiane d'échantillons stables, avec au moins 2 s d'observation exploitable. Rejeter les clignements, poses extrêmes et références trop petites. Conserver EAR de référence gauche et droit, version du modèle et du profil de mesure. Une calibration ne certifie pas la qualité du conducteur ; elle améliore seulement une référence géométrique.

Sans calibration, utiliser une référence générique expérimentale EAR = 0,28 par œil et afficher cette limite. Ne pas recalibrer automatiquement sur une fermeture prolongée. La baseline reste figée pendant la session pour éviter que la fatigue ne devienne la nouvelle « normale ».

On définit `r = EAR / EAR_reference`. Les coefficients `eyeBlinkLeft` et `eyeBlinkRight` peuvent renforcer la mesure, sans être interprétés comme des probabilités de sommeil [S5]. Choix initial par œil :

| Observation | Classification proposée |
| --- | --- |
| Géométrie fiable, r ≤ 0,50, coefficient blink ≥ 0,60 s'il est disponible | Fermé |
| Géométrie fiable, r ≥ 0,70, coefficient blink ≤ 0,35 s'il est disponible | Ouvert |
| Deux sources disponibles mais contradictoires | Inconnu ; ne pas prolonger la durée |
| Valeur entre les deux seuils avec sources cohérentes | Conserver brièvement la classification précédente, au maximum 150 ms, puis inconnu |
| Géométrie invalide ou œil masqué | Inconnu, même si le blendshape paraît élevé |

La géométrie seule peut fonctionner si le blendshape manque, avec un indicateur de qualité dégradée à valider. La fermeture prioritaire exige les deux yeux classés fermés. Un clin d'œil isolé ne satisfait pas cette règle.

Durée bilatérale valide : moins de 400 ms correspond à un événement bref ; 400 à 800 ms alimente les clignements longs ; à partir de 800 ms un avertissement précoce peut être demandé ; à partir de 1 500 ms l'urgence est demandée sans attendre PERCLOS, bouche ou tête. Ces catégories sont des règles de prototype, pas une classification physiologique validée.

## 4. PERCLOS estimé et couverture

La littérature décrit PERCLOS comme une proportion de temps avec une fermeture des yeux d'environ 80 % ou plus [S17]. **EAR divisé par une référence n'est pas une mesure physique du pourcentage de fermeture de la pupille.** WakePilote calculera donc un proxy explicitement nommé « PERCLOS estimé » dans les diagnostics, dont la correspondance devra être validée sur annotations indépendantes.

Pour ce proxy, un segment est « fortement fermé » lorsque les deux rapports r sont ≤ 0,20 et que la géométrie est valide. Cela ne remplace pas la règle prioritaire r ≤ 0,50 de la section précédente. Conserver aussi le taux temporel de fermeture bilatérale générale comme mesure distincte ; ne pas l'étiqueter PERCLOS80.

Fenêtre glissante proposée W = 60 s. Les segments sont tronqués aux bornes exactes de la fenêtre :

`PERCLOS_estime = duree_fortement_fermee_valide / duree_yeux_observables`.

`couverture = duree_yeux_observables / duree_ecoulee_dans_la_fenetre`.

Les durées inconnues ne figurent ni au numérateur ni au dénominateur valide. Le résultat n'est utilisable pour une décision combinée qu'après une minute écoulée, au moins 45 s valides et une couverture ≥ 75 %. Avant cela, il est « en constitution » ou « insuffisamment observable », jamais égal à zéro par défaut. Le détecteur de fermeture prolongée reste utilisable dès les premières observations.

Version initiale du proxy : tous les segments fortement fermés sont comptés, y compris les segments brefs. Cette convention doit apparaître dans les rapports ; ne pas mélanger des études excluant certains clignements et ce calcul sans adaptation. Comparer ensuite, sur les mêmes annotations, une variante excluant les fermetures brèves, puis figer une version.

## 5. Bouche et mouvements de tête

Un bâillement potentiel associe une ouverture géométrique de bouche et un coefficient `jawOpen` élevés pendant 1,5 s, puis une fermeture. Point de départ : ouverture verticale/largeur buccale ≥ 0,60 et `jawOpen` ≥ 0,60, retour sous 0,35 pour terminer. Un événement continu n'est compté qu'une fois ; un minimum de 3 s sépare deux débuts. Parole, sourire et chant restent des confusions possibles. Un événement isolé n'a pas de contribution d'urgence.

Un hochement potentiel correspond à une inclinaison verticale relative d'environ 15° suivie d'un retour en 0,5 à 2 s, dans une pose horizontalement compatible avec le suivi. Une rotation vers le rétroviseur, une observation manquante ou un saut de suivi ne compte pas comme hochement. La pose n'est pas une mesure inertielle ; les secousses du téléphone et de la route doivent être testées.

Compter les bâillements potentiels dans 120 s et les hochements dans 60 s. Ces deux signaux n'amplifient le risque que s'ils sont répétés. Une bouche ou une pose non mesurable reste inconnue, sans inventer d'événement.

## 6. Combinaison explicable

Une urgence par fermeture bilatérale ≥ 1 500 ms a priorité absolue sur le score. Pour la voie combinée seulement, lorsque les statistiques oculaires de la minute sont disponibles :

- P : normaliser le PERCLOS estimé de 10 % à 30 % vers [0,1], avec saturation.
- B : nombre de clignements longs (400 à 800 ms) sur 60 s ; B = minimum(1, nombre / 6), sous les mêmes conditions de couverture.
- Y : 0 si moins de deux bâillements potentiels dans 120 s ; 0,5 pour deux ; 1 pour trois ou plus.
- N : 0 si moins de deux hochements potentiels dans 60 s ; 0,5 pour deux ; 1 pour trois ou plus.

`score = 55 × P + 25 × B + 10 × Y + 10 × N`.

Les coefficients sont une proposition de règle testable. Ils ne proviennent pas d'un modèle entraîné ou d'une validation scientifique. Une mesure secondaire absente n'apporte aucune contribution et est marquée absente, sans redistribuer son poids. Le score obtenu est alors partiel ; il ne signifie pas absence de risque. Si P ou B sont indisponibles, désactiver la voie combinée et conserver seulement les règles immédiates sur les yeux.

| Décision | Condition initiale |
| --- | --- |
| Avertissement précoce | Fermeture ≥ 800 ms, ou score ≥ 40 pendant 3 s de données actuelles exploitables |
| Alerte urgente | Fermeture ≥ 1 500 ms, ou score ≥ 70 pendant 2 s avec contribution oculaire 55P + 25B ≥ 50 |
| Réarmement des règles combinées | Score ≤ 25 pendant 10 s observables et aucun nouvel événement critique |

Un bâillement et un mouvement de tête, même répétés, ne peuvent donc pas produire seuls une urgence. Les durées de persistance du score ne traversent pas une perte de qualité. Les motifs restent accessibles en diagnostic ; aucun pourcentage « chance de s'endormir » n'est affiché.

Sensibilité renforcée proposée : fermeture précoce 600 ms, urgence 1 200 ms, avertissement combiné à 35. Les exigences de qualité, de couverture et de réouverture ne sont pas relâchées. Valider ce profil séparément ; ne pas l'activer sans informer du risque de faux positifs.

## 7. États et transitions

Les sept états décrivent la surveillance ; un sous-état sonore décrit indépendamment lecture, acquittement et panne.

| État | Entrée | Sortie principale |
| --- | --- | --- |
| INACTIVE | Aucune session | Démarrage volontaire → INITIALIZING |
| INITIALIZING | Caméra/modèle en préparation | Données fiables durant 1 s → MONITORING ; panne ou délai dépassé → MONITORING_UNAVAILABLE |
| MONITORING | Mesures disponibles sans règle active | Signe précoce → SUSPECTED_DROWSINESS ; critique → ALERTING |
| SUSPECTED_DROWSINESS | Avertissement précoce demandé | Critique → ALERTING ; fin du signe immédiat et score réarmé ou non encore disponible → MONITORING |
| ALERTING | Règle urgente déclenchée | Réouverture fiable 2 s → RECOVERING ; arrêt manuel → son acquitté, surveillance toujours active |
| RECOVERING | Le son a été arrêté après réouverture observée | Nouveau signe critique → ALERTING ; 10 s stables et règle combinée réarmée → MONITORING |
| MONITORING_UNAVAILABLE | Mesure absente, ancienne ou matériel indisponible | Retour fiable 1 s → recalcul de l'état selon les signes présents, pas retour aveugle à MONITORING |

Depuis chaque état actif : « Terminer » mène à INACTIVE ; une perte de qualité prolongée mène à MONITORING_UNAVAILABLE. Même INITIALIZING et RECOVERING permettent une urgence dès que sa durée observée est atteinte. Une alerte déjà demandée est conservée dans le sous-état d'alerte lors d'une indisponibilité ; aucune disparition de visage n'est assimilée à une réouverture.

Après arrêt automatique, l'ancien score issu de la fenêtre ne doit pas relancer immédiatement le même épisode : la voie combinée est verrouillée jusqu'au réarmement. Une nouvelle fermeture prolongée peut toujours relancer. Pendant RECOVERING, le message d'invitation à la pause reste visible, y compris si le score met plus de dix secondes à redescendre.

## 8. Politique sonore et arrêt manuel

L'avertissement précoce est un signal bref et modéré, limité à une fois toutes les 30 s. L'urgence est une séquence répétée avec attaque progressive. Les répétitions d'un même épisode ne gonflent pas le compteur d'épisodes. Après trois épisodes urgents en dix minutes, maintenir le message de pause fortement recommandée ; cette limite n'empêche pas une nouvelle alerte urgente.

« Arrêter l'alerte » coupe immédiatement le son courant et la vibration, sans arrêter l'analyse ni effacer les fenêtres. Le moteur enregistre un acquittement de 5 s. À son expiration, un signe critique encore présent sur des données fraîches peut relancer le son ; l'ancien score seul, sans nouvelles observations valides, ne suffit pas. La durée inconnue ne consomme pas une preuve de fermeture.

L'arrêt automatique exige deux yeux ouverts de manière fiable pendant 2 s continues. Une tête redressée seule, un visage absent ou une mesure contradictoire ne peut pas arrêter automatiquement le son. Le texte après arrêt est « Son arrêté — faites une pause dès que possible en sécurité », jamais « Vous êtes réveillé ».

Si la vision est perdue pendant une urgence, garder la possibilité d'arrêt manuel et afficher l'indisponibilité. Si le focus audio est perdu, arrêter la lecture conformément à Android et marquer le son interrompu. Une reprise de focus ne rejoue que si la session et la demande d'alerte sont encore actives et non acquittées ; elle ne prouve pas la présence d'un risque nouveau.

Le son d'indisponibilité de suivi est distinct, émis à l'entrée puis au plus une fois par minute tant que la panne persiste ; il ne remplace ni ne masque une urgence en cours. Un acquittement utilisateur vaut aussi pour le son d'indisponibilité courant.

## 9. Exemples attendus

| Séquence | Résultat attendu |
| --- | --- |
| Yeux ouverts, clignement de 150 ms, ouverts | Pas d'urgence ; temps réellement observé conservé |
| Fermeture bilatérale fiable de 1,8 s | Précoce puis urgence sans attendre une minute de PERCLOS |
| Dernière mesure fermée puis caméra figée | Pas de croissance artificielle de la fermeture ; indisponibilité à l'échéance |
| Urgence puis visage absent | Pas d'arrêt automatique pour récupération ; arrêt manuel disponible |
| Urgence, arrêt manuel, yeux toujours fermés et visibles | Son arrêté, puis reprise possible après 5 s |
| Urgence puis yeux ouverts durant 2 s | Son arrêté ; message de pause maintenu |
| Une minute avec 20 s de données inconnues | PERCLOS combiné inutilisable car couverture insuffisante |
| Un bâillement isolé, yeux ouverts | Pas d'urgence |

Les tests détaillés et les écarts admissibles figurent dans la [stratégie de validation](06_VALIDATION.md). Sources factuelles : [S5 et S17](08_SOURCES.md). Toutes les autres règles de cette page sont des propositions propres au projet.
