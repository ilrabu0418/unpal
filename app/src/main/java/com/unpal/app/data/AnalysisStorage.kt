package com.unpal.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.unpal.app.viewmodel.AnalysisResult
import com.unpal.app.viewmodel.InstagramAccount

class AnalysisStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "unpal_analysis"
        private const val KEY_FOLLOWERS = "followers"
        private const val KEY_FOLLOWING = "following"
        private const val KEY_NOT_FOLLOWING_BACK = "not_following_back"
        private const val KEY_NOT_FOLLOWED_BACK = "not_followed_back"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val KEY_REMOVED_ACCOUNTS = "removed_accounts"
        private const val KEY_FOLLOWING_FILE_URI = "following_file_uri"
    }

    fun saveAnalysisResult(result: AnalysisResult) {
        prefs.edit().apply {
            putString(KEY_FOLLOWERS, gson.toJson(result.followers))
            putString(KEY_FOLLOWING, gson.toJson(result.following))
            putString(KEY_NOT_FOLLOWING_BACK, gson.toJson(result.notFollowingBack))
            putString(KEY_NOT_FOLLOWED_BACK, gson.toJson(result.notFollowedBack))
            putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            apply()
        }
    }

    fun getAnalysisResult(): AnalysisResult? {
        val followersJson = prefs.getString(KEY_FOLLOWERS, null) ?: return null
        val followingJson = prefs.getString(KEY_FOLLOWING, null) ?: return null
        val notFollowingBackJson = prefs.getString(KEY_NOT_FOLLOWING_BACK, null) ?: return null
        val notFollowedBackJson = prefs.getString(KEY_NOT_FOLLOWED_BACK, null) ?: return null

        val listType = object : TypeToken<List<InstagramAccount>>() {}.type

        return try {
            AnalysisResult(
                followers = gson.fromJson(followersJson, listType),
                following = gson.fromJson(followingJson, listType),
                notFollowingBack = gson.fromJson(notFollowingBackJson, listType),
                notFollowedBack = gson.fromJson(notFollowedBackJson, listType)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getLastUpdated(): Long {
        return prefs.getLong(KEY_LAST_UPDATED, 0L)
    }

    fun hasStoredData(): Boolean {
        return prefs.getString(KEY_FOLLOWERS, null) != null
    }

    fun clearData() {
        prefs.edit().clear().apply()
    }

    // 삭제된(언팔로우한) 계정 목록 관리
    fun addRemovedAccounts(usernames: List<String>) {
        val existing = getRemovedAccounts().toMutableSet()
        existing.addAll(usernames.map { it.lowercase() })
        prefs.edit()
            .putString(KEY_REMOVED_ACCOUNTS, gson.toJson(existing.toList()))
            .apply()
    }

    fun getRemovedAccounts(): Set<String> {
        val json = prefs.getString(KEY_REMOVED_ACCOUNTS, null) ?: return emptySet()
        return try {
            val listType = object : TypeToken<List<String>>() {}.type
            val list: List<String> = gson.fromJson(json, listType)
            list.map { it.lowercase() }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun clearRemovedAccounts() {
        prefs.edit().remove(KEY_REMOVED_ACCOUNTS).apply()
    }

    // following 파일 URI 저장/불러오기 (원본 파일 수정용)
    fun saveFollowingFileUri(uri: String) {
        prefs.edit().putString(KEY_FOLLOWING_FILE_URI, uri).apply()
    }

    fun getFollowingFileUri(): String? {
        return prefs.getString(KEY_FOLLOWING_FILE_URI, null)
    }

    fun clearFollowingFileUri() {
        prefs.edit().remove(KEY_FOLLOWING_FILE_URI).apply()
    }
}
