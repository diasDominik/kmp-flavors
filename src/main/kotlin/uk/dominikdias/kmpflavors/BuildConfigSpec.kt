package uk.dominikdias.kmpflavors

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import uk.dominikdias.kmpflavors.internal.fail
import uk.dominikdias.kmpflavors.internal.requireIdentifier
import uk.dominikdias.kmpflavors.internal.toScreamingSnake

/**
 * BuildConfig fields for one scope: default config, a product flavor, a build type or a variant.
 * Typed values become `const val`; raw `(type, name, expression)` fields become plain `val`.
 */
class BuildConfigFields internal constructor() {
    internal val fields = linkedMapOf<String, FieldValue>()

    fun buildConfigField(
        name: String,
        value: String,
    ) = put(name, FieldValue.Literal(value))

    fun buildConfigField(
        name: String,
        value: Boolean,
    ) = put(name, FieldValue.Literal(value))

    fun buildConfigField(
        name: String,
        value: Int,
    ) = put(name, FieldValue.Literal(value))

    fun buildConfigField(
        name: String,
        value: Long,
    ) = put(name, FieldValue.Literal(value))

    fun buildConfigField(
        name: String,
        value: Float,
    ) = put(name, FieldValue.Literal(value))

    fun buildConfigField(
        name: String,
        value: Double,
    ) = put(name, FieldValue.Literal(value))

    /** Lazy value (environment variable, file contents, …) of type String, Boolean, Int, Long, Float or Double. */
    fun buildConfigField(
        name: String,
        value: Provider<*>,
    ) = put(name, FieldValue.Lazy(value))

    /** Android-style raw field: [type] is a Kotlin type, [expression] is Kotlin source, e.g. `("List<String>", "HOSTS", "listOf(\"a\")")`. */
    fun buildConfigField(
        type: String,
        name: String,
        expression: String,
    ) = put(name, FieldValue.Raw(type, expression))

    private fun put(
        name: String,
        value: FieldValue,
    ) {
        requireIdentifier(name, "BuildConfig field")
        fields[name] = value
    }
}

internal sealed interface FieldValue {
    fun render(name: String): String

    data class Literal(
        val value: Any,
    ) : FieldValue {
        override fun render(name: String) = renderLiteral(name, value)
    }

    class Lazy(
        val value: Provider<*>,
    ) : FieldValue {
        override fun render(name: String) =
            renderLiteral(name, value.orNull ?: fail("BuildConfig field \"$name\" has no value (its provider is empty)."))
    }

    data class Raw(
        val type: String,
        val expression: String,
    ) : FieldValue {
        override fun render(name: String) = "val $name: $type = $expression"
    }
}

private fun renderLiteral(
    name: String,
    value: Any,
): String =
    when (value) {
        is String -> {
            "const val $name: String = ${value.toKotlinStringLiteral()}"
        }

        is Boolean -> {
            "const val $name: Boolean = $value"
        }

        is Int -> {
            "const val $name: Int = $value"
        }

        is Long -> {
            "const val $name: Long = ${value}L"
        }

        is Float -> {
            if (!value.isFinite()) fail("BuildConfig field \"$name\" must be finite.")
            "const val $name: Float = ${value}f"
        }

        is Double -> {
            if (!value.isFinite()) fail("BuildConfig field \"$name\" must be finite.")
            "const val $name: Double = $value"
        }

        else -> {
            fail("BuildConfig field \"$name\" has unsupported type ${value::class.java.name}; use the raw (type, name, expression) form.")
        }
    }

internal fun String.toKotlinStringLiteral(): String =
    buildString {
        append('"')
        for (c in this@toKotlinStringLiteral) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

/**
 * `kmpFlavors { buildConfig { } }`: a generated Kotlin object in `commonMain` (KMP) or `main` (Kotlin/JVM)
 * holding the active variant's values. It is generated only when [packageName] is set.
 *
 * Values resolve like Android's BuildConfig: variant > build type > product flavors (earlier
 * dimension wins) > default config. These fields are always present:
 * `VARIANT`, `BUILD_TYPE`, `FLAVOR`, `DEBUG` and `FLAVOR_<DIMENSION>` for each dimension.
 */
abstract class BuildConfigSpec {
    abstract val packageName: Property<String>

    /** Defaults to `BuildConfig`. */
    abstract val className: Property<String>

    /** Generate an `internal object` instead of a public one. Defaults to `false`. */
    abstract val internalVisibility: Property<Boolean>

    internal val defaultConfig = BuildConfigFields()
    internal val perFlavor = linkedMapOf<String, BuildConfigFields>()
    internal val perBuildType = linkedMapOf<String, BuildConfigFields>()
    internal val perVariant = linkedMapOf<String, BuildConfigFields>()

    fun buildConfigField(
        name: String,
        value: String,
    ) = defaultConfig.buildConfigField(name, value)

    fun buildConfigField(
        name: String,
        value: Boolean,
    ) = defaultConfig.buildConfigField(name, value)

    fun buildConfigField(
        name: String,
        value: Int,
    ) = defaultConfig.buildConfigField(name, value)

    fun buildConfigField(
        name: String,
        value: Long,
    ) = defaultConfig.buildConfigField(name, value)

    fun buildConfigField(
        name: String,
        value: Float,
    ) = defaultConfig.buildConfigField(name, value)

    fun buildConfigField(
        name: String,
        value: Double,
    ) = defaultConfig.buildConfigField(name, value)

    fun buildConfigField(
        name: String,
        value: Provider<*>,
    ) = defaultConfig.buildConfigField(name, value)

    fun buildConfigField(
        type: String,
        name: String,
        expression: String,
    ) = defaultConfig.buildConfigField(type, name, expression)

    fun productFlavor(
        name: String,
        action: Action<BuildConfigFields>,
    ) = action.execute(perFlavor.getOrPut(name) { BuildConfigFields() })

    fun buildType(
        name: String,
        action: Action<BuildConfigFields>,
    ) = action.execute(perBuildType.getOrPut(name) { BuildConfigFields() })

    fun variant(
        name: String,
        action: Action<BuildConfigFields>,
    ) = action.execute(perVariant.getOrPut(name) { BuildConfigFields() })

    /** Declarations for [variant], in a stable order. Scopes naming unknown flavors/build types/variants fail. */
    internal fun render(
        variant: KmpVariant,
        known: Set<String>,
    ): List<String> {
        (perFlavor.keys + perBuildType.keys + perVariant.keys)
            .firstOrNull { it !in known }
            ?.let { fail("buildConfig refers to \"$it\", which is not a product flavor, build type or variant.") }

        val builtIn =
            linkedMapOf<String, FieldValue>(
                "VARIANT" to FieldValue.Literal(variant.name),
                "BUILD_TYPE" to FieldValue.Literal(variant.buildType),
                "FLAVOR" to FieldValue.Literal(variant.flavorName),
                "DEBUG" to FieldValue.Literal(variant.isDebuggable),
            )
        variant.productFlavors.forEach { (dim, flavor) -> builtIn["FLAVOR_${dim.toScreamingSnake()}"] = FieldValue.Literal(flavor) }

        val user = linkedMapOf<String, FieldValue>()
        user += defaultConfig.fields
        variant.productFlavors.asReversed().forEach { (_, flavor) -> perFlavor[flavor]?.let { user += it.fields } }
        perBuildType[variant.buildType]?.let { user += it.fields }
        perVariant[variant.name]?.let { user += it.fields }

        user.keys.firstOrNull { it in builtIn }?.let {
            fail(
                "BuildConfig field \"$it\" is generated by kmp-flavors and cannot be redefined.",
            )
        }
        return (builtIn + user).map { (name, value) -> value.render(name) }
    }
}
