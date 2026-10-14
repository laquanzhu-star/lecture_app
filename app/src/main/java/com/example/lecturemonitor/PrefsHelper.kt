package com.example.lecturemonitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AlertRecord(
    val accountName: String,
    val title: String,
    val matchedKeyword: String,
    val time: String,
    val url: String = ""
)

object PrefsHelper {
    private const val PREF_NAME = "lecture_monitor_prefs"
    private const val KEY_ACCOUNTS = "accounts_set"
    private const val KEY_KEYWORDS = "keywords_set"
    private const val KEY_HISTORY = "alert_history_json"
    private const val KEY_SEEN_TITLES = "seen_titles_set"

    fun getAccounts(context: Context): MutableSet<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val defaultAccounts = setOf("清华大学", "北京大学", "学术讲座网")
        return prefs.getStringSet(KEY_ACCOUNTS, defaultAccounts)?.toMutableSet() ?: mutableSetOf()
    }

    fun saveAccounts(context: Context, accounts: Set<String>) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_ACCOUNTS, accounts).apply()
    }

    fun getKeywords(context: Context): MutableSet<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val defaultKeywords = setOf("讲座", "学术报告", "论坛", "研讨会", "Workshop")
        return prefs.getStringSet(KEY_KEYWORDS, defaultKeywords)?.toMutableSet() ?: mutableSetOf()
    }

    fun saveKeywords(context: Context, keywords: Set<String>) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_KEYWORDS, keywords).apply()
    }

    fun isTitleSeen(context: Context, uniqueKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(KEY_SEEN_TITLES, emptySet()) ?: emptySet()
        return seen.contains(uniqueKey)
    }

    fun markTitleSeen(context: Context, uniqueKey: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(KEY_SEEN_TITLES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (seen.size > 500) {
            seen.clear()
        }
        seen.add(uniqueKey)
        prefs.edit().putStringSet(KEY_SEEN_TITLES, seen).apply()
    }

    fun addHistoryRecord(context: Context, accountName: String, title: String, matchedKeyword: String, url: String = "") {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val nowStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())

        val obj = JSONObject().apply {
            put("accountName", accountName)
            put("title", title)
            put("matchedKeyword", matchedKeyword)
            put("time", nowStr)
            put("url", url)
        }

        val newArr = JSONArray()
        newArr.put(obj)
        for (i in 0 until minOf(arr.length(), 99)) {
            newArr.put(arr.getJSONObject(i))
        }
        prefs.edit().putString(KEY_HISTORY, newArr.toString()).apply()
    }

    fun getHistoryRecords(context: Context): List<AlertRecord> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<AlertRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                AlertRecord(
                    accountName = obj.optString("accountName"),
                    title = obj.optString("title"),
                    matchedKeyword = obj.optString("matchedKeyword"),
                    time = obj.optString("time"),
                    url = obj.optString("url", "")
                )
            )
        }
        return list
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, "[]").apply()
    }
}
