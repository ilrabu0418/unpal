package com.unpal.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

object AccessibilityUtil {

    /**
     * 접근성 서비스가 활성화되어 있는지 확인
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${serviceClass.canonicalName}"

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any { componentName ->
            componentName.equals(expectedComponentName, ignoreCase = true)
        }
    }

    /**
     * 접근성 설정 화면 열기
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Instagram 앱이 설치되어 있는지 확인
     */
    fun isInstagramInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.instagram.android", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Instagram 앱 열기
     */
    fun openInstagram(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage("com.instagram.android")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Instagram 프로필 페이지를 기본 브라우저에서 열기
     */
    fun openInstagramProfile(context: Context, username: String) {
        val browserPackage = findBrowserPackage(context)

        val url = "https://www.instagram.com/$username"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (browserPackage != null) {
                setPackage(browserPackage)
            } else {
                // 브라우저 패키지를 못 찾은 경우 selector로 강제 브라우저 열기
                val selector = Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    data = Uri.parse("https://")
                }
                this.selector = selector
            }
        }
        context.startActivity(intent)
    }

    /**
     * 인스타그램이 아닌 브라우저 패키지 찾기
     */
    private fun findBrowserPackage(context: Context): String? {
        val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
        val activities = context.packageManager.queryIntentActivities(testIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val browser = activities.firstOrNull {
            it.activityInfo.packageName != "com.instagram.android" &&
            it.activityInfo.packageName != "android"
        }
        if (browser != null) return browser.activityInfo.packageName

        val commonBrowsers = listOf(
            "com.android.chrome",
            "com.sec.android.app.sbrowser",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.brave.browser",
            "com.microsoft.emmx"
        )
        for (pkg in commonBrowsers) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) { }
        }

        return null
    }
}
