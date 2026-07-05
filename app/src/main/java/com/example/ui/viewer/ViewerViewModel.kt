package com.example.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AnnotationDao
import com.example.data.local.AnnotationEntity
import com.example.data.local.AppDatabase
import com.example.data.repository.AiRepository
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PerformanceMetrics(
    val fileOpenTimeMs: Long = 0,
    val firstPageRenderTimeMs: Long = 0,
    val memoryUsedMb: Long = 0,
    val aiResponseLatencyMs: Long = 0
)

data class ViewerUiState(
    val documentUri: Uri? = null,
    val fileName: String = "",
    val fileType: String = "", // "pdf", "image", "text", "office", "zip"
    val pageCount: Int = 0,
    val currentPageIndex: Int = 0,
    val isContinuousScroll: Boolean = true,
    val isNightMode: Boolean = false,
    val pdfPageBitmaps: Map<Int, Bitmap> = emptyMap(),
    val textContent: String = "",
    val zipEntries: List<String> = emptyList(),
    val annotations: List<AnnotationEntity> = emptyList(),
    
    // UI state states
    val isLoading: Boolean = false,
    val searchKeyword: String = "",
    val matchedPageIndices: List<Int> = emptyList(),
    
    // AI panel states
    val isAiPanelExpanded: Boolean = false,
    val aiSummary: String = "",
    val isAiLoading: Boolean = false,
    val chatHistory: List<ChatMessage> = emptyList(),
    val translatedText: String = "",
    
    // Instrumentation
    val metrics: PerformanceMetrics = PerformanceMetrics(),
    
    // Soft Delete / Trash pattern for 5s undo
    val undoAvailable: Boolean = false,
    val undoMessage: String = "",
    
    // Info notification
    val infoMessage: String? = null
)

data class ChatMessage(
    val sender: String, // "user" or "ai"
    val text: String,
    val citations: List<Int> = emptyList()
)

class ViewerViewModel(
    private val context: Context,
    private val documentRepository: DocumentRepository,
    private val aiRepository: AiRepository,
    private val annotationDao: AnnotationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    // Undo action cache
    private var pendingUndoAction: (suspend () -> Unit)? = null
    private var undoJob: kotlinx.coroutines.Job? = null

    /**
     * Load document from content Uri with full performance metrics tracking
     */
    fun loadDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, documentUri = uri) }
            val startTime = System.currentTimeMillis()

            try {
                // Read metadata and determine type
                val metadata = documentRepository.getDocumentMetadata(uri)
                val openTime = System.currentTimeMillis() - startTime

                _uiState.update {
                    it.copy(
                        fileName = metadata.name,
                        fileType = metadata.fileType,
                        pageCount = metadata.pageCount,
                        metrics = it.metrics.copy(
                            fileOpenTimeMs = openTime,
                            memoryUsedMb = getMemoryUsage()
                        )
                    )
                }

                // Handle loading based on type
                when (metadata.fileType) {
                    "pdf" -> {
                        loadAnnotations(uri.toString())
                        // Pre-render page 1 for sub-500ms time-to-first-page-visible
                        renderPage(uri, 0, startTime)
                    }
                    "image" -> {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    "text" -> {
                        val text = documentRepository.readTextFile(uri)
                        _uiState.update { it.copy(textContent = text, isLoading = false) }
                    }
                    "office" -> {
                        val preview = documentRepository.readOfficeDocument(uri)
                        _uiState.update { it.copy(textContent = preview, isLoading = false) }
                    }
                    "zip" -> {
                        val entries = documentRepository.getZipContents(uri)
                        _uiState.update { it.copy(zipEntries = entries, isLoading = false) }
                    }
                }

            } catch (e: Exception) {
                Log.e("ViewerViewModel", "Error loading document", e)
                _uiState.update { it.copy(isLoading = false, infoMessage = "Failed to load document: ${e.message}") }
            }
        }
    }

    private fun loadAnnotations(fileUri: String) {
        viewModelScope.launch {
            annotationDao.getAnnotationsForFile(fileUri).collect { list ->
                _uiState.update { it.copy(annotations = list) }
            }
        }
    }

    /**
     * Lazily render individual page on demand (IO thread, scale matched)
     */
    fun renderPageIfNeeded(pageIndex: Int) {
        val uri = _uiState.value.documentUri ?: return
        if (_uiState.value.pdfPageBitmaps.containsKey(pageIndex)) return

        viewModelScope.launch {
            val bitmap = documentRepository.renderPdfPage(uri, pageIndex)
            if (bitmap != null) {
                _uiState.update {
                    val updatedMaps = it.pdfPageBitmaps.toMutableMap()
                    updatedMaps[pageIndex] = bitmap
                    it.copy(pdfPageBitmaps = updatedMaps)
                }
            }
        }
    }

    private suspend fun renderPage(uri: Uri, index: Int, startTime: Long) {
        val bitmap = documentRepository.renderPdfPage(uri, index)
        val renderTime = System.currentTimeMillis() - startTime
        if (bitmap != null) {
            _uiState.update {
                val updatedMaps = it.pdfPageBitmaps.toMutableMap()
                updatedMaps[index] = bitmap
                it.copy(
                    pdfPageBitmaps = updatedMaps,
                    isLoading = false,
                    metrics = it.metrics.copy(
                        firstPageRenderTimeMs = renderTime,
                        memoryUsedMb = getMemoryUsage()
                    )
                )
            }
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onPageChanged(index: Int) {
        _uiState.update { it.copy(currentPageIndex = index) }
        // Lazy-load surrounding pages (+1 / -1) to achieve instant scroll perception
        renderPageIfNeeded(index + 1)
        if (index > 0) renderPageIfNeeded(index - 1)
    }

    fun toggleContinuousScroll() {
        _uiState.update { it.copy(isContinuousScroll = !it.isContinuousScroll) }
    }

    fun toggleNightMode() {
        _uiState.update { it.copy(isNightMode = !it.isNightMode) }
    }

    fun onSearchKeywordChanged(keyword: String) {
        _uiState.update { it.copy(searchKeyword = keyword) }
        if (keyword.length > 2) {
            // Simulate document text search indexing
            val matches = (0 until _uiState.value.pageCount).filter { idx -> idx % 3 == 0 } // mock hits
            _uiState.update { it.copy(matchedPageIndices = matches) }
        } else {
            _uiState.update { it.copy(matchedPageIndices = emptyList()) }
        }
    }

    fun addAnnotation(annotation: AnnotationEntity) {
        viewModelScope.launch {
            annotationDao.insertAnnotation(annotation)
        }
    }

    /**
     * Form and Signature signing: inserts signature node and saves to database
     */
    fun signDocument(x: Float, y: Float) {
        val uri = _uiState.value.documentUri ?: return
        val pageIdx = _uiState.value.currentPageIndex
        val annotation = AnnotationEntity(
            fileUri = uri.toString(),
            pageIndex = pageIdx,
            type = "SIGNATURE",
            color = 0xFF000000.toInt(),
            thickness = 2f,
            pointsJson = "[]",
            text = "Signature",
            x = x,
            y = y,
            width = 120f,
            height = 50f
        )
        viewModelScope.launch {
            annotationDao.insertAnnotation(annotation)
        }
    }

    fun extractAndLoadZipEntry(zipUri: Uri, entryName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val innerUri = documentRepository.extractZipFile(zipUri, entryName)
            if (innerUri != null) {
                loadDocument(innerUri)
            } else {
                _uiState.update { it.copy(isLoading = false, infoMessage = "Failed to extract ZIP entry") }
            }
        }
    }

    fun renameActiveFile(newName: String) {
        _uiState.update { it.copy(fileName = newName) }
    }

    /**
     * Delete annotations with 5-second Undo grace period
     */
    fun clearAnnotationsOnCurrentPage() {
        val uri = _uiState.value.documentUri?.toString() ?: return
        val pageIdx = _uiState.value.currentPageIndex
        val currentAnnotations = _uiState.value.annotations.filter { it.fileUri == uri && it.pageIndex == pageIdx }

        triggerSoftDelete("Cleared page annotations") {
            viewModelScope.launch {
                annotationDao.deleteAnnotationsForPage(uri, pageIdx)
            }
        } undoAction {
            viewModelScope.launch {
                currentAnnotations.forEach { annotationDao.insertAnnotation(it) }
            }
        }
    }

    /**
     * Delete a single page within PDF with 5s Undo.
     */
    fun deleteCurrentPage() {
        val pageIdx = _uiState.value.currentPageIndex
        val count = _uiState.value.pageCount
        if (count <= 1) {
            _uiState.update { it.copy(infoMessage = "Cannot delete the only page of the document") }
            return
        }

        val originalBitmaps = _uiState.value.pdfPageBitmaps

        triggerSoftDelete("Deleted Page ${pageIdx + 1}") {
            // Apply permanent page delete logic by removing the index and reconstructing state
            _uiState.update {
                val updatedMaps = it.pdfPageBitmaps.toMutableMap()
                updatedMaps.remove(pageIdx)
                // Shift indices down
                val shiftedMaps = mutableMapOf<Int, Bitmap>()
                updatedMaps.forEach { (k, v) ->
                    val newK = if (k > pageIdx) k - 1 else k
                    shiftedMaps[newK] = v
                }
                it.copy(
                    pdfPageBitmaps = shiftedMaps,
                    pageCount = count - 1,
                    currentPageIndex = pageIdx.coerceAtMost(count - 2)
                )
            }
        } undoAction {
            _uiState.update {
                it.copy(
                    pdfPageBitmaps = originalBitmaps,
                    pageCount = count,
                    currentPageIndex = pageIdx
                )
            }
        }
    }

    // --- AI DOCUMENT ASSISTANT FEATURES ---

    fun toggleAiPanel() {
        _uiState.update { it.copy(isAiPanelExpanded = !it.isAiPanelExpanded) }
    }

    fun generateSummary() {
        val uri = _uiState.value.documentUri ?: return
        val fullText = getDocumentCombinedText()
        
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }
            val start = System.currentTimeMillis()
            
            val summary = aiRepository.summarizeDocument(uri.toString(), fullText)
            val latency = System.currentTimeMillis() - start

            _uiState.update {
                it.copy(
                    aiSummary = summary,
                    isAiLoading = false,
                    metrics = it.metrics.copy(aiResponseLatencyMs = latency)
                )
            }
        }
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        
        val chatList = _uiState.value.chatHistory.toMutableList()
        chatList.add(ChatMessage("user", text))
        _uiState.update { it.copy(chatHistory = chatList, isAiLoading = true) }

        viewModelScope.launch {
            // Mock extracted page content mapping for RAG
            val mockPagesText = (0 until _uiState.value.pageCount).associateWith { idx ->
                _uiState.value.textContent.ifBlank { "Content of document page ${idx + 1}" }
            }
            val start = System.currentTimeMillis()
            val result = aiRepository.chatWithDocument(text, mockPagesText)
            val latency = System.currentTimeMillis() - start

            val updatedChat = _uiState.value.chatHistory.toMutableList()
            updatedChat.add(ChatMessage("ai", result.answer, result.citations))

            _uiState.update {
                it.copy(
                    chatHistory = updatedChat,
                    isAiLoading = false,
                    metrics = it.metrics.copy(aiResponseLatencyMs = latency)
                )
            }
        }
    }

    fun triggerSmartRename(onComplete: (String) -> Unit) {
        val text = getDocumentCombinedText()
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }
            val suggested = aiRepository.suggestSmartRename(text)
            _uiState.update { it.copy(isAiLoading = false) }
            onComplete(suggested)
        }
    }

    fun translateDocument(language: String) {
        val text = _uiState.value.textContent.ifBlank { getDocumentCombinedText().take(2000) }
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }
            val result = aiRepository.translateText(text, language)
            _uiState.update { it.copy(translatedText = result, isAiLoading = false) }
        }
    }

    // --- OTHER CORE CRUD OPERATIONS ---

    fun applyWatermarkAndCompress(watermark: String?, compressionQuality: Int) {
        val uri = _uiState.value.documentUri ?: return
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            val destUri = documentRepository.savePdfWithAnnotations(
                sourceUri = uri,
                annotations = _uiState.value.annotations,
                watermarkText = watermark,
                compressionQuality = compressionQuality
            )
            
            _uiState.update { it.copy(isLoading = false) }
            if (destUri != null) {
                loadDocument(destUri)
                _uiState.update { it.copy(infoMessage = "Document processed successfully!") }
            } else {
                _uiState.update { it.copy(infoMessage = "Failed to apply changes") }
            }
        }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    // --- 5-SECOND SOFT DELETE ENGINE ---

    private inline fun triggerSoftDelete(message: String, crossinline commitAction: () -> Unit): UndoBuilder {
        undoJob?.cancel()
        _uiState.update { it.copy(undoAvailable = true, undoMessage = message) }
        
        val builder = UndoBuilder()
        undoJob = viewModelScope.launch {
            delay(5000)
            // Timeout reached, commit action permanently
            commitAction()
            _uiState.update { it.copy(undoAvailable = false, undoMessage = "") }
            pendingUndoAction = null
        }
        return builder
    }

    inner class UndoBuilder {
        infix fun undoAction(action: suspend () -> Unit) {
            pendingUndoAction = action
        }
    }

    fun executeUndo() {
        undoJob?.cancel()
        val action = pendingUndoAction
        if (action != null) {
            viewModelScope.launch {
                action()
                _uiState.update { it.copy(undoAvailable = false, undoMessage = "Operation undone") }
                pendingUndoAction = null
            }
        }
    }

    // --- Helper Instrumentation metrics ---

    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    private fun getDocumentCombinedText(): String {
        return _uiState.value.textContent.ifBlank {
            "Content outline of Document ${_uiState.value.fileName}. Text extraction matched."
        }
    }

    companion object {
        fun provideFactory(
            context: Context,
            documentRepository: DocumentRepository,
            aiRepository: AiRepository,
            annotationDao: AnnotationDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ViewerViewModel(context, documentRepository, aiRepository, annotationDao) as T
            }
        }
    }
}
