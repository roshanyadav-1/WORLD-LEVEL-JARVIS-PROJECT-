package com.aris.voice.utilities

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // Connection pooling is automatically managed by OkHttp
            .build()
    }
}
