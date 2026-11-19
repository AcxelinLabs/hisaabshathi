package com.acxelin.hisaabsaathi.recorder

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acxelin.hisaabsaathi.core.VoicePrompt
import com.acxelin.hisaabsaathi.core.VoiceSamples
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import com.acxelin.hisaabsaathi.core.Lang

/** Where the agent is. */
sealed interface Screen {
    data object Home : Screen
    data class NewSession(val step: Int = 0) : Screen
    data class Consent(val id: String) : Screen
    data class Record(val id: String, val index: Int) : Screen
    data class Done(val id: String) : Screen
}

/** The record button. */
sealed interface Take {
    data object Idle : Take
    data class Recording(val startedAt: Long) : Take
    class Recorded(val pcm: ShortArray, val quality: VoiceSamples.Quality) : Take
    data class Failed(val message: String) : Take
}

/** Leaving a question while a recording is not yet saved: the agent chooses to save it or not. */
class Leaving(val go: () -> Unit, val save: () -> Boolean)

/** What the agent fills in for a new speaker. */
data class SpeakerForm(
    val lang: String = "hi",
    val gender: String = "",
    val age: String = "",
    val state: String = "",
    val district: String = "",
    val place: String = "",
    val mode: String = MODE_READ,
    val count: Int = 25,
    val part: String = PART_ALL,
    val startAt: Int = 1,
    val set: String = SET_BUILTIN,
    val order: String = ORDER_RANDOM,
) {
    /** Step 1 (speaker), 2 (place), 3 (questions): what each needs before Next. */
    fun stepComplete(step: Int) = when (step) {
        0 -> gender.isNotEmpty() && age.isNotEmpty()
        1 -> state.isNotEmpty() && place.isNotEmpty()
        else -> count > 0
    }
    val complete get() = stepComplete(0) && stepComplete(1) && stepComplete(2)
}

const val MODE_READ = "read"
const val MODE_OWN = "own_words"
const val PART_ALL = "all"
const val PART_HARD = "hard"
const val SET_BUILTIN = "hisaabsaathi"
const val SET_FREE = "free"
const val ORDER_RANDOM = "random"
const val ORDER_IN_ORDER = "in_order"

/** A list of questions to record: the built-in HisaabSaathi list or one the agent loaded. */
class QuestionSet(val id: String, val name: String, val prompts: List<VoicePrompt>) {
    val byId = prompts.associateBy { it.id }
}

/** Below this much free space a recording or an export could fail half-way. */
private const val MIN_FREE_BYTES = 40L * 1024 * 1024

private fun langLabel(code: String) = Lang.fromCode(code).englishName

class RecorderViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("recorder", Context.MODE_PRIVATE)
    val store = SessionStore(File(app.filesDir, "sessions"))
    private val promptsText: String = runCatching { app.assets.open("voice_prompts.jsonl").bufferedReader().use { it.readText() } }.getOrDefault("")
    val prompts: List<VoicePrompt> = runCatching { VoiceSamples.parsePrompts(promptsText) }.getOrDefault(emptyList())
    private val builtin = QuestionSet(SET_BUILTIN, "HisaabSaathi questions", prompts)
    private val setsDir = File(app.filesDir, "sets").apply { mkdirs() }
    /** Lists the agent loaded from the phone (CSV, text or JSON lines), kept until deleted. */
    val sets = MutableStateFlow(loadSets())
    /** What the speaker said, typed by the agent (optional): own words, or recording anything. */
    val said = MutableStateFlow("")
    private val promptsVersion = MessageDigest.getInstance("SHA-256").digest(promptsText.toByteArray()).take(6).joinToString("") { "%02x".format(it) }

    val screen = MutableStateFlow<Screen>(Screen.Home)
    val sessions = MutableStateFlow(store.all())
    val take = MutableStateFlow<Take>(Take.Idle)
    val level = MutableStateFlow(0f)
    val agent = MutableStateFlow(prefs.getString("agent", "").orEmpty())
    val form = MutableStateFlow(rememberedForm())
    val message = MutableStateFlow<String?>(null)
    /** The microphone was refused for good: the agent must allow it in the phone's settings. */
    val micBlocked = MutableStateFlow(false)
    val leaving = MutableStateFlow<Leaving?>(null)
    val busy = MutableStateFlow(false)

    private var mic: Mic? = null
    private var player: MediaPlayer? = null
    private var autoStop: Job? = null

    /** The agent's usual place and settings; the speaker's own details always start empty. */
    private fun rememberedForm() = SpeakerForm(
        lang = prefs.getString("lang", "hi")!!, state = prefs.getString("state", "").orEmpty(),
        district = prefs.getString("district", "").orEmpty(), place = prefs.getString("place", "").orEmpty(),
        mode = prefs.getString("mode", MODE_READ)!!, count = prefs.getInt("count", 25),
        part = prefs.getString("part", PART_ALL)!!.let { if (it == PART_HARD) it else PART_ALL },
        set = prefs.getString("set", SET_BUILTIN)!!.let { id -> if (id == SET_BUILTIN || id == SET_FREE || File(setsDir, "$id.jsonl").exists()) id else SET_BUILTIN },
        order = prefs.getString("order", ORDER_RANDOM)!!,
    )

    // ---- question lists

    private fun loadSets(): List<QuestionSet> = setsDir.listFiles().orEmpty().filter { it.name.endsWith(".jsonl") }.sortedBy { it.name }.mapNotNull { f ->
        runCatching { QuestionSet(f.nameWithoutExtension, f.nameWithoutExtension, VoiceSamples.parsePrompts(f.readText())) }.getOrNull()
    }

    fun setOf(id: String): QuestionSet? = if (id == SET_BUILTIN) builtin else sets.value.firstOrNull { it.id == id }

    /** The question [promptId] of session [s]; when recording anything, a placeholder. */
    fun promptFor(s: Session, promptId: String): VoicePrompt? =
        if (s.set == SET_FREE) VoicePrompt(promptId, s.lang, "", "Anything about their work or money", "", "")
        else setOf(s.set)?.byId?.get(promptId)

    /** Loads a question list the agent picked on the phone. */
    fun importSet(uri: android.net.Uri) {
        val app = getApplication<Application>()
        val name = runCatching {
            app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "list.txt"
        val text = runCatching {
            app.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.takeIf { it.size <= 5_000_000 }?.toString(Charsets.UTF_8)
        }.getOrNull()
        if (text == null) { message.value = "Could not read $name (it must be a text, CSV or JSON-lines file under 5 MB)."; return }
        var id = VoiceSamples.slug(name.substringBeforeLast('.'))
        if (id == SET_BUILTIN || id == SET_FREE) id += "-list"
        val list = VoiceSamples.parseQuestionList(text, name, id)
        if (list.isEmpty()) { message.value = "No questions found in $name. Put one sentence per line, or a CSV with a \"text\" column."; return }
        if (!safely { File(setsDir, "$id.jsonl").writeText(list.joinToString("") { VoiceSamples.promptLine(it) + "\n" }) }) return
        sets.value = loadSets()
        updateForm(form.value.copy(set = id))
        message.value = "Loaded ${list.size} questions from $name."
    }

    fun deleteSet(id: String) {
        File(setsDir, "$id.jsonl").delete(); sets.value = loadSets()
        if (form.value.set == id) updateForm(form.value.copy(set = SET_BUILTIN))
    }

    fun setAgent(name: String) { agent.value = name; prefs.edit().putString("agent", name.trim()).apply() }

    fun session(id: String): Session? = sessions.value.firstOrNull { it.id == id }

    private fun freeBytes() = getApplication<Application>().filesDir.usableSpace

    // ---- new speaker

    fun newSession() {
        if (prompts.isEmpty()) { message.value = "The question list is missing from this app. Install the latest Recorder."; return }
        // A new person: gender and age are never carried over from the last speaker.
        val f = rememberedForm()
        form.value = f.copy(startAt = nextStart(f.set, f.lang, f.part))
        screen.value = Screen.NewSession(0)
    }

    fun formStep(step: Int) { screen.value = Screen.NewSession(step.coerceIn(0, 2)) }

    fun updateForm(f: SpeakerForm) {
        val old = form.value
        form.value = if (f.lang != old.lang || f.part != old.part || f.set != old.set) f.copy(startAt = nextStart(f.set, f.lang, f.part)) else f
    }

    /** The questions of list [set] for language [lang] ("hard": only the hard ones of the built-in list). */
    fun questionsOf(set: String, lang: String, part: String): List<VoicePrompt> =
        VoiceSamples.promptsFor(setOf(set)?.prompts.orEmpty(), lang).filter { part != PART_HARD || it.group == "hard" }

    private fun givenKey(set: String, lang: String, part: String) = if (set == SET_BUILTIN) "given_${lang}_$part" else "given_${set}_${lang}_$part"

    /** Where the next speaker starts (in order): after the questions earlier speakers on this phone were given. */
    private fun nextStart(set: String, lang: String, part: String): Int {
        val size = questionsOf(set, lang, part).size.coerceAtLeast(1)
        return prefs.getInt(givenKey(set, lang, part), 0) % size + 1
    }

    /** How often each question of [set] was recorded on this phone, for random picks. */
    private fun recordedCounts(set: String): Map<String, Int> =
        store.all().filter { it.set == set }.flatMap { s -> s.recordings.map { it.promptId } }.groupingBy { it }.eachCount()

    fun startSession() {
        val f = form.value
        if (agent.value.isBlank()) { message.value = "Write your name on the first screen first."; return }
        if (!f.complete) { message.value = "Fill in every detail first."; return }
        if (freeBytes() < MIN_FREE_BYTES) { message.value = "The phone is almost full. Share and delete old sessions first."; return }
        val planned: List<String> = when {
            f.set == SET_FREE -> List(f.count) { "free_%03d".format(it + 1) }
            f.order == ORDER_RANDOM -> VoiceSamples.randomBatch(questionsOf(f.set, f.lang, f.part), recordedCounts(f.set), f.count).map { it.id }
            else -> VoiceSamples.nextBatch(questionsOf(f.set, f.lang, f.part), (f.startAt - 1).coerceAtLeast(0), f.count).map { it.id }
        }
        if (planned.isEmpty()) { message.value = "This list has no questions in ${langLabel(f.lang)}."; return }
        val now = System.currentTimeMillis()
        val speaker = VoiceSamples.speakerCode()
        val s = Session(
            id = Session.newId(f.lang, speaker, now), createdAt = now, agent = agent.value.trim(), lang = f.lang, speaker = speaker,
            gender = f.gender, age = f.age, state = f.state, district = f.district.trim(), place = f.place, mode = f.mode,
            planned = planned, part = f.part, set = f.set, order = if (f.set == SET_FREE) ORDER_IN_ORDER else f.order, device = "${Build.MANUFACTURER} ${Build.MODEL}", appVersion = BuildConfig.VERSION_NAME,
            promptsVersion = promptsVersion,
        )
        if (!safely { store.save(s) }) return
        val edit = prefs.edit().putString("lang", f.lang).putString("state", f.state)
            .putString("district", f.district.trim()).putString("place", f.place).putString("mode", f.mode).putInt("count", f.count)
            .putString("part", f.part).putString("set", f.set).putString("order", f.order)
        if (f.set != SET_FREE && f.order == ORDER_IN_ORDER) edit.putInt(givenKey(f.set, f.lang, f.part), (f.startAt - 1) + planned.size)
        edit.apply()
        refresh()
        take.value = Take.Idle
        screen.value = Screen.Consent(s.id)
    }

    /** The speaker said no: nothing is kept, and the questions go to the next speaker. */
    fun cancelSession(id: String) {
        stopAll()
        val s = session(id)
        if (s != null && s.recordings.isEmpty() && s.order == ORDER_IN_ORDER && s.set != SET_FREE) {
            val key = givenKey(s.set, s.lang, s.part)
            prefs.edit().putInt(key, (prefs.getInt(key, 0) - s.planned.size).coerceAtLeast(0)).apply()
        }
        store.delete(id); refresh(); take.value = Take.Idle; screen.value = Screen.Home
    }

    // ---- recording

    fun tapRecord() {
        when (take.value) {
            is Take.Recording -> stopRecording()
            else -> startRecording()
        }
    }

    private fun startRecording() {
        stopPlaying()
        if (freeBytes() < MIN_FREE_BYTES) { take.value = Take.Failed("The phone is almost full. Share and delete old sessions, then record again."); return }
        val m = micFactory()
        try {
            m.start { level.value = it }
        } catch (t: Throwable) {
            take.value = Take.Failed(t.message ?: "The microphone could not start. Close other apps that use it and try again."); return
        }
        mic = m
        take.value = Take.Recording(System.currentTimeMillis())
        autoStop = viewModelScope.launch { delay(29_500); if (take.value is Take.Recording) stopRecording() }
    }

    private fun stopRecording() {
        autoStop?.cancel()
        val m = mic ?: return
        mic = null
        take.value = try {
            val pcm = m.stop()
            val q = VoiceSamples.quality(pcm)
            if (q == VoiceSamples.Quality.SHORT) Take.Failed("Too short. Tap the button, let them speak, then tap again.") else Take.Recorded(pcm, q)
        } catch (t: Throwable) {
            Take.Failed(t.message ?: "Recording failed. Try again.")
        }
        level.value = 0f
    }

    fun again() { stopPlaying(); take.value = Take.Idle }

    private fun startPlayer(path: String) {
        stopPlaying()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(path); prepare()
                setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
                start()
            }
        }.getOrNull()
    }

    fun play() {
        val pcm = (take.value as? Take.Recorded)?.pcm ?: return
        val f = File(getApplication<Application>().cacheDir, "preview.wav")
        if (runCatching { f.writeBytes(VoiceSamples.wav(pcm)) }.isSuccess) startPlayer(f.path)
    }

    /** Plays a saved recording of the current question again. */
    fun playSaved(id: String, promptId: String) {
        val r = session(id)?.recordingFor(promptId) ?: return
        startPlayer(File(store.audioDir(id), r.file).path)
    }

    private fun stopPlaying() { player?.let { runCatching { it.stop() }; runCatching { it.release() } }; player = null }

    /** Disk full or a storage error: say so instead of closing the app. */
    private fun safely(block: () -> Unit): Boolean = try {
        block(); true
    } catch (t: Throwable) {
        message.value = if (freeBytes() < MIN_FREE_BYTES) "The phone is full. Share and delete old sessions, then try again."
        else "Could not save: ${t.message ?: t.javaClass.simpleName}. Try again."
        false
    }

    fun saveConsent(id: String) {
        val rec = take.value as? Take.Recorded ?: return
        if (rec.quality == VoiceSamples.Quality.SILENT) { message.value = "Nothing was heard. Record their yes again."; return }
        val s = session(id) ?: return
        if (!safely { store.consentFile(id).writeBytes(VoiceSamples.wav(rec.pcm)); store.save(s.copy(consent = true)) }) return
        refresh()
        take.value = Take.Idle; said.value = ""
        screen.value = Screen.Record(id, firstOpen(s))
    }

    private fun firstOpen(s: Session): Int = s.planned.indexOfFirst { s.recordingFor(it) == null }.let { if (it < 0) 0 else it }

    fun continueSession(id: String) {
        val s = session(id) ?: return
        stopAll(); take.value = Take.Idle; said.value = ""
        screen.value = if (!s.consent) Screen.Consent(id) else Screen.Record(id, firstOpen(s))
    }

    /** Keeps the recording of question [index] and moves on. */
    fun saveAndNext(id: String, index: Int, place: String) {
        if (!saveTake(id, index, place)) return
        session(id)?.let { go(it, index + 1) }
    }

    private fun saveTake(id: String, index: Int, place: String): Boolean {
        val rec = take.value as? Take.Recorded ?: return false
        val s = session(id) ?: return false
        var updated: Session? = null
        if (!safely { updated = store.addRecording(s, s.planned[index], rec.pcm, place, System.currentTimeMillis(), said.value) }) return false
        refresh()
        take.value = Take.Idle
        said.value = ""
        return updated != null
    }

    /**
     * Leaving question [index] (Skip, Back, Finish, Home, the back key). A recording not yet
     * saved is never thrown away silently: the agent chooses Save or Discard first.
     */
    fun leave(id: String, index: Int, place: String, go: () -> Unit) {
        if (take.value is Take.Recording) stopRecording()
        if (take.value is Take.Recorded) leaving.value = Leaving(go, save = { saveTake(id, index, place) }) else go()
    }

    fun leaveSaving() {
        val l = leaving.value ?: return
        leaving.value = null
        if (l.save()) l.go()
    }

    fun leaveDiscarding() { val l = leaving.value ?: return; leaving.value = null; take.value = Take.Idle; said.value = ""; l.go() }
    fun stayHere() { leaving.value = null }

    fun skip(id: String, index: Int) { session(id)?.let { go(it, index + 1) } }

    fun back(id: String, index: Int) { if (index > 0) session(id)?.let { go(it, index - 1) } }

    private fun go(s: Session, index: Int) {
        stopAll()
        take.value = Take.Idle
        said.value = s.recordingFor(s.planned.getOrNull(index).orEmpty())?.said.orEmpty()
        screen.value = if (index >= s.planned.size) Screen.Done(s.id) else Screen.Record(s.id, index)
    }

    fun finish(id: String) { stopAll(); take.value = Take.Idle; refresh(); screen.value = Screen.Done(id) }

    fun home() { stopAll(); take.value = Take.Idle; refresh(); screen.value = Screen.Home }

    /** The app went to the background (a call, the home button): stop the microphone, keep what was recorded. */
    fun onBackground() {
        if (take.value is Take.Recording) stopRecording()
        stopPlaying()
    }

    private fun stopAll() {
        stopPlaying()
        autoStop?.cancel()
        mic?.let { runCatching { it.stop() } }; mic = null
        level.value = 0f
    }

    // ---- export

    /** Builds the session's zip and hands it to [share] (the Android share sheet). */
    fun export(id: String, share: (File) -> Unit) {
        val s = session(id) ?: return
        if (s.recordings.isEmpty()) { message.value = "Nothing recorded yet."; return }
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val zip = withContext(Dispatchers.IO) {
                    val prompts = s.planned.mapNotNull { promptFor(s, it) }.associateBy { it.id }
                    SessionExport.zip(store, s, prompts, File(getApplication<Application>().cacheDir, "exports")).also {
                        store.save(s.copy(exportedAt = System.currentTimeMillis()))
                    }
                }
                refresh()
                share(zip)
            } catch (t: Throwable) {
                message.value = if (freeBytes() < MIN_FREE_BYTES) "The phone is too full to make the zip. Delete sessions already shared, then try again."
                else "Could not make the zip: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                busy.value = false
            }
        }
    }

    fun delete(id: String) { store.delete(id); refresh() }

    fun storageUsedMb(): Long = store.bytesUsed() / (1024 * 1024)

    private fun refresh() { sessions.value = store.all() }

    override fun onCleared() { stopAll(); super.onCleared() }

    companion object {
        /** Replaced in tests: there is no microphone on a PC. */
        var micFactory: () -> Mic = { PhoneMic() }
    }
}

/** Read to the speaker before recording, in their language (for the agent: the English below it). */
val consentText: Map<String, String> = mapOf(
    "hi" to "आपकी आवाज़ रिकॉर्ड होगी ताकि हिसाब साथी ऐप लोगों की बात बेहतर समझ सके। इसे सिर्फ हिसाब साथी की टीम सुनेगी। आपका नाम नहीं रखा जाएगा। आप कभी भी रोक सकते हैं। क्या आप राज़ी हैं? हाँ बोलिए।",
    "or" to "ହିସାବ ସାଥୀ ଆପ୍ ଲୋକଙ୍କ କଥା ଭଲ ଭାବେ ବୁଝିବା ପାଇଁ ଆପଣଙ୍କ ସ୍ୱର ରେକର୍ଡ ହେବ। କେବଳ ହିସାବ ସାଥୀ ଦଳ ଏହା ଶୁଣିବେ। ଆପଣଙ୍କ ନାଁ ରଖାଯିବ ନାହିଁ। ଆପଣ ଯେକୌଣସି ସମୟରେ ବନ୍ଦ କରିପାରିବେ। ଆପଣ ରାଜି କି? ହଁ କୁହନ୍ତୁ।",
    "te" to "హిసాబ్ సాథీ యాప్ మనుషుల మాటలు బాగా అర్థం చేసుకోవడానికి మీ గొంతు రికార్డ్ చేస్తాం. దీన్ని హిసాబ్ సాథీ టీమ్ మాత్రమే వింటుంది. మీ పేరు ఎక్కడా రాయము. మీరు ఎప్పుడైనా ఆపవచ్చు. మీకు ఇష్టమేనా? అవును అని చెప్పండి.",
    "kn" to "ಹಿಸಾಬ್ ಸಾಥಿ ಆ್ಯಪ್ ಜನರ ಮಾತನ್ನು ಚೆನ್ನಾಗಿ ಅರ್ಥಮಾಡಿಕೊಳ್ಳಲು ನಿಮ್ಮ ಧ್ವನಿಯನ್ನು ರೆಕಾರ್ಡ್ ಮಾಡುತ್ತೇವೆ. ಇದನ್ನು ಹಿಸಾಬ್ ಸಾಥಿ ತಂಡ ಮಾತ್ರ ಕೇಳುತ್ತದೆ. ನಿಮ್ಮ ಹೆಸರನ್ನು ಬರೆದುಕೊಳ್ಳುವುದಿಲ್ಲ. ನೀವು ಯಾವಾಗ ಬೇಕಾದರೂ ನಿಲ್ಲಿಸಬಹುದು. ನಿಮಗೆ ಒಪ್ಪಿಗೆಯೇ? ಹೌದು ಎಂದು ಹೇಳಿ.",
    "ta" to "ஹிசாப் சாத்தி ஆப் மக்கள் பேசுவதை நன்றாகப் புரிந்துகொள்ள உங்கள் குரலைப் பதிவு செய்கிறோம். இதை ஹிசாப் சாத்தி குழு மட்டுமே கேட்கும். உங்கள் பெயரை எழுத மாட்டோம். நீங்கள் எப்போது வேண்டுமானாலும் நிறுத்தலாம். உங்களுக்குச் சம்மதமா? ஆம் என்று சொல்லுங்கள்.",
    "en" to "Your voice will be recorded so the HisaabSaathi app can understand people better. Only the HisaabSaathi team will listen to it. Your name is not saved. You can stop at any time. Do you agree? Please say yes.",
)
