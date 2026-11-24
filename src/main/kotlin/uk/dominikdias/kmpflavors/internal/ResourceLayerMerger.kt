package uk.dominikdias.kmpflavors.internal

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Merges resource directories the way Android merges source sets: layers come lowest priority
 * first and a higher layer replaces what a lower one defines.
 */
internal object ResourceLayerMerger {
    enum class Mode {
        /**
         * Compose Multiplatform `composeResources`: values (`<string>`, `<plurals>`, `<string-array>`)
         * by type and name per `values*` directory; `drawable*` / `font*` files by name whatever the
         * extension; `files/` by path.
         */
        COMPOSE,

        /** Android `res/`: values by resource type and name per `values*` directory, files by name per directory. */
        ANDROID_RES,

        /** Plain files (Android `assets/`): by relative path. */
        FILES,
    }

    data class Layer(
        val label: String,
        val directory: File,
    )

    fun merge(
        layers: List<Layer>,
        output: File,
        mode: Mode,
    ) {
        output.deleteRecursively()
        output.mkdirs()
        val existing = layers.filter { it.directory.isDirectory }
        mergeFiles(existing, output, mode)
        if (mode != Mode.FILES) mergeValues(existing, output, mode)
    }

    private fun mergeFiles(
        layers: List<Layer>,
        output: File,
        mode: Mode,
    ) {
        val winners = linkedMapOf<String, Pair<File, String>>()
        layers.forEach { layer ->
            layer.directory
                .walkTopDown()
                .filter { it.isFile && !it.name.startsWith(".") }
                .forEach { file ->
                    val relative = file.relativeTo(layer.directory).invariantSeparatorsPath
                    val top = relative.substringBefore('/')
                    if (mode != Mode.FILES && top.startsWith("values") && file.extension == "xml") return@forEach
                    winners[fileKey(relative, top, file, mode)] = file to relative
                }
        }
        winners.values.forEach { (file, relative) -> file.copyTo(output.resolve(relative), overwrite = true) }
    }

    private fun fileKey(
        relative: String,
        top: String,
        file: File,
        mode: Mode,
    ): String {
        val directChild = relative.count { it == '/' } == 1
        return when {
            mode == Mode.FILES || !directChild -> relative

            mode == Mode.COMPOSE && (top == "files" || top.startsWith("files-")) -> relative

            // `icon.9.png` and `icon.xml` are the same resource.
            else -> "$top/${file.nameWithoutExtension.removeSuffix(".9")}"
        }
    }

    private class ValuesFile(
        val layerIndex: Int,
        val label: String,
        val file: File,
        val qualifier: String,
        val document: Document,
    )

    private fun mergeValues(
        layers: List<Layer>,
        output: File,
        mode: Mode,
    ) {
        val files =
            layers.flatMapIndexed { index, layer ->
                layer.directory
                    .listFiles { dir -> dir.isDirectory && dir.name.startsWith("values") }
                    .orEmpty()
                    .sortedBy { it.name }
                    .flatMap { qualifierDir ->
                        qualifierDir
                            .listFiles { f -> f.isFile && f.extension == "xml" }
                            .orEmpty()
                            .sortedBy { it.name }
                            .map { ValuesFile(index, layer.label, it, qualifierDir.name, parse(it)) }
                    }
            }

        // Last (highest-priority) definition of each value wins.
        val owner = hashMapOf<String, ValuesFile>()
        files.forEach { values -> values.document.valueElements().forEach { owner[values.keyOf(it, mode)] = values } }

        files.forEach { values ->
            val elements = values.document.valueElements()
            val overridden = elements.filter { owner[values.keyOf(it, mode)] !== values }
            if (overridden.size == elements.size && elements.isNotEmpty()) return@forEach
            // Android only accepts [a-z0-9_.] in resource file names.
            val name =
                if (values.layerIndex == 0) {
                    values.file.name
                } else {
                    "${values.file.nameWithoutExtension}_${values.label.toScreamingSnake().lowercase()}.xml"
                }
            val target = output.resolve(values.qualifier).resolve(name).apply { parentFile.mkdirs() }
            if (overridden.isEmpty()) {
                values.file.copyTo(target, overwrite = true)
            } else {
                overridden.forEach { it.parentNode.removeChild(it) }
                write(values.document, target)
            }
        }
    }

    private fun ValuesFile.keyOf(
        element: Element,
        mode: Mode,
    ): String {
        val type =
            if (mode == Mode.ANDROID_RES) {
                when (val tag = element.tagName) {
                    "item" -> element.getAttribute("type").ifEmpty { tag }
                    "string-array", "integer-array" -> "array"
                    "declare-styleable" -> "styleable"
                    else -> tag
                }
            } else {
                element.tagName
            }
        return "$qualifier|$type|${element.getAttribute("name")}"
    }

    private fun Document.valueElements(): List<Element> {
        val children = documentElement.childNodes
        return (0 until children.length).mapNotNull { children.item(it) as? Element }.filter { it.hasAttribute("name") }
    }

    private val documentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }

    private fun parse(file: File): Document = documentBuilderFactory.newDocumentBuilder().parse(file)

    private fun write(
        document: Document,
        target: File,
    ) {
        val transformer =
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(OutputKeys.INDENT, "no")
            }
        target.outputStream().use { transformer.transform(DOMSource(document), StreamResult(it)) }
    }
}
