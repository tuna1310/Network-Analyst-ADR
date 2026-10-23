package com.networkanalyst.app.di

import android.content.Context
import androidx.room.Room
import com.networkanalyst.app.data.local.AppDatabase
import com.networkanalyst.app.data.local.HistoryDao
import com.networkanalyst.app.data.service.AdService
import com.networkanalyst.app.data.service.DiagnosticsHelper
import com.networkanalyst.app.data.service.MockAdServiceImpl
import com.networkanalyst.app.data.service.NetworkDiagnosticsService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAdService(impl: MockAdServiceImpl): AdService

    @Binds
    @Singleton
    abstract fun bindDiagnosticsHelper(impl: NetworkDiagnosticsService): DiagnosticsHelper
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "network_analyst.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: AppDatabase): HistoryDao {
        return database.historyDao()
    }
}
