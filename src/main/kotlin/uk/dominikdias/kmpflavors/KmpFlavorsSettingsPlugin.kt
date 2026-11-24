package uk.dominikdias.kmpflavors

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging
import uk.dominikdias.kmpflavors.internal.ModelFile
import uk.dominikdias.kmpflavors.internal.SelectionInput
import uk.dominikdias.kmpflavors.internal.toScreamingSnake

/** `id("uk.dominikdias.kmp-flavors.settings")` in `settings.gradle.kts`. */
class KmpFlavorsSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val extension = settings.extensions.create(EXTENSION_NAME, KmpFlavorsSettingsExtension::class.java)
        val providers = settings.providers
        val taskNames = settings.startParameter.taskNames.toList()

        val localProperties = providers.fileContents(settings.layout.rootDirectory.file("local.properties")).asText

        fun ideSelection(): String? =
            localProperties.orNull?.let { text ->
                java.util
                    .Properties()
                    .apply { load(text.reader()) }
                    .getProperty("kmp.variant")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }

        fun value(
            property: String,
            env: String,
        ): String? =
            providers
                .gradleProperty(property)
                .orElse(providers.environmentVariable(env))
                .orNull
                ?.takeIf { it.isNotBlank() }

        extension.selectionInput = {
            val inXcode = providers.environmentVariable("XCODE_VERSION_ACTUAL").isPresent
            SelectionInput(
                variant = value("kmp.variant", "KMP_VARIANT"),
                flavors =
                    extension.flavorDimensions
                        .mapNotNull { dim -> value("kmp.flavor.$dim", "KMP_FLAVOR_${dim.toScreamingSnake()}")?.let { dim to it } }
                        .toMap(),
                buildType = value("kmp.buildType", "KMP_BUILD_TYPE"),
                taskNames = taskNames,
                xcodeConfiguration = if (inXcode) providers.environmentVariable("CONFIGURATION").orNull else null,
                ideVariant = ideSelection(),
            )
        }

        // The IDE plugin reads the variants from here after each sync.
        settings.gradle.settingsEvaluated {
            ModelFile.write(rootDir.resolve(ModelFile.PATH), extension)
            ideSelection()?.takeIf { selected -> extension.variants.none { it.name.equals(selected, ignoreCase = true) } }?.let {
                Logging
                    .getLogger(
                        KmpFlavorsSettingsPlugin::class.java,
                    ).warn("kmp-flavors: ignoring kmp.variant=$it from local.properties; it is not an enabled variant.")
            }
        }

        // Projects reach the shared definition through the Gradle instance.
        settings.gradle.extensions.add(KmpFlavorsSettingsExtension::class.java, GRADLE_EXTENSION_NAME, extension)

        settings.gradle.lifecycle.beforeProject {
            pluginManager.apply(KmpFlavorsAutoApplyPlugin::class.java)
        }
    }

    internal companion object {
        const val EXTENSION_NAME = "kmpFlavors"
        const val GRADLE_EXTENSION_NAME = "kmpFlavorsSettings"
    }
}
