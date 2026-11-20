package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/** The team's upload service (server/ in the repository), as the phone sees it. */
interface ShareServer {
    /** A new, anonymous install: an id and the token that later uploads and deletes use. */
    fun register(appVersion: String): ShareClient.Install

    /** Sends one batch ([Improve.batch]). Returns once the service has checked it arrived whole. */
    fun upload(token: String, zip: ByteArray, count: Int)

    /** Deletes everything this install sent. */
    fun deleteInstall(installId: String, token: String)
}

/**
 * The upload protocol (version 1), over HTTPS only:
 * 1. `POST /v1/installs` → `{"install_id", "token"}`;
 * 2. `POST /v1/batches` with the token, the zip's size and SHA-256 → `{"batch_id", "upload_url", "headers"}`;
 * 3. `PUT upload_url` with the zip: a short-lived link straight to the storage (S3, Azure Blob, or
 *    the service itself), so the service never holds large files;
 * 4. `POST /v1/batches/{batch_id}/complete`: the service checks size and hash;
 * - `DELETE /v1/installs/{install_id}` removes everything the install sent.
 *
 * Plain FTP is not used: it sends files and passwords unencrypted.
 */
class ShareClient(baseUrl: String, private val timeoutMs: Int = 30_000) : ShareServer {
    data class Install(val installId: String, val token: String)

    /** The service refused (4xx): retrying the same request will not help. 401 means the token is unknown. */
    class Refused(val code: Int, message: String) : IOException(message)

    private val base = baseUrl.trimEnd('/')

    init {
        require(safe(base)) { "upload service must use https" }
    }

    override fun register(appVersion: String): Install {
        val o = call("POST", "$base/v1/installs", null, """{"app_version":${JsonPrimitive(appVersion)}}""")
        return Install(o.str("install_id"), o.str("token"))
    }

    override fun upload(token: String, zip: ByteArray, count: Int) {
        val sha = MessageDigest.getInstance("SHA-256").digest(zip).joinToString("") { "%02x".format(it) }
        val ask = call("POST", "$base/v1/batches", token, """{"bytes":${zip.size},"sha256":"$sha","count":$count}""")
        val url = ask.str("upload_url")
        require(safe(url)) { "upload link must use https" }
        val headers = (ask["headers"] as? JsonObject)?.mapValues { (it.value as JsonPrimitive).content }.orEmpty()
        val put = open("PUT", url)
        try {
            headers.forEach { (k, v) -> put.setRequestProperty(k, v) }
            put.doOutput = true
            put.setFixedLengthStreamingMode(zip.size)
            put.outputStream.use { it.write(zip) }
            val code = put.responseCode
            if (code !in 200..299) fail(code, "upload: ${put.errorStream?.use { it.readBytes().decodeToString().take(200) }}")
        } finally {
            put.disconnect()
        }
        call("POST", "$base/v1/batches/${ask.str("batch_id")}/complete", token, "{}")
    }

    override fun deleteInstall(installId: String, token: String) {
        call("DELETE", "$base/v1/installs/$installId", token, null)
    }

    private fun open(method: String, url: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = false
        }

    private fun call(method: String, url: String, token: String?, body: String?): JsonObject {
        val c = open(method, url)
        try {
            c.setRequestProperty("Accept", "application/json")
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                val bytes = body.toByteArray()
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            val code = c.responseCode
            if (code !in 200..299) fail(code, "$method ${URI(url).path}: ${c.errorStream?.use { it.readBytes().decodeToString().take(200) }}")
            val text = c.inputStream.use { it.readBytes().decodeToString() }
            return if (text.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(text).jsonObject
        } finally {
            c.disconnect()
        }
    }

    private fun fail(code: Int, message: String): Nothing =
        if (code in 400..499) throw Refused(code, message) else throw IOException("$code $message")

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() } ?: throw IOException("reply has no $key")

    companion object {
        /** HTTPS, or plain HTTP only to this computer or the Android emulator's host (for tests). */
        fun safe(url: String): Boolean = runCatching {
            val u = URI(url)
            u.scheme == "https" || (u.scheme == "http" && u.host in setOf("localhost", "127.0.0.1", "10.0.2.2"))
        }.getOrDefault(false)
    }
}
