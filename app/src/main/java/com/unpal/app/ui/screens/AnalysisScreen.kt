package com.unpal.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unpal.app.ui.theme.InstagramOrange
import com.unpal.app.util.AccessibilityUtil
import com.unpal.app.ui.theme.InstagramPink
import com.unpal.app.ui.theme.InstagramPurple
import com.unpal.app.ui.theme.SuccessGreen
import com.unpal.app.viewmodel.InstagramAccount
import com.unpal.app.viewmodel.MainViewModel
import com.unpal.app.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToWebViewUnfollow: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedAccounts by viewModel.selectedAccounts.collectAsState()

    var showConfirmDialog by remember { mutableStateOf(false) }
    var showRemoveFromListDialog by remember { mutableStateOf(false) }
    var showDeletedResultDialog by remember { mutableStateOf(false) }
    var removeTargetUsername by remember { mutableStateOf<String?>(null) }
    var deletedUsernames by remember { mutableStateOf<List<String>>(emptyList()) }

    val analysisResult = (uiState as? UiState.Success)?.result

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "분석 결과",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetState()
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로 가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (analysisResult != null && analysisResult.notFollowingBack.isNotEmpty()) {
                BottomActionBar(
                    selectedCount = selectedAccounts.size,
                    totalCount = analysisResult.notFollowingBack.size,
                    onSelectAll = { viewModel.selectAllAccounts() },
                    onDeselectAll = { viewModel.deselectAllAccounts() },
                    onUnfollow = {
                        if (selectedAccounts.isNotEmpty()) {
                            showConfirmDialog = true
                        }
                    },
                    isUnfollowEnabled = selectedAccounts.isNotEmpty()
                )
            }
        }
    ) { paddingValues ->
        if (analysisResult == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "데이터를 불러올 수 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Statistics Cards
                item {
                    StatisticsSection(
                        followersCount = analysisResult.followers.size,
                        followingCount = analysisResult.following.size,
                        notFollowingBackCount = analysisResult.notFollowingBack.size
                    )
                }

                // 인스타그램 로그인 버튼
                if (analysisResult.notFollowingBack.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = onNavigateToLogin,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("인스타그램 로그인 (언팔로우 전 필수)")
                        }
                    }
                }

                // Section Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "맞팔로우하지 않는 계정",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${selectedAccounts.size}/${analysisResult.notFollowingBack.size}명 선택",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Account List
                if (analysisResult.notFollowingBack.isEmpty()) {
                    item {
                        EmptyStateCard()
                    }
                } else {
                    items(
                        items = analysisResult.notFollowingBack,
                        key = { it.username }
                    ) { account ->
                        AccountListItem(
                            account = account,
                            isSelected = selectedAccounts.contains(account.username),
                            onToggleSelection = { viewModel.toggleAccountSelection(account.username) },
                            onOpenProfile = {
                                AccessibilityUtil.openInstagramProfile(context, account.username)
                            },
                            onRemoveFromList = {
                                removeTargetUsername = account.username
                                showRemoveFromListDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Confirm Unfollow Dialog (WebView 방식)
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.PersonRemove,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("언팔로우 확인")
            },
            text = {
                Text("선택한 ${selectedAccounts.size}개의 계정을 언팔로우하시겠습니까?\n\n앱 내 웹뷰에서 자동으로 실행됩니다.\n(처음 사용 시 인스타그램 로그인이 필요합니다)")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onNavigateToWebViewUnfollow()
                    }
                ) {
                    Text("언팔로우 시작")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // 개별 계정 목록에서 삭제 확인 다이얼로그
    if (showRemoveFromListDialog && removeTargetUsername != null) {
        AlertDialog(
            onDismissRequest = {
                showRemoveFromListDialog = false
                removeTargetUsername = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("목록에서 삭제")
            },
            text = {
                Text("@${removeTargetUsername}을(를) 목록에서 삭제하시겠습니까?\n\n이미 직접 언팔로우한 경우 삭제하세요.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val username = removeTargetUsername!!
                        deletedUsernames = listOf(username)
                        viewModel.removeAccountsFromList(listOf(username))
                        showRemoveFromListDialog = false
                        removeTargetUsername = null
                        showDeletedResultDialog = true
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRemoveFromListDialog = false
                        removeTargetUsername = null
                    }
                ) {
                    Text("취소")
                }
            }
        )
    }

    // 삭제 완료 결과 다이얼로그
    if (showDeletedResultDialog && deletedUsernames.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                showDeletedResultDialog = false
                deletedUsernames = emptyList()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = SuccessGreen
                )
            },
            title = {
                Text("삭제 완료")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "다음 ${deletedUsernames.size}명이 목록에서 삭제되었습니다:",
                        fontWeight = FontWeight.Medium
                    )

                    // 삭제된 username 목록 (최대 10개까지 표시)
                    val displayList = if (deletedUsernames.size > 10) {
                        deletedUsernames.take(10)
                    } else {
                        deletedUsernames
                    }

                    displayList.forEach { username ->
                        Text(
                            text = "• @$username",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (deletedUsernames.size > 10) {
                        Text(
                            text = "... 외 ${deletedUsernames.size - 10}명",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeletedResultDialog = false
                        deletedUsernames = emptyList()
                    }
                ) {
                    Text("확인")
                }
            }
        )
    }
}

@Composable
private fun StatisticsSection(
    followersCount: Int,
    followingCount: Int,
    notFollowingBackCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Group,
            label = "팔로워",
            count = followersCount,
            color = SuccessGreen
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.PersonAdd,
            label = "팔로잉",
            count = followingCount,
            color = InstagramPurple
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.PersonRemove,
            label = "맞팔 X",
            count = notFollowingBackCount,
            color = InstagramPink
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccountListItem(
    account: InstagramAccount,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onOpenProfile: () -> Unit,
    onRemoveFromList: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelection() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Profile placeholder - 클릭 시 인스타그램 프로필 열기
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                InstagramPink,
                                InstagramPurple,
                                InstagramOrange
                            )
                        )
                    )
                    .clickable { onOpenProfile() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = account.username.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.surface
                )
            }

            // Username - 클릭 시 인스타그램 프로필 열기
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenProfile() }
            ) {
                Text(
                    text = "@${account.username}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "프로필 보기 →",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 목록에서 삭제 버튼
            IconButton(
                onClick = onRemoveFromList,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "목록에서 삭제",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Checkbox
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "축하합니다!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "팔로우하는 모든 분들이 맞팔로우 중입니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BottomActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onUnfollow: () -> Unit,
    isUnfollowEnabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Select All / Deselect All
            OutlinedButton(
                onClick = {
                    if (selectedCount == totalCount) {
                        onDeselectAll()
                    } else {
                        onSelectAll()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (selectedCount == totalCount) {
                        Icons.Default.Clear
                    } else {
                        Icons.Default.DoneAll
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (selectedCount == totalCount) "전체 해제" else "전체 선택",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // Unfollow Button
            Button(
                onClick = onUnfollow,
                enabled = isUnfollowEnabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = InstagramPink
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PersonRemove,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "언팔로우 ($selectedCount)",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
