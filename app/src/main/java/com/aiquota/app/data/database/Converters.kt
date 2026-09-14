package com.aiquota.app.data.database

import androidx.room.TypeConverter
import com.aiquota.app.domain.model.CredentialType
import com.aiquota.app.domain.model.DataSource
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.QuotaType
import com.aiquota.app.domain.model.SyncStatus
import com.aiquota.app.domain.model.WindowType
import com.aiquota.app.core.security.SecretMasker
import java.time.Instant

class Converters {
    @TypeConverter fun instantToEpoch(i: Instant?): Long? = i?.toEpochMilli()
    @TypeConverter fun epochToInstant(l: Long?): Instant? = l?.let { Instant.ofEpochMilli(it) }

    @TypeConverter fun dataSourceToString(s: DataSource?): String? = s?.name
    @TypeConverter fun stringToDataSource(s: String?): DataSource? = s?.let { DataSource.valueOf(it) }

    @TypeConverter fun quotaTypeToString(t: QuotaType?): String? = t?.name
    @TypeConverter fun stringToQuotaType(s: String?): QuotaType? = s?.let { QuotaType.valueOf(it) }

    @TypeConverter fun windowTypeToString(t: WindowType?): String? = t?.name
    @TypeConverter fun stringToWindowType(s: String?): WindowType? = s?.let { WindowType.valueOf(it) }

    @TypeConverter fun syncStatusToString(s: SyncStatus?): String? = s?.name
    @TypeConverter fun stringToSyncStatus(s: String?): SyncStatus? = s?.let { SyncStatus.valueOf(it) }

    @TypeConverter fun connectorToString(c: ProviderAccount.ConnectorType?): String? = c?.name
    @TypeConverter fun stringToConnector(s: String?): ProviderAccount.ConnectorType? =
        s?.let { ProviderAccount.ConnectorType.valueOf(it) }

    @TypeConverter fun credentialTypeToString(t: CredentialType?): String? = t?.name
    @TypeConverter fun stringToCredentialType(s: String?): CredentialType? =
        s?.let { CredentialType.valueOf(it) }
}

/** 预防在数据库列上误存明文 secret 的哨兵 */
object DbGuard {
    fun assertNotSecret(value: String?, field: String) {
        if (!value.isNullOrBlank() && value.length > 28) {
            // 仅打日志提醒，不阻断（明文不会由我们写入）
            android.util.Log.w("DbGuard", "$field 疑似包含长敏感串: ${SecretMasker.mask(value)}")
        }
    }
}