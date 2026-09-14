package com.aiquota.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiquota.app.ui.components.OverviewCard
import com.aiquota.app.ui.components.PlatformCard

/**
 * Dashboard：全部启用平台的额度总览。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 额度管家", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = viewModel::refreshAll) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = if (uiState.isRefreshing) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onNavigateToAddAccount) {
                        Icon(Icons.Default.Add, contentDescription = "添加账号")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refreshAll,
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isEmpty) {
                EmptyDashboard(onNavigateToAddAccount = onNavigateToAddAccount)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "overview") { OverviewCard(overview = uiState.overview) }
                    items(uiState.cards, key = { it.account.id }) { card ->
                        PlatformCard(
                            card = card,
                            onClick = { onNavigateToDetail(card.account.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDashboard(onNavigateToAddAccount: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "还没有追踪任何 AI 额度",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "点击右下角 + 添加你的 ChatGPT、GLM、MiniMax 等订阅，\n统一查看剩余额度。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            androidx.compose.material3.Button(onClick = onNavigateToAddAccount) {
                Icon(Icons.Default.Add, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 4.dp))
                Text("添加平台")
            }
        }
    }
}