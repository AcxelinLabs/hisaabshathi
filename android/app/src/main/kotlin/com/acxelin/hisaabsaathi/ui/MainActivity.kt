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
import androidx.compose.foundation.layout.isImeVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acxelin.hisaabsaathi.R
import com.acxelin.hisaabsaathi.core.Strings
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars; each screen pads for them and for the keyboard itself.
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if (intent?.getBooleanExtra(EXTRA_LISTEN, false) == true) vm.requestListen()
        setContent { Root(vm) }
        // Nothing happening for a few seconds (the app silent, not listening, nobody touching):
        // keep what is in progress and put the app away. It comes back where it was.
        val idleMs = (application as com.acxelin.hisaabsaathi.HisaabApp).idleSeconds * 1000L
        if (idleMs > 0) lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                while (true) {
                    kotlinx.coroutines.delay(250)
                    if (vm.idleFor(System.currentTimeMillis(), idleMs)) { vm.wentAway(); moveTaskToBack(true) }
                }
            }
        }
    }

    /** Any touch or key is the worker doing something. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean { vm.touched(); return super.dispatchTouchEvent(ev) }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean { vm.touched(); return super.dispatchKeyEvent(event) }

    /** Back in front: each screen says its guide again. */
    override fun onStart() { super.onStart(); vm.cameBack() }

    /** Leaving the front (put away, the worker left, or the camera opened): stop talking and listening, keep progress. */
    override fun onStop() { super.onStop(); if (!isChangingConfigurations) vm.wentAway() }

    /** Tapped the reminder while the app was open: listen now. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_LISTEN, false)) vm.requestListen()
    }

    companion object {
        /** Set by the reminder: open the app listening for today's work. */
        const val EXTRA_LISTEN = "listen"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root(vm: AppViewModel) {
    val settings = vm.settings.collectAsStateWithLifecycle().value ?: return
    // The welcome, each time the app opens (not again when the phone is turned).
    val app = LocalContext.current.applicationContext as com.acxelin.hisaabsaathi.HisaabApp
    var welcomed by rememberSaveable { mutableStateOf(!app.showWelcome) }
    // While the keyboard is open the worker is typing: never put the app away then.
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val keyboard = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    androidx.compose.runtime.LaunchedEffect(keyboard) { vm.keyboardOpen = keyboard }
    HisaabTheme(settings.lang) {
        if (!welcomed) WelcomeScreen(remember(settings.lang) { Strings(settings.lang) }) { welcomed = true }
        else Screens(vm, settings)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screens(vm: AppViewModel, settings: com.acxelin.hisaabsaathi.data.Settings) {
    val s = remember(settings.lang) { Strings(settings.lang) }
    val capture by vm.capture.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (settings.needsSetup) {
        Onboarding(vm, settings, s)
        return
    }

    // Back where the worker was when the app was last put away.
    val last = settings.lastScreen
    var tab by rememberSaveable { mutableIntStateOf(when (last) { "ledger" -> 1; "summary" -> 2; else -> 0 }) }
    var showSettings by rememberSaveable { mutableStateOf(last == "settings") }
    var showProfile by rememberSaveable { mutableStateOf(last == "profile") }
    var typing by rememberSaveable { mutableStateOf(last == "type") }

    // Microphone permission, asked the first time the mic is tapped.
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.micTapped()
    }
    val onMic = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.micTapped() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    // Opened from the reminder: Home, and listening straight away.
    val listenAsked by vm.listenRequested.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(listenAsked) {
        if (!listenAsked) return@LaunchedEffect
        tab = 0
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.listenFromReminder() else { vm.listenRequested.value = false; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    // The spoken yes/no on the confirm screen needs the microphone too (the entry may have been typed).
    val answerPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.confirmByVoice()
    }
    val onAnswer = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.confirmByVoice() else answerPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Camera: the worker first says what the photo is of; the photo goes straight into
    // app-private storage. Kept across the camera app, even if Android closes this one meanwhile.
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingKind by rememberSaveable { mutableStateOf(com.acxelin.hisaabsaathi.core.PhotoKind.BILL.name) }
    var choosingPhoto by rememberSaveable { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val p = pendingPath
        pendingPath = null
        if (p == null) return@rememberLauncherForActivityResult
        if (taken) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(p))
            vm.photoTaken(uri, p, com.acxelin.hisaabsaathi.core.PhotoKind.valueOf(pendingKind))
        } else File(p).delete()
    }
    // The app declares the camera permission (for the QR scanner), so the camera app needs it granted too.
    val takePhoto = rememberWithCamera(s) {
        val file = File(File(context.filesDir, "attachments").apply { mkdirs() }, "scan-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        pendingPath = file.path
        runCatching { camera.launch(uri) }
        Unit
    }
    val onScan = { choosingPhoto = true }
    // The photo choices are said too: "What is this a photo of? Bill, advance slip, …"
    androidx.compose.runtime.LaunchedEffect(choosingPhoto) {
        if (choosingPhoto) vm.speakGuide(s.photoWhat + " " + com.acxelin.hisaabsaathi.core.PhotoKind.entries.joinToString(", ") { s.photoKindName(it) } + ".")
    }
    if (choosingPhoto) PhotoKindDialog(s, onDismiss = { choosingPhoto = false }) { k ->
        choosingPhoto = false
        pendingKind = k.name
        takePhoto()
    }

    // Each screen says what is on it and which button does what: on arrival, and each time the
    // app comes back to the front. Not when Home is reached from the confirm or typing screen
    // (they have just said their own piece), nor over something already being said or heard.
    val review = capture as? Capture.Review
    val screen = when {
        review != null -> "review"
        typing -> "type"
        showProfile -> "profile"
        showSettings -> "settings"
        else -> listOf("home", "ledger", "summary")[tab]
    }
    androidx.compose.runtime.LaunchedEffect(screen) { if (screen != "review") vm.rememberScreen(screen) }
    val comebacks by vm.comebacks.collectAsStateWithLifecycle()
    val guided = remember { arrayOf("", "-1") } // screen and comeback last guided
    androidx.compose.runtime.LaunchedEffect(screen, comebacks) {
        val (lastScreen, lastBack) = guided
        guided[0] = screen; guided[1] = comebacks.toString()
        val returned = lastBack != comebacks.toString()
        if (!returned && screen == "home" && lastScreen in setOf("review", "type")) return@LaunchedEffect
        if (!returned && screen == "review") return@LaunchedEffect // the read-back says it
        kotlinx.coroutines.delay(700)
        if (!vm.isQuiet()) return@LaunchedEffect
        if (screen != "review" && vm.capture.value != Capture.Idle) return@LaunchedEffect
        vm.speakGuide(when (screen) {
            "review" -> s.guideReview
            "type" -> s.guideType
            "profile" -> s.guideProfile
            "settings" -> s.guideSettings
            "ledger" -> s.guideLedger
            "summary" -> s.guideSummary
            else -> s.guideHome
        })
    }
    if (review != null) {
        BackHandler { vm.dismiss() }
        ReviewScreen(vm, review, s, onSpeakAnswer = onAnswer)
        return
    }
    if (typing) {
        BackHandler { typing = false }
        TypeScreen(s, onBack = { typing = false; vm.typedText = "" }, initial = vm.typedText, onChange = { vm.typedText = it },
            onSubmit = { typing = false; vm.typedText = ""; vm.submitText(it) })
        return
    }
    if (showProfile) {
        BackHandler { showProfile = false }
        ProfileScreen(vm, s, onBack = { showProfile = false })
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
                    // The worker's photo (or a person sign): opens the profile page.
                    val photo by vm.photo.collectAsStateWithLifecycle()
                    IconButton(onClick = { showProfile = true }) {
                        Avatar(photo, 32.dp, Modifier.semantics { contentDescription = s.profileTitle })
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, s.settings) }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Ink) {
                // Pictures that say what is behind each tab: home, the ledger's lines, totals.
                val home = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Home)
                val list = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.AutoMirrored.Filled.List)
                val tabs = listOf(
                    s.tabHome to home, s.tabLedger to list,
                    s.tabSummary to painterResource(R.drawable.ic_summary),
                )
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, label) }, label = { Text(label, maxLines = 2, color = Color.White, fontSize = 11.sp, lineHeight = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink, indicatorColor = Marigold, unselectedIconColor = Color(0xFFB5CDBB),
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> HomeScreen(vm, settings, s, capture, onMic, onScan, onType = { typing = true })
                1 -> LedgerScreen(vm, s, onScan)
                else -> SummaryScreen(vm, settings, s)
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
