package io.retroshelf

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.*

@Entity(tableName = "games")
data class StoredGame(@PrimaryKey val id: String, val provider: String, val json: String, val favorite: Boolean = false, val viewed: Long = 0, val cachedAt: Long = 0)
@Entity(tableName = "downloads")
data class DownloadRecord(@PrimaryKey val id: Long, val title: String, val url: String, val filename: String, val authorization: String, val destination: String, val created: Long = System.currentTimeMillis(), val state: String = "Queued", val progress: Int = 0, val localUri: String? = null)
@Dao
interface ShelfDao {
    @Query("SELECT * FROM games WHERE provider = :provider AND cachedAt > 0") suspend fun catalogue(provider: String): List<StoredGame>
    @Query("SELECT * FROM games WHERE id = :id") suspend fun game(id: String): StoredGame?
    @Upsert suspend fun put(game: StoredGame)
    @Query("UPDATE games SET favorite = :favorite WHERE id = :id") suspend fun favorite(id: String, favorite: Boolean)
    @Query("SELECT * FROM games WHERE favorite = 1 ORDER BY id") fun favorites(): Flow<List<StoredGame>>
    @Query("SELECT * FROM games WHERE viewed > 0 ORDER BY viewed DESC LIMIT 30") fun recent(): Flow<List<StoredGame>>
    @Query("DELETE FROM games WHERE favorite = 0 AND viewed = 0") suspend fun clearUnused()
    @Query("UPDATE games SET cachedAt = 0") suspend fun expireCache()
    @Query("UPDATE games SET cachedAt = 0 WHERE provider = :provider") suspend fun expireProvider(provider: String)
    @Transaction suspend fun replaceCatalogue(provider: String, rows: List<StoredGame>) {
        expireProvider(provider)
        rows.forEach { row -> val previous = game(row.id); put(row.copy(favorite = previous?.favorite ?: false, viewed = previous?.viewed ?: 0)) }
    }
    @Query("SELECT * FROM downloads ORDER BY created DESC") fun downloads(): Flow<List<DownloadRecord>>
    @Query("SELECT * FROM downloads") suspend fun downloadSnapshot(): List<DownloadRecord>
    @Upsert suspend fun putDownload(record: DownloadRecord)
}
@Database(entities = [StoredGame::class, DownloadRecord::class], version = 1, exportSchema = false)
abstract class ShelfDatabase : RoomDatabase() { abstract fun shelf(): ShelfDao }

class ShelfRepository(val registry: ProviderRegistry, val dao: ShelfDao) {
    val gson = Gson()
    fun decode(row: StoredGame): Game = gson.fromJson(row.json, Game::class.java)
    val favorites = dao.favorites().map { rows -> rows.map(::decode) }
    val recent = dao.recent().map { rows -> rows.map(::decode) }
    suspend fun catalogue(provider: String, refresh: Boolean): Pair<List<Game>, Boolean> {
        val cached = dao.catalogue(provider)
        if(!refresh && cached.isNotEmpty() && cached.all { System.currentTimeMillis() - it.cachedAt < 6 * 60 * 60 * 1000 }) return cached.map(::decode) to false
        try {
            val games = registry.get(provider).fetchGames(refresh)
            val rows = games.map { g -> StoredGame(g.id, provider, gson.toJson(g), cachedAt = System.currentTimeMillis()) }
            dao.replaceCatalogue(provider, rows)
            return games to false
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { if(cached.isNotEmpty()) return cached.map(::decode) to true; throw e }
    }
    suspend fun view(game: Game) {
        val old = dao.game(game.id)
        dao.put(StoredGame(game.id, game.provider, gson.toJson(game), old?.favorite ?: false, System.currentTimeMillis(), old?.cachedAt ?: 0))
    }
    suspend fun toggleFavorite(game: Game) {
        val old = dao.game(game.id)
        dao.put(StoredGame(game.id, game.provider, gson.toJson(game), !(old?.favorite ?: false), old?.viewed ?: 0, old?.cachedAt ?: 0))
    }
}
class AppGraph(context: Context) {
    val db = Room.databaseBuilder(context, ShelfDatabase::class.java, "retroshelf.db").build()
    val http = JsonHttp(JsonHttp.client())
    val registry = ProviderRegistry(listOf(UniversalDb(http), HomebrewRepository(http)))
    val repository = ShelfRepository(registry, db.shelf())
    val downloads = ShelfDownloads(context.applicationContext, db.shelf())
}
