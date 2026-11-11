package com.sawn.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sawn.domain.model.BlockType
import com.sawn.domain.model.InterceptMode

class Converters {
    @TypeConverter fun toBlockType(value: String): BlockType = BlockType.valueOf(value)
    @TypeConverter fun fromBlockType(type: BlockType): String = type.name
    @TypeConverter fun toInterceptMode(value: String): InterceptMode = InterceptMode.valueOf(value)
    @TypeConverter fun fromInterceptMode(mode: InterceptMode): String = mode.name
}

@Database(
    entities = [BlockedItem::class, Schedule::class, AccessGrant::class, ProtectedDayEntity::class,
        ChallengeRunEntity::class, ChallengeDayEntity::class, TriggerLogEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SawnDatabase : RoomDatabase() {
    abstract fun blockDao(): BlockDao
    abstract fun protectedDaysDao(): ProtectedDaysDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun triggerDao(): TriggerDao

    companion object {
        @Volatile private var instance: SawnDatabase? = null

        /**
         * v3 → v4: تحدي 30 يوم + سجلات المحفزات (Plan B).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS challenge_runs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "startedAt INTEGER NOT NULL, " +
                        "endedAt INTEGER, " +
                        "completed INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS challenge_days (" +
                        "date TEXT PRIMARY KEY NOT NULL, " +
                        "runId INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS trigger_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "tag TEXT NOT NULL, " +
                        "hourOfDay INTEGER NOT NULL, " +
                        "note TEXT, " +
                        "timestampMs INTEGER NOT NULL)",
                )
            }
        }

        /**
         * v2 → v3: add the protected_days source of truth for the rebuilt day-streak.
         * Additive only — existing blocklists/schedules are untouched.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS protected_days (" +
                        "date TEXT PRIMARY KEY NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "recordedAt INTEGER NOT NULL)",
                )
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blocked_items ADD COLUMN interceptMode TEXT NOT NULL DEFAULT 'BLOCK'")
                db.execSQL("ALTER TABLE blocked_items ADD COLUMN pauseSeconds INTEGER NOT NULL DEFAULT 15")
                db.execSQL("ALTER TABLE blocked_items ADD COLUMN customPrompt TEXT")
                db.execSQL("ALTER TABLE blocked_items ADD COLUMN openLimitPerDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE blocked_items ADD COLUMN dailyLimitMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE blocked_items ADD COLUMN cooldownMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS access_grants (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "itemId INTEGER NOT NULL, packageName TEXT NOT NULL, " +
                        "grantedAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_access_grants_itemId ON access_grants (itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_access_grants_grantedAt ON access_grants (grantedAt)")
            }
        }

        fun get(context: Context): SawnDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SawnDatabase::class.java,
                    "sawn.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    // قاعدة بيانات محلية للتجريب — 2.1 مخصصة للتوزيع الخاص.
                    // أي فشل في الهجرة (أو اختلاف مخطط) لن يوقف التطبيق أبدًا.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
