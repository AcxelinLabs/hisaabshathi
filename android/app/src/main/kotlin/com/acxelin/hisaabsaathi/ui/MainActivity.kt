package com.acxelin.hisaabsaathi.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acxelin.hisaabsaathi.core.Strings
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars; each screen pads for them and for the keyboard itself.
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { Root(vm) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root(vm: AppViewModel) {
    val settings = vm.settings.collectAsStateWithLifecycle().value ?: return
    HisaabTheme(settings.lang) { Screens(vm, settings) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screens(vm: AppViewModel, settings: com.acxelin.hisaabsaathi.data.Settings) {
    val s = remember(settings.lang) { Strings(settings.lang) }
    val capture by vm.capture.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (!settings.onboarded) {
        Onboarding(vm, settings, s)
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var typing by rememberSaveable { mutableStateOf(false) }

    // Microphone permission, asked the first time the mic is tapped.
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.micTapped()
    }
    val onMic = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.micTapped() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Camera: the photo goes straight into app-private storage.
    var pendingPhoto by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val p = pendingPhoto
        if (taken && p != null) vm.scanned(p.first, p.second)
        pendingPhoto = null
    }
    val onScan = {
        val file = File(File(context.filesDir, "attachments").apply { mkdirs() }, "scan-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        pendingPhoto = uri to file.path
        runCatching { camera.launch(uri) }
        Unit
    }

    val review = capture as? Capture.Review
    if (review != null) {
        BackHandler { vm.dismiss() }
        ReviewScreen(vm, review, s, onSpeakAnswer = onMic)
        return
    }
    if (typing) {
        BackHandler { typing = false }
        TypeScreen(s, onBack = { typing = false }, onSubmit = { typing = false; vm.submitText(it) })
        return
    }
    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(vm, settings, s, onBack = { showSettings = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.appName, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink, titleContentColor = Color.White, actionIconContentColor = Color.White,
                ),
                actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, s.settings) }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Ink) {
                val tabs = listOf(
                    s.tabHome to Icons.Default.Home, s.tabLedger to Icons.AutoMirrored.Filled.List,
                    s.tabSummary to Icons.Default.Star, s.tabWage to Icons.Default.Info,
                )
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, label) }, label = { Text(label, maxLines = 1, color = Color.White, fontSize = 12.sp, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink, indicatorColor = Marigold, unselectedIconColor = Color(0xFFB9C0E0),
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> HomeScreen(vm, settings, s, capture, onMic, onScan, onType = { typing = true })
                1 -> LedgerScreen(vm, s)
                2 -> SummaryScreen(vm, settings, s)
                else -> WageScreen(vm, settings, s)
            }
        }
    }

    // Notification permission for the 3 PM / 9 PM reminders (Android 13+), asked once.
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var asked by rememberSaveable { mutableStateOf(false) }
    if (!asked && Build.VERSION.SDK_INT >= 33 && settings.reminders &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            asked = true
            notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
