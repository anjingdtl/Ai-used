package com.aiquota.app.di

import com.aiquota.app.domain.repository.QuotaProvider
import com.aiquota.app.provider.debug.DebugQuotaProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Debug 专属 Provider DI（仅存在于 debug 源集）。
 * Release 不编译本文件，因此 Release 注册表里没有 DebugQuotaProvider。
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugProviderModule {

    @Provides
    @IntoSet
    fun bindDebugProvider(): QuotaProvider = DebugQuotaProvider()
}