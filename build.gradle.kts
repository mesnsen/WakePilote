import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import wakepilote.gradlelogic.OfflineMediaPipe

plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

val offlineMediaPipe = Attribute.of("wakepilote.offline-mediapipe", Boolean::class.javaObjectType)
subprojects {
    dependencies {
        attributesSchema.attribute(offlineMediaPipe)
        artifactTypes.maybeCreate("aar").attributes.attribute(offlineMediaPipe, false)
        registerTransform(OfflineMediaPipe::class.java) {
            from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "aar").attribute(offlineMediaPipe, false)
            to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "aar").attribute(offlineMediaPipe, true)
        }
    }
    configurations.configureEach {
        attributes.attribute(offlineMediaPipe, true)
        exclude(group = "com.google.android.datatransport")
    }
}
