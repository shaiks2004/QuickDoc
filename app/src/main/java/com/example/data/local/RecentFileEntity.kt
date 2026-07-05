package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val lastModified: Long,
    val fileSize: Long,
    val pageCount: Int,
    val pageWidth: Int,
    val pageHeight: Int,
    val thumbnailPath: String?,
    val summary: String?,
    val embeddingBlob: ByteArray? = null
)
