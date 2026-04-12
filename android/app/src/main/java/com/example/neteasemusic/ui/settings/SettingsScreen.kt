package com.example.neteasemusic.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.neteasemusic.data.repository.AppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SectionHeader("API 配置")

            LabeledTextField(
                label = "网易云 API 地址",
                description = "自行部署的 NeteaseCloudMusicApi 服务地址，例如：http://192.168.1.100:3000",
                value = settings.apiUrl,
                onValueChange = { viewModel.update(settings.copy(apiUrl = it)) },
                keyboardType = KeyboardType.Uri
            )

            Spacer(modifier = Modifier.height(12.dp))

            LabeledTextField(
                label = "匹配音源",
                description = "调用 /song/url/match 时传入的 source 参数，留空表示自动匹配",
                value = settings.matchSource,
                onValueChange = { viewModel.update(settings.copy(matchSource = it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            SectionDivider()

            SectionHeader("搜索设置")

            SearchLimitSlider(
                value = settings.searchLimit,
                onValueChange = { viewModel.update(settings.copy(searchLimit = it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            SectionDivider()

            SectionHeader("播放内容")

            SwitchRow(
                label = "发送歌曲介绍",
                description = "加载歌曲时显示歌名、歌手、专辑、时长等信息",
                checked = settings.sendDetail,
                onCheckedChange = { viewModel.update(settings.copy(sendDetail = it)) }
            )
            SwitchRow(
                label = "显示封面图",
                description = "播放时在播放界面显示专辑封面",
                checked = settings.sendCover,
                onCheckedChange = { viewModel.update(settings.copy(sendCover = it)) }
            )
            SwitchRow(
                label = "启用音频播放",
                description = "使用内置播放器播放歌曲音频",
                checked = settings.sendAudio,
                onCheckedChange = { viewModel.update(settings.copy(sendAudio = it)) }
            )
            SwitchRow(
                label = "显示歌词",
                description = "在播放界面展示实时滚动歌词",
                checked = settings.sendLyrics,
                onCheckedChange = { viewModel.update(settings.copy(sendLyrics = it)) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun LabeledTextField(
    label: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun SearchLimitSlider(value: Int, onValueChange: (Int) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "搜索结果数量",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 3f..20f,
            steps = 16,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "每次搜索时展示的歌曲选项数量（3-20 首）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
