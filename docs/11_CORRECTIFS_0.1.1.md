# Correctifs 0.1.1 — analyse, audio et repères du visage

La capture d'un essai réel montrait un aperçu caméra actif, une `IllegalStateException` dans l'analyse et la même classe d'erreur dans le lecteur sonore. Les deux défauts venaient du code ; augmenter la lumière ne pouvait pas les corriger.

## Analyse du visage

Dans MediaPipe 0.10.26.1, fermer `MPImage` recycle le bitmap transmis à `BitmapImageBuilder`. La version 0.1.0 fermait cette image avant de lire les pixels pour vérifier la luminosité du visage. Dès qu'un visage franchissait les premiers contrôles géométriques, `Bitmap.getPixel()` pouvait donc lever une exception.

Le traitement est désormais regroupé dans `FaceFrameProcessor` : inférence, mesures et copie des seuls repères géométriques ont lieu avant la fermeture de l'image. Le bitmap est libéré dans `finally`, y compris en cas d'échec. La caméra ne conserve aucune image pour l'interface.

## Lecture sonore

Un `AudioTrack` en `MODE_STATIC` passe initialement par `STATE_NO_STATIC_DATA`. La version précédente exigeait prématurément `STATE_INITIALIZED`. Le tampon PCM est désormais écrit avant de vérifier cet état, puis la lecture peut démarrer. Les erreurs détaillées restent accessibles dans le panneau de diagnostics.

## Aperçu et états affichés

- Les repères détectés sont dessinés sur le visage ; yeux, iris et bouche sont accentués.
- La conversion utilise les matrices CameraX capteur → tampon et capteur → aperçu. Elle tient compte de la rotation, du cadrage réel, du miroir frontal et des marges de l'aperçu.
- Les repères sont effacés en cas de perte du visage, d'erreur, d'arrêt ou de données âgées de plus de 500 ms.
- Les points sont verts quand les mesures sont exploitables et orange dans les autres cas. Leur présence ne certifie pas la vigilance.
- Une erreur du traitement est distinguée d'une erreur caméra. Le titre signale explicitement un suivi indisponible.

## Régressions contrôlées

Le test initial sur une image sans visage ne lisait pas de pixels après détection et ne pouvait donc pas révéler le premier défaut. Les tests Android ajoutés couvrent maintenant un résultat facial synthétique avec mesure de luminosité, la libération en cas d'erreur, l'effacement des repères, les rotations/cadrages/miroir et la lecture sonore suivie d'un arrêt puis d'un redémarrage.

Les données faciales du test sont entièrement synthétiques ; la capture fournie n'est pas ajoutée au dépôt. Les essais automatisés ne remplacent pas le nouvel essai sur le téléphone concerné.

## Installation

Version Android `0.1.1`, code `2`, ARM64. Les APK de la CI sont signés avec des clés de test propres aux exécutions. Si Android refuse la mise à jour de 0.1.0 pour incompatibilité de signature, désinstaller 0.1.0 puis installer 0.1.1 ; les anciens réglages seront effacés.

Références : [AudioTrack](https://developer.android.com/reference/android/media/AudioTrack), [matrice capteur de PreviewView](https://developer.android.com/reference/androidx/camera/view/PreviewView#getSensorToViewTransform()).
