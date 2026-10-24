package com.subhashh.lumen.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
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
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.ai.Person
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

/** One way of matching a search, shown as its own section: one row until See All. */
data class SearchSection(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
    /** Smart Search's score per item, shown on the cells in development builds only. */
    val scores: Map<Long, Float> = emptyMap(),
) {
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
        Suggestions(recent, suggested, suggestions, contentPadding, onSuggestion, onClearRecent, modifier)
        return
    }
    // Where each cell was last placed, so a tap outside the active section can still fly from it.
    val placed = remember { HashMap<String, LayoutCoordinates>() }
    // Debug and profile builds (".debug"): Smart Search's scores on its cells, to tune its cutoff.
    val showScores = LocalContext.current.packageName.endsWith(".debug")
    val shown = sections?.let { visibleSections(it, lookingInside) }.orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(hideKeyboard)
            .pinchColumns(ColumnSteps, columns, onColumnsChange),
    ) {
        if (people.isNotEmpty() || unnamed != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("People") }
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
                            PersonCell(unnamed.first, unnamed.second, "Add Name", MaterialTheme.colorScheme.primary) {
                                onNamePerson(unnamed.first)
                            }
                        }
                    }
                }
            }
        }
        if (albums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Albums") }
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
                    SectionTitle(
                        when {
                            n == 0 -> "Looking inside photos…"
                            n == 1 -> "1 Result"
                            else -> "%,d Results".format(n)
                        },
                        detail = if (lookingInside && n > 0) "Looking inside photos…" else null,
                    )
                } else {
                    SectionTitle(
                        section.title,
                        detail = when {
                            section.items.isEmpty() -> "Looking inside photos…"
                            else -> "%,d".format(section.items.size)
                        },
                        action = when {
                            section.items.size <= columns -> null
                            section.id in expanded -> "Show Less"
                            else -> "See All"
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
                    selected = picked?.let { item.id in it },
                    onClick = {
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
                    "Try describing what's in the photo, like “cat on a sofa” (with Smart Features in " +
                        "Settings), or a file name, a folder, a month or a year.",
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
private fun SectionTitle(text: String, detail: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        if (detail != null) {
            Spacer(Modifier.width(8.dp))
            Text(detail, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        Spacer(Modifier.weight(1f))
        if (action != null) {
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
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "Search",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )
        if (recent.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onBackground, modifier = Modifier.weight(1f))
                Text(
                    "Clear",
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
                "From your photos",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
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
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)),
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
                "Try",
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
                    .pressable({ onClick(item) }, pressedScale = 0.94f)
                    .clip(CircleShape)
                    .background(onBackground.copy(alpha = 0.08f))
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
