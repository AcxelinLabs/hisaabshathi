package com.acxelin.hisaabsaathi.core

import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Seals a batch to the team's public key before it leaves the phone, so only the training
 * pipeline (which holds the private key) can open it. The upload service, the cloud storage
 * and anyone who obtains a copy see only ciphertext.
 *
 * Format "HSE1": magic (4) · key id (8) · wrapped key length (2, big-endian) · wrapped key ·
 * nonce (12) · AES-256-GCM ciphertext with its 16-byte tag. A fresh AES key per batch is
 * wrapped with RSA-OAEP (SHA-256, MGF1-SHA-256). Everything before the ciphertext is
 * authenticated as associated data. The key id is the first 8 bytes of the SHA-256 of the
 * public key (X.509 SubjectPublicKeyInfo), so the pipeline can hold several keys and rotate.
 * Must match server/hisaab_improve/crypto.py.
 */
class Seal(publicKeyDer: ByteArray) {
    private val key: PublicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer))
    val keyId: ByteArray = keyId(publicKeyDer)

    init {
        require((key as java.security.interfaces.RSAPublicKey).modulus.bitLength() >= 3072) { "team key must be RSA 3072 bits or more" }
    }

    fun seal(plain: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        val aes = ByteArray(32).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val wrap = Cipher.getInstance("RSA/ECB/OAEPPadding")
        wrap.init(Cipher.ENCRYPT_MODE, key, OAEP, random)
        val wrapped = wrap.doFinal(aes)
        val header = ByteArrayOutputStream().apply {
            write(MAGIC); write(keyId)
            write(wrapped.size ushr 8); write(wrapped.size and 0xff); write(wrapped)
            write(nonce)
        }.toByteArray()
        val gcm = Cipher.getInstance("AES/GCM/NoPadding")
        gcm.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aes, "AES"), GCMParameterSpec(128, nonce))
        gcm.updateAAD(header)
        aes.fill(0)
        return header + gcm.doFinal(plain)
    }

    companion object {
        val MAGIC = "HSE1".toByteArray()

        /** Spelled out: some Android providers otherwise use SHA-1 for MGF1. */
        val OAEP = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)

        fun keyId(publicKeyDer: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(publicKeyDer).copyOf(8)

        /** From the base64 text the build is given (-Phisaab.shareKey=...); null if empty or not a usable key. */
        fun fromBase64(text: String): Seal? = text.trim().takeIf { it.isNotEmpty() }
            ?.let { runCatching { Seal(java.util.Base64.getDecoder().decode(it)) }.getOrNull() }
    }
}
