package uk.dominikdias.kmpflavors.internal

internal fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }

internal fun String.decapitalized(): String = replaceFirstChar { it.lowercaseChar() }

/**
 * Name of the flavor-specific counterpart of a Kotlin source set, which is also its folder under `src/`.
 *
 * - KMP: `commonMain` + `free` → `commonFreeMain`, `iosTest` + `paidDebug` → `iosPaidDebugTest`
 * - Kotlin/JVM, Android-style: `main` + `free` → `free`, `test` + `free` → `testFree`
 */
internal fun flavorSourceSetName(
    sourceSet: String,
    component: String,
): String =
    when {
        sourceSet == "main" -> component.decapitalized()
        sourceSet.length > 4 && sourceSet.endsWith("Main") -> sourceSet.dropLast(4) + component.capitalized() + "Main"
        sourceSet.length > 4 && sourceSet.endsWith("Test") -> sourceSet.dropLast(4) + component.capitalized() + "Test"
        else -> sourceSet + component.capitalized()
    }

private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

internal fun requireIdentifier(
    value: String,
    what: String,
) {
    require(identifier.matches(value)) { "$what \"$value\" must be a valid identifier ([A-Za-z_][A-Za-z0-9_]*)." }
}

/** `storeFront` → `STORE_FRONT` */
internal fun String.toScreamingSnake(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .replace(Regex("[^A-Za-z0-9]"), "_")
        .uppercase()
