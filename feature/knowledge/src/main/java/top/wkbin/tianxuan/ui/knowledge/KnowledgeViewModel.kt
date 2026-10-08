package top.wkbin.tianxuan.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.core.database.KbDocumentEntity
import top.wkbin.tianxuan.harness.knowledge.KnowledgeHit
import top.wkbin.tianxuan.harness.knowledge.KnowledgeManager

class KnowledgeViewModel(
    private val knowledgeManager: KnowledgeManager,
) : ViewModel() {

    val documents: StateFlow<List<KbDocumentEntity>> =
        knowledgeManager.documents
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<KnowledgeHit>>(emptyList())
    val searchResults: StateFlow<List<KnowledgeHit>> = _searchResults

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding

    private val _reEmbedding = MutableStateFlow<String?>(null)
    val reEmbedding: StateFlow<String?> = _reEmbedding

    fun addDocument(name: String, content: String) {
        if (name.isBlank() || content.isBlank()) {
            _error.value = "名称和内容不能为空"
            return
        }
        viewModelScope.launch {
            _adding.value = true
            _error.value = null
            try {
                knowledgeManager.addDocument(name, content)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _adding.value = false
            }
        }
    }

    fun removeDocument(docId: String) {
        viewModelScope.launch {
            try {
                knowledgeManager.removeDocument(docId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun reEmbed(docId: String) {
        viewModelScope.launch {
            _reEmbedding.value = docId
            try {
                knowledgeManager.reEmbed(docId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _reEmbedding.value = null
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searching.value = true
            try {
                _searchResults.value = knowledgeManager.search(query, topK = 5)
            } catch (e: Exception) {
                _error.value = e.message
                _searchResults.value = emptyList()
            } finally {
                _searching.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
