package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.PrivateKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Opens a sealed batch, as the pipeline does (server/hisaab_improve/crypto.py). */
internal fun openSealed(sealed: ByteArray, key: PrivateKey): ByteArray {
    val b = ByteBuffer.wrap(sealed)
    val magic = ByteArray(4).also(b::get)
    check(magic.contentEquals(Seal.MAGIC)) { "not sealed" }
    b.position(b.position() + 8) // key id
    val wrapped = ByteArray(b.short.toInt() and 0xffff).also(b::get)
    val nonce = ByteArray(12).also(b::get)
    val header = sealed.copyOf(b.position())
    val rsa = Cipher.getInstance("RSA/ECB/OAEPPadding").apply { init(Cipher.DECRYPT_MODE, key, Seal.OAEP) }
    val aes = rsa.doFinal(wrapped)
    val gcm = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(aes, "AES"), GCMParameterSpec(128, nonce)); updateAAD(header) }
    return gcm.doFinal(sealed, header.size, sealed.size - header.size)
}

class SealTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
    private val seal = Seal(pair.public.encoded)

    @Test fun onlyThePrivateKeyOpensIt() {
        val zip = Improve.batch(listOf(Sample("aaaabbbbccccdddd", 1, "hi", "typed", "300 एडवांस", "ENTRY ADVANCE 300", "", "saved")), { null }, "t", 1)
        val sealed = seal.seal(zip)
        assertArrayEquals(Seal.MAGIC, sealed.copyOf(4))
        assertArrayEquals(seal.keyId, sealed.copyOfRange(4, 12))
        assertFalse("plain text must not show", String(sealed, Charsets.ISO_8859_1).contains("samples.jsonl"))
        assertArrayEquals(zip, openSealed(sealed, pair.private))
        // Each batch gets its own AES key and nonce.
        assertFalse(seal.seal(zip).contentEquals(sealed))
    }

    @Test fun anyChangeIsDetected() {
        val sealed = seal.seal(ByteArray(1000) { it.toByte() })
        for (i in listOf(5, 20, sealed.size - 1)) {
            val bad = sealed.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertThrows(Exception::class.java) { openSealed(bad, pair.private) }
        }
    }

    @Test fun weakOrMissingKeysAreRefused() {
        val small = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertThrows(IllegalArgumentException::class.java) { Seal(small.public.encoded) }
        assertNull(Seal.fromBase64(""))
        assertNull(Seal.fromBase64("not a key"))
        assertTrue(Seal.fromBase64(java.util.Base64.getEncoder().encodeToString(pair.public.encoded)) != null)
    }

    /** Seals a known text with the shared test key; server/tests opens build/sealed-by-kotlin.bin's committed copy. */
    @Test fun sealsWithTheSharedTestKey() {
        val pub = javaClass.getResource("/test_team_key.pub.b64")!!.readText()
        val s = Seal.fromBase64(pub)!!
        val sealed = s.seal("HisaabSaathi sealed by Kotlin".toByteArray())
        File("build").mkdirs()
        File("build/sealed-by-kotlin.bin").writeBytes(sealed)
        assertEquals(4 + 8 + 2 + 384 + 12 + 29 + 16, sealed.size)
    }
}
