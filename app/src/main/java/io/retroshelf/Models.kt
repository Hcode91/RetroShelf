package io.retroshelf

import java.net.URI
import java.util.Locale

data class Console(val id: String, val name: String, val family: String)
object Consoles {
    val all = listOf(
        Console("NES", "NES", "Nintendo"), Console("SNES", "SNES", "Nintendo"), Console("N64", "Nintendo 64", "Nintendo"),
        Console("GB", "Game Boy", "Nintendo"), Console("GBC", "Game Boy Color", "Nintendo"), Console("GBA", "Game Boy Advance", "Nintendo"),
        Console("DS", "Nintendo DS", "Nintendo"), Console("3DS", "Nintendo 3DS", "Nintendo"), Console("GC", "GameCube", "Nintendo"), Console("WII", "Nintendo Wii", "Nintendo"),
        Console("WIIU", "Nintendo Wii U", "Nintendo"), Console("SWITCH", "Nintendo Switch", "Nintendo"),
        Console("PS1", "PlayStation", "Sony"), Console("PS2", "PlayStation 2", "Sony"), Console("PSP", "PSP", "Sony"),
        Console("SMS", "Master System", "Sega"), Console("MD", "Mega Drive / Genesis", "Sega"), Console("DC", "Dreamcast", "Sega"),
        Console("AMIGA", "Commodore Amiga", "Other"), Console("ATARI", "Atari consoles", "Other"), Console("PC", "PC homebrew", "Other"))
}
data class GameFile(val name: String, val url: String, val size: Long? = null, val authorization: String? = null) {
    val format: String get() = name.substringAfterLast('.', "Unknown").uppercase(Locale.ROOT)
}
data class Game(
    val id: String, val provider: String, val slug: String, val title: String, val description: String,
    val consoles: List<String>, val sourceUrl: String, val artwork: String? = null,
    val screenshots: List<String> = emptyList(), val developer: String? = null,
    val publisher: String? = null, val release: String? = null, val updated: String? = null,
    val genres: List<String> = emptyList(), val license: String? = null, val downloads: List<GameFile> = emptyList(),
    val detailsFetchedAt: Long = 0 // Local cache timestamp, never shown as source metadata.
)
enum class Sort { TITLE, UPDATED, DEVELOPER }
data class GameQuery(val console: String, val search: String = "", val genre: String = "", val sort: Sort = Sort.TITLE, val page: Int = 0, val pageSize: Int = 24)
data class GamePage(val games: List<Game>, val hasMore: Boolean, val total: Int)
fun cataloguePage(games: List<Game>, query: GameQuery): GamePage {
    require(query.page >= 0 && query.pageSize in 1..100)
    val matches = games.filter { query.console in it.consoles && (query.genre.isBlank() || query.genre in it.genres) &&
        listOf(it.title, it.developer.orEmpty(), it.description).any { text -> text.contains(query.search.trim(), true) } }
    val sorted = when(query.sort) {
        Sort.TITLE -> matches.sortedBy { it.title.lowercase(Locale.ROOT) }
        Sort.UPDATED -> matches.sortedByDescending { it.updated.orEmpty() }
        Sort.DEVELOPER -> matches.sortedBy { it.developer.orEmpty().lowercase(Locale.ROOT) }
    }
    val start = (query.page.toLong() * query.pageSize).coerceAtMost(sorted.size.toLong()).toInt()
    return GamePage(sorted.drop(start).take(query.pageSize), start + query.pageSize < sorted.size, sorted.size)
}
object UrlSafety {
    fun valid(value: String): Boolean = runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        uri.scheme == "https" && uri.rawUserInfo == null && uri.port in listOf(-1,443) &&
            host.contains('.') && host.all { it.isLetterOrDigit() || it == '.' || it == '-' } &&
            !host.endsWith(".localhost") && !host.endsWith(".local") && host != "localhost" &&
            !host.matches(Regex("[0-9.]+")) && !host.endsWith(".") && value.none { it.isISOControl() }
    }.getOrDefault(false)
    fun filename(value: String): String = value.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('.').take(110).ifBlank { "homebrew.bin" }
    fun validate(file: GameFile) { require(valid(file.url)) { "Unsafe or unsupported download URL" }; require(!file.authorization.isNullOrBlank()) { "Distribution authorization not reviewed. Open the original website." } }
}
