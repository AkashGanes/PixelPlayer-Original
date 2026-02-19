package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    val content: String,
    @ColumnInfo(defaultValue = "0")
    val isSynced: Boolean = false,
    val source: String? = null // "local", "remote", "embedded" - optional
)
