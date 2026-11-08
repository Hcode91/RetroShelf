package io.retroshelf

import com.google.gson.JsonParser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

private class MemoryDao : ShelfDao {
    val games = MutableStateFlow(mapOf<String, StoredGame>())
    private val snapshots = mutableMapOf<String, CatalogueSnapshot>()
    override suspend fun snapshot(provider: String) = snapshots[provider]
    override suspend fun putSnapshot(snapshot: CatalogueSnapshot) { snapshots[snapshot.provider] = snapshot }
    override suspend fun clearSnapshots() { snapshots.clear() }
    override suspend fun retained() = games.value.values.filter { it.favorite || it.viewed > 0 }
    private val downloads = MutableStateFlow(mapOf<Long, DownloadRecord>())
    override suspend fun catalogue(provider: String) = games.value.values.filter { it.provider == provider && it.cachedAt > 0 }
    override suspend fun game(id: String) = games.value[id]
    override suspend fun put(game: StoredGame) { games.value += game.id to game }
    override suspend fun favorite(id: String, favorite: Boolean) { game(id)?.let { put(it.copy(favorite = favorite)) } }
    override fun favorites() = games.map { it.values.filter { g -> g.favorite } }
    override fun recent() = games.map { it.values.filter { g -> g.viewed > 0 }.sortedByDescending { g -> g.viewed }.take(30) }
    override suspend fun clearUnused() { games.value = games.value.filterValues { it.favorite || it.viewed > 0 } }
    override suspend fun expireCache() { games.value = games.value.mapValues { it.value.copy(cachedAt = 0) } }
    override suspend fun expireProvider(provider: String) { games.value = games.value.mapValues { if(it.value.provider == provider) it.value.copy(cachedAt = 0) else it.value } }
    override fun downloads() = downloads.map { it.values.toList() }
    override suspend fun downloadSnapshot() = downloads.value.values.toList()
    override suspend fun putDownload(record: DownloadRecord) { downloads.value += record.id to record }
}
class RepositoryTests {
    private fun game() = Normalizer.universal(JsonParser.parseString(javaClass.classLoader!!.getResource("universal.json")!!.readText()).asJsonArray[0].asJsonObject)!!
    @Test fun `favorites survive repository recreation and viewing`() = runTest {
        val dao = MemoryDao(); val repo = ShelfRepository(ProviderRegistry(emptyList()), dao); val g = game()
        repo.toggleFavorite(g); repo.view(g)
        val recreated = ShelfRepository(ProviderRegistry(emptyList()), dao)
        assertEquals(g.id, recreated.favorites.first().single().id)
        assertEquals(g.id, recreated.recent.first().single().id)
        recreated.toggleFavorite(g)
        assertTrue(repo.favorites.first().isEmpty())
    }
    @Test fun `cached games survive network failure and cache clearing preserves favorites`() = runTest {
        val dao = MemoryDao(); val g = game()
        val broken = object : GameProvider {
            override val id="udb"; override val name="Broken"; override val description=""; override val website="https://example.com"; override val supportedConsoles=setOf("3DS")
            override suspend fun fetchGames(refresh: Boolean): List<Game> = throw java.io.IOException("Offline")
        }
        val repo = ShelfRepository(ProviderRegistry(listOf(broken)), dao)
        repo.toggleFavorite(g)
        dao.put(dao.game(g.id)!!.copy(cachedAt = 1))
        val (games, offline) = repo.catalogue("udb", true)
        assertTrue(offline); assertEquals(g.id, games.single().id)
        dao.clearUnused(); assertNotNull(dao.game(g.id))
        val fresh = ShelfRepository(ProviderRegistry(listOf(broken)), MemoryDao())
        try { fresh.catalogue("udb", false); fail("Expected offline failure") } catch(_: java.io.IOException) { }
    }
    @Test fun `refresh removes vanished catalogue entries while preserving bookmarks`() = runTest {
        val dao = MemoryDao(); val g = game()
        val provider = object : GameProvider {
            override val id="udb"; override val name="Fixture"; override val description=""; override val website="https://example.com"; override val supportedConsoles=setOf("3DS")
            override suspend fun fetchGames(refresh: Boolean) = listOf(g.copy(id="udb:new", slug="new"))
        }
        val repo = ShelfRepository(ProviderRegistry(listOf(provider)), dao)
        repo.toggleFavorite(g)
        dao.put(dao.game(g.id)!!.copy(cachedAt = 1))
        val (games, offline) = repo.catalogue("udb", true)
        assertFalse(offline)
        assertEquals("udb:new", games.single().id)
        assertEquals("udb:new", dao.catalogue("udb").single().id)
        assertTrue(dao.game(g.id)!!.favorite)
        assertEquals(g.id, repo.favorites.first().single().id)
    }

    @Test fun `saved catalogue and details never expire across repository recreation`() = runTest {
        val dao = MemoryDao(); val g = game().copy(detailsFetchedAt = 1)
        val provider = CountingProvider(g)
        dao.put(StoredGame(g.id, g.provider, com.google.gson.Gson().toJson(g), cachedAt = 1))
        val registry = ProviderRegistry(listOf(provider))
        repeat(3) {
            val repo = ShelfRepository(registry, dao)
            assertEquals(g.id, repo.catalogue(g.provider, false).first.single().id)
            assertEquals(1L, repo.details(g).detailsFetchedAt)
        }
        assertEquals(0, provider.catalogueCalls)
        assertEquals(0, provider.detailCalls)
    }
    @Test fun `empty catalogues stay saved until explicit refresh or clear`() = runTest {
        val dao = MemoryDao(); val provider = CountingProvider(game()).apply { entries = emptyList() }
        val registry = ProviderRegistry(listOf(provider))
        ShelfRepository(registry, dao).catalogue(provider.id, false)
        repeat(3) { assertTrue(ShelfRepository(registry, dao).catalogue(provider.id, false).first.isEmpty()) }
        assertEquals(1, provider.catalogueCalls)
        val repo = ShelfRepository(registry, dao)
        repo.catalogue(provider.id, true)
        assertEquals(2, provider.catalogueCalls)
        repo.clearCache()
        assertNull(dao.snapshot(provider.id))
        repo.catalogue(provider.id, false)
        assertEquals(3, provider.catalogueCalls)
    }
    @Test fun `details use saved entry and request only initial or manual refresh`() = runTest {
        val dao = MemoryDao(); val g = game(); val provider = CountingProvider(g)
        val repo = ShelfRepository(ProviderRegistry(listOf(provider)), dao)
        val enriched = repo.details(g)
        assertTrue(enriched.detailsFetchedAt > 0)
        assertEquals(1, provider.detailCalls)
        repo.details(g) // Even a stale summary resolves the saved enriched entry.
        assertEquals(1, provider.detailCalls)
        repo.details(g, refresh = true)
        assertEquals(2, provider.detailCalls)
        assertEquals(0, provider.catalogueCalls)
        repo.toggleFavorite(enriched)
        repo.clearCache()
        assertTrue(dao.game(g.id)!!.favorite)
        assertEquals(0L, repo.decode(dao.game(g.id)!!).detailsFetchedAt)
        repo.details(g)
        assertEquals(3, provider.detailCalls)
    }

    @Test fun `paged sources persist loaded entries and cursor across reopening`() = runTest {
        val dao = MemoryDao(); val p = PagedFixture(game()); val registry = ProviderRegistry(listOf(p))
        val repo = ShelfRepository(registry, dao)
        val first = repo.catalogue(p.id, false).first.single()
        repo.toggleFavorite(first)
        val reopened = ShelfRepository(registry, dao)
        assertEquals(1, reopened.catalogue(p.id, false).first.size)
        assertEquals(listOf(1), p.pages)
        assertEquals(2, dao.snapshot(p.id)!!.nextPage)
        assertEquals(2, reopened.loadMore(p.id).size) // Duplicate IDs are merged.
        assertTrue(dao.game(first.id)!!.favorite)
        assertEquals(3, dao.snapshot(p.id)!!.nextPage)
        assertEquals(3, ShelfRepository(registry, dao).loadMore(p.id).size)
        assertNull(dao.snapshot(p.id)!!.nextPage)
        reopened.loadMore(p.id)
        assertEquals(listOf(1,2,3), p.pages)
    }
    @Test fun `failed remote page preserves saved entries cursor and bookmarks for explicit retry`() = runTest {
        val dao = MemoryDao(); val p = PagedFixture(game()); val repo = ShelfRepository(ProviderRegistry(listOf(p)), dao)
        repo.catalogue(p.id, false); p.failNext = true
        try { repo.loadMore(p.id); fail("Expected page failure") } catch(_: java.io.IOException) { }
        assertEquals(1, dao.catalogue(p.id).size)
        assertEquals(2, dao.snapshot(p.id)!!.nextPage)
        assertEquals(2, repo.loadMore(p.id).size)
        assertEquals(listOf(1,2,2), p.pages)
    }

}

private class CountingProvider(private val game: Game) : GameProvider {
    override val id = game.provider
    override val name = "Counting source"
    override val description = ""
    override val website = "https://example.com"
    override val supportedConsoles = setOf("3DS")
    override val refreshDetails = true
    var catalogueCalls = 0
    var detailCalls = 0
    var entries = listOf(game)
    override suspend fun fetchGames(refresh: Boolean): List<Game> { catalogueCalls++; return entries }
    override suspend fun fetchGameDetails(game: Game): Game { detailCalls++; return game.copy(detailsFetchedAt = System.currentTimeMillis()) }
}

private class PagedFixture(game: Game) : PagedGameProvider {
    override val id = "ziperto"
    override val name = "Paged fixture"
    override val description = ""
    override val website = "https://example.com"
    override val supportedConsoles = setOf("SWITCH")
    private val first = game.copy(id="ziperto:one", provider=id, slug="one", consoles=listOf("SWITCH"))
    val pages = mutableListOf<Int>()
    var failNext = false
    override suspend fun fetchGames(refresh: Boolean): List<Game> = error("Repository must request one explicit page")
    override suspend fun fetchCataloguePage(page: Int): CatalogueBatch {
        pages += page
        if(failNext) { failNext = false; throw java.io.IOException("Offline") }
        return when(page) {
            1 -> CatalogueBatch(listOf(first), 2, 3)
            2 -> CatalogueBatch(listOf(first, first.copy(id="ziperto:two", slug="two")), 3, 3)
            else -> CatalogueBatch(listOf(first.copy(id="ziperto:three", slug="three")), null, 3)
        }
    }
}
