package com.papersnap.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
}

class PaperRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    val prefs = Prefs(appContext)

    fun papersForDate(date: String): Flow<List<PaperEntity>> = db.paperDao().papersForDate(date)
    fun bookmarkedPapers(): Flow<List<PaperEntity>> = db.paperDao().bookmarkedPapers()
    fun allPapers(): Flow<List<PaperEntity>> = db.paperDao().allPapers()
    fun availableDates(): Flow<List<String>> = db.paperDao().availableDates()
    fun chatMessages(id: String): Flow<List<ChatMessageEntity>> = db.chatDao().messagesForPaper(id)

    /** 加载最新一期：配置了数据源则拉 latest.json，否则读内置资产。 */
    suspend fun loadLatest() {
        val url = prefs.dataUrl
        if (url.isNotBlank()) {
            val raw = withContext(Dispatchers.IO) { fetchFromNetwork(url) }
            val (_, papers) = parsePapers(raw)
            db.paperDao().upsertAll(papers)
        } else {
            assetDates().forEach { d ->
                if (db.paperDao().countForDate(d) == 0) loadDate(d)
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

    suspend fun clearCache() {
        db.paperDao().clearAll()
        db.chatDao().clearAll()
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
}
