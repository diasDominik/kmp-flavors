package uk.dominikdias.kmpflavors.manifest

import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.MergingReport
import com.android.utils.StdLogger
import org.gradle.api.GradleException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Android's manifest merger, applied like AGP applies flavor overlays to a library: overlays come
 * lowest priority first, placeholders stay for the consuming app, tools declarations are kept.
 * Only loaded inside the worker, whose classloader has `manifest-merger`.
 */
internal object ManifestOverlayMerger {
    fun merge(
        main: File,
        overlays: List<File>,
        output: File,
    ) {
        // AGP 9 writes minSdk into the merged manifest as <uses-sdk>, which the merger rejects in a main
        // manifest. Set it aside and put it back unchanged.
        val mainDocument = parse(main.readText())
        val usesSdk = mainDocument.documentElement.directChildren("uses-sdk")
        usesSdk.forEach { mainDocument.documentElement.removeChild(it) }
        val mainWithoutSdk = File.createTempFile("kmp-flavors-main", ".xml").apply { writeText(serialize(mainDocument)) }

        try {
            val report =
                ManifestMerger2
                    .newMerger(mainWithoutSdk, StdLogger(StdLogger.Level.WARNING), ManifestMerger2.MergeType.LIBRARY)
                    .withFeatures(
                        ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT,
                        ManifestMerger2.Invoker.Feature.SUPPRESS_MANIFEST_PACKAGE_WARNING,
                        ManifestMerger2.Invoker.Feature.SKIP_BLAME,
                    ).apply {
                        // The merger gives earlier overlays priority (as AGP passes variant, build type,
                        // then flavors); [overlays] come lowest priority first.
                        overlays.asReversed().forEach { addFlavorAndBuildTypeManifest(it) }
                    }.merge()
            if (report.result.isError) {
                throw GradleException(
                    "kmp-flavors: merging manifest overlays failed:\n" +
                        report.loggingRecords.joinToString("\n") { it.toString() },
                )
            }
            val merged =
                parse(
                    report.getMergedDocument(MergingReport.MergedManifestKind.MERGED)
                        ?: throw GradleException("kmp-flavors: the manifest merger produced no document."),
                )
            val root = merged.documentElement
            usesSdk.asReversed().forEach { root.insertBefore(merged.importNode(it, true), root.firstChild) }
            output.parentFile.mkdirs()
            output.writeText(serialize(merged))
        } finally {
            mainWithoutSdk.delete()
        }
    }

    private fun Element.directChildren(tag: String): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == tag }

    private fun parse(xml: String): Document =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                isNamespaceAware = true
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }.newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))

    private fun serialize(document: Document): String =
        StringWriter()
            .also {
                TransformerFactory
                    .newInstance()
                    .newTransformer()
                    .apply {
                        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                    }.transform(DOMSource(document), StreamResult(it))
            }.toString()
}
