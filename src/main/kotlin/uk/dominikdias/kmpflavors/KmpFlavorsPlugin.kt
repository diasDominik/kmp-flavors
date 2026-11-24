package uk.dominikdias.kmpflavors

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import uk.dominikdias.kmpflavors.internal.IntegrationClassLoader

/**
 * `id("uk.dominikdias.kmp-flavors")`. Applied to every project automatically by the settings plugin
 * unless `autoApply = false`. Reacts to the Kotlin Multiplatform, Kotlin/JVM and Android plugins.
 */
class KmpFlavorsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val settings =
            project.gradle.extensions.findByType(KmpFlavorsSettingsExtension::class.java)
                ?: throw GradleException(
                    "kmp-flavors: apply id(\"uk.dominikdias.kmp-flavors.settings\") in settings.gradle.kts; " +
                        "flavors are declared there once for the whole build.",
                )
        val extension =
            project.extensions
                .create(KmpFlavorsSettingsPlugin.EXTENSION_NAME, KmpFlavorsExtension::class.java)
                .also { it.settings = settings }

        if (project == project.rootProject) registerVariantsTask(project, settings)

        fun integrate(
            pluginId: String,
            integration: String,
            then: () -> Unit = {},
        ) = project.pluginManager.withPlugin(pluginId) {
            IntegrationClassLoader.create(project, settings, integration).apply(project, settings, extension)
            then()
        }

        val composeResources = { integrate("org.jetbrains.compose", "ComposeResourcesIntegration") }
        integrate("org.jetbrains.kotlin.multiplatform", "KotlinMultiplatformIntegration", composeResources)
        integrate("org.jetbrains.kotlin.jvm", "KotlinJvmIntegration", composeResources)
        integrate("com.android.application", "AndroidApplicationIntegration")
        integrate("com.android.library", "AndroidLibraryIntegration")
        integrate("com.android.kotlin.multiplatform.library", "AndroidKotlinMultiplatformLibraryIntegration")
    }

    private fun registerVariantsTask(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
    ) {
        project.tasks.register("kmpVariants", KmpVariantsTask::class.java) {
            group = "help"
            description = "Lists the KMP flavor variants and marks the active one."
            lines.set(
                project.provider {
                    val active = settings.variant
                    settings.variants.map { variant ->
                        val flavors = variant.productFlavors.joinToString { (dim, flavor) -> "$dim=$flavor" }
                        val marker = if (variant == active) "*" else " "
                        "$marker ${variant.name.padEnd(24)} buildType=${variant.buildType}" + if (flavors.isEmpty()) "" else ", $flavors"
                    } + listOf("", "Select with -Pkmp.variant=<name>, -Pkmp.flavor.<dimension>=<flavor> or -Pkmp.buildType=<name>.")
                },
            )
        }
    }
}

@DisableCachingByDefault(because = "Prints to the console")
abstract class KmpVariantsTask : DefaultTask() {
    @get:Input
    abstract val lines: ListProperty<String>

    @TaskAction
    fun print() = lines.get().forEach { logger.lifecycle(it) }
}

/**
 * Applied to every project by the settings plugin; applies [KmpFlavorsPlugin] unless the settings
 * turned `autoApply` off. Separate so the settings plugin's per-project hook captures no state.
 */
internal class KmpFlavorsAutoApplyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val settings = project.gradle.extensions.findByType(KmpFlavorsSettingsExtension::class.java) ?: return
        if (settings.autoApply) project.pluginManager.apply(KmpFlavorsPlugin::class.java)
    }
}
