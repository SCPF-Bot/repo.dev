package com.mlbbassistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mlbbassistant.data.db.dao.HeroDao
import com.mlbbassistant.data.db.dao.MetaSnapshotDao
import com.mlbbassistant.data.db.entity.HeroEntity
import com.mlbbassistant.data.db.entity.MetaSnapshotEntity

@Database(
    entities  = [HeroEntity::class, MetaSnapshotEntity::class],
    version   = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun heroDao(): HeroDao
    abstract fun metaSnapshotDao(): MetaSnapshotDao
}
