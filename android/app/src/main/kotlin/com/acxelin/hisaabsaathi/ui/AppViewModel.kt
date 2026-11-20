package com.acxelin.hisaabsaathi.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acxelin.hisaabsaathi.HisaabApp
import com.acxelin.hisaabsaathi.core.Confirm
import com.acxelin.hisaabsaathi.BuildConfig
import com.acxelin.hisaabsaathi.core.Corrections
import com.acxelin.hisaabsaathi.core.Improve
import com.acxelin.hisaabsaathi.core.Sample
import com.acxelin.hisaabsaathi.core.VoiceSamples
import com.acxelin.hisaabsaathi.work.Sharing
import com.acxelin.hisaabsaathi.core.DraftEntry
import com.acxelin.hisaabsaathi.core.Entry
import com.acxelin.hisaabsaathi.core.EntryType
import com.acxelin.hisaabsaathi.core.ExpenseCategory
import com.acxelin.hisaabsaathi.core.Money
import com.acxelin.hisaabsaathi.core.HashChain
import com.acxelin.hisaabsaathi.core.Lang
import com.acxelin.hisaabsaathi.core.Ledger
import com.acxelin.hisaabsaathi.core.PhotoKind
import com.acxelin.hisaabsaathi.core.Query
import com.acxelin.hisaabsaathi.core.QueryPeriod
import com.acxelin.hisaabsaathi.core.QueryTopic
import com.acxelin.hisaabsaathi.core.ReceiptReader
import com.acxelin.hisaabsaathi.core.Extraction
import com.acxelin.hisaabsaathi.core.Understand
import com.acxelin.hisaabsaathi.core.Understood
import com.acxelin.hisaabsaathi.core.Slm
import com.acxelin.hisaabsaathi.core.Strings
import com.acxelin.hisaabsaathi.core.Usage
import com.acxelin.hisaabsaathi.core.Wages
import com.acxelin.hisaabsaathi.data.Settings
import com.acxelin.hisaabsaathi.models.Catalog
import com.acxelin.hisaabsaathi.models.Pack
import com.acxelin.hisaabsaathi.models.PackState
import com.acxelin.hisaabsaathi.speech.Heard
import com.acxelin.hisaabsaathi.work.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Capture {
    data object Idle : Capture
    data object Listening : Capture
    data object Thinking : Capture

    /** Waiting for the worker's yes or no. Nothing is saved before that. */
    data class Review(
        val transcript: String,
        val drafts: List<DraftEntry>,
        val lowConfidence: Boolean,
        val source: String,
        val attachment: String? = null,
        /** What the app first understood, before any fix by the worker (for the correction log). */
        val understood: List<DraftEntry> = drafts,
        /** Lines whose amount box holds something that is not an amount; saving waits until fixed. */
        val invalid: Set<Int> = emptySet(),
        /** What the worker said, kept until they say yes or no (only if they agreed to share). */
        val said: Said? = null,
    ) : Capture {
        /** Something worth saving, and no amount box left with a typing slip. */
        val canSave: Boolean get() = invalid.isEmpty() && drafts.any { it.amountPaise > 0 || it.hours != null || it.pieces != null }
    }

    /** A spoken answer or a problem, also shown as text on the home screen. */
    /** [asked] is what the worker said, shown above the answer so they can see what was understood. */
    data class Message(val text: String, val asked: String? = null) : Capture
}

/** One thing the worker said or typed, and how the app heard it; becomes a [com.acxelin.hisaabsaathi.core.Sample]. */
class Said(val text: String, val source: String, val audio: FloatArray?, val engine: String, val atMs: Long = System.currentTimeMillis())

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HisaabApp

    /** null until the saved settings have been read, so onboarding does not flash. */
    val settings: StateFlow<Settings?> = app.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val entries: StateFlow<List<Entry>> = app.ledger.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val packStates = app.packs.states
    val capture = MutableStateFlow<Capture>(Capture.Idle)
    val level = MutableStateFlow(0f)
    /** Exchanges waiting to be sent (only when the worker agreed to share). */
    val waitingCount: StateFlow<Int> = app.samples.count.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    /** Whether this build can send at all (has an upload service). */
    val canSend: Boolean get() = app.canShare
    val wagePack get() = app.wagePack

    private val lang: Lang get() = settings.value?.lang ?: Lang.HI

    init { app.profile } // start reading the saved details now, so reports have them
    private val strings: Strings get() = Strings(lang)

    val slmPack: Pack get() = Catalog.slmHisaab
    fun importModel(uri: android.net.Uri) = app.packs.importFrom(app, Catalog.slmHisaab, uri)

    // ---- voice

    /**
     * The one piece of voice work running (listening, understanding, or the yes/no answer).
     * A second tap never starts a second recording on top of it.
     */
    private var busy: kotlinx.coroutines.Job? = null
    private val isBusy get() = busy?.isActive == true

    fun micTapped() {
        when (capture.value) {
            Capture.Listening -> { app.stt.stop(); app.systemStt.stop() }
            Capture.Thinking -> cancelThinking()
            else -> if (!isBusy) listenForEntry()
        }
    }

    /** "Understanding…" can take a while with the language model: the worker may stop it. */
    fun cancelThinking() {
        busy?.cancel(); busy = null
        app.speaker.stop()
        capture.value = Capture.Idle
    }

    private fun listenForEntry() {
        busy = viewModelScope.launch(guard) {
            val text = listenOnce() ?: return@launch
            understand(text, "voice")
        }
    }

    /** The recording and engine of the last thing heard, for [Said]. */
    private var lastAudio: FloatArray? = null
    private var lastEngine = ""

    /** Technical note about the last problem, shown in Settings for testers. */
    val lastProblem = MutableStateFlow(lastExitProblem().ifEmpty { app.slm.problem })

    private fun note(what: String) {
        lastProblem.value = "${java.time.LocalTime.now().withNano(0)} $what"
        android.util.Log.w("HisaabSaathi", what)
    }

    /** An unexpected error while answering shows a message instead of closing the app. */
    private val guard = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        note("error: ${e.javaClass.simpleName}: ${e.message}")
        capture.value = Capture.Message(strings.voiceFailed)
    }

    /**
     * Why the app last closed, if it crashed or was killed for memory (Android 11+). Read
     * once per crash so testers can report it without a computer.
     */
    private fun lastExitProblem(): String {
        if (android.os.Build.VERSION.SDK_INT < 30) return ""
        return runCatching {
            val am = app.getSystemService(android.app.ActivityManager::class.java)
            val exit = am.getHistoricalProcessExitReasons(app.packageName, 0, 5).firstOrNull() ?: return ""
            val prefs = app.getSharedPreferences("diagnostics", android.content.Context.MODE_PRIVATE)
            if (prefs.getLong("seen", 0) >= exit.timestamp) return prefs.getString("last", "").orEmpty()
            val why = when (exit.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH -> "app crashed (Java)"
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "app crashed (native)"
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "closed by Android: low memory"
                android.app.ApplicationExitInfo.REASON_ANR -> "app froze (not responding)"
                android.app.ApplicationExitInfo.REASON_SIGNALED -> "app was killed"
                else -> return ""
            }
            // Name the native libraries in the crash report: shows which part failed.
            val libs = runCatching {
                exit.traceInputStream?.use { String(it.readBytes().take(400_000).toByteArray(), Charsets.ISO_8859_1) }
                    ?.let { Regex("lib[A-Za-z0-9_\\-]+\\.so").findAll(it).map { m -> m.value }.distinct().take(4).joinToString(", ") }
            }.getOrNull().orEmpty()
            val text = "${java.time.Instant.ofEpochMilli(exit.timestamp)} [${exit.processName}] $why. ${exit.description.orEmpty()} $libs".trim()
            prefs.edit().putLong("seen", exit.timestamp).putString("last", text).apply()
            text
        }.getOrDefault("")
    }

    /** Returns null (after showing a message that says what went wrong) when nothing usable was heard. */
    private suspend fun listenOnce(): String? {
        val l = lang
        app.speaker.stop()
        val usePack = app.stt.available(l)
        if (!usePack && !app.systemStt.available()) { voicePackMessage(); return null }
        app.slm.unload() // keep peak memory low on 3 GB phones
        lastAudio = null; lastEngine = if (usePack) "pack" else "phone"
        capture.value = Capture.Listening
        val heard = try {
            if (usePack) app.stt.listen(l, onLevel = { level.value = it }, onRecorded = { capture.value = Capture.Thinking })
            else app.systemStt.listen(l, onRecorded = { capture.value = Capture.Thinking })
        } catch (e: Exception) {
            Heard.Failed(Heard.Kind.ENGINE, e.message ?: e.javaClass.simpleName)
        } finally {
            level.value = 0f
        }
        return when (heard) {
            is Heard.Text -> { lastAudio = heard.audio; heard.text }
            Heard.Silence -> { say(strings.heardNothing); null }
            is Heard.Failed -> {
                note("${if (usePack) "voice pack" else "phone recogniser"}: ${heard.detail}")
                when {
                    heard.kind == Heard.Kind.MIC -> say(strings.micFailed)
                    // The phone's own recogniser has no offline model for this language: the voice pack is the answer.
                    !usePack -> voicePackMessage()
                    else -> say(strings.voiceFailed)
                }
                null
            }
        }
    }

    /** Says whether the voice pack is downloading (with progress) or still needs downloading. */
    private fun voicePackMessage() {
        when (val st = app.packs.states.value[Catalog.stt(lang).id]) {
            is PackState.Downloading -> say(strings.voicePackDownloading((st.fraction * 100).toInt()))
            else -> say(strings.noSpeechEngine)
        }
    }

    fun submitText(text: String) {
        if (isBusy) return
        busy = viewModelScope.launch(guard) { understand(text, "typed") }
    }

    private suspend fun understand(text: String, source: String) {
        val said = Said(text, source, lastAudio.takeIf { source == "voice" }, if (source == "voice") lastEngine else "")
        lastAudio = null
        if (text.isBlank()) return say(strings.notUnderstood)
        capture.value = Capture.Thinking
        when (val u = Understand.of(text)) {
            is Understood.Ask -> { answer(u.query, text); replied(said, Improve.understood(u)); count(Usage.Event.QUESTIONS) }
            is Understood.Talk -> { say(strings.chat(u.chat, todayText()), asked = text); replied(said, Improve.understood(u)) }
            is Understood.Entries -> {
                var result = u.extraction
                // The rules were unsure: let the language model have a look, if it is installed.
                if (result.lowConfidence && app.slm.available()) {
                    app.stt.unload()
                    result = Slm.choose(result, Slm.prompt(text)?.let { app.slm.generate(it) })
                }
                review(Capture.Review(text, result.entries, result.lowConfidence, source, said = said))
            }
            is Understood.Unknown -> {
                if (app.slm.available()) {
                    app.stt.unload()
                    val found = Slm.choose(Extraction(text), Slm.prompt(text)?.let { app.slm.generate(it) })
                    if (found.entries.isNotEmpty()) return review(Capture.Review(text, found.entries, true, source, said = said))
                    // A ledger question in words the rules do not know ("did I get all my wages this
                    // month?"). The totals are the app's. Only the model's period is trusted: in tests it
                    // named the period right 60 times in 64 but the topic often wrong, so the worker
                    // hears the whole summary for that period rather than one possibly wrong total.
                    Slm.parseQuestion(app.slm.generate(Slm.questionPrompt(text)))?.let {
                        answer(Query(it.period, QueryTopic.ALL), text)
                        count(Usage.Event.QUESTIONS)
                        return replied(said, "MODEL ASK ${it.period.name} ALL")
                    }
                }
                // Never stay silent: say what the app can do and how to phrase it.
                say(strings.chatFallback, asked = text)
                replied(said, Improve.understood(u), Improve.Outcome.NOT_UNDERSTOOD)
                count(Usage.Event.NOT_UNDERSTOOD)
            }
        }
    }

    /** A question or small talk was answered: keep it with what the app said (if the worker agreed to share). */
    private fun replied(said: Said, understood: String, outcome: String = Improve.Outcome.ANSWERED) =
        keep(said, understood, (capture.value as? Capture.Message)?.text.orEmpty(), outcome)

    /**
     * Keeps one exchange to send later, only while the worker's "share" choice is on. Never
     * slows or breaks the app: the clip is written in the background and errors are only noted.
     */
    private fun keep(said: Said?, understood: String, reply: String, outcome: String, kept: String? = null) {
        val s = settings.value ?: return
        if (said == null || s.share != true || !Improve.counts(said.source)) return
        // A phone, Aadhaar or bank number, or a UPI id: never kept, voice included.
        if (Improve.sensitive(said.text)) return
        val l = lang
        app.scope.launch {
            runCatching {
                val pcm = said.audio?.takeIf { it.isNotEmpty() }?.let(VoiceSamples::pcm16)
                val sample = Sample(
                    id = Improve.newId(), createdAtMs = said.atMs, lang = l.code, source = said.source, heard = said.text,
                    understood = understood, reply = reply, outcome = outcome, kept = kept, region = s.region,
                    appVersion = BuildConfig.VERSION_NAME, engine = said.engine,
                    audioSeconds = (pcm?.size ?: 0) / VoiceSamples.SAMPLE_RATE.toDouble(),
                    quality = pcm?.let { VoiceSamples.quality(it).name.lowercase() }.orEmpty(), hasAudio = pcm != null,
                    consent = Improve.CONSENT_VERSION,
                )
                app.samples.add(sample, pcm?.let { VoiceSamples.wav(it) })
            }.onFailure { note("improve: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    /** Weekly counts, only for workers whose yes covers them (the store checks). Never fails the app. */
    private fun count(vararg events: String) {
        app.scope.launch { runCatching { app.settings.countUsage(LocalDate.now(), *events) } }
    }

    /** How a confirmation card ended. */
    private fun reviewed(r: Capture.Review, outcome: String) {
        val saved = outcome == Improve.Outcome.SAVED || outcome == Improve.Outcome.CORRECTED
        if (saved) {
            val how = when {
                r.source.startsWith("photo") || r.source == "scan" -> Usage.Event.FROM_PHOTO
                r.source == "voice" -> Usage.Event.BY_VOICE
                else -> Usage.Event.TYPED
            }
            val lines = r.drafts.count { it.amountPaise > 0 || it.hours != null || it.pieces != null }.coerceAtLeast(1)
            count(*(List(lines) { Usage.Event.SAVED } + List(lines) { how } +
                listOfNotNull(Usage.Event.FIXED.takeIf { outcome == Improve.Outcome.CORRECTED })).toTypedArray())
        } else if (outcome == Improve.Outcome.REJECTED) count(Usage.Event.SAID_WRONG)
        recordReview(r, outcome)
    }

    private fun recordReview(r: Capture.Review, outcome: String) =
        keep(r.said, "ENTRY " + Corrections.describe(r.understood), strings.readback(r.understood), outcome,
            kept = if (outcome == Improve.Outcome.SAVED || outcome == Improve.Outcome.CORRECTED) Corrections.describe(r.drafts) else null)

    private fun todayText(): String =
        LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", com.acxelin.hisaabsaathi.speech.locale(lang)))

    private fun review(r: Capture.Review) {
        capture.value = r
        app.speaker.speak(strings.readback(r.drafts), lang)
    }

    fun replay() {
        (capture.value as? Capture.Review)?.let { app.speaker.speak(strings.readback(it.drafts), lang) }
    }

    private fun answer(q: Query, asked: String? = null) {
        if (q.topic == QueryTopic.MIN_WAGE) {
            val rate = Wages.floor(Wages.forRegion(wagePack, settings.value?.region.orEmpty()))
            return say(rate?.let { strings.minWageSpoken(it) + " " + strings.wageDataNote } ?: strings.notUnderstood, asked)
        }
        say(strings.answer(q, totals(q.period)), asked)
    }

    fun totals(period: QueryPeriod, list: List<Entry> = entries.value) =
        Ledger.totals(inPeriod(period, list))

    fun inPeriod(period: QueryPeriod, list: List<Entry> = entries.value): List<Entry> {
        val range = Ledger.range(period, LocalDate.now())
        return list.filter { it.epochDay in range }
    }

    private fun say(text: String, asked: String? = null) {
        capture.value = Capture.Message(text, asked)
        app.speaker.speak(text, lang)
    }

    fun speakSummary(period: QueryPeriod) = say(strings.summary(period, totals(period)))

    // ---- review

    fun updateDraft(index: Int, draft: DraftEntry) {
        val r = capture.value as? Capture.Review ?: return
        if (index !in r.drafts.indices) return
        capture.value = r.copy(drafts = r.drafts.toMutableList().also { it[index] = draft })
    }

    /** The worker typed in an amount box: an empty box is 0, a slip ("abc", "1e9") blocks saving. */
    fun updateAmount(index: Int, typed: String) {
        val r = capture.value as? Capture.Review ?: return
        if (index !in r.drafts.indices) return
        val paise = com.acxelin.hisaabsaathi.core.Money.parseTyped(typed)
        capture.value = if (paise == null) r.copy(invalid = r.invalid + index)
        else r.copy(drafts = r.drafts.toMutableList().also { it[index] = it[index].copy(amountPaise = paise) }, invalid = r.invalid - index)
    }

    /** Removes one wrongly heard line from what is about to be saved. */
    fun removeDraft(index: Int) {
        val r = capture.value as? Capture.Review ?: return
        if (r.drafts.size <= 1 || index !in r.drafts.indices) return
        capture.value = r.copy(
            drafts = r.drafts.filterIndexed { i, _ -> i != index },
            invalid = r.invalid.filter { it != index }.map { if (it > index) it - 1 else it }.toSet(),
        )
    }

    /**
     * Saves once. The screen leaves the review state before anything slow happens, so a
     * double tap cannot save the same lines twice.
     */
    fun confirmSave() {
        val r = capture.value as? Capture.Review ?: return
        if (!r.canSave) return
        capture.value = Capture.Thinking
        viewModelScope.launch(guard) {
            val today = LocalDate.now()
            val now = System.currentTimeMillis()
            try {
                app.ledger.add(r.drafts.filter { it.amountPaise > 0 || it.hours != null || it.pieces != null }
                    .map { Ledger.fromDraft(it, today, now, r.transcript, r.source, r.attachment) })
            } catch (e: Exception) {
                note("save: ${e.javaClass.simpleName}: ${e.message}")
                capture.value = r // keep what the worker confirmed on screen; nothing is lost
                app.speaker.speak(strings.voiceFailed, lang)
                return@launch
            }
            reviewed(r, if (r.understood == r.drafts) Improve.Outcome.SAVED else Improve.Outcome.CORRECTED)
            say(strings.saved)
        }
    }

    private fun rejected(r: Capture.Review) = reviewed(r, Improve.Outcome.REJECTED)

    /**
     * "Say yes or no": yes saves, no starts a new recording. If nothing usable is heard the
     * worker's lines come back on screen (the problem is still spoken), never lost.
     */
    fun confirmByVoice() {
        val r = capture.value as? Capture.Review ?: return
        if (isBusy) return
        busy = viewModelScope.launch(guard) {
            val heard = try { listenOnce() } catch (e: Exception) { note("yes/no: ${e.javaClass.simpleName}"); null }
            if (heard == null) { capture.value = r; return@launch }
            val answer = Confirm.parse(heard)
            keep(Said(heard, "voice", lastAudio, lastEngine), "CONFIRM ${when (answer) { true -> "YES"; false -> "NO"; null -> "NONE" }}", "", Improve.Outcome.CONFIRM)
            lastAudio = null
            when (answer) {
                true -> { capture.value = r; confirmSave() }
                false -> { rejected(r); dropPhoto(r); capture.value = Capture.Idle; busy = null; listenForEntry() }
                null -> review(r)
            }
        }
    }

    fun retry() {
        val r = capture.value as? Capture.Review
        r?.let(::rejected)
        val wasVoice = r?.source == "voice"
        r?.let { capture.value = it.copy(said = null) } // already kept as rejected
        dismiss()
        if (wasVoice && !isBusy) listenForEntry()
    }

    fun dismiss() {
        app.speaker.stop()
        (capture.value as? Capture.Review)?.let { reviewed(it, Improve.Outcome.DISMISSED); dropPhoto(it) }
        capture.value = Capture.Idle
    }

    // ---- photos: bills, slips, salary slips (become lines), bank statements and other papers (kept)

    /** One photo the worker took: of a ledger line ([entry]), or a paper kept on its own. */
    data class Photo(val path: String, val kind: PhotoKind, val takenAtMs: Long, val entry: Entry? = null)

    private val photoDir get() = java.io.File(app.filesDir, "attachments").apply { mkdirs() }
    private val papersChanged = MutableStateFlow(0)

    /** Every photo, newest first: those of saved lines and the papers. */
    val photos: StateFlow<List<Photo>> = kotlinx.coroutines.flow.combine(entries, papersChanged) { list, _ ->
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val ofLines = list.mapNotNull { e ->
                val p = e.attachment ?: return@mapNotNull null
                if (!java.io.File(p).exists()) return@mapNotNull null
                Photo(p, PhotoKind.fromSource(e.source) ?: PhotoKind.BILL, e.createdAtMs, e)
            }
            val papers = photoDir.listFiles().orEmpty().mapNotNull { f -> paperOf(f) }
            (ofLines + papers).sortedByDescending { it.takenAtMs }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun paperOf(f: java.io.File): Photo? {
        val m = Regex("""paper-([A-Z]+)-(\d+)\.jpg""").matchEntire(f.name) ?: return null
        val kind = PhotoKind.entries.firstOrNull { it.name == m.groupValues[1] } ?: PhotoKind.OTHER
        return Photo(f.path, kind, m.groupValues[2].toLong())
    }

    /**
     * A photo was taken of [kind]. A bank statement or other paper is kept as it is. A bill,
     * slip or salary slip becomes a line to confirm, with the amount read on the phone; if
     * it cannot be read, the worker types it. Nothing is saved before they confirm.
     */
    fun photoTaken(photo: Uri, path: String, kind: PhotoKind) = viewModelScope.launch(guard) {
        count(Usage.Event.PHOTOS)
        val type = kind.entryType
        if (type == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val f = java.io.File(path)
                f.renameTo(java.io.File(f.parentFile, "paper-${kind.name}-${System.currentTimeMillis()}.jpg"))
            }
            papersChanged.value++
            return@launch say(strings.photoKept)
        }
        capture.value = Capture.Thinking
        val text = app.readPhoto(photo)
        // Same limits as speech: a long run of digits on a bill must not become a ₹ crore line.
        val draft = ReceiptReader.read(text, kind)?.let { Extraction(text, listOf(it)).sane().entries.single() }
            ?.takeIf { it.amountPaise > 0 } ?: ReceiptReader.blank(type)
        val r = Capture.Review(text.lines().filter { it.isNotBlank() }.take(6).joinToString(" / "), listOf(draft), true, "photo:${kind.name}", path)
        if (draft.amountPaise > 0) review(r) else {
            capture.value = r
            app.speaker.speak(strings.photoNoAmount, lang)
        }
    }

    /** A photo whose line was not saved is not kept. */
    private fun dropPhoto(r: Capture.Review) {
        val p = r.attachment ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { java.io.File(p).delete() }
    }

    fun deletePaper(path: String) = viewModelScope.launch(guard) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Only papers here: a line's photo goes when the line is deleted.
            java.io.File(path).takeIf { it.parentFile == photoDir && it.name.startsWith("paper-") }?.delete()
        }
        papersChanged.value++
    }

    // ---- ledger, settings, export

    /** The line deleted last, kept for a few seconds so the worker can undo a wrong tap. */
    val lastDeleted = MutableStateFlow<Entry?>(null)

    fun delete(id: Long) = viewModelScope.launch(guard) {
        val e = entries.value.firstOrNull { it.id == id } ?: return@launch
        forgetDeleted()
        app.ledger.delete(id)
        count(Usage.Event.DELETED)
        lastDeleted.value = e
    }

    fun undoDelete() = viewModelScope.launch(guard) {
        val e = lastDeleted.value ?: return@launch
        lastDeleted.value = null
        app.ledger.restore(e)
    }

    /** The undo time is over: the line's photo goes too (unless another line uses it). */
    fun forgetDeleted() {
        val d = lastDeleted.value ?: return
        lastDeleted.value = null
        val p = d.attachment ?: return
        if (entries.value.none { it.attachment == p && it.id != d.id }) viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { java.io.File(p).delete() }
    }

    /**
     * Changes a saved line. Returns false (nothing changed) if the amount is a typing slip.
     * The line keeps its place in the record, and its note says what it was before, so an
     * exported record shows the edit.
     */
    fun editEntry(original: Entry, type: EntryType, amountText: String, epochDay: Long, category: ExpenseCategory?): Boolean {
        val paise = Money.parseTyped(amountText) ?: return false
        val day = minOf(epochDay, LocalDate.now().toEpochDay())
        val changed = original.copy(
            type = type, amountPaise = paise, epochDay = day,
            category = if (type == EntryType.EXPENSE) category ?: ExpenseCategory.OTHER else null,
            hours = original.hours.takeIf { type == EntryType.WORK },
            pieces = original.pieces.takeIf { type == EntryType.WORK },
            ratePaise = original.ratePaise.takeIf { type == EntryType.WORK },
        )
        if (changed == original) return true
        val was = "${original.type.name} ${Money.format(original.amountPaise)} ${LocalDate.ofEpochDay(original.epochDay)}"
        val note = "${original.note} · edited ${LocalDate.now()}, was $was".trim()
        viewModelScope.launch(guard) { app.ledger.replace(changed.copy(note = note)) }
        count(Usage.Event.EDITED)
        return true
    }
    fun eraseAll() = viewModelScope.launch(guard) {
        app.ledger.eraseAll(); app.corrections.clear(); app.samples.clear(); app.profile.clear()
        // Photos of bills, slips and papers too.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { photoDir.listFiles().orEmpty().forEach { it.delete() } }
        papersChanged.value++
    }

    // ---- help improve the app (only what the worker agreed to)

    /**
     * The worker's choice. Yes: from now on each exchange is kept and sent, with the fixes the
     * old local log held. No: nothing is kept, and anything waiting is deleted.
     */
    fun setShare(on: Boolean) = viewModelScope.launch(guard) {
        app.settings.setShare(on)
        val old = runCatching { app.corrections.all() }.getOrDefault(emptyList())
        if (on) {
            val region = settings.value?.region.orEmpty()
            old.forEach { app.samples.add(Improve.fromCorrection(it, region, BuildConfig.VERSION_NAME), null) }
            Sharing.schedule(app)
        } else {
            app.samples.clear()
            Sharing.cancel(app)
        }
        app.corrections.clear()
    }

    fun sendNow() = Sharing.now(app)

    /** Deletes what is waiting here, and asks the team's service to delete what this phone sent. */
    fun deleteShared() = viewModelScope.launch(guard) {
        app.samples.clear()
        app.settings.requestDelete()
        Sharing.now(app)
    }

    /** Reads a text aloud in the worker's language (the setup question). */
    fun readAloud(text: String) = app.speaker.speak(text, lang)

    /** The worker's own choice of language. Nothing else (location, state) ever changes it. */
    fun setLang(l: Lang) = viewModelScope.launch(guard) { app.settings.setLang(l) }

    /**
     * The state the worker works in (any state or union territory). The saved details keep it,
     * and the minimum-wage work state follows from it ("Other" where the app has no state rates).
     */
    fun setWorkState(state: String) = viewModelScope.launch(guard) {
        app.settings.setRegion(com.acxelin.hisaabsaathi.core.StateInfo.regionFor(state))
        app.profile.flow.value.let { p -> if (p.state != state) app.profile.save(p.copy(state = state, district = "")) }
    }
    fun setGoal(paise: Long) = viewModelScope.launch(guard) { app.settings.setGoal(paise) }

    fun setReminders(on: Boolean) = viewModelScope.launch(guard) {
        app.settings.setReminders(on)
        if (on) Reminders.schedule(app) else Reminders.cancel(app)
    }

    fun finishOnboarding(share: Boolean) = viewModelScope.launch(guard) {
        app.profile.save(cleanDraft())
        app.profile.savePhoto(draftPhoto.value)
        draft.value.state.takeIf { it.isNotEmpty() }?.let { app.settings.setRegion(com.acxelin.hisaabsaathi.core.StateInfo.regionFor(it)) }
        app.settings.setShare(share)
        if (share) Sharing.schedule(app)
        app.settings.setOnboarded()
        app.settings.setSetupDone(com.acxelin.hisaabsaathi.data.Settings.SETUP_VERSION)
        Reminders.schedule(app)
    }

    /**
     * On a new phone, start from the phone's own language if it is one of the app's (the worker
     * still chooses). Someone who already chose keeps their choice.
     */
    fun suggestPhoneLanguage() = viewModelScope.launch(guard) {
        val s = app.settings.flow.first()
        if (s.onboarded) return@launch
        val phone = java.util.Locale.getDefault().language
        Lang.entries.firstOrNull { it.code == phone }?.let { app.settings.setLang(it) }
    }

    // ---- setup: details, downloads

    /** The worker's saved details (name, age, state, district, PIN), encrypted on the phone. */
    val profile: StateFlow<com.acxelin.hisaabsaathi.core.Profile> get() = app.profile.flow

    /** Details being filled in during setup (or in Settings → My details), saved when done. */
    val draft = MutableStateFlow(com.acxelin.hisaabsaathi.core.Profile())

    fun editDraft(change: (com.acxelin.hisaabsaathi.core.Profile) -> com.acxelin.hisaabsaathi.core.Profile) { draft.value = change(draft.value) }

    /** Starts editing from what is saved (Settings → My details, or setup shown again). */
    fun startDraft() {
        val region = settings.value?.region.orEmpty().takeIf { it.isNotEmpty() && it != "Other" }.orEmpty()
        draft.value = app.profile.flow.value.let { if (it.state.isEmpty()) it.copy(state = region) else it }
        draftPhoto.value = app.profile.photo.value
    }

    /** All PIN codes, states and districts, from India Post's directory. */
    val pins: com.acxelin.hisaabsaathi.core.PinDirectory get() = app.pins

    /**
     * The PIN being typed. A known PIN fills in the state, and the district when there is only
     * one (or a confident first guess); otherwise the worker chooses among the few it may be.
     */
    fun setDraftPin(typed: String) {
        val pin = typed.filter(Char::isDigit).take(6)
        val found = app.pins.lookup(pin) as? com.acxelin.hisaabsaathi.core.PinDirectory.Result.Found
        editDraft { d ->
            when {
                found == null -> d.copy(pin = pin)
                found.sure -> d.copy(pin = pin, state = found.state, district = found.districts.first())
                d.state == found.state && d.district in found.districts -> d.copy(pin = pin)
                else -> d.copy(pin = pin, state = found.state, district = "")
            }
        }
    }

    /**
     * "Use my location": one approximate fix finds the nearest PIN code on the phone, which fills
     * in PIN, state and district (never the language). Null if no fix or nothing near.
     */
    suspend fun locatePlace(): com.acxelin.hisaabsaathi.core.PinDirectory.Nearby? {
        val loc = runCatching { app.oneLocation() }.getOrNull() ?: return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { app.pins.nearest(loc.latitude, loc.longitude) }
            ?.also { setDraftPin(it.pin) }
    }

    /** The same from a known position (tests). */
    fun placeFrom(lat: Double, lon: Double): com.acxelin.hisaabsaathi.core.PinDirectory.Nearby? =
        app.pins.nearest(lat, lon)?.also { setDraftPin(it.pin) }

    /** A state chosen by hand: the district is chosen again unless the state stayed the same. */
    fun setDraftState(state: String) = editDraft { it.copy(state = state, district = if (state == it.state) it.district else "") }

    /** The draft with only the name in the name field, however it was typed or said. */
    private fun cleanDraft() = draft.value.let { p ->
        p.copy(name = if (p.name.isBlank()) "" else com.acxelin.hisaabsaathi.core.SpokenDetails.name(p.name))
    }

    fun saveDraft() = viewModelScope.launch(guard) {
        app.profile.save(cleanDraft())
        app.profile.savePhoto(draftPhoto.value)
        draft.value.state.takeIf { it.isNotEmpty() }?.let { app.settings.setRegion(com.acxelin.hisaabsaathi.core.StateInfo.regionFor(it)) }
    }

    /** The worker's saved photo (small JPEG), shown on the profile page and the home screen. */
    val photo: StateFlow<ByteArray?> get() = app.profile.photo

    /** The photo being taken during setup or on the profile page, saved with the details. */
    val draftPhoto = MutableStateFlow<ByteArray?>(null)

    /** A selfie was taken into [path]: make it small and square; the camera's file is deleted. */
    fun selfieTaken(path: String) = viewModelScope.launch(guard) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.acxelin.hisaabsaathi.data.ProfileStore.selfie(path) }
            ?.let { draftPhoto.value = it }
    }

    /**
     * Downloads what the app needs to hear and speak the chosen language, in the background:
     * the speech pack first (so the mic works sooner), then the read-aloud voice.
     */
    fun startVoiceDownloads() {
        if (!app.autoDownloadVoice) return
        val l = lang
        val stt = Catalog.stt(l)
        app.packs.download(stt)
        val voice = Catalog.voice(l) ?: return
        viewModelScope.launch(guard) {
            packStates.first { it[stt.id] is PackState.Ready || it[stt.id] is PackState.Failed }
            app.packs.download(voice)
        }
    }

    /** True while the mic is listening for a setup answer. */
    val answering = MutableStateFlow(false)

    fun speechReady(): Boolean = app.stt.available(lang)

    /**
     * Listens for one answer (name, age, PIN) with the on-device speech pack only: setup
     * answers never go to the phone's online recogniser. [onText] takes what was heard and
     * returns what was understood (just the name, the age…), which is read back; null if nothing.
     */
    fun listenForAnswer(onText: (String) -> String?) {
        if (answering.value) { app.stt.stop(); return }
        if (!app.stt.available(lang) || isBusy) return
        busy = viewModelScope.launch(guard) {
            stopPrompt(); app.speaker.stop()
            answering.value = true
            val heard = try {
                app.stt.listen(lang, maxSeconds = 10, onLevel = { level.value = it })
            } catch (e: Exception) {
                Heard.Failed(Heard.Kind.ENGINE, e.message ?: e.javaClass.simpleName)
            } finally {
                answering.value = false; level.value = 0f
            }
            when (heard) {
                is Heard.Text -> app.speaker.speak(onText(heard.text) ?: strings.notUnderstood, lang)
                Heard.Silence -> app.speaker.speak(strings.heardNothing, lang)
                is Heard.Failed -> { note("setup answer: ${heard.detail}"); app.speaker.speak(if (heard.kind == Heard.Kind.MIC) strings.micFailed else strings.voiceFailed, lang) }
            }
        }
    }

    private var promptPlayer: android.media.MediaPlayer? = null

    private fun stopPrompt() {
        promptPlayer?.let { runCatching { it.stop(); it.release() } }
        promptPlayer = null
    }

    /**
     * Reads a setup question aloud. A recording bundled with the app is used when there is one
     * (assets/onboarding_audio/<language code>/<key>.mp3, .ogg, .m4a or .wav), so questions are
     * spoken before any download; otherwise the app's own voice, or the phone's.
     */
    fun speakPrompt(key: String, text: String) {
        stopPrompt(); app.speaker.stop()
        val dir = "onboarding_audio/${lang.code}"
        val name = runCatching { app.assets.list(dir)?.firstOrNull { it.substringBeforeLast('.') == key } }.getOrNull()
        if (name != null) {
            fun play(source: android.media.MediaPlayer.() -> Unit): Boolean {
                val mp = android.media.MediaPlayer()
                return runCatching {
                    mp.setAudioAttributes(
                        android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                    )
                    mp.source()
                    mp.setOnCompletionListener { it.release(); if (promptPlayer === it) promptPlayer = null }
                    mp.prepare(); mp.start()
                    promptPlayer = mp
                }.onFailure { runCatching { mp.release() } }.isSuccess
            }
            // Played straight from the app package; if the build compressed the file, from a copy.
            val ok = play { app.assets.openFd("$dir/$name").use { fd -> setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) } } ||
                play {
                    val copy = java.io.File(app.cacheDir, "prompt-${lang.code}-$name")
                    if (!copy.exists()) app.assets.open("$dir/$name").use { input -> copy.outputStream().use { input.copyTo(it) } }
                    setDataSource(copy.path)
                }
            if (ok) return
        }
        app.speaker.speak(text, lang)
    }

    fun stopSpeaking() { stopPrompt(); app.speaker.stop() }

    /**
     * Reads the Aadhaar QR in a photo, on the phone, and fills in the details. The photo file
     * (if it was taken for this) is deleted straight away; only name, age, state, district and
     * PIN are kept. [onDone] gets false if no Aadhaar QR could be read.
     */
    fun readAadhaar(photo: Uri, tempFile: String?, onDone: (Boolean) -> Unit) = viewModelScope.launch(guard) {
        val codes = try { app.qr.read(photo) } finally { tempFile?.let { runCatching { java.io.File(it).delete() } } }
        onDone(useAadhaar(codes))
    }

    /** What the live QR scanner read (the scanner shows nothing else and keeps nothing). */
    fun readAadhaarCode(raw: String, onDone: (Boolean) -> Unit) = viewModelScope.launch(guard) {
        onDone(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.acxelin.hisaabsaathi.core.AadhaarQr.read(raw) }
            ?.let { useAadhaar(listOf(raw)) } ?: false)
    }

    /** Fills name, age, state, district and PIN from the first Aadhaar QR among [codes]; nothing else is kept. */
    private suspend fun useAadhaar(codes: List<String>): Boolean {
        val d = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            codes.firstNotNullOfOrNull { com.acxelin.hisaabsaathi.core.AadhaarQr.read(it) }
        } ?: return false
        // The card's state, if it is one we know; else the one its PIN belongs to.
        val byPin = app.pins.lookup(d.pin) as? com.acxelin.hisaabsaathi.core.PinDirectory.Result.Found
        val state = d.state.takeIf { it in app.pins.states } ?: byPin?.state ?: draft.value.state
        val district = com.acxelin.hisaabsaathi.core.Districts.match(d.district, app.pins.districts(state))
            ?: byPin?.takeIf { it.state == state && it.sure }?.districts?.first() ?: ""
        editDraft { it.copy(name = d.name, age = d.age ?: it.age, state = state, district = district, pin = d.pin.ifEmpty { it.pin }) }
        return true
    }

    /** Settings → Show setup again: the downloaded packs and the ledger stay. */
    fun reopenSetup() = viewModelScope.launch(guard) {
        startDraft()
        app.settings.setSetupDone(0)
    }

    fun download(pack: Pack) = app.packs.download(pack)
    fun deletePack(pack: Pack) = app.packs.delete(pack)
    fun sttPack(): Pack = Catalog.stt(lang)
    fun voicePack(): Pack? = Catalog.voice(lang)

    fun exportPdf(period: QueryPeriod) = viewModelScope.launch(guard) {
        count(Usage.Event.REPORTS)
        val s = strings
        val file = app.exporter.pdf(s.periodName(period), inPeriod(period), s, owner = app.profile.flow.value.line())
        app.exporter.share(listOf(file), s.share)
    }

    fun chainIntact(): Boolean = HashChain.firstBroken(entries.value.sortedBy { it.id }) < 0

    // ---- backup

    /** A copy of every line (the evidence file, with its hashes) to keep somewhere safe. */
    fun saveBackup() = viewModelScope.launch(guard) {
        val file = app.exporter.evidenceJson(app.ledger.oldestFirst())
        app.exporter.share(listOf(file), strings.share, mime = "application/json")
    }

    /** Adds the lines from a saved copy; lines already on this phone are not added twice. */
    fun restoreBackup(uri: Uri) = viewModelScope.launch(guard) {
        val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    // Read at most a little over the limit (works on every Android version).
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (out.size() <= MAX_BACKUP_BYTES) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
                    if (out.size() > MAX_BACKUP_BYTES) null else out.toByteArray().decodeToString()
                }
            }.getOrNull()
        }
        val lines = text?.let { com.acxelin.hisaabsaathi.export.Exporter.readBackup(it) }
        val message = if (lines == null) strings.restoreFailed else strings.restored(app.ledger.merge(lines))
        android.widget.Toast.makeText(app, message, android.widget.Toast.LENGTH_LONG).show()
        app.speaker.speak(message, lang)
    }

    fun exportEvidence() = viewModelScope.launch(guard) {
        count(Usage.Event.EVIDENCE)
        val s = strings
        val all = app.ledger.oldestFirst()
        val note = if (HashChain.firstBroken(all) < 0) s.recordIntact else s.recordBroken
        val pdf = app.exporter.pdf(s.evidenceTitle, all.reversed(), s, chainNote = note, owner = app.profile.flow.value.line())
        // With every photo: of the lines, and the papers (salary slips, bank statements, loans).
        val papers = photos.value.filter { it.entry == null }.map { java.io.File(it.path) to it.kind.name }
        val pictures = all.mapNotNull { e -> e.attachment?.let { java.io.File(it) }?.takeIf { it.exists() } } + papers.map { it.first }
        app.exporter.share(listOf(pdf, app.exporter.evidenceJson(all, papers)) + pictures.distinct(), s.share)
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
    }

    override fun onCleared() {
        app.speaker.stop()
    }
}
