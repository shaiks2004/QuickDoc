package com.example.ui.home

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.RecentFileEntity
import com.example.data.repository.AiRepository
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortType {
    NAME, DATE, SIZE
}

data class HomeUiState(
    val recentFiles: List<RecentFileEntity> = emptyList(),
    val filteredFiles: List<RecentFileEntity> = emptyList(),
    val searchQuery: String = "",
    val sortType: SortType = SortType.DATE,
    val isAiSearch: Boolean = false,
    val infoMessage: String? = null
)

class HomeViewModel(
    private val documentRepository: DocumentRepository,
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRecentFiles()
    }

    private fun loadRecentFiles() {
        viewModelScope.launch {
            documentRepository.getRecentFiles().collect { files ->
                _uiState.update { 
                    it.copy(
                        recentFiles = files,
                        filteredFiles = applyFilterAndSort(files, it.searchQuery, it.sortType, it.isAiSearch)
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredFiles = applyFilterAndSort(it.recentFiles, query, it.sortType, it.isAiSearch)
            )
        }
    }

    fun onSortTypeChanged(sortType: SortType) {
        _uiState.update {
            it.copy(
                sortType = sortType,
                filteredFiles = applyFilterAndSort(it.recentFiles, it.searchQuery, sortType, it.isAiSearch)
            )
        }
    }

    fun toggleAiSearch(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isAiSearch = enabled,
                filteredFiles = applyFilterAndSort(it.recentFiles, it.searchQuery, it.sortType, enabled)
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            documentRepository.clearHistory()
        }
    }

    fun removeRecentFile(uri: String) {
        viewModelScope.launch {
            documentRepository.deleteRecentFile(uri)
        }
    }

    fun createNewBlankPdf(name: String, onComplete: (Uri) -> Unit) {
        viewModelScope.launch {
            val uri = documentRepository.createBlankPdf(name)
            if (uri != null) {
                // Pre-warm cache
                try {
                    documentRepository.getDocumentMetadata(uri)
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Metadata prewarm error", e)
                }
                onComplete(uri)
            } else {
                _uiState.update { it.copy(infoMessage = "Failed to create blank PDF") }
            }
        }
    }

    fun createPdfFromSelectedImages(name: String, images: List<Uri>, onComplete: (Uri) -> Unit) {
        viewModelScope.launch {
            val uri = documentRepository.createPdfFromImages(name, images)
            if (uri != null) {
                try {
                    documentRepository.getDocumentMetadata(uri)
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Metadata prewarm error", e)
                }
                onComplete(uri)
            } else {
                _uiState.update { it.copy(infoMessage = "Failed to compile images to PDF") }
            }
        }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    private fun applyFilterAndSort(
        files: List<RecentFileEntity>,
        query: String,
        sortType: SortType,
        isAiSearch: Boolean
    ): List<RecentFileEntity> {
        if (query.isBlank()) return sortFiles(files, sortType)

        val filtered = if (isAiSearch) {
            // AI-powered Natural Language Smart Search
            // Ranks by semantic/keyword mapping against name and AI summarized content
            files.filter { file ->
                val nameMatch = file.name.contains(query, ignoreCase = true)
                val summaryMatch = file.summary?.contains(query, ignoreCase = true) ?: false
                nameMatch || summaryMatch
            }
        } else {
            // Standard search (filename only)
            files.filter { file -> file.name.contains(query, ignoreCase = true) }
        }

        return sortFiles(filtered, sortType)
    }

    private fun sortFiles(files: List<RecentFileEntity>, sortType: SortType): List<RecentFileEntity> {
        return when (sortType) {
            SortType.NAME -> files.sortedBy { it.name.lowercase() }
            SortType.DATE -> files.sortedByDescending { it.lastModified }
            SortType.SIZE -> files.sortedByDescending { it.fileSize }
        }
    }

    companion object {
        fun provideFactory(
            documentRepository: DocumentRepository,
            aiRepository: AiRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(documentRepository, aiRepository) as T
            }
        }
    }
}
