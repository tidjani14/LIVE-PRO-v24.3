package com.tvflux.pro.ui

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvflux.pro.data.db.AppDatabase
import com.tvflux.pro.data.db.entity.ChannelEntity
import com.tvflux.pro.data.db.entity.EpisodeEntity
import com.tvflux.pro.data.db.entity.PlaylistEntity
import com.tvflux.pro.data.remote.NetworkModule
import com.tvflux.pro.data.remote.XtreamApi
import com.tvflux.pro.data.sync.SyncEvent
import com.tvflux.pro.data.sync.SyncOrchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class UiState(
    val isBooting: Boolean = true,
    val isSyncingLive: Boolean = false,
    val isSyncingBackgroundVod: Boolean = false,
    val vodLoadedProgress: Int = 0,
    val seriesLoadedProgress: Int = 0,
    val snackbarMessage: String? = null,
    val errorMessage: String? = null,
    val isLoadingEpisodes: Boolean = false,
    val currentSeriesEpisodes: List<EpisodeEntity> = emptyList()
)

class CatalogViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val orchestrator = SyncOrchestrator(
        db = db,
        api = XtreamApi(NetworkModule.syncClient)
    )

    private val _activePid = MutableStateFlow<String?>(null)
    val activePid: StateFlow<String?> = _activePid.asStateFlow()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    // القراءة الحصرية من Room عبر Flow + flatMapLatest
    val liveTv: StateFlow<List<ChannelEntity>> = _activePid
        .flatMapLatest { pid ->
            if (pid.isNullOrBlank()) emptyFlow() else db.channelDao().observeLive(pid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val movies: StateFlow<List<ChannelEntity>> = _activePid
        .flatMapLatest { pid ->
            if (pid.isNullOrBlank()) emptyFlow() else db.channelDao().observeMovies(pid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val series: StateFlow<List<ChannelEntity>> = _activePid
        .flatMapLatest { pid ->
            if (pid.isNullOrBlank()) emptyFlow() else db.channelDao().observeSeries(pid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        observeEvents()
    }

    private fun observeEvents() {
        viewModelScope.launch {
            orchestrator.events.collect { event ->
                when (event) {
                    is SyncEvent.Phase -> {
                        if (event.phaseName == "PHASE_A_LIVE") {
                            _ui.update {
                                it.copy(
                                    isSyncingLive = true,
                                    isSyncingBackgroundVod = false,
                                    errorMessage = null
                                )
                            }
                        } else if (event.phaseName == "PHASE_B_VOD_SERIES") {
                            _ui.update {
                                it.copy(
                                    isBooting = false,
                                    isSyncingLive = false,
                                    isSyncingBackgroundVod = true
                                )
                            }
                        }
                    }
                    is SyncEvent.LiveReady -> {
                        _ui.update {
                            it.copy(
                                isBooting = false,
                                isSyncingLive = false,
                                isSyncingBackgroundVod = true,
                                snackbarMessage = "⚡ ${event.count} قناة جاهزة — ابدأ المشاهدة"
                            )
                        }
                    }
                    is SyncEvent.VodProgress -> {
                        _ui.update {
                            it.copy(
                                isSyncingBackgroundVod = true,
                                vodLoadedProgress = event.loadedCount
                            )
                        }
                    }
                    is SyncEvent.SeriesProgress -> {
                        _ui.update {
                            it.copy(
                                isSyncingBackgroundVod = true,
                                seriesLoadedProgress = event.loadedCount
                            )
                        }
                    }
                    is SyncEvent.AllDone -> {
                        _ui.update {
                            it.copy(
                                isBooting = false,
                                isSyncingLive = false,
                                isSyncingBackgroundVod = false
                            )
                        }
                    }
                    is SyncEvent.Cancelled -> {
                        _ui.update {
                            it.copy(
                                isBooting = false,
                                isSyncingLive = false,
                                isSyncingBackgroundVod = false
                            )
                        }
                    }
                    is SyncEvent.Error -> {
                        _ui.update {
                            it.copy(
                                isBooting = false,
                                isSyncingLive = false,
                                isSyncingBackgroundVod = false,
                                errorMessage = event.message
                            )
                        }
                    }
                }
            }
        }
    }

    fun openPlaylist(
        pid: String,
        serverUrl: String = "",
        username: String = "",
        password: String = "",
        serverName: String = ""
    ) {
        _activePid.value = pid
        viewModelScope.launch(Dispatchers.IO) {
            val existingCount = db.channelDao().countByType(pid, ChannelEntity.TYPE_LIVE)
            if (existingCount > 0) {
                // Instant Boot من Room في أقل من 800ms بدون انتظار الشبكة
                _ui.update { it.copy(isBooting = false) }
            } else {
                _ui.update { it.copy(isBooting = true) }
            }

            val stored = db.playlistDao().getPlaylist(pid)
            val entity = stored ?: PlaylistEntity(
                playlistId = pid,
                serverUrl = serverUrl,
                username = username,
                password = password,
                serverName = serverName.ifBlank { pid }
            )
            if (entity.serverUrl.isNotBlank() && entity.username.isNotBlank()) {
                orchestrator.startSync(entity, force = false)
            } else {
                _ui.update { it.copy(isBooting = false) }
            }
        }
    }

    fun manualRefresh() {
        val pid = _activePid.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val stored = db.playlistDao().getPlaylist(pid) ?: return@launch
            orchestrator.startSync(stored, force = true)
        }
    }

    // [F5] تحميل كسول لحلقات المسلسل عند فتحه فقط مع احترام TTL = 1 ساعة وحالة التحميل
    fun loadSeriesEpisodesLazy(seriesId: Int) {
        val pid = _activePid.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(isLoadingEpisodes = true, currentSeriesEpisodes = emptyList()) }
            val stored = db.playlistDao().getPlaylist(pid)
            if (stored == null) {
                _ui.update { it.copy(isLoadingEpisodes = false) }
                return@launch
            }
            val episodes = runCatching {
                orchestrator.fetchSeriesEpisodesLazy(stored, seriesId)
            }.getOrDefault(emptyList())
            _ui.update {
                it.copy(
                    isLoadingEpisodes = false,
                    currentSeriesEpisodes = episodes
                )
            }
        }
    }

    fun dismissSnackbar() {
        _ui.update { it.copy(snackbarMessage = null) }
    }

    fun cancelCurrentSync() {
        orchestrator.cancelSync()
    }

    // [F6] إلغاء المزامنة النشطة فوراً عند إغلاق الـ ViewModel
    override fun onCleared() {
        orchestrator.cancelSync()
        super.onCleared()
    }
}
