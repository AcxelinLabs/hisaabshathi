package com.acxelin.hisaabsaathi

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.models.Pack
import com.acxelin.hisaabsaathi.models.PackFile
import com.acxelin.hisaabsaathi.models.PackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/** The download code against a real local web server: resume after a cut, wrong address, partial reply. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class PackDownloadTest {
    private val data = Random(1).nextBytes(300_000)
    private var cutAfter = Int.MAX_VALUE // the server drops the connection after this many bytes
    /** A tiny HTTP server: /model.bin with Range support, anything else 404. */
    private val server = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
    init {
        Thread {
            while (!server.isClosed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    sock.use { c ->
                        val reader = c.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        val path = reader.readLine().split(' ')[1]
                        var from = 0
                        while (true) {
                            val h = reader.readLine() ?: break
                            if (h.isEmpty()) break
                            if (h.lowercase().startsWith("range:")) from = h.substringAfter("bytes=").substringBefore('-').trim().toInt()
                        }
                        val out = c.getOutputStream()
                        if (path != "/model.bin") {
                            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush(); return@use
                        }
                        val body = data.copyOfRange(from, data.size)
                        val status = if (from > 0) "206 Partial Content" else "200 OK"
                        val range = if (from > 0) "Content-Range: bytes $from-${data.size - 1}/${data.size}\r\n" else ""
                        out.write("HTTP/1.1 $status\r\nContent-Length: ${body.size}\r\n${range}Connection: close\r\n\r\n".toByteArray())
                        out.write(body, 0, minOf(body.size, cutAfter)); out.flush()
                    }
                }
            }
        }.apply { isDaemon = true; start() }
    }
    private val base = "http://127.0.0.1:${server.localPort}"
    private val context = ApplicationProvider.getApplicationContext<TestApp>()
    private val packs = PackManager(context, CoroutineScope(Dispatchers.IO))

    @After fun stop() = server.close()

    private fun pack(path: String) = Pack("test-$path", "test", "test", listOf(PackFile("$base/$path", "test/$path", data.size.toLong())))

    @Test fun cutDownloadResumesWhereItStopped() = runBlocking {
        val p = pack("model.bin")
        File(context.filesDir, "models/test/model.bin").delete(); File(context.filesDir, "models/test/model.bin.part").delete()
        cutAfter = 120_000
        try { packs.downloadNow(p); fail("should stop when the connection is cut") } catch (e: java.io.IOException) { /* retried later */ }
        val part = File(context.filesDir, "models/test/model.bin.part")
        assertTrue("partial file kept: ${part.length()}", part.length() in 1 until data.size)
        cutAfter = Int.MAX_VALUE
        packs.downloadNow(p)
        assertTrue(packs.isReady(p))
        assertArrayEquals(data, File(context.filesDir, "models/test/model.bin").readBytes())
    }

    @Test fun wrongAddressFailsForGoodInsteadOfRetrying() = runBlocking {
        try { packs.downloadNow(pack("missing.bin")); fail("404 must fail") } catch (e: java.io.IOException) {
            fail("404 is not a network problem: $e")
        } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("404")) }
    }
}
