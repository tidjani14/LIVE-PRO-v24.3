package com.tvflux.pro

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Lightweight real-callback playback telemetry; it never changes playback decisions. */
object PlaybackMetrics {
    private data class Session(
        val name: String,
        val startedAt: Long,
        var firstFrameAt: Long = 0L,
        var bufferStartedAt: Long = 0L,
        var totalBufferMs: Long = 0L,
        var rebufferCount: Int = 0,
        var hadFirstFrame: Boolean = false,
        var width: Int = 0,
        var height: Int = 0,
        var videoCodec: String = "",
        var audioCodec: String = "",
        var videoSupported: Boolean = false,
        var audioSupported: Boolean = false,
        var lastReportAt: Long = 0L,
        var lastHeartbeatAt: Long = 0L,
        var playingMs: Long = 0L
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    @Synchronized
    fun start(key: String, name: String) {
        val now = SystemClock.elapsedRealtime()
        sessions[key] = Session(name, now, lastReportAt = now, lastHeartbeatAt = now)
        Log.i("LIVEPRO_METRICS", "START channel=$name")
    }

    @Synchronized
    fun onState(key: String, state: Int) {
        val s = sessions[key] ?: return
        val now = SystemClock.elapsedRealtime()
        if (state == Player.STATE_BUFFERING) {
            if (s.bufferStartedAt == 0L) {
                s.bufferStartedAt = now
                if (s.hadFirstFrame) s.rebufferCount++
            }
        } else if (state == Player.STATE_READY && s.bufferStartedAt != 0L) {
            s.totalBufferMs += (now - s.bufferStartedAt).coerceAtLeast(0L)
            s.bufferStartedAt = 0L
        }
    }

    @Synchronized
    fun onFirstFrame(key: String) {
        val s = sessions[key] ?: return
        val now = SystemClock.elapsedRealtime()
        if (s.firstFrameAt == 0L) {
            s.firstFrameAt = now
            Log.i("LIVEPRO_METRICS", "TTFF channel=${s.name} ms=${now - s.startedAt}")
        }
        s.hadFirstFrame = true
        if (s.bufferStartedAt != 0L) {
            s.totalBufferMs += (now - s.bufferStartedAt).coerceAtLeast(0L)
            s.bufferStartedAt = 0L
        }
    }

    @Synchronized
    fun onVideoSize(key: String, width: Int, height: Int) {
        val s = sessions[key] ?: return
        s.width = width
        s.height = height
        val quality = when {
            height >= 2160 || width >= 3840 -> "4K"
            height >= 1080 || width >= 1920 -> "1080p"
            height >= 720 || width >= 1280 -> "720p"
            else -> "SD/other"
        }
        Log.i("LIVEPRO_METRICS", "VIDEO channel=${s.name} size=${width}x$height quality=$quality")
    }

    @Synchronized
    fun onCodecInfo(key: String, video: String, audio: String, videoSupported: Boolean, audioSupported: Boolean) {
        val s = sessions[key] ?: return
        val changed = s.videoCodec != video || s.audioCodec != audio ||
            s.videoSupported != videoSupported || s.audioSupported != audioSupported
        s.videoCodec = video
        s.audioCodec = audio
        s.videoSupported = videoSupported
        s.audioSupported = audioSupported
        if (changed) {
            Log.i("LIVEPRO_METRICS", "CODECS channel=${s.name} video=${video.ifBlank { "unknown" }} " +
                "audio=${audio.ifBlank { "unknown" }} videoSupported=$videoSupported audioSupported=$audioSupported")
        }
    }

    @Synchronized
    fun heartbeat(key: String, isPlaying: Boolean, @Suppress("UNUSED_PARAMETER") positionMs: Long) {
        val s = sessions[key] ?: return
        val now = SystemClock.elapsedRealtime()
        val delta = (now - s.lastHeartbeatAt).coerceAtLeast(0L)
        if (isPlaying) s.playingMs += delta
        s.lastHeartbeatAt = now
        if (now - s.lastReportAt >= 60_000L) {
            report(key, s, now, "MINUTE")
            s.lastReportAt = now
        }
    }

    @Synchronized
    fun finish(key: String) {
        val s = sessions.remove(key) ?: return
        val now = SystemClock.elapsedRealtime()
        report(key, s, now, "END")
    }

    private fun report(key: String, s: Session, now: Long, kind: String) {
        val elapsed = (now - s.startedAt).coerceAtLeast(1L)
        val buffering = s.totalBufferMs +
            if (s.bufferStartedAt != 0L) (now - s.bufferStartedAt).coerceAtLeast(0L) else 0L
        val ratio = buffering * 100.0 / elapsed.toDouble()
        val ttff = if (s.firstFrameAt == 0L) -1L else s.firstFrameAt - s.startedAt
        Log.i("LIVEPRO_METRICS", "$kind channel=${s.name} key=$key elapsedSec=${elapsed / 1000L} " +
            "ttffMs=$ttff rebufferCount=${s.rebufferCount} bufferingMs=$buffering " +
            "bufferRatioPct=${String.format(Locale.US, "%.2f", ratio)} " +
            "resolution=${s.width}x${s.height} videoCodec=${s.videoCodec} audioCodec=${s.audioCodec} " +
            "videoSupported=${s.videoSupported} audioSupported=${s.audioSupported} " +
            "playingSec=${s.playingMs / 1000L}")
    }
}
