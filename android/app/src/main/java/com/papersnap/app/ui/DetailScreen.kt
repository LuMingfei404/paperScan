package com.papersnap.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papersnap.app.AppViewModel
import com.papersnap.app.data.ChatMessageEntity
import com.papersnap.app.data.Paper
import com.papersnap.app.data.PaperContextMode

private val suggestedQuestions = listOf(
    "这篇论文的核心创新点是什么？",
    "和之前的方法比，强在哪里？",
    "我能用它做什么应用？",
    "实现难度有多大？"
)

@Composable
fun DetailScreen(vm: AppViewModel, paperId: String) {
    val papers by vm.papersById.collectAsState()
    val paper = papers[paperId]
    if (paper == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val messages by vm.chatMessages(paperId).collectAsState(initial = emptyList())
    val pending by vm.pendingReply.collectAsState()
    val contextLoading by vm.contextLoading.collectAsState()
    val contextMode by vm.paperContextMode.collectAsState()
    val chatKey by vm.chatKey.collectAsState()
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        SubHeader(
            title = "论文详情",
            onBack = { vm.back() },
            action = {
                TextButton(onClick = { shareText(context, paper.summary) }) {
                    Text("分享", fontSize = 12.sp)
                }
            }
        )

        PaperInfoSection(
            paper = paper,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            context = context
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        ChatPanel(
            modifier = Modifier.weight(1f),
            messages = messages,
            pending = pending,
            contextLoading = contextLoading,
            modeLabel = modeLabel(chatKey, contextLoading, contextMode),
            input = input,
            onInputChange = { input = it },
            onSend = { text ->
                val t = text.trim()
                if (t.isNotEmpty()) {
                    vm.sendChat(paperId, t)
                    if (text == input) input = ""
                }
            }
        )
    }
}

@Composable
private fun PaperInfoSection(
    paper: Paper,
    expanded: Boolean,
    onToggle: () -> Unit,
    context: Context
) {
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            paper.categories.take(4).forEach { cat ->
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        cat,
                        Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "arXiv:${paper.arxivId}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            paper.titleZh.ifBlank { paper.title },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                paper.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${paper.authors.joinToString("、")} · ${paper.published.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    paper.abstract,
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paper.pdfUrl)))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("查看 PDF ↗", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { copyText(context, paper.summary) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("复制总结", fontSize = 13.sp)
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "一句话总结",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(paper.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        TextButton(
            onClick = onToggle,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                if (expanded) "收起 ▲" else "展开摘要/作者信息 ▼",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ChatPanel(
    modifier: Modifier = Modifier,
    messages: List<ChatMessageEntity>,
    pending: Boolean,
    contextLoading: Boolean,
    modeLabel: String,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, pending, contextLoading) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💬 AI 讨论", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    modeLabel,
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            suggestedQuestions.forEach { q ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.clickable(onClick = { onSend(q) })
                ) {
                    Text(q, Modifier.padding(horizontal = 11.dp, vertical = 7.dp), fontSize = 11.sp)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages.size) { i ->
                val m = messages[i]
                val isUser = m.role == "user"
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        color = if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = if (isUser) {
                            RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp)
                        } else {
                            RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
                        },
                        border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            m.content,
                            Modifier.padding(10.dp),
                            fontSize = 13.sp,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            if (contextLoading) {
                item {
                    Text(
                        "正在获取论文资料（首次提问会联网，请稍候）…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (pending) {
                item {
                    Text(
                        "AI 正在思考…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("向 AI 追问这篇论文…", fontSize = 13.sp) },
                shape = RoundedCornerShape(999.dp),
                maxLines = 2,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend(input) })
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = { onSend(input) })
            ) {
                Text(
                    "➤",
                    Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun SubHeader(
    title: String,
    onBack: () -> Unit,
    action: @Composable () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text("←", fontSize = 18.sp)
        }
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) {
            action()
        }
    }
}

private fun modeLabel(chatKey: String, loading: Boolean, mode: PaperContextMode?): String = when {
    chatKey.isBlank() -> "演示模式"
    loading -> "正在获取论文资料…"
    mode == PaperContextMode.FULLTEXT -> "已加载全文"
    mode == PaperContextMode.SEARCH -> "基于网络检索"
    mode == PaperContextMode.ABSTRACT -> "基于摘要"
    else -> "等待提问"
}

internal fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("papersnap", text))
    Toast.makeText(context, "已复制一句话总结", Toast.LENGTH_SHORT).show()
}
