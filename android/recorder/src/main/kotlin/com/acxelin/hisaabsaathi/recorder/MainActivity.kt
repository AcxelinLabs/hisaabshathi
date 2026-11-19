package com.acxelin.hisaabsaathi.recorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.background
import com.acxelin.hisaabsaathi.core.VoiceSamples
import androidx.core.app.ActivityCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acxelin.hisaabsaathi.core.Lang
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    // Made with this activity's own Application (the default factory keeps the first one it saw).
    private val vm: RecorderViewModel by viewModels { viewModelFactory { initializer { RecorderViewModel(application) } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RecorderTheme(Lang.EN) { RecorderUi(vm) } }
    }

    /** A phone call or the home button: the microphone stops, what was recorded is kept. */
    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }
}

// Choices the agent taps. The first of each pair is what goes into the dataset.
val genders = listOf("female" to "Woman", "male" to "Man", "other" to "Other")
val ages = listOf("18-25" to "18–25", "26-35" to "26–35", "36-50" to "36–50", "51+" to "51 or more")
val states = listOf("Odisha", "Telangana", "Andhra Pradesh", "Karnataka", "Tamil Nadu", "Bihar", "Uttar Pradesh", "Jharkhand", "West Bengal", "Maharashtra", "Delhi", "Other")
val places = listOf("quiet_room" to "Quiet room", "noisy_room" to "Noisy room", "outdoors" to "Outdoors", "street_market" to "Street / market", "worksite" to "Worksite")
val modes = listOf(MODE_READ to "Reads the sentence", MODE_OWN to "Says it in own words")
val orders = listOf(ORDER_RANDOM to "Random", ORDER_IN_ORDER to "In order")

fun label(options: List<Pair<String, String>>, code: String) = options.firstOrNull { it.first == code }?.second ?: code
fun langName(code: String) = Lang.fromCode(code).let { if (it == Lang.EN) "English" else "${it.englishName} (${it.nativeName})" }

@Composable
fun RecorderUi(vm: RecorderViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val leaving by vm.leaving.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val share: (File) -> Unit = { zip ->
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
        val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, zip.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(send, "Send ${zip.name}")) }
    }
    // The phone must not sleep while someone is speaking to it.
    val view = LocalView.current
    val recordingScreen = screen is Screen.Record || screen is Screen.Consent
    DisposableEffect(recordingScreen) { view.keepScreenOn = recordingScreen; onDispose { view.keepScreenOn = false } }

    Column(Modifier.fillMaxSize().background(Ink).systemBarsPadding().background(MaterialTheme.colorScheme.background)) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(vm, share)
            is Screen.NewSession -> { BackHandler { if (s.step > 0) vm.formStep(s.step - 1) else vm.home() }; NewSessionScreen(vm, s.step) }
            is Screen.Consent -> { BackHandler { vm.cancelSession(s.id) }; ConsentScreen(vm, s.id) }
            is Screen.Record -> RecordScreen(vm, s.id, s.index)
            is Screen.Done -> { BackHandler { vm.home() }; DoneScreen(vm, s.id, share) }
        }
    }
    message?.let { m ->
        AlertDialog(onDismissRequest = { vm.message.value = null }, text = { Text(m, fontSize = 17.sp) },
            confirmButton = { TextButton(onClick = { vm.message.value = null }) { Text("OK") } })
    }
    leaving?.let {
        AlertDialog(
            onDismissRequest = vm::stayHere,
            title = { Text("Keep this recording?") },
            text = { Text("It is not saved yet.", fontSize = 17.sp) },
            confirmButton = { TextButton(onClick = vm::leaveSaving) { Text("Save it", fontWeight = FontWeight.Bold) } },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::stayHere) { Text("Stay") }
                    TextButton(onClick = vm::leaveDiscarding) { Text("Discard", color = Sindoor) }
                }
            },
        )
    }
}

@Composable
private fun TopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) } else Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = InkWash, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paper).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(vm: RecorderViewModel, share: (File) -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val agent by vm.agent.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Session?>(null) }
    TopBar("HisaabSaathi Recorder", "Voice samples for HisaabSaathi")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(value = agent, onValueChange = vm::setAgent, label = { Text("Your name (field agent)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = vm::newSession, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Marigold, contentColor = Ink)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New speaker", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (sessions.isEmpty()) {
            Text("No recordings yet. Tap New speaker to start.", color = InkSoft, fontSize = 16.sp)
        } else {
            val unshared = sessions.count { it.exportedAt == null && it.recordings.isNotEmpty() }
            val total = sessions.sumOf { it.recordings.size }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (unshared > 0) Notice else InkWash).padding(12.dp)) {
                Text(
                    "${sessions.size} speakers · $total recordings · ${vm.storageUsedMb()} MB" +
                        if (unshared > 0) "\n$unshared not shared yet: tap Share zip on each." else "\nAll shared ✓",
                    color = if (unshared > 0) NoticeInk else Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        for (s in sessions) {
            Card {
                Text("${langName(s.lang)} · ${s.speaker}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${label(genders, s.gender)} · ${label(ages, s.age)} · ${s.state}${if (s.district.isNotBlank()) ", " + s.district else ""} · ${label(places, s.place)}", color = InkSoft)
                val time = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(s.createdAt))
                Text(
                    "$time · ${s.recordings.size} of ${s.planned.size} recorded" + when {
                        s.recordings.isEmpty() -> ""
                        s.exportedAt == null -> " · not shared"
                        s.recordings.any { it.recordedAt > s.exportedAt } -> " · new since shared"
                        else -> " · shared ✓"
                    },
                    fontSize = 15.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.recordings.size < s.planned.size) OutlinedButton(onClick = { vm.continueSession(s.id) }) { Text("Continue") }
                    if (s.recordings.isNotEmpty()) Button(onClick = { vm.export(s.id, share) }, enabled = !busy) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share zip")
                    }
                    IconButton(onClick = { toDelete = s }) { Icon(Icons.Default.Delete, "Delete", tint = InkSoft) }
                }
            }
        }
        Text(
            "When a speaker is done, tap Share zip and send it to the office computer (WhatsApp, Drive or email). " +
                "Delete it here only after it has reached the computer.",
            color = InkSoft, fontSize = 14.sp,
        )
    }
    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${s.speaker}?") },
            text = { Text(if (s.exportedAt == null && s.recordings.isNotEmpty()) "These ${s.recordings.size} recordings have NOT been shared yet. They will be lost." else "Only if the zip has reached the office computer.") },
            confirmButton = { TextButton(onClick = { vm.delete(s.id); toDelete = null }) { Text("Delete", color = Sindoor) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- new speaker: three short steps

/** Big tappable options, [columns] to a row: easier to hit in the field than small chips. */
@Composable
private fun Options(options: List<Pair<String, String>>, selected: String, columns: Int = 2, hint: (String) -> String? = { null }, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in options.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((code, text) in row) {
                    val on = selected == code
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) Marigold else Paper)
                            .clickable { onPick(code) }.padding(horizontal = 12.dp, vertical = 14.dp),
                    ) {
                        Text(text, fontSize = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = Ink)
                        hint(code)?.let { Text(it, fontSize = 13.sp, color = if (on) Ink else InkSoft) }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))

@Composable
fun NewSessionScreen(vm: RecorderViewModel, step: Int) {
    val f by vm.form.collectAsStateWithLifecycle()
    val agent by vm.agent.collectAsStateWithLifecycle()
    val titles = listOf("The speaker", "The place", "The questions")
    TopBar("New speaker · ${step + 1} of 3", "${titles[step]} · agent: ${agent.ifBlank { "write your name first" }}", onBack = { if (step > 0) vm.formStep(step - 1) else vm.home() })
    LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Marigold, trackColor = InkWash)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (step) {
                0 -> {
                    Heading("Language they speak")
                    Options(Lang.entries.map { it.code to (if (it == Lang.EN) "English" else it.nativeName) }, f.lang, columns = 3) { vm.updateForm(f.copy(lang = it)) }
                    Heading("Gender")
                    Options(genders, f.gender, columns = 3) { vm.updateForm(f.copy(gender = it)) }
                    Heading("Age")
                    Options(ages, f.age, columns = 2) { vm.updateForm(f.copy(age = it)) }
                }
                1 -> {
                    Heading("State")
                    Options(states.map { it to it }, f.state, columns = 2) { vm.updateForm(f.copy(state = it)) }
                    OutlinedTextField(value = f.district, onValueChange = { vm.updateForm(f.copy(district = it)) }, label = { Text("District or town (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Heading("Where are you recording")
                    Options(places, f.place, columns = 2) { vm.updateForm(f.copy(place = it)) }
                }
                else -> {
                    // The place is remembered from last time: shown here, changed with one tap.
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(InkWash).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${f.state}${if (f.district.isNotBlank()) ", " + f.district else ""} · ${label(places, f.place)}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { vm.formStep(1) }) { Text("Change place") }
                    }
                    QuestionsStep(vm, f)
                }
            }
        }
        // Next stays at the bottom of the screen.
        Row(Modifier.fillMaxWidth().background(Paper).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val ready = f.stepComplete(step) && (step < 2 || agent.isNotBlank())
            Text(
                when {
                    ready -> ""
                    step == 0 -> "Choose gender and age"
                    step == 1 -> "Choose state and place"
                    else -> "Write your name on the first screen"
                },
                color = InkSoft, fontSize = 14.sp, modifier = Modifier.weight(1f),
            )
            Button(onClick = { if (step == 0 && f.stepComplete(1)) vm.formStep(2) else if (step < 2) vm.formStep(step + 1) else vm.startSession() }, enabled = ready, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(12.dp)) {
                Text(if (step < 2) "Next" else "Start", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Which questions: the built-in list, a list loaded on the phone, or anything they say. */
@Composable
private fun QuestionsStep(vm: RecorderViewModel, f: SpeakerForm) {
    val sets by vm.sets.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importSet) }
    Heading("Question list")
    val lists = listOf(SET_BUILTIN to "HisaabSaathi questions") + sets.map { it.id to it.name } + listOf(SET_FREE to "Record anything (no list)")
    Options(lists, f.set, columns = 1, hint = { id ->
        when (id) {
            SET_BUILTIN -> "${vm.questionsOf(SET_BUILTIN, f.lang, PART_ALL).size} questions in this language"
            SET_FREE -> "They say anything about their work or money; you may type what they said"
            else -> "${vm.questionsOf(id, f.lang, PART_ALL).size} questions in this language · loaded on this phone"
        }
    }) { vm.updateForm(f.copy(set = it)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pick.launch(arrayOf("text/*", "application/json", "application/octet-stream", "application/vnd.ms-excel")) }) { Text("+ Load a list (CSV or text)") }
        if (f.set != SET_BUILTIN && f.set != SET_FREE) TextButton(onClick = { vm.deleteSet(f.set) }) { Text("Remove this list", color = Sindoor) }
    }
    if (f.set != SET_FREE) {
        Heading("Order")
        Options(orders, f.order, columns = 2, hint = {
            if (it == ORDER_RANDOM) "A mix; questions recorded least come first" else "Continues where the last speaker stopped"
        }) { vm.updateForm(f.copy(order = it)) }
        if (f.set == SET_BUILTIN) Options(listOf(PART_ALL to "All questions", PART_HARD to "Only the hard ones"), f.part, columns = 2) { vm.updateForm(f.copy(part = it)) }
        Heading("How they ask")
        Options(modes, f.mode, columns = 1, hint = {
            if (it == MODE_READ) "They read each question shown on the screen" else "For people who do not read: explain the question, they ask it their way"
        }) { vm.updateForm(f.copy(mode = it)) }
    }
    Heading(if (f.set == SET_FREE) "How many recordings" else "How many questions")
    Options(listOf(10, 25, 50, 100).map { it.toString() to it.toString() }, f.count.toString(), columns = 4) { vm.updateForm(f.copy(count = it.toInt())) }
    if (f.set != SET_FREE && f.order == ORDER_IN_ORDER) {
        val size = vm.questionsOf(f.set, f.lang, f.part).size.coerceAtLeast(1)
        Heading("Start at question")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { vm.updateForm(f.copy(startAt = (f.startAt - 1).coerceAtLeast(1))) }) { Text("−", fontSize = 20.sp) }
            Text("${f.startAt}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { vm.updateForm(f.copy(startAt = (f.startAt + 1).coerceAtMost(size))) }) { Text("+", fontSize = 20.sp) }
        }
        val end = (f.startAt - 1 + minOf(f.count, size)) % size
        Text("This speaker gets questions ${f.startAt} to ${if (end == 0) size else end} of $size. The next speaker continues from there.", color = InkSoft, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------- consent

@Composable
fun ConsentScreen(vm: RecorderViewModel, id: String) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val s = sessions.firstOrNull { it.id == id } ?: return
    val take by vm.take.collectAsStateWithLifecycle()
    TopBar("Their consent", "${langName(s.lang)} · ${s.speaker}", onBack = { vm.cancelSession(id) })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Read this to them:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Card {
            Text(consentText[s.lang] ?: consentText["en"]!!, style = TextStyle(fontFamily = fontFor(Lang.fromCode(s.lang))), fontSize = 21.sp, lineHeight = 32.sp)
        }
        if (s.lang != "en") Text("In English: " + consentText["en"], color = InkSoft, fontSize = 14.sp)
        Text("Then record their \"yes\".", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        RecordControls(vm, take, saveLabel = "Save their yes", onSave = { vm.saveConsent(id) })
        OutlinedButton(onClick = { vm.cancelSession(id) }, modifier = Modifier.fillMaxWidth()) { Text("They said no: stop") }
    }
}

// ---------------------------------------------------------------- recording

/** What the agent should do about a recording's sound. */
private fun qualityNote(q: VoiceSamples.Quality): Pair<String, Color>? = when (q) {
    VoiceSamples.Quality.GOOD -> "✓ Sounds good" to Leaf
    VoiceSamples.Quality.QUIET -> "Quiet: ask them to speak a little louder, or hold the phone closer. Record again if you can." to NoticeInk
    VoiceSamples.Quality.LOUD -> "Too loud, it may be distorted: hold the phone a little further away and record again." to NoticeInk
    VoiceSamples.Quality.SILENT -> "Nothing was heard. Check that nothing covers the microphone and record again." to Sindoor
    VoiceSamples.Quality.SHORT -> null
}

@Composable
fun RecordControls(vm: RecorderViewModel, take: Take, saveLabel: String, onSave: () -> Unit) {
    val level by vm.level.collectAsStateWithLifecycle()
    val blocked by vm.micBlocked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { vm.micBlocked.value = false; vm.tapRecord() }
        // Refused with "don't ask again": the phone no longer shows the question, only Settings can allow it.
        else if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)) vm.micBlocked.value = true
    }
    val tap = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.tapRecord()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (blocked) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Notice).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("The microphone is not allowed for this app.", color = NoticeInk, fontWeight = FontWeight.Bold)
                Text("Open Settings > Permissions > Microphone > Allow, then come back.", color = NoticeInk)
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)))
                    }
                }) { Text("Open Settings") }
            }
        }
        when (take) {
            is Take.Recorded -> {
                Text("Recorded ${"%.1f".format(take.pcm.size / 16000.0)} s", color = InkSoft)
                qualityNote(take.quality)?.let { (note, color) -> Text(note, color = color, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = vm::play, modifier = Modifier.height(52.dp)) { Icon(Icons.Default.PlayArrow, null); Text("Play") }
                    OutlinedButton(onClick = vm::again, modifier = Modifier.height(52.dp)) { Icon(Icons.Default.Refresh, null); Text("Again") }
                }
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Leaf)) {
                    Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text(saveLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                val recording = take is Take.Recording
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                if (take is Take.Recording) LaunchedEffect(take) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(200) } }
                Button(
                    onClick = tap, modifier = Modifier.size(132.dp).testTag("record"), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = if (recording) Sindoor else Marigold, contentColor = if (recording) Color.White else Ink),
                ) {
                    if (recording) Box(Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(Color.White))
                    else Icon(painterResource(R.drawable.ic_mic), "Record", modifier = Modifier.size(52.dp))
                }
                if (take is Take.Recording) {
                    val secs = ((now - take.startedAt) / 1000).coerceAtLeast(0)
                    Text("Recording… 0:%02d  Tap to stop".format(secs), color = Sindoor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth(0.7f).height(8.dp), color = Leaf)
                    if (secs >= 25) Text("Stops by itself at 30 seconds", color = InkSoft, fontSize = 13.sp)
                } else {
                    Text("Tap, let them speak, tap again", color = InkSoft, fontSize = 16.sp)
                }
                if (take is Take.Failed) Text(take.message, color = Sindoor, textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordScreen(vm: RecorderViewModel, id: String, index: Int) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val s = sessions.firstOrNull { it.id == id } ?: return
    val take by vm.take.collectAsStateWithLifecycle()
    val promptId = s.planned.getOrNull(index) ?: return
    val p = vm.promptFor(s, promptId)
    val said by vm.said.collectAsStateWithLifecycle()
    var place by rememberSaveable(id) { mutableStateOf(s.recordings.lastOrNull()?.place ?: s.place) }
    var showSentence by rememberSaveable(promptId) { mutableStateOf(false) }
    val saved = s.recordingFor(promptId)
    // Every way out asks first if a recording is not saved yet.
    val leaveTo: (() -> Unit) -> Unit = { go -> vm.leave(id, index, place, go) }
    BackHandler { leaveTo { vm.home() } }
    TopBar("${langName(s.lang)} · ${s.speaker}", "Question ${index + 1} of ${s.planned.size} · $promptId · ${s.recordings.size} saved", onBack = { leaveTo { vm.home() } })
    LinearProgressIndicator(progress = { s.recordings.size / s.planned.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Leaf, trackColor = InkWash)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((code, text) in places) FilterChip(selected = place == code, onClick = { place = code }, label = { Text(text, fontSize = 13.sp) })
        }
        Card {
            if (p == null) {
                Text("Question $promptId is missing (the list was removed or changed). Skip it.", color = Sindoor)
            } else if (s.set == SET_FREE) {
                Text("Recording ${index + 1}", color = InkSoft, fontSize = 14.sp)
                Text("Let them say anything about their work or money: what they earned, spent, took as advance, or a question for the app.", fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
            } else if (s.mode == MODE_READ || showSentence) {
                if (s.mode == MODE_READ) Text("They read:", color = InkSoft, fontSize = 14.sp)
                Text(p.text, style = TextStyle(fontFamily = fontFor(Lang.fromCode(s.lang))), fontSize = 26.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
                if (s.lang != "en") Text(p.english, color = InkSoft, fontSize = 14.sp)
            } else {
                Text("They ask, in their own words:", color = InkSoft, fontSize = 14.sp)
                Text(p.english, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("(The app should answer: ${p.answer})", color = InkSoft, fontSize = 14.sp)
                TextButton(onClick = { showSentence = true }) { Text("Show an example sentence") }
            }
        }
        if (saved != null && take is Take.Idle) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓ Already recorded. Record again to replace it.", color = Leaf, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.playSaved(id, promptId) }) { Text("Play") }
            }
        }
        if (s.set == SET_FREE || s.mode == MODE_OWN) {
            OutlinedTextField(
                value = said, onValueChange = { vm.said.value = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("What they said (optional, any script)") }, textStyle = TextStyle(fontFamily = fontFor(Lang.fromCode(s.lang)), fontSize = 18.sp),
            )
        }
        RecordControls(vm, take, saveLabel = "Save & next") { vm.saveAndNext(id, index, place) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { leaveTo { vm.back(id, index) } }, enabled = index > 0) { Text("← Back") }
            TextButton(onClick = { leaveTo { vm.skip(id, index) } }) { Text("Skip →") }
            TextButton(onClick = { leaveTo { vm.finish(id) } }) { Text("Finish") }
        }
    }
}

// ---------------------------------------------------------------- finished

@Composable
fun DoneScreen(vm: RecorderViewModel, id: String, share: (File) -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val s = sessions.firstOrNull { it.id == id } ?: return
    val weak = s.recordings.count { it.quality == "quiet" || it.quality == "silent" || it.quality == "loud" }
    TopBar("Done", "${langName(s.lang)} · ${s.speaker}", onBack = vm::home)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("✓ ${s.recordings.size} of ${s.planned.size} questions recorded", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (weak > 0) Text("$weak of them sound too quiet or too loud. You can go back to them with Continue.", color = NoticeInk, fontSize = 15.sp)
        Text("Thank them. Then send the zip to the office computer.", color = InkSoft, fontSize = 16.sp)
        Button(onClick = { vm.export(id, share) }, enabled = s.recordings.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Making the zip…" else "Share zip", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (s.recordings.size < s.planned.size) OutlinedButton(onClick = { vm.continueSession(id) }, modifier = Modifier.fillMaxWidth()) { Text("Record the rest") }
        OutlinedButton(onClick = vm::newSession, modifier = Modifier.fillMaxWidth()) { Text("Next speaker") }
        TextButton(onClick = vm::home) { Text("Home") }
    }
}
