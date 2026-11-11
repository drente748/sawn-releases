package com.sawn.core.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** بدء تحدٍّ جديد — لا يتقدّم إلا بيوم محمي. */
@Entity(tableName = "challenge_runs")
data class ChallengeRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val completed: Boolean = false,
)

/** يوم واحد في التحدي (يوم محمي). */
@Entity(tableName = "challenge_days")
data class ChallengeDayEntity(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val runId: Long,
)

@Dao
interface ChallengeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: ChallengeRunEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordDay(day: ChallengeDayEntity)

    @Query("SELECT * FROM challenge_runs ORDER BY id DESC LIMIT 1")
    suspend fun latestRun(): ChallengeRunEntity?

    @Query("SELECT COUNT(*) FROM challenge_days WHERE runId = :runId")
    suspend fun countDays(runId: Long): Int
}

/** وسْم محفز واحد (محلي 100%). */
@Entity(tableName = "trigger_logs")
data class TriggerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tag: String, // TriggerTag.name
    val hourOfDay: Int,
    val note: String? = null,
    val timestampMs: Long = 0L,
)

@Dao
interface TriggerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: TriggerLogEntity)

    @Query("SELECT * FROM trigger_logs ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<TriggerLogEntity>

    @Query("SELECT COUNT(*) FROM trigger_logs")
    suspend fun count(): Int
}
