package io.retroshelf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CatalogueState(val games: List<Game> = emptyList(), val loading: Boolean = false, val error: String? = null, val offline: Boolean = false, val nextPage: Int? = null, val total: Int? = null, val loadingMore: Boolean = false)
class ShelfViewModel(application: Application) : AndroidViewModel(application) {
    val graph = AppGraph(application)
    private val preferences = application.getSharedPreferences("settings", 0)
    private val _dark = MutableStateFlow(preferences.getBoolean("dark", true))
    val dark = _dark.asStateFlow()
    private val _public = MutableStateFlow(preferences.getBoolean("public", true))
    val publicDownloads = _public.asStateFlow()
    private val _disabled = MutableStateFlow(preferences.getStringSet("disabled", emptySet())!!.toSet())
    val disabled = _disabled.asStateFlow()
    private val _status = MutableStateFlow<Map<String,String>>(emptyMap())
    val status = _status.asStateFlow()
    private val _catalogue = MutableStateFlow(CatalogueState())
    val catalogue = _catalogue.asStateFlow()
    val favorites = graph.repository.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recent = graph.repository.recent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val downloads = graph.db.shelf().downloads().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 5)
    private var loadJob: Job? = null
    private var active: String? = null
    init { viewModelScope.launch { while(isActive) { safely { graph.downloads.refresh() }; delay(1500) } } }
    fun setDark(value: Boolean) { preferences.edit().putBoolean("dark", value).apply(); _dark.value = value }
    fun setPublic(value: Boolean) { preferences.edit().putBoolean("public", value).apply(); _public.value = value }
    fun enable(id: String, value: Boolean) { _disabled.value = if(value) _disabled.value - id else _disabled.value + id; preferences.edit().putStringSet("disabled", _disabled.value).apply() }
    fun load(provider: String, refresh: Boolean = false) {
        if(provider == active && !refresh && _catalogue.value.games.isNotEmpty()) return
        loadJob?.cancel(); active = provider
        _catalogue.value = CatalogueState(loading = true)
        loadJob = viewModelScope.launch {
            try {
                val (games, offline) = graph.repository.catalogue(provider, refresh)
                val snapshot = graph.db.shelf().snapshot(provider)
                _catalogue.value = CatalogueState(games, offline = offline, nextPage = snapshot?.nextPage, total = snapshot?.total)
                _status.value += provider to if(offline) "Offline · cached catalogue" else "Catalogue available"
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { _catalogue.value = CatalogueState(error = e.message ?: "Connection failed"); _status.value += provider to "Connection failed" }
        }
    }
    fun loadMore(provider: String) {
        if(active != provider || _catalogue.value.loading || _catalogue.value.loadingMore || _catalogue.value.nextPage == null) return
        _catalogue.value = _catalogue.value.copy(loadingMore = true)
        loadJob = viewModelScope.launch {
            try {
                val games = graph.repository.loadMore(provider)
                val snapshot = graph.db.shelf().snapshot(provider)
                if(active == provider) _catalogue.value = _catalogue.value.copy(games = games, nextPage = snapshot?.nextPage, total = snapshot?.total, loadingMore = false, offline = false)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                if(active == provider) _catalogue.value = _catalogue.value.copy(loadingMore = false)
                messages.emit(e.message ?: "Could not load more listings. Saved entries are still available.")
            }
        }
    }
    fun view(game: Game) { viewModelScope.launch { safely { graph.repository.view(game) } } }
    fun favorite(game: Game) { viewModelScope.launch { safely { graph.repository.toggleFavorite(game) } } }
    fun download(game: Game, file: GameFile) { viewModelScope.launch { safely { graph.downloads.enqueue(game.title, file, publicDownloads.value); messages.emit("Download started") } } }
    fun cancel(row: DownloadRecord) { viewModelScope.launch { safely { graph.downloads.cancel(row) } } }
    fun retry(row: DownloadRecord) { viewModelScope.launch { safely { graph.downloads.retry(row) } } }
    fun export(row: DownloadRecord, uri: android.net.Uri) { viewModelScope.launch { safely { graph.downloads.export(row, uri); messages.emit("File saved") } } }
    fun clearCatalogue() { loadJob?.cancel(); active = null; viewModelScope.launch { safely { graph.repository.clearCache(); _catalogue.value = CatalogueState(); messages.emit("Catalogue cache cleared; favorites and recent games retained") } } }
    private suspend fun safely(block: suspend () -> Unit) { try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) { messages.emit(e.message ?: "Operation failed") } }
    override fun onCleared() { graph.db.close(); super.onCleared() }
}
