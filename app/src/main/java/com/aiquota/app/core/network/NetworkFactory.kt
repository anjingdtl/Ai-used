package com.aiquota.app.core.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.time.Duration
import java.util.concurrent.TimeUnit

object NetworkFactory {

    fun buildJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun buildHttpClient(
        connectTimeout: Long = 15_000,
        readTimeout: Long = 20_000,
        writeTimeout: Long = 20_000,
        retryEnabled: Boolean = true
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(Duration.ofMillis(connectTimeout))
            .readTimeout(Duration.ofMillis(readTimeout))
            .writeTimeout(Duration.ofMillis(writeTimeout))
            .retryOnConnectionFailure(retryEnabled)
            .connectionPool(ConnectionPool(4, 1, TimeUnit.MINUTES))
            .addInterceptor(RedactingInterceptor())
        return builder.build()
    }

    fun buildRetrofit(
        baseUrl: String,
        client: OkHttpClient = buildHttpClient()
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(NetworkFactory.buildJson().asConverterFactory(contentType))
            .build()
    }
}