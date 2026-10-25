package com.subhashh.lumen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Just enough Markdown for Lumen's release notes: `##` headings, `-` bullets, paragraphs,
 * **bold** and `code`. Tables and code blocks aren't drawn (the notes' download table is cut
 * before this; see [releaseChanges]).
 */
@Composable
fun Markdown(text: String, color: Color, headingColor: Color, modifier: Modifier = Modifier) {
    val blocks = remember(text) { blocks(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) when (block) {
            is Block.Heading -> Text(
                block.text, color = headingColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
            is Block.Bullet -> Row {
                Text("•", color = color, fontSize = 14.sp, modifier = Modifier.width(14.dp))
                Text(inline(block.text), color = color, fontSize = 14.sp, lineHeight = 19.sp)
            }
            is Block.Paragraph -> Text(inline(block.text), color = color, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

/** A release's notes up to the "Which file to download" part: what's new, faster and fixed. */
fun releaseChanges(notes: String): String {
    val cut = Regex("^## Which file", RegexOption.MULTILINE).find(notes)?.range?.first ?: notes.length
    return notes.substring(0, cut).trim()
}

private sealed interface Block {
    class Heading(val text: String) : Block
    class Bullet(val text: String) : Block
    class Paragraph(val text: String) : Block
}

private fun blocks(text: String): List<Block> {
    val out = ArrayList<Block>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) out += Block.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    var inCode = false
    for (raw in text.lines()) {
        val line = raw.trim()
        when {
            line.startsWith("```") -> { flush(); inCode = !inCode }
            inCode || line.startsWith("|") -> Unit
            line.isEmpty() -> flush()
            line.startsWith("#") -> { flush(); out += Block.Heading(line.trimStart('#').trim()) }
            line.startsWith("- ") || line.startsWith("* ") -> { flush(); out += Block.Bullet(line.drop(2).trim()) }
            else -> paragraph.append(' ').append(line)
        }
    }
    flush()
    return out
}

/** **bold** and `code`; everything else as written. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end < 0) { append(text.substring(i)); break }
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 2, end)) }
                i = end + 2
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end < 0) { append(text.substring(i)); break }
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) { append(text.substring(i + 1, end)) }
                i = end + 1
            }
            else -> {
                val next = listOf(text.indexOf("**", i), text.indexOf('`', i)).filter { it >= 0 }.minOrNull() ?: text.length
                append(text.substring(i, next))
                i = next
            }
        }
    }
}
