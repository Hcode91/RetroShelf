package io.retroshelf

import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit

interface GameProvider {
    val id: String
    val name: String
    val description: String
    val website: String
    val section: String get() = "Homebrew"
    val attribution: String get() = "Data from $name"
    val metadataLicenseUrl: String? get() = null
    val refreshDetails: Boolean get() = false
    val supportedConsoles: Set<String>
    fun clearMemoryCache() {}
    suspend fun fetchGames(refresh: Boolean = false): List<Game>
    suspend fun searchGames(query: GameQuery): GamePage = cataloguePage(fetchGames(), query)
    suspend fun fetchGameDetails(id: String): Game = fetchGames().first { it.id == id }
    suspend fun fetchGameDetails(game: Game): Game = fetchGameDetails(game.id)
    suspend fun fetchScreenshots(id: String): List<String> = fetchGameDetails(id).screenshots
    suspend fun fetchDownloads(id: String): List<GameFile> = fetchGameDetails(id).downloads
}
class ProviderRegistry(val providers: List<GameProvider>) {
    fun forConsole(console: String) = providers.filter { console in it.supportedConsoles }
    fun get(id: String) = providers.first { it.id == id }
}
data class CatalogueBatch(val games: List<Game>, val nextPage: Int? = null, val total: Int? = null)
interface PagedGameProvider : GameProvider {
    suspend fun fetchCataloguePage(page: Int): CatalogueBatch
}
data class JsonResponse(val body: JsonElement, val headers: Headers)
class JsonHttp(val client: OkHttpClient) {
    suspend fun get(url: String): JsonElement = response(url).body
    suspend fun response(url: String): JsonResponse = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", "RetroShelf/0.2 (+https://github.com/Hcode91/RetroShelf)").build()).execute().use {
            if (!it.isSuccessful) throw IOException("Source returned HTTP ${it.code}. Try later or open its website.")
            JsonResponse(JsonParser.parseString(it.body?.string() ?: throw IOException("Empty response")), it.headers)
        }
    }
    companion object {
        fun client() = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
    }
}

fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
fun JsonObject.strings(key: String): List<String> = get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { v -> v.isJsonPrimitive }?.asString } ?: emptyList()
fun JsonObject.array(key: String): JsonArray = get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
fun cleanDescription(value: String): String = Jsoup.parse(value.replace("\n", "<br>")).wholeText().trim()
private fun safeUrl(value: String?) = value?.takeIf(UrlSafety::valid)

object Normalizer {
    fun universal(x: JsonObject): Game? {
        val slug = x.str("slug") ?: return null
        val title = x.str("title") ?: return null
        if ("game" !in x.strings("categories")) return null
        val license = x.str("license_name") ?: x.str("license")
        // A repository license alone does not establish rights to every port's assets.
        // Only individually reviewed standalone games receive a direct-download grant.
        val grant = if (slug == "2048---3ds-port" && x.str("source") == "https://github.com/0rientd/2048-port-3ds" && x.str("license") == "mit")
            "MIT: https://github.com/0rientd/2048-port-3ds/blob/main/LICENSE" else null
        val files = x.get("downloads")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.mapNotNull { (name, item) ->
            val d = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val url = safeUrl(d.str("url")) ?: return@mapNotNull null
            val approved = grant?.takeIf { url.startsWith("https://github.com/0rientd/2048-port-3ds/releases/download/") }
            GameFile(name, url, d.str("size")?.toLongOrNull(), approved)
        } ?: emptyList()
        return Game("udb:$slug", "udb", slug, title, cleanDescription(x.str("long_description") ?: x.str("description").orEmpty()),
            x.strings("systems"), safeUrl(x.str("source")) ?: safeUrl(x.str("download_page")) ?: "https://db.universal-team.net",
            safeUrl(x.str("image")) ?: safeUrl(x.str("icon")), x.array("screenshots").mapNotNull { if(it.isJsonObject) safeUrl(it.asJsonObject.str("url")) else null },
            x.str("author"), release = null, updated = x.str("updated"), genres = x.strings("genres"), license = license, downloads = files)
    }
    fun hub(x: JsonObject, repository: String): Game? {
        val slug = x.str("slug") ?: return null
        val title = x.str("title") ?: return null
        val platform = x.str("platform") ?: return null
        if(x.str("typetag") != "game") return null
        val base = "https://raw.githubusercontent.com/$repository/master/entries/$slug/"
        fun asset(path: String): String? = path.takeIf { !it.startsWith('/') && it.split('/').none { s -> s == ".." || s.isBlank() } }?.let { p ->
            safeUrl(base + p.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }) }
        val screens = x.strings("screenshots").mapNotNull(::asset)
        return Game("hub:$slug", "hub", slug, title, cleanDescription(x.str("description").orEmpty()), listOf(platform),
            safeUrl(x.str("website")) ?: safeUrl(x.str("repository")) ?: "https://github.com/$repository/tree/master/entries/$slug",
            screens.firstOrNull(), screens, x.str("developer"), release = x.str("date"), genres = x.strings("tags"), license = x.str("license"),
            downloads = x.array("files").mapNotNull { item ->
                if(!item.isJsonObject) return@mapNotNull null
                val name = item.asJsonObject.str("filename") ?: return@mapNotNull null
                val url = asset(name) ?: return@mapNotNull null
                GameFile(name, url) // No per-title distribution review yet: browser-only.
            })
    }
}
abstract class FeedProvider : GameProvider {
    private val mutex = Mutex()
    @Volatile private var loaded: List<Game>? = null
    override fun clearMemoryCache() { loaded = null }
    protected abstract suspend fun load(): List<Game>
    override suspend fun fetchGames(refresh: Boolean): List<Game> = mutex.withLock {
        if(refresh) loaded = null
        loaded ?: load().also { loaded = it }
    }
}
class UniversalDb(private val http: JsonHttp, private val endpoint: String = "https://db.universal-team.net/data/full.json") : FeedProvider() {
    override val id = "udb"
    override val name = "Universal-DB"
    override val description = "Community homebrew catalogue. Data from Universal-DB. Live feed verified 2026-11-07."
    override val website = "https://db.universal-team.net"
    override val supportedConsoles = setOf("DS", "3DS")
    override suspend fun load(): List<Game> {
        val data = http.get(endpoint)
        if(!data.isJsonArray) throw IOException("Universal-DB format changed")
        return data.asJsonArray.mapNotNull { runCatching { Normalizer.universal(it.asJsonObject) }.getOrNull() }
    }
}
class HomebrewRepository(private val http: JsonHttp) : FeedProvider() {
    override val id = "hub"
    override val name = "Homebrew Hub · GBA repository"
    override val description = "Official gbadev-org/games metadata and artwork. Repository access verified 2026-11-07. Direct API unavailable (403)."
    override val website = "https://github.com/gbadev-org/games"
    override val supportedConsoles = setOf("GBA")
    override suspend fun load(): List<Game> = coroutineScope {
        val repo = "gbadev-org/games"
        val tree = http.get("https://api.github.com/repos/$repo/git/trees/master?recursive=1").asJsonObject
        if(tree.get("truncated")?.asBoolean == true) throw IOException("Repository index is truncated")
        val paths = tree.array("tree").mapNotNull { it.asJsonObject.str("path") }.filter { it.startsWith("entries/") && it.endsWith("/game.json") }
        if(paths.isEmpty()) throw IOException("Homebrew repository format changed")
        val semaphore = Semaphore(2)
        paths.map { path -> async { semaphore.withPermit {
            delay(150) // At most two concurrent public raw requests, once per feed refresh.
            val data = http.get("https://raw.githubusercontent.com/$repo/master/$path")
            runCatching { Normalizer.hub(data.asJsonObject, repo) }.getOrNull()
        } } }.awaitAll().filterNotNull()
    }
}
