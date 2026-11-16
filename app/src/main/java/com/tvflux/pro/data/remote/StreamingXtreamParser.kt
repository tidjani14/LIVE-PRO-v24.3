package com.tvflux.pro.data.remote

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.tvflux.pro.data.db.entity.ChannelEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.ResponseBody
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object StreamingXtreamParser {

    const val BATCH_SIZE = 500

    /**
     * قراءة متدفقة (O(1) RAM) عبر Gson JsonReader مباشرة من ResponseBody.byteStream()
     * دون قراءة الاستجابة دفعة واحدة في الذاكرة، وتفريغ كل 500 عنصر عبر onBatch.
     */
    suspend fun streamChannelArray(
        body: ResponseBody,
        type: String,
        pid: String,
        serverUrl: String,
        cats: Map<String, String>,
        user: String,
        pass: String,
        syncStart: Long,
        onBatch: suspend (List<ChannelEntity>) -> Unit
    ): Int {
        var totalParsed = 0
        val batch = ArrayList<ChannelEntity>(BATCH_SIZE)

        body.byteStream().use { rawStream ->
            InputStreamReader(rawStream, StandardCharsets.UTF_8).buffered(64 * 1024).use { bufferedReader ->
                val reader = JsonReader(bufferedReader).apply {
                    isLenient = true
                }

                when (reader.peek()) {
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            currentCoroutineContext().ensureActive()
                            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                val entity = parseSingleChannelObject(
                                    reader = reader,
                                    type = type,
                                    pid = pid,
                                    serverUrl = serverUrl,
                                    cats = cats,
                                    user = user,
                                    pass = pass,
                                    syncStart = syncStart,
                                    fallbackSequence = totalParsed + 1
                                )
                                if (entity != null) {
                                    batch.add(entity)
                                    totalParsed++
                                    if (batch.size >= BATCH_SIZE) {
                                        onBatch(ArrayList(batch))
                                        batch.clear()
                                    }
                                }
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endArray()
                    }
                    JsonToken.BEGIN_OBJECT -> {
                        // دعم لوحات Xtream التي تُرجع كائناً مفهرساً بدلاً من مصفوفة JSONArray
                        reader.beginObject()
                        while (reader.hasNext()) {
                            currentCoroutineContext().ensureActive()
                            reader.nextName()
                            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                val entity = parseSingleChannelObject(
                                    reader = reader,
                                    type = type,
                                    pid = pid,
                                    serverUrl = serverUrl,
                                    cats = cats,
                                    user = user,
                                    pass = pass,
                                    syncStart = syncStart,
                                    fallbackSequence = totalParsed + 1
                                )
                                if (entity != null) {
                                    batch.add(entity)
                                    totalParsed++
                                    if (batch.size >= BATCH_SIZE) {
                                        onBatch(ArrayList(batch))
                                        batch.clear()
                                    }
                                }
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    else -> {
                        reader.skipValue()
                    }
                }
            }
        }

        if (batch.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            onBatch(ArrayList(batch))
            batch.clear()
        }

        return totalParsed
    }

    private fun parseSingleChannelObject(
        reader: JsonReader,
        type: String,
        pid: String,
        serverUrl: String,
        cats: Map<String, String>,
        user: String,
        pass: String,
        syncStart: Long,
        fallbackSequence: Int
    ): ChannelEntity? {
        var streamId = 0
        var name = ""
        var logo = ""
        var categoryId = ""
        var categoryNameFromObj = ""
        var number = fallbackSequence
        var epgChannelId = ""
        var rating = ""
        var added = ""
        var containerExt = "mp4"
        var directSource = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "stream_id", "vod_id", "series_id", "id" -> {
                    val v = nextIntSafe(reader)
                    if (v > 0 && streamId == 0) streamId = v
                }
                "name", "title" -> {
                    val v = nextStringSafe(reader)
                    if (v.isNotBlank() && name.isEmpty()) name = v
                }
                "stream_icon", "cover", "cover_big", "movie_image" -> {
                    val v = nextStringSafe(reader)
                    if (v.isNotBlank() && logo.isEmpty()) logo = v
                }
                "category_id" -> {
                    val v = nextStringSafe(reader)
                    if (v.isNotBlank()) categoryId = v
                }
                "category_name" -> {
                    val v = nextStringSafe(reader)
                    if (v.isNotBlank()) categoryNameFromObj = v
                }
                "num" -> {
                    val v = nextIntSafe(reader)
                    if (v > 0) number = v
                }
                "epg_channel_id" -> {
                    epgChannelId = nextStringSafe(reader)
                }
                "rating", "rating_5based" -> {
                    val v = nextStringSafe(reader)
                    if (v.isNotBlank() && rating.isEmpty()) rating = v
                }
                "added", "last_modified" -> {
                    val v = nextStringSafe(reader)
                    if (v.isNotBlank() && added.isEmpty()) added = v
                }
                "container_extension" -> {
                    val v = nextStringSafe(reader)
                    if (v.isNotBlank()) containerExt = v
                }
                "direct_source" -> {
                    directSource = nextStringSafe(reader)
                }
                else -> {
                    reader.skipValue()
                }
            }
        }
        reader.endObject()

        if (streamId <= 0 && directSource.isBlank()) return null

        val resolvedStreamId = if (streamId > 0) streamId else fallbackSequence
        val resolvedName = name.ifBlank { "$type #$resolvedStreamId" }
        val resolvedCatName = categoryNameFromObj.ifBlank { cats[categoryId] ?: "General" }

        // [F1] بناء الرابط الصحيح فوراً داخل الـ Parser حسب النوع
        val builtUrl = when (type) {
            ChannelEntity.TYPE_LIVE -> XtreamUrlBuilder.live(
                server = serverUrl,
                user = user,
                pass = pass,
                streamId = resolvedStreamId,
                directSource = directSource
            )
            ChannelEntity.TYPE_MOVIE -> XtreamUrlBuilder.movie(
                server = serverUrl,
                user = user,
                pass = pass,
                streamId = resolvedStreamId,
                containerExtension = containerExt,
                directSource = directSource
            )
            ChannelEntity.TYPE_SERIES -> XtreamUrlBuilder.series(
                server = serverUrl,
                user = user,
                pass = pass,
                episodeId = resolvedStreamId,
                containerExtension = containerExt,
                directSource = directSource
            )
            else -> XtreamUrlBuilder.live(
                server = serverUrl,
                user = user,
                pass = pass,
                streamId = resolvedStreamId,
                directSource = directSource
            )
        }

        return ChannelEntity(
            playlistId = pid,
            streamId = resolvedStreamId,
            name = resolvedName,
            url = builtUrl,
            logo = logo,
            categoryId = categoryId,
            categoryName = resolvedCatName,
            contentType = type,
            number = number,
            epgChannelId = epgChannelId,
            rating = rating,
            added = added,
            lastSyncedAt = syncStart
        )
    }

    private fun nextStringSafe(reader: JsonReader): String {
        return try {
            when (reader.peek()) {
                JsonToken.STRING -> reader.nextString().trim()
                JsonToken.NUMBER -> reader.nextString().trim()
                JsonToken.BOOLEAN -> {
                    reader.nextBoolean()
                    ""
                }
                JsonToken.NULL -> {
                    reader.nextNull()
                    ""
                }
                else -> {
                    reader.skipValue()
                    ""
                }
            }
        } catch (_: Throwable) {
            runCatching { reader.skipValue() }
            ""
        }
    }

    private fun nextIntSafe(reader: JsonReader): Int {
        return try {
            when (reader.peek()) {
                JsonToken.NUMBER -> reader.nextInt()
                JsonToken.STRING -> reader.nextString().trim().toIntOrNull() ?: 0
                JsonToken.NULL -> {
                    reader.nextNull()
                    0
                }
                else -> {
                    reader.skipValue()
                    0
                }
            }
        } catch (_: Throwable) {
            runCatching { reader.skipValue() }
            0
        }
    }
}
