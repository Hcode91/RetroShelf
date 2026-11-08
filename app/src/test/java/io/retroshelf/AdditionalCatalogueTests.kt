package io.retroshelf

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AdditionalCatalogueTests {
    private fun fixture(name: String)=javaClass.classLoader!!.getResource(name)!!.readText()
    @Test fun `PC RPGMaker grouping and new consoles register correct sources`() {
        val registry=SourceRegistry.create(JsonHttp(JsonHttp.client()))
        assertEquals(setOf("rpgmaker", "rpgmaker-archive"),registry.forConsole("PC").filter { it.section=="RPGMaker" }.map { it.id }.toSet())
        assertEquals(setOf("VITA", "PS3", "PS4"),Consoles.all.filter { it.id in setOf("VITA","PS3","PS4") }.map { it.id }.toSet())
        assertTrue(registry.forConsole("DS").any { it.id=="gamebrew-ds" })
        assertTrue(registry.forConsole("GB").any { it.id=="gamebrew-gb" })
        assertTrue(registry.forConsole("PSP").any { it.id=="gamebrew-psp" })
        assertFalse(registry.get("steamrip").nativeBrowsing)
        assertFalse(registry.get("nopaystation-ps3").nativeBrowsing)
        assertTrue(registry.get("superpsx").nativeBrowsing)
    }
    @Test fun `browser-only providers never return synthetic games or index blocked sites`() = runTest {
        val source=SourceRegistry.create(JsonHttp(JsonHttp.client())).get("nopaystation-ps3")
        assertTrue(source.connectionHint.contains("WebView"))
        try { source.fetchGames(); fail("Browser source must not claim a native catalogue") } catch(_: java.io.IOException) { }
    }
    @Test fun `RPGMaker real table metadata and details normalize`() {
        val page=HtmlCatalogueParser.catalogue(fixture("rpgmaker-catalogue.html"),HtmlSite.RMN,1)
        assertEquals(2,page.games.size); assertEquals(2,page.nextPage)
        val g=page.games.first()
        assertEquals("Dragon Child",g.title); assertEquals("DragonMedic",g.developer)
        assertEquals("RPG",g.genres.single()); assertEquals(listOf("PC"),g.consoles)
        val detail=HtmlCatalogueParser.details(fixture("rpgmaker-details.html"),HtmlSite.RMN,g)
        assertTrue(detail.description.contains("vagabond")); assertNotNull(detail.artwork)
        assertTrue(detail.downloads.isEmpty())
    }
    @Test fun `SuperPSX native HTML works without its restricted WordPress API`() = runTest {
        MockWebServer().use { server ->
            server.start();val p=HtmlCatalogue(JsonHttp(JsonHttp.client()),HtmlSite.SUPERPSX,server.url("/category/").toString())
            server.enqueue(MockResponse().setBody(fixture("superpsx-catalogue.html")))
            val page=p.fetchCataloguePage(1)
            assertEquals(2,page.games.size); assertEquals(2,page.nextPage)
            assertEquals("/category/",server.takeRequest().path)
            assertTrue(page.games.all { it.artwork!=null && it.consoles==listOf("PS4") })
            val detail=HtmlCatalogueParser.details(fixture("superpsx-details.html"),HtmlSite.SUPERPSX,page.games.first())
            assertTrue(detail.description.isNotBlank());assertNotNull(detail.artwork)
            assertTrue(detail.downloads.isEmpty())
        }
    }
    @Test fun `HTML layout failures and unsafe game URLs are rejected`() {
        assertNull(HtmlCatalogueParser.article("https://example.com/games/1/",HtmlSite.RMN))
        assertNull(HtmlCatalogueParser.article("/admin/",HtmlSite.RMN))
        assertNull(HtmlCatalogueParser.article("/wp-admin/",HtmlSite.SUPERPSX))
        try { HtmlCatalogueParser.catalogue("<h1>Challenge</h1>",HtmlSite.RMN,1);fail("Broken layout accepted") } catch(_: java.io.IOException) { }
    }
    @Test fun `archive real collection API normalizes records and explicit paging`() = runTest {
        MockWebServer().use { server ->
            server.start();val p=RpgMakerArchive(JsonHttp(JsonHttp.client()),server.url("/advancedsearch.php").toString())
            server.enqueue(MockResponse().setBody(fixture("archive-search.json")))
            val page=p.fetchCataloguePage(1)
            assertEquals(3820,page.total); assertEquals(2,page.nextPage)
            assertEquals("Eternity's Point",page.games.first().title)
            assertEquals("RPGMaker",p.section)
            val request=server.takeRequest().requestUrl!!
            assertEquals("collection:rpgmaker-net-game-archive",request.queryParameter("q"))
            assertEquals("50",request.queryParameter("rows"))
            p.fetchGames(false);assertEquals(1,server.requestCount)
        }
        val metadata=JsonParser.parseString(fixture("archive-detail.json")).asJsonObject.getAsJsonObject("metadata")
        val g=ArchiveParser.game(metadata)!!
        assertTrue(g.artwork!!.startsWith("https://archive.org/services/img/"))
        assertFalse(ArchiveParser.validId("../private"));assertFalse(ArchiveParser.validId(".."))
        assertTrue(g.downloads.isEmpty());assertNull(g.release)
    }
    @Test fun `NXbrew uses source table metadata and preserves explicit Switch 2 labels`() = runTest {
        val json=JsonParser.parseString(fixture("nxbrew-posts.json")).asJsonArray
        val g=WordPressParser.game(json[0].asJsonObject,WordPressSite.NXBREW)!!
        assertEquals("nxbrew",g.provider);assertEquals(listOf("SWITCH2"),g.consoles)
        assertEquals("Doraccoon",g.developer); assertEquals("Playstack",g.publisher)
        assertEquals("November 10, 2026",g.release);assertNotNull(g.artwork)
        MockWebServer().use { server ->
            server.start();val p=WordPressSource(JsonHttp(JsonHttp.client()),WordPressSite.NXBREW,server.url("/wp/v2/").toString(),2)
            server.enqueue(MockResponse().setBody(fixture("nxbrew-posts.json")).setHeader("X-WP-TotalPages",654).setHeader("X-WP-Total",1308))
            val page=p.fetchCataloguePage(1)
            assertEquals(1308,page.total);assertEquals(2,page.nextPage)
            assertTrue(page.games.isEmpty()) // These two source records are Switch 2-only.
            assertEquals("22",server.takeRequest().requestUrl!!.queryParameter("categories"))
            assertEquals(setOf("SWITCH"),p.supportedConsoles)
        }
    }
    @Test fun `Game Boy system column keeps Color-only games out of monochrome results`() {
        val game=GameBrewParser.catalogue(fixture("gamebrew-gbc-system.html"),"GB","gamebrew-gb").single()
        assertEquals(listOf("GBC"),game.consoles)
        assertEquals(0,cataloguePage(listOf(game),GameQuery("GB")).total)
        assertEquals(1,cataloguePage(listOf(game),GameQuery("GBC")).total)
        assertTrue(SourceRegistry.create(JsonHttp(JsonHttp.client())).forConsole("GBC").any { it.id=="gamebrew-gb" })
    }

}
