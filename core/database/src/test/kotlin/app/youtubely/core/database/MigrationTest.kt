// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Builds a version 1 database from the exported schema (what the 0.4 app left on the phone),
 * puts rows in it and opens it with the current database class, which runs the automatic migration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun version1OpensAsVersion2WithItsData() =
        runTest {
            val file = context.getDatabasePath("migration.db").apply { parentFile?.mkdirs() }
            createVersion1(file)

            val database =
                Room
                    .databaseBuilder(context, YouTubelyDatabase::class.java, file.absolutePath)
                    .setDriver(AndroidSQLiteDriver())
                    .build()
            try {
                val subscription = database.subscriptionDao().getAll().single()
                assertEquals("Name", subscription.name)
                assertEquals(false, subscription.notify)
                assertEquals(false, subscription.autoDownload)
                assertNull(subscription.groupName)
                assertNull(subscription.lastRefreshedAt)
                assertEquals(5_000L, database.historyDao().get("v")?.positionMs)
                // The new tables work.
                assertEquals(0, database.playlistDao().items(1).size)
                assertEquals(0, database.feedDao().pending().size)
            } finally {
                database.close()
            }
        }

    @Test
    fun version2OpensAsVersion3WithItsData() =
        runTest {
            val file = context.getDatabasePath("migration2.db").apply { parentFile?.mkdirs() }
            createVersion(file, 2) { db ->
                db.execSQL(
                    "INSERT INTO subscriptions (channelId, name, avatarUrl, isVerified, subscribedAt, notify, " +
                        "autoDownload, groupName, lastRefreshedAt) VALUES ('UCa', 'Name', NULL, 1, 100, 1, 1, 'News', 300)",
                )
            }
            val database =
                Room
                    .databaseBuilder(context, YouTubelyDatabase::class.java, file.absolutePath)
                    .setDriver(AndroidSQLiteDriver())
                    .build()
            try {
                val subscription = database.subscriptionDao().getAll().single()
                assertEquals(true, subscription.autoDownload)
                assertEquals("News", subscription.groupName)
                // New rule columns take their defaults: Shorts and live skipped, no limits.
                assertNull(subscription.adQuality)
                assertEquals(true, subscription.adSkipShorts)
                assertEquals(true, subscription.adSkipLive)
                assertEquals(0, subscription.adMaxMinutes)
                assertEquals(false, subscription.adDeleteWatched)
                // The new tables work.
                database.trafficDao().add(day = 1, metered = true, kind = "Video", bytes = 10)
                database.trafficDao().add(day = 1, metered = true, kind = "Video", bytes = 5)
                assertEquals(15L, database.trafficDao().observeMeteredSince(0).first())
                assertNull(database.channelPlaybackDao().get("UCa"))
            } finally {
                database.close()
            }
        }

    private fun createVersion1(file: File) =
        createVersion(file, 1) { db ->
            db.execSQL(
                "INSERT INTO subscriptions (channelId, name, avatarUrl, isVerified, subscribedAt) " +
                    "VALUES ('UCa', 'Name', NULL, 1, 100)",
            )
            db.execSQL(
                "INSERT INTO history (videoId, title, channelId, channelName, channelAvatarUrl, thumbnailUrl, " +
                    "durationSeconds, streamType, positionMs, watchedAt) " +
                    "VALUES ('v', 'T', NULL, NULL, NULL, NULL, 60, 'Video', 5000, 200)",
            )
        }

    /** Creates the tables of schema [version] as an older app did, then lets [fill] add rows. */
    private fun createVersion(
        file: File,
        version: Int,
        fill: (SQLiteDatabase) -> Unit,
    ) {
        val schema =
            JSONObject(File("schemas/app.youtubely.core.database.YouTubelyDatabase/$version.json").readText())
                .getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                for (j in 0 until (indices?.length() ?: 0)) {
                    db.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            fill(db)
            db.version = version
        } finally {
            db.close()
        }
    }
}
