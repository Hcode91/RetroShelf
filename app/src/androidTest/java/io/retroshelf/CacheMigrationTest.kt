package io.retroshelf

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheMigrationTest {
    @Test fun upgradePreservesOldEntriesAndPersistsEmptySnapshots() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cache-policy-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE games (id TEXT NOT NULL PRIMARY KEY, provider TEXT NOT NULL, json TEXT NOT NULL, favorite INTEGER NOT NULL, viewed INTEGER NOT NULL, cachedAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE downloads (id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, url TEXT NOT NULL, filename TEXT NOT NULL, authorization TEXT NOT NULL, destination TEXT NOT NULL, created INTEGER NOT NULL, state TEXT NOT NULL, progress INTEGER NOT NULL, localUri TEXT)")
            old.execSQL("INSERT INTO games VALUES ('udb:saved', 'udb', '{}', 1, 1, 1)")
            old.version = 1
        }
        fun open() = Room.databaseBuilder(context, ShelfDatabase::class.java, name).addMigrations(ShelfDatabase.MIGRATION_1_2).build()
        try {
            open().use { migrated ->
                assertTrue(migrated.shelf().game("udb:saved")!!.favorite)
                assertEquals(1L, migrated.shelf().snapshot("udb")!!.fetchedAt)
                migrated.shelf().replaceCatalogue("empty", emptyList())
            }
            open().use { reopened ->
                assertNotNull(reopened.shelf().snapshot("empty"))
                assertTrue(reopened.shelf().catalogue("empty").isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }
}
