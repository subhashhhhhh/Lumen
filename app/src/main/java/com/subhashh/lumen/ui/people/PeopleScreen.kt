package com.subhashh.lumen.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.ai.Person
import com.subhashh.lumen.ui.collections.ScreenTitle
import com.subhashh.lumen.ui.collections.rememberAlbumGridGestures
import com.subhashh.lumen.ui.library.SelectionMark
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import com.subhashh.lumen.ui.components.pressable

/**
 * Collections → People → Open: everyone found in photos, three across, named people first. Also
 * the picker for a person's Merge with… ([title]). Hidden people, when shown, are dimmed.
 */
@Composable
fun PeopleScreen(
    people: List<Pair<Person, MediaItem>>,
    title: String,
    square: Boolean,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onOpen: (key: String) -> Unit,
    /** Selected person keys (long-press, then tap or drag); null when not selecting. */
    selection: Set<String>? = null,
    onSelectionChange: (Set<String>?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    // Long-press and drag to select, as for albums; three across, so no pinch.
    val gestures = rememberAlbumGridGestures(
        gridState = gridState,
        keys = { listOf<String?>(null) + people.map { CollectionKeys.person(it.first.id) } },
        columns = 3,
        onColumnsChange = {},
        selection = selection,
        onSelectionChange = onSelectionChange,
        contentPadding = contentPadding,
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).then(gestures.modifier),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ScreenTitle(title) }
        items(people, key = { it.first.id }) { (person, cover) ->
            val key = CollectionKeys.person(person.id)
            val selected = selection?.let { key in it }
            val scale by animateFloatAsState(if (selected == true) 0.9f else 1f, spring(dampingRatio = 0.7f, stiffness = 700f), label = "selectScale")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .pressable({ gestures.click(key) { onOpen(key) } }, pressedScale = 0.95f)
                    .graphicsLayer { alpha = if (person.hidden) 0.45f else 1f },
            ) {
                Box {
                    FaceAvatar(
                        person, cover,
                        Modifier.fillMaxWidth().graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                        square = square,
                    )
                    if (selected != null) SelectionMark(selected, Modifier.align(Alignment.BottomEnd).padding(4.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    person.name ?: "%,d photos".format(person.mediaIds.size),
                    fontSize = 14.sp,
                    fontWeight = if (person.name != null) FontWeight.Medium else FontWeight.Normal,
                    color = if (person.name != null) onBackground else onBackground.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
