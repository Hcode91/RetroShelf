package io.retroshelf

import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URI

data class GameBrewCatalogue(val console: String, val page: String, val providerId: String) {
    companion object {
        val GBA = GameBrewCatalogue("GBA", "List_of_GBA_homebrew_games", "gamebrew")
        val all = listOf(GBA,
            GameBrewCatalogue("SWITCH", "List_of_Switch_homebrew_games", "gamebrew-switch"),
            GameBrewCatalogue("WIIU", "List_of_Wii_U_homebrew_games", "gamebrew-wiiu"),
            GameBrewCatalogue("WII", "List_of_Wii_homebrew_games", "gamebrew-wii"),
            GameBrewCatalogue("3DS", "List_of_3DS_homebrew_games", "gamebrew-3ds"),
            GameBrewCatalogue("DS", "List_of_DS_homebrew_games", "gamebrew-ds"),
            GameBrewCatalogue("GB", "List_of_GB_homebrew_games", "gamebrew-gb"),
            GameBrewCatalogue("VITA", "List_of_Vita_homebrew_games", "gamebrew-vita"),
            GameBrewCatalogue("PSP", "List_of_PSP_homebrew_games", "gamebrew-psp"))
    }
}

/** Public MediaWiki parse API; only the selected console is loaded. */
class GameBrew(
    private val http: JsonHttp,
    private val api: String = "https://www.gamebrew.org/api.php",
    val catalogue: GameBrewCatalogue = GameBrewCatalogue.GBA
) : FeedProvider() {
    override val id = catalogue.providerId
    private val consoleName = Consoles.all.first { it.id == catalogue.console }.name
    override val name = "GameBrew · $consoleName"
    override val section = "GameBrew homebrew"
    override val description = "$consoleName homebrew games, genre lists and gallery artwork. Data from GameBrew (CC BY-SA 4.0). Live access verified 2026-11-07."
    override val attribution = "Data from GameBrew contributors · CC BY-SA 4.0. Descriptions converted to plain text. Game files and artwork retain their authors' separate terms."
    override val website = "${GameBrewParser.ROOT}/wiki/${catalogue.page}"
    override val supportedConsoles = if(catalogue.console == "GB") setOf("GB", "GBC") else setOf(catalogue.console)
    override val metadataLicenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/"
    override val refreshDetails = true
    companion object {
        // All GameBrew platforms share one request limit for the same website.
        private val requestLock = Mutex()
        private var lastRequest = 0L
    }
    private suspend fun page(title: String): String = requestLock.withLock {
        val remaining = 1000 - (System.nanoTime() / 1_000_000 - lastRequest)
        if(remaining > 0) delay(remaining)
        try {
            val url = api.toHttpUrl().newBuilder().addQueryParameter("action", "parse")
                .addQueryParameter("page", title).addQueryParameter("prop", "text")
                .addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
                .addQueryParameter("maxlag", "5").build()
            GameBrewParser.response(http.get(url.toString()).asJsonObject)
        } finally { lastRequest = System.nanoTime() / 1_000_000 }
    }
    override suspend fun load(): List<Game> = GameBrewParser.catalogue(page(catalogue.page), catalogue.console, id)
    override suspend fun fetchGameDetails(id: String): Game {
        val summary = fetchGames().first { it.id == id }
        return fetchGameDetails(summary)
    }
    override suspend fun fetchGameDetails(game: Game): Game {
        require(game.provider == id && game.id == "$id:${game.slug}" && GameBrewParser.validArticleTitle(game.slug) && game.consoles.isNotEmpty() && game.consoles.all { it in supportedConsoles }) { "Invalid GameBrew entry" }
        return GameBrewParser.details(page(game.slug), game)
    }
}

object GameBrewParser {
    const val ROOT = "https://www.gamebrew.org"
    const val LIST = "$ROOT/wiki/List_of_GBA_homebrew_games"
    fun response(json: JsonObject): String {
        if(json.has("error")) throw IOException("GameBrew is unavailable or limiting requests. Open the original website or try later.")
        return json.get("parse")?.takeIf { it.isJsonObject }?.asJsonObject?.str("text")
            ?: throw IOException("GameBrew response format changed")
    }
    private fun url(value: String): String? = value.takeIf { it.isNotBlank() }?.let { runCatching { URI(ROOT).resolve(value).toString() }.getOrNull()?.takeIf(UrlSafety::valid) }
    fun validArticleTitle(title: String): Boolean {
        val namespaces = setOf("media", "special", "talk", "user", "user talk", "gamebrew", "gamebrew talk", "project", "project talk", "file", "file talk", "image", "image talk", "mediawiki", "mediawiki talk", "template", "template talk", "help", "help talk", "category", "category talk", "module", "module talk")
        return title.isNotBlank() && (':' !in title || title.substringBefore(':').replace('_', ' ').lowercase(java.util.Locale.ROOT) !in namespaces)
    }
    private fun article(value: String): String? = url(value)?.takeIf {
        val uri = URI(it)
        uri.host == "www.gamebrew.org" && uri.path.startsWith("/wiki/") &&
            validArticleTitle(uri.path.substringAfter("/wiki/")) && uri.rawQuery == null && uri.rawFragment == null
    }
    private fun image(element: Element?): String? = element?.let { url(it.attr("src").ifBlank { it.attr("data-src") }) }
    fun catalogue(html: String, console: String = "GBA", providerId: String = "gamebrew"): List<Game> {
        val doc = Jsoup.parse(html, ROOT)
        val tables = doc.select("table.cargoDynamicTable")
        if(tables.isEmpty()) throw IOException("GameBrew catalogue layout changed. Open the original website.")
        val artwork = doc.select("li.gallerybox").mapNotNull { box ->
            val link = box.selectFirst(".gallerytext a[href]") ?: return@mapNotNull null
            val source = article(link.attr("href")) ?: return@mapNotNull null
            source to image(box.selectFirst("img"))
        }.toMap()
        val games = linkedMapOf<String, Game>()
        for(table in tables) {
            val genre = table.parents().firstOrNull { it.hasClass("tabber") }?.let { tab ->
                var previous = tab.previousElementSibling()
                while(previous != null && previous.tagName() !in listOf("h2", "h3")) previous = previous.previousElementSibling()
                previous?.selectFirst(".mw-headline")?.text()
            }
            val headers = table.selectFirst("thead tr")?.children()?.map { it.text().lowercase().replace(Regex("[^a-z]"), "") }.orEmpty()
            fun column(names: Set<String>, fallback: Int): Int = headers.indexOfFirst { it in names }.takeIf { it >= 0 } ?: fallback
            for(row in table.select("tbody > tr")) {
                val cells = row.children().filter { it.tagName() == "td" }
                if(cells.size < 5) continue
                val link = cells.getOrNull(column(setOf("title", "name"), 0))?.selectFirst("a[href]") ?: continue
                val source = article(link.attr("href")) ?: continue
                val title = link.text().trim().takeIf { it.isNotBlank() } ?: continue
                val slug = URI(source).path.substringAfter("/wiki/")
                val genres = listOfNotNull(genre?.takeIf { it.isNotBlank() })
                val system = cells.getOrNull(column(setOf("system"), -1))?.text()?.uppercase().orEmpty()
                val consoles = if(console != "GB") listOf(console) else when {
                    system.contains("DMG") && system.contains("CGB") -> listOf("GB", "GBC")
                    system == "CGB" || system == "GBC" -> listOf("GBC")
                    else -> listOf("GB")
                }
                val old = games[source]
                games[source] = old?.copy(genres = (old.genres + genres).distinct(), consoles = (old.consoles + consoles).distinct()) ?: Game(
                    "$providerId:$slug", providerId, slug, title, cells.getOrNull(column(setOf("description"), 1))?.text().orEmpty(), consoles, source,
                    artwork = artwork[source], developer = cells.getOrNull(column(setOf("author", "authors", "developer", "developers"), cells.size-2))?.text()?.takeIf { it.isNotBlank() },
                    updated = cells.getOrNull(column(setOf("updated", "lastupdated"), cells.size-1))?.text()?.takeIf { it.isNotBlank() }, genres = genres)
            }
        }
        if(games.isEmpty() && tables.any { it.select("tbody > tr > td").isNotEmpty() }) throw IOException("GameBrew catalogue rows could not be read")
        return games.values.toList()
    }
    fun details(html: String, summary: Game): Game {
        val doc = Jsoup.parse(html, ROOT)
        val content = doc.selectFirst(".mw-parser-output") ?: throw IOException("GameBrew detail layout changed")
        val info = content.selectFirst("table.infobox") ?: throw IOException("GameBrew game information is missing")
        val fields = info.select("tr").mapNotNull { row ->
            val key = row.selectFirst("th.infobox-label")?.text() ?: return@mapNotNull null
            val value = row.selectFirst("td.infobox-data")?.text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            key.lowercase() to value
        }.toMap()
        val intro = content.selectFirst("#citizen-section-0") ?: content
        val paragraphs = intro.children().takeWhile { it.tagName() !in listOf("h2", "h3") }.filter { it.tagName() == "p" }.map { it.text() }.filter { it.isNotBlank() }
        val screenshotHeading = content.selectFirst("#Screenshots")?.parent()
        val screenshotSection = screenshotHeading?.parent()?.takeIf { it.tagName() == "section" }
        val screenshotImages = screenshotSection?.select("img")?.toList() ?: buildList {
            var sibling = screenshotHeading?.nextElementSibling()
            while(sibling != null && sibling.tagName() !in listOf("h2", "h3")) {
                addAll(sibling.select("img")); sibling = sibling.nextElementSibling()
            }
        }
        val screenshots = screenshotImages.mapNotNull(::image).distinct()
        val files = info.select("a[href]").filter { it.text().trim().equals("Download", true) }.mapNotNull { link ->
            val target = url(link.attr("href")) ?: return@mapNotNull null
            val path = URI(target).path.orEmpty().substringAfterLast('/')
            if(!path.contains('.')) return@mapNotNull null
            GameFile(path, target) // Wiki license does not authorize binary redistribution.
        }
        return summary.copy(description = paragraphs.joinToString("\n\n").ifBlank { summary.description },
            artwork = image(info.selectFirst(".infobox-image img")) ?: summary.artwork,
            screenshots = screenshots, developer = fields["author"] ?: fields["authors"] ?: fields["author(s)"] ?: summary.developer,
            license = fields["license"], updated = fields["last updated"] ?: summary.updated,
            downloads = files, detailsFetchedAt = System.currentTimeMillis())
    }
}
