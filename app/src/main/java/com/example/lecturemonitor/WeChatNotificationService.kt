package com.example.lecturemonitor

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WeChatNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 仅监听微信 (com.tencent.mm) 的通知
        val pkg = sbn.packageName ?: return
        if (pkg != "com.tencent.mm") return

        val extras = sbn.notification?.extras ?: return
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val rawText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

        if (rawTitle.isEmpty() && rawText.isEmpty()) return

        checkAndTriggerAlert(rawTitle, rawText)
    }

    private fun checkAndTriggerAlert(notifTitle: String, notifText: String) {
        val accounts = PrefsHelper.getAccounts(applicationContext)
        val keywords = PrefsHelper.getKeywords(applicationContext)

        if (accounts.isEmpty() || keywords.isEmpty()) return

        val combinedText = "$notifTitle $notifText"

        // 1. 判断是否来自用户添加的公众号（支持直接公众号名通知，或“订阅号消息”折叠摘要中带公众号名）
        val matchedAccount = accounts.firstOrNull { acc ->
            notifTitle.contains(acc, ignoreCase = true) || combinedText.contains(acc, ignoreCase = true)
        } ?: return

        // 2. 判断文章标题是否包含用户设定的关键词
        val matchedKeyword = keywords.firstOrNull { kw ->
            notifText.contains(kw, ignoreCase = true) || notifTitle.contains(kw, ignoreCase = true)
        } ?: return

        val uniqueKey = "$matchedAccount|$notifText"
        if (PrefsHelper.isTitleSeen(applicationContext, uniqueKey)) return
        PrefsHelper.markTitleSeen(applicationContext, uniqueKey)

        // 3. 记录历史并弹出手机强提醒
        PrefsHelper.addHistoryRecord(
            context = applicationContext,
            accountName = matchedAccount,
            title = notifText.ifEmpty { notifTitle },
            matchedKeyword = matchedKeyword
        )

        NotificationHelper.sendLectureAlert(
            context = applicationContext,
            accountName = matchedAccount,
            articleTitle = notifText.ifEmpty { notifTitle },
            matchedKeyword = matchedKeyword
        )
    }
}
