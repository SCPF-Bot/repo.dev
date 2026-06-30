package com.mlbb.assistant.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mlbb.assistant.data.local.database.AppDatabase
import com.mlbb.assistant.data.local.database.DraftSessionDao
import com.mlbb.assistant.data.local.database.HeroDao
import com.mlbb.assistant.data.local.database.HeroPoolDao
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

    /**
     * v2→v3:
     * - heroes: adds `hasCCUlt` column (TD-01)
     * - draft_sessions: adds `outcome` and `isSimulation` columns
     * - hero_pool: new table for personal hero pool feature
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // heroes: CC ult flag
            db.execSQL("ALTER TABLE heroes ADD COLUMN hasCCUlt INTEGER NOT NULL DEFAULT 0")

            // draft_sessions: match outcome + simulation flag
            db.execSQL("ALTER TABLE draft_sessions ADD COLUMN outcome TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE draft_sessions ADD COLUMN isSimulation INTEGER NOT NULL DEFAULT 0")

            // hero_pool: personal pool with proficiency level
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS hero_pool (
                    heroId INTEGER PRIMARY KEY NOT NULL,
                    proficiency TEXT NOT NULL DEFAULT 'COMFORTABLE'
                )
            """.trimIndent())
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mlbb_assistant.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            // P1 fix: fallbackToDestructiveMigration() covers upgrade paths whose starting
            // version is not in the migration chain (e.g. v0 → v3 on an old beta install).
            // Without this, Room throws IllegalStateException: "A migration from X to Y
            // cannot be found" and the app crashes before any UI is shown.
            // fallbackToDestructiveMigrationOnDowngrade is still kept for downgrades.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    // P2 fix: all DAO providers are now @Singleton so Hilt returns the same proxy
    // instance on every injection rather than constructing a new one each time.
    // The underlying database is already a singleton; the DAO proxy itself is
    // stateless, so a single shared instance is both safe and more efficient.
    @Provides @Singleton fun provideHeroDao(db: AppDatabase): HeroDao = db.heroDao()

    @Provides @Singleton fun provideDraftSessionDao(db: AppDatabase): DraftSessionDao = db.draftSessionDao()

    @Provides @Singleton fun provideHeroPoolDao(db: AppDatabase): HeroPoolDao = db.heroPoolDao()
}
