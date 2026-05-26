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
    val celebration: CelebrationState? = null
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
        _selectedDate,
        _themeMode,
        _celebration
    ) { trackerState, date, theme, celeb ->
        MainUiState(
            habits = trackerState.habits,
            logs = trackerState.logs,
            selectedDate = date,
            themeMode = theme,
            isLoading = false,
            celebration = celeb
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
        rewardText: String
    ) {
        viewModelScope.launch {
            val habit = Habit(
                id = UUID.randomUUID().toString(),
                domain = domain,
                cadence = cadence,
                cueText = cueText.trim(),
                routineText = routineText.trim(),
                rewardText = rewardText.trim()
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
        createdAt: Long
    ) {
        viewModelScope.launch {
            val habit = Habit(
                id = habitId,
                domain = domain,
                cadence = cadence,
                cueText = cueText.trim(),
                routineText = routineText.trim(),
                rewardText = rewardText.trim(),
                createdAt = createdAt
            )
            storagePort.saveHabit(habit)
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

    companion object {
        fun getTodayDateString(): String {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return formatter.format(Date())
        }
    }
}
