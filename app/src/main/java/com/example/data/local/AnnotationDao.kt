package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE fileUri = :fileUri")
    fun getAnnotationsForFile(fileUri: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE fileUri = :fileUri AND pageIndex = :pageIndex")
    fun getAnnotationsForFileAndPage(fileUri: String, pageIndex: Int): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: Int)

    @Query("DELETE FROM annotations WHERE fileUri = :fileUri AND pageIndex = :pageIndex")
    suspend fun deleteAnnotationsForPage(fileUri: String, pageIndex: Int)

    @Query("DELETE FROM annotations WHERE fileUri = :fileUri")
    suspend fun clearAnnotationsForFile(fileUri: String)
}
