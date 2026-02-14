package com.unpal.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UnfollowProgress(
    val isRunning: Boolean = false,
    val currentUser: String = "",
    val progress: Int = 0,
    val total: Int = 0,
    val isComplete: Boolean = false,
    val success: Boolean = false
)

class UnfollowManager(private val context: Context) {

    private val _progressState = MutableStateFlow(UnfollowProgress())
    val progressState: StateFlow<UnfollowProgress> = _progressState.asStateFlow()

    private var isReceiverRegistered = false

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UnfollowAccessibilityService.ACTION_PROGRESS_UPDATE -> {
                    val currentUser = intent.getStringExtra(UnfollowAccessibilityService.EXTRA_CURRENT_USER) ?: ""
                    val progress = intent.getIntExtra(UnfollowAccessibilityService.EXTRA_PROGRESS, 0)
                    val total = intent.getIntExtra(UnfollowAccessibilityService.EXTRA_TOTAL, 0)

                    _progressState.value = UnfollowProgress(
                        isRunning = true,
                        currentUser = currentUser,
                        progress = progress,
                        total = total,
                        isComplete = false
                    )
                }
                UnfollowAccessibilityService.ACTION_UNFOLLOW_COMPLETE -> {
                    val success = intent.getBooleanExtra(UnfollowAccessibilityService.EXTRA_SUCCESS, false)
                    val progress = intent.getIntExtra(UnfollowAccessibilityService.EXTRA_PROGRESS, 0)
                    val total = intent.getIntExtra(UnfollowAccessibilityService.EXTRA_TOTAL, 0)

                    _progressState.value = UnfollowProgress(
                        isRunning = false,
                        progress = progress,
                        total = total,
                        isComplete = true,
                        success = success
                    )
                }
            }
        }
    }

    fun registerReceiver() {
        if (isReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(UnfollowAccessibilityService.ACTION_PROGRESS_UPDATE)
            addAction(UnfollowAccessibilityService.ACTION_UNFOLLOW_COMPLETE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(progressReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(progressReceiver, filter)
        }
        isReceiverRegistered = true
    }

    fun unregisterReceiver() {
        if (!isReceiverRegistered) return

        try {
            context.unregisterReceiver(progressReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        isReceiverRegistered = false
    }

    fun startUnfollow(usernames: List<String>) {
        if (usernames.isEmpty()) return

        _progressState.value = UnfollowProgress(
            isRunning = true,
            total = usernames.size
        )

        val intent = Intent(UnfollowAccessibilityService.ACTION_START_UNFOLLOW).apply {
            setPackage(context.packageName)
            putStringArrayListExtra(
                UnfollowAccessibilityService.EXTRA_USERNAMES,
                ArrayList(usernames)
            )
        }
        context.sendBroadcast(intent)
    }

    fun stopUnfollow() {
        val intent = Intent(UnfollowAccessibilityService.ACTION_STOP_UNFOLLOW).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    fun resetProgress() {
        _progressState.value = UnfollowProgress()
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        return UnfollowAccessibilityService.isServiceEnabled(context)
    }

    // 앱으로 돌아왔을 때 현재 진행 상태 동기화
    fun syncProgressFromService() {
        val progress = UnfollowAccessibilityService.getCurrentProgress()

        if (progress == null) {
            // 서비스가 없으면 완료 처리
            if (_progressState.value.isRunning) {
                _progressState.value = UnfollowProgress(
                    isRunning = false,
                    isComplete = true,
                    success = true,
                    progress = _progressState.value.progress,
                    total = _progressState.value.total
                )
            }
            return
        }

        if (progress.isRunning) {
            // 아직 진행 중
            _progressState.value = UnfollowProgress(
                isRunning = true,
                currentUser = progress.currentUser,
                progress = progress.currentIndex,
                total = progress.total,
                isComplete = false
            )
        } else {
            // 완료됨
            _progressState.value = UnfollowProgress(
                isRunning = false,
                progress = progress.currentIndex,
                total = progress.total,
                isComplete = true,
                success = progress.currentIndex >= progress.total
            )
        }
    }
}
