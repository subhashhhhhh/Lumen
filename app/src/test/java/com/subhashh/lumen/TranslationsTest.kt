package com.subhashh.lumen

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every translation (from Weblate) against the English text. A placeholder that's missing, extra
 * or of another type crashes the app or shows the wrong number the moment the text is shown, in
 * that language only, so nobody testing in English would see it.
 */
class TranslationsTest {

    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    /** name → quantity ("" for a plain string) → text */
    private fun load(file: File): Map<String, Map<String, String>> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val out = LinkedHashMap<String, Map<String, String>>()
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val name = el.getAttribute("name")
            when (el.tagName) {
                "string" -> out[name] = mapOf("" to el.textContent)
                "plurals" -> {
                    val items = el.getElementsByTagName("item")
                    out[name] = (0 until items.length).associate { j ->
                        val item = items.item(j) as Element
                        item.getAttribute("quantity") to item.textContent
                    }
                }
            }
        }
        return out
    }

    /** Each placeholder as "index:type" ("1:s", "2:d"); "%%" isn't one. Flags (",", "-", width) are left out. */
    private fun placeholders(text: String): Set<String> {
        var next = 1
        return Regex("""%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])""").findAll(text)
            .filter { it.groupValues[2] != "%" }
            .map { (it.groupValues[1].ifEmpty { (next++).toString() }) + ":" + it.groupValues[2].lowercase() }
            .toSet()
    }

    private val english by lazy { load(File(res, "values/strings.xml")) }

    private val translations by lazy {
        res.listFiles()!!.filter { it.name.startsWith("values-") && File(it, "strings.xml").isFile }
            .associate { it.name.removePrefix("values-") to load(File(it, "strings.xml")) }
    }

    @Test
    fun thereAreTranslationsToCheck() {
        assertTrue("found ${translations.keys}", translations.keys.containsAll(listOf("es", "fr")))
        assertTrue(english.size > 500)
    }

    @Test
    fun everyTranslatedTextExistsInEnglish() {
        val stray = translations.flatMap { (lang, texts) -> texts.keys.filter { it !in english }.map { "$lang/$it" } }
        assertTrue("translated but no longer in English: $stray", stray.isEmpty())
    }

    /**
     * Android's per-app language list (Settings → About → Language) is generated from every
     * values-xx folder (generateLocaleConfig). A folder with nothing translated in it offers a
     * language that, once picked, shows Lumen all in English.
     */
    @Test
    fun everyLanguageOfferedHasSomethingTranslated() {
        val folders = res.listFiles()!!
            .filter { it.isDirectory && Regex("""values-[a-z]{2,3}(-r[A-Z]{2})?""").matches(it.name) }
            .map { it.name.removePrefix("values-") }
        val empty = folders.filter { translations[it].isNullOrEmpty() }
        assertTrue("offered in the language list but nothing translated: $empty", empty.isEmpty())
    }

    @Test
    fun namesAndBrandsStayUntranslated() {
        val fixed = Regex("""name="([^"]+)"[^>]*translatable="false"""")
            .findAll(File(res, "values/strings.xml").readText()).map { it.groupValues[1] }.toSet()
        assertTrue(fixed.isNotEmpty())
        val wrong = translations.flatMap { (lang, texts) -> texts.keys.filter { it in fixed }.map { "$lang/$it" } }
        assertTrue("translated though marked translatable=\"false\": $wrong", wrong.isEmpty())
    }

    @Test
    fun aTextIsAStringOrAPluralLikeInEnglish() {
        val wrong = translations.flatMap { (lang, texts) ->
            texts.filter { (name, forms) -> english[name] != null && ("" in forms) != ("" in english.getValue(name)) }
                .map { "$lang/${it.key}" }
        }
        assertTrue("string vs plurals mismatch: $wrong", wrong.isEmpty())
    }

    @Test
    fun stringsHaveExactlyTheEnglishPlaceholders() {
        val wrong = translations.flatMap { (lang, texts) ->
            texts.mapNotNull { (name, forms) ->
                val text = forms[""] ?: return@mapNotNull null
                val want = placeholders(english[name]?.get("") ?: return@mapNotNull null)
                val got = placeholders(text)
                if (got != want) "$lang/$name: $got, English $want" else null
            }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun pluralsHaveTheEnglishPlaceholders() {
        // Every form may use the English "other" form's placeholders, and no others; the "other"
        // form (used for most numbers) must have them all. A "one" may write the number as a word.
        val wrong = translations.flatMap { (lang, texts) ->
            texts.flatMap { (name, forms) ->
                val englishForms = english[name] ?: return@flatMap emptyList()
                if ("" in forms || "" in englishForms) return@flatMap emptyList()
                val want = placeholders(englishForms["other"] ?: return@flatMap emptyList())
                forms.mapNotNull { (quantity, text) ->
                    val got = placeholders(text)
                    when {
                        !want.containsAll(got) -> "$lang/$name[$quantity]: $got, English $want"
                        quantity == "other" && got != want -> "$lang/$name[other]: $got, English $want"
                        else -> null
                    }
                }
            }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun everyPluralHasAnOtherForm() {
        val missing = translations.flatMap { (lang, texts) ->
            texts.filter { (_, forms) -> "" !in forms && "other" !in forms }.map { "$lang/${it.key}" }
        }
        assertTrue("plurals without \"other\": $missing", missing.isEmpty())
    }

    @Test
    fun theCheckerItselfCatchesMistakes() {
        assertTrue(placeholders("Moved %1\$d items to %2\$s") == setOf("1:d", "2:s"))
        assertTrue(placeholders("%1\$,d Results") == setOf("1:d"))
        assertTrue(placeholders("100%% done, %s left") == setOf("1:s"))
        assertTrue(placeholders("%d of %d") == setOf("1:d", "2:d"))
        assertTrue(placeholders("Déplacé %1\$s vers %2\$d") != placeholders("Moved %1\$d items to %2\$s"))
    }
}
