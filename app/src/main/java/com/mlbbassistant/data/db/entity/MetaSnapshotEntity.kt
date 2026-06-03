package com.mlbbassistant.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meta_snapshot")
data class MetaSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "patch")       val patch: String,
    @ColumnInfo(name = "updated_at")  val updatedAt: Long,
    @ColumnInfo(name = "fetched_at")  val fetchedAt: Long = System.currentTimeMillis()
)
