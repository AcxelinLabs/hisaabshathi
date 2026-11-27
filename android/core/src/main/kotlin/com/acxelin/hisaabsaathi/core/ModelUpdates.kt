package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * One published version of a model the app downloads. Files never change once published: a new
 * model is a new [file] name at a new Hugging Face commit ([url] pins it), so a phone can always
 * tell old from new, and a half-done download is never mixed up with another version.
 */
@Serializable
data class ModelRelease(
    /** Which model: "slm-hisaab" (the smart model). */
    val id: String,
    /** Only ever goes up. To roll back, publish the old file again under a higher number. */
    val version: Int,
    /** File name on the phone and on Hugging Face, e.g. "hisaabsaathi-qwen3-ft-v2-q8.litertlm". */
    val file: String,
    /** https://huggingface.co/AcxelinLabs/hisaabsaathi-model/resolve/<40-hex commit>/<file> */
    val url: String,
    val bytes: Long,
    /** Lower-case hex SHA-256 of the file. */
    val sha256: String,
    /** The oldest app versionCode that can use this model (its answers' format). */
    val minAppVersion: Int = 0,
)

/** `models.json` on Hugging Face: what is current. Signed with the team's model key (`models.json.sig`). */
@Serializable
data class ModelManifest(val format: Int = 1, val models: List<ModelRelease> = emptyList())

/**
 * Updating the on-phone model without a new app: the phone fetches the signed manifest, and if it
 * names a newer version it can use, downloads that next to the old one (Wi-Fi only), checks its
 * size and SHA-256, tries it, switches, and deletes the old one. See docs/models/updating-the-model.md.
 */
object ModelUpdates {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parse(text: String): ModelManifest? = runCatching { json.decodeFromString(ModelManifest.serializer(), text) }.getOrNull()
        ?.takeIf { it.format == 1 }

    fun encodeManifest(m: ModelManifest): String = json.encodeToString(ModelManifest.serializer(), m)
    fun encode(r: ModelRelease): String = json.encodeToString(ModelRelease.serializer(), r)
    fun decodeRelease(text: String): ModelRelease? = runCatching { json.decodeFromString(ModelRelease.serializer(), text) }.getOrNull()

    /**
     * True when [signatureBase64] is the team's ECDSA P-256 / SHA-256 signature (DER) of exactly
     * [manifest]'s bytes, for [publicKeyBase64] (X.509 SubjectPublicKeyInfo, base64). Anything
     * malformed is false: an unsigned or altered manifest is never used.
     */
    fun verify(manifest: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean = runCatching {
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.trim())))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(manifest)
            verify(Base64.getDecoder().decode(signatureBase64.trim()))
        }
    }.getOrDefault(false)

    private val safeName = Regex("""^[A-Za-z0-9][A-Za-z0-9._-]{0,120}\.litertlm$""")
    private val pinnedUrl = Regex("""^https://huggingface\.co/AcxelinLabs/hisaabsaathi-model/resolve/[0-9a-f]{40}/([A-Za-z0-9._-]+)$""")
    private val hex64 = Regex("^[0-9a-f]{64}$")

    /** A release the phone may download: a plain file name, pinned to a commit of the team's repo, a sane size and hash. */
    fun acceptable(r: ModelRelease): Boolean =
        safeName.matches(r.file) && pinnedUrl.matchEntire(r.url)?.groupValues?.get(1) == r.file &&
            r.bytes in 1_000_000L..3_000_000_000L && hex64.matches(r.sha256) && r.version > 0

    /** The newest acceptable release of [id] this app version can use, if newer than [current]. */
    fun newer(manifest: ModelManifest, id: String, appVersion: Int, current: Int): ModelRelease? =
        manifest.models.filter { it.id == id && it.minAppVersion <= appVersion && acceptable(it) }
            .maxByOrNull { it.version }?.takeIf { it.version > current }

    /** Lower-case hex SHA-256 of a file, read in pieces (models are hundreds of MB). */
    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1 shl 16).use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Files in the model folder that should go: everything except the model in use, the one being
     * downloaded (and their ".part"), and [keepAlso] (the record of which model is current).
     */
    fun leftovers(names: Collection<String>, current: String, pending: String?, keepAlso: Set<String>): List<String> {
        val keep = setOfNotNull(current, "$current.part", pending, pending?.let { "$it.part" }) + keepAlso
        return names.filter { it !in keep }
    }
}
