package com.example.infrastructure.adapters.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "habits")
data class HabitEntity(
    val id: String, // primary key
    val domain: String,
    val cadence: String,
    val cueText: String,
    val routineText: String,
    val rewardText: String,
    val createdAt: Long,
    val notes: String = "",
    val isBad: Boolean = false
) {
    // Making id primary key
    @androidx.room.PrimaryKey
    var primaryKeyId: String = id
}

@Entity(tableName = "day_logs", primaryKeys = ["date", "habitId"])
data class LogEntity(
    val date: String,
    val habitId: String,
    val completed: Boolean
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @androidx.room.PrimaryKey
    val id: String,
    val description: String,
    val category: String, // "IMPORTANT", "TIME_WASTER", "NEUTRAL"
    val timestamp: Long,
    val durationMinutes: Int
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAt DESC")
    fun getAllHabitsFlow(): Flow<List<HabitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabit(id: String)

    @Query("DELETE FROM habits")
    suspend fun clearAllHabits()
}

@Dao
interface LogDao {
    @Query("SELECT * FROM day_logs")
    fun getAllLogsFlow(): Flow<List<LogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity)

    @Query("DELETE FROM day_logs WHERE habitId = :habitId")
    suspend fun deleteLogsForHabit(habitId: String)

    @Query("DELETE FROM day_logs")
    suspend fun clearAllLogs()
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAllActivityLogs(): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLogEntity)

    @Query("DELETE FROM activity_logs WHERE id = :id")
    suspend fun deleteActivityLog(id: String)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAllActivityLogs()
}

@Database(entities = [HabitEntity::class, LogEntity::class, ActivityLogEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun logDao(): LogDao
    abstract fun activityLogDao(): ActivityLogDao
}
