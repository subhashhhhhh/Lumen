package com.subhashh.lumen.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.data.MediaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)

    private val _items = MutableStateFlow<List<MediaItem>?>(null)
    /** Null until the first load finishes. */
    val items: StateFlow<List<MediaItem>?> = _items.asStateFlow()

    private val _trash = MutableStateFlow<List<MediaItem>>(emptyList())
    val trash: StateFlow<List<MediaItem>> = _trash.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val library = async { repository.loadLibrary() }
            val trash = async { repository.loadTrash() }
            _items.value = library.await()
            _trash.value = trash.await()
        }
    }
}
