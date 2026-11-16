package com.acxelin.hisaabsaathi.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acxelin.hisaabsaathi.HisaabApp
import com.acxelin.hisaabsaathi.core.Confirm
import com.acxelin.hisaabsaathi.core.DraftEntry
import com.acxelin.hisaabsaathi.core.Entry
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
    ) : Capture

    /** A spoken answer or a problem, also shown as text on the home screen. */
    data class Message(val text: String) : Capture
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HisaabApp

    /** null until the saved settings have been read, so onboarding does not flash. */
    val settings: StateFlow<Settings?> = app.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val entries: StateFlow<List<Entry>> = app.ledger.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val packStates = app.packs.states
    val capture = MutableStateFlow<Capture>(Capture.Idle)
    val level = MutableStateFlow(0f)
    val wagePack get() = app.wagePack

    private val lang: Lang get() = settings.value?.lang ?: Lang.HI
    private val strings: Strings get() = Strings(lang)

    val slmPack: Pack get() = Catalog.slmFor(app)

    // ---- voice

    fun micTapped() {
        if (capture.value == Capture.Listening) app.stt.stop() else listenForEntry()
    }

    private fun listenForEntry() = viewModelScope.launch {
        val text = listenOnce() ?: return@launch
        understand(text, "voice")
    }

    /** Returns null (after showing a message) when there is no on-device recogniser. */
    private suspend fun listenOnce(): String? {
        val l = lang
        app.speaker.stop()
        return try {
            when {
                app.stt.available(l) -> {
                    app.slm.unload() // keep peak memory low on 3 GB phones
                    capture.value = Capture.Listening
                    app.stt.listen(l, onLevel = { level.value = it }, onRecorded = { capture.value = Capture.Thinking })
                }
                app.systemStt.available() -> {
                    capture.value = Capture.Listening
                    app.systemStt.listen(l, onRecorded = { capture.value = Capture.Thinking })
                }
                else -> { say(strings.noSpeechEngine); null }
            }
        } catch (e: Exception) {
            say(strings.notUnderstood)
            null
        }
    }

    fun submitText(text: String) = viewModelScope.launch { understand(text, "typed") }

    private suspend fun understand(text: String, source: String) {
        if (text.isBlank()) return say(strings.notUnderstood)
        capture.value = Capture.Thinking
        when (val u = Understand.of(text)) {
            is Understood.Ask -> answer(u.query)
            is Understood.Talk -> say(strings.chat(u.chat, todayText()))
            is Understood.Entries -> {
                var result = u.extraction
                // The rules were unsure: let the language model have a look, if it is installed.
                if (result.lowConfidence && app.slm.available()) {
                    app.stt.unload()
                    result = Slm.choose(result, Slm.prompt(text)?.let { app.slm.generate(it) })
                }
                review(Capture.Review(text, result.entries, result.lowConfidence, source))
            }
            is Understood.Unknown -> {
                if (app.slm.available()) {
                    app.stt.unload()
                    val found = Slm.choose(Extraction(text), Slm.prompt(text)?.let { app.slm.generate(it) })
                    if (found.entries.isNotEmpty()) return review(Capture.Review(text, found.entries, true, source))
                    Slm.cleanChat(app.slm.generate(Slm.chatPrompt(text, lang.englishName)))?.let { return say(it) }
                }
                // Never stay silent: say what the app can do and how to phrase it.
                say(strings.chatFallback)
            }
        }
    }

    private fun todayText(): String =
        LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", com.acxelin.hisaabsaathi.speech.locale(lang)))

    private fun review(r: Capture.Review) {
        capture.value = r
        app.speaker.speak(strings.readback(r.drafts), lang)
    }

    fun replay() {
        (capture.value as? Capture.Review)?.let { app.speaker.speak(strings.readback(it.drafts), lang) }
    }

    private fun answer(q: Query) {
        if (q.topic == QueryTopic.MIN_WAGE) {
            val rate = Wages.floor(Wages.forRegion(wagePack, settings.value?.region.orEmpty()))
            return say(rate?.let { strings.minWageSpoken(it) + " " + strings.wageDataNote } ?: strings.notUnderstood)
        }
        say(strings.answer(q, totals(q.period)))
    }

    fun totals(period: QueryPeriod, list: List<Entry> = entries.value) =
        Ledger.totals(inPeriod(period, list))

    fun inPeriod(period: QueryPeriod, list: List<Entry> = entries.value): List<Entry> {
        val range = Ledger.range(period, LocalDate.now())
        return list.filter { it.epochDay in range }
    }

    private fun say(text: String) {
        capture.value = Capture.Message(text)
        app.speaker.speak(text, lang)
    }

    fun speakSummary(period: QueryPeriod) = say(strings.summary(period, totals(period)))

    // ---- review

    fun updateDraft(index: Int, draft: DraftEntry) {
        val r = capture.value as? Capture.Review ?: return
        capture.value = r.copy(drafts = r.drafts.toMutableList().also { it[index] = draft })
    }

    fun confirmSave() = viewModelScope.launch {
        val r = capture.value as? Capture.Review ?: return@launch
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        app.ledger.add(r.drafts.filter { it.amountPaise > 0 || it.hours != null || it.pieces != null }
            .map { Ledger.fromDraft(it, today, now, r.transcript, r.source, r.attachment) })
        say(strings.saved)
    }

    /** "Say yes or no": yes saves, no starts a new recording. */
    fun confirmByVoice() = viewModelScope.launch {
        val r = capture.value as? Capture.Review ?: return@launch
        val heard = listenOnce() ?: return@launch
        when (Confirm.parse(heard)) {
            true -> { capture.value = r; confirmSave() }
            false -> listenForEntry()
            null -> review(r)
        }
    }

    fun retry() {
        val wasVoice = (capture.value as? Capture.Review)?.source == "voice"
        dismiss()
        if (wasVoice) listenForEntry()
    }

    fun dismiss() {
        app.speaker.stop()
        capture.value = Capture.Idle
    }

    // ---- scan

    fun scanned(photo: Uri, path: String) = viewModelScope.launch {
        capture.value = Capture.Thinking
        val text = app.scanner.text(photo)
        val draft = ReceiptReader.read(text)
        if (draft == null) say(strings.notUnderstood)
        else review(Capture.Review(text.lines().take(6).joinToString(" / "), listOf(draft), true, "scan", path))
    }

    // ---- ledger, settings, export

    fun delete(id: Long) = viewModelScope.launch { app.ledger.delete(id) }
    fun eraseAll() = viewModelScope.launch { app.ledger.eraseAll() }

    fun setLang(l: Lang) = viewModelScope.launch { app.settings.setLang(l) }
    fun setRegion(r: String) = viewModelScope.launch { app.settings.setRegion(r) }
    fun setGoal(paise: Long) = viewModelScope.launch { app.settings.setGoal(paise) }

    fun setReminders(on: Boolean) = viewModelScope.launch {
        app.settings.setReminders(on)
        if (on) Reminders.schedule(app) else Reminders.cancel(app)
    }

    fun finishOnboarding() = viewModelScope.launch {
        app.settings.setOnboarded()
        Reminders.schedule(app)
    }

    fun download(pack: Pack) = app.packs.download(pack)
    fun deletePack(pack: Pack) = app.packs.delete(pack)
    fun sttPack(): Pack = Catalog.stt(lang)

    fun exportPdf(period: QueryPeriod) = viewModelScope.launch {
        val s = strings
        val file = app.exporter.pdf(s.periodName(period), inPeriod(period), s)
        app.exporter.share(listOf(file), s.share)
    }

    fun chainIntact(): Boolean = HashChain.firstBroken(entries.value.sortedBy { it.id }) < 0

    fun exportEvidence() = viewModelScope.launch {
        val s = strings
        val all = app.ledger.oldestFirst()
        val note = if (HashChain.firstBroken(all) < 0) s.recordIntact else s.recordBroken
        val pdf = app.exporter.pdf(s.evidenceTitle, all.reversed(), s, chainNote = note)
        app.exporter.share(listOf(pdf, app.exporter.evidenceJson(all)), s.share)
    }

    override fun onCleared() {
        app.speaker.stop()
    }
}
