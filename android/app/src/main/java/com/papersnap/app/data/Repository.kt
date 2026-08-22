package com.papersnap.app.data

import android.content.Context
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

enum class PaperContextMode { FULLTEXT, SEARCH, ABSTRACT }

data class PaperContext(val mode: PaperContextMode, val text: String)

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("papersnap", Context.MODE_PRIVATE)

    var selectedCategories: Set<String>
        get() = sp.getStringSet("cats", emptySet())?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet("cats", value).apply()

    var darkTheme: Boolean
        get() = sp.getBoolean("dark", true)
        set(value) = sp.edit().putBoolean("dark", value).apply()

    var dataUrl: String
        get() = sp.getString("dataUrl", "") ?: ""
        set(value) = sp.edit().putString("dataUrl", value).apply()

    var chatApiKey: String
        get() = sp.getString("chatKey", "") ?: ""
        set(value) = sp.edit().putString("chatKey", value).apply()

    var chatBaseUrl: String
        get() = sp.getString("chatBase", "https://api.deepseek.com") ?: "https://api.deepseek.com"
        set(value) = sp.edit().putString("chatBase", value).apply()

    var chatModel: String
        get() = sp.getString("chatModel", "deepseek-chat") ?: "deepseek-chat"
        set(value) = sp.edit().putString("chatModel", value).apply()

    var poolSize: Int
        get() = sp.getInt("pool", 40)
        set(value) = sp.edit().putInt("pool", value.coerceIn(1, 100)).apply()

    var timeRangeDays: Int
        get() = sp.getInt("trange", 0)
        set(value) = sp.edit().putInt("trange", value).apply()
}

class PaperRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    val prefs = Prefs(appContext)

    fun papersForDate(date: String): Flow<List<PaperEntity>> = db.paperDao().papersForDate(date)
    fun bookmarkedPapers(): Flow<List<PaperEntity>> = db.paperDao().bookmarkedPapers()
    fun allPapers(): Flow<List<PaperEntity>> = db.paperDao().allPapers()
    fun availableDates(): Flow<List<String>> = db.paperDao().availableDates()
    fun readPapers(): Flow<List<ReadPaperEntity>> = db.readDao().all()
    fun chatMessages(id: String): Flow<List<ChatMessageEntity>> = db.chatDao().messagesForPaper(id)

    /** 加载最新一期：配置了数据源则拉 latest.json，否则读内置资产。 */
    suspend fun loadLatest(force: Boolean = false) {
        val url = prefs.dataUrl
        if (url.isNotBlank()) {
            val raw = withContext(Dispatchers.IO) { fetchFromNetwork(url) }
            val (_, papers) = parsePapers(raw)
            db.paperDao().upsertAll(papers)
        } else {
            assetDates().forEach { d ->
                if (force || db.paperDao().countForDate(d) == 0) loadDate(d, force)
            }
        }
    }

    suspend fun loadDate(date: String, force: Boolean = false) {
        if (!force && db.paperDao().countForDate(date) > 0) return
        val raw = withContext(Dispatchers.IO) {
            val url = prefs.dataUrl
            if (url.isNotBlank()) {
                try {
                    fetchFromNetwork(dateFileUrl(url, date))
                } catch (e: Exception) {
                    readAsset(date)
                }
            } else {
                readAsset(date)
            }
        }
        val (_, papers) = parsePapers(raw)
        db.paperDao().upsertAll(papers)
    }

    suspend fun paperById(id: String): PaperEntity? = db.paperDao().paperById(id)

    suspend fun toggleBookmark(id: String): Boolean {
        val p = db.paperDao().paperById(id) ?: return false
        db.paperDao().setBookmarked(id, !p.isBookmarked)
        return !p.isBookmarked
    }

    suspend fun setHidden(id: String, value: Boolean) = db.paperDao().setHidden(id, value)
    suspend fun restoreHidden(date: String) = db.paperDao().restoreHidden(date)

    suspend fun addChat(arxivId: String, role: String, content: String) {
        db.chatDao().insert(
            ChatMessageEntity(
                arxivId = arxivId,
                role = role,
                content = content,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun chatHistory(id: String): List<ChatMessageEntity> = db.chatDao().listForPaper(id)

    suspend fun markRead(id: String) {
        db.readDao().insert(ReadPaperEntity(arxivId = id, readAt = System.currentTimeMillis()))
    }

    suspend fun removeRead(id: String) = db.readDao().delete(id)

    suspend fun clearRead() = db.readDao().clearAll()

    suspend fun pruneRead(days: Int) {
        db.readDao().deleteOlderThan(System.currentTimeMillis() - days * 86_400_000L)
    }

    /** 按需获取论文资料：全文（arXiv HTML → ar5iv）→ 网络检索 → 摘要兜底；全文本地缓存。 */
    suspend fun loadPaperContext(paper: PaperEntity): PaperContext = withContext(Dispatchers.IO) {
        val cacheFile = File(appContext.filesDir, "fulltext/${paper.arxivId}.txt")
        if (cacheFile.exists()) {
            return@withContext PaperContext(PaperContextMode.FULLTEXT, cacheFile.readText())
        }
        val full = fetchPaperHtml(paper.arxivId)
        if (full != null) {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(full)
            return@withContext PaperContext(PaperContextMode.FULLTEXT, full)
        }
        val search = buildString {
            append(arxivSearch(paper.title))
            append(semanticSearch(paper.arxivId))
        }.trim()
        if (search.isNotBlank()) {
            PaperContext(PaperContextMode.SEARCH, search)
        } else {
            PaperContext(PaperContextMode.ABSTRACT, "")
        }
    }

    suspend fun chatCompletion(
        system: String,
        history: List<Pair<String, String>>,
        question: String
    ): String = withContext(Dispatchers.IO) {
        val base = prefs.chatBaseUrl.trim().removeSuffix("/")
        val model = prefs.chatModel.trim().ifBlank { "deepseek-chat" }
        val key = prefs.chatApiKey.trim()
        if (key.isBlank()) throw IllegalStateException("未配置聊天 API Key")

        val messages = mutableListOf<JSONObject>()
        messages.add(JSONObject().put("role", "system").put("content", system))
        history.forEach { (role, content) ->
            messages.add(JSONObject().put("role", role).put("content", content))
        }
        messages.add(JSONObject().put("role", "user").put("content", question))

        val payload = JSONObject()
            .put("model", model)
            .put("temperature", 0.4)
            .put("messages", JSONArray(messages))

        val conn = URL("$base/chat/completions").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 90_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: ""
        if (code !in 200..299) throw RuntimeException("HTTP $code: ${body.take(200)}")

        JSONObject(body)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    suspend fun clearCache() {
        db.paperDao().clearAll()
        db.chatDao().clearAll()
        db.readDao().clearAll()
    }

    private fun readAsset(date: String): String =
        appContext.assets.open("papers/$date.json").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun assetDates(): List<String> =
        appContext.assets.list("papers")
            ?.filter { it.endsWith(".json") }
            ?.map { it.removeSuffix(".json") }
            ?.sortedDescending()
            ?: emptyList()

    private fun fetchFromNetwork(urlString: String): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun httpGet(urlString: String): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (X11; Linux x86_64) PaperSnap/0.1"
        )
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) throw RuntimeException("HTTP $code")
        return body
    }

    private fun fetchPaperHtml(arxivId: String): String? {
        val candidates = listOf(
            "https://arxiv.org/html/$arxivId",
            "https://ar5iv.labs.arxiv.org/html/$arxivId"
        )
        for (url in candidates) {
            try {
                val doc = Jsoup.parse(httpGet(url))
                val main = doc.selectFirst("article, main") ?: doc.body()
                val text = main?.text()?.replace(Regex("\\s+"), " ")?.trim() ?: ""
                if (text.length > 800) return truncateFulltext(text)
            } catch (_: Exception) {
                // 尝试下一个来源
            }
        }
        return null
    }

    private fun truncateFulltext(text: String): String =
        if (text.length <= MAX_CONTEXT_CHARS) text
        else text.take(MAX_CONTEXT_CHARS) + "\n\n[论文全文过长，已截断，仅保留前 $MAX_CONTEXT_CHARS 字符]"

    private fun arxivSearch(title: String): String {
        return try {
            val query = URLEncoder.encode("ti:${title.take(80)}", "UTF-8")
            val xml = httpGet("https://export.arxiv.org/api/query?search_query=$query&max_results=3")
            val sb = StringBuilder()
            val entryRe = Regex("<entry>.*?</entry>", RegexOption.DOT_MATCHES_ALL)
            entryRe.findAll(xml).forEachIndexed { i, m ->
                val e = m.value
                val t = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
                    .find(e)?.groupValues?.get(1)?.trim().orEmpty()
                val s = Regex("<summary>(.*?)</summary>", RegexOption.DOT_MATCHES_ALL)
                    .find(e)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                if (t.isNotEmpty()) {
                    sb.append("arXiv 相关论文${i + 1}：《$t》\n摘要：$s\n\n")
                }
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun semanticSearch(arxivId: String): String {
        return try {
            val url = "https://api.semanticscholar.org/graph/v1/paper/arXiv:$arxivId" +
                "?fields=title,abstract,venue,year,url,openAccessPdf,citationCount"
            val json = httpGet(url)
            val o = JSONObject(json)
            val sb = StringBuilder()
            sb.append("Semantic Scholar 收录信息：\n")
            sb.append("标题：${o.optString("title")}\n")
            val abs = o.optString("abstract", "")
            if (abs.isNotBlank()) sb.append("摘要：${abs.take(2000)}\n")
            val venue = o.optString("venue", "")
            if (venue.isNotBlank()) sb.append("发表：$venue ${o.optString("year")}\n")
            sb.append("引用数：${o.optInt("citationCount", 0)}\n")
            o.optJSONObject("openAccessPdf")?.let { pdf ->
                val pdfUrl = pdf.optString("url", "")
                if (pdfUrl.isNotBlank()) sb.append("开放获取 PDF：$pdfUrl\n")
            }
            val page = o.optString("url", "")
            if (page.isNotBlank()) sb.append("详情页：$page\n")
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun dateFileUrl(latestUrl: String, date: String): String {
        val base = latestUrl.substringBeforeLast('/', latestUrl).removeSuffix("/")
        return "$base/$date.json"
    }

    private fun parsePapers(raw: String): Pair<String, List<PaperEntity>> {
        val root = JSONObject(raw)
        val arr = root.getJSONArray("papers")
        val date = root.optString("date", "")
        val now = System.currentTimeMillis()
        val papers = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PaperEntity(
                arxivId = o.getString("arxiv_id"),
                date = date,
                title = o.optString("title", ""),
                titleZh = o.optString("title_zh", ""),
                categories = o.optJSONArray("categories")?.let { j ->
                    (0 until j.length()).map { k -> j.getString(k) }.joinToString(",")
                } ?: "",
                authors = o.optJSONArray("authors")?.let { j ->
                    (0 until j.length()).map { k -> j.getString(k) }.joinToString(",")
                } ?: "",
                published = o.optString("published", ""),
                pdfUrl = o.optString("pdf_url", ""),
                abstract = o.optString("abstract", ""),
                summary = o.optString("summary", ""),
                fetchedAt = now
            )
        }
        return date to papers
    }

    companion object {
        private const val MAX_CONTEXT_CHARS = 40_000
    }
}
