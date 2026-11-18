package com.tvflux.pro.data.remote

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.tvflux.pro.data.db.entity.ChannelEntity
import com.tvflux.pro.data.db.entity.EpisodeEntity
import com.tvflux.pro.data.db.entity.PlaylistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SeriesInfo(
    val seriesId: Int,
    val episodes: List<EpisodeEntity>
)

class XtreamApi(
    private val client: OkHttpClient = NetworkModule.syncClient
) {

    fun buildUrl(p: PlaylistEntity, action: String, extraParams: String = ""): String {
        val base = p.serverUrl.trim().removeSuffix("/")
        val u = URLEncoder.encode(p.username.trim(), "UTF-8")
        val pw = URLEncoder.encode(p.password.trim(), "UTF-8")
        val extra = if (extraParams.isNotBlank()) "&$extraParams" else ""
        return "$base/player_api.php?username=$u&password=$pw&action=$action$extra"
    }

    /**
     * [F4] قراءة JSONArray الخاصة بالفئات الخفيفة عبر Stream قارئ آمن عبر التدفق المباشر
     */
    suspend fun executeJson(url: String): JSONArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "IBOPlayerPro/3.9 (Android TV; Linux)")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            val body = response.body ?: return@withContext JSONArray()
            val arr = JSONArray()
            body.byteStream().use { raw ->
                InputStreamReader(raw, StandardCharsets.UTF_8).buffered(16 * 1024).use { reader ->
                    val jsonReader = JsonReader(reader).apply { isLenient = true }
                    if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
                        jsonReader.beginArray()
                        while (jsonReader.hasNext()) {
                            if (jsonReader.peek() == JsonToken.BEGIN_OBJECT) {
                                val obj = JSONObject()
                                jsonReader.beginObject()
                                while (jsonReader.hasNext()) {
                                    val k = jsonReader.nextName()
                                    when (jsonReader.peek()) {
                                        JsonToken.STRING -> obj.put(k, jsonReader.nextString())
                                        JsonToken.NUMBER -> obj.put(k, jsonReader.nextString())
                                        JsonToken.BOOLEAN -> obj.put(k, jsonReader.nextBoolean())
                                        JsonToken.NULL -> {
                                            jsonReader.nextNull()
                                        }
                                        else -> jsonReader.skipValue()
                                    }
                                }
                                jsonReader.endObject()
                                arr.put(obj)
                            } else {
                                jsonReader.skipValue()
                            }
                        }
                        jsonReader.endArray()
                    }
                }
            }
            arr
        }
    }

    suspend fun getLiveCategories(p: PlaylistEntity): Map<String, String> = withContext(Dispatchers.IO) {
        parseCategoryMap(executeJson(buildUrl(p, "get_live_categories")))
    }

    suspend fun getVodCategories(p: PlaylistEntity): Map<String, String> = withContext(Dispatchers.IO) {
        parseCategoryMap(executeJson(buildUrl(p, "get_vod_categories")))
    }

    suspend fun getSeriesCategories(p: PlaylistEntity): Map<String, String> = withContext(Dispatchers.IO) {
        parseCategoryMap(executeJson(buildUrl(p, "get_series_categories")))
    }

    private fun parseCategoryMap(arr: JSONArray): Map<String, String> {
        val map = LinkedHashMap<String, String>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("category_id").trim()
            val name = obj.optString("category_name").trim()
            if (id.isNotEmpty() && name.isNotEmpty()) {
                map[id] = name
            }
        }
        return map
    }

    suspend fun streamLive(
        p: PlaylistEntity,
        cats: Map<String, String>,
        syncStart: Long,
        onBatch: suspend (List<ChannelEntity>) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val primaryCount = runCatching {
            executeStreamingChannelRequest(
                url = buildUrl(p, "get_live_streams"),
                type = ChannelEntity.TYPE_LIVE,
                p = p,
                cats = cats,
                syncStart = syncStart,
                onBatch = onBatch
            )
        }.getOrDefault(0)
        if (primaryCount > 0) return@withContext primaryCount
        executeStreamingChannelRequest(
            url = buildUrl(p, "get_live_streams", "category_id=*"),
            type = ChannelEntity.TYPE_LIVE,
            p = p,
            cats = cats,
            syncStart = syncStart,
            onBatch = onBatch
        )
    }

    suspend fun streamVod(
        p: PlaylistEntity,
        cats: Map<String, String>,
        syncStart: Long,
        onBatch: suspend (List<ChannelEntity>) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val primaryCount = runCatching {
            executeStreamingChannelRequest(
                url = buildUrl(p, "get_vod_streams"),
                type = ChannelEntity.TYPE_MOVIE,
                p = p,
                cats = cats,
                syncStart = syncStart,
                onBatch = onBatch
            )
        }.getOrDefault(0)
        if (primaryCount > 0) return@withContext primaryCount
        executeStreamingChannelRequest(
            url = buildUrl(p, "get_vod_streams", "category_id=*"),
            type = ChannelEntity.TYPE_MOVIE,
            p = p,
            cats = cats,
            syncStart = syncStart,
            onBatch = onBatch
        )
    }

    suspend fun streamSeries(
        p: PlaylistEntity,
        cats: Map<String, String>,
        syncStart: Long,
        onBatch: suspend (List<ChannelEntity>) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val primaryCount = runCatching {
            executeStreamingChannelRequest(
                url = buildUrl(p, "get_series"),
                type = ChannelEntity.TYPE_SERIES,
                p = p,
                cats = cats,
                syncStart = syncStart,
                onBatch = onBatch
            )
        }.getOrDefault(0)
        if (primaryCount > 0) return@withContext primaryCount
        executeStreamingChannelRequest(
            url = buildUrl(p, "get_series", "category_id=*"),
            type = ChannelEntity.TYPE_SERIES,
            p = p,
            cats = cats,
            syncStart = syncStart,
            onBatch = onBatch
        )
    }

    private suspend fun executeStreamingChannelRequest(
        url: String,
        type: String,
        p: PlaylistEntity,
        cats: Map<String, String>,
        syncStart: Long,
        onBatch: suspend (List<ChannelEntity>) -> Unit
    ): Int {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "IBOPlayerPro/3.9 (Android TV; Linux)")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            val body = response.body ?: return 0
            return StreamingXtreamParser.streamChannelArray(
                body = body,
                type = type,
                pid = p.playlistId,
                serverUrl = p.serverUrl,
                cats = cats,
                user = p.username,
                pass = p.password,
                syncStart = syncStart,
                onBatch = onBatch
            )
        }
    }

    /**
     * [F4 & F5] جلب حلقات مسلسل محدد بشكل كسول (Lazy) عبر Streaming JsonReader
     */
    suspend fun getSeriesInfo(p: PlaylistEntity, seriesId: Int): SeriesInfo = withContext(Dispatchers.IO) {
        val url = buildUrl(p, "get_series_info", "series_id=$seriesId")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "IBOPlayerPro/3.9 (Android TV; Linux)")
            .header("Accept", "application/json")
            .build()

        val now = System.currentTimeMillis()
        val episodes = ArrayList<EpisodeEntity>()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for series_id=$seriesId")
            }
            val body = response.body ?: return@withContext SeriesInfo(seriesId, emptyList())
            body.byteStream().use { raw ->
                InputStreamReader(raw, StandardCharsets.UTF_8).buffered(32 * 1024).use { reader ->
                    val jr = JsonReader(reader).apply { isLenient = true }
                    if (jr.peek() == JsonToken.BEGIN_OBJECT) {
                        jr.beginObject()
                        while (jr.hasNext()) {
                            when (jr.nextName()) {
                                "episodes" -> parseEpisodesContainer(jr, p, seriesId, now, episodes)
                                else -> jr.skipValue()
                            }
                        }
                        jr.endObject()
                    }
                }
            }
        }

        SeriesInfo(seriesId = seriesId, episodes = episodes)
    }

    private fun parseEpisodesContainer(
        jr: JsonReader,
        p: PlaylistEntity,
        seriesId: Int,
        fetchedAt: Long,
        out: MutableList<EpisodeEntity>
    ) {
        when (jr.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                jr.beginObject()
                while (jr.hasNext()) {
                    val seasonKey = jr.nextName()
                    val defaultSeason = seasonKey.toIntOrNull() ?: 1
                    if (jr.peek() == JsonToken.BEGIN_ARRAY) {
                        jr.beginArray()
                        var idx = 1
                        while (jr.hasNext()) {
                            if (jr.peek() == JsonToken.BEGIN_OBJECT) {
                                parseSingleEpisode(jr, p, seriesId, defaultSeason, idx++, fetchedAt)?.let { out.add(it) }
                            } else {
                                jr.skipValue()
                            }
                        }
                        jr.endArray()
                    } else {
                        jr.skipValue()
                    }
                }
                jr.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                jr.beginArray()
                var sIdx = 1
                while (jr.hasNext()) {
                    if (jr.peek() == JsonToken.BEGIN_ARRAY) {
                        jr.beginArray()
                        var eIdx = 1
                        while (jr.hasNext()) {
                            if (jr.peek() == JsonToken.BEGIN_OBJECT) {
                                parseSingleEpisode(jr, p, seriesId, sIdx, eIdx++, fetchedAt)?.let { out.add(it) }
                            } else {
                                jr.skipValue()
                            }
                        }
                        jr.endArray()
                    } else if (jr.peek() == JsonToken.BEGIN_OBJECT) {
                        parseSingleEpisode(jr, p, seriesId, 1, sIdx, fetchedAt)?.let { out.add(it) }
                    } else {
                        jr.skipValue()
                    }
                    sIdx++
                }
                jr.endArray()
            }
            else -> jr.skipValue()
        }
    }

    private fun parseSingleEpisode(
        jr: JsonReader,
        p: PlaylistEntity,
        seriesId: Int,
        defaultSeason: Int,
        defaultEpisode: Int,
        fetchedAt: Long
    ): EpisodeEntity? {
        var epId = 0
        var title = ""
        var season = defaultSeason
        var episodeNum = defaultEpisode
        var ext = "mp4"
        var directSource = ""
        var duration = ""
        var rating = ""
        var plot = ""

        jr.beginObject()
        while (jr.hasNext()) {
            when (jr.nextName()) {
                "id", "episode_id" -> {
                    val v = nextIntSafe(jr)
                    if (v > 0) epId = v
                }
                "title", "name" -> title = nextStringSafe(jr)
                "season" -> {
                    val v = nextIntSafe(jr)
                    if (v > 0) season = v
                }
                "episode_num" -> {
                    val v = nextIntSafe(jr)
                    if (v > 0) episodeNum = v
                }
                "container_extension" -> {
                    val v = nextStringSafe(jr)
                    if (v.isNotBlank()) ext = v
                }
                "direct_source" -> directSource = nextStringSafe(jr)
                "info" -> {
                    if (jr.peek() == JsonToken.BEGIN_OBJECT) {
                        jr.beginObject()
                        while (jr.hasNext()) {
                            when (jr.nextName()) {
                                "duration" -> duration = nextStringSafe(jr)
                                "rating" -> rating = nextStringSafe(jr)
                                "plot", "overview" -> plot = nextStringSafe(jr)
                                else -> jr.skipValue()
                            }
                        }
                        jr.endObject()
                    } else {
                        jr.skipValue()
                    }
                }
                else -> jr.skipValue()
            }
        }
        jr.endObject()

        if (epId <= 0 && directSource.isBlank()) return null
        val resolvedEpId = if (epId > 0) epId else (season * 10000 + episodeNum)
        val streamUrl = XtreamUrlBuilder.series(
            server = p.serverUrl,
            user = p.username,
            pass = p.password,
            episodeId = resolvedEpId,
            containerExtension = ext,
            directSource = directSource
        )

        return EpisodeEntity(
            playlistId = p.playlistId,
            seriesId = seriesId,
            episodeId = resolvedEpId,
            title = title.ifBlank { "S${season}E$episodeNum" },
            season = season,
            episode = episodeNum,
            containerExtension = ext,
            streamUrl = streamUrl,
            duration = duration,
            rating = rating,
            plot = plot,
            lastFetchedAt = fetchedAt
        )
    }

    private fun nextStringSafe(jr: JsonReader): String {
        return try {
            when (jr.peek()) {
                JsonToken.STRING -> jr.nextString().trim()
                JsonToken.NUMBER -> jr.nextString().trim()
                JsonToken.NULL -> {
                    jr.nextNull()
                    ""
                }
                else -> {
                    jr.skipValue()
                    ""
                }
            }
        } catch (_: Throwable) {
            runCatching { jr.skipValue() }
            ""
        }
    }

    private fun nextIntSafe(jr: JsonReader): Int {
        return try {
            when (jr.peek()) {
                JsonToken.NUMBER -> jr.nextInt()
                JsonToken.STRING -> jr.nextString().trim().toIntOrNull() ?: 0
                JsonToken.NULL -> {
                    jr.nextNull()
                    0
                }
                else -> {
                    jr.skipValue()
                    0
                }
            }
        } catch (_: Throwable) {
            runCatching { jr.skipValue() }
            0
        }
    }
}
