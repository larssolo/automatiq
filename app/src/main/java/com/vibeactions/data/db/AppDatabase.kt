package com.vibeactions.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vibeactions.data.db.entities.MacroEntity
import com.vibeactions.data.db.entities.MacroLogEntity

@Database(entities = [MacroEntity::class, MacroLogEntity::class], version = 12, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao
    abstract fun macroLogDao(): MacroLogDao
}
