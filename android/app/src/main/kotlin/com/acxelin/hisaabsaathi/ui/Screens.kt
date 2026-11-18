package com.acxelin.hisaabsaathi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acxelin.hisaabsaathi.R
import com.acxelin.hisaabsaathi.core.DraftEntry
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
        Text(title, fontSize = 15.sp, color = InkSoft)
        LeaderRow(s.earned, s.rupees(t.earnedPaise), Leaf)
        LeaderRow(s.advance, s.rupees(t.advancePaise))
        LeaderRow(s.received, s.rupees(t.paidPaise))
        DashedRule()
        LeaderRow(s.due, s.rupees(t.duePaise), if (t.duePaise > 0) Sindoor else Ink, big = true, top = 2.dp)
        if (!compact) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${s.spent} ${s.rupees(t.expensePaise)}", fontSize = 15.sp, color = InkSoft)
                Text("${s.inHand} ${s.rupees(t.inHandPaise)}", fontSize = 15.sp, color = InkSoft)
            }
        }
    }
}

@Composable
private fun BigButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = if (color == Marigold) Ink else Color.White),
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
}

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(vm: AppViewModel, settings: Settings, s: Strings, capture: Capture, onMic: () -> Unit, onScan: () -> Unit, onType: () -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TotalsCard("${s.today}, ${LocalDate.now().format(dayFormat)}", vm.totals(QueryPeriod.DAY, entries), s, compact = true)

        (capture as? Capture.Message)?.let {
            // The spoken answer, also in writing. Tap anywhere on it to put it away.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Notice).clickable(onClickLabel = s.done, onClick = vm::dismiss).padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    it.asked?.let { q ->
                        Text(s.youSaid, fontSize = 13.sp, color = NoticeInk.copy(alpha = 0.75f))
                        Text("“$q”", fontSize = 16.sp, color = NoticeInk, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text(it.text, fontSize = 18.sp, color = NoticeInk, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.Close, s.done, tint = NoticeInk, modifier = Modifier.padding(start = 8.dp).size(26.dp))
            }
        }

        val listening = capture == Capture.Listening
        val thinking = capture == Capture.Thinking
        val pulse by animateFloatAsState(if (listening) 1f + level * 0.35f else 1f, label = "mic")
        val compact = capture is Capture.Message
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(if (compact) 96.dp else 156.dp)) {
            // The ring swells with the worker's voice while listening.
            Box(Modifier.size(if (compact) 92.dp else 146.dp).scale(pulse).clip(CircleShape).background(if (listening) Sindoor.copy(alpha = 0.22f) else Marigold.copy(alpha = 0.28f)))
            Box(
                Modifier.size(if (compact) 78.dp else 126.dp).clip(CircleShape).background(if (listening) Sindoor else Marigold)
                    .clickable(onClick = if (thinking) vm::cancelThinking else onMic),
                contentAlignment = Alignment.Center,
            ) {
                if (thinking) CircularProgressIndicator(color = Ink)
                else Icon(painterResource(R.drawable.ic_mic), s.tapAndSpeak, tint = if (listening) Color.White else Ink, modifier = Modifier.size(if (compact) 40.dp else 62.dp))
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
        if (!listening && !thinking && !compact) Text(s.typeHint, fontSize = 15.sp, color = InkSoft, textAlign = TextAlign.Center)

        // While an answer is showing, the answer takes this space; the buttons return when it is closed.
        if (!compact) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(painterResource(R.drawable.ic_camera), null, tint = Ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.scanBill, fontSize = 14.sp, lineHeight = 16.sp, maxLines = 2, textAlign = TextAlign.Center)
            }
            OutlinedButton(onClick = onType, modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(s.typeInstead, fontSize = 14.sp, lineHeight = 16.sp, maxLines = 2, textAlign = TextAlign.Center)
            }
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
        BigButton(s.next, Marigold, Modifier.fillMaxWidth()) { if (text.isNotBlank()) onSubmit(text) }
    }
}

// ---------------------------------------------------------------- review (voice confirmation)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(vm: AppViewModel, r: Capture.Review, s: Strings, onSpeakAnswer: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding().padding(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(s.isThisRight, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Section(color = InkWash) {
                Text(s.youSaid, fontSize = 13.sp, color = InkSoft)
                Text("“${r.transcript}”", fontSize = 17.sp)
            }
            if (r.lowConfidence) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Notice).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = NoticeInk)
                    Spacer(Modifier.width(10.dp))
                    Text(s.pleaseCheck, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = NoticeInk)
                }
            }
            r.drafts.forEachIndexed { i, d ->
                Section {
                    // All four kinds stay visible (they wrap), so "expense" is never hidden off screen.
                    Row(verticalAlignment = Alignment.Top) {
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EntryType.entries.forEach { type ->
                            FilterChip(
                                selected = d.type == type,
                                onClick = {
                                    vm.updateDraft(
                                        i,
                                        d.copy(type = type, category = if (type == EntryType.EXPENSE) d.category ?: ExpenseCategory.OTHER else null),
                                    )
                                },
                                label = { Text(s.typeName(type), fontSize = 16.sp) },
                                leadingIcon = { Icon(painterResource(typeIcon(type)), null, tint = Ink, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.height(48.dp),
                            )
                        }
                    }
                    // A line the app heard wrongly can be taken out, as long as one line is left.
                    if (r.drafts.size > 1) IconButton(onClick = { vm.removeDraft(i) }) { Icon(Icons.Default.Delete, s.delete, tint = InkSoft) }
                    }
                    val detail = if (d.type == EntryType.WORK) s.workDetail(d.hours, d.pieces, d.ratePaise) else ""
                    if (detail.isNotEmpty()) Text(detail, fontSize = 18.sp)
                    if (d.type == EntryType.EXPENSE) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExpenseCategory.entries.forEach { c ->
                                FilterChip(
                                    selected = (d.category ?: ExpenseCategory.OTHER) == c, onClick = { vm.updateDraft(i, d.copy(category = c)) },
                                    label = { Text(s.categoryName(c), fontSize = 15.sp) }, modifier = Modifier.height(44.dp),
                                )
                            }
                        }
                    }
                    // Which day: said "yesterday", or forgot to; one tap fixes it.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to s.today, -1 to s.yesterday, -2 to s.dayBefore).forEach { (offset, label) ->
                            FilterChip(
                                selected = d.dayOffset == offset, onClick = { vm.updateDraft(i, d.copy(dayOffset = offset)) },
                                label = { Text(label, fontSize = 15.sp) }, modifier = Modifier.height(44.dp),
                            )
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
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::replay) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(s.playAgain)
                }
                OutlinedButton(onClick = onSpeakAnswer) {
                    Icon(painterResource(R.drawable.ic_mic), null, tint = Ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp)); Text("${s.yes} / ${s.no}")
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = vm::retry, modifier = Modifier.weight(1f).height(68.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(26.dp))
                    Text(s.fixByVoice, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
            Button(
                onClick = { vm.confirmSave() }, enabled = r.canSave, modifier = Modifier.weight(1f).height(68.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(26.dp))
                    Text(s.save, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
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

@Composable
fun LedgerScreen(vm: AppViewModel, s: Strings) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val deleted by vm.lastDeleted.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Entry?>(null) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    Box(Modifier.fillMaxSize()) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.noEntries, fontSize = 18.sp, color = InkSoft) }
    } else {
    val byDay = remember(entries) { entries.groupBy { it.epochDay } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        byDay.forEach { (day, list) ->
            item(key = "d$day") {
                Text(
                    LocalDate.ofEpochDay(day).format(dayFormat), fontWeight = FontWeight.SemiBold, color = InkSoft,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(list, key = { it.id }) { e ->
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
                            val detail = s.workDetail(e.hours, e.pieces, e.ratePaise)
                            if (e.type == EntryType.WORK && detail.isNotEmpty()) Text(detail, color = InkSoft)
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QueryPeriod.entries.forEach { p ->
                FilterChip(selected = period == p, onClick = { period = p }, label = { Text(s.periodName(p), fontSize = 16.sp) }, modifier = Modifier.height(48.dp))
            }
        }
        TotalsCard(s.periodName(period), t, s)

        Section {
            val max = maxOf(t.earnedPaise, t.expensePaise, 1L).toFloat()
            Bar(s.earned, t.earnedPaise / max, Green, s.rupees(t.earnedPaise))
            Spacer(Modifier.height(8.dp))
            Bar(s.spent, t.expensePaise / max, Amber, s.rupees(t.expensePaise))
            Spacer(Modifier.height(8.dp))
            Text("${s.daysWorked}: ${t.daysWorked}", color = InkSoft)
        }

        (capture as? Capture.Message)?.let {
            Section(color = Notice) { Text(it.text, fontSize = 18.sp, color = NoticeInk) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(s.readAloud, Marigold, Modifier.weight(1f)) { vm.speakSummary(period) }
            OutlinedButton(onClick = { vm.exportPdf(period) }, modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
                Text(s.exportPdf, fontSize = 14.sp, lineHeight = 16.sp, maxLines = 2, textAlign = TextAlign.Center)
            }
        }

        // Savings goal: based on this month's cash in hand, whatever period is shown above.
        Section {
            Text(s.savingsGoal, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            val goal = settings.monthlyGoalPaise
            if (goal <= 0) {
                TextButton(onClick = { editingGoal = true }) { Text(s.setGoal) }
            } else {
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

@Composable
private fun Bar(label: String, fraction: Float, color: Color, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(72.dp))
        Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(3.dp)).background(Chalk)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(22.dp).background(color))
        }
        Text(value, Modifier.padding(start = 8.dp), fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- know my wages & dispute vault

@Composable
fun WageScreen(vm: AppViewModel, settings: Settings, s: Strings) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val rates = Wages.forRegion(vm.wagePack, settings.region)
    val floor = Wages.floor(rates)
    val since = LocalDate.now().minusDays(30).toEpochDay()
    val recent = com.acxelin.hisaabsaathi.core.Ledger.totals(entries.filter { it.epochDay >= since })

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Section {
            Text(s.minWageTitle, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            val hasStateRates = rates.any { it.region.equals(settings.region, ignoreCase = true) }
            Text(if (hasStateRates) settings.region else s.centralRates, color = InkSoft)
            Spacer(Modifier.height(8.dp))
            rates.forEach { r ->
                LeaderRow(s.skillName(r.skill), s.rupees(Money.rupeesToPaise(r.dailyRupees)))
                if (r.zone.isNotEmpty()) Text(r.zone, fontSize = 13.sp, color = InkSoft)
            }
            Text(s.perDay, fontSize = 13.sp, color = InkSoft, modifier = Modifier.align(Alignment.End))
            floor?.let {
                Spacer(Modifier.height(8.dp))
                Text(it.sector, fontSize = 13.sp, color = InkSoft)
                Text("${s.effectiveFrom}: ${it.effectiveFrom}", fontSize = 13.sp, color = InkSoft)
                Text("${s.source}: ${it.source}", fontSize = 13.sp, color = InkSoft)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Notice).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = NoticeInk, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.wageDataNote, fontSize = 14.sp, color = NoticeInk)
            }
        }

        Section {
            Text(s.yourAverage, fontSize = 15.sp, color = InkSoft)
            if (recent.daysWorked == 0 || floor == null) {
                Text(s.notEnoughData, fontSize = 17.sp)
            } else {
                Text(s.rupees(recent.avgDailyEarnedPaise), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                val gap = Wages.shortfallPaise(recent.avgDailyEarnedPaise, floor)
                if (gap > 0) Text(s.shortfall(gap), fontSize = 17.sp, color = Red, fontWeight = FontWeight.Medium)
                else Text(s.atOrAbove, fontSize = 17.sp, color = Green, fontWeight = FontWeight.Medium)
            }
        }

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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EntryType.entries.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(s.typeName(t), fontSize = 15.sp) }, modifier = Modifier.height(44.dp))
            }
        }
        if (type == EntryType.EXPENSE) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExpenseCategory.entries.forEach { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(s.categoryName(c), fontSize = 14.sp) }, modifier = Modifier.height(40.dp))
            }
        }
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
