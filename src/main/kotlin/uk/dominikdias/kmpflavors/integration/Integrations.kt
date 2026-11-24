package uk.dominikdias.kmpflavors.integration

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import uk.dominikdias.kmpflavors.KmpFlavorsExtension
import uk.dominikdias.kmpflavors.KmpFlavorsSettingsExtension
import uk.dominikdias.kmpflavors.internal.ProjectIntegration

// Entry points loaded through IntegrationClassLoader; see KmpFlavorsPlugin.

internal class KotlinMultiplatformIntegration : ProjectIntegration {
    override fun apply(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
        extension: KmpFlavorsExtension,
    ) = KotlinWiring.wire(project, settings, extension, buildConfigSourceSet = "commonMain")
}

internal class KotlinJvmIntegration : ProjectIntegration {
    override fun apply(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
        extension: KmpFlavorsExtension,
    ) = KotlinWiring.wire(project, settings, extension, buildConfigSourceSet = "main")
}

internal class ComposeResourcesIntegration : ProjectIntegration {
    override fun apply(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
        extension: KmpFlavorsExtension,
    ) = ComposeResourcesWiring.wire(project, project.extensions.getByType(KotlinProjectExtension::class.java), settings.variant)
}

internal class AndroidApplicationIntegration : ProjectIntegration {
    override fun apply(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
        extension: KmpFlavorsExtension,
    ) = AndroidMirror.application(project, settings)
}

internal class AndroidLibraryIntegration : ProjectIntegration {
    override fun apply(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
        extension: KmpFlavorsExtension,
    ) = AndroidMirror.library(project, settings)
}

internal class AndroidKotlinMultiplatformLibraryIntegration : ProjectIntegration {
    override fun apply(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
        extension: KmpFlavorsExtension,
    ) = AndroidMirror.kotlinMultiplatformLibrary(project, settings)
}
