package com.aiquota.app.di

import android.content.Context
import androidx.room.Room
import com.aiquota.app.data.database.AiQuotaDatabase
import com.aiquota.app.data.database.dao.AccountDao
import com.aiquota.app.data.database.dao.AppSettingDao
import com.aiquota.app.data.database.dao.EventDao
import com.aiquota.app.data.database.dao.HistoryDao
import com.aiquota.app.data.database.dao.QuotaDao
import com.aiquota.app.data.repository.AccountRepositoryImpl
import com.aiquota.app.data.repository.DataStoreSettingsRepository
import com.aiquota.app.data.repository.HistoryRepositoryImpl
import com.aiquota.app.data.repository.NotificationRepositoryImpl
import com.aiquota.app.data.repository.QuotaRepositoryImpl
import com.aiquota.app.domain.repository.AccountRepository
import com.aiquota.app.domain.repository.HistoryRepository
import com.aiquota.app.domain.repository.NotificationRepository
import com.aiquota.app.domain.repository.QuotaRepository
import com.aiquota.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AiQuotaDatabase =
        Room.databaseBuilder(context, AiQuotaDatabase::class.java, AiQuotaDatabase.NAME)
            .build()

    @Provides fun provideAccountDao(db: AiQuotaDatabase): AccountDao = db.accountDao()
    @Provides fun provideQuotaDao(db: AiQuotaDatabase): QuotaDao = db.quotaDao()
    @Provides fun provideHistoryDao(db: AiQuotaDatabase): HistoryDao = db.historyDao()
    @Provides fun provideEventDao(db: AiQuotaDatabase): EventDao = db.eventDao()
    @Provides fun provideAppSettingDao(db: AiQuotaDatabase): AppSettingDao = db.appSettingDao()

    @Provides @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        DataStoreSettingsRepository(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindQuotaRepository(impl: QuotaRepositoryImpl): QuotaRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository
}