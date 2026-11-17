package com.tvflux.pro.data.remote

object XtreamUrlBuilder {

    private fun cleanServer(server: String): String {
        return server.trim().removeSuffix("/")
    }

    fun live(
        server: String,
        user: String,
        pass: String,
        streamId: Int,
        containerExtension: String = "",
        directSource: String = ""
    ): String {
        val direct = directSource.trim()
        if (direct.startsWith("http://", ignoreCase = true) || direct.startsWith("https://", ignoreCase = true)) {
            return direct
        }
        val base = cleanServer(server)
        val ext = containerExtension.trim().removePrefix(".")
        return if (ext.isNotBlank()) "$base/live/$user/$pass/$streamId.$ext" else "$base/live/$user/$pass/$streamId"
    }

    fun movie(
        server: String,
        user: String,
        pass: String,
        streamId: Int,
        containerExtension: String = "",
        directSource: String = ""
    ): String {
        val direct = directSource.trim()
        if (direct.startsWith("http://", ignoreCase = true) || direct.startsWith("https://", ignoreCase = true)) {
            return direct
        }
        val base = cleanServer(server)
        val ext = containerExtension.trim().removePrefix(".")
        return if (ext.isNotBlank()) "$base/movie/$user/$pass/$streamId.$ext" else "$base/movie/$user/$pass/$streamId"
    }

    fun series(
        server: String,
        user: String,
        pass: String,
        episodeId: Int,
        containerExtension: String = "",
        directSource: String = ""
    ): String {
        val direct = directSource.trim()
        if (direct.startsWith("http://", ignoreCase = true) || direct.startsWith("https://", ignoreCase = true)) {
            return direct
        }
        val base = cleanServer(server)
        val ext = containerExtension.trim().removePrefix(".")
        return if (ext.isNotBlank()) "$base/series/$user/$pass/$episodeId.$ext" else "$base/series/$user/$pass/$episodeId"
    }
}
