package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.RecentFileDao
import com.example.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiRepository(
    private val context: Context,
    private val apiService: GeminiApiService,
    private val recentFileDao: RecentFileDao
) {
    private val sharedPrefs = context.getSharedPreferences("quickdoc_settings", Context.MODE_PRIVATE)

    /**
     * Gets the active Gemini API key: either the user-defined override from Settings
     * or the secure injected key from BuildConfig (via Secrets panel).
     */
    private fun getApiKey(): String {
        val userKey = sharedPrefs.getString("custom_api_key", "")
        if (!userKey.isNullOrBlank()) {
            return userKey
        }
        return BuildConfig.GEMINI_API_KEY
    }

    /**
     * Summarizes document text.
     * Caches summary in the RecentFile table keyed by URI so subsequent opens are instant.
     */
    suspend fun summarizeDocument(uri: String, fullText: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API Key Missing. Please set your Gemini API Key in Settings to enable AI Summarization."
        }

        // Return cached summary if available
        val cached = recentFileDao.getRecentFileByUri(uri)
        if (!cached?.summary.isNullOrBlank()) {
            return@withContext cached!!.summary!!
        }

        val prompt = """
            You are a helpful, professional document assistant. Summarize the following document content in a highly structured manner.
            Provide:
            1. A concise 2-sentence general overview.
            2. 4-5 bullet points highlighting the most important key insights, actions, or data.
            Keep the tone objective and informative. Avoid any self-praise or conversational filler.
            
            Document text:
            ${fullText.take(6000)}
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(ContentPart(parts = listOf(PartData(text = prompt))))
        )

        try {
            val response = apiService.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )
            val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No summary could be generated."
            
            // Cache the summary in local database
            if (cached != null) {
                recentFileDao.insertRecentFile(cached.copy(summary = result))
            }
            
            return@withContext result
        } catch (e: Exception) {
            Log.e("AiRepository", "Failed to summarize document", e)
            return@withContext "Error generating summary: ${e.message}. Check your internet connection or API quota."
        }
    }

    /**
     * Chat with document (RAG-lite):
     * Split text into page-based chunks, score relevance, compile top contexts, and answer.
     */
    suspend fun chatWithDocument(
        question: String,
        pagesText: Map<Int, String>
    ): ChatAnswer = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext ChatAnswer(
                answer = "Please set your Gemini API Key in Settings to chat with documents.",
                citations = emptyList()
            )
        }

        // Simple and fast RAG-lite scoring mechanism
        // Rank pages based on keyword relevance to user query
        val scoredPages = pagesText.map { (pageIndex, text) ->
            var score = 0
            val words = question.lowercase().split("\\s+".toRegex())
            words.forEach { word ->
                if (word.length > 2 && text.lowercase().contains(word)) {
                    score += 1
                }
            }
            pageIndex to score
        }.sortedByDescending { it.second }

        // Select top 3 relevant pages for grounding
        val topPages = scoredPages.take(3).filter { it.second > 0 }.map { it.first }
        val citations = if (topPages.isNotEmpty()) topPages else listOf(0)
        
        val contextBuilder = StringBuilder()
        citations.forEach { pageIdx ->
            contextBuilder.append("[Page ${pageIdx + 1} Content]:\n")
            contextBuilder.append(pagesText[pageIdx]?.take(1500) ?: "")
            contextBuilder.append("\n\n")
        }

        val prompt = """
            You are an expert document reader. Answer the user's question using ONLY the provided document context below.
            State clearly which pages the information was drawn from.
            If the answer cannot be found in the context, say "I cannot find that information in this document."
            
            User Question:
            $question
            
            Context:
            $contextBuilder
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(ContentPart(parts = listOf(PartData(text = prompt))))
        )

        try {
            val response = apiService.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )
            val answer = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No response generated."
            
            return@withContext ChatAnswer(
                answer = answer,
                citations = citations.map { it + 1 } // Return 1-based page numbers
            )
        } catch (e: Exception) {
            Log.e("AiRepository", "Failed chat with document", e)
            return@withContext ChatAnswer(
                answer = "Error generating answer: ${e.message}",
                citations = emptyList()
            )
        }
    }

    /**
     * OCR via Gemini Vision: Extracts text from images or handwriting using multimodal capabilities.
     */
    suspend fun performOcrOnImage(base64Image: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "OCR disabled. Set a Gemini API Key in Settings."
        }

        val prompt = "Transcribe all visible text, paragraphs, and handwriting in this image. Do not add any conversational remarks."
        val request = GenerateContentRequest(
            contents = listOf(
                ContentPart(
                    parts = listOf(
                        PartData(text = prompt),
                        PartData(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            )
        )

        try {
            val response = apiService.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )
            return@withContext response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No text detected in image."
        } catch (e: Exception) {
            Log.e("AiRepository", "OCR API call failed", e)
            return@withContext "OCR extraction failed: ${e.message}"
        }
    }

    /**
     * Smart rename: suggests clean file names based on contents
     */
    suspend fun suggestSmartRename(fullText: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Invoice_Doc.pdf"
        }

        val prompt = """
            Analyze the document text and suggest a single, short, clean, descriptive file name with '.pdf' extension (e.g. Invoice_Acme_March2026.pdf).
            Do not include any other text or conversational remarks. Return ONLY the suggested filename.
            
            Document Text:
            ${fullText.take(1500)}
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(ContentPart(parts = listOf(PartData(text = prompt))))
        )

        try {
            val response = apiService.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )
            return@withContext response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "SmartDocument.pdf"
        } catch (e: Exception) {
            Log.e("AiRepository", "Smart rename failed", e)
            return@withContext "SmartDocument.pdf"
        }
    }

    /**
     * Translates document text to a target language.
     */
    suspend fun translateText(text: String, targetLanguage: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Set an API Key in settings to translate documents."
        }

        val prompt = """
            Translate the following text into $targetLanguage. Maintain the structural layout and paragraphs exactly.
            Do not add any translator notes, introductory text, or side comments. Only return the final translated text.
            
            Text:
            $text
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(ContentPart(parts = listOf(PartData(text = prompt))))
        )

        try {
            val response = apiService.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )
            return@withContext response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Translation empty."
        } catch (e: Exception) {
            Log.e("AiRepository", "Translation failed", e)
            return@withContext "Translation error: ${e.message}"
        }
    }
}

data class ChatAnswer(
    val answer: String,
    val citations: List<Int>
)
