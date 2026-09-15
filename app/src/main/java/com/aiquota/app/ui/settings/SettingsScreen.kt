package com.aiquota.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiquota.app.domain.model.RefreshInterval

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("自动刷新", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        RefreshIntervalOption(
                            interval = settings.autoRefreshInterval,
                            onSelect = { viewModel.setRefreshIntervalMillis(it.durationMillis) }
                        )
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("实时额度监控", style = MaterialTheme.typography.bodyLarge)
                                Text("后台持续刷新并及时提醒额度变化", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = settings.realtimeMonitoringEnabled,
                                onCheckedChange = viewModel::setRealtimeMonitoring)
                        }
                    }
                }
            }

            item {
                Text("通知阈值", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("当任意平台关键额度低于所选阈值时提醒", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 20, 10, 5, 0).forEach { t ->
                                FilterChip(
                                    selected = settings.thresholds.contains(t),
                                    onClick = {
                                        val next = if (settings.thresholds.contains(t))
                                            settings.thresholds - t else (settings.thresholds + t).sorted()
                                        viewModel.setThresholds(next)
                                    },
                                    label = { Text("$t%") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshIntervalOption(interval: RefreshInterval, onSelect: (RefreshInterval) -> Unit) {
    RefreshInterval.entries.forEach { option ->
        Row(
            Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = option == interval, onClick = { onSelect(option) })
            Spacer(Modifier.width(4.dp))
            Text(option.labelId, style = MaterialTheme.typography.bodyLarge)
        }
    }
}