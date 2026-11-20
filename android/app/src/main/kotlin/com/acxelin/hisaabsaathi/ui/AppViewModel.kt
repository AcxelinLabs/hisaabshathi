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
import com.acxelin.hisaabsaathi.core.Query
import com.acxelin.hisaabsaathi.core.QueryPeriod
import com.acxelin.hisaabsaathi.core.QueryTopic
import com.acxelin.hisaabsaathi.core.ReceiptReader
import com.acxelin.hisaabsaathi.core.Extraction
import com.acxelin.hisaabsaathi.core.Understand
import com.acxelin.hisaabsaathi.core.Understood
import com.acxelin.hisaabsaathi.core.Slm
import com.acxelin.hisaabsaathi.core.Strings
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
    val canSend: Boolean get() = BuildConfig.SHARE_URL.isNotBlank()
    val wagePack get() = app.wagePack

    private val lang: Lang get() = settings.value?.lang ?: Lang.HI
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
            is Understood.Ask -> { answer(u.query, text); replied(said, Improve.understood(u)) }
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
                        return replied(said, "MODEL ASK ${it.period.name} ALL")
                    }
                }
                // Never stay silent: say what the app can do and how to phrase it.
                say(strings.chatFallback, asked = text)
                replied(said, Improve.understood(u), Improve.Outcome.NOT_UNDERSTOOD)
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
                )
                app.samples.add(sample, pcm?.let { VoiceSamples.wav(it) })
            }.onFailure { note("improve: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    /** How a confirmation card ended. */
    private fun reviewed(r: Capture.Review, outcome: String) =
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
                false -> { rejected(r); capture.value = Capture.Idle; busy = null; listenForEntry() }
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
        (capture.value as? Capture.Review)?.let { reviewed(it, Improve.Outcome.DISMISSED) }
        capture.value = Capture.Idle
    }

    // ---- scan

    fun scanned(photo: Uri, path: String) = viewModelScope.launch(guard) {
        capture.value = Capture.Thinking
        val text = app.scanner.text(photo)
        // Same limits as speech: a long run of digits on a bill must not become a ₹ crore line.
        val draft = ReceiptReader.read(text)?.let { Extraction(text, listOf(it)).sane().entries.single() }
        if (draft == null) say(strings.notUnderstood)
        else review(Capture.Review(text.lines().take(6).joinToString(" / "), listOf(draft), true, "scan", path))
    }

    // ---- ledger, settings, export

    /** The line deleted last, kept for a few seconds so the worker can undo a wrong tap. */
    val lastDeleted = MutableStateFlow<Entry?>(null)

    fun delete(id: Long) = viewModelScope.launch(guard) {
        val e = entries.value.firstOrNull { it.id == id } ?: return@launch
        app.ledger.delete(id)
        lastDeleted.value = e
    }

    fun undoDelete() = viewModelScope.launch(guard) {
        val e = lastDeleted.value ?: return@launch
        lastDeleted.value = null
        app.ledger.restore(e)
    }

    fun forgetDeleted() { lastDeleted.value = null }

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
        return true
    }
    fun eraseAll() = viewModelScope.launch(guard) { app.ledger.eraseAll(); app.corrections.clear(); app.samples.clear() }

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

    fun setLang(l: Lang) = viewModelScope.launch(guard) { app.settings.setLang(l) }
    fun setRegion(r: String) = viewModelScope.launch(guard) { app.settings.setRegion(r) }
    fun setGoal(paise: Long) = viewModelScope.launch(guard) { app.settings.setGoal(paise) }

    fun setReminders(on: Boolean) = viewModelScope.launch(guard) {
        app.settings.setReminders(on)
        if (on) Reminders.schedule(app) else Reminders.cancel(app)
    }

    fun finishOnboarding(share: Boolean) = viewModelScope.launch(guard) {
        app.settings.setShare(share)
        if (share) Sharing.schedule(app)
        app.settings.setOnboarded()
        Reminders.schedule(app)
    }

    fun download(pack: Pack) = app.packs.download(pack)
    fun deletePack(pack: Pack) = app.packs.delete(pack)
    fun sttPack(): Pack = Catalog.stt(lang)
    fun voicePack(): Pack? = Catalog.voice(lang)

    fun exportPdf(period: QueryPeriod) = viewModelScope.launch(guard) {
        val s = strings
        val file = app.exporter.pdf(s.periodName(period), inPeriod(period), s)
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
        val s = strings
        val all = app.ledger.oldestFirst()
        val note = if (HashChain.firstBroken(all) < 0) s.recordIntact else s.recordBroken
        val pdf = app.exporter.pdf(s.evidenceTitle, all.reversed(), s, chainNote = note)
        app.exporter.share(listOf(pdf, app.exporter.evidenceJson(all)), s.share)
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
    }

    override fun onCleared() {
        app.speaker.stop()
    }
}
