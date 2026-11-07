package io.retroshelf

import com.google.gson.Gson
import kotlinx.coroutines.flow.map

class ShelfRepository(val registry: ProviderRegistry, val dao: ShelfDao) {
    val gson = Gson()
    fun decode(row: StoredGame): Game = gson.fromJson(row.json, Game::class.java)
    val favorites = dao.favorites().map { rows -> rows.map(::decode) }
    val recent = dao.recent().map { rows -> rows.map(::decode) }
    suspend fun catalogue(provider: String, refresh: Boolean): Pair<List<Game>, Boolean> {
        val cached = dao.catalogue(provider)
        if(!refresh && (cached.isNotEmpty() || dao.snapshot(provider) != null)) return cached.map(::decode) to false
        try {
            val source = registry.get(provider)
            val batch = if(source is PagedGameProvider) source.fetchCataloguePage(1) else CatalogueBatch(source.fetchGames(refresh))
            val games = batch.games
            val rows = games.map { g -> StoredGame(g.id, provider, gson.toJson(g), cachedAt = System.currentTimeMillis()) }
            dao.replaceCatalogue(provider, rows, batch.nextPage, batch.total)
            return games to false
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { if(cached.isNotEmpty()) return cached.map(::decode) to true; throw e }
    }
    suspend fun loadMore(provider: String): List<Game> {
        val next = dao.snapshot(provider)?.nextPage ?: return dao.catalogue(provider).map(::decode)
        val source = registry.get(provider) as? PagedGameProvider ?: return dao.catalogue(provider).map(::decode)
        val batch = source.fetchCataloguePage(next)
        val rows = batch.games.map { StoredGame(it.id, provider, gson.toJson(it), cachedAt = System.currentTimeMillis()) }
        dao.appendCatalogue(provider, rows, batch.nextPage, batch.total)
        return dao.catalogue(provider).map(::decode)
    }
    suspend fun details(game: Game, refresh: Boolean = false): Game {
        val saved = dao.game(game.id)?.let(::decode) ?: game
        val provider = registry.get(saved.provider)
        if(!provider.refreshDetails || (!refresh && saved.detailsFetchedAt > 0)) return saved
        val enriched = provider.fetchGameDetails(saved)
        val old = dao.game(game.id)
        dao.put(StoredGame(game.id, game.provider, gson.toJson(enriched), old?.favorite ?: false, old?.viewed ?: 0, old?.cachedAt ?: 0))
        return enriched
    }
    suspend fun clearCache() {
        registry.providers.forEach { it.clearMemoryCache() }
        dao.clearUnused()
        dao.expireCache()
        dao.clearSnapshots()
        // Bookmarks/history survive, but the user explicitly cleared their saved detail payloads.
        dao.retained().forEach { row -> dao.put(row.copy(json = gson.toJson(decode(row).copy(detailsFetchedAt = 0)))) }
    }
    suspend fun view(game: Game) {
        val old = dao.game(game.id)
        dao.put(StoredGame(game.id, game.provider, gson.toJson(game), old?.favorite ?: false, System.currentTimeMillis(), old?.cachedAt ?: 0))
    }
    suspend fun toggleFavorite(game: Game) {
        val old = dao.game(game.id)
        dao.put(StoredGame(game.id, game.provider, gson.toJson(game), !(old?.favorite ?: false), old?.viewed ?: 0, old?.cachedAt ?: 0))
    }
}
