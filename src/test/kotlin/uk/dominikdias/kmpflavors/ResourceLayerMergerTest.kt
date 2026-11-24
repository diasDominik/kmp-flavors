package uk.dominikdias.kmpflavors

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import uk.dominikdias.kmpflavors.internal.ResourceLayerMerger
import uk.dominikdias.kmpflavors.internal.ResourceLayerMerger.Layer
import uk.dominikdias.kmpflavors.internal.ResourceLayerMerger.Mode
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceLayerMergerTest {
    @TempDir
    lateinit var dir: File

    private fun file(
        path: String,
        content: String,
    ) = dir.resolve(path).apply {
        parentFile.mkdirs()
        writeText(content)
    }

    private fun strings(vararg entries: Pair<String, String>) =
        "<resources>\n" + entries.joinToString("\n") { (k, v) -> "    <string name=\"$k\">$v</string>" } + "\n</resources>\n"

    private val out get() = dir.resolve("out")

    private fun merge(
        vararg labels: String,
        mode: Mode = Mode.COMPOSE,
    ) = ResourceLayerMerger.merge(labels.map { Layer(it, dir.resolve(it)) }, out, mode)

    private fun allValues(qualifier: String = "values"): String =
        out
            .resolve(qualifier)
            .listFiles()!!
            .sortedBy { it.name }
            .joinToString("\n") { it.readText() }

    @Test
    fun `higher layers replace single values and keep the rest`() {
        file("main/values/strings.xml", strings("app_name" to "Base", "greeting" to "Hi", "bye" to "Bye"))
        file("free/values/strings.xml", strings("app_name" to "Free", "greeting" to "Hey free"))
        file("debug/values/strings.xml", strings("greeting" to "Hey debug"))
        merge("main", "free", "debug")

        val values = allValues()
        assertContains(values, ">Free<")
        assertContains(values, ">Hey debug<")
        assertContains(values, ">Bye<")
        assertFalse(">Base<" in values)
        assertFalse(">Hi<" in values)
        assertFalse(">Hey free<" in values)
        assertEquals(1, Regex("name=\"greeting\"").findAll(values).count())
    }

    @Test
    fun `untouched files are copied byte for byte and fully overridden files disappear`() {
        val original = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- keep -->\n" + strings("a" to "A")
        file("main/values/other.xml", original)
        file("main/values/strings.xml", strings("b" to "B"))
        file("free/values/strings.xml", strings("b" to "B2"))
        merge("main", "free")

        assertEquals(original, out.resolve("values/other.xml").readText())
        assertFalse(out.resolve("values/strings.xml").exists())
        assertTrue(out.resolve("values/strings_free.xml").exists())
    }

    @Test
    fun `qualifiers and value types are separate keys`() {
        file("main/values/strings.xml", strings("t" to "en") + "")
        file("main/values-de/strings.xml", strings("t" to "de"))
        file("main/values/plurals.xml", "<resources><plurals name=\"t\"><item quantity=\"one\">x</item></plurals></resources>")
        file("free/values/strings.xml", strings("t" to "en-free"))
        merge("main", "free")

        assertContains(allValues("values-de"), ">de<")
        assertContains(allValues(), "<plurals name=\"t\">")
        assertContains(allValues(), ">en-free<")
        assertFalse(">en<" in allValues())
    }

    @Test
    fun `drawables override by name across extensions and files by path`() {
        file("main/drawable/logo.xml", "<vector/>")
        file("main/drawable/icon.png", "base-icon")
        file("main/files/data/config.json", "base")
        file("main/files/readme.txt", "base")
        file("paid/drawable/logo.png", "paid-logo")
        file("paid/files/data/config.json", "paid")
        merge("main", "paid")

        assertFalse(out.resolve("drawable/logo.xml").exists())
        assertEquals("paid-logo", out.resolve("drawable/logo.png").readText())
        assertEquals("base-icon", out.resolve("drawable/icon.png").readText())
        assertEquals("paid", out.resolve("files/data/config.json").readText())
        assertEquals("base", out.resolve("files/readme.txt").readText())
    }

    @Test
    fun `missing layers are skipped`() {
        file("free/values/strings.xml", strings("only" to "Free"))
        merge("main", "free")
        assertContains(allValues(), ">Free<")
    }

    @Test
    fun `android values use resource types and layered labels become valid file names`() {
        file(
            "main/values/res.xml",
            "<resources><string-array name=\"a\"><item>x</item></string-array><item type=\"string\" name=\"s\">base</item><color name=\"c\">#000</color></resources>",
        )
        file("devDebug/values/res.xml", "<resources><array name=\"a\"><item>y</item></array><string name=\"s\">dev</string></resources>")
        merge("main", "devDebug", mode = Mode.ANDROID_RES)

        val merged = allValues()
        assertFalse("string-array" in merged)
        assertFalse(">base<" in merged)
        assertContains(merged, "<color name=\"c\">")
        assertTrue(out.resolve("values/res_dev_debug.xml").exists())
    }

    @Test
    fun `android nine patches replace plain drawables and assets merge by path`() {
        file("main/res/drawable/bg.png", "plain")
        file("dev/res/drawable/bg.9.png", "nine")
        ResourceLayerMerger.merge(
            listOf(Layer("main", dir.resolve("main/res")), Layer("dev", dir.resolve("dev/res"))),
            out,
            Mode.ANDROID_RES,
        )
        assertFalse(out.resolve("drawable/bg.png").exists())
        assertEquals("nine", out.resolve("drawable/bg.9.png").readText())

        file("main/assets/fonts/a.ttf", "base")
        file("main/assets/values/x.xml", "<resources/>")
        file("dev/assets/fonts/a.otf", "dev")
        ResourceLayerMerger.merge(
            listOf(Layer("main", dir.resolve("main/assets")), Layer("dev", dir.resolve("dev/assets"))),
            out,
            Mode.FILES,
        )
        assertEquals("base", out.resolve("fonts/a.ttf").readText())
        assertEquals("dev", out.resolve("fonts/a.otf").readText())
        assertTrue(out.resolve("values/x.xml").exists())
    }
}
