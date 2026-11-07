package io.retroshelf

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ZipertoTests {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun json() = JsonParser.parseString(fixture("ziperto-detail.json")).asJsonObject
    @Test fun `real WordPress metadata normalizes without following file-host links`() {
        val g = ZipertoParser.game(json())!!
        assertEquals("224761", g.slug)
        assertEquals(listOf("SWITCH"), g.consoles)
        assertTrue(g.title.contains("Wabisabi SushiDerby"))
        assertTrue(g.description.contains("Simulation"))
        assertFalse(g.description.contains("Jailbreak"))
        assertTrue(g.artwork!!.endsWith("-cover.webp"))
        assertEquals(2, g.screenshots.size)
        assertEquals("KODANSHA", g.developer)
        assertEquals("KODANSHA", g.publisher)
        assertEquals("July 15, 2026", g.release)
        assertEquals(listOf("Action", "Racing", "Simulation"), g.genres)
        assertTrue(g.detailsFetchedAt > 0)
        assertTrue(g.downloads.isEmpty())
    }
    @Test fun `missing content falls back to source excerpt and unsafe records are rejected`() {
        val j = json().apply { remove("content") }
        assertTrue(ZipertoParser.game(j)!!.description.isNotBlank())
        assertNull(ZipertoParser.game(j)!!.artwork)
        assertEquals(0L, ZipertoParser.game(j)!!.detailsFetchedAt)
        j.addProperty("link", "javascript:alert(1)")
        assertNull(ZipertoParser.game(j))
        assertNull(ZipertoParser.game(JsonObject()))
    }
    @Test fun `public API requests one explicit page with correct cursor and reuses first page`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val p = Ziperto(JsonHttp(JsonHttp.client()), server.url("/wp/v2/").toString(), pageSize = 2)
            server.enqueue(MockResponse().setBody(fixture("ziperto-page1.json")).setHeader("X-WP-TotalPages",2).setHeader("X-WP-Total",4))
            val first = p.fetchCataloguePage(1)
            assertEquals(2, first.games.size); assertEquals(2, first.nextPage); assertEquals(4, first.total)
            val request = server.takeRequest()
            assertEquals("3534", request.requestUrl!!.queryParameter("categories"))
            assertEquals("2", request.requestUrl!!.queryParameter("per_page"))
            assertEquals("1", request.requestUrl!!.queryParameter("page"))
            assertTrue(request.requestUrl!!.queryParameter("_fields")!!.contains("content"))
            p.fetchGames(false); assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setBody(fixture("ziperto-page2.json")).setHeader("X-WP-TotalPages",2).setHeader("X-WP-Total",4))
            assertNull(p.fetchCataloguePage(2).nextPage)
            assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun `manual details refresh fetches only the numeric article`() = runTest {
        MockWebServer().use { server ->
            server.start(); val p = Ziperto(JsonHttp(JsonHttp.client()), server.url("/wp/v2/").toString())
            server.enqueue(MockResponse().setBody(fixture("ziperto-detail.json")))
            assertEquals("ziperto:224761", p.fetchGameDetails(ZipertoParser.game(json())!!).id)
            assertTrue(server.takeRequest().path!!.startsWith("/wp/v2/posts/224761?"))
            assertEquals(1, server.requestCount)
            try { p.fetchGameDetails("ziperto:../wp-admin"); fail("Unsafe identifier accepted") } catch(_: IllegalArgumentException) { }
        }
    }
    @Test fun `empty pages and network or pagination changes fail without auto-crawling`() = runTest {
        MockWebServer().use { server ->
            server.start(); val p = Ziperto(JsonHttp(JsonHttp.client()), server.url("/wp/v2/").toString())
            server.enqueue(MockResponse().setBody("[]").setHeader("X-WP-TotalPages",0).setHeader("X-WP-Total",0))
            assertTrue(p.fetchCataloguePage(1).games.isEmpty())
            server.enqueue(MockResponse().setBody("[]"))
            try { p.fetchCataloguePage(1); fail("Missing pagination accepted") } catch(_: java.io.IOException) { }
            server.enqueue(MockResponse().setResponseCode(403))
            try { p.fetchCataloguePage(1); fail("403 bypassed") } catch(_: java.io.IOException) { }
            server.enqueue(MockResponse().setBody("[{\"changed\":true}]").setHeader("X-WP-TotalPages",1).setHeader("X-WP-Total",1))
            try { p.fetchCataloguePage(1); fail("Malformed records accepted") } catch(_: java.io.IOException) { }
        }
    }
}
