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
    val createdAt: Long
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

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAt DESC")
    fun getAllHabitsFlow(): Flow<List<HabitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabit(id: String)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM day_logs")
    fun getAllLogsFlow(): Flow<List<LogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity)

    @Query("DELETE FROM day_logs WHERE habitId = :habitId")
    suspend fun deleteLogsForHabit(habitId: String)
}

@Database(entities = [HabitEntity::class, LogEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun logDao(): LogDao
}
