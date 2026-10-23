package com.subhashh.lumen.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.subhashh.lumen.data.ExcludedFolders
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

    private val _hidden = MutableStateFlow<List<MediaItem>?>(null)
    /** Hidden photos and videos; null until scanned (only when the Hidden section opens). */
    val hidden: StateFlow<List<MediaItem>?> = _hidden.asStateFlow()

    /** Walks storage for hidden files (~2 s on a full phone), so only on demand. */
    fun loadHidden() {
        viewModelScope.launch { _hidden.value = HiddenMedia.scan() }
    }

    fun exclude(folders: Collection<String>) = setExcluded(_excluded.value + folders)

    fun include(folder: String) = setExcluded(_excluded.value - folder)

    private fun setExcluded(folders: Set<String>) {
        prefs.excludedFolders = folders
        _excluded.value = folders
    }
}
