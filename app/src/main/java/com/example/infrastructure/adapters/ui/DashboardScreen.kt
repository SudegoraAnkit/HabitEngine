package com.example.infrastructure.adapters.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.domain.Cadence
import com.example.core.domain.Habit
import com.example.core.domain.LifeDomain
import com.example.core.domain.isApplicableOn
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

// Structure representing active particles in our dopamine canvas
data class UiParticle(
    val id: Int,
    val initialX: Float,
    val initialY: Float,
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val maxRadius: Float,
    var currentRadius: Float,
    var alpha: Float,
    val decay: Float
)

// Data aggregation calculation function (Life Domain Command Dashboard)
fun calculateDomainMastery(
    habits: List<Habit>,
    logs: Map<String, Map<String, Boolean>>
): Map<LifeDomain, Int> {
    val domainOpportunities = mutableMapOf<LifeDomain, Int>()
    val domainCompletions = mutableMapOf<LifeDomain, Int>()
    
    // Initialize maps
    LifeDomain.values().forEach {
        domainOpportunities[it] = 0
        domainCompletions[it] = 0
    }
    
    // Let's compute over the logs
    logs.forEach { (dateStr, habitCompletions) ->
        habits.forEach { habit ->
            if (habit.cadence.isApplicableOn(dateStr)) {
                val domain = habit.domain
                domainOpportunities[domain] = (domainOpportunities[domain] ?: 0) + 1
                
                val isCompleted = habitCompletions[habit.id] == true
                if (isCompleted) {
                    domainCompletions[domain] = (domainCompletions[domain] ?: 0) + 1
                }
            }
        }
    }
    
    // Return percentage
    return LifeDomain.values().associateWith { domain ->
        val opps = domainOpportunities[domain] ?: 0
        val comps = domainCompletions[domain] ?: 0
        if (opps == 0) 0 else ((comps.toFloat() / opps.toFloat()) * 100).toInt()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Track dates to display in horizontal tester
    val dates = remember { getMockTestDates() }
    
    // Manage state for adding new habits
    var showAddForm by remember { mutableStateOf(false) }
    
    // Manage state for editing a habit
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    
    // Particles management for immediate checkbox coordinate explosions
    val particles = remember { mutableStateListOf<UiParticle>() }
    var particleIdCounter by remember { mutableIntStateOf(0) }
    
    // Animation frame clock to animate active particles
    if (particles.isNotEmpty()) {
        LaunchedEffect(particles.size) {
            while (isActive && particles.isNotEmpty()) {
                withFrameNanos { _ ->
                    val iterator = particles.iterator()
                    while (iterator.hasNext()) {
                        val p = iterator.next()
                        p.x += p.vx
                        p.y += p.vy
                        p.currentRadius = (p.currentRadius - p.decay * 0.1f).coerceAtLeast(0.1f)
                        p.alpha = (p.alpha - p.decay).coerceAtLeast(0f)
                        if (p.alpha <= 0f || p.currentRadius <= 0.1f) {
                            iterator.remove()
                        }
                    }
                }
                delay(16) // ~60fps updates
            }
        }
    }

    // Monitor ViewModel celebration state in order to spawn particles
    LaunchedEffect(uiState.celebration) {
        val celeb = uiState.celebration
        if (celeb != null) {
            // Spawn a burst of beautiful high-fidelity particles at click coordinates
            val count = 25
            val colors = if (uiState.themeMode == ThemeMode.CYBERPUNK) {
                listOf(CyberPrimary, CyberSecondary, CyberTertiary)
            } else {
                listOf(SunsetPrimary, SunsetSecondary, SunsetTertiary)
            }
            
            for (i in 0 until count) {
                val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                val speed = Random.nextFloat() * 6f + 3f
                val vx = (Math.cos(angle.toDouble()) * speed).toFloat()
                val vy = (Math.sin(angle.toDouble()) * speed).toFloat()
                val color = colors.random()
                val radius = Random.nextFloat() * 8f + 4f
                val decay = Random.nextFloat() * 0.03f + 0.02f
                
                particles.add(
                    UiParticle(
                        id = particleIdCounter++,
                        initialX = celeb.x,
                        initialY = celeb.y,
                        x = celeb.x,
                        y = celeb.y,
                        vx = vx,
                        vy = vy,
                        color = color,
                        maxRadius = radius,
                        currentRadius = radius,
                        alpha = 1.0f,
                        decay = decay
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AURA BYTE",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 3.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    actions = {
                        IconButton(onClick = { viewModel.toggleTheme() }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Toggle Palette Theme",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddForm = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Psychological Habit")
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header calendar tester
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cadence Sandbox",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Test dynamic filters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(dates) { dateObj ->
                                DateCard(
                                    dateLabel = dateObj.dayNumText,
                                    dayOfWeek = dateObj.dayOfWeekText,
                                    dateStr = dateObj.formatString,
                                    isSelected = uiState.selectedDate == dateObj.formatString,
                                    onClick = { viewModel.selectDate(dateObj.formatString) },
                                    isToday = dateObj.formatString == HabitViewModel.getTodayDateString()
                                )
                            }
                        }
                    }
                }

                // Mastery Dashboard Grid
                item {
                    CommandDashboard(
                        habits = uiState.habits,
                        logs = uiState.logs
                    )
                }

                // AuraByte Heatwave Analytics Monitor
                item {
                    HeatwaveDashboard(
                        habits = uiState.habits,
                        logs = uiState.logs,
                        selectedDate = uiState.selectedDate
                    )
                }

                // Daily Habit Header state
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val selectedDayText = getDayOfWeekLongName(uiState.selectedDate)
                            Text(
                                text = "Daily Routine Loop",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Active for $selectedDayText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        Text(
                            text = "${uiState.habits.count { it.cadence.isApplicableOn(uiState.selectedDate) }} Active",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }

                // Habit Cards
                val activeHabits = uiState.habits
                if (activeHabits.isEmpty()) {
                    item {
                        EmptyStateCard(onNewHabitClick = { showAddForm = true })
                    }
                } else {
                    items(activeHabits, key = { it.id }) { habit ->
                        val isSelectable = habit.cadence.isApplicableOn(uiState.selectedDate)
                        val completedMap = uiState.logs[uiState.selectedDate] ?: emptyMap()
                        val isCompleted = completedMap[habit.id] == true

                        val historyDates = getPast7Days(uiState.selectedDate)
                        val history = historyDates.map { date ->
                            val dayCompletions = uiState.logs[date] ?: emptyMap()
                            dayCompletions[habit.id] == true
                        }

                        HabitCard(
                            habit = habit,
                            isCompleted = isCompleted,
                            isSelectable = isSelectable,
                            selectedDate = uiState.selectedDate,
                            history = history,
                            onToggle = { clickX, clickY ->
                                viewModel.toggleHabitCompletion(habit.id, isCompleted, clickX, clickY)
                            },
                            onDelete = {
                                viewModel.deleteHabit(habit.id)
                            },
                            onEdit = {
                                editingHabit = habit
                            }
                        )
                    }
                }
                
                // Gorgeous Custom Footer with Signature, Authorship validation & Version code
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp, bottom = 12.dp)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "I am Ankit Sudegora",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Develop by Gemini and Ankit",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "❤️",
                                fontSize = 11.sp,
                                color = ColorCritical
                            )
                        }
                        Text(
                            text = "v1.0.4",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // Beautiful Interactive Particle Overlay Canvas
        if (particles.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.95f)
            ) {
                particles.forEach { p ->
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha),
                        radius = p.currentRadius,
                        center = Offset(p.x, p.y)
                    )
                }
            }
        }

        // Dopamine Popup Overlay Celebrator
        uiState.celebration?.let { celeb ->
            DopamineCelebrationPopup(
                celebrationState = celeb,
                onDismiss = { viewModel.dismissCelebration() }
            )
        }

        // Creation Dialog Screen (Forms Charles Duhigg psychological sentence sentence)
        if (showAddForm) {
            AddHabitDialog(
                onDismiss = { showAddForm = false },
                onSubmit = { domain, cadence, cue, routine, reward ->
                    viewModel.createHabit(domain, cadence, cue, routine, reward)
                    showAddForm = false
                }
            )
        }

        if (editingHabit != null) {
            val habitToEdit = editingHabit!!
            EditHabitDialog(
                habit = habitToEdit,
                onDismiss = { editingHabit = null },
                onSubmit = { updatedCue, updatedReward ->
                    viewModel.updateHabit(
                        habitId = habitToEdit.id,
                        domain = habitToEdit.domain,
                        cadence = habitToEdit.cadence,
                        cueText = updatedCue,
                        routineText = habitToEdit.routineText,
                        rewardText = updatedReward,
                        createdAt = habitToEdit.createdAt
                    )
                    editingHabit = null
                }
            )
        }
    }
}

// Custom Date Card Component for Cadence Sandbox
@Composable
fun DateCard(
    dateLabel: String,
    dayOfWeek: String,
    dateStr: String,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val isFirstOfMonth = dateLabel == "01"
    
    Box(
        modifier = Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            )
            .border(
                1.dp,
                if (isToday) MaterialTheme.colorScheme.tertiary else if (isFirstOfMonth) MaterialTheme.colorScheme.secondary else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = dayOfWeek,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = dateLabel,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary)
                )
            } else if (isFirstOfMonth) {
                Text(
                    text = "1st",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

// Life Domain Command Dashboard: Mastery Calculations
@Composable
fun CommandDashboard(
    habits: List<Habit>,
    logs: Map<String, Map<String, Boolean>>
) {
    // Perform data aggregation mapping
    val masteryMap = remember(habits, logs) {
        calculateDomainMastery(habits, logs)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Life Domain Command Monitor",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Realtime Engine",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LifeDomain.values().forEach { domain ->
                    val percentage = masteryMap[domain] ?: 0
                    
                    // Categorize mastery levels
                    val (statusLabel, statusColor) = when {
                        percentage < 40 -> "Critical" to ColorCritical
                        percentage < 75 -> "Stable" to ColorWarning
                        else -> "Mastery" to ColorSuccess
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(getDomainColor(domain))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = domain.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                            
                            Text(
                                text = "$percentage% $statusLabel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Linear progress indicator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = percentage.toFloat() / 100f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(statusColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helper to compute past 7 days ending at target date
private fun getPast7Days(endingDateStr: String): List<String> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dates = mutableListOf<String>()
    try {
        val date = sdf.parse(endingDateStr) ?: return emptyList()
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, -6)
        for (i in 0 until 7) {
            dates.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    } catch (e: Exception) {
        // Fallback to empty list
    }
    return dates
}

// Sparkline component to display completion trends using existing data logs
@Composable
fun HabitSparkline(
    history: List<Boolean>, // exactly 7 elements
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return
    val numDays = history.size
    
    Box(
        modifier = modifier
            .width(64.dp)
            .height(24.dp)
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Standard sparklines need at least 2 points to draw lines
            if (numDays < 2) {
                val isCompleted = history.firstOrNull() == true
                drawCircle(
                    color = if (isCompleted) lineColor else lineColor.copy(alpha = 0.2f),
                    radius = 3.dp.toPx(),
                    center = Offset(width / 2f, height / 2f)
                )
                return@Canvas
            }

            val spacing = width / (numDays - 1)
            val points = history.mapIndexed { index, completed ->
                val x = index * spacing
                val y = if (completed) {
                    3.dp.toPx()
                } else {
                    height - 3.dp.toPx()
                }
                Offset(x, y)
            }

            // Draw area gradient fill under sparkline
            val fillPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, height)
                points.forEach { point ->
                    lineTo(point.x, point.y)
                }
                lineTo(width, height)
                close()
            }
            
            drawPath(
                path = fillPath,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.35f),
                        lineColor.copy(alpha = 0.0f)
                    )
                )
            )

            // Draw sparkline line
            val strokePath = androidx.compose.ui.graphics.Path().apply {
                val firstPoint = points.first()
                moveTo(firstPoint.x, firstPoint.y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }

            drawPath(
                path = strokePath,
                color = lineColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            // Draw tiny indicators at points
            points.forEachIndexed { index, point ->
                val isCompleted = history[index]
                if (isCompleted) {
                    drawCircle(
                        color = lineColor,
                        radius = 2.dp.toPx(),
                        center = point
                    )
                } else {
                    drawCircle(
                        color = lineColor.copy(alpha = 0.3f),
                        radius = 1.5.dp.toPx(),
                        center = point,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }
    }
}

// Psychological Habit Card: Cue -> Routine -> Reward
@Composable
fun HabitCard(
    habit: Habit,
    isCompleted: Boolean,
    isSelectable: Boolean,
    selectedDate: String,
    history: List<Boolean>,
    onToggle: (clickX: Float, clickY: Float) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val domainColor = getDomainColor(habit.domain)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isSelectable) 1.0f else 0.4f),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Indicator Tag
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(domainColor)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = habit.routineText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = habit.domain.displayName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = habit.cadence.displayName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Small 7-day completion sparkline chart to show recent consistency trends
                HabitSparkline(
                    history = history,
                    lineColor = domainColor,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                // Right side: interactive checklist action or padlock
                if (isSelectable) {
                    // We intercept pointer location first to get raw coordinate
                    var boxCoordinates by remember { mutableStateOf(Offset.Zero) }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            )
                            .border(
                                1.dp,
                                if (isCompleted) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp)
                            )
                            // Retrieve relative coordinates of tap inside tap gestures detector
                            .pointerInput(isCompleted) {
                                detectTapGestures { offset ->
                                    // Calculate global position to feed the vector particle generator
                                    val screenOffset = boxCoordinates + offset
                                    onToggle(screenOffset.x, screenOffset.y)
                                }
                            }
                            // Allows tracking component boundaries
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.localToRoot(Offset.Zero)
                                boxCoordinates = pos
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Checked off",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Inactive on selected date",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expanded Habit loop showing the Charles Duhigg formula
            val expandTransition = updateTransition(targetState = expanded, label = "expand")
            val contentAlpha by expandTransition.animateFloat(label = "alpha") { if (it) 1f else 0f }
            val contentHeight by expandTransition.animateDp(label = "height") { if (it) 136.dp else 0.dp }

            if (expanded || contentHeight > 0.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(contentHeight)
                        .alpha(contentAlpha)
                        .padding(top = 12.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Psychological Habit Loop",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text(text = "Cue: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(text = habit.cueText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row {
                            Text(text = "Routine: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(text = habit.routineText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row {
                            Text(text = "Reward: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(text = habit.rewardText, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isSelectable) {
                            Text(
                                text = "⚠️ Non-applicable date: filtered",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Habit Description",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Habit",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Dopamine Celebration Popup overlay
@Composable
fun DopamineCelebrationPopup(
    celebrationState: CelebrationState,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        // Overlay banner celebrating reward unlock
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Star Glowing Icon Header
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Reward Unlocked",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "DOPAMINE UNLOCKED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Reward Earned!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Habit summary
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Action Completed:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                text = celebrationState.routineText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Your Psychological Reward is Ready:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "✨ " + celebrationState.rewardText + " ✨",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Secure Dopamine Lock",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

// Dialog form forcing Charles Duhigg's psychology sentence formulation
@Composable
fun AddHabitDialog(
    onDismiss: () -> Unit,
    onSubmit: (domain: LifeDomain, cadence: Cadence, cueText: String, routineText: String, rewardText: String) -> Unit
) {
    var domain by remember { mutableStateOf(LifeDomain.HEALTH) }
    var cadence by remember { mutableStateOf(Cadence.DAILY) }
    
    var cueText by remember { mutableStateOf("") }
    var routineText by remember { mutableStateOf("") }
    var rewardText by remember { mutableStateOf("") }

    var hasAttemptedSubmit by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val focusManager = LocalFocusManager.current
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Build Habit Loop",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        onDismiss()
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close form", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                // Domain Selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Life Domain Target",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LifeDomain.values().forEach { d ->
                            val isSelected = domain == d
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) getDomainColor(d) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                    .clickable { domain = d }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = d.name.capitalizeFirst(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Cadence Selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Trigger Cadence Schedule",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Cadence.values().forEach { c ->
                            val isSelected = cadence == c
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                    .clickable { cadence = c }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = c.name.capitalizeFirst(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                // The Duhigg Sentence Formulation Guide
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Charles Duhigg's psychology cue loop formulation:\n\"When I [Cue], I will [Routine] to enjoy [Reward].\"",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }

                // Sentence Fields
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = cueText,
                        onValueChange = { cueText = it },
                        label = { Text("When I... (Cue Trigger)") },
                        placeholder = { Text("e.g. sit down at my desk") },
                        isError = hasAttemptedSubmit && cueText.trim().isEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        trailingIcon = {
                            if (cueText.isNotEmpty()) {
                                IconButton(onClick = { focusManager.clearFocus() }) {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "Hide Keyboard",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    )

                    OutlinedTextField(
                        value = routineText,
                        onValueChange = { routineText = it },
                        label = { Text("I will... (Heuristic Routine)") },
                        placeholder = { Text("e.g. write 100 words of journal") },
                        isError = hasAttemptedSubmit && routineText.trim().isEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        trailingIcon = {
                            if (routineText.isNotEmpty()) {
                                IconButton(onClick = { focusManager.clearFocus() }) {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "Hide Keyboard",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    )

                    OutlinedTextField(
                        value = rewardText,
                        onValueChange = { rewardText = it },
                        label = { Text("To enjoy... (Dopamine Reward)") },
                        placeholder = { Text("e.g. a hot cup of black tea") },
                        isError = hasAttemptedSubmit && rewardText.trim().isEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        trailingIcon = {
                            if (rewardText.isNotEmpty()) {
                                IconButton(onClick = { focusManager.clearFocus() }) {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "Hide Keyboard",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    )
                }

                if (hasAttemptedSubmit && (cueText.isBlank() || routineText.isBlank() || rewardText.isBlank())) {
                    Text(
                        text = "Please complete all parts of the Habit Loop.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        hasAttemptedSubmit = true
                        if (cueText.isNotBlank() && routineText.isNotBlank() && rewardText.isNotBlank()) {
                            focusManager.clearFocus()
                            onSubmit(domain, cadence, cueText, routineText, rewardText)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Instantiate Habit Loop",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// Data structure for daily heatwave summary
data class DayHeatwaveInfo(
    val dateStr: String,
    val dayLabel: String,
    val totalActive: Int,
    val totalCompleted: Int,
    val percentage: Int,
    val emoji: String
)

// Data structure for monthly heatwave summary
data class MonthHeatwaveInfo(
    val monthLabel: String,
    val yearStr: String,
    val totalActiveDays: Int,
    val totalCompletedDays: Int,
    val percentage: Int,
    val emoji: String
)

// Quadruple tuple helper class
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// Heatwave timescales
enum class HeatwaveFilter {
    WEEK, MONTH, YEAR
}

// Helper to compute daily completion states for n days prior and including endDateStr
private fun getPastNDaysHeatwave(
    endDateStr: String,
    n: Int,
    habits: List<Habit>,
    logs: Map<String, Map<String, Boolean>>
): List<DayHeatwaveInfo> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dayLabelSdf = SimpleDateFormat("EEE", Locale.getDefault())
    val result = mutableListOf<DayHeatwaveInfo>()
    
    try {
        val date = sdf.parse(endDateStr) ?: return emptyList()
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, -(n - 1))
        
        for (i in 0 until n) {
            val currentDateStr = sdf.format(cal.time)
            val dayLabel = dayLabelSdf.format(cal.time).uppercase()
            
            val activeForDay = habits.filter { it.cadence.isApplicableOn(currentDateStr) }
            val completedMap = logs[currentDateStr] ?: emptyMap()
            val completedForDay = activeForDay.filter { completedMap[it.id] == true }
            
            val pct = if (activeForDay.isEmpty()) {
                0
            } else {
                ((completedForDay.size.toFloat() / activeForDay.size.toFloat()) * 100).toInt()
            }
            
            val emoji = when {
                activeForDay.isEmpty() -> "❄️"
                completedForDay.isEmpty() -> "😭"
                completedForDay.size == activeForDay.size -> "🔥"
                else -> "😅"
            }
            
            result.add(
                DayHeatwaveInfo(
                    dateStr = currentDateStr,
                    dayLabel = dayLabel,
                    totalActive = activeForDay.size,
                    totalCompleted = completedForDay.size,
                    percentage = pct,
                    emoji = emoji
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    } catch (e: Exception) {
        // Fallback
    }
    return result
}

// Helper to compute monthly completion stats for 12 months prior and including endDateStr month
private fun getPastYearHeatwave(
    endDateStr: String,
    habits: List<Habit>,
    logs: Map<String, Map<String, Boolean>>
): List<MonthHeatwaveInfo> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val monthLabelSdf = SimpleDateFormat("MMM", Locale.getDefault())
    val yearSdf = SimpleDateFormat("yyyy", Locale.getDefault())
    val result = mutableListOf<MonthHeatwaveInfo>()
    
    try {
        val date = sdf.parse(endDateStr) ?: return emptyList()
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.MONTH, -11)
        
        for (m in 0 until 12) {
            val monthLabel = monthLabelSdf.format(cal.time).uppercase()
            val yearStr = yearSdf.format(cal.time)
            
            var totalActiveOpportunities = 0
            var totalCompletions = 0
            
            val monthCal = Calendar.getInstance()
            monthCal.time = cal.time
            monthCal.set(Calendar.DAY_OF_MONTH, 1)
            val maxDay = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            for (day in 1..maxDay) {
                val dayStr = sdf.format(monthCal.time)
                
                val activeForDay = habits.filter { it.cadence.isApplicableOn(dayStr) }
                val completedMap = logs[dayStr] ?: emptyMap()
                val completedForDay = activeForDay.filter { completedMap[it.id] == true }
                
                totalActiveOpportunities += activeForDay.size
                totalCompletions += completedForDay.size
                
                monthCal.add(Calendar.DAY_OF_MONTH, 1)
            }
            
            val pct = if (totalActiveOpportunities == 0) {
                0
            } else {
                ((totalCompletions.toFloat() / totalActiveOpportunities.toFloat()) * 100).toInt()
            }
            
            val emoji = when {
                totalActiveOpportunities == 0 -> "❄️"
                totalCompletions == 0 -> "😭"
                totalCompletions == totalActiveOpportunities -> "🔥"
                else -> "😅"
            }
            
            result.add(
                MonthHeatwaveInfo(
                    monthLabel = monthLabel,
                    yearStr = yearStr,
                    totalActiveDays = totalActiveOpportunities,
                    totalCompletedDays = totalCompletions,
                    percentage = pct,
                    emoji = emoji
                )
            )
            cal.add(Calendar.MONTH, 1)
        }
    } catch (e: Exception) {
        // Fallback
    }
    return result
}

// AuraByte Heatwave Consistency Dashboard
@Composable
fun HeatwaveDashboard(
    habits: List<Habit>,
    logs: Map<String, Map<String, Boolean>>,
    selectedDate: String
) {
    var currentFilter by remember { mutableStateOf(HeatwaveFilter.WEEK) }
    
    val weekData = remember(habits, logs, selectedDate) {
        getPastNDaysHeatwave(selectedDate, 7, habits, logs)
    }
    
    val monthData = remember(habits, logs, selectedDate) {
        getPastNDaysHeatwave(selectedDate, 30, habits, logs)
    }
    
    val yearData = remember(habits, logs, selectedDate) {
        getPastYearHeatwave(selectedDate, habits, logs)
    }
    
    val overallPercentage = remember(currentFilter, weekData, monthData, yearData) {
        when (currentFilter) {
            HeatwaveFilter.WEEK -> {
                val totalActive = weekData.sumOf { it.totalActive }
                val totalCompleted = weekData.sumOf { it.totalCompleted }
                if (totalActive == 0) 0 else ((totalCompleted.toFloat() / totalActive.toFloat()) * 100).toInt()
            }
            HeatwaveFilter.MONTH -> {
                val totalActive = monthData.sumOf { it.totalActive }
                val totalCompleted = monthData.sumOf { it.totalCompleted }
                if (totalActive == 0) 0 else ((totalCompleted.toFloat() / totalActive.toFloat()) * 100).toInt()
            }
            HeatwaveFilter.YEAR -> {
                val totalActive = yearData.sumOf { it.totalActiveDays }
                val totalCompleted = yearData.sumOf { it.totalCompletedDays }
                if (totalActive == 0) 0 else ((totalCompleted.toFloat() / totalActive.toFloat()) * 100).toInt()
            }
        }
    }
    
    val (summaryEmoji, summaryLabel, summaryDescription, summaryColor) = when {
        overallPercentage == 0 -> Quadruple("😭", "Untouched Streak", "Your routine loop is cold. Complete a habit to ignite!", ColorCritical)
        overallPercentage == 100 -> Quadruple("🔥", "Flawless Streak", "Absolute mastery! Routine loop is fully vaporized!", ColorSuccess)
        else -> Quadruple("😅", "Halfway Warming", "Progression active! Maintain discipline to heat up the engine.", ColorWarning)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("heatwave_dashboard_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AuraByte Heatwave Engine",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                        .padding(2.dp)
                ) {
                    HeatwaveFilter.values().forEach { filter ->
                        val isSelected = currentFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { currentFilter = filter }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.name,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(summaryColor.copy(alpha = 0.06f))
                    .border(BorderStroke(1.dp, summaryColor.copy(alpha = 0.15f)), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = summaryEmoji,
                    fontSize = 32.sp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = summaryLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = summaryColor
                        )
                        Text(
                            text = "$overallPercentage% Consistency",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = summaryDescription,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 14.sp
                    )
                }
            }
            
            when (currentFilter) {
                HeatwaveFilter.WEEK -> WeeklyHeatwaveGrid(weekData)
                HeatwaveFilter.MONTH -> MonthlyHeatwaveGrid(monthData)
                HeatwaveFilter.YEAR -> YearlyHeatwaveGrid(yearData)
            }
        }
    }
}

@Composable
fun WeeklyHeatwaveGrid(data: List<DayHeatwaveInfo>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        data.forEach { info ->
            val rawDayName = info.dayLabel.take(2)
            val color = when (info.emoji) {
                "🔥" -> ColorSuccess
                "😭" -> ColorCritical
                "😅" -> ColorWarning
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = rawDayName,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, color.copy(alpha = 0.4f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = info.emoji,
                        fontSize = 16.sp
                    )
                }
                
                Text(
                    text = "${info.totalCompleted}/${info.totalActive}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun MonthlyHeatwaveGrid(data: List<DayHeatwaveInfo>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val chunkSize = 6
        val chunkedList = data.chunked(chunkSize)
        
        chunkedList.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowItems.forEach { info ->
                    val color = when (info.emoji) {
                        "🔥" -> ColorSuccess
                        "😭" -> ColorCritical
                        "😅" -> ColorWarning
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    }
                    
                    val dateParts = info.dateStr.split("-")
                    val dayNum = dateParts.lastOrNull() ?: ""
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .aspectRatio(1.1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = 0.1f))
                            .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = dayNum,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                text = info.emoji,
                                fontSize = 12.sp
                              )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YearlyHeatwaveGrid(data: List<MonthHeatwaveInfo>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val chunkSize = 3
        val chunkedList = data.chunked(chunkSize)
        
        chunkedList.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowItems.forEach { info ->
                    val color = when (info.emoji) {
                        "🔥" -> ColorSuccess
                        "😭" -> ColorCritical
                        "😅" -> ColorWarning
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    }
                    
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .border(BorderStroke(1.dp, color.copy(alpha = 0.25f)), RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.04f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = info.monthLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            
                            Text(
                                text = info.emoji,
                                fontSize = 18.sp
                            )
                            
                            Text(
                                text = "${info.percentage}% Done",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = color,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// Empty state placeholder card
@Composable
fun EmptyStateCard(onNewHabitClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Empty",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "No Habit Loops Registered",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "AuraByte operates on Charles Duhigg's cue system. Formulate your first loop to start tracking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onNewHabitClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Formulate Habit Loop", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

// Utility class definitions for Sandbox Date navigation
data class TestDateObj(
    val formatString: String, // YYYY-MM-DD
    val dayNumText: String,   // 01, 26, etc.
    val dayOfWeekText: String // MON, SUN, etc.
)

private fun getMockTestDates(): List<TestDateObj> {
    val result = mutableListOf<TestDateObj>()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
    val dowFormat = SimpleDateFormat("EEE", Locale.getDefault())
    
    val cal = Calendar.getInstance()
    // Align around today: -3 to +3
    cal.add(Calendar.DAY_OF_YEAR, -3)
    
    // We add more dates so user can test weekends/weekdays.
    // Let's add 12 days to make it a perfect sandbox!
    for (i in 0 until 12) {
        result.add(
            TestDateObj(
                formatString = sdf.format(cal.time),
                dayNumText = dayFormat.format(cal.time),
                dayOfWeekText = dowFormat.format(cal.time).uppercase()
            )
        )
        // Add 1st day of month of current calendar as a diagnostic option if not already in list
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    
    // Check if 1st day of current month gets included. If not, append it specifically for MONTHLY cadence tests!
    val diagnosticCal = Calendar.getInstance()
    diagnosticCal.set(Calendar.DAY_OF_MONTH, 1)
    val firstOfMonthStr = sdf.format(diagnosticCal.time)
    if (result.none { it.formatString == firstOfMonthStr }) {
        result.add(
            0,
            TestDateObj(
                formatString = firstOfMonthStr,
                dayNumText = "01",
                dayOfWeekText = dowFormat.format(diagnosticCal.time).uppercase()
            )
        )
    }

    return result.sortedBy { it.formatString }
}

private fun getDayOfWeekLongName(dateStr: String): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return try {
        val date = sdf.parse(dateStr) ?: return "Today"
        val labelSdf = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
        labelSdf.format(date)
    } catch (e: Exception) {
        "Selected Date"
    }
}

private fun getDomainColor(domain: LifeDomain): Color {
    return when (domain) {
        LifeDomain.HEALTH -> Color(0xFF10B981)       // Emerald Green
        LifeDomain.PROFESSIONAL -> Color(0xFF8B5CF6) // Violet
        LifeDomain.PERSONAL -> Color(0xFF06B6D4)     // Cyan
        LifeDomain.FAMILY -> Color(0xFFE11D48)       // Crimson Rose
    }
}

// Helpers for capitalizations
private fun String.capitalizeFirst(): String {
    return this.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

@Composable
fun EditHabitDialog(
    habit: Habit,
    onDismiss: () -> Unit,
    onSubmit: (cueText: String, rewardText: String) -> Unit
) {
    var cueText by remember { mutableStateOf(habit.cueText) }
    var rewardText by remember { mutableStateOf(habit.rewardText) }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val focusManager = LocalFocusManager.current
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Habit Loop",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        onDismiss()
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close form", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                // Domain & Cadence Information (Displayed Read-Only)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(getDomainColor(habit.domain).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = habit.domain.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = getDomainColor(habit.domain)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = habit.cadence.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                // The Read-Only Title section (Heuristic Routine)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Habit Title (Routine - Read-only)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = habit.routineText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Sentence Fields (Cue and Reward are editable, Routine is locked)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = cueText,
                        onValueChange = { cueText = it },
                        label = { Text("When I... (Cue Trigger)") },
                        placeholder = { Text("e.g. sit down at my desk") },
                        isError = hasAttemptedSubmit && cueText.trim().isEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        trailingIcon = {
                            if (cueText.isNotEmpty()) {
                                IconButton(onClick = { focusManager.clearFocus() }) {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "Hide Keyboard",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    )

                    OutlinedTextField(
                        value = rewardText,
                        onValueChange = { rewardText = it },
                        label = { Text("To enjoy... (Dopamine Reward)") },
                        placeholder = { Text("e.g. a hot cup of black tea") },
                        isError = hasAttemptedSubmit && rewardText.trim().isEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        trailingIcon = {
                            if (rewardText.isNotEmpty()) {
                                IconButton(onClick = { focusManager.clearFocus() }) {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "Hide Keyboard",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    )
                }

                if (hasAttemptedSubmit && (cueText.isBlank() || rewardText.isBlank())) {
                    Text(
                        text = "Cue and Reward fields are required description components.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        hasAttemptedSubmit = true
                        if (cueText.isNotBlank() && rewardText.isNotBlank()) {
                            focusManager.clearFocus()
                            onSubmit(cueText, rewardText)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Save Description Changes",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
