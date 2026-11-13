package com.tvflux.pro

import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * PLAYER VIP v27.0 — Dedicated StateFlow Architecture ViewModel & Request Coordinator:
 * - Holds massive catalog lists (`allChannels`, `allMovies`, `allSeries`, `pagedList`) inside
 *   `StateFlow` streams collected in Compose via `collectAsStateWithLifecycle()`.
 * - Survives Android configuration changes and prevents UI recomposition storms.
 */
class LiveProViewModel : ViewModel() {
    private val syncRequestSequence = AtomicLong(0L)
    private val playbackRequestSequence = AtomicLong(0L)

    private val _allChannels = MutableStateFlow<List<TvChannel>>(emptyList())
    val allChannels: StateFlow<List<TvChannel>> = _allChannels.asStateFlow()

    private val _allMovies = MutableStateFlow<List<TvChannel>>(emptyList())
    val allMovies: StateFlow<List<TvChannel>> = _allMovies.asStateFlow()

    private val _allSeries = MutableStateFlow<List<TvChannel>>(emptyList())
    val allSeries: StateFlow<List<TvChannel>> = _allSeries.asStateFlow()

    private val _pagedList = MutableStateFlow<List<TvChannel>>(emptyList())
    val pagedList: StateFlow<List<TvChannel>> = _pagedList.asStateFlow()

    fun updateAllChannels(list: List<TvChannel>) {
        _allChannels.value = list
    }

    fun updateAllMovies(list: List<TvChannel>) {
        _allMovies.value = list
    }

    fun updateAllSeries(list: List<TvChannel>) {
        _allSeries.value = list
    }

    fun updatePagedList(list: List<TvChannel>) {
        _pagedList.value = list
    }

    fun appendPagedList(more: List<TvChannel>) {
        if (more.isEmpty()) return
        _pagedList.value = (_pagedList.value + more).distinctBy { it.stableKey }
    }

    var activeTabState by mutableStateOf(MainTab.LIVE)
    var activeOverlayState by mutableStateOf(ProOverlayScreen.NONE)
    var selectedLiveBouquet by mutableStateOf("الكل")
    var selectedMovieGroup by mutableStateOf("الكل")
    var selectedSeriesGroup by mutableStateOf("الكل")
    var lastWatchedStableKey by mutableStateOf<String?>(null)
    var activePlayingChannel by mutableStateOf<TvChannel?>(null)
    var activeDetailsChannel by mutableStateOf<TvChannel?>(null)
    var activeResumePosMs by mutableStateOf(0L)
    var searchDebouncedQuery by mutableStateOf("")

    fun nextSyncToken(): Long = syncRequestSequence.incrementAndGet()
    fun isSyncTokenValid(token: Long): Boolean = syncRequestSequence.get() == token

    fun nextPlaybackToken(channelKey: String): Long {
        lastWatchedStableKey = channelKey
        PlaybackSessionGuard.beginSession(channelKey)
        return playbackRequestSequence.incrementAndGet()
    }

    fun isPlaybackTokenValid(token: Long): Boolean = playbackRequestSequence.get() == token
}
