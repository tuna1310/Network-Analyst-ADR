package com.networkanalyst.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.networkanalyst.app.data.local.entity.HistoryEntity
import com.networkanalyst.app.data.model.ToolType

class DatabaseConverters {
    @TypeConverter
    fun fromToolType(type: ToolType): String = type.name

    @TypeConverter
    fun toToolType(name: String): ToolType = try {
        ToolType.valueOf(name)
    } catch (e: Exception) {
        ToolType.NETWORK_INFO
    }
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
