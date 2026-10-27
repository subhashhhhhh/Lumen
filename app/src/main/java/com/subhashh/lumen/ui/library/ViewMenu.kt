package com.subhashh.lumen.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.subhashh.lumen.R
import com.subhashh.lumen.data.LibraryFilter
import com.subhashh.lumen.data.LibrarySort
import com.subhashh.lumen.ui.components.MenuItem

/**
 * Show, Sort and (for the Library) Grid, each one row with its current choice that opens its own
 * list, like iOS's nested menus: three rows instead of twelve. The Library's ≡ menu and a person's
 * ⋯ menu use them.
 */
@Composable
fun viewMenuItems(
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
    sort: LibrarySort,
    descending: Boolean,
    onSort: (LibrarySort, descending: Boolean) -> Unit,
    aspectRatioGrid: Boolean? = null,
    onAspectRatioGrid: (Boolean) -> Unit = {},
): List<MenuItem> = buildList {
    add(
        MenuItem(
            stringResource(R.string.main_view_filter),
            value = stringResource(filter.label),
            submenu = LibraryFilter.entries.map { option ->
                MenuItem(stringResource(option.label), checked = option == filter) { onFilter(option) }
            },
        ),
    )
    val sortLabels = LibrarySort.entries.associateWith { stringResource(it.label) }
    add(
        MenuItem(
            stringResource(R.string.main_view_sort),
            value = sortLabels.getValue(sort),
            submenu = LibrarySort.entries.map { option ->
                // A new sort starts in its natural direction; the current one keeps its own.
                MenuItem(sortLabels.getValue(option), checked = option == sort) {
                    onSort(option, if (option == sort) descending else option.defaultDescending)
                }
            } + listOf(
                MenuItem(stringResource(sort.ascendingLabel), checked = !descending, startsGroup = true) { onSort(sort, false) },
                MenuItem(stringResource(sort.descendingLabel), checked = descending) { onSort(sort, true) },
            ),
        ),
    )
    if (aspectRatioGrid != null) {
        val square = stringResource(R.string.main_view_square)
        val aspect = stringResource(R.string.main_view_aspect_ratio)
        add(
            MenuItem(
                stringResource(R.string.main_view_grid),
                value = if (aspectRatioGrid) aspect else square,
                submenu = listOf(
                    MenuItem(square, checked = !aspectRatioGrid) { onAspectRatioGrid(false) },
                    MenuItem(aspect, checked = aspectRatioGrid) { onAspectRatioGrid(true) },
                ),
            ),
        )
    }
}
