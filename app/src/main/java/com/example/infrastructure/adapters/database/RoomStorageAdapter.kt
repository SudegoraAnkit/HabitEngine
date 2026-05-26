package com.example.infrastructure.adapters.database

import com.example.core.domain.Cadence
import com.example.core.domain.Habit
import com.example.core.domain.LifeDomain
import com.example.core.domain.TrackerState
import com.example.core.ports.StoragePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class RoomStorageAdapter(
    private val database: AppDatabase
) : StoragePort {

    private val habitDao = database.habitDao()
    private val logDao = database.logDao()

    override fun loadTrackerState(): Flow<TrackerState> {
        return combine(
            habitDao.getAllHabitsFlow(),
            logDao.getAllLogsFlow()
        ) { entityHabits, entityLogs ->
            val habitsList = entityHabits.map { entity ->
                Habit(
                    id = entity.id,
                    domain = try {
                        LifeDomain.valueOf(entity.domain)
                    } catch (e: Exception) {
                        LifeDomain.PERSONAL
                    },
                    cadence = try {
                        Cadence.valueOf(entity.cadence)
                    } catch (e: Exception) {
                        Cadence.DAILY
                    },
                    cueText = entity.cueText,
                    routineText = entity.routineText,
                    rewardText = entity.rewardText,
                    createdAt = entity.createdAt
                )
            }

            // Map List<LogEntity> to Map<String, Map<String, Boolean>>
            // Group by date (YYYY-MM-DD), then associate habitId -> completed boolean
            val logsMap = entityLogs.groupBy { it.date }.mapValues { entry ->
                entry.value.associate { it.habitId to it.completed }
            }

            TrackerState(habitsList, logsMap)
        }
    }

    override suspend fun saveHabit(habit: Habit) = withContext(Dispatchers.IO) {
        val entity = HabitEntity(
            id = habit.id,
            domain = habit.domain.name,
            cadence = habit.cadence.name,
            cueText = habit.cueText,
            routineText = habit.routineText,
            rewardText = habit.rewardText,
            createdAt = habit.createdAt
        )
        habitDao.insertHabit(entity)
    }

    override suspend fun toggleLogEntry(date: String, habitId: String, currentStatus: Boolean) = withContext(Dispatchers.IO) {
        // Safe toggle logic: currentStatus is passed in, toggle state is calculated and persisted
        val nextStatus = !currentStatus
        val entity = LogEntity(
            date = date,
            habitId = habitId,
            completed = nextStatus
        )
        logDao.insertLog(entity)
    }

    override suspend fun deleteHabit(habitId: String) = withContext(Dispatchers.IO) {
        habitDao.deleteHabit(habitId)
        logDao.deleteLogsForHabit(habitId)
    }
}
