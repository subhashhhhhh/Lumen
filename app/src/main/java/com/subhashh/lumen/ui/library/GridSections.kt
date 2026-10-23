package com.subhashh.lumen.ui.library

import androidx.compose.runtime.Immutable
import com.subhashh.lumen.data.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Settings → Library → Section Headers. */
enum class SectionMode(val label: String) { None("None"), Months("Months"), Days("Days") }

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
        fun of(items: List<MediaItem>, mode: SectionMode): GridSections {
            if (mode == SectionMode.None) {
                return GridSections(items.mapIndexed { i, item -> GridEntry.Cell(item, i) }, IntArray(items.size) { it })
            }
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val entries = ArrayList<GridEntry>(items.size + items.size / 8)
            val gridIndex = IntArray(items.size)
            var lastKey = Long.MIN_VALUE
            items.forEachIndexed { i, item ->
                val date = Instant.ofEpochMilli(item.takenAt).atZone(zone).toLocalDate()
                val key = if (mode == SectionMode.Days) date.toEpochDay() else date.year * 12L + date.monthValue
                if (key != lastKey) {
                    entries += GridEntry.Header(label(date, mode, today), key)
                    lastKey = key
                }
                gridIndex[i] = entries.size
                entries += GridEntry.Cell(item, i)
            }
            return GridSections(entries, gridIndex)
        }

        private val MonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        private val DayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
        private val DayYearFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

        private fun label(date: LocalDate, mode: SectionMode, today: LocalDate): String = when {
            mode == SectionMode.Months -> if (date.year == today.year && date.month == today.month) {
                date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            } else {
                MonthFormat.format(date)
            }
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> DayFormat.format(date)
            else -> DayYearFormat.format(date)
        }
    }
}
