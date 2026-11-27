package com.subhashh.lumen.ui.collections

import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.theme.Lumen
import com.subhashh.lumen.ui.images.version
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.images.ImageKeys
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.subhashh.lumen.ui.components.pinchColumns
import com.subhashh.lumen.ui.library.SelectionMark
import com.subhashh.lumen.ui.selection.dragToSelect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import com.subhashh.lumen.ui.settings.groupColors
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaCollections
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.GridThumbnail
import com.subhashh.lumen.ai.Person
import com.subhashh.lumen.ui.people.FaceAvatar
import com.subhashh.lumen.ui.transition.CoverTransition
import com.subhashh.lumen.ui.transition.coverCell
import androidx.compose.ui.res.stringResource
import com.subhashh.lumen.R

@Composable
fun CollectionsScreen(
    /** "Collections", or the selection count while selecting. */
    title: String,
    utilities: List<MediaCollection>,
    /** Hidden shows "Set Up" until it has a lock, then "Locked". */
    hiddenLockSet: Boolean,
    folders: List<MediaCollection>,
    /** People found in photos (Settings → Smart Search), each with the photo for their face. */
    people: List<Pair<Person, MediaItem>>,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onOpen: (key: String) -> Unit,
    /** Album columns; utilities stay two across. */
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    /** Selected album keys while selecting; null when not in selection mode. */
    selection: Set<String>?,
    onSelectionChange: (Set<String>?) -> Unit,
    /** Album covers and faces grow out of their cards into the page they open. */
    coverTransition: CoverTransition,
    modifier: Modifier = Modifier,
) {
    // Grid order: title, People header and row (if any), Utilities header, utilities block,
    // Albums header, then the albums. A wide window puts People and Utilities side by side in
    // one block (the dashboard), the albums below.
    val wide = com.subhashh.lumen.ui.components.isWideWindow()
    val gestures = rememberAlbumGridGestures(
        gridState = gridState,
        // Wide: title, the People + Utilities dashboard, Albums header.
        keys = { List<String?>(if (wide) 3 else if (people.isEmpty()) 4 else 6) { null } + folders.map { it.key } },
        columns = columns,
        onColumnsChange = onColumnsChange,
        selection = selection,
        onSelectionChange = onSelectionChange,
        contentPadding = contentPadding,
    )
    // More faces in a wider window (tablet, desktop mode), each the size it is on a phone.
    val peopleShown = com.subhashh.lumen.ui.components.scaledColumns(PeopleShown)
    LazyVerticalGrid(
        columns = GridCells.Fixed(com.subhashh.lumen.ui.components.scaledColumns(columns)),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            // Inside the padding: drag-select matches touch points against item offsets.
            .then(gestures.modifier),
    ) {
        fullWidth { ScreenTitle(title) }

        if (wide) fullWidth {
            WideDashboard(
                people = people,
                utilities = utilities,
                hiddenLockSet = hiddenLockSet,
                coverTransition = coverTransition,
                onOpen = { if (selection == null) onOpen(it) },
                modifier = Modifier.graphicsLayer { alpha = if (selection != null) 0.4f else 1f },
            )
        }
        if (!wide && people.isNotEmpty()) {
            fullWidth { SectionHeader(stringResource(R.string.main_people), trailing = stringResource(R.string.common_open), onTrailingClick = { if (selection == null) onOpen(CollectionKeys.People) }) }
            fullWidth {
                PeopleRow(
                    people.take(peopleShown),
                    slots = peopleShown,
                    coverTransition = coverTransition,
                    onOpen = { if (selection == null) onOpen(it) },
                    modifier = Modifier.graphicsLayer { alpha = if (selection != null) 0.4f else 1f },
                )
            }
        }

        if (!wide) fullWidth { SectionHeader(stringResource(R.string.main_utilities)) }
        if (!wide) fullWidth {
            // Not selectable: dimmed while selecting albums.
            UtilityGroup(
                utilities,
                hiddenLockSet,
                onOpen = { if (selection == null) onOpen(it) },
                // A readable width in a wide window, not a list across the whole screen.
                modifier = Modifier.widthIn(max = com.subhashh.lumen.ui.components.ReadableWidth).graphicsLayer { alpha = if (selection != null) 0.4f else 1f },
            )
        }

        fullWidth { SectionHeader(stringResource(R.string.main_albums), trailing = folders.size.toString()) }
        items(folders, key = { it.key }) { collection ->
            AlbumCard(
                collection,
                selected = selection?.let { collection.key in it },
                onClick = { gestures.click(collection.key) { onOpen(collection.key) } },
                coverModifier = Modifier.coverCell(coverTransition, collection.key),
            )
        }
    }
}

/** Grid densities for album grids, from most zoomed in to most zoomed out. */
private val AlbumColumnSteps = listOf(1, 2, 3, 4, 5)

internal class AlbumGridGestures(
    val modifier: Modifier,
    /** Opens the album, or toggles it while selecting. */
    val click: (key: String, open: () -> Unit) -> Unit,
)

/**
 * Pinch to change columns, and long-press (then drag) to select albums, as in the Library.
 * [keys] lists every grid cell in order, null for cells that aren't albums.
 */
@Composable
internal fun rememberAlbumGridGestures(
    gridState: LazyGridState,
    keys: () -> List<String?>,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    selection: Set<String>?,
    onSelectionChange: (Set<String>?) -> Unit,
    contentPadding: PaddingValues,
): AlbumGridGestures {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentSelection by rememberUpdatedState(selection)
    val currentKeys by rememberUpdatedState(keys)
    // A long-press's release reaches the card's tap handler first; it mustn't count as a tap.
    val longPressActive = remember { booleanArrayOf(false) }
    val modifier = Modifier
        .pinchColumns(AlbumColumnSteps, columns, onColumnsChange)
        .dragToSelect(
            gridState = gridState,
            ids = { currentKeys() },
            selection = { currentSelection.orEmpty() },
            onSelectionChange = onSelectionChange,
            onStart = {
                longPressActive[0] = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onEnd = { longPressActive[0] = false },
            edgeTop = with(density) { contentPadding.calculateTopPadding().toPx() },
            edgeBottom = with(density) { contentPadding.calculateBottomPadding().toPx() },
        )
    return AlbumGridGestures(modifier) { key, open ->
        val current = currentSelection
        when {
            longPressActive[0] -> Unit
            current == null -> open()
            else -> onSelectionChange(if (key in current) current - key else current + key)
        }
    }
}

@Composable
internal fun ScreenTitle(title: String) {
    Text(
        title,
        style = Lumen.type.screenTitle,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun SectionHeader(title: String, trailing: String? = null, onTrailingClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            style = Lumen.type.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null && onTrailingClick != null) {
            Text(
                trailing,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.pressable(onTrailingClick, pressedScale = 0.92f),
            )
        } else if (trailing != null) {
            Text(trailing, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
    }
}

/**
 * Utilities as one iOS-style grouped list: a colored badge with a white glyph, the name, the
 * count and a chevron per row. Compact, and nothing like the album grid below it.
 */
@Composable
private fun UtilityGroup(
    utilities: List<MediaCollection>,
    hiddenLockSet: Boolean,
    onOpen: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = groupColors()
    val material = Lumen.material
    Column(
        modifier
            .fillMaxWidth()
            .clip(Lumen.shapes.card)
            .background(colors.card),
    ) {
        utilities.forEachIndexed { i, collection ->
            // Material: rows are segments with the page showing between them, as in Settings.
            if (i > 0 && material) Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.background))
            else if (i > 0) HorizontalDivider(color = colors.divider, modifier = Modifier.padding(start = 60.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(collection.key) }
                    .padding(horizontal = if (material) 16.dp else 14.dp, vertical = if (material) 12.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val thumb = utilityThumbnail(collection)
                if (thumb != null) {
                    // The newest photo in it, where the badge would be.
                    Box(Modifier.size(if (material) 40.dp else 32.dp).clip(RoundedCornerShape(8.dp))) { Cover(thumb) }
                } else if (material) {
                    com.subhashh.lumen.ui.settings.IconBadge(utilityIcon(collection.key), utilityColor(collection.key))
                } else Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(utilityColor(collection.key)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(utilityIcon(collection.key), contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(if (material) 16.dp else 14.dp))
                Text(
                    collection.title,
                    fontSize = 16.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        collection.count != MediaCollections.LockedCount -> collection.count.toString()
                        hiddenLockSet -> stringResource(R.string.main_hidden_locked)
                        else -> stringResource(R.string.main_hidden_set_up)
                    },
                    fontSize = 16.sp,
                    color = colors.secondary,
                )
                if (!material) Spacer(Modifier.width(6.dp))
                if (!material) Icon(
                    LumenIcons.Chevron,
                    contentDescription = null,
                    tint = colors.secondary,
                    // The back chevron turned around, as in Settings.
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = 180f },
                )
            }
        }
    }
}

private const val CoverPx = 512

/** The first [PeopleShown] people across the screen, faces in circles; named people first. */
@Composable
private fun PeopleRow(
    people: List<Pair<Person, MediaItem>>,
    coverTransition: CoverTransition,
    onOpen: (key: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Faces across: [PeopleShown] on a phone, more in a wide window. */
    slots: Int = PeopleShown,
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = modifier.fillMaxWidth()) {
        for ((person, cover) in people) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .pressable({ onOpen(CollectionKeys.person(person.id)) }, pressedScale = 0.94f),
            ) {
                FaceAvatar(person, cover, Modifier.fillMaxWidth().coverCell(coverTransition, CollectionKeys.person(person.id), circle = true))
                Spacer(Modifier.height(6.dp))
                Text(
                    person.name ?: "",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // A short row keeps its faces the same size as a full one.
        repeat(slots - people.size) { Spacer(Modifier.weight(1f)) }
    }
}

private const val PeopleShown = 4

/** iOS system colors, one per utility, so each row is recognisable at a glance. */
private fun utilityColor(key: String): Color = when (key) {
    CollectionKeys.Favorites -> Color(0xFFFF375F)
    CollectionKeys.Videos -> Color(0xFF0A84FF)
    CollectionKeys.Screenshots -> Color(0xFF30B0C7)
    CollectionKeys.RecentlyDeleted -> Color(0xFF8E8E93)
    CollectionKeys.Hidden -> Color(0xFF5E5CE6)
    CollectionKeys.Documents -> Color(0xFFFF9F0A)
    else -> Color(0xFF8E8E93)
}

internal fun utilityIcon(key: String): ImageVector = when (key) {
    CollectionKeys.Favorites -> LumenIcons.HeartFilled
    CollectionKeys.Videos -> LumenIcons.Video
    CollectionKeys.Screenshots -> LumenIcons.Screenshot
    CollectionKeys.RecentlyDeleted -> LumenIcons.Delete
    CollectionKeys.Hidden -> LumenIcons.Lock
    CollectionKeys.Documents -> LumenIcons.Document
    else -> LumenIcons.Folder
}

@Composable
internal fun AlbumCard(
    collection: MediaCollection,
    onClick: () -> Unit,
    subtitle: String = if (collection.count == 0) stringResource(R.string.main_album_empty) else collection.count.toString(),
    /** null when not selecting. */
    selected: Boolean? = null,
    /** On the square cover, e.g. for the open transition to find it. */
    coverModifier: Modifier = Modifier,
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    // Selected covers shrink a little inside their slot, like photos in the grid.
    val scale by animateFloatAsState(
        if (selected == true) 0.9f else 1f,
        spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "selectScale",
    )
    // The ripple follows the cover's corners at the top; square-ish below, where the name sits.
    Column(Modifier.pressable(onClick, pressedScale = 0.96f, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp))) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Box(
                coverModifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(Lumen.shapes.cover)
                    .background(onBackground.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                val cover = collection.cover
                if (cover != null) {
                    Cover(cover)
                } else {
                    Icon(LumenIcons.Folder, contentDescription = null, tint = onBackground.copy(alpha = 0.3f))
                }
            }
            if (selected != null) SelectionMark(selected, Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (collection.pinned) {
                Icon(
                    LumenIcons.Pin,
                    contentDescription = stringResource(R.string.main_pinned),
                    tint = onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                collection.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            subtitle,
            fontSize = 13.sp,
            color = onBackground.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
internal fun Cover(item: MediaItem) {
    val context = LocalContext.current
    AsyncImage(
        // Own cache key: covers are larger than grid thumbnails, so they must not reuse those bitmaps.
        model = remember(item.version) {
            ImageRequest.Builder(context)
                // Saved on disk like grid thumbnails, at one size for every cover.
                .data(GridThumbnail(item, CoverPx))
                .size(CoverPx, CoverPx)
                .memoryCacheKey(ImageKeys.cover(item))
                .build()
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Wide windows: People in a card (up to two rows of faces) beside Utilities as tiles with big
 * counts, two across; the albums follow below. Everything at a glance, nothing sideways.
 */
@Composable
private fun WideDashboard(
    people: List<Pair<Person, MediaItem>>,
    utilities: List<MediaCollection>,
    hiddenLockSet: Boolean,
    coverTransition: CoverTransition,
    onOpen: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = groupColors()
    // As tall as the People card: the tiles stretch to fill it, no gap under them.
    Row(modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (people.isNotEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(Lumen.shapes.card)
                    .background(colors.card)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.main_people), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.text, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.common_open),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpen(CollectionKeys.People) }.padding(4.dp),
                    )
                }
                val shown = people.take(PeopleShown * 2)
                shown.chunked(PeopleShown).forEach { row ->
                    PeopleRow(row, coverTransition = coverTransition, onOpen = onOpen, slots = PeopleShown)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            utilities.chunked(2).forEach { pair ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { collection ->
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 98.dp)
                                .clip(Lumen.shapes.card)
                                .background(colors.card)
                                .clickable { onOpen(collection.key) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                val thumb = utilityThumbnail(collection)
                                if (thumb != null) {
                                    // The newest photo in it, with its badge on the corner.
                                    Box(Modifier.size(56.dp)) {
                                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) { Cover(thumb) }
                                        Box(
                                            Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp).size(24.dp).clip(RoundedCornerShape(7.dp)).background(utilityColor(collection.key)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(utilityIcon(collection.key), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else Box(
                                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(utilityColor(collection.key)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(utilityIcon(collection.key), contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    when {
                                        collection.count != MediaCollections.LockedCount -> collection.count.toString()
                                        hiddenLockSet -> stringResource(R.string.main_hidden_locked)
                                        else -> stringResource(R.string.main_hidden_set_up)
                                    },
                                    fontSize = if (collection.count != MediaCollections.LockedCount) 22.sp else 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.text,
                                )
                            }
                            Text(collection.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * A utility's thumbnail: its newest photo. Never Hidden's (a hidden photo on the main page would
 * undo hiding it) nor Recently Deleted's (deleted photos shouldn't greet you every day).
 */
internal fun utilityThumbnail(collection: MediaCollection): MediaItem? =
    collection.cover.takeIf { collection.key != CollectionKeys.Hidden && collection.key != CollectionKeys.RecentlyDeleted }

/**
 * Utilities → Documents (Lumen style): the kinds of paper Smart Search found (Documents,
 * Receipts, Tickets…), each a card with the newest of its strongest matches and how many.
 */
@Composable
internal fun DocumentsScreen(
    documents: List<DocumentKind>,
    counts: Map<String, Int>,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onOpen: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = groupColors()
    LazyVerticalGrid(
        columns = GridCells.Fixed(com.subhashh.lumen.ui.components.scaledColumns(2)),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        fullWidth { ScreenTitle(stringResource(R.string.collections_documents)) }
        items(documents, key = { it.query }) { kind ->
            Column(Modifier.pressable({ onOpen(CollectionKeys.documents(kind.query)) }, pressedScale = 0.96f)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(colors.card),
                    contentAlignment = Alignment.Center,
                ) {
                    if (kind.cover != null) Cover(kind.cover)
                    else Icon(LumenIcons.Document, contentDescription = null, tint = colors.secondary)
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(kind.label), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                counts[kind.query]?.let { Text(it.toString(), fontSize = 13.sp, color = colors.secondary) }
            }
        }
    }
}
