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
import androidx.compose.runtime.saveable.rememberSaveable
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
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
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
    
    // Active Regional Language Settings (Defaults to English)
    var selectedLanguage by rememberSaveable { mutableStateOf(AppLanguage.ENGLISH) }
    
    // Manage state for displaying regional selector menu
    var showLanguageMenu by remember { mutableStateOf(false) }
    
    // Manage state for the Interactive Charles Duhigg Tutorial Guide
    var showTutorialGuide by rememberSaveable { mutableStateOf(true) }
    
    // Search query for filtering routines
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    // Category quick filter by domain
    var selectedFilterDomain by remember { mutableStateOf<LifeDomain?>(null) }
    
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // Function to copy a beautiful Daily Success Receipt to clipboard
    val onShareAchievement = {
        val totalHabits = uiState.habits.count { it.cadence.isApplicableOn(uiState.selectedDate) }
        val completedMap = uiState.logs[uiState.selectedDate] ?: emptyMap()
        val completedCount = uiState.habits.count { 
            it.cadence.isApplicableOn(uiState.selectedDate) && completedMap[it.id] == true 
        }
        val percentage = if (totalHabits == 0) 0 else ((completedCount.toFloat() / totalHabits.toFloat()) * 100).toInt()
        
        val formatDay = getDayOfWeekLongName(uiState.selectedDate)
        val checkListText = uiState.habits.filter { it.cadence.isApplicableOn(uiState.selectedDate) }
            .joinToString("\n") { habit ->
                val isCompleted = completedMap[habit.id] == true
                val statusBox = if (isCompleted) "✅ [OK]" else "⬜ [  ]"
                " $statusBox ${habit.domain.displayName}: ${habit.routineText} (${habit.cadence.displayName})"
            }
            
        val receiptText = """
🏆 AURA BYTE - Daily Success Summary 🏆
📅 Date: $formatDay
📊 Progress: $completedCount / $totalHabits ($percentage%)
--------------------------------------------
$checkListText
--------------------------------------------
Consistently developed by Gemini and Ankit ♥️
🚀 AuraByte: Charles Duhigg Habit Loop Tracker.
        """.trimIndent()
        
        val clip = ClipData.newPlainText("AuraByte Progress", receiptText)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(context, Localizations.get(selectedLanguage, "share_toast"), Toast.LENGTH_SHORT).show()
    }

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
                                text = Localizations.get(selectedLanguage, "app_title"),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            // Info Button to show Habit Loop neuroscience tutorial
                            IconButton(onClick = { showTutorialGuide = !showTutorialGuide }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Toggle Habit Guide",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // Share Success receipt Button
                            IconButton(onClick = onShareAchievement) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Daily Success",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Dynamic Region / Language selector capsule
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .clickable { showLanguageMenu = !showLanguageMenu }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = selectedLanguage.flag, fontSize = 14.sp)
                                    Text(
                                        text = selectedLanguage.code.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Palette Theme Toggle
                            IconButton(onClick = { viewModel.toggleTheme() }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Toggle Palette Theme",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
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
                                text = Localizations.get(selectedLanguage, "cadence_sandbox"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = Localizations.get(selectedLanguage, "test_filters"),
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

                // Interactive Charles Duhigg Habit Loop Tutorial Guide
                if (showTutorialGuide) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    RoundedCornerShape(16.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Tutorial",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = Localizations.get(selectedLanguage, "guide_title"),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = Localizations.get(selectedLanguage, "guide_subtitle"),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    
                                    IconButton(
                                        onClick = { showTutorialGuide = false },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss guide",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                                // Three-Step neuroscience loop explanation
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = Localizations.get(selectedLanguage, "guide_cue"),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = Localizations.get(selectedLanguage, "guide_cue_desc"),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            lineHeight = 15.sp
                                        )
                                    }
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = Localizations.get(selectedLanguage, "guide_routine"),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = Localizations.get(selectedLanguage, "guide_routine_desc"),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            lineHeight = 15.sp
                                        )
                                    }
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = Localizations.get(selectedLanguage, "guide_reward"),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = Localizations.get(selectedLanguage, "guide_reward_desc"),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                                
                                Button(
                                    onClick = { showTutorialGuide = false },
                                    modifier = Modifier.align(Alignment.End),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = Localizations.get(selectedLanguage, "guide_dismiss"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Mastery Dashboard Grid
                item {
                    CommandDashboard(
                        selectedLanguage = selectedLanguage,
                        habits = uiState.habits,
                        logs = uiState.logs
                    )
                }

                // AuraByte Heatwave Analytics Monitor
                item {
                    HeatwaveDashboard(
                        selectedLanguage = selectedLanguage,
                        habits = uiState.habits,
                        logs = uiState.logs,
                        selectedDate = uiState.selectedDate
                    )
                }

                // Search & Category Filter Panel
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { 
                                Text(
                                    text = Localizations.get(selectedLanguage, "search_placeholder"),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                ) 
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search icon",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selectedFilterDomain == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .border(
                                            1.dp,
                                            if (selectedFilterDomain == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedFilterDomain = null }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = Localizations.get(selectedLanguage, "all_categories"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedFilterDomain == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            
                            items(LifeDomain.values().toList()) { domain ->
                                val isSelected = selectedFilterDomain == domain
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) getDomainColor(domain).copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) getDomainColor(domain).copy(alpha = 0.4f)
                                            else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedFilterDomain = domain }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(getDomainColor(domain))
                                        )
                                        Text(
                                            text = domain.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) getDomainColor(domain) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                                text = Localizations.get(selectedLanguage, "daily_routine"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = String.format(Localizations.get(selectedLanguage, "active_for"), selectedDayText),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        Text(
                            text = String.format(Localizations.get(selectedLanguage, "active_badge"), uiState.habits.count { it.cadence.isApplicableOn(uiState.selectedDate) }),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }

                // Filtered Habit Cards List
                val filteredHabits = uiState.habits.filter { habit ->
                    val matchesDate = habit.cadence.isApplicableOn(uiState.selectedDate)
                    val matchesSearch = searchQuery.isBlank() || 
                        habit.routineText.contains(searchQuery, ignoreCase = true) ||
                        habit.cueText.contains(searchQuery, ignoreCase = true) ||
                        habit.rewardText.contains(searchQuery, ignoreCase = true)
                    val matchesCategory = selectedFilterDomain == null || habit.domain == selectedFilterDomain
                    matchesDate && matchesSearch && matchesCategory
                }
                
                if (filteredHabits.isEmpty()) {
                    item {
                        EmptyStateCard(selectedLanguage = selectedLanguage, onNewHabitClick = { showAddForm = true })
                    }
                } else {
                    items(filteredHabits, key = { it.id }) { habit ->
                        val isSelectable = habit.cadence.isApplicableOn(uiState.selectedDate)
                        val completedMap = uiState.logs[uiState.selectedDate] ?: emptyMap()
                        val isCompleted = completedMap[habit.id] == true

                        val historyDates = getPast7Days(uiState.selectedDate)
                        val history = historyDates.map { date ->
                            val dayCompletions = uiState.logs[date] ?: emptyMap()
                            dayCompletions[habit.id] == true
                        }

                        HabitCard(
                            selectedLanguage = selectedLanguage,
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

        // Regional Language Custom Selection Overlay Menu (Material 3 Adaptive Modal)
        if (showLanguageMenu) {
            Dialog(onDismissRequest = { showLanguageMenu = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight()
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            RoundedCornerShape(24.dp)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Regional Translations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            items(AppLanguage.values().toList()) { lang ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selectedLanguage == lang) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            selectedLanguage = lang
                                            showLanguageMenu = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text(text = lang.flag, fontSize = 24.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = lang.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedLanguage == lang) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = when(lang) {
                                                AppLanguage.ENGLISH -> "Universal English Localization"
                                                AppLanguage.SPANISH -> "América Latina y España"
                                                AppLanguage.HINDI -> "भारत और दक्षिण एशिया"
                                                AppLanguage.GERMAN -> "Europa und Deutschland"
                                                AppLanguage.JAPANESE -> "日本地域"
                                                AppLanguage.PORTUGUESE -> "Brasil e Portugal"
                                            },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                    if (selectedLanguage == lang) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Creation Dialog Screen (Forms Charles Duhigg psychological sentence)
        if (showAddForm) {
            AddHabitDialog(
                selectedLanguage = selectedLanguage,
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
                selectedLanguage = selectedLanguage,
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
    selectedLanguage: AppLanguage,
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
                    text = Localizations.get(selectedLanguage, "domain_dashboard_title"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = Localizations.get(selectedLanguage, "realtime_indicator"),
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
                    
                    // Categorize mastery levels using localized descriptions
                    val statusLabel = when {
                        percentage < 40 -> Localizations.get(selectedLanguage, "status_starting")
                        percentage < 75 -> Localizations.get(selectedLanguage, "status_growing")
                        else -> Localizations.get(selectedLanguage, "status_thriving")
                    }
                    val statusColor = when {
                        percentage < 40 -> ColorCritical
                        percentage < 75 -> ColorWarning
                        else -> ColorSuccess
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
    selectedLanguage: AppLanguage,
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
                        text = Localizations.get(selectedLanguage, "guide_title"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text(text = "${Localizations.get(selectedLanguage, "guide_content_cue")}: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(text = habit.cueText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row {
                            Text(text = "${Localizations.get(selectedLanguage, "guide_content_action")}: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(text = habit.routineText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row {
                            Text(text = "${Localizations.get(selectedLanguage, "guide_content_reward")}: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
    selectedLanguage: AppLanguage,
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
                        text = Localizations.get(selectedLanguage, "form_title"),
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
                        text = Localizations.get(selectedLanguage, "domain_title"),
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
                                    text = d.displayName,
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
                        text = Localizations.get(selectedLanguage, "cadence_title"),
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
                                    text = c.displayName,
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
                        text = Localizations.get(selectedLanguage, "formula_sentence"),
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
                        label = { Text(Localizations.get(selectedLanguage, "routine_label")) },
                        placeholder = { Text(Localizations.get(selectedLanguage, "routine_placeholder")) },
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
                        label = { Text(Localizations.get(selectedLanguage, "action_label")) },
                        placeholder = { Text(Localizations.get(selectedLanguage, "action_placeholder")) },
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
                        label = { Text(Localizations.get(selectedLanguage, "reward_label")) },
                        placeholder = { Text(Localizations.get(selectedLanguage, "reward_placeholder")) },
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
                        text = Localizations.get(selectedLanguage, "fields_error"),
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
                        text = Localizations.get(selectedLanguage, "formulate_habit"),
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
    selectedLanguage: AppLanguage,
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
        overallPercentage == 0 -> Quadruple(
            "🌱", 
            Localizations.get(selectedLanguage, "status_starting"), 
            if (selectedLanguage == AppLanguage.SPANISH) "Tu viaje de hábitos te espera. ¡Completa un hábito hoy para comenzar!"
            else if (selectedLanguage == AppLanguage.HINDI) "आपकी आदत यात्रा आपका इंतजार कर रही है। आज ही शुरुआत करें!"
            else if (selectedLanguage == AppLanguage.GERMAN) "Ihre Gewohnheits-Reise wartet auf Sie. Fangen Sie heute an!"
            else if (selectedLanguage == AppLanguage.JAPANESE) "ハッピーな習慣があなたを待っています。今日から始めましょう！"
            else if (selectedLanguage == AppLanguage.PORTUGUESE) "Sua jornada de hábitos está te esperando. Comece hoje!"
            else "Your habit journey is waiting to grow. Complete a habit today to start!", 
            ColorCritical
        )
        overallPercentage == 100 -> Quadruple(
            "👑", 
            Localizations.get(selectedLanguage, "status_thriving"), 
            if (selectedLanguage == AppLanguage.SPANISH) "¡Trabajo excelente! Has completado el 100% de tus objetivos."
            else if (selectedLanguage == AppLanguage.HINDI) "शानदार काम! आपने अपने १००% लक्ष्य पूरे किए हैं।"
            else if (selectedLanguage == AppLanguage.GERMAN) "Großartig! Sie haben 100 % Ihrer Ziele erreicht."
            else if (selectedLanguage == AppLanguage.JAPANESE) "すばらしい！今日まですべての項目を達成できました。"
            else if (selectedLanguage == AppLanguage.PORTUGUESE) "Trabalho fantástico! Você atingiu 100% dos seus objetivos."
            else "Perfect work! You've achieved 100% completion in this timezone!", 
            ColorSuccess
        )
        else -> Quadruple(
            "🌞", 
            Localizations.get(selectedLanguage, "status_growing"), 
            if (selectedLanguage == AppLanguage.SPANISH) "Vas por muy buen camino. ¡Sigue con entusiasmo para crear tu rutina!"
            else if (selectedLanguage == AppLanguage.HINDI) "आप सही रास्ते पर हैं। अपनी आदत बनाने के लिए प्रयास जारी रखें!"
            else if (selectedLanguage == AppLanguage.GERMAN) "Sie sind auf einem guten Weg. Machen Sie weiter so!"
            else if (selectedLanguage == AppLanguage.JAPANESE) "着実に進歩しています。理想的な生活リズムをつくっていきましょう！"
            else if (selectedLanguage == AppLanguage.PORTUGUESE) "Você está no caminho certo. Continue firme para firmar seu ritual!"
            else "You are making steady progress. Keep going to build your habit loop!", 
            ColorWarning
        )
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
                    text = Localizations.get(selectedLanguage, "heatwave_dashboard_title"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                
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
                                text = when (filter) {
                                    HeatwaveFilter.WEEK -> Localizations.get(selectedLanguage, "filter_week")
                                    HeatwaveFilter.MONTH -> Localizations.get(selectedLanguage, "filter_month")
                                    HeatwaveFilter.YEAR -> Localizations.get(selectedLanguage, "filter_year")
                                },
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
fun EmptyStateCard(selectedLanguage: AppLanguage, onNewHabitClick: () -> Unit) {
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
                    text = Localizations.get(selectedLanguage, "empty_title"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Localizations.get(selectedLanguage, "empty_desc"),
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
                Text(Localizations.get(selectedLanguage, "empty_btn"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
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
    selectedLanguage: AppLanguage,
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
                        text = Localizations.get(selectedLanguage, "edit_dialog_title"),
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
                        text = Localizations.get(selectedLanguage, "edit_dialog_subtitle"),
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
                        label = { Text(Localizations.get(selectedLanguage, "routine_label")) },
                        placeholder = { Text(Localizations.get(selectedLanguage, "routine_placeholder")) },
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
                        label = { Text(Localizations.get(selectedLanguage, "reward_label")) },
                        placeholder = { Text(Localizations.get(selectedLanguage, "reward_placeholder")) },
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
                        text = Localizations.get(selectedLanguage, "edit_dialog_error"),
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
                        text = Localizations.get(selectedLanguage, "save_changes"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// App Language configuration regional definitions
enum class AppLanguage(val label: String, val flag: String, val code: String) {
    ENGLISH("English", "🇺🇸", "en"),
    SPANISH("Español", "🇪🇸", "es"),
    HINDI("हिन्दी", "🇮🇳", "hi"),
    GERMAN("Deutsch", "🇩🇪", "de"),
    JAPANESE("日本語", "🇯🇵", "ja"),
    PORTUGUESE("Português", "🇧🇷", "pt")
}

// Translations mappings dictionary
object Localizations {
    private val strings = mapOf(
        AppLanguage.ENGLISH to mapOf(
            "app_title" to "AuraByte",
            "app_subtitle" to "Your companion for building positive habits",
            "share_text" to "Share Receipts",
            "share_progress" to "Celebrating my daily habit progress!",
            "all_categories" to "All Life Areas",
            "search_placeholder" to "Search loops...",
            "guide_title" to "How Habits Work",
            "guide_subtitle" to "Our habits are built around simple loops: Trigger, Action, and Reward.",
            "guide_content_cue" to "1. The Trigger (Cue)",
            "guide_content_cue_desc" to "The visual, physical, or environmental signal that tells you it's time to start.",
            "guide_content_action" to "2. The Action (Routine)",
            "guide_content_action_desc" to "The positive action or ritual you perform after the trigger.",
            "guide_content_reward" to "3. The Reward",
            "guide_content_reward_desc" to "The warm feeling or treat that makes your brain want to do it again.",
            "guide_dismiss" to "I Understand",
            "form_title" to "Build a New Habit Loop",
            "domain_title" to "Life Area Focus",
            "cadence_title" to "How Often (Cadence)",
            "formula_sentence" to "A proven way to build a habit:\n\"When I [Trigger], I will do [Action] to enjoy [Reward].\"",
            "routine_label" to "When I... (The Trigger)",
            "routine_placeholder" to "e.g. sit at my desk, wake up, finish lunch",
            "action_label" to "I will... (The Action)",
            "action_placeholder" to "e.g. write 100 words of journal, drink water",
            "reward_label" to "To enjoy... (The Reward)",
            "reward_placeholder" to "e.g. a hot cup of tea, a mindful deep breath",
            "fields_error" to "Please fill in all blanks to construct your positive loop.",
            "formulate_habit" to "Create My Habit Loop",
            "edit_dialog_title" to "Update My Habit Loop",
            "edit_dialog_subtitle" to "The Action (Immutable)",
            "edit_dialog_error" to "Please fill out both the trigger and reward fields.",
            "save_changes" to "Save Options",
            "share_copied" to "AuraByte progress copied to clipboard!",
            "empty_title" to "Your Habit Journey Starts Here",
            "empty_desc" to "You haven't built any habit loops yet. Let's construct a simple trigger, action, and reward to get going!",
            "empty_btn" to "Create My First Loop",
            "footer_author" to "Ankit Sudegora",
            "footer_by" to "Developed with Gemini and Ankit ♥️",
            "footer_ver" to "v2.0.0 (Global Play Store Release)",
            "domain_dashboard_title" to "My Daily Balance across Life Areas",
            "realtime_indicator" to "Updated Live",
            "heatwave_dashboard_title" to "My Personal Consistency Tracker",
            "filter_week" to "Week",
            "filter_month" to "Month",
            "filter_year" to "Year",
            "status_starting" to "Starting out",
            "status_growing" to "Getting consistent",
            "status_thriving" to "Thriving!"
        ),
        AppLanguage.SPANISH to mapOf(
            "app_title" to "AuraByte",
            "app_subtitle" to "Tu compañero para crear hábitos positivos",
            "share_text" to "Compartir logros",
            "share_progress" to "¡Celebrando mi progreso diario!",
            "all_categories" to "Todas las áreas",
            "search_placeholder" to "Buscar bucles...",
            "guide_title" to "Cómo funcionan los hábitos",
            "guide_subtitle" to "Nuestros hábitos se construyen con tres pasos simples: Señal, Rutina y Recompensa.",
            "guide_content_cue" to "1. La Señal",
            "guide_content_cue_desc" to "El aviso visual, físico o ambiental que te indica cuándo comenzar.",
            "guide_content_action" to "2. La Rutina",
            "guide_content_action_desc" to "La acción positiva o actividad que realizas tras la señal.",
            "guide_content_reward" to "3. La Recompensa",
            "guide_content_reward_desc" to "La sensación agradable que hace que quieras repetirlo mañana.",
            "guide_dismiss" to "Entendido",
            "form_title" to "Crear un Bucle de Hábito",
            "domain_title" to "Área de enfoque",
            "cadence_title" to "¿Con qué frecuencia?",
            "formula_sentence" to "Una forma sencilla de crear un hábito:\n\"Cuando yo [Señal], haré [Rutina] y disfrutaré [Recompensa].\"",
            "routine_label" to "Cuando yo... (La Señal/Disparador)",
            "routine_placeholder" to "ej. me despierte, termine de almorzar",
            "action_label" to "Haré... (La Acción)",
            "action_placeholder" to "ej. tomo un vaso de agua, escribo mis metas",
            "reward_label" to "Para disfrutar... (La Recompensa)",
            "reward_placeholder" to "ej. un momento de descanso, un café rico",
            "fields_error" to "Por favor complete todos los campos para crear su hábito.",
            "formulate_habit" to "¡Crear mi hábito!",
            "edit_dialog_title" to "Actualizar mi hábito",
            "edit_dialog_subtitle" to "La Acción (Solo lectura)",
            "edit_dialog_error" to "Por favor complete los campos de Señal y Recompensa.",
            "save_changes" to "Guardar Cambios",
            "share_copied" to "¡Progreso de AuraByte copiado al portapapeles!",
            "empty_title" to "Tu viaje de hábitos comienza aquí",
            "empty_desc" to "No has creado hábitos todavía. ¡Vamos a crear tu primera rutina positiva!",
            "empty_btn" to "Crear mi primer hábito",
            "footer_author" to "Ankit Sudegora",
            "footer_by" to "Desarrollado con Gemini y Ankit ♥️",
            "footer_ver" to "v2.0.0 (Lanzamiento Global en Play Store)",
            "domain_dashboard_title" to "Mi Equilibrio Diario por Áreas",
            "realtime_indicator" to "En vivo",
            "heatwave_dashboard_title" to "Mi Registro de Consistencia",
            "filter_week" to "Semana",
            "filter_month" to "Mes",
            "filter_year" to "Año",
            "status_starting" to "Iniciando",
            "status_growing" to "Construyendo",
            "status_thriving" to "¡Excelente!"
        ),
        AppLanguage.HINDI to mapOf(
            "app_title" to "AuraByte",
            "app_subtitle" to "आदत निर्माण में आपका सच्चा साथी",
            "share_text" to "रसीद साझा करें",
            "share_progress" to "मेरी दैनिक सफलता साझा की जा रही है!",
            "all_categories" to "सभी जीवन क्षेत्र",
            "search_placeholder" to "खोजें...",
            "guide_title" to "आदतें कैसे काम करती हैं",
            "guide_subtitle" to "हमारी आदतें तीन सरल चरणों से बनती हैं: संकेत, आदत और पुरस्कार।",
            "guide_content_cue" to "1. संकेत (Trigger)",
            "guide_content_cue_desc" to "वह इशारा जो आपको शुरू करने के लिए संकेत देता है।",
            "guide_content_action" to "2. कार्य (Action)",
            "guide_content_action_desc" to "वह सकारात्मक क्रिया या आदत जो आप संकेत के बाद करते हैं।",
            "guide_content_reward" to "3. पुरस्कार (Reward)",
            "guide_content_reward_desc" to "वह मनपसंद अनुभव जो आपके मस्तिष्क को इसे दोहराने के लिए प्रेरित करता है।",
            "guide_dismiss" to "समझ गया",
            "form_title" to "एक नई आदत का ढांचा बनाएं",
            "domain_title" to "जीवन क्षेत्र",
            "cadence_title" to "कितनी बार (Cadence)",
            "formula_sentence" to "आदत बनाने का एक आसान तरीका:\n\"जब मैं [संकेत] देखूंगा, तब मैं [कार्य] करूंगा ताकि मुझे [पुरस्कार] मिले।\"",
            "routine_label" to "जब मैं... (संकेत/इशारा)",
            "routine_placeholder" to "जैसे: सुबह उठते ही, खाना खाने के बाद",
            "action_label" to "मैं करूंगा... (मुख्य आदत)",
            "action_placeholder" to "जैसे: पानी पीऊंगा, डायरी लिखूंगा",
            "reward_label" to "ताकि आनंद मिले... (पुरस्कार)",
            "reward_placeholder" to "जैसे: एक कप चाय, थोड़ा विश्राम",
            "fields_error" to "कृपया आदत लूप के सभी हिस्सों को पूरा करें।",
            "formulate_habit" to "मेरी आदत बनाएं!",
            "edit_dialog_title" to "आदत को बदलें",
            "edit_dialog_subtitle" to "दिनचर्या शीर्षक (केवल पढ़ने के लिए)",
            "edit_dialog_error" to "संकेत और पुरस्कार विवरण बदलना आवश्यक हैं।",
            "save_changes" to "विवरण सहेजें",
            "share_copied" to "AuraByte रिपोर्ट क्लिपबोर्ड पर कॉपी की गई!",
            "empty_title" to "आपकी आदत यात्रा यहाँ से शुरू होती है",
            "empty_desc" to "आपने अभी तक कोई आदत नहीं बनाई है। चलिए शुरुआत करते हैं!",
            "empty_btn" to "पहल करें",
            "footer_author" to "Ankit Sudegora",
            "footer_by" to "Gemini और Ankit ♥️ द्वारा विकसित",
            "footer_ver" to "v2.0.0 (ग्लोबल प्ले स्टोर रिलीज)",
            "domain_dashboard_title" to "जीवन के क्षेत्रों में मेरा संतुलन",
            "realtime_indicator" to "लाइव अपडेट",
            "heatwave_dashboard_title" to "मेरी निरंतरता (Consistency)",
            "filter_week" to "सप्ताह",
            "filter_month" to "माह",
            "filter_year" to "वर्ष",
            "status_starting" to "शुरुआत",
            "status_growing" to "बेहतर हो रहा है",
            "status_thriving" to "शानदार!"
        ),
        AppLanguage.GERMAN to mapOf(
            "app_title" to "AuraByte",
            "app_subtitle" to "Ihr Partner für positive Gewohnheiten",
            "share_text" to "Erfolge teilen",
            "share_progress" to "Tägliche Erfolge geteilt!",
            "all_categories" to "Alle Lebensbereiche",
            "search_placeholder" to "Schleifen durchsuchen...",
            "guide_title" to "Gewohnheiten verstehen",
            "guide_subtitle" to "Gewohnheiten basieren auf drei einfachen Schritten: Auslöser, Routine und Belohnung.",
            "guide_content_cue" to "1. Der Auslöser",
            "guide_content_cue_desc" to "Das Signal aus Ihrem Alltag, das Ihnen anzeigt, wann Sie starten sollen.",
            "guide_content_action" to "2. Die Routine",
            "guide_content_action_desc" to "Die positive Handlung, die Sie direkt nach dem Signal ausführen.",
            "guide_content_reward" to "3. Die Belohnung",
            "guide_content_reward_desc" to "Die kleine Belohnung, die Ihrem Gehirn Freude bereitet.",
            "guide_dismiss" to "Verstanden",
            "form_title" to "Neue Gewohnheit erstellen",
            "domain_title" to "Fokus-Lebensbereich",
            "cadence_title" to "Wie oft?",
            "formula_sentence" to "So einfach klappt es:\n\"Wenn ich [Auslöser], werde ich [Routine], um mich mit [Belohnung] zu belohnen.\"",
            "routine_label" to "Wenn ich... (Der Auslöser)",
            "routine_placeholder" to "z.B. morgens aufwache, aufstehe",
            "action_label" to "Werde ich... (Die Routine)",
            "action_placeholder" to "z.B. ein Glas Wasser trinken",
            "reward_label" to "Belohnung... (Die Belohnung)",
            "reward_placeholder" to "z.B. tiefer Atemzug frischer Luft",
            "fields_error" to "Bitte füllen Sie alle Felder aus.",
            "formulate_habit" to "Gewohnheit erstellen",
            "edit_dialog_title" to "Gewohnheit anpassen",
            "edit_dialog_subtitle" to "Routine (Schreibgeschützt)",
            "edit_dialog_error" to "Auslöser und Belohnung sind erforderlich.",
            "save_changes" to "Speichern",
            "share_copied" to "AuraByte-Beleg in die Zwischenablage kopiert!",
            "empty_title" to "Ihre Gewohnheits-Reise beginnt hier",
            "empty_desc" to "Sie haben noch keine Gewohnheiten erstellt. Fangen wir an!",
            "empty_btn" to "Erste Gewohnheit erstellen",
            "footer_author" to "Ankit Sudegora",
            "footer_by" to "Entwickelt mit Gemini und Ankit ♥️",
            "footer_ver" to "v2.0.0 (Global Play Store Release)",
            "domain_dashboard_title" to "Meine Balance im Leben",
            "realtime_indicator" to "Live aktualisiert",
            "heatwave_dashboard_title" to "Meine Beständigkeit",
            "filter_week" to "Woche",
            "filter_month" to "Monat",
            "filter_year" to "Jahr",
            "status_starting" to "Aller Anfang",
            "status_growing" to "Stabilisiert",
            "status_thriving" to "Hervorragend!"
        ),
        AppLanguage.JAPANESE to mapOf(
            "app_title" to "AuraByte",
            "app_subtitle" to "習慣づくりのための頼れるサポーター",
            "share_text" to "実績を共有",
            "share_progress" to "今日の成果を共有しました！",
            "all_categories" to "すべてのライフエリア",
            "search_placeholder" to "習慣検索...",
            "guide_title" to "習慣づくりの基本",
            "guide_subtitle" to "習慣は「きっかけ」「行動」「ごほうび」の3つのシンプルなステップからできています。",
            "guide_content_cue" to "1. きっかけ (きっかけ)",
            "guide_content_cue_desc" to "行動を始める合図となる、日常のちょっとした出来事や環境の変化。",
            "guide_content_action" to "2. 行動 (アクション)",
            "guide_content_action_desc" to "合図のすぐ後に、あなたが取るポジティブな行動。",
            "guide_content_reward" to "3. ごほうび (ごほうび)",
            "guide_content_reward_desc" to "行動した後に感じる、心が温まるような嬉しいごほうび。",
            "guide_dismiss" to "了解しました",
            "form_title" to "新しい習慣をつくる",
            "domain_title" to "フォーカス分野",
            "cadence_title" to "繰り返す頻度",
            "formula_sentence" to "簡単な習慣づくりの公式:\n「【きっかけ】のとき、【行動】をして、【ごほうび】を楽しみます。」",
            "routine_label" to "【きっかけ】のとき...",
            "routine_placeholder" to "例: 朝起きたとき、食事を終えたとき",
            "action_label" to "【行動】をします...",
            "action_placeholder" to "例: お水を一杯飲む、3つの目標をメモする",
            "reward_label" to "【ごほうび】を楽しみます...",
            "reward_placeholder" to "例: 美味しいお茶を飲む、リフレッシュする",
            "fields_error" to "すべての項目を入力してください。",
            "formulate_habit" to "習慣をつくる！",
            "edit_dialog_title" to "習慣の再編集",
            "edit_dialog_subtitle" to "行動 (編集不可)",
            "edit_dialog_error" to "きっかけとごほうびを入力してください。",
            "save_changes" to "設定を保存",
            "share_copied" to "AuraByte実績をクリップボードにコピーしました！",
            "empty_title" to "習慣づくりの旅を始めましょう",
            "empty_desc" to "まだ習慣が登録されていません。最初のハッピー習慣をつくってみませんか？",
            "empty_btn" to "最初の習慣を作る",
            "footer_author" to "Ankit Sudegora",
            "footer_by" to "Gemini と Ankit ♥️ による開発",
            "footer_ver" to "v2.0.0 (グローバル Playストア公開版)",
            "domain_dashboard_title" to "ライフエリアのバランス",
            "realtime_indicator" to "リアルタイム更新",
            "heatwave_dashboard_title" to "習慣の継続状況",
            "filter_week" to "週",
            "filter_month" to "月",
            "filter_year" to "年",
            "status_starting" to "はじめの一歩",
            "status_growing" to "継続中",
            "status_thriving" to "素晴らしい！"
        ),
        AppLanguage.PORTUGUESE to mapOf(
            "app_title" to "AuraByte",
            "app_subtitle" to "Seu companheiro para construir hábitos positivos",
            "share_text" to "Compartilhar Progresso",
            "share_progress" to "Celebrando meu progresso diário!",
            "all_categories" to "Todas as áreas da vida",
            "search_placeholder" to "Buscar hábitos...",
            "guide_title" to "Como os hábitos funcionam",
            "guide_subtitle" to "Nossos hábitos são formados por três passos simples: Gatilho, Ação e Recompensa.",
            "guide_content_cue" to "1. O Gatilho (Gatilho)",
            "guide_content_cue_desc" to "O sinal do seu dia a dia que avisa ao seu cérebro que é hora de começar.",
            "guide_content_action" to "2. A Ação (Rotina)",
            "guide_content_action_desc" to "A ação ou ritual positivo que você realiza logo após o gatilho.",
            "guide_content_reward" to "3. A Recompensa",
            "guide_content_reward_desc" to "A sensação agradável que faz seu cérebro querer repetir a ação amanhã.",
            "guide_dismiss" to "Entendido",
            "form_title" to "Criar Novo Hábito",
            "domain_title" to "Área de Foco",
            "cadence_title" to "Com que frequência?",
            "formula_sentence" to "Uma maneira simples de criar hábitos:\n\"Quando eu [Gatilho], farei [Ação] para desfrutar [Recompensa].\"",
            "routine_label" to "Quando eu... (Gatilho)",
            "routine_placeholder" to "ex: acordar pela manhã, terminar de almoçar",
            "action_label" to "Farei... (A Ação)",
            "action_placeholder" to "ex: tomar um copo de água, planejar meu dia",
            "reward_label" to "Como recompensa... (Recompensa)",
            "reward_placeholder" to "ex: tomar um bom chá, um minuto de descanso",
            "fields_error" to "Por favor, complete todos os campos para iniciar.",
            "formulate_habit" to "Criar meu hábito!",
            "edit_dialog_title" to "Atualizar hábito",
            "edit_dialog_subtitle" to "A Ação (Apenas leitura)",
            "edit_dialog_error" to "Os campos de gatilho e recompensa são obrigatórios.",
            "save_changes" to "Salvar alterações",
            "share_copied" to "Progresso do AuraByte copiado para o clipboard!",
            "empty_title" to "Sua jornada de hábitos começa aqui",
            "empty_desc" to "Você ainda não tem hábitos criados. Vamos criar seu primeiro hábito positivo!",
            "empty_btn" to "Criar meu primeiro hábito",
            "footer_author" to "Ankit Sudegora",
            "footer_by" to "Desenvolvido com Gemini e Ankit ♥️",
            "footer_ver" to "v2.0.0 (Lanzamiento Global Google Play)",
            "domain_dashboard_title" to "Meu Equilíbrio Diário por Áreas",
            "realtime_indicator" to "Em tempo real",
            "heatwave_dashboard_title" to "Meu Registro de Consistência",
            "filter_week" to "Semana",
            "filter_month" to "Mês",
            "filter_year" to "Ano",
            "status_starting" to "Começando",
            "status_growing" to "Construindo",
            "status_thriving" to "Excelente!"
        )
    )

    fun get(lang: AppLanguage, key: String): String {
        return strings[lang]?.get(key) ?: strings[AppLanguage.ENGLISH]?.get(key) ?: ""
    }
}
