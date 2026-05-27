package com.example.infrastructure.adapters.database

import com.example.core.domain.ActivityCategory
import com.example.core.domain.ActivityLog
import com.example.core.domain.Cadence
import com.example.core.domain.Habit
import com.example.core.domain.LifeDomain
import com.example.core.domain.TrackerState
import com.example.core.ports.StoragePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomStorageAdapter(
    private val database: AppDatabase
) : StoragePort {

    private val habitDao = database.habitDao()
    private val logDao = database.logDao()
    private val activityLogDao = database.activityLogDao()

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
                    createdAt = entity.createdAt,
                    notes = entity.notes,
                    isBad = entity.isBad
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
            createdAt = habit.createdAt,
            notes = habit.notes,
            isBad = habit.isBad
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

    override fun loadActivityLogs(): Flow<List<ActivityLog>> {
        return activityLogDao.getAllActivityLogs().map { entities ->
            entities.map { entity ->
                ActivityLog(
                    id = entity.id,
                    description = entity.description,
                    category = try {
                        ActivityCategory.valueOf(entity.category)
                    } catch (e: Exception) {
                        ActivityCategory.NEUTRAL
                    },
                    timestamp = entity.timestamp,
                    durationMinutes = entity.durationMinutes
                )
            }
        }
    }

    override suspend fun saveActivityLog(log: ActivityLog) = withContext(Dispatchers.IO) {
        val entity = ActivityLogEntity(
            id = log.id,
            description = log.description,
            category = log.category.name,
            timestamp = log.timestamp,
            durationMinutes = log.durationMinutes
        )
        activityLogDao.insertActivityLog(entity)
    }

    override suspend fun deleteActivityLog(id: String) = withContext(Dispatchers.IO) {
        activityLogDao.deleteActivityLog(id)
    }

    override suspend fun restoreBackup(
        habits: List<Habit>,
        logs: List<LogEntity>,
        activityLogs: List<ActivityLog>
    ) = withContext(Dispatchers.IO) {
        // Clear old state completely
        database.habitDao().clearAllHabits()
        database.logDao().clearAllLogs()
        database.activityLogDao().clearAllActivityLogs()

        // Insert new habits
        habits.forEach { habit ->
            val entity = HabitEntity(
                id = habit.id,
                domain = habit.domain.name,
                cadence = habit.cadence.name,
                cueText = habit.cueText,
                routineText = habit.routineText,
                rewardText = habit.rewardText,
                createdAt = habit.createdAt,
                notes = habit.notes,
                isBad = habit.isBad
            )
            database.habitDao().insertHabit(entity)
        }

        // Insert logs
        logs.forEach { log ->
            database.logDao().insertLog(log)
        }

        // Insert activity logs
        activityLogs.forEach { log ->
            val entity = ActivityLogEntity(
                id = log.id,
                description = log.description,
                category = log.category.name,
                timestamp = log.timestamp,
                durationMinutes = log.durationMinutes
            )
            database.activityLogDao().insertActivityLog(entity)
        }
    }
}
