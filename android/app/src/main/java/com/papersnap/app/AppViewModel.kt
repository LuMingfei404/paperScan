package com.papersnap.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.papersnap.app.data.ChatMessageEntity
import com.papersnap.app.data.ChatMock
import com.papersnap.app.data.Paper
import com.papersnap.app.data.PaperContext
import com.papersnap.app.data.PaperContextMode
import com.papersnap.app.data.PaperRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed class Screen {
    object Feed : Screen()
    data class Detail(val id: String) : Screen()
    object Filter : Screen()
    object Bookmarks : Screen()
    object Settings : Screen()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PaperRepository(app)

    val dateOptions: StateFlow<List<String>> = repo.availableDates()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate

    private val _screen = MutableStateFlow<Screen>(Screen.Feed)
    val screen: StateFlow<Screen> = _screen
    private val backStack = ArrayDeque<Screen>()

    private val _cats = MutableStateFlow(repo.prefs.selectedCategories)
    val cats: StateFlow<Set<String>> = _cats
    val darkTheme = MutableStateFlow(repo.prefs.darkTheme)
    val dataUrl = MutableStateFlow(repo.prefs.dataUrl)

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val pendingReply = MutableStateFlow(false)
    val contextLoading = MutableStateFlow(false)
    val paperContextMode = MutableStateFlow<PaperContextMode?>(null)

    private var currentDetailId: String? = null
    private val contextByPaper = mutableMapOf<String, PaperContext>()

    private val _toast = MutableSharedFlow<String>()
    val toast: SharedFlow<String> = _toast

    val chatKey = MutableStateFlow(repo.prefs.chatApiKey)
    val chatBase = MutableStateFlow(repo.prefs.chatBaseUrl)
    val chatModel = MutableStateFlow(repo.prefs.chatModel)

    val feed: StateFlow<List<Paper>> = _selectedDate
        .flatMapLatest { date ->
            if (date == null) flowOf(emptyList())
            else repo.papersForDate(date)
        }
        .combine(_cats) { list, cats ->
            list.filter { entity ->
                !entity.isHidden &&
                    (cats.isEmpty() || entity.categoryList().any { it in cats })
            }.map { it.toPaper() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bookmarks: StateFlow<List<Paper>> = repo.bookmarkedPapers()
        .map { list -> list.map { it.toPaper() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val papersById: StateFlow<Map<String, Paper>> = repo.allPapers()
        .map { list -> list.associate { it.arxivId to it.toPaper() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        viewModelScope.launch {
            repo.availableDates().collect { dates ->
                if (dates.isNotEmpty()) {
                    val cur = _selectedDate.value
                    if (cur == null || cur !in dates) _selectedDate.value = dates.first()
                }
            }
        }
        load()
    }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val cur = _selectedDate.value
                if (cur != null) repo.loadDate(cur, force) else repo.loadLatest()
            } catch (e: Exception) {
                _error.value = "加载失败：${e.message ?: "数据源不可用"}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun setDate(date: String) {
        if (date == _selectedDate.value) return
        _selectedDate.value = date
        load()
    }

    fun navigate(target: Screen) {
        backStack.addLast(_screen.value)
        _screen.value = target
        if (target is Screen.Detail) {
            currentDetailId = target.id
            paperContextMode.value = null
        } else {
            currentDetailId = null
        }
    }

    fun back() {
        _screen.value = backStack.removeLastOrNull() ?: Screen.Feed
    }

    fun toggleBookmark(id: String) {
        viewModelScope.launch {
            val on = repo.toggleBookmark(id)
            _toast.emit(if (on) "已收藏 ♥" else "已取消收藏")
        }
    }

    fun hide(id: String) {
        viewModelScope.launch {
            repo.setHidden(id, true)
            _toast.emit("已隐藏，今天不再显示")
        }
    }

    fun restoreHidden() {
        val date = _selectedDate.value ?: return
        viewModelScope.launch {
            repo.restoreHidden(date)
            _toast.emit("已恢复全部隐藏论文")
        }
    }

    fun saveCats(cats: Set<String>) {
        repo.prefs.selectedCategories = cats
        _cats.value = cats
    }

    fun setDark(dark: Boolean) {
        repo.prefs.darkTheme = dark
        darkTheme.value = dark
    }

    fun setDataUrl(url: String) {
        repo.prefs.dataUrl = url
        dataUrl.value = url
    }

    fun setChatKey(v: String) {
        repo.prefs.chatApiKey = v
        chatKey.value = v
    }

    fun setChatBase(v: String) {
        repo.prefs.chatBaseUrl = v
        chatBase.value = v
    }

    fun setChatModel(v: String) {
        repo.prefs.chatModel = v
        chatModel.value = v
    }

    fun chatMessages(id: String): Flow<List<ChatMessageEntity>> = repo.chatMessages(id)

    fun sendChat(id: String, text: String) {
        val t = text.trim()
        if (t.isEmpty() || pendingReply.value || contextLoading.value) return
        viewModelScope.launch {
            val paperEntity = repo.paperById(id) ?: return@launch
            val paper = paperEntity.toPaper()
            val history = repo.chatHistory(id).map { it.role to it.content }.takeLast(10)
            repo.addChat(id, "user", t)
            pendingReply.value = true
            val reply: String
            try {
                val context = contextByPaper[id] ?: run {
                    contextLoading.value = true
                    val ctx = repo.loadPaperContext(paperEntity)
                    contextByPaper[id] = ctx
                    if (currentDetailId == id) paperContextMode.value = ctx.mode
                    contextLoading.value = false
                    ctx
                }
                reply = if (repo.prefs.chatApiKey.isBlank()) {
                    delay(600 + Random.nextLong(0, 500))
                    ChatMock.reply(paper, t)
                } else {
                    repo.chatCompletion(buildSystemPrompt(paper, context), history, t)
                }
            } catch (e: Exception) {
                pendingReply.value = false
                contextLoading.value = false
                _toast.emit("AI 调用失败：${e.message ?: "网络错误"}")
                return@launch
            }
            repo.addChat(id, "assistant", reply)
            pendingReply.value = false
        }
    }

    private fun buildSystemPrompt(paper: Paper, context: PaperContext): String {
        val modeLabel = when (context.mode) {
            PaperContextMode.FULLTEXT -> "论文全文"
            PaperContextMode.SEARCH -> "网络检索资料"
            PaperContextMode.ABSTRACT -> "论文摘要"
        }
        val contextBlock = if (context.text.isNotBlank()) {
            "\n--- 论文资料（$modeLabel）开始 ---\n${context.text}\n--- 论文资料结束 ---"
        } else {
            ""
        }
        return """
你是 PaperSnap 论文快闪的 AI 助手。请用通俗的中文围绕这篇论文回答用户的问题。
只能基于下面提供的论文信息回答，不要编造摘要中没有的内容；信息不足时明确说明。
当前依据：$modeLabel

标题：${paper.title}
中文标题：${paper.titleZh.ifBlank { "（暂无翻译）" }}
一句话总结：${paper.summary}
摘要：${paper.abstract}
$contextBlock
        """.trimIndent()
    }

    fun clearCache() {
        viewModelScope.launch {
            repo.clearCache()
            _toast.emit("已清理缓存与对话记录")
            load(force = true)
        }
    }
}
