package com.mlbb.assistant.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mlbb.assistant.data.local.database.AppDatabase
import com.mlbb.assistant.data.local.database.DraftSessionDao
import com.mlbb.assistant.data.local.database.HeroDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** v1→v2: adds draft_sessions table and new hero columns */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // New hero columns
            db.execSQL("ALTER TABLE heroes ADD COLUMN secondaryRole TEXT")
            db.execSQL("ALTER TABLE heroes ADD COLUMN lane TEXT NOT NULL DEFAULT 'GOLD'")
            db.execSQL("ALTER TABLE heroes ADD COLUMN tier TEXT NOT NULL DEFAULT 'B'")
            db.execSQL("ALTER TABLE heroes ADD COLUMN patchTrend REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE heroes ADD COLUMN counteredBy TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE heroes ADD COLUMN recommendedSpells TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE heroes ADD COLUMN coreItems TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE heroes ADD COLUMN flexLanes TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE heroes ADD COLUMN isToxicMechanic INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE heroes ADD COLUMN isOP INTEGER NOT NULL DEFAULT 0")

            // New draft_sessions table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS draft_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    rank TEXT NOT NULL,
                    banTotal INTEGER NOT NULL,
                    enemyBanIds TEXT NOT NULL,
                    yourBanIds TEXT NOT NULL,
                    enemyPickIds TEXT NOT NULL,
                    yourPickIds TEXT NOT NULL,
                    ourTeamFirst INTEGER NOT NULL,
                    draftScore INTEGER NOT NULL,
                    metaScore INTEGER NOT NULL,
                    counterScore INTEGER NOT NULL,
                    synergyScore INTEGER NOT NULL,
                    followedRecommendations INTEGER NOT NULL,
                    totalRecommendations INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mlbb_assistant.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideHeroDao(db: AppDatabase): HeroDao = db.heroDao()

    @Provides fun provideDraftSessionDao(db: AppDatabase): DraftSessionDao = db.draftSessionDao()
}
