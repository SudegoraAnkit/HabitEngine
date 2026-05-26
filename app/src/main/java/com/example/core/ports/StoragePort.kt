package com.example.core.ports

import com.example.core.domain.Habit
import com.example.core.domain.TrackerState
import kotlinx.coroutines.flow.Flow

interface StoragePort {
    /**
     * Loads the entire habit tracking ledger (habits and completed day logs) as a reactive stream.
     */
    fun loadTrackerState(): Flow<TrackerState>

    /**
     * Persists or updates a habit in local persistent storage.
     */
    suspend fun saveHabit(habit: Habit)

    /**
     * Toggles or overrides a specific completion event on a given date string.
     */
    suspend fun toggleLogEntry(date: String, habitId: String, currentStatus: Boolean)

    /**
     * Deletes a habit and all associated logs from persistent storage.
     */
    suspend fun deleteHabit(habitId: String)
}
