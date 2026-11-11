package com.sawn.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One row per protected calendar day (ISO yyyy-MM-dd). Presence = protected; absence = missed.
 * The table is tiny (days in a decade ≤ 3 650 rows) so reads stay trivial.
 */
@Entity(tableName = "protected_days")
data class ProtectedDayEntity(
    @PrimaryKey val date: String,
    val source: String,
    val recordedAt: Long,
) {
    companion object {
        fun of(date: LocalDate, source: String): ProtectedDayEntity =
            ProtectedDayEntity(date = date.toString(), source = source, recordedAt = System.currentTimeMillis())
    }
}
