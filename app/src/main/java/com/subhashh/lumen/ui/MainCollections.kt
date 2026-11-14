package com.subhashh.lumen.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.subhashh.lumen.R
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.collections.CollectionsScreen
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.people.PeopleScreen
import com.subhashh.lumen.ui.transition.CoverTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** What [MainState.collectionPages] hands back to [MainScaffold]. */
internal class CollectionPages(
    val peoplePage: @Composable (Modifier) -> Unit,
    val collectionsPage: @Composable (Modifier) -> Unit,
)

/**
 * The Collections and People pages, as [MainScaffold] builds them: each is also drawn under an
 * album or person while its cover flies out of, or back into, its card.
 */
@Composable
internal fun MainState.collectionPages(
    pick: PickRequest?,
    utilities: List<MediaCollection>,
    folders: List<MediaCollection>,
    people: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>>,
    allPeople: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>>,
    statusTop: Dp,
    navBottom: Dp,
    collectionsGrid: LazyGridState,
    peopleGrid: LazyGridState,
    coverTransition: CoverTransition,
    personOf: (String?) -> com.subhashh.lumen.ai.Person?,
    onAlbumColumns: (Int) -> Unit,
    openHidden: () -> Unit,
    openCollectionFromCard: (String) -> Unit,
): CollectionPages {
    // Collections and People: also drawn under an album or person while its cover flies.
    val peoplePage: @Composable (Modifier) -> Unit = { pageModifier ->
            val from = mergeFrom?.let { id -> allPeople.firstOrNull { it.first.id == id }?.first }
            PeopleScreen(
                people = when {
                    from != null -> allPeople.filter { it.first.id != from.id && (!it.first.hidden || showHiddenPeople) }
                    showHiddenPeople -> allPeople
                    else -> people
                },
                title = when {
                    from != null -> stringResource(R.string.main_merge_with_ellipsis)
                    peopleSelection.isNullOrEmpty() -> stringResource(if (peopleSelection != null) R.string.main_select_people else R.string.main_people)
                    else -> pluralStringResource(R.plurals.main_n_selected, peopleSelection!!.size, peopleSelection!!.size)
                },
                selection = if (from == null) peopleSelection else null,
                onSelectionChange = { if (from == null && pick == null) peopleSelection = it },
                square = peopleSquare,
                gridState = peopleGrid,
                contentPadding = PaddingValues(top = statusTop + 64.dp, bottom = navBottom + 96.dp),
                coverTransition = coverTransition,
                modifier = pageModifier,
                onOpen = { key ->
                    val target = personOf(key)
                    if (from != null && target != null) {
                        val fromName = from.name ?: context.getString(R.string.main_this_person)
                        val targetName = target.name ?: context.getString(R.string.main_the_person_you_chose)
                        confirmation = Confirmation(
                            title = context.getString(R.string.main_merge_people_title),
                            message = (target.name ?: from.name)?.let { context.getString(R.string.main_merge_people_message_named, fromName, targetName, it) }
                                ?: context.getString(R.string.main_merge_people_message, fromName, targetName),
                            confirm = context.getString(R.string.main_merge),
                            onConfirm = {
                                mergeFrom = null
                                personFromPeople = false
                                openCollection = key
                                scope.launch(Dispatchers.IO) {
                                    com.subhashh.lumen.ai.FaceStore.of(context).merge(keep = target.id, gone = from.id)
                                }
                            },
                        )
                    } else {
                        personFromPeople = true
                        openCollectionFromCard(key)
                    }
                },
            )
    }
    val collectionsPage: @Composable (Modifier) -> Unit = { pageModifier ->
        CollectionsScreen(
            title = albumSelection?.let { albumSelectionTitle(it.size, folders = false) } ?: stringResource(R.string.main_collections),
            columns = albumColumns,
            onColumnsChange = onAlbumColumns,
            selection = albumSelection,
            onSelectionChange = { if (pick == null) albumSelection = it },
            utilities = utilities,
            hiddenLockSet = lockSet,
            folders = folders,
            people = people,
            gridState = collectionsGrid,
            contentPadding = PaddingValues(top = statusTop + (if (com.subhashh.lumen.ui.theme.Lumen.material) 68.dp else 16.dp), bottom = navBottom + 96.dp),
            coverTransition = coverTransition,
            modifier = pageModifier,
            onOpen = { key ->
                personFromPeople = false
                if (key == CollectionKeys.Hidden) openHidden() else openCollectionFromCard(key)
            },
        )
    }

    return CollectionPages(peoplePage, collectionsPage)
}
