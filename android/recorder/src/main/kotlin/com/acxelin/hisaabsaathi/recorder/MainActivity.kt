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
import androidx.compose.foundation.background
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
    private val vm: RecorderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RecorderTheme(Lang.EN) { RecorderUi(vm) } }
    }
}

// Choices the agent taps. The first of each pair is what goes into the dataset.
val genders = listOf("female" to "Woman", "male" to "Man", "other" to "Other")
val ages = listOf("18-25" to "18–25", "26-35" to "26–35", "36-50" to "36–50", "51+" to "51 or more")
val states = listOf("Odisha", "Telangana", "Andhra Pradesh", "Karnataka", "Tamil Nadu", "Bihar", "Uttar Pradesh", "Jharkhand", "West Bengal", "Maharashtra", "Delhi", "Other")
val places = listOf("quiet_room" to "Quiet room", "noisy_room" to "Noisy room", "outdoors" to "Outdoors", "street_market" to "Street / market", "worksite" to "Worksite")
val modes = listOf(MODE_READ to "Reads the sentence", MODE_OWN to "Says it in own words")
val parts = listOf(PART_ALL to "All 300", PART_EVERYDAY to "1–200 everyday", PART_HARD to "201–300 new & hard")

fun label(options: List<Pair<String, String>>, code: String) = options.firstOrNull { it.first == code }?.second ?: code
fun langName(code: String) = Lang.fromCode(code).let { if (it == Lang.EN) "English" else "${it.englishName} (${it.nativeName})" }

@Composable
fun RecorderUi(vm: RecorderViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val share: (File) -> Unit = { zip ->
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
        val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, zip.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(send, "Send ${zip.name}")) }
    }
    Column(Modifier.fillMaxSize().background(Ink).systemBarsPadding().background(MaterialTheme.colorScheme.background)) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(vm, share)
            Screen.NewSession -> { BackHandler { vm.home() }; NewSessionScreen(vm) }
            is Screen.Consent -> { BackHandler { vm.cancelSession(s.id) }; ConsentScreen(vm, s.id) }
            is Screen.Record -> { BackHandler { vm.home() }; RecordScreen(vm, s.id, s.index) }
            is Screen.Done -> { BackHandler { vm.home() }; DoneScreen(vm, s.id, share) }
        }
    }
    message?.let { m ->
        AlertDialog(onDismissRequest = { vm.message.value = null }, text = { Text(m, fontSize = 17.sp) },
            confirmButton = { TextButton(onClick = { vm.message.value = null }) { Text("OK") } })
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
            Text("Recorded on this phone", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        for (s in sessions) {
            Card {
                Text("${langName(s.lang)} · ${s.speaker}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${label(genders, s.gender)} · ${label(ages, s.age)} · ${s.state}${if (s.district.isNotBlank()) ", " + s.district else ""} · ${label(places, s.place)}", color = InkSoft)
                val time = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(s.createdAt))
                Text("$time · ${s.recordings.size} of ${s.planned.size} recorded" + if (s.exportedAt != null) " · shared ✓" else "", fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.recordings.size < s.planned.size) OutlinedButton(onClick = { vm.continueSession(s.id) }) { Text("Continue") }
                    if (s.recordings.isNotEmpty()) Button(onClick = { vm.export(s.id, share) }) {
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
            text = { Text(if (s.exportedAt == null) "These ${s.recordings.size} recordings have not been shared yet. They will be lost." else "Only if the zip has reached the computer.") },
            confirmButton = { TextButton(onClick = { vm.delete(s.id); toDelete = null }) { Text("Delete", color = Sindoor) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- new speaker

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(title: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((code, text) in options) FilterChip(selected = selected == code, onClick = { onPick(code) }, label = { Text(text, fontSize = 16.sp) }, modifier = Modifier.height(44.dp))
    }
}

@Composable
fun NewSessionScreen(vm: RecorderViewModel) {
    val f by vm.form.collectAsStateWithLifecycle()
    val agent by vm.agent.collectAsStateWithLifecycle()
    TopBar("New speaker", "Agent: ${agent.ifBlank { "fill in your name first" }}", onBack = vm::home)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Choices("Language they speak", Lang.entries.map { it.code to (if (it == Lang.EN) "English" else it.nativeName) }, f.lang) { vm.updateForm(f.copy(lang = it)) }
        Choices("Gender", genders, f.gender) { vm.updateForm(f.copy(gender = it)) }
        Choices("Age", ages, f.age) { vm.updateForm(f.copy(age = it)) }
        Choices("State", states.map { it to it }, f.state) { vm.updateForm(f.copy(state = it)) }
        OutlinedTextField(value = f.district, onValueChange = { vm.updateForm(f.copy(district = it)) }, label = { Text("District or town (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Choices("Where are you recording", places, f.place) { vm.updateForm(f.copy(place = it)) }
        Choices("How they ask", modes, f.mode) { vm.updateForm(f.copy(mode = it)) }
        Text(
            if (f.mode == MODE_READ) "They read each question shown on the screen." else "For people who do not read: you explain each question and they ask it the way they would.",
            color = InkSoft, fontSize = 14.sp,
        )
        Choices("Which questions", parts, f.part) { vm.updateForm(f.copy(part = it)) }
        Choices("How many", listOf(10, 25, 50, 100, 300).map { it.toString() to it.toString() }, f.count.toString()) { vm.updateForm(f.copy(count = it.toInt())) }
        OutlinedTextField(
            value = f.startAt.toString(), onValueChange = { v -> v.toIntOrNull()?.let { vm.updateForm(f.copy(startAt = it.coerceIn(1, 300))) } },
            label = { Text("Start at question number") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Each new speaker starts where the last one stopped, so together they cover every question.") },
        )
        Button(onClick = vm::startSession, enabled = f.complete && agent.isNotBlank(), modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(12.dp)) {
            Text("Next: their consent", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (!f.complete || agent.isBlank()) Text("Choose gender, age, state and place" + if (agent.isBlank()) ", and fill in your name on the first screen." else ".", color = InkSoft, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------- consent

@Composable
fun ConsentScreen(vm: RecorderViewModel, id: String) {
    val s = vm.session(id) ?: return
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

@Composable
fun RecordControls(vm: RecorderViewModel, take: Take, saveLabel: String, onSave: () -> Unit) {
    val level by vm.level.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.tapRecord() }
    val tap = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.tapRecord()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (take) {
            is Take.Recorded -> {
                Text("Recorded ${"%.1f".format(take.pcm.size / 16000.0)} s. Listen, then keep it or record again.", color = InkSoft)
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
    val promptId = s.planned[index]
    val p = vm.promptById[promptId]
    var place by rememberSaveable(id) { mutableStateOf(s.recordings.lastOrNull()?.place ?: s.place) }
    var showSentence by rememberSaveable(promptId) { mutableStateOf(false) }
    val saved = s.recordingFor(promptId)
    TopBar("${langName(s.lang)} · ${s.speaker}", "Question ${index + 1} of ${s.planned.size} · $promptId · ${s.recordings.size} saved", onBack = vm::home)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((code, text) in places) FilterChip(selected = place == code, onClick = { place = code }, label = { Text(text, fontSize = 13.sp) })
        }
        Card {
            if (p == null) {
                Text("Question $promptId is missing from this version of the app.", color = Sindoor)
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
        RecordControls(vm, take, saveLabel = "Save & next") { vm.saveAndNext(id, index, place) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.back(id, index) }, enabled = index > 0) { Text("← Back") }
            TextButton(onClick = { vm.skip(id, index) }) { Text("Skip →") }
            TextButton(onClick = { vm.finish(id) }) { Text("Finish") }
        }
    }
}

// ---------------------------------------------------------------- finished

@Composable
fun DoneScreen(vm: RecorderViewModel, id: String, share: (File) -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val s = sessions.firstOrNull { it.id == id } ?: return
    TopBar("Done", "${langName(s.lang)} · ${s.speaker}", onBack = vm::home)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("✓ ${s.recordings.size} of ${s.planned.size} questions recorded", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Thank them. Then send the zip to the office computer.", color = InkSoft, fontSize = 16.sp)
        Button(onClick = { vm.export(id, share) }, enabled = s.recordings.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share zip", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (s.recordings.size < s.planned.size) OutlinedButton(onClick = { vm.continueSession(id) }, modifier = Modifier.fillMaxWidth()) { Text("Record the rest") }
        OutlinedButton(onClick = vm::newSession, modifier = Modifier.fillMaxWidth()) { Text("Next speaker") }
        TextButton(onClick = vm::home) { Text("Home") }
    }
}
