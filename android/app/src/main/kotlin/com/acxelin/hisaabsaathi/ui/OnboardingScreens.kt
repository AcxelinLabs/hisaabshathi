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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.acxelin.hisaabsaathi.core.Confirm
import com.acxelin.hisaabsaathi.core.PlaceMatch
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
 * Setup, as short as it can be: language → name → address (asked, never required) → Home.
 * The address is a short conversation: "Would you like to tell us your address?" → yes →
 * "From your phone's location?" → yes fills it in; no asks state, district, village or town,
 * and the PIN code only if they know it. Everything can be said, typed or skipped, or read
 * from an Aadhaar QR.
 * Everything else waits: age and photo are on the profile page; the speech packs download in
 * the background from the language screen on, the smart model on Wi-Fi; "Help improve" is asked
 * once on Home after a few days of use, never during setup.
 */
private enum class Step { LANGUAGE, NAME, PLACE }

/** The address questions, one at a time. */
private enum class Where { ASK, LOCATION, STATE, DISTRICT, VILLAGE, PIN }

/** Setup questions that are read aloud on arrival: (recording name, text). */
private fun prompt(step: Step, where: Where, s: Strings): Pair<String, String>? = when (step) {
    Step.NAME -> "q_name" to s.qName
    Step.PLACE -> when (where) {
        Where.ASK -> "q_address" to s.qAddress
        Where.LOCATION -> "q_location" to s.qLocation
        Where.STATE -> "q_state" to s.qState
        Where.DISTRICT -> "q_district" to s.qDistrict
        Where.VILLAGE -> "q_village" to s.qVillage
        Where.PIN -> "q_pin_known" to s.qPinIfKnown
    }
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Onboarding(vm: AppViewModel, settings: Settings, s: Strings) {
    var stepName by rememberSaveable { mutableStateOf(Step.LANGUAGE.name) }
    val step = Step.valueOf(stepName)
    var whereName by rememberSaveable { mutableStateOf(Where.ASK.name) }
    val where = Where.valueOf(whereName)
    fun go(to: Step) { vm.stopSpeaking(); stepName = to.name }
    fun goWhere(to: Where) { vm.stopSpeaking(); whereName = to.name }
    fun back() {
        vm.stopSpeaking()
        when {
            step == Step.PLACE && where == Where.VILLAGE && vm.draft.value.state.isEmpty() -> whereName = Where.STATE.name
            step == Step.PLACE && where == Where.STATE -> whereName = Where.LOCATION.name
            step == Step.PLACE && where != Where.ASK -> whereName = Where.entries[where.ordinal - 1].name
            step.ordinal > 0 -> stepName = Step.entries[step.ordinal - 1].name
        }
    }
    val draft by vm.draft.collectAsStateWithLifecycle()
    BackHandler(enabled = step != Step.LANGUAGE) { back() }

    var reading by remember { mutableStateOf(false) }
    var qrFailed by rememberSaveable { mutableStateOf(false) }

    // A new phone starts from its own language, if the app has it (the worker still chooses);
    // setup shown again after an update starts from the details already saved.
    LaunchedEffect(Unit) { vm.suggestPhoneLanguage(); if (settings.onboarded) vm.startDraft() }

    // Voice first: each question is asked aloud and the app then listens for the answer, with the
    // speech pack or, before it is in, the phone's own on-device recogniser. Typing is there too,
    // below. Only if nothing on the phone can listen yet is the question just read; it is asked
    // again, and listened for, once the pack is in.
    val context = LocalContext.current
    val packStates by vm.packStates.collectAsStateWithLifecycle()
    val phoneCantHear by vm.phoneCantHear.collectAsStateWithLifecycle()
    val sttReady = packStates[vm.sttPack().id] is PackState.Ready
    val canHear = remember(settings.lang, sttReady, phoneCantHear) { vm.canHearSetupAnswer() }
    // After an address answer: where the conversation goes next (null: stay on this question).
    var after by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    fun finish() { vm.stopSpeaking(); vm.finishOnboarding() }
    fun locate() {
        locating = true
        scope.launch {
            val found = vm.locatePlace()
            locating = false
            val d = vm.draft.value
            if (found != null && d.state.isNotEmpty()) {
                vm.sayAndWait(s.foundPlace(listOf(d.district.substringBefore('(').trim(), d.state).filter { it.isNotEmpty() }.joinToString(", ")))
                goWhere(Where.VILLAGE)
            } else {
                vm.sayAndWait(s.placeNotFoundAsk)
                goWhere(Where.STATE)
            }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else goWhere(Where.STATE)
    }
    fun useLocation() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) locate() else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    /** After the village or town: the PIN, unless the location already gave it. */
    fun afterVillage() { if (Pin.valid(vm.draft.value.pin)) finish() else goWhere(Where.PIN) }
    fun afterState() { if (vm.draft.value.state.isEmpty()) goWhere(Where.VILLAGE) else goWhere(Where.DISTRICT) }

    /** What to do with a spoken answer: keep what was understood and say it back (null: not understood). */
    fun answer(st: Step, w: Where): (String) -> String? = when {
        // "mera naam Aditya hai" → "Aditya": only the name goes in the box.
        st == Step.NAME -> { text -> SpokenDetails.name(text).take(60).also { n -> vm.editDraft { it.copy(name = n) } } }
        w == Where.ASK -> { text ->
            when (Confirm.parse(text)) {
                true -> { after = { goWhere(Where.LOCATION) }; "" }
                false -> { after = { finish() }; "" }
                null -> null
            }
        }
        w == Where.LOCATION -> { text ->
            when (Confirm.parse(text)) {
                true -> { after = { useLocation() }; "" }
                false -> { after = { goWhere(Where.STATE) }; "" }
                null -> null
            }
        }
        w == Where.STATE -> { text ->
            PlaceMatch.best(text, vm.pins.states)?.also { vm.setDraftState(it); after = { afterState() } }
        }
        w == Where.DISTRICT -> { text ->
            PlaceMatch.best(text, vm.pins.districts(vm.draft.value.state))?.let { d ->
                vm.editDraft { it.copy(district = d) }; after = { goWhere(Where.VILLAGE) }
                d.substringBefore('(').trim()
            }
        }
        w == Where.VILLAGE -> { text ->
            SpokenDetails.place(text).takeIf { it.isNotBlank() }?.also { v -> vm.editDraft { it.copy(village = v) }; after = { afterVillage() } }
        }
        // Read back digit by digit: "7 5 1 0 0 2". "I don't know" is fine too.
        else -> { text ->
            SpokenDetails.pin(text)?.also { vm.setDraftPin(it); after = { finish() } }?.toList()?.joinToString(" ")
                ?: if (Confirm.parse(text) == false) { after = { finish() }; "" } else null
        }
    }
    val then: () -> Unit = { after?.let { a -> after = null; a() } }
    fun ask() { prompt(step, where, s)?.let { (key, text) -> after = null; vm.askAndListen(key, text, answer(step, where), then.takeIf { step == Step.PLACE }) } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) ask() }
    LaunchedEffect(step, where, settings.lang, sttReady) {
        val (key, text) = prompt(step, where, s) ?: return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        when {
            !vm.canHearSetupAnswer() -> vm.speakPrompt(key, text)
            granted -> ask()
            else -> { vm.speakPrompt(key, text); micPermission.launch(Manifest.permission.RECORD_AUDIO) }
        }
    }

    // An Aadhaar QR fills in name, age and place at once: straight on to the last address
    // question, where the place it found is shown, to check it.
    val scan = rememberAadhaarScan(vm, s, onReading = { busy -> reading = busy; if (busy) { qrFailed = false } }) { ok ->
        if (ok) { whereName = (if (vm.draft.value.state.isEmpty()) Where.ASK else Where.PIN).name; go(Step.PLACE) } else { qrFailed = true }
    }
    if (scan.open) {
        BackHandler { scan.close() }
        QrScannerScreen(s, accept = { com.acxelin.hisaabsaathi.core.AadhaarQr.read(it) != null }, onCode = scan.onCode, onPickPhoto = { scan.close(); scan.pickPhoto() }, onClose = scan.close)
        return
    }
    // The language menu is said when the first screen opens.
    LaunchedEffect(step) { if (step == Step.LANGUAGE) vm.playLanguageMenu() }
    // A language was pressed: on to the name once the choice is saved, so the next question is
    // asked in the language just chosen.
    var chosen by remember { mutableStateOf<Lang?>(null) }
    LaunchedEffect(chosen, settings.lang) {
        val l = chosen ?: return@LaunchedEffect
        if (settings.lang == l) { chosen = null; vm.startVoiceDownloads(l); go(Step.NAME) }
    }

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
                    // The question in every language, each in its own script: readable before any
                    // choice. The voice says it too: "हिंदी के लिए एक दबाइए … For English, press six".
                    // The line being said, in its own script, and its button lit: "हिंदी के लिए 1
                    // दबाइए", then Odia, Telugu… When quiet, the question in the chosen language.
                    val speaking by vm.menuSpeaking.collectAsStateWithLifecycle()
                    val shown = speaking ?: settings.lang
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.animation.Crossfade(targetState = shown to (speaking != null), label = "menu line", modifier = Modifier.weight(1f)) { (l, saying) ->
                            Text(if (saying) menuLine(l) else Strings(l).chooseLanguage, fontFamily = fontFor(l), fontSize = 26.sp,
                                fontWeight = FontWeight.Bold, lineHeight = 34.sp, color = Color.White, minLines = 2)
                        }
                        IconButton(onClick = { vm.playLanguageMenu() }, modifier = Modifier.size(56.dp)) {
                            Icon(painterResource(R.drawable.ic_speaker), s.hearAgain, tint = if (speaking != null) Marigold else Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                    // Pressing a number chooses that language and goes straight on.
                    LanguagePicker(vm, settings, onDark = true, numbered = true, lit = speaking) { l -> chosen = l }
                }
                Step.NAME -> {
                    Question(s.qName) { ask() }
                    MicAnswer(vm, s, canHear, answer(Step.NAME, where))
                    OutlinedTextField(
                        value = draft.name, onValueChange = { v -> vm.editDraft { it.copy(name = v.take(60)) } },
                        label = { Text(s.nameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp),
                    )
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
                    // On screen, the question in a few words; the voice says it in full.
                    Question(if (where == Where.PIN) s.qPinShort else prompt(step, where, s)!!.second) { ask() }
                    when (where) {
                        Where.ASK -> Text(s.addressWhy, fontSize = 17.sp, color = InkSoft, lineHeight = 23.sp)
                        Where.PIN -> Text(s.pinNotNeeded, fontSize = 17.sp, color = InkSoft, lineHeight = 23.sp)
                        else -> Unit
                    }
                    MicAnswer(vm, s, canHear, answer(step, where), then, typeHint = where !in setOf(Where.ASK, Where.LOCATION))
                    when (where) {
                        Where.ASK -> YesNo(s.yes, s.no, onYes = { goWhere(Where.LOCATION) }, onNo = { finish() })
                        Where.LOCATION ->
                            if (locating) Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = Ink, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(s.findingPlace, fontSize = 17.sp, color = Ink)
                            } else YesNo(s.yesUseLocation, s.noTellMyself, onYes = { useLocation() }, onNo = { goWhere(Where.STATE) })
                        Where.STATE -> PickList(s.stateLabel, draft.state, vm.pins.states, s) { vm.setDraftState(it) }
                        Where.DISTRICT -> PickList(s.districtLabel, draft.district, vm.pins.districts(draft.state), s) { d -> vm.editDraft { it.copy(district = d) } }
                        Where.VILLAGE -> OutlinedTextField(
                            value = draft.village, onValueChange = { v -> vm.editDraft { it.copy(village = v.take(60)) } },
                            label = { Text(s.villageLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 22.sp),
                        )
                        Where.PIN -> {
                            OutlinedTextField(
                                value = draft.pin, onValueChange = vm::setDraftPin,
                                label = { Text(s.pinLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(fontSize = 26.sp, color = Ink, letterSpacing = 2.sp),
                            )
                            if (draft.state.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, null, tint = Leaf, modifier = Modifier.size(24.dp))
                                Text(listOf(draft.village, draft.district.substringBefore('(').trim(), draft.state).filter { it.isNotBlank() }.joinToString(", "),
                                    fontSize = 18.sp, color = Leaf, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val onBack: (() -> Unit)? = if (step != Step.LANGUAGE && !reading) ({ back() }) else null
        when (step) {
            Step.LANGUAGE -> SetupButtons(s, null, null, s.next) { vm.startVoiceDownloads(); go(Step.NAME) }
            Step.NAME -> SetupButtons(s, onBack, { go(Step.PLACE) }, s.next, draft.name.isNotBlank()) { go(Step.PLACE) }
            Step.PLACE -> when (where) {
                // Yes and No are the buttons on these two.
                Where.ASK, Where.LOCATION -> SetupButtons(s, onBack, null, null)
                Where.STATE -> SetupButtons(s, onBack, { afterState() }, s.next, draft.state.isNotEmpty()) { afterState() }
                Where.DISTRICT -> SetupButtons(s, onBack, { goWhere(Where.VILLAGE) }, s.next, draft.district.isNotEmpty()) { goWhere(Where.VILLAGE) }
                Where.VILLAGE -> SetupButtons(s, onBack, { afterVillage() }, s.next, draft.village.isNotBlank()) { afterVillage() }
                Where.PIN -> SetupButtons(s, onBack, { finish() }.takeUnless { Pin.valid(draft.pin) }, s.start, Pin.valid(draft.pin), skipLabel = s.dontKnow) { finish() }
            }
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
    skipLabel: String = s.skip,
    onNext: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = onBack, modifier = Modifier.height(52.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, s.cancel, tint = onBackTint)
        }
        if (onSkip != null) TextButton(onClick = onSkip, modifier = Modifier.height(52.dp)) { Text(skipLabel, fontSize = 17.sp, color = InkSoft) }
        if (nextLabel != null) {
            Button(
                onClick = onNext, enabled = nextEnabled,
                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Marigold, contentColor = Ink),
            ) {
                // Forward always has an arrow: "आगे बढ़ें →".
                Text(nextLabel, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Ink, modifier = Modifier.size(22.dp))
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** What the spoken menu says for each language, as written: the same words, with its number. */
private fun menuLine(l: Lang): String = when (l) {
    Lang.HI -> "हिंदी के लिए 1 दबाइए"
    Lang.OR -> "ଓଡ଼ିଆ ପାଇଁ 2 ଦବାନ୍ତୁ"
    Lang.TE -> "తెలుగు కోసం 3 నొక్కండి"
    Lang.KN -> "ಕನ್ನಡಕ್ಕಾಗಿ 4 ಒತ್ತಿ"
    Lang.TA -> "தமிழுக்கு 5 அழுத்துங்கள்"
    Lang.EN -> "For English, press 6"
}

/** Two equal answers to a yes-or-no question, the yes in marigold. */
@Composable
private fun YesNo(yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onYes, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Marigold, contentColor = Ink),
        ) { Text(yes, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center) }
        OutlinedButton(onClick = onNo, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(10.dp)) {
            Text(no, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center)
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


/**
 * The main way to answer a setup question: a big round mic, like the one on Home. It listens by
 * itself after the question; a tap listens again (or stops). It shows whenever something on the
 * phone can hear ([canHear]): the speech pack, or the phone's own on-device recogniser. Otherwise,
 * while the pack is still downloading, a quiet line says so; typing works meanwhile.
 */
@Composable
private fun MicAnswer(vm: AppViewModel, s: Strings, canHear: Boolean, onText: (String) -> String?, then: (() -> Unit)? = null, typeHint: Boolean = true) {
    val context = LocalContext.current
    val listening by vm.answering.collectAsStateWithLifecycle()
    val states by vm.packStates.collectAsStateWithLifecycle()
    val stt = states[vm.sttPack().id]
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.listenForAnswer(onText, then)
    }
    when {
        canHear -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(104.dp).clip(CircleShape).background(if (listening) Red else Marigold)
                    .clickable(onClickLabel = s.speakNow) {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (granted) vm.listenForAnswer(onText, then) else permission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_mic), null, tint = if (listening) Color.White else Ink, modifier = Modifier.size(44.dp)) }
            Text(if (listening) s.listening else s.speakNow, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp))
            if (typeHint) Text(s.orType, fontSize = 14.sp, color = InkSoft, modifier = Modifier.padding(top = 12.dp))
        }
        stt is PackState.Downloading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(progress = { stt.fraction }, color = Ink, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(s.gettingVoiceReady((stt.fraction * 100).toInt()), fontSize = 15.sp, color = InkSoft)
        }
        else -> Unit
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
        OutlinedTextField(
            value = draft.village, onValueChange = { v -> vm.editDraft { it.copy(village = v.take(60)) } },
            label = { Text(s.villageLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontSize = 18.sp, color = Ink),
        )
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
                ProfileLine(s.villageLabel, p.village.ifBlank { s.notGiven })
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
