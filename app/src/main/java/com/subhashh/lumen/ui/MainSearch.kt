package com.subhashh.lumen.ui

import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.SmartSearch
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.SearchIndex
import com.subhashh.lumen.ui.search.SearchSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
