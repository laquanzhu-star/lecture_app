package com.example.lecturemonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_ID = "lecture_alert_high_channel"
    private const val CHANNEL_NAME = "公众号讲座强提醒"

    fun sendLectureAlert(
        context: Context,
        accountName: String,
        articleTitle: String,
        matchedKeyword: String,
        articleUrl: String = ""
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "当监控的微信公众号发布包含目标关键词的文章时弹出强提醒"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val clickIntent = if (articleUrl.isNotBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(articleUrl))
        } else {
            Intent(context, MainActivity::class.java)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("【$matchedKeyword】$accountName 发布新消息")
            .setContentText(articleTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("公众号：$accountName\n文章标题：$articleTitle\n命中关键词：$matchedKeyword"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }
}
