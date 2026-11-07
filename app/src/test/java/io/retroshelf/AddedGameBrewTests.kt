package io.retroshelf

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AddedGameBrewTests(private val console: String, private val slug: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun cases() = listOf(
            arrayOf("DS", "Blimp_Chicken_DS"), arrayOf("GB", "WanWan_GB"),
            arrayOf("VITA", "The_Dark_Queen_Of_Mortholme_Vita"), arrayOf("PSP", "Minicraft_PSP_by_Koutsie"))
    }
    private fun fixture(kind: String) = javaClass.classLoader!!.getResource("gamebrew-${console.lowercase()}-new-$kind.html")!!.readText()
    @Test fun `new homebrew catalogue is scoped and contains real artwork`() {
        val config=GameBrewCatalogue.all.first { it.console==console }
        val games=GameBrewParser.catalogue(fixture("catalogue"),console,config.providerId)
        assertTrue(games.size>=2)
        assertTrue(games.all { it.consoles==listOf(console) && it.provider==config.providerId })
        assertTrue(games.all { it.artwork!=null })
        assertTrue(games.any { it.slug==slug })
        if(console=="GB") {
            assertEquals("X (Akino Hitoshi)",games.first { it.slug==slug }.developer)
            assertEquals("2000/02/03",games.first { it.slug==slug }.updated)
        }
    }
    @Test fun `new homebrew detail stays associated with its source`() {
        val config=GameBrewCatalogue.all.first { it.console==console }
        val summary=GameBrewParser.catalogue(fixture("catalogue"),console,config.providerId).first { it.slug==slug }
        val detail=GameBrewParser.details(fixture("details"),summary)
        assertEquals(summary.id,detail.id)
        assertEquals(summary.consoles,detail.consoles)
        assertTrue(detail.description.isNotBlank())
        assertNotNull(detail.artwork)
        assertTrue(detail.downloads.all { it.authorization==null })
    }
}
