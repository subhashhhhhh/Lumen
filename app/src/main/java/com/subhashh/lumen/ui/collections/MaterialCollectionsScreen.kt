package com.subhashh.lumen.ui.collections

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.Person
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaCollections
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.library.SelectionMark
import com.subhashh.lumen.ui.people.FaceAvatar
import com.subhashh.lumen.ui.transition.CoverTransition
import com.subhashh.lumen.ui.transition.coverCell

/** One card of the Documents row: a kind of paper Smart Search found, and the photo that stands for it. */
@Immutable
class DocumentKind(@StringRes val label: Int, val query: String, val cover: MediaItem?)

/** The kinds of document looked for: what each is called, and the words Smart Search is asked for. */
internal val DocumentKinds = listOf(
    R.string.collections_doc_documents to "document",
    R.string.collections_doc_receipts to "receipt",
    R.string.collections_doc_tickets to "ticket",
    R.string.collections_doc_identity to "id card",
    R.string.collections_doc_books to "book",
    R.string.collections_doc_notes to "whiteboard",
)

/**
 * Collections in the Material 3 Expressive style: a page of rows to browse sideways (people,
 * places, documents), the utilities as a block of tiles, then every album as a
 * tall card with its name on the cover. The same things as [CollectionsScreen], laid out the way
 * Android galleries do.
 */
@Composable
fun MaterialCollectionsScreen(
    utilities: List<MediaCollection>,
    hiddenLockSet: Boolean,
    folders: List<MediaCollection>,
    people: List<Pair<Person, MediaItem>>,
    /** The kinds Smart Search found something for; empty without it. Each opens its own page. */
    documents: List<DocumentKind>,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onOpen: (key: String) -> Unit,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    selection: Set<String>?,
    onSelectionChange: (Set<String>?) -> Unit,
    coverTransition: CoverTransition,
    modifier: Modifier = Modifier,
) {
    // Everything above the albums, as grid cells in order: what the album gestures skip over.
    val heads = (if (people.isEmpty()) 0 else 2) + 2 + (if (documents.isEmpty()) 0 else 2) + 2 + 1
    val gestures = rememberAlbumGridGestures(
        gridState = gridState,
        keys = { List<String?>(heads) { null } + folders.map { it.key } },
        columns = columns,
        onColumnsChange = onColumnsChange,
        selection = selection,
        onSelectionChange = onSelectionChange,
        contentPadding = contentPadding,
    )
    // Not selectable: dimmed while albums are being selected.
    val dim = Modifier.graphicsLayer { alpha = if (selection != null) 0.4f else 1f }
    val open = { key: String -> if (selection == null) onOpen(key) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .then(gestures.modifier),
    ) {
        if (people.isNotEmpty()) {
            wide { RowHeader(stringResource(R.string.main_people), stringResource(R.string.collections_view_all)) { open(CollectionKeys.People) } }
            wide {
                Carousel(dim) {
                    items(people.take(PeopleInRow), key = { it.first.id }) { (person, cover) ->
                        val key = CollectionKeys.person(person.id)
                        // Just the face and the name, no card around them.
                        Column(
                            Modifier.width(104.dp).pressable({ open(key) }, pressedScale = 0.96f, shape = RoundedCornerShape(20.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            FaceAvatar(person, cover, Modifier.fillMaxWidth().coverCell(coverTransition, key, circle = true))
                            Text(
                                person.name.orEmpty(),
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        wide { RowHeader(stringResource(R.string.collections_places)) }
        wide { PlacesSoon(dim) }
        if (documents.isNotEmpty()) {
            wide { RowHeader(stringResource(R.string.collections_documents)) }
            wide {
                Carousel(dim) {
                    items(documents, key = { it.query }) { kind ->
                        TonalCard(stringResource(kind.label), onClick = { open(CollectionKeys.documents(kind.query)) }) {
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (kind.cover != null) Cover(kind.cover)
                                else Icon(LumenIcons.Document, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        wide {
            Text(
                stringResource(R.string.collections_other),
                style = com.subhashh.lumen.ui.theme.Lumen.type.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
            )
        }
        wide { UtilityTiles(utilities, hiddenLockSet, open, dim) }
        wide { RowHeader(stringResource(R.string.main_albums), folders.size.toString()) }
        items(folders, key = { it.key }) { album ->
            val selected = selection?.let { album.key in it }
            val scale by animateFloatAsState(if (selected == true) 0.92f else 1f, spring(dampingRatio = 0.7f, stiffness = 700f), label = "selectScale")
            Box(Modifier.aspectRatio(0.74f)) {
                CoverCard(
                    album.title,
                    album.cover,
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                    coverModifier = Modifier.coverCell(coverTransition, album.key),
                    pinned = album.pinned,
                ) { gestures.click(album.key) { onOpen(album.key) } }
                if (selected != null) SelectionMark(selected, Modifier.align(Alignment.TopStart).padding(12.dp))
            }
        }
    }
}

private fun LazyGridScope.wide(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

/** A row's name, and what's at its end: a link ([onTrailing]) or just a number. */
@Composable
private fun RowHeader(title: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = com.subhashh.lumen.ui.theme.Lumen.type.sectionTitle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        if (trailing != null && onTrailing != null) {
            Text(
                trailing,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.pressable(onTrailing, shape = CircleShape).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        } else if (trailing != null) {
            Text(trailing, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

/** A row that scrolls sideways, running off the page's edges. */
@Composable
private fun Carousel(modifier: Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyRow(
        // Out to the screen's edges: the grid pads its sides by 16 dp.
        modifier = modifier.fillMaxWidth().layout { measurable, constraints ->
            val bleed = 16.dp.roundToPx()
            val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + bleed * 2))
            layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
        },
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** A tonal card: a square of something (a face, a document) and its name underneath. */
@Composable
private fun TonalCard(label: String, onClick: () -> Unit, picture: @Composable () -> Unit) {
    Column(
        Modifier
            .width(132.dp)
            .heightIn(min = 176.dp)
            .pressable(onClick, pressedScale = 0.96f, shape = CardShape)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) { picture() }
        Spacer(Modifier.height(10.dp))
        Text(
            label,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A card filled by its cover photo, the name across the bottom. */
@Composable
private fun CoverCard(
    title: String,
    cover: MediaItem?,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    pinned: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .pressable(onClick, pressedScale = 0.96f, shape = CardShape)
            .clip(CardShape)
            .background(scheme.surfaceContainer),
    ) {
        Box(coverModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (cover != null) Cover(cover) else Icon(LumenIcons.Folder, null, tint = scheme.onSurfaceVariant)
        }
        // Darkens the photo under the name, wherever the photo is light.
        if (cover != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)))),
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ink = if (cover != null) Color.White else scheme.onSurface
            if (pinned) {
                Icon(LumenIcons.Pin, stringResource(R.string.main_pinned), tint = ink, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                title,
                color = ink,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Places isn't built yet: one card saying so, where the map and the places will go. */
@Composable
private fun PlacesSoon(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(CardShape).background(scheme.surfaceContainer).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(scheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Icon(LumenIcons.Location, null, tint = scheme.onTertiaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(stringResource(R.string.collections_places_soon), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface)
            Text(stringResource(R.string.collections_places_soon_detail), fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurfaceVariant)
        }
    }
}

/** The utilities, two across: an icon and a name each, as one rounded block of tiles. */
@Composable
private fun UtilityTiles(utilities: List<MediaCollection>, hiddenLockSet: Boolean, onOpen: (String) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().clip(CardShape), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        utilities.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                pair.forEach { collection ->
                    Row(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .pressable({ onOpen(collection.key) }, pressedScale = 0.97f, shape = RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.surfaceContainer)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(utilityIcon(collection.key), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(collection.title, fontSize = 15.sp, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        // Just the names; Hidden says so while it still has no lock.
                        if (collection.count == MediaCollections.LockedCount && !hiddenLockSet) {
                            Text(stringResource(R.string.main_hidden_set_up), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                // An odd one out keeps its half.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val CardShape = RoundedCornerShape(28.dp)
private const val PeopleInRow = 12

/**
 * The top of a kind of document's page in the Material style: its name, how many there are, and
 * every kind found as a row of chips, to go from one to the next without going back.
 */
@Composable
fun DocumentsHeader(
    kinds: List<Pair<Int, String>>,
    selected: String,
    count: Int,
    onKind: (query: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val at = kinds.indexOfFirst { it.second == selected }
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().statusBarsPadding()) {
        androidx.compose.material3.Text(
            kinds.getOrNull(at)?.let { androidx.compose.ui.res.stringResource(it.first) }.orEmpty(),
            color = scheme.onBackground,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
        )
        androidx.compose.material3.Text(
            androidx.compose.ui.res.pluralStringResource(R.plurals.collections_doc_count, count, count),
            color = scheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 20.dp, top = 2.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(
            // Opens with the chosen kind in view.
            state = androidx.compose.foundation.lazy.rememberLazyListState(if (at >= 3) at - 2 else 0),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            items(kinds.size, key = { kinds[it].second }) { i ->
                val (label, query) = kinds[i]
                val on = query == selected
                androidx.compose.material3.FilterChip(
                    selected = on,
                    onClick = { if (!on) onKind(query) },
                    label = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(label), fontSize = 14.sp) },
                    leadingIcon = if (on) {
                        { androidx.compose.material3.Icon(com.subhashh.lumen.ui.icons.LumenIcons.Check, null, modifier = Modifier.size(18.dp)) }
                    } else null,
                )
            }
        }
    }
}
