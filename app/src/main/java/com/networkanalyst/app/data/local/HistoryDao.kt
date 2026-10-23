package com.networkanalyst.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.networkanalyst.app.data.local.entity.HistoryEntity
import com.networkanalyst.app.data.model.ToolType
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE toolType = :toolType ORDER BY timestamp DESC")
    fun getHistoryByTool(toolType: ToolType): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE target LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryEntity): Long

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scan_history WHERE toolType = :toolType")
    suspend fun clearByTool(toolType: ToolType)

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()
}
