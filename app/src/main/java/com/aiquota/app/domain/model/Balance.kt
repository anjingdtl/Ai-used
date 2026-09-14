package com.aiquota.app.domain.model

import com.aiquota.app.core.serialization.InstantEpochMillisSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** 货币 / Credit 余额 */
@Serializable
data class Balance(
    val id: String,
    val name: String = "余额",
    val available: Double,
    val currency: String? = null,
    val unit: String? = null,
    @Serializable(with = InstantEpochMillisSerializer::class)
    val updatedAt: Instant
)