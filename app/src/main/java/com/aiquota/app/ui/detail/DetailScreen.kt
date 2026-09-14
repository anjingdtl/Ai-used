package com.aiquota.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiquota.app.domain.model.ProviderState
import com.aiquota.app.ui.components.QuotaBucketRow
import com.aiquota.app.ui.components.label
import com.aiquota.app.ui.util.Format
import com.aiquota.app.ui.util.fallbackVisual
import com.aiquota.app.ui.util.quotaColorOf
import com.aiquota.app.ui.util.syncColor
import com.aiquota.app.ui.util.visual

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val account = ui.account
    val providerId = ui.providerId
    val visual = if (providerId != null) providerId.visual()
    else fallbackVisual(account?.providerId ?: "")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account?.displayName ?: "详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (account == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { DetailHeader(ui, visual.brandColor, visual.abbreviation) }

            val snapshot = ui.state.snapshot
            if (snapshot != null && snapshot.buckets.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("额度明细", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            snapshot.buckets.forEach { bucket ->
                                QuotaBucketRow(bucket = bucket)
                            }
                        }
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("最近变化", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (ui.history.isEmpty()) {
                            Text("暂无可用的历史记录", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            ui.history.asReversed().take(30).forEach { point ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(Format.fullTime(point.recordedAt), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline)
                                    Text(Format.percent(point.remainingPercent), style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = quotaColorOf(point.remainingPercent))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("启用此平台", style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = account.enabled, onCheckedChange = viewModel::setEnabled)
                        }
                        HorizontalDivider()
                        TextButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("删除账号", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除账号") },
            text = { Text("删除「${account?.displayName}」？其额度快照与历史记录将被一并清除。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteAccount()
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun DetailHeader(ui: DetailUiState, brandColor: Color, abbr: String) {
    val state = ui.state
    val snapshot = state.snapshot
    val minPercent = snapshot?.minRemainingPercent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(brandColor, brandColor.copy(alpha = 0.85f))), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center) {
                Text(abbr, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(ui.account?.displayName ?: "", color = Color.White,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                snapshot?.planName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("最低剩余额度", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                Text(Format.percent(minPercent), color = Color.White,
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(state.syncStatus.label(), color = Color.White, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text("更新于 " + Format.relativeTime(state.lastSuccessAt), color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}