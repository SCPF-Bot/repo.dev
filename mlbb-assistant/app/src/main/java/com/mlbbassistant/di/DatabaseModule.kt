package com.mlbbassistant.di

import android.content.Context
import androidx.room.Room
import com.mlbbassistant.data.db.AppDatabase
import com.mlbbassistant.data.db.dao.HeroDao
import com.mlbbassistant.data.db.dao.MetaSnapshotDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mlbb_assistant.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideHeroDao(db: AppDatabase): HeroDao = db.heroDao()

    @Provides
    fun provideMetaSnapshotDao(db: AppDatabase): MetaSnapshotDao = db.metaSnapshotDao()
}
