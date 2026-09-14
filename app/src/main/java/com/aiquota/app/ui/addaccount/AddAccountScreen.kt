package com.aiquota.app.ui.addaccount

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiquota.app.BuildConfig
import com.aiquota.app.domain.model.ProviderAccount
import com.aiquota.app.domain.model.ProviderId
import com.aiquota.app.ui.util.fallbackVisual
import com.aiquota.app.ui.util.visual

private data class ProviderOption(
    val id: ProviderId,
    val desc: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddAccountViewModel = hiltViewModel()
) {
    val options = buildList {
        add(ProviderOption(ProviderId.GLM, "智谱 GLM Coding Plan，需开启桌面端本地桥接"))
        add(ProviderOption(ProviderId.OPENAI_CODEX, "ChatGPT / Codex 订阅，需本地桥接"))
        add(ProviderOption(ProviderId.MINIMAX, "MiniMax Token / Coding Plan，需本地桥接"))
        add(ProviderOption(ProviderId.OPENCODE_GO, "OpenCode Go 额度，需本地桥接"))
        add(ProviderOption(ProviderId.GROK, "Grok / xAI 订阅，当前暂未开放额度查询"))
        if (BuildConfig.DEBUG_MOCK_ALLOWED) {
            add(ProviderOption(ProviderId.DEBUG, "内置调试模拟数据，用于验证界面与缓存"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加平台") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "选择要统一监控的 AI 服务，添加后可下拉刷新查看实时额度。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(options.size) { i ->
                ProviderOptionRow(option = options[i]) {
                    viewModel.add(options[i].id) { onBack() }
                }
            }
        }
    }
}

@Composable
private fun ProviderOptionRow(option: ProviderOption, onClick: () -> Unit) {
    val visual = if (option.id.ordinal < 0) fallbackVisual("") else option.id.visual()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).background(visual.brandColor, CircleShape), contentAlignment = Alignment.Center) {
            Text(visual.abbreviation, color = Color.White,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(option.id.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(option.desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.primary)
    }
}