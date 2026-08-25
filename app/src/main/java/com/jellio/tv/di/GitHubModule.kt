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

// GitHub's own real api.github.com, a fixed host, not the reader's own
// Jellyfin server NetworkModule's own BaseUrlInterceptor swaps in per
// request: a separate, plain OkHttpClient/Retrofit pair rather than
// reusing that one (also sidesteps an ambiguous unqualified OkHttpClient
// binding, that module's own singleton already claiming the type).
@Module
@InstallIn(SingletonComponent::class)
object GitHubModule {

    // No public Retrofit/OkHttpClient binding here on purpose: this
    // module's own GitHubApi is the only thing that ever needs either,
    // and NetworkModule already claims the unqualified Retrofit/
    // OkHttpClient types for the reader's own Jellyfin server, a second
    // unqualified binding for either being an ambiguous one Hilt would
    // refuse to resolve.
    @Provides
    @Singleton
    fun provideGitHubApi(moshi: Moshi): GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GitHubApi::class.java)
}
