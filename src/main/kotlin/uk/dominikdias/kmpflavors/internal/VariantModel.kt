package uk.dominikdias.kmpflavors.internal

import org.gradle.api.GradleException
import uk.dominikdias.kmpflavors.KmpVariant
import uk.dominikdias.kmpflavors.VariantFilter

internal data class FlavorDef(
    val name: String,
    val dimension: String?,
    val isDefault: Boolean,
)

internal data class BuildTypeDef(
    val name: String,
    val isDefault: Boolean,
    val isDebuggable: Boolean,
)

/** Everything that can pick the active variant, gathered by the settings plugin. */
internal data class SelectionInput(
    /** `-Pkmp.variant` / `KMP_VARIANT` */
    val variant: String? = null,
    /** `-Pkmp.flavor.<dimension>` / `KMP_FLAVOR_<DIMENSION>`, keyed by dimension. */
    val flavors: Map<String, String> = emptyMap(),
    /** `-Pkmp.buildType` / `KMP_BUILD_TYPE` */
    val buildType: String? = null,
    /** Tasks requested on the command line or by the IDE. */
    val taskNames: List<String> = emptyList(),
    /** Xcode's `CONFIGURATION` build setting, only when Gradle runs inside an Xcode build phase. */
    val xcodeConfiguration: String? = null,
    /** `kmp.variant` in `local.properties`, written by the IDE plugin; unknown names are ignored. */
    val ideVariant: String? = null,
)

/** Validated flavor configuration and the variants it produces. */
internal class VariantModel(
    dimensions: List<String>,
    flavors: List<FlavorDef>,
    buildTypes: List<BuildTypeDef>,
    filter: (VariantFilter) -> Unit = {},
) {
    val dimensions: List<String> = dimensions.distinct()

    /** Flavors with their dimension resolved, grouped per dimension in declaration order. */
    val flavorsByDimension: Map<String, List<FlavorDef>>

    val buildTypes: List<BuildTypeDef> = buildTypes

    /** Every combination, including ignored ones. */
    val allVariants: List<KmpVariant>

    /** Variants not removed by `variantFilter`. */
    val variants: List<KmpVariant>

    init {
        if (buildTypes.isEmpty()) fail("At least one build type is required.")
        if (flavors.isNotEmpty() && this.dimensions.isEmpty()) {
            fail("Product flavors ${flavors.map { it.name }} need a flavor dimension. Declare one with flavorDimensions(\"…\").")
        }
        this.dimensions.forEach { requireIdentifierOrFail(it, "Flavor dimension") }

        val resolved =
            flavors.map { flavor ->
                requireIdentifierOrFail(flavor.name, "Product flavor")
                val dimension =
                    flavor.dimension
                        ?: this.dimensions.singleOrNull()
                        ?: fail("Product flavor \"${flavor.name}\" has no dimension; with several dimensions each flavor must set one.")
                if (dimension !in this.dimensions) {
                    fail("Product flavor \"${flavor.name}\" uses unknown dimension \"$dimension\". Known: ${this.dimensions}.")
                }
                flavor.copy(dimension = dimension)
            }
        flavorsByDimension = this.dimensions.associateWith { dim -> resolved.filter { it.dimension == dim } }
        flavorsByDimension.forEach { (dim, list) ->
            if (list.isEmpty()) fail("Flavor dimension \"$dim\" has no product flavors.")
            if (list.count { it.isDefault } > 1) fail("Flavor dimension \"$dim\" has more than one default flavor.")
        }
        buildTypes.forEach { requireIdentifierOrFail(it.name, "Build type") }
        if (buildTypes.count { it.isDefault } > 1) fail("More than one build type is marked isDefault.")

        val allNames = resolved.map { it.name } + buildTypes.map { it.name }
        allNames.groupBy { it.lowercase() }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
            fail("Name \"$it\" is used more than once across product flavors and build types.")
        }
        allNames.firstOrNull { it in RESERVED_NAMES || it.startsWith("test") || it.startsWith("androidTest") }?.let {
            fail("\"$it\" cannot be used as a flavor or build type name; it clashes with source set names.")
        }

        allVariants =
            cartesian(this.dimensions.map { dim -> flavorsByDimension.getValue(dim).map { dim to it.name } })
                .flatMap { combo -> buildTypes.map { bt -> KmpVariant(bt.name, combo, bt.isDebuggable) } }
        variants = allVariants.filterNot { variant -> VariantFilter(variant).also(filter).ignore }
        if (variants.isEmpty()) fail("variantFilter ignored every variant.")
    }

    /** Every name that can own a source set or dependency configuration across all enabled variants. */
    val allComponents: Set<String> = variants.flatMapTo(linkedSetOf()) { it.components }

    fun resolve(input: SelectionInput): KmpVariant {
        // Android's default: isDefault, else `debug`, else alphabetical.
        var buildType =
            (buildTypes.firstOrNull { it.isDefault } ?: buildTypes.firstOrNull { it.name == "debug" } ?: buildTypes.minBy { it.name }).name
        val flavors =
            flavorsByDimension
                .mapValues { (_, list) -> (list.firstOrNull { it.isDefault } ?: list.minBy { it.name }).name }
                .toMutableMap()
        val defaults = KmpVariant(buildType, dimensions.map { it to flavors.getValue(it) }, isDebuggable = false)
        // Parts chosen by the user (directly or via task names); the rest may move to avoid ignored variants.
        var buildTypePinned = false
        val pinnedDimensions = mutableSetOf<String>()

        // The IDE selection replaces the defaults but nothing chosen for this particular build.
        input.ideVariant
            ?.let { selected -> variants.firstOrNull { it.name.equals(selected, ignoreCase = true) } }
            ?.let { selected ->
                buildType = selected.buildType
                flavors.putAll(selected.flavors)
            }

        input.xcodeConfiguration
            ?.let { config -> buildTypes.firstOrNull { it.name.equals(config, ignoreCase = true) } }
            ?.let {
                buildType = it.name
                buildTypePinned = true
            }

        inferFromTasks(input.taskNames)?.let { inferred ->
            buildType = inferred.buildType
            flavors.putAll(inferred.flavors)
            buildTypePinned = true
            pinnedDimensions += dimensions
        }

        input.flavors.forEach { (dimension, flavor) ->
            val options =
                flavorsByDimension[dimension]
                    ?: fail("Unknown flavor dimension \"$dimension\" in kmp.flavor.$dimension. Known: $dimensions.")
            flavors[dimension] = options.firstOrNull { it.name.equals(flavor, ignoreCase = true) }?.name
                ?: fail("Unknown flavor \"$flavor\" for dimension \"$dimension\". Known: ${options.map { it.name }}.")
            pinnedDimensions += dimension
        }
        input.buildType?.let { requested ->
            buildType = buildTypes.firstOrNull { it.name.equals(requested, ignoreCase = true) }?.name
                ?: fail("Unknown build type \"$requested\". Known: ${buildTypes.map { it.name }}.")
            buildTypePinned = true
        }

        input.variant?.let { requested ->
            return variants.firstOrNull { it.name.equals(requested, ignoreCase = true) }
                ?: allVariants.firstOrNull { it.name.equals(requested, ignoreCase = true) }?.let {
                    fail("Variant \"${it.name}\" is ignored by variantFilter.")
                }
                ?: fail("Unknown variant \"$requested\". Known: ${variants.map { it.name }}.")
        }

        val selected = allVariants.first { it.buildType == buildType && it.flavors == flavors }
        if (selected in variants) return selected

        // Like Android Studio: when the defaults land on an ignored variant, take the enabled variant
        // that keeps every pinned part and as many defaults as possible.
        return variants
            .filter { candidate ->
                (!buildTypePinned || candidate.buildType == buildType) &&
                    pinnedDimensions.all { candidate.flavor(it) == flavors[it] }
            }.maxByOrNull { candidate ->
                // Keeping an explicit isDefault counts more than keeping an alphabetical fallback.
                fun weight(explicit: Boolean) = if (explicit) 2 else 1
                val buildTypeScore =
                    if (candidate.buildType == defaults.buildType) weight(buildTypes.any { it.isDefault }) else 0
                buildTypeScore +
                    dimensions.sumOf { dim ->
                        if (candidate.flavor(dim) ==
                            defaults.flavor(dim)
                        ) {
                            weight(flavorsByDimension.getValue(dim).any { it.isDefault })
                        } else {
                            0
                        }
                    }
            }
            ?: fail(
                "Variant \"${selected.name}\" is ignored by variantFilter and no enabled variant matches the selection. " +
                    "Pick another with -Pkmp.variant=<name>; known: ${variants.map { it.name }}.",
            )
    }

    /**
     * Variant named by the requested tasks, e.g. `:androidApp:assembleFreeDebug` or `compilePaidReleaseKotlinJvm`.
     * Only full variant names count, so a bare `assembleDebug` in a flavored build infers nothing.
     */
    internal fun inferFromTasks(taskNames: List<String>): KmpVariant? {
        val inferred =
            taskNames
                .map { it.substringAfterLast(':') }
                .mapNotNull { task -> variants.filter { mentions(task, it.name) }.maxByOrNull { it.name.length } }
                .distinct()
        if (inferred.size > 1) {
            fail(
                "The requested tasks target several variants (${inferred.joinToString { it.name }}). " +
                    "Kotlin Multiplatform compiles one variant per build; run them in separate builds.",
            )
        }
        return inferred.singleOrNull()
    }

    private fun mentions(
        task: String,
        variant: String,
    ): Boolean {
        fun boundaryAfter(end: Int) = end == task.length || !task[end].isLowerCase()
        if (task.startsWith(variant) && boundaryAfter(variant.length)) return true
        val needle = variant.capitalized()
        var index = task.indexOf(needle, startIndex = 1)
        while (index >= 0) {
            if (boundaryAfter(index + needle.length)) return true
            index = task.indexOf(needle, startIndex = index + 1)
        }
        return false
    }

    private companion object {
        val RESERVED_NAMES = setOf("main", "test", "common", "androidTest", "lint", "testFixtures")

        fun <T> cartesian(lists: List<List<T>>): List<List<T>> =
            lists.fold(listOf(emptyList())) { acc, list -> acc.flatMap { prefix -> list.map { prefix + it } } }
    }
}

private fun requireIdentifierOrFail(
    value: String,
    what: String,
) {
    try {
        requireIdentifier(value, what)
    } catch (e: IllegalArgumentException) {
        fail(e.message!!)
    }
}

internal fun fail(message: String): Nothing = throw GradleException("kmp-flavors: $message")
