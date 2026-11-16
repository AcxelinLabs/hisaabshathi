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
import com.acxelin.hisaabsaathi.core.Entry
import com.acxelin.hisaabsaathi.core.EntryType
import com.acxelin.hisaabsaathi.core.ExpenseCategory
import com.acxelin.hisaabsaathi.core.HashChain
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
) {
    fun toEntry() = Entry(
        id, epochDay, createdAtMs, EntryType.valueOf(type), amountPaise, hours, pieces, ratePaise,
        category?.let(ExpenseCategory::valueOf), note, source, attachment, prevHash, hash,
    )

    companion object {
        fun from(e: Entry) = EntryRow(
            e.id, e.epochDay, e.createdAtMs, e.type.name, e.amountPaise, e.hours, e.pieces, e.ratePaise,
            e.category?.name, e.note, e.source, e.attachment, e.prevHash, e.hash,
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
    open suspend fun deleteAndReseal(id: Long) {
        val keep = allOldestFirst().filter { it.id != id }
        clear()
        var prev = HashChain.GENESIS
        val resealed = keep.map {
            val sealed = HashChain.seal(prev, it.toEntry())
            prev = sealed.hash
            EntryRow.from(sealed)
        }
        insertAll(resealed)
    }
}

@Database(entities = [EntryRow::class], version = 1, exportSchema = false)
abstract class LedgerDb : RoomDatabase() {
    abstract fun entries(): EntryDao

    companion object {
        fun open(context: Context): LedgerDb {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, LedgerDb::class.java, "ledger.db")
                .openHelperFactory(SupportOpenHelperFactory(DbKey.get(context)))
                .build()
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
    suspend fun eraseAll() = dao.clear()
    suspend fun oldestFirst(): List<Entry> = dao.allOldestFirst().map { it.toEntry() }
}
