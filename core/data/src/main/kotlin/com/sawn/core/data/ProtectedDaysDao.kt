package com.sawn.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectedDaysDao {

    /** Idempotent by date PK — today recorded at 3pm and again at 11pm is one row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: ProtectedDayEntity)

    @Query("SELECT date FROM protected_days")
    suspend fun all(): List<String>

    @Query("SELECT date FROM protected_days")
    fun observe(): Flow<List<String>>
}
