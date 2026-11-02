package com.jellio.tv.data.network

import okhttp3.ConnectionPool

// One connection pool for every OkHttp client in the app, so the API,
// images, updates and Watch Next reuse sockets instead of each keeping
// their own.
object SharedHttp {
    val connectionPool = ConnectionPool()
}
