package com.acxelin.hisaabsaathi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
private fun TotalsCard(title: String, t: Totals, s: Strings) {
    Slip {
        Text(title, fontSize = 15.sp, color = InkSoft)
        LeaderRow(s.earned, s.rupees(t.earnedPaise), Leaf)
        LeaderRow(s.advance, s.rupees(t.advancePaise))
        LeaderRow(s.received, s.rupees(t.paidPaise))
        DashedRule()
        LeaderRow(s.due, s.rupees(t.duePaise), if (t.duePaise > 0) Sindoor else Ink, big = true, top = 2.dp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${s.spent} ${s.rupees(t.expensePaise)}", fontSize = 15.sp, color = InkSoft)
            Text("${s.inHand} ${s.rupees(t.inHandPaise)}", fontSize = 15.sp, color = InkSoft)
        }
    }
}

@Composable
private fun BigButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(64.dp), shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = if (color == Marigold) Ink else Color.White),
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
}

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(vm: AppViewModel, settings: Settings, s: Strings, capture: Capture, onMic: () -> Unit, onScan: () -> Unit, onType: () -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(settings.lang.nativeName)
            if (settings.region.isNotEmpty()) Pill(settings.region)
        }
        TotalsCard("${s.today}, ${LocalDate.now().format(dayFormat)}", vm.totals(QueryPeriod.DAY, entries), s)

        (capture as? Capture.Message)?.let {
            Section(color = Color(0xFFFFF8E1)) {
                Text(it.text, fontSize = 18.sp)
                TextButton(onClick = vm::dismiss, modifier = Modifier.align(Alignment.End)) { Text(s.done) }
            }
        }

        val listening = capture == Capture.Listening
        val thinking = capture == Capture.Thinking
        val pulse by animateFloatAsState(if (listening) 1f + level * 0.35f else 1f, label = "mic")
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(188.dp)) {
            // The ring swells with the worker's voice while listening.
            Box(Modifier.size(176.dp).scale(pulse).clip(CircleShape).background(if (listening) Sindoor.copy(alpha = 0.22f) else Marigold.copy(alpha = 0.28f)))
            Box(
                Modifier.size(156.dp).clip(CircleShape).background(if (listening) Sindoor else Marigold)
                    .clickable(enabled = !thinking, onClick = onMic),
                contentAlignment = Alignment.Center,
            ) {
                if (thinking) CircularProgressIndicator(color = Ink)
                else Icon(painterResource(R.drawable.ic_mic), s.tapAndSpeak, tint = if (listening) Color.White else Ink, modifier = Modifier.size(84.dp))
            }
        }
        Text(
            when {
                listening -> "${s.listening}\n${s.tapToStop}"
                thinking -> s.thinking
                else -> s.tapAndSpeak
            },
            fontSize = 20.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
        )
        if (!listening && !thinking) Text(s.typeHint, fontSize = 15.sp, color = InkSoft, textAlign = TextAlign.Center)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(painterResource(R.drawable.ic_camera), null, tint = Ink)
                Spacer(Modifier.width(8.dp))
                Text(s.scanBill)
            }
            OutlinedButton(onClick = onType, modifier = Modifier.weight(1f).height(56.dp)) { Text(s.typeInstead) }
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

@Composable
private fun Pill(text: String) {
    Text(
        text, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Paper).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

// ---------------------------------------------------------------- review (voice confirmation)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(vm: AppViewModel, r: Capture.Review, s: Strings, onSpeakAnswer: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding().padding(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(s.isThisRight, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Section(color = Color(0xFFDDE1F2)) {
                Text(s.youSaid, fontSize = 13.sp, color = InkSoft)
                Text("“${r.transcript}”", fontSize = 17.sp)
            }
            if (r.lowConfidence) {
                Section(color = Color(0xFFFFF8E1)) { Text("⚠ ${s.pleaseCheck}", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            }
            r.drafts.forEachIndexed { i, d ->
                Section {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EntryType.entries.forEach { type ->
                            FilterChip(
                                selected = d.type == type,
                                onClick = {
                                    vm.updateDraft(
                                        i,
                                        d.copy(type = type, category = if (type == EntryType.EXPENSE) d.category ?: ExpenseCategory.OTHER else null),
                                    )
                                },
                                label = { Text("${typeEmoji(type)} ${s.typeName(type)}", fontSize = 16.sp) },
                            )
                        }
                    }
                    val detail = if (d.type == EntryType.WORK) s.workDetail(d.hours, d.pieces, d.ratePaise) else ""
                    if (detail.isNotEmpty()) Text(detail, fontSize = 18.sp)
                    if (d.type == EntryType.EXPENSE) Text(s.categoryName(d.category ?: ExpenseCategory.OTHER), fontSize = 16.sp, color = InkSoft)
                    Spacer(Modifier.height(6.dp))
                    var amount by remember(i, d.amountPaise) { mutableStateOf(if (d.amountPaise == 0L) "" else Money.format(d.amountPaise)) }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { v ->
                            amount = v
                            v.toDoubleOrNull()?.let { vm.updateDraft(i, d.copy(amountPaise = Money.rupeesToPaise(it))) }
                        },
                        label = { Text(s.amountLabel) },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::replay) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(s.playAgain)
                }
                OutlinedButton(onClick = { vm.confirmByVoice() }) {
                    Icon(painterResource(R.drawable.ic_mic), null, tint = Ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp)); Text("${s.yes} / ${s.no}")
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = vm::retry, modifier = Modifier.weight(1f).height(88.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(36.dp))
                    Text(s.fixByVoice, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
            Button(
                onClick = { vm.confirmSave() }, modifier = Modifier.weight(1f).height(88.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(36.dp))
                    Text(s.save, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- ledger

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/** A picture for each kind of line, for workers who do not read easily. */
private fun typeEmoji(type: EntryType): String = when (type) {
    EntryType.WORK -> "🧱"
    EntryType.ADVANCE -> "🤝"
    EntryType.PAYMENT -> "💵"
    EntryType.EXPENSE -> "🛒"
}

private fun typeColor(type: EntryType): Color = when (type) {
    EntryType.WORK -> Leaf
    EntryType.ADVANCE -> Marigold
    EntryType.PAYMENT -> Ink
    EntryType.EXPENSE -> Rule
}

@Composable
fun LedgerScreen(vm: AppViewModel, s: Strings) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Entry?>(null) }
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.noEntries, fontSize = 18.sp, color = InkSoft) }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.groupBy { it.epochDay }.forEach { (day, list) ->
            item(key = "d$day") {
                Text(
                    LocalDate.ofEpochDay(day).format(dayFormat), fontWeight = FontWeight.SemiBold, color = InkSoft,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(list, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Paper).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    // The bar's colour says what kind of line this is.
                    Box(Modifier.width(8.dp).fillMaxHeight().background(typeColor(e.type)))
                    Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                typeEmoji(e.type) + " " + (
                                    if (e.type == EntryType.EXPENSE) "${s.typeName(e.type)}, ${s.categoryName(e.category ?: ExpenseCategory.OTHER)}"
                                    else s.typeName(e.type)
                                    ),
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
        item { Spacer(Modifier.height(16.dp)) }
    }
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QueryPeriod.entries.forEach { p ->
                FilterChip(selected = period == p, onClick = { period = p }, label = { Text(s.periodName(p)) })
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
            Section(color = Color(0xFFFFF8E1)) { Text(it.text, fontSize = 17.sp) }
        }
        BigButton(s.readAloud, Marigold, Modifier.fillMaxWidth()) { vm.speakSummary(period) }
        OutlinedButton(onClick = { vm.exportPdf(period) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(s.exportPdf)
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            Text("⚠ ${s.wageDataNote}", fontSize = 14.sp, color = Color(0xFF8D6E00))
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
            Text(
                if (intact) "✔ ${s.recordIntact}" else "✖ ${s.recordBroken}",
                color = if (intact) Green else Red, fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            BigButton(s.exportEvidence, Ink, Modifier.fillMaxWidth()) { vm.exportEvidence() }
        }
    }
}
