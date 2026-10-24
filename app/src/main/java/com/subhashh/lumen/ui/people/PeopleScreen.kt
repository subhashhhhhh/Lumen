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
    modifier: Modifier = Modifier,
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ScreenTitle(title) }
        items(people, key = { it.first.id }) { (person, cover) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .pressable({ onOpen(CollectionKeys.person(person.id)) }, pressedScale = 0.95f)
                    .graphicsLayer { alpha = if (person.hidden) 0.45f else 1f },
            ) {
                FaceAvatar(cover, person.coverBox, Modifier.fillMaxWidth(), square = square)
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
