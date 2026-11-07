package io.retroshelf

import com.google.gson.JsonObject
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GameBrewPlatformTests(private val config: GameBrewCatalogue) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun platforms() = GameBrewCatalogue.all.drop(1).map { arrayOf(it) }
    }
    private fun fixture(suffix: String) = javaClass.classLoader!!.getResource("gamebrew-${config.console.lowercase()}-$suffix.html")!!.readText()
    private fun games() = GameBrewParser.catalogue(fixture("catalogue"), config.console, config.providerId)
    @Test fun `real catalogue metadata and artwork stay scoped to selected console`() {
        val games = games()
        assertTrue(games.size in 2..3)
        assertTrue(games.all { it.provider == config.providerId && it.id == "${config.providerId}:${it.slug}" && it.consoles == listOf(config.console) })
        assertTrue(games.all { it.artwork != null && it.developer != null && it.genres.isNotEmpty() })
        assertEquals(games.size, cataloguePage(games, GameQuery(config.console)).total)
        assertTrue(cataloguePage(games, GameQuery("GBA")).games.isEmpty())
        assertEquals(1, cataloguePage(games, GameQuery(config.console, "Blockamok")).total)
    }
    @Test fun `real platform details retain attribution source and disable unreviewed downloads`() {
        val summary = games().first { it.slug.startsWith("Blockamok_Remix_") }
        val detail = GameBrewParser.details(fixture("details"), summary)
        assertEquals(summary.provider, detail.provider)
        assertEquals(summary.consoles, detail.consoles)
        assertEquals(summary.sourceUrl, detail.sourceUrl)
        assertTrue(detail.description.contains(if(config.console in setOf("WII", "WIIU")) "physics-based" else "CPU-only"))
        if(config.console == "WIIU") assertTrue(detail.screenshots.isEmpty()) else assertTrue(detail.screenshots.isNotEmpty())
        assertTrue(detail.downloads.isNotEmpty())
        assertTrue(detail.downloads.all { it.authorization == null })
    }
    @Test fun `adapter loads only its own catalogue and reuses memory cache`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val provider = GameBrew(JsonHttp(JsonHttp.client()), server.url("/api.php").toString(), config)
            val body = JsonObject().apply { add("parse", JsonObject().apply { addProperty("text", fixture("catalogue")) }) }
            server.enqueue(MockResponse().setBody(body.toString()))
            assertEquals(games().size, provider.fetchGames().size)
            provider.fetchGames()
            assertEquals(1, server.requestCount)
            assertEquals(config.page, server.takeRequest().requestUrl!!.queryParameter("page"))
            assertEquals(setOf(config.console), provider.supportedConsoles)
            assertTrue(provider.website.endsWith(config.page))
        }
    }
    @Test fun `restarted adapter opens saved article without another catalogue request`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val provider = GameBrew(JsonHttp(JsonHttp.client()), server.url("/api.php").toString(), config)
            val summary = games().first { it.slug.startsWith("Blockamok_Remix_") }
            val body = JsonObject().apply { add("parse", JsonObject().apply { addProperty("text", fixture("details")) }) }
            server.enqueue(MockResponse().setBody(body.toString()))
            val detail = provider.fetchGameDetails(summary)
            assertEquals(config.console == "WIIU", detail.screenshots.isEmpty())
            assertEquals(1, server.requestCount)
            assertEquals(summary.slug, server.takeRequest().requestUrl!!.queryParameter("page"))
            try { provider.fetchGameDetails(summary.copy(provider="gamebrew")); fail("Wrong provider accepted") } catch(_: IllegalArgumentException) { }
            assertEquals(1, server.requestCount)
        }
    }
}
