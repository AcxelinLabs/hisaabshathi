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
            .addMigrations(LedgerDb.ADD_CORRECTIONS, LedgerDb.ADD_SAMPLES).allowMainThreadQueries().build()
        assertEquals(60000L, db.entries().allOldestFirst().single().amountPaise)
        db.corrections().add(Correction(1, "hi", "voice", "heard", "WORK 6", "WORK 600"))
        assertEquals("WORK 600", db.corrections().all().single().kept)
        db.close()
    }

    /** 0.7.x (version 2, with the correction log) to 0.8.0: entries and corrections kept, samples ready. */
    @Test fun versionTwoGainsSamples() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getDatabasePath("migrate2.db").apply { parentFile?.mkdirs(); delete() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                    "`createdAtMs` INTEGER NOT NULL, `type` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `hours` REAL, `pieces` INTEGER, " +
                    "`ratePaise` INTEGER, `category` TEXT, `note` TEXT NOT NULL, `source` TEXT NOT NULL, `attachment` TEXT, " +
                    "`prevHash` TEXT NOT NULL, `hash` TEXT NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `corrections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`createdAtMs` INTEGER NOT NULL, `lang` TEXT NOT NULL, `source` TEXT NOT NULL, `heard` TEXT NOT NULL, " +
                    "`understood` TEXT NOT NULL, `kept` TEXT)",
            )
            db.execSQL("INSERT INTO entries VALUES (1, 20000, 1, 'WORK', 60000, 8.0, NULL, NULL, NULL, 'आज', 'voice', NULL, 'g', 'h')")
            db.execSQL("INSERT INTO corrections VALUES (1, 1, 'hi', 'voice', 'heard', 'WORK 6', 'WORK 600')")
            db.version = 2
        }
        val db = Room.databaseBuilder(context, LedgerDb::class.java, file.path)
            .addMigrations(LedgerDb.ADD_CORRECTIONS, LedgerDb.ADD_SAMPLES).allowMainThreadQueries().build()
        assertEquals(60000L, db.entries().allOldestFirst().single().amountPaise)
        assertEquals("WORK 600", db.corrections().all().single().kept)
        val queue = com.acxelin.hisaabsaathi.data.SampleQueue(db.samples())
        val s = com.acxelin.hisaabsaathi.core.Improve.fromCorrection(db.corrections().all().single().toCorrection(), "Odisha", "0.8.0")
        queue.add(s, byteArrayOf(1, 2, 3))
        assertEquals(s, queue.oldest(5).single())
        assertEquals(3, queue.audio(s.id)?.size)
        db.close()
    }
}
