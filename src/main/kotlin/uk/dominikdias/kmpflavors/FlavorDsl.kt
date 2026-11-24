package uk.dominikdias.kmpflavors

import org.gradle.api.Named
import javax.inject.Inject

/**
 * Properties shared by product flavors and build types. The Android-only ones (application id,
 * version, manifest placeholders, matching fallbacks) are copied onto Android modules
 * (`com.android.application` / `com.android.library`) that live in the same build.
 */
abstract class VariantDimensionDsl(
    private val name: String,
) : Named {
    override fun getName(): String = name

    /** Picks this entry when nothing else selects one, like Android's `isDefault`. */
    var isDefault: Boolean = false

    var applicationIdSuffix: String? = null
    var versionNameSuffix: String? = null

    /** Android: flavors/build types of Android library dependencies to fall back to. */
    val matchingFallbacks: MutableList<String> = mutableListOf()
    val manifestPlaceholders: MutableMap<String, Any> = mutableMapOf()

    /** Free-form values for build logic and iOS tooling, readable through `kmpFlavors.variant`. */
    val extras: MutableMap<String, String> = mutableMapOf()

    fun matchingFallbacks(vararg fallbacks: String) {
        matchingFallbacks += fallbacks
    }
}

abstract class ProductFlavor
    @Inject
    constructor(
        name: String,
    ) : VariantDimensionDsl(name) {
        /** Required when the build declares more than one flavor dimension. */
        var dimension: String? = null

        var applicationId: String? = null
        var versionCode: Int? = null
        var versionName: String? = null
    }

abstract class BuildType
    @Inject
    constructor(
        name: String,
    ) : VariantDimensionDsl(name) {
        /** Exposed as `BuildConfig.DEBUG`; `debug` starts as `true`, every other build type as `false`. */
        var isDebuggable: Boolean = name == "debug"

        var isMinifyEnabled: Boolean? = null

        /** Copies every property of [other] into this build type, like Android's `initWith`. */
        fun initWith(other: BuildType) {
            isDebuggable = other.isDebuggable
            isMinifyEnabled = other.isMinifyEnabled
            applicationIdSuffix = other.applicationIdSuffix
            versionNameSuffix = other.versionNameSuffix
            matchingFallbacks.clear()
            matchingFallbacks += other.matchingFallbacks
            manifestPlaceholders.clear()
            manifestPlaceholders += other.manifestPlaceholders
            extras.clear()
            extras += other.extras
        }
    }
