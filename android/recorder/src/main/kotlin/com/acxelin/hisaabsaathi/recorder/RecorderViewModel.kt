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

/** Where the agent is. */
sealed interface Screen {
    data object Home : Screen
    data object NewSession : Screen
    data class Consent(val id: String) : Screen
    data class Record(val id: String, val index: Int) : Screen
    data class Done(val id: String) : Screen
}

/** The record button. */
sealed interface Take {
    data object Idle : Take
    data class Recording(val startedAt: Long) : Take
    data class Recorded(val pcm: ShortArray) : Take
    data class Failed(val message: String) : Take
}

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
) {
    val complete get() = gender.isNotEmpty() && age.isNotEmpty() && state.isNotEmpty() && place.isNotEmpty()
}

const val MODE_READ = "read"
const val MODE_OWN = "own_words"
const val PART_ALL = "all"
const val PART_EVERYDAY = "1-200"
const val PART_HARD = "201-300"

class RecorderViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("recorder", Context.MODE_PRIVATE)
    val store = SessionStore(File(app.filesDir, "sessions"))
    val prompts: List<VoicePrompt> = runCatching {
        VoiceSamples.parsePrompts(app.assets.open("voice_prompts.jsonl").bufferedReader().use { it.readText() })
    }.getOrDefault(emptyList())
    val promptById = prompts.associateBy { it.id }

    val screen = MutableStateFlow<Screen>(Screen.Home)
    val sessions = MutableStateFlow(store.all())
    val take = MutableStateFlow<Take>(Take.Idle)
    val level = MutableStateFlow(0f)
    val agent = MutableStateFlow(prefs.getString("agent", "").orEmpty())
    val form = MutableStateFlow(SpeakerForm(lang = prefs.getString("lang", "hi")!!, state = prefs.getString("state", "").orEmpty()))
    val message = MutableStateFlow<String?>(null)

    private var mic: Mic? = null
    private var player: MediaPlayer? = null
    private var autoStop: Job? = null

    fun setAgent(name: String) { agent.value = name; prefs.edit().putString("agent", name.trim()).apply() }

    fun session(id: String): Session? = sessions.value.firstOrNull { it.id == id }

    // ---- new speaker

    fun newSession() {
        val f = form.value
        form.value = f.copy(startAt = nextStart(f.lang, f.part))
        screen.value = Screen.NewSession
    }

    fun updateForm(f: SpeakerForm) {
        val old = form.value
        form.value = if (f.lang != old.lang || f.part != old.part) f.copy(startAt = nextStart(f.lang, f.part)) else f
    }

    private fun partOf(lang: String, part: String): List<VoicePrompt> = VoiceSamples.promptsFor(prompts, lang).filter {
        val n = it.id.takeLast(3).toIntOrNull() ?: 0
        when (part) { PART_EVERYDAY -> n <= 200; PART_HARD -> n > 200; else -> true }
    }

    /** Where the next speaker starts: after the questions earlier speakers on this phone were given. */
    private fun nextStart(lang: String, part: String): Int {
        val size = partOf(lang, part).size.coerceAtLeast(1)
        return prefs.getInt("given_${lang}_$part", 0) % size + 1
    }

    fun startSession() {
        val f = form.value
        if (!f.complete || agent.value.isBlank()) { message.value = "Fill in your name and every detail first."; return }
        val list = partOf(f.lang, f.part)
        val batch = VoiceSamples.nextBatch(list, (f.startAt - 1).coerceAtLeast(0), f.count)
        if (batch.isEmpty()) { message.value = "No questions for this language."; return }
        prefs.edit().putInt("given_${f.lang}_${f.part}", (f.startAt - 1) + batch.size).putString("lang", f.lang).putString("state", f.state).apply()
        val now = System.currentTimeMillis()
        val speaker = VoiceSamples.speakerCode()
        val s = Session(
            id = Session.newId(f.lang, speaker, now), createdAt = now, agent = agent.value.trim(), lang = f.lang, speaker = speaker,
            gender = f.gender, age = f.age, state = f.state, district = f.district.trim(), place = f.place, mode = f.mode,
            planned = batch.map { it.id }, device = "${Build.MANUFACTURER} ${Build.MODEL}", appVersion = BuildConfig.VERSION_NAME,
        )
        store.save(s)
        refresh()
        take.value = Take.Idle
        screen.value = Screen.Consent(s.id)
    }

    /** The speaker said no: nothing is kept. */
    fun cancelSession(id: String) {
        stopAll()
        store.delete(id); refresh(); screen.value = Screen.Home
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
        val m = micFactory()
        try {
            m.start { level.value = it }
        } catch (t: Throwable) {
            take.value = Take.Failed(t.message ?: "The microphone could not start."); return
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
            if (pcm.size < VoiceSamples.SAMPLE_RATE / 4) Take.Failed("Too short. Tap the button, let them speak, then tap again.") else Take.Recorded(pcm)
        } catch (t: Throwable) {
            Take.Failed(t.message ?: "Recording failed.")
        }
        level.value = 0f
    }

    fun again() { stopPlaying(); take.value = Take.Idle }

    fun play() {
        val pcm = (take.value as? Take.Recorded)?.pcm ?: return
        stopPlaying()
        val f = File(getApplication<Application>().cacheDir, "preview.wav").apply { writeBytes(VoiceSamples.wav(pcm)) }
        player = runCatching { MediaPlayer().apply { setDataSource(f.path); prepare(); start() } }.getOrNull()
    }

    /** Plays a saved recording of the current question again. */
    fun playSaved(id: String, promptId: String) {
        val r = session(id)?.recordingFor(promptId) ?: return
        stopPlaying()
        player = runCatching { MediaPlayer().apply { setDataSource(File(store.audioDir(id), r.file).path); prepare(); start() } }.getOrNull()
    }

    private fun stopPlaying() { player?.let { runCatching { it.stop(); it.release() } }; player = null }

    fun saveConsent(id: String) {
        val pcm = (take.value as? Take.Recorded)?.pcm ?: return
        val s = session(id) ?: return
        store.consentFile(id).writeBytes(VoiceSamples.wav(pcm))
        store.save(s.copy(consent = true))
        refresh()
        take.value = Take.Idle
        screen.value = Screen.Record(id, firstOpen(s))
    }

    private fun firstOpen(s: Session): Int = s.planned.indexOfFirst { s.recordingFor(it) == null }.let { if (it < 0) 0 else it }

    fun continueSession(id: String) {
        val s = session(id) ?: return
        take.value = Take.Idle
        screen.value = if (!s.consent) Screen.Consent(id) else Screen.Record(id, firstOpen(s))
    }

    /** Keeps the recording of question [index] and moves on. */
    fun saveAndNext(id: String, index: Int, place: String) {
        val pcm = (take.value as? Take.Recorded)?.pcm ?: return
        val s = session(id) ?: return
        val updated = store.addRecording(s, s.planned[index], pcm, place, System.currentTimeMillis())
        refresh()
        go(updated, index + 1)
    }

    fun skip(id: String, index: Int) { session(id)?.let { go(it, index + 1) } }

    fun back(id: String, index: Int) { if (index > 0) { stopAll(); take.value = Take.Idle; screen.value = Screen.Record(id, index - 1) } }

    private fun go(s: Session, index: Int) {
        stopAll()
        take.value = Take.Idle
        screen.value = if (index >= s.planned.size) Screen.Done(s.id) else Screen.Record(s.id, index)
    }

    fun finish(id: String) { stopAll(); take.value = Take.Idle; screen.value = Screen.Done(id) }

    fun home() { stopAll(); take.value = Take.Idle; refresh(); screen.value = Screen.Home }

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
        viewModelScope.launch {
            val zip = withContext(Dispatchers.IO) {
                SessionExport.zip(store, s, promptById, File(getApplication<Application>().cacheDir, "exports"))
            }
            store.save(s.copy(exportedAt = System.currentTimeMillis()))
            refresh()
            share(zip)
        }
    }

    fun delete(id: String) { store.delete(id); refresh() }

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
