package com.mlbb.assistant.di

import android.content.Context
import androidx.room.Room
import com.mlbb.assistant.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mlbb_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideHeroDao(database: AppDatabase) = database.heroDao()
}