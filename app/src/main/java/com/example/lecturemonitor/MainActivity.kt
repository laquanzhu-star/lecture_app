package com.example.lecturemonitor

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var tvServiceStatus: TextView
    private lateinit var chipGroupAccounts: ChipGroup
    private lateinit var chipGroupKeywords: ChipGroup
    private lateinit var layoutHistoryList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()
        schedulePeriodicBackgroundPolling()

        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        chipGroupAccounts = findViewById(R.id.chipGroupAccounts)
        chipGroupKeywords = findViewById(R.id.chipGroupKeywords)
        layoutHistoryList = findViewById(R.id.layoutHistoryList)

        val btnOpenPermission = findViewById<Button>(R.id.btnOpenPermission)
        val etNewAccount = findViewById<EditText>(R.id.etNewAccount)
        val btnAddAccount = findViewById<Button>(R.id.btnAddAccount)
        val etNewKeyword = findViewById<EditText>(R.id.etNewKeyword)
        val btnAddKeyword = findViewById<Button>(R.id.btnAddKeyword)
        val btnTestAlert = findViewById<Button>(R.id.btnTestAlert)
        val btnPollNow = findViewById<Button>(R.id.btnPollNow)
        val btnClearHistory = findViewById<Button>(R.id.btnClearHistory)

        btnOpenPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnAddAccount.setOnClickListener {
            val name = etNewAccount.text.toString().trim()
            if (name.isNotEmpty()) {
                val set = PrefsHelper.getAccounts(this)
                set.add(name)
                PrefsHelper.saveAccounts(this, set)
                etNewAccount.setText("")
                refreshAccountsChips()
            }
        }

        btnAddKeyword.setOnClickListener {
            val kw = etNewKeyword.text.toString().trim()
            if (kw.isNotEmpty()) {
                val set = PrefsHelper.getKeywords(this)
                set.add(kw)
                PrefsHelper.saveKeywords(this, set)
                etNewKeyword.setText("")
                refreshKeywordsChips()
            }
        }

        btnTestAlert.setOnClickListener {
            val sampleAcc = PrefsHelper.getAccounts(this).firstOrNull() ?: "清华大学"
            val sampleKw = PrefsHelper.getKeywords(this).firstOrNull() ?: "讲座"
            val sampleTitle = "【学术预告】本周五人工智能前沿系列${sampleKw}（第42期）"
            PrefsHelper.addHistoryRecord(this, sampleAcc, sampleTitle, sampleKw)
            NotificationHelper.sendLectureAlert(this, sampleAcc, sampleTitle, sampleKw)
            refreshHistoryUI()
            Toast.makeText(this, "已发送测试弹窗提醒！请查看手机顶部通知栏", Toast.LENGTH_SHORT).show()
        }

        btnPollNow.setOnClickListener {
            val req = OneTimeWorkRequestBuilder<ArticlePollWorker>().build()
            WorkManager.getInstance(this).enqueue(req)
            Toast.makeText(this, "已启动后台立即检索公众号最新文章标题...", Toast.LENGTH_SHORT).show()
        }

        btnClearHistory.setOnClickListener {
            PrefsHelper.clearHistory(this)
            refreshHistoryUI()
        }

        refreshAccountsChips()
        refreshKeywordsChips()
        refreshHistoryUI()
    }

    override fun onResume() {
        super.onResume()
        updateListenerStatus()
        refreshHistoryUI()
    }

    private fun updateListenerStatus() {
        val enabled = isNotificationListenerEnabled()
        if (enabled) {
            tvServiceStatus.text = "运行状态：已开启微信通知实时监听 + 后台定时巡检"
            tvServiceStatus.setTextColor(0xFF1B5E20.toInt())
        } else {
            tvServiceStatus.text = "运行状态：尚未开启【通知读取权限】，请点击下方按钮开启"
            tvServiceStatus.setTextColor(0xFFB71C1C.toInt())
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(this, WeChatNotificationService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun refreshAccountsChips() {
        chipGroupAccounts.removeAllViews()
        val accounts = PrefsHelper.getAccounts(this)
        for (acc in accounts) {
            val chip = Chip(this).apply {
                text = acc
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    val updated = PrefsHelper.getAccounts(this@MainActivity)
                    updated.remove(acc)
                    PrefsHelper.saveAccounts(this@MainActivity, updated)
                    refreshAccountsChips()
                }
            }
            chipGroupAccounts.addView(chip)
        }
    }

    private fun refreshKeywordsChips() {
        chipGroupKeywords.removeAllViews()
        val keywords = PrefsHelper.getKeywords(this)
        for (kw in keywords) {
            val chip = Chip(this).apply {
                text = kw
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    val updated = PrefsHelper.getKeywords(this@MainActivity)
                    updated.remove(kw)
                    PrefsHelper.saveKeywords(this@MainActivity, updated)
                    refreshKeywordsChips()
                }
            }
            chipGroupKeywords.addView(chip)
        }
    }

    private fun refreshHistoryUI() {
        layoutHistoryList.removeAllViews()
        val records = PrefsHelper.getHistoryRecords(this)
        if (records.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "暂无命中的讲座推文记录。可点击上方「测试弹窗提醒」体验效果。"
                textSize = 14f
                setPadding(8, 16, 8, 16)
            }
            layoutHistoryList.addView(emptyTv)
            return
        }
        for (rec in records) {
            val itemTv = TextView(this).apply {
                text = "[${rec.time}] 【${rec.matchedKeyword}】${rec.accountName}：${rec.title}"
                textSize = 14f
                setPadding(12, 16, 12, 16)
            }
            layoutHistoryList.addView(itemTv)
        }
    }

    private fun schedulePeriodicBackgroundPolling() {
        val pollRequest = PeriodicWorkRequestBuilder<ArticlePollWorker>(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeChatArticlePollWork",
            ExistingPeriodicWorkPolicy.KEEP,
            pollRequest
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }
}
