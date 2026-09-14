package com.jellio.tv.di

import com.jellio.tv.data.network.APP_VERSION
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.data.network.buildEmbyAuthorizationHeader
import com.jellio.tv.data.session.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

private const val PLACEHOLDER_BASE_URL = "http://localhost/"

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class BaseUrlInterceptor

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class AuthInterceptor

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Retrofit needs a real base URL at build time; the actual
    // Jellyfin server address is only known once the reader has typed
    // it into screens/login.js's own real equivalent here
    // (ui/auth/LoginScreen.kt). This interceptor swaps scheme/host/
    // port onto every outgoing request right before it leaves,
    // keeping the path/query Retrofit's own @GET/@POST annotations
    // already built.
    @Provides
    @Singleton
    @BaseUrlInterceptor
    fun provideBaseUrlInterceptor(sessionManager: SessionManager): Interceptor =
        Interceptor { chain ->
            val original = chain.request()
            val target = runBlocking { sessionManager.serverAddress() }?.toHttpUrlOrNull()
            val request = if (target != null) {
                original.newBuilder()
                    .url(
                        original.url.newBuilder()
                            .scheme(target.scheme)
                            .host(target.host)
                            .port(target.port)
                            .build(),
                    )
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }

    // Real "Authorization" header, not X-Emby-Token: AuthorizationContext.cs's
    // own GetAuthorizationInfoFromDictionary() only ever reads that legacy
    // header behind the same disabled EnableLegacyAuthorization flag
    // JellyfinApi.kt's own buildEmbyAuthorizationHeader() documents, so an
    // X-Emby-Token here used to authenticate nothing at all on a server with
    // it off: every request after a real successful login still read back
    // as anonymous. Sent on every request, session token or not, since the
    // Client/Device/DeviceId fields matter even pre-login (AuthenticateByName
    // itself reads them off this same header, JellyfinApi.kt's own header on
    // that call explains why).
    @Provides
    @Singleton
    @AuthInterceptor
    fun provideAuthInterceptor(sessionManager: SessionManager): Interceptor =
        Interceptor { chain ->
            val token = runBlocking { sessionManager.accessToken() }
            val deviceId = runBlocking { sessionManager.deviceId() }
            val header = buildEmbyAuthorizationHeader(deviceId, APP_VERSION, token)
            val request = chain.request().newBuilder().addHeader("Authorization", header).build()
            chain.proceed(request)
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @BaseUrlInterceptor baseUrlInterceptor: Interceptor,
        @AuthInterceptor authInterceptor: Interceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
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
