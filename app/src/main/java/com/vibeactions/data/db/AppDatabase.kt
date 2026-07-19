package com.vibeactions.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vibeactions.data.db.entities.FolderEntity
import com.vibeactions.data.db.entities.MacroEntity
import com.vibeactions.data.db.entities.MacroLogEntity

@Database(
    entities = [MacroEntity::class, MacroLogEntity::class, FolderEntity::class],
    version = 13,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao
    abstract fun macroLogDao(): MacroLogDao
    abstract fun folderDao(): FolderDao
}
