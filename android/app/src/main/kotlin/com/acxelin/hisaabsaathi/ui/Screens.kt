package com.acxelin.hisaabsaathi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acxelin.hisaabsaathi.R
import com.acxelin.hisaabsaathi.core.DraftEntry
import com.acxelin.hisaabsaathi.core.Ledger
import com.acxelin.hisaabsaathi.core.Entry
import com.acxelin.hisaabsaathi.core.EntryType
import com.acxelin.hisaabsaathi.core.ExpenseCategory
import com.acxelin.hisaabsaathi.core.Money
import com.acxelin.hisaabsaathi.core.QueryPeriod
import com.acxelin.hisaabsaathi.core.Strings
import com.acxelin.hisaabsaathi.core.Totals
import com.acxelin.hisaabsaathi.core.Wages
import com.acxelin.hisaabsaathi.data.Settings
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------- shared pieces

@Composable
private fun Section(modifier: Modifier = Modifier, color: Color = Paper, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(color).padding(16.dp)) { content() }
}

/** The slip every screen shares: what was earned, what was taken, what is still owed. */
@Composable
private fun TotalsCard(title: String, t: Totals, s: Strings, compact: Boolean = false) {
    Slip {
        if (title.isNotEmpty()) Text(title, fontSize = 15.sp, color = InkSoft)
        LeaderRow(s.earned, s.rupees(t.earnedPaise), Leaf)
        // On Home, a line that is ₹0 says nothing: only what happened is shown.
        if (!compact || t.advancePaise != 0L) LeaderRow(s.advance, s.rupees(t.advancePaise))
        if (!compact || t.paidPaise != 0L) LeaderRow(s.received, s.rupees(t.paidPaise))
        DashedRule()
        LeaderRow(s.due, s.rupees(t.duePaise), if (t.duePaise > 0) Sindoor else Ink, big = true, top = 2.dp)
        if (!compact) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${s.spent} ${s.rupees(t.expensePaise)}", fontSize = 15.sp, color = InkSoft)
                Text("${s.inHand} ${s.rupees(t.inHandPaise)}", fontSize = 15.sp, color = InkSoft)
            }
            Text("${s.daysWorked}: ${t.daysWorked}", fontSize = 15.sp, color = InkSoft, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun BigButton(text: String, color: Color, modifier: Modifier = Modifier, forward: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = if (color == Marigold) Ink else Color.White),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        // Going on to the next step: an arrow, as everywhere.
        if (forward) { Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(20.dp)) }
    }
}

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(vm: AppViewModel, settings: Settings, s: Strings, capture: Capture, onMic: () -> Unit, onScan: () -> Unit, onType: () -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()

    // Everything fits between the top bar and the bottom bar: on shorter screens the mic and the
    // gaps get smaller instead of the buttons slipping under the bottom bar.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val tight = maxHeight < 700.dp
    val micBig = (maxHeight - 500.dp).coerceIn(92.dp, 156.dp)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = if (tight) 6.dp else 12.dp).widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (tight) 6.dp else 10.dp),
    ) {
        TotalsCard("${s.today}, ${LocalDate.now().format(dayFormat)}", vm.totals(QueryPeriod.DAY, entries), s, compact = true)

        (capture as? Capture.Message)?.let {
            // A short "saved" goes by itself; an answer stays until it is put away.
            if (it.text == s.saved || it.text == s.photoKept) androidx.compose.runtime.LaunchedEffect(it) { kotlinx.coroutines.delay(3_000); vm.dismiss() }
            // The spoken answer, also in writing. Tap anywhere on it to put it away.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Notice).clickable(onClickLabel = s.done, onClick = vm::dismiss).padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    it.asked?.let { q -> Text("“$q”", fontSize = 16.sp, color = NoticeInk, modifier = Modifier.padding(bottom = 4.dp)) }
                    Text(it.text, fontSize = 18.sp, color = NoticeInk, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.Close, s.done, tint = NoticeInk, modifier = Modifier.padding(start = 8.dp).size(26.dp))
            }
        }

        // "Help improve" is on: said once, on the first Home, with an easy way to turn it off.
        val notice by vm.showShareNotice.collectAsStateWithLifecycle()
        val showShare = notice && capture == Capture.Idle
        if (showShare) ShareCard(vm, s)

        val listening = capture == Capture.Listening
        val thinking = capture == Capture.Thinking
        val pulse by animateFloatAsState(if (listening) 1f + level * 0.35f else 1f, label = "mic")
        val compact = capture is Capture.Message || showShare
        val mic = if (compact) minOf(96.dp, micBig) else micBig
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(mic)) {
            // The ring swells with the worker's voice while listening.
            Box(Modifier.size(mic * 0.94f).scale(pulse).clip(CircleShape).background(if (listening) Sindoor.copy(alpha = 0.22f) else Marigold.copy(alpha = 0.28f)))
            Box(
                Modifier.size(mic * 0.81f).clip(CircleShape).background(if (listening) Sindoor else Marigold)
                    .clickable(onClick = if (thinking) vm::cancelThinking else onMic),
                contentAlignment = Alignment.Center,
            ) {
                if (thinking) CircularProgressIndicator(color = Ink)
                else Icon(painterResource(R.drawable.ic_mic), s.tapAndSpeak, tint = if (listening) Color.White else Ink, modifier = Modifier.size(mic * 0.4f))
            }
        }
        Text(
            when {
                listening -> "${s.listening}\n${s.tapToStop}"
                thinking -> s.thinking
                else -> s.tapAndSpeak
            },
            fontSize = if (compact) 17.sp else 19.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
            maxLines = 2, lineHeight = if (compact) 22.sp else 25.sp,
        )
        if (thinking) TextButton(onClick = vm::cancelThinking) { Text(s.cancel, fontSize = 17.sp) }
        // The example is for the first days: once there are lines, the worker knows how.
        if (!listening && !thinking && !compact && !tight && entries.size < 3) Text(s.typeHint, fontSize = 15.sp, color = InkSoft, textAlign = TextAlign.Center)

        // While an answer is showing, the answer takes this space; the buttons return when it is closed.
        if (!compact) {
            // Speaking is the main way; a photo of a paper next; typing is the quiet fallback.
            OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = onScan, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                Icon(painterResource(R.drawable.ic_camera), null, tint = Ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.scanBill, fontSize = 16.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            TextButton(onClick = onType, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(s.typeInstead, fontSize = 16.sp, color = InkSoft)
            }
        }
    }
}
}

/**
 * "Your voice helps improve the app": once, on the first Home, read aloud. What is sent, what
 * never is, and how to stop. "Turn off" and "OK" are equally easy.
 */
@Composable
private fun ShareCard(vm: AppViewModel, s: Strings) {
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.readAloud(s.noticeTitle + ". " + s.noticeLine) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paper).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
        Text(s.noticeTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, end = 12.dp))
        Text(s.noticeLine, fontSize = 15.sp, color = InkSoft, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp, end = 12.dp))
        Row(Modifier.padding(top = 8.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.readAloud(s.noticeTitle + ". " + s.noticeLine) }) { Icon(painterResource(R.drawable.ic_speaker), s.listen, tint = Ink) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { vm.setShare(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(s.noticeOff, color = Ink, fontSize = 16.sp) }
            Button(shape = RoundedCornerShape(10.dp),
                onClick = { vm.shareNoticeOk() }, modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
            ) { Text(s.noticeOk, fontSize = 16.sp) }
        }
    }
}

/**
 * Typing gets a screen of its own: the box sits at the top where the keyboard cannot cover
 * it, the letters are large, and the button stays just above the keyboard.
 */
@Composable
fun TypeScreen(s: Strings, onBack: () -> Unit, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(Chalk).systemBarsPadding().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, s.cancel, tint = Ink) }
            Text(s.typeInstead, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).focusRequester(focus),
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = Ink, fontWeight = FontWeight.SemiBold),
            minLines = 3,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Paper, unfocusedContainerColor = Paper, focusedTextColor = Ink, unfocusedTextColor = Ink,
                focusedBorderColor = Ink, cursorColor = Ink,
            ),
        )
        Text(s.typeHint, fontSize = 16.sp, color = InkSoft)
        Spacer(Modifier.weight(1f))
        BigButton(s.next, Marigold, Modifier.fillMaxWidth(), forward = true) { if (text.isNotBlank()) onSubmit(text) }
    }
}

// ---------------------------------------------------------------- review (voice confirmation)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(vm: AppViewModel, r: Capture.Review, s: Strings, onSpeakAnswer: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding().padding(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.isThisRight, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = vm::replay, modifier = Modifier.size(52.dp)) {
                    Icon(painterResource(R.drawable.ic_speaker), s.playAgain, modifier = Modifier.size(26.dp))
                }
            }
            // What the app heard, in the worker's own words: quiet, so the lines below stand out.
            Text("“${r.transcript}”", fontSize = 17.sp, color = InkSoft, lineHeight = 23.sp)
            if (r.lowConfidence) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Notice).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = NoticeInk)
                    Spacer(Modifier.width(10.dp))
                    Text(s.pleaseCheck, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = NoticeInk)
                }
            }
            r.drafts.forEachIndexed { i, d ->
                // Each line is a short summary; the choices open only when the worker taps "Change"
                // (or when the amount must be typed), so the screen stays calm.
                val mustFix = i in r.invalid || d.amountPaise == 0L
                var open by remember(i, r.drafts.size) { mutableStateOf(mustFix) }
                // Stays open once opened for a fix, so it never folds away while the worker types.
                LaunchedEffect(mustFix) { if (mustFix) open = true }
                if (open || mustFix) Section {
                    DraftEditor(vm, r, i, d, s)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        // A line the app heard wrongly can be taken out, as long as one line is left.
                        if (r.drafts.size > 1) TextButton(onClick = { vm.removeDraft(i) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Default.Delete, null, tint = InkSoft, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp)); Text(s.delete, fontSize = 16.sp, color = InkSoft)
                        }
                        if (!mustFix) TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(s.done, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                    }
                } else {
                    // The whole line is the button: tap to change it.
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Paper)
                            .clickable(onClickLabel = s.change) { open = true }.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) { DraftSummary(d, s) }
                        Icon(Icons.Default.Edit, null, tint = InkSoft, modifier = Modifier.padding(start = 8.dp).size(20.dp))
                    }
                }
            }
            OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = onSpeakAnswer, modifier = Modifier.height(48.dp)) {
                Icon(painterResource(R.drawable.ic_mic), null, tint = Ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp)); Text(s.sayYesNo, fontSize = 16.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = vm::retry, modifier = Modifier.weight(1f).heightIn(min = 64.dp), shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red),
            ) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.fixByVoice, fontSize = 16.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
            Button(
                onClick = { vm.confirmSave() }, enabled = r.canSave, modifier = Modifier.weight(1f).heightIn(min = 64.dp), shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green),
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.save, fontSize = 16.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** One understood line in a few words and a big amount: what the worker checks at a glance. */
@Composable
private fun DraftSummary(d: DraftEntry, s: Strings) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(typeColor(d.type)), contentAlignment = Alignment.Center) {
            Icon(painterResource(typeIcon(d.type)), null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val kind = s.typeName(d.type) + if (d.type == EntryType.EXPENSE) " · " + s.categoryName(d.category ?: ExpenseCategory.OTHER) else ""
            Text(kind, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            val day = when (d.dayOffset) {
                0 -> s.today
                -1 -> s.yesterday
                -2 -> s.dayBefore
                else -> LocalDate.now().plusDays(d.dayOffset.toLong()).format(dayFormat)
            }
            // What was said with it: "समोसा, चाय · नाश्ता", "बाइक लोन".
            val detail = if (d.type == EntryType.WORK) s.workDetail(d.hours, d.pieces, d.ratePaise) else s.describe(d.detail)
            Text(listOf(day, detail).filter { it.isNotEmpty() }.joinToString(" · "), fontSize = 15.sp, color = InkSoft)
        }
        Spacer(Modifier.width(8.dp))
        Text("₹" + Money.formatGrouped(d.amountPaise), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * One choice shown as a single button with what is chosen now; tapping it lists the others.
 * Keeps the screen to a few big buttons instead of a wall of options.
 */
@Composable
private fun <T> Choice(current: T, options: List<T>, name: (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier, icon: ((T) -> Int)? = null) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) {
            icon?.let { Icon(painterResource(it(current)), null, tint = Ink, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
            Text(name(current), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, null, tint = Ink)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(name(o), fontSize = 18.sp, fontWeight = if (o == current) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = icon?.let { f -> { Icon(painterResource(f(o)), null, tint = Ink, modifier = Modifier.size(22.dp)) } },
                    trailingIcon = if (o == current) ({ Icon(Icons.Default.Check, null, tint = Leaf) }) else null,
                    onClick = { open = false; onPick(o) },
                    modifier = Modifier.height(56.dp),
                )
            }
        }
    }
}

/** Every choice for one line: kind, category, day and the amount box. */
@Composable
private fun DraftEditor(vm: AppViewModel, r: Capture.Review, i: Int, d: DraftEntry, s: Strings) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Choice(
                d.type, EntryType.entries, s::typeName, icon = ::typeIcon, modifier = Modifier.weight(1f),
                onPick = { type -> vm.updateDraft(i, d.copy(type = type, category = if (type == EntryType.EXPENSE) d.category ?: ExpenseCategory.OTHER else null)) },
            )
            // A line the app heard wrongly can be taken out, as long as one line is left.
            if (r.drafts.size > 1) IconButton(onClick = { vm.removeDraft(i) }) { Icon(Icons.Default.Delete, s.delete, tint = InkSoft) }
        }
        val detail = if (d.type == EntryType.WORK) s.workDetail(d.hours, d.pieces, d.ratePaise) else ""
        if (detail.isNotEmpty()) Text(detail, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.type == EntryType.EXPENSE) {
                Choice(d.category ?: ExpenseCategory.OTHER, ExpenseCategory.entries, s::categoryName, onPick = { vm.updateDraft(i, d.copy(category = it)) }, modifier = Modifier.weight(1f))
            }
            // Which day: said "yesterday", or forgot to; one tap fixes it.
            val days = listOf(0 to s.today, -1 to s.yesterday, -2 to s.dayBefore)
            Choice(d.dayOffset.coerceIn(-2, 0), days.map { it.first }, { o -> days.first { it.first == o }.second }, onPick = { vm.updateDraft(i, d.copy(dayOffset = it)) }, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(6.dp))
    // Kept as typed; the view model reads it (empty box = 0, a slip blocks saving).
    var amount by remember(i, r.drafts.size) { mutableStateOf(if (d.amountPaise == 0L) "" else Money.format(d.amountPaise)) }
    val bad = i in r.invalid
    OutlinedTextField(
        value = amount,
        onValueChange = { v -> amount = v; vm.updateAmount(i, v) },
        label = { Text(s.amountLabel) },
        isError = bad,
        supportingText = if (bad) ({ Text(s.amountInvalid) }) else null,
        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------- ledger

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/** A drawn picture for each kind of line, for workers who do not read easily. */
private fun typeIcon(type: EntryType): Int = when (type) {
    EntryType.WORK -> R.drawable.ic_work
    EntryType.ADVANCE -> R.drawable.ic_advance
    EntryType.PAYMENT -> R.drawable.ic_payment
    EntryType.EXPENSE -> R.drawable.ic_expense
}

private fun typeColor(type: EntryType): Color = when (type) {
    EntryType.WORK -> Leaf
    EntryType.ADVANCE -> Marigold
    EntryType.PAYMENT -> Ink
    EntryType.EXPENSE -> InkSoft
}

/** A day's heading: "Today", "Yesterday" or the weekday and date, with what was earned, taken and spent. */
@Composable
private fun DayCard(day: Long, today: Long, list: List<Entry>, open: Boolean, s: Strings, onClick: () -> Unit) {
    val t = Ledger.totals(list)
    val date = LocalDate.ofEpochDay(day)
    val loc = com.acxelin.hisaabsaathi.speech.locale(s.lang)
    val name = when (day) {
        today -> s.today
        today - 1 -> s.yesterday
        else -> date.format(DateTimeFormatter.ofPattern("EEEE", loc))
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(InkWash)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            Text(date.format(DateTimeFormatter.ofPattern("d MMM", loc)), fontSize = 15.sp, color = InkSoft, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Ink)
        }
        val parts = listOfNotNull(
            t.earnedPaise.takeIf { it > 0 }?.let { "${s.earned} ${s.rupees(it)}" },
            t.advancePaise.takeIf { it > 0 }?.let { "${s.typeName(EntryType.ADVANCE)} ${s.rupees(it)}" },
            t.paidPaise.takeIf { it > 0 }?.let { "${s.typeName(EntryType.PAYMENT)} ${s.rupees(it)}" },
            t.expensePaise.takeIf { it > 0 }?.let { "${s.spent} ${s.rupees(it)}" },
        )
        if (parts.isNotEmpty()) Text(parts.joinToString("  ·  "), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

@Composable
fun LedgerScreen(vm: AppViewModel, s: Strings, onTakePhoto: () -> Unit = {}) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val deleted by vm.lastDeleted.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Entry?>(null) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var showPhotos by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<AppViewModel.Photo?>(null) }
    if (showPhotos) {
        PhotosScreen(vm, s, onBack = { showPhotos = false }, onTakePhoto = onTakePhoto)
        return
    }
    Box(Modifier.fillMaxSize()) {
    if (entries.isEmpty() && photos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.noEntries, fontSize = 18.sp, color = InkSoft) }
    } else {
    val byDay = remember(entries) { entries.groupBy { it.epochDay } }
    val open = remember { androidx.compose.runtime.mutableStateMapOf<Long, Boolean>() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val today = LocalDate.now().toEpochDay()
        // Photos of bills, slips and papers: one row that opens them all.
        if (photos.isNotEmpty()) item(key = "photos") {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Paper)
                    .clickable { showPhotos = true }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(InkWash), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_camera), null, tint = Ink, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(s.photosTitle, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("${photos.size}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = InkSoft)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = InkSoft)
            }
        }
        byDay.forEach { (day, list) ->
            // One card per day: its name and totals. Tap to see that day's lines (today starts open).
            val isOpen = open[day] ?: (day == today)
            item(key = "d$day") { DayCard(day, today, list, isOpen, s) { open[day] = !isOpen } }
            if (isOpen) items(list, key = { it.id }) { e ->
                // Tap a line to change it.
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Paper).clickable(onClickLabel = s.editEntry) { editing = e }) {
                    Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(typeColor(e.type)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(typeIcon(e.type)), null, tint = if (e.type == EntryType.ADVANCE) Ink else Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (e.type == EntryType.EXPENSE) "${s.typeName(e.type)}, ${s.categoryName(e.category ?: ExpenseCategory.OTHER)}"
                                else s.typeName(e.type),
                                fontSize = 17.sp, fontWeight = FontWeight.Medium,
                            )
                            val detail = if (e.type == EntryType.WORK) s.workDetail(e.hours, e.pieces, e.ratePaise) else s.describe(e.detail)
                            if (detail.isNotEmpty()) Text(detail, color = InkSoft)
                            // From a photo: what it was of (a loan paper is an advance with its paper).
                            com.acxelin.hisaabsaathi.core.PhotoKind.fromSource(e.source)?.let { Text(s.photoKindName(it), color = InkSoft, fontSize = 14.sp) }
                        }
                        photos.firstOrNull { it.entry?.id == e.id }?.let { p ->
                            IconButton(onClick = { viewing = p }) { Icon(painterResource(R.drawable.ic_camera), s.viewPhoto, tint = Ink, modifier = Modifier.size(22.dp)) }
                        }
                        Text(
                            s.rupees(e.amountPaise), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (e.type == EntryType.WORK) Leaf else Ink,
                        )
                        IconButton(onClick = { toDelete = e }) { Icon(Icons.Default.Delete, s.delete, tint = InkSoft) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(if (deleted != null) 88.dp else 16.dp)) }
    }
    }
    // Undo a delete for a few seconds: a wrong tap must not cost the worker a day's record.
    deleted?.let { d ->
        androidx.compose.runtime.LaunchedEffect(d) { kotlinx.coroutines.delay(8_000); vm.forgetDeleted() }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ink).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${s.entryRemoved}: ${s.typeName(d.type)} ${s.rupees(d.amountPaise)}", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::undoDelete) { Text(s.undo, color = Marigold, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
    }
    editing?.let { e -> EditEntryDialog(vm, e, s) { editing = null } }
    viewing?.let { p -> PhotoViewer(p, s, onClose = { viewing = null }, onDelete = null) }
    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(s.deleteEntryQ) },
            text = { Text("${s.typeName(e.type)} ${s.rupees(e.amountPaise)}") },
            confirmButton = { TextButton(onClick = { vm.delete(e.id); toDelete = null }) { Text(s.delete, color = Red) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(s.cancel) } },
        )
    }
}

// ---------------------------------------------------------------- summary & savings goal

@Composable
fun SummaryScreen(vm: AppViewModel, settings: Settings, s: Strings) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val capture by vm.capture.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(QueryPeriod.WEEK) }
    var editingGoal by remember { mutableStateOf(false) }
    val t = vm.totals(period, entries)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Today / week / month / year, like the index tabs of a ledger book: the chosen one underlined.
        Row(Modifier.fillMaxWidth()) {
            QueryPeriod.tabs.forEach { p ->
                val on = period == p
                Column(
                    Modifier.weight(1f).selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) { period = p },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.periodName(p), fontSize = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) Ink else InkSoft, maxLines = 1, modifier = Modifier.padding(vertical = 12.dp))
                    Box(Modifier.fillMaxWidth().height(if (on) 3.dp else 1.dp).background(if (on) Marigold else Rule))
                }
            }
        }
        TotalsCard("", t, s) // the tab above already names the period

        (capture as? Capture.Message)?.let {
            Section(color = Notice) { Text(it.text, fontSize = 18.sp, color = NoticeInk) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(s.readAloud, Marigold, Modifier.weight(1f)) { vm.speakSummary(period) }
            OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { vm.exportPdf(period) }, modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
                Text(s.exportPdf, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }

        // Proof for a wage dispute: the whole record, checked for changes, with its photos.
        Section {
            Text(s.evidenceTitle, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(s.evidenceBody, fontSize = 15.sp, color = InkSoft)
            Spacer(Modifier.height(8.dp))
            val intact = remember(entries) { vm.chainIntact() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (intact) Icons.Default.Check else Icons.Default.Close, null, tint = if (intact) Leaf else Sindoor)
                Spacer(Modifier.width(6.dp))
                Text(if (intact) s.recordIntact else s.recordBroken, color = if (intact) Leaf else Sindoor, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            BigButton(s.exportEvidence, Ink, Modifier.fillMaxWidth()) { vm.exportEvidence() }
        }

        // Savings goal: based on this month's cash in hand, whatever period is shown above.
        // Until one is set, it is a single quiet link, not a card.
        val goal = settings.monthlyGoalPaise
        if (goal <= 0) TextButton(onClick = { editingGoal = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(s.setGoal, fontSize = 16.sp, color = Ink) }
        else Section {
            Text(s.savingsGoal, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            run {
                val saved = vm.totals(QueryPeriod.MONTH, entries).inHandPaise
                LinearProgressIndicator(
                    progress = { (saved.toFloat() / goal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(12.dp).padding(vertical = 2.dp), color = Green,
                )
                Text(s.goalProgress(saved, goal), fontSize = 16.sp)
                if (saved >= goal) {
                    Text(s.goalReached, color = Green, fontWeight = FontWeight.Medium)
                } else {
                    val today = LocalDate.now()
                    // Assume roughly six working days a week for the rest of the month.
                    val workDaysLeft = maxOf(1, (today.lengthOfMonth() - today.dayOfMonth + 1) * 6 / 7)
                    Text(s.goalAdvice((goal - saved) / workDaysLeft), color = InkSoft)
                }
                TextButton(onClick = { editingGoal = true }) { Text(s.setGoal) }
            }
        }
    }
    if (editingGoal) {
        var text by remember { mutableStateOf(if (settings.monthlyGoalPaise > 0) Money.format(settings.monthlyGoalPaise) else "") }
        AlertDialog(
            onDismissRequest = { editingGoal = false },
            title = { Text(s.goalMonthly) },
            text = {
                OutlinedTextField(text, { text = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { vm.setGoal(Money.rupeesToPaise(text.toDoubleOrNull() ?: 0.0)); editingGoal = false }) { Text(s.done) }
            },
            dismissButton = { TextButton(onClick = { editingGoal = false }) { Text(s.cancel) } },
        )
    }
}


/** Change a saved line: kind, amount, day and (for an expense) what it was for. */
@Composable
private fun EditEntryDialog(vm: AppViewModel, e: Entry, s: Strings, onDone: () -> Unit) {
    // A plain dialog (not AlertDialog): its measuring does not get along with chips that wrap.
    androidx.compose.ui.window.Dialog(onDismissRequest = onDone) {
        EditEntryForm(e, s, onCancel = onDone) { type, amount, day, category -> if (vm.editEntry(e, type, amount, day, category)) onDone() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditEntryForm(e: Entry, s: Strings, onCancel: () -> Unit, onSave: (EntryType, String, Long, ExpenseCategory) -> Unit) {
    var type by remember(e.id) { mutableStateOf(e.type) }
    var category by remember(e.id) { mutableStateOf(e.category ?: ExpenseCategory.OTHER) }
    var day by remember(e.id) { mutableStateOf(e.epochDay) }
    var amount by remember(e.id) { mutableStateOf(Money.format(e.amountPaise)) }
    val bad = Money.parseTyped(amount) == null
    val today = LocalDate.now().toEpochDay()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paper).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(s.editEntry, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Choice(type, EntryType.entries, s::typeName, onPick = { type = it }, icon = ::typeIcon)
        if (type == EntryType.EXPENSE) Choice(category, ExpenseCategory.entries, s::categoryName, onPick = { category = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { day -= 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, s.earlierDay, tint = Ink) }
            Text(LocalDate.ofEpochDay(day).format(dayFormat), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { if (day < today) day += 1 }, enabled = day < today) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, s.laterDay, tint = if (day < today) Ink else InkSoft) }
        }
        OutlinedTextField(
            value = amount, onValueChange = { amount = it }, label = { Text(s.amountLabel) },
            isError = bad, supportingText = if (bad) ({ Text(s.amountInvalid) }) else null,
            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(s.cancel, fontSize = 17.sp) }
            TextButton(onClick = { onSave(type, amount, day, category) }, enabled = !bad) { Text(s.done, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
