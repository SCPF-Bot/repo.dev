package com.mlbb.assistant.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mlbb.assistant.BuildConfig
import com.mlbb.assistant.data.remote.api.MetaApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * P1-02 fix: [RetryInterceptor] has been removed from this module.
 *
 * The previous implementation used [Thread.sleep] inside the OkHttp interceptor
 * to implement exponential back-off. Blocking an OkHttp dispatcher thread for up
 * to 4 seconds under concurrent network calls can starve the thread pool (max 64
 * threads by default) and delay unrelated requests.
 *
 * Retry logic has been moved to [com.mlbb.assistant.data.repository.HeroRepositoryImpl]
 * where it uses coroutine [kotlinx.coroutines.delay] — non-blocking, cancellation-
 * aware, and properly integrated with structured concurrency.
 *
 * HTTP 4xx/5xx responses are NOT retried (same policy as before) and must be
 * handled by callers via the [com.mlbb.assistant.utils.NetworkResult] sealed class.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.META_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideMetaApi(retrofit: Retrofit): MetaApi = retrofit.create(MetaApi::class.java)
}
