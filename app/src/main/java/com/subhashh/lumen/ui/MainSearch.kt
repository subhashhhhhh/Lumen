package com.subhashh.lumen.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.imageDecoderEnabled
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.HfTokenStore
import com.subhashh.lumen.ai.SmartHit
import com.subhashh.lumen.ai.SmartSearch
import com.subhashh.lumen.data.AlbumSettings
import com.subhashh.lumen.data.AlbumSort
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.ExcludedFolders
import com.subhashh.lumen.data.FavoritesImport
import com.subhashh.lumen.data.FileResult
import com.subhashh.lumen.data.GridThumbnailFetcher
import com.subhashh.lumen.data.HiddenLockStore
import com.subhashh.lumen.data.HiddenMedia
import com.subhashh.lumen.data.ImageExport
import com.subhashh.lumen.data.LibraryFilter
import com.subhashh.lumen.data.LibrarySort
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.MediaActions
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaCollections
import com.subhashh.lumen.data.MediaDates
import com.subhashh.lumen.data.MediaFiles
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.MediaThumbnailFetcher
import com.subhashh.lumen.data.PhotoReminders
import com.subhashh.lumen.data.SearchIndex
import com.subhashh.lumen.data.SettingsBackup
import com.subhashh.lumen.data.vault.Vault
import com.subhashh.lumen.data.vault.VaultMigration
import com.subhashh.lumen.ui.albums.AlbumPicker
import com.subhashh.lumen.ui.albums.AlbumTarget
import com.subhashh.lumen.ui.collections.CollectionsScreen
import com.subhashh.lumen.ui.collections.coverUnderlay
import com.subhashh.lumen.ui.collections.heroSubtitle
import com.subhashh.lumen.ui.components.ConfirmDialog
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.components.DateTimeDialog
import com.subhashh.lumen.ui.components.GlassIconButton
import com.subhashh.lumen.ui.components.GlassMenu
import com.subhashh.lumen.ui.components.GlassMorphButton
import com.subhashh.lumen.ui.components.GlassTabBar
import com.subhashh.lumen.ui.components.GlassTextButton
import com.subhashh.lumen.ui.components.LumenTab
import com.subhashh.lumen.ui.components.MenuItem
import com.subhashh.lumen.ui.components.RenameDialog
import com.subhashh.lumen.ui.hidden.HiddenFolder
import com.subhashh.lumen.ui.hidden.HiddenLock
import com.subhashh.lumen.ui.hidden.HiddenLockScreen
import com.subhashh.lumen.ui.hidden.HiddenScreen
import com.subhashh.lumen.ui.hidden.LockFlow
import com.subhashh.lumen.ui.hidden.VaultProgressScreen
import com.subhashh.lumen.ui.hidden.hiddenFolders
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.library.GridSections
import com.subhashh.lumen.ui.library.LibraryScreen
import com.subhashh.lumen.ui.library.LibraryViewModel
import com.subhashh.lumen.ui.library.SectionMode
import com.subhashh.lumen.ui.library.TimelineScreen
import com.subhashh.lumen.ui.library.ZoomLevel
import com.subhashh.lumen.ui.library.periods
import com.subhashh.lumen.ui.onboarding.openSpecial
import com.subhashh.lumen.ui.people.MergePeopleDialog
import com.subhashh.lumen.ui.people.MergeProposal
import com.subhashh.lumen.ui.people.PeopleScreen
import com.subhashh.lumen.ui.search.SearchField
import com.subhashh.lumen.ui.search.SearchScreen
import com.subhashh.lumen.ui.search.SearchSection
import com.subhashh.lumen.ui.search.searchGridIndex
import com.subhashh.lumen.ui.selection.SelectionActionBar
import com.subhashh.lumen.ui.selection.SelectionTopControls
import com.subhashh.lumen.ui.settings.ExcludedFoldersScreen
import com.subhashh.lumen.ui.settings.LicensesScreen
import com.subhashh.lumen.ui.settings.ResizeDialog
import com.subhashh.lumen.ui.settings.SettingsCategory
import com.subhashh.lumen.ui.settings.SettingsScreen
import com.subhashh.lumen.ui.settings.UpdateSession
import com.subhashh.lumen.ui.theme.LocalGlassColors
import com.subhashh.lumen.ui.transition.CoverHeader
import com.subhashh.lumen.ui.transition.CoverTransition
import com.subhashh.lumen.ui.transition.CoverTransitionOverlay
import com.subhashh.lumen.ui.transition.HeroCover
import com.subhashh.lumen.ui.transition.PhotoTransition
import com.subhashh.lumen.ui.transition.PhotoTransitionOverlay
import com.subhashh.lumen.ui.transition.coverPage
import com.subhashh.lumen.ui.transition.parkedViewer
import com.subhashh.lumen.ui.transition.viewerFaceKey
import com.subhashh.lumen.ui.viewer.ViewerScreen
import com.subhashh.lumen.ui.viewer.WallpaperScreen
import com.subhashh.lumen.ui.viewer.fullScreenRect
import com.subhashh.lumen.ui.viewer.rotateMenu
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What [MainState.searchResults] hands back to [MainScaffold]. */
internal class SearchResults(
    val searchSections: List<SearchSection>?,
    val smartPending: Boolean,
    val personMatches: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>>,
    val unnamedSubject: Pair<com.subhashh.lumen.ai.Person, MediaItem>?,
    val searchAlbums: List<MediaCollection>,
    val searchSuggestions: List<String>,
    val rememberSearch: (String) -> Unit,
    val smartSuggestions: List<Pair<String, MediaItem>>,
)

/**
 * Search, as [MainScaffold] shows it: matches by name, folder and date from an index of the library,
 * by caption and by text read in photos, people of that name, and Smart Search's by what is in the
 * photos; put together into the sections of results, with the albums, people and suggestions that
 * go with them.
 */
@Composable
internal fun MainState.searchResults(
    library: List<MediaItem>?,
    folders: List<MediaCollection>,
    people: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>>,
    allPeople: List<Pair<com.subhashh.lumen.ai.Person, MediaItem>>,
): SearchResults {
    // Search: an index rebuilt off the main thread whenever the library changes, queried on
    // every keystroke (a few ms). The previous results stay up while the next ones compute.
    val searchIndex by produceState<SearchIndex?>(null, library) {
        value = library?.let { withContext(Dispatchers.Default) { SearchIndex.build(it) } }
    }
    val keywordResults by produceState<List<MediaItem>?>(null, searchIndex, searchQuery) {
        val index = searchIndex ?: return@produceState
        value = withContext(Dispatchers.Default) { index.search(searchQuery) }
    }
    // "Quoted phrases": only items whose read text or caption has exactly that, like Google Photos.
    val queryPhrases = remember(searchQuery) { com.subhashh.lumen.ai.QueryParts.phrases(searchQuery) }
    // Words read in photos (Settings → Smart Search → Text Read): exact, so right after the
    // name and date matches. A full-text lookup, a few ms, so no typing pause.
    val textMatches by produceState<List<Long>?>(null, searchQuery) {
        val query = searchQuery.trim()
        val phrases = queryPhrases
        value = if (query.length < 2) null else withContext(Dispatchers.IO) {
            if (!com.subhashh.lumen.ai.OcrModel.installed(context)) null
            else if (phrases != null) com.subhashh.lumen.ai.TextStore.of(context).searchPhrases(phrases)
            else com.subhashh.lumen.ai.TextStore.of(context).search(query)
        }
    }
    // Captions the user wrote (Details → Add a Caption): their own words, so exact matches.
    val captionMatches by produceState<List<Long>?>(null, searchQuery) {
        val query = searchQuery.trim()
        val phrases = queryPhrases
        value = if (query.length < 2) null else withContext(Dispatchers.IO) {
            if (phrases != null) com.subhashh.lumen.data.CaptionStore.of(context).searchPhrases(phrases)
            else com.subhashh.lumen.data.CaptionStore.of(context).search(query)
        }
    }
    // Opening Search loads Smart Search's text model and vectors while the user types.
    LaunchedEffect(searching) { if (searching) SmartSearch.prepare(context) }
    // Smart Search: by what's in the photos, once a model is downloaded and photos are indexed.
    // A short pause after typing, so the text model runs once per word, not once per letter.
    // Paired with the query it answers, so the screen can tell when it's still working.
    // Several people named ("hamilton and rosberg"): only items with all of them.
    val peopleGroup = remember(people, searchQuery) { com.subhashh.lumen.ai.QueryParts.people(searchQuery, people.map { it.first }) }
    val smart by produceState<Pair<String, com.subhashh.lumen.ai.SmartResult?>?>(null, searchQuery, peopleGroup) {
        if (searchQuery.isBlank()) {
            value = null
            return@produceState
        }
        // Exact text and named groups are answers already; Smart Search would only add guesses.
        if (queryPhrases != null || peopleGroup != null) {
            value = searchQuery to null
            return@produceState
        }
        delay(250)
        value = searchQuery to SmartSearch.search(context, searchQuery.trim())
    }
    val smartHits = smart?.second?.hits
    val smartPending = searchQuery.isNotBlank() && smart?.first != searchQuery &&
        remember(searching) { com.subhashh.lumen.ai.ModelDownloads.installed(context) != null }
    // People named like the search (every word starts one of the name's words): exact, so
    // right after name and date matches.
    val personMatches = remember(people, searchQuery, peopleGroup) {
        val terms = SearchIndex.terms(searchQuery)
        val group = peopleGroup
        if (group != null) people.filter { (p, _) -> group.any { it.id == p.id } }
        else if (terms.isEmpty() || queryPhrases != null) emptyList()
        else people.filter { (person, _) ->
            val words = SearchIndex.terms(person.name.orEmpty())
            terms.all { term -> words.any { it.startsWith(term) } }
        }
    }
    // Settings → Developer Options → Search Details; read again each time Search opens.
    val searchDetails = remember(searching) { prefs.searchDetails }
    // Exact name, folder and date matches first, then people of that name and photos with the
    // words in them (newest first), then Smart Search's, best first; each photo once.
    // With Search Details, one section per way of matching instead, each complete on its own.
    val searchSections = remember(keywordResults, captionMatches, personMatches, peopleGroup, queryPhrases, textMatches, smartHits, library, searchDetails) {
        // Quoted: exact text only, no name or date matches.
        val keyword = if (queryPhrases != null) emptyList() else keywordResults ?: return@remember null
        val byId = library.orEmpty().associateBy { it.id }
        // A named group: the items all of them are in; one person: theirs.
        val personIds = peopleGroup?.map { it.mediaIds }?.reduce { a, b -> a intersect b }?.toList()
            ?: personMatches.flatMap { it.first.mediaIds }
        if (!searchDetails) {
            val seen = keyword.mapTo(HashSet()) { it.id }
            val ofCaptions = captionMatches.orEmpty().mapNotNull { byId[it] }.filter { seen.add(it.id) }.sortedByDescending { it.takenAt }
            val ofPeople = personIds.mapNotNull { byId[it] }
                .filter { seen.add(it.id) }.sortedByDescending { it.takenAt }
            val ofText = textMatches.orEmpty().mapNotNull { byId[it] }.filter { seen.add(it.id) }.sortedByDescending { it.takenAt }
            val ofSmart = smartHits.orEmpty().mapNotNull { hit -> byId[hit.mediaId]?.takeIf { seen.add(it.id) } }
            return@remember listOf(SearchSection(SearchSection.All, 0, keyword + ofCaptions + ofPeople + ofText + ofSmart))
        }
        listOf(
            SearchSection(SearchSection.Names, R.string.main_search_section_names, keyword),
            SearchSection("captions", R.string.main_search_section_captions, captionMatches.orEmpty().mapNotNull { byId[it] }.sortedByDescending { it.takenAt }),
            SearchSection(
                SearchSection.People, R.string.main_people,
                personIds.distinct().mapNotNull { byId[it] }.sortedByDescending { it.takenAt },
            ),
            SearchSection(
                SearchSection.Text, R.string.main_search_section_text,
                textMatches.orEmpty().mapNotNull { byId[it] }.sortedByDescending { it.takenAt },
            ),
            SearchSection(
                SearchSection.Smart, R.string.main_search_section_smart, smartHits.orEmpty().mapNotNull { byId[it.mediaId] },
                scores = smartHits.orEmpty().associate { it.mediaId to it.score },
            ),
        )
    }
    LaunchedEffect(searchQuery) { searchExpanded = emptySet() }
    // A face group, not yet named, that Smart Search's matches are about: offered in the People row.
    // Smart Search's result is from before any naming, so once it's named it shows as a match instead.
    val unnamedSubject = remember(smart, allPeople) {
        val id = smart?.second?.unnamedSubject?.id ?: return@remember null
        allPeople.firstOrNull { it.first.id == id && !it.first.hidden && it.first.name == null }
    }
    val searchAlbums = remember(folders, searchQuery) {
        val terms = SearchIndex.terms(searchQuery)
        if (terms.isEmpty()) emptyList()
        else folders.filter { album ->
            val words = SearchIndex.terms(album.title)
            terms.all { term -> words.any { SearchIndex.matchScore(term, it) > 0 } }
        }
    }
    val searchSuggestions = remember(library, folders) {
        val years = library.orEmpty().asReversed().asSequence()
            .map { java.util.Calendar.getInstance().apply { timeInMillis = it.takenAt }.get(java.util.Calendar.YEAR).toString() }
            .distinct().take(2).toList()
        // Words the search index knows (in English for now), so they stay untranslated.
        listOf("Videos", "Screenshots", "Favorites") + years + folders.take(3).map { it.title }
    }
    val rememberSearch = { query: String ->
        prefs.addRecentSearch(query)
        recentSearches = prefs.recentSearches
    }
    // Suggested searches from what this library has (Smart Search), each with its best photo;
    // a different handful each time Search opens.
    val smartSuggestions by produceState<List<Pair<String, MediaItem>>>(emptyList(), searching, library) {
        if (!searching) return@produceState
        value = withContext(Dispatchers.IO) {
            val model = com.subhashh.lumen.ai.ModelDownloads.installed(context) ?: return@withContext emptyList()
            val byId = library.orEmpty().associateBy { it.id }
            com.subhashh.lumen.ai.SearchSuggestions.load(context, model)
                .mapNotNull { s -> byId[s.coverId]?.let { s.term to it } }
                .shuffled()
                .take(8)
        }
    }
    return SearchResults(searchSections, smartPending, personMatches, unnamedSubject, searchAlbums, searchSuggestions, rememberSearch, smartSuggestions)
}
