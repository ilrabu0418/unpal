package com.unpal.app.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import kotlin.random.Random

class UnfollowAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UnpalService"

        const val ACTION_START_UNFOLLOW = "com.unpal.app.START_UNFOLLOW"
        const val ACTION_STOP_UNFOLLOW = "com.unpal.app.STOP_UNFOLLOW"
        const val ACTION_PROGRESS_UPDATE = "com.unpal.app.PROGRESS_UPDATE"
        const val ACTION_UNFOLLOW_COMPLETE = "com.unpal.app.UNFOLLOW_COMPLETE"

        const val EXTRA_USERNAMES = "usernames"
        const val EXTRA_CURRENT_USER = "current_user"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_SUCCESS = "success"

        private const val NOTIFICATION_CHANNEL_ID = "unpal_service_channel"
        private const val NOTIFICATION_ID = 1001

        var instance: UnfollowAccessibilityService? = null
            private set

        fun isServiceEnabled(context: Context): Boolean {
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(context.packageName) == true
        }

        fun getCurrentProgress(): ProgressInfo? {
            val service = instance ?: return null
            return ProgressInfo(
                isRunning = service.isProcessing,
                currentIndex = service.currentIndex,
                total = service.unfollowQueue.size,
                currentUser = if (service.currentIndex < service.unfollowQueue.size)
                    service.unfollowQueue[service.currentIndex] else ""
            )
        }

        var completedUsernames: List<String> = emptyList()
            private set

        fun clearCompletedUsernames() {
            completedUsernames = emptyList()
        }

        fun forceStopAndClear() {
            instance?.let { service ->
                service.isProcessing = false
                service.currentState = UnfollowState.IDLE
                service.handler.removeCallbacksAndMessages(null)
                service.unfollowQueue.clear()
                service.currentIndex = 0
                service.stateRetryCount = 0
            }
            completedUsernames = emptyList()
        }
    }

    data class ProgressInfo(
        val isRunning: Boolean,
        val currentIndex: Int,
        val total: Int,
        val currentUser: String
    )

    private val handler = Handler(Looper.getMainLooper())
    private var unfollowQueue = mutableListOf<String>()
    private var currentIndex = 0
    private var isProcessing = false
    private var currentState = UnfollowState.IDLE
    private var stateRetryCount = 0
    private val MAX_STATE_RETRIES = 12

    private enum class UnfollowState {
        IDLE,
        OPENING_PROFILE,
        CLICKING_FOLLOWING,
        WAITING_DIALOG,
        CLICKING_UNFOLLOW,
        WAITING_COMPLETE
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_START_UNFOLLOW -> {
                    val usernames = intent.getStringArrayListExtra(EXTRA_USERNAMES)
                    if (!usernames.isNullOrEmpty()) {
                        startUnfollowProcess(usernames)
                    }
                }
                ACTION_STOP_UNFOLLOW -> {
                    stopUnfollowProcess()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(ACTION_START_UNFOLLOW)
            addAction(ACTION_STOP_UNFOLLOW)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) { }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        showForegroundNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "UNPAL 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "언팔로우 자동화 서비스가 실행 중입니다"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showForegroundNotification() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("UNPAL 서비스 실행 중")
                .setContentText("접근성 서비스가 활성화되어 있습니다")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) { }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isProcessing) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                when (currentState) {
                    UnfollowState.CLICKING_FOLLOWING,
                    UnfollowState.CLICKING_UNFOLLOW -> {
                        handleStateTransition()
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onInterrupt() {
        stopUnfollowProcess()
    }

    // ===== 언팔로우 프로세스 =====

    private fun startUnfollowProcess(usernames: List<String>) {
        unfollowQueue = usernames.toMutableList()
        currentIndex = 0
        isProcessing = true
        currentState = UnfollowState.IDLE
        Log.d(TAG, "Starting unfollow for ${usernames.size} users: $usernames")
        processNextUser()
    }

    private fun stopUnfollowProcess() {
        isProcessing = false
        currentState = UnfollowState.IDLE
        handler.removeCallbacksAndMessages(null)
        val progress = currentIndex
        val total = unfollowQueue.size
        unfollowQueue.clear()
        currentIndex = 0
        stateRetryCount = 0

        sendBroadcast(Intent(ACTION_UNFOLLOW_COMPLETE).apply {
            setPackage(packageName)
            putExtra(EXTRA_SUCCESS, false)
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_TOTAL, total)
        })
    }

    private fun processNextUser() {
        if (!isProcessing || currentIndex >= unfollowQueue.size) {
            completedUsernames = unfollowQueue.toList()
            isProcessing = false
            sendBroadcast(Intent(ACTION_UNFOLLOW_COMPLETE).apply {
                setPackage(packageName)
                putExtra(EXTRA_SUCCESS, true)
                putExtra(EXTRA_PROGRESS, currentIndex)
                putExtra(EXTRA_TOTAL, unfollowQueue.size)
            })
            handler.postDelayed({ returnToApp() }, 1000)
            return
        }

        val username = unfollowQueue[currentIndex]
        Log.d(TAG, "Processing user ${currentIndex + 1}/${unfollowQueue.size}: $username")

        sendBroadcast(Intent(ACTION_PROGRESS_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_CURRENT_USER, username)
            putExtra(EXTRA_PROGRESS, currentIndex)
            putExtra(EXTRA_TOTAL, unfollowQueue.size)
        })

        currentState = UnfollowState.OPENING_PROFILE
        openProfileInBrowser(username)
    }

    // ===== 브라우저에서 프로필 열기 =====

    private fun findBrowserPackage(): String? {
        // 1. 인스타그램 URL을 처리할 수 있는 앱 중 인스타그램이 아닌 브라우저 찾기
        val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
        val activities = packageManager.queryIntentActivities(testIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val browser = activities.firstOrNull {
            it.activityInfo.packageName != "com.instagram.android" &&
            it.activityInfo.packageName != "android"
        }
        if (browser != null) return browser.activityInfo.packageName

        // 2. 흔한 브라우저 패키지 직접 시도
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
                packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) { }
        }

        return null
    }

    private fun openProfileInBrowser(username: String) {
        stateRetryCount = 0

        val browserPackage = findBrowserPackage()
        Log.d(TAG, "Browser package: $browserPackage")

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

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open browser for $username", e)
            onUnfollowComplete()
            return
        }

        currentState = UnfollowState.CLICKING_FOLLOWING

        // 웹 페이지 로딩 대기 (앱보다 오래 걸림)
        handler.postDelayed({
            debugDumpTree()
            handleStateTransition()
        }, Random.nextLong(6000, 9000))
    }

    // ===== 상태 전환 =====

    private fun handleStateTransition() {
        if (!isProcessing) return

        when (currentState) {
            UnfollowState.CLICKING_FOLLOWING -> {
                handler.postDelayed({
                    if (currentState != UnfollowState.CLICKING_FOLLOWING) return@postDelayed
                    if (clickFollowingButton()) {
                        Log.d(TAG, "Following button clicked!")
                        stateRetryCount = 0
                        currentState = UnfollowState.WAITING_DIALOG
                        handler.postDelayed({
                            currentState = UnfollowState.CLICKING_UNFOLLOW
                            handleStateTransition()
                        }, Random.nextLong(1000, 2000))
                    } else {
                        retryOrSkipUser()
                    }
                }, Random.nextLong(300, 800))
            }

            UnfollowState.CLICKING_UNFOLLOW -> {
                handler.postDelayed({
                    if (currentState != UnfollowState.CLICKING_UNFOLLOW) return@postDelayed
                    if (clickUnfollowButton()) {
                        Log.d(TAG, "Unfollow button clicked!")
                        stateRetryCount = 0
                        currentState = UnfollowState.WAITING_COMPLETE
                        handler.postDelayed({
                            onUnfollowComplete()
                        }, Random.nextLong(500, 1500))
                    } else {
                        retryOrSkipUser()
                    }
                }, Random.nextLong(500, 1200))
            }

            else -> {}
        }
    }

    // ===== 버튼 클릭 =====

    private fun clickFollowingButton(): Boolean {
        Log.d(TAG, "clickFollowingButton: searching...")
        // 텍스트 "팔로잉" 또는 SVG aria-label "아래쪽 V자형 아이콘" 으로 검색
        return forceClickNode(
            textSearch = listOf("팔로잉", "팔로", "Following", "following"),
            descSearch = listOf("아래쪽 V자형 아이콘"),
            excludeTexts = listOf("팔로워", "followers", "팔로우 취소", "Unfollow")
        )
    }

    private fun clickUnfollowButton(): Boolean {
        Log.d(TAG, "clickUnfollowButton: searching...")
        return forceClickNode(
            textSearch = listOf("팔로우 취소", "Unfollow", "언팔로우", "unfollow"),
            descSearch = emptyList(),
            excludeTexts = emptyList()
        )
    }

    // ===== 유틸리티 =====

    private fun getAllRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            windows?.forEach { window ->
                window.root?.let { roots.add(it) }
            }
        } catch (_: Exception) { }
        if (roots.isEmpty()) {
            rootInActiveWindow?.let { roots.add(it) }
        }
        Log.d(TAG, "getAllRoots: found ${roots.size} roots")
        return roots
    }

    private fun debugDumpTree() {
        val roots = getAllRoots()
        if (roots.isEmpty()) {
            Log.d(TAG, "DUMP: NO roots available")
            return
        }
        for ((i, root) in roots.withIndex()) {
            Log.d(TAG, "DUMP: ===== Window $i =====")
            dumpNode(root, 0)
        }
        Log.d(TAG, "DUMP: ===== End =====")
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int) {
        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val cls = node.className?.toString() ?: ""
        val clickable = node.isClickable

        if (text.isNotEmpty() || desc.isNotEmpty() || clickable) {
            Log.d(TAG, "DUMP: ${indent}[$cls] text='$text' desc='$desc' click=$clickable")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNode(child, depth + 1)
        }
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) return parent
            parent = parent.parent
        }
        return null
    }

    /**
     * 웹 콘텐츠용 강제 클릭 - clickable 여부 무시하고 매칭되면 클릭 시도
     */
    private fun forceClickNode(
        textSearch: List<String>,
        descSearch: List<String>,
        excludeTexts: List<String>
    ): Boolean {
        val roots = getAllRoots()

        // 1단계: findAccessibilityNodeInfosByText 로 검색
        for (root in roots) {
            for (text in textSearch) {
                val nodes = root.findAccessibilityNodeInfosByText(text)
                Log.d(TAG, "forceClick: text '$text' found ${nodes.size} nodes")

                for (node in nodes) {
                    if (node.className?.toString()?.contains("EditText") == true) continue
                    val nodeText = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
                    if (excludeTexts.any { nodeText.contains(it, ignoreCase = true) }) continue

                    Log.d(TAG, "forceClick: match cls=${node.className} text='${node.text}' desc='${node.contentDescription}' click=${node.isClickable}")

                    // clickable이면 바로 클릭
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "forceClick: clicked directly!")
                        return true
                    }
                    // 부모 중 clickable 찾기
                    val clickableParent = findClickableParent(node)
                    if (clickableParent != null) {
                        clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "forceClick: clicked parent!")
                        return true
                    }
                    // 웹 콘텐츠: clickable 아니어도 강제 클릭 시도
                    Log.d(TAG, "forceClick: force clicking non-clickable node")
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }

        // 2단계: 수동 트리 순회 (text + contentDescription 모두 검색)
        Log.d(TAG, "forceClick: fallback traverse")
        for (root in roots) {
            val found = traverseAndClick(root, textSearch, descSearch, excludeTexts)
            if (found) return true
        }

        return false
    }

    private fun traverseAndClick(
        node: AccessibilityNodeInfo,
        textSearch: List<String>,
        descSearch: List<String>,
        excludeTexts: List<String>
    ): Boolean {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        if (node.className?.toString()?.contains("EditText") == true) {
            // skip EditText
        } else {
            val textMatch = textSearch.any { text.contains(it, ignoreCase = true) }
            val descMatch = descSearch.any { desc.contains(it, ignoreCase = true) }
            val excluded = excludeTexts.any { "$text $desc".contains(it, ignoreCase = true) }

            if ((textMatch || descMatch) && !excluded) {
                Log.d(TAG, "traverse: hit text='$text' desc='$desc' click=${node.isClickable}")

                // clickable이면 바로 클릭
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "traverse: clicked!")
                    return true
                }
                // 부모 중 clickable 찾기
                val parent = findClickableParent(node)
                if (parent != null) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "traverse: clicked parent!")
                    return true
                }
                // 강제 클릭
                Log.d(TAG, "traverse: force click!")
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (traverseAndClick(child, textSearch, descSearch, excludeTexts)) return true
        }
        return false
    }

    private fun retryOrSkipUser() {
        stateRetryCount++
        Log.d(TAG, "retryOrSkipUser: retry $stateRetryCount/$MAX_STATE_RETRIES for state $currentState")
        if (stateRetryCount >= MAX_STATE_RETRIES) {
            Log.d(TAG, "Skipping user: ${unfollowQueue.getOrNull(currentIndex)}")
            stateRetryCount = 0
            onUnfollowComplete()
        } else {
            // 3번째, 6번째 재시도에서 트리 덤프
            if (stateRetryCount == 3 || stateRetryCount == 6) {
                debugDumpTree()
            }
            handler.postDelayed({
                handleStateTransition()
            }, Random.nextLong(1500, 2500))
        }
    }

    // ===== 완료 처리 =====

    private fun onUnfollowComplete() {
        currentIndex++
        val delay = calculateHumanLikeDelay()
        handler.postDelayed({
            currentState = UnfollowState.IDLE
            processNextUser()
        }, delay)
    }

    private fun calculateHumanLikeDelay(): Long {
        var baseDelay = Random.nextLong(2000, 5000)
        if (Random.nextInt(100) < 15) {
            baseDelay = Random.nextLong(5000, 10000)
        }
        if (Random.nextInt(100) < 5) {
            baseDelay = Random.nextLong(10000, 15000)
        }
        if (currentIndex > 0 && currentIndex % 5 == 0) {
            baseDelay += Random.nextLong(2000, 5000)
        }
        if (currentIndex > 0 && currentIndex % 10 == 0) {
            baseDelay += Random.nextLong(3000, 7000)
        }
        return baseDelay
    }

    private fun returnToApp() {
        try {
            val intent = Intent(this, Class.forName("com.unpal.app.MainActivity")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("navigate_to", "analysis")
            }
            startActivity(intent)
        } catch (e: Exception) { }
    }
}
