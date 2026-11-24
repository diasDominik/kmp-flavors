package uk.dominikdias.kmpflavors

import uk.dominikdias.kmpflavors.internal.capitalized
import java.io.Serializable

/**
 * One build variant: a build type plus one product flavor per flavor dimension, exactly like an
 * Android variant. `free` + `play` + `debug` (dimensions `tier`, `store`) is `freePlayDebug`.
 */
data class KmpVariant(
    val buildType: String,
    /** Dimension to flavor, in flavor-dimension order (highest priority first). */
    val productFlavors: List<Pair<String, String>>,
    val isDebuggable: Boolean,
) : Serializable {
    /** Combined flavor name (`freePlay`); empty when the build has no product flavors. */
    val flavorName: String =
        productFlavors.mapIndexed { i, (_, flavor) -> if (i == 0) flavor else flavor.capitalized() }.joinToString("")

    /** Variant name (`freePlayDebug`), or the build type alone when there are no flavors. */
    val name: String = if (flavorName.isEmpty()) buildType else flavorName + buildType.capitalized()

    /** Dimension → flavor. */
    val flavors: Map<String, String> = productFlavors.toMap()

    fun flavor(dimension: String): String? = flavors[dimension]

    /**
     * Source-set components that take part in this variant, lowest priority first, mirroring
     * Android: each flavor (last dimension first), the multi-flavor combination, the build type and
     * finally the full variant. `main` itself is not listed.
     */
    val components: List<String> =
        buildList {
            productFlavors.asReversed().forEach { (_, flavor) -> add(flavor) }
            if (productFlavors.size > 1) add(flavorName)
            add(buildType)
            if (productFlavors.isNotEmpty()) add(name)
        }

    /** Whether [component] (a flavor, flavor combination, build type or variant name) is part of this variant. */
    fun isActive(component: String): Boolean = component in components

    override fun toString(): String = name
}

/** Passed to `variantFilter { }`; set [ignore] to drop a variant, like Android's legacy variant filter. */
class VariantFilter internal constructor(
    val variant: KmpVariant,
) {
    val name: String get() = variant.name
    val buildType: String get() = variant.buildType
    val flavors: Map<String, String> get() = variant.flavors
    var ignore: Boolean = false
}
