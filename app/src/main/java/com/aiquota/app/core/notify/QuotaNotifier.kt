package com.aiquota.app.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aiquota.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** 额度不足提醒 */
@Singleton
class QuotaNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val idGen = AtomicInteger(1000)

    fun notifyThreshold(accountName: String, bucketName: String, percent: Double) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AI 额度不足")
            .setContentText("$accountName · $bucketName 仅剩 ${percent.roundToInt()}%")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$accountName 的「$bucketName」额度仅剩 ${percent.roundToInt()}%，请留意补充。"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(idGen.getAndIncrement(), notification)
    }

    companion object {
        const val CHANNEL_ID = "quota_alert"

        @JvmStatic
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID, "额度提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "当关键额度低于设定阈值时提醒"
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}