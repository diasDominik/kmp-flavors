package uk.dominikdias.kmpflavors.integration

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import uk.dominikdias.kmpflavors.GenerateBuildConfigTask
import uk.dominikdias.kmpflavors.KmpFlavorsExtension
import uk.dominikdias.kmpflavors.KmpFlavorsSettingsExtension
import uk.dominikdias.kmpflavors.internal.flavorSourceSetName

/**
 * For every Kotlin source set `X` and every component `C` of the active variant (flavors, flavor
 * combination, build type, variant), adds `src/<X with C>/kotlin` and `/resources` to `X` and makes
 * `X`'s dependency configurations extend the `<X with C>Implementation` (etc.) ones.
 */
internal object KotlinWiring {
    private val scopes: List<Pair<String, (KotlinSourceSet) -> String>> =
        listOf(
            "Api" to { it.apiConfigurationName },
            "Implementation" to { it.implementationConfigurationName },
            "CompileOnly" to { it.compileOnlyConfigurationName },
            "RuntimeOnly" to { it.runtimeOnlyConfigurationName },
        )

    fun wire(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
        extension: KmpFlavorsExtension,
        buildConfigSourceSet: String,
    ) {
        val kotlin = project.extensions.getByType(KotlinProjectExtension::class.java)
        val model = settings.model
        val variant = settings.variant
        val configurations = project.configurations

        kotlin.sourceSets.configureEach {
            val sourceSet = this
            // Every flavor configuration exists, so build scripts can declare dependencies for any variant.
            model.allComponents.forEach { component ->
                val base = flavorSourceSetName(sourceSet.name, component)
                scopes.forEach { (scope, _) ->
                    if (base + scope !in configurations.names) configurations.dependencyScope(base + scope)
                }
            }
            // Only the active variant's sources and dependencies take part in compilation.
            variant.components.forEach { component ->
                val base = flavorSourceSetName(sourceSet.name, component)
                sourceSet.kotlin.srcDir("src/$base/kotlin")
                sourceSet.resources.srcDir("src/$base/resources")
                scopes.forEach { (scope, kotlinConfiguration) ->
                    val flavorConfiguration = configurations.named(base + scope)
                    configurations
                        .named { it == kotlinConfiguration(sourceSet) }
                        .configureEach { extendsFrom(flavorConfiguration.get()) }
                }
            }
        }

        val knownScopes =
            model.flavorsByDimension.values
                .flatten()
                .map { it.name }
                .toSet() +
                model.buildTypes.map { it.name } +
                model.allVariants.map { it.name }
        val buildConfig = extension.buildConfig
        val generate =
            project.tasks.register("generateKmpFlavorsBuildConfig", GenerateBuildConfigTask::class.java) {
                group = "build"
                description = "Generates the BuildConfig object for the ${variant.name} variant."
                packageName.set(buildConfig.packageName)
                className.set(buildConfig.className)
                internalVisibility.set(buildConfig.internalVisibility)
                declarations.set(
                    project.provider {
                        if (buildConfig.packageName.isPresent) buildConfig.render(variant, knownScopes) else emptyList()
                    },
                )
                outputDirectory.set(project.layout.buildDirectory.dir("generated/kmpFlavors/buildConfig"))
            }
        kotlin.sourceSets
            .named { it == buildConfigSourceSet }
            .configureEach { this.kotlin.srcDir(generate.flatMap { it.outputDirectory }) }
        // Generate during IDE sync so the IDE resolves BuildConfig before the first build.
        project.tasks.named { it == "prepareKotlinIdeaImport" }.configureEach { dependsOn(generate) }
    }
}
