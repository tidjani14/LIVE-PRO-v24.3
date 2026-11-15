package com.tvflux.pro.data.remote

object XtreamUrlBuilder {

    private fun cleanServer(server: String): String {
        return server.trim().removeSuffix("/")
    }

    // [F1] الالتزام الصارم برابط السيرفر المباشر (directSource) أو الامتداد المحدد من سيرفر Xtream (ts أو m3u8)
    fun live(
        server: String,
        user: String,
        pass: String,
        streamId: Int,
        containerExtension: String = "",
        directSource: String = ""
    ): String {
        val direct = directSource.trim()
        if (direct.startsWith("http://", ignoreCase = true) ||
            direct.startsWith("https://", ignoreCase = true) ||
            direct.startsWith("rtmp://", ignoreCase = true) ||
            direct.startsWith("rtsp://", ignoreCase = true)
        ) {
            return direct
        }
        val base = cleanServer(server)
        val ext = containerExtension.trim().removePrefix(".")
        return if (ext.isNotEmpty() && !ext.equals("null", ignoreCase = true)) {
            "$base/live/$user/$pass/$streamId.$ext"
        } else {
            "$base/live/$user/$pass/$streamId.ts"
        }
    }

    // [F1] بناء رابط الفيلم مع الامتداد الفعلي المقدم من السيرفر أو استخدام directSource حرفياً
    fun movie(
        server: String,
        user: String,
        pass: String,
        streamId: Int,
        containerExtension: String = "",
        directSource: String = ""
    ): String {
        val direct = directSource.trim()
        if (direct.startsWith("http://", ignoreCase = true) ||
            direct.startsWith("https://", ignoreCase = true) ||
            direct.startsWith("rtmp://", ignoreCase = true) ||
            direct.startsWith("rtsp://", ignoreCase = true)
        ) {
            return direct
        }
        val base = cleanServer(server)
        val ext = containerExtension.trim().removePrefix(".").takeIf {
            it.isNotEmpty() && !it.equals("null", ignoreCase = true)
        } ?: "mp4"
        return "$base/movie/$user/$pass/$streamId.$ext"
    }

    // [F1] بناء رابط حلقة المسلسل مع الامتداد الفعلي المقدم من السيرفر أو استخدام directSource حرفياً
    fun series(
        server: String,
        user: String,
        pass: String,
        episodeId: Int,
        containerExtension: String = "",
        directSource: String = ""
    ): String {
        val direct = directSource.trim()
        if (direct.startsWith("http://", ignoreCase = true) ||
            direct.startsWith("https://", ignoreCase = true) ||
            direct.startsWith("rtmp://", ignoreCase = true) ||
            direct.startsWith("rtsp://", ignoreCase = true)
        ) {
            return direct
        }
        val base = cleanServer(server)
        val ext = containerExtension.trim().removePrefix(".").takeIf {
            it.isNotEmpty() && !it.equals("null", ignoreCase = true)
        } ?: "mp4"
        return "$base/series/$user/$pass/$episodeId.$ext"
    }
}
