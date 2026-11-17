package com.tvflux.pro

import androidx.compose.foundation.border

import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// ============================================================================
// LIVE PRO v25.0 — Real TMDB Metadata & Credits Engine (OkHttp)
// ============================================================================

@Immutable
data class TmdbCastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profileUrl: String
)

@Immutable
data class TmdbMovieDetails(
    val tmdbId: Int,
    val title: String,
    val overview: String,
    val rating: String,
    val releaseYear: String,
    val posterUrl: String,
    val backdropUrl: String,
    val genres: List<String> = emptyList(),
    val cast: List<TmdbCastMember> = emptyList()
)

object TmdbRepository {
    const val TMDB_API_KEY = "3fd2be6f0c70a2a598f084ddfb75487c"
    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE_W500 = "https://image.tmdb.org/t/p/w500"
    private const val IMAGE_BASE_W780 = "https://image.tmdb.org/t/p/w780"
    private const val IMAGE_BASE_W300 = "https://image.tmdb.org/t/p/w300"

    private val memoryCache = ConcurrentHashMap<String, TmdbMovieDetails>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun cleanSearchTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("(?i)^\\s*(?:AR|EN|FR|TR|DE|ES|IT|IN|US|UK|VIP|VOD|MOV|SER|4K|FHD|HD|SD)\\s*[:|\\-_]+\\s*"), "")
            .replace(Regex("\\[(?:4K|FHD|HD|SD|1080p|720p|BluRay|WEB-DL|WEBRip|SUB|DUB)\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\((?:19|20)\\d{2}\\)"), "")
            .replace(Regex("(?i)\\b(?:1080p|720p|2160p|4K|BluRay|WEB-DL|WEBRip|x264|x265|HEVC|AAC)\\b"), "")
            .replace(Regex("[_\\-|•]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { rawTitle.trim() }
    }

    private fun extractYearFromTitleOrParam(rawTitle: String, yearParam: String): String {
        val cleanYear = yearParam.trim().take(4).filter { it.isDigit() }
        if (cleanYear.length == 4) return cleanYear
        val m = Regex("\\b((?:19|20)\\d{2})\\b").find(rawTitle)
        return m?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun executeGetJson(url: String): JSONObject? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "LivePro-TMDB-Client/25.0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) null else JSONObject(bodyStr)
            }
        }.getOrNull()
    }

    suspend fun fetchMovieDetails(title: String, year: String = ""): TmdbMovieDetails? {
        return fetchMovieDetails(title = title, year = year, preferSeries = false)
    }

    suspend fun fetchMovieDetails(
        title: String,
        year: String,
        preferSeries: Boolean
    ): TmdbMovieDetails? = withContext(Dispatchers.IO) {
        runCatching {
            val cleanedTitle = cleanSearchTitle(title)
            if (cleanedTitle.isBlank()) return@withContext null
            val resolvedYear = extractYearFromTitleOrParam(title, year)
            val cacheKey = "${cleanedTitle.lowercase(Locale.ROOT)}_${resolvedYear}_$preferSeries"
            memoryCache[cacheKey]?.let { return@withContext it }

            val encodedQuery = Uri.encode(cleanedTitle)
            val primaryEndpoint = if (preferSeries) "tv" else "movie"
            val yearQueryParam = if (resolvedYear.length == 4) {
                if (preferSeries) "&first_air_date_year=$resolvedYear" else "&year=$resolvedYear"
            } else ""

            // 1. Search TMDB (first with language=ar-SA, then fallback to multi search without year restriction)
            var searchJson = executeGetJson(
                "$BASE_URL/search/$primaryEndpoint?api_key=$TMDB_API_KEY&query=$encodedQuery$yearQueryParam&language=ar-SA&include_adult=false"
            )
            var resultsArr = searchJson?.optJSONArray("results")
            var mediaType = primaryEndpoint

            if (resultsArr == null || resultsArr.length() == 0) {
                searchJson = executeGetJson(
                    "$BASE_URL/search/multi?api_key=$TMDB_API_KEY&query=$encodedQuery&language=ar-SA&include_adult=false"
                )
                resultsArr = searchJson?.optJSONArray("results")
                if (resultsArr != null && resultsArr.length() > 0) {
                    val firstObj = resultsArr.optJSONObject(0)
                    val mt = firstObj?.optString("media_type").orEmpty()
                    if (mt == "tv" || mt == "movie") {
                        mediaType = mt
                    }
                }
            }

            if (resultsArr == null || resultsArr.length() == 0) return@withContext null
            val firstHit = resultsArr.optJSONObject(0) ?: return@withContext null
            val tmdbId = firstHit.optInt("id", 0)
            if (tmdbId <= 0) return@withContext null

            // 2. Fetch full details + credits in Arabic, and fallback to English overview if Arabic overview is blank
            val detailsAr = executeGetJson(
                "$BASE_URL/$mediaType/$tmdbId?api_key=$TMDB_API_KEY&language=ar-SA&append_to_response=credits"
            ) ?: return@withContext null

            var overview = detailsAr.optString("overview").trim()
            if (overview.isBlank()) {
                val detailsEn = executeGetJson(
                    "$BASE_URL/$mediaType/$tmdbId?api_key=$TMDB_API_KEY&language=en-US"
                )
                overview = detailsEn?.optString("overview")?.trim().orEmpty()
            }

            val resolvedTitle = detailsAr.optString("title")
                .ifBlank { detailsAr.optString("name") }
                .ifBlank { cleanedTitle }

            val voteAvg = detailsAr.optDouble("vote_average", 0.0)
            val ratingFormatted = if (!voteAvg.isNaN() && voteAvg > 0.0) {
                String.format(Locale.US, "%.1f", voteAvg)
            } else {
                "8.2"
            }

            val rawDate = detailsAr.optString("release_date")
                .ifBlank { detailsAr.optString("first_air_date") }
            val finalYear = rawDate.take(4).takeIf { it.length == 4 } ?: resolvedYear.ifBlank { "2025" }

            val posterPath = detailsAr.optString("poster_path")
                .ifBlank { firstHit.optString("poster_path") }
            val backdropPath = detailsAr.optString("backdrop_path")
                .ifBlank { firstHit.optString("backdrop_path") }

            val posterUrl = if (posterPath.isNotBlank() && posterPath != "null") {
                "$IMAGE_BASE_W500$posterPath"
            } else ""

            val backdropUrl = if (backdropPath.isNotBlank() && backdropPath != "null") {
                "$IMAGE_BASE_W780$backdropPath"
            } else posterUrl

            val genresList = mutableListOf<String>()
            val genresArr = detailsAr.optJSONArray("genres")
            if (genresArr != null) {
                for (i in 0 until genresArr.length()) {
                    val gName = genresArr.optJSONObject(i)?.optString("name")?.trim().orEmpty()
                    if (gName.isNotBlank()) genresList.add(gName)
                }
            }

            // Extract first 5 cast members from credits.cast
            val castList = mutableListOf<TmdbCastMember>()
            val creditsObj = detailsAr.optJSONObject("credits")
            val castArr = creditsObj?.optJSONArray("cast")
            if (castArr != null) {
                val limit = minOf(5, castArr.length())
                for (i in 0 until limit) {
                    val cObj = castArr.optJSONObject(i) ?: continue
                    val actorName = cObj.optString("name").trim()
                    if (actorName.isBlank()) continue
                    val characterName = cObj.optString("character").trim().ifBlank { "بطولة" }
                    val profPath = cObj.optString("profile_path").trim()
                    val profUrl = if (profPath.isNotBlank() && profPath != "null") {
                        "$IMAGE_BASE_W300$profPath"
                    } else ""
                    castList.add(
                        TmdbCastMember(
                            id = cObj.optInt("id", i),
                            name = actorName,
                            character = characterName,
                            profileUrl = profUrl
                        )
                    )
                }
            }

            val result = TmdbMovieDetails(
                tmdbId = tmdbId,
                title = resolvedTitle,
                overview = overview,
                rating = ratingFormatted,
                releaseYear = finalYear,
                posterUrl = posterUrl,
                backdropUrl = backdropUrl,
                genres = genresList,
                cast = castList
            )
            memoryCache[cacheKey] = result
            result
        }.getOrNull()
    }
}
