package com.example.budgetapptracker.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.ThemeMode
import com.example.budgetapptracker.security.canUseAppLock
import com.example.budgetapptracker.ui.theme.LocalAppColors
import com.example.budgetapptracker.util.rupeesToPaise
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.budgetapptracker.ui.pay.PayFlow
import com.example.budgetapptracker.ui.pay.PayStart

private val monthTitleFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

private val wholeRupees = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
    .apply { maximumFractionDigits = 0 }

// Summary figures are shown without paise to keep the tiles readable.
private fun Long.toShortRupees(): String = wholeRupees.format(this / 100.0)

// One accent colour per envelope, in the order the envelopes were created.
@Composable
@ReadOnlyComposable
private fun accentFor(index: Int): Color {
    val accents = LocalAppColors.current.accents
    return accents[index % accents.size]
}

private fun iconFor(name: String): ImageVector {
    val text = name.lowercase()
    fun has(vararg words: String) = words.any { it in text }
    return when {
        has("food", "grocer", "dining", "restaurant", "snack", "lunch", "dinner") -> Icons.Rounded.Restaurant
        has("fuel", "petrol", "diesel") -> Icons.Rounded.LocalGasStation
        has("travel", "trip", "flight", "holiday", "vacation") -> Icons.Rounded.Flight
        has("transport", "cab", "metro", "commute", "uber", "ola", "rapido") -> Icons.Rounded.DirectionsBus
        has("shop", "cloth", "amazon", "flipkart") -> Icons.Rounded.ShoppingBag
        has("rent", "home", "house") -> Icons.Rounded.Home
        has("bill", "electric", "recharge", "wifi", "internet", "mobile") -> Icons.Rounded.Receipt
        has("health", "medic", "doctor", "gym", "fitness") -> Icons.Rounded.Favorite
        has("entertain", "movie", "party", "netflix", "subscription") -> Icons.Rounded.Movie
        has("saving", "invest", "sip", "emi", "loan") -> Icons.Rounded.Savings
        has("education", "course", "book", "school", "college") -> Icons.Rounded.School
        else -> Icons.Rounded.AccountBalanceWallet
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun panelColors() =
    CardDefaults.cardColors(containerColor = LocalAppColors.current.panel)

// What the + button in the bottom bar can ask this screen to do.
enum class HomeAction(val label: String) {
    ScanAndPay("Scan and pay"),
    PayUpiId("Pay by UPI ID"),
    AddExpense("Add expense"),
    AddIncome("Add income"),
    NewEnvelope("New envelope"),
}

@Composable
fun HomeScreen(
    action: HomeAction? = null,
    onActionHandled: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val envelopes by viewModel.envelopes.collectAsStateWithLifecycle()
    val incomePaise by viewModel.incomePaise.collectAsStateWithLifecycle()
    val lastSevenDays by viewModel.lastSevenDays.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val lockEnabled by viewModel.lockEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val openEnvelope by viewModel.openEnvelope.collectAsStateWithLifecycle()
    val openTransactions by viewModel.openEnvelopeTransactions.collectAsStateWithLifecycle()
    var showAddEnvelope by rememberSaveable { mutableStateOf(false) }
    var showAddIncome by rememberSaveable { mutableStateOf(false) }
    var showPickEnvelope by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var payStart by remember { mutableStateOf<PayStart?>(null) }
    var expenseTarget by remember { mutableStateOf<EnvelopeSummary?>(null) }
    var budgetTarget by remember { mutableStateOf<EnvelopeSummary?>(null) }

    LaunchedEffect(action) {
        when (action) {
            HomeAction.ScanAndPay -> payStart = PayStart.ScanQr
            HomeAction.PayUpiId -> payStart = PayStart.EnterUpiId
            HomeAction.AddExpense -> showPickEnvelope = true
            HomeAction.AddIncome -> showAddIncome = true
            HomeAction.NewEnvelope -> showAddEnvelope = true
            null -> return@LaunchedEffect
        }
        onActionHandled()
    }

    // Keeps the last opened envelope on screen while the detail view slides away.
    val current = openEnvelope
    var lastOpen by remember { mutableStateOf<EnvelopeSummary?>(null) }
    LaunchedEffect(current) {
        if (current != null) lastOpen = current
    }

    AnimatedContent(
        targetState = current != null,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
            } else {
                fadeIn() togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
            }
        },
        label = "envelope",
    ) { showDetail ->
        val detail = current ?: lastOpen
        if (showDetail && detail != null) {
            EnvelopeDetailScreen(
                envelope = detail,
                transactions = openTransactions,
                onBack = { viewModel.closeEnvelope() },
                onAddExpense = { expenseTarget = detail },
                onEditBudget = { budgetTarget = detail },
                onRemove = { viewModel.removeTransaction(it) },
            )
        } else {
            DashboardScreen(
                envelopes = envelopes,
                incomePaise = incomePaise,
                lastSevenDays = lastSevenDays,
                userName = userName,
                onOpenSettings = { showSettings = true },
                onOpen = { viewModel.openEnvelope(it.id) },
            )
        }
    }
    PayFlow(
        start = payStart,
        onStartHandled = { payStart = null },
        envelopes = envelopes,
        viewModel = viewModel,
    )

    if (showSettings) {
        SettingsDialog(
            currentName = userName.orEmpty(),
            currentLock = lockEnabled,
            currentTheme = themeMode,
            onThemeChange = { viewModel.setThemeMode(it) },
            onDismiss = { showSettings = false },
            onSave = { name, lock ->
                viewModel.saveProfile(name, lock)
                showSettings = false
            },
        )
    }

    if (showPickEnvelope) {
        AlertDialog(
            onDismissRequest = { showPickEnvelope = false },
            title = { Text("Add expense to") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (envelopes.isEmpty()) {
                        Text("Create an envelope first.")
                    }
                    envelopes.forEachIndexed { index, envelope ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    showPickEnvelope = false
                                    expenseTarget = envelope
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = iconFor(envelope.name),
                                contentDescription = null,
                                tint = accentFor(index),
                            )
                            Text(envelope.name, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPickEnvelope = false }) { Text("Cancel") }
            },
        )
    }

    if (showAddEnvelope) {
        EntryDialog(
            title = "New envelope",
            textLabel = "Name (e.g. Food)",
            onDismiss = { showAddEnvelope = false },
            onConfirm = { name, paise ->
                viewModel.addCategory(name, paise)
                showAddEnvelope = false
            },
        )
    }

    if (showAddIncome) {
        EntryDialog(
            title = "Add income",
            textLabel = "Source (e.g. Salary)",
            onDismiss = { showAddIncome = false },
            onConfirm = { source, paise ->
                viewModel.addIncome(source, paise)
                showAddIncome = false
            },
        )
    }

    expenseTarget?.let { target ->
        EntryDialog(
            title = "Spend from ${target.name}",
            textLabel = "Where / what",
            onDismiss = { expenseTarget = null },
            onConfirm = { merchant, paise ->
                viewModel.addExpense(target.id, paise, merchant)
                expenseTarget = null
            },
        )
    }

    budgetTarget?.let { target ->
        EntryDialog(
            title = "Budget for ${target.name}",
            textLabel = null,
            onDismiss = { budgetTarget = null },
            onConfirm = { _, paise ->
                viewModel.updateBudget(target.id, paise)
                budgetTarget = null
            },
        )
    }
}

@Composable
private fun DashboardScreen(
    envelopes: List<EnvelopeSummary>,
    incomePaise: Long,
    lastSevenDays: List<DaySpend>,
    userName: String?,
    onOpenSettings: () -> Unit,
    onOpen: (EnvelopeSummary) -> Unit,
) {
    val allocatedPaise = envelopes.sumOf { it.monthlyBudgetPaise }
    val spentPaise = envelopes.sumOf { it.spentPaise }
    var showBreakdown by rememberSaveable { mutableStateOf(false) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        // The top inset is applied as padding (not content padding) so cards never scroll under the status bar.
        LazyColumn(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(userName = userName, onOpenSettings = onOpenSettings) }
            item {
                HeroCard(
                    incomePaise = incomePaise,
                    allocatedPaise = allocatedPaise,
                    spentPaise = spentPaise,
                )
            }
            if (spentPaise > 0) {
                item {
                    SpendingSplitCard(
                        envelopes = envelopes,
                        spentPaise = spentPaise,
                        onClick = { showBreakdown = true },
                    )
                }
            }
            if (lastSevenDays.isNotEmpty()) {
                item { WeekCard(days = lastSevenDays) }
            }
            item {
                Text(
                    text = "Envelopes",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
            if (envelopes.isEmpty()) {
                item {
                    Text(
                        text = "Tap + below to create your first envelope",
                        modifier = Modifier.padding(start = 4.dp, bottom = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(envelopes.withIndex().chunked(2), key = { row -> row.first().value.id }) { row ->
                Row(
                    modifier = Modifier.animateItem(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { (index, envelope) ->
                        EnvelopeTile(
                            envelope = envelope,
                            accent = accentFor(index),
                            onOpen = { onOpen(envelope) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showBreakdown) {
        SpendingBreakdownSheet(
            rows = envelopes
                .mapIndexed { index, envelope ->
                    BreakdownRow(
                        id = envelope.id,
                        name = envelope.name,
                        spentPaise = envelope.spentPaise,
                        budgetPaise = envelope.monthlyBudgetPaise,
                        color = accentFor(index),
                    )
                }
                .filter { it.spentPaise > 0 }
                .sortedByDescending { it.spentPaise },
            totalPaise = spentPaise,
            onDismiss = { showBreakdown = false },
            onOpenEnvelope = { id ->
                showBreakdown = false
                envelopes.firstOrNull { it.id == id }?.let(onOpen)
            },
        )
    }
}

@Composable
private fun Header(userName: String?, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = YearMonth.now().format(monthTitleFormat),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (userName.isNullOrBlank()) greeting() else "${greeting()}, $userName",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsDialog(
    currentName: String,
    currentLock: Boolean,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lockAvailable = remember { canUseAppLock(context) }
    var name by rememberSaveable { mutableStateOf(currentName) }
    var lockEnabled by rememberSaveable { mutableStateOf(currentLock) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Your name") },
                    singleLine = true,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = currentTheme == mode,
                                onClick = { onThemeChange(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lock the app", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (lockAvailable) "Ask for fingerprint, face or screen lock when opening"
                            else "Set a screen lock on this phone to use this",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = lockEnabled && lockAvailable,
                        onCheckedChange = { lockEnabled = it },
                        enabled = lockAvailable,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, lockEnabled && lockAvailable) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HeroCard(incomePaise: Long, allocatedPaise: Long, spentPaise: Long) {
    val leftPaise = allocatedPaise - spentPaise
    val today = LocalDate.now()
    val daysRemaining = today.lengthOfMonth() - today.dayOfMonth + 1
    val safeTodayPaise = if (leftPaise > 0) leftPaise / daysRemaining else 0L
    val soft = Color.White.copy(alpha = 0.85f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    listOf(LocalAppColors.current.heroStart, LocalAppColors.current.heroEnd)
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Left to spend", color = soft, style = MaterialTheme.typography.labelLarge)
        Text(
            text = leftPaise.toShortRupees(),
            color = Color.White,
            style = MaterialTheme.typography.displaySmall,
        )
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Rounded.Bolt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "${safeTodayPaise.toShortRupees()} safe to spend today",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HeroStat("Income", incomePaise.toShortRupees())
            HeroStat("Allocated", allocatedPaise.toShortRupees())
            HeroStat("Spent", spentPaise.toShortRupees())
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(text = value, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

private data class Slice(val name: String, val amountPaise: Long, val color: Color)

@Composable
private fun SpendingSplitCard(
    envelopes: List<EnvelopeSummary>,
    spentPaise: Long,
    onClick: () -> Unit,
) {
    val otherColor = MaterialTheme.colorScheme.outline
    val ranked = envelopes
        .mapIndexed { index, envelope -> Slice(envelope.name, envelope.spentPaise, accentFor(index)) }
        .filter { it.amountPaise > 0 }
        .sortedByDescending { it.amountPaise }
    val slices = if (ranked.size <= 4) ranked
    else ranked.take(3) + Slice("Other", ranked.drop(3).sumOf { it.amountPaise }, otherColor)

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = panelColors()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(title = "Where it went", note = "Tap for details")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    DonutChart(slices = slices, modifier = Modifier.size(104.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(spentPaise.toShortRupees(), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "spent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    slices.forEach { slice ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(slice.color),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = slice.name,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = slice.amountPaise.toShortRupees(),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.16f
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val total = slices.sumOf { it.amountPaise }.toFloat()
        val gap = if (slices.size > 1) 4f else 0f
        var start = -90f

        slices.forEach { slice ->
            val sweep = slice.amountPaise / total * 360f
            drawArc(
                color = slice.color,
                startAngle = start + gap / 2,
                sweepAngle = (sweep - gap).coerceAtLeast(1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}

@Composable
private fun WeekCard(days: List<DaySpend>) {
    val peak = days.maxOf { it.amountPaise }
    val accents = LocalAppColors.current.accents
    val peakColor = MaterialTheme.colorScheme.primary

    Card(modifier = Modifier.fillMaxWidth(), colors = panelColors()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(
                title = "Last 7 days",
                note = if (peak > 0) "Peak ${peak.toShortRupees()}" else "No spending yet",
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                days.forEachIndexed { index, day ->
                    val fraction = if (peak == 0L) 0f else day.amountPaise.toFloat() / peak
                    val animated by animateFloatAsState(
                        targetValue = fraction.coerceAtLeast(0.06f),
                        animationSpec = tween(600),
                        label = "bar",
                    )
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(animated)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (peak > 0 && day.amountPaise == peak) peakColor
                                        else accents[(index % 3) + 1].copy(alpha = 0.55f)
                                    ),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = day.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardTitle(title: String, note: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = note,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EnvelopeTile(
    envelope: EnvelopeSummary,
    accent: Color,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overspent = envelope.remainingPaise < 0
    val fraction = if (envelope.monthlyBudgetPaise == 0L) 0f
    else (envelope.spentPaise.toFloat() / envelope.monthlyBudgetPaise).coerceIn(0f, 1f)

    Card(
        onClick = onOpen,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconFor(envelope.name),
                        contentDescription = null,
                        tint = LocalAppColors.current.onAccent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                ProgressRing(
                    fraction = fraction,
                    color = if (overspent) MaterialTheme.colorScheme.error else accent,
                    modifier = Modifier.size(38.dp),
                )
            }
            Text(
                text = envelope.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (overspent) "Over by ${(-envelope.remainingPaise).toShortRupees()}"
                else "${envelope.remainingPaise.toShortRupees()} left",
                style = MaterialTheme.typography.bodySmall,
                color = if (overspent) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressRing(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val track = LocalAppColors.current.track
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(600),
        label = "ring",
    )

    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.14f
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * animated,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun EntryDialog(
    title: String,
    textLabel: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    val paise = rupeesToPaise(amount)
    val textOk = textLabel == null || text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (textLabel != null) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(textLabel) },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = textOk && paise != null,
                onClick = { paise?.let { onConfirm(text, it) } },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
