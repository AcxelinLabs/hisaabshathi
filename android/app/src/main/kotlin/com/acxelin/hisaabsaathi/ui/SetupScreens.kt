package com.acxelin.hisaabsaathi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acxelin.hisaabsaathi.core.Lang
import com.acxelin.hisaabsaathi.core.Strings
import com.acxelin.hisaabsaathi.data.Settings
import com.acxelin.hisaabsaathi.models.Catalog
import com.acxelin.hisaabsaathi.models.Pack
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.acxelin.hisaabsaathi.models.PackState

@Composable
internal fun PackRow(vm: AppViewModel, pack: Pack, s: Strings, allowDelete: Boolean = true, canDownload: Boolean = true, title: String = pack.title) {
    val states by vm.packStates.collectAsStateWithLifecycle()
    val state = states[pack.id] ?: PackState.Missing
    // What it does, in the worker's words, and its size. Model names and licences are in Credits.
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text("${pack.bytes / 1_000_000} MB", fontSize = 13.sp, color = InkSoft)
        when (state) {
            PackState.Missing -> if (canDownload) OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { vm.download(pack) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(s.download, color = Ink) }
            is PackState.Downloading -> {
                LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Green)
                Text(if (state.waiting) "${s.waitingForInternet} ${(state.fraction * 100).toInt()}%" else "${s.downloading} ${(state.fraction * 100).toInt()}%")
            }
            PackState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = Leaf)
                Text(s.installed, color = Leaf, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 6.dp))
                if (allowDelete) TextButton(onClick = { vm.deletePack(pack) }) { Text(s.delete, color = Red) }
            }
            is PackState.Failed -> {
                Text(state.message.removePrefix("space:").toLongOrNull()?.takeIf { state.message.startsWith("space:") }?.let(s::notEnoughSpace) ?: s.downloadFailed, color = Red)
                if (canDownload) OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { vm.download(pack) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(s.download, color = Ink) }
            }
        }
    }
}

@Composable
internal fun LanguagePicker(vm: AppViewModel, settings: Settings, onDark: Boolean = false, numbered: Boolean = false, lit: Lang? = null, onPicked: (Lang) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Lang.entries.chunked(if (numbered) 1 else 2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { l ->
                    val selected = settings.lang == l
                    val ink = if (selected || !onDark) Ink else Color.White
                    Button(
                        onClick = { vm.setLang(l); onPicked(l) }, shape = RoundedCornerShape(10.dp),
                        // The language the voice is saying now: a marigold ring, a little larger.
                        modifier = Modifier.weight(1f).height(60.dp)
                            .then(if (l == lit) Modifier.border(3.dp, Marigold, RoundedCornerShape(10.dp)) else Modifier),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Marigold else if (l == lit) Marigold.copy(alpha = 0.28f) else if (onDark) InkRaised else Paper,
                            contentColor = ink,
                        ),
                    ) {
                        // Setup says "press 1 for Hindi…": the number the voice says, in a circle.
                        if (numbered) {
                            Box(Modifier.size(36.dp).border(2.dp, ink, CircleShape), contentAlignment = Alignment.Center) {
                                Text("${l.ordinal + 1}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ink)
                            }
                            Spacer(Modifier.width(16.dp))
                        }
                        // Each name is drawn in its own script's font, whatever the app language is.
                        Text(l.nativeName, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = fontFor(l), color = ink,
                            modifier = if (numbered) Modifier.weight(1f) else Modifier)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- help improve the app


/** Workers who set up the app before the question existed answer it once, before anything else. */
// First-time setup is in OnboardingScreens.kt.

// ---------------------------------------------------------------- settings & privacy

@Composable
fun SettingsScreen(vm: AppViewModel, settings: Settings, s: Strings, onBack: () -> Unit) {
    var confirmErase by remember { mutableStateOf(false) }
    var confirmDeleteShared by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().background(Ink).systemBarsPadding().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().background(Ink).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.done, tint = Color.White) }
            Text(s.settings, color = Color.White, fontSize = 20.sp)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(s.language, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            LanguagePicker(vm, settings)
            HorizontalDivider()

            // The same state list as setup and My details; the minimum wage follows the state.
            val profile by vm.profile.collectAsStateWithLifecycle()
            Text(s.workState, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            PickList(s.stateLabel, profile.state.ifEmpty { settings.region.takeIf { it != "Other" }.orEmpty() }, vm.pins.states, s) { vm.setWorkState(it) }
            HorizontalDivider()

            // A clearer voice for this language when it is not on the phone yet. English's is only
            // downloaded when the worker asks: here, one tap.
            val voiceStates by vm.packStates.collectAsStateWithLifecycle()
            vm.voicePack()?.takeIf { voiceStates[it.id] !is PackState.Ready }?.let { p ->
                PackRow(vm, p, s, title = s.voicePackReadAloud)
                HorizontalDivider()
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.reminders, Modifier.weight(1f), fontSize = 16.sp)
                Switch(checked = settings.reminders, onCheckedChange = { vm.setReminders(it) })
            }
            HorizontalDivider()

            // Help improve: one line and a switch; what is sent is in the line, the rest under More.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.improveTitle, fontSize = 16.sp)
                    Text(s.shareCardLine, fontSize = 14.sp, color = InkSoft, lineHeight = 19.sp)
                }
                Switch(checked = settings.share == true, onCheckedChange = { vm.setShare(it) }, modifier = Modifier.padding(start = 12.dp))
            }
            val waiting by vm.waitingCount.collectAsStateWithLifecycle()
            val counts = listOfNotNull(s.shareWaiting(waiting).takeIf { waiting > 0 }, s.shareSent(settings.sharedCount).takeIf { settings.sharedCount > 0 })
            if (counts.isNotEmpty()) Text(counts.joinToString(" · "), fontSize = 15.sp, color = InkSoft)
            if (waiting > 0 || settings.sharedCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (vm.canSend && waiting > 0 && settings.share == true) {
                        OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { vm.sendNow() }, modifier = Modifier.height(52.dp)) { Text(s.sendNow) }
                    }
                    OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { confirmDeleteShared = true }, modifier = Modifier.height(52.dp)) { Text(s.deleteShared, color = Red) }
                }
            }
            HorizontalDivider()

            // Everything a worker rarely needs: one tap away, not on the first screen.
            var more by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { more = !more }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) {
                Text(s.moreSettings, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                Icon(if (more) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Ink)
            }
            if (more) {
                Text(s.modelPacks, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                PackRow(vm, vm.sttPack(), s, title = s.speechPackTitle)
                vm.voicePack()?.takeIf { voiceStates[it.id] is PackState.Ready }?.let { PackRow(vm, it, s, title = s.voicePackReadAloud) }
                if (Catalog.slmSupported) {
                    PackRow(vm, vm.slmPack, s, canDownload = Catalog.canDownloadSlm, title = s.smartPack)
                    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importModel) }
                    val states by vm.packStates.collectAsStateWithLifecycle()
                    if (states[vm.slmPack.id] !is PackState.Ready && states[vm.slmPack.id] !is PackState.Downloading) {
                        TextButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(s.addFromPhone, color = Ink) }
                    }
                }
                HorizontalDivider()

                Text(s.backupTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(s.backupBody, fontSize = 15.sp)
                val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::restoreBackup) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { vm.saveBackup() }, modifier = Modifier.height(52.dp)) { Text(s.saveBackup) }
                    OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }, modifier = Modifier.height(52.dp)) { Text(s.restoreBackup) }
                }
                HorizontalDivider()

                Text(s.privacyTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(s.privacyBody, fontSize = 15.sp)
                Text(s.improveBody, fontSize = 15.sp, color = InkSoft)
                HorizontalDivider()

                val problem by vm.lastProblem.collectAsStateWithLifecycle()
                if (problem.isNotEmpty()) {
                    Text(s.lastProblemLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(problem, fontSize = 13.sp, color = InkSoft)
                    HorizontalDivider()
                }
                Text(s.credits, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(CREDITS, fontSize = 12.sp, color = InkSoft, lineHeight = 17.sp)
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { vm.reopenSetup() }) { Text(s.showSetupAgain) }
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { confirmErase = true }) { Text(s.eraseAll, color = Red) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDeleteShared) {
        AlertDialog(
            onDismissRequest = { confirmDeleteShared = false },
            title = { Text(s.deleteShared) },
            text = { Text(s.deleteSharedQ) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteShared(); confirmDeleteShared = false
                    android.widget.Toast.makeText(context, s.deleteSharedDone, android.widget.Toast.LENGTH_LONG).show()
                }) { Text(s.yes, color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteShared = false }) { Text(s.no) } },
        )
    }
    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text(s.eraseAll) },
            text = { Text(s.eraseAllQ) },
            confirmButton = { TextButton(onClick = { vm.eraseAll(); confirmErase = false }) { Text(s.yes, color = Red) } },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text(s.no) } },
        )
    }
}

/** Attribution the open models and fonts ask for (CC-BY-4.0 requires credit). */
private const val CREDITS = """Speech recognition: AI4Bharat IndicConformer (MIT); English: NVIDIA Parakeet TDT-CTC 110M (CC-BY-4.0, ONNX export by k2-fsa); voice detection: Silero VAD (MIT); runtime: sherpa-onnx (Apache-2.0).
Voices: Indic Tiny TTS by Sulabh Katiyar (CC-BY-4.0, huggingface.co/sulabhkatiyar), trained on AI4Bharat Rasa (CC-BY-4.0); the English voice on the LIMMITS 2024 / SYSPIN corpus, IISc Bangalore SPIRE Lab (CC-BY-4.0); vocoder: Vocos by charactr (MIT); runtime: ONNX Runtime (MIT).
Understanding: Qwen3-0.6B by Alibaba Cloud (Apache-2.0), fine-tuned by Acxelin Labs; runtime: Google LiteRT-LM (Apache-2.0).
Fonts: Anek by EkType (SIL Open Font Licence).
PIN codes, states and districts: India Post, All India Pincode Directory (data.gov.in, Government Open Data Licence – India)."""
