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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.acxelin.hisaabsaathi.models.Pack
import com.acxelin.hisaabsaathi.models.PackState

@Composable
private fun PackRow(vm: AppViewModel, pack: Pack, s: Strings, allowDelete: Boolean = true) {
    val states by vm.packStates.collectAsStateWithLifecycle()
    val state = states[pack.id] ?: PackState.Missing
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(pack.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text("${pack.bytes / 1_000_000} MB · ${pack.licence}", fontSize = 12.sp, color = Color.DarkGray)
        when (state) {
            PackState.Missing -> Button(onClick = { vm.download(pack) }) { Text(s.download) }
            is PackState.Downloading -> {
                LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Green)
                Text("${s.downloading} ${(state.fraction * 100).toInt()}%")
            }
            PackState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✔ ${s.installed}", color = Green, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (allowDelete) TextButton(onClick = { vm.deletePack(pack) }) { Text(s.delete, color = Red) }
            }
            is PackState.Failed -> {
                Text(s.downloadFailed, color = Red)
                Button(onClick = { vm.download(pack) }) { Text(s.download) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionPicker(vm: AppViewModel, settings: Settings) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.wagePack.regions.forEach { r ->
            FilterChip(selected = settings.region == r, onClick = { vm.setRegion(r) }, label = { Text(r, fontSize = 16.sp) })
        }
    }
}

@Composable
private fun LanguagePicker(vm: AppViewModel, settings: Settings) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Lang.entries.forEach { l ->
            val selected = settings.lang == l
            Button(
                onClick = { vm.setLang(l) }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) Green else GreenLight, contentColor = if (selected) Color.White else Green,
                ),
            ) { Text(l.nativeName, fontSize = 24.sp) }
        }
    }
}

// ---------------------------------------------------------------- first-time setup

@Composable
fun Onboarding(vm: AppViewModel, settings: Settings, s: Strings) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
        Text(s.appName, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Green)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                0 -> {
                    Text(s.chooseLanguage, fontSize = 22.sp)
                    LanguagePicker(vm, settings)
                }
                1 -> {
                    Text(s.chooseState, fontSize = 22.sp)
                    RegionPicker(vm, settings)
                }
                else -> {
                    Text(s.voicePackTitle, fontSize = 22.sp)
                    Text(s.voicePackBody, fontSize = 17.sp)
                    PackRow(vm, vm.sttPack(), s, allowDelete = false)
                    HorizontalDivider()
                    Text(s.privacyTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(s.privacyBody, fontSize = 15.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.height(64.dp)) { Text("←", fontSize = 22.sp) }
            Button(
                onClick = { if (step < 2) step++ else vm.finishOnboarding() },
                enabled = step != 1 || settings.region.isNotEmpty(),
                modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(16.dp),
            ) { Text(if (step < 2) s.next else s.start, fontSize = 20.sp) }
        }
    }
}

// ---------------------------------------------------------------- settings & privacy

@Composable
fun SettingsScreen(vm: AppViewModel, settings: Settings, s: Strings, onBack: () -> Unit) {
    var confirmErase by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().background(Green).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Text(s.smartPack, fontSize = 15.sp, color = Color.DarkGray)
            PackRow(vm, vm.slmPack, s)
            HorizontalDivider()

            Text(s.privacyTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(s.privacyBody, fontSize = 15.sp)
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
