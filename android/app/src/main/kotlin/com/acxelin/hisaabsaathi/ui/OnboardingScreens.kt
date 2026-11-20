package com.acxelin.hisaabsaathi.ui

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import com.acxelin.hisaabsaathi.core.PinDirectory
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.acxelin.hisaabsaathi.core.Strings
import com.acxelin.hisaabsaathi.data.Settings
import com.acxelin.hisaabsaathi.models.Catalog
import com.acxelin.hisaabsaathi.models.PackState
import kotlinx.coroutines.launch
import java.io.File

/**
 * Setup, as short as it can be: language → name → PIN code (which fills in state and
 * district) → Home. Name and PIN can be skipped, typed or said, or read from an Aadhaar QR.
 * Everything else waits: age and photo are on the profile page; the speech packs download in
 * the background from the language screen on, the smart model on Wi-Fi; "Help improve" is asked
 * once on Home after a few days of use, never during setup.
 */
private enum class Step { LANGUAGE, NAME, PLACE }

/** Setup questions that are read aloud on arrival: (recording name, text). */
private fun prompt(step: Step, s: Strings): Pair<String, String>? = when (step) {
    Step.NAME -> "q_name" to s.qName
    Step.PLACE -> "q_pin" to s.qPin
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Onboarding(vm: AppViewModel, settings: Settings, s: Strings) {
    var stepName by rememberSaveable { mutableStateOf(Step.LANGUAGE.name) }
    val step = Step.valueOf(stepName)
    fun go(to: Step) { vm.stopSpeaking(); stepName = to.name }
    fun back() { vm.stopSpeaking(); if (step.ordinal > 0) stepName = Step.entries[step.ordinal - 1].name }
    val draft by vm.draft.collectAsStateWithLifecycle()
    BackHandler(enabled = step != Step.LANGUAGE) { back() }

    var reading by remember { mutableStateOf(false) }
    var qrFailed by rememberSaveable { mutableStateOf(false) }

    // A new phone starts from its own language, if the app has it (the worker still chooses);
    // setup shown again after an update starts from the details already saved.
    LaunchedEffect(Unit) { vm.suggestPhoneLanguage(); if (settings.onboarded) vm.startDraft() }
    LaunchedEffect(step, settings.lang) { prompt(step, s)?.let { (key, text) -> vm.speakPrompt(key, text) } }

    // An Aadhaar QR fills in name, age and place at once: straight on to the PIN, to check it.
    val scan = rememberAadhaarScan(vm, s, onReading = { busy -> reading = busy; if (busy) { qrFailed = false } }) { ok ->
        if (ok) go(Step.PLACE) else { qrFailed = true }
    }
    if (scan.open) {
        BackHandler { scan.close() }
        QrScannerScreen(s, accept = { com.acxelin.hisaabsaathi.core.AadhaarQr.read(it) != null }, onCode = scan.onCode, onPickPhoto = { scan.close(); scan.pickPhoto() }, onClose = scan.close)
        return
    }
    fun finish() { vm.stopSpeaking(); vm.finishOnboarding() }

    val dark = step == Step.LANGUAGE
    val onBg = if (dark) Color.White else Ink
    Column(Modifier.fillMaxSize().background(if (dark) Ink else Chalk).systemBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.appName, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = onBg, modifier = Modifier.weight(1f))
            // Where the worker is: three dots, the current one filled.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Step.entries.forEach { st ->
                    Box(Modifier.size(if (st == step) 10.dp else 8.dp).background(
                        if (st.ordinal <= step.ordinal) (if (dark) Marigold else Ink) else onBg.copy(alpha = 0.25f), CircleShape,
                    ))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                Step.LANGUAGE -> {
                    // The question in every language, each in its own script: readable before any choice.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Lang.entries.forEach { l ->
                            val on = l == settings.lang
                            Text(Strings(l).chooseLanguage, fontFamily = fontFor(l), fontSize = if (on) 21.sp else 16.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) Color.White else Color.White.copy(alpha = 0.7f))
                        }
                    }
                    LanguagePicker(vm, settings, onDark = true)
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
                    // For those who carry it: one scan fills in the rest too. Quiet, never pushed.
                    TextButton(onClick = scan.takePhoto, enabled = !reading, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_camera), null, tint = Ink, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.fillFromAadhaar, fontSize = 16.sp, color = Ink)
                    }
                    if (reading) CircularProgressIndicator(color = Green)
                    if (qrFailed) Text(s.aadhaarFailed, fontSize = 16.sp, color = Red)
                }
                Step.PLACE -> {
                    Question(s.qPin) { vm.speakPrompt("q_pin", s.qPin) }
                    PlaceFields(vm, draft, s, spoken = true)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val onBack: (() -> Unit)? = if (step != Step.LANGUAGE && !reading) ({ back() }) else null
        when (step) {
            Step.LANGUAGE -> SetupButtons(s, null, null, s.next) { vm.startVoiceDownloads(); go(Step.NAME) }
            Step.NAME -> SetupButtons(s, onBack, { go(Step.PLACE) }, s.next, draft.name.isNotBlank()) { go(Step.PLACE) }
            Step.PLACE -> SetupButtons(s, onBack, { finish() }, s.start, draft.state.isNotEmpty()) { finish() }
        }
    }
}

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
        if (onBack != null) OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = onBack, modifier = Modifier.height(52.dp)) {
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
        IconButton(onClick = onHear, modifier = Modifier.size(56.dp)) { Icon(painterResource(R.drawable.ic_speaker), text, tint = Ink, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun PrivacyNote(s: Strings, onHear: () -> Unit) {
    // A plain line, not a warning box: it reassures, it does not alarm.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Lock, null, tint = InkSoft, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(s.detailsPrivacy, fontSize = 15.sp, color = InkSoft, lineHeight = 21.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onHear) { Icon(painterResource(R.drawable.ic_speaker), s.hearAgain, tint = Ink) }
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
    // Until speech works there is nothing to press: no grey button that looks broken.
    if (!ready) return
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

/**
 * A clean dropdown: tap to open the list, type to narrow it, tap to choose. [first] (the few
 * districts a PIN may be in) come at the top, in bold, above a line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickList(label: String, value: String, options: List<String>, s: Strings, first: List<String> = emptyList(), onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    fun close() { open = false; query = ""; focus.clearFocus() }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it; if (!it) query = "" }) {
        OutlinedTextField(
            value = if (open) query else value,
            onValueChange = { query = it.take(40); open = true },
            label = { Text(label) },
            placeholder = { if (value.isNotEmpty()) Text(value, color = InkSoft) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            textStyle = TextStyle(fontSize = 18.sp, color = Ink),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        val typed = query.trim()
        fun matches(o: String) = typed.isEmpty() || o.contains(typed, ignoreCase = true)
        val top = first.filter(::matches)
        val rest = options.filter { it !in first && matches(it) }
        ExposedDropdownMenu(expanded = open, onDismissRequest = { close() }) {
            @Composable
            fun item(o: String, bold: Boolean) = DropdownMenuItem(
                text = { Text(o, fontSize = 17.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = Ink) },
                onClick = { onPick(o); close() },
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
            top.forEach { item(it, bold = true) }
            if (top.isNotEmpty() && rest.isNotEmpty()) HorizontalDivider(color = Rule)
            rest.forEach { item(it, bold = false) }
            if (top.isEmpty() && rest.isEmpty()) {
                DropdownMenuItem(text = { Text(s.noMatch, color = InkSoft) }, onClick = {}, enabled = false)
            }
        }
    }
}

/**
 * Where the worker works, with as little on screen as possible: the PIN box, and one line
 * saying what it found. State and district show only when the PIN does not settle them (not
 * found, several districts) or the worker taps Change or "No PIN?". Never blocks.
 */
@Composable
internal fun PlaceFields(vm: AppViewModel, draft: Profile, s: Strings, spoken: Boolean = false) {
    val result = remember(draft.pin) { vm.pins.lookup(draft.pin) }
    val found = result as? PinDirectory.Result.Found
    var picking by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = draft.pin, onValueChange = vm::setDraftPin,
        label = { Text(s.pinLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(fontSize = 26.sp, color = Ink, letterSpacing = 2.sp),
        isError = draft.pin.length == 6 && found == null,
    )
    // Read back digit by digit: "7 5 1 0 0 2".
    if (spoken) MicAnswer(vm, s) { text -> SpokenDetails.pin(text)?.also { vm.setDraftPin(it) }?.toList()?.joinToString(" ") }
    val settled = found != null && found.state == draft.state && draft.district in found.districts
    when {
        settled -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, null, tint = Leaf, modifier = Modifier.size(24.dp))
            Text("${draft.district}, ${draft.state}", fontSize = 18.sp, color = Leaf, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp).weight(1f))
            if (!picking) TextButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(s.change, fontSize = 16.sp, color = Ink) }
        }
        draft.pin.isEmpty() -> Unit
        found != null && draft.state.isNotEmpty() && found.state != draft.state -> Text(s.pinOtherState, fontSize = 16.sp, color = NoticeInk)
        found != null -> Text(s.pinManyDistricts, fontSize = 16.sp, color = NoticeInk)
        result == PinDirectory.Result.Unknown -> Text(s.pinNotFound, fontSize = 16.sp, color = Red)
        draft.pin.length == 6 -> Text(s.pinBadFormat, fontSize = 16.sp, color = Red)
        else -> Text(s.pinSixDigits, fontSize = 16.sp, color = InkSoft)
    }
    val needLists = picking || (!settled && (draft.pin.length == 6 || draft.state.isNotEmpty()))
    if (needLists) {
        PickList(s.stateLabel, draft.state, vm.pins.states, s) { vm.setDraftState(it) }
        if (draft.state.isNotEmpty()) {
            val likely = found?.takeIf { it.state == draft.state && it.districts.size > 1 }?.districts.orEmpty()
            PickList(s.districtLabel, draft.district, vm.pins.districts(draft.state), s, first = likely) { d -> vm.editDraft { it.copy(district = d) } }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        LocationButton(vm, s)
        if (!needLists && !settled) TextButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(s.noPin, fontSize = 16.sp, color = Ink)
        }
    }
}

/**
 * "Use my location" (optional): asks for approximate location once, then fills in the nearest PIN,
 * state and district on the phone. The location is not kept and never changes the language.
 */
@Composable
private fun LocationButton(vm: AppViewModel, s: Strings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var finding by remember { mutableStateOf(false) }
    var outcome by rememberSaveable { mutableStateOf<Boolean?>(null) } // true: filled in; false: not found
    fun locate() {
        finding = true
        scope.launch { outcome = vm.locatePlace() != null; finding = false }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else outcome = false
    }
    Column {
        TextButton(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) locate() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            enabled = !finding, modifier = Modifier.heightIn(min = 48.dp),
        ) {
            if (finding) CircularProgressIndicator(color = Ink, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else Icon(Icons.Default.LocationOn, null, tint = Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (finding) s.findingPlace else s.useLocation, fontSize = 16.sp, color = Ink)
        }
        when (outcome) {
            true -> Text(s.locationCheckPin, fontSize = 15.sp, color = NoticeInk)
            false -> Text(s.locationNotFound, fontSize = 15.sp, color = NoticeInk)
            null -> Unit
        }
    }
}

@Composable
internal fun DetailsForm(vm: AppViewModel, draft: Profile, s: Strings) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.name, onValueChange = { v -> vm.editDraft { it.copy(name = v.take(60)) } },
            label = { Text(s.nameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontSize = 18.sp, color = Ink),
        )
        OutlinedTextField(
            value = draft.age?.toString().orEmpty(),
            onValueChange = { v -> vm.editDraft { it.copy(age = v.filter(Char::isDigit).take(3).toIntOrNull()) } },
            label = { Text(s.ageLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(fontSize = 18.sp, color = Ink),
        )
        if (draft.age?.let { it < 18 } == true) Text(s.adultsOnly, fontSize = 15.sp, color = Red)
        PlaceFields(vm, draft, s)
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
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = takeSelfie, modifier = Modifier.height(52.dp)) {
                    Icon(painterResource(R.drawable.ic_camera), null, tint = Ink, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (photo == null) s.openCamera else s.takeAgain, fontSize = 16.sp, color = Ink)
                }
            }
            OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = scan.takePhoto, enabled = !reading, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(painterResource(R.drawable.ic_camera), null, tint = Ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(s.scanAadhaar, fontSize = 17.sp, color = Ink)
            }
            OutlinedButton(onClick = scan.pickPhoto, enabled = !reading, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(10.dp)) {
                Text(s.choosePhoto, fontSize = 16.sp, color = Ink)
            }
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
