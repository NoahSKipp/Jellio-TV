package com.jellio.tv.data.network

import okhttp3.ConnectionPool

object SharedHttp {
    val connectionPool = ConnectionPool()
}
