package com.unpal.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.unpal.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewUnfollowScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val usernames = remember { viewModel.getSelectedAccountsList() }
    var currentIndex by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("로그인 확인 중...") }
    var needsLogin by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isComplete by remember { mutableStateOf(false) }
    var completedList by remember { mutableStateOf(listOf<String>()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }
    var currentPageUrl by remember { mutableStateOf("") }

    // 쿠키 영구 저장
    LaunchedEffect(Unit) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            flush()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("언팔로우 진행", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isProcessing) {
                            isProcessing = false
                        }
                        onCancel()
                    }) {
                        Icon(Icons.Default.Close, "취소")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 진행 상태
            if (isProcessing && usernames.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = currentIndex.toFloat() / usernames.size.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${currentIndex} / ${usernames.size}명 완료",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 로그인 필요 메시지
            if (needsLogin) {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "인스타그램 로그인이 필요합니다",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "아래에서 로그인하면 자동으로 시작됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        // 데스크톱 Chrome User-Agent (모바일 WebView 감지 우회)
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

                        // X-Requested-With 헤더 제거 (Instagram WebView 감지 우회)
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                        }

                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webChromeClient = WebChromeClient()

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                currentPageUrl = url ?: ""
                                pageLoaded = true

                                // 쿠키 저장
                                CookieManager.getInstance().flush()

                                if (url?.contains("/accounts/login") == true || url?.contains("/challenge/") == true) {
                                    needsLogin = true
                                } else if (needsLogin) {
                                    // 로그인 완료됨
                                    needsLogin = false
                                    if (!isProcessing && !isComplete) {
                                        isProcessing = true
                                    }
                                }
                            }
                        }

                        webViewRef = this

                        if (usernames.isNotEmpty()) {
                            loadUrl("https://www.instagram.com/${usernames[0]}")
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }

    // 첫 페이지 로드 후 자동 시작
    LaunchedEffect(pageLoaded) {
        if (pageLoaded && !isProcessing && !isComplete && !needsLogin &&
            !currentPageUrl.contains("/accounts/login") && !currentPageUrl.contains("/challenge/")) {
            delay(1000)
            isProcessing = true
        }
    }

    // 언팔로우 처리 코루틴
    LaunchedEffect(isProcessing) {
        if (!isProcessing) return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect
        if (usernames.isEmpty()) return@LaunchedEffect

        val completed = mutableListOf<String>()

        for (i in usernames.indices) {
            if (!isProcessing) break

            currentIndex = i
            val username = usernames[i]

            // 프로필 로딩
            statusText = "프로필 로딩: @$username"
            pageLoaded = false
            withContext(Dispatchers.Main) {
                wv.loadUrl("https://www.instagram.com/$username")
            }

            // 페이지 로드 대기 (최대 15초)
            var waited = 0
            while (!pageLoaded && waited < 15000) {
                delay(300)
                waited += 300
            }

            // 로그인 필요시 중단
            if (currentPageUrl.contains("/accounts/login") || currentPageUrl.contains("/challenge/")) {
                needsLogin = true
                isProcessing = false
                return@LaunchedEffect
            }

            // 동적 콘텐츠 렌더링 대기
            delay(3000)

            // 팔로잉 버튼 클릭
            statusText = "팔로잉 버튼 클릭: @$username"
            var followClicked = false
            for (retry in 0 until 5) {
                val result = evalJS(wv, JS_CLICK_FOLLOWING)
                if (result == "clicked") {
                    followClicked = true
                    break
                }
                delay(1500)
            }

            if (followClicked) {
                delay(2000)

                // 팔로우 취소 버튼 클릭
                statusText = "팔로우 취소: @$username"
                for (retry in 0 until 5) {
                    val result = evalJS(wv, JS_CLICK_UNFOLLOW)
                    if (result == "clicked") {
                        completed.add(username)
                        statusText = "완료: @$username"
                        break
                    }
                    delay(1500)
                }
            } else {
                statusText = "건너뜀: @$username"
            }

            // 다음 유저 전 대기
            delay((2000L..4000L).random())
        }

        completedList = completed
        currentIndex = usernames.size
        isComplete = true
        isProcessing = false
        statusText = "완료: ${completed.size}/${usernames.size}명 언팔로우"
    }

    // 완료 다이얼로그
    if (isComplete) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("언팔로우 완료") },
            text = {
                Text("${completedList.size}/${usernames.size}명 언팔로우 완료")
            },
            confirmButton = {
                Button(onClick = {
                    if (completedList.isNotEmpty()) {
                        viewModel.removeAccountsFromList(completedList)
                    }
                    onComplete()
                }) {
                    Text("확인")
                }
            }
        )
    }
}

private suspend fun evalJS(webView: WebView, js: String): String {
    return withContext(Dispatchers.Main) {
        suspendCoroutine { continuation ->
            webView.evaluateJavascript(js) { result ->
                continuation.resume(result?.trim('"') ?: "error")
            }
        }
    }
}

private const val JS_CLICK_FOLLOWING = """
(function() {
    var buttons = document.querySelectorAll('button');
    for (var i = 0; i < buttons.length; i++) {
        var text = buttons[i].textContent;
        if ((text.includes('팔로잉') || text.includes('Following')) &&
            !text.includes('팔로워') && !text.includes('followers') &&
            !text.includes('팔로우 취소') && !text.includes('Unfollow')) {
            buttons[i].click();
            return 'clicked';
        }
    }
    return 'not_found';
})()
"""

private const val JS_CLICK_UNFOLLOW = """
(function() {
    var all = document.querySelectorAll('button, [role="button"], [role="menuitem"]');
    for (var i = 0; i < all.length; i++) {
        var text = all[i].textContent.trim();
        if (text.includes('팔로우 취소') || text === 'Unfollow') {
            all[i].click();
            return 'clicked';
        }
    }
    return 'not_found';
})()
"""
