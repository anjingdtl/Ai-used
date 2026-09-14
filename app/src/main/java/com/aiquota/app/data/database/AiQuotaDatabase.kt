package com.aiquota.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aiquota.app.data.database.dao.AccountDao
import com.aiquota.app.data.database.dao.AppSettingDao
import com.aiquota.app.data.database.dao.EventDao
import com.aiquota.app.data.database.dao.HistoryDao
import com.aiquota.app.data.database.dao.QuotaDao
import com.aiquota.app.data.database.entity.AppSettingEntity
import com.aiquota.app.data.database.entity.CredentialEntity
import com.aiquota.app.data.database.entity.LatestQuotaSnapshotEntity
import com.aiquota.app.data.database.entity.NotificationEventEntity
import com.aiquota.app.data.database.entity.ProviderAccountEntity
import com.aiquota.app.data.database.entity.QuotaBucketEntity
import com.aiquota.app.data.database.entity.QuotaHistoryEntity
import com.aiquota.app.data.database.entity.SyncEventEntity

@Database(
    entities = [
        ProviderAccountEntity::class,
        CredentialEntity::class,
        LatestQuotaSnapshotEntity::class,
        QuotaBucketEntity::class,
        QuotaHistoryEntity::class,
        SyncEventEntity::class,
        NotificationEventEntity::class,
        AppSettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AiQuotaDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun quotaDao(): QuotaDao
    abstract fun historyDao(): HistoryDao
    abstract fun eventDao(): EventDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        const val NAME = "ai_quota.db"
    }
}