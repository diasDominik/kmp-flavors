package uk.dominikdias.kmpflavors

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildConfigSpecTest {
    private val project = ProjectBuilder.builder().build()

    private fun spec() = project.objects.newInstance(BuildConfigSpec::class.java)

    private val freePlayDebug = KmpVariant("debug", listOf("tier" to "free", "store" to "play"), isDebuggable = true)
    private val known = setOf("free", "paid", "play", "debug", "release", "freePlayDebug")

    @Test
    fun `built-in fields describe the variant`() {
        assertEquals(
            listOf(
                "const val VARIANT: String = \"freePlayDebug\"",
                "const val BUILD_TYPE: String = \"debug\"",
                "const val FLAVOR: String = \"freePlay\"",
                "const val DEBUG: Boolean = true",
                "const val FLAVOR_TIER: String = \"free\"",
                "const val FLAVOR_STORE: String = \"play\"",
            ),
            spec().render(freePlayDebug, known),
        )
    }

    @Test
    fun `variant beats build type beats earlier dimension beats later dimension beats default`() {
        val spec =
            spec().apply {
                buildConfigField("A", "default")
                buildConfigField("B", "default")
                buildConfigField("C", "default")
                buildConfigField("D", "default")
                buildConfigField("E", "default")
                productFlavor("play") {
                    buildConfigField("B", "play")
                    buildConfigField("C", "play")
                    buildConfigField("D", "play")
                    buildConfigField("E", "play")
                }
                productFlavor("free") {
                    buildConfigField("C", "free")
                    buildConfigField("D", "free")
                    buildConfigField("E", "free")
                }
                productFlavor("paid") { buildConfigField("A", "paid") }
                buildType("debug") {
                    buildConfigField("D", "debug")
                    buildConfigField("E", "debug")
                }
                variant("freePlayDebug") { buildConfigField("E", "variant") }
            }
        val user = spec.render(freePlayDebug, known).drop(6)
        assertEquals(
            listOf(
                "const val A: String = \"default\"",
                "const val B: String = \"play\"",
                "const val C: String = \"free\"",
                "const val D: String = \"debug\"",
                "const val E: String = \"variant\"",
            ),
            user,
        )
    }

    @Test
    fun `value types and escaping`() {
        val spec =
            spec().apply {
                buildConfigField("S", "a\"b\$c\\d\n")
                buildConfigField("I", 1)
                buildConfigField("L", 2L)
                buildConfigField("F", 1.5f)
                buildConfigField("D", 2.5)
                buildConfigField("P", project.provider { true })
                buildConfigField("List<String>", "R", "listOf(\"x\")")
            }
        assertEquals(
            listOf(
                "const val S: String = \"a\\\"b\\\$c\\\\d\\n\"",
                "const val I: Int = 1",
                "const val L: Long = 2L",
                "const val F: Float = 1.5f",
                "const val D: Double = 2.5",
                "const val P: Boolean = true",
                "val R: List<String> = listOf(\"x\")",
            ),
            spec.render(freePlayDebug, known).drop(6),
        )
    }

    @Test
    fun `unknown scopes and built-in names are rejected`() {
        assertFailsWith<GradleException> { spec().apply { productFlavor("gold") {} }.render(freePlayDebug, known) }
        assertFailsWith<GradleException> { spec().apply { buildConfigField("DEBUG", false) }.render(freePlayDebug, known) }
        assertFailsWith<IllegalArgumentException> { spec().buildConfigField("not valid", 1) }
    }
}
