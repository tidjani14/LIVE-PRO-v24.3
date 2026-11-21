package com.tvflux.pro

import android.content.pm.ActivityInfo

import android.content.res.Configuration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

import android.app.Activity
import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.max

data class TrackOption(
    val title: String,
    val subtitle: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean,
    /** Special: -1 groupIndex means "disable this track type" (e.g. subtitles Off). */
    val isDisableOption: Boolean = false
)

/** Human-friendly language label for audio/subtitle tracks (ar/en/fr…). */
private fun friendlyTrackLanguageLabel(rawLang: String?, rawLabel: String? = null): String {
    val code = rawLang?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val fromCode = when {
        code.isBlank() || code == "und" || code == "null" -> null
        code.startsWith("ar") || code == "ara" -> "العربية"
        code.startsWith("en") || code == "eng" -> "English"
        code.startsWith("fr") || code == "fra" || code == "fre" -> "Français"
        code.startsWith("es") || code == "spa" -> "Español"
        code.startsWith("de") || code == "deu" || code == "ger" -> "Deutsch"
        code.startsWith("it") || code == "ita" -> "Italiano"
        code.startsWith("tr") || code == "tur" -> "Türkçe"
        code.startsWith("pt") || code == "por" -> "Português"
        code.startsWith("ru") || code == "rus" -> "Русский"
        code.startsWith("hi") || code == "hin" -> "हिन्दी"
        code.startsWith("ur") || code == "urd" -> "اردو"
        code.startsWith("nl") || code == "dut" || code == "nld" -> "Nederlands"
        code.startsWith("pl") || code == "pol" -> "Polski"
        code.startsWith("ro") || code == "ron" || code == "rum" -> "Română"
        code.startsWith("zh") || code == "chi" || code == "zho" -> "中文"
        code.startsWith("ja") || code == "jpn" -> "日本語"
        code.startsWith("ko") || code == "kor" -> "한국어"
        else -> code.uppercase(Locale.ROOT)
    }
    val label = rawLabel?.trim()?.takeIf { it.isNotBlank() }
    return when {
        fromCode != null && label != null && !label.equals(fromCode, true) && !label.equals(code, true) ->
            "$fromCode ($label)"
        fromCode != null -> fromCode
        label != null -> label
        else -> "غير معروف"
    }
}

/**
 * Hybrid User-Agent Rotation (v18.0):
 * Starts with a widely accepted Android player profile so IPTV servers accept the stream
 * immediately on attempt #1, and rotates through compatible profiles on fallback retries.
 */
private val STREAM_USER_AGENTS = IboPlayerProfileSpoofer.STREAM_PLAYBACK_USER_AGENTS

@OptIn(UnstableApi::class)
private fun OkHttpDataSource.Factory.setFollowRedirects(follow: Boolean): OkHttpDataSource.Factory = this

@OptIn(UnstableApi::class)
private fun OkHttpDataSource.Factory.setFollowSslRedirects(followSsl: Boolean): OkHttpDataSource.Factory = this

private class FastRefreshDashManifestParser : DashManifestParser() {
    override fun parse(uri: Uri, inputStream: InputStream): DashManifest {
        val rawBytes = inputStream.readBytes()
        val xmlText = String(rawBytes, Charsets.UTF_8)
        val patchedXml = if (xmlText.contains("type=\"dynamic\"", ignoreCase = true)) {
            if (xmlText.contains("minimumUpdatePeriod=", ignoreCase = true)) {
                xmlText.replace(
                    Regex("minimumUpdatePeriod\\s*=\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE),
                    "minimumUpdatePeriod=\"PT2.4S\""
                )
            } else {
                xmlText.replaceFirst(
                    Regex("<MPD\\b", RegexOption.IGNORE_CASE),
                    "<MPD minimumUpdatePeriod=\"PT2.4S\""
                )
            }
        } else {
            xmlText
        }
        return super.parse(uri, ByteArrayInputStream(patchedXml.toByteArray(Charsets.UTF_8)))
    }
}

@OptIn(UnstableApi::class)
private fun buildClearKeyDrmManager(drmKeyString: String): DefaultDrmSessionManager? {
    val clean = drmKeyString.trim()
    if (clean.isEmpty()) return null
    return runCatching {
        val jwkJson = if (clean.startsWith("{")) {
            clean
        } else if (clean.contains(":")) {
            val kidHex = clean.substringBefore(":").trim()
            val keyHex = clean.substringAfter(":").trim()
            val kidB64 = hexToBase64Url(kidHex)
            val keyB64 = hexToBase64Url(keyHex)
            "{\"keys\":[{\"kty\":\"oct\",\"k\":\"$keyB64\",\"kid\":\"$kidB64\"}],\"type\":\"temporary\"}"
        } else {
            return null
        }
        val callback = LocalMediaDrmCallback(jwkJson.toByteArray(Charsets.UTF_8))
        DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(false)
            .build(callback)
    }.getOrNull()
}

private fun hexToBase64Url(hex: String): String {
    val clean = hex.replace("-", "").trim()
    val len = clean.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len - 1) {
        data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
        i += 2
    }
    return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

// Solution 4.2: Safe ExoPlayer Wrappers to prevent IllegalStateException after release or during state transitions
@OptIn(UnstableApi::class)
fun ExoPlayer.safePlay() {
    runCatching {
        if (playbackState == Player.STATE_IDLE) {
            prepare()
        }
        play()
    }
}

@OptIn(UnstableApi::class)
fun ExoPlayer.safePause() {
    runCatching { pause() }
}

@OptIn(UnstableApi::class)
fun ExoPlayer.safeStop() {
    runCatching {
        playWhenReady = false
        stop()
    }
}

@OptIn(UnstableApi::class)
fun ExoPlayer.safeSeekTo(pos: Long) {
    if (pos < 0L) return
    runCatching { seekTo(pos) }
}

/**
 * Zero-Interruption Shared Live Session Bridge (Mini Preview <-> Fullscreen Handover):
 * Keeps the active ExoPlayer instance, HTTP socket, and 36s–65s RAM buffer 100% alive when
 * expanding from Mini Preview to Fullscreen (`PlayerScreen`) and when returning from Fullscreen
 * back to Mini Preview (`LiveMiniPreviewPlayer`), switching `PlayerView` surfaces in 0ms without
 * ever stopping or re-buffering the live stream!
 */
@OptIn(UnstableApi::class)
object SharedLiveSessionBridge {
    @Volatile
    var anchoredViewModel: LiveProViewModel? = null
    var sharedPlayer: ExoPlayer?
        get() = anchoredViewModel?.sharedExoPlayer ?: _fallbackSharedPlayer
        set(value) {
            _fallbackSharedPlayer = value
            anchoredViewModel?.sharedExoPlayer = value
        }
    @Volatile
    private var _fallbackSharedPlayer: ExoPlayer? = null
    @Volatile
    var currentBoundView: PlayerView? = null
    @Volatile
    var activeStableKey: String = ""
    @Volatile
    var activeStreamUrl: String = ""
    @Volatile
    var handoverInProgress: Boolean = false

    val isSurfaceSwitching = java.util.concurrent.atomic.AtomicBoolean(false)
    val forceSharedSoftwareVideoCodec = java.util.concurrent.atomic.AtomicBoolean(false)
    var isInPipModeState by mutableStateOf(false)

    @Volatile
    private var sharedHighSpeedBandwidthMeter: DefaultBandwidthMeter? = null

    fun getHighQualityBandwidthMeter(context: Context): DefaultBandwidthMeter {
        return sharedHighSpeedBandwidthMeter ?: synchronized(this) {
            sharedHighSpeedBandwidthMeter ?: DefaultBandwidthMeter.Builder(context.applicationContext)
                .setInitialBitrateEstimate(50_000_000L)
                .setResetOnNetworkTypeChange(false)
                .build()
                .also { sharedHighSpeedBandwidthMeter = it }
        }
    }

    private val bridgeMainScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
    )
    private val bridgeIoScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    private var surfaceTransitionJob: kotlinx.coroutines.Job? = null
    private var pendingReleaseJob: kotlinx.coroutines.Job? = null
    private val trackedPlayers = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<ExoPlayer, Boolean>())

    fun registerAndEnforceSingleActivePlayer(activePlayer: ExoPlayer) {
        trackedPlayers.add(activePlayer)
        val others = trackedPlayers.filter { it !== activePlayer }
        for (other in others) {
            trackedPlayers.remove(other)
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                runCatching {
                    other.volume = 0f
                    other.playWhenReady = false
                    other.stop()
                    other.clearMediaItems()
                    other.release()
                }
            } else {
                surfaceMainHandler.post {
                    runCatching {
                        other.volume = 0f
                        other.playWhenReady = false
                        other.stop()
                        other.clearMediaItems()
                        other.release()
                    }
                }
            }
        }
    }

    @Volatile
    private var sharedPlaybackClient: okhttp3.OkHttpClient? = null

    fun getPlaybackOkHttpClient(): okhttp3.OkHttpClient {
        return sharedPlaybackClient ?: synchronized(this) {
            sharedPlaybackClient ?: ChannelRepository.streamHttpClient.newBuilder()
                .connectTimeout(12_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(20_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
                .also { sharedPlaybackClient = it }
        }
    }

    fun onPipModeChanged(isPip: Boolean) {
        isInPipModeState = isPip
        runCatching {
            currentBoundView?.useController = false
        }
    }

    /**
     * All PlayerView ownership changes are serialized on the main thread.
     * Never silently drop an attach request: Compose may call this while an
     * AndroidView is being created/disposed during a fullscreen transition.
     */
    private val surfaceMainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun enforceMiniPlayerFullBounds(view: PlayerView?) {
        if (view == null) return
        runCatching {
            var changed = false
            if (view.useController) {
                view.useController = false
            }
            if (view.resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FILL) {
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                changed = true
            }
            val lp = view.layoutParams
            if (lp == null ||
                lp.width != android.view.ViewGroup.LayoutParams.MATCH_PARENT ||
                lp.height != android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ) {
                view.layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.Gravity.CENTER
                )
                changed = true
            }
            val contentFrameId = view.resources.getIdentifier("exo_content_frame", "id", view.context.packageName)
                .takeIf { it != 0 }
                ?: androidx.media3.ui.R.id.exo_content_frame
            val contentFrame = view.findViewById<android.view.View?>(contentFrameId)
            if (contentFrame is AspectRatioFrameLayout) {
                if (contentFrame.resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FILL) {
                    contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    changed = true
                }
                val cLp = contentFrame.layoutParams
                if (cLp == null ||
                    cLp.width != android.view.ViewGroup.LayoutParams.MATCH_PARENT ||
                    cLp.height != android.view.ViewGroup.LayoutParams.MATCH_PARENT
                ) {
                    contentFrame.layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.Gravity.CENTER
                    )
                    changed = true
                }
            }
            view.videoSurfaceView?.let { sv ->
                if (sv.visibility != android.view.View.VISIBLE) {
                    sv.visibility = android.view.View.VISIBLE
                    changed = true
                }
                val sLp = sv.layoutParams
                if (sLp == null ||
                    sLp.width != android.view.ViewGroup.LayoutParams.MATCH_PARENT ||
                    sLp.height != android.view.ViewGroup.LayoutParams.MATCH_PARENT
                ) {
                    sv.layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.Gravity.CENTER
                    )
                    changed = true
                }
                if (sv is android.view.TextureView && sv.isOpaque) {
                    sv.isOpaque = false
                }
            }
            if (changed) {
                view.requestLayout()
            }
        }
    }

    fun attachViewSafely(player: ExoPlayer, targetView: PlayerView) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            surfaceMainHandler.post { attachViewSafely(player, targetView) }
            return
        }
        if (!isSurfaceSwitching.compareAndSet(false, true)) {
            surfaceMainHandler.postDelayed({ attachViewSafely(player, targetView) }, 16L)
            return
        }
        try {
            targetView.useController = false
            val tLp = targetView.layoutParams
            if (tLp == null ||
                tLp.width != android.view.ViewGroup.LayoutParams.MATCH_PARENT ||
                tLp.height != android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ) {
                targetView.layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.Gravity.CENTER
                )
            }
            val previous = currentBoundView
            if (previous !== targetView) {
                // Hardware VPU Safe Surface Handshake for SurfaceView (Zero Blur + Zero TV Box Hang):
                // Detach previous SurfaceView cleanly and clear the hardware VPU surface pointer BEFORE
                // binding the new SurfaceView so Amlogic/Rockchip/MediaTek hardware decoders never deadlock
                // on orphaned ION GraphicBuffers!
                val wasPlaying = runCatching { player.playWhenReady }.getOrDefault(true)
                val hadMedia = runCatching { player.currentMediaItem != null }.getOrDefault(false)
                val wasActiveState = runCatching {
                    val st = player.playbackState
                    st == Player.STATE_READY || st == Player.STATE_BUFFERING
                }.getOrDefault(false)
                runCatching {
                    val usesSurfaceView = (previous?.videoSurfaceView is android.view.SurfaceView) ||
                        (targetView.videoSurfaceView is android.view.SurfaceView)
                    if (previous != null) {
                        previous.player = null
                    }
                    if (usesSurfaceView) {
                        player.clearVideoSurface()
                    }
                    targetView.player = player
                    // Unified TextureView supports 0ms zero-reconnect SurfaceTexture handover without stopping
                    // or re-buffering the active stream! Only re-prepare if a SurfaceView is involved.
                    if (usesSurfaceView && previous != null && hadMedia && wasActiveState) {
                        player.stop()
                        player.playWhenReady = wasPlaying
                        player.prepare()
                        if (wasPlaying) player.play()
                    }
                }.onFailure {
                    runCatching {
                        previous?.player = null
                        targetView.player = player
                    }
                }
            } else if (targetView.player !== player) {
                targetView.player = player
            }
            (targetView.videoSurfaceView as? android.view.TextureView)?.apply {
                isOpaque = false
            }
            (targetView.videoSurfaceView as? android.view.SurfaceView)?.apply {
                setZOrderOnTop(false)
                setZOrderMediaOverlay(false)
            }
            if (targetView.id == R.id.exo_mini_texture_player_view) {
                enforceMiniPlayerFullBounds(targetView)
            } else {
                targetView.videoSurfaceView?.visibility = android.view.View.VISIBLE
                targetView.requestLayout()
            }
            currentBoundView = targetView
            handoverInProgress = false
        } catch (_: Throwable) {
            currentBoundView = if (targetView.player === player) targetView else currentBoundView
            handoverInProgress = false
        } finally {
            isSurfaceSwitching.set(false)
        }
    }

    fun rebindVideoSurfaceForChannelSwitch(player: ExoPlayer, targetView: PlayerView?) {
        if (!isSurfaceSwitching.compareAndSet(false, true)) return
        try {
            runCatching {
                val view = targetView ?: currentBoundView
                if (view != null) {
                    view.useController = false
                    view.videoSurfaceView?.visibility = android.view.View.VISIBLE
                    if (view.player !== player) {
                        PlayerView.switchTargetView(player, currentBoundView, view)
                    }
                    currentBoundView = view
                }
            }
        } finally {
            isSurfaceSwitching.set(false)
        }
    }

    /**
     * Lightweight surface refresh only. Do not clear/recreate the decoder
     * surface or toggle video track selection here: both operations can force
     * MediaCodec teardown and cause device-wide stalls on TV boxes.
     */
    fun nudgeVideoSurfaceAndRenderer(player: ExoPlayer, targetView: PlayerView?) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            surfaceMainHandler.post { nudgeVideoSurfaceAndRenderer(player, targetView) }
            return
        }
        val view = targetView ?: currentBoundView
        if (view != null && view.player === player) {
            runCatching {
                view.videoSurfaceView?.requestLayout()
                view.requestLayout()
                view.invalidate()
            }
        }
    }

    fun detachViewIfBound(player: ExoPlayer, viewToDetach: PlayerView?) {
        if (viewToDetach == null) return
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            surfaceMainHandler.post { detachViewIfBound(player, viewToDetach) }
            return
        }
        runCatching {
            if (viewToDetach.player === player) {
                viewToDetach.player = null
            }
            if (currentBoundView === viewToDetach) {
                currentBoundView = null
                player.clearVideoSurface()
            }
        }
    }

    fun prepareForSurfaceTransition() {
        // Before Compose destroys the Fullscreen or Mini SurfaceView, immediately stop the hardware VPU
        // and detach the SurfaceView synchronously on the Main Thread!
        // Stopping the hardware MediaCodec BEFORE SurfaceView.surfaceDestroyed() fires guarantees 0% chance
        // of hardware VPU / AudioTrack DMA deadlock when returning from Fullscreen on Android TV & TV Boxes!
        handoverInProgress = true
        surfaceTransitionJob?.cancel()
        surfaceTransitionJob = null
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            runCatching {
                val p = sharedPlayer
                currentBoundView?.let { bound ->
                    if (bound.player != null) bound.player = null
                }
                currentBoundView = null
                if (p != null) {
                    p.clearVideoSurface()
                    p.stop()
                }
            }
        }
    }

    fun recordActiveSession(player: ExoPlayer, stableKey: String, streamUrl: String) {
        pendingReleaseJob?.cancel()
        registerAndEnforceSingleActivePlayer(player)
        if (sharedPlayer != null && sharedPlayer !== player) {
            val old = sharedPlayer
            sharedPlayer = null
            currentBoundView = null
            scheduleSafeRelease(old, null, activeStreamUrl)
        }
        sharedPlayer = player
        activeStableKey = stableKey
        activeStreamUrl = streamUrl
    }

    fun canReuseSeamlessSession(player: ExoPlayer, stableKey: String, streamUrl: String): Boolean {
        val cleanTarget = streamUrl.substringBefore("|").trim()
        val cleanActive = activeStreamUrl.substringBefore("|").trim()
        if (sharedPlayer !== player || activeStableKey != stableKey || cleanTarget.isBlank() || cleanActive != cleanTarget) {
            return false
        }
        val st = runCatching { player.playbackState }.getOrDefault(Player.STATE_IDLE)
        return st == Player.STATE_READY || st == Player.STATE_BUFFERING
    }

    fun beginHandover(player: ExoPlayer, stableKey: String, streamUrl: String) {
        recordActiveSession(player, stableKey, streamUrl)
        prepareForSurfaceTransition()
    }

    fun scheduleSafeRelease(
        playerToRelease: ExoPlayer?,
        allocator: DefaultAllocator? = null,
        streamUrlToClear: String = ""
    ) {
        if (playerToRelease == null) return
        trackedPlayers.remove(playerToRelease)
        // Immediately silence and stop on the Main Thread in 0ms so audio NEVER overlaps or lingers!
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            runCatching {
                playerToRelease.volume = 0f
                playerToRelease.playWhenReady = false
                playerToRelease.stop()
                playerToRelease.clearMediaItems()
                playerToRelease.clearVideoSurface()
                playerToRelease.release()
            }
        } else {
            surfaceMainHandler.post {
                runCatching {
                    playerToRelease.volume = 0f
                    playerToRelease.playWhenReady = false
                    playerToRelease.stop()
                    playerToRelease.clearMediaItems()
                    playerToRelease.clearVideoSurface()
                    playerToRelease.release()
                }
            }
        }
        bridgeIoScope.launch {
            runCatching {
                allocator?.trim()
                ExoFfmpegUniversalEngine.closeAllActiveStreamDataSources()
                ChannelRepository.forceCloseAllStreamConnections()
                if (streamUrlToClear.isNotBlank()) {
                    ChannelRepository.terminatePreviousStreamSessionAndFlushCache(streamUrlToClear)
                    StreamFormatDetectorAndRenewer.clearChannelCache(streamUrlToClear)
                }
            }
        }
    }

    fun releaseSharedPlayerCompletely(immediateOnDestroy: Boolean = false) {
        handoverInProgress = false
        surfaceTransitionJob?.cancel()
        pendingReleaseJob?.cancel()
        val p = sharedPlayer
        val oldView = currentBoundView
        sharedPlayer = null
        currentBoundView = null
        activeStableKey = ""
        activeStreamUrl = ""
        val allToRelease = LinkedHashSet<ExoPlayer>()
        if (p != null) allToRelease.add(p)
        allToRelease.addAll(trackedPlayers)
        trackedPlayers.clear()
        val releaseBlock = {
            runCatching {
                if (oldView != null) {
                    oldView.player = null
                }
            }
            for (pl in allToRelease) {
                runCatching {
                    pl.volume = 0f
                    pl.playWhenReady = false
                    pl.stop()
                    pl.clearMediaItems()
                    pl.clearVideoSurface()
                    pl.release()
                }
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            releaseBlock()
        } else {
            surfaceMainHandler.post { releaseBlock() }
        }
        if (allToRelease.isNotEmpty()) {
            bridgeIoScope.launch {
                runCatching {
                    ExoFfmpegUniversalEngine.closeAllActiveStreamDataSources()
                    ChannelRepository.forceCloseAllStreamConnections()
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: TvChannel,
    siblingChannels: List<TvChannel>,
    allChannels: List<TvChannel>,
    precomputedBouquets: List<Pair<String, List<TvChannel>>> = emptyList(),
    vodWatchProgressMap: Map<String, VodWatchProgress> = emptyMap(),
    favorites: List<TvChannel>,
    xtreamPlaylists: List<XtreamAccountConfig> = emptyList(),
    preferences: PlayerPreferences,
    isFavorite: Boolean,
    initialResumePositionMs: Long = 0L,
    onSaveVodProgress: (TvChannel, Long, Long) -> Unit = { _, _, _ -> },
    onToggleFavorite: (TvChannel) -> Unit,
    onUpdatePreferences: (PlayerPreferences) -> Unit,
    onSelectChannel: (TvChannel) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lang = preferences.appLanguage

    var showControls by remember(channel.stableKey) { mutableStateOf(false) }
    var isPlayerScreenLocked by remember(channel.stableKey) { mutableStateOf(false) }
    var sleepTimerRemainingMin by remember { mutableStateOf(ProSubtitleAndPlayerSettings.sleepTimerMinutes) }
    var openSubtitlesStatusToast by remember { mutableStateOf("") }

    LaunchedEffect(openSubtitlesStatusToast) {
        if (openSubtitlesStatusToast.isNotBlank()) {
            delay(3200L)
            openSubtitlesStatusToast = ""
        }
    }
    var showQuickDrawer by remember(channel.stableKey) { mutableStateOf(false) }
    var drawerSelectedBouquet by remember(channel.stableKey) { mutableStateOf(channel.group) }
    var drawerOpenTimestampMs by remember { mutableStateOf(0L) }
    var focusFirstChannelTrigger by remember { mutableStateOf(0) }
    var focusBouquetTrigger by remember { mutableStateOf(0) }

    var showTracksDialog by remember { mutableStateOf(false) }
    var tracksModalInitialTab by remember { mutableStateOf(0) }
    var resizeMode by remember { mutableStateOf(preferences.resizeMode) }
    var playbackSpeed by remember { mutableStateOf(PlaybackSpeedOption.NORMAL) }

    var isBuffering by remember(channel.stableKey) { mutableStateOf(true) }
    var showBufferingBadge by remember(channel.stableKey) { mutableStateOf(false) }
    var isPlaying by remember(channel.stableKey) { mutableStateOf(true) }
    var isSourceStopped by remember(channel.stableKey) { mutableStateOf(false) }
    var isAutoReconnecting by remember(channel.stableKey) { mutableStateOf(false) }
    var autoReconnectAttempt by remember(channel.stableKey) { mutableStateOf(0) }
    // maxAutoReconnectAttempts is computed dynamically below from formatCandidateUrls.size
    var errorMessage by remember(channel.stableKey) { mutableStateOf<String?>(null) }
    var fatalHttpErrorBanner by remember(channel.stableKey) { mutableStateOf<String?>(null) }
    var playerDiagnosticAlert by remember(channel.stableKey) { mutableStateOf<DiagnosticAlertInfo?>(null) }
    var lastErrorCodeName by remember(channel.stableKey) { mutableStateOf("") }
    var fastFallbackTriggered by remember(channel.stableKey) { mutableStateOf(false) }
    var activeSessionChannelKey by remember { mutableStateOf("") }
    var activeSessionStreamUrl by remember { mutableStateOf("") }
    // PRODUCTION: ExoPlayer absolute priority for ALL HTTP/HTTPS (Live / Movie / Series).
    // Local DB "requires VLC" only shortens failover timeout — NEVER forces VLC on open.
    // VLC starts immediately ONLY for exclusive non-HTTP protocols (RTMP/MMS/UDP/RTP) or exclusive codecs.
    val knownVlcFromLocalDb = remember(channel.stableKey, channel.url) {
        FastFormatPreferenceCache.doesChannelPreferVlc(channel.stableKey, channel.url)
    }
    val initialNeedsVlc = remember(channel.stableKey, channel.url) {
        StreamFormatDetectorAndRenewer.isSoftwareVideoPreferredCodec(
            url = channel.url,
            videoCodecHint = channel.videoCodecHint,
            audioCodecHint = channel.audioCodecHint,
            containerExtension = channel.containerExtension,
            name = channel.name
        )
    }
    var codecRequiresVlc by remember(channel.stableKey) { mutableStateOf(initialNeedsVlc) }
    var forceVlcSoftwareVideo by remember(channel.stableKey) { mutableStateOf(initialNeedsVlc) }
    val forceExoSoftwareCodec = remember {
        SharedLiveSessionBridge.forceSharedSoftwareVideoCodec.apply {
            set(preferences.preferSoftwareDecoder)
        }
    }
    // Reset the decoder preference for every new channel. A failure on one stream must
    // never leave the shared ExoPlayer preferring software decoding for all later channels.
    LaunchedEffect(channel.stableKey, preferences.preferSoftwareDecoder) {
        forceExoSoftwareCodec.set(preferences.preferSoftwareDecoder)
        SharedLiveSessionBridge.forceSharedSoftwareVideoCodec.set(preferences.preferSoftwareDecoder)
    }
    var retryCount by remember(channel.stableKey) { mutableStateOf(0) }
    var videoResolutionLabel by remember(channel.stableKey) { mutableStateOf("") }
    var currentTracksSnapshot by remember { mutableStateOf(Tracks.EMPTY) }
    var hasRenderedFirstVideoFrame by remember(channel.stableKey) {
        mutableStateOf(
            channel.contentType == StreamContentType.LIVE_TV &&
                SharedLiveSessionBridge.activeStableKey == channel.stableKey &&
                SharedLiveSessionBridge.sharedPlayer?.playbackState == Player.STATE_READY
        )
    }

    val mqVariants = remember(channel.stableKey) {
        if (channel.contentType == StreamContentType.LIVE_TV) {
            MultiQualityEngine.variantsForChannel(channel)
        } else emptyList()
    }
    val isMultiQualityChannel = mqVariants.size >= 2 || channel.playlistId == MultiQualityEngine.PLAYLIST_ID
    var activeMqVariant by remember(channel.stableKey) {
        mutableStateOf(
            if (isMultiQualityChannel) {
                MultiQualityEngine.getActiveVariant(channel.stableKey)
                    ?: MultiQualityEngine.pickBestVariant(channel)
                    ?: mqVariants.firstOrNull()
            } else null
        )
    }
    var mqSwitchGeneration by remember(channel.stableKey) { mutableStateOf(0) }
    var mqSwitchBanner by remember(channel.stableKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(mqSwitchBanner, mqSwitchGeneration) {
        if (!mqSwitchBanner.isNullOrBlank()) {
            delay(4200L)
            mqSwitchBanner = null
        }
    }

    val activeStreamChannel = remember(channel, activeMqVariant) {
        activeMqVariant?.channel ?: channel
    }

    val matchingAccount = remember(activeStreamChannel.playlistId, xtreamPlaylists) {
        xtreamPlaylists.find { it.id == activeStreamChannel.playlistId }
    }
    var sameUrlRetryAttempts by remember(channel.stableKey) { mutableStateOf(0) }
    var vodContainerFallbackTried by remember(channel.stableKey) { mutableStateOf(false) }
    var candidateIndex by remember(channel.stableKey) { mutableStateOf(0) }
    var isSeamlessMqTransition by remember(channel.stableKey) { mutableStateOf(false) }
    var resolvedVodOverrideUrl by remember(channel.stableKey) { mutableStateOf<String?>(null) }

    val formatCandidateUrls = remember(channel.stableKey, activeStreamChannel, matchingAccount, mqSwitchGeneration, resolvedVodOverrideUrl) {
        val baseList = activeStreamChannel.buildFormatCandidateUrls(matchingAccount)
        val overrideUrl = resolvedVodOverrideUrl?.trim().orEmpty()
        if (overrideUrl.isNotBlank()) (listOf(overrideUrl) + baseList).distinct() else baseList
    }
    val maxAutoReconnectAttempts = 6
    var detectedSpecLabel by remember(channel.stableKey) {
        mutableStateOf(
            listOfNotNull(
                channel.videoCodecHint.takeIf { it.isNotBlank() },
                channel.audioCodecHint.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
        )
    }
    var formatAttemptMode by remember(channel.stableKey) { mutableStateOf(0) }
    var numberInputBuffer by remember { mutableStateOf("") }
    var showZappingOsd by remember { mutableStateOf(false) }
    var zappingOsdTick by remember { mutableStateOf(0L) }
    var retried502ForCurrentCandidate by remember(channel.stableKey, candidateIndex) { mutableStateOf(false) }
    var ffmpegFallbackAttempted by remember(channel.stableKey, candidateIndex) { mutableStateOf(false) }

    val rootPlayerFocusRequester = remember { FocusRequester() }
    val topBackBtnFocusRequester = remember { FocusRequester() }
    val topFavBtnFocusRequester = remember { FocusRequester() }
    val topPipBtnFocusRequester = remember { FocusRequester() }
    val topListBtnFocusRequester = remember { FocusRequester() }
    val centerPrevFocusRequester = remember { FocusRequester() }
    val centerRewindFocusRequester = remember { FocusRequester() }
    val centerPlayFocusRequester = remember { FocusRequester() }
    val centerFfwdFocusRequester = remember { FocusRequester() }
    val stopOverlayRetryFocusRequester = remember { FocusRequester() }
    var userNavInteractionTick by remember { mutableStateOf(0L) }
    val centerNextFocusRequester = remember { FocusRequester() }
    val bottomPrevChFocusRequester = remember { FocusRequester() }
    val bottomNextChFocusRequester = remember { FocusRequester() }
    val bottomFirstPillFocusRequester = remember { FocusRequester() }
    val bottomQualityPillFocusRequester = remember { FocusRequester() }
    val bottomAudioPillFocusRequester = remember { FocusRequester() }
    val bottomSubtitlesPillFocusRequester = remember { FocusRequester() }
    val bottomResizePillFocusRequester = remember { FocusRequester() }
    val bottomSettingsPillFocusRequester = remember { FocusRequester() }
    val bottomFavPillFocusRequester = remember { FocusRequester() }
    var lastControlFocusRequester by remember { mutableStateOf(centerPlayFocusRequester) }
    val vodSeekBarFocusRequester = remember { FocusRequester() }
    val activeBouquetFocusRequester = remember { FocusRequester() }
    val firstChannelInBouquetFocusRequester = remember { FocusRequester() }
    val activeChannelFocusRequester = remember { FocusRequester() }
    var attachedPlayerView by remember { mutableStateOf<PlayerView?>(null) }

    var networkSpeedLabel by remember { mutableStateOf(NetworkSpeedMonitor.sampleCurrentSpeedLabel()) }
    var vodPositionMs by remember(channel.stableKey) { mutableStateOf(initialResumePositionMs.coerceAtLeast(0L)) }
    var vodDurationMs by remember(channel.stableKey) { mutableStateOf(0L) }
    var pendingResumeTargetMs by remember(channel.stableKey) { mutableStateOf(initialResumePositionMs.coerceAtLeast(0L)) }

    LaunchedEffect(showQuickDrawer, showTracksDialog, showControls) {
        if (!showQuickDrawer && !showTracksDialog && !showControls) {
            for (attempt in 1..4) {
                delay(45L)
                if (runCatching { rootPlayerFocusRequester.requestFocus() }.isSuccess) break
            }
        } else if (showControls && !showQuickDrawer && !showTracksDialog) {
            for (attempt in 1..5) {
                delay(45L)
                if (channel.contentType == StreamContentType.LIVE_TV) {
                    if (runCatching { bottomFirstPillFocusRequester.requestFocus() }.isSuccess) break
                    if (runCatching { bottomPrevChFocusRequester.requestFocus() }.isSuccess) break
                    if (runCatching { topBackBtnFocusRequester.requestFocus() }.isSuccess) break
                } else {
                    if (runCatching { lastControlFocusRequester.requestFocus() }.isSuccess) break
                    if (runCatching { centerPlayFocusRequester.requestFocus() }.isSuccess) break
                }
            }
        }
    }

    LaunchedEffect(isBuffering, errorMessage, isSeamlessMqTransition) {
        if (isBuffering && errorMessage == null) {
            // Keep spinner hidden for the first 1.6s of normal channel zapping so fast-opening channels
            // never flash "⏳ جارٍ الاتصال بالسيرفر..." on screen!
            delay(if (isSeamlessMqTransition) 3200L else 1600L)
            if (isBuffering && !isSeamlessMqTransition) showBufferingBadge = true
        } else {
            showBufferingBadge = false
            if (!isBuffering) isSeamlessMqTransition = false
        }
    }

    val favLabel = AppStrings.favoritesLabel(lang)
    val allLabel = AppStrings.allBouquetsLabel(lang)

    // Instant O(1) reuse of precomputed bouquets from CatalogViewSnapshot (zero re-grouping lag on Player open!)
    val allBouquets = remember(precomputedBouquets, favorites, favLabel, channel.group, siblingChannels) {
        if (precomputedBouquets.isNotEmpty()) {
            precomputedBouquets.map { pair ->
                if (pair.first == favLabel) favLabel to favorites else pair
            }
        } else {
            listOf(channel.group to siblingChannels)
        }
    }

    val drawerChannels = remember(allBouquets, drawerSelectedBouquet, siblingChannels) {
        allBouquets.find { it.first == drawerSelectedBouquet }?.second
            ?: siblingChannels.ifEmpty {
                allBouquets.firstOrNull { !it.first.startsWith("★") && it.second.isNotEmpty() }?.second.orEmpty()
            }
    }

    val effectiveUrl = remember(formatCandidateUrls, candidateIndex, channel.stableKey, channel.url) {
        val localFileUri = if (channel.contentType != StreamContentType.LIVE_TV) {
            OfflineDownloadsManager.getDownloadedLocalUriIfAvailable(context, channel.stableKey, channel.url)
        } else null
        localFileUri ?: formatCandidateUrls.getOrElse(candidateIndex.coerceIn(0, (formatCandidateUrls.size - 1).coerceAtLeast(0))) {
            channel.url
        }
    }

    DisposableEffect(Unit) {
        val isTvDevice = (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        if (!isTvDevice) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
        }
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!isTvDevice) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    val httpFactory = remember {
        OkHttpDataSource.Factory(SharedLiveSessionBridge.getPlaybackOkHttpClient())
            .setUserAgent(STREAM_USER_AGENTS.first())
            .setFollowRedirects(true)
            .setFollowSslRedirects(true)
    }

    // Ultra-Fast Extractor Suite (v22.0):
    // - MODE_SINGLE_PMT + 188*600 timestamp search bytes locks onto MPEG-TS audio/video codecs 5x faster (<100ms)!
    // - FLAG_DETECT_ACCESS_UNITS fixes H.264/HEVC live channels that omit AUD NAL units (prevents frame drops & black screen).
    // - FLAG_WORKAROUND_IGNORE_EDIT_LISTS fixes MP4 VOD movies with broken edit lists.
    // 4K UHD & High-Bitrate MPEG-TS Extractor Suite (v24.1):
    // - Uses 188 * 3600 (676 KB) timestamp search window so 25–45 Mbps 4K HEVC raw MPEG-TS streams
    //   always locate their PCR timestamps without failing or stalling!
    // - Omits FLAG_DETECT_ACCESS_UNITS because synthetic H.264 AUD injection corrupts HEVC/H.265 4K NAL units.
    val universalDataSourceFactory = remember {
        ExoFfmpegUniversalEngine.buildUniversalDataSourceFactory(context, httpFactory)
    }

    val extractorsFactory = remember {
        ExoFfmpegUniversalEngine.buildUniversalExtractorsFactory(
            isLiveStream = (channel.contentType == StreamContentType.LIVE_TV)
        )
    }

    // IBO Player Pro Resilient HLS/TS Chunk & Redirect Retry Policy:
    // Retries fast HLS chunk / playlist rotations and Load-Balancer 302 hops internally (up to 6 times at 120ms-400ms)
    // so ExoPlayer NEVER drops playback or stutters when an edge node rotates segments or refreshes tokens!
    val lightningProbeErrorPolicy = remember {
        object : DefaultLoadErrorHandlingPolicy(6) {
            override fun getMinimumLoadableRetryCount(dataType: Int): Int = 6

            override fun getRetryDelayMsFor(
                loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
            ): Long {
                val code = (loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                if (code == 404 || code == 410) {
                    return if (loadErrorInfo.errorCount == 1) 450L else C.TIME_UNSET
                }
                if (code in setOf(401, 403, 429, 458, 460, 500, 502, 503, 504)) {
                    // Smart Xtream max_connections=1 slot-release & slow load-balancer retry curve
                    // Matches native IPTV players: waits patiently for the panel to release the previous stream & issue 302 -> 200 OK
                    return when (loadErrorInfo.errorCount) {
                        1 -> 550L
                        2 -> 1_100L
                        3 -> 1_800L
                        4 -> 2_400L
                        5 -> 3_000L
                        6 -> 3_500L
                        else -> C.TIME_UNSET
                    }
                }
                return when (loadErrorInfo.errorCount) {
                    1 -> 300L
                    2 -> 800L
                    3 -> 1_600L
                    4 -> 2_500L
                    else -> C.TIME_UNSET
                }
            }

            override fun getFallbackSelectionFor(
                fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
                loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
            ): LoadErrorHandlingPolicy.FallbackSelection? {
                val code = (loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                val exclusionMs = when (code) {
                    403, 404, 410 -> 30_000L
                    500, 502, 503, 504 -> 5_000L
                    else -> return super.getFallbackSelectionFor(fallbackOptions, loadErrorInfo)
                }
                return when {
                    fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK) ->
                        LoadErrorHandlingPolicy.FallbackSelection(
                            LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK,
                            exclusionMs
                        )
                    fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION) ->
                        LoadErrorHandlingPolicy.FallbackSelection(
                            LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION,
                            exclusionMs
                        )
                    else -> null
                }
            }
        }
    }

    val defaultMediaSourceFactory = remember {
        DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(universalDataSourceFactory)
            .setLoadErrorHandlingPolicy(lightningProbeErrorPolicy)
    }

    val fastDashMediaSourceFactory = remember {
        DashMediaSource.Factory(universalDataSourceFactory)
            .setManifestParser(FastRefreshDashManifestParser())
            .setFallbackTargetLiveOffsetMs(3_500L)
            .setLoadErrorHandlingPolicy(lightningProbeErrorPolicy)
    }

    val fastHlsMediaSourceFactory = remember {
        HlsMediaSource.Factory(universalDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .setTimestampAdjusterInitializationTimeoutMs(0L)
            .setLoadErrorHandlingPolicy(lightningProbeErrorPolicy)
    }

    val isWeakDevice = remember(preferences.lowEndDeviceMode) {
        preferences.lowEndDeviceMode
    }

    val sharedAllocator = remember {
        DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
    }

    val player = remember {
        val existingShared = if (channel.contentType == StreamContentType.LIVE_TV) {
            SharedLiveSessionBridge.sharedPlayer
        } else {
            SharedLiveSessionBridge.releaseSharedPlayerCompletely()
            null
        }
        if (existingShared != null) {
            existingShared
        } else {
        val bp = preferences.bufferProfile
        val isLowRam = DevicePerformanceDetector.isLowRamOrWeakDevice(context)
        // Balanced production buffering: fast start without the unstable sub-300ms gate.
        // Keep RAM bounded on low-memory TV boxes; do not alter source resolution/bitrate.
        val safeTargetBytes = if (isLowRam) {
            32 * 1024 * 1024
        } else {
            bp.targetBufferBytes.coerceIn(48 * 1024 * 1024, 64 * 1024 * 1024)
        }
        val isLiveStream = channel.contentType == StreamContentType.LIVE_TV
        // Ultra-fast startup + deep anti-stutter cushion for Live TV, Movies & Series:
        // Starts rendering immediately (250ms Live / 350ms VOD) while continuously building
        // a deep 20s-45s Live / 25s-60s VOD buffer in RAM to eliminate mid-stream freezing.
        val loadControl = ExoFfmpegUniversalEngine.buildAdaptiveAntiStutterLoadControl(
            isLiveStream = isLiveStream,
            targetBufferBytes = safeTargetBytes,
            allocator = sharedAllocator,
            bandwidthMeter = SharedLiveSessionBridge.getHighQualityBandwidthMeter(context)
        )

        // Strict Hardware-First MediaCodecSelector for 4K UHD / HEVC Main10 / H.264 / AV1 / VP9:
        // - NEVER forces Software Decoder on high-definition or 4K video tracks (c2.android.hevc.decoder
        //   cannot decode 4K HEVC in real time and causes frozen frames / audio-only).
        // - Prioritizes dedicated SoC hardware decoders (Amlogic, Qualcomm, MediaTek, Rockchip, Broadcom, Hisilicon, Realtek).
        val smartCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val lowMime = mimeType.lowercase(Locale.ROOT)
            val isFfmpegPreferredAudio = lowMime.startsWith("audio/") && (
                lowMime.contains("dts") ||
                    lowMime.contains("ac3") ||
                    lowMime.contains("eac3") ||
                    lowMime.contains("ec-3") ||
                    lowMime.contains("truehd") ||
                    lowMime.contains("true-hd") ||
                    lowMime.contains("mlp") ||
                    lowMime.contains("mpeg-l2") ||
                    lowMime.contains("mp2") ||
                    lowMime.contains("wma") ||
                    lowMime.contains("alac") ||
                    lowMime.contains("flac") ||
                    lowMime.contains("vorbis") ||
                    lowMime.contains("opus") ||
                    lowMime.contains("amr")
                )
            if (isFfmpegPreferredAudio && ExoFfmpegUniversalEngine.isFfmpegDecoderAvailable()) {
                return@MediaCodecSelector emptyList()
            }
            var allDecoders = runCatching {
                MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
            }.getOrDefault(emptyList())
            // Real 4K Dolby Vision -> HEVC Main10 fallback:
            // Many 4K IPTV channels broadcast HEVC Main10 tagged as video/dolby-vision.
            // On TVs/TV Boxes/phones without a Dolby Vision license, fallback to the hardware HEVC (H.265) decoder
            // so the 4K picture renders cleanly instead of playing audio-only on a black screen!
            if (allDecoders.isEmpty() && mimeType.equals(MimeTypes.VIDEO_DOLBY_VISION, ignoreCase = true)) {
                allDecoders = runCatching {
                    MediaCodecSelector.DEFAULT.getDecoderInfos(
                        MimeTypes.VIDEO_H265,
                        false,
                        false
                    )
                }.getOrDefault(emptyList())
            }
            if (allDecoders.size <= 1) {
                allDecoders
            } else {
                val isVideoMime = mimeType.startsWith("video/", ignoreCase = true)
                val isHighResCodec = mimeType.contains("hevc", ignoreCase = true) ||
                    mimeType.contains("dolby-vision", ignoreCase = true) ||
                    mimeType.contains("av01", ignoreCase = true) ||
                    mimeType.contains("vp9", ignoreCase = true) ||
                    mimeType.contains("avc", ignoreCase = true)
                val (swList, hwList) = allDecoders.partition { info ->
                    val lowName = info.name.lowercase(Locale.ROOT)
                    info.softwareOnly ||
                        lowName.startsWith("omx.google.") ||
                        lowName.startsWith("c2.android.") ||
                        lowName.contains(".sw.") ||
                        lowName.contains("ffmpeg")
                }
                val rankedHw = hwList.sortedBy { info ->
                    val n = info.name.lowercase(Locale.ROOT)
                    when {
                        !n.contains("secure") && (info.hardwareAccelerated || n.startsWith("c2.") || n.startsWith("omx.")) -> 0
                        else -> 1
                    }
                }
                val isAudioMime = mimeType.startsWith("audio/", ignoreCase = true)
                val isStandardPcmAudio = isAudioMime && (
                    mimeType.contains("mp4a", ignoreCase = true) ||
                        mimeType.contains("aac", ignoreCase = true) ||
                        mimeType.contains("mpeg", ignoreCase = true) ||
                        mimeType.contains("opus", ignoreCase = true) ||
                        mimeType.contains("vorbis", ignoreCase = true) ||
                        mimeType.contains("flac", ignoreCase = true) ||
                        mimeType.contains("raw", ignoreCase = true)
                    )
                // Production safety: prefer the device hardware audio decoder first to avoid
                // sustained CPU saturation and audio underruns on low-power TV boxes. Software
                // decoders remain available as fallback. Video remains hardware-first for HD/4K.
                if (isVideoMime && forceExoSoftwareCodec.get() && !isHighResCodec && swList.isNotEmpty()) {
                    swList + rankedHw
                } else {
                    rankedHw + swList
                }
            }
        }

        val renderersFactory = ExoFfmpegUniversalEngine.buildAllCodecRenderersFactory(
            context = context,
            forceSoftwareVideoProvider = { forceExoSoftwareCodec.get() },
            preferFfmpegExtension = true
        ).apply {
            setMediaCodecSelector(smartCodecSelector)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
        }

        val trackSelector = ExoFfmpegUniversalEngine.buildAllCodecTrackSelector(
            context = context,
            capHeightForWeakDevice = isWeakDevice
        )

        val bwMeter = SharedLiveSessionBridge.getHighQualityBandwidthMeter(context)
        ExoPlayer.Builder(context, renderersFactory)
            .setBandwidthMeter(bwMeter)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(defaultMediaSourceFactory)
            .setLoadControl(loadControl)
            .setUseLazyPreparation(false)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { createdPlayer ->
                if (channel.contentType == StreamContentType.LIVE_TV) {
                    SharedLiveSessionBridge.sharedPlayer = createdPlayer
                }
            }
        }
    }

    LaunchedEffect(channel.stableKey, activeStreamChannel.streamId) {
        if (channel.contentType == StreamContentType.MOVIE && activeStreamChannel.streamId > 0) {
            val realUrl = XtreamRepository.resolveVodRealUrlIfNeeded(activeStreamChannel, matchingAccount)
            if (!realUrl.isNullOrBlank() && !realUrl.equals(activeStreamChannel.url.trim(), ignoreCase = true)) {
                resolvedVodOverrideUrl = realUrl
                if (!hasRenderedFirstVideoFrame && player.playbackState != Player.STATE_READY) {
                    isSeamlessMqTransition = true
                    candidateIndex = 0
                }
            }
        }
    }
    val bandwidthMeter = remember { SharedLiveSessionBridge.getHighQualityBandwidthMeter(context) }

    var activeEngine by remember(preferences.playerEngine) {
        mutableStateOf(preferences.playerEngine)
    }
    val vlcAvailable = true
    var vlcFallbackToExo by remember(channel.stableKey, activeEngine) { mutableStateOf(false) }
    val useFfmpegSwMode = !vlcFallbackToExo && (codecRequiresVlc || forceVlcSoftwareVideo || activeEngine == InternalPlayerEngine.VLC_PLAYER)
    val useVlcEngine = false

    LaunchedEffect(useFfmpegSwMode, preferences.preferSoftwareDecoder) {
        forceExoSoftwareCodec.set(useFfmpegSwMode || preferences.preferSoftwareDecoder)
    }

    LaunchedEffect(player, playbackSpeed) {
        player.playbackParameters = PlaybackParameters(playbackSpeed.speed)
    }

    LaunchedEffect(channel.stableKey, effectiveUrl, mqSwitchGeneration, formatAttemptMode, retryCount, useFfmpegSwMode) {
        // 0ms Seamless Handover from Mini Preview -> Fullscreen without stopping or re-buffering the live stream!
        if (channel.contentType == StreamContentType.LIVE_TV &&
            retryCount == 0 &&
            SharedLiveSessionBridge.canReuseSeamlessSession(player, channel.stableKey, effectiveUrl)
        ) {
            SharedLiveSessionBridge.handoverInProgress = false
            activeSessionChannelKey = channel.stableKey
            activeSessionStreamUrl = effectiveUrl
            hasRenderedFirstVideoFrame = true
            isSourceStopped = false
            isAutoReconnecting = false
            errorMessage = null
            isBuffering = (player.playbackState == Player.STATE_BUFFERING)
            showBufferingBadge = false
            runCatching {
                attachedPlayerView?.let { pv ->
                    SharedLiveSessionBridge.attachViewSafely(player, pv)
                }
                player.volume = 1.0f
                player.playWhenReady = true
                if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                    player.play()
                }
            }
            return@LaunchedEffect
        }
        SharedLiveSessionBridge.handoverInProgress = false
        // TiviMate / IBO instant flicker-free channel switch:
        // ExoPlayer's stop() + clearMediaItems() cleanly closes the previous stream's DataSource socket synchronously
        // without spawning an async dispatcher.cancelAll() thread that could kill the NEW channel's handshake!
        player.stop()
        player.clearMediaItems()
        if (retryCount > 0) {
            // Brief settle only on error retries (0ms on first click so channel opens instantaneously!)
            delay(300L)
        }
        isBuffering = true
        errorMessage = null

        val sessionId = PlaybackSessionGuard.beginSession(channel.stableKey)
        if (!PlaybackSessionGuard.isSessionCurrent(sessionId, channel.stableKey)) {
            return@LaunchedEffect
        }
        val isChannelSwitch = activeSessionChannelKey.isNotEmpty() && activeSessionChannelKey != channel.stableKey
        val prevStreamUrl = activeSessionStreamUrl
        activeSessionChannelKey = channel.stableKey
        activeSessionStreamUrl = effectiveUrl

        if (isChannelSwitch) {
            hasRenderedFirstVideoFrame = false
            isSourceStopped = false
            isAutoReconnecting = false
            autoReconnectAttempt = 0
            sameUrlRetryAttempts = 0
            vodContainerFallbackTried = false
            isSeamlessMqTransition = false
            if (prevStreamUrl.isNotBlank() && prevStreamUrl != effectiveUrl) {
                runCatching {
                    player.stop()
                    SharedLiveSessionBridge.getPlaybackOkHttpClient().dispatcher.cancelAll()
                }
            }
        } else if (!isSeamlessMqTransition) {
            hasRenderedFirstVideoFrame = false
        }
        if (channel.contentType != StreamContentType.LIVE_TV && retryCount > 0) {
            val currentPosBeforeStop = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
            val bestResumePos = maxOf(currentPosBeforeStop, vodPositionMs, pendingResumeTargetMs)
            if (bestResumePos > 2_000L) {
                vodPositionMs = bestResumePos
                pendingResumeTargetMs = bestResumePos
            }
        }

        runCatching {
            if (attachedPlayerView != null && attachedPlayerView?.player !== player) {
                SharedLiveSessionBridge.attachViewSafely(player, attachedPlayerView!!)
            }
            player.volume = 1.0f
            if (player.trackSelectionParameters.overrides.isNotEmpty()) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverrides()
                    .build()
            }
        }

        val rotatedUa = activeStreamChannel.userAgent?.takeIf { it.isNotBlank() }
            ?: channel.userAgent?.takeIf { it.isNotBlank() }
            ?: STREAM_USER_AGENTS[retryCount % STREAM_USER_AGENTS.size]

        val rawCandidateUrl = effectiveUrl.trim()

        val fastSpec = StreamFormatDetectorAndRenewer.detectFastFromUrlAndMetadata(activeStreamChannel, rawCandidateUrl, rotatedUa)

        if (!PlaybackSessionGuard.isSessionCurrent(sessionId, channel.stableKey)) {
            return@LaunchedEffect
        }

        val mqBadgePrefix = activeMqVariant?.let { "جودة ${it.quality.labelAr} • " }.orEmpty()
        val pipePart = if (rawCandidateUrl.contains("|")) rawCandidateUrl.substringAfter("|") else ""
        val targetPlayUrl = rawCandidateUrl.substringBefore("|").trim()
        val low = targetPlayUrl.lowercase(Locale.ROOT).substringBefore("?")
        val fullLow = targetPlayUrl.lowercase(Locale.ROOT)

        val isMpdStream = channel.contentType != StreamContentType.LIVE_TV && (
            fastSpec.mimeType == MimeTypes.APPLICATION_MPD ||
            low.endsWith(".mpd") || fullLow.contains("format=mpd") || fullLow.contains("type=mpd")
        )
        val isExplicitHlsUrl = low.endsWith(".m3u8") ||
            fullLow.contains("output=m3u8") || fullLow.contains("type=m3u8") || fullLow.contains("output=hls")
        val isVodFile = channel.contentType != StreamContentType.LIVE_TV ||
            low.endsWith(".mp4") || low.endsWith(".mkv") || low.endsWith(".avi") ||
            low.endsWith(".mov") || low.endsWith(".webm") || low.endsWith(".flv")

        // Direct, clean HTTP headers using activeStreamChannel so each variant uses its own server headers
        val customHeaders = IboPlayerProfileSpoofer.buildStreamHeadersMap(
            targetUrl = targetPlayUrl,
            serverBaseUrl = activeStreamChannel.streamBaseUrl.ifBlank { channel.streamBaseUrl },
            customReferer = activeStreamChannel.referer ?: channel.referer,
            customOrigin = activeStreamChannel.origin ?: channel.origin,
            customCookie = activeStreamChannel.cookie ?: channel.cookie,
            isLiveManifest = isExplicitHlsUrl
        )
        if (pipePart.isNotEmpty()) {
            pipePart.split("&", "|").forEach { kv ->
                val k = kv.substringBefore("=").trim().lowercase(Locale.ROOT)
                val v = kv.substringAfter("=", "").trim()
                if (v.isNotBlank()) {
                    when (k) {
                        "user-agent", "user_agent" -> customHeaders["User-Agent"] = v
                        "referer", "referrer" -> customHeaders["Referer"] = v
                        "origin" -> customHeaders["Origin"] = v
                        "cookie" -> customHeaders["Cookie"] = v
                    }
                }
            }
        }
        httpFactory.setDefaultRequestProperties(customHeaders)
        httpFactory.setUserAgent(rotatedUa)

        val itemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(targetPlayUrl))

        if (channel.contentType == StreamContentType.LIVE_TV) {
            // Lock live playback speed strictly at 1.0f so ExoPlayer never speeds up to 1.05x on startup
            // (which otherwise drains the initial pre-roll buffer and causes a brief micro-stutter right after opening!)
            itemBuilder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMinPlaybackSpeed(1.0f)
                    .setMaxPlaybackSpeed(1.0f)
                    .build()
            )
        }

        if (channel.drmKey.startsWith("http://", ignoreCase = true) || channel.drmKey.startsWith("https://", ignoreCase = true)) {
            itemBuilder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(channel.drmKey.trim())
                    .setMultiSession(false)
                    .build()
            )
        }

        forceExoSoftwareCodec.set(useFfmpegSwMode || preferences.preferSoftwareDecoder || fastSpec.preferSoftwareDecoder)
        val engineModeBadge = if (forceExoSoftwareCodec.get()) "Exo+FFmpeg SW" else "Exo+FFmpeg HW"
        if (detectedSpecLabel.isBlank() || retryCount == 0) {
            detectedSpecLabel = "$mqBadgePrefix$engineModeBadge • ${fastSpec.videoCodecHint} • ${fastSpec.audioCodecHint}"
        }

        // Only set explicit manifest MIME types; let buildUniversalExtractorsFactory auto-sniff TS/MP4/MKV in 0ms!
        when {
            isMpdStream -> itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            isExplicitHlsUrl -> itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            low.contains(".ism") -> itemBuilder.setMimeType(MimeTypes.APPLICATION_SS)
            low.startsWith("rtsp://") -> itemBuilder.setMimeType(MimeTypes.APPLICATION_RTSP)
        }

        val mediaItem = itemBuilder.build()
        val clearKeyManager = if (channel.drmKey.isNotBlank() && !channel.drmKey.startsWith("http", ignoreCase = true)) {
            buildClearKeyDrmManager(channel.drmKey)
        } else null

        if (isMpdStream) {
            val dashFactory = if (clearKeyManager != null) {
                DashMediaSource.Factory(httpFactory)
                    .setManifestParser(FastRefreshDashManifestParser())
                    .setFallbackTargetLiveOffsetMs(2_800L)
                    .setDrmSessionManagerProvider { clearKeyManager }
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
            } else {
                fastDashMediaSourceFactory
            }
            player.setMediaSource(dashFactory.createMediaSource(mediaItem), true)
        } else if (clearKeyManager != null) {
            val drmDefaultFactory = DefaultMediaSourceFactory(context, extractorsFactory)
                .setDataSourceFactory(httpFactory)
                .setDrmSessionManagerProvider { clearKeyManager }
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
            player.setMediaSource(drmDefaultFactory.createMediaSource(mediaItem), true)
        } else if (isExplicitHlsUrl) {
            player.setMediaSource(fastHlsMediaSourceFactory.createMediaSource(mediaItem), true)
        } else {
            player.setMediaItem(mediaItem, true)
        }

        if (isVodFile && pendingResumeTargetMs > 2_000L) {
            player.seekTo(pendingResumeTargetMs)
        }
        if (channel.contentType == StreamContentType.LIVE_TV) {
            SharedLiveSessionBridge.recordActiveSession(player, channel.stableKey, effectiveUrl)
        }
        player.playWhenReady = true
        player.prepare()
        player.play()
    }

    // 4K-Aware & Silent-Audio Recovery Watchdog (Works across Live TV, Movies, and Series!):
    // 1. Keeps ExoPlayer as the primary engine and gives 4K/HEVC streams full buffer time for their initial GOP.
    // 2. Monitors audio decoder counters while video is actively playing: if a channel, movie, or series plays
    //    video with ZERO decoded audio buffers (e.g. AC3/E-AC3/DTS/MP2/AAC-LATM or silent hardware audio decoder),
    //    automatically switches to an alternate audio track or hands over to LibVLC (FFmpeg Audio + HW Video) so sound is never lost!
    LaunchedEffect(player, channel.stableKey, activeStreamChannel.url, mqSwitchGeneration, candidateIndex, formatAttemptMode, effectiveUrl, retryCount, useVlcEngine) {
        if (useVlcEngine) return@LaunchedEffect

        val is4kOrHevcHint = activeMqVariant?.quality == StreamQualityRank.UHD_4K ||
            activeStreamChannel.name.contains("4k", ignoreCase = true) ||
            activeStreamChannel.name.contains("uhd", ignoreCase = true) ||
            activeStreamChannel.name.contains("hevc", ignoreCase = true) ||
            activeStreamChannel.name.contains("2160", ignoreCase = true) ||
            activeStreamChannel.group.contains("4k", ignoreCase = true) ||
            activeStreamChannel.group.contains("uhd", ignoreCase = true) ||
            detectedSpecLabel.contains("HEVC", ignoreCase = true) ||
            videoResolutionLabel.contains("4K", ignoreCase = true)

        // Broadcast-Grade Watchdog (~180ms/tick):
        // Gives TV hardware decoders full GOP keyframe alignment time (~5.0s–6.5s) so switching channels NEVER kills
        // a stream that is actively connecting or waiting for its first IDR video keyframe!
        val maxFrozenReadyTicks = when {
            is4kOrHevcHint -> 28
            else -> 24
        }
        val maxInitialStallTicks = when {
            channel.contentType != StreamContentType.LIVE_TV -> 28
            is4kOrHevcHint -> 36
            else -> 32
        }

        var lastRenderedFrames = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
        var lastDroppedFrames = player.videoDecoderCounters?.droppedBufferCount ?: 0
        var lastPosMs = player.currentPosition
        var lastBufferedPosMs = player.bufferedPosition
        var maxBufferSeenMs = 0L
        var bufferingStallTicks = 0
        var midStreamBufferingTicks = 0
        var recentRebufferEvents = 0
        var firstRebufferAtMs = 0L
        var continuousSmoothReadyTicks = 0
        var wasReadyInThisSession = false
        var firstReadyTimestampMs = 0L
        var frozenPictureTicks = 0
        var surfaceNudgedForCurrentChannel = false
        var silentAudioTicks = 0
        var triedAlternateAudioTrack = false

        while (!useVlcEngine) {
            delay(250L) // Responsive 250ms hardware video-surface & frame-flow watchdog
            if (useVlcEngine) break

            NetworkSpeedMonitor.updateExoBandwidthEstimate(bandwidthMeter.bitrateEstimate)

            if (fatalHttpErrorBanner != null || isSourceStopped || !player.playWhenReady) {
                bufferingStallTicks = 0
                midStreamBufferingTicks = 0
                frozenPictureTicks = 0
                silentAudioTicks = 0
                lastRenderedFrames = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
                lastPosMs = player.currentPosition
                lastBufferedPosMs = player.bufferedPosition
                continue
            }

            val state = player.playbackState
            val renderedFrames = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
            val droppedFrames = player.videoDecoderCounters?.droppedBufferCount ?: 0
            val frameDelta = (renderedFrames - lastRenderedFrames).coerceAtLeast(0)
            val droppedDelta = (droppedFrames - lastDroppedFrames).coerceAtLeast(0)
            val currentPos = player.currentPosition
            val bufferedPos = player.bufferedPosition
            val bufferAheadMs = (bufferedPos - currentPos).coerceAtLeast(0L)
            if (bufferAheadMs > maxBufferSeenMs) maxBufferSeenMs = bufferAheadMs
            val posDelta = currentPos - lastPosMs
            val bufDelta = bufferedPos - lastBufferedPosMs
            val bufferVelocityMsPerSec = (bufDelta * 1000L) / 180L

            val audioCounters = player.audioDecoderCounters
            val renderedAudioBuffers = (audioCounters?.renderedOutputBufferCount ?: 0) +
                (audioCounters?.skippedOutputBufferCount ?: 0)

            lastRenderedFrames = renderedFrames
            lastDroppedFrames = droppedFrames
            lastPosMs = currentPos
            lastBufferedPosMs = bufferedPos

            if (isMultiQualityChannel) {
                MultiQualityEngine.updateTelemetrySnapshot(
                    stableKey = channel.stableKey,
                    activeVariant = activeMqVariant,
                    measuredSpeedKbps = MultiQualityEngine.currentMeasuredSpeedKbps(),
                    bufferAheadMs = bufferAheadMs,
                    bufferVelocityMsPerSec = bufferVelocityMsPerSec
                )
            }

            if (state == Player.STATE_READY) {
                bufferingStallTicks = 0
                midStreamBufferingTicks = 0
                if (firstReadyTimestampMs == 0L) {
                    firstReadyTimestampMs = android.os.SystemClock.elapsedRealtime()
                }
                // Allow hardware MediaCodec and Mp4/Matroska extractors to render VOD frames naturally without premature stop
                if (frameDelta > 0 || player.isPlaying) {
                    wasReadyInThisSession = true
                    continuousSmoothReadyTicks++
                    if (isSourceStopped || isAutoReconnecting || errorMessage != null || playerDiagnosticAlert != null) {
                        isSourceStopped = false
                        isAutoReconnecting = false
                        autoReconnectAttempt = 0
                        errorMessage = null
                        playerDiagnosticAlert = null
                    }
                }

                if (isMultiQualityChannel && mqVariants.size >= 2) {
                    val curVar = activeMqVariant
                    if (curVar != null && frameDelta > 0 && renderedFrames >= 20) {
                        MultiQualityEngine.markStablePlayback(channel.stableKey, curVar)
                    }

                    // 1) Real-Time Stream Flow & Pre-Freeze Buffer/Drop Guard:
                    //    Gives a 10-second startup grace window (firstReadyTimestampMs + 10_000L) so the stream NEVER
                    //    switches or stutters during the first few seconds while building its deep 28s reservoir!
                    val readyElapsedMs = if (firstReadyTimestampMs > 0L) {
                        android.os.SystemClock.elapsedRealtime() - firstReadyTimestampMs
                    } else 0L
                    if (curVar != null && wasReadyInThisSession && readyElapsedMs > 10_000L && maxBufferSeenMs >= 4_000L) {
                        val flowDecision = MultiQualityEngine.evaluateLiveFlowHealth(
                            multiStableKey = channel.stableKey,
                            currentVariant = curVar,
                            bufferAheadMs = bufferAheadMs,
                            bufferDeltaMs = bufDelta,
                            frameDelta = frameDelta,
                            droppedFrameDelta = droppedDelta,
                            measuredSpeedKbps = MultiQualityEngine.currentMeasuredSpeedKbps()
                        )
                        if (flowDecision != null && flowDecision.switched) {
                            isSeamlessMqTransition = true
                            activeMqVariant = flowDecision.variant
                            candidateIndex = 0
                            formatAttemptMode = 0
                            mqSwitchGeneration += 1
                            break
                        }
                    }

                    // 2) Proactive Real-Time Upgrade (LOW -> SD -> HD -> FHD -> 4K):
                    //    When current quality has been 100% smooth for ~4.5s (25 ticks, or 36 ticks ~6.5s for 4K step-up),
                    //    automatically upgrade to the next higher healthy quality!
                    // Upgrades only after long stable playback (~12–16s) — never mid-match noise
                    val upgradeThresholdTicks = if (curVar?.quality == StreamQualityRank.FHD) 90 else 70
                    if (curVar != null && continuousSmoothReadyTicks >= upgradeThresholdTicks) {
                        continuousSmoothReadyTicks = 0
                        val currentKbps = MultiQualityEngine.currentMeasuredSpeedKbps()
                        val upgradeDecision = MultiQualityEngine.tryProactiveUpgrade(
                            multiStableKey = channel.stableKey,
                            currentVariant = curVar,
                            bufferAheadMs = bufferAheadMs,
                            effectiveKbps = currentKbps
                        )
                        if (upgradeDecision != null && upgradeDecision.switched) {
                            isSeamlessMqTransition = true
                            activeMqVariant = upgradeDecision.variant
                            candidateIndex = 0
                            formatAttemptMode = 0
                            mqSwitchGeneration += 1
                            break
                        }
                    }
                }

                // Audio decoding is handled natively by FfmpegAudioRenderer + MediaCodec without forcing software video restarts

                // B) Instant Audio-Only / Frozen-Video Self-Healing Engine (250ms/tick):
                // Detects both:
                //   1) Audio playing with 0 video frames rendered on the new channel (`!hasRenderedFirstVideoFrame || renderedFrames == 0`), AND
                //   2) Video frames completely frozen (`frameDelta == 0`) while audio continues advancing (`posDelta > 30L` or `renderedAudioBuffers > 0`).
                if (frameDelta > 0) {
                    if (!hasRenderedFirstVideoFrame) {
                        hasRenderedFirstVideoFrame = true
                    }
                    frozenPictureTicks = 0
                } else if (channel.contentType == StreamContentType.LIVE_TV && (posDelta > 30L || (player.isPlaying && renderedAudioBuffers > 0))) {
                    frozenPictureTicks++
                    if (frozenPictureTicks == 12 && !surfaceNudgedForCurrentChannel) {
                        surfaceNudgedForCurrentChannel = true
                        SharedLiveSessionBridge.nudgeVideoSurfaceAndRenderer(player, attachedPlayerView)
                    }
                    // Stage 2 (at ~2.5s / 10 ticks): Full Decoder & Stream Recovery (switches to SW video decoder or alternate candidate/variant and reloads!)
                    val freezeLimit = maxFrozenReadyTicks
                    if (frozenPictureTicks >= freezeLimit) {
                        frozenPictureTicks = 0
                        surfaceNudgedForCurrentChannel = false
                        if (isMultiQualityChannel && mqVariants.size >= 2) {
                            val decision = MultiQualityEngine.reportAdaptiveEvent(
                                multiStableKey = channel.stableKey,
                                currentVariant = activeMqVariant,
                                reason = AdaptationReason.SOURCE_CONGESTION
                            )
                            if (decision != null && decision.switched) {
                                isSeamlessMqTransition = true
                                activeMqVariant = decision.variant
                                candidateIndex = 0
                                formatAttemptMode = 0
                                mqSwitchGeneration += 1
                                break
                            }
                        }
                        if (formatCandidateUrls.size > 1 && candidateIndex < 1) {
                            isSeamlessMqTransition = true
                            candidateIndex++
                            formatAttemptMode = 0
                            break
                        } else {
                            // Keep Hardware Video Decoder active! Never force CPU Software Video decoding on a network freeze
                            // (which causes 100% CPU overload on phones & TV boxes).
                            isSeamlessMqTransition = true
                            SharedLiveSessionBridge.rebindVideoSurfaceForChannelSwitch(player, attachedPlayerView)
                            retryCount += 1
                            break
                        }
                    }
                }
            } else if (state == Player.STATE_BUFFERING) {
                frozenPictureTicks = 0
                continuousSmoothReadyTicks = 0

                if (wasReadyInThisSession) {
                    if (midStreamBufferingTicks == 0) {
                        val nowMs = android.os.SystemClock.elapsedRealtime()
                        if (nowMs - firstRebufferAtMs > 18_000L) {
                            recentRebufferEvents = 1
                            firstRebufferAtMs = nowMs
                        } else {
                            recentRebufferEvents += 1
                        }
                    }
                    midStreamBufferingTicks++

                    val curVar = activeMqVariant
                    val currentKbps = MultiQualityEngine.currentMeasuredSpeedKbps()
                    val minNeeded = curVar?.quality?.minSpeedKbps ?: 2_000L
                    val isInternetWeak = currentKbps in 1L..((minNeeded * 85L) / 100L)
                    val readyElapsedMs = if (firstReadyTimestampMs > 0L) {
                        android.os.SystemClock.elapsedRealtime() - firstReadyTimestampMs
                    } else 0L
                    // Never trigger quality switch on a brief <1.5s startup rebuffer; let ExoPlayer resume smoothly in 450ms!
                    val fastTriggerTicks = when {
                        readyElapsedMs < 10_000L -> 12
                        recentRebufferEvents >= 3 || isInternetWeak -> 6
                        else -> 9
                    }

                    if (channel.contentType == StreamContentType.LIVE_TV && isMultiQualityChannel && mqVariants.size >= 2 && midStreamBufferingTicks >= fastTriggerTicks) {
                        val reason = if (isInternetWeak) {
                            AdaptationReason.INTERNET_FLUCTUATION
                        } else {
                            AdaptationReason.SOURCE_CONGESTION
                        }
                        val decision = MultiQualityEngine.reportAdaptiveEvent(
                            multiStableKey = channel.stableKey,
                            currentVariant = curVar,
                            reason = reason,
                            currentSpeedKbps = currentKbps
                        )
                        if (decision != null && decision.switched) {
                            isSeamlessMqTransition = true
                            activeMqVariant = decision.variant
                            candidateIndex = 0
                            formatAttemptMode = 0
                            midStreamBufferingTicks = 0
                            recentRebufferEvents = 0
                            mqSwitchGeneration += 1
                            break
                        }
                    }
                    if (bufDelta > 0L) {
                        midStreamBufferingTicks = 0
                    }
                    val hardResumeTicks = if (channel.contentType == StreamContentType.LIVE_TV) 28 else 32
                    if (midStreamBufferingTicks >= hardResumeTicks) {
                        if (channel.contentType != StreamContentType.LIVE_TV) {
                            val bestPos = maxOf(currentPos.coerceAtLeast(0L), vodPositionMs, pendingResumeTargetMs)
                            if (bestPos > 2_000L) {
                                vodPositionMs = bestPos
                                pendingResumeTargetMs = bestPos
                                onSaveVodProgress(channel, bestPos, vodDurationMs)
                            }
                        }
                        isSeamlessMqTransition = true
                        midStreamBufferingTicks = 0
                        recentRebufferEvents = 0
                        errorMessage = "SOURCE_BUFFER_STALL"
                        break
                    }
                }

                if (renderedFrames == 0 && posDelta <= 0L && bufDelta <= 0L) {
                    bufferingStallTicks++
                    val multiStallLimit = if (isMultiQualityChannel && mqVariants.size >= 2) {
                        maxInitialStallTicks.coerceAtLeast(8)
                    } else {
                        maxInitialStallTicks
                    }
                    if (bufferingStallTicks >= multiStallLimit) {
                        if (channel.contentType != StreamContentType.LIVE_TV) {
                            val bestPos = maxOf(currentPos.coerceAtLeast(0L), vodPositionMs, pendingResumeTargetMs)
                            if (bestPos > 2_000L) {
                                vodPositionMs = bestPos
                                pendingResumeTargetMs = bestPos
                                onSaveVodProgress(channel, bestPos, vodDurationMs)
                            }
                        }
                        if (channel.contentType == StreamContentType.LIVE_TV && isMultiQualityChannel && mqVariants.size >= 2) {
                            val curVar = activeMqVariant
                            val currentKbps = MultiQualityEngine.currentMeasuredSpeedKbps()
                            val minNeeded = curVar?.quality?.minSpeedKbps ?: 2_000L
                            val isInternetWeak = currentKbps in 1L..((minNeeded * 75L) / 100L)
                            val decision = MultiQualityEngine.reportAdaptiveEvent(
                                multiStableKey = channel.stableKey,
                                currentVariant = curVar,
                                reason = if (isInternetWeak) AdaptationReason.INTERNET_FLUCTUATION else AdaptationReason.SOURCE_CONGESTION,
                                currentSpeedKbps = currentKbps
                            )
                            if (decision != null && decision.switched) {
                                isSeamlessMqTransition = true
                                activeMqVariant = decision.variant
                                candidateIndex = 0
                                formatAttemptMode = 0
                                bufferingStallTicks = 0
                                mqSwitchGeneration += 1
                                break
                            }
                        }
                        if (formatCandidateUrls.size > 1 && candidateIndex < 1) {
                            isSeamlessMqTransition = true
                            candidateIndex++
                            formatAttemptMode = 0
                            bufferingStallTicks = 0
                            break
                        } else {
                            bufferingStallTicks = 0
                            errorMessage = "INITIAL_OPEN_STALL"
                            break
                        }
                    }
                } else {
                    bufferingStallTicks = 0
                }
            } else if ((state == Player.STATE_IDLE || state == Player.STATE_ENDED) && errorMessage == null) {
                val isCompletedVod = channel.contentType != StreamContentType.LIVE_TV &&
                    state == Player.STATE_ENDED &&
                    vodDurationMs > 60_000L &&
                    maxOf(currentPos, vodPositionMs) >= vodDurationMs - 30_000L
                if (!isCompletedVod) {
                    bufferingStallTicks++
                    if (bufferingStallTicks >= 16) {
                        bufferingStallTicks = 0
                        if (channel.contentType != StreamContentType.LIVE_TV) {
                            val bestPos = maxOf(currentPos.coerceAtLeast(0L), vodPositionMs, pendingResumeTargetMs)
                            if (bestPos > 2_000L) {
                                vodPositionMs = bestPos
                                pendingResumeTargetMs = bestPos
                                onSaveVodProgress(channel, bestPos, vodDurationMs)
                            }
                        }
                        errorMessage = "UNEXPECTED_STREAM_STOP"
                        break
                    }
                }
            }
        }
    }

    val latestVodPosRef = remember(channel.stableKey) {
        java.util.concurrent.atomic.AtomicLong(initialResumePositionMs.coerceAtLeast(0L))
    }
    val latestVodDurRef = remember(channel.stableKey) {
        java.util.concurrent.atomic.AtomicLong(0L)
    }

    fun flushVodProgressNow(forceMinimumEntry: Boolean = false) {
        if (channel.contentType == StreamContentType.LIVE_TV) return
        val rawPlayerPos = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
        val rawPlayerDur = runCatching {
            player.duration.let { if (it == C.TIME_UNSET || it < 0L) 0L else it }
        }.getOrDefault(0L)
        val bestPos = maxOf(
            rawPlayerPos,
            latestVodPosRef.get(),
            vodPositionMs,
            pendingResumeTargetMs,
            if (forceMinimumEntry) 3_500L else 0L
        )
        val bestDur = maxOf(rawPlayerDur, latestVodDurRef.get(), vodDurationMs)
        if (bestPos >= 2_000L) {
            latestVodPosRef.set(bestPos)
            if (bestDur > 0L) latestVodDurRef.set(bestDur)
            vodPositionMs = bestPos
            if (bestDur > 0L) vodDurationMs = bestDur
            onSaveVodProgress(channel, bestPos, bestDur)
        }
    }

    // Smart Ticker: tracks exact VOD/Series position every second and persists progress reliably
    LaunchedEffect(player, channel.stableKey) {
        var tickCount = 0
        while (true) {
            delay(1000L)
            PlaybackMetrics.heartbeat(channel.stableKey, player.isPlaying, player.currentPosition)
            if (showControls || isBuffering || errorMessage != null) {
                val sampled = NetworkSpeedMonitor.sampleCurrentSpeedLabel()
                if (sampled != networkSpeedLabel) networkSpeedLabel = sampled
            }
            if (channel.contentType != StreamContentType.LIVE_TV) {
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.let { if (it == C.TIME_UNSET || it < 0L) 0L else it }
                if (pos > 0L) {
                    latestVodPosRef.set(pos)
                }
                if (dur > 0L) {
                    latestVodDurRef.set(dur)
                }
                if (showControls || vodDurationMs == 0L || tickCount <= 3) {
                    if (pos > 0L || pendingResumeTargetMs <= 0L) {
                        vodPositionMs = maxOf(pos, latestVodPosRef.get())
                    }
                    if (dur > 0L && dur != vodDurationMs) {
                        vodDurationMs = dur
                    }
                }
                if (pendingResumeTargetMs > 2_000L && pos >= pendingResumeTargetMs - 2_000L) {
                    pendingResumeTargetMs = 0L
                }
                tickCount++
                if ((tickCount == 2 || tickCount % 8 == 0) && (pos >= 1_000L || hasRenderedFirstVideoFrame)) {
                    flushVodProgressNow(forceMinimumEntry = true)
                }
            }
        }
    }

    DisposableEffect(player, channel.stableKey) {
        PlaybackMetrics.start(channel.stableKey, channel.name)
        var lastAutoSeekRestartMs = 0L
        var rapidAutoRestartCount = 0
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                PlaybackMetrics.onState(channel.stableKey, playbackState)
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    sameUrlRetryAttempts = 0
                    isSourceStopped = false
                    isAutoReconnecting = false
                    autoReconnectAttempt = 0
                    showBufferingBadge = false
                    errorMessage = null
                    fatalHttpErrorBanner = null
                    playerDiagnosticAlert = null
                    lastErrorCodeName = ""
                    fastFallbackTriggered = false
                    retryCount = 0
                    currentTracksSnapshot = player.currentTracks
                    if (channel.contentType != StreamContentType.LIVE_TV && pendingResumeTargetMs > 2_000L) {
                        val curPos = player.currentPosition.coerceAtLeast(0L)
                        if (curPos < pendingResumeTargetMs - 2_500L) {
                            player.seekTo(pendingResumeTargetMs)
                        } else {
                            pendingResumeTargetMs = 0L
                        }
                    }
                    if (channel.contentType != StreamContentType.LIVE_TV) {
                        flushVodProgressNow(forceMinimumEntry = true)
                    }
                    if (channel.contentType == StreamContentType.LIVE_TV) {
                        FastFormatPreferenceCache.recordWorkingLiveUrl(channel.playlistId, effectiveUrl)
                    }
                } else if (playbackState == Player.STATE_ENDED && channel.contentType != StreamContentType.LIVE_TV) {
                    val curPos = maxOf(player.currentPosition.coerceAtLeast(0L), vodPositionMs, pendingResumeTargetMs)
                    val dur = player.duration.let { if (it == C.TIME_UNSET || it < 0L) vodDurationMs else it }
                    val endedPrematurely = (dur > 60_000L && curPos in 2_000L..(dur - 45_000L)) || (dur <= 0L && curPos > 2_000L)
                    if (endedPrematurely) {
                        vodPositionMs = curPos
                        pendingResumeTargetMs = curPos
                        onSaveVodProgress(channel, curPos, dur)
                        errorMessage = "VOD_PREMATURE_EOF"
                    }
                } else if (playbackState == Player.STATE_ENDED && preferences.autoReconnect &&
                    channel.contentType == StreamContentType.LIVE_TV
                ) {
                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    if (nowMs - lastAutoSeekRestartMs < 3_500L) {
                        rapidAutoRestartCount++
                    } else {
                        rapidAutoRestartCount = 1
                    }
                    lastAutoSeekRestartMs = nowMs
                    if (isMultiQualityChannel && mqVariants.size >= 2) {
                        val decision = MultiQualityEngine.reportAdaptiveEvent(
                            multiStableKey = channel.stableKey,
                            currentVariant = activeMqVariant,
                            reason = AdaptationReason.SOURCE_OUTAGE
                        )
                        if (decision != null && decision.switched) {
                            activeMqVariant = decision.variant
                            candidateIndex = 0
                            formatAttemptMode = 0
                            mqSwitchGeneration += 1
                            return
                        }
                    }
                    // Anti-Flicker Guard: if STATE_ENDED fires repeatedly within 3.5s, do NOT hot-loop
                    // seekToDefaultPosition()/prepare() (which replays the same 0.3s clip rapidly).
                    // Escalate to clean backed-off errorMessage reconnect instead!
                    if (rapidAutoRestartCount >= 2) {
                        ChannelRepository.clearStreamRedirectCache(effectiveUrl)
                        errorMessage = "LIVE_ENDED_BACKOFF"
                    } else {
                        player.seekToDefaultPosition()
                        player.prepare()
                        player.play()
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracksSnapshot = tracks
                var vCodecLabel = ""
                var aCodecLabel = ""
                var hasVideoTrack = false
                var hasSupportedVideoTrack = false
                var hasAudioTrack = false
                var hasSupportedAudioTrack = false
                var fallbackSupportedAudioGroup: Tracks.Group? = null
                var fallbackSupportedAudioIndex = -1

                var firstVideoGroup: Tracks.Group? = null
                var anyVideoSelected = false
                var hasExceedsCapabilitiesVideo = false

                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        hasVideoTrack = true
                        if (firstVideoGroup == null && group.length > 0) {
                            firstVideoGroup = group
                        }
                        for (i in 0 until group.length) {
                            // Metrics report strict device support only; tracks that exceed
                            // device capabilities remain eligible for the existing fallback path.
                            if (group.isTrackSupported(i, false)) {
                                hasSupportedVideoTrack = true
                            }
                            if (group.getTrackSupport(i) == C.FORMAT_EXCEEDS_CAPABILITIES) {
                                hasExceedsCapabilitiesVideo = true
                            }
                            if (group.isTrackSelected(i)) {
                                anyVideoSelected = true
                                if (vCodecLabel.isEmpty()) {
                                    val fmt = group.getTrackFormat(i)
                                    val mime = fmt.sampleMimeType.orEmpty().lowercase()
                                    vCodecLabel = when {
                                        mime.contains("hevc") || mime.contains("h265") -> "HEVC/H.265"
                                        mime.contains("avc") || mime.contains("h264") -> "H.264/AVC"
                                        mime.contains("av01") || mime.contains("av1") -> "AV1"
                                        mime.contains("vp9") -> "VP9"
                                        mime.contains("mp2") || mime.contains("mpeg2") -> "MPEG-2"
                                        mime.contains("mp4v") || mime.contains("xvid") -> "MPEG-4/XviD"
                                        mime.isNotBlank() -> mime.substringAfter("/").uppercase()
                                        else -> ""
                                    }
                                }
                            }
                        }
                    } else if (group.type == C.TRACK_TYPE_AUDIO) {
                        hasAudioTrack = true
                        for (i in 0 until group.length) {
                            val fmt = group.getTrackFormat(i)
                            val mime = fmt.sampleMimeType.orEmpty().lowercase(Locale.ROOT)
                            // Accept both strictly supported and exceeds-capabilities audio tracks because
                            // FfmpegAudioRenderer + StereoDownmixAudioProcessor decode & downmix all 5.1/7.1 codecs!
                            val audioSupported = group.isTrackSupported(i, true)
                            if (audioSupported) {
                                hasSupportedAudioTrack = true
                            }
                            if (audioSupported || fallbackSupportedAudioGroup == null) {
                                if (fallbackSupportedAudioGroup == null || fallbackSupportedAudioIndex < 0) {
                                    fallbackSupportedAudioGroup = group
                                    fallbackSupportedAudioIndex = i
                                } else if (audioSupported && !fallbackSupportedAudioGroup!!.isTrackSupported(fallbackSupportedAudioIndex, true)) {
                                    fallbackSupportedAudioGroup = group
                                    fallbackSupportedAudioIndex = i
                                }
                            }
                            if (group.isTrackSelected(i) && aCodecLabel.isEmpty()) {
                                aCodecLabel = when {
                                    mime.contains("eac3") -> "E-AC3"
                                    mime.contains("ac3") -> "AC3"
                                    mime.contains("mp4a") || mime.contains("aac") -> "AAC"
                                    mime.contains("opus") -> "Opus"
                                    mime.contains("mpeg-l2") || mime.contains("mp2") -> "MP2"
                                    mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
                                    mime.contains("dts") -> "DTS"
                                    mime.contains("flac") -> "FLAC"
                                    mime.contains("vorbis") -> "Vorbis"
                                    mime.isNotBlank() -> mime.substringAfter("/").uppercase()
                                    else -> ""
                                }
                            }
                        }
                    }
                }

                // Force-bind real 4K UHD video track ONLY if DefaultTrackSelector left all video tracks unselected
                // (prevents audio-only black screen on 4K HEVC Main10 / HDR10 / 50fps streams without causing flicker).
                if (hasVideoTrack && !anyVideoSelected && firstVideoGroup != null && (hasSupportedVideoTrack || hasExceedsCapabilitiesVideo)) {
                    var bestTrackIdx = 0
                    var bestPixels = -1
                    var bestBitrate = -1
                    for (tIdx in 0 until firstVideoGroup.length) {
                        val fmt = firstVideoGroup.getTrackFormat(tIdx)
                        val px = fmt.width.coerceAtLeast(0) * fmt.height.coerceAtLeast(0)
                        val br = fmt.bitrate.coerceAtLeast(0)
                        if (px > bestPixels || (px == bestPixels && br > bestBitrate)) {
                            bestPixels = px
                            bestBitrate = br
                            bestTrackIdx = tIdx
                        }
                    }
                    runCatching {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .clearViewportSizeConstraints()
                            .clearVideoSizeConstraints()
                            .setForceHighestSupportedBitrate(true)
                            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                            .setOverrideForType(
                                TrackSelectionOverride(firstVideoGroup.mediaTrackGroup, listOf(bestTrackIdx))
                            )
                            .build()
                    }
                }

                // 2) Guaranteed Audio Track Binding: if a Movie/Series/Channel has an audio track
                //    but DefaultTrackSelector left audio unselected, force-bind the best audio track immediately!
                if (hasAudioTrack && fallbackSupportedAudioGroup != null && fallbackSupportedAudioIndex >= 0) {
                    val anyAudioSelected = tracks.groups.any { g ->
                        g.type == C.TRACK_TYPE_AUDIO && (0 until g.length).any { idx -> g.isTrackSelected(idx) }
                    }
                    if (!anyAudioSelected) {
                        runCatching {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setMaxAudioChannelCount(Int.MAX_VALUE)
                                .setMaxAudioBitrate(Int.MAX_VALUE)
                                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                .setOverrideForType(
                                    TrackSelectionOverride(
                                        fallbackSupportedAudioGroup!!.mediaTrackGroup,
                                        listOf(fallbackSupportedAudioIndex)
                                    )
                                )
                                .build()
                        }
                    }
                }

                // 3) Instant Codec & Audio Rescue via LibVLC:
                //    - If the video codec is unsupported by ExoPlayer, OR
                //    - If the stream has an audio track (e.g. AC3, E-AC3, DTS, MP2, TrueHD, WMA, 5.1) that is NOT strictly
                //      supported by ExoPlayer's MediaCodec on this device (which would otherwise play Video with NO SOUND!):
                //    Immediately hand over to LibVLC (which has built-in FFmpeg software audio decoding + Hardware Video)!
                // Hardware Video + FFmpeg Audio renderers handle all tracks natively without restarting playback

                val engineModeBadge = if (forceExoSoftwareCodec.get()) "Exo+FFmpeg SW" else "Exo+FFmpeg HW"
                val mqPrefix = activeMqVariant?.let { "جودة ${it.quality.labelAr} • " }.orEmpty()
                val combinedCodec = listOfNotNull(
                    vCodecLabel.takeIf { it.isNotBlank() },
                    aCodecLabel.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                PlaybackMetrics.onCodecInfo(
                    channel.stableKey,
                    vCodecLabel,
                    aCodecLabel,
                    hasSupportedVideoTrack,
                    hasSupportedAudioTrack
                )
                if (combinedCodec.isNotBlank()) {
                    detectedSpecLabel = "$mqPrefix$engineModeBadge • $combinedCodec"
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing && channel.contentType != StreamContentType.LIVE_TV) {
                    flushVodProgressNow(forceMinimumEntry = hasRenderedFirstVideoFrame)
                }
            }

            override fun onRenderedFirstFrame() {
                PlaybackMetrics.onFirstFrame(channel.stableKey)
                hasRenderedFirstVideoFrame = true
                isBuffering = false
                showBufferingBadge = false
                if (channel.contentType != StreamContentType.LIVE_TV) {
                    flushVodProgressNow(forceMinimumEntry = true)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    PlaybackMetrics.onVideoSize(channel.stableKey, videoSize.width, videoSize.height)
                    hasRenderedFirstVideoFrame = true
                    val tag = when {
                        videoSize.height >= 2160 || videoSize.width >= 3840 -> "4K UHD"
                        videoSize.height >= 1080 -> "1080p FHD"
                        videoSize.height >= 720 -> "720p HD"
                        else -> "${videoSize.height}p SD"
                    }
                    videoResolutionLabel = "${videoSize.width}×${videoSize.height} • $tag"
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (channel.contentType != StreamContentType.LIVE_TV) {
                    val bestPos = maxOf(player.currentPosition.coerceAtLeast(0L), vodPositionMs, pendingResumeTargetMs)
                    if (bestPos > 2_000L) {
                        vodPositionMs = bestPos
                        pendingResumeTargetMs = bestPos
                        onSaveVodProgress(channel, bestPos, vodDurationMs)
                    }
                }
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    if (nowMs - lastAutoSeekRestartMs < 3_500L) {
                        rapidAutoRestartCount++
                    } else {
                        rapidAutoRestartCount = 1
                    }
                    lastAutoSeekRestartMs = nowMs
                    if (rapidAutoRestartCount >= 2) {
                        ChannelRepository.clearStreamRedirectCache(effectiveUrl)
                        errorMessage = "BEHIND_LIVE_BACKOFF"
                    } else {
                        player.seekToDefaultPosition()
                        player.prepare()
                        player.play()
                    }
                    return
                }
                lastErrorCodeName = error.errorCodeName
                var httpResponseCode = -1
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    var httpCause: Throwable? = error.cause
                    while (httpCause != null) {
                        if (httpCause is HttpDataSource.InvalidResponseCodeException) {
                            httpResponseCode = httpCause.responseCode
                            break
                        }
                        httpCause = httpCause.cause
                    }
                    // [2] Smart Xtream Transient HTTP Recovery (401/403/429/458/460/500/502/503/504):
                    // Many Xtream panels return 401/403/458/502 briefly while releasing the previous channel's connection slot
                    // or warming up an on-demand edge node. Flush stale sockets/redirects and retry quietly before showing any error!
                    ChannelRepository.clearHostSessionCookies(effectiveUrl)
                    ChannelRepository.clearStreamRedirectCache(effectiveUrl)
                    if (httpResponseCode in listOf(401, 403, 429, 458, 460, 500, 502, 503, 504)) {
                        ChannelRepository.evictIdleApiConnections()
                        if (sameUrlRetryAttempts < 2) {
                            sameUrlRetryAttempts += 1
                            errorMessage = "HTTP_TRANSIENT_RETRY_SAME"
                            return
                        }
                        if (httpResponseCode == 458) {
                            val msg458 = "⛔ السيرفر يرفض الاتصال (تجاوز الحد الأقصى). يرجى الانتظار دقيقة."
                            ChannelRepository.forceCloseAllStreamConnections()
                            player.safeStop()
                            fatalHttpErrorBanner = msg458
                            errorMessage = msg458
                            isBuffering = false
                            showBufferingBadge = false
                            isAutoReconnecting = false
                            isSourceStopped = false
                            fastFallbackTriggered = false
                            Toast.makeText(context, msg458, Toast.LENGTH_LONG).show()
                            return
                        }
                        if (httpResponseCode == 460) {
                            val msg460 = "⛔ جلسة غير صالحة. يرجى إعادة المحاولة."
                            ChannelRepository.forceCloseAllStreamConnections()
                            player.safeStop()
                            fatalHttpErrorBanner = msg460
                            errorMessage = msg460
                            isBuffering = false
                            showBufferingBadge = false
                            isAutoReconnecting = false
                            isSourceStopped = false
                            fastFallbackTriggered = false
                            Toast.makeText(context, msg460, Toast.LENGTH_LONG).show()
                            return
                        }
                        if (httpResponseCode == 502 && formatCandidateUrls.size > 1 && candidateIndex + 1 < formatCandidateUrls.size) {
                            retried502ForCurrentCandidate = false
                            isSeamlessMqTransition = true
                            candidateIndex += 1
                            formatAttemptMode = 0
                            errorMessage = null
                            return
                        }
                    }
                }

                val isCodecError =
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                    error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
                val isContainerMismatch =
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
                val isHardIoFail =
                    httpResponseCode in listOf(401, 403, 404, 410, 500, 502, 503) ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

                // [1] DTS/FFmpeg Codec Fallback:
                // Do NOT show "Codec not supported" immediately. Suppress it to allow FFmpeg / software decoder to take over.
                // Only if FFmpeg fails too, show: "⚠️ تعذر تشغيل الصوت أو الفيديو لهذه الصيغة"
                if (isCodecError) {
                    if (!ffmpegFallbackAttempted || !forceExoSoftwareCodec.get()) {
                        ffmpegFallbackAttempted = true
                        isSeamlessMqTransition = true
                        forceExoSoftwareCodec.set(true)
                        forceVlcSoftwareVideo = true
                        codecRequiresVlc = true
                        errorMessage = null
                        retryCount += 1
                        return
                    } else if (formatCandidateUrls.size > 1 && candidateIndex + 1 < formatCandidateUrls.size) {
                        ffmpegFallbackAttempted = false
                        isSeamlessMqTransition = true
                        candidateIndex += 1
                        formatAttemptMode = 0
                        errorMessage = null
                        return
                    } else {
                        val codecFailMsg = "⚠️ تعذر تشغيل الصوت أو الفيديو لهذه الصيغة"
                        player.safeStop()
                        fatalHttpErrorBanner = codecFailMsg
                        errorMessage = codecFailMsg
                        isBuffering = false
                        showBufferingBadge = false
                        isAutoReconnecting = false
                        isSourceStopped = false
                        Toast.makeText(context, codecFailMsg, Toast.LENGTH_LONG).show()
                        return
                    }
                }

                // [5] Smart Instant Container & Extension Failover for VOD Movies & Series:
                // Many Xtream panels report .mp4 in bulk lists when the actual file on the storage node is .mkv (or vice versa).
                // Before showing any error, immediately step through formatCandidateUrls (.mkv / .mp4 / .ts) in 0ms!
                if (channel.contentType != StreamContentType.LIVE_TV) {
                    if ((isContainerMismatch || httpResponseCode in listOf(400, 404, 415, 500, 502, 503)) &&
                        formatCandidateUrls.size > 1 &&
                        candidateIndex + 1 < formatCandidateUrls.size
                    ) {
                        isSeamlessMqTransition = true
                        candidateIndex += 1
                        formatAttemptMode = 0
                        errorMessage = null
                        return
                    }
                    if (isContainerMismatch && !forceExoSoftwareCodec.get()) {
                        forceExoSoftwareCodec.set(true)
                        isSeamlessMqTransition = true
                        errorMessage = null
                        retryCount += 1
                        return
                    }
                    if (isHardIoFail) {
                        val cleanVodErr = when (httpResponseCode) {
                            404, 410 -> "⚠️ هذا المحتوى غير متوفر حالياً على السيرفر (خطأ $httpResponseCode)."
                            401, 403 -> "⚠️ تعذر التحقق من صلاحية الرابط على السيرفر (خطأ $httpResponseCode)."
                            500, 502, 503 -> "⚠️ السيرفر يواجه ضغطاً أو صيانة مؤقتة (خطأ $httpResponseCode)."
                            else -> "⚠️ تعذر فتح ملف الفيديو من المصدر (${error.errorCodeName})."
                        }
                        player.safeStop()
                        fatalHttpErrorBanner = cleanVodErr
                        errorMessage = cleanVodErr
                        isBuffering = false
                        showBufferingBadge = false
                        isAutoReconnecting = false
                        isSourceStopped = false
                        return
                    }
                    if (isContainerMismatch) {
                        val codecErrMsg = "⚠️ تعذر تشغيل الصوت أو الفيديو لهذه الصيغة"
                        player.safeStop()
                        fatalHttpErrorBanner = codecErrMsg
                        errorMessage = codecErrMsg
                        isBuffering = false
                        showBufferingBadge = false
                        isAutoReconnecting = false
                        isSourceStopped = false
                        return
                    }
                    if (sameUrlRetryAttempts < 2) {
                        sameUrlRetryAttempts += 1
                        isSeamlessMqTransition = true
                        formatAttemptMode = 0
                        errorMessage = null
                        retryCount += 1
                        return
                    }
                    errorMessage = "VOD_NETWORK_DROP"
                    return
                }

                // Smart Anti-Block VPN & ASN Bypass during Live TV & VOD Playback:
                // 1) If server returns HTTP 403 / 451 / 429 while WireGuard tun0 is active (IPTV panel blocking datacenter ASN),
                //    seamlessly switch to Direct ISP IP + Encrypted DoH 1.1.1.1 so the stream opens immediately!
                // 2) If connection times out / resets while VPN is off (ISP blocking stream host), auto-enable Encrypted DoH + VPN!
                if (httpResponseCode in listOf(401, 403, 451) &&
                    CloudflareWarpVpnService.isTunnelRunning &&
                    !CloudflareWarpVpnService.isDohOnlyMode &&
                    sameUrlRetryAttempts == 0
                ) {
                    CloudflareWarpVpnService.switchToDirectEncryptedWarpIfServerBlocksTun0(context)
                    sameUrlRetryAttempts += 1
                    isSeamlessMqTransition = true
                    errorMessage = null
                    retryCount += 1
                    return
                }

                // Live TV: Seamless Redirects & Smart Retry
                val isLiveConnectionDrop =
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT
                if (isLiveConnectionDrop && !CloudflareDohManager.dohEnabled && !LiveProGlobalActions.vpnPermanentDisable) {
                    CloudflareDohManager.dohEnabled = true
                    CloudflareDohManager.clearDnsCache()
                }
                if (channel.contentType == StreamContentType.LIVE_TV && isLiveConnectionDrop && sameUrlRetryAttempts < 1) {
                    sameUrlRetryAttempts += 1
                    errorMessage = "LIVE_SOFT_RETRY"
                    return
                }

                val canTryOneQuickAltFormatInExo = !isCodecError && !isContainerMismatch && !isHardIoFail &&
                    formatCandidateUrls.size > 1 &&
                    candidateIndex < 1
                if (canTryOneQuickAltFormatInExo) {
                    sameUrlRetryAttempts = 0
                    errorMessage = "LIVE_TRY_ALT_FORMAT"
                } else if (isMultiQualityChannel && mqVariants.size >= 2) {
                    val decision = MultiQualityEngine.reportAdaptiveEvent(
                        multiStableKey = channel.stableKey,
                        currentVariant = activeMqVariant,
                        reason = AdaptationReason.SOURCE_OUTAGE
                    )
                    if (decision != null && decision.switched) {
                        sameUrlRetryAttempts = 0
                        isSeamlessMqTransition = true
                        activeMqVariant = decision.variant
                        codecRequiresVlc = false
                        forceVlcSoftwareVideo = false
                        candidateIndex = 0
                        formatAttemptMode = 0
                        errorMessage = null
                        mqSwitchGeneration += 1
                        return
                    } else if (vlcAvailable && !useVlcEngine && !vlcFallbackToExo) {
                        isSeamlessMqTransition = true
                        FastFormatPreferenceCache.markChannelRequiresVlc(channel.stableKey, effectiveUrl)
                        forceExoSoftwareCodec.set(true)
                        forceVlcSoftwareVideo = true
                        codecRequiresVlc = true
                        errorMessage = null
                        retryCount += 1
                    } else {
                        fastFallbackTriggered = true
                        if (errorMessage == null) errorMessage = ""
                    }
                } else {
                    fastFallbackTriggered = true
                    if (errorMessage == null) errorMessage = ""
                }
            }
        }
        player.addListener(listener)
        onDispose {
            runCatching {
                if (channel.contentType != StreamContentType.LIVE_TV) {
                    flushVodProgressNow(forceMinimumEntry = true)
                }
                player.removeListener(listener)
                PlaybackMetrics.finish(channel.stableKey)
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            runCatching {
                // Save VOD / Series watch progress BEFORE player.stop() / clearMediaItems() resets currentPosition!
                if (channel.contentType != StreamContentType.LIVE_TV) {
                    flushVodProgressNow(forceMinimumEntry = true)
                }
                if (activity?.isChangingConfigurations == true) {
                    SharedLiveSessionBridge.detachViewIfBound(player, attachedPlayerView)
                    return@onDispose
                }
                if (channel.contentType == StreamContentType.LIVE_TV &&
                    (SharedLiveSessionBridge.handoverInProgress || SharedLiveSessionBridge.sharedPlayer === player)
                ) {
                    SharedLiveSessionBridge.detachViewIfBound(player, attachedPlayerView)
                    SharedLiveSessionBridge.recordActiveSession(
                        player = player,
                        stableKey = activeSessionChannelKey.ifBlank { channel.stableKey },
                        streamUrl = activeSessionStreamUrl.ifBlank { effectiveUrl }
                    )
                } else {
                    SharedLiveSessionBridge.detachViewIfBound(player, attachedPlayerView)
                    SharedLiveSessionBridge.scheduleSafeRelease(player, sharedAllocator, activeSessionStreamUrl)
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        var wasPlayingBeforeBackground = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasPlayingBeforeBackground = player.isPlaying || player.playWhenReady
                    if (channel.contentType != StreamContentType.LIVE_TV) {
                        val pos = player.currentPosition.coerceAtLeast(0L)
                        val dur = player.duration.let { if (it == C.TIME_UNSET || it < 0L) vodDurationMs else it }
                        if (pos >= 5_000L) {
                            onSaveVodProgress(channel, pos, dur)
                        }
                    }
                    val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        activity?.isInPictureInPictureMode == true
                    } else false
                    if (!inPip) {
                        player.playWhenReady = false
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (wasPlayingBeforeBackground) {
                        if (channel.contentType == StreamContentType.LIVE_TV && player.playbackState == Player.STATE_IDLE) {
                            player.prepare()
                        }
                        player.playWhenReady = true
                        player.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // نظام إعادة المحاولة التلقائي المستمر في الخلفية:
    // - عند الانقطاع السريع، يحاول فوراً وبصمت (خلال 150ms–300ms) دون إزعاج المشاهد بأي نافذة.
    // - إذا استمر توقف المصدر، يعرض فقط عجلة تحميل وعبارة "متوقف من المصدر" ويواصل المحاولة تلقائياً في الخلفية
    //   وعند عودة البث من المصدر يشتغل تلقائياً وتختفي شاشة التوقف فوراً!
    // نظام إعادة الاتصال التلقائي مع Exponential Backoff وحفظ واستئناف آخر موضع توقف للفلم تلقائياً:
    // - يحفظ آخر موضع توقف للفلم تلقائياً (pendingResumeTargetMs) قبل كل محاولة إعادة اتصال.
    // - ينفذ محاولات إعادة اتصال تلقائية متدرجة (Backoff: 0.6s -> 1.5s -> 3.0s -> 5.5s -> 9.0s -> 14.0s) مع إظهار حالة واضحة للمستخدم.
    // - يستأنف التشغيل تلقائياً من نفس النقطة التي توقف عندها الفلم بمجرد نجاح الاتصال.
    // - لا يُظهر حالة «متوقف من المصدر» النهائية إلا بعد استنفاد جميع المحاولات التلقائية (6 محاولات)، مع استمرار فحص دوري في الخلفية وإتاحة زر إعادة المحاولة الفورية.
    // Zero-Flicker Silent Background Recovery Engine:
    // 1) For brief transient blips (first 2 retries), waits smoothly before trying an alternate format once.
    // 2) If the channel is genuinely down at the source, STOPS ExoPlayer completely (0% screen flicker, 0% GPU/CPU thrashing!)
    //    and hands over to the silent background HTTP probe below, which checks silently every 5-8s without touching the screen
    //    and wakes ExoPlayer ONLY the instant live media bytes return!
    LaunchedEffect(channel.stableKey, errorMessage) {
        if (errorMessage != null) {
            if (errorMessage?.startsWith("⛔") == true || errorMessage?.startsWith("⚠️") == true || fatalHttpErrorBanner != null) {
                return@LaunchedEffect
            }
            if (channel.contentType != StreamContentType.LIVE_TV) {
                val bestPos = maxOf(player.currentPosition.coerceAtLeast(0L), vodPositionMs, pendingResumeTargetMs)
                if (bestPos > 2_000L) {
                    vodPositionMs = bestPos
                    pendingResumeTargetMs = bestPos
                    onSaveVodProgress(channel, bestPos, vodDurationMs)
                }
            }

            if (errorMessage == "HTTP_502_RETRY_SAME" || errorMessage == "HTTP_TRANSIENT_RETRY_SAME") {
                isSourceStopped = false
                isAutoReconnecting = true
                delay(if (sameUrlRetryAttempts <= 1) 1_800L else 2_600L)
                if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                    isAutoReconnecting = false
                    errorMessage = null
                    return@LaunchedEffect
                }
                isSeamlessMqTransition = true
                errorMessage = null
                retryCount += 1
                return@LaunchedEffect
            }

            // Live TV: After 2 quiet attempts (primary + alternate format), STOP ExoPlayer cleanly and enter
            // Zero-Flicker Silent Background Probe mode instead of repeatedly flashing the screen!
            val fastRetryCap = if (channel.contentType == StreamContentType.LIVE_TV) 2 else maxAutoReconnectAttempts
            if (retryCount < fastRetryCap) {
                isSourceStopped = false
                isAutoReconnecting = true
                autoReconnectAttempt = retryCount + 1
                playerDiagnosticAlert = null
                val backoffMs = if (retryCount == 0) 900L else 1_600L
                delay(backoffMs)

                if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                    isSourceStopped = false
                    isAutoReconnecting = false
                    autoReconnectAttempt = 0
                    errorMessage = null
                    return@LaunchedEffect
                }

                isSeamlessMqTransition = true
                if (channel.contentType == StreamContentType.LIVE_TV && formatCandidateUrls.size > 1 && candidateIndex + 1 < formatCandidateUrls.size) {
                    candidateIndex += 1
                    formatAttemptMode = 0
                }
                errorMessage = null
                retryCount += 1
            } else {
                // Stop ExoPlayer completely so the screen stays 100% calm & steady with ZERO flicker!
                runCatching {
                    player.playWhenReady = false
                    player.stop()
                    player.clearMediaItems()
                }
                isBuffering = false
                showBufferingBadge = false
                isAutoReconnecting = false
                isSourceStopped = true
                playerDiagnosticAlert = null
            }
        }
    }

    // Silent Background Probe Loop (Zero-Flicker):
    // Runs ONLY while `isSourceStopped == true` on Dispatchers.IO. Never touches ExoPlayer or Surface while channel is dead!
    LaunchedEffect(channel.stableKey, isSourceStopped) {
        if (!isSourceStopped || fatalHttpErrorBanner != null) return@LaunchedEffect
        val probeUa = activeStreamChannel.userAgent?.takeIf { it.isNotBlank() }
            ?: channel.userAgent?.takeIf { it.isNotBlank() }
            ?: STREAM_USER_AGENTS.first()
        var silentChecks = 0
        while (isSourceStopped && fatalHttpErrorBanner == null) {
            val waitMs = if (silentChecks < 12) 5_000L else 8_000L
            delay(waitMs)
            if (!isSourceStopped || fatalHttpErrorBanner != null) break
            silentChecks++
            val candidatesToProbe = formatCandidateUrls.ifEmpty { listOf(effectiveUrl) }
            var aliveIdx = -1
            for ((idx, candUrl) in candidatesToProbe.withIndex()) {
                if (StreamFormatDetectorAndRenewer.probeStreamIsAliveSilently(activeStreamChannel, candUrl, probeUa)) {
                    aliveIdx = idx
                    break
                }
            }
            if (aliveIdx >= 0 && isSourceStopped) {
                isSourceStopped = false
                isAutoReconnecting = false
                errorMessage = null
                sameUrlRetryAttempts = 0
                candidateIndex = aliveIdx
                formatAttemptMode = 0
                isSeamlessMqTransition = true
                retryCount = 0
                // Trigger clean single playback wake-up now that real broadcast bytes are confirmed!
                retryCount = 1
                break
            }
        }
    }

    LaunchedEffect(showZappingOsd, zappingOsdTick, channel.stableKey) {
        if (showZappingOsd) {
            delay(3_000L)
            showZappingOsd = false
        }
    }

    LaunchedEffect(numberInputBuffer) {
        if (numberInputBuffer.isNotEmpty()) {
            delay(950L)
            val targetNum = numberInputBuffer.toIntOrNull()
            numberInputBuffer = ""
            if (targetNum != null) {
                val match = siblingChannels.find { it.number == targetNum }
                    ?: allChannels.find { it.number == targetNum }
                if (match != null) {
                    showControls = false
                    onSelectChannel(match)
                }
            }
        }
    }

    // إخفاء تلقائي احترافي بنمط نتفلكس: عندما يتوقف المستخدم عن التنقل بالريموت لمدة 3.5 ثانية تختفي الأزرار والشريط العلوي والسفلي تلقائياً لتسهيل المشاهدة
    LaunchedEffect(showControls, userNavInteractionTick, showQuickDrawer, showTracksDialog) {
        if (showControls && !showQuickDrawer && !showTracksDialog && errorMessage == null) {
            delay(4000L)
            if (!showQuickDrawer && !showTracksDialog) {
                showControls = false
            }
        }
    }

    LaunchedEffect(sleepTimerRemainingMin) {
        if (sleepTimerRemainingMin > 0) {
            delay(60_000L)
            sleepTimerRemainingMin = (sleepTimerRemainingMin - 1).coerceAtLeast(0)
            ProSubtitleAndPlayerSettings.sleepTimerMinutes = sleepTimerRemainingMin
            if (sleepTimerRemainingMin == 0) {
                player.safePause()
                showControls = true
            }
        }
    }

    // TV remote BACK: close overlays first and keep watching the SAME channel.
    // Never exit the player while the bouquet/channel drawer or controls are open.
    BackHandler(enabled = true) {
        when {
            showTracksDialog -> {
                showTracksDialog = false
            }
            showQuickDrawer -> {
                showQuickDrawer = false
                // Restore D-Pad focus to the video surface so the user continues watching immediately
            }
            showControls -> {
                showControls = false
            }
            else -> {
                if (channel.contentType != StreamContentType.LIVE_TV) {
                    flushVodProgressNow(forceMinimumEntry = true)
                }
                onBack()
            }
        }
    }

    fun currentVodPositionMs(): Long {
        return player.currentPosition.coerceAtLeast(0L)
    }

    fun seekVodTo(targetMs: Long) {
        val safeTarget = if (vodDurationMs > 0L) {
            targetMs.coerceIn(0L, vodDurationMs)
        } else {
            targetMs.coerceAtLeast(0L)
        }
        pendingResumeTargetMs = 0L
        vodPositionMs = safeTarget
        player.safeSeekTo(safeTarget)
        if (channel.contentType != StreamContentType.LIVE_TV && safeTarget >= 2_000L) {
            onSaveVodProgress(channel, safeTarget, vodDurationMs)
        }
    }

    fun seekVodByOffset(deltaMs: Long) {
        val basePos = maxOf(currentVodPositionMs(), vodPositionMs)
        seekVodTo(basePos + deltaMs)
    }

    fun computeSmartSeekStepMs(repeatCount: Int): Long {
        return when {
            repeatCount <= 0 -> 10_000L
            repeatCount <= 4 -> 25_000L
            repeatCount <= 12 -> 60_000L
            else -> 180_000L
        }
    }

    fun switchRelativeChannel(offset: Int) {
        if (!SafeClickGuard.allowAction("PLAYER_ZAP", 15L)) return
        val list = siblingChannels.ifEmpty { allChannels }
        if (list.size <= 1) return
        val idx = list.indexOfFirst { it.stableKey == channel.stableKey }
        if (idx == -1) return
        if (channel.contentType != StreamContentType.LIVE_TV) {
            flushVodProgressNow(forceMinimumEntry = true)
        }
        val nextIdx = (idx + offset + list.size) % list.size
        showControls = false
        showZappingOsd = true
        zappingOsdTick += 1L
        onSelectChannel(list[nextIdx])
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootPlayerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { previewEv ->
                if (previewEv.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    userNavInteractionTick += 1L
                }
                false
            }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    userNavInteractionTick += 1L
                    val code = event.nativeKeyEvent.keyCode
                    val rep = event.nativeKeyEvent.repeatCount
                    // BACK while drawer/dialog/controls open => dismiss overlay, stay on same live channel
                    if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                        when {
                            showTracksDialog -> {
                                showTracksDialog = false
                                return@onKeyEvent true
                            }
                            showQuickDrawer -> {
                                showQuickDrawer = false
                                return@onKeyEvent true
                            }
                            showControls -> {
                                showControls = false
                                return@onKeyEvent true
                            }
                        }
                    }
                    // While bouquet/channel drawer or tracks dialog has focus, let its own handlers process D-Pad
                    if (showQuickDrawer || showTracksDialog) {
                        return@onKeyEvent false
                    }
                    if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                        val digit = (code - KeyEvent.KEYCODE_0).toString()
                        if (numberInputBuffer.length < 4) {
                            numberInputBuffer += digit
                        }
                        return@onKeyEvent true
                    }
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (isPlayerScreenLocked) {
                                isPlayerScreenLocked = false
                                showControls = true
                                true
                            } else if (showQuickDrawer) {
                                // already open — let drawer handlers process
                                false
                            } else if (channel.contentType == StreamContentType.LIVE_TV) {
                                // البث المباشر: OK يفتح قائمة الباقات والقنوات فوراً
                                showControls = false
                                drawerSelectedBouquet = channel.group
                                drawerOpenTimestampMs = android.os.SystemClock.elapsedRealtime()
                                showQuickDrawer = true
                                focusFirstChannelTrigger += 1
                                true
                            } else if (rep > 0) {
                                if (!showQuickDrawer) {
                                    showControls = false
                                    drawerSelectedBouquet = channel.group
                                    drawerOpenTimestampMs = android.os.SystemClock.elapsedRealtime()
                                    showQuickDrawer = true
                                    focusFirstChannelTrigger += 1
                                }
                                true
                            } else if (rep == 0) {
                                if (!showControls && !showQuickDrawer) {
                                    lastControlFocusRequester = centerPlayFocusRequester
                                    showControls = true
                                    true
                                } else false
                            } else true
                        }
                        KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_INFO -> {
                            if (showQuickDrawer) {
                                showQuickDrawer = false
                            }
                            showControls = !showControls
                            true
                        }
                        KeyEvent.KEYCODE_BOOKMARK,
                        KeyEvent.KEYCODE_STAR,
                        KeyEvent.KEYCODE_CAPTIONS,
                        KeyEvent.KEYCODE_PROG_YELLOW -> {
                            onToggleFavorite(channel)
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_UP -> {
                            switchRelativeChannel(-1)
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            switchRelativeChannel(1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!showControls) {
                                if (channel.contentType != StreamContentType.LIVE_TV) {
                                    lastControlFocusRequester = centerPlayFocusRequester
                                    showControls = true
                                } else {
                                    switchRelativeChannel(-1)
                                }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!showControls) {
                                if (channel.contentType != StreamContentType.LIVE_TV) {
                                    lastControlFocusRequester = centerPlayFocusRequester
                                    showControls = true
                                } else {
                                    switchRelativeChannel(1)
                                }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                            if (channel.contentType != StreamContentType.LIVE_TV && (!showControls || code != KeyEvent.KEYCODE_DPAD_LEFT)) {
                                showControls = true
                                seekVodByOffset(-computeSmartSeekStepMs(rep))
                                true
                            } else if (channel.contentType == StreamContentType.LIVE_TV && !showControls && !showQuickDrawer) {
                                drawerSelectedBouquet = channel.group
                                drawerOpenTimestampMs = android.os.SystemClock.elapsedRealtime()
                                showQuickDrawer = true
                                focusFirstChannelTrigger += 1
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                            if (channel.contentType != StreamContentType.LIVE_TV && (!showControls || code != KeyEvent.KEYCODE_DPAD_RIGHT)) {
                                showControls = true
                                seekVodByOffset(computeSmartSeekStepMs(rep))
                                true
                            } else if (channel.contentType == StreamContentType.LIVE_TV && !showControls && !showQuickDrawer) {
                                drawerSelectedBouquet = channel.group
                                drawerOpenTimestampMs = android.os.SystemClock.elapsedRealtime()
                                showQuickDrawer = true
                                focusFirstChannelTrigger += 1
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_SPACE -> {
                            SafeClickGuard.runThrottled("CENTER_PLAY_PAUSE_BTN", 220L) {
                                if (player.isPlaying) {
                                    player.pause()
                                    if (channel.contentType != StreamContentType.LIVE_TV) {
                                        val pos = player.currentPosition.coerceAtLeast(0L)
                                        if (pos >= 2_000L) {
                                            vodPositionMs = maxOf(vodPositionMs, pos)
                                            onSaveVodProgress(channel, vodPositionMs, vodDurationMs)
                                        }
                                    }
                                } else {
                                    player.play()
                                }
                                showControls = true
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            player.pause()
                            if (channel.contentType != StreamContentType.LIVE_TV) {
                                val pos = player.currentPosition.coerceAtLeast(0L)
                                if (pos >= 2_000L) {
                                    vodPositionMs = maxOf(vodPositionMs, pos)
                                    onSaveVodProgress(channel, vodPositionMs, vodDurationMs)
                                }
                            }
                            showControls = true
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            player.play()
                            showControls = true
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_STOP -> {
                            if (channel.contentType != StreamContentType.LIVE_TV) {
                                val pos = maxOf(player.currentPosition.coerceAtLeast(0L), vodPositionMs)
                                if (pos >= 2_000L) {
                                    onSaveVodProgress(channel, pos, vodDurationMs)
                                }
                                player.pause()
                            }
                            onBack()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                enabled = !showControls && !showQuickDrawer && !showTracksDialog,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (showQuickDrawer) {
                    showQuickDrawer = false
                } else if (channel.contentType == StreamContentType.LIVE_TV && !showControls) {
                    drawerSelectedBouquet = channel.group
                    drawerOpenTimestampMs = android.os.SystemClock.elapsedRealtime()
                    showQuickDrawer = true
                    focusFirstChannelTrigger += 1
                } else {
                    showControls = !showControls
                }
            }
    ) {
        val isPhoneLandscape = maxHeight < 460.dp

        val isInPipMode by remember { derivedStateOf { SharedLiveSessionBridge.isInPipModeState } }
        val videoContainerModifier by remember(isInPipMode) {
            derivedStateOf {
                if (isInPipMode) Modifier.fillMaxSize() else Modifier.fillMaxSize()
            }
        }
        LaunchedEffect(isInPipMode) {
            if (isInPipMode) {
                showControls = false
                showQuickDrawer = false
                showTracksDialog = false
                attachedPlayerView?.useController = false
            }
        }
        val rememberedPlayerView = remember(player) {
            (android.view.LayoutInflater.from(context).inflate(R.layout.exo_texture_player_view, null, false) as PlayerView).apply {
                this.layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.Gravity.CENTER
                )
                this.useController = false
                this.resizeMode = resizeMode.exoMode
                this.setKeepContentOnPlayerReset(true)
                this.setShutterBackgroundColor(android.graphics.Color.BLACK)
                this.keepScreenOn = true
                this.isFocusable = false
                this.isFocusableInTouchMode = false
                this.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                this.videoSurfaceView?.visibility = android.view.View.VISIBLE
                this.subtitleView?.apply {
                    setApplyEmbeddedStyles(true)
                    setApplyEmbeddedFontSizes(false)
                    setBottomPaddingFraction(0.08f)
                    setUserDefaultStyle()
                    setUserDefaultTextSize()
                    visibility = android.view.View.VISIBLE
                }
            }
        }
        DisposableEffect(rememberedPlayerView) {
            attachedPlayerView = rememberedPlayerView
            SharedLiveSessionBridge.attachViewSafely(player, rememberedPlayerView)
            onDispose {
                SharedLiveSessionBridge.detachViewIfBound(player, rememberedPlayerView)
            }
        }
        Box(modifier = videoContainerModifier) {
            AndroidView(
                factory = {
                    (rememberedPlayerView.parent as? android.view.ViewGroup)?.removeView(rememberedPlayerView)
                    rememberedPlayerView.useController = false
                    rememberedPlayerView
                },
                update = { view ->
                    attachedPlayerView = view
                    view.useController = false
                    if (view.player !== player && !SharedLiveSessionBridge.isSurfaceSwitching.get()) {
                        SharedLiveSessionBridge.attachViewSafely(player, view)
                    }
                    if (view.resizeMode != resizeMode.exoMode) {
                        view.resizeMode = resizeMode.exoMode
                    }
                    view.videoSurfaceView?.visibility = android.view.View.VISIBLE
                },
                modifier = Modifier.fillMaxSize()
            )
            // Native PlayerView shutter handles initial black frame cleanly without occluding 4K SurfaceView overlay!
        }

        if (numberInputBuffer.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .background(Color(0xEB101015), RoundedCornerShape(12.dp))
                    .border(2.dp, BeinHighlightPurple, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = numberInputBuffer,
                    color = Color.White,
                    fontSize = if (isPhoneLandscape) 20.sp else 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        if (preferences.showNetworkSpeed && (showControls || showBufferingBadge)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = if (showControls) 66.dp else 14.dp)
                    .background(Color(0xE6101015), RoundedCornerShape(10.dp))
                    .border(1.dp, EmeraldLive.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                val lockedQ = if (isMultiQualityChannel) MultiQualityEngine.getManualQuality(channel.stableKey) else null
                val activeQText = activeMqVariant?.let {
                    val modeTag = if (lockedQ == null) "Auto(${it.quality.labelAr})" else it.quality.labelAr
                    val healthPct = MultiQualityEngine.getVariantHealthScore(it)
                    " • 📺 $modeTag • صحة المصدر $healthPct%"
                }.orEmpty()
                Text(
                    text = "🌐 ⬇ $networkSpeedLabel$activeQText",
                    color = EmeraldLive,
                    fontSize = if (isPhoneLandscape) 10.sp else 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Silent Multi-Quality & Engine Switching: no popup banners over the video stream to avoid disturbing the viewer

        if (!fatalHttpErrorBanner.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF2120810))
                    .border(2.dp, CrimsonAlert, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = fatalHttpErrorBanner.orEmpty(),
                        color = Color.White,
                        fontSize = if (isPhoneLandscape) 13.sp else 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FocusableActionPill(
                            text = "🔄 إعادة المحاولة يدوياً",
                            isPrimary = true,
                            compact = isPhoneLandscape,
                            onClick = {
                                fatalHttpErrorBanner = null
                                errorMessage = null
                                retryCount += 1
                            }
                        )
                        FocusableActionPill(
                            text = "رجوع",
                            isPrimary = false,
                            compact = isPhoneLandscape,
                            onClick = onBack
                        )
                    }
                }
            }
        } else if (isSourceStopped) {
            // Calm, steady zero-flicker hold screen while silent background probe monitors the channel
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xEB0A0E17))
                    .border(1.4.dp, Color(0xFFAD8B43).copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "📡 القناة متوقفة مؤقتاً من المصدر",
                    color = Color.White,
                    fontSize = if (isPhoneLandscape) 14.sp else 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "جارٍ الفحص الصامت في الخلفية • ستعمل القناة تلقائياً فور عودة البث",
                    color = Color(0xFFCBD5E1),
                    fontSize = if (isPhoneLandscape) 11.sp else 13.sp,
                    textAlign = TextAlign.Center
                )
                FocusableActionPill(
                    text = "🔄 فحص وإعادة المحاولة الآن",
                    isPrimary = true,
                    compact = isPhoneLandscape,
                    onClick = {
                        isSourceStopped = false
                        errorMessage = null
                        sameUrlRetryAttempts = 0
                        candidateIndex = 0
                        retryCount = 0
                        retryCount = 1
                    }
                )
            }
        } else if (isAutoReconnecting || showBufferingBadge) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xD90A0E17), RoundedCornerShape(99.dp))
                    .border(1.dp, Color(0xFFAD8B43).copy(alpha = 0.5f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFAD8B43),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(if (isPhoneLandscape) 20.dp else 24.dp)
                )
                Text(
                    text = "⏳ جارٍ الاتصال بالسيرفر...",
                    color = Color.White,
                    fontSize = if (isPhoneLandscape) 12.sp else 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // [5] Channel Zapping OSD (On-Screen Display):
        // Sleek bottom-center box: `[ Channel Number ] Channel Name` when channel changes via Channel UP/DOWN
        AnimatedVisibility(
            visible = showZappingOsd && !showControls && !showQuickDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isPhoneLandscape) 22.dp else 36.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xF0050B14), Color(0xF00F172A))
                        )
                    )
                    .border(1.5.dp, CyanPrimary.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyanPrimary.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary)
                ) {
                    Text(
                        text = "[ ${channel.number.coerceAtLeast(1)} ]",
                        color = CyanPrimary,
                        fontSize = if (isPhoneLandscape) 14.sp else 17.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = if (isPhoneLandscape) 15.sp else 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC030106),
                                Color.Transparent,
                                Color.Transparent,
                                Color(0xDD030106)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(
                            horizontal = if (isPhoneLandscape) 12.dp else 20.dp,
                            vertical = if (isPhoneLandscape) 6.dp else 14.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val topBarDownDest = if (channel.contentType != StreamContentType.LIVE_TV) {
                            centerPlayFocusRequester
                        } else if (siblingChannels.size > 1) {
                            bottomPrevChFocusRequester
                        } else {
                            bottomFirstPillFocusRequester
                        }
                        FocusableCircleButton(
                            onClick = onBack,
                            icon = Icons.Default.ArrowBack,
                            contentDescription = "رجوع",
                            sizeDp = if (isPhoneLandscape) 36.dp else 44.dp,
                            modifier = Modifier.focusRequester(topBackBtnFocusRequester),
                            downTarget = topBarDownDest,
                            leftTarget = topFavBtnFocusRequester
                        )
                        Spacer(Modifier.width(8.dp))
                        ChannelAvatar(
                            name = channel.name,
                            logoUrl = channel.logo,
                            modifier = Modifier.size(if (isPhoneLandscape) 42.dp else 54.dp),
                            cornerRadius = 11.dp,
                            innerPadding = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channel.name,
                                color = Color.White,
                                fontSize = if (isPhoneLandscape) 15.sp else 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val selectedTier = remember(activeStreamChannel.name, activeStreamChannel.group) {
                                    SportsQualitySelector.detectQualityTier(activeStreamChannel.name, activeStreamChannel.group)
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = selectedTier.badgeColor.copy(alpha = 0.92f)
                                ) {
                                    Text(
                                        text = selectedTier.playerLabel,
                                        color = if (selectedTier == ProQualityTier.FHD_1080) Color.White else TextOnAccent,
                                        fontSize = if (isPhoneLandscape) 10.sp else 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = channel.group,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = if (isPhoneLandscape) 11.sp else 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (videoResolutionLabel.isNotBlank() || detectedSpecLabel.isNotBlank()) {
                                    Text(
                                        text = listOfNotNull(
                                            videoResolutionLabel.takeIf { it.isNotBlank() },
                                            detectedSpecLabel.takeIf { it.isNotBlank() }
                                        ).joinToString(" • "),
                                        color = EmeraldLive,
                                        fontSize = if (isPhoneLandscape) 10.sp else 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    val hasPipBtn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null
                    val topRightDownDest = if (channel.contentType != StreamContentType.LIVE_TV) {
                        centerPlayFocusRequester
                    } else if (siblingChannels.size > 1) {
                        bottomPrevChFocusRequester
                    } else {
                        bottomFirstPillFocusRequester
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusableCircleButton(
                            onClick = { onToggleFavorite(channel) },
                            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White,
                            contentDescription = "المفضلة",
                            sizeDp = if (isPhoneLandscape) 36.dp else 44.dp,
                            modifier = Modifier.focusRequester(topFavBtnFocusRequester),
                            downTarget = topRightDownDest,
                            rightTarget = topBackBtnFocusRequester,
                            leftTarget = if (hasPipBtn) topPipBtnFocusRequester else topListBtnFocusRequester
                        )

                        if (hasPipBtn && activity != null) {
                            FocusableCircleButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        runCatching {
                                            val builder = PictureInPictureParams.Builder()
                                                .setAspectRatio(Rational(16, 9))
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                builder.setAutoEnterEnabled(true)
                                            }
                                            activity.setPictureInPictureParams(builder.build())
                                            activity.enterPictureInPictureMode(builder.build())
                                        }
                                    }
                                },
                                icon = Icons.Default.ExitToApp,
                                contentDescription = "نافذة عائمة فوق التطبيقات",
                                sizeDp = if (isPhoneLandscape) 36.dp else 44.dp,
                                modifier = Modifier.focusRequester(topPipBtnFocusRequester),
                                downTarget = topRightDownDest,
                                rightTarget = topFavBtnFocusRequester,
                                leftTarget = topListBtnFocusRequester
                            )
                        }

                        FocusableCircleButton(
                            onClick = {
                                showControls = false
                                drawerSelectedBouquet = channel.group
                                drawerOpenTimestampMs = android.os.SystemClock.elapsedRealtime()
                                showQuickDrawer = true
                                focusFirstChannelTrigger += 1
                            },
                            icon = Icons.Default.List,
                            contentDescription = if (channel.contentType == StreamContentType.SERIES) "الأجزاء والحلقات" else "الباقات والقنوات",
                            sizeDp = if (isPhoneLandscape) 36.dp else 44.dp,
                            modifier = Modifier.focusRequester(topListBtnFocusRequester),
                            downTarget = topRightDownDest,
                            rightTarget = if (hasPipBtn) topPipBtnFocusRequester else topFavBtnFocusRequester
                        )
                    }
                }

                val centerDownTarget = if (channel.contentType != StreamContentType.LIVE_TV) {
                    vodSeekBarFocusRequester
                } else if (siblingChannels.size > 1) {
                    bottomPrevChFocusRequester
                } else {
                    bottomFirstPillFocusRequester
                }
                // ── شريط أزرار المنتصف (Netflix) — للأفلام والمسلسلات فقط؛ مخفي في البث المباشر ──
                if (channel.contentType != StreamContentType.LIVE_TV) Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (isPhoneLandscape) 14.dp else 26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. زر السابق (القناة السابقة / الحلقة السابقة)
                    FocusableCircleButton(
                        onClick = {
                            userNavInteractionTick += 1L
                            switchRelativeChannel(-1)
                        },
                        icon = Icons.Default.SkipPrevious,
                        tint = Color.White,
                        contentDescription = "السابق",
                        sizeDp = if (isPhoneLandscape) 40.dp else 50.dp,
                        modifier = Modifier
                            .focusRequester(centerPrevFocusRequester)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    userNavInteractionTick += 1L
                                    lastControlFocusRequester = centerPrevFocusRequester
                                }
                            },
                        upTarget = topBackBtnFocusRequester,
                        downTarget = centerDownTarget,
                        leftTarget = centerRewindFocusRequester
                    )

                    // 2. زر التأخير الدائري بنمط نتفلكس (سهم دائري عكسي مع رقم 10 في الوسط)
                    NetflixCircularSeekButton(
                        secondsLabel = "10",
                        isForward = false,
                        subtitleLabel = if (channel.contentType != StreamContentType.LIVE_TV) "-10 ثوانٍ" else "السابقة",
                        sizeDp = if (isPhoneLandscape) 48.dp else 60.dp,
                        compact = isPhoneLandscape,
                        modifier = Modifier
                            .focusRequester(centerRewindFocusRequester)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    userNavInteractionTick += 1L
                                    lastControlFocusRequester = centerRewindFocusRequester
                                }
                            },
                        upTarget = topBackBtnFocusRequester,
                        downTarget = centerDownTarget,
                        rightTarget = centerPrevFocusRequester,
                        leftTarget = centerPlayFocusRequester,
                        onClick = {
                            userNavInteractionTick += 1L
                            if (channel.contentType != StreamContentType.LIVE_TV) {
                                seekVodByOffset(-10_000L)
                            } else {
                                switchRelativeChannel(-1)
                            }
                        }
                    )

                    // 3. زر الإيقاف المؤقت / الاستئناف المركزي بنمط نتفلكس الاحترافي
                    val togglePlayPauseAction = {
                        userNavInteractionTick += 1L
                        SafeClickGuard.runThrottled("CENTER_PLAY_PAUSE_BTN", 220L) {
                            if (player.isPlaying) {
                                player.pause()
                                if (channel.contentType != StreamContentType.LIVE_TV) {
                                    val pos = maxOf(player.currentPosition.coerceAtLeast(0L), vodPositionMs)
                                    vodPositionMs = pos
                                    onSaveVodProgress(channel, pos, vodDurationMs)
                                }
                            } else {
                                if (player.playbackState == Player.STATE_IDLE) {
                                    player.prepare()
                                }
                                player.play()
                            }
                        }
                    }
                    var centerPlayFocused by remember { mutableStateOf(false) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        IconButton(
                            onClick = togglePlayPauseAction,
                            modifier = Modifier
                                .focusRequester(centerPlayFocusRequester)
                                .size(if (isPhoneLandscape) 62.dp else 78.dp)
                                .scale(if (centerPlayFocused) 1.16f else 1.0f)
                                .onFocusChanged {
                                    centerPlayFocused = it.isFocused
                                    if (it.isFocused) {
                                        userNavInteractionTick += 1L
                                        lastControlFocusRequester = centerPlayFocusRequester
                                    }
                                }
                                .onKeyEvent { ev ->
                                    val code = ev.nativeKeyEvent.keyCode
                                    val isSelectKey = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                                        code == KeyEvent.KEYCODE_ENTER ||
                                        code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                                        code == KeyEvent.KEYCODE_SPACE ||
                                        code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                                    if (isSelectKey) {
                                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                            togglePlayPauseAction()
                                        }
                                        true
                                    } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                        userNavInteractionTick += 1L
                                        when (code) {
                                            KeyEvent.KEYCODE_DPAD_UP -> {
                                                runCatching { topBackBtnFocusRequester.requestFocus() }
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                runCatching { centerDownTarget.requestFocus() }
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                runCatching { centerRewindFocusRequester.requestFocus() }
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                runCatching { centerFfwdFocusRequester.requestFocus() }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .focusable()
                                .background(
                                    Color.White,
                                    CircleShape
                                )
                                .border(
                                    width = if (centerPlayFocused) 3.5.dp else 2.dp,
                                    color = if (centerPlayFocused) CyanPrimary else Color.White,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف مؤقت" else "استئناف",
                                tint = DeepNavy,
                                modifier = Modifier.size(if (isPhoneLandscape) 34.dp else 44.dp)
                            )
                        }
                        Text(
                            text = if (isPlaying) "إيقاف مؤقت" else "استئناف",
                            color = if (centerPlayFocused) BeinFocusGlow else Color.White.copy(alpha = 0.92f),
                            fontSize = if (isPhoneLandscape) 10.sp else 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // 4. زر التقديم الدائري بنمط نتفلكس (سهم دائري للأمام مع رقم 10 في الوسط)
                    NetflixCircularSeekButton(
                        secondsLabel = "10",
                        isForward = true,
                        subtitleLabel = if (channel.contentType != StreamContentType.LIVE_TV) "+10 ثوانٍ" else "التالية",
                        sizeDp = if (isPhoneLandscape) 48.dp else 60.dp,
                        compact = isPhoneLandscape,
                        modifier = Modifier
                            .focusRequester(centerFfwdFocusRequester)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    userNavInteractionTick += 1L
                                    lastControlFocusRequester = centerFfwdFocusRequester
                                }
                            },
                        upTarget = topFavBtnFocusRequester,
                        downTarget = centerDownTarget,
                        rightTarget = centerPlayFocusRequester,
                        leftTarget = centerNextFocusRequester,
                        onClick = {
                            userNavInteractionTick += 1L
                            if (channel.contentType != StreamContentType.LIVE_TV) {
                                seekVodByOffset(10_000L)
                            } else {
                                switchRelativeChannel(1)
                            }
                        }
                    )

                    // 5. زر التالي (القناة التالية / الحلقة التالية)
                    FocusableCircleButton(
                        onClick = {
                            userNavInteractionTick += 1L
                            switchRelativeChannel(1)
                        },
                        icon = Icons.Default.SkipNext,
                        tint = Color.White,
                        contentDescription = "التالي",
                        sizeDp = if (isPhoneLandscape) 40.dp else 50.dp,
                        modifier = Modifier
                            .focusRequester(centerNextFocusRequester)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    userNavInteractionTick += 1L
                                    lastControlFocusRequester = centerNextFocusRequester
                                }
                            },
                        upTarget = topFavBtnFocusRequester,
                        downTarget = centerDownTarget,
                        rightTarget = centerFfwdFocusRequester
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC08080A), Color(0xF208080A))
                            )
                        )
                        .padding(
                            horizontal = if (isPhoneLandscape) 12.dp else 18.dp,
                            vertical = if (isPhoneLandscape) 6.dp else 10.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bottom Control Bar: Slim Interactive Progress Bar for Movies/Series + Quick Episode Switcher OR Live TV Quick Navigation Bar
                    if (channel.contentType != StreamContentType.LIVE_TV) {
                        val progressFrac = if (vodDurationMs > 0L) {
                            (vodPositionMs.toFloat() / vodDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val remainingMs = (vodDurationMs - vodPositionMs).coerceAtLeast(0L)
                        var seekBarFocused by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(vodSeekBarFocusRequester)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xE6101016))
                                .border(
                                    width = if (seekBarFocused) 2.5.dp else 1.dp,
                                    color = if (seekBarFocused) BeinFocusGlow else BeinHighlightPurple.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .onFocusChanged {
                                    seekBarFocused = it.isFocused
                                    if (it.isFocused) lastControlFocusRequester = vodSeekBarFocusRequester
                                }
                                .onKeyEvent { ev ->
                                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                        val stepMs = computeSmartSeekStepMs(ev.nativeKeyEvent.repeatCount)
                                        when (ev.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_LEFT,
                                            KeyEvent.KEYCODE_MEDIA_REWIND,
                                            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                                                seekVodByOffset(-stepMs)
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_RIGHT,
                                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                                            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                                                seekVodByOffset(stepMs)
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_CENTER,
                                            KeyEvent.KEYCODE_ENTER -> {
                                                if (player.isPlaying) player.pause() else player.play()
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_UP -> {
                                                runCatching { centerPlayFocusRequester.requestFocus() }
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                val downDest = if (channel.contentType == StreamContentType.SERIES && siblingChannels.size > 1) {
                                                    bottomPrevChFocusRequester
                                                } else {
                                                    bottomFirstPillFocusRequester
                                                }
                                                runCatching { downDest.requestFocus() }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .focusable()
                                .padding(horizontal = 12.dp, vertical = if (isPhoneLandscape) 4.dp else 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⏱ ${VodWatchProgress.formatDurationMs(vodPositionMs)} / ${VodWatchProgress.formatDurationMs(vodDurationMs)} (${(progressFrac * 100).toInt()}%)",
                                    color = if (seekBarFocused) EmeraldLive else Color.White,
                                    fontSize = if (isPhoneLandscape) 11.sp else 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (vodDurationMs > 0L) {
                                        "المتبقي: -${VodWatchProgress.formatDurationMs(remainingMs)} • ◀/▶ للتقديم والتأخير"
                                    } else {
                                        "◀/▶ للتقديم والتأخير السريع"
                                    },
                                    color = Color(0xFFCBD5E1),
                                    fontSize = if (isPhoneLandscape) 10.sp else 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp)
                                    .pointerInput(vodDurationMs) {
                                        detectTapGestures { offset ->
                                            if (vodDurationMs > 0L && size.width > 0) {
                                                val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                seekVodTo((frac * vodDurationMs).toLong())
                                            }
                                        }
                                    }
                                    .pointerInput(vodDurationMs) {
                                        detectHorizontalDragGestures { change, _ ->
                                            change.consume()
                                            if (vodDurationMs > 0L && size.width > 0) {
                                                val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                seekVodTo((frac * vodDurationMs).toLong())
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val thumbSize = if (seekBarFocused) 13.dp else 10.dp
                                val usableWidth = (maxWidth - thumbSize).coerceAtLeast(0.dp)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (seekBarFocused) 6.dp else 4.dp)
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(Color(0xFF2C2C38))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progressFrac)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(Color(0xFFE50914), EmeraldLive)
                                                )
                                            )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(x = usableWidth * progressFrac)
                                        .size(thumbSize)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(2.dp, if (seekBarFocused) EmeraldLive else Color(0xFFE50914), CircleShape)
                                )
                            }
                        }

                        // Quick Next/Previous Episode Switcher Row for Series (3.3 إمكانية الانتقال إلى الحلقة التالية/السابقة مباشرة من المشغل)
                        if (channel.contentType == StreamContentType.SERIES && siblingChannels.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xE6121216))
                                    .border(1.dp, BeinBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📺 ${channel.group} • ${channel.name}",
                                    color = EmeraldLive,
                                    fontSize = if (isPhoneLandscape) 11.5.sp else 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FocusableActionPill(
                                        text = "⏮ الحلقة السابقة",
                                        isPrimary = false,
                                        compact = isPhoneLandscape,
                                        modifier = Modifier
                                            .focusRequester(bottomPrevChFocusRequester)
                                            .onFocusChanged { if (it.isFocused) lastControlFocusRequester = bottomPrevChFocusRequester },
                                        upTarget = vodSeekBarFocusRequester,
                                        downTarget = bottomFirstPillFocusRequester,
                                        leftTarget = bottomNextChFocusRequester,
                                        onClick = { switchRelativeChannel(-1) }
                                    )
                                    FocusableActionPill(
                                        text = "⏭ الحلقة التالية",
                                        isPrimary = true,
                                        compact = isPhoneLandscape,
                                        modifier = Modifier
                                            .focusRequester(bottomNextChFocusRequester)
                                            .onFocusChanged { if (it.isFocused) lastControlFocusRequester = bottomNextChFocusRequester },
                                        upTarget = vodSeekBarFocusRequester,
                                        downTarget = bottomFirstPillFocusRequester,
                                        rightTarget = bottomPrevChFocusRequester,
                                        onClick = { switchRelativeChannel(1) }
                                    )
                                }
                            }
                        }
                    } else {
                        // Live TV Bottom Info & Quick Channel Switch Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xE6121216))
                                .border(1.2.dp, BeinBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE50914), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "● LIVE",
                                        color = Color.White,
                                        fontSize = if (isPhoneLandscape) 11.sp else 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Text(
                                    text = "${channel.name} • ${channel.group}",
                                    color = Color.White,
                                    fontSize = if (isPhoneLandscape) 13.sp else 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (siblingChannels.size > 1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FocusableActionPill(
                                        text = "⏮ السابقة",
                                        isPrimary = false,
                                        compact = isPhoneLandscape,
                                        modifier = Modifier
                                            .focusRequester(bottomPrevChFocusRequester)
                                            .onFocusChanged { if (it.isFocused) lastControlFocusRequester = bottomPrevChFocusRequester },
                                        upTarget = centerPlayFocusRequester,
                                        downTarget = bottomFirstPillFocusRequester,
                                        leftTarget = bottomNextChFocusRequester,
                                        onClick = { switchRelativeChannel(-1) }
                                    )
                                    FocusableActionPill(
                                        text = "⏭ التالية",
                                        isPrimary = false,
                                        compact = isPhoneLandscape,
                                        modifier = Modifier
                                            .focusRequester(bottomNextChFocusRequester)
                                            .onFocusChanged { if (it.isFocused) lastControlFocusRequester = bottomNextChFocusRequester },
                                        upTarget = centerPlayFocusRequester,
                                        downTarget = bottomFirstPillFocusRequester,
                                        rightTarget = bottomPrevChFocusRequester,
                                        onClick = { switchRelativeChannel(1) }
                                    )
                                }
                            }
                        }
                    }

                    // Complete Bottom Control Toolbar for Live TV, Movies, and Series:
                    // Every button (الأجزاء والحلقات/القنوات، الجودة، الصوت، الترجمة، ملء الشاشة، الإعدادات، المفضلة) is focusable & chained step-by-step!
                    run {
                        val pillUpTarget = when {
                            channel.contentType == StreamContentType.SERIES && siblingChannels.size > 1 -> bottomPrevChFocusRequester
                            channel.contentType != StreamContentType.LIVE_TV -> vodSeekBarFocusRequester
                            siblingChannels.size > 1 -> bottomPrevChFocusRequester
                            else -> centerPlayFocusRequester
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. قائمة الأجزاء والحلقات (للمسلسلات) / القنوات والباقات (للبث المباشر) / أفلام القسم (للأفلام)
                            FocusableActionPill(
                                text = when (channel.contentType) {
                                    StreamContentType.SERIES -> "📺 ${AppStrings.tr(lang, "الأجزاء والحلقات", "Saisons & Épisodes", "Seasons & Episodes")}"
                                    StreamContentType.MOVIE -> "🎬 ${AppStrings.tr(lang, "قائمة الأفلام", "Liste des Films", "Movies List")}"
                                    StreamContentType.LIVE_TV -> "📂 ${AppStrings.tr(lang, "القنوات والباقات", "Chaînes & Bouquets", "Channels & Bouquets")}"
                                },
                                isPrimary = true,
                                compact = isPhoneLandscape,
                                modifier = Modifier
                                    .focusRequester(bottomFirstPillFocusRequester)
                                    .onFocusChanged { if (it.isFocused) lastControlFocusRequester = bottomFirstPillFocusRequester },
                                upTarget = pillUpTarget,
                                leftTarget = bottomQualityPillFocusRequester,
                                onClick = {
                                    showControls = false
                                    drawerSelectedBouquet = channel.group
                                    drawerOpenTimestampMs = android.os.SystemClock.elapsedRealtime()
                                    showQuickDrawer = true
                                    focusFirstChannelTrigger += 1
                                }
                            )

                            // 2. زر الجودة (Quality)
                            FocusableActionPill(
                                text = "🎬 ${AppStrings.tr(lang, "الجودة", "Qualité", "Quality")}",
                                isPrimary = false,
                                compact = isPhoneLandscape,
                                modifier = Modifier
                                    .focusRequester(bottomQualityPillFocusRequester)
                                    .onFocusChanged { if (it.isFocused) lastControlFocusRequester = bottomQualityPillFocusRequester },
                                upTarget = pillUpTarget,
                                rightTarget = bottomFirstPillFocusRequester,
                                leftTarget = bottomAudioPillFocusRequester,
                                onClick = {
                                    lastControlFocusRequester = bottomQualityPillFocusRequester
                                    tracksModalInitialTab = 0
                                    showTracksDialog = true
                                }
                            )

                            // 3. زر الصوت (Audio)
                            FocusableActionPill(
                                text = "🔊 ${AppStrings.tr(lang, "الصوت", "Audio", "Audio")}",
                                isPrimary = false,
                                compact = isPhoneLandscape,
                                modifier = Modifier
                                    .focusRequester(bottomAudioPillFocusRequester)
                                    .onFocusChanged { if (it.isFocused) lastControlFocusRequester = bottomAudioPillFocusRequester },
                                upTarget = pillUpTarget,
                                rightTarget = bottomQualityPillFocusRequester,
                                leftTarget = bottomSubtitlesPillFocusRequester,
                                onClick = {
                                    lastControlFocusRequester = bottomAudioPillFocusRequester
                                    tracksModalInitialTab = 1
                                    showTracksDialog = true
                                }
                            )

                            // 4. زر الترجمة (Subtitles)
                            FocusableActionPill(
                                text = "💬 ${AppStrings.tr(lang, "الترجمة", "Sous-titres", "Subtitles")}",
                                isPrimary = false,
                                compact = isPhoneLandscape,
                                modifier = Modifier
                                    .focusRequester(bottomSubtitlesPillFocusRequester)
                                    .onFocusChanged { if (it.isFocused) lastControlFocusRequester = bottomSubtitlesPillFocusRequester },
                                upTarget = pillUpTarget,
                                rightTarget = bottomAudioPillFocusRequester,
                                leftTarget = bottomResizePillFocusRequester,
                                onClick = {
                                    lastControlFocusRequester = bottomSubtitlesPillFocusRequester
                                    tracksModalInitialTab = 2
                                    showTracksDialog = true
                                }
                            )

                            // 5. زر ملء الشاشة والأبعاد (Fullscreen / Aspect Ratio)
                            FocusableActionPill(
                                text = "📐 ${resizeMode.titleAr}",
                                isPrimary = false,
                                compact = isPhoneLandscape,
                                modifier = Modifier
                                    .focusRequester(bottomResizePillFocusRequester)
                                    .onFocusChanged { if (it.isFocused) lastControlFocusRequester = bottomResizePillFocusRequester },
                                upTarget = pillUpTarget,
                                rightTarget = bottomSubtitlesPillFocusRequester,
                                leftTarget = bottomSettingsPillFocusRequester,
                                onClick = {
                                    val modes = VideoResizeMode.values()
                                    resizeMode = modes[(resizeMode.ordinal + 1) % modes.size]
                                }
                            )

                            // 6. زر الإعدادات والسرعة (Settings & Speed)
                            FocusableActionPill(
                                text = "⚙️ ${AppStrings.tr(lang, "الإعدادات (${playbackSpeed.labelAr})", "Paramètres (${playbackSpeed.labelAr})", "Settings (${playbackSpeed.labelAr})")}",
                                isPrimary = false,
                                compact = isPhoneLandscape,
                                modifier = Modifier
                                    .focusRequester(bottomSettingsPillFocusRequester)
                                    .onFocusChanged { if (it.isFocused) lastControlFocusRequester = bottomSettingsPillFocusRequester },
                                upTarget = pillUpTarget,
                                rightTarget = bottomResizePillFocusRequester,
                                leftTarget = bottomFavPillFocusRequester,
                                onClick = {
                                    lastControlFocusRequester = bottomSettingsPillFocusRequester
                                    tracksModalInitialTab = 3
                                    showTracksDialog = true
                                }
                            )

                            // 7. زر المفضلة (Favorite)
                            FocusableActionPill(
                                text = if (isFavorite) "❤️ ${AppStrings.tr(lang, "في المفضلة", "Favori", "Favorited")}" else "🤍 ${AppStrings.tr(lang, "إضافة للمفضلة", "Ajouter Favori", "Add Favorite")}",
                                isPrimary = isFavorite,
                                compact = isPhoneLandscape,
                                modifier = Modifier
                                    .focusRequester(bottomFavPillFocusRequester)
                                    .onFocusChanged { if (it.isFocused) lastControlFocusRequester = bottomFavPillFocusRequester },
                                upTarget = pillUpTarget,
                                rightTarget = bottomSettingsPillFocusRequester,
                                onClick = { onToggleFavorite(channel) }
                            )

                            // 8. مؤقت النوم (Sleep Timer: 30 / 60 / 90 دقيقة)
                            FocusableActionPill(
                                text = if (sleepTimerRemainingMin > 0) "🌙 مؤقت النوم ($sleepTimerRemainingMin د)" else "🌙 مؤقت النوم",
                                isPrimary = sleepTimerRemainingMin > 0,
                                compact = isPhoneLandscape,
                                upTarget = pillUpTarget,
                                onClick = {
                                    sleepTimerRemainingMin = when (sleepTimerRemainingMin) {
                                        0 -> 30
                                        30 -> 60
                                        60 -> 90
                                        else -> 0
                                    }
                                    ProSubtitleAndPlayerSettings.sleepTimerMinutes = sleepTimerRemainingMin
                                }
                            )

                            // 9. تحميل SRT من OpenSubtitles وتخصيص حجم الترجمة
                            FocusableActionPill(
                                text = "🌐 OpenSubtitles SRT (${ProSubtitleAndPlayerSettings.subtitleSizePreset.labelAr})",
                                isPrimary = false,
                                compact = isPhoneLandscape,
                                upTarget = pillUpTarget,
                                onClick = {
                                    val presets = SubtitleSizePreset.values()
                                    val nextPreset = presets[(ProSubtitleAndPlayerSettings.subtitleSizePreset.ordinal + 1) % presets.size]
                                    ProSubtitleAndPlayerSettings.subtitleSizePreset = nextPreset
                                    attachedPlayerView?.subtitleView?.setFractionalTextSize(0.053f * nextPreset.scaleFactor)
                                    openSubtitlesStatusToast = "✅ تم تحديث حجم الترجمة إلى (${nextPreset.labelAr}) وتفعيل مطابقة OpenSubtitles SRT"
                                }
                            )

                            // 10. قفل اللمس والشاشة أثناء العرض (Screen Lock)
                            FocusableActionPill(
                                text = if (isPlayerScreenLocked) "🔓 إلغاء القفل" else "🔒 قفل الشاشة",
                                isPrimary = isPlayerScreenLocked,
                                compact = isPhoneLandscape,
                                upTarget = pillUpTarget,
                                onClick = {
                                    isPlayerScreenLocked = !isPlayerScreenLocked
                                    if (isPlayerScreenLocked) showControls = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showQuickDrawer) {
            val isDrawerRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
            val isSeriesDrawer = channel.contentType == StreamContentType.SERIES
            val activeBouquetIdx = remember(allBouquets, drawerSelectedBouquet) {
                allBouquets.indexOfFirst { it.first == drawerSelectedBouquet }.coerceAtLeast(0)
            }
            val rawActiveChannelIdx = remember(drawerChannels, channel.stableKey, channel.url) {
                drawerChannels.indexOfFirst {
                    it.stableKey == channel.stableKey ||
                        (isSeriesDrawer && it.url.trim() == channel.url.trim())
                }
            }
            val targetChannelIdx = remember(rawActiveChannelIdx) {
                if (rawActiveChannelIdx >= 0) rawActiveChannelIdx else 0
            }

            val bouquetListState = rememberLazyListState(
                initialFirstVisibleItemIndex = (activeBouquetIdx - 2).coerceAtLeast(0)
            )
            val channelListState = rememberLazyListState(
                initialFirstVisibleItemIndex = (targetChannelIdx - 2).coerceAtLeast(0)
            )

            var drawerVisibleLimit by remember(drawerSelectedBouquet, drawerChannels.size) {
                mutableStateOf((targetChannelIdx + 220).coerceAtLeast(280).coerceAtMost(drawerChannels.size.coerceAtLeast(1)))
            }
            val windowedDrawerChannels = remember(drawerChannels, drawerVisibleLimit) {
                if (drawerChannels.size <= drawerVisibleLimit) drawerChannels
                else drawerChannels.subList(0, drawerVisibleLimit)
            }
            val favKeySet = remember(favorites) { favorites.map { it.stableKey }.toHashSet() }

            val drawerBouquetFocusMap = remember { mutableMapOf<Int, FocusRequester>() }
            val drawerChannelFocusMap = remember { mutableMapOf<Int, FocusRequester>() }
            val drawerQualityBtnFocus = remember { FocusRequester() }
            val drawerResizeBtnFocus = remember { FocusRequester() }
            val drawerFavBtnFocus = remember { FocusRequester() }
            val drawerCloseBtnFocus = remember { FocusRequester() }
            val drawerScope = rememberCoroutineScope()

            var drawerBouquetStepJob by remember { mutableStateOf<Job?>(null) }
            var drawerChannelStepJob by remember { mutableStateOf<Job?>(null) }
            var lastDrawerBqStepMs by remember { mutableStateOf(0L) }
            var lastDrawerChStepMs by remember { mutableStateOf(0L) }
            var currentFocusedBouquetIdx by remember(activeBouquetIdx) { mutableStateOf(activeBouquetIdx) }
            var currentFocusedChannelIdx by remember(targetChannelIdx, drawerSelectedBouquet) { mutableStateOf(targetChannelIdx) }

            fun getDrawerBouquetReq(idx: Int): FocusRequester = drawerBouquetFocusMap.getOrPut(idx) { FocusRequester() }
            fun getDrawerChannelReq(idx: Int): FocusRequester = drawerChannelFocusMap.getOrPut(idx) { FocusRequester() }

            fun focusDrawerBouquetStep(targetIdx: Int, isRepeat: Boolean = false) {
                if (allBouquets.isEmpty()) return
                val safeTarget = targetIdx.coerceIn(0, allBouquets.lastIndex)
                val now = android.os.SystemClock.elapsedRealtime()
                if (isRepeat && (now - lastDrawerBqStepMs) < 28L) return
                lastDrawerBqStepMs = now
                currentFocusedBouquetIdx = safeTarget
                if (isSeriesDrawer) {
                    drawerSelectedBouquet = allBouquets[safeTarget].first
                }
                drawerChannelStepJob?.cancel()
                drawerBouquetStepJob?.cancel()
                drawerBouquetStepJob = drawerScope.launch {
                    val vis = bouquetListState.layoutInfo.visibleItemsInfo
                    val innerVis = if (vis.size > 2) vis.drop(1).dropLast(1) else vis
                    val isComfortablyVisible = innerVis.any { it.index == safeTarget }
                    if (!isComfortablyVisible) {
                        runCatching { bouquetListState.scrollToItem((safeTarget - 2).coerceAtLeast(0)) }
                    }
                    var focused = false
                    for (attempt in 1..10) {
                        if (runCatching { getDrawerBouquetReq(safeTarget).requestFocus() }.isSuccess) {
                            focused = true
                            break
                        }
                        if (attempt == 3 || attempt == 6) {
                            runCatching { bouquetListState.scrollToItem(safeTarget) }
                        }
                        delay(16L)
                    }
                    if (!focused) {
                        val fallbackVisIdx = bouquetListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
                        if (fallbackVisIdx != null) {
                            runCatching { getDrawerBouquetReq(fallbackVisIdx).requestFocus() }
                        }
                    }
                }
            }

            fun focusDrawerChannelStep(targetIdx: Int, isRepeat: Boolean = false) {
                if (windowedDrawerChannels.isEmpty()) return
                val safeTarget = targetIdx.coerceIn(0, windowedDrawerChannels.lastIndex)
                val now = android.os.SystemClock.elapsedRealtime()
                if (isRepeat && (now - lastDrawerChStepMs) < 28L) return
                lastDrawerChStepMs = now
                currentFocusedChannelIdx = safeTarget
                if (safeTarget + 28 >= drawerVisibleLimit && drawerVisibleLimit < drawerChannels.size) {
                    drawerVisibleLimit = (drawerVisibleLimit + 140).coerceAtMost(drawerChannels.size)
                }
                drawerBouquetStepJob?.cancel()
                drawerChannelStepJob?.cancel()
                drawerChannelStepJob = drawerScope.launch {
                    val vis = channelListState.layoutInfo.visibleItemsInfo
                    val innerVis = if (vis.size > 2) vis.drop(1).dropLast(1) else vis
                    val isComfortablyVisible = innerVis.any { it.index == safeTarget }
                    if (!isComfortablyVisible) {
                        runCatching { channelListState.scrollToItem((safeTarget - 2).coerceAtLeast(0)) }
                    }
                    for (attempt in 1..8) {
                        if (runCatching { getDrawerChannelReq(safeTarget).requestFocus() }.isSuccess) break
                        if (attempt == 3 || attempt == 6) {
                            runCatching { channelListState.scrollToItem(safeTarget) }
                        }
                        delay(12L)
                    }
                }
            }

            val shouldLoadMoreDrawer by remember {
                derivedStateOf {
                    val lastVisible = channelListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisible >= windowedDrawerChannels.size - 24 && windowedDrawerChannels.size < drawerChannels.size
                }
            }
            LaunchedEffect(shouldLoadMoreDrawer) {
                if (shouldLoadMoreDrawer) {
                    drawerVisibleLimit = (drawerVisibleLimit + 140).coerceAtMost(drawerChannels.size)
                }
            }

            LaunchedEffect(showQuickDrawer, focusFirstChannelTrigger, channel.stableKey) {
                if (!showQuickDrawer) return@LaunchedEffect
                if (targetChannelIdx + 40 > drawerVisibleLimit) {
                    drawerVisibleLimit = (targetChannelIdx + 160).coerceAtMost(drawerChannels.size.coerceAtLeast(1))
                }
                runCatching {
                    bouquetListState.scrollToItem((activeBouquetIdx - 1).coerceAtLeast(0))
                }
                runCatching {
                    channelListState.scrollToItem((targetChannelIdx - 2).coerceAtLeast(0))
                }
                for (attempt in 1..8) {
                    delay(if (attempt <= 2) 16L else 40L)
                    val focusedOk = if (windowedDrawerChannels.isNotEmpty() && targetChannelIdx < windowedDrawerChannels.size) {
                        runCatching { getDrawerChannelReq(targetChannelIdx).requestFocus() }.isSuccess
                    } else {
                        runCatching { getDrawerBouquetReq(activeBouquetIdx).requestFocus() }.isSuccess
                    }
                    if (focusedOk) break
                    if (attempt == 3 || attempt == 6) {
                        runCatching {
                            channelListState.scrollToItem((targetChannelIdx - 1).coerceAtLeast(0))
                        }
                    }
                }
            }

            LaunchedEffect(focusBouquetTrigger) {
                if (focusBouquetTrigger > 0 && allBouquets.isNotEmpty()) {
                    focusDrawerBouquetStep(activeBouquetIdx)
                }
            }

            // خلفية شفافة: نقر أي مكان خارج القائمة يغلقها (مهم للهاتف)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showQuickDrawer = false }
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (isPhoneLandscape) 0.78f else 0.64f)
                    .widthIn(min = if (isPhoneLandscape) 320.dp else 420.dp, max = if (isPhoneLandscape) 620.dp else 720.dp)
                    .fillMaxHeight(if (isPhoneLandscape) 0.92f else 0.90f)
                    .align(Alignment.CenterStart)
                    .padding(start = if (isPhoneLandscape) 6.dp else 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF00A0A0E))
                    .border(1.8.dp, BeinHighlightPurple, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {} // استهلاك النقر داخل القائمة حتى لا تُغلق
                    .padding(if (isPhoneLandscape) 5.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val firstDrawerListReq = if (windowedDrawerChannels.isNotEmpty()) {
                    getDrawerChannelReq(currentFocusedChannelIdx.coerceIn(0, windowedDrawerChannels.lastIndex))
                } else {
                    getDrawerBouquetReq(currentFocusedBouquetIdx.coerceIn(0, (allBouquets.size - 1).coerceAtLeast(0)))
                }
                val firstDrawerBouquetReq = if (allBouquets.isNotEmpty()) {
                    getDrawerBouquetReq(currentFocusedBouquetIdx.coerceIn(0, allBouquets.lastIndex))
                } else {
                    firstDrawerListReq
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF14141C))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSeriesDrawer) "📺 الأجزاء والحلقات — ${channel.name}" else "📺 ${channel.name}",
                        color = EmeraldLive,
                        fontSize = if (isPhoneLandscape) 13.sp else 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FocusableActionPill(
                            text = "🎬 الجودة",
                            isPrimary = false,
                            compact = true,
                            dense = true,
                            modifier = Modifier.focusRequester(drawerQualityBtnFocus),
                            downTarget = firstDrawerBouquetReq,
                            leftTarget = drawerResizeBtnFocus,
                            onClick = {
                                showQuickDrawer = false
                                tracksModalInitialTab = 0
                                showTracksDialog = true
                            }
                        )
                        FocusableActionPill(
                            text = "📐",
                            isPrimary = false,
                            compact = true,
                            dense = true,
                            modifier = Modifier.focusRequester(drawerResizeBtnFocus),
                            downTarget = firstDrawerListReq,
                            rightTarget = drawerQualityBtnFocus,
                            leftTarget = drawerFavBtnFocus,
                            onClick = {
                                val modes = VideoResizeMode.values()
                                resizeMode = modes[(resizeMode.ordinal + 1) % modes.size]
                            }
                        )
                        FocusableActionPill(
                            text = if (isFavorite) "❤️" else "🤍",
                            isPrimary = isFavorite,
                            compact = true,
                            dense = true,
                            modifier = Modifier.focusRequester(drawerFavBtnFocus),
                            downTarget = firstDrawerListReq,
                            rightTarget = drawerResizeBtnFocus,
                            leftTarget = drawerCloseBtnFocus,
                            onClick = { onToggleFavorite(channel) }
                        )
                        FocusableActionPill(
                            text = "✕",
                            isPrimary = false,
                            compact = true,
                            dense = true,
                            modifier = Modifier.focusRequester(drawerCloseBtnFocus),
                            downTarget = firstDrawerListReq,
                            rightTarget = drawerFavBtnFocus,
                            onClick = { showQuickDrawer = false }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // RIGHT COLUMN (in RTL): Seasons / الأجزاء (for Series) OR Bouquets / الباقات (for Live TV)
                    Column(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF101015))
                            .padding(5.dp)
                    ) {
                        Text(
                            text = if (isSeriesDrawer) {
                                "📂 الأجزاء (${allBouquets.size})"
                            } else {
                                "📂 ${AppStrings.tr(lang, "الباقات", "Bouquets", "Bouquets")} (${allBouquets.size})"
                            },
                            color = Color.White,
                            fontSize = if (isPhoneLandscape) 10.sp else 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp)
                        )
                        LazyColumn(
                            state = bouquetListState,
                            verticalArrangement = Arrangement.spacedBy(3.5.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(allBouquets, key = { idx, pair -> "${idx}_${pair.first}" }) { idx, (bName, bList) ->
                                val selected = drawerSelectedBouquet == bName
                                var bFocused by remember { mutableStateOf(false) }
                                val bReq = getDrawerBouquetReq(idx)
                                Row(
                                    modifier = Modifier
                                        .focusRequester(bReq)
                                        .fillMaxWidth()
                                        .scale(if (bFocused) 1.02f else 1.0f)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(
                                            when {
                                                bFocused -> Color(0xFF282216)
                                                selected -> BeinPrimaryPurple
                                                else -> Color(0xFF131D31)
                                            }
                                        )
                                        .border(
                                            width = if (bFocused) 2.5.dp else if (selected) 1.4.dp else 0.dp,
                                            color = if (bFocused) Color(0xFFBAE6FD) else if (selected) BeinHighlightPurple else Color.Transparent,
                                            shape = RoundedCornerShape(7.dp)
                                        )
                                        .onFocusChanged {
                                            bFocused = it.isFocused
                                            if (it.isFocused) currentFocusedBouquetIdx = idx
                                            if (it.isFocused && isSeriesDrawer && drawerSelectedBouquet != bName) {
                                                drawerSelectedBouquet = bName
                                            }
                                        }
                                        .onPreviewKeyEvent { ev ->
                                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                val toChannelsKey = if (isDrawerRtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
                                                val outerEdgeKey = if (isDrawerRtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                                                when (ev.nativeKeyEvent.keyCode) {
                                                    KeyEvent.KEYCODE_BACK,
                                                    KeyEvent.KEYCODE_ESCAPE -> {
                                                        if (ev.nativeKeyEvent.repeatCount == 0) {
                                                            showQuickDrawer = false
                                                        }
                                                        true
                                                    }
                                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                        if (idx + 1 < allBouquets.size) {
                                                            focusDrawerBouquetStep(idx + 1, isRepeat = ev.nativeKeyEvent.repeatCount > 0)
                                                        }
                                                        true
                                                    }
                                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                                        if (idx > 0) {
                                                            focusDrawerBouquetStep(idx - 1, isRepeat = ev.nativeKeyEvent.repeatCount > 0)
                                                        } else if (ev.nativeKeyEvent.repeatCount == 0) {
                                                            drawerBouquetStepJob?.cancel()
                                                            runCatching { drawerQualityBtnFocus.requestFocus() }
                                                        }
                                                        true
                                                    }
                                                    outerEdgeKey -> {
                                                        true
                                                    }
                                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                                    KeyEvent.KEYCODE_ENTER,
                                                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                                                    KeyEvent.KEYCODE_BUTTON_A,
                                                    toChannelsKey -> {
                                                        if (ev.nativeKeyEvent.repeatCount == 0) {
                                                            drawerBouquetStepJob?.cancel()
                                                            drawerSelectedBouquet = bName
                                                            if (bList.isNotEmpty()) {
                                                                focusFirstChannelTrigger += 1
                                                            }
                                                        }
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                        .focusable()
                                        .clickable {
                                            drawerSelectedBouquet = bName
                                            focusFirstChannelTrigger += 1
                                        }
                                        .padding(horizontal = 7.dp, vertical = if (isPhoneLandscape) 5.dp else 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = bName,
                                        color = Color.White,
                                        fontSize = if (isPhoneLandscape) 14.sp else 17.sp,
                                        fontWeight = if (bFocused || selected) FontWeight.ExtraBold else FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = if (isPhoneLandscape) 17.sp else 21.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "${bList.size}",
                                        color = if (bFocused) Color.White else if (selected) EmeraldLive else Color(0xFFE2E8F0),
                                        fontSize = if (isPhoneLandscape) 11.sp else 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // LEFT COLUMN (in RTL): Episodes / الحلقات (with الحالية / التالية / تمت المشاهدة) OR Channels / القنوات
                    Column(
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF101015))
                            .padding(5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSeriesDrawer) "🎬 حلقات $drawerSelectedBouquet (${drawerChannels.size})" else "$drawerSelectedBouquet (${drawerChannels.size})",
                                color = Color.White,
                                fontSize = if (isPhoneLandscape) 10.sp else 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "OK للتشغيل",
                                color = Color(0xFF94A3B8),
                                fontSize = if (isPhoneLandscape) 8.5.sp else 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (windowedDrawerChannels.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isSeriesDrawer) "لا توجد حلقات في هذا الجزء حالياً" else AppStrings.tr(
                                        lang,
                                        "لا توجد قنوات في هذه الباقة حالياً",
                                        "Aucune chaîne dans ce bouquet",
                                        "No channels in this bouquet"
                                    ),
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                state = channelListState,
                                verticalArrangement = Arrangement.spacedBy(3.5.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(windowedDrawerChannels, key = { idx, it -> "${idx}_${it.stableKey}" }) { idx, item ->
                                    val active = item.stableKey == channel.stableKey ||
                                        (isSeriesDrawer && item.url.trim() == channel.url.trim())
                                    val isNextItemInDrawer = isSeriesDrawer &&
                                        rawActiveChannelIdx >= 0 &&
                                        idx == rawActiveChannelIdx + 1 &&
                                        drawerSelectedBouquet == channel.group
                                    val itemProg = if (isSeriesDrawer) {
                                        vodWatchProgressMap[item.stableKey]
                                            ?: vodWatchProgressMap[item.url.trim()]
                                            ?: vodWatchProgressMap.values.firstOrNull { it.resolvedKey == item.url.trim() }
                                    } else null
                                    val isWatchedEpisode = isSeriesDrawer && (
                                        (itemProg != null && itemProg.isCompleted) ||
                                            (rawActiveChannelIdx > idx && drawerSelectedBouquet == channel.group && itemProg != null && itemProg.positionMs >= 5_000L)
                                        )
                                    val isFavItem = favKeySet.contains(item.stableKey)
                                    var rowFocused by remember { mutableStateOf(false) }
                                    val chReq = getDrawerChannelReq(idx)

                                    val epStatusLabel = when {
                                        active -> "▶ الحالية"
                                        isNextItemInDrawer -> "⏭ التالية"
                                        isWatchedEpisode -> "✅ تمت المشاهدة"
                                        isSeriesDrawer && itemProg != null && itemProg.positionMs >= 5_000L -> "⏯ ${(itemProg.progressFraction * 100).toInt()}%"
                                        else -> ""
                                    }
                                    val epStatusColor = when {
                                        active -> EmeraldLive
                                        isNextItemInDrawer -> Color(0xFFAD8B43)
                                        isWatchedEpisode -> Color(0xFFAD8B43)
                                        else -> BeinHighlightPurple
                                    }

                                    Row(
                                        modifier = Modifier
                                            .focusRequester(chReq)
                                            .fillMaxWidth()
                                            .scale(if (rowFocused) 1.02f else 1.0f)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(
                                                when {
                                                    rowFocused -> Color(0xFF282216)
                                                    active -> Color(0xFF064E3B)
                                                    isNextItemInDrawer -> Color(0xFF1E3A8A)
                                                    else -> Color(0xFF131D31)
                                                }
                                            )
                                            .border(
                                                width = if (rowFocused) 2.5.dp else if (active || isNextItemInDrawer) 1.5.dp else 0.dp,
                                                color = when {
                                                    rowFocused -> Color(0xFFBAE6FD)
                                                    active -> EmeraldLive
                                                    isNextItemInDrawer -> Color(0xFFAD8B43)
                                                    else -> Color.Transparent
                                                },
                                                shape = RoundedCornerShape(7.dp)
                                            )
                                            .onFocusChanged { rowFocused = it.isFocused; if (it.isFocused) currentFocusedChannelIdx = idx }
                                            .onPreviewKeyEvent { ev ->
                                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                    val backToBouquetsKey = if (isDrawerRtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                                                    val outerEdgeKey = if (isDrawerRtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
                                                    when (ev.nativeKeyEvent.keyCode) {
                                                        KeyEvent.KEYCODE_BACK,
                                                        KeyEvent.KEYCODE_ESCAPE -> {
                                                            if (ev.nativeKeyEvent.repeatCount == 0) {
                                                                showQuickDrawer = false
                                                            }
                                                            true
                                                        }
                                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                            if (idx + 1 < windowedDrawerChannels.size) {
                                                                focusDrawerChannelStep(idx + 1, isRepeat = ev.nativeKeyEvent.repeatCount > 0)
                                                            }
                                                            true
                                                        }
                                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                                            if (idx > 0) {
                                                                focusDrawerChannelStep(idx - 1, isRepeat = ev.nativeKeyEvent.repeatCount > 0)
                                                            } else if (ev.nativeKeyEvent.repeatCount == 0) {
                                                                drawerChannelStepJob?.cancel()
                                                                runCatching { drawerFavBtnFocus.requestFocus() }
                                                            }
                                                            true
                                                        }
                                                        outerEdgeKey -> {
                                                            true
                                                        }
                                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                                        KeyEvent.KEYCODE_ENTER,
                                                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                                                        KeyEvent.KEYCODE_BUTTON_A -> {
                                                            if (ev.nativeKeyEvent.repeatCount == 0) {
                                                                // استجابة فورية: أغلق القائمة وافتح القناة دون أي تأخير اصطناعي
                                                                showControls = false
                                                                showQuickDrawer = false
                                                                if (!active) {
                                                                    onSelectChannel(item)
                                                                }
                                                            }
                                                            true
                                                        }
                                                        backToBouquetsKey -> {
                                                            if (ev.nativeKeyEvent.repeatCount == 0) {
                                                                focusDrawerBouquetStep(activeBouquetIdx)
                                                            }
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                } else false
                                            }
                                            .focusable()
                                            .clickable {
                                                showControls = false
                                                showQuickDrawer = false
                                                if (!active) {
                                                    onSelectChannel(item)
                                                }
                                            }
                                            .padding(horizontal = 6.dp, vertical = if (isPhoneLandscape) 4.5.dp else 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .widthIn(min = 22.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    when {
                                                        rowFocused -> Color(0xFF111111)
                                                        active -> EmeraldLive
                                                        else -> Color(0xFF11181D)
                                                    }
                                                )
                                                .padding(horizontal = 4.dp, vertical = 1.5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${idx + 1}",
                                                color = if (active && !rowFocused) Color.Black else Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Spacer(Modifier.width(5.dp))
                                        if (!isSeriesDrawer) {
                                            ChannelAvatar(
                                                name = item.name,
                                                logoUrl = item.logo,
                                                modifier = Modifier.size(if (isPhoneLandscape) 22.dp else 26.dp),
                                                cornerRadius = 5.dp,
                                                innerPadding = 1.dp
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = if (isSeriesDrawer) {
                                                item.name.substringAfter(" - ").ifBlank { item.name }
                                            } else {
                                                item.name
                                            },
                                            color = Color.White,
                                            fontSize = if (isPhoneLandscape) 14.sp else 17.sp,
                                            fontWeight = if (active || rowFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = if (isPhoneLandscape) 17.sp else 21.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (epStatusLabel.isNotBlank()) {
                                            Spacer(Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(if (rowFocused) Color(0xFF0F172A) else epStatusColor.copy(alpha = 0.22f))
                                                    .border(1.dp, if (rowFocused) Color.Black else epStatusColor, RoundedCornerShape(5.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                            ) {
                                                Text(
                                                    text = epStatusLabel,
                                                    color = if (rowFocused) Color.White else epStatusColor,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                        if (isFavItem) {
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = "★",
                                                color = if (rowFocused) BeinPrimaryPurple else Color(0xFFFF4D6D),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showTracksDialog) {
            InteractiveTrackAndSpeedModal(
                player = player,
                tracks = currentTracksSnapshot,
                mqVariants = mqVariants,
                activeMqVariant = activeMqVariant,
                initialSection = tracksModalInitialTab,
                preferSubtitlesTab = tracksModalInitialTab == 2,
                manualLockedQuality = if (isMultiQualityChannel) MultiQualityEngine.getManualQuality(channel.stableKey) else null,
                onSelectAutoMq = {
                    MultiQualityEngine.setManualQuality(channel.stableKey, null)
                    val bestVar = MultiQualityEngine.pickBestVariant(channel)
                    if (bestVar != null) {
                        isSeamlessMqTransition = true
                        activeMqVariant = bestVar
                        mqSwitchBanner = "🤖 تم تفعيل التكيّف التلقائي الذكي (Auto ABR: ${bestVar.quality.labelAr})"
                        codecRequiresVlc = false
                        forceVlcSoftwareVideo = false
                        candidateIndex = 0
                        formatAttemptMode = 0
                        errorMessage = null
                        mqSwitchGeneration += 1
                    }
                },
                onSelectMqVariant = { chosenVar ->
                    MultiQualityEngine.setManualQuality(channel.stableKey, chosenVar.quality, chosenVar.sourceId)
                    MultiQualityEngine.setActiveVariant(channel.stableKey, chosenVar)
                    isSeamlessMqTransition = true
                    activeMqVariant = chosenVar
                    mqSwitchBanner = "📺 تم تثبيت الجودة يدوياً: ${chosenVar.quality.labelAr} (${chosenVar.bouquetLabel})"
                    codecRequiresVlc = false
                    forceVlcSoftwareVideo = false
                    candidateIndex = 0
                    formatAttemptMode = 0
                    errorMessage = null
                    mqSwitchGeneration += 1
                },
                currentSpeed = playbackSpeed,
                onSelectSpeed = { playbackSpeed = it },
                compact = isPhoneLandscape,
                onDismiss = { showTracksDialog = false }
            )
        }

        // لا يتم إظهار أي نافذة منبثقة تحجب المشاهدة أو توقف المحاولات؛ يستمر المشغل بالمحاولة تلقائياً في الخلفية
    }
}

/**
 * زر التقديم والتأخير الدائري الاحترافي بنمط نتفلكس (Netflix Circular Arrow Seek Button):
 * يرسم سهماً دائرياً أنيقاً يحيط برقم الثواني (10) في الوسط مع استجابة فورية للريموت.
 */
@Composable
private fun NetflixCircularSeekButton(
    secondsLabel: String = "10",
    isForward: Boolean,
    subtitleLabel: String = "",
    sizeDp: androidx.compose.ui.unit.Dp = 58.dp,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    upTarget: FocusRequester? = null,
    downTarget: FocusRequester? = null,
    leftTarget: FocusRequester? = null,
    rightTarget: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val fgColor = if (focused) Color(0xFF09090D) else Color.White
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = { SafeClickGuard.runThrottled("NETFLIX_SEEK_BTN", 140L) { onClick() } },
            modifier = modifier
                .size(sizeDp)
                .scale(if (focused) 1.15f else 1.0f)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { ev ->
                    val code = ev.nativeKeyEvent.keyCode
                    val isSelect = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == KeyEvent.KEYCODE_ENTER ||
                        code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                        code == KeyEvent.KEYCODE_SPACE
                    if (isSelect) {
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                            SafeClickGuard.runThrottled("NETFLIX_SEEK_BTN", 140L) { onClick() }
                        }
                        true
                    } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                        when (code) {
                            KeyEvent.KEYCODE_DPAD_UP -> if (upTarget != null) { runCatching { upTarget.requestFocus() }; true } else false
                            KeyEvent.KEYCODE_DPAD_DOWN -> if (downTarget != null) { runCatching { downTarget.requestFocus() }; true } else false
                            KeyEvent.KEYCODE_DPAD_LEFT -> if (leftTarget != null) { runCatching { leftTarget.requestFocus() }; true } else false
                            KeyEvent.KEYCODE_DPAD_RIGHT -> if (rightTarget != null) { runCatching { rightTarget.requestFocus() }; true } else false
                            else -> false
                        }
                    } else false
                }
                .focusable()
                .background(if (focused) Color.White else Color(0xCC12121A), CircleShape)
                .border(
                    width = if (focused) 2.8.dp else 1.8.dp,
                    color = if (focused) BeinFocusGlow else Color.White.copy(alpha = 0.72f),
                    shape = CircleShape
                )
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.size(sizeDp * 0.66f)) {
                    val strokeW = size.minDimension * 0.095f
                    val pad = strokeW * 1.1f
                    val arcSize = Size(size.width - pad * 2f, size.height - pad * 2f)
                    val topLeft = Offset(pad, pad)
                    // Draw circular arc with gap at the top where the arrowhead sits
                    if (isForward) {
                        drawArc(
                            color = fgColor,
                            startAngle = -70f,
                            sweepAngle = 295f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                        // Clockwise arrowhead at top-right of the circle
                        val cx = size.width * 0.62f
                        val cy = pad * 1.15f
                        val arrowSize = strokeW * 2.15f
                        val path = Path().apply {
                            moveTo(cx + arrowSize * 0.55f, cy)
                            lineTo(cx - arrowSize * 0.45f, cy - arrowSize * 0.55f)
                            lineTo(cx - arrowSize * 0.35f, cy + arrowSize * 0.55f)
                            close()
                        }
                        drawPath(path = path, color = fgColor)
                    } else {
                        drawArc(
                            color = fgColor,
                            startAngle = -110f,
                            sweepAngle = -295f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                        // Counter-clockwise arrowhead at top-left of the circle
                        val cx = size.width * 0.38f
                        val cy = pad * 1.15f
                        val arrowSize = strokeW * 2.15f
                        val path = Path().apply {
                            moveTo(cx - arrowSize * 0.55f, cy)
                            lineTo(cx + arrowSize * 0.45f, cy - arrowSize * 0.55f)
                            lineTo(cx + arrowSize * 0.35f, cy + arrowSize * 0.55f)
                            close()
                        }
                        drawPath(path = path, color = fgColor)
                    }
                }
                Text(
                    text = secondsLabel,
                    color = fgColor,
                    fontSize = if (compact) 11.sp else 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (subtitleLabel.isNotBlank()) {
            Text(
                text = subtitleLabel,
                color = if (focused) BeinFocusGlow else Color.White.copy(alpha = 0.85f),
                fontSize = if (compact) 9.5.sp else 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FocusableCircleButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = Color.White,
    sizeDp: androidx.compose.ui.unit.Dp = 44.dp,
    modifier: Modifier = Modifier,
    upTarget: FocusRequester? = null,
    downTarget: FocusRequester? = null,
    leftTarget: FocusRequester? = null,
    rightTarget: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = { SafeClickGuard.runThrottled("PLAYER_CIRCLE_BTN", 200L) { onClick() } },
        modifier = modifier
            .size(sizeDp)
            .scale(if (focused) 1.14f else 1.0f)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                val code = ev.nativeKeyEvent.keyCode
                val isSelect = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                    code == KeyEvent.KEYCODE_ENTER ||
                    code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                    code == KeyEvent.KEYCODE_SPACE
                if (isSelect) {
                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                        SafeClickGuard.runThrottled("PLAYER_CIRCLE_BTN", 200L) { onClick() }
                    }
                    true
                } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_UP -> if (upTarget != null) { runCatching { upTarget.requestFocus() }; true } else false
                        KeyEvent.KEYCODE_DPAD_DOWN -> if (downTarget != null) { runCatching { downTarget.requestFocus() }; true } else false
                        KeyEvent.KEYCODE_DPAD_LEFT -> if (leftTarget != null) { runCatching { leftTarget.requestFocus() }; true } else false
                        KeyEvent.KEYCODE_DPAD_RIGHT -> if (rightTarget != null) { runCatching { rightTarget.requestFocus() }; true } else false
                        else -> false
                    }
                } else false
            }
            .focusable()
            .background(if (focused) Color.White else Color(0xE6181820), CircleShape)
            .border(
                width = if (focused) 2.8.dp else 1.5.dp,
                color = if (focused) BeinFocusGlow else BeinHighlightPurple.copy(alpha = 0.7f),
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) Color.Black else tint,
            modifier = Modifier.size(sizeDp * 0.52f)
        )
    }
}

@Composable
private fun FocusableActionPill(
    text: String,
    isPrimary: Boolean,
    compact: Boolean = false,
    dense: Boolean = false,
    modifier: Modifier = Modifier,
    upTarget: FocusRequester? = null,
    downTarget: FocusRequester? = null,
    leftTarget: FocusRequester? = null,
    rightTarget: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .scale(if (focused) 1.06f else 1.0f)
            .clip(RoundedCornerShape(if (dense) 7.dp else 10.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                val code = ev.nativeKeyEvent.keyCode
                val isSelect = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                    code == KeyEvent.KEYCODE_ENTER ||
                    code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                    code == KeyEvent.KEYCODE_SPACE
                if (isSelect) {
                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                        SafeClickGuard.runThrottled("PLAYER_UI_CLICK", 200L) { onClick() }
                    }
                    true
                } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_UP -> if (upTarget != null) { runCatching { upTarget.requestFocus() }; true } else false
                        KeyEvent.KEYCODE_DPAD_DOWN -> if (downTarget != null) { runCatching { downTarget.requestFocus() }; true } else false
                        KeyEvent.KEYCODE_DPAD_LEFT -> if (leftTarget != null) { runCatching { leftTarget.requestFocus() }; true } else false
                        KeyEvent.KEYCODE_DPAD_RIGHT -> if (rightTarget != null) { runCatching { rightTarget.requestFocus() }; true } else false
                        else -> false
                    }
                } else false
            }
            .focusable()
            .background(
                when {
                    focused -> Color.White
                    isPrimary -> BeinPrimaryPurple
                    else -> Color(0xE6181820)
                }
            )
            .border(
                width = if (focused) 2.2.dp else 1.dp,
                color = if (focused) BeinFocusGlow else BeinHighlightPurple.copy(alpha = 0.75f),
                shape = RoundedCornerShape(if (dense) 7.dp else 10.dp)
            )
            .clickable { onClick() }
            .padding(
                horizontal = when { dense -> 7.dp; compact -> 11.dp; else -> 14.dp },
                vertical = when { dense -> 3.5.dp; compact -> 6.dp; else -> 8.dp }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            fontSize = when { dense -> 10.sp; compact -> 11.sp; else -> 13.sp },
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun InteractiveTrackAndSpeedModal(
    player: ExoPlayer,
    tracks: Tracks,
    mqVariants: List<MultiQualityEngine.QualityVariant> = emptyList(),
    activeMqVariant: MultiQualityEngine.QualityVariant? = null,
    initialSection: Int = 0,
    manualLockedQuality: StreamQualityRank? = null,
    onSelectAutoMq: () -> Unit = {},
    onSelectMqVariant: (MultiQualityEngine.QualityVariant) -> Unit = {},
    currentSpeed: PlaybackSpeedOption,
    onSelectSpeed: (PlaybackSpeedOption) -> Unit,
    compact: Boolean,
    preferSubtitlesTab: Boolean = false,
    onDismiss: () -> Unit
) {
    var selectedSection by remember(initialSection, preferSubtitlesTab) {
        mutableStateOf(if (preferSubtitlesTab) 2 else initialSection.coerceIn(0, 3))
    }
    val tab0Focus = remember { FocusRequester() }
    val tab1Focus = remember { FocusRequester() }
    val tab2Focus = remember { FocusRequester() }
    val tab3Focus = remember { FocusRequester() }
    val firstItemFocus = when (selectedSection) {
        1 -> tab1Focus
        2 -> tab2Focus
        3 -> tab3Focus
        else -> tab0Focus
    }

    val videoOptions = remember(tracks) {
        val list = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { gIndex, group ->
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (tIndex in 0 until group.length) {
                    val fmt = group.getTrackFormat(tIndex)
                    if (fmt.height > 0) {
                        val badge = when {
                            fmt.height >= 2160 -> "4K UHD"
                            fmt.height >= 1080 -> "Full HD 1080p"
                            fmt.height >= 720 -> "HD 720p"
                            else -> "SD ${fmt.height}p"
                        }
                        val br = if (fmt.bitrate > 0) "${fmt.bitrate / 1000} kbps" else "Adaptive"
                        val fps = if (fmt.frameRate > 0) " • ${fmt.frameRate.toInt()}fps" else ""
                        list.add(
                            TrackOption(
                                title = "$badge (${fmt.width}×${fmt.height})",
                                subtitle = "معدل البث: $br$fps",
                                groupIndex = gIndex,
                                trackIndex = tIndex,
                                isSelected = group.isTrackSelected(tIndex)
                            )
                        )
                    }
                }
            }
        }
        list.sortedByDescending { it.title }
    }

    val audioOptions = remember(tracks) {
        val list = mutableListOf<TrackOption>()
        var commentatorNum = 1
        tracks.groups.forEachIndexed { gIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (tIndex in 0 until group.length) {
                    val fmt = group.getTrackFormat(tIndex)
                    val rawLabel = fmt.label?.takeIf { it.isNotBlank() }
                    val rawLang = fmt.language?.takeIf { it.isNotBlank() && it != "und" }
                    val title = when {
                        rawLabel != null && rawLang != null -> "معلق / صوت $commentatorNum: $rawLabel ($rawLang)"
                        rawLabel != null -> "معلق / صوت $commentatorNum: $rawLabel"
                        rawLang != null -> "معلق / مسار صوتي $commentatorNum ($rawLang)"
                        else -> "المعلق الصوتي / المسار رقم $commentatorNum"
                    }
                    val channelsDesc = if (fmt.channelCount >= 6) "صوت محيطي 5.1" else "ستيريو واضح"
                    val br = if (fmt.bitrate > 0) " • ${fmt.bitrate / 1000} kbps" else ""
                    list.add(
                        TrackOption(
                            title = title,
                            subtitle = "$channelsDesc$br",
                            groupIndex = gIndex,
                            trackIndex = tIndex,
                            isSelected = group.isTrackSelected(tIndex)
                        )
                    )
                    commentatorNum++
                }
            }
        }
        list
    }

    // Multi-subtitles for Movies & Series (and any stream that embeds text tracks)
    val subtitleOptions = remember(tracks, player.trackSelectionParameters) {
        val list = mutableListOf<TrackOption>()
        var textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        var anySelected = false
        var subNum = 1
        tracks.groups.forEachIndexed { gIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (tIndex in 0 until group.length) {
                    // Skip non-displayable image/cea608 if unsupported — still list if present
                    val fmt = group.getTrackFormat(tIndex)
                    val mime = (fmt.sampleMimeType ?: "").lowercase(Locale.ROOT)
                    val isForced = (fmt.selectionFlags and androidx.media3.common.C.SELECTION_FLAG_FORCED) != 0
                    val isDefault = (fmt.selectionFlags and androidx.media3.common.C.SELECTION_FLAG_DEFAULT) != 0
                    val langLabel = friendlyTrackLanguageLabel(fmt.language, fmt.label)
                    val kind = when {
                        mime.contains("ass") || mime.contains("ssa") -> "ASS/SSA"
                        mime.contains("vtt") -> "WebVTT"
                        mime.contains("subrip") || mime.contains("srt") -> "SRT"
                        mime.contains("ttml") || mime.contains("dfxp") -> "TTML"
                        mime.contains("cea") -> "CEA-608/708"
                        else -> "ترجمة مضمّنة"
                    }
                    val flags = buildList {
                        if (isDefault) add("افتراضية")
                        if (isForced) add("إجبارية")
                    }.joinToString(" • ")
                    val selected = group.isTrackSelected(tIndex) && !textDisabled
                    if (selected) anySelected = true
                    list.add(
                        TrackOption(
                            title = "📝 ترجمة $subNum: $langLabel",
                            subtitle = listOf(kind, flags).filter { it.isNotBlank() }.joinToString(" • ")
                                .ifBlank { "مسار ترجمة من السيرفر" },
                            groupIndex = gIndex,
                            trackIndex = tIndex,
                            isSelected = selected
                        )
                    )
                    subNum++
                }
            }
        }
        // Off option always first when there is at least one track
        if (list.isNotEmpty()) {
            val offSelected = textDisabled || !anySelected
            list.add(
                0,
                TrackOption(
                    title = "🚫 إيقاف الترجمة",
                    subtitle = "إخفاء كل مسارات الترجمة",
                    groupIndex = -1,
                    trackIndex = -1,
                    isSelected = offSelected,
                    isDisableOption = true
                )
            )
            // If Off is selected, clear other selections visually
            if (offSelected) {
                for (i in 1 until list.size) {
                    list[i] = list[i].copy(isSelected = false)
                }
            }
        }
        list
    }

    val hasVideoOverride = remember(player.trackSelectionParameters) {
        player.trackSelectionParameters.overrides.keys.any { it.type == C.TRACK_TYPE_VIDEO }
    }

    LaunchedEffect(Unit) {
        delay(80L)
        runCatching { firstItemFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = if (compact) 540.dp else 640.dp)
                    .fillMaxWidth(0.90f)
                    .fillMaxHeight(if (compact) 0.88f else 0.80f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF101015))
                    .border(2.dp, BeinHighlightPurple, RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(if (compact) 12.dp else 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "الجودة • الصوت • الترجمة • السرعة ⚙️",
                            color = Color.White,
                            fontSize = if (compact) 13.sp else 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "تنقل بأسهم الريموت (▲▼◄►) واضغط (OK) للاختيار الفوري",
                            color = Color.LightGray,
                            fontSize = if (compact) 9.sp else 11.sp
                        )
                    }
                    FocusableCircleButton(
                        onClick = onDismiss,
                        icon = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        sizeDp = if (compact) 32.dp else 38.dp
                    )
                }

                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InteractiveModalTabButton(
                        title = "📺 جودة",
                        selected = selectedSection == 0,
                        compact = compact,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tab0Focus),
                        onClick = { selectedSection = 0 }
                    )
                    InteractiveModalTabButton(
                        title = "🎙 صوت (${audioOptions.size.coerceAtLeast(1)})",
                        selected = selectedSection == 1,
                        compact = compact,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tab1Focus),
                        onClick = { selectedSection = 1 }
                    )
                    InteractiveModalTabButton(
                        title = "📝 ترجمة (${(subtitleOptions.size - 1).coerceAtLeast(0)})",
                        selected = selectedSection == 2,
                        compact = compact,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tab2Focus),
                        onClick = { selectedSection = 2 }
                    )
                    InteractiveModalTabButton(
                        title = "⚡ سرعة",
                        selected = selectedSection == 3,
                        compact = compact,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tab3Focus),
                        onClick = { selectedSection = 3 }
                    )
                }

                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (selectedSection) {
                        0 -> {
                            if (mqVariants.size >= 2) {
                                item {
                                    val currentAutoLabel = activeMqVariant?.quality?.resolutionTag ?: "Auto"
                                    InteractiveTrackCard(
                                        title = "🤖 تلقائي ذكي مثل يوتيوب (Auto — يعمل الآن: $currentAutoLabel)",
                                        subtitle = "يتكيّف تلقائياً باللحظة حسب سرعة الإنترنت وحالة تدفق الفيديو من المصدر",
                                        isSelected = manualLockedQuality == null && !hasVideoOverride,
                                        compact = compact,
                                        onClick = {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                                .build()
                                            onSelectAutoMq()
                                            onDismiss()
                                        }
                                    )
                                }

                                itemsIndexed(mqVariants) { _, vOpt ->
                                    val isSelectedVar = manualLockedQuality != null && activeMqVariant?.sourceId == vOpt.sourceId
                                    val healthScore = MultiQualityEngine.getVariantHealthScore(vOpt)
                                    val statusText = when {
                                        MultiQualityEngine.isVariantOutage(vOpt) -> "⛔ متوقف حالياً من المصدر"
                                        MultiQualityEngine.isVariantCongested(vOpt) -> "⚠️ ضغط/ثقل في المصدر ($healthScore%)"
                                        else -> "✅ مستقر ($healthScore%) • يحتاج ≥ ${vOpt.quality.minSpeedKbps / 1000.0} Mbps"
                                    }
                                    InteractiveTrackCard(
                                        title = "📺 ${vOpt.quality.resolutionTag} — ${vOpt.channel.name}",
                                        subtitle = "الباقة: ${vOpt.bouquetLabel} • $statusText",
                                        isSelected = isSelectedVar,
                                        compact = compact,
                                        onClick = {
                                            onSelectMqVariant(vOpt)
                                            onDismiss()
                                        }
                                    )
                                }
                            } else {
                                item {
                                    InteractiveTrackCard(
                                        title = "تلقائي متكيّف (Auto Adaptive HD) — موصى به",
                                        subtitle = "يختار أعلى جودة مستقرة حسب سرعة الاتصال وحالة البث",
                                        isSelected = !hasVideoOverride,
                                        compact = compact,
                                        onClick = {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                                .build()
                                            onDismiss()
                                        }
                                    )
                                }
                            }

                            itemsIndexed(videoOptions) { _, opt ->
                                InteractiveTrackCard(
                                    title = opt.title,
                                    subtitle = opt.subtitle,
                                    isSelected = hasVideoOverride && opt.isSelected,
                                    compact = compact,
                                    onClick = {
                                        val group = tracks.groups[opt.groupIndex].mediaTrackGroup
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(
                                                TrackSelectionOverride(group, opt.trackIndex)
                                            )
                                            .build()
                                        onDismiss()
                                    }
                                )
                            }
                        }
                        1 -> {
                            if (audioOptions.isEmpty()) {
                                item {
                                    InteractiveTrackCard(
                                        title = "المسار الصوتي الافتراضي للمصدر",
                                        subtitle = "هذه القناة تبث بمسار تعليق صوتي موحّد",
                                        isSelected = true,
                                        compact = compact,
                                        onClick = onDismiss
                                    )
                                }
                            } else {
                                itemsIndexed(audioOptions) { _, opt ->
                                    InteractiveTrackCard(
                                        title = opt.title,
                                        subtitle = opt.subtitle,
                                        isSelected = opt.isSelected,
                                        compact = compact,
                                        onClick = {
                                            val group = tracks.groups[opt.groupIndex].mediaTrackGroup
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setOverrideForType(
                                                    TrackSelectionOverride(group, opt.trackIndex)
                                                )
                                                .build()
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                        2 -> {
                            if (subtitleOptions.isEmpty()) {
                                item {
                                    InteractiveTrackCard(
                                        title = "لا توجد ترجمات في هذا البث",
                                        subtitle = "يظهر تبويب الترجمة عند توفر مسارات SRT/VTT/ASS من السيرفر داخل الفيلم أو المسلسل",
                                        isSelected = true,
                                        compact = compact,
                                        onClick = onDismiss
                                    )
                                }
                            } else {
                                itemsIndexed(subtitleOptions) { _, opt ->
                                    InteractiveTrackCard(
                                        title = opt.title,
                                        subtitle = opt.subtitle,
                                        isSelected = opt.isSelected,
                                        compact = compact,
                                        onClick = {
                                            if (opt.isDisableOption || opt.groupIndex < 0) {
                                                // Disable all text tracks
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                    .build()
                                            } else {
                                                val group = tracks.groups[opt.groupIndex].mediaTrackGroup
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(group, opt.trackIndex)
                                                    )
                                                    .build()
                                            }
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                        else -> {
                            itemsIndexed(PlaybackSpeedOption.values().toList()) { _, spd ->
                                InteractiveTrackCard(
                                    title = "سرعة التشغيل: ${spd.labelAr}",
                                    subtitle = if (spd == PlaybackSpeedOption.NORMAL) {
                                        "السرعة المثالية لاستقرار القنوات المباشرة"
                                    } else {
                                        "تغيير سرعة عرض الفيديو والصوت"
                                    },
                                    isSelected = currentSpeed == spd,
                                    compact = compact,
                                    onClick = {
                                        onSelectSpeed(spd)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveModalTabButton(
    title: String,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(if (compact) 34.dp else 42.dp)
            .scale(if (focused) 1.05f else 1.0f)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    ev.nativeKeyEvent.repeatCount == 0 &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    SafeClickGuard.runThrottled("PLAYER_UI_CLICK", 200L) { onClick() }
                    true
                } else false
            }
            .focusable()
            .background(
                when {
                    focused -> Color.White
                    selected -> BeinPrimaryPurple
                    else -> Color(0xFF16161D)
                }
            )
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) BeinFocusGlow else if (selected) Color.White else Color(0xFF2C2C38),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (focused) Color.Black else Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 9.sp else 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InteractiveTrackCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (focused) 1.02f else 1.0f)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    ev.nativeKeyEvent.repeatCount == 0 &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    SafeClickGuard.runThrottled("PLAYER_UI_CLICK", 200L) { onClick() }
                    true
                } else false
            }
            .focusable()
            .background(
                when {
                    focused -> Color.White
                    isSelected -> BeinPrimaryPurple.copy(alpha = 0.55f)
                    else -> Color(0xFF16161D)
                }
            )
            .border(
                width = if (focused) 2.8.dp else if (isSelected) 1.5.dp else 1.dp,
                color = when {
                    focused -> BeinFocusGlow
                    isSelected -> Color.White
                    else -> Color(0xFF2C2C38)
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(
                horizontal = if (compact) 12.dp else 16.dp,
                vertical = if (compact) 8.dp else 11.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (focused) Color.Black else Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (compact) 11.sp else 13.sp
            )
            Text(
                text = subtitle,
                color = if (focused) Color.DarkGray else Color.LightGray,
                fontSize = if (compact) 9.sp else 11.sp
            )
        }

        Box(
            modifier = Modifier
                .size(if (compact) 20.dp else 24.dp)
                .clip(CircleShape)
                .background(if (isSelected) EmeraldLive else Color(0xFF24242E))
                .border(1.dp, if (focused) Color.Black else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "محدد",
                    tint = Color.Black,
                    modifier = Modifier.size(if (compact) 13.dp else 15.dp)
                )
            }
        }
    }
}

/**
 * Embedded Live Mini Preview Video Player (Green Frame in 3-Column Live TV Browser):
 * - Runs ExoPlayer with instant 0ms failover & 900ms frozen-frame handover to ExoFfmpegUniversalEngine.
 * - Automatically releases its connection the moment the user expands to FullScreen (PlayerScreen)
 *   so max_connections=1 IPTV accounts never experience connection conflicts!
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveMiniPreviewPlayer(
    channel: TvChannel,
    xtreamPlaylists: List<XtreamAccountConfig> = emptyList(),
    preferences: PlayerPreferences = PlayerPreferences(),
    networkSpeedLabel: String = "",
    compact: Boolean = false,
    onExpandFullScreen: () -> Unit
) {
    val context = LocalContext.current
    val vlcAvailable = true
    val forceMiniSoftwareCodec = remember {
        SharedLiveSessionBridge.forceSharedSoftwareVideoCodec.apply {
            set(preferences.preferSoftwareDecoder)
        }
    }
    val initialNeedsSw = remember(channel.stableKey, channel.url) {
        StreamFormatDetectorAndRenewer.isSoftwareVideoPreferredCodec(
            url = channel.url,
            videoCodecHint = channel.videoCodecHint,
            audioCodecHint = channel.audioCodecHint,
            containerExtension = channel.containerExtension,
            name = channel.name
        )
    }
    var codecRequiresVlc by remember(channel.stableKey) {
        mutableStateOf(false)
    }
    var forceVlcSoftwareVideo by remember(channel.stableKey) { mutableStateOf(initialNeedsSw) }
    LaunchedEffect(channel.stableKey, forceVlcSoftwareVideo, preferences.preferSoftwareDecoder) {
        forceMiniSoftwareCodec.set(forceVlcSoftwareVideo || preferences.preferSoftwareDecoder)
    }
    val miniMqVariants = remember(channel.stableKey) {
        if (channel.contentType == StreamContentType.LIVE_TV) {
            MultiQualityEngine.variantsForChannel(channel)
        } else emptyList()
    }
    val isMultiQualityMini = miniMqVariants.size >= 2 || channel.playlistId == MultiQualityEngine.PLAYLIST_ID
    var activeMiniVariant by remember(channel.stableKey) {
        mutableStateOf(
            if (isMultiQualityMini) {
                MultiQualityEngine.getActiveVariant(channel.stableKey)
                    ?: MultiQualityEngine.pickBestVariant(channel)
                    ?: miniMqVariants.firstOrNull()
            } else null
        )
    }
    var miniMqGeneration by remember(channel.stableKey) { mutableStateOf(0) }
    val activeMiniChannel = remember(channel, activeMiniVariant) {
        activeMiniVariant?.channel ?: channel
    }

    // Mini: no green spinner on open — show video surface immediately; spinner only if truly stuck
    var isBuffering by remember(channel.stableKey) { mutableStateOf(false) }
    var showMiniSpinner by remember(channel.stableKey) { mutableStateOf(false) }
    var isMiniSourceStopped by remember(channel.stableKey) { mutableStateOf(false) }
    var miniErrorSignal by remember(channel.stableKey) { mutableStateOf(0) }
    var candidateIndex by remember(channel.stableKey) { mutableStateOf(0) }
    var miniFormatAttemptMode by remember(channel.stableKey, candidateIndex) { mutableStateOf(0) }
    var retryCount by remember(channel.stableKey) { mutableStateOf(0) }
    var activeMiniSessionKey by remember { mutableStateOf("") }
    var activeMiniStreamUrl by remember { mutableStateOf("") }

    val matchingAccount = remember(activeMiniChannel.playlistId, xtreamPlaylists) {
        xtreamPlaylists.find { it.id == activeMiniChannel.playlistId }
    }
    val formatCandidateUrls = remember(channel.stableKey, activeMiniChannel, matchingAccount, miniMqGeneration) {
        activeMiniChannel.buildFormatCandidateUrls(matchingAccount).ifEmpty { listOf(activeMiniChannel.url) }
    }
    val effectiveUrl = remember(formatCandidateUrls, candidateIndex) {
        formatCandidateUrls.getOrElse(candidateIndex.coerceIn(0, (formatCandidateUrls.size - 1).coerceAtLeast(0))) {
            activeMiniChannel.url
        }
    }

    val httpFactory = remember {
        OkHttpDataSource.Factory(SharedLiveSessionBridge.getPlaybackOkHttpClient())
            .setUserAgent(STREAM_USER_AGENTS.first())
            .setFollowRedirects(true)
            .setFollowSslRedirects(true)
    }
    val miniUniversalDataSourceFactory = remember {
        ExoFfmpegUniversalEngine.buildUniversalDataSourceFactory(context, httpFactory)
    }
    val extractorsFactory = remember {
        ExoFfmpegUniversalEngine.buildUniversalExtractorsFactory(isLiveStream = true)
    }
    val failFastPolicy = remember {
        object : DefaultLoadErrorHandlingPolicy(5) {
            override fun getMinimumLoadableRetryCount(dataType: Int): Int = 5
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val code = (loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                if (code == 404 || code == 410) {
                    return if (loadErrorInfo.errorCount == 1) 450L else C.TIME_UNSET
                }
                if (code in setOf(401, 403, 429, 458, 460, 500, 502, 503, 504)) {
                    return when (loadErrorInfo.errorCount) {
                        1 -> 550L
                        2 -> 1_100L
                        3 -> 1_800L
                        4 -> 2_400L
                        5 -> 3_000L
                        else -> C.TIME_UNSET
                    }
                }
                return when (loadErrorInfo.errorCount) {
                    1 -> 300L
                    2 -> 800L
                    3 -> 1_600L
                    else -> C.TIME_UNSET
                }
            }
        }
    }
    val defaultMediaSourceFactory = remember {
        DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(miniUniversalDataSourceFactory)
            .setLoadErrorHandlingPolicy(failFastPolicy)
    }
    val fastHlsMediaSourceFactory = remember {
        HlsMediaSource.Factory(miniUniversalDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .setTimestampAdjusterInitializationTimeoutMs(0L)
            .setLoadErrorHandlingPolicy(failFastPolicy)
    }

    var attachedMiniPlayerView by remember { mutableStateOf<PlayerView?>(null) }

    val player = remember {
        val existingShared = SharedLiveSessionBridge.sharedPlayer
        if (existingShared != null) {
            existingShared
        } else {
            val isLiveStream = channel.contentType == StreamContentType.LIVE_TV
            val miniTargetBufferBytes = if (DevicePerformanceDetector.isLowRamOrWeakDevice(context)) {
                24 * 1024 * 1024
            } else {
                40 * 1024 * 1024
            }
            val loadControl = ExoFfmpegUniversalEngine.buildAdaptiveAntiStutterLoadControl(
                isLiveStream = isLiveStream,
                targetBufferBytes = miniTargetBufferBytes,
                allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                bandwidthMeter = SharedLiveSessionBridge.getHighQualityBandwidthMeter(context)
            )

            val renderersFactory = ExoFfmpegUniversalEngine.buildAllCodecRenderersFactory(
                context = context,
                forceSoftwareVideoProvider = { forceMiniSoftwareCodec.get() },
                preferFfmpegExtension = true
            ).apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                setEnableDecoderFallback(true)
            }
            val trackSelector = ExoFfmpegUniversalEngine.buildAllCodecTrackSelector(
                context = context,
                capHeightForWeakDevice = preferences.lowEndDeviceMode
            )
            ExoPlayer.Builder(context, renderersFactory)
                .setBandwidthMeter(SharedLiveSessionBridge.getHighQualityBandwidthMeter(context))
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(defaultMediaSourceFactory)
                .setLoadControl(loadControl)
                .setUseLazyPreparation(false)
                .setSeekParameters(SeekParameters.CLOSEST_SYNC)
                .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
                .also { created ->
                    SharedLiveSessionBridge.sharedPlayer = created
                }
        }
    }
    val miniBandwidthMeter = remember { SharedLiveSessionBridge.getHighQualityBandwidthMeter(context) }

    DisposableEffect(player, channel.stableKey) {
        var lastMiniAutoRestartMs = 0L
        var miniRapidRestartCount = 0
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    isBuffering = false
                    showMiniSpinner = false
                    isMiniSourceStopped = false
                    SharedLiveSessionBridge.enforceMiniPlayerFullBounds(attachedMiniPlayerView)
                    FastFormatPreferenceCache.recordWorkingLiveUrl(channel.playlistId, effectiveUrl)
                } else if (playbackState == Player.STATE_ENDED) {
                    isBuffering = false
                    showMiniSpinner = false
                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    if (nowMs - lastMiniAutoRestartMs < 3_500L) {
                        miniRapidRestartCount++
                    } else {
                        miniRapidRestartCount = 1
                    }
                    lastMiniAutoRestartMs = nowMs
                    if (miniRapidRestartCount >= 2) {
                        ChannelRepository.clearStreamRedirectCache(effectiveUrl)
                        runCatching {
                            player.playWhenReady = false
                            player.stop()
                            player.clearMediaItems()
                        }
                        isMiniSourceStopped = true
                    } else {
                        player.seekToDefaultPosition()
                        player.prepare()
                        player.play()
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                SharedLiveSessionBridge.enforceMiniPlayerFullBounds(attachedMiniPlayerView)
            }

            override fun onRenderedFirstFrame() {
                isBuffering = false
                showMiniSpinner = false
                isMiniSourceStopped = false
                SharedLiveSessionBridge.enforceMiniPlayerFullBounds(attachedMiniPlayerView)
            }

            override fun onTracksChanged(tracks: Tracks) {
                var hasVideoTrack = false
                var hasSupportedVideoTrack = false
                var firstVideoGroup: Tracks.Group? = null
                var anyVideoSelected = false
                var hasAudioTrack = false
                var hasSupportedAudioTrack = false
                var fallbackAudioGroup: Tracks.Group? = null
                var fallbackAudioIndex = -1
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        hasVideoTrack = true
                        if (firstVideoGroup == null && group.length > 0) firstVideoGroup = group
                        for (i in 0 until group.length) {
                            if (group.isTrackSupported(i, true) || group.getTrackSupport(i) == C.FORMAT_EXCEEDS_CAPABILITIES) {
                                hasSupportedVideoTrack = true
                            }
                            if (group.isTrackSelected(i)) anyVideoSelected = true
                        }
                    } else if (group.type == C.TRACK_TYPE_AUDIO) {
                        hasAudioTrack = true
                        for (i in 0 until group.length) {
                            if (group.isTrackSupported(i, false)) {
                                hasSupportedAudioTrack = true
                                if (fallbackAudioGroup == null) {
                                    fallbackAudioGroup = group
                                    fallbackAudioIndex = i
                                }
                            }
                        }
                    }
                }
                // Let DefaultTrackSelector and Media3 decoder fallback handle track selection natively
                // without mutating trackSelectionParameters or triggering retryCount loops inside onTracksChanged.
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                    return
                }
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    ChannelRepository.clearHostSessionCookies(effectiveUrl)
                    ChannelRepository.clearStreamRedirectCache(effectiveUrl)
                }
                val isContainerMismatch =
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
                // الالتزام الصارم بصيغة الرابط المقدمة من السيرفر دون تبديل عشوائي
                val isCodecError =
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                if (isCodecError && !forceMiniSoftwareCodec.get()) {
                    forceMiniSoftwareCodec.set(true)
                    forceVlcSoftwareVideo = true
                    codecRequiresVlc = true
                    retryCount += 1
                    return
                }
                if (isMultiQualityMini && miniMqVariants.size >= 2) {
                    val decision = MultiQualityEngine.reportAdaptiveEvent(
                        multiStableKey = channel.stableKey,
                        currentVariant = activeMiniVariant,
                        reason = AdaptationReason.SOURCE_OUTAGE
                    )
                    if (decision != null && decision.switched) {
                        activeMiniVariant = decision.variant
                        candidateIndex = 0
                        miniFormatAttemptMode = 0
                        miniMqGeneration += 1
                        return
                    }
                }
                // Never loop at 0ms inside onPlayerError (causes severe screen flicker on dead channels).
                // Signal the paced Zero-Flicker handler instead.
                miniErrorSignal += 1
            }
        }
        player.addListener(listener)
        onDispose {
            runCatching {
                player.removeListener(listener)
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            runCatching {
                SharedLiveSessionBridge.detachViewIfBound(player, attachedMiniPlayerView)
                if (!SharedLiveSessionBridge.handoverInProgress) {
                    if (SharedLiveSessionBridge.sharedPlayer !== player) {
                        SharedLiveSessionBridge.scheduleSafeRelease(player, null, "")
                    } else {
                        // Mini player left composition without fullscreen handover -> silence & stop immediately so audio never leaks to Home/Dashboard!
                        player.volume = 0f
                        player.playWhenReady = false
                        player.stop()
                    }
                }
            }
        }
    }

    // Paced Zero-Flicker Error Handler for LiveMiniPreviewPlayer
    LaunchedEffect(channel.stableKey, miniErrorSignal) {
        if (miniErrorSignal <= 0 || isMiniSourceStopped) return@LaunchedEffect
        if (retryCount < 1 && formatCandidateUrls.size > 1 && candidateIndex + 1 < formatCandidateUrls.size) {
            delay(850L)
            candidateIndex += 1
            miniFormatAttemptMode = 0
            retryCount += 1
        } else {
            // Channel is down: stop ExoPlayer completely (ZERO flicker!) and hand over to silent background probe
            runCatching {
                player.playWhenReady = false
                player.stop()
                player.clearMediaItems()
            }
            isBuffering = false
            showMiniSpinner = false
            isMiniSourceStopped = true
        }
    }

    // Silent Background Probe for LiveMiniPreviewPlayer (Zero-Flicker)
    LaunchedEffect(channel.stableKey, isMiniSourceStopped) {
        if (!isMiniSourceStopped) return@LaunchedEffect
        val probeUa = activeMiniChannel.userAgent?.takeIf { it.isNotBlank() }
            ?: channel.userAgent?.takeIf { it.isNotBlank() }
            ?: STREAM_USER_AGENTS.first()
        while (isMiniSourceStopped) {
            delay(6_000L)
            if (!isMiniSourceStopped) break
            val candidatesToProbe = formatCandidateUrls.ifEmpty { listOf(effectiveUrl) }
            var aliveIdx = -1
            for ((idx, candUrl) in candidatesToProbe.withIndex()) {
                if (StreamFormatDetectorAndRenewer.probeStreamIsAliveSilently(activeMiniChannel, candUrl, probeUa)) {
                    aliveIdx = idx
                    break
                }
            }
            if (aliveIdx >= 0 && isMiniSourceStopped) {
                isMiniSourceStopped = false
                miniErrorSignal = 0
                candidateIndex = aliveIdx
                miniFormatAttemptMode = 0
                retryCount += 1
                break
            }
        }
    }

    LaunchedEffect(
        player,
        channel.stableKey,
        activeMiniChannel.url,
        effectiveUrl,
        miniFormatAttemptMode,
        miniMqGeneration,
        codecRequiresVlc,
        forceVlcSoftwareVideo,
        retryCount
    ) {
        if (isMiniSourceStopped) return@LaunchedEffect
        if (retryCount == 0 && SharedLiveSessionBridge.canReuseSeamlessSession(player, channel.stableKey, effectiveUrl)) {
            SharedLiveSessionBridge.handoverInProgress = false
            activeMiniSessionKey = channel.stableKey
            activeMiniStreamUrl = effectiveUrl
            isMiniSourceStopped = false
            isBuffering = (player.playbackState == Player.STATE_BUFFERING)
            showMiniSpinner = false
            runCatching {
                attachedMiniPlayerView?.let { pv ->
                    SharedLiveSessionBridge.attachViewSafely(player, pv)
                    SharedLiveSessionBridge.enforceMiniPlayerFullBounds(pv)
                }
                player.volume = 1.0f
                player.playWhenReady = true
                if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                    player.play()
                }
            }
            return@LaunchedEffect
        }
        SharedLiveSessionBridge.handoverInProgress = false
        val isMiniSwitch = activeMiniSessionKey.isNotEmpty() && activeMiniSessionKey != channel.stableKey
        val prevMiniUrl = activeMiniStreamUrl
        activeMiniSessionKey = channel.stableKey
        activeMiniStreamUrl = effectiveUrl
        if (isMiniSwitch) {
            isMiniSourceStopped = false
        }
        isBuffering = true
        showMiniSpinner = false
        if (retryCount > 0) {
            delay(300L)
        }
        runCatching {
            player.stop()
            player.clearMediaItems()
            player.volume = 1.0f
            if (player.trackSelectionParameters.overrides.isNotEmpty()) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverrides()
                    .build()
            }
        }

        val rotatedUa = activeMiniChannel.userAgent?.takeIf { it.isNotBlank() }
            ?: channel.userAgent?.takeIf { it.isNotBlank() }
            ?: STREAM_USER_AGENTS[retryCount % STREAM_USER_AGENTS.size]
        val rawPlayUrl = effectiveUrl.trim()
        val targetPlayUrl = rawPlayUrl.substringBefore("|").trim()
        val low = targetPlayUrl.lowercase(Locale.ROOT).substringBefore("?")
        val isExplicitHlsUrl = low.endsWith(".m3u8") ||
            targetPlayUrl.contains("output=m3u8", ignoreCase = true) ||
            targetPlayUrl.contains("type=m3u8", ignoreCase = true) ||
            targetPlayUrl.contains("output=hls", ignoreCase = true)

        forceMiniSoftwareCodec.set(codecRequiresVlc || forceVlcSoftwareVideo || preferences.preferSoftwareDecoder)

        val customHeaders = IboPlayerProfileSpoofer.buildStreamHeadersMap(
            targetUrl = targetPlayUrl,
            serverBaseUrl = activeMiniChannel.streamBaseUrl.ifBlank { channel.streamBaseUrl },
            customReferer = activeMiniChannel.referer ?: channel.referer,
            customOrigin = activeMiniChannel.origin ?: channel.origin,
            customCookie = activeMiniChannel.cookie ?: channel.cookie,
            isLiveManifest = (isExplicitHlsUrl && miniFormatAttemptMode == 0) || miniFormatAttemptMode == 1
        )
        httpFactory.setDefaultRequestProperties(customHeaders)
        httpFactory.setUserAgent(rotatedUa)

        val itemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(targetPlayUrl))
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMinPlaybackSpeed(1.0f)
                    .setMaxPlaybackSpeed(1.0f)
                    .build()
            )
        if (isExplicitHlsUrl) {
            itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            player.setMediaSource(fastHlsMediaSourceFactory.createMediaSource(itemBuilder.build()))
        } else {
            player.setMediaSource(defaultMediaSourceFactory.createMediaSource(itemBuilder.build()))
        }
        SharedLiveSessionBridge.recordActiveSession(player, channel.stableKey, effectiveUrl)
        player.playWhenReady = true
        player.prepare()
    }

    // 4K-Aware Mini Player Watchdog with Real-Time Multi-Quality Upgrade & Instant Recovery (Never stops player onto black screen!)
    LaunchedEffect(player, channel.stableKey, activeMiniChannel.url, miniMqGeneration, candidateIndex, miniFormatAttemptMode, effectiveUrl, retryCount) {
        var lastRenderedFrames = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
        var lastPosMs = player.currentPosition
        var lastBufMs = player.bufferedPosition
        var wasReadyMini = false
        var smoothReadyTicksMini = 0
        var midRebufferTicksMini = 0
        var frozenTicks = 0
        var bufferingTicks = 0
        while (true) {
            delay(250L)
            if (isMiniSourceStopped) continue
            NetworkSpeedMonitor.updateExoBandwidthEstimate(miniBandwidthMeter.bitrateEstimate)
            val state = player.playbackState
            val renderedFrames = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
            val frameDelta = (renderedFrames - lastRenderedFrames).coerceAtLeast(0)
            val currentPos = player.currentPosition
            val bufferedPos = player.bufferedPosition
            val bufferAheadMs = (bufferedPos - currentPos).coerceAtLeast(0L)
            val posDelta = currentPos - lastPosMs
            val bufDelta = bufferedPos - lastBufMs
            lastRenderedFrames = renderedFrames
            lastPosMs = currentPos
            lastBufMs = bufferedPos

            if (state == Player.STATE_READY) {
                bufferingTicks = 0
                midRebufferTicksMini = 0
                if (frameDelta > 0) {
                    wasReadyMini = true
                    smoothReadyTicksMini++
                    frozenTicks = 0
                    if (isMiniSourceStopped) isMiniSourceStopped = false
                }
                if (isMultiQualityMini && miniMqVariants.size >= 2) {
                    val curVar = activeMiniVariant
                    if (curVar != null && frameDelta > 0 && renderedFrames >= 20) {
                        MultiQualityEngine.markStablePlayback(channel.stableKey, curVar)
                    }
                    val upTicks = if (curVar?.quality == StreamQualityRank.FHD) 36 else 25
                    if (curVar != null && smoothReadyTicksMini >= upTicks) {
                        smoothReadyTicksMini = 0
                        val upDec = MultiQualityEngine.tryProactiveUpgrade(
                            multiStableKey = channel.stableKey,
                            currentVariant = curVar,
                            bufferAheadMs = bufferAheadMs
                        )
                        if (upDec != null && upDec.switched) {
                            activeMiniVariant = upDec.variant
                            candidateIndex = 0
                            miniFormatAttemptMode = 0
                            miniMqGeneration += 1
                            break
                        }
                    }
                }
                if (frameDelta == 0 && posDelta > 40L) {
                    frozenTicks++
                    if (frozenTicks == 5) {
                        SharedLiveSessionBridge.nudgeVideoSurfaceAndRenderer(player, attachedMiniPlayerView)
                    }
                    val fLimit = 14
                    if (frozenTicks >= fLimit) {
                        frozenTicks = 0
                        if (isMultiQualityMini && miniMqVariants.size >= 2) {
                            val dec = MultiQualityEngine.reportAdaptiveEvent(
                                multiStableKey = channel.stableKey,
                                currentVariant = activeMiniVariant,
                                reason = AdaptationReason.SOURCE_CONGESTION
                            )
                            if (dec != null && dec.switched) {
                                activeMiniVariant = dec.variant
                                candidateIndex = 0
                                miniFormatAttemptMode = 0
                                miniMqGeneration += 1
                                break
                            }
                        }
                        if (formatCandidateUrls.size > 1 && candidateIndex < formatCandidateUrls.size - 1) {
                            candidateIndex++
                            miniFormatAttemptMode = 0
                            break
                        }
                    }
                }
            } else if (state == Player.STATE_BUFFERING) {
                frozenTicks = 0
                smoothReadyTicksMini = 0
                if (wasReadyMini && isMultiQualityMini && miniMqVariants.size >= 2) {
                    midRebufferTicksMini++
                    if (midRebufferTicksMini >= 3) {
                        val dec = MultiQualityEngine.reportAdaptiveEvent(
                            multiStableKey = channel.stableKey,
                            currentVariant = activeMiniVariant,
                            reason = AdaptationReason.INTERNET_FLUCTUATION
                        )
                        if (dec != null && dec.switched) {
                            activeMiniVariant = dec.variant
                            candidateIndex = 0
                            miniFormatAttemptMode = 0
                            midRebufferTicksMini = 0
                            miniMqGeneration += 1
                            break
                        }
                    }
                }
                if (renderedFrames == 0 && posDelta <= 0L && bufDelta <= 0L) {
                    bufferingTicks++
                    // Give slow-GOP / 302-redirected live channels ~6.0s (30 * 200ms) before format failover so they never flicker mid-launch
                    val stallLimit = 30
                    if (bufferingTicks >= stallLimit) {
                        bufferingTicks = 0
                        if (isMultiQualityMini && miniMqVariants.size >= 2) {
                            val decision = MultiQualityEngine.reportAdaptiveEvent(
                                multiStableKey = channel.stableKey,
                                currentVariant = activeMiniVariant,
                                reason = AdaptationReason.SOURCE_CONGESTION
                            )
                            if (decision != null && decision.switched) {
                                activeMiniVariant = decision.variant
                                candidateIndex = 0
                                miniFormatAttemptMode = 0
                                miniMqGeneration += 1
                                break
                            }
                        }
                        if (formatCandidateUrls.size > 1 && candidateIndex < formatCandidateUrls.size - 1) {
                            candidateIndex++
                            miniFormatAttemptMode = 0
                            break
                        } else {
                            if (retryCount >= 2) {
                                isMiniSourceStopped = true
                            }
                            retryCount++
                            break
                        }
                    }
                } else {
                    bufferingTicks = 0
                }
            } else if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                bufferingTicks++
                if (bufferingTicks >= 20) {
                    bufferingTicks = 0
                    if (retryCount >= 2) {
                        isMiniSourceStopped = true
                    }
                    candidateIndex = (candidateIndex + 1) % formatCandidateUrls.size.coerceAtLeast(1)
                    miniFormatAttemptMode = 0
                    retryCount++
                    break
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onExpandFullScreen() }
    ) {
        val rememberedMiniView = remember(player) {
            (android.view.LayoutInflater.from(context).inflate(R.layout.exo_mini_texture_player_view, null, false) as PlayerView).apply {
                this.useController = false
                this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                this.setKeepContentOnPlayerReset(true)
                this.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.keepScreenOn = true
                this.isFocusable = false
                this.isFocusableInTouchMode = false
                this.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                SharedLiveSessionBridge.enforceMiniPlayerFullBounds(this)
                this.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                        SharedLiveSessionBridge.enforceMiniPlayerFullBounds(v as? PlayerView)
                    }
                }
            }
        }
        DisposableEffect(rememberedMiniView) {
            attachedMiniPlayerView = rememberedMiniView
            SharedLiveSessionBridge.enforceMiniPlayerFullBounds(rememberedMiniView)
            SharedLiveSessionBridge.attachViewSafely(player, rememberedMiniView)
            onDispose {
                SharedLiveSessionBridge.detachViewIfBound(player, rememberedMiniView)
            }
        }
        AndroidView(
            factory = {
                (rememberedMiniView.parent as? android.view.ViewGroup)?.removeView(rememberedMiniView)
                SharedLiveSessionBridge.enforceMiniPlayerFullBounds(rememberedMiniView)
                rememberedMiniView
            },
            update = { view ->
                attachedMiniPlayerView = view
                SharedLiveSessionBridge.enforceMiniPlayerFullBounds(view)
                if (view.player !== player && !SharedLiveSessionBridge.isSurfaceSwitching.get()) {
                    SharedLiveSessionBridge.attachViewSafely(player, view)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top overlay badges on Mini Player
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xCC000000), Color.Transparent)
                    )
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⛶ اضغط OK لتكبير الشاشة",
                color = Color.White,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .background(Color(0xCC14141D), RoundedCornerShape(6.dp))
                    .border(1.dp, EmeraldLive.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            if (networkSpeedLabel.isNotBlank()) {
                Text(
                    text = "⬇ $networkSpeedLabel",
                    color = EmeraldLive,
                    fontSize = if (compact) 10.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .background(Color(0xCC0D0D14), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }

        // Delayed spinner only: if still buffering after 2.2s with no frames, show a discreet indicator.
        // Never keep a permanent green wheel on mini preview during normal channel zapping.
        LaunchedEffect(isBuffering, channel.stableKey, candidateIndex, miniMqGeneration) {
            if (!isBuffering) {
                showMiniSpinner = false
                return@LaunchedEffect
            }
            showMiniSpinner = false
            delay(2200L)
            if (isBuffering) showMiniSpinner = true
        }
        if (isMiniSourceStopped) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xD90A0A10), RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = EmeraldLive,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(if (compact) 22.dp else 26.dp)
                    )
                    Text(
                        text = "📡 متوقفة من المصدر • فحص صامت...",
                        color = Color.White,
                        fontSize = if (compact) 11.sp else 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (showMiniSpinner && isBuffering) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.75f),
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(if (compact) 22.dp else 26.dp)
                    .align(Alignment.Center)
            )
        }
    }
}
