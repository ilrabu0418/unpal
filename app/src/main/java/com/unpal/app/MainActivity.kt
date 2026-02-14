package com.unpal.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.unpal.app.ui.screens.AnalysisScreen
import com.unpal.app.ui.screens.MainScreen
import com.unpal.app.ui.screens.WebViewLoginScreen
import com.unpal.app.ui.screens.WebViewUnfollowScreen
import com.unpal.app.ui.theme.UnpalTheme
import com.unpal.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val navigateTo = intent?.getStringExtra("navigate_to")

        setContent {
            UnpalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UnpalApp(initialDestination = navigateTo)
                }
            }
        }
    }
}

@Composable
fun UnpalApp(initialDestination: String? = null) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Check accessibility service status on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Will be updated by the composable context
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 특정 목적지가 지정되면 해당 화면으로 이동, 아니면 메인으로
    LaunchedEffect(Unit) {
        if (initialDestination == "analysis") {
            // 분석 화면으로 이동 (언팔로우 완료 후 복귀)
            navController.navigate("analysis") {
                launchSingleTop = true
            }
        } else {
            navController.popBackStack("main", inclusive = false)
        }
    }

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            AccessibilityCheckEffect(viewModel)
            MainScreen(
                viewModel = viewModel,
                onNavigateToAnalysis = {
                    navController.navigate("analysis") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("analysis") {
            AccessibilityCheckEffect(viewModel)
            AnalysisScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToLogin = {
                    navController.navigate("webview_login") {
                        launchSingleTop = true
                    }
                },
                onNavigateToWebViewUnfollow = {
                    navController.navigate("webview_unfollow") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("webview_login") {
            WebViewLoginScreen(
                onLoginComplete = {
                    navController.popBackStack()
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable("webview_unfollow") {
            WebViewUnfollowScreen(
                viewModel = viewModel,
                onComplete = {
                    navController.popBackStack()
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
private fun AccessibilityCheckEffect(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val isEnabled = isAccessibilityServiceEnabled(context)
                viewModel.updateAccessibilityStatus(isEnabled)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        val isEnabled = isAccessibilityServiceEnabled(context)
        viewModel.updateAccessibilityStatus(isEnabled)
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )

    return enabledServices.any { serviceInfo ->
        serviceInfo.resolveInfo.serviceInfo.packageName == context.packageName
    }
}
