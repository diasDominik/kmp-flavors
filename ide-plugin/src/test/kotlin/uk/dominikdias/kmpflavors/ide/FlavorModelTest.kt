package uk.dominikdias.kmpflavors.ide

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlavorModelTest {
    // What the Gradle plugin writes for tier(free, paid) × store(play, foss) × debug/release, paidFoss ignored.
    private val text =
        """
        format=1
        active=freePlayDebug
        dimensions=tier,store
        dimension.tier=free,paid
        dimension.store=play,foss
        buildTypes=debug,release
        variants=freeFossDebug,freeFossRelease,freePlayDebug,freePlayRelease,paidPlayDebug,paidPlayRelease
        """.trimIndent() +
            "\n" +
            listOf("freeFoss", "freePlay", "paidPlay")
                .flatMap { combo ->
                    val tier = if (combo.startsWith("free")) "free" else "paid"
                    val store = combo.removePrefix(tier).lowercase()
                    listOf("debug", "release").map { bt ->
                        val name = combo + bt.replaceFirstChar { it.uppercaseChar() }
                        "variant.$name.buildType=$bt\nvariant.$name.flavors=tier:$tier,store:$store\n" +
                            "variant.$name.components=$store,$tier,$combo,$bt,$name"
                    }
                }.joinToString("\n")

    private val model = FlavorModel.parse(Properties().apply { load(text.reader()) })!!

    @Test
    fun `parses the model`() {
        assertEquals("freePlayDebug", model.active)
        assertEquals(mapOf("tier" to listOf("free", "paid"), "store" to listOf("play", "foss")), model.dimensions)
        assertEquals(6, model.variants.size)
        assertEquals(listOf("tier" to "paid", "store" to "play"), model.variants.first { it.name == "paidPlayRelease" }.flavors)
        assertNull(FlavorModel.parse(Properties().apply { setProperty("format", "2") }))
    }

    @Test
    fun `flavor folders map to their component`() {
        assertEquals("free", model.componentOfFolder("commonFreeMain"))
        assertEquals("freePlay", model.componentOfFolder("commonFreePlayMain"))
        assertEquals("paidPlayDebug", model.componentOfFolder("iosPaidPlayDebugTest"))
        assertEquals("free", model.componentOfFolder("androidHostFreeTest"))
        assertEquals("release", model.componentOfFolder("release"))
        assertEquals("foss", model.componentOfFolder("testFoss"))
        assertNull(model.componentOfFolder("commonMain"))
        assertNull(model.componentOfFolder("FreeMain"))
        assertNull(model.componentOfFolder("androidMain"))
    }

    @Test
    fun `activity and closest variant`() {
        assertTrue(model.isActive("freePlay"))
        assertFalse(model.isActive("paid"))
        // Keep the build type and as many flavors as possible.
        assertEquals("paidPlayDebug", model.closestVariantWith("paid")?.name)
        assertEquals("freeFossDebug", model.closestVariantWith("foss")?.name)
        assertEquals("freePlayRelease", model.closestVariantWith("release")?.name)
        assertNull(model.closestVariantWith("paidFoss"))
    }

    @Test
    fun `local properties keep other lines`() {
        assertEquals("kmp.variant=a\n", LocalProperties.withVariant(null, "a"))
        assertEquals("sdk.dir=/x\nkmp.variant=a\n", LocalProperties.withVariant("sdk.dir=/x", "a"))
        assertEquals(
            "# c\nkmp.variant=b\nsdk.dir=/x\n",
            LocalProperties.withVariant("# c\nkmp.variant = a\nsdk.dir=/x\n", "b"),
        )
        assertEquals("sdk.dir=/x\n", LocalProperties.withVariant("sdk.dir=/x\nkmp.variant=a\n", null))
        assertEquals("b", LocalProperties.selected("kmp.variant = b\n"))
        assertNull(LocalProperties.selected("sdk.dir=/x"))
    }
}
