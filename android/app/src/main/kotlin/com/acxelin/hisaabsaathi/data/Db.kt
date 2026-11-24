package com.acxelin.hisaabsaathi.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.acxelin.hisaabsaathi.core.Correction
import com.acxelin.hisaabsaathi.core.Corrections
import com.acxelin.hisaabsaathi.core.Entry
import com.acxelin.hisaabsaathi.core.EntryType
import com.acxelin.hisaabsaathi.core.ExpenseCategory
import com.acxelin.hisaabsaathi.core.HashChain
import com.acxelin.hisaabsaathi.core.Improve
import com.acxelin.hisaabsaathi.core.Sample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "entries")
data class EntryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val createdAtMs: Long,
    val type: String,
    val amountPaise: Long,
    val hours: Double?,
    val pieces: Int?,
    val ratePaise: Long?,
    val category: String?,
    val note: String,
    val source: String,
    val attachment: String?,
    val prevHash: String,
    val hash: String,
    @androidx.room.ColumnInfo(defaultValue = "") val detail: String = "",
) {
    fun toEntry() = Entry(
        id, epochDay, createdAtMs, EntryType.valueOf(type), amountPaise, hours, pieces, ratePaise,
        category?.let { c -> ExpenseCategory.entries.firstOrNull { it.name == c } ?: ExpenseCategory.OTHER }, note, source, attachment, prevHash, hash, detail,
    )

    companion object {
        fun from(e: Entry) = EntryRow(
            e.id, e.epochDay, e.createdAtMs, e.type.name, e.amountPaise, e.hours, e.pieces, e.ratePaise,
            e.category?.name, e.note, e.source, e.attachment, e.prevHash, e.hash, e.detail,
        )
    }
}

@Dao
abstract class EntryDao {
    @Query("SELECT * FROM entries ORDER BY id DESC")
    abstract fun all(): Flow<List<EntryRow>>

    @Query("SELECT * FROM entries ORDER BY id ASC")
    abstract suspend fun allOldestFirst(): List<EntryRow>

    @Query("SELECT hash FROM entries ORDER BY id DESC LIMIT 1")
    abstract suspend fun lastHash(): String?

    @Insert
    abstract suspend fun insert(row: EntryRow)

    @Insert
    abstract suspend fun insertAll(rows: List<EntryRow>)

    @Query("DELETE FROM entries")
    abstract suspend fun clear()

    /** Appends entries, linking each to the one before it. */
    @Transaction
    open suspend fun append(entries: List<Entry>) {
        var prev = lastHash() ?: HashChain.GENESIS
        for (e in entries) {
            val sealed = HashChain.seal(prev, e)
            insert(EntryRow.from(sealed))
            prev = sealed.hash
        }
    }

    /** Removes one entry and re-links the rest so the chain stays verifiable. */
    @Transaction
    open suspend fun deleteAndReseal(id: Long) = reseal(allOldestFirst().filter { it.id != id }.map { it.toEntry() })

    /** Replaces one entry (same id, same place in the chain) and re-links the rest. */
    @Transaction
    open suspend fun replaceAndReseal(entry: Entry) = reseal(allOldestFirst().map { if (it.id == entry.id) entry else it.toEntry() })

    /** Puts a deleted entry back where it was (undo). */
    @Transaction
    open suspend fun restoreAndReseal(entry: Entry) =
        reseal((allOldestFirst().map { it.toEntry() }.filter { it.id != entry.id } + entry).sortedBy { it.id })

    /** Adds entries from a backup file, skipping any already here, and orders the whole record by when each line was made. */
    @Transaction
    open suspend fun mergeAndReseal(incoming: List<Entry>): Int {
        val have = allOldestFirst().map { it.toEntry() }
        val key = { e: Entry -> Triple(e.createdAtMs, e.type, e.amountPaise) to e.epochDay }
        val known = have.map(key).toHashSet()
        val fresh = incoming.filter { known.add(key(it)) }
        if (fresh.isEmpty()) return 0
        val all = (have + fresh.map { it.copy(id = 0) }).sortedWith(compareBy({ it.createdAtMs }, { it.id }))
        reseal(all.mapIndexed { i, e -> e.copy(id = i + 1L) })
        return fresh.size
    }

    private suspend fun reseal(entriesInOrder: List<Entry>) {
        clear()
        var prev = HashChain.GENESIS
        insertAll(entriesInOrder.map { e -> HashChain.seal(prev, e).also { prev = it.hash }.let(EntryRow::from) })
    }
}

/** One fix the worker made to what the app understood (core/Corrections.kt). */
@Entity(tableName = "corrections")
data class CorrectionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMs: Long,
    val lang: String,
    val source: String,
    val heard: String,
    val understood: String,
    val kept: String?,
) {
    fun toCorrection() = Correction(createdAtMs, lang, source, heard, understood, kept)

    companion object {
        fun from(c: Correction) = CorrectionRow(0, c.createdAtMs, c.lang, c.source, c.heard, c.understood, c.kept)
    }
}

@Dao
abstract class CorrectionDao {
    @Query("SELECT COUNT(*) FROM corrections")
    abstract fun count(): Flow<Int>

    @Query("SELECT * FROM corrections ORDER BY id ASC")
    abstract suspend fun all(): List<CorrectionRow>

    @Insert
    abstract suspend fun insert(row: CorrectionRow)

    @Query("DELETE FROM corrections WHERE id NOT IN (SELECT id FROM corrections ORDER BY id DESC LIMIT :keep)")
    abstract suspend fun trim(keep: Int)

    @Query("DELETE FROM corrections")
    abstract suspend fun clear()

    @Transaction
    open suspend fun add(c: Correction) {
        insert(CorrectionRow.from(c))
        trim(Corrections.LIMIT)
    }
}

/**
 * One exchange waiting to be sent to help improve the app (core/Improve.kt), only for workers
 * who agreed. [json] is the [Sample]; [audio] its WAV clip. Kept here so it is encrypted with
 * the ledger, and deleted once sent.
 */
@Entity(tableName = "samples")
data class SampleRow(
    @PrimaryKey val id: String,
    val createdAtMs: Long,
    val json: String,
    val audio: ByteArray?,
)

/** A sample without its clip: listing many clips at once could exceed Android's row window. */
data class SampleHead(val id: String, val json: String)

@Dao
abstract class SampleDao {
    @Query("SELECT COUNT(*) FROM samples")
    abstract fun count(): Flow<Int>

    @Query("SELECT id, json FROM samples ORDER BY createdAtMs ASC LIMIT :n")
    abstract suspend fun oldest(n: Int): List<SampleHead>

    @Query("SELECT audio FROM samples WHERE id = :id")
    abstract suspend fun audio(id: String): ByteArray?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    abstract suspend fun insert(row: SampleRow)

    @Query("DELETE FROM samples WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<String>)

    @Query("DELETE FROM samples WHERE id NOT IN (SELECT id FROM samples ORDER BY createdAtMs DESC LIMIT :keep)")
    abstract suspend fun trim(keep: Int)

    @Query("DELETE FROM samples")
    abstract suspend fun clear()

    @Transaction
    open suspend fun add(row: SampleRow) {
        insert(row)
        trim(Improve.LIMIT)
    }
}

@Database(entities = [EntryRow::class, CorrectionRow::class, SampleRow::class], version = 4, exportSchema = false)
abstract class LedgerDb : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun corrections(): CorrectionDao
    abstract fun samples(): SampleDao

    companion object {
        fun open(context: Context): LedgerDb {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, LedgerDb::class.java, "ledger.db")
                .openHelperFactory(SupportOpenHelperFactory(runCatching { DbKey.get(context) }.getOrElse { DbKey.startOver(context, it) }))
                .addMigrations(ADD_CORRECTIONS, ADD_SAMPLES, ADD_DETAIL)
                .build()
        }

        /** 0.6.2 adds the correction log; existing records are untouched. */
        val ADD_CORRECTIONS = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `corrections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`createdAtMs` INTEGER NOT NULL, `lang` TEXT NOT NULL, `source` TEXT NOT NULL, `heard` TEXT NOT NULL, " +
                        "`understood` TEXT NOT NULL, `kept` TEXT)",
                )
            }
        }

        /** Database version 4 adds what was eaten or what a loan was for; existing records are untouched. */
        val ADD_DETAIL = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `detail` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** 0.8.0 adds the samples waiting to be sent; existing records are untouched. */
        val ADD_SAMPLES = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `samples` (`id` TEXT NOT NULL, `createdAtMs` INTEGER NOT NULL, " +
                        "`json` TEXT NOT NULL, `audio` BLOB, PRIMARY KEY(`id`))",
                )
            }
        }
    }
}

/**
 * The database passphrase is 32 random bytes. It is stored only in wrapped form, encrypted
 * by an AES key that lives in the Android Keystore and cannot be read out of the phone.
 */
object DbKey {
    private const val ALIAS = "hisaab_db_wrap_key"
    private const val PREFS = "db_key"

    fun get(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wrapped = prefs.getString("wrapped", null)
        val iv = prefs.getString("iv", null)
        if (wrapped != null && iv != null) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            return cipher.doFinal(Base64.decode(wrapped, Base64.NO_WRAP))
        }
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        prefs.edit()
            .putString("wrapped", Base64.encodeToString(cipher.doFinal(passphrase), Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        return passphrase
    }

    /**
     * The phone lost the key that unlocks the ledger (rare: a reset security chip, a broken
     * update). Without this the app would close on every start. The unreadable file is kept
     * aside, not deleted, and a new empty ledger starts; a backup file can be restored into it.
     */
    fun startOver(context: Context, why: Throwable): ByteArray {
        val stamp = System.currentTimeMillis()
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            val f = context.getDatabasePath("ledger.db$suffix")
            if (f.exists()) f.renameTo(java.io.File(f.parentFile, "ledger-unreadable-$stamp.db$suffix"))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
        context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE).edit()
            .putString("last", "ledger key lost (${why.javaClass.simpleName}); old file kept as ledger-unreadable-$stamp.db").apply()
        return get(context)
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }
}

class LedgerRepository(private val dao: EntryDao) {
    val entries: Flow<List<Entry>> = dao.all().map { rows -> rows.map { it.toEntry() } }

    suspend fun add(entries: List<Entry>) = dao.append(entries)
    suspend fun delete(id: Long) = dao.deleteAndReseal(id)
    suspend fun replace(entry: Entry) = dao.replaceAndReseal(entry)
    suspend fun restore(entry: Entry) = dao.restoreAndReseal(entry)
    suspend fun merge(entries: List<Entry>): Int = dao.mergeAndReseal(entries)
    suspend fun eraseAll() = dao.clear()
    suspend fun oldestFirst(): List<Entry> = dao.allOldestFirst().map { it.toEntry() }
}

class CorrectionLog(private val dao: CorrectionDao) {
    val count: Flow<Int> = dao.count()

    suspend fun add(c: Correction) = dao.add(c)
    suspend fun all(): List<Correction> = dao.all().map { it.toCorrection() }
    suspend fun clear() = dao.clear()
}

/** What waits to be sent to help improve the app. */
class SampleQueue(private val dao: SampleDao) {
    val count: Flow<Int> = dao.count()

    suspend fun add(sample: Sample, wav: ByteArray?) = dao.add(SampleRow(sample.id, sample.createdAtMs, Improve.toJson(sample), wav))
    suspend fun oldest(n: Int): List<Sample> = dao.oldest(n).mapNotNull { runCatching { Improve.fromJson(it.json) }.getOrNull() }
    suspend fun audio(id: String): ByteArray? = dao.audio(id)
    suspend fun delete(ids: List<String>) = dao.delete(ids)
    suspend fun clear() = dao.clear()
}
