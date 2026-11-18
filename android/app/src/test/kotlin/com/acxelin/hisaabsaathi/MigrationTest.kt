package com.acxelin.hisaabsaathi

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.Correction
import com.acxelin.hisaabsaathi.data.LedgerDb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A worker's existing ledger (database version 1, before 0.6.2) opens after the update with every entry kept. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class MigrationTest {
    @Test fun versionOneLedgerKeepsItsEntries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getDatabasePath("migrate.db").apply { parentFile?.mkdirs(); delete() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                    "`createdAtMs` INTEGER NOT NULL, `type` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `hours` REAL, `pieces` INTEGER, " +
                    "`ratePaise` INTEGER, `category` TEXT, `note` TEXT NOT NULL, `source` TEXT NOT NULL, `attachment` TEXT, " +
                    "`prevHash` TEXT NOT NULL, `hash` TEXT NOT NULL)",
            )
            db.execSQL("INSERT INTO entries VALUES (1, 20000, 1, 'WORK', 60000, 8.0, NULL, NULL, NULL, 'आज', 'voice', NULL, 'g', 'h')")
            db.version = 1
        }
        val db = Room.databaseBuilder(context, LedgerDb::class.java, file.path)
            .addMigrations(LedgerDb.ADD_CORRECTIONS).allowMainThreadQueries().build()
        assertEquals(60000L, db.entries().allOldestFirst().single().amountPaise)
        db.corrections().add(Correction(1, "hi", "voice", "heard", "WORK 6", "WORK 600"))
        assertEquals("WORK 600", db.corrections().all().single().kept)
        db.close()
    }
}
