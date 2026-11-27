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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    /** The library as this visit may show it: only what a picker asked for. */
    library: List<MediaItem>?,
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
                    // Material's selection bar says how many; the page keeps its name.
                    com.subhashh.lumen.ui.theme.Lumen.material && peopleSelection != null -> stringResource(R.string.main_people)
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
    // Material's Documents row: kinds of paper Smart Search can tell apart. Each is searched for
    // once the page is up; a kind with matches gets a card (and a page of its own), showing the
    // newest of its strongest matches. Nothing without a Smart Search model.
    val material = com.subhashh.lumen.ui.theme.Lumen.material
    val downloads by com.subhashh.lumen.ai.ModelDownloads.state.collectAsState()
    val byId = androidx.compose.runtime.remember(library) { library.orEmpty().associateBy { it.id } }
    // Searched again when photos come or go (or the model does), not when one is merely changed
    // (a favourite, an edit): the ids are the same set then.
    val ids: Set<Long> = byId.keys
    val hits by produceState(emptyMap<String, List<Pair<Long, Float>>>(), downloads, ids) {
        // Nothing to show until found again (and nothing at all once the model is gone).
        value = emptyMap()
        if (ids.isEmpty() || com.subhashh.lumen.ai.ModelDownloads.installed(context) == null) return@produceState
        val found = LinkedHashMap<String, List<Pair<Long, Float>>>()
        for ((_, query) in com.subhashh.lumen.ui.collections.DocumentKinds) {
            val matches = com.subhashh.lumen.ai.SmartSearch.search(context, query)?.hits.orEmpty().filter { it.mediaId in ids }
            if (matches.isEmpty()) continue
            found[query] = matches.map { it.mediaId to it.score }
            // Each kind appears as it's found, rather than all at the end.
            value = found.toMap()
        }
    }
    // The photos themselves as they are now (a favourite just set shows on a document's page too).
    val found: Map<String, List<MediaItem>> = androidx.compose.runtime.remember(hits, byId) {
        hits.mapValues { (_, matches) -> matches.mapNotNull { byId[it.first] }.distinctBy { it.id }.sortedByDescending { it.takenAt } }
    }
    androidx.compose.runtime.SideEffect { if (documentItems != found) documentItems = found }
    val documents = androidx.compose.runtime.remember(hits, byId) {
        com.subhashh.lumen.ui.collections.DocumentKinds.mapNotNull { (label, query) ->
            val matches = hits[query]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            // The cover: among the strongest matches (within a tenth of the best), the newest.
            val best = matches.first().second
            val cover = matches.filter { it.second >= best * 0.9f }.mapNotNull { byId[it.first] }.maxByOrNull { it.takenAt }
            com.subhashh.lumen.ui.collections.DocumentKind(label, query, cover)
        }
    }
    val collectionsPage: @Composable (Modifier) -> Unit = { pageModifier ->
        if (material) com.subhashh.lumen.ui.collections.MaterialCollectionsScreen(
            columns = albumColumns,
            onColumnsChange = onAlbumColumns,
            selection = albumSelection,
            onSelectionChange = { if (pick == null) albumSelection = it },
            utilities = utilities,
            hiddenLockSet = lockSet,
            folders = folders,
            people = people,
            documents = documents,
            gridState = collectionsGrid,
            contentPadding = PaddingValues(top = statusTop + 64.dp, bottom = navBottom + 96.dp),
            coverTransition = coverTransition,
            modifier = pageModifier,
            onOpen = { key ->
                personFromPeople = false
                if (key == CollectionKeys.Hidden) openHidden() else openCollectionFromCard(key)
            },
        ) else CollectionsScreen(
            title = albumSelection?.let { albumSelectionTitle(it.size, folders = false) } ?: stringResource(R.string.main_collections),
            columns = albumColumns,
            onColumnsChange = onAlbumColumns,
            selection = albumSelection,
            onSelectionChange = { if (pick == null) albumSelection = it },
            utilities = utilities,
            hiddenLockSet = lockSet,
            folders = folders,
            people = people,
            documents = documents,
            gridState = collectionsGrid,
            contentPadding = PaddingValues(top = statusTop + 16.dp, bottom = navBottom + 96.dp),
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
