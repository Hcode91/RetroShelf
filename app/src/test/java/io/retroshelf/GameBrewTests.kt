package io.retroshelf

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class GameBrewTests {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun catalogue() = GameBrewParser.catalogue(fixture("gamebrew-catalogue.html"))
    @Test fun `real catalogue excerpt normalizes metadata genre artwork and source`() {
        val games = catalogue()
        assertEquals(4, games.size)
        val minicraft = games.first { it.slug == "Minicraft_GBA" }
        assertEquals("Minicraft", minicraft.title)
        assertEquals("Vulcalien", minicraft.developer)
        assertEquals("2026/02/20", minicraft.updated)
        assertNull(minicraft.release) // Updated is not an original release date.
        assertEquals(listOf("Action"), minicraft.genres)
        assertEquals(listOf("GBA"), minicraft.consoles)
        assertTrue(minicraft.artwork!!.startsWith("https://www.gamebrew.org/thumb.php?"))
        assertEquals("https://www.gamebrew.org/wiki/Minicraft_GBA", minicraft.sourceUrl)
        assertTrue(games.any { "Puzzle" in it.genres })
        assertTrue(games.all { it.downloads.isEmpty() })
        assertEquals(1, cataloguePage(games, GameQuery("GBA", "Minicraft")).total)
        assertEquals(2, cataloguePage(games, GameQuery("GBA", genre="Puzzle")).total)
    }
    @Test fun `real detail API supplies description screenshots license and unreviewed files`() {
        val summary = catalogue().first { it.slug == "Minicraft_GBA" }
        val response = JsonParser.parseString(fixture("gamebrew-details.json")).asJsonObject
        val detail = GameBrewParser.details(GameBrewParser.response(response), summary)
        assertTrue(detail.description.startsWith("Minicraft is a GBA demake"))
        assertFalse(detail.description.contains("Open the gba ROM")) // Installation is not the description.
        assertEquals(3, detail.screenshots.size)
        assertEquals("GPL-3.0", detail.license)
        assertEquals("minicraftgba.7z", detail.downloads.single().name)
        assertNull(detail.downloads.single().authorization)
        assertTrue(detail.detailsFetchedAt > 0)
    }
    @Test fun `duplicates merge genres without merging providers or inventing entries`() {
        val html = fixture("gamebrew-catalogue.html")
        val games = GameBrewParser.catalogue(html + html)
        assertEquals(4, games.size)
        assertTrue(games.all { it.provider == "gamebrew" && it.id.startsWith("gamebrew:") })
    }
    @Test fun `unsafe links are ignored and removed HTML structures report errors`() {
        val html = fixture("gamebrew-catalogue.html").replace("/wiki/Minicraft_GBA", "javascript:alert(1)")
        assertFalse(GameBrewParser.catalogue(html).any { it.slug == "Minicraft_GBA" })
        assertTrue(GameBrewParser.catalogue("<table class='cargoDynamicTable'><tbody></tbody></table>").isEmpty())
        try { GameBrewParser.catalogue("<h1>Challenge</h1>"); fail("Changed layout accepted") } catch(_: java.io.IOException) { }
        try { GameBrewParser.response(JsonObject()); fail("Malformed API accepted") } catch(_: java.io.IOException) { }
        val error = JsonParser.parseString("{\"error\":{\"code\":\"maxlag\"}}").asJsonObject
        try { GameBrewParser.response(error); fail("Rate limit accepted") } catch(_: java.io.IOException) { }
    }
    @Test fun `adapter uses public parse API caches catalogue and loads details on demand`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val provider = GameBrew(JsonHttp(JsonHttp.client()), server.url("/api.php").toString())
            val body = JsonObject().apply { add("parse", JsonObject().apply { addProperty("text", fixture("gamebrew-catalogue.html")) }) }
            server.enqueue(MockResponse().setBody(body.toString()))
            val games = provider.fetchGames()
            assertEquals(4, games.size)
            val request = server.takeRequest()
            assertEquals("parse", request.requestUrl!!.queryParameter("action"))
            assertEquals("List_of_GBA_homebrew_games", request.requestUrl!!.queryParameter("page"))
            assertEquals("5", request.requestUrl!!.queryParameter("maxlag"))
            provider.fetchGames()
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setBody(fixture("gamebrew-details.json")))
            val detail = provider.fetchGameDetails(games.first { it.slug == "Minicraft_GBA" }.id)
            assertEquals(3, detail.screenshots.size)
            assertEquals("Minicraft_GBA", server.takeRequest().requestUrl!!.queryParameter("page"))
            assertEquals("GameBrew homebrew", provider.section)
            assertEquals(setOf("GBA"), provider.supportedConsoles)
            server.enqueue(MockResponse().setResponseCode(403))
            try { provider.fetchGames(true); fail("403 must fail without bypass") } catch(_: java.io.IOException) { }
        }
    }
    @Test fun `saved entry opens details after restart without reloading catalogue`() = runTest {
        val summary = catalogue().first { it.slug == "Minicraft_GBA" }
        MockWebServer().use { server ->
            server.start()
            val restarted = GameBrew(JsonHttp(JsonHttp.client()), server.url("/api.php").toString())
            server.enqueue(MockResponse().setBody(fixture("gamebrew-details.json")))
            assertEquals(3, restarted.fetchGameDetails(summary).screenshots.size)
            assertEquals(1, server.requestCount)
            assertEquals("Minicraft_GBA", server.takeRequest().requestUrl!!.queryParameter("page"))
        }
    }

    @Test fun `colons in game names are allowed while wiki namespaces are blocked`() {
        assertTrue(GameBrewParser.validArticleTitle("GTA:_Chinatown_Wars_Switch"))
        assertTrue(GameBrewParser.validArticleTitle("Talk"))
        listOf("Special:RecentChanges", "File:cover.png", "Template:Games", "User_talk:Example", "Category:Games").forEach {
            assertFalse(it, GameBrewParser.validArticleTitle(it))
        }
    }

}
