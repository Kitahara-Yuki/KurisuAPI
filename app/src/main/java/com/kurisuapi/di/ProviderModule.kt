package com.kurisuapi.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kurisuapi.BuildConfig
import com.kurisuapi.domain.provider.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    @Provides
    @Singleton
    @Named("provider")
    fun provideProviderOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "okhttp_provider_cache")
        val cache = Cache(cacheDir, 10L * 1024 * 1024) // 10 MB
        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
                    else HttpLoggingInterceptor.Level.NONE
                    if (BuildConfig.DEBUG) {
                        redactHeader("Authorization")
                        redactHeader("x-api-key")
                        redactHeader("api-key")
                    }
                }
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOpenAiCompatibleProvider(
        @Named("provider") okHttpClient: OkHttpClient,
        gson: Gson
    ): OpenAiCompatibleProvider = OpenAiCompatibleProvider(okHttpClient, gson)

    @Provides
    @Singleton
    fun provideAnthropicProvider(
        @Named("provider") okHttpClient: OkHttpClient,
        gson: Gson
    ): AnthropicProvider = AnthropicProvider(okHttpClient, gson)

    @Provides
    @Singleton
    fun provideGeminiProvider(
        @Named("provider") okHttpClient: OkHttpClient,
        gson: Gson
    ): GeminiProvider = GeminiProvider(okHttpClient, gson)
}
