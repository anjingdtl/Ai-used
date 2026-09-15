package com.aiquota.app

import android.app.Application

/**
 * Robolectric 专用测试 Application。
 *
 * 生产用 [AiQuotaApplication] 会在 onCreate 里初始化 WorkManager（周期同步调度），
 * 这会导致纯组件测试（加密 / Room CredentialStore）在 Robolectric 下报
 * "WorkManager is not initialized properly"。
 *
 * 这些测试不依赖 Hilt 与 WorkManager，因此用本空 Application 避免其副作用。
 */
class TestRobolectricApplication : Application()