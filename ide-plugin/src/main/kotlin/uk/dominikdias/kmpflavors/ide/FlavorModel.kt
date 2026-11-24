package uk.dominikdias.kmpflavors.ide

import java.io.File
import java.util.Properties

/** The flavor model the Gradle plugin writes to `.gradle/kmp-flavors/model.properties` on every sync. */
data class FlavorModel(
    val active: String,
    val dimensions: Map<String, List<String>>,
    val buildTypes: List<String>,
    val variants: List<Variant>,
) {
    data class Variant(
        val name: String,
        val buildType: String,
        /** dimension to flavor, in dimension order. */
        val flavors: List<Pair<String, String>>,
        /** Source-set components, lowest priority first (`free`, `freePlay`, `debug`, `freePlayDebug`, …). */
        val components: List<String>,
    )

    val activeVariant: Variant? get() = variants.firstOrNull { it.name == active }

    /** Every component of every variant; longest first, so `freePlay` matches before `play`. */
    val allComponents: List<String> by lazy { variants.flatMap { it.components }.distinct().sortedByDescending { it.length } }

    fun isActive(component: String): Boolean = activeVariant?.components?.contains(component) == true

    /**
     * The variant that has [component] and differs least from the active one: same build type and
     * the most flavors in common, then the declaration order of [variants].
     */
    fun closestVariantWith(component: String): Variant? {
        val current = activeVariant
        return variants
            .filter { component in it.components }
            .maxByOrNull { candidate ->
                if (current == null) {
                    0
                } else {
                    candidate.flavors.count { it in current.flavors } * 2 + if (candidate.buildType == current.buildType) 1 else 0
                }
            }
    }

    /**
     * The component a folder directly under `src/` belongs to, or null when it is not a flavor folder.
     * Mirrors the Gradle plugin's naming: `commonFreeMain`, `iosPaidDebugTest`, `androidHostFreeTest`
     * (multiplatform), `free` and `testFree` (Kotlin/JVM and Android modules).
     */
    fun componentOfFolder(name: String): String? =
        allComponents.firstOrNull { component ->
            val capitalized = component.replaceFirstChar { it.uppercaseChar() }
            name == component ||
                name == "test$capitalized" ||
                listOf("Main", "Test").any { suffix ->
                    name.endsWith(capitalized + suffix) &&
                        name.length > capitalized.length + suffix.length &&
                        name.first().isLowerCase()
                }
        }

    companion object {
        const val PATH = ".gradle/kmp-flavors/model.properties"

        fun read(file: File): FlavorModel? = if (file.isFile) parse(Properties().apply { file.reader().use { load(it) } }) else null

        fun parse(properties: Properties): FlavorModel? {
            if (properties.getProperty("format") != "1") return null

            fun list(key: String) =
                properties
                    .getProperty(key)
                    .orEmpty()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            return FlavorModel(
                active = properties.getProperty("active") ?: return null,
                dimensions = list("dimensions").associateWith { list("dimension.$it") },
                buildTypes = list("buildTypes"),
                variants =
                    list("variants").map { name ->
                        Variant(
                            name = name,
                            buildType = properties.getProperty("variant.$name.buildType").orEmpty(),
                            flavors =
                                list("variant.$name.flavors").map {
                                    it.substringBefore(':') to it.substringAfter(':')
                                },
                            components = list("variant.$name.components"),
                        )
                    },
            )
        }
    }
}

/** Edits `kmp.variant` in `local.properties` text, keeping every other line as it is. */
object LocalProperties {
    const val KEY = "kmp.variant"
    private val line = Regex("""^\s*kmp\.variant\s*[=:].*$""")

    fun selected(text: String?): String? =
        text?.let {
            Properties()
                .apply { load(it.reader()) }
                .getProperty(KEY)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }

    fun withVariant(
        text: String?,
        variant: String?,
    ): String {
        val lines = text.orEmpty().lines().dropLastWhile { it.isEmpty() }
        val kept = lines.filterNot { line.matches(it) }
        val index = lines.indexOfFirst { line.matches(it) }.takeIf { it >= 0 } ?: kept.size
        val result = kept.toMutableList()
        if (variant != null) result.add(index.coerceAtMost(result.size), "$KEY=$variant")
        return if (result.isEmpty()) "" else result.joinToString("\n", postfix = "\n")
    }
}
