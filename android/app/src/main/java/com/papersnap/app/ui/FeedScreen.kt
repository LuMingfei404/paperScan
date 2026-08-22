package com.papersnap.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papersnap.app.AppViewModel
import com.papersnap.app.Screen
import com.papersnap.app.data.Paper
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun FeedScreen(vm: AppViewModel) {
    val feed by vm.feed.collectAsState()
    val loading by vm.loading.collectAsState()
    val date by vm.selectedDate.collectAsState()
    val dateOptions by vm.dateOptions.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val bookmarkSet = remember(bookmarks) { bookmarks.map { it.arxivId }.toSet() }
    val pagerState = rememberPagerState(pageCount = { feed.size })

    var dateOpen by remember { mutableStateOf(false) }

    LaunchedEffect(feed.size) {
        if (feed.isNotEmpty() && pagerState.currentPage >= feed.size) {
            pagerState.scrollToPage(feed.size - 1)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "◈",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "PaperSnap",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { dateOpen = true }) {
                    Text("${date ?: "…"} ▾", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = dateOpen, onDismissRequest = { dateOpen = false }) {
                    dateOptions.forEach { d ->
                        DropdownMenuItem(
                            text = { Text("$d ${dateLabel(d)}", fontSize = 13.sp) },
                            onClick = {
                                dateOpen = false
                                vm.setDate(d)
                            }
                        )
                    }
                }
            }
            TextButton(onClick = { vm.load(force = true) }) {
                Text("⟳", fontSize = 16.sp)
            }
            TextButton(onClick = { vm.navigate(Screen.Filter) }) {
                Text("筛选", fontSize = 12.sp)
            }
            TextButton(onClick = { vm.navigate(Screen.Bookmarks) }) {
                Text("♥", fontSize = 15.sp)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && feed.isEmpty() -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )

                feed.isEmpty() -> EmptyFeed(onRestore = { vm.restoreHidden() })

                else -> VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    PaperCard(
                        paper = feed[page],
                        bookmarked = feed[page].arxivId in bookmarkSet,
                        onOpen = { vm.navigate(Screen.Detail(feed[page].arxivId)) },
                        onBookmark = { vm.toggleBookmark(feed[page].arxivId) },
                        onHide = { vm.hide(feed[page].arxivId) }
                    )
                }
            }

            if (feed.isNotEmpty()) {
                Text(
                    "${pagerState.currentPage + 1} / ${feed.size}",
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "↑↓ 翻页 · ←→ 收藏/隐藏",
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        BottomNav(
            current = "feed",
            onHome = { vm.navigate(Screen.Feed) },
            onBookmarks = { vm.navigate(Screen.Bookmarks) },
            onSettings = { vm.navigate(Screen.Settings) }
        )
    }
}

@Composable
private fun PaperCard(
    paper: Paper,
    bookmarked: Boolean,
    onOpen: () -> Unit,
    onBookmark: () -> Unit,
    onHide: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dragX by remember { mutableFloatStateOf(0f) }
    val offsetAnim = remember { Animatable(0f) }
    val shape = RoundedCornerShape(22.dp)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), shape)
            .pointerInput(paper.arxivId) {
                detectHorizontalDragGestures(
                    onDragStart = { scope.launch { offsetAnim.stop() } },
                    onHorizontalDrag = { change, amount ->
                        dragX += amount
                        change.consume()
                    },
                    onDragEnd = {
                        scope.launch {
                            val target = when {
                                dragX < -110f -> -420f
                                dragX > 110f -> 420f
                                else -> 0f
                            }
                            offsetAnim.snapTo(dragX)
                            offsetAnim.animateTo(target)
                            dragX = offsetAnim.value
                            if (target < 0f) onHide()
                            else if (target > 0f) onBookmark()
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetAnim.snapTo(dragX)
                            offsetAnim.animateTo(0f)
                            dragX = offsetAnim.value
                        }
                    }
                )
            }
            .graphicsLayer {
                translationX = dragX
                rotationZ = dragX / 20f
            }
            .clickable(onClick = onOpen)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            paper.categories.take(3).forEach { cat ->
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

        Spacer(Modifier.height(12.dp))
        Text(
            paper.titleZh.ifBlank { paper.title },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            paper.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))

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
                Text(
                    paper.summary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paper.pdfUrl)))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("查看原文 ↗", fontSize = 13.sp)
            }
            Surface(
                color = if (bookmarked) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable(onClick = onBookmark)
            ) {
                Text(
                    if (bookmarked) "♥" else "♡",
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    fontSize = 16.sp,
                    color = if (bookmarked) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyFeed(onRestore: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🗂️", fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text("当天没有可显示的论文", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "可能是领域筛选太窄，或论文已被全部隐藏",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onRestore) {
            Text("恢复全部隐藏论文", fontSize = 13.sp)
        }
    }
}

@Composable
fun BottomNav(
    current: String,
    onHome: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .navigationBarsPadding()
            .padding(vertical = 6.dp)
    ) {
        NavItem("⌂", "首页", current == "feed", onHome, Modifier.weight(1f))
        NavItem("★", "收藏", current == "bookmarks", onBookmarks, Modifier.weight(1f))
        NavItem("⚙", "设置", current == "settings", onSettings, Modifier.weight(1f))
    }
}

@Composable
private fun NavItem(icon: String, label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 18.sp, color = color)
        Text(
            label,
            fontSize = 10.sp,
            color = color,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun dateLabel(date: String): String {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return ""
    val today = LocalDate.now()
    return when (d) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        today.minusDays(2) -> "前天"
        else -> ""
    }
}
