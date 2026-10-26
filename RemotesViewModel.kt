package com.akillikumanda.app.feature.remotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akillikumanda.app.core.database.SavedRemoteEntity
import com.akillikumanda.app.data.remote.SaveResult
import com.akillikumanda.app.data.remote.SavedRemoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface RemoteFilter {
    data object All : RemoteFilter
    data object Favorites : RemoteFilter
    data class Category(val name: String) : RemoteFilter
}

data class RemotesUiState(
    val all: List<SavedRemoteEntity> = emptyList(),
    val visible: List<SavedRemoteEntity> = emptyList(),
    val query: String = "",
    val filter: RemoteFilter = RemoteFilter.All,
    val message: String? = null,
)

class RemotesViewModel(private val repo: SavedRemoteRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<RemoteFilter>(RemoteFilter.All)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<RemotesUiState> = combine(repo.observeAll(), query, filter, message) { all, q, f, m ->
        val needle = q.trim().lowercase()
        val visible = all.filter { r ->
            val passFilter = when (f) {
                RemoteFilter.All -> true
                RemoteFilter.Favorites -> r.favorite
                is RemoteFilter.Category -> r.category == f.name
            }
            val passQuery = needle.isEmpty() || listOfNotNull(r.name, r.brand, r.model, r.room, r.note)
                .any { it.lowercase().contains(needle) }
            passFilter && passQuery
        }
        RemotesUiState(all, visible, q, f, m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemotesUiState())

    fun setQuery(v: String) { query.value = v.take(40) }
    fun setFilter(f: RemoteFilter) { filter.value = f }
    fun clearMessage() { message.value = null }

    fun toggleFavorite(r: SavedRemoteEntity) = viewModelScope.launch { repo.setFavorite(r.id, !r.favorite) }

    fun rename(r: SavedRemoteEntity, name: String) = viewModelScope.launch {
        message.value = when (val res = repo.rename(r.id, name)) {
            is SaveResult.Saved -> "Ad güncellendi"
            is SaveResult.Duplicate -> "Bu profil için aynı adla başka bir kumanda var"
            is SaveResult.Invalid -> res.reason
        }
    }

    fun delete(r: SavedRemoteEntity) = viewModelScope.launch {
        repo.delete(r)
        message.value = "\"${r.name}\" silindi"
    }
}
