package uk.dominikdias.kmpflavors

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import uk.dominikdias.kmpflavors.internal.BuildTypeDef
import uk.dominikdias.kmpflavors.internal.FlavorDef
import uk.dominikdias.kmpflavors.internal.SelectionInput
import uk.dominikdias.kmpflavors.internal.VariantModel
import javax.inject.Inject

/**
 * `kmpFlavors { }` in `settings.gradle.kts`: the single definition of flavor dimensions, product
 * flavors and build types for every module in the build.
 *
 * ```
 * kmpFlavors {
 *     flavorDimensions("tier", "store")
 *     productFlavors {
 *         register("free") { dimension = "tier"; isDefault = true }
 *         register("paid") { dimension = "tier"; applicationIdSuffix = ".paid" }
 *         register("play") { dimension = "store" }
 *         register("foss") { dimension = "store" }
 *     }
 *     buildTypes {
 *         register("staging") { initWith(getByName("release")); isDebuggable = true }
 *     }
 *     variantFilter { ignore = flavors["tier"] == "paid" && flavors["store"] == "foss" }
 * }
 * ```
 */
abstract class KmpFlavorsSettingsExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        val flavorDimensions: MutableList<String> = mutableListOf()

        val productFlavors: NamedDomainObjectContainer<ProductFlavor> = objects.domainObjectContainer(ProductFlavor::class.java)

        /** Starts with `debug` and `release`, like Android. */
        val buildTypes: NamedDomainObjectContainer<BuildType> =
            objects.domainObjectContainer(BuildType::class.java).apply {
                register("debug")
                register("release")
            }

        /** Apply the project plugin to every project automatically. */
        var autoApply: Boolean = true

        /**
         * Fail when an Android module builds a variant other than the active one, because its
         * multiplatform dependencies were compiled for the active variant only.
         */
        var enforceActiveVariantOnAndroid: Boolean = true

        private val filters = mutableListOf<Action<VariantFilter>>()

        internal lateinit var selectionInput: () -> SelectionInput

        /** Bridge classloaders per set of plugin classloaders, shared by all projects of this build. */
        internal val integrationLoaders = java.util.concurrent.ConcurrentHashMap<List<ClassLoader>, ClassLoader>()

        fun flavorDimensions(vararg dimensions: String) {
            flavorDimensions += dimensions
        }

        fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavor>>) = action.execute(productFlavors)

        fun buildTypes(action: Action<NamedDomainObjectContainer<BuildType>>) = action.execute(buildTypes)

        /** Drops variants, like Android's `variantFilter { ignore = … }`. */
        fun variantFilter(action: Action<VariantFilter>) {
            filters += action
        }

        internal val model: VariantModel by lazy {
            VariantModel(
                dimensions = flavorDimensions,
                flavors = productFlavors.map { FlavorDef(it.name, it.dimension, it.isDefault) },
                buildTypes = buildTypes.map { BuildTypeDef(it.name, it.isDefault, it.isDebuggable) },
                filter = { variantFilter -> filters.forEach { it.execute(variantFilter) } },
            )
        }

        /**
         * The variant this build compiles. Resolved once, on first access, from (highest priority first)
         * `-Pkmp.variant`, `-Pkmp.flavor.<dimension>` / `-Pkmp.buildType`, the requested task names,
         * Xcode's `CONFIGURATION`, and finally `isDefault` / the first declared entry.
         */
        val variant: KmpVariant by lazy { model.resolve(selectionInput()) }

        /** All enabled variants. */
        val variants: List<KmpVariant> get() = model.variants

        /** Extras of the active variant's flavors and build type; the build type wins, then earlier dimensions. */
        val extras: Map<String, String> get() = mergedExtras(variant)

        internal fun mergedExtras(variant: KmpVariant): Map<String, String> =
            buildMap {
                variant.productFlavors.asReversed().forEach { (_, flavor) -> putAll(productFlavors.getByName(flavor).extras) }
                putAll(buildTypes.getByName(variant.buildType).extras)
            }
    }
