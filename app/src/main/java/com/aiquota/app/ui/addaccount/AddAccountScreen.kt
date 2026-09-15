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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiquota.app.BuildConfig
import com.aiquota.app.domain.model.ProviderId
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
    val selected by viewModel.selectedProvider.collectAsState()
    if (selected == null) {
        ProviderListScreen(viewModel, onBack, modifier)
    } else {
        ProviderConfigScreen(viewModel, selected!!, onBack, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderListScreen(viewModel: AddAccountViewModel, onBack: () -> Unit, modifier: Modifier) {
    val options = buildList {
        add(ProviderOption(ProviderId.GLM, "智谱 GLM Coding Plan，需本地桌面桥接"))
        add(ProviderOption(ProviderId.OPENAI_CODEX, "ChatGPT / Codex 订阅，需本地桌面桥接"))
        add(ProviderOption(ProviderId.MINIMAX, "MiniMax Coding Plan，需本地桌面桥接"))
        add(ProviderOption(ProviderId.OPENCODE_GO, "OpenCode Go 额度，需本地桌面桥接"))
        add(ProviderOption(ProviderId.GROK, "Grok / xAI 订阅，官方暂未开放额度查询"))
        if (BuildConfig.DEBUG_MOCK_ALLOWED) {
            add(ProviderOption(ProviderId.DEBUG, "内置调试模拟数据，仅用于验证界面与缓存"))
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("添加平台") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
        }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "选择平台，进入配置页填写连接参数并测试连接。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(options.size) { i -> ProviderOptionRow(options[i]) { viewModel.select(options[i].id) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderConfigScreen(
    viewModel: AddAccountViewModel,
    providerId: ProviderId,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val testState by viewModel.testState.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val visual = providerId.visual()

    var accountName by rememberSaveable { mutableStateOf("") }
    var bridgeUrl by rememberSaveable { mutableStateOf("") }
    var bridgeSecret by rememberSaveable { mutableStateOf("") }

    val needsCredential = viewModel.needsCredential(providerId)
    val connector = viewModel.defaultConnector(providerId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配置 ${providerId.displayName}") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.backToList() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回平台列表")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 平台标识
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(visual.brandColor, CircleShape), contentAlignment = Alignment.Center) {
                    Text(visual.abbreviation, color = Color.White,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(providerId.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("连接方式：${connector.name}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()

            if (connector.name != "BRIDGE") {
                // MOCK / UNAVAILABLE：无需连接凭据
                Text("该平台当前无需填写连接凭据。", style = MaterialTheme.typography.bodyMedium)
                if (connector.name == "UNAVAILABLE") {
                    Text("官方暂未开放额度查询接口，账号仅作占位跟踪。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = {
                        viewModel.save(providerId, accountName, "", "", { onBack() })
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saving) "保存中…" else "保存账号")
                }
                return@Column
            }

            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it },
                label = { Text("账号名称") },
                placeholder = { Text("例如：我的 Codex Pro") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bridgeUrl,
                onValueChange = { bridgeUrl = it; viewModel.resetTest() },
                label = { Text("Bridge 地址") },
                placeholder = { Text("https://192.168.1.100:8787") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bridgeSecret,
                onValueChange = { bridgeSecret = it; viewModel.resetTest() },
                label = { Text("Bridge Secret") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 测试连接结果
            TestResultRow(testState)

            OutlinedButton(
                onClick = { viewModel.testConnection(providerId, bridgeUrl, bridgeSecret) },
                enabled = testState !is ConnectionTestState.Testing && bridgeUrl.isNotBlank() && bridgeSecret.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (testState is ConnectionTestState.Testing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("测试连接")
            }

            Button(
                onClick = { viewModel.save(providerId, accountName, bridgeUrl, bridgeSecret, { onBack() }) },
                enabled = !saving && testState is ConnectionTestState.Success,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) "保存中…" else "保存账号")
            }
            Text(
                "提示：请先测试连接成功，再保存账号。Bridge 建议使用 HTTPS；若为局域网 HTTP，请确保网络与安全策略允许。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TestResultRow(state: ConnectionTestState) {
    val (icon, color, text) = when (state) {
        is ConnectionTestState.Success -> Triple(
            Icons.Default.Check, Color(0xFF2E7D32),
            state.planName?.takeIf { it.isNotBlank() }
                ?: "连接成功，目标 Provider 已可通过 Bridge 查询额度")
        is ConnectionTestState.Failure -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, state.message)
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun ProviderOptionRow(option: ProviderOption, onClick: () -> Unit) {
    val visual = option.id.visual()
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
        Icon(Icons.Default.Add, contentDescription = "配置", tint = MaterialTheme.colorScheme.primary)
    }
}