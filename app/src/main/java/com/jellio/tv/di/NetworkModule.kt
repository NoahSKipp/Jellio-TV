package com.jellio.tv.di

import com.jellio.tv.BuildConfig
import com.jellio.tv.data.network.APP_VERSION
import com.jellio.tv.data.network.SharedHttp
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.data.network.buildEmbyAuthorizationHeader
import com.jellio.tv.data.session.SessionGuard
import com.jellio.tv.data.session.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

private const val PLACEHOLDER_BASE_URL = "http://localhost/"

// 172.16.0.0/12, the private range Docker networks use.
private val PRIVATE_172 = Regex("^172\\.(1[6-9]|2\\d|3[01])\\.")

fun normalizeServerAddress(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return trimmed
    val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
    } else {
        val lower = trimmed.lowercase()
        if (lower.contains(":8096") || lower.startsWith("192.168.") || lower.startsWith("10.") || PRIVATE_172.containsMatchIn(lower) || lower.startsWith("localhost")) {
            "http://$trimmed"
        } else {
            "https://$trimmed"
        }
    }
    return withScheme.toHttpUrlOrNull()?.toString()?.trimEnd('/') ?: withScheme
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class BaseUrlInterceptor

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class AuthInterceptor

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    @BaseUrlInterceptor
    fun provideBaseUrlInterceptor(sessionManager: SessionManager): Interceptor =
        Interceptor { chain ->
            val original = chain.request()
            val raw = sessionManager.getCachedServerAddress()
            if (raw.isNullOrBlank()) {
                throw java.io.IOException("No server address configured. Enter your Jellyfin server address.")
            }
            val normalized = normalizeServerAddress(raw)
            val target = normalized.toHttpUrlOrNull()
                ?: throw java.io.IOException("Invalid server address: '$raw'. Please check your server URL.")

            val newUrlBuilder = original.url.newBuilder()
                .scheme(target.scheme)
                .host(target.host)
                .port(target.port)

            val targetPathSegments = target.pathSegments.filter { it.isNotEmpty() }
            if (targetPathSegments.isNotEmpty()) {
                val originalPathSegments = original.url.pathSegments
                newUrlBuilder.encodedPath("")
                for (segment in targetPathSegments) {
                    newUrlBuilder.addPathSegment(segment)
                }
                for (segment in originalPathSegments) {
                    if (segment.isNotEmpty()) {
                        newUrlBuilder.addPathSegment(segment)
                    }
                }
            }

            chain.proceed(original.newBuilder().url(newUrlBuilder.build()).build())
        }

    @Provides
    @Singleton
    @AuthInterceptor
    fun provideAuthInterceptor(sessionManager: SessionManager, sessionGuard: SessionGuard): Interceptor =
        Interceptor { chain ->
            val original = chain.request()
            val activeLang = com.jellio.tv.data.prefs.DisplayPreferences.displayLanguage
            val request = original.newBuilder()
                .header("Accept-Language", "${activeLang.localeTag}, ${activeLang.code};q=0.9, en;q=0.8")
            // A request carrying its own credentials (checking a saved
            // profile's token) keeps them.
            val ownCredentials = original.header("Authorization") != null
            val token = if (ownCredentials) null else sessionManager.getCachedAccessToken()
            if (!ownCredentials) {
                request.header("Authorization", buildEmbyAuthorizationHeader(sessionManager.getCachedDeviceId(), APP_VERSION, token))
            }
            val response = chain.proceed(request.build())
            sessionGuard.onResponse(response.code, original.url.host, original.url.encodedPath, token, ownCredentials)
            response
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @BaseUrlInterceptor baseUrlInterceptor: Interceptor,
        @AuthInterceptor authInterceptor: Interceptor,
    ): OkHttpClient = SharedHttp.serverBase.newBuilder()
        // The default 5 per host queued Home's rows behind each other and the pollers.
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(authInterceptor)
        // A call can ask for a longer wait with this header (following a pasted page to its stream can take
        // over a minute on the server); everything else keeps the short read timeout.
        .addInterceptor { chain ->
            val seconds = chain.request().header("X-Read-Timeout-Seconds")?.toIntOrNull()
            if (seconds == null) {
                chain.proceed(chain.request())
            } else {
                chain.withReadTimeout(seconds, java.util.concurrent.TimeUnit.SECONDS)
                    .proceed(chain.request().newBuilder().removeHeader("X-Read-Timeout-Seconds").build())
            }
        }
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideJellyfinApi(retrofit: Retrofit): JellyfinApi = retrofit.create(JellyfinApi::class.java)
}
