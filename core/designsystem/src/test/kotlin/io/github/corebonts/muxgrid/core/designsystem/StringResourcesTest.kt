package io.github.corebonts.muxgrid.core.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourcesTest {

    private data class ParsedResources(
        val strings: Map<String, String>,
        val nonTranslatable: Set<String>,
        val plurals: Map<String, Map<String, String>>,
    )

    private val formatSpecRegex = Regex("%(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])")

    private fun resDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("core/designsystem/src/main/res"),
        )
        return candidates.first { it.isDirectory }
    }

    private fun parseStringsXml(file: File): ParsedResources {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        doc.documentElement.normalize()

        val strings = LinkedHashMap<String, String>()
        val nonTranslatable = HashSet<String>()
        val plurals = LinkedHashMap<String, Map<String, String>>()

        val children = doc.documentElement.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> {
                    assertTrue(
                        strings.put(name, node.textContent) == null,
                        "Duplicate <string name=\"$name\"> in ${file.path}",
                    )
                    if (node.getAttribute("translatable") == "false") {
                        nonTranslatable += name
                    }
                }
                "plurals" -> {
                    val items = LinkedHashMap<String, String>()
                    val itemNodes = node.getElementsByTagName("item")
                    for (j in 0 until itemNodes.length) {
                        val item = itemNodes.item(j) as Element
                        val qty = item.getAttribute("quantity")
                        assertTrue(
                            items.put(qty, item.textContent) == null,
                            "Duplicate <item quantity=\"$qty\"> in <plurals name=\"$name\"> (${file.path})",
                        )
                    }
                    assertTrue(
                        plurals.put(name, items) == null,
                        "Duplicate <plurals name=\"$name\"> in ${file.path}",
                    )
                }
            }
        }
        return ParsedResources(strings, nonTranslatable, plurals)
    }

    private fun extractPlaceholders(value: String): List<Pair<Int?, String>> =
        formatSpecRegex.findAll(value)
            .filter { it.groupValues[2] != "%" }
            .map { match ->
                val idx = match.groupValues[1].toIntOrNull()
                val type = match.groupValues[2]
                idx to type
            }
            .toList()

    @Test
    fun `default strings xml uses positional format specifiers for multi-arg strings`() {
        val defaultFile = File(resDir(), "values/strings.xml")
        assertTrue(defaultFile.isFile, "Missing canonical values/strings.xml")
        val base = parseStringsXml(defaultFile)
        assertTrue(base.strings.isNotEmpty(), "Canonical values/strings.xml must not be empty")

        for ((name, value) in base.strings) {
            val specs = extractPlaceholders(value)
            if (specs.size >= 2) {
                assertTrue(
                    specs.all { it.first != null },
                    "Multi-arg string '$name' must use positional format specifiers (e.g. %1\$s): $value",
                )
            }
        }
    }

    @Test
    fun `all locale translations match default keys and format specifiers`() {
        val root = resDir()
        val base = parseStringsXml(File(root, "values/strings.xml"))
        val localeDirs = root.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()
        assertTrue(localeDirs.isNotEmpty(), "Expected at least one locale directory (e.g. values-hu)")

        for (dir in localeDirs) {
            val file = File(dir, "strings.xml")
            if (!file.isFile) continue
            val translated = parseStringsXml(file)

            for ((name, value) in translated.strings) {
                assertTrue(
                    name !in base.nonTranslatable,
                    "Non-translatable string '$name' must not appear in ${file.path}",
                )
                val baseValue = base.strings[name]
                assertTrue(
                    baseValue != null,
                    "Extra/unknown string '$name' in ${file.path} not present in values/strings.xml",
                )
                val baseSpecs = extractPlaceholders(baseValue!!).toSet()
                val transSpecs = extractPlaceholders(value).toSet()
                assertEquals(
                    baseSpecs,
                    transSpecs,
                    "Format specifier mismatch for '$name' in ${file.path}",
                )
            }

            for ((name, items) in translated.plurals) {
                val baseItems = base.plurals[name]
                assertTrue(
                    baseItems != null,
                    "Extra/unknown plural '$name' in ${file.path} not present in values/strings.xml",
                )
                val expectedSpecs = extractPlaceholders(baseItems!!.values.first()).toSet()
                for ((qty, value) in items) {
                    assertEquals(
                        expectedSpecs,
                        extractPlaceholders(value).toSet(),
                        "Format specifier mismatch for plural '$name'[$qty] in ${file.path}",
                    )
                }
            }
        }

        // Hungarian is the reference non-English locale and must remain 100% translated.
        val hu = parseStringsXml(File(root, "values-hu/strings.xml"))
        val expectedKeys = base.strings.keys - base.nonTranslatable
        assertEquals(
            expectedKeys,
            hu.strings.keys,
            "values-hu/strings.xml should translate every translatable string key",
        )
        assertEquals(
            base.plurals.keys,
            hu.plurals.keys,
            "values-hu/strings.xml should translate every plurals key",
        )
    }

    @Test
    fun `AppLocaleManager normalizes regional tags to supported locales`() {
        assertEquals("", AppLocaleManager.normalizeToSupportedTag(""))
        assertEquals("en", AppLocaleManager.normalizeToSupportedTag("en-US"))
        assertEquals("hu", AppLocaleManager.normalizeToSupportedTag("hu-HU"))
        assertEquals("hu", AppLocaleManager.normalizeToSupportedTag("hu"))
        assertEquals("", AppLocaleManager.normalizeToSupportedTag("de-DE"))
    }
}
