package com.networkanalyst.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.networkanalyst.app.data.model.ToolType

@Entity(tableName = "scan_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val toolType: ToolType,
    val target: String,
    val summary: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
