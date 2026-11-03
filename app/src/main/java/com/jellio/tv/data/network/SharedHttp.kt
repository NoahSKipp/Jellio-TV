package com.jellio.tv.data.network

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

object SharedHttp {
    val connectionPool = ConnectionPool()

    // For requests outside the Jellyfin API (GitHub, launcher artwork): no
    // server rewriting or auth, and shorter waits than the API client.
    val plainClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
