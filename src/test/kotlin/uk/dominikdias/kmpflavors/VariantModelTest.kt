package uk.dominikdias.kmpflavors

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import uk.dominikdias.kmpflavors.internal.BuildTypeDef
import uk.dominikdias.kmpflavors.internal.FlavorDef
import uk.dominikdias.kmpflavors.internal.SelectionInput
import uk.dominikdias.kmpflavors.internal.VariantModel
import uk.dominikdias.kmpflavors.internal.flavorSourceSetName
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VariantModelTest {
    private val buildTypes =
        listOf(
            BuildTypeDef("debug", isDefault = false, isDebuggable = true),
            BuildTypeDef("release", isDefault = false, isDebuggable = false),
        )

    private fun twoDimensions(filter: (VariantFilter) -> Unit = {}) =
        VariantModel(
            dimensions = listOf("tier", "store"),
            flavors =
                listOf(
                    FlavorDef("free", "tier", isDefault = false),
                    FlavorDef("paid", "tier", isDefault = true),
                    FlavorDef("play", "store", isDefault = false),
                    FlavorDef("foss", "store", isDefault = false),
                ),
            buildTypes = buildTypes,
            filter = filter,
        )

    @Test
    fun `variants are the cartesian product in dimension order`() {
        val names = twoDimensions().variants.map { it.name }
        assertEquals(
            listOf(
                "freePlayDebug",
                "freePlayRelease",
                "freeFossDebug",
                "freeFossRelease",
                "paidPlayDebug",
                "paidPlayRelease",
                "paidFossDebug",
                "paidFossRelease",
            ),
            names,
        )
    }

    @Test
    fun `components follow android source set priority`() {
        val variant = twoDimensions().variants.first { it.name == "freePlayDebug" }
        assertEquals(listOf("play", "free", "freePlay", "debug", "freePlayDebug"), variant.components)
    }

    @Test
    fun `defaults come from isDefault then alphabetical and debug build type`() {
        assertEquals("paidFossDebug", twoDimensions().resolve(SelectionInput()).name)
    }

    @Test
    fun `ignored defaults move to the closest enabled variant but pinned parts stay`() {
        val model = twoDimensions { if (it.flavors["tier"] == "paid" && it.flavors["store"] == "foss") it.ignore = true }
        assertEquals("paidPlayDebug", model.resolve(SelectionInput()).name)
        assertEquals("freeFossRelease", model.resolve(SelectionInput(flavors = mapOf("store" to "foss"), buildType = "release")).name)
        assertFailsWith<GradleException> {
            model.resolve(SelectionInput(flavors = mapOf("store" to "foss", "tier" to "paid")))
        }
    }

    @Test
    fun `explicit variant wins over everything`() {
        val input =
            SelectionInput(
                variant = "freeFossRelease",
                flavors = mapOf("tier" to "paid"),
                buildType = "debug",
                taskNames = listOf(":app:assemblePaidPlayDebug"),
            )
        assertEquals("freeFossRelease", twoDimensions().resolve(input).name)
    }

    @Test
    fun `per dimension overrides combine with task inference`() {
        val input = SelectionInput(flavors = mapOf("store" to "foss"), taskNames = listOf(":androidApp:assembleFreePlayRelease"))
        assertEquals("freeFossRelease", twoDimensions().resolve(input).name)
    }

    @Test
    fun `task names infer the variant`() {
        val model = twoDimensions()
        assertEquals("freeFossRelease", model.resolve(SelectionInput(taskNames = listOf(":androidApp:assembleFreeFossRelease"))).name)
        assertEquals("freePlayDebug", model.resolve(SelectionInput(taskNames = listOf("compileFreePlayDebugKotlinAndroid"))).name)
        assertEquals("paidPlayRelease", model.resolve(SelectionInput(taskNames = listOf("paidPlayReleaseClasses"))).name)
        assertNull(model.inferFromTasks(listOf("assembleDebug", "build", ":composeApp:linkDebugFrameworkIosArm64")))
    }

    @Test
    fun `task inference respects camel case boundaries`() {
        val model =
            VariantModel(
                dimensions = listOf("env"),
                flavors = listOf(FlavorDef("dev", null, false), FlavorDef("devX", null, false)),
                buildTypes = buildTypes,
            )
        assertEquals("devXDebug", model.inferFromTasks(listOf("assembleDevXDebug"))?.name)
        assertEquals("devDebug", model.inferFromTasks(listOf("assembleDevDebugUnitTest"))?.name)
        assertNull(model.inferFromTasks(listOf("assembleDevDebugger")))
    }

    @Test
    fun `tasks for different variants in one build fail`() {
        val e =
            assertFailsWith<GradleException> {
                twoDimensions().resolve(SelectionInput(taskNames = listOf("assembleFreePlayDebug", "assemblePaidPlayDebug")))
            }
        assertTrue("one variant per build" in e.message!!)
    }

    @Test
    fun `without flavors the build type is the variant and task names infer it`() {
        val model = VariantModel(emptyList(), emptyList(), buildTypes)
        assertEquals(listOf("debug", "release"), model.variants.map { it.name })
        assertEquals("release", model.resolve(SelectionInput(taskNames = listOf(":composeApp:linkReleaseFrameworkIosArm64"))).name)
        assertEquals(listOf("release"), model.variants[1].components)
    }

    @Test
    fun `ide selection replaces defaults but not anything chosen for the build`() {
        val model = twoDimensions()
        assertEquals("freePlayRelease", model.resolve(SelectionInput(ideVariant = "freePlayRelease")).name)
        assertEquals("freePlayDebug", model.resolve(SelectionInput(ideVariant = "freePlayRelease", buildType = "debug")).name)
        assertEquals(
            "paidFossDebug",
            model.resolve(SelectionInput(ideVariant = "freePlayRelease", taskNames = listOf("assemblePaidFossDebug"))).name,
        )
        assertEquals("paidFossDebug", model.resolve(SelectionInput(ideVariant = "renamedLongAgo")).name)
    }

    @Test
    fun `xcode configuration picks the build type`() {
        assertEquals("paidFossRelease", twoDimensions().resolve(SelectionInput(xcodeConfiguration = "Release")).name)
    }

    @Test
    fun `variant filter removes variants and rejects selecting them`() {
        val model = twoDimensions { if (it.flavors["tier"] == "paid" && it.flavors["store"] == "foss") it.ignore = true }
        assertEquals(6, model.variants.size)
        assertFailsWith<GradleException> { model.resolve(SelectionInput(variant = "paidFossDebug")) }
        assertFailsWith<GradleException> { model.resolve(SelectionInput(flavors = mapOf("store" to "foss", "tier" to "paid"))) }
    }

    @Test
    fun `single dimension does not need explicit dimension on flavors`() {
        val model = VariantModel(listOf("env"), listOf(FlavorDef("dev", null, false), FlavorDef("prod", null, false)), buildTypes)
        assertEquals(listOf("devDebug", "devRelease", "prodDebug", "prodRelease"), model.variants.map { it.name })
        assertEquals(listOf("dev", "debug", "devDebug"), model.variants.first().components)
    }

    @Test
    fun `invalid definitions fail`() {
        assertFailsWith<GradleException> { VariantModel(emptyList(), listOf(FlavorDef("a", null, false)), buildTypes) }
        assertFailsWith<GradleException> {
            VariantModel(listOf("x", "y"), listOf(FlavorDef("a", null, false), FlavorDef("b", "y", false)), buildTypes)
        }
        assertFailsWith<GradleException> { VariantModel(listOf("x"), listOf(FlavorDef("debug", "x", false)), buildTypes) }
        assertFailsWith<GradleException> { VariantModel(listOf("x", "y"), listOf(FlavorDef("a", "x", false)), buildTypes) }
        assertFailsWith<GradleException> { VariantModel(listOf("x"), listOf(FlavorDef("main", "x", false)), buildTypes) }
        assertFailsWith<GradleException> { twoDimensions().resolve(SelectionInput(flavors = mapOf("tier" to "gold"))) }
        assertFailsWith<GradleException> { twoDimensions().resolve(SelectionInput(buildType = "staging")) }
    }

    @Test
    fun `flavor source set names`() {
        assertEquals("commonFreeMain", flavorSourceSetName("commonMain", "free"))
        assertEquals("iosPaidDebugTest", flavorSourceSetName("iosTest", "paidDebug"))
        assertEquals("androidHostFreeTest", flavorSourceSetName("androidHostTest", "free"))
        assertEquals("free", flavorSourceSetName("main", "free"))
        assertEquals("testFreeDebug", flavorSourceSetName("test", "freeDebug"))
    }
}
