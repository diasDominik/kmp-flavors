package uk.dominikdias.kmpflavors

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * `kmpFlavors { }` in a module's build script. Flavors themselves are declared once in
 * `settings.gradle.kts`; here a module reads the active variant and declares its BuildConfig.
 *
 * Per-variant dependencies use Android-style configurations named after the flavor source set, e.g.
 * `"commonPaidMainImplementation"(libs.billing)`, `"iosDebugMainImplementation"(…)` or, in a
 * Kotlin/JVM module, `"paidImplementation"(…)`.
 */
abstract class KmpFlavorsExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        internal lateinit var settings: KmpFlavorsSettingsExtension

        val buildConfig: BuildConfigSpec =
            objects.newInstance(BuildConfigSpec::class.java).apply {
                className.convention("BuildConfig")
                internalVisibility.convention(false)
            }

        fun buildConfig(action: Action<BuildConfigSpec>) = action.execute(buildConfig)

        /** The variant this build compiles. */
        val variant: KmpVariant get() = settings.variant

        /** All enabled variants. */
        val variants: List<KmpVariant> get() = settings.variants

        /** Extras of the active flavors and build type. */
        val extras: Map<String, String> get() = settings.extras

        /** Whether a flavor, flavor combination, build type or variant name is part of the active variant. */
        fun isActive(name: String): Boolean = variant.isActive(name)
    }
