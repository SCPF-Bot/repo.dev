package com.mlbb.assistant.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for the MLBB Assistant app.
 *
 * Schema export is enabled (see ksp { room.schemaLocation } in build.gradle.kts).
 * The generated JSON files in /schemas/ should be committed to version control
 * to enable safe migration authoring in future versions.
 *
 * IMPORTANT: When bumping [version], add a proper [Migration] object to the
 * builder call below. The [fallbackToDestructiveMigration] guard is here to
 * prevent a crash during development but MUST be replaced with real migrations
 * before a production release where user data must be preserved.
 */
@Database(
    entities = [
        HeroEntity::class,
        DraftSessionEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun heroDao(): HeroDao
    abstract fun draftSessionDao(): DraftSessionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mlbb_assistant.db")
                // Replace with explicit Migration objects before a production release.
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
    }
}
