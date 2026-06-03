package com.mlbbassistant.data.db.dao

import androidx.room.*
import com.mlbbassistant.data.db.entity.MetaSnapshotEntity

@Dao
interface MetaSnapshotDao {

    @Query("SELECT * FROM meta_snapshot ORDER BY fetched_at DESC LIMIT 1")
    suspend fun getLatest(): MetaSnapshotEntity?

    @Upsert
    suspend fun upsert(snapshot: MetaSnapshotEntity)

    @Query("DELETE FROM meta_snapshot")
    suspend fun deleteAll()
}
