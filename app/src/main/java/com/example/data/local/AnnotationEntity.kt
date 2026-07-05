package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileUri: String,
    val pageIndex: Int,
    val type: String, // "DRAW", "HIGHLIGHT", "TEXT", "SIGNATURE"
    val color: Int,
    val thickness: Float,
    val pointsJson: String, // Coordinates serialized as JSON: "[[x1,y1],[x2,y2]]"
    val text: String?,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)
