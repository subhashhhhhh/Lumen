package com.subhashh.lumen.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.subhashh.lumen.data.ExcludedFolders
import com.subhashh.lumen.data.HiddenIndex
import com.subhashh.lumen.data.HiddenMedia
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.MediaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val prefs = LumenPrefs(application)

    /** Everything MediaStore has, excluded folders included (Settings needs them). */
    private val _allItems = MutableStateFlow<List<MediaItem>?>(null)
    val allItems: StateFlow<List<MediaItem>?> = _allItems.asStateFlow()

    private val _excluded = MutableStateFlow(prefs.excludedFolders)
    /** Folders (relative paths) Lumen ignores, each with its subfolders. */
    val excluded: StateFlow<Set<String>> = _excluded.asStateFlow()

    /** The library as every screen sees it: excluded folders removed. Null until first loaded. */
    val items: StateFlow<List<MediaItem>?> = combine(_allItems, _excluded) { all, excluded ->
        all?.let { list -> if (excluded.isEmpty()) list else list.filterNot { ExcludedFolders.covers(excluded, it) } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _trash = MutableStateFlow<List<MediaItem>>(emptyList())
    val trash: StateFlow<List<MediaItem>> = _trash.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val library = async { repository.loadLibrary() }
            val trash = async { repository.loadTrash() }
            _allItems.value = library.await()
            _trash.value = trash.await()
        }
    }

    /** Raw scan result, before exclusions; null until loaded (only once Hidden is opened). */
    private val _hiddenAll = MutableStateFlow<List<MediaItem>?>(null)

    private val _hiddenExcluded = MutableStateFlow(prefs.hiddenExcludedFolders)
    /** Folders left out of Hidden only; shown inside Hidden, behind its lock. */
    val hiddenExcluded: StateFlow<Set<String>> = _hiddenExcluded.asStateFlow()

    /** Hidden photos and videos, minus both kinds of excluded folder. Null until first loaded. */
    val hidden: StateFlow<List<MediaItem>?> = combine(_hiddenAll, _excluded, _hiddenExcluded) { all, excluded, hiddenExcluded ->
        all?.filterNot { ExcludedFolders.covers(excluded, it) || ExcludedFolders.covers(hiddenExcluded, it) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var hiddenScan: kotlinx.coroutines.Job? = null

    /**
     * Shows the last scan straight away (from memory, else from the saved index), then rescans
     * storage behind it and swaps in what changed. Only the very first scan shows a spinner.
     */
    fun loadHidden() {
        hiddenScan?.cancel()
        hiddenScan = viewModelScope.launch {
            val app = getApplication<Application>()
            if (_hiddenAll.value == null) _hiddenAll.value = HiddenIndex.load(app)
            // Unchanged files keep the sizes already read, so they compare equal and nothing redraws.
            val known = _hiddenAll.value.orEmpty().associateBy { it.uri.path }
            val found = HiddenMedia.scan().map { item ->
                known[item.uri.path]?.takeIf { it.takenAt == item.takenAt && it.width > 0 } ?: item
            }
            if (found != _hiddenAll.value) _hiddenAll.value = found
            val sized = HiddenMedia.withSizes(found)
            if (sized != _hiddenAll.value) _hiddenAll.value = sized
            HiddenIndex.save(app, sized)
        }
    }

    fun excludeFromHidden(folders: Collection<String>) = setHiddenExcluded(_hiddenExcluded.value + folders)

    fun includeInHidden(folder: String) = setHiddenExcluded(_hiddenExcluded.value - folder)

    private fun setHiddenExcluded(folders: Set<String>) {
        prefs.hiddenExcludedFolders = folders
        _hiddenExcluded.value = folders
    }

    fun exclude(folders: Collection<String>) = setExcluded(_excluded.value + folders)

    fun include(folder: String) = setExcluded(_excluded.value - folder)

    private fun setExcluded(folders: Set<String>) {
        prefs.excludedFolders = folders
        _excluded.value = folders
    }
}
