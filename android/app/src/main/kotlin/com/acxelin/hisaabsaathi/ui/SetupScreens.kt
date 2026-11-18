package com.acxelin.hisaabsaathi.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
private fun PackRow(vm: AppViewModel, pack: Pack, s: Strings, allowDelete: Boolean = true, canDownload: Boolean = true) {
    val states by vm.packStates.collectAsStateWithLifecycle()
    val state = states[pack.id] ?: PackState.Missing
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(pack.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text("${pack.bytes / 1_000_000} MB · ${pack.licence}", fontSize = 12.sp, color = InkSoft)
        when (state) {
            PackState.Missing -> if (canDownload) Button(onClick = { vm.download(pack) }) { Text(s.download) }
            is PackState.Downloading -> {
                LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Green)
                Text("${s.downloading} ${(state.fraction * 100).toInt()}%")
            }
            PackState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = Leaf)
                Text(s.installed, color = Leaf, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 6.dp))
                if (allowDelete) TextButton(onClick = { vm.deletePack(pack) }) { Text(s.delete, color = Red) }
            }
            is PackState.Failed -> {
                Text(s.downloadFailed, color = Red)
                if (canDownload) Button(onClick = { vm.download(pack) }) { Text(s.download) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionPicker(vm: AppViewModel, settings: Settings) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.wagePack.regions.forEach { r ->
            FilterChip(selected = settings.region == r, onClick = { vm.setRegion(r) }, label = { Text(r, fontSize = 17.sp) }, modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun LanguagePicker(vm: AppViewModel, settings: Settings, onDark: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Lang.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { l ->
                    val selected = settings.lang == l
                    Button(
                        onClick = { vm.setLang(l) }, modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Marigold else if (onDark) InkRaised else Paper,
                            contentColor = if (selected || !onDark) Ink else Color.White,
                        ),
                        // Each name is drawn in its own script's font, whatever the app language is.
                    ) { Text(l.nativeName, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = fontFor(l), color = if (selected || !onDark) Ink else Color.White) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- first-time setup

@Composable
fun Onboarding(vm: AppViewModel, settings: Settings, s: Strings) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(if (step == 0) Ink else Chalk).systemBarsPadding().padding(24.dp)) {
        Text(s.appName, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = if (step == 0) Color.White else Ink)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                0 -> {
                    Text(s.chooseLanguage, fontSize = 22.sp, color = Color.White)
                    LanguagePicker(vm, settings, onDark = true)
                }
                1 -> {
                    Text(s.chooseState, fontSize = 22.sp)
                    RegionPicker(vm, settings)
                }
                else -> {
                    Text(s.voicePackTitle, fontSize = 22.sp)
                    Text(s.voicePackBody, fontSize = 17.sp)
                    PackRow(vm, vm.sttPack(), s, allowDelete = false)
                    vm.voicePack()?.let { Text(s.voicePackReadAloud, fontSize = 15.sp, color = InkSoft); PackRow(vm, it, s, allowDelete = false) }
                    // Optional: the app works without it, using its built-in rules.
                    if (Catalog.slmSupported) {
                        Text(s.smartPack, fontSize = 15.sp, color = InkSoft)
                        PackRow(vm, vm.slmPack, s, allowDelete = false, canDownload = Catalog.canDownloadSlm)
                    }
                    HorizontalDivider()
                    Text(s.privacyTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(s.privacyBody, fontSize = 15.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.height(52.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.cancel, tint = Ink) }
            Button(
                onClick = { if (step < 2) step++ else vm.finishOnboarding() },
                enabled = step != 1 || settings.region.isNotEmpty(),
                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Marigold, contentColor = Ink),
            ) { Text(if (step < 2) s.next else s.start, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
        }
    }
}

// ---------------------------------------------------------------- settings & privacy

@Composable
fun SettingsScreen(vm: AppViewModel, settings: Settings, s: Strings, onBack: () -> Unit) {
    var confirmErase by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Ink).systemBarsPadding().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().background(Ink).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.done, tint = Color.White) }
            Text(s.settings, color = Color.White, fontSize = 20.sp)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(s.language, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            LanguagePicker(vm, settings)
            HorizontalDivider()

            Text(s.workState, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            RegionPicker(vm, settings)
            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.reminders, Modifier.weight(1f), fontSize = 16.sp)
                Switch(checked = settings.reminders, onCheckedChange = { vm.setReminders(it) })
            }
            HorizontalDivider()

            Text(s.modelPacks, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            PackRow(vm, vm.sttPack(), s)
            vm.voicePack()?.let { Text(s.voicePackReadAloud, fontSize = 15.sp, color = InkSoft); PackRow(vm, it, s) }
            if (Catalog.slmSupported) {
                Text(s.smartPack, fontSize = 15.sp, color = InkSoft)
                PackRow(vm, vm.slmPack, s, canDownload = Catalog.canDownloadSlm)
                val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importModel) }
                val states by vm.packStates.collectAsStateWithLifecycle()
                if (states[vm.slmPack.id] !is PackState.Ready && states[vm.slmPack.id] !is PackState.Downloading) {
                    OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.height(52.dp)) { Text(s.addFromPhone) }
                }
            }
            HorizontalDivider()

            val problem by vm.lastProblem.collectAsStateWithLifecycle()
            if (problem.isNotEmpty()) {
                Text(s.lastProblemLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(problem, fontSize = 13.sp, color = InkSoft)
                HorizontalDivider()
            }

            Text(s.improveTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(s.improveBody, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.keepCorrections, Modifier.weight(1f), fontSize = 16.sp)
                Switch(checked = settings.keepCorrections, onCheckedChange = { vm.setKeepCorrections(it) })
            }
            val kept by vm.correctionCount.collectAsStateWithLifecycle()
            Text(s.correctionsCount(kept), fontSize = 15.sp, color = InkSoft)
            if (kept > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { vm.shareCorrections() }, modifier = Modifier.height(52.dp)) { Text(s.shareCorrections) }
                    OutlinedButton(onClick = { vm.deleteCorrections() }, modifier = Modifier.height(52.dp)) { Text(s.deleteCorrections, color = Red) }
                }
            }
            HorizontalDivider()

            Text(s.privacyTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(s.privacyBody, fontSize = 15.sp)
            HorizontalDivider()
            Text("Credits", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(CREDITS, fontSize = 12.sp, color = InkSoft, lineHeight = 17.sp)
            OutlinedButton(onClick = { confirmErase = true }) { Text(s.eraseAll, color = Red) }
            Spacer(Modifier.height(24.dp))
        }
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
Voices: Indic Tiny TTS by Sulabh Katiyar (CC-BY-4.0, huggingface.co/sulabhkatiyar), trained on AI4Bharat Rasa (CC-BY-4.0); vocoder: Vocos by charactr (MIT); runtime: ONNX Runtime (MIT).
Understanding: Qwen2.5-0.5B-Instruct by Alibaba Cloud (Apache-2.0), fine-tuned by Acxelin Labs; runtime: Google LiteRT-LM (Apache-2.0).
Fonts: Anek by EkType (SIL Open Font Licence)."""
