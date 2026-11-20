package com.acxelin.hisaabsaathi.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acxelin.hisaabsaathi.R
import com.acxelin.hisaabsaathi.core.Lang
import com.acxelin.hisaabsaathi.core.Pin
import com.acxelin.hisaabsaathi.core.Profile
import com.acxelin.hisaabsaathi.core.SpokenDetails
import com.acxelin.hisaabsaathi.core.StateInfo
import com.acxelin.hisaabsaathi.core.Strings
import com.acxelin.hisaabsaathi.data.Settings
import com.acxelin.hisaabsaathi.models.Catalog
import com.acxelin.hisaabsaathi.models.PackState
import kotlinx.coroutines.launch
import java.io.File

/**
 * First-time setup:
 * location (optional) → language (picked from the state) → photo (selfie, optional) → your
 * details (Aadhaar QR, or one question per screen, read aloud, answered by typing or by voice)
 * → help improve the app (yes or no) → smart model (optional) → start.
 * The voice packs download in the background from the language screen on.
 */
private enum class Step { LOCATION, LANGUAGE, PHOTO, DETAILS, REVIEW, NAME, AGE, STATE, DISTRICT, PIN, SHARE, MODEL }

/** Setup questions that are read aloud on arrival: (recording name, text). */
private fun prompt(step: Step, s: Strings): Pair<String, String>? = when (step) {
    Step.PHOTO -> "q_photo" to "${s.qPhoto}. ${s.photoHint}"
    Step.DETAILS -> "details_privacy" to s.detailsPrivacy
    Step.REVIEW -> "check_details" to s.checkDetails
    Step.NAME -> "q_name" to s.qName
    Step.AGE -> "q_age" to s.qAge
    Step.STATE -> "q_state" to s.chooseState
    Step.DISTRICT -> "q_district" to s.qDistrict
    Step.PIN -> "q_pin" to s.qPin
    else -> null
}

@Composable
fun Onboarding(vm: AppViewModel, settings: Settings, s: Strings) {
    // The steps visited, so Back retraces the worker's own path (Aadhaar or questions).
    var path by rememberSaveable { mutableStateOf(Step.LOCATION.name) }
    val steps = path.split(',').map { Step.valueOf(it) }
    val step = steps.last()
    fun go(to: Step) { vm.stopSpeaking(); path = "$path,${to.name}" }
    fun back() { vm.stopSpeaking(); if (steps.size > 1) path = steps.dropLast(1).joinToString(",") { it.name } }
    val draft by vm.draft.collectAsStateWithLifecycle()
    // Once the worker taps Download, setup waits on this screen until the smart model is in.
    val allPacks by vm.packStates.collectAsStateWithLifecycle()
    val modelDownloading = step == Step.MODEL && allPacks[vm.slmPack.id] is PackState.Downloading
    BackHandler(enabled = steps.size > 1) { if (!modelDownloading) back() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var finding by remember { mutableStateOf(false) }
    var locationFailed by rememberSaveable { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var qrFailed by rememberSaveable { mutableStateOf(false) }
    // "Help improve HisaabSaathi?": nothing chosen for the worker (kept if setup is shown again).
    var share by rememberSaveable { mutableStateOf(settings.share) }
    val photo by vm.draftPhoto.collectAsStateWithLifecycle()
    val takeSelfie = rememberSelfie(vm, s)

    LaunchedEffect(step, settings.lang) { prompt(step, s)?.let { (key, text) -> vm.speakPrompt(key, text) } }

    fun detectThenLanguage() {
        finding = true
        scope.launch {
            locationFailed = !vm.detectState()
            finding = false
            go(Step.LANGUAGE)
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) detectThenLanguage() else go(Step.LANGUAGE)
    }
    val scan = rememberAadhaarScan(vm, s, onReading = { busy -> reading = busy; if (busy) { qrFailed = false } }) { ok ->
        if (ok) go(Step.REVIEW) else { qrFailed = true }
    }
    if (scan.open) {
        BackHandler { scan.close() }
        QrScannerScreen(s, accept = { com.acxelin.hisaabsaathi.core.AadhaarQr.read(it) != null }, onCode = scan.onCode, onPickPhoto = { scan.close(); scan.pickPhoto() }, onClose = scan.close)
        return
    }
    fun finish() { vm.stopSpeaking(); vm.finishOnboarding(share == true) }

    val dark = step == Step.LOCATION || step == Step.LANGUAGE
    val onBg = if (dark) Color.White else Ink
    // No language is chosen yet on the first screen: it speaks the app's starting language
    // (Hindi), like every other screen, with English underneath for those who read it.
    val en = remember { Strings(Lang.EN) }
    val both = s.lang != Lang.EN
    val head = s
    Column(Modifier.fillMaxSize().background(if (dark) Ink else Chalk).systemBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(head.appName, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = onBg)
        if (!dark) { Spacer(Modifier.height(8.dp)); VoiceProgress(vm, s) }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                Step.LOCATION -> {
                    Icon(Icons.Default.LocationOn, null, tint = Marigold, modifier = Modifier.size(64.dp))
                    Text(s.locationTitle, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(s.locationBody, fontSize = 18.sp, color = Color.White, lineHeight = 26.sp)
                    if (both) {
                        Text(en.locationTitle, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
                        Text(en.locationBody, fontSize = 15.sp, color = Color.White.copy(alpha = 0.75f), lineHeight = 21.sp)
                    }
                    if (finding) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Marigold, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(s.findingState, color = Color.White, fontSize = 17.sp)
                    }
                }
                Step.LANGUAGE -> {
                    val detected by vm.detectedState.collectAsStateWithLifecycle()
                    detected?.let {
                        Text(s.yourState(it), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Marigold)
                        Text(s.languageForState, fontSize = 17.sp, color = Color.White)
                    }
                    if (detected == null && locationFailed) Text(s.locationFailed, fontSize = 16.sp, color = Marigold)
                    Text(s.chooseLanguage, fontSize = 22.sp, color = Color.White)
                    LanguagePicker(vm, settings, onDark = true)
                }
                Step.PHOTO -> {
                    Question(s.qPhoto) { vm.speakPrompt("q_photo", "${s.qPhoto}. ${s.photoHint}") }
                    Text(s.photoHint, fontSize = 17.sp, color = InkSoft, lineHeight = 24.sp)
                    Avatar(photo, 180.dp, Modifier.align(Alignment.CenterHorizontally))
                    Button(
                        onClick = takeSelfie, modifier = Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Marigold, contentColor = Ink),
                    ) {
                        Icon(painterResource(R.drawable.ic_camera), null, tint = Ink, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(if (photo == null) s.openCamera else s.takeAgain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                }
                Step.DETAILS -> {
                    Text(s.detailsTitle, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    PrivacyNote(s, onHear = { vm.speakPrompt("details_privacy", s.detailsPrivacy) })
                    // Two equal choices, like the app's other choices: nothing nudges a worker towards Aadhaar.
                    val choice = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)
                    Button(
                        onClick = { go(Step.NAME) }, enabled = !reading, colors = choice,
                        modifier = Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Edit, null, tint = Ink, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(s.fillMyself, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    Button(
                        onClick = scan.takePhoto, enabled = !reading, colors = choice,
                        modifier = Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_camera), null, tint = Ink, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(s.scanAadhaar, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    Text(s.scanAadhaarHint, fontSize = 15.sp, color = InkSoft)
                    OutlinedButton(onClick = scan.pickPhoto, enabled = !reading, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(10.dp)) {
                        Text(s.choosePhoto, fontSize = 16.sp, color = Ink)
                    }
                    if (reading) CircularProgressIndicator(color = Green)
                    if (qrFailed) Text(s.aadhaarFailed, fontSize = 16.sp, color = Red)
                }
                Step.REVIEW -> {
                    Question(s.checkDetails) { vm.speakPrompt("check_details", s.checkDetails) }
                    DetailsForm(vm, draft, s)
                }
                Step.NAME -> {
                    Question(s.qName) { vm.speakPrompt("q_name", s.qName) }
                    OutlinedTextField(
                        value = draft.name, onValueChange = { v -> vm.editDraft { it.copy(name = v.take(60)) } },
                        label = { Text(s.nameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp),
                    )
                    // "mera naam Aditya hai" → "Aditya": only the name goes in the box.
                    MicAnswer(vm, s) { text -> SpokenDetails.name(text).take(60).also { n -> vm.editDraft { it.copy(name = n) } } }
                }
                Step.AGE -> {
                    Question(s.qAge) { vm.speakPrompt("q_age", s.qAge) }
                    OutlinedTextField(
                        value = draft.age?.toString().orEmpty(),
                        onValueChange = { v -> vm.editDraft { it.copy(age = v.filter(Char::isDigit).take(3).toIntOrNull()) } },
                        label = { Text(s.ageLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp),
                    )
                    MicAnswer(vm, s) { text -> SpokenDetails.age(text)?.also { a -> vm.editDraft { it.copy(age = a) } }?.toString() }
                    if (draft.age?.let { it < 18 } == true) Text(s.adultsOnly, fontSize = 17.sp, color = Red)
                }
                Step.STATE -> {
                    Question(s.chooseState) { vm.speakPrompt("q_state", s.chooseState) }
                    RegionChips(regions(vm), draft.state) { r -> vm.editDraft { it.copy(state = r, district = if (r == it.state) it.district else "") } }
                }
                Step.DISTRICT -> {
                    Question(s.qDistrict) { vm.speakPrompt("q_district", s.qDistrict) }
                    DistrictPicker(vm, draft, s)
                }
                Step.PIN -> {
                    Question(s.qPin) { vm.speakPrompt("q_pin", s.qPin) }
                    PinField(vm, draft, s)
                    // Read back digit by digit: "7 5 1 0 0 2".
                    MicAnswer(vm, s) { text -> SpokenDetails.pin(text)?.also { p -> vm.editDraft { it.copy(pin = p) } }?.toList()?.joinToString(" ") }
                }
                Step.SHARE -> ShareQuestion(vm, s, share) { share = it }
                Step.MODEL -> {
                    Text(s.modelTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (Catalog.slmSupported) {
                        Text(s.modelBody(vm.slmPack.bytes / 1_000_000), fontSize = 17.sp, lineHeight = 24.sp)
                        val states by vm.packStates.collectAsStateWithLifecycle()
                        val slm = states[vm.slmPack.id] ?: PackState.Missing
                        if (slm is PackState.Missing && Catalog.canDownloadSlm) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { vm.download(vm.slmPack) }, modifier = Modifier.weight(1f).height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                                ) { Text(s.download, fontSize = 18.sp) }
                                OutlinedButton(onClick = { finish() }, modifier = Modifier.weight(1f).height(56.dp)) { Text(s.later, fontSize = 18.sp, color = Ink) }
                            }
                        } else {
                            PackRow(vm, vm.slmPack, s, allowDelete = false, canDownload = Catalog.canDownloadSlm)
                            if (modelDownloading) Text(s.waitForModel, fontSize = 16.sp, color = NoticeInk, lineHeight = 22.sp,
                                modifier = Modifier.fillMaxWidth().background(Notice, RoundedCornerShape(10.dp)).padding(12.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val onBack: (() -> Unit)? = if (steps.size > 1 && !finding && !reading) ({ back() }) else null
        when (step) {
            Step.LOCATION -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (granted) detectThenLanguage() else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    enabled = !finding, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Marigold, contentColor = Ink),
                ) { Label2(s.allowLocation, en.allowLocation.takeIf { both }, Ink, 18) }
                OutlinedButton(
                    onClick = { go(Step.LANGUAGE) }, enabled = !finding,
                    modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(10.dp),
                ) { Label2(s.chooseMyself, en.chooseMyself.takeIf { both }, Color.White, 17) }
            }
            Step.LANGUAGE -> SetupButtons(s, onBack, null, s.next, onBackTint = Color.White) { vm.startVoiceDownloads(); go(Step.PHOTO) }
            Step.PHOTO -> SetupButtons(s, onBack, { go(Step.DETAILS) }, s.next, photo != null) { go(Step.DETAILS) }
            Step.DETAILS -> SetupButtons(s, onBack, null, null)
            Step.REVIEW -> SetupButtons(s, onBack, null, s.next) { go(if (draft.state.isEmpty()) Step.STATE else Step.SHARE) }
            Step.NAME -> SetupButtons(s, onBack, { go(Step.AGE) }, s.next, draft.name.isNotBlank()) { go(Step.AGE) }
            Step.AGE -> SetupButtons(s, onBack, { go(Step.STATE) }, s.next, draft.age != null) { go(Step.STATE) }
            Step.STATE -> SetupButtons(s, onBack, null, s.next, draft.state.isNotEmpty()) { go(Step.DISTRICT) }
            Step.DISTRICT -> SetupButtons(s, onBack, { go(Step.PIN) }, s.next, draft.district.isNotBlank()) { go(Step.PIN) }
            Step.PIN -> SetupButtons(s, onBack, { go(Step.SHARE) }, s.next, Pin.valid(draft.pin)) { go(Step.SHARE) }
            Step.SHARE -> SetupButtons(s, onBack, null, s.next, share != null) { go(Step.MODEL) }
            Step.MODEL -> SetupButtons(s, if (modelDownloading) null else onBack, null, s.start, nextEnabled = !modelDownloading) { finish() }
        }
    }
}

private fun regions(vm: AppViewModel): List<String> =
    vm.wagePack.regions.ifEmpty { StateInfo.appRegions + "Other" }

@Composable
private fun SetupButtons(
    s: Strings,
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    nextLabel: String?,
    nextEnabled: Boolean = true,
    onBackTint: Color = Ink,
    onNext: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) OutlinedButton(onClick = onBack, modifier = Modifier.height(52.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, s.cancel, tint = onBackTint)
        }
        if (onSkip != null) TextButton(onClick = onSkip, modifier = Modifier.height(52.dp)) { Text(s.skip, fontSize = 17.sp, color = InkSoft) }
        if (nextLabel != null) {
            Button(
                onClick = onNext, enabled = nextEnabled,
                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Marigold, contentColor = Ink),
            ) { Text(nextLabel, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** A setup question in large type, with a button to hear it again. */
@Composable
private fun Question(text: String, onHear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onHear, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.PlayArrow, text, tint = Green, modifier = Modifier.size(36.dp)) }
    }
}

@Composable
private fun PrivacyNote(s: Strings, onHear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Notice, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Lock, null, tint = NoticeInk, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(s.detailsPrivacy, fontSize = 16.sp, color = NoticeInk, lineHeight = 22.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onHear) { Icon(Icons.Default.PlayArrow, s.hearAgain, tint = NoticeInk) }
    }
}

/**
 * The voice downloads started on the language screen: a small bar on every later setup
 * screen, so the worker knows something is happening.
 */
@Composable
private fun VoiceProgress(vm: AppViewModel, s: Strings) {
    val states by vm.packStates.collectAsStateWithLifecycle()
    val packs = listOfNotNull(vm.sttPack(), vm.voicePack())
    val st = packs.map { states[it.id] ?: PackState.Missing }
    if (st.all { it is PackState.Missing }) return
    val total = packs.sumOf { it.bytes }.toDouble()
    val done = packs.zip(st).sumOf { (p, x) ->
        p.bytes * when (x) { is PackState.Ready -> 1.0; is PackState.Downloading -> x.fraction.toDouble(); else -> 0.0 }
    }
    val f = (done / total).toFloat().coerceIn(0f, 1f)
    val pct = (f * 100).toInt()
    Column(Modifier.fillMaxWidth().background(InkWash, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        when {
            st.all { it is PackState.Ready } -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = Leaf, modifier = Modifier.size(20.dp))
                Text(s.voiceReady, fontSize = 15.sp, color = Leaf, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
            st.any { it is PackState.Failed } -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.downloadFailed, fontSize = 14.sp, color = Red, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.startVoiceDownloads() }) { Text(s.tryAgain) }
            }
            else -> {
                val waiting = st.any { it is PackState.Downloading && it.waiting }
                Text(if (waiting) "${s.waitingForInternet} $pct%" else "🎙️ ${s.gettingVoiceReady(pct)}", fontSize = 15.sp, color = Ink)
                LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Green)
            }
        }
    }
}

/** Big mic button for a spoken answer; works once the speech pack is downloaded. */
@Composable
private fun MicAnswer(vm: AppViewModel, s: Strings, onText: (String) -> String?) {
    val context = LocalContext.current
    val listening by vm.answering.collectAsStateWithLifecycle()
    val states by vm.packStates.collectAsStateWithLifecycle()
    val stt = states[vm.sttPack().id]
    val ready = stt is PackState.Ready
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.listenForAnswer(onText)
    }
    Button(
        onClick = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (granted) vm.listenForAnswer(onText) else permission.launch(Manifest.permission.RECORD_AUDIO)
        },
        enabled = ready, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (listening) Red else Green, contentColor = Color.White),
    ) {
        Icon(painterResource(R.drawable.ic_mic), null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        val label = when {
            listening -> s.listening
            ready -> s.speakAnswer
            stt is PackState.Downloading -> s.gettingVoiceReady((stt.fraction * 100).toInt())
            else -> s.speakAnswer
        }
        Text(label, fontSize = 18.sp, color = Color.White)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionChips(regions: List<String>, selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        regions.forEach { r ->
            FilterChip(selected = selected == r, onClick = { onPick(r) }, label = { Text(r, fontSize = 17.sp) }, modifier = Modifier.height(48.dp))
        }
    }
}

/** Tap a district from the state's list (with a search box), or type one for states without a list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DistrictPicker(vm: AppViewModel, draft: Profile, s: Strings) {
    val list = vm.districts[draft.state]
    if (list == null) {
        OutlinedTextField(
            value = draft.district, onValueChange = { v -> vm.editDraft { it.copy(district = v.take(60)) } },
            label = { Text(s.districtLabel) }, placeholder = { Text(s.typeDistrict) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    var query by remember(draft.state) { mutableStateOf("") }
    OutlinedTextField(
        value = query, onValueChange = { query = it.take(40) },
        label = { Text(s.districtLabel) }, placeholder = { Text(s.typeDistrict) }, singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth(),
    )
    val typed = query.trim()
    val shown = if (typed.isEmpty()) list else list.filter { it.contains(typed, ignoreCase = true) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { d ->
            FilterChip(selected = draft.district == d, onClick = { vm.editDraft { it.copy(district = d) } }, label = { Text(d, fontSize = 16.sp) }, modifier = Modifier.height(44.dp))
        }
        // Not in the list (a new district, another spelling): keep what was typed.
        if (shown.isEmpty() && typed.isNotEmpty()) {
            FilterChip(selected = draft.district == typed, onClick = { vm.editDraft { it.copy(district = typed) } }, label = { Text(typed, fontSize = 16.sp) }, modifier = Modifier.height(44.dp))
        }
    }
}

@Composable
private fun PinField(vm: AppViewModel, draft: Profile, s: Strings) {
    OutlinedTextField(
        value = draft.pin, onValueChange = { v -> vm.editDraft { it.copy(pin = v.filter(Char::isDigit).take(6)) } },
        label = { Text(s.pinLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp),
    )
    when {
        draft.pin.isNotEmpty() && !Pin.valid(draft.pin) -> Text(s.pinSixDigits, fontSize = 15.sp, color = InkSoft)
        Pin.valid(draft.pin) && draft.state.isNotEmpty() && !Pin.fitsState(draft.pin, draft.state) -> Text(s.pinOtherState, fontSize = 15.sp, color = NoticeInk)
    }
}

/** All details on one screen: after an Aadhaar scan, and in Settings → My details. */
@Composable
internal fun DetailsForm(vm: AppViewModel, draft: Profile, s: Strings) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.name, onValueChange = { v -> vm.editDraft { it.copy(name = v.take(60)) } },
            label = { Text(s.nameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.age?.toString().orEmpty(),
            onValueChange = { v -> vm.editDraft { it.copy(age = v.filter(Char::isDigit).take(3).toIntOrNull()) } },
            label = { Text(s.ageLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (draft.age?.let { it < 18 } == true) Text(s.adultsOnly, fontSize = 15.sp, color = Red)
        Text(s.workState, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        RegionChips(regions(vm), draft.state) { r -> vm.editDraft { it.copy(state = r, district = if (r == it.state) it.district else "") } }
        if (draft.district.isNotBlank()) Text("${s.districtLabel}: ${draft.district}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        DistrictPicker(vm, draft, s)
        PinField(vm, draft, s)
    }
}

/**
 * Aadhaar QR → details. [takePhoto] opens the app's own live QR scanner (camera view with four
 * corner brackets; read on the phone); the caller shows [QrScannerScreen] while [open] is true.
 * [pickPhoto] reads a saved image (e-Aadhaar). Nothing but name, age, state, district and PIN is kept.
 */
internal class AadhaarScan(
    val takePhoto: () -> Unit,
    val pickPhoto: () -> Unit,
    val open: Boolean,
    val close: () -> Unit,
    val onCode: (String) -> Unit,
)

@Composable
internal fun rememberAadhaarScan(vm: AppViewModel, s: Strings, onReading: (Boolean) -> Unit, onResult: (Boolean) -> Unit): AadhaarScan {
    var open by rememberSaveable { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            onReading(true)
            vm.readAadhaar(uri, null) { ok -> onReading(false); onResult(ok) }
        }
    }
    val startScanner = rememberWithCamera(s) { open = true }
    return AadhaarScan(
        takePhoto = startScanner,
        pickPhoto = { runCatching { gallery.launch("image/*") } },
        open = open,
        close = { open = false },
        onCode = { raw ->
            open = false
            onReading(true)
            vm.readAadhaarCode(raw) { ok -> onReading(false); onResult(ok) }
        },
    )
}

/** Settings → My details: change the details, or fill them from the Aadhaar QR. */
@Composable
fun DetailsEditScreen(vm: AppViewModel, s: Strings, onDone: () -> Unit) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    var reading by remember { mutableStateOf(false) }
    var qrFailed by remember { mutableStateOf(false) }
    val scan = rememberAadhaarScan(vm, s, onReading = { busy -> reading = busy; if (busy) { qrFailed = false } }) { ok -> qrFailed = !ok }
    if (scan.open) {
        BackHandler { scan.close() }
        QrScannerScreen(s, accept = { com.acxelin.hisaabsaathi.core.AadhaarQr.read(it) != null }, onCode = scan.onCode, onPickPhoto = { scan.close(); scan.pickPhoto() }, onClose = scan.close)
        return
    }
    Column(Modifier.fillMaxSize().background(Ink).systemBarsPadding().background(Chalk).imePadding()) {
        Row(Modifier.fillMaxWidth().background(Ink).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.cancel, tint = Color.White) }
            Text(s.myDetails, color = Color.White, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrivacyNote(s, onHear = { vm.speakPrompt("details_privacy", s.detailsPrivacy) })
            val photo by vm.draftPhoto.collectAsStateWithLifecycle()
            val takeSelfie = rememberSelfie(vm, s)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(photo, 96.dp)
                Spacer(Modifier.width(16.dp))
                OutlinedButton(onClick = takeSelfie, modifier = Modifier.height(52.dp)) {
                    Icon(painterResource(R.drawable.ic_camera), null, tint = Ink, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (photo == null) s.openCamera else s.takeAgain, fontSize = 16.sp, color = Ink)
                }
            }
            OutlinedButton(onClick = scan.takePhoto, enabled = !reading, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(painterResource(R.drawable.ic_camera), null, tint = Ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(s.scanAadhaar, fontSize = 17.sp, color = Ink)
            }
            TextButton(onClick = scan.pickPhoto, enabled = !reading) { Text(s.choosePhoto) }
            if (reading) CircularProgressIndicator(color = Green)
            if (qrFailed) Text(s.aadhaarFailed, fontSize = 15.sp, color = Red)
            DetailsForm(vm, draft, s)
        }
        Button(
            onClick = { vm.saveDraft(); onDone() },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp), shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Marigold, contentColor = Ink),
        ) { Text(s.done, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
    }
}

/** The camera, asking for the front lens (most phones honour it); the photo goes to [AppViewModel.selfieTaken]. */
private class TakeSelfie : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: android.content.Context, input: Uri): android.content.Intent =
        super.createIntent(context, input)
            .putExtra("android.intent.extras.CAMERA_FACING", 1)
            .putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            .putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
}

@Composable
internal fun rememberSelfie(vm: AppViewModel, s: Strings): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(TakeSelfie()) { taken ->
        val path = pending
        pending = null
        if (path != null) { if (taken) vm.selfieTaken(path) else runCatching { File(path).delete() } }
    }
    return rememberWithCamera(s) {
        val file = File(File(context.cacheDir, "scans").apply { mkdirs() }, "selfie-${System.currentTimeMillis()}.jpg")
        pending = file.path
        runCatching { camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file)) }
    }
}

/** The worker's photo in a circle, or a plain person sign when there is none. */
@Composable
internal fun Avatar(jpeg: ByteArray?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val image = remember(jpeg) {
        jpeg?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    val shape = androidx.compose.foundation.shape.CircleShape
    if (image != null) {
        androidx.compose.foundation.Image(
            bitmap = image, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        androidx.compose.foundation.layout.Box(modifier.size(size).clip(shape).background(InkWash), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, tint = InkSoft, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/**
 * The worker's profile, from the photo button on the home screen: photo, name, age, state,
 * district and PIN, all kept only on this phone. Edit changes them (or the photo, or fills them
 * from the Aadhaar QR).
 */
@Composable
fun ProfileScreen(vm: AppViewModel, s: Strings, onBack: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    if (editing) {
        BackHandler { editing = false }
        DetailsEditScreen(vm, s, onDone = { editing = false })
        return
    }
    val p by vm.profile.collectAsStateWithLifecycle()
    val photo by vm.photo.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Ink).systemBarsPadding().background(Chalk)) {
        Row(Modifier.fillMaxWidth().background(Ink).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.done, tint = Color.White) }
            Text(s.profileTitle, color = Color.White, fontSize = 20.sp)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(photo, 150.dp)
            Text(if (p.name.isBlank()) s.notGiven else SpokenDetails.name(p.name), fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Column(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileLine(s.ageLabel, p.age?.toString() ?: s.notGiven)
                ProfileLine(s.workState, p.state.ifBlank { s.notGiven })
                ProfileLine(s.districtLabel, p.district.ifBlank { s.notGiven })
                ProfileLine(s.pinLabel, p.pin.ifBlank { s.notGiven })
            }
            PrivacyNote(s, onHear = { vm.speakPrompt("details_privacy", s.detailsPrivacy) })
        }
        Button(
            onClick = { vm.startDraft(); editing = true },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp), shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Marigold, contentColor = Ink),
        ) {
            Icon(Icons.Default.Edit, null, tint = Ink)
            Spacer(Modifier.width(8.dp))
            Text(s.changeDetails, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, color = InkSoft, modifier = Modifier.weight(1f))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
    }
}

/** A button label in the app's language, with the English underneath when the screen shows both. */
@Composable
private fun Label2(main: String, english: String?, color: Color, size: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(main, fontSize = size.sp, fontWeight = FontWeight.SemiBold, color = color, textAlign = TextAlign.Center, lineHeight = (size + 2).sp)
        english?.let { Text(it, fontSize = 13.sp, color = color.copy(alpha = 0.8f), textAlign = TextAlign.Center, lineHeight = 15.sp) }
    }
}
