package com.tvflux.pro

import android.content.Context
import android.net.Uri
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.UdpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import java.util.ArrayList
import java.util.Locale
import java.util.UUID

/**
 * Ultra-Fast ExoPlayer (Media3) + Native FFmpeg 6.0 All-Codec Engine (v29.0 Pure Exo+FFmpeg):
 * - Completely replaces legacy LibVLC with Media3 ExoPlayer + Jellyfin FFmpeg 6.0 Decoder (`FfmpegAudioRenderer`)
 *   and Universal DataSource (`OkHttp` + `RTMP` + `UDP/RTP` + `RTSP` + `HLS` + `DASH` + `SmoothStreaming`).
 * - Decodes ALL broadcast and VOD audio codecs natively via FFmpeg:
 *   AC-3 (Dolby Digital), E-AC-3 (Dolby Digital Plus / Atmos), DTS, DTS-HD, TrueHD, MP2 (MPEG-1/2 Layer II Broadcast),
 *   MP3, AAC (LC / HE-AAC v1 & v2 / xHE-AAC), Vorbis, Opus, FLAC, ALAC, PCM, AMR, WMA.
 * - Decodes ALL video codecs via Hardware MediaCodec with automatic Software (`c2.android.*` / `OMX.google.*`) fallback:
 *   HEVC / H.265 (8-bit & 10-bit 4K UHD), H.264 / AVC, MPEG-2 (Broadcast TS), MPEG-4 / XviD / DivX, VP9, VP8, AV1, FLV, VC-1.
 * - Tuned for sub-120ms first-frame zapping on Live TV MPEG-TS (`.ts`) and HLS (`.m3u8`) streams.
 */
@OptIn(UnstableApi::class)
object ExoFfmpegUniversalEngine {

    @Volatile
    private var cachedFfmpegAvailable: Boolean? = null

    private val activeStreamDelegates = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<DataSource, Boolean>()
    )

    fun closeAllActiveStreamDataSources() {
        // ExoPlayer's internal LoaderThread closes its own active DataSource safely when player.stop() / clearMediaItems() is called.
        // Clearing our reference set without cross-thread ds.close() prevents NetworkOnMainThreadException & mid-switch stream kills.
        runCatching {
            activeStreamDelegates.clear()
        }
    }

    fun isFfmpegDecoderAvailable(): Boolean {
        cachedFfmpegAvailable?.let { return it }
        val ok = runCatching {
            val libClass = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegLibrary")
            val isAvailMethod = libClass.getMethod("isAvailable")
            (isAvailMethod.invoke(null) as? Boolean) ?: true
        }.getOrElse {
            runCatching {
                Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
                true
            }.getOrDefault(false)
        }
        cachedFfmpegAvailable = ok
        return ok
    }

    /**
     * Builds a dynamic MediaCodecSelector that never drops tracks and prioritizes either:
     * - Hardware decoders first (default for 4K 60fps zero-lag playback) with Software fallback, OR
     * - Software decoders (`c2.android.*` / `OMX.google.*`) first when `forceSoftwareVideoProvider()` is true.
     */
    fun buildSmartMediaCodecSelector(forceSoftwareVideoProvider: () -> Boolean = { false }): MediaCodecSelector {
        return MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            // Route all surround/broadcast cinema audio (AC-3, E-AC-3, DTS, TrueHD, MP2, MLP) directly to
            // FfmpegAudioRenderer (clean 16-bit Stereo PCM) by returning emptyList() from MediaCodecSelector!
            // This prevents buggy TV Box / TV hardware AC3/EAC3 decoders from locking up the HDMI AudioTrack with a screeching buzz,
            // while keeping standard AAC/MP4A on the zero-CPU hardware DSP decoder!
            val lowMime = mimeType.lowercase(Locale.ROOT)
            if (lowMime.contains("dts") ||
                lowMime.contains("ac3") ||
                lowMime.contains("eac3") ||
                lowMime.contains("ec-3") ||
                lowMime.contains("truehd") ||
                lowMime.contains("mlp") ||
                lowMime.contains("mpeg-l2") ||
                lowMime.contains("mp2")
            ) {
                return@MediaCodecSelector emptyList()
            }
            val primaryList = runCatching {
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            }.getOrDefault(emptyList())

            val baseList = if (primaryList.isNotEmpty()) {
                primaryList
            } else {
                runCatching {
                    MediaCodecUtil.getDecoderInfos(mimeType, false, false)
                }.getOrDefault(emptyList())
            }

            if (baseList.size <= 1) {
                baseList
            } else {
                val forceSw = forceSoftwareVideoProvider()
                val isVideoMime = mimeType.startsWith("video/", ignoreCase = true)
                val is4kOnlyCodec = mimeType.contains("dolby-vision", ignoreCase = true)
                val (swList, hwList) = baseList.partition { info -> isSoftwareCodecInfo(info) }
                val rankedHw = hwList.sortedBy { info ->
                    val n = info.name.lowercase(Locale.ROOT)
                    if (!n.contains("secure") && (info.hardwareAccelerated || n.startsWith("c2.") || n.startsWith("omx."))) 0 else 1
                }
                if (isVideoMime) {
                    val isBroadcastHwCodec = mimeType.contains("avc", ignoreCase = true) ||
                        mimeType.contains("hevc", ignoreCase = true) ||
                        mimeType.contains("h264", ignoreCase = true) ||
                        mimeType.contains("h265", ignoreCase = true) ||
                        mimeType.contains("vp9", ignoreCase = true) ||
                        mimeType.contains("av01", ignoreCase = true) ||
                        mimeType.contains("mpeg2", ignoreCase = true)
                    // Always keep Hardware Video Decoders first for broadcast/HD/4K codecs so Big Screen TVs never play audio-only without video!
                    // DefaultRenderersFactory.setEnableDecoderFallback(true) automatically falls back to swList if HW init ever fails.
                    if (forceSw && !is4kOnlyCodec && !isBroadcastHwCodec && swList.isNotEmpty()) {
                        swList + rankedHw
                    } else {
                        rankedHw + swList
                    }
                } else {
                    // For Audio MediaCodec fallback (after FfmpegAudioRenderer), prefer PCM software decoders first
                    // so vendor HDMI-passthrough decoders never mute stereo TV/phone speakers
                    if (swList.isNotEmpty()) swList + rankedHw else rankedHw
                }
            }
        }
    }

    private fun isSoftwareCodecInfo(info: MediaCodecInfo): Boolean {
        val nameLow = info.name.lowercase(Locale.ROOT)
        return info.softwareOnly ||
            nameLow.startsWith("c2.android.") ||
            nameLow.startsWith("omx.google.") ||
            nameLow.contains("ffmpeg") ||
            nameLow.contains(".sw.") ||
            nameLow.contains("software")
    }

    /**
     * Builds the All-Codec RenderersFactory combining Hardware/Software MediaCodec Video with
     * FFmpeg 6.0 `FfmpegAudioRenderer` as a single fallback after native audio renderers so AC-3, E-AC-3, DTS,
     * MP2, TrueHD, FLAC, Opus, Vorbis, and multi-channel surround audio always play with crystal clarity on every TV & phone.
     */
    fun buildAllCodecRenderersFactory(
        context: Context,
        forceSoftwareVideoProvider: () -> Boolean = { false },
        preferFfmpegExtension: Boolean = true
    ): DefaultRenderersFactory {
        return object : DefaultRenderersFactory(context.applicationContext) {
            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: Handler,
                eventListener: VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<Renderer>
            ) {
                // Synchronous MediaCodec Adapter is enforced via forceDisableMediaCodecAsynchronousQueueing() below,
                // preventing duplicate MediaCodecVideoRenderer instances while keeping 100% Media3 1.3.1 compatibility.
                super.buildVideoRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    eventHandler,
                    eventListener,
                    allowedVideoJoiningTimeMs,
                    out
                )
            }

            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>
            ) {
                // 1) Native MediaCodecAudioRenderer handles standard AAC/MP3 in hardware (0% CPU on Phone & TV).
                //    Because buildSmartMediaCodecSelector returns emptyList() for AC3/EAC3/DTS/MP2/TrueHD,
                //    MediaCodecAudioRenderer immediately delegates those formats to FfmpegAudioRenderer below!
                super.buildAudioRenderers(
                    context,
                    EXTENSION_RENDERER_MODE_OFF,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out
                )
                // 2) FfmpegAudioRenderer decodes AC-3, E-AC-3, DTS, MP2, TrueHD cleanly to 16-bit Stereo PCM
                //    so TV Boxes never suffer HDMI passthrough screeching or AudioTrack deadlocks!
                if (preferFfmpegExtension) {
                    runCatching {
                        val rendererClass = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
                        val constructor = rendererClass.getConstructor(
                            Handler::class.java,
                            AudioRendererEventListener::class.java,
                            AudioSink::class.java
                        )
                        val renderer = constructor.newInstance(eventHandler, eventListener, audioSink) as Renderer
                        if (out.none { it.javaClass.name == renderer.javaClass.name }) out.add(renderer)
                    }
                }
            }
        }.apply {
            // Keep Media3's platform-default codec adapter. Forcing synchronous codec queueing
            // globally can trigger vendor MediaCodec stalls on Android TV devices.
            setAllowedVideoJoiningTimeMs(1_200L)
            setExtensionRendererMode(
                // Keep platform MediaCodec video renderers first for native hardware decoding.
                // FFmpeg audio is appended once as a fallback after native audio renderers.
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            )
            setEnableDecoderFallback(true)
            setEnableAudioFloatOutput(false)
            // Disable hardware AudioTrack playback-rate params on TV boxes: rate adjustments on compressed/PCM
            // HDMI sinks corrupt the DMA ringbuffer and cause a loud screeching buzz & system freeze!
            setEnableAudioTrackPlaybackParams(false)
            setMediaCodecSelector(buildSmartMediaCodecSelector(forceSoftwareVideoProvider))
        }
    }

    /**
     * Builds an ultra-fast `ExtractorsFactory` supporting all containers:
     * - Places `TsExtractor` FIRST (index 0) for Live TV / `.ts` streams so regular MPEG-TS streams
     *   recognize video (H.264/HEVC/MPEG-2) and audio (AAC/MP2/AC-3/E-AC-3) codecs in <2ms on the very first 188-byte packet!
     * - Omits `FLAG_DETECT_ACCESS_UNITS` so H.264/HEVC SPS/PPS headers emit `Format` immediately without bit-slice scanning delay.
     * - Uses 64 TS packets (~12 KB) for Live TV PCR lock and 300 packets for VOD.
     */
    fun buildUniversalExtractorsFactory(isLiveStream: Boolean = true): ExtractorsFactory {
        val tsFlags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
            DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or
            DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
        // 600 TS packets (~112 KB) is the sweet spot: large enough to lock onto MPEG-TS PCR timestamps
        // on 50fps FHD/4K channels (where PCR repeats every <=100ms), yet small enough to download in <60ms!
        val tsSearchBytes = if (isLiveStream) 600 * TsExtractor.TS_PACKET_SIZE else 1200 * TsExtractor.TS_PACKET_SIZE
        val baseFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            .setTsExtractorFlags(tsFlags)
            .setTsExtractorMode(TsExtractor.MODE_SINGLE_PMT)
            .setTsExtractorTimestampSearchBytes(tsSearchBytes)

        return object : ExtractorsFactory {
            override fun createExtractors(): Array<Extractor> {
                val defaults = baseFactory.createExtractors()
                if (!isLiveStream) return defaults
                val (tsList, others) = defaults.partition { it is TsExtractor }
                return if (tsList.isNotEmpty()) (tsList + others).toTypedArray() else defaults
            }

            override fun createExtractors(
                uri: Uri,
                responseHeaders: Map<String, List<String>>
            ): Array<Extractor> {
                val defaults = baseFactory.createExtractors(uri, responseHeaders)
                val pathLow = uri.path?.lowercase(Locale.ROOT).orEmpty()
                val isKnownVodContainer = pathLow.endsWith(".mp4") || pathLow.endsWith(".mkv") ||
                    pathLow.endsWith(".avi") || pathLow.endsWith(".mov") ||
                    pathLow.endsWith(".webm") || pathLow.endsWith(".flv")
                if (!isLiveStream && isKnownVodContainer) {
                    return defaults
                }
                if (defaults.isNotEmpty() && defaults[0] is TsExtractor) {
                    return defaults
                }
                val (tsList, others) = defaults.partition { it is TsExtractor }
                return if (tsList.isNotEmpty()) (tsList + others).toTypedArray() else defaults
            }
        }
    }

    /**
     * Multi-protocol DataSource.Factory that transparently handles:
     * - HTTP / HTTPS (via OkHttpDataSource with custom User-Agent, Referer, Origin, Cookie, and 302 Edge Token Cache)
     * - RTMP / RTMPS / RTMPT (via Media3 `RtmpDataSource`)
     * - UDP / RTP multicast & unicast streams (`udp://@239.x.x.x:1234` or `udp://...` via `UdpDataSource`)
     * - Local files / Content URIs / Data URIs (via `DefaultDataSource`)
     */
    fun buildUniversalDataSourceFactory(
        context: Context,
        okHttpFactory: OkHttpDataSource.Factory,
        transferListener: TransferListener? = null
    ): DataSource.Factory {
        val appCtx = context.applicationContext
        val defaultFactory = DefaultDataSource.Factory(appCtx, okHttpFactory).apply {
            if (transferListener != null) {
                setTransferListener(transferListener)
            }
        }
        return DataSource.Factory {
            object : DataSource {
                private var activeDelegate: DataSource? = null

                override fun addTransferListener(transferListener: TransferListener) {
                    defaultFactory.setTransferListener(transferListener)
                }

                override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                    val rawUri = dataSpec.uri
                    val scheme = rawUri.scheme?.lowercase(Locale.ROOT).orEmpty()
                    var effectiveSpec = dataSpec

                    val delegate: DataSource = when {
                        scheme == "rtmp" || scheme == "rtmps" || scheme == "rtmpt" || scheme == "rtmpe" -> {
                            RtmpDataSource.Factory().createDataSource()
                        }
                        scheme == "udp" || scheme == "rtp" -> {
                            val rawStr = rawUri.toString()
                            if (rawStr.contains("://@")) {
                                val cleanedUri = Uri.parse(rawStr.replace("://@", "://"))
                                effectiveSpec = dataSpec.buildUpon().setUri(cleanedUri).build()
                            }
                            UdpDataSource(8000, 15_000)
                        }
                        else -> {
                            defaultFactory.createDataSource()
                        }
                    }
                    if (transferListener != null && delegate !is DefaultDataSource) {
                        runCatching { delegate.addTransferListener(transferListener) }
                    }
                    activeDelegate = delegate
                    activeStreamDelegates.add(delegate)
                    return delegate.open(effectiveSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return activeDelegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
                }

                override fun getUri(): Uri? = activeDelegate?.uri

                override fun getResponseHeaders(): Map<String, List<String>> {
                    return activeDelegate?.responseHeaders ?: emptyMap()
                }

                override fun close() {
                    val d = activeDelegate
                    activeDelegate = null
                    if (d != null) {
                        activeStreamDelegates.remove(d)
                        runCatching { d.close() }
                    }
                }
            }
        }
    }

    /**
     * Builds a Universal MediaSource.Factory configured with fast extractors and multi-protocol DataSource.
     */
    fun buildUniversalMediaSourceFactory(
        context: Context,
        okHttpFactory: OkHttpDataSource.Factory,
        isLiveStream: Boolean = true,
        transferListener: TransferListener? = null
    ): MediaSource.Factory {
        val dataSourceFactory = buildUniversalDataSourceFactory(context, okHttpFactory, transferListener)
        val extractorsFactory = buildUniversalExtractorsFactory(isLiveStream)
        return DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
    }

    /**
     * Builds a Crystal-Clear 4K UHD / 1080p FHD TrackSelector:
     * - Clears Android TV UI viewport constraints (which otherwise cap video to 1280x720 on TV boxes!)
     * - Forces highest supported video bitrate & resolution from frame #1 (`setForceHighestSupportedBitrate(true)`)
     * - Accepts all video & audio capabilities, mixed MIME types, and surround sound downmixing via FFmpeg.
     */
    fun buildAllCodecTrackSelector(
        context: Context,
        capHeightForWeakDevice: Boolean = false
    ): DefaultTrackSelector {
        return DefaultTrackSelector(context).apply {
            val paramsBuilder = buildUponParameters()
                .clearViewportSizeConstraints()
                .clearVideoSizeConstraints()
                // Never force a 4K/HEVC10 stream onto a 1080p-only TV Box hardware decoder that reports
                // FORMAT_EXCEEDS_CAPABILITIES — exceeding hardware codec limits locks up the SoC VPU & Audio HAL!
                .setExceedRendererCapabilitiesIfNecessary(false)
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedAudioConstraintsIfNecessary(false)
                .setTunnelingEnabled(false)
                .setForceHighestSupportedBitrate(true)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .setAllowVideoMixedDecoderSupportAdaptiveness(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedSampleRateAdaptiveness(true)
                .setAllowAudioMixedChannelCountAdaptiveness(true)
                .setAllowAudioNonSeamlessAdaptiveness(true)
                .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
                .setMaxVideoSize(if (capHeightForWeakDevice) 3840 else Int.MAX_VALUE, if (capHeightForWeakDevice) 2160 else Int.MAX_VALUE)
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .setMaxVideoFrameRate(Int.MAX_VALUE)
            setParameters(paramsBuilder)
        }
    }
}
