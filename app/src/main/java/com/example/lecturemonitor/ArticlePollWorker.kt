package com.example.lecturemonitor

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.jsoup.Jsoup
import java.net.URLEncoder

class ArticlePollWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val accounts = PrefsHelper.getAccounts(applicationContext)
        val keywords = PrefsHelper.getKeywords(applicationContext)

        if (accounts.isEmpty() || keywords.isEmpty()) {
            return Result.success()
        }

        for (account in accounts) {
            try {
                val query = URLEncoder.encode(account, "UTF-8")
                val searchUrl = "https://weixin.sogou.com/weixin?type=2&query=$query"
                val doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                    .timeout(10000)
                    .get()

                val items = doc.select("ul.news-list li")
                for (item in items) {
                    val titleEl = item.selectFirst("h3 a") ?: continue
                    val titleText = titleEl.text().trim()
                    val href = titleEl.attr("href")
                    val fullUrl = if (href.startsWith("http")) href else "https://weixin.sogou.com$href"

                    val matchedKw = keywords.firstOrNull { kw ->
                        titleText.contains(kw, ignoreCase = true)
                    }

                    if (matchedKw != null) {
                        val uniqueKey = "$account|$titleText"
                        if (!PrefsHelper.isTitleSeen(applicationContext, uniqueKey)) {
                            PrefsHelper.markTitleSeen(applicationContext, uniqueKey)
                            PrefsHelper.addHistoryRecord(
                                context = applicationContext,
                                accountName = account,
                                title = titleText,
                                matchedKeyword = matchedKw,
                                url = fullUrl
                            )
                            NotificationHelper.sendLectureAlert(
                                context = applicationContext,
                                accountName = account,
                                articleTitle = titleText,
                                matchedKeyword = matchedKw,
                                articleUrl = fullUrl
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // 网络波动或验证码限制时静默跳过，等待下次轮询或由通知栏监听兜底
            }
        }
        return Result.success()
    }
}
