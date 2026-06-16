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
