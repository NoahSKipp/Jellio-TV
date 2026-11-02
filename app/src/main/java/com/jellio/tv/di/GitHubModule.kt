package com.jellio.tv.di

import com.jellio.tv.data.network.GitHubApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GitHubModule {
    @Provides
    @Singleton
    fun provideGitHubApi(moshi: Moshi): GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(
            OkHttpClient.Builder()
                .connectionPool(com.jellio.tv.data.network.SharedHttp.connectionPool)
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                // GitHub's API asks every client for a User-Agent naming it.
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "Jellio-TV/${com.jellio.tv.data.network.APP_VERSION}")
                            .header("Accept", "application/vnd.github+json")
                            .build(),
                    )
                }
                .build(),
        )
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GitHubApi::class.java)
}
