package com.subhashh.lumen.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import com.subhashh.lumen.ui.theme.Lumen
import com.subhashh.lumen.data.MediaThumbnail
import coil3.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.subhashh.lumen.R
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.subhashh.lumen.ui.selection.dragToSelect
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.ai.Person
import com.subhashh.lumen.ui.people.FaceAvatar
import com.subhashh.lumen.ui.collections.AlbumCard
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.images.ImageKeys
import com.subhashh.lumen.ui.library.Thumbnail
import com.subhashh.lumen.ui.library.ColumnSteps
import com.subhashh.lumen.ui.components.pinchColumns
import com.subhashh.lumen.ui.transition.PhotoTransition
import com.subhashh.lumen.ui.transition.transitionCell

/**
 * The order of the one list of results: best match first (exact names and dates, then people and
 * text, then Smart Search's best first), or by date. Tap the order beside the result count to
 * change it. Kept for as long as Lumen runs. Applied by [SearchSection.ordered] where the list is
 * shown and where the viewer pages through it, not in MainScaffold (JVM method size).
 */
enum class SearchOrder(@StringRes val label: Int) {
    Best(R.string.main_search_order_best),
    Newest(R.string.data_sort_newest),
    Oldest(R.string.data_sort_oldest);

    fun sorted(items: List<MediaItem>): List<MediaItem> = when (this) {
        Best -> items
        Newest -> items.sortedByDescending { it.takenAt }
        Oldest -> items.sortedBy { it.takenAt }
    }

    companion object {
        var current by mutableStateOf(Best)
    }
}

/** One way of matching a search, shown as its own section: one row until See All. */
data class SearchSection(
    val id: String,
    /** The section's heading; 0 for none (the one list). */
    @StringRes val title: Int,
    val items: List<MediaItem>,
    /** Smart Search's score per item, shown on the cells in development builds only. */
    val scores: Map<Long, Float> = emptyMap(),
) {
    /** The one list in the chosen [SearchOrder]; other sections as they are. */
    fun ordered(): SearchSection =
        if (id == All && SearchOrder.current != SearchOrder.Best) copy(items = SearchOrder.current.sorted(items)) else this

    companion object {
        const val Names = "names"
        const val People = "people"
        const val Text = "text"
        const val Smart = "smart"
        /** Everything in one list: the normal view, without Search Details. */
        const val All = "all"
    }
}

/** Sections with something to show; Smart Search's (or the one list) also while it's still looking. */
fun visibleSections(sections: List<SearchSection>, lookingInside: Boolean) =
    sections.filter { it.items.isNotEmpty() || (lookingInside && (it.id == SearchSection.Smart || it.id == SearchSection.All)) }

/** How many of a section's items are in the grid: one row, or all once expanded (the one list: all). */
fun shownCount(section: SearchSection, expanded: Set<String>, columns: Int) =
    if (section.id in expanded || section.id == SearchSection.All) section.items.size else minOf(columns, section.items.size)

/** Where [section]'s item [index] sits in [SearchScreen]'s grid, to scroll back to it. */
fun searchGridIndex(
    albums: Boolean,
    people: Boolean,
    sections: List<SearchSection>,
    lookingInside: Boolean,
    expanded: Set<String>,
    columns: Int,
    section: String,
    index: Int,
): Int {
    var at = (if (albums) 2 else 0) + (if (people) 2 else 0)
    for (s in visibleSections(sections, lookingInside)) {
        at++ // header
        if (s.id == section) return at + index
        at += shownCount(s, expanded, columns)
    }
    return 0
}

/**
 * Search results under the glass search field (which lives outside the page layer, see
 * LumenApp): matching albums and people in rows, then every matching photo and video in one
 * list. With Search Details (Developer Options), a section per way of matching instead (names
 * and dates, people, text in photos, Smart Search), each one row until See All opens it.
 * With nothing typed it offers a few starting points.
 */
@Composable
fun SearchScreen(
    query: String,
    /** null while the index is being built. */
    sections: List<SearchSection>?,
    /** Smart Search is still working on this query; its section fills in when it's done. */
    lookingInside: Boolean,
    /** Sections opened with See All. */
    expanded: Set<String>,
    onToggleSection: (id: String) -> Unit,
    /** The section the viewer pages through: only its cells take part in the zoom transition. */
    activeSection: String?,
    albums: List<MediaCollection>,
    /** People whose names match, each with their cover photo. */
    people: List<Pair<Person, MediaItem>>,
    /** A person, not yet named, Smart Search's matches are about: offered to be named. */
    unnamed: Pair<Person, MediaItem>?,
    onOpenPerson: (key: String) -> Unit,
    onNamePerson: (Person) -> Unit,
    /** Recent searches, newest first. */
    recent: List<String>,
    /** Smart Search's suggestions for this library, with each one's best photo. */
    suggested: List<Pair<String, MediaItem>>,
    /** Name/date starting points, for when there's no Smart Search. */
    suggestions: List<String>,
    onClearRecent: () -> Unit,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    transition: PhotoTransition,
    onOpen: (section: String, index: Int) -> Unit,
    onOpenAlbum: (key: String) -> Unit,
    onSuggestion: (String) -> Unit,
    /** Picking several: ticks on what's chosen. */
    picked: Set<Long>?,
    /** Columns, saved separately from the Library's; pinch changes them. */
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** People to offer before anything is typed (the Material style's row of faces). */
    topPeople: List<Pair<Person, MediaItem>> = emptyList(),
    /** Selected ids while selecting (long-press a result to start); null when not. */
    selection: Set<Long>? = null,
    /** The selection changed; [section] is the list it's in. */
    onSelectionChange: (section: String, Set<Long>?) -> Unit = { _, _ -> },
) {
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    // Scrolling the results puts the keyboard away, like iOS.
    val hideKeyboard = remember(keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) keyboard?.hide()
                return Offset.Zero
            }
        }
    }
    val cellPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() } / columns
    val bucket = ImageKeys.thumbBucket(cellPx)

    if (query.isBlank()) {
        Suggestions(recent, suggested, suggestions, contentPadding, onSuggestion, onClearRecent, modifier, topPeople) { onOpenPerson(CollectionKeys.person(it.id)) }
        return
    }
    // Where each cell was last placed, so a tap outside the active section can still fly from it.
    val placed = remember { HashMap<String, LayoutCoordinates>() }
    // Debug and profile builds (".debug"): Smart Search's scores on its cells, to tune its cutoff.
    val showScores = LocalContext.current.packageName.endsWith(".debug")
    val order = SearchOrder.current
    val shown = remember(sections, lookingInside, order) {
        sections?.let { visibleSections(it, lookingInside) }.orEmpty().map { it.ordered() }
    }
    // Long-press selects in the one list of results (with Search Details, in the first section).
    val selectIn = (shown.firstOrNull { it.id == SearchSection.All } ?: shown.firstOrNull())?.id
    val peopleRow = people.isNotEmpty() || unnamed != null
    val albumsRow = albums.isNotEmpty()
    // Ids in grid order, for a drag across cells: null for titles, rows and other sections.
    val gridIds = remember(shown, expanded, columns, peopleRow, albumsRow, selectIn) {
        buildList<Long?> {
            repeat((if (peopleRow) 2 else 0) + (if (albumsRow) 2 else 0)) { add(null) }
            for (section in shown) {
                add(null)
                repeat(shownCount(section, expanded, columns)) { add(if (section.id == selectIn) section.items[it].id else null) }
            }
        }
    }
    val currentSelection by rememberUpdatedState(selection)
    // A long-press's release reaches the cell's tap handler too: not a tap (see LibraryScreen).
    val longPressActive = remember { booleanArrayOf(false) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val selectable = if (picked != null || selectIn == null) Modifier else Modifier.dragToSelect(
        gridState = gridState,
        ids = { gridIds },
        selection = { currentSelection.orEmpty() },
        onSelectionChange = { onSelectionChange(selectIn, it) },
        onStart = {
            longPressActive[0] = true
            keyboard?.hide()
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onEnd = { longPressActive[0] = false },
        edgeTop = with(density) { contentPadding.calculateTopPadding().toPx() },
        edgeBottom = with(density) { contentPadding.calculateBottomPadding().toPx() },
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(hideKeyboard)
            .pinchColumns(ColumnSteps, columns, onColumnsChange)
            .then(selectable),
    ) {
        if (people.isNotEmpty() || unnamed != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.main_people)) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(people, key = { it.first.id }) { (person, cover) ->
                        PersonCell(person, cover, person.name.orEmpty(), MaterialTheme.colorScheme.onBackground) {
                            onOpenPerson(CollectionKeys.person(person.id))
                        }
                    }
                    if (unnamed != null) {
                        item(key = "unnamed") {
                            PersonCell(unnamed.first, unnamed.second, stringResource(R.string.main_add_name), MaterialTheme.colorScheme.primary) {
                                onNamePerson(unnamed.first)
                            }
                        }
                    }
                }
            }
        }
        if (albums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.main_albums)) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(albums, key = { it.key }) { album ->
                        Box(Modifier.width(120.dp)) { AlbumCard(album, onClick = { onOpenAlbum(album.key) }) }
                    }
                }
            }
        }
        for (section in shown) {
            val count = shownCount(section, expanded, columns)
            item(key = "header:${section.id}", span = { GridItemSpan(maxLineSpan) }) {
                if (section.id == SearchSection.All) {
                    val n = section.items.size
                    val lookingText = stringResource(R.string.main_search_looking_inside)
                    SectionTitle(
                        when {
                            n == 0 -> lookingText
                            else -> pluralStringResource(R.plurals.main_search_results, n, n)
                        },
                        detail = if (lookingInside && n > 0) lookingText else null,
                        action = if (n > 1) stringResource(SearchOrder.current.label) else null,
                        actionIcon = LumenIcons.Sort,
                        onAction = {
                            SearchOrder.current = SearchOrder.entries[(SearchOrder.current.ordinal + 1) % SearchOrder.entries.size]
                        },
                    )
                } else {
                    SectionTitle(
                        if (section.title != 0) stringResource(section.title) else "",
                        detail = when {
                            section.items.isEmpty() -> stringResource(R.string.main_search_looking_inside)
                            else -> "%,d".format(section.items.size)
                        },
                        action = when {
                            section.items.size <= columns -> null
                            section.id in expanded -> stringResource(R.string.main_search_show_less)
                            else -> stringResource(R.string.main_search_see_all)
                        },
                        onAction = { onToggleSection(section.id) },
                    )
                }
            }
            items(count, key = { "${section.id}:${section.items[it].id}" }) { index ->
                val item = section.items[index]
                val cellKey = "${section.id}:${item.id}"
                Box {
                Thumbnail(
                    item = item,
                    bucket = bucket,
                    fit = false,
                    showDuration = columns <= 5,
                    selected = picked?.let { item.id in it } ?: selection?.takeIf { section.id == selectIn }?.let { item.id in it },
                    onClick = click@{
                        if (longPressActive[0]) return@click
                        val chosen = selection
                        if (chosen != null) {
                            // Selecting: a tap ticks or unticks; other sections wait.
                            if (section.id == selectIn) onSelectionChange(section.id, if (item.id in chosen) chosen - item.id else chosen + item.id)
                            return@click
                        }
                        keyboard?.hide()
                        placed[cellKey]?.let { transition.register(item.id, it) }
                        onOpen(section.id, index)
                    },
                    modifier = Modifier
                        .onPlaced { placed[cellKey] = it }
                        .then(if (section.id == activeSection) Modifier.transitionCell(transition, item.id) else Modifier),
                )
                val score = section.scores[item.id]
                if (showScores && score != null) {
                    Text(
                        "#${index + 1} %.3f".format(score),
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
                }
            }
        }
        if (sections != null && shown.isEmpty() && albums.isEmpty() && people.isEmpty() && unnamed == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.main_search_no_results_hint),
                    color = colors.onBackground.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                )
            }
        }
    }
}

/** A face in the People row: tap to open the person (or, for [label] "Add Name", to name them). */
@Composable
private fun PersonCell(person: Person, cover: MediaItem, label: String, labelColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp).pressable(onClick, pressedScale = 0.94f),
    ) {
        FaceAvatar(person, cover, Modifier.size(72.dp))
        Text(
            label,
            fontSize = 13.sp,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    detail: String? = null,
    action: String? = null,
    /** Material: an icon in the action's chip (the order of the results). */
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onAction: () -> Unit = {},
) {
    val material = Lumen.material
    Row(
        Modifier.padding(start = 16.dp, end = if (material) 16.dp else 10.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = if (material) Alignment.CenterVertically else Alignment.Bottom,
    ) {
        Text(text, fontSize = 20.sp, fontWeight = Lumen.type.strong, color = MaterialTheme.colorScheme.onBackground)
        if (detail != null) {
            Spacer(Modifier.width(8.dp))
            Text(detail, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        Spacer(Modifier.weight(1f))
        if (action != null && material) {
            // Material: a tonal chip, as Android's own apps offer a list's order.
            val scheme = MaterialTheme.colorScheme
            Row(
                Modifier
                    .height(36.dp)
                    .pressable(onAction, pressedScale = 0.94f, shape = CircleShape)
                    .background(scheme.surfaceContainerHigh, CircleShape)
                    .padding(start = if (actionIcon != null) 12.dp else 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (actionIcon != null) {
                    Icon(actionIcon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(action, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface, maxLines = 1)
            }
        } else if (action != null) {
            Text(
                action,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.pressable(onAction, pressedScale = 0.94f).padding(6.dp),
            )
        }
    }
}

/**
 * Starting points while nothing is typed: recent searches, then things this library has plenty
 * of (each with its best photo), or name/date ideas without Smart Search. Tap one to search it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(
    recent: List<String>,
    suggested: List<Pair<String, MediaItem>>,
    keywords: List<String>,
    contentPadding: PaddingValues,
    onSuggestion: (String) -> Unit,
    onClearRecent: () -> Unit,
    modifier: Modifier = Modifier,
    topPeople: List<Pair<Person, MediaItem>> = emptyList(),
    onOpenPerson: (Person) -> Unit = {},
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val material = Lumen.material
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        if (!material) Text(
            stringResource(R.string.common_search),
            style = Lumen.type.screenTitle,
            color = onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )
        // Material: faces across the top, then what was searched before as a plain list.
        if (material && topPeople.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for ((person, cover) in topPeople.take(12)) {
                    FaceAvatar(person, cover, Modifier.size(64.dp).pressable({ onOpenPerson(person) }, shape = CircleShape))
                }
            }
        }
        if (material && recent.isNotEmpty()) {
            val scheme = MaterialTheme.colorScheme
            recent.forEach { term ->
                Row(
                    Modifier.fillMaxWidth().pressable({ onSuggestion(term) }, pressedScale = 0.99f).padding(horizontal = 8.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(LumenIcons.History, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(20.dp))
                    Text(term, fontSize = 17.sp, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                androidx.compose.material3.HorizontalDivider(color = scheme.outlineVariant)
            }
            Text(
                stringResource(R.string.main_search_clear),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.primary,
                modifier = Modifier.align(Alignment.End).pressable(onClearRecent, shape = CircleShape).padding(horizontal = 12.dp, vertical = 10.dp),
            )
            Spacer(Modifier.size(12.dp))
        }
        if (!material && recent.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.main_search_recent), fontSize = 20.sp, fontWeight = Lumen.type.strong, color = onBackground, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.main_search_clear),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.pressable(onClearRecent, pressedScale = 0.94f).padding(6.dp),
                )
            }
            Chips(recent, onSuggestion)
            Spacer(Modifier.size(24.dp))
        }
        if (suggested.isNotEmpty()) {
            Text(
                stringResource(R.string.main_search_from_your_photos),
                fontSize = 20.sp,
                fontWeight = Lumen.type.strong,
                color = onBackground,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                maxItemsInEachRow = 4,
            ) {
                suggested.forEach { (term, cover) ->
                    Column(
                        Modifier.weight(1f).pressable({ onSuggestion(term) }, pressedScale = 0.94f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model = MediaThumbnail(cover.uri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(if (Lumen.material) Lumen.shapes.cover else RoundedCornerShape(14.dp)),
                        )
                        Text(
                            term.replaceFirstChar { it.uppercase() },
                            fontSize = 13.sp,
                            color = onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        } else if (keywords.isNotEmpty()) {
            Text(
                stringResource(R.string.main_search_try),
                fontSize = 15.sp,
                color = onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            Chips(keywords, onSuggestion)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(items: List<String>, onClick: (String) -> Unit) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            Row(
                Modifier
                    .pressable({ onClick(item) }, pressedScale = 0.94f, shape = CircleShape)
                    .clip(CircleShape)
                    .background(if (Lumen.material) MaterialTheme.colorScheme.surfaceContainerHigh else onBackground.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    LumenIcons.Search,
                    contentDescription = null,
                    tint = onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(item, fontSize = 15.sp, color = onBackground)
            }
        }
    }
}
