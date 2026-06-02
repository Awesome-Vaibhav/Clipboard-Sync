package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ClipboardDatabase
import com.example.data.ClipboardItem
import com.example.data.ClipboardRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClipboardRepository
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        val database = ClipboardDatabase.getDatabase(application)
        repository = ClipboardRepository(database.clipboardDao())
    }

    val clipboardItems: StateFlow<List<ClipboardItem>> = _searchQuery
        .debounce(100) // Lower delay for instant responsiveness
        .flatMapLatest { query ->
            repository.searchItems(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun togglePin(item: ClipboardItem) {
        viewModelScope.launch {
            repository.togglePin(item)
        }
    }

    fun deleteItem(item: ClipboardItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun copyToClipboardDirect(text: String, clipboardManager: ClipboardManager) {
        val clip = ClipData.newPlainText("Copied Text", text)
        clipboardManager.setPrimaryClip(clip)
        viewModelScope.launch {
            repository.saveCopiedText(text)
        }
    }

    fun addNewManualClip(text: String) {
        viewModelScope.launch {
            repository.saveCopiedText(text)
        }
    }
}
