package com.salahlock.app.ui.knowledge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.db.entity.HadithEntity
import com.salahlock.app.data.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HadithReaderUiState(
    val title: String = "",
    val hadiths: Map<Int, HadithEntity> = emptyMap(),
    val totalCount: Int = 0,
    val initialIndex: Int = 0,
    val isLoading: Boolean = true,
    val isPaging: Boolean = false,
    val isSingleMode: Boolean = false,
)

class HadithReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication

    // Use the application-scoped singleton — prevents duplicate Retrofit/Room instances.
    private val repository = app.knowledgeRepository
    private val userPrefs = UserPreferences(application)

    private val _uiState = MutableStateFlow(HadithReaderUiState())
    val uiState: StateFlow<HadithReaderUiState> = _uiState.asStateFlow()

    private var currentMode: LoadMode? = null
    private val pageSize = 30
    private var readPositionKey: String = ""
    private val loadedChunks = mutableSetOf<Int>()

    sealed class LoadMode {
        data class Topic(val topic: String, val language: String) : LoadMode()
        data class Book(val collection: String, val bookNumber: String, val language: String) : LoadMode()
        data class Single(val hadithId: String) : LoadMode()
    }

    fun loadTopic(topic: String, language: String) {
        currentMode = LoadMode.Topic(topic, language)
        readPositionKey = "topic_${topic}_$language"
        loadedChunks.clear()

        viewModelScope.launch {
            val count = repository.getHadithCountByTopic(topic, language)
            val savedIndex = userPrefs.getHadithPosition(readPositionKey).first().coerceAtMost(maxOf(0, count - 1))

            _uiState.value = HadithReaderUiState(
                title = topic,
                isLoading = false,
                totalCount = count,
                initialIndex = savedIndex,
            )
            requestPage(savedIndex)
        }
    }

    fun loadBook(collection: String, bookNumber: String, language: String, startHadithId: String? = null) {
        val bookName = "${collection.replaceFirstChar { it.uppercase() }} — Book $bookNumber"
        currentMode = LoadMode.Book(collection, bookNumber, language)
        readPositionKey = "book_${collection}_${bookNumber}_$language"
        loadedChunks.clear()

        viewModelScope.launch {
            val count = repository.getHadithCountByBook(collection, bookNumber, language)

            // If a specific hadith ID is provided (from search navigation), jump to its position.
            val startIndex = if (startHadithId != null) {
                repository.getHadithPositionInBook(collection, bookNumber, language, startHadithId)
                    .coerceAtMost(maxOf(0, count - 1))
            } else {
                userPrefs.getHadithPosition(readPositionKey).first().coerceAtMost(maxOf(0, count - 1))
            }

            // Update position key description once we know the book title
            val bookTitle = repository.getBookTitle(collection, bookNumber)
            val displayTitle = if (bookTitle != null)
                "${collection.replaceFirstChar { it.uppercase() }} — $bookTitle"
            else bookName

            _uiState.value = HadithReaderUiState(
                title = displayTitle,
                isLoading = false,
                totalCount = count,
                initialIndex = startIndex,
            )
            requestPage(startIndex)
        }
    }

    /**
     * Load a single hadith directly by its entity ID (used when navigating from search results).
     * The id format is "{collection}-{globalNumber}-{language}", e.g., "bukhari-657-eng".
     */
    fun loadSingle(hadithId: String) {
        currentMode = LoadMode.Single(hadithId)
        readPositionKey = ""
        loadedChunks.clear()
        _uiState.value = HadithReaderUiState(isLoading = true)

        viewModelScope.launch {
            // Parse the stable ID components: "bukhari-657-eng"
            val parts = hadithId.split("-")
            val collection = parts.getOrNull(0)
            val globalNum = parts.getOrNull(1)
            val language = parts.getOrNull(2) ?: "eng"

            val hadith = if (collection != null && globalNum != null) {
                repository.getHadithByGlobalNumber(collection, globalNum, language)
            } else null

            if (hadith != null) {
                val bookTitle = repository.getBookTitle(hadith.collection, hadith.bookNumber)
                val title = "${hadith.collection.replaceFirstChar { it.uppercase() }}" +
                        (if (bookTitle != null) " — $bookTitle" else "")
                _uiState.value = HadithReaderUiState(
                    title = title,
                    hadiths = mapOf(0 to hadith),
                    totalCount = 1,
                    initialIndex = 0,
                    isLoading = false,
                    isSingleMode = true,
                )
            } else {
                _uiState.value = HadithReaderUiState(isLoading = false, totalCount = 0)
            }
        }
    }

    fun requestPage(index: Int) {
        if (currentMode is LoadMode.Single) return // Single mode has no paging
        val chunkIndex = index / pageSize
        if (loadedChunks.contains(chunkIndex)) return
        loadedChunks.add(chunkIndex)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPaging = true)
            val offset = chunkIndex * pageSize

            val newHadiths = when (val mode = currentMode) {
                is LoadMode.Topic -> repository.getHadithsByTopic(mode.topic, mode.language, pageSize, offset)
                is LoadMode.Book -> repository.getHadithsByBook(mode.collection, mode.bookNumber, mode.language, pageSize, offset)
                else -> emptyList()
            }

            val newMap = newHadiths.mapIndexed { i, hadith -> (offset + i) to hadith }.toMap()
            _uiState.value = _uiState.value.copy(
                hadiths = _uiState.value.hadiths + newMap,
                isPaging = false,
            )

            // Prefetch next chunk if near end
            val nextChunk = chunkIndex + 1
            if ((index % pageSize) > (pageSize * 0.8) &&
                !loadedChunks.contains(nextChunk) &&
                (nextChunk * pageSize) < _uiState.value.totalCount
            ) {
                requestPage(nextChunk * pageSize)
            }
        }
    }

    fun savePosition(index: Int) {
        if (readPositionKey.isNotEmpty() && index >= 0) {
            viewModelScope.launch {
                userPrefs.saveHadithPosition(readPositionKey, index)
                // Feed the "recently read" list from the hadith actually on screen.
                _uiState.value.hadiths[index]?.let {
                    repository.updateReadTimestamp(it.id, System.currentTimeMillis())
                }
            }
        }
    }

    fun toggleBookmark(id: String, isBookmarked: Boolean) {
        // Topic mode = Knowledge module reader; book/single mode = Hadith module.
        val source = if (currentMode is LoadMode.Topic)
            com.salahlock.app.data.repository.KnowledgeRepository.BOOKMARK_SOURCE_KNOWLEDGE
        else
            com.salahlock.app.data.repository.KnowledgeRepository.BOOKMARK_SOURCE_HADITH
        viewModelScope.launch {
            repository.toggleHadithBookmark(id, isBookmarked, source)
            val updatedMap = _uiState.value.hadiths.mapValues { (_, hadith) ->
                if (hadith.id == id) hadith.copy(isBookmarked = isBookmarked) else hadith
            }
            _uiState.value = _uiState.value.copy(hadiths = updatedMap)
        }
    }
}
