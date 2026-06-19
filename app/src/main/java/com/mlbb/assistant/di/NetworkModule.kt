package com.mlbb.assistant.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mlbb.assistant.BuildConfig
import com.mlbb.assistant.data.remote.api.MetaApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Interceptor that retries transient network failures up to [maxRetries] times
 * with exponential back-off. Only retries on [IOException] (connection errors);
 * HTTP error codes (4xx/5xx) are NOT retried and must be handled by callers.
 */
private class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastException: IOException? = null

        while (attempt < maxRetries) {
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful) return response
                // Non-2xx HTTP response — do not retry, return immediately.
                return response
            } catch (e: IOException) {
                lastException = e
                attempt++
                if (attempt < maxRetries) {
                    val backoffMs = (500L * attempt).coerceAtMost(4_000L)
                    Timber.w("RetryInterceptor: attempt $attempt failed — retrying in ${backoffMs}ms")
                    Thread.sleep(backoffMs)
                }
            }
        }

        throw lastException ?: IOException("RetryInterceptor: all $maxRetries retries exhausted")
    }
}

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
            .addInterceptor(RetryInterceptor(maxRetries = 3))

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
