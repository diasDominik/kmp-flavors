package uk.dominikdias.kmpflavors.internal

import uk.dominikdias.kmpflavors.KmpFlavorsSettingsExtension
import java.io.File

/**
 * `.gradle/kmp-flavors/model.properties`: the flavor model and active variant for the IDE plugin,
 * rewritten whenever the build is configured (every IDE sync). Keys:
 *
 * ```
 * format=1
 * active=freePlayDebug
 * dimensions=tier,store
 * dimension.tier=free,paid
 * buildTypes=debug,release
 * variants=freePlayDebug,…
 * variant.freePlayDebug.buildType=debug
 * variant.freePlayDebug.flavors=tier:free,store:play
 * variant.freePlayDebug.components=play,free,freePlay,debug,freePlayDebug
 * ```
 */
internal object ModelFile {
    const val PATH = ".gradle/kmp-flavors/model.properties"

    fun write(
        file: File,
        settings: KmpFlavorsSettingsExtension,
    ) {
        val model = settings.model
        val lines =
            buildList {
                add("# Written by kmp-flavors on every Gradle configuration; read by the kmp-flavors IDE plugin.")
                add("format=1")
                add("active=${settings.variant.name}")
                add("dimensions=${model.dimensions.joinToString(",")}")
                model.flavorsByDimension.forEach { (dim, flavors) -> add("dimension.$dim=${flavors.joinToString(",") { it.name }}") }
                add("buildTypes=${model.buildTypes.joinToString(",") { it.name }}")
                add("variants=${model.variants.joinToString(",") { it.name }}")
                model.variants.forEach { variant ->
                    add("variant.${variant.name}.buildType=${variant.buildType}")
                    add("variant.${variant.name}.flavors=${variant.productFlavors.joinToString(",") { (dim, flavor) -> "$dim:$flavor" }}")
                    add("variant.${variant.name}.components=${variant.components.joinToString(",")}")
                }
            }
        // Write without reading or checking the file first: any such check would make it a
        // configuration cache input, and this very write would then invalidate the cache.
        java.nio.file.Files
            .createDirectories(file.parentFile.toPath())
        java.nio.file.Files
            .writeString(file.toPath(), lines.joinToString("\n", postfix = "\n"))
    }
}
