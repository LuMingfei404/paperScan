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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papersnap.app.AppViewModel
import com.papersnap.app.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ALL_CATEGORIES = listOf("cs.AI", "cs.CV", "cs.CL", "cs.LG", "cs.RO", "cs.SE", "cs.GR")

private val TIME_OPTIONS = listOf(
    0 to "不限",
    30 to "近1个月",
    90 to "近3个月",
    180 to "近6个月",
    365 to "近1年"
)

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
            "勾选要关注的论文类型（全不选 = 显示全部）",
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
                        catLabel(c),
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
                    PaperListCard(
                        title = p.titleZh.ifBlank { p.title },
                        summary = p.summary,
                        footer = p.categories.joinToString(" · ") { catLabel(it) },
                        actionText = "♥ 取消收藏",
                        onClick = { vm.navigate(Screen.Detail(p.arxivId)) },
                        onAction = { vm.toggleBookmark(p.arxivId) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val history by vm.readHistory.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val fmt = remember { SimpleDateFormat("M/d HH:mm", Locale.CHINA) }
    val filtered = remember(history, q) {
        history.filter {
            q.isEmpty() ||
                (it.paper.titleZh + it.paper.title + it.paper.summary).contains(q, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(
            title = "浏览记录",
            onBack = { vm.back() },
            action = {
                TextButton(onClick = { vm.clearRead() }) {
                    Text("🗑 清空", fontSize = 12.sp)
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
                if (history.isEmpty()) "还没有浏览记录\n\n点击论文卡片阅读后会自动记录，并移出论文池"
                else "没有匹配的记录",
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
                items(filtered, key = { it.paper.arxivId }) { entry ->
                    PaperListCard(
                        title = entry.paper.titleZh.ifBlank { entry.paper.title },
                        summary = entry.paper.summary,
                        footer = "已读 ${fmt.format(Date(entry.record.readAt))} · " +
                            entry.paper.categories.joinToString(" · ") { catLabel(it) },
                        actionText = "✕ 移除",
                        onClick = { vm.navigate(Screen.Detail(entry.paper.arxivId)) },
                        onAction = { vm.removeRead(entry.paper.arxivId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaperListCard(
    title: String,
    summary: String,
    footer: String,
    actionText: String,
    onClick: () -> Unit,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                summary,
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
                    footer,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onAction) {
                    Text(actionText, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsScreen(vm: AppViewModel) {
    val dark by vm.darkTheme.collectAsState()
    val url by vm.dataUrl.collectAsState()
    val chatKey by vm.chatKey.collectAsState()
    val chatBase by vm.chatBase.collectAsState()
    val chatModel by vm.chatModel.collectAsState()
    val cats by vm.cats.collectAsState()
    val timeRange by vm.timeRangeDays.collectAsState()
    val poolSize by vm.poolSize.collectAsState()

    var urlText by remember(url) { mutableStateOf(url) }
    var chatKeyText by remember(chatKey) { mutableStateOf(chatKey) }
    var chatBaseText by remember(chatBase) { mutableStateOf(chatBase) }
    var chatModelText by remember(chatModel) { mutableStateOf(chatModel) }
    var poolText by remember(poolSize) { mutableStateOf(poolSize.toString()) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(title = "设置", onBack = { vm.back() })
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingRow("每天论文数量", "论文池容量上限（1~100，默认 40）") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { vm.setPoolSize(poolSize - 10) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("−", fontSize = 16.sp)
                    }
                    OutlinedTextField(
                        value = poolText,
                        onValueChange = { poolText = it },
                        modifier = Modifier.width(64.dp).padding(horizontal = 4.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedButton(
                        onClick = { vm.setPoolSize(poolSize + 10) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("＋", fontSize = 16.sp)
                    }
                }
            }

            SettingRow("论文类型（多选）", "全不选 = 显示全部") {
                Spacer(Modifier.width(0.dp))
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ALL_CATEGORIES.forEach { c ->
                    val on = c in cats
                    Surface(
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = if (on) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { vm.saveCats(if (on) cats - c else cats + c) }
                    ) {
                        Text(
                            catLabel(c),
                            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            SettingRow("发表时间", "只显示所选日期附近发表的论文") {
                Spacer(Modifier.width(0.dp))
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TIME_OPTIONS.forEach { (days, label) ->
                    val on = timeRange == days
                    Surface(
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = if (on) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.clickable { vm.setTimeRange(days) }
                    ) {
                        Text(
                            label,
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

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
            SettingRow("聊天 API Key", "留空则 AI 讨论使用演示回复") {
                OutlinedTextField(
                    value = chatKeyText,
                    onValueChange = {
                        chatKeyText = it
                        vm.setChatKey(it)
                    },
                    modifier = Modifier.width(150.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                    visualTransformation = PasswordVisualTransformation()
                )
            }
            SettingRow("聊天 API 地址", "OpenAI 兼容接口，默认 DeepSeek") {
                OutlinedTextField(
                    value = chatBaseText,
                    onValueChange = {
                        chatBaseText = it
                        vm.setChatBase(it)
                    },
                    modifier = Modifier.width(150.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )
            }
            SettingRow("聊天模型", "如 deepseek-chat / gpt-4o-mini") {
                OutlinedTextField(
                    value = chatModelText,
                    onValueChange = {
                        chatModelText = it
                        vm.setChatModel(it)
                    },
                    modifier = Modifier.width(150.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )
            }
            SettingRow("清理缓存", "清除本地缓存的论文、对话与浏览记录", onClick = { vm.clearCache() })
            SettingRow("当前版本", "v0.2.0 · 自用版")
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
