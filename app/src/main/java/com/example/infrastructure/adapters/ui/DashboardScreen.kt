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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.domain.Cadence
import com.example.core.domain.Habit
import com.example.core.domain.LifeDomain
import com.example.core.domain.isApplicableOn
import com.example.core.domain.ActivityLog
import com.example.core.domain.ActivityCategory
import androidx.compose.ui.text.TextStyle
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

enum class DashboardTab {
    DASHBOARD,
    CREATE_HABIT,
    ACTIVITY_LOGGER
}

enum class ActivitySortOption(val displayName: String) {
    NEWEST_FIRST("⏱️ Newest First"),
    OLDEST_FIRST("🕰️ Oldest First"),
    DURATION_DESC("⏳ Longest"),
    DURATION_ASC("⚡ Shortest")
}

enum class ActivityDateFilterOption(val displayName: String) {
    ALL("📅 All Days"),
    SELECTED_DATE("🎯 Selected Day"),
    TODAY("☀️ Today"),
    PAST_7_DAYS("🔄 Past 7 Days")
}

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
    logs: Map<String, Map<String, Boolean>>,
    selectedDate: String
): Map<LifeDomain, Int> {
    val domainOpportunities = mutableMapOf<LifeDomain, Int>()
    val domainCompletions = mutableMapOf<LifeDomain, Int>()
    
    // Initialize maps
    LifeDomain.values().forEach {
        domainOpportunities[it] = 0
        domainCompletions[it] = 0
    }
    
    // Let's compute only for the specific selectedDate
    val habitCompletions = logs[selectedDate] ?: emptyMap()
    habits.forEach { habit ->
        if (habit.cadence.isApplicableOn(selectedDate)) {
            val domain = habit.domain
            domainOpportunities[domain] = (domainOpportunities[domain] ?: 0) + 1
            
            val isCompleted = habitCompletions[habit.id] == true
            if (isCompleted) {
                domainCompletions[domain] = (domainCompletions[domain] ?: 0) + 1
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
    
    // Active navigation tab
    var activeTab by rememberSaveable { mutableStateOf(DashboardTab.DASHBOARD) }
    
    // Active Regional Language Settings (Defaults to English)
    var selectedLanguage by rememberSaveable { mutableStateOf(AppLanguage.ENGLISH) }
    
    // Manage state for displaying regional selector menu
    var showLanguageMenu by remember { mutableStateOf(false) }
    
    // Manage state for the Interactive Charles Duhigg Tutorial Guide
    var showTutorialGuide by rememberSaveable { mutableStateOf(false) }
    
    // Manage state for the Settings and FAQ page dialog
    var showSettingsAndFaq by remember { mutableStateOf(false) }
    
    // Search query for filtering routines
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    // Category quick filter by domain
    var selectedFilterDomain by remember { mutableStateOf<LifeDomain?>(null) }
    
    // Manage state for the customized social media share dialogue
    var showShareDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // Function to show the beautiful Share Dialog
    val onShareAchievement = {
        showShareDialog = true
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
                            // Settings and FAQ Button
                            IconButton(onClick = { showSettingsAndFaq = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Open Settings & FAQ",
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
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Toggle Palette Theme",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )
            },

            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier.testTag("dashboard_bottom_navigation")
                ) {
                    val tabs = listOf(
                        Triple(DashboardTab.DASHBOARD, Icons.Default.CheckCircle, when (selectedLanguage) {
                            AppLanguage.SPANISH -> "Rutinas"
                            AppLanguage.HINDI -> "डैशबोर्ड"
                            AppLanguage.GERMAN -> "Übersicht"
                            AppLanguage.JAPANESE -> "ダッシュボード"
                            AppLanguage.PORTUGUESE -> "Rotinas"
                            else -> "Dashboard"
                        }),
                        Triple(DashboardTab.CREATE_HABIT, Icons.Default.Add, when (selectedLanguage) {
                            AppLanguage.SPANISH -> "Crear"
                            AppLanguage.HINDI -> "बनाएं"
                            AppLanguage.GERMAN -> "Erstellen"
                            AppLanguage.JAPANESE -> "作成"
                            AppLanguage.PORTUGUESE -> "Criar"
                            else -> "Create"
                        }),
                        Triple(DashboardTab.ACTIVITY_LOGGER, Icons.Default.List, when (selectedLanguage) {
                            AppLanguage.SPANISH -> "Actividades"
                            AppLanguage.HINDI -> "टाइम लॉग"
                            AppLanguage.GERMAN -> "Aktivitäten"
                            AppLanguage.JAPANESE -> "履歴"
                            AppLanguage.PORTUGUESE -> "Atividades"
                            else -> "Logger"
                        })
                    )
                    
                    tabs.forEach { (tab, icon, label) ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Icon(imageVector = icon, contentDescription = label) },
                            label = { Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                        )
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (activeTab == DashboardTab.DASHBOARD) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
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
                        logs = uiState.logs,
                        selectedDate = uiState.selectedDate
                    )
                }

                // HabitEngine Heatwave Analytics Monitor
                item {
                    HeatwaveDashboard(
                        selectedLanguage = selectedLanguage,
                        habits = uiState.habits,
                        logs = uiState.logs,
                        selectedDate = uiState.selectedDate
                    )
                }

                // Real-time Activity Logger & Time Audit
                item {
                    ActivityLoggerConsole(
                        selectedLanguage = selectedLanguage,
                        selectedDate = uiState.selectedDate,
                        activityLogs = uiState.activityLogs,
                        onCreateActivity = { desc, cat, duration ->
                            viewModel.createActivityLog(desc, cat, duration)
                        },
                        onDeleteActivity = { id ->
                            viewModel.deleteActivityLog(id)
                        },
                        showCreateForm = false
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
                
                val goodHabits = filteredHabits.filter { !it.isBad }
                val badHabits = filteredHabits.filter { it.isBad }

                if (filteredHabits.isEmpty()) {
                    item {
                        EmptyStateCard(selectedLanguage = selectedLanguage, onNewHabitClick = { activeTab = DashboardTab.CREATE_HABIT })
                    }
                } else {
                    if (goodHabits.isNotEmpty()) {
                        item {
                            Text(
                                text = "Actionable Routines (Good Habits) 🌸",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        items(goodHabits, key = { it.id }) { habit ->
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

                    if (badHabits.isNotEmpty()) {
                        item {
                            Text(
                                text = "Avoidance & Impulse Control (Bad Habits) 🛡️",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                            )
                        }
                        items(badHabits, key = { it.id }) { habit ->
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

        if (activeTab == DashboardTab.CREATE_HABIT) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                CreateHabitInlineScreen(
                    selectedLanguage = selectedLanguage,
                    onSubmit = { domain, cadence, cue, routine, reward, notes, isBad ->
                        viewModel.createHabit(domain, cadence, cue, routine, reward, notes, isBad)
                        activeTab = DashboardTab.DASHBOARD
                    }
                )
            }
        }

        if (activeTab == DashboardTab.ACTIVITY_LOGGER) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        Box(modifier = Modifier.padding(16.dp)) {
                            ActivityLoggerConsole(
                                selectedLanguage = selectedLanguage,
                                selectedDate = uiState.selectedDate,
                                activityLogs = uiState.activityLogs,
                                onCreateActivity = { desc, cat, duration ->
                                    viewModel.createActivityLog(desc, cat, duration)
                                },
                                onDeleteActivity = { id ->
                                    viewModel.deleteActivityLog(id)
                                }
                            )
                        }
                    }
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
                onSubmit = { domain, cadence, cue, routine, reward, notes, isBad ->
                    viewModel.createHabit(domain, cadence, cue, routine, reward, notes, isBad)
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
                onSubmit = { updatedDomain, updatedCue, updatedReward, updatedNotes, updatedIsBad ->
                    viewModel.updateHabit(
                        habitId = habitToEdit.id,
                        domain = updatedDomain,
                        cadence = habitToEdit.cadence,
                        cueText = updatedCue,
                        routineText = habitToEdit.routineText,
                        rewardText = updatedReward,
                        createdAt = habitToEdit.createdAt,
                        notes = updatedNotes,
                        isBad = updatedIsBad
                    )
                    editingHabit = null
                }
            )
        }

        val context = LocalContext.current
        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                try {
                    val jsonString = viewModel.exportBackupAsJson()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Backup saved to device!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val jsonString = inputStream.bufferedReader().use { it.readText() }
                        viewModel.restoreBackupFromJson(jsonString) { success ->
                            if (success) {
                                Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to restore backup format.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        if (showSettingsAndFaq) {
            SettingsAndFaqDialog(
                selectedLanguage = selectedLanguage,
                showTutorialGuide = showTutorialGuide,
                onToggleTutorialGuide = { showTutorialGuide = it },
                onExportBackup = { exportLauncher.launch("HabitEngine_backup_${System.currentTimeMillis()}.json") },
                onImportBackup = { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
                onDismiss = { showSettingsAndFaq = false }
            )
        }

        if (showShareDialog) {
            ShareProgressDialog(
                uiState = uiState,
                selectedLanguage = selectedLanguage,
                onDismiss = { showShareDialog = false }
            )
        }
    }
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
    logs: Map<String, Map<String, Boolean>>,
    selectedDate: String
) {
    // Perform data aggregation mapping
    val masteryMap = remember(habits, logs, selectedDate) {
        calculateDomainMastery(habits, logs, selectedDate)
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
            containerColor = if (isCompleted) {
                if (habit.isBad) Color(0xFF10B981).copy(alpha = 0.07f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
            } else {
                if (habit.isBad) MaterialTheme.colorScheme.error.copy(alpha = 0.015f)
                else MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isCompleted) {
                if (habit.isBad) Color(0xFF10B981).copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            } else {
                if (habit.isBad) MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            }
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
                            maxLines = if (expanded) 4 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (habit.isBad) "AVOIDANCE 🛡️" else habit.domain.displayName,
                                fontSize = 11.sp,
                                color = if (habit.isBad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontWeight = if (habit.isBad) FontWeight.Bold else FontWeight.Normal
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
                                if (isCompleted) {
                                    if (habit.isBad) Color(0xFF10B981)
                                    else MaterialTheme.colorScheme.primary
                                } else {
                                    if (habit.isBad) MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                }
                            )
                            .border(
                                1.dp,
                                if (isCompleted) Color.Transparent else {
                                    if (habit.isBad) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                },
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

            // Expanded Habit loop showing the Charles Duhigg formula and notes
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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

                    if (habit.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Notes / Context:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = habit.notes,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isSelectable) {
                            Text(
                                text = "⚠️ Non-applicable date: filtered",
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Edit Button (Primary-colored, outline styling for prominence)
                            OutlinedButton(
                                onClick = onEdit,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Habit Description",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (selectedLanguage) {
                                        AppLanguage.SPANISH -> "Editar"
                                        AppLanguage.HINDI -> "संपादित करें"
                                        AppLanguage.GERMAN -> "Bearbeiten"
                                        AppLanguage.JAPANESE -> "編集"
                                        AppLanguage.PORTUGUESE -> "Editar"
                                        else -> "Edit"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            // Delete Button (Critical red error outline style)
                            OutlinedButton(
                                onClick = onDelete,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.8f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Habit",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (selectedLanguage) {
                                        AppLanguage.SPANISH -> "Eliminar"
                                        AppLanguage.HINDI -> "हटाएं"
                                        AppLanguage.GERMAN -> "Löschen"
                                        AppLanguage.JAPANESE -> "削除"
                                        AppLanguage.PORTUGUESE -> "Excluir"
                                        else -> "Delete"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
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
    onSubmit: (domain: LifeDomain, cadence: Cadence, cueText: String, routineText: String, rewardText: String, notesText: String, isBad: Boolean) -> Unit
) {
    var domain by remember { mutableStateOf(LifeDomain.HEALTH) }
    var cadence by remember { mutableStateOf(Cadence.DAILY) }
    var notesText by remember { mutableStateOf("") }
    var isBad by remember { mutableStateOf(false) }
    
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

                // Habit Type Selection (Good vs Bad)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Habit Type:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (!isBad) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                )
                                .border(
                                    if (!isBad) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { isBad = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Routine (Good Habit) 🌸", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!isBad) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isBad) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                )
                                .border(
                                    if (isBad) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else BorderStroke(0.dp, Color.Transparent),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { isBad = true }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Avoidance (Bad Habit) 🛡️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isBad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
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
                        label = { Text(Localizations.get(selectedLanguage, "routine_label") + " (Optional)") },
                        placeholder = { Text(Localizations.get(selectedLanguage, "routine_placeholder")) },
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
                        label = { Text(Localizations.get(selectedLanguage, "reward_label") + " (Optional)") },
                        placeholder = { Text(Localizations.get(selectedLanguage, "reward_placeholder")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Personal Notes / Tips / Avoidance Strategy") },
                        placeholder = { Text("e.g. Put phone in drawer, start small with 2 mins...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        )
                    )
                }
 
                if (hasAttemptedSubmit && routineText.isBlank()) {
                    Text(
                        text = "The habit name/action cannot be blank.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
 
                Button(
                    onClick = {
                        hasAttemptedSubmit = true
                        if (routineText.isNotBlank()) {
                            focusManager.clearFocus()
                            onSubmit(domain, cadence, cueText, routineText, rewardText, notesText, isBad)
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

@Composable
fun ActivityLoggerConsole(
    selectedLanguage: AppLanguage,
    selectedDate: String,
    activityLogs: List<ActivityLog>,
    onCreateActivity: (String, ActivityCategory, Int) -> Unit,
    onDeleteActivity: (String) -> Unit,
    showCreateForm: Boolean = true
) {
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ActivityCategory.IMPORTANT) }
    var durationMinutes by remember { mutableStateOf(30) } // default 30 min
    
    var activeSort by rememberSaveable { mutableStateOf(ActivitySortOption.NEWEST_FIRST) }
    var activeDateFilter by rememberSaveable { mutableStateOf(ActivityDateFilterOption.SELECTED_DATE) }
    var searchQueryLogs by rememberSaveable { mutableStateOf("") }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date()) }

    val filteredActivities = remember(activityLogs, selectedDate, activeDateFilter, activeSort, searchQueryLogs) {
        val baseList = activityLogs.filter { log ->
            val matchesDate = when (activeDateFilter) {
                ActivityDateFilterOption.SELECTED_DATE -> {
                    try {
                        sdf.format(java.util.Date(log.timestamp)) == selectedDate
                    } catch (e: Exception) {
                        false
                    }
                }
                ActivityDateFilterOption.TODAY -> {
                    try {
                        sdf.format(java.util.Date(log.timestamp)) == todayStr
                    } catch (e: Exception) {
                        false
                    }
                }
                ActivityDateFilterOption.PAST_7_DAYS -> {
                    val msIn7Days = 7L * 24L * 60L * 60L * 1000L
                    (System.currentTimeMillis() - log.timestamp) <= msIn7Days
                }
                ActivityDateFilterOption.ALL -> true
            }

            val matchesSearch = searchQueryLogs.isBlank() || log.description.contains(searchQueryLogs, ignoreCase = true)
            matchesDate && matchesSearch
        }

        when (activeSort) {
            ActivitySortOption.NEWEST_FIRST -> baseList.sortedByDescending { it.timestamp }
            ActivitySortOption.OLDEST_FIRST -> baseList.sortedBy { it.timestamp }
            ActivitySortOption.DURATION_DESC -> baseList.sortedByDescending { it.durationMinutes }
            ActivitySortOption.DURATION_ASC -> baseList.sortedBy { it.durationMinutes }
        }
    }
    
    // Analytics calculations
    val importantMinutes = filteredActivities.filter { it.category == ActivityCategory.IMPORTANT }.sumOf { it.durationMinutes }
    val wastedMinutes = filteredActivities.filter { it.category == ActivityCategory.TIME_WASTER }.sumOf { it.durationMinutes }
    val neutralMinutes = filteredActivities.filter { it.category == ActivityCategory.NEUTRAL }.sumOf { it.durationMinutes }
    val totalMinutes = importantMinutes + wastedMinutes + neutralMinutes
    
    val focusScore = if (importantMinutes + wastedMinutes == 0) 100 else {
        ((importantMinutes.toFloat() / (importantMinutes + wastedMinutes).toFloat()) * 100).toInt()
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Real-time Time Audit & Focus Analyzer ⚡",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Audit your time to eliminate toxic habits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            
            // Dynamic Stats Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.025f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Focus Gauge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                focusScore >= 75 -> Color(0xFF10B981).copy(alpha = 0.15f)
                                focusScore >= 40 -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$focusScore%",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                focusScore >= 75 -> Color(0xFF10B981)
                                focusScore >= 40 -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            text = "Score",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                
                // Textual Breakdowns
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Deep Focus / Important:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Text("${importantMinutes}m", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Time Wasted / Drifts:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Text("${wastedMinutes}m", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Routine / Neutral Tasks:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Text("${neutralMinutes}m", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            
            // Inline Add Activity Dialog Form Controls
            if (showCreateForm) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "LOG RUNTIME ACTIVITY:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("e.g. Studying, browsing reels, gym, meditation...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(fontSize = 13.sp),
                        singleLine = true
                    )
                    
                    // Category click badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            ActivityCategory.IMPORTANT,
                            ActivityCategory.TIME_WASTER,
                            ActivityCategory.NEUTRAL
                        ).forEach { cat ->
                            val isSelected = category == cat
                            val catColor = when (cat) {
                                ActivityCategory.IMPORTANT -> MaterialTheme.colorScheme.primary
                                ActivityCategory.TIME_WASTER -> MaterialTheme.colorScheme.error
                                ActivityCategory.NEUTRAL -> MaterialTheme.colorScheme.secondary
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) catColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) catColor else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { category = cat }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat.displayName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    
                    // Stepper for duration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Duration: $durationMinutes minutes",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { if (durationMinutes > 5) durationMinutes -= 5 },
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            IconButton(
                                onClick = { durationMinutes += 5 },
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                    
                    Button(
                        onClick = {
                            if (description.isNotBlank()) {
                                onCreateActivity(description.trim(), category, durationMinutes)
                                description = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Add timestamped activity log ⚡", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            }

            // Filters & Controls Panel
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "FILTER RANGE:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ActivityDateFilterOption.values().toList()) { opt ->
                        val isSelected = activeDateFilter == opt
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { activeDateFilter = opt }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = opt.displayName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SORT ORDER:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ActivitySortOption.values().toList()) { opt ->
                        val isSelected = activeSort == opt
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { activeSort = opt }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = opt.displayName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQueryLogs,
                    onValueChange = { searchQueryLogs = it },
                    textStyle = TextStyle(fontSize = 11.sp),
                    placeholder = { Text("Search logs by keyword...", fontSize = 11.sp) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search logs", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }

            // Vertically scrolling list of logged activities
            if (filteredActivities.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                Text(
                    text = "REVIEWS & LOGS (" + filteredActivities.size + ")",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val timeSdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
                    val dateSdf = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
                    filteredActivities.forEach { log ->
                        val itemColor = when (log.category) {
                            ActivityCategory.IMPORTANT -> MaterialTheme.colorScheme.primary
                            ActivityCategory.TIME_WASTER -> MaterialTheme.colorScheme.error
                            ActivityCategory.NEUTRAL -> MaterialTheme.colorScheme.secondary
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(itemColor.copy(alpha = 0.03f))
                                .border(1.dp, itemColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Bullet node
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(itemColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = log.description,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = try {
                                                val datePart = dateSdf.format(java.util.Date(log.timestamp))
                                                val timePart = timeSdf.format(java.util.Date(log.timestamp))
                                                "$datePart • $timePart"
                                            } catch(e: Exception) { "" },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                        Text(
                                            text = "${log.durationMinutes} min",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { onDeleteActivity(log.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Log",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No activities found matching criteria.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

// HabitEngine Heatwave Consistency Dashboard
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
    onSubmit: (domain: LifeDomain, cueText: String, rewardText: String, notesText: String, isBad: Boolean) -> Unit
) {
    var selectedDomain by remember { mutableStateOf(habit.domain) }
    var notesText by remember { mutableStateOf(habit.notes) }
    var isBad by remember { mutableStateOf(habit.isBad) }

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
 
                // Domain (Life Area) Selection Header (Interactive Editor!)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Change Life Area Focus:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LifeDomain.values().forEach { d ->
                            val isSelected = selectedDomain == d
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) getDomainColor(d).copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                    .clickable { selectedDomain = d }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = d.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Habit Type Selection (Good vs Bad)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Habit Type:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (!isBad) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                )
                                .border(
                                    if (!isBad) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { isBad = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Routines (Good Habit) 🌸", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!isBad) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isBad) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                )
                                .border(
                                    if (isBad) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else BorderStroke(0.dp, Color.Transparent),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { isBad = true }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Avoidance (Bad Habit) 🛡️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isBad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
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
                        label = { Text(Localizations.get(selectedLanguage, "routine_label") + " (Optional)") },
                        placeholder = { Text(Localizations.get(selectedLanguage, "routine_placeholder")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )
 
                    OutlinedTextField(
                        value = rewardText,
                        onValueChange = { rewardText = it },
                        label = { Text(Localizations.get(selectedLanguage, "reward_label") + " (Optional)") },
                        placeholder = { Text(Localizations.get(selectedLanguage, "reward_placeholder")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )
 
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Personal Notes / Tips / Avoidance Strategy") },
                        placeholder = { Text("e.g. Put phone in drawer, start small with 2 mins...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        )
                    )
                }
 
                Button(
                    onClick = {
                        hasAttemptedSubmit = true
                        focusManager.clearFocus()
                        onSubmit(selectedDomain, cueText, rewardText, notesText, isBad)
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
            "app_title" to "HabitEngine",
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
            "share_copied" to "HabitEngine progress copied to clipboard!",
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
            "app_title" to "HabitEngine",
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
            "share_copied" to "¡Progreso de HabitEngine copiado al portapapeles!",
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
            "app_title" to "HabitEngine",
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
            "share_copied" to "HabitEngine रिपोर्ट क्लिपबोर्ड पर कॉपी की गई!",
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
            "app_title" to "HabitEngine",
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
            "share_copied" to "HabitEngine-Beleg in die Zwischenablage kopiert!",
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
            "app_title" to "HabitEngine",
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
            "share_copied" to "HabitEngine実績をクリップボードにコピーしました！",
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
            "app_title" to "HabitEngine",
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
            "share_copied" to "Progresso do HabitEngine copiado para o clipboard!",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAndFaqDialog(
    selectedLanguage: AppLanguage,
    showTutorialGuide: Boolean,
    onToggleTutorialGuide: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    var activeTab by remember { mutableIntStateOf(0) }
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    
    val faqItems = getFaqItems(selectedLanguage)
    val doc = getLifeAreaDoc(selectedLanguage)

    val backupHeader = when (selectedLanguage) {
        AppLanguage.SPANISH -> "💾 Copia de Seguridad"
        AppLanguage.HINDI -> "💾 स्थानीय डिवाइस बैकअप"
        AppLanguage.GERMAN -> "💾 Lokales Geräte-Backup"
        AppLanguage.JAPANESE -> "💾 ローカルバックアップ"
        AppLanguage.PORTUGUESE -> "💾 Backup do Dispositivo"
        else -> "💾 Local Device Backup"
    }

    val backupDesc = when (selectedLanguage) {
        AppLanguage.SPANISH -> "Exporta tus hábitos e historial a un archivo JSON o restáuralos desde una copia previa."
        AppLanguage.HINDI -> "अपनी आदतों, लॉग और इतिहास को JSON फ़ाइल में सहेजें या पूर्व बैकअप से पुनर्स्थापित करें।"
        AppLanguage.GERMAN -> "Exportieren Sie Ihre Gewohnheiten in eine JSON-Datei oder stellen Sie sie wieder her."
        AppLanguage.JAPANESE -> "習慣と履歴をJSONファイルにエクスポート、または既存の物から復元を行います。"
        AppLanguage.PORTUGUESE -> "Exporte seus hábitos e histórico para um arquivo JSON ou restaure de uma cópia prévia."
        else -> "Export your habits and history to a JSON file or restore from a previous backup on your device."
    }

    val exportLabel = when (selectedLanguage) {
        AppLanguage.SPANISH -> "Exportar"
        AppLanguage.HINDI -> "निर्यात करें"
        AppLanguage.GERMAN -> "Backup erstellen"
        AppLanguage.JAPANESE -> "エクスポート"
        AppLanguage.PORTUGUESE -> "Exportar Cópia"
        else -> "Export Backup"
    }

    val importLabel = when (selectedLanguage) {
        AppLanguage.SPANISH -> "Importar"
        AppLanguage.HINDI -> "आयात करें"
        AppLanguage.GERMAN -> "Backup einspielen"
        AppLanguage.JAPANESE -> "インポート"
        AppLanguage.PORTUGUESE -> "Importar Cópia"
        else -> "Import Backup"
    }
    
    val settingsTitle = when (selectedLanguage) {
        AppLanguage.SPANISH -> "Ajustes de HabitEngine"
        AppLanguage.HINDI -> "HabitEngine सेटिंग्स"
        AppLanguage.GERMAN -> "HabitEngine-Einstellungen"
        AppLanguage.JAPANESE -> "HabitEngine 設定"
        AppLanguage.PORTUGUESE -> "Configurações do HabitEngine"
        else -> "HabitEngine Settings & FAQs"
    }
    
    val faqTabLabel = when (selectedLanguage) {
        AppLanguage.SPANISH -> "💡 Preguntas"
        AppLanguage.HINDI -> "💡 मुख्य प्रश्न"
        AppLanguage.GERMAN -> "💡 FAQ-Guide"
        AppLanguage.JAPANESE -> "💡 よくある質問"
        AppLanguage.PORTUGUESE -> "💡 Perguntas"
        else -> "💡 FAQ Guide"
    }

    val docTabLabel = when (selectedLanguage) {
        AppLanguage.SPANISH -> "⚖️ Filosofía 4 Áreas"
        AppLanguage.HINDI -> "⚖️ ४ क्षेत्र दर्शन"
        AppLanguage.GERMAN -> "⚖️ 4 Bereiche"
        AppLanguage.JAPANESE -> "⚖️ 4つのライフの解説"
        AppLanguage.PORTUGUESE -> "⚖️ Filosofia 4 Áreas"
        else -> "⚖️ 4 Life Areas"
    }

    val tutorialToggleLabel = when (selectedLanguage) {
        AppLanguage.SPANISH -> "Mostrar guía interactiva de hábitos"
        AppLanguage.HINDI -> "दैनिक आदत गाइड प्रदर्शित करें"
        AppLanguage.GERMAN -> "Interaktiven Gewohnheits-Guide anzeigen"
        AppLanguage.JAPANESE -> "習慣ガイドを表示"
        AppLanguage.PORTUGUESE -> "Mostrar guia de hábitos"
        else -> "Display dashboard habit loop guide"
    }
    
    val doneBtnText = when (selectedLanguage) {
        AppLanguage.SPANISH -> "Entendido"
        AppLanguage.HINDI -> "सहेजें"
        AppLanguage.GERMAN -> "Schließen"
        AppLanguage.JAPANESE -> "完了"
        AppLanguage.PORTUGUESE -> "Fechar"
        else -> "Close Settings"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = settingsTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Settings",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Custom Tab Row Capsules
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(faqTabLabel, docTabLabel).forEachIndexed { index, label ->
                        val isSelected = activeTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { activeTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.61f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                // Scrollable container
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (activeTab == 0) {
                        // FAQ tab
                        faqItems.forEachIndexed { index, faq ->
                            val isExpanded = expandedIndex == index
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isExpanded) faq.categoryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                        ),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { expandedIndex = if (isExpanded) null else index }
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(text = faq.icon, fontSize = 15.sp)
                                        Text(
                                            text = faq.question,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isExpanded) faq.categoryColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = if (isExpanded) "▲" else "▼",
                                        fontSize = 10.sp,
                                        color = if (isExpanded) faq.categoryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = faq.answer,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Philosophy tab
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), RoundedCornerShape(14.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = doc.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = doc.introduction,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                                    lineHeight = 16.sp
                                )
                            }
                            
                            doc.areas.forEach { (areaTitle, areaDesc, areaColor) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(areaColor.copy(alpha = 0.04f))
                                        .border(BorderStroke(1.dp, areaColor.copy(alpha = 0.15f)), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(areaColor)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = areaTitle,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = areaColor
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = areaDesc,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.77f),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = doc.conclusion,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                Spacer(modifier = Modifier.height(12.dp))

                // Local Backup Section
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("local_backup_section"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = backupHeader,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = backupDesc,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onExportBackup,
                                modifier = Modifier.weight(1f).testTag("export_backup_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Export icon",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(exportLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            FilledTonalButton(
                                onClick = onImportBackup,
                                modifier = Modifier.weight(1f).testTag("import_backup_button"),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Import icon",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(importLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Bottom control actions: Interactive Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tutorialToggleLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = showTutorialGuide,
                        onCheckedChange = onToggleTutorialGuide,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = doneBtnText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Function to calculate consecutive days with at least one completion
fun calculateCurrentStreak(logs: Map<String, Map<String, Boolean>>, selectedDate: String): Int {
    if (logs.isEmpty()) return 0
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    val cal = java.util.Calendar.getInstance()
    
    val curDate = try {
        sdf.parse(selectedDate) ?: java.util.Date()
    } catch(e: Exception) {
        java.util.Date()
    }
    cal.time = curDate
    
    var streak = 0
    var dateStr = sdf.format(cal.time)
    
    for (i in 0..1000) {
        val dayCompletions = logs[dateStr] ?: emptyMap()
        val hasCompletion = dayCompletions.values.any { it == true }
        
        if (hasCompletion) {
            streak++
            cal.add(java.util.Calendar.DATE, -1)
            dateStr = sdf.format(cal.time)
        } else {
            if (i == 0) {
                cal.add(java.util.Calendar.DATE, -1)
                dateStr = sdf.format(cal.time)
                val yesterdayCompletions = logs[dateStr] ?: emptyMap()
                val hasYesterdayCompletion = yesterdayCompletions.values.any { it == true }
                if (hasYesterdayCompletion) {
                    streak++
                    cal.add(java.util.Calendar.DATE, -1)
                    dateStr = sdf.format(cal.time)
                    continue
                }
            }
            break
        }
    }
    return streak
}

private fun getInstagramTemplate(percentage: Int, streak: Int, url: String, lang: AppLanguage): String {
    val nonZeroStreak = if (streak > 0) streak else 1
    return when (lang) {
        AppLanguage.SPANISH -> "Engine de disciplina, bloque a bloque. 🚀 Equilibrando mis 4 cuadrantes con HabitEngine de la psicología de bucle de Charles Duhigg. Sin fricción, pura ejecución. ⚡️\n\n📊 Nivel diario: $percentage% completado\n🔥 Racha Heatwave: $nonZeroStreak días\n\nOptimiza tu rutina diaria y equilibra tu vida de forma offline: $url\n\n#HabitEngine #BucleDeHabitos #Productividad #Disciplina #Desarrolladores"
        AppLanguage.HINDI -> "अनुशासन के बाइट्स, ब्लॉक दर ब्लॉक। 🚀 आज मैंने चार्ल्स डुहिंग के हैबिट लूप की मदद से अपने जीवन के ४ क्षेत्रों को संतुलित किया। कोई बहाना नहीं, सिर्फ फोकस। ⚡️\n\n📊 दैनिक स्तर: $percentage% पूरा हुआ\n🔥 हीटवेव स्ट्रीक: $nonZeroStreak दिन\n\nअपने रूटीन को अपग्रेड करें और जीवन में संतुलन पाएं: $url\n\n#HabitEngine #HabitLoop #SelfDiscipline #Focus #HindiDevs"
        AppLanguage.GERMAN -> "Disziplin-Engine, Stein für Stein. 🚀 Heute habe ich meine 4 Lebensquadranten mit HabitEngine nach der Gewohnheitspsychologie von Charles Duhigg ausbalanciert. Keine Ausreden, nur Ausführung. ⚡️\n\n📊 Tages-Level: $percentage% abgeschlossen\n🔥 Heatwave-Serie: $nonZeroStreak Tage\n\nOptimiere deine Routinen und bringe Balance in dein Leben: $url\n\n#HabitEngine #HabitLoop #Produktivität #Disziplin #DevLife"
        AppLanguage.JAPANESE -> "規律の積み重ね、1日1歩。🚀 チャールズ・デュヒッグ式習慣ループを活用して、HabitEngineで人生の4つの柱のバランスを整えました。摩擦ゼロ、圧倒的実行力。⚡️\n\n📊 今日のフォーカス率: $percentage% 達成\n🔥 継続の熱量（Heatwave）: $nonZeroStreak 日連続\n\n習慣ルーティンワークスペースを最適化しましょう。HabitEngineをダウンロード: $url\n\n#HabitEngine #習慣ループ #ハック #自己管理 #デベロッパー"
        AppLanguage.PORTUGUESE -> "Engine de disciplina, bloco por bloco. 🚀 Equilibrei meus 4 quadrantes de vida hoje com o HabitEngine, baseado no loop de hábitos de Charles Duhigg. Sem desculpas, apenas execução. ⚡️\n\n📊 Nível diário: $percentage% concluído\n🔥 Streak de calor (Heatwave): $nonZeroStreak dias\n\nTurbine seus hábitos e equilibre sua vida: $url\n\n#HabitEngine #LoopDeHabitos #Foco #Produtividade #Devs"
        else -> "Engine of discipline, block by block. 🚀 Managed my 4 life quadrants today with HabitEngine of Charles Duhigg behavior loops. No friction, just pure execution. ⚡️\n\n📊 Daily level: $percentage% completed\n🔥 Heatwave Streak: $nonZeroStreak days\n\nCheck out my routine workspace and balance your life. Download HabitEngine at: $url\n\n#HabitEngine #HabitLoop #DevDiscipline #BuildingInPublic #Productivity"
    }
}

private fun getWhatsappTemplate(percentage: Int, streak: Int, date: String, completedList: String, url: String, lang: AppLanguage): String {
    val nonZeroStreak = if (streak > 0) streak else 1
    return when (lang) {
        AppLanguage.SPANISH -> "🏆 *HabitEngine | Mi Progreso de Hábitos* 🏆\n📅 *Fecha*: $date\n🔥 *Racha actual*: $nonZeroStreak Días\n📊 *Nivel de enfoque*: $percentage%\n\n*Cuadrantes completados hoy:*\n$completedList\n\nManteniéndome firme con el bucle de hábitos de Charles Duhigg. \n⚡️ Construye hábitos permanentes sin anuncios y 100% offline.\n🔗 Descarga HabitEngine aquí: $url"
        AppLanguage.HINDI -> "🏆 *HabitEngine | मेरी आदतों का प्रोग्रेस* 🏆\n📅 *दिनांक*: $date\n🔥 *वर्तमान स्ट्रीक*: $nonZeroStreak दिन\n📊 *दैनिक फोकस लेवल*: $percentage%\n\n*आज पूरे किए गए क्षेत्र:*\n$completedList\n\nचार्ल्स डुहिंग के हैबिट लूप आर्किटेक्चर के साथ निरंतर बने रहें।\n⚡️ बिना विज्ञापनों और बिना इंटरनेट के अपनी आदतें सुधारें।\n🔗 HabitEngine प्राप्त करें: $url"
        AppLanguage.GERMAN -> "🏆 *HabitEngine | Mein Gewohnheits-Fortschritt* 🏆\n📅 *Datum*: $date\n🔥 *Aktuelle Serie*: $nonZeroStreak Tage\n📊 *Fokus-Level heute*: $percentage%\n\n*Meine abgeschlossenen Quadranten:*\n$completedList\n\nKonsequentes Tracking durch Charles Duhiggs Verhaltensarchitektur. \n⚡️ Baue dauerhafte Gewohnheiten auf – werbefrei und offline.\n🔗 HabitEngine herunterladen unter: $url"
        AppLanguage.JAPANESE -> "🏆 *HabitEngine | 今日の習慣ログ* 🏆\n📅 *日程*: $date\n🔥 *継続日数（Streak）*: $nonZeroStreak 日連続\n📊 *今日のフォーカス度*: $percentage%\n\n*本日クリアした領域:*\n$completedList\n\n脳心理学に基づいたハックで日常を圧倒的自動化。\n⚡️ 広告なし・完全オフライン・究極のプライベート習慣トラッカー。\n🔗 HabitEngine を今すぐ入手: $url"
        AppLanguage.PORTUGUESE -> "🏆 *HabitEngine | Meu Progresso de Hábitos* 🏆\n📅 *Data*: $date\n🔥 *Streak atual*: $nonZeroStreak Dias\n📊 *Nível de Foco*: $percentage%\n\n*Quadrantes concluídos hoje:*\n$completedList\n\nMantendo a consistência pelo método comportamental de Charles Duhigg.\n⚡️ Crie rotinas permanentes sem comerciais e 100% offline.\n🔗 Baixe o HabitEngine em: $url"
        else -> "🏆 *HabitEngine | My Habit Loop Progress* 🏆\n📅 *Date*: $date\n🔥 *Current Streak*: $nonZeroStreak Days\n📊 *Today's Focus Level*: $percentage%\n\n*My Completed Quadrants today:*\n$completedList\n\nConsistently tracking via Charles Duhigg behavioral architecture. \n⚡️ Start building habits that stick. No ads, fully offline, private. \n🔗 Get HabitEngine at: $url"
    }
}

private fun getFacebookTemplate(percentage: Int, streak: Int, url: String, lang: AppLanguage): String {
    val nonZeroStreak = if (streak > 0) streak else 1
    return when (lang) {
        AppLanguage.SPANISH -> "La constancia es la ventaja competitiva definitiva. Hoy logré completar el $percentage% de mis rutinas de hábito en HabitEngine, equilibrando los cuatro cuadrantes clave de la vida: Salud, Profesional, Personal y Familia.\n\nCon $nonZeroStreak días consecutivos de ejecución enfocada (Heatwave 🔥), compruebo que los bucles de comportamiento correctos generan cambios a largo plazo. Sin atajos, solo crecimiento compuesto.\n\n¿Qué estás ejecutando hoy? 💡\nOptimiza tu vida offline de manera privada y sin anuncios: $url"
        AppLanguage.HINDI -> "निरंतरता ही सबसे बड़ी ताकत है। आज मैंने HabitEngine की मदद से अपने चारों प्रमुख जीवन स्तंभों (स्वास्थ्य, पेशेवर, व्यक्तिगत और परिवार) में संतुलन बनाते हुए $percentage% काम पूरा किया।\n\nलगातार $nonZeroStreak दिनों के फोकस्ड काम (Heatwave 🔥) के साथ, मैं साबित कर रहा हूँ कि सही हैबिट लूप दीर्घकालिक परिणाम देते हैं। कोई शार्टकट नहीं, सिर्फ रोज़ाना की प्रगति।\n\nआप आज क्या शुरू कर रहे हैं? 💡\nअपने जीवन को पूरी तरह ऑफलाइन और विज्ञापन-मुक्त सुरक्षित रूप से संतुलित करें: $url"
        AppLanguage.GERMAN -> "Beständigkeit ist der ultimative Wettbewerbsvorteil. Heute habe ich $percentage% meiner Gewohnheitsroutinen mit HabitEngine erreicht und meine Fortschritte in den vier entscheidenden Lebensquadranten verfolgt: Gesundheit, Beruf, Persönlich und Familie.\n\nMit $nonZeroStreak aufeinanderfolgenden Tagen fokussierter Ausführung (Heatwave 🔥) beweise ich, dass ehrliche Verhaltensschleifen langfristige Systeme aufbauen. Kein schneller Hack, nur Zinseszins-Wachstum.\n\nWas setzt du heute um? 💡\nBringe Struktur in deinen Tag – offline, sicher und werbefrei: $url"
        AppLanguage.JAPANESE -> "「継続」こそが、人生における究極の競争優位性です。今日、HabitEngineのルーティンワークスペースを活用して、健康、仕事、個人、家族という「人生の4つの重要な柱」のタスクを $percentage% クリアしました。\n\n$nonZeroStreak 日間連続でフォーカス（Heatwave 🔥）を実行したことで、正しい行動ループが長期的な習慣システムを構築することを証明しています。奇をてらわず、毎日の複利の力を信じるインクリメンタルな成長。\n\nあなたは今日、何を実行しますか？ 💡\nオフライン＆プライベートで、気を散らさずに最高の1日を設計しましょう: $url"
        AppLanguage.PORTUGUESE -> "A consistência é o maior diferencial competitivo de todos. Hoje concluí $percentage% das minhas rotinas no HabitEngine, monitorando meu progresso nos quatro quadrantes cruciais do viver: Saúde, Carreira, Mente e Família.\n\nCom $nonZeroStreak dias consecutivos de foco ativo (Heatwave 🔥), provo que bons loops comportamentais geram resultados de longo prazo de verdade. Sem milagres, apenas crescimento acumulado.\n\nO que você vai executar hoje? 💡\nOtimize o seu dia totalmente offline, seguro e livre de anúncios: $url"
        else -> "Consistency is the ultimate competitive advantage. Today I managed to hit $percentage% on my habit routine workspace with HabitEngine, tracking my progress across the four crucial life quadrants: Health, Professional, Personal, and Family. \n\nWith $nonZeroStreak consecutive days of focused execution (Heatwave 🔥), I am proving that standard behavior loops build long-term systems. No quick hacks, just compound growth.\n\nWhat are you executing today? 💡\nOptimize your day offline, secure, and ad-free: $url"
    }
}

@Composable
fun ShareProgressDialog(
    uiState: MainUiState,
    selectedLanguage: AppLanguage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    
    val totalHabits = uiState.habits.count { it.cadence.isApplicableOn(uiState.selectedDate) }
    val completedMap = uiState.logs[uiState.selectedDate] ?: emptyMap()
    val completedCount = uiState.habits.count { 
        it.cadence.isApplicableOn(uiState.selectedDate) && completedMap[it.id] == true 
    }
    val percentage = if (totalHabits == 0) 0 else ((completedCount.toFloat() / totalHabits.toFloat()) * 100).toInt()
    val streak = remember(uiState.logs, uiState.selectedDate) {
        calculateCurrentStreak(uiState.logs, uiState.selectedDate)
    }
    
    val formatDay = getDayOfWeekLongName(uiState.selectedDate)
    
    val completedListText = remember(uiState.habits, completedMap) {
        val activeHabits = uiState.habits.filter { it.cadence.isApplicableOn(uiState.selectedDate) }
        val comps = activeHabits.filter { completedMap[it.id] == true }
        if (comps.isEmpty()) {
            "• (No quadrants completed yet)"
        } else {
            comps.map { habit ->
                "• ${habit.domain.displayName}: ${habit.routineText}"
            }.joinToString("\n")
        }
    }
    
    val appUrl = "https://HabitEngine.app"
    
    val instagramTemplate = remember(percentage, streak, selectedLanguage) {
        getInstagramTemplate(percentage, streak, appUrl, selectedLanguage)
    }
    
    val whatsappTemplate = remember(percentage, streak, formatDay, completedListText, selectedLanguage) {
        getWhatsappTemplate(percentage, streak, formatDay, completedListText, appUrl, selectedLanguage)
    }
    
    val facebookTemplate = remember(percentage, streak, selectedLanguage) {
        getFacebookTemplate(percentage, streak, appUrl, selectedLanguage)
    }
    
    var activeTab by remember { mutableStateOf(0) }
    
    val activeTemplateText = when (activeTab) {
        0 -> instagramTemplate
        1 -> whatsappTemplate
        2 -> facebookTemplate
        else -> {
            val checkListText = uiState.habits.filter { it.cadence.isApplicableOn(uiState.selectedDate) }
                .joinToString("\n") { habit ->
                    val isCompleted = completedMap[habit.id] == true
                    val statusBox = if (isCompleted) "✅ [OK]" else "⬜ [  ]"
                    " $statusBox ${habit.domain.displayName}: ${habit.routineText} (${habit.cadence.displayName})"
                }
            """
🏆 Habit Engine - Daily Success Summary 🏆
📅 Date: $formatDay
📊 Progress: $completedCount / $totalHabits ($percentage%)
🔥 Heatwave Streak: $streak Days
--------------------------------------------
$checkListText
--------------------------------------------
Consistently developed by Gemini and Ankit ♥️
🚀 Download HabitEngine: $appUrl
            """.trimIndent()
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glow Circle Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        drawCircle(
                            color = Color(0xFF1565C0).copy(alpha = 0.2f),
                            radius = size.width / 3.5f,
                            center = center
                        )
                        drawCircle(
                            color = Color(0xFFEF6C00).copy(alpha = 0.15f),
                            radius = size.width / 2.2f,
                            center = center
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "HabitEngine Star Logo",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = when (selectedLanguage) {
                        AppLanguage.SPANISH -> "EMISORA DE LOGROS"
                        AppLanguage.HINDI -> "उपलब्धि ब्रॉडकास्टर"
                        AppLanguage.GERMAN -> "ERFOLGS-BROADCASTER"
                        AppLanguage.JAPANESE -> "実績ブロードキャスター"
                        AppLanguage.PORTUGUESE -> "BROADCASTER DE SUCESSO"
                        else -> "ACHIEVEMENT BROADCASTER"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = when (selectedLanguage) {
                        AppLanguage.SPANISH -> "¡Comparte tu disciplina con orgullo!"
                        AppLanguage.HINDI -> "अपने अनुशासन पर गर्व महसूस करें!"
                        AppLanguage.GERMAN -> "Teile deine Disziplin mit Stolz!"
                        AppLanguage.JAPANESE -> "規律ある日常を、誇りを持ってシェア！"
                        AppLanguage.PORTUGUESE -> "Compartilhe sua disciplina com orgulho!"
                        else -> "Broadcast your disciplined execution!"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(14.dp))
                
                // Stats Dashboard
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (streak > 0) "🔥 $streak" else "🔥 1",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFEF6C00)
                        )
                        Text(
                            text = when (selectedLanguage) {
                                AppLanguage.SPANISH -> "Racha"
                                AppLanguage.HINDI -> "स्ट्रीक"
                                AppLanguage.GERMAN -> "Serie"
                                AppLanguage.JAPANESE -> "記録"
                                AppLanguage.PORTUGUESE -> "Série"
                                else -> "Streak"
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$completedCount/$totalHabits",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = when (selectedLanguage) {
                                AppLanguage.SPANISH -> "Completados"
                                AppLanguage.HINDI -> "पूरे कार्य"
                                AppLanguage.GERMAN -> "Abgeschlossen"
                                AppLanguage.JAPANESE -> "完了"
                                AppLanguage.PORTUGUESE -> "Feitos"
                                else -> "Completed"
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$percentage%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = if (percentage == 100) Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = when (selectedLanguage) {
                                AppLanguage.SPANISH -> "Nivel foco"
                                AppLanguage.HINDI -> "फोकस"
                                AppLanguage.GERMAN -> "Fokus-Grad"
                                AppLanguage.JAPANESE -> "達成度"
                                AppLanguage.PORTUGUESE -> "Foco"
                                else -> "Focus Level"
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                // Slide / Tab Platform Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabData = listOf(
                        Triple("Instagram", 0, Color(0xFFE1306C)),
                        Triple("WhatsApp", 1, Color(0xFF25D366)),
                        Triple("Facebook", 2, Color(0xFF1877F2)),
                        Triple("Copy Custom", 3, MaterialTheme.colorScheme.secondary)
                    )
                    
                    tabData.forEach { (label, index, color) ->
                        val isSelected = activeTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) color.copy(alpha = 0.12f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { activeTab = index }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                colprivate fun getFaqItems(language: AppLanguage): List<FaqItem> {
    return when (language) {
        AppLanguage.SPANISH -> listOf(
            FaqItem(
                "¿Cuál es la ciencia detrás de esta app?", 
                "HabitEngine se basa en un truco psicológico simple: el bucle de hábitos de Señal ➔ Rutina ➔ Recompensa. En lugar de proponerte metas vagas como 'cocinar más', vinculas un disparador ('Cuando entre a la cocina a las 7 PM') con una acción diminuta ('Cortaré una verdura') seguido de un premio inmediato. Así reconfiguras tu cerebro usando el camino de menor resistencia.", 
                "💡", 
                Color(0xFF1565C0)
            ),
            FaqItem(
                "¿Cómo construyo un hábito que realmente dure?", 
                "El secreto es empezar de forma ridículamente pequeña y anclarlo a cosas que ya haces sin pensar (como lavarte los dientes o abrir tu laptop). Concéntrate únicamente en cumplir cada día. Usa nuestro registro flexible de frecuencias (Diario, Días hábiles, Fines de semana) para calentar tu racha ('Heatwave') sin agotarte.", 
                "🔥", 
                Color(0xFFEF6C00)
            ),
            FaqItem(
                "¿Por qué dividir mi vida en 4 cuadrantes?", 
                "Porque darlo todo en el trabajo no sirve de nada si tu salud o tus relaciones se están cayendo a pedazos. HabitEngine te obliga a registrar tu progreso diario en cuatro pilares básicos: Salud, Profesional, Personal y Familia. Es un golpe de realidad diseñado para mantenerte enfocado y en equilibrio.", 
                "⚖️", 
                Color(0xFFE91E63)
            ),
            FaqItem(
                "¿Qué es la Consola del Registrador de Actividad?", 
                "La Consola del Registrador de Actividad es una terminal de comportamiento en tiempo real integrada en HabitEngine. Te permite registrar actividades personalizadas, estados de productividad o reflexiones emocionales al instante de forma offline. Se almacenan de forma local en la base de datos Room para que puedas auditar tu productividad frente a distractores de manera segura.", 
                "📑", 
                Color(0xFF00ACC1)
            ),
            FaqItem(
                "¿Quién construyó esto? (La historia de Ankit)", 
                "Hola, soy Ankit. Pasé años buscando un rastreador de hábitos en modo oscuro que fuera limpio, sin anuncios, privado y basado en psicología real. Todo lo que encontraba estaba lleno de funciones inútiles o bloqueado tras una suscripción. Así que me alié con Gemini y construí HabitEngine yo mismo. Un espacio de trabajo offline y sin distracciones, hecho con ♥️ para desarrolladores que quieren pasar a la acción.", 
                "👨‍💻", 
                Color(0xFF2E7D32)
            ),
            FaqItem(
                "¿Por qué se llama HabitEngine?", 
                "El nombre es una fusión de la cultura de internet moderna y las ciencias de la computación. Tu 'Habit' es tu vibra, tu energía y tu estado de ejecución personal. Un 'Engine' es la unidad fundamental de datos digitales. HabitEngine significa que ya no estás dejando tu crecimiento personal al azar; estás cuantificando tu energía, hábitos y disciplina del mundo real en un motor de datos limpios e inmutables.", 
                "⚡", 
                Color(0xFF7E57C2)
            )
        )
        AppLanguage.HINDI -> listOf(
            FaqItem(
                "इस ऐप के पीछे की साइंस क्या है?", 
                "HabitEngine एक आसान साइकोलॉजिकल ट्रिक पर काम करता है: संकेत ➔ आदत ➔ पुरस्कार (Habit Loop)। 'ज़्यादा कोडिंग करना' जैसे ढीले वादे करने के बजाय, आप एक निश्चित ट्रिगर तय करते हैं (जैसे 'जब मैं सुबह 9 बजे डेस्क पर बैठूँगा') और उसके साथ एक छोटा सा काम जोड़ते हैं ('1 DSA सवाल सॉल्व करूँगा')। इसके तुरंत बाद मिलने वाला रिवॉर्ड आपके दिमाग को इस रूटीन का आदी बना देता है।", 
                "💡", 
                Color(0xFF1565C0)
            ),
            FaqItem(
                "ऐसी आदत कैसे बनाएं जो कभी न छूटे?", 
                "सीक्रेट यह है कि शुरुआत बहुत ही छोटी करें और इसे उन कामों से जोड़ दें जो आप रोज़ बिना सोचे-समझे करते हैं (जैसे ब्रश करना या लैपटॉप खोलना)। शुरुआत में सिर्फ रोज़ ऐप पर आने और काम करने पर ध्यान दें। बिना बर्नआउट हुए लगातार अपनी स्ट्रीक ('Heatwave') बनाए रखने के लिए हमारे फ्लेक्सिबल शेड्यूल (दैनिक, कार्यदिवस, वीकेंड) का उपयोग करें।", 
                "🔥", 
                Color(0xFFEF6C00)
            ),
            FaqItem(
                "अपने जीवन को ४ क्षेत्रों में बांटना क्यों ज़रूरी है?", 
                "क्योंकि करियर में आगे बढ़ने का कोई मतलब नहीं रह जाता अगर आपका स्वास्थ्य या आपके रिश्ते बिगड़ने लगें। HabitEngine आपको रोज़ाना ४ ज़रूरी पिलर्स पर नज़र रखने के लिए प्रेरित करता है: स्वास्थ्य, पेशेवर, व्यक्तिगत, और परिवार। यह डैशबोर्ड आपको आईना दिखाता है ताकि आपका जीवन हर तरफ से संतुलित रहे।", 
                "⚖️", 
                Color(0xFFE91E63)
            ),
            FaqItem(
                "एक्टिविटी लॉगर कंसोल क्या है?", 
                "एक्टिविटी लॉगर कंसोल HabitEngine में सीधे बनाया गया एक रियल-टाइम व्यवहार टर्मिनल है। यह आपको लाइव गतिविधियों, उत्पादकता (productivity) या भावनात्मक विचारों (जैसे ध्यान या गुस्सा) को तुरंत रिकॉर्ड करने की अनुमति देता है। यह डेटा स्थानीय रूस्टर (Room database) में पूरी तरह ऑफलाइन सुरक्षित रहता है ताकि आप अपनी उत्पादकता की समीक्षा कर सकें।", 
                "📑", 
                Color(0xFF00ACC1)
            ),
            FaqItem(
                "इसे किसने बनाया? (अंकित की कहानी)", 
                "हे, मैं हूँ अंकित। मैं लंबे समय से एक ऐसा आदत ट्रैक करने वाला ऐप ढूंढ रहा था जो सुंदर हो, डार्क मोड में हो, विज्ञापन-मुक्त हो और पूरी तरह से प्राइवेट हो। बाज़ार में मौजूद ज़्यादातर ऐप्स या तो बहुत जटिल थे या पैसों के पीछे भाग रहे थे। इसलिए, मैंने Gemini के साथ मिलकर खुद का HabitEngine बनाया। बिना किसी बकवास और बिना इंटरनेट के चलने वाला एक साफ-सुथरा वर्कस्पेस—उन डेवलपर्स के लिए जो असल में लाइफ में बदलाव देखना चाहते हैं। ♥ी के साथ निर्मित!", 
                "👨‍💻", 
                Color(0xFF2E7D32)
            ),
            FaqItem(
                "इस ऐप का नाम HabitEngine क्यों है?", 
                "यह नाम मॉडर्न इंटरनेट कल्चर और कंप्यूटर साइंस का एक बेहतरीन फ्यूजन है। आपकी 'Habit' का मतलब है आपका वाइब, आपकी एनर्जी और आपके काम करने का तरीका। और एक 'Engine' डिजिटल डेटा की सबसे बुनियादी इकाई (unit) है। HabitEngine का सीधा सा मतलब है कि आप अपनी पर्सनल ग्रोथ को किस्मत के भरोसे नहीं छोड़ रहे हैं; आप अपनी असली दुनिया की ऊर्जा, आदतों और अनुशासन को साफ और पक्के डेटा बाइट्स में बदल रहे हैं।", 
                "⚡", 
                Color(0xFF7E57C2)
            )
        )
        AppLanguage.GERMAN -> listOf(
            FaqItem(
                "Welche Wissenschaft steckt hinter dieser App?", 
                "HabitEngine basiert auf einem einfachen psychologischen Trick: der Gewohnheitsschleife aus Auslöser ➔ Routine ➔ Belohnung. Statt vager Ziele wie 'mehr Sport' verknüpfst du einen klaren Auslöser ('Wenn ich um 7 Uhr meine Laufschuhe sehe') mit einer winzigen Aktion ('Ich gehe 5 Minuten raus') und belohnst dich sofort danach. So programmierst du dein Gehirn um.", 
                "💡", 
                Color(0xFF1565C0)
            ),
            FaqItem(
                "Wie baue ich eine Gewohnheit auf, die wirklich bleibt?", 
                "Das Geheimnis ist, lächerlich klein anzufangen und die neue Routine an Dinge zu knüpfen, die du sowieso schon automatisch tust (wie Zähneputzen oder den Laptop aufklappen). Konzentriere dich anfangs nur darauf, überhaupt aufzutauchen. Nutze unsere flexiblen Zeitpläne (Täglich, Werktage, Wochenende), um deine 'Heatwave'-Serie auszubauen, ohne auszubrennen.", 
                "🔥", 
                Color(0xFFEF6C00)
            ),
            FaqItem(
                "Warum sollte ich mein Leben in 4 Quadranten aufteilen?", 
                "Weil Erfolg im Job wertlos ist, wenn deine Gesundheit oder deine Beziehungen vor dem Aus stehen. HabitEngine zwingt dich dazu, deinen Alltag in vier essenziellen Bereichen zu tracken: Gesundheit, Beruf, Persönliches und Familie. Ein Realitätscheck, der dich im Gleichgewicht hält.", 
                "⚖️", 
                Color(0xFFE91E63)
            ),
            FaqItem(
                "Was ist die Aktivitäts-Logger-Konsole?", 
                "Die Aktivitäts-Logger-Konsole ist ein integriertes Echtzeit-Terminal für Verhaltensaufzeichnungen. Damit können Sie benutzerdefinierte Aktivitäten, Produktivitätszustände oder emotionale Reflexionen (wie Fokus oder Stimmung) spontan protokollieren. Diese werden offline in der Room-Datenbank gespeichert, sodass Sie Ihre Produktivität lückenlos auswerten können.", 
                "📑", 
                Color(0xFF00ACC1)
            ),
            FaqItem(
                "Wer hat die App gebaut? (Ankits Story)", 
                "Hi, ich bin Ankit. Ich habe ewig nach einem sauberen Dark-Mode-Gewohnheitstracker gesucht, der werbefrei, absolut privat und psychologisch fundiert ist. Alles auf dem Markt war entweder überladen oder hinter einer Paywall versteckt. Also habe ich mich mit Gemini zusammengetan und HabitEngine einfach selbst gebaut. Ein komplett offline funktionierender, ablenkungsfreier Workspace—mit ♥️ gebaut für Entwickler, die einfach machen wollen.", 
                "👨‍💻", 
                Color(0xFF2E7D32)
            ),
            FaqItem(
                "Warum heißt die App HabitEngine?", 
                "Der Name ist eine Verschmelzung aus moderner Internetkultur und Kerninformatik. Deine 'Habit' steht für deinen Vibe, deine Energie und deinen persönlichen Fokus. Ein 'Engine' ist die grundlegende Einheit digitaler Daten. HabitEngine bedeutet, dass du dein persönliches Wachstum nicht mehr dem Zufall überlässt: Du quantifizierst deine reale Energie, deine Gewohnheiten und deine Disziplin in saubere, unveränderliche Daten-Engine.", 
                "⚡", 
                Color(0xFF7E57C2)
            )
        )
        AppLanguage.JAPANESE -> listOf(
            FaqItem(
                "このアプリの仕組みは？", 
                "HabitEngineはシンプルな心理学のハック、つまり「きっかけ ➔ 行動 ➔ ごほうび」の習慣ループに基づいています。「もっと勉強する」といった曖昧な目標を立てる代わりに、「デスクに座ったら」という明確なきっかけと「DSAを1問解く」という小さな行動を結びつけ、すぐにごほうびを与えます。これが脳の配線を変える一番の近道です。", 
                "💡", 
                Color(0xFF1565C0)
            ),
            FaqItem(
                "本当に続く習慣を身につけるには？", 
                "秘訣は、あきれるほど小さく始め、すでに無意識にやっていること（歯磨きやノートPCを開くなど）に新しい行動をくっつけることです。最初は「とにかく毎日やる」ことだけに集中しましょう。平日の仕事中や週末など、ライフスタイルに合わせたスケジュール設定で、燃え尽きることなく「Heatwave（継続の熱量）」を維持できます。", 
                "🔥", 
                Color(0xFFEF6C00)
            ),
            FaqItem(
                "なぜ人生を4つのエリアに分けるのですか？", 
                "仕事でどれだけ結果を出しても、体調を崩したり、大切な人との関係が壊れてしまっては意味がないからです。HabitEngineは「健康」「仕事」「個人」「家族」という4つの柱で日々の行動を管理します。これは、自分が今どこに偏っているのかを突きつける、人生のリアルタイムなバランス調整ツールです。", 
                "⚖️", 
                Color(0xFFE91E63)
            ),
            FaqItem(
                "アクティビティロガー・コンソールとは何ですか？", 
                "アクティビティロガー・コンソールは、リアルタイムな行動ログ記録用コンソールターミナルです。その場での生産性、感情やフォーカスなどを即座に記録できます。データはRoomに完全オフラインで保存され、自身の生産性と時間の無駄づかいを振り返り、監査することができます。", 
                "📑", 
                Color(0xFF00ACC1)
            ),
            FaqItem(
                "開発者はどんな人？（Ankitのストーリー）", 
                "こんにちは、Ankitです。心理学に基づいた、広告が一切ないクリーンなダークモードの習慣トラッカーをずっと探していました。しかし、世の中にあるツールは機能が多すぎて使いづらいか、サブスク課金ばかり。それなら自分で作ろうと思い、Geminiとタッグを組んで開発したのがHabitEngineです。完全にオフラインで集中できる、目標を実行に移したい開発者のためのワークスペースを、愛を込めてお届けします ♥️", 
                "👨‍💻", 
                Color(0xFF2E7D32)
            ),
            FaqItem(
                "なぜHabitEngineという名前なのですか？", 
                "この名前は、現代のインターネットカルチャーとコンピュータサイエンスの核心を融合させたものです。「Habit」はあなたの習慣、バイブス、エネルギー、日々の実行力を表します。そして「Engine」は動力源となるエンジンです。HabitEngineという名前には、個人の成長をあいまいにせず、現実世界でのエネルギー、習慣、そして規律を、動力源（Engine）として推進していく不変なデジタルデータとして数値化していくという意味が込められています。", 
                "⚡", 
                Color(0xFF7E57C2)
            )
        )
        AppLanguage.PORTUGUESE -> listOf(
            FaqItem(
                "Qual é a ciência por trás deste app?", 
                "O HabitEngine funciona com base em um truque psicológico simples: o loop de Gatilho ➔ Ação ➔ Recompensa. Envez de criar metas vagas como 'estudar mais', você conecta um gatilho específico ('Quando eu sentar na mesa às 8h') a uma microação ('Vou resolver 1 problema de algoritmo') e se dá uma recompensa imediata. É assim que você reconfigura o seu cérebro pelo caminho de menor resistência.", 
                "💡", 
                Color(0xFF1565C0)
            ),
            FaqItem(
                "Como construir um hábito que realmente dure?", 
                "O segredo é começar ridiculamente pequeno e ancorar a nova rotina em coisas que você já faz no piloto automático (como escovar os dentes ou abrir o notebook). Esqueça a perfeição; foque apenas em aparecer todo santo dia. Use nossos cronogramas flexíveis (Diário, Dias de semana, Fins de semana) para manter o seu 'Heatwave' aceso sem se esgotar.", 
                "🔥", 
                Color(0xFFEF6C00)
            ),
            FaqItem(
                "Por que dividir minha vida em 4 quadrantes?", 
                "Porque não adianta nada evoluir na carreira se a sua saúde ou os seus relacionamentos estiverem desmoronando. O HabitEngine te força a acompanhar o progresso diário em quatro pilares fundamentais: Saúde, Profissional, Pessoal e Família. É um choque de realidade para te manter equilibrado e focado no que importa.", 
                "⚖️", 
                Color(0xFFE91E63)
            ),
            FaqItem(
                "O que é o Console Registrador de Atividades?", 
                "O Console Registrador de Atividades é um terminal de comportamento em tempo real integrado ao HabitEngine. Ele permite registrar atividades personalizadas, estados de produtividade ou reflexões emocionais na hora de forma offline. Tudo é salvo localmente no banco de dados Room para você auditar suas atividades produtivas e gerenciar o histórico de foco.", 
                "📑", 
                Color(0xFF00ACC1)
            ),
            FaqItem(
                "Quem desenvolveu o app? (História do Ankit)", 
                "Fala aí, eu sou o Ankit. Passei anos procurando um rastreador de hábitos em modo escuro que fosse limpo, sem anúncios, totalmente privado e baseado em psicologia comportamental de verdade. Tudo no mercado era poluído ou cobrava assinatura. Então, juntei forças com o Gemini e montei o HabitEngine. Um espaço de trabalho offline e sem distrações—feito com muito ♥️ para desenvolvedores que querem parar de planejar e começar a executar.", 
                "👨‍💻", 
                Color(0xFF2E7D32)
            ),
            FaqItem(
                "Por que se chama HabitEngine?", 
                "O nome é uma fusão da cultura moderna da internet com os fundamentos da ciência da computação. Sua 'Habit' é a sua vibração, sua energia e seu estado de execução pessoal. Um 'Engine' é a unidade fundamental de dados digitais. HabitEngine significa que você não está mais deixando seu crescimento pessoal ao acaso; você está quantificando sua energia, hábitos e disciplina do mundo real em um motor de dados limpos e imutáveis.", 
                "⚡", 
                Color(0xFF7E57C2)
            )
        )
        else -> listOf(
            FaqItem(
                "What's the science behind this app?", 
                "HabitEngine is built on a simple psychological hack: the Cue ➔ Action ➔ Reward loop. Instead of setting vague resolutions like 'code more,' you anchor a concrete trigger ('When I sit down at my desk with coffee') to a tiny micro-action ('I will solve 1 DSA problem') followed by an instant reward. It rewires your brain using the path of least resistance.", 
                "💡", 
                Color(0xFF1565C0)
            ),
            FaqItem(
                "How do I build a habit that actually sticks?", 
                "The secret is starting stupidly small and stacking it onto things you already do without thinking (like brushing your teeth or launching your IDE). Focus entirely on just showing up every day. Use our flexible tracking schedules (Daily, Weekdays, Weekends) to run a high 'Heatwave' streak without burning yourself out.", 
                "🔥", 
                Color(0xFFEF6C00)
            ),
            FaqItem(
                "Why divide my life into 4 quadrants?", 
                "Because crushing your career milestones doesn't mean anything if your physical health or your relationships are actively falling apart. HabitEngine holds you accountable across four baseline dimensions: Health, Professional, Personal, and Family. It's a daily reality check to make sure you stay balanced and consistent.", 
                "⚖️", 
                Color(0xFFE91E63)
            ),
            FaqItem(
                "What is the Activity Logger Console?", 
                "The Activity Logger Console is a real-time behavioral terminal built directly into HabitEngine. It allows you to log custom raw activities, productivity states, or emotional reflections (like anger levels or focus states) on-the-fly. These are stored locally in the secured Room database so you can audit your productivity vs. time-wasters offline, keeping a secure chronological ledger of your daily execution progress.", 
                "📑", 
                Color(0xFF00ACC1)
            ),
            FaqItem(
                "Who built this? (Ankit's Story)", 
                "Hey, I'm Ankit. I spent years looking for a crisp, minimal dark-mode habit loop tracker that was completely ad-free, secure, and based on actual behavioral psychology. Everything out there was either absolute bloatware or locked behind a monthly subscription. So, I partnered up with Gemini and built HabitEngine myself. A fully offline, distraction-free workspace—handcrafted with ♥️ for devs who just want to execute.", 
                "👨‍💻", 
                Color(0xFF2E7D32)
            ),
            FaqItem(
                "Why is it called HabitEngine?", 
                "The name is a fusion of modern internet culture and core computer science. Your 'Habit' is your ultimate vibe, your energy, and your personal execution state. A 'Engine' is the fundamental unit of digital data. HabitEngine means you are no longer leaving your personal growth to chance; you are quantifying your real-world energy, habits, and discipline into clean, immutable data Engine.", 
                "⚡", 
                Color(0xFF7E57C2)
            )
        )
    }
}� नहीं छोड़ रहे हैं; आप अपनी असली दुनिया की ऊर्जा, आदतों और अनुशासन को साफ और पक्के डेटा बाइट्स में बदल रहे हैं।", 
                "⚡", 
                Color(0xFF7E57C2)
            )
        )
        AppLanguage.GERMAN -> listOf(
            FaqItem(
                "Welche Wissenschaft steckt hinter dieser App?", 
                "HabitEngine basiert auf einem einfachen psychologischen Trick: der Gewohnheitsschleife aus Auslöser ➔ Routine ➔ Belohnung. Statt vager Ziele wie 'mehr Sport' verknüpfst du einen klaren Auslöser ('Wenn ich um 7 Uhr meine Laufschuhe sehe') mit einer winzigen Aktion ('Ich gehe 5 Minuten raus') und belohnst dich sofort danach. So programmierst du dein Gehirn um.", 
                "💡", 
                Color(0xFF1565C0)
            ),
            FaqItem(
                "Wie baue ich eine Gewohnheit auf, die wirklich bleibt?", 
                "Das Geheimnis ist, lächerlich klein anzufangen und die neue Routine an Dinge zu knüpfen, die du sowieso schon automatisch tust (wie Zähneputzen oder den Laptop aufklappen). Konzentriere dich anfangs nur darauf, überhaupt aufzutauchen. Nutze unsere flexiblen Zeitpläne (Täglich, Werktage, Wochenende), um deine 'Heatwave'-Serie auszubauen, ohne auszubrennen.", 
                "🔥", 
                Color(0xFFEF6C00)
            ),
            FaqItem(
                "Warum sollte ich mein Leben in 4 Quadranten aufteilen?", 
                "Weil Erfolg im Job wertlos ist, wenn deine Gesundheit oder deine Beziehungen vor dem Aus stehen. HabitEngine zwingt dich dazu, deinen Alltag in vier essenziellen Bereichen zu tracken: Gesundheit, Beruf, Persönliches und Familie. Ein Realitätscheck, der dich im Gleichgewicht hält.", 
                "⚖️", 
                Color(0xFFE91E63)
            ),
            FaqItem(
                "Wer hat die App gebaut? (Ankits Story)", 
                "Hi, ich bin Ankit. Ich habe ewig nach einem sauberen Dark-Mode-Gewohnheitstracker gesucht, der werbefrei, absolut privat und psychologisch fundiert ist. Alles auf dem Markt war entweder überladen oder hinter einer Paywall versteckt. Also habe ich mich mit Gemini zusammengetan und HabitEngine einfach selbst gebaut. Ein komplett offline funktionierender, ablenkungsfreier Workspace—mit ♥️ gebaut für Entwickler, die einfach machen wollen.", 
                "👨‍💻", 
                Color(0xFF2E7D32)
            ),
            FaqItem(
                "Warum heißt die App HabitEngine?", 
                "Der Name ist eine Verschmelzung aus moderner Internetkultur und Kerninformatik. Deine 'Habit' steht für deinen Vibe, deine Energie und deinen persönlichen Fokus. Ein 'Engine' ist die grundlegende Einheit digitaler Daten. HabitEngine bedeutet, dass du dein persönliches Wachstum nicht mehr dem Zufall überlässt: Du quantifizierst deine reale Energie, deine Gewohnheiten und deine Disziplin in saubere, unveränderliche Daten-Engine.", 
                "⚡", 
                Color(0xFF7E57C2)
            )
        )
        AppLanguage.JAPANESE -> listOf(
            FaqItem(
                "このアプリの仕組みは？", 
                "HabitEngineはシンプルな心理学のハック、つまり「きっかけ ➔ 行動 ➔ ごほうび」の習慣ループに基づいています。「もっと勉強する」といった曖昧な目標を立てる代わりに、「デスクに座ったら」という明確なきっかけと「DSAを1問解く」という小さな行動を結びつけ、すぐにごほうびを与えます。これが脳の配線を変える一番の近道です。", 
                "💡", 
                Color(0xFF1565C0)
            ),
            FaqItem(
                "本当に続く習慣を身につけるには？", 
                "秘訣は、あきれるほど小さく始め、すでに無意識にやっていること（歯磨きやノートPCを開くなど）に新しい行動をくっつけることです。最初は「とにかく毎日やる」ことだけに集中しましょう。平日の仕事中や週末など、ライフスタイルに合わせたスケジュール設定で、燃え尽きることなく「Heatwave（継続の熱量）」を維持できます。", 
                "🔥", 
                Color(0xFFEF6C00)
            ),
            FaqItem(
                "なぜ人生を4つのエリアに分けるのですか？", 
                "仕事でどれだけ結果を出しても、体調を崩したり、大切な人との関係が壊れてしまっては意味がないからです。HabitEngineは「健康」「仕事」「個人」「家族」という4つの柱で日々の行動を管理します。これは、自分が今どこに偏っているのかを突きつける、人生のリアルタイムなバランス調整ツールです。", 
                "⚖️", 
                Color(0xFFE91E63)
            ),
            FaqItem(
                "開発者はどんな人？（Ankitのストーリー）", 
                "こんにちは、Ankitです。心理学に基づいた、広告が一切ないクリーンなダークモードの習慣トラッカーをずっと探していました。しかし、世の中にあるツールは機能が多すぎて使いづらいか、サブスク課金ばかり。それなら自分で作ろうと思い、Geminiとタッグを組んで開発したのがHabitEngineです。完全にオフラインで集中できる、目標を実行に移したい開発者のためのワークスペースを、愛を込めてお届けします ♥️", 
                "👨‍💻", 
                Color(0xFF2E7D32)
            ),
            FaqItem(
                "なぜHabitEngine（オーラバイト）という名前なのですか？", 
                "この名前は、現代のインターネットカルチャーとコンピュータサイエンスの核心を融合させたものです。「Habit（オーラ）」はあなたのバイブス、エネルギー、そして日々の実行力を表します。そして「Engine（バイト）」はデジタルデータの基本単位です。HabitEngineという名前には、個人の成長をあいまいにせず、現実世界でのエネルギー、習慣、そして規律を、クリーンで不変なデジタルデータとして数値化していくという意味が込められています。", 
                "⚡", 
                Color(0xFF7E57C2)
            )
        )
        AppLanguage.PORTUGUESE -> listOf(
            FaqItem(
                "Qual é a ciência por trás deste app?", 
                "O HabitEngine funciona com base em um truque psicológico simples: o loop de Gatilho ➔ Ação ➔ Recompensa. Em vez de criar metas vagas como 'estudar mais', você conecta um gatilho específico ('Quando eu sentar na mesa às 8h') a uma microação ('Vou resolver 1 problema de algoritmo') e se dá uma recompensa imediata. É assim que você reconfigura o seu cérebro pelo caminho de menor resistência.", 
                "💡", 
                Color(0xFF1565C0)
            ),
            FaqItem(
                "Como construir um hábito que realmente dure?", 
                "O segredo é começar ridiculamente pequeno e ancorar a nova rotina em coisas que você já faz no piloto automático (como escovar os dentes ou abrir o notebook). Esqueça a perfeição; foque apenas em aparecer todo santo dia. Use nossos cronogramas flexíveis (Diário, Dias de semana, Fins de semana) para manter o seu 'Heatwave' aceso sem se esgotar.", 
                "🔥", 
                Color(0xFFEF6C00)
            ),
            FaqItem(
                "Por que dividir minha vida em 4 quadrantes?", 
                "Porque não adianta nada evoluir na carreira se a sua saúde ou os seus relacionamentos estiverem desmoronando. O HabitEngine te força a acompanhar o progresso diário em quatro pilares fundamentais: Saúde, Profissional, Pessoal e Família. É um choque de realidade para te manter equilibrado e focado no que importa.", 
                "⚖️", 
                Color(0xFFE91E63)
            ),
            FaqItem(
                "Quem desenvolveu o app? (História do Ankit)", 
                "Fala aí, eu sou o Ankit. Passei anos procurando um rastreador de hábitos em modo escuro que fosse limpo, sem anúncios, totalmente privado e baseado em psicologia comportamental de verdade. Tudo no mercado era poluído ou cobrava assinatura. Então, juntei forças com o Gemini e montei o HabitEngine. Um espaço de trabalho offline e sem distrações—feito com muito ♥️ para desenvolvedores que querem parar de planejar e começar a executar.", 
                "👨‍💻", 
                Color(0xFF2E7D32)
            ),
            FaqItem(
                "Por que se chama HabitEngine?", 
                "O nome é uma fusão da cultura moderna da internet com os fundamentos da ciência da computação. Sua 'Habit' é a sua vibração, sua energia e seu estado de execução pessoal. Um 'Engine' é a unidade fundamental de dados digitais. HabitEngine significa que você não está mais deixando seu crescimento pessoal ao acaso; você está quantificando sua energia, hábitos e disciplina do mundo real em Engine de dados limpos e imutáveis.", 
                "⚡", 
                Color(0xFF7E57C2)
            )
        )
        else -> listOf(
            FaqItem(
                "What's the science behind this app?", 
                "HabitEngine is built on a simple psychological hack: the Cue ➔ Action ➔ Reward loop. Instead of setting vague resolutions like 'code more,' you anchor a concrete trigger ('When I sit down at my desk with coffee') to a tiny micro-action ('I will solve 1 DSA problem') followed by an instant reward. It rewires your brain using the path of least resistance.", 
                "💡", 
                Color(0xFF1565C0)
            ),
            FaqItem(
                "How do I build a habit that actually sticks?", 
                "The secret is starting stupidly small and stacking it onto things you already do without thinking (like brushing your teeth or launching your IDE). Focus entirely on just showing up every day. Use our flexible tracking schedules (Daily, Weekdays, Weekends) to run a high 'Heatwave' streak without burning yourself out.", 
                "🔥", 
                Color(0xFFEF6C00)
            ),
            FaqItem(
                "Why divide my life into 4 quadrants?", 
                "Because crushing your career milestones doesn't mean anything if your physical health or your relationships are actively falling apart. HabitEngine holds you accountable across four baseline dimensions: Health, Professional, Personal, and Family. It's a daily reality check to make sure you stay balanced and consistent.", 
                "⚖️", 
                Color(0xFFE91E63)
            ),
            FaqItem(
                "Who built this? (Ankit's Story)", 
                "Hey, I'm Ankit. I spent years looking for a crisp, minimal dark-mode habit loop tracker that was completely ad-free, secure, and based on actual behavioral psychology. Everything out there was either absolute bloatware or locked behind a monthly subscription. So, I partnered up with Gemini and built HabitEngine myself. A fully offline, distraction-free workspace—handcrafted with ♥️ for devs who just want to execute.", 
                "👨‍💻", 
                Color(0xFF2E7D32)
            ),
            FaqItem(
                "Why is it called HabitEngine?", 
                "The name is a fusion of modern internet culture and core computer science. Your 'Habit' is your ultimate vibe, your energy, and your personal execution state. A 'Engine' is the fundamental unit of digital data. HabitEngine means you are no longer leaving your personal growth to chance; you are quantifying your real-world energy, habits, and discipline into clean, immutable data Engine.", 
                "⚡", 
                Color(0xFF7E57C2)
            )
        )
    }
}

private fun getLifeAreaDoc(language: AppLanguage): LifeAreaDoc {
    return when (language) {
        AppLanguage.SPANISH -> LifeAreaDoc(
            title = "La filosofía de los 4 cuadrantes",
            introduction = "Tu vida es como un motor de alto rendimiento. Si un solo cilindro falla, todo el vehículo empieza a fallar. Para evitar que te satures o descuides lo importante, organizamos tus hábitos en 4 dimensiones críticas de crecimiento continuo:",
            areas = listOf(
                Triple("1. Salud (Tu base de energía)", "El pilar físico que sostiene todo lo demás. Tu sueño, nutrición y entrenamiento diario configuran la batería real con la que enfrentarás tus desafíos.", Color(0xFF2E7D32)),
                Triple("2. Profesional (Impacto y maestría)", "Tu carrera, tus proyectos personales y tus habilidades técnicas. Este es tu terreno para escribir código excepcional, resolver arquitecturas complejas y generar valor real.", Color(0xFF1565C0)),
                Triple("3. Personal (Mente y sabiduría)", "Lectura, pasatiempos, meditación y autorreflexión (como monitorear y controlar tus problemas de ira). Esto nutre tu mundo interno y mantiene tu mente ágil, fría y bajo control.", Color(0xFFE91E63)),
                Triple("4. Familia (Conexión real)", "Tu red de seguridad emocional. El tiempo intencional y de alta calidad que dedicas a tus padres, pareja, hijos o amigos cercanos. Es lo que le da sentido a todo el esfuerzo.", Color(0xFFEF6C00))
            ),
            conclusion = "Al balancear tus 'Engine' en estos bloques, evitas que tu crecimiento sea desigual. Tu Command Dashboard te recordará de forma visual si estás dejando algún cilindro vacío."
        )
        AppLanguage.HINDI -> LifeAreaDoc(
            title = "४ लाइफ क्वाड्रंट्स का दर्शन",
            introduction = "आपका जीवन एक हाई-परफॉर्मेंस इंजन की तरह है। यदि इसका एक भी सिलेंडर रुक जाए, तो पूरी गाड़ी डगमगाने लगती है। जीवन को एकतरफा होने से बचाने के लिए, हमने आपके डेली रूटीन को ४ मुख्य हिस्सों में सेट किया है:",
            areas = listOf(
                Triple("१. स्वास्थ्य (आपकी ऊर्जा का स्रोत)", "यह आपका फिजिकल फाउंडेशन है। आपकी नींद, डाइट और वर्कआउट वो बैटरी चार्ज करते हैं जिससे आपकी बाकी की पूरी दिनचर्या चलती है।", Color(0xFF2E7D32)),
                Triple("२. पेशेवर (करियर और स्किल डेवलपमेंट)", "आपकी नौकरी, कोड क्वालिटी, DSA प्रैक्टिस और ड्रीम प्रोजेक्ट्स। यह वो ज़मीन है जहाँ आप अपनी टेक्निकल मास्टर हासिल करते हैं और दुनिया को अपना आउटपुट देते हैं।", Color(0xFF1565C0)),
                Triple("३. व्यक्तिगत (मन और आत्मनिरीक्षण)", "किताबें पढ़ना, नई चीज़ें सीखना, ध्यान लगाना और अपनी कमियों (जैसे कि गुस्से की समस्या) पर काम करना। यह आपके दिमाग को शांत, तेज़ और फोकस्ड रखता है।", Color(0xFFE91E63)),
                Triple("४. परिवार (सच्चे रिश्ते और जुड़ाव)", "आपका इमोशनल सपोर्ट सिस्टम। अपने परिवार और करीबी दोस्तों के साथ बिताया गया वो क्वालिटी टाइम जहाँ आप बिना किसी गैजेट के पूरी तरह उनके साथ मौजूद होते हैं। यही असल खुशी है।", Color(0xFFEF6C00))
            ),
            conclusion = "इन अलग-अलग क्षेत्रों में आदतें बनाने से आप कभी लाइफ में पीछे नहीं छूटेंगे। डैशबोर्ड आपको लाइव इंडिकेटर दिखाता रहेगा कि आपके इंजन का कौन सा सिलेंडर कमज़ोर पड़ रहा है।"
        )
        AppLanguage.GERMAN -> LifeAreaDoc(
            title = "Die Philosophie der 4 Quadranten",
            introduction = "Das Leben ist wie ein Hochleistungsmotor. Wenn ein einziger Zylinder ausfällt, stottert das ganze Fahrzeug. Damit dein Alltag nicht einseitig wird, teilen wir deine Gewohnheiten in 4 kritische Lebensbereiche auf:",
            areas = listOf(
                Triple("1. Gesundheit (Deine Energiebasis)", "Das körperliche Fundament für alles andere. Guter Schlaf, saubere Ernährung und Training sind der Treibstoff, der deinen Tag antreibt.", Color(0xFF2E7D32)),
                Triple("2. Beruf (Wirkung & Code-Maestrie)", "Deine Karriere, Tech-Skills und eigenen Projekte. Hier verfeinerst du deine Fähigkeiten, baust funktionale Systeme und schaffst echten Wert.", Color(0xFF1565C0)),
                Triple("3. Persönlich (Geist & Selbstreflexion)", "Lesen, Meditation, Hobbys und die bewusste Arbeit an dir selbst (z. B. das Tracken von Aggressions- oder Wutmetriken). Das hält deinen Verstand scharf, ruhig und besonnen.", Color(0xFFE91E63)),
                Triple("4. Familie (Echte Bindungen)", "Dein emotionales Sicherheitsnetz. Die bewusste, ungestörte Zeit für Partner, Eltern, Kinder oder enge Freunde. Das, was am Ende des Tages wirklich zählt.", Color(0xFFEF6C00))
            ),
            conclusion = "Durch die Verteilung deiner täglichen Gewohnheiten verhinderst du, dass du eine Baustelle im Leben ignorierst. Das Command-Dashboard zeigt dir sofort, welcher Zylinder Aufmerksamkeit braucht."
        )
        AppLanguage.JAPANESE -> LifeAreaDoc(
            title = "4つのクアドラント（柱）の哲学",
            introduction = "人生は高出力のエンジンのようなものです。1つのシリンダーが焼き付けば、車全体がストップしてしまいます。生活のバランスを保ち、燃え尽き症候群を防ぐために、日々の習慣を4つの領域に最適化しました：",
            areas = listOf(
                Triple("1. 健康（すべてのエネルギー源）", "すべての土台となる肉体的な基盤です。良質な睡眠、栄養、そして運動が、日々の開発や思考を支えるバッテリーの役割を果たします。", Color(0xFF2E7D32)),
                Triple("2. 職業（社会的インパクトと技術の磨き）", "キャリア、技術スタックの向上、DSAの練習、個人の開発プロジェクト。自分の能力を尖らせ、エンジニアとして価値を世の中にアウトプットする領域です。", Color(0xFF1565C0)),
                Triple("3. 個人（精神の向上と内省）", "読書や創作、瞑想、あるいは自身の感情管理（イライラや怒りのコントロールなど）。内面を豊かにし、どんな状況でもブレない冷静で賢明なマインドを作ります。", Color(0xFFE91E63)),
                Triple("4. 家族（本当の人間関係）", "あなたの精神的なセーフティネットです。スマホの画面を閉じ、家族や大切な人と向き合うために確保する静かで上質な時間。これこそが、人生を支える本質です。", Color(0xFFEF6C00))
            ),
            conclusion = "特定のタスクだけに偏ることなく、日々の進捗を均等にビルडできます。Command Dashboardが、どのシリンダーに燃料が足りていないかをリアルタイムであなたに伝えます。"
        )
        AppLanguage.PORTUGUESE -> LifeAreaDoc(
            title = "A Filosofia dos 4 Quadrantes",
            introduction = "Sua vida funciona como um motor de alta performance. Se um único cilindro falhar, o veículo inteiro começa a engasgar. Para evitar que você negligencie o que importa, organizamos suas rotinas em 4 pilares centrais:",
            areas = listOf(
                Triple("1. Saúde (Sua base de força)", "O pilar físico que sustenta tudo. Seu sono, sua alimentação e seus treinos são a bateria que mantém você focado e operante ao longo do dia.", Color(0xFF2E7D32)),
                Triple("2. Profissional (Impacto e maestria)", "Sua carreira, linhas de código, DSA e projetos paralelos. É aqui que você refina sua capacidade técnica, constrói sistemas robustos e gera valor de mercado.", Color(0xFF1565C0)),
                Triple("3. Pessoal (Mente livre e autoconhecimento)", "Leitura, hobbies, meditação e autocontrole (como monitorar e mitigar problemas de temperamento ou raiva). Mantém a sua mente afiada, fria e sob comando.", Color(0xFFE91E63)),
                Triple("4. Família (Conexão e presença)", "Sua rede de apoio emocional. O tempo intencional e sem distrações digitais dedicado a quem você ama—seus pais, parceiro(a), filhos ou amigos reais. É o que dá sentido à jornada.", Color(0xFFEF6C00))
            ),
            conclusion = "Dividir seus 'Engine' por essas frentes evita que sua vida cresça torta. O painel interativo te mostra em tempo real como está a saúde de cada engrenagem vital."
        )
        else -> LifeAreaDoc(
            title = "The Philosophy of the 4 Quadrants",
            introduction = "Your life functions exactly like a high-performance engine. If a single cylinder misfires, the entire vehicle begins to sputter. To keep you from tracking heavily in one area while completely neglecting another, we map your habits into 4 critical zones:",
            areas = listOf(
                Triple("1. Health (Your Energy Baseline)", "The physical core that fuels everything else. Your sleep architecture, nutrition, and workout habits represent the raw battery capacity of your day.", Color(0xFF2E7D32)),
                Triple("2. Professional (Impact & Code Mastery)", "Your career, engineering roadmap, DSA consistency, and side projects. This is where you hone your execution skills and build products of real value.", Color(0xFF1565C0)),
                Triple("3. Personal (Mind & Self-Reflection)", "Reading, independent learning, meditation, and behavioral course-corrections (like logging and managing anger metrics). This expands your internal world and keeps your head cool.", Color(0xFFE91E63)),
                Triple("4. Family (Intentional Connection)", "Your emotional anchor. High-quality, screens-down, active time spent nurturing your family, close friends, and core relationships. This is what keeps the grind meaningful.", Color(0xFFEF6C00))
            ),
            conclusion = "By distributing your habits systematically, you prevent life from running lopsided. The Command Dashboard visually warns you the second one of your vital cylinders drops below optimal capacity."
        )
    }
}

@Composable
fun CreateHabitInlineScreen(
    selectedLanguage: AppLanguage,
    onSubmit: (domain: LifeDomain, cadence: Cadence, cueText: String, routineText: String, rewardText: String, notesText: String, isBad: Boolean) -> Unit
) {
    var domain by rememberSaveable { mutableStateOf(LifeDomain.HEALTH) }
    var cadence by rememberSaveable { mutableStateOf(Cadence.DAILY) }
    var notesText by rememberSaveable { mutableStateOf("") }
    var isBad by rememberSaveable { mutableStateOf(false) }
    
    var cueText by rememberSaveable { mutableStateOf("") }
    var routineText by rememberSaveable { mutableStateOf("") }
    var rewardText by rememberSaveable { mutableStateOf("") }
 
    var hasAttemptedSubmit by rememberSaveable { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "FORMULATE A NEW HABIT 🧠",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            
            Text(
                text = "Configure your cue, routine, and reward triggers in alignment with the Charles Duhigg loop philosophy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            // Domain Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = Localizations.get(selectedLanguage, "domain_title"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
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
                                .padding(vertical = 10.dp),
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = Localizations.get(selectedLanguage, "cadence_title"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
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
                                .padding(vertical = 10.dp),
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

            // Habit Type Selection (Good vs Bad)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Habit Type:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (!isBad) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            )
                            .border(
                                if (!isBad) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { isBad = false }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Routine (Good Habit) 🌸", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!isBad) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isBad) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            )
                            .border(
                                if (isBad) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else BorderStroke(0.dp, Color.Transparent),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { isBad = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Avoidance (Bad Habit) 🛡️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isBad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            // Duhigg Sentinel Loop Description
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f))
                    .padding(12.dp)
            ) {
                Text(
                    text = Localizations.get(selectedLanguage, "formula_sentence"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
            }

            // Sentence Fields
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cueText,
                    onValueChange = { cueText = it },
                    label = { Text(Localizations.get(selectedLanguage, "routine_label") + " (Optional)") },
                    placeholder = { Text(Localizations.get(selectedLanguage, "routine_placeholder")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                OutlinedTextField(
                    value = routineText,
                    onValueChange = { routineText = it },
                    label = { Text(Localizations.get(selectedLanguage, "action_label")) },
                    placeholder = { Text(Localizations.get(selectedLanguage, "action_placeholder")) },
                    isError = hasAttemptedSubmit && routineText.trim().isEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                OutlinedTextField(
                    value = rewardText,
                    onValueChange = { rewardText = it },
                    label = { Text(Localizations.get(selectedLanguage, "reward_label") + " (Optional)") },
                    placeholder = { Text(Localizations.get(selectedLanguage, "reward_placeholder")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Personal Notes / Avoidance Strategy") },
                    placeholder = { Text("e.g. Keep water nearby, start with 2 mins...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }

            if (hasAttemptedSubmit && routineText.isBlank()) {
                Text(
                    text = "The habit action/name cannot be blank.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = {
                    hasAttemptedSubmit = true
                    if (routineText.isNotBlank()) {
                        focusManager.clearFocus()
                        onSubmit(domain, cadence, cueText, routineText, rewardText, notesText, isBad)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "SAVE AND FORMULATE 🧠",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

