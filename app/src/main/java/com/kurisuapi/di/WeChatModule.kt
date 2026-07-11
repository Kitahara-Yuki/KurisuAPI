package com.kurisuapi.di

import android.content.Context
import com.google.gson.GsonBuilder
import com.kurisuapi.BuildConfig
import com.kurisuapi.data.wechat.WeChatApiService
import com.kurisuapi.data.wechat.WeChatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WeChatModule {

    @Provides
    @Singleton
    @Named("wechat")
    fun provideWeChatOkHttpClient(
        weChatRepository: WeChatRepository,
        @ApplicationContext context: Context
    ): OkHttpClient {
        // Bug 6 fix: 动态替换 base URL，支持微信服务器重定向
        val redirectInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val currentBaseUrl = weChatRepository.baseUrl
            if (currentBaseUrl != WeChatRepository.DEFAULT_BASE_URL) {
                val originalUrl = originalRequest.url
                val newBaseUrl = currentBaseUrl.toHttpUrlOrNull()
                if (newBaseUrl != null) {
                    val newUrl = originalUrl.newBuilder()
                        .scheme(newBaseUrl.scheme)
                        .host(newBaseUrl.host)
                        .port(newBaseUrl.port)
                        .build()
                    val newRequest = originalRequest.newBuilder().url(newUrl).build()
                    return@Interceptor chain.proceed(newRequest)
                }
            }
            chain.proceed(originalRequest)
        }

        val cacheDir = File(context.cacheDir, "okhttp_wechat_cache")
        val cache = Cache(cacheDir, 5L * 1024 * 1024) // 5 MB

        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(redirectInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
                    else HttpLoggingInterceptor.Level.NONE
                    if (BuildConfig.DEBUG) {
                        redactHeader("Authorization")
                        redactHeader("iLink-App-ClientVersion")
                    }
                }
            )
            .build()
    }

    @Provides
    @Singleton
    @Named("wechat")
    fun provideWeChatRetrofit(@Named("wechat") okHttpClient: OkHttpClient): Retrofit {
        val gson = GsonBuilder().create()
        return Retrofit.Builder()
            .baseUrl("https://ilinkai.weixin.qq.com/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())  // String/基本类型优先
            .addConverterFactory(GsonConverterFactory.create(gson))  // JSON 对象其次
            .build()
    }

    @Provides
    @Singleton
    fun provideWeChatApiService(@Named("wechat") retrofit: Retrofit): WeChatApiService {
        return retrofit.create(WeChatApiService::class.java)
    }
}
