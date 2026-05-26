package com.example.core.domain

import java.util.Calendar

enum class LifeDomain {
    HEALTH,
    PROFESSIONAL,
    PERSONAL,
    FAMILY;

    val displayName: String
        get() = when (this) {
            HEALTH -> "Health"
            PROFESSIONAL -> "Professional"
            PERSONAL -> "Personal"
            FAMILY -> "Social"
        }
}

enum class Cadence {
    DAILY,
    WEEKDAYS,
    WEEKENDS,
    MONTHLY;

    val displayName: String
        get() = when (this) {
            DAILY -> "Daily"
            WEEKDAYS -> "Weekdays"
            WEEKENDS -> "Weekends"
            MONTHLY -> "Monthly"
        }
}

data class Habit(
    val id: String,
    val domain: LifeDomain,
    val cadence: Cadence,
    val cueText: String,
    val routineText: String,
    val rewardText: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class DayLog(
    val date: String, // format YYYY-MM-DD
    val completions: Map<String, Boolean> // HabitId -> CompletionStatus
)

data class TrackerState(
    val habits: List<Habit> = emptyList(),
    val logs: Map<String, Map<String, Boolean>> = emptyMap() // date -> HabitId -> CompletionStatus
)

/**
 * Analytical checks (the Dynamic Cadence Filter) isolated in the Domain Layer.
 * Determines if a habit is historically active/tracked or selectable on the given date.
 */
fun Cadence.isApplicableOn(dateStr: String): Boolean {
    val dateParts = dateStr.split("-")
    if (dateParts.size != 3) return true // default fallback
    
    return try {
        val year = dateParts[0].toInt()
        val month = dateParts[1].toInt() - 1 // Calendar is 0-indexed for months
        val day = dateParts[2].toInt()
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
        }
        
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        when (this) {
            Cadence.DAILY -> true
            Cadence.WEEKDAYS -> {
                dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY
            }
            Cadence.WEEKENDS -> {
                dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
            }
            Cadence.MONTHLY -> {
                // Applicable on the 1st day of the month for visual clean cycle,
                // or we can allow the first week as selectable. Let's make it on day 1
                // or simply day of month == 1 as requested, keeping business logic clean.
                calendar.get(Calendar.DAY_OF_MONTH) == 1
            }
        }
    } catch (e: Exception) {
        true // fallback safe default
    }
}
