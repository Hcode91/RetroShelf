package io.retroshelf

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ProviderTests {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun game() = Normalizer.universal(JsonParser.parseString(fixture("universal.json")).asJsonArray[0].asJsonObject)!!
    @Test fun `console filtering search sort paging and empty results`() {
        val g = game()
        assertEquals(1, cataloguePage(listOf(g), GameQuery("3DS", "2048")).total)
        assertEquals(0, cataloguePage(listOf(g), GameQuery("GBA")).total)
        assertEquals(0, cataloguePage(listOf(g), GameQuery("3DS", "does not exist")).total)
        assertEquals(0, cataloguePage(listOf(g), GameQuery("3DS", page = 1)).games.size)
        assertTrue(cataloguePage(listOf(g, g.copy(id = "b", title = "Zebra")), GameQuery("3DS", pageSize = 1)).hasMore)
        assertEquals("Zebra", cataloguePage(listOf(g, g.copy(title="Zebra", updated="9999")), GameQuery("3DS", sort=Sort.UPDATED)).games.first().title)
    }
    @Test fun `registry advertises only supported consoles`() {
        val http = JsonHttp(JsonHttp.client())
        val registry = ProviderRegistry(listOf(UniversalDb(http), HomebrewRepository(http)) + GameBrewCatalogue.all.map { GameBrew(http, catalogue = it) })
        assertEquals(setOf("udb", "gamebrew-3ds"), registry.forConsole("3DS").map { it.id }.toSet())
        assertEquals(setOf("hub", "gamebrew"), registry.forConsole("GBA").map { it.id }.toSet())
        assertEquals("GameBrew homebrew", registry.get("gamebrew").section)
        assertTrue(registry.forConsole("PS2").isEmpty())
        assertEquals(21, Consoles.all.size)
    }
    @Test fun `real universal fixture normalized with reviewed distribution grant`() {
        val g = game()
        assertEquals("2048 - 3DS Port", g.title)
        assertEquals(listOf("3DS"), g.consoles)
        assertTrue(g.description.contains("Maurits"))
        assertTrue(g.downloads.first().size!! > 0)
        assertNotNull(g.downloads.first().authorization)
        val changed = JsonParser.parseString(fixture("universal.json")).asJsonArray[0].asJsonObject
        changed.addProperty("slug", "unreviewed-port")
        assertNull(Normalizer.universal(changed)!!.downloads.first().authorization)
    }
    @Test fun `real gba manifest normalizes source descriptions files and screenshots`() {
        val g = Normalizer.hub(JsonParser.parseString(fixture("gba.json")).asJsonObject, "gbadev-org/games")!!
        assertEquals("2004Mbit Compo Game", g.title)
        assertEquals(listOf("GBA"), g.consoles)
        assertTrue(g.artwork!!.endsWith("/screen.png"))
        assertEquals("GBA", g.downloads.first().format)
        assertNull(g.downloads.first().authorization)
    }
    @Test fun `malformed metadata is rejected without synthetic games`() {
        assertNull(Normalizer.universal(JsonParser.parseString("{}").asJsonObject))
        assertNull(Normalizer.hub(JsonParser.parseString("{\"slug\":\"oops\"}").asJsonObject, "gbadev-org/games"))
    }
    @Test fun `embedded source html is converted to plain text`() {
        val text = cleanDescription(fixture("description.html"))
        assertTrue(text.contains("Explore tiny worlds."))
        assertFalse(text.contains("malicious"))
        assertFalse(text.contains("<b>"))
    }
    @Test fun `mock API success empty response and failure are deterministic`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val provider = UniversalDb(JsonHttp(JsonHttp.client()), server.url("/full.json").toString())
            server.enqueue(MockResponse().setBody(fixture("universal.json")))
            assertEquals(1, provider.fetchGames().size)
            assertEquals(1, server.requestCount)
            assertEquals(1, provider.fetchGames().size)
            assertEquals(1, server.requestCount) // memory cache avoids repeated requests
            server.enqueue(MockResponse().setBody("[]"))
            assertTrue(provider.fetchGames(true).isEmpty())
            server.enqueue(MockResponse().setResponseCode(503))
            try { provider.fetchGames(true); fail("Expected HTTP failure") } catch(e: java.io.IOException) { assertTrue(e.message!!.contains("503")) }
            server.enqueue(MockResponse().setBody("{\"changed\":true}"))
            try { provider.fetchGames(true); fail("Expected schema error") } catch(e: java.io.IOException) { assertTrue(e.message!!.contains("format")) }
        }
    }
    @Test fun `broken json fails cleanly`() = runTest {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setBody("not-json[{"))
            try { UniversalDb(JsonHttp(JsonHttp.client()), server.url("/").toString()).fetchGames(); fail("Expected malformed data failure") } catch(_: com.google.gson.JsonParseException) { }
        }
    }
    @Test fun `download URL and filename validation`() {
        listOf("file:///etc/passwd", "javascript:alert(1)", "http://example.com/a", "https://127.0.0.1/a", "https://192.168.1.1/a", "https://[::1]/a", "https://localhost/a", "https://user:pass@example.com/a", "https://example.com:444/a", "https://example.local/a").forEach { assertFalse(it, UrlSafety.valid(it)) }
        assertTrue(UrlSafety.valid("https://github.com/owner/repo/releases/download/v1/game.3dsx"))
        assertEquals("evil.gba", UrlSafety.filename("../../evil.gba"))
        assertEquals("homebrew.bin", UrlSafety.filename(".."))
        try { UrlSafety.validate(GameFile("a", "https://example.com/a")); fail("Unreviewed file allowed") } catch(_: IllegalArgumentException) { }
        UrlSafety.validate(game().downloads.first())
    }
}
