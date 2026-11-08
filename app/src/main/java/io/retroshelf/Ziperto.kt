package io.retroshelf

import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI

data class WordPressSite(val id: String, val name: String, val website: String, val api: String, val category: Int, val console: String, val section: String) {
    companion object {
        val ZIPERTO = WordPressSite("ziperto", "Ziperto · Nintendo Switch", "https://www.ziperto.com/nintendo-switch-nsp/", "https://www.ziperto.com/wp-json/wp/v2/", 3534, "SWITCH", "Switch catalogues")
        val NXBREW = WordPressSite("nxbrew", "NXbrew · Nintendo Switch", "https://nxbrew.me/category/switch-games/", "https://nxbrew.me/wp-json/wp/v2/", 22, "SWITCH", "Switch catalogues")
    }
}
class Ziperto(http: JsonHttp, api: String = WordPressSite.ZIPERTO.api, category: Int = 3534, pageSize: Int = 100) :
    WordPressSource(http, WordPressSite.ZIPERTO.copy(category = category), api, pageSize)

/** Shared public WordPress listing adapter; downloads use the original article. */
open class WordPressSource(
    private val http: JsonHttp,
    private val site: WordPressSite,
    private val api: String = site.api,
    private val pageSize: Int = 100
) : PagedGameProvider {
    override val id = site.id
    override val name = site.name
    override val section = site.section
    override val description = "Loads $pageSize listings at a time; older pages load only when requested. Public API verified 2026-11-07."
    override val website = site.website
    override val supportedConsoles = setOf(site.console)
    override val attribution = "Listings and metadata from ${site.name}. Open the original article for its download links."
    override val refreshDetails = true
    private var firstPage: CatalogueBatch? = null
    companion object {
        private val requestLock = Mutex()
        private var lastRequest = 0L
        private const val FIELDS = "id,link,title,excerpt,content,modified_gmt"
    }
    init { require(pageSize in 1..100) }
    override fun clearMemoryCache() { firstPage = null }
    private suspend fun request(url: String): JsonResponse = requestLock.withLock {
        val remaining = 1000 - (System.nanoTime() / 1_000_000 - lastRequest)
        if(remaining > 0) delay(remaining)
        try { http.response(url) } finally { lastRequest = System.nanoTime() / 1_000_000 }
    }
    override suspend fun fetchGames(refresh: Boolean): List<Game> {
        if(!refresh) firstPage?.let { return it.games }
        return fetchCataloguePage(1).games
    }
    override suspend fun fetchCataloguePage(page: Int): CatalogueBatch {
        require(page >= 1)
        val url = api.toHttpUrl().newBuilder().addPathSegment("posts")
            .addQueryParameter("categories", site.category.toString()).addQueryParameter("per_page", pageSize.toString())
            .addQueryParameter("page", page.toString()).addQueryParameter("orderby", "date").addQueryParameter("order", "desc")
            .addQueryParameter("_fields", FIELDS).build()
        val response = request(url.toString())
        if(!response.body.isJsonArray) throw IOException("${site.name} catalogue response changed")
        val pages = response.headers["X-WP-TotalPages"]?.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IOException("${site.name} pagination information is missing")
        val total = response.headers["X-WP-Total"]?.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IOException("${site.name} listing count is missing")
        val data = response.body.asJsonArray
        val normalized = data.mapNotNull { row -> if(row.isJsonObject) WordPressParser.game(row.asJsonObject, site) else null }
        if(data.size() > 0 && normalized.isEmpty()) throw IOException("${site.name} listing format changed")
        val games = normalized.filter { game -> game.consoles.any { it in supportedConsoles } }
        return CatalogueBatch(games, if(page < pages) page + 1 else null, total).also { if(page == 1) firstPage = it }
    }
    override suspend fun fetchGameDetails(id: String): Game {
        require(id.startsWith("${this.id}:"))
        return article(id.substringAfter(':'))
    }
    override suspend fun fetchGameDetails(game: Game): Game {
        require(game.provider == id && game.id == "$id:${game.slug}" && (game.consoles == listOf(site.console) || (site.console == "SWITCH" && game.consoles == listOf("SWITCH2"))))
        return article(game.slug)
    }
    private suspend fun article(post: String): Game {
        require(post.toLongOrNull()?.let { it > 0 } == true) { "Invalid source article" }
        val url = api.toHttpUrl().newBuilder().addPathSegment("posts").addPathSegment(post).addQueryParameter("_fields", FIELDS).build()
        val response = request(url.toString())
        val result = if(response.body.isJsonObject) WordPressParser.game(response.body.asJsonObject, site) else null
        return result?.takeIf { it.slug == post } ?: throw IOException("${site.name} article is unavailable or changed")
    }
}

object ZipertoParser {
    fun game(json: JsonObject): Game? = WordPressParser.game(json, WordPressSite.ZIPERTO)
}
object WordPressParser {
    private fun rendered(json: JsonObject, key: String): String = json.get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.str("rendered").orEmpty()
    private fun source(value: String?, site: WordPressSite): String? = value?.takeIf(UrlSafety::valid)?.takeIf { URI(it).host?.removePrefix("www.") == URI(site.website).host.removePrefix("www.") }
    fun game(json: JsonObject, site: WordPressSite): Game? {
        val post = json.str("id")?.toLongOrNull()?.takeIf { it > 0 }?.toString() ?: return null
        val link = source(json.str("link"), site) ?: return null
        val title = Jsoup.parse(rendered(json, "title")).text().takeIf { it.isNotBlank() } ?: return null
        val html = rendered(json, "content")
        val doc = Jsoup.parse(html)
        val info = doc.select("div, table").firstOrNull { it.text().contains("Release Date:", true) && it.text().contains("Genre:", true) }
        val fields = info?.let { element ->
            Jsoup.parse(element.html().replace(Regex("(?i)<br\\s*/?>"), "\n")).wholeText().lines().mapNotNull { line ->
                Regex("^\\s*(Release Date|Genre|Publisher|Developer)\\s*:\\s*(.+?)\\s*$", RegexOption.IGNORE_CASE).matchEntire(line)?.let { it.groupValues[1].lowercase() to it.groupValues[2] }
            }.toMap()
        }.orEmpty().toMutableMap()
        doc.select("tr").forEach { row ->
            val cells = row.children().filter { it.tagName() in listOf("td", "th") }
            if(cells.size == 2) {
                val key = cells[0].text().trim().trimEnd(':').lowercase()
                if(key in setOf("release date", "genre", "publisher", "developer", "platform")) fields[key] = cells[1].text()
            }
        }
        val excerpt = Jsoup.parse(rendered(json, "excerpt")).text()
        val description = doc.select("p").map { it.text().trim() }.firstOrNull { it.isNotBlank() }
            ?: excerpt.split(Regex("(?i)ScreenShots:|How to Jailbreak|Download Links:")).first().trim()
        val images = doc.select("img").mapNotNull { image ->
            source(image.attr("src").ifBlank { image.attr("data-src") }, site)?.takeIf { URI(it).path.startsWith("/wp-content/uploads/") }
        }.distinct()
        val console = if(site.console == "SWITCH" && fields["platform"]?.contains("Switch 2", true) == true) "SWITCH2" else site.console
        return Game("${site.id}:$post", site.id, post, title, description, listOf(console), link,
            artwork = images.firstOrNull(), screenshots = images.drop(1).take(12), developer = fields["developer"],
            publisher = fields["publisher"], release = fields["release date"], updated = json.str("modified_gmt"),
            genres = fields["genre"]?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
            detailsFetchedAt = if(html.isNotBlank()) System.currentTimeMillis() else 0)
    }
}
