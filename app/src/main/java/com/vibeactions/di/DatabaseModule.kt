package com.vibeactions.di

import android.content.Context
import androidx.room.Room
import com.vibeactions.data.db.AppDatabase
import com.vibeactions.data.db.MIGRATION_1_2
import com.vibeactions.data.db.MIGRATION_2_3
import com.vibeactions.data.db.MIGRATION_3_4
import com.vibeactions.data.db.MIGRATION_4_5
import com.vibeactions.data.db.MIGRATION_5_6
import com.vibeactions.data.db.MIGRATION_6_7
import com.vibeactions.data.db.MacroDao
import com.vibeactions.data.db.MacroLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "vibeactions.db")
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
            )
            .build()

    @Provides fun provideMacroDao(db: AppDatabase): MacroDao = db.macroDao()
    @Provides fun provideMacroLogDao(db: AppDatabase): MacroLogDao = db.macroLogDao()
}
