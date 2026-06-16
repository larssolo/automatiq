package com.vibeactions.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: adds `last_scheduled_fire_at` (decouples the scheduled once-per-day guard from manual
 * sends) and `sort_order` (manual drag-and-drop list ordering). Existing rows keep
 * last_scheduled_fire_at = NULL (so their next scheduled occurrence still fires) and sort_order = 0
 * (preserving the prior newest-first ordering until the user reorders).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE macros ADD COLUMN last_scheduled_fire_at INTEGER")
        db.execSQL("ALTER TABLE macros ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v2 → v3: adds `days_of_week` (weekday bitmask, bit day-1 for ISO 1=Mon..7=Sun). Existing rows
 * default to 127 (all seven days) so prior "every day" scheduled macros keep firing daily.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE macros ADD COLUMN days_of_week INTEGER NOT NULL DEFAULT 127")
    }
}
