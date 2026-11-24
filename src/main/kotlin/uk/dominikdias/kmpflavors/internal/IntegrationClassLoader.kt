package uk.dominikdias.kmpflavors.internal

import org.gradle.api.Project
import uk.dominikdias.kmpflavors.KmpFlavorsExtension
import uk.dominikdias.kmpflavors.KmpFlavorsSettingsExtension

/** Code that touches Kotlin, Android or Compose Gradle plugin types; lives in the `integration` package. */
internal fun interface ProjectIntegration {
    fun apply(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
        extension: KmpFlavorsExtension,
    )
}

/**
 * The settings plugin, and therefore this plugin, is loaded by the settings classloader, which cannot
 * see plugins that modules apply later (KGP, AGP, Compose). Classes of the `integration` package are
 * therefore defined again in this loader, which resolves everything else from this plugin's own
 * loader first (so shared types such as [KmpFlavorsSettingsExtension] stay identical) and then from
 * the classloaders of the plugins the module applied.
 */
internal class IntegrationClassLoader(
    private val own: ClassLoader,
    private val plugins: List<ClassLoader>,
) : ClassLoader(null) {
    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> =
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)
                ?: if (name.startsWith(INTEGRATION_PACKAGE)) {
                    val bytes =
                        own.getResourceAsStream(name.replace('.', '/') + ".class")?.use { it.readBytes() }
                            ?: throw ClassNotFoundException(name)
                    defineClass(name, bytes, 0, bytes.size)
                } else {
                    (listOf(own) + plugins).firstNotNullOfOrNull { loader ->
                        try {
                            loader.loadClass(name)
                        } catch (_: ClassNotFoundException) {
                            null
                        } catch (_: LinkageError) {
                            null
                        }
                    } ?: throw ClassNotFoundException(name)
                }
        }

    override fun getResource(name: String) = (listOf(own) + plugins).firstNotNullOfOrNull { it.getResource(name) }

    companion object {
        const val INTEGRATION_PACKAGE = "uk.dominikdias.kmpflavors.integration."

        /** Instantiates an integration with the classloaders of the plugins currently applied to [project]. */
        fun create(
            project: Project,
            settings: KmpFlavorsSettingsExtension,
            simpleName: String,
        ): ProjectIntegration {
            val own = IntegrationClassLoader::class.java.classLoader
            val plugins =
                project.plugins
                    .map { it.javaClass.classLoader }
                    .filter { it != null && it != own }
                    .distinct()
            val loader = settings.integrationLoaders.getOrPut(plugins) { IntegrationClassLoader(own, plugins) }
            return loader
                .loadClass(INTEGRATION_PACKAGE + simpleName)
                .getDeclaredConstructor()
                .newInstance() as ProjectIntegration
        }
    }
}
