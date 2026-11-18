package com.example.data.remote

import com.example.data.model.CatalogMovie
import com.example.data.model.DetectedWebStream
import com.example.data.model.M3uChannel
import com.example.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MediaDiscoveryService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun searchArchiveMovies(query: String): Result<List<CatalogMovie>> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.trim().ifEmpty { "classic cinema" }
            val encoded = URLEncoder.encode(
                "($cleanQuery) AND mediatype:(movies) AND format:(MPEG4 OR h.264)",
                "UTF-8"
            )
            val url = "https://archive.org/advancedsearch.php?q=$encoded" +
                "&fl[]=identifier,title,description,year,downloads" +
                "&sort[]=downloads+desc&rows=12&page=1&output=json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CinemaStreamAndroid/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val docs = json.optJSONObject("response")?.optJSONArray("docs")
                    ?: return@withContext Result.success(emptyList())

                val results = mutableListOf<CatalogMovie>()
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    val identifier = doc.optString("identifier")
                    val title = doc.optString("title").takeIf { it.isNotBlank() } ?: identifier
                    if (identifier.isBlank()) continue
                    val description = doc.optString("description")
                        .replace(Regex("<[^>]*>"), " ")
                        .trim()
                        .take(120)
                        .ifEmpty { "فیلم از آرشیو عمومی اینترنت" }
                    val year = doc.optString("year", "")

                    // Direct Internet Archive download/stream redirect URL for the item's primary MP4
                    val streamUrl = "https://archive.org/download/$identifier/${identifier}_512kb.mp4"
                    val posterUrl = "https://archive.org/services/img/$identifier"

                    results.add(
                        CatalogMovie(
                            id = "ia_$identifier",
                            title = title,
                            subtitle = description,
                            streamUrl = streamUrl,
                            posterUrl = posterUrl,
                            category = "آرشیو فیلم",
                            sourceName = "Internet Archive",
                            mediaType = MediaType.MOVIE,
                            year = year,
                        )
                    )
                }
                Result.success(results)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveArchiveDirectMp4(identifier: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://archive.org/metadata/$identifier/files")
                .header("User-Agent", "CinemaStreamAndroid/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string().orEmpty())
                val files = json.optJSONArray("result") ?: return@withContext null
                var bestName: String? = null
                for (i in 0 until files.length()) {
                    val f = files.optJSONObject(i) ?: continue
                    val name = f.optString("name")
                    val format = f.optString("format")
                    if (name.endsWith(".mp4", ignoreCase = true) ||
                        format.contains("MPEG4", ignoreCase = true) ||
                        format.contains("h.264", ignoreCase = true)
                    ) {
                        if (bestName == null || !name.contains("512kb")) {
                            bestName = name
                        }
                    }
                }
                bestName?.let { "https://archive.org/download/$identifier/$it" }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchM3uPlaylist(playlistUrl: String, fallbackGroup: String): Result<List<M3uChannel>> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(playlistUrl.trim())
                    .header("User-Agent", "CinemaStreamAndroid/1.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("خطا در دریافت لیست: HTTP ${response.code}"))
                    }
                    val content = response.body?.string().orEmpty()
                    val parsed = M3uParser.parse(content, fallbackGroup)
                    if (parsed.isEmpty()) {
                        Result.failure(IllegalStateException("هیچ کانال یا لینک ویدیوی معتبری در این لیست پیدا نشد."))
                    } else {
                        Result.success(parsed)
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun extractStreamsFromWebpage(pageUrl: String): Result<List<DetectedWebStream>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = normalizeUrl(pageUrl)
                if (isDirectStreamUrl(cleanUrl)) {
                    return@withContext Result.success(
                        listOf(
                            DetectedWebStream(
                                url = cleanUrl,
                                formatBadge = inferFormatBadge(cleanUrl),
                                title = cleanUrl.substringAfterLast('/').substringBefore('?')
                                    .ifBlank { "استریم مستقیم" },
                            )
                        )
                    )
                }

                val request = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36")
                    .build()

                client.newCall(request).execute().use { response ->
                    val contentType = response.header("Content-Type").orEmpty()
                    if (contentType.startsWith("video/") ||
                        contentType.contains("mpegurl", ignoreCase = true) ||
                        contentType.contains("dash+xml", ignoreCase = true)
                    ) {
                        return@withContext Result.success(
                            listOf(
                                DetectedWebStream(
                                    url = cleanUrl,
                                    formatBadge = inferFormatBadge(cleanUrl, contentType),
                                    title = "ویدیوی مستقیم",
                                )
                            )
                        )
                    }

                    val html = response.body?.string().orEmpty()
                    val found = scanHtmlForStreams(html, cleanUrl)
                    Result.success(found)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun scanHtmlForStreams(html: String, baseUrl: String): List<DetectedWebStream> {
        val streamUrlRegex = Regex(
            """https?://[^\s"'<>\\)]+?\.(?:m3u8|mp4|mpd|webm|mkv|mov)(?:\?[^\s"'<>\\)]*)?""",
            RegexOption.IGNORE_CASE
        )
        val ogVideoRegex = Regex(
            """<meta[^>]+property=["']og:video(?::url|:secure_url)?["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        val titleRegex = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val pageTitle = titleRegex.find(html)?.groupValues?.getOrNull(1)?.trim()?.take(60)
            ?.ifBlank { null } ?: baseUrl.substringAfter("://").substringBefore('/')

        val urls = linkedSetOf<String>()
        ogVideoRegex.findAll(html).forEach { match ->
            match.groupValues.getOrNull(1)?.let { urls.add(it.replace("&amp;", "&")) }
        }
        streamUrlRegex.findAll(html).forEach { match ->
            urls.add(match.value.replace("&amp;", "&"))
        }

        return urls.take(25).mapIndexed { index, url ->
            DetectedWebStream(
                url = url,
                formatBadge = inferFormatBadge(url),
                title = if (urls.size == 1) pageTitle else "$pageTitle (${index + 1})",
            )
        }
    }

    companion object {
        fun normalizeUrl(raw: String): String {
            val trimmed = raw.trim()
            return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }

        fun isDirectStreamUrl(url: String): Boolean {
            val lower = url.lowercase().substringBefore('?')
            return lower.endsWith(".m3u8") ||
                lower.endsWith(".mp4") ||
                lower.endsWith(".mpd") ||
                lower.endsWith(".webm") ||
                lower.endsWith(".mkv") ||
                lower.endsWith(".mov")
        }

        fun inferFormatBadge(url: String, contentType: String = ""): String {
            val lower = url.lowercase()
            return when {
                lower.contains(".m3u8") || contentType.contains("mpegurl", true) -> "HLS"
                lower.contains(".mpd") || contentType.contains("dash", true) -> "DASH"
                lower.contains(".webm") -> "WEBM"
                lower.contains(".mkv") -> "MKV"
                else -> "MP4"
            }
        }
    }
}
