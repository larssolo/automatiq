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

/**
 * v3 → v4: adds `week_interval` (1 = weekly, 2 = every other week, …) and `anchor_epoch_day`
 * (first-fire date anchoring the rhythm). Existing rows default to interval 1 / null anchor, i.e.
 * unchanged weekly behaviour.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE macros ADD COLUMN week_interval INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE macros ADD COLUMN anchor_epoch_day INTEGER")
    }
}

/** v4 → v5: adds `card_color` (ARGB Long). Existing macros default to 0 (= no color; UI uses primary accent). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE macros ADD COLUMN card_color INTEGER NOT NULL DEFAULT 0")
    }
}

/** v5 → v6: adds `valid_until_epoch_day` (inclusive expiry date). NULL = no expiry (unchanged behaviour). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE macros ADD COLUMN valid_until_epoch_day INTEGER")
    }
}

/** v6 → v7: adds `match_sender` and `match_keyword` for INCOMING (auto-reply) macros. NULL = match any. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE macros ADD COLUMN match_sender TEXT")
        db.execSQL("ALTER TABLE macros ADD COLUMN match_keyword TEXT")
    }
}

/** v7 → v8: adds geofence columns for LOCATION macros (lat/lng/radius/transition). NULL for others. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE macros ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE macros ADD COLUMN longitude REAL")
        db.execSQL("ALTER TABLE macros ADD COLUMN radius_meters REAL")
        db.execSQL("ALTER TABLE macros ADD COLUMN geofence_transition INTEGER")
    }
}

/** v8 → v9: adds AI reply fields for INCOMING macros.
 *  ai_reply_enabled: 0 = off (existing macros unchanged).
 *  ai_send_mode: 'APPROVE' = show notification before sending (safe default). */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE macros ADD COLUMN ai_reply_enabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE macros ADD COLUMN ai_send_mode TEXT NOT NULL DEFAULT 'APPROVE'")
    }
}
