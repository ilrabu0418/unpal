package com.unpal.app.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unpal.app.data.AnalysisStorage
import com.unpal.app.service.UnfollowAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class InstagramAccount(
    val username: String,
    val profileUrl: String = "",
    val profilePicUrl: String = "",
    val userId: String = "",
    val timestamp: Long = 0L,
    var isSelected: Boolean = false
)

data class AnalysisResult(
    val followers: List<InstagramAccount>,
    val following: List<InstagramAccount>,
    val notFollowingBack: List<InstagramAccount>,
    val notFollowedBack: List<InstagramAccount>
)

sealed class UiState {
    object Initial : UiState()
    object Loading : UiState()
    data class Success(val result: AnalysisResult) : UiState()
    data class Error(val message: String) : UiState()
}

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedAccounts = MutableStateFlow<Set<String>>(emptySet())
    val selectedAccounts: StateFlow<Set<String>> = _selectedAccounts.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _isUnfollowInProgress = MutableStateFlow(false)
    val isUnfollowInProgress: StateFlow<Boolean> = _isUnfollowInProgress.asStateFlow()

    private val _unfollowProgress = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0))
    val unfollowProgress: StateFlow<Pair<Int, Int>> = _unfollowProgress.asStateFlow()

    private val _lastUpdated = MutableStateFlow(0L)
    val lastUpdated: StateFlow<Long> = _lastUpdated.asStateFlow()

    // 새 분석 완료 시 자동 네비게이션 플래그
    private val _shouldNavigateToAnalysis = MutableStateFlow(false)
    val shouldNavigateToAnalysis: StateFlow<Boolean> = _shouldNavigateToAnalysis.asStateFlow()

    // following 파일 URI (원본 파일 수정용)
    private var followingFileUri: Uri? = null
    private var appContext: Context? = null

    private var currentAnalysisResult: AnalysisResult? = null
    private var analysisStorage: AnalysisStorage? = null
    private var isInitialized = false

    // 앱 시작 시 저장된 분석 결과 복원
    fun initSession(context: Context) {
        if (isInitialized) return
        isInitialized = true

        appContext = context.applicationContext
        analysisStorage = AnalysisStorage(context)

        // 저장된 following 파일 URI 복원
        analysisStorage?.getFollowingFileUri()?.let { uriString ->
            try {
                followingFileUri = Uri.parse(uriString)
                Log.d("MainViewModel", "Restored following file URI: $uriString")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to restore URI: ${e.message}")
            }
        }

        loadSavedAnalysis()
    }

    private fun loadSavedAnalysis() {
        analysisStorage?.getAnalysisResult()?.let { result ->
            currentAnalysisResult = result
            _uiState.value = UiState.Success(result)
            _lastUpdated.value = analysisStorage?.getLastUpdated() ?: 0L
        }
    }

    fun hasSavedAnalysis(): Boolean {
        return analysisStorage?.hasStoredData() == true
    }

    // 접근성 서비스로 언팔로우 시작
    fun startAccessibilityUnfollow(context: Context) {
        val selected = _selectedAccounts.value.toList()
        if (selected.isEmpty()) return

        _isUnfollowInProgress.value = true
        _unfollowProgress.value = Pair(0, selected.size)

        // 접근성 서비스에 언팔로우 요청 전송
        val intent = android.content.Intent("com.unpal.app.START_UNFOLLOW").apply {
            setPackage(context.packageName)
            putStringArrayListExtra("usernames", ArrayList(selected))
        }
        context.sendBroadcast(intent)
    }

    // 접근성 서비스 언팔로우 완료 처리
    fun onAccessibilityUnfollowComplete(success: Boolean, completedCount: Int) {
        _isUnfollowInProgress.value = false
        _unfollowProgress.value = Pair(0, 0)
    }

    // 접근성 서비스 진행 상황 업데이트
    fun updateUnfollowProgress(current: Int, total: Int) {
        _unfollowProgress.value = Pair(current, total)
    }

    private fun calculateAnalysisResult(
        followers: List<InstagramAccount>,
        following: List<InstagramAccount>
    ): AnalysisResult {
        val followerUsernames = followers.map { it.username.lowercase() }.toSet()
        val followingUsernames = following.map { it.username.lowercase() }.toSet()

        val notFollowingBack = following.filter {
            !followerUsernames.contains(it.username.lowercase())
        }

        val notFollowedBack = followers.filter {
            !followingUsernames.contains(it.username.lowercase())
        }

        return AnalysisResult(
            followers = followers,
            following = following,
            notFollowingBack = notFollowingBack,
            notFollowedBack = notFollowedBack
        )
    }

    fun toggleAccountSelection(username: String) {
        _selectedAccounts.value = _selectedAccounts.value.toMutableSet().apply {
            if (contains(username)) {
                remove(username)
            } else {
                add(username)
            }
        }
    }

    fun selectAllAccounts() {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            _selectedAccounts.value = currentState.result.notFollowingBack.map { it.username }.toSet()
        }
    }

    fun deselectAllAccounts() {
        _selectedAccounts.value = emptySet()
    }

    fun updateAccessibilityStatus(isEnabled: Boolean) {
        _isAccessibilityEnabled.value = isEnabled
    }

    fun startUnfollow() {
        if (_selectedAccounts.value.isEmpty()) return
        _isUnfollowInProgress.value = true
    }

    fun stopUnfollow() {
        _isUnfollowInProgress.value = false
        _unfollowProgress.value = Pair(0, 0)

        // 접근성 서비스도 강제 중지
        UnfollowAccessibilityService.forceStopAndClear()
    }

    // 취소 버튼 클릭 시 호출 - 서비스 중지 및 상태 초기화
    fun cancelUnfollow(context: Context) {
        // 브로드캐스트로 서비스에 중지 요청
        val intent = android.content.Intent("com.unpal.app.STOP_UNFOLLOW").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)

        // 강제로 상태 초기화
        UnfollowAccessibilityService.forceStopAndClear()

        _isUnfollowInProgress.value = false
        _unfollowProgress.value = Pair(0, 0)
    }

    // 앱으로 돌아왔을 때 접근성 서비스의 현재 상태 동기화
    fun syncUnfollowProgress() {
        val progress = UnfollowAccessibilityService.getCurrentProgress()

        if (progress == null) {
            // 서비스가 없으면 진행 중이던 작업 완료 처리
            if (_isUnfollowInProgress.value) {
                _isUnfollowInProgress.value = false
                _unfollowProgress.value = Pair(0, 0)
            }
            return
        }

        if (progress.isRunning) {
            // 아직 진행 중
            _isUnfollowInProgress.value = true
            _unfollowProgress.value = Pair(progress.currentIndex, progress.total)
        } else {
            // 완료됨
            _isUnfollowInProgress.value = false
            _unfollowProgress.value = Pair(progress.currentIndex, progress.total)
        }
    }

    fun getSelectedAccountsList(): List<String> {
        return _selectedAccounts.value.toList()
    }

    // 언팔로우 완료된 계정들을 목록에서 제거
    fun removeAccountsFromList(usernames: List<String>) {
        val currentResult = currentAnalysisResult ?: return
        val usernamesToRemove = usernames.map { it.lowercase() }.toSet()

        // notFollowingBack에서 제거
        val updatedNotFollowingBack = currentResult.notFollowingBack.filter {
            !usernamesToRemove.contains(it.username.lowercase())
        }

        // following에서도 제거 (실제로 언팔로우 했으므로)
        val updatedFollowing = currentResult.following.filter {
            !usernamesToRemove.contains(it.username.lowercase())
        }

        val updatedResult = currentResult.copy(
            following = updatedFollowing,
            notFollowingBack = updatedNotFollowingBack
        )

        currentAnalysisResult = updatedResult
        _uiState.value = UiState.Success(updatedResult)

        // 선택 목록 초기화
        _selectedAccounts.value = emptySet()

        // 저장
        analysisStorage?.saveAnalysisResult(updatedResult)

        // 원본 following 파일에서도 삭제
        viewModelScope.launch(Dispatchers.IO) {
            removeFromFollowingFile(usernamesToRemove)
        }
    }

    // 원본 following.json 파일에서 계정 삭제
    private fun removeFromFollowingFile(usernamesToRemove: Set<String>) {
        val uri = followingFileUri
        val context = appContext

        if (uri == null) {
            Log.e("MainViewModel", "removeFromFollowingFile: followingFileUri is null")
            return
        }
        if (context == null) {
            Log.e("MainViewModel", "removeFromFollowingFile: appContext is null")
            return
        }

        Log.d("MainViewModel", "Attempting to remove ${usernamesToRemove.size} accounts from file: $uri")
        Log.d("MainViewModel", "Usernames to remove: $usernamesToRemove")

        try {
            // 파일 읽기
            val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            }

            if (content == null) {
                Log.e("MainViewModel", "Failed to read file content")
                return
            }

            Log.d("MainViewModel", "File content length: ${content.length}")

            val json = org.json.JSONObject(content)

            // relationships_following 배열 찾기
            val keys = listOf("relationships_following", "following")
            var modified = false
            var totalRemoved = 0

            for (key in keys) {
                val array = json.optJSONArray(key) ?: continue
                Log.d("MainViewModel", "Found array '$key' with ${array.length()} items")

                // 삭제할 인덱스 찾기 (뒤에서부터 삭제해야 인덱스 꼬이지 않음)
                val indicesToRemove = mutableListOf<Int>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val title = item.optString("title", "").lowercase()

                    // string_list_data에서도 확인
                    val stringListData = item.optJSONArray("string_list_data")
                    var valueUsername = ""
                    if (stringListData != null && stringListData.length() > 0) {
                        val data = stringListData.optJSONObject(0)
                        valueUsername = data?.optString("value", "")?.lowercase() ?: ""
                    }

                    if (usernamesToRemove.contains(title) || usernamesToRemove.contains(valueUsername)) {
                        indicesToRemove.add(i)
                        Log.d("MainViewModel", "Marking for removal: title='$title', value='$valueUsername'")
                    }
                }

                // 뒤에서부터 삭제
                for (index in indicesToRemove.sortedDescending()) {
                    array.remove(index)
                    modified = true
                    totalRemoved++
                }
            }

            if (modified) {
                // 파일에 쓰기
                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                if (outputStream == null) {
                    Log.e("MainViewModel", "Failed to open output stream for writing")
                    return
                }
                outputStream.use { stream ->
                    stream.write(json.toString(2).toByteArray(Charsets.UTF_8))
                }
                Log.d("MainViewModel", "Successfully removed $totalRemoved accounts from following file")
            } else {
                Log.d("MainViewModel", "No accounts were modified in the file")
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to modify following file: ${e.message}", e)
        }
    }

    fun resetState() {
        _uiState.value = UiState.Initial
        _selectedAccounts.value = emptySet()
        _isUnfollowInProgress.value = false
        _shouldNavigateToAnalysis.value = false
    }

    fun onNavigatedToAnalysis() {
        _shouldNavigateToAnalysis.value = false
    }

    // Instagram 데이터 내보내기 파일(HTML/ZIP) 여러 개 처리
    fun processFiles(context: Context, uris: List<Uri>) {
        appContext = context.applicationContext
        if (analysisStorage == null) {
            analysisStorage = AnalysisStorage(context)
        }
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading

                val result = withContext(Dispatchers.IO) {
                    val allFollowers = mutableListOf<InstagramAccount>()
                    val allFollowing = mutableListOf<InstagramAccount>()

                    for (uri in uris) {
                        val contentResolver = context.contentResolver
                        val mimeType = contentResolver.getType(uri)
                        val fileName = getFileName(context, uri)
                        val lowerFileName = fileName?.lowercase() ?: ""

                        Log.d("MainViewModel", "Processing file: $fileName, mimeType: $mimeType")

                        // 파일 내용 읽기
                        val content = contentResolver.openInputStream(uri)?.use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
                        } ?: continue

                        // ZIP 파일 처리
                        if (mimeType == "application/zip" || lowerFileName.endsWith(".zip")) {
                            processZipFile(context, uri)?.let { zipResult ->
                                allFollowers.addAll(zipResult.followers)
                                allFollowing.addAll(zipResult.following)
                            }
                            continue
                        }

                        // JSON 또는 HTML 파싱 시도
                        val accounts = when {
                            mimeType == "application/json" || lowerFileName.endsWith(".json") -> {
                                parseJsonFile(content)
                            }
                            else -> {
                                // HTML로 시도, 실패하면 JSON으로도 시도
                                val htmlAccounts = parseHtmlFile(content)
                                if (htmlAccounts.isEmpty()) parseJsonFile(content) else htmlAccounts
                            }
                        }

                        Log.d("MainViewModel", "Parsed ${accounts.size} accounts from $fileName")

                        // 파일명으로 팔로워/팔로잉 구분
                        when {
                            lowerFileName.contains("follower") && !lowerFileName.contains("following") -> {
                                allFollowers.addAll(accounts)
                                Log.d("MainViewModel", "Added ${accounts.size} to followers")
                            }
                            lowerFileName.contains("following") -> {
                                allFollowing.addAll(accounts)
                                // following 파일 URI 저장 (원본 파일 수정용)
                                followingFileUri = uri
                                // 저장소에도 URI 저장 (앱 재시작 시 복원용)
                                analysisStorage?.saveFollowingFileUri(uri.toString())
                                // 쓰기 권한 유지 요청
                                try {
                                    context.contentResolver.takePersistableUriPermission(
                                        uri,
                                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    )
                                } catch (e: Exception) {
                                    Log.w("MainViewModel", "Could not take persistable permission: ${e.message}")
                                }
                                Log.d("MainViewModel", "Added ${accounts.size} to following, saved URI: $uri")
                            }
                            else -> {
                                // 파일명으로 구분 불가 시 팔로잉으로 처리
                                allFollowing.addAll(accounts)
                                Log.d("MainViewModel", "Added ${accounts.size} to following (default)")
                            }
                        }
                    }

                    if (allFollowers.isNotEmpty() || allFollowing.isNotEmpty()) {
                        calculateAnalysisResult(
                            allFollowers.distinctBy { it.username.lowercase() },
                            allFollowing.distinctBy { it.username.lowercase() }
                        )
                    } else {
                        null
                    }
                }

                if (result != null) {
                    currentAnalysisResult = result
                    analysisStorage?.saveAnalysisResult(result)
                    _lastUpdated.value = System.currentTimeMillis()
                    _uiState.value = UiState.Success(result)
                    _shouldNavigateToAnalysis.value = true
                } else {
                    _uiState.value = UiState.Error("파일에서 Instagram 데이터를 찾을 수 없습니다.")
                }

            } catch (e: Exception) {
                Log.e("MainViewModel", "processFiles error", e)
                _uiState.value = UiState.Error("파일 처리 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    private fun processZipFile(context: Context, uri: Uri): AnalysisResult? {
        val followers = mutableListOf<InstagramAccount>()
        val following = mutableListOf<InstagramAccount>()

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipInputStream ->
                var entry = zipInputStream.nextEntry
                while (entry != null) {
                    val entryName = entry.name.lowercase()

                    // followers_and_following 폴더 안의 파일들 처리
                    if (entryName.contains("followers_and_following") ||
                        entryName.contains("followers") ||
                        entryName.contains("following")) {

                        if (entryName.endsWith(".html")) {
                            val content = BufferedReader(InputStreamReader(zipInputStream)).readText()
                            val accounts = parseHtmlFile(content)

                            val fileName = entry.name.substringAfterLast("/").lowercase()
                            when {
                                fileName.startsWith("followers") -> followers.addAll(accounts)
                                fileName.startsWith("following") -> following.addAll(accounts)
                            }
                        }
                    }

                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
        }

        return if (followers.isNotEmpty() || following.isNotEmpty()) {
            calculateAnalysisResult(followers, following)
        } else {
            null
        }
    }

    private fun parseHtmlFile(content: String): List<InstagramAccount> {
        val accounts = mutableListOf<InstagramAccount>()

        // 여러 패턴 시도 (following은 /_u/ 경로 사용)
        val patterns = listOf(
            // 패턴 1: instagram.com/_u/username (following 형식)
            Regex("""instagram\.com/_u/([a-zA-Z0-9._]+)"""),
            // 패턴 2: href="https://www.instagram.com/username" (followers 형식)
            Regex("""href="https?://(www\.)?instagram\.com/([^"/_]+)"""),
            // 패턴 3: instagram.com/username 일반 형식
            Regex("""instagram\.com/([a-zA-Z0-9._]+)""")
        )

        for (pattern in patterns) {
            pattern.findAll(content).forEach { match ->
                // 마지막 캡처 그룹에서 username 추출
                val username = match.groupValues.lastOrNull {
                    it.isNotBlank() &&
                    !it.startsWith("www") &&
                    !it.startsWith("_u") &&
                    it.length > 1
                } ?: return@forEach

                if (username.isNotBlank() &&
                    !username.contains("/") &&
                    !username.equals("www", ignoreCase = true) &&
                    !username.equals("_u", ignoreCase = true)) {
                    accounts.add(
                        InstagramAccount(
                            username = username,
                            profileUrl = "https://instagram.com/$username"
                        )
                    )
                }
            }
            if (accounts.isNotEmpty()) break
        }

        Log.d("MainViewModel", "parseHtmlFile found ${accounts.size} accounts")
        return accounts.distinctBy { it.username.lowercase() }
    }

    private fun parseJsonFile(content: String): List<InstagramAccount> {
        val accounts = mutableListOf<InstagramAccount>()
        try {
            val json = org.json.JSONTokener(content).nextValue()

            when (json) {
                is org.json.JSONArray -> {
                    // followers_1.json 형식: [{"string_list_data": [{"value": "username", ...}]}]
                    for (i in 0 until json.length()) {
                        val item = json.optJSONObject(i) ?: continue
                        val stringListData = item.optJSONArray("string_list_data") ?: continue
                        for (j in 0 until stringListData.length()) {
                            val data = stringListData.optJSONObject(j) ?: continue
                            val username = data.optString("value", "")
                            val href = data.optString("href", "")
                            val timestamp = data.optLong("timestamp", 0L)
                            if (username.isNotBlank()) {
                                accounts.add(
                                    InstagramAccount(
                                        username = username,
                                        profileUrl = href.ifBlank { "https://instagram.com/$username" },
                                        timestamp = timestamp * 1000 // 초 -> 밀리초
                                    )
                                )
                            }
                        }
                    }
                }
                is org.json.JSONObject -> {
                    // following.json 형식: {"relationships_following": [{"title": "username", ...}]}
                    val keys = listOf("relationships_following", "relationships_followers", "followers", "following")
                    for (key in keys) {
                        val array = json.optJSONArray(key) ?: continue
                        for (i in 0 until array.length()) {
                            val item = array.optJSONObject(i) ?: continue

                            // title에서 username 가져오기 (following.json 형식)
                            val titleUsername = item.optString("title", "")

                            val stringListData = item.optJSONArray("string_list_data")
                            var username = ""
                            var href = ""
                            var timestamp = 0L

                            if (stringListData != null && stringListData.length() > 0) {
                                val data = stringListData.optJSONObject(0)
                                if (data != null) {
                                    // value가 있으면 value 사용, 없으면 title 사용
                                    username = data.optString("value", "").ifBlank { titleUsername }
                                    href = data.optString("href", "")
                                    timestamp = data.optLong("timestamp", 0L)
                                }
                            }

                            // username이 여전히 비어있으면 title 사용
                            if (username.isBlank()) {
                                username = titleUsername
                            }

                            if (username.isNotBlank()) {
                                accounts.add(
                                    InstagramAccount(
                                        username = username,
                                        profileUrl = href.ifBlank { "https://instagram.com/$username" },
                                        timestamp = timestamp * 1000
                                    )
                                )
                            }
                        }
                        if (accounts.isNotEmpty()) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "parseJsonFile error", e)
        }
        Log.d("MainViewModel", "parseJsonFile found ${accounts.size} accounts")
        return accounts.distinctBy { it.username.lowercase() }
    }
}
