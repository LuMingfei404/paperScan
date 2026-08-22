package com.papersnap.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papersnap.app.AppViewModel
import com.papersnap.app.Screen

private val ALL_CATEGORIES = listOf("cs.AI", "cs.CV", "cs.CL", "cs.LG", "cs.RO", "cs.SE", "cs.GR")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterScreen(vm: AppViewModel) {
    val current by vm.cats.collectAsState()
    var selected by remember { mutableStateOf(current) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(
            title = "关注领域",
            onBack = { vm.back() },
            action = {
                TextButton(onClick = { vm.saveCats(selected); vm.back() }) {
                    Text("✓", fontSize = 16.sp)
                }
            }
        )
        Text(
            "勾选要关注的领域（全不选 = 全部显示）",
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ALL_CATEGORIES.forEach { c ->
                val on = c in selected
                Surface(
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = if (on) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.clickable {
                        selected = if (on) selected - c else selected + c
                    }
                ) {
                    Text(
                        c,
                        Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { vm.saveCats(selected); vm.back() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(50.dp)
        ) {
            Text("确认选择", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BookmarksScreen(vm: AppViewModel) {
    val bookmarks by vm.bookmarks.collectAsState()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val filtered = remember(bookmarks, q) {
        bookmarks.filter {
            q.isEmpty() || (it.titleZh + it.title + it.summary).contains(q, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(
            title = "我的灵感库",
            onBack = { vm.back() },
            action = {
                TextButton(
                    onClick = {
                        shareText(
                            context,
                            bookmarks.joinToString("\n\n") { "${it.titleZh}\n${it.summary}" }
                        )
                    }
                ) {
                    Text("分享", fontSize = 12.sp)
                }
            }
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            placeholder = { Text("搜索标题 / 一句话总结", fontSize = 13.sp) },
            leadingIcon = { Text("🔍", fontSize = 14.sp) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        if (filtered.isEmpty()) {
            Text(
                if (bookmarks.isEmpty()) "还没有收藏 ♡\n\n在首页右滑卡片，或点卡片上的 ♥ 收藏"
                else "没有匹配的收藏",
                Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
            ) {
                items(filtered, key = { it.arxivId }) { p ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.navigate(Screen.Detail(p.arxivId)) }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                p.titleZh.ifBlank { p.title },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                p.summary,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    p.categories.joinToString(" · "),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { vm.toggleBookmark(p.arxivId) }) {
                                    Text("♥ 取消收藏", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val dark by vm.darkTheme.collectAsState()
    val url by vm.dataUrl.collectAsState()
    var urlText by remember(url) { mutableStateOf(url) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(title = "设置", onBack = { vm.back() })
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingRow("深色模式", "切换界面主题") {
                Switch(checked = dark, onCheckedChange = { vm.setDark(it) })
            }
            SettingRow("数据源地址", "留空使用内置演示数据；填入后端 JSON 地址后生效") {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        vm.setDataUrl(it)
                    },
                    modifier = Modifier.width(150.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )
            }
            SettingRow("清理缓存", "清除本地缓存的论文与对话记录", onClick = { vm.clearCache() })
            SettingRow("当前版本", "v0.1.0 · 自用版")
            SettingRow("关于", "PaperSnap 论文快闪：每日学术速览")
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    desc: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
    }
}
