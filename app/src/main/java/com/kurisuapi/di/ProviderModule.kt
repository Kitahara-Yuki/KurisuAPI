package com.kurisuapi.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kurisuapi.domain.provider.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    @Provides
    @Singleton
    @Named("provider")
    fun provideProviderOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.NONE
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
