package com.example.infrastructure.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.domain.Cadence
import com.example.core.domain.Habit
import com.example.core.domain.LifeDomain
import com.example.core.ports.StoragePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import com.example.core.domain.ActivityCategory
import com.example.core.domain.ActivityLog
import com.example.infrastructure.adapters.database.LogEntity
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode {
    CYBERPUNK,
    SUNSET
}

data class CelebrationState(
    val habitId: String,
    val routineText: String,
    val rewardText: String,
    val x: Float,
    val y: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class MainUiState(
    val habits: List<Habit> = emptyList(),
    val logs: Map<String, Map<String, Boolean>> = emptyMap(),
    val selectedDate: String = "",
    val themeMode: ThemeMode = ThemeMode.CYBERPUNK,
    val isLoading: Boolean = true,
    val celebration: CelebrationState? = null,
    val activityLogs: List<ActivityLog> = emptyList()
)

class HabitViewModel(
    private val storagePort: StoragePort
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(getTodayDateString())
    private val _themeMode = MutableStateFlow(ThemeMode.CYBERPUNK)
    private val _celebration = MutableStateFlow<CelebrationState?>(null)

    // Reactive stream combining custom theme preference, dates, and ports state
    val uiState: StateFlow<MainUiState> = combine(
        storagePort.loadTrackerState(),
        storagePort.loadActivityLogs(),
        _selectedDate,
        _themeMode,
        _celebration
    ) { trackerState, activities, date, theme, celeb ->
        MainUiState(
            habits = trackerState.habits,
            logs = trackerState.logs,
            selectedDate = date,
            themeMode = theme,
            isLoading = false,
            celebration = celeb,
            activityLogs = activities
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(selectedDate = getTodayDateString())
    )

    fun selectDate(dateString: String) {
        _selectedDate.value = dateString
    }

    fun toggleTheme() {
        _themeMode.update { if (it == ThemeMode.CYBERPUNK) ThemeMode.SUNSET else ThemeMode.CYBERPUNK }
    }

    fun createHabit(
        domain: LifeDomain,
        cadence: Cadence,
        cueText: String,
        routineText: String,
        rewardText: String,
        notes: String = "",
        isBad: Boolean = false
    ) {
        viewModelScope.launch {
            val habit = Habit(
                id = UUID.randomUUID().toString(),
                domain = domain,
                cadence = cadence,
                cueText = cueText.trim(),
                routineText = routineText.trim(),
                rewardText = rewardText.trim(),
                notes = notes.trim(),
                isBad = isBad
            )
            storagePort.saveHabit(habit)
        }
    }

    fun updateHabit(
        habitId: String,
        domain: LifeDomain,
        cadence: Cadence,
        cueText: String,
        routineText: String,
        rewardText: String,
        createdAt: Long,
        notes: String = "",
        isBad: Boolean = false
    ) {
        viewModelScope.launch {
            val habit = Habit(
                id = habitId,
                domain = domain,
                cadence = cadence,
                cueText = cueText.trim(),
                routineText = routineText.trim(),
                rewardText = rewardText.trim(),
                createdAt = createdAt,
                notes = notes.trim(),
                isBad = isBad
            )
            storagePort.saveHabit(habit)
        }
    }

    fun createActivityLog(
        description: String,
        category: ActivityCategory,
        durationMinutes: Int
    ) {
        viewModelScope.launch {
            val log = ActivityLog(
                id = UUID.randomUUID().toString(),
                description = description.trim(),
                category = category,
                timestamp = System.currentTimeMillis(),
                durationMinutes = durationMinutes
            )
            storagePort.saveActivityLog(log)
        }
    }

    fun deleteActivityLog(id: String) {
        viewModelScope.launch {
            storagePort.deleteActivityLog(id)
        }
    }

    fun toggleHabitCompletion(habitId: String, currentStatus: Boolean, clickX: Float, clickY: Float) {
        viewModelScope.launch {
            val date = _selectedDate.value
            storagePort.toggleLogEntry(date, habitId, currentStatus)

            // If the habit is now completed, we trigger the Dopamine Reward celebration
            if (!currentStatus) {
                // Fetch habit in current state list
                val state = uiState.value
                val habit = state.habits.find { it.id == habitId }
                if (habit != null) {
                    _celebration.value = CelebrationState(
                        habitId = habitId,
                        routineText = habit.routineText,
                        rewardText = habit.rewardText,
                        x = clickX,
                        y = clickY
                    )
                }
            }
        }
    }

    fun dismissCelebration() {
        _celebration.value = null
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            storagePort.deleteHabit(habitId)
        }
    }

    fun exportBackupAsJson(): String {
        return try {
            val state = uiState.value
            val backupObj = JSONObject()
            
            // 1. Habits
            val habitsArr = JSONArray()
            state.habits.forEach { habit ->
                val habitObj = JSONObject().apply {
                    put("id", habit.id)
                    put("domain", habit.domain.name)
                    put("cadence", habit.cadence.name)
                    put("cueText", habit.cueText)
                    put("routineText", habit.routineText)
                    put("rewardText", habit.rewardText)
                    put("createdAt", habit.createdAt)
                    put("notes", habit.notes)
                    put("isBad", habit.isBad)
                }
                habitsArr.put(habitObj)
            }
            backupObj.put("habits", habitsArr)
            
            // 2. Logs
            val logsObj = JSONObject()
            state.logs.forEach { dateKey, habitMap ->
                val habitMapObj = JSONObject()
                habitMap.forEach { habitId, completed ->
                    habitMapObj.put(habitId, completed)
                }
                logsObj.put(dateKey, habitMapObj)
            }
            backupObj.put("logs", logsObj)
            
            // 3. Activity Logs
            val activitiesArr = JSONArray()
            state.activityLogs.forEach { log ->
                val logObj = JSONObject().apply {
                    put("id", log.id)
                    put("description", log.description)
                    put("category", log.category.name)
                    put("timestamp", log.timestamp)
                    put("durationMinutes", log.durationMinutes)
                }
                activitiesArr.put(logObj)
            }
            backupObj.put("activityLogs", activitiesArr)
            
            backupObj.toString(4)
        } catch (e: Exception) {
            ""
        }
    }

    fun restoreBackupFromJson(jsonString: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val backupObj = JSONObject(jsonString)
                
                // Parse habits
                val habitsList = mutableListOf<Habit>()
                if (backupObj.has("habits")) {
                    val habitsArr = backupObj.getJSONArray("habits")
                    for (i in 0 until habitsArr.length()) {
                        val obj = habitsArr.getJSONObject(i)
                        habitsList.add(
                            Habit(
                                id = obj.getString("id"),
                                domain = try { LifeDomain.valueOf(obj.getString("domain")) } catch(e: Exception) { LifeDomain.PERSONAL },
                                cadence = try { Cadence.valueOf(obj.getString("cadence")) } catch(e: Exception) { Cadence.DAILY },
                                cueText = obj.optString("cueText", ""),
                                routineText = obj.getString("routineText"),
                                rewardText = obj.optString("rewardText", ""),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                notes = obj.optString("notes", ""),
                                isBad = obj.optBoolean("isBad", false)
                            )
                        )
                    }
                }
                
                // Parse logs
                val parsedLogs = mutableListOf<LogEntity>()
                if (backupObj.has("logs")) {
                    val logsObj = backupObj.getJSONObject("logs")
                    val dates = logsObj.keys()
                    while (dates.hasNext()) {
                        val date = dates.next()
                        val habitMapObj = logsObj.getJSONObject(date)
                        val habitIds = habitMapObj.keys()
                        while (habitIds.hasNext()) {
                            val habitId = habitIds.next()
                            val completed = habitMapObj.getBoolean(habitId)
                            parsedLogs.add(
                                LogEntity(
                                    date = date,
                                    habitId = habitId,
                                    completed = completed
                                )
                            )
                        }
                    }
                }
                
                // Parse activity logs
                val parsedActivities = mutableListOf<ActivityLog>()
                if (backupObj.has("activityLogs")) {
                    val activitiesArr = backupObj.getJSONArray("activityLogs")
                    for (i in 0 until activitiesArr.length()) {
                        val obj = activitiesArr.getJSONObject(i)
                        parsedActivities.add(
                            ActivityLog(
                                id = obj.getString("id"),
                                description = obj.getString("description"),
                                category = try { ActivityCategory.valueOf(obj.getString("category")) } catch(e: Exception) { ActivityCategory.NEUTRAL },
                                timestamp = obj.getLong("timestamp"),
                                durationMinutes = obj.optInt("durationMinutes", 0)
                            )
                        )
                    }
                }
                
                // Invoke port restore helper
                storagePort.restoreBackup(habitsList, parsedLogs, parsedActivities)
                callback(true)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false)
            }
        }
    }

    companion object {
        fun getTodayDateString(): String {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return formatter.format(Date())
        }
    }
}
