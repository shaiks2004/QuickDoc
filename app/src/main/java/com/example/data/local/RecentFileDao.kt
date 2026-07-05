package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY lastModified DESC")
    fun getAllRecentFiles(): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(file: RecentFileEntity)

    @Query("SELECT * FROM recent_files WHERE uri = :uri LIMIT 1")
    suspend fun getRecentFileByUri(uri: String): RecentFileEntity?

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    suspend fun deleteRecentFileByUri(uri: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearAllRecentFiles()
}
