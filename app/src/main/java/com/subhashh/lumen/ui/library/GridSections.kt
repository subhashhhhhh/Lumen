package com.subhashh.lumen.ui.library

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.subhashh.lumen.R
import com.subhashh.lumen.data.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Settings → Library → Section Headers. [labelRes] is [label] as a string resource. */
enum class SectionMode(@StringRes val labelRes: Int) {
    None(R.string.main_section_none),
    Months(R.string.main_section_months),
    Days(R.string.main_section_days),
}

/** One cell of a sectioned grid: a date header spanning the row, or a photo. */
@Immutable
sealed interface GridEntry {
    data class Header(val label: String, val key: Long) : GridEntry
    data class Cell(val item: MediaItem, val itemIndex: Int) : GridEntry
}

/**
 * A photo grid with date headers mixed in. Callers think in item indexes (the viewer, Years /
 * Months, "open at the newest"); the grid thinks in its own, headers included. [gridIndexOf]
 * translates.
 */
@Immutable
class GridSections(val entries: List<GridEntry>, private val gridIndex: IntArray) {
    fun gridIndexOf(itemIndex: Int): Int = gridIndex.getOrElse(itemIndex) { itemIndex }

    /** The photo at a grid index, or the first one after a header. */
    fun itemAt(gridIndex: Int): MediaItem? {
        val i = gridIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        // A header is always followed by at least one photo.
        return (entries.getOrNull(i) as? GridEntry.Cell ?: entries.getOrNull(i + 1) as? GridEntry.Cell)?.item
    }

    companion object {
        /** [context] supplies the localized "Today" / "Yesterday" for day headers. */
        fun of(context: Context, items: List<MediaItem>, mode: SectionMode): GridSections {
            if (mode == SectionMode.None) {
                return GridSections(items.mapIndexed { i, item -> GridEntry.Cell(item, i) }, IntArray(items.size) { it })
            }
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val formats = Formats(context)
            val entries = ArrayList<GridEntry>(items.size + items.size / 8)
            val gridIndex = IntArray(items.size)
            var lastKey = Long.MIN_VALUE
            items.forEachIndexed { i, item ->
                val date = Instant.ofEpochMilli(item.takenAt).atZone(zone).toLocalDate()
                val key = if (mode == SectionMode.Days) date.toEpochDay() else date.year * 12L + date.monthValue
                if (key != lastKey) {
                    entries += GridEntry.Header(formats.label(date, mode, today), key)
                    lastKey = key
                }
                gridIndex[i] = entries.size
                entries += GridEntry.Cell(item, i)
            }
            return GridSections(entries, gridIndex)
        }

        /** Header formats in the current locale: "October 2026", "Sun, 25 Oct" (or that locale's order). */
        private class Formats(context: Context) {
            private val locale = Locale.getDefault()
            private val monthFormat = pattern("MMMMyyyy")
            private val dayFormat = pattern("EEEdMMM")
            private val dayYearFormat = pattern("EEEdMMMyyyy")
            private val todayLabel = context.getString(R.string.main_today)
            private val yesterdayLabel = context.getString(R.string.main_yesterday)

            private fun pattern(skeleton: String) =
                DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

            fun label(date: LocalDate, mode: SectionMode, today: LocalDate): String = when {
                mode == SectionMode.Months -> if (date.year == today.year && date.month == today.month) {
                    date.month.getDisplayName(TextStyle.FULL, locale)
                } else {
                    monthFormat.format(date)
                }
                date == today -> todayLabel
                date == today.minusDays(1) -> yesterdayLabel
                date.year == today.year -> dayFormat.format(date)
                else -> dayYearFormat.format(date)
            }
        }
    }
}
