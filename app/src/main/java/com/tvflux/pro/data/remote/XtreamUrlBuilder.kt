package com.tvflux.pro.data.remote

object XtreamUrlBuilder {

    private fun cleanServer(server: String): String {
        return server.trim().removeSuffix("/")
    }

    // [F1] بناء رابط البث المباشر بصيغة .ts القياسية أو استخدام directSource إن وُجد
    fun live(
        server: String,
        user: String,
        pass: String,
        streamId: Int,
        directSource: String = ""
    ): String {
        val direct = directSource.trim()
        if (direct.startsWith("http://", ignoreCase = true) || direct.startsWith("https://", ignoreCase = true)) {
            return direct
        }
        val base = cleanServer(server)
        return "$base/live/$user/$pass/$streamId.ts"
    }

    // [F1] بناء رابط الفيلم مع الامتداد الفعلي أو استخدام directSource إن وُجد
    fun movie(
        server: String,
        user: String,
        pass: String,
        streamId: Int,
        containerExtension: String = "mp4",
        directSource: String = ""
    ): String {
        val direct = directSource.trim()
        if (direct.startsWith("http://", ignoreCase = true) || direct.startsWith("https://", ignoreCase = true)) {
            return direct
        }
        val base = cleanServer(server)
        val ext = containerExtension.trim().removePrefix(".").ifBlank { "mp4" }
        return "$base/movie/$user/$pass/$streamId.$ext"
    }

    // [F1] بناء رابط حلقة المسلسل مع الامتداد الفعلي أو استخدام directSource إن وُجد
    fun series(
        server: String,
        user: String,
        pass: String,
        episodeId: Int,
        containerExtension: String = "mp4",
        directSource: String = ""
    ): String {
        val direct = directSource.trim()
        if (direct.startsWith("http://", ignoreCase = true) || direct.startsWith("https://", ignoreCase = true)) {
            return direct
        }
        val base = cleanServer(server)
        val ext = containerExtension.trim().removePrefix(".").ifBlank { "mp4" }
        return "$base/series/$user/$pass/$episodeId.$ext"
    }
}
