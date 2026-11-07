package io.retroshelf

import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI

/** Public WordPress listings. Intermediary download hosts are opened on the original website. */
class Ziperto(
    private val http: JsonHttp,
    private val api: String = "https://www.ziperto.com/wp-json/wp/v2/",
    private val category: Int = 3534,
    private val pageSize: Int = 100
) : PagedGameProvider {
    override val id = "ziperto"
    override val name = "Ziperto · Nintendo Switch"
    override val section = "Switch catalogues"
    override val description = "Nintendo Switch NSP listings. Loads $pageSize listings at a time; older pages load only when requested. Public API verified 2026-11-07."
    override val website = "https://www.ziperto.com/nintendo-switch-nsp/"
    override val supportedConsoles = setOf("SWITCH")
    override val attribution = "Listings and metadata from Ziperto. Open the original article for its download links."
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
            .addQueryParameter("categories", category.toString()).addQueryParameter("per_page", pageSize.toString())
            .addQueryParameter("page", page.toString()).addQueryParameter("orderby", "date").addQueryParameter("order", "desc")
            .addQueryParameter("_fields", FIELDS).build()
        val response = request(url.toString())
        if(!response.body.isJsonArray) throw IOException("Ziperto catalogue response changed")
        val pages = response.headers["X-WP-TotalPages"]?.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IOException("Ziperto pagination information is missing")
        val total = response.headers["X-WP-Total"]?.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IOException("Ziperto listing count is missing")
        val data = response.body.asJsonArray
        val games = data.mapNotNull { row -> if(row.isJsonObject) ZipertoParser.game(row.asJsonObject) else null }
        if(data.size() > 0 && games.isEmpty()) throw IOException("Ziperto listing format changed")
        return CatalogueBatch(games, if(page < pages) page + 1 else null, total).also { if(page == 1) firstPage = it }
    }
    override suspend fun fetchGameDetails(id: String): Game {
        require(id.startsWith("ziperto:"))
        return article(id.substringAfter(':'))
    }
    override suspend fun fetchGameDetails(game: Game): Game {
        require(game.provider == id && game.id == "$id:${game.slug}" && game.consoles == listOf("SWITCH"))
        return article(game.slug)
    }
    private suspend fun article(post: String): Game {
        require(post.toLongOrNull()?.let { it > 0 } == true) { "Invalid Ziperto article" }
        val url = api.toHttpUrl().newBuilder().addPathSegment("posts").addPathSegment(post).addQueryParameter("_fields", FIELDS).build()
        val response = request(url.toString())
        val result = if(response.body.isJsonObject) ZipertoParser.game(response.body.asJsonObject) else null
        return result?.takeIf { it.slug == post } ?: throw IOException("Ziperto article is unavailable or changed")
    }
}

object ZipertoParser {
    private fun rendered(json: JsonObject, key: String): String = json.get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.str("rendered").orEmpty()
    private fun source(value: String?): String? = value?.takeIf(UrlSafety::valid)?.takeIf { URI(it).host in setOf("www.ziperto.com", "ziperto.com") }
    fun game(json: JsonObject): Game? {
        val post = json.str("id")?.toLongOrNull()?.takeIf { it > 0 }?.toString() ?: return null
        val link = source(json.str("link")) ?: return null
        val title = Jsoup.parse(rendered(json, "title")).text().takeIf { it.isNotBlank() } ?: return null
        val html = rendered(json, "content")
        val doc = Jsoup.parse(html)
        val info = doc.select("div, table").firstOrNull { it.text().contains("Release Date:", true) && it.text().contains("Genre:", true) }
        val fields = info?.let { element ->
            Jsoup.parse(element.html().replace(Regex("(?i)<br\\s*/?>"), "\n")).wholeText().lines().mapNotNull { line ->
                Regex("^\\s*(Release Date|Genre|Publisher|Developer)\\s*:\\s*(.+?)\\s*$", RegexOption.IGNORE_CASE).matchEntire(line)?.let { it.groupValues[1].lowercase() to it.groupValues[2] }
            }.toMap()
        }.orEmpty()
        val excerpt = Jsoup.parse(rendered(json, "excerpt")).text()
        val description = doc.select("p").map { it.text().trim() }.firstOrNull { it.isNotBlank() }
            ?: excerpt.split(Regex("(?i)ScreenShots:|How to Jailbreak|Download Links:")).first().trim()
        val images = doc.select("img").mapNotNull { image ->
            source(image.attr("src").ifBlank { image.attr("data-src") })?.takeIf { URI(it).path.startsWith("/wp-content/uploads/") }
        }.distinct()
        return Game("ziperto:$post", "ziperto", post, title, description, listOf("SWITCH"), link,
            artwork = images.firstOrNull(), screenshots = images.drop(1).take(12), developer = fields["developer"],
            publisher = fields["publisher"], release = fields["release date"], updated = json.str("modified_gmt"),
            genres = fields["genre"]?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
            detailsFetchedAt = if(html.isNotBlank()) System.currentTimeMillis() else 0)
    }
}
