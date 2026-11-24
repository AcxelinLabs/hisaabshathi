package com.acxelin.hisaabsaathi.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.acxelin.hisaabsaathi.core.Entry
import com.acxelin.hisaabsaathi.core.EntryType
import com.acxelin.hisaabsaathi.core.ExpenseCategory
import com.acxelin.hisaabsaathi.core.HashChain
import com.acxelin.hisaabsaathi.core.Ledger
import com.acxelin.hisaabsaathi.core.Strings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Builds files the worker can choose to share: a PDF report and a tamper-evident evidence
 * pack. Files are written to the app's private cache and leave the phone only through the
 * Android share sheet, on the worker's tap.
 */
class Exporter(private val context: Context) {
    private val dir get() = File(context.cacheDir, "exports").apply { mkdirs() }
    private val dateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    /** [owner]: the worker's name, district, state and PIN from setup ("" if not given). */
    fun pdf(title: String, entriesNewestFirst: List<Entry>, s: Strings, chainNote: String? = null, owner: String = ""): File {
        val doc = PdfDocument()
        val width = 595; val height = 842; val margin = 40f
        val body = Paint().apply { textSize = 11f; isAntiAlias = true }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val head = Paint(bold).apply { textSize = 18f }
        val small = Paint(body).apply { textSize = 8f; color = 0xFF555555.toInt() }

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
        var y = margin + 10
        fun line(text: String, paint: Paint = body, x: Float = margin, gap: Float = 16f) {
            if (y > height - margin) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, ++pageNo).create())
                y = margin + 10
            }
            page.canvas.drawText(text, x, y, paint)
            y += gap
        }

        val t = Ledger.totals(entriesNewestFirst)
        line("${s.appName} — $title", head, gap = 24f)
        if (owner.isNotBlank()) line(owner, bold, gap = 18f)
        line(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")), small, gap = 20f)
        line("${s.earned}: ${s.rupees(t.earnedPaise)}    ${s.advance}: ${s.rupees(t.advancePaise)}    ${s.received}: ${s.rupees(t.paidPaise)}", bold)
        line("${s.spent}: ${s.rupees(t.expensePaise)}    ${s.due}: ${s.rupees(t.duePaise)}    ${s.daysWorked}: ${t.daysWorked}", bold, gap = 24f)
        chainNote?.let { line(it, bold, gap = 22f) }

        for (e in entriesNewestFirst.sortedBy { it.epochDay }) {
            val date = LocalDate.ofEpochDay(e.epochDay).format(dateFmt)
            val label = when (e.type) {
                EntryType.EXPENSE -> "${s.typeName(e.type)} (${s.categoryName(e.category ?: ExpenseCategory.OTHER)})" + s.describe(e.detail).let { if (it.isEmpty()) "" else ": $it" }
                else -> s.typeName(e.type)
            }
            val detail = if (e.type == EntryType.WORK) s.workDetail(e.hours, e.pieces, e.ratePaise) else ""
            line("$date   $label   ${s.rupees(e.amountPaise)}   $detail")
            if (chainNote != null) line("    #${e.hash.take(16)}  \"${e.note.take(70)}\"", small, gap = 13f)
        }
        doc.finishPage(page)
        val file = File(dir, "hisaab-${System.currentTimeMillis()}.pdf")
        file.outputStream().use(doc::writeTo)
        doc.close()
        return file
    }

    /** Machine-readable record with every hash, so anyone can re-check the chain. */
    fun evidenceJson(entriesOldestFirst: List<Entry>, papers: List<Pair<File, String>> = emptyList()): File {
        val broken = HashChain.firstBroken(entriesOldestFirst)
        val root = JSONObject()
            .put("app", "HisaabSaathi")
            .put("generatedAt", LocalDateTime.now().toString())
            .put("hashAlgorithm", "SHA-256; hash = sha256(prevHash + '|' + canonical); canonical = epochDay|createdAtMs|type|amountPaise|hours|pieces|ratePaise|category|source|note, then |detail when a line has one")
            .put("genesis", HashChain.GENESIS)
            .put("chainIntact", broken < 0)
            .put("entries", JSONArray().apply {
                for (e in entriesOldestFirst) put(
                    JSONObject()
                        .put("date", LocalDate.ofEpochDay(e.epochDay).toString())
                        .put("epochDay", e.epochDay).put("createdAtMs", e.createdAtMs)
                        .put("type", e.type.name).put("amountPaise", e.amountPaise)
                        .put("hours", e.hours ?: JSONObject.NULL).put("pieces", e.pieces ?: JSONObject.NULL)
                        .put("ratePaise", e.ratePaise ?: JSONObject.NULL)
                        .put("category", e.category?.name ?: JSONObject.NULL)
                        .put("source", e.source).put("note", e.note).put("detail", e.detail)
                        .put("prevHash", e.prevHash).put("hash", e.hash)
                        // The photo sent with this file, and its fingerprint so a changed photo shows.
                        .put("photo", e.attachment?.let(::File)?.takeIf { it.exists() }?.let { photoInfo(it) } ?: JSONObject.NULL),
                )
            })
            .put("papers", JSONArray().apply {
                for ((f, kind) in papers) if (f.exists()) put(photoInfo(f).put("kind", kind))
            })
        return File(dir, "hisaab-evidence-${System.currentTimeMillis()}.json").apply { writeText(root.toString(2)) }
    }

    private fun photoInfo(f: File): JSONObject {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) } }
        return JSONObject().put("file", f.name).put("sha256", digest.digest().joinToString("") { "%02x".format(it) })
    }

    companion object {
        /**
         * Reads a saved copy (the evidence file). Null if it is not one. The hashes are not
         * trusted: the lines are re-sealed when they join this phone's record.
         */
        fun readBackup(text: String): List<Entry>? = runCatching {
            val root = JSONObject(text)
            if (root.optString("app") != "HisaabSaathi") return null
            val arr = root.getJSONArray("entries")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                fun optLong(k: String) = if (o.isNull(k)) null else o.getLong(k)
                Entry(
                    epochDay = o.getLong("epochDay"), createdAtMs = o.getLong("createdAtMs"),
                    type = EntryType.valueOf(o.getString("type")), amountPaise = o.getLong("amountPaise").coerceIn(0, com.acxelin.hisaabsaathi.core.Money.MAX_PAISE),
                    hours = if (o.isNull("hours")) null else o.getDouble("hours"),
                    pieces = optLong("pieces")?.toInt(), ratePaise = optLong("ratePaise"),
                    category = if (o.isNull("category")) null else ExpenseCategory.valueOf(o.getString("category")),
                    note = o.optString("note"), source = o.optString("source", "backup"), detail = o.optString("detail"),
                )
            }
        }.getOrNull()
    }

    fun share(files: List<File>, chooserTitle: String, mime: String = "application/pdf") {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
