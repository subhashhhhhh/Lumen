package com.subhashh.lumen

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every English string is used somewhere: translators on Weblate translate whatever is in
 * values/strings.xml, and before 6738a6d they had translated 20 strings nothing showed.
 *
 * Used means named as R.string.x / R.plurals.x (Kotlin, Java) or @string/x / @plurals/x (XML:
 * manifest, layouts, styles, other values files) anywhere in the app's own sources, every build
 * type. Lumen Lab doesn't count: it isn't in releases (nor on the server or GitHub), so a string only
 * Lab uses is dead text to translators and belongs in Lab. Nothing looks strings up by name
 * (getIdentifier), so file scanning is the whole picture. Plain JVM: runs on the build server.
 */
class UnusedStringsTest {

    private val app = listOf(File("."), File("app")).first { File(it, "src/main/res/values/strings.xml").isFile }
    private val sourceSets = listOf("main", "debug", "profile", "release").map { File(app, "src/$it") }.filter { it.isDirectory }

    /** The string and plurals names English defines. */
    private val defined: Set<String> by lazy {
        Regex("""<(?:string|plurals)\s+name="([^"]+)"""")
            .findAll(File(app, "src/main/res/values/strings.xml").readText())
            .map { it.groupValues[1] }.toSet()
    }

    /** A strings file of any language: where names are defined, not used. */
    private fun isStringsFile(file: File) = file.name == "strings.xml" && file.parentFile.name.startsWith("values")

    private val reference = Regex("""(?<![A-Za-z0-9_$])(?:R\.(?:string|plurals)\.|@(?:string|plurals)/)([A-Za-z0-9_]+)""")

    /** Every string name the app's sources refer to. */
    private val used: Set<String> by lazy {
        sourceSets.asSequence()
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.extension in setOf("kt", "java", "xml") && !isStringsFile(it) }
            .flatMap { file -> reference.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
    }

    @Test
    fun everyEnglishStringIsUsed() {
        // A scan that found nothing would pass vacuously: make sure it saw the app.
        assertTrue("only ${defined.size} strings defined: wrong folder?", defined.size > 300)
        assertTrue("only ${used.size} references found: wrong folder?", used.size > 300)
        val unused = (defined - used).sorted()
        assertTrue(
            "${unused.size} strings nothing uses; remove them from values/strings.xml and every translation:\n  " +
                unused.joinToString("\n  "),
            unused.isEmpty(),
        )
    }

    @Test
    fun theScanSeesEveryKindOfReference() {
        // The forms the app uses today, so a regex change can't quietly stop matching one.
        val sample = """
            stringResource(R.string.kotlin_one)
            com.subhashh.lumen.R.plurals.qualified
            android:label="@string/manifest_label"
            <item name="android:text">@plurals/xml_plural</item>
            R.string.with_digits_2
        """.trimIndent()
        val found = reference.findAll(sample).map { it.groupValues[1] }.toSet()
        assertTrue(found.toString(), found == setOf("kotlin_one", "qualified", "manifest_label", "xml_plural", "with_digits_2"))
        // Not a reference: another class's R, or a name that only starts the same.
        assertTrue(reference.findAll("myR.string.nope MyR.string.nope2 R.strings.nope3").none())
        assertTrue(reference.findAll("R.string.main_title_extra").single().groupValues[1] == "main_title_extra")
    }
}
