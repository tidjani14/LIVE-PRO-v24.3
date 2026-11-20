package com.tvflux.pro.data.remote

import com.tvflux.pro.ChannelRepository
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

object NetworkModule {

    /**
     * عميل المزامنة القياسي (OkHttp Sync Client):
     * - يشارك طبقة SSL و DoH من ChannelRepository.xtreamHttpClient بدون كسر أي ميزة حالية.
     * - يفرض HTTP_1_1، maxRequestsPerHost = 8، ConnectionPool(20, 5, MINUTES)، و gzip التلقائي من OkHttp.
     */
    val syncClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 8
        }
        ChannelRepository.xtreamHttpClient.newBuilder()
            .dispatcher(dispatcher)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * عميل البث المباشر والأفلام (Stream Client):
     * - يدعم HTTP/1.1 + HTTP/2 مع الحفاظ على hlsStickyRedirectInterceptor المدمج في ChannelRepository.streamHttpClient.
     */
    val streamClient: OkHttpClient by lazy {
        ChannelRepository.streamHttpClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }
}
